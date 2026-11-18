import dev.hydev.spectrecore.migration.MigrationRules;
import dev.hydev.spectrecore.core.CombatRules;
import dev.hydev.spectrecore.treasure.CombatTreasureRules;
import dev.hydev.spectrecore.treasure.TreasureLedger;
import dev.hydev.spectrecore.activities.ActivityRules;
import dev.hydev.spectrecore.systems.ProgressRules;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MigrationRegression {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(Math.abs(MigrationRules.multiplier(true, true, true, true, true) - .3046875) < 1e-12, "All boss tags must stack");
        check(MigrationRules.multiplier(false, false, false, false, false) == 1, "Untagged entities unchanged");
        check(MigrationRules.multiplier(false, false, false, false, true) == .2, "Shield reduces by 80 percent");
        double max = 15000;
        for (double floor : new double[]{.70, .35}) {
            check(MigrationRules.crosses(max, 100000, max, floor), "Lethal hit crosses phase");
            check(MigrationRules.guardedHealth(max, max, floor) == max * floor, "Lethal hit lands exactly at floor");
            check(MigrationRules.crosses(max * floor + 1, 1, max, floor), "Exact threshold crossing");
            check(!MigrationRules.crosses(max, 1, max, floor), "Small hit not locked early");
            check(MigrationRules.guardedHealth(100, max, floor) == 100, "Stale lock never heals boss");
            check(!MigrationRules.crosses(max * floor, 0, max, floor), "Zero damage never advances phase");
        }
        check(Math.abs(CombatRules.oathMultiplier(false,.15,0) - 1.15) < 1e-12,"Legacy Voto bonus without AuraSkills");
        check(Math.abs(CombatRules.oathMultiplier(true,.15,0) - 1.0) < 1e-12,"AuraSkills Strength suppresses duplicate legacy bonus");
        check(Math.abs(CombatRules.oathMultiplier(true,.15,.10) - 1.10) < 1e-12,"Blink bonus remains when AuraSkills is active");
        check(Math.abs(CombatRules.oathMultiplier(false,.15,.10) - 1.25) < 1e-12,"Legacy bonus and Blink combine without AuraSkills");
        check(Math.abs(CombatRules.dragonMultiplier(false,false,false,.03,.06)-1.03)<1e-12,"Legacy Dragon passive");
        check(Math.abs(CombatRules.dragonMultiplier(false,false,true,.03,.06)-1.09)<1e-12,"Legacy Dragon passive+active");
        check(Math.abs(CombatRules.dragonMultiplier(true,true,true,.03,.06)-1.0)<1e-12,"AuraSkills Dragon suppresses both legacy bonuses");
        check(Math.abs(CombatRules.dragonMultiplier(true,false,true,.03,.06)-1.06)<1e-12,"Partial AuraSkills Dragon uses active fallback");
        check(!MigrationRules.breaksChannel(100), "Skript hearts must not become 100 Java HP");
        check(!MigrationRules.breaksChannel(199.99), "Below 200 HP does not break channel");
        check(MigrationRules.breaksChannel(200), "Exactly 200 HP breaks channel");
        check(MigrationRules.breaksChannel(250), "Over 200 HP breaks channel");
        Map<String, Integer> quality = new HashMap<>();
        for (int tier = 1; tier <= 3; tier++) {
            quality.clear();
            for (int roll = 1; roll <= 100; roll++) quality.merge(MigrationRules.quality(tier, roll), 1, Integer::sum);
            check(quality.getOrDefault("basica", 0) == (tier == 1 ? 100 : tier == 2 ? 75 : 50), "Basic distribution tier " + tier);
            check(quality.getOrDefault("intermedia", 0) == (tier == 1 ? 0 : tier == 2 ? 25 : 40), "Intermediate distribution tier " + tier);
            check(quality.getOrDefault("superior", 0) == (tier == 3 ? 10 : 0), "Superior distribution tier " + tier);
        }
        check(MigrationRules.charge(1) == 1 && MigrationRules.charge(2) == 2 && MigrationRules.charge(3) == 4, "Charges 1/2/4");
        check(MigrationRules.charge(0) == 0, "Non-ore never charges");
        check(!MigrationRules.expired(0, 40000), "No activation does not expire");
        check(!MigrationRules.expired(30000, 29999), "Window lasts until deadline");
        check(MigrationRules.expired(30000, 30000), "Expires exactly at deadline");
        check(MigrationRules.expired(30000, 90000), "Offline time counts toward window");
        Set<Long> positions = new HashSet<>();
        for (int y = -64; y < 320; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            check(positions.add(MigrationRules.position(x, y, z)), "Chunk ore markers must not collide");
        }
        check(MigrationRules.position(-1, -64, -1) == MigrationRules.position(15, -64, 15), "Negative chunk coordinate packing");
        for (String entity : new String[]{"ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "SLIME"}) check(CombatTreasureRules.tier(entity) == 1, "Combat tier 1: " + entity);
        for (String entity : new String[]{"WITCH", "ENDERMAN", "BLAZE", "GUARDIAN", "PHANTOM"}) check(CombatTreasureRules.tier(entity) == 2, "Combat tier 2: " + entity);
        for (String entity : new String[]{"WITHER_SKELETON", "EVOKER", "VINDICATOR", "WARDEN"}) check(CombatTreasureRules.tier(entity) == 3, "Combat tier 3: " + entity);
        for (String entity : new String[]{"PLAYER", "COW", "ARMOR_STAND", "MAGMA_CUBE", "ELDER_GUARDIAN", "HUSK"}) check(CombatTreasureRules.tier(entity) == 0, "No unrequested additional combat types: " + entity);
        TreasureLedger ledger = new TreasureLedger(); UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        check(ledger.ready(player, 10000), "Initial treasure ready");
        ledger.start(player, 10000, 5000); // Mining win uses the same ledger as combat.
        check(!ledger.ready(player, 10001), "Mining reward blocks combat immediately");
        check(ledger.ready(other, 10001), "Cooldown is per player");
        check(!ledger.ready(player, 14999), "Cannot win second type before five seconds");
        check(ledger.ready(player, 15000), "Cooldown ready exactly at five seconds");
        ledger.start(player, 15000, 5000); // Now a combat win blocks mining.
        check(!ledger.ready(player, 15001), "Combat reward blocks mining");
        TreasureLedger restored = new TreasureLedger(); ledger.snapshot(16000).forEach(restored::restore);
        check(!restored.ready(player, 19999) && restored.ready(player, 20000), "Shared cooldown survives snapshot/restart");
        restored.restore(player, 17000);
        check(!restored.ready(player, 18000), "Legacy import cannot shorten a newer deadline");
        Map<String, Integer> chest = new HashMap<>(); int escapes = 0;
        for (int roll = 1; roll <= 100; roll++) {
            chest.merge(ActivityRules.chestPrize(roll), 1, Integer::sum);
            if (ActivityRules.cygnusEscapes(roll)) escapes++;
        }
        check(escapes == 30, "Cygnus keeps exact 30/70 outcomes");
        check(chest.equals(Map.of("COAL_ORE",35,"IRON_ORE",25,"COPPER_ORE",20,"GOLD_ORE",10,"REDSTONE_ORE",5,"LAPIS_ORE",3,"DIAMOND_ORE",1,"EMERALD_ORE",1)), "Exact eight chest probabilities");
        for (String crop : new String[]{"WHEAT","CARROTS","POTATOES","BEETROOTS"}) check(ActivityRules.cropTier(crop) == 1, "Crop tier 1: " + crop);
        for (String crop : new String[]{"PUMPKIN","MELON","SUGAR_CANE","COCOA"}) check(ActivityRules.cropTier(crop) == 2, "Crop tier 2: " + crop);
        for (String crop : new String[]{"NETHER_WART","CAVE_VINES","CAVE_VINES_PLANT","SWEET_BERRY_BUSH"}) check(ActivityRules.cropTier(crop) == 3, "Crop tier 3: " + crop);
        check(ActivityRules.cropTier("STONE") == 0 && ActivityRules.cropTier("GLOW_BERRIES") == 0, "Agriculture uses actual block types, not berry items");
        ledger.start(player, 30000, 5000); // Agriculture uses this same service too.
        check(!ledger.ready(player, 34999) && ledger.ready(player, 35000), "Agricultural treasure shares five-second deadline");
        check(ProgressRules.remaining(-1,10000,5000)==0,"First job reward ready");
        check(ProgressRules.remaining(10000,12000,5000)==3000,"Job cooldown based on last payment");
        check(ProgressRules.remaining(10000,12000,1000)==0,"Current resource tier selects cooldown, not highest unlocked rank");
        check(ProgressRules.remaining(10000,15000,5000)==0,"Job cooldown exact deadline");
        check(ProgressRules.remaining(10000,9000,5000)==5000,"Backward wall clock never bypasses cooldown");
        check(ProgressRules.miningTier("NETHER_GOLD_ORE")==3 && ProgressRules.miningTier("NETHER_QUARTZ_ORE")==3,"Job-only Nether ores retained");
        check(ProgressRules.required(1)==20&&ProgressRules.required(2)==50&&ProgressRules.required(3)==90,"All Slayer targets preserved");
        check(ProgressRules.inside(1.75,4.5,-1.75),"Portal boundary included");
        check(ProgressRules.inside(0,-2,0),"Portal lower bound included");
        check(!ProgressRules.inside(1.751,0,0)&&!ProgressRules.inside(0,4.501,0)&&!ProgressRules.inside(0,-2.001,0),"Outside portal bounds excluded");
        ProgressRules.SlayerProgress mission=new ProgressRules.SlayerProgress(); mission.active=true; mission.tier=1; mission.required=20;
        for(int kill=0;kill<19;kill++)mission.recordKill();
        check(!mission.ready(),"Slayer does not finish before target");
        mission.recordKill(); check(mission.ready(),"Slayer ready exactly at target");
        mission.recordKill(); check(mission.kills==20&&mission.active,"Failed spawn retries never overflow or erase objective");
        mission.confirmSpawn(); check(!mission.active&&mission.kills==0,"Only confirmed spawn finishes mission");
        mission.recordKill(); check(mission.kills==0,"Inactive missions do not count kills");
        var configuredChest=new java.util.LinkedHashMap<String,Integer>();configuredChest.put("COAL_ORE",0);configuredChest.put("DIAMOND_ORE",100);
        for(int roll=1;roll<=100;roll++)check(ActivityRules.weightedPrize(roll,configuredChest).equals("DIAMOND_ORE"),"Configurable chest weights respect 0/100 percent");
        boolean invalidWeights=false;try{ActivityRules.weightedPrize(1,java.util.Map.of("COAL_ORE",50));}catch(IllegalArgumentException expected){invalidWeights=true;}check(invalidWeights,"Reject invalid prize distribution rather than awarding fallback");
        System.out.println("PASS: " + assertions + " assertions; all 300 quality rolls, phase locks, 200 HP and 98,304 chunk positions.");
    }
}
