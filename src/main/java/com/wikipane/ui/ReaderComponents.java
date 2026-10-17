package com.wikipane.ui;

import static com.wikipane.document.WikiDocumentParser.escape;

import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.CompositionAudit;
import com.wikipane.view.LayoutConstraints;
import com.wikipane.view.ReaderLayout;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.html.*;

/** Shared sidebar primitives. Width, typography, spacing and interactions live here. */
public final class ReaderComponents {
    public static final Color BACKGROUND = new Color(37, 37, 37);
    public static final Color SURFACE = new Color(45, 45, 45);
    public static final Color BORDER = new Color(61, 61, 61);
    public static final Color MUTED = SemanticPalette.MUTED;
    public static final Color ACCENT = SemanticPalette.LINK;
    private final Consumer<String> links;
    private CompositionAudit audit;
    private com.wikipane.pin.PinContext pinContext;
    private com.wikipane.semantic.SemanticDocument pinDocument;
    public void pinDocument(com.wikipane.semantic.SemanticDocument document) { pinDocument = document; }
    public void pinContext(com.wikipane.pin.PinContext context) { pinContext = context; }
    public com.wikipane.pin.PinContext pinContext() { return pinContext; }
    private com.wikipane.pin.PinContext fieldContext(ReaderLayout.Field field, com.wikipane.pin.PinContext.Part part) {
        com.wikipane.pin.PinContext base = pinContext;
        if (base == null && pinDocument != null) {
            for (Node node : pinDocument.nodes) if (node.units.contains(field.source.unit)) {
                base = new com.wikipane.pin.PinContext(pinDocument, node); break;
            }
        }
        return base == null ? null : base.field(field.source, field.label, field.value, part);
    }
    private JEditorPane boundText(String html, com.wikipane.pin.PinContext context) {
        JEditorPane pane = text(html); pane.putClientProperty("wiki.pin.context", context); return pane;
    }
    public void audit(CompositionAudit audit) {
        this.audit = audit;
    }
    public ReaderComponents(Consumer<String> links) {
        this.links = links;
    }
    public static JPanel column() {
        JPanel panel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }
    public JPanel card(String name) {
        JPanel card = column();
        card.setName(name);
        card.setOpaque(true);
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
            BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        return card;
    }
    public JComponent gap(int height) {
        return (JComponent) Box.createRigidArea(new Dimension(0, height));
    }
    public JComponent heading(String title, String breadcrumb, Source source) {
        JPanel group = column();
        group.setBorder(BorderFactory.createEmptyBorder(12, 0, 5, 0));
        if (!breadcrumb.isBlank())
            group.add(text("<span class='muted tiny'>" + escape(breadcrumb) + "</span>"));
        JEditorPane label = boundText("<span class='heading'>" + escape(title) + "</span>",
            pinContext == null ? null : pinContext.heading());
        if (source != null) {
            label.setToolTipText("Source: " + source.sectionTitle + " · Right-click to open on the Wiki");
            JPopupMenu menu = new JPopupMenu();
            JMenuItem original = new JMenuItem("Open source section in browser");
            original.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(source.link()));
            menu.add(original);
            label.setComponentPopupMenu(menu);
        }
        group.add(label);
        return group;
    }
    public JComponent field(ReaderLayout.Field field) {
        if (field.label.isBlank() || field.label.equals(field.value.text))
            return fieldValue(field);
        JEditorPane label = boundText("<span class='muted'>" + escape(field.label) + "</span>",
            fieldContext(field, com.wikipane.pin.PinContext.Part.LABEL));
        JComponent value = fieldValue(field);
        if (field.inline) {
            CompositionAudit diagnostics = audit;
            JPanel row = new JPanel(null) {
                private boolean paired(int width) {
                    boolean paired = LayoutConstraints.pairedFields(width);
                    if (!paired && getWidth() > 0 && diagnostics != null)
                        diagnostics.add("FACT_STACKED", field.source.unit, CompositionAudit.Outcome.REPAIRED,
                            "Label and value stacked at " + width + " px to preserve readable widths.");
                    return paired;
                }
                private int heightAt(int width) {
                    if (!paired(width)) {
                        label.setSize(width, Short.MAX_VALUE);
                        value.setSize(width, Short.MAX_VALUE);
                        return label.getPreferredSize().height + value.getPreferredSize().height + 6;
                    }
                    int split = Math.max(1, (int) (width * .46));
                    label.setSize(split, Short.MAX_VALUE);
                    value.setSize(Math.max(1, width - split - 6), Short.MAX_VALUE);
                    return Math.max(label.getPreferredSize().height, value.getPreferredSize().height) + 4;
                }
                @Override
                public Dimension getPreferredSize() {
                    int width = getWidth() > 0 ? getWidth() : 180;
                    return new Dimension(width, heightAt(width));
                }
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
                }
                @Override
                public void doLayout() {
                    int width = getWidth(), h = heightAt(width);
                    if (!paired(width)) {
                        int labelHeight = label.getPreferredSize().height;
                        label.setBounds(0, 0, width, labelHeight);
                        value.setBounds(0, labelHeight + 2, width, h - labelHeight - 2);
                    } else {
                        int split = (int) (width * .46);
                        label.setBounds(0, 0, split, h);
                        value.setBounds(split + 6, 0, Math.max(1, width - split - 6), h);
                    }
                }
            };
            row.setName("wiki.field");
            row.putClientProperty("wiki.source", field.source.unit);
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(label);
            row.add(value);
            // Each text fragment measures the bounds assigned to it, not the full two-column row.
            label.putClientProperty("reader.column", Boolean.TRUE);
            value.putClientProperty("reader.column", Boolean.TRUE);
            return row;
        }
        JPanel stacked = column();
        stacked.add(label);
        stacked.add(value);
        stacked.add(gap(5));
        return stacked;
    }
    private JComponent fieldValue(ReaderLayout.Field field) {
        com.wikipane.view.FieldPresentation display = field.presentation;
        Color color = SemanticPalette.field(display);
        String html = "<span style='color:" + SemanticPalette.hex(color) + "'>" + field.value.html + "</span>";
        if (display.numeric || display.probability != null && display.probability.form != com.wikipane.view.Probability.Form.UNPARSED)
            html = "<p style='text-align:right;margin:0'>" + html + "</p>";
        JEditorPane text = boundText(html, fieldContext(field, com.wikipane.pin.PinContext.Part.VALUE));
        text.putClientProperty("wiki.field.presentation", display);
        if (display.probability == null) return text;
        text.setToolTipText(display.probability.explanation);
        if (display.probability.label.isEmpty()) return text;
        // Derived labels are separate from selectable source text, so pinning retains only Wiki facts.
        JPanel value = column(); value.add(text);
        JLabel rarity = new JLabel(display.probability.label) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        rarity.setName("wiki.rarity"); rarity.setAlignmentX(Component.LEFT_ALIGNMENT); rarity.setForeground(color);
        rarity.setFont(rarity.getFont().deriveFont(10f)); rarity.setHorizontalAlignment(SwingConstants.RIGHT);
        rarity.setToolTipText(display.probability.explanation); value.add(rarity);
        return value;
    }
    public JComponent condition(String html) {
        JPanel row = column();
        row.add(text(html));
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(3, 7, 3, 0)));
        return row;
    }
    public JComponent highlights(java.util.List<ReaderLayout.Field> fields) {
        JPanel strip = card("wiki.highlights");
        CompositionAudit diagnostics = audit;
        strip.setLayout(new GridLayout(0, Math.max(1, fields.size()), 6, 4) {
            private void choose(Container parent) {
                int width = parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
                int columns = LayoutConstraints.highlightColumns(Math.max(1, width), fields.size());
                setColumns(columns);
                if (parent.getWidth() > 0 && columns < fields.size() && diagnostics != null)
                    diagnostics.add("HIGHLIGHTS_STACKED", "highlights", CompositionAudit.Outcome.REPAIRED,
                        "Highlight grid uses a vertical list at " + width + " px.");
            }
            @Override
            public Dimension preferredLayoutSize(Container parent) {
                choose(parent);
                return super.preferredLayoutSize(parent);
            }
            @Override
            public void layoutContainer(Container parent) {
                choose(parent);
                super.layoutContainer(parent);
            }
        });
        for (ReaderLayout.Field field : fields) {
            JPanel stat = column();
            stat.add(fieldValue(field));
            stat.add(boundText("<span class='muted tiny'>" + escape(field.label) + "</span>",
                fieldContext(field, com.wikipane.pin.PinContext.Part.LABEL)));
            strip.add(stat);
        }
        return strip;
    }
    public JComponent record(ReaderLayout.Record record) {
        com.wikipane.pin.PinContext previous = pinContext;
        if (pinContext != null && record.identity != null) pinContext = pinContext.record(record.identity.value.text);
        JPanel card = card("wiki.record");
        if (record.identity != null) {
            JEditorPane name = boundText("<b>" + record.identity.value.html + "</b>",
                fieldContext(record.identity, com.wikipane.pin.PinContext.Part.VALUE));
            if (!record.illustrations.isEmpty())
                name.setToolTipText(record.illustrations.get(0).value.text);
            card.add(name);
            card.add(gap(3));
        }
        for (ReaderLayout.Field field : record.fields) card.add(field(field));
        pinContext = previous;
        return card;
    }
    public JEditorPane text(String fragment) {
        JEditorPane pane = new JEditorPane() {
            @Override
            public Dimension getPreferredSize() {
                int width = Boolean.TRUE.equals(getClientProperty("reader.column")) && getWidth() > 0
                    ? getWidth()
                    : getParent() == null
                    ? 190
                    : getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right;
                if (width <= 0)
                    width = 200;
                setSize(width, Short.MAX_VALUE);
                Dimension size = super.getPreferredSize();
                Object floor = getClientProperty("reader.requiredHeight");
                if (floor instanceof Dimension && ((Dimension) floor).width == width)
                    size.height = Math.max(size.height, ((Dimension) floor).height);
                return new Dimension(width, size.height);
            }
            @Override
            public Dimension getMinimumSize() {
                return new Dimension(0, getPreferredSize().height);
            }
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        HTMLEditorKit kit = new HTMLEditorKit() {
            @Override
            public ViewFactory getViewFactory() {
                return new HTMLFactory() {
                    @Override
                    public View create(Element element) {
                        View view = super.create(element);
                        if (view instanceof InlineView && !"br".equals(element.getName()))
                            return new InlineView(element) {
                                @Override
                                public float getMinimumSpan(int axis) {
                                    return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                                }
                            };
                        return view;
                    }
                };
            }
        };
        kit.getStyleSheet().addRule(
            "body { font-family: sans-serif; font-size: 12px; margin: 0; } "
            + "p { margin-top: 0; margin-bottom: 7px; } a { text-decoration: none; } "
            + "li { margin-bottom: 5px; } sup { font-size: 10px; vertical-align: baseline; } ul, ol { "
            + "margin-left: 13px; } pre { white-space: normal; } "
            + ".tiny, small { font-size: 10px; } "
            + ".heading { font-size: 13px; font-weight: bold; color: #f0f0f0; } "
            + ".article-title { font-size: 16px; font-weight: bold; } "
            + ".source { font-size: 10px; margin-top: 12px; }");
        kit.getStyleSheet().addRule(SemanticPalette.css());
        kit.getStyleSheet().addRule("ul ul, ol ul, ul ol, ol ol { margin-left: 10px; } blockquote { margin-left: 8px; margin-right: 0; }");
        pane.putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.TRUE);
        pane.setEditorKit(kit);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.FALSE);
        DefaultCaret caret = new DefaultCaret() {
            @Override
            protected void adjustVisibility(Rectangle rectangle) {}
        };
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        pane.setCaret(caret);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        pane.setText("<html><body>" + fragment + "</body></html>");
        pane.setCaretPosition(0);
        pane.putClientProperty("wiki.pin.context", pinContext);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                && !Boolean.TRUE.equals(pane.getClientProperty("wiki.selectionDragging")))
                links.accept(e.getDescription());
        });
        return pane;
    }
}
