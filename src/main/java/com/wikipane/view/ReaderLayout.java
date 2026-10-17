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
        Field(String label, RichText value, Source source) {
            this.label = label;
            this.value = value;
            this.source = source;
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
        public final List<String> lines;
        public final Source source;
        ConditionGroup(String title, Requirement condition, Source source) {
            this.title = title;
            this.condition = condition;
            List<String> content = new ArrayList<>();
            conditionLines(condition, content);
            lines = List.copyOf(content);
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
        public final String title, breadcrumb;
        public final List<FactGroup> facts;
        public final List<ConditionGroup> requirements;
        Section(ArticleViewModel.Component c) {
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
                        .add(new Field(f.label, f.value, f.source));
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
            group = source.heading;
            columnHeader = source.columnHeader;
            units = source.units;
            nested = source.nested;
            Field named = null;
            List<Field> values = new ArrayList<>();
            for (TableRecords.Field f : source.fields) {
                Field field = new Field(f.label, f.value, f.source);
                // Only promote explicit identity columns; never guess that a number is a record name.
                if (named == null && f.illustrationTarget.isBlank()
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
    private final Map<String, Section> sections;
    private final Map<String, List<Record>> records;
    private final Map<String, List<Field>> highlights;
    public ReaderLayout(ArticleViewModel view) {
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
                    values.add(new Field(fact.label, fact.value, fact.source));
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
        for (TableRecords.Record record : table.records) {
            rows.add(new Record(record));
            for (Table nested : record.nested) adaptTable(nested, target);
        }
        target.put(table.source.unit, List.copyOf(rows));
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
