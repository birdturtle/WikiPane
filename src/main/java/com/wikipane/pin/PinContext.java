package com.wikipane.pin;

import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;

/** Semantic provenance for a rendered fragment, independent of Swing and cached presentation. */
public final class PinContext {
    public enum Part { TEXT, HEADING, LABEL, VALUE }
    public final SemanticDocument document;
    public final Node node;
    public final Source source;
    public final String group, record, label;
    public final RichText value;
    public final Requirement condition;
    public final Part part;
    public PinContext(SemanticDocument document, Node node) {
        this(document, node, node.source, "", "", "", null, null, Part.TEXT);
    }
    private PinContext(SemanticDocument document, Node node, Source source, String group, String record,
                       String label, RichText value, Requirement condition, Part part) {
        this.document = document; this.node = node; this.source = source;
        this.group = group; this.record = record; this.label = label;
        this.value = value; this.condition = condition; this.part = part;
    }
    public PinContext heading() {
        return new PinContext(document, node, source, group, record, label, value, condition, Part.HEADING);
    }
    public PinContext group(String name) {
        return new PinContext(document, node, source, name, record, label, value, condition, part);
    }
    public PinContext record(String name) {
        return new PinContext(document, node, source, group, name, label, value, condition, part);
    }
    public PinContext field(Source source, String label, RichText value, Part part) {
        return new PinContext(document, node, source, group, record, label, value, null, part);
    }
    public PinContext condition(Source source, String title, Requirement requirement) {
        return new PinContext(document, node, source, title, record, "", null, requirement, Part.TEXT);
    }
    public String restriction() {
        if (document.title.toLowerCase(java.util.Locale.ROOT).matches(".*/strateg(y|ies)$")
            || node.role == Role.STRATEGY || source.sectionTitle.toLowerCase(java.util.Locale.ROOT)
            .matches(".*(strategies|strategy|suggested skills|recommended equipment).*"))
            return "Combat strategy text stays in the reader. Static statistics can be pinned.";
        if (document.type == PageType.MONSTER && (value == null || condition != null))
            return "Monster pins support static statistics; combat advice stays in the reader.";
        return "";
    }
}
