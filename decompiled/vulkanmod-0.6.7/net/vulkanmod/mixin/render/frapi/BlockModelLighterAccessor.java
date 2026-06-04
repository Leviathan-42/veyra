package net.vulkanmod.mixin.render.frapi;

import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockModelLighter.Cache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockModelLighter.class)
public interface BlockModelLighterAccessor {
   @Accessor("CACHE")
   static ThreadLocal<Cache> fabric_getCACHE() {
      throw new AssertionError();
   }
}
