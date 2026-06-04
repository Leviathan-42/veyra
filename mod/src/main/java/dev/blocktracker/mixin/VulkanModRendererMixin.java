package dev.blocktracker.mixin;

import dev.blocktracker.shader.VeyraIrisRuntime;
import net.vulkanmod.vulkan.pass.DefaultMainPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultMainPass.class, remap = false)
public abstract class VulkanModRendererMixin {
    @Inject(
            method = "end",
            at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/Renderer;endRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;)V", shift = At.Shift.AFTER),
            remap = false
    )
    private void veyra$executeIrisBeforeCommandBufferEnd(CallbackInfo ci) {
        VeyraIrisRuntime.executeAfterMainPass();
    }
}
