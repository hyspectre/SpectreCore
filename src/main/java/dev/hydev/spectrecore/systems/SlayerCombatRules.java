package dev.hydev.spectrecore.systems;

/** Pure encounter rules. No Bukkit dependency, allowing deterministic regression tests. */
public final class SlayerCombatRules {
    private SlayerCombatRules() { }

    public static double validDamage(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    /** Only a direct hit or a projectile attributed to the contract owner counts. */
    public static double accumulate(double current, double finalDamage, double remainingHealth) {
        double capped = Math.min(validDamage(finalDamage), validDamage(remainingHealth));
        return Math.min(1_000_000_000d, validDamage(current) + capped);
    }

    public static boolean enoughParticipation(double dealt, double maxHealth, double requiredPercent,
                                               boolean ownerOnlineAndNearby) {
        if (!ownerOnlineAndNearby || !Double.isFinite(maxHealth) || maxHealth <= 0
                || !Double.isFinite(requiredPercent) || requiredPercent < 0 || requiredPercent > 100) return false;
        return validDamage(dealt) > 0 && validDamage(dealt) + 1.0e-6 >= maxHealth * requiredPercent / 100d;
    }

    /** Unload must never count as despawn. Confirmed non-death removal permits a free retry. */
    public static boolean restoresPendingOnRemoval(String cause) {
        return cause != null && !cause.equals("UNLOAD") && !cause.equals("DEATH");
    }

    public static boolean trackedAndActive(boolean active, String expectedBossId, String actualBossId) {
        return active && expectedBossId != null && expectedBossId.equals(actualBossId);
    }
}
