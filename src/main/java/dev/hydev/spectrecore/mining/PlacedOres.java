package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.migration.MigrationRules;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Arrays;

/** Chunk PDC survives normal saves/restarts. Never loads a chunk to edit its markers. */
public final class PlacedOres implements Listener {
    private final NamespacedKey key;
    private final java.util.function.Predicate<org.bukkit.Material> tracked;
    public PlacedOres(JavaPlugin plugin) { this(plugin, "placed_ores_v1", material -> MiningModule.tier(material) > 0); }
    public PlacedOres(JavaPlugin plugin, String name, java.util.function.Predicate<org.bukkit.Material> tracked) {
        key = new NamespacedKey(plugin, name); this.tracked = tracked;
    }
    private long[] markers(Block b) {
        long[] raw = b.getChunk().getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        return raw == null ? new long[0] : raw;
    }
    private long pos(Block b) { return MigrationRules.position(b.getX(), b.getY(), b.getZ()); }
    public boolean isMarked(Block block) { return Arrays.binarySearch(markers(block), pos(block)) >= 0; }
    private void save(Block b, long[] values) {
        var pdc = b.getChunk().getPersistentDataContainer();
        if (values.length == 0) pdc.remove(key);
        else pdc.set(key, PersistentDataType.LONG_ARRAY, values);
    }
    public boolean consume(Block b) {
        long[] values = markers(b); int index = Arrays.binarySearch(values, pos(b));
        if (index < 0) return false;
        long[] next = new long[values.length - 1];
        System.arraycopy(values, 0, next, 0, index);
        System.arraycopy(values, index + 1, next, index, values.length - index - 1);
        save(b, next); return true;
    }
    private void mark(Block b) {
        long[] values = markers(b); int index = Arrays.binarySearch(values, pos(b));
        if (index >= 0) return;
        index = -index - 1; long[] next = new long[values.length + 1];
        System.arraycopy(values, 0, next, 0, index); next[index] = pos(b);
        System.arraycopy(values, index, next, index + 1, values.length - index);
        save(b, next);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e instanceof BlockMultiPlaceEvent multi) {
            multi.getReplacedBlockStates().forEach(state -> placed(state.getBlock()));
        } else placed(e.getBlockPlaced());
    }
    private void placed(Block block) { consume(block); if (tracked.test(block.getType())) mark(block); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent e) { moved(e.getBlocks(), e.getDirection()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) {
        if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional piston)
            moved(e.getBlocks(), piston.getFacing().getOppositeFace());
    }
    private void moved(java.util.List<Block> blocks, org.bukkit.block.BlockFace direction) {
        // Mark every moved ore, including a naturally generated one: moving never resets provenance.
        var ores = blocks.stream().filter(b -> tracked.test(b.getType())).toList();
        blocks.forEach(this::consume);
        ores.forEach(b -> mark(b.getRelative(direction)));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent e) { e.blockList().forEach(this::consume); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent e) { e.blockList().forEach(this::consume); }
}
