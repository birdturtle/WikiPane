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
        if(base!=null&&!base.node.units.contains(field.source.unit)&&!base.node.source.unit.equals(field.source.unit))base=null;
        if (base == null && pinDocument != null) {
            for (Node node : pinDocument.nodes) if (node.units.contains(field.source.unit)) {
                base = new com.wikipane.pin.PinContext(pinDocument, node);
                if(pinContext!=null)base=base.group(pinContext.group).record(pinContext.record);
                break;
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
    public JComponent disclosure(String title, JComponent body) {
        JPanel group = column();
        JButton toggle = new JButton();
        toggle.setName("wiki.disclosure");
        toggle.setForeground(ACCENT);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setMargin(new Insets(4, 6, 4, 6));
        toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        body.setVisible(false);
        Runnable update = () -> {
            toggle.setText((body.isVisible() ? "▾ " : "▸ ") + title);
            toggle.getAccessibleContext().setAccessibleName(title + (body.isVisible() ? ", expanded" : ", collapsed"));
        };
        update.run();
        toggle.addActionListener(event -> {
            body.setVisible(!body.isVisible()); update.run(); group.revalidate(); group.repaint();
        });
        group.add(toggle); group.add(body); return group;
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
        label.setName("wiki.sectionHeading");
        group.putClientProperty("wiki.heading.label", label);
        if (source != null) label.putClientProperty("wiki.heading.source", source.link());
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
    public JEditorPane recipeName(ReaderLayout.Field field,String html) {
        JEditorPane label=boundText(html,fieldContext(field,com.wikipane.pin.PinContext.Part.VALUE));
        label.putClientProperty("reader.column",Boolean.TRUE);return label;
    }
    public JComponent recipeItem(ReaderLayout.Field item, ReaderLayout.Field amount, String html, JComponent icon) {
        JEditorPane name=recipeName(item,html);
        JComponent quantity=fieldValue(amount,false);
        quantity.putClientProperty("reader.column",Boolean.TRUE);
        JLabel times=new JLabel("×");times.setForeground(MUTED);
        int quantityWidth=Math.max(14,quantity.getFontMetrics(new Font(Font.SANS_SERIF,Font.BOLD,12)).stringWidth(amount.value.text)+6);
        JPanel row=new JPanel(null) {
            private int nameLeft(){return (icon==null?0:28)+quantityWidth+14;}
            private int heightAt(int width) {
                name.setSize(Math.max(1,width-nameLeft()),Short.MAX_VALUE);
                quantity.setSize(quantityWidth,Short.MAX_VALUE);
                return Math.max(icon==null?0:icon.getPreferredSize().height,
                    Math.max(name.getPreferredSize().height,quantity.getPreferredSize().height));
            }
            @Override public Dimension getPreferredSize(){int width=getWidth()>0?getWidth():180;return new Dimension(width,heightAt(width));}
            @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
            @Override public void doLayout(){
                int left=icon==null?0:28,height=heightAt(getWidth());
                if(icon!=null)icon.setBounds(0,0,22,icon.getPreferredSize().height);
                quantity.setBounds(left,0,quantityWidth,height);
                times.setBounds(left+quantityWidth,0,12,Math.min(24,height));
                name.setBounds(nameLeft(),0,Math.max(1,getWidth()-nameLeft()),height);
            }
        };
        row.setOpaque(false);row.setAlignmentX(Component.LEFT_ALIGNMENT);
        if(icon!=null)row.add(icon);row.add(quantity);row.add(times);row.add(name);return row;
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
    private JComponent fieldValue(ReaderLayout.Field field) { return fieldValue(field, true); }
    private JComponent fieldValue(ReaderLayout.Field field, boolean alignNumbers) {
        com.wikipane.view.FieldPresentation display = field.presentation;
        Color color = display.membership
            ? field.value.text.equals("Members") ? net.runelite.client.ui.ColorScheme.BRAND_ORANGE : SemanticPalette.MUTED
            : SemanticPalette.field(display);
        String valueHtml = field.displayHtml;
        String html = "<span style='color:" + SemanticPalette.hex(color) + "'>" + valueHtml + "</span>";
        if (!alignNumbers && (display.numeric || display.probability != null)) html = "<b>" + html + "</b>";
        if (alignNumbers && (display.numeric || display.probability != null && display.probability.form != com.wikipane.view.Probability.Form.UNPARSED))
            html = "<p style='text-align:right;margin:0'>" + html + "</p>";
        JEditorPane text = boundText("<div class='record-content'>" + html + "</div>", fieldContext(field, com.wikipane.pin.PinContext.Part.VALUE));
        text.putClientProperty("wiki.field.presentation", display);
        if (display.probability == null) return text;
        text.setToolTipText(display.probability.explanation);
        // Exact source odds remain primary; derived rarity is conveyed by a restrained accent
        // and explicitly identified in the tooltip, never printed as a Wiki-supplied label.
        return text;
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
    public JComponent record(ReaderLayout.Record record) { return record(record, null); }
    public JComponent record(ReaderLayout.Record record, JComponent icon) {
        com.wikipane.pin.PinContext previous = pinContext;
        if (pinContext != null && record.identity != null) pinContext = pinContext.record(record.keyedIdentity
            ? record.identity.label + " " + record.identity.value.text : record.identity.value.text);
        JPanel card = card("wiki.record");
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER),
            BorderFactory.createEmptyBorder(4,7,4,7)));
        // A keyed row with one short detail is a line, rather than a heading over another card body.
        if (record.keyedIdentity && record.metadata.isEmpty() && record.nested.isEmpty()
            && record.fields.size() == 1 && record.fields.get(0).value.text.length() <= 64
            && !record.fields.get(0).value.html.matches("(?is).*</?(ul|ol|li|p|br).*?")
            && record.fields.get(0).label.equals(record.group)
            && com.wikipane.view.FactPolicy.importance(record.fields.get(0).label, record.fields.get(0).value.text)
                == com.wikipane.view.FactPolicy.Importance.PRIMARY) {
            JComponent key = compactMetric(record.identity, true);
            JComponent detail = fieldValue(record.fields.get(0), false);
            detail.putClientProperty("reader.column", Boolean.TRUE);
            int keyWidth = Math.max(24 + key.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12))
                    .stringWidth(record.identity.label),
                10 + key.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 12))
                    .stringWidth(record.identity.label + " " + record.identity.value.text));
            JPanel summary = new JPanel(null) {
                private int heightAt(int width) {
                    key.setSize(keyWidth, Short.MAX_VALUE);
                    detail.setSize(Math.max(1, width-keyWidth-8), Short.MAX_VALUE);
                    return Math.max(key.getPreferredSize().height, detail.getPreferredSize().height);
                }
                @Override public Dimension getPreferredSize() {
                    int width=getWidth()>0?getWidth():180; return new Dimension(width,heightAt(width));
                }
                @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
                @Override public Dimension getMinimumSize() { return new Dimension(0,getPreferredSize().height); }
                @Override public void doLayout() {
                    int height=heightAt(getWidth()); key.setBounds(0,0,keyWidth,height);
                    detail.setBounds(keyWidth+8,0,Math.max(1,getWidth()-keyWidth-8),height);
                }
            };
            summary.setOpaque(false); summary.setAlignmentX(Component.LEFT_ALIGNMENT);
            summary.setName("wiki.record.summary"); summary.add(key); summary.add(detail); card.add(summary);
            pinContext=previous; return card;
        }
        if (record.keyedIdentity) {
            JPanel heading = column(); heading.setLayout(new CompactRecordLayout(false));
            heading.setName("wiki.record.key"); heading.add(compactMetric(record.identity, true));
            for (ReaderLayout.Field field : record.metadata) heading.add(fieldValue(field, false));
            card.add(heading);
        } else if (record.identity != null) {
            JEditorPane name = boundText("<b>" + record.identity.value.html + "</b>",
                fieldContext(record.identity, com.wikipane.pin.PinContext.Part.VALUE));
            if (!record.illustrations.isEmpty())
                name.setToolTipText(record.illustrations.get(0).value.text);
            if (icon != null) {
                JPanel identity = new JPanel(null) {
                    private int heightAt(int width) {
                        int left = icon.getPreferredSize().width + 6;
                        name.setSize(Math.max(1,width-left), Short.MAX_VALUE);
                        return Math.max(icon.getPreferredSize().height,name.getPreferredSize().height);
                    }
                    @Override public Dimension getPreferredSize() {
                        int width=getWidth()>0?getWidth():180;
                        return new Dimension(width,heightAt(width));
                    }
                    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE,getPreferredSize().height); }
                    @Override public Dimension getMinimumSize() { return new Dimension(0,getPreferredSize().height); }
                    @Override public void doLayout() {
                        Dimension size=icon.getPreferredSize();
                        int height=heightAt(getWidth());
                        icon.setBounds(0,0,size.width,size.height);
                        name.setBounds(size.width+6,0,Math.max(1,getWidth()-size.width-6),height);
                    }
                };
                identity.setOpaque(false); identity.setAlignmentX(Component.LEFT_ALIGNMENT);
                name.putClientProperty("reader.column", Boolean.TRUE);
                icon.setName("wiki.record.icon");
                identity.add(icon); identity.add(name);
                card.add(identity);
            } else card.add(name);
            card.add(gap(3));
        }
        if (!record.keyedIdentity && !record.compact && !record.metadata.isEmpty()) {
            JPanel metadata = column(); metadata.setName("wiki.record.metadata");
            for (ReaderLayout.Field field : record.metadata) metadata.add(fieldValue(field, false));
            card.add(metadata);
        }
        java.util.List<ReaderLayout.Field> primary = new java.util.ArrayList<>();
        JPanel technical = column(), supporting = column();
        for (ReaderLayout.Field field : record.fields) {
            com.wikipane.view.FactPolicy.Importance importance = com.wikipane.view.FactPolicy.importance(field.label, field.value.text);
            if (importance == com.wikipane.view.FactPolicy.Importance.TECHNICAL) technical.add(field(field));
            else if (importance == com.wikipane.view.FactPolicy.Importance.SUPPORTING) supporting.add(field(field));
            else primary.add(field);
        }
        if (record.compact) {
            JPanel metrics = column();
            metrics.setName("wiki.record.metrics");
            boolean fullRows = primary.stream().anyMatch(f -> f.presentation.probability != null);
            metrics.setLayout(new CompactRecordLayout(fullRows));
            for (ReaderLayout.Field field : primary) {
                metrics.add(compactMetric(field));
            }
            if (!record.keyedIdentity)
                for (ReaderLayout.Field field : record.metadata) metrics.add(compactMetric(field));
            card.add(metrics);
        } else for (ReaderLayout.Field field : primary) {
            if (record.keyedIdentity && record.fields.size() == 1 && field.label.equals(record.group))
                card.add(fieldValue(field, false));
            else card.add(field(field));
        }
        if (supporting.getComponentCount() > 0) card.add(disclosure("Article details", supporting));
        if (technical.getComponentCount() > 0) card.add(disclosure("Technical details", technical));
        pinContext = previous;
        return card;
    }
    private JComponent compactMetric(ReaderLayout.Field field) {
        return compactMetric(field, false);
    }
    private JComponent compactMetric(ReaderLayout.Field field, boolean heading) {
        JComponent value = fieldValue(field, false);
        if (field.presentation.membership) {
            JPanel membership = column(); membership.add(value); return membership;
        }
        JEditorPane label = boundText(heading ? "<b>" + escape(field.label) + "</b>"
            : "<span class='muted tiny'>" + escape(field.label) + "</span>",
            fieldContext(field, com.wikipane.pin.PinContext.Part.LABEL));
        int labelWidth = label.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12)).stringWidth(field.label) + 12;
        int minimumValueWidth = heading ? 12 : 30;
        label.putClientProperty("reader.column", Boolean.TRUE);
        value.putClientProperty("reader.column", Boolean.TRUE);
        JPanel row = new JPanel(null) {
            private int heightAt(int width) {
                boolean paired = width >= labelWidth + minimumValueWidth;
                label.setSize(paired ? labelWidth : width, Short.MAX_VALUE);
                value.setSize(paired ? width-labelWidth : width, Short.MAX_VALUE);
                return paired ? Math.max(label.getPreferredSize().height, value.getPreferredSize().height)
                    : label.getPreferredSize().height + value.getPreferredSize().height;
            }
            @Override public Dimension getPreferredSize() {
                int width = getWidth() > 0 ? getWidth() : 90;
                return new Dimension(width, heightAt(width));
            }
            @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            @Override public void doLayout() {
                int width=getWidth(), height=heightAt(width);
                if(width >= labelWidth+minimumValueWidth) {
                    label.setBounds(0,0,labelWidth,height); value.setBounds(labelWidth,0,width-labelWidth,height);
                } else {
                    int top=label.getPreferredSize().height;
                    label.setBounds(0,0,width,top); value.setBounds(0,top,width,height-top);
                }
            }
        };
        row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label); row.add(value); return row;
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
                                public Color getForeground() {
                                    // Source formatting and semantic field colors must not hide a live link.
                                    return getElement().getAttributes().isDefined(HTML.Tag.A)
                                        ? ACCENT : super.getForeground();
                                }
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
        kit.getStyleSheet().addRule(".record-content li { margin-bottom: 2px; } .record-content ul, .record-content ol { margin-top: 2px; margin-bottom: 2px; }");
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
