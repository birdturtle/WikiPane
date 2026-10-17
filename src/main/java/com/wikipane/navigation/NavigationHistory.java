package com.wikipane.navigation;

import com.wikipane.document.WikiDocument;
import com.wikipane.view.WikiPage;
import java.util.*;

/** One EDT-owned history for search results, articles, and source-section jumps. */
public final class NavigationHistory {
    public static final class Entry {
        public final WikiDocument document;
        public final WikiPage page;
        public final String anchor, query;
        public final List<String> results;
        public int scroll;
        public boolean skillPositioned;
        public String area = "", variant = "", section = "";
        public final Map<String, Integer> areaScroll = new HashMap<>();
        public final Map<String, Integer> tables = new HashMap<>();
        public Entry(WikiDocument document, String anchor, int scroll) {
            this(new WikiPage(document), anchor, scroll);
        }
        public Entry(WikiPage page, String anchor, int scroll) {
            this.page = page;
            document = page.document;
            this.anchor = anchor;
            this.section = anchor;
            this.scroll = scroll;
            query = "";
            results = List.of();
        }
        private Entry(String query, List<String> results, int scroll) {
            page = null;
            document = null;
            anchor = "";
            this.query = query;
            this.results = List.copyOf(results);
            this.scroll = scroll;
        }
        public boolean isSearch() {
            return page == null;
        }
    }
    private final List<Entry> entries = new ArrayList<>();
    private int index = -1;
    public Entry current() {
        return index < 0 ? null : entries.get(index);
    }
    public boolean canBack() {
        return index > 0;
    }
    public boolean canForward() {
        return index + 1 < entries.size();
    }
    public void saveScroll(int scroll) {
        if (current() != null)
            current().scroll = scroll;
    }
    public void push(WikiDocument document, String anchor) {
        push(new WikiPage(document), anchor);
    }
    public void push(WikiPage page, String anchor) {
        Entry old = current();
        if (old != null && !old.isSearch() && old.document.url.equals(page.document.url)
            && old.anchor.equals(anchor))
            return;
        append(new Entry(page, anchor, 0));
    }
    public void pushDestination(WikiPage page, String area, String anchor) {
        Entry old = current();
        if (old != null && !old.isSearch() && old.document.url.equals(page.document.url)
            && old.area.equals(area) && old.section.equals(anchor))
            return;
        Entry entry = new Entry(page, anchor, 0);
        entry.area = area;
        append(entry);
    }
    public void pushSearch(String query, List<String> results) {
        if (current() != null && current().isSearch())
            return;
        append(new Entry(query, results, 0));
    }
    public void updateSearch(String query, List<String> results) {
        if (current() == null || !current().isSearch()) {
            pushSearch(query, results);
            return;
        }
        while (entries.size() > index + 1) entries.remove(entries.size() - 1);
        entries.set(index, new Entry(query, results, 0));
    }
    private void append(Entry entry) {
        while (entries.size() > index + 1) entries.remove(entries.size() - 1);
        entries.add(entry);
        index++;
        if (entries.size() > 40) {
            entries.remove(0);
            index--;
        }
    }
    public Entry back() {
        if (canBack())
            index--;
        return current();
    }
    public Entry forward() {
        if (canForward())
            index++;
        return current();
    }
    public void clear() {
        entries.clear();
        index = -1;
    }
}
