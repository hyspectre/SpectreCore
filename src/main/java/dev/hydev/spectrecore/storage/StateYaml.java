package dev.hydev.spectrecore.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Read existing player state without Bukkit's tolerant loadConfiguration fallback. */
public final class StateYaml {
    private StateYaml() { }
    public static ConfigurationSection read(File file, String root) throws IOException, InvalidConfigurationException {
        if (Files.notExists(file.toPath())) return new YamlConfiguration().createSection(root);
        YamlConfiguration yaml = load(file);
        ConfigurationSection section = yaml.getConfigurationSection(root);
        if (section == null) throw new IOException("Missing state section: " + root);
        return section;
    }
    public static YamlConfiguration load(File file) throws IOException, InvalidConfigurationException {
        if (!Files.isRegularFile(file.toPath()) || Files.size(file.toPath()) == 0)
            throw new IOException("Invalid or empty state file: " + file.getName());
        String text = Files.readString(file.toPath());
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object raw = new Yaml(new SafeConstructor(options)).load(text);
        if (!(raw instanceof Map<?, ?>)) throw new IOException("Expected state mapping: " + file.getName());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
    public static UUID uuid(String key) throws IOException {
        try {
            UUID id = UUID.fromString(key);
            if (id.toString().equals(key)) return id;
        } catch (IllegalArgumentException ignored) { }
        throw new IOException("Invalid UUID in state: " + key);
    }
    public static long nonNegative(ConfigurationSection row, String key) throws IOException {
        Object value = row.get(key);
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0)
            throw new IOException("Invalid integral state field: " + row.getCurrentPath() + "." + key);
        return ((Number)value).longValue();
    }
}
