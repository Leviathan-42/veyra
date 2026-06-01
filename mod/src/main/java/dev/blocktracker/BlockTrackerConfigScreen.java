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
import java.util.function.BooleanSupplier;

public final class BlockTrackerConfigScreen extends Screen {
    private static final int PANEL_COLOR = 0xE60C1117;
    private static final int PANEL_EDGE = 0xB04AA3A6;
    private static final int BUTTON_COLOR = 0xCC151D26;
    private static final int BUTTON_HOVER = 0xE6203542;
    private static final int BUTTON_ON = 0xD9287A62;
    private static final int BUTTON_OFF = 0xD936414E;
    private static final int TEXT = 0xFFF1F6F7;
    private static final int MUTED = 0xFF93A8AE;
    private static final int ACCENT = 0xFFFFC857;

    private final List<MenuControl> controls = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public BlockTrackerConfigScreen() {
        super(Component.literal("Veyra"));
    }

    @Override
    protected void init() {
        controls.clear();
        panelWidth = Math.min(340, this.width - 28);
        panelHeight = Math.min(390, this.height - 28);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int x = panelX + 16;
        int y = panelY + 58;
        int width = panelWidth - 32;

        addToggle(x, y, width, "Block ESP", BlockTrackerState::blockEspEnabled, BlockTrackerState::toggleBlockEsp);
        y += 25;
        addToggle(x, y, width, "Tracer", BlockTrackerState::tracerEnabled, BlockTrackerState::toggleTracer);
        y += 25;
        addToggle(x, y, width, "Top HUD", BlockTrackerState::hudLabelEnabled, BlockTrackerState::toggleHudLabel);
        y += 33;
        addToggle(x, y, width, "Entity Hitboxes", BlockTrackerState::entityEspEnabled, BlockTrackerState::toggleEntityEsp);
        y += 25;
        addToggle(x, y, width, "Players", BlockTrackerState::playerEspEnabled, BlockTrackerState::togglePlayerEsp);
        y += 25;
        addToggle(x, y, width, "Animals", BlockTrackerState::animalEspEnabled, BlockTrackerState::toggleAnimalEsp);
        y += 25;
        addToggle(x, y, width, "Hostile Mobs", BlockTrackerState::hostileEspEnabled, BlockTrackerState::toggleHostileEsp);
        y += 31;
        addToggle(x, y, width, "Stats HUD", BlockTrackerState::statsHudEnabled, BlockTrackerState::toggleStatsHud);
        y += 25;
        addToggle(x, y, width, "Waypoints HUD", BlockTrackerState::waypointHudEnabled, BlockTrackerState::toggleWaypointHud);
        y += 25;
        addToggle(x, y, width, "Fullbright", BlockTrackerState::fullbrightEnabled, BlockTrackerState::toggleFullbright);
        y += 34;
        addAction(x, y, (width - 8) / 2, "Add Waypoint", () -> BlockTrackerState.addWaypoint(Minecraft.getInstance()));
        addAction(x + ((width - 8) / 2) + 8, y, (width - 8) / 2, "Clear Waypoints", BlockTrackerState::clearWaypoints);
        y += 28;
        addAction(x, y, (width - 8) / 2, "Clear Target", BlockTrackerState::clear);
        addAction(x + ((width - 8) / 2) + 8, y, (width - 8) / 2, "Done", () -> Minecraft.getInstance().setScreen(null));
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier state, Runnable action) {
        controls.add(new MenuControl(x, y, width, 20, label, state, action));
    }

    private void addAction(int x, int y, int width, String label, Runnable action) {
        controls.add(new MenuControl(x, y, width, 22, label, null, action));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RSHIFT) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        for (MenuControl control : controls) {
            if (control.contains(event.x(), event.y())) {
                control.action().run();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_EDGE);
        graphics.fill(panelX, panelY, panelX + 4, panelY + panelHeight, 0xD04AA3A6);
        graphics.fill(panelX + 4, panelY, panelX + 8, panelY + panelHeight, 0xD0FFC857);

        graphics.text(this.font, "VEYRA CLIENT", panelX + 18, panelY + 14, ACCENT);
        graphics.text(this.font, "Utility controls", panelX + 18, panelY + 30, TEXT);
        graphics.text(this.font, "Right Shift closes", panelX + panelWidth - this.font.width("Right Shift closes") - 14, panelY + 18, MUTED);

        for (MenuControl control : controls) {
            control.extract(graphics, this.font, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record MenuControl(int x, int y, int width, int height, String label, BooleanSupplier state, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        void extract(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            boolean hover = contains(mouseX, mouseY);
            boolean toggle = state != null;
            int fill = hover ? BUTTON_HOVER : BUTTON_COLOR;

            graphics.fill(x, y, x + width, y + height, fill);
            graphics.outline(x, y, width, height, hover ? ACCENT : 0x70687984);
            graphics.text(font, label, x + 8, y + 6, TEXT);

            if (!toggle) {
                return;
            }

            boolean enabled = state.getAsBoolean();
            String value = enabled ? "ON" : "OFF";
            int valueWidth = font.width(value);
            int pillWidth = valueWidth + 18;
            int pillX = x + width - pillWidth - 5;
            int pillY = y + 4;

            graphics.fill(pillX, pillY, pillX + pillWidth, pillY + height - 8, enabled ? BUTTON_ON : BUTTON_OFF);
            graphics.text(font, value, pillX + 9, pillY + 3, 0xFFFFFFFF);
        }
    }
}
