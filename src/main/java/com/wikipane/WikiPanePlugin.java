package com.wikipane;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.wikipane.api.WikiClient;
import com.wikipane.integration.WikiButtonIntegration;
import com.wikipane.ui.WikiPanePanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.LinkBrowser;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "WikiPane", description = "Read and search the OSRS Wiki inside RuneLite",
    tags = {"wiki", "search", "quest", "item", "npc"})
public class WikiPanePlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private ItemManager items;
    @Inject private PluginManager plugins;
    @Inject private WikiPaneConfig config;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    private WikiClient wiki;
    private ExecutorService workers;
    private WikiPanePanel panel;
    private NavigationButton navigation;
    private WikiButtonIntegration integration;
    private volatile boolean active;
    private volatile long lifecycle;
    @Provides
    WikiPaneConfig configuration(ConfigManager manager) {
        return manager.getConfig(WikiPaneConfig.class);
    }
    @Override
    protected void startUp() {
        active = true;
        long version = ++lifecycle;
        workers = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(48), r -> {
            Thread thread = new Thread(r, "WikiPane-worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        wiki = new WikiClient(http, gson);
        WikiClient currentWiki = wiki;
        ExecutorService currentWorkers = workers;
        SwingUtilities.invokeLater(() -> {
            if (!active || version != lifecycle)
                return;
            panel = new WikiPanePanel(currentWiki, currentWorkers);
            navigation =
                NavigationButton.builder().tooltip("WikiPane").priority(7).icon(icon()).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
        integration = new WikiButtonIntegration(client, items, plugins, config,
            url
            -> {
                if (config.destination() == WikiPaneConfig.Destination.BROWSER) {
                    LinkBrowser.browse(url.toString());
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (active && version == lifecycle && panel != null) {
                        toolbar.openPanel(navigation);
                        panel.openEntity(url);
                    }
                });
            },
            ()
                -> SwingUtilities.invokeLater(() -> {
                if (active && version == lifecycle && panel != null) {
                    toolbar.openPanel(navigation);
                    panel.focusSearch();
                }
            }),
            message -> SwingUtilities.invokeLater(() -> {
                if (active && version == lifecycle && panel != null)
                    panel.guidance(message);
            }));
        clientThread.invokeLater(() -> {
            if (active && version == lifecycle)
                integration.reconcile();
        });
    }
    @Override
    protected void shutDown() {
        active = false;
        lifecycle++;
        WikiPanePanel oldPanel = panel;
        NavigationButton oldNavigation = navigation;
        WikiButtonIntegration old = integration;
        if (old != null)
            clientThread.invokeLater(old::stop);
        if (wiki != null)
            wiki.close();
        if (workers != null)
            workers.shutdownNow();
        SwingUtilities.invokeLater(() -> {
            if (oldPanel != null)
                oldPanel.stop();
            if (oldNavigation != null)
                toolbar.removeNavigation(oldNavigation);
            if (panel == oldPanel)
                panel = null;
            if (navigation == oldNavigation)
                navigation = null;
        });
    }
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (active)
            integration.reconcile();
    }
    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (active && event.getScriptId() == net.runelite.api.ScriptID.WIKI_ICON_UPDATE)
            integration.reconcile();
    }
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (active)
            integration.clicked(event);
    }
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (active)
            integration.menu(event);
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (active)
            integration.reconcile();
    }
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (active
            && (event.getGroup().equals(WikiPaneConfig.GROUP) || event.getGroup().equals("runelite")
                || event.getGroup().equals("wiki")))
            clientThread.invokeLater(() -> {
                if (active)
                    integration.refresh();
            });
    }
    @Subscribe public void onPluginChanged(PluginChanged event){
        if(active&&event.getPlugin() instanceof net.runelite.client.plugins.wiki.WikiPlugin){long version=lifecycle;clientThread.invokeLater(()->{if(active&&version==lifecycle)integration.refresh();});}
    }
    private BufferedImage icon() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(ColorScheme.BRAND_ORANGE);
        g.setFont(new Font(Font.SERIF, Font.BOLD, 22));
        g.drawString("W", 0, 21);
        g.dispose();
        return image;
    }
}
