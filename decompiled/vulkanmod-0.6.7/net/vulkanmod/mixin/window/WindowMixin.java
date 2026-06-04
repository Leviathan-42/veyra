package net.vulkanmod.mixin.window;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.mojang.blaze3d.systems.GpuBackend;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.Config;
import net.vulkanmod.config.Platform;
import net.vulkanmod.config.option.Options;
import net.vulkanmod.config.video.VideoModeManager;
import net.vulkanmod.config.video.VideoModeSet;
import net.vulkanmod.config.video.WindowMode;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public abstract class WindowMixin {
   @Final
   @Shadow
   private long handle;
   @Shadow
   private boolean vsync;
   @Shadow
   private boolean fullscreen;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   private int windowedX;
   @Shadow
   private int windowedY;
   @Shadow
   private int windowedWidth;
   @Shadow
   private int windowedHeight;
   @Shadow
   private int x;
   @Shadow
   private int y;
   @Shadow
   private int width;
   @Shadow
   private int height;
   @Shadow
   private boolean isResized;
   @Shadow
   private boolean minimized;
   @Shadow
   private int framebufferWidth;
   @Shadow
   private int framebufferHeight;
   @Shadow
   @Final
   private WindowEventHandler eventHandler;
   @Shadow
   private boolean actuallyFullscreen;
   @Shadow
   @Final
   private ScreenManager screenManager;
   @Unique
   private boolean wasOnFullscreen = false;

   @Shadow
   public abstract int getWidth();

   @Shadow
   public abstract int getHeight();

   @Shadow
   protected abstract void updateFullscreen(boolean var1);

   @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
   private static void vulkanHint(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
      GLFW.glfwWindowHint(139265, 0);
      boolean b = Platform.isGnome() | Platform.isWeston() | Platform.isGeneric() && Platform.isWayLand();
      GLFW.glfwWindowHint(131077, b ? 0 : 1);
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void getHandle(
      WindowEventHandler eventHandler, DisplayData displayData, String fullscreenVideoModeString, String title, GpuBackend backend, CallbackInfo ci
   ) {
      VRenderSystem.setWindow(this.handle);
   }

   @Overwrite
   public void updateVsync(boolean vsync) {
      this.vsync = vsync;
      Vulkan.setVsync(vsync);
   }

   @Overwrite
   public void toggleFullScreen() {
      this.fullscreen = !this.fullscreen;
      Options.fullscreenDirty = true;
      if (!this.fullscreen) {
         Config config = Initializer.CONFIG;
         config.windowMode = WindowMode.WINDOWED.mode;
      }
   }

   @Overwrite
   public void updateFullscreenIfChanged() {
      if (Options.fullscreenDirty) {
         Options.fullscreenDirty = false;
         this.updateFullscreen(this.vsync);
      }
   }

   @Overwrite
   private void setMode() {
      Config config = Initializer.CONFIG;
      VideoModeManager.checkConfigVideoMode(config);
      if (this.fullscreen) {
         config.windowMode = WindowMode.EXCLUSIVE_FULLSCREEN.mode;
      }

      if (this.fullscreen) {
         VideoModeManager.selectBestMonitor((Window)this);
         long monitor = VideoModeManager.selectedMonitor;
         VideoModeSet.VideoMode videoMode = config.videoMode;
         VideoModeSet set = VideoModeManager.getVideoModeSet(videoMode);
         boolean supported;
         if (set != null) {
            supported = set.hasRefreshRate(videoMode.refreshRate);
         } else {
            supported = false;
         }

         if (!supported) {
            LOGGER.error("Resolution not supported, using first available as fallback");
            videoMode = VideoModeManager.getFirstAvailable().getVideoMode();
         }

         if (!this.wasOnFullscreen) {
            this.windowedX = this.x;
            this.windowedY = this.y;
            this.windowedWidth = this.width;
            this.windowedHeight = this.height;
         }

         this.x = 0;
         this.y = 0;
         this.width = videoMode.width;
         this.height = videoMode.height;
         this.isResized = true;
         GLFW.glfwSetWindowMonitor(this.handle, monitor, this.x, this.y, this.width, this.height, videoMode.refreshRate);
         this.wasOnFullscreen = true;
      } else if (config.windowMode == WindowMode.WINDOWED_FULLSCREEN.mode) {
         VideoModeManager.selectBestMonitor((Window)this);
         VideoModeSet.VideoMode videoMode = VideoModeManager.getOsVideoMode();
         if (!this.wasOnFullscreen) {
            this.windowedX = this.x;
            this.windowedY = this.y;
            this.windowedWidth = this.width;
            this.windowedHeight = this.height;
         }

         int width = videoMode.width;
         int height = videoMode.height;
         GLFW.glfwSetWindowAttrib(this.handle, 131077, 0);
         GLFW.glfwSetWindowMonitor(this.handle, 0L, 0, 0, width, height, -1);
         this.width = width;
         this.height = height;
         this.isResized = true;
         this.wasOnFullscreen = true;
      } else {
         this.x = this.windowedX;
         this.y = this.windowedY;
         this.width = this.windowedWidth;
         this.height = this.windowedHeight;
         this.isResized = true;
         GLFW.glfwSetWindowMonitor(this.handle, 0L, this.x, this.y, this.width, this.height, -1);
         GLFW.glfwSetWindowAttrib(this.handle, 131077, 1);
         this.wasOnFullscreen = false;
      }
   }

   @Overwrite
   private void onFramebufferResize(long handle, int newWidth, int newHeight) {
      if (handle == this.handle) {
         int oldWidth = this.getWidth();
         int oldHeight = this.getHeight();
         if (newWidth != 0 && newHeight != 0) {
            this.minimized = false;
            if (newWidth != oldWidth || newHeight != oldHeight) {
               this.framebufferWidth = newWidth;
               this.framebufferHeight = newHeight;
               this.isResized = true;
               Renderer.scheduleSwapChainUpdate();

               try {
                  this.eventHandler.resizeGui();
               } catch (Exception var10) {
                  CrashReport report = CrashReport.forThrowable(var10, "Window resize");
                  CrashReportCategory windowSizeDetails = report.addCategory("Window Dimensions");
                  windowSizeDetails.setDetail("Old", oldWidth + "x" + oldHeight);
                  windowSizeDetails.setDetail("New", newWidth + "x" + newHeight);
                  throw new ReportedException(report);
               }
            }
         } else {
            this.minimized = true;
         }
      }
   }

   @Overwrite
   private void onResize(long window, int width, int height) {
      this.width = width;
      this.height = height;
      if (width > 0 && height > 0) {
         Renderer.scheduleSwapChainUpdate();
      }
   }
}
