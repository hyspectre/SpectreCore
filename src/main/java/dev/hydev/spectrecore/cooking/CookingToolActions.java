package dev.hydev.spectrecore.cooking;

/** Single source of truth for the physical cooking controls (no Bukkit dependency). */
public final class CookingToolActions {
    private CookingToolActions() {}

    public enum Use { CREATE, STIR, CLEAN, REMOVE, SERVE, NONE }

    public static Use select(String material, boolean registered, boolean burned) {
        if (material == null) return Use.NONE;
        if (!registered) return "FLINT_AND_STEEL".equals(material) ? Use.CREATE : Use.NONE;
        return switch (material) {
            case "WOODEN_SHOVEL" -> Use.STIR;
            case "BOWL" -> Use.SERVE;
            case "SPONGE", "WET_SPONGE" -> burned ? Use.CLEAN : Use.REMOVE;
            default -> Use.NONE;
        };
    }
}
