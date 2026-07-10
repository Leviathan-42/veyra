package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockScan {
    public static final int DEFAULT_CHUNK_RADIUS = 12;

    private static final int BLOCK_CHECKS_PER_TASK_PER_TICK = 4_096;
    private static final int SECTION_CHECKS_PER_TASK_PER_TICK = 96;
    private static final int EMPTY_RESULT_RETRY_TICKS = 200;

    private static final Map<Identifier, ScanTask> ACTIVE_SCANS = new LinkedHashMap<>();
    private static final Map<Identifier, Long> RETRY_AFTER_TICK = new HashMap<>();
    private static long scanTick;

    private BlockScan() {
    }

    public static void request(
            Minecraft client,
            Identifier targetId,
            Collection<Block> targets,
            int chunkRadius
    ) {
        if (client.player == null || client.level == null || targetId == null || targets.isEmpty()) {
            return;
        }

        ScanTask task = new ScanTask(client.level, client.player.blockPosition(), targets, chunkRadius);
        ACTIVE_SCANS.put(targetId, task);
        RETRY_AFTER_TICK.remove(targetId);
        BlockTrackerState.updateBlockTargetResults(targetId, task.results);
    }

    public static void requestIfNeeded(
            Minecraft client,
            Identifier targetId,
            Collection<Block> targets,
            int chunkRadius
    ) {
        if (targetId == null || ACTIVE_SCANS.containsKey(targetId)) {
            return;
        }

        long retryAfter = RETRY_AFTER_TICK.getOrDefault(targetId, 0L);
        if (scanTick < retryAfter) {
            return;
        }

        request(client, targetId, targets, chunkRadius);
    }

    public static void tick(Minecraft client) {
        scanTick++;

        if (client.level == null || client.player == null) {
            ACTIVE_SCANS.clear();
            RETRY_AFTER_TICK.clear();
            return;
        }

        Iterator<Map.Entry<Identifier, ScanTask>> iterator = ACTIVE_SCANS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Identifier, ScanTask> entry = iterator.next();
            Identifier targetId = entry.getKey();
            ScanTask task = entry.getValue();

            if (task.level != client.level) {
                iterator.remove();
                continue;
            }

            boolean complete = task.step(
                    targetId,
                    BLOCK_CHECKS_PER_TASK_PER_TICK,
                    SECTION_CHECKS_PER_TASK_PER_TICK
            );
            if (!complete) {
                continue;
            }

            task.results.markComplete();
            BlockTrackerState.updateBlockTargetPos(targetId, task.results.closest());
            RETRY_AFTER_TICK.put(targetId, scanTick + EMPTY_RESULT_RETRY_TICKS);
            iterator.remove();
        }
    }

    public static boolean isScanning(Identifier targetId) {
        return targetId != null && ACTIVE_SCANS.containsKey(targetId);
    }

    public static void cancel(Identifier targetId) {
        if (targetId == null) {
            return;
        }

        ACTIVE_SCANS.remove(targetId);
        RETRY_AFTER_TICK.remove(targetId);
    }

    public static void clear() {
        ACTIVE_SCANS.clear();
        RETRY_AFTER_TICK.clear();
    }

    public static final class ScanResults {
        private final int totalSections;
        private final List<SectionMatches> sections = new ArrayList<>();
        private final List<SectionMatches> sectionsView = Collections.unmodifiableList(sections);
        private final Map<Long, ChunkBounds> chunkBounds = new LinkedHashMap<>();
        private final Collection<ChunkBounds> chunkBoundsView = Collections.unmodifiableCollection(chunkBounds.values());

        private int scannedSections;
        private int matchCount;
        private BlockPos closest;
        private double closestDistance = Double.MAX_VALUE;
        private boolean complete;

        private ScanResults(int totalSections) {
            this.totalSections = Math.max(0, totalSections);
        }

        public List<SectionMatches> sections() {
            return sectionsView;
        }

        public Collection<ChunkBounds> chunkBounds() {
            return chunkBoundsView;
        }

        public int matchCount() {
            return matchCount;
        }

        public BlockPos closest() {
            return closest;
        }

        public boolean complete() {
            return complete;
        }

        public int progressPercent() {
            if (complete || totalSections == 0) {
                return 100;
            }
            return Math.min(99, (int) (((long) scannedSections * 100L) / totalSections));
        }

        private SectionMatches addSection(int chunkX, int sectionY, int chunkZ) {
            SectionMatches section = new SectionMatches(chunkX, sectionY, chunkZ);
            sections.add(section);
            long chunkKey = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
            chunkBounds.compute(chunkKey, (ignored, existing) -> {
                if (existing == null) {
                    return new ChunkBounds(chunkX, chunkZ, sectionY, sectionY);
                }
                existing.include(sectionY);
                return existing;
            });
            return section;
        }

        private void recordMatch(BlockPos position, double distance) {
            matchCount++;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = position;
            }
        }

        private void finishSection() {
            scannedSections = Math.min(totalSections, scannedSections + 1);
        }

        private void finishSections(int count) {
            scannedSections = Math.min(totalSections, scannedSections + Math.max(0, count));
        }

        private void markComplete() {
            scannedSections = totalSections;
            complete = true;
        }
    }

    public static final class SectionMatches {
        private final int chunkX;
        private final int sectionY;
        private final int chunkZ;
        private final BitSet matches = new BitSet(4_096);
        private int count;

        private SectionMatches(int chunkX, int sectionY, int chunkZ) {
            this.chunkX = chunkX;
            this.sectionY = sectionY;
            this.chunkZ = chunkZ;
        }

        public int count() {
            return count;
        }

        public int nextMatch(int fromIndex) {
            return matches.nextSetBit(fromIndex);
        }

        public BlockPos position(int localIndex) {
            int localX = localIndex & 15;
            int localZ = (localIndex >> 4) & 15;
            int localY = (localIndex >> 8) & 15;
            return new BlockPos((chunkX << 4) + localX, (sectionY << 4) + localY, (chunkZ << 4) + localZ);
        }

        public BlockPos origin() {
            return new BlockPos(chunkX << 4, sectionY << 4, chunkZ << 4);
        }

        private void add(int localIndex) {
            if (!matches.get(localIndex)) {
                matches.set(localIndex);
                count++;
            }
        }
    }

    public static final class ChunkBounds {
        private final int chunkX;
        private final int chunkZ;
        private int minSectionY;
        private int maxSectionY;

        private ChunkBounds(int chunkX, int chunkZ, int minSectionY, int maxSectionY) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minSectionY = minSectionY;
            this.maxSectionY = maxSectionY;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public int minSectionY() {
            return minSectionY;
        }

        public int maxSectionY() {
            return maxSectionY;
        }

        private void include(int sectionY) {
            minSectionY = Math.min(minSectionY, sectionY);
            maxSectionY = Math.max(maxSectionY, sectionY);
        }
    }

    private static final class ScanTask {
        private final Level level;
        private final BlockPos origin;
        private final Set<Block> targets;
        private final List<ChunkCoordinate> chunks;
        private final int[] sectionOrder;
        private final BlockPos.MutableBlockPos chunkProbe = new BlockPos.MutableBlockPos();
        private final ScanResults results;

        private int chunkCursor;
        private int sectionCursor;
        private int localBlockCursor;
        private LevelChunk currentChunk;
        private LevelChunkSection currentSection;
        private SectionMatches currentMatches;
        private boolean sectionPrepared;

        private ScanTask(Level level, BlockPos origin, Collection<Block> targets, int chunkRadius) {
            this.level = level;
            this.origin = origin.immutable();
            this.targets = Set.copyOf(targets);
            this.chunks = orderedChunks(origin, Math.max(0, chunkRadius));
            this.sectionOrder = orderedSections(level, origin.getY());
            this.results = new ScanResults(chunks.size() * sectionOrder.length);
        }

        private boolean step(Identifier targetId, int blockBudget, int sectionBudget) {
            int checkedBlocks = 0;
            int checkedSections = 0;

            while (chunkCursor < chunks.size()) {
                if (currentChunk == null && !loadCurrentChunk()) {
                    continue;
                }

                if (sectionCursor >= sectionOrder.length) {
                    advanceChunk();
                    continue;
                }

                if (!sectionPrepared) {
                    if (checkedSections >= sectionBudget) {
                        return false;
                    }

                    checkedSections++;
                    int sectionIndex = sectionOrder[sectionCursor];
                    currentSection = currentChunk.getSection(sectionIndex);
                    sectionPrepared = true;

                    if (currentSection == null
                            || currentSection.hasOnlyAir()
                            || !currentSection.maybeHas(state -> targets.contains(state.getBlock()))) {
                        advanceSection();
                        continue;
                    }
                }

                int sectionIndex = sectionOrder[sectionCursor];
                int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
                int sectionBaseY = sectionY << 4;
                ChunkCoordinate chunk = chunks.get(chunkCursor);

                while (localBlockCursor < 4_096 && checkedBlocks < blockBudget) {
                    int localIndex = localBlockCursor++;
                    int localX = localIndex & 15;
                    int localZ = (localIndex >> 4) & 15;
                    int localY = (localIndex >> 8) & 15;
                    checkedBlocks++;

                    if (!targets.contains(currentSection.getBlockState(localX, localY, localZ).getBlock())) {
                        continue;
                    }

                    int worldX = (chunk.x << 4) + localX;
                    int worldY = sectionBaseY + localY;
                    int worldZ = (chunk.z << 4) + localZ;
                    BlockPos position = new BlockPos(worldX, worldY, worldZ);
                    double dx = worldX - origin.getX();
                    double dy = worldY - origin.getY();
                    double dz = worldZ - origin.getZ();
                    double distance = (dx * dx) + (dy * dy) + (dz * dz);

                    if (currentMatches == null) {
                        currentMatches = results.addSection(chunk.x, sectionY, chunk.z);
                    }
                    currentMatches.add(localIndex);
                    BlockPos previousClosest = results.closest();
                    results.recordMatch(position, distance);
                    if (results.closest() != previousClosest) {
                        BlockTrackerState.updateBlockTargetPos(targetId, results.closest());
                    }
                }

                if (localBlockCursor >= 4_096) {
                    advanceSection();
                }

                if (checkedBlocks >= blockBudget) {
                    return false;
                }
            }

            return true;
        }

        private boolean loadCurrentChunk() {
            ChunkCoordinate chunk = chunks.get(chunkCursor);
            chunkProbe.set((chunk.x << 4) + 8, origin.getY(), (chunk.z << 4) + 8);

            if (!level.hasChunkAt(chunkProbe)) {
                results.finishSections(sectionOrder.length);
                advanceChunk();
                return false;
            }

            currentChunk = level.getChunk(chunk.x, chunk.z);
            return true;
        }

        private void advanceSection() {
            if (sectionPrepared) {
                results.finishSection();
            }
            sectionCursor++;
            localBlockCursor = 0;
            currentSection = null;
            currentMatches = null;
            sectionPrepared = false;
        }

        private void advanceChunk() {
            chunkCursor++;
            sectionCursor = 0;
            localBlockCursor = 0;
            currentChunk = null;
            currentSection = null;
            currentMatches = null;
            sectionPrepared = false;
        }

        private static List<ChunkCoordinate> orderedChunks(BlockPos origin, int radius) {
            int originChunkX = origin.getX() >> 4;
            int originChunkZ = origin.getZ() >> 4;
            int diameter = (radius * 2) + 1;
            List<ChunkCoordinate> result = new ArrayList<>(diameter * diameter);

            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    result.add(new ChunkCoordinate(originChunkX + offsetX, originChunkZ + offsetZ));
                }
            }

            result.sort(Comparator.comparingLong(chunk -> minimumHorizontalDistanceSquared(chunk, origin)));
            return result;
        }

        private static long minimumHorizontalDistanceSquared(ChunkCoordinate chunk, BlockPos origin) {
            int minX = chunk.x << 4;
            int maxX = minX + 15;
            int minZ = chunk.z << 4;
            int maxZ = minZ + 15;
            long dx = origin.getX() < minX
                    ? minX - origin.getX()
                    : origin.getX() > maxX ? origin.getX() - maxX : 0L;
            long dz = origin.getZ() < minZ
                    ? minZ - origin.getZ()
                    : origin.getZ() > maxZ ? origin.getZ() - maxZ : 0L;
            return (dx * dx) + (dz * dz);
        }

        private static int[] orderedSections(Level level, int originY) {
            int originSectionY = originY >> 4;
            List<Integer> indexes = new ArrayList<>(level.getSectionsCount());
            for (int index = 0; index < level.getSectionsCount(); index++) {
                indexes.add(index);
            }

            indexes.sort(Comparator.comparingInt(index ->
                    Math.abs(level.getSectionYFromSectionIndex(index) - originSectionY)
            ));
            return indexes.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private record ChunkCoordinate(int x, int z) {
    }
}
