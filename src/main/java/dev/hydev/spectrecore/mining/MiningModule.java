package dev.hydev.spectrecore.mining;

import static dev.hydev.spectrecore.relicario.RelicarioModule.miningBonus;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MigrationRules;
import dev.hydev.spectrecore.migration.MmoBridge;
import net.kyori.adventure.bossbar.BossBar;
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
import dev.hydev.spectrecore.storage.AtomicFileStore;
import dev.hydev.spectrecore.storage.StateYaml;

public final class MiningModule implements Listener {
    private static String prefix() { return Settings.text("resonancia", "MiningModule.etiqueta-046", "§8[§d✦§8] §r"); }
    private static final class State { int charge; long activeUntil; Long legacyTreasureUntil; }
    private final JavaPlugin plugin;
    private final MmoBridge mmo = new MmoBridge();
    private final MiningVisuals visuals;
    private final PlacedOres placed;
    private final MithrilToolModule mithrilTools;
    private final MiningLuck luck;
    private final MiningGems gems;
    private Listener forgeUpgrades;
    private final MiningEncounters encounters;
    private final StoneDrill drill;
    private final MiningProgression progression;
    private final Map<UUID, State> states = new HashMap<>();
    private final dev.hydev.spectrecore.treasure.TreasureCooldowns treasureCooldown;
    private final Set<String> reported = new HashSet<>();
    private YamlConfiguration config;
    private BukkitTask timer;
    private final String geodeNamespace;
    private static final List<String> CORES = List.of("VETA", "MAGNETITA", "FLUORITA", "DIAMANTE_LOCO");
    private static final List<String> CORE_NAMES = List.of("Veta", "Magnetita", "Fluorita", "Diamante Loco");
    private boolean dirty;
    private boolean storageHealthy;
    private final MithrilRegionAccess geodeRegions;
    private int saveClock;
    private int barFrame;
    private final Map<UUID, BossBar> resonanceBars = new HashMap<>();
    private final MiningPermissionSync permissionSync;
    public MiningModule(JavaPlugin plugin, dev.hydev.spectrecore.treasure.TreasureCooldowns treasureCooldown) {
        this.plugin = plugin; geodeNamespace = new NamespacedKey(plugin, "geoda").getNamespace(); this.treasureCooldown = treasureCooldown;
        visuals = new MiningVisuals(plugin);
        geodeRegions = new MithrilRegionAccess(plugin);
        // Obsidian must be marked on placement too; no recycled blocks may summon Prospectors.
        placed = new PlacedOres(plugin, "placed_ores_v1",
                material -> MiningModule.tier(material) > 0 || material == Material.OBSIDIAN);
        luck = new MiningLuck(plugin);
        encounters = new MiningEncounters(plugin, luck);
        drill = new StoneDrill(plugin);
        progression = new MiningProgression(plugin);
        permissionSync = new MiningPermissionSync(plugin, progression);
        mithrilTools = new MithrilToolModule(plugin, placed);
        gems = new MiningGems(plugin,mmo,progression,mithrilTools);
    }
    public void enable() {
        reload(); loadData();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(placed, plugin); visuals.enable();
        mithrilTools.enable();
        encounters.enable();
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            forgeUpgrades = new MiningForgeUpgrades(plugin);
            Bukkit.getPluginManager().registerEvents(forgeUpgrades, plugin);
        }
        Bukkit.getPluginManager().registerEvents(gems,plugin);
        drill.enable();
        progression.enable();
        for (String name : List.of("resonancia", "debugresonancia", "nombreitem")) {
            var command = dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name);
            if (command != null) {
                command.setExecutor((sender, cmd, label, args) -> command(sender, cmd.getName(), args));
                command.setTabCompleter((sender, cmd, label, args) -> List.of());
            }
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean second = ++barFrame % 4 == 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (second) showGeodes(player);
                State state = storageHealthy ? states.get(player.getUniqueId()) : null;
                if (state != null && config.getBoolean("gemas.activadas", true)) expire(player, state);
                if (barFrame % 2 == 0) updateBar(player, state);
            }
            if (second && ++saveClock >= 10) { saveClock = 0; if (dirty) saveData(); }
        }, 5, 5);
    }

    private void updateBar(Player player, State state) {
        ItemStack held = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();
        var portable = mithrilTools.hud(player, held, now);
        if (portable != null) {
            var bar = bar(player);
            bar.progress(portable.progress());
            bar.color(portable.color());
            bar.name(portable.title());
            return;
        }
        if (!config.getBoolean("gemas.activadas", true) || !tool(held)) {
            hideBar(player);
            return;
        }
        var bar = bar(player);
        int charge = state == null ? 0 : state.charge;
        bar.progress(Math.clamp(charge / (float)maximum(), 0f, 1f));
        if (!storageHealthy) {
            bar.color(BossBar.Color.RED);
            bar.name(Settings.message("resonancia", "MiningModule.barra-no-disponible", "<red>Resonancia no disponible"));
        } else if (!progression.meets(player, "resonancia")) {
            bar.color(BossBar.Color.YELLOW);
            bar.name(Settings.message("resonancia", "MiningModule.barra-bloqueada", "<yellow>Resonancia · Desbloquea Minería {nivel}",
                    "nivel", progression.required("resonancia")));
        } else if (player.getGameMode() != GameMode.SURVIVAL) {
            bar.color(BossBar.Color.YELLOW);
            bar.name(Settings.message("resonancia", "MiningModule.barra-supervivencia", "<yellow>Resonancia · Solo en supervivencia"));
        } else if (state != null && state.activeUntil > now) {
            long remaining = state.activeUntil - now;
            bar.progress(Math.min(1f, remaining / (window() * 1000f)));
            bar.color(barFrame % 8 < 4 ? BossBar.Color.PINK : BossBar.Color.PURPLE);
            bar.name(Settings.message("resonancia", "MiningModule.barra-lista",
                    "<light_purple>✦ Agáchate y mina · {s} s", "s", MithrilRules.remainingSeconds(now, state.activeUntil)));
        } else {
            bar.color(BossBar.Color.PURPLE);
            bar.name(Settings.message("resonancia", "MiningModule.barra-carga",
                    "<light_purple>✦ Resonancia <white>{carga}/{max}", "carga", charge, "max", maximum()));
        }
    }

    private BossBar bar(Player player) {
        return resonanceBars.computeIfAbsent(player.getUniqueId(), id -> {
            BossBar created = BossBar.bossBar(Component.empty(), 0, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
    }

    private void hideBar(Player player) {
        BossBar bar = resonanceBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }
    public void disable() {
        if (timer != null) timer.cancel();
        Bukkit.getOnlinePlayers().forEach(this::hideBar);
        permissionSync.stop();
        progression.disable();
        drill.disable();
        encounters.disable();
        if (forgeUpgrades != null) HandlerList.unregisterAll(forgeUpgrades);
        HandlerList.unregisterAll(gems);
        gems.clear();
        mithrilTools.disable();
        HandlerList.unregisterAll(this); HandlerList.unregisterAll(placed); visuals.disable(); saveData();
        states.clear();
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).mining();
        if (mithrilTools != null) mithrilTools.reload();
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
        if (MithrilAutomationGuard.active()) return;
        Block block = event.getBlock();
        boolean relocated = placed.consume(block);
        int tier = tier(block.getType());
        Player player = event.getPlayer();
        if (relocated || (tier == 0 && block.getType() != Material.OBSIDIAN)
                || player.getGameMode() != GameMode.SURVIVAL) return;
        encounters.naturalOreBroken(player, block);
        if (tier == 0) return; // Obsidian triggers a Prospector, not ore resonance or Mitrilo.
        int veta = gems.mine(player,block,this::deliver);
        discoverGeode(player, block);
        mithrilTools.onManualOre(player, block);
        if (storageHealthy && progression.meets(player,"resonancia")
                && config.getBoolean("gemas.activadas", true)
                && tool(player.getInventory().getItemInMainHand())) resonance(player, block, tier);
        if (config.getBoolean("tesoros.activados", true)) treasure(player, block, tier, veta);
    }
    // Each discoverer's player data owns its markers. Absolute deadlines survive relogs.
    private NamespacedKey marker(Block block) {
        return new NamespacedKey(plugin, "geoda/" + block.getWorld().getUID() + "/" +
                block.getX() + "/" + block.getY() + "/" + block.getZ());
    }

    private void discoverGeode(Player player, Block ore) {
        switch (ore.getType()) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                    REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> { }
            default -> { return; }
        }
        double chance = config.getDouble("geodas.probabilidad-porcentaje", 0.5) / 100.0;
        if (!Double.isFinite(chance) || !progression.meets(player, "arcana")
                || ThreadLocalRandom.current().nextDouble() >= Math.clamp(chance * (1 + miningBonus(plugin, player, "geode_chance") / 100), 0, 1)) return;
        int duration = Math.clamp(config.getInt("geodas.duracion-minutos", 10), 1, 1440);
        if (mmo.build("MATERIAL", "LLAVE_RESONANTE", 1) == null) return;
        for (String core : CORES) {
            if (mmo.build("GEM_STONE", "GEMA_" + core + "_IV", 1) == null) return;
        }
        for (var face : List.of(org.bukkit.block.BlockFace.DOWN, org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST, org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.UP)) {
            Block anchor = ore.getRelative(face);
            if (!geodeRegions.canBuild(player, anchor)) continue;
            if (anchor.getType() != Material.STONE && anchor.getType() != Material.DEEPSLATE) continue;
            var key = marker(anchor);
            var data = player.getPersistentDataContainer();
            var type = org.bukkit.persistence.PersistentDataType.LONG_ARRAY;
            long now = System.currentTimeMillis();
            long[] previous = data.get(key, type);
            if (previous != null && previous.length == 2 && previous[0] > now) continue;
            data.set(key, type, new long[]{now + Duration.ofMinutes(duration).toMillis(), 0});
            player.playSound(anchor.getLocation(), Settings.sound("resonancia", "MiningModule.geoda-sonido", Sound.BLOCK_AMETHYST_BLOCK_CHIME), 1, 1);
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-descubierta", "<light_purple>Geoda descubierta <gray>· {x}, {y}, {z} · Reservada para ti durante {minutos} min.",
                    "x", anchor.getX(), "y", anchor.getY(), "z", anchor.getZ(), "minutos", duration));
            return;
        }
    }

    private void showGeodes(Player player) {
        var data = player.getPersistentDataContainer();
        var type = org.bukkit.persistence.PersistentDataType.LONG_ARRAY;
        long now = System.currentTimeMillis();
        for (NamespacedKey key : data.getKeys()) {
            if (!key.getNamespace().equals(geodeNamespace) || !key.getKey().startsWith("geoda/")) continue;
            long[] state = data.get(key, type);
            if (state == null || state.length != 2 || state[0] <= now) {
                data.remove(key);
                continue;
            }
            String[] parts = key.getKey().split("/");
            if (parts.length != 5 || !parts[1].equals(player.getWorld().getUID().toString())) continue;
            Location point;
            try { point = new Location(player.getWorld(), Integer.parseInt(parts[2]) + 0.5,
                    Integer.parseInt(parts[3]) + 0.5, Integer.parseInt(parts[4]) + 0.5); }
            catch (NumberFormatException malformed) { data.remove(key); continue; }
            if (point.distanceSquared(player.getLocation()) <= 32 * 32)
                player.spawnParticle(Particle.END_ROD, point, 8, 0.6, 0.6, 0.6, 0);
        }
    }

    @EventHandler
    public void openGeode(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.isCancelled()) return;
        var player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        var data = player.getPersistentDataContainer();
        var type = org.bukkit.persistence.PersistentDataType.LONG_ARRAY;
        var block = event.getClickedBlock();
        NamespacedKey key = block == null ? null : marker(block);
        long[] state = key == null ? null : data.get(key, type);
        if (state == null) {
            var heldKey = player.getInventory().getItemInMainHand();
            if (!"LLAVE_RESONANTE".equals(mmo.id(heldKey)) || !"MATERIAL".equals(mmo.type(heldKey))) return;
            // The private marker can still be pointed at after its backing rock is mined.
            var ray = new org.bukkit.util.BlockIterator(player, 5);
            while (ray.hasNext()) {
                var point = ray.next();
                key = marker(point);
                state = data.get(key, type);
                if (state != null) { block = point; break; }
                if (!point.isPassable()) break;
            }
        }
        if (state == null || state.length != 2) return;
        if (state[0] <= System.currentTimeMillis()) { data.remove(key); return; }
        if (state[1] < 0 || state[1] > CORES.size()) return;
        if (!progression.meets(player, "arcana")) {
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-requisito", "<red>Necesitas Resonancia Arcana para abrir geodas."));
            return;
        }
        if (block == null || !geodeRegions.canBuild(player, block)) {
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-protegida", "<red>No puedes abrir una geoda aquí."));
            return;
        }
        var held = player.getInventory().getItemInMainHand();
        if (!"LLAVE_RESONANTE".equals(mmo.id(held)) || !"MATERIAL".equals(mmo.type(held))) {
            player.sendActionBar(Settings.message("resonancia", "MiningModule.geoda-sellada", "<gray>Geoda sellada · Usa una Llave Resonante"));
            return;
        }
        event.setCancelled(true);
        int index = (int) state[1];
        if (player.isSneaking() || index == 0) {
            index = index % CORES.size() + 1;
            data.set(key, type, new long[]{state[0], index});
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-seleccion", "<light_purple>{gema} IV <gray>· Agáchate y pulsa para cambiar. Pulsa de pie para abrir.", "gema", CORE_NAMES.get(index - 1)));
            return;
        }
        String core = CORES.get(index - 1);
        int payment = -1;
        if (core.equals("DIAMANTE_LOCO")) {
            ItemStack[] inventory = player.getInventory().getStorageContents();
            for (int i = 0; i < inventory.length; i++) {
                ItemStack candidate = inventory[i];
                if (candidate != null && "GEM_STONE".equals(mmo.type(candidate)) &&
                        "GEMA_DIAMANTE_LOCO_III".equals(mmo.id(candidate))) { payment = i; break; }
            }
            if (payment < 0) { player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-diamante", "<yellow>Necesitas un Diamante Loco III.")); return; }
        }
        ItemStack reward = mmo.build("GEM_STONE", "GEMA_" + core + "_IV", 1);
        if (reward == null || reward.getType().isAir()) {
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-no-disponible", "<yellow>Este núcleo aún no está disponible.")); return;
        }
        // Plan the entire exchange in storage slots, then apply once. A full inventory
        // leaves the key, extra gem, and marker intact (no persistent world drop).
        var inventory = player.getInventory();
        ItemStack[] next = inventory.getStorageContents();
        for (int i = 0; i < next.length; i++) if (next[i] != null) next[i] = next[i].clone();
        int keySlot = inventory.getHeldItemSlot();
        if (next[keySlot] == null || next[keySlot].getAmount() < 1) return;
        next[keySlot] = minusOne(next[keySlot]);
        if (payment >= 0) next[payment] = minusOne(next[payment]);
        int remaining = reward.getAmount();
        int max = reward.getMaxStackSize();
        for (int i = 0; i < next.length && remaining > 0; i++) {
            if (next[i] == null || !next[i].isSimilar(reward)) continue;
            int amount = Math.min(remaining, Math.max(0, max - next[i].getAmount()));
            next[i].setAmount(next[i].getAmount() + amount); remaining -= amount;
        }
        for (int i = 0; i < next.length && remaining > 0; i++) {
            if (next[i] != null && !next[i].getType().isAir()) continue;
            int amount = Math.min(remaining, max);
            next[i] = reward.clone(); next[i].setAmount(amount); remaining -= amount;
        }
        if (remaining != 0) {
            player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-inventario", "<red>Libera espacio para la gema antes de abrir la geoda."));
            return;
        }
        inventory.setStorageContents(next);
        data.remove(key);
        player.sendMessage(Settings.message("resonancia", "MiningModule.geoda-abierta", "<green>✦ Geoda abierta: <white>{gema} IV", "gema", CORE_NAMES.get(index - 1)));
    }

    private static ItemStack minusOne(ItemStack stack) {
        if (stack.getAmount() <= 1) return null;
        stack.setAmount(stack.getAmount() - 1);
        return stack;
    }

    private String gemId(String quality) {
        String path = "gemas.ids." + quality;
        List<String> pool = config.getStringList(path);
        if (!pool.isEmpty()) return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        return config.getString(path, "");
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
            double roll = MiningLuckRules.qualityRoll(ThreadLocalRandom.current().nextDouble(100),
                    luck.stat(player),
                    config.getDouble("suerte.calidad-gemas.puntos-por-luck",0.05),
                    config.getDouble("suerte.calidad-gemas.maximo-puntos",5.0));
            String quality = dev.hydev.spectrecore.config.Gameplay.gemQuality(tier, roll);
            String id = gemId(quality);
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
        int extra = ThreadLocalRandom.current().nextDouble(100) < miningBonus(plugin, player, "resonance_extra") ? 1 : 0;
        state.charge = Math.min(maximum(), state.charge + extra + Settings.module("resonancia/config.yml").getInt("reglas.carga-por-tier.tier"+tier)); dirty = true;
        if (state.charge >= maximum()) {
            state.activeUntil = System.currentTimeMillis() + window() * 1000L;
            boolean nativeToast = ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts() != null
                    && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts().show(player, "resonancia");
            if (!nativeToast) player.playSound(player.getLocation(), Settings.sound("resonancia", "MiningModule.sound-008", Sound.BLOCK_AMETHYST_BLOCK_RESONATE),Settings.f("resonancia", "MiningModule.playsound-006-arg2", 1),Settings.f("resonancia", "MiningModule.playsound-007-arg3", 1.4f));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-007", "{prefix}§d§lRESONANCIA COMPLETA", "prefix", Settings.component(prefix())));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-008", "§7Agáchate y rompe un mineral en los próximos {window} segundos.", "window", window()));
            player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-009", "§7La calidad depende del mineral que elijas.")); if(config.getBoolean("visuales-resonancia.activados",true)) visuals.charged(player);
        }
    }
    private void treasure(Player player, Block block, int tier, int veta) {
        if (!treasureCooldown.ready(player.getUniqueId())) return;
        // Mitrilo is exclusive with regular mining treasures for this block.
        // No new world blocks, background scanning or extra cooldown ledgers.
        if (mithril(player, block, tier, veta)) return;
        if (!progression.meets(player,"paladio")) return;
        double chance = MiningLuckRules.chance(
                config.getDouble("probabilidades.mineria.tier" + tier, 0), luck.stat(player),
                config.getDouble("suerte.hallazgos.incremento-relativo-por-punto", 1.5),
                config.getDouble("suerte.hallazgos.multiplicador-maximo", 2.0));
        if (ThreadLocalRandom.current().nextDouble(100) >= chance) return;
        String type = config.getString("tesoros.tipo", "MATERIAL"), id = config.getString("tesoros.id", "PALADIO");
        int min = Math.max(1, Math.min(64, config.getInt("tesoros.cantidad-minima", 2)));
        int max = Math.max(min, Math.min(64, config.getInt("tesoros.cantidad-maxima", 8)));
        int quantity = MiningLuckRules.quantity(ThreadLocalRandom.current().nextInt(min, max + 1),
                64, luck.stat(player),
                config.getDouble("suerte.cantidad-paladio.por-punto", 1.0),
                config.getDouble("suerte.cantidad-paladio.maximo-porcentaje", 35.0),
                ThreadLocalRandom.current().nextDouble(100));
        ItemStack reward = mmo.build(type, id, quantity);
        if (reward == null) { missing(player, type, id); return; }
        deliver(player, reward);
        treasureCooldown.awarded(player.getUniqueId());
        dirty = true;
        if(config.getBoolean("visuales-tesoros.activados",true)) visuals.treasure(block.getLocation().add(.5, .5, .5), block.getType(), tier);
        player.playSound(player.getLocation(), Settings.sound("tesoros", "MiningModule.sound-013", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("tesoros", "MiningModule.playsound-008-arg2", 1),Settings.f("tesoros", "MiningModule.playsound-009-arg3", 1.5f));
        player.showTitle(Title.title(Settings.component(Settings.text("tesoros", "MiningModule.mensaje-014", "¡TESORO ENCONTRADO!"), Settings.color("tesoros", "MiningModule.color-014", NamedTextColor.GOLD)), Settings.component(Settings.text("tesoros", "MiningModule.mensaje-015", "✦ {reward-getamount} minerales raros", "reward-getamount", reward.getAmount()), Settings.color("tesoros", "MiningModule.color-015", NamedTextColor.YELLOW)), Title.Times.times(Duration.ofMillis(Settings.i("tesoros", "MiningModule.ofmillis-010-arg0", 150)), Duration.ofSeconds(Settings.i("tesoros", "MiningModule.ofseconds-011-arg0", 2)), Duration.ofMillis(Settings.i("tesoros", "MiningModule.ofmillis-012-arg0", 350)))));
        player.sendMessage(Settings.message("tesoros", "MiningModule.mensaje-016", "§f↹ §e¡Obtuviste un mineral raro!"));
    }
    private boolean mithril(Player player, Block block, int tier, int veta) {
        if (tier < 2 || !progression.meets(player,"mitrilo")) return false;
        boolean experienced = player.hasPermission("trabajo.minero.experimentado")
                || player.hasPermission("trabajo.minero.profesional");
        if (!experienced) return false;
        int required = config.getInt("mitrilo.rango-minimo", 2);
        int rank = player.hasPermission("trabajo.minero.profesional") ? 3 : 2;
        if (required > rank) return false;
        double chance = config.getDouble("mitrilo.probabilidades.tier" + tier, 0);
        // Exact MMOItems TOOL IDs only: ordinary renamed pickaxes receive no bonus.
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        String expectedType = config.getString("mitrilo.herramientas.tipo", "TOOL");
        if (expectedType.equalsIgnoreCase(mmo.type(pickaxe))) {
            String id = mmo.id(pickaxe);
            Object raw = config.get("mitrilo.herramientas.bonificaciones." + id);
            if (raw instanceof Number factor && Double.isFinite(factor.doubleValue())
                    && factor.doubleValue() >= 1 && factor.doubleValue() <= 3)
                chance *= factor.doubleValue();
        }
        chance *= 1 + veta * .10;
        chance = MiningLuckRules.chance(Math.min(100.0, chance), luck.stat(player),
                config.getDouble("suerte.hallazgos.incremento-relativo-por-punto", 1.5),
                config.getDouble("suerte.hallazgos.multiplicador-maximo", 2.0));
        if (chance <= 0 || ThreadLocalRandom.current().nextDouble(100) >= chance) return false;
        String type = config.getString("mitrilo.tipo", "MATERIAL");
        String id = config.getString("mitrilo.id", "MITRILO");
        int min = config.getInt("mitrilo.cantidad-minima", 1);
        int max = config.getInt("mitrilo.cantidad-maxima", 2);
        if (min < 1 || min > max || max > 64) return false;
        int quantity = MiningLuckRules.quantity(ThreadLocalRandom.current().nextInt(min, max + 1),
                64, luck.stat(player),
                config.getDouble("suerte.cantidad-mitrilo.por-punto", 0.8),
                config.getDouble("suerte.cantidad-mitrilo.maximo-porcentaje", 25.0),
                ThreadLocalRandom.current().nextDouble(100));
        ItemStack reward = mmo.build(type, id, quantity);
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
            ItemStack gem = mmo.build("GEM_STONE", gemId(quality), 1);
            visuals.gem(at, gem == null ? new ItemStack(Material.AMETHYST_SHARD) : gem, quality);
        } else { player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-026", "§cUsa tesoro o gema.")); return true; }
        player.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-027", "§7Vista previa visual: no entrega objetos ni cambia la resonancia."));
        return true;
    }
    private boolean command(CommandSender sender, String name, String[] args) {
        if (name.equals("debugresonancia") && args.length == 1 && args[0].equals("sincronizar")) {
            if (admin(sender)) permissionSync.start(sender);
            else sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-019", "§cNo tienes permiso."));
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-028", "Usa este comando dentro del juego.")); return true; }
        if (!name.equals("resonancia") && !admin(sender)) { sender.sendMessage(Settings.message("resonancia", "MiningModule.mensaje-029", "§cNo tienes permiso.")); return true; }
        if (name.equals("resonancia")) {
            var portable = mithrilTools.hud(player, player.getInventory().getItemInMainHand(), System.currentTimeMillis());
            if (portable != null) { player.sendMessage(portable.title()); return true; }
        }
        if (!storageHealthy) {
            sender.sendMessage(Settings.message("resonancia", "MiningModule.datos-no-disponibles", "<red>La resonancia no está disponible. Avisa a administración."));
            return true;
        }
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
    @EventHandler public void quit(PlayerQuitEvent event) { gems.quit(event.getPlayer().getUniqueId()); hideBar(event.getPlayer()); if (dirty) saveData(); }
    private void loadData() {
        storageHealthy = false;
        File file = new File(plugin.getDataFolder(), "mining-data.yml");
        Map<UUID, State> loaded = new HashMap<>();
        try {
            var section = StateYaml.read(file, "players");
            for (String key : section.getKeys(false)) {
                UUID id;
                try { id = StateYaml.uuid(key); }
                catch (IOException error) {
                    throw new IOException(Settings.plain("resonancia", "MiningModule.mensaje-044", "UUID inválido en mining-data.yml: {key}", "key", key), error);
                }
                var row = section.getConfigurationSection(key);
                if (row == null) throw new IOException("Invalid mining record: " + key);
                State state = new State();
                state.charge = (int)Math.min(maximum(), StateYaml.nonNegative(row, "charge"));
                state.activeUntil = StateYaml.nonNegative(row, "active-until");
                if (row.contains("treasure-until")) state.legacyTreasureUntil = StateYaml.nonNegative(row, "treasure-until");
                if (state.activeUntil > 0 && state.activeUntil <= System.currentTimeMillis()) {
                    state.charge = 0; state.activeUntil = 0;
                }
                loaded.put(id, state);
            }
            states.clear();
            states.putAll(loaded);
            storageHealthy = true;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException | RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Mining: mining-data.yml inválido; resonancia bloqueada, archivo conservado.", error);
        }
    }
    private void saveData() {
        if (!storageHealthy || !dirty) return;
        var data = new YamlConfiguration();
        data.createSection("players");
        for (var entry : states.entrySet()) {
            State state = entry.getValue(); String root = "players." + entry.getKey();
            data.set(root + ".charge", state.charge); data.set(root + ".active-until", state.activeUntil);
            if (state.legacyTreasureUntil != null && !treasureCooldown.healthy())
                data.set(root + ".treasure-until", state.legacyTreasureUntil);
        }
        try {
            AtomicFileStore.writeUtf8(new File(plugin.getDataFolder(), "mining-data.yml").toPath(), data.saveToString());
            dirty = false;
        } catch (IOException error) {
            storageHealthy = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    Settings.plain("resonancia", "MiningModule.mensaje-045", "No se pudo guardar la minería: {error-getmessage}", "error-getmessage", error.getMessage())
                            + "; resonancia bloqueada hasta recuperación.", error);
        }
    }
}
