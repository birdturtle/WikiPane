package com.wikipane.integration;

import java.util.function.Consumer;
import okhttp3.HttpUrl;

/** The single destination for enabled Wiki integration is the sidebar. */
public final class WikiLookupRouter {
    private final Consumer<HttpUrl> sidebar;
    private final Runnable search;
    public WikiLookupRouter(Consumer<HttpUrl> sidebar, Runnable search) {
        this.sidebar = sidebar; this.search = search;
    }
    public void lookup(HttpUrl url) { sidebar.accept(url); }
    public void search() { search.run(); }
}
