import dev.hydev.spectrecore.relicario.RelicarioStore;
import dev.hydev.spectrecore.storage.StateYaml;
import java.nio.file.*;
import java.util.UUID;

public class StateRecoverySmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("spectre-state-test");
        try {
            Path file = root.resolve("mining-data.yml");
            require(StateYaml.read(file.toFile(), "players").getKeys(false).isEmpty(), "missing = new state");
            for (String bad : new String[]{"", "players: [", "players: {}\nplayers: {}\n", "players: []"}) {
                Files.writeString(file, bad);
                boolean rejected = false;
                try { StateYaml.read(file.toFile(), "players"); }
                catch (Exception expected) { rejected = true; }
                require(rejected && Files.readString(file).equals(bad), "reject without overwriting");
            }
            Files.writeString(file, "players: {}\n");
            require(StateYaml.read(file.toFile(), "players").getKeys(false).isEmpty(), "valid empty state");

            UUID id = UUID.randomUUID();
            var store = new RelicarioStore(root.toFile(), 32);
            require(store.initialize() && store.load(id), "new ledger");
            var transaction = store.begin(id, "TEST_RELIC");
            require(transaction != null && store.discovered(id).isEmpty() && store.blocked(id), "pending never grants");
            require(store.begin(id, "OTHER_RELIC") == null && !store.confirm(id, "wrong"), "one receipt");
            store.unload(id);
            require(store.load(id) && store.blocked(id) && store.discovered(id).isEmpty(), "restart before payment");
            require(store.confirm(id, transaction.token()), "recover paid item");
            store.unload(id);
            require(store.load(id) && !store.blocked(id) && store.discovered(id).contains("TEST_RELIC"), "confirmed persists");
            require(store.begin(id, "TEST_RELIC") == null, "duplicate cannot charge");
            UUID old = UUID.randomUUID();
            Path ledger = root.resolve("data/relicario/" + old + ".yml");
            String legacy = "version: 1\nuuid: " + old + "\nabsorbed: [OLD_RELIC]\npending-withdrawal: [OLD_RELIC]\n";
            Files.writeString(ledger, legacy);
            require(store.load(old) && store.blocked(old) && store.discovered(old).contains("OLD_RELIC"), "legacy kept and quarantined");
            require(store.begin(old, "OTHER_RELIC") == null && Files.readString(ledger).equals(legacy), "legacy not rewritten");
            System.out.println("PASS StateRecoverySmoke: strict state, pending, restart, receipt, duplicate and legacy");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
}
