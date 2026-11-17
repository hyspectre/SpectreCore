package dev.hydev.spectrecore.model;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import dev.hydev.spectrecore.text.TextService;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bounded YAML Display models. Previews are temporary. Placed models persist as anchors,
 * never as saved entity instances. The engine restores only anchors in loaded chunks.
 * No chunk is force-loaded, and all expensive operations execute on the server thread.
 */
public final class DisplayModels implements Listener {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private final SpectreCorePlugin plugin;
    private final dev.hydev.spectrecore.integration.WorldGuardBridge worldGuard;
    private final Map<UUID, List<Display>> previews = new HashMap<>();
    private final Map<UUID, Anchor> anchors = new LinkedHashMap<>();
    private final Map<UUID, Live> live = new HashMap<>();
    private final NamespacedKey marker;
    private final File placements;
    private YamlConfiguration options;
    private BukkitTask animationTask;
    private boolean storageReady;
    private long frames;

    private record Anchor(UUID instance, String model, UUID world, double x, double y, double z) {
        Location location(World world) { return new Location(world, x, y, z); }
    }
    private record Animation(String mode, float speed, float amplitude, int period) { }
    private record Part(String type, Material material, Vector3f offset, Vector3f scale, Vector3f rotation,
                        String base64, String text, Animation animation) { }
    private record Live(List<Display> entities, List<Part> parts, Location origin) { }

