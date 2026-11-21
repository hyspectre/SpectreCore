package dev.hydev.spectrecore.cooking;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.text.TextService;
import net.kyori.adventure.text.format.TextDecoration;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import dev.hydev.spectrecore.api.CookingCompletedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import dev.hydev.spectrecore.storage.ClaimJournal;
import org.bukkit.configuration.InvalidConfigurationException;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper-only cooking minigame. The cooking station is a real water cauldron,
 * not a custom block; displays are decorative. All state mutations run on the
 * primary server thread. Recipes and all three output quality IDs are configured in YAML.
 */
public final class CookingModule implements Listener, CommandExecutor, TabCompleter {
    private final SpectreCorePlugin plugin;
    public CookingModule(SpectreCorePlugin plugin) {
        this.plugin = plugin;
        this.worldGuard = new dev.hydev.spectrecore.integration.WorldGuardBridge(plugin.getLogger());
    }
    private org.bukkit.configuration.file.FileConfiguration getConfig() { return plugin.getConfig(); }
    private org.bukkit.Server getServer() { return plugin.getServer(); }
    private java.util.logging.Logger getLogger() { return plugin.getLogger(); }
    private static String prefix() { return ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + Settings.text("cocina", "CookingModule.etiqueta-023", "Cocina Arcana") + ChatColor.DARK_GRAY + "] " + ChatColor.RESET; }
    private static final String LEGACY_TAG = "hydev_cocina_visual";
    private static final String OUR_TAG = "spectrecore_cooking_visual";
    private static final String PREVIOUS_TAG = "spectrecooking_visual";
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private YamlConfiguration recipeBookLayout;
    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final Map<String, UUID> textDisplays = new HashMap<>();
    private final Map<String, UUID> liquidDisplays = new HashMap<>();
    private final Map<String, Map<Integer, UUID>> ingredientDisplays = new HashMap<>();
    private final Map<UUID, Set<String>> discoveries = new HashMap<>();
    private NamespacedKey displayStationKey;
    private NamespacedKey displayRoleKey;
    private File stationsFile;
    private File recipesFile;
    private File discoveriesFile;
    private int saveCounter;
    private ClaimJournal claimJournal;
    private final dev.hydev.spectrecore.integration.WorldGuardBridge worldGuard;
    private boolean discoveriesStorageReady = true;
    private boolean stationStorageReady = true;
    private long animationFrame;
    /** Raw records for dimensions/worlds not yet loaded; never drop them on save. */
    private final List<Map<String, Object>> unresolvedStations = new ArrayList<>();

    private record Recipe(String id, String name, List<Material> ingredients,
                          int stirFrom, int stirUntil, int readyFrom, int readyUntil,
                          int burnAt, String hint, String normal, String exquisite, String overcooked,
                          int ingredientTier, String utility, String effect, Material icon) {
        String output(String quality) {
            return switch (quality) {
                case "exquisito" -> exquisite;
                case "pasado" -> overcooked;
                default -> normal;
            };
        }
    }

    private static final class Station {
        final Location blockLocation;  // integer coordinates: origin at lower NW corner
        String recipeId;
        int accepted;
        int portions = 1;
        int readyServings;
        String batchQuality;
        boolean stirred;
        boolean preciseStir;
        boolean cooking;
        String lastCueStage;
        long elapsedMs;
        long idleMs;
        long lastWallMs;
        String lastLabel;
        Station(Location blockLocation) {
            this.blockLocation = blockLocation;
            this.lastWallMs = System.currentTimeMillis();
        }
        void reset() {
            recipeId = null; accepted = 0; portions = 1; readyServings = 0; batchQuality = null;
            stirred = false; preciseStir = false; cooking = false; lastCueStage = null;
            elapsedMs = 0; idleMs = 0; lastWallMs = System.currentTimeMillis();
        }
    }

