package dev.hydev.spectrecore.activities;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.mining.MiningVisuals;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import org.bukkit.configuration.InvalidConfigurationException;
import dev.hydev.spectrecore.storage.AtomicFileStore;
import dev.hydev.spectrecore.systems.EconomyBridge;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LegacyActivities implements Listener {
    private record Offer(String type, String id) { }
    private static final Map<String, Offer> OFFERS = Map.of(
        "rompemuelas", new Offer("SWORD", "ROMPEMUELAS"), "taladroroto", new Offer("TOOL", "TALADRO"),
        "furiaestelar", new Offer("SWORD", "FURIA_ESTELAR"), "capuchaencogedora", new Offer("ARMOR", "CAPUCHA"),
        "slimehechizado", new Offer("ARMOR", "BOTASSLIME"), "compactador", new Offer("MATERIAL", "COMPACTADOR"),
        "golemcobre", new Offer("ARMOR", "COPPERGOLEM"));
    private static final Map<String, int[]> MONEY = Map.of(
        "polvodiamante", new int[]{70, 200}, "polvohierro", new int[]{20, 40}, "polvooro", new int[]{40, 70},
        "polvocobre", new int[]{5, 20}, "polvolapis", new int[]{20, 60}, "polvoredstone", new int[]{20, 50});
    private static final Map<String, String> UNLOCKS = Map.of(
        "almas1", "tesoros.combate.tier1", "mineria1", "tesoros.mineria.tier1",
        "slayerzombie1", "slayer.revenant.tier1", "slayerzombie2", "slayer.revenant.tier2", "slayerzombie3", "slayer.revenant.tier3");
    private final JavaPlugin plugin; private final MmoBridge mmo = new MmoBridge(); private final MiningVisuals visuals;
    private final EconomyBridge economy = new EconomyBridge();
    private final Map<UUID, Material> pending = new HashMap<>();
    /** Delivery may have happened. NEVER automatically replay these claims after a crash. */
    private final Map<UUID, Material> claiming = new HashMap<>();
    private boolean chestStorageReady = true;
    private final Map<UUID, BukkitTask> jobs = new HashMap<>();
    private YamlConfiguration config;
    public LegacyActivities(JavaPlugin plugin) { this.plugin = plugin; visuals = new MiningVisuals(plugin); }
    public static Set<String> commandNames() {
        Set<String> names = new HashSet<>(OFFERS.keySet()); names.addAll(MONEY.keySet()); names.addAll(UNLOCKS.keySet());
        names.add("opentreasure"); names.add("telefono1"); return Set.copyOf(names);
    }
    public void enable() {
        reload(); chestStorageReady = loadPending(); visuals.enable(); Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String name : commandNames()) if (dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name) != null) {
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setExecutor((sender, command, label, args) -> command(sender, command.getName()));
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setTabCompleter((sender, command, label, args) -> List.of());
        }
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "actividades/config.yml");
        visuals.configure(config.getBoolean("visuales.activados", true), config.getInt("visuales.max-animaciones", 32));
    }
    public int pendingChestCount() { return pending.size(); }
    public int ambiguousChestCount() { return claiming.size(); }
    public boolean storageReady() { return chestStorageReady; }
    public void disable() {
        HandlerList.unregisterAll(this); new ArrayList<>(jobs.values()).forEach(BukkitTask::cancel); jobs.clear();
        // Do not deliver prizes while shutting down: pending survives until next join.
        if (chestStorageReady) savePending();
        visuals.disable();
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (active("cygnus") && config.getBoolean("cygnus.mover-al-entrar", true)) moveCygnus();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (pending.containsKey(player.getUniqueId())) finishChest(player);
            if (active("entrada") && config.getBoolean("entrada.proteccion", true)) {
                int seconds = Math.max(1, Math.min(3600, config.getInt("entrada.duracion-segundos", 30)));
                int level = Math.max(1, Math.min(256, config.getInt("entrada.nivel-resistencia", 100)));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, level - 1, false, false, true));
                player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-001", Sound.ENTITY_PLAYER_LEVELUP),Settings.f("actividades", "LegacyActivities.playsound-001-arg2", 1),Settings.f("actividades", "LegacyActivities.playsound-002-arg3", 1));
            }
        }, 1);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e) {
        BukkitTask job = jobs.remove(e.getPlayer().getUniqueId()); if (job != null) job.cancel();
        // A disconnect must not spawn a dropped item while the player is leaving.
        // The recorded pending claim is completed on the next join.
    }
    private boolean admin(CommandSender sender) { return sender.hasPermission("spectrecore.activities.admin") || sender.hasPermission("spectrecore.admin"); }
    private boolean active(String group) { return dev.hydev.spectrecore.config.ModuleConfiguration.of(plugin).active("actividades."+group); }
    private boolean command(CommandSender sender, String name) {
        if (!admin(sender)) { sender.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-001", "§cNo tienes permiso para usar este comando.")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-002", "Esta acción debe realizarse dentro del juego.")); return true; }
        String group=OFFERS.containsKey(name)?"cygnus":MONEY.containsKey(name)?"polvos":UNLOCKS.containsKey(name)?"desbloqueos":name.equals("telefono1")?"telefono":"cofre";
        if(!active(group)){sender.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-003", "Esta actividad está desactivada."));return true;}
        if (OFFERS.containsKey(name)) {
            Offer fallback=OFFERS.get(name);
            offer(player,new Offer(config.getString("cygnus.ofertas."+name+".tipo",fallback.type),config.getString("cygnus.ofertas."+name+".id",fallback.id)));
        }
        else if (MONEY.containsKey(name)) {
            int[] range = MONEY.get(name);int min=config.getInt("polvos.comandos."+name+".minimo",range[0]),max=config.getInt("polvos.comandos."+name+".maximo",range[1]);
            if(min<0||max<min){player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-004", "Rango de pago inválido."));return true;}
            long amount = ThreadLocalRandom.current().nextLong(min, (long)max + 1);
            EconomyBridge.Result payment = economy.deposit(player, amount, true);
            if (payment != EconomyBridge.Result.FAILED) {
                if (payment == EconomyBridge.Result.COMMAND_SENT)
                    plugin.getLogger().warning("Economía: comando de depósito emitido pero no confirmado para " + player.getUniqueId());
                player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-005", "§fRecibiste §a${amount} §fpor pulverizar este mineral.", "amount", amount));
                player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-002", Sound.BLOCK_AMETHYST_CLUSTER_BREAK),Settings.f("actividades", "LegacyActivities.playsound-003-arg2", .7f),Settings.f("actividades", "LegacyActivities.playsound-004-arg3", 1.3f));
            } else player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-006", "§cNo se pudo confirmar el pago. Inténtalo más tarde."));
        } else if (UNLOCKS.containsKey(name)) {
            String permission=config.getString("desbloqueos.comandos."+name,UNLOCKS.get(name));
            if(!permission.matches("[A-Za-z0-9._*\\-]+")){player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-007", "Este desbloqueo no está disponible. Avisa a administración."));return true;}
            if (dispatch("lp user " + player.getName() + " permission set " + permission + " true")) {
                player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-008","{valor1}","valor1", name.equals("almas1") ? Settings.component(Settings.text("actividades", "LegacyActivities.texto-001", "§8Sientes un escalofrío...")) : name.equals("mineria1") ? Settings.component(Settings.text("actividades", "LegacyActivities.texto-002", "§8Sientes una energía revitalizándote...")) : Settings.component(Settings.text("actividades", "LegacyActivities.texto-003", "§8Los no-muertos reclaman tu nombre..."))));
                player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-003", Sound.BLOCK_ENCHANTMENT_TABLE_USE),Settings.f("actividades", "LegacyActivities.playsound-005-arg2", .5f),Settings.f("actividades", "LegacyActivities.playsound-006-arg3", 1.1f));
            } else player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-009", "§cNo se pudo activar el desbloqueo. Avisa a administración."));
        } else if (name.equals("telefono1")) {
            if (dispatch(config.getString("telefono.comando","zmenu open zmenu:slayers {player}").replace("{player}",player.getName()))) { player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-010", "§8Ring... ring... ring...")); player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-004", Sound.BLOCK_NOTE_BLOCK_BELL),Settings.f("actividades", "LegacyActivities.playsound-007-arg2", .7f),Settings.f("actividades", "LegacyActivities.playsound-008-arg3", 1.4f)); }
            else player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-011", "§cEl catálogo no está disponible por ahora."));
        } else if (name.equals("opentreasure")) openChest(player);
        return true;
    }
    private void offer(Player player, Offer offer) {
        if (ThreadLocalRandom.current().nextInt(1, 101)<=Math.max(0,Math.min(100,config.getInt("cygnus.probabilidad-fuga",30)))) {
            if (!moveCygnus()) { player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-012", "§cCygnus no puede desplazarse en este momento.")); return; }
            player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-013", "§f")); player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-014", "§eCygnus fue más astuto y salió volando con tu dinero...")); player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-015", "§f"));
            player.getWorld().spawnParticle(Settings.particle("actividades", "LegacyActivities.particle-005", Particle.PORTAL), player.getLocation().add(0, 1, 0),Settings.i("actividades", "LegacyActivities.spawnparticle-009-arg2", 24),Settings.d("actividades", "LegacyActivities.spawnparticle-010-arg3", .4),Settings.d("actividades", "LegacyActivities.spawnparticle-011-arg4", .5),Settings.d("actividades", "LegacyActivities.spawnparticle-012-arg5", .4),Settings.d("actividades", "LegacyActivities.spawnparticle-013-arg6", .15));
            player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-006", Sound.ENTITY_BAT_TAKEOFF),Settings.f("actividades", "LegacyActivities.playsound-014-arg2", .7f),Settings.f("actividades", "LegacyActivities.playsound-015-arg3", .8f));
        } else {
            var reward = mmo.build(offer.type, offer.id, 1);
            if (reward == null) { player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-016", "§cEsta recompensa no está disponible. Avisa a administración.", "offer-type", offer.type, "offer-id", offer.id)); return; }
            deliver(player, reward); player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-017", "§f")); player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-018", "§eCygnus confía en tu valor y te entregó el objeto..."));
            player.playSound(player.getLocation(), Settings.sound("actividades", "LegacyActivities.sound-007", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("actividades", "LegacyActivities.playsound-016-arg2", .8f),Settings.f("actividades", "LegacyActivities.playsound-017-arg3", 1.3f));
            visuals.gem(player.getLocation(), reward, "superior");
        }
    }
    private boolean moveCygnus() {
        List<String> commands = config.getStringList("cygnus.movimientos");
        return !commands.isEmpty() && dispatch(commands.get(ThreadLocalRandom.current().nextInt(commands.size())));
    }
    private boolean dispatch(String command) {
        String root = command.split(" ", 2)[0];
        if (Bukkit.getCommandMap().getCommand(root) == null) { plugin.getLogger().warning(Settings.plain("actividades", "LegacyActivities.mensaje-019", "Falta comando externo para actividad: {root}", "root", root)); return false; }
        try { return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command); }
        catch (RuntimeException error) { plugin.getLogger().warning(Settings.plain("actividades", "LegacyActivities.mensaje-020", "Falló comando de actividad: {error-getmessage}", "error-getmessage", error.getMessage())); return false; }
    }
    private void deliver(Player player, ItemStack item) {
        player.getInventory().addItem(item.clone()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left, drop -> { drop.setOwner(player.getUniqueId()); drop.setPickupDelay(0); }));
    }
    private void openChest(Player player) {
        UUID id = player.getUniqueId();
        if (!chestStorageReady) {
            player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-027",
                    "§cEl sistema de cofres está temporalmente desactivado por un error de almacenamiento."));
            return;
        }
        if (claiming.containsKey(id)) {
            player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-028",
                    "§eTu cofre anterior está pendiente de revisión por administración."));
            return;
        }
        if (pending.containsKey(id)) { player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-021", "§7Tu cofre ya se está abriendo.")); return; }
        var weights=new LinkedHashMap<String,Integer>();var section=config.getConfigurationSection("cofre.premios");
        try{
            if(section==null)throw new IllegalArgumentException(Settings.text("actividades", "LegacyActivities.etiqueta-028", "Faltan premios"));
            for(String material:section.getKeys(false)){if(!section.isInt(material))throw new IllegalArgumentException(Settings.text("actividades", "LegacyActivities.texto-004", "Peso no entero"));Material type=Material.valueOf(material);if(type.isAir()||!type.isItem())throw new IllegalArgumentException(Settings.text("actividades", "LegacyActivities.texto-005", "Material inválido"));weights.put(material,section.getInt(material));}
            pending.put(id, Material.valueOf(ActivityRules.weightedPrize(ThreadLocalRandom.current().nextInt(1,101),weights)));
        }catch(IllegalArgumentException error){
            plugin.getLogger().warning("Cofre de actividad: configuración inválida: " + error.getMessage());
            player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-022",
                    "<red>Este cofre no está disponible. Avisa a administración."));
            return;
        }
        if (!savePending()) { pending.remove(id); chestStorageReady = false; player.sendMessage(Settings.message("actividades", "LegacyActivities.mensaje-023", "§cNo se pudo guardar el cofre; inténtalo más tarde.")); return; }
        Location forward = player.getLocation(); double yaw = Math.toRadians(forward.getYaw());
        Location at = forward.clone().add(-Math.sin(yaw) * 2, 1, Math.cos(yaw) * 2);
        int opening=Math.max(20,Math.min(1200,config.getInt("cofre.apertura-ticks",60)));
        visuals.chest(at,opening);
        player.showTitle(Title.title(Settings.component(Settings.text("actividades", "LegacyActivities.mensaje-024", "Abriendo..."), Settings.color("actividades", "LegacyActivities.color-008", NamedTextColor.GOLD)), Component.empty(), Title.Times.times(Duration.ofMillis(Settings.i("actividades", "LegacyActivities.ofmillis-018-arg0", 100)), Duration.ofMillis(opening * 50L), Duration.ofMillis(Settings.i("actividades", "LegacyActivities.ofmillis-019-arg0", 100)))));
        jobs.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            jobs.remove(id); Player online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline()) finishChest(online);
        }, opening));
    }
    private void finishChest(Player player) {
        if (!chestStorageReady) return;
        UUID id = player.getUniqueId();
        Material material = pending.get(id);
        if (material == null || claiming.containsKey(id)) return;

        // FIRST durably claim the entitlement, THEN deliver it. The uncertain
        // window can require human reconciliation, but never triggers auto-duplication.
        pending.remove(id);
        claiming.put(id, material);
        if (!savePending()) {
            claiming.remove(id);
            pending.put(id, material);
            chestStorageReady = false;
            return;
        }
        try { deliver(player, new ItemStack(material)); }
        catch (RuntimeException failure) {
            plugin.getLogger().severe("Cofre CLAIMING de " + id + " (" + material
                    + "): entrega fallida/indeterminada; revisión manual necesaria. " + failure.getMessage());
            return;
        }
        claiming.remove(id);
        if (!savePending()) {
            claiming.put(id, material);
            chestStorageReady = false;
            plugin.getLogger().severe("Cofre CLAIMING de " + id + " (" + material
                    + "): premio posiblemente entregado, pero no se pudo cerrar el registro. NO volver a entregarlo automáticamente.");
        }
        String name=config.getString("cofre.nombres."+material.name(),material.name());
        boolean rare = config.getStringList("cofre.raros").contains(material.name());
        player.showTitle(Title.title(Settings.component(name, rare ? Settings.color("actividades", "LegacyActivities.color-009", NamedTextColor.AQUA) : Settings.color("actividades", "LegacyActivities.color-010", NamedTextColor.GOLD)), Component.empty(), Title.Times.times(Duration.ofMillis(Settings.i("actividades", "LegacyActivities.ofmillis-020-arg0", 100)), Duration.ofSeconds(rare ? 3 : 2), Duration.ofMillis(Settings.i("actividades", "LegacyActivities.ofmillis-021-arg0", 400)))));
        player.playSound(player.getLocation(), rare ? Settings.sound("actividades", "LegacyActivities.sound-011", Sound.UI_TOAST_CHALLENGE_COMPLETE) : material == Material.COAL_ORE || material == Material.IRON_ORE || material == Material.COPPER_ORE ? Settings.sound("actividades", "LegacyActivities.sound-012", Sound.ENTITY_CAT_HURT) : Settings.sound("actividades", "LegacyActivities.sound-013", Sound.ENTITY_PLAYER_LEVELUP),Settings.f("actividades", "LegacyActivities.playsound-022-arg2", 1),Settings.f("actividades", "LegacyActivities.playsound-023-arg3", 1));
    }

    /** Legacy root UUID=material entries are accepted, alongside new claims.<uuid>.material. */
    private boolean loadPending() {
        pending.clear(); claiming.clear();
        File file = new File(plugin.getDataFolder(), "activity-pending.yml");
        if (file.exists() && !file.isFile()) {
            plugin.getLogger().severe(Settings.plain("actividades", "LegacyActivities.mensaje-029",
                    "La ruta de activity-pending.yml no es un archivo regular. Cofres desactivados."));
            return false;
        }
        if (!file.isFile()) return true;
        var data = new YamlConfiguration();
        try { data.load(file); }
        catch (IOException | InvalidConfigurationException failure) {
            plugin.getLogger().severe("No se pudo leer activity-pending.yml. Cofres desactivados para proteger premios: " + failure.getMessage());
            return false;
        }
        for (String id : data.getKeys(false)) {
            if (id.equals("claims")) continue;
            try { pending.put(UUID.fromString(id), Material.valueOf(data.getString(id, ""))); }
            catch (IllegalArgumentException error) {
                plugin.getLogger().severe(Settings.plain("actividades", "LegacyActivities.mensaje-025", "Cofre pendiente inválido: {id}", "id", id));
                return false; // Invalid records must not be silently discarded on next save.
            }
        }
        var claims = data.getConfigurationSection("claims");
        if (data.contains("claims") && claims == null) {
            plugin.getLogger().severe(Settings.plain("actividades", "LegacyActivities.mensaje-030",
                    "La sección claims de activity-pending.yml está dañada."));
            return false;
        }
        if (claims != null) for (String id : claims.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(id);
                Material material = Material.valueOf(claims.getString(id + ".material", ""));
                claiming.put(playerId, material);
                pending.remove(playerId);
                plugin.getLogger().warning("Cofre CLAIMING pendiente de revisión: jugador=" + id + " material=" + material);
            } catch (IllegalArgumentException error) {
                plugin.getLogger().severe("Registro CLAIMING corrupto " + id + "; los cofres no se habilitarán hasta repararlo.");
                return false;
            }
        }
        return true;
    }
    private boolean savePending() {
        var data = new YamlConfiguration();
        pending.forEach((id, material) -> data.set(id.toString(), material.name()));
        claiming.forEach((id, material) -> data.set("claims." + id + ".material", material.name()));
        Path file = new File(plugin.getDataFolder(), "activity-pending.yml").toPath();
        try {
            AtomicFileStore.writeUtf8(file, data.saveToString());
            return true;
        } catch (IOException error) {
            plugin.getLogger().severe(Settings.plain("actividades", "LegacyActivities.mensaje-026", "No se pudo guardar cofre pendiente: {error-getmessage}", "error-getmessage", error.getMessage()));
            return false;
        }
    }

}
