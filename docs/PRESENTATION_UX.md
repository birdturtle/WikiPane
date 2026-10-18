# Presentation framework UX review

## Rules

Identity comes first in a repeated record. Explicit name columns become titles. When there is no entity name, a numeric Level/Tier/Rank/Stage/Wave column becomes a labeled key; its label and value stay separate source-backed fragments. Short, simple attributes use a compact two-column metric layout; below 160px of available metric width they become one column. Long prose, nested tables and complex condition groups retain full labeled-field presentation. Short loot records use the same compact format while retaining exact probabilities, qualifiers, citation links and source-defined rarity labels. Labels and values remain separate selectable source fragments, with the original field provenance for pinning.

Image-only cells and adjacent linked names can contain exactly the same normalized text. Retain both source cells in table records, then assign the actual text cell to identity and its matching image cell to illustration metadata. Text equality alone is insufficient evidence that one source cell is redundant. This fixes Cooking records that previously retained only the image-derived name and therefore had no promoted identity.

When an explicit Members or Free-to-play icon accompanies a hidden numeric sorting span, normalize the icon's own label rather than presenting the sorting number. Do not interpret arbitrary 0/1 values as booleans. The normalization occurs before semantic extraction, so article text and pinned selections use the same meaningful label.

Headings use the semantic section hierarchy rather than splitting display strings on slashes. Literal headings such as "Meat / fish" remain intact. Multi-column table headers are suppressed as standalone headings only when their text is already represented in the labeled data fields; unrelated section notes remain visible.

## Components reviewed

| Component | Outcome |
| --- | --- |
| Repeated table records | Identity first; compact short attributes; generic fallback remains for complex values. |
| Infobox facts | Keep labeled fields and width-aware stacking; these are distinct facts rather than comparable records. |
| Drop odds | Retain exact source odds, conditional text and source-defined rarity labels on compact full-width rows. |
| Requirements | Preserve per-quest checks, indentation, alternatives and full qualification text. No extra status tooltip. |
| Headings and prose | Keep existing section hierarchy and wrapping. No article title duplication added. |
| Images | Keep existing bounded asynchronous thumbnails; image-only duplicate names become illustration metadata. |
| Navigation and related links | Keep persistent controls, history and source-section labels; related lists remain optional expansions. |
| Quick Inspect | Keep its independently bounded miniature layout; consumes the same normalized facts. |
| Pins | Preserve highlight/right-click/pin, structured label/value context and six-line pagination. |

## Verification boundaries

Fixture tests cover Cooking identity/statistics/membership, exact drop odds in compact presentation, and preservation of source fields across article fixtures. Isolated Swing tests measure complete record text and horizontal bounds at 145, 185 and 225px. Offline screenshots exercise actual reader components, not gameplay. Large-page visual audits retain their bounded 1024-component budget and cannot certify every row. Final visual usability and navigation require user testing in RuneLite.

## Gameplay feedback follow-up

Compact metrics pair muted small labels and source values on the same line, with tighter card padding and separation. Membership labels stand alone. Two columns fall back to a vertical arrangement when width requires it. Loot records use these rules as well as skill tables.

Reference definitions remain under References. They must not create additional Drops/Seeds/etc. destinations or ancestor aliases in the section menu, where they previously shadowed actual loot sections. Citation links continue to open their definitions. Backlinks are available through one Referenced from menu per note rather than a vertical button for each use.

Loot records use compact full-width labeled lines rather than a two-column metric grid, leaving room for complete odds, footnote qualifiers and prices. Numeric values are emphasized; secondary membership labels are muted. Numeric wrapping is a regression failure, independently of geometric clipping.

Table row icons retain their source image metadata through normalization. Only illustrations explicitly matched to the named record are shown. They use the existing visible-only asynchronous Wiki image loader/cache and a 22px bound. They have no link, click handler, tooltip, or hand cursor. Failed images leave a quiet empty slot; the item name and facts remain usable. Header height is measured at the remaining text width so icons cannot clip long names.

The persistent contextual navigation is one section selector, initially Overview, with a small dropdown arrow. `ArticleNavigation.roots` is the canonical section tree for both the searchable dropdown and `ArticleReader`'s scrollable disclosures. Overview starts expanded; other categories and nested source sections start collapsed. Explicit destinations open their ancestors and align the same source-backed header in the reader. Guide chapters retain their requirements, instructions and rewards together rather than appearing under several categories. Back/forward restores expansion state and the single article scroll position. Membership values use RuneLite BRAND_ORANGE for Members and muted gray for Free-to-play, including their source links.

Section jumps use the actual source-backed header label (the same label with the source-section browser action), not its containing card or breadcrumb. Source aliases share that header anchor. After materialization, two bounded ancestor-layout passes resolve wrapping and the updated viewport extent before positioning the header at the top. The Wine UI regression requires alignment within two pixels; source-header mapping is tested on Cooking, Bronze dagger, Vorkath and Recipe for Disaster. Destinations without a visible header retain a content fallback. The viewport reserves only the trailing height needed to align a short final section at the top; that height is reconstructed when restoring history. Back/forward scroll restoration remains separate.

Navigation alignment also validates the native scroll pane and performs one guarded follow-up on the next EDT turn, allowing the final width/extent update to settle. It never runs a continuous recentering loop. Thumbnail geometry stays independent of decoded dimensions: row icons use a fixed 22px slot, and article images preserve declared (or bounded fallback) space. Different decoded aspect ratios are fitted inside that space instead of shifting subsequent headings.


## Shared row hierarchy and column semantics

Table normalization distinguishes a mixed-row key from a column heading. In a grid with known columns, row keys do not overwrite the meaning of following cells or future rows. Infobox key/value rows and explicit recipe layouts retain their existing contracts. The semantic record keeps labeled row-header values rather than silently skipping them.

ReaderLayout assigns every source field a presentation role: entity or labeled numeric identity, verified membership metadata, illustration, or ordinary content. Record validation counts all roles and requires original labels, values and source units to remain intact; unsupported records still use the conservative fallback. No article titles or level-up-table CSS classes select this behavior.

Keyed narrative records show the labeled key once, with membership metadata beside it where width allows. A single short detail uses a compact line rather than a full header/body card. Named item and loot records keep their compact metric grid, including membership as a secondary metric, so extracting metadata does not inflate them. Lists retain structure and links with tighter field-content spacing. A body label is omitted only when it is already represented by the record's source group. Empty/no-unlock rows remain accessible; nothing is filtered based on wording. Pin context includes labeled keys such as Level 1 while selected values remain unchanged source fragments.

Regression fixtures cover Sailing, Farming and Herblore level-up tables. Synthetic tables exercise named row headers, tiers and ordered lists independently of skills. Existing Cooking, loot, quest, recipe and variant checks remain required. Native preview and geometry checks cover 185/225px without game input; player acceptance is still needed.


The section dropdown uses a width-tracking native Swing tree with explicit disclosure arrows and vertical-only reveal behavior. Filtering temporarily reveals matching branches without changing reader expansion. Labels use the same source-derived section objects as the reader, including deep chapters. Popup width follows the sidebar selector; long labels wrap rather than shifting the viewport horizontally.

`ArticleReader` composes existing `ContentRenderer` fragments on demand. Fragments share the existing executor, image cache, image viewer and player snapshot. There is one selection coordinator across the visible fragments. Closed sections do not fetch additional Wiki pages or render their content eagerly. Existing value, table, requirement and pin rendering remains in `ContentRenderer`/`ReaderComponents`.
