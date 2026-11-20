package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.text.TextService;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Independent, read-only Hydev knowledge catalogue.
 * Only allowlisted YAML page IDs are navigable; no command or plugin is executed
 * through inventory items, and other category GUIs are never opened by its buttons.
 */
public final class MagoMenu implements Listener {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final List<Integer> SLOTS =
            List.of(10,12,14,16,28,30,32,34,38,40,42,44);
    private static final Set<String> RESERVED = Set.of("regresar", "cerrar");
    private final SpectreCorePlugin plugin;
    private final ArmorGuide armors;
    private volatile YamlConfiguration pages;
    private final Map<UUID, Long> clickGate = new HashMap<>();
    private boolean listening;

    private static final class Holder implements InventoryHolder {
        final String page;
        final List<String> history;
        final Map<Integer, String> targets = new HashMap<>();
        Inventory inventory;
        Holder(String page, List<String> history) {
            this.page = page; this.history = List.copyOf(history);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public MagoMenu(SpectreCorePlugin plugin) throws IOException, InvalidConfigurationException {
        this.plugin = plugin;
        this.armors = plugin.armors();
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    public void reload() throws IOException, InvalidConfigurationException {
        File installed = new File(plugin.getDataFolder(), "ui/mago.yml");
        if (!installed.exists()) plugin.saveResource("ui/mago.yml", false);
        if (!installed.isFile() || installed.length() > 96 * 1024)
            throw new IOException("ui/mago.yml ausente o supera 96 KiB");
        dev.hydev.spectrecore.config.PresentationMigrations.migrate(
                plugin.getDataFolder(), installed, "ui/mago.yml");
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(installed);
        mergeVisuals(installed, candidate);
        ConfigurationSection root = candidate.getConfigurationSection("pages");
        if (root == null || !root.isConfigurationSection("inicio") || root.getKeys(false).size() > 40)
            throw new IOException("El Mago: catálogo de páginas incompleto");
        for (String id : root.getKeys(false)) {
            if (!ID.matcher(id).matches() || RESERVED.contains(id))
                throw new IOException("El Mago: identificador de página inválido " + id);
            ConfigurationSection page = root.getConfigurationSection(id);
            if (page == null || !page.isString("title") || !page.isString("body")
                    || page.getString("title", "").length() > 256
                    || page.getString("body", "").length() > 3200
                    || page.getStringList("puntos").size() > 12)
                throw new IOException("El Mago: página inválida " + id);
            List<?> buttons = page.getList("buttons", List.of());
            if (buttons.size() > SLOTS.size()) throw new IOException("Demasiados accesos en " + id);
            Set<String> seen = new HashSet<>();
            for (Object raw : buttons) {
                if (!(raw instanceof Map<?, ?> map)
                        || !(map.get("page") instanceof String dest)
                        || !(map.get("label") instanceof String title)
                        || !ID.matcher(dest).matches() || !root.isConfigurationSection(dest)
                        || !seen.add(dest) || title.length() > 120
                        || (map.get("descripcion") instanceof String detail && detail.length() > 360))
                    throw new IOException("El Mago: acceso inválido en " + id);
            }
        }
        ConfigurationSection icons = candidate.getConfigurationSection("visual.iconos");
        if (icons == null || !candidate.isString("visual.portada")
                || !candidate.isString("visual.volver")
                || !candidate.isString("visual.cerrar"))
            throw new IOException("El Mago: recursos visuales ausentes");
        for (String icon : icons.getKeys(false))
            if (!ID.matcher(icon).matches() || !icons.isString(icon)
                    || icons.getString(icon, "").length() > 1024)
                throw new IOException("El Mago: icono inválido " + icon);
        pages = candidate;  // Only publish a completely validated catalogue.
    }

    /** Merge additive, user-editable content into legacy files; preserve every edited field. */
    private void mergeVisuals(File installed, YamlConfiguration candidate) throws IOException, InvalidConfigurationException {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("ui/mago.yml")) {
            if (stream == null) throw new IOException("Falta la plantilla visual de El Mago");
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        boolean changed = false;
        ConfigurationSection visual = defaults.getConfigurationSection("visual");
        if (visual == null) throw new IOException("Faltan recursos visuales");
        for (String key : visual.getKeys(true)) {
            if (visual.isConfigurationSection(key)) continue;
            String path = "visual." + key;
            if (!candidate.contains(path)) { candidate.set(path, visual.get(key)); changed = true; }
        }
        ConfigurationSection originals = defaults.getConfigurationSection("pages");
        if (originals != null) for (String id : originals.getKeys(false)) {
            if (!candidate.isConfigurationSection("pages." + id)) continue;
            ConfigurationSection original = originals.getConfigurationSection(id);
            if (original == null) continue;
            for (String key : List.of("resumen", "puntos")) {
                String path = "pages." + id + "." + key;
                if (!candidate.contains(path) && original.contains(key)) {
                    candidate.set(path, original.get(key));
                    changed = true;
                }
            }
            List<Map<?, ?>> targets = new ArrayList<>();
            for (Map<?, ?> raw : original.getMapList("buttons")) targets.add(raw);
            String base = "pages." + id + ".buttons";
            List<Map<?, ?>> installedButtons = new ArrayList<>(candidate.getMapList(base));
            List<Map<String,Object>> amended = new ArrayList<>();
            boolean buttonChanged = false;
            for (Map<?, ?> button : installedButtons) {
                Map<String,Object> copy = new java.util.LinkedHashMap<>();
                button.forEach((key,value) -> copy.put(String.valueOf(key),value));
                if (!copy.containsKey("descripcion")) for (Map<?, ?> source : targets)
                    if (source.get("page") != null && source.get("page").equals(copy.get("page"))
                            && source.get("descripcion") instanceof String description) {
                        copy.put("descripcion", description); buttonChanged = true; break;
                    }
                amended.add(copy);
            }
            if (buttonChanged) { candidate.set(base, amended); changed = true; }
        }
        if (!changed) return;
        File backup = new File(plugin.getDataFolder(), "backups/mago-visual-v1/ui/mago.yml");
        if (!backup.exists()) {
            Files.createDirectories(backup.toPath().getParent());
            Files.copy(installed.toPath(), backup.toPath());
        }
        AtomicFileStore.writeUtf8(installed.toPath(), candidate.saveToString());
    }

    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(styled(pages.getString("console-message", "Consulta al Mago dentro del juego.")));
            return true;
        }
        String section = args.length == 0 ? "inicio" : args[0].toLowerCase(Locale.ROOT);
        // Preserve legacy explicit commands, but NEVER link separate categories inside the GUI.
        if (section.equals("armaduras")) {
            if (armors != null) armors.open(player);
            else player.sendMessage(styled(pages.getString("armaduras-no-disponible",
                    "<red>El catálogo de armaduras está desactivado.")));
            return true;
        }
        if (section.equals("recetas")) {
            return plugin.getServer().dispatchCommand(player, "spectrecore cocina recetas");
        }
        if (section.equals("armadura") && args.length == 3) {
            if (armors != null) armors.openDetail(player, args[1], args[2]);
            else player.sendMessage(styled(pages.getString("armaduras-no-disponible",
                    "<red>El catálogo de armaduras está desactivado.")));
            return true;
        }
        if (!ID.matcher(section).matches() || !pages.isConfigurationSection("pages." + section)) {
            player.sendMessage(styled(pages.getString("invalid-page",
                    "<red>El Mago no conoce esa sección.")));
            return true;
        }
        open(player, section, List.of());
        return true;
    }

