package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MiningVisuals implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final Set<Show> shows = new HashSet<>();
    private boolean enabled = true;
    private int limit = 32;
    public MiningVisuals(JavaPlugin plugin) { this.plugin = plugin; key = new NamespacedKey(plugin, "mining_visual"); }
    public void configure(boolean enabled, int limit) { this.enabled = enabled; this.limit = Math.max(1, Math.min(128, limit)); }
    public void enable() { Bukkit.getPluginManager().registerEvents(this, plugin); Bukkit.getWorlds().forEach(w -> { for (Chunk c : w.getLoadedChunks()) cleanup(c); }); }
    public void disable() { new ArrayList<>(shows).forEach(Show::close); org.bukkit.event.HandlerList.unregisterAll(this); }
    @EventHandler public void chunk(ChunkLoadEvent e) { cleanup(e.getChunk()); }
    private void cleanup(Chunk c) { for (Entity e : c.getEntities()) if (e.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) e.remove(); }
    public void treasure(Location origin, Material ore, int tier) {
        if (available(origin)) new Show(origin, ore, null, tier == 1 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-001-arg0", 190),Settings.i("nucleo", "MiningVisuals.fromrgb-002-arg1", 180),Settings.i("nucleo", "MiningVisuals.fromrgb-003-arg2", 160)) : tier == 2 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-004-arg0", 255),Settings.i("nucleo", "MiningVisuals.fromrgb-005-arg1", 70),Settings.i("nucleo", "MiningVisuals.fromrgb-006-arg2", 90)) : Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-007-arg0", 55),Settings.i("nucleo", "MiningVisuals.fromrgb-008-arg1", 235),Settings.i("nucleo", "MiningVisuals.fromrgb-009-arg2", 255)), 32).begin();
    }
    public void gem(Location origin, ItemStack gem, String quality) {
        if (available(origin)) new Show(origin, null, gem, quality.equals("superior") ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-010-arg0", 190),Settings.i("nucleo", "MiningVisuals.fromrgb-011-arg1", 100),Settings.i("nucleo", "MiningVisuals.fromrgb-012-arg2", 255)) : quality.equals("intermedia") ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-013-arg0", 70),Settings.i("nucleo", "MiningVisuals.fromrgb-014-arg1", 150),Settings.i("nucleo", "MiningVisuals.fromrgb-015-arg2", 255)) : Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-016-arg0", 90),Settings.i("nucleo", "MiningVisuals.fromrgb-017-arg1", 240),Settings.i("nucleo", "MiningVisuals.fromrgb-018-arg2", 150)), 36).begin();
    }
    public void soul(Location origin, int tier) {
        if (available(origin)) new Show(origin, null, new ItemStack(Settings.material("nucleo", "MiningVisuals.material-028", Material.SKELETON_SKULL)),
            tier == 1 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-019-arg0", 120),Settings.i("nucleo", "MiningVisuals.fromrgb-020-arg1", 205),Settings.i("nucleo", "MiningVisuals.fromrgb-021-arg2", 245)) : tier == 2 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-022-arg0", 45),Settings.i("nucleo", "MiningVisuals.fromrgb-023-arg1", 160),Settings.i("nucleo", "MiningVisuals.fromrgb-024-arg2", 255)) : Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-025-arg0", 120),Settings.i("nucleo", "MiningVisuals.fromrgb-026-arg1", 100),Settings.i("nucleo", "MiningVisuals.fromrgb-027-arg2", 255)), 40, true).begin();
    }
    public void harvest(Location origin, int tier) {
        if (available(origin)) new Show(origin, Settings.material("nucleo", "MiningVisuals.material-029", Material.HAY_BLOCK), null,
            tier == 1 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-028-arg0", 255),Settings.i("nucleo", "MiningVisuals.fromrgb-029-arg1", 230),Settings.i("nucleo", "MiningVisuals.fromrgb-030-arg2", 60)) : tier == 2 ? Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-031-arg0", 255),Settings.i("nucleo", "MiningVisuals.fromrgb-032-arg1", 160),Settings.i("nucleo", "MiningVisuals.fromrgb-033-arg2", 35)) : Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-034-arg0", 165),Settings.i("nucleo", "MiningVisuals.fromrgb-035-arg1", 80),Settings.i("nucleo", "MiningVisuals.fromrgb-036-arg2", 235)), 36).begin();
    }
    public void chest(Location origin) {
        chest(origin,60);
    }
    public void chest(Location origin,int duration) {
        if (available(origin)) new Show(origin, null, new ItemStack(Settings.material("nucleo", "MiningVisuals.material-030", Material.CHEST)), Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-037-arg0", 255),Settings.i("nucleo", "MiningVisuals.fromrgb-038-arg1", 215),Settings.i("nucleo", "MiningVisuals.fromrgb-039-arg2", 0)), duration, false, true).begin();
    }
    public void charged(Player player) {
        if (!enabled) return;
        Location at = player.getLocation().add(0, Settings.d("nucleo", "MiningVisuals.geometria-001", .15), 0);
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            at.getWorld().spawnParticle(Settings.particle("nucleo", "MiningVisuals.particle-001", Particle.DUST), at.clone().add(Math.cos(a) * Settings.d("nucleo", "MiningVisuals.geometria-002", .8), 0, Math.sin(a) * Settings.d("nucleo", "MiningVisuals.geometria-003", .8)),Settings.i("nucleo", "MiningVisuals.spawnparticle-040-arg2", 1),Settings.d("nucleo", "MiningVisuals.spawnparticle-041-arg3", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-042-arg4", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-043-arg5", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-044-arg6", 0), new Particle.DustOptions(Color.fromRGB(Settings.i("nucleo", "MiningVisuals.fromrgb-046-arg0", 195),Settings.i("nucleo", "MiningVisuals.fromrgb-047-arg1", 115),Settings.i("nucleo", "MiningVisuals.fromrgb-048-arg2", 255)),Settings.f("nucleo", "MiningVisuals.dustoptions-045-arg1", .8f)));
        }
    }
    private boolean available(Location p) { return enabled && shows.size() < limit && p.getWorld().isChunkLoaded(p.getBlockX() >> 4, p.getBlockZ() >> 4); }
    private void mark(Display display) {
        display.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        display.setPersistent(false); display.setInvulnerable(true); display.setGravity(false);
        display.setBrightness(new Display.Brightness(Settings.i("nucleo", "MiningVisuals.brillo-001-arg0", 15),Settings.i("nucleo", "MiningVisuals.brillo-002-arg1", 15))); display.setViewRange(Settings.f("nucleo", "MiningVisuals.setviewrange-049-arg0", .6f));
        display.setTeleportDuration(Settings.i("nucleo", "MiningVisuals.setteleportduration-050-arg0", 2)); display.setInterpolationDuration(Settings.i("nucleo", "MiningVisuals.setinterpolationduration-051-arg0", 2));
    }
    private Transformation pose(float scale, Quaternionf rotation, boolean block) {
        Vector3f offset = block ? new Vector3f(-scale / 2, -scale / 2, -scale / 2).rotate(rotation) : new Vector3f();
        return new Transformation(offset, rotation, new Vector3f(scale), new Quaternionf());
    }
    private final class Show extends BukkitRunnable {
        final Location origin; final Material ore; final ItemStack gem; final Color color; final int duration; final boolean soul; final boolean chest;
        final List<Display> entities = new ArrayList<>(); int age;
        Show(Location origin, Material ore, ItemStack gem, Color color, int duration) { this(origin, ore, gem, color, duration, false); }
        Show(Location origin, Material ore, ItemStack gem, Color color, int duration, boolean soul) {
            this(origin, ore, gem, color, duration, soul, false);
        }
        Show(Location origin, Material ore, ItemStack gem, Color color, int duration, boolean soul, boolean chest) {
            this.origin = origin.clone().add(0, Settings.d("nucleo", "MiningVisuals.geometria-004", .1), 0); this.ore = ore; this.gem = gem; this.color = color; this.duration = duration; this.soul = soul; this.chest = chest;
        }
        void begin() {
            try {
                Display center;
                if (ore != null) center = origin.getWorld().spawn(origin, BlockDisplay.class, d -> { mark(d); d.setBlock(ore.createBlockData()); });
                else center = origin.getWorld().spawn(origin, ItemDisplay.class, d -> { mark(d); d.setItemStack(gem.clone()); d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED); });
                entities.add(center);
                for (int i = 0; i < 3; i++) entities.add(origin.getWorld().spawn(origin, ItemDisplay.class, d -> {
                    mark(d); d.setItemStack(new ItemStack(soul ? Settings.material("nucleo", "MiningVisuals.material-031", Material.SOUL_LANTERN) : chest ? Settings.material("nucleo", "MiningVisuals.material-032", Material.GOLD_NUGGET) : Settings.material("nucleo", "MiningVisuals.material-033", Material.AMETHYST_SHARD))); d.setTransformation(pose(Settings.f("nucleo", "MiningVisuals.geometria-005", .22f), new Quaternionf(), false));
                }));
                shows.add(this); runTaskTimer(plugin, 0, 2);
                origin.getWorld().playSound(origin, Settings.sound("nucleo", "MiningVisuals.sound-003", Sound.BLOCK_AMETHYST_BLOCK_RESONATE),Settings.f("nucleo", "MiningVisuals.playsound-052-arg2", .55f),Settings.f("nucleo", "MiningVisuals.playsound-053-arg3", .85f));
                if (chest) origin.getWorld().playSound(origin, Settings.sound("nucleo", "MiningVisuals.sound-004", Sound.BLOCK_CHEST_OPEN),Settings.f("nucleo", "MiningVisuals.playsound-054-arg2", .8f),Settings.f("nucleo", "MiningVisuals.playsound-055-arg3", .7f));
            } catch (RuntimeException error) { close(); plugin.getLogger().warning(Settings.plain("nucleo", "MiningVisuals.mensaje-001", "No se pudo mostrar la animación minera: {error-getmessage}", "error-getmessage", error.getMessage())); }
        }
        @Override public void run() {
            try {
                if (!origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4) || entities.stream().anyMatch(e -> !e.isValid())) { close(); return; }
                age += 2; float t = (float) age / duration;
                if (t >= 1) {
                    origin.getWorld().spawnParticle(soul ? Settings.particle("nucleo", "MiningVisuals.particle-005", Particle.SOUL_FIRE_FLAME) : Settings.particle("nucleo", "MiningVisuals.particle-006", Particle.END_ROD), origin.clone().add(0, Settings.d("nucleo", "MiningVisuals.geometria-006", .9), 0),Settings.i("nucleo", "MiningVisuals.spawnparticle-056-arg2", 18),Settings.d("nucleo", "MiningVisuals.spawnparticle-057-arg3", .35),Settings.d("nucleo", "MiningVisuals.spawnparticle-058-arg4", .3),Settings.d("nucleo", "MiningVisuals.spawnparticle-059-arg5", .35),Settings.d("nucleo", "MiningVisuals.spawnparticle-060-arg6", .04));
                    origin.getWorld().playSound(origin, Settings.sound("nucleo", "MiningVisuals.sound-007", Sound.BLOCK_AMETHYST_CLUSTER_BREAK),Settings.f("nucleo", "MiningVisuals.playsound-061-arg2", .6f),Settings.f("nucleo", "MiningVisuals.playsound-062-arg3", 1.45f));
                    close(); return;
                }
                double height = Settings.d("nucleo", "MiningVisuals.geometria-007", .3) + Math.sin(t * Math.PI / 2) * Settings.d("nucleo", "MiningVisuals.geometria-008", .85);
                Display core = entities.getFirst(); core.teleport(origin.clone().add(0, height, 0));
                float scale = (ore == null ? Settings.f("nucleo", "MiningVisuals.geometria-009", .65f) : Settings.f("nucleo", "MiningVisuals.geometria-010", .45f)) * (t > Settings.d("nucleo", "MiningVisuals.geometria-011", .8) ? (1 - t) * 5 : Math.min(1, .3f + t * 4));
                Quaternionf rotation = new Quaternionf().rotateY(t * (chest ? 13 : 7)).rotateX(Settings.f("nucleo", "MiningVisuals.geometria-012", .25f));
                if (chest) rotation.rotateZ((float) Math.sin(age * Settings.d("nucleo", "MiningVisuals.geometria-013", .8)) * Settings.f("nucleo", "MiningVisuals.geometria-014", .08f) * t);
                core.setInterpolationDelay(0); core.setTransformation(pose(scale, rotation, ore != null));
                if (chest && age % 10 == 0) origin.getWorld().playSound(origin, Settings.sound("nucleo", "MiningVisuals.sound-008", Sound.BLOCK_AMETHYST_BLOCK_CHIME),Settings.f("nucleo", "MiningVisuals.playsound-063-arg2", .3f), Settings.f("nucleo", "MiningVisuals.geometria-015", .8f) + t * Settings.f("nucleo", "MiningVisuals.geometria-016", .7f));
                for (int i = 1; i < entities.size(); i++) {
                    double a = t * Math.PI * 4 + (i - 1) * Math.PI * 2 / 3;
                    Location orbit = origin.clone().add(Math.cos(a) * Settings.d("nucleo", "MiningVisuals.geometria-017", .38), height + Math.sin(a * 2) * Settings.d("nucleo", "MiningVisuals.geometria-018", .15), Math.sin(a) * Settings.d("nucleo", "MiningVisuals.geometria-019", .38));
                    if (!orbit.getWorld().isChunkLoaded(orbit.getBlockX() >> 4, orbit.getBlockZ() >> 4)) { close(); return; }
                    entities.get(i).teleport(orbit);
                    entities.get(i).setRotation((float) Math.toDegrees(-a), 15);
                }
                for (int i = 0; i < 6; i++) {
                    double a = t * 9 + i * Math.PI / 3;
                    origin.getWorld().spawnParticle(Settings.particle("nucleo", "MiningVisuals.particle-009", Particle.DUST), origin.clone().add(Math.cos(a) * (Settings.d("nucleo", "MiningVisuals.geometria-020", .35) + Settings.d("nucleo", "MiningVisuals.geometria-021", .4) * t), height * Settings.d("nucleo", "MiningVisuals.geometria-022", .6), Math.sin(a) * (Settings.d("nucleo", "MiningVisuals.geometria-023", .35) + Settings.d("nucleo", "MiningVisuals.geometria-024", .4) * t)),Settings.i("nucleo", "MiningVisuals.spawnparticle-064-arg2", 1),Settings.d("nucleo", "MiningVisuals.spawnparticle-065-arg3", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-066-arg4", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-067-arg5", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-068-arg6", 0), new Particle.DustOptions(color,Settings.f("nucleo", "MiningVisuals.dustoptions-069-arg1", .7f)));
                    if (soul) origin.getWorld().spawnParticle(Settings.particle("nucleo", "MiningVisuals.particle-011", Particle.SOUL_FIRE_FLAME), origin.clone().add(Math.cos(a) * Settings.d("nucleo", "MiningVisuals.geometria-025", .28), height + Math.sin(a) * Settings.d("nucleo", "MiningVisuals.geometria-026", .25), Math.sin(a) * Settings.d("nucleo", "MiningVisuals.geometria-027", .28)),Settings.i("nucleo", "MiningVisuals.spawnparticle-070-arg2", 1),Settings.d("nucleo", "MiningVisuals.spawnparticle-071-arg3", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-072-arg4", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-073-arg5", 0),Settings.d("nucleo", "MiningVisuals.spawnparticle-074-arg6", .005));
                }
            } catch (RuntimeException error) { close(); }
        }
        void close() { entities.forEach(Entity::remove); entities.clear(); shows.remove(this); try { cancel(); } catch (IllegalStateException ignored) { } }
    }
}
