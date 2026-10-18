package com.wikipane.integration;
import java.util.*;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.*;
public class WikiLookupRouterTest {
    @Test public void unifiedLookupAndSearchUseTheSidebar() {
        List<HttpUrl> articles = new ArrayList<>(); List<String> search = new ArrayList<>();
        WikiLookupRouter router = new WikiLookupRouter(articles::add, () -> search.add("search"));
        HttpUrl entity = GameEntityLookup.url("item", 1205, "Bronze dagger", null);
        router.lookup(entity); router.search();
        assertEquals(List.of(entity), articles); assertEquals(List.of("search"), search);
    }
}
