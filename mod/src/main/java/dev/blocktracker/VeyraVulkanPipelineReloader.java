package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.vulkanmod.render.shader.PipelineManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class VeyraVulkanPipelineReloader {
    private static final Logger LOGGER = LogManager.getLogger("VeyraVulkanPipelineReloader");
    private static boolean reloading;

    private VeyraVulkanPipelineReloader() {
    }

    public static void reloadNow() {
        if (reloading) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (reloading) {
                return;
            }

            reloading = true;
            try {
                LOGGER.info("Reloading VulkanMod pipelines for selected shaderpack '{}'", VeyraShaderPackManager.selectedPack());
                PipelineManager.destroyPipelines();
                PipelineManager.init();

                if (client.levelRenderer != null) {
                    client.levelRenderer.allChanged();
                }
            } catch (Throwable throwable) {
                LOGGER.error("Failed to hot-reload VulkanMod pipelines. Restart Minecraft to apply shader changes.", throwable);
            } finally {
                reloading = false;
            }
        });
    }
}
