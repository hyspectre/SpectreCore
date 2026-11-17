package dev.hydev.spectrecore.core;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Narrowly scoped non-crit two-piece bonuses. Do NOT also configure these in MMOItems. */
public final class PassiveBonuses {
    private final SpectreCorePlugin plugin;
    private final Map<UUID, Set<String>> active = new HashMap<>();
    private final Map<UUID, Map<String,Double>> amounts = new HashMap<>();
    public PassiveBonuses(SpectreCorePlugin plugin) { this.plugin = plugin; }
    private boolean eligible(Player p, String id) {
        return plugin.getConfig().getBoolean("skills." + id + ".enabled", false)
                && plugin.sets().countArmor(p, plugin.getConfig().getConfigurationSection("skills." + id + ".armor")) >= Settings.module("equipamiento/config.yml").getInt("pasivos.piezas",2);
    }
    private void apply(Player p, String id, Attribute attr, String key, double amount, AttributeModifier.Operation op) {
        if (eligible(p, id)) {
            var values=amounts.computeIfAbsent(p.getUniqueId(),unused->new HashMap<>());
            if (active.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(key)||!Double.valueOf(amount).equals(values.get(key))) {
                plugin.effects().setAttribute(p, attr, key, amount, op);
                values.put(key,amount);
            }
        } else {
            Set<String> keys = active.get(p.getUniqueId());
            if (keys != null && keys.remove(key)) plugin.effects().clearAttribute(p, attr, key);
        }
    }
    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isValid() || p.isDead()) { clear(p); continue; }
            var config=Settings.module("equipamiento/config.yml");
            apply(p, "asterion", Attribute.MAX_HEALTH, "set_laberinto_health", config.getDouble("pasivos.laberinto-vida",6), AttributeModifier.Operation.ADD_NUMBER);
            apply(p, "ultimo-juramento", Attribute.MOVEMENT_SPEED, "set_voto_speed", config.getDouble("pasivos.voto-velocidad",.04), AttributeModifier.Operation.ADD_SCALAR);
            apply(p, "sangre-primer-dragon", Attribute.MAX_HEALTH, "set_dragon_health", config.getDouble("pasivos.dragon-vida",4), AttributeModifier.Operation.ADD_NUMBER);
            apply(p, "sangre-primer-dragon", Attribute.MOVEMENT_SPEED, "set_dragon_speed", config.getDouble("pasivos.dragon-velocidad",.02), AttributeModifier.Operation.ADD_SCALAR);
        }
    }
    public void clear(Player p) {
        amounts.remove(p.getUniqueId());
        Set<String> keys = active.remove(p.getUniqueId());
        if (keys == null) return;
        for (String key : keys) {
            Attribute attribute = key.endsWith("health") ? Attribute.MAX_HEALTH : Attribute.MOVEMENT_SPEED;
            plugin.effects().clearAttribute(p, attribute, key);
        }
    }
}
