package dev.hydev.spectrecore.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Safely replaces small state files. The first existing version is retained as .bak.
 * Keep calls on the owning thread; larger/frequently updated state needs snapshot batching.
 */
public final class AtomicFileStore {
    private AtomicFileStore() { }

    public static void writeUtf8(Path target, String content) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Path directory = absolute.getParent();
        if (directory == null) throw new IOException("Missing destination directory: " + target);
        Files.createDirectories(directory);
        if (Files.isDirectory(absolute)) throw new IOException("State path is a directory: " + absolute);
        Path previous = absolute.resolveSibling(absolute.getFileName() + ".bak");
        // A one-time safety copy: do not double disk IO on every frequent station update.
        if (Files.isRegularFile(absolute) && !Files.exists(previous)) {
            Path copy = Files.createTempFile(directory, absolute.getFileName() + ".backup-", ".tmp");
            try {
                Files.copy(absolute, copy, StandardCopyOption.REPLACE_EXISTING);
                moveReplacing(copy, previous);
            } finally { Files.deleteIfExists(copy); }
        }
        Path temp = Files.createTempFile(directory, absolute.getFileName() + ".write-", ".tmp");
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            moveReplacing(temp, absolute);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void moveReplacing(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) {
            // Filesystems without atomic rename keep the preexisting .bak for manual recovery.
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
