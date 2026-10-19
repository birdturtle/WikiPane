# WikiPane friend beta — 1.0.0-beta.2

## Install

1. Install [Cabbage beta v1.2.0-beta.2 or newer](https://github.com/birdturtle/Cabbage/releases/tag/v1.2.0-beta.2).
2. Save `wikipane-1.0.0-beta.2-developer-plugin.jar` somewhere permanent. You do not run this JAR directly.
3. Close any RuneLite client you will replace. In Cabbage, open **Settings → Advanced → Developer plugins**, enable developer plugins, and **Add plugin JAR…**. Select the JAR and leave its checkbox checked.
4. Launch your character normally. Enable **WikiPane** in RuneLite's plugin list and select its sidebar icon.

On first use Cabbage copies your normal RuneLite profiles, plugin choices and supported plugin data into a separate, stable beta profile. Later launches and JAR updates retain that beta profile. Changes do not synchronize with your normal profile. Your Jagex login stays with the launcher; never send credentials to the plugin author.

Local plugins have not undergone Plugin Hub review. Install only a JAR from someone you trust. The included `BUILD.txt` identifies its required RuneLite version. Cabbage checks compatibility; a RuneLite update may require a new JAR. Do not bypass that check.

## Beta 2 changes

Section/Map buttons toggle closed on a second click. The map chooser remembers expanded groups and scroll until you leave the article. Hidden preview work is paused. Dropdowns fit the available window/screen space; map tooltips and failure messages describe their real actions. Ctrl-wheel zoom keeps the image under your cursor; toolbar zoom keeps the viewport center.

## First use

- **Search:** type a title, then Enter or select a suggestion. Try Bronze dagger, Recipe for Disaster and Vorkath.
- **Navigate:** Back/Forward changes articles or returns to results. The section dropdown and expandable article headings represent the same hierarchy. Section jumps stay within the current history entry.
- **Use the game's Wiki actions:** opt into **Open Wiki links in WikiPane**. Core Wiki can remain enabled. Minimap lookup/search and supported quest/skill Wiki actions now open the sidebar.
- **Right-click an entity:** opt into **Add WikiPane to entity menus**. Default access is Shift-right-click; choose Quick Inspect for a temporary overlay or Full article for the sidebar.
- **Pin:** highlight sidebar text → right-click → Pin selection. Alt-drag to move; footer arrows turn pages. Shift-right-click for source or removal. Up to five session pins; restarting/disabling clears them.
- **Images/maps:** click article images for full-size viewing. **Map ▾** groups available maps and verified locations; a new game-map selection replaces previous WikiPane flags. Underground area auto-switching is unavailable; use the Wiki preview/image.
- **Character checks:** skill/quest/quest-point checks stay local. A question mark means unsupported or unavailable, not failed. Inventory and bank ownership are not evaluated.

Searches, articles and selected entity details go to the OSRS Wiki; map imagery comes from its map server. Those servers see your IP address. No account identifiers, levels, quest states, inventory snapshots or credentials are uploaded.

## Small beta checklist

Please play normally; do not automate input.

- Bronze dagger: name, image, and stab attack **+4**. Compare a drop table against the Wiki: item, quantity, exact odds and notes should stay together.
- Recipe for Disaster: starting requirements separate from full completion; inspect an item and dismiss without losing your place. Follow a link, Back, and check position.
- Skill guide: current-level positioning, both members/free rows dimmed consistently; manually scrolling should remain possible.
- Pin several passages, page, move, return to source and remove them. Close the sidebar: pins remain visible.
- Map: Cook's Assistant points, Hans region, Farming/Patch locations groups, and a dungeon image. Other plugins' markers must survive.
- With Menu Entry Swapper/core Wiki enabled, test inventory/bank/equipment/NPC/object menus and quest Wiki guide actions. Ordinary game actions should be unchanged.
- Log out/in, hop worlds, resize, disable/re-enable WikiPane, and close an image or sidebar during loading. Check for leftover UI, stale character checks or browser launches.

## Updating / leaving beta

Close RuneLite. Remove the previous JAR entry in Cabbage and add the new JAR; reselecting authorizes its new checksum. Do not enable two WikiPane versions. Launch again; the beta profile remains. Turn developer-plugin mode off to return to your normal RuneLite profile.

## Reporting a problem

Use [WikiPane issues](https://github.com/birdturtle/WikiPane/issues). Include beta version, RuneLite version (from BUILD.txt/client), OS/UI scale, article and variant/section, expected vs actual behavior, and exact steps. A cropped screenshot is useful. Review logs/screenshots and redact character names, chat and private data; never attach credentials or session files. Say whether the issue also occurs after re-enabling WikiPane.
