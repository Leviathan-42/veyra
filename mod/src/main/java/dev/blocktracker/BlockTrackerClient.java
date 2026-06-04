package dev.blocktracker;

import dev.blocktracker.shader.VeyraIrisCompat;
import net.fabricmc.api.ClientModInitializer;

public final class BlockTrackerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockTrackerState.clear();
        VeyraIrisCompat.reloadSelectedPack();
    }
}
