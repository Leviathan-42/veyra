package dev.blocktracker.shader;

import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.shader.VeyraIrisPipelineBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.vulkanmod.vulkan.framebuffer.RenderPass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class VeyraIrisRuntime {
    private static final Logger LOGGER = LogManager.getLogger("VeyraIrisRuntime");
    private static boolean initialized;
    private static IrisRenderPlan activeRenderPlan;
    private static boolean wroteExecutionReport;

    private VeyraIrisRuntime() {
    }

    public static void afterVulkanPipelinesInit() {
        IrisShaderPackLoader.LoadedPack pack = VeyraIrisCompat.activePack();
        if (pack == null) {
            VeyraIrisTargetManager.cleanUp();
            VeyraIrisPipelineBridge.cleanUp();
            initialized = false;
            return;
        }

        try {
            IrisRenderPlan renderPlan = IrisRenderPlan.from(pack);
            VeyraIrisTargetManager.AllocationResult allocation = VeyraIrisTargetManager.rebuild(renderPlan);
            VeyraIrisPipelineBridge.BuildResult result = VeyraIrisPipelineBridge.rebuildGraphicsPipelines(pack.presentPrograms(), renderPassesFor(renderPlan));
            initialized = result.failures().isEmpty();
            activeRenderPlan = renderPlan;
            wroteExecutionReport = false;
            appendRuntimeReport(allocation, result);
            LOGGER.info("Veyra Iris Vulkan module bridge built {}/{} graphics shader-module pairs for '{}'",
                    result.created(), result.attempted(), pack.displayName());
        } catch (Throwable throwable) {
            initialized = false;
            appendReport("\nVulkan runtime module bridge:\n- failed=" + throwable.getClass().getSimpleName() + ": " + throwable.getMessage() + "\n");
            LOGGER.error("Failed building Veyra Iris Vulkan shader-module bridge", throwable);
        }
    }

    private static Map<String, RenderPass> renderPassesFor(IrisRenderPlan plan) {
        Map<String, RenderPass> renderPasses = new HashMap<>();
        for (IrisRenderPlan.Pass pass : plan.passes()) {
            if (pass.drawBuffers().isEmpty()) {
                continue;
            }
            RenderPass renderPass = VeyraIrisTargetManager.colorPass("colortex" + pass.drawBuffers().get(0));
            if (renderPass != null) {
                renderPasses.put(pass.name(), renderPass);
            }
        }
        return renderPasses;
    }

    public static boolean initialized() {
        return initialized;
    }

    public static void executeAfterMainPass() {
        if (!initialized || activeRenderPlan == null || !Boolean.getBoolean("veyra.iris.executeGraph")) {
            return;
        }
        VeyraIrisPipelineBridge.ExecutionResult execution = VeyraIrisPipelineBridge.executeFullscreenPasses(activeRenderPlan.passes());
        if (!wroteExecutionReport) {
            wroteExecutionReport = true;
            appendReport("\nVulkan runtime execution bridge:\n- offscreenFullscreenPassesExecuted=" + execution.executed() + "\n- offscreenPassNames=" + String.join(",", execution.passNames()) + "\n- maxPasses=" + Integer.getInteger("veyra.iris.executeGraph.maxPasses", 64) + "\n- status=offscreen pass dispatch active; main scene/gbuffers capture next\n");
        }
    }

    private static void appendRuntimeReport(VeyraIrisTargetManager.AllocationResult allocation, VeyraIrisPipelineBridge.BuildResult result) {
        StringBuilder out = new StringBuilder();
        out.append("\nVulkan runtime resource bridge:\n");
        out.append("- size=").append(allocation.width()).append('x').append(allocation.height()).append('\n');
        out.append("- colorTargetsAllocated=").append(allocation.colorTargets()).append('\n');
        out.append("- depthTargetsAllocated=").append(allocation.depthTargets()).append('\n');
        out.append("- colorRenderPassesAllocated=").append(allocation.colorRenderPasses()).append('\n');
        out.append("\nVulkan runtime module bridge:\n");
        out.append("- attemptedGraphicsPairs=").append(result.attempted()).append('\n');
        out.append("- createdShaderModulePairs=").append(result.created()).append('\n');
        out.append("- createdVkGraphicsPipelines=").append(result.actualGraphicsPipelines()).append('\n');
        out.append("- failedGraphicsPairs=").append(result.failures().size()).append('\n');
        result.failures().stream().limit(24).forEach(failure -> out.append("- FAIL ").append(failure).append('\n'));
        out.append("- status=").append(result.failures().isEmpty() ? "resources and shader modules ready; draw dispatch binding next" : "partial").append('\n');
        appendReport(out.toString());
    }

    private static void appendReport(String text) {
        try {
            Path path = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config")
                    .resolve("veyra-iris-compat-report.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8, Files.exists(path)
                    ? java.nio.file.StandardOpenOption.APPEND
                    : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException exception) {
            LOGGER.warn("Failed appending Veyra Iris runtime report", exception);
        }
    }
}
