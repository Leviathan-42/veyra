package net.vulkanmod.render.chunk.build.frapi.render;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.render.AltModelBlockRenderer;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.ArrayLightDataCache;
import net.vulkanmod.render.chunk.build.light.flat.FlatLightPipeline;
import net.vulkanmod.render.chunk.build.light.smooth.SmoothLightPipeline;
import net.vulkanmod.render.chunk.build.renderer.AbstractBlockRenderContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class AltModelBlockRendererImpl extends AbstractBlockRenderContext implements AltModelBlockRenderer, QuadTransform {
   private boolean ambientOcclusion;
   private boolean cull;
   private final Predicate<@Nullable Direction> cullTest = face -> !this.shouldRenderFace(face);
   private final RandomSource random = RandomSource.createThreadLocalInstance(0L);
   private final Vector3f offset = new Vector3f();
   private final ArrayLightDataCache lightDataCache = new ArrayLightDataCache();

   public AltModelBlockRendererImpl(boolean ambientOcclusion, boolean cull, BlockColors blockColors) {
      this.ambientOcclusion = ambientOcclusion;
      this.cull = cull;
      LightPipeline flatLightPipeline = new FlatLightPipeline(this.lightDataCache);
      LightPipeline smoothLightPipeline = new SmoothLightPipeline(this.lightDataCache);
      this.setupLightPipelines(flatLightPipeline, smoothLightPipeline);
   }

   public void setup(boolean ambientOcclusion, boolean cull) {
      this.ambientOcclusion = ambientOcclusion;
      this.cull = cull;
   }

   public void tesselateBlock(
      QuadEmitter output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos, BlockState blockState, BlockStateModel model, long seed
   ) {
      Vec3 offset = blockState.getOffset(pos);
      this.offset.set(x + offset.x, y + offset.y, z + offset.z);
      this.blockPos = pos;
      this.blockState = blockState;
      this.defaultAO = this.ambientOcclusion && blockState.getLightEmission() == 0;
      this.prepareForWorld(level, false);
      this.resetTintCache();
      this.lightDataCache.reset(level, pos);
      this.random.setSeed(seed);
      output.clear();
      output.pushTransform(this);

      try {
         model.emitQuads(output, level, pos, blockState, this.random, this.cull ? this.cullTest : var0 -> false);
      } finally {
         output.popTransform();
      }
   }

   public boolean transform(MutableQuadView quad) {
      if (this.cull && !this.shouldRenderFace(quad.cullFace())) {
         return false;
      }

      boolean ao = this.ambientOcclusion && quad.ambientOcclusion().orElse(this.defaultAO);
      boolean emissive = quad.emissive();
      boolean vanillaShade = quad.shadeMode() == ShadeMode.VANILLA;
      LightPipeline lightPipeline = ao ? this.smoothLightPipeline : this.flatLightPipeline;
      this.shadeQuad((MutableQuadViewImpl)quad, lightPipeline, emissive, vanillaShade);
      this.tintQuad(quad);
      quad.translate(this.offset.x, this.offset.y, this.offset.z);
      return true;
   }
}
