package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel.*;
import java.util.*;

/** One source-owned section tree for dropdown navigation and the scrollable article reader. */
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
    public final List<ReadingSection> roots;
    public static final class ReadingSection {
        public final Destination destination;
        public final Source source;
        public final List<Component> components;
        public final List<ReadingSection> children;
        private ReadingSection(Destination destination, Source source, List<Component> components, List<ReadingSection> children) {
            this.destination=destination; this.source=source; this.components=List.copyOf(components); this.children=List.copyOf(children);
        }
        public String key() { return destination.area + "\0" + destination.anchor; }
        @Override public String toString() { return destination.label; }
    }
    private static final class Branch {
        final String area, anchor, label;
        Source source;
        final List<Component> components=new ArrayList<>();
        final List<Branch> children=new ArrayList<>();
        Branch(String area,String anchor,String label,Source source) {this.area=area;this.anchor=anchor;this.label=label;this.source=source;}
    }
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
        List<ReadingSection> tree=new ArrayList<>();
        for (Area mode : readingModes(modes)) tree.add(readingTree(mode));
        roots=List.copyOf(tree);
        List<Destination> flattened=new ArrayList<>();
        for (ReadingSection root : roots) flatten(root,flattened);
        sections=List.copyOf(flattened);
    }
    private List<Area> readingModes(List<Area> modes) {
        Set<String> chapters=new HashSet<>();
        for(Area mode:modes)if(mode.id.equals("walkthrough"))for(Component component:mode.components) {
            String top=topSection(component.source.section);if(!top.isEmpty())chapters.add(top);
        }
        Map<String,List<Component>> grouped=new LinkedHashMap<>();for(Area mode:modes)grouped.put(mode.id,new ArrayList<>());
        for(Area mode:modes)for(Component component:mode.components) {
            // A guide chapter owns its requirements, instructions and rewards together. Do not
            // clone that same chapter under three unrelated category headings.
            String owner=!mode.id.equals("references")&&chapters.contains(topSection(component.source.section))?"walkthrough":mode.id;
            grouped.get(owner).add(component);
        }
        List<Area> result=new ArrayList<>();
        for(Area mode:modes)if(mode.id.equals("overview")||!grouped.get(mode.id).isEmpty()||!mode.related.isEmpty())
            result.add(new Area(mode.id,mode.label,grouped.get(mode.id),mode.related));
        return result;
    }
    private String topSection(String anchor) {
        Set<String> seen=new HashSet<>();String top=anchor;
        while(!top.isBlank()&&seen.add(top)) {
            Section section=sourceSections.get(top);if(section==null||section.parent.isBlank())break;top=section.parent;
        }
        return top;
    }
    private ReadingSection readingTree(Area area) {
        Source article=new Source(view.document.title,view.document.url,view.document.revision,"",view.document.title,"article");
        Branch root=new Branch(area.id,"",area.label,article);
        Map<String,Branch> headings=new LinkedHashMap<>();
        if(area.id.equals("references")) {root.components.addAll(area.components);return freeze(root,0);}
        for (Component component : area.components) {
            Branch owner=root;
            Source origin=component.source;
            for(com.wikipane.semantic.Recipe recipe:view.document.recipes)
                if(recipe.materialsTable.source.unit.equals(component.source.unit))origin=recipe.requirementsTable.source;
            if (area.id.equals("overview") && component.type==ComponentType.REQUIREMENTS
                && (origin.section.isBlank() || origin.sectionTitle.equals("Details"))) {
                owner=headings.get("@requirements");
                if (owner==null) {
                    owner=new Branch(area.id,component.id,"Requirements",component.source);
                    headings.put("@requirements",owner);root.children.add(owner);
                }
            } else if (!(area.id.equals("overview") && component.type==ComponentType.FACTS && origin.sectionTitle.equals("Details"))
                && !origin.section.isBlank() && sourceSections.containsKey(origin.section)) owner=ensureHeading(origin.section,root,headings,new HashSet<>());
            owner.components.add(component);
        }
        return freeze(root,0);
    }
    private Branch ensureHeading(String anchor,Branch root,Map<String,Branch> headings,Set<String> visited) {
        if (headings.containsKey(anchor)) return headings.get(anchor);
        Section section=sourceSections.get(anchor);
        if (section==null || !visited.add(anchor)) return root;
        Branch parent=section.parent.isBlank()?root:ensureHeading(section.parent,root,headings,visited);
        if (parent==root && (section.title.equalsIgnoreCase(root.label)
            || root.area.equals("overview") && section.title.equalsIgnoreCase("Information"))) {
            if(!root.area.equals("overview"))root.source=new Source(view.document.title,view.document.url,view.document.revision,
                anchor,section.title,"section:"+anchor);
            headings.put(anchor,root);return root;
        }
        Source source=new Source(view.document.title,view.document.url,view.document.revision,anchor,section.title,"section:"+anchor);
        Branch branch=new Branch(root.area,anchor,section.title,source);parent.children.add(branch);headings.put(anchor,branch);return branch;
    }
    private ReadingSection freeze(Branch branch,int depth) {
        List<ReadingSection> children=new ArrayList<>();
        for(Branch child:branch.children)children.add(freeze(child,depth+1));
        return new ReadingSection(new Destination(branch.area,branch.anchor,branch.label,depth),branch.source,branch.components,children);
    }
    private static void flatten(ReadingSection node,List<Destination> into) {
        into.add(node.destination);for(ReadingSection child:node.children)flatten(child,into);
    }
    public ReadingSection readingSection(String area,String anchor) {
        for (ReadingSection root:roots) {ReadingSection found=find(root,area,anchor);if(found!=null)return found;}
        return null;
    }
    private ReadingSection find(ReadingSection node,String area,String anchor) {
        if(node.destination.area.equals(area) && (node.destination.anchor.equals(anchor)
            || !anchor.isEmpty() && node.components.stream().anyMatch(c -> c.id.equals(anchor)
                || c.source.section.equals(anchor) || PresentationValidator.hasAnchor(c.node,anchor)))) return node;
        for(ReadingSection child:node.children) {ReadingSection found=find(child,area,anchor);if(found!=null)return found;}
        return null;
    }
    public String sourceUrl(String area,String anchor) {
        ReadingSection node=readingSection(area,anchor);
        if(node!=null && node.destination.anchor.equals(anchor))return node.source.link();
        return view.document.url+(anchor.isEmpty()?"":"#"+anchor);
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
        String owner=ownerForAnchor(anchor);if(owner!=null)return owner;
        if (anchors(preferredArea).containsKey(anchor))
            return preferredArea;
        return areaForAnchor(anchor);
    }
    private String ownerForAnchor(String anchor) {
        if(anchor.isBlank()||roots==null)return null;
        for(ReadingSection root:roots) {
            ReadingSection owner=owner(root,anchor);if(owner!=null)return owner.destination.area;
        }
        return null;
    }
    private ReadingSection owner(ReadingSection section,String anchor) {
        if(section.destination.anchor.equals(anchor)||section.source.section.equals(anchor))return section;
        for(ReadingSection child:section.children){ReadingSection found=owner(child,anchor);if(found!=null)return found;}
        if(section.components.stream().anyMatch(c -> c.id.equals(anchor)||c.source.section.equals(anchor)||PresentationValidator.hasAnchor(c.node,anchor)))return section;
        return null;
    }
    public String areaForAnchor(String anchor) {
        String owner=ownerForAnchor(anchor);if(owner!=null)return owner;
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
