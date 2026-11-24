package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.SpectreCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only AuraSkills integration. Missing Luck means no special bonus. */
public final class MiningLuck {
    private final JavaPlugin plugin;
    public MiningLuck(JavaPlugin plugin) { this.plugin = plugin; }

    public double stat(Player player) {
        if (!(plugin instanceof SpectreCorePlugin core) || core.auraSkillsBridge() == null) return 0;
        double result = core.auraSkillsBridge().miningLuck(player);
        return Double.isFinite(result) && result > 0 ? Math.min(10000, result) : 0;
    }
}
