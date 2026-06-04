package dev.blocktracker.shader;

import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VeyraIrisTargetManager {
    private static final Logger LOGGER = LogManager.getLogger("VeyraIrisTargetManager");
    private static final Map<String, Framebuffer> COLOR_TARGETS = new LinkedHashMap<>();
    private static final Map<String, Framebuffer> DEPTH_TARGETS = new LinkedHashMap<>();
    private static final Map<String, RenderPass> DEPTH_PASSES = new LinkedHashMap<>();
    private static final Map<String, RenderPass> COLOR_PASSES = new LinkedHashMap<>();
    private static final Map<String, RenderPass> COLOR_LOAD_PASSES = new LinkedHashMap<>();
    private static int width;
    private static int height;

    private VeyraIrisTargetManager() {
    }

    public static AllocationResult rebuild(IrisRenderPlan plan) {
        cleanUp();

        Minecraft client = Minecraft.getInstance();
        if (client.getWindow() != null) {
            width = Math.max(1, client.getWindow().getWidth());
            height = Math.max(1, client.getWindow().getHeight());
        } else {
            // VulkanMod initializes core pipelines before Minecraft's Window is
            // always visible through the client singleton during quick-play
            // startup. Allocate a safe default; resize integration will replace
            // this once we wire the full Iris render graph.
            width = 1920;
            height = 1080;
        }

        int colors = 0;
        int depths = 0;
        int passes = 0;

        for (String target : plan.colorTargets()) {
            try {
                Framebuffer framebuffer = Framebuffer.builder(width, height, 1, false).setLinearFiltering(true).build();
                RenderPass renderPass = RenderPass.builder(framebuffer).build();
                RenderPass loadRenderPass = RenderPass.builder(framebuffer).setLoadOp(0).build();
                COLOR_TARGETS.put(target, framebuffer);
                COLOR_PASSES.put(target, renderPass);
                COLOR_LOAD_PASSES.put(target, loadRenderPass);
                colors++;
                passes++;
            } catch (Throwable throwable) {
                LOGGER.warn("Failed allocating Iris color target {}", target, throwable);
            }
        }

        for (String target : plan.depthTargets()) {
            try {
                Framebuffer framebuffer = Framebuffer.builder(width, height, 0, true).setDepthLinearFiltering(false).build();
                RenderPass renderPass = RenderPass.builder(framebuffer).build();
                DEPTH_TARGETS.put(target, framebuffer);
                DEPTH_PASSES.put(target, renderPass);
                depths++;
            } catch (Throwable throwable) {
                LOGGER.warn("Failed allocating Iris depth target {}", target, throwable);
            }
        }

        bindSelectorSlots();
        return new AllocationResult(width, height, colors, depths, passes);
    }

    public static void bindSelectorSlots() {
        COLOR_TARGETS.forEach((name, framebuffer) -> {
            int idx = textureIndexFor(name);
            if (idx >= 0 && idx < 8 && framebuffer.getColorAttachment() != null) {
                VTextureSelector.bindTexture(idx, framebuffer.getColorAttachment());
            }
        });
        DEPTH_TARGETS.forEach((name, framebuffer) -> {
            int idx = textureIndexFor(name);
            if (idx >= 0 && idx < 12 && framebuffer.getDepthAttachment() != null) {
                VTextureSelector.bindTexture(idx, framebuffer.getDepthAttachment());
            }
        });
    }

    private static int textureIndexFor(String name) {
        if (name.startsWith("colortex")) {
            return parseTrailingIndex(name, 0);
        }
        if (name.startsWith("depthtex")) {
            return Math.min(10, 8 + parseTrailingIndex(name, 0));
        }
        if (name.startsWith("shadowtex")) {
            return 11;
        }
        return -1;
    }

    private static int parseTrailingIndex(String value, int fallback) {
        int i = value.length() - 1;
        while (i >= 0 && Character.isDigit(value.charAt(i))) i--;
        if (i == value.length() - 1) return fallback;
        try {
            return Integer.parseInt(value.substring(i + 1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static Framebuffer colorTarget(String name) {
        return COLOR_TARGETS.get(name);
    }

    public static RenderPass colorPass(String name) {
        return COLOR_PASSES.get(name);
    }

    public static RenderPass colorLoadPass(String name) {
        return COLOR_LOAD_PASSES.get(name);
    }

    public static Framebuffer depthTarget(String name) {
        return DEPTH_TARGETS.get(name);
    }

    public static RenderPass depthPass(String name) {
        return DEPTH_PASSES.get(name);
    }

    public static void cleanUp() {
        COLOR_PASSES.values().forEach(renderPass -> {
            try {
                renderPass.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris render pass", throwable);
            }
        });
        COLOR_PASSES.clear();

        COLOR_LOAD_PASSES.values().forEach(renderPass -> {
            try {
                renderPass.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris load render pass", throwable);
            }
        });
        COLOR_LOAD_PASSES.clear();

        DEPTH_PASSES.values().forEach(renderPass -> {
            try {
                renderPass.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris depth render pass", throwable);
            }
        });
        DEPTH_PASSES.clear();

        COLOR_TARGETS.values().forEach(framebuffer -> {
            try {
                framebuffer.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris color framebuffer", throwable);
            }
        });
        COLOR_TARGETS.clear();

        DEPTH_TARGETS.values().forEach(framebuffer -> {
            try {
                framebuffer.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris depth framebuffer", throwable);
            }
        });
        DEPTH_TARGETS.clear();
    }

    public record AllocationResult(int width, int height, int colorTargets, int depthTargets, int colorRenderPasses) {
    }
}
