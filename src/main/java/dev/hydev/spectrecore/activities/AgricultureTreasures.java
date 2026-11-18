package dev.hydev.spectrecore.activities;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.mining.MiningVisuals;
import dev.hydev.spectrecore.mining.PlacedOres;
import dev.hydev.spectrecore.treasure.TreasureCooldowns;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.CaveVines;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AgricultureTreasures implements Listener {
    private final JavaPlugin plugin; private final TreasureCooldowns cooldowns;
    private final MmoBridge mmo = new MmoBridge(); private final MiningVisuals visuals; private final PlacedOres placed;
    private final Set<String> reported = new HashSet<>(); private YamlConfiguration config;
    public AgricultureTreasures(JavaPlugin plugin, TreasureCooldowns cooldowns) {
        this.plugin = plugin; this.cooldowns = cooldowns; visuals = new MiningVisuals(plugin);
        placed = new PlacedOres(plugin, "placed_crop_blocks_v1", material -> material == Material.PUMPKIN || material == Material.MELON);
    }
    public void enable() { reload(); Bukkit.getPluginManager().registerEvents(this, plugin); Bukkit.getPluginManager().registerEvents(placed, plugin); visuals.enable(); }
    public void disable() { HandlerList.unregisterAll(this); HandlerList.unregisterAll(placed); visuals.disable(); }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "tesoros/agricultura.yml");
        visuals.configure(config.getBoolean("visuales.activados", true), config.getInt("visuales.max-animaciones", 32)); reported.clear();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void broken(BlockBreakEvent e) {
        boolean relocated = placed.consume(e.getBlock());
        if (relocated && config.getBoolean("cosecha.excluir-calabazas-sandias-colocadas", true)) return;
        harvest(e.getPlayer(), e.getBlock());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void picked(PlayerHarvestBlockEvent e) {
        if (config.getBoolean("cosecha.incluir-recoleccion-bayas", true) && !e.getItemsHarvested().isEmpty()) harvest(e.getPlayer(), e.getHarvestedBlock());
    }
    private void harvest(Player player, Block block) {
        int tier = dev.hydev.spectrecore.config.Gameplay.tier("agricultura", block.getType().name());
        if (tier == 0 || !player.hasPermission("tesoros.agricultura.tier" + tier) || !cooldowns.ready(player.getUniqueId())) return;
        if (config.getBoolean("cosecha.solo-supervivencia", false) && player.getGameMode() != GameMode.SURVIVAL) return;
        var data = block.getBlockData();
        if (config.getBoolean("cosecha.solo-maduros", false) && data instanceof Ageable age && age.getAge() < age.getMaximumAge()) return;
        if (data instanceof CaveVines vine && !vine.isBerries()) return;
        if (data instanceof CaveVinesPlant vine && !vine.isBerries()) return;
        double chance = config.getDouble("probabilidades.agricultura.tier" + tier, 0);
        if (!Double.isFinite(chance) || ThreadLocalRandom.current().nextDouble(100) >= Math.max(0, Math.min(100, chance))) return;
        String id = config.getString("botin.tier" + tier, "AGRICULTURA_T" + tier);
        var reward = mmo.build(config.getString("tipo","CONSUMABLE"), id, config.getInt("cantidad",1));
        if (reward == null) {
            if (reported.add(id)) plugin.getLogger().warning(Settings.plain("tesoros", "AgricultureTreasures.mensaje-001", "Agricultura: falta CONSUMABLE:{id} o MMOItems no pudo generarlo.", "id", id));
            player.sendMessage(Settings.message("tesoros", "AgricultureTreasures.mensaje-002", "§6§l[Tesoro Agricultura] §cRecompensa no disponible; avisa a administración.")); return;
        }
        player.getInventory().addItem(reward.clone()).values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item, drop -> { drop.setOwner(player.getUniqueId()); drop.setPickupDelay(0); }));
        cooldowns.awarded(player.getUniqueId()); visuals.harvest(block.getLocation().add(.5, .5, .5), tier);
        Component name = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName() ? reward.getItemMeta().displayName() : Settings.component(id, Settings.color("tesoros", "AgricultureTreasures.color-001", NamedTextColor.YELLOW));
        player.showTitle(Title.title(Settings.component(Settings.text("tesoros", "AgricultureTreasures.mensaje-003", "¡TESORO ENCONTRADO!"), Settings.color("tesoros", "AgricultureTreasures.color-002", NamedTextColor.GOLD)), name, Title.Times.times(Duration.ofMillis(Settings.i("tesoros", "AgricultureTreasures.ofmillis-001-arg0", 150)), Duration.ofSeconds(Settings.i("tesoros", "AgricultureTreasures.ofseconds-002-arg0", 2)), Duration.ofMillis(Settings.i("tesoros", "AgricultureTreasures.ofmillis-003-arg0", 350)))));
        player.playSound(player.getLocation(), Settings.sound("tesoros", "AgricultureTreasures.sound-003", Sound.ENTITY_EXPERIENCE_ORB_PICKUP),Settings.f("tesoros", "AgricultureTreasures.playsound-004-arg2", 1),Settings.f("tesoros", "AgricultureTreasures.playsound-005-arg3", 1.5f));
        player.sendMessage(Settings.component(Settings.text("tesoros", "AgricultureTreasures.mensaje-004", "[Tesoro Agricultura] ¡Obtuviste "), Settings.color("tesoros", "AgricultureTreasures.color-004", NamedTextColor.GOLD)).append(name).append(Settings.component(Settings.text("tesoros", "AgricultureTreasures.mensaje-005", "!"))));
    }
    public boolean preview(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spectrecore.admin") && !sender.hasPermission("hydev.admin")) { sender.sendMessage(Settings.message("tesoros", "AgricultureTreasures.mensaje-006", "Sin permiso.")); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage(Settings.message("tesoros", "AgricultureTreasures.mensaje-007", "Usa la demo dentro del juego.")); return true; }
        int tier = 0; if (args.length >= 2) try { tier = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
        if (tier < 1 || tier > 3) { sender.sendMessage(Settings.message("tesoros", "AgricultureTreasures.mensaje-008", "/spectrecore tesoros agricultura <1|2|3>")); return true; }
        var block = player.getTargetBlockExact(6);
        visuals.harvest(block == null ? player.getLocation() : block.getLocation().add(.5, .5, .5), tier);
        sender.sendMessage(Settings.message("tesoros", "AgricultureTreasures.mensaje-009", "§7Demostración sin recompensa ni cooldown.")); return true;
    }
}
