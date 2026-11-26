package dev.hydev.spectrecore.core;

import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** One transient native indicator per equipped skill, updated by its existing engine tick. */
public final class SkillHud {
    private BossBar bar;
    private long nextUpdate;

    public boolean due(long tick) { return tick >= nextUpdate; }
    public void updated(long tick) { nextUpdate = tick + 10; }
    public static float progress(long value, long maximum) {
        return Math.clamp(value / (float) Math.max(1, maximum), 0f, 1f);
    }
    public static long seconds(long ticks) { return Math.max(0, (ticks + 19) / 20); }
    public void show(Player player, Component title, float progress, BossBar.Color color) {
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, BossBar.Overlay.PROGRESS);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }
    }
    public void hide(UUID owner) {
        if (bar == null) return;
        Player player = Bukkit.getPlayer(owner);
        if (player != null) player.hideBossBar(bar);
        bar = null;
    }
}
