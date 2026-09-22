package net.raphimc.viabedrockfabric.platform;

import com.viaversion.vialoader.impl.viaversion.VLLoader;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.provider.NettyPipelineProvider;
import net.raphimc.viabedrockfabric.network.FabricNettyPipelineProvider;

public final class FabricVLLoader extends VLLoader {
    @Override
    public void load() {
        super.load();

        Via.getManager().getProviders().use(NettyPipelineProvider.class, new FabricNettyPipelineProvider());
        Via.getManager().getProviders().use(VersionProvider.class, new BaseVersionProvider() {
            @Override
            public ProtocolVersion getClosestServerProtocol(UserConnection connection) {
                return BedrockProtocolVersion.bedrockLatest;
            }
        });
    }
}
