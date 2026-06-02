package dev.blocktracker.mixin;

import dev.blocktracker.VeyraFreecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientInput.class)
public abstract class FreecamInputMixin {
    @Shadow
    public Input keyPresses;

    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("TAIL"))
    private void veyra$cancelMovementWhileFreecam(CallbackInfo ci) {
        if (!VeyraFreecam.enabled()) {
            return;
        }

        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
