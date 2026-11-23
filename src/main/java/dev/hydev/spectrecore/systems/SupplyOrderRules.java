package dev.hydev.spectrecore.systems;

import java.util.Arrays;

/** Pure allocation rules; never touch another item when paying a material order. */
public final class SupplyOrderRules {
    private SupplyOrderRules() { }

    /**
     * The returned array is the quantity to consume from each counted stack,
     * or an empty array when the order cannot be satisfied.
     */
    public static int[] allocation(int[] matchingStacks, int required) {
        if (matchingStacks == null || required < 1 || required > 1024)
            throw new IllegalArgumentException("Invalid order");
        int[] taken = new int[matchingStacks.length];
        int remaining = required;
        for (int i = 0; i < matchingStacks.length; i++) {
            int stack = matchingStacks[i];
            if (stack < 0 || stack > 99) throw new IllegalArgumentException("Invalid inventory stack");
            taken[i] = Math.min(stack, remaining);
            remaining -= taken[i];
        }
        return remaining == 0 ? taken : new int[0];
    }

    public static boolean eligible(int currentRank, int minimumRank, int available, int required) {
        return minimumRank >= 1 && minimumRank <= 3 && currentRank >= minimumRank
                && required >= 1 && required <= 64 && available >= required;
    }
}
