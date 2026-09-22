package net.raphimc.viabedrockfabric.network;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.RakClientChannel;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrockfabric.mixin.ConnectionAccessor;
import net.raphimc.viabedrockfabric.auth.BedrockAccountManager;
import net.raphimc.viabedrockfabric.ui.BedrockConnectScreen;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the direct Bedrock client connection. No ViaProxy/ViaFabricPlus proxy
 * connection layer is involved: the Minecraft client Connection itself is
 * attached to the RakNet channel and the ViaBedrock pipeline translates it.
 */
public final class BedrockConnectionManager {
    private static final NioEventLoopGroup RAKNET_GROUP = new NioEventLoopGroup(1, runnable -> {
        Thread thread = new Thread(runnable, "ViaBedrock-RakNet");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private BedrockConnectionManager() {
    }

    public static void initialize() {
        INITIALIZED.set(true);
    }

    public static void shutdown() {
        RAKNET_GROUP.shutdownGracefully();
    }

    public static void connect(final Minecraft minecraft, final Screen parent, final String host, final int port) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException("ViaBedrock client is not initialized");
        }

        final BedrockConnectScreen screen = new BedrockConnectScreen(parent);
        minecraft.disconnectWithProgressScreen(false);
        minecraft.prepareForMultiplayer();
        minecraft.gui.setScreen(screen);

        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        screen.setConnection(connection);

        final ServerData serverData = new ServerData("Bedrock: " + host, host + ":" + port, ServerData.Type.OTHER);

        final Thread thread = new Thread(() -> {
            try {
                screen.setStatus(Component.literal("Resolving " + host + ":" + port + "..."));
                final InetSocketAddress address = new InetSocketAddress(host, port);

                final ChannelFuture future = new Bootstrap()
                        .group(RAKNET_GROUP)
                        .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                        .handler(new ChannelInitializer<RakClientChannel>() {
                            @Override
                            protected void initChannel(final RakClientChannel channel) {
                                Connection.configureSerialization(channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
                                connection.configurePacketHandler(channel.pipeline());
                            }
                        })
                        .connect(address);

                screen.setChannelFuture(future);
                future.syncUninterruptibly();

                connection.runOnceConnected(connected -> {
                    try {
                        screen.setStatus(Component.literal("Installing ViaBedrock protocol pipeline..."));

                        final UserConnection user = new UserConnectionImpl(channelOf(connected), true);
                        final net.raphimc.viabedrock.protocol.storage.AuthData authData = BedrockAccountManager.createAuthData();
                        if (authData == null) {
                            throw new IllegalStateException("No Microsoft Bedrock account is logged in. Open the Bedrock screen and sign in first.");
                        }
                        user.put(authData);
                        user.getProtocolInfo().setProtocolVersion(ProtocolConstants.JAVA_VERSION);
                        user.getProtocolInfo().setServerProtocolVersion(BedrockProtocolVersion.bedrockLatest);
                        new ProtocolPipelineImpl(user);

                        // Adds ViaEncoder/ViaDecoder and, for a Bedrock client connection,
                        // the RakNet/BatchLength/Packet codecs required by ViaBedrock.
                        channelOf(connected).pipeline().addLast("viabedrock-fabric-pipeline", new FabricVLLegacyPipeline(user));
                        screen.setViaConnection(user);

                        final ClientHandshakePacketListenerImpl listener = new ClientHandshakePacketListenerImpl(
                                connected,
                                minecraft,
                                serverData,
                                parent,
                                false,
                                null,
                                screen::setStatus,
                                new LevelLoadTracker(),
                                null
                        );

                        // Use Minecraft 26.2's normal login bootstrap. ViaBedrock's LoginPackets
                        // intercepts this Java handshake and starts the Bedrock login sequence.
                        connected.initiateServerboundPlayConnection(
                                host,
                                port,
                                LoginProtocols.SERVERBOUND,
                                LoginProtocols.CLIENTBOUND,
                                listener,
                                false
                        );

                        screen.setStatus(Component.literal("Logging in to Bedrock..."));
                    } catch (Throwable throwable) {
                        screen.fail(Component.literal(formatError("ViaBedrock initialization failed", throwable)));
                    }
                });
            } catch (Throwable throwable) {
                screen.fail(Component.literal(formatError("Bedrock connection failed", throwable)));
            }
        }, "ViaBedrock Connector");

        thread.setDaemon(true);
        thread.start();
    }

    private static String formatError(final String prefix, final Throwable throwable) {
        final Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        final String message = cause.getMessage();
        return message == null || message.isBlank() ? prefix : prefix + ": " + message;
    }

    private static io.netty.channel.Channel channelOf(final Connection connection) {
        return ((ConnectionAccessor) (Object) connection).viabedrock$getChannel();
    }
}
