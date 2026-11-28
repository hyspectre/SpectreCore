package dev.hydev.spectrecore.skills;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.core.SkillModule;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Corona de Ánimas: 3 temporary skulls, 3 shots each, one global shot every 3 s. */
public final class CoronaSkill implements SkillModule {
    private static final class Skull {
        final ItemDisplay visual;
        int shots;
        Skull(ItemDisplay visual) { this.visual = visual; }
    }
    private static final class State {
        UUID activation;
        UUID lastAggressor;
        UUID lastAttacked;
        World world;
        long startedAtTick;
        long activeUntilMs;
        long nextShotTick;
        long lastShotTick;
        int shotsFired;
        final List<Skull> skulls = new ArrayList<>();
    }

    private final SpectreCorePlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();

    public CoronaSkill(SpectreCorePlugin plugin) { this.plugin = plugin; }
    @Override public String id() { return "necrotica"; }
    private int getInt(String path, int def) {
        return plugin.getConfig().getInt("skills.necrotica." + path, def);
    }
    private double getDouble(String path, double def) {
        return plugin.getConfig().getDouble("skills.necrotica." + path, def);
    }
    private boolean access(Player player) { return plugin.hasAccess(player, id(), hasSet(player)); }
    @Override public boolean hasSet(Player player) {
        return plugin.sets().hasArmor(player, plugin.getConfig().getConfigurationSection("skills.necrotica.armor"));
    }

    @Override public void onMelee(Player player, LivingEntity enemy, long engineTick) {
        if (!access(player)) return;
        states.computeIfAbsent(player.getUniqueId(), ignored -> new State()).lastAttacked = enemy.getUniqueId();
    }
    @Override public void onHostileDamage(Player player, LivingEntity aggressor, long engineTick) {
        if (!access(player)) return;
        State state = states.computeIfAbsent(player.getUniqueId(), ignored -> new State());
        state.lastAggressor = aggressor.getUniqueId();
        if (state.activation != null || !plugin.cooldowns().ready(player.getUniqueId(), id())) return;
        activate(player, state, engineTick);
    }

