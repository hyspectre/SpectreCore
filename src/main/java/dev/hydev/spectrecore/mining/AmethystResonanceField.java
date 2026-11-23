package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mineable enchanted-looking blocks made of BlockDisplay + Interaction entities.
 * Never modifies real blocks. Each node can award exactly one configured MMOItems
 * GEM_STONE and is removed before delivery; only its summoning player can mine it.
 */
public final class AmethystResonanceField implements Listener {
    public record Plan(List<Location> positions, List<ItemStack> rewards) { }

    private static final int[][] OFFSETS = {
            {2,0}, {0,2}, {-2,0}, {0,-2}, {2,2}, {-2,2}, {-2,-2}, {2,-2},
            {3,0}, {0,3}, {-3,0}, {0,-3}, {3,1}, {-3,1}, {1,3}, {1,-3}
    };

    private static final class Node {
        final Session session;
        final BlockDisplay visual;
        final Interaction hitbox;
        int strikes;
        long lastStrike;
        boolean claimed;
        Node(Session session, BlockDisplay visual, Interaction hitbox) {
            this.session = session; this.visual = visual; this.hitbox = hitbox;
        }
    }

    private static final class Session {
        final UUID owner;
        final String toolSerial;
        final World world;
        final List<Node> nodes = new ArrayList<>();
        final List<ItemStack> rewards;
        BukkitTask timeout;
        Session(Player player, String serial, List<ItemStack> rewards) {
            owner = player.getUniqueId(); toolSerial = serial; world = player.getWorld();
            this.rewards = rewards;
        }
    }

    private final JavaPlugin plugin;
    private final MithrilDeviceData devices;
    private final MithrilRegionAccess regions;
    private final MmoBridge items = new MmoBridge();
    private final NamespacedKey marker;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Node> hitboxes = new HashMap<>();
    private org.bukkit.configuration.file.YamlConfiguration config;
    private boolean registered;
    private boolean warnedMissingRewards;

    public AmethystResonanceField(JavaPlugin plugin, MithrilDeviceData devices,
                                  MithrilRegionAccess regions) {
        this.plugin = plugin;
        this.devices = devices;
        this.regions = regions;
        marker = new NamespacedKey(plugin, "amatista_nodo_v1");
    }

    public void configure(org.bukkit.configuration.file.YamlConfiguration config) {
        this.config = config;
    }

    private int amount(String name) {
        return config.getInt("herramientas-mitrilo.pico-amatista." + name);
    }

    private String gemId(String quality) {
        return config.getString("herramientas-mitrilo.pico-amatista.gemas." + quality, "");
    }

    public int activeFields() { return sessions.size(); }

