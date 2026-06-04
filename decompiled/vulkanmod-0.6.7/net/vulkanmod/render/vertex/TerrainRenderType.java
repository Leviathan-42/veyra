package net.vulkanmod.render.vertex;

import java.util.EnumSet;
import java.util.function.Function;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.interfaces.ExtendedRenderType;

public enum TerrainRenderType {
   SOLID(0.0F),
   CUTOUT(0.5F),
   TRANSLUCENT(0.01F),
   TRIPWIRE(0.1F);

   public static final TerrainRenderType[] VALUES = values();
   public static final EnumSet<TerrainRenderType> COMPACT_RENDER_TYPES = EnumSet.of(CUTOUT, TRANSLUCENT);
   public static final EnumSet<TerrainRenderType> SEMI_COMPACT_RENDER_TYPES = EnumSet.of(SOLID, CUTOUT, TRANSLUCENT);
   private static Function<TerrainRenderType, TerrainRenderType> remapper;
   public final float alphaCutout;

   TerrainRenderType(float alphaCutout) {
      this.alphaCutout = alphaCutout;
   }

   public void setCutoutUniform() {
      alphaCutout = this.alphaCutout;
   }

   public static TerrainRenderType get(RenderType renderType) {
      return ((ExtendedRenderType)renderType).getTerrainRenderType();
   }

   public static TerrainRenderType get(ChunkSectionLayer layer) {
      return switch (layer) {
         case SOLID -> SOLID;
         case CUTOUT -> CUTOUT;
         case TRANSLUCENT -> TRANSLUCENT;
         default -> throw new MatchException(null, null);
      };
   }

   public static TerrainRenderType get(String name) {
      return switch (name) {
         case "solid" -> SOLID;
         case "cutout" -> CUTOUT;
         case "translucent" -> TRANSLUCENT;
         case "tripwire" -> TRIPWIRE;
         default -> null;
      };
   }

   public static ChunkSectionLayer getLayer(TerrainRenderType renderType) {
      return switch (renderType) {
         case SOLID -> ChunkSectionLayer.SOLID;
         case CUTOUT -> ChunkSectionLayer.CUTOUT;
         case TRANSLUCENT -> ChunkSectionLayer.TRANSLUCENT;
         case TRIPWIRE -> ChunkSectionLayer.TRANSLUCENT;
      };
   }

   public static void updateMapping() {
      if (Initializer.CONFIG.uniqueOpaqueLayer) {
         remapper = renderType -> {
            return switch (renderType) {
               case SOLID, CUTOUT -> CUTOUT;
               case TRANSLUCENT, TRIPWIRE -> TRANSLUCENT;
            };
         };
      } else {
         remapper = renderType -> {
            return switch (renderType) {
               case SOLID -> SOLID;
               case CUTOUT -> CUTOUT;
               case TRANSLUCENT, TRIPWIRE -> TRANSLUCENT;
            };
         };
      }
   }

   public static TerrainRenderType getRemapped(TerrainRenderType renderType) {
      return remapper.apply(renderType);
   }

   static {
      SEMI_COMPACT_RENDER_TYPES.add(CUTOUT);
      SEMI_COMPACT_RENDER_TYPES.add(TRANSLUCENT);
      COMPACT_RENDER_TYPES.add(TRANSLUCENT);
   }
}
