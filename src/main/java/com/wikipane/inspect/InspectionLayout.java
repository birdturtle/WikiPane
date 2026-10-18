package com.wikipane.inspect;

import com.wikipane.knowledge.Inspection;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

/** Prepared once on the worker. Overlay rendering only paints already wrapped, bounded lines. */
public final class InspectionLayout {
    public static final int WIDTH = 210, PADDING = 6, INNER = WIDTH - PADDING * 2, LINES = 12, LINE_HEIGHT = 13;
    public static final Font BODY = net.runelite.client.ui.FontManager.getRunescapeSmallFont();
    public static final Font LABEL = BODY;
    public static final Font TITLE = net.runelite.client.ui.FontManager.getRunescapeBoldFont();
    private static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), true, true);
    public static final class Line {
        public final String text;
        public final boolean label;
        public final String value;
        public final Color color;
        public final int indent;
        Line(String text, boolean label) { this(text, label, ""); }
        Line(String text, boolean label, String value) { this(text, label, value, com.wikipane.ui.SemanticPalette.TEXT); }
        Line(String text, boolean label, String value, Color color) { this(text,label,value,color,0); }
        Line(String text, boolean label, String value, Color color, int indent) {
            this.text=text; this.label=label; this.value=value; this.color=color; this.indent=indent;
        }
    }
    public final List<List<Line>> pages;
    public final List<String> title;
    public final String variant;
    public InspectionLayout(Inspection inspection) {
        title = wrap(inspection.article.document.title, TITLE, INNER - 28);
        variant = inspection.article.variant;
        List<Line> lines = new ArrayList<>();
        if (!variant.isEmpty()) add(lines, variant.matches("[0-9]+") ? "Variant " + variant : variant, true);
        if (!inspection.article.variantKnown) {
            lines.add(new Line("Variant not verified", true));
            add(lines, "Variant-specific statistics are hidden. Open the article to choose a variant.", false);
        }
        String context = "";
        for (Inspection.Row row : inspection.rows) {
            String label = row.label;
            Color color = com.wikipane.ui.SemanticPalette.field(com.wikipane.view.FieldPresentation.of(
                label, row.value, com.wikipane.semantic.SemanticDocument.Role.INFORMATION));
            if (!row.source.section.isEmpty() && !context.equals(row.source.sectionTitle)) {
                context = row.source.sectionTitle;
                add(lines, context, true);
            }
            // A compact selection may omit a very long group, but must not cut its logical conditions.
            if (row.value.text.length() > 1200) {
                add(lines, label, true);
                add(lines, "Open the full article for this complete requirement group.", false);
            } else if ((row.condition == null || row.condition.children.isEmpty()) && !row.label.equals("About")
                && LABEL.getStringBounds(label, FRC).getWidth()
                    + BODY.getStringBounds(row.value.text, FRC).getWidth() + 7 <= INNER) {
                lines.add(new Line(label, false, row.value.text, color));
            } else {
                add(lines, label, true);
                for (String paragraph : structuredText(row.value.html)) {
                    int depth=0; while (depth<paragraph.length() && paragraph.charAt(depth)=='\t') depth++;
                    int indent=Math.min(6,depth)*8;
                    for (String part : wrap(paragraph.strip(), BODY, INNER-indent)) lines.add(new Line(part,false,"",color,indent));
                }
            }
        }
        if (lines.isEmpty()) add(lines, "No verified compact facts are available. Open the full article.", false);
        List<List<Line>> result = new ArrayList<>();
        int from = 0;
        String previousLabel = "";
        while (from < lines.size()) {
            List<Line> page = new ArrayList<>();
            if (from > 0 && !lines.get(from).label && lines.get(from).value.isEmpty() && !previousLabel.isEmpty())
                for (String label : wrap(previousLabel + " (continued)", LABEL, INNER))
                    page.add(new Line(label, true));
            int end = Math.min(lines.size(), from + LINES - page.size());
            if (end < lines.size() && lines.get(end - 1).label && end - from > 1) end--;
            page.addAll(lines.subList(from, end));
            for (int i = from; i < end; i++) if (lines.get(i).label) previousLabel = lines.get(i).text;
            result.add(List.copyOf(page));
            from = end;
        }
        pages = List.copyOf(result);
    }
    /** Preserve list boundaries and AND/OR prose from the semantic source instead of flattening it. */
    static List<String> structuredText(String html) {
        org.jsoup.nodes.Element root = org.jsoup.Jsoup.parseBodyFragment(html).body();
        for (org.jsoup.nodes.Element list : root.select("ol,ul")) {
            int index = 1;
            try { if (list.hasAttr("start")) index = Integer.parseInt(list.attr("start")); } catch (NumberFormatException ignored) {}
            for (org.jsoup.nodes.Element item : list.children()) if (item.tagName().equals("li")) {
                try { if (item.hasAttr("value")) index = Integer.parseInt(item.attr("value")); } catch (NumberFormatException ignored) {}
                int depth=0;
                for (org.jsoup.nodes.Element ancestor=list.parent(); ancestor!=null; ancestor=ancestor.parent())
                    if (ancestor.tagName().equals("ol") || ancestor.tagName().equals("ul")) depth++;
                item.prependText("\t".repeat(depth) + (list.tagName().equals("ol") ? index++ + ". " : "• "));
            }
        }
        for (org.jsoup.nodes.Element block : root.select("li,p,dt,dd,br")) {
            block.before(new org.jsoup.nodes.TextNode("\n"));
            if (!block.tagName().equals("br")) block.after(new org.jsoup.nodes.TextNode("\n"));
        }
        List<String> result = new ArrayList<>();
        for (String line : root.wholeText().split("\n")) if (!line.isBlank()) {
            int depth=0; while(depth<line.length() && line.charAt(depth)=='\t')depth++;
            result.add("\t".repeat(depth)+line.strip().replaceAll("[ \t]+", " "));
        }
        return result.isEmpty() ? List.of(root.text()) : List.copyOf(result);
    }
    private static void add(List<Line> lines, String text, boolean label) {
        for (String part : wrap(text, label ? LABEL : BODY, INNER)) lines.add(new Line(part, label));
    }
    public static List<String> wrap(String text, Font font, int width) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty()) return List.of("");
        java.text.AttributedString attributed = new java.text.AttributedString(text);
        attributed.addAttribute(TextAttribute.FONT, font);
        LineBreakMeasurer measure = new LineBreakMeasurer(attributed.getIterator(), FRC);
        while (measure.getPosition() < text.length()) {
            int start = measure.getPosition();
            measure.nextLayout(Math.max(1, width));
            result.add(text.substring(start, measure.getPosition()).strip());
        }
        return List.copyOf(result);
    }
}
