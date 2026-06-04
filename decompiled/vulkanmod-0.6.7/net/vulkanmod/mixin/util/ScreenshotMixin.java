package net.vulkanmod.mixin.util;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.vulkanmod.vulkan.util.ScreenshotUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Screenshot.class)
public class ScreenshotMixin {
   @Overwrite
   public static void takeScreenshot(RenderTarget renderTarget, int mipLevel, Consumer<NativeImage> consumer) {
      ScreenshotUtil.takeScreenshot(renderTarget, mipLevel, consumer);
   }
}
