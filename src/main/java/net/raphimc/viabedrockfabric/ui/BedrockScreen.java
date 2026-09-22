package net.raphimc.viabedrockfabric.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrockfabric.network.BedrockConnectionManager;
import net.raphimc.viabedrockfabric.auth.BedrockAccountManager;

public final class BedrockScreen extends Screen {
    private static final int FIELD_WIDTH = 220;

    private final Screen parent;
    private EditBox addressBox;
    private EditBox portBox;

    public BedrockScreen(final Screen parent) {
        super(Component.literal("Bedrock"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - FIELD_WIDTH / 2;
        final int top = this.height / 2 - 58;

        this.addressBox = new EditBox(this.font, left, top, FIELD_WIDTH, 20, Component.literal("Server Address"));
        this.addressBox.setValue("play.lbsg.net");
        this.addressBox.setMaxLength(255);
        addRenderableWidget(this.addressBox);

        this.portBox = new EditBox(this.font, left, top + 28, FIELD_WIDTH, 20, Component.literal("Port"));
        this.portBox.setValue("19132");
        this.portBox.setMaxLength(5);
        addRenderableWidget(this.portBox);

        addRenderableWidget(Button.builder(
                Component.literal("Bedrock Protocol: " + BedrockProtocolVersion.bedrockLatest.getName()),
                button -> {
                    // The embedded ViaBedrock core currently targets its latest Bedrock protocol.
                }
        ).bounds(left, top + 56, FIELD_WIDTH, 20).build());

        final String accountText = BedrockAccountManager.isLoggedIn()
                ? "Microsoft: " + BedrockAccountManager.getDisplayName()
                : "Microsoft: Not signed in";
        addRenderableWidget(Button.builder(Component.literal(accountText), button ->
                        this.minecraft.gui.setScreen(new BedrockAccountScreen(this)))
                .bounds(left, top + 82, FIELD_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Connect"), button -> connect())
                .bounds(left, top + 108, 106, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + 114, top + 108, 106, 20)
                .build());

        setInitialFocus(this.addressBox);
    }

    private void connect() {
        final String host = this.addressBox.getValue().trim();
        if (host.isEmpty()) {
            return;
        }

        final int port;
        try {
            port = Integer.parseInt(this.portBox.getValue().trim());
        } catch (NumberFormatException ignored) {
            return;
        }
        if (port < 1 || port > 65535) {
            return;
        }

        BedrockConnectionManager.connect(this.minecraft, this, host, port);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(final net.minecraft.client.gui.GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 96, -1);
        graphics.text(this.font, Component.literal("Server Address"), this.width / 2 - FIELD_WIDTH / 2, this.height / 2 - 77, -1, false);
        graphics.text(this.font, Component.literal("Port"), this.width / 2 - FIELD_WIDTH / 2, this.height / 2 - 49, -1, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
