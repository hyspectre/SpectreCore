package dev.hydev.spectrecore.skills;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.core.Effects;
import dev.hydev.spectrecore.core.CombatRules;
import dev.hydev.spectrecore.core.AsterionComboRules;
import dev.hydev.spectrecore.core.SkillModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Asterión: fury accumulation, manual double-shift transformation, single-prey melee combo. */
public final class AsterionSkill implements SkillModule {
    private static final String RESISTANCE_KEY = "asterion_knockback";
    private static final class State {
        int fury, combo;
        long lastCombat = -100000, lastTap = -100000, lastHit = -100000, activeUntil;
        UUID prey;
        boolean resistant, readyNotice;
        long nextHudTick, lastNotice = -100000;
        BossBar hud;
        final List<BlockDisplay> parts = new ArrayList<>();
    }
    private final SpectreCorePlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    public AsterionSkill(SpectreCorePlugin plugin) { this.plugin = plugin; }
    @Override public String id() { return "asterion"; }
    private int i(String key, int def) { return plugin.getConfig().getInt("skills.asterion." + key, def); }
    private double d(String key, double def) { return plugin.getConfig().getDouble("skills.asterion." + key, def); }
    @Override public boolean hasSet(Player p) { return plugin.sets().hasArmor(p, plugin.getConfig().getConfigurationSection("skills.asterion.armor")); }
    private boolean access(Player p) { return plugin.hasAccess(p, id(), hasSet(p)); }
    private int furyCap() { return Settings.module("equipamiento/config.yml").getInt("furia-maxima",100); }
    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }
    private boolean active(State s, long tick) { return tick < s.activeUntil; }

    @Override public void onMelee(Player player, LivingEntity target, long tick) {
        if (!access(player)) return;
        State s = state(player);
        s.lastCombat = tick;
        if (!active(s, tick)) {
            s.fury = Math.min(furyCap(),Math.max(0,s.fury+Math.max(0,i("fury-on-hit",12))));
            updateHud(player, s, tick);
            return;
        }
        int previousCombo = s.combo;
        var combo = AsterionComboRules.landed(
                new AsterionComboRules.Combo(s.prey, s.combo, s.lastHit),
                target.getUniqueId(), tick, i("combo-gap-ticks", 50), i("max-combo-stacks", 4));
        s.prey = combo.prey();
        s.combo = combo.stacks();
        s.lastHit = combo.lastHit();
        updateHud(player, s, tick);
        if (s.combo > previousCombo && s.combo == i("max-combo-stacks", 4)) {
            Effects.dust(target.getLocation().add(0, 1, 0),Settings.i("equipamiento", "AsterionSkill.polvo-001-arg1", 162),Settings.i("equipamiento", "AsterionSkill.polvo-002-arg2", 45),Settings.i("equipamiento", "AsterionSkill.polvo-003-arg3", 40),Settings.i("equipamiento", "AsterionSkill.polvo-004-arg4", 4), Settings.f("equipamiento", "AsterionSkill.geometria-001", .75f));
        }
    }

    @Override public void onHostileDamage(Player player, LivingEntity aggressor, long tick) {
        if (!access(player)) return;
        State s = state(player);
        s.lastCombat = tick;
        if (!active(s, tick)) {
            s.fury = Math.min(furyCap(),Math.max(0,s.fury+Math.max(0,i("fury-on-damage",6))));
            updateHud(player, s, tick);
        }
    }

    @Override public double outgoingMultiplier(Player p, LivingEntity target, long tick) {
        if (!access(p)) return 1;
        State s = states.get(p.getUniqueId());
        if (s == null || !active(s, tick)) return 1;
        // onMelee runs after damage; include the imminent successful hit in the outgoing multiplier.
        return AsterionComboRules.multiplier(
                new AsterionComboRules.Combo(s.prey, s.combo, s.lastHit),
                target.getUniqueId(), tick, i("combo-gap-ticks", 50),
                i("max-combo-stacks", 4), d("damage-per-stack", .03));
    }

    @Override public void onSneakPress(Player p, long tick) {
        if (!access(p)) return;
        State s = state(p);
        boolean second = tick - s.lastTap >= 1 && tick - s.lastTap <= i("double-shift-window-ticks", 9);
        s.lastTap = tick;
        if (!second || active(s, tick)) return;
        if (s.fury < furyCap()) {
            notice(p, s, tick, Settings.message("equipamiento", "AsterionSkill.furia-insuficiente",
                    "<yellow>Furia insuficiente: {furia}/{maximo}", "furia", s.fury, "maximo", furyCap()));
            return;
        }
        if (!plugin.cooldowns().start(p.getUniqueId(), id(), i("cooldown-seconds", 50))) {
            notice(p, s, tick, Settings.message("equipamiento", "AsterionSkill.en-recarga",
                    "<yellow>Asterión · Recarga {s}s", "s", plugin.cooldowns().remainingSeconds(p.getUniqueId(), id())));
            return;
        }
        s.fury = 0;
        s.combo = 0;
        s.prey = null;
        s.lastHit = -100000;
        s.activeUntil = tick + Math.max(1, i("duration-seconds", 10)) * 20L;
        updateHud(p, s, tick);
        p.getWorld().playSound(p.getLocation(), Settings.string("equipamiento", "AsterionSkill.sonido-002", "minecraft:entity.ravager.roar"),Settings.f("equipamiento", "AsterionSkill.playsound-001-arg2", 0.85f),Settings.f("equipamiento", "AsterionSkill.playsound-002-arg3", 0.65f));
        p.getWorld().spawnParticle(Settings.particle("equipamiento", "AsterionSkill.particle-001", Particle.LARGE_SMOKE), p.getLocation().add(0, Settings.d("equipamiento", "AsterionSkill.geometria-002", .8), 0),Settings.i("equipamiento", "AsterionSkill.spawnparticle-003-arg2", 26),Settings.d("equipamiento", "AsterionSkill.spawnparticle-004-arg3", .65),Settings.d("equipamiento", "AsterionSkill.spawnparticle-005-arg4", .45),Settings.d("equipamiento", "AsterionSkill.spawnparticle-006-arg5", .65),Settings.d("equipamiento", "AsterionSkill.spawnparticle-007-arg6", .01));
        p.sendMessage(Settings.message("equipamiento", "AsterionSkill.mensaje-001", "§6✦ §cAsterión §7— La bestia despierta."));
    }

    private void notice(Player p, State s, long tick, Component message) {
        if (tick - s.lastNotice < 40) return;
        s.lastNotice = tick;
        p.sendMessage(message);
    }

    @Override public void tick(long tick) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!p.isValid() || p.isDead() || !access(p)) { clear(id); continue; }
            State s = state(p);
            if (!s.resistant) {
                plugin.effects().setAttribute(p, Attribute.KNOCKBACK_RESISTANCE, RESISTANCE_KEY,
                        d("passive-knockback-resistance", .10), AttributeModifier.Operation.ADD_NUMBER);
                s.resistant = true;
            }
            if (!active(s, tick) && s.activeUntil != 0) {
                s.activeUntil = 0; s.combo = 0; s.prey = null;
                p.getWorld().playSound(p.getLocation(), Settings.string("equipamiento", "AsterionSkill.sonido-003", "minecraft:block.anvil.land"),Settings.f("equipamiento", "AsterionSkill.playsound-008-arg2", .5f),Settings.f("equipamiento", "AsterionSkill.playsound-009-arg3", 1.25f));
            }
            if (active(s, tick) && s.prey != null) {
                var combo = AsterionComboRules.expire(
                        new AsterionComboRules.Combo(s.prey, s.combo, s.lastHit),
                        tick, i("combo-gap-ticks", 50));
                s.prey = combo.prey();
                s.combo = combo.stacks();
            }
            if (s.fury > 0 && !active(s, tick) && tick - s.lastCombat >= i("fury-decay-delay-ticks", 400)
                    && tick % Math.max(1, i("fury-decay-every-ticks", 10)) == 0) s.fury--;
            if (s.parts.isEmpty() || s.parts.stream().anyMatch(part -> !part.isValid())) {
                removeParts(s); spawnParts(p, s);
            }
            render(p, s, active(s, tick), tick);
            if (tick >= s.nextHudTick) updateHud(p, s, tick);
        }
    }

    /**
     * A single compact native bar: fury before transformation, actual combo
     * during it. No actionbar, extra repeating task or per-hit display entity.
     */
    private void updateHud(Player player, State s, long tick) {
        s.nextHudTick = tick + 10;
        boolean transformed = active(s, tick);
        int furyMax = Math.max(1, furyCap());
        long cooldown = plugin.cooldowns().remainingSeconds(player.getUniqueId(), id());
        boolean ready = !transformed && s.fury >= furyMax && cooldown == 0;
        if (ready && !s.readyNotice && plugin.toasts() != null)
            plugin.toasts().show(player, "furia");
        s.readyNotice = ready;
        if (!transformed && s.fury == 0 && cooldown == 0) {
            hideHud(player, s);
            return;
        }

        Component title;
        float progress;
        BossBar.Color color;
        if (transformed) {
            int max = Math.max(0, i("max-combo-stacks", 4));
            int combo = Math.clamp(s.combo, 0, max);
            long seconds = Math.max(0, (s.activeUntil - tick + 19) / 20);
            Component detail;
            if (s.prey == null)
                detail = Settings.message("equipamiento", "AsterionSkill.hud-primer-golpe",
                        "<gray>Golpea a un enemigo");
            else if (combo == 0)
                detail = Settings.message("equipamiento", "AsterionSkill.hud-mismo-enemigo",
                        "<gray>Mantén el objetivo");
            else {
                double percent = AsterionComboRules.earnedBonusPercent(combo, max, d("damage-per-stack", .03));
                String bonus = java.math.BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
                detail = Settings.message("equipamiento", "AsterionSkill.hud-dano",
                        "<green>+{dano}% daño <gray>· Mismo objetivo", "dano", bonus);
            }
            title = Settings.message("equipamiento", "AsterionSkill.hud-impetu",
                    "<#E3BC7B>✦ <bold>Ímpetu</bold> <white>{combo}/{maximo} <dark_gray>│ {estado} <gray>· <white>{s}s",
                    "combo", combo, "maximo", max, "estado", detail, "s", seconds);
            progress = combo / (float) Math.max(1, max);
            color = BossBar.Color.RED;
        } else {
            Component detail = cooldown > 0
                    ? Settings.message("equipamiento", "AsterionSkill.hud-recarga", "<yellow>Recarga {s}s", "s", cooldown)
                    : ready ? Settings.message("equipamiento", "AsterionSkill.hud-lista", "<green>Doble Shift")
                    : Settings.message("equipamiento", "AsterionSkill.hud-cargando", "<gray>Golpea o recibe daño");
            title = Settings.message("equipamiento", "AsterionSkill.hud-furia",
                    "<#E3BC7B>✦ <bold>Furia</bold> <white>{furia}/{maximo} <dark_gray>│ {estado}",
                    "furia", s.fury, "maximo", furyMax, "estado", detail);
            progress = Math.clamp(s.fury / (float) furyMax, 0f, 1f);
            color = ready ? BossBar.Color.YELLOW : BossBar.Color.RED;
        }
        if (s.hud == null) {
            s.hud = BossBar.bossBar(title, progress, color, BossBar.Overlay.PROGRESS);
            player.showBossBar(s.hud);
        } else {
            s.hud.name(title);
            s.hud.progress(progress);
            s.hud.color(color);
        }
    }

    private void hideHud(Player player, State s) {
        if (s.hud == null) return;
        if (player != null) player.hideBossBar(s.hud);
        s.hud = null;
    }

    private void spawnParts(Player p, State s) {
        Effects effects = plugin.effects();
        for (int j = 0; j < 4; j++) s.parts.add(effects.block(p, Settings.material("equipamiento", "AsterionSkill.material-041", Material.CALCITE), Settings.f("equipamiento", "AsterionSkill.geometria-003", .11f), Settings.f("equipamiento", "AsterionSkill.geometria-004", .16f), Settings.f("equipamiento", "AsterionSkill.geometria-005", .11f)));
        s.parts.add(effects.block(p, Settings.material("equipamiento", "AsterionSkill.material-042", Material.CUT_COPPER), Settings.f("equipamiento", "AsterionSkill.geometria-006", .19f), Settings.f("equipamiento", "AsterionSkill.geometria-007", .13f), Settings.f("equipamiento", "AsterionSkill.geometria-008", .22f)));
        s.parts.add(effects.block(p, Settings.material("equipamiento", "AsterionSkill.material-043", Material.CUT_COPPER), Settings.f("equipamiento", "AsterionSkill.geometria-009", .19f), Settings.f("equipamiento", "AsterionSkill.geometria-010", .13f), Settings.f("equipamiento", "AsterionSkill.geometria-011", .22f)));
    }
    private void render(Player p, State s, boolean transformed, long tick) {
        if (s.parts.size() != 6) return;
        double growth = transformed ? 1.0 : Settings.d("equipamiento", "AsterionSkill.geometria-012", 0.48);
        for (int side = -1; side <= 1; side += 2) {
            int index = side == -1 ? 0 : 2;
            BlockDisplay lower = s.parts.get(index);
            BlockDisplay tip = s.parts.get(index + 1);
            Effects.scale(lower, Settings.f("equipamiento", "AsterionSkill.geometria-013", .12f), (float)(Settings.d("equipamiento", "AsterionSkill.geometria-014", .21) * growth), Settings.f("equipamiento", "AsterionSkill.geometria-015", .12f));
            Effects.scale(tip, Settings.f("equipamiento", "AsterionSkill.geometria-016", .085f), (float)(Settings.d("equipamiento", "AsterionSkill.geometria-017", .21) * growth), Settings.f("equipamiento", "AsterionSkill.geometria-018", .085f));
            Effects.position(lower, Effects.relative(p, side * Settings.d("equipamiento", "AsterionSkill.geometria-019", .22), Settings.d("equipamiento", "AsterionSkill.geometria-020", .02), (p.isSneaking()?Settings.d("equipamiento", "AsterionSkill.geometria-021", 1.35):Settings.d("equipamiento", "AsterionSkill.geometria-022", 1.79)) + Settings.d("equipamiento", "AsterionSkill.geometria-023", .10) * growth));
            Effects.position(tip, Effects.relative(p, side * (Settings.d("equipamiento", "AsterionSkill.geometria-024", .22) + Settings.d("equipamiento", "AsterionSkill.geometria-025", .10) * growth), Settings.d("equipamiento", "AsterionSkill.geometria-026", .025),
                    (p.isSneaking()?Settings.d("equipamiento", "AsterionSkill.geometria-027", 1.35):Settings.d("equipamiento", "AsterionSkill.geometria-028", 1.79)) + Settings.d("equipamiento", "AsterionSkill.geometria-029", .31) * growth));
            BlockDisplay shoulder = s.parts.get(side == -1 ? 4 : 5);
            Effects.scale(shoulder, transformed ? Settings.f("equipamiento", "AsterionSkill.geometria-030", .28f) : Settings.f("equipamiento", "AsterionSkill.geometria-031", .18f), transformed ? Settings.f("equipamiento", "AsterionSkill.geometria-032", .21f) : Settings.f("equipamiento", "AsterionSkill.geometria-033", .13f), Settings.f("equipamiento", "AsterionSkill.geometria-034", .23f));
            Effects.position(shoulder, Effects.relative(p, side * Settings.d("equipamiento", "AsterionSkill.geometria-035", .37), -Settings.d("equipamiento", "AsterionSkill.geometria-036", .02), p.isSneaking()?Settings.d("equipamiento", "AsterionSkill.geometria-037", 1.10):Settings.d("equipamiento", "AsterionSkill.geometria-038", 1.48)));
        }
        if (transformed && tick % 8 == 0) Effects.dust(p.getLocation().add(0, Settings.d("equipamiento", "AsterionSkill.geometria-039", 1.1), 0),Settings.i("equipamiento", "AsterionSkill.polvo-005-arg1", 148),Settings.i("equipamiento", "AsterionSkill.polvo-006-arg2", 28),Settings.i("equipamiento", "AsterionSkill.polvo-007-arg3", 32),Settings.i("equipamiento", "AsterionSkill.polvo-008-arg4", 3), Settings.f("equipamiento", "AsterionSkill.geometria-040", .75f));
    }
    private void removeParts(State s) {
        for (BlockDisplay part : s.parts) if (part.isValid()) part.remove();
        s.parts.clear();
    }
    @Override public void clear(UUID id) {
        State s = states.remove(id);
        if (s == null) return;
        removeParts(s);
        Player p = Bukkit.getPlayer(id);
        hideHud(p, s);
        if (p != null && s.resistant) plugin.effects().clearAttribute(p, Attribute.KNOCKBACK_RESISTANCE, RESISTANCE_KEY);
    }
    @Override public String status(Player p) {
        State s = states.get(p.getUniqueId());
        return Settings.text("equipamiento", "AsterionSkill.etiqueta-005", "§6♜ Asterión §7| set: §f") + hasSet(p) + Settings.text("equipamiento", "AsterionSkill.etiqueta-006", " §7| furia: §f") + (s == null ? 0 : s.fury)
                + Settings.text("equipamiento", "AsterionSkill.etiqueta-007", "/{maximo} §7| activo: §f","maximo",furyCap()) + (s != null && active(s, plugin.tick())) + Settings.text("equipamiento", "AsterionSkill.etiqueta-008", " §7| CD: §f")
                + plugin.cooldowns().remainingSeconds(p.getUniqueId(), id()) + "s";
    }
}
