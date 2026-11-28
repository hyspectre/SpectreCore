package dev.hydev.spectrecore.core;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Level;

/** Only accepts real MMOItems IDs, never display-name/lore matches. */
public final class SetDetector {
    private final JavaPlugin plugin;
    private Method getId;
    private Method getTypeName;

    public SetDetector(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("MMOItems") == null) {
            plugin.getLogger().warning(Settings.text("equipamiento", "SetDetector.mensaje-001", "No se pudieron cargar las habilidades de los conjuntos."));
            return;
        }
        try {
            Class<?> api = Class.forName("net.Indyuce.mmoitems.MMOItems");
            getId = api.getMethod("getID", ItemStack.class);
            getTypeName = api.getMethod("getTypeName", ItemStack.class);
            plugin.getLogger().info(Settings.text("equipamiento", "SetDetector.mensaje-002", "Las habilidades de los conjuntos están disponibles."));
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, Settings.text("equipamiento", "SetDetector.etiqueta-003", "No se pueden consultar las propiedades del equipamiento."), ex);
        }
    }

    private boolean matches(ItemStack item, String type, String id) {
        if (item == null || item.getType() == Material.AIR || type == null || id == null || getId == null) return false;
        try {
            return Objects.equals(type, getTypeName.invoke(null, item)) && Objects.equals(id, getId.invoke(null, item));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // Fail closed: no display-name fallback, which would let renamed vanilla items activate skills.
            return false;
        }
    }

    /** Fifth set piece only counts with the exact actual MMOItems type/id. */
    public boolean hasWeapon(Player player, String type, String id) {
        return matches(player.getInventory().getItemInMainHand(), type, id);
    }

    /** Used by visual skill states to prevent duplicate activation on false client item names. */
    public boolean matchesArmorPiece(ItemStack item, String type, String id) {
        return matches(item, type, id);
    }

    public int countArmor(Player player, ConfigurationSection cfg) {
        if (cfg == null) return 0;
        String type = cfg.getString("type", "ARMOR");
        int count = 0;
        if (matches(player.getInventory().getHelmet(), type, cfg.getString("helmet"))) count++;
        if (matches(player.getInventory().getChestplate(), type, cfg.getString("chestplate"))) count++;
        if (matches(player.getInventory().getLeggings(), type, cfg.getString("leggings"))) count++;
        if (matches(player.getInventory().getBoots(), type, cfg.getString("boots"))) count++;
        return count;
    }

    public boolean hasArmor(Player player, ConfigurationSection cfg) {
        if (cfg == null) return false;
        String type = cfg.getString("type", "ARMOR");
        return matches(player.getInventory().getHelmet(), type, cfg.getString("helmet"))
                && matches(player.getInventory().getChestplate(), type, cfg.getString("chestplate"))
                && matches(player.getInventory().getLeggings(), type, cfg.getString("leggings"))
                && matches(player.getInventory().getBoots(), type, cfg.getString("boots"));
    }
}
