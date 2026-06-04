package net.vulkanmod.mixin.profiling;

import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.systems.TimerQuery.FrameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TimerQuery.class)
public class TimerQueryM {
   @Overwrite
   public void beginProfile() {
   }

   @Overwrite
   public FrameProfile endProfile() {
      return null;
   }
}
