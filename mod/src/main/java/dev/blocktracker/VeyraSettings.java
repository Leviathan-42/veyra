package dev.blocktracker;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persists client-only Veyra settings without writing on every HUD drag event. */
public final class VeyraSettings {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("veyra-client.properties");
    private static final long SAVE_DELAY_NANOS = 500_000_000L;

    private static boolean loaded;
    private static boolean loading;
    private static boolean dirty;
    private static long dirtyAt;

    private VeyraSettings() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        loading = true;
        try {
            if (Files.isRegularFile(PATH)) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    properties.load(reader);
                }
                BlockTrackerState.applyPersistentProperties(properties);
            }
        } catch (IOException | RuntimeException ignored) {
            // Keep safe defaults if an older or partially written config cannot be read.
        } finally {
            loading = false;
            dirty = false;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(VeyraSettings::saveNow, "Veyra settings save"));
    }

    public static synchronized void markDirty() {
        if (!loaded || loading) {
            return;
        }

        dirty = true;
        dirtyAt = System.nanoTime();
    }

    public static synchronized void tick() {
        if (dirty && System.nanoTime() - dirtyAt >= SAVE_DELAY_NANOS) {
            saveNow();
        }
    }

    public static synchronized void saveNow() {
        if (!loaded || loading || !dirty) {
            return;
        }

        Properties properties = BlockTrackerState.persistentProperties();
        Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                properties.store(writer, "Veyra client settings");
            }

            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException ignored) {
            // Leave dirty=true so a later tick or shutdown can retry.
        }
    }
}
