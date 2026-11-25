package dev.hydev.spectrecore.mining;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded synchronous tunnel. Never touches protected blocks, ores or block entities. */
public final class MithrilDrill {
    private final JavaPlugin plugin;
    private final MithrilDeviceData data;
    private final MithrilRegionAccess regions;
    private final Map<UUID, BukkitTask> running = new HashMap<>();

    public MithrilDrill(JavaPlugin plugin, MithrilDeviceData data, MithrilRegionAccess regions) {
        this.plugin = plugin; this.data = data; this.regions = regions;
    }

    public boolean running(Player player) { return running.containsKey(player.getUniqueId()); }

    /** Returns false when a tool is destroyed and should not continue. */
    public static boolean wear(Player player, MithrilDeviceData data, int amount) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (data.id(stack).isEmpty()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        if (meta.isUnbreakable()) return true;
        int maximum = stack.getType().getMaxDurability();
        if (maximum <= 0) return false;
        int next = damageable.getDamage() + amount;
        if (next >= maximum) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return false;
        }
        damageable.setDamage(next);
        stack.setItemMeta(meta);
        player.getInventory().setItemInMainHand(stack);
        return true;
    }

    public boolean start(Player player, int steps, int intervalTicks, List<String> materials) {
        if ((running.size() >= 8 && !running(player)) || running(player) || steps < 1 || steps > 8 || intervalTicks < 1 || intervalTicks > 40
                || materials == null || materials.isEmpty()) return false;
        final ItemStack held = player.getInventory().getItemInMainHand();
        if (!MithrilDeviceData.DRILL.equals(data.id(held)) || data.serial(held).isEmpty()) return false;
        final String serial = data.serial(held);
        final Location origin = player.getLocation().clone();
        final World world = origin.getWorld();
        final int x = origin.getBlockX(), y = origin.getBlockY(), z = origin.getBlockZ();
        if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) return false;
        final BlockFace face = cardinal(origin);
        // Preflight the first tunnel slice before the caller spends resonance.
        int fx = x + face.getModX(), fz = z + face.getModZ();
        if (!world.isChunkLoaded(fx >> 4, fz >> 4)) return false;
        Block firstFeet = world.getBlockAt(fx, y, fz);
        Block firstHead = world.getBlockAt(fx, y + 1, fz);
        if (!allowed(firstFeet, materials) || !allowed(firstHead, materials)
                || !regions.canBreak(player, firstFeet) || !regions.canBreak(player, firstHead))
            return false;
        final int[] progress = {0};
        final UUID id = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ItemStack current = player.getInventory().getItemInMainHand();
            if (!plugin.isEnabled() || !player.isOnline() || player.getWorld() != world
                    || !serial.equals(data.serial(current)) || !MithrilDeviceData.DRILL.equals(data.id(current))
                    || player.getLocation().distanceSquared(origin) > 36 || progress[0] >= steps * 2) {
                cancel(id); return;
            }
            // One physical block per 5 ticks (0.25 s), up to 8 x 2 blocks.
            int index = progress[0]++, delta = index / 2 + 1;
            int bx = x + face.getModX() * delta, bz = z + face.getModZ() * delta;
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) { cancel(id); return; }
            Block feet = world.getBlockAt(bx, y, bz), head = world.getBlockAt(bx, y + 1, bz);
            // Check both cells before drilling either to avoid half-entering restricted terrain.
            if (!regions.canBreak(player, feet) || !regions.canBreak(player, head)
                    || !allowed(feet, materials) || !allowed(head, materials)) {
                cancel(id); return;
            }
            Block target = index % 2 == 0 ? feet : head;
            if (!target.getType().isAir()) {
                current = player.getInventory().getItemInMainHand();
                ItemStack breaker = current;
                if (!MithrilAutomationGuard.breakBlock(() -> target.breakNaturally(breaker, true))) {
                    cancel(id); return;
                }
                if (!wear(player, data, 1)) { cancel(id); return; }
            }
            if (progress[0] >= steps * 2) cancel(id);
        }, 0, intervalTicks);
        running.put(id, task);
        return true;
    }

    private static boolean allowed(Block block, List<String> materials) {
        Material type = block.getType();
        if (type.isAir()) return true;
        // Exact whitelist means ores, chests, water, lava, bedrock and tiles all stop the tool.
        return MithrilRules.drillable(type.name(), materials) && !block.getState().getClass().getName().contains("Container");
    }

    private static BlockFace cardinal(Location location) {
        var direction = location.getDirection();
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ()))
            return direction.getX() >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return direction.getZ() >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    public void cancel(UUID id) {
        BukkitTask task = running.remove(id);
        if (task != null) task.cancel();
    }

    public void disable() {
        running.values().forEach(BukkitTask::cancel);
        running.clear();
    }
}
