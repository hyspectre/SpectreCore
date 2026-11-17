package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import dev.hydev.spectrecore.storage.AtomicFileStore;

public final class YamlStore {
    private YamlStore() { }
    public static boolean save(JavaPlugin plugin, String filename, YamlConfiguration yaml) {
        Path target = new File(plugin.getDataFolder(), filename).toPath();
        try {
            AtomicFileStore.writeUtf8(target, yaml.saveToString());
            return true;
        } catch (IOException e) { plugin.getLogger().severe(Settings.text("nucleo", "YamlStore.texto-001", "No se pudo guardar ") + filename + ": " + e.getMessage()); return false; }
    }
}
