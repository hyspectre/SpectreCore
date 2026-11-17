package dev.hydev.spectrecore.migration;

/** Pure rules shared with the regression tests. Bukkit HP, never Skript hearts. */
public final class MigrationRules {
    private MigrationRules() { }
    public static double multiplier(boolean draco, boolean bark, boolean knight, boolean mino, boolean shield) {
        return (draco ? 1.5 : 1) * (bark ? .65 : 1) * (knight ? 1.25 : 1)
            * (mino ? 1.25 : 1) * (shield ? .20 : 1);
    }
    public static boolean crosses(double health, double damage, double max, double fraction) {
        return damage > 0 && health - damage <= max * fraction;
    }
    public static double guardedHealth(double health, double max, double fraction) {
        return Math.min(health, max * fraction); // Never heal a boss already below a lock.
    }
    public static int charge(int tier) { return tier == 1 ? 1 : tier == 2 ? 2 : tier == 3 ? 4 : 0; }
    public static boolean breaksChannel(double effectiveHp) { return effectiveHp >= 200.0; }
    public static boolean expired(long deadline, long now) { return deadline > 0 && now >= deadline; }
    public static String quality(int tier, int roll) {
        if (roll < 1 || roll > 100 || tier < 1 || tier > 3) throw new IllegalArgumentException();
        if (tier == 3 && roll > 90) return "superior";
        if ((tier == 3 && roll > 50) || (tier == 2 && roll > 75)) return "intermedia";
        return "basica";
    }
    public static long position(int x, int y, int z) {
        return ((long) y << 8) | ((z & 15) << 4) | (x & 15);
    }
}
