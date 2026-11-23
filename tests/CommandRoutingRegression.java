import dev.hydev.spectrecore.command.CommandRoutes;
import java.util.*;

public final class CommandRoutingRegression {
    static int count;
    static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("--export")) {
            List<String> lines=new ArrayList<>();for(var route:CommandRoutes.ALL)lines.add(String.join("\t",route.path(),route.target(),route.module(),route.access().name(),route.usage(),route.description()));
            java.nio.file.Files.write(java.nio.file.Path.of(args[1]),lines);return;
        }
        Set<String> targets=new HashSet<>();
        for(var route:CommandRoutes.ALL) {
            check(CommandRoutes.allowed(route,p->false,false)==(route.access()==CommandRoutes.Access.PUBLIC),"Unprivileged access "+route.path());
            check(CommandRoutes.allowed(route,p->p.equals("spectrecore.admin"),false)==(route.access()!=CommandRoutes.Access.CONSOLE),"Admins cannot impersonate console "+route.path());
            check(CommandRoutes.allowed(route,p->false,true)==(route.access()==CommandRoutes.Access.PUBLIC||route.access()==CommandRoutes.Access.CONSOLE),"Console identity does not grant player permissions "+route.path());
            if(route.access()==CommandRoutes.Access.ACTIVITIES_ADMIN)check(CommandRoutes.allowed(route,p->p.equals("spectrecore.activities.admin"),false),"Activity permission retained");
            if(route.access()==CommandRoutes.Access.HYDEV_ADMIN)check(CommandRoutes.allowed(route,p->p.equals("hydev.admin"),false),"Hydev admin permission retained");
            String[] input=(route.path()+" Alex session-01").split(" ");var match=CommandRoutes.resolve(input);
            check(match!=null&&match.route().equals(route),"Route: "+route.path());
            List<String> expected=new ArrayList<>(route.prefix());expected.addAll(List.of("Alex","session-01"));
            check(match.arguments().equals(expected),"Preserve all arguments and injected handler prefix: "+route.path());
            check(CommandRoutes.resolve(route.path().toUpperCase(Locale.ROOT).split(" ")).route().equals(route),"Case-insensitive "+route.path());
            check(route.usage().startsWith("/spectrecore "),"Canonical usage "+route.path());
            if(!route.target().equals("$core"))targets.add(route.target());
        }
        check(targets.size()==42,"All private handlers plus Relicario and Mago menus");
        check(CommandRoutes.resolve(new String[]{"resonancia","debug"}).route().target().equals("debugresonancia"),"Longest prefix wins over public resonance query");
        check(CommandRoutes.resolve(new String[]{"tesoros","mineria","3"}).arguments().equals(List.of("mineria","tesoro","3")),"Preview adapter keeps expected argument positions");
        check(CommandRoutes.resolve(new String[]{"portal","registrar","Alex","My session","abc"}).arguments().equals(List.of("Alex","My session","abc")),"Session arguments unchanged, no split/re-dispatch");
        check(CommandRoutes.resolve(new String[]{"slayer","activar","spider","2"}).arguments().equals(List.of("spider","2")),"Slayer category/tier forwarded");
        check(CommandRoutes.resolve(new String[]{"trabajos"}).route().target().equals("trabajo"),"Native Jobs index");
        check(CommandRoutes.resolve(new String[]{"trabajos","mineria"}).arguments().equals(List.of("minero")),"Native Mining contracts");
        check(CommandRoutes.resolve(new String[]{"trabajos","agricultura"}).arguments().equals(List.of("granjero")),"Native Farming contracts");
        check(CommandRoutes.resolve(new String[]{"trabajos","caza"}).arguments().equals(List.of("cazador")),"Native Hunter contracts");
        check(CommandRoutes.resolve(new String[]{"trabajos","estado"}).arguments().equals(List.of("estado")),"Jobs chat status preserved");
        check(CommandRoutes.resolve(new String[]{"misiones"}).route().target().equals("misiones"),"Native Misiones route");
        check(CommandRoutes.resolve(new String[]{"mago"}).route().target().equals("mago"),"Mago nested under /spectrecore");
        check(CommandRoutes.resolve(new String[]{"trabajos","transacciones"}).route().access()==CommandRoutes.Access.ADMIN,"Pending purchases require admin");
        check(CommandRoutes.resolve(new String[]{"trabajos","resolver","token"}).arguments().equals(List.of("resolver","token")),"Administrative reconciliation forwarded");
        check(CommandRoutes.resolve(new String[]{"misiones","monarquia"}).arguments().equals(List.of("revenant")),"Native Revenant deep link");
        check(CommandRoutes.resolve(new String[]{"unknown"})==null,"Unknown path");
        check(CommandRoutes.resolve(new String[]{"portal"})==null,"Incomplete family");
        var publicRoots=CommandRoutes.children(new String[]{""},r->r.access()==CommandRoutes.Access.PUBLIC);
        check(publicRoots.containsAll(List.of("cocina","armaduras","relicario","trabajos","resonancia","slayer","misiones","status","modulos")),"Public discovery");
        check(!publicRoots.contains("cygnus")&&!publicRoots.contains("portal")&&!publicRoots.contains("reload"),"Hide privileged families");
        check(CommandRoutes.children(new String[]{"slayer","a"},r->true).equals(List.of("abandonar","activar")),"Prefix completion includes player cancellation");
        check(CommandRoutes.children(new String[]{"portal",""},r->r.access()!=CommandRoutes.Access.CONSOLE).equals(List.of("demo","estado","limpiar")),"Hide console callbacks from player suggestions");
        for(var route:CommandRoutes.ALL)if(route.target().startsWith("hydevportal")&&(route.target().endsWith("join")||route.target().endsWith("leave")))check(route.access()==CommandRoutes.Access.CONSOLE,"Console-only callback preserved");
        String old="Usa /activarslayer revenant 2; /cocina libro; /hydevportaljoin Alex run42. /mi give y /trabajootro no cambian.";
        String migrated=CommandRoutes.migrateReferences(old);
        check(migrated.contains("/spectrecore slayer activar revenant 2"),"Migrate Slayer reference");
        check(migrated.contains("/spectrecore cocina libro"),"Migrate cooking reference");
        check(migrated.contains("/spectrecore portal registrar Alex run42"),"Migrate console callback reference");
        check(migrated.contains("/mi give")&&migrated.contains("/trabajootro"),"Do not touch external/unrelated commands");
        check(CommandRoutes.migrateReferences(migrated).equals(migrated),"Migration is idempotent");
        check(CommandRoutes.migrateReferences("/SpectreCore cocina libro").equals("/SpectreCore cocina libro"),"Existing root preserved");
        System.out.println("PASS CommandRoutingRegression: "+count+" route/argument/completion/migration assertions; "+targets.size()+" private handlers");
    }
}
