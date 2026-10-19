package com.wikipane.view;

import com.wikipane.integration.WikiMapIntegration;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.jsoup.Jsoup;

/** A deliberate, source-backed map selection. Coordinates never become user-facing captions. */
public final class MapDestination {
    public static final class Place {
        public final Location location;
        public final String label;
        public Place(Location location, String label) {
            this.location = location;
            this.label = label.isBlank() ? location.source.article : label;
        }
    }
    public final List<Place> places;
    public final List<Place> unavailable;
    public final String action;
    private MapDestination(List<Place> places, String action) {
        this(places,List.of(),action);
    }
    private MapDestination(List<Place> places,List<Place> unavailable,String action) {
        this.places=List.copyOf(places);this.unavailable=List.copyOf(unavailable);this.action=action;
    }
    public static MapDestination single(Location location) {
        String section = location.source.sectionTitle;
        String label = section.isBlank() || section.equals(location.source.article)
            ? location.source.article : location.source.article + " · " + section;
        return new MapDestination(List.of(new Place(location, label)), "Show on map");
    }
    public MapDestination excludingUnits(Set<String> units) {
        List<Place> remaining=new ArrayList<>();
        for(Place place:places) if(!units.contains(place.location.source.unit))remaining.add(place);
        List<Place> unsupported=new ArrayList<>();
        for(Place place:unavailable)if(!units.contains(place.location.source.unit))unsupported.add(place);
        return new MapDestination(remaining,unsupported,action);
    }
    private static boolean questStart(Fact fact) {
        return Set.of("start point","starting point","quest start").contains(fact.label.toLowerCase(Locale.ROOT));
    }
    private static String label(Location location, Fact fact) {
        String label=questStart(fact) ? location.source.article+" · Quest start"
            : fact.label.equalsIgnoreCase("map") ? "" : fact.label;
        // Wiki links supply concise place/person names without guessing from prose.
        LinkedHashSet<String> names=new LinkedHashSet<>();
        for(Link link:fact.value.links) if(!link.label.isBlank()) {
            names.add(link.label);if(names.size()==3)break;
        }
        if(!names.isEmpty())label=(label.isBlank()?"":label+" · ")+String.join(" · ",names);
        return label.isBlank()?single(location).places.get(0).label:label;
    }
    public static MapDestination forFact(Fact fact,List<Location> locations) {
        List<Place> places=new ArrayList<>();
        for(Location location:locations) if(location.source.unit.equals(fact.source.unit))
            places.add(new Place(location,label(location,fact)));
        return create(places,questStart(fact)?"Show quest start":action(fact.source.sectionTitle,places.size()));
    }
    public static MapDestination forNode(Node node) {
        List<Place> places=new ArrayList<>();boolean start=false;
        for(Location location:node.locations) {
            Fact fact=node.facts.stream().filter(f->f.source.unit.equals(location.source.unit)).findFirst().orElse(null);
            if(fact!=null&&questStart(fact))start=true;
            places.add(new Place(location,fact==null?single(location).places.get(0).label:label(location,fact)));
        }
        return create(places,start?"Show quest start":action(node.source.sectionTitle,places.size()));
    }
    public static MapDestination forRecord(List<Location> locations, ReaderLayout.Record record) {
        List<Place> places = new ArrayList<>();
        for (Location location : locations) {
            String label = record.identity == null ? "" : Jsoup.parseBodyFragment(
                MapPresentation.html(record.identity.value.html, record.identity.source)).text();
            if (label.isBlank()) label = single(location).places.get(0).label;
            places.add(new Place(location, label));
        }
        String section = locations.isEmpty() ? "" : locations.get(0).source.sectionTitle;
        return create(places, action(section, places.size()));
    }
    private static String action(String section, int count) {
        String key = section.toLowerCase(Locale.ROOT);
        if (key.contains("spawn")) return count > 1 ? "Show spawn locations" : "Show spawn";
        if (key.contains("shop")) return count > 1 ? "Show shops" : "Show shop";
        return count > 1 ? "Show locations" : "Show on map";
    }
    public static MapDestination create(List<Place> places, String action) {
        Map<String, Place> unique = new LinkedHashMap<>(),unsupported=new LinkedHashMap<>();
        for (Place place : places) {
            Location location = place.location;
            String identity=location.mapId+":"+location.x+","+location.y+","+location.plane+":"+place.label;
            // Distinct entities at the same tile retain their names; only equivalent entries consolidate.
            (WikiMapIntegration.destination(location)==null?unsupported:unique).putIfAbsent(identity,place);
        }
        return new MapDestination(new ArrayList<>(unique.values()),new ArrayList<>(unsupported.values()), action);
    }
}
