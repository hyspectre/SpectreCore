package dev.hydev.spectrecore.relicario;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure progression logic. Player data is a set of discovered IDs, never an
 * accumulated stat counter. Recomputing after every join/reload is idempotent.
 */
public final class RelicarioRules {
    private RelicarioRules() { }

    public record Relic(String id, String family, int rank, Map<String, Double> bonuses) {
        public Relic {
            if (id == null || !id.matches("[A-Z][A-Z0-9_]{1,63}"))
                throw new IllegalArgumentException("Invalid accessory id");
            if (family == null) family = "";
            if (!family.isEmpty() && !family.matches("[a-z][a-z0-9_-]{1,39}"))
                throw new IllegalArgumentException("Invalid family");
            if (rank < 1 || rank > 16 || (family.isEmpty() && rank != 1))
                throw new IllegalArgumentException("Invalid rank");
            if (bonuses == null || bonuses.isEmpty() || bonuses.size() > 8)
                throw new IllegalArgumentException("Invalid stat map");
            for (var stat : bonuses.entrySet()) {
                if (!stat.getKey().matches("[a-z][a-z0-9_]{1,39}") || stat.getValue() == null
                        || !Double.isFinite(stat.getValue()) || stat.getValue() <= 0
                        || stat.getValue() > 1000) throw new IllegalArgumentException("Invalid stat");
            }
            bonuses = Map.copyOf(bonuses);
        }
    }

    public static Map<String, Relic> validateCatalog(Collection<Relic> relics) {
        Objects.requireNonNull(relics);
        if (relics.isEmpty() || relics.size() > 256) throw new IllegalArgumentException("Catalog size");
        Map<String, Relic> result = new LinkedHashMap<>();
        for (Relic relic : relics) {
            if (result.putIfAbsent(relic.id(), relic) != null)
                throw new IllegalArgumentException("Duplicate id " + relic.id());
        }
        return Collections.unmodifiableMap(result);
    }

    /** Every consumed ID contributes concurrently. Family and rank only classify relics. */
    public static List<Relic> active(Collection<Relic> catalog, Set<String> discovered) {
        var byId = validateCatalog(catalog);
        Objects.requireNonNull(discovered);
        return discovered.stream().map(byId::get).filter(Objects::nonNull)
                .sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    /** Aggregated permanent contributions, clamped by the configured cap per stat. */
    public static Map<String, Double> totals(Collection<Relic> catalog,
                                             Set<String> discovered, Map<String, Double> caps) {
        Objects.requireNonNull(caps);
        Map<String, Double> result = new LinkedHashMap<>();
        for (Relic relic : active(catalog, discovered))
            for (var bonus : relic.bonuses().entrySet())
                result.merge(bonus.getKey(), bonus.getValue(), Double::sum);
        for (var entry : result.entrySet()) {
            double max = caps.getOrDefault(entry.getKey(), 0.0);
            if (!Double.isFinite(max) || max < 0 || max > 100000)
                throw new IllegalArgumentException("Invalid cap for " + entry.getKey());
            entry.setValue(Math.min(max, Math.round(entry.getValue() * 10000.0) / 10000.0));
        }
        return Map.copyOf(result);
    }

    public static boolean canAbsorb(String id, Set<String> discovered, Map<String, Relic> catalog) {
        return id != null && catalog.containsKey(id) && !discovered.contains(id);
    }

}
