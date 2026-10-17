package com.wikipane.knowledge;

import com.wikipane.DocumentTest;
import com.wikipane.api.WikiUrls;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RelatedInformationTest {
    @Test public void fixtureLinksRetainExactTargetsAndProvenanceWithoutInventingRelationships() throws Exception {
        int count = 0;
        for (String name : List.of("Ranarr_seed", "Rake", "Cooking", "Bronze_dagger")) {
            KnowledgeArticle article = new KnowledgeArticle(DocumentTest.document(name), "", "");
            List<RelatedInformation.Group> groups = RelatedInformation.of(article);
            for (RelatedInformation.Group group : groups) {
                Set<String> urls = new HashSet<>();
                for (Relationship relationship : group.links) {
                    count++;
                    assertTrue(article.relationships.contains(relationship));
                    assertTrue(urls.add(relationship.target.url));
                    assertTrue(WikiUrls.wiki(WikiUrls.resolve(relationship.target.url)));
                    assertFalse(relationship.guide);
                    assertFalse(relationship.source.unit.isBlank());
                    assertFalse(relationship.source.article.isBlank());
                    assertFalse(relationship.target.label.isBlank());
                    assertTrue(Set.of(Role.METHODS, Role.SOURCES, Role.RELATIONSHIPS).contains(relationship.role));
                }
            }
            assertTrue(new com.wikipane.view.WikiPage(article.document).view.audit.valid());
        }
        assertTrue("Expected real related Wiki links", count > 0);
    }
    @Test public void projectionsAreImmutableAndNeverIncludeVariantSpecificSources() throws Exception {
        KnowledgeArticle article = new KnowledgeArticle(DocumentTest.document("Vorkath"), "", "");
        Set<String> variants = new HashSet<>();
        for (Node node : article.semantic.nodes) if (node.table != null && !node.table.variant.isEmpty()) variants.add(node.source.unit);
        List<RelatedInformation.Group> groups = RelatedInformation.of(article);
        for (RelatedInformation.Group group : groups) for (Relationship link : group.links)
            assertFalse(variants.contains(link.source.unit));
        try { groups.clear(); fail("Mutable projection"); } catch (UnsupportedOperationException expected) {}
    }
}