    public void enable() {
        displayStationKey = new NamespacedKey(plugin, "cooking_visual_station");
        displayRoleKey = new NamespacedKey(plugin, "cooking_visual_role");
        File directory = new File(plugin.getDataFolder(), "cooking");
        if (!directory.exists() && !directory.mkdirs()) getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-001", "No se pudo crear carpeta cooking."));
        recipesFile = plugin.moduleConfigs().file("cocina/recetas.yml");
        stationsFile = new File(directory, "stations.yml");
        discoveriesFile = new File(directory, "discoveries.yml");
        claimJournal = new ClaimJournal(new File(directory, "claims.yml").toPath());
        migrateLegacyCookingData();
        loadRecipes();
        loadRecipeBookLayout();
        stationStorageReady = loadStations() && claimJournal.load();
        if (!claimJournal.ready()) getLogger().severe("COCINA: almacenamiento de transacciones dañado o ilegible. Operaciones desactivadas.");
        if (claimJournal.count() > 0) getLogger().warning("COCINA: " + claimJournal.count() + " transacciones interrumpidas; usa /spectrecore cocina transacciones.");
        if (!stationStorageReady) {
            getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-023",
                    "Cocina desactivada: stations.yml no es legible. Recupera el archivo antes de reiniciar."));
            return;
        }
        loadDiscoveries();
        getServer().getPluginManager().registerEvents(this, plugin);
        if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("cocina") != null) {
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("cocina").setExecutor(this);
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("cocina").setTabCompleter(this);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickStations, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::animateStations, 20L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Station station : stations.values()) if (chunkLoaded(station.blockLocation)) repairVisual(station);
        }, 30L);
        getLogger().info(Settings.plain("cocina", "CookingModule.mensaje-002", "Cocina: {stations-size} estaciones, {recipes-size} recetas.", "stations-size", stations.size(), "recipes-size", recipes.size()));
    }

    public void disable() {
        if (stationStorageReady) saveStations();
        saveDiscoveries();
        // Displays are persistent: they'll be reconciled on enable and chunk load.
        textDisplays.clear();
        liquidDisplays.clear();
        ingredientDisplays.clear();
    }

    public void reload() {
        loadRecipes(); loadRecipeBookLayout();
        for (Station station : stations.values()) updateLabel(station);
    }
    public int stationCount() { return stations.size(); }
    public int pendingClaims() { return claimJournal == null ? -1 : claimJournal.count(); }
    public boolean storageReady() { return stationStorageReady && claimJournal != null && claimJournal.ready(); }

    /** Prevent combat abilities from detecting cooking actions as combat right-clicks. */
    public boolean isCookingStation(Block block) {
        return block != null && stations.containsKey(key(block.getLocation()));
    }

    /** Also intercept the FIRST click with the lighter, before the station exists. */
    public boolean isCookingActivation(Block block, ItemStack held) {
        return block != null && block.getType() == Material.WATER_CAULDRON && held != null
                && held.getType().name().equals(Settings.module("cocina/config.yml").getString("herramientas.crear","FLINT_AND_STEEL"));
    }

    private void migrateLegacyCookingData() {
        File old = new File(plugin.getDataFolder().getParentFile(), "SpectreCooking");
        // Sólo copia si SpectreCore aún no contiene esos datos. No modifica el original.
        for (String filename : List.of("recipes.yml", "stations.yml")) {
            File previous = new File(old, filename);
            File destination = filename.equals("recipes.yml") ? recipesFile : stationsFile;
            if (previous.isFile() && !destination.exists()) {
                try {
                    Files.copy(previous.toPath(), destination.toPath());
                    getLogger().info(Settings.plain("cocina", "CookingModule.etiqueta-024", "Cocina migrada: ") + previous + " -> " + destination);
                } catch (IOException ex) {
                    getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-003", "No se pudo copiar {filename}: {ex-getmessage}", "filename", filename, "ex-getmessage", ex.getMessage()));
                }
            }
        }
    }

    private void loadRecipes() {
        recipes.clear();
        Set<Material> firstIngredients = new HashSet<>();
        Set<String> outputIds = new HashSet<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(recipesFile);
        ConfigurationSection root = yaml.getConfigurationSection("recetas");
        if (root == null) { getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-004", "recipes.yml no contiene 'recetas'.")); return; }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            try {
                List<Material> ingredients = new ArrayList<>();
                for (String token : sec.getStringList("ingredientes")) {
                    Material material = Material.matchMaterial(token);
                    if (material == null || material == Material.AIR || !material.isItem())
                        throw new IllegalArgumentException(Settings.text("cocina", "CookingModule.etiqueta-025", "Ingrediente desconocido: ") + token);
                    ingredients.add(material);
                }
                if (ingredients.size() != 3) throw new IllegalArgumentException("Exige 3 ingredientes");
                if (!firstIngredients.add(ingredients.getFirst()))
                    throw new IllegalArgumentException("Ingrediente inicial duplicado: " + ingredients.getFirst()
                            + " (la receta no podría distinguirse)");
                int a = sec.getInt("revolver-desde", -1), b = sec.getInt("revolver-hasta", -1);
                int c = sec.getInt("listo-desde", -1), d = sec.getInt("listo-hasta", -1);
                int e = sec.getInt("quemado", -1);
                if (!(a >= 0 && a < b && b <= c && c < d && d < e))
                    throw new IllegalArgumentException(Settings.text("cocina", "CookingModule.etiqueta-026", "Ventanas de coccion invalidas"));
                String normal = required(sec, "resultado-normal");
                String exquisite = required(sec, "resultado-exquisito");
                String overcooked = required(sec, "resultado-pasado");
                for (String reward : List.of(normal, exquisite, overcooked))
                    if (!outputIds.add(reward))
                        throw new IllegalArgumentException("Resultado compartido: " + reward);
                int ingredientTier = sec.getInt("nivel-ingredientes", 1);
                if (ingredientTier < 1 || ingredientTier > 5)
                    throw new IllegalArgumentException("Grado de ingredientes fuera de rango");
                Material icon = Material.matchMaterial(sec.getString("icono", "MUSHROOM_STEW"));
                if (icon == null || !icon.isItem()) throw new IllegalArgumentException("Icono inválido");
                Recipe recipe = new Recipe(id, sec.getString("nombre", id), List.copyOf(ingredients),
                        a, b, c, d, e, sec.getString("pista", defaultHint(id)), normal, exquisite, overcooked,
                        ingredientTier, sec.getString("uso", "Clásicos"),
                        sec.getString("efecto-descripcion", "Una ayuda en tus aventuras"), icon);
                recipes.put(id, recipe);
            } catch (Exception ex) {
                getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-005", "Receta '{id}' ignorada: {ex-getmessage}", "id", id, "ex-getmessage", ex.getMessage()));
            }
        }
    }

    private static String defaultHint(String id) {
        return switch (id) {
            case "guardian" -> "Una cosecha de tierra y el vigor de una bestia.";
            case "alba" -> "El amanecer favorece los ingredientes dorados.";
            case "carmesi" -> "Raíces rojas y un ingrediente del Nether.";
            case "espectral" -> "El bosque, los huesos y una lágrima perdida.";
            default -> Settings.text("cocina", "CookingModule.etiqueta-027", "Su aroma todavía es un misterio.");
        };
    }

    private static String required(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null || !value.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("ID faltante/invalido: " + key);
        return value;
    }

    private String key(Location blockLocation) {
        Location l = blockLocation.getBlock().getLocation();
        return Objects.requireNonNull(l.getWorld()).getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private boolean loadStations() {
        stations.clear();
        unresolvedStations.clear();
        YamlConfiguration yaml = new YamlConfiguration();
        if (stationsFile.exists() && !stationsFile.isFile()) {
            getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-025",
                    "La ruta de stations.yml existe pero no es un archivo regular."));
            return false;
        }
        if (stationsFile.isFile()) {
            try { yaml.load(stationsFile); }
            catch (IOException | InvalidConfigurationException failure) {
                getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-024",
                        "No se pudo leer stations.yml: {error}", "error", failure.getMessage()));
                return false; // NEVER silently replace corrupt existing state with an empty file.
            }
        }
        ConfigurationSection root = yaml.getConfigurationSection("stations");
        if (yaml.contains("stations") && root == null) {
            getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-026",
                    "La sección stations no tiene un formato válido. Cocina desactivada."));
            return false;
        }
        if (root == null) return true;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                String worldId = s.getString("world");
                if (worldId == null) throw new IllegalArgumentException(Settings.text("cocina", "CookingModule.etiqueta-028", "Falta UUID del mundo"));
                World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world == null) {
                    getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-006", "Estacion pendiente, mundo no cargado (se conservara el registro): {id}", "id", id));
                    unresolvedStations.add(new LinkedHashMap<>(s.getValues(false)));
                    continue;
                }
                Location l = new Location(world, s.getInt("x"), s.getInt("y"), s.getInt("z"));
                Station station = new Station(l);
                String recipeId = s.getString("recipe", "");
                station.recipeId = recipes.containsKey(recipeId) ? recipeId : null;
                station.portions = CookingBatch.portionsFromWater(s.getInt("portions", 1));
                station.accepted = station.recipeId == null ? 0 : Math.max(0, Math.min(
                        CookingBatch.requiredIngredients(station.portions), s.getInt("accepted")));
                station.readyServings = Math.max(0, Math.min(station.portions, s.getInt("ready-servings", 0)));
                station.batchQuality = station.readyServings > 0 ? s.getString("batch-quality", "normal") : null;
                station.stirred = s.getBoolean("stirred", false);
                station.preciseStir = s.getBoolean("precise-stir", station.stirred);
                station.cooking = station.recipeId != null && CookingBatch.readyToCook(station.accepted, station.portions)
                        && s.getBoolean("cooking", false);
                if (!station.cooking) { station.readyServings = 0; station.batchQuality = null; }
                station.elapsedMs = Math.max(0L, s.getLong("elapsed-ms", 0L));
                station.idleMs = Math.max(0L, s.getLong("idle-ms", 0L));
                stations.put(key(l), station);
            } catch (Exception ex) {
                unresolvedStations.add(new LinkedHashMap<>(s.getValues(false)));
                getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-007", "Estacion corrupta conservada {id}: {ex-getmessage}", "id", id, "ex-getmessage", ex.getMessage()));
            }
        }
        return true;
    }

    private boolean saveStations() {
        YamlConfiguration yaml = new YamlConfiguration();
        int n = 0;
        for (Station station : stations.values()) {
            String path = "stations.s" + n++;
            Location l = station.blockLocation;
            yaml.set(path + ".world", Objects.requireNonNull(l.getWorld()).getUID().toString());
            yaml.set(path + ".x", l.getBlockX()); yaml.set(path + ".y", l.getBlockY()); yaml.set(path + ".z", l.getBlockZ());
            yaml.set(path + ".recipe", station.recipeId == null ? "" : station.recipeId);
            yaml.set(path + ".accepted", station.accepted);
            yaml.set(path + ".portions", station.portions);
            yaml.set(path + ".ready-servings", station.readyServings);
            yaml.set(path + ".batch-quality", station.batchQuality == null ? "" : station.batchQuality);
            yaml.set(path + ".stirred", station.stirred);
            yaml.set(path + ".precise-stir", station.preciseStir);
            yaml.set(path + ".cooking", station.cooking);
            yaml.set(path + ".elapsed-ms", station.elapsedMs);
            yaml.set(path + ".idle-ms", station.idleMs);
        }
        for (Map<String, Object> raw : unresolvedStations) {
            String path = "stations.s" + n++;
            for (Map.Entry<String, Object> entry : raw.entrySet()) yaml.set(path + "." + entry.getKey(), entry.getValue());
        }
        if (!stationStorageReady) return false;
        long start = plugin instanceof dev.hydev.spectrecore.SpectreCorePlugin core ? core.diagnostics().start() : 0L;
        try {
            AtomicFileStore.writeUtf8(stationsFile.toPath(), yaml.saveToString());
            if (plugin instanceof dev.hydev.spectrecore.SpectreCorePlugin core) core.diagnostics().record("cocina.save", start, true);
            return true;
        }
        catch (IOException ex) {
            if (plugin instanceof dev.hydev.spectrecore.SpectreCorePlugin core) core.diagnostics().record("cocina.save", start, false);
            stationStorageReady = false; // Do not allow further item-consuming interactions.
            getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-008", "No se pudo guardar stations.yml: {ex-getmessage}", "ex-getmessage", ex.getMessage()));
            return false;
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Station station = stations.get(key(block.getLocation()));
        if (station == null && (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE))
            station = stations.get(key(block.getRelative(BlockFace.UP).getLocation()));
        if (station != null) {
            event.setCancelled(true);
            msg(event.getPlayer(), ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-029", "Retira primero la cocina con una esponja (clic derecho)."));
        }
    }

    private boolean isProtected(Block block) {
        if (stations.containsKey(key(block.getLocation()))) return true;
        return (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE)
                && stations.containsKey(key(block.getRelative(BlockFace.UP).getLocation()));
    }
    @EventHandler(ignoreCancelled = true) public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }
    @EventHandler(ignoreCancelled = true) public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!stationStorageReady || stations.isEmpty() || !nearStation(player.getLocation(), 6)) return;
        Item entity = event.getItemDrop();
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                if (!entity.isValid() || ++attempts > 30) { cancel(); return; }
                Station station = stationAtDrop(entity.getLocation());
                if (station == null) return;
                processIngredient(station, entity, player);
                cancel();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private boolean nearStation(Location pos, double distance) {
        for (Station station : stations.values()) {
            if (sameWorld(pos, station.blockLocation) && pos.distanceSquared(station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-001", .5), Settings.d("cocina", "CookingModule.geometria-002", .5), Settings.d("cocina", "CookingModule.geometria-003", .5))) <= distance * distance)
                return true;
        }
        return false;
    }
    private boolean sameWorld(Location a, Location b) { return a.getWorld() != null && a.getWorld().equals(b.getWorld()); }
    private Station stationAtDrop(Location pos) {
        for (Station station : stations.values()) {
            if (!sameWorld(pos, station.blockLocation)) continue;
            Location b = station.blockLocation;
            double dx = pos.getX() - (b.getX() + Settings.d("cocina", "CookingModule.geometria-004", .5)), dz = pos.getZ() - (b.getZ() + Settings.d("cocina", "CookingModule.geometria-005", .5));
            double dy = pos.getY() - (b.getY() + Settings.d("cocina", "CookingModule.geometria-006", .5));
            // Include the basin surface at all three cauldron fill levels.
            if (Math.abs(dx) < Settings.d("cocina", "CookingModule.geometria-007", .43) && Math.abs(dz) < Settings.d("cocina", "CookingModule.geometria-008", .43) && dy >= -Settings.d("cocina", "CookingModule.geometria-009", .05) && dy <= Settings.d("cocina", "CookingModule.geometria-010", 1.03)) return station;
        }
        return null;
    }
    private void processIngredient(Station station, Item entity, Player player) {
        if (!stationStorageReady || claimJournal.blocked(key(station.blockLocation))) return;
        if (station.cooking) { msg(player, ChatColor.YELLOW + Settings.text("cocina", "CookingModule.etiqueta-030", "Este caldero ya esta cocinando.")); return; }
        if (station.blockLocation.getBlock().getType() != Material.WATER_CAULDRON) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-031", "Esta estacion necesita un caldero con agua.")); return;
        }
        ItemStack stack = entity.getItemStack();
        if (stack.getAmount() < 1 || stack.getType() == Material.AIR) return;
        if (!getConfig().getBoolean("cooking.ingredients.allow-items-with-custom-meta", false) && stack.hasItemMeta()) {
            msg(player, ChatColor.GRAY + Settings.text("cocina", "CookingModule.etiqueta-032", "Este ingrediente tiene datos personalizados y no se consumira.")); return;
        }
        Recipe recipe = station.recipeId == null ? null : recipes.get(station.recipeId);
        if (recipe == null) {
            for (Recipe candidate : recipes.values()) {
                if (candidate.ingredients().getFirst() == stack.getType()) { recipe = candidate; break; }
            }
        }
        if (recipe == null) { msg(player, ChatColor.GRAY + Settings.text("cocina", "CookingModule.etiqueta-033", "Este ingrediente no inicia ninguna receta.")); return; }
        int portions = station.recipeId == null
                ? (getConfig().getBoolean("cooking.servings.water-level-portions", true)
                    ? waterPortions(station.blockLocation.getBlock()) : 1) : station.portions;
        int ingredientIndex = CookingBatch.ingredientIndex(station.accepted, portions);
        if (ingredientIndex < 0) return;
        Material expected = recipe.ingredients().get(ingredientIndex);
        if (stack.getType() != expected) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-034", "Ingrediente incorrecto. No se ha consumido."));
            sound(station, Settings.sound("cocina", "CookingModule.sound-001", Sound.BLOCK_NOTE_BLOCK_BASS), Settings.f("cocina", "CookingModule.geometria-011", 0.7f));
            return;
        }
        int consumed = CookingBatch.acceptedFromStack(station.accepted, portions, stack.getAmount());
        if (consumed <= 0) return;
        // Journal before changing world or persistent state; interrupted operations
        // are quarantined for manual reconciliation, never auto-replayed.
        UUID claim = claimJournal.begin(key(station.blockLocation), player.getUniqueId(),
                "INGREDIENT", recipe.id() + ":" + expected.name(), consumed);
        if (claim == null) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-030", "§cCocina bloqueada: transacción pendiente o error de almacenamiento."));
            return;
        }
        station.recipeId = recipe.id();
        station.portions = portions;
        station.accepted += consumed;
        station.idleMs = 0;
        station.lastWallMs = System.currentTimeMillis();
        syncIngredientVisuals(station);
        updateLiquid(station);
        sound(station, Settings.sound("cocina", "CookingModule.sound-002", Sound.BLOCK_BREWING_STAND_BREW), Settings.f("cocina", "CookingModule.geometria-012", 1.25f));
        if (CookingBatch.readyToCook(station.accepted, portions)) {
            station.cooking = true; station.elapsedMs = 0; station.stirred = false; station.preciseStir = false;
            label(station, Settings.text("cocina", "CookingModule.etiqueta-035", "§6♨ §e") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-036", " §7| Calentando ") + portions + " raciones...");
            msg(player, ChatColor.GOLD + Settings.text("cocina", "CookingModule.etiqueta-037", "¡Preparacion de ") + portions + Settings.text("cocina", "CookingModule.etiqueta-038", " raciones iniciada! Vigila el caldero."));
        } else {
            int required = CookingBatch.requiredIngredients(portions);
            int next = CookingBatch.ingredientIndex(station.accepted, portions);
            label(station, Settings.text("cocina", "CookingModule.etiqueta-039", "§e") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-040", " §7| §f") + station.accepted + "/" + required + " ingredientes");
            msg(player, ChatColor.GREEN + "Ingredientes " + station.accepted + "/" + required + ". "
                    + ChatColor.GRAY + "Siguiente: " + ChatColor.WHITE + recipe.ingredients().get(next));
        }
        if (!saveStations()) return;
        try {
            entity.remove();
            if (stack.getAmount() > consumed) {
                ItemStack remainder = stack.clone();
                remainder.setAmount(stack.getAmount() - consumed);
                for (ItemStack extra : player.getInventory().addItem(remainder).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        } catch (RuntimeException ex) {
            getLogger().severe("COCINA: transacción de ingrediente incierta " + claim + ": " + ex.getMessage());
            return;
        }
        if (!claimJournal.finish(claim)) getLogger().severe("COCINA: falta confirmar ingrediente " + claim);
    }

    private int waterPortions(Block cauldron) {
        return cauldron.getBlockData() instanceof Levelled level
                ? CookingBatch.portionsFromWater(level.getLevel()) : 1;
    }

    // Even a valid right-click can arrive pre-cancelled if vanilla predicts no action
    // (e.g. a sponge on a cauldron). Do not use ignoreCancelled=true here.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (!stationStorageReady) {
            if (stations.containsKey(key(block.getLocation())) || block.getType() == Material.WATER_CAULDRON) {
                event.setCancelled(true);
                msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-122",
                        "Cocina en mantenimiento: error de persistencia. Avisa a administración."));
            }
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        Station station = stations.get(key(block.getLocation()));
        if (station != null && claimJournal.blocked(key(station.blockLocation))) {
            event.setCancelled(true);
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-031", "§cEsta estación requiere revisión administrativa antes de usarla."));
            return;
        }
        Recipe recipe = station == null || station.recipeId == null ? null : recipes.get(station.recipeId);
        if (station != null) accrue(station, System.currentTimeMillis());
        boolean burned = station != null && station.cooking && station.readyServings == 0 && recipe != null
                && station.elapsedMs >= recipe.burnAt() * 1000L;
        CookingToolActions.Use use = dev.hydev.spectrecore.config.Gameplay.cookingTool(held.getType().name(), station != null, burned);

        if (station == null) {
            if (block.getType() != Material.WATER_CAULDRON || use != CookingToolActions.Use.CREATE) return;
            if (claimJournal.blocked(key(block.getLocation()))) {
                event.setCancelled(true);
                msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-031", "§cEsta estación requiere revisión administrativa antes de usarla."));
                return;
            }
            event.setCancelled(true);
            registerStation(player, block);
            return;
        }
        // Station right-click never triggers vanilla cauldron behavior (water changes etc.).
        event.setCancelled(true);
        if (held.getType() == Material.AIR) {
            inspectStation(player, station, player.isSneaking());
            return;
        }
        if (held.getType() == Material.BOOK) {
            openRecipeBook(player);
            return;
        }
        if (use == CookingToolActions.Use.CLEAN) {
            station.reset(); updateLabel(station); syncIngredientVisuals(station); updateLiquid(station); saveStations();
            sound(station, Settings.sound("cocina", "CookingModule.sound-003", Sound.BLOCK_FIRE_EXTINGUISH), Settings.f("cocina", "CookingModule.geometria-013", 1.1f));
            particleBurst(station, Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-001-arg0", 154),Settings.i("cocina", "CookingModule.fromrgb-002-arg1", 211),Settings.i("cocina", "CookingModule.fromrgb-003-arg2", 240)), 16);
            msg(player, ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-041", "Limpiaste el guiso quemado con la esponja. La cocina sigue instalada."));
            return;
        }
        if (use == CookingToolActions.Use.REMOVE) {
            if (station.readyServings > 0 && !player.isSneaking()) {
                msg(player, ChatColor.YELLOW + "Quedan " + station.readyServings + Settings.text("cocina", "CookingModule.etiqueta-042", " platos por servir. ")
                        + Settings.text("cocina", "CookingModule.etiqueta-043", "Para descartar el lote y retirar la cocina, usa Shift + esponja."));
                return;
            }
            unregisterStation(player, block, station);
            return;
        }
        if (!station.cooking || recipe == null) {
            msg(player, ChatColor.GRAY + Settings.text("cocina", "CookingModule.etiqueta-044", "Arroja los tres ingredientes en orden. ")
                    + Settings.text("cocina", "CookingModule.etiqueta-045", "Usa un mechero para crear una cocina y una esponja para retirarla."));
            return;
        }
        if (use == CookingToolActions.Use.STIR) {
            if (!station.stirred && station.elapsedMs >= recipe.stirFrom() * 1000L
                    && station.elapsedMs < recipe.stirUntil() * 1000L) {
                station.stirred = true;
                station.preciseStir = CookingTiming.isPrecise(station.elapsedMs, recipe.stirFrom(), recipe.stirUntil(),
                        getConfig().getDouble("cooking.precision.stir-sweetspot-fraction", .60));
                label(station, station.preciseStir ? Settings.text("cocina", "CookingModule.etiqueta-046", "§a✦ Revolvimiento perfecto") : Settings.text("cocina", "CookingModule.etiqueta-047", "§e♨ Mezcla revuelta"));
                sound(station, Settings.sound("cocina", "CookingModule.sound-004", Sound.BLOCK_BREWING_STAND_BREW), Settings.f("cocina", "CookingModule.geometria-014", 1.6f));
                msg(player, station.preciseStir ? ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-048", "¡Revolvimiento perfecto! Vas por un plato exquisito.")
                        : ChatColor.YELLOW + Settings.text("cocina", "CookingModule.etiqueta-049", "Has revuelto, pero la mezcla no quedo perfecta."));
                particleBurst(station, station.preciseStir ? Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-004-arg0", 92),Settings.i("cocina", "CookingModule.fromrgb-005-arg1", 238),Settings.i("cocina", "CookingModule.fromrgb-006-arg2", 149)) : Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-007-arg0", 247),Settings.i("cocina", "CookingModule.fromrgb-008-arg1", 183),Settings.i("cocina", "CookingModule.fromrgb-009-arg2", 69)), 18);
                saveStations();
            } else msg(player, ChatColor.GRAY + Settings.text("cocina", "CookingModule.etiqueta-050", "No es el momento de revolver, o ya lo hiciste."));
            return;
        }
        if (use != CookingToolActions.Use.SERVE) {
            msg(player, ChatColor.GRAY + Settings.text("cocina", "CookingModule.etiqueta-051", "Usa una pala de madera para revolver, un cuenco para servir ")
                    + Settings.text("cocina", "CookingModule.etiqueta-052", "o una esponja para limpiar/retirar."));
            return;
        }
        if (burned) {
            // A burned meal NEVER disappears by waiting or by trying to serve it.
            msg(player, ChatColor.DARK_RED + Settings.text("cocina", "CookingModule.etiqueta-053", "Preparacion quemada. Limpia el caldero con una esponja."));
            return;
        }
        if (station.readyServings == 0 && station.elapsedMs < recipe.readyFrom() * 1000L) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-054", "Todavia esta crudo.")); return;
        }
        boolean firstServing = station.readyServings == 0;
        String quality = firstServing
                ? CookingTiming.quality(station.elapsedMs, recipe.readyFrom(), recipe.readyUntil(),
                    station.stirred, station.preciseStir, getConfig().getBoolean("cooking.precision.enabled", true))
                : station.batchQuality;
        String rewardId = recipe.output(quality);
        ItemStack reward = resolveMMOItem(rewardId);
        if (reward == null || reward.getType() == Material.AIR) {
            player.sendMessage(Settings.message("cocina", "CookingModule.plato-no-disponible",
                    "<red>No se pudo servir {nombre}. Tu cuenco y la preparación siguen intactos.",
                    "nombre", recipe.name()));
            getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-009", "No se pudo generar la recompensa culinaria: {rewardid}", "rewardid", rewardId));
            return;
        }
        // Durable claim -> station commit -> bowl + reward -> claim clearance.
        UUID servingClaim = claimJournal.begin(key(station.blockLocation), player.getUniqueId(),
                "SERVE", recipe.id() + ":" + quality + ":" + rewardId, 1);
        if (servingClaim == null) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-030", "§cCocina bloqueada: transacción pendiente o error de almacenamiento."));
            return;
        }
        if (firstServing) {
            station.readyServings = station.portions;
            station.batchQuality = quality;
        }
        station.readyServings--;
        int leftAfterServing = station.readyServings;
        if (station.readyServings == 0) station.reset();
        if (!saveStations()) return;
        try {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                else { ItemStack newHand = held.clone(); newHand.setAmount(held.getAmount() - 1); player.getInventory().setItemInMainHand(newHand); }
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
            for (ItemStack remaining : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), remaining);
        } catch (RuntimeException error) {
            getLogger().severe("COCINA: entrega incierta " + servingClaim + ": " + error.getMessage());
            return;
        }
        if (!claimJournal.finish(servingClaim)) getLogger().severe("COCINA: falta confirmar entrega " + servingClaim);
        if (firstServing && quality.equals("exquisito")) exquisiteCelebration(station, player, recipe);
        else sound(station, Settings.sound("cocina", "CookingModule.sound-005", Sound.ENTITY_PLAYER_LEVELUP), Settings.f("cocina", "CookingModule.geometria-015", 1.5f));
        msg(player, ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-057", "Comida obtenida: ") + recipe.name() + " " + ChatColor.GOLD + "(" + quality + ")"
                + ChatColor.GRAY + " | Restantes: " + leftAfterServing);
        Location completedAt = station.blockLocation.clone();
        String completedRecipe = recipe.id();
        if (!quality.equals("exquisito") || !firstServing)
            particleBurst(station, quality.equals("exquisito") ? Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-010-arg0", 255),Settings.i("cocina", "CookingModule.fromrgb-011-arg1", 211),Settings.i("cocina", "CookingModule.fromrgb-012-arg2", 62)) : Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-013-arg0", 255),Settings.i("cocina", "CookingModule.fromrgb-014-arg1", 219),Settings.i("cocina", "CookingModule.fromrgb-015-arg2", 111)), 16);
        discoverRecipe(player, completedRecipe);
        updateLabel(station); syncIngredientVisuals(station); updateLiquid(station);
        Bukkit.getPluginManager().callEvent(new CookingCompletedEvent(player, completedRecipe, quality, completedAt));
    }

    private void registerStation(Player player, Block cauldron) {
        Block below = cauldron.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.CAMPFIRE && below.getType() != Material.SOUL_CAMPFIRE) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-058", "Coloca el caldero CON AGUA justo encima de una fogata.")); return;
        }
        if (getConfig().getBoolean("cooking.stations.require-lit-campfire-on-create", true)
                && (!(below.getBlockData() instanceof Campfire fire) || !fire.isLit())) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-059", "Enciende primero la fogata debajo del caldero.")); return;
        }
        Location l = cauldron.getLocation();
        if (!worldGuard.canBuild(player, l)) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-039", "No puedes colocar ni retirar estaciones en esta región."));
            return;
        }
        double min = getConfig().getDouble("cooking.stations.minimum-separation", 3.0);
        for (Station other : stations.values()) {
            if (sameWorld(l, other.blockLocation) && l.distanceSquared(other.blockLocation) < min * min) {
                msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-060", "Debe haber al menos ") + min + Settings.text("cocina", "CookingModule.etiqueta-061", " bloques entre cocinas.")); return;
            }
        }
        Station station = new Station(l);
        stations.put(key(l), station);
        if (!saveStations()) { stations.remove(key(l)); return; }
        repairVisual(station);
        sound(station, Settings.sound("cocina", "CookingModule.sound-006", Sound.BLOCK_BREWING_STAND_BREW), Settings.f("cocina", "CookingModule.geometria-016", .9f));
        particleBurst(station, Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-016-arg0", 255),Settings.i("cocina", "CookingModule.fromrgb-017-arg1", 194),Settings.i("cocina", "CookingModule.fromrgb-018-arg2", 83)), 18);
        msg(player, ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-062", "Cocina registrada con el mechero. Arroja ingredientes al caldero."));
    }

    private void unregisterStation(Player player, Block cauldron, Station station) {
        if (!worldGuard.canBuild(player, station.blockLocation)) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-039", "No puedes colocar ni retirar estaciones en esta región."));
            return;
        }
        UUID removalClaim = claimJournal.begin(key(station.blockLocation), player.getUniqueId(), "REMOVE", "station", 1);
        if (removalClaim == null) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.mensaje-030", "§cCocina bloqueada: transacción pendiente o error de almacenamiento."));
            return;
        }
        stations.remove(key(station.blockLocation));
        if (!saveStations()) { stations.put(key(station.blockLocation), station); return; }
        // No refunds after the first dish was delivered, preventing an item duplication exploit.
        if (station.recipeId != null && station.accepted > 0 && station.readyServings == 0) {
            Recipe recipe = recipes.get(station.recipeId);
            if (recipe != null) {
                for (int i = 0; i < station.accepted; i++) {
                    int index = CookingBatch.ingredientIndex(i, station.portions);
                    if (index < 0) continue;
                    cauldron.getWorld().dropItemNaturally(station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-017", .5), Settings.d("cocina", "CookingModule.geometria-018", 1.05), Settings.d("cocina", "CookingModule.geometria-019", .5)),
                            new ItemStack(recipe.ingredients().get(index)));
                }
            }
        }
        removeVisual(station);
        if (!claimJournal.finish(removalClaim)) getLogger().severe("COCINA: retiro pendiente " + removalClaim);
        cauldron.getWorld().playSound(station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-020", .5), Settings.d("cocina", "CookingModule.geometria-021", .5), Settings.d("cocina", "CookingModule.geometria-022", .5)),
                Settings.sound("cocina", "CookingModule.sound-007", Sound.BLOCK_FIRE_EXTINGUISH),Settings.f("cocina", "CookingModule.playsound-019-arg2", 1.0f),Settings.f("cocina", "CookingModule.playsound-020-arg3", .8f));
        msg(player, ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-063", "Cocina retirada con la esponja. Los bloques siguen intactos."));
    }

    private ItemStack resolveMMOItem(String id) {
        Plugin mmo = getServer().getPluginManager().getPlugin("MMOItems");
        if (mmo == null || !mmo.isEnabled()) return null;
        try {
            Class<?> api = Class.forName("net.Indyuce.mmoitems.MMOItems", true, mmo.getClass().getClassLoader());
            Object instance = api.getField("plugin").get(null);
            Method getItem = api.getMethod("getItem", String.class, String.class);
            Object value = getItem.invoke(instance, getConfig().getString("cooking.rewards.mmoitems-type", "CONSUMABLE"), id);
            return value instanceof ItemStack stack ? stack.clone() : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-010","No se pudo preparar la recompensa: {valor1}: {ex-getmessage}","valor1", ex.getClass().getSimpleName(),"ex-getmessage", ex.getMessage()));
            return null;
        }
    }

    private void tickStations() {
        if (!stationStorageReady) return;
        long now = System.currentTimeMillis();
        boolean dirty = false;
        for (Station station : stations.values()) {
            if (claimJournal.blocked(key(station.blockLocation))) continue;
            accrue(station, now);
            if (station.recipeId == null) continue;
            Recipe recipe = recipes.get(station.recipeId);
            if (recipe == null) { station.reset(); updateLabel(station); dirty = true; continue; }
            updateAmbient(station, recipe);
            if (!station.cooking) {
                if (station.idleMs >= 1000L * getConfig().getInt("cooking.stations.incomplete-timeout-seconds", 90)) {
                    station.reset(); updateLabel(station); dirty = true;
                }
                continue;
            }
            // Burned food remains until manually cleaned with a sponge.
            updateLabel(station);
        }
        // Disk writes every 5 seconds while running, and immediately after a reset.
        if (dirty || ++saveCounter >= 5) { saveCounter = 0; saveStations(); }
    }
    /** Keep the interaction clock precise even between 1-second scheduler ticks. */
    private void accrue(Station station, long now) {
        long delta = Math.max(0L, now - station.lastWallMs);
        station.lastWallMs = now;
        if (station.cooking && station.readyServings == 0
                && (!getConfig().getBoolean("cooking.stations.require-lit-campfire-while-cooking", true) || isHeated(station))) station.elapsedMs += delta;
        else if (station.recipeId != null) station.idleMs += delta;
    }
    private boolean isHeated(Station station) {
        if (!chunkLoaded(station.blockLocation)) return false;
        Block block = station.blockLocation.getBlock().getRelative(BlockFace.DOWN);
        return (block.getType() == Material.CAMPFIRE || block.getType() == Material.SOUL_CAMPFIRE)
                && block.getBlockData() instanceof Campfire fire && fire.isLit();
    }

    private void particleBurst(Station station, Color color, int count) {
        if (!getConfig().getBoolean("cooking.visuals.particles-enabled", true) || !chunkLoaded(station.blockLocation)) return;
        Location point = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-023", .5), Settings.d("cocina", "CookingModule.geometria-024", .96), Settings.d("cocina", "CookingModule.geometria-025", .5));
        station.blockLocation.getWorld().spawnParticle(Settings.particle("cocina", "CookingModule.particle-008", Particle.DUST), point, count,Settings.d("cocina", "CookingModule.spawnparticle-021-arg3", .25),Settings.d("cocina", "CookingModule.spawnparticle-022-arg4", .12),Settings.d("cocina", "CookingModule.spawnparticle-023-arg5", .25),Settings.d("cocina", "CookingModule.spawnparticle-024-arg6", 0), new Particle.DustOptions(color,Settings.f("cocina", "CookingModule.dustoptions-025-arg1", .9f)));
    }

    private void updateAmbient(Station station, Recipe recipe) {
        updateLiquid(station);
        if (!getConfig().getBoolean("cooking.visuals.particles-enabled", true) || !chunkLoaded(station.blockLocation)) return;
        World world = station.blockLocation.getWorld();
        Location pos = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-026", .5), Settings.d("cocina", "CookingModule.geometria-027", .99), Settings.d("cocina", "CookingModule.geometria-028", .5));
        double radius = Math.max(2., getConfig().getDouble("cooking.visuals.particle-radius", 12.));
        if (world.getPlayers().stream().noneMatch(p -> p.getLocation().distanceSquared(pos) <= radius * radius)) return;
        if (recipe == null || !station.cooking) return;
        String stage;
        Color color;
        if (station.readyServings > 0) { stage = "sirviendo"; color = station.batchQuality.equals("exquisito")
                ? Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-026-arg0", 255),Settings.i("cocina", "CookingModule.fromrgb-027-arg1", 210),Settings.i("cocina", "CookingModule.fromrgb-028-arg2", 72)) : Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-029-arg0", 133),Settings.i("cocina", "CookingModule.fromrgb-030-arg1", 224),Settings.i("cocina", "CookingModule.fromrgb-031-arg2", 167)); }
        else if (!isHeated(station)) { stage = "apagada"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-032-arg0", 115),Settings.i("cocina", "CookingModule.fromrgb-033-arg1", 118),Settings.i("cocina", "CookingModule.fromrgb-034-arg2", 125)); }
        else if (station.elapsedMs >= recipe.burnAt() * 1000L) { stage = "quemado"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-035-arg0", 70),Settings.i("cocina", "CookingModule.fromrgb-036-arg1", 70),Settings.i("cocina", "CookingModule.fromrgb-037-arg2", 70)); }
        else if (station.elapsedMs >= recipe.readyUntil() * 1000L) { stage = "pasado"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-038-arg0", 207),Settings.i("cocina", "CookingModule.fromrgb-039-arg1", 105),Settings.i("cocina", "CookingModule.fromrgb-040-arg2", 55)); }
        else if (station.elapsedMs >= recipe.readyFrom() * 1000L) { stage = "listo"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-041-arg0", 92),Settings.i("cocina", "CookingModule.fromrgb-042-arg1", 227),Settings.i("cocina", "CookingModule.fromrgb-043-arg2", 133)); }
        else if (!station.stirred && station.elapsedMs >= recipe.stirFrom() * 1000L && station.elapsedMs < recipe.stirUntil() * 1000L) { stage = "revolver"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-044-arg0", 247),Settings.i("cocina", "CookingModule.fromrgb-045-arg1", 192),Settings.i("cocina", "CookingModule.fromrgb-046-arg2", 69)); }
        else { stage = "calentando"; color = Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-047-arg0", 216),Settings.i("cocina", "CookingModule.fromrgb-048-arg1", 199),Settings.i("cocina", "CookingModule.fromrgb-049-arg2", 155)); }
        world.spawnParticle(Settings.particle("cocina", "CookingModule.particle-010", Particle.DUST), pos,Settings.i("cocina", "CookingModule.spawnparticle-050-arg2", 5),Settings.d("cocina", "CookingModule.spawnparticle-051-arg3", .20),Settings.d("cocina", "CookingModule.spawnparticle-052-arg4", .03),Settings.d("cocina", "CookingModule.spawnparticle-053-arg5", .20),Settings.d("cocina", "CookingModule.spawnparticle-054-arg6", .008), new Particle.DustOptions(color,Settings.f("cocina", "CookingModule.dustoptions-055-arg1", .75f)));
        world.spawnParticle(Settings.particle("cocina", "CookingModule.particle-012", Particle.CLOUD), pos.clone().add(0, Settings.d("cocina", "CookingModule.geometria-029", .07), 0),Settings.i("cocina", "CookingModule.spawnparticle-056-arg2", 1),Settings.d("cocina", "CookingModule.spawnparticle-057-arg3", .12),Settings.d("cocina", "CookingModule.spawnparticle-058-arg4", .05),Settings.d("cocina", "CookingModule.spawnparticle-059-arg5", .12),Settings.d("cocina", "CookingModule.spawnparticle-060-arg6", .015));
        if (getConfig().getBoolean("cooking.visuals.animated-liquid", true) && isHeated(station))
            world.spawnParticle(Settings.particle("cocina", "CookingModule.particle-013", Particle.BUBBLE_POP), pos.clone().add(0, -Settings.d("cocina", "CookingModule.geometria-030", .13), 0),Settings.i("cocina", "CookingModule.spawnparticle-061-arg2", 3),Settings.d("cocina", "CookingModule.spawnparticle-062-arg3", .17),Settings.d("cocina", "CookingModule.spawnparticle-063-arg4", .025),Settings.d("cocina", "CookingModule.spawnparticle-064-arg5", .17),Settings.d("cocina", "CookingModule.spawnparticle-065-arg6", .005));
        if (!stage.equals(station.lastCueStage)) {
            if (getConfig().getBoolean("cooking.visuals.sounds-enabled", true)) {
                if (stage.equals("revolver")) sound(station, Settings.sound("cocina", "CookingModule.sound-014", Sound.BLOCK_NOTE_BLOCK_PLING), Settings.f("cocina", "CookingModule.geometria-031", 1.40f));
                if (stage.equals("listo")) sound(station, Settings.sound("cocina", "CookingModule.sound-015", Sound.BLOCK_NOTE_BLOCK_CHIME), Settings.f("cocina", "CookingModule.geometria-032", 1.65f));
                if (stage.equals("quemado")) sound(station, Settings.sound("cocina", "CookingModule.sound-016", Sound.BLOCK_FIRE_EXTINGUISH), Settings.f("cocina", "CookingModule.geometria-033", .80f));
            }
            station.lastCueStage = stage;
        }
    }

    private void updateLabel(Station station) {
        Recipe recipe = station.recipeId == null ? null : recipes.get(station.recipeId);
        if (recipe == null) { label(station, Settings.text("cocina", "CookingModule.etiqueta-064", "§6✦ Cocina Arcana §7| §f") + waterPortions(station.blockLocation.getBlock()) + " raciones"); return; }
        if (!station.cooking) { label(station, Settings.text("cocina", "CookingModule.etiqueta-065", "§e") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-066", " §7| §f") + station.accepted + "/"
                + CookingBatch.requiredIngredients(station.portions) + " ingredientes"); return; }
        if (station.readyServings > 0) {
            label(station, Settings.text("cocina", "CookingModule.etiqueta-067", "§a✦ ") + station.readyServings + Settings.text("cocina", "CookingModule.etiqueta-068", " platos §7| §f") + station.batchQuality + Settings.text("cocina", "CookingModule.etiqueta-069", " §7| Usa cuencos"));
            return;
        }
        if (station.elapsedMs >= recipe.burnAt() * 1000L) label(station, Settings.text("cocina", "CookingModule.etiqueta-070", "§4✖ Quemado §7| Usa una esponja"));
        else if (getConfig().getBoolean("cooking.stations.require-lit-campfire-while-cooking", true) && !isHeated(station)) label(station, Settings.text("cocina", "CookingModule.texto-001", "§7✖ Fogata apagada §8| Coccion pausada"));
        else if (station.elapsedMs >= recipe.readyUntil() * 1000L) label(station, Settings.text("cocina", "CookingModule.etiqueta-071", "§6⚠ Sobrecocido: sirve ya"));
        else if (station.elapsedMs >= recipe.readyFrom() * 1000L) label(station, Settings.text("cocina", "CookingModule.etiqueta-072", "§a✦ ¡LISTO PARA SERVIR!"));
        else if (!station.stirred && station.elapsedMs >= recipe.stirFrom() * 1000L && station.elapsedMs < recipe.stirUntil() * 1000L)
            label(station, CookingTiming.isPrecise(station.elapsedMs, recipe.stirFrom(), recipe.stirUntil(),
                    getConfig().getDouble("cooking.precision.stir-sweetspot-fraction", .60))
                    ? Settings.text("cocina", "CookingModule.etiqueta-073", "§a✦ ¡MOMENTO PERFECTO PARA REVOLVER!") : Settings.text("cocina", "CookingModule.etiqueta-074", "§6⚠ §eRevuelve la mezcla"));
        else if (station.stirred) label(station, station.preciseStir ? Settings.text("cocina", "CookingModule.etiqueta-075", "§a✦ Mezcla precisa §7| Esperando...") : Settings.text("cocina", "CookingModule.etiqueta-076", "§e♨ Mezcla revuelta §7| Esperando..."));
        else if (station.elapsedMs >= recipe.stirUntil() * 1000L)
            label(station, Settings.text("cocina", "CookingModule.etiqueta-077", "§6⚠ Se paso el momento de revolver"));
        else label(station, Settings.text("cocina", "CookingModule.etiqueta-078", "§6♨ §e") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-079", " §7| Calentando..."));
    }

    private void inspectStation(Player player, Station station, boolean verbose) {
        Recipe recipe = station.recipeId == null ? null : recipes.get(station.recipeId);
        String status;
        if (recipe == null) {
            status = Settings.text("cocina", "CookingModule.etiqueta-080", "§eCaldero vacío §7| §fAgua: ") + waterPortions(station.blockLocation.getBlock())
                    + Settings.text("cocina", "CookingModule.etiqueta-081", "/3 §7| Arroja ingredientes");
        } else if (!station.cooking) {
            int next = CookingBatch.ingredientIndex(station.accepted, station.portions);
            status = Settings.text("cocina", "CookingModule.etiqueta-082", "§e") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-083", " §7| §f") + station.accepted + "/"
                    + CookingBatch.requiredIngredients(station.portions) + Settings.text("cocina", "CookingModule.etiqueta-084", " §7| Siguiente: §f")
                    + (next >= 0 ? recipe.ingredients().get(next) : "-");
        } else if (station.readyServings > 0) {
            status = Settings.text("cocina", "CookingModule.etiqueta-085", "§a") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-086", " §7| §e") + station.readyServings
                    + Settings.text("cocina", "CookingModule.etiqueta-087", " raciones disponibles §7| Calidad: §f") + station.batchQuality;
        } else if (station.elapsedMs >= recipe.burnAt() * 1000L) {
            status = Settings.text("cocina", "CookingModule.etiqueta-088", "§4✖ Preparación quemada §7| Limpia con una esponja");
        } else if (!isHeated(station) && getConfig().getBoolean("cooking.stations.require-lit-campfire-while-cooking", true)) {
            status = Settings.text("cocina", "CookingModule.etiqueta-089", "§7Fogata apagada §8| §fCocción pausada, ") + station.elapsedMs / 1000L + " s";
        } else {
            int progress = (int) Math.min(100, station.elapsedMs * 100L / Math.max(1L, recipe.readyFrom() * 1000L));
            String action = station.elapsedMs >= recipe.readyFrom() * 1000L ? Settings.text("cocina", "CookingModule.etiqueta-090", "§aSirve con un cuenco")
                    : !station.stirred && station.elapsedMs >= recipe.stirFrom() * 1000L
                    && station.elapsedMs < recipe.stirUntil() * 1000L ? Settings.text("cocina", "CookingModule.etiqueta-091", "§eRevuelve con pala")
                    : Settings.text("cocina", "CookingModule.etiqueta-092", "§7Calentando");
            status = Settings.text("cocina", "CookingModule.etiqueta-093", "§6") + recipe.name() + Settings.text("cocina", "CookingModule.etiqueta-094", " §7| §f") + progress + Settings.text("cocina", "CookingModule.etiqueta-095", "% §8(") + station.elapsedMs / 1000L
                    + Settings.text("cocina", "CookingModule.etiqueta-096", " s) §7| ") + action;
        }
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(status));
        if (verbose) msg(player, status);
    }

    private void exquisiteCelebration(Station station, Player player, Recipe recipe) {
        if (getConfig().getBoolean("cooking.visuals.exquisite-celebration", true)) {
            particleBurst(station, Color.fromRGB(Settings.i("cocina", "CookingModule.fromrgb-066-arg0", 255),Settings.i("cocina", "CookingModule.fromrgb-067-arg1", 208),Settings.i("cocina", "CookingModule.fromrgb-068-arg2", 55)), 70);
            if (chunkLoaded(station.blockLocation)) {
                World world = station.blockLocation.getWorld();
                Location at = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-034", .5), Settings.d("cocina", "CookingModule.geometria-035", 1.16), Settings.d("cocina", "CookingModule.geometria-036", .5));
                world.spawnParticle(Settings.particle("cocina", "CookingModule.particle-017", Particle.END_ROD), at,Settings.i("cocina", "CookingModule.spawnparticle-069-arg2", 25),Settings.d("cocina", "CookingModule.spawnparticle-070-arg3", .38),Settings.d("cocina", "CookingModule.spawnparticle-071-arg4", .26),Settings.d("cocina", "CookingModule.spawnparticle-072-arg5", .38),Settings.d("cocina", "CookingModule.spawnparticle-073-arg6", .035));
                world.spawnParticle(Settings.particle("cocina", "CookingModule.particle-018", Particle.HAPPY_VILLAGER), at,Settings.i("cocina", "CookingModule.spawnparticle-074-arg2", 15),Settings.d("cocina", "CookingModule.spawnparticle-075-arg3", .28),Settings.d("cocina", "CookingModule.spawnparticle-076-arg4", .18),Settings.d("cocina", "CookingModule.spawnparticle-077-arg5", .28),Settings.d("cocina", "CookingModule.spawnparticle-078-arg6", .02));
            }
            player.sendTitle(Settings.text("cocina", "CookingModule.mensaje-011", "§6✦ EXQUISITO ✦"), Settings.text("cocina", "CookingModule.texto-002", "§e") + recipe.name(),Settings.i("cocina", "CookingModule.titulo-001-arg2", 5),Settings.i("cocina", "CookingModule.titulo-002-arg3", 30),Settings.i("cocina", "CookingModule.titulo-003-arg4", 10));
            if (getConfig().getBoolean("cooking.visuals.sounds-enabled", true))
                player.playSound(player.getLocation(), Settings.sound("cocina", "CookingModule.sound-019", Sound.UI_TOAST_CHALLENGE_COMPLETE),Settings.f("cocina", "CookingModule.playsound-079-arg2", .75f),Settings.f("cocina", "CookingModule.playsound-080-arg3", 1.28f));
        }
        sound(station, Settings.sound("cocina", "CookingModule.sound-020", Sound.ENTITY_PLAYER_LEVELUP), Settings.f("cocina", "CookingModule.geometria-037", 1.7f));
    }

    private void label(Station station, String text) {
        if (Objects.equals(station.lastLabel, text)) return;
        station.lastLabel = text;
        UUID id = textDisplays.get(key(station.blockLocation));
        if (id == null || !chunkLoaded(station.blockLocation)) return;
        Entity entity = Bukkit.getEntity(id);
        if (entity instanceof TextDisplay display) display.text(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private boolean chunkLoaded(Location location) {
        return location.getWorld() != null && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        for (Station station : stations.values()) {
            Location l = station.blockLocation;
            if (l.getWorld() != event.getWorld() || (l.getBlockX() >> 4) != cx || (l.getBlockZ() >> 4) != cz) continue;
            Bukkit.getScheduler().runTaskLater(plugin, () -> repairVisual(station), 2L);
        }
    }

    private void removeVisual(Station station) {
        if (!chunkLoaded(station.blockLocation)) return;
        String id = key(station.blockLocation);
        Location center = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-038", .5), Settings.d("cocina", "CookingModule.geometria-039", .5), Settings.d("cocina", "CookingModule.geometria-040", .5));
        Collection<Entity> entities = center.getWorld().getNearbyEntities(center, Settings.d("cocina", "CookingModule.geometria-041", 2.4), Settings.d("cocina", "CookingModule.geometria-042", 2.4), Settings.d("cocina", "CookingModule.geometria-043", 2.4));
        for (Entity e : entities) {
            // Legacy tag is safe here because stations have 3-block separation.
            if (e.getScoreboardTags().contains(LEGACY_TAG) || e.getScoreboardTags().contains(PREVIOUS_TAG) ||
                    (e.getScoreboardTags().contains(OUR_TAG) && id.equals(e.getPersistentDataContainer().get(displayStationKey, PersistentDataType.STRING))))
                e.remove();
        }
        textDisplays.remove(id);
        liquidDisplays.remove(id);
        ingredientDisplays.remove(id);
    }
    private void repairVisual(Station station) {
        if (!getConfig().getBoolean("cooking.visuals.enabled", true) || !chunkLoaded(station.blockLocation)) return;
        // 14 structural pieces + text + liquid + 0-3 ingredient displays.
        int found = 0; UUID textId = null; UUID liquidId = null;
        Map<Integer, UUID> icons = new HashMap<>();
        String id = key(station.blockLocation);
        Location center = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-044", .5), Settings.d("cocina", "CookingModule.geometria-045", .5), Settings.d("cocina", "CookingModule.geometria-046", .5));
        for (Entity e : center.getWorld().getNearbyEntities(center, Settings.d("cocina", "CookingModule.geometria-047", 2.4), Settings.d("cocina", "CookingModule.geometria-048", 2.4), Settings.d("cocina", "CookingModule.geometria-049", 2.4))) {
            if (e.getScoreboardTags().contains(OUR_TAG) && id.equals(e.getPersistentDataContainer().get(displayStationKey, PersistentDataType.STRING))) {
                found++;
                if (e instanceof TextDisplay) textId = e.getUniqueId();
                String role = e.getPersistentDataContainer().get(displayRoleKey, PersistentDataType.STRING);
                if ("liquid".equals(role) && e instanceof BlockDisplay) liquidId = e.getUniqueId();
                if (role != null && role.startsWith("ingredient-") && e instanceof ItemDisplay) {
                    try { icons.put(Integer.parseInt(role.substring("ingredient-".length())), e.getUniqueId()); }
                    catch (NumberFormatException ignored) { /* rebuilt below */ }
                }
            }
        }
        int needed = getConfig().getBoolean("cooking.visuals.visible-ingredients", true)
                ? CookingBatch.visibleIngredients(station.accepted, station.portions) : 0;
        if (found == 16 + needed && textId != null && liquidId != null && icons.size() == needed) {
            textDisplays.put(id, textId);
            liquidDisplays.put(id, liquidId);
            ingredientDisplays.put(id, icons);
            station.lastLabel = null;
            updateLabel(station); updateLiquid(station); return;
        }
        removeVisual(station);
        Location origin = station.blockLocation;
        // Model from Cocina Arcana 0.1.2, anchored at the BLOCK CORNER, not block center.
        piece(station, Settings.material("cocina", "CookingModule.material-171", Material.POLISHED_BLACKSTONE), -Settings.f("cocina", "CookingModule.geometria-050", .02f), Settings.f("cocina", "CookingModule.geometria-051", .90f), -Settings.f("cocina", "CookingModule.geometria-052", .02f), Settings.f("cocina", "CookingModule.geometria-053", 1.04f), Settings.f("cocina", "CookingModule.geometria-054", .07f), Settings.f("cocina", "CookingModule.geometria-055", .09f));
        piece(station, Settings.material("cocina", "CookingModule.material-172", Material.POLISHED_BLACKSTONE), -Settings.f("cocina", "CookingModule.geometria-056", .02f), Settings.f("cocina", "CookingModule.geometria-057", .90f), Settings.f("cocina", "CookingModule.geometria-058", .93f), Settings.f("cocina", "CookingModule.geometria-059", 1.04f), Settings.f("cocina", "CookingModule.geometria-060", .07f), Settings.f("cocina", "CookingModule.geometria-061", .09f));
        piece(station, Settings.material("cocina", "CookingModule.material-173", Material.POLISHED_BLACKSTONE), -Settings.f("cocina", "CookingModule.geometria-062", .02f), Settings.f("cocina", "CookingModule.geometria-063", .90f), -Settings.f("cocina", "CookingModule.geometria-064", .02f), Settings.f("cocina", "CookingModule.geometria-065", .09f), Settings.f("cocina", "CookingModule.geometria-066", .07f), Settings.f("cocina", "CookingModule.geometria-067", 1.04f));
        piece(station, Settings.material("cocina", "CookingModule.material-174", Material.POLISHED_BLACKSTONE), Settings.f("cocina", "CookingModule.geometria-068", .93f), Settings.f("cocina", "CookingModule.geometria-069", .90f), -Settings.f("cocina", "CookingModule.geometria-070", .02f), Settings.f("cocina", "CookingModule.geometria-071", .09f), Settings.f("cocina", "CookingModule.geometria-072", .07f), Settings.f("cocina", "CookingModule.geometria-073", 1.04f));
        piece(station, Settings.material("cocina", "CookingModule.material-175", Material.COPPER_BLOCK), -Settings.f("cocina", "CookingModule.geometria-074", .04f), Settings.f("cocina", "CookingModule.geometria-075", .85f), -Settings.f("cocina", "CookingModule.geometria-076", .04f), Settings.f("cocina", "CookingModule.geometria-077", .10f), Settings.f("cocina", "CookingModule.geometria-078", .12f), Settings.f("cocina", "CookingModule.geometria-079", .10f));
        piece(station, Settings.material("cocina", "CookingModule.material-176", Material.COPPER_BLOCK), Settings.f("cocina", "CookingModule.geometria-080", .94f), Settings.f("cocina", "CookingModule.geometria-081", .85f), -Settings.f("cocina", "CookingModule.geometria-082", .04f), Settings.f("cocina", "CookingModule.geometria-083", .10f), Settings.f("cocina", "CookingModule.geometria-084", .12f), Settings.f("cocina", "CookingModule.geometria-085", .10f));
        piece(station, Settings.material("cocina", "CookingModule.material-177", Material.COPPER_BLOCK), -Settings.f("cocina", "CookingModule.geometria-086", .04f), Settings.f("cocina", "CookingModule.geometria-087", .85f), Settings.f("cocina", "CookingModule.geometria-088", .94f), Settings.f("cocina", "CookingModule.geometria-089", .10f), Settings.f("cocina", "CookingModule.geometria-090", .12f), Settings.f("cocina", "CookingModule.geometria-091", .10f));
        piece(station, Settings.material("cocina", "CookingModule.material-178", Material.COPPER_BLOCK), Settings.f("cocina", "CookingModule.geometria-092", .94f), Settings.f("cocina", "CookingModule.geometria-093", .85f), Settings.f("cocina", "CookingModule.geometria-094", .94f), Settings.f("cocina", "CookingModule.geometria-095", .10f), Settings.f("cocina", "CookingModule.geometria-096", .12f), Settings.f("cocina", "CookingModule.geometria-097", .10f));
        piece(station, Settings.material("cocina", "CookingModule.material-179", Material.IRON_BLOCK), -Settings.f("cocina", "CookingModule.geometria-098", .14f), Settings.f("cocina", "CookingModule.geometria-099", .74f), Settings.f("cocina", "CookingModule.geometria-100", .33f), Settings.f("cocina", "CookingModule.geometria-101", .14f), Settings.f("cocina", "CookingModule.geometria-102", .10f), Settings.f("cocina", "CookingModule.geometria-103", .35f));
        piece(station, Settings.material("cocina", "CookingModule.material-180", Material.IRON_BLOCK), 1.00f, Settings.f("cocina", "CookingModule.geometria-104", .74f), Settings.f("cocina", "CookingModule.geometria-105", .33f), Settings.f("cocina", "CookingModule.geometria-106", .14f), Settings.f("cocina", "CookingModule.geometria-107", .10f), Settings.f("cocina", "CookingModule.geometria-108", .35f));
        // Cuatro remaches de latón, pequeños para no tapar el agua.
        piece(station, Settings.material("cocina", "CookingModule.material-181", Material.GOLD_BLOCK), Settings.f("cocina", "CookingModule.geometria-109", .09f), Settings.f("cocina", "CookingModule.geometria-110", .955f), Settings.f("cocina", "CookingModule.geometria-111", .09f), Settings.f("cocina", "CookingModule.geometria-112", .055f), Settings.f("cocina", "CookingModule.geometria-113", .035f), Settings.f("cocina", "CookingModule.geometria-114", .055f));
        piece(station, Settings.material("cocina", "CookingModule.material-182", Material.GOLD_BLOCK), Settings.f("cocina", "CookingModule.geometria-115", .86f), Settings.f("cocina", "CookingModule.geometria-116", .955f), Settings.f("cocina", "CookingModule.geometria-117", .09f), Settings.f("cocina", "CookingModule.geometria-118", .055f), Settings.f("cocina", "CookingModule.geometria-119", .035f), Settings.f("cocina", "CookingModule.geometria-120", .055f));
        piece(station, Settings.material("cocina", "CookingModule.material-183", Material.GOLD_BLOCK), Settings.f("cocina", "CookingModule.geometria-121", .09f), Settings.f("cocina", "CookingModule.geometria-122", .955f), Settings.f("cocina", "CookingModule.geometria-123", .86f), Settings.f("cocina", "CookingModule.geometria-124", .055f), Settings.f("cocina", "CookingModule.geometria-125", .035f), Settings.f("cocina", "CookingModule.geometria-126", .055f));
        piece(station, Settings.material("cocina", "CookingModule.material-184", Material.GOLD_BLOCK), Settings.f("cocina", "CookingModule.geometria-127", .86f), Settings.f("cocina", "CookingModule.geometria-128", .955f), Settings.f("cocina", "CookingModule.geometria-129", .86f), Settings.f("cocina", "CookingModule.geometria-130", .055f), Settings.f("cocina", "CookingModule.geometria-131", .035f), Settings.f("cocina", "CookingModule.geometria-132", .055f));
        BlockDisplay liquid = origin.getWorld().spawn(origin, BlockDisplay.class, e -> {
            markVisual(e, station, "liquid");
            e.setBlock(Bukkit.createBlockData(liquidMaterial(station)));
            e.setTransformation(transform(Settings.f("cocina", "CookingModule.geometria-133", .17f), liquidHeight(station), Settings.f("cocina", "CookingModule.geometria-134", .17f), Settings.f("cocina", "CookingModule.geometria-135", .66f), Settings.f("cocina", "CookingModule.geometria-136", .018f), Settings.f("cocina", "CookingModule.geometria-137", .66f)));
            e.setViewRange((float) getConfig().getDouble("cooking.visuals.view-range", .7));
            e.setInterpolationDuration(Settings.i("cocina", "CookingModule.setinterpolationduration-081-arg0", 10));
        });
        liquidDisplays.put(id, liquid.getUniqueId());
        TextDisplay text = origin.getWorld().spawn(origin.clone().add(Settings.d("cocina", "CookingModule.geometria-138", .5), Settings.d("cocina", "CookingModule.geometria-139", 1.48), Settings.d("cocina", "CookingModule.geometria-140", .5)), TextDisplay.class, e -> {
            markVisual(e, station, "label");
            e.setBillboard(Display.Billboard.CENTER);
            e.setLineWidth(160);
            e.setViewRange((float) getConfig().getDouble("cooking.visuals.view-range", .7));
            float scale = (float) getConfig().getDouble("cooking.visuals.text-scale", .34);
            e.setTransformation(transform(0, 0, 0, scale, scale, scale));
        });
        textDisplays.put(id, text.getUniqueId());
        station.lastLabel = null; updateLabel(station);
        syncIngredientVisuals(station);
    }

    private Material liquidMaterial(Station station) {
        if (station.recipeId == null) return Settings.material("cocina", "CookingModule.material-185", Material.LIGHT_BLUE_STAINED_GLASS);
        Recipe recipe = recipes.get(station.recipeId);
        if (recipe != null && station.readyServings == 0 && station.cooking
                && station.elapsedMs >= recipe.burnAt() * 1000L) return Settings.material("cocina", "CookingModule.material-186", Material.BLACK_STAINED_GLASS);
        if (station.readyServings > 0 && "exquisito".equals(station.batchQuality)) return Settings.material("cocina", "CookingModule.material-187", Material.YELLOW_STAINED_GLASS);
        return switch (station.recipeId) {
            case "guardian" -> Settings.material("cocina", "CookingModule.material-188", Material.GREEN_STAINED_GLASS);
            case "alba" -> Settings.material("cocina", "CookingModule.material-189", Material.ORANGE_STAINED_GLASS);
            case "carmesi" -> Settings.material("cocina", "CookingModule.material-190", Material.RED_STAINED_GLASS);
            case "espectral" -> Settings.material("cocina", "CookingModule.material-191", Material.PURPLE_STAINED_GLASS);
            default -> Settings.material("cocina", "CookingModule.material-192", Material.LIGHT_BLUE_STAINED_GLASS);
        };
    }

    private float liquidHeight(Station station) {
        // Match the approximate surface height of vanilla cauldrons, including partial fills.
        return Settings.f("cocina", "CookingModule.geometria-141", .41f) + Settings.f("cocina", "CookingModule.geometria-142", .145f) * (waterPortions(station.blockLocation.getBlock()) - 1);
    }

    private void updateLiquid(Station station) {
        if (!chunkLoaded(station.blockLocation)) return;
        UUID uuid = liquidDisplays.get(key(station.blockLocation));
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity instanceof BlockDisplay liquid && liquid.getBlock().getMaterial() != liquidMaterial(station))
            liquid.setBlock(Bukkit.createBlockData(liquidMaterial(station)));
    }

    private void syncIngredientVisuals(Station station) {
        if (!getConfig().getBoolean("cooking.visuals.enabled", true)
                || !getConfig().getBoolean("cooking.visuals.visible-ingredients", true)
                || !chunkLoaded(station.blockLocation)) return;
        String id = key(station.blockLocation);
        Map<Integer, UUID> icons = ingredientDisplays.computeIfAbsent(id, ignored -> new HashMap<>());
        int visible = CookingBatch.visibleIngredients(station.accepted, station.portions);
        for (int i = 0; i < 3; i++) {
            UUID existing = icons.get(i);
            Entity entity = existing == null ? null : Bukkit.getEntity(existing);
            if (i >= visible) {
                if (entity != null) entity.remove();
                icons.remove(i);
                continue;
            }
            Recipe recipe = recipes.get(station.recipeId);
            if (recipe == null) continue;
            Material material = recipe.ingredients().get(i);
            if (entity instanceof ItemDisplay item) {
                if (item.getItemStack().getType() != material) item.setItemStack(new ItemStack(material));
                continue;
            }
            final int slot = i;
            ItemDisplay created = station.blockLocation.getWorld().spawn(station.blockLocation, ItemDisplay.class, e -> {
                markVisual(e, station, "ingredient-" + slot);
                e.setItemStack(new ItemStack(material));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setViewRange((float) getConfig().getDouble("cooking.visuals.view-range", .7));
                e.setInterpolationDuration(Settings.i("cocina", "CookingModule.setinterpolationduration-082-arg0", 10));
                e.setTransformation(transform(Settings.f("cocina", "CookingModule.geometria-143", .5f), Settings.f("cocina", "CookingModule.geometria-144", .99f), Settings.f("cocina", "CookingModule.geometria-145", .5f), Settings.f("cocina", "CookingModule.geometria-146", .29f), Settings.f("cocina", "CookingModule.geometria-147", .29f), Settings.f("cocina", "CookingModule.geometria-148", .29f)));
            });
            icons.put(i, created.getUniqueId());
        }
    }

    /** Render only when someone is close; display interpolation keeps the client motion smooth. */
    private void animateStations() {
        if (!getConfig().getBoolean("cooking.visuals.enabled", true)) return;
        animationFrame++;
        for (Station station : stations.values()) {
            if (!chunkLoaded(station.blockLocation)) continue;
            World world = station.blockLocation.getWorld();
            Location center = station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-149", .5), 1, Settings.d("cocina", "CookingModule.geometria-150", .5));
            double radius = Math.max(3., getConfig().getDouble("cooking.visuals.particle-radius", 12.));
            if (world.getPlayers().stream().noneMatch(p -> p.getLocation().distanceSquared(center) < radius * radius)) continue;
            if (getConfig().getBoolean("cooking.visuals.animated-liquid", true)) {
                UUID uuid = liquidDisplays.get(key(station.blockLocation));
                Entity e = uuid == null ? null : Bukkit.getEntity(uuid);
                if (e instanceof BlockDisplay liquid) {
                    float bob = (float) (Settings.d("cocina", "CookingModule.geometria-151", .012) * Math.sin(animationFrame * Settings.d("cocina", "CookingModule.geometria-152", .52)));
                    liquid.setInterpolationDelay(0);
                    liquid.setTransformation(transform(Settings.f("cocina", "CookingModule.geometria-153", .17f), liquidHeight(station) + bob, Settings.f("cocina", "CookingModule.geometria-154", .17f), Settings.f("cocina", "CookingModule.geometria-155", .66f), Settings.f("cocina", "CookingModule.geometria-156", .018f), Settings.f("cocina", "CookingModule.geometria-157", .66f)));
                }
            }
            if (!getConfig().getBoolean("cooking.visuals.visible-ingredients", true)) continue;
            Map<Integer, UUID> icons = ingredientDisplays.getOrDefault(key(station.blockLocation), Map.of());
            int total = Math.max(1, icons.size());
            for (Map.Entry<Integer, UUID> entry : icons.entrySet()) {
                Entity e = Bukkit.getEntity(entry.getValue());
                if (!(e instanceof ItemDisplay item)) continue;
                double angle = animationFrame * Settings.d("cocina", "CookingModule.geometria-158", .26) + entry.getKey() * Math.PI * 2 / total;
                float x = (float) (Settings.d("cocina", "CookingModule.geometria-159", .5) + Settings.d("cocina", "CookingModule.geometria-160", .19) * Math.cos(angle));
                float z = (float) (Settings.d("cocina", "CookingModule.geometria-161", .5) + Settings.d("cocina", "CookingModule.geometria-162", .19) * Math.sin(angle));
                float y = (float) (1.0 + Settings.d("cocina", "CookingModule.geometria-163", .035) * Math.sin(angle * Settings.d("cocina", "CookingModule.geometria-164", 1.6)));
                item.setInterpolationDelay(0);
                item.setTransformation(new Transformation(new Vector3f(x, y, z),
                        new AxisAngle4f((float) angle, 0f, 1f, 0f),
                        new Vector3f(Settings.f("cocina", "CookingModule.geometria-165", .29f), Settings.f("cocina", "CookingModule.geometria-166", .29f), Settings.f("cocina", "CookingModule.geometria-167", .29f)), new AxisAngle4f()));
            }
        }
    }
    private void piece(Station station, Material material, float tx, float ty, float tz, float sx, float sy, float sz) {
        station.blockLocation.getWorld().spawn(station.blockLocation, BlockDisplay.class, e -> {
            markVisual(e, station);
            e.setBlock(Bukkit.createBlockData(material));
            e.setTransformation(transform(tx, ty, tz, sx, sy, sz));
            e.setViewRange((float) getConfig().getDouble("cooking.visuals.view-range", .7));
        });
    }
    private static Transformation transform(float tx, float ty, float tz, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(tx, ty, tz), new AxisAngle4f(), new Vector3f(sx, sy, sz), new AxisAngle4f());
    }
    private void markVisual(Display e, Station station) {
        markVisual(e, station, "frame");
    }
    private void markVisual(Display e, Station station, String role) {
        e.addScoreboardTag(OUR_TAG);
        e.getPersistentDataContainer().set(displayStationKey, PersistentDataType.STRING, key(station.blockLocation));
        e.getPersistentDataContainer().set(displayRoleKey, PersistentDataType.STRING, role);
        e.setPersistent(true);
    }

    private void loadDiscoveries() {
        discoveries.clear();
        YamlConfiguration yaml = new YamlConfiguration();
        try { if (discoveriesFile.isFile()) yaml.load(discoveriesFile); }
        catch (IOException | InvalidConfigurationException ex) {
            discoveriesStorageReady = false;
            getLogger().severe("COCINA: discoveries.yml dañado, guardado deshabilitado: " + ex.getMessage());
            return;
        }
        if (yaml.contains("players") && !yaml.isConfigurationSection("players")) {
            discoveriesStorageReady = false;
            getLogger().severe("COCINA: sección players inválida en discoveries.yml.");
            return;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String id : players.getKeys(false)) {
            try { discoveries.put(UUID.fromString(id), new HashSet<>(players.getStringList(id))); }
            catch (IllegalArgumentException ex) { getLogger().warning(Settings.plain("cocina", "CookingModule.mensaje-012", "UUID de cocinero invalido: {id}", "id", id)); }
        }
    }

    private void saveDiscoveries() {
        if (!discoveriesStorageReady) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : discoveries.entrySet())
            yaml.set("players." + entry.getKey(), entry.getValue().stream().sorted().toList());
        try { AtomicFileStore.writeUtf8(discoveriesFile.toPath(), yaml.saveToString()); }
        catch (IOException ex) { discoveriesStorageReady = false; getLogger().severe(Settings.plain("cocina", "CookingModule.mensaje-013", "No se pudieron guardar recetas descubiertas: {ex-getmessage}", "ex-getmessage", ex.getMessage())); }
    }

    private void discoverRecipe(Player player, String recipeId) {
        if (!discoveriesStorageReady || !Settings.module("cocina/config.yml").getBoolean("cooking.book.discovery-enabled", true)) return;
        if (!discoveries.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(recipeId)) return;
        saveDiscoveries();
        msg(player, Settings.text("cocina", "CookingModule.etiqueta-097", "§d✦ Nueva receta descubierta: §e") + recipes.get(recipeId).name()
                + Settings.text("cocina", "CookingModule.etiqueta-098", " §7| Consulta tu recetario para conocerla."));
    }


    private static final class RecipeBookHolder implements InventoryHolder {
        final String selected;
        final int page;
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory;
        RecipeBookHolder(String selected, int page) {
            this.selected = selected;
            this.page = page;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private void loadRecipeBookLayout() {
        File file = new File(plugin.getDataFolder(), "ui/recetario.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/recetario.yml", false);
            if (!file.isFile() || file.length() > 96 * 1024)
                throw new IOException("ui/recetario.yml ausente o demasiado grande");
            YamlConfiguration next = new YamlConfiguration();
            next.load(file);
            if (!next.getBoolean("enabled", true)) { recipeBookLayout = next; return; }
            for (String section : List.of("catalogo", "detalle")) {
                if (next.getInt(section + ".espacios") != 54 || !next.isString(section + ".titulo"))
                    throw new IOException("Página incompleta: " + section);
            }
            List<Integer> positions = next.getIntegerList("catalogo.posiciones");
            if (positions.size() != 15 || new HashSet<>(positions).size() != 15)
                throw new IOException("El catálogo requiere quince espacios únicos");
            Set<Integer> catalogue = new HashSet<>(positions);
            for (String key : List.of("resumen-slot","anterior-slot","siguiente-slot","cerrar-slot")) {
                int slot = next.getInt("catalogo." + key, -1);
                if (slot < 0 || slot >= 54 || !catalogue.add(slot))
                    throw new IOException("Espacio de catálogo reservado: " + key);
            }
            Set<Integer> details = new HashSet<>(next.getIntegerList("detalle.ingrediente-slots"));
            if (details.size() != 3) throw new IOException("Deben existir tres ingredientes");
            for (String key : List.of("cabecera-slot","preparacion-slot","resultado-slot","volver-slot","cerrar-slot")) {
                int slot = next.getInt("detalle." + key, -1);
                if (slot < 0 || slot >= 54 || !details.add(slot))
                    throw new IOException("Espacio de detalle reservado: " + key);
            }
            if (catalogue.stream().anyMatch(slot -> slot < 0 || slot >= 54)
                    || details.stream().anyMatch(slot -> slot < 0 || slot >= 54)
                    || !next.isConfigurationSection("iconos") || !next.isConfigurationSection("materiales"))
                throw new IOException("Configuración visual inválida");
            recipeBookLayout = next;
        } catch (Exception error) {
            getLogger().warning("Recetario: se conserva la última configuración válida: " + error.getMessage());
        }
    }

    private boolean knowsRecipe(Player player, String id) {
        return !Settings.module("cocina/config.yml").getBoolean("cooking.book.discovery-enabled", true)
                || discoveries.getOrDefault(player.getUniqueId(), Set.of()).contains(id);
    }

    private String bookText(String input, Recipe recipe, Player player, int page, int totalPages) {
        if (input == null) return "";
        long discovered = !Settings.module("cocina/config.yml").getBoolean("cooking.book.discovery-enabled", true)
                ? recipes.size() : discoveries.getOrDefault(player.getUniqueId(), Set.of()).stream()
                    .filter(recipes::containsKey).count();
        String text = input.replace("{descubiertas}", Long.toString(discovered))
                .replace("{total}", Integer.toString(recipes.size()))
                .replace("{pagina}", Integer.toString(page + 1))
                .replace("{paginas}", Integer.toString(totalPages));
        if (recipe == null) return text;
        return text.replace("{nombre}", recipe.name()).replace("{pista}", recipe.hint())
                .replace("{uso}", recipe.utility()).replace("{efecto}", recipe.effect())
                .replace("{nivel-ingredientes}", Integer.toString(recipe.ingredientTier()))
                .replace("{revolver}", recipe.stirFrom() + "–" + recipe.stirUntil())
                .replace("{servir}", recipe.readyFrom() + "–" + recipe.readyUntil());
    }

    private ItemStack bookCard(Material material, String texture, String name, List<String> lore) {
        ItemStack item;
        try { item = texture == null || texture.isBlank()
                ? new ItemStack(material) : CustomHeads.fromBase64(texture); }
        catch (IllegalArgumentException | IllegalStateException error) { item = new ItemStack(material); }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(TextService.component(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().limit(32).map(line ->
                TextService.component(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
    private String bookHead(String key) { return recipeBookLayout.getString("iconos." + key, ""); }
    private void openRecipeBook(Player player) { openRecipeBook(player, 0); }

    private void openRecipeBook(Player player, int page) {
        YamlConfiguration ui = recipeBookLayout;
        if (ui == null || !ui.getBoolean("enabled", true)) {
            player.sendMessage(Settings.message("cocina", "CookingModule.recetario-no-disponible",
                    "<red>El recetario está cerrado por ahora."));
            return;
        }
        List<Integer> slots = ui.getIntegerList("catalogo.posiciones");
        List<Recipe> ordered = new ArrayList<>(recipes.values());
        ordered.sort(java.util.Comparator.comparingInt(Recipe::ingredientTier)
                .thenComparing(Recipe::name)); // affordable dishes first, rare rewards last
        int pages = Math.max(1, (ordered.size() + slots.size() - 1) / slots.size());
        page = Math.max(0, Math.min(page, pages - 1));
        RecipeBookHolder holder = new RecipeBookHolder(null, page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                TextService.component(ui.getString("catalogo.titulo", "<gold>Recetario")));
        holder.inventory = inventory;
        for (int i = 0; i < slots.size(); i++) {
            int index = page * slots.size() + i;
            if (index >= ordered.size()) break;
            Recipe recipe = ordered.get(index);
            boolean known = knowsRecipe(player, recipe.id());
            boolean hidden = !known && ui.getBoolean("catalogo.ocultar-desconocidas", false);
            String section = hidden ? "catalogo.desconocido"
                    : known ? "catalogo.conocido" : "catalogo.pendiente";
            String title = bookText(ui.getString(section + ".nombre", "Receta"),recipe,player,page,pages);
            List<String> lore = new ArrayList<>();
            for (String line : ui.getStringList(section + ".lore"))
                lore.add(bookText(line,recipe,player,page,pages));
            String head = hidden ? bookHead("desconocido")
                    : ui.getString("iconos.recetas." + recipe.id(), "");
            inventory.setItem(slots.get(i), bookCard(hidden ? Material.GRAY_DYE : recipe.icon(), head, title, lore));
            holder.actions.put(slots.get(i), "receta:" + recipe.id());
        }
        List<String> summary = new ArrayList<>();
        for (String line : ui.getStringList("catalogo.resumen.lore"))
            summary.add(bookText(line,null,player,page,pages));
        inventory.setItem(ui.getInt("catalogo.resumen-slot"), bookCard(Material.BOOK,bookHead("resumen"),
                ui.getString("catalogo.resumen.nombre", "<gold>Archivo de recetas"),summary));
        if (page > 0) {
            int slot = ui.getInt("catalogo.anterior-slot");
            inventory.setItem(slot, bookCard(Material.ARROW,bookHead("anterior"),
                    ui.getString("navegacion.anterior-nombre", "<gold>Anterior"),
                    ui.getStringList("navegacion.lore")));
            holder.actions.put(slot,"anterior");
        }
        if (page + 1 < pages) {
            int slot = ui.getInt("catalogo.siguiente-slot");
            inventory.setItem(slot, bookCard(Material.ARROW,bookHead("siguiente"),
                    ui.getString("navegacion.siguiente-nombre", "<gold>Siguiente"),
                    ui.getStringList("navegacion.lore")));
            holder.actions.put(slot,"siguiente");
        }
        int close = ui.getInt("catalogo.cerrar-slot");
        inventory.setItem(close, bookCard(Material.BARRIER,bookHead("cerrar"),
                ui.getString("navegacion.cerrar-nombre", "<gray>Cerrar"),
                ui.getStringList("navegacion.lore")));
        holder.actions.put(close,"cerrar");
        player.openInventory(inventory);
    }

    private void openRecipeDetail(Player player, String id, int page) {
        YamlConfiguration ui = recipeBookLayout;
        Recipe recipe = recipes.get(id);
        if (ui == null || recipe == null) { openRecipeBook(player,page); return; }
        boolean hidden = !knowsRecipe(player,id) && ui.getBoolean("catalogo.ocultar-desconocidas", false);
        String title = ui.getString("detalle.titulo", "<gold>Receta <gray>│ <white>{nombre}");
        String heading = !hidden ? recipe.name() : ui.getString("catalogo.desconocido.nombre", "Receta desconocida");
        RecipeBookHolder holder = new RecipeBookHolder(id,page);
        Inventory inventory = Bukkit.createInventory(holder,54,
                TextService.component(title.replace("{nombre}",heading)));
        holder.inventory = inventory;
        String headerKey = hidden ? "detalle.desconocido" : "detalle.cabecera";
        List<String> headerLore = new ArrayList<>();
        for (String line : ui.getStringList(headerKey + ".lore"))
            headerLore.add(bookText(line,hidden ? null : recipe,player,page,1)
                    .replace("{pista}",recipe.hint()));
        int headerSlot = ui.getInt("detalle.cabecera-slot");
        inventory.setItem(headerSlot,bookCard(hidden ? Material.GRAY_DYE : recipe.icon(),
                hidden ? bookHead("desconocido") : ui.getString("iconos.recetas." + id,""),
                bookText(ui.getString(headerKey + ".nombre","<gold>Receta"),hidden ? null : recipe,player,page,1),
                headerLore));
        if (!hidden) {
            List<Integer> ingredientSlots = ui.getIntegerList("detalle.ingrediente-slots");
            for (int i = 0; i < 3; i++) {
                Material ingredient = recipe.ingredients().get(i);
                String name = ui.getString("materiales." + ingredient.name(),
                        ingredient.name().toLowerCase(java.util.Locale.ROOT).replace('_',' '));
                String label = ui.getString("detalle.ingrediente.nombre","<white>{nombre-ingrediente}")
                        .replace("{nombre-ingrediente}",name);
                List<String> ingredientLore = new ArrayList<>();
                for (String line : ui.getStringList("detalle.ingrediente.lore"))
                    ingredientLore.add(line.replace("{indice}",Integer.toString(i+1)));
                inventory.setItem(ingredientSlots.get(i),bookCard(ingredient,"",label,ingredientLore));
            }
            List<String> prepare = new ArrayList<>();
            for (String line : ui.getStringList("detalle.preparacion.lore"))
                prepare.add(bookText(line,recipe,player,page,1));
            inventory.setItem(ui.getInt("detalle.preparacion-slot"),bookCard(Material.CAMPFIRE,
                    bookHead("preparacion"),ui.getString("detalle.preparacion.nombre","<gold>Preparación"),prepare));
            List<String> benefit = new ArrayList<>();
            for (String line : ui.getStringList("detalle.resultado.lore"))
                benefit.add(bookText(line,recipe,player,page,1));
            inventory.setItem(ui.getInt("detalle.resultado-slot"),bookCard(Material.POTION,
                    bookHead("resultado"),ui.getString("detalle.resultado.nombre","<gold>Beneficio"),benefit));
        }
        int back = ui.getInt("detalle.volver-slot");
        inventory.setItem(back,bookCard(Material.ARROW,bookHead("volver"),
                ui.getString("navegacion.volver-nombre","<gold>Volver"),
                ui.getStringList("navegacion.lore")));
        holder.actions.put(back,"volver");
        int close = ui.getInt("detalle.cerrar-slot");
        inventory.setItem(close,bookCard(Material.BARRIER,bookHead("cerrar"),
                ui.getString("navegacion.cerrar-nombre","<gray>Cerrar"),
                ui.getStringList("navegacion.lore")));
        holder.actions.put(close,"cerrar");
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBookClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeBookHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String action = holder.actions.get(slot);
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            switch (action) {
                case "cerrar" -> player.closeInventory();
                case "volver" -> openRecipeBook(player,holder.page);
                case "anterior" -> openRecipeBook(player,holder.page-1);
                case "siguiente" -> openRecipeBook(player,holder.page+1);
                default -> {
                    if (action.startsWith("receta:"))
                        openRecipeDetail(player,action.substring("receta:".length()),holder.page);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBookDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RecipeBookHolder)
            for (int slot : event.getRawSlots())
                if (slot < 54) { event.setCancelled(true); break; }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("cocina")) return false;
        String action = args.length == 0 ? "ayuda" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("transacciones")) {
            if (!cookingAdmin(sender)) { sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-032", "§cNo tienes permiso.")); return true; }
            if (!claimJournal.ready()) { sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-033", "§cRegistro de transacciones ilegible; restaura claims.yml.")); return true; }
            sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-034", "§6Transacciones pendientes: {count}", "count", claimJournal.count()));
            for (ClaimJournal.Claim c : claimJournal.unresolved())
                sender.sendMessage(c.id() + " " + c.operation() + " " + c.subject() + " jugador=" + c.player() + " estación=" + c.station());
            return true;
        }
        if (action.equals("resolver") && args.length == 3) {
            if (!cookingAdmin(sender) || !(sender instanceof Player admin) || !admin.isOp()) {
                sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-035", "§cLa reconciliación exige un operador dentro del juego.")); return true;
            }
            if (!args[2].equalsIgnoreCase("confirmado")) {
                sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-036", "§cUsa /spectrecore cocina resolver <UUID> confirmado tras auditar.")); return true;
            }
            try {
                UUID id = UUID.fromString(args[1]);
                boolean ok = claimJournal.resolve(id);
                sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-037", "§eResultado de reconciliación: {estado}", "estado", ok ? "cerrada sin mover objetos" : "fallida"));
                if (ok) getLogger().warning("AUDITORIA_COCINA: operador " + admin.getUniqueId() + " cerró transacción " + id);
            } catch (IllegalArgumentException ex) { sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-038", "§cUUID inválido.")); }
            return true;
        }
        if (action.equals("ayuda")) {
            sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-014", "{prefix}§eMechero: crear §7| §ePala de madera: revolver §7| §eEsponja: limpiar/retirar §7| §eCuenco: servir", "prefix", Settings.component(prefix())));
            sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-015", "{prefix}§e/mago recetas §7| §e/spectrecore cocina estado §7| admin: reparar, recargar", "prefix", Settings.component(prefix())));
            return true;
        }
        if (action.equals("recetas") || action.equals("libro")) {
            if (sender instanceof Player player) openRecipeBook(player);
            else for (Recipe r : recipes.values()) sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-016", "{prefix}§e{r-name} §7» {r-ingredients}", "prefix", Settings.component(prefix()), "r-name", r.name(), "r-ingredients", r.ingredients()));
            return true;
        }
        if (action.equals("recargar")) {
            if (!cookingAdmin(sender)) { sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-017", "{prefix}§cNo tienes permiso.", "prefix", Settings.component(prefix()))); return true; }
            try { plugin.reloadConfig(); loadRecipes(); loadRecipeBookLayout(); sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-018", "{prefix}§aConfiguracion y recetas recargadas.", "prefix", Settings.component(prefix()))); }
            catch (Exception error) { sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-019", "{prefix}§c{error-getmessage}", "prefix", Settings.component(prefix()), "error-getmessage", error.getMessage())); }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-020", "{prefix}Este comando requiere un jugador.", "prefix", Settings.component(prefix()))); return true;
        }
        if (action.equals("reparar") && !cookingAdmin(sender)) {
            sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-021", "{prefix}§cNo tienes permiso.", "prefix", Settings.component(prefix()))); return true;
        }
        Block looked = player.getTargetBlockExact(6);
        if (looked == null || !isCookingStation(looked)) {
            msg(player, ChatColor.RED + Settings.text("cocina", "CookingModule.etiqueta-120", "Mira directamente a una cocina registrada.")); return true;
        }
        Station station = stations.get(key(looked.getLocation()));
        switch (action) {
            case "reparar" -> {
                removeVisual(station); repairVisual(station);
                msg(player, ChatColor.GREEN + Settings.text("cocina", "CookingModule.etiqueta-121", "Modelo de cocina reconstruido."));
            }
            case "estado" -> { accrue(station, System.currentTimeMillis()); inspectStation(player, station, true); }
            default -> sender.sendMessage(Settings.message("cocina", "CookingModule.mensaje-022", "{prefix}§e/spectrecore cocina ayuda | libro | estado | reparar | recargar", "prefix", Settings.component(prefix())));
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        List<String> actions = cookingAdmin(sender)
                ? List.of("ayuda", "recetas", "libro", "estado", "reparar", "recargar", "transacciones", "resolver")
                : List.of("ayuda", "recetas", "libro", "estado");
        return actions.stream().filter(x -> x.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    }
    private boolean cookingAdmin(CommandSender sender) {
        return sender.hasPermission("hydev.cocina.admin") || sender.hasPermission("spectrecore.cooking.admin")
                || sender.hasPermission("spectrecore.admin");
    }
    private void msg(Player player, String message) { player.sendMessage(Settings.component(prefix() + message)); }
    private void sound(Station station, Sound sound, float pitch) {
        station.blockLocation.getWorld().playSound(station.blockLocation.clone().add(Settings.d("cocina", "CookingModule.geometria-168", .5), Settings.d("cocina", "CookingModule.geometria-169", .5), Settings.d("cocina", "CookingModule.geometria-170", .5)), sound,Settings.f("cocina", "CookingModule.playsound-083-arg2", 1f), pitch);
    }
}
