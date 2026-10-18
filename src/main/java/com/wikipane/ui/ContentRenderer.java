package com.wikipane.ui;

import static com.wikipane.document.WikiDocumentParser.escape;

import com.wikipane.cache.ImageCache;
import com.wikipane.document.WikiDocument;
import com.wikipane.document.WikiDocument.Image;
import com.wikipane.semantic.SemanticDocument.*;
import com.wikipane.view.ArticleViewModel;
import com.wikipane.view.WikiPage;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;

/** Native components consume the page view model, never source table geometry or Wiki templates. */
public final class ContentRenderer {
    private final Consumer<String> links;
    private final ReaderComponents components;
    private final ReaderSelection selection;
    private com.wikipane.semantic.SemanticDocument pinDocument;
    private com.wikipane.view.ReaderLayout layout;
    private final ExecutorService workers;
    private final ImageCache images;
    private ImageViewer imageViewer;
    private boolean fragment, fragmentHighlights;
    private String fragmentScope="";
    void fragmentScope(String scope){fragmentScope=scope;}
    private Runnable contentChanged=() -> {};
    void contentChanged(Runnable changed) {contentChanged=changed;}
    void imageViewer(ImageViewer viewer) {imageViewer=viewer;}
    JPanel renderFragment(WikiPage page,ArticleViewModel.Area area,Map<String,Integer> saved,String variant,boolean highlights) {
        fragment=true; fragmentHighlights=highlights;
        return renderArea(page,area,saved,variant);
    }
    public void closeImageViewer() { if (imageViewer != null) imageViewer.close(); }
    private final List<Runnable> imageChecks = new ArrayList<>();
    private final Map<String, JComponent> anchors = new HashMap<>();
    private Map<String, Integer> tableState = new HashMap<>();
    private volatile long generation;
    private JPanel flow;
    private Map<String, String> anchorAliases = Map.of();
    private JPanel rendered;
    private final Set<String> mappedFactUnits=new HashSet<>();
    private Consumer<com.wikipane.view.MapDestination> mapRequests = destination -> {};
    private final Map<String, java.util.List<Location>> locationsByUnit = new HashMap<>();
    public void mapRequests(Consumer<com.wikipane.view.MapDestination> show) { mapRequests=show; }
    private JComponent mapActions(com.wikipane.view.MapDestination destination) {
        JPanel actions=column();
        if(destination.places.isEmpty())return actions;
        JButton show=linkButton(destination.action);
        show.setName("wiki.showOnMap"); show.putClientProperty("wiki.destination",destination);
        show.putClientProperty("wiki.location",destination.places.get(0).location);
        String label=destination.places.get(0).label;
        show.setToolTipText("<html>"+escape(label)+(destination.places.size()>1
            ? " · "+destination.places.size()+" locations" : "")+"</html>");
        show.addActionListener(event->mapRequests.accept(destination));
        actions.add(show);
        return actions;
    }
    private String progressionSkill = "";
    private Consumer<String> skillRequests = skill -> {};
    private final Map<Integer, java.util.List<JComponent>> levelRows = new TreeMap<>();
    private final java.util.List<Consumer<Integer>> levelRevealers = new ArrayList<>();
    public void skillRequests(Consumer<String> requests) { skillRequests = requests; }
    public JComponent currentLevelTarget() {
        Integer base = com.wikipane.context.SkillGuideProgress.base(progressionSkill, player);
        if (base == null) return null;
        for (Consumer<Integer> reveal : java.util.List.copyOf(levelRevealers)) reveal.accept(base);
        Integer target = com.wikipane.context.SkillGuideProgress.target(levelRows.keySet(), base);
        return target == null ? null : levelRows.get(target).get(0);
    }
    private void updateLevelRows() {
        Integer base = com.wikipane.context.SkillGuideProgress.base(progressionSkill, player);
        levelRows.forEach((level, rows) -> rows.forEach(row -> {
            row.putClientProperty("wiki.reachedLevel", base != null && level < base);
            row.repaint();
        }));
    }
    private com.wikipane.view.CompositionAudit audit;
    private int auditedWidth = -1, auditedState, auditedBand = -1;
    private boolean auditScheduled;
    private List<ArticleViewModel.Component> pending = List.of();
    private int revealed;
    private String areaId = "";
    private String selectedVariant = "", previousBreadcrumb = "";
    private final java.util.List<RequirementBadge> badges = new ArrayList<>();
    private com.wikipane.context.PlayerContextSnapshot player = com.wikipane.context.PlayerContextSnapshot.unavailable(false);
    private Consumer<java.util.List<Requirement>> contextRequests = requirements -> {};
    private java.util.Map<String, javax.swing.Icon> skillIcons = java.util.Map.of();
    private Future<?> evaluation;
    private final Set<String> presentedFacts = new HashSet<>();
    private final Map<String, JComponent> factViews = new HashMap<>();
    private final Map<String, List<Source>> factSources = new HashMap<>();
    private JPanel supportingFacts, technicalFacts;
    public ContentRenderer(Consumer<String> links, ExecutorService workers, ImageCache images) {
        this(links, workers, images, null, message -> {});
    }
    public ContentRenderer(Consumer<String> links, ExecutorService workers, ImageCache images,
                          Consumer<com.wikipane.pin.PinSelection> pin, Consumer<String> error) {
        this.links = links;
        components = new ReaderComponents(links);
        selection = pin == null ? null : new ReaderSelection(pin, error);
        this.workers = workers;
        this.images = images;
    }
    public void contextRequests(Consumer<java.util.List<Requirement>> requests) { contextRequests = requests; }
    public void playerContext(com.wikipane.context.PlayerContextSnapshot snapshot) { player = snapshot; evaluateRequirements(); updateLevelRows(); }
    public void skillIcons(java.util.Map<String, javax.swing.Icon> icons) { skillIcons = java.util.Map.copyOf(icons); evaluateRequirements(); }
    private void cancelEvaluation() {
        if (evaluation != null) {
            evaluation.cancel(false);
            if (workers instanceof ThreadPoolExecutor && evaluation instanceof Runnable) ((ThreadPoolExecutor)workers).remove((Runnable)evaluation);
            evaluation = null;
        }
    }
    private void evaluateRequirements() {
        cancelEvaluation();
        if (badges.isEmpty()) return;
        long version = generation;
        com.wikipane.context.PlayerContextSnapshot snapshot = player;
        java.util.List<RequirementBadge> rows = java.util.List.copyOf(badges);
        // Clear old account values immediately while the worker prepares the new annotations.
        com.wikipane.context.RequirementEvaluator evaluator = new com.wikipane.context.RequirementEvaluator();
        for (RequirementBadge row : rows) if (!snapshot.available || row.getClientProperty("wiki.requirementStatusReady") == null)
            row.update(evaluator.evaluate(row.condition,
            com.wikipane.context.PlayerContextSnapshot.unavailable(snapshot.enabled)), snapshot.enabled, null);
        try {
            evaluation = workers.submit(() -> {
                java.util.List<com.wikipane.context.RequirementEvaluator.Result> results = new ArrayList<>();
                for (RequirementBadge row : rows) results.add(evaluator.evaluate(row.condition, snapshot));
                SwingUtilities.invokeLater(() -> {
                    if (version != generation || player != snapshot) return;
                    for (int i = 0; i < rows.size(); i++) {
                        Requirement condition = rows.get(i).condition;
                        javax.swing.Icon icon = condition.kind == Requirement.Kind.SKILL
                            ? skillIcons.get(com.wikipane.context.PlayerContextSnapshot.skillKey(condition.name)) : null;
                        rows.get(i).update(results.get(i), snapshot.enabled, icon);
                    }
                });
            });
        } catch (RejectedExecutionException ex) { /* Unknown remains visible; no guessed state. */ }
    }
    public void clear() { clear(true); }
    private void clear(boolean notify) {
        generation++;
        cancelEvaluation(); badges.clear();
        levelRows.clear(); levelRevealers.clear(); progressionSkill = "";
        locationsByUnit.clear();mappedFactUnits.clear();
        if (notify) contextRequests.accept(java.util.List.of());
        if (notify) skillRequests.accept("");
        if (selection != null) selection.clear();
        components.pinContext(null);
        components.pinDocument(null); pinDocument = null;
        imageChecks.clear();
        anchors.clear();
        presentedFacts.clear(); factViews.clear(); factSources.clear(); previousBreadcrumb = "";
        pending = List.of();
        flow = null;
        rendered = null;
        auditedWidth = -1;
        auditScheduled = false;
    }
    public Map<String, Integer> tableState() {
        return new HashMap<>(tableState);
    }
    public JComponent anchor(String anchor) {
        return anchors.get(anchor);
    }
    public void loadVisibleImages() {
        new ArrayList<>(imageChecks).forEach(Runnable::run);
        revealNearBottom();
        scheduleAudit();
    }
    public JPanel render(WikiDocument article, Map<String, Integer> saved) {
        return render(article, saved, "");
    }
    public JPanel render(WikiDocument article, Map<String, Integer> saved, String anchor) {
        WikiPage page = new WikiPage(article);
        ArticleViewModel.Area area = page.view.sourceArea(anchor);
        return renderArea(page, area, saved, variantFor(page, anchor));
    }
    private String variantFor(WikiPage page, String anchor) {
        for (ArticleViewModel.Area area : page.view.areas)
            for (ArticleViewModel.Component component : area.components)
                if (component.node.fallback instanceof WikiDocument.Table) {
                    WikiDocument.Table table = (WikiDocument.Table) component.node.fallback;
                    if (table.anchor.equals(anchor) && !table.variant.isBlank())
                        return table.variant;
                }
        return "";
    }
    public JPanel renderArea(
        WikiDocument article, ArticleViewModel.Area area, Map<String, Integer> saved, String variant) {
        return renderArea(new WikiPage(article), area, saved, variant);
    }
    public JPanel renderArea(
        WikiPage page, ArticleViewModel.Area area, Map<String, Integer> saved, String variant) {
        WikiDocument article = page.document;
        layout = page.layout;
        clear(false);
        audit = page.view.audit;
        components.audit(audit);
        pinDocument = article.semantic;
        for(Node node:pinDocument.nodes) for(Location location:node.locations)
            locationsByUnit.computeIfAbsent(location.source.unit,ignored->new ArrayList<>()).add(location);
        progressionSkill = com.wikipane.context.SkillGuideProgress.skill(article.title);
        components.pinDocument(pinDocument);
        java.util.List<Requirement> requested = new ArrayList<>();
        for (Node node : pinDocument.nodes) for (RequirementSet set : node.requirements)
            for (RequirementGroup group : set.groups) requested.add(group.expression);
        for(com.wikipane.semantic.Recipe recipe:pinDocument.recipes)
            for(com.wikipane.semantic.Recipe.Skill skill:recipe.skills) requested.add(skill.requirement);
        contextRequests.accept(java.util.List.copyOf(requested));
        skillRequests.accept(progressionSkill);
        areaId = fragment?fragmentScope:area.id;
        anchorAliases = page.navigation.anchors(area.id);
        tableState = new HashMap<>(saved);
        selectedVariant = variant;
        JPanel body = column();
        rendered = body;

        if ((!fragment || fragmentHighlights) && (area.id.equals("info") || area.id.equals("overview")) && !layout.highlights(variant).isEmpty()) {
            body.add(components.highlights(layout.highlights(variant)));
            for (com.wikipane.view.ReaderLayout.Field field : layout.highlights(variant)) presentedFacts.add(field.identity());
            body.add(components.gap(8));
        }
        // These are actual Wiki links to complete related guides, not generated recommendations.
        for (Relationship relationship : area.related) {
            String guideName = relationship.target.label.isBlank()
                ? Objects.toString(com.wikipane.api.WikiUrls.title(
                                       com.wikipane.api.WikiUrls.resolve(relationship.target.url)),
                      "guide")
                : relationship.target.label;
            JButton guide = linkButton("<html>Read " + escape(guideName) + "</html>");
            guide.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            guide.setPreferredSize(new Dimension(200, 38));
            guide.setToolTipText(relationship.target.url);
            guide.addActionListener(e -> links.accept(relationship.target.url));
            body.add(guide);
        }
        if (area.id.equals("other") || area.id.equals("methods") || area.id.equals("sources")) {
            for (com.wikipane.knowledge.RelatedInformation.Group group : page.relatedInformation) {
                if (area.id.equals("methods") && !group.label.equals("Production & methods")
                    || area.id.equals("sources") && !group.label.equals("Sources")
                    || area.id.equals("other") && !group.label.equals("Related information")) continue;
                JPanel related = column();
                related.add(text("<b>" + escape(group.label) + " links</b>"));
                for (Relationship relationship : group.links) {
                    JButton destination = linkButton("<html>" + escape(relationship.target.label) + "</html>");
                    destination.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                    destination.setToolTipText("From: " + relationship.source.sectionTitle);
                    destination.addActionListener(e -> links.accept(relationship.target.url));
                    related.add(destination);
                }
                // Long link collections remain available without pushing the source content away.
                body.add(components.disclosure("Related links (" + group.links.size() + ")", related));
            }
        }
        pending = new ArrayList<>();
        for (ArticleViewModel.Component component : area.components)
            if (variantMatches(component, area))
                pending.add(component);
        flow = column();
        body.add(flow);
        supportingFacts = column(); technicalFacts = column();
        JComponent supporting = components.disclosure("Article details", supportingFacts);
        JComponent technical = components.disclosure("Technical details", technicalFacts);
        supporting.setVisible(false); technical.setVisible(false);
        body.add(supporting); body.add(technical);
        revealed = 0;
        appendNext(Math.max(6, tableState.getOrDefault("@shown:" + areaId, 6)));
        if (!fragment && pending.isEmpty() && area.related.isEmpty())
            body.add(text("No readable content was returned for this area."));
        if (!fragment) body.add(text("<p class='source'>Content: <a href='" + escape(article.url)
            + ("'>OSRS Wiki</a> · <a href='https://oldschool.runescape.wiki/w/RuneScape:Copyrights'>CC "
                + "BY-NC-SA</a></p>")));
        if (selection != null) selection.refresh(body);
        return body;
    }
    private void scheduleAudit() {
        if (rendered == null || auditScheduled)
            return;
        int width = rendered.getWidth(), state = tableState.hashCode(),
            band = rendered.getVisibleRect().y / 180;
        if (width <= 0 || width == auditedWidth && state == auditedState && band == auditedBand)
            return;
        long version = generation;
        auditScheduled = true;
        SwingUtilities.invokeLater(() -> {
            auditScheduled = false;
            if (version != generation || rendered == null)
                return;
            auditedWidth = rendered.getWidth();
            auditedState = tableState.hashCode();
            auditedBand = rendered.getVisibleRect().y / 180;
            SidebarLayoutValidator.inspect(rendered, audit);
        });
    }
    private boolean variantMatches(ArticleViewModel.Component component, ArticleViewModel.Area area) {
        if (component.node.table == null || component.node.table.variant.isEmpty())
            return true;
        String first = "";
        boolean selectedExists = false;
        for (ArticleViewModel.Component candidate : area.components) {
            if (candidate.node.table == null || !candidate.node.table.kind.equals(component.node.table.kind))
                continue;
            if (first.isEmpty())
                first = candidate.node.table.variant;
            if (candidate.node.table.variant.equals(selectedVariant))
                selectedExists = true;
        }
        return component.node.table.variant.equals(selectedExists ? selectedVariant : first);
    }
    private void appendNext(int count) {
        if (flow == null)
            return;
        int stop = Math.min(pending.size(), revealed + count);
        while (revealed < stop) {
            ArticleViewModel.Component component = pending.get(revealed++);
            com.wikipane.semantic.Recipe recipe=layout.recipe(component.node.source.unit);
            if(recipe!=null) {
                boolean entityVariant=pinDocument.nodes.stream().anyMatch(n -> n.table!=null && !n.table.variant.isEmpty() && n.table.variant.equals(recipe.variant));
                String recipeVariant=selectedVariant;
                if(recipeVariant.isEmpty())recipeVariant=pinDocument.nodes.stream().filter(n->n.table!=null&&!n.table.variant.isEmpty()).map(n->n.table.variant).findFirst().orElse("");
                if(entityVariant&&!recipe.variant.equals(recipeVariant)) continue;
                if(!component.node.source.unit.equals(recipe.requirementsTable.source.unit)) {
                    JComponent destination=anchors.get(recipe.requirementsTable.source.unit);
                    if(destination!=null)anchors.put(component.id,destination);
                    continue;
                }
            }
            components.pinContext(new com.wikipane.pin.PinContext(pinDocument, component.node));
            JPanel section = column();
            section.putClientProperty("wiki.source", component.node.id);
            String sourceSection = component.source.section;
            JComponent anchorDestination = section;
            if (!sourceSection.isBlank() && !anchors.containsKey(sourceSection)) {
                if (!fragment && !component.source.sectionTitle.equals(component.source.article)
                    && !(component.source.sectionTitle.equals("Details")
                        && (component.type == ArticleViewModel.ComponentType.FACTS
                            || component.type == ArticleViewModel.ComponentType.REQUIREMENTS))) {
                    JComponent heading = sourceHeading(layout.section(component.id), component.source);
                    section.add(heading);
                    anchorDestination = (JComponent) heading.getClientProperty("wiki.heading.label");
                }
                anchors.put(sourceSection, anchorDestination);
            }
            anchors.put(component.id, anchorDestination);
            JComponent sourceDestination = anchors.getOrDefault(sourceSection, anchorDestination);
            anchorAliases.forEach((alias, target) -> {
                if (target.equals(component.source.section))
                    anchors.putIfAbsent(alias, sourceDestination);
            });
            for (Citation use : com.wikipane.view.PresentationValidator.citations(component.node))
                if (!use.id.isBlank())
                    anchors.putIfAbsent(use.id, section);
            if (component.node.fallback instanceof WikiDocument.Table) {
                String id = ((WikiDocument.Table) component.node.fallback).anchor;
                if (!id.isBlank())
                    anchors.putIfAbsent(id, anchorDestination);
            }
            switch (component.type) {
                case TEXT:
                    if (component.node.text != null) {
                        com.wikipane.view.ReaderLayout.Section source = layout.section(component.id);
                        JComponent prose = text(source.textHtml);
                        if (source.textCondition != null) {
                            RequirementBadge row = new RequirementBadge(source.textCondition, prose);
                            badges.add(row); section.add(row);
                        } else section.add(prose);
                    }
                    break;
                case FACTS:
                case REQUIREMENTS:
                    renderFacts(component, section);
                    break;
                case TABLE:
                    if(recipe!=null) section.add(recipe(recipe));
                    else if (component.node.table != null)
                        section.add(table(component.node.table, component.id));
                    break;
                case REFERENCES:
                    for (com.wikipane.view.ReaderLayout.ReferenceCard ref : layout.references)
                        if (ref.source.unit.equals(component.node.source.unit)) {
                            JPanel card = components.card("wiki.reference");
                            card.add(text("<b>" + escape(ref.label) + "</b>"));
                            card.add(text(ref.html));
                            if (!ref.backlinks.isEmpty()) {
                                JButton returnTo = linkButton("Referenced from (" + ref.backlinks.size() + ")");
                                JPopupMenu destinations = new JPopupMenu();
                                for (int back = 0; back < ref.backlinks.size(); back++) {
                                    String url = ref.backlinks.get(back);
                                    JMenuItem destination = new JMenuItem("Citation " + (back + 1));
                                    destination.addActionListener(e -> links.accept(url));
                                    destinations.add(destination);
                                }
                                returnTo.setComponentPopupMenu(destinations);
                                returnTo.addActionListener(e -> {
                                    if (ref.backlinks.size() == 1) links.accept(ref.backlinks.get(0));
                                    else destinations.show(returnTo, 0, returnTo.getHeight());
                                });
                                card.add(returnTo);
                            }
                            anchors.put(ref.id, card);
                            section.add(card);
                            section.add(components.gap(6));
                        }
                    break;
                case IMAGE:
                    if (component.node.fallback instanceof Image)
                        section.add(image((Image) component.node.fallback));
                    break;
                default:
                    if (component.node.text != null)
                        section.add(text(component.node.text.html));
                    else if (component.node.table != null)
                        section.add(table(component.node.table, component.id));
                    else if (component.node.fallback instanceof Image)
                        section.add(image((Image) component.node.fallback));
                    else
                        section.add(text("This content is available through Open in browser."));
                    break;
            }
            if(component.node.table==null||!component.node.table.kind.isEmpty())
                section.add(mapActions(com.wikipane.view.MapDestination.forNode(component.node).excludingUnits(mappedFactUnits)));
            flow.add(section);
            components.pinContext(null);
        }
        if (selection != null && rendered != null) selection.refresh(rendered);
        contentChanged.run();
        evaluateRequirements();
        tableState.put("@shown:" + areaId, revealed);
        flow.revalidate();
        flow.repaint();
        scheduleAudit();
    }
    public void revealAnchor(String anchor) {
        while (!anchors.containsKey(anchor) && revealed < pending.size()) appendNext(6);
    }
    public void revealAll() {
        appendNext(pending.size());
    }
    private void revealNearBottom() {
        if (flow == null || revealed >= pending.size() || !flow.isShowing())
            return;
        Rectangle visible = flow.getVisibleRect();
        if (visible.y + visible.height >= flow.getHeight() - 240)
            appendNext(6);
    }
    public static JPanel column() {
        return ReaderComponents.column();
    }
    private JComponent sourceHeading(com.wikipane.view.ReaderLayout.Section section, Source source) {
        String breadcrumb = section.breadcrumb.equals(previousBreadcrumb) ? "" : section.breadcrumb;
        previousBreadcrumb = section.breadcrumb;
        return components.heading(section.title, breadcrumb, source);
    }
    private void renderFacts(ArticleViewModel.Component component, JPanel target) {
        Table table = component.node.table;
        com.wikipane.view.ReaderLayout.Section section = layout.section(component.id);
        if (component.type == ArticleViewModel.ComponentType.FACTS && table != null && !table.images.isEmpty()
            && !table.kind.equals("Combat bonuses")
            && component.units.contains(table.source.unit + ".image0")) {
            target.add(image(table.images.get(0), true));
            target.add(components.gap(6));
        }
        com.wikipane.pin.PinContext basePin = components.pinContext();
        for (com.wikipane.view.ReaderLayout.ConditionGroup condition : section.requirements) {
            if (basePin != null) components.pinContext(basePin.condition(condition.source, condition.title, condition.condition));
            JPanel card = components.card("wiki.requirements");
            card.add(text("<b>" + escape(condition.title) + "</b>"));
            card.add(components.gap(4));
            for (com.wikipane.view.RequirementRows.Row content : condition.rows) {
                if (basePin != null) components.pinContext(basePin.condition(condition.source, condition.title,
                    content.condition == null ? condition.condition : content.condition));
                JComponent source = components.condition(content.html);
                JComponent row = source;
                if (content.condition != null) {
                    RequirementBadge badge = new RequirementBadge(content.condition, source);
                    badges.add(badge); row = badge;
                }
                JPanel inset = column();
                inset.setBorder(BorderFactory.createEmptyBorder(0, Math.min(4, content.depth) * 6, 0, 0));
                inset.putClientProperty("wiki.requirement.depth", content.depth);
                if (!content.parent.isBlank()) source.setToolTipText("Prerequisite of: " + content.parent);
                inset.add(row); card.add(inset);
            }
            target.add(card);
            target.add(components.gap(8));
        }
        for (com.wikipane.view.ReaderLayout.FactGroup group : section.facts) {
            if (basePin != null) components.pinContext(basePin.group(group.title));
            java.util.List<com.wikipane.view.ReaderLayout.Field> visible = new ArrayList<>();
            for (com.wikipane.view.ReaderLayout.Field field : group.fields) {
                Fact fact = component.facts.stream().filter(f -> f.source.unit.equals(field.source.unit)
                    && f.label.equals(field.label) && f.value.html.equals(field.value.html)).findFirst().orElse(null);
                String key = fact == null ? field.identity() : com.wikipane.view.FactPolicy.duplicateKey(fact);
                List<Source> sources = factSources.computeIfAbsent(key, ignored -> new ArrayList<>());
                sources.add(field.source);
                if (!presentedFacts.add(field.identity()) || sources.size() > 1) {
                    JComponent existing = factViews.get(key);
                    if (existing != null) existing.putClientProperty("wiki.fact.sources", List.copyOf(sources));
                    audit.add("FACT_ALREADY_PRESENTED", field.source.unit,
                        com.wikipane.view.CompositionAudit.Outcome.REPAIRED,
                        "Equivalent source fact consolidated; all provenance retained.");
                    continue;
                }
                com.wikipane.view.FactPolicy.Importance importance = com.wikipane.view.FactPolicy.importance(field.label, field.value.text);
                if (importance == com.wikipane.view.FactPolicy.Importance.PRIMARY) {
                    visible.add(field);
                    // The view is attached below, after its source-aware group heading.
                } else {
                    JComponent row = components.field(field);
                    row.putClientProperty("wiki.fact.sources", List.copyOf(sources)); factViews.put(key, row);
                    JPanel destination = importance == com.wikipane.view.FactPolicy.Importance.TECHNICAL ? technicalFacts : supportingFacts;
                    destination.add(row); destination.getParent().setVisible(true);
                }
            }
            if (visible.isEmpty()) continue;
            if (!group.title.isBlank())
                target.add(components.heading(group.title, "", null));
            JPanel card = components.card("wiki.facts");
            for (com.wikipane.view.ReaderLayout.Field field : visible) {
                JComponent row = components.field(field); card.add(row);
                Fact fact = component.facts.stream().filter(f -> f.source.unit.equals(field.source.unit)
                    && f.label.equals(field.label)).findFirst().orElse(null);
                String key = fact == null ? field.identity() : com.wikipane.view.FactPolicy.duplicateKey(fact);
                factViews.put(key, row); row.putClientProperty("wiki.fact.sources", List.copyOf(factSources.get(key)));
                if(fact!=null) {
                    com.wikipane.view.MapDestination destination=com.wikipane.view.MapDestination.forFact(fact,component.node.locations);
                    if(!destination.places.isEmpty()) {
                        card.add(mapActions(destination));mappedFactUnits.add(fact.source.unit);
                    }
                }
            }
            target.add(card);
            target.add(components.gap(8));
        }
        components.pinContext(basePin);
        if (table != null)
            for (int i = 1; i < table.images.size(); i++) target.add(image(table.images.get(i)));
    }
    private JComponent recipe(com.wikipane.semantic.Recipe recipe) {
        JPanel card=components.card("wiki.recipe");
        com.wikipane.pin.PinContext original=components.pinContext();
        if(original!=null&&!recipe.label().isEmpty())original=original.group(recipe.label()+" recipe");
        components.pinContext(original);
        if(!recipe.label().isEmpty())card.add(text("<span class='muted tiny'>"+escape(recipe.label())+"</span>"));
        card.add(text("<b>Produces</b>"));
        for(com.wikipane.semantic.Recipe.Ingredient output:recipe.outputs) card.add(recipeIngredient(output));
        for(com.wikipane.semantic.Recipe.Skill skill:recipe.skills) {
            JPanel row=column();
            if(original!=null)components.pinContext(original.condition(skill.level.source,skill.name,skill.requirement));
            JComponent condition=components.condition(escape(skill.name)+" "+skill.level.value.html);
            RequirementBadge badge=new RequirementBadge(skill.requirement,condition);badges.add(badge);row.add(badge);
            components.pinContext(original);
            row.add(components.field(new com.wikipane.view.ReaderLayout.Field("XP",skill.experience.value,skill.experience.source)));
            card.add(row);
        }
        card.add(text("<b>Ingredients</b>"));
        for(com.wikipane.semantic.Recipe.Ingredient input:recipe.inputs) card.add(recipeIngredient(input));
        for(String label:java.util.List.of("Tools","Facilities","Members","Ticks")) {
            Cell value=recipe.conditions.get(label);
            if(value!=null&&!value.value.text.isBlank())card.add(components.field(new com.wikipane.view.ReaderLayout.Field(label,value.value,value.source)));
        }
        JComponent disclosure;
        JPanel source=column();source.setVisible(false);
        // Reuse the complete source table renderer; no unsupported values are discarded.
        com.wikipane.pin.PinContext sourceContext=original;
        disclosure = components.disclosure("Costs & source details", source);
        JButton details = (JButton) disclosure.getComponent(0);
        details.addActionListener(e -> {
            if(source.getComponentCount()==0) {
                components.pinContext(sourceContext);
                source.add(table(recipe.requirementsTable,recipe.id+".requirements"));
                source.add(table(recipe.materialsTable,recipe.id+".materials"));
                components.pinContext(null);
                if(selection!=null&&rendered!=null)selection.refresh(rendered);
                contentChanged.run();
            }
            card.revalidate();
        });
        card.add(disclosure);components.pinContext(original);
        return card;
    }
    private JComponent recipeIngredient(com.wikipane.semantic.Recipe.Ingredient ingredient) {
        com.wikipane.pin.PinContext previous=components.pinContext();
        if(previous!=null)components.pinContext(previous.record(ingredient.name.text));
        com.wikipane.view.ReaderLayout.Field item=new com.wikipane.view.ReaderLayout.Field("Item",ingredient.name,ingredient.source);
        com.wikipane.view.ReaderLayout.Field count=new com.wikipane.view.ReaderLayout.Field("Quantity",ingredient.quantity,ingredient.quantitySource);
        String name=ingredient.name.html;
        if(ingredient.name.links.isEmpty())name="<a href='"+escape(ingredient.target.url)+"'>"+name+"</a>";
        JComponent row=components.recipeItem(item,count,name,ingredient.icon==null?null:image(ingredient.icon,false,true));
        components.pinContext(previous);return row;
    }

