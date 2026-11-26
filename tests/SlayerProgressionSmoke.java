import dev.hydev.spectrecore.systems.SlayerProgressionRules;

public final class SlayerProgressionSmoke {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        long[] xp = SlayerProgressionRules.defaultThresholds();
        check(SlayerProgressionRules.validThresholds(xp), "curve valid");
        check(SlayerProgressionRules.level(0, xp) == 0, "level zero");
        check(SlayerProgressionRules.level(99, xp) == 0, "not yet first level");
        check(SlayerProgressionRules.level(100, xp) == 1, "level one at 100");
        check(SlayerProgressionRules.level(1000, xp) == 5, "level five");
        check(SlayerProgressionRules.level(3800, xp) == 10, "level ten");
        check(SlayerProgressionRules.level(5000, xp) == 11, "max level");
        check(SlayerProgressionRules.progressSegments(0, xp, 10) == 0, "empty progress");
        check(SlayerProgressionRules.progressSegments(50, xp, 10) == 5, "half progress");
        check(SlayerProgressionRules.progressSegments(100, xp, 10) == 0, "reset at level boundary");
        check(SlayerProgressionRules.progressSegments(175, xp, 10) == 5, "level 1 bar uses local span");
        check(SlayerProgressionRules.progressSegments(5000, xp, 10) == 10, "max progress filled");
        check(SlayerProgressionRules.remainingXp(99, xp) == 1, "one xp needed");
        check(SlayerProgressionRules.remainingXp(175, xp) == 75, "next level distance");
        check(SlayerProgressionRules.remainingXp(5000, xp) == 0, "max no remaining xp");
        check(SlayerProgressionRules.xpForNext(11, xp) == -1, "max has no next");
        check(SlayerProgressionRules.earnedNodes(0).contains("slayer.revenant.tier1"), "start tier");
        check(!SlayerProgressionRules.earnedNodes(4).contains("slayer.revenant.tier2"), "tier 2 locked");
        check(SlayerProgressionRules.earnedNodes(5).contains("slayer.revenant.tier2"), "tier 2 earned");
        check(SlayerProgressionRules.earnedNodes(3).contains("slayer.necromancer.tier1"), "necromancer earned");
        check(SlayerProgressionRules.earnedNodes(10).contains("slayer.revenant.tier3"), "tier 3 earned");
        check(SlayerProgressionRules.earnedNodes(11).contains("hydev.slayer.item.sword.necroespada"), "last sword");
        check(SlayerProgressionRules.accessible("revenant",1,0,false), "tier 1 free to start");
        check(!SlayerProgressionRules.accessible("revenant",3,9,true), "tier 3 level gated");
        check(SlayerProgressionRules.accessible("necromancer",1,3,false), "necromancer unlocked");
        check(SlayerProgressionRules.minimumRevenantLevel("revenant", 2) == 5, "rank 2 hint matches gate");
        check(SlayerProgressionRules.minimumRevenantLevel("revenant", 3) == 10, "rank 3 hint matches gate");
        check(SlayerProgressionRules.minimumRevenantLevel("necromancer", 1) == 3, "second category hint matches gate");
        check(SlayerProgressionRules.minimumRevenantLevel("spider", 1) == -1, "no fabricated level for external unlock");
        for (String category : java.util.List.of("revenant", "necromancer", "spider")) {
            for (int tier = 0; tier <= 4; tier++) {
                for (int level = 0; level <= 11; level++) {
                    for (boolean permission : new boolean[]{false, true}) {
                        boolean original = tier >= 1 && tier <= 3 && switch (category) {
                            case "revenant" -> tier == 1 || tier == 2 && level >= 5 || tier == 3 && level >= 10;
                            case "necromancer" -> tier == 1 ? level >= 3 : permission;
                            default -> permission;
                        };
                        check(SlayerProgressionRules.accessible(category, tier, level, permission) == original,
                                "Access rule unchanged: " + category + "/" + tier + "/" + level + "/" + permission);
                    }
                }
            }
        }
        System.out.println("PASS SlayerProgressionSmoke: " + checks + " checks");
    }
}
