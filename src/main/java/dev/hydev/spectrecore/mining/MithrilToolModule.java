package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** All tools use physical MMOItems IDs and persistent item data, not lore or slots. */
public final class MithrilToolModule implements Listener {
    private final JavaPlugin plugin;
    private final PlacedOres placed;
    private final MithrilDeviceData data;
    private final MithrilRegionAccess regions;
    private final MithrilProspector prospector;
    private final MithrilLantern lantern;
    private final MithrilDrill drill;
    private final AmethystResonanceField amethystField;
    private YamlConfiguration settings;
    private final Map<UUID, BukkitTask> signals = new HashMap<>();
    private boolean active;

    public MithrilToolModule(JavaPlugin plugin, PlacedOres placed) {
        this.plugin = plugin;
        this.placed = placed;
        data = new MithrilDeviceData(plugin);
        regions = new MithrilRegionAccess(plugin);
        prospector = new MithrilProspector(plugin, placed, regions);
        lantern = new MithrilLantern(plugin, data);
        drill = new MithrilDrill(plugin, data, regions);
        amethystField = new AmethystResonanceField(plugin, data, regions);
    }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        lantern.enable();
        amethystField.enable();
        active = true;
    }
    public void reload() {
        settings = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "tesoros/mineria.yml");
        amethystField.configure(settings);
    }
    public void disable() {
        prospector.disable();
        drill.disable();
        lantern.disable();
        amethystField.disable();
        signals.values().forEach(BukkitTask::cancel);
        signals.clear();
        if (active) HandlerList.unregisterAll(this);
        active = false;
    }

    private int capacity() { return settings.getInt("herramientas-mitrilo.resonancia.maxima", 100); }
    private int cost(String path) { return settings.getInt("herramientas-mitrilo." + path, 0); }
    private void feedback(Player player, String id, String fallback, Object... values) {
        // Explicit literals keep every user-visible default in the audited message catalog.
        player.sendMessage(switch (id) {
            case "carga-completa" -> Settings.message("tesoros", "MithrilTools.carga-completa", "<gray>La herramienta ya está completamente cargada.", values);
            case "cargada" -> Settings.message("tesoros", "MithrilTools.cargada", "<aqua>✦ Resonancia restaurada: <white>{carga}/100", values);
            case "sin-carga" -> Settings.message("tesoros", "MithrilTools.sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.", values);
            case "sin-veta" -> Settings.message("tesoros", "MithrilTools.sin-veta", "<gray>Mira una veta natural que puedas extraer.", values);
            case "veta" -> Settings.message("tesoros", "MithrilTools.veta", "<aqua>✦ Veta marcada. Extrae para recoger hasta tres bloques adicionales.", values);
            case "zona" -> Settings.message("tesoros", "MithrilTools.zona", "<red>La herramienta no puede utilizarse en esta zona.", values);
            case "sin-senal" -> Settings.message("tesoros", "MithrilTools.sin-senal", "<gray>El pulso no detectó minerales accesibles.", values);
            case "senal" -> Settings.message("tesoros", "MithrilTools.senal", "<aqua>✦ Señal {intensidad}<gray> · {direccion} · Profundidad {profundidad}", values);
            case "luz" -> Settings.message("tesoros", "MithrilTools.luz", "<aqua>✦ Farol encendido.", values);
            case "luz-apagada" -> Settings.message("tesoros", "MithrilTools.luz-apagada", "<gray>Farol apagado.", values);
            case "anclado" -> Settings.message("tesoros", "MithrilTools.anclado", "<aqua>✦ El Farol recuerda tu ubicación.", values);
            case "ancla-limpiada" -> Settings.message("tesoros", "MithrilTools.ancla-limpiada", "<gray>Se borró el sendero del Farol.", values);
            case "ruta" -> Settings.message("tesoros", "MithrilTools.ruta", "<aqua>✦ Sendero de retorno visible durante 20 segundos.", values);
            case "sin-tunel" -> Settings.message("tesoros", "MithrilTools.sin-tunel", "<gray>La Barrena necesita roca común y un camino permitido.", values);
            case "tunel" -> Settings.message("tesoros", "MithrilTools.tunel", "<aqua>✦ Barrena activada. No extrae minerales ni concede experiencia adicional.", values);
            case "sin-cristales" -> Settings.message("tesoros", "MithrilTools.sin-cristales", "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.", values);
            case "campo-ametista" -> Settings.message("tesoros", "MithrilTools.campo-ametista", "<light_purple>✦ Resonancia amplificada: {cantidad} cristales. ¡Rómpelos con tu pico!", values);
            default -> Settings.component(fallback);
        });
    }

    /** Called exclusively from a genuine, uncancelled player BlockBreakEvent. */
    public void onManualOre(Player player, Block ore) {
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        // Prevent charging devices by punching ores with an unsuitable tool.
        if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) return;
        int amount = cost("resonancia.por-mineral-natural");
        if (amount <= 0) return;
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            ItemStack stack = data.held(player, hand);
            if (data.id(stack).isEmpty()) continue;
            data.update(player, hand, data.changeCharge(stack, amount, capacity()));
        }
        ItemStack current = player.getInventory().getItemInMainHand();
        String id = data.id(current);
        if (MithrilDeviceData.AMETHYST.equals(id)) {
            player.sendActionBar(Settings.message("tesoros", "MithrilTools.amatista-carga",
                    "<light_purple>✦ Resonancia de amatista: <white>{actual}/{maxima}",
                    "actual", data.energy(current), "maxima", capacity()));
            return; // Amethyst rewards belong exclusively to enchanted nodes.
        }
        if (!MithrilDeviceData.PICK.equals(id) && !MithrilDeviceData.TEMPERED.equals(id)) return;
        if (data.activeUntil(current) <= System.currentTimeMillis()) return;
        int perBlock = cost("pico.coste-bloque-adicional");
        if (perBlock <= 0) return;
        List<Block> vein = prospector.connected(player, ore,
                cost("pico.radio"), cost("pico.max-resaltados"));
        int limit = cost("pico.max-bloques-adicionales");
        List<Block> extras = vein.stream().filter(extra -> !extra.equals(ore)).limit(limit).toList();
        if (extras.isEmpty()) return;
        // Do not modify world blocks at MONITOR. Finish the real break first,
        // then revalidate region, item identity and provenance the following tick.
        String serial = data.serial(current), originalOre = ore.getType().name();
        org.bukkit.World world = ore.getWorld();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline() || player.getWorld() != world) return;
            for (Block extra : extras) {
                ItemStack live = player.getInventory().getItemInMainHand();
                if (data.energy(live) < perBlock || data.activeUntil(live) <= System.currentTimeMillis()
                        || !serial.equals(data.serial(live))
                        || (!MithrilDeviceData.PICK.equals(data.id(live))
                            && !MithrilDeviceData.TEMPERED.equals(data.id(live)))
                        || !world.isChunkLoaded(extra.getX() >> 4, extra.getZ() >> 4)
                        || !MithrilRules.sameOre(originalOre, extra.getType().name())
                        || placed.isMarked(extra) || !regions.canBuild(player, extra)) break;
                ItemStack breaker = live;
                if (!MithrilAutomationGuard.breakBlock(() -> extra.breakNaturally(breaker, true))) break;
                data.update(player, EquipmentSlot.HAND, data.changeCharge(live, -perBlock, capacity()));
                if (!MithrilDrill.wear(player, data, 1)) break;
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        if (event.getPlayer().isSneaking()
                && (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && MithrilDeviceData.LANTERN.equals(
                    data.id(event.getPlayer().getInventory().getItemInMainHand()))) {
            lantern.teleported(event.getPlayer()); // Clear the anchor, not the light.
            feedback(event.getPlayer(), "ancla-limpiada", "<gray>Se borró el sendero del Farol.");
            event.setCancelled(true);
            return;
        }
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.isCancelled())) return;
        Player player = event.getPlayer();
        ItemStack main = data.held(player, EquipmentSlot.HAND);
        if (data.crystal(main)) {
            if (recharge(player)) event.setCancelled(true);
            return;
        }
        String id = data.id(main);
        if (id.isEmpty()) return;
        // Exactly one interaction per main hand; offhand dispatch cannot double-charge.
        main = data.identify(main);
        data.update(player, EquipmentSlot.HAND, main);
        boolean used = switch (id) {
            case MithrilDeviceData.PICK, MithrilDeviceData.TEMPERED -> player.isSneaking() && pick(player);
            case MithrilDeviceData.AMETHYST -> player.isSneaking() && amethyst(player);
            case MithrilDeviceData.COMPASS -> compass(player);
            case MithrilDeviceData.LANTERN -> light(player);
            case MithrilDeviceData.DRILL -> player.isSneaking() && drill(player);
            default -> false;
        };
        if (used) event.setCancelled(true);
    }

    private boolean recharge(Player player) {
        ItemStack stack = data.held(player, EquipmentSlot.OFF_HAND);
        if (data.id(stack).isEmpty()) return false;
        int max = capacity(), existing = data.energy(stack);
        if (existing >= max) {
            feedback(player, "carga-completa", "<gray>La herramienta ya está completamente cargada.");
            return true;
        }
        data.update(player, EquipmentSlot.OFF_HAND,
                data.changeCharge(stack, cost("cristal.recupera"), max));
        ItemStack crystal = data.held(player, EquipmentSlot.HAND);
        if (crystal.getAmount() <= 1) data.update(player, EquipmentSlot.HAND, new ItemStack(Material.AIR));
        else {
            crystal.setAmount(crystal.getAmount() - 1);
            data.update(player, EquipmentSlot.HAND, crystal);
        }
        feedback(player, "cargada", "<aqua>✦ Resonancia restaurada: <white>{carga}/100",
                "carga", data.energy(data.held(player, EquipmentSlot.OFF_HAND)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, 1.25f);
        return true;
    }

    private boolean spend(Player player, int energy, long cooldownMs, long activeMs) {
        ItemStack stack = data.held(player, EquipmentSlot.HAND);
        long now = System.currentTimeMillis();
        if (!MithrilRules.canUse(data.energy(stack), energy, now, data.cooldown(stack))) {
            feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
            return false;
        }
        stack = data.changeCharge(stack, -energy, capacity());
        stack = data.cooldown(stack, now + cooldownMs);
        if (activeMs > 0) stack = data.active(stack, now + activeMs);
        data.update(player, EquipmentSlot.HAND, stack);
        return true;
    }

    private boolean pick(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target == null || MithrilRules.sameOre(target.getType().name(), target.getType().name()) == false
                || placed.isMarked(target) || !regions.canBuild(player, target)) {
            feedback(player, "sin-veta", "<gray>Mira una veta natural que puedas extraer.");
            return true;
        }
        int radius = cost("pico.radio"), highlights = cost("pico.max-resaltados");
        List<Block> vein = prospector.connected(player, target, radius, highlights);
        if (vein.isEmpty()) {
            feedback(player, "sin-veta", "<gray>Mira una veta natural que puedas extraer.");
            return true;
        }
        if (!spend(player, cost("pico.activacion"), cost("pico.recarga-segundos") * 1000L,
                cost("pico.duracion-segundos") * 1000L)) return true;
        prospector.highlight(player, vein, cost("pico.resaltado-segundos"));
        feedback(player, "veta", "<aqua>✦ Veta marcada. Extrae para recoger hasta tres bloques adicionales.");
        return true;
    }

    private boolean amethyst(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        int price = cost("pico-amatista.activacion");
        if (!MithrilRules.canUse(data.energy(held), price, System.currentTimeMillis(),
                data.cooldown(held))) {
            feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
            return true;
        }
        var plan = amethystField.prepare(player);
        if (plan == null) {
            feedback(player, "sin-cristales",
                    "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.");
            return true;
        }
        int summoned = amethystField.summon(player, data.serial(held), plan);
        if (summoned == 0) {
            feedback(player, "sin-cristales",
                    "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.");
            return true;
        }
        if (!spend(player, price, cost("pico-amatista.recarga-segundos") * 1000L, 0)) {
            amethystField.close(player.getUniqueId());
            return true;
        }
        feedback(player, "campo-ametista",
                "<light_purple>✦ Resonancia amplificada: {cantidad} cristales. ¡Rómpelos con tu pico!",
                "cantidad", summoned);
        return true;
    }

    private boolean compass(Player player) {
        if (!regions.canBuild(player, player.getLocation().getBlock())) {
            feedback(player, "zona", "<red>La herramienta no puede utilizarse en esta zona.");
            return true;
        }
        if (!prospector.scanSlotAvailable(player.getUniqueId())) {
            feedback(player, "sin-senal", "<gray>El pulso no detectó minerales accesibles.");
            return true; // Do not charge while the bounded scanner is at capacity.
        }
        if (!spend(player, cost("brujula.activacion"),
                cost("brujula.recarga-segundos") * 1000L, 0)) return true;
        prospector.scan(player, cost("brujula.radio-horizontal"), cost("brujula.radio-vertical"), result -> {
            if (result == null) {
                feedback(player, "sin-senal", "<gray>El pulso no detectó minerales accesibles.");
                return;
            }
            String intensity = MithrilRules.signal(result.distance());
            String direction = MithrilRules.direction(result.dx(), result.dz());
            String depth = MithrilRules.depth(result.dy());
            feedback(player, "senal",
                    "<aqua>✦ Señal {intensidad}<gray> · {direccion} · Profundidad {profundidad}",
                    "intensidad", intensity, "direccion", direction, "profundidad", depth);
            UUID uuid = player.getUniqueId();
            BukkitTask old = signals.remove(uuid);
            if (old != null) old.cancel();
            int[] remaining = {cost("brujula.duracion-senal-segundos")};
            BukkitTask repeating = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || remaining[0]-- <= 0) {
                    BukkitTask timer = signals.remove(uuid);
                    if (timer != null) timer.cancel();
                    return;
                }
                player.sendActionBar(Settings.message("tesoros", "MithrilTools.senal",
                        "<aqua>✦ Señal {intensidad}<gray> · {direccion} · Profundidad {profundidad}",
                        "intensidad", intensity, "direccion", direction, "profundidad", depth));
            }, 0, 20);
            signals.put(uuid, repeating);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, 1f);
        });
        return true;
    }

    private boolean light(Player player) {
        ItemStack stack = data.held(player, EquipmentSlot.HAND);
        if (!player.isSneaking()) {
            boolean lit = !data.lit(stack);
            data.update(player, EquipmentSlot.HAND, data.lit(stack, lit));
            if (lit) lantern.light(player);
            feedback(player, lit ? "luz" : "luz-apagada",
                    lit ? "<aqua>✦ Farol encendido." : "<gray>Farol apagado.");
            return true;
        }
        String anchor = data.anchor(stack);
        if (anchor.isBlank()) {
            int price = cost("farol.anclar");
            if (data.energy(stack) < price) {
                feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
                return true;
            }
            if (lantern.anchor(player, EquipmentSlot.HAND, price, capacity()))
                feedback(player, "anclado", "<aqua>✦ El Farol recuerda tu ubicación.");
            return true;
        }
        if (lantern.route(player, EquipmentSlot.HAND, cost("farol.ruta"), capacity(),
                cost("farol.recarga-segundos") * 1000L, cost("farol.marcas-segundos")))
            feedback(player, "ruta", "<aqua>✦ Sendero de retorno visible durante 20 segundos.");
        else feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
        return true;
    }

    private boolean drill(Player player) {
        if (drill.running(player)) return true;
        if (!regions.canBuild(player, player.getLocation().getBlock())) {
            feedback(player, "zona", "<red>La herramienta no puede utilizarse en esta zona.");
            return true;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        int price = cost("barrena.activacion");
        if (!MithrilRules.canUse(data.energy(stack), price, System.currentTimeMillis(),
                data.cooldown(stack))) {
            feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
            return true;
        }
        int steps = cost("barrena.pasos"), interval = cost("barrena.intervalo-ticks");
        if (!drill.start(player, steps, interval,
                settings.getStringList("herramientas-mitrilo.barrena.bloques"))) {
            feedback(player, "sin-tunel", "<gray>La Barrena necesita roca común y un camino permitido.");
            return true;
        }
        ItemStack updated = data.held(player, EquipmentSlot.HAND);
        updated = data.cooldown(data.changeCharge(updated, -price, capacity()),
                System.currentTimeMillis() + cost("barrena.recarga-segundos") * 1000L);
        data.update(player, EquipmentSlot.HAND, updated);
        feedback(player, "tunel", "<aqua>✦ Barrena activada. No extrae minerales ni concede experiencia adicional.");
        return true;
    }

    @EventHandler public void move(PlayerMoveEvent event) {
        if (event.getTo() != null) lantern.moved(event.getPlayer(), event.getFrom(), event.getTo(),
                cost("farol.distancia-maxima"));
    }
    @EventHandler public void teleport(PlayerTeleportEvent event) { lantern.teleported(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        prospector.quit(id);
        drill.cancel(id);
        lantern.cancel(id);
        BukkitTask signal = signals.remove(id);
        if (signal != null) signal.cancel();
    }
}
