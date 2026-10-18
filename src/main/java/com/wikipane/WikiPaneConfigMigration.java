package com.wikipane;

import net.runelite.client.config.ConfigManager;

/** Consolidate the old overlapping routing switches once per RuneLite profile. */
final class WikiPaneConfigMigration {
    static void apply(ConfigManager manager) {
        String group = WikiPaneConfig.GROUP;
        if ("1".equals(manager.getConfiguration(group, "settingsRevision"))) return;
        if (Boolean.parseBoolean(manager.getConfiguration(group, "questLookups"))
            || Boolean.parseBoolean(manager.getConfiguration(group, "skillLookups")))
            manager.setConfiguration(group, "replacement", true);
        manager.unsetConfiguration(group, "questLookups");
        manager.unsetConfiguration(group, "skillLookups");
        manager.unsetConfiguration(group, "destination");
        manager.setConfiguration(group, "settingsRevision", "1");
    }
}
