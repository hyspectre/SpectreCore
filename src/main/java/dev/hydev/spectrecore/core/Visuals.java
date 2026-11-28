package dev.hydev.spectrecore.core;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the entities made by the plugin and removes leftovers on startup. */
public final class Visuals {
    private final NamespacedKey marker;
    private final NamespacedKey legacyBotania;
    private final JavaPlugin plugin;

    public Visuals(JavaPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "managed_visual");
        legacyBotania = new NamespacedKey("hydevbotania", "botania_display");
    }

    public void mark(Display display) {
        display.setPersistent(false);
        display.setViewRange((float) plugin.getConfig().getDouble("engine.visuals.view-range", 0.75));
        display.setTeleportDuration(Settings.i("equipamiento", "Visuals.setteleportduration-001-arg0", 2));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Settings.i("equipamiento", "Visuals.setinterpolationduration-002-arg0", 2));
        display.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
    }

    public void removeOrphans() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) {
                    display.remove(); removed++;
                }
            }
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (display.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)
                        || display.getPersistentDataContainer().has(legacyBotania, PersistentDataType.BYTE)) {
                    display.remove(); removed++;
                }
            }
        }
        if (removed > 0) plugin.getLogger().info(Settings.plain("equipamiento", "Visuals.mensaje-001", "Displays huérfanos eliminados: {removed}", "removed", removed));
    }
}
