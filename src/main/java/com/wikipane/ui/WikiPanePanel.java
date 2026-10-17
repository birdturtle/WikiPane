package com.wikipane.ui;

import com.wikipane.api.*;
import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.navigation.NavigationHistory;
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
    private final JButton back = button("‹", "Back"), forward = button("›", "Forward"),
                          home = button("Results", "Return to search"),
                          browser = button("Wiki ↗", "Open in browser");
    private final JPanel content = new ReaderViewport();
    private final JScrollPane scroll = new JScrollPane(content);
    private final JPanel topics = new JPanel(new GridLayout(0, 2, 3, 3));
    private final JComboBox<String> variants = new JComboBox<>();
    private final JPanel readingTools = new JPanel(new BorderLayout(0, 5));
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
        JLabel title = new JLabel("WikiPane");
        title.setForeground(ColorScheme.BRAND_ORANGE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        top.add(title, BorderLayout.NORTH);
        JPanel controls = new JPanel(new BorderLayout(0, 5));
        controls.setOpaque(false);
        search.setToolTipText("Searches and lookups are sent to the OSRS Wiki");
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setOpaque(false);
        searchRow.add(search, BorderLayout.CENTER);
        JButton searchButton = new JButton("Search");
        searchButton.setMargin(new Insets(3, 5, 3, 5));
        searchButton.addActionListener(e -> search.postActionEvent());
        searchRow.add(searchButton, BorderLayout.EAST);
        controls.add(searchRow, BorderLayout.NORTH);
        JPanel toolbar = new JPanel(new GridLayout(1, 4, 3, 0));
        toolbar.setOpaque(false);
        for (JButton b : new JButton[] {back, forward, home, browser}) toolbar.add(b);
        controls.add(toolbar, BorderLayout.CENTER);
        topics.setOpaque(false);
        readingTools.setOpaque(false);
        readingTools.add(topics, BorderLayout.CENTER);
        readingTools.add(variants, BorderLayout.NORTH);
        readingTools.setVisible(false);
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
        controls.add(readingTools, BorderLayout.SOUTH);
        top.add(controls, BorderLayout.CENTER);
        message.putClientProperty("html.disable", true);
        message.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        message.setFont(message.getFont().deriveFont(11f));
        message.setToolTipText(message.getText());
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
        back.addActionListener(e -> travel(false));
        forward.addActionListener(e -> travel(true));
        home.addActionListener(e -> showSearch());
        browser.addActionListener(e -> LinkBrowser.browse(fallbackUrl));
        variants.addActionListener(e -> {
            if (!settingSections && viewingArticle && history.current() != null) {
                saveScroll();
                history.current().variant = Objects.toString(variants.getSelectedItem(), "");
                show(history.current(), false);
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
        if (viewingArticle) {
            history.saveScroll(scroll.getVerticalScrollBar().getValue());
            if (history.current() != null)
                history.current().areaScroll.put(history.current().area, history.current().scroll);
            NavigationHistory.Entry entry = history.current();
            if (entry != null) {
                entry.tables.clear();
                entry.tables.putAll(renderer.tableState());
            }
        }
    }
    private void controls() {
        back.setEnabled(history.canBack() || (!viewingArticle && history.current() != null));
        forward.setEnabled(history.canForward());
    }
    public void focusSearch() {
        showSearch();
        search.requestFocusInWindow();
    }
    private void showSearch() {
        request++;
        saveScroll();
        renderer.clear();
        viewingArticle = false;
        readingTools.setVisible(false);
        display(results);
        controls();
        status("Search the OSRS Wiki");
        search.requestFocusInWindow();
    }
    private void performSearch(boolean suggestion) {
        String query = search.getText().trim();
        long version = ++searchRequest;
        request++;
        if (query.isEmpty()) {
            status("Enter a search term.");
            return;
        }
        status(suggestion ? "Finding suggestions…" : "Searching…");
        submit(()
                   -> wiki.search(query, suggestion),
            titles
            -> {
                if (version != searchRequest)
                    return;
                results = ContentRenderer.column();
                if (titles.isEmpty())
                    results.add(renderer.text("No results. Try another search."));
                for (String title : titles) {
                    JButton result = new JButton(
                        "<html>" + com.wikipane.document.WikiDocumentParser.escape(title) + "</html>");
                    result.setHorizontalAlignment(SwingConstants.LEFT);
                    result.setAlignmentX(Component.LEFT_ALIGNMENT);
                    result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
                    result.addActionListener(e -> openTitle(title, ""));
                    results.add(result);
                }
                installResultKeys();
                saveScroll();
                renderer.clear();
                viewingArticle = false;
                readingTools.setVisible(false);
                display(results);
                status(titles.isEmpty() ? "No results" : suggestion ? "Suggestions" : "Search results");
                controls();
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
            if (current != null && current.document.url.equals(WikiUrls.article(title))
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
        NavigationHistory.Entry entry = !viewingArticle ? history.current()
            : next                                      ? history.forward()
                                                        : history.back();
        if (entry != null)
            show(entry, true);
    }
    private void show(NavigationHistory.Entry entry, boolean restore) {
        viewingArticle = true;
        ArticleViewModel view = entry.page.view;
        if (entry.area.isEmpty())
            entry.area = entry.anchor.isEmpty() ? view.areas.get(0).id : view.sourceArea(entry.anchor).id;
        ArticleViewModel.Area area = view.area(entry.area);
        entry.area = area.id;
        fallbackUrl = entry.document.url + (entry.anchor.isEmpty() ? "" : "#" + entry.anchor);
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
        topics.removeAll();
        for (ArticleViewModel.Area option : view.areas) {
            String label = compactArea(option.label);
            JToggleButton button = new JToggleButton(label);
            button.setFont(button.getFont().deriveFont(11f));
            button.setMargin(new Insets(3, 4, 3, 4));
            button.setPreferredSize(new Dimension(96, 25));
            button.setSelected(option.id.equals(area.id));
            button.setForeground(button.isSelected() ? ReaderComponents.ACCENT : ReaderComponents.MUTED);
            button.setBackground(button.isSelected() ? new Color(58, 52, 41) : new Color(44, 44, 44));
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(
                    0, 0, 2, 0, button.isSelected() ? ReaderComponents.ACCENT : ReaderComponents.BORDER),
                BorderFactory.createEmptyBorder(3, 4, 3, 4)));
            button.setToolTipText(option.label);
            button.getAccessibleContext().setAccessibleName(option.label);
            button.addActionListener(e -> {
                saveScroll();
                entry.area = option.id;
                entry.scroll = entry.areaScroll.getOrDefault(option.id, 0);
                show(entry, true);
            });
            topics.add(button);
        }
        settingSections = false;
        readingTools.setVisible(true);
        display(renderer.renderArea(entry.page, area, entry.tables, entry.variant));
        controls();
        status(entry.document.title);
        message.setVisible(false);
        SwingUtilities.invokeLater(() -> {
            if (!active || history.current() != entry || !viewingArticle)
                return;
            if (restore)
                scroll.getVerticalScrollBar().setValue(entry.scroll);
            else if (!entry.anchor.isEmpty() && view.sourceArea(entry.anchor).id.equals(entry.area)) {
                renderer.revealAnchor(entry.anchor);
                SwingUtilities.invokeLater(() -> {
                    if (!active || history.current() != entry)
                        return;
                    JComponent target = renderer.anchor(entry.anchor);
                    if (target != null)
                        target.scrollRectToVisible(new Rectangle(0, 0, 1, Math.max(1, target.getHeight())));
                });
            } else
                scroll.getVerticalScrollBar().setValue(0);
            renderer.loadVisibleImages();
        });
    }
    private static String compactArea(String label) {
        switch (label) {
            case "Information":
                return "Info";
            case "Combat & strategy":
                return "Combat";
            case "Access & locations":
                return "Access";
            case "Other sections":
                return "More";
            case "Making & uses":
                return "Uses";
            case "Training & methods":
                return "Training";
            default:
                return label;
        }
    }
    private void anchor(String id, boolean push) {
        NavigationHistory.Entry old = history.current();
        if (old == null)
            return;
        boolean exists = id.isEmpty()
            || old.document.semantic.sections.stream().anyMatch(s -> s.anchor.equals(id))
            || old.document.blocks.stream().anyMatch(
                b -> b instanceof WikiDocument.Table && ((WikiDocument.Table) b).anchor.equals(id));
        if (!exists) {
            status("This section is available in the browser.");
            fallbackUrl = old.document.url + "#" + id;
            return;
        }
        saveScroll();
        if (push)
            history.push(old.page, id);
        NavigationHistory.Entry entry = history.current();
        entry.tables.putAll(old.tables);
        entry.area = old.page.view.sourceArea(id).id;
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
        active = false;
        request++;
        searchRequest++;
        debounce.stop();
        renderer.clear();
        history.clear();
    }
}
