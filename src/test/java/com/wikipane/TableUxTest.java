package com.wikipane;

import com.wikipane.view.*;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TableUxTest {
    @Test public void cookingFoodRecordsHaveNamesAndCompactLabeledStatistics() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Cooking"));
        ReaderLayout.Record food = page.document.semantic.nodes.stream().filter(n -> n.table != null)
            .flatMap(n -> page.layout.records(n.table).stream())
            .filter(r -> r.identity != null && r.identity.value.text.equals("Cooked giant crab meat"))
            .findFirst().orElseThrow();
        assertTrue(food.compact);
        assertTrue(food.fields.stream().anyMatch(f -> f.label.equals("Level") && f.value.text.equals("21")));
        assertTrue(food.fields.stream().anyMatch(f -> f.label.equals("XP") && f.value.text.equals("100")));
        assertTrue(food.fields.stream().anyMatch(f -> f.label.equals("Healing") && f.value.text.equals("10")));
        assertTrue(food.fields.stream().anyMatch(f -> f.label.equals("Members") && f.value.text.equals("Members")));
        assertFalse(food.illustrations.isEmpty());
        assertNotNull(food.icon);
        assertTrue(com.wikipane.api.WikiUrls.image(com.wikipane.api.WikiUrls.resolve(food.icon.url)));
        assertTrue(food.icon.url.contains("Cooked_giant_crab_meat"));
        assertFalse(food.fields.stream().anyMatch(f -> f.value.text.equals(food.identity.value.text)));
    }
    @Test public void membershipIconsReplaceHiddenSortNumbersWithoutChangingOrdinaryNumbers() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Cooking"));
        List<ReaderLayout.Field> membership = new ArrayList<>();
        for (Node node : page.document.semantic.nodes) if (node.table != null)
            for (ReaderLayout.Record row : page.layout.records(node.table))
                for (ReaderLayout.Field field : row.fields) if (field.label.equals("Members")) membership.add(field);
        assertTrue(membership.stream().anyMatch(f -> f.value.text.equals("Members")));
        assertTrue(membership.stream().anyMatch(f -> f.value.text.equals("Free-to-play")));
        assertFalse(membership.stream().anyMatch(f -> f.value.text.matches("[01]")));
    }
    @Test public void sourceHierarchyKeepsLiteralSlashesAndColumnHeadersDoNotBecomeSectionTitles() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Cooking"));
        ArticleViewModel.Component food = page.view.areas.stream().flatMap(a -> a.components.stream())
            .filter(c -> c.source.section.equals("Meat_/_fish") && c.node.table != null).findFirst().orElseThrow();
        assertEquals("Meat / fish", page.layout.section(food.id).title);
        assertEquals("Types of food", page.layout.section(food.id).breadcrumb);
        assertTrue(page.layout.records(food.node.table).stream().anyMatch(r -> r.columnHeader && r.group.equals("Item")));
    }
    @Test public void dropRecordsRetainExactOddsInCompactPresentation() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Vorkath"));
        ReaderLayout.Record bones = page.document.semantic.nodes.stream().filter(n -> n.table != null)
            .flatMap(n -> page.layout.records(n.table).stream())
            .filter(r -> r.identity != null && r.identity.value.text.equals("Superior dragon bones"))
            .findFirst().orElseThrow();
        assertTrue(bones.compact);
        assertTrue(bones.fields.stream().anyMatch(f -> f.label.equals("Rarity") && f.value.text.equals("Always")));
    }
}
