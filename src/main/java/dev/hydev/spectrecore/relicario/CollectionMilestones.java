package dev.hydev.spectrecore.relicario;

import java.util.List;
import java.util.Objects;

/** Pure, read-only collection milestones. Discovery IDs remain the only source of progress. */
public final class CollectionMilestones {
    private CollectionMilestones() { }

    public record Progress(int obtained, int total, int percent, int nextPercent, int nextRequired) {
        public boolean complete() { return total > 0 && obtained == total; }
    }

    public static List<Integer> validate(List<Integer> percentages) {
        Objects.requireNonNull(percentages, "Milestones");
        if (percentages.isEmpty() || percentages.size() > 8)
            throw new IllegalArgumentException("Invalid number of collection milestones");
        int previous = 0;
        for (Integer value : percentages) {
            if (value == null || value <= previous || value > 100)
                throw new IllegalArgumentException("Milestones must increase from 1 to 100");
            previous = value;
        }
        if (previous != 100) throw new IllegalArgumentException("The final milestone must be 100%");
        return List.copyOf(percentages);
    }

    public static int required(int percent, int total) {
        if (percent < 1 || percent > 100 || total < 1 || total > 4096)
            throw new IllegalArgumentException("Invalid collection milestone or size");
        return (percent * total + 99) / 100;
    }

    public static Progress progress(int obtained, int total, List<Integer> milestones) {
        validate(milestones);
        if (total < 0 || total > 4096 || obtained < 0 || obtained > total)
            throw new IllegalArgumentException("Invalid collection progress");
        if (total == 0) return new Progress(0, 0, 0, 0, 0);
        int percent = obtained * 100 / total;
        for (int goal : milestones) {
            int required = required(goal, total);
            if (obtained < required) return new Progress(obtained, total, percent, goal, required);
        }
        return new Progress(obtained, total, percent, 0, 0);
    }

    /** Returns the highest newly reached milestone, or 0 when none was crossed. */
    public static int crossed(int previous, int current, int total, List<Integer> milestones) {
        validate(milestones);
        if (total < 1 || total > 4096 || previous < 0 || previous > current || current > total)
            throw new IllegalArgumentException("Invalid milestone transition");
        int reached = 0;
        for (int goal : milestones) {
            int required = required(goal, total);
            if (previous < required && current >= required) reached = goal;
        }
        return reached;
    }
}
