package com.wikipane;

import net.runelite.client.config.ConfigManager;

/** Migrate only WikiPane preferences; never change core Wiki settings. */
final class WikiPaneConfigMigration {
    static void apply(ConfigManager manager) {
        String group = WikiPaneConfig.GROUP;
        String revision = manager.getConfiguration(group, "settingsRevision");
        if ("2".equals(revision)) return;
        if (!"1".equals(revision)) {
            if (Boolean.parseBoolean(manager.getConfiguration(group, "questLookups"))
                || Boolean.parseBoolean(manager.getConfiguration(group, "skillLookups")))
                manager.setConfiguration(group, "replacement", true);
            manager.unsetConfiguration(group, "questLookups");
            manager.unsetConfiguration(group, "skillLookups");
            manager.unsetConfiguration(group, "destination");
        }
        // The minimap takeover was removed. Keep the existing quest/skill opt-in under its stable key.
        manager.unsetConfiguration(group, "leftSearch");
        manager.setConfiguration(group, "settingsRevision", "2");
    }
}
