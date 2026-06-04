package dev.blocktracker.mixin;

import dev.blocktracker.VeyraShaderPackManager;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.OptionBlock;
import net.vulkanmod.config.option.CyclingOption;
import net.vulkanmod.config.option.Option;
import net.vulkanmod.config.option.OptionPage;
import net.vulkanmod.config.option.Options;
import net.vulkanmod.config.option.PerformanceImpact;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = Options.class, remap = false)
public abstract class VulkanModOptionsMixin {
    @Inject(method = "getOptionPages", at = @At("RETURN"), remap = false)
    private static void veyra$addShaderPage(CallbackInfoReturnable<List<OptionPage>> cir) {
        List<OptionPage> pages = cir.getReturnValue();

        boolean alreadyAdded = pages.stream().anyMatch(page -> "Shaders".equals(page.name));
        if (alreadyAdded) {
            return;
        }

        Option<String> shaderPackOption = new CyclingOption<>(
                Component.literal("Shader Pack"),
                VeyraShaderPackManager.availablePacks().toArray(String[]::new),
                VeyraShaderPackManager::selectPack,
                VeyraShaderPackManager::selectedPack
        ).setTranslator(value -> VeyraShaderPackManager.OFF.equals(value)
                ? Component.translatable("options.off")
                : Component.literal(value)
        ).setTooltip(value -> Component.literal(
                "Experimental Vulkan shaderpack selector. Select Solas here; rendering support is still being ported."
        )).setImpact(PerformanceImpact.HIGH);

        pages.add(new OptionPage("Shaders", new OptionBlock[]{
                new OptionBlock("", new Option<?>[]{ shaderPackOption })
        }));
    }
}
