package net.vulkanmod.render.chunk.build.frapi.mesh;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;

public class MeshImpl extends MeshViewImpl implements Mesh {
   public MeshImpl(int[] data) {
      this.data = data;
      this.limit = data.length;
   }
}
