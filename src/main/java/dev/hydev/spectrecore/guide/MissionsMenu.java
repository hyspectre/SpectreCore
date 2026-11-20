package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.systems.SlayerModule;
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
import java.nio.file.StandardCopyOption;
import dev.hydev.spectrecore.storage.AtomicFileStore;
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
            this.page = page; this.choices = choices;
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
            // Existing servers keep their custom YAML. Merge ONLY missing progression
            // UI, never overwrite edited prices, text or other menu entries.
            migrateProgression(file, candidate);
            for (String page : List.of("misiones", "revenant", "progresion")) {
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
            layout = candidate; // Publish atomically after validating the available pages.
        } catch (Exception exception) {
            plugin.getLogger().warning("Misiones: configuración rechazada; se conserva la última válida: "
                    + exception.getMessage());
        }
    }

    private void migrateProgression(File installed, YamlConfiguration candidate) throws Exception {
        boolean missingPage = !candidate.isConfigurationSection("pages.progresion");
        boolean missingEntry = !candidate.isConfigurationSection("pages.misiones.items.progresion");
        if (!missingPage && !missingEntry) return;
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource("ui/misiones.yml")) {
            if (stream == null) throw new IOException("No existe el catálogo predeterminado de progresión");
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        if (!candidate.isConfigurationSection("pages.misiones.items"))
            throw new IOException("El catálogo principal no es válido; no se migró");
        if (missingPage) {
            candidate.set("pages.progresion", defaults.getConfigurationSection("pages.progresion"));
        }
        if (missingEntry) {
            var template = defaults.getConfigurationSection("pages.misiones.items.progresion");
            if (template == null) throw new IOException("Falta el acceso de progresión en recursos");
            int slot = template.getInt("slot", 31);
            var existing = candidate.getConfigurationSection("pages.misiones.items");
            Set<Integer> used = new HashSet<>();
            for (String item : existing.getKeys(false))
                if (existing.isInt(item + ".slot")) used.add(existing.getInt(item + ".slot"));
            if (used.contains(slot)) {
                slot = -1;
                // Prefer the lower central panel; never replace a customized item.
                for (int fallback : List.of(31, 32, 30, 40, 39, 41, 22, 23, 21))
                    if (!used.contains(fallback)) { slot = fallback; break; }
                if (slot < 0) for (int i = 0; i < 54; i++)
                    if (!used.contains(i)) { slot = i; break; }
                if (slot < 0) throw new IOException("No quedan espacios para acceder a progresión");
            }
            candidate.set("pages.misiones.items.progresion", template);
            candidate.set("pages.misiones.items.progresion.slot", slot);
        }
        File backup = new File(plugin.getDataFolder(), "backups/progresion-v1/ui/misiones.yml");
        if (!backup.exists()) {
            Files.createDirectories(backup.toPath().getParent());
            Files.copy(installed.toPath(), backup.toPath());
        }
        AtomicFileStore.writeUtf8(installed.toPath(), candidate.saveToString());
        plugin.getLogger().info("Misiones: progresión instalada, conservando la configuración anterior en " + backup);
    }

    public boolean open(Player player, String page) {
        if (layout == null || !layout.getBoolean("enabled", true)) {
            player.sendMessage("§cEl menú de misiones está temporalmente desactivado.");
            return false;
        }
        if (!Set.of("misiones", "revenant", "progresion").contains(page))
            page = "misiones";
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
            for (String line : layout.getStringList(path + ".lore"))
                lore.add(styled(resolveLore(player, page, id, line)));
            if (page.equals("misiones") && id.equals("progresion")) {
                lore.add(styled(""));
                lore.add(styled("<gold>Tu nivel: <white>" + slayer.slayerLevel(player) + " <dark_gray>/ <white>11"));
                lore.add(styled("<gray>XP: <yellow>" + slayer.slayerXp(player)
                        + " <dark_gray>/ <yellow>" + slayer.slayerNextXp(player)));
            }
            if (page.equals("progresion") && id.equals("resumen")) {
                lore.add(styled(""));
                lore.add(styled("<gold>Progreso actual: <yellow>" + slayer.slayerLevel(player) + "<gray>/11"));
            }
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

    /** Known placeholders only, sourced from the authoritative Slayer module, never player commands. */
    private String resolveLore(Player player, String page, String item, String source) {
        if (source == null || !source.contains("{")) return source;
        if (page.equals("progresion")) {
            String message = source.replace("{nivel}", Integer.toString(slayer.slayerLevel(player)))
                    .replace("{xp}", Long.toString(slayer.slayerXp(player)))
                    .replace("{siguiente}", slayer.slayerNextXp(player))
                    .replace("{victorias}", Integer.toString(slayer.slayerVictories(player)));
            if (item.startsWith("nivel")) {
                try {
                    int unlockLevel = Integer.parseInt(item.substring("nivel".length()));
                    message = message.replace("{estado}", slayer.slayerRewardState(player, unlockLevel));
                } catch (NumberFormatException ignored) { }
            }
            return message;
        }
        if (page.equals("misiones") && item.equals("telefono"))
            return source.replace("{precio}", slayer.phonePriceLabel());
        if (!page.equals("revenant")) return source;
        if (Set.of("1", "2", "3").contains(item)) {
            int tier = Integer.parseInt(item);
            return source.replace("{precio}", slayer.contractPriceLabel("revenant", tier))
                    .replace("{objetivo}", Integer.toString(slayer.objectiveForTier(tier)))
                    .replace("{vida}", slayer.bossHealthLabel(tier))
                    .replace("{dano}", slayer.bossDamageLabel(tier))
                    .replace("{xp-slayer}", slayer.slayerBossXpLabel(tier));
        }
        if (item.equals("cancelar")) return source.replace("{precio}", slayer.cancellationPriceLabel("revenant"));
        if (item.equals("descripcion"))
            return source.replace("{precio1}", slayer.contractPriceLabel("revenant", 1))
                    .replace("{precio2}", slayer.contractPriceLabel("revenant", 2))
                    .replace("{precio3}", slayer.contractPriceLabel("revenant", 3));
        return source;
    }

    /** MiniMessage for new YAML; legacy color codes remain readable for existing server configs. */
    private static Component styled(String source) {
        return TextService.component(source).decoration(TextDecoration.ITALIC, false);
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

        // InventoryClickEvent is not safe for openInventory/closeInventory.
        // Defer every transition to the next server tick and re-check player state.
        if (holder.page.equals("misiones")) {
            switch (id) {
                case "1" -> nextTick(player, holder, () -> open(player, "revenant"));
                case "progresion" -> nextTick(player, holder, () -> open(player, "progresion"));
                case "telefono" -> nextTick(player, holder, () -> {
                    player.closeInventory();
                    slayer.buyPhoneFromMenu(player);
                });
                case "cerrar" -> nextTick(player, holder, player::closeInventory);
                default -> { /* locked / informative entries deliberately have no actions */ }
            }
            return;
        }
        if (holder.page.equals("progresion")) {
            if (id.equals("volver")) nextTick(player, holder, () -> open(player, "misiones"));
            else if (id.equals("cerrar")) nextTick(player, holder, player::closeInventory);
            return;
        }
        switch (id) {
            case "1", "2", "3" -> {
                int tier = Integer.parseInt(id);
                nextTick(player, holder, () -> {
                    player.closeInventory();
                    slayer.contractFromMenu(player, tier);
                });
            }
            case "reintentar" -> nextTick(player, holder, () -> {
                player.closeInventory();
                slayer.retrySpawnFromMenu(player);
            });
            case "cancelar" -> nextTick(player, holder, () -> {
                player.closeInventory();
                slayer.cancelFromMenu(player);
            });
            case "cerrar" -> nextTick(player, holder, () -> open(player, "misiones"));
            case "cerrar_menu" -> nextTick(player, holder, player::closeInventory);
            default -> { }
        }
    }

    private void nextTick(Player player, MenuHolder expected, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.isEnabled()
                    && player.getOpenInventory().getTopInventory().getHolder() == expected) action.run();
        });
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
