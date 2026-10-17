package com.wikipane.view;

import static com.wikipane.view.CompositionAudit.Outcome.*;

import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel.*;
import java.util.*;

/** One deterministic repair pass over composition. Never fetches, summarizes or invents source values. */
public final class PresentationValidator {
    public static final class Result {
        public final ArticleViewModel view;
        Result(ArticleViewModel view) {
            this.view = view;
        }
    }
    private PresentationValidator() {}
    public static boolean referenceNode(Node node) {
        return node.type == NodeType.REFERENCES
            || node.fallback instanceof WikiDocument.Text
            && !((WikiDocument.Text) node.fallback).references.isEmpty();
    }
    public static List<Citation> citations(Node node) {
        List<Citation> uses = new ArrayList<>();
        if (node.text != null)
            uses.addAll(node.text.citations);
        if (node.table != null)
            citations(node.table, uses);
        return uses;
    }
    private static void citations(Table table, List<Citation> uses) {
        for (Row row : table.rows)
            for (Cell cell : row.cells) {
                uses.addAll(cell.value.citations);
                for (Table nested : cell.nested) citations(nested, uses);
            }
    }
    public static boolean hasAnchor(Node node, String anchor) {
        if (node.fallback instanceof WikiDocument.Text)
            for (WikiDocument.ReferenceDefinition ref : ((WikiDocument.Text) node.fallback).references)
                if (ref.id.equals(anchor))
                    return true;
        return citations(node).stream().anyMatch(c -> !c.id.isBlank() && c.id.equals(anchor));
    }
    public static Result validate(SemanticDocument source, List<Area> proposed, List<Fact> highlights) {
        CompositionAudit audit = new CompositionAudit(source.contentUnits.size(), source.references.size());
        Map<String, Node> originals = new LinkedHashMap<>();
        source.nodes.forEach(n -> originals.put(n.id, n));
        Set<String> invalid = new HashSet<>();
        for (Area area : proposed)
            for (Component c : area.components) {
                Node node = originals.get(c.node.id);
                if (node == null) {
                    audit.add("FOREIGN_NODE", c.id, REPAIRED,
                        "Component outside the source document rejected; source coverage is restored "
                            + "independently.");
                    continue;
                }
                String problem = contract(c, node, source);
                if (problem != null) {
                    invalid.add(node.id);
                    audit.add("COMPONENT_CONTRACT", c.id, REPAIRED,
                        problem + " Original source uses generic reading.");
                }
            }
        LinkedHashMap<String, List<Component>> content = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        Map<String, List<Relationship>> related = new HashMap<>();
        for (Area area : proposed) {
            content.computeIfAbsent(area.id, k -> new ArrayList<>());
            labels.put(area.id, area.label);
            related.put(area.id, area.related);
        }
        Set<String> fallback = new HashSet<>(), ids = new HashSet<>();
        Map<String, Component> seen = new HashMap<>();
        for (Area area : proposed)
            for (Component c : area.components) {
                if (!originals.containsKey(c.node.id))
                    continue;
                Node original = originals.get(c.node.id);
                String destination = area.id;
                if (invalid.contains(original.id)) {
                    if (!fallback.add(original.id))
                        continue;
                    c = genericFallback(original);
                    destination = "other";
                }
                if (referenceNode(original)) {
                    if (!destination.equals("references") || c.type != ComponentType.REFERENCES)
                        audit.add("REFERENCES_IN_PROSE", c.id, REPAIRED,
                            "Reference definitions moved to References; citation links retained.");
                    c = new Component(c.id, c.title, ComponentType.REFERENCES, original.source, original,
                        List.of(), List.of(), original.units);
                    destination = "references";
                }
                Component earlier = seen.get(c.id);
                if (earlier != null && earlier.node == c.node && earlier.type == c.type
                    && earlier.units.equals(c.units) && earlier.facts.equals(c.facts)
                    && earlier.requirements.equals(c.requirements)) {
                    audit.add("DUPLICATE_COMPONENT_ID", c.id, REPAIRED,
                        "Identical duplicate component removed; source content retained once.");
                    continue;
                }
                seen.put(c.id, c);
                String id = c.id;
                int suffix = 1;
                while (!ids.add(id)) id = c.id + "~" + (suffix++);
                if (!id.equals(c.id)) {
                    audit.add("DUPLICATE_COMPONENT_ID", c.id, REPAIRED,
                        "Assigned a unique presentation ID; source identity unchanged.");
                    c = new Component(
                        id, c.title, c.type, c.source, c.node, c.facts, c.requirements, c.units);
                }
                content.computeIfAbsent(destination, k -> new ArrayList<>()).add(c);
            }
        Set<String> covered = new HashSet<>();
        content.values().forEach(cs -> cs.forEach(c -> covered.addAll(c.units)));
        for (Node node : source.nodes)
            if (!covered.containsAll(node.units)) {
                String destination = referenceNode(node) ? "references" : "other";
                Component recovered = referenceNode(node)
                    ? new Component(node.id, "References", ComponentType.REFERENCES, node.source, node,
                          List.of(), List.of(), node.units)
                    : genericFallback(node);
                content.computeIfAbsent(destination, k -> new ArrayList<>()).add(recovered);
                covered.addAll(node.units);
                audit.add("UNASSIGNED_SOURCE", node.id, REPAIRED,
                    "Unassigned source content restored through generic reading.");
            }
        Set<String> knownReferences = new HashSet<>();
        source.references.forEach(r -> knownReferences.add(r.id));
        for (Node node : source.nodes)
            for (Citation use : citations(node)) {
                okhttp3.HttpUrl destination = com.wikipane.api.WikiUrls.resolve(use.url);
                if (destination != null
                    && com.wikipane.api.WikiUrls.article(source.title)
                        .equals(destination.newBuilder().fragment(null).build().toString())
                    && !knownReferences.contains(use.target))
                    audit.add("REFERENCE_DESTINATION_MISSING", node.id, WARNING,
                        "Citation " + use.label
                            + " has no definition in this response; original Wiki link remains available.");
            }
        List<Area> areas = new ArrayList<>();
        Set<String> usedLabels = new HashSet<>();
        for (String id : content.keySet())
            if (!content.get(id).isEmpty()) {
                String label =
                    labels.getOrDefault(id, id.equals("references") ? "References" : "Other sections");
                if (!usedLabels.add(label)) {
                    String repaired = label + " (" + id + ")";
                    labels.put(id, repaired);
                    usedLabels.add(repaired);
                    audit.add("DUPLICATE_AREA_LABEL", id, REPAIRED,
                        "Ambiguous area label distinguished as " + repaired + ".");
                }
            }

        for (Map.Entry<String, List<Component>> entry : content.entrySet())
            if (!entry.getValue().isEmpty() || !related.getOrDefault(entry.getKey(), List.of()).isEmpty())
                areas.add(new Area(entry.getKey(),
                    labels.getOrDefault(entry.getKey(),
                        entry.getKey().equals("references") ? "References" : "Other sections"),
                    entry.getValue(), related.getOrDefault(entry.getKey(), List.of())));
        if (areas.isEmpty())
            areas.add(new Area("info", "Article", List.of(), List.of()));
        Set<String> accounted = new HashSet<>(covered);
        accounted.retainAll(source.contentUnits);
        audit.covered(accounted.size());
        if (!accounted.containsAll(source.contentUnits))
            audit.add("COVERAGE", source.title, ERROR, "Some semantic units could not be recovered.");
        List<Fact> verifiedHighlights = new ArrayList<>();
        for (Fact fact : highlights) {
            if (source.nodes.stream()
                    .flatMap(n -> n.facts.stream())
                    .anyMatch(f
                        -> f.source.unit.equals(fact.source.unit) && f.value.html.equals(fact.value.html)
                            && f.label.equals(fact.label)))
                verifiedHighlights.add(fact);
            else
                audit.add("HIGHLIGHT_SOURCE", fact.source.unit, REPAIRED,
                    "Non-source highlight rejected; original fields remain available.");
        }
        return new Result(new ArticleViewModel(source, areas, verifiedHighlights, audit));
    }
    private static Component genericFallback(Node node) {
        return new Component(node.id + ".fallback", node.source.sectionTitle, ComponentType.FALLBACK,
            node.source, node, List.of(), List.of(), node.units);
    }
    private static boolean sameSource(Source a, Source b) {
        return a.url.equals(b.url) && a.revision == b.revision && a.unit.equals(b.unit)
            && a.section.equals(b.section);
    }
    private static String contract(Component c, Node original, SemanticDocument source) {
        if (c.node != original || !sameSource(c.source, original.source) || !c.source.url.equals(source.url)
            || c.source.revision != source.revision || !original.units.containsAll(c.units))
            return "Source provenance or ownership mismatch.";
        if (c.type == ComponentType.FACTS || c.type == ComponentType.REQUIREMENTS) {
            if (original.table == null)
                return "Field-based component has no source field structure.";
            if (c.facts.stream().anyMatch(f -> !c.units.contains(f.source.unit)))
                return "Field ownership differs from component coverage.";
            if (c.facts.isEmpty() && c.requirements.isEmpty())
                return "Specialized component contains no fields.";
            for (Fact fact : c.facts)
                if (original.facts.stream().noneMatch(f
                        -> sameSource(f.source, fact.source) && f.label.equals(fact.label)
                            && f.value.html.equals(fact.value.html)))
                    return "Fact differs from its source.";
            for (Fact fact : original.facts)
                if (c.units.contains(fact.source.unit)
                    && c.facts.stream().noneMatch(f -> f.source.unit.equals(fact.source.unit)))
                    return "A source field is absent from the component.";
            for (RequirementSet set : original.requirements)
                if (c.units.contains(set.source.unit) && c.type == ComponentType.REQUIREMENTS
                    && c.requirements.stream().noneMatch(r -> r == set))
                    return "Requirement grouping or alternatives are missing.";
        }
        if (c.type == ComponentType.REFERENCES && !referenceNode(original))
            return "Reference component has no reference definitions.";
        if (original.type == NodeType.IMAGE && c.type != ComponentType.IMAGE
            && c.type != ComponentType.FALLBACK)
            return "Image was assigned to a text-only component.";
        if (c.type == ComponentType.TABLE && original.table == null)
            return "Table component has no table.";
        if (c.type == ComponentType.IMAGE && original.type != NodeType.IMAGE)
            return "Image component has no image.";
        if (c.type == ComponentType.TEXT && original.text == null)
            return "Reading component has no text.";
        return null;
    }
}
