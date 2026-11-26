package dev.hydev.spectrecore.core;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Bounded, private combat feedback. Only confirmed direct melee multipliers
 * are labelled; do not infer bonuses from the final damage or external stats. */
public final class CombatFeedback {
    private record Pulse(TextDisplay display, long expires) { }
    private final SpectreCorePlugin plugin;
    private final Map<EntityDamageByEntityEvent, Double> pending = new WeakHashMap<>();
    private final Map<UUID, Pulse> pulses = new HashMap<>();
    private boolean warned;

    public CombatFeedback(SpectreCorePlugin plugin) { this.plugin = plugin; }

    public void mark(EntityDamageByEntityEvent event, double factor) {
        if (Double.isFinite(factor) && factor > 1 && event.getDamage() > 0)
            pending.put(event, factor);
    }

    public void confirm(EntityDamageByEntityEvent event, Player player, LivingEntity target, long tick) {
        Double factor = pending.remove(event);
        if (factor == null || event.isCancelled() || event.getFinalDamage() <= 0 || target.getWorld() != player.getWorld()) return;
        UUID owner = player.getUniqueId();
        Pulse previous = pulses.get(owner);
        if (previous != null && previous.expires() > tick) return; // Do not stack floats on rapid attacks.
        clear(owner);
        double percent = CombatRules.bonusPercent(factor);
        if (percent <= 0) return;
        String bonus = BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString();
        var text = Settings.message("equipamiento", "CombatFeedback.golpe-potenciado",
                "<#EBC88A>✦ +{dano}% <gray>daño de habilidad", "dano", bonus);
        var location = target.getLocation().add(0, target.getHeight() +
                Settings.d("equipamiento", "CombatFeedback.altura", .35), 0);
        TextDisplay display = null;
        try {
            display = target.getWorld().spawn(location, TextDisplay.class, e -> {
                plugin.visuals().mark(e);
                e.setVisibleByDefault(false);
                e.setBillboard(Display.Billboard.CENTER);
                e.setShadowed(true);
                e.setDefaultBackground(false);
                e.setGravity(false);
                e.text(text);
            });
            player.showEntity(plugin, display);
            pulses.put(owner, new Pulse(display, tick +
                    Math.clamp(Settings.i("equipamiento", "CombatFeedback.duracion-ticks", 16), 4, 40)));
        } catch (RuntimeException | LinkageError error) {
            if (display != null) display.remove();
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Indicador de combate no disponible: " + error.getMessage());
            }
        }
    }

    public void tick(long tick) {
        var it = pulses.entrySet().iterator();
        while (it.hasNext()) {
            Pulse pulse = it.next().getValue();
            if (pulse.expires() <= tick || !pulse.display().isValid()) {
                pulse.display().remove();
                it.remove();
            }
        }
    }

    public void clear(UUID id) {
        Pulse pulse = pulses.remove(id);
        if (pulse != null) pulse.display().remove();
    }

    public void close() {
        for (Pulse pulse : pulses.values()) pulse.display().remove();
        pulses.clear();
        pending.clear();
    }
}
