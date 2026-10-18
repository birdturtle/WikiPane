package com.wikipane.ui;

import com.wikipane.cache.ImageCache;
import com.wikipane.context.*;
import com.wikipane.pin.PinSelection;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.util.LinkBrowser;

/** One scrollable article. Its disclosure tree is also the dropdown's navigation model. */
final class ArticleReader {
    private final Consumer<String> links, feedback;
    private final ExecutorService workers;
    private final ImageCache images;
    private final ImageViewer viewer;
    private final ReaderSelection selection;
    private final ReaderComponents components;
    private final Map<String, SectionView> sections=new LinkedHashMap<>();
    private final List<ContentRenderer> fragments=new ArrayList<>();
    private Map<String,Integer> saved=Map.of();
    private WikiPage page;
    private String variant="";
    private JPanel body;
    private Consumer<List<Requirement>> contextRequests=ignored -> {};
    private Consumer<String> skillRequests=ignored -> {};
    private Consumer<MapDestination> mapRequests=ignored -> {};
    private Runnable changed=() -> {};
    private PlayerContextSnapshot player=PlayerContextSnapshot.unavailable(false);
    private Map<String,Icon> skillIcons=Map.of();
    ArticleReader(Consumer<String> links,ExecutorService workers,ImageCache images,Consumer<PinSelection> pin,Consumer<String> feedback) {
        this.links=links;this.workers=workers;this.images=images;this.feedback=feedback;
        components=new ReaderComponents(links);viewer=new ImageViewer(workers,images);
        selection=pin==null?null:new ReaderSelection(pin,feedback);
    }
    void changed(Runnable callback) {changed=callback;}
    void contextRequests(Consumer<List<Requirement>> callback) {contextRequests=callback;}
    void skillRequests(Consumer<String> callback) {skillRequests=callback;}
    void mapRequests(Consumer<MapDestination> callback) {mapRequests=callback;}
    void playerContext(PlayerContextSnapshot snapshot) {player=snapshot;for(ContentRenderer renderer:List.copyOf(fragments))renderer.playerContext(snapshot);}
    void skillIcons(Map<String,Icon> icons) {skillIcons=Map.copyOf(icons);for(ContentRenderer renderer:fragments)renderer.skillIcons(icons);}
    void closeImageViewer() {viewer.close();}
    JPanel render(WikiPage value,Map<String,Integer> state,String selectedVariant) {
        clear(false);page=value;saved=new HashMap<>(state);variant=selectedVariant;body=ReaderComponents.column();
        for(ArticleNavigation.ReadingSection root:page.navigation.roots) body.add(new SectionView(root,null).container);
        List<Requirement> requirements=new ArrayList<>();
        for(Node node:page.document.semantic.nodes)for(RequirementSet set:node.requirements)
            for(RequirementGroup group:set.groups)requirements.add(group.expression);
        for(com.wikipane.semantic.Recipe recipe:page.document.semantic.recipes)
            for(com.wikipane.semantic.Recipe.Skill skill:recipe.skills)requirements.add(skill.requirement);
        contextRequests.accept(List.copyOf(requirements));skillRequests.accept(SkillGuideProgress.skill(page.document.title));
        body.add(components.text("<p class='source'>Content: <a href='"+com.wikipane.document.WikiDocumentParser.escape(page.document.url)
            +"'>OSRS Wiki</a> · <a href='https://oldschool.runescape.wiki/w/RuneScape:Copyrights'>CC BY-NC-SA</a></p>"));
        refreshSelection();return body;
    }
    private final class SectionView {
        final ArticleNavigation.ReadingSection node;
        final SectionView parent;
        final JPanel container=ReaderComponents.column(), content=ReaderComponents.column();
        final JButton header=new JButton() {
            @Override public Dimension getPreferredSize() {
                javax.swing.text.View html=(javax.swing.text.View)getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
                int width=getParent()!=null&&getParent().getWidth()>0?getParent().getWidth():200;
                Insets inset=getInsets();
                if(html==null)return new Dimension(width,30);
                html.setSize(Math.max(1,width-inset.left-inset.right),0);
                return new Dimension(width,Math.max(30,(int)Math.ceil(html.getPreferredSpan(javax.swing.text.View.Y_AXIS))+inset.top+inset.bottom));
            }
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
            @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
        };
        ContentRenderer renderer;
        boolean materialized, open;
        SectionView(ArticleNavigation.ReadingSection node,SectionView parent) {
            this.node=node;this.parent=parent;sections.put(node.key(),this);
            container.setName("wiki.articleSection");container.putClientProperty("wiki.destination",node.destination);
            header.setName("wiki.sectionHeading");header.putClientProperty("wiki.destination",node.destination);
            header.putClientProperty("wiki.heading.source",node.source.link());
            header.setAlignmentX(Component.LEFT_ALIGNMENT);header.setHorizontalAlignment(SwingConstants.LEFT);
            header.setFont(new Font(Font.SANS_SERIF,Font.BOLD,parent==null?13:12));
            header.setForeground(ReaderComponents.ACCENT);header.setBackground(ReaderComponents.SURFACE);
            header.setMargin(new Insets(7,7,7,7));
            // HTML wraps full labels at actual sidebar width; no fixed one-line clipping.
            container.setBorder(BorderFactory.createEmptyBorder(parent==null?4:3,0,2,0));
            content.setBorder(BorderFactory.createEmptyBorder(3,parent==null?0:6,2,0));
            container.add(header);container.add(content);
            header.addActionListener(event -> setOpen(!open));
            JPopupMenu source=new JPopupMenu();JMenuItem original=new JMenuItem("Open source section in browser");
            original.addActionListener(event -> LinkBrowser.browse(node.source.link()));source.add(original);header.setComponentPopupMenu(source);
            open=saved.getOrDefault(stateKey(),parent==null&&node.destination.area.equals("overview")?1:0)==1;
            updateHeader();content.setVisible(open);
            if(open)materialize();
        }
        String stateKey(){return "@expanded:"+node.key();}
        void updateHeader() {
            header.setText("<html>"+(open?"▾ ":"▸ ")+com.wikipane.document.WikiDocumentParser.escape(node.destination.label)+"</html>");
            header.getAccessibleContext().setAccessibleName(node.destination.label+(open?", expanded":", collapsed"));
            header.putClientProperty("wiki.expanded",open);
        }
        void setOpen(boolean value) {
            if(open==value) return;
            open=value;if(open)materialize();content.setVisible(open);updateHeader();
            if(selection!=null)refreshSelection();
            container.revalidate();container.repaint();changed.run();
            if(open)SwingUtilities.invokeLater(ArticleReader.this::loadVisibleImages);
        }
        void materialize() {
            if(materialized)return;materialized=true;
            if(!node.components.isEmpty() || parent==null&&!page.navigation.area(node.destination.area).related.isEmpty()) {
                renderer=new ContentRenderer(links,workers,images,null,feedback);fragments.add(renderer);
                renderer.fragmentScope(node.key());renderer.imageViewer(viewer);renderer.contentChanged(ArticleReader.this::refreshSelection);
                renderer.mapRequests(mapRequests);renderer.playerContext(player);renderer.skillIcons(skillIcons);
                ArticleViewModel.Area area=page.navigation.area(node.destination.area);
                content.add(renderer.renderFragment(page,new ArticleViewModel.Area(area.id,node.destination.label,node.components,
                    parent==null?area.related:List.of()),saved,variant,parent==null&&area.id.equals("overview")));
            }
            for(ArticleNavigation.ReadingSection child:node.children)content.add(new SectionView(child,this).container);
            if(content.getComponentCount()==0)content.add(components.text("No readable content was returned for this section."));
        }
        void reveal() {if(parent!=null)parent.reveal();setOpen(true);}
    }
    private void refreshSelection() {if(selection!=null&&body!=null)selection.refresh(body);}
    private SectionView section(String area,String anchor) {
        if(page==null)return null;
        ArticleNavigation.ReadingSection node=page.navigation.readingSection(area,anchor);
        if(node==null)return sections.get(area+"\0");
        // Opening ancestors creates only the requested branch's content, not the whole article.
        return ensure(node,page.navigation.roots,null);
    }
    private SectionView ensure(ArticleNavigation.ReadingSection target,List<ArticleNavigation.ReadingSection> nodes,SectionView parent) {
        for(ArticleNavigation.ReadingSection node:nodes) {
            if(!contains(node,target))continue;
            SectionView view=sections.get(node.key());
            if(view==null&&parent!=null){parent.reveal();view=sections.get(node.key());}
            if(node==target)return view;
            if(view!=null){view.reveal();return ensure(target,node.children,view);}
        }
        return null;
    }
    private boolean contains(ArticleNavigation.ReadingSection node,ArticleNavigation.ReadingSection target) {
        return node==target||node.children.stream().anyMatch(child -> contains(child,target));
    }
    void revealDestination(String area,String anchor) {
        SectionView target=section(area,anchor);if(target==null)return;target.reveal();
        if(!anchor.isEmpty()&&target.renderer!=null)target.renderer.revealAnchor(anchor);
        refreshSelection();
    }
    JComponent destination(String area,String anchor) {
        SectionView section=section(area,anchor);if(section==null)return null;
        if(!anchor.isEmpty()&&!section.node.destination.anchor.equals(anchor)&&section.renderer!=null) {
            JComponent nested=section.renderer.anchor(anchor);if(nested!=null)return nested;
        }
        return section.header;
    }
    ArticleNavigation.Destination visibleDestination(Container root,int y) {
        ArticleNavigation.Destination nearest=page==null?null:page.navigation.overview;int best=-1;
        for(SectionView view:sections.values()) {
            if(view.header.getWidth()<=0||view.header.getHeight()<=0||!visible(view.header,root))continue;
            int top=SwingUtilities.convertPoint(view.header,0,0,root).y;
            if(top<=y&&top>=best){nearest=view.node.destination;best=top;}
        }
        return nearest;
    }
    private boolean visible(Component child,Container root) {
        for(Component current=child;current!=root;current=current.getParent())if(current==null||!current.isVisible())return false;
        return true;
    }
    Set<String> expanded() {
        Set<String> open=new HashSet<>();
        for(SectionView section:sections.values()) {
            boolean visible=true;for(SectionView parent=section;parent!=null;parent=parent.parent)if(!parent.open){visible=false;break;}
            if(visible)open.add(section.node.key());
        }
        return open;
    }
    Map<String,Integer> tableState() {
        Map<String,Integer> state=new HashMap<>(saved);
        for(ContentRenderer fragment:fragments)state.putAll(fragment.tableState());
        for(SectionView section:sections.values())state.put(section.stateKey(),section.open?1:0);
        return state;
    }
    JComponent currentLevelTarget() {
        if(page==null)return null;
        Integer base=SkillGuideProgress.base(SkillGuideProgress.skill(page.document.title),player);if(base==null)return null;
        TreeMap<Integer,ArticleNavigation.ReadingSection> targets=new TreeMap<>();
        for(ArticleNavigation.ReadingSection root:page.navigation.roots)collectLevels(root,targets);
        Integer level=SkillGuideProgress.target(targets.keySet(),base);if(level==null)return null;
        ArticleNavigation.ReadingSection node=targets.get(level);SectionView view=section(node.destination.area,node.destination.anchor);
        if(view==null)return null;view.reveal();
        return view.renderer==null?null:view.renderer.currentLevelTarget();
    }
    private void collectLevels(ArticleNavigation.ReadingSection node,Map<Integer,ArticleNavigation.ReadingSection> targets) {
        for(ArticleViewModel.Component component:node.components)if(component.node.table!=null)
            for(ReaderLayout.Record record:page.layout.records(component.node.table)) {
                Integer level=SkillGuideProgress.level(record);if(level!=null)targets.putIfAbsent(level,node);
            }
        for(ArticleNavigation.ReadingSection child:node.children)collectLevels(child,targets);
    }
    void loadVisibleImages() {for(ContentRenderer renderer:List.copyOf(fragments))renderer.loadVisibleImages();}
    JEditorPane text(String html) {return components.text(html);}
    void clear() {clear(true);}
    private void clear(boolean notify) {
        for(ContentRenderer renderer:fragments)renderer.clear();fragments.clear();sections.clear();
        if(selection!=null)selection.clear();body=null;page=null;
        if(notify){contextRequests.accept(List.of());skillRequests.accept("");}
    }
}
