package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.time.Duration;
import java.util.*;

public final class SlayerModule implements Listener {
    private static final List<String> CATEGORIES = List.of("revenant", "necromancer", "spider");
    private static final class Mission extends ProgressRules.SlayerProgress { }
    private final JavaPlugin plugin; private YamlConfiguration config;
    private final EconomyBridge contractsEconomy = new EconomyBridge();
    private final MmoBridge contractItems = new MmoBridge();
    private MissionsMenu missionsMenu;
    private final Map<UUID, Map<String, Mission>> missions = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>(); private final Map<UUID, Long> attempts = new HashMap<>();
    private BukkitTask timer; private boolean dirty;
    public SlayerModule(JavaPlugin plugin) { this.plugin = plugin; }
    public void enable() {
        reload(); load(); Bukkit.getPluginManager().registerEvents(this, plugin);
        missionsMenu = new MissionsMenu(plugin, this);
        missionsMenu.enable();
        for (String name : List.of("activarslayer", "cancelarslayer", "resetslayer", "slayerstats", "slayerinvocar", "contratarslayer", "abandonarslayer", "comprartelefono", "misiones")) if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name) != null) {
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setExecutor((sender, cmd, label, args) -> command(sender, cmd.getName(), args));
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setTabCompleter((sender, cmd, label, args) -> args.length == 1 && !cmd.getName().equals("slayerstats") ? CATEGORIES : List.of());
        }
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200, 200);
        Bukkit.getOnlinePlayers().forEach(this::refreshBar);
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "slayer/config.yml");
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
    private boolean command(CommandSender sender, String command, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-001", "Conserva el uso del Skript: ejecuta estos comandos como jugador.")); return true; }
        UUID id = p.getUniqueId();
        if (command.equals("misiones")) {
            if (missionsMenu != null) missionsMenu.open(p, args.length > 0 && args[0].equalsIgnoreCase("revenant") ? "revenant" : "misiones");
            return true;
        }
        if (command.equals("comprartelefono")) return purchasePhone(p, args);
        if (command.equals("slayerstats")) {
            p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-002", "§6✦ ESTADÍSTICAS DE MISIONES"));
            for (String c : CATEGORIES) { Mission m = mission(id, c); p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-003","§e{name-c}§7: §f{m-kills}/{m-required} §7| Tier {m-tier} | {valor5}","name-c", name(c),"m-kills", m.kills,"m-required", m.required,"m-tier", m.tier,"valor5", (m.active ? Settings.text("slayer", "SlayerModule.texto-001", "§aActiva") : Settings.text("slayer", "SlayerModule.texto-002", "§7Inactiva")))); }
            return true;
        }
        if (command.equals("contratarslayer") || command.equals("abandonarslayer"))
            return playerContract(p, command, args);
        if (!admin(sender)) { sender.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-004", "§cNo tienes permiso.")); return true; }
        if (args.length < 1 || !CATEGORIES.contains(args[0].toLowerCase(Locale.ROOT))) { p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-005", "§cCategoría: revenant, necromancer o spider.")); return true; }
        String category = args[0].toLowerCase(Locale.ROOT); Mission m = mission(id, category);
        if (command.equals("activarslayer")) {
            int tier; try { tier = args.length > 1 ? Integer.parseInt(args[1]) : 0; } catch (NumberFormatException e) { tier = 0; }
            if (tier < 1 || tier > 3) { p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-006", "§cTier inválido. Usa 1, 2 o 3.")); return true; }
            if (CATEGORIES.stream().anyMatch(c -> mission(id, c).active)) { p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-007", "§cYa tienes una misión activa. Complétala o cancélala.")); return true; }
            if (!p.hasPermission("slayer." + category + ".tier" + tier)) { p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-008", "§cNo tienes permiso para ese tier.")); return true; }
            m.tier = tier; m.kills = 0; m.required = Math.max(1, config.getInt("objetivos.tier" + tier, ProgressRules.required(tier))); m.active = true;
            dirty = true; save(); refreshBar(p); p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-009", "§6MISIONES §e{name-category} · Tier {tier} · Objetivo: {m-required}", "name-category", name(category), "tier", tier, "m-required", m.required));
            p.playSound(p.getLocation(), Settings.sound("slayer", "SlayerModule.sound-001", Sound.ENTITY_PLAYER_LEVELUP),Settings.f("slayer", "SlayerModule.playsound-001-arg2", 1),Settings.f("slayer", "SlayerModule.playsound-002-arg3", 1)); return true;
        }
        if (command.equals("slayerinvocar")) {
            if (!m.active || m.kills < m.required) p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-010", "§cTodavía no has completado ese objetivo."));
            else summon(p, category, m);
            return true;
        }
        if (command.equals("cancelarslayer") && !m.active) { p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-011", "§cNo tienes esa misión activa.")); return true; }
        m.active = false; if (command.equals("resetslayer")) m.kills = 0;
        dirty = true; save(); refreshBar(p); p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-012","§7Misión {name-category}{valor2}","name-category", name(category),"valor2", (command.equals("resetslayer") ? " reiniciada." : " cancelada."))); return true;
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

    public String progressLine(Player player, String category, int tier) {
        Mission state = mission(player.getUniqueId(), category);
        if (state.active && state.tier == tier)
            return "§a✦ Activa: §f" + state.kills + "§7/§f" + state.required + " bajas";
        if (state.active) return "§cTienes otra misión activa";
        if (!player.hasPermission("slayer." + category + ".tier" + tier)) return "§cNo tienes acceso a este rango";
        return "§e✦ Disponible para contratar";
    }

    /**
     * Secure purchase of the remote quest phone; never elevate the player to OP.
     * Item and capacity are checked before confirmed Vault withdrawal.
     */
    private boolean purchasePhone(Player player, String[] args) {
        if (args.length != 0 || !config.getBoolean("acceso-remoto.habilitado", false)) {
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-no-disponible",
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
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-no-disponible",
                    "§cEl acceso remoto no está disponible por ahora."));
            return true;
        }
        ItemStack item = contractItems.build(type, itemId, 1);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-item-fallo",
                    "§cNo se pudo generar el Teléfono Rojo de MMOItems. No se ha cobrado."));
            return true;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-inventario",
                    "§cLibera al menos una ranura de inventario antes de comprar."));
            return true;
        }
        if (contractsEconomy.withdraw(player, price) != EconomyBridge.Result.CONFIRMED) {
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-pago-fallo",
                    "§cVault no confirmó el pago. No se ha entregado el objeto."));
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
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-comprado",
                    "§aHas comprado el Teléfono Rojo por {precio} ↾.", "precio", price));
        } catch (RuntimeException error) {
            // Log for manual reconciliation: a failure after insertion might have already
            // delivered an item. Never blindly refund and risk generating free duplicates.
            plugin.getLogger().severe("SLAYER PHONE DELIVERY RECONCILIATION REQUIRED player="
                    + player.getUniqueId() + " price=" + price + " cause=" + error);
            player.sendMessage(Settings.text("slayer", "SlayerModule.telefono-entrega-fallo",
                    "§cError de entrega. Avisa a un administrador; no intentes pagar de nuevo."));
        }
        return true;
    }

    /** Player contracts are opt-in until zMenu stops doing its own payments/OP dispatch. */
    private boolean playerContract(Player p, String command, String[] args) {
        if (args.length < 1 || !CATEGORIES.contains(args[0].toLowerCase(Locale.ROOT))) {
            p.sendMessage(Settings.text("slayer", "SlayerModule.contrato-categoria", "§cCategoría inválida.")); return true;
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
                    p.hasPermission("slayer." + category + ".tier" + tier), cost)
                : SlayerContractRules.validateCancel(enabled, m.active, cost);
        if (refusal != SlayerContractRules.Refusal.NONE) {
            p.sendMessage(Settings.text("slayer", "SlayerModule.contrato-rechazado",
                    "§cContrato rechazado: {motivo}", "motivo", refusal.name()));
            return true;
        }
        if (contractsEconomy.withdraw(p, cost) != EconomyBridge.Result.CONFIRMED) {
            p.sendMessage(Settings.text("slayer", "SlayerModule.contrato-pago-fallo",
                    "§cNo se confirmó el cobro mediante Vault; el contrato no cambió."));
            return true;
        }
        int oldTier = m.tier, oldKills = m.kills, oldRequired = m.required;
        boolean oldActive = m.active;
        if (start) {
            m.tier = tier; m.kills = 0;
            m.required = Math.max(1, config.getInt("objetivos.tier" + tier, ProgressRules.required(tier)));
            m.active = true;
        } else m.active = false;
        dirty = true;
        if (!save()) {
            m.tier = oldTier; m.kills = oldKills; m.required = oldRequired; m.active = oldActive;
            dirty = true;
            // Failed writes are exceptional. Refund immediately without unconfirmed command fallback.
            if (cost > 0 && contractsEconomy.deposit(p, cost, false) != EconomyBridge.Result.CONFIRMED)
                plugin.getLogger().severe("SLAYER REFUND FAILURE: " + p.getUniqueId() + " amount=" + cost + " category=" + category);
            p.sendMessage(Settings.text("slayer", "SlayerModule.contrato-storage-fallo",
                    "§cNo se pudo guardar el contrato; solicita revisión administrativa del saldo."));
            return true;
        }
        refreshBar(p);
        p.sendMessage(Settings.text("slayer", "SlayerModule.contrato-aplicado",
                "§6Contrato {accion}: {categoria} · Costo {costo} ↾",
                "accion", start ? "iniciado" : "abandonado", "categoria", name(category), "costo", cost));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller(); if (p == null || !p.isOnline()) return;
        for (String category : CATEGORIES) {
            Mission m = mission(p.getUniqueId(), category);
            if (!m.active || !e.getEntityType().name().equals(config.getString("categorias." + category + ".entidad"))) continue;
            m.recordKill(); dirty = true; refreshBar(p);
            if (m.kills % Math.max(1,config.getInt("avisos.cada-bajas",10)) == 0 && m.kills < m.required) p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-013", "§6MISIONES §e{name-category} §f{m-kills}/{m-required}", "name-category", name(category), "m-kills", m.kills, "m-required", m.required));
            if (m.ready()) summon(p, category, m);
        }
    }
    private void summon(Player p, String category, Mission m) {
        long now = System.currentTimeMillis(); if (now - attempts.getOrDefault(p.getUniqueId(), 0L) < Math.max(0,config.getLong("invocacion.reintento-milisegundos",5000))) return;
        attempts.put(p.getUniqueId(), now);
        String mob = config.getString("categorias." + category + ".mob", "").replace("{tier}", Integer.toString(m.tier));
        Entity spawned = null;
        try {
            var mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
            if (mythic != null && mythic.isEnabled()) {
                Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, mythic.getClass().getClassLoader());
                Object instance = api.getMethod("inst").invoke(null), helper = api.getMethod("getAPIHelper").invoke(instance);
                Object result = helper.getClass().getMethod("spawnMythicMob", String.class, Location.class, int.class).invoke(helper, mob, p.getLocation(), config.getInt("invocacion.nivel-mob",1));
                if (result instanceof Entity entity && entity.isValid()) spawned = entity;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { plugin.getLogger().warning(Settings.text("slayer", "SlayerModule.mensaje-014", "Slayer: no se pudo invocar {mob}: {error-getmessage}", "mob", mob, "error-getmessage", error.getMessage())); }
        if (spawned == null) {
            p.sendMessage(Settings.text("slayer", "SlayerModule.mensaje-015", "§cNo se pudo invocar {mob}. Tu objetivo se conserva. Reintenta con /spectrecore slayer invocar {category}", "mob", mob, "category", category));
            return;
        }
        m.confirmSpawn(); dirty = true; save(); refreshBar(p);
        p.showTitle(Title.title(Settings.component(Settings.text("slayer", "SlayerModule.mensaje-016", "¡MISIÓN COMPLETADA!"), Settings.color("slayer", "SlayerModule.color-002", NamedTextColor.GOLD)), Settings.component(Settings.text("slayer", "SlayerModule.mensaje-017", "{name-category} ha sido invocado", "name-category", name(category)), Settings.color("slayer", "SlayerModule.color-003", NamedTextColor.YELLOW)), Title.Times.times(Duration.ofMillis(Settings.i("slayer", "SlayerModule.ofmillis-003-arg0", 150)), Duration.ofSeconds(Settings.i("slayer", "SlayerModule.ofseconds-004-arg0", 3)), Duration.ofMillis(Settings.i("slayer", "SlayerModule.ofmillis-005-arg0", 400)))));
        p.playSound(p.getLocation(), Settings.sound("slayer", "SlayerModule.sound-004", Sound.ENTITY_WITHER_SPAWN),Settings.f("slayer", "SlayerModule.playsound-006-arg2", 1),Settings.f("slayer", "SlayerModule.playsound-007-arg3", .8f));
        Location center = spawned.getLocation().add(0,.15,0);
        for (int i = 0; i < 32; i++) { double a = i * Math.PI / 16; center.getWorld().spawnParticle(Settings.particle("slayer", "SlayerModule.particle-005", Particle.SOUL_FIRE_FLAME), center.clone().add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4),Settings.i("slayer", "SlayerModule.spawnparticle-008-arg2", 1),Settings.d("slayer", "SlayerModule.spawnparticle-009-arg3", 0),Settings.d("slayer", "SlayerModule.spawnparticle-010-arg4", 0),Settings.d("slayer", "SlayerModule.spawnparticle-011-arg5", 0),Settings.d("slayer", "SlayerModule.spawnparticle-012-arg6", .02)); }
    }
    private void refreshBar(Player p) {
        hideBar(p);
        for (String c : CATEGORIES) { Mission m = mission(p.getUniqueId(), c); if (!m.active) continue;
            BossBar bar = BossBar.bossBar(Settings.component(Settings.text("slayer", "SlayerModule.mensaje-018", "{name-c} · {m-kills}/{m-required}", "name-c", name(c), "m-kills", m.kills, "m-required", m.required)), Math.min(1f, (float)m.kills / Math.max(1,m.required)), c.equals("revenant") ? BossBar.Color.RED : c.equals("spider") ? BossBar.Color.GREEN : BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
            bars.put(p.getUniqueId(), bar); p.showBossBar(bar); break;
        }
    }
    private void hideBar(Player p) { BossBar bar = bars.remove(p.getUniqueId()); if (bar != null) p.hideBossBar(bar); }
    @EventHandler public void quit(PlayerQuitEvent e) { hideBar(e.getPlayer()); if (dirty) save(); }
    @EventHandler public void join(PlayerJoinEvent e) { Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) refreshBar(e.getPlayer()); }, 1); }
    private void load() {
        var data = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "slayer-data.yml"));
        for (String id : data.getKeys(false)) try { UUID uuid = UUID.fromString(id);
            for (String c : CATEGORIES) { var s = data.getConfigurationSection(id + "." + c); if (s == null) continue;
                Mission m = mission(uuid,c); m.tier = Math.max(0,Math.min(3,s.getInt("tier",0))); m.required = m.tier == 0 ? 0 : Math.max(1,s.getInt("required",ProgressRules.required(m.tier))); m.kills = Math.max(0,Math.min(m.required,s.getInt("kills"))); m.active = m.tier > 0 && s.getBoolean("active");
            }
        } catch (IllegalArgumentException e) { plugin.getLogger().warning(Settings.text("slayer", "SlayerModule.mensaje-019", "UUID inválido en slayer-data.yml: {id}", "id", id)); }
    }
    private boolean save() {
        var data = new YamlConfiguration(); missions.forEach((id, values) -> values.forEach((c,m) -> {
            String root = id + "." + c; data.set(root + ".tier",m.tier); data.set(root + ".kills",m.kills); data.set(root + ".required",m.required); data.set(root + ".active",m.active);
        })); if (YamlStore.save(plugin,"slayer-data.yml",data)) { dirty = false; return true; }
        return false;
    }
}
