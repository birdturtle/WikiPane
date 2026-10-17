package com.wikipane.integration;

import com.wikipane.WikiPaneConfig;
import java.util.*;
import okhttp3.HttpUrl;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class WikiLookupRouterTest {
    @Test public void destinationChangesAreAppliedToTheNextLookupAndSearchAlwaysUsesTheSidebar() {
        WikiPaneConfig config = mock(WikiPaneConfig.class);
        List<HttpUrl> sidebar = new ArrayList<>();
        List<String> browser = new ArrayList<>(), searches = new ArrayList<>();
        WikiLookupRouter router = new WikiLookupRouter(config, sidebar::add, browser::add, () -> searches.add("search"));
        HttpUrl entity = GameEntityLookup.url("item", 1205, "Bronze dagger", null);
        when(config.destination()).thenReturn(WikiPaneConfig.Destination.SIDEBAR);
        router.lookup(entity); router.search();
        assertEquals(List.of(entity), sidebar);
        assertTrue(browser.isEmpty());
        when(config.destination()).thenReturn(WikiPaneConfig.Destination.BROWSER);
        router.lookup(entity); router.search();
        assertEquals(1, sidebar.size());
        assertEquals(List.of(entity.toString()), browser);
        when(config.destination()).thenReturn(WikiPaneConfig.Destination.SIDEBAR);
        router.lookup(entity);
        assertEquals(2, sidebar.size());
        assertEquals(1, browser.size());
        assertEquals(2, searches.size());
    }
}
