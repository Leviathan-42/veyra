package net.vulkanmod.mixin.render.frame;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.vulkanmod.vulkan.Renderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
   @Inject(method = "runTick", at = @At("HEAD"))
   private void preFrameOps(boolean advanceGameTime, CallbackInfo ci) {
      Renderer.getInstance().beginFrame();
      Renderer.clearAttachments(16640);
   }

   @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("RETURN"))
   private void beginRender2(CallbackInfo ci) {
   }

   @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))
   private void removeBlit(RenderTarget instance) {
   }
}
