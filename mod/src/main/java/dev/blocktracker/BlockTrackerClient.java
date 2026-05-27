package dev.blocktracker;

import net.fabricmc.api.ClientModInitializer;

public final class BlockTrackerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockTrackerState.clear();
    }
}
