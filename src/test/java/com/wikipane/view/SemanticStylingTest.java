package com.wikipane.view;

import com.wikipane.DocumentTest;
import com.wikipane.document.WikiDocumentParser;
import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.semantic.SemanticExtractor;
import com.google.gson.JsonObject;
import java.util.*;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class SemanticStylingTest {
    @Test public void simpleOddsKeepExactSourceAndHaveExplicitBoundaryRules() {
        String[] odds = {"1/10", "1/100", "1/1,000", "1/1001", "10%", "1%", "0.1%", "0.099%", "1/37.5"};
        Probability.Rarity[] expected = {Probability.Rarity.COMMON, Probability.Rarity.UNCOMMON, Probability.Rarity.RARE,
            Probability.Rarity.VERY_RARE, Probability.Rarity.COMMON, Probability.Rarity.UNCOMMON,
            Probability.Rarity.RARE, Probability.Rarity.VERY_RARE, Probability.Rarity.UNCOMMON};
        for (int i = 0; i < odds.length; i++) {
            Probability value = Probability.of(odds[i]);
            assertEquals(odds[i], value.original); assertEquals(odds[i], expected[i], value.rarity);
            assertFalse(value.label.isEmpty()); assertTrue(value.explanation.contains("not a share of total loot"));
        }
        assertEquals(Probability.Form.FRACTION, Probability.of("1/37.5").form);
        assertEquals(Probability.Form.PERCENT, Probability.of("2.5%").form);
    }
    @Test public void multipleRollsConditionsRangesFormulasAndInvalidOddsStayNeutral() {
        for (String odds : List.of("2 × 5/150", "1/100 (on task)", "1/100–1/200", "1/100[1]", "1/(level + 1)",
                "~1/100", "Unknown", "1/0", "2/1", "101%", "-1/100", "0.0.1%", "12", "1/10,00")) {
            Probability value = Probability.of(odds);
            assertEquals(odds, Probability.Form.UNPARSED, value.form);
            assertEquals(Probability.Rarity.NONE, value.rarity); assertEquals(odds, value.original); assertTrue(value.label.isEmpty());
        }
        assertEquals(Probability.Rarity.NONE, Probability.of("Always").rarity);
        assertEquals(Probability.Rarity.NONE, Probability.of("0/100").rarity);
        assertEquals(Probability.Rarity.NONE, Probability.of("100%").rarity);
        assertEquals(Probability.Form.LABEL, Probability.of("Rare").form);
        assertTrue(Probability.of("Rare").explanation.contains("supplied by the Wiki"));
    }
    @Test public void onlyExplicitlyLabeledProbabilitiesReceiveRarity() {
        RichText fraction = SemanticExtractor.rich("1/100");
        assertNull(FieldPresentation.of("Quantity", fraction, Role.DROPS).probability);
        assertNull(FieldPresentation.of("Price", fraction, Role.DROPS).probability);
        assertNotNull(FieldPresentation.of("Rarity", fraction, Role.DROPS).probability);
        FieldPresentation attack = FieldPresentation.of("Stab", SemanticExtractor.rich("+4"), Role.COMBAT);
        assertTrue(attack.numeric); assertEquals(FieldPresentation.Tone.COMBAT, attack.tone);
        assertEquals(FieldPresentation.Tone.INPUT, FieldPresentation.of("Ingredients", fraction, Role.METHODS).tone);
        assertEquals(FieldPresentation.Tone.OUTPUT, FieldPresentation.of("Produces", fraction, Role.METHODS).tone);
    }
    @Test public void requirementStylingPreservesTextLinksScopesAndNestedLogic() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        List<ReaderLayout.ConditionGroup> groups = new ArrayList<>();
        for (ArticleViewModel.Component component : page.view.area("requirements").components)
            groups.addAll(page.layout.section(component.id).requirements);
        assertTrue(groups.stream().anyMatch(g -> g.title.startsWith("To start")));
        assertTrue(groups.stream().anyMatch(g -> g.title.startsWith("To complete all")));
        boolean qualifiers = false;
        for (ReaderLayout.ConditionGroup group : groups) {
            assertEquals(group.lines.size(), group.styledLines.size());
            for (int i = 0; i < group.lines.size(); i++) {
                org.jsoup.nodes.Element original = Jsoup.parseBodyFragment(group.lines.get(i)).body();
                org.jsoup.nodes.Element styled = Jsoup.parseBodyFragment(group.styledLines.get(i)).body();
                assertEquals(original.text(), styled.text());
                assertEquals(original.select("a").eachAttr("href"), styled.select("a").eachAttr("href"));
                assertEquals(original.select("ul,ol,li").size(), styled.select("ul,ol,li").size());
                qualifiers |= !styled.select(".boost-forbidden,.boost-allowed").isEmpty();
            }
        }
        assertTrue(qualifiers);
        String choice = "<ul><li>Either 70 Magic or 75 Ranged (not boostable)<ul><li>2 ropes or a ladder</li></ul></li></ul>";
        org.jsoup.nodes.Element styled = Jsoup.parseBodyFragment(RequirementPresentation.html(choice));
        assertEquals(Jsoup.parseBodyFragment(choice).text(), styled.text());
        assertEquals(1, styled.select(".boost-forbidden").size()); assertEquals(0, styled.select(".boost-allowed").size());
        assertEquals(2, styled.select(".alternative").size()); assertEquals(2, styled.select("li").size());
    }
    @Test public void rakeHasOneDailyVolumeFactAndNoScriptPlaceholderWithInheritedLabels() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Rake"));
        List<Fact> facts = new ArrayList<>(); page.document.semantic.nodes.forEach(node -> facts.addAll(node.facts));
        assertEquals(1, facts.stream().filter(f -> f.label.equals("Daily volume")).count());
        Fact volume = facts.stream().filter(f -> f.label.equals("Daily volume")).findFirst().orElseThrow();
        assertEquals("325", volume.value.text); assertEquals("Grand Exchange", volume.group);
        Fact prices = facts.stream().filter(f -> f.value.text.equals("View real-time prices")).findFirst().orElseThrow();
        assertTrue(prices.label.isEmpty()); assertEquals("Grand Exchange", prices.group);
        assertFalse(facts.stream().anyMatch(f -> f.value.text.contains("Loading...")));
        assertTrue(facts.stream().anyMatch(f -> f.label.equals("Tradeable") && f.group.equals("Properties")));
        assertTrue(page.view.audit.valid()); assertTrue(page.view.missingUnits.isEmpty());
    }
    @Test public void ordinaryColumnHeadersAndFullWidthInfoboxNotesKeepTheirOwnMeaning() {
        JsonObject parse = new JsonObject(), response = new JsonObject(); parse.addProperty("title", "Example");
        parse.addProperty("text", "<table class='infobox'><tr><th colspan='2'>Properties</th></tr><tr><th>Daily volume</th><td>325</td></tr>"
            + "<tr><td colspan='2'>Not the daily volume</td></tr></table>"
            + "<table><tr><th>Item</th><th>Quantity</th></tr><tr><td>Rope</td><td>2</td></tr></table>");
        response.add("parse", parse);
        SemanticDocument doc = new WikiDocumentParser().parse(response).semantic;
        assertTrue(doc.nodes.get(0).facts.stream().anyMatch(f -> f.label.isEmpty() && f.value.text.equals("Not the daily volume")));
        assertTrue(doc.nodes.get(1).facts.stream().anyMatch(f -> f.label.equals("Quantity") && f.value.text.equals("2")));
    }
    @Test public void displayDedupIdentityDistinguishesSourceCellsVariantsAndRevisions() {
        RichText value = SemanticExtractor.rich("750");
        Source first = new Source("Example", "https://oldschool.runescape.wiki/w/Example", 1, "Post-quest", "Stats", "b1.r1.c1");
        Source distinctCell = new Source(first.article, first.url, 1, "Quest", "Stats", "b2.r1.c1");
        Source distinctRevision = new Source(first.article, first.url, 2, first.section, "Stats", first.unit);
        assertNotEquals(FieldPresentation.identity("Hitpoints", value, first), FieldPresentation.identity("Hitpoints", value, distinctCell));
        assertNotEquals(FieldPresentation.identity("Hitpoints", value, first), FieldPresentation.identity("Hitpoints", value, distinctRevision));
    }
}
