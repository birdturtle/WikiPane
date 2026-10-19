/* Adapted from jsoup 1.23.2, Copyright Jonathan Hedley; MIT license: third-party/jsoup-LICENSE.txt. */
package org.jsoup;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** In-memory facade only. No HTTP sessions, file loading, URL fetching or form submission. */
public final class Jsoup {
    private Jsoup() {}
    public static Document parse(String html) { return Parser.parse(html, ""); }
    public static Document parse(String html, String baseUri) { return Parser.parse(html, baseUri); }
    public static Document parse(String html, Parser parser) { return parser.parseInput(html, ""); }
    public static Document parse(String html, String baseUri, Parser parser) {
        return parser.parseInput(html, baseUri);
    }
    public static Document parseBodyFragment(String html) { return Parser.parseBodyFragment(html, ""); }
    public static Document parseBodyFragment(String html, String baseUri) {
        return Parser.parseBodyFragment(html, baseUri);
    }
    public static String clean(String html, Safelist safelist) { return clean(html, "", safelist); }
    public static String clean(String html, String baseUri, Safelist safelist) {
        return new Cleaner(safelist).clean(parseBodyFragment(html, baseUri)).body().html();
    }
    public static boolean isValid(String html, Safelist safelist) {
        return new Cleaner(safelist).isValidBodyHtml(html);
    }
}
