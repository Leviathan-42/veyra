package net.vulkanmod.render.engine;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.vulkanmod.vulkan.memory.MemoryManager;
import net.vulkanmod.vulkan.memory.MemoryType;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.Buffer;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VkGpuBuffer extends GpuBuffer {
   protected boolean closed;
   @Nullable
   protected final Supplier<String> label;
   Buffer buffer;
   int offset;

   protected VkGpuBuffer(VkDebugLabel debugLabel, @Nullable Supplier<String> supplier, int usage, long size) {
      super(usage, size);
      this.label = supplier;
      int vkUsage = 0;
      if ((usage & 16) != 0) {
         vkUsage |= 1;
      }

      if ((usage & 8) != 0) {
         vkUsage |= 2;
      }

      if ((usage & 32) != 0) {
         vkUsage |= 128;
      }

      if ((usage & 64) != 0) {
         vkUsage |= 64;
      }

      if ((usage & 128) != 0) {
         vkUsage |= 16;
      }

      if ((usage & 256) != 0) {
         vkUsage |= 4;
      }

      boolean mappable = (usage & 1) != 0 | (usage & 2) != 0 | (usage & 4) != 0;
      MemoryType memoryType = mappable ? MemoryTypes.HOST_MEM : MemoryTypes.GPU_MEM;
      this.buffer = new Buffer(supplier.get(), vkUsage, memoryType);
      this.buffer.createBuffer(this.size());
   }

   protected VkGpuBuffer(Buffer buffer) {
      super(buffer.usage, buffer.getBufferSize());
      this.label = null;
      this.buffer = buffer;
   }

   public boolean isClosed() {
      return this.closed;
   }

   public void close() {
      if (!this.closed) {
         this.closed = true;
         MemoryManager.getInstance().addToFreeable(this.buffer);
      }
   }

   public Buffer getBuffer() {
      return this.buffer;
   }

   public void setOffset(int offset) {
      this.offset = offset;
   }

   public static int bufferUsageToGlEnum(int i) {
      boolean stream = (i & 4) != 0;
      if ((i & 2) != 0) {
         return stream ? 35040 : 35044;
      } else if ((i & 1) != 0) {
         return stream ? 35041 : 35045;
      } else {
         return 35044;
      }
   }

   public static VkGpuBuffer from(Buffer buffer) {
      return new VkGpuBuffer(buffer);
   }

   @Environment(EnvType.CLIENT)
   public static class MappedView implements com.mojang.blaze3d.buffers.GpuBuffer.MappedView {
      private final int target;
      private final ByteBuffer data;

      protected MappedView(int i, ByteBuffer byteBuffer) {
         this.target = i;
         this.data = byteBuffer;
      }

      public ByteBuffer data() {
         return this.data;
      }

      public void close() {
      }
   }
}
