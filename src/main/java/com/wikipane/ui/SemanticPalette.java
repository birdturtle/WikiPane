package com.wikipane.ui;

import com.wikipane.view.FieldPresentation;
import com.wikipane.view.Probability;
import java.awt.Color;

/** Shared semantic colors for sidebar and compact views. Text remains the primary meaning. */
public final class SemanticPalette {
    private SemanticPalette() {}
    public static final Color TEXT = new Color(222, 222, 222), MUTED = new Color(160, 165, 171),
        LINK = net.runelite.client.ui.ColorScheme.BRAND_ORANGE, NUMBER = new Color(166, 211, 225),
        REQUIREMENT = new Color(194, 207, 230), INPUT = new Color(194, 207, 230),
        OUTPUT = new Color(152, 207, 170), WARNING = new Color(244, 198, 111),
        MET = new Color(143, 208, 156), UNMET = new Color(240, 155, 149),
        COMMON = new Color(143, 208, 156), UNCOMMON = new Color(143, 190, 244),
        RARE = new Color(203, 169, 241), VERY_RARE = new Color(244, 198, 111);
    public static Color field(FieldPresentation display) {
        return display.probability == null ? tone(display.tone) : rarity(display.probability.rarity);
    }
    public static Color rarity(Probability.Rarity rarity) {
        switch (rarity) {
            case COMMON: return COMMON;
            case UNCOMMON: return UNCOMMON;
            case RARE: return RARE;
            case VERY_RARE: return VERY_RARE;
            default: return TEXT;
        }
    }
    public static Color tone(FieldPresentation.Tone tone) {
        switch (tone) {
            case NUMBER: case COMBAT: return NUMBER;
            case REQUIREMENT: return REQUIREMENT;
            case INPUT: return INPUT;
            case OUTPUT: return OUTPUT;
            case NOTE: return WARNING;
            default: return TEXT;
        }
    }
    public static String hex(Color color) { return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()); }
    public static String css() {
        return "body { color: " + hex(TEXT) + "; } a { color: " + hex(LINK) + "; } "
            + ".muted, .tiny, small, .source { color: " + hex(MUTED) + "; } "
            + ".requirement-number { color: " + hex(REQUIREMENT) + "; font-weight: bold; } "
            + ".boost-allowed { color: " + hex(OUTPUT) + "; } .boost-forbidden { color: " + hex(WARNING) + "; } "
            + ".alternative { font-weight: bold; }";
    }
}
