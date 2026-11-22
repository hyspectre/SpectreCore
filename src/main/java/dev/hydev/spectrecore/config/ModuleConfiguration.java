package dev.hydev.spectrecore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import dev.hydev.spectrecore.SpectreCorePlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All gameplay modules are always installed. Legacy enabled switches are stripped on migration. */
public final class ModuleConfiguration {
    public static final List<String> MODULES=List.of("equipamiento","cocina","tesoros","resonancia","trabajos","slayer","dungeons","actividades","accesorios");
    private static final List<String> FILES=List.of("accesorios/config.yml","accesorios/catalogo.yml","equipamiento/config.yml","cocina/config.yml","cocina/recetas.yml","tesoros/config.yml","tesoros/mineria.yml","tesoros/combate.yml","tesoros/agricultura.yml","resonancia/config.yml","trabajos/config.yml","slayer/config.yml","dungeons/config.yml","dungeons/jefes.yml","dungeons/portal.yml","actividades/config.yml","equipamiento/mensajes.yml","equipamiento/ajustes.yml","cocina/mensajes.yml","cocina/ajustes.yml","tesoros/mensajes.yml","tesoros/ajustes.yml","resonancia/mensajes.yml","resonancia/ajustes.yml","trabajos/mensajes.yml","trabajos/ajustes.yml","slayer/mensajes.yml","slayer/ajustes.yml","dungeons/mensajes.yml","dungeons/ajustes.yml","actividades/mensajes.yml","actividades/ajustes.yml","nucleo/mensajes.yml","nucleo/ajustes.yml","nucleo/comandos.yml");
    private static final List<String> LEGACY=List.of("config.yml","mining.yml","treasures.yml","combat-treasures.yml","agriculture-treasures.yml","jobs.yml","slayer.yml","portal.yml","activities.yml","cooking/recipes.yml");
    private final File folder; private final ClassLoader resources;
    private Map<String,YamlConfiguration> files=Map.of(); private Map<String,Boolean> requested=Map.of();
    public ModuleConfiguration(File folder,ClassLoader resources){this.folder=folder;this.resources=resources;}
    public static ModuleConfiguration of(JavaPlugin plugin){return ((SpectreCorePlugin)plugin).moduleConfigs();}
    public static YamlConfiguration module(JavaPlugin plugin,String path){return of(plugin).read(path);}
    public File file(String path){if(!FILES.contains(path))throw new IllegalArgumentException(path);return new File(folder,"modules/"+path);}
    public YamlConfiguration read(String path){YamlConfiguration value=files.get(path);if(value==null)throw new IllegalArgumentException("Módulo desconocido: "+path);return value;}
    private YamlConfiguration strict(File file)throws Exception{var yaml=new YamlConfiguration();if(file.isFile())yaml.load(file);return yaml;}
    private YamlConfiguration resource(String path)throws Exception{
        try(InputStream in=resources.getResourceAsStream(path)){if(in==null)throw new IOException("Falta recurso "+path);var yaml=new YamlConfiguration();yaml.load(new InputStreamReader(in,StandardCharsets.UTF_8));return yaml;}
    }
    private static void merge(YamlConfiguration into,ConfigurationSection from,String prefix){
        if(from==null)return;for(String key:from.getKeys(true))if(!from.isConfigurationSection(key))into.set(prefix+key,from.get(key));
    }
    private static void branch(YamlConfiguration into,String key,YamlConfiguration from){var section=from.getConfigurationSection(key);if(section!=null)merge(into,section,key+".");}
    private static boolean importCommandMetadata(String path,YamlConfiguration yaml,YamlConfiguration defaults) {
        if(!path.equals("nucleo/comandos.yml"))return false;boolean changed=false;
        for(var route:dev.hydev.spectrecore.command.CommandRoutes.ALL)if(!route.target().equals("$core"))for(String field:List.of("descripcion","uso")) {
            String old=route.target()+"."+field,next="rutas."+route.key()+"."+field;
            if(!yaml.contains(next)&&yaml.get(old) instanceof String value) {
                String migrated=dev.hydev.spectrecore.command.CommandRoutes.migrateReferences(value);
                if(!migrated.equals(defaults.getString(old,""))){yaml.set(next,migrated);changed=true;}
            }
        }return changed;
    }
    private void save(File file,YamlConfiguration yaml)throws Exception{
        dev.hydev.spectrecore.storage.AtomicFileStore.writeUtf8(file.toPath(), yaml.saveToString());
    }
    /** No persisted flag can disable a module. Gameplay and integration options remain editable. */
    private static List<String> retiredSwitches(String path) {
        return switch(path) {
            case "accesorios/config.yml","accesorios/catalogo.yml",
                 "tesoros/mineria.yml","tesoros/combate.yml","tesoros/agricultura.yml" -> List.of("enabled");
            case "equipamiento/config.yml" -> List.of(
                "skills.botania.enabled","skills.necrotica.enabled",
                "skills.asterion.enabled","skills.ultimo-juramento.enabled",
                "skills.sangre-primer-dragon.enabled","libro.enabled");
            case "dungeons/config.yml" -> List.of("jefes.enabled","portal.enabled");
            case "trabajos/config.yml" -> List.of("minero.enabled","granjero.enabled","cazador.enabled");
            case "actividades/config.yml" -> List.of("cygnus.enabled","entrada.enabled",
                "cofre.enabled","polvos.enabled","desbloqueos.enabled","telefono.enabled");
            default -> List.of();
        };
    }
    private static boolean removeModuleSwitches(String path,YamlConfiguration yaml) {
        boolean changed=false;
        for (String key:retiredSwitches(path)) if(yaml.contains(key)) {
            yaml.set(key,null); changed=true;
        }
        return changed;
    }
    private void backup(File source,String path)throws IOException {
        if (!source.isFile())return;
        File target=new File(folder,"backups/always-on-v1/"+path);
        if(!target.exists()) {
            Files.createDirectories(target.toPath().getParent());
            Files.copy(source.toPath(),target.toPath());
        }
    }
    public void reload()throws Exception{
        File rootFile=new File(folder,"config.yml");YamlConfiguration old=strict(rootFile);
        boolean legacy=old.getInt("config-version",0)<2||old.contains("skills")||old.contains("engine")||old.contains("cooking");
        boolean migrating=legacy||!file("equipamiento/config.yml").isFile();
        Map<String,YamlConfiguration> originals=new HashMap<>();for(String name:LEGACY)originals.put(name,migrating?strict(new File(folder,name)):new YamlConfiguration());
        var skript=migrating?strict(new File(folder.getParentFile(),"Skript/tesoros-config.yaml")):new YamlConfiguration();
        var jobsLegacy=migrating?strict(new File(folder.getParentFile(),"Skript/trabajos-config.yaml")):new YamlConfiguration();
        var mining=originals.get("mining.yml");
        if(!new File(folder,"mining.yml").isFile()){branch(mining,"gemas",skript);var s=skript.getConfigurationSection("probabilidades.mineria");if(s!=null)merge(mining,s,"probabilidades.mineria.");}
        if(migrating){for(String name:LEGACY){File source=new File(folder,name),backup=new File(folder,"backups/configuration-v1/"+name);if(source.isFile()&&!backup.exists()){Files.createDirectories(backup.toPath().getParent());Files.copy(source.toPath(),backup.toPath());}}}
        Map<String,YamlConfiguration> next=new LinkedHashMap<>();
        for(String path:FILES){File destination=file(path);YamlConfiguration yaml;
            if(destination.isFile()){
                if (path.endsWith("/mensajes.yml") || path.equals("nucleo/comandos.yml"))
                    PresentationMigrations.migrate(folder, destination, "modules/" + path);
                yaml = strict(destination);
                var defaults = resource("modules/" + path);
                boolean changed = importCommandMetadata(path, yaml, defaults);
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !yaml.contains(key)) {
                        yaml.set(key, defaults.get(key));
                        changed = true;
                    }
                }
                // The type is no longer configurable: remove the old selector
                // rather than leaving an option that appears to work.
                if (path.equals("cocina/config.yml")
                        && yaml.contains("cooking.rewards.mmoitems-type")) {
                    File backup = new File(folder, "backups/cocina-tipo-v1/modules/cocina/config.yml");
                    if (!backup.exists()) {
                        Files.createDirectories(backup.toPath().getParent());
                        Files.copy(destination.toPath(), backup.toPath());
                    }
                    yaml.set("cooking.rewards.mmoitems-type", null);
                    var rewards = yaml.getConfigurationSection("cooking.rewards");
                    if (rewards != null && rewards.getKeys(false).isEmpty())
                        yaml.set("cooking.rewards", null);
                    changed = true;
                }
                if (changed) {
                    File backup = new File(folder, "backups/configuration-v2/modules/" + path);
                    if (!backup.exists()) {
                        Files.createDirectories(backup.toPath().getParent());
                        Files.copy(destination.toPath(), backup.toPath());
                    }
                    save(destination, yaml);
                }
            }
            else{
                yaml=resource("modules/"+path);
                switch(path){
                    case "equipamiento/config.yml"->{branch(yaml,"engine",old);branch(yaml,"skills",old);}
                    case "cocina/config.yml"->branch(yaml,"cooking",old);
                    case "cocina/recetas.yml"->{var recipes=originals.get("cooking/recipes.yml");if(!recipes.contains("recetas"))recipes=strict(new File(folder.getParentFile(),"SpectreCooking/recipes.yml"));merge(yaml,recipes,"");}
                    case "resonancia/config.yml"->{branch(yaml,"gemas",mining);branch(yaml,"visuales",mining);yaml.set("gemas.activadas",null);}
                    case "tesoros/mineria.yml"->{branch(yaml,"tesoros",mining);branch(yaml,"probabilidades",mining);branch(yaml,"visuales",mining);yaml.set("tesoros.activados",null);}
                    case "tesoros/config.yml"->{var shared=originals.get("treasures.yml");if(shared.contains("cooldown-compartido-segundos"))yaml.set("cooldown-compartido-segundos",shared.get("cooldown-compartido-segundos"));else if(mining.contains("tesoros.cooldown-segundos"))yaml.set("cooldown-compartido-segundos",mining.get("tesoros.cooldown-segundos"));}
                    case "tesoros/combate.yml","tesoros/agricultura.yml"->{String name=path.contains("combate")?"combat":"agriculture",type=name.equals("combat")?"combate":"agricultura";File source=new File(folder,name+"-treasures.yml");merge(yaml,originals.get(name+"-treasures.yml"),"");if(!source.isFile()){var s=skript.getConfigurationSection("probabilidades."+type);if(s!=null)merge(yaml,s,"probabilidades."+type+".");}}
                    case "trabajos/config.yml"->merge(yaml,new File(folder,"jobs.yml").isFile()?originals.get("jobs.yml"):jobsLegacy,"");
                    case "slayer/config.yml"->merge(yaml,originals.get("slayer.yml"),"");
                    case "dungeons/config.yml"->{ /* The dungeon systems are always present. */ }
                    case "dungeons/portal.yml"->merge(yaml,originals.get("portal.yml"),"");
                    case "actividades/config.yml"->{merge(yaml,originals.get("activities.yml"),"");yaml.set("entrada.proteccion",null);}
                    default->{}
                }
                if (path.equals("cocina/config.yml")) {
                    yaml.set("cooking.rewards.mmoitems-type", null);
                    var rewards = yaml.getConfigurationSection("cooking.rewards");
                    if (rewards != null && rewards.getKeys(false).isEmpty())
                        yaml.set("cooking.rewards", null);
                }
                removeModuleSwitches(path,yaml);
                save(destination,yaml);
            }
            // Existing installed configs may contain retired false flags. Preserve a byte
            // for byte backup, then strip them so administrators never see inert switches.
            if (removeModuleSwitches(path,yaml)) {
                backup(destination,"modules/"+path);
                save(destination,yaml);
            }
            // Rewrite only explicit references to this plugin's former slash commands.
            // Existing configuration files are backed up before changing their text.
            if(path.endsWith("/mensajes.yml")||path.equals("nucleo/comandos.yml")) {
                boolean changed=false;
                for(String key:yaml.getKeys(true))if(yaml.get(key) instanceof String value) {
                    String updated=dev.hydev.spectrecore.command.CommandRoutes.migrateReferences(value);
                    if(!updated.equals(value)){yaml.set(key,updated);changed=true;}
                }
                if(changed){File backup=new File(folder,"backups/commands-v1/modules/"+path);if(!backup.exists()){Files.createDirectories(backup.toPath().getParent());Files.copy(destination.toPath(),backup.toPath());}save(destination,yaml);}
            }
            // One-time in-place migration for installed message catalogs. Preserve overrides
            // and write a byte-for-byte backup before converting legacy colours to MiniMessage.
            // Machine-readable IDs, recipes and command metadata are intentionally excluded.
            if (path.endsWith("/mensajes.yml")) {
                boolean converted = false;
                for (String key : yaml.getKeys(true)) {
                    if (!(yaml.get(key) instanceof String text)) continue;
                    String mini = dev.hydev.spectrecore.text.TextService.legacyToMini(text);
                    if (!mini.equals(text)) { yaml.set(key, mini); converted = true; }
                }
                if (converted) {
                    File backup = new File(folder, "backups/minimessage-v1/modules/" + path);
                    if (!backup.isFile()) {
                        Files.createDirectories(backup.toPath().getParent());
                        Files.copy(destination.toPath(), backup.toPath());
                    }
                    save(destination, yaml);
                }
            }
            next.put(path,yaml);
        }
        var index=legacy?resource("config.yml"):old;
        if(legacy) {
            for(String key:old.getKeys(false))
                if(!Set.of("config-version","modules","skills","engine","cooking").contains(key)) {
                    if(old.isConfigurationSection(key))merge(index,old.getConfigurationSection(key),key+".");
                    else index.set(key,old.get(key));
                }
        }
        // An older config may have modules.*.enabled=false, including a previously
        // broken relicario migration. The entire selector tree is obsolete.
        boolean rootChanged=legacy || index.contains("modules");
        if(rootChanged) {
            backup(rootFile,"config.yml");
            index.set("modules",null);
            index.set("config-version",2);
            save(rootFile,index);
        }
        Map<String,Boolean> flags=new LinkedHashMap<>();
        for(String name:MODULES)flags.put(name,true);
        for(String name:List.of("mineria","combate","agricultura"))flags.put("tesoros."+name,true);
        for(String name:List.of("jefes","portal"))flags.put("dungeons."+name,true);
        flags.put("equipamiento.libro",true);
        for(String name:List.of("botania","necrotica","asterion","ultimo-juramento","sangre-primer-dragon"))flags.put("equipamiento."+name,true);
        for(String name:List.of("minero","granjero","cazador"))flags.put("trabajos."+name,true);
        for(String name:List.of("cygnus","cofre","polvos","desbloqueos","telefono","entrada"))flags.put("actividades."+name,true);
        Settings.validate(next,resources);
        Gameplay.validate(next);
        files=Collections.unmodifiableMap(next);requested=Collections.unmodifiableMap(flags);
    }
    public boolean active(String name){return requested.getOrDefault(name,false);}
    public Map<String,Boolean> requested(){return requested;}
    public boolean restartPending(String name){return false;}
    public void install(FileConfiguration runtime){
        runtime.set("engine",null);runtime.set("skills",null);runtime.set("cooking",null);runtime.set("modules",null);
        copy(runtime,read("equipamiento/config.yml"),"engine");copy(runtime,read("equipamiento/config.yml"),"skills");copy(runtime,read("cocina/config.yml"),"cooking");
        for(String skill:List.of("botania","necrotica","asterion","ultimo-juramento","sangre-primer-dragon"))runtime.set("skills."+skill+".enabled",active("equipamiento."+skill));
        Map<String,Boolean> aliases=new LinkedHashMap<>();aliases.put("skills",active("equipamiento"));aliases.put("cooking",active("cocina"));aliases.put("mining",active("resonancia")||active("tesoros.mineria"));aliases.put("combat-treasures",active("tesoros.combate"));aliases.put("agriculture-treasures",active("tesoros.agricultura"));aliases.put("boss-damage",active("dungeons.jefes"));aliases.put("exit-portal",active("dungeons.portal"));aliases.put("jobs",active("trabajos"));aliases.put("slayer",active("slayer"));aliases.put("activities",active("actividades"));
        aliases.forEach((key,value)->runtime.set("modules."+key+".enabled",true));
        for(String name:MODULES)runtime.set("modules."+name+".enabled",true);
    }
    private static void copy(FileConfiguration into,YamlConfiguration from,String branch){var s=from.getConfigurationSection(branch);if(s!=null)for(String key:s.getKeys(true))if(!s.isConfigurationSection(key))into.set(branch+"."+key,s.get(key));}
    public YamlConfiguration mining(){
        var merged=new YamlConfiguration();merge(merged,read("tesoros/mineria.yml"),"");branch(merged,"gemas",read("resonancia/config.yml"));
        merged.set("gemas.activadas",active("resonancia"));
        merged.set("tesoros.activados",active("tesoros.mineria"));
        var gem=read("resonancia/config.yml").getConfigurationSection("visuales");if(gem!=null)merge(merged,gem,"visuales-resonancia.");
        var ores=read("tesoros/mineria.yml").getConfigurationSection("visuales");if(ores!=null)merge(merged,ores,"visuales-tesoros.");
        merged.set("visuales.activados",merged.getBoolean("visuales-resonancia.activados",true)||merged.getBoolean("visuales-tesoros.activados",true));
        return merged;
    }
}
