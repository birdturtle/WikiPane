package com.wikipane;

import com.wikipane.vendor.jsoup.Jsoup;
import com.wikipane.vendor.jsoup.nodes.Document;
import com.wikipane.vendor.jsoup.nodes.FormElement;
import org.junit.Test;
import static org.junit.Assert.*;

/** Behavioral contracts for the parser-only build, beyond dependency/API inspection. */
public class ParserSubsetTest {
    @Test public void malformedWikiTablesListsAndEntityTextRetainTheirContentAndLinks() {
        Document document = Jsoup.parseBodyFragment("<table><tr><th>Item<th>Chance<tr><td><a href='/w/Seed'>"
            + "Seed &amp; herb</a><td>1/128</table><ol><li>First<li>Second</ol>",
            "https://oldschool.runescape.wiki/w/Example");
        assertEquals(2, document.select("table tr").size());
        assertEquals("1/128", document.select("table tr").last().select("td").last().text());
        assertEquals("Seed & herb", document.selectFirst("a").text());
        assertEquals("https://oldschool.runescape.wiki/w/Seed", document.selectFirst("a").absUrl("href"));
        assertEquals(2, document.select("ol > li").size());
        assertEquals("Second", document.select("ol > li").last().text());
    }
    @Test public void formsRemainReadableDomAndRegexSelectorsUseTheJdkPath() {
        Document document = Jsoup.parse("<form action='https://example.invalid'><input name='x'>"
            + "<p>Cooking 10</p></form>");
        FormElement form = (FormElement)document.selectFirst("form");
        assertEquals(1, form.elements().size());
        assertEquals("Cooking 10", form.selectFirst("p:matches(Cooking.*10)").text());
        assertFalse(com.wikipane.vendor.jsoup.helper.Regex.usingRe2j());
        try { document.selectXpath("//p"); fail("XPath/JAXP integration is deliberately absent"); }
        catch (UnsupportedOperationException expected) {}
    }
}
