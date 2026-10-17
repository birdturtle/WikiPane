package com.wikipane.navigation;

import com.wikipane.document.WikiDocument;
import java.util.*;

/** Accessed on EDT. Entries retain immutable documents and exact viewport positions. */
public final class NavigationHistory {
    public static final class Entry {
        public final WikiDocument document;
        public final com.wikipane.view.WikiPage page;
        public final String anchor;
        public int scroll;
        public String area="",variant="";
        public final Map<String,Integer> areaScroll=new HashMap<>();
        public final Map<String, Integer> tables = new HashMap<>();
        public Entry(WikiDocument document, String anchor, int scroll) {
            this(new com.wikipane.view.WikiPage(document),anchor,scroll);
        }
        public Entry(com.wikipane.view.WikiPage page,String anchor,int scroll){
            this.page=page;this.document=page.document;
            this.anchor = anchor;
            this.scroll = scroll;
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
    public void push(WikiDocument document,String anchor){push(new com.wikipane.view.WikiPage(document),anchor);}
    public void push(com.wikipane.view.WikiPage page,String anchor){WikiDocument document=page.document;
        Entry old = current();
        if (old != null && old.document.url.equals(document.url) && old.anchor.equals(anchor))
            return;
        while (entries.size() > index + 1) entries.remove(entries.size() - 1);
        entries.add(new Entry(page, anchor, 0));
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
