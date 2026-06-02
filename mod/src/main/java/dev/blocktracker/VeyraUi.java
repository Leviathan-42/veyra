package dev.blocktracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class VeyraUi {
    public static final int BACKGROUND = 0xF0060709;
    public static final int IN_GAME_BACKGROUND = 0x8A060709;
    public static final int PANEL = 0xF2161A20;
    public static final int SURFACE = 0xE01F242C;
    public static final int SURFACE_HOVER = 0xF02A313C;
    public static final int SURFACE_DISABLED = 0xAA191D23;
    public static final int ACCENT = 0xFF7DD3FC;
    public static final int TEAL = 0xFF8BE9D8;
    public static final int EDGE = 0x5538424F;

    private VeyraUi() {
    }

    public static void screenBackground(GuiGraphicsExtractor graphics, int width, int height, boolean inGame) {
        graphics.fill(0, 0, width, height, inGame ? IN_GAME_BACKGROUND : BACKGROUND);
    }

    public static void titleBackground(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xFF07080A);
        graphics.fill(0, 0, width, height, 0x22000000);
        graphics.fill(width / 2 - 150, 78, width / 2 + 150, 79, 0x557DD3FC);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, EDGE);
    }

    public static void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean active, boolean highlighted) {
        int fill = !active ? SURFACE_DISABLED : highlighted ? SURFACE_HOVER : SURFACE;
        int edge = !active ? 0x40444B55 : highlighted ? 0xAA7DD3FC : EDGE;

        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, edge);
    }

    public static Component component(String text) {
        return Component.literal(text);
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, component(text), x, y, color);
    }

    public static void centeredText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.centeredText(font, component(text), x, y, color);
    }

    public static int width(Font font, String text) {
        return font.width(component(text));
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (width(font, text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        String value = text;
        while (!value.isEmpty() && width(font, value + ellipsis) > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ellipsis;
    }
}
