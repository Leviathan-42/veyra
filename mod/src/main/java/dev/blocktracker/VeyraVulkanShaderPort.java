package dev.blocktracker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tiny first-step Vulkan shaderpack compatibility shim.
 *
 * This is not a full Iris pipeline. It makes selected Iris-style packs visibly do
 * something under VulkanMod by grading VulkanMod's native terrain/cloud shaders.
 * Full Solas/Complementary/Photon support still needs render-pass, framebuffer,
 * shadow, and uniform emulation.
 */
public final class VeyraVulkanShaderPort {
    private static final Logger LOGGER = LogManager.getLogger("VeyraVulkanShaderPort");
    private static final Set<String> PATCHED_LOGS = new HashSet<>();

    private VeyraVulkanShaderPort() {
    }

    public static String patchShader(String shaderName, String source) {
        if (source == null || shaderName == null || !VeyraShaderPackManager.isEnabled()) {
            return source;
        }

        // Practical Vulkan shaderpack path: keep VulkanMod's fast native renderer
        // and inject the selected pack's visible post/lighting style while the
        // full Iris gbuffers/shadow bridge is being completed. Disable with
        // -Dveyra.disablePracticalShaderPort=true.
        if (Boolean.getBoolean("veyra.disablePracticalShaderPort")) {
            return source;
        }

        String lowerName = shaderName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".fsh")) {
            return source;
        }

        String selectedPack = VeyraShaderPackManager.selectedPack();
        String pack = selectedPack.toLowerCase(Locale.ROOT);
        String function = gradeFunction(pack);
        String patched = source;

        if (lowerName.endsWith("terrain.fsh")) {
            patched = patchTerrain(source, function);
        } else if (lowerName.endsWith("clouds.fsh")) {
            patched = patchClouds(source, function);
        }
        // Do not patch final blit or early-Z. Final blit mistakes affect the whole
        // screen (including inverted/black output), and early-Z should stay depth-only.

        if (patched != source) {
            logPatched(selectedPack, shaderName);
        }

        return patched;
    }

    private static String patchTerrain(String source, String function) {
        if (source.contains("veyra_port_grade")) {
            return source;
        }

        String patched = injectBeforeMain(source, function);
        patched = patched.replace(
                "color = mix(FogColor * vec4(1, 1, 1, color.a), color, fadeFactor);",
                "color = veyra_port_grade(color);\n    color = mix(FogColor * vec4(1, 1, 1, color.a), color, fadeFactor);"
        );
        patched = patched.replace(
                "fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);",
                "fragColor = veyra_port_grade(apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor));"
        );
        return patched;
    }

    private static String patchClouds(String source, String function) {
        if (source.contains("veyra_port_grade")) {
            return source;
        }

        String patched = injectBeforeMain(source, function);
        patched = patched.replace(
                "fragColor = vec4(color.rgb, color.a);",
                "fragColor = veyra_port_grade(vec4(color.rgb, color.a));"
        );
        return patched;
    }

    private static String patchFinalBlit(String source, String function) {
        if (source.contains("veyra_port_grade")) {
            return source;
        }

        String patched = injectBeforeMain(source, function + finalPortHelpers());
        patched = patched.replace(
                "fragColor = color;",
                "fragColor = veyra_port_final_grade(color, texCoord);"
        );
        return patched;
    }

    private static String injectBeforeMain(String source, String function) {
        int mainIndex = source.indexOf("void main()");
        if (mainIndex < 0) {
            return source;
        }
        return source.substring(0, mainIndex) + function + "\n" + source.substring(mainIndex);
    }

    private static void logPatched(String selectedPack, String shaderName) {
        String key = selectedPack + ":" + shaderName;
        if (PATCHED_LOGS.add(key)) {
            LOGGER.info("Applying experimental Vulkan shaderpack port '{}' to VulkanMod shader {}", selectedPack, shaderName);
        }
    }

    private static String finalPortHelpers() {
        return """

vec4 veyra_port_final_grade(vec4 color, vec2 uv) {
    vec4 graded = veyra_port_grade(color);

    // Iris-style approximation passes that VulkanMod does not expose yet:
    // a little vignette, fake aerial contrast, and highlight bloom. This is
    // intentionally strong so selected packs are visibly different.
    vec2 centered = uv * 2.0 - 1.0;
    float vignette = smoothstep(1.45, 0.20, dot(centered, centered));
    vec3 highlight = smoothstep(vec3(0.62), vec3(1.0), graded.rgb) * vec3(0.18, 0.13, 0.08);
    graded.rgb = graded.rgb * mix(0.72, 1.08, vignette) + highlight;
    graded.rgb = clamp(graded.rgb, 0.0, 1.0);
    return vec4(graded.rgb, color.a);
}
""";
    }

    private static String gradeFunction(String pack) {
        if (pack.contains("photon")) {
            return """
vec4 veyra_port_grade(vec4 color) {
    vec3 c = color.rgb;
    c = pow(max(c, vec3(0.0)), vec3(0.72));
    c *= vec3(1.00, 1.12, 1.34);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, 1.36);
    c += smoothstep(vec3(0.48), vec3(1.0), c) * vec3(0.10, 0.16, 0.28);
    c = c / (c + vec3(0.08));
    c *= 1.22;
    return vec4(clamp(c, 0.0, 1.0), color.a);
}
""";
        }

        if (pack.contains("complementary")) {
            return """
vec4 veyra_port_grade(vec4 color) {
    vec3 c = color.rgb;
    c = pow(max(c, vec3(0.0)), vec3(0.70));
    c *= vec3(1.28, 1.12, 0.94);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, 1.30);
    c += smoothstep(vec3(0.50), vec3(1.0), c) * vec3(0.18, 0.11, 0.04);
    c = c / (c + vec3(0.10));
    c *= 1.22;
    return vec4(clamp(c, 0.0, 1.0), color.a);
}
""";
        }

        // Solas / generic warm cinematic look.
        return """
vec4 veyra_port_grade(vec4 color) {
    vec3 c = color.rgb;
    c = pow(max(c, vec3(0.0)), vec3(0.82));
    c *= vec3(1.12, 1.05, 0.92);
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, 1.16);
    c += smoothstep(vec3(0.72), vec3(1.0), c) * vec3(0.08, 0.05, 0.02);
    return vec4(clamp(c, 0.0, 1.0), color.a);
}
""";
    }
}
