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
        public final WikiMaps.Frame frame;
        public final com.wikipane.document.WikiDocument.Image image;
        Group(String title,List<MapDestination> destinations,WikiMaps.Frame frame) {
            this(title,destinations,frame,null);
        }
        Group(String title,List<MapDestination> destinations,WikiMaps.Frame frame,com.wikipane.document.WikiDocument.Image image) {
            this.title=title;this.destinations=List.copyOf(destinations);this.frame=frame;this.image=image;
        }
        public MapDestination all() {
            List<MapDestination.Place> places=new ArrayList<>();for(MapDestination destination:destinations)places.addAll(destination.places);
            return MapDestination.create(places,"Show all locations");
        }
    }
    public final List<Group> groups;
    public ArticleMaps(SemanticDocument document,ReaderLayout layout) {
        List<Group> groups=new ArrayList<>();Set<String> used=new HashSet<>();
        for(WikiMaps.Frame frame:document.maps.frames) {
            List<MapDestination> destinations=new ArrayList<>();
            for(WikiMaps.Feature feature:frame.features(document.maps)) {
                MapDestination destination=feature(feature,frame.source);
                if(!destination.places.isEmpty()) {destinations.add(destination);used.add(feature.source.unit);}
            }
            groups.add(new Group(frame.title,destinations,frame));
        }
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
                    if(destination.places.isEmpty())continue;
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
            if(node.table!=null)candidates.addAll(node.table.images);
            for(com.wikipane.document.WikiDocument.Image image:candidates)
                if(image.alt.matches("(?is).*\\bmap\\b.*")&&!image.alt.equalsIgnoreCase("Map icon")&&images.add(image.url))
                    groups.add(new Group(image.alt,List.of(),null,image));
        }
        groups.sort(Comparator.comparingInt(group->group.title.equals("Quest start")?0:group.frame!=null?1:2));
        this.groups=List.copyOf(groups);
    }
    private static void add(Map<String,List<MapDestination>> groups,String name,MapDestination destination) {
        if(destination.places.isEmpty())return;
        if(name.isBlank()||name.equals("Information"))name="Locations";
        List<MapDestination> existing=groups.computeIfAbsent(name,key->new ArrayList<>());
        String identity=identity(destination);
        if(existing.stream().noneMatch(old->identity(old).equals(identity)))existing.add(destination);
    }
    private static String identity(MapDestination destination) {
        StringBuilder key=new StringBuilder();for(MapDestination.Place place:destination.places)
            key.append(place.label).append(':').append(place.location.x).append(',').append(place.location.y).append(',').append(place.location.plane).append(';');
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
