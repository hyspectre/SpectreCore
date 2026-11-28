package dev.hydev.spectrecore.systems;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import dev.hydev.spectrecore.config.Settings;

public final class EconomyBridge {
    public enum Result { CONFIRMED, COMMAND_SENT, FAILED }
    /**
     * Confirmed Vault withdrawal only. Never run a console economy command for player contracts:
     * a dispatch "success" cannot prove the money was actually deducted.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result withdraw(Player player, long amount) {
        if (player == null || amount < 0 || amount > 1_000_000_000L) return Result.FAILED;
        if (amount == 0) return Result.CONFIRMED;
        var vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return Result.FAILED;
        try {
            Class<?> api = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            var registration = Bukkit.getServicesManager().getRegistration((Class) api);
            if (registration == null) return Result.FAILED;
            Object provider = registration.getProvider();
            Object has = api.getMethod("has", OfflinePlayer.class, double.class).invoke(provider, player, (double) amount);
            if (!Boolean.TRUE.equals(has)) return Result.FAILED;
            Object response = api.getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                    .invoke(provider, player, (double) amount);
            return Boolean.TRUE.equals(response.getClass().getMethod("transactionSuccess").invoke(response))
                    ? Result.CONFIRMED : Result.FAILED;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            return Result.FAILED;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result deposit(Player player, long amount, boolean fallback) {
        var vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault != null && vault.isEnabled()) try {
            Class<?> api = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            var registration = Bukkit.getServicesManager().getRegistration((Class) api);
            if (registration != null) {
                Object response = api.getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(registration.getProvider(), player, (double) amount);
                return Boolean.TRUE.equals(response.getClass().getMethod("transactionSuccess").invoke(response)) ? Result.CONFIRMED : Result.FAILED;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { return Result.FAILED; }
        String command=Settings.string("nucleo","EconomyBridge.comando-deposito","eco give {jugador} {cantidad}").replace("{jugador}",player.getName()).replace("{cantidad}",Long.toString(amount));
        if (!fallback || command.isBlank()) return Result.FAILED;
        // Controlled fallback: never run arbitrary privileged commands from YAML.
        String[] words = command.trim().split("\\s+");
        if (words.length != 4 || !(words[0].equalsIgnoreCase("eco") || words[0].equalsIgnoreCase("essentials:eco"))
                || !words[1].equalsIgnoreCase("give") || !words[2].equals(player.getName())
                || !words[3].equals(Long.toString(amount))) return Result.FAILED;
        if (Bukkit.getCommandMap().getCommand(words[0]) == null) return Result.FAILED;
        try { return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command) ? Result.COMMAND_SENT : Result.FAILED; }
        catch (RuntimeException error) { return Result.FAILED; }
    }
}
