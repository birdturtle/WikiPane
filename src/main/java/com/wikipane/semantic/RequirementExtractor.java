package com.wikipane.semantic;

import static com.wikipane.semantic.SemanticDocument.*;

import com.wikipane.api.WikiUrls;
import com.wikipane.document.WikiDocumentParser;
import java.util.*;
import org.jsoup.nodes.Element;

/** Reads explicit Wiki skill/level markup and list scopes. Ambiguous prose remains OTHER. */
public final class RequirementExtractor {
    private RequirementExtractor() {}
    public static List<RequirementGroup> extract(Element root, String label) {
        boolean context =
            label.toLowerCase(Locale.ROOT).matches(".*(require|needed|recommended|prerequisite).*");
        if (!context && root.select("[data-skill][data-level]").isEmpty())
            return List.of();
        List<RequirementGroup> groups = new ArrayList<>();
        String heading = label.isBlank() ? "Requirements" : label;
        for (Element child :
            (root.tagName().equals("ul") || root.tagName().equals("ol")) ? List.of(root) : root.children()) {
            if ((child.tagName().equals("p") || child.tagName().matches("h[1-6]")) && !child.text().isBlank())
                heading = child.text();
            if (child.tagName().equals("ul") || child.tagName().equals("ol")) {
                List<Requirement> requirements = new ArrayList<>();
                for (Element li : child.children())
                    if (li.tagName().equals("li"))
                        requirements.add(item(li, label));
                if (!requirements.isEmpty())
                    groups.add(new RequirementGroup(heading,
                        new Requirement(Requirement.Kind.ALL, heading, "", null, null,
                            Requirement.Boost.UNSPECIFIED, SemanticExtractor.rich(child), requirements)));
            }
        }
        if (groups.isEmpty())
            groups.add(new RequirementGroup(heading, item(root, label)));
        return groups;
    }
    private static Requirement item(Element element, String context) {
        Element own = element.clone();
        own.select("ul,ol").remove();
        List<Requirement> children = new ArrayList<>();
        for (Element skill : own.select("[data-skill][data-level]")) {
            Integer level = integer(skill.attr("data-level"));
            String name = skill.attr("data-skill");
            String text = own.text().toLowerCase(Locale.ROOT);
            Requirement.Boost boost = text.contains("not boostable") ? Requirement.Boost.NOT_ALLOWED
                : text.contains("boostable")                         ? Requirement.Boost.ALLOWED
                                                                     : Requirement.Boost.UNSPECIFIED;
            String url = skill.select("a[href]")
                             .stream()
                             .map(e -> WikiDocumentParser.safeLink(e.absUrl("href")))
                             .findFirst()
                             .orElse("");
            children.add(new Requirement(
                name.equals("Quest points") ? Requirement.Kind.QUEST_POINTS : Requirement.Kind.SKILL, name,
                url, level, null, boost, SemanticExtractor.rich(own), List.of()));
        }
        if (children.isEmpty()) {
            Element link = own.selectFirst("a[href]");
            String text = own.text();
            String lower = text.toLowerCase(Locale.ROOT);
            Requirement.Kind kind = Requirement.Kind.OTHER;
            if (context.toLowerCase(Locale.ROOT).matches(".*(items|equipment).*"))
                kind = Requirement.Kind.ITEM;
            else if (link != null
                && (lower.startsWith("completion of ") || lower.startsWith("complete ")
                    || lower.startsWith("completed ")))
                kind = Requirement.Kind.QUEST;
            Integer quantity = null;
            java.util.regex.Matcher amount =
                java.util.regex.Pattern.compile("^([0-9][0-9,]*)\\s").matcher(text);
            if (kind == Requirement.Kind.ITEM && amount.find())
                quantity = integer(amount.group(1).replace(",", ""));
            children.add(new Requirement(kind, link == null ? text : link.text(),
                link == null ? "" : WikiDocumentParser.safeLink(link.absUrl("href")), null, quantity,
                Requirement.Boost.UNSPECIFIED, SemanticExtractor.rich(own), List.of()));
        }
        for (Element nested : element.children())
            if (nested.tagName().equals("ul") || nested.tagName().equals("ol"))
                for (Element li : nested.children())
                    if (li.tagName().equals("li"))
                        children.add(item(li, context));
        if (children.size() == 1)
            return children.get(0);
        String text = own.text().toLowerCase(Locale.ROOT);
        boolean choice = text.contains(" or ") || text.contains("one of") || text.startsWith("either ");
        return new Requirement(choice ? Requirement.Kind.ANY : Requirement.Kind.ALL, own.text(), "", null,
            null, Requirement.Boost.UNSPECIFIED, SemanticExtractor.rich(element), children);
    }
    private static Integer integer(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
