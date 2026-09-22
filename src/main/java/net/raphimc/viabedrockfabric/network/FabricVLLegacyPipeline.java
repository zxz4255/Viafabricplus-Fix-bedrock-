package net.raphimc.viabedrockfabric.network;

import com.viaversion.vialoader.netty.VLLegacyPipeline;
import com.viaversion.viaversion.api.connection.UserConnection;

public final class FabricVLLegacyPipeline extends VLLegacyPipeline {
    public FabricVLLegacyPipeline(UserConnection connection) {
        super(connection);
    }

    @Override
    protected String decompressName() {
        return "decompress";
    }

    @Override
    protected String compressName() {
        return "compress";
    }

    @Override
    protected String packetDecoderName() {
        return "decoder";
    }

    @Override
    protected String packetEncoderName() {
        return "encoder";
    }

    @Override
    protected String lengthSplitterName() {
        return "splitter";
    }

    @Override
    protected String lengthPrependerName() {
        return "prepender";
    }
}
