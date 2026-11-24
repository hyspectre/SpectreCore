package dev.hydev.spectrecore.systems;

/** Side-effect free arithmetic and limits for Hydev's native Miner workshop. */
public final class MinerWorkshopRules {
    private MinerWorkshopRules() { }
    public static int batches(int available, int unitsPerBatch, int cap) {
        if (available < 0 || unitsPerBatch < 1 || cap < 1 || cap > 64)
            throw new IllegalArgumentException("Miner workshop batch limit");
        return Math.min(cap, available / unitsPerBatch);
    }
    public static int selected(int available, int unitsPerBatch, int cap, int requested) {
        int maximum = batches(available, unitsPerBatch, cap);
        return requested == -1 ? maximum : requested > 0 ? Math.min(requested, maximum) : 0;
    }
    public static long payout(int batches, long perUnit) {
        if (batches < 1 || batches > 64 || perUnit < 0 || perUnit > 1_000_000L)
            throw new IllegalArgumentException("Miner workshop payout");
        return Math.multiplyExact(batches, perUnit);
    }
    public static boolean ready(int level, int minimum, int batches, int cap) {
        return minimum >= 1 && level >= minimum && batches > 0 && batches <= cap && cap <= 64;
    }
}
