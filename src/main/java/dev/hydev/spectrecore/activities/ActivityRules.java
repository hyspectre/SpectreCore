package dev.hydev.spectrecore.activities;

/** Input rules independent of Bukkit, tested exhaustively at their boundaries. */
public final class ActivityRules {
    private ActivityRules() { }
    public static String weightedPrize(int roll,java.util.Map<String,Integer> weights) {
        if(roll<1||roll>100)throw new IllegalArgumentException("Tirada inválida");
        long sum=0;for(var weight:weights.values()){if(weight==null||weight<0)throw new IllegalArgumentException("Peso inválido");sum+=weight;}
        if(sum!=100)throw new IllegalArgumentException("Las probabilidades del cofre deben sumar 100");
        int cursor=0;for(var entry:weights.entrySet()){cursor+=entry.getValue();if(roll<=cursor)return entry.getKey();}
        throw new IllegalArgumentException("Sin premio");
    }
    public static String chestPrize(int roll) {
        if (roll < 1 || roll > 100) throw new IllegalArgumentException();
        if (roll <= 35) return "COAL_ORE";
        if (roll <= 60) return "IRON_ORE";
        if (roll <= 80) return "COPPER_ORE";
        if (roll <= 90) return "GOLD_ORE";
        if (roll <= 95) return "REDSTONE_ORE";
        if (roll <= 98) return "LAPIS_ORE";
        if (roll == 99) return "DIAMOND_ORE";
        return "EMERALD_ORE";
    }
    public static boolean cygnusEscapes(int roll) {
        if (roll < 1 || roll > 100) throw new IllegalArgumentException();
        return roll <= 30;
    }
    public static int cropTier(String material) {
        return switch (material) {
            case "WHEAT", "CARROTS", "POTATOES", "BEETROOTS" -> 1;
            case "PUMPKIN", "MELON", "SUGAR_CANE", "COCOA" -> 2;
            case "NETHER_WART", "CAVE_VINES", "CAVE_VINES_PLANT", "SWEET_BERRY_BUSH" -> 3;
            default -> 0;
        };
    }
}
