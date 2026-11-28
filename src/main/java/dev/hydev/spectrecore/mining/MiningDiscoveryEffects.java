package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Owner-only effects; no entities, repeating tasks, world changes or delayed rewards. */
final class MiningDiscoveryEffects {
    private MiningDiscoveryEffects() { }

    private static void burst(Player player, Location at, Particle particle, int count, double radius, double speed) {
        if (at.getWorld() != player.getWorld() || !at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) return;
        if (particle.getDataType() != Void.class) particle = Particle.END_ROD;
        int bounded = Math.clamp(count, 0, 64);
        if (bounded > 0) player.spawnParticle(particle, at, bounded,
                Math.clamp(radius, 0, 2), Math.clamp(radius, 0, 2), Math.clamp(radius, 0, 2),
                Math.clamp(speed, 0, 0.5));
    }

    static void marker(Player player, Location point) {
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-marcador", Particle.END_ROD),
                Settings.i("resonancia", "MiningDiscoveryEffects.marcador-cantidad", 8),
                Settings.d("resonancia", "MiningDiscoveryEffects.marcador-radio", 0.55), 0);
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-marcador-secundario", Particle.ENCHANT),
                Settings.i("resonancia", "MiningDiscoveryEffects.marcador-secundario-cantidad", 6),
                Settings.d("resonancia", "MiningDiscoveryEffects.marcador-radio", 0.55), 0.02);
    }

    static void geode(Player player, Location point) {
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-geoda", Particle.END_ROD),
                Settings.i("resonancia", "MiningDiscoveryEffects.geoda-cantidad", 28),
                Settings.d("resonancia", "MiningDiscoveryEffects.geoda-radio", 0.6), 0.07);
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-geoda-destello", Particle.ENCHANT),
                Settings.i("resonancia", "MiningDiscoveryEffects.geoda-destello-cantidad", 20),
                Settings.d("resonancia", "MiningDiscoveryEffects.geoda-radio", 0.6), 0.03);
        player.playSound(point, Settings.sound("resonancia", "MiningModule.geoda-sonido", Sound.BLOCK_AMETHYST_BLOCK_CHIME),
                Settings.f("resonancia", "MiningDiscoveryEffects.geoda-volumen", 0.85f),
                Settings.f("resonancia", "MiningDiscoveryEffects.geoda-tono", 1.25f));
    }

    static void gemSparkles(Player player, Location point) {
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-gema", Particle.END_ROD),
                Settings.i("resonancia", "MiningDiscoveryEffects.gema-cantidad", 20),
                Settings.d("resonancia", "MiningDiscoveryEffects.gema-radio", 0.42), 0.06);
        burst(player, point, Settings.particle("resonancia", "MiningDiscoveryEffects.particle-gema-destello", Particle.ENCHANT),
                Settings.i("resonancia", "MiningDiscoveryEffects.gema-destello-cantidad", 16),
                Settings.d("resonancia", "MiningDiscoveryEffects.gema-radio", 0.42), 0.025);
    }

    static void gemReward(Player player, Location point) {
        gemSparkles(player, point);
        player.playSound(point, Settings.sound("resonancia", "MiningDiscoveryEffects.sound-gema", Sound.BLOCK_AMETHYST_CLUSTER_BREAK),
                Settings.f("resonancia", "MiningDiscoveryEffects.gema-volumen", 0.75f),
                Settings.f("resonancia", "MiningDiscoveryEffects.gema-tono", 1.35f));
    }

    static void gem(Player player, Location point, ItemStack reward) {
        gemReward(player, point);
        Component name = reward.getItemMeta() != null ? reward.getItemMeta().displayName() : null;
        // Only called after the award, never during discovery preview.
        if (name != null) player.sendMessage(Settings.message("resonancia", "MiningDiscoveryEffects.gema-obtenida",
                "<light_purple>✦ <gray>Gema encontrada: <white>{gema}", "gema", name));
    }
}
