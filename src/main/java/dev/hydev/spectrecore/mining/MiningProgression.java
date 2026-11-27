package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
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
    /** Read-only presentation, never a second access check or permission grant. */
    public boolean missingLevel(Player player, String stage) {
        int current = level(player), minimum = required(stage);
        return current >= 0 && minimum >= 1 && current < minimum;
    }
    public String lockDetail(Player player, String stage) {
        int current = level(player), minimum = required(stage);
        if (current < 0 || minimum < 1)
            return Settings.text("trabajos", "MiningProgression.progreso-no-disponible",
                    "<yellow>Progreso minero no disponible");
        if (current < minimum)
            return Settings.text("trabajos", "MiningProgression.nivel-requerido",
                    "<yellow>Requiere Minería nivel {nivel}", "nivel", minimum);
        return Settings.text("trabajos", "MiningProgression.desbloqueo-pendiente",
                "<yellow>Desbloqueo pendiente · Consulta al Minero");
    }
    public static String contractStage(String rank) {
        return switch(rank){
            case "novato" -> "inicial";
            case "experimentado" -> "paladio";
            case "profesional" -> "resonancia";
            default -> throw new IllegalArgumentException("Contrato minero desconocido");
        };
    }
    public int contract(String rank) { return required(contractStage(rank)); }
    public void enable() {
        if(!Bukkit.getPluginManager().isPluginEnabled("AuraSkills"))
            plugin.getLogger().severe("Minería: AuraSkills ausente. Los hitos se mantendrán bloqueados.");
    }
    public void disable() {
        // No listener, schedules or duplicate progress state to clean up.
    }
}
