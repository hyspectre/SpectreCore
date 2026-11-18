package dev.hydev.spectrecore.treasure;

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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatTreasures implements Listener {
    private final JavaPlugin plugin;
    private final TreasureCooldowns cooldowns;
    private final MmoBridge mmo = new MmoBridge();
    private final MiningVisuals visuals;
    private YamlConfiguration config;
    private boolean reported;
    public CombatTreasures(JavaPlugin plugin, TreasureCooldowns cooldowns) {
        this.plugin = plugin; this.cooldowns = cooldowns; visuals = new MiningVisuals(plugin);
    }
    public void enable() { reload(); visuals.enable(); Bukkit.getPluginManager().registerEvents(this, plugin); }
    public void disable() { HandlerList.unregisterAll(this); visuals.disable(); }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "tesoros/combate.yml");
        visuals.configure(config.getBoolean("visuales.activados", true), config.getInt("visuales.max-animaciones", 32)); reported = false;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent event) {
        int tier = dev.hydev.spectrecore.config.Gameplay.tier("combate", event.getEntityType().name());
        Player player = event.getEntity().getKiller();
        if (tier == 0 || player == null || !player.isOnline() || !player.hasPermission("tesoros.combate.tier" + tier) || !cooldowns.ready(player.getUniqueId())) return;
        double chance = config.getDouble("probabilidades.combate.tier" + tier, 0);
        if (!Double.isFinite(chance) || ThreadLocalRandom.current().nextDouble(100) >= Math.max(0, Math.min(100, chance))) return;
        String type = config.getString("botin.tipo", "MATERIAL"), id = config.getString("botin.id", "ALMA_1");
        var reward = mmo.build(type, id, config.getInt("cantidad",1));
        if (reward == null) {
            if (!reported) { plugin.getLogger().warning(Settings.plain("tesoros", "CombatTreasures.mensaje-001", "Almas: no se pudo generar {type}:{id}. Revisa MMOItems y el ID.", "type", type, "id", id)); reported = true; }
            player.sendMessage(Settings.message("tesoros", "CombatTreasures.mensaje-002", "§f⧇ §9§lALMAS §cLa recompensa no está disponible; avisa a administración.")); return;
        }
        player.getInventory().addItem(reward.clone()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left, drop -> {
            drop.setOwner(player.getUniqueId()); drop.setThrower(player.getUniqueId()); drop.setPickupDelay(0);
        }));
        cooldowns.awarded(player.getUniqueId());
        visuals.soul(event.getEntity().getLocation().add(0, .35, 0), tier);
        player.playSound(player.getLocation(), Settings.sound("tesoros", "CombatTreasures.sound-001", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("tesoros", "CombatTreasures.playsound-001-arg2", 1),Settings.f("tesoros", "CombatTreasures.playsound-002-arg3", 1.5f));
        player.showTitle(Title.title(Settings.component(Settings.text("tesoros", "CombatTreasures.mensaje-003", "¡ALMA RECOLECTADA!"), Settings.color("tesoros", "CombatTreasures.color-002", NamedTextColor.BLUE)), Settings.component(Settings.text("tesoros", "CombatTreasures.mensaje-004", "⧇"), Settings.color("tesoros", "CombatTreasures.color-003", NamedTextColor.WHITE)), Title.Times.times(Duration.ofMillis(Settings.i("tesoros", "CombatTreasures.ofmillis-003-arg0", 150)), Duration.ofSeconds(Settings.i("tesoros", "CombatTreasures.ofseconds-004-arg0", 2)), Duration.ofMillis(Settings.i("tesoros", "CombatTreasures.ofmillis-005-arg0", 350)))));
    }
    public boolean preview(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spectrecore.admin") && !sender.hasPermission("hydev.admin")) { sender.sendMessage(Settings.message("tesoros", "CombatTreasures.mensaje-005", "§cNo tienes permiso.")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("tesoros", "CombatTreasures.mensaje-006", "Usa la demostración dentro del juego.")); return true; }
        int tier = 0;
        if (args.length >= 3 && args[1].equalsIgnoreCase("alma")) try { tier = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
        if (tier < 1 || tier > 3) { sender.sendMessage(Settings.message("tesoros", "CombatTreasures.mensaje-007", "/spectrecore tesoros combate <1|2|3>")); return true; }
        var block = player.getTargetBlockExact(6);
        Location at = block == null ? player.getLocation().add(player.getLocation().getDirection().multiply(2)) : block.getLocation().add(.5, .5, .5);
        visuals.soul(at, tier);
        sender.sendMessage(Settings.message("tesoros", "CombatTreasures.mensaje-008", "§7Vista previa de alma: no entrega objetos ni consume cooldown.")); return true;
    }
}
