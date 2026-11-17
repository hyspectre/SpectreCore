package dev.hydev.spectrecore.core;

import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared transient player attribute modifiers and compact Minecraft block-display geometry. */
public final class Effects {
    private final JavaPlugin plugin;
    private final Visuals visuals;
    public Effects(JavaPlugin plugin, Visuals visuals) { this.plugin = plugin; this.visuals = visuals; }

    public void setAttribute(Player player, Attribute attribute, String key, double amount,
                             AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey id = new NamespacedKey(plugin, key);
        AttributeModifier old = instance.getModifier(id);
        if (old != null) instance.removeModifier(old);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }
    public void clearAttribute(Player player, Attribute attribute, String key) {
        setAttribute(player, attribute, key, 0, AttributeModifier.Operation.ADD_NUMBER);
    }

    public BlockDisplay block(Player owner, Material material, float sx, float sy, float sz) {
        return owner.getWorld().spawn(owner.getLocation(), BlockDisplay.class, e -> {
            e.setBlock(material.createBlockData());
            visuals.mark(e);
            scale(e, sx, sy, sz);
        });
    }
    public static void scale(BlockDisplay block, float x, float y, float z) {
        block.setTransformation(new Transformation(new Vector3f(-x / 2f, -y / 2f, -z / 2f),
                new Quaternionf(), new Vector3f(x, y, z), new Quaternionf()));
    }
    public static void position(BlockDisplay block, Location point) {
        if (block.isValid()) block.teleport(point);
    }
    public static Location relative(Player player, double right, double forward, double up) {
        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001) direction.setX(0).setZ(1);
        direction.normalize();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());
        return player.getLocation().add(sideways.multiply(right)).add(direction.multiply(forward)).add(0, up, 0);
    }
    public static void dust(Location point, int red, int green, int blue, int count, float size) {
        point.getWorld().spawnParticle(Settings.particle("equipamiento", "Effects.particle-001", Particle.DUST), point, count,Settings.d("equipamiento", "Effects.spawnparticle-001-arg3", 0.07),Settings.d("equipamiento", "Effects.spawnparticle-002-arg4", 0.08),Settings.d("equipamiento", "Effects.spawnparticle-003-arg5", 0.07),Settings.d("equipamiento", "Effects.spawnparticle-004-arg6", 0),
                new Particle.DustOptions(Color.fromRGB(red, green, blue), size));
    }
}
