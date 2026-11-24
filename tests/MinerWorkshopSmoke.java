import dev.hydev.spectrecore.systems.MinerWorkshopRules;

public final class MinerWorkshopSmoke {
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void invalid(Runnable task) {
        try { task.run(); throw new AssertionError("Expected invalid work order"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        ok(MinerWorkshopRules.batches(63, 64, 8)==0,"63 not enough");
        ok(MinerWorkshopRules.batches(64, 64, 8)==1,"one compact batch");
        ok(MinerWorkshopRules.batches(255, 64, 8)==3,"floor division");
        ok(MinerWorkshopRules.batches(576, 64, 8)==8,"batch cap");
        ok(MinerWorkshopRules.selected(192, 64, 8, 4)==3,"requested capped by supplies");
        ok(MinerWorkshopRules.selected(576, 64, 8, -1)==8,"all safe");
        ok(MinerWorkshopRules.selected(100, 1, 8, -1)==8,"pulverize capped");
        ok(MinerWorkshopRules.selected(100, 1, 8, 1)==1,"single pulverize");
        ok(MinerWorkshopRules.payout(4,135)==540,"fixed gem payout");
        ok(MinerWorkshopRules.ready(3,3,1,8),"level 3 unlocked");
        ok(!MinerWorkshopRules.ready(2,3,1,8),"level gate");
        ok(!MinerWorkshopRules.ready(25,3,0,8),"no free claim");
        ok(!MinerWorkshopRules.ready(25,3,9,8),"no oversized claim");
        invalid(()->MinerWorkshopRules.batches(-1,64,8));
        invalid(()->MinerWorkshopRules.batches(64,0,8));
        invalid(()->MinerWorkshopRules.payout(65,135));
        invalid(()->MinerWorkshopRules.payout(1,-1));
        System.out.println("PASS MinerWorkshopSmoke: 64:1, 1/4/all, level gate and fixed payments");
    }
}
