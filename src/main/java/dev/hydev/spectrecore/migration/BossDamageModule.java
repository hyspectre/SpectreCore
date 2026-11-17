package dev.hydev.spectrecore.migration;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BossDamageModule implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Double> channels = new HashMap<>();
    private BukkitTask cleanup;
    private org.bukkit.configuration.file.YamlConfiguration config;
    public BossDamageModule(JavaPlugin plugin) { this.plugin = plugin; }
    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        cleanup = Bukkit.getScheduler().runTaskTimer(plugin, () -> channels.keySet().removeIf(id -> {
            var entity = Bukkit.getEntity(id);
            return entity == null || !entity.isValid() || !entity.getScoreboardTags().contains("HYDEV_CABA_CANAL");
        }), 200, 200);
    }
    public void reload() { config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin,"dungeons/jefes.yml"); }
    public void disable() { HandlerList.unregisterAll(this); if (cleanup != null) cleanup.cancel(); channels.clear(); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void ethereal(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains("HYDEV_SPECTRE_ETEREO")) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void modify(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        Set<String> t = mob.getScoreboardTags();
        if (t.contains("HYDEV_SPECTRE_ETEREO")) { event.setCancelled(true); return; }
        double factor = 1;
        String[] names = {"HYDEV_DRACO_AGOTADO","HYDEV_ENT_CORTEZA","HYDEV_CABA_VULNERABLE","HYDEV_MINO_VULNERABLE","HYDEV_MINO_SHIELD"};
        double[] defaults = {1.5,.65,1.25,1.25,.20};
        for (int i=0;i<names.length;i++) if(t.contains(names[i])) { double multiplier=config.getDouble("multiplicadores."+names[i],defaults[i]);factor*=Double.isFinite(multiplier)&&multiplier>=0?multiplier:defaults[i]; }
        if (factor != 1) event.setDamage(event.getDamage() * factor);
        var attribute = mob.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        double floor = t.contains("HYDEV_MINO_LOCK70") ? config.getDouble("bloqueos.HYDEV_MINO_LOCK70",.70) : t.contains("HYDEV_MINO_LOCK35") ? config.getDouble("bloqueos.HYDEV_MINO_LOCK35",.35) : 0;
        if (floor > 0 && MigrationRules.crosses(mob.getHealth(), event.getFinalDamage(), attribute.getValue(), floor)) {
            event.setCancelled(true);
            double guarded = MigrationRules.guardedHealth(mob.getHealth(), attribute.getValue(), floor);
            if (guarded < mob.getHealth()) {
                var center = mob.getLocation().add(0, .15, 0);
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI / 12;
                    mob.getWorld().spawnParticle(Settings.particle("dungeons", "BossDamageModule.particle-001", org.bukkit.Particle.DUST), center.clone().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5),Settings.i("dungeons", "BossDamageModule.spawnparticle-001-arg2", 1),Settings.d("dungeons", "BossDamageModule.spawnparticle-002-arg3", 0),Settings.d("dungeons", "BossDamageModule.spawnparticle-003-arg4", 0),Settings.d("dungeons", "BossDamageModule.spawnparticle-004-arg5", 0),Settings.d("dungeons", "BossDamageModule.spawnparticle-005-arg6", 0),
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(Settings.i("dungeons", "BossDamageModule.fromrgb-007-arg0", 190),Settings.i("dungeons", "BossDamageModule.fromrgb-008-arg1", 65),Settings.i("dungeons", "BossDamageModule.fromrgb-009-arg2", 35)),Settings.f("dungeons", "BossDamageModule.dustoptions-006-arg1", 1)));
                }
                mob.getWorld().playSound(center, Settings.sound("dungeons", "BossDamageModule.sound-003", org.bukkit.Sound.BLOCK_ANVIL_LAND),Settings.f("dungeons", "BossDamageModule.playsound-010-arg2", .5f),Settings.f("dungeons", "BossDamageModule.playsound-011-arg3", .65f));
            }
            mob.setHealth(guarded);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void count(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        UUID id = mob.getUniqueId();
        Set<String> tags = mob.getScoreboardTags();
        if (!tags.contains("HYDEV_CABA_CANAL")) { channels.remove(id); return; }
        if (tags.contains("HYDEV_CABA_RESET")) {
            channels.remove(id);
            mob.removeScoreboardTag("HYDEV_CABA_RESET");
        }
        double damage = Math.min(mob.getHealth(), Math.max(0, event.getFinalDamage()));
        double threshold=config.getDouble("robo-vitalidad.umbral-hp",200);
        if(!Double.isFinite(threshold)||threshold<=0)threshold=200;
        if (damage > 0 && channels.merge(id, damage, Double::sum)>=threshold) {
            boolean newlyBroken = mob.addScoreboardTag("HYDEV_CABA_BREAK");
            if (newlyBroken) {
                mob.getWorld().spawnParticle(Settings.particle("dungeons", "BossDamageModule.particle-004", org.bukkit.Particle.CRIT), mob.getLocation().add(0, 1, 0),Settings.i("dungeons", "BossDamageModule.spawnparticle-012-arg2", 22),Settings.d("dungeons", "BossDamageModule.spawnparticle-013-arg3", .5),Settings.d("dungeons", "BossDamageModule.spawnparticle-014-arg4", .6),Settings.d("dungeons", "BossDamageModule.spawnparticle-015-arg5", .5),Settings.d("dungeons", "BossDamageModule.spawnparticle-016-arg6", .2));
                mob.getWorld().playSound(mob.getLocation(), Settings.sound("dungeons", "BossDamageModule.sound-005", org.bukkit.Sound.BLOCK_AMETHYST_CLUSTER_BREAK),Settings.f("dungeons", "BossDamageModule.playsound-017-arg2", .7f),Settings.f("dungeons", "BossDamageModule.playsound-018-arg3", .7f));
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) { channels.remove(event.getEntity().getUniqueId()); }
}
