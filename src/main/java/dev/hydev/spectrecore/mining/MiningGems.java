package dev.hydev.spectrecore.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hydev.spectrecore.migration.MmoBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/** Reads actual socketed MMOItems identities. Only the highest rank in each branch applies. */
final class MiningGems implements Listener {
    private static final List<String> GRADES = List.of("I", "II", "III", "IV");
    private static final List<String> POOL = List.of("GEMA_VETA_I", "GEMA_MAGNETITA_I", "GEMA_FLUORITA_I", "GEMA_VOLCANITA");
    private final JavaPlugin plugin;
    private final MmoBridge mmo;
    private final MiningProgression progression;
    private final MithrilToolModule tools;
    private final Map<UUID, Long> haste = new HashMap<>();
    private record Magnet(UUID owner, int range) {}
    private final Map<Location, Magnet> pending = new HashMap<>();
    private Method wrap, read;
    private boolean warned;

    MiningGems(JavaPlugin plugin, MmoBridge mmo, MiningProgression progression, MithrilToolModule tools) {
        this.plugin = plugin;
        this.mmo = mmo;
        this.progression = progression;
        this.tools = tools;
    }

    static Map<String,Integer> ranks(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Map<String,Integer> ranks = new HashMap<>();
        var list = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("Gemstones");
        if (list == null) return ranks;
        for (var element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject gem = element.getAsJsonObject();
            if (!gem.has("Type") || !gem.has("Id") || !"GEM_STONE".equals(gem.get("Type").getAsString())) continue;
            String id = gem.get("Id").getAsString();
            if (id.equals("GEMA_VOLCANITA")) ranks.put("VOLCANITA",1);
            else if (id.startsWith("GEMA_") && id.lastIndexOf('_') > 4) {
                int split = id.lastIndexOf('_');
                int rank = GRADES.indexOf(id.substring(split+1)) + 1;
                if (rank > 0) ranks.merge(id.substring(5,split),rank,Math::max);
            }
        }
        return ranks;
    }

    private Map<String,Integer> socketed(ItemStack held) {
        try {
            if (read == null) {
                var lib = Bukkit.getPluginManager().getPlugin("MythicLib");
                if (lib == null) return Map.of();
                Class<?> nbt = Class.forName("io.lumine.mythic.lib.api.item.NBTItem",true,lib.getClass().getClassLoader());
                wrap = nbt.getMethod("get", ItemStack.class);
                read = nbt.getMethod("getString",String.class);
            }
            return ranks((String) read.invoke(wrap.invoke(null,held),"MMOITEMS_GEM_STONES"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) plugin.getLogger().warning("Gemas mineras: no se pudieron leer las ranuras: " + failure);
            warned = true;
            return Map.of();
        }
    }

    int mine(Player player, Block block, BiConsumer<Player, ItemStack> deliver) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!"TOOL".equals(mmo.type(held))) return 0;
        var random = ThreadLocalRandom.current();
        String tool = mmo.id(held);
        if (("PICO_MITRILO".equals(tool) || "PICO_MITRILO_TEMPLADO".equals(tool))
                && random.nextDouble() < .01 && progression.meets(player,"mitrilo")) {
            ItemStack gem = mmo.build("GEM_STONE", POOL.get(random.nextInt(POOL.size())),1);
            if (gem != null) {
                deliver.accept(player,gem);
                MiningDiscoveryEffects.gem(player, block.getLocation().add(.5, .5, .5), gem);
            }
        }
        var ranks = socketed(held);
        int magnet = ranks.getOrDefault("MAGNETITA",0);
        if (magnet > 0) {
            Location location = block.getLocation();
            Magnet effect = new Magnet(player.getUniqueId(),2 + 2 * magnet);
            pending.put(location,effect);
            Bukkit.getScheduler().runTask(plugin, () -> pending.remove(location,effect));
        }
        int fluorite = ranks.getOrDefault("FLUORITA",0);
        long now = System.currentTimeMillis();
        if (fluorite > 0 && now >= haste.getOrDefault(player.getUniqueId(),0L)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,20 * (1 + 2 * fluorite),0));
            haste.put(player.getUniqueId(),now + 12000);
        }
        if (ranks.containsKey("VOLCANITA") && random.nextDouble() < .03 && progression.meets(player,"arcana")) {
            var crystal = mmo.build("MATERIAL","AMATISTA_RESONANTE",1);
            if (crystal != null) {
                deliver.accept(player, crystal);
                MiningDiscoveryEffects.gemSparkles(player, block.getLocation().add(.5, .5, .5));
            }
        }
        int diamond = ranks.getOrDefault("DIAMANTE_LOCO",0);
        if (diamond > 0 && random.nextInt(100) < diamond && progression.meets(player,"arcana")) {
            var spot = block.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getWorld() == spot.getWorld()
                        && player.getLocation().distanceSquared(spot) <= 64) tools.revealOre(player,spot,tool);
            });
        }
        return ranks.getOrDefault("VETA",0);
    }

    @EventHandler(priority = EventPriority.MONITOR,ignoreCancelled = true)
    public void drops(BlockDropItemEvent event) {
        Magnet magnet = pending.remove(event.getBlock().getLocation());
        Player player = event.getPlayer();
        if (magnet == null || !magnet.owner().equals(player.getUniqueId())) return;
        for (var item : event.getItems()) {
            if (item.getLocation().distanceSquared(player.getLocation()) <= magnet.range() * magnet.range()) {
                item.teleport(player.getLocation());
            }
        }
    }

    void quit(UUID player) { haste.remove(player); }
    void clear() { haste.clear(); pending.clear(); }
}
