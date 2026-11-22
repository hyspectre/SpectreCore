package dev.hydev.spectrecore.relicario;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.text.TextService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Relicario: confirm-on-right-click, durable UUID discoveries, non-equippable
 * MMOItems RELIQUIA IDs and read-only menus. No unsafe OP command dispatch.
 */
public final class RelicarioModule implements Listener {
    private record Entry(String id, String name, String category, String rarity,
                         String origin, Material material, RelicarioRules.Relic relic) { }
    private static final List<String> GROUPS = List.of("cripta", "superficie",
            "exploracion", "excepcionales");

    private static final class Holder implements InventoryHolder {
        final String page;
        final String id;
        final int heldSlot;
        final ItemStack snapshot;
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory;
        Holder(String page, String id, int heldSlot, ItemStack snapshot) {
            this.page = page;
            this.id = id;
            this.heldSlot = heldSlot;
            this.snapshot = snapshot;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final SpectreCorePlugin plugin;
    private RelicarioStore store;
    private RelicarioStats stats;
    private Map<String, Entry> entries = Map.of();
    private Map<String, RelicarioRules.Relic> rules = Map.of();
    private Map<String, Double> caps = Map.of();
    private YamlConfiguration ui;
    private boolean active;
    private Method getType, getId;
    private boolean warnedMmo;
    private final Map<UUID, Long> clickGate = new HashMap<>();

    public RelicarioModule(SpectreCorePlugin plugin) { this.plugin = plugin; }
    public RelicarioStats stats() { return stats; }
    public boolean active() { return active; }

    /** On failure nothing registers, so the player can never lose a relic. */
    public boolean enable() {
        if (!reload()) return false;
        int limit = plugin.moduleConfigs().read("accesorios/config.yml")
                .getInt("storage.max-ids-per-player", 256);
        store = new RelicarioStore(plugin.getDataFolder(), limit);
        if (!store.initialize()) {
            plugin.getLogger().severe("Relicario: no se pudo crear la carpeta de datos; módulo desactivado.");
            return false;
        }
        stats = new RelicarioStats(plugin, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(stats, plugin);
        active = true;
        for (Player p : Bukkit.getOnlinePlayers()) join(p);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active) return;
            for (Player p : Bukkit.getOnlinePlayers()) if (ready(p.getUniqueId())) stats.refresh(p);
        }, 100L, 200L);
        return true;
    }

    public boolean reload() {
        try {
            YamlConfiguration config = plugin.moduleConfigs().read("accesorios/config.yml");
            YamlConfiguration catalog = plugin.moduleConfigs().read("accesorios/catalogo.yml");
            if (!config.getBoolean("enabled", false) || !catalog.getBoolean("enabled", false))
                return false;
            File file = new File(plugin.getDataFolder(), "ui/relicario.yml");
            if (!file.exists()) plugin.saveResource("ui/relicario.yml", false);
            if (!file.isFile() || file.length() > 96_000) throw new IllegalArgumentException("UI invalid");
            YamlConfiguration nextUi = new YamlConfiguration();
            nextUi.load(file);
            if (!nextUi.getBoolean("enabled", false) || nextUi.getInt("general.size") != 54)
                throw new IllegalArgumentException("UI disabled or invalid");
            ConfigurationSection section = catalog.getConfigurationSection("accesorios");
            ConfigurationSection capSection = catalog.getConfigurationSection("caps");
            if (section == null || capSection == null || section.getKeys(false).size() != 32)
                throw new IllegalArgumentException("Accessory catalog missing or incomplete");
            Map<String, Double> limits = new LinkedHashMap<>();
            for (String key : capSection.getKeys(false)) {
                double value = capSection.getDouble(key, -1);
                if (value <= 0 || value > 100_000 || !Double.isFinite(value))
                    throw new IllegalArgumentException("Invalid cap: " + key);
                limits.put(key, value);
            }
            Map<String, Entry> next = new LinkedHashMap<>();
            for (String id : section.getKeys(false)) {
                ConfigurationSection line = section.getConfigurationSection(id);
                if (line == null) throw new IllegalArgumentException("Missing accessory " + id);
                String group = line.getString("categoria", "");
                if (!GROUPS.contains(group)) throw new IllegalArgumentException("Invalid category");
                String family = line.getString("familia", "");
                int rank = line.getInt("rango", 1);
                String name = line.getString("nombre", id);
                String rarity = line.getString("rareza", "RARE");
                String origin = line.getString("origen", "");
                Material material = Material.matchMaterial(line.getString("material", ""));
                if (material == null || !material.isItem() || name.length() > 80 ||
                        origin.length() > 120 || !Set.of("RARE","EPIC","LEGENDARY").contains(rarity))
                    throw new IllegalArgumentException("Invalid accessory display: " + id);
                ConfigurationSection stats = line.getConfigurationSection("bonos");
                if (stats == null) throw new IllegalArgumentException("Missing bonuses");
                Map<String, Double> bonuses = new LinkedHashMap<>();
                for (String stat : stats.getKeys(false)) {
                    if (!limits.containsKey(stat)) throw new IllegalArgumentException("Unregistered bonus: " + stat);
                    bonuses.put(stat, stats.getDouble(stat));
                }
                var relic = new RelicarioRules.Relic(id, family, rank, bonuses);
                next.put(id, new Entry(id, name, group, rarity, origin, material, relic));
            }
            var validated = RelicarioRules.validateCatalog(next.values().stream().map(Entry::relic).toList());
            for (String group : GROUPS) {
                if (!nextUi.isConfigurationSection("colecciones." + group) ||
                    nextUi.getIntegerList("colecciones." + group + ".recipe-slots").size()
                        < next.values().stream().filter(e -> e.category.equals(group)).count())
                    throw new IllegalArgumentException("Incomplete UI category: " + group);
            }
            ui = nextUi;
            entries = Map.copyOf(next);
            rules = validated;
            caps = Map.copyOf(limits);
            if (active) {
                for (Player p : Bukkit.getOnlinePlayers()) if (ready(p.getUniqueId())) stats.refresh(p);
            }
            return true;
        } catch (Exception failure) {
            plugin.getLogger().warning("Relicario: configuración rechazada, se conserva la anterior: "
                    + failure.getMessage());
            return false;
        }
    }

