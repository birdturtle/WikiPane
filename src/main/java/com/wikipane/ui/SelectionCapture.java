package com.wikipane.ui;

import com.wikipane.pin.*;
import com.wikipane.pin.PinSelection.*;
import java.util.*;
import javax.swing.JEditorPane;
import javax.swing.text.*;
import javax.swing.text.html.*;

/** Copies selected existing Swing document structure on the EDT. No fetching or HTML reparsing. */
public final class SelectionCapture {
    private SelectionCapture() {}
    public static Fragment capture(JEditorPane pane, int start, int end) {
        PinContext context = (PinContext) pane.getClientProperty("wiki.pin.context");
        if (context == null) throw new IllegalArgumentException("Select article content to pin.");
        HTMLDocument document = (HTMLDocument) pane.getDocument();
        start = Math.max(0, start); end = Math.min(document.getLength(), end);
        if (end - start > PinSelection.MAX_CHARACTERS)
            throw new IllegalArgumentException("Select a smaller passage to pin. Nothing has been truncated.");
        List<Paragraph> paragraphs = new ArrayList<>();
        Element previousItem = null;
        try {
            for (int offset = start; offset < end;) {
                Element paragraph = document.getParagraphElement(offset);
                int stop = Math.min(end, paragraph.getEndOffset());
                Element item = ancestor(paragraph, HTML.Tag.LI);
                String marker = item == null || item == previousItem ? "" : marker(item);
                int depth = 0;
                for (Element parent = item == null ? null : item.getParentElement(); parent != null; parent = parent.getParentElement())
                    if (is(parent, HTML.Tag.UL) || is(parent, HTML.Tag.OL)) depth++;
                previousItem = item;
                List<Span> spans = new ArrayList<>();
                for (int position = offset; position < stop;) {
                    Element character = document.getCharacterElement(position);
                    int next = Math.min(stop, character.getEndOffset());
                    AttributeSet attrs = character.getAttributes();
                    String text = document.getText(position, next - position);
                    if (is(character, HTML.Tag.BR)) {
                        if (!spans.isEmpty()) paragraphs.add(new Paragraph(spans, marker, Math.max(0, depth - 1)));
                        spans = new ArrayList<>(); marker = "";
                    } else {
                        boolean bold = context.part == PinContext.Part.HEADING || StyleConstants.isBold(attrs) || attrs.isDefined(HTML.Tag.B) || attrs.isDefined(HTML.Tag.STRONG)
                            || "bold".equals(String.valueOf(attrs.getAttribute(CSS.Attribute.FONT_WEIGHT)));
                        boolean italic = StyleConstants.isItalic(attrs) || attrs.isDefined(HTML.Tag.I) || attrs.isDefined(HTML.Tag.EM)
                            || "italic".equals(String.valueOf(attrs.getAttribute(CSS.Attribute.FONT_STYLE)));
                        Object a = attrs.getAttribute(HTML.Tag.A);
                        String link = a instanceof AttributeSet ? Objects.toString(((AttributeSet)a).getAttribute(HTML.Attribute.HREF), "") : "";
                        // Paragraph terminators are structural, never literal spaces between unrelated fields.
                        text = text.replace("\r", "").replace("\n", "");
                        if (!text.isEmpty()) spans.add(new Span(text, bold, italic, link));
                    }
                    position = next;
                }
                if (spans.stream().anyMatch(span -> !span.text.isBlank()))
                    paragraphs.add(new Paragraph(spans, marker, Math.max(0, depth - 1)));
                offset = stop;
                if (paragraphs.size() > PinSelection.MAX_PARAGRAPHS)
                    throw new IllegalArgumentException("Select a smaller passage to pin.");
            }
        } catch (BadLocationException ex) { throw new IllegalArgumentException("That selection is no longer available.", ex); }
        return new Fragment(context, start, end, paragraphs);
    }
    private static boolean is(Element element, HTML.Tag tag) {
        return element.getAttributes().getAttribute(StyleConstants.NameAttribute) == tag;
    }
    private static Element ancestor(Element element, HTML.Tag tag) {
        for (; element != null; element = element.getParentElement()) if (is(element, tag)) return element;
        return null;
    }
    private static int number(Object value, int fallback) {
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }
    private static String marker(Element item) {
        Element list = item.getParentElement();
        if (!is(list, HTML.Tag.OL)) return "•";
        int ordinal = number(list.getAttributes().getAttribute(HTML.Attribute.START), 1);
        for (int i = 0; i < list.getElementCount(); i++) {
            Element sibling = list.getElement(i);
            if (!is(sibling, HTML.Tag.LI)) continue;
            ordinal = number(sibling.getAttributes().getAttribute(HTML.Attribute.VALUE), ordinal);
            if (sibling == item) return ordinal + ".";
            ordinal++;
        }
        return ordinal + ".";
    }
}
