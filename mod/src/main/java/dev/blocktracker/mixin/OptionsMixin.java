package dev.blocktracker.mixin;

import dev.blocktracker.VeyraKeybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Unique
    private boolean veyra$keyMappingsAdded;

    @Inject(method = "load", at = @At("HEAD"))
    private void veyra$addKeyMappingsBeforeLoad(CallbackInfo ci) {
        veyra$addKeyMappings();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void veyra$addKeyMappingsAfterInit(Minecraft minecraft, File gameDirectory, CallbackInfo ci) {
        veyra$addKeyMappings();
    }

    @Unique
    private void veyra$addKeyMappings() {
        if (veyra$keyMappingsAdded) {
            return;
        }

        keyMappings = VeyraKeybinds.appendTo(keyMappings);
        veyra$keyMappingsAdded = true;
    }
}
