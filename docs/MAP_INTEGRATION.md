# Source-backed world map actions

The reader offers contextual map actions for qualifying semantic locations, preserving its article, history and reading position. Quest-start actions sit directly beneath the starting-location fact; spawn/shop actions belong to their table record. Multiple verified points in one record form one selection; duplicate tiles in that selection are consolidated, with planes kept distinct. Another selection replaces the previous group. There is no separate removal control; lifecycle cleanup removes the entire owned group.

`MapDestination` is a presentation projection of existing semantic locations and facts. It retains the exact Location/Source objects; it does not parse pages again or infer coordinates. Labels use explicit fact labels, linked person/place names, and table identities. Groups are limited to 64 markers; an oversized request is rejected as a whole rather than truncated. A group centers its first source point; automatic fit-to-group zoom is not exposed by the inspected public API. Only single markers snap to map edges, avoiding a pile of overlapping edge flags for groups.

## Provenance and validation

Previously, coordinates were normalized into prose and rediscovered by a text regex. That is insufficient evidence for native markers. The parser now retains generated metadata from actual `.mw-kartographer-maplink` elements: longitude as x, latitude as y, explicit plane and Wiki map ID. Semantic locations keep the original source unit and verified-map provenance. Ordinary coordinate-like prose remains unverified. Generic and nested table cells are included, not only infobox facts.

The native destination validator requires integral bounded coordinates, a known plane 0–3, and source map ID 0 or -1 (main/automatic map context). It rejects fractional coordinates, missing plane/map ID, malformed numbers, nonpositive/out-of-range coordinates and other map namespaces. Unknown locations remain readable but have no speculative map action. Hans is a negative fixture: its embedded map's viewport center is not promoted to a verified NPC spawn. Cook's Assistant's explicit quest-start link is the initial positive case (3208, 3216, plane 0).

Source prose, links, table content and coordinates are retained. No hardcoded article coordinates select production behavior. No related pages are crawled. Native marker selection needs no tiles; optional Wiki map previews fetch only their bounded viewport as described below.

## RuneLite interaction

Uses the current WorldMapPointManager/WorldMapPoint APIs, WorldMap.setWorldMapPositionTarget, and gameval Worldmap.MAP_DISPLAY. All game/map reads and mutations occur on the client thread. Swing callbacks dispatch through the plugin's existing lifecycle generation guard. Marker feedback returns to the EDT without replacing the article.

The revised action uses Client.openInterface on the native WORLDMAP interface in the matching fixed/resizable FLOATER layer (NON_MODAL), provided that layer is unoccupied. An already-open native map is reused. The returned WidgetNode is tracked by identity against the component table; native close-button actions close only WikiPane's owned node, and shutdown never closes a map opened by the player or a replacement node. No menuAction, synthetic input, packets, guessed script arguments or custom map renderer are used. The inspected orb on-op handler only plays a sound, so executing that callback was explicitly rejected as an ineffective opening strategy. Native opening/closing behavior still requires in-game confirmation.

The marker uses SpriteManager's cached native MAPMARKER frame 0 destination-flag sprite asynchronously. Client-thread callbacks carry a generation token so late sprite results cannot restore replaced or shutdown markers. No custom marker bitmap is drawn. Pending centering checks only the map widget/data on game ticks; after centering it stops changing map position. Current map coverage is checked before centering; unrelated map areas are left untouched with feedback.

Controls use Show quest start, Show spawn/Show spawn locations, Show shop/Show shops, or a generic Show on map/Show locations, with source context in the tooltip. Map coordinates remain available in the semantic model, but supported coordinate spans are removed from the display projection; source facts and provenance are unchanged. Marker names identify their source-backed destination, and wrapped plain-text tooltips retain the article/section without coordinate strings. Successful actions do not add a persistent status banner. Guidance labels correctly enable escaped HTML wrapping rather than exposing raw markup.

