package dev.hydev.spectrecore.cooking;

/** Precision math isolated from Bukkit so timing rules can be unit-tested. */
public final class CookingTiming {
    private CookingTiming() {}

    /** Half-open interval, with the middle fraction being the sweet spot. */
    public static boolean isPrecise(long elapsedMs, int startSeconds, int endSeconds, double fraction) {
        if (startSeconds < 0 || endSeconds <= startSeconds || !Double.isFinite(fraction)) return false;
        long start = startSeconds * 1000L, end = endSeconds * 1000L;
        if (elapsedMs < start || elapsedMs >= end) return false;
        double safeFraction = Math.max(0.05, Math.min(1.0, fraction));
        double mid = (start + end) / 2.0;
        double half = ((end - start) * safeFraction) / 2.0;
        return elapsedMs >= mid - half && elapsedMs < mid + half;
    }

    public static String quality(long elapsedMs, int readyFromSeconds, int readyUntilSeconds,
                                 boolean stirred, boolean preciseStir, boolean precisionEnabled) {
        if (elapsedMs >= readyUntilSeconds * 1000L) return "pasado";
        return stirred && (!precisionEnabled || preciseStir) ? "exquisito" : "normal";
    }
}
