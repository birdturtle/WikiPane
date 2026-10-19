package com.wikipane.view;

import static org.junit.Assert.*;

import com.wikipane.DocumentTest;
import com.wikipane.document.*;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel.*;
import java.util.*;
import org.junit.Test;

public class PresentationValidationTest {
    @Test
    public void realReferenceListsAreMetadataNotInstructionsAndKeepTheirLinks() throws Exception {
        for (String fixture : List.of("Vorkath", "Vorkath__Strategies", "Recipe_for_Disaster__Full_guide")) {
            WikiDocument doc = DocumentTest.document(fixture);
            WikiPage page = new WikiPage(doc);
            String source = DocumentTest.fixture(fixture).getAsJsonObject("parse").get("text").getAsString();
            int expected = com.wikipane.vendor.jsoup.Jsoup.parseBodyFragment(source).select("ol.references > li").size();
            assertTrue(fixture, expected > 0);
            assertEquals(fixture, expected, doc.semantic.references.size());
            assertEquals(expected, page.layout.references.size());
            assertTrue(page.view.audit.summary(), page.view.audit.valid());
            for (Node node : doc.semantic.nodes)
                if (PresentationValidator.referenceNode(node)) {
                    assertTrue(node.instructions.isEmpty());
                    assertEquals(NodeType.REFERENCES, node.type);
                    assertTrue(
                        page.view.area("references").components.stream().anyMatch(c -> c.node == node));
                    assertFalse(page.view.areas.stream()
                            .filter(a -> a.id.equals("walkthrough"))
                            .flatMap(a -> a.components.stream())
                            .anyMatch(c -> c.node == node));
                }
            for (Node node : doc.semantic.nodes)
                for (Citation use : PresentationValidator.citations(node)) {
                    if (doc.semantic.references.stream().anyMatch(r -> r.id.equals(use.target)))
                        assertEquals("references", page.view.sourceArea(use.target).id);
                    if (!use.id.isBlank())
                        assertTrue(PresentationValidator.hasAnchor(node, use.id));
                }
            for (Reference ref : doc.semantic.references)
                assertTrue(ref.backlinks.stream().allMatch(url -> url.startsWith(doc.url + "#cite_ref")));
        }
    }
    @Test
    public void missingCompositionRestoresEverySourceUnitWithoutGuessing() throws Exception {
        WikiDocument doc = DocumentTest.document("Vorkath");
        ArticleViewModel repaired = PresentationValidator.validate(doc.semantic, List.of(), List.of()).view;
        assertTrue(repaired.missingUnits.isEmpty());
        assertTrue(repaired.audit.valid());
        assertEquals(
            doc.semantic.nodes.size(), repaired.areas.stream().mapToInt(a -> a.components.size()).sum());
        assertTrue(repaired.audit.findings().stream().anyMatch(f -> f.rule.equals("UNASSIGNED_SOURCE")));
        assertTrue(repaired.areas.stream()
                .flatMap(a -> a.components.stream())
                .map(Component::text)
                .anyMatch(t -> t.contains("Skeletal visage")));
    }
    @Test
    public void corruptedFactFallsBackToCanonicalSourceIncludingCorrectStabBonus() throws Exception {
        WikiDocument doc = DocumentTest.document("Bronze_dagger");
        ArticleViewModel good = ArticleViewModel.create(doc.semantic);
        Component original = good.area("stats")
                                 .components.stream()
                                 .filter(c -> c.facts.stream().anyMatch(f -> f.key.equals("astab")))
                                 .findFirst()
                                 .orElseThrow();
        Fact actual = original.facts.stream().filter(f -> f.key.equals("astab")).findFirst().orElseThrow();
        Fact wrong = new Fact(actual.key, actual.label, actual.group, actual.variant,
            new RichText("+40", "+40", List.of()), actual.source);
        List<Fact> fields = new ArrayList<>(original.facts);
        fields.set(fields.indexOf(actual), wrong);
        Component broken = new Component(original.id, original.title, original.type, original.source,
            original.node, fields, original.requirements, original.units);
        ArticleViewModel repaired =
            PresentationValidator
                .validate(doc.semantic, List.of(new Area("stats", "Stats", List.of(broken), List.of())),
                    List.of(wrong))
                .view;
        assertTrue(repaired.audit.valid());
        assertTrue(repaired.highlights.isEmpty());
        Component fallback = repaired.area("other")
                                 .components.stream()
                                 .filter(c -> c.node == original.node)
                                 .findFirst()
                                 .orElseThrow();
        assertEquals(ComponentType.FALLBACK, fallback.type);
        assertTrue(fallback.text().contains("+4"));
        assertFalse(fallback.text().contains("+40"));
        assertTrue(repaired.audit.findings().stream().anyMatch(f -> f.rule.equals("COMPONENT_CONTRACT")));
    }
    @Test
    public void misroutedReferencesAndDuplicateIdsGetDeterministicRepairs() throws Exception {
        WikiDocument doc = DocumentTest.document("Vorkath");
        ArticleViewModel good = ArticleViewModel.create(doc.semantic);
        Component ref = good.area("references").components.get(0);
        Component wrong = new Component(
            ref.id, ref.title, ComponentType.TEXT, ref.source, ref.node, List.of(), List.of(), ref.units);
        ArticleViewModel repaired =
            PresentationValidator
                .validate(doc.semantic,
                    List.of(new Area("walkthrough", "Instructions", List.of(wrong, wrong), List.of())),
                    List.of())
                .view;
        assertTrue(repaired.area("references")
                .components.stream()
                .allMatch(c -> c.type == ComponentType.REFERENCES));
        Set<String> ids = new HashSet<>();
        for (Area area : repaired.areas)
            for (Component c : area.components) assertTrue(ids.add(c.id));
        assertTrue(repaired.audit.findings().stream().anyMatch(f -> f.rule.equals("REFERENCES_IN_PROSE")));
        assertTrue(repaired.audit.findings().stream().anyMatch(f -> f.rule.equals("DUPLICATE_COMPONENT_ID")));
    }
    @Test
    public void requirementsCannotLoseAlternativesOrScopedConditions() throws Exception {
        WikiDocument doc = DocumentTest.document("Recipe_for_Disaster");
        ArticleViewModel good = ArticleViewModel.create(doc.semantic);
        Component req = good.area("requirements").components.get(0);
        Component broken =
            new Component(req.id, req.title, req.type, req.source, req.node, req.facts, List.of(), req.units);
        ArticleViewModel repaired =
            PresentationValidator
                .validate(doc.semantic,
                    List.of(new Area("requirements", "Requirements", List.of(broken), List.of())), List.of())
                .view;
        assertTrue(repaired.area("other").components.stream().anyMatch(
            c -> c.node == req.node && c.type == ComponentType.FALLBACK));
        assertTrue(repaired.areas.stream()
                .flatMap(a -> a.components.stream())
                .anyMatch(c -> c.text().contains("To start the quest")));
        assertTrue(repaired.missingUnits.isEmpty());
    }
    @Test
    public void anArticleContainingOnlyCitationsIsNotClassifiedAsAGuide() {
        com.google.gson.JsonObject response = new com.google.gson.JsonObject(),
                                   parse = new com.google.gson.JsonObject();
        parse.addProperty("title", "Reference example");
        parse.addProperty("text",
            "<h2 id='References'>References</h2><ol class='references'><li id='cite_note-1'><span "
                + "class='reference-text'>A source.</span></li></ol>");
        response.add("parse", parse);
        WikiDocument doc = new WikiDocumentParser().parse(response);
        assertEquals(PageType.GENERAL, doc.semantic.type);
        assertEquals(1, doc.semantic.references.size());
        assertTrue(doc.semantic.nodes.stream().allMatch(n -> n.instructions.isEmpty()));
    }
    @Test
    public void incorrectReferenceComponentCannotHideOrdinaryProse() throws Exception {
        WikiDocument doc = DocumentTest.document("Vorkath");
        Node prose =
            doc.semantic.nodes.stream().filter(n -> n.type == NodeType.TEXT).findFirst().orElseThrow();
        Component wrong = new Component(prose.id, "References", ComponentType.REFERENCES, prose.source, prose,
            List.of(), List.of(), prose.units);
        ArticleViewModel repaired =
            PresentationValidator
                .validate(doc.semantic,
                    List.of(new Area("references", "References", List.of(wrong), List.of())), List.of())
                .view;
        assertTrue(repaired.areas.stream()
                .flatMap(a -> a.components.stream())
                .anyMatch(c -> c.node == prose && c.type == ComponentType.FALLBACK));
        assertTrue(repaired.audit.valid());
    }
    @Test
    public void structuralReferenceIdsWorkWithoutCssAndNumberedInstructionsRemainInstructions() {
        com.google.gson.JsonObject response = new com.google.gson.JsonObject(),
                                   parse = new com.google.gson.JsonObject();
        parse.addProperty("title", "Guide example");
        parse.addProperty("text",
            "<h2 id='Walkthrough'>Walkthrough</h2><ol><li>Read the number [1].</li></ol><ol><li "
                + "id='cite_note-1'><span class='reference-text'>A citation.</span></li></ol>");
        response.add("parse", parse);
        WikiDocument doc = new WikiDocumentParser().parse(response);
        assertEquals(1, doc.semantic.references.size());
        assertTrue(doc.semantic.nodes.stream().anyMatch(
            n -> !n.instructions.isEmpty() && n.text.text.contains("Read the number [1].")));
    }
    @Test
    public void proseRequirementsCannotBeMisassignedToAnEmptyFactPanel() throws Exception {
        WikiDocument doc = DocumentTest.document("Recipe_for_Disaster__Full_guide");
        Node prose = doc.semantic.nodes.stream()
                         .filter(n -> n.text != null && !n.requirements.isEmpty())
                         .findFirst()
                         .orElseThrow();
        Component wrong = new Component(prose.id, "Facts", ComponentType.FACTS, prose.source, prose,
            List.of(), prose.requirements, prose.units);
        ArticleViewModel repaired =
            PresentationValidator
                .validate(
                    doc.semantic, List.of(new Area("info", "Info", List.of(wrong), List.of())), List.of())
                .view;
        assertTrue(repaired.areas.stream()
                .flatMap(a -> a.components.stream())
                .anyMatch(c -> c.node == prose && c.type == ComponentType.FALLBACK));
        assertTrue(repaired.audit.valid());
    }
    @Test
    public void auditIsBoundedAndDeduplicatesRepeatedMeasurements() {
        CompositionAudit audit = new CompositionAudit(0, 0);
        for (int i = 0; i < 10000; i++)
            audit.add("WIDTH", "same", CompositionAudit.Outcome.REPAIRED, "same repair");
        assertEquals(1, audit.findings().size());
        for (int i = 0; i < 10000; i++)
            audit.add("WIDTH", "id" + i, CompositionAudit.Outcome.WARNING, "different");
        assertEquals(256, audit.findings().size());
    }
}
