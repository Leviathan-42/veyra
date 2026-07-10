package dev.blocktracker.mixin;

import dev.blocktracker.VeyraFreecam;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void veyra$applyFreecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!VeyraFreecam.enabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        VeyraFreecam.frame(client, deltaTracker.getRealtimeDeltaTicks());

        Vec3 position = VeyraFreecam.position();
        if (position == null || client.player == null) {
            return;
        }

        CameraAccessor accessor = (CameraAccessor) this;
        accessor.veyra$setPosition(position);
        accessor.veyra$setRotation(VeyraFreecam.yaw(), VeyraFreecam.pitch());
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void veyra$disableDirectionalOcclusion(CameraRenderState state, float partialTick, CallbackInfo ci) {
        if (VeyraFreecam.enabled()) {
            // Freecam can reverse direction or move across sections without the
            // player moving. Rebuilding the graph without smart occlusion keeps
            // already-loaded sections visible in every direction.
            state.smartCull = false;
        }
    }
}
