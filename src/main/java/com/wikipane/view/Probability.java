package com.wikipane.view;

import java.math.*;
import java.util.Locale;
import java.util.regex.*;

/** A display interpretation of explicitly labeled odds. Original odds are never replaced. */
public final class Probability {
    public enum Form { FRACTION, PERCENT, LABEL, UNPARSED }
    public enum Rarity { COMMON, UNCOMMON, RARE, VERY_RARE, NONE }
    public final String original, label, explanation;
    public final Form form;
    public final Rarity rarity;
    private static final String NUMBER = "(?:[0-9]+(?:\\.[0-9]+)?|[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?)";
    private static final Pattern FRACTION = Pattern.compile("(" + NUMBER + ")\\s*/\\s*(" + NUMBER + ")");
    private static final Pattern PERCENT = Pattern.compile("(" + NUMBER + ")\\s*%");
    private Probability(String original, Form form, Rarity rarity, String label, String explanation) {
        this.original = original; this.form = form; this.rarity = rarity;
        this.label = label; this.explanation = explanation;
    }
    public static Probability of(String source) {
        String text = source.replace('\u00a0', ' ').strip();
        if (text.length() > 160) return unknown(source);
        switch (text.toLowerCase(Locale.ROOT)) {
            case "always": return new Probability(source, Form.LABEL, Rarity.NONE, "", "Guaranteed by the source; other drop rolls may also occur.");
            case "common": return named(source, Rarity.COMMON);
            case "uncommon": return named(source, Rarity.UNCOMMON);
            case "rare": return named(source, Rarity.RARE);
            case "very rare": return named(source, Rarity.VERY_RARE);
            default: break;
        }
        Matcher fraction = FRACTION.matcher(text), percent = PERCENT.matcher(text);
        BigDecimal numerator, denominator;
        Form form;
        if (fraction.matches()) {
            numerator = decimal(fraction.group(1)); denominator = decimal(fraction.group(2)); form = Form.FRACTION;
        } else if (percent.matches()) {
            numerator = decimal(percent.group(1)); denominator = new BigDecimal("100"); form = Form.PERCENT;
        } else return unknown(source);
        if (denominator.signum() <= 0 || numerator.signum() < 0 || numerator.compareTo(denominator) > 0)
            return unknown(source);
        if (numerator.signum() == 0 || numerator.compareTo(denominator) == 0)
            return new Probability(source, form, Rarity.NONE, "", "Exact source odds; no rarity category assigned.");
        Rarity rarity = numerator.multiply(BigDecimal.TEN).compareTo(denominator) >= 0 ? Rarity.COMMON
            : numerator.multiply(new BigDecimal("100")).compareTo(denominator) >= 0 ? Rarity.UNCOMMON
            : numerator.multiply(new BigDecimal("1000")).compareTo(denominator) >= 0 ? Rarity.RARE : Rarity.VERY_RARE;
        String percentage = numerator.multiply(new BigDecimal("100")).divide(denominator, new MathContext(4, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString();
        return new Probability(source, form, rarity, label(rarity), "WikiPane display category: " + label(rarity)
            + ". Approximately " + percentage + "% for these source odds. Other rolls or source conditions may apply; this is not a share of total loot.");
    }
    private static BigDecimal decimal(String text) { return new BigDecimal(text.replace(",", "")); }
    private static Probability named(String source, Rarity rarity) {
        return new Probability(source, Form.LABEL, rarity, "", "Rarity label supplied by the Wiki; no numeric probability inferred.");
    }
    private static String label(Rarity rarity) {
        switch (rarity) {
            case COMMON: return "Common";
            case UNCOMMON: return "Uncommon";
            case RARE: return "Rare";
            case VERY_RARE: return "Very rare";
            default: return "";
        }
    }
    private static Probability unknown(String source) {
        return new Probability(source, Form.UNPARSED, Rarity.NONE, "", "Source odds retained. Multiple rolls, conditions, ranges and formulas are not assigned a rarity category.");
    }
}
