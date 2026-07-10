package dev.blocktracker.mixin;

import dev.blocktracker.BlockSearchScreen;
import dev.blocktracker.BlockTrackerConfigScreen;
import dev.blocktracker.VeyraKeybinds;
import dev.blocktracker.VeyraBootSequence;
import dev.blocktracker.VeyraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenThemeMixin extends Screen {
    protected TitleScreenThemeMixin() {
        super(Component.literal("Veyra"));
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void veyra$initCustomTitle(CallbackInfo ci) {
        clearWidgets();
        VeyraBootSequence.beginIfNeeded();

        int panelWidth = Math.min(620, this.width - 32);
        int panelHeight = Math.min(330, this.height - 24);
        int x = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);
        int left = x + 230;
        int buttonWidth = panelWidth - 248;
        int buttonHeight = 25;
        int y = panelY + 82;

        addRenderableWidget(Button.builder(VeyraUi.component("Singleplayer"), button ->
                this.minecraft.gui.setScreen(new SelectWorldScreen(this)))
                .bounds(left, y, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Multiplayer"), button ->
                this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(left, y + 32, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Veyra Search"), button ->
                this.minecraft.gui.setScreen(new BlockSearchScreen(this)))
                .bounds(left, y + 64, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Veyra Settings"), button ->
                this.minecraft.gui.setScreen(new BlockTrackerConfigScreen()))
                .bounds(left, y + 96, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Theme: " + VeyraUi.themeName()), button -> {
                    VeyraUi.cycleTheme();
                    this.rebuildWidgets();
                })
                .bounds(left, y + 128, buttonWidth, buttonHeight)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Options"), button ->
                this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false)))
                .bounds(left, y + 164, (buttonWidth - 8) / 2, 24)
                .build());
        addRenderableWidget(Button.builder(VeyraUi.component("Quit"), button -> this.minecraft.stop())
                .bounds(left + ((buttonWidth - 8) / 2) + 8, y + 164, (buttonWidth - 8) / 2, 24)
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
        if (VeyraBootSequence.render(graphics, this.font, this.width, this.height)) {
            ci.cancel();
            return;
        }

        VeyraUi.titleBackground(graphics, this.width, this.height);

        int panelWidth = Math.min(620, this.width - 32);
        int panelHeight = Math.min(330, this.height - 24);
        int x = (this.width - panelWidth) / 2;
        int y = Math.max(12, (this.height - panelHeight) / 2);

        VeyraUi.shell(
                graphics,
                this.font,
                x,
                y,
                panelWidth,
                panelHeight,
                "Veyra Client",
                "Minecraft utility suite • local overlays • zero server install",
                ""
        );

        int railX = x + 18;
        int railY = y + 70;
        int railW = 194;
        VeyraUi.card(graphics, railX, railY, railW, 194, false);
        VeyraUi.mark(graphics, railX + 65, railY + 35, 64);
        VeyraUi.centeredText(graphics, this.font, "VEYRA", railX + railW / 2, railY + 123, VeyraUi.TEXT);
        VeyraUi.centeredText(graphics, this.font, "CLIENT BUILD 0.1.0", railX + railW / 2, railY + 142, VeyraUi.ACCENT);

        VeyraUi.sectionLabel(graphics, this.font, "Launch console", x + 230, y + 63, panelWidth - 248);

        int statusX = x + 18;
        int statusY = y + panelHeight - 42;
        String keyHint = VeyraKeybinds.menuKeyName() + " opens in-game controls";
        VeyraUi.centeredText(graphics, this.font, keyHint, statusX + (panelWidth - 36) / 2, statusY + 8, VeyraUi.MUTED);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void veyra$skipBoot(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (VeyraBootSequence.active()) {
            VeyraBootSequence.skip();
            cir.setReturnValue(true);
        }
    }
}