    private JComponent table(Table table, String key) {
        JPanel group = column();
        if (!layout.tableTitle(table).isBlank())
            group.add(components.heading(layout.tableTitle(table), "", null));
        JPanel records = column();
        group.add(records);
        java.util.List<com.wikipane.view.ReaderLayout.Record> rows = layout.records(table);
        JButton more = linkButton("Show more entries");
        JButton collapse = linkButton("Show fewer entries"); collapse.setName("wiki.collapseTable");
        int[] shown = {0};
        String[] previous = {""};
        com.wikipane.pin.PinContext tableContext = components.pinContext();
        Runnable next = () -> {
            com.wikipane.pin.PinContext previousContext = components.pinContext();
            components.pinContext(tableContext);
            records.remove(more); records.remove(collapse);
            int end = Math.min(rows.size(), shown[0] + 20);
            for (int i = shown[0]; i < end; i++) {
                com.wikipane.view.ReaderLayout.Record record = rows.get(i);
                if (!record.group.isBlank() && !record.group.equals(previous[0])
                    && !record.group.equals(table.source.article)) {
                    // A column-header-only row does not need to repeat the labels printed on every record.
                    if (!record.columnHeader
                        || rows.stream().noneMatch(r -> r.identity != null || !r.fields.isEmpty() || !r.metadata.isEmpty()))
                        records.add(components.heading(record.group, "", null));
                }
                previous[0] = record.group;
                if (record.identity != null || !record.fields.isEmpty() || !record.metadata.isEmpty()) {
                    JComponent card = components.record(record, record.icon == null ? null : image(record.icon, false, true));
                    Integer level = progressionSkill.isEmpty() ? null : com.wikipane.context.SkillGuideProgress.level(record);
                    if (level != null) {
                        JPanel contextRow = new JPanel(new BorderLayout()) {
                            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
                            @Override public void paint(Graphics graphics) {
                                Graphics2D copy = (Graphics2D) graphics.create();
                                if (Boolean.TRUE.equals(getClientProperty("wiki.reachedLevel")))
                                    copy.setComposite(AlphaComposite.SrcOver.derive(.72f));
                                super.paint(copy); copy.dispose();
                            }
                        };
                        contextRow.setOpaque(false); contextRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                        contextRow.putClientProperty("wiki.skillLevel", level);
                        contextRow.add(card); levelRows.computeIfAbsent(level, ignored -> new ArrayList<>()).add(contextRow); card = contextRow;
                    }
                    records.add(card);
                    java.util.List<Location> locations=new ArrayList<>();
                    for(String unit:record.units) locations.addAll(locationsByUnit.getOrDefault(unit,java.util.List.of()));
                    records.add(mapActions(com.wikipane.view.MapDestination.forRecord(locations,record)));
                    records.add(components.gap(3));
                }
                for (int n = 0; n < record.nested.size(); n++)
                    records.add(table(record.nested.get(n), key + ".r" + i + ".n" + n));
            }
            shown[0] = end;
            tableState.put(key, end);
            if (end < rows.size()) {
                more.setText("Show more (" + (rows.size() - end) + " remaining)");
                records.add(more);
            }
            if (end > 20) records.add(collapse);
            components.pinContext(previousContext);
            if (selection != null && rendered != null) selection.refresh(rendered);
            contentChanged.run();
            group.revalidate();
            group.repaint();
            updateLevelRows();
            scheduleAudit();
        };
        int wanted = Math.max(20, tableState.getOrDefault(key, 20));
        do {
            next.run();
        } while (shown[0] < Math.min(wanted, rows.size()));
        if (!progressionSkill.isEmpty()) {
            long version = generation;
            levelRevealers.add(base -> {
                if (version != generation || rendered == null || !SwingUtilities.isDescendingFrom(group, rendered)) return;
                java.util.Map<Integer, Integer> positions = new TreeMap<>();
                for (int i = 0; i < rows.size(); i++) {
                    Integer level = com.wikipane.context.SkillGuideProgress.level(rows.get(i));
                    if (level != null) positions.putIfAbsent(level, i);
                }
                Integer target = com.wikipane.context.SkillGuideProgress.target(positions.keySet(), base);
                if (target != null) while (shown[0] <= positions.get(target)) next.run();
            });
        }
        more.addActionListener(e -> next.run());
        collapse.addActionListener(e -> {
            levelRows.values().forEach(list -> list.removeIf(row -> SwingUtilities.isDescendingFrom(row, records)));
            levelRows.values().removeIf(List::isEmpty);
            records.removeAll(); shown[0] = 0; previous[0] = ""; next.run();
            more.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
        });
        return group;
    }
    private JButton linkButton(String title) {
        JButton button = new JButton(title) {
            @Override public Dimension getPreferredSize() {
                javax.swing.text.View html=(javax.swing.text.View)getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
                if(html==null)return super.getPreferredSize();
                int width=getWidth()>0?getWidth():200;Insets inset=getInsets();
                html.setSize(Math.max(1,width-inset.left-inset.right),0);
                return new Dimension(width,Math.max(28,(int)Math.ceil(html.getPreferredSpan(javax.swing.text.View.Y_AXIS))+inset.top+inset.bottom));
            }
            @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        };
        button.setForeground(ReaderComponents.ACCENT);
        button.setMargin(new Insets(5, 6, 5, 6));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return button;
    }
    private JComponent image(Image image) {
        return image(image, false);
    }
    private JComponent image(Image image, boolean identity) { return image(image, identity, false); }
    private JComponent image(Image image, boolean identity, boolean decoration) {
        int maxWidth = decoration ? 22 : identity ? 88 : 200, maxHeight = decoration ? 22 : identity ? 88 : 220;
        FittedThumbnail label = new FittedThumbnail(maxWidth, maxHeight, image.width, image.height, audit, decoration);
        label.putClientProperty("html.disable", true);
        label.setText(decoration ? "" : "…");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.getAccessibleContext().setAccessibleName(image.alt.isBlank() ? "Wiki image" : image.alt);
        label.setForeground(ReaderComponents.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setToolTipText(decoration ? null : image.alt);
        label.setBorder(BorderFactory.createEmptyBorder(decoration ? 0 : 4, 0, decoration ? 0 : 4, 0));
        if (!decoration) {
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (imageViewer == null) imageViewer = new ImageViewer(workers, images);
                    imageViewer.open(label, image);
                }
            });
        }
        long version = generation;
        boolean[] requested = {false};
        imageChecks.add(() -> {
            if (requested[0] || !label.isShowing() || label.getVisibleRect().isEmpty())
                return;
            requested[0] = true;
            try {
                workers.submit(() -> {
                    try {
                        java.awt.image.BufferedImage decoded = images.load(image.url);
                        double fit = Math.min(1,
                            Math.min((double) maxWidth / decoded.getWidth(),
                                (double) maxHeight / decoded.getHeight()));
                        java.awt.image.BufferedImage loaded;
                        if (fit < 1) {
                            loaded = new java.awt.image.BufferedImage(
                                Math.max(1, (int) (decoded.getWidth() * fit)),
                                Math.max(1, (int) (decoded.getHeight() * fit)),
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            Graphics2D g = loaded.createGraphics();
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g.drawImage(decoded, 0, 0, loaded.getWidth(), loaded.getHeight(), null);
                            g.dispose();
                        } else
                            loaded = decoded;
                        SwingUtilities.invokeLater(() -> {
                            if (version != generation)
                                return;
                            label.setText("");
                            label.image(loaded);
                            label.revalidate();
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            if (version != generation) return;
                            if (!decoration) {
                                label.unavailable();
                                label.setToolTipText("Image unavailable · Click to try the full-size image");
                            }
                        });
                    }
                });
            } catch (RejectedExecutionException ignored) {
            }
        });
        return label;
    }
    public JEditorPane text(String fragment) {
        return components.text(fragment);
    }
}
