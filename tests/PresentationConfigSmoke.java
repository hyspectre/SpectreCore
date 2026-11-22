import dev.hydev.spectrecore.config.*;
import dev.hydev.spectrecore.migration.MigrationRules;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

/** Real loader/readback tests; Bukkit registries still require in-server validation. */
public final class PresentationConfigSmoke {
    private static int assertions;
    static void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    static Path root;static ModuleConfiguration manager;
    static YamlConfiguration read(String path){return YamlConfiguration.loadConfiguration(root.resolve("modules/"+path).toFile());}
    static void save(String path,YamlConfiguration yaml)throws Exception{yaml.save(root.resolve("modules/"+path).toFile());}
    static void reject(String path,String key,Object value)throws Exception{
        Path file=root.resolve("modules/"+path);String before=Files.readString(file);
        var yaml=read(path);yaml.set(key,value);save(path,yaml);
        boolean rejected=false;try{manager.reload();}catch(java.io.IOException expected){rejected=true;}
        check(rejected,"Reject invalid "+path+" "+key);Files.writeString(file,before);
    }
    public static void main(String[] args)throws Exception{
        root=Files.createTempDirectory("spectrecore-presentation-");
        manager=new ModuleConfiguration(root.toFile(),PresentationConfigSmoke.class.getClassLoader());manager.reload();Settings.install(manager);
        check(Files.isRegularFile(root.resolve("modules/nucleo/mensajes.yml")),"Core messages installed");
        for(int tier=1;tier<=3;tier++)for(int roll=1;roll<=100;roll++)check(Gameplay.gemQuality(tier,roll-.5).equals(MigrationRules.quality(tier,roll)),"Default quality distribution unchanged");
        check(Gameplay.tier("mineria","DIAMOND_ORE")==3,"Configured mining classification");
        check(Gameplay.tier("trabajos-mineria","NETHER_QUARTZ_ORE")==3&&Gameplay.tier("mineria","NETHER_QUARTZ_ORE")==0,"Preserve original jobs distinction");
        var messages=read("trabajos/mensajes.yml");messages.set("JobsModule.mensaje-001","&aMensaje propio {jugador}");save("trabajos/mensajes.yml",messages);manager.reload();
        check(Settings.text("trabajos","JobsModule.mensaje-001","fallback","jugador","Alex").equals("<reset><green>Mensaje propio Alex"),"Legacy custom message migrated to MiniMessage");
        check(Files.isRegularFile(root.resolve("backups/minimessage-v1/modules/trabajos/mensajes.yml")),
                "Legacy message backup created before migration");
        check(Settings.format("{uno}/{dos}","uno","{dos}","dos","$x\\name").equals("{dos}/$x\\name"),"One-pass literal placeholder replacement");
        // MiniMessage migration: preserve legacy colors and never parse user-controlled markup.
        var plainText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        var decorated = dev.hydev.spectrecore.text.TextService.component("§6§lMonarquía §cRenacida");
        check(plainText.serialize(decorated).equals("Monarquía Renacida"), "Legacy colors render via MiniMessage");
        var untrusted = dev.hydev.spectrecore.text.TextService.render(
                "<gold>Jugador: <white>{nombre}", java.util.Map.of("nombre","<red>INYECCIÓN</red>"));
        check(plainText.serialize(untrusted).equals("Jugador: <red>INYECCIÓN</red>"),
                "Untrusted name remains literal, not MiniMessage markup");
        var trusted = dev.hydev.spectrecore.text.TextService.render("<gray>Estado: {estado}",
                java.util.Map.of("estado", dev.hydev.spectrecore.text.TextService.component("<green>ACTIVO")));
        check(plainText.serialize(trusted).equals("Estado: ACTIVO"),
                "Trusted nested component retains Adventure styling");

        var effects=read("trabajos/ajustes.yml");String volume=effects.getKeys(true).stream().filter(k->k.contains("playsound")&&k.endsWith("arg2")).findFirst().orElseThrow();
        effects.set(volume,1.35);save("trabajos/ajustes.yml",effects);manager.reload();
        check(Math.abs(Settings.f("trabajos",volume,0)-1.35)<.00001,"Fractional volume edit applied despite integral original API literal");
        reject("trabajos/ajustes.yml",volume,-1);
        check(Math.abs(Settings.f("trabajos",volume,0)-1.35)<.00001,"Rejected reload retained live snapshot");
        reject("trabajos/mensajes.yml","JobsModule.mensaje-001",42);
        var mining=read("nucleo/ajustes.yml");String dust=mining.getKeys(true).stream().filter(k->k.contains(".particle-")&&"DUST".equals(mining.get(k))).findFirst().orElseThrow();
        reject("nucleo/ajustes.yml",dust,"FLAME");
        String sound=effects.getKeys(true).stream().filter(k->k.contains(".sound-")).findFirst().orElseThrow();reject("trabajos/ajustes.yml",sound,"INVALID_SOUND");
        reject("nucleo/ajustes.yml","toasts.presets.furia.volumen", 9.5);
        reject("nucleo/ajustes.yml","toasts.presets.furia.tono", 0.1);
        reject("nucleo/ajustes.yml","toasts.presets.furia.intervalo-segundos", -1);
        reject("equipamiento/config.yml","furia-maxima",0);
        reject("equipamiento/config.yml","pasivos.piezas",5);
        reject("dungeons/jefes.yml","bloqueos.HYDEV_MINO_LOCK35",.9);
        reject("dungeons/portal.yml","zona-extraccion.altura-inferior",10);
        reject("resonancia/config.yml","reglas.calidad.tier3.superior",11);
        reject("resonancia/config.yml","reglas.carga-por-tier.tier1",1.5);
        reject("tesoros/config.yml","clasificacion.combate.tier2",List.of("ZOMBIE"));
        reject("cocina/config.yml","herramientas.revolver","BOWL");
        reject("tesoros/combate.yml","cantidad",-1);
        var resonance=read("resonancia/config.yml");resonance.set("reglas.calidad.tier3.basica",0);resonance.set("reglas.calidad.tier3.intermedia",0);resonance.set("reglas.calidad.tier3.superior",100);resonance.set("reglas.carga-por-tier.tier3",9);save("resonancia/config.yml",resonance);manager.reload();
        for(int roll=0;roll<100;roll++)check(Gameplay.gemQuality(3,roll+.5).equals("superior"),"Custom gem quality actually used");
        check(Settings.module("resonancia/config.yml").getInt("reglas.carga-por-tier.tier3")==9,"Custom resonance charge preserved");
        var tools=read("cocina/config.yml");tools.set("herramientas.revolver","STICK");save("cocina/config.yml",tools);manager.reload();
        check(Gameplay.cookingTool("STICK",true,false)==dev.hydev.spectrecore.cooking.CookingToolActions.Use.STIR,"Configured cooking controls actually used");
        // Old installations may still say CONSUMABLE: the runtime accepts only COCINA.
        var oldCooking = read("cocina/config.yml");
        oldCooking.set("cooking.rewards.mmoitems-type", "CONSUMABLE");
        save("cocina/config.yml", oldCooking);
        manager.reload();
        check("COCINA".equals(read("cocina/config.yml")
                .getString("cooking.rewards.mmoitems-type")),
                "Legacy cooking output type is migrated to COCINA");
        check(Files.isRegularFile(root.resolve(
                "backups/cocina-tipo-v1/modules/cocina/config.yml")),
                "Legacy output type backed up before migration");
        check("CONSUMABLE".equals(YamlConfiguration.loadConfiguration(
                root.resolve("backups/cocina-tipo-v1/modules/cocina/config.yml").toFile())
                .getString("cooking.rewards.mmoitems-type")),
                "Backup retains previous CONSUMABLE type");

        var old=read("trabajos/mensajes.yml");old.set("JobsModule.mensaje-001","");old.set("JobsModule.mensaje-002",null);old.set("personalizado","Conservar");save("trabajos/mensajes.yml",old);manager.reload();
        check(Settings.text("trabajos","JobsModule.mensaje-001","fallback").isEmpty(),"Empty text preserved");
        check(read("trabajos/mensajes.yml").contains("JobsModule.mensaje-002")&&read("trabajos/mensajes.yml").getString("personalizado").equals("Conservar"),"Missing defaults added without deleting unknown keys");
        var instructions=read("nucleo/mensajes.yml");instructions.set("personalizado","Mi guía: /cocina libro y /activarslayer spider 2; /mi no cambia.");save("nucleo/mensajes.yml",instructions);manager.reload();
        check(read("nucleo/mensajes.yml").getString("personalizado").equals("Mi guía: /spectrecore cocina libro y /spectrecore slayer activar spider 2; /mi no cambia."),"Custom slash references migrated");
        check(Files.isRegularFile(root.resolve("backups/commands-v1/modules/nucleo/mensajes.yml")),"Command reference migration backed up");
        var commandTexts=read("nucleo/comandos.yml");commandTexts.set("rutas",null);commandTexts.set("cocina.descripcion","Mi cocina personalizada");commandTexts.set("cocina.uso","/cocina libro");save("nucleo/comandos.yml",commandTexts);manager.reload();
        check(read("nucleo/comandos.yml").getString("rutas.cocina.descripcion").equals("Mi cocina personalizada"),"Custom old command descriptions retained in canonical routes");
        check(read("nucleo/comandos.yml").getString("rutas.cocina.uso").equals("/spectrecore cocina libro"),"Custom old usages migrated and retained");
        Settings.clear();System.out.println("PASS PresentationConfigSmoke: "+assertions+" live configuration/reload assertions");
    }
}
