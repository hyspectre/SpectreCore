package dev.hydev.spectrecore.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Optional WorldGuard 7 BUILD query without requiring WorldGuard classes at startup.
 * Fails closed on mismatched APIs; never grants access because a hook is broken.
 * Adaptation tested against public WorldGuard RegionQuery.testBuild contract.
 */
public final class WorldGuardBridge {
    private final Logger logger;
    private boolean warned;
    public WorldGuardBridge(Logger logger) { this.logger = logger; }
    public boolean canBuild(Player player, Location location) {
        Plugin guard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (guard == null || !guard.isEnabled()) return true;
        if (location == null || location.getWorld() == null) return false;
        try {
            ClassLoader loader = guard.getClass().getClassLoader();
            Class<?> pluginType = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin", true, loader);
            Object wgPlugin = pluginType.getMethod("inst").invoke(null);
            Object localPlayer = pluginType.getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
            Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
            Class<?> wgType = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object wg = wgType.getMethod("getInstance").invoke(null);
            Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
            for (Method method : query.getClass().getMethods()) {
                if (!method.getName().equals("testBuild") || method.getParameterCount() != 3) continue;
                Class<?>[] types = method.getParameterTypes();
                if (!types[0].isInstance(adapted) || !types[1].isInstance(localPlayer)
                        || !types[2].isArray()) continue;
                Object flags = Array.newInstance(types[2].getComponentType(), 0);
                return Boolean.TRUE.equals(method.invoke(query, adapted, localPlayer, flags));
            }
            throw new NoSuchMethodException("RegionQuery.testBuild(Location, LocalPlayer, StateFlag...)");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (!warned) {
                warned = true;
                logger.severe("WorldGuard no pudo comprobar BUILD; creación/retiro de estaciones BLOQUEADOS: " + error);
            }
            return false;
        }
    }
}
