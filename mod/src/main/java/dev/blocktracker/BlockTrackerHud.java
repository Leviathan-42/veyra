package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class BlockTrackerHud {
    private static final int PANEL = 0xA8101722;
    private static final int EDGE = 0x805CA7FF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB9D7FF;
    private static final int GOOD = 0xFF62FF8B;
    private static final int WARN = 0xFFFFD166;
    private static final int BAD = 0xFFFF5C7A;

    private BlockTrackerHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        extractTargetHud(client, graphics);
        extractStatsHud(client, graphics);
        extractWaypointHud(client, graphics);
    }

    private static void extractTargetHud(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.hudLabelEnabled() || !BlockTrackerState.hasBlockTarget()) {
            return;
        }

        Identifier id = BlockTrackerState.targetId();
        BlockPos pos = BlockTrackerState.targetPos();
        int variantCount = BlockTrackerState.targetBlocks().size();
        String target = id.toString() + (variantCount > 1 ? " + deepslate" : "");
        String text = pos == null
                ? "Tracking " + target + " - no loaded match"
                : "Tracking " + target + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ();

        int textWidth = client.font.width(text);
        int x = (graphics.guiWidth() - textWidth) / 2;
        int y = 8;
        drawPanel(graphics, x - 7, y - 4, textWidth + 14, 17, EDGE);
        graphics.text(client.font, text, x, y, TEXT);
    }

    private static void extractStatsHud(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.statsHudEnabled() || client.player == null) {
            return;
        }

        Player player = client.player;
        List<Line> lines = new ArrayList<>();
        BlockPos pos = player.blockPosition();
        lines.add(new Line("XYZ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), TEXT));
        lines.add(new Line("FPS " + safeFps(client) + "  Ping " + safePing(client, player) + "ms", MUTED));
        lines.add(new Line("RAM " + ramText(), MUTED));

        String main = durabilityText("Tool", player.getMainHandItem());
        if (main != null) {
            lines.add(new Line(main, durabilityColor(player.getMainHandItem())));
        }

        String offhand = durabilityText("Offhand", player.getOffhandItem());
        if (offhand != null) {
            lines.add(new Line(offhand, durabilityColor(player.getOffhandItem())));
        }

        addArmorLine(lines, player, "Helmet", EquipmentSlot.HEAD);
        addArmorLine(lines, player, "Chest", EquipmentSlot.CHEST);
        addArmorLine(lines, player, "Legs", EquipmentSlot.LEGS);
        addArmorLine(lines, player, "Boots", EquipmentSlot.FEET);

        Collection<MobEffectInstance> effects = player.getActiveEffects();
        if (!effects.isEmpty()) {
            int shown = 0;
            for (MobEffectInstance effect : effects) {
                if (shown >= 5) {
                    lines.add(new Line("+" + (effects.size() - shown) + " more effects", MUTED));
                    break;
                }
                lines.add(new Line(effectText(effect), GOOD));
                shown++;
            }
        }

        drawLines(client, graphics, 8, 8, lines, 0x8042A08B);
    }

    private static void extractWaypointHud(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.waypointHudEnabled() || client.player == null) {
            return;
        }

        List<Line> lines = new ArrayList<>();
        BlockPos death = BlockTrackerState.deathMarker();
        if (death != null) {
            lines.add(new Line("Death " + distanceText(client.player, death), BAD));
        }

        int shown = 0;
        for (BlockTrackerState.Waypoint waypoint : BlockTrackerState.waypoints()) {
            if (shown >= 6) {
                lines.add(new Line("+" + (BlockTrackerState.waypoints().size() - shown) + " more waypoints", MUTED));
                break;
            }
            lines.add(new Line(waypoint.name() + " " + distanceText(client.player, waypoint.pos()), WARN));
            shown++;
        }

        if (lines.isEmpty()) {
            return;
        }

        int width = lines.stream().mapToInt(line -> client.font.width(line.text())).max().orElse(0) + 14;
        drawLines(client, graphics, graphics.guiWidth() - width - 8, 8, lines, 0x80FFC857);
    }

    private static void drawLines(Minecraft client, GuiGraphicsExtractor graphics, int x, int y, List<Line> lines, int edge) {
        int width = lines.stream().mapToInt(line -> client.font.width(line.text())).max().orElse(0) + 14;
        int height = (lines.size() * 10) + 8;
        drawPanel(graphics, x, y, width, height, edge);

        int textY = y + 5;
        for (Line line : lines) {
            graphics.text(client.font, line.text(), x + 7, textY, line.color());
            textY += 10;
        }
    }

    private static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int edge) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.outline(x, y, width, height, edge);
    }

    private static void addArmorLine(List<Line> lines, Player player, String label, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        String text = durabilityText(label, stack);
        if (text != null) {
            lines.add(new Line(text, durabilityColor(stack)));
        }
    }

    private static int safeFps(Minecraft client) {
        try {
            return client.getFps();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int safePing(Minecraft client, Player player) {
        try {
            if (client.getConnection() == null) {
                return 0;
            }

            PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
            return info == null ? 0 : info.getLatency();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String ramText() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        return mb(used) + " / " + mb(max) + " MB";
    }

    private static long mb(long bytes) {
        return bytes / 1024L / 1024L;
    }

    private static String durabilityText(String label, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return null;
        }

        int max = stack.getMaxDamage();
        int left = max - stack.getDamageValue();
        return label + " " + left + "/" + max;
    }

    private static int durabilityColor(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return MUTED;
        }

        float ratio = (max - stack.getDamageValue()) / (float) max;
        if (ratio <= 0.2F) {
            return BAD;
        }
        if (ratio <= 0.45F) {
            return WARN;
        }
        return GOOD;
    }

    private static String effectText(MobEffectInstance effect) {
        String name = effect.getEffect().value().getDisplayName().getString();
        int amplifier = effect.getAmplifier() + 1;
        return name + " " + amplifier + " " + ticksToTime(effect.getDuration());
    }

    private static String ticksToTime(int ticks) {
        if (ticks < 0) {
            return "∞";
        }

        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static String distanceText(Player player, BlockPos pos) {
        int distance = (int) Math.round(Math.sqrt(pos.distSqr(player.blockPosition())));
        return distance + "m @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private record Line(String text, int color) {
    }
}
