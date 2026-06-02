package dev.blocktracker.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.blocktracker.BlockSearchScreen;
import dev.blocktracker.BlockTrackerConfigScreen;
import dev.blocktracker.BlockTrackerRenderer;
import dev.blocktracker.BlockTrackerState;
import dev.blocktracker.VeyraFreecam;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Unique
    private boolean blockTracker$backslashWasDown;
    @Unique
    private boolean blockTracker$rightShiftWasDown;

    @Inject(method = "tick", at = @At("HEAD"))
    private void veyra$tickFreecam(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) {
            return;
        }

        VeyraFreecam.tick(client, InputConstants.isKeyDown(client.getWindow(), InputConstants.KEY_C));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void blockTracker$openSearchScreen(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (client.player == null || client.getWindow() == null) {
            return;
        }

        boolean backslashDown = InputConstants.isKeyDown(
                client.getWindow(),
                InputConstants.KEY_BACKSLASH
        );

        if (backslashDown && !blockTracker$backslashWasDown) {
            if (client.screen instanceof BlockSearchScreen) {
                client.setScreen(null);
            } else {
                client.setScreen(new BlockSearchScreen());
            }
        }

        boolean rightShiftDown = InputConstants.isKeyDown(
                client.getWindow(),
                InputConstants.KEY_RSHIFT
        );

        if (rightShiftDown && !blockTracker$rightShiftWasDown) {
            if (client.screen instanceof BlockTrackerConfigScreen) {
                client.setScreen(null);
            } else if (client.screen == null) {
                client.setScreen(new BlockTrackerConfigScreen());
            }
        }

        blockTracker$backslashWasDown = backslashDown;
        blockTracker$rightShiftWasDown = rightShiftDown;
        BlockTrackerState.updatePlayerTracking(client);
        BlockTrackerRenderer.emit(client);
    }
}
