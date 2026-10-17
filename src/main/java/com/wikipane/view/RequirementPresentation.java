package com.wikipane.view;

import java.util.*;
import java.util.regex.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

/** Worker-side styling of existing requirement text. No condition rewriting or new facts. */
public final class RequirementPresentation {
    private RequirementPresentation() {}
    private static final Pattern TOKENS = Pattern.compile("(?i)\\b(not boostable|boostable|or|[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\b");
    public static String html(String original) {
        Element root = Jsoup.parseBodyFragment(original).body();
        root.ownerDocument().outputSettings().prettyPrint(false);
        List<TextNode> texts = new ArrayList<>(); collect(root, texts);
        for (TextNode text : texts) {
            if (text.parent() instanceof Element) {
                Element parent = (Element)text.parent(), superscript = parent.closest("sup");
                if (parent.closest("code") != null || superscript != null
                    && !superscript.text().toLowerCase(Locale.ROOT).contains("boostable")) continue;
            }
            String value = text.getWholeText(); Matcher match = TOKENS.matcher(value);
            int offset = 0;
            while (match.find()) {
                if (match.start() > offset) text.before(new TextNode(value.substring(offset, match.start())));
                String token = match.group(), key = token.toLowerCase(Locale.ROOT);
                String style = key.equals("not boostable") ? "boost-forbidden" : key.equals("boostable") ? "boost-allowed"
                    : key.equals("or") ? "alternative" : "requirement-number";
                text.before(new Element("span").addClass(style).text(token)); offset = match.end();
            }
            if (offset > 0) {
                if (offset < value.length()) text.before(new TextNode(value.substring(offset)));
                text.remove();
            }
        }
        return root.html();
    }
    private static void collect(Node node, List<TextNode> texts) {
        if (node instanceof TextNode) texts.add((TextNode)node);
        else for (Node child : node.childNodes()) collect(child, texts);
    }
}
