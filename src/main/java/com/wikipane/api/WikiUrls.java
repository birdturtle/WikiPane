package com.wikipane.api;

import okhttp3.HttpUrl;

public final class WikiUrls {
    public static final HttpUrl BASE = HttpUrl.get("https://oldschool.runescape.wiki/");
    public static final HttpUrl API = BASE.newBuilder().addPathSegment("api.php").build();
    private WikiUrls() {}
    public static HttpUrl resolve(String value) {
        return BASE.resolve(value);
    }
    public static boolean wiki(HttpUrl url) {
        return url != null && url.isHttps() && url.host().equals(BASE.host()) && url.port() == 443
            && url.username().isEmpty() && url.password().isEmpty();
    }
    public static boolean image(HttpUrl url) {
        return wiki(url) && url.encodedPath().startsWith("/images/");
    }
    public static boolean browser(HttpUrl url) {
        return url != null && url.isHttps() && url.username().isEmpty() && url.password().isEmpty();
    }
    public static String article(String title) {
        return BASE.newBuilder()
            .addPathSegment("w")
            .addPathSegment(title.replace(' ', '_'))
            .build()
            .toString();
    }
    public static String title(HttpUrl url) {
        if (!wiki(url))
            return null;
        if (url.encodedPath().startsWith("/w/") && url.pathSegments().size() > 1)
            return String.join("/", url.pathSegments().subList(1, url.pathSegments().size()))
                .replace('_', ' ');
        if (url.encodedPath().equals("/index.php"))
            return url.queryParameter("title");
        return null;
    }
}
