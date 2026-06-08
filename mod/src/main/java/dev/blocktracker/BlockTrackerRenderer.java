package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class BlockTrackerRenderer {
    private static final int BOX_STROKE = 0xF0FFC857;
    private static final int BOX_STROKE_2 = 0xF05EEAD4;
    private static final int BOX_STROKE_3 = 0xF0C084FC;
    private static final int LINE_COLOR = 0xF0FFC857;
    private static final int LINE_COLOR_2 = 0xF05EEAD4;
    private static final int LINE_COLOR_3 = 0xF0C084FC;
    private static final int PLAYER_STROKE = 0xF05EEAD4;
    private static final int ANIMAL_STROKE = 0xF0A7F3D0;
    private static final int HOSTILE_STROKE = 0xF0FB7185;
    private static final int WAYPOINT_COLOR = 0xF0FFC857;
    private static final int DEATH_COLOR = 0xF0FB7185;
    private static final int WAYPOINT_BEAM_COLOR = 0xC0FFC857;
    private static final int DEATH_BEAM_COLOR = 0xD0FB7185;
    private static final double BEAM_HALF_WIDTH = 0.18D;
    private static final double ENTITY_ESP_RANGE_SQ = 256.0D * 256.0D;
    private static final int MAX_ENTITY_BOXES = 192;

    private static final GizmoStyle BOX_STYLE = GizmoStyle.stroke(BOX_STROKE, 2.0F);
    private static final GizmoStyle BOX_STYLE_2 = GizmoStyle.stroke(BOX_STROKE_2, 2.0F);
    private static final GizmoStyle BOX_STYLE_3 = GizmoStyle.stroke(BOX_STROKE_3, 2.0F);
    private static final GizmoStyle[] BLOCK_STYLES = {BOX_STYLE, BOX_STYLE_2, BOX_STYLE_3};
    private static final int[] BLOCK_LINE_COLORS = {LINE_COLOR, LINE_COLOR_2, LINE_COLOR_3};
    private static final GizmoStyle PLAYER_STYLE = GizmoStyle.stroke(PLAYER_STROKE, 2.0F);
    private static final GizmoStyle ANIMAL_STYLE = GizmoStyle.stroke(ANIMAL_STROKE, 2.0F);
    private static final GizmoStyle HOSTILE_STYLE = GizmoStyle.stroke(HOSTILE_STROKE, 2.0F);
    private static final GizmoStyle WAYPOINT_STYLE = GizmoStyle.stroke(WAYPOINT_COLOR, 2.0F);
    private static final GizmoStyle DEATH_STYLE = GizmoStyle.stroke(DEATH_COLOR, 2.0F);

    private BlockTrackerRenderer() {
    }

    public static void emit(Minecraft client) {
        emitBlockTarget(client);
        emitWaypoints(client);
        emitEntityEsp(client);
    }

    private static void emitBlockTarget(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        List<BlockTrackerState.BlockTarget> targets = BlockTrackerState.blockTargets();

        if (player == null || level == null || targets.isEmpty()) {
            return;
        }

        for (int index = 0; index < targets.size(); index++) {
            emitBlockTarget(client, player, level, index, targets.get(index));
        }
    }

    private static void emitBlockTarget(Minecraft client, Player player, Level level, int index, BlockTrackerState.BlockTarget target) {
        BlockPos targetPos = target.pos();
        List<Block> targetBlocks = target.blocks();
        Identifier targetId = target.id();

        if (targetPos == null || targetBlocks.isEmpty() || targetId == null) {
            if (BlockTrackerState.shouldRetarget()) {
                BlockTrackerState.updateBlockTargetPos(index, BlockScan.findClosest(client, targetBlocks, BlockScan.DEFAULT_CHUNK_RADIUS));
            }
            return;
        }

        if (!level.hasChunkAt(targetPos) || targetBlocks.stream().noneMatch(block -> level.getBlockState(targetPos).is(block))) {
            if (BlockTrackerState.shouldRetarget()) {
                BlockTrackerState.updateBlockTargetPos(index, BlockScan.findClosest(client, targetBlocks, BlockScan.DEFAULT_CHUNK_RADIUS));
            }
            return;
        }

        int styleIndex = Math.min(index, BLOCK_STYLES.length - 1);
        if (BlockTrackerState.blockEspEnabled()) {
            Gizmos.cuboid(targetPos, BLOCK_STYLES[styleIndex])
                    .setAlwaysOnTop();
        }

        if (!BlockTrackerState.tracerEnabled()) {
            return;
        }

        Vec3 start = player.position().add(0.0D, player.getBbHeight() * 0.82D, 0.0D);
        Vec3 end = Vec3.atCenterOf(targetPos);

        Gizmos.line(start, end, BLOCK_LINE_COLORS[styleIndex], 7.0F)
                .setAlwaysOnTop();
    }

    private static void emitWaypoints(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        if (!BlockTrackerState.waypointHudEnabled() || player == null || level == null) {
            return;
        }

        for (BlockTrackerState.Waypoint waypoint : BlockTrackerState.waypoints()) {
            BlockPos pos = waypoint.pos();
            emitBeacon(level, pos, WAYPOINT_BEAM_COLOR, WAYPOINT_STYLE);
        }

        BlockPos death = BlockTrackerState.deathMarker();
        if (death != null) {
            emitBeacon(level, death, DEATH_BEAM_COLOR, DEATH_STYLE);
        }
    }

    private static void emitBeacon(Level level, BlockPos pos, int color, GizmoStyle baseStyle) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double bottom = Math.min(pos.getY(), level.getMinY());
        double top = Math.max(pos.getY() + 1.0D, level.getMaxY());

        // A single thick line can look too much like a tracer from some angles,
        // so draw a small column: center + four edge lines, beacon-style.
        emitBeamLine(x, z, bottom, top, color, 12.0F);
        emitBeamLine(x - BEAM_HALF_WIDTH, z - BEAM_HALF_WIDTH, bottom, top, color, 5.0F);
        emitBeamLine(x + BEAM_HALF_WIDTH, z - BEAM_HALF_WIDTH, bottom, top, color, 5.0F);
        emitBeamLine(x - BEAM_HALF_WIDTH, z + BEAM_HALF_WIDTH, bottom, top, color, 5.0F);
        emitBeamLine(x + BEAM_HALF_WIDTH, z + BEAM_HALF_WIDTH, bottom, top, color, 5.0F);

        if (level.hasChunkAt(pos)) {
            Gizmos.cuboid(pos, baseStyle)
                    .setAlwaysOnTop();
        }
    }

    private static void emitBeamLine(double x, double z, double bottom, double top, int color, float width) {
        Gizmos.line(new Vec3(x, bottom, z), new Vec3(x, top, z), color, width)
                .setAlwaysOnTop();
    }

    private static void emitEntityEsp(Minecraft client) {
        if (!BlockTrackerState.entityEspEnabled() || client.level == null || client.player == null) {
            return;
        }

        int rendered = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (rendered >= MAX_ENTITY_BOXES) {
                return;
            }

            if (entity == client.player || !entity.isAlive() || entity.distanceToSqr(client.player) > ENTITY_ESP_RANGE_SQ) {
                continue;
            }

            GizmoStyle style = entityStyle(entity);
            if (style == null) {
                continue;
            }

            Gizmos.cuboid(entity.getBoundingBox().inflate(0.03D), style)
                    .setAlwaysOnTop();
            rendered++;
        }
    }

    private static GizmoStyle entityStyle(Entity entity) {
        if (entity instanceof Player) {
            return BlockTrackerState.playerEspEnabled() ? PLAYER_STYLE : null;
        }

        MobCategory category = entity.getType().getCategory();
        if (category == MobCategory.MONSTER) {
            return BlockTrackerState.hostileEspEnabled() ? HOSTILE_STYLE : null;
        }

        if (isAnimalCategory(category)) {
            return BlockTrackerState.animalEspEnabled() ? ANIMAL_STYLE : null;
        }

        return null;
    }

    private static boolean isAnimalCategory(MobCategory category) {
        return category == MobCategory.CREATURE
                || category == MobCategory.AMBIENT
                || category == MobCategory.AXOLOTLS
                || category == MobCategory.UNDERGROUND_WATER_CREATURE
                || category == MobCategory.WATER_CREATURE
                || category == MobCategory.WATER_AMBIENT;
    }
}
