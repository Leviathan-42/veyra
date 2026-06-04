package net.vulkanmod.render.engine;

import com.mojang.blaze3d.GLFWErrorCapture.Error;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;

public class VkBackend implements GpuBackend {
   public String getName() {
      return "Vulkan";
   }

   public void setWindowHints() {
   }

   public void handleWindowCreationErrors(Error error) throws BackendCreationException {
   }

   public GpuDevice createDevice(long window, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions) {
      return new GpuDevice(new VkGpuDevice(window, defaultShaderSource, debugOptions));
   }
}
