package com.wikipane.html;

import java.util.*;

/** An in-memory fragment node. Never loads URLs, stylesheets or executable content. */
public abstract class Node {
    Element parent;
    public Element parent() { return parent; }
    public List<Node> childNodes() { return List.of(); }
    public void remove() { if (parent != null) { parent.nodes.remove(this); parent = null; } }
    public void before(Node node) { insert(node, 0); }
    public void after(Node node) { insert(node, 1); }
    private void insert(Node node, int offset) {
        if (parent == null) return;
        Element target = parent;
        node.remove();
        target.nodes.add(target.nodes.indexOf(this) + offset, node);
        node.parent = target;
    }
    public void replaceWith(Node node) { before(node); remove(); }
    abstract void serialize(StringBuilder out);
}
