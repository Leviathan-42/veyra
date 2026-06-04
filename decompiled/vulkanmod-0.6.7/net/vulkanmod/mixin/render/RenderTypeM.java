package net.vulkanmod.mixin.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup.TextureAndSampler;
import net.vulkanmod.interfaces.ExtendedRenderType;
import net.vulkanmod.render.engine.VkCommandEncoder;
import net.vulkanmod.render.engine.VkRenderPass;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class RenderTypeM implements ExtendedRenderType {
   @Unique
   TerrainRenderType terrainRenderType;
   @Shadow
   @Final
   private RenderSetup state;
   @Shadow
   @Final
   protected String name;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void inj(String string, RenderSetup renderSetup, CallbackInfo ci) {
      this.terrainRenderType = switch (string) {
         case "solid" -> TerrainRenderType.SOLID;
         case "cutout" -> TerrainRenderType.CUTOUT;
         case "translucent" -> TerrainRenderType.TRANSLUCENT;
         case "tripwire" -> TerrainRenderType.TRIPWIRE;
         default -> null;
      };
   }

   @Override
   public TerrainRenderType getTerrainRenderType() {
      return this.terrainRenderType;
   }

   @Overwrite
   public void draw(MeshData meshData) {
      Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
      RenderSetupAccessor renderSetupAccessor = (RenderSetupAccessor)this.state;
      Consumer<Matrix4fStack> consumer = renderSetupAccessor.layeringTransform().getModifier();
      if (consumer != null) {
         matrix4fStack.pushMatrix();
         consumer.accept(matrix4fStack);
      }

      GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
         .writeTransform(
            RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), renderSetupAccessor.textureTransform().getMatrix()
         );
      Map<String, TextureAndSampler> map = this.state.getTextures();
      GpuBuffer gpuBuffer = renderSetupAccessor.pipeline().getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
      if (meshData.indexBuffer() == null) {
         AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
         GpuBuffer gpuBuffer2 = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
         IndexType indexType = autoStorageIndexBuffer.type();
      } else {
         GpuBuffer gpuBuffer2 = renderSetupAccessor.pipeline().getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
         IndexType indexType = meshData.drawState().indexType();
      }

      RenderTarget renderTarget = renderSetupAccessor.outputTarget().getRenderTarget();
      GpuTextureView gpuTextureView = RenderSystem.outputColorTextureOverride != null
         ? RenderSystem.outputColorTextureOverride
         : renderTarget.getColorTextureView();
      GpuTextureView gpuTextureView2 = renderTarget.useDepth
         ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
         : null;
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Immediate draw for " + this.name, gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty());

      try {
         renderPass.setPipeline(renderSetupAccessor.pipeline());
         ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
         if (scissorState.enabled()) {
            renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
         }

         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
         renderPass.setVertexBuffer(0, gpuBuffer);

         for (Entry<String, TextureAndSampler> entry : map.entrySet()) {
            renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
         }

         VRenderSystem.applyModelViewMatrix(RenderSystem.getModelViewMatrix());
         VRenderSystem.calculateMVP();
         VkCommandEncoder commandEncoder = (VkCommandEncoder)RenderSystem.getDevice().createCommandEncoder().backend;
         commandEncoder.trySetup((VkRenderPass)renderPass.backend);
         Renderer.getDrawer()
            .draw(
               meshData.vertexBuffer(), meshData.indexBuffer(), meshData.drawState().mode(), meshData.drawState().format(), meshData.drawState().vertexCount()
            );
      } catch (Throwable var18) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var17) {
               var18.addSuppressed(var17);
            }
         }

         throw var18;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      if (meshData != null) {
         meshData.close();
      }

      if (consumer != null) {
         matrix4fStack.popMatrix();
      }
   }
}
