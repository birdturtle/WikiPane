package com.wikipane.view;

import static org.junit.Assert.*;

import com.wikipane.DocumentTest;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import org.junit.Test;

public class ArticleNavigationTest {
    @Test
    public void pageTypesChooseTwoMeaningfulPrimaryModesWithoutPromotingReferences() throws Exception {
        for (String[] example : new String[][] {{"Recipe_for_Disaster", "walkthrough"}, {"Vorkath", "combat"},
                 {"Bronze_dagger", "stats"}, {"Cooking", "methods"}}) {
            WikiPage page = new WikiPage(DocumentTest.document(example[0]));
            assertEquals("Overview", page.navigation.overview.label);
            assertNotNull(page.navigation.secondary);
            assertEquals(example[1], page.navigation.secondary.area);
            assertNotEquals("references", page.navigation.secondary.area);
            assertTrue(page.navigation.sections.stream().anyMatch(d -> d.label.equals("Overview")));
        }
    }
    @Test
    public void everySourceAreaAndComponentRemainsReachableWithoutTurningItIntoATab() throws Exception {
        for (String fixture : List.of("Bronze_dagger", "Recipe_for_Disaster", "Vorkath", "Cooking",
                 "Vorkath__Strategies", "Recipe_for_Disaster__Full_guide")) {
            WikiPage page = new WikiPage(DocumentTest.document(fixture));
            Set<String> destinations = new HashSet<>();
            for (ArticleNavigation.Destination destination : page.navigation.sections) {
                assertTrue(destinations.add(destination.area + "\0" + destination.anchor));
                if (!destination.anchor.isBlank())
                    assertTrue(page.view.areas.stream()
                            .flatMap(a -> a.components.stream())
                            .anyMatch(c
                                -> c.id.equals(destination.anchor)
                                    || page.navigation.anchors(destination.area)
                                        .containsKey(destination.anchor)));
            }
            for (ArticleViewModel.Area area : page.view.areas)
                assertTrue(fixture + " " + area.id,
                    page.navigation.sections.stream().anyMatch(
                        d -> d.area.equals(page.navigation.modeFor(area.id))));
        }
    }
    @Test
    public void questOverviewContainsRequirementsAndWalkthroughDoesNotStartWithGeneralQuestIntro()
        throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster"));
        assertTrue(page.navigation.area("overview")
                .components.stream()
                .anyMatch(c -> c.type == ArticleViewModel.ComponentType.REQUIREMENTS));
        assertTrue(page.navigation.subtitle.contains("Quest"));
        assertTrue(page.navigation.subtitle.contains("Special"));
        for (ArticleViewModel.Component c : page.view.area("walkthrough").components)
            if (c.node.text != null)
                assertFalse(c.node.text.text.startsWith("Recipe for Disaster is"));
        assertTrue(page.navigation.sections.stream().anyMatch(
            d -> d.label.equals("Requirements") && !d.anchor.isBlank()));
    }
    @Test
    public void numberedInstructionsAndFullGuideChaptersStillHaveDirectDestinations() throws Exception {
        WikiPage page = new WikiPage(DocumentTest.document("Recipe_for_Disaster__Full_guide"));
        assertTrue(page.navigation.sections.stream().anyMatch(
            d -> d.label.toLowerCase(Locale.ROOT).contains("mountain dwarf") && !d.anchor.isBlank()));
        for (ArticleNavigation.Destination d : page.navigation.sections)
            if (!d.anchor.isBlank())
                assertEquals(d.area, page.navigation.areaForAnchor(d.anchor, d.area));
        assertTrue(
            page.view.area("walkthrough").components.stream().anyMatch(c -> !c.node.instructions.isEmpty()));
    }
}
