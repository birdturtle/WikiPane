package com.wikipane.semantic;

import com.google.gson.JsonObject;
import com.wikipane.DocumentTest;
import com.wikipane.document.WikiDocument;
import com.wikipane.document.WikiDocumentParser;
import com.wikipane.knowledge.KnowledgeArticle;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.WikiPage;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipeTest {
    @Test public void livePotionFixtureRetainsExactInputsOutputLevelsXpAndProvenance() throws Exception {
        WikiDocument document=DocumentTest.document("Prayer_potion");
        assertEquals(1,document.semantic.recipes.size());
        Recipe recipe=document.semantic.recipes.get(0);
        assertEquals(2,recipe.inputs.size());assertEquals(1,recipe.outputs.size());
        assertEquals("Ranarr potion (unf)",recipe.inputs.get(0).name.text);
        assertEquals("Snape grass",recipe.inputs.get(1).name.text);
        assertEquals("Prayer potion(3)",recipe.outputs.get(0).target.label);
        assertEquals(BigInteger.ONE,recipe.outputs.get(0).count);
        assertTrue(recipe.materialsTable.rows.stream().flatMap(r->r.cells.stream()).anyMatch(c->c.source.unit.equals(recipe.outputs.get(0).quantitySource.unit)&&c.label.equals("Quantity")));
        Recipe.Skill skill=recipe.skills.get(0);
        assertEquals("Herblore",skill.name);assertEquals(Integer.valueOf(38),skill.requirement.level);
        assertEquals(Requirement.Boost.ALLOWED,skill.requirement.boost);
        assertEquals("87.5",skill.experience.value.text);
        for(Recipe.Ingredient ingredient:recipe.inputs) {
            assertEquals(document.revision,ingredient.source.revision);
            assertEquals(document.url,ingredient.source.url);
            assertTrue(document.semantic.contentUnits.contains(ingredient.source.unit));
            assertTrue(document.semantic.contentUnits.contains(ingredient.quantitySource.unit));
            assertNotNull(ingredient.icon);
        }
        assertTrue(new WikiPage(document).view.audit.valid());
    }
    @Test public void equipmentMethodsNeverMergeVariantsOrConsumeTools() throws Exception {
        WikiDocument document=DocumentTest.document("Bronze_dagger");
        assertEquals(4,document.semantic.recipes.size());
        assertFalse(DocumentTest.flatten(document).contains("Loading..."));
        assertFalse(DocumentTest.flatten(document).contains("UnpoisonedPoisonPoison+Poison++"));
        Recipe smith=document.semantic.recipes.get(0), poison=document.semantic.recipes.get(1);
        assertEquals("Unpoisoned",smith.variant);assertEquals("Poison",poison.variant);
        assertEquals(1,smith.inputs.size());assertEquals("Bronze bar",smith.inputs.get(0).name.text);
        assertEquals("Hammer",smith.conditions.get("Tools").value.text);
        assertEquals("Anvil",smith.conditions.get("Facilities").value.text);
        assertEquals("12.5",smith.skills.get(0).experience.value.text);
        assertEquals("Bronze dagger",smith.outputs.get(0).target.label);
        assertEquals(2,poison.inputs.size());assertTrue(poison.skills.isEmpty());
        assertEquals("Weapon poison",poison.inputs.get(1).name.text);
        KnowledgeArticle selected=new KnowledgeArticle(document,"","Unpoisoned");
        assertEquals(1,selected.recipes.size());assertEquals("Unpoisoned",selected.recipes.get(0).variant);
    }
    @Test public void unrecognizedRecipeBoundariesUseTheCompleteOriginalTables() throws Exception {
        JsonObject fixture=DocumentTest.fixture("Prayer_potion");
        fixture.getAsJsonObject("parse").addProperty("text",fixture.getAsJsonObject("parse").get("text").getAsString().replace("Total cost","Unrecognized separator"));
        WikiDocument document=new WikiDocumentParser().parse(fixture);
        assertTrue(document.semantic.recipes.isEmpty());
        assertTrue(DocumentTest.flatten(document).contains("Snape grass"));
        assertTrue(DocumentTest.flatten(document).contains("Unrecognized separator"));
        assertTrue(new WikiPage(document).view.audit.valid());
    }
    @Test public void unsupportedConditionsAndAlternativeIngredientsKeepGenericSource() throws Exception {
        for(boolean alternative:new boolean[]{false,true}) {
            JsonObject fixture=DocumentTest.fixture("Prayer_potion");
            com.wikipane.html.Element root=com.wikipane.html.WikiHtml.parseBodyFragment(fixture.getAsJsonObject("parse").get("text").getAsString()).body();
            com.wikipane.html.Element wrapper=root.selectFirst(".recipe-table");
            if(alternative) {
                com.wikipane.html.Element item=wrapper.select("table").get(1).select("tr").get(2).select("td").get(1);
                item.append(" or <a href='/w/Another_ingredient'>another ingredient</a>");
            } else wrapper.selectFirst("table tbody").append("<tr><th>Other requirements</th><td colspan='3'>A quest stage must be reached</td></tr>");
            fixture.getAsJsonObject("parse").addProperty("text",root.html());
            WikiDocument document=new WikiDocumentParser().parse(fixture);
            assertTrue(document.semantic.recipes.isEmpty());
            assertTrue(new WikiPage(document).view.audit.valid());
            assertTrue(DocumentTest.flatten(document).contains(alternative?"another ingredient":"A quest stage must be reached"));
        }
    }
    @Test public void separateMethodsAndSourceQuantitiesAreNotMerged() throws Exception {
        JsonObject fixture=DocumentTest.fixture("Prayer_potion");
        com.wikipane.html.Element root=com.wikipane.html.WikiHtml.parseBodyFragment(fixture.getAsJsonObject("parse").get("text").getAsString()).body();
        com.wikipane.html.Element original=root.selectFirst(".recipe-table"),copy=original.clone();
        copy.select("table").get(1).select("tr").get(1).select("td").get(2).text("2");
        original.after(copy);fixture.getAsJsonObject("parse").addProperty("text",root.html());
        WikiDocument document=new WikiDocumentParser().parse(fixture);
        assertEquals(2,document.semantic.recipes.size());
        assertEquals(BigInteger.ONE,document.semantic.recipes.get(0).inputs.get(0).count);
        assertEquals(BigInteger.valueOf(2),document.semantic.recipes.get(1).inputs.get(0).count);
        assertNotEquals(document.semantic.recipes.get(0).id,document.semantic.recipes.get(1).id);
        com.wikipane.knowledge.Inspection inspection=com.wikipane.knowledge.Inspection.project(new KnowledgeArticle(document,"",""),1);
        assertTrue(inspection.rows.stream().anyMatch(r->r.label.equals("Ingredient")&&r.value.text.startsWith("2 × Ranarr")));
        assertFalse(inspection.rows.stream().anyMatch(r->r.label.equals("Ingredient")&&r.value.text.startsWith("1 × Ranarr")));
    }
    @Test public void inspectAndLocalChecksConsumeTheSameStructuredRecipe() throws Exception {
        WikiDocument document=DocumentTest.document("Prayer_potion");
        com.wikipane.knowledge.Inspection inspection=com.wikipane.knowledge.Inspection.project(new KnowledgeArticle(document,"",""));
        com.wikipane.knowledge.Inspection.Row ingredient=inspection.rows.stream().filter(r->r.label.equals("Ingredient")&&r.value.text.contains("Snape grass")).findFirst().orElseThrow();
        assertEquals("1 × Snape grass",ingredient.value.text);assertEquals(2,ingredient.sources.size());
        assertTrue(inspection.rows.stream().noneMatch(r->r.value.text.contains("Construction")));
        assertTrue(inspection.related.stream().anyMatch(r->r.target.url.endsWith("/w/Snape_grass")));
        com.wikipane.context.PlayerContextSnapshot player=new com.wikipane.context.PlayerContextSnapshot(true,true,
            java.util.Map.of("Herblore",new com.wikipane.context.PlayerContextSnapshot.Level(37,40)),java.util.Map.of(),null);
        assertEquals(com.wikipane.context.RequirementEvaluator.Status.MET,
            new com.wikipane.context.RequirementEvaluator().evaluate(document.semantic.recipes.get(0).skills.get(0).requirement,player).status);
    }

    @Test public void realAlternativeMethodsKeepFurnacesRunesAndSkillXpSeparate() throws Exception {
        WikiDocument document=DocumentTest.document("Bronze_bar");
        assertEquals(3,document.semantic.recipes.size());
        Recipe smelt=document.semantic.recipes.get(0),blast=document.semantic.recipes.get(1),spell=document.semantic.recipes.get(2);
        assertEquals("Standard",smelt.method);
        assertFalse(blast.method.isEmpty());assertFalse(spell.method.isEmpty());
        assertEquals("Furnace",smelt.conditions.get("Facilities").value.text);
        assertEquals("Blast Furnace",blast.conditions.get("Facilities").value.text);
        assertEquals(2,smelt.inputs.size());assertEquals(2,blast.inputs.size());
        assertEquals("Bronze bar",spell.outputs.get(0).name.text);
        assertEquals(2,spell.skills.size());
        assertEquals("Magic",spell.skills.get(1).name);assertEquals(Integer.valueOf(43),spell.skills.get(1).requirement.level);
        assertEquals("53",spell.skills.get(1).experience.value.text);
        assertTrue(spell.inputs.stream().anyMatch(i->i.name.text.equals("Nature rune")&&BigInteger.ONE.equals(i.count)));
        assertFalse(smelt.inputs.stream().anyMatch(i->i.name.text.contains("rune")));
        KnowledgeArticle article=new KnowledgeArticle(document,"","");
        assertEquals(3,article.recipes.size());
        com.wikipane.knowledge.Inspection inspection=com.wikipane.knowledge.Inspection.project(article,2);
        assertTrue(inspection.rows.stream().anyMatch(r->r.label.equals("Magic")&&r.value.text.startsWith("43")));
        assertTrue(new WikiPage(document).view.audit.valid());
    }

}
