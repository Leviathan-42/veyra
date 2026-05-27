package dev.blocktracker.mixin;

import dev.blocktracker.VeyraUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenThemeMixin {
    @Shadow
    public int width;

    @Shadow
    public int height;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    public abstract boolean isInGameUi();

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void veyra$extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VeyraUi.screenBackground(graphics, width, height, isInGameUi());
        minecraft.gui.hud.extractDeferredSubtitles();
        ci.cancel();
    }
}
