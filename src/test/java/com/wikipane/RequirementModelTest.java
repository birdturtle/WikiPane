package com.wikipane;
import com.wikipane.semantic.*;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;
import com.wikipane.html.WikiHtml;
import org.junit.Test;
import static org.junit.Assert.*;
public class RequirementModelTest {
    private List<RequirementGroup> groups(String html,String label){return RequirementExtractor.extract(WikiHtml.parseBodyFragment(html,"https://oldschool.runescape.wiki/w/Test").body(),label);}
    @Test public void preservesExplicitAlternativeSkillsAndBoostRestrictions(){
        List<RequirementGroup> groups=groups("<ul><li>Either <span data-skill='Magic' data-level='70'><a href='/w/Magic'>70 Magic</a></span> or <span data-skill='Ranged' data-level='75'><a href='/w/Ranged'>75 Ranged</a></span> (not boostable)</li></ul>","Requirements");
        Requirement choice=groups.get(0).expression.children.get(0);assertEquals(Requirement.Kind.ANY,choice.kind);assertEquals(2,choice.children.size());assertEquals(Integer.valueOf(70),choice.children.get(0).level);assertEquals(Requirement.Boost.NOT_ALLOWED,choice.children.get(0).boost);assertEquals("https://oldschool.runescape.wiki/w/Ranged",choice.children.get(1).url);
    }
    @Test public void inventoryRequirementsKeepExplicitAmountsAndUnknownQuantities(){
        Requirement root=groups("<ul><li>2 <a href='/w/Bucket'>Buckets</a></li><li><a href='/w/Rope'>Rope</a> as needed</li></ul>","Items required").get(0).expression;assertEquals(Requirement.Kind.ITEM,root.children.get(0).kind);assertEquals(Integer.valueOf(2),root.children.get(0).quantity);assertNull(root.children.get(1).quantity);assertTrue(root.children.get(1).original.text.contains("as needed"));
    }
    @Test public void unfamiliarRequirementProseStaysAvailableWithoutGuessing(){
        Requirement root=groups("<ul><li>Access to the newly discovered area, or help from another player</li></ul>","Requirements").get(0).expression.children.get(0);assertEquals(Requirement.Kind.OTHER,root.kind);assertNull(root.level);assertTrue(root.original.text.contains("help from another player"));
    }
}
