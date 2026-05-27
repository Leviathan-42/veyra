package dev.blocktracker;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class BlockTrackerState {
    private static final int RETARGET_INTERVAL_TICKS = 10;

    private BlockTrackerState() {
    }

    private static Identifier targetId;
    private static Block targetBlock;
    private static BlockPos targetPos;
    private static int retargetCooldownTicks;

    private static boolean blockEspEnabled = true;
    private static boolean tracerEnabled = true;
    private static boolean hudLabelEnabled = true;
    private static boolean entityEspEnabled = false;
    private static boolean playerEspEnabled = true;
    private static boolean animalEspEnabled = true;
    private static boolean hostileEspEnabled = false;

    public static void setTarget(Identifier id, Block block, BlockPos pos) {
        targetId = id;
        targetBlock = block;
        targetPos = pos;
        retargetCooldownTicks = 0;
    }

    public static Identifier targetId() {
        return targetId;
    }

    public static Block targetBlock() {
        return targetBlock;
    }

    public static BlockPos targetPos() {
        return targetPos;
    }

    public static void setTargetPos(BlockPos pos) {
        targetPos = pos;
    }

    public static boolean hasBlockTarget() {
        return targetId != null && targetBlock != null;
    }

    public static boolean blockEspEnabled() {
        return blockEspEnabled;
    }

    public static void toggleBlockEsp() {
        blockEspEnabled = !blockEspEnabled;
    }

    public static boolean tracerEnabled() {
        return tracerEnabled;
    }

    public static void toggleTracer() {
        tracerEnabled = !tracerEnabled;
    }

    public static boolean hudLabelEnabled() {
        return hudLabelEnabled;
    }

    public static void toggleHudLabel() {
        hudLabelEnabled = !hudLabelEnabled;
    }

    public static boolean entityEspEnabled() {
        return entityEspEnabled;
    }

    public static void toggleEntityEsp() {
        entityEspEnabled = !entityEspEnabled;
    }

    public static boolean playerEspEnabled() {
        return playerEspEnabled;
    }

    public static void togglePlayerEsp() {
        playerEspEnabled = !playerEspEnabled;
    }

    public static boolean animalEspEnabled() {
        return animalEspEnabled;
    }

    public static void toggleAnimalEsp() {
        animalEspEnabled = !animalEspEnabled;
    }

    public static boolean hostileEspEnabled() {
        return hostileEspEnabled;
    }

    public static void toggleHostileEsp() {
        hostileEspEnabled = !hostileEspEnabled;
    }

    public static boolean shouldRetarget() {
        if (retargetCooldownTicks > 0) {
            retargetCooldownTicks--;
            return false;
        }

        retargetCooldownTicks = RETARGET_INTERVAL_TICKS;
        return true;
    }

    public static void clear() {
        targetId = null;
        targetBlock = null;
        targetPos = null;
        retargetCooldownTicks = 0;
    }
}
