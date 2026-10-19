package com.wikipane.cache;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.semantic.WikiMaps;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapImageCacheTest {
    private byte[] tile(int size) throws Exception {
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(30,50,70));g.fillRect(0,0,size,size);g.dispose();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return bytes.toByteArray();
    }
    @Test public void mapUsesItsBoundedViewportRealPointsAndHigherResolutionTiles() throws Exception {
        WikiMaps maps=DocumentTest.document("Cooks_Assistant_maps").maps;WikiMaps.Frame frame=maps.frames.get(0);
        AtomicInteger requests=new AtomicInteger();byte[] png=tile(256);
        WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain->{
            assertEquals("maps.runescape.wiki",chain.request().url().host());requests.incrementAndGet();
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(ResponseBody.create(MediaType.get("image/png"),png)).build();}).build(),new Gson());
        try {
            MapImageCache cache=new MapImageCache(wiki);BufferedImage image=cache.load(frame,maps,false);
            assertEquals(300,image.getWidth());assertEquals(300,image.getHeight());assertTrue(requests.get()<=16);
            int count=requests.get();assertSame(image,cache.load(frame,maps,false));assertEquals(count,requests.get());
            BufferedImage detail=cache.load(frame,maps,true);assertEquals(600,detail.getWidth());assertEquals(600,detail.getHeight());
            assertTrue(requests.get()>count);assertTrue(requests.get()<=36);
            WikiMaps.Feature chicken=frame.features(maps).get(0);
            int x=(int)Math.round(150+(chicken.x-frame.x)*4),y=(int)Math.round(150-(chicken.y-frame.y)*4);
            assertEquals(MapImageCache.color(chicken.icon).getRGB(),image.getRGB(x+5,y));
        } finally {wiki.close();}
    }
    @Test public void malformedOrFailedTilesAreNotCachedAsSuccessfulMaps() throws Exception {
        WikiMaps maps=DocumentTest.document("Cooks_Assistant_maps").maps;byte[] bad=tile(1024),good=tile(256);AtomicInteger requests=new AtomicInteger();
        WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request())
            .protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(ResponseBody.create(MediaType.get("image/png"),requests.getAndIncrement()==0?bad:good)).build()).build(),new Gson());
        try {
            MapImageCache cache=new MapImageCache(wiki);
            try {cache.load(maps.frames.get(0),maps,false);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("dimensions"));}
            assertEquals(300,cache.load(maps.frames.get(0),maps,false).getWidth());assertTrue(requests.get()>1);
        } finally {wiki.close();}
    }
    @Test public void outOfRangeViewportIsRejectedBeforeAnyTileRequest() throws Exception {
        WikiMaps maps=DocumentTest.document("Cooks_Assistant_maps").maps;WikiMaps.Frame original=maps.frames.get(0);
        WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain->{throw new AssertionError("No network for invalid viewport");}).build(),new Gson());
        try {
            WikiMaps.Frame invalid=new WikiMaps.Frame("invalid",original.title,original.caption,original.mapId,original.plane,original.tileTemplate,
                Double.MAX_VALUE,original.y,original.width,original.height,original.zoom,original.overlays,original.source);
            try {new MapImageCache(wiki).load(invalid,maps,false);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("viewport"));}
        }finally{wiki.close();}
    }

}
