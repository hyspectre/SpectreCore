package dev.hydev.spectrecore.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Emitted only after a real MMOItems meal has been successfully granted. */
public final class CookingCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String recipeId;
    private final String quality;
    private final Location station;

    public CookingCompletedEvent(Player player, String recipeId, String quality, Location station) {
        this.player = player;
        this.recipeId = recipeId;
        this.quality = quality;
        this.station = station.clone();
    }

    public Player getPlayer() { return player; }
    public String getRecipeId() { return recipeId; }
    public String getQuality() { return quality; }
    public Location getStationLocation() { return station.clone(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
