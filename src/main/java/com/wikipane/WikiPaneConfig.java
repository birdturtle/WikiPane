package com.wikipane;

import net.runelite.client.config.*;

@ConfigGroup(WikiPaneConfig.GROUP)
public interface WikiPaneConfig extends Config {
    String GROUP = "wikipane";
    @ConfigItem(keyName = "replacement", name = "Replace Wiki button",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by "
            + "RuneLite developers",
        description = "Disable RuneLite's core Wiki plugin first. WikiPane does not provide its DPS tool.",
        position = 0)
    default boolean
    replacement() {
        return false;
    }
    @ConfigItem(keyName = "destination", name = "Lookup destination",
        description = "Where to open entity lookups", position = 1)
    default Destination
    destination() {
        return Destination.SIDEBAR;
    }
    @ConfigItem(keyName = "leftSearch", name = "Left-click Search",
        description = "Search instead of selecting an entity with the Wiki button", position = 2)
    default boolean
    leftSearch() {
        return false;
    }
    enum Destination { SIDEBAR, BROWSER }
}
