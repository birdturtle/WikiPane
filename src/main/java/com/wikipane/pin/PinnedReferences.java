package com.wikipane.pin;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** Client-thread session pins. Layout runs on the worker; no Wiki request or player-state processing. */
@Slf4j
public final class PinnedReferences {
    public static final int LIMIT = 5;
    public static final class Pin {
        public final int slot;
        public final PinLayout layout;
        public volatile int page;
        private Pin(int slot, PinLayout layout) { this.slot = slot; this.layout = layout; }
    }
    private static final class Pending {
        final int slot;
        Future<?> future;
        Pending(int slot) { this.slot = slot; }
    }
    private final ExecutorService workers;
    private final Consumer<Runnable> dispatch;
    private final Consumer<Pin> add, remove;
    private final Consumer<String> source, error;
    private final Map<String, Pending> pending = new HashMap<>();
    private final Map<String, Pin> pins = new HashMap<>();
    private boolean stopped;
    public PinnedReferences(ExecutorService workers, Consumer<Runnable> dispatch, Consumer<Pin> add,
                            Consumer<Pin> remove, Consumer<String> source, Consumer<String> error) {
        this.workers = workers; this.dispatch = dispatch; this.add = add; this.remove = remove;
        this.source = source; this.error = error;
    }
    public void pin(PinSelection selection) {
        if (stopped || pins.containsKey(selection.id) || pending.containsKey(selection.id)) return;
        if (pins.size() + pending.size() >= LIMIT) {
            error.accept("You have five pinned references. Unpin one before adding another."); return;
        }
        Set<Integer> used = new HashSet<>();
        for (Pin pin : pins.values()) used.add(pin.slot);
        for (Pending request : pending.values()) used.add(request.slot);
        int slot = 1;
        while (used.contains(slot)) slot++;
        Pending request = new Pending(slot);
        pending.put(selection.id, request);
        try {
            request.future = workers.submit(() -> {
                try {
                    PinLayout layout = new PinLayout(selection);
                    dispatch.accept(() -> {
                        if (stopped || pending.get(selection.id) != request) return;
                        pending.remove(selection.id);
                        Pin pin = new Pin(request.slot, layout); pins.put(selection.id, pin); add.accept(pin);
                    });
                } catch (RuntimeException ex) {
                    log.debug("WikiPane pin layout unavailable", ex);
                    dispatch.accept(() -> {
                        if (stopped || pending.get(selection.id) != request) return;
                        pending.remove(selection.id);
                        error.accept(ex instanceof IllegalArgumentException ? ex.getMessage() : "That selection could not be pinned.");
                    });
                }
            });
        } catch (RejectedExecutionException ex) {
            pending.remove(selection.id); error.accept("WikiPane is busy. Try pinning the selection again.");
        }
    }
    public boolean active(Pin pin) { return !stopped && pins.get(pin.layout.selection.id) == pin; }
    public void page(Pin pin, int delta) {
        if (active(pin)) pin.page = Math.max(0, Math.min(pin.layout.pages.size() - 1, pin.page + delta));
    }
    public void openSource(Pin pin) {
        if (active(pin)) source.accept(pin.layout.selection.sources.get(0).link());
    }
    public void unpin(Pin pin) {
        if (!active(pin)) return;
        pins.remove(pin.layout.selection.id); remove.accept(pin);
    }
    public void stop() {
        stopped = true;
        for (Pending request : pending.values()) if (request.future != null) request.future.cancel(true);
        pending.clear();
        for (Pin pin : pins.values()) remove.accept(pin);
        pins.clear();
    }
}
