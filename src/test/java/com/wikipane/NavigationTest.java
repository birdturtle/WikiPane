package com.wikipane;
import static org.junit.Assert.*;

import com.wikipane.cache.WikiCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.navigation.NavigationHistory;
import java.util.*;
import org.junit.Test;
public class NavigationTest {
    private WikiDocument doc(String name) {
        return new WikiDocument(name, "https://oldschool.runescape.wiki/w/" + name, 1, List.of(), List.of());
    }
    @Test
    public void backForwardPreserveDocumentAnchorAndScroll() {
        NavigationHistory h = new NavigationHistory();
        WikiDocument recipe = doc("Recipe"), cook = doc("Cook");
        h.push(recipe, "");
        h.saveScroll(731);
        h.push(cook, "");
        h.saveScroll(46);
        assertSame(recipe, h.back().document);
        assertEquals(731, h.current().scroll);
        assertSame(cook, h.forward().document);
        assertEquals(46, h.current().scroll);
        h.back();
        h.push(recipe, "Requirements");
        assertTrue(h.canForward());
        assertFalse(h.canBack());
        assertEquals("Requirements", h.current().section);
        assertEquals(731, h.current().scroll);
        assertSame(cook, h.forward().document);
    }
    @Test
    public void trivialRedrawDoesNotDuplicateAndHistoryIsBounded() {
        NavigationHistory h = new NavigationHistory();
        WikiDocument d = doc("Test");
        h.push(d, "");
        h.push(d, "");
        assertFalse(h.canBack());
        for (int i = 0; i < 100; i++) h.push(doc("Page" + i), "");
        int count = 1;
        while (h.canBack()) {
            h.back();
            count++;
        }
        assertEquals(40, count);
        h.clear();
        assertNull(h.current());
    }
    @Test
    public void searchResultsAreRealHistoryEntriesAndNewQueriesBranchHistory() {
        NavigationHistory history = new NavigationHistory();
        history.pushSearch("bronze", List.of("Bronze dagger"));
        history.saveScroll(83);
        history.push(doc("Bronze_dagger"), "");
        history.saveScroll(191);
        assertTrue(history.back().isSearch());
        assertEquals("bronze", history.current().query);
        assertEquals(List.of("Bronze dagger"), history.current().results);
        assertEquals(83, history.current().scroll);
        assertFalse(history.forward().isSearch());
        assertEquals(191, history.current().scroll);
        history.back();
        history.updateSearch("Vorkath", List.of("Vorkath"));
        assertFalse(history.canForward());
    }
    @Test
    public void sectionDestinationsUpdateOneArticleEntryAndPreserveForwardHistory() {
        NavigationHistory history = new NavigationHistory();
        com.wikipane.view.WikiPage page = new com.wikipane.view.WikiPage(doc("Test"));
        history.pushDestination(page, "overview", "");
        history.pushDestination(page, "walkthrough", "Chapter");
        history.saveScroll(914);
        history.pushDestination(page, "walkthrough", "Chapter");
        assertEquals(914, history.current().scroll);
        assertFalse(history.canBack());
        assertEquals("walkthrough", history.current().area);
        history.push(doc("Other"), "");
        assertEquals("Chapter", history.back().section);
        history.pushDestination(page, "overview", "");
        assertTrue(history.canForward());
        assertEquals(914, history.current().scroll);
        assertEquals("Other", history.forward().document.title);
    }
    @Test
    public void cacheExpiresAndEvictsLeastRecentlyUsed() {
        long[] time = {1};
        WikiCache<String, String> cache = new WikiCache<>(2, 100, () -> time[0]);
        cache.put("a", "A");
        cache.put("b", "B");
        assertEquals("A", cache.get("a"));
        cache.put("c", "C");
        assertNull(cache.get("b"));
        time[0] = 101;
        assertNull(cache.get("a"));
        assertEquals(1, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }
}
