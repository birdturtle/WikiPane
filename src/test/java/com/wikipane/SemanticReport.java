package com.wikipane;
import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument.*;
public final class SemanticReport {
 public static void main(String[] args)throws Exception{for(String fixture:new String[]{"Bronze_dagger","Recipe_for_Disaster","Vorkath","Cooking","Vorkath__Strategies","Recipe_for_Disaster__Full_guide"}){
 WikiDocument d=DocumentTest.document(fixture);System.out.println("\n"+d.title+" ["+d.semantic.type+"] "+d.semantic.nodes.size()+" nodes, "+d.semantic.contentUnits.size()+" source units, "+com.wikipane.view.ArticleViewModel.create(d.semantic).coveredUnits.size()+" exposed, missing "+com.wikipane.view.ArticleViewModel.create(d.semantic).missingUnits);
 for(com.wikipane.view.ArticleViewModel.Area area:com.wikipane.view.ArticleViewModel.create(d.semantic).areas)System.out.println("  "+area.id+" "+area.components.size()+" components, "+area.components.stream().mapToInt(c->c.text().length()).sum()+" characters, "+area.related.size()+" related guides");
 for(Fact fact:com.wikipane.view.ArticleViewModel.create(d.semantic).highlights)System.out.println("  Highlight "+fact.key+" "+fact.label+"="+fact.value.text);
 long count=d.semantic.nodes.stream().flatMap(n->n.requirements.stream()).mapToLong(s->s.groups.size()).sum();System.out.println("  "+count+" requirement groups");
 }}
}
