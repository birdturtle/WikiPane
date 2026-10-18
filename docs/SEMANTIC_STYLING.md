# Semantic presentation rules

This pass extends `ReaderLayout`, `ReaderComponents` and the existing content renderer. It does not replace the article pipeline, add account state, change navigation or add pin formatting steps.

## Probability rules

The source odds remain visible exactly as provided, with links, notes and quantities retained. Only explicitly named Rarity, Drop rate, Drop chance, Probability or Chance fields are considered. A quantity, price or combat statistic is never treated as a probability.

For a single fraction or percentage with probability p strictly between zero and one, WikiPane assigns these display categories:

| Probability | Display label | Accent |
| --- | --- | --- |
| p ≥ 1/10 | Common | Green |
| 1/100 ≤ p < 1/10 | Uncommon | Blue |
| 1/1,000 ≤ p < 1/100 | Rare | Purple |
| 0 < p < 1/1,000 | Very rare | Amber |

These are WikiPane display thresholds, not the Wiki's rarity classifications. Exact decimal arithmetic determines boundaries. An approximate percentage is available in the tooltip; it does not replace the exact fraction. Numeric rates retain their exact source text and use a restrained accent. Derived categories and approximate percentages are explained only in the tooltip; they are not printed as if supplied by the Wiki and are not included in highlighted source text or pins.

Source labels such as Common, Rare and Always remain the original visible text. A source-supplied rarity label is not used to infer numeric odds. Always, zero and certain probabilities are neutral rather than assigned a rarity category.

Multiple rolls (such as `2 × 5/150`), conditional rates, ranges, formulas, citations appended to odds, invalid values and unknown text remain neutral and unchanged. There is no multiplication, guessed interpretation, or normalization into a share of total loot. An independently rolled or tertiary table retains its source section and wording; categories describe the labeled probability only. Quantity and probability stay separate labeled fields.

## Shared visual language

`SemanticPalette` centralizes text, muted attribution, gold links, cyan numeric values, requirement text, input/output distinctions and rarity accents. The sidebar, Quick Inspect and pins reuse this palette, with their existing independent density and layout rules. Compact views keep their own density and native overlay controls; they do not acquire full sidebar cards.

Numeric source values align to the right within their existing label/value row. Negative combat values are not colored as success or failure. The label and the Wiki's signed number stay intact. Recipe inputs and outputs keep explicit source labels; colors never invent a recipe relationship. Arbitrary prose is not classified as a warning by keyword guessing.

Requirement presentation adds emphasis to existing numbers and alternative connectors, with distinct boostable/not-boostable text colors. Original wording, links, nesting, qualifications and start/full-completion groups remain intact. It does not evaluate the player's state. Formatting runs on the article worker, not on the EDT or game thread.

## Duplicate facts and normalization

Reader deduplication uses the same source URL, revision, source unit, label and original HTML. If a highlight already displays that exact fact in the active reading mode, its detail card does not repeat it. Semantic data, source coverage and navigation anchors remain intact. Opening a different mode builds its own display set. Consecutive source sections with the same parent breadcrumb show that parent once, keeping every actual section title and anchor. Distinct source cells or variants with matching values are not merged.

Rake exposed two normalization bugs: row labels were carried into later full-width infobox content, and row labels were also mistaken for section group headers. Infobox labels now apply to their own row; standalone header rows or explicit Wiki infobox-subheader markup establish groups. This preserves combat group headers alongside equipment images. Ordinary column-header tables retain their column mapping. Script-only Grand Exchange chart placeholders are removed; actual source statistics and the real-time price link remain available.

## Validation

Use the ordinary test suite and `semanticReport`, plus isolated Swing previews at real sidebar widths. Relevant fixtures include Rake, ranarr seed, Vorkath, Recipe for Disaster, its full guide and Bronze dagger's variants. Pin selection and navigation regression tests must continue to pass.

Player tests should inspect Rake's Daily volume and real-time price link, Vorkath's ordinary and tertiary drops, quest start/full requirements, nested lists and alternate item variants. Recheck highlight → right-click → Pin, source navigation and paging. Isolated previews do not establish in-game correctness.

Official compatibility references reviewed for this pass:

- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features
- https://github.com/runelite/plugin-hub/blob/master/README.md

This milestone changes presentation only. No game menu, widget, map or player-state integration is introduced.
