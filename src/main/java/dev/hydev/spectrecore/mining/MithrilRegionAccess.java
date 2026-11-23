package dev.hydev.spectrecore.mining;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * WorldGuard's documented public RegionQuery.testBuild adapter, resolved without
 * a hard runtime dependency. If unavailable, automatic excavation and scans
 * fail closed rather than bypass protections.
 */
public final class MithrilRegionAccess {
    private final JavaPlugin plugin;
    private boolean warned;
    private Method instance, platform, container, createQuery, testBuild, wrapPlayer, adapt;
    private Object worldGuardPlugin;
    private Object zeroFlags;
    private boolean resolved;

    public MithrilRegionAccess(JavaPlugin plugin) { this.plugin = plugin; }

    private boolean initialize() {
        if (resolved) return true;
        Plugin guard = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (guard == null || !guard.isEnabled()) return false;
        try {
            ClassLoader loader = guard.getClass().getClassLoader();
            Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Class<?> platformType = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform", true, loader);
            Class<?> regionContainer = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer", true, loader);
            Class<?> regionQuery = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery", true, loader);
            Class<?> localPlayer = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
            Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
            Class<?> wgBukkit = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin", true, loader);
            instance = wg.getMethod("getInstance");
            platform = wg.getMethod("getPlatform");
            container = platformType.getMethod("getRegionContainer");
            createQuery = regionContainer.getMethod("createQuery");
            testBuild = regionQuery.getMethod("testBuild", weLocation, localPlayer,
                    Array.newInstance(stateFlag, 0).getClass());
            wrapPlayer = wgBukkit.getMethod("wrapPlayer", Player.class);
            adapt = adapter.getMethod("adapt", org.bukkit.Location.class);
            worldGuardPlugin = wgBukkit.getMethod("inst").invoke(null);
            zeroFlags = Array.newInstance(stateFlag, 0);
            resolved = true;
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Mitrilo: WorldGuard no ofrece la API de protección esperada; se bloqueará la minería automática.");
            }
            return false;
        }
    }

    public boolean canBuild(Player player, Block block) {
        if (!initialize() || player == null || block == null
                || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
        try {
            Object wg = instance.invoke(null);
            Object platformValue = platform.invoke(wg);
            Object query = createQuery.invoke(container.invoke(platformValue));
            Object location = adapt.invoke(null, block.getLocation());
            Object actor = wrapPlayer.invoke(worldGuardPlugin, player);
            return Boolean.TRUE.equals(testBuild.invoke(query, location, actor, zeroFlags));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Mitrilo: error al consultar la protección de regiones; se niega la habilidad.");
            }
            return false;
        }
    }
}
