package net.vulkanmod.mixin.render.debug;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
   @Shadow
   private int cachedGuiScale;
   @Shadow
   @Final
   private Map<Object, OversizedItemRenderer> oversizedItemRenderers;

   @Shadow
   protected abstract void invalidateItemAtlas();

   @Overwrite
   private int getGuiScaleInvalidatingItemAtlasIfChanged() {
      int guiScale = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.guiScale;
      if (guiScale != this.cachedGuiScale) {
         this.invalidateItemAtlas();

         for (OversizedItemRenderer renderer : this.oversizedItemRenderers.values()) {
            renderer.invalidateTexture();
         }

         this.cachedGuiScale = guiScale;
      }

      return guiScale;
   }
}
