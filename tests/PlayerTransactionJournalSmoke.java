import dev.hydev.spectrecore.storage.ClaimJournal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class PlayerTransactionJournalSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("spectrecore-job-claims-");
        Path file = root.resolve("purchases.yml");
        UUID player = UUID.randomUUID();
        String station = "job:" + player;
        ClaimJournal initial = new ClaimJournal(file);
        check(initial.load(), "A missing file starts as an empty journal");
        UUID token = initial.begin(station, player, "JOB_PURCHASE", "minero:novato:50", 1);
        check(token != null && initial.count() == 1, "Claim persisted before payment");

        ClaimJournal reopened = new ClaimJournal(file);
        check(reopened.load() && reopened.blocked(station), "A restart retains pending purchases");
        check(reopened.begin(station, player, "JOB_PURCHASE", "minero:novato:50", 1) == null,
                "A pending purchase prevents a duplicate for the same player");
        check(reopened.unresolved().getFirst().id().equals(token), "Reconciliation preserves token");
        check(reopened.resolve(token), "An administrator can close an audited claim");
        ClaimJournal cleared = new ClaimJournal(file);
        check(cleared.load() && !cleared.blocked(station), "Resolved claims stay resolved across restart");

        for (String invalid : new String[]{"claims: [", "", "{}", "schema-version: 2\n",
                "schema-version: 1\nclaims: {}\nclaims: {}\n", "schema-version: 1\nclaims: []\n"}) {
            Files.writeString(file, invalid, StandardCharsets.UTF_8);
            ClaimJournal damaged = new ClaimJournal(file);
            check(!damaged.load() && !damaged.ready(), "Corrupt or unrecognized state fails closed");
            check(damaged.begin(station, player, "JOB_PURCHASE", "granjero:novato:50", 1) == null,
                    "Broken storage must not accept a new transaction");
            check(Files.readString(file).equals(invalid), "Fail-closed never overwrites corrupt progress");
        }
        Files.writeString(file, "schema-version: 1\n", StandardCharsets.UTF_8);
        check(new ClaimJournal(file).load(), "Recognized legacy empty journal remains valid");
        System.out.println("PASS PlayerTransactionJournalSmoke: restart, duplicate guard, manual reconciliation and corruption");
    }
}
