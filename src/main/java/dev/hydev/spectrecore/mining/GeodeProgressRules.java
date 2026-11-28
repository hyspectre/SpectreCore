package dev.hydev.spectrecore.mining;

/** Fixed-point progress: avoids missed thresholds caused by floating point accumulation. */
public final class GeodeProgressRules {
    public static final long TARGET = 100_000_000L;
    private GeodeProgressRules() { }

    public static long step(double percent, double bonus) {
        if (!Double.isFinite(percent) || !Double.isFinite(bonus) || percent <= 0) return 0;
        return Math.round(Math.clamp(percent * (1 + bonus / 100), 0, 100) * 1_000_000);
    }

    public static long advance(long progress, long step) {
        long current = stored(progress);
        return current == TARGET ? TARGET : current + Math.clamp(step, 0, TARGET);
    }

    public static long stored(long progress) { return Math.clamp(progress, 0, TARGET); }

    public static long afterDiscovery(long progress) { return stored(progress - TARGET); }

    public static long remaining(long progress, long step) {
        return step <= 0 ? 0 : Math.ceilDiv(TARGET - stored(progress), step);
    }
}
