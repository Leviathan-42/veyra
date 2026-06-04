package net.vulkanmod.render.texture;

import java.util.HashSet;
import java.util.Set;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

public abstract class SpriteUpdateUtil {
   private static final Set<VulkanImage> transitionedLayouts = new HashSet<>();

   public static void addTransitionedLayout(VulkanImage image) {
      transitionedLayouts.add(image);
   }

   public static void transitionLayouts() {
      if (!transitionedLayouts.isEmpty()) {
         VkCommandBuffer commandBuffer = ImageUploadHelper.INSTANCE.getOrStartCommandBuffer().handle;
         transitionedLayouts.forEach(image -> {
            MemoryStack stack = MemoryStack.stackPush();

            try {
               image.readOnlyLayout(stack, commandBuffer);
            } catch (Throwable t$) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable x2) {
                     t$.addSuppressed(x2);
                  }
               }

               throw t$;
            }

            if (stack != null) {
               stack.close();
            }
         });
         transitionedLayouts.clear();
      }
   }
}
