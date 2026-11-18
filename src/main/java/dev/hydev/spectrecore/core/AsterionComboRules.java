package dev.hydev.spectrecore.core;

import java.util.UUID;

/**
 * Pure deterministic Asterion combo logic. One selected prey; no global Strength buff.
 * The first landed hit establishes the prey, the following valid hits gain one stack.
 */
public final class AsterionComboRules {
    private AsterionComboRules() { }

    public record Combo(UUID prey, int stacks, long lastHit) {
        public static Combo empty() { return new Combo(null, 0, Long.MIN_VALUE); }
    }

    public static Combo landed(Combo previous, UUID target, long tick, int gapTicks, int cap) {
        if (target == null) return previous;
        boolean same = previous.prey() != null && previous.prey().equals(target);
        boolean quick = tick >= previous.lastHit() && previous.lastHit() >= 0
                && tick - previous.lastHit() <= Math.max(0, gapTicks);
        int stacks = same && quick ? Math.min(Math.max(0, cap), previous.stacks() + 1) : 0;
        return new Combo(target, stacks, tick);
    }

    /** Multiplier on the next swing, before the onMelee callback records it. */
    public static double multiplier(Combo previous, UUID target, long tick,
                                    int gapTicks, int cap, double perStack) {
        if (target == null || previous.prey() == null || !previous.prey().equals(target)
                || tick < previous.lastHit() || previous.lastHit() < 0
                || tick - previous.lastHit() > Math.max(0, gapTicks)) return 1.0;
        return CombatRules.comboMultiplier(previous.stacks() + 1, cap, perStack);
    }

    public static Combo expire(Combo previous, long tick, int gapTicks) {
        if (previous.prey() == null) return previous;
        return tick - previous.lastHit() > Math.max(0, gapTicks) ? Combo.empty() : previous;
    }
}
