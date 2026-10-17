package com.wikipane.context;

import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.Requirement;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlayerContextServiceTest {
    private static final class Session {
        final Client client=mock(Client.class);
        final Queue<Runnable> queue=new ArrayDeque<>();
        final List<PlayerContextSnapshot> snapshots=new ArrayList<>();
        final PlayerContextService service=new PlayerContextService(client,queue::add,snapshots::add);
        Session() {
            when(client.isClientThread()).thenReturn(true);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getRealSkillLevel(Skill.COOKING)).thenReturn(42);when(client.getBoostedSkillLevel(Skill.COOKING)).thenReturn(45);
            when(client.getVarpValue(VarPlayerID.QP)).thenReturn(100);when(client.getIntStack()).thenReturn(new int[]{2});
        }
        void flush() {while(!queue.isEmpty())queue.remove().run();}
        void tick() {service.tick();flush();}
        PlayerContextSnapshot last() {return snapshots.get(snapshots.size()-1);}
        void watch() {service.watch(List.of(new Requirement(Requirement.Kind.QUEST,"Cook's Assistant","https://oldschool.runescape.wiki/w/Cook%27s_Assistant",null,null,Requirement.Boost.UNSPECIFIED,SemanticExtractor.rich("Completion of Cook's Assistant"),List.of())));}
    }
    @Test public void waitsForGameTickAndReadsOnlyRequestedQuestsOnClientThread() {
        Session s=new Session();s.watch();s.flush();assertTrue(s.snapshots.isEmpty());
        s.tick();assertTrue(s.last().available);assertEquals(42,s.last().levels.get("cooking").base);assertEquals(Integer.valueOf(100),s.last().questPoints);
        assertEquals(PlayerContextSnapshot.QuestState.COMPLETE,s.last().quests.get("cook's assistant"));
        assertEquals(1,s.last().quests.size());verify(s.client,times(1)).getIntStack();
        verify(s.client,never()).getUsername();verify(s.client,never()).getAccountHash();
    }
    @Test public void coalescesVariableBurstsAndBoundsQuestRefreshFrequency() {
        Session s=new Session();s.watch();s.tick();
        for(int i=0;i<100;i++)s.service.variablesChanged();
        assertTrue(s.queue.isEmpty());s.tick();verify(s.client,times(1)).getIntStack();
        for(int i=0;i<4;i++)s.tick();verify(s.client,times(2)).getIntStack();
        assertEquals("Unchanged snapshots should not redraw annotations",1,s.snapshots.size());
    }
    @Test public void logoutClearsOldLevelsAndQuestsBeforeAnotherLogin() {
        Session s=new Session();s.watch();s.tick();
        when(s.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);s.service.stateChanged(GameState.LOGIN_SCREEN);
        assertFalse(s.last().available);assertTrue(s.last().levels.isEmpty());assertTrue(s.last().quests.isEmpty());
        when(s.client.getRealSkillLevel(Skill.COOKING)).thenReturn(20);when(s.client.getIntStack()).thenReturn(new int[]{1});
        when(s.client.getGameState()).thenReturn(GameState.LOGGED_IN);s.service.stateChanged(GameState.LOGGED_IN);
        assertFalse(s.last().available);s.tick();assertEquals(20,s.last().levels.get("cooking").base);
        assertEquals(PlayerContextSnapshot.QuestState.INCOMPLETE,s.last().quests.get("cook's assistant"));
    }
    @Test public void shutdownDisableAndClosingReaderPreventPendingReads() {
        Session s=new Session();s.watch();s.service.tick();assertFalse(s.queue.isEmpty());
        s.service.stop();s.flush();assertFalse(s.last().enabled);verify(s.client,never()).getIntStack();
        Session disabled=new Session();disabled.watch();disabled.service.enabled(false);disabled.tick();verify(disabled.client,never()).getIntStack();
        Session closed=new Session();closed.watch();closed.tick();closed.service.watch(List.of());closed.service.variablesChanged();closed.tick();
        verify(closed.client,times(1)).getIntStack();assertFalse(closed.last().available);
    }
    @Test public void wrongThreadIsRejectedBeforeReadingPlayerState() {
        Session s=new Session();when(s.client.isClientThread()).thenReturn(false);
        try{s.service.tick();fail("Wrong thread accepted");}catch(IllegalStateException expected){}
        verify(s.client,never()).getRealSkillLevel(any());verify(s.client,never()).getIntStack();
    }
    @Test public void unavailableQuestScriptStaysUnknownAndCanRecoverWithoutWikiRequests() {
        Session s=new Session();when(s.client.getIntStack()).thenReturn(null);s.watch();s.tick();assertTrue(s.last().quests.isEmpty());
        when(s.client.getIntStack()).thenReturn(new int[]{2});for(int i=0;i<5;i++)s.tick();
        assertEquals(PlayerContextSnapshot.QuestState.COMPLETE,s.last().quests.get("cook's assistant"));
    }
    @Test public void skillGuideCanWatchLevelsWithoutInventingQuestRequirements() {
        Session s = new Session(); s.service.watchSkill("Cooking"); s.tick();
        assertTrue(s.last().available); assertEquals(42, s.last().levels.get("cooking").base);
        assertTrue(s.last().quests.isEmpty()); verify(s.client, never()).getIntStack();
        s.service.watch(List.of()); s.tick(); assertTrue(s.last().available);
        clearInvocations(s.client);
        s.service.watchSkill(""); assertFalse(s.last().available);
        s.service.statChanged(); s.tick(); verify(s.client, never()).getRealSkillLevel(Skill.COOKING);
        s.service.watchSkill("Unknown skill"); s.tick(); assertFalse(s.last().available);
    }
}
