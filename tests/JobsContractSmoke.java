import dev.hydev.spectrecore.systems.JobsContractRules;
import static dev.hydev.spectrecore.systems.JobsContractRules.Refusal.*;

public final class JobsContractSmoke {
    private static int checks;
    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (String job : JobsContractRules.JOBS) {
            check(JobsContractRules.validate(job, "novato", true, 0, 50, false) == NONE, "Novato allowed " + job);
            check(JobsContractRules.validate(job, "experimentado", true, 1, 1500, false) == NONE, "Experimentado allowed " + job);
            check(JobsContractRules.validate(job, "profesional", true, 2, 3500, false) == NONE, "Profesional allowed " + job);
            check(JobsContractRules.validate(job, "experimentado", true, 0, 1500, false)
                    == PREVIOUS_RANK_REQUIRED, "Skip novice denied " + job);
            check(JobsContractRules.validate(job, "profesional", true, 1, 3500, false)
                    == PREVIOUS_RANK_REQUIRED, "Skip experienced denied " + job);
            check(JobsContractRules.validate(job, "novato", true, 1, 50, false) == ALREADY_OWNED, "Repeat charge denied " + job);
            check(JobsContractRules.validate(job, "profesional", true, 3, 3500, false) == ALREADY_OWNED, "Max rank repeat charge denied " + job);
            check(JobsContractRules.validate(job, "novato", false, 0, 50, false) == DISABLED, "Disabled denied " + job);
            check(JobsContractRules.validate(job, "novato", true, 0, 50, true) == IN_PROGRESS, "Double click denied " + job);
            check(JobsContractRules.validate(job, "novato", true, 0, -1, false) == INVALID_PRICE, "Negative price denied " + job);
            check(JobsContractRules.validate(job, "novato", true, 0, 1_000_000_001L, false) == INVALID_PRICE, "Huge price denied " + job);
        }
        check(JobsContractRules.validate("unknown", "novato", true, 0, 50, false) == INVALID_JOB, "Unknown job denied");
        check(JobsContractRules.validate("minero", "legendario", true, 0, 50, false) == INVALID_RANK, "Unknown rank denied");
        check(JobsContractRules.tier("profesional") == 3, "Tier mapping");
        System.out.println("PASS JobsContractSmoke: " + checks + " assertions");
    }
}
