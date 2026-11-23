import dev.hydev.spectrecore.mining.MithrilRules;
import java.util.List;

public final class MithrilRulesSmoke {
    private static void ok(boolean valid) { if (!valid) throw new AssertionError("Mithril tool rules"); }
    public static void main(String[] args) {
        ok(MithrilRules.charge(99, 4, 100) == 100);
        ok(MithrilRules.charge(5, -25, 100) == 0);
        ok(MithrilRules.charge(50, 50, 100) == 100);
        ok(MithrilRules.canUse(50, 30, 8000, 4000));
        ok(!MithrilRules.canUse(20, 30, 8000, 4000));
        ok(!MithrilRules.canUse(50, 30, 8000, 9000));
        ok("intensa".equals(MithrilRules.signal(5.0)));
        ok("moderada".equals(MithrilRules.signal(8.0)));
        ok("debil".equals(MithrilRules.signal(15.0)));
        ok("ninguna".equals(MithrilRules.signal(Double.NaN)));
        ok("N".equals(MithrilRules.direction(0, -3)));
        ok("E".equals(MithrilRules.direction(4, 0)));
        ok("SO".equals(MithrilRules.direction(-5, 5)));
        ok("abajo".equals(MithrilRules.depth(-5)));
        ok("similar".equals(MithrilRules.depth(4)));
        ok("arriba".equals(MithrilRules.depth(8)));
        ok(MithrilRules.within(16, 12, 0, 16, 12));
        ok(!MithrilRules.within(16, 0, 5, 16, 12));
        ok(MithrilRules.sameOre("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"));
        ok(!MithrilRules.sameOre("IRON_ORE", "DIAMOND_ORE"));
        ok(!MithrilRules.sameOre("STONE", "STONE"));
        ok(MithrilRules.drillable("STONE", List.of("STONE","DEEPSLATE")));
        ok(!MithrilRules.drillable("DIAMOND_ORE", List.of("STONE","DEEPSLATE")));
        System.out.println("PASS MithrilRulesSmoke: portable charge, scan guidance and protected drill rock");
    }
}
