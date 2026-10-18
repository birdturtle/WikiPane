package com.wikipane;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.mockito.Mockito.*;
public class WikiPaneConfigMigrationTest {
    @Test public void legacyRoutingOptInsConsolidateOnceWithoutChangingCoreWikiSettings() {
        ConfigManager manager=mock(ConfigManager.class);
        when(manager.getConfiguration("wikipane","questLookups")).thenReturn("true");
        WikiPaneConfigMigration.apply(manager);
        verify(manager).setConfiguration("wikipane","replacement",true);
        verify(manager).unsetConfiguration("wikipane","destination");
        verify(manager).setConfiguration("wikipane","settingsRevision","1");
        verify(manager,never()).setConfiguration(eq("wiki"),anyString(),any());
        clearInvocations(manager);
        when(manager.getConfiguration("wikipane","settingsRevision")).thenReturn("1");
        WikiPaneConfigMigration.apply(manager);
        verify(manager,never()).setConfiguration(anyString(),anyString(),any());
    }
    @Test public void freshInstallDoesNotOptIntoNetworkIntegration() {
        ConfigManager manager=mock(ConfigManager.class); WikiPaneConfigMigration.apply(manager);
        verify(manager,never()).setConfiguration("wikipane","replacement",true);
    }
}
