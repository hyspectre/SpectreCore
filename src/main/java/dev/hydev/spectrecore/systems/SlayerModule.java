package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.integration.SlayerUnlockBridge;
import dev.hydev.spectrecore.guide.MissionsMenu;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.time.Duration;
import java.util.*;

public final class SlayerModule implements Listener {
    private static final List<String> CATEGORIES = List.of("revenant", "necromancer", "spider");
    private static final class Mission extends ProgressRules.SlayerProgress {
        UUID bossId;
        double bossMaxHealth, ownerDamage;
        String bossWorld;
        int bossX, bossY, bossZ;
    }
    private static final class SlayerRecord { long totalXp; int victories; }
    private record BossClaim(UUID owner, String category, int tier) { }
    private final JavaPlugin plugin; private YamlConfiguration config;
    private final EconomyBridge contractsEconomy = new EconomyBridge();
    private final MmoBridge contractItems = new MmoBridge();
    private final SlayerUnlockBridge unlocks;
    private final NamespacedKey bossMarker;
    private MissionsMenu missionsMenu;
    private final Map<UUID, Map<String, Mission>> missions = new HashMap<>();
    private final Map<UUID, SlayerRecord> revenantRecords = new HashMap<>();
    private final Map<UUID, BossClaim> activeBosses = new HashMap<>();
    private long[] xpThresholds = SlayerProgressionRules.defaultThresholds();
    private final Map<UUID, BossBar> bars = new HashMap<>(); private final Map<UUID, Long> attempts = new HashMap<>();
    private BukkitTask timer; private boolean dirty;
    private boolean mythicDetectionWarned;
    public SlayerModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.unlocks = new SlayerUnlockBridge(plugin);
        this.bossMarker = new NamespacedKey(plugin, "slayer_contract");
    }
    public void enable() {
        reload(); load(); Bukkit.getPluginManager().registerEvents(this, plugin);
        missionsMenu = new MissionsMenu(plugin, this);
        missionsMenu.enable();
        for (String name : List.of("activarslayer", "cancelarslayer", "resetslayer", "slayerstats", "slayerinvocar", "contratarslayer", "abandonarslayer", "comprartelefono", "misiones")) if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name) != null) {
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setExecutor((sender, cmd, label, args) -> command(sender, cmd.getName(), args));
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setTabCompleter((sender, cmd, label, args) -> args.length == 1 && !cmd.getName().equals("slayerstats") ? CATEGORIES : List.of());
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            snapshotActiveBossLocations();
            if (dirty) save();
        }, 200, 200);
        Bukkit.getOnlinePlayers().forEach(player -> {
            refreshBar(player);
            unlocks.synchronize(player.getUniqueId(), level(player.getUniqueId()));
        });
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "slayer/config.yml");
        long[] candidate = SlayerProgressionRules.defaultThresholds();
        for (int i = 1; i <= SlayerProgressionRules.MAX_LEVEL; i++)
            candidate[i] = config.getLong("progresion.revenant.niveles." + i, candidate[i]);
        if (SlayerProgressionRules.validThresholds(candidate)) xpThresholds = candidate;
        else {
            xpThresholds = SlayerProgressionRules.defaultThresholds();
            plugin.getLogger().warning("Slayer: curva XP inválida. Se utilizan los umbrales seguros por defecto.");
        }
        if (missionsMenu != null) missionsMenu.reload();
    }
    public void disable() {
        if (timer != null) timer.cancel();
        if (missionsMenu != null) { missionsMenu.disable(); missionsMenu = null; }
        HandlerList.unregisterAll(this); Bukkit.getOnlinePlayers().forEach(this::hideBar); save();
    }
    private Mission mission(UUID id, String category) { return missions.computeIfAbsent(id, unused -> new HashMap<>()).computeIfAbsent(category, unused -> new Mission()); }
    private String name(String category) { return config.getString("categorias." + category + ".nombre", category); }
    private boolean admin(CommandSender s) { return s.hasPermission("spectrecore.admin") || s.hasPermission("hydev.admin"); }
    private SlayerRecord revenant(UUID player) {
        return revenantRecords.computeIfAbsent(player, unused -> new SlayerRecord());
    }
    private int level(UUID player) {
        return SlayerProgressionRules.level(revenant(player).totalXp, xpThresholds);
    }
    private boolean accessible(Player player, String category, int tier) {
        return admin(player) || SlayerProgressionRules.accessible(
                category, tier, level(player.getUniqueId()), player.hasPermission("slayer." + category + ".tier" + tier));
    }
    /** Human-readable boss metadata is configurable; it does not override MythicMobs itself. */
    public String bossHealthLabel(int tier) {
        return Integer.toString(config.getInt("presentacion.revenant.tier" + tier + ".vida",
                switch (tier) { case 1 -> 1600; case 2 -> 3800; default -> 7800; }));
    }
    public String bossDamageLabel(int tier) {
        return Integer.toString(config.getInt("presentacion.revenant.tier" + tier + ".dano",
                switch (tier) { case 1 -> 8; case 2 -> 20; default -> 50; }));
    }
    public String slayerBossXpLabel(int tier) { return Integer.toString(xpFromBoss(tier)); }
    private int xpFromBoss(int tier) {
        int fallback = switch (tier) { case 1 -> 25; case 2 -> 100; case 3 -> 250; default -> 0; };
        return Math.max(0, Math.min(100_000, config.getInt("progresion.revenant.xp-jefes.tier" + tier, fallback)));
    }
    public int slayerLevel(Player player) { return level(player.getUniqueId()); }
    public long slayerXp(Player player) { return revenant(player.getUniqueId()).totalXp; }
    public int slayerVictories(Player player) { return revenant(player.getUniqueId()).victories; }
    public String slayerNextXp(Player player) {
        long next = SlayerProgressionRules.xpForNext(slayerLevel(player), xpThresholds);
        return next < 0 ? "MAX" : Long.toString(next);
    }
    public String slayerRewardState(Player player, int rewardLevel) {
        return slayerLevel(player) >= rewardLevel
                ? Settings.text("slayer", "SlayerModule.nivel-desbloqueado", "<green>DESBLOQUEADO")
                : Settings.text("slayer", "SlayerModule.nivel-bloqueado", "<dark_gray>BLOQUEADO");
    }
    private boolean command(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-001", "Esta acción solo puede realizarse dentro del juego.")); return true; }
        UUID id = p.getUniqueId();
        if (command.equals("misiones")) {
            if (missionsMenu != null) {
                String page = args.length > 0 && args[0].equalsIgnoreCase("revenant") ? "revenant"
                        : args.length > 0 && args[0].equalsIgnoreCase("progresion") ? "progresion" : "misiones";
                missionsMenu.open(p, page);
            }
            return true;
        }
        if (command.equals("comprartelefono")) return purchasePhone(p, args);
        if (command.equals("slayerstats")) {
            p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-002", "§6✦ ESTADÍSTICAS DE MISIONES"));
            p.sendMessage(Settings.message("slayer", "SlayerModule.estadisticas-nivel",
                    "<gold>Monarquía Renacida <white>Nivel {nivel} <gray>· <yellow>{xp}/{siguiente} XP <gray>· Victorias: <white>{victorias}",
                    "nivel", slayerLevel(p), "xp", slayerXp(p), "siguiente", slayerNextXp(p),
                    "victorias", slayerVictories(p)));
            for (String c : CATEGORIES) { Mission m = mission(id, c); p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-003","§e{name-c}§7: §f{m-kills}/{m-required} §7| Tier {m-tier} | {valor5}","name-c", name(c),"m-kills", m.kills,"m-required", m.required,"m-tier", m.tier,"valor5", (m.active ? Settings.component(Settings.text("slayer", "SlayerModule.texto-001", "§aActiva")) : Settings.component(Settings.text("slayer", "SlayerModule.texto-002", "§7Inactiva"))))); }
            return true;
        }
        if (command.equals("contratarslayer") || command.equals("abandonarslayer"))
            return playerContract(p, command, args);
        // Reintento seguro sin permisos administrativos: solo sobre una cacería pagada y completada.
        if (command.equals("slayerinvocar")) {
            if (args.length != 1 || !CATEGORIES.contains(args[0].toLowerCase(Locale.ROOT))) {
                p.sendMessage(Settings.component(Settings.text("slayer", "SlayerModule.reintento-uso",
                        "<red>Uso: /spectrecore slayer invocar [categoria]")));
                return true;
            }
            String category = args[0].toLowerCase(Locale.ROOT);
            Mission pending = mission(id, category);
            if (!pending.active || !pending.ready()) {
                p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-010",
                        "§cTodavía no has completado ese objetivo."));
            } else if (pending.bossId != null) {
                p.sendMessage(Settings.component(Settings.text("slayer", "SlayerModule.reintento-en-curso",
                        "<yellow>Ya tienes un jefe activo. No se permite otra invocación.")));
            } else summon(p, category, pending);
            return true;
        }
        if (!admin(sender)) { sender.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-004", "§cNo tienes permiso.")); return true; }
        if (args.length < 1 || !CATEGORIES.contains(args[0].toLowerCase(Locale.ROOT))) { p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-005", "§cCategoría: revenant, necromancer o spider.")); return true; }
        String category = args[0].toLowerCase(Locale.ROOT); Mission m = mission(id, category);
        if (command.equals("activarslayer")) {
            int tier; try { tier = args.length > 1 ? Integer.parseInt(args[1]) : 0; } catch (NumberFormatException e) { tier = 0; }
            if (tier < 1 || tier > 3) { p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-006", "§cTier inválido. Usa 1, 2 o 3.")); return true; }
            if (CATEGORIES.stream().anyMatch(c -> mission(id, c).active)) { p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-007", "§cYa tienes una misión activa. Complétala o cancélala.")); return true; }
            if (!accessible(p, category, tier)) { p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-008", "§cNo tienes permiso para ese tier.")); return true; }
            m.tier = tier; m.kills = 0; m.required = Math.max(1, config.getInt("objetivos.tier" + tier, ProgressRules.required(tier))); m.active = true;
            dirty = true; save(); refreshBar(p); p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-009", "§6MISIONES §e{name-category} · Tier {tier} · Objetivo: {m-required}", "name-category", name(category), "tier", tier, "m-required", m.required));
            p.playSound(p.getLocation(), Settings.sound("slayer", "SlayerModule.sound-001", Sound.ENTITY_PLAYER_LEVELUP),Settings.f("slayer", "SlayerModule.playsound-001-arg2", 1),Settings.f("slayer", "SlayerModule.playsound-002-arg3", 1)); return true;
        }
        if (command.equals("cancelarslayer") && !m.active) { p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-011", "§cNo tienes esa misión activa.")); return true; }
        m.active = false; if (command.equals("resetslayer")) m.kills = 0;
        dismissBoss(m);
        dirty = true; save(); refreshBar(p); p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-012","§7Misión {name-category}{valor2}","name-category", name(category),"valor2", (command.equals("resetslayer") ? " reiniciada." : " cancelada."))); return true;
    }
    /** Native menu actions use the same validated contract code as the public command. */
    public void contractFromMenu(Player player, int tier) {
        playerContract(player, "contratarslayer", new String[]{"revenant", Integer.toString(tier)});
    }
    public void cancelFromMenu(Player player) {
        playerContract(player, "abandonarslayer", new String[]{"revenant"});
    }
    public void buyPhoneFromMenu(Player player) { purchasePhone(player, new String[0]); }
    /** Read-only values for native GUI. Never use YAML lore as a source of prices. */
    private String costLabel(String key) {
        Object raw = config.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.longValue() < 0 || number.longValue() > 1_000_000_000L)
            return "Sin configurar";
        return Long.toString(number.longValue());
    }
    public String contractPriceLabel(String category, int tier) {
        return costLabel("contratos.precios." + category + ".tier" + tier);
    }
    public String cancellationPriceLabel(String category) {
        return costLabel("contratos.cancelacion." + category);
    }
    public String phonePriceLabel() {
        return costLabel("acceso-remoto.precio");
    }
    public int objectiveForTier(int tier) {
        if (tier < 1 || tier > 3) return 0;
        return Math.max(1, config.getInt("objetivos.tier" + tier, ProgressRules.required(tier)));
    }

    private static void clearBoss(Mission mission) {
        mission.bossId = null;
        mission.bossMaxHealth = 0;
        mission.ownerDamage = 0;
        mission.bossWorld = null;
        mission.bossX = mission.bossY = mission.bossZ = 0;
    }

    private void rememberPosition(Mission mission, Entity boss) {
        Location at = boss.getLocation();
        if (!at.getWorld().getUID().toString().equals(mission.bossWorld)
                || at.getBlockX() != mission.bossX || at.getBlockY() != mission.bossY
                || at.getBlockZ() != mission.bossZ) {
            mission.bossWorld = at.getWorld().getUID().toString();
            mission.bossX = at.getBlockX();
            mission.bossY = at.getBlockY();
            mission.bossZ = at.getBlockZ();
            dirty = true;
        }
    }
    private void snapshotActiveBossLocations() {
        for (Map.Entry<UUID,BossClaim> entry : activeBosses.entrySet()) {
            Entity boss = Bukkit.getEntity(entry.getKey());
            if (boss == null || !boss.isValid()) continue; // Unloaded != dead.
            Mission current = mission(entry.getValue().owner(), entry.getValue().category());
            if (entry.getKey().equals(current.bossId)) rememberPosition(current, boss);
        }
    }
    public boolean canRetrySpawn(Player player) {
        Mission current = mission(player.getUniqueId(), "revenant");
        return current.ready() && current.bossId == null;
    }
    public void retrySpawnFromMenu(Player player) {
        if (canRetrySpawn(player)) {
            summon(player, "revenant", mission(player.getUniqueId(), "revenant"));
            return;
        }
        player.sendMessage(Settings.component(Settings.text("slayer", "SlayerModule.reintento-sin-pendientes",
                "<gray>No tienes un jefe de Monarquía pendiente de invocación.")));
    }
    public String progressLine(Player player, String category, int tier) {
        Mission state = mission(player.getUniqueId(), category);
        if (state.active && state.tier == tier) {
            if (state.bossId != null) return Bukkit.getEntity(state.bossId) != null
                    ? "<gold>✦ Jefe activo: <white>derrota al monarca"
                    : "<yellow>✦ Jefe descargado: <gray>regresa a la zona";
            return "§a✦ Activa: §f" + state.kills + "§7/§f" + state.required + " bajas";
        }
        if (state.active) return "§cTienes otra misión activa";
        if (!accessible(player, category, tier)) return "§cNo tienes acceso a este rango";
        return "§e✦ Disponible para contratar";
    }

    /**
     * Secure purchase of the remote quest phone; never elevate the player to OP.
     * Item and capacity are checked before confirmed Vault withdrawal.
     */
    private void dismissBoss(Mission mission) {
        if (mission.bossId == null) return;
        UUID bossId = mission.bossId;
        clearBoss(mission);
        activeBosses.remove(bossId);
        Entity boss = Bukkit.getEntity(bossId);
        if (boss != null) boss.remove();
        // A tagged boss in an unloaded chunk is removed when its entities load again.
    }

    private boolean purchasePhone(Player player, String[] args) {
        if (args.length != 0 || !config.getBoolean("acceso-remoto.habilitado", false)) {
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-no-disponible",
                    "§cEl acceso remoto no está disponible por ahora."));
            return true;
        }
        String type = config.getString("acceso-remoto.tipo", "MATERIAL");
        String itemId = config.getString("acceso-remoto.id", "TELEFONO1");
        Object configuredPrice = config.get("acceso-remoto.precio");
        long price = configuredPrice instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() == Math.rint(number.doubleValue())
                ? number.longValue() : -1;
        if (price < 0 || price > 1_000_000_000L
                || !"MATERIAL".equals(type) || !"TELEFONO1".equals(itemId)) {
            plugin.getLogger().warning("Slayer: invalid remote phone item/price configuration");
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-no-disponible",
                    "§cEl acceso remoto no está disponible por ahora."));
            return true;
        }
        ItemStack item = contractItems.build(type, itemId, 1);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-item-fallo",
                    "§cNo se pudo preparar el Teléfono Rojo. No se ha cobrado."));
            return true;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-inventario",
                    "§cLibera al menos una ranura de inventario antes de comprar."));
            return true;
        }
        if (contractsEconomy.withdraw(player, price) != EconomyBridge.Result.CONFIRMED) {
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-pago-fallo",
                    "§cNo se confirmó el pago. No se ha entregado el objeto."));
            return true;
        }
        try {
            var leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                // Should be unreachable after firstEmpty() on the main thread. Preserve the
                // paid item even if an external plugin changed capacity during insertion.
                leftover.values().forEach(remaining -> player.getWorld().dropItem(
                        player.getLocation(), remaining.clone(), drop -> {
                            drop.setOwner(player.getUniqueId());
                            drop.setThrower(player.getUniqueId());
                            drop.setPickupDelay(0);
                        }));
            }
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-comprado",
                    "§aHas comprado el Teléfono Rojo por {precio} ↾.", "precio", price));
        } catch (RuntimeException error) {
            // Log for manual reconciliation: a failure after insertion might have already
            // delivered an item. Never blindly refund and risk generating free duplicates.
            plugin.getLogger().severe("SLAYER PHONE DELIVERY RECONCILIATION REQUIRED player="
                    + player.getUniqueId() + " price=" + price + " cause=" + error);
            player.sendMessage(Settings.message("slayer", "SlayerModule.telefono-entrega-fallo",
                    "§cError de entrega. Avisa a un administrador; no intentes pagar de nuevo."));
        }
        return true;
    }

    /** Player contracts are opt-in until zMenu stops doing its own payments/OP dispatch. */
    private boolean playerContract(Player p, String command, String[] args) {
        if (args.length < 1 || !CATEGORIES.contains(args[0].toLowerCase(Locale.ROOT))) {
            p.sendMessage(Settings.message("slayer", "SlayerModule.contrato-categoria", "§cCategoría inválida.")); return true;
        }
        boolean enabled = config.getBoolean("contratos.habilitados", false);
        String category = args[0].toLowerCase(Locale.ROOT);
        UUID id = p.getUniqueId(); Mission m = mission(id, category);
        boolean start = command.equals("contratarslayer");
        int tier = 0;
        if (start && args.length > 1) try { tier = Integer.parseInt(args[1]); }
        catch (NumberFormatException ignored) { tier = 0; }
        String key = start ? "contratos.precios." + category + ".tier" + tier
                           : "contratos.cancelacion." + category;
        Object priceValue = config.get(key);
        long cost = priceValue instanceof Number n && Double.isFinite(n.doubleValue())
                && n.doubleValue() == Math.rint(n.doubleValue()) ? n.longValue() : -1;
        SlayerContractRules.Refusal refusal = start
                ? SlayerContractRules.validateStart(enabled, tier,
                    CATEGORIES.stream().anyMatch(c -> mission(id, c).active),
                    accessible(p, category, tier), cost)
                : SlayerContractRules.validateCancel(enabled, m.active, cost);
        if (refusal != SlayerContractRules.Refusal.NONE) {
            p.sendMessage(Settings.message("slayer", "SlayerModule.contrato-rechazado",
                    "§cContrato rechazado: {motivo}", "motivo", refusal.name()));
            return true;
        }
        if (contractsEconomy.withdraw(p, cost) != EconomyBridge.Result.CONFIRMED) {
            p.sendMessage(Settings.message("slayer", "SlayerModule.contrato-pago-fallo",
                    "§cNo se pudo confirmar el cobro; tu contrato sigue intacto."));
            return true;
        }
        int oldTier = m.tier, oldKills = m.kills, oldRequired = m.required;
        boolean oldActive = m.active;
        UUID oldBossId = m.bossId;
        double oldBossMaxHealth = m.bossMaxHealth, oldOwnerDamage = m.ownerDamage;
        String oldBossWorld = m.bossWorld;
        int oldBossX = m.bossX, oldBossY = m.bossY, oldBossZ = m.bossZ;
        if (start) {
            m.tier = tier; m.kills = 0;
            m.required = Math.max(1, config.getInt("objetivos.tier" + tier, ProgressRules.required(tier)));
            m.active = true; clearBoss(m);
        } else { m.active = false; clearBoss(m); }
        dirty = true;
        if (!save()) {
            m.tier = oldTier; m.kills = oldKills; m.required = oldRequired; m.active = oldActive;
            m.bossId = oldBossId; m.bossMaxHealth = oldBossMaxHealth; m.ownerDamage = oldOwnerDamage;
            m.bossWorld = oldBossWorld; m.bossX = oldBossX; m.bossY = oldBossY; m.bossZ = oldBossZ;
            dirty = true;
            // Failed writes are exceptional. Refund immediately without unconfirmed command fallback.
            if (cost > 0 && contractsEconomy.deposit(p, cost, false) != EconomyBridge.Result.CONFIRMED)
                plugin.getLogger().severe("SLAYER REFUND FAILURE: " + p.getUniqueId() + " amount=" + cost + " category=" + category);
            p.sendMessage(Settings.message("slayer", "SlayerModule.contrato-storage-fallo",
                    "§cNo se pudo guardar el contrato; solicita revisión administrativa del saldo."));
            return true;
        }
        if (!start && oldBossId != null) {
            activeBosses.remove(oldBossId);
            Entity former = Bukkit.getEntity(oldBossId);
            if (former != null) former.remove();
        }
        refreshBar(p);
        p.sendMessage(Settings.message("slayer", "SlayerModule.contrato-aplicado",
                "§6Contrato {accion}: {categoria} · Costo {costo} ↾",
                "accion", start ? "iniciado" : "abandonado", "categoria", name(category), "costo", cost));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void bossDamage(EntityDamageByEntityEvent event) {
        BossClaim claim = activeBosses.get(event.getEntity().getUniqueId());
        if (claim == null || !(event.getEntity() instanceof LivingEntity boss)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) attacker = shooter;
        // Pets, magic helpers, turrets and other participants cannot earn credit for the owner.
        if (attacker == null || !attacker.getUniqueId().equals(claim.owner())) return;
        Mission current = mission(claim.owner(), claim.category());
        if (!current.active || !event.getEntity().getUniqueId().equals(current.bossId)) return;
        if (current.bossMaxHealth <= 0) current.bossMaxHealth = Math.max(boss.getMaxHealth(), boss.getHealth());
        double next = SlayerCombatRules.accumulate(current.ownerDamage, event.getFinalDamage(), boss.getHealth());
        if (next > current.ownerDamage) {
            current.ownerDamage = next;
            dirty = true; // Periodic flush, plus join/quit and final boss death.
        }
    }

    /** Identity survives chunk unloads; a loaded old contract boss is removed as stale. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesLoaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            String marker = entity.getPersistentDataContainer().get(bossMarker, PersistentDataType.STRING);
            BossClaim claim = activeBosses.get(entity.getUniqueId());
            if (marker == null && claim == null) continue;
            if (claim == null) { entity.remove(); continue; }
            Mission current = mission(claim.owner(), claim.category());
            if (!current.active || !entity.getUniqueId().equals(current.bossId)
                    || (marker != null && !marker.equals(claim.owner() + ":" + claim.category()))) {
                entity.remove();
                continue;
            }
            // Older v0.7.4 active bosses lacked the persistent marker: attach on first load.
            if (marker == null)
                entity.getPersistentDataContainer().set(bossMarker, PersistentDataType.STRING,
                        claim.owner() + ":" + claim.category());
            if (entity instanceof LivingEntity living && current.bossMaxHealth <= 0) {
                current.bossMaxHealth = Math.max(living.getMaxHealth(), living.getHealth());
                dirty = true;
            }
            rememberPosition(current, entity);
        }
    }

    /**
     * Paper distinguishes entity unload from permanent removal. A despawn restores
     * the paid contract's ready-to-summon state; never interpret chunk unload as death.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void bossRemoved(EntityRemoveEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        BossClaim claim = activeBosses.get(entityId);
        if (claim == null || !SlayerCombatRules.restoresPendingOnRemoval(event.getCause().name())) return;
        Mission current = mission(claim.owner(), claim.category());
        activeBosses.remove(entityId);
        if (!current.active || !entityId.equals(current.bossId)) return;
        clearBoss(current);
        current.kills = current.required; // Paid progress preserved, never award XP on removal.
        dirty = true;
        boolean saved = save();
        Player owner = Bukkit.getPlayer(claim.owner());
        if (owner != null && owner.isOnline()) {
            refreshBar(owner);
            owner.sendMessage(Settings.message("slayer", "SlayerModule.jefe-desaparecido",
                    "<yellow>Tu jefe desapareció. Conservaste el contrato; usa /spectrecore slayer invocar {category} para reintentarlo.",
                    "category", claim.category()));
            if (!saved) owner.sendMessage(Settings.message("slayer", "SlayerModule.guardado-pendiente",
                    "<red>El estado de Slayer no pudo guardarse. Avisa a administración antes de reiniciar."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent e) {
        UUID victim = e.getEntity().getUniqueId();
        BossClaim claim = activeBosses.remove(victim);
        if (claim != null) {
            finishBoss(e, claim);
            return;
        }
        // Do not count MythicMobs (including unnamed summons) as ordinary kills.
        if (isMythicMob(e.getEntity()) || e.getEntity().getCustomName() != null
                || e.getEntity().getScoreboardTags().contains("SLAYER_NO_PROGRESS")) return;
        Player p = e.getEntity().getKiller();
        if (p == null || !p.isOnline()) return;
        for (String category : CATEGORIES) {
            Mission m = mission(p.getUniqueId(), category);
            if (!m.active || m.bossId != null || !e.getEntityType().name().equals(config.getString("categorias." + category + ".entidad"))) continue;
            m.recordKill(); dirty = true; refreshBar(p);
            if (m.kills % Math.max(1,config.getInt("avisos.cada-bajas",10)) == 0 && m.kills < m.required)
                p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-013",
                        "§6MISIONES §e{name-category} §f{m-kills}/{m-required}",
                        "name-category", name(category), "m-kills", m.kills, "m-required", m.required));
            if (m.ready()) summon(p, category, m);
        }
    }

    private boolean isMythicMob(Entity entity) {
        var mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled()) return entity.hasMetadata("MythicMob");
        try {
            Class<?> mythicClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true,
                    mythic.getClass().getClassLoader());
            Object instance = mythicClass.getMethod("inst").invoke(null);
            Object helper = instance.getClass().getMethod("getAPIHelper").invoke(instance);
            Object checked = helper.getClass().getMethod("isMythicMob", Entity.class).invoke(helper, entity);
            return Boolean.TRUE.equals(checked);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            if (!mythicDetectionWarned) {
                mythicDetectionWarned = true;
                plugin.getLogger().warning("Slayer: no se pudo consultar MythicMobs; no se contarán bajas hasta reparar la integración: " + failure);
            }
            return true; // Fail closed: avoids farming summons when detection is unavailable.
        }
    }
    private void finishBoss(EntityDeathEvent event, BossClaim claim) {
        Mission current = mission(claim.owner(), claim.category());
        if (!current.active || !event.getEntity().getUniqueId().equals(current.bossId)) return;
        Player owner = Bukkit.getPlayer(claim.owner());
        int radius = Math.max(1, Math.min(256, config.getInt("participacion.radio-bloques", 48)));
        double threshold = Math.max(0, Math.min(100, config.getDouble("participacion.minimo-porcentaje-vida", 10)));
        boolean present = owner != null && owner.isOnline()
                && owner.getWorld().equals(event.getEntity().getWorld())
                && owner.getLocation().distanceSquared(event.getEntity().getLocation()) <= radius * radius;
        double bossHealth = current.bossMaxHealth > 0 ? current.bossMaxHealth
                : event.getEntity().getMaxHealth();
        boolean victory = SlayerCombatRules.enoughParticipation(
                current.ownerDamage, bossHealth, threshold, present);
        current.active = false;
        clearBoss(current);
        current.kills = 0;

        int gained = 0, previousLevel = 0, newLevel = 0;
        if (victory && "revenant".equals(claim.category())) {
            SlayerRecord progress = revenant(claim.owner());
            previousLevel = level(claim.owner());
            gained = xpFromBoss(claim.tier());
            progress.totalXp = Math.min(1_000_000_000L, progress.totalXp + gained);
            progress.victories = Math.min(Integer.MAX_VALUE, progress.victories + 1);
            newLevel = level(claim.owner());
        }

        dirty = true;
        boolean persisted = save(); // Do not grant permissions before state becomes durable.
        if (owner != null) {
            refreshBar(owner);
            if (!persisted) owner.sendMessage(Settings.message("slayer", "SlayerModule.guardado-pendiente",
                    "<red>El estado de Slayer no pudo guardarse. Avisa a administración antes de reiniciar."));
            else if (victory) owner.sendMessage(Settings.message("slayer", "SlayerModule.victoria-cooperativa",
                    "<green>¡Jefe derrotado! <gold>+{xp} XP Slayer <gray>· Nivel {nivel}",
                    "xp", gained, "nivel", newLevel));
            else owner.sendMessage(Settings.message("slayer", "SlayerModule.victoria-no-acreditada",
                    "<red>No recibiste XP Slayer: necesitas infligir al menos {porcentaje}% de la vida del jefe y estar a {radio} bloques al morir.",
                    "porcentaje", threshold, "radio", radius));
        }
        if (persisted && victory && newLevel > previousLevel)
            unlocks.synchronize(claim.owner(), newLevel);
    }

    private void summon(Player p, String category, Mission m) {
        if (m.bossId != null || !m.ready()) return; // Exactly one registered boss per contract.
        long now = System.currentTimeMillis();
        if (now - attempts.getOrDefault(p.getUniqueId(), 0L)
                < Math.max(0, config.getLong("invocacion.reintento-milisegundos", 5000))) return;
        attempts.put(p.getUniqueId(), now);
        String mob = config.getString("categorias." + category + ".mob", "")
                .replace("{tier}", Integer.toString(m.tier));
        Entity spawned = null;
        try {
            var mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mythic != null && mythic.isEnabled()) {
                Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, mythic.getClass().getClassLoader());
                Object instance = api.getMethod("inst").invoke(null);
                Object helper = api.getMethod("getAPIHelper").invoke(instance);
                Object result = helper.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class)
                        .invoke(helper, mob, p.getLocation(), config.getInt("invocacion.nivel-mob",1));
                if (result instanceof Entity entity && entity.isValid()) spawned = entity;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            plugin.getLogger().warning(Settings.plain("slayer", "SlayerModule.mensaje-014",
                    "Slayer: no se pudo invocar {mob}: {error-getmessage}",
                    "mob", mob, "error-getmessage", error.getMessage()));
        }
        if (spawned == null) {
            p.sendMessage(Settings.message("slayer", "SlayerModule.mensaje-015",
                    "§cNo se pudo invocar {mob}. Tu objetivo se conserva. Reintenta con /spectrecore slayer invocar {category}",
                    "mob", mob, "category", category));
            return;
        }
        UUID bossId = spawned.getUniqueId();
        spawned.getPersistentDataContainer().set(bossMarker, PersistentDataType.STRING,
                p.getUniqueId() + ":" + category);
        m.bossId = bossId;
        m.ownerDamage = 0;
        m.bossMaxHealth = spawned instanceof LivingEntity living
                ? Math.max(living.getMaxHealth(), living.getHealth()) : 0;
        rememberPosition(m, spawned);
        activeBosses.put(bossId, new BossClaim(p.getUniqueId(), category, m.tier));
        dirty = true;
        if (!save()) {
            activeBosses.remove(bossId);
            clearBoss(m);
            spawned.remove();
            p.sendMessage("§cNo se pudo guardar el jefe. La invocación fue cancelada; el progreso se conserva.");
            return;
        }
        refreshBar(p);
        p.showTitle(Title.title(
                Settings.component(Settings.text("slayer", "SlayerModule.mensaje-016",
                        "¡JEFE INVOCADO!"), Settings.color("slayer", "SlayerModule.color-002", NamedTextColor.GOLD)),
                Settings.component(Settings.text("slayer", "SlayerModule.mensaje-017",
                        "{name-category} ha aparecido. ¡Derrota al jefe!", "name-category", name(category)),
                        Settings.color("slayer", "SlayerModule.color-003", NamedTextColor.YELLOW)),
                Title.Times.times(
                        Duration.ofMillis(Settings.i("slayer", "SlayerModule.ofmillis-003-arg0", 150)),
                        Duration.ofSeconds(Settings.i("slayer", "SlayerModule.ofseconds-004-arg0", 3)),
                        Duration.ofMillis(Settings.i("slayer", "SlayerModule.ofmillis-005-arg0", 400)))));
        p.playSound(p.getLocation(), Settings.sound("slayer", "SlayerModule.sound-004", Sound.ENTITY_WITHER_SPAWN),
                Settings.f("slayer", "SlayerModule.playsound-006-arg2", 1),
                Settings.f("slayer", "SlayerModule.playsound-007-arg3", .8f));
        Location center = spawned.getLocation().add(0, .15, 0);
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            center.getWorld().spawnParticle(Settings.particle("slayer", "SlayerModule.particle-005", Particle.SOUL_FIRE_FLAME),
                    center.clone().add(Math.cos(angle) * 1.4, 0, Math.sin(angle) * 1.4),
                    Settings.i("slayer", "SlayerModule.spawnparticle-008-arg2", 1),
                    Settings.d("slayer", "SlayerModule.spawnparticle-009-arg3", 0),
                    Settings.d("slayer", "SlayerModule.spawnparticle-010-arg4", 0),
                    Settings.d("slayer", "SlayerModule.spawnparticle-011-arg5", 0),
                    Settings.d("slayer", "SlayerModule.spawnparticle-012-arg6", .02));
        }
    }

    private void refreshBar(Player p) {
        hideBar(p);
        for (String c : CATEGORIES) { Mission m = mission(p.getUniqueId(), c); if (!m.active) continue;
            String title = m.bossId == null
                    ? Settings.text("slayer", "SlayerModule.mensaje-018", "{name-c} · {m-kills}/{m-required}",
                            "name-c", name(c), "m-kills", m.kills, "m-required", m.required)
                    : "§c" + name(c) + " §7· Jefe activo";
            float percent = m.bossId == null ? Math.min(1f, (float)m.kills / Math.max(1,m.required)) : 1f;
            BossBar bar = BossBar.bossBar(Settings.component(title), percent,
                    c.equals("revenant") ? BossBar.Color.RED : c.equals("spider") ? BossBar.Color.GREEN : BossBar.Color.PURPLE,
                    BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), bar); p.showBossBar(bar); break;
        }
    }
    private void hideBar(Player p) { BossBar bar = bars.remove(p.getUniqueId()); if (bar != null) p.hideBossBar(bar); }
    @EventHandler public void quit(PlayerQuitEvent e) { hideBar(e.getPlayer()); if (dirty) save(); }
    @EventHandler public void join(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                refreshBar(e.getPlayer());
                for (String category : CATEGORIES) {
                    Mission active = mission(e.getPlayer().getUniqueId(), category);
                    if (!active.active || active.bossId == null
                            || Bukkit.getEntity(active.bossId) != null) continue;
                    e.getPlayer().sendMessage(Settings.message("slayer", "SlayerModule.jefe-descargado",
                            "<yellow>Tu jefe sigue registrado, pero no está cargado. Regresa a la zona del combate; no invoques otro."));
                    if (active.bossWorld != null) try {
                        World world = Bukkit.getWorld(UUID.fromString(active.bossWorld));
                        if (world != null) e.getPlayer().sendMessage(Settings.message("slayer", "SlayerModule.jefe-ultima-posicion",
                                "<gray>Última ubicación conocida: <white>{world} {x} {y} {z}",
                                "world", world.getName(), "x", active.bossX, "y", active.bossY, "z", active.bossZ));
                    } catch (IllegalArgumentException invalid) {
                        plugin.getLogger().warning("Slayer: invalid boss-world UUID for " + e.getPlayer().getUniqueId());
                    }
                }
                unlocks.synchronize(e.getPlayer().getUniqueId(), level(e.getPlayer().getUniqueId()));
            }
        }, 1);
    }
    private void load() {
        var data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "slayer-data.yml"));
        for (String id : data.getKeys(false)) try {
            UUID uuid = UUID.fromString(id);
            for (String category : CATEGORIES) {
                var section = data.getConfigurationSection(id + "." + category);
                if (section == null) continue;
                Mission m = mission(uuid, category);
                m.tier = Math.max(0, Math.min(3, section.getInt("tier",0)));
                m.required = m.tier == 0 ? 0 : Math.max(1, section.getInt("required", ProgressRules.required(m.tier)));
                m.kills = Math.max(0, Math.min(m.required, section.getInt("kills")));
                m.active = m.tier > 0 && section.getBoolean("active");
                String storedId = section.getString("boss-id");
                if (m.active && storedId != null) try {
                    m.bossId = UUID.fromString(storedId);
                    double max = section.getDouble("boss-max-health", 0);
                    double damage = section.getDouble("boss-owner-damage", 0);
                    m.bossMaxHealth = Double.isFinite(max) && max > 0 ? max : 0;
                    m.ownerDamage = Double.isFinite(damage) && damage >= 0 ? damage : 0;
                    m.bossWorld = section.getString("boss-world");
                    m.bossX = section.getInt("boss-x");
                    m.bossY = section.getInt("boss-y");
                    m.bossZ = section.getInt("boss-z");
                    activeBosses.put(m.bossId, new BossClaim(uuid, category, m.tier));
                } catch (IllegalArgumentException invalid) {
                    plugin.getLogger().warning("Slayer: UUID de jefe inválido para " + uuid);
                }
            }
            var experience = data.getConfigurationSection(id + ".revenant-progression");
            if (experience != null) {
                SlayerRecord progress = revenant(uuid);
                progress.totalXp = Math.max(0L, Math.min(1_000_000_000L, experience.getLong("xp", 0)));
                progress.victories = Math.max(0, experience.getInt("victorias", 0));
            }
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning(Settings.plain("slayer", "SlayerModule.mensaje-019",
                    "UUID inválido en slayer-data.yml: {id}", "id", id));
        }
    }

    private boolean save() {
        var data = new YamlConfiguration();
        missions.forEach((id, values) -> values.forEach((category, mission) -> {
            String root = id + "." + category;
            data.set(root + ".tier", mission.tier);
            data.set(root + ".kills", mission.kills);
            data.set(root + ".required", mission.required);
            data.set(root + ".active", mission.active);
            data.set(root + ".boss-id", mission.bossId == null ? null : mission.bossId.toString());
            data.set(root + ".boss-max-health", mission.bossId == null ? null : mission.bossMaxHealth);
            data.set(root + ".boss-owner-damage", mission.bossId == null ? null : mission.ownerDamage);
            data.set(root + ".boss-world", mission.bossId == null ? null : mission.bossWorld);
            data.set(root + ".boss-x", mission.bossId == null ? null : mission.bossX);
            data.set(root + ".boss-y", mission.bossId == null ? null : mission.bossY);
            data.set(root + ".boss-z", mission.bossId == null ? null : mission.bossZ);
        }));
        revenantRecords.forEach((id, progress) -> {
            data.set(id + ".revenant-progression.xp", progress.totalXp);
            data.set(id + ".revenant-progression.victorias", progress.victories);
        });
        if (YamlStore.save(plugin, "slayer-data.yml", data)) { dirty = false; return true; }
        return false;
    }
}
