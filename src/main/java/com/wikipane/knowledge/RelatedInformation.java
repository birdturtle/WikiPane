package com.wikipane.knowledge;

import com.wikipane.api.WikiUrls;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import okhttp3.HttpUrl;

/** A source-labeled projection of explicit links. Section context never implies recipe direction. */
public final class RelatedInformation {
    public static final class Group {
        public final String label;
        public final List<Relationship> links;
        private Group(String label, Collection<Relationship> links) {
            this.label = label; this.links = List.copyOf(links);
        }
    }
    private RelatedInformation() {}
    public static List<Group> of(KnowledgeArticle article) {
        Map<String, LinkedHashMap<String, Relationship>> groups = new LinkedHashMap<>();
        Set<String> variantUnits = new HashSet<>();
        for (Node node : article.semantic.nodes)
            if (node.table != null && !node.table.variant.isEmpty()) variantUnits.add(node.source.unit);
        for (Relationship relationship : article.relationships) {
            // A page-wide shortcut must never borrow information from another selected variant.
            if (variantUnits.contains(relationship.source.unit)) continue;
            if (relationship.target.label.isBlank() || relationship.guide || !relationship.target.internal) continue;
            HttpUrl url = WikiUrls.resolve(relationship.target.url);
            String title = WikiUrls.title(url);
            if (url == null || !WikiUrls.wiki(url) || title == null || title.contains(":")) continue;
            if (title.replace('_',' ').equalsIgnoreCase(article.document.title.replace('_',' '))) continue;
            String label;
            switch (relationship.role) {
                case METHODS: label = "Production & methods"; break;
                case SOURCES: label = "Sources"; break;
                case RELATIONSHIPS: label = "Related information"; break;
                default: continue;
            }
            groups.computeIfAbsent(label, ignored -> new LinkedHashMap<>())
                .putIfAbsent(url.toString(), relationship);
        }
        List<Group> result = new ArrayList<>();
        groups.forEach((label, links) -> result.add(new Group(label, links.values())));
        return List.copyOf(result);
    }
}
