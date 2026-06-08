package dev.blocktracker;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VeyraOnboarding {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("veyra-onboarding.properties");
    private static boolean loaded;
    private static boolean dismissed;
    private static boolean shownThisSession;

    private VeyraOnboarding() {
    }

    public static boolean consumeShouldShow() {
        load();
        if (dismissed || shownThisSession) {
            return false;
        }

        shownThisSession = true;
        return true;
    }

    public static void dismissForever() {
        load();
        dismissed = true;
        save();
    }

    public static void showNextLaunch() {
        load();
        dismissed = false;
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        if (!Files.isRegularFile(PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(PATH)) {
            properties.load(reader);
            dismissed = Boolean.parseBoolean(properties.getProperty("dismissed", "false"));
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("dismissed", Boolean.toString(dismissed));
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                properties.store(writer, "Veyra startup tutorial settings");
            }
        } catch (IOException ignored) {
        }
    }
}
