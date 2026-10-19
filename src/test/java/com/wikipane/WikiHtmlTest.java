package com.wikipane;

import com.wikipane.html.WikiHtml;
import com.wikipane.html.Document;
import org.junit.Test;
import static org.junit.Assert.*;

/** Dependency-free markup normalization contracts; expected data does not use a second parser. */
public class WikiHtmlTest {
    @Test public void malformedWikiTablesListsAndEntityTextRetainTheirContentAndLinks() {
        Document document = WikiHtml.parseBodyFragment("<table><tr><th>Item<th>Chance<tr><td><a href='/w/Seed'>"
            + "Seed &amp; herb</a><td>1/128</table><ol><li>First<li>Second</ol>",
            "https://oldschool.runescape.wiki/w/Example");
        assertEquals(2, document.select("table tr").size());
        assertEquals("1/128", document.select("table tr").last().select("td").last().text());
        assertEquals("Seed & herb", document.selectFirst("a").text());
        assertEquals("https://oldschool.runescape.wiki/w/Seed", document.selectFirst("a").absUrl("href"));
        assertEquals(2, document.select("ol > li").size());
        assertEquals("Second", document.select("ol > li").last().text());
    }
    @Test public void modernContainersAndQuotedAttributesRemainIntact() {
        Document document = WikiHtml.parse("<section data-value='a > b'><figure><img src='/x.png'>"
            + "<figcaption>Fish &#x1F41F; &apos; &nbsp;</figcaption></figure></section>");
        assertEquals("a > b", document.selectFirst("section").attr("data-value"));
        assertTrue(document.selectFirst("figcaption").text().contains("Fish 🐟 '"));
        assertEquals(1, document.select("section > figure img").size());
    }
    @Test public void commentsScriptsAndUnknownContainersCannotHideFollowingContent() {
        Document document = WikiHtml.parse("<!-- <table>not data</table> --><script>const x = '<tr>';</script>"
            + "<custom-box data-skill='Cooking' data-level='10'><p>10 Cooking</p></custom-box><p>After</p>");
        assertEquals("10 Cooking After", document.text());
        assertEquals("10", document.selectFirst("[data-skill][data-level]").attr("data-level"));
        assertNull(document.selectFirst("script"));
        assertEquals(2, document.select("p").size());
    }
    @Test public void nestedListsDoNotCloseTheirParentItem() {
        Document document = WikiHtml.parse("<ul><li>First<ul><li>Nested</li></ul></li><li>Second</li></ul>");
        assertEquals(2, document.body().child(0).childrenSize());
        assertEquals("First Nested", document.selectFirst("li").text());
    }
    @Test public void tablesRetainCellSpansAttributesAndNestedOwnership() {
        Document document = WikiHtml.parse("<table><tr><th rowspan='2'>Item</th><td><table><tr><td>Nested</td></tr>"
            + "</table></td></tr><tr><td colspan='3'>1/128</td></tr></table>");
        assertEquals("2", document.selectFirst("th").attr("rowspan"));
        assertEquals("3", document.selectFirst("td[colspan]").attr("colspan"));
        assertEquals(3, document.select("tr").size());
        assertSame(document.select("table").get(1), document.selectFirst("td td").closest("table"));
    }
    @Test public void cloningAndFragmentReplacementPreserveLinksAndSourceTree() {
        Document source = WikiHtml.parseBodyFragment("<p><a href='/w/Herb?x=1&amp;y=2'>Herb</a></p>",
            "https://oldschool.runescape.wiki/w/Seed");
        com.wikipane.html.Element copy = source.body().clone();
        copy.selectFirst("a").text("Changed");
        assertEquals("Herb", source.text());
        assertEquals("https://oldschool.runescape.wiki/w/Herb?x=1&y=2", copy.selectFirst("a").absUrl("href"));
        assertTrue(copy.html().contains("x=1&amp;y=2"));
        copy.html("<p>New</p>");
        assertEquals("New", copy.text());
        assertEquals("Herb", source.text());
    }
    @Test public void numericAndUnknownEntitiesKeepTheirMeaning() {
        assertEquals("+4 & < > \" © − &unknown;", WikiHtml.parse("&#43;4 &amp; &lt; &gt; &quot; &copy; &minus; &unknown;").text());
    }
    @Test public void excessiveNestingFailsExplicitly() {
        try { WikiHtml.parse("<div>".repeat(300) + "content"); fail("Expected bounded nesting"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("nested")); }
    }
}
