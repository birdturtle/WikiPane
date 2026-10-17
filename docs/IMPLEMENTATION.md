# Implementation

WikiPane targets Java 11 and RuneLite's official example-plugin workflow. The `latest.release` dependency resolved to RuneLite 1.13.2 during development.

## Components

- `WikiPanePlugin`, `WikiPaneConfig`: lifecycle, toolbar registration, settings, and client-thread dispatch.
- `api/WikiClient`, `api/WikiUrls`: bounded HTTPS requests, API parsing, official entity lookup resolution, URL restrictions, and cancellation.
- `semantic/SemanticDocument`, `SemanticExtractor`, `RequirementExtractor`, `TableRecords`: source-linked facts, scoped requirements, instructions, locations, relationships, complete tables, and generic content.
- `view/ArticleViewModel`, `WikiPage`: page-type-specific content areas and cached presentation models, independent of Swing.
- `view/ReaderLayout`: component contracts for fact groups, scoped requirement rows, named records, table headers, and variant-aware highlights. Built on the article worker.
- `ui/ReaderComponents`: shared typography, spacing, surfaces, aligned fields, requirement groups, record cards, headings, links, and source actions.
- `document/WikiDocument`, `WikiDocumentParser`: immutable content model, server-HTML normalization, infobox variants, table geometry, safe inline formatting, and image references.
- `ui/WikiPanePanel`, `ContentRenderer`: fixed search/navigation controls, native reader, expandable table records, keyboard navigation, content areas, anchor navigation, and lazy images.
- `navigation/NavigationHistory`: article/anchor history and viewport positions.
- `integration/GameEntityLookup`, `WikiButtonIntegration`: canonicalized item IDs, transformed NPC/object definitions, location-aware lookup URLs, selection cleanup, conflict detection, and owned-widget restoration.
- `cache/WikiCache`, `ImageCache`: expiring bounded caches and bounded image decoding.

All HTTP requests, document normalization, and image decoding run on a three-thread worker pool with a bounded queue. Swing updates run on the EDT; game state reads and widget modifications run on the client thread. Request generations reject stale search/article completions. Shutdown cancels tracked HTTP calls, stops the debounce timer, clears reader state, shuts down workers without waiting, removes the tab, and queues owned-widget cleanup.

## Retrieval

Suggestions use MediaWiki `action=opensearch` with redirect resolution. Full search uses `action=query&list=search`. Articles use `action=parse&prop=text|sections|revid|categories&redirects=1&formatversion=2`.

Entity requests use the same `Special:Lookup` parameters as RuneLite's Wiki plugin. Same-Wiki HTTP redirects and the response's canonical article link are supported. No title-only search substitutes for a failed ID lookup. Ambiguous/unresolved lookups retain the official lookup URL through the browser action. Infobox resource IDs select the correct variant when the lookup fragment uses an older alias.

The reader receives normalized blocks, not website HTML. Inline fragments contain only selected formatting and safe links. Scripts, frames, forms, editing controls, site navigation, and hidden infobox resources are not sent to Swing. Infobox switch resources are materialized into labeled variants before their raw markup is removed.

## Tables

Source rows retain every cell, its column index, header label, colspan, and rowspan. Data cells in a row with a single heading use that heading. Image-only headings use their alt/title text. Row-spanning context is carried into subsequent records. Nested tables remain separate expandable tables.

The pipeline is complete server-parsed article → normalized document → semantic document → page-type view model → native components. Extraction and page organization run on workers. Neither the document nor semantic layers imports Swing or depends on the view layer. Facts retain source article, revision, section, variant, and source-unit identifiers. Pure numeric values are available separately from their original text.

Requirements retain their source groups, explicit skill levels, boost qualifiers, quest/item links, quantities when explicit, and ALL/ANY expressions. Unknown conditions remain original prose. Ordered instructions retain numbering and nested steps. This model can support later account comparisons; account inspection is not implemented.

All meaningful normalized text, cells, links, images, nested tables, and unknown content receive a destination. Source-to-semantic and semantic-to-view coverage are tested separately from selected factual values. This validates the captured fixtures, not every possible Wiki template. Website controls are removed; unrecognized article content remains generic content rather than being summarized away.

