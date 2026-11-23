package dev.hydev.spectrecore.mining;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Portable lantern: basic light, per-item anchor and a short-lived breadcrumb trail. */
public final class MithrilLantern {
    private final JavaPlugin plugin;
    private final MithrilDeviceData data;
    private final Map<UUID,BukkitTask> routes = new HashMap<>();
    private BukkitTask lightTask;

    public MithrilLantern(JavaPlugin plugin, MithrilDeviceData data) {
        this.plugin = plugin; this.data = data;
    }

    public void enable() {
        // Ten-second cadence, not a per-tick scan. Long enough to avoid Night Vision flicker.
        lightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers())
                for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
                    ItemStack stack = data.held(player, hand);
                    if (!MithrilDeviceData.LANTERN.equals(data.id(stack)) || !data.lit(stack)) continue;
                    light(player);
                    break;
                }
        }, 20, 200);
    }

    public void light(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && (current.getAmplifier() > 0 || current.getDuration() > 500)) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 500, 0, true, false, false));
    }

    public boolean anchor(Player player, EquipmentSlot hand, int cost, int capacity) {
        ItemStack stack = data.held(player, hand);
        if (!MithrilDeviceData.LANTERN.equals(data.id(stack)) || data.energy(stack) < cost) return false;
        Location at = player.getLocation();
        String start = at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
        stack = data.changeCharge(stack, -cost, capacity);
        stack = data.anchor(stack, player.getWorld().getUID() + ":" + start);
        stack = data.path(stack, start);
        stack = data.lit(stack, true);
        data.update(player, hand, stack);
        light(player);
        return true;
    }

    public boolean route(Player player, EquipmentSlot hand, int cost, int capacity, long cooldownMs,
                         int seconds) {
        ItemStack stack = data.held(player, hand);
        if (!MithrilDeviceData.LANTERN.equals(data.id(stack)) || data.anchor(stack).isBlank()
                || data.energy(stack) < cost || data.cooldown(stack) > System.currentTimeMillis()) return false;
        String prefix = player.getWorld().getUID() + ":";
        if (!data.anchor(stack).startsWith(prefix)) return false;
        List<Location> stops = parse(player.getWorld(), data.path(stack));
        if (stops.isEmpty()) return false;
        stack = data.cooldown(data.changeCharge(stack, -cost, capacity),
                System.currentTimeMillis() + cooldownMs);
        data.update(player, hand, stack);
        cancel(player.getUniqueId());
        final UUID id = player.getUniqueId();
        final World world = player.getWorld();
        final int[] remaining = {Math.min(seconds,20)};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getWorld() != world || remaining[0]-- <= 0) {
                cancel(id); return;
            }
            int stride = Math.max(1, (stops.size() + 15) / 16);
            for (int i = 0; i < stops.size(); i += stride) {
                Location where = stops.get(i);
                if (!world.isChunkLoaded(where.getBlockX() >> 4, where.getBlockZ() >> 4)) continue;
                player.spawnParticle(Particle.END_ROD, where.clone().add(.5,.6,.5),
                        2, .08,.1,.08, .01);
            }
        }, 0, 20);
        routes.put(id, task);
        return true;
    }

    public void moved(Player player, Location from, Location to, int maximumDistance) {
        if (to == null || from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            ItemStack stack = data.held(player, hand);
            if (stack.getType() != org.bukkit.Material.SOUL_LANTERN
                    || !MithrilDeviceData.LANTERN.equals(data.id(stack)) || !data.lit(stack)) continue;
            String origin = data.anchor(stack);
            if (origin.isBlank()) continue;
            if (!origin.startsWith(player.getWorld().getUID() + ":")) {
                clear(player, hand, stack); continue;
            }
            List<Location> stops = parse(player.getWorld(), data.path(stack));
            if (stops.isEmpty()) continue;
            Location last = stops.getLast();
            if (last.distanceSquared(to) < 16 || stops.size() >= 64) continue;
            String rawAnchor = origin.substring(origin.indexOf(':') + 1);
            List<Location> beginnings = parse(player.getWorld(), rawAnchor);
            if (beginnings.isEmpty() || beginnings.getFirst().distanceSquared(to)
                    > maximumDistance * maximumDistance) continue;
            String point = to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ();
            String path = data.path(stack) + ";" + point;
            if (path.length() <= 4096) data.update(player, hand, data.path(stack, path));
        }
    }

    private List<Location> parse(World world, String path) {
        List<Location> list = new ArrayList<>();
        if (path == null || path.isBlank() || path.length() > 4096) return list;
        for (String tuple : path.split(";")) {
            String[] parts = tuple.split(",");
            if (parts.length != 3) return List.of();
            try {
                int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]),
                        z = Integer.parseInt(parts[2]);
                if (y < world.getMinHeight() || y >= world.getMaxHeight()) return List.of();
                list.add(new Location(world, x, y, z));
            } catch (NumberFormatException invalid) { return List.of(); }
            if (list.size() > 64) return List.of();
        }
        return list;
    }

    private void clear(Player player, EquipmentSlot hand, ItemStack stack) {
        data.update(player, hand, data.path(data.anchor(stack, ""), ""));
    }

    public void teleported(Player player) {
        cancel(player.getUniqueId());
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            ItemStack stack = data.held(player, hand);
            if (MithrilDeviceData.LANTERN.equals(data.id(stack))) clear(player, hand, stack);
        }
    }

    public void cancel(UUID id) {
        BukkitTask task = routes.remove(id);
        if (task != null) task.cancel();
    }

    public void disable() {
        if (lightTask != null) lightTask.cancel();
        routes.values().forEach(BukkitTask::cancel);
        routes.clear();
    }
}
