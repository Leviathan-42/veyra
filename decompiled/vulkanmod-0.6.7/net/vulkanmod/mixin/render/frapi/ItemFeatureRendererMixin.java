package net.vulkanmod.mixin.render.frapi;

import net.fabricmc.fabric.api.client.renderer.v1.render.FabricSubmitNodeCollection.ExtendedItemSubmit;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.vulkanmod.render.chunk.build.frapi.render.AltItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
abstract class ItemFeatureRendererMixin {
   @Unique
   private final AltItemRenderer altItemRenderer = new AltItemRenderer();

   @Inject(method = "renderSolid", at = @At("RETURN"))
   private void onReturnRenderSolid(SubmitNodeCollection nodeCollection, BufferSource bufferSource, OutlineBufferSource outlineBufferSource, CallbackInfo ci) {
      this.altItemRenderer.prepare(bufferSource, outlineBufferSource, false);

      for (ExtendedItemSubmit submit : nodeCollection.getExtendedItemSubmits()) {
         this.altItemRenderer.renderItem(submit);
      }

      this.altItemRenderer.clear();
   }

   @Inject(method = "renderTranslucent", at = @At("RETURN"))
   private void onReturnRenderTranslucent(
      SubmitNodeCollection nodeCollection, BufferSource bufferSource, OutlineBufferSource outlineBufferSource, CallbackInfo ci
   ) {
      this.altItemRenderer.prepare(bufferSource, outlineBufferSource, true);

      for (ExtendedItemSubmit submit : nodeCollection.getExtendedItemSubmits()) {
         this.altItemRenderer.renderItem(submit);
      }

      this.altItemRenderer.clear();
   }
}
