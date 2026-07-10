package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
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
        extractModulesHud(client, graphics, false);
    }

    private static void extractCustomCrosshair(GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.customCrosshairEnabled()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        int size = BlockTrackerState.crosshairSize();
        int gap = Math.max(2, size / 2);
        int color = crosshairColor(client);
        int accent = color;
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

    private static int crosshairColor(Minecraft client) {
        if (client == null || client.player == null || client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS) {
            return BAD;
        }

        if (client.crosshairPickEntity != null || client.hitResult.getType() == HitResult.Type.ENTITY) {
            return GOOD;
        }

        if (client.hitResult.getType() == HitResult.Type.BLOCK) {
            return 0xFF22D3EE;
        }

        return BAD;
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
            for (BlockTrackerState.Waypoint waypoint : BlockTrackerState.waypointsFor(client.level)) {
                drawWorldMarker(graphics, player, Vec3.atCenterOf(waypoint.pos()), centerX, centerY, waypoint.name(), 0xFFFBBF24, false);
            }
            BlockPos death = BlockTrackerState.deathMarkerFor(client.level);
            if (death != null) {
                drawWorldMarker(graphics, player, Vec3.atCenterOf(death), centerX, centerY, "DEATH", 0xFFFB7185, false);
            }
        }

        if (BlockTrackerState.entityEspEnabled() && client.level != null) {
            int rendered = 0;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (rendered >= 128) {
                    break;
                }
                if (entity == player || !entity.isAlive() || entity.distanceToSqr(player) > 256.0D * 256.0D) {
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

        String text = "Freecam  " + VeyraKeybinds.freecamKeyName() + " to return";
        String speed = "Scroll speed  " + VeyraFreecam.speedText();
        int width = Math.max(Math.max(client.font.width(text), client.font.width(speed)), 112) + 14;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 66;
        drawPanel(graphics, x, y - 4, width, 34, 0x887DD3FC);
        graphics.text(client.font, text, x + 7, y, 0xFF7DD3FC);
        graphics.text(client.font, speed, x + 7, y + 10, TEXT);

        int barX = x + 7;
        int barY = y + 23;
        int barWidth = width - 14;
        int fillWidth = Math.max(2, (int) Math.round(barWidth * VeyraFreecam.speedRatio()));
        graphics.fill(barX, barY, barX + barWidth, barY + 3, 0x5538424F);
        graphics.fill(barX, barY, barX + fillWidth, barY + 3, 0xFF7DD3FC);
    }

    private static void extractTargetHud(Minecraft client, GuiGraphicsExtractor graphics) {
        if (!BlockTrackerState.hudLabelEnabled() || !BlockTrackerState.hasBlockTarget()) {
            return;
        }

        List<BlockTrackerState.BlockTarget> targets = BlockTrackerState.blockTargets();
        List<Line> lines = new ArrayList<>();
        int index = 1;
        for (BlockTrackerState.BlockTarget blockTarget : targets) {
            Identifier id = blockTarget.id();
            BlockPos pos = blockTarget.pos();
            BlockScan.ScanResults results = blockTarget.results();
            int variantCount = blockTarget.blocks().size();
            String target = id.toString() + (variantCount > 1 ? " + deepslate" : "");
            int matchCount = results == null ? 0 : results.matchCount();
            String scanStatus = results == null
                    ? "waiting"
                    : results.complete()
                    ? matchCount + (matchCount == 1 ? " match" : " matches")
                    : matchCount + " found / scanning " + results.progressPercent() + "%";
            String location = pos == null
                    ? ""
                    : " / nearest " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
            String text = index + ". " + target + " - " + scanStatus + location;
            lines.add(new Line(text, blockTargetColor(index - 1)));
            index++;
        }

        if (lines.isEmpty()) {
            return;
        }

        int width = lines.stream().mapToInt(line -> client.font.width(line.text())).max().orElse(0) + 14;
        drawLines(client, graphics, (graphics.guiWidth() - width) / 2, 8, lines, EDGE);
    }

    public static void extractEditPreview(GuiGraphicsExtractor graphics) {
        extractModulesHud(Minecraft.getInstance(), graphics, true);
    }

    public static HudBox findHudBox(Minecraft client, int mouseX, int mouseY) {
        List<HudBox> boxes = buildHudBoxes(client);
        for (int index = boxes.size() - 1; index >= 0; index--) {
            HudBox box = boxes.get(index);
            if (mouseX >= box.x() && mouseX <= box.x() + box.width() && mouseY >= box.y() && mouseY <= box.y() + box.height()) {
                return box;
            }
        }
        return null;
    }

    private static void extractModulesHud(Minecraft client, GuiGraphicsExtractor graphics, boolean editMode) {
        if (!BlockTrackerState.statsHudEnabled() || client.player == null) {
            return;
        }

        for (HudBox box : buildHudBoxes(client)) {
            drawHudBox(client, graphics, box, editMode);
        }
    }

    private static List<HudBox> buildHudBoxes(Minecraft client) {
        List<HudBox> boxes = new ArrayList<>();
        if (client.player == null) {
            return boxes;
        }

        List<Line> mainLines = new ArrayList<>();
        for (BlockTrackerState.HudModule module : BlockTrackerState.HudModule.values()) {
            BlockTrackerState.HudModuleState state = BlockTrackerState.hudModuleState(module);
            if (!state.enabled()) {
                continue;
            }

            List<Line> lines = moduleLines(client, module);
            if (lines.isEmpty()) {
                continue;
            }

            if (state.detached() || module == BlockTrackerState.HudModule.NETWORK) {
                boxes.add(new HudBox(module, state.x(), state.y(), boxWidth(client, lines, module), boxHeight(lines, module), module.title(), lines, module == BlockTrackerState.HudModule.WAYPOINTS ? 0x80FFC857 : 0x8042A08B));
            } else {
                if (!mainLines.isEmpty() && !BlockTrackerState.hudCompactMode()) {
                    mainLines.add(new Line("", MUTED));
                }
                mainLines.addAll(lines);
            }
        }

        if (!mainLines.isEmpty()) {
            boxes.add(0, new HudBox(null, BlockTrackerState.hudMainX(), BlockTrackerState.hudMainY(), boxWidth(client, mainLines, null), boxHeight(mainLines, null), "Main HUD", mainLines, 0x8042A08B));
        }
        return boxes;
    }

    private static List<Line> moduleLines(Minecraft client, BlockTrackerState.HudModule module) {
        Player player = client.player;
        if (player == null) {
            return List.of();
        }

        boolean compact = BlockTrackerState.hudCompactMode();
        List<Line> lines = new ArrayList<>();
        switch (module) {
            case POSITION -> {
                BlockPos pos = player.blockPosition();
                lines.add(new Line((compact ? "XYZ " : "Position ") + pos.getX() + " " + pos.getY() + " " + pos.getZ(), TEXT));
            }
            case FPS -> lines.add(new Line("FPS " + safeFps(client), MUTED));
            case RAM -> lines.add(new Line((compact ? "RAM " : "Memory ") + ramText(), MUTED));
            case DURABILITY -> addDurabilityLines(lines, player, compact);
            case EFFECTS -> addEffectLines(lines, player, compact);
            case WAYPOINTS -> addWaypointLines(lines, client, player, compact);
            case NETWORK -> {
                int ping = VeyraHudTelemetry.currentPing(client);
                lines.add(new Line("Ping " + ping + "ms  avg " + VeyraHudTelemetry.averagePing() + "  max " + VeyraHudTelemetry.maxPing(), pingColor(ping)));
            }
            case VITALS -> addVitalLines(lines, player, compact);
            case MUSIC -> lines.add(new Line("Music  " + VeyraHudTelemetry.musicText(client), MUTED));
            case CLOCK -> {
                lines.add(new Line("Clock  " + VeyraHudTelemetry.clockText(), TEXT));
                lines.add(new Line("Session  " + VeyraHudTelemetry.sessionText(), MUTED));
            }
            case INPUT -> addInputLines(lines, client);
        }
        return lines;
    }

    private static void addDurabilityLines(List<Line> lines, Player player, boolean compact) {
        String main = durabilityText(compact ? "Tool" : "Main tool", player.getMainHandItem());
        if (main != null) {
            lines.add(new Line(main, durabilityColor(player.getMainHandItem())));
        }
        String offhand = durabilityText("Offhand", player.getOffhandItem());
        if (offhand != null && !compact) {
            lines.add(new Line(offhand, durabilityColor(player.getOffhandItem())));
        }
        if (!compact) {
            addArmorLine(lines, player, "Helmet", EquipmentSlot.HEAD);
            addArmorLine(lines, player, "Chest", EquipmentSlot.CHEST);
            addArmorLine(lines, player, "Legs", EquipmentSlot.LEGS);
            addArmorLine(lines, player, "Boots", EquipmentSlot.FEET);
        }
    }

    private static void addEffectLines(List<Line> lines, Player player, boolean compact) {
        Collection<MobEffectInstance> effects = player.getActiveEffects();
        List<MobEffectInstance> sortedEffects = new ArrayList<>(effects);
        sortedEffects.sort(Comparator.comparingInt(effect -> effect.getDuration() < 0 ? Integer.MAX_VALUE : effect.getDuration()));
        int limit = compact ? 2 : 5;
        int shown = 0;
        for (MobEffectInstance effect : sortedEffects) {
            if (shown >= limit) {
                lines.add(new Line("+" + (effects.size() - shown) + " more effects", MUTED));
                break;
            }
            int duration = effect.getDuration();
            int color = duration < 0 ? GOOD : duration <= 100 ? BAD : duration <= 200 ? WARN : GOOD;
            lines.add(new Line((color == BAD ? "! " : "") + effectText(effect), color));
            shown++;
        }
    }

    private static void addWaypointLines(List<Line> lines, Minecraft client, Player player, boolean compact) {
        if (!BlockTrackerState.waypointHudEnabled()) {
            return;
        }
        BlockPos death = BlockTrackerState.deathMarkerFor(client.level);
        if (death != null) {
            lines.add(new Line("Death " + distanceText(player, death), BAD));
        }
        List<BlockTrackerState.Waypoint> waypoints = new ArrayList<>(BlockTrackerState.waypointsFor(client.level));
        waypoints.sort(Comparator.comparingDouble(waypoint -> waypoint.pos().distSqr(player.blockPosition())));
        int limit = compact ? 3 : 6;
        int shown = 0;
        for (BlockTrackerState.Waypoint waypoint : waypoints) {
            if (shown >= limit) {
                lines.add(new Line("+" + (waypoints.size() - shown) + " more waypoints", MUTED));
                break;
            }
            lines.add(new Line(waypoint.name() + " " + distanceText(player, waypoint.pos()), WARN));
            shown++;
        }
    }

    private static int blockTargetColor(int index) {
        return switch (index) {
            case 1 -> 0xFF5EEAD4;
            case 2 -> 0xFFC084FC;
            default -> WARN;
        };
    }

    private static void drawLines(Minecraft client, GuiGraphicsExtractor graphics, int x, int y, List<Line> lines, int edge) {
        drawLines(client, graphics, x, y, lines, edge, false, "");
    }

    private static int boxWidth(Minecraft client, List<Line> lines) {
        return boxWidth(client, lines, null);
    }

    private static int boxWidth(Minecraft client, List<Line> lines, BlockTrackerState.HudModule module) {
        int contentWidth = lines.stream().mapToInt(line -> line.text().isEmpty() ? 26 : client.font.width(line.text())).max().orElse(0) + 14;
        if (module == BlockTrackerState.HudModule.NETWORK) {
            contentWidth = Math.max(contentWidth, 156);
        }
        return scale(contentWidth);
    }

    private static int boxHeight(List<Line> lines) {
        return boxHeight(lines, null);
    }

    private static int boxHeight(List<Line> lines, BlockTrackerState.HudModule module) {
        int graphHeight = module == BlockTrackerState.HudModule.NETWORK ? 30 : 0;
        return Math.max(scale(20), scale((lines.size() * 10) + 8 + graphHeight));
    }

    private static void drawLines(Minecraft client, GuiGraphicsExtractor graphics, int x, int y, List<Line> lines, int edge, boolean editMode, String title) {
        int lineHeight = scale(10);
        int paddingX = scale(7);
        int paddingY = scale(5);
        int width = boxWidth(client, lines);
        int height = boxHeight(lines) + (editMode ? scale(10) : 0);
        drawPanel(graphics, x, y, width, height, edge);

        if (editMode) {
            graphics.fill(x, y, x + width, y + scale(12), 0xAA263340);
            graphics.text(client.font, title, x + paddingX, y + 2, 0xFF7DD3FC);
        }

        int textY = y + paddingY + (editMode ? scale(10) : 0);
        for (Line line : lines) {
            if (!line.text().isEmpty()) {
                graphics.text(client.font, line.text(), x + paddingX, textY, line.color());
            }
            textY += lineHeight;
        }
    }

    private static void drawHudBox(Minecraft client, GuiGraphicsExtractor graphics, HudBox box, boolean editMode) {
        int titleHeight = editMode ? scale(12) : 0;
        int height = box.height() + (editMode ? scale(10) : 0);
        drawPanel(graphics, box.x(), box.y(), box.width(), height, box.edge());

        if (editMode) {
            graphics.fill(box.x(), box.y(), box.x() + box.width(), box.y() + titleHeight, 0xAA263340);
            graphics.text(client.font, box.title(), box.x() + scale(7), box.y() + 2, 0xFF7DD3FC);
        }

        int textY = box.y() + scale(5) + (editMode ? scale(10) : 0);
        for (Line line : box.lines()) {
            if (!line.text().isEmpty()) {
                graphics.text(client.font, line.text(), box.x() + scale(7), textY, line.color());
            }
            textY += scale(10);
        }

        if (box.module() == BlockTrackerState.HudModule.NETWORK) {
            drawPingGraph(graphics, box.x() + scale(7), textY + scale(2), box.width() - scale(14), scale(22));
        }
    }

    private static void drawPingGraph(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x7710151C);
        graphics.outline(x, y, width, height, 0x5538424F);

        int[] samples = VeyraHudTelemetry.pingSamples();
        if (samples.length < 2) {
            return;
        }

        int maximum = 100;
        for (int sample : samples) {
            maximum = Math.max(maximum, sample);
        }

        int previousX = x + 1;
        int previousY = y + height - 2 - Math.round((samples[0] / (float) maximum) * (height - 4));
        for (int index = 1; index < samples.length; index++) {
            int sampleX = x + 1 + Math.round(index / (float) (samples.length - 1) * (width - 3));
            int sampleY = y + height - 2 - Math.round((samples[index] / (float) maximum) * (height - 4));
            drawHudLine(graphics, previousX, previousY, sampleX, sampleY, pingColor(samples[index]), 1);
            previousX = sampleX;
            previousY = sampleY;
        }
    }

    private static int scale(int value) {
        return Math.max(1, Math.round(value * (BlockTrackerState.hudScalePercent() / 100.0F)));
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

    private static void addVitalLines(List<Line> lines, Player player, boolean compact) {
        float health = Math.max(0.0F, player.getHealth());
        float maximum = Math.max(1.0F, player.getMaxHealth());
        float healthRatio = health / maximum;
        int hunger = player.getFoodData().getFoodLevel();
        int healthColor = healthRatio <= 0.3F ? BAD : healthRatio <= 0.55F ? WARN : GOOD;
        int hungerColor = hunger <= 6 ? BAD : hunger <= 12 ? WARN : GOOD;

        String healthText = String.format(java.util.Locale.ROOT, "Health %.1f/%.1f", health, maximum);
        lines.add(new Line((healthColor == BAD ? "! " : "") + healthText, healthColor));
        lines.add(new Line((hungerColor == BAD ? "! " : "") + "Hunger " + hunger + "/20", hungerColor));
        if (!compact) {
            lines.add(new Line(String.format(java.util.Locale.ROOT, "Saturation %.1f", player.getFoodData().getSaturationLevel()), MUTED));
        }
    }

    private static void addInputLines(List<Line> lines, Minecraft client) {
        lines.add(new Line(
                keyToken(client.options.keyUp, "W") + " "
                        + keyToken(client.options.keyLeft, "A") + " "
                        + keyToken(client.options.keyDown, "S") + " "
                        + keyToken(client.options.keyRight, "D"),
                TEXT
        ));
        lines.add(new Line(keyToken(client.options.keyJump, "SPACE") + "  LMB " + VeyraHudTelemetry.cps() + " CPS", MUTED));
    }

    private static String keyToken(KeyMapping mapping, String fallback) {
        String name;
        try {
            name = mapping.getTranslatedKeyMessage().getString();
        } catch (RuntimeException ignored) {
            name = fallback;
        }
        if (name == null || name.isBlank() || name.length() > 8) {
            name = fallback;
        }
        return mapping.isDown() ? "[" + name.toUpperCase(java.util.Locale.ROOT) + "]" : " " + name.toUpperCase(java.util.Locale.ROOT) + " ";
    }

    private static int safeFps(Minecraft client) {
        try {
            return client.getFps();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int pingColor(int ping) {
        if (ping >= 180) {
            return BAD;
        }
        if (ping >= 90) {
            return WARN;
        }
        return GOOD;
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
        String warning = max > 0 && left / (float) max <= 0.2F ? "! " : "";
        return warning + label + " " + left + "/" + max;
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

    public record HudBox(BlockTrackerState.HudModule module, int x, int y, int width, int height, String title, List<Line> lines, int edge) {
    }

    private record Line(String text, int color) {
    }
}
