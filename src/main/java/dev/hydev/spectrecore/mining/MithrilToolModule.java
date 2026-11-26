package dev.hydev.spectrecore.mining;

import static dev.hydev.spectrecore.relicario.RelicarioModule.miningBonus;

import dev.hydev.spectrecore.config.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.entity.PlayerDeathEvent;
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
    private final MiningProgression progression;
    private final MithrilRegionAccess regions;
    private final MithrilProspector prospector;
    private final MithrilLantern lantern;
    private final MithrilDrill drill;
    private final AmethystResonanceField amethystField;
    private YamlConfiguration settings;
    private record Signal(BossBar bar, BukkitTask expiry) { }
    private final Map<UUID, Signal> signals = new HashMap<>();
    private boolean active;

    public MithrilToolModule(JavaPlugin plugin, PlacedOres placed) {
        this.plugin = plugin;
        this.placed = placed;
        data = new MithrilDeviceData(plugin);
        progression = new MiningProgression(plugin);
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
    private final Map<UUID, java.util.Set<org.bukkit.Location>> markedVeins = new HashMap<>();

    public void disable() {
        markedVeins.clear();
        prospector.disable();
        drill.disable();
        lantern.disable();
        amethystField.disable();
        for (UUID id : List.copyOf(signals.keySet())) clearSignal(id);
        if (active) HandlerList.unregisterAll(this);
        active = false;
    }

    public void revealOre(Player player, org.bukkit.Location location, String tool) {
        if (active && amethystField.revealOre(player,location,tool)) {
            player.sendMessage("§b✦ Diamante Loco · Golpea la mena revelada.");
        }
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
            case "campo-ametista" -> Settings.message("tesoros", "MithrilTools.campo-ametista", "<light_purple>✦ {cantidad} cristales invocados <gray>(-{costo} de resonancia). <light_purple>¡Rómpelos!", values);
            default -> Settings.component(fallback);
        });
    }

    private String stage(String id) {
        return switch (id) {
            case MithrilDeviceData.AMETHYST -> "arcana";
            case MithrilDeviceData.COMPASS, MithrilDeviceData.LANTERN,
                 MithrilDeviceData.DRILL -> "ingenieria";
            default -> "mitrilo";
        };
    }
    private boolean unlocked(Player player, String id) {
        return progression.meets(player,stage(id));
    }

    record ToolHud(Component title, float progress, BossBar.Color color) { }

    /**
     * Main-hand read-only snapshot. Reuse the actual per-item ledger, progression and costs.
     * Ready means enough energy and no cooldown; target/region validation stays on interaction.
     */
    ToolHud hud(Player player, ItemStack stack, long now) {
        if (!active || stack == null || !stack.hasItemMeta()) return null;
        String id = data.id(stack);
        if (id.isEmpty()) return null;
        int energy = data.energy(stack), maximum = capacity();
        int minimumCost, fullCost;
        long cooldown = data.cooldown(stack);
        Component ability;
        Component control = Settings.message("tesoros", "MithrilTools.hud-control-agachado", "<gray>Agáchate + clic der.");
        Component inUse = null;
        switch (id) {
            case MithrilDeviceData.PICK, MithrilDeviceData.TEMPERED -> {
                ability = Settings.message("tesoros", "MithrilTools.hud-veta", "Veta");
                minimumCost = fullCost = cost("pico.activacion");
                long seconds = MithrilRules.remainingSeconds(now, data.activeUntil(stack));
                if (seconds > 0 && markedVeins.containsKey(player.getUniqueId()))
                    inUse = Settings.message("tesoros", "MithrilTools.hud-veta-activa",
                            "<aqua>Mina la veta marcada · {s}s", "s", seconds);
            }
            case MithrilDeviceData.AMETHYST -> {
                ability = Settings.message("tesoros", "MithrilTools.hud-amatista", "Amatista");
                fullCost = cost("pico-amatista.activacion");
                minimumCost = AmethystResonanceRules.activationCost(fullCost,
                        cost("pico-amatista.minimo-bloques"), cost("pico-amatista.bloques"));
                if (amethystField.hasField(player.getUniqueId()))
                    inUse = Settings.message("tesoros", "MithrilTools.hud-campo-activo",
                            "<aqua>Termina el hallazgo activo");
                else if (amethystField.activeFields() >= cost("pico-amatista.max-sesiones"))
                    inUse = Settings.message("tesoros", "MithrilTools.hud-campos-ocupados",
                            "<yellow>Espera un momento");
            }
            case MithrilDeviceData.COMPASS -> {
                ability = Settings.message("tesoros", "MithrilTools.hud-brujula", "Pulso");
                control = Settings.message("tesoros", "MithrilTools.hud-control-click", "<gray>Clic der.");
                minimumCost = fullCost = cost("brujula.activacion");
            }
            case MithrilDeviceData.LANTERN -> {
                if (data.anchor(stack).isBlank()) {
                    ability = Settings.message("tesoros", "MithrilTools.hud-anclar", "Fijar retorno");
                    minimumCost = fullCost = cost("farol.anclar");
                    cooldown = 0; // Anchor placement ignores route cooldown in the actual ability.
                } else {
                    ability = Settings.message("tesoros", "MithrilTools.hud-sendero", "Sendero");
                    minimumCost = fullCost = cost("farol.ruta");
                }
            }
            case MithrilDeviceData.DRILL -> {
                ability = Settings.message("tesoros", "MithrilTools.hud-barrena", "Barrena");
                minimumCost = fullCost = cost("barrena.activacion");
                if (drill.running(player))
                    inUse = Settings.message("tesoros", "MithrilTools.hud-barrena-activa", "<aqua>Excavando");
            }
            default -> { return null; }
        }
        var state = MithrilRules.abilityStatus(energy, maximum, minimumCost, fullCost, now, cooldown);
        Component detail;
        BossBar.Color color;
        if (!unlocked(player, id)) {
            detail = Settings.message("tesoros", "MithrilTools.hud-bloqueada", "<yellow>Desbloquea Minería {nivel}",
                    "nivel", progression.required(stage(id)));
            color = BossBar.Color.YELLOW;
        } else if (player.getGameMode() != GameMode.SURVIVAL) {
            detail = Settings.message("tesoros", "MithrilTools.hud-supervivencia", "<yellow>Solo en supervivencia");
            color = BossBar.Color.YELLOW;
        } else if (inUse != null) {
            detail = inUse;
            color = BossBar.Color.BLUE;
        } else {
            switch (state.readiness()) {
                case COOLDOWN -> {
                    detail = Settings.message("tesoros", "MithrilTools.hud-recarga", "<yellow>Recarga {s}s",
                            "s", state.seconds());
                    color = BossBar.Color.YELLOW;
                }
                case CHARGING -> {
                    detail = Settings.message("tesoros", "MithrilTools.hud-falta", "<gray>Faltan <white>{falta} <gray>de carga",
                            "falta", state.missing());
                    color = BossBar.Color.PURPLE;
                }
                case PARTIAL -> {
                    detail = Settings.message("tesoros", "MithrilTools.hud-parcial",
                            "<yellow>Campo parcial posible <gray>· {control}", "control", control);
                    color = BossBar.Color.YELLOW;
                }
                case READY -> {
                    detail = Settings.message("tesoros", "MithrilTools.hud-lista",
                            "<green>Carga lista <gray>· {control}", "control", control);
                    color = BossBar.Color.GREEN;
                }
                default -> throw new IllegalStateException("Unexpected resonance state");
            }
        }
        Component title = Settings.message("tesoros", "MithrilTools.hud-barra",
                "<aqua>✦ Resonancia <white>{carga}/{max} <gray>· <white>{habilidad} <dark_gray>│ {estado}",
                "carga", energy, "max", maximum, "habilidad", ability, "estado", detail);
        return new ToolHud(title, state.progress(), color);
    }

    /** Called exclusively from a genuine, uncancelled player BlockBreakEvent. */
    public void onManualOre(Player player, Block ore) {
        if (player.getGameMode() != GameMode.SURVIVAL
                || !progression.meets(player,"mitrilo")) return;
        // Prevent charging devices by punching ores with an unsuitable tool.
        if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) return;
        int amount = cost("resonancia.por-mineral-natural");
        if (amount <= 0) return;
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            ItemStack stack = data.held(player, hand);
            if (data.id(stack).isEmpty() || !unlocked(player,data.id(stack))) continue;
            data.update(player, hand, data.changeCharge(stack, amount, capacity()));
        }
        ItemStack current = player.getInventory().getItemInMainHand();
        String id = data.id(current);
        if (MithrilDeviceData.AMETHYST.equals(id)) {
            return; // Amethyst rewards belong exclusively to enchanted nodes; HUD shows charge.
        }
        if (!MithrilDeviceData.PICK.equals(id) && !MithrilDeviceData.TEMPERED.equals(id)) return;
        if (data.activeUntil(current) <= System.currentTimeMillis()) return;
        var marked = markedVeins.get(player.getUniqueId());
        if (marked == null || !marked.contains(ore.getLocation())) return;
        int perBlock = cost("pico.coste-bloque-adicional");
        if (perBlock <= 0) return;
        List<Block> vein = prospector.connected(player, ore,
                cost("pico.radio"), cost("pico.max-resaltados"));
        int limit = cost("pico.max-bloques-adicionales");
        List<Block> extras = vein.stream().filter(extra -> !extra.equals(ore) && marked.contains(extra.getLocation())).limit(limit).toList();
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
                        || placed.isMarked(extra) || !regions.canBreak(player, extra)) break;
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
                && unlocked(event.getPlayer(),MithrilDeviceData.LANTERN)
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
            String heldTool=data.id(data.held(player,EquipmentSlot.OFF_HAND));
            if (!heldTool.isEmpty() && !unlocked(player,heldTool)) {
                event.setCancelled(true);
                player.sendMessage(Settings.message("trabajos","MiningProgression.requisito-herramienta",
                        "<yellow>Necesitas Minería nivel {nivel} para utilizar esta herramienta.",
                        "nivel", progression.required(stage(heldTool))));
                return;
            }
            if (recharge(player)) event.setCancelled(true);
            return;
        }
        String id = data.id(main);
        if (id.isEmpty()) return;
        if(!unlocked(player,id)){
            event.setCancelled(true);
            player.sendMessage(Settings.message("trabajos","MiningProgression.requisito-herramienta",
                    "<yellow>Necesitas Minería nivel {nivel} para utilizar esta herramienta.",
                    "nivel", progression.required(stage(id))));
            return;
        }
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

    private int configRecharge() { return settings.getInt("herramientas-mitrilo.carga.recupera", 30); }

    private boolean recharge(Player player) {
        ItemStack stack = data.held(player, EquipmentSlot.OFF_HAND);
        if (data.id(stack).isEmpty() || MithrilDeviceData.AMETHYST.equals(data.id(stack))) return false;
        int max = capacity(), existing = data.energy(stack);
        if (existing >= max) {
            feedback(player, "carga-completa", "<gray>La herramienta ya está completamente cargada.");
            return true;
        }
        data.update(player, EquipmentSlot.OFF_HAND,
                data.changeCharge(stack, configRecharge(), max));
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
                || placed.isMarked(target) || !regions.canBreak(player, target)) {
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
        markedVeins.put(player.getUniqueId(), vein.stream().map(Block::getLocation).collect(java.util.stream.Collectors.toSet()));
        prospector.highlight(player, vein, cost("pico.resaltado-segundos"));
        feedback(player, "veta", "<aqua>✦ Veta marcada. Extrae para recoger hasta tres bloques adicionales.");
        return true;
    }

    private boolean amethyst(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        long now = System.currentTimeMillis();
        int fullPrice = cost("pico-amatista.activacion");
        int totalNodes = cost("pico-amatista.bloques");
        int minimum = cost("pico-amatista.minimo-bloques");
        int maxFields = cost("pico-amatista.max-sesiones");
        if (amethystField.activeFields() >= maxFields) {
            feedback(player, "sin-cristales",
                    "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.");
            return true;
        }
        int minimumPrice = AmethystResonanceRules.activationCost(fullPrice, minimum, totalNodes);
        if (!MithrilRules.canUse(data.energy(held), minimumPrice, now, data.cooldown(held))) {
            feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
            return true;
        }
        var plan = amethystField.prepare(player);
        if (plan == null) {
            feedback(player, "sin-cristales",
                    "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.");
            return true;
        }
        int expectedCost = AmethystResonanceRules.activationCost(fullPrice, plan.positions().size(), totalNodes);
        if (!AmethystResonanceRules.available(data.energy(held), data.cooldown(held),
                now, expectedCost, amethystField.activeFields(), maxFields)) {
            feedback(player, "sin-carga", "<yellow>Necesitas más resonancia o aguardar la recarga.");
            return true;
        }
        int summoned = amethystField.summon(player, data.serial(held), plan);
        if (summoned == 0) {
            feedback(player, "sin-cristales",
                    "<yellow>No hay espacio seguro para invocar cristales o sus gemas todavía no están disponibles.");
            return true;
        }
        int price = AmethystResonanceRules.activationCost(fullPrice, summoned, totalNodes);
        if (!spend(player, price, cost("pico-amatista.recarga-segundos") * 1000L, 0)) {
            amethystField.close(player.getUniqueId());
            return true;
        }
        feedback(player, "campo-ametista",
                "<light_purple>✦ {cantidad} cristales invocados <gray>(-{costo} de resonancia). <light_purple>¡Rómpelos!",
                "cantidad", summoned, "costo", price);
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
            clearSignal(uuid);
            long duration = cost("brujula.duracion-senal-segundos") + (long) miningBonus(plugin, player, "signal_seconds");
            if (duration > 0 && player.isOnline()) {
                BossBar bar = BossBar.bossBar(Settings.message("tesoros", "MithrilTools.senal",
                        "<aqua>✦ Señal {intensidad}<gray> · {direccion} · Profundidad {profundidad}",
                        "intensidad", intensity, "direccion", direction, "profundidad", depth),
                        1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> clearSignal(uuid), duration * 20L);
                signals.put(uuid, new Signal(bar, expiry));
                player.showBossBar(bar);
            }
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
    private void clearSignal(UUID id) {
        Signal signal = signals.remove(id);
        if (signal == null) return;
        signal.expiry().cancel();
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.hideBossBar(signal.bar());
    }
    @EventHandler(ignoreCancelled = true) public void teleport(PlayerTeleportEvent event) {
        lantern.teleported(event.getPlayer());
        clearSignal(event.getPlayer().getUniqueId());
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) { clearSignal(event.getEntity().getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        markedVeins.remove(event.getPlayer().getUniqueId());
        var id = event.getPlayer().getUniqueId();
        prospector.quit(id);
        drill.cancel(id);
        lantern.cancel(id);
        clearSignal(id);
    }
}
