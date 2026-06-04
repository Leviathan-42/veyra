package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.gui.render.GuiRenderer;
import net.vulkanmod.render.engine.VkRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
   @Redirect(
      method = "executeDraw",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/RenderPass;setIndexBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V"
      )
   )
   private void removeIndexBuffer(RenderPass instance, GpuBuffer indexBuffer, IndexType indexType) {
   }

   @Redirect(method = "executeDraw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
   private void useVertexCount(RenderPass renderPass, int baseVertex, int firstIndex, int indexCount, int instanceCount) {
      VkRenderPass vkRenderPass = (VkRenderPass)renderPass.backend;
      if (vkRenderPass.getPipeline().getVertexFormatMode() != Mode.TRIANGLES) {
         int vertexCount = indexCount * 2 / 3;
         renderPass.drawIndexed(baseVertex, 0, vertexCount, 1);
      } else {
         renderPass.drawIndexed(baseVertex, 0, indexCount, 1);
      }
   }
}
