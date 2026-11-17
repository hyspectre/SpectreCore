import dev.hydev.spectrecore.cooking.CookingTiming;

public class CookingTimingSmoke {
    public static void main(String[] args) {
        assert !CookingTiming.isPrecise(7999, 8, 14, .6);
        assert !CookingTiming.isPrecise(8100, 8, 14, .6);
        assert CookingTiming.isPrecise(11000, 8, 14, .6);
        assert !CookingTiming.isPrecise(13800, 8, 14, .6);
        assert !CookingTiming.isPrecise(14000, 8, 14, .6);
        assert CookingTiming.quality(19000, 18, 23, true, true, true).equals("exquisito");
        assert CookingTiming.quality(19000, 18, 23, true, false, true).equals("normal");
        assert CookingTiming.quality(23000, 18, 23, true, true, true).equals("pasado");
        assert CookingTiming.quality(19000, 18, 23, true, false, false).equals("exquisito");
        System.out.println("OK: Cocina, ventanas precisas y calidades");
    }
}
