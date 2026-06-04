package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.profiling.Profiler;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
   @Inject(method = "lambda$addCloudsPass$0", at = @At("HEAD"))
   private void pushProfiler(
      int cloudColor, CloudStatus cloudStatus, float cloudHeight, int cloudRange, Vec3 cameraPosition, long gameTime, float partialTicks, CallbackInfo ci
   ) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.push("Clouds");
   }

   @Inject(method = "lambda$addCloudsPass$0", at = @At("RETURN"))
   private void popProfiler(
      int cloudColor, CloudStatus cloudStatus, float cloudHeight, int cloudRange, Vec3 cameraPosition, long gameTime, float partialTicks, CallbackInfo ci
   ) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.pop();
   }

   @Inject(
      method = "lambda$addMainPass$0",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeStorage;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
      )
   )
   private void pushProfiler3(
      GpuBufferSlice terrainFog,
      LevelRenderState levelRenderState,
      ProfilerFiller profilerFiller,
      ChunkSectionsToRender chunkSectionsToRender,
      ResourceHandle entityOutlineTarget,
      ResourceHandle translucentTarget,
      ResourceHandle mainTarget,
      ResourceHandle itemEntityTarget,
      ResourceHandle particleTarget,
      boolean renderOutline,
      Matrix4fc modelViewMatrix,
      CallbackInfo ci
   ) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.push("Particles");
   }

   @Inject(method = "lambda$addMainPass$0", at = @At("RETURN"))
   private void popProfiler3(
      GpuBufferSlice terrainFog,
      LevelRenderState levelRenderState,
      ProfilerFiller profilerFiller,
      ChunkSectionsToRender chunkSectionsToRender,
      ResourceHandle entityOutlineTarget,
      ResourceHandle translucentTarget,
      ResourceHandle mainTarget,
      ResourceHandle itemEntityTarget,
      ResourceHandle particleTarget,
      boolean renderOutline,
      Matrix4fc modelViewMatrix,
      CallbackInfo ci
   ) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.pop();
   }

   @Inject(method = "submitEntities", at = @At("HEAD"))
   private void profilerTerrain2(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
      Profiler profiler = Profiler.getMainProfiler();
      profiler.pop();
      profiler.push("entities");
   }
}