    public void disable() {
        active = false;
        HandlerList.unregisterAll(this);
        if (stats != null) {
            HandlerList.unregisterAll(stats);
            stats.cleanupAll();
        }
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                player.closeInventory();
        if (store != null) store.clear();
        clickGate.clear();
    }

    public boolean ready(UUID uuid) { return active && store != null && store.healthy(uuid); }
    public Set<String> discovered(UUID uuid) { return ready(uuid) ? store.discovered(uuid) : Set.of(); }
    public Map<String, Double> calculate(UUID uuid) {
        if (!ready(uuid)) return Map.of();
        return RelicarioRules.totals(rules.values(), store.discovered(uuid), caps);
    }
    public long addJobCoins(Player player, long amount) {
        return stats == null ? amount : stats.salary(player, amount);
    }
    public double cookingSpeed(Player player) {
        return stats == null ? 0 : stats.cookingSpeed(player);
    }

    private void join(Player player) {
        if (!store.load(player.getUniqueId())) {
            plugin.getLogger().severe("Relicario: datos ilegibles para " + player.getUniqueId()
                    + "; no se permitirá absorber reliquias.");
            player.sendMessage(text(msg("storage-error", "<red>No se pudo abrir tu Relicario. Avisa a administración.")));
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && ready(player.getUniqueId())) stats.refresh(player);
        }, 10L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { if (active) join(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (!active) return;
        stats.cleanup(event.getPlayer());
        store.unload(event.getPlayer().getUniqueId());
        clickGate.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active && event.getPlayer().isOnline()) stats.refresh(event.getPlayer());
        }, 2L);
    }

    private String mmo(ItemStack item, boolean type) {
        if (item == null || item.getType() == Material.AIR ||
                !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return null;
        try {
            if (getType == null || getId == null) {
                Class<?> api = Class.forName("net.Indyuce.mmoitems.MMOItems");
                getType = api.getMethod("getTypeName", ItemStack.class);
                getId = api.getMethod("getID", ItemStack.class);
            }
            Object result = (type ? getType : getId).invoke(null, item);
            return result instanceof String s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warnedMmo) {
                warnedMmo = true;
                plugin.getLogger().warning("Relicario: no se pudo verificar un objeto real de MMOItems; consumo bloqueado.");
            }
            return null; // fail closed: NEVER trust display names
        }
    }

    public boolean isCookingItem(ItemStack item) { return "COCINA".equals(mmo(item, true)); }

    private String relicId(ItemStack item) {
        if (!"RELIQUIA".equals(mmo(item, true))) return null;
        String id = mmo(item, false);
        return entries.containsKey(id) ? id : null;
    }

    // Paper may pre-cancel an air-click when vanilla predicts no use.
    // Real MMOItems ID validation (below) still protects all mutations.
    @EventHandler(priority=EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (!active || event.getHand() != EquipmentSlot.HAND ||
                (event.getAction() != Action.RIGHT_CLICK_AIR &&
                 event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String id = relicId(item);
        if (id == null) return;
        event.setCancelled(true);
        if (!ready(player.getUniqueId())) {
            player.sendMessage(text(msg("storage-error", "<red>Tu Relicario no está disponible.")));
            return;
        }
        if (store.discovered(player.getUniqueId()).contains(id)) {
            player.sendMessage(text(msg("duplicate", "<yellow>Ya absorbiste esta reliquia.")));
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder) return;
        int hotbar = player.getInventory().getHeldItemSlot();
        ItemStack copy = item.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !ready(player.getUniqueId())) return;
            ItemStack held = player.getInventory().getItemInMainHand();
            if (player.getInventory().getHeldItemSlot() != hotbar || held == null ||
                    !held.isSimilar(copy) || !id.equals(relicId(held))) return;
            confirmScreen(player, id, hotbar, copy);
        });
    }

    private void confirmScreen(Player player, String id, int slot, ItemStack copy) {
        Holder holder = new Holder("confirmacion", id, slot, copy);
        Inventory inv = Bukkit.createInventory(holder, 27,
                text(ui.getString("confirmacion.title", "<gold>Absorber reliquia")));
        holder.inventory = inv;
        Entry entry = entries.get(id);
        put(inv, ui.getInt("confirmacion.item-slot",13), entry.material,
                "<gold>" + entry.name, List.of("<gray>" + entry.origin));
        int yes = ui.getInt("confirmacion.confirmar-slot",11),
            no = ui.getInt("confirmacion.cancelar-slot",15);
        put(inv, yes, Material.LIME_CONCRETE, msg("confirm", "<green>✦ Confirmar absorción"),
                List.of(msg("permanent", "<gray>Su poder quedará contigo para siempre.")));
        put(inv, no, Material.RED_CONCRETE, msg("cancel", "<red>⇜ Cancelar"),
                List.of(msg("cancel-detail", "<gray>Conservar la reliquia.")));
        holder.actions.put(yes, "confirm");
        holder.actions.put(no, "close");
        player.openInventory(inv);
    }

    private String msg(String key, String fallback) { return ui == null ? fallback :
            ui.getString("mensajes." + key, fallback); }
    private static Component text(String s) { return TextService.component(s).decoration(TextDecoration.ITALIC, false); }
    private void put(Inventory inv, int slot, Material material, String title, List<String> lore) {
        if (slot < 0 || slot >= inv.getSize()) return;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(text(title));
        meta.lore(lore.stream().limit(28).map(RelicarioModule::text).toList());
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    public void open(Player player) { openPage(player, "inicio"); }
    public void openPage(Player player, String requested) {
        if (!active || !ready(player.getUniqueId())) {
            player.sendMessage(text(msg("storage-error", "<red>Tu Relicario no está disponible.")));
            return;
        }
        String page = (GROUPS.contains(requested) || requested.equals("estadisticas")) ?
                requested : "inicio";
        Holder holder = new Holder(page, "", -1, null);
        String title = page.equals("inicio")
                ? ui.getString("general.title", "<gold>Relicario")
                : page.equals("estadisticas") ? ui.getString("estadisticas.title", "<gold>Estadísticas")
                : ui.getString("colecciones." + page + ".title", "<gold>Relicario");
        Inventory inv = Bukkit.createInventory(holder, 54, text(title));
        holder.inventory = inv;
        Set<String> obtained = discovered(player.getUniqueId());
        if (page.equals("inicio")) {
            ConfigurationSection icons = ui.getConfigurationSection("general.iconos");
            if (icons != null) for (String id : icons.getKeys(false)) {
                int slot = icons.getInt(id + ".slot", -1);
                Material material = Material.matchMaterial(icons.getString(id + ".material", "BOOK"));
                if (material == null) material = Material.BOOK;
                String label = icons.getString(id + ".name", id);
                List<String> lore = new ArrayList<>(icons.getStringList(id + ".lore"));
                if (GROUPS.contains(id)) {
                    long have = entries.values().stream().filter(e -> e.category.equals(id)
                            && obtained.contains(e.id)).count();
                    long all = entries.values().stream().filter(e -> e.category.equals(id)).count();
                    lore.add(ui.getString("general.numeros",
                            "<gray>Descubiertos <yellow>{obtenidos}<gray>/{total}")
                            .replace("{obtenidos}", Long.toString(have)).replace("{total}", Long.toString(all)));
                }
                put(inv, slot, material, label, lore);
                holder.actions.put(slot, id.equals("cerrar") ? "close" : id);
            }
        } else if (page.equals("estadisticas")) {
            int slot = 10;
            for (var total : calculate(player.getUniqueId()).entrySet().stream().sorted(
                    Map.Entry.comparingByKey()).toList()) {
                String label = ui.getString("estadisticas.nombres." + total.getKey(), total.getKey());
                String value = format(total.getValue()) +
                        (Set.of("health", "mana").contains(total.getKey()) ? "" : "%");
                List<String> lore = new ArrayList<>();
                if (total.getKey().equals("mana") && !stats.manaAvailable(player))
                    lore.add(msg("mana-offline", "<yellow>El maná requiere AuraSkills activo."));
                else lore.add(msg("active-bonus", "<green>Bonificación activa"));
                put(inv, slot++, Material.PAPER, "<white>" + label + " <green>+" + value, lore);
            }
            back(inv, holder);
        } else {
            List<Integer> slots = ui.getIntegerList("colecciones." + page + ".recipe-slots");
            int i = 0;
            for (Entry entry : entries.values()) {
                if (!entry.category.equals(page) || i >= slots.size()) continue;
                boolean owned = obtained.contains(entry.id);
                boolean upgraded = owned && RelicarioRules.superseded(entry.relic, rules.values(), obtained);
                String state = owned ? upgraded ? ui.getString("estados.superado", "<yellow>✦ Evolucionado")
                                  : ui.getString("estados.descubierto", "<green>✦ Absorbido")
                             : ui.getString("estados.pendiente", "<gray>✦ Pendiente");
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>" + entry.origin);
                lore.add("");
                for (var bonus : entry.relic.bonuses().entrySet()) {
                    String label = ui.getString("estadisticas.nombres." + bonus.getKey(), bonus.getKey());
                    lore.add("<gray>" + label + " <green>+" + format(bonus.getValue()) +
                            (Set.of("health", "mana").contains(bonus.getKey()) ? "" : "%"));
                }
                lore.add("");
                lore.add(state);
                put(inv, slots.get(i++), entry.material, "<gold>" + entry.name, lore);
            }
            back(inv, holder);
        }
        player.openInventory(inv);
    }

    private void back(Inventory inv, Holder holder) {
        int back = ui.getInt("colecciones.cripta.volver-slot",48);
        int close = ui.getInt("colecciones.cripta.cerrar-slot",50);
        put(inv, back, Material.ARROW, msg("back", "<gold>⇜ Volver"),
                List.of(msg("back-detail", "<gray>Regresar al catálogo.")));
        put(inv, close, Material.BARRIER, msg("close", "<gray>Cerrar menú"), List.of());
        holder.actions.put(back,"inicio");
        holder.actions.put(close,"close");
    }

    private static String format(double number) {
        return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$","").replaceAll("\\.$","");
    }

    @EventHandler public void clicked(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        String action = holder.actions.get(slot);
        if (action == null) return;
        long now = System.currentTimeMillis();
        if (now - clickGate.getOrDefault(player.getUniqueId(), 0L) < 300L) return;
        clickGate.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (action.equals("confirm")) absorb(player, holder);
            else if (action.equals("close")) player.closeInventory();
            else if (action.equals("guia")) guide(player);
            else if (action.equals("rasgos") || action.equals("habilidades") || action.equals("vinculos"))
                openPage(player, "estadisticas");
            else openPage(player, action);
        });
    }

    @EventHandler public void dragged(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private void guide(Player player) {
        player.closeInventory();
        player.sendMessage(text(msg("guide", "<gold>Absorbe una reliquia para guardar su poder. Solo cuenta la versión superior de cada familia.")));
    }

    private void absorb(Player player, Holder holder) {
        if (!ready(player.getUniqueId()) || !rules.containsKey(holder.id)) {
            player.closeInventory();
            player.sendMessage(text(msg("storage-error", "<red>Tu Relicario no está disponible.")));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (player.getInventory().getHeldItemSlot() != holder.heldSlot ||
                held == null || held.getAmount() < 1 || !held.isSimilar(holder.snapshot) ||
                !holder.id.equals(relicId(held))) {
            player.closeInventory();
            player.sendMessage(text(msg("changed", "<red>La reliquia cambió. Vuelve a seleccionarla.")));
            return;
        }
        if (!RelicarioRules.canAbsorb(holder.id, store.discovered(player.getUniqueId()), rules)) {
            player.closeInventory();
            player.sendMessage(text(msg("duplicate", "<yellow>Ya absorbiste esta reliquia.")));
            return;
        }
        if (!store.discover(player.getUniqueId(), holder.id)) {
            player.closeInventory();
            player.sendMessage(text(msg("storage-error", "<red>No se pudo guardar tu Relicario.")));
            return;
        }
        // Ledger committed: only now consume exactly one item.
        if (held.getAmount() == 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        else { held.setAmount(held.getAmount() - 1); player.getInventory().setItemInMainHand(held); }
        stats.refresh(player);
        player.closeInventory();
        Entry entry = entries.get(holder.id);
        player.sendMessage(text(msg("absorbed", "<green>✦ Reliquia absorbida: <white>{nombre}")
                .replace("{nombre}", entry.name)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .8f, 1.4f);
    }
}
