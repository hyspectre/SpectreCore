import dev.hydev.spectrecore.relicario.CollectionMilestones;
import java.util.List;

public final class CollectionMilestonesSmoke {
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid milestone state"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        List<Integer> goals = CollectionMilestones.validate(List.of(25, 50, 100));
        check(CollectionMilestones.required(25, 11) == 3, "25% of eleven requires three");
        check(CollectionMilestones.required(50, 11) == 6, "50% rounds upward");
        var start = CollectionMilestones.progress(0, 11, goals);
        check(start.obtained() == 0 && start.nextPercent() == 25 && start.nextRequired() == 3,
                "Initial category objective");
        var next = CollectionMilestones.progress(4, 11, goals);
        check(next.percent() == 36 && next.nextPercent() == 50 && next.nextRequired() == 6,
                "Next objective after reaching 25%");
        check(CollectionMilestones.crossed(1, 2, 11, goals) == 0, "No false milestones");
        check(CollectionMilestones.crossed(2, 3, 11, goals) == 25, "First milestone reached");
        check(CollectionMilestones.crossed(5, 6, 11, goals) == 50, "Second milestone reached");
        check(CollectionMilestones.crossed(10, 11, 11, goals) == 100, "Completion");
        check(CollectionMilestones.progress(11, 11, goals).complete(), "Full collection recognized");
        check(!CollectionMilestones.progress(0, 0, goals).complete()
                && CollectionMilestones.progress(0, 0, goals).nextPercent() == 0,
                "Empty category has no invented completion");
        check(CollectionMilestones.crossed(0, 1, 1, goals) == 100,
                "A single-discovery category shows only the highest milestone");
        invalid(() -> CollectionMilestones.validate(List.of(25, 25, 100)));
        invalid(() -> CollectionMilestones.validate(List.of(25, 50)));
        invalid(() -> CollectionMilestones.validate(List.of(0, 100)));
        invalid(() -> CollectionMilestones.progress(12, 11, goals));
        invalid(() -> CollectionMilestones.crossed(3, 2, 11, goals));
        System.out.println("PASS CollectionMilestonesSmoke: exact thresholds, empty sets, persisted counts and false positives");
    }
}
