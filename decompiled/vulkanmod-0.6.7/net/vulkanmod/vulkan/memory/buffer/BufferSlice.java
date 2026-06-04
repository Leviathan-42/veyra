package net.vulkanmod.vulkan.memory.buffer;

public class BufferSlice {
   Buffer buffer;
   long offset;
   int size;

   public void set(Buffer buffer, long offset, int size) {
      this.buffer = buffer;
      this.offset = offset;
      this.size = size;
   }

   public Buffer getBuffer() {
      return this.buffer;
   }

   public long getOffset() {
      return this.offset;
   }

   public int getSize() {
      return this.size;
   }
}
