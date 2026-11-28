package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.config.Settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.crafting.ingredient.CheckedIngredient;
import net.Indyuce.mmoitems.api.event.PlayerUseCraftingStationEvent;
import net.Indyuce.mmoitems.api.event.MMOItemReforgeFinishEvent;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.GemSocketsData;
import net.Indyuce.mmoitems.stat.data.GemstoneData;
import net.Indyuce.mmoitems.stat.type.StatHistory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Optional MMOItems listener. The station owns payment, output delivery and inventory overflow. */
final class MiningForgeUpgrades implements Listener {
    private record Upgrade(String source, String target) {}
    private static final Map<String, Upgrade> UPGRADES = Map.of(
            "taladro-reforzado", new Upgrade("TALADRO", "TALADRO_REFORZADO"),
            "taladro-calibrado", new Upgrade("TALADRO_REFORZADO", "TALADRO_CALIBRADO"),
            "pico-mitrilo-templado", new Upgrade("PICO_MITRILO", "PICO_MITRILO_TEMPLADO"));
    private final JavaPlugin plugin;
    private final MithrilDeviceData deviceData;

    MiningForgeUpgrades(JavaPlugin plugin) {
        this.plugin = plugin;
        deviceData = new MithrilDeviceData(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void revision(MMOItemReforgeFinishEvent event) {
        ItemStack original = event.getReforger().getStack();
        if (deviceData.id(original).isEmpty()) return;
        try {
            event.setFinishedItem(deviceData.preserveRevision(original, event.getFinishedItem()));
        } catch (RuntimeException | LinkageError failure) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.WARNING, "No se pudo conservar la resonancia al actualizar una herramienta.", failure);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(PlayerUseCraftingStationEvent event) {
        Upgrade upgrade = UPGRADES.get(event.getRecipe().getId());
        if (upgrade == null) return;
        var action = event.getInteraction();
        // A legacy queue entry has already been paid for. Never grant items again on claim/cancel.
        if (action != PlayerUseCraftingStationEvent.StationAction.INSTANT_RECIPE
                && action != PlayerUseCraftingStationEvent.StationAction.INTERACT_WITH_RECIPE) return;
        boolean dedicated = "forja-minera".equals(event.getStation().getId());
        try {
            ItemStack source = selectedTool(event, upgrade.source());
            if (source == null && !dedicated) return;
            if (source == null) throw new IllegalStateException("Falta la herramienta de origen");
            var input = new LiveMMOItem(source.clone());
            Map<UUID, JsonObject> installed = socketRecords(input);
            if (action == PlayerUseCraftingStationEvent.StationAction.INTERACT_WITH_RECIPE) {
                if (!installed.isEmpty() || dedicated) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Settings.message("resonancia", "MiningModule.mensaje-050", "§eMejora esta herramienta en la Forja Minera actualizada."));
                }
                return;
            }
            ItemStack result = event.getResult();
            if (result == null || !"TOOL".equals(MMOItems.getTypeName(result))
                    || !upgrade.target().equals(MMOItems.getID(result)) || result.getAmount() != 1) {
                throw new IllegalStateException("Destino de mejora inesperado");
            }
            ItemStack rebuilt = transfer(input, installed, result);
            if (rebuilt != null && !result.setItemMeta(rebuilt.getItemMeta()))
                throw new IllegalStateException("No se pudo preparar el resultado");
        } catch (RuntimeException | LinkageError failure) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Settings.message("resonancia", "MiningModule.forja-error-ranuras", "<red>No se pudo conservar la configuración de ranuras. Conservas los ingredientes."));
            plugin.getLogger().log(Level.WARNING,"Mejora minera rechazada: " + event.getRecipe().getId(),failure);
        }
    }

    // CheckedIngredient.takeAway consumes the first nonempty stack in getFound(), in this order.
    private static ItemStack selectedTool(PlayerUseCraftingStationEvent event, String id) {
        for (CheckedIngredient ingredient : event.getRecipeInfo().getIngredients()) {
            for (var candidate : ingredient.getFound()) {
                ItemStack item = candidate.getItem();
                if (item.getAmount() <= 0) continue;
                if (id.equals(MMOItems.getID(item)) && "TOOL".equals(MMOItems.getTypeName(item))) {
                    if (ingredient.getIngredient().getAmount() != 1 || item.getAmount() != 1)
                        throw new IllegalStateException("Se requiere una herramienta individual");
                    return item;
                }
                break;
            }
        }
        return null;
    }

    private static ItemStack transfer(LiveMMOItem input, Map<UUID, JsonObject> installed, ItemStack result) {
        var output = new LiveMMOItem(result.clone());
        var target = (GemSocketsData) output.getData(ItemStats.GEM_SOCKETS);
        var source = (GemSocketsData) input.getData(ItemStats.GEM_SOCKETS);
        int freeBefore = source == null ? 0 : source.getEmptySlots().size();
        if (target == null || !target.getGems().isEmpty()
                || target.getEmptySlots().size() <= installed.size() + freeBefore)
            throw new IllegalStateException("La mejora debe añadir una ranura libre");
        int expectedFree = target.getEmptySlots().size() - installed.size();
        if (installed.isEmpty()) return null;
        var sockets = output.computeStatHistory(ItemStats.GEM_SOCKETS);
        var original = (GemSocketsData) sockets.getOriginalData();
        for (GemstoneData gem : input.getGemstones()) {
            String color = gem.getSocketColor();
            if (color == null || !installed.containsKey(gem.getHistoricUUID()) || !original.apply(color, gem))
                throw new IllegalStateException("Gema incompatible con la nueva herramienta");
        }
        // Rebuild sockets first. MMOItems discards stat histories with no corresponding socket UUID.
        output.setData(ItemStats.GEM_SOCKETS, sockets.recalculate(output.getUpgradeLevel()));
        for (StatHistory history : input.getStatHistories()) {
            for (UUID id : installed.keySet()) {
                var contribution = history.getGemstoneData(id);
                if (contribution != null) output.mergeData(history.getItemStat(), contribution, id);
            }
        }
        ItemStack rebuilt = output.newBuilder().build();
        var check = new LiveMMOItem(rebuilt);
        var finalSockets = (GemSocketsData) check.getData(ItemStats.GEM_SOCKETS);
        if (finalSockets == null || finalSockets.getEmptySlots().size() != expectedFree
                || !installed.equals(socketRecords(check))
                || !gemHistory(input, installed).equals(gemHistory(check, installed)))
            throw new IllegalStateException("La verificación de las gemas falló");
        return rebuilt;
    }

    // Compare only the per-gem contributions. The target's base stats and upgrades are its own.
    private static Map<String, Map<UUID, JsonElement>> gemHistory(LiveMMOItem item, Map<UUID, JsonObject> gems) {
        var nbt = item.getNBT();
        Map<String, Map<UUID, JsonElement>> result = new HashMap<>();
        for (String key : nbt.getTags()) {
            if (!key.startsWith("HSTRY_")) continue;
            var history = JsonParser.parseString(nbt.getString(key)).getAsJsonObject();
            if (!history.has("Gemstory")) continue;
            for (var record : history.getAsJsonArray("Gemstory")) {
                for (var entry : record.getAsJsonObject().entrySet()) {
                    UUID id = UUID.fromString(entry.getKey());
                    if (gems.containsKey(id)) result.computeIfAbsent(key, unused -> new HashMap<>()).put(id, entry.getValue());
                }
            }
        }
        return result;
    }

    // Compare serialized identities (including grade, color and historic UUID). Old or malformed
    // socket data must fail closed even when MMOItems silently skips it while loading the item.
    private static Map<UUID, JsonObject> socketRecords(LiveMMOItem item) {
        var nbt = item.getNBT();
        if (!nbt.hasTag("MMOITEMS_GEM_STONES")) return Map.of();
        var raw = JsonParser.parseString(nbt.getString("MMOITEMS_GEM_STONES")).getAsJsonObject();
        JsonArray gems = raw.getAsJsonArray("Gemstones");
        Map<UUID, JsonObject> result = new HashMap<>();
        for (var entry : gems) {
            var gem = entry.getAsJsonObject();
            if (!gem.has("Id") || !gem.has("Type")) throw new IllegalStateException("Gema sin identidad");
            UUID id = UUID.fromString(gem.get("History").getAsString());
            if (result.put(id,gem) != null) throw new IllegalStateException("Identidad de gema duplicada");
        }
        if (item.getGemstones().size() != result.size()) throw new IllegalStateException("Ranuras ilegibles");
        return result;
    }
}