The plugin clears its marker on logout, world hopping and shutdown and never removes another plugin's points. Stale callbacks cannot restore a marker after shutdown. Sidebar closure/article changes do not remove a deliberately selected marker. No persistence, scene scan, account context or telemetry is involved.

## Validation and player checks

Automated tests cover real quest-start provenance, record-cell provenance, viewport-center rejection, map IDs, invalid/fractional coordinates, unknown planes, marker replacement/removal, delayed map opening, one-time centering, wrong-area handling, logout/disabled state and client-thread enforcement. A Swing test verifies source-backed controls send the selected location without invoking article navigation and have no extra removal control or coordinate-bearing caption. Existing source-coverage and reader tests remain required.

Player verification:

1. Open Cook's Assistant, locate its source starting information and Show on map control. Click with the game map closed. Confirm the native map opens and the article/reading position remain unchanged.
2. Confirm a WikiPane marker and centering at the Wiki's stated quest-start coordinate in Lumbridge. Verify the tooltip and compare the source Wiki map.
3. Repeat with the map already open. Select another supported source location and verify only the WikiPane marker is replaced.
4. Close the native map with its X button and reopen through another lookup. Check normal closing behavior and that other map points remain. Close/reopen the sidebar and test Back/Forward and pinning.
5. Logout/login, world hop and disable/re-enable WikiPane; verify no orphaned marker or delayed recenter.

Known limits: automatic local map opening currently targets fixed and resizable desktop floating-map layers only; occupied/unknown layers are not overwritten. no guessed locations, no generic embedded-map center conversion, no native mapping for unsupported Wiki map IDs/instances, no automatic map-area switching, no route planning, world-map-to-article access is limited to WikiPane-owned flags. In-game marker geometry, map-load timing and visual usability remain unverified until player acceptance.

Primary APIs inspected: https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/overlay/worldmap/WorldMapPoint.java and WorldMapPointManager.java; installed RuneLite 1.13.2 WorldMap/WorldMapData and gameval interfaces. ScriptID.WORLDMAP_LOADMAP takes nine integer arguments and is not used as an assumed open-map API.

Revised UX acceptance pending: automatic native opening, game location sprite, coordinate-free controls/display and replacement-only marker interaction. Initial marker positioning was already accepted functionally, but this revised opening/closing path needs a fresh player check.

Player testing confirmed automatic map opening and positioning. The marker has since changed to the native red destination flag to distinguish it from the player-location indicator; its appearance awaits a fresh visual check.

The native destination flag is enlarged 2× using nearest-neighbor scaling once when the sprite arrives, preserving crisp pixel art without per-frame image work. Player feedback found the original size too small among other map symbols.


## Contextual map selection — map-integration branch

Right-clicking an owned flag adds **Read in WikiPane** alongside the existing native **Focus on** action. This uses MenuOpened and a RUNELITE-only action, matched to the owned marker name; it does not replace the native action, add server actions, or change other plugins' marker menus. A generation guard rejects menus from replaced selections or shutdown. The callback uses the existing article router and source section URL. Same-page source navigation remains within the current history entry; different articles use normal page history.

Public API inspection (RuneLite 1.13.2 and upstream source) found WorldMap.getWorldMapData, initializeWorldMap and setWorldMapPositionTarget, but no Client/WorldMap API to enumerate or resolve map areas, and WorldMapData only exposes surfaceContainsPosition. Automatic underground map-area selection remains unsupported in this slice. Wiki map IDs other than 0/-1 remain excluded from native conversion; no mapping from a Wiki map namespace to a client map area is invented. Generic embedded-map viewport centers remain unverified. These are explicit remaining tasks, not implemented features.

Additional automated checks cover real Cook's Assistant labels/provenance, one control for a multi-point table record, exact-tile deduplication, grouped marker ownership/removal, source-menu navigation, stale menu rejection, and exclusion of other plugins' focus entries. The approved 2× native flag is unchanged. Manual tests still required:

