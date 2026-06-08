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
    private final List<MenuControl> controls = new ArrayList<>();
    private final List<TabControl> tabs = new ArrayList<>();
    private Tab activeTab = Tab.BLOCKS;
    private long openedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int currentOffsetY;

    public BlockTrackerConfigScreen() {
        super(Component.literal("Veyra"));
        openedAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, this.width - 28);
        panelHeight = Math.min(330, this.height - 28);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        rebuildLayout();
    }

    private void rebuildLayout() {
        controls.clear();
        tabs.clear();

        int navX = panelX + 16;
        int navY = panelY + 64;
        int navWidth = 132;
        int tabHeight = 28;
        for (Tab tab : Tab.values()) {
            tabs.add(new TabControl(navX, navY, navWidth, tabHeight, tab));
            navY += tabHeight + 8;
        }

        int contentX = panelX + 168;
        int contentY = panelY + 70;
        int contentWidth = panelWidth - 192;
        int half = (contentWidth - 10) / 2;

        switch (activeTab) {
            case BLOCKS -> {
                addToggle(contentX, contentY, half, "Block ESP", BlockTrackerState::blockEspEnabled, BlockTrackerState::toggleBlockEsp);
                addToggle(contentX + half + 10, contentY, half, "Tracer", BlockTrackerState::tracerEnabled, BlockTrackerState::toggleTracer);
                contentY += 38;
                addToggle(contentX, contentY, half, "Top HUD", BlockTrackerState::hudLabelEnabled, BlockTrackerState::toggleHudLabel);
                addAction(contentX + half + 10, contentY, half, "Clear Targets", BlockTrackerState::clear);
            }
            case ENTITIES -> {
                addToggle(contentX, contentY, contentWidth, "Entity Hitboxes", BlockTrackerState::entityEspEnabled, BlockTrackerState::toggleEntityEsp);
                contentY += 38;
                addToggle(contentX, contentY, half, "Players", BlockTrackerState::playerEspEnabled, BlockTrackerState::togglePlayerEsp);
                addToggle(contentX + half + 10, contentY, half, "Animals", BlockTrackerState::animalEspEnabled, BlockTrackerState::toggleAnimalEsp);
                contentY += 38;
                addToggle(contentX, contentY, half, "Hostile Mobs", BlockTrackerState::hostileEspEnabled, BlockTrackerState::toggleHostileEsp);
            }
            case WAYPOINTS -> {
                addToggle(contentX, contentY, contentWidth, "Waypoints HUD", BlockTrackerState::waypointHudEnabled, BlockTrackerState::toggleWaypointHud);
                contentY += 38;
                addAction(contentX, contentY, half, "Add Here", () -> BlockTrackerState.addWaypoint(Minecraft.getInstance()));
                addAction(contentX + half + 10, contentY, half, "Mark Look", () -> BlockTrackerState.addLookWaypoint(Minecraft.getInstance()));
                contentY += 38;
                addAction(contentX, contentY, half, "Remove Nearest", () -> BlockTrackerState.removeNearestWaypoint(Minecraft.getInstance()));
                addAction(contentX + half + 10, contentY, half, "Clear All", BlockTrackerState::clearWaypoints);
                contentY += 38;
                addAction(contentX, contentY, half, "Clear Death", BlockTrackerState::clearDeathMarker);
            }
            case VISUALS -> {
                addToggle(contentX, contentY, half, "Stats HUD", BlockTrackerState::statsHudEnabled, BlockTrackerState::toggleStatsHud);
                addAction(contentX + half + 10, contentY, half, "Edit HUD", () -> Minecraft.getInstance().setScreen(new VeyraHudEditScreen()));
                contentY += 38;
                addToggle(contentX, contentY, half, "Fullbright", BlockTrackerState::fullbrightEnabled, BlockTrackerState::toggleFullbright);
                addAction(contentX + half + 10, contentY, half, "HUD: " + (BlockTrackerState.hudCompactMode() ? "Compact" : "Expanded"), BlockTrackerState::toggleHudCompactMode);
                contentY += 38;
                addToggle(contentX, contentY, contentWidth, "Custom Crosshair", BlockTrackerState::customCrosshairEnabled, BlockTrackerState::toggleCustomCrosshair);
                contentY += 38;
                addAction(contentX, contentY, half, "Style: " + BlockTrackerState.crosshairStyleName(), BlockTrackerState::cycleCrosshairStyle);
                addAction(contentX + half + 10, contentY, half, "Size +", BlockTrackerState::increaseCrosshairSize);
                contentY += 38;
                addAction(contentX, contentY, half, "Size -", BlockTrackerState::decreaseCrosshairSize);
                addAction(contentX + half + 10, contentY, half, "Done", () -> Minecraft.getInstance().setScreen(null));
            }
            case APPEARANCE -> {
                addAction(contentX, contentY, contentWidth, "Theme: " + VeyraUi.themeName(), VeyraUi::cycleTheme);
                contentY += 38;
                addAction(contentX, contentY, contentWidth, "Buttons: " + VeyraUi.buttonStyleName(), VeyraUi::cycleButtonStyle);
                contentY += 38;
                addAction(contentX, contentY, half, "Startup Guide", () -> Minecraft.getInstance().setScreen(new VeyraTutorialScreen(this)));
                addAction(contentX + half + 10, contentY, half, "Show On Launch", VeyraOnboarding::showNextLaunch);
                contentY += 38;
                addAction(contentX, contentY, half, "Reset Appearance", VeyraUi::resetAppearance);
                addAction(contentX + half + 10, contentY, half, "Done", () -> Minecraft.getInstance().setScreen(null));
            }
        }
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier state, Runnable action) {
        controls.add(new MenuControl(x, y, width, 30, label, state, action));
    }

    private void addAction(int x, int y, int width, String label, Runnable action) {
        controls.add(new MenuControl(x, y, width, 30, label, null, action));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (VeyraKeybinds.openMenuMatches(event) || event.key() == InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (VeyraKeybinds.openMenuMatches(event)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        for (TabControl tab : tabs) {
            if (tab.contains(event.x(), event.y(), currentOffsetY)) {
                activeTab = tab.tab();
                rebuildLayout();
                return true;
            }
        }

        for (MenuControl control : controls) {
            if (control.contains(event.x(), event.y(), currentOffsetY)) {
                control.action().run();
                rebuildLayout();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        VeyraUi.screenBackground(graphics, this.width, this.height, true);

        float progress = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 230.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 4.0F);
        int offscreenOffset = this.height - panelY + 18;
        currentOffsetY = Math.round((1.0F - eased) * offscreenOffset);

        int y = panelY + currentOffsetY;
        VeyraUi.panel(graphics, panelX, y, panelWidth, panelHeight);
        graphics.fill(panelX + 1, y + 1, panelX + panelWidth - 1, y + 42, 0xB0141820);
        graphics.fill(panelX + 18, y + 43, panelX + panelWidth - 18, y + 44, 0x4438424F);

        VeyraUi.text(graphics, this.font, "Veyra", panelX + 20, y + 15, VeyraUi.TEXT);
        VeyraUi.text(graphics, this.font, activeTab.title, panelX + 168, y + 15, VeyraUi.ACCENT);
        String closeHint = "Esc / " + VeyraKeybinds.menuKeyName() + " closes";
        VeyraUi.text(graphics, this.font, closeHint, panelX + panelWidth - VeyraUi.width(this.font, closeHint) - 20, y + 15, VeyraUi.MUTED);

        for (TabControl tab : tabs) {
            tab.extract(graphics, this.font, mouseX, mouseY, currentOffsetY, tab.tab() == activeTab);
        }

        VeyraUi.text(graphics, this.font, activeTab.description, panelX + 168, panelY + currentOffsetY + 49, VeyraUi.MUTED);

        for (MenuControl control : controls) {
            control.extract(graphics, this.font, mouseX, mouseY, currentOffsetY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        BLOCKS("Blocks", "Search, ESP, tracers, and target controls"),
        ENTITIES("Entities", "Configure player, animal, and hostile overlays"),
        WAYPOINTS("Waypoints", "Drop markers, track locations, and manage death points"),
        VISUALS("Visuals", "HUD modules, fullbright, and display options"),
        APPEARANCE("Appearance", "Change the Veyra theme and custom button style");

        private final String title;
        private final String description;

        Tab(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private record TabControl(int x, int y, int width, int height, Tab tab) {
        boolean contains(double mouseX, double mouseY, int offsetY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y + offsetY && mouseY <= y + offsetY + height;
        }

        void extract(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY, int offsetY, boolean selected) {
            boolean hover = contains(mouseX, mouseY, offsetY);
            int drawY = y + offsetY;
            int fill = selected ? VeyraUi.SURFACE_HOVER : hover ? VeyraUi.SURFACE : 0x00111111;
            int edge = selected ? VeyraUi.ACCENT : hover ? VeyraUi.EDGE : 0x0038424F;

            graphics.fill(x, drawY, x + width, drawY + height, fill);
            graphics.outline(x, drawY, width, height, edge);
            if (selected) {
                graphics.fill(x, drawY + 6, x + 2, drawY + height - 6, VeyraUi.ACCENT);
            }
            VeyraUi.text(graphics, font, tab.title, x + 12, drawY + 9, selected ? VeyraUi.TEXT : VeyraUi.MUTED);
        }
    }

    private record MenuControl(int x, int y, int width, int height, String label, BooleanSupplier state, Runnable action) {
        boolean contains(double mouseX, double mouseY, int offsetY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y + offsetY && mouseY <= y + offsetY + height;
        }

        void extract(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY, int offsetY) {
            boolean hover = contains(mouseX, mouseY, offsetY);
            boolean toggle = state != null;
            int drawY = y + offsetY;
            VeyraUi.button(graphics, x, drawY, width, height, true, hover);
            VeyraUi.text(graphics, font, label, x + 11, drawY + 10, VeyraUi.TEXT);

            if (!toggle) {
                return;
            }

            boolean enabled = state.getAsBoolean();
            String value = enabled ? "ON" : "OFF";
            int valueWidth = VeyraUi.width(font, value);
            int pillWidth = Math.max(42, valueWidth + 18);
            int pillX = x + width - pillWidth - 7;
            int pillY = drawY + 6;

            graphics.fill(pillX, pillY, pillX + pillWidth, pillY + height - 12, enabled ? 0xCC145C4A : 0xCC4A222A);
            VeyraUi.text(graphics, font, value, pillX + (pillWidth - valueWidth) / 2, pillY + 4, enabled ? VeyraUi.GOOD : VeyraUi.BAD);
        }
    }
}
