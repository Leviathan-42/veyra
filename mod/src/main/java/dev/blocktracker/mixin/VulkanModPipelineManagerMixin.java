package dev.blocktracker.mixin;

import dev.blocktracker.shader.VeyraIrisRuntime;
import net.vulkanmod.render.shader.PipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PipelineManager.class, remap = false)
public abstract class VulkanModPipelineManagerMixin {
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private static void veyra$afterVulkanPipelinesInit(CallbackInfo ci) {
        VeyraIrisRuntime.afterVulkanPipelinesInit();
    }
}
