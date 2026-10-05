# Changelog

All notable changes to InventoryManager+ are listed here. Version numbers match the versions published on [Modrinth](https://modrinth.com/mod/inventorymanagerplus/versions).

## [Unreleased]

### Changed
- **Drop** and **Auto Sort drop** now move items out of keep-empty slots into a free slot first. Items are only thrown out when there's no free slot left (slots marked empty don't count as free).

## [1.1.0] - 2026-10-04

For Minecraft 26.2, Fabric Loader 0.19.3+, Fabric API.

### Added
- Preset hotkeys: give a preset its own key to apply it from anywhere.
- Reorder presets with ▲ ▼ and duplicate them with ⧉.
- Slot options menu (right-click a slot) for categories and enchantments.
- Press F over a slot in the editor to remove it.
- Optional **Drop** and **Auto Sort drop** settings that throw out items left in keep-empty slots. Both are off by default.
- Six color themes, with themed buttons and dialogs.
- Applying a preset tops up each slot from other stacks of the same item.
- Large presets keep applying until every slot that can be filled is filled.
- The active preset is remembered after restarting the game.
- New mod icon, and Modrinth and Releases links in Mod Menu.

### Changed
- Item matching is by item type only. The Basic/Exact match modes were removed. Old presets still load and use the new matching.
- Presets no longer store a stack amount per slot. Slots are filled as full as possible.
- "Any Armor" in an armour slot now means armour for that slot (the helmet slot takes any helmet).
- Better item categories. Armor only includes wearable armour pieces.

### Fixed
- Auto Sort no longer runs while a chest or other container is open in Creative mode.
- Creative auto-get never overwrites an item when the inventory is full, and no longer fills the inventory with items that can't satisfy a slot.
- Worn armour and the off-hand item are no longer taken to fill other slots unless the preset manages those slots.
- A planned drop is cancelled if you change your inventory while the mod is waiting for a screen to close.
- Pressing F while typing a preset name no longer clears a slot.
- The preset list no longer gets stuck scrolled off-screen after deleting presets.
- Presets whose first slot is a category now show an icon.
- An unreadable presets file is backed up to `presets.json.bak` instead of being overwritten.

## [1.0.0] - 2026-08-28

Initial release for Minecraft 26.2.

- Save your inventory layout as a preset and apply it whenever you want.
- Auto Sort keeps the layout in place as you play.
- Set slots by copying from your inventory or browsing every item in the game.
- Category slots accept any Food, Block, Tool and so on.
- Require specific enchantments on weapons, tools and armour.
- Choose stack amounts per slot.
- Never drops or deletes items.

[1.1.0]: https://github.com/Syzy3/InventoryManagerPlus/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Syzy3/InventoryManagerPlus/releases/tag/v1.0.0
