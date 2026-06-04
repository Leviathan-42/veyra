package net.vulkanmod.mixin.render.depth;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public class CameraMixin {
   @Shadow
   private float depthFar;

   @Redirect(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = 181))
   private void infiniteZFar(Camera instance, float value) {
      this.depthFar = Float.POSITIVE_INFINITY;
   }
}
