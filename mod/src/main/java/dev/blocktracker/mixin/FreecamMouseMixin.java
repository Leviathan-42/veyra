package dev.blocktracker.mixin;

import dev.blocktracker.VeyraFreecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class FreecamMouseMixin {
    @Shadow
    private Minecraft minecraft;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void veyra$adjustFreecamSpeed(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!VeyraFreecam.enabled()) {
            return;
        }

        VeyraFreecam.adjustSpeed(vertical);
        ci.cancel();
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void veyra$turnFreecamOnly(double delta, CallbackInfo ci) {
        if (!VeyraFreecam.enabled()) {
            return;
        }

        double sensitivity = minecraft.options.sensitivity().get() * 0.6000000238418579D + 0.20000000298023224D;
        double scale = sensitivity * sensitivity * sensitivity * 8.0D;
        VeyraFreecam.turn(accumulatedDX * scale, accumulatedDY * scale);
        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
        ci.cancel();
    }
}
