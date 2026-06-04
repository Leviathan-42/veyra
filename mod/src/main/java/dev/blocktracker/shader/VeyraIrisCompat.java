package dev.blocktracker.shader;

import dev.blocktracker.VeyraShaderPackManager;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VeyraIrisCompat {
    private static final Logger LOGGER = LogManager.getLogger("VeyraIrisCompat");
    private static IrisShaderPackLoader.LoadedPack activePack;

    private VeyraIrisCompat() {
    }

    public static void reloadSelectedPack() {
        String selected = VeyraShaderPackManager.selectedPack();
        if (VeyraShaderPackManager.OFF.equals(selected)) {
            activePack = null;
            writeReport("Shaders disabled.\n");
            return;
        }

        try {
            activePack = IrisShaderPackLoader.load(selected);
            String report = buildReport(activePack);
            writeReport(report);
            LOGGER.info("Loaded Iris shaderpack '{}' with {} programs, {} properties, {} preprocessing errors",
                    activePack.displayName(),
                    activePack.presentPrograms().size(),
                    activePack.properties().size(),
                    activePack.errors().size());
        } catch (IOException exception) {
            activePack = null;
            String report = "Failed to load shaderpack " + selected + ": " + exception.getMessage() + "\n";
            writeReport(report);
            LOGGER.error("Failed to load Iris shaderpack '{}'", selected, exception);
        }
    }

    public static IrisShaderPackLoader.LoadedPack activePack() {
        return activePack;
    }

    private static String buildReport(IrisShaderPackLoader.LoadedPack pack) {
        StringBuilder out = new StringBuilder();
        out.append("Veyra Iris compatibility report\n");
        out.append("Pack: ").append(pack.displayName()).append('\n');
        out.append("Path: ").append(pack.path()).append('\n');
        out.append("Properties: ").append(pack.properties().size()).append('\n');
        out.append("Programs: ").append(pack.presentPrograms().size()).append('\n');
        out.append('\n');

        out.append("Program graph order:\n");
        pack.presentPrograms().forEach(program -> out.append("- ")
                .append(program.name())
                .append(" v=").append(program.hasVertex())
                .append(" f=").append(program.hasFragment())
                .append(" c=").append(program.hasCompute())
                .append('\n'));
        out.append('\n');

        out.append("Important shader.properties keys:\n");
        pack.properties().stringPropertyNames().stream()
                .filter(key -> key.startsWith("program.")
                        || key.startsWith("blend.")
                        || key.startsWith("scale.")
                        || key.startsWith("texture.")
                        || key.startsWith("buffer")
                        || key.startsWith("colortex")
                        || key.startsWith("shadow"))
                .sorted(Comparator.naturalOrder())
                .limit(160)
                .forEach(key -> out.append("- ").append(key).append('=').append(pack.properties().getProperty(key)).append('\n'));
        out.append('\n');

        out.append("Preprocessor errors: ").append(pack.errors().size()).append('\n');
        pack.errors().stream().limit(100).forEach(error -> out.append("- ").append(error).append('\n'));
        out.append('\n');

        List<String> compileResults = compileCheck(pack);
        out.append("SPIR-V compile check:\n");
        compileResults.forEach(line -> out.append(line).append('\n'));
        out.append('\n');

        IrisRenderPlan renderPlan = IrisRenderPlan.from(pack);
        out.append("Iris render plan:\n");
        out.append("- passes=").append(renderPlan.passes().size()).append('\n');
        out.append("- colorTargets=").append(String.join(", ", renderPlan.colorTargets())).append('\n');
        out.append("- depthTargets=").append(String.join(", ", renderPlan.depthTargets())).append('\n');
        out.append("- samplers=").append(renderPlan.samplers().size()).append(" images=").append(renderPlan.images().size()).append(" customTextures=").append(renderPlan.customTextures().size()).append('\n');
        renderPlan.passes().stream().limit(96).forEach(pass -> out.append("- ")
                .append(pass.kind())
                .append(' ')
                .append(pass.name())
                .append(" -> DRAWBUFFERS:")
                .append(String.join("", pass.drawBuffers()))
                .append('\n'));
        if (renderPlan.passes().size() > 96) {
            out.append("- skipped remaining render-plan passes\n");
        }
        out.append('\n');

        dumpTransformedSources(pack);

        out.append("Next build step: allocate Iris colortex/depthtex/shadowtex resources and create Vulkan graphics/compute pipelines from the compiled Solas programs.\n");
        return out.toString();
    }

    private static List<String> compileCheck(IrisShaderPackLoader.LoadedPack pack) {
        List<String> results = new ArrayList<>();
        int checked = 0;
        int ok = 0;
        int failed = 0;

        int maxChecks = Integer.getInteger("veyra.iris.compileCheckLimit", 256);
        for (IrisProgramSource program : pack.presentPrograms()) {
            if (checked >= maxChecks) {
                results.add("- skipped remaining programs after first " + maxChecks + " to keep reload time sane");
                break;
            }

            if (program.hasVertex()) {
                checked++;
                if (compileOne(program.name() + ".vsh", program.vertexSource(), SPIRVUtils.ShaderKind.VERTEX_SHADER, results)) ok++; else failed++;
            }
            if (program.hasFragment()) {
                checked++;
                if (compileOne(program.name() + ".fsh", program.fragmentSource(), SPIRVUtils.ShaderKind.FRAGMENT_SHADER, results)) ok++; else failed++;
            }
            if (program.hasCompute()) {
                checked++;
                if (compileOne(program.name() + ".csh", program.computeSource(), SPIRVUtils.ShaderKind.COMPUTE_SHADER, results)) ok++; else failed++;
            }
        }

        results.add(0, "- checked=" + checked + " ok=" + ok + " failed=" + failed);
        return results;
    }

    private static boolean compileOne(String name, String source, SPIRVUtils.ShaderKind kind, List<String> results) {
        try {
            SPIRVUtils.compileShader("veyra/" + name, source, kind);
            results.add("- OK " + name);
            return true;
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null) {
                message = throwable.getClass().getSimpleName();
            }
            message = message.replace('\n', ' ').replace('\r', ' ');
            if (message.length() > 500) {
                message = message.substring(0, 500) + "...";
            }
            results.add("- FAIL " + name + " :: " + message);
            return false;
        }
    }

    private static void dumpTransformedSources(IrisShaderPackLoader.LoadedPack pack) {
        try {
            Path root = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config")
                    .resolve("veyra-iris-dump")
                    .resolve(safeFileName(pack.displayName()));
            deleteDirectory(root);
            Files.createDirectories(root);
            for (IrisProgramSource program : pack.presentPrograms()) {
                if (program.hasVertex()) writeDump(root, program.name() + ".vsh", program.vertexSource());
                if (program.hasFragment()) writeDump(root, program.name() + ".fsh", program.fragmentSource());
                if (program.hasCompute()) writeDump(root, program.name() + ".csh", program.computeSource());
            }
        } catch (IOException exception) {
            LOGGER.warn("Failed dumping transformed Iris shader sources", exception);
        }
    }

    private static void writeDump(Path root, String relative, String source) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, source == null ? "" : source, StandardCharsets.UTF_8);
    }

    private static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path current : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void writeReport(String report) {
        try {
            Path path = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config")
                    .resolve("veyra-iris-compat-report.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, report, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Failed writing Iris compatibility report", exception);
        }
    }
}
