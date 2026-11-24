package dev.hydev.spectrecore.systems;

import dev.hydev.spectrecore.SpectreCorePlugin;
import dev.hydev.spectrecore.config.ModuleConfiguration;
import dev.hydev.spectrecore.config.Settings;
import dev.hydev.spectrecore.migration.MmoBridge;
import dev.hydev.spectrecore.storage.ClaimJournal;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Native 64:1 processing. The existing MMOItems MATERIAL catalog is the only
 * source of enchanted mineral IDs; no station, hidden permission or duplicate item.
 * All inventory mutations stay on the primary thread and behind a durable journal.
 */
public final class MinerWorkshop {
    public static final List<String> KEYS = List.of("cobre", "hierro", "oro", "lapis", "redstone", "diamante");
    public enum Mode { COMPACTAR, PULVERIZAR }
    private final JavaPlugin plugin;
    private final JobsModule jobs;
    private final ClaimJournal journal;
    private final MmoBridge mmo = new MmoBridge();
    private final EconomyBridge economy = new EconomyBridge();

    public MinerWorkshop(JavaPlugin plugin, JobsModule jobs, ClaimJournal journal) {
        this.plugin = plugin;
        this.jobs = jobs;
        this.journal = journal;
    }
    private YamlConfiguration config() {
        return ModuleConfiguration.module(plugin, "trabajos/config.yml");
    }
    public int level(Player player) {
        if (!(plugin instanceof SpectreCorePlugin core) || core.auraSkillsBridge() == null) return -1;
        return core.auraSkillsBridge().miningLevel(player);
    }
    public int minimumLevel() { return config().getInt("taller-minero.nivel-minimo", 3); }
    public int perBatch() { return config().getInt("taller-minero.compactar-unidades", 64); }
    public int cap() { return config().getInt("taller-minero.max-operaciones", 8); }
    public String display(String key) {
        return config().getString("taller-minero.recetas." + key + ".nombre", key);
    }
    public String id(String key) { return config().getString("taller-minero.recetas." + key + ".resultado-id", ""); }
    public long payment(String key) { return config().getLong("taller-minero.recetas." + key + ".pago", -1); }
    public Material raw(String key) {
        return Material.matchMaterial(config().getString("taller-minero.recetas." + key + ".origen", ""));
    }
    private boolean rawMatches(ItemStack stack, Material expected) {
        // Item metadata may conceal another plugin's or MMOItems custom material.
        return stack != null && stack.getType() == expected && stack.getAmount() > 0 && !stack.hasItemMeta();
    }
    private boolean enchantedMatches(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0
                && "MATERIAL".equalsIgnoreCase(mmo.type(stack)) && id.equalsIgnoreCase(mmo.id(stack));
    }
    public int available(Player player, String key, Mode mode) {
        if (!KEYS.contains(key)) return 0;
        Material origin = raw(key);
        if (origin == null) return 0;
        int amount = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (mode == Mode.COMPACTAR ? rawMatches(stack, origin) : enchantedMatches(stack, id(key))) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }
    public int possible(Player player, String key, Mode mode) {
        int unit = mode == Mode.COMPACTAR ? perBatch() : 1;
        return MinerWorkshopRules.batches(available(player, key, mode), unit, cap());
    }
    public boolean availableFor(Player player) {
        int level = level(player);
        return level >= 0 && level >= minimumLevel();
    }
    public boolean ready() { return journal != null && journal.ready(); }

    /**
     * Returns a safely constructed post-consumption inventory, or null when the
     * input is insufficient or output cannot fit. Does not touch live inventory.
     */
    private ItemStack[] projected(ItemStack[] original, String key, Mode mode, int batches,
                                  ItemStack result) {
        Material raw = raw(key);
        String id = id(key);
        int quantity = mode == Mode.COMPACTAR ? Math.multiplyExact(batches, perBatch()) : batches;
        ItemStack[] next = new ItemStack[original.length];
        int remaining = quantity;
        for (int i = 0; i < original.length; i++) {
            ItemStack item = original[i];
            if (item == null || item.getType().isAir()) { next[i] = item; continue; }
            boolean matches = mode == Mode.COMPACTAR ? rawMatches(item, raw) : enchantedMatches(item, id);
            int use = matches ? Math.min(remaining, item.getAmount()) : 0;
            remaining -= use;
            if (use == item.getAmount()) next[i] = null;
            else {
                next[i] = item.clone();
                next[i].setAmount(item.getAmount() - use);
            }
        }
        if (remaining != 0) return null;
        if (mode == Mode.PULVERIZAR) return next;

        int toInsert = batches;
        int maximum = Math.max(1, Math.min(64, result.getMaxStackSize()));
        // Stack only on genuinely identical MMOItems. Never replace other contents.
        for (int i = 0; i < next.length && toInsert > 0; i++) {
            ItemStack item = next[i];
            if (item == null || !item.isSimilar(result)) continue;
            int room = maximum - item.getAmount();
            if (room <= 0) continue;
            int add = Math.min(room, toInsert);
            next[i] = item.clone();
            next[i].setAmount(item.getAmount() + add);
            toInsert -= add;
        }
        for (int i = 0; i < next.length && toInsert > 0; i++) {
            if (next[i] != null && !next[i].getType().isAir()) continue;
            int add = Math.min(maximum, toInsert);
            next[i] = result.clone();
            next[i].setAmount(add);
            toInsert -= add;
        }
        return toInsert == 0 ? next : null;
    }

