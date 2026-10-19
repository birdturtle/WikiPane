# Third-party material

## RuneLite

The widget selection implementation in `WikiButtonIntegration` and entity lookup behavior are adapted from RuneLite WikiPlugin by Abex and RuneLite contributors.

Source: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/wiki/WikiPlugin.java

/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

## jsoup

jsoup 1.23.2, Copyright Jonathan Hedley, MIT license. Used for in-memory HTML parsing only. https://jsoup.org/license

## OSRS Wiki

Article fixtures and displayed text: OSRS Wiki contributors, CC BY-NC-SA 3.0. Layout normalization is an adaptation. Images have separate terms, including Jagex copyright.

https://oldschool.runescape.wiki/w/RuneScape:Copyrights

Fixture revisions, captured 2026-10-16:

- Bronze dagger, revision 15320184, https://oldschool.runescape.wiki/w/Bronze_dagger
- Recipe for Disaster, revision 15373729, https://oldschool.runescape.wiki/w/Recipe_for_Disaster
- Vorkath, revision 15364808, https://oldschool.runescape.wiki/w/Vorkath
- Cook's Assistant, revision 15369640, https://oldschool.runescape.wiki/w/Cook's_Assistant

- Evergreen tree, revision 15285567, https://oldschool.runescape.wiki/w/Evergreen_tree
- Rake, revision 15184295, https://oldschool.runescape.wiki/w/Rake
- Ranarr seed, revision 15333230, https://oldschool.runescape.wiki/w/Ranarr_seed
- Cooking, revision 15359642, https://oldschool.runescape.wiki/w/Cooking
- Vorkath/Strategies, revision 15368595, https://oldschool.runescape.wiki/w/Vorkath/Strategies
- Recipe for Disaster/Full guide, revision 14460980, https://oldschool.runescape.wiki/w/Recipe_for_Disaster/Full_guide

The offline reader preview includes the Wiki's Vorkath and Bronze dagger PNGs. Their exact source URLs are recorded in `src/test/resources/wiki/images/sources.json`. Game imagery is © Jagex Ltd.

Recipe fixture captured 2026-10-17: Prayer potion, revision 15374387, https://oldschool.runescape.wiki/w/Prayer_potion . Used to validate explicit recipe inputs, outputs, skill levels, quantities and source notes under the Wiki attribution above.

Alternative-method fixture captured 2026-10-17: Bronze bar, revision 15315107, https://oldschool.runescape.wiki/w/Bronze_bar . Standard smelting, Blast Furnace and Superheat methods retain their separate source conditions.

Shared table-layout regression fixture captured 2026-10-17: Sailing/Level up table, revision 15369359, https://oldschool.runescape.wiki/w/Sailing/Level_up_table . Used under the Wiki attribution above.

Shared table-layout regression fixture captured 2026-10-17: Farming/Level up table, revision 15369346, https://oldschool.runescape.wiki/w/Farming/Level_up_table . Used under the Wiki attribution above.

Shared table-layout regression fixture captured 2026-10-17: Herblore/Level up table, revision 15369351, https://oldschool.runescape.wiki/w/Herblore/Level_up_table . Used under the Wiki attribution above.

Map regression fixture captured 2026-10-17: Hans, revision 15373142, https://oldschool.runescape.wiki/w/Hans . Used under the Wiki attribution above.


Map-data regression fixture captured 2026-10-18: Cook's Assistant, revision 15375073, https://oldschool.runescape.wiki/w/Cook%27s_Assistant . `Cooks_Assistant_maps.json` retains the API's rendered text and `jsconfigvars` map features. Attribution and content terms are as above. Wiki map imagery fetched at runtime is © Jagex Ltd.; served by the OSRS Wiki at https://maps.runescape.wiki/ . No map tiles are bundled.

The map tile protocol and Simple projection were checked against the Wiki's published Kartographer extension: https://github.com/weirdgloop/mediawiki-extensions-Kartographer/tree/weirdgloop/REL1_45 . WikiPane independently implements bounded viewport composition and numbered markers; no extension code or JavaScript is loaded or distributed.


Additional complete API fixtures captured 2026-10-18 for the map coverage audit, under the Wiki attribution and content terms above:

- Hans, revision 15373142: https://oldschool.runescape.wiki/w/Hans
- Bob's Brilliant Axes., revision 15084401: https://oldschool.runescape.wiki/w/Bob%27s_Brilliant_Axes.
- Farming/Patch locations, revision 15275974: https://oldschool.runescape.wiki/w/Farming/Patch_locations
- Varrock Sewers, revision 15273145: https://oldschool.runescape.wiki/w/Varrock_Sewers
- Taverley Dungeon, revision 15366418: https://oldschool.runescape.wiki/w/Taverley_Dungeon
- Tree Gnome Village, revision 15351851: https://oldschool.runescape.wiki/w/Tree_Gnome_Village

No map tiles are included in these fixtures. Region/route geometry is retained from the Wiki's `jsconfigvars` GeoJSON data.
