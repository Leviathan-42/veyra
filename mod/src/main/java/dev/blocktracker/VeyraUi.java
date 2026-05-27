package dev.blocktracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class VeyraUi {
    public static final int BACKGROUND = 0xF00D1012;
    public static final int IN_GAME_BACKGROUND = 0xA80D1012;
    public static final int PANEL = 0xE60C1117;
    public static final int SURFACE = 0xCC151D26;
    public static final int SURFACE_HOVER = 0xE6203542;
    public static final int SURFACE_DISABLED = 0xAA1A1E22;
    public static final int ACCENT = 0xFFFFC857;
    public static final int TEAL = 0xFF42A08B;
    public static final int EDGE = 0xA04AA3A6;

    private VeyraUi() {
    }

    public static void screenBackground(GuiGraphicsExtractor graphics, int width, int height, boolean inGame) {
        graphics.fill(0, 0, width, height, inGame ? IN_GAME_BACKGROUND : BACKGROUND);
        graphics.fill(0, 0, width, 28, PANEL);
        graphics.fill(0, height - 3, width, height, 0x804AA3A6);
        graphics.fill(0, 0, 4, height, 0xD04AA3A6);
        graphics.fill(4, 0, 8, height, 0xD0FFC857);
        graphics.fill(width - 4, 0, width, height, 0x7042A08B);

        if (!inGame) {
            graphics.fill(8, 28, width, 31, 0x8042A08B);
        }
    }

    public static void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean active, boolean highlighted) {
        int fill = !active ? SURFACE_DISABLED : highlighted ? SURFACE_HOVER : SURFACE;
        int edge = !active ? 0x60687984 : highlighted ? ACCENT : EDGE;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + 3, y + height, active ? TEAL : 0x80687984);
        graphics.outline(x, y, width, height, edge);

        if (highlighted && active) {
            graphics.fill(x + 3, y + height - 2, x + width - 1, y + height - 1, ACCENT);
        }
    }
}
