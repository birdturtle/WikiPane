package com.wikipane.knowledge;

import com.wikipane.document.WikiDocument;
import com.wikipane.semantic.SemanticDocument.*;
import java.util.*;

/** Deliberate compact projection. Every row keeps the unchanged semantic value and its provenance. */
public final class Inspection {
    public static final class Row {
        public final String label;
        public final RichText value;
        public final Source source;
        public final Requirement condition;
        public final List<Source> sources;
        Row(String label, RichText value, Source source, Requirement condition) {
            this(label,value,source,condition,List.of(source));
        }
        Row(String label, RichText value, Source source, Requirement condition,List<Source> sources) {
            this.label = label; this.value = value; this.source = source; this.condition = condition;this.sources=List.copyOf(sources);
        }
    }
    public final KnowledgeArticle article;
    public final List<Row> rows;
    public final List<Relationship> related;
    public final WikiDocument.Image image;
    public final int recipeIndex;
    private Inspection(KnowledgeArticle article, List<Row> rows, List<Relationship> related,
                       WikiDocument.Image image,int recipeIndex) {
        this.article = article; this.rows = List.copyOf(rows); this.related = List.copyOf(related);
        this.image = image;this.recipeIndex=recipeIndex;
    }
    public static Inspection project(KnowledgeArticle article) { return project(article,0); }
    public static Inspection project(KnowledgeArticle article,int recipeIndex) {
        if(recipeIndex<0||article.recipes.isEmpty()&&recipeIndex!=0||!article.recipes.isEmpty()&&recipeIndex>=article.recipes.size())throw new IllegalArgumentException("Unknown recipe method");
        List<Row> rows = new ArrayList<>();
        Set<String> included = new HashSet<>();
        if(article.semantic.type==PageType.ITEM&&!article.recipes.isEmpty()) {
            com.wikipane.semantic.Recipe recipe=article.recipes.get(recipeIndex);
            if(article.recipes.size()>1||!recipe.label().isEmpty())rows.add(new Row("Recipe",com.wikipane.semantic.SemanticExtractor.rich(
                com.wikipane.document.WikiDocumentParser.escape(recipe.label().isEmpty()?"Method "+(recipeIndex+1)+" of "+article.recipes.size():recipe.label())),recipe.requirementsTable.source,null));
            for(com.wikipane.semantic.Recipe.Ingredient product:recipe.outputs)rows.add(recipeRow("Produces",product));
            for(com.wikipane.semantic.Recipe.Skill skill:recipe.skills) {
                rows.add(new Row(skill.name,skill.level.value,skill.level.source,skill.requirement));
                rows.add(new Row("XP",skill.experience.value,skill.experience.source,null));
            }
            for(com.wikipane.semantic.Recipe.Ingredient input:recipe.inputs)rows.add(recipeRow("Ingredient",input));
            for(String label:List.of("Tools","Facilities")) {
                Cell value=recipe.conditions.get(label);
                if(value!=null&&!value.value.text.isBlank())rows.add(new Row(label,value.value,value.source,null));
            }
        }
        List<Fact> candidates = new ArrayList<>();
        for (Node node : article.nodes) {
            if (node.table == null) continue;
            // A comparison or drop table can contain identically named columns for other entities.
            // Only infobox fields are safe to promote as the inspected entity's own statistics.
            boolean identityFacts = article.semantic.type == PageType.MONSTER
                ? node.table.classes.contains("infobox-monster")
                : node.table.classes.contains("infobox") || node.table.classes.contains("questdetails");
            if (identityFacts) candidates.addAll(node.facts);
        }
        // Labels are structural Wiki field names, never article titles or invented values.
        String[] priorities = article.semantic.type == PageType.MONSTER
            ? new String[]{"combat level", "hitpoints", "slayer level", "slayer xp", "attack style", "max hit", "weakness"}
            : article.semantic.type == PageType.QUEST
            ? new String[]{"start point", "official difficulty", "official length"}
            : new String[]{"level required", "growth time", "crop", "patch", "plant", "ingredients", "materials", "attack speed", "slot"};
        for (String label : priorities) {
            for (Fact fact : candidates) {
                if (fact.label.equalsIgnoreCase(label) && !fact.value.text.isBlank()
                    && fact.value.text.length() <= 220 && included.add(fact.source.unit)) {
                    rows.add(new Row(fact.label, fact.value, fact.source, null));
                    break;
                }
            }
        }
        if (rows.isEmpty() && article.semantic.type == PageType.ITEM) {
            for (Fact fact : candidates) {
                if (rows.size() >= 5) break;
                if (Set.of("equipable", "weight", "attack speed", "slot").contains(fact.label.toLowerCase(Locale.ROOT))
                    && fact.value.text.length() <= 100 && included.add(fact.source.unit))
                    rows.add(new Row(fact.label, fact.value, fact.source, null));
            }
        }
        // Preserve requirement expressions whole. Compact display can page through long content.
        for (RequirementSet set : article.requirements) {
            // Product/comparison tables describe other items, not requirements of the inspected item.
            if(article.semantic.type==PageType.ITEM&&candidates.stream().noneMatch(f->f.source.unit.equals(set.source.unit)))continue;
            // Combat preparations can contain prayer recommendations. Keep those in the reader,
            // not an in-game overlay; the compact monster view is limited to static sourced stats.
            if (article.semantic.type == PageType.MONSTER) break;
            if (included.contains(set.source.unit)) continue;
            if (rows.size() >= 10) break;
            for (RequirementGroup group : set.groups) {
                if (rows.size() >= 10) break;
                String key = set.source.unit + ":" + group.expression.original.html;
                if (included.add(key)) rows.add(new Row(group.label,
                    group.expression.original, set.source, group.expression));
            }
        }
        // Keep an actual source passage explaining the entity; no automatic summary or interpretation.
        for (Node node : article.nodes) {
            if (article.semantic.type == PageType.MONSTER) break;
            if (node.type == NodeType.TEXT && !node.navigation && node.source.section.isEmpty() && node.text != null
                && !node.text.text.isBlank() && node.text.text.length() <= 550) {
                rows.add(new Row("About", node.text, node.source, null));
                break;
            }
        }
        List<Relationship> links = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (Row row : rows)
            for (Link link : row.value.links)
                if (link.internal && !link.label.isBlank() && !link.url.contains("/w/File:") && !link.url.contains("#cite_")
                    && !link.url.equals(article.document.url) && urls.add(link.url))
                    links.add(new Relationship(Role.RELATIONSHIPS, link, row.source, false));
        // Use source sections for one-action expansion, rather than dumping unbounded drop tables.
        Set<Role> sectionRoles = new HashSet<>();
        for (Section section : article.semantic.sections) {
            if (!section.anchor.isEmpty() && Set.of(Role.DROPS, Role.SOURCES, Role.METHODS,
                    Role.LOCATIONS, Role.WALKTHROUGH, Role.REQUIREMENTS).contains(section.role)
                && sectionRoles.add(section.role)) {
                String url = com.wikipane.api.WikiUrls.resolve(article.document.url).newBuilder()
                    .fragment(section.anchor).build().toString();
                if (urls.add(url)) links.add(new Relationship(section.role,
                    new Link(section.title, url, true),
                    new Source(article.document.title, article.document.url, article.document.revision,
                        section.anchor, section.title, "section:" + section.anchor), false));
            }
        }
        links.sort(Comparator.comparingInt(link -> link.role == Role.RELATIONSHIPS ? 1 : 0));
        WikiDocument.Image image = null;
        for (Node node : article.nodes) {
            if (node.table != null && node.table.kind.toLowerCase(Locale.ROOT).contains("information")
                && !node.table.images.isEmpty()) { image = node.table.images.get(0); break; }
        }
        return new Inspection(article, rows, links, image,recipeIndex);
    }
    private static Row recipeRow(String label,com.wikipane.semantic.Recipe.Ingredient ingredient) {
        RichText value=com.wikipane.semantic.SemanticExtractor.rich(ingredient.quantity.html+" × "+ingredient.name.html);
        List<Link> links=new ArrayList<>(value.links);
        if(links.stream().noneMatch(link->link.url.equals(ingredient.target.url)))links.add(ingredient.target);
        value=new RichText(value.html,value.text,links);
        return new Row(label,value,ingredient.source,null,List.of(ingredient.source,ingredient.quantitySource));
    }

}
