package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.ModuleConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

/**
 * AuraSkills is the source of XP, levels, permission rewards and level-up text.
 * SpectreCore requires BOTH its authoritative current Mining level and the
 * permission AuraSkills grants at each milestone. No duplicate level-up spam.
 */
public final class MiningProgression {
    public static final List<String> STAGES = List.of("inicial","hechizados","paladio",
            "duendes","resonancia","mitrilo","arcana","ingenieria");
    private final JavaPlugin plugin;
    public MiningProgression(JavaPlugin plugin) { this.plugin=plugin; }

    public int level(Player player) {
        if (!(plugin instanceof SpectreCorePlugin core) || core.auraSkillsBridge()==null)return -1;
        return core.auraSkillsBridge().miningLevel(player);
    }
    public int required(String stage) {
        if(!STAGES.contains(stage))throw new IllegalArgumentException("Hito minero desconocido");
        var cfg=ModuleConfiguration.module(plugin,"trabajos/config.yml");
        return stage.equals("hechizados")?cfg.getInt("taller-minero.nivel-minimo",3)
                :cfg.getInt("progresion-mineria."+stage,-1);
    }
    public String permission(String stage) {
        if(!STAGES.contains(stage))throw new IllegalArgumentException("Hito minero desconocido");
        return "hydev.mineria."+stage;
    }
    public boolean meets(Player player,String stage) {
        int current=level(player),minimum=required(stage);
        // Player permission comes from AuraSkills rewards/mining.yml, not a paid job rank.
        return current>=0 && minimum>=1 && current>=minimum
                && player.hasPermission(permission(stage));
    }
    public int contract(String rank) {
        return required(switch(rank){
            case "novato" -> "inicial";
            case "experimentado" -> "paladio";
            case "profesional" -> "resonancia";
            default -> throw new IllegalArgumentException("Contrato minero desconocido");
        });
    }
    public void enable() {
        if(!Bukkit.getPluginManager().isPluginEnabled("AuraSkills"))
            plugin.getLogger().severe("Minería: AuraSkills ausente. Los hitos se mantendrán bloqueados.");
    }
    public void disable() {
        // No listener, schedules or duplicate progress state to clean up.
    }
}
