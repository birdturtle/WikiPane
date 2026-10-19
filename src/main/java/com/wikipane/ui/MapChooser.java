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

/** A compact article map index. Groups come from source maps and sections, never a global POI database. */
final class MapChooser extends JPanel {
    private final List<MapPreview> previews=new ArrayList<>();
    MapChooser(WikiPage page,ExecutorService workers,ImageCache images,ImageViewer viewer,Consumer<MapDestination> show,Runnable dismiss) {
        super(new BorderLayout());setName("wiki.mapChooser");
        JPanel groups=ReaderComponents.column();ReaderComponents components=new ReaderComponents(url->{});
        for(ArticleMaps.Group group:page.maps.groups) {
            JPanel body=ReaderComponents.column();body.setBorder(BorderFactory.createEmptyBorder(4,5,7,5));
            if(group.destinations.size()>1&&group.all().places.size()<=64)body.add(action("Show all on game map",()->{dismiss.run();show.accept(group.all());}));
            JPanel entries=ReaderComponents.column();body.add(entries);
            JButton more=action("Show more locations",()->{});int[] shown={0};
            Runnable append=()->{
                int end=Math.min(group.destinations.size(),shown[0]+30);
                for(int index=shown[0];index<end;index++) {
                    MapDestination destination=group.destinations.get(index);
                    MapDestination.Place place=destination.places.get(0);
                    String label=place.label,articlePrefix=place.location.source.article+" · ";
                    if(label.startsWith(articlePrefix))label=label.substring(articlePrefix.length());
                    if(label.startsWith(group.title+" · "))label=label.substring(group.title.length()+3);
                    entries.add(action(label,()->{dismiss.run();show.accept(destination);}));
                }
                shown[0]=end;more.setVisible(end<group.destinations.size());entries.revalidate();
            };
            more.addActionListener(event->append.run());append.run();body.add(more);
            if(group.image!=null) {
                JButton inspect=action("Inspect map image",()->viewer.open(this,group.image));
                inspect.setToolTipText("Open larger Wiki map image");body.add(inspect);
            }
            if(group.frame!=null) {
                MapPreview preview=new MapPreview(group.frame,page.document.maps,workers,images,viewer);previews.add(preview);
                body.add(preview);
            }
            JComponent disclosure=components.disclosure(group.title,body);
            groups.add(disclosure);
            if(groups.getComponentCount()==1)((JButton)disclosure.getComponent(0)).doClick();
            ((JButton)disclosure.getComponent(0)).addActionListener(event->SwingUtilities.invokeLater(this::loadVisible));
        }
        JScrollPane scroll=new JScrollPane(groups);scroll.setBorder(null);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(210,Math.min(430,Math.max(120,page.maps.groups.size()*65))));
        scroll.getViewport().addChangeListener(event->loadVisible());add(scroll);
        addHierarchyListener(event->{if(isShowing())SwingUtilities.invokeLater(this::loadVisible);});
    }
    private JButton action(String text,Runnable action) {
        JButton button=ReaderComponents.navigationButton("<html>"+escape(text)+"</html>");
        button.setToolTipText("Show "+text+" on game map");
        button.addActionListener(event->action.run());return button;
    }
    private void loadVisible(){for(MapPreview preview:previews)preview.request();}
    void stop(){previews.forEach(MapPreview::stop);}
}
