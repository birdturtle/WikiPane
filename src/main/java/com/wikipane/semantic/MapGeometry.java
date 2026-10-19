package com.wikipane.semantic;

import com.google.gson.*;
import java.util.*;

/** Validated GeoJSON geometry. Areas/routes retain their shape and never become guessed point destinations. */
public final class MapGeometry {
    public enum Kind { POINT, LINE, AREA, UNSUPPORTED }
    public static final class Position {
        public final double x,y;
        Position(double x,double y) {this.x=x;this.y=y;}
    }
    public final Kind kind;
    public final List<List<Position>> paths;
    public final String stroke,fill;
    public final double opacity,fillOpacity,weight,minX,minY,maxX,maxY;
    private MapGeometry(Kind kind,List<List<Position>> paths,String stroke,String fill,double opacity,double fillOpacity,double weight) {
        this.kind=kind;this.paths=List.copyOf(paths);this.stroke=stroke;this.fill=fill;
        this.opacity=opacity;this.fillOpacity=fillOpacity;this.weight=weight;
        double x0=Double.POSITIVE_INFINITY,y0=x0,x1=Double.NEGATIVE_INFINITY,y1=x1;
        for(List<Position> path:paths)for(Position point:path){x0=Math.min(x0,point.x);y0=Math.min(y0,point.y);x1=Math.max(x1,point.x);y1=Math.max(y1,point.y);}
        minX=x0;minY=y0;maxX=x1;maxY=y1;
    }
    public static MapGeometry parse(JsonObject feature) {
        try {
            JsonObject geometry=feature.getAsJsonObject("geometry"),properties=feature.getAsJsonObject("properties");
            if(properties!=null&&properties.has("providerID")&&!properties.get("providerID").getAsString().equals("0"))return unsupported();
            String type=geometry.get("type").getAsString();JsonArray coordinates=geometry.getAsJsonArray("coordinates");
            List<List<Position>> paths=new ArrayList<>();Kind kind;
            switch(type) {
                case "Point":kind=Kind.POINT;paths.add(List.of(position(coordinates)));break;
                case "LineString":kind=Kind.LINE;paths.add(path(coordinates,2));break;
                case "Polygon":kind=Kind.AREA;for(JsonElement ring:coordinates)paths.add(path(ring.getAsJsonArray(),3));break;
                case "MultiLineString":kind=Kind.LINE;for(JsonElement line:coordinates)paths.add(path(line.getAsJsonArray(),2));break;
                case "MultiPolygon":kind=Kind.AREA;for(JsonElement polygon:coordinates)for(JsonElement ring:polygon.getAsJsonArray())paths.add(path(ring.getAsJsonArray(),3));break;
                default:return unsupported();
            }
            if(paths.isEmpty()||paths.stream().mapToInt(List::size).sum()>8192)return unsupported();
            String stroke=color(properties,"stroke","#3388ff"),fill=color(properties,"fill",stroke);
            return new MapGeometry(kind,paths,stroke,fill,number(properties,"stroke-opacity",1,0,1),
                number(properties,"fill-opacity",0.2,0,1),number(properties,"stroke-width",3,0,12));
        }catch(RuntimeException error){return unsupported();}
    }
    private static MapGeometry unsupported(){return new MapGeometry(Kind.UNSUPPORTED,List.of(),"#3388ff","#3388ff",1,0.2,3);}
    private static List<Position> path(JsonArray coordinates,int minimum) {
        if(coordinates.size()<minimum||coordinates.size()>8192)throw new IllegalArgumentException();
        List<Position> result=new ArrayList<>();for(JsonElement coordinate:coordinates)result.add(position(coordinate.getAsJsonArray()));return List.copyOf(result);
    }
    private static Position position(JsonArray pair) {
        double x=pair.get(0).getAsDouble(),y=pair.get(1).getAsDouble();
        if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>16384||y<0||y>16384)throw new IllegalArgumentException();
        return new Position(x,y);
    }
    private static double number(JsonObject properties,String key,double fallback,double low,double high) {
        if(properties==null||!properties.has(key))return fallback;
        double value=properties.get(key).getAsDouble();return Double.isFinite(value)?Math.max(low,Math.min(high,value)):fallback;
    }
    private static String color(JsonObject properties,String key,String fallback) {
        if(properties==null||!properties.has(key))return fallback;
        String value=properties.get(key).getAsString();return value.matches("#[0-9a-fA-F]{6}")?value:fallback;
    }
}
