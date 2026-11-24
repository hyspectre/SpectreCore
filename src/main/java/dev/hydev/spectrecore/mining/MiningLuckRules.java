package dev.hydev.spectrecore.mining;

/**
 * AuraSkills Luck influences Hydev rewards, not AuraSkills XP or vanilla drops.
 * Pure bounded rules keep high Luck and malicious YAML from inflating the economy.
 */
public final class MiningLuckRules {
    private MiningLuckRules() { }

    private static double bounded(double input, double upper) {
        if (!Double.isFinite(input) || input <= 0) return 0;
        return Math.min(input, upper);
    }

    /** Returns a percentage in [0, 100] with bounded relative Luck amplification. */
    public static double chance(double basePercent, double luck,
                                double bonusPercentPerLuck, double maxMultiplier) {
        if (!Double.isFinite(basePercent) || basePercent < 0 || basePercent > 100
                || !Double.isFinite(bonusPercentPerLuck) || bonusPercentPerLuck < 0 || bonusPercentPerLuck > 10
                || !Double.isFinite(maxMultiplier) || maxMultiplier < 1 || maxMultiplier > 5)
            throw new IllegalArgumentException("Invalid mining Luck settings");
        double points = bounded(luck, 10000);
        double multiplier = Math.min(maxMultiplier, 1.0 + points * bonusPercentPerLuck / 100.0);
        return Math.min(100.0, basePercent * multiplier);
    }

    /** Only one additional unit may be awarded; quantity never doubles recursively. */
    public static int quantity(int base, int maxBase, double luck,
                               double bonusPercentPerLuck, double bonusCapPercent,
                               double roll0to100) {
        if (base < 0 || base > maxBase || maxBase > 64
                || !Double.isFinite(bonusPercentPerLuck) || bonusPercentPerLuck < 0 || bonusPercentPerLuck > 10
                || !Double.isFinite(bonusCapPercent) || bonusCapPercent < 0 || bonusCapPercent > 100
                || !Double.isFinite(roll0to100) || roll0to100 < 0 || roll0to100 >= 100)
            throw new IllegalArgumentException("Invalid mining quantity settings");
        double threshold = Math.min(bonusCapPercent, bounded(luck, 10000) * bonusPercentPerLuck);
        return base + (base > 0 && roll0to100 < threshold ? 1 : 0);
    }
}
