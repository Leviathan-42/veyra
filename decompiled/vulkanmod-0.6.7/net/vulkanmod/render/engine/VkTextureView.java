package net.vulkanmod.render.engine;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.Nullable;

public class VkTextureView extends GpuTextureView {
   private boolean closed;
   private final Int2ReferenceMap<VkFbo> fboCache = new Int2ReferenceOpenHashMap();

   protected VkTextureView(VkGpuTexture gpuTexture, int baseMipLevel, int mipLevels) {
      super(gpuTexture, baseMipLevel, mipLevels);
      gpuTexture.addViews();
   }

   public VkFbo getFbo(@Nullable GpuTexture depthAttachment) {
      int depthAttachmentId = depthAttachment == null ? 0 : ((VkGpuTexture)depthAttachment).id;
      return (VkFbo)this.fboCache.computeIfAbsent(depthAttachmentId, j -> new VkFbo(this, (VkGpuTexture)depthAttachment));
   }

   public boolean isClosed() {
      return this.closed;
   }

   public void close() {
      if (!this.closed) {
         this.closed = true;
         this.texture().removeViews();
      }

      ObjectIterator var1 = this.fboCache.values().iterator();

      while (var1.hasNext()) {
         VkFbo fbo = (VkFbo)var1.next();
         fbo.close();
      }
   }

   public VkGpuTexture texture() {
      return (VkGpuTexture)super.texture();
   }
}
