package dev.hydev.spectrecore.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Non-invasive runtime feature detection. Never directly invokes optional plugin classes. */
public final class IntegrationRegistry {
    private static final String[] NAMES = {"MythicLib", "MMOItems", "MythicMobs", "ItemsAdder", "AuraSkills",
            "FancyHolograms", "FancyNPCs", "zMenu", "WorldGuard", "LuckPerms", "Essentials",
            "FastAsyncWorldEdit", "Vault"};
    private final PluginManager manager;
    public IntegrationRegistry(PluginManager manager) { this.manager = manager; }
    public boolean available(String name) {
        for (String known : NAMES) if (known.equalsIgnoreCase(name)) {
            Plugin plugin = manager.getPlugin(known);
            return plugin != null && plugin.isEnabled();
        }
        return false;
    }
    public Map<String, Boolean> status() {
        Map<String, Boolean> status = new LinkedHashMap<>();
        for (String name : NAMES) status.put(name, available(name));
        return Collections.unmodifiableMap(status);
    }
}
