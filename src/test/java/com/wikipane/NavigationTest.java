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
        assertFalse(h.canForward());
        assertEquals("Requirements", h.current().anchor);
        assertEquals(731, h.back().scroll);
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
