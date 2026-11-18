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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Migration of the original HydevBotania prototype into the shared engine. */
public final class BotaniaSkill implements SkillModule {
    private enum Kind { STEM, BUD, PETAL, HEART }
    private record Piece(BlockDisplay display, int shoulder, Kind kind, int petalIndex) { }
    private static final class Roses {
        World world;
        boolean opened;
        boolean initialized;
        final List<Piece> parts = new ArrayList<>();
        Roses(World world) { this.world = world; }
    }
    private static final class State {
        int hits;
        long lastHitTick;
        long activeUntil;
        UUID lastTarget;
        UUID activationId;
    }
    private final SpectreCorePlugin plugin;
    private final Map<UUID, Roses> displays = new HashMap<>();
    private final Map<UUID, State> states = new HashMap<>();

    public BotaniaSkill(SpectreCorePlugin plugin) { this.plugin = plugin; }
    @Override public String id() { return "botania"; }
    private ConfigurationSection cfg() { return plugin.getConfig().getConfigurationSection("skills.botania"); }
    private int getInt(String key, int def) { return plugin.getConfig().getInt("skills.botania." + key, def); }
    private double getDouble(String key, double def) { return plugin.getConfig().getDouble("skills.botania." + key, def); }
    private boolean access(Player player) { return plugin.hasAccess(player, id(), hasSet(player)); }
    @Override public boolean hasSet(Player player) {
        return plugin.sets().hasArmor(player, plugin.getConfig().getConfigurationSection("skills.botania.armor"));
    }
    private State state(UUID id) { return states.computeIfAbsent(id, ignored -> new State()); }

    @Override public void onMelee(Player player, LivingEntity mob, long engineTick) {
        if (!access(player)) return;
        State s = state(player.getUniqueId());
        s.lastTarget = mob.getUniqueId();
        if (engineTick - s.lastHitTick > getInt("hit-window-ticks", 60)) s.hits = 0;
        s.lastHitTick = engineTick;
        if (System.currentTimeMillis() < s.activeUntil || !plugin.cooldowns().ready(player.getUniqueId(), id())) return;
        if (++s.hits >= getInt("hits-required", 4)) {
            s.hits = 0;
            activate(player, s);
        }
    }

