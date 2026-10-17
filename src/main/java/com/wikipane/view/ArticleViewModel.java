package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;

/** Page-type presentation decisions over complete semantic data. No Swing, fetching, or summarization. */
public final class ArticleViewModel {
    public enum ComponentType { TEXT, FACTS, REQUIREMENTS, TABLE, IMAGE, REFERENCES, FALLBACK }
    public static final class Component {
        public final String id, title;
        public final ComponentType type;
        public final Source source;
        public final Node node;
        public final List<Fact> facts;
        public final List<RequirementSet> requirements;
        public final Set<String> units;
        Component(String id, String title, ComponentType type, Source source, Node node, List<Fact> facts,
            List<RequirementSet> requirements, Set<String> units) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.source = source;
            this.node = node;
            this.facts = List.copyOf(facts);
            this.requirements = List.copyOf(requirements);
            this.units = Set.copyOf(units);
        }
        public String text() {
            StringBuilder out = new StringBuilder(title);
            if (type == ComponentType.FACTS || type == ComponentType.REQUIREMENTS)
                for (Fact f : facts) out.append(' ').append(f.label).append(' ').append(f.value.text);
            else if (node.text != null)
                out.append(' ').append(node.text.text);
            else if (node.table != null)
                text(node.table, out);
            return out.toString();
        }
        private void text(Table table, StringBuilder out) {
            for (Row row : table.rows)
                for (Cell cell : row.cells) {
                    out.append(' ').append(cell.value.text);
                    for (Table nested : cell.nested) text(nested, out);
                }
        }
    }
    public static final class Area {
        public final String id, label;
        public final List<Component> components;
        public final List<Relationship> related;
        Area(String id, String label, List<Component> components, List<Relationship> related) {
            this.id = id;
            this.label = label;
            this.components = List.copyOf(components);
            this.related = List.copyOf(related);
        }
    }
    public final SemanticDocument document;
    public final List<Area> areas;
    public final List<Fact> highlights;
    public final Set<String> coveredUnits, missingUnits;
    public final CompositionAudit audit;
    ArticleViewModel(
        SemanticDocument document, List<Area> areas, List<Fact> highlights, CompositionAudit audit) {
        this.audit = audit;
        this.document = document;
        this.areas = List.copyOf(areas);
        this.highlights = List.copyOf(highlights);
        Set<String> covered = new LinkedHashSet<>();
        for (Area area : areas)
            for (Component component : area.components) covered.addAll(component.units);
        coveredUnits = Set.copyOf(covered);
        Set<String> missing = new LinkedHashSet<>(document.contentUnits);
        missing.removeAll(covered);
        missingUnits = Set.copyOf(missing);
    }
    public static ArticleViewModel create(SemanticDocument document) {
        LinkedHashMap<String, List<Component>> content = new LinkedHashMap<>();
        Map<String, String> labels = labels(document.type);
        labels.keySet().forEach(id -> content.put(id, new ArrayList<>()));
        for (Node node : document.nodes) {
            if (node.type == NodeType.INFOBOX && node.table != null
                && node.table.rows.stream().noneMatch(
                    r -> r.cells.stream().anyMatch(c -> !c.nested.isEmpty()))) {
                Map<String, List<Fact>> split = new LinkedHashMap<>();
                for (Fact fact : node.facts) {
                    String area = area(factRole(fact, node.role), document.type);
                    split.computeIfAbsent(area, k -> new ArrayList<>()).add(fact);
                }
                if (split.isEmpty())
                    add(content, "other", generic(node));
                else {
                    Set<String> assigned = new LinkedHashSet<>();
                    for (List<Fact> facts : split.values())
                        for (Fact fact : facts) assigned.add(fact.source.unit);
                    String owner = split.containsKey("info") ? "info" : split.keySet().iterator().next();
                    for (Map.Entry<String, List<Fact>> entry : split.entrySet()) {
                        Set<String> units = new LinkedHashSet<>();
                        for (Fact fact : entry.getValue()) units.add(fact.source.unit);
                        if (entry.getKey().equals(owner)) {
                            Set<String> structural = new LinkedHashSet<>(node.units);
                            structural.removeAll(assigned);
                            units.addAll(structural);
                        }
                        List<RequirementSet> requirements = new ArrayList<>();
                        for (RequirementSet set : node.requirements)
                            if (units.contains(set.source.unit))
                                requirements.add(set);
                        entry.getValue().sort(
                            java.util.Comparator.comparingInt(f -> priority(f, document.type)));
                        ComponentType type = entry.getKey().equals("requirements")
                            ? ComponentType.REQUIREMENTS
                            : ComponentType.FACTS;
                        add(content, entry.getKey(),
                            new Component(node.id + "." + entry.getKey(), node.table.kind, type, node.source,
                                node, entry.getValue(), requirements, units));
                    }
                }
            } else
                add(content, node.type == NodeType.REFERENCES ? "references" : area(node.role, document.type), generic(node));
        }
        // Unknown components stay exposed; category recognition never controls content retention.
        Set<String> retained = new HashSet<>();
        for (List<Component> list : content.values())
            for (Component c : list) retained.addAll(c.units);
        for (Node node : document.nodes)
            if (!retained.containsAll(node.units))
                add(content, "other", generic(node));
        List<Area> areas = new ArrayList<>();
        for (Map.Entry<String, List<Component>> entry : content.entrySet()) {
            if (entry.getKey().equals("drops"))
                entry.getValue().sort(
                    java.util.Comparator.comparingInt(c -> c.node.role == Role.DROPS ? 0 : 1));
            List<Relationship> related = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Relationship link : document.relationships)
                if (link.guide
                    && (link.role == Role.STRATEGY || link.role == Role.WALKTHROUGH
                        || link.role == Role.METHODS)
                    && area(link.role, document.type).equals(entry.getKey())
                    && !link.target.url.startsWith(document.url + "#")
                    && !link.target.url.equals(document.url) && seen.add(link.target.url))
                    related.add(link);
            if (!entry.getValue().isEmpty() || !related.isEmpty()
                || (entry.getKey().equals("info") && document.nodes.isEmpty()))
                areas.add(new Area(entry.getKey(), labels.getOrDefault(entry.getKey(), "Other sections"),
                    entry.getValue(), related));
        }
        List<Fact> highlights = new ArrayList<>();
        for (Node node : document.nodes)
            for (Fact fact : node.facts)
                if (highlight(fact) && fact.variant.equals(firstVariant(node, document)))
                    highlights.add(fact);
        return PresentationValidator.validate(document, areas, highlights).view;
    }
    public List<Fact> highlights(String selectedVariant) {
        List<Fact> result = new ArrayList<>();
        for (Node node : document.nodes) {
            String variant = firstVariant(node, document);
            if (node.table != null
                && document.nodes.stream().anyMatch(candidate
                    -> candidate.table != null && candidate.table.kind.equals(node.table.kind)
                        && candidate.table.variant.equals(selectedVariant)))
                variant = selectedVariant;
            for (Fact fact : node.facts)
                if (highlight(fact) && fact.variant.equals(variant))
                    result.add(fact);
        }
        return List.copyOf(result);
    }
    private static int priority(Fact fact, PageType type) {
        String label = fact.label.toLowerCase(Locale.ROOT);
        List<String> order = type == PageType.ITEM
            ? List.of("members", "quest item", "tradeable", "equipable", "stackable", "weight", "examine")
            : type == PageType.MONSTER
            ? List.of("combat level", "hitpoints", "slayer xp", "attack style", "max hit", "aggressive",
                  "poisonous", "examine")
            : List.of("requirements", "items required", "items recommended", "start point");
        int index = order.indexOf(label);
        return index < 0 ? 100 : index;
    }
    private static String firstVariant(Node node, SemanticDocument document) {
        if (node.table == null)
            return "";
        for (Node candidate : document.nodes)
            if (candidate.table != null && candidate.table.kind.equals(node.table.kind))
                return candidate.table.variant;
        return "";
    }
    private static boolean highlight(Fact fact) {
        String key = fact.key.toLowerCase(Locale.ROOT), label = fact.label.toLowerCase(Locale.ROOT);
        return Set.of("combat", "hitpoints", "slayxp").contains(key)
            || Set.of("combat level", "hitpoints", "slayer xp").contains(label);
    }
    static Component generic(Node node) {
        return new Component(node.id, node.source.sectionTitle,
            node.type == NodeType.REFERENCES     ? ComponentType.REFERENCES
                : node.table != null             ? ComponentType.TABLE
                : node.type == NodeType.IMAGE    ? ComponentType.IMAGE
                : node.type == NodeType.FALLBACK ? ComponentType.FALLBACK
                                                 : ComponentType.TEXT,
            node.source, node, List.of(), node.requirements, node.units);
    }
    private static void add(Map<String, List<Component>> content, String area, Component c) {
        content.computeIfAbsent(area, k -> new ArrayList<>()).add(c);
    }
    private static Role factRole(Fact fact, Role inherited) {
        String key = fact.key.toLowerCase(Locale.ROOT), label = fact.label.toLowerCase(Locale.ROOT);
        if (!fact.value.text.isBlank()
            && (label.matches(".*(require|items needed|items recommended|equipment needed|prerequisite).*")))
            return Role.REQUIREMENTS;
        if (label.matches(".*(start point|location|habitat).*"))
            return Role.LOCATIONS;
        if (label.matches(".*(reward|experience reward).*"))
            return Role.REWARDS;
        if (key.matches("(a|d)(stab|slash|crush|magic|range)")
            || Set
                .of("combat", "hitpoints", "att", "str", "def", "mage", "range", "slayxp", "maxhit",
                    "attstyle", "attackspeed", "strbonus", "rstr", "mdmg", "prayer")
                .contains(key)
            || label.matches(
                ".*(combat level|hitpoints|slayer xp|attack style|max hit|attack speed|weakness).*"))
            return Role.COMBAT;
        return inherited == Role.OTHER || inherited == Role.REQUIREMENTS ? Role.INFORMATION : inherited;
    }
    private static String area(Role role, PageType type) {
        if (type == PageType.MONSTER) {
            if (role == Role.LOCATIONS || role == Role.REQUIREMENTS)
                return "access";
            if (role == Role.METHODS || role == Role.REWARDS)
                return "drops";
        }
        if (type == PageType.ITEM && (role == Role.DROPS || role == Role.LOCATIONS))
            return "sources";
        if (type == PageType.QUEST) {
            if (role == Role.LOCATIONS)
                return "info";
            if (role == Role.METHODS)
                return "walkthrough";
        }
        if (type == PageType.GENERAL && (role == Role.OTHER || role == Role.RELATIONSHIPS))
            return "info";
        switch (role) {
            case REFERENCES:
                return "references";
            case COMBAT:
            case STRATEGY:
                return type == PageType.ITEM ? "stats" : "combat";
            case DROPS:
                return "drops";
            case REQUIREMENTS:
                return "requirements";
            case WALKTHROUGH:
                return "walkthrough";
            case REWARDS:
                return "rewards";
            case LOCATIONS:
                return "locations";
            case METHODS:
                return "methods";
            case SOURCES:
                return "sources";
            case OTHER:
            case RELATIONSHIPS:
                return "other";
            default:
                return "info";
        }
    }
    private static Map<String, String> labels(PageType type) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        switch (type) {
            case MONSTER:
                names.put("info", "Information");
                names.put("combat", "Combat & strategy");
                names.put("drops", "Drops");
                names.put("access", "Access & locations");
                break;
            case ITEM:
                names.put("info", "Information");
                names.put("stats", "Stats");
                names.put("sources", "Sources");
                names.put("methods", "Making & uses");
                break;
            case QUEST:
                names.put("requirements", "Requirements");
                names.put("walkthrough", "Walkthrough");
                names.put("rewards", "Rewards");
                names.put("info", "Information");
                break;
            case SKILL:
                names.put("info", "Information");
                names.put("methods", "Training & methods");
                names.put("requirements", "Requirements");
                break;
            case GUIDE:
                names.put("info", "Guide");
                names.put("requirements", "Requirements");
                names.put("combat", "Combat & strategy");
                names.put("methods", "Methods");
                names.put("walkthrough", "Instructions");
                break;
            default:
                names.put("info", "Article");
                break;
        }
        names.putIfAbsent("locations", "Locations");
        names.putIfAbsent("requirements", "Requirements");
        names.putIfAbsent("rewards", "Rewards");
        names.putIfAbsent("combat", "Combat & strategy");
        names.putIfAbsent("sources", "Sources");
        names.putIfAbsent("methods", "Methods");
        names.putIfAbsent("walkthrough", "Instructions");
        names.put("references", "References");
        names.put("other", "Other sections");
        return names;
    }
    public Area area(String id) {
        return areas.stream().filter(a -> a.id.equals(id)).findFirst().orElse(areas.get(0));
    }
    public Area sourceArea(String anchor) {
        if (anchor.isEmpty())
            return area("info");
        for (Area area : areas)
            for (Component component : area.components)
                if (PresentationValidator.hasAnchor(component.node, anchor)
                    || component.source.section.equals(anchor)
                    || (component.node.fallback instanceof com.wikipane.document.WikiDocument.Table
                        && ((com.wikipane.document.WikiDocument.Table) component.node.fallback)
                            .anchor.equals(anchor)))
                    return area;
        return areas.get(0);
    }
    public Component componentAt(String anchor) {
        for (Area area : areas)
            for (Component c : area.components)
                if (c.source.section.equals(anchor))
                    return c;
        return null;
    }
}
