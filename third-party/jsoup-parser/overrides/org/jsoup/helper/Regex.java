/* Adapted from jsoup 1.23.2, Copyright Jonathan Hedley; MIT license: third-party/jsoup-LICENSE.txt. */
package org.jsoup.helper;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** The same JDK regex path used upstream when re2j is absent; no optional class/module loading. */
public class Regex {
    private final Pattern pattern;
    Regex(Pattern pattern) { this.pattern = pattern; }
    public static Regex compile(String regex) {
        try { return new Regex(Pattern.compile(regex)); }
        catch (PatternSyntaxException error) {
            throw new ValidationException("Pattern syntax error: " + error.getMessage());
        }
    }
    public static Regex fromPattern(Pattern pattern) { return new Regex(pattern); }
    public static boolean usingRe2j() { return false; }
    public Matcher matcher(CharSequence input) {
        java.util.regex.Matcher delegate = pattern.matcher(input);
        return delegate::find;
    }
    @Override public String toString() { return pattern.toString(); }
    public interface Matcher { boolean find(); }
}
