package net.vulkanmod.mixin.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
   @Inject(method = "setupFog", at = @At("RETURN"))
   private void onSetupFog(
      Camera camera,
      int renderDistanceInChunks,
      DeltaTracker deltaTracker,
      float darkenWorldAmount,
      ClientLevel level,
      CallbackInfoReturnable<Vector4f> cir,
      @Local(name = "fog") FogData fogData
   ) {
      VRenderSystem.fogData = fogData;
      Vector4f fogColor = fogData.color;
      VRenderSystem.setShaderFogColor(fogColor.x(), fogColor.y(), fogColor.z(), fogColor.w());
   }
}
