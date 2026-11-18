package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.File;
import java.util.*;

/** Session-aware extraction. Dungeons+ remains the authority for ending a run. */
public final class PortalModule implements Listener {
    private static final class Gate {
        final UUID id; final String session; final Location center; final long expires; final boolean demo;
        final Set<UUID> members = new HashSet<>(); final List<Display> displays = new ArrayList<>();
        Gate(UUID id, String session, Location center, long expires, boolean demo) { this.id=id; this.session=session; this.center=center; this.expires=expires; this.demo=demo; }
    }
    private final JavaPlugin plugin; private final NamespacedKey key; private YamlConfiguration config;
    private final Map<UUID,String> sessions = new HashMap<>(); private final Map<UUID,UUID> eligible = new HashMap<>();
    private final Map<UUID,Gate> gates = new HashMap<>(); private final Map<UUID,Long> since = new HashMap<>(), pending = new HashMap<>();
    private BukkitTask timer; private int tick;
    public PortalModule(JavaPlugin plugin) { this.plugin=plugin; key=new NamespacedKey(plugin,"exit_portal_visual"); }
    public void enable() {
        reload(); Bukkit.getPluginManager().registerEvents(this,plugin);
        for (World w:Bukkit.getWorlds()) for (Chunk c:w.getLoadedChunks()) cleanupChunk(c);
        for (String name:List.of("hydevportaljoin","hydevportalleave","hydevportalcleanup","hydevportalstatus","hydevportaldemo")) if(dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name)!=null) {
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setExecutor((s,c,l,a)->command(s,c.getName(),a));
            dev.hydev.spectrecore.command.CommandRouter.of(plugin).get(name).setTabCompleter((s,c,l,a)->List.of());
        }
        timer=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);
    }
    public void reload() {
        config = dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "dungeons/portal.yml");
    }
    public void disable() { if(timer!=null)timer.cancel(); HandlerList.unregisterAll(this); new ArrayList<>(gates.keySet()).forEach(this::clear); sessions.clear(); since.clear(); pending.clear(); }
    private boolean admin(CommandSender s) { return s.hasPermission("hydev.admin")||s.hasPermission("spectrecore.admin"); }
    private boolean command(CommandSender sender,String command,String[] args) {
        if(command.equals("hydevportaljoin")||command.equals("hydevportalleave")) {
            if(!(sender instanceof ConsoleCommandSender)){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-001", "Este callback es solo de consola."));return true;}
            if(args.length<(command.equals("hydevportaljoin")?2:1)){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-002","Uso: /{command} <jugador>{valor2}","command", "spectrecore "+dev.hydev.spectrecore.command.CommandRoutes.newPath(command),"valor2", (command.equals("hydevportaljoin")?" <sesion>":"")));return true;}
            Player p=Bukkit.getPlayerExact(args[0]);if(p==null){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-003", "Jugador no conectado."));return true;}
            String session=String.join(" ",Arrays.copyOfRange(args,1,args.length));
            if(command.equals("hydevportaljoin")&&(session.isBlank()||session.contains("<")||session.contains(">"))){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-004", "Identificador de sesión vacío o sin resolver."));return true;}
            reset(p.getUniqueId());
            if(command.equals("hydevportaljoin")){sessions.put(p.getUniqueId(),session);plugin.getLogger().info(Settings.plain("dungeons", "PortalModule.mensaje-005", "Portal JOIN: {p-getname} | {session}", "p-getname", p.getName(), "session", session));}
            else sessions.remove(p.getUniqueId());
            return true;
        }
        if(!admin(sender)){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-006", "Sin permiso."));return true;}
        if(command.equals("hydevportalcleanup")){new ArrayList<>(gates.keySet()).forEach(this::clear);sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-007", "Portales retirados."));return true;}
        Player p=sender instanceof Player player?player:args.length>0?Bukkit.getPlayerExact(args[0]):null;
        if(p==null){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-008", "Indica un jugador conectado."));return true;}
        if(command.equals("hydevportaldemo")) {
            if(gates.size()>=limit()){sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-009", "Límite de portales alcanzado."));return true;}
            Location center=p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
            Gate gate=new Gate(UUID.randomUUID(),"demo",center,System.currentTimeMillis()+15000,true);gates.put(gate.id,gate);draw(gate);
            sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-010", "§7Vista previa por 15 s: no finaliza ninguna expedición."));return true;
        }
        UUID id=p.getUniqueId();Gate g=gates.get(eligible.get(id));
        sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-011", "Sesión: {sessions-get-id} | Portal: {eligible-get-id}", "sessions-get-id", sessions.get(id), "eligible-get-id", eligible.get(id)));
        sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-012", "Inicio: {since-get-id} | Pendiente hasta: {pending-get-id}", "since-get-id", since.get(id), "pending-get-id", pending.get(id)));
        if(g!=null)sender.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-013","Dentro: {inside-p-g} | Restantes: {g-members-size} | Expira en {valor3} s","inside-p-g", inside(p,g),"g-members-size", g.members.size(),"valor3", Math.max(0,(g.expires-System.currentTimeMillis())/1000)));
        return true;
    }
    private int limit(){return Math.max(1,Math.min(64,config.getInt("visuales.max-portales",16)));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void boss(EntityDeathEvent event) {
        if(event.getEntityType()!=EntityType.PIGLIN_BRUTE||!event.getEntity().getScoreboardTags().contains("HYDEV_MINO_BOSS"))return;
        UUID id=event.getEntity().getUniqueId();if(gates.containsKey(id)||gates.size()>=limit())return;
        Location death=event.getEntity().getLocation();double radius=Math.max(1,config.getDouble("radio-participantes",64));
        List<Player> nearby=death.getWorld().getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR&&p.getLocation().distanceSquared(death)<=radius*radius&&sessions.containsKey(p.getUniqueId())).sorted(Comparator.comparingDouble(p->p.getLocation().distanceSquared(death))).toList();
        if(nearby.isEmpty()){plugin.getLogger().warning(Settings.plain("dungeons", "PortalModule.mensaje-014", "Minotauro muerto sin sesiones registradas. Configura callbacks /spectrecore portal registrar y /spectrecore portal salir en Dungeons+."));return;}
        String session=sessions.get(nearby.getFirst().getUniqueId());
        Location center=new Location(death.getWorld(),death.getBlockX()+Settings.d("dungeons", "PortalModule.geometria-001", .5),death.getBlockY(),death.getBlockZ()+Settings.d("dungeons", "PortalModule.geometria-002", .5));
        Gate gate=new Gate(id,session,center,System.currentTimeMillis()+Math.max(1,config.getLong("duracion-segundos",900))*1000,false);
        for(Player p:nearby)if(session.equals(sessions.get(p.getUniqueId()))) {
            reset(p.getUniqueId());gate.members.add(p.getUniqueId());eligible.put(p.getUniqueId(),id);
        }
        gates.put(id,gate);draw(gate);
        for(UUID member:gate.members){Player p=Bukkit.getPlayer(member);if(p!=null){p.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-015", "§6✦ EL MINOTAURO HA CAÍDO ✦"));p.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-016","§7Recoge tu cofre y permanece {valor1} segundos dentro del arco para salir.","valor1", config.getInt("tiempo-dentro-segundos",5)));p.playSound(center,Settings.sound("dungeons", "PortalModule.sound-001", Sound.BLOCK_END_PORTAL_SPAWN),Settings.f("dungeons", "PortalModule.playsound-001-arg2", .6f),Settings.f("dungeons", "PortalModule.playsound-002-arg3", 1.2f));}}
    }
    private boolean inside(Player p,Gate gate){return p.getWorld().equals(gate.center.getWorld())&&Math.abs(p.getX()-gate.center.getX())<=config.getDouble("zona-extraccion.radio-horizontal",1.75)&&Math.abs(p.getZ()-gate.center.getZ())<=config.getDouble("zona-extraccion.radio-horizontal",1.75)&&p.getY()-gate.center.getY()>=config.getDouble("zona-extraccion.altura-inferior",-2)&&p.getY()-gate.center.getY()<=config.getDouble("zona-extraccion.altura-superior",4.5);}
    private void tick(){
        tick+=5;long now=System.currentTimeMillis();
        for(Gate gate:new ArrayList<>(gates.values())){
            if(now>=gate.expires||!gate.demo&&gate.members.isEmpty()){clear(gate.id);continue;}
            if(gate.center.getWorld().isChunkLoaded(gate.center.getBlockX()>>4,gate.center.getBlockZ()>>4)&&tick%10==0){
                double a=tick*Settings.d("dungeons", "PortalModule.geometria-003", .05);gate.center.getWorld().spawnParticle(Settings.particle("dungeons", "PortalModule.particle-002", Particle.REVERSE_PORTAL),gate.center.clone().add(Math.cos(a)*Settings.d("dungeons", "PortalModule.geometria-004", 1.15),2+Math.sin(a*2),0),Settings.i("dungeons", "PortalModule.spawnparticle-003-arg2", 5),Settings.d("dungeons", "PortalModule.spawnparticle-004-arg3", .2),Settings.d("dungeons", "PortalModule.spawnparticle-005-arg4", .2),Settings.d("dungeons", "PortalModule.spawnparticle-006-arg5", .08),Settings.d("dungeons", "PortalModule.spawnparticle-007-arg6", .015));
            }
            if(gate.demo)continue;
            for(UUID id:new ArrayList<>(gate.members)){
                Player p=Bukkit.getPlayer(id);
                if(p==null||!p.isOnline()||!gate.session.equals(sessions.get(id))){reset(id);continue;}
                if(!p.getWorld().equals(gate.center.getWorld())||p.getLocation().distanceSquared(gate.center)>128*128){reset(id);sessions.remove(id);continue;}
                if(p.getGameMode()==GameMode.SPECTATOR||p.isDead()||!inside(p,gate)){since.remove(id);pending.remove(id);continue;}
                Long until=pending.get(id);
                if(until!=null){if(now<until)continue;pending.remove(id);since.remove(id);p.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-017", "§cNo se confirmó tu salida. Puedes reintentar; revisa Dungeons+ con administración."));}
                long started=since.computeIfAbsent(id,unused->now), wait=Math.max(1,config.getInt("tiempo-dentro-segundos",5))*1000L;
                if(now-started>=wait){request(p,gate);continue;}
                p.sendActionBar(Settings.component(Settings.text("dungeons", "PortalModule.mensaje-018","Salida en {valor1} s · Permanece en el arco","valor1", ((wait-(now-started)+999)/1000)),Settings.color("dungeons", "PortalModule.color-003", NamedTextColor.GOLD)));
            }
        }
    }
    private void request(Player p,Gate gate){
        String template=config.getString("salida.comando","");
        pending.put(p.getUniqueId(),System.currentTimeMillis()+Math.max(1,config.getInt("salida.confirmacion-segundos",10))*1000L);
        if(template.isBlank()){p.sendMessage(Settings.message("dungeons", "PortalModule.mensaje-019", "§cEl comando de salida no está configurado. Avisa a administración."));return;}
        String command=template.replace("{player}",p.getName()).replace("{session}",gate.session);
        try{
            boolean accepted=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command);
            p.sendActionBar(Settings.component(Settings.text("dungeons", "PortalModule.mensaje-020","{valor1}","valor1", accepted?Settings.text("dungeons", "PortalModule.texto-001", "Finalizando tu expedición..."):Settings.text("dungeons", "PortalModule.texto-002", "Dungeons+ rechazó el comando de salida")),Settings.color("dungeons", "PortalModule.color-004", NamedTextColor.GOLD)));
        }catch(RuntimeException e){plugin.getLogger().warning(Settings.plain("dungeons", "PortalModule.mensaje-021", "Falló salida de portal: {e-getmessage}", "e-getmessage", e.getMessage()));}
        // Never assume dispatch=true means the dungeon accepted or ended the run.
    }
    private void reset(UUID id){
        UUID portal=eligible.remove(id);since.remove(id);pending.remove(id);
        Gate gate=gates.get(portal);if(gate!=null){gate.members.remove(id);if(gate.members.isEmpty())clear(gate.id);}
    }
    private void clear(UUID id){
        Gate gate=gates.remove(id);if(gate==null)return;
        gate.displays.forEach(Entity::remove);gate.displays.clear();
        for(UUID member:gate.members){eligible.remove(member,id);since.remove(member);pending.remove(member);}gate.members.clear();
    }
    @EventHandler public void quit(PlayerQuitEvent e){reset(e.getPlayer().getUniqueId());sessions.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void dead(PlayerDeathEvent e){since.remove(e.getEntity().getUniqueId());pending.remove(e.getEntity().getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){since.remove(e.getPlayer().getUniqueId());pending.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void chunk(ChunkLoadEvent e){cleanupChunk(e.getChunk());for(Gate gate:new ArrayList<>(gates.values()))if(gate.center.getWorld().equals(e.getWorld())&&Math.abs((gate.center.getBlockX()>>4)-e.getChunk().getX())<=1&&Math.abs((gate.center.getBlockZ()>>4)-e.getChunk().getZ())<=1)draw(gate);}
    private void cleanupChunk(Chunk c){for(Entity e:c.getEntities())if(e.getPersistentDataContainer().has(key,PersistentDataType.BYTE))e.remove();}
    private void mark(Display display){display.setPersistent(false);display.setInvulnerable(true);display.setGravity(false);display.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);display.setBrightness(new Display.Brightness(Settings.i("dungeons", "PortalModule.brillo-001-arg0", 15),Settings.i("dungeons", "PortalModule.brillo-002-arg1", 15)));display.setViewRange(Settings.f("dungeons", "PortalModule.setviewrange-008-arg0", .8f));}
    private void draw(Gate gate){
        gate.displays.forEach(Entity::remove);gate.displays.clear();World world=gate.center.getWorld();
        for(int dx=-2;dx<=2;dx++)for(int dy=0;dy<=4;dy++)if(Math.abs(dx)==2||dy==4){
            Location at=gate.center.clone().add(dx,dy,0);if(at.getY()<world.getMinHeight()||at.getY()>=world.getMaxHeight()||!world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))continue;
            Material material=dy==4?Settings.material("dungeons", "PortalModule.material-011", Material.GOLD_BLOCK):dy==2?Settings.material("dungeons", "PortalModule.material-012", Material.SEA_LANTERN):Settings.material("dungeons", "PortalModule.material-013", Material.CRYING_OBSIDIAN);
            gate.displays.add(world.spawn(at,BlockDisplay.class,d->{mark(d);d.setBlock(material.createBlockData());d.setTransformation(new Transformation(new Vector3f(-Settings.f("dungeons", "PortalModule.geometria-005", .45f),0,-Settings.f("dungeons", "PortalModule.geometria-006", .18f)),new Quaternionf(),new Vector3f(Settings.f("dungeons", "PortalModule.geometria-007", .9f),Settings.f("dungeons", "PortalModule.geometria-008", .9f),Settings.f("dungeons", "PortalModule.geometria-009", .36f)),new Quaternionf()));}));
        }
        Location label=gate.center.clone().add(0,Settings.d("dungeons", "PortalModule.geometria-010", 5.2),0);if(!world.isChunkLoaded(label.getBlockX()>>4,label.getBlockZ()>>4))return;
        gate.displays.add(world.spawn(label,TextDisplay.class,d->{mark(d);d.setBillboard(Display.Billboard.CENTER);d.setSeeThrough(false);d.setLineWidth(200);d.text(Settings.component(Settings.text("dungeons", "PortalModule.mensaje-022","{valor1}{valor2} segundos","valor1", gate.demo?Settings.text("dungeons", "PortalModule.texto-003", "Portal · Vista previa"):Settings.text("dungeons", "PortalModule.texto-004", "Salida de la Cripta\nPermanece "),"valor2", config.getInt("tiempo-dentro-segundos",5)),Settings.color("dungeons", "PortalModule.color-005", NamedTextColor.GOLD)));}));
    }
}
