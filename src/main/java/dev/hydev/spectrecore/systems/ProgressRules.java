package dev.hydev.spectrecore.systems;

public final class ProgressRules {
    private ProgressRules() { }
    public static class SlayerProgress {
        public int tier, kills, required;
        public boolean active;
        public void recordKill() { if (active) kills = Math.min(required, kills + 1); }
        public boolean ready() { return active && required > 0 && kills >= required; }
        public void confirmSpawn() {
            if (!ready()) throw new IllegalStateException("Objective is not ready");
            active = false; kills = 0;
        }
    }
    public static long remaining(long lastPaid, long now, long cooldownMillis) {
        return lastPaid < 0 ? 0 : Math.max(0, Math.max(0, cooldownMillis) - Math.max(0, now - lastPaid));
    }
    public static int required(int tier) {
        return switch (tier) { case 1 -> 20; case 2 -> 50; case 3 -> 90; default -> throw new IllegalArgumentException("Invalid tier"); };
    }
    public static boolean inside(double dx, double dy, double dz) {
        return Math.abs(dx) <= 1.75 && Math.abs(dz) <= 1.75 && dy >= -2 && dy <= 4.5;
    }
    public static String rank(int tier) { return tier == 3 ? "profesional" : tier == 2 ? "experimentado" : "novato"; }
    public static int miningTier(String material) {
        return switch (material) {
            case "COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "IRON_ORE", "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE" -> 1;
            case "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE" -> 2;
            case "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE", "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE" -> 3;
            default -> 0;
        };
    }
}