    public List<String> complete(String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>(pages.getConfigurationSection("pages").getKeys(false));
        ids.addAll(List.of("armaduras", "recetas"));
        return ids.stream().filter(id -> id.startsWith(prefix)).sorted().toList();
    }

    public void show(Player player, String id) { open(player, id, List.of()); }

    private void open(Player player, String requested, List<String> history) {
        String id = pages.isConfigurationSection("pages." + requested) ? requested : "inicio";
        ConfigurationSection page = pages.getConfigurationSection("pages." + id);
        if (page == null) return;
        Holder holder = new Holder(id, history);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                styled(page.getString("title", "<gold>El Mago")));
        holder.inventory = inventory;

        String title = page.getString("title", "<gold>El Mago");
        List<String> intro = new ArrayList<>();
        intro.add("");
        String summary = page.getString("resumen", page.getString("body", ""));
        if (!summary.isBlank()) intro.add(summary);
        if (page.isList("puntos")) {
            intro.add("");
            intro.addAll(page.getStringList("puntos").stream().limit(10).toList());
        }
        place(inventory, 4, pages.getString("visual.portada", ""), Material.ENCHANTED_BOOK,
                title, intro);

        List<Map<?, ?>> links = new ArrayList<>(page.getMapList("buttons"));
        int count = Math.min(links.size(), SLOTS.size());
        List<Integer> slots = count == 3 ? List.of(20,22,24)
                : count == 2 ? List.of(21,23)
                : count == 1 ? List.of(22) : SLOTS;
        for (int i = 0; i < count; i++) {
            Map<?, ?> option = links.get(i);
            String dest = String.valueOf(option.get("page"));
            String label = String.valueOf(option.get("label"));
            String icon = pages.getString("visual.iconos." + dest,
                    pages.getString("visual.iconos.guia", ""));
            String desc = option.get("descripcion") instanceof String value ? value
                    : pages.getString("pages." + dest + ".resumen",
                            pages.getString("pages." + dest + ".body", ""));
            place(inventory, slots.get(i), icon, Material.BOOK,
                    label, List.of("", desc,
                            "", pages.getString("visual.accion", "<gold>► Clic para leer")));
            holder.targets.put(slots.get(i), dest);
        }

