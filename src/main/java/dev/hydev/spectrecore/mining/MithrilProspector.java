package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Gameplay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** On-demand bounded scans: no global chunk loads or continuous searches. */
public final class MithrilProspector {
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private final JavaPlugin plugin;
    private final PlacedOres placed;
    private final MithrilRegionAccess regions;
    private final Map<UUID, BukkitTask> scans = new HashMap<>();
    private final Map<UUID, BukkitTask> highlights = new HashMap<>();

    public record Signal(double distance, int dx, int dy, int dz) { }

    public MithrilProspector(JavaPlugin plugin, PlacedOres placed, MithrilRegionAccess regions) {
        this.plugin = plugin; this.placed = placed; this.regions = regions;
    }

    public List<Block> connected(Player player, Block origin, int radius, int maximum) {
        List<Block> found = new ArrayList<>();
        if (origin == null || Gameplay.tier("mineria", origin.getType().name()) == 0
                || placed.isMarked(origin) || !regions.canBuild(player, origin)) return found;
        ArrayDeque<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < maximum && visited.size() < 180) {
            Block block = queue.removeFirst();
            String key = block.getX() + ":" + block.getY() + ":" + block.getZ();
            if (!visited.add(key)) continue;
            if (block.getWorld() != origin.getWorld()
                    || Math.abs(block.getX() - origin.getX()) > radius
                    || Math.abs(block.getY() - origin.getY()) > radius
                    || Math.abs(block.getZ() - origin.getZ()) > radius
                    || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                    || !MithrilRules.sameOre(origin.getType().name(), block.getType().name())
                    || placed.isMarked(block) || !regions.canBuild(player, block)) continue;
            found.add(block);
            for (BlockFace face : FACES) queue.add(block.getRelative(face));
        }
        return found;
    }

    public void highlight(Player player, List<Block> vein, int seconds) {
        BukkitTask previous = highlights.remove(player.getUniqueId());
        if (previous != null) previous.cancel();
        if (vein.isEmpty()) return;
        final int[] left = {Math.min(5, seconds)};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || --left[0] < 0) {
                cancel(highlights, player.getUniqueId()); return;
            }
            for (Block block : vein) if (block.getWorld() == player.getWorld()
                    && block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                player.spawnParticle(Particle.END_ROD, block.getLocation().add(.5,.5,.5), 4, .2,.2,.2, .015);
        }, 0, 20);
        highlights.put(player.getUniqueId(), task);
    }

    public void scan(Player player, int radius, int vertical, Consumer<Signal> completed) {
        cancel(scans, player.getUniqueId());
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int diameter = 2 * radius + 1, height = 2 * vertical + 1;
        int total = diameter * diameter * height;
        final int[] index = {0};
        final double[] best = {Double.POSITIVE_INFINITY};
        final int[] bestOffset = {0,0,0};
        UUID id = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getWorld() != world) { cancel(scans, id); return; }
            for (int processed = 0; processed < 500 && index[0] < total; processed++, index[0]++) {
                int n = index[0], dx = n % diameter - radius;
                int dz = (n / diameter) % diameter - radius;
                int dy = n / (diameter * diameter) - vertical;
                if (!MithrilRules.within(dx, dy, dz, radius, vertical)) continue;
                int x = cx + dx, y = cy + dy, z = cz + dz;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()
                        || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block block = world.getBlockAt(x,y,z);
                if (Gameplay.tier("mineria", block.getType().name()) == 0 || placed.isMarked(block)) continue;
                double dist2 = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                if (dist2 >= best[0] || !regions.canBuild(player, block)) continue;
                best[0] = dist2;
                bestOffset[0] = dx; bestOffset[1] = dy; bestOffset[2] = dz;
            }
            if (index[0] >= total) {
                cancel(scans, id);
                completed.accept(best[0] == Double.POSITIVE_INFINITY
                        ? null : new Signal(Math.sqrt(best[0]), bestOffset[0], bestOffset[1], bestOffset[2]));
            }
        }, 1, 1);
        scans.put(id, task);
    }

    private void cancel(Map<UUID, BukkitTask> tasks, UUID id) {
        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();
    }

    public void quit(UUID player) { cancel(scans, player); cancel(highlights, player); }
    public void disable() {
        scans.values().forEach(BukkitTask::cancel);
        highlights.values().forEach(BukkitTask::cancel);
        scans.clear(); highlights.clear();
    }
}
