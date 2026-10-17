# Source-backed world map actions

The reader offers Show on map for qualifying semantic locations, preserving its article, history and reading position. Actions belong to their source table record or prose/infobox component. Multiple source locations remain separate actions; repeated coordinates within one control group are consolidated. One session marker is active at a time. There is no separate removal control; another lookup replaces the selected marker, and lifecycle cleanup removes it.

## Provenance and validation

Previously, coordinates were normalized into prose and rediscovered by a text regex. That is insufficient evidence for native markers. The parser now retains generated metadata from actual `.mw-kartographer-maplink` elements: longitude as x, latitude as y, explicit plane and Wiki map ID. Semantic locations keep the original source unit and verified-map provenance. Ordinary coordinate-like prose remains unverified. Generic and nested table cells are included, not only infobox facts.

The native destination validator requires integral bounded coordinates, a known plane 0–3, and source map ID 0 or -1 (main/automatic map context). It rejects fractional coordinates, missing plane/map ID, malformed numbers, nonpositive/out-of-range coordinates and other map namespaces. Unknown locations remain readable but have no speculative map action. Hans is a negative fixture: its embedded map's viewport center is not promoted to a verified NPC spawn. Cook's Assistant's explicit quest-start link is the initial positive case (3208, 3216, plane 0).

Source prose, links, table content and coordinates are retained. No hardcoded article coordinates select production behavior. No related pages are crawled and no remote map images/tiles are downloaded by WikiPane.

## RuneLite interaction

Uses the current WorldMapPointManager/WorldMapPoint APIs, WorldMap.setWorldMapPositionTarget, and gameval Worldmap.MAP_DISPLAY. All game/map reads and mutations occur on the client thread. Swing callbacks dispatch through the plugin's existing lifecycle generation guard. Marker feedback returns to the EDT without replacing the article.

The revised action uses Client.openInterface on the native WORLDMAP interface in the matching fixed/resizable FLOATER layer (NON_MODAL), provided that layer is unoccupied. An already-open native map is reused. The returned WidgetNode is tracked by identity against the component table; native close-button actions close only WikiPane's owned node, and shutdown never closes a map opened by the player or a replacement node. No menuAction, synthetic input, packets, guessed script arguments or custom map renderer are used. The inspected orb on-op handler only plays a sound, so executing that callback was explicitly rejected as an ineffective opening strategy. Native opening/closing behavior still requires in-game confirmation.

The marker uses SpriteManager's cached native MAPMARKER frame 0 destination-flag sprite asynchronously. Client-thread callbacks carry a generation token so late sprite results cannot restore replaced or shutdown markers. No custom marker bitmap is drawn. Pending centering checks only the map widget/data on game ticks; after centering it stops changing map position. Current map coverage is checked before centering; unrelated map areas are left untouched with feedback.

Controls show only Show on map, with source attribution in the tooltip. Map coordinates remain available in the semantic model, but supported coordinate spans are removed from the display projection; source facts and provenance are unchanged. Marker names/tooltips identify the article without coordinate strings. Successful actions do not add a persistent status banner. Guidance labels correctly enable escaped HTML wrapping rather than exposing raw markup.

The plugin clears its marker on logout, world hopping and shutdown and never removes another plugin's points. Stale callbacks cannot restore a marker after shutdown. Sidebar closure/article changes do not remove a deliberately selected marker. No persistence, scene scan, account context or telemetry is involved.

## Validation and player checks

Automated tests cover real quest-start provenance, record-cell provenance, viewport-center rejection, map IDs, invalid/fractional coordinates, unknown planes, marker replacement/removal, delayed map opening, one-time centering, wrong-area handling, logout/disabled state and client-thread enforcement. A Swing test verifies source-backed controls send the selected location without invoking article navigation and have no extra removal control or coordinate-bearing caption. Existing source-coverage and reader tests remain required.

Player verification:

1. Open Cook's Assistant, locate its source starting information and Show on map control. Click with the game map closed. Confirm the native map opens and the article/reading position remain unchanged.
2. Confirm a WikiPane marker and centering at the Wiki's stated quest-start coordinate in Lumbridge. Verify the tooltip and compare the source Wiki map.
3. Repeat with the map already open. Select another supported source location and verify only the WikiPane marker is replaced.
4. Close the native map with its X button and reopen through another lookup. Check normal closing behavior and that other map points remain. Close/reopen the sidebar and test Back/Forward and pinning.
5. Logout/login, world hop and disable/re-enable WikiPane; verify no orphaned marker or delayed recenter.

Known limits: automatic local map opening currently targets fixed and resizable desktop floating-map layers only; occupied/unknown layers are not overwritten. no guessed locations, no generic embedded-map center conversion, no native mapping for unsupported Wiki map IDs/instances, no automatic map-area switching, no route planning, no world-map-to-article menu yet. In-game marker geometry, map-load timing and visual usability remain unverified until player acceptance.

Primary APIs inspected: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/overlay/worldmap/WorldMapPoint.java and WorldMapPointManager.java; installed RuneLite 1.13.2 WorldMap/WorldMapData and gameval interfaces. ScriptID.WORLDMAP_LOADMAP takes nine integer arguments and is not used as an assumed open-map API.

Revised UX acceptance pending: automatic native opening, game location sprite, coordinate-free controls/display and replacement-only marker interaction. Initial marker positioning was already accepted functionally, but this revised opening/closing path needs a fresh player check.

Player testing confirmed automatic map opening and positioning. The marker has since changed to the native red destination flag to distinguish it from the player-location indicator; its appearance awaits a fresh visual check.

The native destination flag is enlarged 2× using nearest-neighbor scaling once when the sprite arrives, preserving crisp pixel art without per-frame image work. Player feedback found the original size too small among other map symbols.
