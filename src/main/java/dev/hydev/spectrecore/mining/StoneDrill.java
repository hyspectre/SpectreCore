package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-block ore mining and rock extraction: no XP or loot from extra
 * clearance, no loading chunks and no protected-region changes.
 * Distinct from Mitrilo's long 1x2 tunnel Barrena.
 */
public final class StoneDrill implements Listener {
    private static final List<BlockFace> NEIGHBORS = List.of(BlockFace.UP,
            BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH);
    private final JavaPlugin plugin;
    private final MmoBridge mmo = new MmoBridge();
    private final MithrilRegionAccess regions;
    private final MiningProgression progression;
    private final Map<UUID, Long> lastBreak = new HashMap<>(), lastRequirement = new HashMap<>();
    private boolean active;

    public StoneDrill(JavaPlugin plugin) {
        this.plugin=plugin;
        regions=new MithrilRegionAccess(plugin);
        progression=new MiningProgression(plugin);
    }
    private YamlConfiguration config() {
        return ModuleConfiguration.module(plugin,"tesoros/mineria.yml");
    }
    private int tier(ItemStack item) {
        if(item==null || item.getType().isAir()
                || !"TOOL".equalsIgnoreCase(mmo.type(item)))return 0;
        String id=mmo.id(item);
        List<String> upgrades=config().getStringList("taladro.tipos");
        for(int i=0;i<upgrades.size();i++)if(id.equals(upgrades.get(i)))return i+1;
        return 0;
    }
    private boolean ore(Block block) {
        Material material = block.getType();
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private boolean stone(Block block) {
        return !ore(block) && config().getStringList("taladro.bloques").contains(block.getType().name());
    }
    public void enable(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        active=true;
    }
    public void disable(){
        if(active)HandlerList.unregisterAll(this);
        active=false;
        lastBreak.clear();
        lastRequirement.clear();
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(BlockDamageEvent event){
        Player player=event.getPlayer();
        int tier=tier(player.getInventory().getItemInMainHand());
        if(tier==0)return;
        String requiredStage=tier==3?"resonancia":tier==2?"duendes":"paladio";
        if(!progression.meets(player,requiredStage)){
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - lastRequirement.getOrDefault(player.getUniqueId(), 0L) >= 2500L) {
                lastRequirement.put(player.getUniqueId(), now);
                player.sendMessage(Settings.message("trabajos","MiningProgression.requisito-herramienta",
                        "<yellow>Necesitas Minería nivel {nivel} para utilizar esta herramienta.",
                        "nivel",progression.required(requiredStage)));
            }
            return;
        }
        if(player.getGameMode()!=GameMode.SURVIVAL
                || !regions.canBreak(player,event.getBlock())){
            event.setCancelled(true);return;
        }
        if (ore(event.getBlock())) return;
        if (stone(event.getBlock())) event.setInstaBreak(true);
        else event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakRock(BlockBreakEvent event){
        Player player=event.getPlayer();
        int tier=tier(player.getInventory().getItemInMainHand());
        if(tier==0)return;
        Block rock=event.getBlock();
        String requiredStage=tier==3?"resonancia":tier==2?"duendes":"paladio";
        if(!progression.meets(player,requiredStage)
                || player.getGameMode()!=GameMode.SURVIVAL
                || !regions.canBreak(player,rock)){
            event.setCancelled(true);return;
        }
        if (ore(rock)) return;
        if (!stone(rock)) { event.setCancelled(true); return; }
        long now=System.currentTimeMillis();
        int delay=config().getInt("taladro.intervalo-milisegundos.nivel"+tier,500);
        if(now-lastBreak.getOrDefault(player.getUniqueId(),0L)<delay){
            event.setCancelled(true);return;
        }
        lastBreak.put(player.getUniqueId(),now);
        int extras=config().getInt("taladro.extras.nivel"+tier,0);
        if(extras<=0)return;
        UUID owner=player.getUniqueId();
        org.bukkit.Location origin=rock.getLocation();
        String heldId=mmo.id(player.getInventory().getItemInMainHand());
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled() || !origin.getBlock().getType().isAir()
                    || !active||!player.isOnline()||!player.getUniqueId().equals(owner)
                    ||player.getWorld()!=origin.getWorld()
                    ||player.getLocation().distanceSquared(origin)>64
                    ||!heldId.equals(mmo.id(player.getInventory().getItemInMainHand())))return;
            int completed=0;
            Block center=origin.getBlock();
            for(BlockFace face:NEIGHBORS){
                if(completed>=extras)break;
                Block target=center.getRelative(face);
                if(!target.getWorld().isChunkLoaded(target.getX()>>4,target.getZ()>>4)
                        ||!stone(target)||!regions.canBreak(player,target))continue;
                // Auxiliary clearance gives no drops and no AuraSkills XP.
                target.setType(Material.AIR,false);
                completed++;
            }
            if(completed>0)player.playSound(origin,Sound.BLOCK_DEEPSLATE_BREAK,0.45f,1.4f);
        });
    }

    @EventHandler public void quit(PlayerQuitEvent event){
        lastBreak.remove(event.getPlayer().getUniqueId());
        lastRequirement.remove(event.getPlayer().getUniqueId());
    }
}
