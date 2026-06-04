package net.vulkanmod.render.chunk.build.renderer;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import net.vulkanmod.render.chunk.build.light.LightPipeline;
import net.vulkanmod.render.chunk.build.light.data.QuadLightData;
import net.vulkanmod.render.chunk.build.thread.BuilderResources;
import net.vulkanmod.render.chunk.cull.QuadFacing;
import net.vulkanmod.render.model.quad.ModelQuadView;
import net.vulkanmod.render.model.quad.QuadUtils;
import net.vulkanmod.render.vertex.TerrainBufferBuilder;
import net.vulkanmod.render.vertex.TerrainBuilder;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Vector3f;

public class BlockRenderer extends AbstractBlockRenderContext {
   private Vector3f pos;
   private BuilderResources resources;
   BlockStateModelSet blockModels;
   private TerrainBuilder terrainBuilder;
   final boolean backFaceCulling;
   private TerrainRenderType renderType;

   public void setResources(BuilderResources resources) {
      this.resources = resources;
   }

   public BlockRenderer(LightPipeline flatLightPipeline, LightPipeline smoothLightPipeline) {
      this.backFaceCulling = Initializer.CONFIG.backFaceCulling;
      this.setupLightPipelines(flatLightPipeline, smoothLightPipeline);
      this.random = new SingleThreadedRandomSource(42L);
      this.blockModels = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
   }

   public void renderBlock(BlockState blockState, BlockPos blockPos, Vector3f pos) {
      this.pos = pos;
      this.blockPos = blockPos;
      this.blockState = blockState;
      this.random.setSeed(blockState.getSeed(blockPos));
      this.resetTintCache();
      BlockStateModel model = this.blockModels.get(blockState);
      this.prepareForBlock(blockState, blockPos, true);
      BlockAndTintGetter renderRegion = this.renderRegion;
      Vec3 offset = blockState.getOffset(blockPos);
      pos.add((float)offset.x, (float)offset.y, (float)offset.z);
      model.emitQuads(this.getEmitter(), renderRegion, blockPos, blockState, this.random, this::isFaceCulled);
   }

   @Override
   protected void endRenderQuad(MutableQuadViewImpl quad) {
      TriState aoMode = quad.ambientOcclusion();
      boolean ao = this.useAO && (aoMode == TriState.TRUE || aoMode == TriState.DEFAULT && this.defaultAO);
      boolean emissive = quad.emissive();
      boolean vanillaShade = quad.shadeMode() == ShadeMode.VANILLA;
      TerrainRenderType renderType = TerrainRenderType.get(quad.chunkLayer());
      this.renderType = TerrainRenderType.getRemapped(renderType);
      TerrainBuilder terrainBuilder = this.getBufferBuilder(quad.chunkLayer());
      terrainBuilder.setBlockAttributes(this.blockState);
      LightPipeline lightPipeline = ao ? this.smoothLightPipeline : this.flatLightPipeline;
      this.tintQuad(quad);
      this.shadeQuad(quad, lightPipeline, emissive, vanillaShade);
      this.bufferQuad(terrainBuilder, this.pos, quad, this.quadLightData);
   }

   private TerrainBuilder getBufferBuilder(ChunkSectionLayer layer) {
      if (layer == null) {
         return this.terrainBuilder;
      }

      TerrainRenderType renderType = TerrainRenderType.get(layer);
      renderType = TerrainRenderType.getRemapped(renderType);
      TerrainBuilder bufferBuilder = this.resources.builderPack.builder(renderType);
      bufferBuilder.setBlockAttributes(this.blockState);
      return bufferBuilder;
   }

   public void bufferQuad(TerrainBuilder terrainBuilder, Vector3f pos, ModelQuadView quad, QuadLightData quadLightData) {
      QuadFacing quadFacing = quad.getQuadFacing();
      if (this.renderType == TerrainRenderType.TRANSLUCENT || !this.backFaceCulling) {
         quadFacing = QuadFacing.UNDEFINED;
      }

      TerrainBufferBuilder bufferBuilder = terrainBuilder.getBufferBuilder(quadFacing.ordinal());
      int packedNormal = quad.getNormal();
      float[] brightnessArr = quadLightData.br;
      int[] lights = quadLightData.lm;
      int idx = QuadUtils.getIterationStartIdx(brightnessArr, lights);
      bufferBuilder.ensureCapacity();

      for (byte i = 0; i < 4; i++) {
         float x = pos.x() + quad.getX(idx);
         float y = pos.y() + quad.getY(idx);
         float z = pos.z() + quad.getZ(idx);
         int quadColor = quad.getColor(idx);
         int color = ColorUtil.ARGB.toRGBA(quadColor);
         int light = lights[idx];
         float u = quad.getU(idx);
         float v = quad.getV(idx);
         bufferBuilder.vertex(x, y, z, color, u, v, light, packedNormal);
         idx = idx + 1 & 3;
      }
   }
}
