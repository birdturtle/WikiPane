package com.wikipane.context;

import com.wikipane.view.ReaderLayout;
import java.util.Collection;
import net.runelite.api.Skill;

/** Local display context for identified skill level-up articles; never part of cached Wiki facts. */
public final class SkillGuideProgress {
    private SkillGuideProgress() {}
    public static String skill(String title) {
        String suffix = "/Level up table";
        if (!title.endsWith(suffix)) return "";
        String name = title.substring(0, title.length() - suffix.length());
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL
            && PlayerContextSnapshot.skillKey(skill.getName()).equals(PlayerContextSnapshot.skillKey(name)))
            return skill.getName();
        return "";
    }
    public static Integer level(ReaderLayout.Record row) {
        if (row.identity == null || !row.identity.label.equalsIgnoreCase("Level")) return null;
        try {
            int value = Integer.parseInt(row.identity.value.text);
            return value > 0 && value <= 126 ? value : null;
        } catch (NumberFormatException ex) { return null; }
    }
    public static Integer base(String skill, PlayerContextSnapshot player) {
        if (skill.isEmpty() || !player.enabled || !player.available) return null;
        PlayerContextSnapshot.Level level = player.levels.get(PlayerContextSnapshot.skillKey(skill));
        return level == null ? null : level.base;
    }
    public static Integer target(Collection<Integer> levels, int base) {
        return levels.stream().filter(level -> level >= base).min(Integer::compareTo)
            .orElseGet(() -> levels.stream().max(Integer::compareTo).orElse(null));
    }
}
