package net.vulkanmod.mixin.texture.update;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Lightmap.class)
public class MLightTexture {
   @Unique
   private static final Vector3f END_FLASH_SKY_LIGHT_COLOR = new Vector3f(0.9F, 0.5F, 1.0F);
   @Unique
   private DynamicTexture lightTexture;
   @Unique
   private GpuTextureView textureView;
   @Unique
   private NativeImage lightPixels;
   private Vector3f[] tempVecs;

   @Unique
   private float calculateDarknessScale(LivingEntity livingEntity, float f, float g) {
      float h = 0.45F * f;
      return Math.max(0.0F, Mth.cos((livingEntity.tickCount - g) * (float) Math.PI * 0.025F) * h);
   }

   @Unique
   private static float lerp(float a, float x, float t) {
      return (x - a) * t + a;
   }

   @Unique
   private static void clampColor(Vector3f vector3f) {
      vector3f.set(Mth.clamp(vector3f.x, 0.0F, 1.0F), Mth.clamp(vector3f.y, 0.0F, 1.0F), Mth.clamp(vector3f.z, 0.0F, 1.0F));
   }

   @Unique
   private float notGamma(float f) {
      float g = 1.0F - f;
      g *= g;
      return 1.0F - g * g;
   }

   @Unique
   private static float getBrightness(float ambientLight, int i) {
      float f = i / 15.0F;
      float g = f / (4.0F - 3.0F * f);
      return Mth.lerp(ambientLight, g, 1.0F);
   }
}
