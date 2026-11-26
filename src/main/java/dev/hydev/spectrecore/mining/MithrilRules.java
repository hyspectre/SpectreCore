package dev.hydev.spectrecore.mining;

import java.util.List;

/** Bounded, dependency-free rules for reusable Mithril tools. */
public final class MithrilRules {
    private MithrilRules() { }

    public static int charge(int stored, int delta, int maximum) {
        if (maximum < 1 || maximum > 1000) throw new IllegalArgumentException("Invalid capacity");
        return Math.max(0, Math.min(maximum, (int) Math.max(Integer.MIN_VALUE,
                Math.min(Integer.MAX_VALUE, (long) stored + delta))));
    }

    public static boolean canUse(int stored, int cost, long now, long cooldownUntil) {
        return cost > 0 && stored >= cost && now >= cooldownUntil;
    }

    public enum Readiness { COOLDOWN, CHARGING, PARTIAL, READY }
    public record AbilityStatus(Readiness readiness, int missing, long seconds, float progress) { }

    /** Read-only presentation of the actual charge and cooldown; no target/world checks. */
    public static AbilityStatus abilityStatus(int stored, int capacity, int minimumCost, int fullCost,
                                              long now, long cooldownUntil) {
        int energy = Math.max(0, stored);
        float progress = Math.clamp(energy / (float)Math.max(1, capacity), 0f, 1f);
        long remaining = remainingSeconds(now, cooldownUntil);
        if (remaining > 0) return new AbilityStatus(Readiness.COOLDOWN, 0, remaining, progress);
        if (energy < minimumCost)
            return new AbilityStatus(Readiness.CHARGING, minimumCost - energy, 0, progress);
        if (energy < fullCost)
            return new AbilityStatus(Readiness.PARTIAL, fullCost - energy, 0, progress);
        return new AbilityStatus(Readiness.READY, 0, 0, progress);
    }

    public static long remainingSeconds(long now, long deadline) {
        if (deadline <= now) return 0;
        long millis = deadline - now;
        return millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
    }

    public static String signal(double distance) {
        if (!Double.isFinite(distance) || distance < 0) return "ninguna";
        return distance <= 6 ? "intensa" : distance <= 12 ? "moderada" : "debil";
    }

    public static String direction(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) return "desconocida";
        double angle = Math.atan2(dx, -dz);
        String[] sectors = {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
        int index = Math.floorMod((int) Math.round(angle / (Math.PI / 4)), 8);
        return sectors[index];
    }

    public static String depth(int delta) {
        return delta > 4 ? "arriba" : delta < -4 ? "abajo" : "similar";
    }

    public static boolean within(int dx, int dy, int dz, int radius, int vertical) {
        return Math.abs(dy) <= vertical && (long) dx * dx + (long) dz * dz <= (long) radius * radius;
    }

    public static boolean sameOre(String a, String b) {
        return oreName(a).equals(oreName(b)) && !"".equals(oreName(a));
    }

    private static String oreName(String value) {
        if (value == null) return "";
        String normalized = value.startsWith("DEEPSLATE_") ? value.substring(10) : value;
        return normalized.endsWith("_ORE") ? normalized : "";
    }

    public static boolean drillable(String material, List<String> allowlist) {
        return material != null && allowlist != null && allowlist.contains(material);
    }
}
