package com.wikipane;
import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PresentationTest {
    @Test public void semanticSectionsKeepEveryBlockAndResolveSourceAnchors()throws Exception{
        WikiDocument document=DocumentTest.document("Bronze_dagger");ArticleViewModel view=ArticleViewModel.create(document.semantic);
        assertTrue(view.missingUnits.isEmpty());assertEquals("stats",view.sourceArea("Combat_stats").id);assertEquals("info",view.sourceArea("Unpoisoned").id);
        assertTrue(document.semantic.sections.stream().anyMatch(s->s.anchor.equals("Combat_stats")&&s.role==Role.COMBAT));
        assertEquals(document.blocks.stream().filter(b->!(b instanceof WikiDocument.Heading)).count(),document.semantic.nodes.size());
    }
    @Test public void nativeFactsKeepValuesAndOriginalUnits()throws Exception{
        WikiDocument document=DocumentTest.document("Bronze_dagger");Fact members=document.semantic.nodes.stream().flatMap(n->n.facts.stream()).filter(f->f.variant.equals("Unpoisoned")&&f.label.equals("Members")).findFirst().get();assertEquals("No",members.value.text);assertEquals(document.url,members.source.url);assertTrue(ArticleViewModel.create(document.semantic).coveredUnits.contains(members.source.unit));
    }
    @Test public void combatPresentationKeepsAttackAndDefenceGroupsAndExactValues()throws Exception{
        WikiDocument doc=DocumentTest.document("Bronze_dagger");Node node=doc.semantic.nodes.stream().filter(n->n.table!=null&&n.table.kind.equals("Combat bonuses")&&n.table.variant.equals("Unpoisoned")).findFirst().get();
        assertTrue(node.facts.stream().anyMatch(f->f.group.equals("Attack bonuses")&&f.label.equals("Stab")&&f.value.text.equals("+4")));assertTrue(node.facts.stream().anyMatch(f->f.group.equals("Defence bonuses")));
    }
}
