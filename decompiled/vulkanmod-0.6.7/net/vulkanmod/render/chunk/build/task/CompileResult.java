package net.vulkanmod.render.chunk.build.task;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.vulkanmod.interfaces.VisibilitySetExtended;
import net.vulkanmod.render.chunk.RenderSection;
import net.vulkanmod.render.chunk.build.UploadBuffer;
import net.vulkanmod.render.vertex.QuadSorter;
import net.vulkanmod.render.vertex.TerrainRenderType;

public class CompileResult {
   public final RenderSection renderSection;
   public final boolean fullUpdate;
   final List<BlockEntity> globalBlockEntities = new ArrayList<>();
   final List<BlockEntity> blockEntities = new ArrayList<>();
   public final EnumMap<TerrainRenderType, UploadBuffer> renderedLayers = new EnumMap<>(TerrainRenderType.class);
   VisibilitySet visibilitySet;
   QuadSorter.SortState transparencyState;
   CompiledSection compiledSection;

   CompileResult(RenderSection renderSection, boolean fullUpdate) {
      this.renderSection = renderSection;
      this.fullUpdate = fullUpdate;
   }

   public void updateSection() {
      this.renderSection.updateGlobalBlockEntities(this.globalBlockEntities);
      this.renderSection.setCompiledSection(this.compiledSection);
      this.renderSection.setVisibility(((VisibilitySetExtended)this.visibilitySet).getVisibility());
      this.renderSection.setCompletelyEmpty(this.compiledSection.isCompletelyEmpty);
      this.renderSection.setContainsBlockEntities(!this.blockEntities.isEmpty());
   }
}
