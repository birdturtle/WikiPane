package com.wikipane.semantic;

import com.wikipane.semantic.SemanticDocument.Source;
import java.util.*;

/** Wiki map frames and explicit features, independent of Swing and mutable game state. */
public final class WikiMaps {
    public static final WikiMaps EMPTY=new WikiMaps(List.of(),List.of(),false);
    public final List<Frame> frames;
    public final List<Feature> features;
    public final boolean incomplete;
    public WikiMaps(List<Frame> frames,List<Feature> features,boolean incomplete) {
        this.frames=List.copyOf(frames);this.features=List.copyOf(features);this.incomplete=incomplete;
    }
    public static final class Feature {
        public final String label,group,mapId,plane,icon,raw;
        public final double x,y;
        public final boolean point;
        public final MapGeometry geometry;
        public final Source source;
        public Feature(String label,String group,String mapId,String plane,String icon,double x,double y,
                       boolean point,String raw,Source source) {
            this.label=label;this.group=group;this.mapId=mapId;this.plane=plane;this.icon=icon;
            this.x=x;this.y=y;this.point=point;this.raw=raw;this.source=source;
            MapGeometry parsed;
            try {parsed=MapGeometry.parse(new com.google.gson.JsonParser().parse(raw).getAsJsonObject());}
            catch(RuntimeException error){parsed=MapGeometry.parse(new com.google.gson.JsonObject());}
            this.geometry=parsed;
        }
    }
    public static final class Frame {
        public final String id,title,caption,mapId,plane,tileTemplate;
        public final double x,y;
        public final int width,height,zoom;
        public final Set<String> overlays;
        public final Source source;
        public Frame(String id,String title,String caption,String mapId,String plane,String template,double x,double y,
                     int width,int height,int zoom,Set<String> overlays,Source source) {
            this.id=id;this.title=title;this.caption=caption;this.mapId=mapId;this.plane=plane;this.tileTemplate=template;
            this.x=x;this.y=y;this.width=width;this.height=height;this.zoom=zoom;
            this.overlays=Set.copyOf(overlays);this.source=source;
        }
        public boolean contains(Feature feature) {
            if(feature.geometry.kind==MapGeometry.Kind.UNSUPPORTED||!overlays.contains(feature.group)||!plane.equals(feature.plane))return false;
            if(!mapId.equals(feature.mapId)&&!(Set.of("0","-1").contains(mapId)&&Set.of("0","-1").contains(feature.mapId)))return false;
            double scale=Math.pow(2,zoom);
            return feature.geometry.maxX>=x-width/2.0/scale&&feature.geometry.minX<=x+width/2.0/scale
                &&feature.geometry.maxY>=y-height/2.0/scale&&feature.geometry.minY<=y+height/2.0/scale;
        }
        public List<Feature> features(WikiMaps maps) {
            List<Feature> result=new ArrayList<>();for(Feature feature:maps.features)if(contains(feature))result.add(feature);
            return List.copyOf(result);
        }
    }
}
