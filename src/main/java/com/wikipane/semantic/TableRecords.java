package com.wikipane.semantic;

import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;

/** Complete table records, prepared off EDT. Geometry is resolved before native rendering. */
public final class TableRecords {
    public static final class Field {
        public final String label, illustrationTarget;
        public final com.wikipane.document.WikiDocument.Image illustration;
        public final RichText value;
        public final Source source;
        Field(String label, RichText value, Source source, String illustrationTarget, com.wikipane.document.WikiDocument.Image illustration) {
            this.illustration = illustration;
            this.illustrationTarget = illustrationTarget;
            this.label = label;
            this.value = value;
            this.source = source;
        }
    }
    public static final class Record {
        public final String heading;
        public final boolean columnHeader;
        public final List<Field> fields;
        public final List<Table> nested;
        public final Set<String> units;
        Record(String heading, List<Field> fields, List<Table> nested, Set<String> units) {
            this(heading, fields, nested, units, false);
        }
        Record(
            String heading, List<Field> fields, List<Table> nested, Set<String> units, boolean columnHeader) {
            this.columnHeader = columnHeader;
            this.heading = heading;
            this.fields = List.copyOf(fields);
            this.nested = List.copyOf(nested);
            this.units = Set.copyOf(units);
        }
    }
    private TableRecords() {}
    public static List<Record> of(Table table) {
        List<Record> result = new ArrayList<>();
        String group = "";
        Set<String> fieldLabels = new HashSet<>();
        for (Row sourceRow : table.rows) for (Cell cell : sourceRow.cells)
            if (!cell.header) fieldLabels.addAll(Arrays.asList(cell.label.split(" / ")));
        for (Row row : table.rows) {
            List<Cell> cells = new ArrayList<>();
            for (Cell cell : row.cells)
                if (!cell.value.text.isBlank() || !cell.nested.isEmpty())
                    cells.add(cell);
            if (cells.isEmpty())
                continue;
            if (cells.stream().allMatch(c -> c.header)) {
                for (Cell cell : cells)
                    if (cell.colspan > 1 || cells.size() == 1) {
                        group = cell.value.text;
                        break;
                    }
                // Retain standalone notes/terminal header content as a readable heading record.
                Set<String> units = new LinkedHashSet<>();
                cells.forEach(c -> units.add(c.source.unit));
                result.add(new Record(group.isEmpty()
                        ? String.join(" / ", cells.stream().map(c -> c.value.text).toArray(String[] ::new))
                        : group,
                    List.of(), List.of(), units,
                    cells.size() > 1 && cells.stream().allMatch(cell -> fieldLabels.contains(cell.value.text))));
                continue;
            }
            List<Field> fields = new ArrayList<>();
            List<Table> nested = new ArrayList<>();
            Set<String> units = new LinkedHashSet<>();
            List<Cell> all = new ArrayList<>(row.carried);
            all.addAll(cells);
            for (Cell cell : all) {
                units.add(cell.source.unit);
                nested.addAll(cell.nested);
                if (cell.header || cell.value.text.isBlank())
                    continue;
                String label = cell.label.isBlank() ? "" : cell.label;
                fields.add(new Field(label, cell.value, cell.source, cell.illustrationTarget, cell.illustration));
            }
            if (!fields.isEmpty() || !nested.isEmpty())
                result.add(new Record(group, fields, nested, units));
        }
        return List.copyOf(result);
    }
    /** Conservative row records for a failed specialized-table contract. Header text is retained too. */
    public static List<Record> safeOf(Table table) {
        List<Record> records = new ArrayList<>();
        for (Row row : table.rows) {
            List<Field> fields = new ArrayList<>();
            List<Table> nested = new ArrayList<>();
            Set<String> units = new LinkedHashSet<>();
            List<Cell> cells = new ArrayList<>(row.carried);
            cells.addAll(row.cells);
            for (Cell cell : cells) {
                if (!cell.value.text.isBlank())
                    fields.add(new Field(
                        cell.header ? "" : cell.label, cell.value, cell.source, cell.illustrationTarget, cell.illustration));
                nested.addAll(cell.nested);
                if (!cell.value.text.isBlank() || !cell.nested.isEmpty())
                    units.add(cell.source.unit);
            }
            if (!fields.isEmpty() || !nested.isEmpty())
                records.add(new Record("", fields, nested, units));
        }
        return List.copyOf(records);
    }
}
