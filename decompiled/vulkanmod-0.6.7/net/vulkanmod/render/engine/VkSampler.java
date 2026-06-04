package net.vulkanmod.render.engine;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.util.OptionalDouble;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.vulkanmod.vulkan.texture.SamplerManager;

@Environment(EnvType.CLIENT)
public class VkSampler extends GpuSampler {
   private final AddressMode addressModeU;
   private final AddressMode addressModeV;
   private final FilterMode minFilter;
   private final FilterMode magFilter;
   private final int maxAnisotropy;
   private final float maxLod;
   private boolean closed;
   private final long id;

   public VkSampler(AddressMode addressModeU, AddressMode addressModeV, FilterMode minFilter, FilterMode magFilter, int maxAnisotropy, OptionalDouble maxLod) {
      this.addressModeU = addressModeU;
      this.addressModeV = addressModeV;
      this.minFilter = minFilter;
      this.magFilter = magFilter;
      this.maxAnisotropy = maxAnisotropy;
      this.maxLod = maxLod.isPresent() ? (byte)maxLod.getAsDouble() : 1000.0F;
      this.id = SamplerManager.getSampler(
         VkConst.of(addressModeU), VkConst.of(addressModeV), VkConst.of(minFilter), VkConst.of(magFilter), 1, this.maxLod, maxAnisotropy > 1, maxAnisotropy, -1
      );
   }

   public long getId() {
      return this.id;
   }

   public AddressMode getAddressModeU() {
      return this.addressModeU;
   }

   public AddressMode getAddressModeV() {
      return this.addressModeV;
   }

   public FilterMode getMinFilter() {
      return this.minFilter;
   }

   public FilterMode getMagFilter() {
      return this.magFilter;
   }

   public int getMaxAnisotropy() {
      return this.maxAnisotropy;
   }

   public OptionalDouble getMaxLod() {
      return null;
   }

   public void close() {
      if (!this.closed) {
         this.closed = true;
      }
   }

   public boolean isClosed() {
      return this.closed;
   }
}
