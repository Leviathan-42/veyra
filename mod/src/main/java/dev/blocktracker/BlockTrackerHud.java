package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class BlockTrackerHud {
    private BlockTrackerHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.hudLabelEnabled() || !BlockTrackerState.hasBlockTarget()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Identifier id = BlockTrackerState.targetId();
        BlockPos pos = BlockTrackerState.targetPos();
        String text = pos == null
                ? "Tracking " + id + " - no loaded match"
                : "Tracking " + id + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ();

        int textWidth = client.font.width(text);
        int x = (graphics.guiWidth() - textWidth) / 2;
        int y = 8;
        graphics.fill(x - 7, y - 4, x + textWidth + 7, y + 13, 0xA8101722);
        graphics.outline(x - 7, y - 4, textWidth + 14, 17, 0x805CA7FF);
        graphics.text(client.font, text, x, y, 0xFFFFFFFF);
    }
}
