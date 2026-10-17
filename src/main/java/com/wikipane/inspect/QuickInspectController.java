package com.wikipane.inspect;

import com.wikipane.knowledge.*;
import com.wikipane.api.WikiUrls;
import com.wikipane.cache.ImageCache;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;

/** Owns temporary state only. All mutations are dispatched on the client thread; no sidebar history. */
@Slf4j
public final class QuickInspectController {
    public static final class View {
        public final HttpUrl lookup;
        public final Inspection inspection;
        public final InspectionLayout layout;
        public final BufferedImage image;
        public final String message;
        public final int page;
        public final java.util.List<String> messageLines;
        View(HttpUrl lookup, Inspection inspection, InspectionLayout layout, BufferedImage image,
             String message, int page) {
            this.lookup = lookup; this.inspection = inspection; this.layout = layout;
            this.image = image; this.message = message; this.page = page;
            this.messageLines = InspectionLayout.wrap(message, InspectionLayout.BODY, InspectionLayout.INNER);
        }
    }
    private final KnowledgeService knowledge;
    private final ImageCache images;
    private final ExecutorService workers;
    private final Consumer<Runnable> dispatch;
    private final Consumer<String> open;
    private volatile View view;
    private long generation;
    private boolean stopped;
    private Future<?> pending;
    public QuickInspectController(KnowledgeService knowledge, ImageCache images, ExecutorService workers,
                                  Consumer<Runnable> dispatch, Consumer<String> open) {
        this.knowledge = knowledge; this.images = images; this.workers = workers;
        this.dispatch = dispatch; this.open = open;
    }
    public View view() { return view; }
    public void inspect(HttpUrl lookup) {
        if (stopped || !WikiUrls.wiki(lookup)) return;
        cancel();
        long version = ++generation;
        view = new View(lookup, null, null, null, "Loading Wiki information…", 0);
        try {
            pending = workers.submit(() -> {
                try {
                    Inspection inspection = Inspection.project(knowledge.entity(lookup));
                    if (Thread.currentThread().isInterrupted()) return;
                    InspectionLayout layout = new InspectionLayout(inspection);
                    dispatch.accept(() -> {
                        if (!stopped && version == generation)
                            view = new View(lookup, inspection, layout, null, "", 0);
                    });
                    if (inspection.image != null && !Thread.currentThread().isInterrupted()) {
                        BufferedImage image;
                        try { image = images.load(inspection.image.url); }
                        catch (Exception ex) { log.debug("WikiPane inspection image unavailable", ex); return; }
                        dispatch.accept(() -> {
                            if (!stopped && version == generation && view != null)
                                view = new View(lookup, inspection, layout, image, "", view.page);
                        });
                    }
                } catch (Exception ex) {
                    log.debug("WikiPane inspection lookup failed", ex);
                    dispatch.accept(() -> {
                        if (!stopped && version == generation)
                            view = new View(lookup, null, null, null,
                                "Wiki information could not be loaded. Shift-right-click to retry or open the lookup in your browser.", 0);
                    });
                }
            });
        } catch (RejectedExecutionException ex) {
            view = new View(lookup, null, null, null, "WikiPane is busy. Shift-right-click to retry.", 0);
        }
    }
    public void page(int delta) {
        View current = view;
        if (current != null && current.layout != null)
            view = new View(current.lookup, current.inspection, current.layout, current.image, "",
                Math.max(0, Math.min(current.layout.pages.size() - 1, current.page + delta)));
    }
    public void recipe(int index) {
        View current=view;
        if(stopped||current==null||current.inspection==null
            ||current.inspection.article.semantic.type!=com.wikipane.semantic.SemanticDocument.PageType.ITEM
            ||index<0||index>=current.inspection.article.recipes.size())return;
        cancel();long version=++generation;
        try {
            pending=workers.submit(()->{
                try {
                    Inspection inspection=Inspection.project(current.inspection.article,index);
                    InspectionLayout layout=new InspectionLayout(inspection);
                    dispatch.accept(()->{
                        if(!stopped&&version==generation)view=new View(current.lookup,inspection,layout,current.image,"",0);
                    });
                } catch(RuntimeException error) { log.debug("WikiPane recipe projection unavailable",error); }
            });
        } catch(RejectedExecutionException ignored) {}
    }
    public void retry() { View current = view; if (current != null) inspect(current.lookup); }
    public void explore() {
        View current = view;
        if (current == null || current.inspection == null) return;
        KnowledgeArticle article = current.inspection.article;
        open.accept(WikiUrls.resolve(article.document.url).newBuilder().fragment(article.anchor).build().toString());
        dismiss();
    }
    public void related(int index) {
        View current = view;
        if (current == null || current.inspection == null || index < 0
            || index >= current.inspection.related.size()) return;
        open.accept(current.inspection.related.get(index).target.url);
        dismiss();
    }
    private void cancel() {
        if (pending == null) return;
        pending.cancel(true);
        if (workers instanceof ThreadPoolExecutor && pending instanceof Runnable)
            ((ThreadPoolExecutor) workers).remove((Runnable) pending);
        pending = null;
    }
    public void dismiss() { generation++; cancel(); view = null; }
    public void stop() { stopped = true; dismiss(); images.clear(); }
}
