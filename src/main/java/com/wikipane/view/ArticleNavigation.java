package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel.*;
import java.util.*;

/** Reading modes are buffered views; section destinations are explicit source-anchor jumps. */
public final class ArticleNavigation {
    public static final class Destination {
        public final String area, anchor, label;
        public final int depth;
        Destination(String area, String anchor, String label, int depth) {
            this.area = area;
            this.anchor = anchor;
            this.label = label;
            this.depth = depth;
        }
        @Override
        public String toString() {
            return label;
        }
    }
    public final List<Destination> sections;
    public final Destination overview, secondary;
    public final String subtitle;
    private final ArticleViewModel view;
    private final Area overviewArea;
    private final Set<String> overviewSources;
    private final Map<String, Section> sourceSections = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> aliases = new HashMap<>();
    public ArticleNavigation(ArticleViewModel view) {
        this.view = view;
        for (Section section : view.document.sections) sourceSections.put(section.anchor, section);
        overviewSources = new LinkedHashSet<>();
        overviewSources.add("info");
        if (view.document.type == PageType.QUEST)
            overviewSources.add("requirements");
        LinkedHashMap<String, Component> components = new LinkedHashMap<>();
        List<Relationship> links = new ArrayList<>();
        // Requirements lead a quest overview; the original article description remains available beneath
        // them.
        List<String> order =
            view.document.type == PageType.QUEST ? List.of("requirements", "info") : List.of("info");
        for (String id : order)
            for (Area area : view.areas)
                if (area.id.equals(id)) {
                    for (Component component : area.components)
                        components.putIfAbsent(component.id, component);
                    links.addAll(area.related);
                }
        if (components.isEmpty())
            for (Area area : view.areas)
                if (!area.components.isEmpty()) {
                    overviewSources.add(area.id);
                    for (Component component : area.components)
                        components.putIfAbsent(component.id, component);
                    links.addAll(area.related);
                    break;
                }
        overviewArea = new Area("overview", "Overview", new ArrayList<>(components.values()), links);
        overview = new Destination("overview", "", "Overview", 0);
        String[] preferred = view.document.type == PageType.QUEST ? new String[] {"walkthrough", "rewards"}
            : view.document.type == PageType.MONSTER              ? new String[] {"combat", "drops"}
            : view.document.type == PageType.ITEM
            ? new String[] {"stats", "sources", "methods"}
            : new String[] {"methods", "walkthrough", "combat", "requirements"};
        Destination other = null;
        for (String id : preferred)
            for (Area area : view.areas)
                if (other == null && area.id.equals(id) && !overviewSources.contains(id))
                    other = new Destination(id, "", primaryLabel(area), 0);
        if (other == null)
            for (Area area : view.areas)
                if (!overviewSources.contains(area.id) && !area.id.equals("references")
                    && !area.id.equals("other")) {
                    other = new Destination(area.id, "", primaryLabel(area), 0);
                    break;
                }
        secondary = other;
        List<Destination> entries = new ArrayList<>();
        entries.add(overview);
        Set<String> anchors = new HashSet<>();
        for (Area area : view.areas) {
            String target = modeFor(area.id);
            if (!area.id.equals("info"))
                entries.add(new Destination(target,
                    overviewSources.contains(area.id) && !area.components.isEmpty()
                        ? (area.components.get(0).source.section.isBlank()
                                  ? area.components.get(0).id
                                  : area.components.get(0).source.section)
                        : "",
                    area.label, 0));
            for (Section section : view.document.sections) {
                if (area.id.equals("references")) continue;
                if (section.anchor.isBlank() || !anchors.add(area.id + "\0" + section.anchor))
                    continue;
                if (area.components.stream().noneMatch(c -> descendsFrom(c.source.section, section.anchor)))
                    continue;
                String label = section.title;
                if (label.equals(area.label) || label.equals("Information"))
                    continue;
                entries.add(new Destination(
                    target, section.anchor, label, Math.max(1, Math.min(3, section.level - 1))));
            }
        }
        LinkedHashMap<String, Destination> unique = new LinkedHashMap<>();
        for (Destination destination : entries) {
            String key = destination.area + "\0" + destination.anchor;
            Destination old = unique.get(key);
            if (old == null || destination.depth < old.depth)
                unique.put(key, destination);
        }
        sections = List.copyOf(unique.values());
        List<Area> modes = new ArrayList<>();
        modes.add(overviewArea);
        for (Area area : view.areas)
            if (!overviewSources.contains(area.id))
                modes.add(area);
        for (Area mode : modes) {
            Map<String, String> destinations = new LinkedHashMap<>();
            for (Component component : mode.components) {
                if (mode.id.equals("references")) continue;
                String own = component.source.section, ancestor = own;
                Set<String> visited = new HashSet<>();
                while (!ancestor.isBlank() && visited.add(ancestor)) {
                    destinations.putIfAbsent(ancestor, own);
                    Section heading = sourceSections.get(ancestor);
                    ancestor = heading == null ? "" : heading.parent;
                }
            }
            aliases.put(mode.id, Map.copyOf(destinations));
        }

        List<String> metadata = new ArrayList<>();
        String type = view.document.type.name().toLowerCase(Locale.ROOT);
        metadata.add(view.document.type == PageType.GENERAL
                ? "OSRS Wiki"
                : Character.toUpperCase(type.charAt(0)) + type.substring(1));
        if (view.document.type == PageType.QUEST)
            for (String key : List.of("difficulty", "length")) {
                view.document.nodes.stream()
                    .flatMap(n -> n.facts.stream())
                    .filter(
                        f -> f.key.equalsIgnoreCase(key) || f.label.toLowerCase(Locale.ROOT).contains(key))
                    .filter(f -> f.value.text.length() < 35)
                    .findFirst()
                    .ifPresent(f -> metadata.add(f.value.text));
            }
        subtitle = String.join(" · ", metadata);
    }
    private String primaryLabel(Area area) {
        switch (area.id) {
            case "combat":
                return "Combat";
            case "methods":
                return view.document.type == PageType.SKILL ? "Training" : "Methods";
            default:
                return area.label;
        }
    }
    public String modeFor(String sourceArea) {
        return overviewSources.contains(sourceArea) ? "overview" : sourceArea;
    }
    public Area area(String id) {
        return id.equals("overview") ? overviewArea : view.area(id);
    }
    private boolean descendsFrom(String anchor, String ancestor) {
        Set<String> visited = new HashSet<>();
        while (!anchor.isBlank() && visited.add(anchor)) {
            if (anchor.equals(ancestor))
                return true;
            Section heading = sourceSections.get(anchor);
            anchor = heading == null ? "" : heading.parent;
        }
        return false;
    }
    public Map<String, String> anchors(String area) {
        return aliases.getOrDefault(area, Map.of());
    }
    public String areaForAnchor(String anchor, String preferredArea) {
        if (anchors(preferredArea).containsKey(anchor))
            return preferredArea;
        return areaForAnchor(anchor);
    }
    public String areaForAnchor(String anchor) {
        if (anchor.isBlank())
            return "overview";
        for (Area area : view.areas)
            if (area.components.stream().anyMatch(c -> c.id.equals(anchor)))
                return modeFor(area.id);
        for (Area area : view.areas)
            if (anchors(modeFor(area.id)).containsKey(anchor))
                return modeFor(area.id);
        return modeFor(view.sourceArea(anchor).id);
    }
    public Destination section(String anchor) {
        for (Destination destination : sections)
            if (!anchor.isBlank() && destination.anchor.equals(anchor))
                return destination;
        if (view.document.references.stream().anyMatch(r -> r.id.equals(anchor)))
            return new Destination("references", anchor, "References", 1);
        for (Node node : view.document.nodes)
            if (PresentationValidator.hasAnchor(node, anchor)) {
                String label = node.source.sectionTitle;
                return new Destination(
                    areaForAnchor(anchor), anchor, label.isBlank() ? "Cited passage" : label, 1);
            }
        return new Destination(areaForAnchor(anchor), anchor, anchor, 1);
    }
}
