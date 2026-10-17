package com.wikipane.ui;

import static com.wikipane.document.WikiDocumentParser.escape;

import com.wikipane.semantic.SemanticDocument.*;
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
    public static final Color MUTED = new Color(160, 165, 171);
    public static final Color ACCENT = new Color(255, 183, 77);
    private final Consumer<String> links;
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
        JEditorPane label = text("<span class='heading'>" + escape(title) + "</span>");
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
            return text(field.value.html);
        JEditorPane label = text("<span class='muted'>" + escape(field.label) + "</span>");
        JEditorPane value = text(field.value.html);
        if (field.inline) {
            JPanel row = new JPanel(null) {
                private int heightAt(int width) {
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
                    int split = (int) (getWidth() * .46);
                    int h = heightAt(getWidth());
                    label.setBounds(0, 0, split, h);
                    value.setBounds(split + 6, 0, Math.max(1, getWidth() - split - 6), h);
                }
            };
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
    public JComponent condition(String html) {
        JPanel row = column();
        row.add(text(html));
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(3, 7, 3, 0)));
        return row;
    }
    public JComponent highlights(java.util.List<ReaderLayout.Field> fields) {
        JPanel strip = card("wiki.highlights");
        strip.setLayout(new GridLayout(1, fields.size(), 6, 0));
        for (ReaderLayout.Field field : fields) {
            JPanel stat = column();
            stat.add(text("<b>" + field.value.html + "</b>"));
            stat.add(text("<span class='muted tiny'>" + escape(field.label) + "</span>"));
            strip.add(stat);
        }
        return strip;
    }
    public JComponent record(ReaderLayout.Record record) {
        JPanel card = card("wiki.record");
        if (record.identity != null) {
            JEditorPane name = text("<b>" + record.identity.value.html + "</b>");
            if (!record.illustrations.isEmpty())
                name.setToolTipText(record.illustrations.get(0).value.text);
            card.add(name);
            card.add(gap(3));
        }
        for (ReaderLayout.Field field : record.fields) card.add(field(field));
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
            "body { font-family: sans-serif; font-size: 12px; color: #dedede; margin: 0; } "
            + "p { margin-top: 0; margin-bottom: 7px; } a { color: #ffb74d; text-decoration: none; } "
            + "li { margin-bottom: 5px; } sup { font-size: 10px; vertical-align: baseline; } ul, ol { "
            + "margin-left: 13px; } pre { white-space: normal; } "
            + ".muted { color: #a0a5ab; } .tiny, small { font-size: 10px; color: #a0a5ab; } "
            + ".heading { font-size: 13px; font-weight: bold; color: #f0f0f0; } "
            + ".article-title { font-size: 16px; font-weight: bold; } "
            + ".source { font-size: 10px; color: #999; margin-top: 12px; }");
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
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
                links.accept(e.getDescription());
        });
        return pane;
    }
}
