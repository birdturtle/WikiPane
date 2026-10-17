package com.wikipane.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small access-ordered cache. Errors are never inserted. */
public final class WikiCache<K, V> {
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    private final long ttl;
    private final LongSupplier clock;
    public WikiCache(int capacity, long ttl) {
        this(capacity, ttl, System::currentTimeMillis);
    }
    public WikiCache(int capacity, long ttl, LongSupplier clock) {
        this.capacity = capacity;
        this.ttl = ttl;
        this.clock = clock;
    }
    public synchronized V get(K key) {
        Entry<V> e = entries.get(key);
        if (e == null)
            return null;
        if (clock.getAsLong() - e.time >= ttl) {
            entries.remove(key);
            return null;
        }
        return e.value;
    }
    public synchronized void put(K key, V value) {
        entries.put(key, new Entry<>(value, clock.getAsLong()));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    public synchronized void clear() {
        entries.clear();
    }
    public synchronized int size() {
        return entries.size();
    }
    private static class Entry<V> {
        final V value;
        final long time;
        Entry(V v, long t) {
            value = v;
            time = t;
        }
    }
}
