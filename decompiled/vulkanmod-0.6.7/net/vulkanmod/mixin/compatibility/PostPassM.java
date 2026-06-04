package net.vulkanmod.mixin.compatibility;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.PostPass.Input;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public abstract class PostPassM {
   @Shadow
   @Final
   private List<Input> inputs;

   @Inject(
      method = "lambda$addToFrame$1",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createCommandEncoder()Lcom/mojang/blaze3d/systems/CommandEncoder;")
   )
   private void transitionLayouts(ResourceHandle outputHandle, GpuBufferSlice shaderOrthoMatrix, Map targets, CallbackInfo ci) {
      Renderer.getInstance().endRenderPass();

      for (Input input : this.inputs) {
         VkGpuTexture gpuTexture = (VkGpuTexture)input.texture(targets).texture();
         if (gpuTexture.needsClear()) {
            gpuTexture.getFbo(null).bind();
         }

         gpuTexture.getVulkanImage().readOnlyLayout();
      }
   }
}
