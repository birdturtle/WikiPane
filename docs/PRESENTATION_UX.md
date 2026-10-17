# Presentation framework UX review

## Rules

Identity comes first in a repeated record. Only an explicitly named identity column can become the title. Short, simple attributes use a compact two-column metric layout; below 160px of available metric width they become one column. Long prose, nested tables and complex condition groups retain full labeled-field presentation. Short loot records use the same compact format while retaining exact probabilities, qualifiers, citation links and rarity captions. Labels and values remain separate selectable source fragments, with the original field provenance for pinning.

Image-only cells and adjacent linked names can contain exactly the same normalized text. Retain both source cells in table records, then assign the actual text cell to identity and its matching image cell to illustration metadata. Text equality alone is insufficient evidence that one source cell is redundant. This fixes Cooking records that previously retained only the image-derived name and therefore had no promoted identity.

When an explicit Members or Free-to-play icon accompanies a hidden numeric sorting span, normalize the icon's own label rather than presenting the sorting number. Do not interpret arbitrary 0/1 values as booleans. The normalization occurs before semantic extraction, so article text and pinned selections use the same meaningful label.

Headings use the semantic section hierarchy rather than splitting display strings on slashes. Literal headings such as "Meat / fish" remain intact. Multi-column table headers are suppressed as standalone headings only when their text is already represented in the labeled data fields; unrelated section notes remain visible.

## Components reviewed

| Component | Outcome |
| --- | --- |
| Repeated table records | Identity first; compact short attributes; generic fallback remains for complex values. |
| Infobox facts | Keep labeled fields and width-aware stacking; these are distinct facts rather than comparable records. |
| Drop odds | Retain exact source odds, conditional text and existing rarity captions on compact full-width rows. |
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

The persistent contextual navigation is one section selector, initially Overview, with a small dropdown arrow. Selecting a heading updates the selector label; the separate mode buttons and location subtitle are removed. Back/forward and per-area reading positions remain supported. Membership values use RuneLite BRAND_ORANGE for Members and muted gray for Free-to-play, including their source links.

Section jumps use the actual source-backed header label (the same label with the source-section browser action), not its containing card or breadcrumb. Source aliases share that header anchor. After materialization, two bounded ancestor-layout passes resolve wrapping and the updated viewport extent before positioning the header at the top. The Wine UI regression requires alignment within two pixels; source-header mapping is tested on Cooking, Bronze dagger, Vorkath and Recipe for Disaster. Destinations without a visible header retain a content fallback; near the end of an article normal scroll limits apply. Back/forward scroll restoration remains separate.

Navigation alignment also validates the native scroll pane and performs one guarded follow-up on the next EDT turn, allowing the final width/extent update to settle. It never runs a continuous recentering loop. Thumbnail geometry stays independent of decoded dimensions: row icons use a fixed 22px slot, and article images preserve declared (or bounded fallback) space. Different decoded aspect ratios are fitted inside that space instead of shifting subsequent headings.
