package net.vulkanmod.mixin.render.frapi;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemFeatureRenderer.class)
public interface ItemFeatureRendererAccessor {
   @Invoker("getFoilBuffer")
   static VertexConsumer fabric_getFoilBuffer(MultiBufferSource bufferSource, RenderType renderType, @Nullable Pose foilDecalPose) {
      throw new AssertionError();
   }

   @Invoker("computeFoilDecalPose")
   static Pose fabric_computeFoilDecalPose(ItemDisplayContext type, Pose pose) {
      throw new AssertionError();
   }
}
