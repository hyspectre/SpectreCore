import dev.hydev.spectrecore.config.ModuleConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;

public final class ModuleConfigSmoke {
    static int checks;
    static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    static void write(Path root,String name,String text)throws Exception{Path file=root.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,text);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("spectrecore-config-migration-");
        String legacy="engine:\n  visual-update-ticks: 6\nskills:\n  botania:\n    cooldown-seconds: 99\ncooking:\n  precision:\n    enabled: false\nmodules:\n  skills:\n    enabled: false\n  mining:\n    enabled: true\n  boss-damage:\n    enabled: false\n  activities:\n    enabled: false\n  accesorios:\n    enabled: true\n";
        write(root,"config.yml",legacy);
        String mining="gemas:\n  ids:\n    basica: MI_GEMA\n  carga-maxima: 72\n  activadas: true\ntesoros:\n  cantidad-minima: 4\nprobabilidades:\n  mineria:\n    tier1: 12.5\nvisuales:\n  activados: false\n";
        write(root,"mining.yml",mining);write(root,"treasures.yml","cooldown-compartido-segundos: 9\n");
        write(root,"jobs.yml","minero:\n  profesional:\n    dinero:\n      minimo: 700\n      maximo: 1400\ncooldowns:\n  minero:\n    profesional: 5\n");
        write(root,"portal.yml","salida:\n  comando: dungeon-custom salida {player}\n");
        write(root,"cooking/recipes.yml","recetas:\n  mi_plato:\n    nombre: Plato propio\n");
        write(root,"mining-data.yml","DATOS SIN TOCAR [");write(root,"cooking/stations.yml","ESTACIONES SIN TOCAR [");
        var manager=new ModuleConfiguration(root.toFile(),ModuleConfigSmoke.class.getClassLoader());manager.reload();
        check(!manager.active("equipamiento"),"Honor legacy disabled equipment");
        check(manager.active("accesorios"),"Preserve explicit accessories enabled through legacy migration");
        check(!manager.active("actividades")&&!manager.active("actividades.cofre"),"Parent disables child activities");
        check(!manager.active("dungeons.jefes")&&manager.active("dungeons.portal"),"Independent dungeon switches");
        check(manager.read("equipamiento/config.yml").getInt("skills.botania.cooldown-seconds")==99,"Preserve skill settings");
        check(manager.read("cocina/config.yml").getBoolean("cooking.precision.enabled")==false,"Preserve explicit false setting");
        check(manager.read("cocina/recetas.yml").contains("recetas.mi_plato"),"Preserve custom recipes");
        check(manager.mining().getInt("gemas.carga-maxima")==72&&manager.mining().getString("gemas.ids.basica").equals("MI_GEMA"),"Preserve resonance data");
        check(manager.mining().getDouble("probabilidades.mineria.tier1")==12.5,"Preserve noninteger probability");
        check(manager.read("tesoros/config.yml").getInt("cooldown-compartido-segundos")==9,"Preserve shared cooldown");
        check(manager.read("trabajos/config.yml").getInt("minero.profesional.dinero.maximo")==1400,"Preserve salaries");
        check(manager.read("dungeons/portal.yml").getString("salida.comando").equals("dungeon-custom salida {player}"),"Preserve custom integration command");
        check(Files.readString(root.resolve("backups/configuration-v1/config.yml")).equals(legacy),"Backup exact root original");
        check(Files.readString(root.resolve("mining.yml")).equals(mining),"Do not edit flat legacy originals");
        check(Files.readString(root.resolve("mining-data.yml")).equals("DATOS SIN TOCAR [")&&Files.readString(root.resolve("cooking/stations.yml")).equals("ESTACIONES SIN TOCAR ["),"Never read or rewrite player/station data");
        var index=YamlConfiguration.loadConfiguration(root.resolve("config.yml").toFile());
        check(index.getInt("config-version")==2&&!index.contains("skills")&&!index.contains("cooking"),"Root becomes clean module index");
        check(index.getBoolean("modules.accesorios.enabled"),"Migrated config persists explicit Relicario switch");
        index.set("modules.resonancia.enabled",false);index.save(root.resolve("config.yml").toFile());manager.reload();
        check(manager.active("resonancia")&&manager.restartPending("resonancia"),"Enabled changes require restart");
        var restarted=new ModuleConfiguration(root.toFile(),ModuleConfigSmoke.class.getClassLoader());restarted.reload();
        check(!restarted.active("resonancia")&&restarted.active("tesoros.mineria"),"Resonance independent of mining treasure");
        check(!restarted.mining().getBoolean("gemas.activadas")&&restarted.mining().getBoolean("tesoros.activados"),"Runtime routing honors independent toggles");
        write(root,"mining.yml","INVALID OLD YAML [");restarted.reload();
        check(restarted.mining().getDouble("probabilidades.mineria.tier1")==12.5,"Ignore obsolete flat files after migration");
        var equipment=restarted.read("equipamiento/config.yml");equipment.set("skills.botania.cooldown-seconds",123);equipment.save(restarted.file("equipamiento/config.yml"));restarted.reload();
        check(restarted.read("equipamiento/config.yml").getInt("skills.botania.cooldown-seconds")==123,"Never overwrite existing modular edits");
        write(root,"modules/equipamiento/config.yml","skills: [");boolean failed=false;try{restarted.reload();}catch(Exception expected){failed=true;}
        check(failed&&restarted.read("equipamiento/config.yml").getInt("skills.botania.cooldown-seconds")==123,"Invalid reload keeps last valid cache");
        Path disabled=Files.createTempDirectory("spectrecore-disabled-");var allOff=new YamlConfiguration();allOff.set("config-version",2);
        for(String module:ModuleConfiguration.MODULES)allOff.set("modules."+module+".enabled",false);allOff.save(disabled.resolve("config.yml").toFile());
        var off=new ModuleConfiguration(disabled.toFile(),ModuleConfigSmoke.class.getClassLoader());off.reload();check(off.requested().values().stream().noneMatch(Boolean::booleanValue),"All-off includes every child");
        var runtime=new YamlConfiguration();off.install(runtime);check(!runtime.getBoolean("modules.mining.enabled")&&!runtime.getBoolean("modules.skills.enabled"),"Disabled modules stay disabled in compatibility view");
        check(!Files.exists(disabled.resolve("mining.yml"))&&!Files.exists(disabled.resolve("jobs.yml")),"Fresh setup never recreates obsolete config files");
        for(var route:dev.hydev.spectrecore.command.CommandRoutes.ALL)check(route.module().isEmpty()||off.requested().containsKey(route.module()),"Every command route uses a real module switch: "+route.path());
        System.out.println("PASS ModuleConfigSmoke: "+checks+" migration/routing assertions; fixtures: "+root);
    }
}
