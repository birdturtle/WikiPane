package com.wikipane.semantic;

import com.wikipane.document.WikiDocument;
import java.math.BigDecimal;
import java.util.*;

/** Complete, immutable, UI-independent information and its provenance. No account or Swing state. */
public final class SemanticDocument {
    public enum PageType { ITEM, MONSTER, QUEST, SKILL, GUIDE, GENERAL }
    public enum Role {
        INFORMATION,
        COMBAT,
        STRATEGY,
        DROPS,
        REQUIREMENTS,
        WALKTHROUGH,
        REWARDS,
        LOCATIONS,
        METHODS,
        SOURCES,
        RELATIONSHIPS,
        REFERENCES,
        OTHER
    }
    public enum NodeType { TEXT, LIST, INSTRUCTIONS, REFERENCES, TABLE, INFOBOX, IMAGE, FALLBACK }
    public static final class Source {
        public final String article, url, section, sectionTitle, unit;
        public final long revision;
        public Source(
            String article, String url, long revision, String section, String sectionTitle, String unit) {
            this.article = article;
            this.url = url;
            this.revision = revision;
            this.section = section;
            this.sectionTitle = sectionTitle;
            this.unit = unit;
        }
        public String link() {
            return url + (section.isEmpty() ? "" : "#" + section);
        }
    }
    public static final class RichText {
        public final String html, text;
        public final List<Link> links;
        public final List<Citation> citations;
        public RichText(String html, String text, List<Link> links) {
            this.html = html;
            this.text = text;
            this.links = List.copyOf(links);
            List<Citation> uses = new ArrayList<>();
            com.wikipane.html.Element root = com.wikipane.html.WikiHtml.parseBodyFragment(html).body();
            for (com.wikipane.html.Element a : root.select("a[href]")) {
                okhttp3.HttpUrl target = com.wikipane.api.WikiUrls.resolve(a.attr("href"));
                if (target != null && com.wikipane.api.WikiUrls.wiki(target) && target.fragment() != null
                    && target.fragment().startsWith("cite_note")) {
                    com.wikipane.html.Element marker = a.closest("sup[id]");
                    uses.add(new Citation(
                        marker == null ? "" : marker.id(), target.fragment(), a.text(), target.toString()));
                }
            }
            citations = List.copyOf(uses);
        }
    }
    public static final class Citation {
        public final String id, target, label, url;
        Citation(String id, String target, String label, String url) {
            this.id = id;
            this.target = target;
            this.label = label;
            this.url = url;
        }
    }
    public static final class Reference {
        public final String id;
        public final RichText content;
        public final List<String> backlinks;
        public final Source source;
        Reference(String id, RichText content, List<String> backlinks, Source source) {
            this.id = id;
            this.content = content;
            this.backlinks = List.copyOf(backlinks);
            this.source = source;
        }
    }
    public static final class Link {
        public final String label, url;
        public final boolean internal;
        public Link(String label, String url, boolean internal) {
            this.label = label;
            this.url = url;
            this.internal = internal;
        }
    }
    public static final class Relationship {
        public final Role role;
        public final Link target;
        public final Source source;
        public final boolean guide;
        public Relationship(Role role, Link target, Source source, boolean guide) {
            this.role = role;
            this.target = target;
            this.source = source;
            this.guide = guide;
        }
    }
    public static final class Fact {
        public final String key, label, group, variant;
        public final RichText value;
        public final Source source;
        public final BigDecimal number;
        public Fact(String key, String label, String group, String variant, RichText value, Source source) {
            this.key = key;
            this.label = label;
            this.group = group;
            this.variant = variant;
            this.value = value;
            this.source = source;
            BigDecimal parsed = null;
            if (value.text.matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
                try {
                    parsed = new BigDecimal(value.text);
                } catch (NumberFormatException ignored) {
                }
            number = parsed;
        }
    }
    public static final class Requirement {
        public enum Kind { ALL, ANY, SKILL, QUEST, QUEST_STAGE, ITEM, QUEST_POINTS, OTHER }
        public enum Boost { ALLOWED, NOT_ALLOWED, UNSPECIFIED }
        public final Kind kind;
        public final boolean verifiable;
        public final String name, url;
        public final Integer level, quantity;
        public final Boost boost;
        public final RichText original;
        public final List<Requirement> children;
        public Requirement(Kind kind, String name, String url, Integer level, Integer quantity, Boost boost,
            RichText original, List<Requirement> children) {
            this(kind, name, url, level, quantity, boost, original, children, true);
        }
        public Requirement(Kind kind, String name, String url, Integer level, Integer quantity, Boost boost,
            RichText original, List<Requirement> children, boolean verifiable) {
            this.verifiable = verifiable;
            this.kind = kind;
            this.name = name;
            this.url = url;
            this.level = level;
            this.quantity = quantity;
            this.boost = boost;
            this.original = original;
            this.children = List.copyOf(children);
        }
    }
    public static final class RequirementGroup {
        public final String label;
        public final Requirement expression;
        public RequirementGroup(String label, Requirement expression) {
            this.label = label;
            this.expression = expression;
        }
    }
    public static final class RequirementSet {
        public final List<RequirementGroup> groups;
        public final Source source;
        public RequirementSet(List<RequirementGroup> groups, Source source) {
            this.groups = List.copyOf(groups);
            this.source = source;
        }
    }
    public static final class Instruction {
        public final int ordinal;
        public final RichText content;
        public final Source source;
        public final List<Instruction> children;
        public Instruction(int ordinal, RichText content, Source source, List<Instruction> children) {
            this.ordinal = ordinal;
            this.content = content;
            this.source = source;
            this.children = List.copyOf(children);
        }
    }
    public static final class Location {
        public final boolean verifiedMap;
        public final String mapId;
        public final String x, y, plane;
        public final Source source;
        public Location(String x, String y, String plane, Source source) {
            this(x,y,plane,source,false);
        }
        public Location(String x, String y, String plane, Source source, boolean verifiedMap) {
            this(x,y,plane,source,verifiedMap,"-1");
        }
        public Location(String x, String y, String plane, Source source, boolean verifiedMap, String mapId) {
            this.mapId = mapId;
            this.verifiedMap = verifiedMap;
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.source = source;
        }
    }
    public static final class Section {
        public final String anchor, title, parent;
        public final int level;
        public final Role role;
        public final List<Node> nodes;
        public Section(String anchor, String title, String parent, int level, Role role, List<Node> nodes) {
            this.anchor = anchor;
            this.title = title;
            this.parent = parent;
            this.level = level;
            this.role = role;
            this.nodes = List.copyOf(nodes);
        }
    }
    public static final class Cell {
        public final RichText value;
        public final String key, label, illustrationTarget;
        public final WikiDocument.Image illustration;
        public final boolean header;
        public final int column, colspan, rowspan;
        public final Source source;
        public final List<Table> nested;
        public final List<RequirementGroup> requirements;
        public Cell(RichText value, String key, String label, boolean header, int column, int colspan,
            int rowspan, Source source, List<Table> nested, List<RequirementGroup> requirements) {
            this(value, key, label, header, column, colspan, rowspan, source, nested, requirements, "");
        }
        public Cell(RichText value, String key, String label, boolean header, int column, int colspan,
            int rowspan, Source source, List<Table> nested, List<RequirementGroup> requirements,
            String illustrationTarget) {
            this(value,key,label,header,column,colspan,rowspan,source,nested,requirements,illustrationTarget,null);
        }
        public Cell(RichText value, String key, String label, boolean header, int column, int colspan,
            int rowspan, Source source, List<Table> nested, List<RequirementGroup> requirements,
            String illustrationTarget, WikiDocument.Image illustration) {
            this.illustration = illustration;
            this.illustrationTarget = illustrationTarget;
            this.value = value;
            this.key = key;
            this.label = label;
            this.header = header;
            this.column = column;
            this.colspan = colspan;
            this.rowspan = rowspan;
            this.source = source;
            this.nested = List.copyOf(nested);
            this.requirements = List.copyOf(requirements);
        }
    }
    public static final class Row {
        public final List<Cell> cells, carried;
        public Row(List<Cell> cells, List<Cell> carried) {
            this.cells = List.copyOf(cells);
            this.carried = List.copyOf(carried);
        }
    }
    public static final class Table {
        public final String title, variant, kind;
        public final String recipeGroup, recipeVariant, recipeMethod;
        public final Role role;
        public final Source source;
        public final List<Row> rows;
        public final List<com.wikipane.semantic.TableRecords.Record> records;
        public final Set<String> classes;
        public final List<WikiDocument.Image> images;
        public Table(String title, String variant, String kind, Role role, Source source, List<Row> rows,
            Set<String> classes, List<WikiDocument.Image> images) {
            this(title,variant,kind,role,source,rows,classes,images,"","","");
        }
        public Table(String title, String variant, String kind, Role role, Source source, List<Row> rows,
            Set<String> classes, List<WikiDocument.Image> images, String recipeGroup, String recipeVariant, String recipeMethod) {
            this.recipeGroup=recipeGroup; this.recipeVariant=recipeVariant; this.recipeMethod=recipeMethod;
            this.title = title;
            this.variant = variant;
            this.kind = kind;
            this.role = role;
            this.source = source;
            this.rows = List.copyOf(rows);
            this.classes = Set.copyOf(classes);
            this.images = List.copyOf(images);
            this.records = com.wikipane.semantic.TableRecords.of(this);
        }
    }
    public static final class Node {
        public final String id;
        public final NodeType type;
        public final boolean navigation;
        public final Role role;
        public final Source source;
        public final WikiDocument.Block fallback;
        public final RichText text;
        public final Table table;
        public final List<Fact> facts;
        public final List<RequirementSet> requirements;
        public final List<Relationship> relationships;
        public final List<Location> locations;
        public final List<Instruction> instructions;
        public final Set<String> units;
        public Node(String id, NodeType type, Role role, Source source, WikiDocument.Block fallback,
            RichText text, Table table, List<Fact> facts, List<RequirementSet> requirements,
            List<Relationship> relationships, List<Location> locations, List<Instruction> instructions,
            Set<String> units) {
            this.id = id;
            this.type = type;
            this.navigation = fallback instanceof WikiDocument.Text && ((WikiDocument.Text) fallback).navigation;
            this.role = role;
            this.source = source;
            this.fallback = fallback;
            this.text = text;
            this.table = table;
            this.facts = List.copyOf(facts);
            this.requirements = List.copyOf(requirements);
            this.relationships = List.copyOf(relationships);
            this.locations = List.copyOf(locations);
            this.instructions = List.copyOf(instructions);
            this.units = Set.copyOf(units);
        }
    }
    public final WikiMaps maps;
    public final PageType type;
    public final String title, url;
    public final long revision;
    public final List<Section> sections;
    public final List<Node> nodes;
    public final List<Relationship> relationships;
    public final List<Reference> references;
    public final List<Recipe> recipes;
    public final Set<String> contentUnits;
    public SemanticDocument(PageType type, String title, String url, long revision, List<Section> sections,
        List<Node> nodes, List<Relationship> relationships, Set<String> units) {
        this(type,title,url,revision,sections,nodes,relationships,units,WikiMaps.EMPTY);
    }
    public SemanticDocument(PageType type,String title,String url,long revision,List<Section> sections,
        List<Node> nodes,List<Relationship> relationships,Set<String> units,WikiMaps maps) {
        this.maps=maps;
        this.type = type;
        this.title = title;
        this.url = url;
        this.revision = revision;
        this.sections = List.copyOf(sections);
        this.nodes = List.copyOf(nodes);
        List<Reference> definitions = new ArrayList<>();
        for (Node node : nodes)
            if (node.fallback instanceof WikiDocument.Text)
                for (WikiDocument.ReferenceDefinition ref : ((WikiDocument.Text) node.fallback).references)
                    definitions.add(new Reference(ref.id, SemanticExtractor.rich(ref.html), ref.backlinks,
                        new Source(title, url, revision, ref.id, "References", node.source.unit)));
        this.references = List.copyOf(definitions);
        this.relationships = List.copyOf(relationships);
        this.contentUnits = Set.copyOf(units);
        this.recipes = RecipeExtractor.extract(this.nodes);
    }
}
