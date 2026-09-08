package games.brennan.enderchestpersistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcpConfigTest {

    @TempDir
    Path configDir;

    private void writeConfig(String contents) throws IOException {
        Files.writeString(configDir.resolve(EcpConfig.FILE_NAME), contents, StandardCharsets.UTF_8);
    }

    @Test
    void missingFileWritesTheDefaultAndReturnsOutside() {
        assertEquals(StoreMode.OUTSIDE, EcpConfig.readStoreMode(configDir));
        assertTrue(Files.isRegularFile(configDir.resolve(EcpConfig.FILE_NAME)),
                "first run should leave a config file the player can edit");
    }

    @Test
    void theDefaultFileParsesBackToTheDefaultMode() throws IOException {
        // Guards against the shipped comments and the parser drifting apart.
        writeConfig(EcpConfig.defaultFileContents());
        assertEquals(EcpConfig.DEFAULT_MODE, EcpConfig.readStoreMode(configDir));
    }

    @Test
    void readsAnExplicitMode() throws IOException {
        writeConfig("store-location=inside\n");
        assertEquals(StoreMode.INSIDE, EcpConfig.readStoreMode(configDir));

        writeConfig("store-location=off\n");
        assertEquals(StoreMode.OFF, EcpConfig.readStoreMode(configDir));
    }

    @Test
    void unrecognisedValueFallsBackToTheDefault() throws IOException {
        writeConfig("store-location=somewhere-else\n");
        assertEquals(StoreMode.OUTSIDE, EcpConfig.readStoreMode(configDir));
    }

    @Test
    void missingOrBlankKeyFallsBackToTheDefault() throws IOException {
        writeConfig("# nothing set here\n");
        assertEquals(StoreMode.OUTSIDE, EcpConfig.readStoreMode(configDir));

        writeConfig("store-location=\n");
        assertEquals(StoreMode.OUTSIDE, EcpConfig.readStoreMode(configDir));
    }

    @Test
    void anExistingFileIsNotOverwritten() throws IOException {
        writeConfig("store-location=inside\n");
        EcpConfig.readStoreMode(configDir);
        assertEquals("store-location=inside\n",
                Files.readString(configDir.resolve(EcpConfig.FILE_NAME), StandardCharsets.UTF_8),
                "re-reading must not rewrite a player's edited config");
    }
}
