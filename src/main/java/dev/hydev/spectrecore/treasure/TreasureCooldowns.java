package dev.hydev.spectrecore.treasure;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public final class TreasureCooldowns {
    private final JavaPlugin plugin;
    private final TreasureLedger ledger = new TreasureLedger();
    private long duration = 5000;
    private boolean dirty;
    private BukkitTask timer;
    public TreasureCooldowns(JavaPlugin plugin) { this.plugin = plugin; }
    public void enable() {
        reload();
        File file = new File(plugin.getDataFolder(), "treasure-data.yml");
        var data = YamlConfiguration.loadConfiguration(file);
        var deadlines = data.getConfigurationSection("cooldowns");
        if (deadlines != null) for (String id : deadlines.getKeys(false)) try { ledger.restore(UUID.fromString(id), deadlines.getLong(id)); }
        catch (IllegalArgumentException ignored) { plugin.getLogger().warning(Settings.text("tesoros", "TreasureCooldowns.mensaje-001", "UUID inválido en treasure-data.yml: {id}", "id", id)); }
        if (!file.isFile()) {
            var old = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mining-data.yml")).getConfigurationSection("players");
            if (old != null) for (String id : old.getKeys(false)) try { ledger.restore(UUID.fromString(id), old.getLong(id + ".treasure-until")); }
            catch (IllegalArgumentException ignored) { }
            dirty = true; save();
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200, 200);
    }
    public void reload() {
        duration = Math.max(0, dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "tesoros/config.yml").getLong("cooldown-compartido-segundos", 5)) * 1000;
    }
    public boolean ready(UUID player) { return ledger.ready(player, System.currentTimeMillis()); }
    public void awarded(UUID player) { ledger.start(player, System.currentTimeMillis(), duration); dirty = true; }
    public void disable() { if (timer != null) timer.cancel(); save(); }
    private void save() {
        var data = new YamlConfiguration();
        ledger.snapshot(System.currentTimeMillis()).forEach((id, deadline) -> data.set("cooldowns." + id, deadline));
        Path target = new File(plugin.getDataFolder(), "treasure-data.yml").toPath();
        Path temp = target.resolveSibling("treasure-data.yml.tmp");
        try {
            Files.writeString(temp, data.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (IOException error) { plugin.getLogger().severe(Settings.text("tesoros", "TreasureCooldowns.mensaje-002", "No se pudo guardar cooldown de tesoros: {error-getmessage}", "error-getmessage", error.getMessage())); }
    }
}
