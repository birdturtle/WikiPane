package com.wikipane.pin;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PinnedReferencesTest {
    private static final class Workers extends AbstractExecutorService {
        final Queue<Runnable> queue = new ArrayDeque<>(); boolean stopped;
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; List<Runnable> list = List.copyOf(queue); queue.clear(); return list; }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped && queue.isEmpty(); }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
        public void execute(Runnable task) { if (stopped) throw new RejectedExecutionException(); queue.add(task); }
        void runAll() { while (!queue.isEmpty()) queue.remove().run(); }
    }
    private static final class Session {
        final Workers workers = new Workers();
        final List<Runnable> dispatch = new ArrayList<>();
        final List<PinnedReferences.Pin> added = new ArrayList<>(), removed = new ArrayList<>();
        final List<String> sources = new ArrayList<>(), errors = new ArrayList<>();
        final PinnedReferences pins = new PinnedReferences(workers, dispatch::add, added::add,
            removed::add, sources::add, errors::add);
        void complete() { workers.runAll(); flush(); }
        void flush() { List<Runnable> copy = List.copyOf(dispatch); dispatch.clear(); copy.forEach(Runnable::run); }
    }
    @Test public void selectingAndPinningCreatesAnIndependentReferenceWithoutOpeningAnything() throws Exception {
        Session session = new Session();
        PinSelection selection = PinSelectionTest.simple("<p>Keep this useful reference.</p>");
        session.pins.pin(selection);
        assertTrue(session.added.isEmpty()); session.complete();
        assertEquals(1, session.added.size());
        PinnedReferences.Pin pin = session.added.get(0);
        assertSame(selection, pin.layout.selection); assertTrue(session.pins.active(pin));
        assertTrue(session.sources.isEmpty()); assertTrue(session.errors.isEmpty());
        session.pins.openSource(pin);
        assertEquals(selection.sources.get(0).link(), session.sources.get(0));
        session.pins.unpin(pin);
        assertFalse(session.pins.active(pin)); assertEquals(List.of(pin), session.removed);
        session.pins.openSource(pin); assertEquals(1, session.sources.size());
    }
    @Test public void duplicateSelectionsDoNotCreateDuplicateOverlays() throws Exception {
        Session session = new Session();
        PinSelection first = PinSelectionTest.simple("<p>One reference.</p>");
        PinSelection same = PinSelectionTest.simple("<p>One reference.</p>");
        assertEquals(first.id, same.id);
        session.pins.pin(first); session.pins.pin(same); session.complete();
        session.pins.pin(same); session.complete();
        assertEquals(1, session.added.size());
    }
    @Test public void currentPageOpensItsOwnSourceAndDuplicatePinIsEmphasized() throws Exception {
        com.wikipane.document.WikiDocument doc=PinSelectionTest.document("<h2><span id='First'>First</span></h2><p>First passage.</p>"
            +"<h2><span id='Second'>Second</span></h2><p>"+"Second passage repeated. ".repeat(20)+"</p>");
        PinSelection selection=PinSelectionTest.edt(() -> {
            java.util.List<PinSelection.Fragment> fragments=new ArrayList<>();
            for(com.wikipane.semantic.SemanticDocument.Node node:doc.semantic.nodes)
                if(node.text!=null && node.type==com.wikipane.semantic.SemanticDocument.NodeType.TEXT)
                    fragments.add(PinSelectionTest.whole(PinSelectionTest.pane(node.text.html,new PinContext(doc.semantic,node))));
            return new PinSelection(fragments);
        });
        Session session=new Session();session.pins.pin(selection);session.complete();
        PinnedReferences.Pin pin=session.added.get(0);session.pins.page(pin,100);
        session.pins.openSource(pin);assertTrue(session.sources.get(0).endsWith("#Second"));
        session.pins.pin(selection);assertEquals(0,pin.page);assertTrue(pin.emphasizeUntil>System.nanoTime());
        assertEquals(1,session.added.size());
    }
    @Test public void pinsAreBoundedAndUnpinningReusesTheFreePositionSlot() throws Exception {
        Session session = new Session();
        for (int i = 0; i < PinnedReferences.LIMIT + 1; i++) session.pins.pin(PinSelectionTest.simple("<p>Reference " + i + ".</p>"));
        session.complete();
        assertEquals(PinnedReferences.LIMIT, session.added.size()); assertEquals(1, session.errors.size());
        Set<Integer> slots = new HashSet<>(); for (PinnedReferences.Pin pin : session.added) assertTrue(slots.add(pin.slot));
        PinnedReferences.Pin removed = session.added.get(0); session.pins.unpin(removed);
        session.pins.pin(PinSelectionTest.simple("<p>Another reference.</p>")); session.complete();
        assertEquals(removed.slot, session.added.get(session.added.size() - 1).slot);
    }
    @Test public void shutdownRemovesPinsAndIgnoresPendingCompletions() throws Exception {
        Session session = new Session();
        session.pins.pin(PinSelectionTest.simple("<p>First reference.</p>")); session.complete();
        session.pins.pin(PinSelectionTest.simple("<p>Pending reference.</p>")); session.workers.runAll();
        session.pins.stop(); session.flush();
        assertEquals(1, session.added.size()); assertEquals(session.added, session.removed);
        assertFalse(session.pins.active(session.added.get(0)));
        session.pins.pin(PinSelectionTest.simple("<p>After shutdown.</p>")); session.complete();
        assertEquals(1, session.added.size());
    }
    @Test public void rejectedQueueDoesNotReserveSlotsForever() throws Exception {
        Session session = new Session(); session.workers.shutdown();
        for (int i = 0; i < 7; i++) session.pins.pin(PinSelectionTest.simple("<p>Reference " + i + ".</p>"));
        assertTrue(session.added.isEmpty()); assertEquals(7, session.errors.size());
        assertTrue(session.errors.stream().allMatch(error -> error.contains("busy")));
    }
    @Test public void nativePinnedPagesRemainSmallTranslucentAndFullyReachable() throws Exception {
        Session session = new Session();
        session.pins.pin(PinSelectionTest.simple("<p>" + "A useful reference with <b>emphasis</b>. ".repeat(35) + "</p>"));
        session.complete(); PinnedReferences.Pin pin = session.added.get(0);
        assertTrue(pin.layout.pages.size() > 1);
        for (int page = 0; page < pin.layout.pages.size(); page++) {
            session.pins.page(pin, page - pin.page); assertEquals(page, pin.page);
            BufferedImage image = new BufferedImage(300, 500, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics(); Dimension size = PinnedOverlay.paint(graphics, pin.layout, pin.page); graphics.dispose();
            assertEquals(210, size.width); assertTrue("Pin is too tall", size.height <= 240);
            int translucent = 0;
            for (int y = 0; y < size.height; y++) for (int x = 0; x < size.width; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) translucent++;
            }
            assertTrue(translucent > size.width * size.height / 2);
        }
        session.pins.page(pin, 100); assertEquals(pin.layout.pages.size() - 1, pin.page);
        session.pins.page(pin, -1000); assertEquals(0, pin.page);
    }
}
