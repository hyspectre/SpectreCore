package dev.hydev.spectrecore.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistent LuckPerms grant for Jobs. No console commands or temporary OP permissions.
 * Only fixed, validated trabajo.<job>.<rank> nodes are allowed.
 *
 * Vault and LuckPerms storage are different systems: callers must log and
 * compensate on failures. They are NOT an ACID transaction across crashes.
 */
public final class LuckPermsContractBridge {
    private final JavaPlugin plugin;
    public LuckPermsContractBridge(JavaPlugin plugin) { this.plugin = plugin; }
    private LuckPerms api() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return null;
        try { return LuckPermsProvider.get(); }
        catch (IllegalStateException | LinkageError error) { return null; }
    }
    public boolean ready(Player player) {
        LuckPerms lp = api();
        return lp != null && player != null && lp.getUserManager().getUser(player.getUniqueId()) != null;
    }

    public CompletableFuture<Boolean> grant(Player player, String job, String level) {
        if (player == null || !dev.hydev.spectrecore.systems.JobsContractRules.JOBS.contains(job)
                || !dev.hydev.spectrecore.systems.JobsContractRules.LEVELS.contains(level))
            return CompletableFuture.completedFuture(false);
        LuckPerms lp = api();
        if (lp == null) return CompletableFuture.completedFuture(false);
        UUID uuid = player.getUniqueId();
        User user = lp.getUserManager().getUser(uuid);
        if (user == null) return CompletableFuture.completedFuture(false);

        Node node = Node.builder("trabajo." + job + "." + level).value(true).build();
        try {
            // Source is the local in-memory user node, never player names or YAML commands.
            user.data().add(node);
            return lp.getUserManager().saveUser(user).handle((ignored, error) -> {
                if (error == null) return true;
                // Best-effort compensating change; never claim a guaranteed rollback
                // against an external permissions store.
                user.data().remove(node);
                lp.getUserManager().saveUser(user).exceptionally(rollbackError -> {
                    plugin.getLogger().severe("LUCKPERMS COMPENSATION FAILED: " + uuid + " node=" + node.getKey()
                            + " cause=" + rollbackError.getMessage());
                    return null;
                });
                plugin.getLogger().severe("LUCKPERMS SAVE FAILED: " + uuid + " node=" + node.getKey()
                        + " cause=" + error.getMessage());
                return false;
            });
        } catch (RuntimeException | LinkageError error) {
            user.data().remove(node);
            plugin.getLogger().severe("LUCKPERMS GRANT FAILED: " + uuid + " node=" + node.getKey()
                    + " cause=" + error.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
