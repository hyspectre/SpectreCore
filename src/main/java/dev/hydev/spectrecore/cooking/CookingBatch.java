package dev.hydev.spectrecore.cooking;

/** Pure calculations for a multi-serving cauldron; no Paper dependency. */
public final class CookingBatch {
    private CookingBatch() {}

    public static int portionsFromWater(int waterLevel) {
        return Math.max(1, Math.min(3, waterLevel));
    }

    public static int requiredIngredients(int portions) {
        return portionsFromWater(portions) * 3;
    }

    /** Ingredients are grouped by type: N potatoes, then N meats, then N mushrooms. */
    public static int ingredientIndex(int accepted, int portions) {
        if (accepted < 0 || accepted >= requiredIngredients(portions)) return -1;
        return accepted / portionsFromWater(portions);
    }

    /** Do not consume items beyond this ingredient's quota, even from a stack of 64. */
    public static int acceptedFromStack(int accepted, int portions, int stackAmount) {
        if (stackAmount <= 0 || ingredientIndex(accepted, portions) < 0) return 0;
        int need = portionsFromWater(portions) - (accepted % portionsFromWater(portions));
        return Math.min(need, stackAmount);
    }

    public static boolean readyToCook(int accepted, int portions) {
        return accepted == requiredIngredients(portions);
    }

    public static int visibleIngredients(int accepted, int portions) {
        if (accepted <= 0) return 0;
        return Math.min(3, (accepted - 1) / portionsFromWater(portions) + 1);
    }
}
