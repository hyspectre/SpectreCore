package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.storage.ClaimJournal;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;

/**
 * A repeatable, consumable material order. Only main-thread inventory operations;
 * no crafted item, reward or currency is given until the exact original MMOItems
 * have been reserved in a durable journal and removed from inventory.
 */
public final class SupplyOrders {
    private final JavaPlugin plugin;
    private final JobsModule jobs;
    private final MmoBridge mmo = new MmoBridge();
    private final EconomyBridge economy = new EconomyBridge();
    private final ClaimJournal journal;

    public SupplyOrders(JavaPlugin plugin, JobsModule jobs, ClaimJournal journal) {
        this.plugin = plugin;
        this.jobs = jobs;
        this.journal = journal;
    }

    private YamlConfiguration config() {
        return dev.hydev.spectrecore.config.ModuleConfiguration.module(plugin, "trabajos/config.yml");
    }

    public int required() { return config().getInt("encargos.mitrilo.cantidad", 4); }
    public long payment() { return config().getLong("encargos.mitrilo.recompensa", 180); }
    public int minimumRank() { return config().getInt("encargos.mitrilo.rango-minimo", 2); }
    public String type() { return config().getString("encargos.mitrilo.tipo", "MATERIAL"); }
    public String itemId() { return config().getString("encargos.mitrilo.id", "MITRILO"); }

    public int count(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir() && matches(item)) total += item.getAmount();
        }
        return total;
    }

    private boolean matches(ItemStack item) {
        return item != null && !item.getType().isAir()
                && type().equalsIgnoreCase(mmo.type(item)) && itemId().equalsIgnoreCase(mmo.id(item));
    }

    public boolean fulfill(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Inventory orders require the server thread");
        if (!player.isOnline() || journal == null || !journal.ready()) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.no-disponible",
                    "<red>No se puede procesar este encargo. Contacta a administración."));
            return false;
        }
        int needed = required(), rank = minimumRank();
        long coins = payment();
        String id = itemId(), type = type();
        if (needed < 1 || needed > 64 || coins < 1 || coins > 1_000_000_000L
                || !type.matches("[A-Z_]{2,32}") || !id.matches("[A-Z0-9_]{2,64}")
                || mmo.build(type, id, 1) == null) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.no-disponible",
                    "<red>No se puede procesar este encargo. Contacta a administración."));
            return false;
        }
        if (jobs.rank(player, "minero") < rank) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.rango",
                    "<yellow>Debes mejorar tu contrato de minería."));
            return false;
        }
        if (jobs.isPending(player)) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.pendiente",
                    "<yellow>Tienes una transacción pendiente. Contacta a administración."));
            return false;
        }
        String station = "job:" + player.getUniqueId(); // Shared with rank purchases.
        if (journal.blocked(station)) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.pendiente",
                    "<yellow>Tienes una transacción pendiente. Contacta a administración."));
            return false;
        }
        // Storage only; do not consume armor, offhand, GUI icons or virtual items.
        ItemStack[] storage = player.getInventory().getStorageContents();
        int[] counts = new int[storage.length];
        for (int i = 0; i < storage.length; i++)
            counts[i] = matches(storage[i]) ? storage[i].getAmount() : 0;
        int available = Arrays.stream(counts).sum();
        if (!SupplyOrderRules.eligible(jobs.rank(player, "minero"), rank, available, needed)) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.insuficiente",
                    "<yellow>Necesitas {cantidad} Mitrilo para entregar el pedido.",
                    "cantidad", needed));
            return false;
        }
        int[] taken = SupplyOrderRules.allocation(counts, needed);
        if (taken.length != storage.length) return false;
        UUID token = journal.begin(station, player.getUniqueId(), "SUPPLY_DELIVERY",
                type + ":" + id + ":" + needed + ":" + coins, needed);
        if (token == null) {
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.no-disponible",
                    "<red>No se puede procesar este encargo. Contacta a administración."));
            return false;
        }
        // Inventory mutation is synchronous and confined to the transaction window.
        // A crash leaves the unresolved token for manual verification (never auto-replay).
        try {
            ItemStack[] next = new ItemStack[storage.length];
            for (int i = 0; i < storage.length; i++) {
                ItemStack original = storage[i];
                if (original == null || taken[i] == 0) { next[i] = original; continue; }
                int remaining = original.getAmount() - taken[i];
                if (remaining < 0) throw new IllegalStateException("Invalid allocation");
                if (remaining > 0) {
                    next[i] = original.clone();
                    next[i].setAmount(remaining);
                }
            }
            player.getInventory().setStorageContents(next);
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("ENCARGO PENDIENTE por error de inventario: " + token
                    + ", jugador=" + player.getUniqueId());
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.revision",
                    "<red>El pedido requiere revisión. Avisa a administración."));
            return false;
        }
        EconomyBridge.Result result = economy.deposit(player, coins, false);
        if (result != EconomyBridge.Result.CONFIRMED) {
            plugin.getLogger().severe("ENTREGA DE MITRILO sin pago confirmado: " + token
                    + ", jugador=" + player.getUniqueId() + ", unidades=" + needed + ", monedas=" + coins);
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.revision",
                    "<red>El pedido requiere revisión. Avisa a administración."));
            return false; // Never refund after an ambiguous economy response.
        }
        if (!journal.finish(token)) {
            plugin.getLogger().severe("ENTREGA DE MITRILO pagada pero no conciliada: " + token
                    + ", jugador=" + player.getUniqueId());
            player.sendMessage(Settings.message("trabajos", "SupplyOrders.pagado-pendiente",
                    "<yellow>Recibiste el pago; administración revisará el registro."));
            return false;
        }
        player.sendMessage(Settings.message("trabajos", "SupplyOrders.entregado",
                "<green>✦ Pedido entregado: <white>{cantidad} Mitrilo <gray>· <gold>{monedas} ↾",
                "cantidad", needed, "monedas", coins));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.25f);
        return true;
    }
}