Visible area buttons organize content by page type. Important facts appear first without discarding secondary facts. Remaining material stays in Other sections. Separate strategy/full-guide articles remain explicit linked articles; the plugin does not crawl their links automatically. The complete selected article is buffered before display. Swing components are created six at a time as the player scrolls; large tables expose 20 records at a time. All records and columns remain available, with source row order and relationships preserved.

## Bounds

- Suggestions: 80 entries, 2-minute expiry.
- Parsed articles and metadata: 12 entries, 10-minute expiry.
- Entity destinations: 100 entries, 10-minute expiry.
- History: 40 entries, retaining immutable documents.
- Images: 48 decoded thumbnails, 10-minute expiry; loaded lazily for visible components.
- Article responses: 4 MB. Entity HTML: 2 MB. Images: 1 MB compressed, 12 million source pixels, 8192 pixels per dimension.
- Decoded thumbnails fit within 200 × 220 pixels. Images load only on the Wiki-controlled `/images/` path; no automatic high-resolution srcset requests.
- Failed requests are not cached. HTTP 429 is reported without an automatic retry loop.

## Rendering limits

No JavaScript, video/audio playback, interactive maps, image animation, website CSS, table sorting, or calculators. Some image formats unsupported by Java ImageIO show a fallback. Only the first frame of animated images is used. Additional images and oversized pages require the original Wiki article. Mathematical and unusual interactive templates may render as plain text or need the browser.

Section headings and infobox-variant anchors are supported. Arbitrary anchors buried in specialized templates may need the browser. Section jumps do not reproduce website script behavior. Back/Forward retain scroll positions, table expansion, and the number of table rows revealed.

Quest, diary, and DPS-specific interface menu additions are not included. The replacement minimap button covers items (including equipped/supported item widgets), NPCs, objects, ground items, and direct search. Standard mode preserves RuneLite's original workflow.

The acceptance articles, Cooking, Vorkath/Strategies, and Recipe for Disaster/Full guide are regression fixtures, not a claim of full-Wiki rendering compatibility.

## Plugin Hub submission

The project uses `build=gradle` because jsoup is a necessary additional dependency for recovering and traversing MediaWiki HTML. Networking still uses RuneLite's injected OkHttp client; jsoup is used only as an in-memory parser. Gson is injected by RuneLite.

Before submission:

1. Complete the player tests, including all game layouts and enable/disable cycles.
2. Address any rendering defects found on additional articles.
3. Review jsoup against Plugin Hub's third-party dependency verification list. If this version is not approved, submit the required verification-template dependency and SHA-256 metadata for maintainer review.
4. Publish a source repository and submit the standard repository/commit marker only when authorized.
5. Have Plugin Hub maintainers review the replacement widget behavior and distribution.

Source repository: https://github.com/birdturtle/WikiPane. The project retains the official example plugin's Git history. Plugin Hub submission remains pending player testing and maintainer review.

## Sidebar presentation framework

`ReaderLayout` adapts semantic information into component contracts before it reaches Swing. `ContentRenderer` handles navigation anchors, progressive materialization, pagination, and lazy images; `ReaderComponents` owns appearance and layout. Document extraction does not depend on these components.

Short fields use a two-column row with a consistent label/value split. Long values stack beneath their label. Domain groups such as attack and defence bonuses remain distinct. Record identity is promoted only from explicit name/item/location/method columns. Remaining columns keep their labels and values. An image-only cell linking to the same record identity becomes an illustration description in its tooltip; the semantic data and presentation description remain available. Arbitrary prose is not discarded as an image caption.

Requirements retain scope headings and original wording. Their outer list shells are translated into consistently spaced rows without duplicate bullets. Unknown conditions and alternatives keep their source wording. Text wrapping preserves Swing's dedicated line-break view.

The fixed controls use one navigation row and compact area tabs. Source-section actions are available from a heading's right-click menu rather than repeated buttons in the reading flow. Identity images are limited to 88 × 88; content images retain the larger thumbnail bound. Resizing runs on the image worker.
