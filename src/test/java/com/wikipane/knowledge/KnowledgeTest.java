package com.wikipane.knowledge;

import com.wikipane.DocumentTest;
import com.wikipane.document.*;
import com.wikipane.semantic.SemanticDocument.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KnowledgeTest {
    @Test public void itemMonsterAndQuestQueriesDoNotConstructAnArticleView() throws Exception {
        for (String name : new String[]{"Bronze_dagger", "Vorkath", "Recipe_for_Disaster"}) {
            WikiDocument doc = DocumentTest.document(name);
            String entityId = doc.entityAnchors.keySet().stream().findFirst().orElse("");
            KnowledgeArticle article = new KnowledgeArticle(doc, entityId, "");
            assertTrue(article.variantKnown);
            assertSame(doc.semantic, article.semantic);
            for (Fact fact : article.facts) {
                assertEquals(doc.revision, fact.source.revision);
                assertEquals(doc.url, fact.source.url);
            }
            assertFalse(Inspection.project(article).rows.isEmpty());
        }
    }
    @Test public void seedInspectionUsesActualFarmingFieldsAndLinkedCrop() throws Exception {
        KnowledgeArticle article = new KnowledgeArticle(DocumentTest.document("Ranarr_seed"), "5295", "");
        Inspection view = Inspection.project(article);
        assertTrue(view.rows.stream().anyMatch(r -> r.label.equals("Level required") && r.value.text.contains("32")));
        assertTrue(view.rows.stream().anyMatch(r -> r.label.equals("Growth time") && r.value.text.equals("80 min (4x20 min)")));
        assertTrue(view.rows.stream().anyMatch(r -> r.label.equals("Crop") && r.value.text.contains("Grimy ranarr weed")));
        assertTrue(view.related.stream().anyMatch(r -> r.target.url.contains("Grimy_ranarr_weed")));
        assertTrue(view.related.stream().anyMatch(r -> r.target.label.equals("Herblore")));
        for (Inspection.Row row : view.rows) assertEquals(article.document.revision, row.source.revision);
        java.util.Set<String> units = new java.util.HashSet<>();
        for (Inspection.Row row : view.rows) assertTrue("Repeated compact source", units.add(row.source.unit));
        assertFalse(article.requirements.stream().flatMap(set -> set.groups.stream())
            .anyMatch(group -> group.expression.original.text.equals("Patch")));
    }
    @Test public void ambiguousVariantsAreNeverFilledWithTheFirstVariant() throws Exception {
        KnowledgeArticle unknown = new KnowledgeArticle(DocumentTest.document("Vorkath"), "", "");
        assertFalse(unknown.variantKnown);
        assertTrue(unknown.facts.stream().noneMatch(f -> !f.variant.isEmpty()));
        KnowledgeArticle known = new KnowledgeArticle(DocumentTest.document("Vorkath"), "8061", "");
        assertTrue(known.variantKnown);
        assertFalse(known.variant.isEmpty());
        assertTrue(known.facts.stream().allMatch(f -> f.variant.isEmpty() || f.variant.equals(known.variant)));
    }
    @Test public void comparisonTablesCannotSupplyTheInspectedMonstersOwnStatistics() throws Exception {
        com.google.gson.JsonObject fixture = DocumentTest.fixture("Vorkath");
        com.google.gson.JsonObject parse = fixture.getAsJsonObject("parse");
        parse.addProperty("text", "<table><tr><th>Combat level</th><td>9999</td></tr></table>"
            + parse.get("text").getAsString());
        WikiDocument doc = new WikiDocumentParser().parse(fixture);
        KnowledgeArticle ambiguous = new KnowledgeArticle(doc, "", "");
        assertTrue(ambiguous.facts.stream().anyMatch(fact -> fact.value.text.equals("9999")));
        assertTrue(Inspection.project(ambiguous).rows.isEmpty());
        KnowledgeArticle exact = new KnowledgeArticle(doc, "8061", "");
        Inspection view = Inspection.project(exact);
        assertTrue(view.rows.stream().anyMatch(row -> row.label.equals("Combat level") && row.value.text.equals("732")));
        assertTrue(view.rows.stream().noneMatch(row -> row.value.text.equals("9999")));
    }
    @Test public void monsterOverlayDoesNotProjectCombatPrayerOrPreparationRecommendations() throws Exception {
        KnowledgeArticle monster = new KnowledgeArticle(DocumentTest.document("Vorkath"), "8061", "");
        Inspection view = Inspection.project(monster);
        assertTrue(view.rows.stream().noneMatch(row -> row.condition != null));
        assertTrue(view.rows.stream().noneMatch(row -> row.label.equals("About")));
        assertTrue(view.rows.stream().anyMatch(row -> row.label.equals("Hitpoints") && row.value.text.equals("750")));
        assertTrue(view.related.stream().anyMatch(link -> link.role == Role.DROPS));
        assertFalse(monster.requirements.isEmpty());
    }
    @Test public void treeInspectionShowsArticleFactsInsteadOfTheDisambiguationNotice() throws Exception {
        WikiDocument doc = DocumentTest.document("Evergreen_tree");
        String id = doc.entityAnchors.entrySet().stream().filter(e -> e.getValue().equals("3"))
            .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        KnowledgeArticle article = new KnowledgeArticle(doc, id, "");
        Inspection view = Inspection.project(article);
        assertEquals("3", article.variant);
        assertTrue(new com.wikipane.inspect.InspectionLayout(view).pages.stream().flatMap(java.util.List::stream)
            .anyMatch(line -> line.text.equals("Variant 3")));
        assertTrue(article.nodes.stream().anyMatch(node -> node.navigation && node.text.text.contains("other uses")));
        assertTrue(view.rows.stream().noneMatch(row -> row.value.text.contains("other uses")));
        assertTrue(view.rows.stream().anyMatch(row -> row.label.equals("About") && row.value.text.contains("25")));
    }
    @Test public void scopedQuestConditionsRemainStructured() throws Exception {
        KnowledgeArticle quest = new KnowledgeArticle(DocumentTest.document("Recipe_for_Disaster"), "", "");
        assertFalse(quest.requirements.isEmpty());
        Inspection inspection = Inspection.project(quest);
        assertTrue(inspection.rows.stream().anyMatch(r -> r.condition != null));
        for (Inspection.Row row : inspection.rows) if (row.condition != null) {
            assertSame(row.condition.original, row.value);
            assertFalse(row.source.sectionTitle.isEmpty());
        }
    }
}
