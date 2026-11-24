package dev.hydev.spectrecore.mining;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * MythicMobs Free public Bukkit helper. No premium mechanics, commands or fake
 * entities. A missing/misconfigured mob fails closed and warns administrators.
 */
public final class MythicMiningSpawns {
    private final JavaPlugin plugin;
    private final Set<String> warned = new HashSet<>();
    public MythicMiningSpawns(JavaPlugin plugin) { this.plugin = plugin; }

    public Entity spawn(String id, Location location) {
        Plugin mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled() || id == null || !id.matches("[A-Za-z0-9_]{2,64}")) {
            warn(id, "MythicMobs o identificación del encuentro no disponible");
            return null;
        }
        try {
            Class<?> type = Class.forName("io.lumine.mythic.bukkit.MythicBukkit",
                    true, mythic.getClass().getClassLoader());
            Object instance = type.getMethod("inst").invoke(null);
            Object helper = type.getMethod("getAPIHelper").invoke(instance);
            Object registered = helper.getClass().getMethod("getMythicMob", String.class).invoke(helper,id);
            if (registered == null) {
                warn(id, "criatura minera no registrada en MythicMobs");
                return null;
            }
            Method spawn = helper.getClass().getMethod("spawnMythicMob", String.class, Location.class);
            Object result = spawn.invoke(helper,id,location);
            return result instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(id, "falló el adaptador público: "+error.getClass().getSimpleName());
            return null;
        }
    }

    private void warn(String id, String cause) {
        if (warned.add(String.valueOf(id)))
            plugin.getLogger().warning("Encuentro minero "+id+": "+cause);
    }
}
