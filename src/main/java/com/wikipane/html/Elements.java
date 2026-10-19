package com.wikipane.html;

import java.util.*;
import java.util.stream.Collectors;

/** Snapshot selections permit safe removal while traversing the source fragment. */
public final class Elements extends ArrayList<Element> {
    private static final long serialVersionUID = 1L;
    public void remove() { forEach(Element::remove); }
    public Element first() { return isEmpty() ? null : get(0); }
    public Element last() { return isEmpty() ? null : get(size() - 1); }
    public List<String> eachAttr(String key) {
        return stream().filter(e -> e.hasAttr(key)).map(e -> e.attr(key)).collect(Collectors.toList());
    }
}
