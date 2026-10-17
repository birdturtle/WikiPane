package com.wikipane.ui;

import static com.wikipane.document.WikiDocumentParser.escape;

import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.document.WikiDocument.Image;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Native components consume the page view model, never source table geometry or Wiki templates. */
public final class ContentRenderer {
    private final Consumer<String> links;
    private final ReaderComponents components;
    private final ReaderSelection selection;
    private com.wikipane.semantic.SemanticDocument pinDocument;
    private com.wikipane.view.ReaderLayout layout;
    private final ExecutorService workers;
    private final ImageCache images;
    private final List<Runnable> imageChecks = new ArrayList<>();
    private final Map<String, JComponent> anchors = new HashMap<>();
    private Map<String, Integer> tableState = new HashMap<>();
    private volatile long generation;
    private JPanel flow;
    private Map<String, String> anchorAliases = Map.of();
    private JPanel rendered;
    private com.wikipane.view.CompositionAudit audit;
    private int auditedWidth = -1, auditedState, auditedBand = -1;
    private boolean auditScheduled;
    private List<ArticleViewModel.Component> pending = List.of();
    private int revealed;
    private String areaId = "";
    private String selectedVariant = "";
    public ContentRenderer(Consumer<String> links, ExecutorService workers, ImageCache images) {
        this(links, workers, images, null, message -> {});
    }
    public ContentRenderer(Consumer<String> links, ExecutorService workers, ImageCache images,
                          Consumer<com.wikipane.pin.PinSelection> pin, Consumer<String> error) {
        this.links = links;
        components = new ReaderComponents(links);
        selection = pin == null ? null : new ReaderSelection(pin, error);
        this.workers = workers;
        this.images = images;
    }
    public void clear() {
        generation++;
        if (selection != null) selection.clear();
        components.pinContext(null);
        components.pinDocument(null); pinDocument = null;
        imageChecks.clear();
        anchors.clear();
        pending = List.of();
        flow = null;
        rendered = null;
        auditedWidth = -1;
        auditScheduled = false;
    }
    public Map<String, Integer> tableState() {
        return new HashMap<>(tableState);
    }
    public JComponent anchor(String anchor) {
        return anchors.get(anchor);
    }
    public void loadVisibleImages() {
        new ArrayList<>(imageChecks).forEach(Runnable::run);
        revealNearBottom();
        scheduleAudit();
    }
    public JPanel render(WikiDocument article, Map<String, Integer> saved) {
        return render(article, saved, "");
    }
    public JPanel render(WikiDocument article, Map<String, Integer> saved, String anchor) {
        WikiPage page = new WikiPage(article);
        ArticleViewModel.Area area = page.view.sourceArea(anchor);
        return renderArea(page, area, saved, variantFor(page, anchor));
    }
    private String variantFor(WikiPage page, String anchor) {
        for (ArticleViewModel.Area area : page.view.areas)
            for (ArticleViewModel.Component component : area.components)
                if (component.node.fallback instanceof WikiDocument.Table) {
                    WikiDocument.Table table = (WikiDocument.Table) component.node.fallback;
                    if (table.anchor.equals(anchor) && !table.variant.isBlank())
                        return table.variant;
                }
        return "";
    }
    public JPanel renderArea(
        WikiDocument article, ArticleViewModel.Area area, Map<String, Integer> saved, String variant) {
        return renderArea(new WikiPage(article), area, saved, variant);
    }
    public JPanel renderArea(
        WikiPage page, ArticleViewModel.Area area, Map<String, Integer> saved, String variant) {
        WikiDocument article = page.document;
        layout = page.layout;
        clear();
        audit = page.view.audit;
        components.audit(audit);
        pinDocument = article.semantic;
        components.pinDocument(pinDocument);
        areaId = area.id;
        anchorAliases = page.navigation.anchors(area.id);
        tableState = new HashMap<>(saved);
        selectedVariant = variant;
        JPanel body = column();
        rendered = body;

        if ((area.id.equals("info") || area.id.equals("overview")) && !layout.highlights(variant).isEmpty()) {
            body.add(components.highlights(layout.highlights(variant)));
            body.add(components.gap(8));
        }
        // These are actual Wiki links to complete related guides, not generated recommendations.
        for (Relationship relationship : area.related) {
            String guideName = relationship.target.label.isBlank()
                ? Objects.toString(com.wikipane.api.WikiUrls.title(
                                       com.wikipane.api.WikiUrls.resolve(relationship.target.url)),
                      "guide")
                : relationship.target.label;
            JButton guide = linkButton("<html>Read " + escape(guideName) + "</html>");
            guide.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            guide.setPreferredSize(new Dimension(200, 38));
            guide.setToolTipText(relationship.target.url);
            guide.addActionListener(e -> links.accept(relationship.target.url));
            body.add(guide);
        }
        pending = new ArrayList<>();
        for (ArticleViewModel.Component component : area.components)
            if (variantMatches(component, area))
                pending.add(component);
        flow = column();
        body.add(flow);
        revealed = 0;
        appendNext(Math.max(6, tableState.getOrDefault("@shown:" + area.id, 6)));
        if (pending.isEmpty() && area.related.isEmpty())
            body.add(text("No readable content was returned for this area."));
        body.add(text("<p class='source'>Content: <a href='" + escape(article.url)
            + ("'>OSRS Wiki</a> · <a href='https://oldschool.runescape.wiki/w/RuneScape:Copyrights'>CC "
                + "BY-NC-SA</a></p>")));
        if (selection != null) selection.refresh(body);
        return body;
    }
    private void scheduleAudit() {
        if (rendered == null || auditScheduled)
            return;
        int width = rendered.getWidth(), state = tableState.hashCode(),
            band = rendered.getVisibleRect().y / 180;
        if (width <= 0 || width == auditedWidth && state == auditedState && band == auditedBand)
            return;
        long version = generation;
        auditScheduled = true;
        SwingUtilities.invokeLater(() -> {
            auditScheduled = false;
            if (version != generation || rendered == null)
                return;
            auditedWidth = rendered.getWidth();
            auditedState = tableState.hashCode();
            auditedBand = rendered.getVisibleRect().y / 180;
            SidebarLayoutValidator.inspect(rendered, audit);
        });
    }
    private boolean variantMatches(ArticleViewModel.Component component, ArticleViewModel.Area area) {
        if (component.node.table == null || component.node.table.variant.isEmpty())
            return true;
        String first = "";
        boolean selectedExists = false;
        for (ArticleViewModel.Component candidate : area.components) {
            if (candidate.node.table == null || !candidate.node.table.kind.equals(component.node.table.kind))
                continue;
            if (first.isEmpty())
                first = candidate.node.table.variant;
            if (candidate.node.table.variant.equals(selectedVariant))
                selectedExists = true;
        }
        return component.node.table.variant.equals(selectedExists ? selectedVariant : first);
    }
    private void appendNext(int count) {
        if (flow == null)
            return;
        int stop = Math.min(pending.size(), revealed + count);
        while (revealed < stop) {
            ArticleViewModel.Component component = pending.get(revealed++);
            components.pinContext(new com.wikipane.pin.PinContext(pinDocument, component.node));
            JPanel section = column();
            section.putClientProperty("wiki.source", component.node.id);
            String sourceSection = component.source.section;
            if (!sourceSection.isBlank() && !anchors.containsKey(sourceSection)) {
                if (!component.source.sectionTitle.equals(component.source.article)
                    && !(component.source.sectionTitle.equals("Details")
                        && (component.type == ArticleViewModel.ComponentType.FACTS
                            || component.type == ArticleViewModel.ComponentType.REQUIREMENTS)))
                    section.add(sourceHeading(layout.section(component.id), component.source));
                anchors.put(sourceSection, section);
            }
            anchors.put(component.id, section);
            anchorAliases.forEach((alias, target) -> {
                if (target.equals(component.source.section))
                    anchors.putIfAbsent(alias, section);
            });
            for (Citation use : com.wikipane.view.PresentationValidator.citations(component.node))
                if (!use.id.isBlank())
                    anchors.putIfAbsent(use.id, section);
            if (component.node.fallback instanceof WikiDocument.Table) {
                String id = ((WikiDocument.Table) component.node.fallback).anchor;
                if (!id.isBlank())
                    anchors.putIfAbsent(id, section);
            }
            switch (component.type) {
                case TEXT:
                    if (component.node.text != null)
                        section.add(text(component.node.text.html));
                    break;
                case FACTS:
                case REQUIREMENTS:
                    renderFacts(component, section);
                    break;
                case TABLE:
                    if (component.node.table != null)
                        section.add(table(component.node.table, component.id));
                    break;
                case REFERENCES:
                    for (com.wikipane.view.ReaderLayout.ReferenceCard ref : layout.references)
                        if (ref.source.unit.equals(component.node.source.unit)) {
                            JPanel card = components.card("wiki.reference");
                            card.add(text("<b>" + escape(ref.label) + "</b>"));
                            card.add(text(ref.html));
                            for (int back = 0; back < ref.backlinks.size(); back++) {
                                String url = ref.backlinks.get(back);
                                JButton returnTo = linkButton("Return to citation"
                                    + (ref.backlinks.size() > 1 ? " " + (back + 1) : ""));
                                returnTo.addActionListener(e -> links.accept(url));
                                card.add(returnTo);
                            }
                            anchors.put(ref.id, card);
                            section.add(card);
                            section.add(components.gap(6));
                        }
                    break;
                case IMAGE:
                    if (component.node.fallback instanceof Image)
                        section.add(image((Image) component.node.fallback));
                    break;
                default:
                    if (component.node.text != null)
                        section.add(text(component.node.text.html));
                    else if (component.node.table != null)
                        section.add(table(component.node.table, component.id));
                    else if (component.node.fallback instanceof Image)
                        section.add(image((Image) component.node.fallback));
                    else
                        section.add(text("This content is available through Open in browser."));
                    break;
            }
            flow.add(section);
            components.pinContext(null);
        }
        if (selection != null && rendered != null) selection.refresh(rendered);
        tableState.put("@shown:" + areaId, revealed);
        flow.revalidate();
        flow.repaint();
        scheduleAudit();
    }
    public void revealAnchor(String anchor) {
        while (!anchors.containsKey(anchor) && revealed < pending.size()) appendNext(6);
    }
    public void revealAll() {
        appendNext(pending.size());
    }
    private void revealNearBottom() {
        if (flow == null || revealed >= pending.size() || !flow.isShowing())
            return;
        Rectangle visible = flow.getVisibleRect();
        if (visible.y + visible.height >= flow.getHeight() - 240)
            appendNext(6);
    }
    public static JPanel column() {
        return ReaderComponents.column();
    }
    private JComponent sourceHeading(com.wikipane.view.ReaderLayout.Section section, Source source) {
        return components.heading(section.title, section.breadcrumb, source);
    }
    private void renderFacts(ArticleViewModel.Component component, JPanel target) {
        Table table = component.node.table;
        com.wikipane.view.ReaderLayout.Section section = layout.section(component.id);
        if (component.type == ArticleViewModel.ComponentType.FACTS && table != null && !table.images.isEmpty()
            && !table.kind.equals("Combat bonuses")
            && component.units.contains(table.source.unit + ".image0")) {
            target.add(image(table.images.get(0), true));
            target.add(components.gap(6));
        }
        com.wikipane.pin.PinContext basePin = components.pinContext();
        for (com.wikipane.view.ReaderLayout.ConditionGroup condition : section.requirements) {
            if (basePin != null) components.pinContext(basePin.condition(condition.source, condition.title, condition.condition));
            JPanel card = components.card("wiki.requirements");
            card.add(text("<b>" + escape(condition.title) + "</b>"));
            card.add(components.gap(4));
            for (String line : condition.lines) card.add(components.condition(line));
            target.add(card);
            target.add(components.gap(8));
        }
        for (com.wikipane.view.ReaderLayout.FactGroup group : section.facts) {
            if (basePin != null) components.pinContext(basePin.group(group.title));
            if (!group.title.isBlank())
                target.add(components.heading(group.title, "", null));
            JPanel card = components.card("wiki.facts");
            for (com.wikipane.view.ReaderLayout.Field field : group.fields) card.add(components.field(field));
            target.add(card);
            target.add(components.gap(8));
        }
        components.pinContext(basePin);
        if (table != null)
            for (int i = 1; i < table.images.size(); i++) target.add(image(table.images.get(i)));
    }
    private JComponent table(Table table, String key) {
        JPanel group = column();
        if (!layout.tableTitle(table).isBlank())
            group.add(components.heading(layout.tableTitle(table), "", null));
        JPanel records = column();
        group.add(records);
        java.util.List<com.wikipane.view.ReaderLayout.Record> rows = layout.records(table);
        JButton more = linkButton("Show more entries");
        int[] shown = {0};
        String[] previous = {""};
        com.wikipane.pin.PinContext tableContext = components.pinContext();
        Runnable next = () -> {
            com.wikipane.pin.PinContext previousContext = components.pinContext();
            components.pinContext(tableContext);
            records.remove(more);
            int end = Math.min(rows.size(), shown[0] + 20);
            for (int i = shown[0]; i < end; i++) {
                com.wikipane.view.ReaderLayout.Record record = rows.get(i);
                if (!record.group.isBlank() && !record.group.equals(previous[0])
                    && !record.group.equals(table.source.article)) {
                    // A column-header-only row does not need to repeat the labels printed on every record.
                    if (!record.columnHeader
                        || rows.stream().noneMatch(r -> r.identity != null || !r.fields.isEmpty()))
                        records.add(components.heading(record.group, "", null));
                }
                previous[0] = record.group;
                if (record.identity != null || !record.fields.isEmpty()) {
                    records.add(components.record(record));
                    records.add(components.gap(6));
                }
                for (int n = 0; n < record.nested.size(); n++)
                    records.add(table(record.nested.get(n), key + ".r" + i + ".n" + n));
            }
            shown[0] = end;
            tableState.put(key, end);
            if (end < rows.size()) {
                more.setText("Show more (" + (rows.size() - end) + " remaining)");
                records.add(more);
            }
            components.pinContext(previousContext);
            if (selection != null && rendered != null) selection.refresh(rendered);
            group.revalidate();
            group.repaint();
            scheduleAudit();
        };
        int wanted = Math.max(20, tableState.getOrDefault(key, 20));
        do {
            next.run();
        } while (shown[0] < Math.min(wanted, rows.size()));
        more.addActionListener(e -> next.run());
        return group;
    }
    private JButton linkButton(String title) {
        JButton button = new JButton(title);
        button.setMargin(new Insets(5, 6, 5, 6));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return button;
    }
    private JComponent image(Image image) {
        return image(image, false);
    }
    private JComponent image(Image image, boolean identity) {
        int maxWidth = identity ? 88 : 200, maxHeight = identity ? 88 : 220;
        FittedThumbnail label = new FittedThumbnail(maxWidth, maxHeight, image.width, image.height, audit);
        label.putClientProperty("html.disable", true);
        label.setText(image.alt.isBlank() ? "Image" : image.alt);
        label.setForeground(ReaderComponents.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setToolTipText(image.alt);
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        if (!image.link.isBlank()) {
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    links.accept(image.link);
                }
            });
        }
        long version = generation;
        boolean[] requested = {false};
        imageChecks.add(() -> {
            if (requested[0] || !label.isShowing() || label.getVisibleRect().isEmpty())
                return;
            requested[0] = true;
            try {
                workers.submit(() -> {
                    try {
                        java.awt.image.BufferedImage decoded = images.load(image.url);
                        double fit = Math.min(1,
                            Math.min((double) maxWidth / decoded.getWidth(),
                                (double) maxHeight / decoded.getHeight()));
                        java.awt.image.BufferedImage loaded;
                        if (fit < 1) {
                            loaded = new java.awt.image.BufferedImage(
                                Math.max(1, (int) (decoded.getWidth() * fit)),
                                Math.max(1, (int) (decoded.getHeight() * fit)),
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = loaded.createGraphics();
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g.drawImage(decoded, 0, 0, loaded.getWidth(), loaded.getHeight(), null);
                            g.dispose();
                        } else
                            loaded = decoded;
                        SwingUtilities.invokeLater(() -> {
                            if (version != generation)
                                return;
                            label.setText("");
                            label.image(loaded);
                            label.revalidate();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (version == generation)
                                label.setText("Image unavailable");
                        });
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        });
        return label;
    }
    public JEditorPane text(String fragment) {
        return components.text(fragment);
    }
}
