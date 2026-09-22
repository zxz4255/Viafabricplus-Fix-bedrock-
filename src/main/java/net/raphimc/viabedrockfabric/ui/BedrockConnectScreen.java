package net.raphimc.viabedrockfabric.ui;

import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class BedrockConnectScreen extends Screen {
    private final Screen parent;
    private volatile Connection connection;
    private volatile ChannelFuture channelFuture;
    private volatile UserConnection viaConnection;
    private volatile Component status = Component.translatable("connect.connecting");

    public BedrockConnectScreen(final Screen parent) {
        super(Component.literal("Connecting to Bedrock"));
        this.parent = parent;
    }

    public void setConnection(final Connection connection) {
        this.connection = connection;
    }

    public void setChannelFuture(final ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
    }

    public void setViaConnection(final UserConnection viaConnection) {
        this.viaConnection = viaConnection;
    }

    public void setStatus(final Component status) {
        this.status = status;
    }

    public void fail(final Component reason) {
        this.status = reason;
        Minecraft.getInstance().execute(() -> {
            if (this.minecraft.gui.screen() == this) {
                this.minecraft.gui.setScreen(new DisconnectedScreen(this.parent, CommonComponents.CONNECT_FAILED, reason));
            }
        });
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            final ChannelFuture currentFuture = this.channelFuture;
            final Connection currentConnection = this.connection;
            if (currentFuture != null) {
                currentFuture.cancel(true);
            }
            if (currentConnection != null) {
                currentConnection.disconnect(Component.translatable("connect.aborted"));
            }
            onClose();
        }).bounds(this.width / 2 - 100, this.height / 2 + 30, 200, 20).build());
    }

    @Override
    public void tick() {
        final Connection current = this.connection;
        if (current == null) {
            return;
        }

        if (current.isConnected()) {
            current.tick();
        } else if (!current.isConnecting()) {
            current.handleDisconnection();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.status, this.width / 2, this.height / 2 - 25, -1);
    }
}
