package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class VeyraTutorialScreen extends Screen {
    private static final String[] FEATURES = {
            "\\ opens Block Search: pick a block and Veyra outlines the nearest loaded match.",
            "Shift + \\ cancels/clears the current Block ESP target instantly.",
            "Right Shift opens the in-game Veyra menu for ESP, tracers, HUD, waypoints, and visuals.",
            "Entity Hitboxes can outline players, animals, and hostile mobs at long range.",
            "Waypoints can mark your position, your looked-at block, and your death point.",
            "HUD Edit lets you drag modules, detach windows, change scale, and compact the overlay.",
            "Appearance changes themes and custom button styles; the title screen Theme button cycles fast.",
            "C toggles Veyra Freecam. Change keybinds in Minecraft controls if needed."
    };

    private final Screen parent;
    private boolean dontShowAgain = true;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int checkboxX;
    private int checkboxY;
    private int doneX;
    private int doneY;
    private int buttonW;
    private int buttonH;

    public VeyraTutorialScreen(Screen parent) {
        super(Component.literal("Welcome to Veyra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(620, this.width - 32);
        panelH = Math.min(360, this.height - 32);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        checkboxX = panelX + 24;
        checkboxY = panelY + panelH - 48;
        buttonW = 116;
        buttonH = 24;
        doneX = panelX + panelW - buttonW - 24;
        doneY = panelY + panelH - 52;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            closeTutorial();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        if (contains(event.x(), event.y(), checkboxX, checkboxY, 190, 18)) {
            dontShowAgain = !dontShowAgain;
            return true;
        }

        if (contains(event.x(), event.y(), doneX, doneY, buttonW, buttonH)) {
            closeTutorial();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        VeyraUi.screenBackground(graphics, this.width, this.height, false);
        VeyraUi.panel(graphics, panelX, panelY, panelW, panelH);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 48, VeyraUi.withAlpha(VeyraUi.SURFACE, 0xB8));
        graphics.fill(panelX + 24, panelY + 49, panelX + panelW - 24, panelY + 50, VeyraUi.EDGE);

        VeyraUi.text(graphics, this.font, "Welcome to Veyra", panelX + 24, panelY + 17, VeyraUi.TEXT);
        VeyraUi.text(graphics, this.font, "Quick startup guide for the tools and shortcuts", panelX + 162, panelY + 17, VeyraUi.MUTED);

        int y = panelY + 68;
        for (String feature : FEATURES) {
            graphics.fill(panelX + 26, y + 3, panelX + 30, y + 7, VeyraUi.ACCENT);
            VeyraUi.text(graphics, this.font, feature, panelX + 38, y, VeyraUi.TEXT);
            y += 24;
        }

        drawCheckbox(graphics, mouseX, mouseY);
        VeyraUi.button(graphics, doneX, doneY, buttonW, buttonH, true, contains(mouseX, mouseY, doneX, doneY, buttonW, buttonH));
        VeyraUi.centeredText(graphics, this.font, "Got it", doneX + buttonW / 2, doneY + 8, VeyraUi.TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawCheckbox(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hover = contains(mouseX, mouseY, checkboxX, checkboxY, 190, 18);
        int box = 12;
        graphics.fill(checkboxX, checkboxY, checkboxX + box, checkboxY + box, hover ? VeyraUi.SURFACE_HOVER : VeyraUi.SURFACE);
        graphics.outline(checkboxX, checkboxY, box, box, dontShowAgain ? VeyraUi.ACCENT : VeyraUi.EDGE);
        if (dontShowAgain) {
            graphics.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 8, VeyraUi.ACCENT);
            graphics.fill(checkboxX + 5, checkboxY + 7, checkboxX + 10, checkboxY + 9, VeyraUi.ACCENT);
        }
        VeyraUi.text(graphics, this.font, "Don't show this on startup again", checkboxX + 18, checkboxY + 2, VeyraUi.MUTED);
    }

    private void closeTutorial() {
        if (dontShowAgain) {
            VeyraOnboarding.dismissForever();
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
