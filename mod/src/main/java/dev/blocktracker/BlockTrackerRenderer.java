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

public final class BlockTrackerRenderer {
    private static final int BOX_STROKE = 0xF04DA3FF;
    private static final int BOX_FILL = 0x264DA3FF;
    private static final int LINE_COLOR = 0xF0FFD34D;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int PLAYER_STROKE = 0xF044E07A;
    private static final int PLAYER_FILL = 0x2444E07A;
    private static final int ANIMAL_STROKE = 0xF0FFB84D;
    private static final int ANIMAL_FILL = 0x24FFB84D;
    private static final int HOSTILE_STROKE = 0xF0FF4D6D;
    private static final int HOSTILE_FILL = 0x24FF4D6D;
    private static final double ENTITY_ESP_RANGE_SQ = 128.0D * 128.0D;
    private static final int MAX_ENTITY_BOXES = 96;

    private static final GizmoStyle BOX_STYLE = GizmoStyle.strokeAndFill(BOX_STROKE, 3.0F, BOX_FILL);
    private static final GizmoStyle PLAYER_STYLE = GizmoStyle.strokeAndFill(PLAYER_STROKE, 2.0F, PLAYER_FILL);
    private static final GizmoStyle ANIMAL_STYLE = GizmoStyle.strokeAndFill(ANIMAL_STROKE, 2.0F, ANIMAL_FILL);
    private static final GizmoStyle HOSTILE_STYLE = GizmoStyle.strokeAndFill(HOSTILE_STROKE, 2.0F, HOSTILE_FILL);

    private BlockTrackerRenderer() {
    }

    public static void emit(Minecraft client) {
        emitBlockTarget(client);
        emitEntityEsp(client);
    }

    private static void emitBlockTarget(Minecraft client) {
        Player player = client.player;
        Level level = client.level;
        BlockPos targetPos = BlockTrackerState.targetPos();
        Block targetBlock = BlockTrackerState.targetBlock();
        Identifier targetId = BlockTrackerState.targetId();

        if (player == null || level == null || targetPos == null || targetBlock == null || targetId == null) {
            return;
        }

        if (!level.hasChunkAt(targetPos) || !level.getBlockState(targetPos).is(targetBlock)) {
            if (BlockTrackerState.shouldRetarget()) {
                BlockTrackerState.setTargetPos(BlockScan.findClosest(client, targetBlock, 6));
            }
            return;
        }

        if (BlockTrackerState.blockEspEnabled()) {
            Gizmos.cuboid(targetPos, BOX_STYLE)
                    .setAlwaysOnTop();
            Gizmos.billboardTextOverBlock(targetId.toString(), targetPos, 0, LABEL_COLOR, 0.45F)
                    .setAlwaysOnTop();
        }

        if (!BlockTrackerState.tracerEnabled()) {
            return;
        }

        Vec3 start = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.45D));
        Vec3 end = Vec3.atCenterOf(targetPos);

        Gizmos.line(start, end, LINE_COLOR, 3.0F)
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
