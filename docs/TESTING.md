# Native gameplay integration

Original checkpoint: milestones A and B on `work/next-features`. Current extensions are on `gameplay-event-integration`.

## Implemented

- `knowledge/KnowledgeService` resolves exact Wiki lookups and reads the existing cached `WikiDocument`. It does not construct Swing components or the full article view.
- `KnowledgeArticle` exposes the existing semantic nodes, facts, structured requirements, relationships, source sections and locations. Selection respects the Wiki's entity-ID mapping and redirect anchor. Unknown variants omit variant-specific nodes.
- `Inspection` selects a compact projection with unchanged rich values, source units and revision references. It does not summarize or infer missing values. Relevant section links and the full article retain access to unselected information.
- `ContextMenuIntegration` snapshots canonical item IDs, transformed NPC/object IDs and object locations on the client thread. Local callbacks are added only when a right-click menu opens. No gameplay actions are replaced, reordered, consumed or sent to the server. The existing target-selection mode is left alone.
- `QuickInspectController` runs resolution, parsing, layout and image decoding on the bounded worker pool. Client-thread completions check a generation token. Superseded queued work is canceled and removed; dismiss, logout, hopping and shutdown invalidate pending completions.
- `QuickInspectOverlay` uses real RuneLite overlay menu callbacks and a native related-article submenu. RuneLite currently exposes these overlay actions on Shift-right-click. Alt-drag positioning belongs to RuneLite. No synthetic input or fake clickable painted controls are used.
- The minimap and sidebar entity resolver now use `KnowledgeService` as well. Full reader presentation and navigation remain unchanged.

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

## Remaining milestones

- C: selection-based pins and independent native overlay lifecycle are implemented for player testing. Highlight sidebar text, right-click, then Pin selection. See [Pinning](PINNING.md). Manual checklists and persistence are not implemented.
- D: client-thread snapshots and local met/unmet/unknown skill, quest-completion and quest-point annotations are implemented for player testing, preserving AND/OR and boosts. Inventory/equipment checks remain deferred.
- E: verified quest-journal and skill entry points routed through the shared service.
- F: map connections using validated coordinates, after earlier milestones are stable.

Selection pinning is available alongside Quick Inspect. Native text selection, overlay controls and lifecycle still require player testing. Local player-state annotations are ready for player testing; see [PLAYER_CONTEXT.md](PLAYER_CONTEXT.md).

## Developer commands

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew liveCheck
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew inspectPreview -Pcapture=/tmp/wikipane-inspect.png
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew inspectPreview -Particle=Vorkath -PentityId=8061 -Pcapture=/tmp/wikipane-npc.png
```

`inspectPreview` paints only the offline overlay, without launching or interacting with RuneScape. Sources and revisions are listed in `THIRD_PARTY.md`.

## Replacement-button regression checks

The replacement supports the game's disabled native entity-lookup setting, using RuneLite's existing Wiki-button positioning. A rebuilt parent must still own the installed child before the widget can be reused. Search menu actions are consumed explicitly, including low-priority actions and dynamic-child IDs; the native on-op listener remains a guarded fallback. Sidebar/Browser routing is separate from Search, which always opens WikiPane. Conflicts surface once in the sidebar instead of leaving an apparently active checkbox without guidance. Other plugins are never disabled automatically.

## Local requirements and contextual menu checks

See [PLAYER_CONTEXT.md](PLAYER_CONTEXT.md) for the manual sequence covering character levels, boosts, quest completion, quest points, logout/account changes, sidebar activation, pin selection and Menu Entry Swapper. Automated mocked-client and Swing tests do not establish in-game success.