    public void enable() {
        if (registered) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds())
            for (Chunk chunk : world.getLoadedChunks()) cleanupOrphans(chunk);
        registered = true;
    }

    /** Validates both the available nodes and every possible reward BEFORE charge. */
    public Plan prepare(Player player) {
        if (!player.isOnline() || sessions.containsKey(player.getUniqueId())
                || sessions.size() >= amount("max-sesiones")
                || !regions.canBuild(player, player.getLocation().getBlock())) return null;
        List<ItemStack> gemItems = new ArrayList<>();
        for (String quality : List.of("basica", "intermedia", "superior")) {
            String id = gemId(quality);
            ItemStack item = id.isBlank() ? null : items.build("GEM_STONE", id, 1);
            if (item == null) {
                if (!warnedMissingRewards) {
                    plugin.getLogger().warning("Pico de Amatista: falta una gema GEM_STONE configurada "
                            + "o la integración MMOItems; no se descontará resonancia.");
                    warnedMissingRewards = true;
                }
                return null;
            }
            gemItems.add(item);
        }
        World world = player.getWorld();
        int x = player.getLocation().getBlockX(), y = player.getLocation().getBlockY(),
                z = player.getLocation().getBlockZ();
        List<Location> available = new ArrayList<>();
        for (int[] pair : OFFSETS) {
            if (available.size() >= amount("bloques")) break;
            int bx = x + pair[0], bz = z + pair[1];
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)
                    || y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            Block at = world.getBlockAt(bx, y, bz);
            if (!at.getType().isAir() || !at.getRelative(0, 1, 0).getType().isAir()
                    || !at.getRelative(0, -1, 0).getType().isSolid()
                    || !regions.canBuild(player, at)) continue;
            available.add(new Location(world, bx, y, bz));
        }
        return available.size() < amount("minimo-bloques") ? null : new Plan(List.copyOf(available), List.copyOf(gemItems));
    }

    /** No reward is scheduled or handed out before a player actually breaks a node. */
    public int summon(Player owner, String serial, Plan plan) {
        if (plan == null || serial == null || serial.isBlank()
                || sessions.containsKey(owner.getUniqueId()) || sessions.size() >= amount("max-sesiones"))
            return 0;
        Session session = new Session(owner, serial, plan.rewards);
        try {
            for (Location spot : plan.positions) {
                if (!owner.isOnline() || owner.getWorld() != spot.getWorld()
                        || !spot.getWorld().isChunkLoaded(spot.getBlockX() >> 4, spot.getBlockZ() >> 4)
                        || !spot.getBlock().getType().isAir()
                        || !regions.canBuild(owner, spot.getBlock())) continue;
                BlockDisplay visual = spot.getWorld().spawn(spot, BlockDisplay.class, display -> {
                    display.setBlock(Material.AMETHYST_BLOCK.createBlockData());
                    display.setPersistent(false);
                    display.setInvulnerable(true);
                    display.setGravity(false);
                    display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                    display.setViewRange(0.8f);
                    display.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
                });
                Interaction hitbox;
                try {
                    hitbox = spot.getWorld().spawn(spot.clone().add(.5, 0, .5), Interaction.class, entity -> {
                        entity.setInteractionWidth(.92f);
                        entity.setInteractionHeight(1f);
                        entity.setResponsive(true);
                        entity.setPersistent(false);
                        entity.setInvulnerable(true);
                        entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
                    });
                } catch (RuntimeException failure) {
                    visual.remove();
                    throw failure;
                }
                Node node = new Node(session, visual, hitbox);
                session.nodes.add(node);
                hitboxes.put(hitbox.getUniqueId(), node);
                spot.getWorld().spawnParticle(Particle.END_ROD, spot.clone().add(.5, .6, .5),
                        7, .24, .25, .24, .015);
            }
            if (session.nodes.size() < amount("minimo-bloques")) {
                remove(session);
                return 0;
            }
            sessions.put(owner.getUniqueId(), session);
            session.timeout = Bukkit.getScheduler().runTaskLater(plugin, () ->
                    close(owner.getUniqueId()), 20L * amount("duracion-segundos"));
            owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, .85f, 1.15f);
            return session.nodes.size();
        } catch (RuntimeException failure) {
            remove(session);
            plugin.getLogger().warning("Pico de Amatista: no se pudo invocar el campo: "
                    + failure.getClass().getSimpleName());
            return 0;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void mine(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction target)) return;
        Node node = hitboxes.get(target.getUniqueId());
        if (node == null) return;
        event.setCancelled(true); // Interaction entities are hitboxes, not damageable mobs.
        Player player = event.getPlayer();
        if (!node.session.owner.equals(player.getUniqueId()) || node.claimed
                || !player.getWorld().equals(node.session.world) || !node.hitbox.isValid()
                || player.getLocation().distanceSquared(target.getLocation()) > 36) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!MithrilDeviceData.AMETHYST.equals(devices.id(held))
                || !node.session.toolSerial.equals(devices.serial(held))
                || !regions.canBuild(player, target.getLocation().getBlock())) return;
        long now = System.currentTimeMillis();
        if (!AmethystResonanceRules.hit(now, node.lastStrike,
                amount("intervalo-golpe-ms"), amount("golpes") - node.strikes)) return;
        node.lastStrike = now;
        node.strikes++;
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, .6f, .8f + node.strikes * .12f);
        target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, .5, 0),
                8, .25, .25, .25, .03);
        if (node.strikes < amount("golpes")) return;

        // One-shot claim is made before the item enters any inventory.
        node.claimed = true;
        node.session.nodes.remove(node);
        hitboxes.remove(target.getUniqueId());
        node.hitbox.remove();
        node.visual.remove();

        String[] grades = {"basica", "intermedia", "superior"};
        int index = AmethystResonanceRules.quality(ThreadLocalRandom.current().nextDouble(100),
                amount("calidad.basica"), amount("calidad.intermedia"), amount("calidad.superior"));
        ItemStack reward = node.session.rewards.get(index).clone();
        var excess = player.getInventory().addItem(reward);
        for (ItemStack item : excess.values()) {
            player.getWorld().dropItem(player.getLocation(), item, drop -> {
                drop.setPickupDelay(20);
                drop.setOwner(player.getUniqueId());
            });
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, .65f, 1.25f);
        player.sendMessage(Settings.message("tesoros", "AmethystField.gema",
                "<light_purple>✦ Cristal roto: <white>Gema {calidad}.",
                "calidad", grades[index]));
        if (node.session.nodes.isEmpty()) close(node.session.owner);
    }

    private void remove(Session session) {
        if (session.timeout != null) session.timeout.cancel();
        for (Node node : new ArrayList<>(session.nodes)) {
            hitboxes.remove(node.hitbox.getUniqueId());
            node.visual.remove();
            node.hitbox.remove();
        }
        session.nodes.clear();
    }

    public void close(UUID owner) {
        Session session = sessions.remove(owner);
        if (session != null) remove(session);
    }

    @EventHandler public void quit(PlayerQuitEvent event) { close(event.getPlayer().getUniqueId()); }
    @EventHandler public void teleport(PlayerTeleportEvent event) { close(event.getPlayer().getUniqueId()); }

    /** Remove stranded nonpersistent entities after restart/chunk reloading. */
    @EventHandler public void chunk(ChunkLoadEvent event) { cleanupOrphans(event.getChunk()); }
    private void cleanupOrphans(Chunk chunk) {
        for (Entity entity : chunk.getEntities())
            if (entity.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)
                    && !hitboxes.containsKey(entity.getUniqueId()))
                entity.remove();
    }

    public void disable() {
        for (UUID owner : new ArrayList<>(sessions.keySet())) close(owner);
        if (registered) HandlerList.unregisterAll(this);
        registered = false;
    }
}
