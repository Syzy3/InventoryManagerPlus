# Inventory Manager+

Save your inventory layout as a preset and put it back with one click.

Made a perfect PvP hotbar? A mining kit? A building loadout? Save it once, then restore it any
time — after a death, after raiding a chest, or just because everything drifted out of place.

**Inventory Manager+ never drops, deletes or destroys an item.** Not when your inventory is full,
not when a preset can't be completed, not if you close the screen halfway through. Items only ever
move between slots.

---

## Features

**Presets** — Save any arrangement of your hotbar, inventory, off-hand and armour. Capture your
current layout in one click, or build one by hand from any item in the game.

**One-click apply** — Open the menu, click a preset, done. Items already in the right place are
left alone, so applying a preset twice costs nothing.

**Auto Sort** — Turn it on for a preset and your layout maintains itself. Pick something up out of
place and it slides back where it belongs. Hold **left Alt** to pause it while you rearrange
things manually.

**Enchantment requirements** — A preset can ask for *any* Efficiency pickaxe, or specifically a
Sharpness IV-or-better sword. Right-click a weapon, tool or armour piece in the editor to choose.

**Partial presets** — You don't have to fill every slot. Slots you never touch stay unmanaged, and
the preset leaves whatever is in them completely alone.

**Blank slots** — Mark a slot as deliberately empty and the preset will keep it clear.

**Creative support** — In Creative, items you don't own are created for you, enchantments
included. In Survival nothing is conjured; missing items are simply reported.

**Multiplayer safe** — Every action is an ordinary click the server sees and approves. Nothing is
faked client-side, and the mod won't act while a chest or crafting table is open.

---

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer for **Minecraft 26.2**
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder — required
3. Put `inventory-manager-plus-1.0.0.jar` in your `mods` folder
4. Launch

[Mod Menu](https://modrinth.com/mod/modmenu) is optional. With it you get a config button on the
mod list; without it everything is still reachable in game.

This is a **client-side** mod. You don't need it on the server, and the server doesn't need to know
it exists.

---

## Using it

**Open the menu** with the icon in the pause screen, or press **Z**.

**Make a preset:** New Preset → arrange your inventory how you like it → Capture Hotbar or Capture
Inventory → name it → Save.

**Apply one:** open the menu and click it.

**Edit a slot:** click it to copy from your inventory, or switch to Browse to pick any item in the
game. Right-click a weapon, tool or armour piece to set enchantment requirements.

**Rebind the keys** in Options → Controls, under Inventory Manager+.

### Match modes

Each preset matches in one of two ways:

- **Basic** — item type only. Any diamond sword satisfies "diamond sword". This is what you want
  almost always.
- **Exact** — item type plus custom name, potion type and other item data. Use it to tell a
  named sword apart from an ordinary one.

Durability never matters in either mode, and neither does stack size — a preset saved with 64
cobblestone is happy with 37.

---

## Settings

Auto Sort speed and limits, default match mode, delete confirmation, Creative item creation, and
status messages are all adjustable in the settings screen. There are six colour themes.

Presets are stored in `config/inventory-manager-plus/`, written safely so a crash can't corrupt
them.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Fabric API | required |
| Java | 25 |
| Side | Client only |

---

## Licence

Copyright © 2026 **Syzy3**. All rights reserved. See [LICENSE](LICENSE).

You're welcome to download and play with this mod. Please don't reupload it, sell it, or put it in
a modpack without asking me first.
