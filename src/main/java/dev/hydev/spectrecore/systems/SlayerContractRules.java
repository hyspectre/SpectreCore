package dev.hydev.spectrecore.systems;

/** Pure validation for player-facing Slayer contracts, before any Vault operation. */
public final class SlayerContractRules {
    private SlayerContractRules() { }
    public enum Refusal { NONE, DISABLED, INVALID_TIER, ALREADY_ACTIVE, NO_PERMISSION, BAD_PRICE }

    public static Refusal validateStart(boolean contractsEnabled, int tier, boolean alreadyActive,
                                        boolean hasPermission, long price) {
        if (!contractsEnabled) return Refusal.DISABLED;
        if (tier < 1 || tier > 3) return Refusal.INVALID_TIER;
        if (alreadyActive) return Refusal.ALREADY_ACTIVE;
        if (!hasPermission) return Refusal.NO_PERMISSION;
        if (price < 0 || price > 1_000_000_000L) return Refusal.BAD_PRICE;
        return Refusal.NONE;
    }
    public static Refusal validateCancel(boolean contractsEnabled, boolean active, long price) {
        if (!contractsEnabled) return Refusal.DISABLED;
        if (!active) return Refusal.ALREADY_ACTIVE;
        if (price < 0 || price > 1_000_000_000L) return Refusal.BAD_PRICE;
        return Refusal.NONE;
    }
}
