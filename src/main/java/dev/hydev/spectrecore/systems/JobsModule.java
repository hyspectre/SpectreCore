package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.activities.ActivityRules;
import dev.hydev.spectrecore.mining.PlacedOres;
import dev.hydev.spectrecore.guide.JobsMenu;
import dev.hydev.spectrecore.integration.LuckPermsContractBridge;
import dev.hydev.spectrecore.treasure.CombatTreasureRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class JobsModule implements Listener {
    private final JavaPlugin plugin; private final EconomyBridge economy = new EconomyBridge(); private final PlacedOres placed;
    private YamlConfiguration config; private final Map<String, Long> last = new HashMap<>(), messages = new HashMap<>();
    private final Set<String> warned = new HashSet<>(); private BukkitTask timer; private boolean dirty;
    private final PlacedOres previousMining, previousCrops;
    private LuckPermsContractBridge permissions;
    private JobsMenu menu;
    private final Set<UUID> purchasesInProgress = new HashSet<>();
    private final Map<UUID, Set<String>> confirmedRanks = new HashMap<>();
    private final Map<BlockBreakEvent, Boolean> provenance = new WeakHashMap<>();
    public JobsModule(JavaPlugin plugin) {
        this.plugin = plugin; placed = new PlacedOres(plugin, "jobs_placed_v1", m -> dev.hydev.spectrecore.config.Gameplay.tier("trabajos-mineria", m.name()) > 0 || dev.hydev.spectrecore.config.Gameplay.tier("agricultura", m.name()) > 0);
        previousMining = new PlacedOres(plugin);
        previousCrops = new PlacedOres(plugin, "placed_crop_blocks_v1", m -> false);
    }
    public void enable() {
        reload(); var data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "jobs-data.yml"));
        for (String job : List.of("minero", "granjero", "cazador")) {
            var section = data.getConfigurationSection(job);
            if (section != null) for (String id : section.getKeys(false)) last.put(job + "." + id, section.getLong(id));
        }
        Bukkit.getPluginManager().registerEvents(this, plugin); Bukkit.getPluginManager().registerEvents(placed, plugin);
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try { permissions = new LuckPermsContractBridge(plugin); }
            catch (LinkageError error) { plugin.getLogger().warning("LuckPerms API no disponible: compras de trabajo deshabilitadas."); }
        }
        menu = new JobsMenu(plugin, this);
        menu.enable();
        if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("trabajo") != null) dev.hydev.spectrecore.command.CommandRouter.of(plugin).get("trabajo").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) { sender.sendMessage(Settings.text("trabajos", "JobsModule.mensaje-001", "Usa /spectrecore trabajos dentro del juego.")); return true; }
            if (args.length == 0 || !args[0].equalsIgnoreCase("estado")) {
                String page = args.length > 0 && JobsContractRules.JOBS.contains(args[0].toLowerCase(Locale.ROOT))
                        ? args[0].toLowerCase(Locale.ROOT) : "trabajos";
                if (menu != null) menu.open(p, page);
                return true;
            }
            p.sendMessage(Settings.text("trabajos", "JobsModule.mensaje-002", "§6✦ NIVELES DE TRABAJO"));
            for (String job : List.of("minero", "granjero", "cazador")) { int tier = rank(p, job); p.sendMessage(Settings.text("trabajos", "JobsModule.mensaje-003","§7▪ §f{job}: {valor2}","job", job,"valor2", (!dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).active("trabajos."+job)?Settings.text("trabajos", "JobsModule.texto-001", "§cDesactivado"):tier == 0 ? Settings.text("trabajos", "JobsModule.texto-002", "§cNo disponible") : Settings.text("trabajos", "JobsModule.texto-003", "§e") + ProgressRules.rank(tier)))); }
            p.sendMessage(Settings.text("trabajos", "JobsModule.mensaje-004", "§7Mejora tu nivel firmando §6contratos§7.")); return true;
        });
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200, 200);
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "trabajos/config.yml");
        warned.clear();
        if (menu != null) menu.reload();
    }
    public void disable() {
        if (timer != null) timer.cancel();
        if (menu != null) { menu.disable(); menu = null; }
        HandlerList.unregisterAll(this); HandlerList.unregisterAll(placed); save();
        if (!purchasesInProgress.isEmpty())
            plugin.getLogger().warning("TRABAJOS: Hay " + purchasesInProgress.size() + " compra(s) LuckPerms pendiente(s) al apagar; revisar saldos si hubo errores.");
    }
    private void save() { var yaml = new YamlConfiguration(); last.forEach(yaml::set); if (YamlStore.save(plugin, "jobs-data.yml", yaml)) dirty = false; }
    public int rank(Player p, String job) {
        for (int tier = 3; tier >= 1; tier--) if (p.hasPermission("trabajo." + job + "." + ProgressRules.rank(tier))) return tier;
        return 0;
    }
    public boolean isPending(Player p) { return purchasesInProgress.contains(p.getUniqueId()); }

    /** Main-thread purchase entry point, shared by native UI and command routes. */
    public void purchase(Player player, String job, String level) {
        String priceKey = "contratos.precios." + level;
        Object raw = config.get(priceKey);
        long price = raw instanceof Number n && Double.isFinite(n.doubleValue())
                && n.doubleValue() == Math.rint(n.doubleValue()) ? n.longValue() : -1;
        boolean enabled = config.getBoolean("contratos.habilitados", false)
                && dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).active("trabajos." + job);
        var result = JobsContractRules.validate(job, level, enabled,
                JobsContractRules.JOBS.contains(job) ? rank(player, job) : 0,
                price, isPending(player));
        // Guard against an LP save that has completed but whose Bukkit
        // permission cache has not reflected the new rank yet.
        if (result == JobsContractRules.Refusal.NONE
                && confirmedRanks.getOrDefault(player.getUniqueId(), Set.of()).contains(job + "." + level))
            result = JobsContractRules.Refusal.ALREADY_OWNED;
        if (result != JobsContractRules.Refusal.NONE) {
            player.sendMessage(Settings.text("trabajos", "JobsModule.contrato-rechazado",
                    "§cNo puedes firmar este contrato: {motivo}", "motivo", result.name()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        if (permissions == null || !permissions.ready(player)) {
            player.sendMessage(Settings.text("trabajos", "JobsModule.contrato-sin-luckperms",
                    "§cLuckPerms no está disponible; no se ha cobrado."));
            return;
        }
        UUID id = player.getUniqueId();
        purchasesInProgress.add(id);
        if (economy.withdraw(player, price) != EconomyBridge.Result.CONFIRMED) {
            purchasesInProgress.remove(id);
            player.sendMessage(Settings.text("trabajos", "JobsModule.contrato-sin-saldo",
                    "§cNo se pudo confirmar el pago mediante Vault."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
            return;
        }
        // All the permission work is performed via LuckPerms, never an OP console command.
        final java.util.concurrent.CompletableFuture<Boolean> grant;
        try { grant = permissions.grant(player, job, level); }
        catch (RuntimeException | LinkageError failure) {
            purchasesInProgress.remove(id);
            if (economy.deposit(player, price, false) != EconomyBridge.Result.CONFIRMED)
                plugin.getLogger().severe("REEMBOLSO DE TRABAJO FALLIDO: " + id
                        + " " + job + " " + level + " monto=" + price);
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
                    if (economy.deposit(player, price, false) != EconomyBridge.Result.CONFIRMED)
                        plugin.getLogger().severe("REEMBOLSO DE TRABAJO FALLIDO: " + id
                                + " " + job + " " + level + " monto=" + price);
                    if (player.isOnline()) player.sendMessage(Settings.text("trabajos", "JobsModule.contrato-fallo",
                            "§cNo se pudo guardar el contrato. Se intentó devolver el pago."));
                    return;
                }
                confirmedRanks.computeIfAbsent(id, key -> new HashSet<>()).add(job + "." + level);
                plugin.getLogger().info("TRABAJO CONTRATADO: " + id + " " + job + "." + level + " monto=" + price);
                if (player.isOnline()) {
                    player.recalculatePermissions();
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                    player.sendMessage(Settings.text("trabajos", "JobsModule.contrato-exito",
                            "§aContrato firmado: {trabajo} {rango}. Precio: {precio} ↾.",
                            "trabajo", job, "rango", level, "precio", price));
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
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
    private void pay(Player p, String job, int tier, Location location) {
        if(!dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).active("trabajos."+job))return;
        if (rank(p, job) < tier) return;
        String level = ProgressRules.rank(tier), root = job + "." + level + ".dinero", cdKey = "cooldowns." + job + "." + level;
        if (!(config.get(root + ".minimo") instanceof Number minimum) || !(config.get(root + ".maximo") instanceof Number maximum) || !(config.get(cdKey) instanceof Number)) { missing(job, level); return; }
        long min = config.getLong(root + ".minimo", -1), max = config.getLong(root + ".maximo", -1);
        double cooldown = config.getDouble(cdKey, -1);
        if (minimum.doubleValue()!=min || maximum.doubleValue()!=max || min < 0 || max < min || max > Integer.MAX_VALUE || !Double.isFinite(cooldown) || cooldown < 0) { missing(job, level); return; }
        String key = job + "." + p.getUniqueId(); long now = System.currentTimeMillis();
        long remaining = ProgressRules.remaining(last.getOrDefault(key, -1L), now, (long) (cooldown * 1000));
        if (remaining > 0) {
            if (now - messages.getOrDefault(key, 0L) >= Math.max(0,config.getLong("avisos.intervalo-cooldown-milisegundos",5000))) {
                p.sendActionBar(Settings.component(Settings.text("trabajos", "JobsModule.mensaje-005", "Espera {remaining-999-1000} s para otra recompensa de {job}", "remaining-999-1000", ((remaining + 999) / 1000), "job", job), Settings.color("trabajos", "JobsModule.color-001", NamedTextColor.RED))); messages.put(key, now);
            }
            return;
        }
        long amount = ThreadLocalRandom.current().nextLong(min, max + 1);
        var result = economy.deposit(p, amount, config.getBoolean("economia.permitir-comando-eco", true));
        if (result == EconomyBridge.Result.FAILED) { if (warned.add("economy")) plugin.getLogger().warning(Settings.text("trabajos", "JobsModule.mensaje-006", "Trabajos: no se pudo pagar; revisa Vault/economía o eco. No se consume cooldown.")); return; }
        last.put(key, now); dirty = true;
        location.getWorld().spawnParticle(Settings.particle("trabajos", "JobsModule.particle-002", Particle.GLOW), location.clone().add(0,.5,0),Settings.i("trabajos", "JobsModule.spawnparticle-001-arg2", 10),Settings.d("trabajos", "JobsModule.spawnparticle-002-arg3", .3),Settings.d("trabajos", "JobsModule.spawnparticle-003-arg4", .3),Settings.d("trabajos", "JobsModule.spawnparticle-004-arg5", .3),Settings.d("trabajos", "JobsModule.spawnparticle-005-arg6", .05));
        p.playSound(p.getLocation(), Settings.sound("trabajos", "JobsModule.sound-003", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("trabajos", "JobsModule.playsound-006-arg2", .5f),Settings.f("trabajos", "JobsModule.playsound-007-arg3", 1.5f));
        String paymentMessage = Settings.text("trabajos", "JobsModule.mensaje-007","{valor1}+{amount} ↾ · {job}","valor1", (result == EconomyBridge.Result.COMMAND_SENT ? Settings.text("trabajos", "JobsModule.texto-004", "Pago solicitado: ") : ""),"amount", amount,"job", job);
        boolean toasted = result == EconomyBridge.Result.CONFIRMED
                && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts() != null
                && ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).toasts().show(p, "trabajo-" + job);
        if (toasted) p.sendMessage(paymentMessage); // Preserve exact amount without another actionbar.
        else p.sendActionBar(Settings.component(paymentMessage, Settings.color("trabajos", "JobsModule.color-004", NamedTextColor.GOLD)));
    }
    private void missing(String job, String level) { if (warned.add(job + "." + level)) plugin.getLogger().warning(Settings.text("trabajos", "JobsModule.mensaje-008", "Trabajos: faltan o son inválidos dinero/cooldown de {job}.{level}. Esa categoría no paga hasta configurarse.", "job", job, "level", level)); }
}
