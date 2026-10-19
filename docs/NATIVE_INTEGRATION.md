# Native gameplay integration

Current implementation reference. For release status and testing, see [implementation](IMPLEMENTATION.md) and [testing](TESTING.md).

## Implemented

- `knowledge/KnowledgeService` resolves exact Wiki lookups and reads the existing cached `WikiDocument`. It does not construct Swing components or the full article view.
- `KnowledgeArticle` exposes the existing semantic nodes, facts, structured requirements, relationships, source sections and locations. Selection respects the Wiki's entity-ID mapping and redirect anchor. Unknown variants omit variant-specific nodes.
- `Inspection` selects a compact projection with unchanged rich values, source units and revision references. It does not summarize or infer missing values. Relevant section links and the full article retain access to unselected information.
- `ContextMenuIntegration` snapshots canonical item IDs, transformed NPC/object IDs and object locations on the client thread. Local callbacks are added only when a right-click menu opens. No gameplay actions are replaced, reordered, consumed or sent to the server. The existing target-selection mode is left alone.
- `QuickInspectController` runs resolution, parsing, layout and image decoding on the bounded worker pool. Client-thread completions check a generation token. Superseded queued work is canceled and removed; dismiss, logout, hopping and shutdown invalidate pending completions.
- `QuickInspectOverlay` uses real RuneLite overlay menu callbacks and a native related-article submenu. RuneLite currently exposes these overlay actions on Shift-right-click. Alt-drag positioning belongs to RuneLite. Footer controls use explicit hit-tested MouseManager listeners; other controls use supported overlay menus. No synthetic input is used.
- Sidebar entity lookups use `KnowledgeService` as well. Full reader presentation and navigation remain unchanged.

Menu Entry Swapper uses its own `MenuOpened` callbacks. WikiPane runs after its normal-priority callback and only adds local entries. It does not capture Shift, change swap settings, or mutate the event's original entries. Actual coexistence still requires player testing.

Repository instructions require third-party-server features to be opt-in. Contextual lookup starts disabled; once enabled, Shift-right-click is the default. Ordinary right-click includes Shift as well, so it also provides both entry points.

## Presentation and policy boundaries

The overlay shares the reader's palette and compact label/value conventions. Lines are prepared on the worker, grouped with their values, wrapped to 198 pixels and paginated to 12 lines. The panel is 210 pixels wide, with RuneLite's native small font and standard translucent background. Full requirements over 1,200 characters get an explicit full-article notice instead of a truncated logical expression. All source requirements remain available from the query model and full reader.

Monster overlays deliberately project static source statistics only. They do not project strategy prose, preparation requirements, combat prayer recommendations or live encounter cues. Wiki articles remain available in the full reader. This is not a claim of Plugin Hub approval.

The original lookup/Quick Inspect milestone reads no player state; local reader comparisons were added separately in [PLAYER_CONTEXT.md](PLAYER_CONTEXT.md). No player state is transmitted. Wiki requests contain the selected entity identity and, for location-sensitive lookups, its location. There are no hover-triggered downloads, scene scans, inventory snapshots or background tracking.

## Test before continuing

1. Enable contextual lookup. With Menu Entry Swapper enabled, Shift-right-click an inventory herb seed. Confirm both plugins' menu actions remain available and ordinary left-click behavior is unchanged.
2. Inspect ranarr seed. Check Farming 32, growth time, crop, source image, and related Herblore/crop links against the Wiki. Confirm the browser stays closed.
3. Repeat with bank, equipment, ground item, NPC and object menus. Check transformed/variant NPCs and location-sensitive objects. Interfaces without reliable item metadata should offer no action.
4. Read Recipe for Disaster, scroll, inspect an item and dismiss it. The article and scroll must stay put. Open the inspection's full article, then Back; confirm restoration.
5. Shift-right-click the panel. Test full article, related submenu, paging, retry and dismiss. Alt-drag the panel. Check normal and small game sizes manually.
6. Inspect rapidly, log out, hop, disable/re-enable the plugin, and disable contextual lookup during a request. No stale panel, extra overlay, competing target mode or browser should appear.
7. Test failed Wiki requests and unavailable images. A failed image should leave facts readable; a failed lookup should offer retry, browser lookup and dismiss.

These game interactions are not automated. The isolated preview is not an in-game acceptance test.

## Delivered integrations and deferred options

Shared knowledge access, contextual inspection, selection pins, local requirements, quest/skill entry points and map integration are implemented. See [map integration](MAP_INTEGRATION.md) for current location support. Manual checklists, persisted pins and inventory/equipment ownership checks are deferred options, not claimed beta features.

## Developer commands

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew liveCheck
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew inspectPreview -Pcapture=/tmp/wikipane-inspect.png
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew inspectPreview -Particle=Vorkath -PentityId=8061 -Pcapture=/tmp/wikipane-npc.png
```

`inspectPreview` paints only the offline overlay, without launching or interacting with RuneScape. Sources and revisions are listed in `THIRD_PARTY.md`.

## Minimap ownership

The former `WikiButtonIntegration` and target-selection resolver are removed. WikiPane never installs, hides, repositions, replaces or changes callbacks on RuneLite Wiki widgets. There are no WidgetLoaded, Wiki-icon-script or core-plugin-change hooks and no per-tick minimap reconciliation. Core Wiki settings are not changed.

The existing `replacement` config key now controls only supported quest/skill Wiki links. Its displayed label is **Quest and skill Wiki links**. The removed `leftSearch` key is cleaned up once per profile; existing quest/skill and contextual lookup preferences are retained.

The local packaged-API audit rejects Widget mutators and client target-selection setters, including negative classfile fixtures. In-game checks still need to cover the normal minimap Wiki lookup/search/DPS actions, hide preference, fixed/resizable layouts, core Wiki enable/disable and WikiPane lifecycle alongside separate contextual menus.
