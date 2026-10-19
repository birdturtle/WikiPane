package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.semantic.WikiMaps;
import java.util.*;

/** Article-scoped map index. It never crawls linked pages or reads the player's state. */
public final class ArticleMaps {
    public static final class Group {
        public final String title;
        public final List<MapDestination> destinations;
        public final List<Group> children;
        public final WikiMaps.Frame frame;
        public final com.wikipane.document.WikiDocument.Image image;
        Group(String title,List<MapDestination> destinations,WikiMaps.Frame frame) {
            this(title,destinations,frame,null);
        }
        Group(String title,List<MapDestination> destinations,WikiMaps.Frame frame,com.wikipane.document.WikiDocument.Image image) {
            this(title,destinations,frame,image,List.of());
        }
        Group(String title,List<Group> children) {this(title,List.of(),null,null,children);}
        private Group(String title,List<MapDestination> destinations,WikiMaps.Frame frame,com.wikipane.document.WikiDocument.Image image,List<Group> children) {
            this.title=title;this.destinations=List.copyOf(destinations);this.frame=frame;this.image=image;this.children=List.copyOf(children);
        }
        public MapDestination all() {
            List<MapDestination.Place> places=new ArrayList<>();for(MapDestination destination:destinations){places.addAll(destination.places);places.addAll(destination.unavailable);}
            for(Group child:children){places.addAll(child.all().places);places.addAll(child.all().unavailable);}
            return MapDestination.create(places,"Show all locations");
        }
    }
    public final List<Group> groups;
    public ArticleMaps(SemanticDocument document,ReaderLayout layout) {
        List<Group> groups=new ArrayList<>();Set<String> used=new HashSet<>();
        Map<String,List<Group>> frameGroups=new LinkedHashMap<>();Set<String> frameKeys=new HashSet<>();
        for(WikiMaps.Frame frame:document.maps.frames) {
            String title=frameTitle(frame,document);
            String frameKey=title+":"+frame.caption+":"+frame.tileTemplate+":"+frame.source.section+":"+frame.mapId+":"+frame.plane+":"+frame.x+":"+frame.y+":"+frame.width+":"+frame.height+":"+frame.zoom+":"+new TreeSet<>(frame.overlays);
            if(!frameKeys.add(frameKey))continue;
            List<MapDestination> destinations=new ArrayList<>();
            for(WikiMaps.Feature feature:frame.features(document.maps)) {
                MapDestination destination=feature(feature,frame.source);
                if(!destination.places.isEmpty()||!destination.unavailable.isEmpty()) {destinations.add(destination);used.add(feature.source.unit);}
            }
            frameGroups.computeIfAbsent(frame.source.section,key->new ArrayList<>()).add(new Group(title,destinations,frame));
        }
        frameGroups.forEach((section,children)->{
            String title=children.get(0).frame.source.sectionTitle;
            groups.add(children.size()>1?new Group(title.isBlank()?"Location maps":title,children):children.get(0));
        });
        Map<String,List<MapDestination>> additional=new LinkedHashMap<>();
        for(Node node:document.nodes) {
            if(node.locations.isEmpty())continue;
            if(node.table!=null&&node.table.kind.isEmpty()) {
                for(ReaderLayout.Record record:layout.records(node.table)) {
                    List<Location> locations=new ArrayList<>();
                    for(Location location:node.locations)if(record.units.contains(location.source.unit))locations.add(location);
                    add(additional,node.source.sectionTitle,MapDestination.forRecord(locations,record));
                }
            } else {
                Set<String> matched=new HashSet<>();
                for(Fact fact:node.facts) {
                    MapDestination destination=MapDestination.forFact(fact,node.locations);
                    if(destination.places.isEmpty()&&destination.unavailable.isEmpty())continue;
                    add(additional,destination.action.equals("Show quest start")?"Quest start":node.source.sectionTitle,destination);
                    matched.add(fact.source.unit);
                }
                add(additional,node.source.sectionTitle,MapDestination.forNode(node).excludingUnits(matched));
            }
        }
        for(WikiMaps.Feature feature:document.maps.features)if(feature.point&&!used.contains(feature.source.unit))
            add(additional,"Map locations",feature(feature,feature.source));
        additional.forEach((name,destinations)->groups.add(new Group(name,destinations,null)));
        Set<String> images=new HashSet<>();
        for(Node node:document.nodes) {
            List<com.wikipane.document.WikiDocument.Image> candidates=new ArrayList<>();
            if(node.fallback instanceof com.wikipane.document.WikiDocument.Image)candidates.add((com.wikipane.document.WikiDocument.Image)node.fallback);
            if(node.table!=null)mapImages(node.table,candidates);
            for(com.wikipane.document.WikiDocument.Image image:candidates)
                if(image.map&&images.add(image.link.isBlank()?image.url:image.link))
                    groups.add(new Group(imageTitle(image),List.of(),null,image));
        }
        groups.sort(Comparator.comparingInt(group->group.title.equals("Quest start")?0:group.frame!=null?1:2));
        this.groups=List.copyOf(groups);
    }
    private static void mapImages(Table table,List<com.wikipane.document.WikiDocument.Image> images) {
        images.addAll(table.images);
        for(Row row:table.rows)for(Cell cell:row.cells) {
            if(cell.illustration!=null)images.add(cell.illustration);
            for(Table nested:cell.nested)mapImages(nested,images);
        }
    }
    private static String imageTitle(com.wikipane.document.WikiDocument.Image image) {
        if(!image.alt.isBlank())return image.alt;
        String title=com.wikipane.api.WikiUrls.title(com.wikipane.api.WikiUrls.resolve(image.link));
        return title!=null&&title.startsWith("File:")?title.substring(5).replaceFirst("(?i)\\.(png|jpg|jpeg|webp|gif)$",""):"Map image";
    }
    private static String frameTitle(WikiMaps.Frame frame,SemanticDocument document) {
        for(Node node:document.nodes)if(node.table!=null) {
            String title=rowTitle(frame.id,node.table);
            if(!title.isBlank())return title;
        }
        return frame.title.equals("Map")||frame.title.equals("Information")?"Location map":frame.title;
    }
    private static String rowTitle(String id,Table table) {
        for(Row row:table.rows) {
            boolean matches=row.cells.stream().anyMatch(cell->cell.value.html.contains("data-wikipane-frame=\""+id+"\""));
            if(matches) {
                List<Cell> cells=new ArrayList<>(row.cells);cells.addAll(row.carried);
                for(Cell cell:cells)if(Set.of("location","name","place","patch").contains(cell.label.toLowerCase(Locale.ROOT))&&!cell.value.text.isBlank())return cell.value.text;
            }
            for(Cell cell:row.cells)for(Table nested:cell.nested){String title=rowTitle(id,nested);if(!title.isBlank())return title;}
        }
        return "";
    }
    private static void add(Map<String,List<MapDestination>> groups,String name,MapDestination destination) {
        if(destination.places.isEmpty()&&destination.unavailable.isEmpty())return;
        if(name.isBlank()||name.equals("Information"))name="Locations";
        List<MapDestination> existing=groups.computeIfAbsent(name,key->new ArrayList<>());
        String identity=identity(destination);
        if(existing.stream().noneMatch(old->identity(old).equals(identity)))existing.add(destination);
    }
    private static String identity(MapDestination destination) {
        StringBuilder key=new StringBuilder();List<MapDestination.Place> places=new ArrayList<>(destination.places);places.addAll(destination.unavailable);
        for(MapDestination.Place place:places)key.append(place.label).append(':').append(place.location.mapId).append(':').append(place.location.x).append(',').append(place.location.y).append(',').append(place.location.plane).append(';');
        return key.toString();
    }
    public static MapDestination feature(WikiMaps.Feature feature,Source frame) {
        // GeoJSON uses continuous tile coordinates. A point inside a tile maps to that containing tile.
        if(!feature.point||feature.x<=0||feature.x>=16384||feature.y<=0||feature.y>=16384)return MapDestination.create(List.of(),"Show on map");
        Source source=new Source(feature.source.article,feature.source.url,feature.source.revision,frame.section,frame.sectionTitle,feature.source.unit);
        Location location=new Location(Integer.toString((int)Math.floor(feature.x)),Integer.toString((int)Math.floor(feature.y)),feature.plane,source,true,feature.mapId);
        return MapDestination.create(List.of(new MapDestination.Place(location,feature.label)),"Show on map");
    }
}
