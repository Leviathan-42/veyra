package dev.blocktracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class BlockScan {
    private BlockScan() {
    }

    public static BlockPos findClosest(Minecraft client, Block target, int chunkRadius) {
        Player player = client.player;
        Level level = client.level;

        if (player == null || level == null) {
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
                    if (!level.getBlockState(cursor).is(target)) {
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
