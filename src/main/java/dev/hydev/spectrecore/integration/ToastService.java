package dev.hydev.spectrecore.integration;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
public final class ToastService {
    private record Preset(String icon, String title, String description) { }
    private static final Map<String, Preset> DEFAULTS = Map.of(
            "furia", new Preset("minecraft:netherite_axe", "Furia completa", "Doble Shift para despertar"),
            "resonancia", new Preset("minecraft:amethyst_shard", "Resonancia completa", "Agachate y extrae una gema"),
            "gema-basica", new Preset("minecraft:emerald", "Gema obtenida", "Calidad basica"),
            "gema-intermedia", new Preset("minecraft:diamond", "Gema obtenida", "Calidad intermedia"),
            "gema-superior", new Preset("minecraft:nether_star", "Gema obtenida", "Calidad superior"),
            "trabajo-minero", new Preset("minecraft:iron_pickaxe", "Trabajo completado", "Pago de mineria"),
            "trabajo-granjero", new Preset("minecraft:wheat", "Trabajo completado", "Pago de agricultura"),
            "trabajo-cazador", new Preset("minecraft:iron_sword", "Trabajo completado", "Pago de caceria")
    );

    private final JavaPlugin plugin;
    private final Map<String, Advancement> advancements = new LinkedHashMap<>();
    private final Map<UUID, Long> lastToast = new HashMap<>();
    private YamlConfiguration settings;
    private boolean warned;

    public ToastService(JavaPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        reload();
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
     * Grant then revoke an impossible criterion. Returns true only when the toast was issued.
     * Caller must retain its prior fallback if false.
     */
    public boolean show(Player player, String id) {
        if (player == null || !player.isOnline() || !settings.getBoolean("toasts.enabled", true)
                || !settings.getBoolean("toasts.eventos." + id, true)) return false;
        Advancement advancement = advancements.get(id);
        if (advancement == null) return false;
        long now = System.currentTimeMillis();
        long interval = Math.max(0, Math.min(600, settings.getLong("toasts.intervalo-global-segundos", 4))) * 1000L;
        if (now - lastToast.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < interval) return false;
        try {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) for (String criterion : progress.getAwardedCriteria()) progress.revokeCriteria(criterion);
            boolean granted = false;
            for (String criterion : progress.getRemainingCriteria()) {
                granted |= progress.awardCriteria(criterion);
            }
            if (!granted) return false;
            lastToast.put(player.getUniqueId(), now);
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

    public void forget(UUID player) { lastToast.remove(player); }
    public void disable() {
        // Do not delete registered advancements: Paper requires reloadData() to fully
        // remove them. Advancement progress is revoked and no player progress is saved.
        for (Player p : Bukkit.getOnlinePlayers()) for (Advancement advancement : advancements.values()) {
            try {
                AdvancementProgress progress = p.getAdvancementProgress(advancement);
                for (String criterion : progress.getAwardedCriteria()) progress.revokeCriteria(criterion);
            } catch (RuntimeException ignored) { }
        }
        advancements.clear();
        lastToast.clear();
    }

    private static String json(String icon, String title, String description) {
        return "{\"display\":{\"icon\":{\"id\":\"" + escape(icon) + "\"},"
                + "\"title\":{\"text\":\"" + escape(title) + "\"},"
                + "\"description\":{\"text\":\"" + escape(description) + "\"},"
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
