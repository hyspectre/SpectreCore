package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.systems.JobsContractRules;
import dev.hydev.spectrecore.systems.JobsModule;
import dev.hydev.spectrecore.mining.MiningProgression;
import dev.hydev.spectrecore.systems.ProgressRules;
import net.kyori.adventure.text.Component;
import dev.hydev.spectrecore.text.TextService;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Four native Paper inventories: a Jobs index, and Miner, Farmer, Hunter contracts.
 * The YAML may change visual content only. Click actions and payment are Java only.
 */
public final class JobsMenu implements Listener {
    private static final Pattern HEX = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Set<String> PAGES = Set.of("trabajos", "minero", "granjero", "cazador");
    private static final List<String> RANKS = JobsContractRules.LEVELS;

    private final JavaPlugin plugin;
    private final JobsModule jobs;
    private final Map<UUID, Long> clickTimes = new HashMap<>();
    private YamlConfiguration layout;
    private boolean listening;

    private static final class Holder implements InventoryHolder {
        final String page;
        final Map<Integer, String> slots = new HashMap<>();
        Inventory inventory;
        Holder(String page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public JobsMenu(JavaPlugin plugin, JobsModule jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "ui/trabajos.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/trabajos.yml", false);
            if (!file.isFile() || file.length() > 64 * 1024)
                throw new IOException("ui/trabajos.yml ausente o demasiado grande");
            dev.hydev.spectrecore.config.PresentationMigrations.migrate(
                    plugin.getDataFolder(), file, "ui/trabajos.yml");
            var candidate = new YamlConfiguration();
            candidate.load(file);
            mergeMissingLocalization(file, candidate);
            provideSupplyShortcut(candidate); // In-memory fallback for existing customized job menus.
            provideWorkshopShortcut(candidate);
            for (String page : PAGES) {
                String path = "pages." + page;
                var items = candidate.getConfigurationSection(path + ".items");
                if (items == null || !candidate.isString(path + ".title")
                        || candidate.getInt(path + ".size") != 54 || items.getKeys(false).size() > 40)
                    throw new IOException(path + " incompleto");
                Set<Integer> slots = new HashSet<>();
                for (String item : items.getKeys(false)) {
                    String base = path + ".items." + item;
                    if (!candidate.isInt(base + ".slot") || !candidate.isString(base + ".name")
                            || !candidate.isString(base + ".texture") || !candidate.isList(base + ".lore"))
                        throw new IOException(base + " incompleto");
                    int slot = candidate.getInt(base + ".slot");
                    if (slot < 0 || slot >= 54 || !slots.add(slot)
                            || candidate.getString(base + ".texture", "").length() > 1024
                            || candidate.getString(base + ".name", "").length() > 256
                            || candidate.getStringList(base + ".lore").size() > 28)
                        throw new IOException(base + " fuera de límites");
                }
                for (String required : page.equals("trabajos")
                        ? List.of("minero", "granjero", "cazador", "cerrar")
                        : List.of("novato", "experimentado", "profesional", "cerrar"))
                    if (!items.contains(required)) throw new IOException(path + " no contiene " + required);
            }
            layout = candidate;
        } catch (Exception error) {
            plugin.getLogger().warning("Trabajos GUI: YAML rechazado; se mantiene el último válido: "
                    + error.getMessage());
        }
    }

