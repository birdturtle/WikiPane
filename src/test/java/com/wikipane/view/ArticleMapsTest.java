package com.wikipane.view;

import com.google.gson.*;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiUrls;
import com.wikipane.document.WikiDocumentParser;
import com.wikipane.semantic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleMapsTest {
    @Test public void liveResponseSeparatesViewingCenterFromActualIngredientPoints() throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document("Cooks_Assistant_maps"));
        WikiMaps maps=page.document.semantic.maps;
        assertEquals(1,maps.frames.size());assertEquals(4,maps.features.size());assertFalse(maps.incomplete);
        WikiMaps.Frame frame=maps.frames.get(0);
        assertEquals("Ingredient locations",frame.title);assertEquals(3166,frame.x,0);assertEquals(3293,frame.y,0);
        assertEquals(3,frame.features(maps).size());
        ArticleMaps.Group ingredients=page.maps.groups.stream().filter(g->g.title.equals("Ingredient locations")).findFirst().orElseThrow();
        assertEquals(3,ingredients.destinations.size());assertEquals(3,ingredients.all().places.size());
        assertEquals("Chicken coop",ingredients.destinations.get(0).places.get(0).label);
        assertEquals("3181",ingredients.destinations.get(0).places.get(0).location.x);
        assertTrue(ingredients.destinations.stream().noneMatch(d->d.places.get(0).location.x.equals("3166")));
        assertTrue(frame.tileTemplate,frame.tileTemplate.contains("/{zoom}/{plane}_{x}_{y}.png"));
        assertTrue(frame.caption.contains("Dairy cow field"));
    }
    @Test public void olderHtmlWithoutFeatureDataKeepsPreviewAndAnHonestIncompleteState() throws Exception {
        WikiMaps maps=DocumentTest.document("Hans").maps;
        assertEquals(1,maps.frames.size());assertTrue(maps.features.isEmpty());assertTrue(maps.incomplete);
        assertTrue(new WikiPage(DocumentTest.document("Hans")).maps.groups.get(0).destinations.isEmpty());
    }
    @Test public void unsupportedGeometryIsRetainedAndDoesNotBecomeAnInventedPoint() throws Exception {
        JsonObject response=DocumentTest.fixture("Cooks_Assistant_maps");
        JsonObject feature=response.getAsJsonObject("parse").getAsJsonObject("jsconfigvars").getAsJsonObject("wgKartographerLiveData")
            .getAsJsonArray("pins").get(1).getAsJsonObject().getAsJsonArray("features").get(0).getAsJsonObject();
        feature.getAsJsonObject("geometry").addProperty("type","Polygon");
        WikiMaps maps=new WikiDocumentParser().parse(response).maps;
        assertTrue(maps.incomplete);assertEquals(4,maps.features.size());assertFalse(maps.features.get(1).point);
        assertTrue(maps.features.get(1).raw.contains("Polygon"));assertEquals(2,maps.frames.get(0).features(maps).size());
    }
    @Test public void malformedFramesStayAccessibleThroughAnUnavailablePreview() throws Exception {
        JsonObject response=DocumentTest.fixture("Cooks_Assistant_maps");
        JsonObject parse=response.getAsJsonObject("parse");
        parse.addProperty("text",parse.get("text").getAsString().replace("data-width=\"300\"","data-width=\"huge\""));
        WikiMaps maps=new WikiDocumentParser().parse(response).maps;
        assertTrue(maps.incomplete);assertEquals(1,maps.frames.size());assertEquals("",maps.frames.get(0).tileTemplate);
    }
    @Test public void tileDomainPathAndCredentialsAreStrictlyRestricted() {
        String good="https://maps.runescape.wiki/osrs/versions/2026-10-07_a/tiles/rendered/0/2/0_48_52.png";
        assertTrue(WikiUrls.mapTile(WikiUrls.resolve(good)));
        for(String bad:new String[]{good.replace("https:","http:"),good.replace("maps.runescape.wiki","evil.example"),
                good.replace("https://","https://user@"),good+"?redirect=evil",good.replace("/tiles/rendered/","/other/")})
            assertFalse(bad,WikiUrls.mapTile(WikiUrls.resolve(bad)));
    }
}
