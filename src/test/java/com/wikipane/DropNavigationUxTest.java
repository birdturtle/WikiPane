package com.wikipane;
import com.wikipane.view.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DropNavigationUxTest {
 @Test public void dropSectionsCannotBeShadowedByTheirFootnotes() throws Exception {
  WikiPage page=new WikiPage(DocumentTest.document("Vorkath"));
  assertTrue(page.navigation.sections.stream().noneMatch(d->d.area.equals("references")&&!d.anchor.isEmpty()));
  for(String section:new String[]{"Drops","Seeds","Shark_drop_table","Rare_drop_table","Tertiary"}) {
   assertEquals(section,"drops",page.navigation.areaForAnchor(section,"references"));
  }
  assertTrue(page.view.area("drops").components.stream().anyMatch(c->c.type==ArticleViewModel.ComponentType.TABLE));
  assertTrue(page.view.area("drops").components.stream().noneMatch(c->c.type==ArticleViewModel.ComponentType.REFERENCES));
  assertFalse(page.layout.references.isEmpty());
  for(ReaderLayout.ReferenceCard ref:page.layout.references)
   assertEquals("references",page.navigation.section(ref.id).area);
 }
}
