package com.wikipane.view;

/** Deterministic width contracts in logical Swing pixels, after component insets. */
public final class LayoutConstraints {
    private LayoutConstraints() {}
    public static boolean pairedFields(int width) {
        return width >= 144;
    }
    public static int highlightColumns(int width, int count) {
        return count > 0 && width >= count * 64 + Math.max(0, count - 1) * 6 ? count : 1;
    }
}
