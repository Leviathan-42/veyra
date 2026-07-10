package dev.blocktracker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public final class VeyraUi {
    public static int BACKGROUND;
    public static int IN_GAME_BACKGROUND;
    public static int PANEL;
    public static int SURFACE;
    public static int SURFACE_HOVER;
    public static int SURFACE_DISABLED;
    public static int ACCENT;
    public static int TEAL;
    public static int EDGE;
    public static int TEXT;
    public static int MUTED;
    public static int SUBTLE;
    public static int GOOD;
    public static int BAD;

    private static Theme theme = Theme.VEYRA;
    private static ButtonStyle buttonStyle = ButtonStyle.SOFT;

    static {
        loadAppearance();
        applyTheme();
    }

    private VeyraUi() {
    }

    public static Theme theme() {
        return theme;
    }

    public static String themeName() {
        return theme.displayName;
    }

    public static void cycleTheme() {
        Theme[] themes = Theme.values();
        theme = themes[(theme.ordinal() + 1) % themes.length];
        applyTheme();
        saveAppearance();
    }

    public static ButtonStyle buttonStyle() {
        return buttonStyle;
    }

    public static String buttonStyleName() {
        return buttonStyle.displayName;
    }

    public static void cycleButtonStyle() {
        ButtonStyle[] styles = ButtonStyle.values();
        buttonStyle = styles[(buttonStyle.ordinal() + 1) % styles.length];
        saveAppearance();
    }

    public static void resetAppearance() {
        theme = Theme.VEYRA;
        buttonStyle = ButtonStyle.SOFT;
        applyTheme();
        saveAppearance();
    }

    private static void loadAppearance() {
        Path path = appearancePath();
        if (!Files.isRegularFile(path)) {
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            theme = parseEnum(Theme.class, properties.getProperty("theme"), theme);
            buttonStyle = parseEnum(ButtonStyle.class, properties.getProperty("buttonStyle"), buttonStyle);
        } catch (IOException ignored) {
        }
    }

    private static void saveAppearance() {
        Path path = appearancePath();
        Properties properties = new Properties();
        properties.setProperty("theme", theme.name());
        properties.setProperty("buttonStyle", buttonStyle.name());
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "Veyra appearance settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static Path appearancePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("veyra-appearance.properties");
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void applyTheme() {
        BACKGROUND = theme.background;
        IN_GAME_BACKGROUND = theme.inGameBackground;
        PANEL = theme.panel;
        SURFACE = theme.surface;
        SURFACE_HOVER = theme.surfaceHover;
        SURFACE_DISABLED = theme.surfaceDisabled;
        ACCENT = theme.accent;
        TEAL = theme.teal;
        EDGE = theme.edge;
        TEXT = theme.text;
        MUTED = theme.muted;
        SUBTLE = theme.subtle;
        GOOD = theme.good;
        BAD = theme.bad;
    }

    public static void screenBackground(GuiGraphicsExtractor graphics, int width, int height, boolean inGame) {
        int background = inGame ? IN_GAME_BACKGROUND : BACKGROUND;
        graphics.fill(0, 0, width, height, background);

        int grid = withAlpha(EDGE, inGame ? 0x18 : 0x24);
        for (int x = 24; x < width; x += 48) {
            graphics.fill(x, 0, x + 1, height, grid);
        }
        for (int y = 24; y < height; y += 48) {
            graphics.fill(0, y, width, y + 1, grid);
        }

        graphics.fill(0, 0, width, 2, withAlpha(ACCENT, 0x55));
        graphics.fill(0, height - 2, width, height, withAlpha(TEAL, 0x22));
    }

    public static void titleBackground(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, theme.titleBackground);
        screenBackground(graphics, width, height, false);
        graphics.fill(0, 0, width, height, theme.titleOverlay);
        graphics.fill(width / 2 - 230, 62, width / 2 + 230, 63, withAlpha(ACCENT, 0x44));
        graphics.fill(width / 2 - 90, 62, width / 2 + 90, 64, theme.titleAccentLine);
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x + 5, y + 6, x + width + 5, y + height + 6, 0x66000000);
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, withAlpha(ACCENT, 0x88));
        cornerBrackets(graphics, x, y, width, height, withAlpha(ACCENT, 0xAA));
    }

    public static void shell(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int width,
            int height,
            String title,
            String subtitle,
            String badge
    ) {
        panel(graphics, x, y, width, height);
        graphics.fill(x + 1, y + 3, x + width - 1, y + 50, withAlpha(SURFACE, 0xB8));
        graphics.fill(x + 18, y + 50, x + width - 18, y + 51, withAlpha(EDGE, 0x88));
        mark(graphics, x + 18, y + 14, 22);
        text(graphics, font, title, x + 51, y + 13, TEXT);
        text(graphics, font, subtitle, x + 51, y + 29, MUTED);

        if (badge != null && !badge.isBlank()) {
            int badgeWidth = width(font, badge) + 16;
            int badgeX = x + width - badgeWidth - 18;
            graphics.fill(badgeX, y + 17, badgeX + badgeWidth, y + 35, withAlpha(ACCENT, 0x22));
            graphics.outline(badgeX, y + 17, badgeWidth, 18, withAlpha(ACCENT, 0x88));
            centeredText(graphics, font, badge, badgeX + badgeWidth / 2, y + 22, ACCENT);
        }
    }

    public static void card(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean highlighted) {
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x33000000);
        graphics.fill(x, y, x + width, y + height, highlighted ? SURFACE_HOVER : SURFACE);
        graphics.outline(x, y, width, height, highlighted ? withAlpha(ACCENT, 0xAA) : EDGE);
        if (highlighted) {
            graphics.fill(x, y + 5, x + 2, y + height - 5, ACCENT);
        }
    }

    public static void sectionLabel(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width) {
        String text = label.toUpperCase(Locale.ROOT);
        text(graphics, font, text, x, y, ACCENT);
        int start = x + width(font, text) + 9;
        if (start < x + width) {
            graphics.fill(start, y + 4, x + width, y + 5, withAlpha(EDGE, 0x88));
        }
    }

    public static void mark(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int mid = size / 2;
        int color = withAlpha(ACCENT, 0xDD);
        int inner = withAlpha(PANEL, 0xF8);
        for (int row = 0; row < mid; row++) {
            int inset = mid - row - 1;
            graphics.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
            graphics.fill(x + inset, y + size - row - 1, x + size - inset, y + size - row, color);
        }
        for (int row = 4; row < size - 4; row++) {
            int inset = Math.abs(mid - row) + 4;
            if (x + inset < x + size - inset) {
                graphics.fill(x + inset, y + row, x + size - inset, y + row + 1, inner);
            }
        }
        text(graphics, net.minecraft.client.Minecraft.getInstance().font, "V", x + mid - 3, y + mid - 4, TEXT);
    }

    public static void cornerBrackets(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int length = 10;
        graphics.fill(x, y, x + length, y + 1, color);
        graphics.fill(x, y, x + 1, y + length, color);
        graphics.fill(x + width - length, y, x + width, y + 1, color);
        graphics.fill(x + width - 1, y, x + width, y + length, color);
        graphics.fill(x, y + height - 1, x + length, y + height, color);
        graphics.fill(x, y + height - length, x + 1, y + height, color);
        graphics.fill(x + width - length, y + height - 1, x + width, y + height, color);
        graphics.fill(x + width - 1, y + height - length, x + width, y + height, color);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static void button(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean active, boolean highlighted) {
        int fill;
        int edge;
        if (!active) {
            fill = SURFACE_DISABLED;
            edge = theme.disabledEdge;
        } else if (buttonStyle == ButtonStyle.SOLID) {
            fill = highlighted ? theme.accentStrong : theme.accentSoft;
            edge = highlighted ? theme.accent : theme.edge;
        } else if (buttonStyle == ButtonStyle.OUTLINE) {
            fill = highlighted ? theme.outlineHover : theme.outlineFill;
            edge = highlighted ? theme.accent : theme.edge;
        } else {
            fill = highlighted ? SURFACE_HOVER : SURFACE;
            edge = highlighted ? theme.hoverEdge : EDGE;
        }

        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x33000000);
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.outline(x, y, width, height, edge);
        if (active && highlighted) {
            graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ACCENT);
            graphics.fill(x + 3, y + height - 2, x + width - 2, y + height - 1, withAlpha(ACCENT, 0x88));
        }
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

    public enum ButtonStyle {
        SOFT("Soft"),
        SOLID("Solid"),
        OUTLINE("Outline");

        private final String displayName;

        ButtonStyle(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum Theme {
        VEYRA("Veyra", 0xF0060709, 0x8A060709, 0xF2161A20, 0xE01F242C, 0xF02A313C, 0xAA191D23, 0xFF7DD3FC, 0xFF8BE9D8, 0x5538424F, 0xFFF4F7FA, 0xFF8B96A5, 0xFF5F6B7A, 0xFF5EEAD4, 0xFFFB7185, 0xFF07080A, 0x22000000, 0x557DD3FC, 0x40444B55, 0xAA7DD3FC, 0x662E7490, 0xCC155E75, 0x22000000, 0x330E7490),
        AMETHYST("Amethyst", 0xF00A0612, 0x8A0A0612, 0xF21D1430, 0xE0271B3F, 0xF0342554, 0xAA1E1730, 0xFFC084FC, 0xFFF0ABFC, 0x555B4B78, 0xFFFAF5FF, 0xFFC4B5D8, 0xFF8B7AA6, 0xFFD8B4FE, 0xFFFF8FA3, 0xFF10081A, 0x26000000, 0x66C084FC, 0x40514362, 0xAAC084FC, 0x665B21B6, 0xCC6D28D9, 0x22000000, 0x335B21B6),
        CRIMSON("Crimson", 0xF0110608, 0x8A110608, 0xF22B1116, 0xE0381820, 0xF04C202A, 0xAA2A1218, 0xFFFB7185, 0xFFFCA5A5, 0x557A3642, 0xFFFFF1F2, 0xFFF0A5AE, 0xFFB66A75, 0xFFFFB4A2, 0xFFFF6B81, 0xFF16070A, 0x26000000, 0x66FB7185, 0x40583A40, 0xAAFB7185, 0x66881D2D, 0xCC9F2438, 0x22000000, 0x33881D2D),
        EMERALD("Emerald", 0xF0040D0A, 0x8A040D0A, 0xF20D241C, 0xE0163027, 0xF01F4538, 0xAA10251E, 0xFF34D399, 0xFF99F6E4, 0x55446B5B, 0xFFF0FDF8, 0xFFA7F3D0, 0xFF6FAF95, 0xFF86EFAC, 0xFFFF7A90, 0xFF06120E, 0x26000000, 0x6634D399, 0x40415851, 0xAA34D399, 0x660F766E, 0xCC108B78, 0x22000000, 0x330F766E),
        GOLD("Gold", 0xF0120D04, 0x8A120D04, 0xF22D2311, 0xE03B2F19, 0xF0504022, 0xAA2B2213, 0xFFFBBF24, 0xFFFDE68A, 0x55746336, 0xFFFFFBEB, 0xFFE8D7A0, 0xFFAA9155, 0xFFFACC15, 0xFFFB7185, 0xFF171006, 0x26000000, 0x66FBBF24, 0x40595442, 0xAAFBBF24, 0x66924A16, 0xCCB45309, 0x22000000, 0x33924A16),
        MIDNIGHT("Midnight", 0xF0031020, 0x8A031020, 0xF20A1B33, 0xE0122847, 0xF01A3A66, 0xAA0C203A, 0xFF60A5FA, 0xFF93C5FD, 0x55314B6E, 0xFFEFF6FF, 0xFFA9C4E5, 0xFF7190B5, 0xFF67E8F9, 0xFFFF83A1, 0xFF04101F, 0x26000000, 0x6660A5FA, 0x4031455F, 0xAA60A5FA, 0x661D4ED8, 0xCC2563EB, 0x22000000, 0x331D4ED8);

        private final String displayName;
        private final int background;
        private final int inGameBackground;
        private final int panel;
        private final int surface;
        private final int surfaceHover;
        private final int surfaceDisabled;
        private final int accent;
        private final int teal;
        private final int edge;
        private final int text;
        private final int muted;
        private final int subtle;
        private final int good;
        private final int bad;
        private final int titleBackground;
        private final int titleOverlay;
        private final int titleAccentLine;
        private final int disabledEdge;
        private final int hoverEdge;
        private final int accentSoft;
        private final int accentStrong;
        private final int outlineFill;
        private final int outlineHover;

        Theme(String displayName, int background, int inGameBackground, int panel, int surface, int surfaceHover, int surfaceDisabled, int accent, int teal, int edge, int text, int muted, int subtle, int good, int bad, int titleBackground, int titleOverlay, int titleAccentLine, int disabledEdge, int hoverEdge, int accentSoft, int accentStrong, int outlineFill, int outlineHover) {
            this.displayName = displayName;
            this.background = background;
            this.inGameBackground = inGameBackground;
            this.panel = panel;
            this.surface = surface;
            this.surfaceHover = surfaceHover;
            this.surfaceDisabled = surfaceDisabled;
            this.accent = accent;
            this.teal = teal;
            this.edge = edge;
            this.text = text;
            this.muted = muted;
            this.subtle = subtle;
            this.good = good;
            this.bad = bad;
            this.titleBackground = titleBackground;
            this.titleOverlay = titleOverlay;
            this.titleAccentLine = titleAccentLine;
            this.disabledEdge = disabledEdge;
            this.hoverEdge = hoverEdge;
            this.accentSoft = accentSoft;
            this.accentStrong = accentStrong;
            this.outlineFill = outlineFill;
            this.outlineHover = outlineHover;
        }
    }
}
