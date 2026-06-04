package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(CommandEncoder.class)
public class CommandEncoderMixin {
   @Overwrite
   private void verifyColorTexture(GpuTexture colorTexture) {
   }

   @Overwrite
   private void verifyDepthTexture(GpuTexture depthTexture) {
   }
}
