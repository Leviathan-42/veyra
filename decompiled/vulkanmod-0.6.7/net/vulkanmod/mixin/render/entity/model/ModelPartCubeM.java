package net.vulkanmod.mixin.render.entity.model;

import java.util.Set;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.core.Direction;
import net.vulkanmod.interfaces.ModelPartCubeMixed;
import net.vulkanmod.render.model.CubeModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Cube.class)
public class ModelPartCubeM implements ModelPartCubeMixed {
   @Unique
   CubeModel cube;

   @Inject(
      method = "<init>",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/model/geom/ModelPart$Cube;polygons:[Lnet/minecraft/client/model/geom/ModelPart$Polygon;",
         ordinal = 0,
         shift = Shift.AFTER
      )
   )
   private void getVertices(
      int xTexOffs,
      int yTexOffs,
      float minX,
      float minY,
      float minZ,
      float width,
      float height,
      float depth,
      float growX,
      float growY,
      float growZ,
      boolean mirror,
      float xTexSize,
      float yTexSize,
      Set<Direction> visibleFaces,
      CallbackInfo ci
   ) {
      CubeModel cube = new CubeModel();
      cube.setVertices(xTexOffs, yTexOffs, minX, minY, minZ, width, height, depth, growX, growY, growZ, mirror, xTexSize, yTexSize, visibleFaces);
      this.cube = cube;
   }

   @Override
   public CubeModel getCubeModel() {
      return this.cube;
   }
}
