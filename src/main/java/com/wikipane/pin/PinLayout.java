package com.wikipane.pin;

import com.wikipane.pin.PinSelection.*;
import com.wikipane.semantic.SemanticDocument.Source;
import com.wikipane.ui.SemanticPalette;
import java.awt.*;
import java.awt.font.*;
import java.awt.geom.AffineTransform;
import java.text.AttributedString;
import java.util.*;
import java.util.List;
import net.runelite.client.ui.FontManager;

/** Miniature reference rules: source-backed labels, intact lists, styled text, bounded native pages. */
public final class PinLayout {
    public static final int WIDTH = 210, PAD = 6, INNER = WIDTH - PAD * 2, LINES = 6, STEP = 15;
    public static final Font BODY = FontManager.getRunescapeSmallFont();
    private static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), false, false);
    public static final class Line {
        public final TextLayout text;
        public final String plain, continuation;
        public final int x;
        public final boolean heading;
        public final Source source;
        Line(TextLayout text, String plain, int x, boolean heading, Source source, String continuation) {
            this.text = text; this.plain = plain; this.x = x; this.heading = heading;
            this.source = source; this.continuation = continuation;
        }
    }
    public final List<List<Line>> pages;
    public final List<Line> title;
    public final PinSelection selection;
    public PinLayout(PinSelection selection) {
        this.selection = selection;
        for (Fragment fragment : selection.fragments) {
            String restriction = fragment.context.restriction();
            if (!restriction.isEmpty()) throw new IllegalArgumentException(restriction);
        }
        Source first = selection.sources.get(0);
        List<Line> heading = wrap(List.of(new Span(shorten(first.article, 64), true, false, "")), "", 0, true, first, "");
        if (heading.size() > 2) {
            String last = heading.get(1).plain.strip();
            List<Line> shortened;
            do {
                if (!last.isEmpty()) last = last.substring(0, last.offsetByCodePoints(last.length(), -1));
                shortened = wrap(List.of(new Span(last + "...", true, false, "")), "", 0, true, first, "");
            } while (shortened.size() > 1);
            title = List.of(heading.get(0), shortened.get(0));
        } else title = List.copyOf(heading);
        List<Line> lines = new ArrayList<>();
        Set<String> values = new HashSet<>();
        for (Fragment fragment : selection.fragments)
            if (fragment.context.part == PinContext.Part.VALUE) values.add(fieldKey(fragment.context));
        String lastGroup = "", lastRecord = "", lastVariant = "";
        int metadata = 0;
        for (Fragment fragment : selection.fragments) {
            PinContext context = fragment.context;
            String selected = fragment.text();
            metadata += context.group.length() + context.record.length() + context.label.length();
            if (metadata > PinSelection.MAX_CHARACTERS)
                throw new IllegalArgumentException("This selection has too much context for a compact pin. Select a shorter passage.");
            String variant = context.node.table == null ? "" : context.node.table.variant;
            if (!variant.isBlank() && !variant.equals(lastVariant)) {
                lines.addAll(header(variant.matches("[0-9]+") ? "Variant " + variant : variant, context.source));
                lastVariant = variant;
            }
            if (context.part == PinContext.Part.LABEL && values.contains(fieldKey(context))
                && normalize(context.label).contains(normalize(selected))) continue;
            if (!context.group.isBlank() && !context.group.equals(context.source.article)
                && !context.group.equals(lastGroup) && !normalize(selected).equals(normalize(context.group))) {
                lines.addAll(header(context.group, context.source));
            }
            lastGroup = context.group;
            if (!context.record.isBlank() && !context.record.equals(lastRecord)
                && !normalize(selected).equals(normalize(context.record)))
                lines.addAll(header(context.record, context.source));
            lastRecord = context.record;
            boolean field = context.part == PinContext.Part.VALUE && !context.label.isBlank()
                && !normalize(context.label).equals(normalize(selected));
            String fieldLabel = context.label;
            if (field && context.value != null && !normalize(selected).equals(normalize(context.value.text)))
                fieldLabel += " (excerpt)";
            boolean stacked = field && (fragment.paragraphs.size() > 1
                || fragment.paragraphs.stream().anyMatch(paragraph -> !paragraph.marker.isEmpty()));
            if (stacked) lines.addAll(header(fieldLabel, context.source));
            Color color = context.part == PinContext.Part.VALUE && context.value != null
                ? SemanticPalette.field(com.wikipane.view.FieldPresentation.of(context.label, context.value, context.node.role))
                : SemanticPalette.TEXT;
            for (Paragraph paragraph : fragment.paragraphs) {
                List<Span> spans = new ArrayList<>();
                if (field && !stacked) spans.add(new Span(fieldLabel + ": ", true, false, ""));
                spans.addAll(paragraph.spans);
                String carry = field ? fieldLabel : !context.group.isBlank() ? context.group : context.source.sectionTitle;
                lines.addAll(wrap(spans, paragraph.marker, paragraph.depth, false, context.source, carry, color));
            }
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Highlight article text to pin it.");
        List<List<Line>> result = new ArrayList<>();
        for (int start = 0; start < lines.size();) {
            List<Line> page = new ArrayList<>();
            Line firstLine = lines.get(start);
            if (start > 0 && !firstLine.heading && !firstLine.continuation.isBlank()
                && lines.get(start - 1).source.unit.equals(firstLine.source.unit))
                page.addAll(header(shorten(firstLine.continuation, 32) + " (continued)", firstLine.source));
            int end = Math.min(lines.size(), start + LINES - page.size());
            if (end <= start) throw new IllegalArgumentException("That selection cannot fit a compact overlay.");
            if (end < lines.size() && end - start > 1 && lines.get(end - 1).heading) end--;
            page.addAll(lines.subList(start, end)); result.add(List.copyOf(page)); start = end;
        }
        pages = List.copyOf(result);
    }
    private static String fieldKey(PinContext context) {
        return context.source.url + "|" + context.source.unit + "|" + context.label;
    }
    private static String normalize(String text) { return text.replaceAll("(?U)\\s+", " ").strip(); }
    private static String shorten(String text, int limit) {
        return text.codePointCount(0, text.length()) <= limit ? text
            : text.substring(0, text.offsetByCodePoints(0, limit - 3)) + "...";
    }
    private static List<Line> header(String text, Source source) {
        return wrap(List.of(new Span(text, true, false, "")), "", 0, true, source, text);
    }
    private static Font font(Span span) {
        int style = (span.bold ? Font.BOLD : Font.PLAIN) | (span.italic ? Font.ITALIC : Font.PLAIN);
        Font nativeFont = BODY.deriveFont(style);
        return nativeFont.canDisplayUpTo(span.text) == -1 ? nativeFont
            : FontManager.getDefaultFont().deriveFont(style, 11f);
    }
    private static List<Line> wrap(List<Span> original, String marker, int depth, boolean heading,
                                   Source source, String continuation) {
        return wrap(original, marker, depth, heading, source, continuation, SemanticPalette.TEXT);
    }
    private static List<Line> wrap(List<Span> original, String marker, int depth, boolean heading,
                                   Source source, String continuation, Color color) {
        List<Span> spans = new ArrayList<>();
        String prefix = marker.isEmpty() ? "" : (marker.equals("•") ? "-" : marker) + " ";
        if (!prefix.isEmpty()) spans.add(new Span(prefix, false, false, ""));
        spans.addAll(original);
        StringBuilder plain = new StringBuilder();
        for (Span span : spans) plain.append(span.text);
        if (plain.toString().isBlank()) return List.of();
        AttributedString styled = new AttributedString(plain.toString());
        int offset = 0;
        for (Span span : spans) {
            int end = offset + span.text.length();
            if (end > offset) {
                styled.addAttribute(TextAttribute.FONT, font(span), offset, end);
                styled.addAttribute(TextAttribute.FOREGROUND, heading || span.bold || !span.link.isEmpty() ? SemanticPalette.LINK : color, offset, end);
            }
            offset = end;
        }
        int indent = Math.min(8, Math.max(0, depth)) * 6;
        int hanging = prefix.isEmpty() ? 0 : (int)Math.ceil(BODY.getStringBounds(prefix, FRC).getWidth());
        List<Line> result = new ArrayList<>();
        LineBreakMeasurer measure = new LineBreakMeasurer(styled.getIterator(), FRC);
        while (measure.getPosition() < plain.length()) {
            int start = measure.getPosition(), x = indent + (start == 0 ? 0 : hanging);
            TextLayout text = measure.nextLayout(INNER - x);
            result.add(new Line(text, plain.substring(start, measure.getPosition()), x, heading, source, continuation));
        }
        return result;
    }
}
