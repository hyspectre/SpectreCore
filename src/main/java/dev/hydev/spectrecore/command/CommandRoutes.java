package dev.hydev.spectrecore.command;

import java.util.*;
import java.util.function.Predicate;

/** Pure routing rules. No Bukkit command registration, command strings or console impersonation. */
public final class CommandRoutes {
    public enum Access { PUBLIC, ADMIN, HYDEV_ADMIN, COOKING_ADMIN, ACTIVITIES_ADMIN, CONSOLE }
    public record Route(String path,String target,String module,Access access,String parameters,String description,List<String> prefix) {
        public List<String> words(){return List.of(path.split(" "));}
        public String key(){return path.replace(' ','.');}
        public String usage(){return "/spectrecore "+path+(parameters.isEmpty()?"":" "+parameters);}
    }
    public record Match(Route route,List<String> arguments) { }
    private static Route route(String path,String target,String module,Access access,String parameters,String description,String... prefix) {
        return new Route(path,target,module,access,parameters,description,List.of(prefix));
    }
    public static final List<Route> ALL;
    static {
        List<Route> routes=new ArrayList<>();
        routes.add(route("status","$core","",Access.PUBLIC,"","Estado de habilidades","status"));
        routes.add(route("modulos","$core","",Access.PUBLIC,"","Estado de módulos","modulos"));
        routes.add(route("reload","$core","",Access.ADMIN,"","Recargar configuración","reload"));
        routes.add(route("reset","$core","equipamiento",Access.ADMIN,"","Reiniciar tus habilidades y tiempos de espera","reset"));
        routes.add(route("demo","$core","equipamiento",Access.ADMIN,"<habilidad>","Alternar una habilidad de prueba","demo"));
        routes.add(route("blink","$core","equipamiento.ultimo-juramento",Access.CONSOLE,"<jugador>","Notificar un Blink confirmado","blink"));
        routes.add(route("toast prueba","$core","",Access.ADMIN,"<id>","Probar una notificación","toast","prueba"));
        routes.add(route("armaduras","armaduras","equipamiento.libro",Access.PUBLIC,"","Libro de armaduras"));
        routes.add(route("relicario","relicario","accesorios",Access.PUBLIC,"","Colección de reliquias y estadísticas"));
        routes.add(route("cocina","cocina","cocina",Access.PUBLIC,"<ayuda|libro|recetas|estado|reparar|recargar>","Cocina y recetario"));
        routes.add(route("trabajos","trabajo","trabajos",Access.PUBLIC,"","Abrir catálogo de contratos y oficios"));
        routes.add(route("trabajos mineria","trabajo","trabajos",Access.PUBLIC,"","Abrir contratos de minería","minero"));
        routes.add(route("trabajos agricultura","trabajo","trabajos",Access.PUBLIC,"","Abrir contratos de agricultura","granjero"));
        routes.add(route("trabajos caza","trabajo","trabajos",Access.PUBLIC,"","Abrir contratos de caza","cazador"));
        routes.add(route("trabajos estado","trabajo","trabajos",Access.PUBLIC,"","Consultar niveles de trabajo en chat","estado"));
        routes.add(route("trabajos transacciones","trabajo","trabajos",Access.ADMIN,"","Revisar compras de trabajo pendientes","transacciones"));
        routes.add(route("trabajos resolver","trabajo","trabajos",Access.ADMIN,"<uuid>","Cerrar un caso conciliado manualmente","resolver"));
        routes.add(route("resonancia","resonancia","resonancia",Access.PUBLIC,"","Carga del Pico Perdido"));
        routes.add(route("resonancia debug","debugresonancia","resonancia",Access.HYDEV_ADMIN,"","Diagnóstico de resonancia"));
        routes.add(route("item nombre","nombreitem","",Access.HYDEV_ADMIN,"","Inspeccionar y copiar el nombre del objeto"));
        routes.add(route("misiones","misiones","slayer",Access.PUBLIC,"","Abrir el catálogo de misiones"));
        routes.add(route("misiones monarquia","misiones","slayer",Access.PUBLIC,"","Abrir los contratos de Monarquía Renacida","revenant"));
        routes.add(route("misiones progresion","misiones","slayer",Access.PUBLIC,"","Ver niveles, XP y desbloqueos de Slayer","progresion"));
        routes.add(route("slayer estado","slayerstats","slayer",Access.PUBLIC,"","Progreso de misiones Slayer"));
        routes.add(route("slayer comprar telefono","comprartelefono","slayer",Access.PUBLIC,"","Obtener el Teléfono Rojo para consultar tus misiones"));
        routes.add(route("slayer contratar","contratarslayer","slayer",Access.PUBLIC,"<categoria> <1|2|3>","Contratar una cacería"));
        routes.add(route("slayer abandonar","abandonarslayer","slayer",Access.PUBLIC,"<categoria>","Abandonar una cacería"));
        routes.add(route("slayer activar","activarslayer","slayer",Access.HYDEV_ADMIN,"<categoria> <1|2|3>","Iniciar una misión Slayer"));
        routes.add(route("slayer cancelar","cancelarslayer","slayer",Access.HYDEV_ADMIN,"<categoria>","Cancelar una misión Slayer"));
        routes.add(route("slayer reiniciar","resetslayer","slayer",Access.HYDEV_ADMIN,"<categoria>","Reiniciar una misión Slayer"));
        routes.add(route("slayer invocar","slayerinvocar","slayer",Access.PUBLIC,"<categoria>","Reintentar tu propia invocación pendiente"));
        routes.add(route("portal registrar","hydevportaljoin","dungeons.portal",Access.CONSOLE,"<jugador> <sesion>","Registrar participante y sesión"));
        routes.add(route("portal salir","hydevportalleave","dungeons.portal",Access.CONSOLE,"<jugador>","Retirar el registro de un participante"));
        routes.add(route("portal estado","hydevportalstatus","dungeons.portal",Access.HYDEV_ADMIN,"[jugador]","Diagnóstico del portal"));
        routes.add(route("portal demo","hydevportaldemo","dungeons.portal",Access.HYDEV_ADMIN,"[jugador]","Previsualizar el portal"));
        routes.add(route("portal limpiar","hydevportalcleanup","dungeons.portal",Access.HYDEV_ADMIN,"","Retirar los portales activos"));
        routes.add(route("tesoros abrir","opentreasure","actividades.cofre",Access.ACTIVITIES_ADMIN,"","Abrir un cofre con premio"));
        routes.add(route("tesoros mineria","$core","tesoros.mineria",Access.HYDEV_ADMIN,"<1|2|3>","Previsualizar un tesoro minero","mineria","tesoro"));
        routes.add(route("tesoros gema","$core","resonancia",Access.HYDEV_ADMIN,"<basica|intermedia|superior>","Previsualizar una gema","mineria","gema"));
        routes.add(route("tesoros combate","$core","tesoros.combate",Access.HYDEV_ADMIN,"<1|2|3>","Previsualizar un alma","combate","alma"));
        routes.add(route("tesoros agricultura","$core","tesoros.agricultura",Access.HYDEV_ADMIN,"<1|2|3>","Previsualizar un hallazgo agrícola","agricultura"));
        for(String offer:List.of("rompemuelas","taladroroto","furiaestelar","capuchaencogedora","slimehechizado","compactador","golemcobre"))
            routes.add(route("cygnus "+offer,offer,"actividades.cygnus",Access.ACTIVITIES_ADMIN,"","Oferta de Cygnus: "+offer));
        for(String mineral:List.of("diamante","hierro","oro","cobre","lapis","redstone"))
            routes.add(route("polvos "+mineral,"polvo"+mineral,"actividades.polvos",Access.ACTIVITIES_ADMIN,"","Pago de polvo: "+mineral));
        routes.add(route("desbloquear almas","almas1","actividades.desbloqueos",Access.ACTIVITIES_ADMIN,"","Desbloquear almas de tier 1"));
        routes.add(route("desbloquear mineria","mineria1","actividades.desbloqueos",Access.ACTIVITIES_ADMIN,"","Desbloquear minería de tier 1"));
        for(int tier=1;tier<=3;tier++)routes.add(route("desbloquear slayerzombie "+tier,"slayerzombie"+tier,"actividades.desbloqueos",Access.ACTIVITIES_ADMIN,"","Desbloquear Revenant de tier "+tier));
        routes.add(route("telefono","telefono1","actividades.telefono",Access.ACTIVITIES_ADMIN,"","Abrir el menú de Slayers"));
        ALL=List.copyOf(routes);
        if(ALL.stream().map(Route::path).distinct().count()!=ALL.size())throw new IllegalStateException("Duplicate command route");
    }
    private CommandRoutes() { }
    public static boolean allowed(Route route,Predicate<String> permission,boolean console) {
        boolean admin=permission.test("spectrecore.admin"),hydev=permission.test("hydev.admin");
        return switch(route.access()) {
            case PUBLIC->true;
            case ADMIN->admin||permission.test("spectreskills.admin");
            case HYDEV_ADMIN->admin||hydev||(route.target().equals("nombreitem")&&permission.test("spectreskills.admin"));
            case COOKING_ADMIN->admin||permission.test("spectrecore.cooking.admin")||permission.test("hydev.cocina.admin");
            case ACTIVITIES_ADMIN->admin||permission.test("spectrecore.activities.admin");
            case CONSOLE->console;
        };
    }
    public static Match resolve(String[] input) {
        Route best=null;
        for(Route route:ALL){var words=route.words();if(words.size()>input.length)continue;boolean matches=true;
            for(int i=0;i<words.size();i++)if(!words.get(i).equalsIgnoreCase(input[i])){matches=false;break;}
            if(matches&&(best==null||words.size()>best.words().size()))best=route;
        }
        if(best==null)return null;
        List<String> args=new ArrayList<>(best.prefix());args.addAll(Arrays.asList(input).subList(best.words().size(),input.length));
        return new Match(best,List.copyOf(args));
    }
    public static List<String> children(String[] input,Predicate<Route> visible) {
        if(input.length==0)return List.of();int index=input.length-1;String prefix=input[index].toLowerCase(Locale.ROOT);Set<String> result=new TreeSet<>();
        for(Route route:ALL){var words=route.words();if(!visible.test(route)||words.size()<=index)continue;boolean matches=true;
            for(int i=0;i<index;i++)if(!words.get(i).equalsIgnoreCase(input[i])){matches=false;break;}
            if(matches&&words.get(index).startsWith(prefix))result.add(words.get(index));
        }return List.copyOf(result);
    }
    public static String newPath(String legacy) {return ALL.stream().filter(r->r.target().equals(legacy)).map(Route::path).findFirst().orElse(legacy);}
    /** Update explicit slash-command references, never unrelated prose or external plugin commands. */
    public static String migrateReferences(String text) {
        for(String legacy:ALL.stream().map(Route::target).filter(t->!t.equals("$core")).distinct().sorted(Comparator.comparingInt(String::length).reversed()).toList())
            text=text.replaceAll("/"+java.util.regex.Pattern.quote(legacy)+"(?=$|[^A-Za-z0-9_:-])",java.util.regex.Matcher.quoteReplacement("/spectrecore "+newPath(legacy)));
        return text;
    }
}
