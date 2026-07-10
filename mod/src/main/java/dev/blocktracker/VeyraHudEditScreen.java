package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class VeyraHudEditScreen extends Screen {
    private final List<Button> buttons = new ArrayList<>();
    private BlockTrackerHud.HudBox dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean draggingScale;

    public VeyraHudEditScreen() {
        super(Component.literal("Veyra HUD editor"));
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttons.clear();
        int x = 20;
        int y = 78;
        buttons.add(new Button(x, y, 142, 22, () -> "Mode: " + (BlockTrackerState.hudCompactMode() ? "Compact" : "Expanded"), BlockTrackerState::toggleHudCompactMode));
        buttons.add(new Button(x + 152, y, 142, 22, () -> "Preset: " + BlockTrackerState.hudPresetName(), BlockTrackerState::cycleHudPreset));
        y += 30;
        buttons.add(new Button(x, y, 68, 22, () -> "- Scale", BlockTrackerState::decreaseHudScale));
        buttons.add(new Button(x + 74, y, 68, 22, () -> "+ Scale", BlockTrackerState::increaseHudScale));
        y += 54;

        int index = 0;
        for (BlockTrackerState.HudModule module : BlockTrackerState.HudModule.values()) {
            int columnX = x + ((index % 2) * 152);
            int rowY = y + ((index / 2) * 24);
            buttons.add(new Button(columnX, rowY, 92, 20, () -> (BlockTrackerState.hudModuleEnabled(module) ? "On " : "Off ") + module.title(), () -> BlockTrackerState.toggleHudModule(module)));
            buttons.add(new Button(columnX + 98, rowY, 48, 20, () -> BlockTrackerState.hudModuleDetached(module) ? "Window" : "Group", () -> BlockTrackerState.toggleHudModuleDetached(module)));
            index++;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (VeyraKeybinds.openMenuMatches(event) || event.key() == InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        for (Button button : buttons) {
            if (button.contains(event.x(), event.y())) {
                button.action().run();
                rebuildButtons();
                return true;
            }
        }

        if (scaleSliderContains(event.x(), event.y())) {
            draggingScale = true;
            updateScaleFromMouse(event.x());
            return true;
        }

        dragging = BlockTrackerHud.findHudBox(Minecraft.getInstance(), (int) event.x(), (int) event.y());
        if (dragging != null) {
            dragOffsetX = (int) event.x() - dragging.x();
            dragOffsetY = (int) event.y() - dragging.y();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = null;
        draggingScale = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingScale) {
            updateScaleFromMouse(event.x());
            return true;
        }
        if (dragging != null) {
            int x = clamp((int) event.x() - dragOffsetX, 0, this.width - 20);
            int y = clamp((int) event.y() - dragOffsetY, 0, this.height - 20);
            if (dragging.module() == null) {
                BlockTrackerState.moveHudMain(x, y);
            } else {
                BlockTrackerState.moveHudModule(dragging.module(), x, y);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x77000000);
        BlockTrackerHud.extractEditPreview(graphics);

        int panelX = 8;
        int panelY = 8;
        int panelW = 320;
        int panelH = this.height - 16;
        VeyraUi.shell(graphics, this.font, panelX, panelY, panelW, panelH, "HUD Edit", "Drag outlined modules", "");
        VeyraUi.sectionLabel(graphics, this.font, "Layout", panelX + 12, panelY + 61, panelW - 24);

        drawScaleSlider(graphics, mouseX, mouseY);
        for (Button button : buttons) {
            button.extract(graphics, this.font, mouseX, mouseY);
        }

        String hint = "Esc to save";
        VeyraUi.text(graphics, this.font, hint, panelX + 12, this.height - 24, VeyraUi.MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawScaleSlider(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = 24;
        int y = 145;
        int w = 126;
        graphics.fill(x, y, x + w, y + 4, VeyraUi.EDGE);
        int knob = x + Math.round((BlockTrackerState.hudScalePercent() - 60) / 100.0F * w);
        graphics.fill(knob - 3, y - 4, knob + 4, y + 9, scaleSliderContains(mouseX, mouseY) ? VeyraUi.ACCENT : VeyraUi.TEXT);
        VeyraUi.text(graphics, this.font, "Scale " + BlockTrackerState.hudScalePercent() + "%", x, y - 13, VeyraUi.MUTED);
    }

    private boolean scaleSliderContains(double mouseX, double mouseY) {
        return mouseX >= 24 && mouseX <= 150 && mouseY >= 136 && mouseY <= 156;
    }

    private void updateScaleFromMouse(double mouseX) {
        int value = 60 + Math.round((clamp((int) mouseX, 24, 150) - 24) / 126.0F * 100);
        BlockTrackerState.setHudScalePercent(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Button(int x, int y, int width, int height, Label label, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        void extract(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            boolean hover = contains(mouseX, mouseY);
            VeyraUi.button(graphics, x, y, width, height, true, hover);
            String text = label.text();
            boolean off = text.startsWith("Off");
            VeyraUi.text(graphics, font, text, x + 7, y + 7, off ? VeyraUi.BAD : (hover ? VeyraUi.ACCENT : VeyraUi.GOOD));
        }
    }

    @FunctionalInterface
    private interface Label {
        String text();
    }
}
