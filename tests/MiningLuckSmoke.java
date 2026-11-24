import dev.hydev.spectrecore.mining.MiningLuckRules;

public final class MiningLuckSmoke {
    private static void ok(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
    private static void invalid(Runnable callback) {
        try { callback.run(); throw new AssertionError("Must reject invalid Luck"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        ok(MiningLuckRules.chance(2.2,0,1.5,2)==2.2,"zero Luck preserves base");
        ok(Math.abs(MiningLuckRules.chance(2.2,12,1.5,2)-2.596)<0.00001,"12 Luck = +18% relative");
        ok(MiningLuckRules.chance(0,10000,1.5,2)==0,"zero base never produces free spawns");
        ok(MiningLuckRules.chance(0.5,10000,1.5,2)==1.0,"encounter caps at x2");
        ok(MiningLuckRules.chance(100,10000,1.5,2)==100,"hard 100% cap");
        ok(MiningLuckRules.chance(3,Double.NaN,1.5,2)==3,"unavailable stats never buff");
        ok(MiningLuckRules.chance(3,-200,1.5,2)==3,"negative Luck never penalizes");
        ok(MiningLuckRules.quantity(2,64,0,1,35,0)==2,"no Luck no bonus");
        ok(MiningLuckRules.quantity(2,64,12,1,35,11.9)==3,"Paladio lucky +1");
        ok(MiningLuckRules.quantity(2,64,12,1,35,12)==2,"Paladio unlucky");
        ok(MiningLuckRules.quantity(2,64,1000,1,35,34.9)==3,"Paladio bonus cap");
        ok(MiningLuckRules.quantity(2,64,1000,1,35,35)==2,"no >35% extra chance");
        ok(MiningLuckRules.quantity(1,64,12,.8,25,9.5)==2,"Mitrilo extra unit");
        ok(MiningLuckRules.quantity(1,64,12,.8,25,9.61)==1,"Mitrilo no extra");
        ok(MiningLuckRules.quantity(0,64,1000,1,35,0)==0,"zero original gives zero");
        ok(Math.abs(MiningLuckRules.qualityRoll(90,12,.05,5)-90.6)<.00001,
                "Luck slightly raises gem rarity");
        ok(MiningLuckRules.qualityRoll(99.99,10000,.05,5)<100,
                "quality roll never exceeds 100");
        ok(MiningLuckRules.qualityRoll(40,10000,.05,5)==45,
                "gem rarity cap");
        invalid(()->MiningLuckRules.chance(120,0,1,2));
        invalid(()->MiningLuckRules.chance(2,0,-1,2));
        invalid(()->MiningLuckRules.quantity(1,64,4,1,35,100));
        System.out.println("PASS MiningLuckSmoke: chances, caps, natural reward limits and missing stat");
    }
}
