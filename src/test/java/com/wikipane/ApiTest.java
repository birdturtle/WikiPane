package com.wikipane;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.wikipane.api.*;
import com.wikipane.cache.ImageCache;
import com.wikipane.integration.GameEntityLookup;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.coords.WorldPoint;
import okhttp3.*;
import org.junit.Test;

public class ApiTest {
    private WikiClient client(Interceptor interceptor) {
        return new WikiClient(new OkHttpClient.Builder().addInterceptor(interceptor).build(), new Gson());
    }
    private Response response(Interceptor.Chain chain, int status, String body) {
        return new Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("test")
            .body(ResponseBody.create(MediaType.get("application/json"), body))
            .build();
    }
    @Test
    public void fullSearchUsesActualTitlesAndCachesWithoutHttp() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WikiClient client = client(chain -> {
            calls.incrementAndGet();
            assertEquals("search", chain.request().url().queryParameter("list"));
            return response(chain, 200, "{\"query\":{\"search\":[{\"title\":\"Bronze dagger\"}]}}");
        });
        assertEquals("Bronze dagger", client.search("bronze dagger", false).get(0));
        client.search("bronze dagger", false);
        assertEquals(1, calls.get());
        assertTrue(client.search(" ", false).isEmpty());
        client.close();
    }
    @Test
    public void articleRedirectUsesCanonicalApiTitleAndRevision() throws Exception {
        WikiClient client = client(chain -> {
            assertEquals("1", chain.request().url().queryParameter("redirects"));
            return response(chain, 200,
                "{\"parse\":{\"title\":\"Cook's Assistant\",\"revid\":42,\"text\":\"<p>Actual "
                    + "content</p>\"}}");
        });
        assertEquals("Cook's Assistant", client.article("Cooks Assistant").title);
        assertEquals(42, client.article("Cooks Assistant").revision);
        client.close();
    }
    @Test
    public void lookupRetainsIdentityAndResolvesRealCanonicalPage() throws Exception {
        HttpUrl url = GameEntityLookup.url("object", 123, "Door", new WorldPoint(3200, 3201, 2));
        assertEquals("123", url.queryParameter("id"));
        assertEquals("3200", url.queryParameter("x"));
        assertEquals("2", url.queryParameter("plane"));
        WikiClient client = client(chain
            -> response(chain, 200,
                "<html><head><link rel='canonical' "
                    + "href='https://oldschool.runescape.wiki/w/Door_(Varrock)'></head></html>"));
        assertTrue(client.entity(url).endsWith("Door_(Varrock)"));
        client.close();
    }
    @Test
    public void lookupFollowsSameWikiRedirectButRejectsForeignRedirect() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        WikiClient client = client(chain -> {
            if (calls.incrementAndGet() == 1)
                return response(chain, 302, "").newBuilder().header("Location", "/w/Bronze_dagger").build();
            return response(chain, 200, "unused");
        });
        assertEquals(WikiUrls.article("Bronze dagger"),
            client.entity(GameEntityLookup.url("item", 1205, "Bronze dagger", null)));
        client.close();
        WikiClient foreign = client(chain
            -> response(chain, 302, "")
                .newBuilder()
                .header("Location", "https://evil.example/w/Test")
                .build());
        try {
            foreign.entity(GameEntityLookup.url("item", 1205, "Bronze dagger", null));
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("outside"));
        } finally {
            foreign.close();
        }
    }
    @Test
    public void malformedMissingAndRateLimitedResponsesRemainRetryable() throws Exception {
        for (String body : new String[] {"bad json", "{}", "{\"error\":{\"code\":\"missingtitle\"}}"}) {
            WikiClient client = client(chain -> response(chain, 200, body));
            try {
                client.article("Missing");
                fail();
            } catch (IOException expected) {
            } finally {
                client.close();
            }
        }
        AtomicInteger calls = new AtomicInteger();
        WikiClient rate = client(chain
            -> response(chain, calls.incrementAndGet() == 1 ? 429 : 200, "{\"query\":{\"search\":[]}}"));
        try {
            rate.search("test", false);
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("wait"));
        }
        assertTrue(rate.search("test", false).isEmpty());
        assertEquals(2, calls.get());
        rate.close();
    }
    @Test
    public void blockedSchemesAndImageSourcesNeverReachNetwork() throws Exception {
        assertFalse(WikiUrls.browser(WikiUrls.resolve("javascript:alert(1)")));
        assertFalse(WikiUrls.image(WikiUrls.resolve("https://evil.example/images/test.png")));
        assertFalse(WikiUrls.image(WikiUrls.resolve("file:///etc/passwd")));
        assertFalse(WikiUrls.wiki(HttpUrl.get("https://oldschool.runescape.wiki:444/w/Test")));
        WikiClient client = client(chain -> {
            fail("No request allowed");
            return null;
        });
        try {
            client.image("https://evil.example/images/a.png");
            fail();
        } catch (IOException expected) {
        }
        client.close();
    }
    @Test
    public void responseBytesAreBoundedBeforeParsing() throws Exception {
        try {
            WikiClient.limited(new ByteArrayInputStream(new byte[1025]), 1024);
            fail();
        } catch (IOException expected) {
        }
        assertEquals(10, WikiClient.limited(new ByteArrayInputStream(new byte[10]), 1024).length);
    }
}
