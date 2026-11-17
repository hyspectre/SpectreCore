package dev.hydev.spectrecore.treasure;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One deadline per player for BOTH combat and mining. Times are absolute milliseconds. */
public final class TreasureLedger {
    private final Map<UUID, Long> deadlines = new HashMap<>();
    public boolean ready(UUID id, long now) { return now >= deadlines.getOrDefault(id, 0L); }
    public void start(UUID id, long now, long duration) { deadlines.put(id, now + Math.max(0, duration)); }
    public void restore(UUID id, long deadline) { deadlines.merge(id, deadline, Math::max); }
    public Map<UUID, Long> snapshot(long now) {
        deadlines.values().removeIf(until -> until <= now);
        return Map.copyOf(deadlines);
    }
}
