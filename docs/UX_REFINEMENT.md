# UX refinement

The `ux-refinement` branch starts from the developer-plugin packaging/configuration/icon checkpoint. Main has not been changed or pushed by this work.

## Information hierarchy

- The complete normalized Wiki document remains unchanged by presentation importance.
- `FactPolicy` classifies explicit identifier fields/MOID links as technical and editorial/release fields as supporting. Infobox facts are collected into collapsed **Technical details** and **Article details** groups after the reading content. Table records use the same policy locally.
- Deduplication across source units is restricted to explicitly enumerated singular infobox properties. Article URL, revision, variant, label and exact rich value must agree. Every consolidated source is retained on the rendered fact (`wiki.fact.sources`). Arbitrary records, requirements, conditional odds and locations are never deduplicated by text alone.
- Existing specific GE chart selectors remove script-only loading placeholders; Rake fixtures already cover that behavior. No broad rule deletes prose containing “loading”.
- Numeric drop odds retain their exact source value; computed rarity only influences accent and an explicitly identified tooltip. Source-defined labels remain visible.
- “None”, “N/A”, “Not required” and “No requirements” OTHER conditions do not get redundant unknown-state rows. Actual unsupported requirements retain unknown status.
- Supported map coordinates become map actions; unsupported map metadata gets a descriptive fallback rather than raw coordinates. Coordinates remain in the semantic source.

## Navigation and recovery

- The toolbar browser action belongs to the displayed article/visible section. Failed requests have their own wrapped error, Retry and browser actions, including before the first article has loaded.
- Network/timeout/TLS messages use a small safe vocabulary. Details stay in debug logs. Worker rejection ends the loading state with a retryable message.
- The section selector tracks visible source headings without pushing navigation history. Section choices still navigate using the existing source-header anchors.
- Variant changes return to the source section when available, otherwise the top, rather than reusing a pixel offset from a different variant.
- Full search uses [MediaWiki `sroffset` continuation](https://www.mediawiki.org/wiki/API:Search). More results retains a bounded window of 200 results with explicit messaging if earlier results leave that window; continuation remains available. Suggestions offer a full-search action. Cached pages and history retain continuation state.
- The sections menu wraps long labels, distinguishes categories by bold hierarchy and accessible names, and retains keyboard filtering/selection.

## Shared controls

- Related links, recipe source tables, article details and technical details share a disclosure control with visible expanded/collapsed arrows and accessible state text.
- Decorative table icons reserve 22×22 pixels without extra vertical padding; article images keep their separate spacing.
- Tables expand in batches of 20 and can be collapsed back to 20. Removed rows are removed from current-level tracking and selection registration.
- Map actions stay with their associated records. Multi-location groups use source-section labels where available.
- `autoPositionSkills` is a separate local setting. Existing `playerRequirements` and all other stored keys are retained.
- RuneLite 1.13.2 `ConfigItem` supports static `hidden`, not conditional visibility/enabled expressions. Subordinate settings therefore remain visible with explicit parent/dependency wording; no private config UI is patched and no saved preference is erased.

## Compact views, selection, and images

- Quick Inspect has real hit-tested Read article, previous/next and dismiss controls. Modifier gestures and unrelated game clicks pass through. Registration/removal follows plugin lifecycle; stale queued clicks are ignored. Shift-right-click retains recipe, related-link and recovery actions.
- Miniature inspection prose preserves list and numbered-step boundaries instead of flattening the entire rich value into one paragraph. It still uses the same semantic rows; no separate extraction engine.
- Hidden disclosure content is excluded from dragging, pin capture and copying. Original source menus survive reader registration/cleanup.
- Copy uses plain selected document text independently of overlay character/provenance limits. Pin capture still enforces its existing bounds and source contracts.
- Pin Open source uses the first content source on the current page, falling back to its heading source. Duplicate selections return the existing pin to page one and emphasize it briefly, rather than silently doing nothing. Pending duplicates provide feedback.
- Image windows show the source image identity, load the full-size raster on a worker, and support Fit, 100%, bounded zoom (5–400%), Ctrl-wheel zoom and drag panning. No additional raster is allocated for zoom. Retry and an explicit Wiki/browser action remain available on failure. Existing image memory/download/cache limits remain in force.
- Small thumbnail loading/failure placeholders avoid clipping long image alt text; the source description remains accessible and in the tooltip.

## Verification and manual acceptance

Automated checks cover the existing reader/semantic fixtures plus technical disclosure visibility, conservative deduplication/provenance, search continuation, failed-navigation browser targets, first-load recovery, hidden selections, large copying, original-menu restoration, current-page pin sources, duplicate emphasis, miniature list formatting, overlay hit testing and image zoom bounds. The semantic report covers 14 representative articles.

Native visual inspection uses isolated Swing previews with offline fixtures, never game input. These checks do not establish in-game behavior.

Player acceptance checklist:

1. Rake/Bronze dagger: ordinary gameplay facts first; expand Technical details to find IDs/MOID and Article details for editorial fields. Vorkath: exact odds/quantity and source links remain intact.
2. Open a long quest/skill article; scroll and verify the selector names the visible section. Jump to sections, change variants, then Back/Forward. Verify scroll restoration.
3. Search a broad term and use More results. Test a failed first load and a failed link while another article is open: Retry targets the failure; the toolbar browser action targets the visible article.
4. Expand/collapse related links, recipe source details and long tables. Highlight across cards with a closed disclosure between them; Copy and Pin must exclude the closed content.
5. Right-click an entity with Quick Inspect selected. Test footer Read article, paging and dismiss; Shift-right-click related/recipe actions; Alt movement. Confirm ordinary game clicks are unaffected.
6. Pin text spanning two source sections, page onward and Open source. Pin the same selection again, unpin it, then repeat enable/disable and login/logout checks.
7. Click a large image: Fit, 100%, zoom, pan, resize and Escape. Decorative item icons must remain non-interactive. Test an unavailable image.
8. Toggle Jump to your skill level separately from Show character checks. Existing Wiki button, quest Wiki actions and skill Wiki actions must still route correctly.

No Plugin Hub submission or publication is performed here. Config-dependent visual disabling remains an upstream API limitation, not a custom UI workaround.


## Unified section navigation follow-up

The reader and dropdown now consume one immutable `ArticleNavigation.ReadingSection` tree. Section headers are collapsible in the reader, with Overview initially expanded and other branches closed. Selecting a destination opens its ancestor chain and aligns that exact header. History stores the open branches and the single article scroll position. Existing deep links and skill-level positioning can open their relevant branch explicitly.

The category audit moved monster methods out of Drops, excluded citation-definition origins from navigation, and kept a guide chapter's requirements, instructions and rewards together instead of duplicating its chapter across categories. The dropdown supports nested expansion, filtering, keyboard arrows/Enter, wrapped labels and vertical-only scrolling. It never launches a browser when choosing a section.

Automated parity tests verify that the flattened dropdown destinations are the exact objects owned by the reader tree and that every composed source component has one owner. Additional tests cover collapsed defaults, lazy content, restored expansion, nested filtering, and existing section alignment, citations, pinning and player progress. Use `./gradlew sectionReport -Particle=Recipe_for_Disaster__Full_guide` to inspect ownership, or `./gradlew sectionPreview` for an isolated side-by-side preview without a game client.
