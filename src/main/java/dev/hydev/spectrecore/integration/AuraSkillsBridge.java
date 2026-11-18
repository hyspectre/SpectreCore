package dev.hydev.spectrecore.integration;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.stat.StatModifier;
import dev.aurelium.auraskills.api.stat.Stats;
import dev.aurelium.auraskills.api.user.SkillsUser;
import dev.aurelium.auraskills.api.util.AuraSkillsModifier;
import dev.hydev.spectrecore.SpectreCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Optional AuraSkills 2.3 stat modifier bridge. Never alters player XP, skill levels or permanent stats.
 * All owned modifiers have unique names, are non-persistent and have a wall-clock safety expiration.
 * Public methods only expose Bukkit/SpectreCore types to keep the optional API behind this boundary.
 */
public final class AuraSkillsBridge {
    private final SpectreCorePlugin plugin;
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private boolean warned;

    public AuraSkillsBridge(SpectreCorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ready() {
        return plugin.getServer().getPluginManager().isPluginEnabled("AuraSkills");
    }

    private SkillsUser user(Player player) {
        if (!ready() || player == null || !player.isOnline()) return null;
        SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
        return user != null && user.isLoaded() ? user : null;
    }

    /**
     * Add a named temporary Strength modifier. false means the legacy damage multiplier must be used.
     * safetySeconds must exceed the skill's maximal active duration; normal removal is driven by SpectreCore.
     */
    public boolean applyStrength(Player player, String key, double amount, long safetySeconds) {
        if (player == null || key == null || !key.startsWith("spectrecore_")
                || !Double.isFinite(amount) || amount <= 0 || amount > 5000 || safetySeconds < 1) return false;
        try {
            SkillsUser user = user(player);
            if (user == null) return false;
            if (user.getStatModifier(key) != null) user.removeStatModifier(key);
            StatModifier modifier = new StatModifier(key, Stats.STRENGTH, amount, AuraSkillsModifier.Operation.ADD);
            modifier.setNonPersistent();
            modifier.makeTemporary(System.currentTimeMillis() + Math.min(safetySeconds, 3600L) * 1000L, false);
            user.addStatModifier(modifier);
            if (user.getStatModifier(key) == null) return false;
            owned.computeIfAbsent(player.getUniqueId(), unused -> new HashSet<>()).add(key);
            return true;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return false;
        }
    }

    /** Source of truth for avoiding a second legacy damage multiplier. */
    public boolean isApplied(Player player, String key) {
        if (player == null || key == null) return false;
        if (!owned.getOrDefault(player.getUniqueId(), Set.of()).contains(key)) return false;
        try {
            SkillsUser user = user(player);
            return user != null && user.getStatModifier(key) != null;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return false;
        }
    }

    public void remove(Player player, String key) {
        if (player == null || key == null) return;
        UUID id = player.getUniqueId();
        Set<String> keys = owned.get(id);
        if (keys == null || !keys.remove(key)) return;
        if (keys.isEmpty()) owned.remove(id);
        try {
            SkillsUser user = user(player);
            if (user != null && user.getStatModifier(key) != null) user.removeStatModifier(key);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    /** Called before shutdown or before AuraSkills itself is disabled. */
    public void clearAll() {
        for (UUID id : Set.copyOf(owned.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) { owned.remove(id); continue; }
            for (String key : Set.copyOf(owned.getOrDefault(id, Set.of()))) remove(player, key);
        }
        owned.clear();
    }

    private void warn(Throwable error) {
        if (warned) return;
        warned = true;
        plugin.getLogger().warning("AuraSkillsBridge: no se pudo actualizar el modificador temporal; se usara el multiplicador antiguo. " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }
}
