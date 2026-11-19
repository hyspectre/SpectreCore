package dev.hydev.spectrecore.systems;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only verification of MMOItems native use-permission restrictions.
 * Does NOT rewrite another plugin's configuration or grant broad permissions.
 */
public final class SlayerEquipmentAudit {
    private static final String PREFIX = "hydev.slayer.item.";
    private SlayerEquipmentAudit() { }

    public static void inspect(JavaPlugin spectreCore) {
        Plugin mmo = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (mmo == null || !mmo.isEnabled()) {
            spectreCore.getLogger().warning("[Slayer] MMOItems no está activo; los objetos no están verificados.");
            return;
        }
        File folder = mmo.getDataFolder();
        File configFile = new File(folder, "config.yml");
        if (!configFile.isFile()) {
            spectreCore.getLogger().warning("[Slayer] Falta plugins/MMOItems/config.yml; permisos no verificables.");
            return;
        }
        boolean itemsEnabled = YamlConfiguration.loadConfiguration(configFile)
                .getBoolean("permissions.items", false);
        if (!itemsEnabled) {
            spectreCore.getLogger().warning("[Slayer] MMOItems permissions.items debe ser true para exigir el acceso al equipamiento.");
        }

        var expectedNodes = SlayerProgressionRules.earnedNodes(SlayerProgressionRules.MAX_LEVEL);
        List<String> missing = new ArrayList<>();
        int verified = 0;
        for (String permission : expectedNodes) {
            if (!permission.startsWith(PREFIX)) continue;
            String itemName = permission.substring(PREFIX.length());
            int split = itemName.indexOf('.');
            if (split < 1 || split == itemName.length() - 1) {
                missing.add(permission + " (nodo mal formado)");
                continue;
            }
            String type = itemName.substring(0, split).toUpperCase(Locale.ROOT);
            String id = itemName.substring(split + 1).toUpperCase(Locale.ROOT);
            File file = new File(folder, "item/" + type + ".yml");
            if (!file.isFile()) {
                missing.add(type + "/" + id + " (archivo ausente)");
                continue;
            }
            YamlConfiguration items = YamlConfiguration.loadConfiguration(file);
            var base = items.getConfigurationSection(id + ".base");
            if (base != null && base.getStringList("permission").contains(permission))
                verified++;
            else missing.add(type + "/" + id);
        }
        if (!itemsEnabled || !missing.isEmpty()) {
            spectreCore.getLogger().warning("[Slayer] MMOItems: " + verified + "/16 requisitos verificados. "
                    + "Ejecuta tools/apply_slayer_mmoitems_permissions.py en staging; faltan: "
                    + String.join(", ", missing));
        } else {
            spectreCore.getLogger().info("[Slayer] MMOItems: 16/16 requisitos nativos de permiso verificados.");
        }
    }
}
