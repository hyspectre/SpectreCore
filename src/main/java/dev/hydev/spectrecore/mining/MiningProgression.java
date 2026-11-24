package dev.hydev.spectrecore.mining;

import dev.aurelium.auraskills.api.event.skill.SkillLevelUpEvent;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

/**
 * AuraSkills owns XP, stat rewards and its native level UI; SpectreCore only
 * evaluates gameplay capabilities. No second XP, menu, toggles or persistent
 * duplicate levels.
 */
public final class MiningProgression implements Listener {
    public static final List<String> STAGES = List.of("inicial","hechizados","paladio",
            "duendes","resonancia","mitrilo","arcana","ingenieria");
    private final JavaPlugin plugin;
    private boolean listening;
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
    public boolean meets(Player player,String stage) {
        int current=level(player),threshold=required(stage);
        return current>=0 && threshold>=1 && current>=threshold;
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
        if(!Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            plugin.getLogger().severe("Minería: AuraSkills ausente; los desbloqueos se bloquean hasta corregir la dependencia.");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        listening=true;
    }
    public void disable() {
        if(listening)HandlerList.unregisterAll(this);
        listening=false;
    }
    @EventHandler public void onLevel(SkillLevelUpEvent event) {
        if(event.getSkill()!=Skills.MINING)return;
        int newLevel=event.getLevel();
        var config=ModuleConfiguration.module(plugin,"trabajos/config.yml");
        for(String stage:STAGES) if(required(stage)==newLevel) {
            String label=config.getString("progresion-mineria.nombres."+stage,stage);
            event.getPlayer().sendMessage(Settings.message("trabajos","MiningProgression.desbloqueado",
                    "<gold>✦ Minería nivel {nivel}: <white>{nombre} <green>desbloqueado.",
                    "nivel",newLevel,"nombre",label));
        }
    }
}
