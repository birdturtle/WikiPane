package com.wikipane.ui;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.tree.*;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/** Offline article composition and Swing only: no client, game input or live Wiki. */
public class UnifiedSectionsTest {
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();for(Component child:root.getComponents()){result.add(child);if(child instanceof Container)result.addAll(all((Container)child));}return result;
    }
    private static void collect(ArticleNavigation.ReadingSection node,List<ArticleNavigation.Destination> destinations,List<String> components) {
        destinations.add(node.destination);node.components.forEach(c -> components.add(c.id));
        for(ArticleNavigation.ReadingSection child:node.children)collect(child,destinations,components);
    }
    private static JButton header(Container root,String area,String anchor) {
        return (JButton)all(root).stream().filter(c -> "wiki.sectionHeading".equals(c.getName())&&c instanceof JButton)
            .filter(c -> {ArticleNavigation.Destination d=(ArticleNavigation.Destination)((JComponent)c).getClientProperty("wiki.destination");return d!=null&&d.area.equals(area)&&d.anchor.equals(anchor);})
            .findFirst().orElseThrow();
    }
    private static String text(Container root){StringBuilder s=new StringBuilder();for(Component child:all(root))if(child instanceof JEditorPane)s.append(com.wikipane.vendor.jsoup.Jsoup.parse(((JEditorPane)child).getText()).text()).append('\n');return s.toString();}
    @Test public void treeAndDropdownHaveIdenticalDestinationsAndEveryComponentHasOneOwner() throws Exception {
        for(String fixture:List.of("Vorkath","Cooking","Recipe_for_Disaster__Full_guide","Bronze_dagger","Prayer_potion")) {
            WikiPage page=new WikiPage(DocumentTest.document(fixture));
            List<ArticleNavigation.Destination> destinations=new ArrayList<>();List<String> ids=new ArrayList<>();
            page.navigation.roots.forEach(node -> collect(node,destinations,ids));
            assertEquals(page.navigation.sections,destinations);assertEquals(ids.size(),new HashSet<>(ids).size());
            Set<String> expected=new HashSet<>();page.view.areas.forEach(area -> area.components.forEach(c -> expected.add(c.id)));
            assertEquals(fixture,expected,new HashSet<>(ids));
        }
        WikiPage monster=new WikiPage(DocumentTest.document("Vorkath"));
        assertTrue(monster.navigation.sections.stream().anyMatch(d -> d.label.equals("Money making")&&!d.area.equals("drops")));
    }
    @Test public void onlyOverviewStartsExpandedAndDropdownSelectionOpensTheSameReaderBranch() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Vorkath"));
        ExecutorService workers=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient(),new Gson());
        try {SwingUtilities.invokeAndWait(() -> {
            ArticleReader reader=new ArticleReader(url -> {},workers,new ImageCache(wiki),pin -> {},error -> fail(error));
            JPanel body=reader.render(page,Map.of(),"Post-quest");
            assertEquals(Set.of("overview\0"),reader.expanded());
            for(ArticleNavigation.ReadingSection root:page.navigation.roots) {
                JButton button=header(body,root.destination.area,"");
                assertEquals(root.destination.area.equals("overview"),button.getClientProperty("wiki.expanded"));
            }
            assertFalse(text(body).contains("Rune longsword"));
            ArticleNavigation.Destination weapons=page.navigation.sections.stream().filter(d -> d.anchor.equals("Weapons_and_armour")).findFirst().orElseThrow();
            reader.revealDestination(weapons.area,weapons.anchor);
            JButton target=header(body,weapons.area,weapons.anchor);
            assertSame(target,reader.destination(weapons.area,weapons.anchor));
            assertTrue(reader.expanded().contains("drops\0"));assertTrue(reader.expanded().contains("drops\0Weapons_and_armour"));
            assertTrue(text(body).contains("Rune longsword"));
            assertEquals(page.document.url+"#Weapons_and_armour",target.getClientProperty("wiki.heading.source"));
            Map<String,Integer> state=reader.tableState();JPanel restored=reader.render(page,state,"Post-quest");
            assertTrue(reader.expanded().contains("drops\0Weapons_and_armour"));
            assertEquals(Boolean.TRUE,header(restored,"drops","Weapons_and_armour").getClientProperty("wiki.expanded"));
            header(restored,"drops","").doClick();
            assertFalse(reader.expanded().contains("drops\0Weapons_and_armour"));
            reader.clear();
        });}finally{wiki.close();workers.shutdownNow();}
    }
    @Test public void popupIsCollapsedSearchableAndUsesTheActualSectionObjects() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Vorkath"));
        SwingUtilities.invokeAndWait(() -> {
            List<ArticleNavigation.Destination> selected=new ArrayList<>();
            SectionNavigator navigator=new SectionNavigator(page.navigation.roots,Set.of("overview\0"),"overview","",selected::add);
            JTree tree=(JTree)all(navigator).stream().filter(c -> c instanceof JTree).findFirst().orElseThrow();
            DefaultMutableTreeNode root=(DefaultMutableTreeNode)tree.getModel().getRoot();
            for(int i=0;i<root.getChildCount();i++) {
                DefaultMutableTreeNode node=(DefaultMutableTreeNode)root.getChildAt(i);
                ArticleNavigation.ReadingSection section=(ArticleNavigation.ReadingSection)node.getUserObject();
                if(!section.destination.area.equals("overview"))assertFalse(tree.isExpanded(new TreePath(node.getPath())));
            }
            JTextField filter=(JTextField)all(navigator).stream().filter(c -> c instanceof JTextField).findFirst().orElseThrow();filter.setText("weapons and armour");
            tree.getActionMap().get("choose").actionPerformed(new java.awt.event.ActionEvent(tree,0,"choose"));
            assertEquals(1,selected.size());assertEquals("Weapons_and_armour",selected.get(0).anchor);
            assertSame(page.navigation.sections.stream().filter(d -> d.anchor.equals("Weapons_and_armour")).findFirst().orElseThrow(),selected.get(0));
            filter.setText("nonexistent-section-xx");assertEquals(0,tree.getRowCount());
        });
    }
}
