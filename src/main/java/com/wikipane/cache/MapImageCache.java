package com.wikipane.cache;

import com.wikipane.api.WikiClient;
import com.wikipane.semantic.WikiMaps;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Composes only the selected viewport. No browser, scripts, disk cache, or world-map download. */
public final class MapImageCache {
    private final WikiClient wiki;
    private final WikiCache<String,BufferedImage> tiles=new WikiCache<>(32,600_000);
    private final WikiCache<String,BufferedImage> frames=new WikiCache<>(4,600_000);
    public MapImageCache(WikiClient wiki) {this.wiki=wiki;}
    private static int factor(WikiMaps.Frame frame,boolean detail) {
        return detail&&frame.zoom<5&&frame.width<=512&&frame.height<=512?2:1;
    }
    private static String key(WikiMaps.Frame frame,boolean detail) {
        return frame.source.url+"@"+frame.source.revision+":"+frame.id+":"+factor(frame,detail);
    }
    public BufferedImage cached(WikiMaps.Frame frame,boolean detail) {return frames.get(key(frame,detail));}
    public void clear(){tiles.clear();frames.clear();}
    public BufferedImage load(WikiMaps.Frame frame,WikiMaps maps,boolean detail) throws IOException {
        if(!Double.isFinite(frame.x)||!Double.isFinite(frame.y)||frame.x<0||frame.x>16384||frame.y<0||frame.y>16384
            ||frame.width<1||frame.height<1||frame.zoom< -3||frame.zoom>5)throw new IOException("Unsupported map viewport.");
        int factor=factor(frame,detail);
        String key=key(frame,detail);
        BufferedImage old=frames.get(key);if(old!=null)return old;
        if(frame.tileTemplate.isBlank())throw new IOException("This Wiki map has no supported image tiles.");
        int width=frame.width*factor,height=frame.height*factor,zoom=frame.zoom+(factor==2?1:0);
        double scale=Math.pow(2,zoom),left=frame.x-width/2.0/scale,top=frame.y+height/2.0/scale;
        double tileSize=256/scale;
        int x0=(int)Math.floor(left/tileSize),x1=(int)Math.floor((left+width/scale)/tileSize);
        int y0=(int)Math.floor((top-height/scale)/tileSize),y1=(int)Math.floor(top/tileSize);
        if(width>1200||height>1200||(long)(x1-x0+1)*(y1-y0+1)>36)throw new IOException("This map is too large to preview.");
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try {
            graphics.setColor(new Color(28,28,28));graphics.fillRect(0,0,width,height);
            for(int y=y1;y>=y0;y--)for(int x=x0;x<=x1;x++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                String url=frame.tileTemplate.replace("{zoom}",Integer.toString(zoom)).replace("{plane}",frame.plane)
                    .replace("{x}",Integer.toString(x)).replace("{y}",Integer.toString(y));
                graphics.drawImage(tile(url),(int)Math.round((x*tileSize-left)*scale),(int)Math.round((top-(y+1)*tileSize)*scale),null);
            }
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,12));
            int number=0;
            java.util.List<String> references=new java.util.ArrayList<>();java.util.List<java.awt.Point> positions=new java.util.ArrayList<>();
            int areaReference=0,routeReference=0;
            for(WikiMaps.Feature feature:frame.features(maps))if(!feature.point) {
                com.wikipane.semantic.MapGeometry geometry=feature.geometry;
                java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double(java.awt.geom.Path2D.WIND_EVEN_ODD);
                for(java.util.List<com.wikipane.semantic.MapGeometry.Position> ring:geometry.paths) {
                    boolean first=true;
                    for(com.wikipane.semantic.MapGeometry.Position point:ring) {
                        double x=width/2.0+(point.x-frame.x)*scale,y=height/2.0-(point.y-frame.y)*scale;
                        if(first){path.moveTo(x,y);first=false;}else path.lineTo(x,y);
                    }
                    if(geometry.kind==com.wikipane.semantic.MapGeometry.Kind.AREA)path.closePath();
                }
                if(geometry.kind==com.wikipane.semantic.MapGeometry.Kind.AREA) {
                    graphics.setComposite(AlphaComposite.SrcOver.derive((float)geometry.fillOpacity));
                    graphics.setColor(Color.decode(geometry.fill));graphics.fill(path);
                }
                graphics.setComposite(AlphaComposite.SrcOver.derive((float)geometry.opacity));
                graphics.setStroke(new BasicStroke((float)geometry.weight,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                graphics.setColor(Color.decode(geometry.stroke));if(geometry.weight>0)graphics.draw(path);graphics.setComposite(AlphaComposite.SrcOver);
                boolean area=geometry.kind==com.wikipane.semantic.MapGeometry.Kind.AREA;
                references.add(area?"A"+(++areaReference):"R"+(++routeReference));
                java.awt.geom.Rectangle2D bounds=path.getBounds2D();
                positions.add(new java.awt.Point((int)Math.round(bounds.getCenterX()),(int)Math.round(bounds.getCenterY())));
            }
            // Reference badges identify shapes in the static legend; these never become game destinations.
            for(int index=0;index<references.size();index++) {
                java.awt.Point point=positions.get(index);if(point.x<0||point.x>=width||point.y<0||point.y>=height)continue;
                String label=references.get(index);int labelWidth=graphics.getFontMetrics().stringWidth(label)+6;
                graphics.setColor(new Color(20,20,20,210));graphics.fillRoundRect(point.x-labelWidth/2,point.y-9,labelWidth,17,4,4);
                graphics.setColor(Color.WHITE);graphics.drawString(label,point.x-labelWidth/2+3,point.y+4);
            }
            for(WikiMaps.Feature feature:frame.features(maps))if(feature.point) {
                int x=(int)Math.round(width/2.0+(feature.x-frame.x)*scale),y=(int)Math.round(height/2.0-(feature.y-frame.y)*scale);
                graphics.setColor(Color.BLACK);graphics.fillOval(x-8,y-8,17,17);
                graphics.setColor(color(feature.icon));graphics.fillOval(x-7,y-7,15,15);
                String label=Integer.toString(++number);graphics.setColor(Color.BLACK);
                graphics.drawString(label,x-graphics.getFontMetrics().stringWidth(label)/2,y+4);
            }
        } finally {graphics.dispose();}
        frames.put(key,image);return image;
    }
    public static Color color(String icon) {
        String key=icon.toLowerCase(Locale.ROOT);
        if(key.startsWith("red"))return new Color(238,107,98);
        if(key.startsWith("blue"))return new Color(126,182,244);
        if(key.startsWith("green"))return new Color(129,204,143);
        if(key.startsWith("yellow"))return new Color(245,209,96);
        if(key.startsWith("cyan"))return new Color(118,215,218);
        if(key.startsWith("purple"))return new Color(201,159,230);
        return new Color(245,209,96);
    }
    private BufferedImage tile(String url) throws IOException {
        BufferedImage old=tiles.get(url);if(old!=null)return old;
        byte[] bytes=wiki.mapTile(url);
        try(ImageInputStream input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Map image unavailable.");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                if(reader.getWidth(0)!=256||reader.getHeight(0)!=256)throw new IOException("Unexpected map tile dimensions.");
                BufferedImage image=reader.read(0);tiles.put(url,image);return image;
            } finally {reader.dispose();}
        }
    }
}
