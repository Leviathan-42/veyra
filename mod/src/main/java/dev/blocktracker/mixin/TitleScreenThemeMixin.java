package dev.blocktracker.mixin;

import dev.blocktracker.VeyraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenThemeMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void veyra$extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        VeyraUi.screenBackground(graphics, screen.width, screen.height, false);
        ci.cancel();
    }
}
