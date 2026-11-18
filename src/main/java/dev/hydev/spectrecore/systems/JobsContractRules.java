package dev.hydev.spectrecore.systems;

import java.util.List;

/** Side-effect free prerequisites for the native Jobs contract menus. */
public final class JobsContractRules {
    private JobsContractRules() { }
    public static final List<String> JOBS = List.of("minero", "granjero", "cazador");
    public static final List<String> LEVELS = List.of("novato", "experimentado", "profesional");
    public enum Refusal {
        NONE, DISABLED, INVALID_JOB, INVALID_RANK, ALREADY_OWNED, PREVIOUS_RANK_REQUIRED,
        INVALID_PRICE, IN_PROGRESS
    }
    public static int tier(String level) { return LEVELS.indexOf(level) + 1; }
    public static Refusal validate(String job, String level, boolean enabled, int currentRank,
                                   long price, boolean pending) {
        if (!JOBS.contains(job)) return Refusal.INVALID_JOB;
        int requested = tier(level);
        if (requested < 1) return Refusal.INVALID_RANK;
        if (!enabled) return Refusal.DISABLED;
        if (pending) return Refusal.IN_PROGRESS;
        if (currentRank >= requested) return Refusal.ALREADY_OWNED;
        if (currentRank != requested - 1) return Refusal.PREVIOUS_RANK_REQUIRED;
        if (price < 0 || price > 1_000_000_000L) return Refusal.INVALID_PRICE;
        return Refusal.NONE;
    }
}
