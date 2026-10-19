package com.wikipane.html;

public final class Document extends Element {
    private final Element body;
    Document(String base) {
        super("html"); this.base = base;
        body = new Element("body"); appendChild(body);
    }
    public Element body() { return body; }
}
