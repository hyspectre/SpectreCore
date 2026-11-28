package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Player-owned progress persists in vanilla player data; bars exist only while mining. */
final class GeodeProgress {
    private record Visible(BossBar bar, long expires, UUID world) { }
    private final NamespacedKey key;
    private final Map<UUID, Visible> visible = new HashMap<>();

    GeodeProgress(JavaPlugin plugin) { key = new NamespacedKey(plugin, "geoda_progreso"); }

    long advance(Player player, long step) {
        var data = player.getPersistentDataContainer();
        long next = GeodeProgressRules.advance(data.getOrDefault(key, PersistentDataType.LONG, 0L), step);
        data.set(key, PersistentDataType.LONG, GeodeProgressRules.stored(next));
        return next;
    }

    void discovered(Player player, long progress) {
        player.getPersistentDataContainer().set(key, PersistentDataType.LONG,
                GeodeProgressRules.afterDiscovery(progress));
    }

    void show(Player player, long progress, long step, int seconds) {
        long current = GeodeProgressRules.stored(progress);
        var old = visible.get(player.getUniqueId());
        BossBar bar;
        if (old == null) {
            bar = BossBar.bossBar(Component.empty(), 0, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(bar);
        } else bar = old.bar();
        bar.progress(current / (float) GeodeProgressRules.TARGET);
        if (current == GeodeProgressRules.TARGET) {
            bar.color(BossBar.Color.PURPLE);
            bar.name(Settings.message("resonancia", "GeodeProgress.preparada",
                    "<light_purple>✦ Geoda preparada <gray>· Sigue minando en una zona libre"));
        } else {
            bar.color(BossBar.Color.BLUE);
            bar.name(Settings.message("resonancia", "GeodeProgress.medidor",
                    "<aqua>✦ Geoda <white>{progreso}% <gray>· {menas} menas restantes",
                    "progreso", String.format(Locale.ROOT, "%.1f", (current / 100_000) / 10.0),
                    "menas", GeodeProgressRules.remaining(current, step)));
        }
        visible.put(player.getUniqueId(), new Visible(bar, System.currentTimeMillis() + seconds * 1000L,
                player.getWorld().getUID()));
    }

    void tick(Player player) {
        var entry = visible.get(player.getUniqueId());
        if (entry != null && (entry.expires() <= System.currentTimeMillis()
                || !entry.world().equals(player.getWorld().getUID())
                || player.getGameMode() != org.bukkit.GameMode.SURVIVAL)) hide(player);
    }

    void hide(Player player) {
        var entry = visible.remove(player.getUniqueId());
        if (entry != null) player.hideBossBar(entry.bar());
    }
}
