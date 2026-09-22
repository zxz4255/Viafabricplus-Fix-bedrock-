package net.raphimc.viabedrockfabric.ui;

import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.viabedrockfabric.auth.BedrockAccountManager;

public final class BedrockAccountScreen extends Screen {
    private final Screen parent;
    private volatile Component status = Component.literal("Not signed in");
    private volatile String userCode;
    private volatile String verificationUri;
    private Button loginButton;
    private Button openButton;
    private Button logoutButton;

    public BedrockAccountScreen(final Screen parent) {
        super(Component.literal("Bedrock Account"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 110;
        if (BedrockAccountManager.isLoggedIn()) {
            this.status = Component.literal("Signed in as " + BedrockAccountManager.getDisplayName());
        }

        this.loginButton = addRenderableWidget(Button.builder(Component.literal("Microsoft Login"), button -> login())
                .bounds(left, this.height / 2 + 10, 220, 20).build());
        this.openButton = addRenderableWidget(Button.builder(Component.literal("Open Microsoft Login"), button -> openVerificationUri())
                .bounds(left, this.height / 2 + 36, 220, 20).build());
        this.logoutButton = addRenderableWidget(Button.builder(Component.literal("Logout"), button -> logout())
                .bounds(left, this.height / 2 + 62, 105, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + 115, this.height / 2 + 62, 105, 20).build());

        updateButtons();
    }

    private void login() {
        if (BedrockAccountManager.isLoggedIn()) {
            return;
        }
        this.status = Component.literal("Requesting Microsoft device code...");
        this.userCode = null;
        this.verificationUri = null;
        updateButtons();

        BedrockAccountManager.loginAsync(
                this::onDeviceCode,
                name -> {
                    this.status = Component.literal("Signed in as " + name);
                    updateButtons();
                },
                error -> {
                    String message = error.getMessage();
                    this.status = Component.literal("Login failed: " + (message == null ? error.getClass().getSimpleName() : message));
                    updateButtons();
                }
        );
    }

    private void onDeviceCode(final MsaDeviceCode deviceCode) {
        this.userCode = deviceCode.getUserCode();
        this.verificationUri = deviceCode.getDirectVerificationUri().toString();
        this.status = Component.literal("Enter the code in your browser: " + this.userCode);
        this.minecraft.execute(this::updateButtons);
    }

    private void openVerificationUri() {
        final String uri = this.verificationUri;
        if (uri != null && !uri.isBlank()) {
            Util.getPlatform().openUri(uri);
        }
    }

    private void logout() {
        BedrockAccountManager.logout();
        this.status = Component.literal("Not signed in");
        this.userCode = null;
        this.verificationUri = null;
        updateButtons();
    }

    private void updateButtons() {
        if (this.loginButton == null) {
            return;
        }
        final boolean loggedIn = BedrockAccountManager.isLoggedIn();
        this.loginButton.active = !loggedIn && this.userCode == null;
        this.openButton.active = this.verificationUri != null && !loggedIn;
        this.logoutButton.active = loggedIn;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 96, -1);
        graphics.centeredText(this.font, this.status, this.width / 2, this.height / 2 - 52, -1);
        if (this.userCode != null) {
            graphics.centeredText(this.font, Component.literal("Code: " + this.userCode), this.width / 2, this.height / 2 - 28, -1);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
