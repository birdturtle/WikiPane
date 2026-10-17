package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;

/** Small derived display contract. It neither mutates nor guesses source facts. */
public final class FieldPresentation {
    public enum Tone { TEXT, NUMBER, REQUIREMENT, COMBAT, INPUT, OUTPUT, NOTE }
    public final Tone tone;
    public final boolean numeric;
    public final Probability probability;
    public final boolean membership;
    private FieldPresentation(Tone tone, boolean numeric, Probability probability, boolean membership) {
        this.tone = tone; this.numeric = numeric; this.probability = probability;
        this.membership = membership;
    }
    public static FieldPresentation of(String label, RichText value, Role role) {
        String key = label.toLowerCase(Locale.ROOT).strip();
        boolean number = value.text.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?(?:%| kg| coins)?");
        Probability odds = Set.of("rarity", "drop rate", "drop chance", "probability", "chance").contains(key)
            ? Probability.of(value.text) : null;
        Tone tone = Set.of("requirements", "level required", "skill required", "items required", "required items").contains(key) ? Tone.REQUIREMENT
            : Set.of("ingredients", "input", "inputs", "materials", "materials required").contains(key) ? Tone.INPUT
            : Set.of("output", "outputs", "produces", "product", "products", "crop").contains(key) ? Tone.OUTPUT
            : Set.of("warning", "important", "note").contains(key) ? Tone.NOTE
            : role == Role.COMBAT && number ? Tone.COMBAT : number ? Tone.NUMBER : Tone.TEXT;
        boolean membership = Set.of("Members", "Free-to-play").contains(value.text)
            && (key.equals("members") || value.links.stream().anyMatch(link ->
                link.url.equals(com.wikipane.api.WikiUrls.article(value.text))));
        return new FieldPresentation(tone, number, odds, membership);
    }
    public static String identity(String label, RichText value, Source source) {
        return source.url + "\0" + source.revision + "\0" + source.unit + "\0" + label + "\0" + value.html;
    }
}
