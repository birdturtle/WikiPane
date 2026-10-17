package com.wikipane.knowledge;

import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;

/** Account-independent query view of the existing semantic document. No rendering or extraction. */
public final class KnowledgeArticle {
    public final WikiDocument document;
    public final SemanticDocument semantic;
    public final String anchor, variant;
    public final boolean variantKnown;
    public final List<Node> nodes;
    public final List<Fact> facts;
    public final List<RequirementSet> requirements;
    public final List<Relationship> relationships;
    public final List<Location> locations;
    public final List<com.wikipane.semantic.Recipe> recipes;

    public KnowledgeArticle(WikiDocument document, String entityId, String destinationAnchor) {
        this.document = document;
        semantic = document.semantic;
        anchor = document.entityAnchors.getOrDefault(entityId,
            destinationAnchor.isEmpty() ? document.redirectAnchor : destinationAnchor);
        Set<String> variants = new LinkedHashSet<>();
        String matched = "";
        for (Node node : semantic.nodes) {
            if (node.table == null || node.table.variant.isEmpty()) continue;
            variants.add(node.table.variant);
            if (node.fallback instanceof WikiDocument.Table
                && ((WikiDocument.Table) node.fallback).anchor.equals(anchor)) matched = node.table.variant;
        }
        variant = !matched.isEmpty() ? matched : variants.size() == 1 ? variants.iterator().next() : "";
        variantKnown = variants.isEmpty() || !variant.isEmpty();
        List<Node> selected = new ArrayList<>();
        List<Fact> fs = new ArrayList<>();
        List<RequirementSet> rs = new ArrayList<>();
        List<Relationship> links = new ArrayList<>();
        List<Location> ls = new ArrayList<>();
        for (Node node : semantic.nodes) {
            if (node.table != null && !node.table.variant.isEmpty()
                && !node.table.variant.equals(variant)) continue;
            selected.add(node);
            fs.addAll(node.facts);
            rs.addAll(node.requirements);
            links.addAll(node.relationships);
            ls.addAll(node.locations);
        }
        nodes = List.copyOf(selected);
        facts = List.copyOf(fs);
        requirements = List.copyOf(rs);
        relationships = List.copyOf(links);
        locations = List.copyOf(ls);
        recipes = semantic.recipes.stream().filter(recipe -> recipe.variant.isEmpty()
            || !variants.contains(recipe.variant) || variantKnown && recipe.variant.equals(variant))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
    }
    public List<Node> section(String anchor) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) if (node.source.section.equals(anchor)) result.add(node);
        return List.copyOf(result);
    }
    public List<Node> role(Role role) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) if (node.role == role) result.add(node);
        return List.copyOf(result);
    }
}
