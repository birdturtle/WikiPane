# Semantic reader validation

2026-10-16, Java 11, RuneLite dependency 1.13.2.

Original semantic implementation: 39 passing tests. See the validation-pass update below for the current result.

## Fixture coverage

| Article | Semantic source units | Units assigned to reader areas |
| --- | ---: | ---: |
| Bronze dagger | 673 | 673 |
| Recipe for Disaster | 112 | 112 |
| Vorkath | 1071 | 1071 |
| Cooking | 1417 | 1417 |
| Vorkath/Strategies | 374 | 374 |
| Recipe for Disaster/Full guide | 458 | 458 |

A source unit is a meaningful normalized paragraph, cell, or image, including nested tables. Headings are retained separately as section hierarchy. Tests compare source cell values and geometry against semantic data, check source text and facts against the view model, and verify selected requirements and statistics. These counts do not prove that normalization recognizes every possible Wiki structure.

The full-guide Walkthrough area contains 182 components and 42,855 characters. Vorkath's Drops area contains 20 components and 15,015 characters; its reference definitions now live in References. Separate linked articles remain separate navigable pages.

Isolated Swing tests exercise complete drop-table pagination, complete walkthrough materialization, visible area navigation, stale requests, failed-request retention, and Recipe for Disaster → Cook's Assistant → Back/Forward with scroll restoration. Requirement tests cover explicit quantities, alternatives, boost qualifiers, and unknown prose. Monster highlights are checked against each source variant.

Standalone previews were inspected at 242 × 780 pixels with dark styling. Requirements and drop text wrap at sidebar width. This inspection did not interact with RuneScape.

## Still needs player review

- Ordinary reading and switching areas inside the actual RuneLite sidebar.
- Scrolling long guides, following section links, and Back/Forward after switching variants.
- Replacement-button targeting, transformed entities, and widget lifecycle across game layouts.
- Responsiveness and memory during extended ordinary gameplay.

Unusual interactive templates, calculators, media, and arbitrary embedded anchors may still need the browser. General fallback preserves unclassified normalized content; it is not a guarantee of universal Wiki fidelity.

## Presentation framework refactor

Final verification: `./gradlew test build` successful; 45 tests, zero failures or errors. A clean build also passed before the final source-heading adapter cleanup.

A reusable `ReaderLayout` adapter and `ReaderComponents` library now separate component content from Swing styling. Added tests compare every table field against its presentation role across six real fixtures, check record identities and linked illustration descriptions, preserve requirement scopes, exercise record links, and verify fact-panel bounds at 185/225/300 pixels. A separate regression checks actual line positions across an explicit HTML line break.

Standalone previews inspected item bonuses, monster facts, drop records, and quest requirement groups. These are local Swing previews, not game screenshots or in-game acceptance.

## Presentation validation pass

`./gradlew test build semanticReport`: successful, 59 tests, zero failures or errors. A clean build also passed during this validation pass. Fault injection covers missing source destinations, altered statistics, invalid reference components, dropped requirement groups, duplicate IDs, and misplaced references. Native tests cover citation → reference → backlink → Back navigation, narrow-field stacking, highlight fallback, overflow/clipping diagnostics, and complete-image fitting.

All six fixture audits retain their source-unit coverage. Reference definitions match the raw Wiki HTML: Vorkath 13, Vorkath/Strategies 13, and Recipe for Disaster/Full guide 1. Reference lists no longer become walkthrough steps. A normal numbered instruction containing `[1]` remains an instruction.

Standalone reference and drop previews were inspected at 242 × 780. These are visual checks of isolated Swing panels, not in-game acceptance or automatic aesthetic scoring. Whole-page pixel baselines are not part of the suite; the preview command emits a screenshot and the current audit for manual regression comparison.

The final standalone Vorkath drop view measured 216 pixels, inspected 43 visible components, and reported no geometry warnings. This is one measured view, not whole-Wiki visual certification.

## Contextual navigation

`./gradlew clean test build semanticReport`: successful, 72 tests, zero failures or errors.

Added checks cover page-type mode selection, complete source-area reachability through Sections, search-result history and forward branching, persistent identity/controls, hidden reading-state search, per-mode scroll restoration, direct section history, section filtering/Enter, parent-chapter materialization, and source-backed transclusion hierarchy.

Standalone previews inspected the quest overview and full-guide walkthrough at 242 × 780. Article identity is outside the scroll pane, with no repeated dedicated title in the reader. Source-unit coverage remains complete for the existing fixtures. In-client navigation acceptance remains a player test.

## Native gameplay integration: first checkpoint

`./gradlew test build` passes: 85 tests, zero failures or errors. This includes the existing reader tests and new knowledge-query, exact-variant filtering, seed facts, compact provenance, stale response, dismissal/shutdown, queue rejection, retry bounded overlay-layout tests, and rejection of comparison-table values as entity statistics. A Swing integration test verifies that temporary inspection leaves Recipe for Disaster at its existing scroll position, then explicit full-article navigation and Back restore it.

