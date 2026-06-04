package dev.blocktracker.mixin;

import dev.blocktracker.VeyraShaderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends Screen {
    protected VideoSettingsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void veyra$addShadersButton(CallbackInfo ci) {
        Button shadersListButton = Button.builder(
                Component.literal("Shaders..."),
                button -> Minecraft.getInstance().setScreen(new VeyraShaderScreen(this))
        ).width(150).build();

        ((OptionsSubScreenAccessor) this).blocktracker$getList().addSmall(List.<AbstractWidget>of(shadersListButton));

        Button shadersTopButton = Button.builder(
                Component.literal("Shaders..."),
                button -> Minecraft.getInstance().setScreen(new VeyraShaderScreen(this))
        ).bounds(this.width - 118, 6, 100, 20).build();

        this.addRenderableWidget(shadersTopButton);
    }
}
