package com.wikipane.context;

import com.wikipane.DocumentTest;
import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.Requirement;
import com.wikipane.view.*;
import java.util.*;
import java.util.stream.*;
import net.runelite.api.Quest;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuestRequirementRowsTest {
    @Test public void rfdNestedQuestsHaveIndividualChecksAndKeepTheirSource() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        Set<String> catalog = Arrays.stream(Quest.values()).map(q -> PlayerContextSnapshot.key(q.getName())).collect(Collectors.toSet());
        int quests = 0;
        for (ArticleViewModel.Component component : page.view.area("requirements").components) {
            for (ReaderLayout.ConditionGroup group : page.layout.section(component.id).requirements) {
                String original = Jsoup.parse(group.condition.original.html).text();
                String rows = group.rows.stream().map(r -> Jsoup.parse(r.html).text()).collect(Collectors.joining(" "));
                assertEquals(original, rows);
                List<String> before = Jsoup.parse(group.condition.original.html).select("a[href]").eachAttr("href");
                List<String> after = new ArrayList<>();
                for (RequirementRows.Row row : group.rows) {
                    after.addAll(Jsoup.parse(row.html).select("a[href]").eachAttr("href"));
                    if (row.condition == null) continue;
                    String title = RequirementEvaluator.questTitle(row.condition);
                    if (title == null || title.isEmpty()) continue;
                    quests++;
                    assertTrue("Unsupported quest: " + title, catalog.contains(PlayerContextSnapshot.key(title)));
                    PlayerContextSnapshot complete = new PlayerContextSnapshot(true,true,Map.of(),Map.of(title,PlayerContextSnapshot.QuestState.COMPLETE),null);
                    assertEquals(title, RequirementEvaluator.Status.MET, new RequirementEvaluator().evaluate(row.condition,complete).status);
                    assertTrue(row.condition.children.isEmpty());
                }
                assertEquals(before, after);
            }
        }
        assertTrue("Expected the complete nested prerequisite list", quests > 25);
    }
    @Test public void partialStageRequiresProofAndAlternativesStayGrouped() {
        Requirement stage = extract("<ul><li>Started <a href='/w/Legends%27_Quest'>Legends' Quest</a> up until gaining access to the Kharazi Jungle</li></ul>");
        stage = RequirementRows.of(stage).get(0).condition;
        assertEquals(Requirement.Kind.QUEST_STAGE,stage.kind);
        RequirementEvaluator evaluator = new RequirementEvaluator();
        PlayerContextSnapshot incomplete = new PlayerContextSnapshot(true,true,Map.of(),Map.of("Legends' Quest",PlayerContextSnapshot.QuestState.INCOMPLETE),null);
        PlayerContextSnapshot complete = new PlayerContextSnapshot(true,true,Map.of(),Map.of("Legends' Quest",PlayerContextSnapshot.QuestState.COMPLETE),null);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(stage,incomplete).status);
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(stage,complete).status);
        Requirement alternative=extract("<ul><li>Either <span data-skill='Cooking' data-level='10'>10 Cooking</span> or <span data-skill='Agility' data-level='20'>20 Agility</span></li></ul>");
        assertEquals(1,RequirementRows.of(alternative).size());
        assertTrue(Jsoup.parse(RequirementRows.of(alternative).get(0).html).text().contains(" or "));
    }
    private Requirement extract(String html) {
        return RequirementExtractor.extract(Jsoup.parseBodyFragment(html,"https://oldschool.runescape.wiki/w/Test").body(),"Requirements").get(0).expression;
    }
}
