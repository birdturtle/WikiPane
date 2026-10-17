package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.semantic.TableRecords;
import java.util.*;

/** Presentation contracts prepared on the article worker. No Swing or Wiki HTML layout decisions. */
public final class ReaderLayout {
    public static final class Field {
        public final String label;
        public final RichText value;
        public final Source source;
        public final boolean inline;
        public final FieldPresentation presentation;
        public String identity() { return FieldPresentation.identity(label, value, source); }
        Field(String label, RichText value, Source source) {
            this(label, value, source, Role.INFORMATION);
        }
        Field(String label, RichText value, Source source, Role role) {
            this.label = label;
            this.value = value;
            this.source = source;
            presentation = FieldPresentation.of(label, value, role);
            inline = !label.isBlank() && label.length() <= 24 && value.text.length() <= 24
                && !value.html.matches("(?is).*</?(ul|ol|li|p|br).*?");
        }
    }
    public static final class FactGroup {
        public final String title;
        public final List<Field> fields;
        FactGroup(String title, List<Field> fields) {
            this.title = title;
            this.fields = List.copyOf(fields);
        }
    }
    public static final class ConditionGroup {
        public final String title;
        public final Requirement condition;
        public final List<String> lines, styledLines;
        public final Source source;
        ConditionGroup(String title, Requirement condition, Source source) {
            this.title = title;
            this.condition = condition;
            List<String> content = new ArrayList<>();
            conditionLines(condition, content);
            lines = List.copyOf(content);
            styledLines = content.stream().map(RequirementPresentation::html).collect(java.util.stream.Collectors.toUnmodifiableList());
            this.source = source;
        }
    }
    private static void conditionLines(Requirement condition, List<String> lines) {
        if (condition.kind == Requirement.Kind.ALL && !condition.children.isEmpty()
            && condition.original.html.trim().matches("(?is)^<(ul|ol)[ >].*")) {
            for (Requirement child : condition.children) conditionLines(child, lines);
        } else {
            String html = condition.original.html.trim()
                              .replaceFirst("(?is)^<li(?: [^>]*)?>", "")
                              .replaceFirst("(?is)</li>$", "");
            lines.add(html);
        }
    }
    public static final class Section {
        public final String title, breadcrumb, textHtml;
        public final List<FactGroup> facts;
        public final List<ConditionGroup> requirements;
        Section(ArticleViewModel.Component c) {
            textHtml = c.node.text == null ? "" : !c.node.requirements.isEmpty()
                ? RequirementPresentation.html(c.node.text.html) : c.node.text.html;
            String[] path = c.source.sectionTitle.split(" / ");
            title = path[path.length - 1];
            breadcrumb = path.length > 1 ? String.join(" / ", Arrays.copyOf(path, path.length - 1)) : "";
            List<ConditionGroup> conditions = new ArrayList<>();
            Set<String> structured = new HashSet<>();
            if (c.type == ArticleViewModel.ComponentType.REQUIREMENTS)
                for (RequirementSet set : c.requirements) {
                    for (RequirementGroup group : set.groups)
                        conditions.add(new ConditionGroup(group.label, group.expression, set.source));
                    structured.add(set.source.unit);
                }
            requirements = List.copyOf(conditions);
            LinkedHashMap<String, List<Field>> grouped = new LinkedHashMap<>();
            for (Fact f : c.facts)
                if (!structured.contains(f.source.unit)) {
                    String group = f.group;
                    if (group.equals(c.source.article) || group.equals("Properties") || group.equals("Values")
                        || group.equalsIgnoreCase(f.label))
                        group = "";
                    grouped.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new Field(f.label, f.value, f.source, c.node.role));
                }
            List<FactGroup> result = new ArrayList<>();
            grouped.forEach((name, fields) -> result.add(new FactGroup(name, fields)));
            facts = List.copyOf(result);
        }
    }
    public static final class Record {
        public final String group;
        public final boolean columnHeader;
        public final Field identity;
        public final List<Field> fields;
        public final List<Table> nested;
        public final List<Field> illustrations;
        public final Set<String> units;
        Record(TableRecords.Record source) {
            this(source, true);
        }
        Record(TableRecords.Record source, boolean specialize) {
            group = source.heading;
            columnHeader = source.columnHeader;
            units = source.units;
            nested = source.nested;
            Field named = null;
            List<Field> values = new ArrayList<>();
            for (TableRecords.Field f : source.fields) {
                Field field = new Field(f.label, f.value, f.source);
                // Only promote explicit identity columns; never guess that a number is a record name.
                if (specialize && named == null && f.illustrationTarget.isBlank()
                    && Set
                        .of("item", "name", "monster", "npc", "object", "location", "method", "equipment",
                            "skill", "quest")
                        .contains(f.label.toLowerCase(Locale.ROOT)))
                    named = field;
                else
                    values.add(field);
            }
            identity = named;
            List<Field> captions = new ArrayList<>();
            if (named != null)
                for (TableRecords.Field field : source.fields) {
                    if (!field.source.unit.equals(named.source.unit) && !field.illustrationTarget.isBlank()
                        && named.value.links.stream().anyMatch(
                            link -> link.url.equals(field.illustrationTarget))) {
                        values.removeIf(value -> value.source.unit.equals(field.source.unit));
                        captions.add(new Field(field.label, field.value, field.source));
                    }
                }
            illustrations = List.copyOf(captions);
            fields = List.copyOf(values);
        }
    }
    public static final class ReferenceCard {
        public final String id, label, html;
        public final List<String> backlinks;
        public final Source source;
        ReferenceCard(Reference reference, String label) {
            id = reference.id;
            this.label = label;
            backlinks = reference.backlinks;
            source = reference.source;
            html = reference.content.html.trim()
                       .replaceFirst("(?is)^<li(?: [^>]*)?>", "")
                       .replaceFirst("(?is)</li>$", "");
        }
    }
    public final List<ReferenceCard> references;
    private final CompositionAudit audit;
    private final Map<String, Section> sections;
    private final Map<String, List<Record>> records;
    private final Map<String, List<Field>> highlights;
    public ReaderLayout(ArticleViewModel view) {
        audit = view.audit;
        Map<String, String> markers = new LinkedHashMap<>();
        for (Node node : view.document.nodes)
            for (Citation use : PresentationValidator.citations(node))
                markers.putIfAbsent(use.target, use.label);
        List<ReferenceCard> cards = new ArrayList<>();
        for (Reference reference : view.document.references)
            cards.add(new ReferenceCard(reference, markers.getOrDefault(reference.id, "Reference")));
        references = List.copyOf(cards);
        Map<String, Section> sectionMap = new HashMap<>();
        Map<String, List<Record>> recordMap = new HashMap<>();
        for (ArticleViewModel.Area area : view.areas)
            for (ArticleViewModel.Component c : area.components) {
                sectionMap.put(c.id, new Section(c));
                if (c.node.table != null)
                    adaptTable(c.node.table, recordMap);
            }
        Map<String, List<Field>> highlightMap = new HashMap<>();
        Set<String> variants = new LinkedHashSet<>();
        variants.add("");
        for (Node node : view.document.nodes)
            if (node.table != null)
                variants.add(node.table.variant);
        for (String variant : variants) {
            List<Field> values = new ArrayList<>();
            Set<String> labels = new HashSet<>();
            for (Fact fact : view.highlights(variant))
                if (labels.add(fact.label))
                    values.add(new Field(fact.label, fact.value, fact.source, Role.COMBAT));
            highlightMap.put(variant, List.copyOf(values));
        }
        highlights = Map.copyOf(highlightMap);
        sections = Map.copyOf(sectionMap);
        records = Map.copyOf(recordMap);
    }
    private void adaptTable(Table table, Map<String, List<Record>> target) {
        if (target.containsKey(table.source.unit))
            return;
        List<Record> rows = new ArrayList<>();
        for (TableRecords.Record record : validatedRecords(table, audit)) {
            Record adapted = new Record(record);
            if (!recordContract(record, adapted)) {
                audit.add("RECORD_CONTRACT", table.source.unit, CompositionAudit.Outcome.REPAIRED,
                    "Named record did not preserve its fields; using labeled source fields.");
                adapted = new Record(record, false);
            }
            rows.add(adapted);
            for (Table nested : record.nested) adaptTable(nested, target);
        }
        target.put(table.source.unit, List.copyOf(rows));
    }
    static List<TableRecords.Record> validatedRecords(Table table, CompositionAudit audit) {
        Map<String, Cell> cells = new HashMap<>();
        collectCells(table, cells);
        boolean valid = true;
        for (TableRecords.Record row : table.records)
            for (TableRecords.Field field : row.fields) {
                Cell source = cells.get(field.source.unit);
                if (source == null || !row.units.contains(field.source.unit)
                    || !source.label.equals(field.label) || !source.value.html.equals(field.value.html))
                    valid = false;
            }
        for (Cell cell : cells.values())
            if (!cell.header && !cell.value.text.isBlank() && cell.nested.isEmpty()) {
                boolean present =
                    table.records.stream()
                        .filter(row -> row.units.contains(cell.source.unit))
                        .flatMap(row -> row.fields.stream())
                        .anyMatch(f -> f.label.equals(cell.label) && f.value.html.equals(cell.value.html));
                if (!present)
                    valid = false;
            }
        if (valid)
            return table.records;
        audit.add("TABLE_RECORD_SOURCE", table.source.unit, CompositionAudit.Outcome.REPAIRED,
            "Table records did not match source cells; using conservative labeled source rows.");
        return TableRecords.safeOf(table);
    }
    private static void collectCells(Table table, Map<String, Cell> cells) {
        for (Row row : table.rows)
            for (Cell cell : row.cells) cells.put(cell.source.unit, cell);
    }
    static boolean recordContract(TableRecords.Record source, Record target) {
        List<Field> fields = new ArrayList<>(target.fields);
        fields.addAll(target.illustrations);
        if (target.identity != null)
            fields.add(target.identity);
        return fields.size() == source.fields.size() && target.units.equals(source.units)
            && source.fields.stream().allMatch(f
                -> fields.stream().anyMatch(v
                    -> v.source.unit.equals(f.source.unit) && v.label.equals(f.label)
                        && v.value.html.equals(f.value.html)))
            && target.nested.equals(source.nested);
    }
    public Section section(String id) {
        return sections.get(id);
    }
    public List<Field> highlights(String variant) {
        return highlights.getOrDefault(variant, highlights.getOrDefault("", List.of()));
    }
    public String tableTitle(Table table) {
        if (table.classes.contains("item-drops") && table.title.equals("Drops"))
            return "";
        return table.title.equals(table.source.sectionTitle) ? "" : table.title;
    }
    public List<Record> records(Table table) {
        return records.getOrDefault(table.source.unit, List.of());
    }
}
