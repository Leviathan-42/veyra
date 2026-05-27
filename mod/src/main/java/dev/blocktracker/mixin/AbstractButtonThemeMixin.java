package dev.blocktracker.mixin;

import dev.blocktracker.VeyraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonThemeMixin {
    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void veyra$extractDefaultSprite(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        AbstractWidget widget = (AbstractWidget) (Object) this;
        VeyraUi.button(
                graphics,
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                widget.active,
                widget.isHoveredOrFocused()
        );
        ci.cancel();
    }
}
