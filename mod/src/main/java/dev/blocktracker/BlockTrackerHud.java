package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class BlockTrackerHud {
    private static final int PANEL = 0xAA11151B;
    private static final int EDGE = 0x5538424F;
    private static final int TEXT = 0xFFF4F7FA;
    private static final int MUTED = 0xFF8B96A5;
    private static final int GOOD = 0xFF5EEAD4;
    private static final int WARN = 0xFFFBBF24;
    private static final int BAD = 0xFFFB7185;

    private BlockTrackerHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        extractCustomCrosshair(graphics);
        extractFreecamHud(client, graphics);
        extractTargetHud(client, graphics);
        extractStatsHud(client, graphics);
        extractWaypointHud(client, graphics);
    }

    private static void extractCustomCrosshair(GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.customCrosshairEnabled()) {
            return;
        }

        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        int size = BlockTrackerState.crosshairSize();
        int gap = Math.max(2, size / 2);
        int color = 0xEAF4F7FA;
        int accent = 0xFF7DD3FC;
        int outline = 0xAA050608;

        switch (BlockTrackerState.crosshairStyle()) {
            case 1 -> {
                graphics.fill(x - 2, y - 2, x + 3, y + 3, outline);
                graphics.fill(x - 1, y - 1, x + 2, y + 2, accent);
            }
            case 2 -> {
                drawCrosshairLine(graphics, x - gap - size, y - gap, x - gap, y - gap + 1, outline, color);
                drawCrosshairLine(graphics, x - gap - size, y + gap, x - gap, y + gap + 1, outline, color);
                drawCrosshairLine(graphics, x + gap, y - gap, x + gap + size, y - gap + 1, outline, color);
                drawCrosshairLine(graphics, x + gap, y + gap, x + gap + size, y + gap + 1, outline, color);
            }
            default -> {
                drawCrosshairLine(graphics, x - gap - size, y, x - gap, y + 1, outline, color);
                drawCrosshairLine(graphics, x + gap, y, x + gap + size, y + 1, outline, color);
                drawCrosshairLine(graphics, x, y - gap - size, x + 1, y - gap, outline, color);
                drawCrosshairLine(graphics, x, y + gap, x + 1, y + gap + size, outline, color);
                graphics.fill(x, y, x + 1, y + 1, accent);
            }
        }
    }

    private static void drawCrosshairLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int outline, int color) {
        graphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, outline);
        graphics.fill(x1, y1, x2, y2, color);
    }

    private static void extractVulkanSafeWorldOverlay(Minecraft client, GuiGraphicsExtractor graphics) {
        if (client.player == null) {
            return;
        }

        Player player = client.player;
        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;

        BlockPos target = BlockTrackerState.targetPos();
        if (target != null && (BlockTrackerState.blockEspEnabled() || BlockTrackerState.tracerEnabled())) {
            drawWorldMarker(graphics, player, Vec3.atCenterOf(target), centerX, centerY, "", 0xFFFBBF24, BlockTrackerState.tracerEnabled());
        }

        if (BlockTrackerState.waypointHudEnabled()) {
            for (BlockTrackerState.Waypoint waypoint : BlockTrackerState.waypoints()) {
                drawWorldMarker(graphics, player, Vec3.atCenterOf(waypoint.pos()), centerX, centerY, waypoint.name(), 0xFFFBBF24, false);
            }
            BlockPos death = BlockTrackerState.deathMarker();
            if (death != null) {
                drawWorldMarker(graphics, player, Vec3.atCenterOf(death), centerX, centerY, "DEATH", 0xFFFB7185, false);
            }
        }

        if (BlockTrackerState.entityEspEnabled() && client.level != null) {
            int rendered = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (rendered >= 48) {
                    break;
                }
                if (entity == player || !entity.isAlive() || entity.distanceToSqr(player) > 96.0D * 96.0D) {
                    continue;
                }

                int color = entityColor(entity);
                if (color == 0) {
                    continue;
                }

                drawWorldMarker(graphics, player, entity.position().add(0.0D, entity.getBbHeight() * 0.55D, 0.0D), centerX, centerY, "", color, false);
                rendered++;
            }
        }
    }

    private static void drawWorldMarker(GuiGraphicsExtractor graphics, Player player, Vec3 target, int centerX, int centerY, String label, int color, boolean tracer) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x() - eye.x();
        double dy = target.y() - eye.y();
        double dz = target.z() - eye.z();
        double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (distance < 0.01D) {
            return;
        }

        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double yawDelta = wrapDegrees(targetYaw - player.getYRot());
        double visible = Math.max(-1.0D, Math.min(1.0D, yawDelta / 82.0D));
        int x = centerX + (int) Math.round(visible * (graphics.guiWidth() * 0.42D));
        int y = centerY - (int) Math.round(Math.max(-80.0D, Math.min(80.0D, (dy / Math.max(8.0D, distance)) * 120.0D)));
        x = Math.max(18, Math.min(graphics.guiWidth() - 18, x));
        y = Math.max(28, Math.min(graphics.guiHeight() - 28, y));

        graphics.fill(x - 7, y - 7, x + 8, y - 5, color);
        graphics.fill(x - 7, y + 6, x + 8, y + 8, color);
        graphics.fill(x - 7, y - 7, x - 5, y + 8, color);
        graphics.fill(x + 6, y - 7, x + 8, y + 8, color);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xAA050608);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);

        if (tracer) {
            drawHudLine(graphics, centerX, centerY + 12, x, y, 0xAA050608, 2);
            drawHudLine(graphics, centerX, centerY + 12, x, y, color, 1);
        }

        // Keep ESP clean: outline only, no names or distance labels.
    }

    private static int entityColor(Entity entity) {
        if (entity instanceof Player) {
            return BlockTrackerState.playerEspEnabled() ? 0xFFFBBF24 : 0;
        }

        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MONSTER) {
            return BlockTrackerState.hostileEspEnabled() ? 0xFFFBBF24 : 0;
        }

        if (category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.AXOLOTLS || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT) {
            return BlockTrackerState.animalEspEnabled() ? 0xFFFBBF24 : 0;
        }

        return 0;
    }

    private static double wrapDegrees(double value) {
        value %= 360.0D;
        if (value >= 180.0D) {
            value -= 360.0D;
        }
        if (value < -180.0D) {
            value += 360.0D;
        }
        return value;
    }

    private static void drawHudLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color, int thickness) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps <= 0) {
            graphics.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }

        for (int step = 0; step <= steps; step++) {
            int x = x1 + ((x2 - x1) * step / steps);
            int y = y1 + ((y2 - y1) * step / steps);
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    private static void extractFreecamHud(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!VeyraFreecam.enabled()) {
            return;
        }

        String text = "Freecam  C to return";
        int textWidth = client.font.width(text);
        int x = (graphics.guiWidth() - textWidth) / 2;
        int y = graphics.guiHeight() - 54;
        drawPanel(graphics, x - 7, y - 4, textWidth + 14, 17, 0x887DD3FC);
        graphics.text(client.font, text, x, y, 0xFF7DD3FC);
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

        List<BlockTrackerState.Waypoint> waypoints = new ArrayList<>(BlockTrackerState.waypoints());
        waypoints.sort(Comparator.comparingDouble(waypoint -> waypoint.pos().distSqr(client.player.blockPosition())));

        int shown = 0;
        for (BlockTrackerState.Waypoint waypoint : waypoints) {
            if (shown >= 6) {
                lines.add(new Line("+" + (waypoints.size() - shown) + " more waypoints", MUTED));
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
        BlockPos playerPos = player.blockPosition();
        int distance = (int) Math.round(Math.sqrt(pos.distSqr(playerPos)));
        int yDelta = pos.getY() - playerPos.getY();
        String yText = yDelta == 0 ? "same Y" : (yDelta > 0 ? "+" : "") + yDelta + "Y";
        return directionText(playerPos, pos) + " " + distance + "m " + yText + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static String directionText(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
            return "here";
        }

        String northSouth = dz < -1 ? "N" : dz > 1 ? "S" : "";
        String eastWest = dx > 1 ? "E" : dx < -1 ? "W" : "";
        return northSouth + eastWest;
    }

    private record Line(String text, int color) {
    }
}
