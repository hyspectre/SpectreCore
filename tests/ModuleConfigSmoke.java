import dev.hydev.spectrecore.config.ModuleConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

public final class ModuleConfigSmoke {
    static int checks;
    static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    static void write(Path root,String name,String text)throws Exception{
        Path file=root.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,text);
    }
    static YamlConfiguration yaml(Path path){return YamlConfiguration.loadConfiguration(path.toFile());}
    static void alwaysOn(ModuleConfiguration manager) {
        for(String name:ModuleConfiguration.MODULES)check(manager.active(name),"Always-on module: "+name);
        for(var entry:manager.requested().entrySet()) {
            check(entry.getValue(),"No module or child may be disabled: "+entry.getKey());
            check(!manager.restartPending(entry.getKey()),"No startup-only activation state");
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spectrecore-always-on-migration-");
        String legacy="engine:\n  visual-update-ticks: 6\nskills:\n  botania:\n    cooldown-seconds: 99\n    enabled: false\ncooking:\n  precision:\n    enabled: false\nmodules:\n  skills:\n    enabled: false\n  mining:\n    enabled: false\n  boss-damage:\n    enabled: false\n  activities:\n    enabled: false\n  accesorios:\n    enabled: false\n";
        write(root,"config.yml",legacy);
        String mining="gemas:\n  ids:\n    basica: MI_GEMA\n  carga-maxima: 72\n  activadas: true\ntesoros:\n  cantidad-minima: 4\nprobabilidades:\n  mineria:\n    tier1: 12.5\nvisuales:\n  activados: false\n";
        write(root,"mining.yml",mining);
        write(root,"treasures.yml","cooldown-compartido-segundos: 9\n");
        write(root,"jobs.yml","minero:\n  profesional:\n    dinero:\n      minimo: 700\n      maximo: 1400\ncooldowns:\n  minero:\n    profesional: 5\n");
        write(root,"portal.yml","salida:\n  comando: dungeon-custom salida {player}\n");
        write(root,"cooking/recipes.yml","recetas:\n  mi_plato:\n    nombre: Plato propio\n");
        write(root,"mining-data.yml","DATOS SIN TOCAR [");
        write(root,"cooking/stations.yml","ESTACIONES SIN TOCAR [");

        var manager=new ModuleConfiguration(root.toFile(),ModuleConfigSmoke.class.getClassLoader());
        manager.reload();
        alwaysOn(manager);
        check(manager.read("equipamiento/config.yml").getInt("skills.botania.cooldown-seconds")==99,
                "Keep legacy balance settings");
        check(!manager.read("equipamiento/config.yml").contains("skills.botania.enabled"),
                "Remove legacy skill module disable switch");
        check(manager.read("cocina/config.yml").getBoolean("cooking.precision.enabled")==false,
                "Retain non-module gameplay option");
        check(manager.read("cocina/recetas.yml").contains("recetas.mi_plato"),"Preserve custom recipes");
        check(manager.mining().getInt("gemas.carga-maxima")==72
                && manager.mining().getString("gemas.ids.basica").equals("MI_GEMA"),"Preserve resonance data");
        check(manager.mining().getDouble("probabilidades.mineria.tier1")==12.5,
                "Preserve noninteger probability");
        check(manager.read("tesoros/config.yml").getInt("cooldown-compartido-segundos")==9,
                "Preserve shared cooldown");
        check(manager.read("trabajos/config.yml").getInt("minero.profesional.dinero.maximo")==1400,
                "Preserve salaries");
        check(manager.read("dungeons/portal.yml").getString("salida.comando")
                .equals("dungeon-custom salida {player}"),"Preserve portal settings");
        check(Files.readString(root.resolve("backups/configuration-v1/config.yml")).equals(legacy),
                "Preserve exact previous root before migrating");
        check(Files.readString(root.resolve("mining.yml")).equals(mining),"Never edit flat legacy data");
        check(Files.readString(root.resolve("mining-data.yml")).equals("DATOS SIN TOCAR [")
                && Files.readString(root.resolve("cooking/stations.yml")).equals("ESTACIONES SIN TOCAR ["),
                "Never alter player or station data");
        var index=yaml(root.resolve("config.yml"));
        check(index.getInt("config-version")==2&&!index.contains("modules")
                && !index.contains("skills")&&!index.contains("cooking"),
                "Migration removes all root module switches");
        var runtime=new YamlConfiguration();manager.install(runtime);
        check(runtime.getBoolean("modules.skills.enabled")&&runtime.getBoolean("modules.accesorios.enabled")
                && runtime.getBoolean("modules.boss-damage.enabled")
                && runtime.getBoolean("skills.botania.enabled"),
                "Compatibility aliases are always active");

        // Even newly added false values in a v2 config must be removed, not honored.
        index.set("modules.resonancia.enabled",false);
        index.set("modules.accesorios.enabled",false);
        index.save(root.resolve("config.yml").toFile());
        var miningModule=yaml(root.resolve("modules/tesoros/mineria.yml"));
        miningModule.set("enabled", false);
        miningModule.save(root.resolve("modules/tesoros/mineria.yml").toFile());
        write(root,"modules/equipamiento/config.yml",
                "skills:\n  botania:\n    enabled: false\n    cooldown-seconds: 123\nlibro:\n  enabled: false\n");
        write(root,"modules/accesorios/catalogo.yml",
                "enabled: false\ncaps:\n  health: 12\n");
        write(root,"modules/dungeons/config.yml","jefes:\n  enabled: false\nportal:\n  enabled: false\n");
        write(root,"modules/actividades/config.yml","cygnus:\n  enabled: false\n");
        write(root,"modules/trabajos/config.yml","minero:\n  enabled: false\ncontratos:\n  habilitados: false\n");

        manager.reload();
        alwaysOn(manager);
        check(!yaml(root.resolve("config.yml")).contains("modules"),
                "No root selector survives a reload");
        for(String path:List.of("tesoros/mineria.yml","equipamiento/config.yml",
                "accesorios/catalogo.yml","dungeons/config.yml",
                "actividades/config.yml","trabajos/config.yml")) {
            check(Files.exists(root.resolve("backups/always-on-v1/modules/"+path)),
                    "Old submodule switches backed up: "+path);
        }
        check(!manager.read("tesoros/mineria.yml").contains("enabled")
                && !manager.read("equipamiento/config.yml").contains("skills.botania.enabled")
                && !manager.read("equipamiento/config.yml").contains("libro.enabled")
                && !manager.read("accesorios/catalogo.yml").contains("enabled")
                && !manager.read("dungeons/config.yml").contains("portal.enabled")
                && !manager.read("actividades/config.yml").contains("cygnus.enabled")
                && !manager.read("trabajos/config.yml").contains("minero.enabled")
                && !manager.read("trabajos/config.yml").contains("contratos.habilitados"),
                "Every deprecated child switch removed");
        check(manager.read("equipamiento/config.yml").getInt("skills.botania.cooldown-seconds")==123,
                "Remove disabled flag without destroying configured cooldown");
        check(manager.active("resonancia")&&manager.active("accesorios")
                && manager.mining().getBoolean("gemas.activadas")
                && manager.mining().getBoolean("tesoros.activados"),
                "Even explicitly disabled services remain active in compatibility views");

        var restarted=new ModuleConfiguration(root.toFile(),ModuleConfigSmoke.class.getClassLoader());
        restarted.reload();alwaysOn(restarted);
        write(root,"mining.yml","INVALID OLD YAML [");restarted.reload();
        check(restarted.mining().getDouble("probabilidades.mineria.tier1")==12.5,
                "Ignore obsolete flat files");
        write(root,"modules/equipamiento/config.yml","skills: [");
        boolean failed=false;try{restarted.reload();}catch(Exception expected){failed=true;}
        check(failed&&restarted.read("equipamiento/config.yml")
                .getInt("skills.botania.cooldown-seconds")==123,
                "Invalid reload preserves last valid configuration");
        for(var route:dev.hydev.spectrecore.command.CommandRoutes.ALL)
            check(route.module().isEmpty()||restarted.requested().containsKey(route.module()),
                    "Every routed command refers to a known always-on module: "+route.path());
        System.out.println("PASS ModuleConfigSmoke: "+checks+
                " always-on migration, cleanup, data preservation and routing checks");
    }
}
