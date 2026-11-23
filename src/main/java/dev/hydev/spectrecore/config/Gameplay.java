package dev.hydev.spectrecore.config;

import java.util.*;
import org.bukkit.Material;
import dev.hydev.spectrecore.cooking.CookingToolActions;

/** Gameplay settings shared by listeners; persistent identifiers and wire formats stay stable. */
public final class Gameplay {
    private Gameplay() { }
    public static int tier(String domain,String name) {
        var config=Settings.module("tesoros/config.yml");
        for(int tier=1;tier<=3;tier++)if(config.getStringList("clasificacion."+domain+".tier"+tier).contains(name))return tier;
        return 0;
    }
    public static String gemQuality(int tier,double roll) {
        var config=Settings.module("resonancia/config.yml");
        double cursor=0;
        for(String quality:List.of("basica","intermedia","superior")) {
            cursor+=config.getDouble("reglas.calidad.tier"+tier+"."+quality);
            if(roll<cursor)return quality;
        }
        return "superior";
    }
    public static CookingToolActions.Use cookingTool(String material,boolean registered,boolean burned) {
        var config=Settings.module("cocina/config.yml");String root="herramientas.";
        if(!registered)return material.equals(config.getString(root+"crear","FLINT_AND_STEEL"))?CookingToolActions.Use.CREATE:CookingToolActions.Use.NONE;
        if(material.equals(config.getString(root+"revolver","WOODEN_SHOVEL")))return CookingToolActions.Use.STIR;
        if(material.equals(config.getString(root+"servir","BOWL")))return CookingToolActions.Use.SERVE;
        if(config.getStringList(root+"limpiar").contains(material))return burned?CookingToolActions.Use.CLEAN:CookingToolActions.Use.REMOVE;
        return CookingToolActions.Use.NONE;
    }
    public static void validate(Map<String,org.bukkit.configuration.file.YamlConfiguration> values)throws java.io.IOException {
        var commands=values.get("nucleo/comandos.yml");
        for(String key:commands.getKeys(true))if(!commands.isConfigurationSection(key)&&!(commands.get(key) instanceof String))throw new java.io.IOException("nucleo/comandos.yml: debe ser texto: "+key);
        var equipment=values.get("equipamiento/config.yml");
        bounded(values.get("tesoros/combate.yml"),"cantidad",1,64,true);
        bounded(values.get("tesoros/agricultura.yml"),"cantidad",1,64,true);
        bounded(values.get("slayer/config.yml"),"avisos.cada-bajas",1,1000000,true);
        bounded(values.get("slayer/config.yml"),"invocacion.reintento-milisegundos",0,3600000,true);
        bounded(values.get("slayer/config.yml"),"invocacion.nivel-mob",1,10000,true);
        var jobs = values.get("trabajos/config.yml");
        bounded(jobs,"avisos.intervalo-cooldown-milisegundos",0,3600000,true);
        for (String level : List.of("novato","experimentado","profesional"))
            bounded(jobs,"contratos.precios."+level,0,1_000_000_000,true);
        var ores = values.get("tesoros/mineria.yml");
        if (!"MATERIAL".equals(ores.getString("mitrilo.tipo"))
                || !"MITRILO".equals(ores.getString("mitrilo.id")))
            throw new java.io.IOException("tesoros/mineria.yml: el Mitrilo debe conservar el ID MATERIAL:MITRILO");
        bounded(ores, "mitrilo.cantidad-minima", 1, 64, true);
        bounded(ores, "mitrilo.cantidad-maxima", 1, 64, true);
        if (ores.getInt("mitrilo.cantidad-maxima") < ores.getInt("mitrilo.cantidad-minima"))
            throw new java.io.IOException("tesoros/mineria.yml: cantidad Mitrilo invertida");
        bounded(ores, "mitrilo.rango-minimo", 2, 3, true);
        for (int tier = 2; tier <= 3; tier++)
            bounded(ores, "mitrilo.probabilidades.tier" + tier, 0, 100, false);
        if (!"TOOL".equals(ores.getString("mitrilo.herramientas.tipo")))
            throw new java.io.IOException("tesoros/mineria.yml: herramientas Mitrilo deben usar TOOL");
        for (String tool : List.of("PICO_MITRILO", "PICO_MITRILO_TEMPLADO"))
            bounded(ores, "mitrilo.herramientas.bonificaciones." + tool, 1, 3, false);
        if (!"MATERIAL".equals(jobs.getString("encargos.mitrilo.tipo"))
                || !"MITRILO".equals(jobs.getString("encargos.mitrilo.id")))
            throw new java.io.IOException("trabajos/config.yml: el pedido debe usar MATERIAL:MITRILO");
        bounded(jobs, "encargos.mitrilo.cantidad", 1, 64, true);
        bounded(jobs, "encargos.mitrilo.recompensa", 1, 1_000_000_000, true);
        bounded(jobs, "encargos.mitrilo.rango-minimo", 2, 3, true);
        for (String job : List.of("minero","granjero","cazador")) {
            for (String level : List.of("novato","experimentado","profesional")) {
                bounded(jobs,"cooldowns."+job+"."+level,0,3600,false);
                bounded(jobs,job+"."+level+".dinero.minimo",0,1_000_000_000,true);
                bounded(jobs,job+"."+level+".dinero.maximo",0,1_000_000_000,true);
                if (jobs.getLong(job+"."+level+".dinero.maximo") <
                        jobs.getLong(job+"."+level+".dinero.minimo"))
                    throw new java.io.IOException("trabajos/config.yml: maximo menor que minimo en "+job+"."+level);
            }
        }
        bounded(equipment,"pasivos.piezas",1,4,true);
        bounded(equipment,"pasivos.laberinto-vida",0,10000,false);
        bounded(equipment,"pasivos.dragon-vida",0,10000,false);
        bounded(equipment,"pasivos.voto-velocidad",0,5,false);
        bounded(equipment,"pasivos.dragon-velocidad",0,5,false);
        bounded(equipment,"furia-maxima",1,1000000,true);
        for(String skill:List.of("botania","necrotica","asterion","ultimo-juramento","sangre-primer-dragon"))bounded(equipment,"libro.colores."+skill,0,16777215,true);
        var bosses=values.get("dungeons/jefes.yml");
        bounded(bosses,"bloqueos.HYDEV_MINO_LOCK70",0.001,1,false);
        bounded(bosses,"bloqueos.HYDEV_MINO_LOCK35",0.001,1,false);
        if(bosses.getDouble("bloqueos.HYDEV_MINO_LOCK70")<=bosses.getDouble("bloqueos.HYDEV_MINO_LOCK35"))throw new java.io.IOException("dungeons/jefes.yml: el primer bloqueo debe superar al segundo");
        var portal=values.get("dungeons/portal.yml");
        bounded(portal,"zona-extraccion.radio-horizontal",0.1,64,false);
        bounded(portal,"zona-extraccion.altura-inferior",-64,64,false);
        bounded(portal,"zona-extraccion.altura-superior",-64,64,false);
        if(portal.getDouble("zona-extraccion.altura-inferior")>=portal.getDouble("zona-extraccion.altura-superior"))throw new java.io.IOException("dungeons/portal.yml: alturas invertidas");
        var resonance=values.get("resonancia/config.yml");
        for(int tier=1;tier<=3;tier++) {
            double total=0;
            for(String quality:List.of("basica","intermedia","superior")) {
                String key="reglas.calidad.tier"+tier+"."+quality;Object value=resonance.get(key);
                if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<0||n.doubleValue()>100)throw new java.io.IOException("resonancia/config.yml: probabilidad inválida en "+key);
                total+=((Number)value).doubleValue();
            }
            if(Math.abs(total-100)>.000001)throw new java.io.IOException("resonancia/config.yml: reglas.calidad.tier"+tier+" debe sumar 100");
            Object charge=resonance.get("reglas.carga-por-tier.tier"+tier);
            if(!(charge instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<0||n.intValue()>1000000)throw new java.io.IOException("resonancia/config.yml: carga-por-tier inválida");
        }
        var shared=values.get("tesoros/config.yml");
        for(String domain:List.of("mineria","combate","agricultura","trabajos-mineria")) {
            Set<String> used=new HashSet<>();
            for(int tier=1;tier<=3;tier++) {
                Object list=shared.get("clasificacion."+domain+".tier"+tier);
                if(!(list instanceof List<?> entries))throw new java.io.IOException("tesoros/config.yml: lista de clasificación inválida");
                for(Object item:entries) {
                    if(!(item instanceof String name)||!used.add(name))throw new java.io.IOException("tesoros/config.yml: clasificación duplicada o inválida: "+item);
                    try { if(domain.equals("combate"))org.bukkit.entity.EntityType.valueOf(name);else Material.valueOf(name); }
                    catch(IllegalArgumentException error){throw new java.io.IOException("tesoros/config.yml: material o entidad desconocidos: "+item);}
                }
            }
        }
        var cooking=values.get("cocina/config.yml");Set<String> tools=new HashSet<>();
        for(String action:List.of("crear","revolver","servir"))checkTool(cooking.get("herramientas."+action),tools);
        Object cleaners=cooking.get("herramientas.limpiar");
        if(!(cleaners instanceof List<?> list))throw new java.io.IOException("cocina/config.yml: limpiar debe ser una lista");
        for(Object tool:list)checkTool(tool,tools);
    }
    private static void bounded(org.bukkit.configuration.file.YamlConfiguration config,String key,double min,double max,boolean integer)throws java.io.IOException {
        Object value=config.get(key);
        if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max||(integer&&n.doubleValue()!=Math.rint(n.doubleValue())))throw new java.io.IOException("Valor fuera de rango: "+key+" ["+min+", "+max+"]");
    }
    private static void checkTool(Object value,Set<String> used)throws java.io.IOException {
        if(!(value instanceof String name)||!used.add(name))throw new java.io.IOException("cocina/config.yml: herramienta duplicada o inválida");
        try { Material material=Material.valueOf(name);if(name.equals("AIR")||(org.bukkit.Bukkit.getServer()!=null&&!material.isItem()))throw new IllegalArgumentException(); }
        catch(IllegalArgumentException error){throw new java.io.IOException("cocina/config.yml: herramienta desconocida: "+name);}
    }
}