        if (!history.isEmpty()) {
            place(inventory, 48, pages.getString("visual.volver", ""), Material.ARROW,
                    pages.getString("visual.nombre-volver", "<gold>⇜ Volver"),
                    List.of(pages.getString("visual.descripcion-volver", "<gray>Regresar a la página anterior")));
            holder.targets.put(48, "regresar");
        }
        place(inventory, 50, pages.getString("visual.cerrar", ""), Material.BARRIER,
                pages.getString("visual.nombre-cerrar", "<gray>Cerrar"),
                List.of(pages.getString("visual.descripcion-cerrar", "<gray>Terminar la consulta")));
        holder.targets.put(50, "cerrar");
        player.openInventory(inventory);
    }

    private void place(Inventory inventory, int slot, String encoded, Material fallback,
                       String title, List<String> lore) {
        ItemStack item;
        try { item = encoded == null || encoded.isBlank()
                ? new ItemStack(fallback) : CustomHeads.fromBase64(encoded); }
        catch (RuntimeException error) { item = new ItemStack(fallback); }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(styled(title));
        meta.lore(lore.stream().limit(30).map(MagoMenu::styled).toList());
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }
    private static Component styled(String message) {
        return TextService.component(message).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String target = holder.targets.get(slot);
        if (target == null) return;
        long now = System.currentTimeMillis();
        if (now - clickGate.getOrDefault(player.getUniqueId(), 0L) < 300) return;
        clickGate.put(player.getUniqueId(), now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            if (target.equals("cerrar")) { player.closeInventory(); return; }
            if (target.equals("regresar")) {
                int last = holder.history.size() - 1;
                if (last >= 0)
                    open(player, holder.history.get(last), holder.history.subList(0, last));
                return;
            }
            if (!pages.isConfigurationSection("pages." + target)) return;
            List<String> path = new ArrayList<>(holder.history);
            path.add(holder.page);
            if (path.size() > 16) return;
            open(player, target, path);
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            for (int slot : event.getRawSlots())
                if (slot < 54) { event.setCancelled(true); break; }
    }
    @EventHandler public void quit(PlayerQuitEvent event) { forget(event.getPlayer()); }

    public void forget(Player player) { clickGate.remove(player.getUniqueId()); }
    public void close() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                p.closeInventory();
        clickGate.clear();
    }
}
