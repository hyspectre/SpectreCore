package dev.hydev.spectrecore.relicario;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.trait.TraitModifier;
import dev.aurelium.auraskills.api.trait.Traits;
import dev.aurelium.auraskills.api.user.SkillsUser;
import dev.aurelium.auraskills.api.util.AuraSkillsModifier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Single application point for actual accessory stats. No additive per-login counters. */
public final class RelicarioStats implements Listener {
    private static final String MANA_KEY = "spectrecore_relicario_max_mana";
    private static final Set<String> UNDEAD = Set.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER",
        "ZOMBIFIED_PIGLIN", "ZOGLIN", "SKELETON", "STRAY", "BOGGED", "WITHER_SKELETON",
        "WITHER", "PHANTOM", "SKELETON_HORSE", "ZOMBIE_HORSE");
    private static final Map<Material, Material> CROPS = Map.of(
        Material.WHEAT, Material.WHEAT, Material.CARROTS, Material.CARROT,
        Material.POTATOES, Material.POTATO, Material.BEETROOTS, Material.BEETROOT);
    private record Igniter(UUID player, long expiresAt) { }

    private final JavaPlugin plugin;
    private final RelicarioModule module;
    private final Map<UUID, Map<String, Double>> totals = new HashMap<>();
    private final Map<UUID, Igniter> fireSources = new HashMap<>();
    private boolean warnedMana;

    public RelicarioStats(JavaPlugin plugin, RelicarioModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public double amount(UUID uuid, String key) {
        return totals.getOrDefault(uuid, Map.of()).getOrDefault(key, 0.0);
    }

    public Map<String, Double> totals(UUID uuid) {
        return totals.getOrDefault(uuid, Map.of());
    }

    public boolean manaAvailable(Player player) {
        try {
            if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null
                    || !Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) return false;
            SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
            return user != null && user.isLoaded() && Traits.MAX_MANA.isEnabled();
        } catch (RuntimeException | LinkageError error) { return false; }
    }

    /** Replay from the UUID ledger, replacing only modifiers owned by this module. */
    public void refresh(Player player) {
        if (player == null || !player.isOnline() || !module.ready(player.getUniqueId())) return;
        Map<String, Double> next = module.calculate(player.getUniqueId());
        totals.put(player.getUniqueId(), next);
        nativeStat(player, Attribute.MAX_HEALTH, "relicario_health", amount(player.getUniqueId(), "health"),
                AttributeModifier.Operation.ADD_NUMBER);
        nativeStat(player, Attribute.MOVEMENT_SPEED, "relicario_move",
                amount(player.getUniqueId(), "movement_speed") / 100.0,
                AttributeModifier.Operation.ADD_SCALAR);
        nativeStat(player, Attribute.BLOCK_BREAK_SPEED, "relicario_mine",
                amount(player.getUniqueId(), "mining_speed") / 100.0,
                AttributeModifier.Operation.ADD_SCALAR);
        applyMana(player);
    }

    private void nativeStat(Player player, Attribute attribute, String key, double value,
                            AttributeModifier.Operation mode) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey owned = new NamespacedKey(plugin, key);
        AttributeModifier previous = instance.getModifier(owned);
        if (previous != null && previous.getAmount() == value && previous.getOperation() == mode) return;
        if (previous != null) instance.removeModifier(previous);
        if (value > 0) instance.addTransientModifier(new AttributeModifier(owned, value, mode));
    }

    /** AuraSkills trait modifier is non-persistent and is rebuilt from the saved ledger. */
    private void applyMana(Player player) {
        if (!manaAvailable(player)) return;
        try {
            SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
            double value = amount(player.getUniqueId(), "mana");
            TraitModifier previous = user.getTraitModifier(MANA_KEY);
            if (previous != null && Math.abs(previous.value() - value) < 0.00001) return;
            if (previous != null) user.removeTraitModifier(MANA_KEY);
            if (value > 0) {
                TraitModifier modifier = new TraitModifier(MANA_KEY, Traits.MAX_MANA, value,
                        AuraSkillsModifier.Operation.ADD);
                modifier.setNonPersistent();
                user.addTraitModifier(modifier);
            }
        } catch (RuntimeException | LinkageError error) {
            if (!warnedMana) {
                warnedMana = true;
                plugin.getLogger().warning("Relicario: no se pudo aplicar mana de AuraSkills: " + error.getMessage());
            }
        }
    }

    public void cleanup(Player player) {
        if (player == null) return;
        nativeStat(player, Attribute.MAX_HEALTH, "relicario_health", 0, AttributeModifier.Operation.ADD_NUMBER);
        nativeStat(player, Attribute.MOVEMENT_SPEED, "relicario_move", 0, AttributeModifier.Operation.ADD_SCALAR);
        nativeStat(player, Attribute.BLOCK_BREAK_SPEED, "relicario_mine", 0, AttributeModifier.Operation.ADD_SCALAR);
        try {
            if (manaAvailable(player)) {
                SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
                if (user.getTraitModifier(MANA_KEY) != null) user.removeTraitModifier(MANA_KEY);
            }
        } catch (RuntimeException | LinkageError ignored) { }
        totals.remove(player.getUniqueId());
    }

    public void cleanupAll() {
        for (Player player : Bukkit.getOnlinePlayers()) cleanup(player);
        totals.clear();
        fireSources.clear();
    }

    private static boolean fire(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE ||
               cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
               cause == EntityDamageEvent.DamageCause.LAVA ||
               cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void incoming(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || event.getDamage() <= 0) return;
        double reduction = amount(victim.getUniqueId(), "damage_reduction");
        if (fire(event.getCause())) reduction += amount(victim.getUniqueId(), "fire_reduction");
        if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE)
            reduction += amount(victim.getUniqueId(), "projectile_reduction");
        if (reduction > 0) event.setDamage(event.getDamage() * (1.0 - Math.min(80.0, reduction) / 100.0));
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void outgoing(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) ||
                !(event.getEntity() instanceof LivingEntity enemy) || event.getDamage() <= 0) return;
        double bonus = 0;
        var kind = event.getCause();
        if (kind == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                kind == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            bonus += amount(player.getUniqueId(), "melee_damage");
            if (ThreadLocalRandom.current().nextDouble(100.0) <
                    amount(player.getUniqueId(), "critical_chance"))
                bonus += Math.max(0, Math.min(100, plugin instanceof
                        dev.hydev.spectrecore.SpectreCorePlugin core
                        ? core.moduleConfigs().read("accesorios/config.yml").getDouble(
                                "effects.critical-hit-bonus-percent", 50.0) : 50.0));
        } else if (kind == EntityDamageEvent.DamageCause.MAGIC) {
            bonus += amount(player.getUniqueId(), "magic_damage");
        }
        if (enemy instanceof Monster) bonus += amount(player.getUniqueId(), "hostile_damage");
        if (UNDEAD.contains(enemy.getType().name())) bonus += amount(player.getUniqueId(), "undead_damage");
        if (bonus > 0) event.setDamage(event.getDamage() * (1.0 + bonus / 100.0));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void ignited(EntityCombustByEntityEvent event) {
        if (event.getCombuster() instanceof Player player && event.getEntity() instanceof LivingEntity)
            fireSources.put(event.getEntity().getUniqueId(),
                new Igniter(player.getUniqueId(), System.currentTimeMillis()
                    + 1000L * Math.max(1, Math.min(60,
                        ((dev.hydev.spectrecore.SpectreCorePlugin) plugin).moduleConfigs()
                            .read("accesorios/config.yml").getInt("effects.fire-credit-seconds", 15)))));
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void fireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || mob instanceof Player ||
                !fire(event.getCause()) || event.getDamage() <= 0) return;
        Igniter igniter = fireSources.get(mob.getUniqueId());
        if (igniter == null) return;
        if (igniter.expiresAt() < System.currentTimeMillis()) {
            fireSources.remove(mob.getUniqueId()); return;
        }
        double bonus = amount(igniter.player(), "fire_damage");
        if (bonus > 0) event.setDamage(event.getDamage() * (1.0 + bonus / 100.0));
    }

    /** Crops are already broken, protected and drop-permitted at this event. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void cropBonus(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        double chance = amount(player.getUniqueId(), "crop_bonus");
        if (chance <= 0 || event.getItems().isEmpty() ||
                ThreadLocalRandom.current().nextDouble(100) >= chance) return;
        Material drop = CROPS.get(event.getBlockState().getType());
        if (drop == null || !(event.getBlockState().getBlockData() instanceof Ageable age)
                || age.getAge() < age.getMaximumAge()) return;
        var stack = new ItemStack(drop, 1);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(.5,.2,.5), stack);
    }

    /** Extend only new cooking effects, never preexisting potion effects. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void food(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        double bonus = amount(player.getUniqueId(), "food_duration");
        if (bonus <= 0 || !module.isCookingItem(event.getItem())) return;
        Map<PotionEffectType, Integer> prior = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects())
            prior.put(effect.getType(), effect.getDuration());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !module.ready(player.getUniqueId())) return;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getDuration() <= prior.getOrDefault(effect.getType(), 0)) continue;
                int extended = (int) Math.min(2_000_000, Math.ceil(effect.getDuration() * (1 + bonus / 100.0)));
                player.addPotionEffect(new PotionEffect(effect.getType(), extended, effect.getAmplifier(),
                        effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
            }
        }, 2L);
    }

    public long salary(Player player, long original) {
        double extra = amount(player.getUniqueId(), "contract_coins");
        return Math.max(original, Math.round(original * (1.0 + extra / 100.0)));
    }

    public double cookingSpeed(Player player) {
        return player == null ? 0 : amount(player.getUniqueId(), "cooking_speed");
    }
}
