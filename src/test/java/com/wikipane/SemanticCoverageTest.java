package com.wikipane;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.wikipane.document.*;
import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel;
import java.math.BigDecimal;
import java.util.*;
import org.junit.Test;

public class SemanticCoverageTest {
    private static final String[] FIXTURES = {"Bronze_dagger", "Recipe_for_Disaster", "Vorkath", "Cooking",
        "Vorkath__Strategies", "Recipe_for_Disaster__Full_guide", "Ranarr_seed", "Evergreen_tree", "Rake"};
    @Test
    public void everySourceBlockAndMeaningfulCellHasAnExposedSemanticDestination() throws Exception {
        for (String fixture : FIXTURES) {
            WikiDocument doc = DocumentTest.document(fixture);
            SemanticDocument semantic = doc.semantic;
            assertEquals(fixture,
                doc.blocks.stream().filter(b -> !(b instanceof WikiDocument.Heading)).count(),
                semantic.nodes.size());
            assertTrue(
                fixture + " missing " + com.wikipane.view.ArticleViewModel.create(doc.semantic).missingUnits,
                com.wikipane.view.ArticleViewModel.create(doc.semantic).missingUnits.isEmpty());
            for (Node node : semantic.nodes) {
                assertNotNull(node.source);
                assertEquals(doc.url, node.source.url);
                assertEquals(doc.revision, node.source.revision);
                assertFalse(node.units.isEmpty());
                if (node.table != null) {
                    WikiDocument.Table source = (WikiDocument.Table) node.fallback;
                    assertCells(source, node.table);
                } else if (node.text != null && node.fallback instanceof WikiDocument.Text)
                    assertEquals(
                        org.jsoup.Jsoup.parseBodyFragment(((WikiDocument.Text) node.fallback).html).text(),
                        node.text.text);
            }
            // Verify actual values, not only matching bookkeeping counts.
            String exposed = com.wikipane.view.ArticleViewModel.create(doc.semantic)
                                 .areas.stream()
                                 .flatMap(a -> a.components.stream())
                                 .map(ArticleViewModel.Component::text)
                                 .reduce("", (a, b) -> a + " " + b);
            for (Node node : semantic.nodes) {
                if (node.text != null && !node.text.text.isBlank())
                    assertTrue(fixture + ": missing paragraph " + node.id, exposed.contains(node.text.text));
                for (Fact fact : node.facts)
                    if (!fact.value.text.isBlank())
                        assertTrue(fixture + ": missing value " + fact.source.unit,
                            exposed.contains(fact.value.text));
            }
        }
    }
    @Test
    public void highlightedMonsterValuesFollowTheSelectedSourceVariant() throws Exception {
        SemanticDocument doc = DocumentTest.document("Vorkath").semantic;
        ArticleViewModel view = ArticleViewModel.create(doc);
        Set<String> variants = new LinkedHashSet<>();
        for (Node node : doc.nodes)
            for (Fact fact : node.facts)
                if (fact.key.equals("hitpoints"))
                    variants.add(fact.variant);
        assertTrue(variants.size() > 1);
        for (String variant : variants) {
            Fact actual = view.highlights(variant)
                              .stream()
                              .filter(f -> f.key.equals("hitpoints"))
                              .findFirst()
                              .orElseThrow();
            Fact source = doc.nodes.stream()
                              .flatMap(n -> n.facts.stream())
                              .filter(f -> f.key.equals("hitpoints") && f.variant.equals(variant))
                              .findFirst()
                              .orElseThrow();
            assertEquals(variant, actual.variant);
            assertEquals(source.value.text, actual.value.text);
        }
    }
    private void assertCells(WikiDocument.Table raw, Table semantic) {
        assertEquals(raw.rows.size(), semantic.rows.size());
        for (int r = 0; r < raw.rows.size(); r++) {
            assertEquals(raw.rows.get(r).cells.size(), semantic.rows.get(r).cells.size());
            for (int c = 0; c < raw.rows.get(r).cells.size(); c++) {
                WikiDocument.Cell a = raw.rows.get(r).cells.get(c);
                Cell b = semantic.rows.get(r).cells.get(c);
                assertEquals(a.text, b.value.text);
                assertEquals(a.column, b.column);
                assertEquals(a.rowspan, b.rowspan);
                assertEquals(a.colspan, b.colspan);
                assertEquals(a.semanticKey, b.key);
                for (int n = 0; n < a.nested.size(); n++) assertCells(a.nested.get(n), b.nested.get(n));
            }
        }
    }
    @Test
    public void classifiesStructuresWithoutArticleTitleChecks() throws Exception {
        assertEquals(PageType.ITEM, DocumentTest.document("Bronze_dagger").semantic.type);
        assertEquals(PageType.MONSTER, DocumentTest.document("Vorkath").semantic.type);
        assertEquals(PageType.QUEST, DocumentTest.document("Recipe_for_Disaster").semantic.type);
        assertEquals(PageType.SKILL, DocumentTest.document("Cooking").semantic.type);
        assertEquals(PageType.GUIDE, DocumentTest.document("Vorkath__Strategies").semantic.type);
        JsonObject fixture = DocumentTest.fixture("Vorkath");
        fixture.getAsJsonObject("parse").addProperty("title", "An unrelated test title");
        WikiDocument renamed = new WikiDocumentParser().parse(fixture);
        assertEquals(PageType.MONSTER, renamed.semantic.type);
        assertTrue(com.wikipane.view.ArticleViewModel.create(renamed.semantic)
                .areas.stream()
                .anyMatch(a -> a.id.equals("drops")));
    }
    @Test
    public void exactCombatAttributesAndCompleteDropRowsAreAccessible() throws Exception {
        WikiDocument bronze = DocumentTest.document("Bronze_dagger");
        Fact stab = bronze.semantic.nodes.stream()
                        .flatMap(n -> n.facts.stream())
                        .filter(f -> f.key.equals("astab") && f.variant.equals("Unpoisoned"))
                        .findFirst()
                        .get();
        assertEquals(new BigDecimal("4"), stab.number);
        assertEquals("+4", stab.value.text);
        assertEquals(Role.COMBAT,
            com.wikipane.view.ArticleViewModel.create(bronze.semantic)
                .area("stats")
                .components.get(0)
                .node.role);
        WikiDocument monster = DocumentTest.document("Vorkath");
        assertTrue(com.wikipane.view.ArticleViewModel.create(monster.semantic)
                .highlights.stream()
                .anyMatch(f -> f.key.equals("combat") && f.value.text.equals("732")));
        assertTrue(com.wikipane.view.ArticleViewModel.create(monster.semantic)
                .highlights.stream()
                .anyMatch(f -> f.key.equals("hitpoints") && f.value.text.equals("750")));
        int sourceRows = monster.semantic.nodes.stream()
                             .filter(n -> n.table != null && n.table.classes.contains("item-drops"))
                             .mapToInt(n -> n.table.rows.size())
                             .sum();
        int exposedRows =
            com.wikipane.view.ArticleViewModel.create(monster.semantic)
                .area("drops")
                .components.stream()
                .filter(c -> c.node.table != null && c.node.table.classes.contains("item-drops"))
                .mapToInt(c -> c.node.table.rows.size())
                .sum();
        assertTrue(sourceRows > 100);
        assertEquals(sourceRows, exposedRows);
        assertTrue(com.wikipane.view.ArticleViewModel.create(monster.semantic)
                .area("combat")
                .components.stream()
                .anyMatch(c -> c.text().toLowerCase(Locale.ROOT).contains("dragonfire")));
        assertTrue(com.wikipane.view.ArticleViewModel.create(monster.semantic)
                .area("combat")
                .related.stream()
                .anyMatch(r
                    -> r.target.url.contains("Vorkath/Strategies")
                        || r.target.url.contains("Vorkath%2FStrategies")));
    }
    private List<Requirement> leaves(Requirement requirement) {
        List<Requirement> result = new ArrayList<>();
        if (requirement.children.isEmpty())
            result.add(requirement);
        else
            for (Requirement child : requirement.children) result.addAll(leaves(child));
        return result;
    }
    @Test
    public void startingAndFullQuestRequirementsKeepScopeAndStructuredSkillLevels() throws Exception {
        WikiDocument doc = DocumentTest.document("Recipe_for_Disaster");
        List<RequirementGroup> groups = doc.semantic.nodes.stream()
                                            .flatMap(n -> n.requirements.stream())
                                            .flatMap(s -> s.groups.stream())
                                            .collect(java.util.stream.Collectors.toList());
        RequirementGroup starting =
            groups.stream()
                .filter(g -> g.label.toLowerCase(Locale.ROOT).contains("to start the quest"))
                .findFirst()
                .get();
        assertTrue(leaves(starting.expression)
                .stream()
                .anyMatch(r
                    -> r.kind == Requirement.Kind.SKILL && r.name.equals("Cooking")
                        && Integer.valueOf(10).equals(r.level) && r.boost == Requirement.Boost.ALLOWED));
        assertTrue(leaves(starting.expression)
                .stream()
                .anyMatch(r -> r.kind == Requirement.Kind.QUEST && r.name.equals("Cook's Assistant")));
        assertTrue(groups.stream().anyMatch(
            g -> g.label.toLowerCase(Locale.ROOT).contains("complete all chapters")));
        assertTrue(doc.semantic.nodes.stream()
                .flatMap(n -> n.locations.stream())
                .anyMatch(l -> l.x.equals("3208") && l.y.equals("3216")));
    }
    @Test
    public void fullWalkthroughAndStrategyContainTheActualInstructions() throws Exception {
        WikiDocument guide = DocumentTest.document("Recipe_for_Disaster__Full_guide");
        ArticleViewModel.Area walkthrough =
            com.wikipane.view.ArticleViewModel.create(guide.semantic).area("walkthrough");
        String text = walkthrough.components.stream()
                          .map(ArticleViewModel.Component::text)
                          .reduce("", (a, b) -> a + " " + b);
        assertTrue(text.length() > 30_000);
        assertTrue(text.toLowerCase(Locale.ROOT).contains("mountain dwarf"));
        assertTrue(text.toLowerCase(Locale.ROOT).contains("goblin"));
        assertTrue(walkthrough.components.stream().anyMatch(c -> c.source.sectionTitle.contains(" / ")));
        WikiDocument strategy = DocumentTest.document("Vorkath__Strategies");
        String combat = com.wikipane.view.ArticleViewModel.create(strategy.semantic)
                            .area("combat")
                            .components.stream()
                            .map(ArticleViewModel.Component::text)
                            .reduce("", (a, b) -> a + " " + b);
        assertTrue(combat.length() > 8_000);
        assertTrue(combat.toLowerCase(Locale.ROOT).contains("zombified"));
        assertTrue(combat.toLowerCase(Locale.ROOT).contains("dragonfire"));
        assertTrue(combat.contains("Woox"));
    }
    @Test
    public void unfamiliarContentAndGenericSectionsRemainExposed() {
        JsonObject response = new JsonObject(), parse = new JsonObject();
        parse.addProperty("title", "Unclassified");
        parse.addProperty("text",
            "<p>Intro</p><h2 id='Unusual'>Unusual section</h2><custom-container><table><tr><th>Unexpected "
                + "key</th><td>Exact unknown value</td></tr></table><ol start='5'><li>Fifth "
                  + "instruction</li><li "
                + "value='9'>Ninth instruction</li></ol></custom-container>");
        response.add("parse", parse);
        WikiDocument doc = new WikiDocumentParser().parse(response);
        assertTrue(com.wikipane.view.ArticleViewModel.create(doc.semantic).missingUnits.isEmpty());
        String text = com.wikipane.view.ArticleViewModel.create(doc.semantic)
                          .areas.stream()
                          .flatMap(a -> a.components.stream())
                          .map(ArticleViewModel.Component::text)
                          .reduce("", (a, b) -> a + b);
        assertTrue(text.contains("Exact unknown value"));
        assertTrue(text.contains("Ninth instruction"));
        List<Instruction> steps = doc.semantic.nodes.stream()
                                      .flatMap(n -> n.instructions.stream())
                                      .collect(java.util.stream.Collectors.toList());
        assertEquals(5, steps.get(0).ordinal);
        assertEquals(9, steps.get(1).ordinal);
    }
}
