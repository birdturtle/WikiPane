package com.wikipane;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.wikipane.api.WikiClient;
import com.wikipane.integration.WikiButtonIntegration;
import com.wikipane.integration.ContextMenuIntegration;
import com.wikipane.integration.WikiLookupRouter;
import com.wikipane.knowledge.KnowledgeService;
import com.wikipane.cache.ImageCache;
import com.wikipane.inspect.*;
import com.wikipane.pin.*;
import net.runelite.client.ui.overlay.OverlayManager;
import com.wikipane.ui.WikiPanePanel;
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
import net.runelite.client.util.ImageUtil;
import okhttp3.OkHttpClient;

@PluginDescriptor(name = "WikiPane", description = "Read and search the OSRS Wiki inside RuneLite",
    tags = {"wiki", "search", "quest", "item", "npc"})
public class WikiPanePlugin extends Plugin {
    @Inject private Client client;
    @Inject private net.runelite.client.ui.overlay.worldmap.WorldMapPointManager mapPoints;
    @Inject private net.runelite.client.game.SpriteManager sprites;
    private com.wikipane.integration.WikiMapIntegration maps;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private ItemManager items;
    @Inject private PluginManager plugins;
    @Inject private WikiPaneConfig config;
    @Inject private ConfigManager configManager;
    @Inject private OkHttpClient http;
    @Inject private Gson gson;
    @Inject private OverlayManager overlays;
    @Inject private net.runelite.client.input.MouseManager mouseManager;
    private final java.util.Map<PinnedOverlay, PinMouseListener> pinMouse = new java.util.HashMap<>();
    @Inject private net.runelite.client.game.SkillIconManager skillIcons;
    private com.wikipane.context.PlayerContextService playerContext;
    private volatile com.wikipane.context.PlayerContextSnapshot playerSnapshot = com.wikipane.context.PlayerContextSnapshot.unavailable(true);
    private WikiClient wiki;
    private ExecutorService workers, pinWorkers;
    private WikiPanePanel panel;
    private NavigationButton navigation;
    private InspectMouseListener inspectMouse;
    private WikiButtonIntegration integration;
    private ContextMenuIntegration contextMenus;
    private com.wikipane.integration.SkillInterfaceIntegration skillMenus;
    private QuickInspectController inspections;
    private QuickInspectOverlay inspectOverlay;
    private PinnedReferences pinned;
    private java.util.Map<Integer, PinnedOverlay> pinOverlays = java.util.Map.of();
    private volatile boolean active;
    private volatile long lifecycle;
    @Provides
    WikiPaneConfig configuration(ConfigManager manager) {
        return manager.getConfig(WikiPaneConfig.class);
    }
    @Override
    protected void startUp() {
        WikiPaneConfigMigration.apply(configManager);
        active = true;
        long version = ++lifecycle;
        com.wikipane.integration.WikiMapIntegration currentMap = new com.wikipane.integration.WikiMapIntegration(client,mapPoints,sprites,
            task -> clientThread.invokeLater(task),
            message -> SwingUtilities.invokeLater(() -> { if(active&&version==lifecycle&&panel!=null)panel.guidance(message); }));
        currentMap.sourceRequests(url -> openReference(url, version));
        maps=currentMap;
        workers = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(48), r -> {
            Thread thread = new Thread(r, "WikiPane-worker");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        wiki = new WikiClient(http, gson);
        WikiClient currentWiki = wiki;
        ExecutorService currentWorkers = workers;
        java.util.Map<Integer, PinnedOverlay> currentPinOverlays = new java.util.HashMap<>();
        pinOverlays = currentPinOverlays;
        pinWorkers = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(PinnedReferences.LIMIT), task -> {
                Thread thread = new Thread(task, "WikiPane-pin-layout");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        PinnedReferences[] pinOwner = new PinnedReferences[1];
        pinned = new PinnedReferences(pinWorkers, task -> clientThread.invokeLater(() -> {
            if (active && version == lifecycle) task.run();
        }), pin -> {
            PinnedOverlay overlay = new PinnedOverlay(this, client, pinOwner[0], pin);
            PinMouseListener listener = new PinMouseListener(
                () -> active && version == lifecycle && client.getGameState() == net.runelite.api.GameState.LOGGED_IN
                    && !client.isMenuOpen(),
                delta -> clientThread.invokeLater(() -> {
                    if (active && version == lifecycle) pinOwner[0].page(pin, delta);
                }));
            overlay.mouse(listener); pinMouse.put(overlay, listener); mouseManager.registerMouseListener(listener);
            currentPinOverlays.put(pin.slot, overlay); overlays.add(overlay);
        }, pin -> {
            PinnedOverlay overlay = currentPinOverlays.remove(pin.slot);
            if (overlay != null) {
                PinMouseListener listener = pinMouse.remove(overlay);
                if (listener != null) mouseManager.unregisterMouseListener(listener);
                overlays.remove(overlay);
            }
        }, url -> openReference(url, version), message -> SwingUtilities.invokeLater(() -> {
            if (active && version == lifecycle && panel != null) panel.guidance(message);
        }));
        pinOwner[0] = pinned;
        PinnedReferences currentPins = pinned;
        playerSnapshot = com.wikipane.context.PlayerContextSnapshot.unavailable(config.playerRequirements());
        playerContext = new com.wikipane.context.PlayerContextService(client, task -> clientThread.invokeLater(task), snapshot -> {
            if (!active || version != lifecycle) return;
            playerSnapshot = snapshot;
            SwingUtilities.invokeLater(() -> { if (active && version == lifecycle && panel != null) panel.playerContext(snapshot); });
        });
        com.wikipane.context.PlayerContextService currentPlayer = playerContext;
        SwingUtilities.invokeLater(() -> {
            if (!active || version != lifecycle)
                return;
            panel = new WikiPanePanel(currentWiki, currentWorkers, selection -> clientThread.invokeLater(() -> {
                if (active && version == lifecycle) currentPins.pin(selection);
            }));
            panel.playerContext(playerSnapshot);
            panel.autoPositionSkills(config.autoPositionSkills());
            panel.mapRequests(location -> clientThread.invokeLater(() -> {
                if(active&&version==lifecycle)currentMap.show(location);
            }));
            panel.contextRequests(requirements -> clientThread.invokeLater(() -> {
                if (active && version == lifecycle) currentPlayer.watch(requirements);
            }));
            panel.skillRequests(skill -> clientThread.invokeLater(() -> {
                if (active && version == lifecycle) currentPlayer.watchSkill(skill);
            }));
            try { currentWorkers.submit(() -> {
                java.util.Map<String, javax.swing.Icon> icons = new java.util.HashMap<>();
                for (net.runelite.api.Skill skill : net.runelite.api.Skill.values()) if (skill != net.runelite.api.Skill.OVERALL) {
                    try { icons.put(com.wikipane.context.PlayerContextSnapshot.skillKey(skill.getName()),
                        new javax.swing.ImageIcon(skillIcons.getSkillImage(skill, true))); }
                    catch (RuntimeException ex) { /* An unavailable bundled icon does not affect requirement checks. */ }
                }
                SwingUtilities.invokeLater(() -> { if (active && version == lifecycle && panel != null) panel.skillIcons(icons); });
            }); } catch (RejectedExecutionException ex) { /* Shutdown may precede optional icon preparation. */ }
            navigation =
                NavigationButton.builder()
                    .tooltip("WikiPane")
                    .icon(icon())
                    .priority(1)
                    .panel(panel)
                    .build();
            toolbar.addNavigation(navigation);
        });
        inspections = new QuickInspectController(new KnowledgeService(currentWiki), new ImageCache(currentWiki),
            currentWorkers, task -> clientThread.invokeLater(task), url -> openReference(url, version));
        inspectOverlay = new QuickInspectOverlay(this, client, inspections);
        overlays.add(inspectOverlay);
        QuickInspectOverlay currentInspect = inspectOverlay;
        inspectMouse = new InspectMouseListener(() -> active && version == lifecycle
            && client.getGameState() == net.runelite.api.GameState.LOGGED_IN && !client.isMenuOpen()
            && inspections.view() != null,
            action -> {
                QuickInspectController.View selected = inspections.view();
                clientThread.invokeLater(() -> {
                    if (active && version == lifecycle && inspections.view() == selected) currentInspect.control(action);
                });
            });
        inspectOverlay.mouse(inspectMouse); mouseManager.registerMouseListener(inspectMouse);
        contextMenus = new ContextMenuIntegration(client, items, config, url -> {
            if (!active || version != lifecycle) return;
            if (config.contextDestination() == WikiPaneConfig.ContextDestination.FULL_ARTICLE)
                openEntity(url, version);
            else inspections.inspect(url);
        });
        skillMenus = new com.wikipane.integration.SkillInterfaceIntegration(client, items, config,
            () -> active && version == lifecycle, url -> openReference(url.toString(), version),
            url -> openEntity(url, version));
        WikiLookupRouter wikiRouter = new WikiLookupRouter(url -> openEntity(url, version), () -> SwingUtilities.invokeLater(() -> {
                if (active && version == lifecycle && panel != null) {
                    toolbar.openPanel(navigation);
                    panel.focusSearch();
                }
            }));
        integration = new WikiButtonIntegration(client, items, plugins, config,
            wikiRouter::lookup, wikiRouter::search,
            message -> SwingUtilities.invokeLater(() -> {
                if (active && version == lifecycle && panel != null) {
                    toolbar.openPanel(navigation);
                    panel.guidance(message);
                }
            }));
        clientThread.invokeLater(() -> {
            if (active && version == lifecycle) {
                currentPlayer.enabled(config.playerRequirements());
                integration.reconcile();
            }
        });
    }
    @Override
    protected void shutDown() {
        active = false;
        lifecycle++;
        com.wikipane.integration.WikiMapIntegration oldMap=maps;
        if(oldMap!=null)clientThread.invokeLater(oldMap::stop);
        com.wikipane.context.PlayerContextService oldPlayer = playerContext;
        if (oldPlayer != null) clientThread.invokeLater(oldPlayer::stop);
        playerSnapshot = com.wikipane.context.PlayerContextSnapshot.unavailable(false);
        WikiPanePanel oldPanel = panel;
        NavigationButton oldNavigation = navigation;
        WikiButtonIntegration old = integration;
        if (old != null)
            clientThread.invokeLater(old::stop);
        QuickInspectController oldInspections = inspections;
        if (oldInspections != null) clientThread.invokeLater(oldInspections::stop);
        if (inspectOverlay != null) overlays.remove(inspectOverlay);
        if (inspectMouse != null) { mouseManager.unregisterMouseListener(inspectMouse); inspectMouse = null; }
        PinnedReferences oldPins = pinned;
        if (oldPins != null) clientThread.invokeLater(oldPins::stop);
        if (wiki != null)
            wiki.close();
        if (workers != null)
            workers.shutdownNow();
        if (pinWorkers != null) pinWorkers.shutdownNow();
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
    private void openReference(String url, long version) {
        SwingUtilities.invokeLater(() -> {
            if (active && version == lifecycle && panel != null) {
                toolbar.openPanel(navigation);
                panel.openLink(url);
            }
        });
    }
    private void openEntity(okhttp3.HttpUrl url, long version) {
        SwingUtilities.invokeLater(() -> {
            if (active && version == lifecycle && panel != null) {
                toolbar.openPanel(navigation);
                panel.openEntity(url);
            }
        });
    }
    @Subscribe(priority = -1)
    public void onMenuOpened(MenuOpened event) {
        if (!active) return;
        for (PinnedOverlay overlay : pinOverlays.values()) if (overlay.hovered()) return;
        if (inspectOverlay != null && inspectOverlay.opened()) return;
        if (maps != null) maps.opened(event);
        if (skillMenus != null) skillMenus.opened(event);
        if (contextMenus != null) contextMenus.opened(event);
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
        if (active) {
            if (maps != null && maps.clicked(event)) return;
            if (skillMenus != null && skillMenus.clicked(event)) return;
            if (com.wikipane.integration.QuestInterfaceLookup.clicked(client, config, event,
                    url -> openReference(url.toString(), lifecycle))) return;
            integration.clicked(event);
        }
    }
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (active)
            integration.menu(event);
    }
    @Subscribe public void onStatChanged(StatChanged event) { if (active && playerContext != null) playerContext.statChanged(); }
    @Subscribe public void onVarbitChanged(VarbitChanged event) { if (active && playerContext != null) playerContext.variablesChanged(); }
    @Subscribe public void onGameTick(GameTick event) {
        if (active && playerContext != null) playerContext.tick();
        if (active && maps != null) maps.center();
        if (active && integration != null) integration.reconcile();
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (active) {
            if (playerContext != null) playerContext.stateChanged(event.getGameState());
            if(maps!=null&&(event.getGameState()==net.runelite.api.GameState.LOGIN_SCREEN
                ||event.getGameState()==net.runelite.api.GameState.HOPPING))maps.clear();
            integration.reconcile();
            if (inspections != null && (event.getGameState() == net.runelite.api.GameState.LOGIN_SCREEN
                || event.getGameState() == net.runelite.api.GameState.HOPPING
                || event.getGameState() == net.runelite.api.GameState.CONNECTION_LOST)) inspections.dismiss();
        }
    }
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!active || !(event.getGroup().equals(WikiPaneConfig.GROUP)
            || event.getGroup().equals("runelite") || event.getGroup().equals("wiki"))) return;
        long version = lifecycle;
        boolean contextOnly = event.getGroup().equals(WikiPaneConfig.GROUP)
            && event.getKey().startsWith("context");
        clientThread.invokeLater(() -> {
            if (!active || version != lifecycle) return;
            if (event.getGroup().equals(WikiPaneConfig.GROUP) && event.getKey().equals("autoPositionSkills")) {
                boolean enabled = config.autoPositionSkills();
                SwingUtilities.invokeLater(() -> { if (active && version == lifecycle && panel != null) panel.autoPositionSkills(enabled); });
                return;
            }
            if (event.getGroup().equals(WikiPaneConfig.GROUP) && event.getKey().equals("playerRequirements")) {
                if (playerContext != null) playerContext.enabled(config.playerRequirements());
                return;
            }
            if (!contextOnly) integration.refresh();
            if (!config.contextLookups() && inspections != null) inspections.dismiss();
        });
    }
    @Subscribe public void onProfileChanged(net.runelite.client.events.ProfileChanged event) {
        if (!active) return;
        WikiPaneConfigMigration.apply(configManager);
        long version = lifecycle;
        clientThread.invokeLater(() -> {
            if (active && version == lifecycle) {
                integration.refresh();
                boolean autoPosition = config.autoPositionSkills();
                SwingUtilities.invokeLater(() -> { if (active && version == lifecycle && panel != null) panel.autoPositionSkills(autoPosition); });
                if (playerContext != null) playerContext.enabled(config.playerRequirements());
            }
        });
    }
    @Subscribe public void onPluginChanged(PluginChanged event){
        if(active&&event.getPlugin() instanceof net.runelite.client.plugins.wiki.WikiPlugin){long version=lifecycle;clientThread.invokeLater(()->{if(active&&version==lifecycle)integration.refresh();});}
    }
    private BufferedImage icon() {
        return ImageUtil.loadImageResource(WikiPanePlugin.class, "icon.png");
    }
}
