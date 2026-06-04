package dev.blocktracker;

import dev.blocktracker.shader.VeyraIrisCompat;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class VeyraShaderPackManager {
    public static final String OFF = "OFF";

    private VeyraShaderPackManager() {
    }

    public static List<String> availablePacks() {
        List<String> packs = new ArrayList<>();
        packs.add(OFF);

        Minecraft client = Minecraft.getInstance();
        scan(client.gameDirectory.toPath().resolve("shaderpacks"), packs);
        scan(client.gameDirectory.toPath().resolve("vulkan-shaderpacks"), packs);

        return packs.stream()
                .distinct()
                .sorted(Comparator.comparing(value -> value.equals(OFF) ? "" : value.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public static String selectedPack() {
        Path path = configPath();
        if (!Files.exists(path)) {
            return OFF;
        }

        try {
            String selected = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (selected.isBlank()) {
                return OFF;
            }
            return selected;
        } catch (IOException ignored) {
            return OFF;
        }
    }

    public static void selectPack(String pack) {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, pack == null || pack.isBlank() ? OFF : pack, StandardCharsets.UTF_8);
            VeyraIrisCompat.reloadSelectedPack();
            VeyraVulkanPipelineReloader.reloadNow();
        } catch (IOException ignored) {
        }
    }

    public static boolean isEnabled() {
        return !OFF.equals(selectedPack());
    }

    private static Path configPath() {
        Minecraft client = Minecraft.getInstance();
        return client.gameDirectory.toPath().resolve("config").resolve("veyra-vulkan-shaderpack.txt");
    }

    private static void scan(Path directory, List<String> packs) {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (var stream = Files.list(directory)) {
            stream.forEach(path -> {
                String fileName = path.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (Files.isDirectory(path) && Files.isDirectory(path.resolve("shaders"))) {
                    packs.add(fileName);
                } else if (Files.isRegularFile(path) && lower.endsWith(".zip")) {
                    packs.add(fileName);
                }
            });
        } catch (IOException ignored) {
        }
    }
}
