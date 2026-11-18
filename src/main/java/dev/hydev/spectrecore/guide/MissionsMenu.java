package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.systems.SlayerModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two 54-slot Paper inventories built and operated entirely by SpectreCore.
 * ui/misiones.yml is a PRESENTATION source; it has no command dispatch fields.
 * Each interaction is mapped to an allowlisted Java action after server-side checks.
 */
public final class MissionsMenu implements Listener {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private final JavaPlugin plugin;
    private final SlayerModule slayer;
    private final Map<UUID, Long> clickGate = new HashMap<>();
    private YamlConfiguration layout;
    private boolean listening;

    private static final class MenuHolder implements InventoryHolder {
        private final String page;
        private final Map<Integer, String> choices;
        private Inventory inventory;
        MenuHolder(String page, Map<Integer, String> choices) {
            this.page = page; this.choices = Map.copyOf(choices);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public MissionsMenu(JavaPlugin plugin, SlayerModule slayer) {
        this.plugin = plugin;
        this.slayer = slayer;
    }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening = true;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "ui/misiones.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/misiones.yml", false);
            if (!file.isFile() || file.length() > 64 * 1024)
                throw new IOException("ui/misiones.yml ausente o demasiado grande");
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(file);
            for (String page : List.of("misiones", "revenant")) {
                String root = "pages." + page;
                ConfigurationSection items = candidate.getConfigurationSection(root + ".items");
                if (items == null || !candidate.isString(root + ".title")
                        || candidate.getInt(root + ".size") != 54 || items.getKeys(false).size() > 40)
                    throw new IOException(root + " incompleto");
                Set<Integer> occupied = new HashSet<>();
                for (String id : items.getKeys(false)) {
                    String key = root + ".items." + id;
                    if (!candidate.isInt(key + ".slot") || !candidate.isString(key + ".name")
                            || !candidate.isString(key + ".texture") || !candidate.isList(key + ".lore"))
                        throw new IOException(key + " incompleto");
                    int slot = candidate.getInt(key + ".slot");
                    if (slot < 0 || slot >= 54 || !occupied.add(slot))
                        throw new IOException(key + " usa un slot duplicado o inválido");
                    if (candidate.getString(key + ".name", "").length() > 256
                            || candidate.getString(key + ".texture", "").length() > 1024
                            || candidate.getStringList(key + ".lore").size() > 32)
                        throw new IOException(key + " supera los límites");
                }
            }
            layout = candidate; // Publish atomically after validating BOTH pages.
        } catch (Exception exception) {
            plugin.getLogger().warning("Misiones: configuración rechazada; se conserva la última válida: "
                    + exception.getMessage());
        }
    }

    public boolean open(Player player, String page) {
        if (layout == null || !layout.getBoolean("enabled", true)) {
            player.sendMessage("§cEl menú de misiones está temporalmente desactivado.");
            return false;
        }
        if (!Set.of("misiones", "revenant").contains(page)) page = "misiones";
        String root = "pages." + page;
        ConfigurationSection buttons = layout.getConfigurationSection(root + ".items");
        if (buttons == null) return false;
        Map<Integer, String> choices = new HashMap<>();
        MenuHolder holder = new MenuHolder(page, choices);
        Inventory inventory = Bukkit.createInventory(holder, 54, styled(layout.getString(root + ".title", "Misiones")));
        holder.inventory = inventory;
        for (String id : buttons.getKeys(false)) {
            String path = root + ".items." + id;
            int slot = layout.getInt(path + ".slot");
            String encoded = layout.getString(path + ".texture", "");
            ItemStack item;
            try {
                item = encoded.isBlank() ? new ItemStack(Material.BOOK) : CustomHeads.fromBase64(encoded);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                item = new ItemStack(Material.BOOK);
                plugin.getLogger().warning("Misiones: textura inválida en " + path);
            }
            ItemMeta meta = item.getItemMeta();
            meta.displayName(styled(layout.getString(path + ".name", "Misiones")));
            List<Component> lore = new ArrayList<>();
            for (String line : layout.getStringList(path + ".lore")) lore.add(styled(line));
            if (page.equals("revenant") && Set.of("1", "2", "3").contains(id)) {
                int tier = Integer.parseInt(id);
                lore.add(styled(""));
                lore.add(styled(slayer.progressLine(player, "revenant", tier)));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            choices.put(slot, id);
        }
        player.openInventory(inventory);
        return true;
    }

    private static Component styled(String source) {
        if (source == null || source.isEmpty()) return Component.empty();
        Matcher matcher = HEX.matcher(source);
        StringBuffer legacy = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) replacement.append('&').append(c);
            matcher.appendReplacement(legacy, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(legacy);
        return LegacyComponentSerializer.legacySection()
                .deserialize(ChatColor.translateAlternateColorCodes('&', legacy.toString()));
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true); // Includes shift-click from the player's own inventory.
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String id = holder.choices.get(slot);
        if (id == null) return;
        long now = System.currentTimeMillis(), previous = clickGate.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 350L) return;
        clickGate.put(player.getUniqueId(), now);

        if (holder.page.equals("misiones")) {
            switch (id) {
                case "1" -> open(player, "revenant");
                case "telefono" -> {
                    player.closeInventory();
                    slayer.buyPhoneFromMenu(player);
                }
                case "cerrar" -> player.closeInventory();
                default -> { /* locked / informative entries deliberately have no actions */ }
            }
            return;
        }
        switch (id) {
            case "1", "2", "3" -> {
                player.closeInventory();
                slayer.contractFromMenu(player, Integer.parseInt(id));
            }
            case "cancelar" -> {
                player.closeInventory();
                slayer.cancelFromMenu(player);
            }
            case "cerrar" -> open(player, "misiones");
            case "cerrar_menu" -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder)
            for (int slot : event.getRawSlots()) if (slot < 54) {
                event.setCancelled(true); break;
            }
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        clickGate.remove(event.getPlayer().getUniqueId());
    }

    public void disable() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)
                player.closeInventory();
        clickGate.clear();
    }
}
