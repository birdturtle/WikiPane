package com.wikipane;

import static org.junit.Assert.*;

import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.semantic.TableRecords;
import com.wikipane.view.*;
import java.util.*;
import org.junit.Test;

public class ReaderLayoutTest {
    @Test
    public void everyTableValueHasAPresentationRoleIncludingImageDescriptions() throws Exception {
        for (String fixture : List.of("Bronze_dagger", "Vorkath", "Recipe_for_Disaster", "Cooking",
                 "Vorkath__Strategies", "Recipe_for_Disaster__Full_guide")) {
            WikiPage page = new WikiPage(DocumentTest.document(fixture));
            for (Node node : page.document.semantic.nodes)
                if (node.table != null)
                    compare(node.table, page.layout);
        }
    }
    private void compare(Table table, ReaderLayout layout) {
        List<ReaderLayout.Record> rows = layout.records(table);
        assertEquals(table.records.size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            TableRecords.Record source = table.records.get(i);
            ReaderLayout.Record target = rows.get(i);
            assertEquals(source.units, target.units);
            assertEquals(source.heading, target.group);
            List<ReaderLayout.Field> fields = new ArrayList<>(target.fields);
            fields.addAll(target.illustrations);
            if (target.identity != null)
                fields.add(target.identity);
            assertEquals(source.fields.size(), fields.size());
            for (TableRecords.Field field : source.fields)
                assertTrue(field.source.unit,
                    fields.stream().anyMatch(f
                        -> f.source.unit.equals(field.source.unit) && f.value.html.equals(field.value.html)));
            for (Table nested : source.nested) compare(nested, layout);
        }
    }
    @Test
    public void dropsHaveAnIdentityAndStableLabeledValuesRatherThanImageAltProse() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Vorkath"));
        ReaderLayout.Record bones =
            page.document.semantic.nodes.stream()
                .filter(n -> n.table != null)
                .flatMap(n -> page.layout.records(n.table).stream())
                .filter(r -> r.identity != null && r.identity.value.text.equals("Superior dragon bones"))
                .findFirst()
                .orElseThrow();
        assertTrue(
            bones.fields.stream().anyMatch(f -> f.label.equals("Quantity") && f.value.text.equals("2")));
        assertTrue(
            bones.fields.stream().anyMatch(f -> f.label.equals("Rarity") && f.value.text.equals("Always")));
        assertFalse(bones.illustrations.isEmpty());
        assertFalse(bones.fields.stream().anyMatch(f -> f.value.text.contains(".png:")));
    }
    @Test
    public void requirementsRetainScopesAndHaveSingleBulletFreeRows() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        List<ReaderLayout.ConditionGroup> groups = new ArrayList<>();
        for (ArticleViewModel.Component component : page.view.area("requirements").components)
            groups.addAll(page.layout.section(component.id).requirements);
        assertTrue(groups.stream().anyMatch(g -> g.title.startsWith("To start the quest")));
        assertTrue(groups.stream().anyMatch(g -> g.title.startsWith("To complete all chapters")));
        for (ReaderLayout.ConditionGroup group : groups)
            for (String line : group.lines) assertFalse(line.startsWith("<li>"));
    }
}
