package dev.hydev.spectrecore.integration;

import dev.hydev.spectrecore.systems.SlayerProgressionRules;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Idempotently reconciles earned Slayer nodes with LuckPerms.
 * Slayer XP remains authoritative in slayer-data.yml; grants are retried on login.
 */
public final class SlayerUnlockBridge {
    private final JavaPlugin plugin;
    public SlayerUnlockBridge(JavaPlugin plugin) { this.plugin = plugin; }

    public void synchronize(UUID playerId, int level) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.getLogger().warning("Slayer: LuckPerms absent; unlocks pending for " + playerId);
            return;
        }
        final LuckPerms api;
        try { api = LuckPermsProvider.get(); }
        catch (IllegalStateException | LinkageError error) {
            plugin.getLogger().warning("Slayer: LuckPerms API absent; unlocks pending for " + playerId);
            return;
        }
        api.getUserManager().loadUser(playerId).thenCompose(user -> {
            for (String permission : SlayerProgressionRules.earnedNodes(level))
                user.data().add(Node.builder(permission).value(true).build());
            return api.getUserManager().saveUser(user);
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("SLAYER UNLOCK RECONCILIATION NEEDED player="
                        + playerId + " level=" + level + " cause=" + failure);
                return;
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) player.recalculatePermissions();
            });
        });
    }
}
