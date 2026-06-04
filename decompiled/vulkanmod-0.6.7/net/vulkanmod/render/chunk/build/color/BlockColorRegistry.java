package net.vulkanmod.render.chunk.build.color;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;

public class BlockColorRegistry {
   private final Reference2ReferenceOpenHashMap<Block, List<BlockTintSource>> map = new Reference2ReferenceOpenHashMap();

   public void register(List<BlockTintSource> layers, Block... blocks) {
      for (Block block : blocks) {
         this.map.put(block, layers);
      }
   }

   public List<BlockTintSource> getTintSources(Block block) {
      return (List<BlockTintSource>)this.map.getOrDefault(block, List.of());
   }
}
