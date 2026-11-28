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
        int previousLevel=0;
        for(String stage : List.of("inicial","paladio","duendes",
                "resonancia","mitrilo","arcana","ingenieria")) {
            String key="progresion-mineria."+stage;
            bounded(jobs,key,1,100,true);
            int level=jobs.getInt(key);
            if(level<=previousLevel)
                throw new java.io.IOException("trabajos/config.yml: niveles de Minería fuera de orden: "+stage);
            previousLevel=level;
        }
        var stages=jobs.getConfigurationSection("progresion-mineria.nombres");
        if(stages==null)
            throw new java.io.IOException("trabajos/config.yml: faltan nombres de progresión minera");
        for(String stage:List.of("inicial","hechizados","paladio","duendes",
                "resonancia","mitrilo","arcana","ingenieria")) {
            String label=jobs.getString("progresion-mineria.nombres."+stage,"");
            if(label.isBlank()||label.length()>96)
                throw new java.io.IOException("trabajos/config.yml: nombre de hito minero inválido: "+stage);
        }
        String workshop = "taller-minero.";
        bounded(jobs, workshop+"nivel-minimo", 1, 100, true);
        if(jobs.getInt("progresion-mineria.inicial")>jobs.getInt(workshop+"nivel-minimo")
                || jobs.getInt(workshop+"nivel-minimo")>=jobs.getInt("progresion-mineria.paladio"))
            throw new java.io.IOException("trabajos/config.yml: Mineralurgia debe quedar entre inicio y Paladio");
        bounded(jobs, workshop+"compactar-unidades", 64, 64, true);
        bounded(jobs, workshop+"max-operaciones", 1, 16, true);
        Map<String,String> enchantedMinerals = Map.of(
                "cobre","COBRE", "hierro","HIERRO", "oro","ORO",
                "lapis","LAPIS", "redstone","REDSTONE", "diamante","DIAMANTE");
        Set<String> rawInputs = new HashSet<>();
        Set<String> supportedRaw = Set.of("COPPER_INGOT","IRON_INGOT","GOLD_INGOT",
                "LAPIS_LAZULI","REDSTONE","DIAMOND");
        for (var entry : enchantedMinerals.entrySet()) {
            String root = workshop+"recetas."+entry.getKey()+".";
            if (!entry.getValue().equals(jobs.getString(root+"resultado-id")))
                throw new java.io.IOException("trabajos/config.yml: ID encantado modificado: "+entry.getKey());
            String origin = jobs.getString(root+"origen","");
            // Registry-free validation also runs in headless Gradle tests.
            if (!supportedRaw.contains(origin) || !rawInputs.add(origin))
                throw new java.io.IOException("trabajos/config.yml: mineral base inválido o repetido: "+origin);
            if (!jobs.isString(root+"nombre") || jobs.getString(root+"nombre","").isBlank()
                    || jobs.getString(root+"nombre","").length()>48)
                throw new java.io.IOException("trabajos/config.yml: nombre de mineral inválido: "+entry.getKey());
            bounded(jobs, root+"pago", 1, 1000000, true);
        }
        var ores = values.get("tesoros/mineria.yml");
        if (!"MATERIAL".equals(ores.getString("mitrilo.tipo"))
                || !"MITRILO".equals(ores.getString("mitrilo.id")))
            throw new java.io.IOException("tesoros/mineria.yml: el Mitrilo debe conservar el ID MATERIAL:MITRILO");
        for (int tier=1; tier<=3; tier++)
            bounded(ores,"probabilidades.mineria.tier"+tier,0,100,false);
        for (String path : List.of("hallazgos","encuentros","botin")) {
            bounded(ores, "suerte."+path+".incremento-relativo-por-punto",0,10,false);
            bounded(ores, "suerte."+path+".multiplicador-maximo",1,5,false);
        }
        for (String material : List.of("paladio","mitrilo")) {
            bounded(ores, "suerte.cantidad-"+material+".por-punto",0,10,false);
            bounded(ores, "suerte.cantidad-"+material+".maximo-porcentaje",0,100,false);
        }
        if(!ores.getStringList("taladro.tipos").equals(
                List.of("TALADRO","TALADRO_REFORZADO","TALADRO_CALIBRADO")))
            throw new java.io.IOException("tesoros/mineria.yml: conservar los IDs del Taladro y sus dos mejoras");
        Set<String> drillableRocks=Set.of("STONE","DEEPSLATE","ANDESITE","DIORITE","GRANITE","TUFF","CALCITE");
        var rocks=ores.getStringList("taladro.bloques");
        if(rocks.isEmpty() || rocks.size()>drillableRocks.size()
                || new HashSet<>(rocks).size()!=rocks.size() || !drillableRocks.containsAll(rocks))
            throw new java.io.IOException("tesoros/mineria.yml: Taladro solo puede perforar piedra");
        for(int tier=1;tier<=3;tier++){
            bounded(ores,"taladro.intervalo-milisegundos.nivel"+tier,150,1200,true);
            bounded(ores,"taladro.extras.nivel"+tier,0,3,true);
        }
        if(ores.getInt("taladro.extras.nivel1")!=0
                || ores.getInt("taladro.extras.nivel2")>1
                || ores.getInt("taladro.extras.nivel3")>3)
            throw new java.io.IOException("tesoros/mineria.yml: Taladro supera sus límites de extracción");
        bounded(ores,"suerte.calidad-gemas.puntos-por-luck",0,1,false);
        bounded(ores,"suerte.calidad-gemas.maximo-puntos",0,10,false);
        bounded(ores,"encuentros.max-activos",1,16,true);
        Set<String> permittedGoblin=Set.of("GOLD_ORE","DEEPSLATE_GOLD_ORE");
        Set<String> permittedProspector=Set.of("DIAMOND_ORE","DEEPSLATE_DIAMOND_ORE",
                "EMERALD_ORE","DEEPSLATE_EMERALD_ORE","OBSIDIAN");
        for(String kind : List.of("duende","prospector")) {
            String rootEncounter="encuentros."+kind+".";
            String mob=ores.getString(rootEncounter+"mob","");
            if(!mob.matches("[A-Za-z0-9_]{2,64}"))
                throw new java.io.IOException("tesoros/mineria.yml: mob minero inválido: "+kind);
            var triggers=ores.getStringList(rootEncounter+"menas");
            Set<String> allowed=kind.equals("duende")?permittedGoblin:permittedProspector;
            if(triggers.isEmpty() || triggers.size()>allowed.size()
                    || new HashSet<>(triggers).size()!=triggers.size() || !allowed.containsAll(triggers))
                throw new java.io.IOException("tesoros/mineria.yml: menas de encuentro inválidas: "+kind);
            bounded(ores,rootEncounter+"probabilidad",0,100,false);
            bounded(ores,rootEncounter+"recarga-segundos",0,3600,true);
            List<String> drops=kind.equals("duende")?List.of("chatarra"):
                    List.of("chatarra","paladio","fragmento");
            for(String drop:drops) {
                String rootDrop=rootEncounter+"botin."+drop+".";
                String id=ores.getString(rootDrop+"id","");
                if(!"MATERIAL".equals(ores.getString(rootDrop+"tipo"))
                        || !id.matches("[A-Z][A-Z0-9_]{2,63}")
                        || !ores.isString(rootDrop+"nombre"))
                    throw new java.io.IOException("tesoros/mineria.yml: botín inválido "+rootDrop);
                bounded(ores,rootDrop+"probabilidad",0,100,false);
                bounded(ores,rootDrop+"minimo",1,32,true);
                bounded(ores,rootDrop+"maximo",1,32,true);
                if(ores.getInt(rootDrop+"maximo")<ores.getInt(rootDrop+"minimo"))
                    throw new java.io.IOException("tesoros/mineria.yml: cantidad invertida en "+rootDrop);
                if(drop.equals("paladio") && !"PALADIO".equals(id))
                    throw new java.io.IOException("tesoros/mineria.yml: el Paladio debe conservar su ID");
            }
        }
        bounded(ores, "geodas.probabilidad-porcentaje", 0, 100, false);
        bounded(ores, "geodas.duracion-minutos", 1, 1440, true);
        bounded(ores, "mitrilo.cantidad-minima", 1, 64, true);
        bounded(ores, "mitrilo.cantidad-maxima", 1, 64, true);
        if (ores.getInt("mitrilo.cantidad-maxima") < ores.getInt("mitrilo.cantidad-minima"))
            throw new java.io.IOException("tesoros/mineria.yml: cantidad Mitrilo invertida");
        bounded(ores, "mitrilo.rango-minimo", 2, 3, true);
        String root = "herramientas-mitrilo.";
        for (String item : List.of("resonancia.maxima","resonancia.por-mineral-natural",
                "cristal.recupera","pico.activacion","pico.duracion-segundos",
                "pico.radio","pico.max-resaltados","pico.resaltado-segundos",
                "pico.max-bloques-adicionales","pico.coste-bloque-adicional",
                "pico.recarga-segundos",
                "barrena.activacion","barrena.pasos","barrena.intervalo-ticks",
                "barrena.recarga-segundos"))
            bounded(ores, root+item, 1, 256, true);
        if (ores.getInt(root+"resonancia.maxima") > 200
                || ores.getInt(root+"pico.radio") > 6
                || ores.getInt(root+"pico.max-resaltados") > 8
                || ores.getInt(root+"pico.max-bloques-adicionales") > 3
                || ores.getInt(root+"barrena.pasos") > 8
                || ores.getInt(root+"barrena.intervalo-ticks") < 4
                || ores.getInt(root+"barrena.intervalo-ticks") > 20)
            throw new java.io.IOException("tesoros/mineria.yml: límites de rendimiento de Mitrilo fuera de rango");
        String lantern = root + "linterna.";
        bounded(ores, lantern+"vision-segundos", 15, 600, true);
        bounded(ores, lantern+"radio", 1, 16, true);
        bounded(ores, lantern+"resaltado-segundos", 1, 5, true);
        bounded(ores, lantern+"recarga-segundos", 30, 600, true);
        bounded(ores, lantern+"max-menas", 1, 32, true);
        bounded(ores, lantern+"max-pulsos", 1, 8, true);
        String field = root + "pico-amatista.";
        for (String number : List.of("activacion","recarga-segundos","duracion-segundos",
                "bloques","minimo-bloques","max-sesiones","golpes","intervalo-golpe-ms"))
            bounded(ores, field + number, 1, 1000, true);
        if (ores.getInt(field+"activacion") > ores.getInt(root+"resonancia.maxima")
                || ores.getInt(field+"recarga-segundos") < 30 || ores.getInt(field+"recarga-segundos") > 600
                || ores.getInt(field+"duracion-segundos") > 60
                || ores.getInt(field+"bloques") > 6 || ores.getInt(field+"bloques") < 3
                || ores.getInt(field+"minimo-bloques") < 2
                || ores.getInt(field+"minimo-bloques") > ores.getInt(field+"bloques")
                || ores.getInt(field+"max-sesiones") > 12
                || ores.getInt(field+"golpes") > 6
                || ores.getInt(field+"intervalo-golpe-ms") < 200)
            throw new java.io.IOException("tesoros/mineria.yml: límites de resonancia amplificada inválidos");
        int totalGemChance = 0;
        Set<String> gemIds = new HashSet<>();
        for (String grade : List.of("basica", "intermedia", "superior")) {
            bounded(ores, field + "calidad." + grade, 0, 100, true);
            totalGemChance += ores.getInt(field + "calidad." + grade);
            String id = ores.getString(field + "gemas." + grade, "");
            if (!id.matches("[A-Z][A-Z0-9_]{2,63}") || !gemIds.add(id))
                throw new java.io.IOException("tesoros/mineria.yml: gemas de amatista con IDs inválidos o repetidos");
        }
        if (totalGemChance != 100)
            throw new java.io.IOException("tesoros/mineria.yml: calidad de gemas de amatista debe sumar 100");
        var safeStone = Set.of("STONE", "DEEPSLATE", "ANDESITE", "DIORITE",
                "GRANITE", "TUFF", "CALCITE", "BLACKSTONE", "BASALT",
                "NETHERRACK", "END_STONE");
        var drillable = ores.getStringList(root+"barrena.bloques");
        if (drillable.isEmpty() || drillable.size() > safeStone.size()
                || !safeStone.containsAll(drillable)
                || new HashSet<>(drillable).size() != drillable.size())
            throw new java.io.IOException("tesoros/mineria.yml: la Barrena solo admite roca común sin duplicados");
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
