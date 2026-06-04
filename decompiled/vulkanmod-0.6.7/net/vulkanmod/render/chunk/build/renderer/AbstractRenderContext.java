package net.vulkanmod.render.chunk.build.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.vulkanmod.render.chunk.build.frapi.mesh.EncodingFormat;
import net.vulkanmod.render.chunk.build.frapi.mesh.MutableQuadViewImpl;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public abstract class AbstractRenderContext {
   private final MutableQuadViewImpl editorQuad = new MutableQuadViewImpl() {
      {
         this.data = new int[EncodingFormat.TOTAL_STRIDE];
         this.clear();
      }

      @Override
      protected void emitDirectly() {
         AbstractRenderContext.this.bufferQuad(this);
      }
   };
   private final Vector4f posVec = new Vector4f();
   private final Vector3f normalVec = new Vector3f();
   protected Pose matrices;
   protected int overlay;

   protected abstract void bufferQuad(MutableQuadViewImpl var1);

   protected void bufferQuad(MutableQuadViewImpl quad, VertexConsumer vertexConsumer) {
      Vector4f posVec = this.posVec;
      Vector3f normalVec = this.normalVec;
      Pose matrices = this.matrices;
      Matrix4f posMatrix = matrices.pose();
      boolean useNormals = quad.hasVertexNormals();
      if (useNormals) {
         quad.populateMissingNormals();
      } else {
         matrices.transformNormal(quad.faceNormal(), normalVec);
      }

      for (int i = 0; i < 4; i++) {
         posVec.set(quad.x(i), quad.y(i), quad.z(i), 1.0F);
         posVec.mul(posMatrix);
         if (useNormals) {
            quad.copyNormal(i, normalVec);
            matrices.transformNormal(normalVec, normalVec);
         }

         vertexConsumer.addVertex(
            posVec.x(),
            posVec.y(),
            posVec.z(),
            quad.color(i),
            quad.u(i),
            quad.v(i),
            this.overlay,
            quad.lightmap(i),
            normalVec.x(),
            normalVec.y(),
            normalVec.z()
         );
      }
   }
}
