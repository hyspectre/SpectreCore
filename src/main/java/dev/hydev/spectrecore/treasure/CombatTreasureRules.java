package dev.hydev.spectrecore.treasure;

public final class CombatTreasureRules {
    private CombatTreasureRules() { }
    public static int tier(String entityType) {
        return switch (entityType) {
            case "ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "SLIME" -> 1;
            case "WITCH", "ENDERMAN", "BLAZE", "GUARDIAN", "PHANTOM" -> 2;
            case "WITHER_SKELETON", "EVOKER", "VINDICATOR", "WARDEN" -> 3;
            default -> 0;
        };
    }
}
