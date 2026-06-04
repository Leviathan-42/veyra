package net.vulkanmod.config.gui;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GuiElement implements GuiEventListener, NarratableEntry {
   protected int width;
   protected int height;
   public int x;
   public int y;
   protected boolean hovered;
   protected long hoverStartTime;
   protected int hoverTime;
   protected long hoverStopTime;

   public void setPosition(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void setPosition(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
   }

   public void resize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void updateState(double mX, double mY) {
      if (this.isMouseOver(mX, mY)) {
         if (!this.hovered) {
            this.hoverStartTime = Util.getMillis();
         }

         this.hovered = true;
         this.hoverTime = (int)(Util.getMillis() - this.hoverStartTime);
      } else {
         if (this.hovered) {
            this.hoverStopTime = Util.getMillis();
         }

         this.hovered = false;
         this.hoverTime = 0;
      }
   }

   public float getHoverMultiplier(float time) {
      if (this.hovered) {
         return Math.min(this.hoverTime / time, 1.0F);
      }

      int delta = (int)(Util.getMillis() - this.hoverStopTime);
      return Math.max(1.0F - delta / time, 0.0F);
   }

   @Nullable
   public ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
      return super.nextFocusPath(focusNavigationEvent);
   }

   public boolean isMouseOver(double mouseX, double mouseY) {
      return mouseX >= this.x && mouseY >= this.y && mouseX <= this.x + this.width && mouseY <= this.y + this.height;
   }

   @Nullable
   public ComponentPath getCurrentFocusPath() {
      return super.getCurrentFocusPath();
   }

   @NotNull
   public ScreenRectangle getRectangle() {
      return super.getRectangle();
   }

   public void setFocused(boolean bl) {
   }

   public boolean isFocused() {
      return false;
   }

   @NotNull
   public NarrationPriority narrationPriority() {
      return NarrationPriority.NONE;
   }

   public void updateNarration(NarrationElementOutput narrationElementOutput) {
   }
}
