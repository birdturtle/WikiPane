package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.Requirement;
import java.util.*;
import com.wikipane.vendor.jsoup.Jsoup;
import com.wikipane.vendor.jsoup.nodes.Element;

/** Source-preserving rows for conjunctions and nested prerequisites; alternatives stay together. */
public final class RequirementRows {
    public static final class Row {
        public final Requirement condition;
        public final String html, parent;
        public final int depth;
        Row(Requirement condition, String html, int depth, String parent) {
            this.condition = condition; this.html = RequirementPresentation.html(html);
            this.depth = depth; this.parent = parent;
        }
    }
    private RequirementRows() {}
    public static List<Row> of(Requirement condition) {
        List<Row> rows = new ArrayList<>(); append(condition, 0, "", rows); return List.copyOf(rows);
    }
    private static void append(Requirement condition, int depth, String parent, List<Row> rows) {
        String html = condition.original.html.strip();
        Element root = Jsoup.parseBodyFragment(html).body();
        Element first = root.childrenSize() == 1 ? root.child(0) : null;
        if (condition.kind == Requirement.Kind.ALL && first != null && first.tagName().equals("ul")
            && !condition.children.isEmpty()) {
            for (Requirement child : condition.children) append(child, depth, parent, rows);
            return;
        }
        if (condition.kind == Requirement.Kind.ALL && first != null && first.tagName().equals("li")
            && first.children().stream().anyMatch(e -> e.tagName().equals("ul"))
            && first.select("ol").isEmpty() && !condition.children.isEmpty() && depth < 16
            && noTrailingProse(first)) {
            Element own = first.clone(); own.select("ul,ol").remove();
            Requirement leading = condition.children.get(0);
            boolean ownCondition = leading.children.isEmpty() && leading.original.text.equals(own.text());
            rows.add(new Row(ownCondition ? leading : null, own.html(), depth, parent));
            for (int i = ownCondition ? 1 : 0; i < condition.children.size(); i++)
                append(condition.children.get(i), depth + 1, own.text(), rows);
            return;
        }
        String content = html.replaceFirst("(?is)^<li(?: [^>]*)?>", "").replaceFirst("(?is)</li>$", "");
        rows.add(new Row(condition, content, depth, parent));
    }
    private static boolean noTrailingProse(Element root) {
        boolean list = false;
        for (com.wikipane.vendor.jsoup.nodes.Node node : root.childNodes()) {
            if (node instanceof Element && ((Element)node).tagName().equals("ul")) list = true;
            else if (list && (node instanceof com.wikipane.vendor.jsoup.nodes.TextNode
                ? !((com.wikipane.vendor.jsoup.nodes.TextNode)node).text().isBlank() : !(node instanceof Element) || !((Element)node).text().isBlank())) return false;
        }
        return true;
    }
}
