package com.wikipane.ui;

import com.wikipane.api.*;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.navigation.NavigationHistory;
import com.wikipane.view.ArticleNavigation;
import com.wikipane.view.ArticleViewModel;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.event.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.LinkBrowser;
import okhttp3.HttpUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WikiPanePanel extends PluginPanel {
    private static final Logger log = LoggerFactory.getLogger(WikiPanePanel.class);
    private final WikiClient wiki;
    private final ExecutorService workers;
    private final ArticleReader renderer;
    private final ImageCache images;
    private final JButton mapButton=button("Map ▾","Article maps and locations");
    private final JPopupMenu mapPopup=new JPopupMenu();
    private MapChooser mapChooser;
    private java.util.function.Consumer<com.wikipane.view.MapDestination> mapRequests=ignored->{};
    private java.util.function.Consumer<java.util.List<com.wikipane.semantic.SemanticDocument.Requirement>> contextRequests = requirements -> {};
    private java.util.List<com.wikipane.semantic.SemanticDocument.Requirement> contextRequirements = java.util.List.of();
    private boolean contextVisible;
    private String contextSkill = "";
    private java.util.function.Consumer<String> skillRequests = skill -> {};
    private NavigationHistory.Entry pendingSkillPosition;
    public void skillRequests(java.util.function.Consumer<String> requests) { skillRequests = requests; }
    public void mapRequests(java.util.function.Consumer<com.wikipane.view.MapDestination> show) { mapRequests=show;renderer.mapRequests(show); }
    public void contextRequests(java.util.function.Consumer<java.util.List<com.wikipane.semantic.SemanticDocument.Requirement>> requests) { contextRequests = requests; }
    public void playerContext(com.wikipane.context.PlayerContextSnapshot snapshot) {
        if (active) { renderer.playerContext(snapshot); positionSkillGuide(); }
    }
    public void skillIcons(java.util.Map<String, javax.swing.Icon> icons) { if (active) renderer.skillIcons(icons); }
    @Override public void onActivate() { contextVisible = true; contextRequests.accept(contextRequirements); skillRequests.accept(contextSkill); }
    @Override public void onDeactivate() { contextVisible = false; contextRequests.accept(java.util.List.of()); skillRequests.accept(""); }
    private final NavigationHistory history = new NavigationHistory();
    private final JTextField search = new JTextField();
    private final JEditorPane message = new ReaderComponents(url -> {}).text("Search the OSRS Wiki");
    private final JPanel recovery = new JPanel(new GridLayout(0, 1, 0, 3));
    private final JButton retry = button("Retry", "Retry this request"), failedBrowser = button("Open in browser", "Open the failed request in browser");
    private Runnable retryAction;
    private String failedUrl, displayedSection = "";
    private boolean sectionUpdateQueued;
    private boolean autoPositionSkills = true;
    public void autoPositionSkills(boolean enabled) { autoPositionSkills = enabled; if (!enabled) cancelSkillPosition(); }
    private final JButton back = button("", "Back"), forward = button("", "Forward"),
                          searchToggle = button("", "Search"), browser = button("", "Open in browser"),
                          sectionsButton = button("Overview", "Jump to section");
    private final JPanel content = new ReaderViewport();
    private final JScrollPane scroll = new JScrollPane(content) {
        @Override protected boolean processKeyBinding(KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
            if (pressed && java.util.Set.of(KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_UP,
                    KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END).contains(event.getKeyCode()))
                cancelSkillPosition();
            return super.processKeyBinding(stroke, event, condition, pressed);
        }
    };
    private final JComboBox<String> variants = new JComboBox<>();
    private final JPanel readingTools = new JPanel(new BorderLayout(0, 5));
    private final JPanel searchTools = new JPanel(new BorderLayout(0, 5));
    private final JPanel statePanel = new JPanel(new CardLayout()) {
        @Override
        public Dimension getPreferredSize() {
            for (Component component : getComponents())
                if (component.isVisible())
                    return component.getPreferredSize();
            return super.getPreferredSize();
        }
    };
    private final JPanel identity = ContentRenderer.column();
    private final JEditorPane articleTitle = new ReaderComponents(this::openLink).text("");
    private final JEditorPane metadata = new ReaderComponents(this::openLink).text("");
    private final JPopupMenu sectionPopup = new JPopupMenu();
    private String lastQuery = "";
    private List<String> lastTitles = List.of();
    private Integer lastSearchOffset;
    private boolean lastSuggestions;
    private boolean restoringSearch;
    private final javax.swing.Timer debounce;
    private long request, searchRequest;
    private boolean active = true, settingSections, viewingArticle;
    private final JProgressBar progress = new JProgressBar();
    private String fallbackUrl = WikiUrls.BASE.toString();
    private JPanel results = ContentRenderer.column();
    public WikiPanePanel(WikiClient wiki, ExecutorService workers) {
        this(wiki, workers, null);
    }
    public WikiPanePanel(WikiClient wiki, ExecutorService workers,
                         java.util.function.Consumer<com.wikipane.pin.PinSelection> pin) {
        super(false);
        this.wiki = wiki;
        this.workers = workers;
        mapPopup.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {if(mapChooser!=null)mapChooser.resume();}
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {if(mapChooser!=null)mapChooser.pause();}
            public void popupMenuCanceled(PopupMenuEvent event) {if(mapChooser!=null)mapChooser.pause();}
        });
        images=new ImageCache(wiki);
        renderer = new ArticleReader(this::openLink, workers, images, pin, this::guidance);
        renderer.changed(this::scheduleSectionLabel);
        renderer.contextRequests(requirements -> {
            contextRequirements = requirements;
            contextRequests.accept(contextVisible ? requirements : java.util.List.of());
        });
        renderer.skillRequests(skill -> { contextSkill = skill; skillRequests.accept(contextVisible ? skill : ""); });
        setLayout(new BorderLayout(0, 6));
        setBackground(ReaderComponents.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel top = new JPanel(new BorderLayout(0, 5));
        top.setOpaque(false);
        JPanel toolbar = new JPanel(new GridLayout(1, 4, 4, 0));
        toolbar.setOpaque(false);
        toolbar.setName("wiki.globalNavigation");
        back.setIcon(new NavigationIcons(NavigationIcons.Action.BACK));
        forward.setIcon(new NavigationIcons(NavigationIcons.Action.FORWARD));
        searchToggle.setIcon(new NavigationIcons(NavigationIcons.Action.SEARCH));
        browser.setIcon(new NavigationIcons(NavigationIcons.Action.BROWSER));
        for (JButton control : new JButton[] {back, forward, searchToggle, browser}) {
            control.setPreferredSize(new Dimension(32, 26));
            toolbar.add(control);
        }
        top.add(toolbar, BorderLayout.NORTH);
        JLabel brand = new JLabel("WikiPane");
        brand.setForeground(ColorScheme.BRAND_ORANGE);
        failedBrowser.setForeground(ReaderComponents.ACCENT);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 12f));
        searchTools.setOpaque(false);
        searchTools.add(brand, BorderLayout.NORTH);
        search.setName("wiki.search");
        search.setToolTipText("Searches and lookups are sent to the OSRS Wiki");
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(search, BorderLayout.CENTER);
        JButton searchButton = button("Search", "Search the OSRS Wiki");
        searchButton.addActionListener(e -> search.postActionEvent());
        searchRow.add(searchButton, BorderLayout.EAST);
        searchTools.add(searchRow, BorderLayout.CENTER);
        articleTitle.setName("wiki.articleTitle");
        metadata.setName("wiki.articleMetadata");
        identity.add(articleTitle);
        identity.add(metadata);
        readingTools.setOpaque(false);
        readingTools.add(identity, BorderLayout.NORTH);
        JPanel contextual = ContentRenderer.column();
        variants.setToolTipText("Choose item or monster variant");
        variants.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, "", index, selected, focus);
                label.putClientProperty("html.disable", true);
                label.setText(value == null ? "" : value.toString());
                return label;
            }
        });
        variants.setAlignmentX(Component.LEFT_ALIGNMENT);
        contextual.add(variants);
        sectionsButton.setName("wiki.currentSection");
        sectionsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionsButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        sectionsButton.setMargin(new Insets(4,8,4,8));
        sectionsButton.putClientProperty("html.disable", true);
        sectionsButton.setHorizontalAlignment(SwingConstants.LEFT);
        sectionsButton.setHorizontalTextPosition(SwingConstants.LEFT);
        sectionsButton.setIconTextGap(8);
        sectionsButton.setIcon(new Icon() {
            public int getIconWidth() { return 8; }
            public int getIconHeight() { return 6; }
            public void paintIcon(Component c, Graphics g, int x, int y) {
                x = Math.max(0, c.getWidth() - ((JComponent)c).getInsets().right - getIconWidth());
                g.setColor(ReaderComponents.MUTED);
                g.fillPolygon(new int[]{x,x+8,x+4},new int[]{y,y,y+5},3);
            }
        });
        sectionsButton.setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        sectionsButton.setPreferredSize(new Dimension(180,28));
        sectionsButton.setComponentPopupMenu(sectionPopup);
        JPanel destinations=new JPanel(new BorderLayout(4,0));destinations.setOpaque(false);
        destinations.add(sectionsButton,BorderLayout.CENTER);
        mapButton.setName("wiki.articleMap");mapButton.setForeground(ReaderComponents.ACCENT);
        mapButton.setMargin(new Insets(4,6,4,6));mapButton.setVisible(false);
        PopupToggle.bind(mapButton,mapPopup,this::openMaps);destinations.add(mapButton,BorderLayout.EAST);
        contextual.add(destinations);
        readingTools.add(contextual, BorderLayout.CENTER);
        statePanel.setOpaque(false);
        statePanel.add(searchTools, "search");
        statePanel.add(readingTools, "article");
        top.add(statePanel, BorderLayout.CENTER);
        JPopupMenu diagnostics = new JPopupMenu();
        JMenuItem auditItem = new JMenuItem("Composition audit");
        auditItem.addActionListener(e -> {
            JTextArea report = new JTextArea(compositionAudit(), 18, 46);
            report.setEditable(false);
            report.setLineWrap(true);
            report.setWrapStyleWord(true);
            report.setCaretPosition(0);
            JOptionPane.showMessageDialog(
                this, new JScrollPane(report), "WikiPane composition audit", JOptionPane.INFORMATION_MESSAGE);
        });
        diagnostics.add(auditItem);
        brand.setComponentPopupMenu(diagnostics);
        articleTitle.setComponentPopupMenu(diagnostics);
        message.putClientProperty("html.disable", true);
        message.setForeground(ReaderComponents.MUTED);
        message.setFont(message.getFont().deriveFont(11f));
        JPanel feedback = new JPanel(new BorderLayout(0, 3));
        feedback.setOpaque(false);
        feedback.add(message, BorderLayout.CENTER);
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(190, 3));
        progress.setForeground(ColorScheme.BRAND_ORANGE);
        progress.setVisible(false);
        JPanel feedbackActions = new JPanel(new BorderLayout(0, 3)); feedbackActions.setOpaque(false);
        recovery.setOpaque(false); recovery.add(retry); recovery.add(failedBrowser); recovery.setVisible(false);
        retry.addActionListener(event -> { if (retryAction != null) retryAction.run(); });
        failedBrowser.addActionListener(event -> { if (failedUrl != null) LinkBrowser.browse(event.getActionCommand()); });
        feedbackActions.add(progress, BorderLayout.NORTH); feedbackActions.add(recovery, BorderLayout.SOUTH);
        feedback.add(feedbackActions, BorderLayout.SOUTH);
        top.add(feedback, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ReaderComponents.BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.addMouseWheelListener(event -> cancelSkillPosition());
        scroll.getVerticalScrollBar().addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { cancelSkillPosition(); }
        });
        scroll.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) { cancelSkillPosition(); }
        });
        scroll.getViewport().addChangeListener(e -> { renderer.loadVisibleImages(); scheduleSectionLabel(); });
        add(scroll, BorderLayout.CENTER);
        debounce = new javax.swing.Timer(300, e -> performSearch(true));
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                if (restoringSearch)
                    return;
                searchRequest++;
                debounce.restart();
            }
            public void insertUpdate(DocumentEvent e) {
                changed();
            }
            public void removeUpdate(DocumentEvent e) {
                changed();
            }
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        search.addActionListener(e -> {
            debounce.stop();
            performSearch(false);
        });
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "leaveSearch");
        search.getActionMap().put("leaveSearch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (history.canBack())
                    travel(false);
            }
        });
        back.addActionListener(e -> travel(false));
        forward.addActionListener(e -> travel(true));
        searchToggle.addActionListener(e -> focusSearch());
        PopupToggle.bind(sectionsButton, sectionPopup, this::openSections);
        browser.addActionListener(e -> LinkBrowser.browse(e.getActionCommand()));
        variants.addActionListener(e -> {
            if (!settingSections && viewingArticle && history.current() != null) {
                saveScroll();
                NavigationHistory.Entry entry = history.current();
                entry.variant = Objects.toString(variants.getSelectedItem(), "");
                entry.section = displayedSection;
                entry.scroll = 0;
                show(entry, false);
            }
        });
        showSearch();
    }
    private static final class ReaderViewport extends JPanel implements Scrollable {
        ReaderViewport() {
            super(new BorderLayout());
            setBackground(ReaderComponents.BACKGROUND);
        }
        @Override public Dimension getPreferredSize() {
            Dimension size=super.getPreferredSize();
            Object minimum=getClientProperty("wiki.anchorHeight");
            if(minimum instanceof Integer)size.height=Math.max(size.height,(Integer)minimum);
            return size;
        }
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(205, 600);
        }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
    private static JButton button(String title, String tooltip) {
        JButton b = new JButton(title);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(3, 0, 3, 0));
        b.setFont(b.getFont().deriveFont(11f));
        b.getAccessibleContext().setAccessibleName(tooltip);
        return b;
    }
    public String compositionAudit() {
        return history.current() == null || history.current().isSearch()
            ? "No article loaded."
            : history.current().page.view.audit.summary();
    }
    private boolean mapGuidance;
    public void mapGuidance(String value) {
        if(value.isEmpty()&&!mapGuidance)return;
        status(value);mapGuidance=!value.isEmpty();
    }
    public void guidance(String value) { status(value); }
    private void status(String value) {
        mapGuidance=false;
        recovery.setVisible(false); retryAction = null; failedUrl = null;
        message.setVisible(!value.isEmpty());
        progress.setVisible(value.endsWith("…"));
        message.setText("<html><body><span class='muted'>"
            + com.wikipane.document.WikiDocumentParser.escape(value) + "</span></body></html>");
        message.getAccessibleContext().setAccessibleName(value);
    }
    private void failure(String error, Runnable repeat, String url) {
        HttpUrl destination = url == null ? null : WikiUrls.resolve(url);
        String title = WikiUrls.title(destination);
        if ("Special:Lookup".equals(title)) title = destination.queryParameter("name");
        status(title == null || title.isBlank() ? error : "While opening " + title + ": " + error);
        progress.setVisible(false);
        retryAction = repeat; failedUrl = url; failedBrowser.setActionCommand(url);
        recovery.removeAll();
        retry.setVisible(repeat != null); failedBrowser.setVisible(url != null);
        if (repeat != null) recovery.add(retry);
        if (url != null) recovery.add(failedBrowser);
        recovery.setVisible(repeat != null || url != null);
        revalidate();
    }
    private void scheduleSectionLabel() {
        if (sectionUpdateQueued) return;
        sectionUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            sectionUpdateQueued = false;
            if (!active || !viewingArticle || history.current() == null) return;
            NavigationHistory.Entry entry = history.current();
            ArticleNavigation.Destination visible=renderer.visibleDestination(content,scroll.getViewport().getViewPosition().y+8);
            if(visible==null)return;
            String anchor=visible.anchor;
            displayedSection=anchor;entry.area=visible.area;
            String label=visible.label;
            sectionsButton.setText(label);
            sectionsButton.getAccessibleContext().setAccessibleName(label + ", choose article section");
            fallbackUrl = entry.page.navigation.sourceUrl(visible.area,anchor);
            browser.setActionCommand(fallbackUrl);
        });
    }
    private void display(JComponent component) {
        content.putClientProperty("wiki.anchorHeight",0);
        content.removeAll();
        content.add(component, BorderLayout.NORTH);
        content.revalidate();
        content.repaint();
    }
    private void saveScroll() {
        NavigationHistory.Entry entry = history.current();
        if (entry == null)
            return;
        history.saveScroll(scroll.getVerticalScrollBar().getValue());
        if (viewingArticle && !entry.isSearch()) {
            entry.tables.clear();
            entry.tables.putAll(renderer.tableState());
        }
    }
    private void controls() {
        back.setEnabled(history.canBack());
        forward.setEnabled(history.canForward());
        browser.setEnabled(viewingArticle); browser.setActionCommand(fallbackUrl);
    }
    public void focusSearch() {
        if (!viewingArticle && history.current() != null && history.current().isSearch()) {
            search.requestFocusInWindow();
            return;
        }
        showSearch();
        search.requestFocusInWindow();
    }
    private void showSearch() {
        request++;
        searchRequest++;
        debounce.stop();
        saveScroll();
        history.pushSearch(lastQuery, lastTitles);
        history.current().nextSearchOffset = lastSearchOffset;
        history.current().suggestions = lastSuggestions;
        showSearchEntry(history.current(), true);
    }
    private void showSearchEntry(NavigationHistory.Entry entry, boolean restore) {
        closeMaps();
        renderer.clear();
        viewingArticle = false;
        sectionPopup.setVisible(false);
        ((CardLayout) statePanel.getLayout()).show(statePanel, "search");
        restoringSearch = true;
        search.setText(entry.query);
        restoringSearch = false;
        debounce.stop();
        lastQuery = entry.query;
        lastTitles = entry.results; lastSearchOffset = entry.nextSearchOffset; lastSuggestions = entry.suggestions;
        results = ContentRenderer.column();
        if (entry.results.isEmpty())
            results.add(renderer.text(
                entry.query.isEmpty() ? "Type an item, NPC, quest or topic." : "No results. Try another search."));
        for (String title : entry.results) {
            JButton result =
                new JButton("<html>" + com.wikipane.document.WikiDocumentParser.escape(title) + "</html>");
            result.setForeground(ReaderComponents.ACCENT);
            result.setHorizontalAlignment(SwingConstants.LEFT);
            result.setAlignmentX(Component.LEFT_ALIGNMENT);
            result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            result.addActionListener(e -> openTitle(title, ""));
            results.add(result);
        }
        if (entry.nextSearchOffset != null) {
            JButton more = button("More results", "Load more Wiki search results");
            more.setName("wiki.moreResults"); more.setAlignmentX(Component.LEFT_ALIGNMENT);
            more.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            more.addActionListener(event -> moreResults(entry)); results.add(more);
        } else if (entry.suggestions && !entry.query.isEmpty()) {
            JButton fullSearch = button("Search all articles", "Search beyond suggestions");
            fullSearch.setAlignmentX(Component.LEFT_ALIGNMENT);
            fullSearch.addActionListener(event -> performSearch(false)); results.add(fullSearch);
        }
        installResultKeys();
        display(results);
        controls();
        status(entry.query.isEmpty() ? "" : entry.suggestions ? "Suggestions · Enter searches all articles" : "Search results");
        SwingUtilities.invokeLater(() -> {
            if (active && history.current() == entry && !viewingArticle)
                scroll.getVerticalScrollBar().setValue(restore ? entry.scroll : 0);
        });
    }
    private void performSearch(boolean suggestion) {
        if (viewingArticle)
            return;
        String query = search.getText().trim();
        long version = ++searchRequest;
        request++;
        if (query.isEmpty()) {
            history.updateSearch("", List.of());
            showSearchEntry(history.current(), false);
            return;
        }
        status(suggestion ? "Finding suggestions…" : "Searching…");
        submit(()
                   -> suggestion ? new WikiClient.SearchPage(wiki.search(query, true), null) : wiki.searchPage(query, 0),
            page
            -> {
                if (version != searchRequest || viewingArticle)
                    return;
                history.updateSearch(query, page.titles);
                history.current().nextSearchOffset = page.nextOffset;
                history.current().suggestions = suggestion;
                showSearchEntry(history.current(), false);
            },
            error -> {
                if (version == searchRequest && !viewingArticle)
                    failure(error, () -> performSearch(suggestion), null);
            });
    }
    private void moreResults(NavigationHistory.Entry entry) {
        if (entry != history.current() || entry.nextSearchOffset == null) return;
        long version = ++searchRequest; request++; debounce.stop();
        int offset = entry.nextSearchOffset;
        int position = scroll.getVerticalScrollBar().getValue();
        status("Loading more results…");
        submit(() -> wiki.searchPage(entry.query, offset), page -> {
            if (version != searchRequest || history.current() != entry || viewingArticle) return;
            // Keep a bounded window of results; continuation remains available rather than silently stopping.
            LinkedHashSet<String> titles = new LinkedHashSet<>(entry.results); titles.addAll(page.titles);
            List<String> window = new java.util.ArrayList<>(titles);
            boolean shifted = window.size() > 200;
            if (shifted) window = window.subList(window.size() - 200, window.size());
            history.updateSearch(entry.query, window);
            history.current().nextSearchOffset = page.nextOffset;
            history.current().scroll = shifted ? 0 : position;
            showSearchEntry(history.current(), true);
            if (shifted) status("Showing the latest 200 results. Refine your search for earlier matches.");
        }, error -> {
            if (version == searchRequest && history.current() == entry && !viewingArticle)
                failure(error, () -> moreResults(entry), null);
        });
    }
    private void installResultKeys() {
        Component[] buttons = java.util.Arrays.stream(results.getComponents()).filter(component -> component instanceof JButton).toArray(Component[]::new);
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "suggestion");
        search.getActionMap().put("suggestion", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (buttons.length > 0)
                    buttons[0].requestFocusInWindow();
            }
        });
        for (int i = 0; i < buttons.length; i++)
            if (buttons[i] instanceof JButton) {
                JButton button = (JButton) buttons[i];
                final int index = i;
                button.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
                button.getActionMap().put("open", new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        button.doClick();
                    }
                });
                for (int direction : new int[] {-1, 1}) {
                    String key = "move" + direction;
                    button.getInputMap().put(
                        KeyStroke.getKeyStroke(direction < 0 ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0), key);
                    button.getActionMap().put(key, new AbstractAction() {
                        public void actionPerformed(ActionEvent e) {
                            int next = index + direction;
                            if (next < 0)
                                search.requestFocusInWindow();
                            else
                                buttons[Math.min(next, buttons.length - 1)].requestFocusInWindow();
                        }
                    });
                }
            }
    }
    public void openTitle(String title, String anchor) {
        debounce.stop();
        searchRequest++;
        long version = ++request;
        status("Loading article…");

        submit(()
                   -> wiki.page(title),
            document
            -> {
                if (version != request)
                    return;
                present(document, anchor.isEmpty() ? document.document.redirectAnchor : anchor);
            },
            error -> {
                if (version == request)
                    failure(error, () -> openTitle(title, anchor), WikiUrls.article(title) + (anchor.isEmpty() ? "" : "#" + anchor));
            });
    }
    private void present(com.wikipane.view.WikiPage document, String anchor) {
        saveScroll();
        history.push(document, anchor);
        show(history.current(), false);
    }
    public void openEntity(HttpUrl lookup) {
        debounce.stop();
        searchRequest++;
        long version = ++request;

        status("Resolving lookup…");
        submit(
            ()
                -> {
                com.wikipane.knowledge.KnowledgeArticle article =
                    new com.wikipane.knowledge.KnowledgeService(wiki).entity(lookup);
                return new NavigationHistory.Entry(wiki.page(article.document.title), article.anchor, 0);
            },
            entry
            -> {
                if (version == request)
                    present(entry.page, entry.anchor);
            },
            error -> {
                if (version == request)
                    failure(error, () -> openEntity(lookup), lookup.toString());
            });
    }
    public void openLink(String raw) {
        HttpUrl url = WikiUrls.resolve(raw);
        if (!WikiUrls.browser(url)) {
            status("Unsupported link.");
            return;
        }
        String title = WikiUrls.title(url);
        if ("Special:Lookup".equals(title)) {
            openEntity(url);
            return;
        }
        if (title != null && !title.startsWith("Special:") && !title.startsWith("File:")
            && !title.startsWith("Media:")) {
            NavigationHistory.Entry current = history.current();
            String anchor = url.fragment() == null ? "" : url.fragment();
            if (current != null && !current.isSearch() && current.document.url.equals(WikiUrls.article(title))
                && content.getComponentCount() > 0 && viewingArticle) {
                anchor(anchor, true);
                return;
            }
            openTitle(title, anchor);
        } else
            LinkBrowser.browse(url.toString());
    }
    private void travel(boolean next) {
        request++;
        searchRequest++;
        debounce.stop();
        saveScroll();
        NavigationHistory.Entry entry = next ? history.forward() : history.back();
        if (entry != null)
            show(entry, true);
    }
    private void show(NavigationHistory.Entry entry, boolean restore) {
        pendingSkillPosition = null;
        if (entry.isSearch()) {
            showSearchEntry(entry, restore);
            return;
        }
        viewingArticle = true;
        closeMaps();mapButton.setVisible(!entry.page.maps.groups.isEmpty());
        if (restore || !entry.section.isEmpty()) entry.skillPositioned = true;
        else if (autoPositionSkills && !entry.skillPositioned && !com.wikipane.context.SkillGuideProgress.skill(entry.document.title).isEmpty())
            pendingSkillPosition = entry;
        ArticleNavigation nav = entry.page.navigation;
        if (entry.area.isEmpty())
            entry.area = nav.areaForAnchor(entry.anchor);
        ArticleViewModel.Area area = nav.area(entry.area);
        entry.area = area.id;
        String browserAnchor = entry.section;
        fallbackUrl = nav.sourceUrl(entry.area,browserAnchor);
        sectionPopup.setVisible(false);
        settingSections = true;
        variants.removeAllItems();
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        for (com.wikipane.semantic.SemanticDocument.Node node : entry.document.semantic.nodes)
            if (node.table != null && !node.table.variant.isBlank())
                versions.add(node.table.variant);
        for (String version : versions) variants.addItem(version);
        if (entry.variant.isEmpty()) {
            for (WikiDocument.Block block : entry.document.blocks)
                if (block instanceof WikiDocument.Table
                    && ((WikiDocument.Table) block).anchor.equals(entry.anchor))
                    entry.variant = ((WikiDocument.Table) block).variant;
            if (entry.variant.isEmpty() && !versions.isEmpty())
                entry.variant = versions.iterator().next();
        }
        variants.setSelectedItem(entry.variant);
        variants.setVisible(versions.size() > 1);
        settingSections = false;
        articleTitle.setText("<html><body><span class='article-title'>"
            + com.wikipane.document.WikiDocumentParser.escape(entry.document.title)
            + "</span></body></html>");
        metadata.setText("<html><body><span class='muted tiny'>"
            + com.wikipane.document.WikiDocumentParser.escape(nav.subtitle) + "</span></body></html>");
        displayedSection = entry.section;
        String location = entry.section.isEmpty() ? area.label : nav.section(entry.section).label;
        sectionsButton.setText(location);
        sectionsButton.setForeground(ReaderComponents.ACCENT);
        sectionsButton.getAccessibleContext().setAccessibleName(location + ", choose article section");
        ((CardLayout) statePanel.getLayout()).show(statePanel, "article");
        display(renderer.render(entry.page, entry.tables, entry.variant));
        controls();
        status(entry.document.title);
        message.setVisible(false);
        String requestedArea=entry.area, requestedAnchor=entry.section;
        SwingUtilities.invokeLater(() -> {
            if (!active || history.current() != entry || !viewingArticle)
                return;
            if (restore) {
                content.putClientProperty("wiki.anchorHeight",entry.scroll+scroll.getViewport().getExtentSize().height);
                scroll.getViewport().setViewSize(new Dimension(scroll.getViewport().getExtentSize().width,content.getPreferredSize().height));
                scroll.getVerticalScrollBar().setValue(entry.scroll);
            }
            else if(requestedArea.equals("overview")&&requestedAnchor.isEmpty())scroll.getVerticalScrollBar().setValue(0);
            else {
                String destinationArea=requestedArea, destinationAnchor=requestedAnchor;
                renderer.revealDestination(destinationArea,destinationAnchor);
                SwingUtilities.invokeLater(() -> {
                    if(!active || history.current()!=entry || !viewingArticle)return;
                    JComponent target=renderer.destination(destinationArea,destinationAnchor);
                    if(target==null){scroll.getVerticalScrollBar().setValue(0);return;}
                    alignSectionHeader(target);
                    SwingUtilities.invokeLater(() -> {
                        if(active&&viewingArticle&&history.current()==entry)alignSectionHeader(target);
                    });
                });
            }
            renderer.loadVisibleImages();
            positionSkillGuide();
        });
    }
    private void cancelSkillPosition() {
        if (pendingSkillPosition != null) pendingSkillPosition.skillPositioned = true;
        pendingSkillPosition = null;
    }
    private void positionSkillGuide() {
        NavigationHistory.Entry entry = pendingSkillPosition;
        if (entry == null || !active || !viewingArticle || history.current() != entry) return;
        JComponent target = renderer.currentLevelTarget();
        if (target == null) return;
        SwingUtilities.invokeLater(() -> {
            if (!active || !viewingArticle || history.current() != entry || pendingSkillPosition != entry) return;
            alignSectionHeader(target);
            entry.skillPositioned = true; pendingSkillPosition = null;
        });
    }
    private void alignSectionHeader(JComponent target) {
        scroll.validate();
        java.util.List<Container> path = new java.util.ArrayList<>();
        for (Container parent = target.getParent(); parent != null; parent = parent.getParent()) {
            path.add(parent);
            if (parent == content) break;
        }
        if (path.isEmpty() || path.get(path.size()-1) != content) return;
        java.util.Collections.reverse(path);
        JViewport viewport = scroll.getViewport();
        // Resolve width-dependent wrapping and the updated extent before using header coordinates.
        // Work is limited to the destination's ancestor layouts, not a recursive article relayout.
        for (int pass = 0; pass < 2; pass++) {
            for (Container parent : path) parent.invalidate();
            viewport.setViewSize(new Dimension(Math.max(1,viewport.getExtentSize().width), content.getPreferredSize().height));
            for (Container parent : path) parent.doLayout();
            scroll.validate();
        }
        Point position = SwingUtilities.convertPoint(target, 0, 0, content);
        content.putClientProperty("wiki.anchorHeight",position.y+viewport.getExtentSize().height);
        viewport.setViewSize(new Dimension(Math.max(1,viewport.getExtentSize().width),content.getPreferredSize().height));
        int maximum = Math.max(0,content.getHeight()-viewport.getExtentSize().height);
        viewport.setViewPosition(new Point(0,Math.max(0,Math.min(position.y,maximum))));
    }
    private void openSections() {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        sectionPopup.removeAll();
        SectionNavigator navigator =
            new SectionNavigator(entry.page.navigation.roots, renderer.expanded(), entry.area, displayedSection, destination -> {
                sectionPopup.setVisible(false);
                navigateSection(destination);
            });
        navigator.setPreferredSize(PopupSizing.fit(sectionsButton, Math.max(160,sectionsButton.getWidth()),
            navigator.getPreferredSize().height));
        sectionPopup.add(navigator);
        if (sectionsButton.isShowing()) {
            PopupSizing.show(sectionPopup, sectionsButton);
            SwingUtilities.invokeLater(() -> {
                if (sectionPopup.isVisible() && history.current() == entry)
                    navigator.focusFilter();
            });
        }
    }
    private void closeMaps() {
        mapPopup.setVisible(false);mapPopup.removeAll();
        if(mapChooser!=null)mapChooser.stop();mapChooser=null;
    }
    private void openMaps() {
        NavigationHistory.Entry entry=history.current();
        if(!active||entry==null||entry.isSearch())return;
        if(mapChooser==null) {
            mapChooser=new MapChooser(entry.page,workers,images,renderer.mapViewer(),destination->{
                if(active&&history.current()==entry)mapRequests.accept(destination);
            },()->mapPopup.setVisible(false));
            mapPopup.add(mapChooser);
        }
        mapChooser.setPreferredSize(PopupSizing.fit(readingTools,Math.max(160,readingTools.getWidth()-6),400));
        PopupSizing.show(mapPopup,readingTools);
    }
    public void navigateSection(ArticleNavigation.Destination destination) {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        anchor(destination.anchor,true,destination.area);
    }
    private void anchor(String id, boolean push) {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        anchor(id, push, entry.page.navigation.areaForAnchor(id, entry.area));
    }
    private void anchor(String id, boolean push, String destinationArea) {
        NavigationHistory.Entry old = history.current();
        if (old == null || old.isSearch())
            return;
        boolean exists = id.isEmpty()
            || old.document.semantic.sections.stream().anyMatch(s -> s.anchor.equals(id))
            || old.page.view.areas.stream().flatMap(a -> a.components.stream()).anyMatch(c -> c.id.equals(id))
            || old.document.semantic.nodes.stream().anyMatch(
                n -> com.wikipane.view.PresentationValidator.hasAnchor(n, id))
            || old.document.blocks.stream().anyMatch(
                b -> b instanceof WikiDocument.Table && ((WikiDocument.Table) b).anchor.equals(id));
        if (!exists) {
            failure("This section is available on the Wiki website.", null, old.document.url + "#" + id);
            return;
        }
        request++;
        searchRequest++;
        debounce.stop();
        saveScroll();
        if (push)
            history.pushDestination(old.page, destinationArea, id);
        NavigationHistory.Entry entry = history.current();
        entry.tables.putAll(old.tables);
        entry.area = destinationArea;
        entry.section = id;
        entry.variant = old.variant;
        entry.skillPositioned = true;
        show(entry, false);
    }
    private <T> void submit(Callable<T> work, java.util.function.Consumer<T> success,
        java.util.function.Consumer<String> failure) {
        try {
            workers.submit(() -> {
                try {
                    T result = work.call();
                    SwingUtilities.invokeLater(() -> {
                        if (active)
                            success.accept(result);
                    });
                } catch (Exception error) {
                    log.debug("WikiPane request failed", error);
                    SwingUtilities.invokeLater(() -> {
                        if (active)
                            failure.accept(WikiErrors.message(error));
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
            if (active) failure.accept("WikiPane is busy. Please try again.");
        }
    }
    public void stop() {
        closeMaps();
        renderer.closeImageViewer();
        sectionPopup.setVisible(false);
        sectionPopup.removeAll();
        active = false;
        request++;
        searchRequest++;
        debounce.stop();
        renderer.clear();
        history.clear();
    }
}
