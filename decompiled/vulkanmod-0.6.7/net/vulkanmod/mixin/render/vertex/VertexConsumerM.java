package net.vulkanmod.mixin.render.vertex;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.vulkanmod.mixin.matrix.PoseAccessor;
import net.vulkanmod.render.util.MathUtil;
import net.vulkanmod.render.vertex.format.I32_SNorm;
import net.vulkanmod.vulkan.util.ColorUtil;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VertexConsumer.class)
public interface VertexConsumerM {
   @Shadow
   void addVertex(float var1, float var2, float var3, int var4, float var5, float var6, int var7, int var8, float var9, float var10, float var11);

   @Overwrite
   default void putBlockBakedQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
      Vector3fc normal = quad.direction().getUnitVec3f();
      int lightEmission = quad.materialInfo().lightEmission();

      for (int vertex = 0; vertex < 4; vertex++) {
         Vector3fc pos = quad.position(vertex);
         long packedUv = quad.packedUV(vertex);
         int vertexColor = instance.getColor(vertex);
         vertexColor = ColorUtil.RGBA.fromArgb32(vertexColor);
         int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
         float u = UVPair.unpackU(packedUv);
         float v = UVPair.unpackV(packedUv);
         this.addVertex(pos.x() + x, pos.y() + y, pos.z() + z, vertexColor, u, v, instance.overlayCoords(), light, normal.x(), normal.y(), normal.z());
      }
   }

   @Overwrite
   default void putBakedQuad(Pose pose, BakedQuad quad, QuadInstance instance) {
      Vector3fc normalVec = quad.direction().getUnitVec3f();
      Matrix4f matrix = pose.pose();
      boolean trustedNormals = ((PoseAccessor)pose).trustedNormals();
      int packedNormal = MathUtil.packTransformedNorm(pose.normal(), trustedNormals, normalVec.x(), normalVec.y(), normalVec.z());
      int lightEmission = quad.materialInfo().lightEmission();

      for (int vertex = 0; vertex < 4; vertex++) {
         Vector3fc position = quad.position(vertex);
         long packedUv = quad.packedUV(vertex);
         int vertexColor = instance.getColor(vertex);
         vertexColor = ColorUtil.RGBA.fromArgb32(vertexColor);
         int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
         float x = position.x();
         float y = position.y();
         float z = position.z();
         float tx = MathUtil.transformX(matrix, x, y, z);
         float ty = MathUtil.transformY(matrix, x, y, z);
         float tz = MathUtil.transformZ(matrix, x, y, z);
         float u = UVPair.unpackU(packedUv);
         float v = UVPair.unpackV(packedUv);
         this.addVertex(
            tx,
            ty,
            tz,
            vertexColor,
            u,
            v,
            instance.overlayCoords(),
            light,
            I32_SNorm.unpackX(packedNormal),
            I32_SNorm.unpackY(packedNormal),
            I32_SNorm.unpackZ(packedNormal)
         );
      }
   }
}
