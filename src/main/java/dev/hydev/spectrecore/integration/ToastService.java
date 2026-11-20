package dev.hydev.spectrecore.integration;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.text.TextService;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import java.util.Locale;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Native Java client achievement-toasts using fixed, impossible-trigger advancements.
 *
 * Paper 1.21.10 still exposes UnsafeValues#loadAdvancement; this is version-specific
 * and deprecated. We intentionally register a finite set of namespaced advancements,
 * no player-specific/dynamic advancements, no rewards or chat announcements, and
 * revoke the granted criterion shortly afterward. No calls to reloadData().
 *
 * If registration fails, callers keep their previous actionbar/message fallback.
 */
@SuppressWarnings("deprecation")
public final class ToastService implements Listener {
    private record Preset(String icon, String title, String description, String sound) { }
    private static final Map<String, Preset> DEFAULTS = Map.of(
            "furia", new Preset("minecraft:netherite_axe", "Furia completa", "Doble Shift para despertar", "ENTITY_PLAYER_LEVELUP"),
            "resonancia", new Preset("minecraft:amethyst_shard", "Resonancia completa", "Agáchate y extrae una gema", "BLOCK_AMETHYST_BLOCK_CHIME"),
            "gema-basica", new Preset("minecraft:emerald", "Gema obtenida", "Calidad básica", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            "gema-intermedia", new Preset("minecraft:diamond", "Gema obtenida", "Calidad intermedia", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            "gema-superior", new Preset("minecraft:nether_star", "Gema obtenida", "Calidad superior", "ENTITY_PLAYER_LEVELUP"),
            "trabajo-minero", new Preset("minecraft:iron_pickaxe", "Trabajo completado", "Pago de minería", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            "trabajo-granjero", new Preset("minecraft:wheat", "Trabajo completado", "Pago de agricultura", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            "trabajo-cazador", new Preset("minecraft:iron_sword", "Trabajo completado", "Pago de cacería", "ENTITY_EXPERIENCE_ORB_PICKUP")
    );

    private final JavaPlugin plugin;
    private final Map<String, Advancement> advancements = new LinkedHashMap<>();
    private final Map<UUID, Long> lastToast = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastByPreset = new HashMap<>();
    private YamlConfiguration settings;
    private boolean warned;

    public ToastService(JavaPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Map.Entry<String, Preset> entry : DEFAULTS.entrySet()) {
            String id = entry.getKey();
            Preset fallback = entry.getValue();
            NamespacedKey key = new NamespacedKey(plugin, "toast_" + id.replace('-', '_'));
            try {
                Advancement advancement = Bukkit.getAdvancement(key);
                if (advancement == null) {
                    String prefix = "toasts.presets." + id + ".";
                    String icon = settings.getString(prefix + "icon", fallback.icon());
                    if (icon == null || !icon.matches("(minecraft:)?[a-z0-9_]+")) icon = fallback.icon();
                    if (!icon.contains(":")) icon = "minecraft:" + icon;
                    String title = settings.getString(prefix + "titulo", fallback.title());
                    String description = settings.getString(prefix + "descripcion", fallback.description());
                    advancement = Bukkit.getUnsafe().loadAdvancement(key, json(icon, title, description));
                }
                if (advancement == null) throw new IllegalStateException("Paper no registro el avance");
                advancements.put(id, advancement);
            } catch (RuntimeException | LinkageError error) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().log(Level.WARNING, "Toasts nativos no disponibles. Los mensajes anteriores siguen activos: " + error.getMessage());
                }
            }
        }
    }

    /** Changes to enabled/cooldowns are hot-reloadable; titles/icons are registered at boot. */
    public void reload() {
        settings = ModuleConfiguration.module(plugin, "nucleo/ajustes.yml");
    }

    /**
     * Grant then revoke an impossible criterion. Returns true when the notification was
     * handled, including anti-flood suppression; false means the caller must use its fallback.
     * Caller must retain its prior fallback if false.
     */
    public boolean show(Player player, String id) {
        if (player == null || !player.isOnline() || !settings.getBoolean("toasts.enabled", true)
                || !settings.getBoolean("toasts.eventos." + id, true)) return false;
        Advancement advancement = advancements.get(id);
        if (advancement == null) return false;
        long now = System.currentTimeMillis();
        long interval = Math.max(0, Math.min(600, settings.getLong("toasts.intervalo-global-segundos", 3))) * 1000L;
        long ownInterval = Math.max(0, Math.min(3600,
                settings.getLong("toasts.presets." + id + ".intervalo-segundos", 0))) * 1000L;
        // Global antispam and per-event cooldown are independent.
        if (now - lastToast.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < interval) return true;
        if (now - lastByPreset.getOrDefault(player.getUniqueId(), Map.of())
                .getOrDefault(id, Long.MIN_VALUE / 2) < ownInterval) return true;
        try {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) for (String criterion : progress.getAwardedCriteria()) progress.revokeCriteria(criterion);
            boolean granted = false;
            for (String criterion : progress.getRemainingCriteria()) {
                granted |= progress.awardCriteria(criterion);
            }
            if (!granted) return false;
            lastToast.put(player.getUniqueId(), now);
            lastByPreset.computeIfAbsent(player.getUniqueId(), unused -> new HashMap<>()).put(id, now);
            playConfiguredSound(player, id);
            UUID owner = player.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player online = Bukkit.getPlayer(owner);
                if (online == null || !online.isOnline()) return;
                try {
                    AdvancementProgress current = online.getAdvancementProgress(advancement);
                    for (String criterion : current.getAwardedCriteria()) current.revokeCriteria(criterion);
                } catch (RuntimeException ignored) { /* No rewards or persistent progress. */ }
            }, 2L);
            return true;
        } catch (RuntimeException error) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Toast no disponible: " + error.getMessage());
            }
            return false;
        }
    }

    /**
     * Sounds are independent of advancement display configuration and apply after
     * /spectrecore reload. Invalid sound names fail silently to preserve the toast.
     * "NINGUNO" turns off the effect for this preset.
     */
    private void playConfiguredSound(Player player, String id) {
        Preset defaults = DEFAULTS.get(id);
        if (defaults == null) return;
        String prefix = "toasts.presets." + id + ".";
        String name = settings.getString(prefix + "sonido", defaults.sound());
        if (name == null || name.isBlank() || name.equalsIgnoreCase("NINGUNO")
                || name.equalsIgnoreCase("NONE")) return;
        try {
            Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
            double rawVolume = settings.getDouble(prefix + "volumen", 0.8);
            double rawPitch = settings.getDouble(prefix + "tono", 1.0);
            if (!Double.isFinite(rawVolume) || !Double.isFinite(rawPitch)) return;
            float volume = (float) Math.max(0, Math.min(4, rawVolume));
            float pitch = (float) Math.max(0.5, Math.min(2, rawPitch));
            if (volume > 0) player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Toast " + id + ": sonido desconocido " + name);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) clear(event.getPlayer());
        }, 3L);
    }

    /** Explicitly clear progress at quit/join so a quick disconnect never saves a completed toast. */
    public void clear(Player player) {
        if (player == null) return;
        lastToast.remove(player.getUniqueId());
        lastByPreset.remove(player.getUniqueId());
        for (Advancement advancement : advancements.values()) {
            try {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                for (String criterion : progress.getAwardedCriteria()) progress.revokeCriteria(criterion);
            } catch (RuntimeException ignored) { }
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        // Do not delete registered advancements: Paper requires reloadData() to fully
        // remove them. Advancement progress is revoked and no player progress is saved.
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        advancements.clear();
        lastToast.clear();
        lastByPreset.clear();
    }

    /** Advancement components use Adventure JSON, not literal MiniMessage tags. */
    private static String json(String icon, String title, String description) {
        String titleJson = GsonComponentSerializer.gson().serialize(TextService.component(title));
        String descriptionJson = GsonComponentSerializer.gson().serialize(TextService.component(description));
        return "{\"display\":{\"icon\":{\"id\":\"" + escape(icon) + "\"},"
                + "\"title\":" + titleJson + ","
                + "\"description\":" + descriptionJson + ","
                + "\"frame\":\"task\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true},"
                + "\"criteria\":{\"notify\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"requirements\":[[\"notify\"]]}";
    }

    private static String escape(String input) {
        if (input == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < input.length() && i < 120; i++) {
            char c = input.charAt(i);
            if (c == '"' || c == '\\') text.append('\\');
            if (c == '\n' || c == '\r' || c == '\t' || c < 32) c = ' ';
            text.append(c);
        }
        return text.toString();
    }
}