    private void activate(Player player, State state, long engineTick) {
        if (!plugin.cooldowns().start(player.getUniqueId(), id(), getInt("cooldown-seconds", 45))) return;
        state.activation = UUID.randomUUID();
        state.world = player.getWorld();
        state.startedAtTick = engineTick;
        state.activeUntilMs = System.currentTimeMillis() + getInt("duration-seconds", 30) * 1000L;
        state.nextShotTick = engineTick + Math.max(getInt("summoning-ticks", 16), getInt("fire-first-delay-ticks", 60));
        state.shotsFired = 0;
        for (Skull skull : state.skulls) if (skull.visual.isValid()) skull.visual.remove();
        state.skulls.clear();
        int count = Math.min(3, Math.max(1, getInt("skulls", 3))); // Current choreography has three positions.
        double scale = Math.max(0.15, Math.min(0.8, getDouble("skull-scale", 0.4)));
        for (int i = 0; i < count; i++) {
            ItemDisplay display = player.getWorld().spawn(player.getLocation().add(0, Settings.d("equipamiento", "CoronaSkill.geometria-001", 0.3), 0), ItemDisplay.class, entity -> {
                entity.setItemStack(new ItemStack(Settings.material("equipamiento", "CoronaSkill.material-014", Material.WITHER_SKELETON_SKULL)));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                plugin.visuals().mark(entity);
                entity.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(),
                        new Vector3f((float) scale, (float) scale, (float) scale), new Quaternionf()));
            });
            state.skulls.add(new Skull(display));
        }
        Location feet = player.getLocation().add(0, Settings.d("equipamiento", "CoronaSkill.geometria-002", 0.1), 0);
        for (int i = 0; i < 28; i++) {
            double angle = i * Math.PI * 2 / Settings.d("equipamiento", "CoronaSkill.geometria-003", 28.0);
            Location particleAt = feet.clone().add(Math.cos(angle) * Settings.d("equipamiento", "CoronaSkill.geometria-004", 1.05), 0, Math.sin(angle) * Settings.d("equipamiento", "CoronaSkill.geometria-005", 1.05));
            player.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-001", Particle.SOUL), particleAt,Settings.i("equipamiento", "CoronaSkill.spawnparticle-001-arg2", 1),Settings.d("equipamiento", "CoronaSkill.spawnparticle-002-arg3", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-003-arg4", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-004-arg5", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-005-arg6", 0.01));
        }
        player.getWorld().playSound(player.getLocation(), Settings.string("equipamiento", "CoronaSkill.sonido-008", "minecraft:entity.wither.ambient"),Settings.f("equipamiento", "CoronaSkill.playsound-006-arg2", 0.85f),Settings.f("equipamiento", "CoronaSkill.playsound-007-arg3", 1.4f));
        player.sendMessage(Settings.message("equipamiento", "CoronaSkill.mensaje-001", "§3☠ §bCorona de Ánimas §7— Tres almas responden a tu dolor."));
    }

    @Override public void tick(long engineTick) {
        for (Map.Entry<UUID, State> entry : new HashMap<>(states).entrySet()) {
            UUID id = entry.getKey();
            State state = entry.getValue();
            Player player = Bukkit.getPlayer(id);
            if (player == null || player.isDead() || !player.isValid() || !access(player)
                    || (state.world != null && state.world != player.getWorld())) {
                clear(id);
                continue;
            }
            if (state.activation == null) continue; // Only the tracking state exists before activation.
            int count = state.skulls.size();
            int cap = count * Math.max(1, getInt("shots-per-skull", 3));
            if (System.currentTimeMillis() >= state.activeUntilMs) {
                clear(id);
                continue;
            }
            if (state.shotsFired >= cap && engineTick > state.lastShotTick + getInt("projectile-travel-ticks", 8) + 2) {
                clear(id);
                continue;
            }
            updateOrbit(player, state, engineTick);
            if (state.shotsFired < cap && engineTick >= state.nextShotTick) {
                LivingEntity target = chooseTarget(player, state);
                // Retain unused ammo and retry next tick when there is no valid target.
                if (target != null) {
                    shoot(player, state, target, engineTick);
                    state.nextShotTick = engineTick + Math.max(1, getInt("fire-interval-ticks", 60));
                }
            }
        }
    }

    private void updateOrbit(Player player, State state, long engineTick) {
        long elapsed = engineTick - state.startedAtTick;
        double progress = Math.min(1, Math.max(0, elapsed / (double) Math.max(1, getInt("summoning-ticks", 16))));
        double orbitRadius = Math.max(0.3, getDouble("orbit-radius", 1.10)) * progress;
        Location origin = player.getLocation();
        for (int i = 0; i < state.skulls.size(); i++) {
            Skull skull = state.skulls.get(i);
            if (!skull.visual.isValid()) continue;
            double angle = elapsed * Settings.d("equipamiento", "CoronaSkill.geometria-006", 0.032) + i * Math.PI * 2.0 / state.skulls.size();
            double baseHeight = Settings.d("equipamiento", "CoronaSkill.geometria-007", 1.42) + (i == 2 ? Settings.d("equipamiento", "CoronaSkill.geometria-008", 0.18) : 0.0);
            double y = Settings.d("equipamiento", "CoronaSkill.geometria-009", 0.25) + (baseHeight - Settings.d("equipamiento", "CoronaSkill.geometria-010", 0.25)) * progress + Math.sin(elapsed * Settings.d("equipamiento", "CoronaSkill.geometria-011", 0.10) + i) * Settings.d("equipamiento", "CoronaSkill.geometria-012", 0.08);
            Location at = origin.clone().add(Math.cos(angle) * orbitRadius, y, Math.sin(angle) * orbitRadius);
            Vector face = player.getEyeLocation().toVector().subtract(at.toVector());
            if (face.lengthSquared() > 0.0001) at.setDirection(face);
            skull.visual.teleport(at);
            if (elapsed % 12 == 0) player.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-002", Particle.SOUL_FIRE_FLAME), at,Settings.i("equipamiento", "CoronaSkill.spawnparticle-008-arg2", 1),Settings.d("equipamiento", "CoronaSkill.spawnparticle-009-arg3", .06),Settings.d("equipamiento", "CoronaSkill.spawnparticle-010-arg4", .07),Settings.d("equipamiento", "CoronaSkill.spawnparticle-011-arg5", .06),Settings.d("equipamiento", "CoronaSkill.spawnparticle-012-arg6", 0.002));
        }
    }

    private LivingEntity chooseTarget(Player player, State state) {
        double range = Math.max(1, getDouble("range", 8));
        LivingEntity target = plugin.targeting().valid(player, state.lastAggressor, range);
        if (target == null) target = plugin.targeting().valid(player, state.lastAttacked, range);
        if (target == null) target = plugin.targeting().nearest(player, range);
        return target;
    }

    private void shoot(Player player, State state, LivingEntity target, long engineTick) {
        // Round robin across skulls, three projectiles each, without synchronized salvoes.
        int skullIndex = state.shotsFired % state.skulls.size();
        Skull skull = state.skulls.get(skullIndex);
        if (!skull.visual.isValid()) return;
        Location start = skull.visual.getLocation().clone();
        UUID token = state.activation;
        UUID playerId = player.getUniqueId();
        UUID enemyId = target.getUniqueId();
        state.shotsFired++;
        state.lastShotTick = engineTick;
        skull.shots++;
        player.getWorld().playSound(start, Settings.string("equipamiento", "CoronaSkill.sonido-009", "minecraft:entity.allay.hurt"),Settings.f("equipamiento", "CoronaSkill.playsound-013-arg2", 0.6f),Settings.f("equipamiento", "CoronaSkill.playsound-014-arg3", 0.60f));
        if (skull.shots >= Math.max(1, getInt("shots-per-skull", 3))) {
            player.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-003", Particle.SOUL), start,Settings.i("equipamiento", "CoronaSkill.spawnparticle-015-arg2", 14),Settings.d("equipamiento", "CoronaSkill.spawnparticle-016-arg3", .18),Settings.d("equipamiento", "CoronaSkill.spawnparticle-017-arg4", .17),Settings.d("equipamiento", "CoronaSkill.spawnparticle-018-arg5", .18),Settings.d("equipamiento", "CoronaSkill.spawnparticle-019-arg6", .015));
            skull.visual.remove();
        }
        // The flying projectile is cosmetic. Damage is checked and applied on impact.
        final int travel = Math.max(2, Math.min(24, getInt("projectile-travel-ticks", 8)));
        new BukkitRunnable() {
            int step;
            @Override public void run() {
                Player owner = Bukkit.getPlayer(playerId);
                State current = states.get(playerId);
                if (owner == null || current == null || !token.equals(current.activation)
                        || !access(owner) || owner.isDead() || current.world != owner.getWorld()) { cancel(); return; }
                LivingEntity enemy = plugin.targeting().valid(owner, enemyId, getDouble("range", 8.0));
                if (enemy == null) { cancel(); return; }
                step++;
                Location end = enemy.getEyeLocation().add(0, -Settings.d("equipamiento", "CoronaSkill.geometria-013", 0.14), 0);
                Vector vector = end.toVector().subtract(start.toVector());
                double t = Math.min(1, step / (double) travel);
                Location point = start.clone().add(vector.multiply(t));
                owner.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-004", Particle.SOUL), point,Settings.i("equipamiento", "CoronaSkill.spawnparticle-020-arg2", 2),Settings.d("equipamiento", "CoronaSkill.spawnparticle-021-arg3", .05),Settings.d("equipamiento", "CoronaSkill.spawnparticle-022-arg4", .05),Settings.d("equipamiento", "CoronaSkill.spawnparticle-023-arg5", .05),Settings.d("equipamiento", "CoronaSkill.spawnparticle-024-arg6", .002));
                owner.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-005", Particle.DUST), point,Settings.i("equipamiento", "CoronaSkill.spawnparticle-025-arg2", 1),Settings.d("equipamiento", "CoronaSkill.spawnparticle-026-arg3", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-027-arg4", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-028-arg5", 0),Settings.d("equipamiento", "CoronaSkill.spawnparticle-029-arg6", 0),
                        new Particle.DustOptions(Color.fromRGB(Settings.i("equipamiento", "CoronaSkill.fromrgb-031-arg0", 88),Settings.i("equipamiento", "CoronaSkill.fromrgb-032-arg1", 191),Settings.i("equipamiento", "CoronaSkill.fromrgb-033-arg2", 166)),Settings.f("equipamiento", "CoronaSkill.dustoptions-030-arg1", .8f)));
                if (step >= travel) {
                    plugin.dealSkillMagicDamage(enemy, getDouble("damage-per-shot", 5.0));
                    owner.getWorld().spawnParticle(Settings.particle("equipamiento", "CoronaSkill.particle-007", Particle.SOUL_FIRE_FLAME), end,Settings.i("equipamiento", "CoronaSkill.spawnparticle-034-arg2", 7),Settings.d("equipamiento", "CoronaSkill.spawnparticle-035-arg3", .14),Settings.d("equipamiento", "CoronaSkill.spawnparticle-036-arg4", .14),Settings.d("equipamiento", "CoronaSkill.spawnparticle-037-arg5", .14),Settings.d("equipamiento", "CoronaSkill.spawnparticle-038-arg6", .01));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override public void clear(UUID playerId) {
        State state = states.remove(playerId);
        if (state != null) for (Skull skull : state.skulls) if (skull.visual.isValid()) skull.visual.remove();
        // Cooldowns survive unequipping, death and logoff while server stays online.
    }

    @Override public String status(Player player) {
        State s = states.get(player.getUniqueId());
        int shots = s == null ? 0 : s.shotsFired;
        return Settings.text("equipamiento", "CoronaSkill.etiqueta-002", "§3☠ Corona de Ánimas §7| set: §f") + hasSet(player) + Settings.text("equipamiento", "CoronaSkill.etiqueta-003", "§7 | disparos: §f")
                + shots + Settings.text("equipamiento", "CoronaSkill.etiqueta-004", "/9 §7| CD: §f") + plugin.cooldowns().remainingSeconds(player.getUniqueId(), id()) + "s";
    }
}
