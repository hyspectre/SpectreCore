package dev.hydev.spectrecore.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

/** Lightweight, bounded per-module counters. Disabled mode does not allocate or sample. */
public final class DiagnosticsService {
    public record Snapshot(String metric, long events, long failures, long elapsedNanos) {
        public double averageMs() { return events == 0 ? 0 : elapsedNanos / 1_000_000.0 / events; }
    }
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final int MAX_METRICS = 64;
    private final Map<String, Bucket> metrics = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private static final class Bucket {
        final LongAdder events = new LongAdder();
        final LongAdder failures = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
    }
    public DiagnosticsService(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public void enabled(boolean value) { enabled = value; }
    public long start() { return enabled ? System.nanoTime() : 0L; }
    public void record(String id, long started, boolean success) {
        if (!enabled || !VALID.matcher(id).matches()) return;
        Bucket bucket = metrics.get(id);
        if (bucket == null) {
            if (metrics.size() >= MAX_METRICS) return;
            bucket = metrics.computeIfAbsent(id, ignored -> new Bucket());
        }
        bucket.events.increment();
        if (!success) bucket.failures.increment();
        if (started > 0) bucket.totalNanos.add(Math.max(0L, System.nanoTime() - started));
    }
    public List<Snapshot> snapshot() {
        List<Snapshot> result = new ArrayList<>();
        metrics.forEach((id, b) -> result.add(new Snapshot(id, b.events.sum(), b.failures.sum(), b.totalNanos.sum())));
        result.sort(Comparator.comparing(Snapshot::metric));
        return List.copyOf(result);
    }
    public void reset() { metrics.clear(); }
}
