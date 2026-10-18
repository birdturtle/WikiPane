package com.wikipane.view;

import com.google.gson.JsonObject;
import com.wikipane.DocumentTest;
import com.wikipane.document.WikiDocumentParser;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapDestinationTest {
    public static WikiPage groupedPage() {
        JsonObject response=new JsonObject(), parse=new JsonObject();
        parse.addProperty("title","Example creature");
        String first="<a class='mw-kartographer-maplink' data-lon='3208' data-lat='3216' data-plane='0' data-mapid='0'>Show map</a>";
        String second=first.replace("3208","3210");
        parse.addProperty("text","<h2 id='Spawns'>Spawns</h2><table class='wikitable'><tr><th>Location</th><th>Map</th></tr>"
            +"<tr><td><a href='/w/West_garden'>West garden</a></td><td>"+first+second+first+"</td></tr></table>");
        response.add("parse",parse);
        return new WikiPage(new WikiDocumentParser().parse(response));
    }
    public static MapDestination group() {
        WikiPage page=groupedPage();
        Node node=page.document.semantic.nodes.stream().filter(n->!n.locations.isEmpty()).findFirst().orElseThrow();
        return MapDestination.forRecord(node.locations,page.layout.records(node.table).stream()
            .filter(r->r.identity!=null).findFirst().orElseThrow());
    }
    @Test public void questStartUsesTheActualSourceFactAndLinkedPlaceNames() throws Exception {
        Node node=DocumentTest.document("Cooks_Assistant").semantic.nodes.stream()
            .filter(n->!n.locations.isEmpty()).findFirst().orElseThrow();
        MapDestination destination=MapDestination.forNode(node);
        assertEquals("Show quest start",destination.action);
        assertEquals(1,destination.places.size());
        String label=destination.places.get(0).label;
        assertTrue(label,label.contains("Cook"));assertTrue(label,label.contains("Lumbridge Castle"));
        assertFalse(label.contains("3208"));
        assertSame(node.locations.get(0),destination.places.get(0).location);
    }
    @Test public void recordGroupsExactTilesWithoutLosingIdentityOrSource() {
        MapDestination destination=group();
        assertEquals("Show spawn locations",destination.action);
        assertEquals(2,destination.places.size());
        for(MapDestination.Place place:destination.places) {
            assertEquals("West garden",place.label);
            assertEquals("Spawns",place.location.source.section);
            assertTrue(place.location.source.unit.contains(".r"));
        }
        assertNotEquals(destination.places.get(0).location.x,destination.places.get(1).location.x);
    }
    @Test public void unsupportedLocationsDoNotAcquireGuessedWorldTiles() throws Exception {
        for(Node node:DocumentTest.document("Rake").semantic.nodes) {
            MapDestination destination=MapDestination.forNode(node);
            assertTrue(destination.places.isEmpty());
        }
    }
}
