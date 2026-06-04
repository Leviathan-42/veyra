package net.vulkanmod.render.shader;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;

public class CustomRenderPipelines {
   public static final List<RenderPipeline> pipelines = new ArrayList<>();
   public static final Snippet GUI_TRIANGLES_SNIPPET = RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_PROJECTION_SNIPPET})
      .withVertexShader("core/gui")
      .withFragmentShader("core/gui")
      .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
      .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, Mode.TRIANGLES)
      .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
      .buildSnippet();
   public static final RenderPipeline GUI_TRIANGLES = register(
      RenderPipeline.builder(new Snippet[]{GUI_TRIANGLES_SNIPPET}).withLocation("pipeline/gui").build()
   );

   static RenderPipeline register(RenderPipeline pipeline) {
      pipelines.add(pipeline);
      return pipeline;
   }
}
