package net.vulkanmod.render.chunk.build.frapi.mesh;

import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;

enum ItemRenderType {
   CUTOUT(Sheets.cutoutItemSheet()),
   TRANSLUCENT(Sheets.translucentItemSheet()),
   CUTOUT_BLOCK(Sheets.cutoutBlockItemSheet()),
   TRANSLUCENT_BLOCK(Sheets.translucentBlockItemSheet());

   static final RenderType[] RENDER_TYPES = Arrays.stream(values()).map(t -> t.renderType).toArray(RenderType[]::new);
   static final Map<RenderType, ItemRenderType> RENDER_TYPE_2_ENUM;
   static final ItemRenderType DEFAULT = CUTOUT_BLOCK;
   final RenderType renderType;

   ItemRenderType(RenderType renderType) {
      this.renderType = renderType;
   }

   static {
      RENDER_TYPE_2_ENUM = Map.of(
         CUTOUT.renderType, CUTOUT, TRANSLUCENT.renderType, TRANSLUCENT, CUTOUT_BLOCK.renderType, CUTOUT_BLOCK, TRANSLUCENT_BLOCK.renderType, TRANSLUCENT_BLOCK
      );
   }
}
