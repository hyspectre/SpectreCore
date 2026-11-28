import dev.hydev.spectrecore.mining.GeodeProgressRules;

public final class GeodeProgressSmoke {
    public static void main(String[] args) {
        long target = GeodeProgressRules.TARGET;
        long normal = GeodeProgressRules.step(.5, 0);
        assert normal == 500_000L;
        long progress = 0;
        for (int i = 1; i < 200; i++) {
            progress = GeodeProgressRules.advance(progress, normal);
            assert progress < target;
            assert GeodeProgressRules.remaining(progress, normal) == 200 - i;
        }
        progress = GeodeProgressRules.advance(progress, normal);
        assert progress == target;
        assert GeodeProgressRules.afterDiscovery(progress) == 0;
        // Keep the full meter when the marker cannot be placed; retry at the next valid ore.
        assert GeodeProgressRules.advance(GeodeProgressRules.stored(progress), normal) == target;
        long bonus = GeodeProgressRules.step(.5, 30);
        assert bonus == 650_000L && GeodeProgressRules.remaining(0, bonus) == 154;
        progress = 0;
        for (int i = 0; i < 153; i++) progress = GeodeProgressRules.advance(progress, bonus);
        assert progress < target;
        progress = GeodeProgressRules.advance(progress, bonus);
        assert progress >= target && GeodeProgressRules.afterDiscovery(progress) == 100_000L;
        assert GeodeProgressRules.step(Double.NaN, 0) == 0;
        assert GeodeProgressRules.step(0, 30) == 0;
        assert GeodeProgressRules.step(100, 30) == target;
        assert GeodeProgressRules.stored(-100) == 0;
        assert GeodeProgressRules.stored(Long.MAX_VALUE) == target;
        System.out.println("PASS Geoda: 200 menas, Lupa de Espato, overflow, retry al 100% y límites");
    }
}
