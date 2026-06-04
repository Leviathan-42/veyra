package net.vulkanmod.vulkan.shader.layout;

import java.util.function.Supplier;
import net.vulkanmod.vulkan.util.MappedBuffer;
import net.vulkanmod.vulkan.util.MemoryAccess;

public class Vec1f extends Uniform {
   private Supplier<Float> floatSupplier;

   public Vec1f(Uniform.Info info) {
      super(info);
   }

   @Override
   protected void setupSupplier() {
      if (this.info.floatSupplier != null) {
         this.floatSupplier = this.info.floatSupplier;
      } else if (this.info.bufferSupplier != null) {
         this.setSupplier(this.info.bufferSupplier);
      }
   }

   @Override
   public void setSupplier(Supplier<MappedBuffer> supplier) {
      this.floatSupplier = () -> supplier.get().getFloat(0);
   }

   @Override
   void update(long ptr) {
      if (this.floatSupplier != null) {
         float f = this.floatSupplier.get();
         MemoryAccess.memPutFloat(ptr + this.offset, f);
      }
   }
}
