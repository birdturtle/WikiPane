package com.wikipane.semantic;

import com.google.gson.*;
import com.wikipane.api.WikiUrls;
import com.wikipane.semantic.SemanticDocument.Source;
import java.util.*;
import java.util.regex.*;
import okhttp3.HttpUrl;
import com.wikipane.html.WikiHtml;
import com.wikipane.html.Element;

/** Consumes server-rendered frames and action=parse jsconfigvars. Never executes Wiki JavaScript. */
public final class WikiMapExtractor {
    private WikiMapExtractor() {}
    public static WikiMaps extract(Element root,JsonObject parse,String article,long revision) {
        List<WikiMaps.Frame> frames=new ArrayList<>();List<WikiMaps.Feature> features=new ArrayList<>();boolean incomplete=false;
        String section="",heading="Map";int ordinal=0;
        for(Element element:root.getAllElements()) {
            if(element.tagName().matches("h[1-6]")) {section=element.id();Element own=element.clone();own.select(".mw-editsection").remove();heading=own.text();}
            if(!element.hasClass("mw-kartographer-map"))continue;
            try {
                int width=Integer.parseInt(element.attr("data-width")),height=Integer.parseInt(element.attr("data-height"));
                int zoom=Integer.parseInt(element.attr("data-zoom"));
                double x=Double.parseDouble(element.attr("data-lon")),y=Double.parseDouble(element.attr("data-lat"));
                if(width<1||width>1200||height<1||height>1200||zoom< -3||zoom>5||!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>16384||y<0||y>16384)throw new IllegalArgumentException();
                String template="";
                Matcher tile=Pattern.compile("url\\(['\"]?([^)'\"]+)").matcher(element.attr("style"));
                if(tile.find()) {
                    HttpUrl url=WikiUrls.resolve(tile.group(1).trim());
                    if(WikiUrls.mapTile(url))template=url.toString().replaceFirst("/(-?[0-9]+)/(-?[0-9]+)_(-?[0-9]+)_(-?[0-9]+)\\.png$","/{zoom}/{plane}_{x}_{y}.png");
                }
                Element container=element.closest(".mw-kartographer-container");
                Element caption=container==null?null:container.selectFirst(".thumbcaption");
                String captionText=caption==null?"":caption.text();String title=heading;
                if(caption!=null) {
                    Element own=caption.clone();own.select("ul,ol").remove();
                    if(!own.text().isBlank())title=own.text();
                }
                if(title.isBlank()||title.equals("Information"))title="Map";
                Set<String> overlays=new LinkedHashSet<>();
                JsonArray groups=element.attr("data-overlays").isBlank()?new JsonArray():new JsonParser().parse(element.attr("data-overlays")).getAsJsonArray();
                for(JsonElement group:groups)if(group.isJsonPrimitive())overlays.add(group.getAsString());
                String id="map-frame-"+(ordinal++);element.attr("data-wikipane-frame",id);
                frames.add(new WikiMaps.Frame(id,title,captionText,element.attr("data-mapid"),element.attr("data-plane"),template,
                    x,y,width,height,zoom,overlays,new Source(article,WikiUrls.article(article),revision,section,heading,id)));
            } catch(RuntimeException error) {
                incomplete=true;
                String id="map-frame-"+(ordinal++);element.attr("data-wikipane-frame",id);
                frames.add(new WikiMaps.Frame(id,heading,"Map preview unavailable",element.attr("data-mapid"),element.attr("data-plane"),"",
                    0,0,300,300,1,Set.of(),new Source(article,WikiUrls.article(article),revision,section,heading,id)));
            }
        }
        try {
            JsonObject config=parse.has("jsconfigvars")?parse.getAsJsonObject("jsconfigvars"):new JsonObject();
            JsonObject data=config.has("wgKartographerLiveData")?config.getAsJsonObject("wgKartographerLiveData"):new JsonObject();
            int index=0;
            for(Map.Entry<String,JsonElement> group:data.entrySet()) {
                for(JsonElement collection:group.getValue().getAsJsonArray()) {
                    for(JsonElement value:collection.getAsJsonObject().getAsJsonArray("features")) {
                        if(features.size()>=1000) {incomplete=true;break;}
                        try {
                            JsonObject feature=value.getAsJsonObject(),geometry=feature.getAsJsonObject("geometry"),properties=feature.getAsJsonObject("properties");
                            boolean point="Point".equals(string(geometry,"type"))&&(!properties.has("providerID")||"0".equals(string(properties,"providerID")));
                            double x=Double.NaN,y=Double.NaN;
                            if(point) {JsonArray coords=geometry.getAsJsonArray("coordinates");x=coords.get(0).getAsDouble();y=coords.get(1).getAsDouble();point=Double.isFinite(x)&&Double.isFinite(y);}
                            Element description=WikiHtml.parseBodyFragment(string(properties,"description")).body();
                            description.select("p").stream().filter(p->p.text().matches("(?is)X/Y:.*")).forEach(Element::remove);
                            String label=string(properties,"title");if(label.isBlank())label=description.text();if(label.isBlank())label="Map location";
                            Source source=new Source(article,WikiUrls.article(article),revision,"","Map locations","map-feature-"+(index++));
                            features.add(new WikiMaps.Feature(label,group.getKey(),string(properties,"mapID"),string(properties,"plane"),string(properties,"icon"),x,y,point,feature.toString(),source));
                            if(features.get(features.size()-1).geometry.kind==MapGeometry.Kind.UNSUPPORTED)incomplete=true;
                        } catch(RuntimeException error) {incomplete=true;}
                    }
                }
            }
        } catch(RuntimeException error) {incomplete=true;}
        if(!parse.has("jsconfigvars")&&frames.stream().anyMatch(frame->!frame.overlays.isEmpty()))incomplete=true;
        return new WikiMaps(frames,features,incomplete);
    }
    private static String string(JsonObject object,String key) {
        return object!=null&&object.has(key)&&object.get(key).isJsonPrimitive()?object.get(key).getAsString():"";
    }
}
