import dev.hydev.spectrecore.relicario.RelicarioRules;
import java.util.*;

public final class RelicarioRulesSmoke {
    private static void ok(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        var low = new RelicarioRules.Relic("BROTE_MARCHITO", "raigambre", 1, Map.of("mana", 6.0));
        var high = new RelicarioRules.Relic("NUCLEO_SILVESTRE", "raigambre", 2, Map.of("mana", 16.0));
        var flat = new RelicarioRules.Relic("CORONA_PEREGRINO", "", 1, Map.of("mana", 10.0));
        var doubleStat = new RelicarioRules.Relic("ECLIPSE_BOLSILLO", "", 1,
                Map.of("magic_damage", 1.0, "melee_damage", 1.0));
        var catalog = List.of(low, high, flat, doubleStat);
        var caps = Map.of("mana", 20.0, "magic_damage", 4.0, "melee_damage", 4.0);
        var discovered = Set.of("BROTE_MARCHITO","NUCLEO_SILVESTRE","CORONA_PEREGRINO","ECLIPSE_BOLSILLO");
        var sum = RelicarioRules.totals(catalog,discovered,caps);
        ok(sum.get("mana") == 20.0); // 6+16+10, capped
        ok(sum.get("magic_damage") == 1.0 && sum.get("melee_damage") == 1.0);
        ok(RelicarioRules.active(catalog,discovered).size()==4);
        ok(RelicarioRules.totals(catalog,discovered,Map.of("mana",100.0,"magic_damage",4.0,"melee_damage",4.0)).get("mana")==32.0);
        ok(!RelicarioRules.canAbsorb(low.id(),discovered,RelicarioRules.validateCatalog(catalog)));
        ok(RelicarioRules.canAbsorb(low.id(),Set.of(),RelicarioRules.validateCatalog(catalog)));
        ok(RelicarioRules.totals(catalog,Set.of("BROTE_MARCHITO"),caps).get("mana")==6.0);
        ok(RelicarioRules.totals(catalog,Set.of("MISSING"),caps).isEmpty());
        try {
            RelicarioRules.validateCatalog(List.of(low,low));
            throw new AssertionError("duplicate should fail");
        } catch (IllegalArgumentException expected) { }
        try {
            new RelicarioRules.Relic("BAD", "broken", 1, Map.of("mana",Double.NaN));
            throw new AssertionError("nonfinite should fail");
        } catch (IllegalArgumentException expected) { }
        // Ranks and families are labels, not mutually exclusive equipment slots.
        ok(RelicarioRules.active(List.of(low, new RelicarioRules.Relic("OTHER_BROTE","raigambre",1,Map.of("mana",5.0))), Set.of("BROTE_MARCHITO","OTHER_BROTE")).size()==2);
        ok(RelicarioRules.previewGain(catalog, Set.of(low.id()), caps, high.id()).get("mana") == 14.0);
        ok(RelicarioRules.previewGain(catalog, Set.of(low.id(), high.id()), caps, flat.id()).get("mana") == 0.0);
        ok(RelicarioRules.previewGain(catalog, Set.of(low.id()), caps, low.id()).isEmpty());
        ok(RelicarioRules.previewGain(catalog, Set.of(), caps, doubleStat.id()).get("melee_damage") == 1.0);
        System.out.println("PASS RelicarioRulesSmoke: simultaneous bonuses, global caps, duplicate safety and deterministic recomputation");
    }
}
