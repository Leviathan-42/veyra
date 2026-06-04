package dev.blocktracker.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.blocktracker.shader.VeyraIrisRuntime;
import dev.blocktracker.shader.VeyraIrisTargetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.ChunkArea;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.WorldRenderer;
import net.vulkanmod.render.chunk.buffer.DrawBuffers;
import net.vulkanmod.render.chunk.graph.SectionGraph;
import net.vulkanmod.render.chunk.util.StaticQueue;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.memory.buffer.IndexBuffer;
import net.vulkanmod.vulkan.memory.buffer.IndirectBuffer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.VeyraIrisPipelineBridge;
import net.vulkanmod.vulkan.texture.SamplerManager;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Set;

@Mixin(value = WorldRenderer.class, remap = false)
public abstract class VulkanModWorldRendererMixin {
    @Shadow private Minecraft minecraft;
    @Shadow private SectionGraph sectionGraph;
    @Shadow IndirectBuffer[] indirectBuffers;
    @Shadow private final Vector3d cameraPos = new Vector3d();
    @Shadow private long terrainSampler;
    @Unique private static long veyra$lastTerrainCaptureNanos;
    @Unique private static final java.util.Set<String> veyra$clearedTerrainTargets = new java.util.HashSet<>();

    @Inject(method = "renderSectionLayer", at = @At("TAIL"), remap = false)
    private void veyra$captureTerrainLayerToIris(TerrainRenderType renderType, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!VeyraIrisRuntime.initialized() || Boolean.getBoolean("veyra.iris.disableTerrainCapture")) {
            return;
        }
        veyra$replayTerrainLayerToTarget(renderType, camX, camY, camZ, modelView, projection, "colortex0");
        veyra$replayTerrainLayerToTarget(renderType, camX, camY, camZ, modelView, projection, "colortex3");
        veyra$replayTerrainLayerToTarget(renderType, camX, camY, camZ, modelView, projection, "shadowtex0");
        veyra$replayTerrainLayerToTarget(renderType, camX, camY, camZ, modelView, projection, "shadowcolor0");
        if (renderType == TerrainRenderType.TRANSLUCENT) {
            // Solas water/translucent gbuffers use DRAWBUFFERS:013. We cannot
            // identify water quads from VulkanMod's compact layer yet, so seed
            // the additional Solas water buffer from the translucent pass.
            veyra$replayTerrainLayerToTarget(renderType, camX, camY, camZ, modelView, projection, "colortex1");
        }
    }

    @Unique
    private void veyra$replayTerrainLayerToTarget(TerrainRenderType renderType, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection, String targetName) {
        boolean shadowDepthTarget = targetName.startsWith("shadowtex");
        boolean shadowSpaceTarget = targetName.startsWith("shadowtex") || targetName.startsWith("shadowcolor");
        Framebuffer framebuffer = shadowDepthTarget ? VeyraIrisTargetManager.depthTarget(targetName) : VeyraIrisTargetManager.colorTarget(targetName);
        RenderPass renderPass = shadowDepthTarget ? VeyraIrisTargetManager.depthPass(targetName) : veyra$terrainRenderPass(targetName);
        if (framebuffer == null || renderPass == null || sectionGraph == null) {
            return;
        }
        try {
            Renderer renderer = Renderer.getInstance();
            renderer.beginRenderPass(renderPass, framebuffer);

            boolean isTranslucent = renderType == TerrainRenderType.TRANSLUCENT;
            boolean indirectDraw = Initializer.CONFIG.indirectDraw;
            if (!isTranslucent) {
                GlStateManager._disableBlend();
            } else {
                GlStateManager._enableBlend();
                VRenderSystem.blendFuncSeparate(770, 771, 1, 771);
            }

            VRenderSystem.enableCull();
            VRenderSystem.depthFunc(515);
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(15);
            GlStateManager._disablePolygonOffset();
            VRenderSystem.setPolygonModeGL(6914);
            if (shadowSpaceTarget) {
                VRenderSystem.applyMVP(VeyraIrisPipelineBridge.shadowModelViewMatrix(), VeyraIrisPipelineBridge.shadowProjectionMatrix());
            } else {
                VRenderSystem.applyMVP(modelView, projection);
            }
            VRenderSystem.setPrimitiveTopologyGL(4);

            GraphicsPipeline pipeline = switch (targetName) {
                case "colortex0" -> {
                    GraphicsPipeline solas = isTranslucent
                            ? VeyraIrisPipelineBridge.waterSolasGbufferPipeline(renderPass, 0)
                            : VeyraIrisPipelineBridge.terrainSolasGbufferPipeline(renderPass, 0);
                    yield solas == null ? VeyraIrisPipelineBridge.terrainGbufferPipeline(renderPass) : solas;
                }
                case "colortex1" -> {
                    GraphicsPipeline solas = isTranslucent ? VeyraIrisPipelineBridge.waterSolasGbufferPipeline(renderPass, 1) : null;
                    yield solas == null ? VeyraIrisPipelineBridge.terrainWaterAuxPipeline(renderPass, 1) : solas;
                }
                case "colortex3" -> {
                    GraphicsPipeline solas = isTranslucent
                            ? VeyraIrisPipelineBridge.waterSolasGbufferPipeline(renderPass, 2)
                            : VeyraIrisPipelineBridge.terrainSolasGbufferPipeline(renderPass, 1);
                    yield solas == null ? VeyraIrisPipelineBridge.terrainWaterAuxPipeline(renderPass, 3) : solas;
                }
                case "shadowtex0" -> VeyraIrisPipelineBridge.terrainShadowPipeline(renderPass);
                case "shadowcolor0" -> VeyraIrisPipelineBridge.terrainShadowColorPipeline(renderPass);
                default -> PipelineManager.getTerrainShader(renderType);
            };
            renderer.bindGraphicsPipeline(pipeline);

            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            AbstractTexture atlasTexture = textureManager.getTexture(TextureAtlas.LOCATION_BLOCKS);
            GpuTextureView texView = atlasTexture.getTextureView();
            boolean useAnisotropy = minecraft.options.textureFiltering().get() == TextureFilteringMethod.ANISOTROPIC;
            int maxAnisotropy = minecraft.options.maxAnisotropyValue();
            VkGpuTexture texture = (VkGpuTexture) texView.texture();
            if (terrainSampler == 0L) {
                terrainSampler = SamplerManager.getSampler(true, false, texture.getVulkanImage().mipLevels - 1, useAnisotropy, maxAnisotropy);
            }
            texture.getVulkanImage().setSampler(terrainSampler);
            VRenderSystem.setShaderTexture(0, texView);
            VRenderSystem.setShaderTexture(2, Minecraft.getInstance().gameRenderer.lightmap());
            VTextureSelector.bindShaderTextures(pipeline);
            VRenderSystem.setTextureSize(texView.getWidth(0), texView.getHeight(0));
            VRenderSystem.setCurrentTime((int) System.currentTimeMillis());

            long currentTimeMs = System.currentTimeMillis();
            float fadeTime = ((Double) Minecraft.getInstance().options.chunkSectionFadeInTime().get()).floatValue();
            int fadeTimeMs = (int) (fadeTime * 1000.0F);
            float fadeTimeInv = fadeTime > 0.0F ? 1.0F / (fadeTime * 1000.0F) : 1.0F;
            IndexBuffer indexBuffer = Renderer.getDrawer().getQuadsIndexBuffer().getIndexBuffer();
            veyra$bindIndexBuffer(indexBuffer);
            int currentFrame = Renderer.getCurrentFrame();
            Set<TerrainRenderType> allowedRenderTypes = Initializer.CONFIG.uniqueOpaqueLayer
                    ? TerrainRenderType.COMPACT_RENDER_TYPES
                    : TerrainRenderType.SEMI_COMPACT_RENDER_TYPES;
            if (allowedRenderTypes.contains(renderType)) {
                renderType.setCutoutUniform();
                Iterator<ChunkArea> iterator = sectionGraph.getChunkAreaQueue().iterator();
                while (iterator.hasNext()) {
                    ChunkArea chunkArea = iterator.next();
                    StaticQueue<RenderSection> queue = chunkArea.sectionQueue;
                    DrawBuffers drawBuffers = chunkArea.getDrawBuffers();
                    if (drawBuffers.getAreaBuffer(renderType) != null && queue.size() > 0) {
                        veyra$bindDrawBuffers(drawBuffers, pipeline, renderType, camX, camY, camZ, currentTimeMs, fadeTimeMs, fadeTimeInv);
                        renderer.uploadAndBindUBOs(pipeline);
                        if (indirectDraw) {
                            drawBuffers.buildDrawBatchesIndirect(cameraPos, indirectBuffers[currentFrame], queue, renderType);
                        } else {
                            drawBuffers.buildDrawBatchesDirect(cameraPos, queue, renderType);
                        }
                    }
                }
            }
            if (renderType == TerrainRenderType.CUTOUT || renderType == TerrainRenderType.TRIPWIRE) {
                indirectBuffers[currentFrame].submitUploads();
            }
            if (!indirectDraw) {
                VRenderSystem.setModelOffset(0.0F, 0.0F, 0.0F);
                renderer.pushConstants(pipeline);
            }
            renderer.endRenderPass();
            VeyraIrisTargetManager.bindSelectorSlots();
        } catch (Throwable throwable) {
            org.apache.logging.log4j.LogManager.getLogger("VeyraIrisTerrainCapture").warn("Failed replaying terrain layer {} to {}", renderType, targetName, throwable);
        }
    }

    @Unique
    private static RenderPass veyra$terrainRenderPass(String targetName) {
        long now = System.nanoTime();
        if (now - veyra$lastTerrainCaptureNanos > 8_000_000L) {
            veyra$clearedTerrainTargets.clear();
        }
        veyra$lastTerrainCaptureNanos = now;
        if (veyra$clearedTerrainTargets.add(targetName)) {
            return VeyraIrisTargetManager.colorPass(targetName);
        }
        RenderPass loadPass = VeyraIrisTargetManager.colorLoadPass(targetName);
        return loadPass == null ? VeyraIrisTargetManager.colorPass(targetName) : loadPass;
    }

    @Unique
    private static Object veyra$commandBuffer() throws ReflectiveOperationException {
        return Renderer.class.getMethod("getCommandBuffer").invoke(null);
    }

    @Unique
    private static void veyra$bindIndexBuffer(IndexBuffer indexBuffer) throws ReflectiveOperationException {
        Class<?> vkCommandBufferClass = Class.forName("org.lwjgl.vulkan.VkCommandBuffer");
        Renderer.getDrawer().getClass()
                .getMethod("bindIndexBuffer", vkCommandBufferClass, net.vulkanmod.vulkan.memory.buffer.Buffer.class, int.class)
                .invoke(Renderer.getDrawer(), veyra$commandBuffer(), indexBuffer, indexBuffer.indexType.value);
    }

    @Unique
    private static void veyra$bindDrawBuffers(DrawBuffers drawBuffers, GraphicsPipeline pipeline, TerrainRenderType renderType,
                                               double camX, double camY, double camZ, long currentTimeMs, int fadeTimeMs, float fadeTimeInv) throws ReflectiveOperationException {
        for (java.lang.reflect.Method method : drawBuffers.getClass().getMethods()) {
            if (method.getName().equals("bindBuffers") && method.getParameterCount() == 9) {
                method.invoke(drawBuffers, veyra$commandBuffer(), pipeline, renderType, camX, camY, camZ, currentTimeMs, fadeTimeMs, fadeTimeInv);
                return;
            }
        }
        throw new NoSuchMethodException("DrawBuffers.bindBuffers/9");
    }
}
