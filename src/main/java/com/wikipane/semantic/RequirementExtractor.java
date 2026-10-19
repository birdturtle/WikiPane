package com.wikipane.semantic;

import static com.wikipane.semantic.SemanticDocument.*;

import com.wikipane.api.WikiUrls;
import com.wikipane.document.WikiDocumentParser;
import java.util.*;
import com.wikipane.vendor.jsoup.nodes.Element;

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
        return item(element, context, false);
    }
    private static Requirement item(Element element, String context, boolean questList) {
        Element own = element.clone();
        own.select("ul,ol").remove();
        List<Requirement> children = new ArrayList<>();
        boolean skillsVerifiable = simpleSkills(own);
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
                url, level, null, boost, SemanticExtractor.rich(own), List.of(), skillsVerifiable));
        }
        boolean listIntroduction = element.children().stream().anyMatch(child -> child.tagName().equals("ul"))
            && own.select("a[href]").isEmpty()
            && own.text().matches("(?i)^Completion(?: \\(or partial completion where stated\\))? of (?:the )?following quests:?$");
        if (children.isEmpty() && !listIntroduction) {
            Element link = own.selectFirst("a[href]");
            String text = own.text();
            String lower = text.toLowerCase(Locale.ROOT);
            Requirement.Kind kind = Requirement.Kind.OTHER;
            if (context.toLowerCase(Locale.ROOT).matches(".*(items|equipment).*"))
                kind = Requirement.Kind.ITEM;
            else if (link != null
                && (lower.startsWith("completion of ") || lower.startsWith("complete ")
                    || lower.startsWith("completed ") || questList && own.select("a[href]").size() == 1
                        && text.equals(link.text())))
                kind = Requirement.Kind.QUEST;
            else if (link != null && positiveQuestStage(own, link)) kind = Requirement.Kind.QUEST_STAGE;
            Integer quantity = null;
            java.util.regex.Matcher amount =
                java.util.regex.Pattern.compile("^([0-9][0-9,]*)\\s").matcher(text);
            if (kind == Requirement.Kind.ITEM && amount.find())
                quantity = integer(amount.group(1).replace(",", ""));
            children.add(new Requirement(kind, link == null ? text : link.text(),
                link == null ? "" : WikiDocumentParser.safeLink(link.absUrl("href")), null, quantity,
                Requirement.Boost.UNSPECIFIED, SemanticExtractor.rich(own), List.of(),
                kind != Requirement.Kind.QUEST || simpleQuest(own, link, questList)));
        }
        for (Element nested : element.children())
            if (nested.tagName().equals("ul") || nested.tagName().equals("ol"))
                for (Element li : nested.children())
                    if (li.tagName().equals("li"))
                        children.add(item(li, context, questList || own.text().toLowerCase(Locale.ROOT).startsWith("completion")
                            && own.text().toLowerCase(Locale.ROOT).contains("quests")));
        if (children.size() == 1 && !listIntroduction)
            return children.get(0);
        String text = own.text().toLowerCase(Locale.ROOT);
        boolean choice = text.contains(" or ") || text.contains("one of") || text.startsWith("either ");
        return new Requirement(choice ? Requirement.Kind.ANY : Requirement.Kind.ALL, own.text(), "", null,
            null, Requirement.Boost.UNSPECIFIED, SemanticExtractor.rich(element), children);
    }
    private static boolean positiveQuestStage(Element own, Element quest) {
        String text = own.text();
        return text.matches("(?i)^Started\\s+" + java.util.regex.Pattern.quote(quest.text())
            + "\\s+(?:up until|up to|until)\\s+[^.;]+[.]?$")
            && !text.matches("(?i).*\\b(and|or|if|unless|without|not|before|instead)\\b.*");
    }
    private static boolean simpleQuest(Element own, Element link, boolean questList) {
        if (link == null || own.select("a[href]").size() != 1) return false;
        String text = own.text().strip();
        if (questList && text.equals(link.text())) return true;
        return text.matches("(?i)^(?:completion of|complete|completed)\\s+" + java.util.regex.Pattern.quote(link.text()) + "[.]?$");
    }
    private static boolean simpleSkills(Element own) {
        if (own.select("[data-skill][data-level]").isEmpty()) return false;
        Element rest = new Element("div").appendChild(own.clone());
        for (Element skill : rest.select("[data-skill][data-level]")) skill.replaceWith(new com.wikipane.vendor.jsoup.nodes.TextNode(" condition "));
        String text = rest.text().toLowerCase(Locale.ROOT);
        // Reward-accounting notes explain where points come from, not an alternative threshold.
        // Keep the note verbatim and never add those future rewards to the player's current points.
        if (own.select("[data-skill][data-level]").size() == 1
            && own.selectFirst("[data-skill][data-level]").attr("data-skill").equals("Quest points")
            && text.matches("^condition\\s*\\([0-9]+ are (?:earned|gained) [^()]+\\)$")
            && !text.matches(".*\\b(if|unless|when|while|or|instead|alternatively|only|before|after)\\b.*")) return true;
        boolean differentBoosts = text.contains("not boostable") && text.replace("not boostable", "").contains("boostable");
        if (differentBoosts || text.contains("and") && text.contains(" or ")) return false;
        text = text.replaceAll("\\[?(?:not boostable|boostable)\\]?", "").replaceAll("[()\\[\\]]", "").replaceAll("\\s+", " ").strip();
        return text.matches("(?i)^(?:either |both )?condition(?:\\s*(?:,|and|or)\\s*condition)*[.]?$");
    }
    private static Integer integer(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
