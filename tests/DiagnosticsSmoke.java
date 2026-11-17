import dev.hydev.spectrecore.diagnostics.DiagnosticsService;
import java.util.List;

/** Independent Java 21 regression checks: no Paper JVM required. */
public final class DiagnosticsSmoke {
    public static void main(String[] args) {
        DiagnosticsService d = new DiagnosticsService(false);
        d.record("cocina.save", 0, false);
        assert d.snapshot().isEmpty() : "disabled diagnostics should not allocate";
        d.enabled(true);
        long start = d.start();
        d.record("cocina.save", start, true);
        d.record("cocina.save", start, false);
        d.record("bad metric with spaces", start, true);
        assert d.snapshot().size() == 1 : "invalid metric must be rejected";
        var single = d.snapshot().getFirst();
        assert single.events() == 2 && single.failures() == 1;
        assert single.elapsedNanos() >= 0 && single.averageMs() >= 0;
        for (int i = 0; i < 100; i++) d.record("module." + i, d.start(), true);
        assert d.snapshot().size() <= 64 : "metric cardinality must be capped";
        d.enabled(false);
        int count = d.snapshot().size();
        d.record("added.disabled", d.start(), true);
        assert d.snapshot().size() == count;
        d.reset();
        assert d.snapshot().isEmpty();
        System.out.println("PASS DiagnosticsSmoke: disabled mode, counters, failures, caps, reset");
    }
}
