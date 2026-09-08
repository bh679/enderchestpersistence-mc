package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seeder is what stops the new default from emptying every existing player's chest, so its two
 * safety rules — never overwrite, never move — are the point of these tests.
 */
class StoreSeederTest {

    @TempDir
    Path root;

    private Path write(String name, String contents) throws IOException {
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void copiesWhenTheDestinationIsAbsent() throws IOException {
        Path source = write("instance/chest.dat", "stash");
        Path destination = root.resolve("appdata/chest.dat");

        assertTrue(StoreSeeder.seedIfMissing(destination, source));
        assertEquals("stash", Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    void leavesTheOriginalInPlace() throws IOException {
        Path source = write("instance/chest.dat", "stash");
        StoreSeeder.seedIfMissing(root.resolve("appdata/chest.dat"), source);

        assertTrue(Files.isRegularFile(source),
                "the instance copy must survive so switching back to 'inside' still finds a chest");
    }

    @Test
    void neverOverwritesAnExistingDestination() throws IOException {
        Path source = write("instance/chest.dat", "old stash");
        Path destination = write("appdata/chest.dat", "current stash");

        assertFalse(StoreSeeder.seedIfMissing(destination, source));
        assertEquals("current stash", Files.readString(destination, StandardCharsets.UTF_8));
    }

    @Test
    void doesNothingWithoutASource() {
        Path destination = root.resolve("appdata/chest.dat");
        assertFalse(StoreSeeder.seedIfMissing(destination, root.resolve("instance/chest.dat")));
        assertFalse(Files.exists(destination));
    }

    @Test
    void doesNothingWhenBothPathsAreTheSame() throws IOException {
        // The 'inside' mode resolves store and instance to one directory; seeding there is meaningless.
        Path file = write("instance/chest.dat", "stash");
        assertFalse(StoreSeeder.seedIfMissing(file, file));
    }

    @Test
    void doesNothingWhenTheSourceIsADirectory() throws IOException {
        Files.createDirectories(root.resolve("instance/chest.dat"));
        assertFalse(StoreSeeder.seedIfMissing(root.resolve("appdata/chest.dat"),
                root.resolve("instance/chest.dat")));
    }
}
