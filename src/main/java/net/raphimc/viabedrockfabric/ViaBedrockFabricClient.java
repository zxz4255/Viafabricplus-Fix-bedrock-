package net.raphimc.viabedrockfabric;

import net.fabricmc.api.ClientModInitializer;
import net.raphimc.viabedrockfabric.network.BedrockConnectionManager;
import net.raphimc.viabedrockfabric.auth.BedrockAccountManager;
import net.raphimc.viabedrockfabric.platform.FabricViaBedrockBootstrap;

public final class ViaBedrockFabricClient implements ClientModInitializer {
    public static final String MOD_ID = "viabedrock-fabric-client";

    @Override
    public void onInitializeClient() {
        FabricViaBedrockBootstrap.initialize();
        BedrockAccountManager.initialize();
        BedrockConnectionManager.initialize();
    }
}
