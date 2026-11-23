import dev.hydev.spectrecore.mining.AmethystResonanceRules;

public final class AmethystResonanceSmoke {
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static void invalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected invalid amethyst weights"); }
        catch (IllegalArgumentException correct) { }
    }

    public static void main(String[] ignored) {
        check(AmethystResonanceRules.available(75, 0, 123L, 75, 0, 8), "ready");
        check(!AmethystResonanceRules.available(74, 0, 123L, 75, 0, 8), "charge");
        check(!AmethystResonanceRules.available(100, 400L, 123L, 75, 0, 8), "cooldown");
        check(!AmethystResonanceRules.available(100, 0, 123L, 75, 8, 8), "global cap");
        check(AmethystResonanceRules.activationCost(75, 3, 5) == 45, "three crystals cost 45");
        check(AmethystResonanceRules.activationCost(75, 4, 5) == 60, "four crystals cost 60");
        check(AmethystResonanceRules.activationCost(75, 5, 5) == 75, "full field cost 75");
        invalid(() -> AmethystResonanceRules.activationCost(75, 6, 5));
        check(AmethystResonanceRules.quality(0, 75, 22, 3) == 0, "basic first");
        check(AmethystResonanceRules.quality(74.999, 75, 22, 3) == 0, "basic edge");
        check(AmethystResonanceRules.quality(75, 75, 22, 3) == 1, "intermediate edge");
        check(AmethystResonanceRules.quality(96.999, 75, 22, 3) == 1, "intermediate last");
        check(AmethystResonanceRules.quality(97, 75, 22, 3) == 2, "superior edge");
        check(AmethystResonanceRules.quality(99.999, 75, 22, 3) == 2, "superior last");
        invalid(() -> AmethystResonanceRules.quality(30, 70, 20, 9));
        invalid(() -> AmethystResonanceRules.quality(-1, 75, 22, 3));
        invalid(() -> AmethystResonanceRules.quality(Double.NaN, 75, 22, 3));
        check(AmethystResonanceRules.hit(1000, 600, 300, 3), "hit cooldown elapsed");
        check(!AmethystResonanceRules.hit(800, 600, 300, 3), "hit spam rejected");
        check(!AmethystResonanceRules.hit(1000, 600, 300, 0), "no extra hits");
        check(AmethystResonanceRules.slot(2, 2, 4), "valid offset");
        check(!AmethystResonanceRules.slot(1, 0, 4), "not on player");
        check(!AmethystResonanceRules.slot(5, 0, 4), "not outside radius");
        System.out.println("PASS AmethystResonanceSmoke: energy, odds, hits and bounded positions");
    }
}
