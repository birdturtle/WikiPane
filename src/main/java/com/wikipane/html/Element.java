package com.wikipane.html;

import java.net.URI;
import java.util.*;

/** Wiki markup retained for normalization; deliberately has no browser or CSS execution behavior. */
public class Element extends Node {
    private final String tag;
    final Map<String, String> attrs = new LinkedHashMap<>();
    final List<Node> nodes = new ArrayList<>();
    String base = "";
    private static final Set<String> BLOCK = Set.of("address", "article", "aside", "blockquote", "br", "caption",
        "center", "dd", "div", "dl", "dt", "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4",
        "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table", "tbody", "td",
        "tfoot", "th", "thead", "tr", "ul");
    public Element(String tag) { this.tag = tag.toLowerCase(Locale.ROOT); }
    public String tagName() { return tag; }
    public String normalName() { return tag; }
    public String attr(String key) { return attrs.getOrDefault(key.toLowerCase(Locale.ROOT), ""); }
    public Element attr(String key, String value) { attrs.put(key.toLowerCase(Locale.ROOT), value); return this; }
    public boolean hasAttr(String key) { return attrs.containsKey(key.toLowerCase(Locale.ROOT)); }
    public String id() { return attr("id"); }
    public Set<String> classNames() {
        return new LinkedHashSet<>(Arrays.asList(attr("class").strip().split("\\s+")));
    }
    public boolean hasClass(String name) { return classNames().contains(name); }
    public Element addClass(String name) { return attr("class", (attr("class") + " " + name).strip()); }
    public String baseUri() { return base.isEmpty() && parent != null ? parent.baseUri() : base; }
    public String absUrl(String key) {
        try { return URI.create(baseUri()).resolve(attr(key)).toString(); }
        catch (IllegalArgumentException e) { return ""; }
    }
    @Override public List<Node> childNodes() { return new ArrayList<>(nodes); }
    public Elements children() {
        Elements result = new Elements();
        for (Node node : nodes) if (node instanceof Element) result.add((Element)node);
        return result;
    }
    public int childrenSize() { return children().size(); }
    public Element child(int index) { return children().get(index); }
    public Element appendChild(Node node) { node.remove(); nodes.add(node); node.parent = this; return this; }
    public Elements parents() {
        Elements out = new Elements();
        for (Element node = parent; node != null; node = node.parent) out.add(node);
        return out;
    }
    public Element append(String html) {
        for (Node node : WikiHtml.parseBodyFragment(html, baseUri()).body().childNodes()) appendChild(node);
        return this;
    }
    public Element prependElement(String name) {
        Element child = new Element(name); nodes.add(0, child); child.parent = this; return child;
    }
    public Element prependText(String text) {
        TextNode child = new TextNode(text); nodes.add(0, child); child.parent = this; return this;
    }
    public Element previousElementSibling() {
        if (parent == null) return null;
        Element previous = null;
        for (Node node : parent.nodes) {
            if (node == this) return previous;
            if (node instanceof Element) previous = (Element)node;
        }
        return null;
    }
    @Override public Element clone() {
        Element result = new Element(tag); result.attrs.putAll(attrs); result.base = baseUri();
        for (Node node : nodes) result.appendChild(node instanceof Element ? ((Element)node).clone()
            : new TextNode(((TextNode)node).getWholeText()));
        return result;
    }
    public String html() {
        StringBuilder out = new StringBuilder(); for (Node node : nodes) node.serialize(out); return out.toString();
    }
    public Element html(String html) {
        for (Node node : nodes) node.parent = null;
        nodes.clear();
        for (Node node : WikiHtml.parseBodyFragment(html, baseUri()).body().childNodes()) appendChild(node);
        return this;
    }
    public Element text(String text) {
        for (Node node : nodes) node.parent = null;
        nodes.clear(); return appendChild(new TextNode(text));
    }
    public String wholeText() { StringBuilder out = new StringBuilder(); collectText(out, false); return out.toString(); }
    public String text() {
        StringBuilder out = new StringBuilder(); collectText(out, true);
        return out.toString().replace('\u00a0', ' ').replaceAll("[\\t\\n\\r\\f ]+", " ").strip();
    }
    private void collectText(StringBuilder out, boolean spacing) {
        for (Node node : nodes) {
            if (node instanceof TextNode) out.append(((TextNode)node).getWholeText());
            else {
                Element element = (Element)node;
                if (Set.of("script", "style").contains(element.tag)) continue;
                boolean boundary = spacing && BLOCK.contains(element.tag);
                if (boundary && out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
                element.collectText(out, spacing);
                if (boundary && out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
            }
        }
    }
    @Override void serialize(StringBuilder out) {
        out.append('<').append(tag);
        attrs.forEach((key, value) -> out.append(' ').append(key).append("=\"")
            .append(WikiHtml.escape(value)).append('"'));
        out.append('>');
        if (!WikiHtml.VOID.contains(tag)) { out.append(html()); out.append("</").append(tag).append('>'); }
    }
    public Elements getAllElements() {
        Elements out = new Elements(); collect(this, out); return out;
    }
    private static void collect(Element element, Elements out) {
        out.add(element); for (Element child : element.children()) collect(child, out);
    }
    public Elements select(String selector) { return MarkupQuery.select(this, selector); }
    public Element selectFirst(String selector) { return select(selector).first(); }
    public Element closest(String selector) {
        for (Element element = this; element != null; element = element.parent)
            if (MarkupQuery.matches(element, selector)) return element;
        return null;
    }
}
