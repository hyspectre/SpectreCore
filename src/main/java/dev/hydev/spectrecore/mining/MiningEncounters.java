package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.integration.WorldGuardBridge;
import dev.hydev.spectrecore.migration.MmoBridge;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Player-triggered, natural-ore encounters. MythicMobs defines the creatures;
 * SpectreCore alone owns chance, provenance, cooldowns and rewards.
 */
public final class MiningEncounters implements Listener {
    private final JavaPlugin plugin;
    private final MiningLuck luck;
    private final MythicMiningSpawns mythic;
    private final MiningProgression progression;
    private final WorldGuardBridge regions;
    private final MmoBridge mmo = new MmoBridge();
    private final NamespacedKey kindKey, ownerKey;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, String> active = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private boolean enabled;

    public MiningEncounters(JavaPlugin plugin, MiningLuck luck) {
        this.plugin = plugin; this.luck = luck;
        mythic = new MythicMiningSpawns(plugin);
        progression = new MiningProgression(plugin);
        regions = new WorldGuardBridge(plugin.getLogger());
        kindKey = new NamespacedKey(plugin, "mining_encounter_kind");
        ownerKey = new NamespacedKey(plugin, "mining_encounter_owner");
    }
    private YamlConfiguration config() {
        return ModuleConfiguration.module(plugin, "tesoros/mineria.yml");
    }
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        enabled=true;
    }
    public void disable() {
        if(enabled)HandlerList.unregisterAll(this);
        enabled=false;
        for(UUID id : Set.copyOf(active.keySet())) {
            Entity mob = Bukkit.getEntity(id);
            if(mob != null)mob.remove();
        }
        active.clear();
        pending.clear();
        cooldowns.clear();
    }
    private boolean trigger(String kind, Material ore) {
        return config().getStringList("encuentros."+kind+".menas").contains(ore.name());
    }
    public void naturalOreBroken(Player player, Block block) {
        if (!enabled || player.getGameMode()!=GameMode.SURVIVAL || !player.isOnline())return;
        String kind = trigger("duende",block.getType())?"duende":
                trigger("prospector",block.getType())?"prospector":null;
        if(kind==null) return;
        if(!progression.meets(player,kind.equals("duende")?"duendes":"resonancia"))return;
        String key="encuentros."+kind+".";
        YamlConfiguration cfg=config();
        int cap=cfg.getInt("encuentros.max-activos",12);
        if(active.size()+pending.size()>=cap)return;
        long now=System.currentTimeMillis();
        Map<UUID,Long> timer=cooldowns.computeIfAbsent(kind,unused->new HashMap<>());
        long wait=Math.max(0,cfg.getLong(key+"recarga-segundos",90))*1000L;
        if(now-timer.getOrDefault(player.getUniqueId(),0L)<wait)return;
        String reserve=kind+":"+player.getUniqueId();
        if(!pending.add(reserve))return;
        double chance=MiningLuckRules.chance(
                cfg.getDouble(key+"probabilidad",0),
                luck.stat(player),
                cfg.getDouble("suerte.encuentros.incremento-relativo-por-punto",1.5),
                cfg.getDouble("suerte.encuentros.multiplicador-maximo",2.0));
        if(ThreadLocalRandom.current().nextDouble(100)>=chance) {
            pending.remove(reserve);
            return;
        }
        // BlockBreakEvent MONITOR still sees the ore; spawn only after removal.
        Location original=block.getLocation();
        UUID owner=player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin,()->{
            try {
                if(!enabled || !plugin.isEnabled() || !player.isOnline() ||
                        !player.getUniqueId().equals(owner) || player.getWorld()!=original.getWorld() ||
                        player.getLocation().distanceSquared(original)>400) return;
                if(active.size()>=cap)return;
                Location spawn=findLocation(player,original);
                if(spawn==null)return;
                String mobId=cfg.getString(key+"mob","");
                Entity spawned=mythic.spawn(mobId,spawn);
                if(!(spawned instanceof LivingEntity living)) {
                    if(spawned!=null)spawned.remove();
                    return;
                }
                spawned.getPersistentDataContainer().set(kindKey,PersistentDataType.STRING,kind);
                spawned.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,owner.toString());
                active.put(spawned.getUniqueId(),kind);
                timer.put(owner,System.currentTimeMillis());
                player.sendMessage(kind.equals("duende")
                        ? Settings.message("tesoros","MiningEncounters.duende",
                            "<gold>✦ ¡Un Duende del Oro salió de las rocas!")
                        : Settings.message("tesoros","MiningEncounters.prospector",
                            "<light_purple>✦ ¡Has despertado a un Prospector Antiguo!"));
                player.playSound(player.getLocation(),
                        Settings.sound("tesoros","MiningEncounters.sonido-encuentro",Sound.BLOCK_AMETHYST_BLOCK_CHIME),
                        Settings.f("tesoros","MiningEncounters.volumen",0.7f),
                        kind.equals("duende")
                                ? Settings.f("tesoros","MiningEncounters.tono-duende",1.7f)
                                : Settings.f("tesoros","MiningEncounters.tono-prospector",0.7f));
            } finally { pending.remove(reserve); }
        });
    }

    private Location findLocation(Player owner, Location ore) {
        if(ore.getWorld()==null || !ore.getWorld().isChunkLoaded(ore.getBlockX()>>4,ore.getBlockZ()>>4))return null;
        if(!regions.canBuild(owner,ore))return null;
        Location playerLoc=owner.getLocation();
        int[][] directions={{2,0},{-2,0},{0,2},{0,-2},{2,2},{-2,-2}};
        for(int[] direction:directions) {
            Location attempt=playerLoc.clone().add(direction[0],0,direction[1]);
            if(!attempt.getWorld().isChunkLoaded(attempt.getBlockX()>>4,attempt.getBlockZ()>>4))continue;
            if(attempt.getBlock().isPassable() && attempt.clone().add(0,1,0).getBlock().isPassable()
                    && attempt.clone().add(0,-1,0).getBlock().getType().isSolid()
                    && regions.canBuild(owner,attempt))return attempt;
        }
        return null;
    }

    private void reward(Player player,String kind,String suffix,double stat) {
        String root="encuentros."+kind+".botin."+suffix+".";
        var cfg=config();
        double chance=MiningLuckRules.chance(
                cfg.getDouble(root+"probabilidad",0),stat,
                cfg.getDouble("suerte.botin.incremento-relativo-por-punto",1),
                cfg.getDouble("suerte.botin.multiplicador-maximo",1.8));
        if(ThreadLocalRandom.current().nextDouble(100)>=chance)return;
        int min=cfg.getInt(root+"minimo",1),max=cfg.getInt(root+"maximo",1);
        if(min<1||max<min||max>32)return;
        int amount=ThreadLocalRandom.current().nextInt(min,max+1);
        if(suffix.equals("paladio"))
            amount=MiningLuckRules.quantity(amount,32,stat,
                    cfg.getDouble("suerte.cantidad-paladio.por-punto",1),
                    cfg.getDouble("suerte.cantidad-paladio.maximo-porcentaje",35),
                    ThreadLocalRandom.current().nextDouble(100));
        String id=cfg.getString(root+"id","");
        String type=cfg.getString(root+"tipo","MATERIAL");
        if(!id.matches("[A-Z0-9_]{2,64}") || !type.equals("MATERIAL"))return;
        ItemStack item=mmo.build(type,id,amount);
        if(item==null) {
            if(warned.add(type+":"+id))
                plugin.getLogger().warning("Encuentros mineros: falta material de recompensa "+type+":"+id);
            return;
        }
        for(ItemStack remaining:player.getInventory().addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(),remaining,drop->{
                drop.setOwner(player.getUniqueId());
                drop.setThrower(player.getUniqueId());
                drop.setPickupDelay(10);
            });
        }
        player.sendMessage(Settings.message("tesoros","MiningEncounters.recompensa",
                "<green>✦ Hallazgo minero: <white>{cantidad} {nombre}",
                "cantidad",amount,"nombre",cfg.getString(root+"nombre","Material raro")));
    }

    @EventHandler public void death(EntityDeathEvent event) {
        Entity entity=event.getEntity();
        String kind=entity.getPersistentDataContainer().get(kindKey,PersistentDataType.STRING);
        if(!"duende".equals(kind)&&!"prospector".equals(kind))return;
        active.remove(entity.getUniqueId());
        // No vanilla drops alongside the native rewards.
        event.getDrops().clear();
        event.setDroppedExp(0);
        String ownerId=entity.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING);
        if(ownerId==null || event.getEntity().getKiller()==null)return;
        final UUID id;
        try{id=UUID.fromString(ownerId);}catch(IllegalArgumentException invalid){return;}
        Player owner=Bukkit.getPlayer(id);
        if(owner==null || !owner.isOnline() || owner.getGameMode()!=GameMode.SURVIVAL
                || owner.getWorld()!=entity.getWorld()
                || owner.getLocation().distanceSquared(entity.getLocation())>48*48)return;
        double stat=luck.stat(owner);
        if(kind.equals("duende"))reward(owner,kind,"chatarra",stat);
        else {
            reward(owner,kind,"chatarra",stat);
            reward(owner,kind,"paladio",stat);
            reward(owner,kind,"fragmento",stat);
        }
    }
}
