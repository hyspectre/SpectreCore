package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.systems.JobsModule;
import dev.hydev.spectrecore.systems.ProgressRules;
import dev.hydev.spectrecore.text.TextService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only resource sourcing board. Reads the actual treasure rules; never creates
 * recipes, items, payments or additional persisted state. No periodic inventory scan.
 */
public final class SupplyGuide implements Listener {
    private static final List<String> SOURCES = List.of(
            "mineria", "combate", "agricultura-1", "agricultura-2", "agricultura-3");
    private static final List<String> BUTTONS = List.of(
            "mineria", "combate", "agricultura-1", "agricultura-2", "agricultura-3", "volver", "cerrar", "guia");

    private record Resource(String type, String id, String job, String chanceDomain, int tier) {
        boolean valid() {
            return type != null && id != null && type.matches("[A-Z][A-Z0-9_]{1,63}")
                    && id.matches("[A-Z][A-Z0-9_]{1,63}");
        }
    }

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final JavaPlugin plugin;
    private final JobsModule jobs;
    private final MmoBridge mmo = new MmoBridge();
    private YamlConfiguration layout;
    private boolean listening;

    public SupplyGuide(JavaPlugin plugin, JobsModule jobs) {
        this.plugin = plugin;
        this.jobs = jobs;
    }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "ui/abastecimiento.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/abastecimiento.yml", false);
            if (!file.isFile() || file.length() > 32_768L)
                throw new IOException("El tablón de abastecimiento es ilegible o demasiado grande");
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file); // Strict: malformed YAML preserves the last valid layout.
            if (candidate.getInt("size", -1) != 54 || !candidate.isString("title")
                    || candidate.getString("title", "").isBlank()
                    || candidate.getString("title", "").length() > 256)
                throw new IOException("Tamaño o título inválido en el tablón de abastecimiento");
            Set<Integer> taken = new HashSet<>();
            for (String key : BUTTONS) {
                String root = "items." + key;
                if (!candidate.isInt(root + ".slot") || !candidate.isString(root + ".nombre")
                        || !candidate.isString(root + ".material") || !candidate.isList(root + ".lore"))
                    throw new IOException("Entrada incompleta: " + key);
                int slot = candidate.getInt(root + ".slot");
                Material icon = Material.matchMaterial(candidate.getString(root + ".material", ""));
                if (slot < 0 || slot >= 54 || !taken.add(slot) || icon == null || !icon.isItem()
                        || candidate.getString(root + ".nombre", "").isBlank()
                        || candidate.getString(root + ".nombre", "").length() > 256)
                    throw new IOException("Entrada fuera de límites o superpuesta: " + key);
                Object loreValue = candidate.get(root + ".lore");
                if (!(loreValue instanceof List<?> lore) || lore.size() > 16)
                    throw new IOException("Lista de texto inválida: " + key);
                for (Object value : lore)
                    if (!(value instanceof String line) || line.length() > 256)
                        throw new IOException("Texto inválido o demasiado largo: " + key);
            }
            for (String key : List.of("sin-hallazgos", "sin-acceso", "disponible",
                    "recurso-no-disponible", "consulta-no-disponible", "sin-contrato", "error")) {
                if (!candidate.isString("estados." + key)
                        || candidate.getString("estados." + key, "").length() > 256)
                    throw new IOException("Estado inválido: " + key);
            }
            layout = candidate;
        } catch (Exception invalid) {
            plugin.getLogger().warning("Abastecimiento: se conserva el último diseño válido: " + invalid.getMessage());
        }
    }

    private Resource source(String key) {
        if (key.equals("mineria")) {
            var yaml = ModuleConfiguration.module(plugin, "tesoros/mineria.yml");
            return new Resource(yaml.getString("tesoros.tipo", "MATERIAL"),
                    yaml.getString("tesoros.id", ""), "minero", "mineria", 0);
        }
        if (key.equals("combate")) {
            var yaml = ModuleConfiguration.module(plugin, "tesoros/combate.yml");
            return new Resource(yaml.getString("botin.tipo", "MATERIAL"),
                    yaml.getString("botin.id", ""), "cazador", "combate", 0);
        }
        int tier = Integer.parseInt(key.substring("agricultura-".length()));
        var yaml = ModuleConfiguration.module(plugin, "tesoros/agricultura.yml");
        return new Resource(yaml.getString("tipo", "CONSUMABLE"),
                yaml.getString("botin.tier" + tier, ""), "granjero", "agricultura", tier);
    }

    private String acquisition(Player player, Resource resource, boolean itemAvailable) {
        if (!resource.valid() || !itemAvailable)
            return layout.getString("estados.recurso-no-disponible");
        var yaml = ModuleConfiguration.module(plugin, "tesoros/" +
                (resource.chanceDomain().equals("mineria") ? "mineria" :
                        resource.chanceDomain().equals("combate") ? "combate" : "agricultura") + ".yml");
        boolean anyChance = false, unlocked = false;
        for (int tier = 1; tier <= 3; tier++) {
            if (resource.tier() > 0 && resource.tier() != tier) continue;
            Object raw = yaml.get("probabilidades." + resource.chanceDomain() + ".tier" + tier);
            if (!(raw instanceof Number rate) || !Double.isFinite(rate.doubleValue())
                    || rate.doubleValue() <= 0 || rate.doubleValue() > 100) continue;
            anyChance = true;
            if (player.hasPermission("tesoros." + resource.chanceDomain() + ".tier" + tier))
                unlocked = true;
        }
        if (!anyChance) return layout.getString("estados.sin-hallazgos");
        return layout.getString(unlocked ? "estados.disponible" : "estados.sin-acceso");
    }

    /** Single inventory snapshot per menu open; not an expensive repeating scan. */
    private Map<String, Integer> inventoryCounts(Player player) {
        Map<String, Integer> count = new HashMap<>();
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return count;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            String id = mmo.id(item);
            String type = mmo.type(item);
            if (id.isBlank() || type.isBlank()) continue;
            count.merge(type.toUpperCase(Locale.ROOT) + ":" + id.toUpperCase(Locale.ROOT),
                    item.getAmount(), Integer::sum);
        }
        return count;
    }

    private Component styled(String raw, Map<String, ?> values) {
        return TextService.render(raw, values).decoration(TextDecoration.ITALIC, false);
    }

    public boolean open(Player player) {
        if (layout == null) {
            player.sendMessage(TextService.component("<red>El tablón de abastecimiento no está disponible."));
            return false;
        }
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                styled(layout.getString("title"), Map.of()));
        holder.inventory = inv;
        boolean readable = Bukkit.getPluginManager().isPluginEnabled("MMOItems");
        Map<String, Integer> counts = inventoryCounts(player);
        for (String key : BUTTONS) {
            String root = "items." + key;
            int slot = layout.getInt(root + ".slot");
            Material material = Material.matchMaterial(layout.getString(root + ".material", "BOOK"));
            ItemStack icon = new ItemStack(material == null ? Material.BOOK : material);
            Map<String, Object> values = new HashMap<>();
            if (SOURCES.contains(key)) {
                Resource resource = source(key);
                ItemStack actual = resource.valid() ? mmo.build(resource.type(), resource.id(), 1) : null;
                if (actual != null && !actual.getType().isAir()) icon = actual.clone();
                String rank = jobs.rank(player, resource.job()) == 0
                        ? layout.getString("estados.sin-contrato")
                        : jobs.displayRank(ProgressRules.rank(jobs.rank(player, resource.job())));
                String quantity = readable && resource.valid() && actual != null
                        ? Integer.toString(counts.getOrDefault(
                                resource.type().toUpperCase(Locale.ROOT) + ":" + resource.id().toUpperCase(Locale.ROOT), 0))
                        : layout.getString("estados.consulta-no-disponible");
                values.put("cantidad", quantity);
                values.put("rango", rank);
                values.put("estado", styled(acquisition(player, resource, actual != null), Map.of()));
            }
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;
            meta.displayName(styled(layout.getString(root + ".nombre"), values));
            List<Component> lore = new ArrayList<>();
            for (String line : layout.getStringList(root + ".lore")) lore.add(styled(line, values));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            holder.actions.put(slot, key);
        }
        player.openInventory(inv);
        return true;
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String action = holder.actions.get(slot);
        if (action == null || !List.of("volver", "cerrar").contains(action)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (action.equals("volver")) jobs.openOverview(player);
            else player.closeInventory();
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int slot : event.getRawSlots()) if (slot < 54) {
            event.setCancelled(true);
            break;
        }
    }

    public void disable() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                player.closeInventory();
    }
}
