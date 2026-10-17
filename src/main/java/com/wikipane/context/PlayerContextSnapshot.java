package com.wikipane.context;

import java.util.*;

/** Immutable local state. No account identifiers, inventory, networking or Wiki cache ownership. */
public final class PlayerContextSnapshot {
    public static final class Level {
        public final int base, current;
        public Level(int base, int current) { this.base = base; this.current = current; }
        @Override public boolean equals(Object other) { return other instanceof Level && ((Level)other).base == base && ((Level)other).current == current; }
        @Override public int hashCode() { return Objects.hash(base, current); }
    }
    public enum QuestState { COMPLETE, INCOMPLETE }
    public final boolean enabled, available;
    public final Map<String, Level> levels;
    public final Map<String, QuestState> quests;
    public final Integer questPoints;
    public PlayerContextSnapshot(boolean enabled, boolean available, Map<String, Level> levels,
                                 Map<String, QuestState> quests, Integer questPoints) {
        this.enabled = enabled; this.available = available;
        Map<String, Level> skills = new HashMap<>(); levels.forEach((name, value) -> skills.put(skillKey(name), value));
        Map<String, QuestState> states = new HashMap<>(); quests.forEach((name, value) -> states.put(key(name), value));
        this.levels = Map.copyOf(skills); this.quests = Map.copyOf(states); this.questPoints = questPoints;
    }
    public static PlayerContextSnapshot unavailable(boolean enabled) { return new PlayerContextSnapshot(enabled, false, Map.of(), Map.of(), null); }
    public static String key(String name) { return name.replace('_', ' ').replace('\u2019', '\'').replace('\u00a0', ' ').strip().toLowerCase(Locale.ROOT); }
    public static String skillKey(String name) { String key = key(name); return key.equals("runecrafting") ? "runecraft" : key; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof PlayerContextSnapshot)) return false;
        PlayerContextSnapshot s = (PlayerContextSnapshot)other;
        return enabled == s.enabled && available == s.available && levels.equals(s.levels) && quests.equals(s.quests) && Objects.equals(questPoints, s.questPoints);
    }
    @Override public int hashCode() { return Objects.hash(enabled, available, levels, quests, questPoints); }
}
