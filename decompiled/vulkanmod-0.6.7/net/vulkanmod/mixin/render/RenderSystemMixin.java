package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import net.minecraft.client.renderer.DynamicUniforms;
import net.vulkanmod.vulkan.Renderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
   @Shadow
   @Nullable
   private static Thread renderThread;
   @Shadow
   @Nullable
   private static GpuDevice DEVICE;
   @Shadow
   @Nullable
   private static DynamicUniforms dynamicUniforms;
   @Shadow
   private static SamplerCache samplerCache;
   @Shadow
   private static String apiDescription;

   @Overwrite
   public static void initRenderer(GpuDevice device) {
      if (DEVICE != null) {
         throw new IllegalStateException("RenderSystem.DEVICE already initialized");
      }

      DEVICE = device;
      Renderer.initRenderer();
      apiDescription = RenderSystem.getDevice().getImplementationInformation();
      dynamicUniforms = new DynamicUniforms();
      samplerCache.initialize();
   }
}
