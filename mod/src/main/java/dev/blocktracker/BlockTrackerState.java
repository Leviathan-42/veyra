package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class BlockTrackerState {
    private static final int RETARGET_INTERVAL_TICKS = 10;
    private static final int MAX_WAYPOINTS = 24;

    private BlockTrackerState() {
    }

    private static Identifier targetId;
    private static Block targetBlock;
    private static List<Block> targetBlocks = List.of();
    private static BlockPos targetPos;
    private static int retargetCooldownTicks;

    private static boolean blockEspEnabled = true;
    private static boolean tracerEnabled = true;
    private static boolean hudLabelEnabled = true;
    private static boolean entityEspEnabled = false;
    private static boolean playerEspEnabled = true;
    private static boolean animalEspEnabled = true;
    private static boolean hostileEspEnabled = false;
    private static boolean statsHudEnabled = true;
    private static boolean waypointHudEnabled = true;
    private static boolean fullbrightEnabled = false;
    private static boolean wasDead;
    private static BlockPos deathMarker;
    private static final List<Waypoint> waypoints = new ArrayList<>();

    public static void setTarget(Identifier id, Block block, BlockPos pos) {
        setTarget(id, List.of(block), pos);
    }

    public static void setTarget(Identifier id, List<Block> blocks, BlockPos pos) {
        targetId = id;
        targetBlock = blocks.isEmpty() ? null : blocks.getFirst();
        targetBlocks = List.copyOf(blocks);
        targetPos = pos;
        retargetCooldownTicks = 0;
    }

    public static Identifier targetId() {
        return targetId;
    }

    public static Block targetBlock() {
        return targetBlock;
    }

    public static List<Block> targetBlocks() {
        return targetBlocks;
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

    public static boolean statsHudEnabled() {
        return statsHudEnabled;
    }

    public static void toggleStatsHud() {
        statsHudEnabled = !statsHudEnabled;
    }

    public static boolean waypointHudEnabled() {
        return waypointHudEnabled;
    }

    public static void toggleWaypointHud() {
        waypointHudEnabled = !waypointHudEnabled;
    }

    public static boolean fullbrightEnabled() {
        return fullbrightEnabled;
    }

    public static void toggleFullbright() {
        fullbrightEnabled = !fullbrightEnabled;
    }

    public static List<Waypoint> waypoints() {
        return List.copyOf(waypoints);
    }

    public static BlockPos deathMarker() {
        return deathMarker;
    }

    public static void addWaypoint(Minecraft client) {
        if (client.player == null) {
            return;
        }

        if (waypoints.size() >= MAX_WAYPOINTS) {
            waypoints.remove(0);
        }

        BlockPos pos = client.player.blockPosition().immutable();
        waypoints.add(new Waypoint("Waypoint " + (waypoints.size() + 1), pos));
    }

    public static void clearWaypoints() {
        waypoints.clear();
    }

    public static void clearDeathMarker() {
        deathMarker = null;
    }

    public static void updatePlayerTracking(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            wasDead = false;
            return;
        }

        boolean dead = !player.isAlive() || player.getHealth() <= 0.0F;
        if (dead && !wasDead) {
            deathMarker = player.blockPosition().immutable();
        }
        wasDead = dead;
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
        targetBlocks = List.of();
        targetPos = null;
        retargetCooldownTicks = 0;
    }

    public record Waypoint(String name, BlockPos pos) {
    }
}
