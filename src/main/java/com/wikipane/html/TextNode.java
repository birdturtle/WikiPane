package com.wikipane.html;

public final class TextNode extends Node {
    private final String value;
    public TextNode(String value) { this.value = value; }
    public String text() { return value.replaceAll("[\\t\\n\\r\\f ]+", " "); }
    public String getWholeText() { return value; }
    @Override void serialize(StringBuilder out) { out.append(WikiHtml.escape(value)); }
}
