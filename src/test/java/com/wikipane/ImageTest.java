package com.wikipane;
import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.api.WikiClient;
import com.wikipane.cache.ImageCache;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import okhttp3.*;
import org.junit.Test;
public class ImageTest {
    private byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
    private WikiClient source(byte[] bytes, AtomicInteger calls) {
        return new WikiClient(new OkHttpClient.Builder()
                                  .addInterceptor(chain -> {
                                      calls.incrementAndGet();
                                      return new Response.Builder()
                                          .request(chain.request())
                                          .protocol(Protocol.HTTP_1_1)
                                          .code(200)
                                          .message("image")
                                          .body(ResponseBody.create(MediaType.get("image/png"), bytes))
                                          .build();
                                  })
                                  .build(),
            new Gson());
    }
    @Test
    public void tallImagesFitSidebarAndReuseDecodedCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (WikiClient wiki = source(png(400, 800), calls)) {
            ImageCache cache = new ImageCache(wiki);
            BufferedImage image = cache.load("https://oldschool.runescape.wiki/images/test.png");
            assertTrue(image.getWidth() <= 200);
            assertTrue(image.getHeight() <= 220);
            assertSame(image, cache.load("https://oldschool.runescape.wiki/images/test.png"));
            assertEquals(1, calls.get());
        }
    }
    @Test
    public void previewUsesLargerBoundedRasterAndIndependentCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (WikiClient wiki = source(png(1600, 1200), calls)) {
            ImageCache cache = new ImageCache(wiki);
            String url = "https://oldschool.runescape.wiki/images/thumb/a/ab/Example.png/200px-Example.png";
            BufferedImage small = cache.load(url), large = cache.preview(url);
            assertTrue(large.getWidth() > small.getWidth());
            assertEquals(1600, large.getWidth());
            assertEquals(1200, large.getHeight());
            assertSame(large, cache.preview(url));
            assertEquals(2, calls.get());
            cache.clear(); cache.preview(url);
            assertEquals(3, calls.get());
        }
    }
    @Test
    public void oversizedRasterIsRejectedBeforeDecoding() throws Exception {
        byte[] bytes = png(1, 1);
        bytes[16] = 0;
        bytes[17] = 0;
        bytes[18] = 0x40;
        bytes[19] = 0;
        try (WikiClient wiki = source(bytes, new AtomicInteger())) {
            try {
                new ImageCache(wiki).load("https://oldschool.runescape.wiki/images/huge.png");
                fail();
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("large"));
            }
        }
    }
}
