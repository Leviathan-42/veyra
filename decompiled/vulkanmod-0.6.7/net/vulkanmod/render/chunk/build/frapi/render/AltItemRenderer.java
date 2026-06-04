package net.vulkanmod.render.chunk.build.frapi.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricSubmitNodeCollection.ExtendedItemSubmit;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.vulkanmod.mixin.render.frapi.ItemFeatureRendererAccessor;
import net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import org.jspecify.annotations.Nullable;

public class AltItemRenderer {
   private final MutableQuadViewImpl emitter = new MutableQuadViewImpl() {
      {
         this.data = new int[EncodingFormat.TOTAL_STRIDE];
         this.clear();
      }

      @Override
      protected void emitDirectly() {
         AltItemRenderer.this.bufferQuad(this);
      }
   };
   private MultiBufferSource bufferSource;
   private OutlineBufferSource outlineBufferSource;
   private boolean translucent;
   private ExtendedItemSubmit submit;
   private @Nullable Pose foilDecalPose;

   public void prepare(BufferSource bufferSource, OutlineBufferSource outlineBufferSource, boolean translucent) {
      this.bufferSource = bufferSource;
      this.outlineBufferSource = outlineBufferSource;
      this.translucent = translucent;
   }

   public void clear() {
      this.bufferSource = null;
      this.outlineBufferSource = null;
   }

   public void renderItem(ExtendedItemSubmit submit) {
      this.submit = submit;
      if (submit.outlineColor() != 0) {
         this.outlineBufferSource.setColor(submit.outlineColor());
      }

      this.bufferQuads(submit.quads(), submit.mesh());
      this.foilDecalPose = null;
   }

   private void bufferQuads(List<BakedQuad> vanillaQuads, MeshView mesh) {
      QuadEmitter emitter = this.emitter;
      emitter.clear();

      for (int i = 0; i < vanillaQuads.size(); i++) {
         BakedQuad q = vanillaQuads.get(i);
         emitter.fromBakedQuad(q);
         emitter.emit();
      }

      mesh.outputTo(emitter);
   }

   private void bufferQuad(MutableQuadViewImpl quad) {
      RenderType renderType = quad.itemRenderType();
      if (renderType.hasBlending() == this.translucent) {
         this.shadeQuad(quad, quad.emissive());
         this.tintQuad(quad);
         ExtendedItemSubmit submit = this.submit;
         FoilType foilType = quad.foilType() == null ? submit.foilType() : quad.foilType();
         if (foilType != FoilType.NONE) {
            Pose foilDecalPose;
            if (foilType == FoilType.SPECIAL) {
               if (this.foilDecalPose == null) {
                  this.foilDecalPose = ItemFeatureRendererAccessor.fabric_computeFoilDecalPose(submit.displayContext(), submit.pose());
               }

               foilDecalPose = this.foilDecalPose;
            } else {
               foilDecalPose = null;
            }

            VertexConsumer foilBuffer = ItemFeatureRendererAccessor.fabric_getFoilBuffer(this.bufferSource, renderType, foilDecalPose);
            quad.buffer(submit.overlayCoords(), submit.pose(), foilBuffer);
         }

         if (submit.outlineColor() != 0) {
            quad.buffer(submit.overlayCoords(), submit.pose(), this.outlineBufferSource.getBuffer(renderType));
         }

         quad.buffer(submit.overlayCoords(), submit.pose(), this.bufferSource.getBuffer(renderType));
      }
   }

   private void shadeQuad(MutableQuadViewImpl quad, boolean emissive) {
      if (emissive) {
         quad.lightmap(15728880, 15728880, 15728880, 15728880);
      } else {
         quad.minLightmap(this.submit.lightCoords());
      }
   }

   private void tintQuad(MutableQuadViewImpl quad) {
      int tintIndex = quad.tintIndex();
      if (tintIndex >= 0 && tintIndex < this.submit.tintLayers().length) {
         quad.multiplyColor(this.submit.tintLayers()[tintIndex]);
      }
   }
}
