package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Collection;

public final class BlockScan {
    public static final int DEFAULT_CHUNK_RADIUS = 12;

    private BlockScan() {
    }

    public static BlockPos findClosest(Minecraft client, Block target, int chunkRadius) {
        return findClosest(client, java.util.List.of(target), chunkRadius);
    }

    public static BlockPos findClosest(Minecraft client, Collection<Block> targets, int chunkRadius) {
        Player player = client.player;
        Level level = client.level;

        if (player == null || level == null || targets.isEmpty()) {
            return null;
        }

        BlockPos origin = player.blockPosition();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        int radius = chunkRadius * 16;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                cursor.set(x, origin.getY(), z);
                if (!level.hasChunkAt(cursor)) {
                    continue;
                }

                for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                    cursor.set(x, y, z);
                    if (!targets.stream().anyMatch(target -> level.getBlockState(cursor).is(target))) {
                        continue;
                    }

                    double distance = cursor.distSqr(origin);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closest = cursor.immutable();
                    }
                }
            }
        }

        return closest;
    }
}
