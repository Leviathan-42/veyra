package dev.blocktracker.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.blocktracker.shader.VeyraIrisRuntime;
import dev.blocktracker.shader.VeyraIrisTargetManager;
import net.minecraft.client.CloudStatus;
import net.vulkanmod.render.VBO;
import net.vulkanmod.render.shader.PipelineManager;
import net.vulkanmod.render.sky.CloudRenderer;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CloudRenderer.class, remap = false)
public abstract class VulkanModCloudRendererMixin {
    @Shadow private VBO cloudBuffer;
    @Shadow private int prevCloudX;
    @Shadow private int prevCloudZ;
    @Shadow private byte prevCloudY;
    @Shadow private CloudStatus prevCloudsStatus;

    @Inject(method = "renderClouds", at = @At("TAIL"), remap = false)
    private void veyra$captureCloudsToIris(float cloudHeight, int cloudColor, double camX, double camY, double camZ, long gameTime, float partialTicks, CallbackInfo ci) {
        if (!VeyraIrisRuntime.initialized() || Boolean.getBoolean("veyra.iris.disableCloudCapture") || cloudBuffer == null) {
            return;
        }
        veyra$drawCloudsTo("colortex0", cloudHeight, cloudColor, camX, camY, camZ, gameTime, partialTicks);
    }

    @Unique
    private void veyra$drawCloudsTo(String targetName, float cloudHeight, int cloudColor, double camX, double camY, double camZ, long gameTime, float partialTicks) {
        Framebuffer framebuffer = VeyraIrisTargetManager.colorTarget(targetName);
        RenderPass renderPass = VeyraIrisTargetManager.colorLoadPass(targetName);
        if (framebuffer == null || renderPass == null) return;
        try {
            float timeOffset = (float) (gameTime % 4096L) + partialTicks;
            double centerX = camX + timeOffset * 0.03F;
            double centerZ = camZ + 3.96F;
            double centerY = cloudHeight - (float) camY + 0.33F;
            float xTranslation = (float) (centerX - prevCloudX * 12);
            float yTranslation = (float) centerY;
            float zTranslation = (float) (centerZ - prevCloudZ * 12);

            Renderer renderer = Renderer.getInstance();
            renderer.beginRenderPass(renderPass, framebuffer);

            Matrix4fStack poseStack = RenderSystem.getModelViewStack();
            poseStack.pushMatrix();
            poseStack.translate(-xTranslation, yTranslation, -zTranslation);
            VRenderSystem.applyModelViewMatrix(poseStack);
            VRenderSystem.calculateMVP();
            VRenderSystem.setModelOffset(-xTranslation, 0.0F, -zTranslation);

            float r = ColorUtil.ARGB.unpackR(cloudColor);
            float g = ColorUtil.ARGB.unpackG(cloudColor);
            float b = ColorUtil.ARGB.unpackB(cloudColor);
            VRenderSystem.setShaderColor(r, g, b, 0.8F);
            GraphicsPipeline pipeline = PipelineManager.getCloudsPipeline();
            VRenderSystem.enableBlend();
            VRenderSystem.blendFuncSeparate(770, 771, 1, 0);
            VRenderSystem.enableDepthTest();
            VRenderSystem.depthFunc(515);
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(15);
            GlStateManager._disablePolygonOffset();
            VRenderSystem.setPolygonModeGL(6914);
            VRenderSystem.setPrimitiveTopologyGL(4);
            if (prevCloudY == 2 || prevCloudsStatus == CloudStatus.FAST && centerY <= 0.0) {
                VRenderSystem.disableCull();
            } else {
                VRenderSystem.enableCull();
            }
            renderer.bindGraphicsPipeline(pipeline);
            renderer.uploadAndBindUBOs(pipeline);
            cloudBuffer.bind(pipeline);
            cloudBuffer.draw();
            renderer.endRenderPass();
            poseStack.popMatrix();
            VRenderSystem.enableCull();
            VRenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            VRenderSystem.setModelOffset(0.0F, 0.0F, 0.0F);
            VeyraIrisTargetManager.bindSelectorSlots();
        } catch (Throwable throwable) {
            org.apache.logging.log4j.LogManager.getLogger("VeyraIrisCloudCapture").warn("Failed capturing clouds to Iris {}", targetName, throwable);
        }
    }
}