    private void mergeMissingLocalization(File file, YamlConfiguration candidate) throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("ui/trabajos.yml")) {
            if (stream == null) throw new IOException("No se encontró la traducción predeterminada de trabajos");
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        ConfigurationSection labels = defaults.getConfigurationSection("labels");
        if (labels == null) throw new IOException("Falta la sección labels de trabajos");
        boolean changed = false;
        for (String key : labels.getKeys(true)) {
            if (labels.isConfigurationSection(key)) continue;
            String path = "labels." + key;
            if (!candidate.contains(path)) {
                candidate.set(path, labels.get(key));
                changed = true;
            }
        }
        if (!changed) return;
        File backup = new File(plugin.getDataFolder(), "backups/trabajos-labels-v1/ui/trabajos.yml");
        if (!backup.exists()) {
            Files.createDirectories(backup.toPath().getParent());
            Files.copy(file.toPath(), backup.toPath());
        }
        AtomicFileStore.writeUtf8(file.toPath(), candidate.saveToString());
    }

    /**
     * Legacy layouts did not have the supply board. Offer it without rewriting
     * administrator menus, and never replace an occupied custom slot.
     */
    private void provideSupplyShortcut(YamlConfiguration candidate) throws IOException {
        String root = "pages.trabajos.items";
        if (candidate.contains(root + ".abastecimiento")) return;
        ConfigurationSection current = candidate.getConfigurationSection(root);
        if (current == null) return;
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("ui/trabajos.yml")) {
            if (stream == null) throw new IOException("No hay diseño de Trabajos incluido");
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (org.bukkit.configuration.InvalidConfigurationException malformed) {
            throw new IOException("El diseño incluido de Trabajos es inválido", malformed);
        }
        String newItem = root + ".abastecimiento";
        if (!defaults.isConfigurationSection(newItem)) throw new IOException("Falta el acceso a abastecimiento");
        Set<Integer> occupied = new HashSet<>();
        for (String id : current.getKeys(false)) {
            int index = candidate.getInt(root + "." + id + ".slot", -1);
            if (index >= 0 && index < 54) occupied.add(index);
        }
        int slot = defaults.getInt(newItem + ".slot", 31);
        if (occupied.contains(slot)) {
            slot = -1;
            for (int candidateSlot = 27; candidateSlot <= 44; candidateSlot++)
                if (!occupied.contains(candidateSlot)) { slot = candidateSlot; break; }
        }
        if (slot < 0) {
            plugin.getLogger().info("Trabajos: índice personalizado sin espacio libre; el abastecimiento sigue accesible por comando.");
            return;
        }
        candidate.set(newItem + ".slot", slot);
        for (String field : List.of("texture", "name", "lore"))
            candidate.set(newItem + "." + field, defaults.get(newItem + "." + field));
    }

    /** Existing customized Jobs menus get the workshop without destructive rewrites. */
    private void provideWorkshopShortcut(YamlConfiguration candidate) throws IOException {
        String root="pages.trabajos.items", key=root+".taller";
        if(candidate.contains(key))return;
        ConfigurationSection current=candidate.getConfigurationSection(root);
        if(current==null)return;
        YamlConfiguration defaults=new YamlConfiguration();
        try(InputStream stream=plugin.getResource("ui/trabajos.yml")) {
            if(stream==null)throw new IOException("Falta el menú de Minería");
            defaults.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(org.bukkit.configuration.InvalidConfigurationException malformed) {
            throw new IOException("La plantilla de Minería es inválida",malformed);
        }
        Set<Integer> occupied=new HashSet<>();
        for(String item:current.getKeys(false))occupied.add(candidate.getInt(root+"."+item+".slot",-1));
        int slot=defaults.getInt(key+".slot",33);
        if(occupied.contains(slot)){
            slot=-1;
            for(int alternative=27;alternative<=44;alternative++)if(!occupied.contains(alternative)){
                slot=alternative;break;
            }
        }
        if(slot<0){
            plugin.getLogger().info("Trabajos: menú sin lugar para el taller; usa /spectrecore minero.");
            return;
        }
        candidate.set(key+".slot",slot);
        for(String field:List.of("texture","name","lore"))
            candidate.set(key+"."+field,defaults.get(key+"."+field));
    }

    public boolean open(Player player, String page) {
        if (!PAGES.contains(page)) page = "trabajos";
        if (layout == null) {
            player.sendMessage(Settings.text("trabajos", "JobsMenu.desactivado", "§cEl catálogo de trabajos no está disponible por ahora."));
            return false;
        }
        String base = "pages." + page;
        ConfigurationSection items = layout.getConfigurationSection(base + ".items");
        if (items == null) return false;
        Holder holder = new Holder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, styled(layout.getString(base + ".title")));
        holder.inventory = inventory;
        for (String id : items.getKeys(false)) {
            String path = base + ".items." + id;
            String texture = layout.getString(path + ".texture", "");
            ItemStack item;
            try { item = texture.isBlank() ? new ItemStack(Material.BOOK) : CustomHeads.fromBase64(texture); }
            catch (IllegalArgumentException | IllegalStateException error) {
                item = new ItemStack(Material.BOOK);
                plugin.getLogger().warning("Trabajos: textura inválida en " + path);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            meta.displayName(styled(layout.getString(path + ".name", "Trabajos")));
            List<Component> lore = new ArrayList<>();
            for (String line : layout.getStringList(path + ".lore")) {
                if (line.equals("{actividades}") && RANKS.contains(id) && !page.equals("trabajos"))
                    for (String activity : activities(page, RANKS.indexOf(id) + 1)) lore.add(styled(activity));
                else lore.add(styled(resolve(player, page, id, line)));
            }
            // Snapshot on opening; no background actionbar or per-player refresh task.
            if (!page.equals("trabajos") && RANKS.contains(id)) {
                int tier = RANKS.indexOf(id) + 1;
                if (jobs.rank(player, page) >= tier) {
                    long wait = jobs.paymentWaitMillis(player, page, tier);
                    String payment = wait < 0 ? label("pago-no-disponible", "<red>Pago no disponible")
                            : wait == 0 ? label("pago-listo", "<green>Espera de pago cumplida")
                            : label("pago-espera", "<yellow>Espera al consultar: {s}s")
                                .replace("{s}", Long.toString((wait + 999) / 1000));
                    lore.add(styled(payment));
                }
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            int slot = layout.getInt(path + ".slot");
            inventory.setItem(slot, item);
            holder.slots.put(slot, id);
        }
        player.openInventory(inventory);
        return true;
    }

    private List<String> activities(String job, int tier) {
        String domain = job.equals("minero") ? "trabajos-mineria" : job.equals("granjero") ? "agricultura" : "combate";
        List<String> entries = ModuleConfiguration.module(plugin, "tesoros/config.yml")
                .getStringList("clasificacion." + domain + ".tier" + tier);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String material : entries) {
            String translation = layout.getString("material-names." + material,
                    material.replace('_', ' ').toLowerCase(Locale.ROOT));
            names.add(translation);
        }
        if (names.isEmpty()) return List.of(layout.getString("labels.sin-actividades",
                "  <gray>• <dark_gray>No hay actividades disponibles"));
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder("  &7• &b");
        for (String name : names) {
            if (current.length() > 12 && current.length() + name.length() + 2 > 46) {
                lines.add(current.toString()); current = new StringBuilder("    &b");
            }
            if (!current.toString().endsWith("&b")) current.append("&7, &b");
            current.append(name);
        }
        lines.add(current.toString());
        return lines;
    }

    private String resolve(Player player, String page, String id, String line) {
        if (line == null || !line.contains("{")) return line;
        String job = page.equals("trabajos") ? id : page;
        if (!JobsContractRules.JOBS.contains(job)) return line;
        int current = jobs.rank(player, job);
        String currentRank = current == 0 ? label("sin-contrato", "Sin contrato")
                : jobs.displayRank(ProgressRules.rank(current));
        String state = label("disponible", "Disponible");
        if (page.equals("trabajos") || !RANKS.contains(id)) {
            return line.replace("{rango}", currentRank).replace("{estado}", state);
        }
        int tier = RANKS.indexOf(id) + 1;
        String level = RANKS.get(tier - 1);
        var config = ModuleConfiguration.module(plugin, "trabajos/config.yml");
        String root = job + "." + level + ".dinero";
        String min = readableNumber(config.get(root + ".minimo"));
        String max = readableNumber(config.get(root + ".maximo"));
        String cooldown = readableNumber(config.get("cooldowns." + job + "." + level));
        String price = readableNumber(config.get("contratos.precios." + level));
        if (current >= tier) state = label("ya-contratado", "Ya contratado");
        else if (jobs.isPending(player)) state = label("procesando", "Procesando");
        else state = current == tier - 1
                ? label("disponible", "Disponible")
                : label("rango-anterior", "Requiere rango anterior");
        if (job.equals("minero") && current < tier && current == tier-1) {
            var progression=new MiningProgression(plugin);
            String milestone=switch(level) {
                case "novato" -> "inicial";
                case "experimentado" -> "paladio";
                default -> "resonancia";
            };
            if(!progression.meets(player,milestone))
                state="<yellow>Minería nivel "+progression.required(milestone)+" requerida";
        }
        return line.replace("{rango}", currentRank).replace("{minimo}", min)
                .replace("{maximo}", max).replace("{espera}", cooldown)
                .replace("{precio}", price).replace("{estado}", state);
    }

    private String label(String key, String fallback) {
        return layout.getString("labels.estados." + key, fallback);
    }
    private String readableNumber(Object data) {
        if (!(data instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0) return layout.getString("labels.sin-valor", "Sin configurar");
        double value = number.doubleValue();
        return value == Math.rint(value) ? Long.toString(number.longValue()) : Double.toString(value);
    }

    /** MiniMessage for new YAML; legacy color codes remain readable for existing server configs. */
    private static Component styled(String source) {
        return TextService.component(source).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String clicked = holder.slots.get(slot);
        if (clicked == null) return;
        long now = System.currentTimeMillis(), previous = clickTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 400) return;
        clickTimes.put(player.getUniqueId(), now);
        if (holder.page.equals("trabajos")) {
            if (JobsContractRules.JOBS.contains(clicked)) defer(player, holder, () -> open(player, clicked));
            else if (clicked.equals("abastecimiento")) defer(player, holder, () -> jobs.openSupplies(player));
            else if (clicked.equals("taller")) defer(player, holder, () -> jobs.openMinerWorkshop(player));
            else if (clicked.equals("cerrar")) defer(player, holder, player::closeInventory);
            return;
        }
        if (RANKS.contains(clicked)) {
            final String job = holder.page, rank = clicked;
            defer(player, holder, () -> {
                player.closeInventory();
                jobs.purchase(player, job, rank);
            });
        } else if (clicked.equals("cerrar")) defer(player, holder, () -> open(player, "trabajos"));
        else if (clicked.equals("cerrar_menu")) defer(player, holder, player::closeInventory);
    }

    private void defer(Player player, Holder holder, Runnable next) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() == holder) next.run();
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            for (int slot : event.getRawSlots()) if (slot < 54) {
                event.setCancelled(true);
                break;
            }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        clickTimes.remove(event.getPlayer().getUniqueId());
    }

    public void disable() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                player.closeInventory();
        }
        clickTimes.clear();
    }
}
