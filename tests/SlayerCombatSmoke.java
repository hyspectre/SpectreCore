import dev.hydev.spectrecore.systems.SlayerCombatRules;
import static dev.hydev.spectrecore.systems.SlayerCombatRules.*;

public final class SlayerCombatSmoke {
    private static int checks;
    static void check(boolean condition,String label) { checks++; if (!condition) throw new AssertionError(label); }
    public static void main(String[] args) {
        check(accumulate(0, 25, 100) == 25, "valid hit");
        check(accumulate(0, 200, 100) == 100, "no overkill inflation");
        check(accumulate(20, -10, 100) == 20, "negative no credit");
        check(accumulate(20, Double.NaN, 100) == 20, "nan no credit");
        check(enoughParticipation(160,1600,10,true), "exact 10 percent qualified");
        check(!enoughParticipation(159,1600,10,true), "less than 10 percent rejected");
        check(!enoughParticipation(160,1600,10,false), "must be online and nearby");
        check(!enoughParticipation(0,1600,0,true), "must actually participate");
        check(!enoughParticipation(250,Double.NaN,10,true), "invalid hp rejected");
        check(!enoughParticipation(250,1600,101,true), "invalid threshold rejected");
        check(!restoresPendingOnRemoval("UNLOAD"), "unload keeps boss registration");
        check(!restoresPendingOnRemoval("DEATH"), "death handled separately");
        check(restoresPendingOnRemoval("DESPAWN"), "confirmed despawn enables retry");
        check(restoresPendingOnRemoval("PLUGIN"), "plugin remove enables retry");
        check(trackedAndActive(true,"uuid-1","uuid-1"), "active known boss");
        check(!trackedAndActive(true,"uuid-1","uuid-2"), "reject foreign boss");
        check(!trackedAndActive(false,"uuid-1","uuid-1"), "reject inactive");
        System.out.println("PASS SlayerCombatSmoke: " + checks + " checks");
    }
}
