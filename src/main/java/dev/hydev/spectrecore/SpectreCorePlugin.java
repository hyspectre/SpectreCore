package dev.hydev.spectrecore;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.cooking.CookingModule;
import dev.hydev.spectrecore.core.Cooldowns;
import dev.hydev.spectrecore.core.Effects;
import dev.hydev.spectrecore.core.SetDetector;
import dev.hydev.spectrecore.core.PassiveBonuses;
import dev.hydev.spectrecore.core.SkillModule;
import dev.hydev.spectrecore.core.Targeting;
import dev.hydev.spectrecore.core.Visuals;
import dev.hydev.spectrecore.integration.AuraSkillsBridge;
import dev.hydev.spectrecore.integration.ToastService;
import dev.hydev.spectrecore.skills.AsterionSkill;
import dev.hydev.spectrecore.skills.BotaniaSkill;
import dev.hydev.spectrecore.skills.CoronaSkill;
import dev.hydev.spectrecore.skills.PrimerDragonSkill;
import dev.hydev.spectrecore.skills.UltimoJuramentoSkill;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Main-thread, single-authority dispatcher. The 5 modules may not recursively trigger each other. */
public final class SpectreCorePlugin extends JavaPlugin implements Listener, TabCompleter {
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, String> demo = new HashMap<>();
    private final List<SkillModule> modules = new ArrayList<>();
    private SetDetector sets;
    private Targeting targeting;
    private Visuals visuals;
    private Effects effects;
    private PassiveBonuses passive;
    private UltimoJuramentoSkill ultimo;
    private CookingModule cooking;
    private dev.hydev.spectrecore.migration.BossDamageModule bossDamage;
    private dev.hydev.spectrecore.mining.MiningModule mining;
    private dev.hydev.spectrecore.treasure.TreasureCooldowns treasureCooldowns;
    private dev.hydev.spectrecore.treasure.CombatTreasures combatTreasures;
    private dev.hydev.spectrecore.activities.AgricultureTreasures agricultureTreasures;
    private dev.hydev.spectrecore.activities.LegacyActivities activities;
    private dev.hydev.spectrecore.systems.JobsModule jobs;
    private dev.hydev.spectrecore.systems.SlayerModule slayer;
    private dev.hydev.spectrecore.systems.PortalModule portal;
    private dev.hydev.spectrecore.model.DisplayModels displayModels;
    private long tick;
    private final dev.hydev.spectrecore.diagnostics.DiagnosticsService diagnostics =
            new dev.hydev.spectrecore.diagnostics.DiagnosticsService(false);
    public dev.hydev.spectrecore.diagnostics.DiagnosticsService diagnostics() { return diagnostics; }
    private void reloadDiagnostics() {
        try {
            File file = new File(getDataFolder(), "diagnostics.yml");
            if (!file.exists()) saveResource("diagnostics.yml", false);
            if (!file.isFile() || file.length() > 8192) throw new IOException("diagnostics.yml inválido o demasiado grande");
            YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file);
            if (!yaml.isBoolean("enabled") || !yaml.isBoolean("show-on-command"))
                throw new IllegalArgumentException("diagnostics.yml requiere true/false");
            diagnostics.enabled(yaml.getBoolean("enabled"));
        } catch (Exception ex) {
            getLogger().warning("Diagnóstico: no se aplicó configuración inválida; se conserva el estado anterior: " + ex.getMessage());
        }
    }
    private dev.hydev.spectrecore.guide.MagoMenu mago;
    private dev.hydev.spectrecore.guide.ArmorGuide armorGuide;
    public dev.hydev.spectrecore.guide.ArmorGuide armors() { return armorGuide; }
    private dev.hydev.spectrecore.integration.IntegrationRegistry integrations;
    private AuraSkillsBridge auraSkillsBridge;
    private ToastService toastService;
    public dev.hydev.spectrecore.integration.IntegrationRegistry integrations() { return integrations; }
    public AuraSkillsBridge auraSkillsBridge() { return auraSkillsBridge; }
    public ToastService toasts() { return toastService; }
    private boolean applyingSkillDamage;
    private record CombatTrace(String attacker, String enemy, double original, double requested, double factor) { }
    private final java.util.Map<EntityDamageByEntityEvent, CombatTrace> combatTraces = new java.util.WeakHashMap<>();
    private final java.util.Map<UUID, Long> combatLogLast = new HashMap<>();
    private dev.hydev.spectrecore.command.CommandRouter commandRouter;
    public dev.hydev.spectrecore.command.CommandRouter commands() { return commandRouter; }
    private dev.hydev.spectrecore.config.ModuleConfiguration moduleConfigs;
    public dev.hydev.spectrecore.config.ModuleConfiguration moduleConfigs() { return moduleConfigs; }

    @Override public void reloadConfig() {
        super.reloadConfig();
        if (moduleConfigs != null) {
            try { moduleConfigs.reload(); }
            catch (Exception error) { if (!moduleConfigs.requested().isEmpty()) moduleConfigs.install(super.getConfig()); throw new IllegalStateException(Settings.text("nucleo", "SpectreCorePlugin.etiqueta-035", "Configuración modular inválida: ") + error.getMessage(), error); }
            moduleConfigs.install(super.getConfig());
            Settings.install(moduleConfigs);
            Settings.configureCommands(this);
        }
    }

    public SetDetector sets() { return sets; }
    public Targeting targeting() { return targeting; }
    public Visuals visuals() { return visuals; }
    public Effects effects() { return effects; }
    public Cooldowns cooldowns() { return cooldowns; }
    public long tick() { return tick; }

    public boolean hasAccess(Player player, String id, boolean actualSet) {
        return getConfig().getBoolean("skills." + id + ".enabled", false)
                && (actualSet || id.equals(demo.get(player.getUniqueId())));
    }

    @Override public void onEnable() {
        commandRouter=new dev.hydev.spectrecore.command.CommandRouter(this,(sender,args)->handleCore(sender,getCommand("spectrecore"),"spectrecore",args));
        integrations = new dev.hydev.spectrecore.integration.IntegrationRegistry(getServer().getPluginManager());
        if (integrations.available("AuraSkills")) {
            try { auraSkillsBridge = new AuraSkillsBridge(this); }
            catch (RuntimeException | LinkageError error) {
                getLogger().warning("AuraSkillsBridge incompatible: se conserva el modificador de daño anterior. " + error.getMessage());
            }
        }
        migrateLegacySkillsConfig();
        saveDefaultConfig();
        moduleConfigs = new dev.hydev.spectrecore.config.ModuleConfiguration(getDataFolder(), getClass().getClassLoader());
        try { reloadConfig(); }
        catch (Exception error) { getLogger().severe(error.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        reloadDiagnostics();
        toastService = new ToastService(this);
        toastService.enable();
        for (String name : commandRouter.names()) if (!name.equals("spectrecore") && commandRouter.get(name) != null) {
            commandRouter.get(name).setExecutor((sender, command, label, args) -> { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-001", "Esta función está desactivada.")); return true; });
            commandRouter.get(name).setTabCompleter((sender, command, label, args) -> List.of());
        }
        for (String obsolete : List.of("SpectreSkills", "SpectreCooking", "HydevBotania")) {
            if (getServer().getPluginManager().getPlugin(obsolete) != null) {
                getLogger().severe(Settings.plain("nucleo", "SpectreCorePlugin.mensaje-002", "Hay sistemas incompatibles activos. Consulta a administración antes de continuar.", "obsolete", obsolete));
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        boolean skillsEnabled = getConfig().getBoolean("modules.skills.enabled", true);
        boolean cookingEnabled = getConfig().getBoolean("modules.cooking.enabled", true);
        if (skillsEnabled) {
            sets = new SetDetector(this);
            targeting = new Targeting(this);
            visuals = new Visuals(this);
            effects = new Effects(this, visuals);
            passive = new PassiveBonuses(this);
            visuals.removeOrphans();
            modules.add(new BotaniaSkill(this));
            modules.add(new CoronaSkill(this));
            modules.add(new AsterionSkill(this));
            ultimo = new UltimoJuramentoSkill(this);
            modules.add(ultimo);
            modules.add(new PrimerDragonSkill(this));
            getServer().getPluginManager().registerEvents(this, this);
            getServer().getScheduler().runTaskTimer(this, () -> {
                tick++;
                if (tick % Math.max(1, getConfig().getInt("engine.visual-update-ticks", 2)) != 0) return;
                passive.tick();
                for (SkillModule module : modules) module.tick(tick);
            }, 1L, 1L);
        }
        if (cookingEnabled) {
            cooking = new CookingModule(this);
            cooking.enable();
        }
        if (moduleConfigs.active("tesoros.mineria") || moduleConfigs.active("tesoros.combate") || moduleConfigs.active("tesoros.agricultura")) {
            treasureCooldowns = new dev.hydev.spectrecore.treasure.TreasureCooldowns(this);
            treasureCooldowns.enable();
        }
        if (getConfig().getBoolean("modules.boss-damage.enabled", true)) {
            bossDamage = new dev.hydev.spectrecore.migration.BossDamageModule(this);
            bossDamage.enable();
        }
        if (getConfig().getBoolean("modules.mining.enabled", true)) {
            mining = new dev.hydev.spectrecore.mining.MiningModule(this, treasureCooldowns);
            mining.enable();
        } else for (String name : List.of("resonancia", "debugresonancia", "nombreitem")) {
            if (commandRouter.get(name) != null) commandRouter.get(name).setExecutor((sender, command, label, args) -> {
                sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-003", "La minería especial está desactivada.")); return true;
            });
        }
        if (getConfig().getBoolean("modules.combat-treasures.enabled", true)) {
            combatTreasures = new dev.hydev.spectrecore.treasure.CombatTreasures(this, treasureCooldowns);
            combatTreasures.enable();
        }
        if (getConfig().getBoolean("modules.agriculture-treasures.enabled", true)) {
            agricultureTreasures = new dev.hydev.spectrecore.activities.AgricultureTreasures(this, treasureCooldowns);
            agricultureTreasures.enable();
        }
        if (getConfig().getBoolean("modules.activities.enabled", true)) {
            activities = new dev.hydev.spectrecore.activities.LegacyActivities(this);
            activities.enable();
        } else for (String name : dev.hydev.spectrecore.activities.LegacyActivities.commandNames()) {
            if (commandRouter.get(name) != null) commandRouter.get(name).setExecutor((sender, command, label, args) -> {
                sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-004", "Actividades desactivadas.")); return true;
            });
        }
        if (getConfig().getBoolean("modules.jobs.enabled", true)) { jobs = new dev.hydev.spectrecore.systems.JobsModule(this); jobs.enable(); }
        if (getConfig().getBoolean("modules.slayer.enabled", true)) { slayer = new dev.hydev.spectrecore.systems.SlayerModule(this); slayer.enable(); }
        if (getConfig().getBoolean("modules.exit-portal.enabled", true)) { portal = new dev.hydev.spectrecore.systems.PortalModule(this); portal.enable(); }
        java.util.Map<String, Boolean> systemCommands = new java.util.HashMap<>();
        systemCommands.put("trabajo", jobs != null);
        for (String name : List.of("activarslayer","cancelarslayer","resetslayer","slayerstats","slayerinvocar","contratarslayer","abandonarslayer","comprartelefono","misiones")) systemCommands.put(name, slayer != null);
        for (String name : List.of("hydevportaljoin","hydevportalleave","hydevportalcleanup","hydevportalstatus","hydevportaldemo")) systemCommands.put(name, portal != null);
        systemCommands.forEach((name, enabled) -> { if (!enabled && commandRouter.get(name) != null) commandRouter.get(name).setExecutor((sender,command,label,args) -> { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-005", "Ese módulo está desactivado.")); return true; }); });
        if (getCommand("spectrecore") != null) getCommand("spectrecore").setTabCompleter(this);
        if (commandRouter.get("armaduras") != null && moduleConfigs.active("equipamiento.libro")) {
            armorGuide = new dev.hydev.spectrecore.guide.ArmorGuide(this);
            armorGuide.enable();
            commandRouter.get("armaduras").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) armorGuide.open(player);
                else sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-006", "Esta guía se abre dentro del juego."));
                return true;
            });
            commandRouter.get("armaduras").setTabCompleter((sender, command, label, args) -> List.of());
        }
        displayModels = new dev.hydev.spectrecore.model.DisplayModels(this);
        try {
            mago = new dev.hydev.spectrecore.guide.MagoMenu(this);
        } catch (Exception error) {
            getLogger().severe("No se pudo cargar ui/mago.yml: " + error.getMessage() + "; /mago no estará disponible.");
        }
        if (getCommand("mago") != null) {
            getCommand("mago").setExecutor((sender,cmd,label,args) -> {
                if (mago == null) { sender.sendMessage(Settings.component(Settings.text("nucleo", "MagoMenu.no-disponible", "<red>El Mago está temporalmente desactivado.</red>"))); return true; }
                return mago.command(sender,args);
            });
            getCommand("mago").setTabCompleter((sender,cmd,label,args) -> mago == null ? List.of() : mago.complete(args));
        }
        registerItemInspector();
        getLogger().info(Settings.plain("nucleo", "SpectreCorePlugin.mensaje-007", "Hydev {getpluginmeta-getversion}: habilidades={skillsenabled}, cocina={cookingenabled}.", "getpluginmeta-getversion", getPluginMeta().getVersion(), "skillsenabled", skillsEnabled, "cookingenabled", cookingEnabled));
    }

    private void migrateLegacySkillsConfig() {
        File newConfig = new File(getDataFolder(), "config.yml");
        File oldConfig = new File(getDataFolder().getParentFile(), "SpectreSkills/config.yml");
        if (newConfig.exists() || !oldConfig.isFile()) return;
        saveDefaultConfig();
        YamlConfiguration target = YamlConfiguration.loadConfiguration(newConfig);
        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldConfig);
        for (String path : old.getKeys(true)) if (!old.isConfigurationSection(path)) target.set(path, old.get(path));
        try {
            target.save(newConfig);
            reloadConfig();
            getLogger().info(Settings.plain("nucleo", "SpectreCorePlugin.mensaje-008", "Se actualizaron ajustes anteriores; el archivo original sigue intacto."));
        } catch (IOException ex) {
            getLogger().severe(Settings.plain("nucleo", "SpectreCorePlugin.mensaje-009", "No se pudo migrar configuración antigua: {ex-getmessage}", "ex-getmessage", ex.getMessage()));
        }
    }

    @Override public void onDisable() {
        if (portal != null) { portal.disable(); portal = null; }
        if (slayer != null) { slayer.disable(); slayer = null; }
        if (jobs != null) { jobs.disable(); jobs = null; }
        if (activities != null) { activities.disable(); activities = null; }
        if (agricultureTreasures != null) { agricultureTreasures.disable(); agricultureTreasures = null; }
        if (combatTreasures != null) { combatTreasures.disable(); combatTreasures = null; }
        if (bossDamage != null) { bossDamage.disable(); bossDamage = null; }
        if (mining != null) { mining.disable(); mining = null; }
        if (treasureCooldowns != null) { treasureCooldowns.disable(); treasureCooldowns = null; }
        if (cooking != null) { cooking.disable(); cooking = null; }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player.getUniqueId());
            if (passive != null) passive.clear(player);
        }
        if (auraSkillsBridge != null) { auraSkillsBridge.clearAll(); auraSkillsBridge = null; }
        if (toastService != null) { toastService.disable(); toastService = null; }
        if (visuals != null) visuals.removeOrphans();
        if (displayModels != null) { displayModels.stop(); displayModels = null; }
        if (mago != null) { mago.close(); mago = null; }
        if (armorGuide != null) { armorGuide.disable(); armorGuide = null; }
        modules.clear(); demo.clear(); cooldowns.clearAll(); combatTraces.clear(); combatLogLast.clear();
    }

    /** Incoming damage: applies to any source, including magic, environmental damage and mobs. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIncoming(EntityDamageEvent event) {
        if (applyingSkillDamage || !(event.getEntity() instanceof Player player) || event.getDamage() <= 0) return;
        double factor = 1.0;
        for (SkillModule module : modules) factor *= module.incomingMultiplier(player, tick);
        if (factor != 1.0) event.setDamage(Math.max(0, event.getDamage() * factor));
    }

    /** Outgoing direct melee damage. No skill-generated damage can pass this hook. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOutgoing(EntityDamageByEntityEvent event) {
        if (applyingSkillDamage || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target) || !targeting.isHostile(target)) return;
        double factor = 1.0;
        for (SkillModule module : modules) factor *= module.outgoingMultiplier(attacker, target, tick);
        double original = event.getDamage();
        if (factor != 1.0) event.setDamage(Math.max(0, original * factor));
        if (getConfig().getBoolean("engine.combat.debug-multipliers", false))
            combatTraces.put(event, new CombatTrace(attacker.getName(), target.getType().name(),
                    original, event.getDamage(), factor));
    }

    /** Post-validation: never count a cancelled or zero-damage hit toward combos or activations. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        CombatTrace trace = combatTraces.remove(event);
        if (trace != null && event.getDamager() instanceof Player observer
                && getConfig().getBoolean("engine.combat.debug-multipliers", false)) {
            long now = System.currentTimeMillis(), min = Math.max(250, getConfig().getLong("engine.combat.debug-rate-millis", 1000));
            if (now - combatLogLast.getOrDefault(observer.getUniqueId(), 0L) >= min) {
                combatLogLast.put(observer.getUniqueId(), now);
                getLogger().info(String.format(java.util.Locale.ROOT,
                        "[Combate QA] %s -> %s original=%.2f esperado=%.2f evento=%.2f final=%.2f factor=%.3f",
                        trace.attacker(), trace.enemy(), trace.original(), trace.requested(),
                        event.getDamage(), event.getFinalDamage(), trace.factor()));
            }
        }
        if (applyingSkillDamage || event.getFinalDamage() <= 0) return;
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity target
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && targeting.isHostile(target)) {
            for (SkillModule module : modules) module.onMelee(attacker, target, tick);
        }
        if (event.getEntity() instanceof Player victim && victim.getHealth() > event.getFinalDamage()) {
            LivingEntity aggressor = attackerOf(event.getDamager());
            if (aggressor != null && targeting.isHostile(aggressor)) {
                for (SkillModule module : modules) module.onHostileDamage(victim, aggressor, tick);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) for (SkillModule module : modules) module.onSneakPress(event.getPlayer(), tick);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractEvent event) {
        if (ultimo == null || event.getHand() != EquipmentSlot.HAND) return;
        if (cooking != null && (cooking.isCookingStation(event.getClickedBlock())
                || cooking.isCookingActivation(event.getClickedBlock(), event.getItem()))) return;
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            ultimo.markRightClick(event.getPlayer(), tick);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (ultimo != null && event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN && event.getTo() != null)
            ultimo.confirmTeleport(event.getPlayer(), event.getFrom(), event.getTo(), tick);
    }

    private LivingEntity attackerOf(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }
    /** Neutral damage never credits a player or triggers SpectreSkills combos. */
    public void dealSkillDamage(LivingEntity target, double amount) {
        if (targeting == null || !targeting.isHostile(target) || amount <= 0 || !target.isValid()) return;
        applyingSkillDamage = true;
        try { target.damage(amount); } finally { applyingSkillDamage = false; }
    }
    public void dealSkillMagicDamage(LivingEntity target, double amount) {
        if (targeting == null || !targeting.isHostile(target) || amount <= 0 || !target.isValid()) return;
        applyingSkillDamage = true;
        try { target.damage(amount, DamageSource.builder(DamageType.MAGIC).build()); }
        finally { applyingSkillDamage = false; }
    }

    private void clear(UUID id) { for (SkillModule module : modules) module.clear(id); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId(); if (displayModels != null) displayModels.clear(id); if (mago != null) mago.forget(event.getPlayer()); clear(id); if (passive != null) passive.clear(event.getPlayer()); demo.remove(id);
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) { clear(event.getEntity().getUniqueId()); if (passive != null) passive.clear(event.getEntity()); }
    @EventHandler public void onChangeWorld(PlayerChangedWorldEvent event) { clear(event.getPlayer().getUniqueId()); if (passive != null) passive.clear(event.getPlayer()); }
    @EventHandler public void onPluginDisable(PluginDisableEvent event) {
        if (auraSkillsBridge != null && event.getPlugin().getName().equalsIgnoreCase("AuraSkills"))
            auraSkillsBridge.clearAll();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) { return commandRouter.dispatch(sender,args); }

    private boolean handleCore(CommandSender sender, Command command, String alias, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("integraciones")) {
            if (!canAdmin(sender)) { sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.sin-permiso", "<red>Sin permiso.</red>"))); return true; }
            integrations.status().forEach((name,ok) -> sender.sendMessage(Settings.component(Settings.text("nucleo", "Integraciones.estado",
                "{nombre}: {estado}", "nombre", name,
                "estado", ok ? Settings.text("nucleo", "Integraciones.activo", "<green>detectado</green>")
                            : Settings.text("nucleo", "Integraciones.inactivo", "<gray>no activo</gray>")))));
            return true;
        }
        if (sub.equals("toast")) {
            if (!canAdmin(sender)) { sender.sendMessage(Settings.message("nucleo", "ToastService.permiso", "§cSin permiso.")); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("nucleo", "ToastService.solo-jugador", "§7Prueba el toast desde el juego.")); return true; }
            if (args.length != 3 || !args[1].equalsIgnoreCase("prueba")
                    || !java.util.Set.of("furia","resonancia","gema-basica","gema-intermedia","gema-superior",
                            "trabajo-minero","trabajo-granjero","trabajo-cazador").contains(args[2])) {
                sender.sendMessage(Settings.message("nucleo", "ToastService.uso", "§eUso: /spectrecore toast prueba <id>"));
                return true;
            }
            if (toastService == null || !toastService.show(player, args[2]))
                sender.sendMessage(Settings.message("nucleo", "ToastService.no-disponible", "§eEsta notificación no está disponible."));
            else sender.sendMessage(Settings.message("nucleo", "ToastService.solicitado", "§7Toast solicitado (puede estar filtrado por antispam)."));
            return true;
        }
        if (sub.equals("diagnostico") || sub.equals("diagnostics")) {
            if (!canAdmin(sender)) { sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.sin-permiso", "<red>Sin permiso.</red>"))); return true; }
            sender.sendMessage(Settings.component(Settings.text("nucleo", "Diagnostico.estado",
                    "<gold>Hydev — diagnóstico: {estado}</gold>", "estado", diagnostics.enabled() ? "ON" : "OFF")));
            sender.sendMessage(Settings.component(Settings.text("nucleo", "Diagnostico.cocina", "<gray>Cocina: {estado}</gray>", "estado",
                    cooking == null ? "inactiva" : cooking.stationCount() + " estaciones, " + cooking.pendingClaims() + " pendientes, storage=" + cooking.storageReady())));
            sender.sendMessage(Settings.component(Settings.text("nucleo", "Diagnostico.cofres", "<gray>Cofres: {estado}</gray>", "estado",
                    activities == null ? "inactivos" : activities.pendingChestCount() + " pendientes, " + activities.ambiguousChestCount() + " ambiguos, storage=" + activities.storageReady())));
            sender.sendMessage(Settings.component(Settings.text("nucleo", "Diagnostico.modelos", "<gray>Modelos: {estado}</gray>", "estado",
                    displayModels == null ? "inactivos" : displayModels.placedCount() + " colocados, " + displayModels.liveCount() + " activos, storage=" + displayModels.storageReady())));
            for (var metric : diagnostics.snapshot()) sender.sendMessage(Settings.component(Settings.text("nucleo", "Diagnostico.metrica", "<gray>{metrica}: eventos={eventos}, fallos={fallos}, promedio={promedio}ms</gray>",
                    "metrica", metric.metric(), "eventos", metric.events(), "fallos", metric.failures(),
                    "promedio", String.format(java.util.Locale.ROOT, "%.3f", metric.averageMs()))));
            return true;
        }
        if (sub.equals("modelo")) {
            if (!canAdmin(sender)) { sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.sin-permiso", "<red>Sin permiso.</red>"))); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.solo-jugador", "<red>Solo jugador.</red>"))); return true; }
            if (displayModels == null) return true;
            if (args.length == 2 && args[1].equalsIgnoreCase("lista")) return displayModels.list(player);
            if (args.length != 3) {
                sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.uso",
                    "<gray>Uso: /spectrecore modelo vista|colocar &lt;id&gt; / quitar &lt;UUID&gt; / lista</gray>"))); return true;
            }
            return switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
                case "vista" -> displayModels.preview(player, args[2]);
                case "colocar" -> displayModels.place(player, args[2]);
                case "quitar" -> displayModels.remove(player, args[2]);
                default -> { sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.uso",
                    "<gray>Uso: /spectrecore modelo vista|colocar &lt;id&gt; / quitar &lt;UUID&gt; / lista</gray>"))); yield true; }
            };
        }
        if (sub.equals("agricultura")) {
            if (agricultureTreasures != null) return agricultureTreasures.preview(sender, args);
            sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-010", "Agricultura desactivada.")); return true;
        }
        if (sub.equals("combate")) {
            if (combatTreasures != null) return combatTreasures.preview(sender, args);
            sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-011", "Tesoros de combate desactivados.")); return true;
        }
        if (sub.equals("mineria")) {
            if (mining != null) return mining.preview(sender, args);
            sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-012", "Minería desactivada.")); return true;
        }
        if (sub.equals("modulos") || sub.equals("modules")) {
            moduleConfigs.requested().forEach((name, enabled) -> sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-013","§6{name}§7: {valor2}{valor3}","name", name,"valor2", (moduleConfigs.active(name) ? Settings.component(Settings.text("nucleo", "SpectreCorePlugin.texto-001", "§aACTIVO")) : Settings.component(Settings.text("nucleo", "SpectreCorePlugin.texto-002", "§cINACTIVO"))),"valor3", (moduleConfigs.restartPending(name) ? Settings.component(Settings.text("nucleo", "SpectreCorePlugin.texto-003", " §e(cambio pendiente: reiniciar)")) : ""))));
            return true;
        }
        if (sub.equals("cocina")) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-014", "§7Usa /mago cocina para aprender o /spectrecore cocina recetas para consultar recetas.")); return true; }
        if (sub.equals("habilidades") || sub.equals("skills")) sub = "status";
        if (sub.equals("reload")) {
            if (!canAdmin(sender)) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-015", "§cSin permiso.")); return true; }
            try { reloadConfig(); }
            catch (Exception error) {
                getLogger().warning("Configuración: recarga rechazada: " + error.getMessage());
                sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-016",
                        "<red>No se pudieron actualizar los ajustes. Se conserva la configuración anterior."));
                return true;
            }
            for (Player player : Bukkit.getOnlinePlayers()) { clear(player.getUniqueId()); if (passive != null) passive.clear(player); }
            try {
                if (mago == null) mago = new dev.hydev.spectrecore.guide.MagoMenu(this);
                else mago.reload();
            } catch (Exception badMenu) {
                getLogger().warning("El Mago: recarga rechazada: " + badMenu.getMessage());
                sender.sendMessage(Settings.message("nucleo", "MagoMenu.reload-error",
                    "<red>No se pudo actualizar la guía. Se conserva la versión anterior."));
            }
            reloadDiagnostics();
            if (toastService != null) toastService.reload();
            if (displayModels != null && !displayModels.reloadOptions()) sender.sendMessage(Settings.component(Settings.text("nucleo", "Modelos.settings-invalido", "<red>No se pudieron actualizar los ajustes de modelos.</red>")));
            if (cooking != null) cooking.reload();
            if (mining != null) mining.reload();
            if (combatTreasures != null) combatTreasures.reload();
            if (treasureCooldowns != null) treasureCooldowns.reload();
            if (agricultureTreasures != null) agricultureTreasures.reload();
            if (activities != null) activities.reload();
            if (jobs != null) jobs.reload();
            if (armorGuide != null) armorGuide.reload();
            if (slayer != null) slayer.reload();
            if (portal != null) portal.reload();
            if (bossDamage != null) bossDamage.reload();
            sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-017", "§aSe actualizaron habilidades y recetas. Para cambiar módulos activos, reinicia el servidor."));
            return true;
        }
        // Server-console-only bridge for custom/MMOItems versions that do not emit teleport events.
        if (sub.equals("blink")) {
            if (!(sender instanceof ConsoleCommandSender)) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-018", "§cSolo consola.")); return true; }
            if (args.length < 2) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-019", "Uso: /spectrecore blink <jugador>")); return true; }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null && ultimo != null) ultimo.signalBlink(target, target.getLocation(), tick);
            else sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-020", "§cJugador no conectado."));
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-021", "§7Usa este comando desde el juego (salvo reload/blink).")); return true; }
        UUID id = player.getUniqueId();
        if (sub.equals("status")) {
            player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-022","§d✦ Hydev {getpluginmeta-getversion} §7| demo: §f{valor2}","getpluginmeta-getversion", getPluginMeta().getVersion(),"valor2", demo.getOrDefault(id, "ninguno")));
            for (SkillModule module : modules) player.sendMessage(Settings.component(module.status(player)));
            return true;
        }
        if (!canAdmin(player)) { player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-023", "§cSin permiso.")); return true; }
        if (sub.equals("demo") && args.length >= 2) {
            String requested = args[1].toLowerCase();
            SkillModule found = modules.stream().filter(m -> m.id().equals(requested)).findFirst().orElse(null);
            if (found == null) { player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-024", "§cMódulo desconocido. Usa /spectrecore demo <nombre> (Tab).")); return true; }
            clear(id);
            if (requested.equals(demo.get(id))) {
                demo.remove(id); player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-025", "§7Demo desactivada."));
            } else {
                demo.put(id, requested); player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-026", "§aDemo de {requested} activa.", "requested", requested));
            }
            return true;
        }
        if (sub.equals("reset")) {
            clear(id); cooldowns.resetAll(id);
            player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-027", "§aHabilidades y tiempos de espera reiniciados."));
            return true;
        }
        player.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-028", "§7/spectrecore ayuda"));
        return true;
    }

    private boolean canAdmin(CommandSender sender) {
        return sender.hasPermission("spectrecore.admin") || sender.hasPermission("spectreskills.admin");
    }

    private void registerItemInspector() {
        if (commandRouter.get("nombreitem") == null) return;
        commandRouter.get("nombreitem").setExecutor((sender,command,label,args) -> {
            if (!canAdmin(sender) && !sender.hasPermission("hydev.admin")) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-029", "§cSin permiso.")); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-030", "Usa este comando dentro del juego.")); return true; }
            var item = player.getInventory().getItemInMainHand();
            if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) { sender.sendMessage(Settings.message("nucleo", "SpectreCorePlugin.mensaje-031", "§cSostén un objeto con nombre personalizado.")); return true; }
            var name = item.getItemMeta().displayName();
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
            player.sendMessage(Settings.component(Settings.text("nucleo", "SpectreCorePlugin.mensaje-032", "Nombre: ")).append(name));
            player.sendMessage(Settings.component(Settings.text("nucleo", "SpectreCorePlugin.mensaje-033", "[COPIAR NOMBRE]"), Settings.color("nucleo", "SpectreCorePlugin.color-001", net.kyori.adventure.text.format.NamedTextColor.GREEN)).clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(plain)));
            getLogger().info(Settings.plain("nucleo", "SpectreCorePlugin.mensaje-034","Nombre del item: [{valor1}]","valor1", plain.replace('\n',' ').replace('\r',' '))); return true;
        });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return commandRouter.complete(sender,args); }
}
