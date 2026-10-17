package com.wikipane;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
public class WikiPanePluginTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(WikiPanePlugin.class);
        RuneLite.main(args);
    }
}
