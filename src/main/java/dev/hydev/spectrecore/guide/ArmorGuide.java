package dev.hydev.spectrecore.guide;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.SpectreCorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import dev.hydev.spectrecore.model.CustomHeads;
import dev.hydev.spectrecore.text.TextService;
import org.bukkit.Material;
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
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only native 54-slot GUI. No equipment mutations or command dispatch from items. */
public final class ArmorGuide implements Listener {
    private record Entry(String name, String shortName, String skill, String set, String ability, int color) { }
    private List<Entry> entries() { return List.of(
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-058", "Raigambre Ancestral"), Settings.text("equipamiento", "ArmorGuide.etiqueta-059", "Raigambre"), Settings.text("equipamiento", "ArmorGuide.etiqueta-060", "botania"), Settings.text("equipamiento", "ArmorGuide.etiqueta-061", "HYDEV_RAIGAMBRE"), Settings.text("equipamiento", "ArmorGuide.etiqueta-062", "Botania"), Settings.module("equipamiento/config.yml").getInt("libro.colores.botania",0x537D3B)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-063", "Armadura Necrótica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-064", "Necrótica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-065", "necrotica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-066", "NECROTICO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-067", "Trinidad Profana"), Settings.module("equipamiento/config.yml").getInt("libro.colores.necrotica",0x685093)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-068", "Sangre del Laberinto"), Settings.text("equipamiento", "ArmorGuide.etiqueta-069", "Laberinto"), Settings.text("equipamiento", "ArmorGuide.etiqueta-070", "asterion"), Settings.text("equipamiento", "ArmorGuide.etiqueta-071", "HYDEV_LABERINTO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-072", "Asterión"), Settings.module("equipamiento/config.yml").getInt("libro.colores.asterion",0xA25735)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-073", "Voto del Deshonrado"), Settings.text("equipamiento", "ArmorGuide.etiqueta-074", "Deshonrado"), Settings.text("equipamiento", "ArmorGuide.etiqueta-075", "ultimo-juramento"), Settings.text("equipamiento", "ArmorGuide.etiqueta-076", "HYDEV_VOTO"), Settings.text("equipamiento", "ArmorGuide.etiqueta-077", "Último Juramento"), Settings.module("equipamiento/config.yml").getInt("libro.colores.ultimo-juramento",0x6F7486)),
        new Entry(Settings.text("equipamiento", "ArmorGuide.etiqueta-078", "Herencia Dracónica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-079", "Dracónica"), Settings.text("equipamiento", "ArmorGuide.etiqueta-080", "sangre-primer-dragon"), Settings.text("equipamiento", "ArmorGuide.etiqueta-081", "HYDEV_IGNEA"), Settings.text("equipamiento", "ArmorGuide.etiqueta-082", "Sangre del Primer Dragón"), Settings.module("equipamiento/config.yml").getInt("libro.colores.sangre-primer-dragon",0xAA3A29))
    ); }
    private Map<String, String> bonusNames() { return Map.ofEntries(
        Map.entry("max-health", Settings.text("equipamiento", "ArmorGuide.etiqueta-083", "Vida máxima")), Map.entry("max-mana", Settings.text("equipamiento", "ArmorGuide.etiqueta-084", "Maná máximo")),
        Map.entry("magic-damage", Settings.text("equipamiento", "ArmorGuide.etiqueta-085", "Daño mágico (%)")), Map.entry("damage-reduction", Settings.text("equipamiento", "ArmorGuide.etiqueta-086", "Reducción daño (%)")),
        Map.entry("weapon-damage", Settings.text("equipamiento", "ArmorGuide.etiqueta-087", "Daño de arma (%)")), Map.entry("critical-strike-chance", Settings.text("equipamiento", "ArmorGuide.etiqueta-088", "Prob. crítica (%)")),
        Map.entry("critical-strike-power", Settings.text("equipamiento", "ArmorGuide.etiqueta-089", "Poder crítico (%)")),
        Map.entry("fire-damage-reduction", Settings.text("equipamiento", "ArmorGuide.etiqueta-090", "Resist. fuego (%)")),
        Map.entry("potion-regeneration", Settings.text("equipamiento", "ArmorGuide.etiqueta-091", "Regeneración")),
        Map.entry("potion-fire_resistance", Settings.text("equipamiento", "ArmorGuide.etiqueta-092", "Resist. fuego (nivel)")),
        Map.entry("potion-speed", Settings.text("equipamiento", "ArmorGuide.etiqueta-093", "Velocidad (nivel)")), Map.entry("movement-speed", Settings.text("equipamiento", "ArmorGuide.etiqueta-094", "Velocidad"))
    ); }
    private final SpectreCorePlugin plugin;
    private YamlConfiguration layout;
    private boolean listening;
    private final Map<UUID, Long> clickGate = new HashMap<>();
    public ArmorGuide(SpectreCorePlugin plugin) { this.plugin = plugin; }
    private static final class Holder implements InventoryHolder {
        final String page;
        final Map<Integer, String> choices = new HashMap<>();
        Inventory inventory;
        Holder(String page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listening = true;
    }
    public void reload() {
        File file = new File(plugin.getDataFolder(), "ui/armaduras.yml");
        try {
            if (!file.exists()) plugin.saveResource("ui/armaduras.yml", false);
            if (!file.isFile() || file.length() > 96 * 1024)
                throw new IOException("ui/armaduras.yml ausente o demasiado grande");
            YamlConfiguration next = new YamlConfiguration();
            next.load(file);
            mergeEquipmentLabels(file, next);
            for (String page : Set.of("armaduras", "botania", "necrotica", "asterion",
                    "ultimo-juramento", "sangre-primer-dragon")) {
                String root = "pages." + page;
                ConfigurationSection items = next.getConfigurationSection(root + ".items");
                if (items == null || next.getInt(root + ".size") != 54
                        || !next.isString(root + ".title") || items.getKeys(false).size() > 40)
                    throw new IOException("Página incompleta: " + page);
                Set<Integer> occupied = new HashSet<>();
                for (String id : items.getKeys(false)) {
                    String base = root + ".items." + id;
                    int slot = next.getInt(base + ".slot", -1);
                    if (slot < 0 || slot >= 54 || !occupied.add(slot)
                            || !next.isString(base + ".texture")
                            || !next.isString(base + ".name") || !next.isList(base + ".lore")
                            || next.getString(base + ".texture", "").length() > 1024
                            || next.getString(base + ".name", "").length() > 256
                            || next.getStringList(base + ".lore").size() > 30)
                        throw new IOException("Icono inválido: " + base);
                }
                for (String required : page.equals("armaduras")
                        ? List.of("botania", "necrotica", "asterion",
                                "ultimo-juramento", "sangre-primer-dragon", "cerrar")
                        : List.of("bonos", "habilidad", "volver", "cerrar"))
                    if (!items.contains(required)) throw new IOException("Icono ausente: " + required);
            }
            layout = next;
        } catch (Exception ex) {
            plugin.getLogger().warning("Armaduras: se conserva la última configuración válida: " + ex.getMessage());
        }
    }
    private void mergeEquipmentLabels(File installed, YamlConfiguration candidate) throws Exception {
        YamlConfiguration defaults = new YamlConfiguration();
        try (var stream = plugin.getResource("ui/armaduras.yml")) {
            if (stream == null) throw new IOException("No existe la plantilla de etiquetas de armaduras");
            defaults.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
        ConfigurationSection section = defaults.getConfigurationSection("indicador");
        if (section == null) return;
        boolean changed = false;
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            String path = "indicador." + key;
            if (!candidate.contains(path)) {
                candidate.set(path, section.get(key));
                changed = true;
            }
        }
        // Only update the shipped, misleading legacy label; preserve custom words.
        String oldLabel = "<green>✦ ʜᴀʙɪʟɪᴅᴀᴅ ᴅɪsᴘᴏɴɪʙʟᴇ";
        if (oldLabel.equals(candidate.getString("indicador.completo"))) {
            candidate.set("indicador.completo", section.getString("completo"));
            changed = true;
        }
        if (!changed) return;
        File backup = new File(plugin.getDataFolder(), "backups/armaduras-indicador-v2/ui/armaduras.yml");
        if (!backup.exists()) {
            java.nio.file.Files.createDirectories(backup.toPath().getParent());
            java.nio.file.Files.copy(installed.toPath(), backup.toPath());
        }
        dev.hydev.spectrecore.storage.AtomicFileStore.writeUtf8(
                installed.toPath(), candidate.saveToString());
    }

    private List<Component> equippedLore(Player player, Entry entry) {
        var config = Settings.module("equipamiento/config.yml");
        int count = plugin.sets() == null ? 0
                : plugin.sets().countArmor(player, config.getConfigurationSection("skills." + entry.skill + ".armor"));
        int pieces = Math.max(1, Math.min(4, config.getInt("pasivos.piezas", 2)));
        boolean partial = Set.of("asterion", "ultimo-juramento", "sangre-primer-dragon").contains(entry.skill);
        String status;
        if (count == 4) {
            long remaining = plugin.cooldowns().remainingSeconds(player.getUniqueId(), entry.skill);
            if (!plugin.hasAccess(player, entry.skill, true))
                status = layout.getString("indicador.sin-acceso", "<yellow>Habilidad no disponible");
            else if (remaining > 0)
                status = layout.getString("indicador.recarga", "<yellow>Recarga: {s}s")
                        .replace("{s}", Long.toString(remaining));
            else status = layout.getString("indicador.completo", "<green>Conjunto completo");
        }
        else if (partial && count >= pieces) status = layout.getString("indicador.parcial", "<yellow>Bonificación parcial activa");
        else status = layout.getString("indicador.incompleto", "<gray>Bonificaciones aún incompletas");
        String summary = layout.getString("indicador.piezas",
                "<gray>Piezas equipadas: <yellow>{equipadas}<gray>/4")
                .replace("{equipadas}", Integer.toString(count));
        return List.of(styled(summary), styled(status));
    }

    public void disable() {
        if (listening) HandlerList.unregisterAll(this);
        listening = false;
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)
                player.closeInventory();
        clickGate.clear();
    }
    private double n(Entry e, String key, double fallback) {
        return Settings.module("equipamiento/config.yml")
                .getDouble("skills." + e.skill + "." + key, fallback);
    }
    private String value(double number) { return String.format(Locale.ROOT, "%.2f", number).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private String v(Entry e, String key, double fallback) { return value(n(e, key, fallback)); }
    private String pct(Entry e, String key, double fallback) { return value(n(e, key, fallback) * 100) + "%"; }
    private String seconds(Entry e, String key, double fallback) { return value(n(e, key, fallback) / 20) + " s"; }


public void open(Player player) { openPage(player, "armaduras"); }

    public void openDetail(Player player, String skill, String view) {
        if (entries().stream().anyMatch(e -> e.skill.equals(skill))) openPage(player, skill);
        else open(player);
    }

    public boolean openPage(Player player, String page) {
        if (layout == null) {
            player.sendMessage(Settings.message("equipamiento", "ArmorGuide.gui-no-disponible",
                    "<red>El catálogo de armaduras no está disponible por ahora."));
            return false;
        }
        if (!layout.isConfigurationSection("pages." + page)) page = "armaduras";
        final String selectedPage = page;
        Entry selected = entries().stream().filter(e -> e.skill.equals(selectedPage)).findFirst().orElse(null);
        String root = "pages." + page;
        ConfigurationSection items = layout.getConfigurationSection(root + ".items");
        if (items == null) return false;
        Holder holder = new Holder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54, styled(layout.getString(root + ".title", "Armaduras")));
        holder.inventory = inventory;
        YamlConfiguration sets = null;
        if (selected != null) {
            Plugin mmo = Bukkit.getPluginManager().getPlugin("MMOItems");
            File file = mmo == null ? null : new File(mmo.getDataFolder(), "item-sets.yml");
            sets = file != null && file.isFile() ? YamlConfiguration.loadConfiguration(file) : null;
        }
        for (String id : items.getKeys(false)) {
            String at = root + ".items." + id;
            String encoded = layout.getString(at + ".texture", "");
            ItemStack item;
            try { item = encoded.isBlank() ? new ItemStack(Material.BOOK) : CustomHeads.fromBase64(encoded); }
            catch (IllegalArgumentException | IllegalStateException error) {
                item = new ItemStack(Material.BOOK);
                plugin.getLogger().warning("Armaduras: textura inválida en " + at);
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            Entry named = selected == null ? entries().stream().filter(e -> e.skill.equals(id)).findFirst().orElse(null) : selected;
            String title = layout.getString(at + ".name", "Armaduras");
            if (named != null) title = title.replace("{nombre}", named.name).replace("{habilidad}", named.ability);
            meta.displayName(styled(title));
            List<Component> lore = new ArrayList<>();
            for (String line : layout.getStringList(at + ".lore")) {
                if (selected != null && line.equals("{bonos}")) {
                    for (String bonusLine : bonuses(selected, sets))
                        if (lore.size() < 32) lore.add(styled(bonusLine));
                } else if (selected != null && line.equals("{habilidad}")) {
                    for (String abilityLine : ability(selected))
                        if (lore.size() < 32) lore.add(styled(abilityLine));
                } else if (lore.size() < 32) {
                    lore.add(styled(line));
                }
            }
            if (named != null && (selected != null && id.equals("informacion")
                    || selected == null && named.skill.equals(id))) {
                lore.add(styled(""));
                lore.addAll(equippedLore(player, named));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            int slot = layout.getInt(at + ".slot");
            inventory.setItem(slot, item);
            holder.choices.put(slot, id);
        }
        player.openInventory(inventory);
        return true;
    }

    private static Component styled(String value) {
        return TextService.component(value).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        String id = holder.choices.get(slot);
        if (id == null) return;
        long now = System.currentTimeMillis();
        if (now - clickGate.getOrDefault(player.getUniqueId(), 0L) < 350L) return;
        clickGate.put(player.getUniqueId(), now);
        if (id.equals("cerrar")) { defer(player, holder, player::closeInventory); return; }
        if (id.equals("volver")) { defer(player, holder, () -> open(player)); return; }
        if (holder.page.equals("armaduras") && entries().stream().anyMatch(e -> e.skill.equals(id)))
            defer(player, holder, () -> openPage(player, id));
    }

    private void defer(Player player, Holder holder, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() == holder)
                action.run();
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            for (int slot : event.getRawSlots())
                if (slot < 54) { event.setCancelled(true); break; }
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        clickGate.remove(event.getPlayer().getUniqueId());
    }

    /** A configurable message can contain literal line breaks to keep each lore line short. */
    private List<String> compactLines(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source)
            for (String part : line.split("\\R", -1))
                if (!part.isBlank() || !result.isEmpty()) result.add(part);
        return result;
    }

    private List<String> bonuses(Entry e, YamlConfiguration sets) {
        List<String> lines = new ArrayList<>();
        switch (e.skill) {
            case "asterion" -> {
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-009", "<gray>({piezas}) <white>ᴠɪᴅᴀ ᴍáx. <gold>+{vida}","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"vida",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.laberinto-vida",6))));
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-010","<gray>(4) <white>ʀᴇsɪsᴛ. ᴇᴍᴘᴜᴊᴇ <gold>+{valor1}","valor1", v(e, "passive-knockback-resistance", .10)));
            }
            case "ultimo-juramento" -> lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-011", "<gray>({piezas}) <white>ᴠᴇʟᴏᴄɪᴅᴀᴅ <light_purple>+{velocidad}%","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"velocidad",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.voto-velocidad",.04)*100)));
            case "sangre-primer-dragon" -> {
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-012", "<gray>({piezas}) <white>ᴠɪᴅᴀ ᴍáx. <gold>+{vida}\n<gray>     <white>ᴠᴇʟᴏᴄɪᴅᴀᴅ <gold>+{velocidad}%","piezas",Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2),"vida",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.dragon-vida",4)),"velocidad",value(Settings.module("equipamiento/config.yml").getDouble("pasivos.dragon-velocidad",.02)*100)));
                lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-013","<gray>(4) <white>ᴅᴀñᴏ ᴄᴇʀᴄᴀɴᴏ <gold>+{valor1}\n<gray>     <white>ᴅᴀñᴏ ʀᴇᴄɪʙɪᴅᴏ <gold>-{valor2}","valor1", pct(e, "passive-damage-bonus", .03),"valor2", pct(e, "passive-damage-reduction", .03)));
            }
            default -> { /* Estos conjuntos reciben sus bonos desde la tabla de sets. */ }
        }
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-015", "<gray>(4) <white> ʜᴀʙɪʟɪᴅᴀᴅ <gold>{e-ability}", "e-ability", e.ability));
        if (e.skill.equals("ultimo-juramento")) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-016","<gray>⚔ <white>ᴊᴜʀᴀᴍᴇɴᴛᴏ ᴘʀᴏғᴀɴᴀᴅᴏ\n<gray>    ᴇᴄᴏ ᴅᴇ ʙʟɪɴᴋ <light_purple>+{valor1}\n<gray>    ᴘʀóx. ɢᴏʟᴘᴇ ᴇɴ {valor2}","valor1", pct(e, "blink-next-hit-bonus", .10),"valor2", seconds(e, "blink-next-hit-window-ticks", 50)));
        if (e.skill.equals("sangre-primer-dragon")) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-017","<gray>⚔ <white>ᴅʀᴀᴄᴏʀᴇʟɪǫᴜɪᴀ\n<gray>    ʙᴇɴᴅɪᴄɪóɴ <gold>+{valor1} s","valor1", v(e, "weapon-duration-bonus-seconds", 2)));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-018", ""));
        lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-019", "<gold>ʙᴏɴᴏs ᴀᴅɪᴄɪᴏɴᴀʟᴇs"));
        ConfigurationSection bonuses = sets == null ? null : sets.getConfigurationSection(e.set + ".bonuses");
        if (bonuses == null) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-020", "<gray>ᴏᴛʀᴏs ʙᴏɴᴏs ɴᴏ ᴅɪsᴘᴏɴɪʙʟᴇs."));
        else if (bonuses.getKeys(false).isEmpty()) { /* Sin bonos adicionales definidos. */ }
        else for (String count : bonuses.getKeys(false)) {
            ConfigurationSection level = bonuses.getConfigurationSection(count);
            if (level == null) continue;
            lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-022", "<gray>({count})", "count", count.equals("5") ? "4 + arma" : count));
            for (String key : level.getKeys(false)) {
                if (level.isConfigurationSection(key)) {
                    lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-023","<gray>ʜᴀʙɪʟɪᴅᴀᴅ ᴇxᴛʀᴀ <white>{valor1}","valor1", level.getString(key + ".type", "adicional").replace('_', ' ')));
                    continue;
                }
                if (key.equals("potion-regeneration")) {
                    lines.add(Settings.text("equipamiento", "ArmorGuide.regeneracion",
                            "<gray>     <green>ʀᴇɢᴇɴᴇʀᴀᴄɪóɴ ɪ"));
                } else {
                    String display = bonusNames().get(key);
                    if (display != null) lines.add(Settings.text("equipamiento", "ArmorGuide.mensaje-024",
                            "<gray>     <white>{valor1} <green>+{level-get-key}", "valor1", display, "level-get-key", level.get(key)));
                }
            }
        }
        return compactLines(lines);
    }

    private List<String> ability(Entry e) {
        List<String> t = new ArrayList<>();
        t.add("<white>" + e.ability);
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-027", ""));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-028", "<gray>ʀᴇǫᴜɪᴇʀᴇ <white>4 ᴘɪᴇᴢᴀs"));
        switch (e.skill) {
            case "botania" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-029","<gray>Encadena <white>{v-e-hits-required-4} golpes\n<gray>Máximo <yellow>{valor2} <gray>entre golpes.","v-e-hits-required-4", v(e, "hits-required", 4),"valor2", seconds(e, "hit-window-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-030","<gray>{valor1} ʟᴀᴛɪɢᴀᴢᴏs <white>ᴅᴇ {v-e-lash-damage-3} ʜᴘ\n<gray>ʟᴇɴᴛɪᴛᴜᴅ ɪ <yellow>{valor3}","valor1", value(Math.min(8, Math.max(1, n(e, "lash-count", 4)))),"v-e-lash-damage-3", v(e, "lash-damage", 3),"valor3", seconds(e, "slowness-ticks", 30)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-031", "<gray>ᴀʟᴄᴀɴᴄᴇ <white>{v-e-lash-range-7} ʙʟᴏǫᴜᴇs", "v-e-lash-range-7", v(e, "lash-range", 7)));
            }
            case "necrotica" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-033","<gray>ᴀʟ ʀᴇᴄɪʙɪʀ ᴅᴀñᴏ ʜᴏsᴛɪʟ\n<gray>ɪɴᴠᴏᴄᴀ <white>{valor1} ᴄᴀʟᴀᴠᴇʀᴀs","valor1", value(Math.min(3, Math.max(1, n(e, "skulls", 3))))));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-034", "<gray>{v-e-shots-per-skull-3} ᴅɪsᴘᴀʀᴏs ᴘᴏʀ ᴄᴀʟᴀᴠᴇʀᴀ\n<gray>ᴅᴀñᴏ ᴘᴏʀ ɪᴍᴘᴀᴄᴛᴏ <white>{v-e-damage-per-shot-5} ʜᴘ", "v-e-shots-per-skull-3", v(e, "shots-per-skull", 3), "v-e-damage-per-shot-5", v(e, "damage-per-shot", 5)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-035","<gray>Alcance <white>{v-e-range-8} bloques\n<gray>Un disparo cada <white>{valor2}.\n<gray>Sin enemigo cercano, conserva almas.","v-e-range-8", v(e, "range", 8),"valor2", seconds(e, "fire-interval-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-036", "<gray>ɴᴏ ᴀᴛᴀᴄᴀɴ ᴊᴜɢᴀᴅᴏʀᴇs."));
            }
            case "asterion" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-037", "<gray>ғᴜʀɪᴀ <white>+{v-e-fury-on-hit-12} <gray>ᴘᴏʀ ɢᴏʟᴘᴇ\n<gray>      <white>+{v-e-fury-on-damage-6} <gray>ᴀʟ ʀᴇᴄɪʙɪʀ ᴅᴀñᴏ", "v-e-fury-on-hit-12", v(e, "fury-on-hit", 12), "v-e-fury-on-damage-6", v(e, "fury-on-damage", 6)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-038","<gray>{furia} ᴅᴇ ғᴜʀɪᴀ: <white>ᴅᴏʙʟᴇ sʜɪғᴛ\n<gray>ᴠᴇɴᴛᴀɴᴀ <yellow>{valor1}","furia",Settings.module("equipamiento/config.yml").getInt("furia-maxima",100),"valor1", seconds(e, "double-shift-window-ticks", 9)));
                t.add(Settings.text("equipamiento", "ArmorGuide.impetu-como",
                        "<#E3BC7B>Ímpetu <gray>· Asterión activo\n<gray>Primer golpe: fija el objetivo.\n<gray>Golpes al mismo enemigo:\n<green>+{valor1} daño por carga.\n<gray>Hasta <white>{cargas} cargas (+{valor2}%).",
                        "valor1", pct(e, "damage-per-stack", .03),
                        "cargas", v(e, "max-combo-stacks", 4),
                        "valor2", value(n(e, "max-combo-stacks", 4) * n(e, "damage-per-stack", .03) * 100)));
                t.add(Settings.text("equipamiento", "ArmorGuide.impetu-reinicio",
                        "<gray>Se reinicia al cambiar de enemigo\n<gray>o dejar pasar más de <white>{segundos}\n<gray>entre golpes.", "segundos", seconds(e, "combo-gap-ticks", 50)));
            }
            case "ultimo-juramento" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-042","<gray>ᴅᴏʙʟᴇ sʜɪғᴛ <white>ᴇɴ {valor1}","valor1", seconds(e, "double-shift-window-ticks", 9)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-043",
                        "<gray>ᴠᴇʟᴏᴄɪᴅᴀᴅ <light_purple>+{pct-e-speed-bonus-20}\n<gray>ᴅᴀñᴏ ʀᴇᴄɪʙɪᴅᴏ <red>+{valor3}",
                        "pct-e-speed-bonus-20", pct(e, "speed-bonus", .20),
                        "valor3", pct(e, "damage-taken-increase", .20)));
                if (Settings.module("equipamiento/config.yml")
                        .getBoolean("skills.ultimo-juramento.auraskills.enabled", true)) {
                    t.add(Settings.text("equipamiento", "ArmorGuide.juramento-fuerza",
                            "<gray>ғᴜᴇʀᴢᴀ <light_purple>+{valor1}",
                            "valor1", v(e, "auraskills.strength-bonus", 45)));
                } else {
                    t.add(Settings.text("equipamiento", "ArmorGuide.juramento-dano",
                            "<gray>ᴅᴀñᴏ ᴄᴇʀᴄᴀɴᴏ <light_purple>+{valor1}",
                            "valor1", pct(e, "melee-damage-bonus", .15)));
                }
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-044","<gray>ɢᴏʟᴘᴇs ᴘʀᴏʟᴏɴɢᴀɴ <white>{valor1}\n<gray>ʜᴀsᴛᴀ <white>{valor2} s","valor1", seconds(e, "extension-per-hit-ticks", 12),"valor2", v(e, "maximum-duration-seconds", 10)));
            }
            case "sangre-primer-dragon" -> {
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-045","<gray>ʀᴇᴄɪʙᴇ ᴅᴀñᴏ ᴏ ᴄᴏɴᴇᴄᴛᴀ\n<gray><white>{valor1} ɢᴏʟᴘᴇs <gray>ᴇɴ {valor2}","valor1", v(e, "melee-hits-to-activate", 2),"valor2", seconds(e, "melee-hit-window-ticks", 60)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-046","<gray>ᴅᴀñᴏ ᴄᴇʀᴄᴀɴᴏ <gold>+{valor1}\n<gray>ᴅᴀñᴏ ʀᴇᴄɪʙɪᴅᴏ <gold>-{valor2}\n<gray>ᴠᴇʟᴏᴄɪᴅᴀᴅ <gold>+{pct-e-speed-bonus-06}","valor1", pct(e, "active-damage-bonus", .06),"valor2", pct(e, "active-damage-reduction", .06),"pct-e-speed-bonus-06", pct(e, "speed-bonus", .06)));
                t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-047", "<gray>ʀᴇᴄᴜᴘᴇʀᴀ <green>{v-e-heal-on-activation-4} ʜᴘ", "v-e-heal-on-activation-4", v(e, "heal-on-activation", 4)));
            }
            default -> { }
        }
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-049", ""));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-050","<gray>ᴅᴜʀᴀᴄɪóɴ <yellow>{valor1} s","valor1", v(e, "duration-seconds", e.skill.equals("necrotica") ? 30 : e.skill.equals("botania") ? 8 : e.skill.equals("ultimo-juramento") ? 7 : 10)));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-051","<gray>ᴇɴғʀɪᴀᴍɪᴇɴᴛᴏ <yellow>{valor1} s","valor1", v(e, "cooldown-seconds", e.skill.equals("botania") ? 30 : e.skill.equals("asterion") ? 50 : e.skill.equals("ultimo-juramento") ? 40 : 45)));
        t.add(Settings.text("equipamiento", "ArmorGuide.mensaje-053", "<dark_gray>Quitar una pieza termina el efecto."));
        return compactLines(t);
    }

}
