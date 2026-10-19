# Map coverage and UX audit

Captured from the official Wiki API on 2026-10-18; working branch `map-integration`.

| Article | Revision | Frames | Features | Main finding and change |
| --- | --- | ---: | ---: | --- |
| Hans | 15373142 | 1 | 1 area | A roaming region is a polygon. The outline/fill now survives; no spawn tile is invented. |
| Bob's Brilliant Axes. | 15084401 | 1 | 1 area | A shop footprint is an area, not an exact marker. Its preview stays usable. |
| Farming/Patch locations | 15275974 | 67 | 67 areas | Maps now group by source patch type, with table-derived location names and lazy disclosure bodies. |
| Varrock Sewers | 15273145 | 0 | 0 | The dungeon map is a raster image beneath an explicit Map header. It now appears in the map chooser despite having no map filename/alt label. |
| Taverley Dungeon | 15366418 | 1 | 2 points | Entrance preview, source point locations and full-resolution dungeon image remain available. Unsupported displayed-map coverage cannot replace an existing marker with an invisible selection. |
| Tree Gnome Village | 15351851 | 3 | 1 point, 2 routes, 4 areas | Map previews now retain source paths and regions, including route colors and named tracker areas. |

All audited region/route features are accessible through a source preview. Tests verify this independently of article-text coverage. Tile rendering is bounded and uses the existing cache; no map or player state is downloaded or scanned in the background.

## Framework changes

- `MapGeometry` validates Point, LineString, MultiLineString, Polygon and MultiPolygon geometry, with bounded coordinates/vertices and source stroke/fill/opacity. Unknown geometry remains raw source data and triggers the existing incomplete-map notice.
- Regions/routes stay distinct from exact destinations. Their preview labels (A1, R1, etc.) are visual references only and are never used as game coordinates. Numbered exact points continue to match their legend.
- Map frames retain a normalization identifier inside source table cells, allowing the map index to reuse the semantic row's explicit Location/Name fields. This avoids maintaining a separate HTML table parser for map labels.
- Source patch sections become parent groups when they contain multiple maps. Nested bodies/previews are created on demand. Long headings, legends and guidance wrap at the actual viewport width.
- Raster maps are identified from explicit Map headers/columns/captions or map filenames. Infobox screenshots and small map icons are excluded. Nested cell illustrations are included in the index.
- Only equivalent destination entries consolidate; distinct named entities at one tile remain distinct. Frame consolidation also includes source context, title/caption and tile namespace, preserving maps with different meanings.
- Known unsupported exact locations remain accessible through a preview or an explicitly labeled Wiki browser action. They are no longer silently filtered from the chooser. Show all is omitted when a group contains unsupported destinations.
- Native marker replacement waits for the sprite and displayed map data. Every requested point must be displayable before the previous owned selection is removed. Wrong-area requests retain previous markers, do not pan, and direct the player to the preview/source. Mixed groups are not silently truncated.

## API investigation and remaining limits

The inspected public RuneLite API exposes the current WorldMapData and `surfaceContainsPosition`, plus `initializeWorldMap` when an existing WorldMapData object is supplied. It does not expose a map-area enumerator/resolver or a world-to-surface conversion suitable for resolving arbitrary underground areas. This audit does not add guessed client area IDs, coordinate offsets, reflection or scripts with assumed parameters.

Automatic underground area switching therefore remains unsupported. A dungeon's Wiki image/preview is available independently; an exact point is accepted only when the currently displayed game map reports coverage. Areas/routes are preview content, not native pins. A source shape's center is never presented as an exact entity location. Unknown styles/geometry may still need additional normalization; raw feature data and explicit browser access remain available.

References inspected:

- https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/worldmap/WorldMap.java
- https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/worldmap/WorldMapData.java
- https://github.com/weirdgloop/mediawiki-extensions-Kartographer/tree/weirdgloop/REL1_45

## Validation

Offline tests use saved complete API responses, plus synthetic edge cases for Map-column images, duplicate locations and unsupported map coverage. Pixel checks verify actual polygon fill and route colors, not just successful HTTP requests. Swing fixture previews using real Wiki tiles were inspected for the NPC region and quest route map; no game input was automated.

Build and reports:

```sh
JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew build semanticReport mapCoverageReport verifyDeveloperPluginJar
JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew mapPreview -Particle=Tree_Gnome_Village_maps -Pcapture=/tmp/wikipane-quest-map.png
```

The isolated preview accepts `-PmapFrame=0` (frame index), and its larger raster is written to `/tmp/wikipane-map-detail.png`.

Player checks still required:

1. Hans: Map → Location map. Confirm the marked region is visible; there should be no fabricated exact-spawn action.
2. Farming/Patch locations: open a patch category and a named location. Confirm useful grouping, readable labels and loading only for opened previews.
3. Varrock Sewers or Taverley Dungeon: open the map image and verify full-size viewing. For an underground native request outside the current map, verify useful feedback and preservation of a previous valid marker.
4. Tree Gnome Village: inspect the maze routes and tracker regions; match route/area references to the legend.
5. Cook's Assistant: recheck ingredient points, Show all, native centering, source return, and page-only Back/Forward. Close during loading and repeat enable/disable/logout checks.
