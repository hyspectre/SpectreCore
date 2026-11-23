package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MigrationRules;
import dev.hydev.spectrecore.migration.MmoBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MiningModule implements Listener {
    private static String prefix() { return Settings.text("resonancia", "MiningModule.etiqueta-046", "§8[§d✦§8] §r"); }
    private static final class State { int charge; long activeUntil; }
    private final JavaPlugin plugin;
    private final MmoBridge mmo = new MmoBridge();
    private final MiningVisuals visuals;
    private final PlacedOres placed;
    private final Map<UUID, State> states = new HashMap<>();
    private final dev.hydev.spectrecore.treasure.TreasureCooldowns treasureCooldown;
    private final Set<String> reported = new HashSet<>();
    private YamlConfiguration config;
    private BukkitTask timer;
    private boolean dirty;
    private int saveClock;
    public MiningModule(JavaPlugin plugin, dev.hydev.spectrecore.treasure.TreasureCooldowns treasureCooldown) {
        this.plugin = plugin; this.treasureCooldown = treasureCooldown;
        visuals = new MiningVisuals(plugin); placed = new PlacedOres(plugin);
    }
    public void enable() {
        reload(); loadData();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(placed, plugin); visuals.enable();
        for (String name : List.of("resonancia", "debugresonancia", "nombreitem")) {
            var command = dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name);
            if (command != null) {
                command.setExecutor((sender, cmd, label, args) -> command(sender, cmd.getName()));
                command.setTabCompleter((sender, cmd, label, args) -> List.of());
            }
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!config.getBoolean("gemas.activadas", true)) break;
                State state = states.get(player.getUniqueId());
                if (state != null && expire(player, state)) continue;
                if (state != null && state.activeUntil > 0 && tool(player.getInventory().getItemInMainHand())) {
                    long left = Math.max(0, (state.activeUntil - System.currentTimeMillis() + 999) / 1000);
                    player.sendActionBar(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-001", "✦ Resonancia lista · Shift + minar · {left} s", "left", left), Settings.color("resonancia", "MiningModule.color-001", NamedTextColor.LIGHT_PURPLE)));
                }
            }
            if (++saveClock >= 10) { saveClock = 0; if (dirty) saveData(); }
        }, 20, 20);
    }
    public void disable() {
        if (timer != null) timer.cancel();
        HandlerList.unregisterAll(this); HandlerList.unregisterAll(placed); visuals.disable(); saveData();
        states.clear();
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).mining();
        visuals.configure(config.getBoolean("visuales.activados", true), config.getInt("visuales.max-animaciones", 32));
        reported.clear();
    }
    private State state(Player p) { return states.computeIfAbsent(p.getUniqueId(), id -> new State()); }
    private int maximum() { return Math.max(1, config.getInt("gemas.carga-maxima", 50)); }
    private int window() { return Math.max(1, config.getInt("gemas.ventana-segundos", 30)); }
    private String clean(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) return "";
        String plain = PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().displayName());
        return ChatColor.stripColor(plain);
    }
    private boolean tool(ItemStack stack) {
        if (stack == null || !stack.getType().name().endsWith("_PICKAXE")) return false;
        if (config.getString("gemas.identificar-por", "nombre").equalsIgnoreCase("id")) {
            String id = config.getString("gemas.pico", "");
            return !id.isBlank() && id.equals(mmo.id(stack)) && config.getString("gemas.tipo-pico", "TOOL").equals(mmo.type(stack));
        }
        return clean(stack).contains(config.getString("gemas.nombre", "Pico Perdido"));
    }
    public static int tier(Material material) { return dev.hydev.spectrecore.config.Gameplay.tier("mineria",material.name()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean relocated = placed.consume(block);
        int tier = tier(block.getType());
        Player player = event.getPlayer();
        if (relocated || tier == 0 || player.getGameMode() != GameMode.SURVIVAL) return;
        if (config.getBoolean("gemas.activadas", true) && tool(player.getInventory().getItemInMainHand())) resonance(player, block, tier);
        if (config.getBoolean("tesoros.activados", true)) treasure(player, block, tier);
    }
    private boolean expire(Player player, State state) {
        if (MigrationRules.expired(state.activeUntil, System.currentTimeMillis())) {
            state.activeUntil = 0; state.charge = 0; dirty = true;
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-002", "{prefix}§7La resonancia se ha disipado.", "prefix", Settings.component(prefix()))); return true;
        }
        return false;
    }
    private void resonance(Player player, Block block, int tier) {
        State state = state(player);
        if (expire(player, state)) return;
        if (state.activeUntil > 0) {
            if (!player.isSneaking()) {
                player.sendActionBar(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-003", "✦ Resonancia completa · Agáchate y mina"), Settings.color("resonancia", "MiningModule.color-002", NamedTextColor.LIGHT_PURPLE))); return;
            }
            String quality = dev.hydev.spectrecore.config.Gameplay.gemQuality(tier, ThreadLocalRandom.current().nextDouble(100));
            String id = config.getString("gemas.ids." + quality, "");
            ItemStack reward = mmo.build("GEM_STONE", id, 1);
            if (reward == null) { missing(player, "GEM_STONE", id); return; }
            deliver(player, reward); // Resolve before consuming resonance; no delayed award can be lost on quit.
            state.activeUntil = 0; state.charge = 0; dirty = true;
            String label = quality.equals("superior") ? Settings.text("resonancia", "MiningModule.texto-001", "Superior") : quality.equals("intermedia") ? Settings.text("resonancia", "MiningModule.texto-002", "Intermedia") : Settings.text("resonancia", "MiningModule.etiqueta-047", "Básica");
            NamedTextColor color = quality.equals("superior") ? Settings.color("resonancia", "MiningModule.color-003", NamedTextColor.DARK_PURPLE) : quality.equals("intermedia") ? Settings.color("resonancia", "MiningModule.color-004", NamedTextColor.BLUE) : Settings.color("resonancia", "MiningModule.color-005", NamedTextColor.GREEN);
            boolean nativeToast = ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts() != null
                    && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts().show(player, "gema-" + quality);
            if (!nativeToast) player.showTitle(Title.title(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-004", "✦ GEMA EXTRAÍDA"), Settings.color("resonancia", "MiningModule.color-006", NamedTextColor.LIGHT_PURPLE)), Settings.component(Settings.text("resonancia", "MiningModule.mensaje-005", "Calidad: {label}", "label", label), color), Title.Times.times(Duration.ofMillis(Settings.i("resonancia", "MiningModule.ofmillis-001-arg0", 150)), Duration.ofSeconds(Settings.i("resonancia", "MiningModule.ofseconds-002-arg0", 2)), Duration.ofMillis(Settings.i("resonancia", "MiningModule.ofmillis-003-arg0", 350)))));
            if (!nativeToast) player.playSound(player.getLocation(), Settings.sound("resonancia", "MiningModule.sound-007", Sound.BLOCK_AMETHYST_BLOCK_CHIME),Settings.f("resonancia", "MiningModule.playsound-004-arg2", 1),Settings.f("resonancia", "MiningModule.playsound-005-arg3", 1.2f));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-006", "{prefix}§fHas extraído una gema de calidad {label}.", "prefix", Settings.component(prefix()), "label", label));
            if (config.getBoolean("visuales-resonancia.activados",true)) visuals.gem(block.getLocation().add(.5, .5, .5), reward, quality);
            return;
        }
        state.charge = Math.min(maximum(), state.charge + Settings.module("resonancia/config.yml").getInt("reglas.carga-por-tier.tier"+tier)); dirty = true;
        if (state.charge >= maximum()) {
            state.activeUntil = System.currentTimeMillis() + window() * 1000L;
            boolean nativeToast = ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts() != null
                    && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts().show(player, "resonancia");
            if (!nativeToast) player.playSound(player.getLocation(), Settings.sound("resonancia", "MiningModule.sound-008", Sound.BLOCK_AMETHYST_BLOCK_RESONATE),Settings.f("resonancia", "MiningModule.playsound-006-arg2", 1),Settings.f("resonancia", "MiningModule.playsound-007-arg3", 1.4f));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-007", "{prefix}§d§lRESONANCIA COMPLETA", "prefix", Settings.component(prefix())));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-008", "§7Agáchate y rompe un mineral en los próximos {window} segundos.", "window", window()));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-009", "§7La calidad depende del mineral que elijas.")); if(config.getBoolean("visuales-resonancia.activados",true)) visuals.charged(player);
        } else {
            int filled = (int) Math.floor(12.0 * state.charge / maximum());
            player.sendActionBar(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-010", "✦ "), Settings.color("resonancia", "MiningModule.color-009", NamedTextColor.LIGHT_PURPLE))
                .append(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-011", "{repeat-filled}", "repeat-filled", "▰".repeat(filled)), Settings.color("resonancia", "MiningModule.color-010", NamedTextColor.LIGHT_PURPLE)))
                .append(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-012", "{repeat-12-filled}", "repeat-12-filled", "▱".repeat(12 - filled)), Settings.color("resonancia", "MiningModule.color-011", NamedTextColor.DARK_GRAY)))
                .append(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-013", " {state-charge}/{maximum}", "state-charge", state.charge, "maximum", maximum()), Settings.color("resonancia", "MiningModule.color-012", NamedTextColor.WHITE))));
        }
    }
    private void treasure(Player player, Block block, int tier) {
        if (!treasureCooldown.ready(player.getUniqueId())) return;
        // Mitrilo is exclusive with regular mining treasures for this block.
        // No new world blocks, background scanning or extra cooldown ledgers.
        if (mithril(player, block, tier)) return;
        if (!player.hasPermission("tesoros.mineria.tier" + tier)) return;
        double chance = config.getDouble("probabilidades.mineria.tier" + tier, 0);
        if (!Double.isFinite(chance) || ThreadLocalRandom.current().nextDouble(100) >= Math.max(0, Math.min(100, chance))) return;
        String type = config.getString("tesoros.tipo", "MATERIAL"), id = config.getString("tesoros.id", "PALADIO");
        int min = Math.max(1, Math.min(64, config.getInt("tesoros.cantidad-minima", 2)));
        int max = Math.max(min, Math.min(64, config.getInt("tesoros.cantidad-maxima", 8)));
        ItemStack reward = mmo.build(type, id, ThreadLocalRandom.current().nextInt(min, max + 1));
        if (reward == null) { missing(player, type, id); return; }
        deliver(player, reward);
        treasureCooldown.awarded(player.getUniqueId());
        dirty = true;
        if(config.getBoolean("visuales-tesoros.activados",true)) visuals.treasure(block.getLocation().add(.5, .5, .5), block.getType(), tier);
        player.playSound(player.getLocation(), Settings.sound("tesoros", "MiningModule.sound-013", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("tesoros", "MiningModule.playsound-008-arg2", 1),Settings.f("tesoros", "MiningModule.playsound-009-arg3", 1.5f));
        player.showTitle(Title.title(Settings.component(Settings.text("tesoros", "MiningModule.mensaje-014", "¡TESORO ENCONTRADO!"), Settings.color("tesoros", "MiningModule.color-014", NamedTextColor.GOLD)), Settings.component(Settings.text("tesoros", "MiningModule.mensaje-015", "✦ {reward-getamount} minerales raros", "reward-getamount", reward.getAmount()), Settings.color("tesoros", "MiningModule.color-015", NamedTextColor.YELLOW)), Title.Times.times(Duration.ofMillis(Settings.i("tesoros", "MiningModule.ofmillis-010-arg0", 150)), Duration.ofSeconds(Settings.i("tesoros", "MiningModule.ofseconds-011-arg0", 2)), Duration.ofMillis(Settings.i("tesoros", "MiningModule.ofmillis-012-arg0", 350)))));
        player.sendMessage(Settings.message("tesoros", "MiningModule.mensaje-016", "§f↹ §e¡Obtuviste un mineral raro!"));
    }
    private boolean mithril(Player player, Block block, int tier) {
        if (tier < 2) return false;
        boolean experienced = player.hasPermission("trabajo.minero.experimentado")
                || player.hasPermission("trabajo.minero.profesional");
        if (!experienced) return false;
        int required = config.getInt("mitrilo.rango-minimo", 2);
        int rank = player.hasPermission("trabajo.minero.profesional") ? 3 : 2;
        if (required > rank) return false;
        double chance = config.getDouble("mitrilo.probabilidades.tier" + tier, 0);
        if (!Double.isFinite(chance) || chance <= 0 || chance > 100
                || ThreadLocalRandom.current().nextDouble(100) >= chance) return false;
        String type = config.getString("mitrilo.tipo", "MATERIAL");
        String id = config.getString("mitrilo.id", "MITRILO");
        int min = config.getInt("mitrilo.cantidad-minima", 1);
        int max = config.getInt("mitrilo.cantidad-maxima", 2);
        if (min < 1 || min > max || max > 64) return false;
        ItemStack reward = mmo.build(type, id, ThreadLocalRandom.current().nextInt(min, max + 1));
        if (reward == null) { missing(player, type, id); return false; }
        deliver(player, reward);
        treasureCooldown.awarded(player.getUniqueId());
        dirty = true;
        if (config.getBoolean("visuales-tesoros.activados", true))
            visuals.treasure(block.getLocation().add(.5, .5, .5), block.getType(), tier);
        player.sendMessage(Settings.message("tesoros", "MiningModule.mitrilo",
                "<aqua>✦ Encontraste <white>{cantidad} <aqua>Mitrilo.",
                "cantidad", reward.getAmount()));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.35f);
        return true;
    }

    private void missing(Player player, String type, String id) {
        player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-017", "{prefix}§cLa recompensa no está disponible. Avisa a administración.", "prefix", Settings.component(prefix())));
        if (reported.add(type + ":" + id)) plugin.getLogger().warning(Settings.plain("resonancia", "MiningModule.mensaje-018", "No se pudo generar el hallazgo minero ({type}:{id}).", "type", type, "id", id));
    }
    private void deliver(Player player, ItemStack reward) {
        player.getInventory().addItem(reward.clone()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left, drop -> {
            drop.setOwner(player.getUniqueId()); drop.setThrower(player.getUniqueId()); drop.setPickupDelay(0);
        }));
    }
    private boolean admin(CommandSender sender) { return sender.hasPermission("spectrecore.admin") || sender.hasPermission("hydev.admin"); }
    public boolean preview(CommandSender sender, String[] args) {
        if (!admin(sender)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-019", "§cNo tienes permiso.")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-020", "Usa la demostración dentro del juego.")); return true; }
        if (args.length < 3) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-021", "/spectrecore tesoros mineria <1|2|3> o /spectrecore tesoros gema <basica|intermedia|superior>")); return true; }
        var block = player.getTargetBlockExact(6);
        Location at = block == null ? player.getLocation().add(player.getLocation().getDirection().multiply(2)) : block.getLocation().add(.5, .5, .5);
        if (args[1].equalsIgnoreCase("tesoro")) {
            if(!config.getBoolean("tesoros.activados",true)){sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-022", "Tesoros mineros desactivados."));return true;}
            int tier;
            try { tier = Integer.parseInt(args[2]); } catch (NumberFormatException error) { tier = 0; }
            if (tier < 1 || tier > 3) { player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-023", "§cNivel válido: 1, 2 o 3.")); return true; }
            visuals.treasure(at, tier == 1 ? Material.IRON_ORE : tier == 2 ? Material.REDSTONE_ORE : Material.DIAMOND_ORE, tier);
        } else if (args[1].equalsIgnoreCase("gema")) {
            if(!config.getBoolean("gemas.activadas",true)){sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-024", "Resonancia desactivada."));return true;}
            String quality = args[2].toLowerCase(Locale.ROOT);
            if (!Set.of("basica", "intermedia", "superior").contains(quality)) { player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-025", "§cCalidad válida: basica, intermedia o superior.")); return true; }
            ItemStack gem = mmo.build("GEM_STONE", config.getString("gemas.ids." + quality, ""), 1);
            visuals.gem(at, gem == null ? new ItemStack(Material.AMETHYST_SHARD) : gem, quality);
        } else { player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-026", "§cUsa tesoro o gema.")); return true; }
        player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-027", "§7Vista previa visual: no entrega objetos ni cambia la resonancia."));
        return true;
    }
    private boolean command(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-028", "Usa este comando dentro del juego.")); return true; }
        if (!name.equals("resonancia") && !admin(sender)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-029", "§cNo tienes permiso.")); return true; }
        State state = state(player); expire(player, state);
        if (name.equals("resonancia")) {
            if(!config.getBoolean("gemas.activadas",true)){sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-030", "Resonancia desactivada."));return true;}
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-031", "{prefix}§fPico Perdido: §d{state-charge}§7/§d{maximum}", "prefix", Settings.component(prefix()), "state-charge", state.charge, "maximum", maximum()));
            if (state.activeUntil > 0) player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-032","§7Lista para extraer: {valor1} s.","valor1", Math.max(0, (state.activeUntil - System.currentTimeMillis() + 999) / 1000)));
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand(); String plain = clean(held);
        if (name.equals("nombreitem")) {
            if (plain.isEmpty()) { player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-033", "§cDebes sostener un objeto con nombre personalizado.")); return true; }
            player.sendMessage(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-034", "Nombre original: "), Settings.color("resonancia", "MiningModule.color-016", NamedTextColor.GRAY)).append(held.getItemMeta().displayName()));
            player.sendMessage(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-035", "Nombre sin colores: [{plain}]", "plain", plain), Settings.color("resonancia", "MiningModule.color-017", NamedTextColor.WHITE)));
            player.sendMessage(Settings.component(Settings.text("resonancia", "MiningModule.mensaje-036", "[COPIAR NOMBRE]"), Settings.color("resonancia", "MiningModule.color-018", NamedTextColor.GREEN)).clickEvent(ClickEvent.copyToClipboard(plain)));
            plugin.getLogger().info(Settings.plain("resonancia", "MiningModule.mensaje-037","Nombre del item: [{valor1}]","valor1", plain.replace('\n', ' ').replace('\r', ' ')));
        } else {
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-038", "§dHYDEV · DIAGNÓSTICO MINERO"));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-039", "§7Herramienta: §f{held-gettype} §7| Nombre: §f{plain}", "held-gettype", held.getType(), "plain", plain));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-040", "<gray>Herramienta inspeccionada."));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-041", "<gray>Condiciones de extracción verificadas."));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-042", "§7Herramienta válida: §f{tool-held} §7| Carga: §f{state-charge}/{maximum}", "tool-held", tool(held), "state-charge", state.charge, "maximum", maximum()));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-043","§7Probabilidades T1/T2/T3: §f{valor1}/{valor2}/{valor3}","valor1", config.getDouble("probabilidades.mineria.tier1", 0),"valor2", config.getDouble("probabilidades.mineria.tier2", 0),"valor3", config.getDouble("probabilidades.mineria.tier3", 0)));
        }
        return true;
    }
    @EventHandler public void quit(PlayerQuitEvent event) { if (dirty) saveData(); }
    private void loadData() {
        var data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "mining-data.yml"));
        var section = data.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) try {
            UUID id = UUID.fromString(key); State state = new State();
            state.charge = Math.max(0, Math.min(maximum(), section.getInt(key + ".charge")));
            state.activeUntil = section.getLong(key + ".active-until");
            if (state.activeUntil > 0 && state.activeUntil <= System.currentTimeMillis()) { state.charge = 0; state.activeUntil = 0; }
            states.put(id, state);
        } catch (IllegalArgumentException invalid) { plugin.getLogger().warning(Settings.plain("resonancia", "MiningModule.mensaje-044", "UUID inválido en mining-data.yml: {key}", "key", key)); }
    }
    private void saveData() {
        var data = new YamlConfiguration();
        Set<UUID> all = new HashSet<>(states.keySet());
        for (UUID id : all) {
            State state = states.getOrDefault(id, new State()); String root = "players." + id;
            data.set(root + ".charge", state.charge); data.set(root + ".active-until", state.activeUntil);
        }
        try {
            var target = new File(plugin.getDataFolder(), "mining-data.yml").toPath();
            var temporary = new File(plugin.getDataFolder(), "mining-data.yml.tmp").toPath();
            java.nio.file.Files.writeString(temporary, data.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try { java.nio.file.Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { java.nio.file.Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        }
        catch (IOException error) { plugin.getLogger().severe(Settings.plain("resonancia", "MiningModule.mensaje-045", "No se pudo guardar la minería: {error-getmessage}", "error-getmessage", error.getMessage())); }
    }
}
