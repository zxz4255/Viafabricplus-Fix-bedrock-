package net.raphimc.viabedrockfabric.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.raphimc.viabedrock.ViaBedrockConfig;
import net.raphimc.viabedrock.platform.ViaBedrockPlatform;

import java.io.File;
import java.util.logging.Logger;

public final class FabricViaBedrockPlatform implements ViaBedrockPlatform {
    private final Logger logger = Logger.getLogger("ViaBedrock-Fabric");
    private final File dataFolder;

    public FabricViaBedrockPlatform() {
        this.dataFolder = FabricLoader.getInstance().getConfigDir().resolve("viabedrock-fabric").toFile();
        this.dataFolder.mkdirs();
        this.init(new ViaBedrockConfig(new File(this.dataFolder, "viabedrock.yml"), this.logger));
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public File getDataFolder() {
        return this.dataFolder;
    }
}
