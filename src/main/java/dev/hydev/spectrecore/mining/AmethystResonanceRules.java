package dev.hydev.spectrecore.mining;

/** Pure limits shared by the Amethyst pickaxe field and its tests. */
public final class AmethystResonanceRules {
    private AmethystResonanceRules() { }

    public static boolean available(int energy, long cooldownUntil, long now, int cost,
                                    int activeFields, int maximumFields) {
        return cost > 0 && energy >= cost && now >= cooldownUntil
                && activeFields >= 0 && activeFields < maximumFields;
    }

    /** Roll in [0,100), weights must sum to exactly 100. */
    public static int quality(double roll, int basic, int intermediate, int superior) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 100
                || basic < 0 || intermediate < 0 || superior < 0
                || basic + intermediate + superior != 100)
            throw new IllegalArgumentException("Invalid resonance quality weights or roll");
        return roll < basic ? 0 : roll < basic + intermediate ? 1 : 2;
    }

    /** A short cave must never cost as much as a full five-node field. */
    public static int activationCost(int fullCost, int generated, int maximum) {
        if (fullCost < 1 || fullCost > 1000 || maximum < 1 || maximum > 8
                || generated < 1 || generated > maximum)
            throw new IllegalArgumentException("Invalid crystal field size or price");
        return (fullCost * generated + maximum - 1) / maximum;
    }

    public static boolean hit(long now, long previous, int intervalMillis, int remaining) {
        return now >= previous && now - previous >= intervalMillis && remaining > 0;
    }

    public static boolean slot(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius && dx * dx + dz * dz >= 4;
    }
}
