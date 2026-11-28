package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.hydev.spectrecore.relicario.RelicarioModule.miningBonus;

/** Renewable night vision and private, temporary outlines of nearby natural ores. */
public final class MithrilLantern {
    private static final String ROOT = "herramientas-mitrilo.linterna.";
    private final JavaPlugin plugin;
    private final PlacedOres placed;
    private final MithrilRegionAccess regions;
    private final MiningProgression progression;
    private final NamespacedKey cooldown;
    private final Map<UUID, Pulse> pulses = new HashMap<>();
    private YamlConfiguration settings;
    private record Pulse(List<BlockDisplay> displays, BukkitTask expiry) { }
    private record Candidate(Block block, int distanceSquared) { }

    public MithrilLantern(JavaPlugin plugin, PlacedOres placed, MithrilRegionAccess regions) {
        this.plugin = plugin;
        this.placed = placed;
        this.regions = regions;
        progression = new MiningProgression(plugin);
        cooldown = new NamespacedKey(plugin, "linterna_pulso_hasta");
    }

    public void configure(YamlConfiguration settings) {
        disable();
        this.settings = settings;
    }

    private int config(String key) { return settings.getInt(ROOT + key); }

    public long remaining(Player player, long now) {
        return MithrilRules.remainingSeconds(now, player.getPersistentDataContainer()
                .getOrDefault(cooldown, PersistentDataType.LONG, 0L));
    }

    public float progress(Player player, long now) {
        return Math.clamp(1f - remaining(player, now) / (float) config("recarga-segundos"), 0f, 1f);
    }

    public void light(Player player) {
        if (!progression.meets(player,"ingenieria")) return;
        int ticks = config("vision-segundos") * 20;
        PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
        if (current != null && (current.isInfinite() || current.getAmplifier() > 0
                || current.getDuration() >= ticks)) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, true, false, false));
        player.sendMessage(Settings.message("tesoros", "MithrilTools.linterna-luz",
                "<aqua>✦ Visión nocturna <gray>· <white>{s}s", "s", config("vision-segundos")));
    }

    public void reveal(Player player) {
        if (!progression.meets(player,"ingenieria")) return;
        long now = System.currentTimeMillis();
        long wait = remaining(player, now);
        if (wait > 0) {
            player.sendMessage(Settings.message("tesoros", "MithrilTools.en-recarga",
                    "<yellow>Recarga: <white>{s}s", "s", wait));
            return;
        }
        UUID id = player.getUniqueId();
        if (pulses.containsKey(id) || pulses.size() >= config("max-pulsos")) {
            player.sendMessage(Settings.message("tesoros", "MithrilTools.linterna-ocupada",
                    "<yellow>Espera un momento para usar el pulso."));
            return;
        }
        if (!regions.canBuild(player, player.getLocation().getBlock())) {
            player.sendMessage(Settings.message("tesoros", "MithrilTools.zona",
                    "<red>La herramienta no puede utilizarse en esta zona."));
            return;
        }
        // The old compass relic now extends range, never the three-second glow.
        int radius = Math.min(16, config("radio") + (int) Math.max(0, miningBonus(plugin, player, "lantern_radius")));
        // An empty search is still a pulse: do not allow continuous scans of empty terrain.
        player.getPersistentDataContainer().set(cooldown, PersistentDataType.LONG,
                now + config("recarga-segundos") * 1000L);
        List<BlockDisplay> displays = new ArrayList<>();
        try {
            for (Block block : nearby(player, radius)) {
                if (displays.size() >= config("max-menas")) break;
                BlockDisplay display = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, e -> {
                    if (plugin instanceof SpectreCorePlugin core) core.visuals().mark(e);
                    e.setPersistent(false);
                    e.setInvulnerable(true);
                    e.setGravity(false);
                    e.setVisibleByDefault(false);
                    e.setBlock(block.getBlockData());
                    e.setBrightness(new Display.Brightness(15, 15));
                    e.setViewRange(1f);
                    e.setGlowing(true);
                });
                displays.add(display);
                player.showEntity(plugin, display);
            }
            if (displays.isEmpty()) {
                player.sendMessage(Settings.message("tesoros", "MithrilTools.linterna-sin-menas",
                        "<gray>El pulso no detectó minerales accesibles."));
                return;
            }
            BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> cancel(id),
                    config("resaltado-segundos") * 20L);
            pulses.put(id, new Pulse(displays, expiry));
            player.sendMessage(Settings.message("tesoros", "MithrilTools.linterna-pulso",
                    "<aqua>✦ {cantidad} menas reveladas", "cantidad", displays.size()));
        } catch (RuntimeException failure) {
            cancel(id);
            displays.forEach(BlockDisplay::remove);
            player.getPersistentDataContainer().remove(cooldown);
            plugin.getLogger().warning("Linterna: no se pudo mostrar el pulso: " + failure.getMessage());
            player.sendMessage(Settings.message("tesoros", "MithrilTools.uso-no-disponible",
                    "<yellow>Esta habilidad no está disponible."));
        }
    }

    private List<Block> nearby(Player player, int radius) {
        World world = player.getWorld();
        var center = player.getLocation();
        // Read configured ore classification once, not 3 YAML lists per block.
        Set<String> ores = new HashSet<>();
        var treasure = Settings.module("tesoros/config.yml");
        for (int tier = 1; tier <= 3; tier++)
            ores.addAll(treasure.getStringList("clasificacion.mineria.tier" + tier));
        List<Candidate> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int x = center.getBlockX() + dx, z = center.getBlockZ() + dz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int dy = -radius; dy <= radius; dy++) {
                int y = center.getBlockY() + dy;
                if (y < world.getMinHeight() || y >= world.getMaxHeight()
                        || !MithrilRules.withinPulse(dx, dy, dz, radius)) continue;
                Block block = world.getBlockAt(x, y, z);
                if (!ores.contains(block.getType().name()) || placed.isMarked(block)) continue;
                found.add(new Candidate(block, dx * dx + dy * dy + dz * dz));
            }
        }
        found.sort(Comparator.comparingInt(Candidate::distanceSquared));
        List<Block> allowed = new ArrayList<>();
        for (Candidate candidate : found) {
            if (!regions.canBreak(player, candidate.block())) continue;
            allowed.add(candidate.block());
            if (allowed.size() >= config("max-menas")) break;
        }
        return allowed;
    }

    public void cancel(UUID id) {
        Pulse pulse = pulses.remove(id);
        if (pulse == null) return;
        pulse.expiry().cancel();
        pulse.displays().forEach(BlockDisplay::remove);
    }

    public void disable() { List.copyOf(pulses.keySet()).forEach(this::cancel); }
}
