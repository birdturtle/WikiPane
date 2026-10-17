package com.wikipane.context;

import com.wikipane.DocumentTest;
import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequirementEvaluatorTest {
    private final RequirementEvaluator evaluator = new RequirementEvaluator();
    private Requirement skill(String name, int level, Requirement.Boost boost) {
        return new Requirement(Requirement.Kind.SKILL, name, "", level, null, boost, SemanticExtractor.rich(level+" "+name), List.of());
    }
    private PlayerContextSnapshot player(int base, int current) {
        return new PlayerContextSnapshot(true,true,Map.of("Cooking",new PlayerContextSnapshot.Level(base,current)),
            Map.of("Cook's Assistant",PlayerContextSnapshot.QuestState.COMPLETE),100);
    }
    private Requirement group(Requirement.Kind kind, Requirement... children) {
        return new Requirement(kind,"Group","",null,null,Requirement.Boost.UNSPECIFIED,SemanticExtractor.rich("Conditions"),List.of(children));
    }
    private Requirement unknown() { return new Requirement(Requirement.Kind.OTHER,"Unknown","",null,null,Requirement.Boost.UNSPECIFIED,SemanticExtractor.rich("Unknown"),List.of()); }
    @Test public void skillChecksRespectAllowedForbiddenUnspecifiedBoostsAndDrains() {
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(skill("Cooking",40,Requirement.Boost.ALLOWED),player(35,42)).status);
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(skill("Cooking",40,Requirement.Boost.NOT_ALLOWED),player(35,42)).status);
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(skill("Cooking",40,Requirement.Boost.UNSPECIFIED),player(35,42)).status);
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(skill("Cooking",40,Requirement.Boost.ALLOWED),player(42,35)).status);
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(skill("Cooking",40,Requirement.Boost.NOT_ALLOWED),player(42,35)).status);
    }
    @Test public void missingUnsupportedAndLoggedOutStateAreUnknown() {
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(skill("Sailing",30,Requirement.Boost.UNSPECIFIED),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(skill("Cooking",30,Requirement.Boost.UNSPECIFIED),PlayerContextSnapshot.unavailable(true)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(skill("Cooking",30,Requirement.Boost.UNSPECIFIED),PlayerContextSnapshot.unavailable(false)).status);
    }
    @Test public void allAndAnyApplyThreeValuedLogicRecursively() {
        Requirement met=skill("Cooking",10,Requirement.Boost.UNSPECIFIED), unmet=skill("Cooking",99,Requirement.Boost.UNSPECIFIED), unknown=unknown();
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(group(Requirement.Kind.ALL,met,met),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(group(Requirement.Kind.ALL,met,unmet,unknown),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(group(Requirement.Kind.ALL,met,unknown),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(group(Requirement.Kind.ANY,met,unknown),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(group(Requirement.Kind.ANY,unmet,unmet),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(group(Requirement.Kind.ANY,unmet,unknown),player(42,42)).status);
        Requirement nested=group(Requirement.Kind.ALL,met,group(Requirement.Kind.ANY,unmet,unknown));
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(nested,player(42,42)).status);
        assertEquals(2,evaluator.evaluate(nested,player(42,42)).children.get(1).children.size());
    }
    private Requirement extract(String html) {
        return RequirementExtractor.extract(Jsoup.parseBodyFragment(html,"https://oldschool.runescape.wiki/w/Test").body(),"Requirements").get(0).expression;
    }
    @Test public void parsedAlternativesWorkButAdditionalConditionsNeverBecomeMet() {
        Requirement direct = RequirementExtractor.extract(Jsoup.parseBodyFragment("<span data-skill='Cooking' data-level='10'>10 Cooking</span>").selectFirst("span"), "Level required").get(0).expression;
        assertEquals(RequirementEvaluator.Status.MET, evaluator.evaluate(direct, player(42,42)).status);
        Requirement alternative=extract("<ul><li>Either <span data-skill='Cooking' data-level='10'>10 Cooking</span> or <span data-skill='Agility' data-level='99'>99 Agility</span> (not boostable)</li></ul>");
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(alternative,player(42,42)).status);
        Requirement conditional=extract("<ul><li><span data-skill='Cooking' data-level='10'>10 Cooking</span> if the special route is available</li></ul>");
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(conditional,player(42,42)).status);
        Requirement mixed=extract("<ul><li><span data-skill='Cooking' data-level='10'>10 Cooking</span> (boostable) or <span data-skill='Agility' data-level='99'>99 Agility</span> (not boostable)</li></ul>");
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(mixed,player(42,42)).status);
    }
    @Test public void exactQuestCompletionWorksButPartialStagesAndVariantsStayUnknown() {
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(extract("<ul><li>Completion of <a href='/w/Cook%27s_Assistant'>Cook's Assistant</a></li></ul>"),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(extract("<ul><li>Completion of <a href='/w/Cook%27s_Assistant'>Cook's Assistant</a> up to a particular stage</li></ul>"),player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.UNKNOWN,evaluator.evaluate(extract("<ul><li>Completion of <a href='/w/Cook%27s_Assistant#Walkthrough'>Cook's Assistant</a></li></ul>"),player(42,42)).status);
    }
    @Test public void questPointsCompareCurrentStateWithoutCountingFutureRewards() {
        Requirement points=new Requirement(Requirement.Kind.QUEST_POINTS,"Quest points","",175,null,Requirement.Boost.UNSPECIFIED,SemanticExtractor.rich("175 Quest points"),List.of());
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(points,player(42,42)).status);
        PlayerContextSnapshot rich=new PlayerContextSnapshot(true,true,Map.of(),Map.of(),200);
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(points,rich).status);
        Requirement explained = extract("<ul><li><span data-skill='Quest points' data-level='175'>175 Quest points</span> (9 are earned for other parts, and 79 are gained by completing required quests)</li></ul>");
        assertEquals(RequirementEvaluator.Status.UNMET, evaluator.evaluate(explained, player(42,42)).status);
        assertEquals(RequirementEvaluator.Status.MET, evaluator.evaluate(explained, rich).status);
        Requirement conditional = extract("<ul><li><span data-skill='Quest points' data-level='175'>175 Quest points</span> (9 are earned if the alternative route is used)</li></ul>");
        assertEquals(RequirementEvaluator.Status.UNKNOWN, evaluator.evaluate(conditional, rich).status);
    }
    @Test public void recipeForDisasterKeepsStartingAndCompletionScopesAndGenuineUnknowns() throws Exception {
        com.wikipane.view.WikiPage page=new com.wikipane.view.WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        List<com.wikipane.view.ReaderLayout.ConditionGroup> groups=new ArrayList<>();
        for (com.wikipane.view.ArticleViewModel.Component c:page.view.area("requirements").components)groups.addAll(page.layout.section(c.id).requirements);
        com.wikipane.view.ReaderLayout.ConditionGroup start=groups.stream().filter(g->g.title.startsWith("To start")).findFirst().orElseThrow();
        assertEquals(RequirementEvaluator.Status.MET,evaluator.evaluate(start.condition,player(42,42)).status);
        com.wikipane.view.ReaderLayout.ConditionGroup full=groups.stream().filter(g->g.title.startsWith("To complete all")).findFirst().orElseThrow();
        assertEquals(RequirementEvaluator.Status.UNMET,evaluator.evaluate(full.condition,player(42,42)).status);
        assertTrue(full.conditions.stream().map(c->evaluator.evaluate(c,player(42,42))).anyMatch(r->r.status==RequirementEvaluator.Status.UNKNOWN));
        assertTrue(page.view.audit.valid());
    }
    @Test public void snapshotsAreImmutableAndDoNotShareAccountMutableMaps() {
        Map<String,PlayerContextSnapshot.Level> skills=new HashMap<>();skills.put("Cooking",new PlayerContextSnapshot.Level(42,42));
        PlayerContextSnapshot snapshot=new PlayerContextSnapshot(true,true,skills,Map.of(),null);skills.clear();
        assertEquals(1,snapshot.levels.size());
        try {snapshot.levels.clear();fail("Mutable snapshot");}catch(UnsupportedOperationException expected){}
    }
}
