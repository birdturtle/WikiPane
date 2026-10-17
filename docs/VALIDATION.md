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
