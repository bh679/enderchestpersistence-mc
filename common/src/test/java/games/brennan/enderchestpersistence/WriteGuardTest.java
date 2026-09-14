package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteGuardTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @BeforeEach
    @AfterEach
    void reset() {
        WriteGuard.reset();
    }

    @Test
    void unblockedByDefault() {
        assertFalse(WriteGuard.isBlocked(A));
        assertFalse(WriteGuard.isBlocked(null));
    }

    @Test
    void blockIsPerUuidAndClearable() {
        WriteGuard.block(A);
        assertTrue(WriteGuard.isBlocked(A));
        assertFalse(WriteGuard.isBlocked(B));

        WriteGuard.clear(A);
        assertFalse(WriteGuard.isBlocked(A));
    }

    @Test
    void nullIsIgnored() {
        WriteGuard.block(null);
        WriteGuard.clear(null);
        assertFalse(WriteGuard.isBlocked(null));
    }
}
