package com.wikipane.context;

import com.wikipane.semantic.SemanticDocument.Requirement;
import java.util.*;
import java.util.function.Consumer;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import lombok.extern.slf4j.Slf4j;

/** Client-thread read-only adapter. Requests are bounded to quests in the current reader. */
@Slf4j
public final class PlayerContextService {
    private final Client client;
    private final Consumer<Runnable> later;
    private final Consumer<PlayerContextSnapshot> publish;
    private final Map<String, Quest> catalog = new HashMap<>();
    private Set<Quest> requested = Set.of();
    private boolean enabled = true, watching, ready, dirty = true, questsDirty = true, queued, stopped;
    private int tick, nextQuestTick;
    private Map<String, PlayerContextSnapshot.QuestState> questStates = Map.of();
    private PlayerContextSnapshot last;
    public PlayerContextService(Client client, Consumer<Runnable> later, Consumer<PlayerContextSnapshot> publish) {
        this.client = client; this.later = later; this.publish = publish;
        for (Quest quest : Quest.values()) catalog.put(PlayerContextSnapshot.key(quest.getName()), quest);
    }
    private void checkThread() { if (!client.isClientThread()) throw new IllegalStateException("Player state requires the client thread"); }
    public void watch(List<Requirement> requirements) {
        checkThread(); if (stopped) return;
        Set<Quest> quests = new LinkedHashSet<>();
        int[] budget = {2048};
        for (Requirement requirement : requirements) collect(requirement, quests, budget, 0);
        watching = !requirements.isEmpty(); requested = Set.copyOf(quests);
        questStates = Map.of(); dirty = questsDirty = true; nextQuestTick = tick;
        if (!watching) emit(PlayerContextSnapshot.unavailable(enabled));
        else queue();
    }
    private void collect(Requirement r, Set<Quest> quests, int[] budget, int depth) {
        if (--budget[0] < 0 || depth > 32 || quests.size() >= 64) return;
        String title = RequirementEvaluator.questTitle(r);
        Quest quest = title == null ? null : catalog.get(PlayerContextSnapshot.key(title));
        if (quest != null) quests.add(quest);
        for (Requirement child : r.children) collect(child, quests, budget, depth + 1);
    }
    public void enabled(boolean enabled) {
        checkThread(); if (stopped) return;
        this.enabled = enabled; questStates = Map.of(); dirty = questsDirty = true; nextQuestTick = tick;
        emit(PlayerContextSnapshot.unavailable(enabled)); if (enabled) queue();
    }
    public void stateChanged(GameState state) {
        checkThread(); if (stopped) return;
        ready = false; questStates = Map.of(); dirty = questsDirty = true; nextQuestTick = tick;
        emit(PlayerContextSnapshot.unavailable(enabled));
    }
    public void statChanged() { checkThread(); if (!stopped) dirty = true; }
    public void variablesChanged() { checkThread(); if (!stopped) { dirty = questsDirty = true; } }
    public void tick() {
        checkThread(); if (stopped) return;
        tick++;
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        ready = true; queue();
    }
    private void queue() {
        if (stopped || queued || !enabled || !watching || !ready || !dirty && (!questsDirty || tick < nextQuestTick)) return;
        queued = true;
        later.accept(() -> {
            queued = false;
            if (stopped || !enabled || !watching || !ready || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
            checkThread(); capture();
        });
    }
    private void capture() {
        Map<String, PlayerContextSnapshot.Level> skills = new HashMap<>();
        for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) {
            try {
                int base = client.getRealSkillLevel(skill), current = client.getBoostedSkillLevel(skill);
                if (base > 0 && current >= 0) skills.put(skill.getName(), new PlayerContextSnapshot.Level(base, current));
            } catch (RuntimeException ex) { log.debug("WikiPane skill state unavailable for {}", skill, ex); }
        }
        Integer points = null;
        try { int qp = client.getVarpValue(VarPlayerID.QP); if (qp >= 0) points = qp; }
        catch (RuntimeException ex) { log.debug("WikiPane quest points unavailable", ex); }
        if (questsDirty && tick >= nextQuestTick) {
            Map<String, PlayerContextSnapshot.QuestState> states = new HashMap<>();
            boolean failed = false;
            for (Quest quest : requested) {
                try {
                    QuestState state = quest.getState(client);
                    if (state != null) states.put(quest.getName(), state == QuestState.FINISHED
                        ? PlayerContextSnapshot.QuestState.COMPLETE : PlayerContextSnapshot.QuestState.INCOMPLETE);
                } catch (RuntimeException ex) { failed = true; log.debug("WikiPane quest state unavailable for {}", quest.getName(), ex); }
            }
            questStates = Map.copyOf(states); questsDirty = failed; nextQuestTick = tick + 5;
        }
        dirty = false;
        emit(new PlayerContextSnapshot(true, true, skills, questStates, points));
    }
    private void emit(PlayerContextSnapshot snapshot) {
        if (!snapshot.equals(last)) { last = snapshot; publish.accept(snapshot); }
    }
    public void stop() { checkThread(); stopped = true; requested = Set.of(); questStates = Map.of(); watching = false; emit(PlayerContextSnapshot.unavailable(false)); }
}
