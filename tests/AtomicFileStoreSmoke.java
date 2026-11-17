import dev.hydev.spectrecore.storage.AtomicFileStore;
import java.io.IOException;
import java.nio.file.*;

public final class AtomicFileStoreSmoke {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("spectre-atomic-");
        try {
            Path target = directory.resolve("stations.yml");
            AtomicFileStore.writeUtf8(target, "stations: \u00f1\n");
            assert Files.readString(target).contains("\u00f1");
            AtomicFileStore.writeUtf8(target, "stations: version2\n");
            assert Files.readString(target).equals("stations: version2\n");
            assert Files.readString(directory.resolve("stations.yml.bak")).equals("stations: \u00f1\n");
            AtomicFileStore.writeUtf8(target, "stations: version3\n");
            assert Files.readString(directory.resolve("stations.yml.bak")).equals("stations: \u00f1\n");
            boolean failed = false;
            try { AtomicFileStore.writeUtf8(directory, "impossible"); }
            catch (IOException expected) { failed = true; }
            assert failed : "A directory must never be overwritten as a state file";
            assert Files.readString(target).equals("stations: version3\n");
            try (var files = Files.list(directory)) {
                assert files.noneMatch(f -> f.toString().endsWith(".tmp"));
            }
            System.out.println("PASS AtomicFileStoreSmoke");
        } finally {
            try (var files = Files.walk(directory)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException ignored) { }
                });
            }
        }
    }
}
