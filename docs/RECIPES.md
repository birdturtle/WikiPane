# Structured recipes

This feature extends the existing normalized Wiki tables. It does not fetch a second source, crawl ingredient pages, or maintain an independent recipe database.

## Source and extraction

The parser retains the boundary of each explicit `.recipe-table`, its entity switch label and any source method-tab name. Method labels and entity variants remain distinct. `RecipeExtractor` reads the resulting semantic tables off the UI thread. A recognized method must contain a Requirements table and a Materials table with explicit Item and Quantity columns and exactly one Total cost separator. Rows before that separator are inputs; rows afterward are outputs. Tools and facilities remain conditions, not consumable ingredients.

Each ingredient/product retains its original rich text, exact destination, quantity text, name and quantity source units, revision and available icon. A safe positive integer count is also exposed when the quantity can be parsed unambiguously. Otherwise the original quantity remains available without a guessed count. Skill levels and XP remain associated with their source skill row. The Wiki's explicit boostable title is expanded into readable text; unfamiliar qualifiers remain unknown for local evaluation.

Methods remain separate. Unknown headers or additional requirements, ambiguous item alternatives, unfamiliar table shapes, nested requirement tables and missing separators fall back to the complete generic article tables. No article-title exceptions are used. Product/comparison lists are not automatically classified as recipes in this first slice.

## Presentation and query access

`SemanticDocument.recipes` is account-independent. `KnowledgeArticle.recipes` applies existing entity-variant scope; unknown entity variants cannot borrow another variant's method. Method labels that are independent of entity variants remain separate available methods.

The existing reader renders one recipe card per applicable method in Making & uses. It shows products, ingredient quantities, skill level/XP and explicit tools, facilities, membership and action timing. Original requirements/materials tables remain available through Costs & source details, including prices, profit calculations and unusual source notes. The second source table is not repeated as another always-visible card. Ingredient/product links use normal reader navigation; icons remain decorative. Selected fields keep the correct source node and record identity for highlight/right-click/pin.

Quick Inspect projects one recipe at a time from that same model. When multiple methods are available, its existing Shift-right-click menu provides Recipe methods (up to 12 entries, with an explicit full-article action for larger collections). Switching methods uses cached semantic data on the worker, resets only the temporary inspection page, sends no network request and does not change sidebar history. Dismissal and newer lookups invalidate pending method projections.

The reader's existing local player-context evaluator annotates supported recipe skill requirements. Inventory/bank ownership is not evaluated. Quick Inspect displays source requirements and does not claim account-aware completion statuses. No player state is added to Wiki requests.

## Verification

- Current Prayer potion API response, revision 15374387: one Ranarr potion (unf) plus one Snape grass produces one Prayer potion(3); Herblore 38, boostable, 87.5 XP.
- Live Bronze bar fixture, revision 15315107: Standard smelting, Blast Furnace and Superheat remain separate; Superheat retains Magic 43, 53 Magic XP and rune inputs.
- Existing Bronze dagger fixture: Smithing and three poison methods remain separate, with tools/facilities and exact inputs/outputs.
- Synthetic mutations of those real responses exercise missing separators, unknown conditions, alternatives and separate methods with different quantities.
- UI tests check exact links, complete source details, pin provenance and text geometry at 185/225px.
- Mocked controller tests check method-switch caching and dismissal of stale results. Overlay rendering tests include the potion fixture.

## Manual acceptance

1. Search Prayer potion, select Creation, and verify the recipe, quantities, XP and skill status against the character's current Herblore level.
2. Open Snape grass and use Back. Confirm the same recipe and reading position return.
3. Expand Costs & source details and verify costs, timing and notes remain accessible.
4. Highlight an ingredient/quantity and pin it. Verify the overlay names the ingredient and preserves its quantity.
5. Search Bronze dagger and change variants. Verify bars/Smithing/tools appear for Unpoisoned, and the correct poison method appears for each poisoned variant.
6. Inspect Bronze bar in-game. Use Shift-right-click Recipe methods to compare Standard, Blast Furnace and Superheat; confirm their facilities, runes and skills remain separate.
7. Inspect a supported recipe item in-game. Confirm the compact recipe appears without replacing the sidebar article. Where several methods are available, use Shift-right-click Recipe methods and verify each stays separate.
8. Dismiss during a lookup/method switch and disable/re-enable the plugin. Confirm no stale inspection returns.

These are player tests; automated fixtures and isolated Swing/overlay previews do not establish in-game success.
