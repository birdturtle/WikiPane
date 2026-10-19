package com.wikipane;

import static org.junit.Assert.*;

import com.google.gson.*;
import com.wikipane.document.*;
import com.wikipane.document.WikiDocument.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.wikipane.vendor.jsoup.Jsoup;
import org.junit.Test;

public class DocumentTest {
    public static JsonObject fixture(String name) throws IOException {
        try (InputStream stream = DocumentTest.class.getResourceAsStream("/wiki/" + name + ".json")) {
            return new JsonParser()
                .parse(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }
    public static WikiDocument document(String name) throws IOException {
        return new WikiDocumentParser().parse(fixture(name));
    }
    private static String plain(String html) {
        return Jsoup.parseBodyFragment(html).text();
    }
    public static String flatten(WikiDocument article) {
        StringBuilder s = new StringBuilder(article.title);
        for (Block b : article.blocks) {
            if (b instanceof Text)
                s.append(' ').append(plain(((Text) b).html));
            if (b instanceof Heading)
                s.append(' ').append(((Heading) b).text);
            if (b instanceof Table)
                for (Row row : ((Table) b).rows)
                    for (Cell cell : row.cells)
                        s.append(' ').append(cell.label).append(' ').append(plain(cell.html));
        }
        return s.toString();
    }
    private String importedHeading(int level, String id, String title, String source) {
        return "<div class='mw-heading'><h" + level + " id='" + id + "'>" + title + "</h" + level
            + "><span class='mw-editsection'><a href='/w/" + source + "?action=edit'>edit</a></span></div>";
    }
    @Test
    public void transcludedArticleIdentityCreatesAContainerButUnrelatedHeadingsStaySiblings() {
        JsonObject response = new JsonObject(), parse = new JsonObject();
        parse.addProperty("title", "Combined guide");
        parse.addProperty("text",
            importedHeading(2, "Chapter", "Study chapter", "Example/Study_chapter")
                + importedHeading(3, "Details", "Details", "Example/Study_chapter") + "<p>Details.</p>"
                + importedHeading(2, "Steps", "Walkthrough", "Example/Study_chapter")
                + "<p>Follow these steps.</p>"
                + importedHeading(2, "Unrelated", "First topic", "Example/Other_article")
                + importedHeading(3, "Subtopic", "Subtopic", "Example/Other_article") + "<p>Content.</p>"
                + importedHeading(2, "Independent", "Second topic", "Example/Other_article")
                + "<p>Independent.</p>");
        response.add("parse", parse);
        WikiDocument doc = new WikiDocumentParser().parse(response);
        assertEquals("Chapter",
            doc.semantic.sections.stream()
                .filter(section -> section.anchor.equals("Steps"))
                .findFirst()
                .orElseThrow()
                .parent);
        assertEquals("",
            doc.semantic.sections.stream()
                .filter(section -> section.anchor.equals("Independent"))
                .findFirst()
                .orElseThrow()
                .parent);
    }
    @Test
    public void bronzeBonusesKeepImageOnlyLabelsAndExactSignedValues() throws Exception {
        WikiDocument article = document("Bronze_dagger");
        assertEquals("Bronze dagger", article.title);
        assertEquals("Unpoisoned", article.entityAnchors.get("1205"));
        boolean found = false;
        for (Block b : article.blocks)
            if (b instanceof Table) {
                String context = "";
                for (Row row : ((Table) b).rows)
                    for (Cell cell : row.cells) {
                        String value = plain(cell.html);
                        if (cell.header && value.contains("Attack bonuses"))
                            context = "Attack";
                        if (cell.header && value.contains("Defence bonuses"))
                            context = "Defence";
                        if (context.equals("Attack") && cell.label.equals("Stab") && !cell.header) {
                            assertEquals("+4", value);
                            assertEquals(1, cell.colspan);
                            found = true;
                        }
                    }
            }
        assertTrue("Stab attack must retain +4 and its heading", found);
        assertTrue(article.blocks.stream().anyMatch(
            b -> b instanceof Image || b instanceof Table && !((Table) b).images.isEmpty()));
        assertTrue(article.blocks.stream().anyMatch(
            b -> b instanceof Table && ((Table) b).caption.equals("Item information / Poison++")));
        assertFalse(flatten(article).contains("(edit)"));
        assertTrue(article.sections.stream().anyMatch(s -> s.anchor.equals("Combat_stats")));
    }
    @Test
    public void recipeStartingRequirementsAreSeparateFromFullCompletion() throws Exception {
        WikiDocument article = document("Recipe_for_Disaster");
        String text = flatten(article);
        assertTrue(text.contains("10 Cooking"));
        assertTrue(text.contains("Cook's Assistant"));
        assertTrue(article.sections.stream().anyMatch(s -> s.title.contains("Another Cook")));
        assertTrue(article.sections.stream().anyMatch(s -> s.title.contains("Defeating")));
        List<Cell> requirements = new ArrayList<>();
        for (Block b : article.blocks)
            if (b instanceof Table)
                for (Row row : ((Table) b).rows)
                    for (Cell c : row.cells)
                        if (plain(c.html).contains("10 Cooking")
                            && plain(c.html).contains("Cook's Assistant"))
                            requirements.add(c);
        assertFalse("Starting requirements must remain in one semantic cell", requirements.isEmpty());
        assertTrue(requirements.stream().anyMatch(
            c -> c.html.contains("/w/Cook%27s_Assistant") || c.html.contains("/w/Cook's_Assistant")));
    }
    @Test
    public void vorkathKeepsStatsAndEverySourceTableCell() throws Exception {
        JsonObject source = fixture("Vorkath");
        WikiDocument article = new WikiDocumentParser().parse(source);
        String text = flatten(article);
        assertTrue(text.contains("Vorkath"));
        assertTrue(text.contains("750"));
        assertTrue(text.contains("732"));
        com.wikipane.vendor.jsoup.nodes.Document raw = Jsoup.parse(source.getAsJsonObject("parse").get("text").getAsString());
        raw.select(".navbox,.metadata,.toc,.infobox-switch-buttons-caption,.infobox-switch-resources")
            .remove();
        int expected = 0;
        for (com.wikipane.vendor.jsoup.nodes.Element tr : raw.select("tr"))
            for (com.wikipane.vendor.jsoup.nodes.Element e : tr.children())
                if (e.tagName().equals("th") || e.tagName().equals("td"))
                    if (e.closest("table").parents().stream().noneMatch(
                            parent -> parent.tagName().equals("table")))
                        expected++;
        int actual = 0;
        for (Block b : article.blocks)
            if (b instanceof Table)
                for (Row row : ((Table) b).rows) actual += row.cells.size();
        assertTrue("All source cells plus explicitly expanded variants must survive", actual >= expected);
    }
    @Test
    public void malformedMarkupDoesNotExecuteCodeOrLeakUnsafeLinks() {
        String html = "<h2><span id='Hello'>Hello</span></h2><p>Useful <b>fact</b><script>bad()</script><img "
            + "src='file:///etc/passwd'><a href='javascript:evil()'>bad</a><a "
            + "href='/w/Cooking#Training'>safe</a></p><table><tr><th "
            + "rowspan=2>Name<th>Value<tr><td>4</table>";
        JsonObject p = new JsonObject();
        p.addProperty("title", "Test");
        p.addProperty("text", html);
        JsonObject response = new JsonObject();
        response.add("parse", p);
        WikiDocument article = new WikiDocumentParser().parse(response);
        String text = flatten(article);
        assertTrue(text.contains("Useful fact"));
        assertFalse(text.contains("bad()"));
        assertEquals("Hello", article.sections.get(0).anchor);
        for (Block b : article.blocks)
            if (b instanceof Text) {
                assertFalse(((Text) b).html.contains("javascript:"));
                assertFalse(((Text) b).html.contains("file:"));
            }
        Table table = (Table) article.blocks.get(article.blocks.size() - 1);
        assertEquals(1, table.rows.get(1).cells.get(0).column);
        assertEquals("Value", table.rows.get(1).cells.get(0).label);
    }
    @Test
    public void sectionOnlyLinksUseArticleUrlAndRedirectFragment() {
        JsonObject parse = new JsonObject();
        parse.addProperty("title", "Cooking");
        parse.addProperty("text", "<p><a href='#Training'>Training</a></p><h2 id='Training'>Training</h2>");
        JsonArray redirects = new JsonArray();
        JsonObject redirect = new JsonObject();
        redirect.addProperty("tofragment", "Training");
        redirects.add(redirect);
        parse.add("redirects", redirects);
        JsonObject response = new JsonObject();
        response.add("parse", parse);
        WikiDocument doc = new WikiDocumentParser().parse(response);
        assertEquals("Training", doc.redirectAnchor);
        assertTrue(
            ((Text) doc.blocks.get(0)).html.contains("https://oldschool.runescape.wiki/w/Cooking#Training"));
    }
    @Test
    public void linksFormattingAndOrderedListsSurvive() {
        String html = WikiDocumentParser.inline(Jsoup
                .parseBodyFragment(
                    "<ol><li>First <a href='/w/Cooking'>Cooking</a></li><li><b>Second</b></li></ol>")
                .body());
        assertTrue(html.contains("<ol>"));
        assertTrue(html.contains("<li>"));
        assertTrue(html.contains("https://oldschool.runescape.wiki/w/Cooking"));
        assertTrue(html.contains("<b>Second</b>"));
    }
}
