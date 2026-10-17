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

## Contextual lookup verification and local player requirements

`env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build playerContextPreview semanticReport` passes: 150 tests, zero failures, errors or skips. The eight-fixture source report still shows complete coverage, valid composition and zero composition warnings.

New tests cover base/current levels, allowed/forbidden/unspecified boosts and drains, recursive ALL/ANY logic, unavailable state, exact quest links and unsupported stages/variants, quest points without future rewards, source extraction confidence, immutable snapshots, client-thread enforcement, targeted/coalesced quest reads, logout/new-login state replacement, unavailable-script recovery, shutdown/disable/pending work, original-menu preservation, canonical item and transformed entity identity, modifier settings and stale menu callbacks. Swing tests cover status updates without replacing source panes or changing scroll, selection excluding player values, disabled checks, queued stale results, and wrapped-text geometry at 185 and 225 pixels.

The isolated 242 × 780 actual-sidebar preview uses synthetic Cooking, Agility, Mining, quest completion and quest-point values. It was inspected for readable icons/status, preserved source groups, correct met/unmet/unknown examples and full wrapped-text visibility. The first preview exposed a clipped final requirement line; width-aware row measurement fixed it, and a glyph-bound regression now checks source text as well as status bounds. No live game capture or game input was used.

Actual character-state comparisons, event timing, native menu coexistence and lifecycle behavior still require player acceptance. Partial quest stages, unfamiliar conditional prose, inventory/equipment possession and unsupported quest aliases remain unknown. Player state is local only; Wiki caches and pin contents remain account-independent. See `PLAYER_CONTEXT.md`.

RFD prerequisite regression: individual nested quest checks retain source text and ordered links; all extracted quest names match the RuneLite quest catalog. Completed parent quests check independently of prerequisite availability. Positive partial-stage requirements are satisfied only by verified full completion; intermediate stages remain unknown. Isolated synthetic-state preview inspected at 242px outer panel width (`playerContextPreview`); in-game confirmation remains required.

## Reader UX follow-up

The final `test build` run passes 163 tests. Added coverage includes source-backed related links, record identity and membership normalization, compact records and icons at narrow widths, drop/reference destination separation, exact header anchors across page types, dropdown navigation and scroll history, and thumbnail geometry stability after decoding. Player visual feedback informed the compact layouts, icons, membership colors and single dropdown. The latest right-edge arrow adjustment was accepted in-game.

## Structured recipe checkpoint

`test build` passes 172 tests. Live-captured Prayer potion and Bronze bar fixtures, plus existing Bronze dagger variants, validate explicit ingredients, quantities, outputs, skills/XP, boost handling, conditions and source provenance. Mutated fixtures verify separate methods and generic fallback for missing boundaries, ambiguous alternatives and unsupported conditions. UI coverage includes links, source details, pin source ownership and 185/225px layout. Mocked Quick Inspect method changes perform no new HTTP request and ignore completion after dismissal. This checkpoint still requires player confirmation of variant selection, account skill annotations, native method menus and pin UX. See RECIPES.md.

The ten-fixture semantic report passes complete source coverage with zero composition warnings, including both new fixtures. Offline reader previews were inspected for Prayer potion, Bronze dagger and Bronze bar; Quick Inspect was inspected with the resolved three-dose potion ID. These remain isolated UI checks, not gameplay acceptance.

The player reviewed the recipe checkpoint in the development client and confirmed it looked good on 2026-10-17. This records general in-game acceptance; the automated checks above remain the detailed evidence for individual contracts.
