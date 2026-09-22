package net.raphimc.viabedrockfabric.platform;

import com.viaversion.vialoader.ViaLoader;
import com.viaversion.viaversion.api.Via;
import net.fabricmc.loader.api.FabricLoader;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricViaBedrockBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("ViaBedrock-Fabric");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private FabricViaBedrockBootstrap() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        ViaLoader.init(null, new FabricVLLoader(), null, null, FabricViaBedrockPlatform::new);
        LOGGER.info("ViaBedrock Fabric client initialized for Java {} -> {}",
                ProtocolConstants.JAVA_VERSION.getName(), BedrockProtocolVersion.bedrockLatest.getName());

        // Fail fast instead of silently starting with no Via platform.
        if (Via.getManager() == null) {
            throw new IllegalStateException("ViaVersion failed to initialize");
        }
    }

}