    private void activate(Player player, State s) {
        long duration = getInt("duration-seconds", 8);
        if (!plugin.cooldowns().start(player.getUniqueId(), id(), getInt("cooldown-seconds", 30))) return;
        s.activeUntil = System.currentTimeMillis() + duration * 1000L;
        UUID token = UUID.randomUUID();
        s.activationId = token;
        player.getWorld().playSound(player.getLocation(), Settings.string("equipamiento", "BotaniaSkill.sonido-005", "minecraft:block.amethyst_block.chime"),Settings.f("equipamiento", "BotaniaSkill.playsound-001-arg2", 0.75f),Settings.f("equipamiento", "BotaniaSkill.playsound-002-arg3", 1.5f));
        player.sendMessage(Settings.message("equipamiento", "BotaniaSkill.mensaje-001", "§d✿ §aBotania §7— Las Rosas Gemelas despiertan."));
        int count = Math.min(8, Math.max(1, getInt("lash-count", 4)));
        long first = Math.max(1, getInt("lash-first-delay-ticks", 20));
        int interval = Math.max(1, getInt("lash-interval-ticks", 24));
        UUID playerId = player.getUniqueId();
        for (int i = 0; i < count; i++) {
            final int side = i % 2 == 0 ? -1 : 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> lash(playerId, token, side), first + (long) interval * i);
        }
    }

    private void lash(UUID id, UUID token, int side) {
        Player player = Bukkit.getPlayer(id);
        State state = states.get(id);
        if (player == null || state == null || !token.equals(state.activationId)
                || !access(player) || System.currentTimeMillis() >= state.activeUntil) return;
        double range = getDouble("lash-range", 7.0);
        LivingEntity target = plugin.targeting().valid(player, state.lastTarget, range);
        if (target == null) target = plugin.targeting().nearest(player, range);
        if (target == null) return;
        Vector forward = horizontalForward(player);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location start = player.getLocation().add(right.multiply(side * Settings.d("equipamiento", "BotaniaSkill.geometria-001", 0.34))).add(0, Settings.d("equipamiento", "BotaniaSkill.geometria-002", 1.48), 0);
        Location end = target.getEyeLocation().add(0, -Settings.d("equipamiento", "BotaniaSkill.geometria-003", 0.15), 0);
        Vector delta = end.toVector().subtract(start.toVector());
        int nodes = Math.min(30, Math.max(12, (int) (delta.length() * 4)));
        for (int n = 0; n <= nodes; n++) {
            double t = (double) n / nodes;
            Location at = start.clone().add(delta.clone().multiply(t)).add(0, Math.sin(t * Math.PI) * Settings.d("equipamiento", "BotaniaSkill.geometria-004", 0.18), 0);
            player.getWorld().spawnParticle(Settings.particle("equipamiento", "BotaniaSkill.particle-001", Particle.DUST), at,Settings.i("equipamiento", "BotaniaSkill.spawnparticle-003-arg2", 1),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-004-arg3", 0),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-005-arg4", 0),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-006-arg5", 0),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-007-arg6", 0),
                    new Particle.DustOptions(Color.fromRGB(Settings.i("equipamiento", "BotaniaSkill.fromrgb-009-arg0", 67),Settings.i("equipamiento", "BotaniaSkill.fromrgb-010-arg1", 139),Settings.i("equipamiento", "BotaniaSkill.fromrgb-011-arg2", 80)),Settings.f("equipamiento", "BotaniaSkill.dustoptions-008-arg1", 0.72f)));
        }
        player.getWorld().spawnParticle(Settings.particle("equipamiento", "BotaniaSkill.particle-003", Particle.DUST), end,Settings.i("equipamiento", "BotaniaSkill.spawnparticle-012-arg2", 5),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-013-arg3", 0.13),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-014-arg4", 0.15),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-015-arg5", 0.13),Settings.d("equipamiento", "BotaniaSkill.spawnparticle-016-arg6", 0),
                new Particle.DustOptions(Color.fromRGB(Settings.i("equipamiento", "BotaniaSkill.fromrgb-018-arg0", 221),Settings.i("equipamiento", "BotaniaSkill.fromrgb-019-arg1", 144),Settings.i("equipamiento", "BotaniaSkill.fromrgb-020-arg2", 175)),Settings.f("equipamiento", "BotaniaSkill.dustoptions-017-arg1", 0.65f)));
        player.getWorld().playSound(start, Settings.string("equipamiento", "BotaniaSkill.sonido-006", "minecraft:block.grass.break"),Settings.f("equipamiento", "BotaniaSkill.playsound-021-arg2", 0.7f),Settings.f("equipamiento", "BotaniaSkill.playsound-022-arg3", 1.45f));
        plugin.dealSkillDamage(target, getDouble("lash-damage", 3.0));
        if (!target.isDead()) target.addPotionEffect(new PotionEffect(Settings.potion("equipamiento", "BotaniaSkill.pocion-030", PotionEffectType.SLOWNESS),
                getInt("slowness-ticks", 30), 0, true, true, true));
    }

    private Vector horizontalForward(Player player) {
        Vector forward = player.getLocation().getDirection().setY(0);
        return forward.lengthSquared() < 0.00001 ? new Vector(0, 0, 1) : forward.normalize();
    }

    @Override public void tick(long engineTick) {
        Set<UUID> active = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!player.isValid() || player.isDead() || !access(player)) {
                clear(id);
                continue;
            }
            active.add(id);
            State state = state(id);
            boolean opened = System.currentTimeMillis() < state.activeUntil;
            if (!plugin.getConfig().getBoolean("skills.botania.show-permanent-buds", true) && !opened) {
                Roses old = displays.remove(id);
                if (old != null) remove(old);
                continue;
            }
            Roses view = displays.get(id);
            if (view == null || view.world != player.getWorld()
                    || view.parts.stream().anyMatch(part -> !part.display().isValid())) {
                if (view != null) remove(view);
                view = spawnRoses(player);
                displays.put(id, view);
            }
            update(player, view, opened);
        }
        for (UUID id : new HashSet<>(displays.keySet())) if (!active.contains(id)) clear(id);
    }

    private Roses spawnRoses(Player player) {
        Roses view = new Roses(player.getWorld());
        for (int side : new int[] {-1, 1}) {
            addPart(player, view, side, Kind.STEM, 0, Settings.material("equipamiento", "BotaniaSkill.material-031", Material.MOSS_BLOCK));
            addPart(player, view, side, Kind.BUD, 0, Settings.material("equipamiento", "BotaniaSkill.material-032", Material.PINK_CONCRETE));
            for (int i = 0; i < 4; i++) addPart(player, view, side, Kind.PETAL, i, Settings.material("equipamiento", "BotaniaSkill.material-033", Material.PINK_WOOL));
            addPart(player, view, side, Kind.HEART, 0, Settings.material("equipamiento", "BotaniaSkill.material-034", Material.GOLD_BLOCK));
        }
        return view;
    }
    private void addPart(Player player, Roses view, int side, Kind kind, int petal, Material material) {
        BlockDisplay display = player.getWorld().spawn(player.getLocation(), BlockDisplay.class, e -> {
            e.setBlock(Bukkit.createBlockData(material));
            plugin.visuals().mark(e);
        });
        view.parts.add(new Piece(display, side, kind, petal));
    }
    private void update(Player player, Roses view, boolean open) {
        Vector forward = horizontalForward(player);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double yBase = player.isSneaking() ? Settings.d("equipamiento", "BotaniaSkill.geometria-005", 1.17) : Settings.d("equipamiento", "BotaniaSkill.geometria-006", 1.45);
        if (player.isSwimming() || player.isGliding()) yBase = Settings.d("equipamiento", "BotaniaSkill.geometria-007", 0.55);
        boolean refreshShape = !view.initialized || view.opened != open;
        for (Piece part : view.parts) {
            double x = part.shoulder() * Settings.d("equipamiento", "BotaniaSkill.geometria-008", 0.34);
            double y = yBase + Settings.d("equipamiento", "BotaniaSkill.geometria-009", 0.04);
            double z = -Settings.d("equipamiento", "BotaniaSkill.geometria-010", 0.07);
            float sx, sy, sz;
            switch (part.kind()) {
                case STEM -> { y -= Settings.d("equipamiento", "BotaniaSkill.geometria-011", 0.085); sx = Settings.f("equipamiento", "BotaniaSkill.geometria-012", 0.065f); sy = Settings.f("equipamiento", "BotaniaSkill.geometria-013", 0.14f); sz = Settings.f("equipamiento", "BotaniaSkill.geometria-014", 0.065f); }
                case BUD -> { sx = open ? Settings.f("equipamiento", "BotaniaSkill.geometria-015", 0.11f) : Settings.f("equipamiento", "BotaniaSkill.geometria-016", 0.16f); sy = open ? Settings.f("equipamiento", "BotaniaSkill.geometria-017", 0.12f) : Settings.f("equipamiento", "BotaniaSkill.geometria-018", 0.17f); sz = sx; }
                case HEART -> { y += Settings.d("equipamiento", "BotaniaSkill.geometria-019", 0.045); sx = open ? Settings.f("equipamiento", "BotaniaSkill.geometria-020", 0.073f) : Settings.f("equipamiento", "BotaniaSkill.geometria-021", 0.014f); sy = sx; sz = sx; }
                case PETAL -> {
                    double angle = part.petalIndex() * Math.PI / 2 + Math.PI / 4;
                    double radius = open ? Settings.d("equipamiento", "BotaniaSkill.geometria-022", 0.145) : Settings.d("equipamiento", "BotaniaSkill.geometria-023", 0.047);
                    x += Math.cos(angle) * radius;
                    z += Math.sin(angle) * radius;
                    y += open ? Settings.d("equipamiento", "BotaniaSkill.geometria-024", 0.075) : Settings.d("equipamiento", "BotaniaSkill.geometria-025", 0.04);
                    sx = open ? Settings.f("equipamiento", "BotaniaSkill.geometria-026", 0.135f) : Settings.f("equipamiento", "BotaniaSkill.geometria-027", 0.085f);
                    sy = open ? Settings.f("equipamiento", "BotaniaSkill.geometria-028", 0.055f) : Settings.f("equipamiento", "BotaniaSkill.geometria-029", 0.085f);
                    sz = sx;
                }
                default -> throw new IllegalStateException(Settings.text("equipamiento", "BotaniaSkill.etiqueta-002", "Parte de rosa inválida"));
            }
            Vector point = player.getLocation().toVector().add(right.clone().multiply(x))
                    .add(forward.clone().multiply(z)).add(new Vector(0, y, 0));
            part.display().teleport(new Location(player.getWorld(), point.getX(), point.getY(), point.getZ()));
            if (refreshShape) part.display().setTransformation(new Transformation(
                    new Vector3f(-sx / 2, -sy / 2, -sz / 2), new Quaternionf(),
                    new Vector3f(sx, sy, sz), new Quaternionf()));
        }
        view.opened = open;
        view.initialized = true;
    }

    private void remove(Roses view) {
        for (Piece part : view.parts) if (part.display().isValid()) part.display().remove();
    }
    @Override public void clear(UUID playerId) {
        Roses view = displays.remove(playerId);
        if (view != null) remove(view);
        states.remove(playerId);
        // Cooldown persists across switching the armor off/on.
    }
    @Override public String status(Player player) {
        State state = states.get(player.getUniqueId());
        int hits = state == null ? 0 : state.hits;
        return Settings.text("equipamiento", "BotaniaSkill.etiqueta-003", "§d✿ Botania §7| set: §f") + hasSet(player) + Settings.text("equipamiento", "BotaniaSkill.etiqueta-004", "§7 | golpes: §f") + hits
                + Settings.text("equipamiento", "BotaniaSkill.etiqueta-005", "§7 | CD: §f") + plugin.cooldowns().remainingSeconds(player.getUniqueId(), id()) + "s";
    }
}
