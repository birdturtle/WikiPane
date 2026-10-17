package com.wikipane.document;

import static com.wikipane.document.WikiDocument.*;

import com.google.gson.*;
import com.wikipane.api.WikiUrls;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Server HTML is parsed, then reduced to explicit semantic blocks and safe inline tags. */
public final class WikiDocumentParser {
    public WikiDocument parse(JsonObject response) {
        if (response.has("error"))
            throw new IllegalArgumentException("The Wiki could not find this article.");
        JsonObject p = response.getAsJsonObject("parse");
        if (p == null || !p.has("text"))
            throw new IllegalArgumentException("The Wiki returned an incomplete article.");
        String title = p.get("title").getAsString();
        Element root = Jsoup.parseBodyFragment(p.get("text").getAsString(), WikiUrls.article(title)).body();
        normalizeTranscludedHeadings(root, title);
        // Script-only chart placeholders contain no article data. Keep the separate real-time price link.
        root.select(".GEChartBox .GEdatachart,.GEChartBox .GEdataprices,.GEChartBox .GEChartItems").remove();
        Map<String, String> entityAnchors = variantIds(root);
        materializeVariants(root);
        for (Element map : root.select(".mw-kartographer-maplink")) {
            String x = map.attr("data-lon"), y = map.attr("data-lat"), plane = map.attr("data-plane");
            map.replaceWith(new Element("span").text(x.isEmpty() || y.isEmpty() ? map.text()
                                                                                : "Map coordinates: " + x
                        + ", " + y + (plane.isEmpty() ? "" : " (plane " + plane + ")")));
        }
        root.select("img[alt='Map icon']").remove();
        root.select("script,style,iframe,form,input,button,.mw-editsection,.toc,.rs-"
                + "external-header-links,.printfooter,.mw-empty-elt,.infobox-switch-buttons-caption,."
                + "infobox-switch-resources,.mw-editsection-like")
            .remove();
        List<Block> blocks = new ArrayList<>();
        walk(root, blocks);
        List<Section> sections = new ArrayList<>();
        for (Block b : blocks)
            if (b instanceof Heading) {
                Heading h = (Heading) b;
                sections.add(new Section(h.text, h.anchor));
            }
        return new WikiDocument(title, WikiUrls.article(title),
            p.has("revid") ? p.get("revid").getAsLong() : 0, blocks, sections, entityAnchors,
            redirectAnchor(p), categories(p));
    }
    /** Recover article-identity containers in transcluded guides using Wiki-provided edit-source titles. */
    private void normalizeTranscludedHeadings(Element root, String article) {
        String origin = "";
        int base = 0, offset = 0;
        boolean container = false;
        for (Element heading : root.select("h1,h2,h3,h4,h5,h6")) {
            int level = Integer.parseInt(heading.tagName().substring(1));
            Element wrapper = heading.closest(".mw-heading");
            Element edit = wrapper == null ? heading.selectFirst(".mw-editsection a[href]")
                                           : wrapper.selectFirst(".mw-editsection a[href]");
            okhttp3.HttpUrl url = edit == null ? null : WikiUrls.resolve(edit.absUrl("href"));
            String source = WikiUrls.wiki(url) ? Objects.toString(WikiUrls.title(url), "") : "";
            if (source.equals(article))
                source = "";
            if (!source.equals(origin)) {
                origin = source;
                base = level;
                offset = 0;
                String leaf = source.substring(source.lastIndexOf('/') + 1).replace('_', ' ');
                container = !source.isBlank() && leaf.equalsIgnoreCase(heading.text().trim());
            } else if (container && level <= base)
                offset = base + 1 - level;
            if (container)
                heading.attr("data-wikipane-level", Integer.toString(Math.min(6, level + offset)));
        }
    }
    private Set<String> categories(JsonObject parse) {
        Set<String> values = new LinkedHashSet<>();
        if (parse.has("categories"))
            for (JsonElement e : parse.getAsJsonArray("categories")) {
                JsonObject c = e.getAsJsonObject();
                if (c.has("category"))
                    values.add(c.get("category").getAsString());
            }
        return values;
    }
    private String redirectAnchor(JsonObject parse) {
        String anchor = "";
        if (parse.has("redirects"))
            for (JsonElement e : parse.getAsJsonArray("redirects")) {
                JsonObject r = e.getAsJsonObject();
                if (r.has("tofragment"))
                    anchor = r.get("tofragment").getAsString();
            }
        return anchor;
    }
    private Map<String, String> variantIds(Element root) {
        Map<String, String> result = new HashMap<>();
        for (Element table : root.select("table[data-resource-class]")) {
            String selector = table.attr("data-resource-class");
            if (!selector.matches("\\.[A-Za-z0-9_-]+"))
                continue;
            Element resources = root.selectFirst(selector);
            if (resources == null)
                continue;
            for (Element button : table.select("[data-switch-index]")) {
                String index = button.attr("data-switch-index");
                if (!index.matches("[0-9]+"))
                    continue;
                for (Element ids :
                    resources.select("[data-attr-param=id] > [data-attr-index='" + index + "']")) {
                    java.util.regex.Matcher matcher =
                        java.util.regex.Pattern.compile("\\b[0-9]+\\b").matcher(ids.text());
                    while (matcher.find())
                        result.put(matcher.group(), button.attr("data-switch-anchor").replaceFirst("^#", ""));
                }
            }
        }
        return result;
    }
    /** Wiki infobox switches store alternate values in hidden resource spans, not complete tables. */
    private void materializeVariants(Element root) {
        for (Element table : new ArrayList<>(root.select("table[data-resource-class]"))) {
            String selector = table.attr("data-resource-class");
            if (!selector.matches("\\.[A-Za-z0-9_-]+"))
                continue;
            Element resources = root.selectFirst(selector);
            List<Element> buttons = table.select(".infobox-switch-buttons-caption [data-switch-index]");
            if (resources == null || buttons.isEmpty())
                continue;
            Element group = new Element("div");
            for (Element button : buttons) {
                Element variant = table.clone();
                String index = button.attr("data-switch-index");
                if (!index.matches("[0-9]+"))
                    continue;
                for (Element target : variant.select("[data-attr-param]")) {
                    Element resource = null;
                    for (Element candidate : resources.children())
                        if (candidate.attr("data-attr-param").equals(target.attr("data-attr-param"))) {
                            resource = candidate;
                            break;
                        }
                    if (resource == null)
                        continue;
                    Element value = resource.selectFirst("[data-attr-index='" + index + "']");
                    if (value == null)
                        value = resource.selectFirst("[data-attr-index='0']");
                    if (value != null) {
                        Element copy = value.clone();
                        copy.select("a[href*=action=edit]").remove();
                        target.html(copy.html());
                    }
                }
                variant.select(".infobox-switch-buttons-caption").remove();
                String kind = table.hasClass("infobox-bonuses") ? "Combat bonuses"
                    : table.hasClass("infobox-monster")         ? "Monster information"
                                                                : "Item information";
                variant.prependElement("caption").text(kind + " / " + button.text());
                variant.attr("data-wikipane-kind", kind);
                variant.attr("data-wikipane-variant", button.text());
                variant.attr("id", button.attr("data-switch-anchor").replaceFirst("^#", ""));
                group.appendChild(variant);
            }
            table.replaceWith(group);
        }
    }
    private void walk(Element parent, List<Block> out) {
        StringBuilder loose = new StringBuilder();
        for (Node n : parent.childNodes()) {
            if (n instanceof TextNode) {
                loose.append(escape(((TextNode) n).text()));
                continue;
            }
            if (!(n instanceof Element))
                continue;
            Element e = (Element) n;
            String tag = e.normalName();
            if ((e.hasClass("hatnote") || e.hasClass("dablink") || e.hasClass("rellink"))
                && e.select("table,h1,h2,h3,h4,h5,h6").isEmpty()) {
                flush(loose, out);
                out.add(new Text(inline(e), List.of(), List.of(), true));
            } else if (tag.equals("ol")
                && (e.hasClass("references")
                    || e.children().stream().anyMatch(li -> li.id().startsWith("cite_note")))) {
                flush(loose, out);
                List<ReferenceDefinition> definitions = new ArrayList<>();
                for (Element li : e.children())
                    if (li.tagName().equals("li")) {
                        List<String> backlinks = new ArrayList<>();
                        for (Element a : li.select(".mw-cite-backlink a[href]")) {
                            String url = safeLink(a.absUrl("href"));
                            if (!url.isBlank())
                                backlinks.add(url);
                        }
                        Element content = li.clone();
                        content.select(".mw-cite-backlink").remove();
                        definitions.add(new ReferenceDefinition(li.id(), inline(content), backlinks));
                    }
                if (!definitions.isEmpty())
                    out.add(new Text(inline(e), List.of(), definitions));
            } else if (tag.matches("h[1-6]")) {
                flush(loose, out);
                String anchor = e.id();
                if (anchor.isEmpty()) {
                    Element a = e.selectFirst("[id]");
                    if (a != null)
                        anchor = a.id();
                }
                out.add(new Heading(e.text(), anchor,
                    e.hasAttr("data-wikipane-level") ? Integer.parseInt(e.attr("data-wikipane-level"))
                                                     : Integer.parseInt(tag.substring(1))));
            } else if (tag.equals("table")) {
                flush(loose, out);
                Table normalized = table(e);
                out.add(normalized);
                if (normalized.kind.isEmpty())
                    for (Element image : e.select("img"))
                        if (size(image, "width") > 40 || size(image, "height") > 40
                            || image.closest(".infobox-image") != null)
                            addImage(image, out);
            } else if (tag.equals("img")) {
                flush(loose, out);
                addImage(e, out);
            } else if (Arrays.asList("p", "ul", "ol", "dl", "pre", "blockquote").contains(tag)) {
                flush(loose, out);
                String html = inline(e);
                if (!e.text().isBlank() || !e.select("img").isEmpty())
                    out.add(new Text(html, com.wikipane.semantic.RequirementExtractor.extract(e, "")));
                for (Element image : e.select("img"))
                    if (size(image, "width") > 40 || size(image, "height") > 40
                        || image.closest(".infobox-image") != null)
                        addImage(image, out);
            } else if (Arrays.asList("div", "section", "figure", "figcaption", "center", "aside")
                           .contains(tag)) {
                flush(loose, out);
                walk(e, out);
            } else if (!e.select("table,h1,h2,h3,h4,h5,h6,p,ul,ol,figure").isEmpty()) {
                flush(loose, out);
                walk(e, out);
            } else {
                loose.append(inline(e));
            }
        }
        flush(loose, out);
    }
    private static void flush(StringBuilder text, List<Block> out) {
        if (!Jsoup.parseBodyFragment(text.toString()).text().isBlank())
            out.add(new Text(text.toString()));
        text.setLength(0);
    }
    private static int size(Element e, String key) {
        try {
            return Integer.parseInt(e.attr(key));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
    private void addImage(Element e, List<Block> out) {
        okhttp3.HttpUrl url = WikiUrls.resolve(e.attr("src"));
        if (!WikiUrls.image(url))
            return;
        Element link = e.closest("a[href]");
        String href = link == null ? "" : safeLink(link.attr("href"));
        out.add(new Image(url.toString(), imageText(e), href, size(e, "width"), size(e, "height")));
    }
    private Table table(Element table) {
        List<Row> rows = new ArrayList<>();
        Map<Integer, Integer> occupied = new HashMap<>();
        Map<Integer, String> labels = new HashMap<>();
        Map<Integer, Cell> spanning = new TreeMap<>();
        Element caption = table.selectFirst("caption");
        String name = caption == null ? "" : caption.text();
        if (name.isBlank()) {
            if (table.hasClass("questdetails"))
                name = "Quest requirements and details";
            else if (table.hasClass("infobox-quest"))
                name = "Quest information";
            else if (table.hasClass("infobox-npc"))
                name = "NPC information";
            else if (table.hasClass("item-drops"))
                name = "Drops";
            else if (table.hasClass("messagebox"))
                name = "Article notice";
        }
        for (Element tr : table.select("tr")) {
            if (tr.closest("table") != table)
                continue;
            List<Cell> cells = new ArrayList<>();
            List<Cell> carried = new ArrayList<>();
            for (Map.Entry<Integer, Cell> span : spanning.entrySet())
                if (occupied.getOrDefault(span.getKey(), 0) > 0)
                    carried.add(span.getValue());
            int column = 0;
            String rowLabel = "";
            boolean rowHeader = tr.children().stream().filter(c -> c.tagName().equals("th")).count() == 1
                && tr.children().stream().anyMatch(c -> c.tagName().equals("td"));
            for (Element td : tr.children()) {
                if (!td.normalName().equals("td") && !td.normalName().equals("th"))
                    continue;
                while (occupied.getOrDefault(column, 0) > 0) column++;
                int cs = Math.max(1, Math.min(1000, size(td, "colspan"))),
                    rs = Math.max(1, Math.min(10000, size(td, "rowspan")));
                boolean header = td.normalName().equals("th");
                List<Table> nested = new ArrayList<>();
                for (Element nestedTable : td.select("table"))
                    if (nestedTable.parent().closest("table") == table)
                        nested.add(table(nestedTable));
                Element own = td.clone();
                own.select("table").remove();
                // Membership icons carry the visible meaning; hidden numeric sort keys do not.
                if (own.select("a[title=Members] img, a[title=Free-to-play] img").size() > 0)
                    own.select("span[style]").stream()
                        .filter(span -> span.attr("style").replace(" ", "").toLowerCase(Locale.ROOT).contains("display:none")
                            && span.text().matches("[01]"))
                        .forEach(Element::remove);
                String html = inline(own);
                String text = Jsoup.parseBodyFragment(html).text();
                StringJoiner join = new StringJoiner(" / ");
                Set<String> unique = new LinkedHashSet<>();
                for (int c = column; c < column + cs; c++) {
                    String l = labels.get(c);
                    if (l != null && !l.isBlank())
                        unique.add(l);
                }
                unique.forEach(join::add);
                // Infobox headers label their own row, not subsequent full-width controls or notes.
                String label = !header && !rowLabel.isEmpty() ? rowLabel
                    : (table.hasClass("infobox") || table.hasClass("questdetails")) && cs > 1
                        && tr.children().stream().filter(child -> child.normalName().matches("td|th")).count() == 1
                    ? "" : join.toString();
                if (header && rowHeader && !text.isBlank())
                    rowLabel = text;
                List<Block> cellImages = new ArrayList<>();
                if (own.text().isBlank() && own.selectFirst("img") != null)
                    addImage(own.selectFirst("img"), cellImages);
                cells.add(new Cell(html, label, header, column, cs, rs, nested, td.attr("data-attr-param"),
                    com.wikipane.semantic.RequirementExtractor.extract(td, header ? "" : label),
                    own.text().isBlank() && own.selectFirst("img") != null
                            && own.selectFirst("a[href]") != null
                        ? safeLink(own.selectFirst("a[href]").absUrl("href"))
                        : "", header && td.hasClass("infobox-subheader"),
                    cellImages.isEmpty() ? null : (Image) cellImages.get(0)));
                if (header && !text.isBlank())
                    for (int c = column; c < column + cs; c++) labels.put(c, text);
                for (int c = column; c < column + cs; c++) occupied.put(c, rs);
                if (rs > 1)
                    spanning.put(column, cells.get(cells.size() - 1));
                column += cs;
            }
            if (!cells.isEmpty())
                rows.add(new Row(cells, carried));
            occupied.replaceAll((c, left) -> Math.max(0, left - 1));
            spanning.keySet().removeIf(c -> occupied.getOrDefault(c, 0) == 0);
        }
        String kind = table.attr("data-wikipane-kind");
        if (table.hasClass("questdetails"))
            kind = "Quest details";
        if (kind.isEmpty() && table.hasClass("infobox"))
            kind = name.isBlank() ? "Information" : name;
        List<Image> pictures = new ArrayList<>();
        for (Element photo : table.select(".infobox-image img")) {
            List<Block> images = new ArrayList<>();
            addImage(photo, images);
            for (Block image : images) pictures.add((Image) image);
        }
        return new Table(
            name, table.id(), rows, kind, table.attr("data-wikipane-variant"), pictures, table.classNames());
    }
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String imageText(Element e) {
        String alt = e.attr("alt");
        Element context = e.closest("p,li,th,td");
        if (context != null && !context.text().isBlank() && (alt.isBlank() || context.text().contains(alt)))
            return "";
        if (alt.isBlank()) {
            Element a = e.closest("a[title]");
            if (a != null)
                alt = a.attr("title");
        }
        return alt;
    }
    public static String safeLink(String raw) {
        okhttp3.HttpUrl u = WikiUrls.resolve(raw);
        return WikiUrls.browser(u) ? u.toString() : "";
    }
    public static String inline(Node node) {
        if (node instanceof TextNode)
            return escape(((TextNode) node).text());
        if (!(node instanceof Element))
            return "";
        Element e = (Element) node;
        String tag = e.normalName();
        if (Arrays.asList("script", "style", "iframe", "input", "form", "button").contains(tag))
            return "";
        if (tag.equals("img"))
            return escape(imageText(e));
        if ((tag.equals("ol") && e.attr("start").matches("[0-9]+"))
            || (tag.equals("li") && e.attr("value").matches("[0-9]+"))) {
            StringBuilder body = new StringBuilder();
            for (Node child : e.childNodes()) body.append(inline(child));
            return "<" + tag + " " + (tag.equals("ol") ? "start" : "value") + "=\""
                + e.attr(tag.equals("ol") ? "start" : "value") + "\">" + body + "</" + tag + ">";
        }
        if (tag.equals("br"))
            return "<br>";
        StringBuilder body = new StringBuilder();
        for (Node child : e.childNodes()) body.append(inline(child));
        if ((tag.equals("sup") || tag.equals("li")) && e.id().matches("cite_(?:ref|note)[A-Za-z0-9_.:-]*"))
            return "<" + tag + " id=\"" + escape(e.id()) + "\">" + body + "</" + tag + ">";
        if (tag.equals("a")) {
            String link =
                e.hasAttr("href") ? safeLink(e.baseUri().isEmpty() ? e.attr("href") : e.absUrl("href")) : "";
            return link.isEmpty() ? body.toString() : "<a href=\"" + escape(link) + "\">" + body + "</a>";
        }
        if (Arrays
                .asList("b", "strong", "i", "em", "sup", "sub", "u", "s", "code", "ul", "ol", "li", "dl",
                    "dt", "dd", "blockquote", "p")
                .contains(tag))
            return "<" + tag + ">" + body + "</" + tag + ">";
        if (tag.equals("tr"))
            return body + "<br>";
        if (tag.equals("td") || tag.equals("th"))
            return body + " ";
        return body.toString();
    }
}
