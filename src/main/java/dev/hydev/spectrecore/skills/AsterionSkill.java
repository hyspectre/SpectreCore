package dev.hydev.spectrecore.skills;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.core.Effects;
import dev.hydev.spectrecore.core.CombatRules;
import dev.hydev.spectrecore.core.AsterionComboRules;
import dev.hydev.spectrecore.core.SkillModule;
import net.kyori.adventure.text.Component;
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
        boolean resistant;
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
            int before = s.fury;
            s.fury = Math.min(furyCap(),Math.max(0,s.fury+Math.max(0,i("fury-on-hit",12))));
            if (before < furyCap() && s.fury >= furyCap() && plugin.toasts() != null)
                plugin.toasts().show(player, "furia");
            return;
        }
        var combo = AsterionComboRules.landed(
                new AsterionComboRules.Combo(s.prey, s.combo, s.lastHit),
                target.getUniqueId(), tick, i("combo-gap-ticks", 50), i("max-combo-stacks", 4));
        s.prey = combo.prey();
        s.combo = combo.stacks();
        s.lastHit = combo.lastHit();
        if (s.combo == i("max-combo-stacks", 4)) {
            Effects.dust(target.getLocation().add(0, 1, 0),Settings.i("equipamiento", "AsterionSkill.polvo-001-arg1", 162),Settings.i("equipamiento", "AsterionSkill.polvo-002-arg2", 45),Settings.i("equipamiento", "AsterionSkill.polvo-003-arg3", 40),Settings.i("equipamiento", "AsterionSkill.polvo-004-arg4", 4), Settings.f("equipamiento", "AsterionSkill.geometria-001", .75f));
        }
    }

    @Override public void onHostileDamage(Player player, LivingEntity aggressor, long tick) {
        if (!access(player)) return;
        State s = state(player);
        s.lastCombat = tick;
        if (!active(s, tick)) {
            int before = s.fury;
            s.fury = Math.min(furyCap(),Math.max(0,s.fury+Math.max(0,i("fury-on-damage",6))));
            if (before < furyCap() && s.fury >= furyCap() && plugin.toasts() != null)
                plugin.toasts().show(player, "furia");
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
            p.sendActionBar(Settings.message("equipamiento", "AsterionSkill.furia-insuficiente",
                    "<yellow>Furia insuficiente: {furia}/{maximo}", "furia", s.fury, "maximo", furyCap()));
            return;
        }
        if (!plugin.cooldowns().start(p.getUniqueId(), id(), i("cooldown-seconds", 50))) {
            p.sendActionBar(Settings.message("equipamiento", "AsterionSkill.en-recarga",
                    "<yellow>Asterión · Recarga {s}s", "s", plugin.cooldowns().remainingSeconds(p.getUniqueId(), id())));
            return;
        }
        s.fury = 0;
        s.combo = 0;
        s.prey = null;
        s.lastHit = -100000;
        s.activeUntil = tick + Math.max(1, i("duration-seconds", 10)) * 20L;
        p.getWorld().playSound(p.getLocation(), Settings.string("equipamiento", "AsterionSkill.sonido-002", "minecraft:entity.ravager.roar"),Settings.f("equipamiento", "AsterionSkill.playsound-001-arg2", 0.85f),Settings.f("equipamiento", "AsterionSkill.playsound-002-arg3", 0.65f));
        p.getWorld().spawnParticle(Settings.particle("equipamiento", "AsterionSkill.particle-001", Particle.LARGE_SMOKE), p.getLocation().add(0, Settings.d("equipamiento", "AsterionSkill.geometria-002", .8), 0),Settings.i("equipamiento", "AsterionSkill.spawnparticle-003-arg2", 26),Settings.d("equipamiento", "AsterionSkill.spawnparticle-004-arg3", .65),Settings.d("equipamiento", "AsterionSkill.spawnparticle-005-arg4", .45),Settings.d("equipamiento", "AsterionSkill.spawnparticle-006-arg5", .65),Settings.d("equipamiento", "AsterionSkill.spawnparticle-007-arg6", .01));
        p.sendMessage(Settings.message("equipamiento", "AsterionSkill.mensaje-001", "§6✦ §cAsterión §7— La bestia despierta."));
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
            if (plugin.getConfig().getBoolean("skills.asterion.show-fury-actionbar", true) && tick % 20 == 0) {
                long remaining = plugin.cooldowns().remainingSeconds(id, id());
                String notice = remaining > 0
                        ? Settings.text("equipamiento", "AsterionSkill.furia-recarga", " | Recarga {s}s", "s", remaining)
                        : s.fury >= furyCap() ? Settings.text("equipamiento","AsterionSkill.furia-lista"," | DOBLE SHIFT") : "";
                String info = active(s,tick) ? Settings.text("equipamiento","AsterionSkill.barra-activa","✦ ASTERIÓN | {segundos}s | Ímpetu {combo}/{maximo}","segundos",Math.max(0,(s.activeUntil-tick+19)/20),"combo",s.combo,"maximo",i("max-combo-stacks",4)) : Settings.text("equipamiento","AsterionSkill.barra-furia","✦ FURIA | {furia}/{maximo}{aviso}","furia",s.fury,"maximo",furyCap(),"aviso",notice);
                p.sendActionBar(Settings.component(info));
            }
        }
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
        if (p != null && s.resistant) plugin.effects().clearAttribute(p, Attribute.KNOCKBACK_RESISTANCE, RESISTANCE_KEY);
    }
    @Override public String status(Player p) {
        State s = states.get(p.getUniqueId());
        return Settings.text("equipamiento", "AsterionSkill.etiqueta-005", "§6♜ Asterión §7| set: §f") + hasSet(p) + Settings.text("equipamiento", "AsterionSkill.etiqueta-006", " §7| furia: §f") + (s == null ? 0 : s.fury)
                + Settings.text("equipamiento", "AsterionSkill.etiqueta-007", "/{maximo} §7| activo: §f","maximo",furyCap()) + (s != null && active(s, plugin.tick())) + Settings.text("equipamiento", "AsterionSkill.etiqueta-008", " §7| CD: §f")
                + plugin.cooldowns().remainingSeconds(p.getUniqueId(), id()) + "s";
    }
}
