package dev.hydev.spectrecore.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Durable, fail-closed journal for operations that touch both Minecraft inventory and disk.
 * This does NOT offer exactly-once delivery. An interrupted claim is quarantined for a human
 * to reconcile; it is intentionally never replayed or silently deleted.
 */
public final class ClaimJournal {
    public record Claim(UUID id, String station, UUID player, String operation, String subject,
                        int count, long createdAt) { }
    private static final int MAX_CLAIMS = 512;
    private final Path file;
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    private boolean ready;

    public ClaimJournal(Path file) { this.file = file; }
    public boolean load() {
        ready = false;
        File source = file.toFile();
        Map<UUID, Claim> loaded = new LinkedHashMap<>();
        try {
            if (!Files.notExists(file)) {
                var yaml = StateYaml.load(source);
                if (!yaml.isInt("schema-version") || yaml.getInt("schema-version") != 1) return false;
                ConfigurationSection section = yaml.getConfigurationSection("claims");
                if (yaml.contains("claims") && section == null) return false;
                // Historical empty journals have schema-version: 1 without the claims section.
                if (section != null) {
                    if (section.getKeys(false).size() > MAX_CLAIMS) return false;
                    for (String raw : section.getKeys(false)) {
                        UUID id = StateYaml.uuid(raw);
                        ConfigurationSection value = section.getConfigurationSection(raw);
                        if (value == null) return false;
                        for (String field : List.of("station", "player", "operation", "subject"))
                            if (!value.isString(field)) return false;
                        String station = require(value.getString("station"), 240);
                        UUID player = StateYaml.uuid(require(value.getString("player"), 36));
                        String operation = require(value.getString("operation"), 32);
                        String subject = require(value.getString("subject"), 128);
                        long count = StateYaml.nonNegative(value, "count");
                        long created = StateYaml.nonNegative(value, "created-at");
                        if (count < 1 || count > 1024 || created < 1) return false;
                        loaded.put(id, new Claim(id, station, player, operation, subject, (int)count, created));
                    }
                }
            }
            claims.clear();
            claims.putAll(loaded);
            ready = true; return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
            return false;
        }
    }
    private static String require(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit) throw new IllegalArgumentException("Registro incompleto");
        return value;
    }
    public boolean ready() { return ready; }
    public int count() { return claims.size(); }
    public List<Claim> unresolved() { return List.copyOf(claims.values()); }
    public boolean blocked(String station) {
        return !ready || claims.values().stream().anyMatch(claim -> claim.station().equals(station));
    }
    /** Must run on the server thread. The caller must not mutate or grant until a token is returned. */
    public UUID begin(String station, UUID player, String operation, String subject, int count) {
        if (!ready || blocked(station) || claims.size() >= MAX_CLAIMS) return null;
        UUID id = UUID.randomUUID();
        claims.put(id, new Claim(id, require(station,240), player, require(operation,32), require(subject,128),
            Math.max(1, Math.min(1024, count)), Instant.now().toEpochMilli()));
        if (!persist()) { claims.remove(id); ready = false; return null; }
        return id;
    }
    /** Completion removes a durable quarantine only AFTER Minecraft and state writes have succeeded. */
    public boolean finish(UUID token) { return remove(token); }
    /** Admin-only, manual reconciliation of an ambiguous crash window. Never awards or refunds items. */
    public boolean resolve(UUID token) { return remove(token); }
    private boolean remove(UUID token) {
        if (!ready || !claims.containsKey(token)) return false;
        Claim previous = claims.remove(token);
        if (persist()) return true;
        claims.put(token, previous); ready = false;
        return false;
    }
    private boolean persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.createSection("claims");
        for (Claim c : claims.values()) {
            String root = "claims." + c.id();
            yaml.set(root + ".station", c.station());
            yaml.set(root + ".player", c.player().toString());
            yaml.set(root + ".operation", c.operation());
            yaml.set(root + ".subject", c.subject());
            yaml.set(root + ".count", c.count());
            yaml.set(root + ".created-at", c.createdAt());
        }
        try { AtomicFileStore.writeUtf8(file, yaml.saveToString()); return true; }
        catch (IOException ex) { return false; }
    }
}
