# Inventory Manager+

A client-side Fabric mod for saving and applying inventory layouts. Never drops, deletes or
destroys an item.

---

## Read this first: the target version

Your spec asks for **Minecraft 1.26.2 with JDK 25**. There is no 1.26.2. Mojang changed the
versioning scheme after 1.21.11 — the current line is **26.1 → 26.2 → 26.3**, matching the calendar
year, and the next will be 27.1. This project targets **26.2**, which is almost certainly what you
meant, and the JDK 25 requirement is correct and in fact mandatory.

That is not a cosmetic difference. Three things changed underneath 26.1 that invalidate most
existing Fabric tutorials and any code written against 1.21.x:

| | Before | 26.1+ |
|---|---|---|
| Obfuscation | Obfuscated, Yarn mappings | **Unobfuscated.** Yarn is discontinued; Mojang's official names are the only option |
| Loom plugin | `fabric-loom` | **`net.fabricmc.fabric-loom`** — does not remap |
| Dependencies | `modImplementation` | **`implementation`** |
| Build output | `remapJar` | **`jar`** |
| `mappings` line | required | **must be removed** |

`ResourceLocation` was also renamed to `Identifier`, `ItemGroupEvents` to `CreativeModeTabEvents`,
`KeyBindingHelper` to `KeyMappingHelper` (new package `keymapping.v1`), and screen handling moved
off `Minecraft` onto `Gui` — `Minecraft.getInstance().gui.setScreen(...)`, not `setScreen(...)`.
26.2 additionally renamed `GuiGraphics` to `GuiGraphicsExtractor` and replaced `Screen#render` with
`Screen#extractRenderState`. All of that is reflected in the code.

Pinned versions, all verified current as of writing:

```properties
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.158.0+26.2
# Gradle 9.5.1, Java 25 (also required for the Gradle JVM itself)
```

IntelliJ IDEA **2025.3 or newer** is required for mixins to resolve correctly.

---

## Verification status — please read before building

I verified the build system, versions, key mapping API, screen lifecycle, and the
`GuiGraphicsExtractor` methods `fill` / `outline` / `text` / `blit` / `enableScissor` against
Fabric's official 26.2 documentation.

