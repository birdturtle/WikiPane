package com.wikipane.api;

import com.google.gson.*;
import com.wikipane.cache.WikiCache;
import com.wikipane.document.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** All entry points run on WikiPane's worker pool, never the game or Swing thread. */
public final class WikiClient implements AutoCloseable {
    private final OkHttpClient http;
    private final Gson gson;
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private final WikiCache<String, List<String>> searches = new WikiCache<>(80, 120_000);
    private final WikiCache<String, com.wikipane.view.WikiPage> pages=new WikiCache<>(12,600_000);
    private final WikiCache<String, WikiDocument> articles = new WikiCache<>(12, 600_000);
    private final WikiCache<String, String> entities = new WikiCache<>(100, 600_000);
    public WikiClient(OkHttpClient injected, Gson gson) {
        this.gson = gson;
        http = injected.newBuilder()
                   .followRedirects(false)
                   .followSslRedirects(false)
                   .connectTimeout(10, TimeUnit.SECONDS)
                   .readTimeout(20, TimeUnit.SECONDS)
                   .callTimeout(30, TimeUnit.SECONDS)
                   .build();
    }
    private byte[] get(HttpUrl url, int maximum) throws IOException {
        if (closed)
            throw new IOException("WikiPane is closed.");
        if (!WikiUrls.wiki(url))
            throw new IOException("The Wiki returned an unsupported destination.");
        Call call = http.newCall(new Request.Builder()
                .url(url)
                .header("User-Agent", "WikiPane/0.1 (RuneLite; https://github.com/birdturtle)")
                .header("Accept", "application/json,text/html;q=0.9,image/*;q=0.8")
                .build());
        calls.add(call);
        if (closed) {
            call.cancel();
            calls.remove(call);
            throw new IOException("WikiPane is closed.");
        }
        try (Response response = call.execute()) {
            if (response.code() == 429)
                throw new IOException("The Wiki is busy. Please wait before trying again.");
            if (response.isRedirect())
                throw new IOException("Unexpected Wiki redirect. Open this page in your browser.");
            if (!response.isSuccessful() || response.body() == null)
                throw new IOException("Wiki request failed (HTTP " + response.code() + "). Try again.");
            if (response.body().contentLength() > maximum)
                throw new IOException("This page is too large for the sidebar. Open it in your browser.");
            return limited(response.body().byteStream(), maximum);
        } finally {
            calls.remove(call);
        }
    }
    public static byte[] limited(InputStream stream, int maximum) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (out.size() + count > maximum)
                throw new IOException("Wiki content exceeds the sidebar limit.");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
    private JsonObject json(HttpUrl url) throws IOException {
        try {
            JsonObject object = gson.fromJson(
                new String(get(url, 4_000_000), java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            if (object == null)
                throw new IllegalStateException();
            if (object.has("error"))
                throw new IOException("The Wiki could not find this page or complete this request.");
            return object;
        } catch (JsonParseException | IllegalStateException ex) {
            throw new IOException("The Wiki returned an unreadable response. Try again.", ex);
        }
    }
    public List<String> search(String query, boolean suggestions) throws IOException {
        query = query.trim();
        if (query.isEmpty())
            return List.of();
        String key = (suggestions ? "s:" : "q:") + query.toLowerCase(Locale.ROOT);
        List<String> cached = searches.get(key);
        if (cached != null)
            return cached;
        HttpUrl.Builder url = WikiUrls.API.newBuilder().addQueryParameter("format", "json");
        List<String> result = new ArrayList<>();
        try {
            if (suggestions) {
                url.addQueryParameter("action", "opensearch")
                    .addQueryParameter("search", query)
                    .addQueryParameter("redirects", "resolve")
                    .addQueryParameter("limit", "8")
                    .addQueryParameter("namespace", "0");
                JsonArray array = gson.fromJson(
                    new String(get(url.build(), 256_000), java.nio.charset.StandardCharsets.UTF_8),
                    JsonArray.class);
                for (JsonElement e : array.get(1).getAsJsonArray()) result.add(e.getAsString());
            } else {
                url.addQueryParameter("action", "query")
                    .addQueryParameter("list", "search")
                    .addQueryParameter("srsearch", query)
                    .addQueryParameter("srnamespace", "0")
                    .addQueryParameter("srlimit", "20")
                    .addQueryParameter("srprop", "")
                    .addQueryParameter("formatversion", "2");
                for (JsonElement e : json(url.build()).getAsJsonObject("query").getAsJsonArray("search"))
                    result.add(e.getAsJsonObject().get("title").getAsString());
            }
        } catch (RuntimeException ex) {
            throw new IOException("The Wiki returned unreadable search results.", ex);
        }
        List<String> value = List.copyOf(result);
        searches.put(key, value);
        return value;
    }
    public com.wikipane.view.WikiPage page(String title)throws IOException{com.wikipane.view.WikiPage old=pages.get(title);if(old!=null)return old;com.wikipane.view.WikiPage page=new com.wikipane.view.WikiPage(article(title));pages.put(title,page);return page;}
    public WikiDocument article(String title) throws IOException {
        WikiDocument cached = articles.get(title);
        if (cached != null)
            return cached;
        HttpUrl url = WikiUrls.API.newBuilder()
                          .addQueryParameter("action", "parse")
                          .addQueryParameter("page", title)
                          .addQueryParameter("prop", "text|sections|revid|categories")
                          .addQueryParameter("redirects", "1")
                          .addQueryParameter("format", "json")
                          .addQueryParameter("formatversion", "2")
                          .build();
        try {
            WikiDocument doc = new WikiDocumentParser().parse(json(url));
            articles.put(title, doc);
            return doc;
        } catch (RuntimeException ex) {
            throw new IOException("This Wiki article could not be read. Open it in your browser.", ex);
        }
    }
    /** Special:Lookup is an official extension, not a title search. Follow only same-Wiki redirects. */
    public String entity(HttpUrl lookup) throws IOException {
        String cached = entities.get(lookup.toString());
        if (cached != null)
            return cached;
        HttpUrl current = lookup;
        for (int redirects = 0; redirects < 6; redirects++) {
            if (closed || !WikiUrls.wiki(current))
                throw new IOException("Unsupported entity lookup destination.");
            Call call = http.newCall(new Request.Builder()
                    .url(current)
                    .header("User-Agent", "WikiPane/0.1 (RuneLite; https://github.com/birdturtle)")
                    .build());
            calls.add(call);
            if (closed) {
                call.cancel();
                calls.remove(call);
                throw new IOException("WikiPane is closed.");
            }
            try (Response response = call.execute()) {
                if (response.isRedirect()) {
                    HttpUrl next = current.resolve(Objects.toString(response.header("Location"), ""));
                    if (!WikiUrls.wiki(next))
                        throw new IOException("The Wiki redirected outside its domain.");
                    current = next;
                    continue;
                }
                if (!response.isSuccessful() || response.body() == null)
                    throw new IOException(
                        "The Wiki could not resolve this entity. Use Open lookup in browser.");
                String resolved = WikiUrls.title(current);
                if (resolved != null && !resolved.startsWith("Special:")) {
                    entities.put(lookup.toString(), current.toString());
                    return current.toString();
                }
                String html = new String(limited(response.body().byteStream(), 2_000_000),
                    java.nio.charset.StandardCharsets.UTF_8);
                Element canonical = Jsoup.parse(html).selectFirst("link[rel=canonical][href]");
                HttpUrl target = canonical == null ? null : current.resolve(canonical.attr("href"));
                String title = WikiUrls.title(target);
                if (title != null && !title.startsWith("Special:")) {
                    entities.put(lookup.toString(), target.toString());
                    return target.toString();
                }
                throw new IOException(
                    "No single article was resolved. Open lookup in browser to choose a result.");
            } finally {
                calls.remove(call);
            }
        }
        throw new IOException("Too many Wiki redirects. Open lookup in browser.");
    }
    public byte[] image(String url) throws IOException {
        HttpUrl u = WikiUrls.resolve(url);
        if (!WikiUrls.image(u))
            throw new IOException("Unsupported image host.");
        return get(u, 1_000_000);
    }
    @Override
    public void close() {
        closed = true;
        for (Call call : calls) call.cancel();
        calls.clear();
        articles.clear();pages.clear();
        searches.clear();
        entities.clear();
    }
}
