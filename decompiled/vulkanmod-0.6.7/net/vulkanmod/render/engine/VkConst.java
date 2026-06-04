package net.vulkanmod.render.engine;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;

public class VkConst {
   public static int of(AddressMode addressMode) {
      return switch (addressMode) {
         case REPEAT -> 0;
         case CLAMP_TO_EDGE -> 2;
         default -> throw new MatchException(null, null);
      };
   }

   public static int of(FilterMode filterMode) {
      return switch (filterMode) {
         case NEAREST -> 0;
         case LINEAR -> 1;
         default -> throw new MatchException(null, null);
      };
   }
}
