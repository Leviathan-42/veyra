package dev.blocktracker.mixin;

import dev.blocktracker.BlockTrackerState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void blockTracker$applyFullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (!BlockTrackerState.fullbrightEnabled()) {
            return;
        }

        state.needsUpdate = true;
        state.brightness = 1.0F;
        state.darknessEffectScale = 0.0F;
        state.nightVisionEffectIntensity = 1.0F;
        state.bossOverlayWorldDarkening = 0.0F;
    }
}
