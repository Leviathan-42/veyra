package dev.blocktracker.shader;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IrisShaderPackLoader {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#include\\s+[\"<]([^\">]+)[\">].*$");

    private IrisShaderPackLoader() {
    }

    public static LoadedPack load(String selectedPack) throws IOException {
        Path packPath = resolvePackPath(selectedPack);
        if (packPath == null) {
            throw new IOException("Shaderpack not found: " + selectedPack);
        }

        if (Files.isRegularFile(packPath) && packPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + packPath.toUri()), Map.of())) {
                return loadFromRoot(selectedPack, zip.getPath("/"));
            }
        }

        return loadFromRoot(selectedPack, packPath);
    }

    private static LoadedPack loadFromRoot(String displayName, Path root) throws IOException {
        Path shaders = root.resolve("shaders");
        Properties properties = new Properties();
        Path propertiesPath = shaders.resolve("shaders.properties");
        if (Files.exists(propertiesPath)) {
            try (var reader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        List<String> errors = new ArrayList<>();
        List<IrisProgramSource> programs = new ArrayList<>();
        for (String programName : discoverPrograms(shaders, errors)) {
            programs.add(loadProgram(shaders, programName, errors));
        }

        return new LoadedPack(displayName, root.toString(), properties, programs, errors);
    }

    private static List<String> discoverPrograms(Path shaders, List<String> errors) {
        Set<String> names = new LinkedHashSet<>();

        for (IrisProgramId id : IrisProgramId.ORDERED) {
            names.add(id.irisName());
        }
        for (String world : List.of("world0", "world1", "world-1")) {
            for (IrisProgramId id : IrisProgramId.ORDERED) {
                names.add(world + "/" + id.irisName());
            }
        }

        if (Files.isDirectory(shaders)) {
            try (Stream<Path> paths = Files.walk(shaders, 4)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> shaders.relativize(path).toString().replace('\\', '/'))
                        .filter(path -> path.endsWith(".vsh") || path.endsWith(".fsh") || path.endsWith(".csh"))
                        .map(path -> path.substring(0, path.lastIndexOf('.')))
                        .sorted(IrisShaderPackLoader::compareProgramNames)
                        .forEach(names::add);
            } catch (IOException exception) {
                errors.add("Failed discovering programs: " + exception.getMessage());
            }
        }

        return new ArrayList<>(names);
    }

    private static int compareProgramNames(String left, String right) {
        return Integer.compare(programSortKey(left), programSortKey(right));
    }

    private static int programSortKey(String name) {
        String localName = name;
        int slash = localName.lastIndexOf('/');
        if (slash >= 0) {
            localName = localName.substring(slash + 1);
        }

        for (int i = 0; i < IrisProgramId.ORDERED.size(); i++) {
            if (IrisProgramId.ORDERED.get(i).irisName().equals(localName)) {
                return i;
            }
        }
        if (localName.startsWith("setup")) return -20;
        if (localName.startsWith("prepare")) return -10;
        if (localName.startsWith("shadow")) return 0;
        if (localName.startsWith("gbuffers")) return 100;
        if (localName.startsWith("deferred")) return 200 + trailingNumber(localName);
        if (localName.startsWith("composite")) return 300 + trailingNumber(localName);
        if (localName.startsWith("final")) return 500;
        return 1000;
    }

    private static int trailingNumber(String value) {
        int i = value.length() - 1;
        while (i >= 0 && Character.isDigit(value.charAt(i))) {
            i--;
        }
        if (i == value.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(value.substring(i + 1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static IrisProgramSource loadProgram(Path shaders, String name, List<String> errors) {
        String vertex = readPreprocessed(shaders, name + ".vsh", errors, new ArrayList<>());
        String fragment = readPreprocessed(shaders, name + ".fsh", errors, new ArrayList<>());
        String compute = readPreprocessed(shaders, name + ".csh", errors, new ArrayList<>());

        return new IrisProgramSource(
                name,
                transformVertex(name, vertex),
                IrisSourceTransformer.toVulkanGlsl(fragment, IrisSourceTransformer.Stage.FRAGMENT),
                IrisSourceTransformer.toVulkanGlsl(compute, IrisSourceTransformer.Stage.COMPUTE),
                vertex != null,
                fragment != null,
                compute != null
        );
    }

    private static String transformVertex(String name, String vertex) {
        if (vertex == null) {
            return null;
        }
        String local = name;
        int slash = local.lastIndexOf('/');
        if (slash >= 0) {
            local = local.substring(slash + 1);
        }
        if (local.startsWith("deferred") || local.startsWith("composite") || local.equals("final")) {
            return """
#version 450
layout(location = 0) out vec2 texCoord;
const vec4 veyra_FullscreenPos[3] = vec4[3](vec4(-1.0, -1.0, 0.0, 1.0), vec4(3.0, -1.0, 0.0, 1.0), vec4(-1.0, 3.0, 0.0, 1.0));
const vec2 veyra_FullscreenUv[3] = vec2[3](vec2(0.0, 0.0), vec2(2.0, 0.0), vec2(0.0, 2.0));
void main() {
    texCoord = veyra_FullscreenUv[gl_VertexIndex];
    gl_Position = veyra_FullscreenPos[gl_VertexIndex];
}
""";
        }
        return IrisSourceTransformer.toVulkanGlsl(vertex, IrisSourceTransformer.Stage.VERTEX);
    }

    private static String readPreprocessed(Path shaders, String relative, List<String> errors, List<String> stack) {
        Path path = shaders.resolve(relative).normalize();
        if (!Files.exists(path)) {
            return null;
        }

        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return resolveIncludes(shaders, relative, source, errors, stack);
        } catch (IOException exception) {
            errors.add("Failed reading " + relative + ": " + exception.getMessage());
            return null;
        }
    }

    private static String resolveIncludes(Path shaders, String currentRelative, String source, List<String> errors, List<String> stack) {
        if (stack.contains(currentRelative)) {
            errors.add("Include cycle: " + String.join(" -> ", stack) + " -> " + currentRelative);
            return source;
        }

        stack.add(currentRelative);
        Matcher matcher = INCLUDE.matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String include = matcher.group(1);
            String includeRelative = resolveIncludeRelative(currentRelative, include);
            Path includePath = shaders.resolve(includeRelative).normalize();
            String replacement;
            if (!Files.exists(includePath)) {
                errors.add("Missing include " + include + " from " + currentRelative + " -> " + includeRelative);
                replacement = "// VEYRA missing include: " + include;
            } else {
                try {
                    replacement = resolveIncludes(shaders, includeRelative, Files.readString(includePath, StandardCharsets.UTF_8), errors, new ArrayList<>(stack));
                } catch (IOException exception) {
                    errors.add("Failed include " + includeRelative + ": " + exception.getMessage());
                    replacement = "// VEYRA failed include: " + include;
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String resolveIncludeRelative(String currentRelative, String include) {
        String normalized = include.startsWith("/") ? include.substring(1) : include;
        if (include.startsWith("/")) {
            return normalized;
        }

        int slash = currentRelative.lastIndexOf('/');
        if (slash < 0) {
            return normalized;
        }

        return currentRelative.substring(0, slash + 1) + normalized;
    }

    private static Path resolvePackPath(String selectedPack) {
        if (selectedPack == null || selectedPack.isBlank() || selectedPack.equals("OFF")) {
            return null;
        }

        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        for (String folder : List.of("shaderpacks", "vulkan-shaderpacks")) {
            Path path = gameDir.resolve(folder).resolve(selectedPack);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    public record LoadedPack(
            String displayName,
            String path,
            Properties properties,
            List<IrisProgramSource> programs,
            List<String> errors
    ) {
        public List<IrisProgramSource> presentPrograms() {
            return programs.stream().filter(IrisProgramSource::present).toList();
        }
    }
}
