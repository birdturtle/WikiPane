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
    private final ContentRenderer renderer;
    private final NavigationHistory history = new NavigationHistory();
    private final JTextField search = new JTextField();
    private final JLabel message = new JLabel("Search the OSRS Wiki");
    private final JButton back = button("", "Back"), forward = button("", "Forward"),
                          searchToggle = button("", "Search"), browser = button("", "Open in browser"),
                          sectionsButton = button("Sections", "Jump to section");
    private final JToggleButton overview = new JToggleButton("Overview"), secondary = new JToggleButton();
    private final JPanel content = new ReaderViewport();
    private final JScrollPane scroll = new JScrollPane(content);
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
    private final JLabel currentSection = new JLabel();
    private final JPopupMenu sectionPopup = new JPopupMenu();
    private String lastQuery = "";
    private List<String> lastTitles = List.of();
    private boolean restoringSearch;
    private final javax.swing.Timer debounce;
    private long request, searchRequest;
    private boolean active = true, settingSections, viewingArticle;
    private final JProgressBar progress = new JProgressBar();
    private String fallbackUrl = WikiUrls.BASE.toString();
    private JPanel results = ContentRenderer.column();
    public WikiPanePanel(WikiClient wiki, ExecutorService workers) {
        super(false);
        this.wiki = wiki;
        this.workers = workers;
        renderer = new ContentRenderer(this::openLink, workers, new ImageCache(wiki));
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
        contextual.add(variants);
        JPanel modeRow = new JPanel(new BorderLayout(4, 0));
        modeRow.setOpaque(false);
        JPanel modes = new JPanel(new GridLayout(1, 2, 3, 0));
        modes.setOpaque(false);
        modes.add(overview);
        modes.add(secondary);
        modeRow.add(modes, BorderLayout.CENTER);
        modeRow.add(sectionsButton, BorderLayout.EAST);
        modeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        contextual.add(modeRow);
        currentSection.setName("wiki.currentSection");
        currentSection.setForeground(ReaderComponents.MUTED);
        currentSection.setFont(currentSection.getFont().deriveFont(10f));
        currentSection.putClientProperty("html.disable", true);
        currentSection.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
        contextual.add(currentSection);
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
        feedback.add(progress, BorderLayout.SOUTH);
        top.add(feedback, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ReaderComponents.BACKGROUND);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().addChangeListener(e -> renderer.loadVisibleImages());
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
        overview.addActionListener(e -> switchMode("overview"));
        secondary.addActionListener(e -> {
            NavigationHistory.Entry entry = history.current();
            if (entry != null && !entry.isSearch() && entry.page.navigation.secondary != null)
                switchMode(entry.page.navigation.secondary.area);
        });
        sectionsButton.addActionListener(e -> openSections());
        browser.addActionListener(e -> LinkBrowser.browse(fallbackUrl));
        variants.addActionListener(e -> {
            if (!settingSections && viewingArticle && history.current() != null) {
                saveScroll();
                history.current().variant = Objects.toString(variants.getSelectedItem(), "");
                show(history.current(), true);
            }
        });
        showSearch();
    }
    private static final class ReaderViewport extends JPanel implements Scrollable {
        ReaderViewport() {
            super(new BorderLayout());
            setBackground(ReaderComponents.BACKGROUND);
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
    public void guidance(String value) {
        status(value);
    }
    private void status(String value) {
        message.setVisible(true);
        progress.setVisible(value.endsWith("…"));
        message.setText(value);
        message.setToolTipText(value);
    }
    private void display(JComponent component) {
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
            entry.areaScroll.put(entry.area, entry.scroll);
            entry.tables.clear();
            entry.tables.putAll(renderer.tableState());
        }
    }
    private void controls() {
        back.setEnabled(history.canBack());
        forward.setEnabled(history.canForward());
        browser.setEnabled(viewingArticle);
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
        showSearchEntry(history.current(), true);
    }
    private void showSearchEntry(NavigationHistory.Entry entry, boolean restore) {
        renderer.clear();
        viewingArticle = false;
        sectionPopup.setVisible(false);
        ((CardLayout) statePanel.getLayout()).show(statePanel, "search");
        restoringSearch = true;
        search.setText(entry.query);
        restoringSearch = false;
        debounce.stop();
        lastQuery = entry.query;
        lastTitles = entry.results;
        results = ContentRenderer.column();
        if (entry.results.isEmpty())
            results.add(renderer.text(
                entry.query.isEmpty() ? "Search the OSRS Wiki" : "No results. Try another search."));
        for (String title : entry.results) {
            JButton result =
                new JButton("<html>" + com.wikipane.document.WikiDocumentParser.escape(title) + "</html>");
            result.setHorizontalAlignment(SwingConstants.LEFT);
            result.setAlignmentX(Component.LEFT_ALIGNMENT);
            result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            result.addActionListener(e -> openTitle(title, ""));
            results.add(result);
        }
        installResultKeys();
        display(results);
        controls();
        status(entry.query.isEmpty() ? "Search the OSRS Wiki" : "Search results");
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
                   -> wiki.search(query, suggestion),
            titles
            -> {
                if (version != searchRequest || viewingArticle)
                    return;
                history.updateSearch(query, titles);
                showSearchEntry(history.current(), false);
                status(titles.isEmpty() ? "No results" : suggestion ? "Suggestions" : "Search results");
            },
            error -> {
                if (version == searchRequest)
                    status(error);
            });
    }
    private void installResultKeys() {
        Component[] buttons = results.getComponents();
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
        fallbackUrl = WikiUrls.article(title);
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
                    status(error);
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
        fallbackUrl = lookup.toString();
        status("Resolving lookup…");
        submit(
            ()
                -> {
                HttpUrl resolved = WikiUrls.resolve(wiki.entity(lookup));
                String title = WikiUrls.title(resolved);
                if (title == null)
                    throw new java.io.IOException("The lookup did not resolve to an article.");
                com.wikipane.view.WikiPage page = wiki.page(title);
                WikiDocument document = page.document;
                String anchor = document.entityAnchors.get(lookup.queryParameter("id"));
                if (anchor == null)
                    anchor = resolved.fragment() == null ? document.redirectAnchor : resolved.fragment();
                return new NavigationHistory.Entry(page, anchor, 0);
            },
            entry
            -> {
                if (version == request)
                    present(entry.page, entry.anchor);
            },
            error -> {
                if (version == request)
                    status(error);
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
    private void modeStyle(JToggleButton button, boolean selected) {
        button.setSelected(selected);
        button.setFont(button.getFont().deriveFont(11f));
        button.setMargin(new Insets(3, 3, 3, 3));
        button.setForeground(selected ? ReaderComponents.ACCENT : ReaderComponents.MUTED);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(
                0, 0, 2, 0, selected ? ReaderComponents.ACCENT : ReaderComponents.BORDER),
            BorderFactory.createEmptyBorder(4, 3, 4, 3)));
    }
    private void show(NavigationHistory.Entry entry, boolean restore) {
        if (entry.isSearch()) {
            showSearchEntry(entry, restore);
            return;
        }
        viewingArticle = true;
        ArticleViewModel view = entry.page.view;
        ArticleNavigation nav = entry.page.navigation;
        if (entry.area.isEmpty())
            entry.area = nav.areaForAnchor(entry.anchor);
        ArticleViewModel.Area area = nav.area(entry.area);
        entry.area = area.id;
        String browserAnchor = entry.section;
        fallbackUrl = entry.document.url + (browserAnchor.isEmpty() ? "" : "#" + browserAnchor);
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
        overview.setToolTipText("Overview");
        overview.getAccessibleContext().setAccessibleName("Overview");
        modeStyle(overview, area.id.equals("overview"));
        secondary.setVisible(nav.secondary != null);
        if (nav.secondary != null) {
            secondary.setText(nav.secondary.label);
            secondary.setToolTipText(view.area(nav.secondary.area).label);
            secondary.getAccessibleContext().setAccessibleName(nav.secondary.label);
            modeStyle(secondary, area.id.equals(nav.secondary.area));
        }
        sectionsButton.setForeground(
            !area.id.equals("overview") && (nav.secondary == null || !area.id.equals(nav.secondary.area))
                ? ReaderComponents.ACCENT
                : ReaderComponents.MUTED);
        String location = entry.section.isEmpty() ? area.label : nav.section(entry.section).label;
        currentSection.setText(location);
        currentSection.setToolTipText(location);
        currentSection.setVisible(!entry.section.isEmpty()
            || !area.id.equals("overview") && (nav.secondary == null || !area.id.equals(nav.secondary.area)));
        ((CardLayout) statePanel.getLayout()).show(statePanel, "article");
        display(renderer.renderArea(entry.page, area, entry.tables, entry.variant));
        controls();
        status(entry.document.title);
        message.setVisible(false);
        SwingUtilities.invokeLater(() -> {
            if (!active || history.current() != entry || !viewingArticle)
                return;
            if (restore)
                scroll.getVerticalScrollBar().setValue(entry.scroll);
            else if (!entry.section.isEmpty()) {
                String anchor = entry.section;
                renderer.revealAnchor(anchor);
                SwingUtilities.invokeLater(() -> {
                    if (!active || history.current() != entry)
                        return;
                    JComponent target = renderer.anchor(anchor);
                    if (target != null)
                        target.scrollRectToVisible(
                            new Rectangle(0, 0, 1, Math.min(24, Math.max(1, target.getHeight()))));
                });
            } else
                scroll.getVerticalScrollBar().setValue(0);
            renderer.loadVisibleImages();
        });
    }
    private void switchMode(String id) {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        request++;
        searchRequest++;
        debounce.stop();
        saveScroll();
        entry.area = id;
        entry.section = "";
        entry.scroll = entry.areaScroll.getOrDefault(id, 0);
        show(entry, true);
    }
    private void openSections() {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        sectionPopup.removeAll();
        SectionNavigator navigator =
            new SectionNavigator(entry.page.navigation.sections, entry.area, entry.section, destination -> {
                sectionPopup.setVisible(false);
                navigateSection(destination);
            });
        sectionPopup.add(navigator);
        if (sectionsButton.isShowing()) {
            sectionPopup.show(sectionsButton, 0, sectionsButton.getHeight());
            SwingUtilities.invokeLater(() -> {
                if (sectionPopup.isVisible() && history.current() == entry)
                    navigator.focusFilter();
            });
        }
    }
    public void navigateSection(ArticleNavigation.Destination destination) {
        NavigationHistory.Entry entry = history.current();
        if (entry == null || entry.isSearch())
            return;
        if (destination.anchor.isEmpty()) {
            request++;
            searchRequest++;
            debounce.stop();
            saveScroll();
            history.pushDestination(entry.page, destination.area, "");
            NavigationHistory.Entry target = history.current();
            target.variant = entry.variant;
            target.tables.putAll(entry.tables);
            target.areaScroll.putAll(entry.areaScroll);
            target.scroll = 0;
            show(target, false);
            return;
        }
        anchor(destination.anchor, true, destination.area);
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
            status("This section is available in the browser.");
            fallbackUrl = old.document.url + "#" + id;
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
        entry.areaScroll.putAll(old.areaScroll);
        entry.variant = old.variant;
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
                            failure.accept(error instanceof java.io.IOException
                                    ? error.getMessage()
                                    : "Could not read the Wiki response. Try again.");
                    });
                }
            });
        } catch (RejectedExecutionException ignored) {
        }
    }
    public void stop() {
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
