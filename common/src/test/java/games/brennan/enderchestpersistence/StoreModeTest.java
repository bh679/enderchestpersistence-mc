package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreModeTest {

    @Test
    void parsesEachMode() {
        assertEquals(Optional.of(StoreMode.INSIDE), StoreMode.parse("inside"));
        assertEquals(Optional.of(StoreMode.OUTSIDE), StoreMode.parse("outside"));
        assertEquals(Optional.of(StoreMode.OFF), StoreMode.parse("off"));
    }

    @Test
    void toleratesCasingAndSurroundingWhitespace() {
        // Hand-edited config files pick up both; neither should cost a player their chest.
        assertEquals(Optional.of(StoreMode.OUTSIDE), StoreMode.parse("  OutSide \t"));
    }

    @Test
    void rejectsUnknownAndAbsentValues() {
        assertTrue(StoreMode.parse("elsewhere").isEmpty());
        assertTrue(StoreMode.parse("").isEmpty());
        assertTrue(StoreMode.parse("   ").isEmpty());
        assertTrue(StoreMode.parse(null).isEmpty());
    }

    @Test
    void configValuesRoundTrip() {
        for (StoreMode mode : StoreMode.values()) {
            assertEquals(Optional.of(mode), StoreMode.parse(mode.configValue()));
        }
    }
}
