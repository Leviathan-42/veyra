package dev.blocktracker.mixin;

import dev.blocktracker.BlockTrackerState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void veyra$hideVanillaCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BlockTrackerState.customCrosshairEnabled()) {
            ci.cancel();
        }
    }
}
