package dev.blocktracker.mixin;

import dev.blocktracker.VeyraVulkanShaderPort;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = ShaderLoadUtil.class, remap = false)
public abstract class VulkanModShaderLoadUtilMixin {
    @Inject(method = "loadShader(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true, remap = false)
    private static void veyra$patchLoadedShader(String path, String shaderName, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(VeyraVulkanShaderPort.patchShader(shaderName, cir.getReturnValue()));
    }

    @Inject(method = "loadShader(Ljava/lang/String;Ljava/lang/String;Ljava/util/List;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true, remap = false)
    private static void veyra$patchLoadedShaderWithDefines(String path, String shaderName, List<String> defines, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(VeyraVulkanShaderPort.patchShader(shaderName, cir.getReturnValue()));
    }
}
