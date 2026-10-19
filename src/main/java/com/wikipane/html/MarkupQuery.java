package com.wikipane.html;

import java.util.*;
import java.util.regex.*;

/** Only the structural queries used by Wiki normalization: tag, class, attributes, ancestors and children. */
final class MarkupQuery {
    private static final Pattern PART = Pattern.compile("(^[a-zA-Z][\\w-]*|\\*)|\\.([\\w-]+)|"
        + "\\[([\\w:-]+)(?:(\\*=|=)(?:'([^']*)'|\"([^\"]*)\"|([^\\]]*)))?\\]");
    private MarkupQuery() {}
    static Elements select(Element root, String query) {
        Elements result = new Elements();
        List<List<String>> groups = groups(query);
        for (Element element : root.getAllElements()) {
            for (List<String> group : groups) if (chain(element, group, group.size() - 1)) {
                result.add(element); break;
            }
        }
        return result;
    }
    static boolean matches(Element element, String query) {
        for (List<String> group : groups(query)) if (chain(element, group, group.size() - 1)) return true;
        return false;
    }
    private static boolean chain(Element element, List<String> parts, int index) {
        if (element == null || index < 0 || !simple(element, parts.get(index))) return false;
        if (index == 0) return true;
        if (parts.get(index - 1).equals(">")) return chain(element.parent(), parts, index - 2);
        for (Element parent = element.parent(); parent != null; parent = parent.parent())
            if (chain(parent, parts, index - 1)) return true;
        return false;
    }
    private static boolean simple(Element element, String query) {
        Matcher matcher = PART.matcher(query); int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Unsupported markup query: " + query);
            end = matcher.end();
            if (matcher.group(1) != null && !matcher.group(1).equals("*")
                && !element.tagName().equalsIgnoreCase(matcher.group(1))) return false;
            if (matcher.group(2) != null && !element.hasClass(matcher.group(2))) return false;
            if (matcher.group(3) != null) {
                String key = matcher.group(3), operator = matcher.group(4);
                if (!element.hasAttr(key)) return false;
                String expected = matcher.group(5) != null ? matcher.group(5)
                    : matcher.group(6) != null ? matcher.group(6) : matcher.group(7);
                if (operator != null && !(operator.equals("=") ? element.attr(key).equals(expected)
                    : element.attr(key).contains(expected))) return false;
            }
        }
        if (end != query.length()) throw new IllegalArgumentException("Unsupported markup query: " + query);
        return true;
    }
    private static List<List<String>> groups(String query) {
        List<List<String>> groups = new ArrayList<>(); List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder(); int bracket = 0; char quote = 0;
        for (int i = 0; i <= query.length(); i++) {
            char c = i == query.length() ? ',' : query.charAt(i);
            if (quote != 0) { token.append(c); if (c == quote) quote = 0; continue; }
            if (c == '\'' || c == '"') { quote = c; token.append(c); continue; }
            if (c == '[') bracket++;
            if (c == ']') bracket--;
            if (bracket == 0 && (Character.isWhitespace(c) || c == '>' || c == ',')) {
                if (token.length() > 0) { parts.add(token.toString()); token.setLength(0); }
                if (c == '>') parts.add(">");
                if (c == ',') { if (!parts.isEmpty()) groups.add(parts); parts = new ArrayList<>(); }
            } else token.append(c);
        }
        return groups;
    }
}
