package dev.hydev.spectrecore.skills;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.core.SkillHud;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.core.Effects;
import dev.hydev.spectrecore.core.CombatRules;
import dev.hydev.spectrecore.core.SkillModule;
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

/** Último Juramento: temporary glass-cannon with mobile floating plates and verified Blink synergy. */
public final class UltimoJuramentoSkill implements SkillModule {
    private static final String SPEED_KEY = "ultimo_juramento_speed";
    private static final String STRENGTH_KEY = "spectrecore_ultimo_juramento_strength";
    private static final class State {
        final SkillHud hud = new SkillHud();
        long lastTap = -100000, startTick, activeUntil, maxUntil;
        long blinkCandidate = -100000, blinkBonusUntil, lastBlink, lastNotice = -100000;
        boolean speedApplied;
        final List<BlockDisplay> plates = new ArrayList<>();
    }
    private final SpectreCorePlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    public UltimoJuramentoSkill(SpectreCorePlugin plugin) { this.plugin = plugin; }
    @Override public String id() { return "ultimo-juramento"; }
    private int i(String key, int def) { return plugin.getConfig().getInt("skills.ultimo-juramento." + key, def); }
    private double d(String key, double def) { return plugin.getConfig().getDouble("skills.ultimo-juramento." + key, def); }
    @Override public boolean hasSet(Player p) { return plugin.sets().hasArmor(p, plugin.getConfig().getConfigurationSection("skills.ultimo-juramento.armor")); }
    private boolean access(Player p) { return plugin.hasAccess(p, id(), hasSet(p)); }
    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), x -> new State()); }
    private boolean active(State s, long tick) { return tick < s.activeUntil; }
    private boolean withSword(Player p) { return plugin.sets().hasWeapon(p,
            plugin.getConfig().getString("skills.ultimo-juramento.weapon.type", "SWORD"),
            plugin.getConfig().getString("skills.ultimo-juramento.weapon.id", "JURAMENTO_PROFANADO")); }

    @Override public void onSneakPress(Player p, long tick) {
        if (!access(p)) return;
        State s = state(p);
        boolean isDouble = tick - s.lastTap >= 1 && tick - s.lastTap <= i("double-shift-window-ticks", 9);
        s.lastTap = tick;
        if (!isDouble || active(s, tick)) return;
        if (!plugin.cooldowns().start(p.getUniqueId(), id(), i("cooldown-seconds", 40))) {
            if (tick - s.lastNotice >= 40) {
                s.lastNotice = tick;
                p.sendMessage(Settings.message("equipamiento", "UltimoJuramentoSkill.en-recarga",
                        "<yellow>Último Juramento · Recarga {s}s", "s", plugin.cooldowns().remainingSeconds(p.getUniqueId(), id())));
            }
            return;
        }
        s.startTick = tick;
        s.activeUntil = tick + i("duration-seconds", 7) * 20L;
        s.maxUntil = tick + i("maximum-duration-seconds", 10) * 20L;
        var aura = plugin.auraSkillsBridge();
        if (aura != null && plugin.getConfig().getBoolean("skills.ultimo-juramento.auraskills.enabled", true))
            aura.applyStrength(p, STRENGTH_KEY, d("auraskills.strength-bonus", 45.0),
                    Math.max(2, i("maximum-duration-seconds", 10) + 3));
        plugin.effects().setAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY, d("speed-bonus", .20), AttributeModifier.Operation.ADD_SCALAR);
        s.speedApplied = true;
        updateHud(p, s, tick);
        p.getWorld().playSound(p.getLocation(), Settings.string("equipamiento", "UltimoJuramentoSkill.sonido-005", "minecraft:block.anvil.break"),Settings.f("equipamiento", "UltimoJuramentoSkill.playsound-001-arg2", .65f),Settings.f("equipamiento", "UltimoJuramentoSkill.playsound-002-arg3", .72f));
        p.getWorld().spawnParticle(Settings.particle("equipamiento", "UltimoJuramentoSkill.particle-001", Particle.REVERSE_PORTAL), p.getLocation().add(0, 1.0, 0),Settings.i("equipamiento", "UltimoJuramentoSkill.spawnparticle-003-arg2", 26),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-004-arg3", .5),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-005-arg4", .7),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-006-arg5", .5),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-007-arg6", .02));
        p.sendMessage(Settings.message("equipamiento", "UltimoJuramentoSkill.mensaje-001", "§5✦ §dÚltimo Juramento §7— El acero deja de protegerte."));
    }

    @Override public double outgoingMultiplier(Player p, LivingEntity target, long tick) {
        if (!access(p)) return 1;
        State s = states.get(p.getUniqueId());
        if (s == null || !active(s, tick)) return 1;
        var aura = plugin.auraSkillsBridge();
        boolean strengthApplied = aura != null && aura.isApplied(p, STRENGTH_KEY);
        double blinkBonus = s.blinkBonusUntil >= tick && withSword(p) ? d("blink-next-hit-bonus", .10) : 0;
        return CombatRules.oathMultiplier(strengthApplied, d("melee-damage-bonus", .15), blinkBonus);
    }
    @Override public double incomingMultiplier(Player p, long tick) {
        State s = states.get(p.getUniqueId());
        return access(p) && s != null && active(s, tick) ? 1 + d("damage-taken-increase", .20) : 1;
    }
    @Override public void onMelee(Player p, LivingEntity enemy, long tick) {
        if (!access(p)) return;
        State s = states.get(p.getUniqueId());
        if (s == null || !active(s, tick)) return;
        s.activeUntil = CombatRules.extend(s.activeUntil, s.maxUntil, i("extension-per-hit-ticks", 12));
        if (s.blinkBonusUntil >= tick) {
            s.blinkBonusUntil = 0; // one hit only, even if the target resisted damage later
            p.getWorld().spawnParticle(Settings.particle("equipamiento", "UltimoJuramentoSkill.particle-002", Particle.REVERSE_PORTAL), enemy.getLocation().add(0, 1, 0),Settings.i("equipamiento", "UltimoJuramentoSkill.spawnparticle-008-arg2", 14),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-009-arg3", .18),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-010-arg4", .3),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-011-arg5", .18),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-012-arg6", .01));
        }
        updateHud(p, s, tick);
    }

    private void updateHud(Player p, State s, long tick) {
        s.hud.updated(tick);
        if (!active(s, tick)) { s.hud.hide(p.getUniqueId()); return; }
        Component detail;
        if (s.blinkBonusUntil >= tick && s.blinkBonusUntil > 0 && withSword(p)) {
            detail = Settings.message("equipamiento", "UltimoJuramentoSkill.hud-eco",
                    "<light_purple>Eco: +{dano}% próximo golpe <gray>({s}s)",
                    "dano", Math.round(d("blink-next-hit-bonus", .10) * 10000) / 100.0,
                    "s", SkillHud.seconds(Math.min(s.blinkBonusUntil, s.activeUntil) - tick));
        } else if (s.activeUntil >= s.maxUntil)
            detail = Settings.message("equipamiento", "UltimoJuramentoSkill.hud-limite", "<gray>Duración máxima");
        else detail = Settings.message("equipamiento", "UltimoJuramentoSkill.hud-extiende", "<gray>Golpea para prolongar");
        s.hud.show(p, Settings.message("equipamiento", "UltimoJuramentoSkill.hud-activo",
                "<#C6A1E0>✦ <bold>Juramento</bold> <white>{s}s <gray>│ {estado}",
                "s", SkillHud.seconds(s.activeUntil - tick), "estado", detail),
                SkillHud.progress(s.activeUntil - tick, s.maxUntil - s.startTick), BossBar.Color.PURPLE);
    }

    /** Only right-clicking the real sword during the active form can arm a Blink candidate. */
    public void markRightClick(Player p, long tick) {
        if (!access(p) || !withSword(p)) return;
        State s = states.get(p.getUniqueId());
        if (s != null && active(s, tick)) s.blinkCandidate = tick;
    }
    /** Confirm by a short plugin-caused teleport. Does not count a random teleport as Blink. */
    public void confirmTeleport(Player p, Location from, Location to, long tick) {
        State s = states.get(p.getUniqueId());
        if (s == null || !active(s, tick) || !withSword(p) || tick - s.blinkCandidate > 6) return;
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > Math.pow(d("blink-max-range", 18), 2)) return;
        if (from.distanceSquared(to) < Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-001", 0.5)) return;
        s.blinkCandidate = -100000;
        signalBlink(p, from, tick);
    }
    /** Optional explicit console bridge if a particular MMOItems Blink build skips teleport events. */
    public void signalBlink(Player p, Location previous, long tick) {
        State s = states.get(p.getUniqueId());
        if (s == null || !access(p) || !active(s, tick) || !withSword(p)) return;
        if (tick - s.lastBlink < 2) return;
        s.lastBlink = tick;
        s.blinkBonusUntil = tick + i("blink-next-hit-window-ticks", 50);
        updateHud(p, s, tick);
        spawnEcho(p, previous);
    }
    private void spawnEcho(Player p, Location from) {
        for (double offset : new double[] {Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-002", 0.38), 1.0, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-003", 1.55)}) {
            Location at = from.clone().add(0, offset, 0);
            p.getWorld().spawnParticle(Settings.particle("equipamiento", "UltimoJuramentoSkill.particle-003", Particle.REVERSE_PORTAL), at,Settings.i("equipamiento", "UltimoJuramentoSkill.spawnparticle-013-arg2", 12),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-014-arg3", .27),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-015-arg4", .18),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-016-arg5", .25),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-017-arg6", .01));
            Effects.dust(at,Settings.i("equipamiento", "UltimoJuramentoSkill.polvo-001-arg1", 143),Settings.i("equipamiento", "UltimoJuramentoSkill.polvo-002-arg2", 124),Settings.i("equipamiento", "UltimoJuramentoSkill.polvo-003-arg3", 182),Settings.i("equipamiento", "UltimoJuramentoSkill.polvo-004-arg4", 5), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-004", .90f));
        }
        // Three short-lived spectral armor plates, not a second combat summon.
        List<BlockDisplay> echo = new ArrayList<>();
        for (int n = 0; n < 3; n++) {
            BlockDisplay piece = plugin.effects().block(p, n == 1 ? Settings.material("equipamiento", "UltimoJuramentoSkill.material-034", Material.AMETHYST_BLOCK) : Settings.material("equipamiento", "UltimoJuramentoSkill.material-035", Material.POLISHED_BLACKSTONE),
                    n == 1 ? Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-005", .39f) : Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-006", .27f), n == 1 ? Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-007", .46f) : Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-008", .25f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-009", .18f));
            Effects.position(piece, from.clone().add(0, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-010", .38) + Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-011", .57) * n, 0));
            echo.add(piece);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (BlockDisplay display : echo) if (display.isValid()) display.remove();
        }, 12L);
    }

    @Override public void tick(long tick) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            State s = states.get(id);
            if (s == null) continue;
            if (!p.isValid() || p.isDead() || !access(p)) { clear(id); continue; }
            if (s.hud.due(tick)) updateHud(p, s, tick);
            if (!active(s, tick)) {
                s.hud.hide(id);
                if (plugin.auraSkillsBridge() != null) plugin.auraSkillsBridge().remove(p, STRENGTH_KEY);
                if (s.speedApplied) {
                    plugin.effects().clearAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY);
                    s.speedApplied = false;
                }
                removePlates(s);
                continue;
            }
            if (s.plates.size() != 4 || s.plates.stream().anyMatch(v -> !v.isValid())) {
                removePlates(s); spawnPlates(p, s);
            }
            positionPlates(p, s, tick);
            if (tick % 6 == 0) p.getWorld().spawnParticle(Settings.particle("equipamiento", "UltimoJuramentoSkill.particle-004", Particle.REVERSE_PORTAL), p.getLocation().add(0, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-012", 1.1), 0),Settings.i("equipamiento", "UltimoJuramentoSkill.spawnparticle-018-arg2", 3),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-019-arg3", .37),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-020-arg4", .45),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-021-arg5", .37),Settings.d("equipamiento", "UltimoJuramentoSkill.spawnparticle-022-arg6", .005));
            if (s.blinkBonusUntil != 0 && tick > s.blinkBonusUntil) s.blinkBonusUntil = 0;
        }
    }
    private void spawnPlates(Player p, State s) {
        Effects e = plugin.effects();
        for (int n = 0; n < 2; n++) s.plates.add(e.block(p, Settings.material("equipamiento", "UltimoJuramentoSkill.material-036", Material.POLISHED_BLACKSTONE), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-013", .22f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-014", .24f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-015", .30f)));
        s.plates.add(e.block(p, Settings.material("equipamiento", "UltimoJuramentoSkill.material-037", Material.NETHERITE_BLOCK), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-016", .30f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-017", .35f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-018", .16f)));
        s.plates.add(e.block(p, Settings.material("equipamiento", "UltimoJuramentoSkill.material-038", Material.AMETHYST_BLOCK), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-019", .16f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-020", .15f), Settings.f("equipamiento", "UltimoJuramentoSkill.geometria-021", .12f)));
    }
    private void positionPlates(Player p, State s, long tick) {
        double wave = Math.sin(tick * Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-022", .13)) * Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-023", .045);
        double baseY = p.isSneaking() ? Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-024", 1.1) : Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-025", 1.45);
        Effects.position(s.plates.get(0), Effects.relative(p, -Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-026", .45) - wave, -Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-027", .06), baseY));
        Effects.position(s.plates.get(1), Effects.relative(p, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-028", .45) + wave, -Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-029", .06), baseY));
        Effects.position(s.plates.get(2), Effects.relative(p, 0, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-030", .24) + wave, baseY - Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-031", .30)));
        Effects.position(s.plates.get(3), Effects.relative(p, 0, Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-032", .30) + wave, baseY - Settings.d("equipamiento", "UltimoJuramentoSkill.geometria-033", .32)));
    }
    private void removePlates(State s) {
        for (BlockDisplay b : s.plates) if (b.isValid()) b.remove();
        s.plates.clear();
    }
    @Override public void clear(UUID id) {
        State s = states.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null && plugin.auraSkillsBridge() != null) plugin.auraSkillsBridge().remove(p, STRENGTH_KEY);
        if (s == null) return;
        s.hud.hide(id);
        removePlates(s);
        if (p != null && s.speedApplied) plugin.effects().clearAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY);
    }
    @Override public String status(Player p) {
        State s = states.get(p.getUniqueId());
        long tick = plugin.tick();
        return Settings.text("equipamiento", "UltimoJuramentoSkill.etiqueta-002", "§5✦ Último Juramento §7| set: §f") + hasSet(p)
                + Settings.text("equipamiento", "UltimoJuramentoSkill.etiqueta-003", " §7| activo: §f") + (s != null && active(s, tick))
                + Settings.text("equipamiento", "UltimoJuramentoSkill.etiqueta-004", " §7| restante: §f") + (s == null ? 0 : Math.max(0, (s.activeUntil - tick + 19) / 20))
                + Settings.text("equipamiento", "UltimoJuramentoSkill.etiqueta-005", "s §7| CD: §f") + plugin.cooldowns().remainingSeconds(p.getUniqueId(), id()) + "s";
    }
}
