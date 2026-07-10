package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class VeyraTutorialScreen extends Screen {
    private static final Feature[] FEATURES = {
            new Feature("BLOCK SCANNER", "\\ queues targets; all matches highlight, nearest gets the tracer"),
            new Feature("QUICK CLEAR", "Shift + \\ cancels every active scan immediately"),
            new Feature("CONTROL CENTER", "Right Shift opens every Veyra client setting in-game"),
            new Feature("ENTITY ESP", "Outline players, animals, and hostile mobs independently"),
            new Feature("WAYPOINTS", "Mark here, mark what you see, and preserve death locations"),
            new Feature("HUD WORKSPACE", "Drag, detach, scale, and compact individual HUD modules"),
            new Feature("APPEARANCE", "Cycle color themes and choose a custom button treatment"),
            new Feature("FREECAM", "C toggles freecam; keybinds remain editable in Controls")
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
        panelW = Math.min(700, this.width - 32);
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
        VeyraUi.shell(
                graphics,
                this.font,
                panelX,
                panelY,
                panelW,
                panelH,
                "Welcome to Veyra",
                "Eight client systems. One clean control layer.",
                "QUICK START"
        );

        int gap = 10;
        int cardWidth = (panelW - 58 - gap) / 2;
        int cardHeight = 45;
        for (int index = 0; index < FEATURES.length; index++) {
            Feature feature = FEATURES[index];
            int column = index % 2;
            int row = index / 2;
            int x = panelX + 24 + column * (cardWidth + gap);
            int y = panelY + 66 + row * (cardHeight + 7);
            VeyraUi.card(graphics, x, y, cardWidth, cardHeight, false);
            graphics.fill(x + 10, y + 10, x + 16, y + 16, index % 2 == 0 ? VeyraUi.ACCENT : VeyraUi.TEAL);
            VeyraUi.text(graphics, this.font, feature.title(), x + 23, y + 7, VeyraUi.TEXT);
            VeyraUi.text(graphics, this.font, VeyraUi.fit(this.font, feature.description(), cardWidth - 20), x + 10, y + 25, VeyraUi.MUTED);
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
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private record Feature(String title, String description) {
    }
}
