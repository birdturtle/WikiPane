package com.wikipane.context;

import com.wikipane.api.WikiUrls;
import com.wikipane.semantic.SemanticDocument.Requirement;
import java.util.*;

/** Pure local three-state evaluation. Does not rewrite Wiki data or query game/network state. */
public final class RequirementEvaluator {
    public enum Status { MET, UNMET, UNKNOWN }
    public static final class Result {
        public final Status status;
        public final String summary, detail;
        public final List<Result> children;
        Result(Status status, String summary, String detail, List<Result> children) {
            this.status = status; this.summary = summary; this.detail = detail; this.children = List.copyOf(children);
        }
    }
    public Result evaluate(Requirement requirement, PlayerContextSnapshot player) { return evaluate(requirement, player, 0); }
    private Result evaluate(Requirement r, PlayerContextSnapshot player, int depth) {
        if (!player.enabled) return result(Status.UNKNOWN, "Not checked", "Player requirement checks are disabled.");
        if (!player.available) return result(Status.UNKNOWN, "State unavailable", "Log in and allow the client to finish loading. No requirement has been assumed met.");
        if (depth > 32) return result(Status.UNKNOWN, "Not checked", "This requirement is too deeply nested to verify.");
        if (!r.verifiable) return result(Status.UNKNOWN,
            r.kind == Requirement.Kind.QUEST_POINTS && player.questPoints != null ? "Points " + player.questPoints + " · check notes" : "Check conditions",
            "Additional source conditions are not supported. Read the full requirement; a numeric value alone does not verify it.");
        switch (r.kind) {
            case ALL: case ANY:
                if (r.children.isEmpty()) return result(Status.UNKNOWN, "Not checked", "No verified conditions are available.");
                List<Result> children = new ArrayList<>();
                for (Requirement child : r.children) children.add(evaluate(child, player, depth + 1));
                boolean all = r.kind == Requirement.Kind.ALL;
                Status status = all
                    ? children.stream().anyMatch(c -> c.status == Status.UNMET) ? Status.UNMET
                        : children.stream().allMatch(c -> c.status == Status.MET) ? Status.MET : Status.UNKNOWN
                    : children.stream().anyMatch(c -> c.status == Status.MET) ? Status.MET
                        : children.stream().allMatch(c -> c.status == Status.UNMET) ? Status.UNMET : Status.UNKNOWN;
                return new Result(status, status == Status.MET ? all ? "All met" : "Alternative met"
                    : status == Status.UNMET ? all ? "Not all met" : "No alternative met" : "Partly unverified",
                    (all ? "All conditions must be met." : "At least one alternative must be met.")
                        + " Unsupported conditions remain unknown.", children);
            case SKILL:
                PlayerContextSnapshot.Level level = player.levels.get(PlayerContextSnapshot.skillKey(r.name));
                if (r.level == null || r.level < 1 || level == null || level.base < 1 || level.current < 0)
                    return result(Status.UNKNOWN, "Level unavailable", "This skill or required level could not be verified.");
                int actual = r.boost == Requirement.Boost.ALLOWED ? level.current : level.base;
                String numbers = level.current == level.base ? "Level " + level.base : "Base " + level.base + ", current " + level.current;
                String rule = r.boost == Requirement.Boost.ALLOWED ? "Current level used because boosts are explicitly allowed."
                    : r.boost == Requirement.Boost.NOT_ALLOWED ? "Base level used; boosts are not allowed."
                    : "Base level used; boost permission is unspecified.";
                return result(actual >= r.level ? Status.MET : Status.UNMET, numbers, rule + " Required: " + r.level + ". This compares the stated level, not overall activity eligibility.");
            case QUEST_POINTS:
                if (r.level == null || r.level < 0 || player.questPoints == null)
                    return result(Status.UNKNOWN, "Points unavailable", "Quest points are unavailable.");
                return result(player.questPoints >= r.level ? Status.MET : Status.UNMET,
                    "Quest points " + player.questPoints, "Current quest points compared with the stated requirement of " + r.level + ". Future quest rewards are not counted.");
            case QUEST: case QUEST_STAGE:
                String name = questTitle(r);
                PlayerContextSnapshot.QuestState quest = name == null ? null : player.quests.get(PlayerContextSnapshot.key(name));
                if (quest == null) return result(Status.UNKNOWN, "Quest unverified", "No exact supported quest completion state is available for this Wiki link.");
                if (r.kind == Requirement.Kind.QUEST_STAGE) return result(
                    quest == PlayerContextSnapshot.QuestState.COMPLETE ? Status.MET : Status.UNKNOWN,
                    quest == PlayerContextSnapshot.QuestState.COMPLETE ? "Required progress met" : "Check quest stage",
                    "Full completion satisfies this positive quest-progress requirement. An unfinished quest's intermediate stage is not verified.");
                return result(quest == PlayerContextSnapshot.QuestState.COMPLETE ? Status.MET : Status.UNMET,
                    quest == PlayerContextSnapshot.QuestState.COMPLETE ? "Completed" : "Not completed", "Completion checked locally through RuneLite's quest API. Partial quest stages are not inferred.");
            default:
                if (r.original.text.matches("(?i)^(?:all )?prerequisite quest requirements[.]?$"))
                    return result(Status.UNKNOWN, "See quest list below", "This is a reference to the individual prerequisite quests below, not a separate missing quest.");
                return result(Status.UNKNOWN, "Not checked", "This condition is not supported. Inventory, equipment and intermediate quest stages are not checked.");
        }
    }
    public static String questTitle(Requirement r) {
        okhttp3.HttpUrl url = WikiUrls.resolve(r.url);
        return (r.kind == Requirement.Kind.QUEST || r.kind == Requirement.Kind.QUEST_STAGE) && r.verifiable && WikiUrls.wiki(url)
            && url.fragment() == null && url.querySize() == 0 ? WikiUrls.title(url) : null;
    }
    private Result result(Status status, String summary, String detail) { return new Result(status, summary, detail, List.of()); }
}
