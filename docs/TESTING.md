# Testing WikiPane

Automated Swing tests exercise isolated reader panels. They do not launch RuneScape or inject game input. Only a player can verify in-game behavior.

## Automated

Run `env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean test build`.

Tests cover real article fixtures, exact Bronze dagger attack bonuses, Recipe for Disaster starting requirements, Vorkath table content, preserved lists/links, malformed markup, redirect fragments, entity IDs and coordinates, allowed URLs, response limits, cache expiry/eviction, image decode limits, stale article responses, failed requests preserving content, real Recipe for Disaster → Cook's Assistant navigation, and Swing Back/Forward scroll restoration.

`liveCheck` separately requests the three acceptance articles, suggestions, full search, exact Bronze dagger lookup, and one Vorkath image. It is not part of the offline test suite.

## Reader checks

At normal sidebar width:

- Search Bronze dagger. Choose Stats and select Unpoisoned. Confirm Stab attack is +4, Slash +2, Crush -4, Magic +1, Ranged +0, with defence values kept separate.
- Check Poison, Poison+, and Poison++ variants have their own labels and content.
- Search Recipe for Disaster. Open Sections → Requirements. Find 10 Cooking and Cook's Assistant as starting requirements; distinguish full completion requirements.
- Open Walkthrough and its full-guide link, follow Cook's Assistant, then Back and Forward. Check reading position.
- Search Vorkath. Choose monster variants, combat sections, and drop sections. Check every value has the right heading; test Show more entries and nested tables.
- Follow section links and switch content areas. Check Back after an anchor jump.
- Verify images fit, controls stay accessible, and no text is clipped or horizontally scrolled.
- Open article, file, and external links. The explicit browser command must remain available. Right-click a section heading to check its source action.

## In-game checks

1. Confirm standard mode leaves the core Wiki plugin and button alone.
2. Enable replacement while core Wiki is enabled. Confirm guidance appears and there is only one button.
3. Disable core Wiki. Enable replacement and SIDEBAR destination.
4. Select an inventory item, equipped item, ground item, NPC, and object with the Wiki button. Confirm the right article/variant and no browser launch.
5. Test a transformed NPC/object and an ambiguous entity. A failed resolution must keep the official lookup URL available; it must not guess by name.
6. Cancel selection and click unsupported widgets. Confirm target mode exits and normal game actions resume.
7. Test left-click Search, right-click Search, and BROWSER destination.
8. Log out/in, resize, switch fixed/resizable layouts, toggle minimap, and disable/re-enable WikiPane. Confirm safe cleanup and no duplicate widgets.
9. Re-enable core Wiki while replacement is active. Confirm WikiPane yields without altering core settings.

## Reliability

- Empty query, no results, missing article, disconnected network, and a slow Wiki request.
- Rapidly change queries and articles. Old results must not replace newer ones.
- Disable WikiPane during a request, then re-enable it.
- Search while logged out.
- Navigate repeatedly and monitor client responsiveness and heap use. Cached navigation should avoid redundant article HTTP calls.
- Expand a large table, navigate away/back, and confirm the expanded table and its revealed rows are restored.

Record player results here before declaring in-game acceptance complete. At implementation time, no player test has been performed.

## Semantic completeness

Run `./gradlew semanticReport` to report source units, exposed areas, and full text sizes for the captured fixtures. Tests compare every normalized cell, its geometry, and its text against the semantic model, then check exposed paragraph and fact values. They also exercise all drop-table pages and the complete buffered quest walkthrough through isolated Swing components.

Additional assertions cover explicit alternative requirements, quantities, boost rules, instruction numbering, unknown content, provenance, and variant-specific highlighted values. These checks do not establish universal Wiki parsing completeness. New template types still need source comparison and visual review.

## Presentation framework checks

- Compare item attack and defence groups. Every value should line up with its own label.
- Open Vorkath drops. Names should head separate records, with quantity, rarity, price, and alchemy values below. Long image descriptions should not fill the record body.
- Check Recipe for Disaster starting and full-completion requirement cards. No duplicate bullets or oversized gaps.
- Confirm explicit line breaks separate notice text and instructions.
- Switch variants and areas, then use Back and Forward. Confirm the same reading position and expanded records return.
- Inspect at narrow sidebar width and with a larger UI scale. Automated component bounds cover 185, 225, and 300 pixels; desktop scaling still needs player review.

## Validation and references

- Open Recipe for Disaster/Full guide. Follow its citation marker, read the reference card, and use Return to citation. Test Back and Forward across both locations.
- Confirm reference definitions no longer appear as numbered walkthrough steps.
- Right-click the article title → Composition audit. Check that source units are accounted for and inspect any warnings. An audit generated without a rendered panel must say visual inspection has not been measured.
- Resize the reader narrowly. Facts should stack and highlight grids should become lists when necessary. Images must shrink rather than crop.
- Repeat after expanding tables and scrolling into later content. A geometry warning is diagnostic evidence, not a claim that an automatic repair succeeded.

For isolated screenshots and audit output, without RuneScape:

```sh
xvfb-run -a env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew preview -Particle="Recipe for Disaster/Full guide" -Panchor=cite_note-1 -Pcapture=/tmp/wikipane-references.png
```

## Contextual navigation

- Reading shows the article identity and at most two primary modes. Search opens with the magnifying-glass icon. It must not remain expanded above an article.
- Search a query, open a result, press Back, and confirm the query/results/scroll return. Forward must restore the article without another search.
- Scroll Overview, switch to Walkthrough, scroll again, and switch back and forth. Each position should be retained.
- Open Sections, filter for a chapter, and choose it with Enter. It should jump directly to that chapter in the relevant mode. Test Back and Forward afterward.
- Open the full Recipe for Disaster guide and select Freeing the Mountain dwarf under Walkthrough. Its content should be reachable even though the original heading has no immediate prose.
- Scroll a long guide. Article identity, Back, Forward, Search, Open in Browser, and Sections must remain accessible.
- Test Escape from search, popup keyboard focus, switching variants, and disabling WikiPane while Sections is open.

Native automated tests cover these navigation transitions using captured Wiki responses and isolated Swing panels. Actual RuneLite interaction and desktop scaling still need player review.

## Contextual lookup checkpoint

The first gameplay integration has its setup, manual scenarios and remaining scope in [NATIVE_INTEGRATION.md](NATIVE_INTEGRATION.md). Contextual lookup is opt-in. Start with Shift-right-click on an inventory seed, then test bank, equipment, ground item, NPC and object entries with Menu Entry Swapper enabled. Inspecting and dismissing must preserve the open article; explicit full-article navigation must support Back. Overlay actions use Shift-right-click and positioning uses Alt-drag.

## Selection pins

Highlight article text, right-click and choose Pin selection. No editor or confirmation should appear. Test selection across linked paragraphs, requirements and table fields; verify list numbering, emphasis, field labels and source variants. Close the sidebar and confirm the pin remains. Test Alt-drag, Shift-right-click source/paging/unpin actions and repeated plugin enable/disable. The complete checklist is in [PINNING.md](PINNING.md).
