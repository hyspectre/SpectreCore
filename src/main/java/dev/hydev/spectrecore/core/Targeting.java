package dev.hydev.spectrecore.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/** Shared non-PvP hostile detection and validation, including tagged MythicMobs. */
public final class Targeting {
    private final JavaPlugin plugin;
    public Targeting(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean isHostile(Entity entity) {
        if (!(entity instanceof LivingEntity mob) || entity instanceof Player || mob.isDead() || !mob.isValid()) return false;
        if (entity instanceof Enemy) return true;
        List<String> tags = plugin.getConfig().getStringList("engine.combat.hostile-scoreboard-tags");
        for (String tag : tags) if (entity.getScoreboardTags().contains(tag)) return true;
        return false;
    }

    public LivingEntity valid(Player source, UUID id, double range) {
        if (id == null) return null;
        Entity found = Bukkit.getEntity(id);
        if (!(found instanceof LivingEntity mob) || !isHostile(mob) || !mob.getWorld().equals(source.getWorld())) return null;
        if (mob.getLocation().distanceSquared(source.getLocation()) > range * range) return null;
        if (plugin.getConfig().getBoolean("engine.combat.require-line-of-sight", true) && !source.hasLineOfSight(mob)) return null;
        return mob;
    }

    public LivingEntity nearest(Player player, double range) {
        LivingEntity closest = null;
        double best = range * range;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!isHostile(entity)) continue;
            LivingEntity mob = (LivingEntity) entity;
            double squared = player.getLocation().distanceSquared(mob.getLocation());
            if (squared < best && (!plugin.getConfig().getBoolean("engine.combat.require-line-of-sight", true) || player.hasLineOfSight(mob))) {
                best = squared;
                closest = mob;
            }
        }
        return closest;
    }
}
