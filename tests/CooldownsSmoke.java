import dev.hydev.spectrecore.core.Cooldowns;
import java.util.UUID;

public class CooldownsSmoke {
    public static void main(String[] args) {
        Cooldowns cds = new Cooldowns();
        UUID p = UUID.randomUUID();
        UUID q = UUID.randomUUID();
        assert cds.ready(p, "necrotica");
        assert cds.start(p, "necrotica", 45);
        assert !cds.ready(p, "necrotica");
        assert !cds.start(p, "necrotica", 45);
        assert cds.ready(p, "botania");
        assert cds.ready(q, "necrotica");
        cds.reset(p, "necrotica");
        assert cds.ready(p, "necrotica");
        assert cds.start(p, "necrotica", 45);
        cds.resetAll(p);
        assert cds.ready(p, "necrotica");
        System.out.println("OK — cooldowns por jugador y habilidad; bloqueos y reset");
    }
}
