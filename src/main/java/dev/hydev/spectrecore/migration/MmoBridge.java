package dev.hydev.spectrecore.migration;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;

/** Reflection keeps the paid MMOItems JAR out of the distributable. */
public final class MmoBridge {
    private Class<?> api() throws ClassNotFoundException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (plugin == null || !plugin.isEnabled()) throw new ClassNotFoundException(Settings.text("nucleo", "MmoBridge.etiqueta-001", "Sistema de equipamiento no disponible"));
        return Class.forName("net.Indyuce.mmoitems.MMOItems", true, plugin.getClass().getClassLoader());
    }
    public ItemStack build(String type, String id, int amount) {
        if (type == null || type.isBlank() || id == null || id.isBlank() || amount <= 0) return null;
        try {
            Class<?> api = api(); Object instance = api.getField("plugin").get(null); Object result;
            try { result = api.getMethod("getItem", String.class, String.class).invoke(instance, type, id); }
            catch (NoSuchMethodException missingOverload) {
                Object types = api.getMethod("getTypes").invoke(instance);
                Object itemType = types.getClass().getMethod("get", String.class).invoke(types, type);
                if (itemType == null) return null;
                Method method = api.getMethod("getItem", Class.forName("net.Indyuce.mmoitems.api.Type", true, api.getClassLoader()), String.class);
                result = method.invoke(instance, itemType, id);
            }
            if (!(result instanceof ItemStack item) || item.getType().isAir()) return null;
            ItemStack copy = item.clone(); copy.setAmount(amount); return copy;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return null; }
    }
    public String id(ItemStack item) { return identify("getID", item); }
    public String type(ItemStack item) { return identify("getTypeName", item); }
    private String identify(String method, ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        try { Object value = api().getMethod(method, ItemStack.class).invoke(null, item); return value == null ? "" : value.toString(); }
        catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return ""; }
    }
}
