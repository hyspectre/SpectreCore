package dev.hydev.spectrecore.treasure;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import dev.hydev.spectrecore.storage.StateYaml;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TreasureCooldowns {
    private final JavaPlugin plugin;
    private final TreasureLedger ledger = new TreasureLedger();
    private long duration = 5000;
    private boolean dirty;
    private boolean storageHealthy;
    private BukkitTask timer;
    public TreasureCooldowns(JavaPlugin plugin) { this.plugin = plugin; }
    public void enable() {
        reload();
        File file = new File(plugin.getDataFolder(), "treasure-data.yml");
        boolean existing = !Files.notExists(file.toPath());
        Map<UUID, Long> loaded = new HashMap<>();
        try {
            var section = existing ? StateYaml.read(file, "cooldowns")
                    : StateYaml.read(new File(plugin.getDataFolder(), "mining-data.yml"), "players");
            for (String id : section.getKeys(false)) {
                UUID uuid;
                try { uuid = StateYaml.uuid(id); }
                catch (IOException error) {
                    throw new IOException(Settings.plain("tesoros", "TreasureCooldowns.mensaje-001", "UUID inválido en treasure-data.yml: {id}", "id", id), error);
                }
                if (existing) loaded.put(uuid, StateYaml.nonNegative(section, id));
                else {
                    var row = section.getConfigurationSection(id);
                    if (row == null) throw new IOException("Invalid legacy mining record");
                    if (row.contains("treasure-until")) loaded.put(uuid, StateYaml.nonNegative(row, "treasure-until"));
                }
            }
            loaded.forEach(ledger::restore);
            storageHealthy = true;
            if (!existing) { dirty = true; save(); }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Tesoros: datos inválidos; recompensas bloqueadas, archivo conservado.", error);
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200, 200);
    }
    public void reload() {
        duration = Math.max(0, dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "tesoros/config.yml").getLong("cooldown-compartido-segundos", 5)) * 1000;
    }
    public boolean healthy() { return storageHealthy; }
    public boolean ready(UUID player) { return storageHealthy && ledger.ready(player, System.currentTimeMillis()); }
    public void awarded(UUID player) { if (!storageHealthy) return; ledger.start(player, System.currentTimeMillis(), duration); dirty = true; }
    public void disable() { if (timer != null) timer.cancel(); save(); }
    private void save() {
        if (!storageHealthy || !dirty) return;
        var data = new YamlConfiguration();
        data.createSection("cooldowns");
        ledger.snapshot(System.currentTimeMillis()).forEach((id, deadline) -> data.set("cooldowns." + id, deadline));
        try {
            AtomicFileStore.writeUtf8(new File(plugin.getDataFolder(), "treasure-data.yml").toPath(), data.saveToString());
            dirty = false;
        } catch (IOException error) {
            storageHealthy = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, Settings.plain("tesoros", "TreasureCooldowns.mensaje-002", "No se pudo guardar cooldown de tesoros: {error-getmessage}", "error-getmessage", error.getMessage()) + "; recompensas bloqueadas.", error);
        }
    }
}