    public boolean process(Player player, String key, Mode mode, int requested) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Taller Minero: se requiere hilo principal");
        if (player == null || !player.isOnline() || !KEYS.contains(key) || !ready()) {
            if (player != null) unavailable(player);
            return false;
        }
        int level = level(player);
        if (level < 0) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.sin-nivel",
                    "<yellow>No se pudo consultar tu progreso de Minería. Inténtalo más tarde."));
            return false;
        }
        if (level < minimumLevel()) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.nivel",
                    "<yellow>Necesitas Minería nivel {nivel} para usar el taller.",
                    "nivel", minimumLevel()));
            return false;
        }
        if (jobs.isPending(player) || journal.blocked("job:" + player.getUniqueId())) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.pendiente",
                    "<yellow>Hay una operación pendiente de revisión. Avisa a administración."));
            return false;
        }
        Material raw = raw(key);
        String id = id(key);
        if (raw == null || !raw.isItem() || !id.matches("[A-Z][A-Z0-9_]{1,63}")
                || perBatch() < 2 || perBatch() > 64 || cap() < 1 || cap() > 16) {
            unavailable(player); return false;
        }
        ItemStack built = mmo.build("MATERIAL", id, 1);
        if (built == null) { unavailable(player); return false; }
        int sourceCount = available(player, key, mode);
        int unit = mode == Mode.COMPACTAR ? perBatch() : 1;
        int batches = MinerWorkshopRules.selected(sourceCount, unit, cap(), requested);
        if (!MinerWorkshopRules.ready(level, minimumLevel(), batches, cap())) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.insuficiente",
                    "<yellow>No tienes suficientes materiales para esta operación."));
            return false;
        }
        long money = 0;
        if (mode == Mode.PULVERIZAR) {
            try { money = MinerWorkshopRules.payout(batches, payment(key)); }
            catch (IllegalArgumentException | ArithmeticException invalid) { unavailable(player); return false; }
            // Fail before removing inventory if Vault is definitely absent.
            if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) { unavailable(player); return false; }
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] projected = projected(storage, key, mode, batches, built);
        if (projected == null) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.espacio",
                    "<yellow>No hay espacio suficiente o faltan materiales en tu mochila."));
            return false;
        }
        String station = "job:" + player.getUniqueId();
        UUID token = journal.begin(station, player.getUniqueId(), "MINER_WORKSHOP",
                mode.name() + ":" + key + ":" + batches + ":" + money, batches);
        if (token == null) { unavailable(player); return false; }
        try {
            player.getInventory().setStorageContents(projected);
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("TALLER MINERO: inventario ambiguo, caso=" + token
                    + ", jugador=" + player.getUniqueId());
            review(player);
            return false;
        }
        if (mode == Mode.PULVERIZAR && economy.deposit(player, money, false) != EconomyBridge.Result.CONFIRMED) {
            plugin.getLogger().severe("TALLER MINERO: pago pendiente, caso=" + token
                    + ", jugador=" + player.getUniqueId() + ", monedas=" + money);
            review(player);
            return false;
        }
        if (!journal.finish(token)) {
            plugin.getLogger().severe("TALLER MINERO: no se cerró transacción=" + token
                    + ", jugador=" + player.getUniqueId());
            review(player);
            return false;
        }
        if (mode == Mode.COMPACTAR) {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.compactado",
                    "<green>✦ Has compactado <white>{cantidad} {mineral} <green>hechizado(s).",
                    "cantidad", batches, "mineral", display(key)));
        } else {
            player.sendMessage(Settings.message("trabajos", "MinerWorkshop.pulverizado",
                    "<gold>✦ Has pulverizado <white>{cantidad} {mineral} <gold>por {dinero} monedas.",
                    "cantidad", batches, "mineral", display(key), "dinero", money));
        }
        Sound sound = mode == Mode.COMPACTAR
                ? Settings.sound("trabajos", "MinerWorkshop.sonido-compactar", Sound.BLOCK_ANVIL_USE)
                : Settings.sound("trabajos", "MinerWorkshop.sonido-pulverizar", Sound.BLOCK_GRINDSTONE_USE);
        player.playSound(player.getLocation(), sound, 0.6f, mode == Mode.COMPACTAR ? 1.25f : 1.45f);
        player.spawnParticle(Settings.particle("trabajos", "MinerWorkshop.particula", org.bukkit.Particle.ENCHANT),
                player.getLocation().add(0, 1.1, 0),
                Settings.i("trabajos", "MinerWorkshop.particulas", 12), .32, .35, .32, .02);
        player.sendActionBar(Settings.message("trabajos", "MinerWorkshop.resultado-breve",
                "<gold>✦ Mineralurgia completada."));
        return true;
    }
    private void unavailable(Player player) {
        player.sendMessage(Settings.message("trabajos", "MinerWorkshop.no-disponible",
                "<red>El taller no puede procesar esta operación. Avisa a administración."));
    }
    private void review(Player player) {
        player.sendMessage(Settings.message("trabajos", "MinerWorkshop.revision",
                "<red>La operación requiere revisión. Avisa a administración antes de repetirla."));
    }
}
