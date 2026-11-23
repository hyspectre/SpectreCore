package dev.hydev.spectrecore.mining;

import java.util.function.BooleanSupplier;

/** Main-thread context for automated ore breaking, never counts as manual mining. */
public final class MithrilAutomationGuard {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private MithrilAutomationGuard() { }
    public static boolean active() { return ACTIVE.get(); }
    public static boolean breakBlock(BooleanSupplier action) {
        if (ACTIVE.get()) return false;
        ACTIVE.set(true);
        try { return action.getAsBoolean(); }
        finally { ACTIVE.remove(); }
    }
}
