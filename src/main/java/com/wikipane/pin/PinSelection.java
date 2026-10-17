package com.wikipane.pin;

import com.wikipane.semantic.SemanticDocument.Source;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable selection, not a screenshot or arbitrary HTML. Semantic references survive formatting. */
public final class PinSelection {
    public static final int MAX_CHARACTERS = 12000, MAX_FRAGMENTS = 128, MAX_PARAGRAPHS = 256;
    public static final class Span {
        public final String text, link;
        public final boolean bold, italic;
        public Span(String text, boolean bold, boolean italic, String link) {
            this.text = text; this.bold = bold; this.italic = italic; this.link = link;
        }
    }
    public static final class Paragraph {
        public final List<Span> spans;
        public final String marker;
        public final int depth;
        public Paragraph(List<Span> spans, String marker, int depth) {
            this.spans = List.copyOf(spans); this.marker = marker; this.depth = depth;
        }
        public String text() {
            StringBuilder text = new StringBuilder();
            for (Span span : spans) text.append(span.text);
            return text.toString();
        }
    }
    public static final class Fragment {
        public final PinContext context;
        public final int start, end;
        public final List<Paragraph> paragraphs;
        public Fragment(PinContext context, int start, int end, List<Paragraph> paragraphs) {
            this.context = context; this.start = start; this.end = end;
            this.paragraphs = List.copyOf(paragraphs);
        }
        public String text() {
            return String.join("\n", paragraphs.stream().map(Paragraph::text).toArray(String[]::new));
        }
    }
    public final List<Fragment> fragments;
    public final List<Source> sources;
    public final String id;
    public PinSelection(List<Fragment> fragments) {
        if (fragments.isEmpty() || fragments.size() > MAX_FRAGMENTS)
            throw new IllegalArgumentException("Select a smaller passage to pin.");
        int characters = 0, paragraphs = 0;
        List<Source> sources = new ArrayList<>();
        StringBuilder signature = new StringBuilder();
        String article = fragments.get(0).context.document.url;
        for (Fragment fragment : fragments) {
            if (!fragment.context.document.url.equals(article))
                throw new IllegalArgumentException("A pin must come from one article.");
            characters += fragment.text().length(); paragraphs += fragment.paragraphs.size();
            Source source = fragment.context.source;
            PinContext context = fragment.context;
            if (!context.document.nodes.contains(context.node) || source.revision != context.document.revision
                || !source.url.equals(context.document.url)
                || !(source.unit.equals(context.node.source.unit) || context.node.units.contains(source.unit)))
                throw new IllegalArgumentException("That selection has no matching Wiki source.");
            if (!com.wikipane.api.WikiUrls.wiki(com.wikipane.api.WikiUrls.resolve(source.url)))
                throw new IllegalArgumentException("The selection has no supported Wiki source.");
            if (sources.stream().noneMatch(s -> s.url.equals(source.url) && s.section.equals(source.section)))
                sources.add(source);
            signature.append(source.url).append('|').append(source.revision).append('|').append(source.unit)
                .append('|').append(fragment.context.group).append('|').append(fragment.context.record)
                .append('|').append(fragment.context.label).append('|').append(fragment.context.part)
                .append('|').append(fragment.start).append(':').append(fragment.end).append('|');
            for (Paragraph paragraph : fragment.paragraphs) {
                signature.append(paragraph.depth).append(':').append(paragraph.marker).append(':');
                for (Span span : paragraph.spans) signature.append(span.text).append(':')
                    .append(span.bold).append(':').append(span.italic).append(':').append(span.link).append('|');
            }
        }
        if (characters == 0) throw new IllegalArgumentException("Highlight text to pin it.");
        if (characters > MAX_CHARACTERS || paragraphs > MAX_PARAGRAPHS)
            throw new IllegalArgumentException("Select a smaller passage to pin. Nothing has been truncated.");
        this.fragments = List.copyOf(fragments); this.sources = List.copyOf(sources);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            id = hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
