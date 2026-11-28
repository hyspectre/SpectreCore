package dev.hydev.spectrecore.mining;

import dev.hydev.spectrecore.migration.MmoBridge;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/** Charge and cooldown travel with the physical MMOItems item. */
public final class MithrilDeviceData {
    public static final String PICK = "PICO_MITRILO";
    public static final String TEMPERED = "PICO_MITRILO_TEMPLADO";
    public static final String AMETHYST = "PICO_AMATISTA";
    // Stable MMOItems ID: existing Faroles become Linternas without losing the item.
    public static final String LANTERN = "FAROL_MITRILO";
    public static final String DRILL = "BARRENA_MITRILO";
    public static final String CHARGE = "CARGA_RESONANTE";
    private static final Set<String> TOOLS = Set.of(PICK, TEMPERED, AMETHYST, LANTERN, DRILL);
    private final MmoBridge mmo = new MmoBridge();
    private final NamespacedKey charge, cooldown, active, serial;

    public MithrilDeviceData(JavaPlugin plugin) {
        charge = new NamespacedKey(plugin, "mitrilo_resonancia");
        cooldown = new NamespacedKey(plugin, "mitrilo_recarga_hasta");
        active = new NamespacedKey(plugin, "mitrilo_activo_hasta");
        serial = new NamespacedKey(plugin, "mitrilo_serial");
    }

    public String id(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !"TOOL".equalsIgnoreCase(mmo.type(stack))) return "";
        String id = mmo.id(stack);
        return TOOLS.contains(id) ? id : "";
    }

    public boolean crystal(ItemStack stack) {
        return stack != null && !stack.getType().isAir()
                && "MATERIAL".equalsIgnoreCase(mmo.type(stack))
                && (CHARGE.equals(mmo.id(stack)) || "CRISTAL_MITRILO".equals(mmo.id(stack)));
    }

    public String serial(ItemStack stack) { return string(stack, serial); }
    public ItemStack identify(ItemStack stack) {
        if (id(stack).isEmpty() || !serial(stack).isEmpty()) return stack;
        return modify(stack, data -> data.set(serial, PersistentDataType.STRING,
                java.util.UUID.randomUUID().toString()));
    }

    public int energy(ItemStack stack) {
        PersistentDataContainer data = read(stack);
        return data == null ? 0 : Math.max(0, data.getOrDefault(charge, PersistentDataType.INTEGER, 0));
    }

    public long cooldown(ItemStack stack) { return number(stack, cooldown); }
    public long activeUntil(ItemStack stack) { return number(stack, active); }
    private long number(ItemStack stack, NamespacedKey key) {
        PersistentDataContainer data = read(stack);
        if (data == null) return 0;
        return data.getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private String string(ItemStack stack, NamespacedKey key) {
        PersistentDataContainer data = read(stack);
        return data == null ? "" : data.getOrDefault(key, PersistentDataType.STRING, "");
    }

    private PersistentDataContainer read(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer();
    }

    public ItemStack setCharge(ItemStack stack, int points, int capacity) {
        return modify(stack, data -> data.set(charge, PersistentDataType.INTEGER,
                MithrilRules.charge(energy(stack), points, capacity)));
    }

    public ItemStack changeCharge(ItemStack stack, int delta, int capacity) {
        return setCharge(stack, delta, capacity);
    }

    public ItemStack cooldown(ItemStack stack, long until) {
        return modify(stack, data -> data.set(cooldown, PersistentDataType.LONG, until));
    }
    public ItemStack active(ItemStack stack, long until) {
        return modify(stack, data -> data.set(active, PersistentDataType.LONG, until));
    }
    /** A template lore/revision update must not reset a physical tool's progression. */
    public ItemStack preserveRevision(ItemStack original, ItemStack result) {
        String sourceId = id(original);
        if (sourceId.isEmpty() || !sourceId.equals(id(result))) return result;
        var old = read(original);
        if (old == null) return result;
        return modify(result, updated -> {
            copy(old, updated, charge, PersistentDataType.INTEGER);
            copy(old, updated, cooldown, PersistentDataType.LONG);
            copy(old, updated, active, PersistentDataType.LONG);
            copy(old, updated, serial, PersistentDataType.STRING);
        });
    }

    private static <T, Z> void copy(PersistentDataContainer from, PersistentDataContainer to,
                                    NamespacedKey key, PersistentDataType<T,Z> type) {
        Z value = from.get(key, type);
        if (value != null) to.set(key, type, value);
    }

    private ItemStack modify(ItemStack original, java.util.function.Consumer<PersistentDataContainer> change) {
        if (id(original).isEmpty()) return original;
        ItemStack stack = original.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return original;
        change.accept(meta.getPersistentDataContainer());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack held(Player player, EquipmentSlot hand) {
        return hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
    }
    public void update(Player player, EquipmentSlot hand, ItemStack stack) {
        if (hand == EquipmentSlot.OFF_HAND) player.getInventory().setItemInOffHand(stack);
        else player.getInventory().setItemInMainHand(stack);
    }
}
