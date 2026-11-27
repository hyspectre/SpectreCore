package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.activities.ActivityRules;
import dev.hydev.spectrecore.mining.PlacedOres;
import dev.hydev.spectrecore.mining.MiningProgression;
import dev.hydev.spectrecore.guide.JobsMenu;
import dev.hydev.spectrecore.guide.SupplyGuide;
import dev.hydev.spectrecore.guide.MinerWorkshopMenu;
import dev.hydev.spectrecore.integration.LuckPermsContractBridge;
import dev.hydev.spectrecore.storage.ClaimJournal;
import dev.hydev.spectrecore.treasure.CombatTreasureRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class JobsModule implements Listener {
    private final JavaPlugin plugin; private final EconomyBridge economy = new EconomyBridge(); private final PlacedOres placed;
    private YamlConfiguration config; private final Map<String, Long> last = new HashMap<>(), messages = new HashMap<>();
    private final Set<String> warned = new HashSet<>(); private BukkitTask timer; private boolean dirty;
    private final PlacedOres previousMining, previousCrops;
    private LuckPermsContractBridge permissions;
    private JobsMenu menu;
    private SupplyGuide supplies;
    private ClaimJournal purchaseJournal;
    private SupplyOrders supplyOrders;
    private MinerWorkshop minerWorkshop;
    private MinerWorkshopMenu minerWorkshopMenu;
    private boolean storageLoaded;
    private final Set<UUID> purchasesInProgress = new HashSet<>();
    private final Map<UUID, Set<String>> confirmedRanks = new HashMap<>();
    private final Map<BlockBreakEvent, Boolean> provenance = new WeakHashMap<>();
    public JobsModule(JavaPlugin plugin) {
        this.plugin = plugin; placed = new PlacedOres(plugin, "jobs_placed_v1", m -> dev.hydev.spectrecore.config.Gameplay.tier("trabajos-mineria", m.name()) > 0 || dev.hydev.spectrecore.config.Gameplay.tier("agricultura", m.name()) > 0);
        previousMining = new PlacedOres(plugin);
        previousCrops = new PlacedOres(plugin, "placed_crop_blocks_v1", m -> false);
    }
    public void enable() {
        reload();
        var data = new YamlConfiguration();
        File source = new File(plugin.getDataFolder(), "jobs-data.yml");
        try {
            if (source.exists()) {
                if (!source.isFile() || source.length() > 8_000_000L) throw new IOException("Archivo de trabajos inválido");
                data.load(source); // Strict parse; never silently replace corrupted progress.
            }
            for (String job : List.of("minero", "granjero", "cazador")) {
                var section = data.getConfigurationSection(job);
                if (data.contains(job) && section == null) throw new IOException("Sección de trabajo inválida: " + job);
                if (section != null) for (String id : section.getKeys(false)) {
                    UUID.fromString(id);
                    long timestamp = section.getLong(id, -1L);
                    if (timestamp < 0) throw new IOException("Tiempo de trabajo inválido: " + job + "." + id);
                    last.put(job + "." + id, timestamp);
                }
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Trabajos: no se cargarán datos dañados ni se sobrescribirá jobs-data.yml", failure);
        }
        purchaseJournal = new ClaimJournal(new File(plugin.getDataFolder(), "data/jobs/purchases.yml").toPath());
        if (!purchaseJournal.load()) throw new IllegalStateException("Trabajos: registro de compras ilegible; revisar data/jobs/purchases.yml");
        supplyOrders = new SupplyOrders(plugin, this, purchaseJournal);
        minerWorkshop = new MinerWorkshop(plugin, this, purchaseJournal);
        for (ClaimJournal.Claim pending : purchaseJournal.unresolved())
            plugin.getLogger().severe("TRABAJO PENDIENTE: transacción=" + pending.id() + " jugador="
                    + pending.player() + " contrato=" + pending.subject() + "; verificar saldo y permiso antes de resolver.");
        storageLoaded = true;
        Bukkit.getPluginManager().registerEvents(this, plugin); Bukkit.getPluginManager().registerEvents(placed, plugin);
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try { permissions = new LuckPermsContractBridge(plugin); }
            catch (LinkageError error) { plugin.getLogger().warning("LuckPerms API no disponible: compras de trabajo deshabilitadas."); }
        }
        menu = new JobsMenu(plugin, this);
        menu.enable();
        supplies = new SupplyGuide(plugin, this);
        supplies.enable();
        minerWorkshopMenu = new MinerWorkshopMenu(plugin, this, minerWorkshop);
        minerWorkshopMenu.enable();
        if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("trabajo") != null) dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("trabajo").setExecutor((sender, cmd, label, args) -> {
            if (args.length > 0 && (args[0].equalsIgnoreCase("transacciones") || args[0].equalsIgnoreCase("resolver"))) {
                return purchaseAdmin(sender, args);
            }
            if (!(sender instanceof Player p)) { sender.sendMessage(Settings.message("trabajos", "JobsModule.mensaje-001", "Usa /spectrecore trabajos dentro del juego.")); return true; }
            if (args.length > 0 && args[0].equalsIgnoreCase("abastecimiento")) return openSupplies(p);
            if (args.length > 0 && args[0].equalsIgnoreCase("taller")) return openMinerWorkshop(p);
            if (args.length == 0 || !args[0].equalsIgnoreCase("estado")) {
                String page = args.length > 0 && JobsContractRules.JOBS.contains(args[0].toLowerCase(Locale.ROOT))
                        ? args[0].toLowerCase(Locale.ROOT) : "trabajos";
                if (menu != null) menu.open(p, page);
                return true;
            }
            p.sendMessage(Settings.message("trabajos", "JobsModule.mensaje-002", "§6✦ NIVELES DE TRABAJO"));
            for (String job : List.of("minero", "granjero", "cazador")) { int tier = rank(p, job); p.sendMessage(Settings.message("trabajos", "JobsModule.mensaje-003","§7▪ §f{job}: {valor2}","job", displayJob(job),"valor2", (tier == 0 ? Settings.component(Settings.text("trabajos", "JobsModule.texto-002", "§cNo disponible")) : Settings.component(Settings.text("trabajos", "JobsModule.texto-003", "§e") + displayRank(ProgressRules.rank(tier)))))); }
            p.sendMessage(Settings.message("trabajos", "JobsModule.mensaje-004", "§7Mejora tu nivel firmando §6contratos§7.")); return true;
        });
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200, 200);
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "trabajos/config.yml");
        warned.clear();
        if (menu != null) menu.reload();
        if (supplies != null) supplies.reload();
        if (minerWorkshopMenu != null) minerWorkshopMenu.reload();
    }
    public void disable() {
        if (timer != null) timer.cancel();
        if (minerWorkshopMenu != null) { minerWorkshopMenu.disable(); minerWorkshopMenu = null; }
        if (supplies != null) { supplies.disable(); supplies = null; }
        if (menu != null) { menu.disable(); menu = null; }
        HandlerList.unregisterAll(this); HandlerList.unregisterAll(placed); if (storageLoaded) save();
        if (purchaseJournal != null && purchaseJournal.count() > 0)
            plugin.getLogger().severe("TRABAJOS: existen " + purchaseJournal.count() + " transacciones que necesitan reconciliación.");
        if (!purchasesInProgress.isEmpty())
            plugin.getLogger().warning("TRABAJOS: Hay " + purchasesInProgress.size() + " compra(s) LuckPerms pendiente(s) al apagar; revisar saldos si hubo errores.");
    }
    public boolean openSupplies(Player player) { return supplies != null && supplies.open(player); }
    public boolean openMinerWorkshop(Player player) { return minerWorkshopMenu != null && minerWorkshopMenu.open(player); }
    public void fulfillMithrilOrder(Player player) {
        if (supplyOrders != null) supplyOrders.fulfill(player);
    }
    public SupplyOrders supplyOrders() { return supplyOrders; }
    public boolean openOverview(Player player) { return menu != null && menu.open(player, "trabajos"); }

    public String displayJob(String id) {
        String fallback = switch (id) {
            case "minero" -> "Minería";
            case "granjero" -> "Agricultura";
            case "cazador" -> "Caza";
            default -> id;
        };
        return config.getString("presentacion.trabajos." + id, fallback);
    }
    public String displayRank(String id) {
        String fallback = switch (id) {
            case "novato" -> "Novato";
            case "experimentado" -> "Experimentado";
            case "profesional" -> "Profesional";
            default -> id;
        };
        return config.getString("presentacion.rangos." + id, fallback);
    }
    private void save() { var yaml = new YamlConfiguration(); last.forEach(yaml::set); if (YamlStore.save(plugin, "jobs-data.yml", yaml)) dirty = false; }
    public int rank(Player p, String job) {
        for (int tier = 3; tier >= 1; tier--) if (p.hasPermission("trabajo." + job + "." + ProgressRules.rank(tier))) return tier;
        return 0;
    }
    public boolean isPending(Player p) { return purchasesInProgress.contains(p.getUniqueId()); }

    /** Reconciliation requires manual verification of both the Vault balance and LuckPerms rank. */
    private boolean purchaseAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spectrecore.admin")) {
            sender.sendMessage(Settings.component("<red>Sin permiso.")); return true;
        }
        if (purchaseJournal == null || !purchaseJournal.ready()) {
            sender.sendMessage(Settings.component("<red>Registro de compras no disponible.")); return true;
        }
        if (args[0].equalsIgnoreCase("transacciones")) {
            sender.sendMessage(Settings.component("<gold>Transacciones pendientes: " + purchaseJournal.count()));
            for (ClaimJournal.Claim claim : purchaseJournal.unresolved())
                sender.sendMessage(Settings.component("<yellow>" + claim.id() + "<gray> · " + claim.player()
                        + " · " + claim.subject()));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(Settings.component("<yellow>Uso: /spectrecore trabajos resolver <uuid>")); return true;
        }
        try {
            UUID token = UUID.fromString(args[1]);
            if (purchaseJournal.unresolved().stream().noneMatch(claim -> claim.id().equals(token))) {
                sender.sendMessage(Settings.component("<red>No existe esa transacción.")); return true;
            }
            if (purchaseJournal.resolve(token)) {
                plugin.getLogger().warning("TRABAJO: transacción reconciliada manualmente por " + sender.getName() + ": " + token);
                sender.sendMessage(Settings.component("<green>Registro resuelto. Verifica previamente inventario, saldo y permisos."));
            } else sender.sendMessage(Settings.component("<red>No se pudo registrar la resolución."));
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(Settings.component("<red>UUID inválido."));
        }
        return true;
    }

    private void endPurchase(UUID claim, String context) {
        if (purchaseJournal == null || !purchaseJournal.finish(claim))
            plugin.getLogger().severe("TRABAJO: transacción no finalizada " + claim + " (" + context + "); requiere revisión.");
    }

    private void refundPurchase(Player player, long price, UUID claim, String context) {
        if (economy.deposit(player, price, false) == EconomyBridge.Result.CONFIRMED) endPurchase(claim, context);
        else plugin.getLogger().severe("TRABAJO: reembolso sin confirmar " + claim + " jugador="
                + player.getUniqueId() + " monto=" + price + " (" + context + "); requiere conciliación.");
    }

    private Component contractRefusal(JobsContractRules.Refusal refusal, String level) {
        return switch (refusal) {
            case DISABLED -> Settings.message("trabajos", "JobsModule.contrato-no-disponible",
                    "<yellow>Este oficio no está disponible por ahora.");
            case INVALID_JOB -> Settings.message("trabajos", "JobsModule.contrato-oficio-invalido",
                    "<red>Elige un oficio válido en Trabajos.");
            case INVALID_RANK -> Settings.message("trabajos", "JobsModule.contrato-rango-invalido",
                    "<red>Elige un rango válido en Trabajos.");
            case ALREADY_OWNED -> Settings.message("trabajos", "JobsModule.contrato-ya-adquirido",
                    "<yellow>Ya tienes este rango o uno superior.");
            case PREVIOUS_RANK_REQUIRED -> Settings.message("trabajos", "JobsModule.contrato-rango-anterior",
                    "<yellow>Primero firma el contrato de rango <white>{rango}<yellow>.",
                    "rango", displayRank(ProgressRules.rank(JobsContractRules.tier(level) - 1)));
            case INVALID_PRICE -> Settings.message("trabajos", "JobsModule.contrato-precio-no-disponible",
                    "<red>Este contrato no está disponible por ahora. No se ha cobrado.");
            case IN_PROGRESS -> Settings.message("trabajos", "JobsModule.contrato-en-curso",
                    "<yellow>Tu contrato se está confirmando. Espera un momento.");
            case NONE -> Component.empty();
        };
    }

    /** Main-thread purchase entry point, shared by native UI and command routes. */
    public void purchase(Player player, String job, String level) {
        String priceKey = "contratos.precios." + level;
        Object raw = config.get(priceKey);
        long price = raw instanceof Number n && Double.isFinite(n.doubleValue())
                && n.doubleValue() == Math.rint(n.doubleValue()) ? n.longValue() : -1;
        boolean enabled = JobsContractRules.JOBS.contains(job);
        var result = JobsContractRules.validate(job, level, enabled,
                JobsContractRules.JOBS.contains(job) ? rank(player, job) : 0,
                price, isPending(player));
        // Guard against an LP save that has completed but whose Bukkit
        // permission cache has not reflected the new rank yet.
        if (result == JobsContractRules.Refusal.NONE
                && confirmedRanks.getOrDefault(player.getUniqueId(), Set.of()).contains(job + "." + level))
            result = JobsContractRules.Refusal.ALREADY_OWNED;
        if (result != JobsContractRules.Refusal.NONE) {
            player.sendMessage(Settings.message("trabajos", "JobsModule.contrato-rechazado",
                    "<red>{motivo}", "motivo", contractRefusal(result, level)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        if ("minero".equals(job)) {
            MiningProgression progression=new MiningProgression(plugin);
            String milestone=MiningProgression.contractStage(level);
            if (!progression.meets(player,milestone)) {
                player.sendMessage(progression.missingLevel(player,milestone)
                        ? Settings.message("trabajos","MiningProgression.requisito-contrato",
                            "<yellow>Necesitas Minería nivel {nivel} para firmar este contrato.",
                            "nivel",progression.required(milestone))
                        : Settings.component(progression.lockDetail(player,milestone)));
                return;
            }
        }
        if (permissions == null || !permissions.ready(player)) {
            player.sendMessage(Settings.message("trabajos", "JobsModule.contrato-sin-luckperms",
                    "§cNo se pudo registrar el contrato. No se ha cobrado."));
            return;
        }
        UUID id = player.getUniqueId();
        String station = "job:" + id;
        if (purchaseJournal == null || purchaseJournal.blocked(station)) {
            player.sendMessage(Settings.component("<red>Hay una compra pendiente de revisión. Avisa a administración."));
            return;
        }
        purchasesInProgress.add(id);
        UUID claim = purchaseJournal.begin(station, id, "JOB_PURCHASE", job + ":" + level + ":" + price, 1);
        if (claim == null) {
            purchasesInProgress.remove(id);
            player.sendMessage(Settings.component("<red>No se pudo registrar el contrato. No se ha cobrado."));
            return;
        }
        if (economy.withdraw(player, price) != EconomyBridge.Result.CONFIRMED) {
            purchasesInProgress.remove(id);
            endPurchase(claim, "cobro no confirmado");
            player.sendMessage(Settings.message("trabajos", "JobsModule.contrato-sin-saldo",
                    "§cNo se pudo confirmar el pago."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        // All the permission work is performed via LuckPerms, never an OP console command.
        final java.util.concurrent.CompletableFuture<Boolean> grant;
        try { grant = permissions.grant(player, job, level); }
        catch (RuntimeException | LinkageError failure) {
            purchasesInProgress.remove(id);
            refundPurchase(player, price, claim, "excepción al conceder permiso");
            plugin.getLogger().severe("LUCKPERMS CONTRACT ERROR AFTER CHARGE " + id + ": " + failure);
            return;
        }
        grant.whenComplete((saved, error) -> {
            if (!plugin.isEnabled()) {
                plugin.getLogger().severe("TRABAJO PENDIENTE DE RECONCILIACION AL CERRAR: " + id
                        + " " + job + " " + level + " pagado=" + price);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                purchasesInProgress.remove(id);
                if (error != null || !Boolean.TRUE.equals(saved)) {
                    refundPurchase(player, price, claim, "concesión de permiso fallida");
                    if (player.isOnline()) player.sendMessage(Settings.message("trabajos", "JobsModule.contrato-fallo",
                            "§cNo se pudo guardar el contrato. Se intentó devolver el pago."));
                    return;
                }
                endPurchase(claim, "permiso concedido");
                confirmedRanks.computeIfAbsent(id, key -> new HashSet<>()).add(job + "." + level);
                plugin.getLogger().info("TRABAJO CONTRATADO: " + id + " " + job + "." + level + " monto=" + price);
                if (player.isOnline()) {
                    player.recalculatePermissions();
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                    player.sendMessage(Settings.message("trabajos", "JobsModule.contrato-exito",
                            "§aContrato firmado: {trabajo} {rango}. Precio: {precio} ↾.",
                            "trabajo", displayJob(job), "rango", displayRank(level), "precio", price));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        if (dev.hydev.spectrecore.mining.MithrilAutomationGuard.active()) return;
        boolean wasPlaced = placed.consume(e.getBlock());
        boolean alreadyKnown = Boolean.TRUE.equals(provenance.remove(e));
        if (wasPlaced || alreadyKnown) return;
        int ore = dev.hydev.spectrecore.config.Gameplay.tier("trabajos-mineria", e.getBlock().getType().name());
        if (ore > 0) pay(e.getPlayer(), "minero", ore, e.getBlock().getLocation().add(.5,.5,.5));
        else { int crop = dev.hydev.spectrecore.config.Gameplay.tier("agricultura", e.getBlock().getType().name()); if (crop > 0) pay(e.getPlayer(), "granjero", crop, e.getBlock().getLocation().add(.5,.5,.5)); }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void provenance(BlockBreakEvent e) {
        if (dev.hydev.spectrecore.config.Gameplay.tier("trabajos-mineria", e.getBlock().getType().name()) > 0 || dev.hydev.spectrecore.config.Gameplay.tier("agricultura", e.getBlock().getType().name()) > 0)
            provenance.put(e, placed.isMarked(e.getBlock()) || previousMining.isMarked(e.getBlock()) || previousCrops.isMarked(e.getBlock()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller(); int tier = dev.hydev.spectrecore.config.Gameplay.tier("combate", e.getEntityType().name());
        if (p != null && p.isOnline() && tier > 0) pay(p, "cazador", tier, e.getEntity().getLocation());
    }
    /** Time until this activity tier can pay again; -1 means no configured/eligible payment. */
    public long paymentWaitMillis(Player player, String job, int tier) {
        if (!JobsContractRules.JOBS.contains(job) || tier < 1 || tier > 3 || rank(player, job) < tier) return -1;
        Object raw = config.get("cooldowns." + job + "." + ProgressRules.rank(tier));
        if (!(raw instanceof Number seconds) || !Double.isFinite(seconds.doubleValue())
                || seconds.doubleValue() < 0) return -1;
        return paymentWaitMillis(player.getUniqueId(), job, System.currentTimeMillis(),
                (long) (seconds.doubleValue() * 1000));
    }
    private long paymentWaitMillis(UUID player, String job, long now, long cooldownMillis) {
        return ProgressRules.remaining(last.getOrDefault(job + "." + player, -1L), now, cooldownMillis);
    }

    private void pay(Player p, String job, int tier, Location location) {
        if (rank(p, job) < tier) return;
        String level = ProgressRules.rank(tier), root = job + "." + level + ".dinero", cdKey = "cooldowns." + job + "." + level;
        if (!(config.get(root + ".minimo") instanceof Number minimum) || !(config.get(root + ".maximo") instanceof Number maximum) || !(config.get(cdKey) instanceof Number)) { missing(job, level); return; }
        long min = config.getLong(root + ".minimo", -1), max = config.getLong(root + ".maximo", -1);
        double cooldown = config.getDouble(cdKey, -1);
        if (minimum.doubleValue()!=min || maximum.doubleValue()!=max || min < 0 || max < min || max > Integer.MAX_VALUE || !Double.isFinite(cooldown) || cooldown < 0) { missing(job, level); return; }
        String key = job + "." + p.getUniqueId(); long now = System.currentTimeMillis();
        long remaining = paymentWaitMillis(p.getUniqueId(), job, now, (long) (cooldown * 1000));
        // Cooldown remains enforced; it is shown in the Jobs GUI, not the actionbar.
        if (remaining > 0) return;
        long amount = ThreadLocalRandom.current().nextLong(min, max + 1);
        var core = (dev.hydev.spectrecore.SpectreCorePlugin) plugin;
        if (core.relicario() != null) amount = core.relicario().addJobCoins(p, amount);
        var result = economy.deposit(p, amount, config.getBoolean("economia.permitir-comando-eco", true));
        if (result == EconomyBridge.Result.FAILED) { if (warned.add("economy")) plugin.getLogger().warning(Settings.plain("trabajos", "JobsModule.mensaje-006", "Trabajos: no se pudo confirmar el pago. No se aplicó el tiempo de espera.")); return; }
        last.put(key, now); dirty = true;
        location.getWorld().spawnParticle(Settings.particle("trabajos", "JobsModule.particle-002", Particle.GLOW), location.clone().add(0,.5,0),Settings.i("trabajos", "JobsModule.spawnparticle-001-arg2", 10),Settings.d("trabajos", "JobsModule.spawnparticle-002-arg3", .3),Settings.d("trabajos", "JobsModule.spawnparticle-003-arg4", .3),Settings.d("trabajos", "JobsModule.spawnparticle-004-arg5", .3),Settings.d("trabajos", "JobsModule.spawnparticle-005-arg6", .05));
        String paymentMessage = Settings.text("trabajos", "JobsModule.mensaje-007","{valor1}+{amount} {prefijo} · {job}","valor1", (result == EconomyBridge.Result.COMMAND_SENT ? Settings.text("trabajos", "JobsModule.texto-004", "Pago solicitado: ") : ""),"amount", amount,"job", displayJob(job),"prefijo", Settings.text("trabajos", "JobsModule.prefijo-monedas", "§f↾"));
        boolean toasted = result == EconomyBridge.Result.CONFIRMED
                && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts() != null
                && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts().show(p, "trabajo-" + job);
        if (toasted) p.sendMessage(Settings.component(paymentMessage)); // Preserve the exact amount.
        else {
            p.playSound(p.getLocation(), Settings.sound("trabajos", "JobsModule.sound-003", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("trabajos", "JobsModule.playsound-006-arg2", .5f),Settings.f("trabajos", "JobsModule.playsound-007-arg3", 1.5f));
            p.sendMessage(Settings.component(paymentMessage, Settings.color("trabajos", "JobsModule.color-004", NamedTextColor.GOLD)));
        }
    }
    private void missing(String job, String level) { if (warned.add(job + "." + level)) plugin.getLogger().warning(Settings.plain("trabajos", "JobsModule.mensaje-008", "Trabajos: faltan o son inválidos dinero/cooldown de {job}.{level}. Esa categoría no paga hasta configurarse.", "job", job, "level", level)); }
}
