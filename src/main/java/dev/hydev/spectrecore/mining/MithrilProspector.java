package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Gameplay;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
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

/** Bounded connected-vein search and particle highlighting for Mithril picks. */
public final class MithrilProspector {
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private final JavaPlugin plugin;
    private final PlacedOres placed;
    private final MithrilRegionAccess regions;
    private final Map<UUID, BukkitTask> highlights = new HashMap<>();


    public MithrilProspector(JavaPlugin plugin, PlacedOres placed, MithrilRegionAccess regions) {
        this.plugin = plugin; this.placed = placed; this.regions = regions;
    }

    public List<Block> connected(Player player, Block origin, int radius, int maximum) {
        List<Block> found = new ArrayList<>();
        if (origin == null || Gameplay.tier("mineria", origin.getType().name()) == 0
                || placed.isMarked(origin) || !regions.canBreak(player, origin)) return found;
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
                    || placed.isMarked(block) || !regions.canBreak(player, block)) continue;
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

    private void cancel(Map<UUID, BukkitTask> tasks, UUID id) {
        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();
    }

    public void quit(UUID player) { cancel(highlights, player); }
    public void disable() {
        highlights.values().forEach(BukkitTask::cancel);
        highlights.clear();
    }
}
