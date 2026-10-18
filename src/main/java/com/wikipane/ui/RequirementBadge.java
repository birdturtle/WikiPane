package com.wikipane.ui;

import com.wikipane.context.RequirementEvaluator;
import com.wikipane.semantic.SemanticDocument.Requirement;
import java.awt.*;
import javax.swing.*;

/** Annotation is separate from selectable Wiki prose; updates don't reconstruct the reader. */
public final class RequirementBadge extends JPanel {
    public final Requirement condition;
    private final JComponent source;
    private final JLabel status = new JLabel() {
        @Override public Dimension getPreferredSize() { return new Dimension(0, 17); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 17); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, 17); }
    };
    public RequirementBadge(Requirement condition, JComponent source) {
        this.condition = condition; this.source = source;
        setLayout(null); setOpaque(false); setAlignmentX(LEFT_ALIGNMENT);
        add(source);
        status.setName("wiki.requirementStatus"); status.putClientProperty("html.disable", true);
        status.setFont(status.getFont().deriveFont(10f)); status.setBorder(BorderFactory.createEmptyBorder(0,9,0,0));
        status.setAlignmentX(LEFT_ALIGNMENT); status.setVisible(false); add(status);
    }
    private int sourceHeight(int width) {
        if (source.getWidth() != width) { source.setSize(width, Short.MAX_VALUE); source.invalidate(); }
        return source.getPreferredSize().height;
    }
    @Override public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : 190;
        return new Dimension(width, sourceHeight(width) + (status.isVisible() ? 17 : 0));
    }
    @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    @Override public void doLayout() {
        int height = sourceHeight(Math.max(1, getWidth()));
        source.setBounds(0, 0, getWidth(), height); status.setBounds(0, height, getWidth(), status.isVisible() ? 17 : 0);
    }
    public void update(RequirementEvaluator.Result result, boolean enabled, Icon icon) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Requirement annotations require EDT");
        putClientProperty("wiki.requirementStatusReady", Boolean.TRUE);
        String symbol = result.status == RequirementEvaluator.Status.MET ? "✓" : result.status == RequirementEvaluator.Status.UNMET ? "✕" : "?";
        status.setText(symbol + " " + result.summary);
        status.setForeground(result.status == RequirementEvaluator.Status.MET ? SemanticPalette.MET
            : result.status == RequirementEvaluator.Status.UNMET ? SemanticPalette.UNMET : SemanticPalette.MUTED);
        status.getAccessibleContext().setAccessibleName(result.status + ": " + result.summary);
        status.setIcon(icon); status.putClientProperty("wiki.requirement.result", result);
        boolean noCondition = condition.kind == Requirement.Kind.OTHER
            && condition.original.text.strip().matches("(?i)(?:none|n/a|not required|no requirements)[.!]?");
        status.setVisible(enabled && !noCondition);
        revalidate(); repaint();
    }
}
