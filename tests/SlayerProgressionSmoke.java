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
        System.out.println("PASS SlayerProgressionSmoke: " + checks + " checks");
    }
}
