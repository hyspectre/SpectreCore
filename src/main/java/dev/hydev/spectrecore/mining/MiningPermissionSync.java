package dev.hydev.spectrecore.mining;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class MiningPermissionSync {
    private final JavaPlugin plugin;
    private final MiningProgression progression;
    private volatile boolean stopped;
    private boolean running;

    MiningPermissionSync(JavaPlugin plugin, MiningProgression progression) {
        this.plugin = plugin;
        this.progression = progression;
    }

    void stop() {
        stopped = true;
    }

    void start(CommandSender sender) {
        if (running) {
            sender.sendMessage("Ya hay una sincronización minera en curso.");
            return;
        }
        Plugin aura = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (aura == null || !aura.isEnabled() || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            sender.sendMessage("La sincronización requiere AuraSkills y LuckPerms activos.");
            return;
        }
        Map<String, Integer> milestones = new LinkedHashMap<>();
        for (String stage : MiningProgression.STAGES) {
            int required = progression.required(stage);
            if (required < 1) {
                sender.sendMessage("Falta el nivel del hito minero " + stage + ".");
                return;
            }
            milestones.put(progression.permission(stage), required);
        }
        var online = Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId()).toList();
        running = true;
        sender.sendMessage("Revisando los perfiles de Minería…");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                result = apply(aura, online, milestones);
            } catch (Exception | LinkageError error) {
                plugin.getLogger().log(Level.SEVERE, "Error al sincronizar los hitos mineros", error);
                result = "Sincronización interrumpida. Revisa la consola y vuelve a ejecutarla.";
            }
            String report = result;
            if (!stopped && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                running = false;
                sender.sendMessage(report);
                if (sender != Bukkit.getConsoleSender()) plugin.getLogger().info(report);
            });
        });
    }

    private String apply(Plugin aura, List<UUID> online, Map<String, Integer> milestones) throws Exception {
        var api = AuraSkillsApi.get();
        int defaultLevel = api.getMainConfig().getStartLevel();
        var levels = storedLevels(aura, defaultLevel);
        online.forEach(uuid -> levels.putIfAbsent(uuid, null));
        var users = LuckPermsProvider.get().getUserManager();
        int reviewed = 0, updated = 0, errors = 0;
        for (var entry : levels.entrySet()) {
            if (stopped || Thread.currentThread().isInterrupted()) break;
            UUID id = entry.getKey();
            try {
                var live = api.getUser(id);
                Integer level = entry.getValue();
                if (live.isLoaded()) level = live.getSkillLevel(Skills.MINING);
                if (level == null) throw new IllegalStateException("Perfil pendiente de carga");
                int current = level;
                AtomicBoolean changed = new AtomicBoolean();
                users.modifyUser(id, user -> {
                    for (var reward : milestones.entrySet()) {
                        if (current < reward.getValue()) continue;
                        String grantKey = reward.getKey();
                        // A direct denial (including contextual nodes) is deliberate.
                        // Do not turn it into an unconditional grant; also respect inherited denials.
                        if (user.data().toCollection().stream().anyMatch(node ->
                                node.getKey().equals(grantKey) && !node.getValue())) continue;
                        if (user.getCachedData().getPermissionData().checkPermission(grantKey)
                                == net.luckperms.api.util.Tristate.FALSE) continue;
                        Node grant = Node.builder(grantKey).build();
                        if (user.data().toCollection().contains(grant)) continue;
                        if (user.data().add(grant).wasSuccessful()) changed.set(true);
                    }
                }).join();
                reviewed++;
                if (changed.get()) updated++;
            } catch (RuntimeException error) {
                errors++;
                plugin.getLogger().log(Level.WARNING, "Error al aplicar permisos mineros a " + id, error);
            }
        }
        return "Minería · Revisados: " + reviewed + " · Actualizados: " + updated + " · Errores: " + errors;
    }

    // AuraSkills 2.3.12 exposes individual users through the API, but only its storage provider
    // enumerates all profiles (including SQL-only UUIDs). Read snapshots without logging users in.
    private Map<UUID, Integer> storedLevels(Plugin aura, int defaultLevel) throws ReflectiveOperationException {
        Object provider = aura.getClass().getMethod("getStorageProvider").invoke(aura);
        var states = (List<?>) provider.getClass().getMethod("loadStates", boolean.class, boolean.class)
                .invoke(provider, false, true);
        Map<UUID, Integer> levels = new HashMap<>();
        if (states.isEmpty()) return levels;
        var stateType = states.get(0).getClass();
        var uuid = stateType.getMethod("uuid");
        var skills = stateType.getMethod("skillLevels");
        for (Object state : states) {
            var values = (Map<?, ?>) skills.invoke(state);
            Integer mining = (Integer) values.get(Skills.MINING);
            levels.put((UUID) uuid.invoke(state), mining == null ? defaultLevel : mining);
        }
        return levels;
    }
}
