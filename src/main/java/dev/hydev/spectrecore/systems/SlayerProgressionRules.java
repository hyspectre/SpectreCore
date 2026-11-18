package dev.hydev.spectrecore.systems;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pure, deterministic XP curve and allowlisted Slayer unlocks. */
public final class SlayerProgressionRules {
    public static final int MAX_LEVEL = 11;
    private static final long[] DEFAULT_XP = {0, 100, 250, 450, 700, 1000, 1400, 1850, 2350, 3000, 3800, 5000};
    private SlayerProgressionRules() { }

    /** Cumulative total XP required for each level; 0 is the starting level. */
    public static long[] defaultThresholds() { return DEFAULT_XP.clone(); }

    public static boolean validThresholds(long[] thresholds) {
        if (thresholds == null || thresholds.length != MAX_LEVEL + 1 || thresholds[0] != 0) return false;
        for (int i = 1; i < thresholds.length; i++)
            if (thresholds[i] <= thresholds[i - 1] || thresholds[i] > 1_000_000_000L) return false;
        return true;
    }

    public static int level(long totalXp, long[] thresholds) {
        if (!validThresholds(thresholds)) throw new IllegalArgumentException("Invalid Slayer XP curve");
        int result = 0;
        for (int i = 1; i < thresholds.length && totalXp >= thresholds[i]; i++) result = i;
        return result;
    }

    public static long xpForNext(int level, long[] thresholds) {
        if (!validThresholds(thresholds)) throw new IllegalArgumentException("Invalid Slayer XP curve");
        return level >= MAX_LEVEL ? -1 : thresholds[Math.max(0, level) + 1];
    }

    /** All nodes are fixed in source: XP/YAML may never grant arbitrary permissions. */
    public static Set<String> earnedNodes(int level) {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.add("slayer.revenant.tier1");
        if (level >= 1) nodes.add("hydev.slayer.item.sword.filopodrido");
        if (level >= 2) { nodes.add("hydev.slayer.item.armor.zombiearmor_1"); nodes.add("hydev.slayer.item.armor.zombiearmor_2"); }
        if (level >= 3) { nodes.add("hydev.slayer.item.armor.zombiearmor_3"); nodes.add("hydev.slayer.item.armor.zombiearmor_4"); nodes.add("slayer.necromancer.tier1"); }
        if (level >= 5) { nodes.add("hydev.slayer.item.sword.colmillo_putrefacto"); nodes.add("slayer.revenant.tier2"); }
        if (level >= 6) { nodes.add("hydev.slayer.item.armor.renacido_1"); nodes.add("hydev.slayer.item.armor.renacido_2"); }
        if (level >= 7) { nodes.add("hydev.slayer.item.armor.renacido_3"); nodes.add("hydev.slayer.item.armor.renacido_4"); }
        if (level >= 8) nodes.add("hydev.slayer.item.sword.sable_necrotico");
        if (level >= 9) { nodes.add("hydev.slayer.item.armor.necrotico_1"); nodes.add("hydev.slayer.item.armor.necrotico_2"); }
        if (level >= 10) { nodes.add("hydev.slayer.item.armor.necrotico_3"); nodes.add("hydev.slayer.item.armor.necrotico_4"); nodes.add("slayer.revenant.tier3"); }
        if (level >= 11) nodes.add("hydev.slayer.item.sword.necroespada");
        return Set.copyOf(nodes);
    }

    public static boolean accessible(String category, int tier, int revenantLevel, boolean legacyPermission) {
        if (tier < 1 || tier > 3) return false;
        return switch (category) {
            case "revenant" -> tier == 1 || (tier == 2 && revenantLevel >= 5) || (tier == 3 && revenantLevel >= 10);
            case "necromancer" -> tier == 1 ? revenantLevel >= 3 : legacyPermission;
            default -> legacyPermission;
        };
    }
}
