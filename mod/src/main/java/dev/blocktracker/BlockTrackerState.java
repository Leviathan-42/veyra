package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class BlockTrackerState {
    private static final int MAX_BLOCK_TARGETS = 3;
    private static final int MAX_WAYPOINTS = 24;

    private BlockTrackerState() {
    }

    private static Identifier targetId;
    private static Block targetBlock;
    private static List<Block> targetBlocks = List.of();
    private static BlockPos targetPos;
    private static final List<BlockTarget> blockTargets = new ArrayList<>();

    private static boolean blockEspEnabled = true;
    private static boolean tracerEnabled = true;
    private static boolean hudLabelEnabled = true;
    private static int blockScanRadius = BlockScan.DEFAULT_CHUNK_RADIUS;
    private static boolean entityEspEnabled = false;
    private static boolean playerEspEnabled = true;
    private static boolean animalEspEnabled = true;
    private static boolean hostileEspEnabled = false;
    private static boolean statsHudEnabled = true;
    private static boolean waypointHudEnabled = true;
    private static int hudScalePercent = 100;
    private static boolean hudCompactMode;
    private static int hudMainX = 8;
    private static int hudMainY = 8;
    private static HudPreset hudPreset = HudPreset.CUSTOM;
    private static final List<HudModuleState> hudModules = new ArrayList<>();
    private static boolean fullbrightEnabled = false;
    private static boolean customCrosshairEnabled = true;
    private static int crosshairStyle;
    private static int crosshairSize = 5;
    private static boolean wasDead;
    private static BlockPos deathMarker;
    private static Identifier deathDimension;
    private static final List<Waypoint> waypoints = new ArrayList<>();

    static {
        for (HudModule module : HudModule.values()) {
            hudModules.add(new HudModuleState(module, module.defaultEnabled, module.defaultDetached, module.defaultX, module.defaultY));
        }
    }

    public static void setTarget(Identifier id, Block block, BlockPos pos) {
        setTarget(id, List.of(block), pos);
    }

    public static void setTarget(Identifier id, List<Block> blocks, BlockPos pos) {
        clear();
        addTarget(id, blocks, pos);
    }

    public static void addTarget(Identifier id, List<Block> blocks, BlockPos pos) {
        if (id == null || blocks == null || blocks.isEmpty()) {
            return;
        }

        BlockScan.cancel(id);
        blockTargets.removeIf(target -> target.id().equals(id));
        if (blockTargets.size() >= MAX_BLOCK_TARGETS) {
            BlockTarget removed = blockTargets.removeFirst();
            BlockScan.cancel(removed.id());
        }

        blockTargets.add(new BlockTarget(id, List.copyOf(blocks), pos, null));
        syncPrimaryTarget();
    }

    public static int maxBlockTargets() {
        return MAX_BLOCK_TARGETS;
    }

    public static List<BlockTarget> blockTargets() {
        return List.copyOf(blockTargets);
    }

    public static void removeBlockTarget(int index) {
        if (index < 0 || index >= blockTargets.size()) {
            return;
        }

        BlockTarget removed = blockTargets.remove(index);
        BlockScan.cancel(removed.id());
        syncPrimaryTarget();
    }

    public static void updateBlockTargetPos(int index, BlockPos pos) {
        if (index < 0 || index >= blockTargets.size()) {
            return;
        }

        BlockTarget target = blockTargets.get(index);
        blockTargets.set(index, new BlockTarget(target.id(), target.blocks(), pos, target.results()));
        syncPrimaryTarget();
    }

    public static void updateBlockTargetPos(Identifier id, BlockPos pos) {
        if (id == null) {
            return;
        }

        for (int index = 0; index < blockTargets.size(); index++) {
            if (blockTargets.get(index).id().equals(id)) {
                updateBlockTargetPos(index, pos);
                return;
            }
        }
    }

    public static void updateBlockTargetResults(Identifier id, BlockScan.ScanResults results) {
        if (id == null) {
            return;
        }

        for (int index = 0; index < blockTargets.size(); index++) {
            BlockTarget target = blockTargets.get(index);
            if (target.id().equals(id)) {
                BlockPos closest = results == null ? null : results.closest();
                blockTargets.set(index, new BlockTarget(target.id(), target.blocks(), closest, results));
                syncPrimaryTarget();
                return;
            }
        }
    }

    public static void clearBlockTargetResults(Identifier id) {
        updateBlockTargetResults(id, null);
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
        updateBlockTargetPos(0, pos);
    }

    public static boolean hasBlockTarget() {
        return !blockTargets.isEmpty();
    }

    public static boolean blockEspEnabled() {
        return blockEspEnabled;
    }

    public static void toggleBlockEsp() {
        blockEspEnabled = !blockEspEnabled;
        settingsChanged();
    }

    public static boolean tracerEnabled() {
        return tracerEnabled;
    }

    public static void toggleTracer() {
        tracerEnabled = !tracerEnabled;
        settingsChanged();
    }

    public static boolean hudLabelEnabled() {
        return hudLabelEnabled;
    }

    public static void toggleHudLabel() {
        hudLabelEnabled = !hudLabelEnabled;
        settingsChanged();
    }

    public static int blockScanRadius() {
        return blockScanRadius;
    }

    public static void increaseBlockScanRadius() {
        setBlockScanRadius(blockScanRadius + 4);
    }

    public static void decreaseBlockScanRadius() {
        setBlockScanRadius(blockScanRadius - 4);
    }

    private static void setBlockScanRadius(int radius) {
        int clamped = Math.max(4, Math.min(24, radius));
        if (blockScanRadius == clamped) {
            return;
        }

        blockScanRadius = clamped;
        BlockScan.clear();
        for (int index = 0; index < blockTargets.size(); index++) {
            BlockTarget target = blockTargets.get(index);
            blockTargets.set(index, new BlockTarget(target.id(), target.blocks(), null, null));
        }
        syncPrimaryTarget();
        settingsChanged();
    }

    public static boolean entityEspEnabled() {
        return entityEspEnabled;
    }

    public static void toggleEntityEsp() {
        entityEspEnabled = !entityEspEnabled;
        settingsChanged();
    }

    public static boolean playerEspEnabled() {
        return playerEspEnabled;
    }

    public static void togglePlayerEsp() {
        playerEspEnabled = !playerEspEnabled;
        settingsChanged();
    }

    public static boolean animalEspEnabled() {
        return animalEspEnabled;
    }

    public static void toggleAnimalEsp() {
        animalEspEnabled = !animalEspEnabled;
        settingsChanged();
    }

    public static boolean hostileEspEnabled() {
        return hostileEspEnabled;
    }

    public static void toggleHostileEsp() {
        hostileEspEnabled = !hostileEspEnabled;
        settingsChanged();
    }

    public static boolean statsHudEnabled() {
        return statsHudEnabled;
    }

    public static void toggleStatsHud() {
        statsHudEnabled = !statsHudEnabled;
        markHudCustom();
        settingsChanged();
    }

    public static int hudScalePercent() {
        return hudScalePercent;
    }

    public static void setHudScalePercent(int scale) {
        int clamped = Math.max(60, Math.min(160, scale));
        if (hudScalePercent != clamped) {
            hudScalePercent = clamped;
            markHudCustom();
            settingsChanged();
        }
    }

    public static void increaseHudScale() {
        setHudScalePercent(hudScalePercent + 10);
    }

    public static void decreaseHudScale() {
        setHudScalePercent(hudScalePercent - 10);
    }

    public static boolean hudCompactMode() {
        return hudCompactMode;
    }

    public static void toggleHudCompactMode() {
        hudCompactMode = !hudCompactMode;
        markHudCustom();
        settingsChanged();
    }

    public static String hudPresetName() {
        return hudPreset.displayName;
    }

    public static void cycleHudPreset() {
        HudPreset next = switch (hudPreset) {
            case CUSTOM, SCREENSHOT -> HudPreset.PVP;
            case PVP -> HudPreset.BUILDING;
            case BUILDING -> HudPreset.SCREENSHOT;
        };
        applyHudPreset(next);
    }

    private static void applyHudPreset(HudPreset preset) {
        hudPreset = preset;
        statsHudEnabled = preset != HudPreset.SCREENSHOT;
        hudCompactMode = preset == HudPreset.PVP;
        hudLabelEnabled = preset != HudPreset.SCREENSHOT;
        waypointHudEnabled = preset == HudPreset.BUILDING;
        customCrosshairEnabled = preset != HudPreset.SCREENSHOT;
        hudMainX = 8;
        hudMainY = 8;

        for (HudModule module : HudModule.values()) {
            boolean enabled = switch (preset) {
                case PVP -> module == HudModule.FPS
                        || module == HudModule.NETWORK
                        || module == HudModule.DURABILITY
                        || module == HudModule.EFFECTS
                        || module == HudModule.VITALS
                        || module == HudModule.INPUT;
                case BUILDING -> module == HudModule.POSITION
                        || module == HudModule.FPS
                        || module == HudModule.RAM
                        || module == HudModule.DURABILITY
                        || module == HudModule.EFFECTS
                        || module == HudModule.WAYPOINTS
                        || module == HudModule.MUSIC
                        || module == HudModule.CLOCK;
                case SCREENSHOT -> false;
                case CUSTOM -> module.defaultEnabled;
            };
            hudModules.set(module.ordinal(), new HudModuleState(
                    module,
                    enabled,
                    module.defaultDetached,
                    module.defaultX,
                    module.defaultY
            ));
        }
        settingsChanged();
    }

    public static int hudMainX() {
        return hudMainX;
    }

    public static int hudMainY() {
        return hudMainY;
    }

    public static void moveHudMain(int x, int y) {
        int nextX = Math.max(0, x);
        int nextY = Math.max(0, y);
        if (hudMainX != nextX || hudMainY != nextY) {
            hudMainX = nextX;
            hudMainY = nextY;
            markHudCustom();
            settingsChanged();
        }
    }

    public static List<HudModuleState> hudModules() {
        return List.copyOf(hudModules);
    }

    public static HudModuleState hudModuleState(HudModule module) {
        return hudModules.get(module.ordinal());
    }

    public static boolean hudModuleEnabled(HudModule module) {
        return hudModuleState(module).enabled();
    }

    public static boolean hudModuleDetached(HudModule module) {
        return hudModuleState(module).detached();
    }

    public static void toggleHudModule(HudModule module) {
        HudModuleState state = hudModuleState(module);
        hudModules.set(module.ordinal(), new HudModuleState(module, !state.enabled(), state.detached(), state.x(), state.y()));
        markHudCustom();
        settingsChanged();
    }

    public static void toggleHudModuleDetached(HudModule module) {
        HudModuleState state = hudModuleState(module);
        hudModules.set(module.ordinal(), new HudModuleState(module, state.enabled(), !state.detached(), state.x(), state.y()));
        markHudCustom();
        settingsChanged();
    }

    public static void moveHudModule(HudModule module, int x, int y) {
        HudModuleState state = hudModuleState(module);
        int nextX = Math.max(0, x);
        int nextY = Math.max(0, y);
        if (state.x() != nextX || state.y() != nextY) {
            hudModules.set(module.ordinal(), new HudModuleState(module, state.enabled(), state.detached(), nextX, nextY));
            markHudCustom();
            settingsChanged();
        }
    }

    public static boolean waypointHudEnabled() {
        return waypointHudEnabled;
    }

    public static void toggleWaypointHud() {
        waypointHudEnabled = !waypointHudEnabled;
        settingsChanged();
    }

    public static boolean fullbrightEnabled() {
        return fullbrightEnabled;
    }

    public static void toggleFullbright() {
        fullbrightEnabled = !fullbrightEnabled;
        settingsChanged();
    }

    public static boolean customCrosshairEnabled() {
        return customCrosshairEnabled;
    }

    public static void toggleCustomCrosshair() {
        customCrosshairEnabled = !customCrosshairEnabled;
        settingsChanged();
    }

    public static int crosshairStyle() {
        return crosshairStyle;
    }

    public static String crosshairStyleName() {
        return switch (crosshairStyle) {
            case 1 -> "Dot";
            case 2 -> "Brackets";
            default -> "Plus";
        };
    }

    public static void cycleCrosshairStyle() {
        crosshairStyle = (crosshairStyle + 1) % 3;
        settingsChanged();
    }

    public static int crosshairSize() {
        return crosshairSize;
    }

    public static void increaseCrosshairSize() {
        int next = Math.min(12, crosshairSize + 1);
        if (crosshairSize != next) {
            crosshairSize = next;
            settingsChanged();
        }
    }

    public static void decreaseCrosshairSize() {
        int next = Math.max(2, crosshairSize - 1);
        if (crosshairSize != next) {
            crosshairSize = next;
            settingsChanged();
        }
    }

    public static List<Waypoint> waypoints() {
        return List.copyOf(waypoints);
    }

    public static List<Waypoint> waypointsFor(Level level) {
        if (level == null) {
            return List.of();
        }

        Identifier dimension = level.dimension().identifier();
        return waypoints.stream().filter(waypoint -> waypoint.dimension().equals(dimension)).toList();
    }

    public static BlockPos deathMarker() {
        return deathMarker;
    }

    public static BlockPos deathMarkerFor(Level level) {
        if (level == null || deathDimension == null || !deathDimension.equals(level.dimension().identifier())) {
            return null;
        }
        return deathMarker;
    }

    public static void addWaypoint(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        addWaypoint(
                "Home " + (waypoints.size() + 1),
                client.player.blockPosition().immutable(),
                client.level.dimension().identifier()
        );
    }

    public static void addLookWaypoint(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        BlockPos pos = client.player.blockPosition().immutable();
        String name = "Home ";
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            pos = blockHit.getBlockPos().immutable();
            name = "Marker ";
        }

        addWaypoint(name + (waypoints.size() + 1), pos, client.level.dimension().identifier());
    }

    private static void addWaypoint(String name, BlockPos pos, Identifier dimension) {
        if (waypoints.size() >= MAX_WAYPOINTS) {
            waypoints.remove(0);
        }

        waypoints.add(new Waypoint(name, pos, dimension));
        settingsChanged();
    }

    public static void removeNearestWaypoint(Minecraft client) {
        if (client.player == null || client.level == null || waypoints.isEmpty()) {
            return;
        }

        BlockPos playerPos = client.player.blockPosition();
        Identifier dimension = client.level.dimension().identifier();
        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < waypoints.size(); index++) {
            if (!waypoints.get(index).dimension().equals(dimension)) {
                continue;
            }
            double distance = waypoints.get(index).pos().distSqr(playerPos);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = index;
            }
        }

        if (nearestIndex >= 0) {
            waypoints.remove(nearestIndex);
            settingsChanged();
        }
    }

    public static void clearWaypoints() {
        if (!waypoints.isEmpty()) {
            waypoints.clear();
            settingsChanged();
        }
    }

    public static void clearDeathMarker() {
        if (deathMarker != null || deathDimension != null) {
            deathMarker = null;
            deathDimension = null;
            settingsChanged();
        }
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
            deathDimension = client.level == null ? null : client.level.dimension().identifier();
            settingsChanged();
        }
        wasDead = dead;
    }

    public static void clear() {
        BlockScan.clear();
        blockTargets.clear();
        syncPrimaryTarget();
    }

    private static void syncPrimaryTarget() {
        if (blockTargets.isEmpty()) {
            targetId = null;
            targetBlock = null;
            targetBlocks = List.of();
            targetPos = null;
            return;
        }

        BlockTarget primary = blockTargets.getFirst();
        targetId = primary.id();
        targetBlocks = primary.blocks();
        targetBlock = targetBlocks.isEmpty() ? null : targetBlocks.getFirst();
        targetPos = primary.pos();
    }

    static Properties persistentProperties() {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("blocks.esp", Boolean.toString(blockEspEnabled));
        properties.setProperty("blocks.tracer", Boolean.toString(tracerEnabled));
        properties.setProperty("blocks.hud", Boolean.toString(hudLabelEnabled));
        properties.setProperty("blocks.radius", Integer.toString(blockScanRadius));
        properties.setProperty("entities.enabled", Boolean.toString(entityEspEnabled));
        properties.setProperty("entities.players", Boolean.toString(playerEspEnabled));
        properties.setProperty("entities.animals", Boolean.toString(animalEspEnabled));
        properties.setProperty("entities.hostile", Boolean.toString(hostileEspEnabled));
        properties.setProperty("hud.stats", Boolean.toString(statsHudEnabled));
        properties.setProperty("hud.waypoints", Boolean.toString(waypointHudEnabled));
        properties.setProperty("hud.scale", Integer.toString(hudScalePercent));
        properties.setProperty("hud.compact", Boolean.toString(hudCompactMode));
        properties.setProperty("hud.main.x", Integer.toString(hudMainX));
        properties.setProperty("hud.main.y", Integer.toString(hudMainY));
        properties.setProperty("hud.preset", hudPreset.name());
        properties.setProperty("visuals.fullbright", Boolean.toString(fullbrightEnabled));
        properties.setProperty("crosshair.enabled", Boolean.toString(customCrosshairEnabled));
        properties.setProperty("crosshair.style", Integer.toString(crosshairStyle));
        properties.setProperty("crosshair.size", Integer.toString(crosshairSize));
        properties.setProperty("death.present", Boolean.toString(deathMarker != null && deathDimension != null));
        if (deathMarker != null && deathDimension != null) {
            properties.setProperty("death.x", Integer.toString(deathMarker.getX()));
            properties.setProperty("death.y", Integer.toString(deathMarker.getY()));
            properties.setProperty("death.z", Integer.toString(deathMarker.getZ()));
            properties.setProperty("death.dimension", deathDimension.toString());
        }

        for (HudModuleState state : hudModules) {
            String prefix = "hud.module." + state.module().name().toLowerCase() + ".";
            properties.setProperty(prefix + "enabled", Boolean.toString(state.enabled()));
            properties.setProperty(prefix + "detached", Boolean.toString(state.detached()));
            properties.setProperty(prefix + "x", Integer.toString(state.x()));
            properties.setProperty(prefix + "y", Integer.toString(state.y()));
        }

        properties.setProperty("waypoints.count", Integer.toString(waypoints.size()));
        for (int index = 0; index < waypoints.size(); index++) {
            Waypoint waypoint = waypoints.get(index);
            String prefix = "waypoints." + index + ".";
            properties.setProperty(prefix + "name", waypoint.name());
            properties.setProperty(prefix + "x", Integer.toString(waypoint.pos().getX()));
            properties.setProperty(prefix + "y", Integer.toString(waypoint.pos().getY()));
            properties.setProperty(prefix + "z", Integer.toString(waypoint.pos().getZ()));
            properties.setProperty(prefix + "dimension", waypoint.dimension().toString());
        }
        return properties;
    }

    static void applyPersistentProperties(Properties properties) {
        blockEspEnabled = booleanProperty(properties, "blocks.esp", blockEspEnabled);
        tracerEnabled = booleanProperty(properties, "blocks.tracer", tracerEnabled);
        hudLabelEnabled = booleanProperty(properties, "blocks.hud", hudLabelEnabled);
        blockScanRadius = clamp(intProperty(properties, "blocks.radius", blockScanRadius), 4, 24);
        entityEspEnabled = booleanProperty(properties, "entities.enabled", entityEspEnabled);
        playerEspEnabled = booleanProperty(properties, "entities.players", playerEspEnabled);
        animalEspEnabled = booleanProperty(properties, "entities.animals", animalEspEnabled);
        hostileEspEnabled = booleanProperty(properties, "entities.hostile", hostileEspEnabled);
        statsHudEnabled = booleanProperty(properties, "hud.stats", statsHudEnabled);
        waypointHudEnabled = booleanProperty(properties, "hud.waypoints", waypointHudEnabled);
        hudScalePercent = clamp(intProperty(properties, "hud.scale", hudScalePercent), 60, 160);
        hudCompactMode = booleanProperty(properties, "hud.compact", hudCompactMode);
        hudMainX = Math.max(0, intProperty(properties, "hud.main.x", hudMainX));
        hudMainY = Math.max(0, intProperty(properties, "hud.main.y", hudMainY));
        hudPreset = enumProperty(HudPreset.class, properties, "hud.preset", HudPreset.CUSTOM);
        fullbrightEnabled = booleanProperty(properties, "visuals.fullbright", fullbrightEnabled);
        customCrosshairEnabled = booleanProperty(properties, "crosshair.enabled", customCrosshairEnabled);
        crosshairStyle = clamp(intProperty(properties, "crosshair.style", crosshairStyle), 0, 2);
        crosshairSize = clamp(intProperty(properties, "crosshair.size", crosshairSize), 2, 12);

        if (booleanProperty(properties, "death.present", false)) {
            Identifier dimension = Identifier.tryParse(properties.getProperty("death.dimension", Level.OVERWORLD.identifier().toString()));
            if (dimension != null) {
                deathDimension = dimension;
                deathMarker = new BlockPos(
                        intProperty(properties, "death.x", 0),
                        intProperty(properties, "death.y", 0),
                        intProperty(properties, "death.z", 0)
                );
            }
        } else {
            deathMarker = null;
            deathDimension = null;
        }

        for (HudModule module : HudModule.values()) {
            HudModuleState fallback = hudModuleState(module);
            String prefix = "hud.module." + module.name().toLowerCase() + ".";
            hudModules.set(module.ordinal(), new HudModuleState(
                    module,
                    booleanProperty(properties, prefix + "enabled", fallback.enabled()),
                    booleanProperty(properties, prefix + "detached", fallback.detached()),
                    Math.max(0, intProperty(properties, prefix + "x", fallback.x())),
                    Math.max(0, intProperty(properties, prefix + "y", fallback.y()))
            ));
        }

        waypoints.clear();
        int count = clamp(intProperty(properties, "waypoints.count", 0), 0, MAX_WAYPOINTS);
        for (int index = 0; index < count; index++) {
            String prefix = "waypoints." + index + ".";
            String name = properties.getProperty(prefix + "name", "Marker " + (index + 1)).trim();
            int x = intProperty(properties, prefix + "x", 0);
            int y = intProperty(properties, prefix + "y", 0);
            int z = intProperty(properties, prefix + "z", 0);
            Identifier dimension = Identifier.tryParse(properties.getProperty(prefix + "dimension", Level.OVERWORLD.identifier().toString()));
            if (dimension == null) {
                dimension = Level.OVERWORLD.identifier();
            }
            waypoints.add(new Waypoint(name.isEmpty() ? "Marker " + (index + 1) : name, new BlockPos(x, y, z), dimension));
        }
    }

    private static boolean booleanProperty(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <T extends Enum<T>> T enumProperty(Class<T> type, Properties properties, String key, T fallback) {
        try {
            return Enum.valueOf(type, properties.getProperty(key, fallback.name()).trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void settingsChanged() {
        VeyraSettings.markDirty();
    }

    private static void markHudCustom() {
        hudPreset = HudPreset.CUSTOM;
    }

    public record BlockTarget(Identifier id, List<Block> blocks, BlockPos pos, BlockScan.ScanResults results) {
    }

    public enum HudModule {
        POSITION("Position", true, false, 8, 8),
        FPS("Performance", true, false, 8, 42),
        RAM("RAM", true, false, 8, 76),
        DURABILITY("Durability", true, false, 8, 110),
        EFFECTS("Effects", true, false, 8, 178),
        WAYPOINTS("Waypoints", true, true, 220, 8),
        NETWORK("Network", true, true, 220, 92),
        VITALS("Vitals", true, true, 220, 152),
        MUSIC("Now Playing", true, true, 220, 212),
        CLOCK("Clock / Session", true, true, 220, 262),
        INPUT("Input / CPS", true, true, 220, 312);

        private final String title;
        private final boolean defaultEnabled;
        private final boolean defaultDetached;
        private final int defaultX;
        private final int defaultY;

        HudModule(String title, boolean defaultEnabled, boolean defaultDetached, int defaultX, int defaultY) {
            this.title = title;
            this.defaultEnabled = defaultEnabled;
            this.defaultDetached = defaultDetached;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
        }

        public String title() {
            return title;
        }
    }

    private enum HudPreset {
        CUSTOM("Custom"),
        PVP("PvP"),
        BUILDING("Building"),
        SCREENSHOT("Screenshot");

        private final String displayName;

        HudPreset(String displayName) {
            this.displayName = displayName;
        }
    }

    public record HudModuleState(HudModule module, boolean enabled, boolean detached, int x, int y) {
    }

    public record Waypoint(String name, BlockPos pos, Identifier dimension) {
    }
}
