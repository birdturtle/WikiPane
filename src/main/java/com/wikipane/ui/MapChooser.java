package com.wikipane.ui;

import com.wikipane.cache.ImageCache;
import com.wikipane.view.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javax.swing.*;
import static com.wikipane.document.WikiDocumentParser.escape;

/** Source-organized maps. Nested collections materialize only when opened. */
final class MapChooser extends JPanel {
    private final List<MapPreview> previews=new ArrayList<>();
    private final WikiPage page;
    private final ExecutorService workers;
    private final ImageCache images;
    private final ImageViewer viewer;
    private final Consumer<MapDestination> show;
    private final Runnable dismiss;
    private boolean stopped;
    MapChooser(WikiPage page,ExecutorService workers,ImageCache images,ImageViewer viewer,Consumer<MapDestination> show,Runnable dismiss) {
        super(new BorderLayout());setName("wiki.mapChooser");
        this.page=page;this.workers=workers;this.images=images;this.viewer=viewer;this.show=show;this.dismiss=dismiss;
        JPanel groups=new WidthTrackingColumn();
        for(ArticleMaps.Group group:page.maps.groups)groups.add(group(group,groups.getComponentCount()==0));
        JScrollPane scroll=new JScrollPane(groups);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(210,Math.min(430,Math.max(120,page.maps.groups.size()*65))));
        scroll.getViewport().addChangeListener(event->loadVisible());add(scroll);
        addHierarchyListener(event->{if(isShowing())SwingUtilities.invokeLater(this::loadVisible);});
    }
    private JComponent group(ArticleMaps.Group group,boolean initiallyOpen) {
        JPanel container=ReaderComponents.column(),body=ReaderComponents.column();body.setVisible(false);
        body.setBorder(BorderFactory.createEmptyBorder(4,5,7,5));
        JButton toggle=ReaderComponents.navigationButton("");toggle.setName("wiki.disclosure");
        boolean[] loaded={false};
        Runnable update=()->{
            toggle.setText("<html>"+(body.isVisible()?"▾ ":"▸ ")+escape(group.title)+"</html>");
            toggle.getAccessibleContext().setAccessibleName(group.title+(body.isVisible()?", expanded":", collapsed"));
        };
        toggle.addActionListener(event->{
            if(stopped)return;
            body.setVisible(!body.isVisible());
            if(body.isVisible()&&!loaded[0]){populate(group,body);loaded[0]=true;}
            update.run();container.revalidate();SwingUtilities.invokeLater(this::loadVisible);
        });
        update.run();container.add(toggle);container.add(body);if(initiallyOpen)toggle.doClick();return container;
    }
    private void populate(ArticleMaps.Group group,JPanel body) {
        for(ArticleMaps.Group child:group.children)body.add(group(child,false));
        MapDestination all=group.all();
        if(group.children.isEmpty()&&group.destinations.size()>1&&all.unavailable.isEmpty()&&all.places.size()<=64)
            body.add(action("Show all on game map","Show these locations together on the game map",()->{dismiss.run();show.accept(all);}));
        JPanel entries=ReaderComponents.column();body.add(entries);
        JButton more=action("Show more locations","Reveal more locations in this list",()->{});int[] shown={0};
        Runnable append=()->{
            int end=Math.min(group.destinations.size(),shown[0]+30);
            for(int index=shown[0];index<end;index++) {
                MapDestination destination=group.destinations.get(index);
                MapDestination.Place place=destination.places.isEmpty()?destination.unavailable.get(0):destination.places.get(0);
                String label=place.label,articlePrefix=place.location.source.article+" · ";
                if(label.startsWith(articlePrefix))label=label.substring(articlePrefix.length());
                if(label.startsWith(group.title+" · "))label=label.substring(group.title.length()+3);
                if(destination.unavailable.isEmpty())entries.add(action(label,"Show "+label+" on the game map",()->{dismiss.run();show.accept(destination);}));
                else if(group.frame!=null) {
                    JButton inspect=action("Inspect "+label,()->viewer.open(this,group.frame,page.document.maps,group.title));
                    inspect.setToolTipText("This location is available in the Wiki preview, not the native game map.");entries.add(inspect);
                } else {
                    JButton source=action("View "+label+" on Wiki",()->net.runelite.client.util.LinkBrowser.browse(place.location.source.link()));
                    source.setToolTipText("Open the source in your browser; native map positioning is unavailable.");entries.add(source);
                }
            }
            shown[0]=end;more.setVisible(end<group.destinations.size());entries.revalidate();
        };
        more.addActionListener(event->append.run());append.run();body.add(more);
        if(group.image!=null) {
            JButton inspect=action("Inspect map image",()->viewer.open(this,group.image));
            inspect.setToolTipText("Open larger Wiki map image");body.add(inspect);
        }
        if(group.frame!=null) {
            boolean areas=group.frame.features(page.document.maps).stream().anyMatch(feature->!feature.point);
            if(areas)body.add(new ReaderComponents(url->{}).text("<p class='muted'>Areas and routes are shown in the preview.</p>"));
            MapPreview preview=new MapPreview(group.frame,page.document.maps,workers,images,viewer,group.title);previews.add(preview);body.add(preview);
        }
    }
    private static final class WidthTrackingColumn extends JPanel implements Scrollable {
        WidthTrackingColumn(){setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 16;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(16,visible.height-16);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private JButton action(String text,Runnable action) { return action(text,null,action); }
    private JButton action(String text,String tooltip,Runnable action) {
        JButton button=ReaderComponents.navigationButton("<html>"+escape(text)+"</html>");
        button.setToolTipText(tooltip);button.addActionListener(event->action.run());return button;
    }
    private void loadVisible(){if(!stopped)for(MapPreview preview:previews)preview.request();}
    void pause(){previews.forEach(MapPreview::pause);}
    void resume(){if(!stopped){previews.forEach(MapPreview::resume);SwingUtilities.invokeLater(this::loadVisible);}}
    void stop(){stopped=true;previews.forEach(MapPreview::stop);}
}