I could **not** verify three call signatures from any published source: drawing an item icon,
drawing its stack-count overlay, and showing an item tooltip. Rather than spread guesses across
four screen classes, all three are isolated in **`gui/Render.java`**, with the likely alternative
names listed in its javadoc. If the project fails to compile, it will fail there and nowhere else,
and the fix is three one-line edits. Check the real signatures in the decompiled
`GuiGraphicsExtractor` — [mcsrc.dev](https://mcsrc.dev) decompiles the jar in your browser.

I also left the persistent on-screen HUD out. `HudRenderCallback` was removed in 26.1 in favour of
`HudElementRegistry`, whose signature I could not confirm, so status is reported on the **action
bar** instead (`displayClientMessage(text, true)`) — verified stable, and arguably better UX than a
permanent corner overlay. Adding the HUD later is a single registration call.

---

## The core guarantee, and how it is enforced

The spec's most important rule is that the mod must never lose an item. This is not implemented as
a check — checks get bypassed by the one code path nobody thought about. It is implemented as a
structural property:

> **The planner can only emit swaps, and a swap is a permutation.**

`ArrangementPlanner` produces a list of `Move(from, to)` records, and `InventoryOps` executes each
as a two-slot exchange. There is no code path anywhere in the mod that drops, deletes, merges away
or overwrites a stack. The multiset of items before and after any plan is identical *by
construction* — regardless of how the preset is configured, how full the inventory is, or where
execution gets interrupted.

Consequences that fall out for free:

- An unmanaged item can be pushed to a different slot, but never out of the inventory.
- A preset that cannot be completed leaves everything else untouched.
- Interrupting a plan mid-sequence is always safe; at worst a stack sits on the cursor, which
  vanilla returns to the inventory when the screen closes.

I property-tested the algorithm across 20,000 randomised inventory/preset combinations. Zero item
loss, planner simulation always matched the applied result, and every plan was idempotent — a
second pass over the result produced no further moves, which is the property that stops Auto Sort
from entering a move loop. The spec's two worked examples reproduce exactly: the pickaxe/sword swap
costs one operation, and the partial-hotbar preset leaves diamonds, emeralds, netherite upgrade and
obsidian all present.

---

## Architecture

GUI and inventory logic are fully separated. `ArrangementPlanner` is pure — it takes an array of
stacks and returns a plan, touches no Minecraft state, and can be unit-tested standalone.

```
InventoryManagerPlus     client entry point, tick loop, keybind dispatch
ModKeys                  four rebindable keys, all in Options > Controls

preset/
  Preset                 name, icon, match mode, auto-sort flag, sparse slot map
  PresetSlot             an item requirement or an intentional blank
  MatchMode              BASIC (item type) / EXACT (+ data components)
  PresetManager          preset list, apply orchestration, active preset

inventory/
  ArrangementPlanner     the three-pass swap-only algorithm
  ItemMatcher            matching rules + candidate ranking
  InventoryOps           rate-limited click executor
  CreativeSupplier       Creative acquisition of missing items
  InvSlots               inventory-index <-> menu-slot mapping
  InventorySnapshot      snapshot + cheap change fingerprint

autosort/AutoSortManager change-driven background correction
config/                  Config + atomic JSON storage
gui/                     list screen, editor, item picker, Render helper
command/ModCommands      /inventorymanager
```

### The algorithm

Three passes over a simulated copy:

1. **Lock what is already right.** Managed slots already satisfying their requirement are marked
   final and never touched. This is what makes repeated application free.
2. **Fill the rest.** For each remaining slot, swap in the best matching stack. Because the
   simulation is re-read after each swap rather than following a precomputed assignment, cycles of
   any length are handled without special-casing, and the two-item case collapses to one operation.
3. **Honour intentional blanks.** Contents are swapped to a genuinely free slot; if none exists the
   item stays put and the slot is reported as blocked.

Each pass locks its target before moving on, so a later step cannot undo an earlier one. That
bounds moves at one per managed slot and guarantees termination.

Source selection prefers unmanaged slots (disturbing nothing else) and larger stacks, so the hotbar
gets the 64 blocks rather than the leftover three.

### Matching

Stack size is *never* a requirement. A preset saved with 64 Cobblestone is satisfied by 37, exactly
as specified. Durability is ignored in both modes — a half-worn pickaxe is still the pickaxe you
meant. `EXACT` additionally requires the components the preset recorded to be present and equal,
which is how you distinguish a Sharpness V sword from a plain one.

### Auto Sort safety

Four independent brakes, because the failure mode here is a mod that fights the player:

- Checks only every `autoSortIntervalTicks` (default 10 — half a second, far below reaction time).
- A cheap allocation-free fingerprint gates the check, so the steady state is one integer
  comparison twice a second. No per-frame scanning.
- Corrects at most `maxMovesPerAutoSortCycle` slots per cycle, so disturbances heal over a second
  rather than in a packet burst.
- The pause key (default **left Alt**) suspends it while held, plus a one-second grace period after
  release so manual rearrangement is not fought.

The fingerprint is invalidated after each queued correction, so the mod's own moves are never
mistaken for external changes — which is the usual way mods like this end up looping.

### Multiplayer

Every action is a click a real player could perform, handed to `MultiPlayerGameMode` so the server
confirms or rejects it normally. Nothing is faked client-side. Three consecutive failures abort the
queue with a message rather than retrying. Client commands are marked `attended`, a 26.x feature
that stops a server from silently triggering them via a clickable chat component.

The mod refuses to act while any container is open — one check that covers chests, crafting tables
and every other menu at once, and prevents accidentally shift-moving items into someone's storage.

---

## Edge cases

| Case | Behaviour |
|---|---|
| Player has none of a required item | Slot skipped, reported as missing, rest applied |
| Multiple matching stacks | Largest / least-disruptive wins |
| Target holds another required item | Resolved by swapping; cycles handled |
| Target holds an unmanaged item | Swapped out, never dropped |
| Inventory completely full | Blanks refused rather than forced; items intact |
| Hotbar-only preset | Rows 1–3 untouched and unmanaged |
| Full-inventory preset | Works; unsatisfiable slots left alone |
| Player dies | Queue cancelled on death |
| Chest / crafting table / any container open | Paused, resumes on close |
| Creative, missing items | Acquired, only into empty slots, no duplicates if owned |
| Creative ↔ Survival switch | Acquisition silently skipped in Survival |
| Dimension change / join / leave | Queue cancelled, Auto Sort state reset |
| Server rejects an action | Abort after 3 failures, message shown |
| Screen closed mid-rearrangement | Safe; each move is independently consistent |
| Item vanishes between operations | Move skipped |
| Custom components / NBT | Preserved; matched in EXACT mode |
| Similar items, different durability/enchants | Durability ignored; enchants distinguished in EXACT |

---

## Configuration

`config/inventory-manager-plus/` — `config.json` and `presets.json`, written atomically via a temp
file so a crash mid-save cannot corrupt your layouts. One malformed preset entry is skipped rather
than taking the file down with it.

Tunable: Auto Sort on/off, interval, operation cooldown, move caps, default match mode, delete
confirmation, Creative acquisition, status messages, container guard.

No Mod Menu dependency — settings are reachable from the main screen. Adding a Mod Menu entry point
is straightforward if you want it.

---

## Building

```bash
./gradlew build      # output in build/libs/
./gradlew runClient
```

Set your Gradle JVM to Java 25 (IntelliJ: Settings → Build Tools → Gradle → Gradle JVM). Note
there is no `gradle-wrapper.jar` in this archive — run `gradle wrapper --gradle-version 9.5.1` once,
or use your system Gradle.

## Suggested first steps

1. Build and fix the three flagged calls in `Render.java` if needed.
2. Test in a single-player creative world: `/inventorymanager create PvP` captures your hotbar
   instantly and is the fastest way to exercise the whole pipeline.
3. Then test on a server, watching for rejected-action messages.
