package net.vulkanmod.interfaces.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.vulkanmod.render.engine.EGlProgram;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;

public interface ExtendedRenderPipeline {
   static ExtendedRenderPipeline of(RenderPipeline renderPipeline) {
      return (ExtendedRenderPipeline)renderPipeline;
   }

   void setPipeline(GraphicsPipeline var1);

   void setProgram(EGlProgram var1);

   GraphicsPipeline getPipeline();

   EGlProgram getProgram();
}
