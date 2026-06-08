package dev.blocktracker.mixin;

import dev.blocktracker.BlockTrackerRenderer;
import dev.blocktracker.BlockTrackerState;
import dev.blocktracker.VeyraFreecam;
import dev.blocktracker.VeyraKeybinds;
import dev.blocktracker.VeyraOnboarding;
import dev.blocktracker.VeyraTutorialScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void veyra$tickFreecam(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.getWindow() == null) {
            return;
        }

        VeyraFreecam.tick(client, VeyraKeybinds.TOGGLE_FREECAM.isDown());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void blockTracker$openSearchScreen(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;

        if (client.getWindow() == null) {
            return;
        }

        if (client.screen instanceof TitleScreen && VeyraOnboarding.consumeShouldShow()) {
            client.setScreen(new VeyraTutorialScreen(client.screen));
            return;
        }

        if (client.player == null) {
            return;
        }

        VeyraKeybinds.tick(client);
        BlockTrackerState.updatePlayerTracking(client);
        BlockTrackerRenderer.emit(client);
    }
}
