package net.raphimc.viabedrockfabric.network;

import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.ChannelPipeline;
import net.raphimc.viabedrock.netty.CompressionCodec;
import net.raphimc.viabedrock.netty.raknet.AesEncryptionCodec;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PacketCompressionAlgorithm;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;

import javax.crypto.SecretKey;
import java.util.logging.Level;

public final class FabricNettyPipelineProvider extends NettyPipelineProvider {
    private static final String COMPRESSION = "viabedrock-fabric-compression";
    private static final String ENCRYPTION = "viabedrock-fabric-encryption";

    @Override
    public void enableCompression(UserConnection user, PacketCompressionAlgorithm preferredCompressionAlgorithm, int threshold) {
        ChannelPipeline pipeline = user.getChannel().pipeline();
        if (pipeline.get(COMPRESSION) != null) {
            pipeline.remove(COMPRESSION);
        }
        pipeline.addBefore("via-decoder", COMPRESSION, new CompressionCodec(preferredCompressionAlgorithm, threshold));
    }

    @Override
    public void enableEncryption(UserConnection user, SecretKey key) {
        ChannelPipeline pipeline = user.getChannel().pipeline();
        try {
            if (pipeline.get(ENCRYPTION) != null) {
                pipeline.remove(ENCRYPTION);
            }
            final String anchor = pipeline.get(COMPRESSION) != null ? COMPRESSION : "via-decoder";
            pipeline.addBefore(anchor, ENCRYPTION, new AesEncryptionCodec(key));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to install Bedrock encryption", e);
        }
    }
}
