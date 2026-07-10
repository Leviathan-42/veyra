package dev.blocktracker;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** One short, skippable Minecraft-side startup sequence per client session. */
public final class VeyraBootSequence {
    private static final long DURATION_MS = 1_800L;
    private static long startedAt;
    private static boolean shown;

    private VeyraBootSequence() {
    }

    public static void beginIfNeeded() {
        if (!shown && startedAt == 0L) {
            startedAt = System.currentTimeMillis();
        }
    }

    public static boolean active() {
        beginIfNeeded();
        if (shown) {
            return false;
        }
        if (System.currentTimeMillis() - startedAt >= DURATION_MS) {
            shown = true;
            return false;
        }
        return true;
    }

    public static void skip() {
        shown = true;
    }

    public static boolean render(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        if (!active()) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - startedAt;
        float progress = Math.max(0.0F, Math.min(1.0F, elapsed / (float) DURATION_MS));
        float eased = 1.0F - (float) Math.pow(1.0F - progress, 3.0D);

        VeyraUi.titleBackground(graphics, width, height);
        int panelW = Math.min(390, width - 36);
        int panelH = Math.min(210, height - 30);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        VeyraUi.panel(graphics, panelX, panelY, panelW, panelH);

        int markSize = 58 + Math.round(eased * 18.0F);
        int markX = (width - markSize) / 2;
        int markY = panelY + 32;
        VeyraUi.mark(graphics, markX, markY, markSize);

        double angle = progress * Math.PI * 4.0D;
        int orbitX = width / 2 + (int) Math.round(Math.cos(angle) * 62.0D);
        int orbitY = markY + markSize / 2 + (int) Math.round(Math.sin(angle) * 34.0D);
        graphics.fill(orbitX - 2, orbitY - 2, orbitX + 3, orbitY + 3, VeyraUi.ACCENT);

        String stage = progress < 0.34F
                ? "INITIALIZING INTERFACE"
                : progress < 0.72F
                ? "LINKING CLIENT MODULES"
                : "STARTING VEYRA";
        VeyraUi.centeredText(graphics, font, "VEYRA", width / 2, panelY + 122, VeyraUi.TEXT);
        VeyraUi.centeredText(graphics, font, stage, width / 2, panelY + 143, VeyraUi.ACCENT);

        int barX = panelX + 42;
        int barY = panelY + 166;
        int barW = panelW - 84;
        graphics.fill(barX, barY, barX + barW, barY + 4, VeyraUi.EDGE);
        graphics.fill(barX, barY, barX + Math.max(2, Math.round(barW * eased)), barY + 4, VeyraUi.ACCENT);
        VeyraUi.centeredText(graphics, font, "CLICK TO SKIP", width / 2, panelY + 184, VeyraUi.SUBTLE);
        return true;
    }
}
