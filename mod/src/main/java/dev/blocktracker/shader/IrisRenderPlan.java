package dev.blocktracker.shader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record IrisRenderPlan(
        List<Pass> passes,
        List<String> colorTargets,
        List<String> depthTargets,
        List<String> samplers,
        List<String> images,
        Map<String, String> customTextures
) {
    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS:([^*]+)\\*/");
    private static final Pattern OPAQUE_UNIFORM = Pattern.compile("layout\\s*\\(\\s*binding\\s*=\\s*(\\d+)\\s*\\)\\s*(?:readonly\\s+|writeonly\\s+|coherent\\s+|volatile\\s+|restrict\\s+)*uniform\\s+([A-Za-z0-9_]+)\\s+([^;]+);");

    public static IrisRenderPlan from(IrisShaderPackLoader.LoadedPack pack) {
        List<Pass> passes = new ArrayList<>();
        Set<String> colorTargets = new LinkedHashSet<>();
        Set<String> depthTargets = new LinkedHashSet<>();
        Set<String> samplers = new LinkedHashSet<>();
        Set<String> images = new LinkedHashSet<>();

        for (IrisProgramSource program : pack.presentPrograms()) {
            if (program.hasFragment()) {
                List<String> drawBuffers = drawBuffers(program.fragmentSource());
                drawBuffers.forEach(buffer -> colorTargets.add("colortex" + buffer));
                passes.add(new Pass(program.name(), passKind(program.name()), drawBuffers));
                collectOpaqueUniforms(program.fragmentSource(), samplers, images);
            }
            if (program.hasVertex()) {
                collectOpaqueUniforms(program.vertexSource(), samplers, images);
            }
            if (program.hasCompute()) {
                collectOpaqueUniforms(program.computeSource(), samplers, images);
            }
        }

        // Core Iris targets expected by Solas and most packs.
        depthTargets.add("depthtex0");
        depthTargets.add("depthtex1");
        depthTargets.add("depthtex2");
        colorTargets.add("shadowcolor0");
        for (String sampler : samplers) {
            if (sampler.startsWith("depthtex") || sampler.startsWith("shadowtex")) {
                depthTargets.add(sampler);
            }
            if (sampler.equals("dhDepthTex0") || sampler.equals("vxDepthTexOpaque")) {
                depthTargets.add("depthtex0");
            }
            if (sampler.equals("dhDepthTex1") || sampler.equals("vxDepthTexTrans")) {
                depthTargets.add("depthtex1");
            }
            if (sampler.startsWith("colortex") || sampler.startsWith("shadowcolor")) {
                colorTargets.add(sampler);
            }
        }

        return new IrisRenderPlan(
                passes,
                new ArrayList<>(colorTargets),
                new ArrayList<>(depthTargets),
                new ArrayList<>(samplers),
                new ArrayList<>(images),
                customTextures(pack.properties())
        );
    }

    private static List<String> drawBuffers(String source) {
        Matcher matcher = DRAWBUFFERS.matcher(source == null ? "" : source);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1).trim();
        }
        if (last == null || last.isBlank()) {
            return List.of("0");
        }

        List<String> buffers = new ArrayList<>();
        for (int i = 0; i < last.length(); i++) {
            char c = last.charAt(i);
            if (Character.isDigit(c)) {
                buffers.add(String.valueOf(c));
            } else if (c >= 'A' && c <= 'F') {
                buffers.add(String.valueOf(10 + c - 'A'));
            } else if (c >= 'a' && c <= 'f') {
                buffers.add(String.valueOf(10 + c - 'a'));
            }
        }
        return buffers.isEmpty() ? List.of("0") : buffers;
    }

    private static void collectOpaqueUniforms(String source, Set<String> samplers, Set<String> images) {
        if (source == null) {
            return;
        }
        Matcher matcher = OPAQUE_UNIFORM.matcher(source);
        while (matcher.find()) {
            String type = matcher.group(2);
            for (String name : matcher.group(3).split(",")) {
                String clean = name.trim();
                int bracket = clean.indexOf('[');
                if (bracket >= 0) clean = clean.substring(0, bracket).trim();
                if (clean.isBlank()) continue;
                if (type.contains("sampler")) samplers.add(clean);
                if (type.contains("image")) images.add(clean);
            }
        }
    }

    private static Map<String, String> customTextures(Properties properties) {
        Map<String, String> textures = new LinkedHashMap<>();
        properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("texture."))
                .sorted()
                .forEach(key -> textures.put(key, properties.getProperty(key)));
        return textures;
    }

    private static String passKind(String name) {
        String local = name;
        int slash = local.lastIndexOf('/');
        if (slash >= 0) local = local.substring(slash + 1);
        if (local.startsWith("shadow")) return "shadow";
        if (local.startsWith("gbuffers")) return "gbuffers";
        if (local.startsWith("deferred")) return "deferred";
        if (local.startsWith("composite")) return "composite";
        if (local.startsWith("final")) return "final";
        if (local.startsWith("dh_")) return "distant-horizons";
        if (local.startsWith("clrwl_")) return "colorwheel";
        return "custom";
    }

    public record Pass(String name, String kind, List<String> drawBuffers) {}
}
