package dev.blocktracker.mixin;

import dev.blocktracker.BlockSearchScreen;
import dev.blocktracker.BlockTrackerConfigScreen;
import dev.blocktracker.VeyraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenThemeMixin extends Screen {
    protected TitleScreenThemeMixin() {
        super(Component.literal("Veyra"));
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void veyra$initCustomTitle(CallbackInfo ci) {
        clearWidgets();

        int panelWidth = Math.min(430, this.width - 40);
        int panelHeight = Math.min(300, this.height - 24);
        int x = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);
        int left = x + 22;
        int buttonWidth = panelWidth - 44;
        int buttonHeight = 22;
        int gap = 7;
        int bottomPadding = 52;
        int totalButtonsHeight = (4 * buttonHeight) + (3 * gap) + 8 + 22;
        int y = panelY + panelHeight - bottomPadding - totalButtonsHeight;
        y = Math.max(panelY + 76, y);

        addRenderableWidget(Button.builder(Component.literal("Singleplayer"), button ->
                this.minecraft.setScreen(new SelectWorldScreen(this)))
                .bounds(left, y, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Multiplayer"), button ->
                this.minecraft.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(left, y + 29, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Veyra Search"), button ->
                this.minecraft.setScreen(new BlockSearchScreen()))
                .bounds(left, y + 58, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Veyra Settings"), button ->
                this.minecraft.setScreen(new BlockTrackerConfigScreen()))
                .bounds(left, y + 87, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Options"), button ->
                this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false)))
                .bounds(left, y + 120, (buttonWidth - 8) / 2, 22)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Quit"), button -> this.minecraft.stop())
                .bounds(left + ((buttonWidth - 8) / 2) + 8, y + 120, (buttonWidth - 8) / 2, 22)
                .build());

        ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void veyra$extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VeyraUi.titleBackground(graphics, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void veyra$extractCustomTitle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        VeyraUi.titleBackground(graphics, this.width, this.height);

        int panelWidth = Math.min(430, this.width - 40);
        int panelHeight = Math.min(300, this.height - 24);
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(12, (this.height - panelHeight) / 2);

        graphics.fill(x, y, x + panelWidth, y + panelHeight, VeyraUi.PANEL);
        graphics.outline(x, y, panelWidth, panelHeight, VeyraUi.EDGE);
        graphics.fill(x, y, x + 5, y + panelHeight, VeyraUi.TEAL);
        graphics.fill(x + 5, y, x + 9, y + panelHeight, VeyraUi.ACCENT);

        graphics.text(this.font, "VEYRA", x + 24, y + 18, VeyraUi.ACCENT);
        graphics.text(this.font, "Aperture Fabric Utility Client", x + 24, y + 34, 0xFFF1F6F7);
        graphics.text(this.font, "optimized renderer  |  iris ready  |  local overlays", x + 24, y + 50, 0xFF93A8AE);

        int statusX = x + 24;
        int statusY = y + panelHeight - 34;
        graphics.fill(statusX, statusY, x + panelWidth - 24, statusY + 18, 0x99151D26);
        graphics.outline(statusX, statusY, panelWidth - 48, 18, 0x804AA3A6);
        graphics.text(this.font, "Right Shift: Veyra in-game controls", statusX + 8, statusY + 5, 0xFFB9D7FF);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }
}
