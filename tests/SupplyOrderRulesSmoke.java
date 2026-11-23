import dev.hydev.spectrecore.systems.SupplyOrderRules;
import java.util.Arrays;
public final class SupplyOrderRulesSmoke {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Supply order allocation failed"); }
    public static void main(String[] args) {
        check(Arrays.equals(SupplyOrderRules.allocation(new int[] {2, 3, 8}, 4), new int[] {2, 2, 0}));
        check(Arrays.equals(SupplyOrderRules.allocation(new int[] {1, 2, 0}, 4), new int[0]));
        check(Arrays.equals(SupplyOrderRules.allocation(new int[] {0, 4, 0}, 4), new int[] {0, 4, 0}));
        check(SupplyOrderRules.eligible(2, 2, 4, 4));
        check(SupplyOrderRules.eligible(3, 2, 8, 4));
        check(!SupplyOrderRules.eligible(1, 2, 100, 4));
        check(!SupplyOrderRules.eligible(2, 2, 3, 4));
        check(!SupplyOrderRules.eligible(3, 2, 100, 0));
        boolean rejected = false;
        try { SupplyOrderRules.allocation(new int[] {2, -1}, 1); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected);
        System.out.println("PASS SupplyOrderRulesSmoke: exact inventory allocation, rank gates, insufficient stock");
    }
}
