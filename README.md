# InventoryManager+

A client-side Fabric mod for Minecraft 26.2 and 26.3. Save the layout of your hotbar, inventory, armour and off-hand as a preset, then put everything back in place with one click or a hotkey.

[Download on Modrinth](https://modrinth.com/mod/inventorymanagerplus)

> [!WARNING]
> **Check a server's rules before using this mod on it.** Many servers, especially PvP, minigame and competitive servers, ban inventory sorting and auto-arranging mods, and their anticheat may flag the fast inventory clicks this mod makes. You can be kicked or banned for using it where it isn't allowed. If you are unsure, ask the server's staff first. Use it on servers at your own risk. The author is not responsible for any punishment.

## Requirements

| | |
|---|---|
| Minecraft | 26.2 or 26.3 (download the file for your version) |
| Fabric Loader | 0.19.3 or newer (26.2), 0.19.5 or newer (26.3) |
| Fabric API | Required |
| Mod Menu | Optional (adds a config button to the mod list) |
| Java | 25 |
| Side | Client only. Not needed on the server. |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download the latest version for your Minecraft version from [Modrinth](https://modrinth.com/mod/inventorymanagerplus) and put it in your `mods` folder.

## Features

- **Presets:** save any arrangement of the hotbar, main inventory, armour and off-hand. Capture your current layout, or build one by hand from any item in the game.
- **Apply:** click a preset to rearrange your inventory to match it. Slots that are already correct are left alone. Each slot is filled as full as possible from matching stacks.
- **Auto Sort:** keeps one preset's layout in place as you play. It waits while you are eating, drawing a bow or holding an item on the cursor, and pauses while a chest or other container is open. Hold left Alt to pause it.
- **Preset hotkeys:** give a preset its own key to apply it from anywhere in game.
- **Categories:** a slot can accept any Weapon, Armor, Tool, Food, Block, Material or Miscellaneous item instead of one specific item. In an armour slot, "Any Armor" means armour for that slot.
- **Enchantment requirements:** a slot can require specific enchantments, for example any Efficiency pickaxe or a sword with Sharpness IV or higher.
- **Keep-empty slots:** mark a hotbar or inventory slot as empty and the preset keeps it clear.
- **Partial presets:** slots you don't set are unmanaged and never touched.
- **Creative mode:** optionally creates preset items you don't have, enchantments included. Nothing is ever created in Survival.
- **Color themes:** six color themes for the mod's screens.

## Usage

| Action | How |
|---|---|
| Open the menu | Press **Z**, or use the button on the pause screen |
| Create a preset | **New Preset**, then **Capture Hotbar** or **Capture Inventory**, name it, **Save Preset** |
| Apply a preset | Click it in the menu, or press its hotkey |
| Set a slot | Click it to copy from your inventory, or switch to **Browse items** to pick any item |
| Slot options | Right-click a slot for categories and enchantments |
| Remove a slot | Hover over it and press **F** |
| Set a preset hotkey | Click **Key** next to the preset name and press a key (Backspace clears it) |
| Reorder or duplicate | Use the ▲ ▼ and ⧉ buttons on a preset card |
| Pause Auto Sort | Hold **left Alt** |

All keys can be changed in Options → Controls → InventoryManager+. The **Apply Active Preset** and **Toggle Auto Sort** keys are unbound by default.

### How items are matched

A slot matches by item type. Any diamond sword counts as "diamond sword". Durability, custom names and stack size are ignored. To be more specific about gear, right-click the slot and set enchantment requirements.

## Item safety

Items only ever move between slots of your own inventory. The mod does not drop, delete or destroy items, including when your inventory is full, a preset can't be completed, or you close a screen partway through.

The one exception is the **Drop** setting, which is off by default. With it on, applying a preset throws out items in slots marked empty, but only after first trying to stack them onto the same item elsewhere and then trying to move them to a free slot. Auto Sort only drops items if **Auto Sort drop** is also turned on.

Every action is a normal inventory click that the server sees and approves. Nothing is faked on the client.

## Settings

Open settings from the menu or from Mod Menu.

| Setting | Default | Description |
|---|---|---|
| Color | Grey | Color theme for the mod's screens |
| Drop | Off | Applying a preset throws out items in keep-empty slots |
| Auto Sort drop | Off | Same as Drop, but for Auto Sort |
| Check every | 10 ticks | How often Auto Sort checks your inventory |
| Move delay | 2 ticks | Delay between inventory actions. Raise it if a server rejects moves |
| Status messages | On | Show messages above the hotbar |
| Confirm delete | On | Ask before deleting a preset |
| Creative auto-get | Off | Create missing preset items in Creative mode |
| Pause in inventory | On | Auto Sort waits while any screen is open |

Presets and settings are saved in `config/inventory-manager-plus/`.

## Versions

The mod is only distributed through [Modrinth](https://modrinth.com/mod/inventorymanagerplus). Download it there or through the Modrinth app. No jars are published on GitHub.

Version numbers match between GitHub and Modrinth. Each version is tagged in this repository (for example `v1.0.0`), and its changelog is on the [Releases](../../releases) page and in [CHANGELOG.md](CHANGELOG.md).

## License

Copyright © 2026 Syzy3. All rights reserved. See [LICENSE](LICENSE) for the full terms.

In short: you can play with the mod, make videos with it, and include it in modpacks that download it from the official Modrinth page. You can't reupload it, sell it, or publish modified versions without permission.
