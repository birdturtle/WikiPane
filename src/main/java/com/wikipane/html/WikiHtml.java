package com.wikipane.html;

import java.util.*;
import javax.swing.text.html.parser.DTD;
import javax.swing.text.html.parser.Entity;
import javax.swing.text.html.parser.ParserDelegator;

/**
 * Reads MediaWiki's already-rendered HTML into a normalization tree. No fetching, scripting, CSS,
 * forms, HTML5 rendering engine or wikitext expansion. Unknown elements retain their children.
 * Supports quoted attributes, comments, entities and common omitted list/table end tags.
 */
public final class WikiHtml {
    static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr");
    private static final Set<String> PARAGRAPH_END = Set.of("address", "article", "aside", "blockquote", "div",
        "dl", "figure", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "ol", "p", "pre", "section", "table", "ul");
    private static final DTD ENTITIES = entities();
    private static DTD entities() {
        // Initializes the JDK's bundled entity table; no external DTD or URL is read.
        new ParserDelegator();
        try { return DTD.getDTD("html32"); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private WikiHtml() {}
    public static Document parse(String html) { return parseBodyFragment(html); }
    public static Document parse(String html, String base) { return parseBodyFragment(html, base); }
    public static Document parseBodyFragment(String html) { return parseBodyFragment(html, ""); }
    public static Document parseBodyFragment(String html, String base) {
        if (html.length() > 12_000_000) throw new IllegalArgumentException("Wiki markup exceeds the reader limit");
        Document document = new Document(base);
        List<Element> stack = new ArrayList<>(); stack.add(document.body());
        int cursor = 0, nodes = 0;
        while (cursor < html.length()) {
            if (++nodes > 300_000) throw new IllegalArgumentException("Wiki markup contains too many elements");
            Element parent = stack.get(stack.size() - 1);
            if (html.startsWith("<!--", cursor)) {
                int end = html.indexOf("-->", cursor + 4); cursor = end < 0 ? html.length() : end + 3; continue;
            }
            if (html.charAt(cursor) != '<') {
                int end = html.indexOf('<', cursor); if (end < 0) end = html.length();
                parent.appendChild(new TextNode(decode(html.substring(cursor, end)))); cursor = end; continue;
            }
            int end = tagEnd(html, cursor + 1);
            if (end < 0) { parent.appendChild(new TextNode(html.substring(cursor))); break; }
            String source = html.substring(cursor + 1, end); cursor = end + 1;
            if (source.isEmpty() || source.charAt(0) == '!' || source.charAt(0) == '?') continue;
            boolean closing = source.charAt(0) == '/'; int start = closing ? 1 : 0, nameEnd = start;
            while (nameEnd < source.length() && (Character.isLetterOrDigit(source.charAt(nameEnd))
                || source.charAt(nameEnd) == '-' || source.charAt(nameEnd) == ':')) nameEnd++;
            if (nameEnd == start) { parent.appendChild(new TextNode("<" + source + ">")); continue; }
            String tag = source.substring(start, nameEnd).toLowerCase(Locale.ROOT);
            if (closing) { close(stack, Set.of(tag), Set.of()); continue; }
            if (Set.of("html", "body", "head").contains(tag)) continue;
            if (Set.of("script", "style").contains(tag)) {
                // The reader intentionally strips these. Do not tokenize apparent tags inside raw text.
                int finish = indexOfIgnoreCase(html, "</" + tag, cursor);
                cursor = finish < 0 ? html.length() : html.indexOf('>', finish);
                cursor = cursor < 0 ? html.length() : cursor + 1; continue;
            }
            if (PARAGRAPH_END.contains(tag)) close(stack, Set.of("p"), Set.of("td", "th", "li", "div", "section"));
            if (tag.equals("li")) close(stack, Set.of("li"), Set.of("ul", "ol"));
            if (tag.equals("dt") || tag.equals("dd")) close(stack, Set.of("dt", "dd"), Set.of("dl"));
            if (tag.equals("tr")) close(stack, Set.of("tr"), Set.of("table"));
            if (tag.equals("td") || tag.equals("th")) close(stack, Set.of("td", "th"), Set.of("tr", "table"));
            Element element = new Element(tag); attributes(source, nameEnd, element);
            stack.get(stack.size() - 1).appendChild(element);
            if (!VOID.contains(tag) && !source.endsWith("/")) {
                if (stack.size() >= 256) throw new IllegalArgumentException("Wiki markup is nested too deeply");
                stack.add(element);
            }
        }
        return document;
    }
    private static int indexOfIgnoreCase(String text, String find, int start) {
        for (int i = start; i <= text.length() - find.length(); i++)
            if (text.regionMatches(true, i, find, 0, find.length())) return i;
        return -1;
    }
    private static void close(List<Element> stack, Set<String> tags, Set<String> boundary) {
        for (int i = stack.size() - 1; i > 0; i--) {
            String tag = stack.get(i).tagName();
            if (tags.contains(tag)) { stack.subList(i, stack.size()).clear(); return; }
            if (boundary.contains(tag)) return;
        }
    }
    private static int tagEnd(String source, int start) {
        char quote = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; }
            else if (c == '\'' || c == '"') quote = c;
            else if (c == '>') return i;
        }
        return -1;
    }
    private static void attributes(String source, int cursor, Element element) {
        while (cursor < source.length()) {
            while (cursor < source.length() && (Character.isWhitespace(source.charAt(cursor)) || source.charAt(cursor) == '/')) cursor++;
            int start = cursor;
            while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))
                && source.charAt(cursor) != '=' && source.charAt(cursor) != '/') cursor++;
            if (start == cursor) { cursor++; continue; }
            String key = source.substring(start, cursor); String value = "";
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor < source.length() && source.charAt(cursor) == '=') {
                cursor++; while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
                char quote = cursor < source.length() ? source.charAt(cursor) : 0;
                if (quote == '\'' || quote == '"') {
                    start = ++cursor;
                    while (cursor < source.length() && source.charAt(cursor) != quote) cursor++;
                    value = source.substring(start, cursor); if (cursor < source.length()) cursor++;
                } else {
                    start = cursor;
                    while (cursor < source.length() && !Character.isWhitespace(source.charAt(cursor))) cursor++;
                    value = source.substring(start, cursor);
                }
            }
            if (!element.hasAttr(key)) element.attr(key, decode(value));
        }
    }
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String decode(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                int end = text.indexOf(';', i + 1);
                if (end > i && end - i < 40) {
                    String name = text.substring(i + 1, end), decoded = null;
                    if (name.startsWith("#")) {
                        try {
                            int cp = Integer.parseInt(name.substring(name.length() > 1
                                && (name.charAt(1) == 'x' || name.charAt(1) == 'X') ? 2 : 1),
                                name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X') ? 16 : 10);
                            decoded = new String(Character.toChars(cp == 0 || cp >= 0xD800 && cp <= 0xDFFF ? 0xFFFD : cp));
                        } catch (IllegalArgumentException ignored) { /* Preserve unrecognized source text. */ }
                    } else {
                        Entity entity = ENTITIES.getEntity(name);
                        if (entity != null) decoded = entity.getString();
                        else if (name.equals("apos")) decoded = "'";
                    }
                    if (decoded != null) { out.append(decoded); i = end; continue; }
                }
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }
}
