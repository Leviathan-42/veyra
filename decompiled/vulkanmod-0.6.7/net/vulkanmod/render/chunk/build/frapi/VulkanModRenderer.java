package net.vulkanmod.render.chunk.build.frapi;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.color.block.BlockColors;
import net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableMeshImpl;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.frapi.render.AltModelBlockRendererImpl;

public class VulkanModRenderer implements Renderer {
   public static final VulkanModRenderer INSTANCE = new VulkanModRenderer();
   public static AltModelBlockRendererImpl modelBlockRenderer;

   private VulkanModRenderer() {
   }

   static VulkanModRenderer getOrCreateInstance() {
      return INSTANCE;
   }

   public QuadEmitter quadEmitter(final Consumer<? super MutableQuadView> consumer) {
      return new MutableQuadViewImpl() {
         {
            this.data = new int[EncodingFormat.TOTAL_STRIDE];
            this.clear();
         }

         @Override
         protected void emitDirectly() {
            consumer.accept(this);
         }
      };
   }

   public MutableMesh mutableMesh() {
      return new MutableMeshImpl();
   }

   public AltModelBlockRenderer altModelBlockRenderer(boolean ambientOcclusion, boolean cull, BlockColors blockColors) {
      if (modelBlockRenderer == null) {
         modelBlockRenderer = new AltModelBlockRendererImpl(ambientOcclusion, cull, blockColors);
      }

      modelBlockRenderer.setup(ambientOcclusion, cull);
      return modelBlockRenderer;
   }
}
