package dev.hydev.spectrecore.relicario;

import dev.hydev.spectrecore.storage.AtomicFileStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread, fail-closed discovery ledger.
 * Persist an updated full snapshot BEFORE consuming a held item.
 * A crash between commit and removal can leave a harmless unconsumable duplicate,
 * never a paid-for item missing from the collection.
 * <p>
 * New in 0.9: every discovered ID may be recorded as "pending withdrawal".
 * The plugin must call {@link #confirmWithdrawal(UUID, String)} after the
 * holding item has been removed from the player's inventory. If the server
 * crashes before the call, the pending flag survives reload and the admin
 * can resolve the inconsistency via {@link #rollbackPending(UUID)}.
 */
public final class RelicarioStore {
    private final Path directory;
    private final Map<UUID, Set<String>> loaded = new HashMap<>();
    private final Map<UUID, Set<String>> pendingWithdrawal = new HashMap<>();
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

    /** Must be invoked at join, or lazily before the first interaction. */
    public boolean load(UUID uuid) {
        if (loaded.containsKey(uuid)) return true;
        if (invalid.contains(uuid)) return false;
        Path file = path(uuid);
        if (Files.exists(file)) {
            try {
                if (!Files.isRegularFile(file) || Files.size(file) > 48_000)
                    throw new IOException("Invalid ledger file");
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file.toFile());
                if (yaml.getInt("version", -1) != 1 || !uuid.toString().equals(yaml.getString("uuid"))
                        || !yaml.isList("absorbed")) throw new IOException("Invalid ledger schema");
                List<?> raw = yaml.getList("absorbed");
                if (raw == null || raw.size() > limit) throw new IOException("Accessory ledger overflow");
                Set<String> ids = new HashSet<>();
                for (Object value : raw) {
                    if (!(value instanceof String id) || !id.matches("[A-Z][A-Z0-9_]{1,63}")
                            || !ids.add(id)) throw new IOException("Invalid/duplicate accessory id");
                }
                loaded.put(uuid, Set.copyOf(ids));
                // Load pending withdrawal set (may be missing in old files)
                pendingWithdrawal.put(uuid, new HashSet<>(yaml.getStringList("pending-withdrawal")));
                return true;
            } catch (Exception failure) {
                invalid.add(uuid);
                return false;
            }
        }
        loaded.put(uuid, Set.of());
        pendingWithdrawal.put(uuid, new HashSet<>());
        return true;
    }

    public Set<String> discovered(UUID uuid) {
        return loaded.getOrDefault(uuid, Set.of());
    }

    /** Filesystem write is atomic. On failure the old in-memory state is retained. */
    public boolean discover(UUID uuid, String id) {
        if (!loaded.containsKey(uuid) || invalid.contains(uuid) || id == null
                || !id.matches("[A-Z][A-Z0-9_]{1,63}")) return false;
        Set<String> old = loaded.get(uuid);
        if (old.contains(id) || old.size() >= limit) return false;
        Set<String> next = new HashSet<>(old);
        next.add(id);
        // Mark this new ID as pending withdrawal: the server must confirm after removing the item.
        Set<String> pending = new HashSet<>(pendingWithdrawal.getOrDefault(uuid, Set.of()));
        pending.add(id);
        pendingWithdrawal.put(uuid, pending);

        YamlConfiguration data = new YamlConfiguration();
        data.set("version", 1);
        data.set("uuid", uuid.toString());
        data.set("absorbed", next.stream().sorted().toList());
        data.set("pending-withdrawal", pending.stream().sorted().toList());
        try {
            AtomicFileStore.writeUtf8(path(uuid), data.saveToString());
            loaded.put(uuid, Set.copyOf(next));
            return true;
        } catch (IOException error) {
            invalid.add(uuid);
            // Roll back the pending flag if write failed
            pendingWithdrawal.put(uuid, pending);
            return false;
        }
    }

    /**
     * Call after the relicario item has been removed from the player's inventory.
     * Confirms the pending withdrawal so the ID is permanently recorded as consumed.
     * If the server never calls this, the pending flag survives reload and the
     * admin can decide via {@link #rollbackPending(UUID)}.
     */
    public boolean confirmWithdrawal(UUID uuid, String id) {
        if (!pendingWithdrawal.containsKey(uuid)) return false;
        Set<String> pending = pendingWithdrawal.get(uuid);
        if (!pending.contains(id)) return false;
        pending.remove(id);
        pendingWithdrawal.put(uuid, pending);

        // Persist the updated pending list
        Path file = path(uuid);
        if (!Files.exists(file)) return false;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            yaml.set("pending-withdrawal", pending.stream().sorted().toList());
            AtomicFileStore.writeUtf8(file, yaml.saveToString());
            return true;
        } catch (IOException e) {
            // Re-add to pending if persist failed
            pendingWithdrawal.put(uuid, pending);
            return false;
        }
    }

    /**
     * Roll back pending withdrawals for a given UUID. Use after a crash recovery
     * to either re-offer the relicario to the player (if still in inventory) or
     * permanently consummate the withdrawal.
     * <p>
     * Returns the set of IDs that were pending before rollback.
     */
    public Set<String> rollbackPending(UUID uuid) {
        if (!pendingWithdrawal.containsKey(uuid)) return Set.of();
        Set<String> before = new HashSet<>(pendingWithdrawal.get(uuid));
        pendingWithdrawal.put(uuid, new HashSet<>());
        // Persist empty pending list
        try {
            Path file = path(uuid);
            if (Files.exists(file)) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file.toFile());
                yaml.set("pending-withdrawal", new java.util.ArrayList<>());
                AtomicFileStore.writeUtf8(file, yaml.saveToString());
            }
        } catch (IOException ignored) { }
        return before;
    }

    public boolean healthy(UUID uuid) { return loaded.containsKey(uuid) && !invalid.contains(uuid); }
    public void unload(UUID uuid) { loaded.remove(uuid); pendingWithdrawal.remove(uuid); }
    public void clear() { loaded.clear(); pendingWithdrawal.clear(); }
}
