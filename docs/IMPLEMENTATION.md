# Current implementation

WikiPane is a Java 11 RuneLite plugin with native Swing reading and RuneLite overlays. It uses RuneLite's injected OkHttp client and Gson, and the dependency-free `com.wikipane.html` markup reader for in-memory normalization of server-rendered Wiki HTML. Plugin Hub builds use `build=standard`. There is no external service, script execution, telemetry, account handling or gameplay automation.

## Content pipeline

`WikiClient` retrieves complete server-parsed article HTML, metadata and structured map data. `WikiDocumentParser` normalizes source content; `SemanticExtractor` produces `SemanticDocument` with source units, requirements, relationships and locations. `ArticleViewModel`, `ReaderLayout` and the presentation validation pass compose width-aware article components. Content not safely specialized stays accessible in the general reader. Unknown values are never filled in.

`KnowledgeService` and `KnowledgeArticle` reuse the same cached documents for entity-aware compact inspection. Exact item/NPC/object identities and source variants are preserved; an unresolved variant cannot borrow another variant's statistics.

## Reader and history

`ArticleNavigation` supplies one canonical hierarchy to the section dropdown and scrollable disclosures. Overview starts expanded; the other sections start collapsed. Source-backed heading anchors align section jumps. Back/Forward records page/results changes, not same-page section jumps; entries retain position and disclosure state.

`ReaderComponents` owns typography, spacing, links and compact records. Semantic styling retains exact odds and qualifications. Technical IDs/MOIDs and editorial facts use separate disclosures. Source-aware deduplication consolidates repeated presentation without discarding provenance. Article images use asynchronous bounded decoding; the non-modal Swing viewer fetches the original raster on deliberate click (SVGs use a larger Wiki PNG).

## Game integrations

The opt-in unified Wiki routing switch covers minimap lookup/search and supported quest/skill Wiki actions. Core Wiki may stay enabled: the integration retains/restores its widget and forwards its DPS callback. It does not mutate another plugin's config or intercept global browser launches. Context menus are separate, opt-in informational client callbacks. See [native integration](NATIVE_INTEGRATION.md).

Quick Inspect keeps the article/history untouched until the player explicitly opens the article. Selection pins use the existing semantic provenance and a separate miniature layout: highlight → right-click → Pin. They are bounded session snapshots, not account data or persisted HTML. See [pinning](PINNING.md).

`PlayerContextService` snapshots supported local skills, requested quest states and quest points on the client thread. `RequirementEvaluator` applies MET/UNMET/UNKNOWN, nested ALL/ANY and explicit boost rules independently of cached Wiki knowledge. No inventory/equipment/bank comparison is implemented. See [player context](PLAYER_CONTEXT.md).

`ArticleMaps` indexes source images, frames and verified points. Map previews preserve source regions/routes; exact verified locations can open the native map with one replaceable marker selection. Unsupported map areas preserve the previous marker and offer preview/source access. See [map integration](MAP_INTEGRATION.md) and [coverage audit](MAP_COVERAGE_AUDIT.md).

## Threads, bounds and lifecycle

HTTP/parsing/image/layout work runs on bounded worker executors. Swing updates run on the EDT; game reads and native marker/menu callbacks run on the client thread. Generation guards ignore stale completions. Shutdown cancels work/HTTP calls and removes owned widgets, overlays, mouse listeners and navigation. No injected client's shared executor is shut down.

Caches: 80 search/suggestion entries each (2 minutes), 12 articles and 12 prepared pages (10 minutes), 100 resolved entities (10 minutes), 40 history entries, 48 thumbnails (10 minutes), 2 full images (10 minutes), and 32 map tiles plus 4 composed map rasters (10 minutes). Thumbnails fit 200×220. Original images are capped at 12 MB compressed, 12 million pixels and 8192 per dimension; map compositions are at most 1200×1200 and 36 tiles. Failed responses are not cached and rate limits do not trigger retry loops.

## Limits and release status

No JavaScript, calculators, interactive website controls, audio/video or animated image playback. Unusual templates/formulas can need the explicit browser fallback. Pins are session-only. Unknown requirements remain unknown. Underground native map-area switching is not exposed by the inspected public API; regions/routes remain Wiki preview geometry, not inferred exact game destinations.

The real user has tested major interactions in gameplay. Broader beta/lifecycle/platform checks remain a release gate, separate from automated tests. Current instructions: [beta guide](BETA_TESTING.md), [tests](TESTING.md), [Plugin Hub readiness](PLUGIN_HUB.md). The [public-beta Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/18901) is open; maintainer approval remains pending.
