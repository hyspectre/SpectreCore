package dev.hydev.spectrecore.skills;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.core.Effects;
import dev.hydev.spectrecore.core.CombatRules;
import dev.hydev.spectrecore.core.SkillModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sangre del Primer Dragón: automatically triggered universal blessing and temporary spirit. */
public final class PrimerDragonSkill implements SkillModule {
    private static final String SPEED_KEY = "primer_dragon_speed";
    private static final class State {
        long start, baseEnd, extendedEnd, lastHit = -100000;
        int meleeHits;
        UUID activation;
        boolean speedApplied;
        final List<BlockDisplay> dragon = new ArrayList<>();
    }
    private final SpectreCorePlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    public PrimerDragonSkill(SpectreCorePlugin plugin) { this.plugin = plugin; }
    @Override public String id() { return "sangre-primer-dragon"; }
    private int i(String key, int def) { return plugin.getConfig().getInt("skills.sangre-primer-dragon." + key, def); }
    private double d(String key, double def) { return plugin.getConfig().getDouble("skills.sangre-primer-dragon." + key, def); }
    @Override public boolean hasSet(Player p) { return plugin.sets().hasArmor(p,
            plugin.getConfig().getConfigurationSection("skills.sangre-primer-dragon.armor")); }
    private boolean access(Player p) { return plugin.hasAccess(p, id(), hasSet(p)); }
    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), k -> new State()); }
    private boolean withSword(Player p) { return plugin.sets().hasWeapon(p,
            plugin.getConfig().getString("skills.sangre-primer-dragon.weapon.type", "SWORD"),
            plugin.getConfig().getString("skills.sangre-primer-dragon.weapon.id", "DRAGON")); }
    private boolean active(Player p, State s, long tick) {
        return s != null && s.baseEnd > 0 && (tick < CombatRules.blessingEnd(s.baseEnd, s.extendedEnd, withSword(p)));
    }
    @Override public void onMelee(Player p, LivingEntity enemy, long tick) {
        if (!access(p)) return;
        State s = state(p);
        if (active(p, s, tick)) return;
        s.meleeHits = tick - s.lastHit <= i("melee-hit-window-ticks", 60) ? s.meleeHits + 1 : 1;
        s.lastHit = tick;
        if (s.meleeHits >= Math.max(1, i("melee-hits-to-activate", 2))) activate(p, s, tick);
    }
    @Override public void onHostileDamage(Player p, LivingEntity aggressor, long tick) {
        if (!access(p)) return;
        State s = state(p);
        if (!active(p, s, tick)) activate(p, s, tick);
    }
    private void activate(Player p, State s, long tick) {
        if (!plugin.cooldowns().start(p.getUniqueId(), id(), i("cooldown-seconds", 45))) return;
        s.start = tick;
        s.baseEnd = tick + Math.max(1, i("duration-seconds", 10)) * 20L;
        s.extendedEnd = s.baseEnd + Math.max(0, i("weapon-duration-bonus-seconds", 2)) * 20L;
        s.activation = UUID.randomUUID();
        s.meleeHits = 0;
        plugin.effects().setAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY, d("speed-bonus", .06),
                AttributeModifier.Operation.ADD_SCALAR);
        s.speedApplied = true;
        p.getWorld().playSound(p.getLocation(), Settings.string("equipamiento", "PrimerDragonSkill.sonido-003", "minecraft:entity.ender_dragon.flap"),Settings.f("equipamiento", "PrimerDragonSkill.playsound-001-arg2", .75f),Settings.f("equipamiento", "PrimerDragonSkill.playsound-002-arg3", 1.35f));
        p.getWorld().spawnParticle(Settings.particle("equipamiento", "PrimerDragonSkill.particle-001", Particle.END_ROD), p.getLocation().add(0, Settings.d("equipamiento", "PrimerDragonSkill.geometria-001", .6), 0),Settings.i("equipamiento", "PrimerDragonSkill.spawnparticle-003-arg2", 24),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-004-arg3", .5),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-005-arg4", .5),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-006-arg5", .5),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-007-arg6", .015));
        p.sendMessage(Settings.text("equipamiento", "PrimerDragonSkill.mensaje-001", "§6✦ §eSangre del Primer Dragón §7— La herencia despierta."));
        // The event invoking this may be before Minecraft has deducted the incoming hit.
        // Heal the *missing* HP on the following tick; never heal past max health.
        UUID owner = p.getUniqueId(), token = s.activation;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(owner);
            State current = states.get(owner);
            if (online == null || !online.isValid() || online.isDead() || current == null
                    || !token.equals(current.activation) || !access(online)) return;
            AttributeInstance max = online.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) online.setHealth(Math.min(max.getValue(), online.getHealth() + d("heal-on-activation", 4.0)));
        }, 1L);
        spawnDragon(p, s);
    }
    @Override public double outgoingMultiplier(Player p, LivingEntity target, long tick) {
        if (!access(p)) return 1;
        double bonus = d("passive-damage-bonus", .03);
        if (active(p, states.get(p.getUniqueId()), tick)) bonus += d("active-damage-bonus", .06);
        return 1 + bonus;
    }
    @Override public double incomingMultiplier(Player p, long tick) {
        if (!access(p)) return 1;
        double reduction = d("passive-damage-reduction", .03);
        if (active(p, states.get(p.getUniqueId()), tick)) reduction += d("active-damage-reduction", .06);
        return Math.max(.1, 1 - reduction);
    }

    private void spawnDragon(Player p, State s) {
        removeDragon(s);
        Effects e = plugin.effects();
        // Stylised spectral silhouette: golden head, six body/tail segments and two wings.
        for (int n = 0; n < 7; n++) {
            BlockDisplay b = e.block(p, n == 0 ? Settings.material("equipamiento", "PrimerDragonSkill.material-027", Material.GOLD_BLOCK) : Settings.material("equipamiento", "PrimerDragonSkill.material-028", Material.WAXED_CUT_COPPER),
                    n == 0 ? Settings.f("equipamiento", "PrimerDragonSkill.geometria-002", .24f) : (float) Math.max(.09, .21 - n * .018),
                    n == 0 ? Settings.f("equipamiento", "PrimerDragonSkill.geometria-003", .23f) : Settings.f("equipamiento", "PrimerDragonSkill.geometria-004", .14f), n == 0 ? Settings.f("equipamiento", "PrimerDragonSkill.geometria-005", .26f) : Settings.f("equipamiento", "PrimerDragonSkill.geometria-006", .14f));
            s.dragon.add(b);
        }
        for (int n = 0; n < 2; n++) s.dragon.add(e.block(p, Settings.material("equipamiento", "PrimerDragonSkill.material-029", Material.GOLD_BLOCK), Settings.f("equipamiento", "PrimerDragonSkill.geometria-007", .34f), Settings.f("equipamiento", "PrimerDragonSkill.geometria-008", .08f), Settings.f("equipamiento", "PrimerDragonSkill.geometria-009", .21f)));
    }
    private void removeDragon(State s) {
        for (BlockDisplay display : s.dragon) if (display.isValid()) display.remove();
        s.dragon.clear();
    }
    private void render(Player p, State s, long tick) {
        long age = tick - s.start;
        int duration = Math.max(8, i("dragon-animation-ticks", 32));
        if (age > duration) { removeDragon(s); return; }
        if (s.dragon.size() != 9) return;
        Location base = p.getLocation();
        double progress = Math.max(0, Math.min(1, age / (double) duration));
        double radius = Settings.d("equipamiento", "PrimerDragonSkill.geometria-010", .65) + Settings.d("equipamiento", "PrimerDragonSkill.geometria-011", .55) * Math.sin(progress * Math.PI);
        double angle = age * Settings.d("equipamiento", "PrimerDragonSkill.geometria-012", .22);
        for (int n = 0; n < 7; n++) {
            double phase = angle - n * Settings.d("equipamiento", "PrimerDragonSkill.geometria-013", .23);
            double height = Settings.d("equipamiento", "PrimerDragonSkill.geometria-014", .35) + progress * Settings.d("equipamiento", "PrimerDragonSkill.geometria-015", 1.42) - n * Settings.d("equipamiento", "PrimerDragonSkill.geometria-016", .09) + Math.sin(age * Settings.d("equipamiento", "PrimerDragonSkill.geometria-017", .12) - n * Settings.d("equipamiento", "PrimerDragonSkill.geometria-018", .25)) * Settings.d("equipamiento", "PrimerDragonSkill.geometria-019", .10);
            Effects.position(s.dragon.get(n), base.clone().add(Math.cos(phase) * radius, height,
                    Math.sin(phase) * radius));
        }
        Location head = s.dragon.get(0).getLocation();
        for (int wing = 0; wing < 2; wing++) {
            double side = wing == 0 ? -1 : 1;
            Effects.position(s.dragon.get(7 + wing), head.clone().add(-Math.sin(angle) * Settings.d("equipamiento", "PrimerDragonSkill.geometria-020", .40) * side,
                    Settings.d("equipamiento", "PrimerDragonSkill.geometria-021", .08) + Math.sin(age * Settings.d("equipamiento", "PrimerDragonSkill.geometria-022", .42)) * Settings.d("equipamiento", "PrimerDragonSkill.geometria-023", .12), Math.cos(angle) * Settings.d("equipamiento", "PrimerDragonSkill.geometria-024", .40) * side));
        }
        if (age % 3 == 0) {
            Effects.dust(head,Settings.i("equipamiento", "PrimerDragonSkill.polvo-001-arg1", 223),Settings.i("equipamiento", "PrimerDragonSkill.polvo-002-arg2", 186),Settings.i("equipamiento", "PrimerDragonSkill.polvo-003-arg3", 99),Settings.i("equipamiento", "PrimerDragonSkill.polvo-004-arg4", 5), Settings.f("equipamiento", "PrimerDragonSkill.geometria-025", .95f));
            p.getWorld().spawnParticle(Settings.particle("equipamiento", "PrimerDragonSkill.particle-002", Particle.END_ROD), head,Settings.i("equipamiento", "PrimerDragonSkill.spawnparticle-008-arg2", 2),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-009-arg3", .13),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-010-arg4", .10),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-011-arg5", .13),Settings.d("equipamiento", "PrimerDragonSkill.spawnparticle-012-arg6", .005));
        }
    }

    @Override public void tick(long tick) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            State s = states.get(id);
            if (s == null) continue;
            if (!p.isValid() || p.isDead() || !access(p)) { clear(id); continue; }
            if (!active(p, s, tick)) {
                if (s.speedApplied) {
                    plugin.effects().clearAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY);
                    s.speedApplied = false;
                }
                removeDragon(s);
                s.baseEnd = 0; s.extendedEnd = 0;
                continue;
            }
            render(p, s, tick);
            if (tick % 15 == 0) Effects.dust(p.getLocation().add(0, 1, 0),Settings.i("equipamiento", "PrimerDragonSkill.polvo-005-arg1", 230),Settings.i("equipamiento", "PrimerDragonSkill.polvo-006-arg2", 200),Settings.i("equipamiento", "PrimerDragonSkill.polvo-007-arg3", 134),Settings.i("equipamiento", "PrimerDragonSkill.polvo-008-arg4", 2), Settings.f("equipamiento", "PrimerDragonSkill.geometria-026", .65f));
        }
    }
    @Override public void clear(UUID id) {
        State s = states.remove(id);
        if (s == null) return;
        removeDragon(s);
        Player p = Bukkit.getPlayer(id);
        if (p != null && s.speedApplied) plugin.effects().clearAttribute(p, Attribute.MOVEMENT_SPEED, SPEED_KEY);
    }
    @Override public String status(Player p) {
        State s = states.get(p.getUniqueId());
        long tick = plugin.tick();
        return Settings.text("equipamiento", "PrimerDragonSkill.etiqueta-002", "§6✦ Sangre del Primer Dragón §7| set: §f") + hasSet(p)
                + Settings.text("equipamiento", "PrimerDragonSkill.etiqueta-003", " §7| bendición: §f") + active(p, s, tick)
                + Settings.text("equipamiento", "PrimerDragonSkill.etiqueta-004", " §7| CD: §f") + plugin.cooldowns().remainingSeconds(p.getUniqueId(), id()) + "s";
    }
}
