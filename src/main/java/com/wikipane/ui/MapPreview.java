package com.wikipane.ui;

import com.wikipane.cache.ImageCache;
import com.wikipane.semantic.WikiMaps;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;
import javax.swing.*;
import static com.wikipane.document.WikiDocumentParser.escape;

/** A static, bounded Wiki viewport. Clicking opens the same full image viewer used by article images. */
final class MapPreview extends JPanel {
    private final FittedThumbnail thumbnail;
    private final WikiMaps.Frame frame;
    private final WikiMaps maps;
    private final ExecutorService workers;
    private final ImageCache images;
    private boolean requested,stopped;
    private Future<?> task;
    MapPreview(WikiMaps.Frame frame,WikiMaps maps,ExecutorService workers,ImageCache images,ImageViewer viewer) {
        setLayout(new BorderLayout(0,4));setOpaque(false);setName("wiki.mapPreview");
        this.frame=frame;this.maps=maps;this.workers=workers;this.images=images;
        thumbnail=new FittedThumbnail(200,200,frame.width,frame.height,null,false);
        thumbnail.setText("Wiki map · click to inspect");thumbnail.setForeground(ReaderComponents.ACCENT);
        thumbnail.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        thumbnail.setHorizontalAlignment(SwingConstants.CENTER);thumbnail.setToolTipText("Inspect "+frame.title);
        thumbnail.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent event){
            if(SwingUtilities.isLeftMouseButton(event))viewer.open(thumbnail,frame,maps);
        }});
        java.awt.image.BufferedImage cached=images.maps.cached(frame,false);if(cached!=null)thumbnail.image(cached);
        add(thumbnail,BorderLayout.CENTER);
        JEditorPane caption=new ReaderComponents(url->{}).text(legend(frame,maps));add(caption,BorderLayout.SOUTH);
    }
    @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
    static String legend(WikiMaps.Frame frame,WikiMaps maps) {
        StringBuilder text=new StringBuilder("<b>").append(escape(frame.title)).append("</b>");int number=0;
        for(WikiMaps.Feature feature:frame.features(maps))text.append("<br>").append(++number).append(". ").append(escape(feature.label));
        StringBuilder represented=new StringBuilder(frame.title);
        for(WikiMaps.Feature feature:frame.features(maps))represented.append(' ').append(feature.label);
        if(!frame.caption.isBlank()&&!frame.caption.equals(frame.title)&&!frame.caption.equals(represented.toString()))
            text.append("<p class='muted'>").append(escape(frame.caption)).append("</p>");
        if(maps.incomplete)text.append("<p class='muted'>Some map features are unavailable here. Open the Wiki for the complete map.</p>");
        text.append("<p class='muted tiny'>Click map to inspect · OSRS Wiki</p>");return text.toString();
    }
    void request() {
        if(requested||stopped||!isShowing()||getVisibleRect().isEmpty())return;requested=true;
        try {task=workers.submit(()->{
            try {
                java.awt.image.BufferedImage image=images.maps.load(frame,maps,false);
                SwingUtilities.invokeLater(()->{if(!stopped){thumbnail.setText("");thumbnail.image(image);revalidate();repaint();}});
            } catch(Exception error) {SwingUtilities.invokeLater(()->{if(!stopped){thumbnail.setText("Map unavailable · click to retry");revalidate();}});}
        });}catch(RejectedExecutionException error){requested=false;thumbnail.setText("Click to inspect map");}
    }
    void stop(){stopped=true;if(task!=null)task.cancel(true);}
}
