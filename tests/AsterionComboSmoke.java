import dev.hydev.spectrecore.core.AsterionComboRules;
import java.util.UUID;

/** Deterministic Asterion regression for prey changes and expiration. */
public class AsterionComboSmoke {
    private static int count;
    private static void check(boolean ok, String msg) {
        count++;
        if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var state = AsterionComboRules.Combo.empty();
        check(AsterionComboRules.multiplier(state, a, 100, 50, 4, .03) == 1, "No initial prey bonus");
        state = AsterionComboRules.landed(state, a, 100, 50, 4);
        check(state.stacks() == 0, "First landed hit locks prey without bonus");
        for (int hit = 1; hit <= 6; hit++) {
            double expected = 1 + Math.min(4, hit) * .03;
            check(Math.abs(AsterionComboRules.multiplier(state, a, 100 + hit * 5, 50, 4, .03) - expected) < 1e-12, "Next hit "+hit+" with same prey");
            state = AsterionComboRules.landed(state, a, 100 + hit * 5, 50, 4);
        }
        check(state.stacks() == 4, "Cap four");
        check(AsterionComboRules.multiplier(state, b, 136, 50, 4, .03) == 1, "No target leak");
        state = AsterionComboRules.landed(state, b, 136, 50, 4);
        check(state.stacks() == 0 && state.prey().equals(b), "Switch target resets");
        check(AsterionComboRules.multiplier(state, b, 187, 50, 4, .03) == 1, "Gap above 50 resets");
        check(AsterionComboRules.expire(state, 187, 50).prey() == null, "Expired prey clears");
        state = AsterionComboRules.landed(state, b, 187, 50, 4);
        check(state.stacks() == 0, "New prey after expiration starts at zero");
        state = AsterionComboRules.landed(state, b, 237, 50, 4);
        check(state.stacks() == 1, "Exactly 50 ticks keeps combo");
        check(AsterionComboRules.multiplier(state, b, 237, 50, 4, .03) > 1, "Immediate subsequent hit keeps same target");
        check(AsterionComboRules.multiplier(state, b, 236, 50, 4, .03) == 1, "Backwards ticks cannot trigger bonus");
        check(AsterionComboRules.earnedBonusPercent(0, 4, .03) == 0, "No claimed bonus at first hit");
        check(AsterionComboRules.earnedBonusPercent(2, 4, .03) == 6, "Two charges: six percent");
        check(AsterionComboRules.earnedBonusPercent(8, 4, .03) == 12, "HUD obeys configured cap");
        check(AsterionComboRules.earnedBonusPercent(1, 4, .025) == 2.5, "Non-integer bonus shown exactly");
        System.out.println("PASS AsterionComboSmoke: " + count + " assertions");
    }
}
