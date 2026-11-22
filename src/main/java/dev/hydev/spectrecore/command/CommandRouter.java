package dev.hydev.spectrecore.command;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BiFunction;

/** Only /spectrecore is registered with Bukkit. Legacy names are private handler IDs. */
public final class CommandRouter {
    public static final class Handler extends Command {
        private CommandExecutor executor;
        private TabCompleter completer;
        Handler(String name){super(name);}
        public void setExecutor(CommandExecutor value){executor=value;}
        public void setTabCompleter(TabCompleter value){completer=value;}
        @Override public boolean execute(CommandSender sender,String label,String[] args){return executor!=null&&executor.onCommand(sender,this,label,args);}
        @Override public List<String> tabComplete(CommandSender sender,String alias,String[] args){var values=completer==null?List.<String>of():completer.onTabComplete(sender,this,alias,args);return values==null?List.of():values;}
    }
    private final SpectreCorePlugin plugin;
    private final BiFunction<CommandSender,String[],Boolean> core;
    private final Map<String,Handler> handlers=new LinkedHashMap<>();
    public CommandRouter(SpectreCorePlugin plugin,BiFunction<CommandSender,String[],Boolean> core) {
        this.plugin=plugin;this.core=core;
        CommandRoutes.ALL.stream().map(CommandRoutes.Route::target).filter(t->!t.equals("$core")).distinct().forEach(name->handlers.put(name,new Handler(name)));
    }
    public static CommandRouter of(JavaPlugin plugin){return ((SpectreCorePlugin)plugin).commands();}
    public Handler get(String name){return handlers.get(name);}
    public Set<String> names(){return Collections.unmodifiableSet(handlers.keySet());}
    private boolean access(CommandSender sender,CommandRoutes.Route route) {
        return CommandRoutes.allowed(route,sender::hasPermission,sender instanceof ConsoleCommandSender);
    }
    private boolean enabled(CommandRoutes.Route route){return route.module().isEmpty()||plugin.moduleConfigs().active(route.module());}
    private String usage(CommandRoutes.Route route){return Settings.module("nucleo/comandos.yml").getString("rutas."+route.key()+".uso",route.usage());}
    private String description(CommandRoutes.Route route){return Settings.module("nucleo/comandos.yml").getString("rutas."+route.key()+".descripcion",route.description());}
    private void say(CommandSender sender,String key,String fallback,Object... bindings){sender.sendMessage(Settings.message("nucleo","CommandRouter."+key,fallback,bindings));}
    public boolean dispatch(CommandSender sender,String[] args) {
        if(args.length==0||args[0].equalsIgnoreCase("ayuda")||args[0].equalsIgnoreCase("help")){help(sender,args.length>1?args[1]:"");return true;}
        var match=CommandRoutes.resolve(args);
        if(match==null){
            // Existing subcommands already under /spectrecore retain their paths.
            if(Set.of("mineria","combate","agricultura","modules","skills","habilidades","modelo","integraciones","diagnostico","diagnostics").contains(args[0].toLowerCase(Locale.ROOT)))return core.apply(sender,args);
            help(sender,args[0]);return true;
        }
        var route=match.route();
        if(!access(sender,route)){say(sender,"sin-permiso","&cNo tienes permiso para este comando.");return true;}
        if(!enabled(route)){say(sender,"desactivado","&cEsta función está desactivada.");return true;}
        if(route.parameters().isEmpty()&&args.length>route.words().size()){say(sender,"uso","&eUso: {uso}","uso",usage(route));return true;}
        String[] forwarded=match.arguments().toArray(String[]::new);
        if(route.target().equals("$core"))return core.apply(sender,forwarded);
        Handler handler=handlers.get(route.target());
        if(handler==null||handler.executor==null){say(sender,"desactivado","&cEsta función está desactivada.");return true;}
        return handler.execute(sender,"spectrecore "+route.path(),forwarded);
    }
    private void help(CommandSender sender,String group) {
        say(sender,"cabecera","&6✦ Hydev &7— comandos");
        if (sender.hasPermission("spectrecore.admin")) sender.sendMessage("§7Controles de administración: /spectrecore integraciones | /spectrecore modelo vista|colocar|quitar|lista · /spectrecore diagnostico");
        if(group.isEmpty()) {
            var groups=new TreeSet<String>();for(var route:CommandRoutes.ALL)if(access(sender,route)&&enabled(route))groups.add(route.words().getFirst());
            say(sender,"categorias","&eCategorías: &f{categorias}","categorias",String.join(", ",groups));
            say(sender,"ayuda","&7Consulta &f/spectrecore ayuda <categoria>&7. Usa Tab para completar.");return;
        }
        boolean found=false;
        for(var route:CommandRoutes.ALL)if(route.words().getFirst().equalsIgnoreCase(group)&&access(sender,route)&&enabled(route)) {
            say(sender,"entrada","&e{uso} &8— &7{descripcion}","uso",usage(route),"descripcion",description(route));found=true;
            if(route.path().equals("cocina"))say(sender,"cocina","&7Ayuda, recetas y estado son consultas. Reparar y recargar requieren permiso administrativo.");
        }
        if(!found)say(sender,"sin-rutas","&7No hay comandos disponibles para esa categoría.");
    }
    public List<String> complete(CommandSender sender,String[] args) {
        if(args.length==0)return List.of();
        TreeSet<String> result=new TreeSet<>(CommandRoutes.children(args,r->access(sender,r)&&enabled(r)));
        if(args.length==1&&"ayuda".startsWith(args[0].toLowerCase(Locale.ROOT)))result.add("ayuda");
        if(args.length==2&&Set.of("ayuda","help").contains(args[0].toLowerCase(Locale.ROOT))) {
            for(var route:CommandRoutes.ALL)if(access(sender,route)&&enabled(route))result.add(route.words().getFirst());
        } else {
            var match=CommandRoutes.resolve(args);
            if(match!=null&&access(sender,match.route())&&enabled(match.route())) {
                var route=match.route();int remaining=args.length-route.words().size();
                if(remaining>0) {
                    if(route.target().equals("cocina")) {
                        boolean admin=sender.hasPermission("spectrecore.admin")||sender.hasPermission("spectrecore.cooking.admin")||sender.hasPermission("hydev.cocina.admin");
                        if(remaining==1)result.addAll(admin?List.of("ayuda","libro","recetas","estado","reparar","recargar"):List.of("ayuda","libro","recetas","estado"));
                    } else if(route.path().equals("demo")&&remaining==1) {
                        for(String skill:List.of("botania","necrotica","asterion","ultimo-juramento","sangre-primer-dragon"))if(plugin.moduleConfigs().active("equipamiento."+skill))result.add(skill);
                    } else if(route.path().startsWith("slayer ")&&!route.target().equals("slayerstats")) {
                        if(remaining==1)result.addAll(List.of("revenant","necromancer","spider"));
                        else if(remaining==2&&(route.target().equals("activarslayer")||route.target().equals("contratarslayer")))result.addAll(List.of("1","2","3"));
                    } else if(route.path().equals("tesoros gema")&&remaining==1)result.addAll(List.of("basica","intermedia","superior"));
                    else if(route.path().startsWith("tesoros ")&&!route.path().equals("tesoros abrir")&&remaining==1)result.addAll(List.of("1","2","3"));
                    else if((route.path().startsWith("portal ")&&!route.path().equals("portal limpiar")||route.path().equals("blink"))&&remaining==1)plugin.getServer().getOnlinePlayers().forEach(p->result.add(p.getName()));
                    else {var handler=handlers.get(route.target());if(handler!=null)result.addAll(handler.tabComplete(sender,"spectrecore",match.arguments().toArray(String[]::new)));}
                }
            }
        }
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return result.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
