package com.wikipane.semantic;

import static com.wikipane.semantic.SemanticDocument.*;

import com.wikipane.api.WikiUrls;
import com.wikipane.document.WikiDocument;
import com.wikipane.document.WikiDocumentParser;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Extracts domain structure from all normalized blocks. No UI priorities or truncation. */
public final class SemanticExtractor {
    private SemanticExtractor() {}
    public static RichText rich(Element element) {
        return rich(WikiDocumentParser.inline(element));
    }
    public static RichText rich(String html) {
        Element root = Jsoup.parseBodyFragment(html, WikiUrls.BASE.toString()).body();
        List<Link> links = new ArrayList<>();
        for (Element a : root.select("a[href]")) {
            okhttp3.HttpUrl url = WikiUrls.resolve(a.attr("href"));
            if (WikiUrls.browser(url))
                links.add(new Link(a.text(), url.toString(), WikiUrls.wiki(url)));
        }
        return new RichText(html, root.text(), links);
    }
    public static SemanticDocument extract(WikiDocument document) {
        PageType type = pageType(document);
        List<Section> sections = new ArrayList<>();
        List<Node> nodes = new ArrayList<>();
        List<Relationship> allLinks = new ArrayList<>();
        Set<String> allUnits = new LinkedHashSet<>();
        List<Node> current = new ArrayList<>();
        String anchor = "", title = "Information", parent = "";
        int level = 1;
        Role inherited = Role.INFORMATION;
        Deque<SectionHead> hierarchy = new ArrayDeque<>();
        hierarchy.push(new SectionHead("", 1, Role.INFORMATION, ""));
        for (int index = 0; index < document.blocks.size(); index++) {
            WikiDocument.Block block = document.blocks.get(index);
            if (block instanceof WikiDocument.Heading) {
                sections.add(new Section(anchor, title, parent, level, inherited, current));
                current = new ArrayList<>();
                WikiDocument.Heading heading = (WikiDocument.Heading) block;
                while (!hierarchy.isEmpty() && hierarchy.peek().level >= heading.level) hierarchy.pop();
                parent = hierarchy.isEmpty() ? "" : hierarchy.peek().anchor;
                Role ancestor = hierarchy.isEmpty() ? Role.OTHER : hierarchy.peek().role;
                anchor = heading.anchor;
                title = heading.text;
                level = heading.level;
                inherited = role(title, ancestor);
                hierarchy.push(new SectionHead(anchor, level, inherited, title));
                continue;
            }
            String id = "b" + index;
            StringJoiner path = new StringJoiner(" / ");
            for (java.util.Iterator<SectionHead> it = hierarchy.descendingIterator(); it.hasNext();) {
                String part = it.next().title;
                if (!part.isEmpty())
                    path.add(part);
            }
            Source source = new Source(document.title, document.url, document.revision, anchor,
                path.length() == 0 ? "Information" : path.toString(), id);
            Set<String> units = new LinkedHashSet<>();
            List<Fact> facts = new ArrayList<>();
            List<RequirementSet> requirements = new ArrayList<>();
            List<Relationship> links = new ArrayList<>();
            List<Location> locations = new ArrayList<>();
            List<Instruction> instructions = new ArrayList<>();
            RichText text = null;
            Table table = null;
            Role semanticRole = inherited;
            NodeType nodeType = NodeType.FALLBACK;
            if (block instanceof WikiDocument.Text) {
                text = rich(((WikiDocument.Text) block).html);
                nodeType = !((WikiDocument.Text) block).references.isEmpty() ? NodeType.REFERENCES
                    : text.html.contains("<ol>")                             ? NodeType.INSTRUCTIONS
                    : text.html.contains("<ul>")                             ? NodeType.LIST
                                                                             : NodeType.TEXT;
                units.add(id);
                relationships(text, source, inherited, links);
                locations.addAll(locations(text, source));
                WikiDocument.Text raw = (WikiDocument.Text) block;
                List<RequirementGroup> groups = raw.requirements;
                if (groups.isEmpty() && inherited == Role.REQUIREMENTS && nodeType != NodeType.REFERENCES)
                    groups = RequirementExtractor.extract(
                        Jsoup.parseBodyFragment(raw.html, document.url).body(), "Requirements");
                if (!groups.isEmpty())
                    requirements.add(new RequirementSet(groups, source));
                Element content = Jsoup.parseBodyFragment(raw.html, document.url).body();
                if (nodeType == NodeType.REFERENCES)
                    semanticRole = Role.REFERENCES;
                for (Element ol : content.select("ol"))
                    if (nodeType != NodeType.REFERENCES && ol.parent().closest("ol") == null)
                        instructions.addAll(instructions(ol, source));
            } else if (block instanceof WikiDocument.Image) {
                WikiDocument.Image image = (WikiDocument.Image) block;
                text = rich(WikiDocumentParser.escape(image.alt));
                nodeType = NodeType.IMAGE;
                units.add(id);
            } else if (block instanceof WikiDocument.Table) {
                WikiDocument.Table raw = (WikiDocument.Table) block;
                semanticRole = tableRole(raw, inherited);
                table = table(document, raw, source, semanticRole, units, facts, requirements, links);
                nodeType = raw.kind.isEmpty() ? NodeType.TABLE : NodeType.INFOBOX;
            } else {
                units.add(id);
                text = rich("This content is available in the original Wiki article.");
            }
            if (!instructions.isEmpty() && (type == PageType.GUIDE || type == PageType.QUEST)
                && (semanticRole == Role.OTHER || semanticRole == Role.INFORMATION))
                semanticRole = Role.WALKTHROUGH;
            for (Fact fact : facts) locations.addAll(locations(fact.value, fact.source));
            Node node = new Node(id, nodeType, semanticRole, source, block, text, table, facts, requirements,
                links, locations, instructions, units);
            nodes.add(node);
            current.add(node);
            allLinks.addAll(links);
            allUnits.addAll(units);
        }
        sections.add(new Section(anchor, title, parent, level, inherited, current));
        return new SemanticDocument(
            type, document.title, document.url, document.revision, sections, nodes, allLinks, allUnits);
    }
    private static Table table(WikiDocument document, WikiDocument.Table raw, Source source, Role role,
        Set<String> units, List<Fact> facts, List<RequirementSet> requirements, List<Relationship> links) {
        List<Row> rows = new ArrayList<>();
        Map<WikiDocument.Cell, Cell> mapped = new IdentityHashMap<>();
        String group = "";
        for (int r = 0; r < raw.rows.size(); r++) {
            WikiDocument.Row row = raw.rows.get(r);
            List<Cell> cells = new ArrayList<>(), carried = new ArrayList<>();
            for (WikiDocument.Cell previous : row.carried) {
                Cell old = mapped.get(previous);
                if (old != null)
                    carried.add(old);
            }
            for (int c = 0; c < row.cells.size(); c++) {
                WikiDocument.Cell original = row.cells.get(c);
                String id = source.unit + ".r" + r + ".c" + c;
                Source origin = new Source(
                    source.article, source.url, source.revision, source.section, source.sectionTitle, id);
                RichText value = rich(original.html);
                List<Table> nested = new ArrayList<>();
                for (int n = 0; n < original.nested.size(); n++) {
                    Source ns = new Source(source.article, source.url, source.revision, source.section,
                        source.sectionTitle, id + ".n" + n);
                    nested.add(
                        table(document, original.nested.get(n), ns, role, units, facts, requirements, links));
                }
                Cell cell = new Cell(value, original.semanticKey, original.label, original.header,
                    original.column, original.colspan, original.rowspan, origin, nested,
                    original.requirements, original.illustrationTarget);
                cells.add(cell);
                mapped.put(original, cell);
                if (!value.text.isBlank() || !nested.isEmpty())
                    units.add(id);
                if (cell.header && (original.sectionHeader || row.cells.stream().allMatch(candidate -> candidate.header))
                    && (cell.colspan > 1 || row.cells.size() == 1) && !cell.value.text.isBlank())
                    group = cell.value.text;
                if (!cell.header && !value.text.isBlank())
                    facts.add(new Fact(cell.key, cell.label, group, raw.variant, value, origin));
                if (!cell.requirements.isEmpty())
                    requirements.add(new RequirementSet(cell.requirements, origin));
                relationships(value, origin, role, links);
            }
            if (!cells.isEmpty())
                rows.add(new Row(cells, carried));
        }
        for (int i = 0; i < raw.images.size(); i++) units.add(source.unit + ".image" + i);
        return new Table(raw.caption, raw.variant, raw.kind, role, source, rows, raw.classes, raw.images);
    }
    private static List<Instruction> instructions(Element list, Source source) {
        List<Instruction> result = new ArrayList<>();
        int number = 1;
        try {
            if (list.hasAttr("start"))
                number = Integer.parseInt(list.attr("start"));
        } catch (NumberFormatException ignored) {
        }
        for (Element li : list.children())
            if (li.tagName().equals("li")) {
                List<Instruction> children = new ArrayList<>();
                for (Element child : li.children())
                    if (child.tagName().equals("ol"))
                        children.addAll(instructions(child, source));
                Element own = li.clone();
                own.select("ol").remove();
                try {
                    if (li.hasAttr("value"))
                        number = Integer.parseInt(li.attr("value"));
                } catch (NumberFormatException ignored) {
                }
                result.add(new Instruction(number++, rich(own), source, children));
            }
        return result;
    }
    private static List<Location> locations(RichText text, Source source) {
        List<Location> values = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                                        .compile("Map coordinates: (-?[0-9]+(?:\\.[0-9]+)?), "
                                            + "(-?[0-9]+(?:\\.[0-9]+)?)(?: \\(plane ([0-9-]+)\\))?")
                                        .matcher(text.text);
        while (m.find())
            values.add(new Location(m.group(1), m.group(2), Objects.toString(m.group(3), ""), source));
        return values;
    }
    private static void relationships(RichText text, Source source, Role context, List<Relationship> links) {
        for (Link link : text.links) {
            Role role = context;
            String name = link.label.toLowerCase(Locale.ROOT);
            String target =
                Objects.toString(WikiUrls.title(WikiUrls.resolve(link.url)), "").toLowerCase(Locale.ROOT);
            if (target.endsWith("/strategies") || target.endsWith("/strategy"))
                role = Role.STRATEGY;
            else if (target.endsWith("/full guide") || target.endsWith("/quick guide")
                || name.equals("walkthrough") || name.equals("full guide"))
                role = Role.WALKTHROUGH;
            else if (target.contains("training") && (target.contains("/") || name.contains("training")))
                role = Role.METHODS;
            links.add(new Relationship(role, link, source,
                target.endsWith("/strategies") || target.endsWith("/strategy")
                    || target.endsWith("/full guide") || target.endsWith("/quick guide")
                    || target.contains("training")));
        }
    }
    public static Role role(String title, Role inherited) {
        String s = title.toLowerCase(Locale.ROOT);
        if (s.matches(".*(drop|loot|rewards table).*"))
            return Role.DROPS;
        if (s.matches(".*(requirement|prerequisite|items needed|equipment needed).*"))
            return Role.REQUIREMENTS;
        if (s.matches(".*(walkthrough|guide|getting started|quest stages|subquests|chapters).*"))
            return Role.WALKTHROUGH;
        if (s.matches(".*(strateg|mechanic|fight overview|attacks|fight).*"))
            return Role.STRATEGY;
        if (s.matches(".*(combat stat|combat bonus|attack bonus|defence bonus|equipment stat).*"))
            return Role.COMBAT;
        if (s.matches(".*(location|getting there|access|transport).*"))
            return Role.LOCATIONS;
        if (s.matches(".*(training|method|how to|experience|money making|creation|production|types of).*"))
            return Role.METHODS;
        if (s.matches(".*(item sources|acquisition|shop locations|obtaining).*"))
            return Role.SOURCES;
        if (s.matches(".*(reward|unlock).*"))
            return Role.REWARDS;
        if (s.matches(
                ".*(reference|trivia|history|changes|gallery|music|dialogue|external links|see also).*"))
            return Role.OTHER;
        return inherited == Role.INFORMATION ? Role.OTHER : inherited;
    }
    private static Role tableRole(WikiDocument.Table table, Role inherited) {
        if (table.classes.contains("navbox") || table.classes.contains("navbox-subgroup"))
            return Role.RELATIONSHIPS;
        if (table.classes.contains("item-drops"))
            return Role.DROPS;
        if (table.classes.contains("infobox-bonuses"))
            return Role.COMBAT;
        return inherited;
    }
    private static PageType pageType(WikiDocument document) {
        Set<String> classes = new HashSet<>();
        for (WikiDocument.Block block : document.blocks)
            if (block instanceof WikiDocument.Table)
                classes.addAll(((WikiDocument.Table) block).classes);
        if (classes.contains("infobox-monster"))
            return PageType.MONSTER;
        if (classes.contains("infobox-item"))
            return PageType.ITEM;
        if (classes.contains("infobox-quest") || classes.contains("questdetails"))
            return PageType.QUEST;
        for (String category : document.categories) {
            String c = category.toLowerCase(Locale.ROOT).replace('_', ' ');
            if (c.equals("skills"))
                return PageType.SKILL;
            if (c.contains("guides") || c.contains("strategies"))
                return PageType.GUIDE;
        }
        if (classes.contains("infobox-skill"))
            return PageType.SKILL;
        if (document.blocks.stream().anyMatch(b
                -> b instanceof WikiDocument.Text && ((WikiDocument.Text) b).references.isEmpty()
                    && ((WikiDocument.Text) b).html.contains("<ol>")))
            return PageType.GUIDE;
        return PageType.GENERAL;
    }
    private static final class SectionHead {
        final String anchor, title;
        final int level;
        final Role role;
        SectionHead(String a, int l, Role r, String t) {
            anchor = a;
            level = l;
            role = r;
            title = t;
        }
    }
}
