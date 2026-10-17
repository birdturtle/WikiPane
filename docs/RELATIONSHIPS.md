# Relationship navigation

The initial projection reuses `KnowledgeArticle.relationships`; it does not fetch or parse a second copy of an article. `RelatedInformation` groups explicit internal article links by their existing source role. Production & methods, Sources, and Related information are section-context labels, not assertions that every destination is an ingredient, product, or acquisition method.

The reader exposes collapsed related-link lists in Making & uses, Sources, and Other sections when corresponding links exist. Clicking a destination uses the existing article navigation callback and history. The original article content remains available unchanged. Hovering a shortcut identifies its source section.

External links, special namespaces, self-links, guide shortcuts already handled elsewhere, and variant-specific table links are excluded. Exact destinations (including fragments) are retained and deduplicated within a group. Variant-specific relationships remain accessible in their original reader components until the projection can safely follow the active variant.

Structured production recipes now expose explicit inputs, outputs, quantities, requirements and conditions from recipe-table boundaries. See [RECIPES.md](RECIPES.md) for supported structures, scope and verification. Ordinary contextual links remain section-labeled links rather than invented ingredient/product relationships.

Manual verification: open Ranarr seed and Cooking, inspect available related lists, follow a link and use Back. Verify reading position restoration and unchanged source content. The new controls have not yet been verified in-game.
