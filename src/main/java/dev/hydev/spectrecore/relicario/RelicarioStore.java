package dev.hydev.spectrecore.relicario;

import dev.hydev.spectrecore.storage.AtomicFileStore;
import dev.hydev.spectrecore.storage.StateYaml;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Main-thread ledger. Pending purchases never contribute to the collection. */
public final class RelicarioStore {
    public record Pending(String id, String token) {}
    private final Path directory;
    private final Map<UUID, Set<String>> loaded = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> legacyPending = new HashSet<>();
    private final Set<UUID> invalid = new HashSet<>();
    private final int limit;

    public RelicarioStore(File dataFolder, int maxIds) {
        if (maxIds < 32 || maxIds > 4096) throw new IllegalArgumentException("Invalid accessory limit");
        directory = new File(dataFolder, "data/relicario").toPath();
        limit = maxIds;
    }

    public boolean initialize() {
        try { Files.createDirectories(directory); return Files.isDirectory(directory); }
        catch (IOException failure) { return false; }
    }
    private Path path(UUID uuid) { return directory.resolve(uuid + ".yml"); }
    private static boolean validId(String id) { return id != null && id.matches("[A-Z][A-Z0-9_]{1,63}"); }

    public boolean load(UUID uuid) {
        if (loaded.containsKey(uuid)) return healthy(uuid);
        if (invalid.contains(uuid)) return false;
        Path file = path(uuid);
        try {
            Set<String> ids = new HashSet<>();
            if (!Files.notExists(file)) {
                if (!Files.isRegularFile(file) || Files.size(file) > 48_000)
                    throw new IOException("Invalid ledger file");
                YamlConfiguration yaml = StateYaml.load(file.toFile());
                int version = yaml.getInt("version", -1);
                if (!yaml.isInt("version") || (version != 1 && version != 2) || !uuid.toString().equals(yaml.getString("uuid"))
                        || !yaml.isList("absorbed")) throw new IOException("Invalid ledger schema");
                List<?> raw = yaml.getList("absorbed");
                if (raw == null || raw.size() > limit) throw new IOException("Accessory ledger overflow");
                for (Object value : raw) {
                    if (!(value instanceof String id) || !validId(id) || !ids.add(id))
                        throw new IOException("Invalid/duplicate accessory id");
                }
                if (version == 1 && yaml.contains("pending-withdrawal")) {
                    // The previous implementation counted these IDs as absorbed without any receipt.
                    // Preserve historical bonuses, block new purchases until an administrator reviews it.
                    List<?> old = yaml.getList("pending-withdrawal");
                    if (old == null || old.stream().anyMatch(value -> !ids.contains(value)))
                        throw new IOException("Invalid legacy pending withdrawal");
                    if (!old.isEmpty()) legacyPending.add(uuid);
                }
                if (version == 2 && yaml.contains("pending")) {
                    String id = yaml.getString("pending.id"), token = yaml.getString("pending.token");
                    if (!validId(id) || ids.contains(id) || ids.size() >= limit || token == null
                            || !UUID.fromString(token).toString().equals(token))
                        throw new IOException("Invalid pending purchase");
                    pending.put(uuid, new Pending(id, token));
                }
            }
            loaded.put(uuid, Set.copyOf(ids));
            return true;
        } catch (Exception failure) {
            invalid.add(uuid);
            return false;
        }
    }

    public Set<String> discovered(UUID uuid) { return loaded.getOrDefault(uuid, Set.of()); }
    public Pending pending(UUID uuid) { return pending.get(uuid); }
    public boolean blocked(UUID uuid) { return pending.containsKey(uuid) || legacyPending.contains(uuid); }

    /** Reserve a unique transaction on disk before touching the player's inventory. */
    public Pending begin(UUID uuid, String id) {
        if (!healthy(uuid) || blocked(uuid) || !validId(id)) return null;
        Set<String> old = loaded.get(uuid);
        if (old.contains(id) || old.size() >= limit) return null;
        Pending transaction = new Pending(id, UUID.randomUUID().toString());
        if (!write(uuid, old, transaction)) return null;
        pending.put(uuid, transaction);
        return transaction;
    }

    /** Only a receipt stored with player data can confirm the withdrawal. */
    public boolean confirm(UUID uuid, String receipt) {
        Pending transaction = pending.get(uuid);
        if (!healthy(uuid) || transaction == null || !transaction.token().equals(receipt)) return false;
        Set<String> next = new HashSet<>(loaded.get(uuid));
        next.add(transaction.id());
        if (!write(uuid, next, null)) return false;
        loaded.put(uuid, Set.copyOf(next));
        pending.remove(uuid);
        return true;
    }

    private boolean write(UUID uuid, Set<String> ids, Pending transaction) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("version", 2);
        data.set("uuid", uuid.toString());
        data.set("absorbed", ids.stream().sorted().toList());
        if (transaction != null) {
            data.set("pending.id", transaction.id());
            data.set("pending.token", transaction.token());
        }
        try {
            AtomicFileStore.writeUtf8(path(uuid), data.saveToString());
            return true;
        } catch (IOException error) {
            // Retain old confirmed state and pending transaction. Never accept another purchase.
            invalid.add(uuid);
            return false;
        }
    }

    public boolean healthy(UUID uuid) { return loaded.containsKey(uuid) && !invalid.contains(uuid); }
    public void unload(UUID uuid) {
        loaded.remove(uuid); pending.remove(uuid); legacyPending.remove(uuid); invalid.remove(uuid);
    }
    public void clear() { loaded.clear(); pending.clear(); legacyPending.clear(); invalid.clear(); }
}
