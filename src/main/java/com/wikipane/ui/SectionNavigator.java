package com.wikipane.ui;

import com.wikipane.view.ArticleNavigation;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;

/** A searchable, virtualized table of contents. Every row is a direct destination, never a submenu. */
public final class SectionNavigator extends JPanel {
    private final JList<ArticleNavigation.Destination> list = new JList<>();
    private final DefaultListModel<ArticleNavigation.Destination> model = new DefaultListModel<>();
    private final JTextField filter = new JTextField();
    public void focusFilter() {
        filter.requestFocusInWindow();
    }
    public SectionNavigator(List<ArticleNavigation.Destination> entries, String activeArea,
        String activeAnchor, Consumer<ArticleNavigation.Destination> choose) {
        super(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        filter.setToolTipText("Filter article sections");
        filter.getAccessibleContext().setAccessibleName("Filter article sections");
        JPanel filterRow = new JPanel(new BorderLayout(0, 3));
        filterRow.setOpaque(false);
        JLabel hint = new JLabel("Filter sections");
        hint.setForeground(ReaderComponents.MUTED);
        hint.setFont(hint.getFont().deriveFont(11f));
        filterRow.add(hint, BorderLayout.NORTH);
        filterRow.add(filter, BorderLayout.CENTER);
        add(filterRow, BorderLayout.NORTH);
        list.setName("wiki.sections");
        list.setModel(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> owner, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, "", index, selected, focus);
                ArticleNavigation.Destination destination = (ArticleNavigation.Destination) value;
                label.putClientProperty("html.disable", false);
                int width = Math.max(100, (owner.getWidth() > 0 ? owner.getWidth() : 200) - 16 - destination.depth * 9);
                String text = com.wikipane.document.WikiDocumentParser.escape(destination.label);
                label.setText("<html><body style='width:" + width + "px'>" + (destination.depth == 0 ? "<b>" + text + "</b>" : text) + "</body></html>");
                label.setBorder(BorderFactory.createEmptyBorder(5, 4 + destination.depth * 9, 5, 4));
                label.setToolTipText(destination.label);
                label.getAccessibleContext().setAccessibleName((destination.depth == 0 ? "Category: " : "Section: ") + destination.label);
                return label;
            }
        });
        Runnable update = () -> {
            model.clear();
            String text = filter.getText().trim().toLowerCase(Locale.ROOT);
            for (ArticleNavigation.Destination entry : entries)
                if (entry.label.toLowerCase(Locale.ROOT).contains(text))
                    model.addElement(entry);
            int selected = -1;
            for (int i = 0; i < model.size(); i++)
                if (model.get(i).area.equals(activeArea)) {
                    if (selected < 0)
                        selected = i;
                    if (model.get(i).anchor.equals(activeAnchor)) {
                        selected = i;
                        break;
                    }
                }
            if (!model.isEmpty())
                list.setSelectedIndex(selected < 0 ? 0 : selected);
            list.ensureIndexIsVisible(Math.max(0, list.getSelectedIndex()));
        };
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                update.run();
            }
            public void removeUpdate(DocumentEvent e) {
                update.run();
            }
            public void changedUpdate(DocumentEvent e) {
                update.run();
            }
        });
        Runnable select = () -> {
            ArticleNavigation.Destination value = list.getSelectedValue();
            if (value != null)
                choose.accept(value);
        };
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose");
        list.getActionMap().put("choose", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                select.run();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    list.setSelectedIndex(index);
                    select.run();
                }
            }
        });
        filter.addActionListener(e -> select.run());
        filter.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "sections");
        filter.getActionMap().put("sections", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                list.requestFocusInWindow();
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(215, Math.min(340, Math.max(80, entries.size() * 27))));
        add(scroll, BorderLayout.CENTER);
        update.run();
    }
}
