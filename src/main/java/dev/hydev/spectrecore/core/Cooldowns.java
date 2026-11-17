package dev.hydev.spectrecore.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One cooldown authority for every skill, per player. Runtime-only by design. */
public final class Cooldowns {
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    public boolean ready(UUID player, String skill) { return remainingMs(player, skill) == 0; }
    public long remainingMs(UUID player, String skill) {
        return Math.max(0L, readyAt.getOrDefault(player, Map.of()).getOrDefault(skill, 0L) - System.currentTimeMillis());
    }
    public long remainingSeconds(UUID player, String skill) {
        return (remainingMs(player, skill) + 999) / 1000;
    }
    public boolean start(UUID player, String skill, long cooldownSeconds) {
        if (!ready(player, skill)) return false;
        readyAt.computeIfAbsent(player, ignored -> new HashMap<>())
                .put(skill, System.currentTimeMillis() + Math.max(0L, cooldownSeconds) * 1000L);
        return true;
    }
    public void reset(UUID player, String skill) {
        Map<String, Long> skills = readyAt.get(player);
        if (skills != null) skills.remove(skill);
    }
    public void resetAll(UUID player) { readyAt.remove(player); }
    public void clearAll() { readyAt.clear(); }
}
