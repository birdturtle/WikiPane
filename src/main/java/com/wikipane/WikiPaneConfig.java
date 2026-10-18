package com.wikipane;

import net.runelite.client.config.*;

@ConfigGroup(WikiPaneConfig.GROUP)
public interface WikiPaneConfig extends Config {
    String GROUP = "wikipane";
    @ConfigSection(name = "Wiki integration", description = "Use the sidebar for existing game Wiki actions", position = 0)
    String INTEGRATION = "integration";
    @ConfigSection(name = "Right-click lookup", description = "Optional direct lookup from game entities", position = 1)
    String CONTEXT = "context";
    @ConfigSection(name = "Your character", description = "Local requirement and skill-guide annotations", position = 2)
    String CHARACTER = "character";

    @ConfigItem(keyName = "replacement", name = "Open Wiki links in WikiPane", section = INTEGRATION,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        description = "Open minimap Wiki lookups, searches, quest guides and skill Wiki links in the sidebar. Works with RuneLite's Wiki plugin and retains its DPS action.", position = 0)
    default boolean replacement() { return false; }
    @ConfigItem(keyName = "leftSearch", name = "Wiki button: left-click search", section = INTEGRATION,
        description = "When integration is enabled: checked opens search; unchecked selects an entity to look up.", position = 1)
    default boolean leftSearch() { return false; }
    @ConfigItem(keyName = "contextLookups", name = "Add WikiPane to entity menus", section = CONTEXT, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        description = "Add an informational lookup to item, NPC and object menus. Lookups are sent to the OSRS Wiki.")
    default boolean contextLookups() { return false; }
    @ConfigItem(keyName = "contextAccess", name = "Entity menus: show on", section = CONTEXT, position = 1,
        description = "Applies when entity menus are enabled. Shift-right-click keeps ordinary menus clear; right-click always shows the entry.")
    default ContextAccess contextAccess() { return ContextAccess.SHIFT_RIGHT_CLICK; }
    @ConfigItem(keyName = "contextDestination", name = "Entity menus: open in", section = CONTEXT, position = 2,
        description = "Applies when entity menus are enabled. Full article opens the sidebar. Quick Inspect opens a temporary overlay without changing your article.")
    default ContextDestination contextDestination() { return ContextDestination.FULL_ARTICLE; }
    @ConfigItem(keyName = "playerRequirements", name = "Show character checks", section = CHARACTER, position = 0,
        description = "Show met/unmet/unknown requirement indicators and your level in skill guides. Comparisons stay local; turning this off leaves the original Wiki information.")
    default boolean playerRequirements() { return true; }

    @ConfigItem(keyName = "autoPositionSkills", name = "Jump to your skill level", section = CHARACTER, position = 1,
        description = "With character checks enabled, initially position skill level guides at your base level. Turning this off keeps level dimming and requirement checks.")
    default boolean autoPositionSkills() { return true; }

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
}
