package dev.hydev.spectrecore.core;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface SkillModule {
    String id();
    boolean hasSet(Player player);
    void tick(long engineTick);
    default void onMelee(Player player, LivingEntity enemy, long engineTick) { }
    default void onHostileDamage(Player player, LivingEntity aggressor, long engineTick) { }
    /** Applied only to direct melee hits against hostile entities, never secondary skill damage. */
    default double outgoingMultiplier(Player player, LivingEntity target, long engineTick) { return 1.0; }
    /** Applied to incoming damage of any kind; no other event handler should modify it twice. */
    default double incomingMultiplier(Player player, long engineTick) { return 1.0; }
    /** Called only on the press, not release, of Shift. */
    default void onSneakPress(Player player, long engineTick) { }
    void clear(UUID playerId);
    String status(Player player);
}
