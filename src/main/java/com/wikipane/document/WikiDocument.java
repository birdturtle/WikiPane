package com.wikipane.document;

import java.util.List;
import java.util.Map;

/** Immutable, UI-independent article. HTML is limited to normalized inline formatting. */
public final class WikiDocument {
    public final String title, url;
    public final long revision;
    public final List<Block> blocks;
    public final List<Section> sections;
    public final Map<String, String> entityAnchors;
    public final String redirectAnchor;
    public final java.util.Set<String> categories;
    public final com.wikipane.semantic.SemanticDocument semantic;
    public WikiDocument(String title, String url, long revision, List<Block> blocks, List<Section> sections) {
        this(title, url, revision, blocks, sections, Map.of(), "");
    }
    public WikiDocument(String title, String url, long revision, List<Block> blocks, List<Section> sections,
        Map<String, String> entityAnchors, String redirectAnchor) {
        this(title, url, revision, blocks, sections, entityAnchors, redirectAnchor, java.util.Set.of());
    }
    public WikiDocument(String title, String url, long revision, List<Block> blocks, List<Section> sections,
        Map<String, String> entityAnchors, String redirectAnchor, java.util.Set<String> categories) {
        this.categories = java.util.Set.copyOf(categories);
        this.entityAnchors = Map.copyOf(entityAnchors);
        this.redirectAnchor = redirectAnchor;
        this.title = title;
        this.url = url;
        this.revision = revision;
        this.blocks = List.copyOf(blocks);
        this.sections = List.copyOf(sections);
        this.semantic = com.wikipane.semantic.SemanticExtractor.extract(this);
    }
    public interface Block {}
    public static final class Text implements Block {
        public final String html;
        public final List<com.wikipane.semantic.SemanticDocument.RequirementGroup> requirements;
        public Text(String html) {
            this(html, List.of());
        }
        public Text(String html, List<com.wikipane.semantic.SemanticDocument.RequirementGroup> requirements) {
            this.html = html;
            this.requirements = List.copyOf(requirements);
        }
    }
    public static final class Heading implements Block {
        public final String text, anchor;
        public final int level;
        public Heading(String text, String anchor, int level) {
            this.text = text;
            this.anchor = anchor;
            this.level = level;
        }
    }
    public static final class Image implements Block {
        public final String url, alt, link;
        public final int width, height;
        public Image(String url, String alt, String link, int width, int height) {
            this.url = url;
            this.alt = alt;
            this.link = link;
            this.width = width;
            this.height = height;
        }
    }
    public static final class Table implements Block {
        public final String caption, anchor, kind, variant;
        public final List<Row> rows;
        public final List<Image> images;
        public final java.util.Set<String> classes;
        public Table(String caption, String anchor, List<Row> rows) {
            this(caption, anchor, rows, "", "");
        }
        public Table(String caption, String anchor, List<Row> rows, String kind, String variant) {
            this(caption, anchor, rows, kind, variant, List.of());
        }
        public Table(
            String caption, String anchor, List<Row> rows, String kind, String variant, List<Image> images) {
            this(caption, anchor, rows, kind, variant, images, java.util.Set.of());
        }
        public Table(String caption, String anchor, List<Row> rows, String kind, String variant,
            List<Image> images, java.util.Set<String> classes) {
            this.classes = java.util.Set.copyOf(classes);
            this.images = List.copyOf(images);
            this.kind = kind;
            this.variant = variant;
            this.caption = caption;
            this.anchor = anchor;
            this.rows = List.copyOf(rows);
        }
    }
    public static final class Row {
        public final List<Cell> cells, carried;
        public Row(List<Cell> cells, List<Cell> carried) {
            this.cells = List.copyOf(cells);
            this.carried = List.copyOf(carried);
        }
    }
    public static final class Cell {
        public final String html, label, text, semanticKey, illustrationTarget;
        public final List<com.wikipane.semantic.SemanticDocument.RequirementGroup> requirements;
        public final List<Table> nested;
        public final boolean header;
        public final int column, colspan, rowspan;
        public Cell(String html, String label, boolean header, int column, int colspan, int rowspan,
            List<Table> nested) {
            this(html, label, header, column, colspan, rowspan, nested, "", List.of());
        }
        public Cell(String html, String label, boolean header, int column, int colspan, int rowspan,
            List<Table> nested, String semanticKey,
            List<com.wikipane.semantic.SemanticDocument.RequirementGroup> requirements) {
            this(html, label, header, column, colspan, rowspan, nested, semanticKey, requirements, "");
        }
        public Cell(String html, String label, boolean header, int column, int colspan, int rowspan,
            List<Table> nested, String semanticKey,
            List<com.wikipane.semantic.SemanticDocument.RequirementGroup> requirements,
            String illustrationTarget) {
            this.illustrationTarget = illustrationTarget;
            this.semanticKey = semanticKey;
            this.requirements = List.copyOf(requirements);
            this.html = html;
            this.text = org.jsoup.Jsoup.parseBodyFragment(html).text().strip();
            this.label = label;
            this.header = header;
            this.column = column;
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.nested = List.copyOf(nested);
        }
    }
    public static final class Section {
        public final String title, anchor;
        public Section(String title, String anchor) {
            this.title = title;
            this.anchor = anchor;
        }
        @Override
        public String toString() {
            return title;
        }
    }
}
