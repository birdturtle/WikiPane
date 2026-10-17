package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.function.Consumer;
import okhttp3.HttpUrl;

/** Explicit destinations for owned Wiki button actions. Never intercepts another plugin's browser. */
public final class WikiLookupRouter {
    private final WikiPaneConfig config;
    private final Consumer<HttpUrl> sidebar;
    private final Consumer<String> browser;
    private final Runnable search;
    public WikiLookupRouter(WikiPaneConfig config, Consumer<HttpUrl> sidebar,
                            Consumer<String> browser, Runnable search) {
        this.config = config; this.sidebar = sidebar; this.browser = browser; this.search = search;
    }
    public void lookup(HttpUrl url) {
        if (config.destination() == WikiPaneConfig.Destination.BROWSER) browser.accept(url.toString());
        else sidebar.accept(url);
    }
    public void search() { search.run(); }
}
