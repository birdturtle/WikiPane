package com.wikipane.view;

import com.wikipane.DocumentTest;
import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real API fixtures captured for the coverage audit, with no live network dependency. */
public class MapCoverageTest {
    @Test public void npcAndShopAreasKeepTheirRealOutlineWithoutInventingASpawnTile() throws Exception {
        for(String fixture:List.of("Hans_maps","Bobs_axes_maps")) {
            WikiPage page=new WikiPage(DocumentTest.document(fixture));WikiMaps maps=page.document.maps;
            assertEquals(1,maps.frames.size());assertEquals(1,maps.features.size());assertFalse(maps.incomplete);
            assertEquals(MapGeometry.Kind.AREA,maps.features.get(0).geometry.kind);
            assertEquals(1,maps.frames.get(0).features(maps).size());
            assertTrue(page.maps.groups.get(0).destinations.isEmpty());assertFalse(maps.features.get(0).point);
            assertTrue(maps.features.get(0).geometry.paths.get(0).size()>=4);
        }
    }
    @Test public void sixtySevenFarmingMapsAreNamedAndGroupedByTheirSourcePatchType() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Farming_patches_maps"));
        assertEquals(67,page.document.maps.frames.size());assertEquals(67,page.document.maps.features.size());assertFalse(page.document.maps.incomplete);
        assertTrue(page.maps.groups.size()<20);
        Set<String> frames=new HashSet<>();for(ArticleMaps.Group group:page.maps.groups)collect(group,frames);
        assertEquals("Every source map must remain accessible",67,frames.size());
        ArticleMaps.Group allotments=page.maps.groups.stream().filter(g->g.title.contains("Allotment/Flower/Herb")).findFirst().orElseThrow();
        assertTrue(allotments.children.stream().anyMatch(g->g.title.startsWith("South of Falador")));
        assertTrue(allotments.children.stream().noneMatch(g->g.title.equals("Map")));
    }
    private void collect(ArticleMaps.Group group,Set<String> frames) {
        if(group.frame!=null)assertTrue(frames.add(group.frame.id));for(ArticleMaps.Group child:group.children)collect(child,frames);
    }
    @Test public void structurallyLabeledDungeonMapsDoNotDependOnAltTextOrFileNames() throws Exception {
        WikiPage sewer=new WikiPage(DocumentTest.document("Varrock_Sewers_maps"));
        assertTrue(sewer.maps.groups.stream().anyMatch(g->g.image!=null&&g.title.equals("Varrock Sewers Monster Spawns")));
        assertFalse(sewer.maps.groups.stream().anyMatch(g->g.image!=null&&g.image.url.contains("300px-Varrock_Sewers.png")));
        WikiPage dungeon=new WikiPage(DocumentTest.document("Taverley_Dungeon_maps"));
        assertTrue(dungeon.maps.groups.stream().anyMatch(g->g.image!=null&&g.title.equals("Taverley Dungeon map")));
    }
    @Test public void questRoutesAndRegionsRemainSeparateFromExactPointDestinations() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Tree_Gnome_Village_maps"));
        assertFalse(page.document.maps.incomplete);
        assertEquals(2,page.document.maps.features.stream().filter(f->f.geometry.kind==MapGeometry.Kind.LINE).count());
        assertEquals(4,page.document.maps.features.stream().filter(f->f.geometry.kind==MapGeometry.Kind.AREA).count());
        assertTrue(page.document.maps.frames.stream().anyMatch(f->f.features(page.document.maps).stream().anyMatch(p->p.geometry.kind==MapGeometry.Kind.LINE)));
        for(WikiMaps.Feature feature:page.document.maps.features)if(!feature.point)
            assertTrue(ArticleMaps.feature(feature,feature.source).places.isEmpty());
    }
    @Test public void unsupportedExactLocationsKeepTheirSourceInsteadOfDisappearing() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Rake"));
        assertTrue(page.maps.groups.stream().flatMap(g->g.destinations.stream()).anyMatch(d->!d.unavailable.isEmpty()));
        assertTrue(page.maps.groups.stream().flatMap(g->g.destinations.stream()).allMatch(d->d.places.isEmpty()));
    }
    @Test public void onlyEquivalentDestinationEntriesAreConsolidated() {
        Source source=new Source("Example","https://oldschool.runescape.wiki/w/Example",1,"Locations","Locations","b1");
        Location location=new Location("3208","3216","0",source,true,"0");
        MapDestination destination=MapDestination.create(List.of(new MapDestination.Place(location,"Shop"),
            new MapDestination.Place(location,"Shop"),new MapDestination.Place(location,"NPC")),"Show locations");
        assertEquals(2,destination.places.size());assertTrue(destination.unavailable.isEmpty());
    }
    @Test public void mapColumnsKeepTheirImagesEvenWhenTheFilenameDoesNotSayMap() {
        com.google.gson.JsonObject response=new com.google.gson.JsonObject(),parse=new com.google.gson.JsonObject();
        parse.addProperty("title","Example garden");parse.addProperty("text","<table class='wikitable'><tr><th>Location</th><th>Map</th></tr>"
            +"<tr><td>West garden</td><td><a href='/w/File:Garden_layout.png'><img src='/images/Garden_layout.png' width='300' height='200'></a></td></tr></table>");
        response.add("parse",parse);WikiPage page=new WikiPage(new com.wikipane.document.WikiDocumentParser().parse(response));
        assertTrue(page.maps.groups.stream().anyMatch(g->g.image!=null&&g.image.map&&g.title.equals("Garden layout")));
    }

    @Test public void everyAuditedRegionAndRouteIsExposedByASourcePreview() throws Exception {
        for(String fixture:List.of("Hans_maps","Bobs_axes_maps","Farming_patches_maps","Tree_Gnome_Village_maps")) {
            WikiMaps maps=DocumentTest.document(fixture).maps;
            for(WikiMaps.Feature feature:maps.features)if(!feature.point)
                assertTrue(fixture+": "+feature.source.unit,maps.frames.stream().anyMatch(frame->frame.contains(feature)));
        }
    }

}
