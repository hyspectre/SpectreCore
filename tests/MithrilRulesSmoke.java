import dev.hydev.spectrecore.mining.MithrilRules;
import java.util.List;
import static dev.hydev.spectrecore.mining.MithrilRules.Readiness.*;

public final class MithrilRulesSmoke {
    private static void ok(boolean valid) { if (!valid) throw new AssertionError("Mithril tool rules"); }
    public static void main(String[] args) {
        ok(MithrilRules.charge(99, 4, 100) == 100);
        ok(MithrilRules.charge(5, -25, 100) == 0);
        ok(MithrilRules.charge(50, 50, 100) == 100);
        ok(MithrilRules.canUse(50, 30, 8000, 4000));
        ok(!MithrilRules.canUse(20, 30, 8000, 4000));
        ok(!MithrilRules.canUse(50, 30, 8000, 9000));
        // HUD shares the activation threshold; zero charge and cooldown boundaries are explicit.
        var empty = MithrilRules.abilityStatus(0, 100, 25, 25, 8000, 0);
        ok(empty.readiness() == CHARGING && empty.missing() == 25 && empty.progress() == 0f);
        ok(MithrilRules.abilityStatus(24, 100, 25, 25, 8000, 0).missing() == 1);
        var ready = MithrilRules.abilityStatus(25, 100, 25, 25, 8000, 8000);
        ok(ready.readiness() == READY && ready.progress() == .25f);
        var cooling = MithrilRules.abilityStatus(100, 100, 25, 25, 8000, 8001);
        ok(cooling.readiness() == COOLDOWN && cooling.seconds() == 1);
        ok(MithrilRules.remainingSeconds(8000, 10001) == 3);
        int partialCost = dev.hydev.spectrecore.mining.AmethystResonanceRules.activationCost(75, 3, 5);
        ok(partialCost == 45);
        ok(MithrilRules.abilityStatus(44, 100, partialCost, 75, 0, 0).readiness() == CHARGING);
        ok(MithrilRules.abilityStatus(45, 100, partialCost, 75, 0, 0).readiness() == PARTIAL);
        ok(MithrilRules.abilityStatus(74, 100, partialCost, 75, 0, 0).missing() == 1);
        ok(MithrilRules.abilityStatus(75, 100, partialCost, 75, 0, 0).readiness() == READY);
        ok(MithrilRules.abilityStatus(120, 100, 25, 25, 0, 0).progress() == 1f);
        ok(MithrilRules.withinPulse(10, 0, 0, 10));
        ok(MithrilRules.withinPulse(0, -10, 0, 10));
        ok(MithrilRules.withinPulse(6, 8, 0, 10));
        ok(!MithrilRules.withinPulse(6, 8, 1, 10));
        ok(!MithrilRules.withinPulse(10, 10, 0, 10));
        ok(MithrilRules.remainingSeconds(0, 140_000) == 140);
        ok(MithrilRules.remainingSeconds(139_999, 140_000) == 1);
        ok(MithrilRules.remainingSeconds(140_000, 140_000) == 0);
        ok(MithrilRules.sameOre("DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"));
        ok(!MithrilRules.sameOre("IRON_ORE", "DIAMOND_ORE"));
        ok(!MithrilRules.sameOre("STONE", "STONE"));
        ok(MithrilRules.drillable("STONE", List.of("STONE","DEEPSLATE")));
        ok(!MithrilRules.drillable("DIAMOND_ORE", List.of("STONE","DEEPSLATE")));
        System.out.println("PASS MithrilRulesSmoke: portable charge, spherical pulse and cooldown and protected drill rock");
    }
}
