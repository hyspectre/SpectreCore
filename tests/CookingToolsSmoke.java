import dev.hydev.spectrecore.cooking.CookingToolActions;
import static dev.hydev.spectrecore.cooking.CookingToolActions.Use;

public final class CookingToolsSmoke {
    private static void expect(Use expected, String material, boolean registered, boolean burned) {
        Use actual = CookingToolActions.select(material, registered, burned);
        if (expected != actual) throw new AssertionError(material + ": " + expected + " != " + actual);
    }
    public static void main(String[] args) {
        expect(Use.CREATE, "FLINT_AND_STEEL", false, false);
        expect(Use.NONE, "BOWL", false, false);
        expect(Use.NONE, "SPONGE", false, false);
        expect(Use.STIR, "WOODEN_SHOVEL", true, false);
        expect(Use.NONE, "STICK", true, false);
        expect(Use.SERVE, "BOWL", true, false);
        expect(Use.REMOVE, "SPONGE", true, false);
        expect(Use.REMOVE, "WET_SPONGE", true, false);
        expect(Use.CLEAN, "SPONGE", true, true);
        expect(Use.CLEAN, "WET_SPONGE", true, true);
        expect(Use.NONE, "FLINT_AND_STEEL", true, true);
        System.out.println("OK: controles fisicos de cocina");
    }
}
