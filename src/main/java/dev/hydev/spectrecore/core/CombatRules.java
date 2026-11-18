package dev.hydev.spectrecore.core;

/** Pure balance calculations: can be tested without Paper or MMOItems. */
public final class CombatRules {
    private CombatRules() { }
    public static int addFury(int current, int delta) {
        return Math.min(100, Math.max(0, current + Math.max(0, delta)));
    }
    public static double comboMultiplier(int stacks, int cap, double bonusPerStack) {
        return 1.0 + Math.max(0, Math.min(cap, stacks)) * Math.max(0, bonusPerStack);
    }
    /** When AuraSkills already supplies Strength, the legacy base bonus must not be applied again. */
    public static double oathMultiplier(boolean auraStrengthApplied, double baseBonus, double blinkBonus) {
        return 1.0 + (auraStrengthApplied ? 0.0 : baseBonus) + blinkBonus;
    }
    public static long extend(long currentEnd, long maximumEnd, long bonusTicks) {
        return Math.min(maximumEnd, currentEnd + Math.max(0, bonusTicks));
    }
    public static long blessingEnd(long baseEnd, long extendedEnd, boolean hasFifthPiece) {
        return hasFifthPiece ? extendedEnd : baseEnd;
    }
}