Live Wiki checks passed for exact ranarr seed item lookup and Vorkath NPC ID 8061, resolved to its Post-quest variant with 750 Hitpoints. Existing live search, article and image checks also passed. Offline previews inspected the 280-pixel seed panel and post-quest Vorkath statistics. They did not interact with RuneScape.

The seed fixture exposed a parser bug: a table heading could inherit the preceding row's requirement label. Header-only text no longer creates that false condition; actual skill markup remains eligible. Compact projection also avoids repeating the same source value as both a fact and a requirement. Source cell coverage now includes the captured ranarr seed article.

Native menu access, Menu Entry Swapper coexistence, overlay menu hit testing, positioning, all supported item interfaces and game lifecycle still require player testing. See `NATIVE_INTEGRATION.md`. Pins, player-state evaluation, quest/skill entry points and map integration are not implemented at this checkpoint.


## Replacement-button and compact overlay fixes

`./gradlew test build` passes: 97 tests, zero failures or errors. Before the fix, widget fixtures reproduced three failures: no replacement when vanilla entity lookup is disabled, a detached widget reused after reconstruction, and Search not handled by the menu callback. These tests now pass. Additional fixtures check low-priority dynamic-child search, left-click Search's action index, exact ground-item identity routing, parent layout restoration, stale callbacks after shutdown, foreign-widget ownership, conflict reporting and explicit destination switching. They mock RuneLite APIs; they do not interact with the game.

Quick Inspect now uses RuneLite's native small font and `BackgroundComponent` with its standard translucent background. It is 210 pixels wide with 6-pixel padding, 13-pixel line spacing and 12-line pages. Pixel checks verify translucency as well as wrapping and bounded dimensions. An isolated Evergreen tree variant 3 preview measures 210 × 151 pixels. Wiki navigation notices retain semantic metadata and remain available in the full reader, while the compact About projection selects actual article content. Numeric variant labels are explicit.

Both fixes still need player confirmation in RuneLite, including minimap lookup, both Search interactions, interface resizing/reconstruction, and comfortable overlay positioning during gameplay. Mockito is test-only and is not a production plugin dependency.

## Selection-based reference pins

`env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build` passes: 114 tests, zero failures, errors or skipped tests. A preceding clean build and offline `pinPreview` also passed.

Tests cover source-backed selection across native reader components, links and emphasis, numbered and nested lists, field labels and partial-value excerpts, variant identity, Unicode whitespace, bounded long selections and paging, duplicate pins, the five-pin limit, shutdown with pending work, queue rejection and translucent overlay dimensions. A Recipe for Disaster reader test verifies that pinning leaves the article and scroll position unchanged without additional Wiki requests.

The isolated requirements pin preview measures 210 × 104 pixels and was inspected without launching or interacting with RuneScape. Actual mouse selection, native overlay menus, Alt-drag positioning, sidebar independence and login/world-hop behavior require user testing in RuneLite. Pins are session-only; manual checklists and player-aware annotations are not implemented.

## Clickable pin paging

`env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build` passes: 116 tests, zero failures, errors or skips. Pins now use six body lines per page and explicit previous/next footer buttons with disabled boundary states and a page counter. MouseManager input handling consumes the footer click sequence locally, dispatches page changes to the client thread, and unregisters with the overlay. Tests cover a single page action per click, press/release/click consumption, modified clicks, outside clicks and inactive listeners; existing paging tests retain all selected content and clamp page boundaries. Actual in-game button hit testing, Alt-drag and overlapping overlays still need player confirmation.

## Semantic presentation refinement: Milestone A

`env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build semanticReport inspectPreview pinPreview` passes: 126 tests, zero failures, errors or skips. This retains the previous 116 tests and adds tests for probability boundaries, invalid/conditional/multi-roll neutrality, explicit field labeling, preserved requirement wording/links/nesting/scopes, source identity, Rake normalization and live-price navigation, selectable odds without derived captions, and real component bounds at 185 and 225 pixels.

The source-coverage report passes all eight fixtures, including newly captured Rake revision 15184295 and ranarr seed. Every fixture reports complete semantic-unit coverage and valid composition with zero composition warnings. Signed Bronze dagger values and separate attack/defence groups remain covered by existing tests. No player-state or game-menu behavior changed.

Isolated 242 × 780 Swing previews were inspected for Rake, Recipe for Disaster requirements and full-guide instructions, Bronze dagger combat bonuses, ranarr seed and Vorkath drops. A deterministic source-section preview separately inspected Vorkath's Tertiary table. It showed green `1/10` with a Common caption, independent quantity/price rows, and unchanged neutral qualified odds. That fully materialized preview hit the existing 1024-component inspection budget and reported its explicit budget warning; it is not a whole-page geometry certification. Dedicated native-overlay previews inspected ranarr seed at 210 × 190 and quest requirement pins at 210 × 104.

The live Wiki Rake API response was retrieved to diagnose the inherited Daily volume label and chart-placeholder issue. Automated tests use captured fixtures, not the live network. Actual RuneLite usability, scaling and input remain player tests. No in-game screenshots or automated game input were used. See `SEMANTIC_STYLING.md` for rules and `TESTING.md` for the manual checklist. Further milestones wait for player feedback on this pass.
