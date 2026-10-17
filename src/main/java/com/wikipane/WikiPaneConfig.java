package com.wikipane;

import net.runelite.client.config.*;

@ConfigGroup(WikiPaneConfig.GROUP)
public interface WikiPaneConfig extends Config {
    String GROUP = "wikipane";
    @ConfigItem(keyName = "replacement", name = "Replace Wiki button",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by "
            + "RuneLite developers",
        description = "Disable RuneLite's core Wiki plugin first. Sidebar destination also routes quest-list Wiki guides. WikiPane does not provide the DPS tool.",
        position = 0)
    default boolean
    replacement() {
        return false;
    }
    @ConfigItem(keyName = "destination", name = "Wiki button destination",
        description = "Entity lookups open in WikiPane sidebar or an external browser. Search always opens WikiPane.", position = 1)
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
    @ConfigItem(keyName = "contextLookups", name = "Contextual WikiPane lookup", position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        description = "Add local WikiPane actions for items, NPCs and objects. Lookups are sent to the OSRS Wiki.")
    default boolean contextLookups() { return false; }
    @ConfigItem(keyName = "contextAccess", name = "Context menu access", position = 4,
        description = "Shift-right-click keeps ordinary menus clear; right-click shows the entry with or without Shift.")
    default ContextAccess contextAccess() { return ContextAccess.SHIFT_RIGHT_CLICK; }
    @ConfigItem(keyName = "contextDestination", name = "Context lookup destination", position = 5,
        description = "Quick Inspect keeps your current sidebar article and reading position.")
    default ContextDestination contextDestination() { return ContextDestination.QUICK_INSPECT; }
    @ConfigItem(keyName = "playerRequirements", name = "Check your requirements", position = 6,
        description = "Compare supported skills, quest completion and quest points locally. No player state is sent to the Wiki.")
    default boolean playerRequirements() { return true; }
    @ConfigItem(keyName = "questLookups", name = "Quest guides in WikiPane", position = 7,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        description = "Also route quest-list guides into the sidebar without replacing the Wiki button. Replacement with Sidebar destination already includes this.")
    default boolean questLookups() { return false; }
    enum ContextAccess {
        SHIFT_RIGHT_CLICK("Shift-right-click"), RIGHT_CLICK("Right-click");
        private final String label;
        ContextAccess(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    enum ContextDestination {
        QUICK_INSPECT("Quick Inspect"), FULL_ARTICLE("Full article");
        private final String label;
        ContextDestination(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    enum Destination {
        SIDEBAR("WikiPane sidebar"), BROWSER("External browser");
        private final String label;
        Destination(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
}