1. Cook's Assistant: use Show quest start directly below Start point. Confirm closed-map opening, accurate centering, useful tooltip, and unchanged sidebar position.
2. Right-click the flag: verify Read in WikiPane and Focus on both work. Read another article first, then return through the flag and verify Back/Forward remain page-based.
3. Vorkath Spawns: verify Show spawn and a marker named for its actual location. Select another location and confirm previous WikiPane markers disappear.
4. A record containing multiple supported exact points: verify a single action places the group; selecting a different destination removes all of it. Group source menus and logout/shutdown must remain safe.
5. Rake's underground Wiki map ID remains unsupported: no guessed native marker should appear. Use the explicit browser fallback when necessary.

References inspected: https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/worldmap/WorldMap.java , WorldMapData.java, and https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/ui/overlay/worldmap/WorldMapOverlay.java .


## Article map chooser and Wiki map previews

The API parse request now includes `jsconfigvars`. `WikiMapExtractor` preserves `.mw-kartographer-map` frames and actual `wgKartographerLiveData` feature collections, with article/revision provenance, captions, groups and original feature JSON. A frame center describes a viewport, never an entity spawn. Point features use continuous tile coordinates: native destinations select the containing tile by floor, and previews use the original coordinates. Explicit map links retain integral validation. Unsupported geometry/provider data is retained as raw metadata and produces an incomplete-map notice, rather than an invented pin.

`ArticleMaps` indexes source maps, record destinations, facts and identifiable ordinary map images into article-scoped groups. It never crawls related articles or creates a global POI database. The persistent Map control opens a width-constrained chooser beside section navigation. Individual destinations open the game map; Show all replaces the current selection with the group's points (maximum 64). Large lists append 30 controls at a time. Existing inline map actions remain available. Different source coordinates remain distinct even if their labels look similar.

`MapImageCache` requests allowlisted versioned PNG paths on maps.runescape.wiki: HTTPS, no credentials/query/fragment and no redirects. It composes the viewport using the Wiki's Simple projection (2^zoom pixels per tile, 256-pixel image tiles). Thumbnails use source zoom. The viewer requests a 2× detail crop at the next zoom for frames up to 512×512 when zoom is below 5; larger frames retain their source crop. Each composition is limited to 36 tiles and 1200×1200 pixels. Tile bytes and dimensions are checked before decode. Ten-minute caches hold at most 32 tiles and 4 compositions. No world-map download, disk cache, scripts or arbitrary CSS execution.

The existing image viewer retains fit/zoom/pan, retry, explicit browser access and lifecycle cancellation. Numbered points match the source-derived legend. Unrecognized icon styles receive neutral coloring without invented meaning. Incomplete feature data is identified in the thumbnail and larger view. Inline previews request images only when visible. Closing the chooser cancels pending preview work; stale results cannot update it. Gold wrapping navigation buttons are shared with the article reader.

Automated checks cover a saved live Cook's Assistant response, three ingredient points distinct from the viewport center, source captions, unsupported geometries, strict tile URLs, decode limits, failed-request cache behavior, higher-detail crops, marker placement, source-section previews and a 209-pixel chooser. An isolated Swing preview using real Wiki tiles confirmed readable controls and ingredient-map geometry. This does not verify game interactions.

Manual checks:

1. Cook's Assistant: Map remains beside the section selector while scrolling. Expand Ingredient locations and click its preview. Check matching numbered points/legend and fit/zoom/pan.
2. Choose Chicken coop, Dairy cow field, Wheat field, then Show all on game map. Check centering, complete marker replacement, three group flags and unchanged sidebar position.
3. Return through a marker's source menu; verify page history, search, pinning, section jumps and ordinary full-resolution images still work.
4. Close during loading, reopen, change articles rapidly, logout and disable/re-enable. Check for stale updates, orphaned markers or leftover windows.
5. Unsupported underground maps and missing feature data retain preview/browser access without invented native coordinates.

`JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew mapPreview` captures /tmp/wikipane-map-chooser.png and /tmp/wikipane-map-detail.png using the saved fixture and its visible Wiki-controlled tiles. It never starts or interacts with RuneScape.
