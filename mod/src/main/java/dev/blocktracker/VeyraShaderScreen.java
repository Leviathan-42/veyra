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

public final class VeyraShaderScreen extends Screen {
    private static final int TEXT = 0xFFF4F7FA;
    private static final int MUTED = 0xFF8B96A5;
    private static final int ACTIVE = 0xFF7DD3FC;
    private static final int WARN = 0xFFFBBF24;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private long openedAt;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int currentOffsetY;

    public VeyraShaderScreen(Screen parent) {
        super(Component.literal("Shaders"));
        this.parent = parent;
        this.openedAt = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(620, this.width - 28);
        panelHeight = Math.min(360, this.height - 28);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        List<String> packs = VeyraShaderPackManager.availablePacks();
        int x = panelX + 24;
        int y = panelY + 116;
        int width = panelWidth - 48;
        for (String pack : packs) {
            rows.add(new Row(x, y, width, 28, pack));
            y += 34;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        for (Row row : rows) {
            if (row.contains(event.x(), event.y(), currentOffsetY)) {
                VeyraShaderPackManager.selectPack(row.pack());
                rebuildRows();
                return true;
            }
        }

        int backX = panelX + panelWidth - 96;
        int backY = panelY + currentOffsetY + panelHeight - 42;
        if (event.x() >= backX && event.x() <= backX + 72 && event.y() >= backY && event.y() <= backY + 26) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        VeyraUi.screenBackground(graphics, this.width, this.height, true);

        float progress = Math.min(1.0F, (System.currentTimeMillis() - openedAt) / 220.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 4.0F);
        currentOffsetY = Math.round((1.0F - eased) * (this.height - panelY + 18));
        int y = panelY + currentOffsetY;

        VeyraUi.panel(graphics, panelX, y, panelWidth, panelHeight);
        graphics.fill(panelX + 1, y + 1, panelX + panelWidth - 1, y + 42, 0xB0141820);
        graphics.fill(panelX + 18, y + 43, panelX + panelWidth - 18, y + 44, 0x4438424F);

        VeyraUi.text(graphics, this.font, "Shaders", panelX + 20, y + 15, TEXT);
        VeyraUi.text(graphics, this.font, "Vulkan shaderpack selector", panelX + 112, y + 15, ACTIVE);

        String selected = VeyraShaderPackManager.selectedPack();
        VeyraUi.text(graphics, this.font, "Selected: " + selected, panelX + 24, y + 58, TEXT);
        VeyraUi.text(graphics, this.font, "Packs are detected from shaderpacks/ and vulkan-shaderpacks/.", panelX + 24, y + 78, MUTED);
        VeyraUi.text(graphics, this.font, "Experimental: selection is saved; full Vulkan rendering is the next port step.", panelX + 24, y + 96, WARN);

        for (Row row : rows) {
            row.extract(graphics, this.font, mouseX, mouseY, currentOffsetY, row.pack().equals(selected));
        }

        int backX = panelX + panelWidth - 96;
        int backY = y + panelHeight - 42;
        boolean hover = mouseX >= backX && mouseX <= backX + 72 && mouseY >= backY && mouseY <= backY + 26;
        graphics.fill(backX, backY, backX + 72, backY + 26, hover ? VeyraUi.SURFACE_HOVER : 0xE01F2937);
        graphics.outline(backX, backY, 72, 26, hover ? 0x887DD3FC : 0x5538424F);
        VeyraUi.text(graphics, this.font, "Back", backX + 24, backY + 9, TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(int x, int y, int width, int height, String pack) {
        boolean contains(double mouseX, double mouseY, int offsetY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y + offsetY && mouseY <= y + offsetY + height;
        }

        void extract(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY, int offsetY, boolean selected) {
            boolean hover = contains(mouseX, mouseY, offsetY);
            int drawY = y + offsetY;
            graphics.fill(x, drawY, x + width, drawY + height, selected ? 0xE0263340 : hover ? VeyraUi.SURFACE_HOVER : 0xB01F2937);
            graphics.outline(x, drawY, width, height, selected ? 0x887DD3FC : hover ? 0x5538424F : 0x3338424F);
            VeyraUi.text(graphics, font, selected ? "✓ " + pack : pack, x + 12, drawY + 9, selected ? ACTIVE : TEXT);
        }
    }
}