    public DisplayModels(SpectreCorePlugin plugin) {
        this.plugin = plugin;
        this.worldGuard = new dev.hydev.spectrecore.integration.WorldGuardBridge(plugin.getLogger());
        marker = new NamespacedKey(plugin, "model_instance");
        var sample = new File(plugin.getDataFolder(), "models/ejemplo.yml");
        if (!sample.exists()) plugin.saveResource("models/ejemplo.yml", false);
        var settings = new File(plugin.getDataFolder(), "models/settings.yml");
        if (!settings.exists()) plugin.saveResource("models/settings.yml", false);
        placements = new File(plugin.getDataFolder(), "models/placements.yml");
        if (!reloadOptions()) plugin.getLogger().severe("Modelos: settings.yml inválido; se mantienen límites conservadores.");
        storageReady = loadPlacements();
        if (!storageReady) plugin.getLogger().severe("Modelos persistentes desactivados: placements.yml dañado o ilegible.");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::restoreLoaded, 25);
        animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animate, 10,
                Math.max(2, Math.min(40, setting("animation.update-ticks", 10))));
    }
    public boolean reloadOptions() {
        File file = new File(plugin.getDataFolder(), "models/settings.yml");
        try {
            if (!file.isFile() || file.length() > 16_384) return false;
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file);
            for (String path : List.of("limits.preview-players", "limits.parts-per-model",
                    "limits.placed-models", "animation.update-ticks", "animation.viewer-radius")) {
                if (!candidate.isInt(path) || candidate.getInt(path) < 1 || candidate.getInt(path) > 512) return false;
            }
            if (candidate.getInt("limits.parts-per-model") > 64 || candidate.getInt("limits.placed-models") > 256) return false;
            options = candidate; return true;
        } catch (IOException | InvalidConfigurationException ex) { return false; }
    }
    private int setting(String path, int fallback) { return options == null ? fallback : options.getInt(path, fallback); }
    public boolean storageReady() { return storageReady; }
    public int placedCount() { return anchors.size(); }
    public int liveCount() { return live.size(); }
    public void stop() {
        if (animationTask != null) animationTask.cancel();
        for (UUID player : List.copyOf(previews.keySet())) clear(player);
        for (UUID instance : List.copyOf(live.keySet())) despawn(instance);
    }
    public void clear(UUID player) {
        List<Display> entities = previews.remove(player);
        if (entities != null) entities.forEach(display -> { if (display.isValid()) display.remove(); });
    }
    public boolean preview(Player player, String modelId) {
        if (!validId(modelId)) { message(player, "Modelos.id-invalido", "<red>ID de modelo inválido.</red>"); return true; }
        if (!previews.containsKey(player.getUniqueId()) && previews.size() >= setting("limits.preview-players", 4)) {
            message(player, "Modelos.capacidad", "<yellow>Demasiadas vistas previas simultáneas.</yellow>"); return true;
        }
        List<Part> parts;
        int duration;
        try {
            YamlConfiguration yaml = definition(modelId);
            duration = yaml.getInt("duration-ticks", 200);
            if (duration < 20 || duration > 1200) throw new IllegalArgumentException("duration-ticks fuera de rango");
            parts = parseParts(yaml);
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            player.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.invalido", "<red>Modelo inválido: {error}</red>", "error", sanitize(ex.getMessage()))));
            return true;
        }
        clear(player.getUniqueId());
        List<Display> spawned = new ArrayList<>();
        previews.put(player.getUniqueId(), spawned);
        Location origin = player.getEyeLocation().add(player.getEyeLocation().getDirection().normalize().multiply(2.5));
        try { for (Part part : parts) spawned.add(spawn(origin, part, null)); }
        catch (RuntimeException ex) {
            clear(player.getUniqueId());
            plugin.getLogger().warning("Vista previa 3D fallida: " + ex.getMessage());
            message(player, "Modelos.fallo", "<red>No se pudo visualizar el modelo; no se conservaron entidades.</red>"); return true;
        }
        UUID owner = player.getUniqueId();
        List<Display> snapshot = spawned;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (previews.get(owner) == snapshot) clear(owner);
        }, duration);
        player.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.creado",
                "<green>Vista previa de {id}: {partes} piezas.</green>", "id", modelId, "partes", spawned.size())));
        return true;
    }
    public boolean place(Player admin, String modelId) {
        if (!storageReady) { message(admin, "Modelos.storage", "<red>Almacenamiento de modelos bloqueado. Recupera placements.yml.</red>"); return true; }
        if (anchors.size() >= setting("limits.placed-models", 128)) {
            message(admin, "Modelos.limite", "<red>Se alcanzó el límite de modelos persistentes.</red>"); return true;
        }
        try {
            parseParts(definition(modelId)); // Validate before writing anything.
            Location point = admin.getEyeLocation().add(admin.getEyeLocation().getDirection().normalize().multiply(2.5));
            if (!worldGuard.canBuild(admin, point)) {
                message(admin, "Modelos.worldguard", "<red>WorldGuard: esta región no permite colocar o retirar modelos.</red>");
                return true;
            }
            UUID id = UUID.randomUUID();
            Anchor anchor = new Anchor(id, modelId, admin.getWorld().getUID(), point.getX(), point.getY(), point.getZ());
            anchors.put(id, anchor);
            if (!savePlacements()) {
                anchors.remove(id); storageReady = false;
                message(admin, "Modelos.storage", "<red>Almacenamiento de modelos bloqueado. Recupera placements.yml.</red>"); return true;
            }
            restore(anchor);
            admin.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.colocado",
                    "<green>Modelo colocado: {id}. Para retirarlo usa /spectrecore modelo quitar {id}</green>", "id", id)));
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) {
            admin.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.invalido",
                    "<red>Modelo inválido: {error}</red>", "error", sanitize(ex.getMessage()))));
        }
        return true;
    }
    public boolean remove(Player admin, String uuid) {
        if (!storageReady) { message(admin, "Modelos.storage", "<red>Almacenamiento de modelos bloqueado. Recupera placements.yml.</red>"); return true; }
        try {
            UUID id = UUID.fromString(uuid);
            Anchor anchor = anchors.remove(id);
            if (anchor == null) { message(admin, "Modelos.no-existe", "<red>No existe esa instancia.</red>"); return true; }
            World anchorWorld = Bukkit.getWorld(anchor.world());
            if (anchorWorld == null || !worldGuard.canBuild(admin, anchor.location(anchorWorld))) {
                anchors.put(id, anchor);
                message(admin, "Modelos.worldguard", "<red>WorldGuard: esta región no permite colocar o retirar modelos.</red>"); return true;
            }
            if (!savePlacements()) { anchors.put(id, anchor); storageReady = false; message(admin, "Modelos.storage", "<red>Almacenamiento de modelos bloqueado. Recupera placements.yml.</red>"); return true; }
            despawn(id);
            message(admin, "Modelos.retirado", "<green>Modelo retirado.</green>");
        } catch (IllegalArgumentException ex) { message(admin, "Modelos.uuid-invalido", "<red>UUID inválido.</red>"); }
        return true;
    }
    public boolean list(Player admin) {
        admin.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.listado",
                "<gold>Modelos colocados: {cantidad}</gold>", "cantidad", anchors.size())));
        for (Anchor anchor : anchors.values()) {
            World world = Bukkit.getWorld(anchor.world());
            if (world == null || !admin.getWorld().equals(world)) continue;
            if (admin.getLocation().distanceSquared(anchor.location(world)) > 32 * 32) continue;
            admin.sendMessage(TextService.component(Settings.text("nucleo", "Modelos.entrada", "<gray>{model} · {id}</gray>",
                    "model", anchor.model(), "id", anchor.instance())));
        }
        return true;
    }
    private void message(Player player, String key, String fallback) {
        player.sendMessage(TextService.component(Settings.text("nucleo", key, fallback)));
    }
    private static boolean validId(String model) { return model != null && ID.matcher(model).matches(); }
    private static String sanitize(String raw) { return raw == null ? "Error" : raw.replace('<', ' ').replace('>', ' ').replace('\n', ' '); }
    private YamlConfiguration definition(String id) throws IOException, InvalidConfigurationException {
        if (!validId(id)) throw new IllegalArgumentException("ID incorrecto");
        File file = new File(plugin.getDataFolder(), "models/" + id + ".yml");
        if (!file.isFile() || file.length() > 32 * 1024) throw new IllegalArgumentException("No existe o es demasiado grande: " + id);
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file); return yaml;
    }
    private List<Part> parseParts(YamlConfiguration yaml) {
        List<Map<?, ?>> entries = new ArrayList<>(yaml.getMapList("parts"));
        if (entries.isEmpty() || entries.size() > setting("limits.parts-per-model", 24))
            throw new IllegalArgumentException("Cantidad de piezas fuera de rango");
        List<Part> result = new ArrayList<>();
        for (Map<?, ?> data : entries) result.add(parse(data));
        return List.copyOf(result);
    }
    private static Vector3f vec(Object raw, Vector3f fallback, double min, double max) {
        if (raw == null) return fallback;
        if (!(raw instanceof List<?> list) || list.size() != 3) throw new IllegalArgumentException("Vector 3 inválido");
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(list.get(i) instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
                throw new IllegalArgumentException("Vector fuera de límites");
            values[i] = n.floatValue();
        }
        return new Vector3f(values[0], values[1], values[2]);
    }
    private static String string(Map<?, ?> data, String key, String fallback) {
        Object raw = data.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof String str)) throw new IllegalArgumentException(key + " debe ser texto");
        return str;
    }
    private static Part parse(Map<?, ?> data) {
        String type = string(data, "type", "").toUpperCase(Locale.ROOT);
        if (!List.of("BLOCK_DISPLAY", "ITEM_DISPLAY", "TEXT_DISPLAY").contains(type))
            throw new IllegalArgumentException("Display desconocido: " + type);
        Vector3f offset = vec(data.get("offset"), new Vector3f(), -8, 8);
        Vector3f scale = vec(data.get("scale"), new Vector3f(1,1,1), .05, 4);
        Vector3f rotation = vec(data.get("rotation"), new Vector3f(), -360, 360);
        String materialName = string(data, "material", type.equals("ITEM_DISPLAY") ? "PLAYER_HEAD" : "STONE");
        Material material = Material.matchMaterial(materialName);
        if (!type.equals("TEXT_DISPLAY") && (material == null || (type.equals("BLOCK_DISPLAY") && !material.isBlock())
                || (type.equals("ITEM_DISPLAY") && !material.isItem()))) throw new IllegalArgumentException("Material inválido: " + materialName);
        String base64 = string(data, "base64", "");
        if (!base64.isBlank()) {
            if (!type.equals("ITEM_DISPLAY") || material != Material.PLAYER_HEAD)
                throw new IllegalArgumentException("Base64 requiere ITEM_DISPLAY y PLAYER_HEAD");
            base64 = TexturePayload.checked(base64);
        }
        String text = string(data, "text", "");
        if (text.length() > 500) throw new IllegalArgumentException("Texto demasiado largo");
        Animation animation = new Animation("NONE", 0, 0, 40);
        Object rawAnimation = data.get("animation");
        if (rawAnimation != null) {
            if (!(rawAnimation instanceof Map<?, ?> fields)) throw new IllegalArgumentException("Animación inválida");
            String mode = string(fields, "mode", "NONE").toUpperCase(Locale.ROOT);
            if (!List.of("NONE", "BOB", "SPIN").contains(mode)) throw new IllegalArgumentException("Animación desconocida: " + mode);
            Object speedValue = (fields.containsKey("speed") ? fields.get("speed") : 45);
            Object amplitudeValue = (fields.containsKey("amplitude") ? fields.get("amplitude") : .10);
            Object periodValue = (fields.containsKey("period-ticks") ? fields.get("period-ticks") : 40);
            if (!(speedValue instanceof Number speed) || !(amplitudeValue instanceof Number amplitude) || !(periodValue instanceof Number period)
                    || !Double.isFinite(speed.doubleValue()) || !Double.isFinite(amplitude.doubleValue())
                    || speed.doubleValue() < 0 || speed.doubleValue() > 360 || amplitude.doubleValue() < 0 || amplitude.doubleValue() > 1
                    || period.intValue() < 10 || period.intValue() > 400) throw new IllegalArgumentException("Parámetros de animación inválidos");
            animation = new Animation(mode, speed.floatValue(), amplitude.floatValue(), period.intValue());
        }
        return new Part(type, material, offset, scale, rotation, base64, text, animation);
    }
    private static Transformation transform(Part part, long ticks) {
        Vector3f offset = new Vector3f(part.offset());
        Quaternionf quaternion = new Quaternionf().rotateXYZ(
                (float)Math.toRadians(part.rotation().x), (float)Math.toRadians(part.rotation().y), (float)Math.toRadians(part.rotation().z));
        if (part.animation().mode().equals("BOB"))
            offset.y += (float)Math.sin(2 * Math.PI * (ticks % part.animation().period()) / part.animation().period()) * part.animation().amplitude();
        else if (part.animation().mode().equals("SPIN"))
            quaternion.rotateY((float)Math.toRadians(part.animation().speed() * ticks / 20.0));
        return new Transformation(offset, quaternion, part.scale(), new Quaternionf());
    }
    private Display spawn(Location origin, Part part, UUID anchor) {
        World world = origin.getWorld();
        Display display;
        if (part.type().equals("BLOCK_DISPLAY"))
            display = world.spawn(origin, BlockDisplay.class, e -> e.setBlock(part.material().createBlockData()));
        else if (part.type().equals("ITEM_DISPLAY")) {
            ItemStack item = part.base64().isBlank() ? new ItemStack(part.material()) : CustomHeads.fromBase64(part.base64());
            display = world.spawn(origin, ItemDisplay.class, e -> {
                e.setItemStack(item);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            });
        } else display = world.spawn(origin, TextDisplay.class, e -> e.text(TextService.component(part.text())));
        display.setPersistent(false);
        display.setViewRange(.75f);
        display.setTransformation(transform(part, 0));
        if (anchor != null) display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, anchor.toString());
        return display;
    }
    private boolean loadPlacements() {
        anchors.clear();
        if (placements.exists() && !placements.isFile()) return false;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (placements.isFile()) yaml.load(placements);
            ConfigurationSection list = yaml.getConfigurationSection("instances");
            if (yaml.contains("instances") && list == null) return false;
            if (list != null) {
                if (list.getKeys(false).size() > setting("limits.placed-models", 128)) return false;
                for (String raw : list.getKeys(false)) {
                    UUID id = UUID.fromString(raw);
                    ConfigurationSection c = list.getConfigurationSection(raw);
                    if (c == null || !validId(c.getString("model"))) return false;
                    UUID world = UUID.fromString(c.getString("world", ""));
                    double x = c.getDouble("x", Double.NaN), y = c.getDouble("y", Double.NaN), z = c.getDouble("z", Double.NaN);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                            || Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984 || y < -128 || y > 512) return false;
                    anchors.put(id, new Anchor(id, c.getString("model"), world, x, y, z));
                }
            }
            return true;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException ex) { return false; }
    }
    private boolean savePlacements() {
        if (!storageReady) return false;
        YamlConfiguration yaml = new YamlConfiguration(); yaml.set("schema-version", 1);
        for (Anchor a : anchors.values()) {
            String path = "instances." + a.instance();
            yaml.set(path + ".model", a.model()); yaml.set(path + ".world", a.world().toString());
            yaml.set(path + ".x", a.x()); yaml.set(path + ".y", a.y()); yaml.set(path + ".z", a.z());
        }
        try { AtomicFileStore.writeUtf8(placements.toPath(), yaml.saveToString()); return true; }
        catch (IOException ex) { plugin.getLogger().severe("Modelos: no se pudo guardar placements.yml: " + ex.getMessage()); return false; }
    }
    private void restoreLoaded() {
        if (!storageReady) return;
        for (Anchor a : anchors.values()) restore(a);
    }
    private void restore(Anchor a) {
        if (live.containsKey(a.instance())) return;
        World world = Bukkit.getWorld(a.world());
        if (world == null) return;
        Location point = a.location(world);
        if (!world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)) return;
        List<Part> parts;
        try { parts = parseParts(definition(a.model())); }
        catch (Exception ex) { plugin.getLogger().warning("Modelo colocado " + a.instance() + " no restaurado: " + ex.getMessage()); return; }
        List<Display> displays = new ArrayList<>();
        try { for (Part part : parts) displays.add(spawn(point, part, a.instance())); }
        catch (RuntimeException error) {
            displays.forEach(display -> { if (display.isValid()) display.remove(); });
            plugin.getLogger().warning("Error restaurando modelo " + a.instance() + ": " + error.getMessage()); return;
        }
        live.put(a.instance(), new Live(displays, parts, point));
    }
    private void despawn(UUID instance) {
        Live v = live.remove(instance);
        if (v != null) v.entities().forEach(display -> { if (display.isValid()) display.remove(); });
    }
    @EventHandler public void loaded(ChunkLoadEvent event) {
        // Remove orphan displays from older crashed sessions, then restore our anchors.
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof Display display)) continue;
            String id = display.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
            if (id != null && !live.values().stream().anyMatch(v -> v.entities().contains(display))) display.remove();
        }
        if (!storageReady) return;
        for (Anchor a : anchors.values()) if (a.world().equals(event.getWorld().getUID())
                && ((int)Math.floor(a.x()) >> 4) == event.getChunk().getX()
                && ((int)Math.floor(a.z()) >> 4) == event.getChunk().getZ()) restore(a);
    }
    @EventHandler public void unloaded(ChunkUnloadEvent event) {
        for (Anchor a : anchors.values()) if (a.world().equals(event.getWorld().getUID())
                && ((int)Math.floor(a.x()) >> 4) == event.getChunk().getX()
                && ((int)Math.floor(a.z()) >> 4) == event.getChunk().getZ()) despawn(a.instance());
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) { plugin.getServer().getScheduler().runTaskLater(plugin, this::restoreLoaded, 5); }
    private void animate() {
        frames += Math.max(2, Math.min(40, setting("animation.update-ticks", 10)));
        int radius = setting("animation.viewer-radius", 24);
        for (Live model : live.values()) {
            World world = model.origin().getWorld();
            if (world.getPlayers().stream().noneMatch(player -> player.getLocation().distanceSquared(model.origin()) < radius * radius)) continue;
            for (int i = 0; i < model.parts().size(); i++) {
                Part part = model.parts().get(i);
                if (part.animation().mode().equals("NONE")) continue;
                Display display = model.entities().get(i);
                if (!display.isValid()) continue;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(Math.max(1, setting("animation.update-ticks", 10)));
                display.setTransformation(transform(part, frames));
            }
        }
    }
}
