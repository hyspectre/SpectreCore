package dev.hydev.spectrecore.config;

import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Cached, typed presentation settings. IDs and persistent storage keys never pass here. */
public final class Settings {
    private static volatile ModuleConfiguration configuration;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private Settings() { }
    public static void install(ModuleConfiguration manager) { configuration=manager; }
    public static void clear() { configuration=null; }
    public static void configureCommands(org.bukkit.plugin.java.JavaPlugin plugin) {
        var config=module("nucleo/comandos.yml");
        for(String name:plugin.getDescription().getCommands().keySet()) {
            var command=plugin.getCommand(name);if(command==null)continue;
            command.setDescription(config.getString(name+".descripcion",command.getDescription()));
            command.setUsage(config.getString(name+".uso",command.getUsage()));
        }
    }
    public static YamlConfiguration module(String path) { var manager=configuration; return manager==null?new YamlConfiguration():manager.read(path); }
    private static Object get(String module,String filename,String key,Object fallback) {
        var manager=configuration;
        if(manager==null)return fallback;
        Object value=manager.read(module+"/"+filename+".yml").get(key);
        return value==null?fallback:value;
    }
    public static String text(String module,String key,String fallback,Object... bindings) {
        return format(String.valueOf(get(module,"mensajes",key,fallback)),bindings);
    }
    /** One-pass substitution prevents item names containing braces from becoming other placeholders. */
    public static String format(String template,Object... bindings) {
        if(bindings.length%2!=0)throw new IllegalArgumentException("Bindings must be name/value pairs");
        Map<String,String> values=new HashMap<>();
        for(int n=0;n<bindings.length;n+=2)values.put(String.valueOf(bindings[n]),String.valueOf(bindings[n+1]));
        Matcher matcher=PLACEHOLDER.matcher(ChatColor.translateAlternateColorCodes('&',template));
        StringBuilder result=new StringBuilder();
        while(matcher.find())matcher.appendReplacement(result,Matcher.quoteReplacement(values.getOrDefault(matcher.group(1),matcher.group())));
        matcher.appendTail(result);return result.toString();
    }
    /** Native, injection-safe Adventure message from a configurable MiniMessage template. */
    public static Component message(String module, String key, String fallback, Object... bindings) {
        if (bindings.length % 2 != 0) throw new IllegalArgumentException("Bindings must be name/value pairs");
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < bindings.length; i += 2) values.put(String.valueOf(bindings[i]), bindings[i+1]);
        return dev.hydev.spectrecore.text.TextService.render(
                String.valueOf(get(module, "mensajes", key, fallback)), values);
    }
    /** Logs are plaintext: never print MiniMessage tags into the console. */
    public static String plain(String module, String key, String fallback, Object... bindings) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(message(module, key, fallback, bindings));
    }
    public static Component component(String text) { return dev.hydev.spectrecore.text.TextService.component(text); }
    public static Component component(String text,TextColor color) { return Component.empty().color(color).append(component(text)); }
    public static Component component(int value) { return Component.text(value); }
    public static String string(String module,String key,String fallback) { return String.valueOf(get(module,"ajustes",key,fallback)); }
    public static double d(String module,String key,double fallback) { return ((Number)get(module,"ajustes",key,fallback)).doubleValue(); }
    public static float f(String module,String key,float fallback) { return ((Number)get(module,"ajustes",key,fallback)).floatValue(); }
    public static int i(String module,String key,int fallback) { return ((Number)get(module,"ajustes",key,fallback)).intValue(); }
    public static long l(String module,String key,long fallback) { return ((Number)get(module,"ajustes",key,fallback)).longValue(); }
    public static Sound sound(String module,String key,Sound fallback) { return Sound.valueOf(string(module,key,fallback.name())); }
    public static Particle particle(String module,String key,Particle fallback) { return Particle.valueOf(string(module,key,fallback.name())); }
    public static NamedTextColor color(String module,String key,NamedTextColor fallback) {
        NamedTextColor result=NamedTextColor.NAMES.value(string(module,key,fallback.toString()).toLowerCase(Locale.ROOT));
        return result==null?fallback:result;
    }
    public static Material material(String module,String key,Material fallback) { return Material.valueOf(string(module,key,fallback.name())); }
    public static PotionEffectType potion(String module,String key,PotionEffectType fallback) { return PotionEffectType.getByName(string(module,key,fallback.getName())); }
    /** Validate the entire candidate snapshot before publishing it; a broken reload keeps live settings. */
    public static void validate(Map<String,YamlConfiguration> candidate,ClassLoader resources)throws Exception {
        YamlConfiguration limits=new YamlConfiguration();
        try(var in=resources.getResourceAsStream("settings-limits.json")) {
            if(in==null)throw new IOException("Missing settings limits");
            limits.load(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
        for(var entry:candidate.entrySet()) {
            String path=entry.getKey();if(!path.endsWith("/mensajes.yml")&&!path.endsWith("/ajustes.yml"))continue;
            YamlConfiguration defaults=new YamlConfiguration();
            try(var in=resources.getResourceAsStream("modules/"+path)) { defaults.load(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)); }
            for(String key:defaults.getKeys(true)) {
                if(defaults.isConfigurationSection(key))continue;
                Object expected=defaults.get(key),value=entry.getValue().get(key,expected);
                String at="modules/"+path+" -> "+key;
                if(expected instanceof Number number) {
                    if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException(at+": debe ser un número finito");
                    double v=n.doubleValue();
                    if((number instanceof Integer||number instanceof Long)&&v!=Math.rint(v))throw new IOException(at+": debe ser un entero");
                    if(limits.get(path+"|"+key) instanceof List<?> range && (v<((Number)range.get(0)).doubleValue()||v>((Number)range.get(1)).doubleValue()))throw new IOException(at+": fuera del rango "+range);
                } else if(expected instanceof Boolean) {
                    if(!(value instanceof Boolean))throw new IOException(at+": debe ser true/false");
                } else if(!(value instanceof String))throw new IOException(at+": debe ser texto entre comillas");
                if(!(value instanceof String string)||path.endsWith("/mensajes.yml"))continue;
                String leaf=key.substring(key.lastIndexOf('.')+1);
                try {
                    if(leaf.startsWith("sound-") || key.equals("MiningModule.geoda-sonido")) {
                        try { if(Sound.class.getField(string).getType()!=Sound.class)throw new NoSuchFieldException(string); }
                        catch(NoSuchFieldException error){throw new IllegalArgumentException("sonido desconocido: "+string);}
                    }
                    if(leaf.startsWith("particle-")) {
                        Particle def=Particle.valueOf(String.valueOf(expected)),selected=Particle.valueOf(string);
                        if(def.getDataType()!=selected.getDataType())throw new IllegalArgumentException("tipo de datos de partícula incompatible");
                    }
                    if(leaf.startsWith("color-")&&NamedTextColor.NAMES.value(string.toLowerCase(Locale.ROOT))==null)throw new IllegalArgumentException("color desconocido");
                    if(leaf.startsWith("material-")) {
                        Material selected=Material.valueOf(string),original=Material.valueOf(String.valueOf(expected));
                        if(org.bukkit.Bukkit.getServer()!=null) {
                            if(original.isBlock()&&!selected.isBlock())throw new IllegalArgumentException("debe ser un bloque");
                            if(original.isItem()&&!selected.isItem())throw new IllegalArgumentException("debe ser un objeto");
                        }
                    }
                    if(leaf.startsWith("pocion-")) {
                        try { if(PotionEffectType.class.getField(string).getType()!=PotionEffectType.class)throw new NoSuchFieldException(string); }
                        catch(NoSuchFieldException error){throw new IllegalArgumentException("poción desconocida");}
                    }
                } catch(IllegalArgumentException error){throw new IOException(at+": "+error.getMessage(),error);}
            }
        }
    }
}
