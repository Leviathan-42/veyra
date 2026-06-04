package net.vulkanmod.vulkan.util;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public class MappedBuffer {
   public final ByteBuffer buffer;
   public final long ptr;

   public static MappedBuffer createFromBuffer(ByteBuffer buffer) {
      return new MappedBuffer(buffer, MemoryUtil.memAddress0(buffer));
   }

   MappedBuffer(ByteBuffer buffer, long ptr) {
      this.buffer = buffer;
      this.ptr = ptr;
   }

   public MappedBuffer(int size) {
      this.buffer = MemoryUtil.memAlloc(size);
      this.ptr = MemoryUtil.memAddress0(this.buffer);
   }

   public void putFloat(int idx, float f) {
      MemoryAccess.memPutFloat(this.ptr + idx, f);
   }

   public void putInt(int idx, int f) {
      MemoryAccess.memPutInt(this.ptr + idx, f);
   }

   public float getFloat(int idx) {
      return MemoryAccess.memGetFloat(this.ptr + idx);
   }

   public int getInt(int idx) {
      return MemoryAccess.memGetInt(this.ptr + idx);
   }
}
