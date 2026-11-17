package dev.hydev.spectrecore.model;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Supports custom GameProfile Base64 skulls as items or ITEM_DISPLAY pieces. */
public final class CustomHeads {
    private static final Map<String, ItemStack> CACHE = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, ItemStack> eldest) { return size() > 128; }
    };
    private CustomHeads() { }
    public static synchronized ItemStack fromBase64(String encoded) {
        String payload = TexturePayload.checked(encoded);
        ItemStack old = CACHE.get(payload);
        if (old != null) return old.clone();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        UUID stable = UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfileExact(stable, null);
        profile.setProperty(new ProfileProperty("textures", payload));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        CACHE.put(payload, item.clone());
        return item;
    }
}
