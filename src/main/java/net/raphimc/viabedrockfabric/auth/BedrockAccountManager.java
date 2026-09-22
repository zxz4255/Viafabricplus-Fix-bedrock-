package net.raphimc.viabedrockfabric.auth;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import net.raphimc.viabedrock.protocol.storage.AuthData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Owns the local Microsoft/Xbox/Bedrock account for direct Bedrock connections. */
public final class BedrockAccountManager {
    private static final Path ACCOUNT_FILE = Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve("viabedrock-fabric")
            .resolve("bedrock-account.json");

    private static final AtomicReference<BedrockAuthManager> ACCOUNT = new AtomicReference<>();

    private BedrockAccountManager() {
    }

    public static void initialize() {
        load();
    }

    public static boolean isLoggedIn() {
        final BedrockAuthManager manager = ACCOUNT.get();
        return manager != null && manager.getMinecraftMultiplayerToken().hasValue();
    }

    public static String getDisplayName() {
        final BedrockAuthManager manager = ACCOUNT.get();
        if (manager == null || !manager.getMinecraftMultiplayerToken().hasValue()) {
            return null;
        }
        return manager.getMinecraftMultiplayerToken().getCached().getDisplayName();
    }

    public static void loginAsync(
            final Consumer<MsaDeviceCode> deviceCodeConsumer,
            final Consumer<String> successConsumer,
            final Consumer<Throwable> failureConsumer
    ) {
        final Thread thread = new Thread(() -> {
            try {
                final BedrockAuthManager manager = BedrockAuthManager
                        .create(MinecraftAuth.createHttpClient("ViaBedrock-Fabric-Client/1.0"), ProtocolConstants.BEDROCK_VERSION_NAME)
                        .login(DeviceCodeMsaAuthService::new, deviceCodeConsumer);

                manager.getMinecraftMultiplayerToken().refresh();
                manager.getMinecraftCertificateChain().refresh();
                ACCOUNT.set(manager);
                save(manager);

                final String name = manager.getMinecraftMultiplayerToken().getCached().getDisplayName();
                Minecraft.getInstance().execute(() -> successConsumer.accept(name));
            } catch (Throwable throwable) {
                Minecraft.getInstance().execute(() -> failureConsumer.accept(throwable));
            }
        }, "ViaBedrock-Microsoft-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public static void logout() {
        ACCOUNT.set(null);
        try {
            Files.deleteIfExists(ACCOUNT_FILE);
        } catch (IOException ignored) {
        }
    }

    /** Creates the authenticated Bedrock identity consumed by ViaBedrock LoginPackets. */
    public static AuthData createAuthData() throws Exception {
        final BedrockAuthManager manager = ACCOUNT.get();
        if (manager == null) {
            return null;
        }

        manager.getMinecraftMultiplayerToken().refreshIfExpired();
        manager.getMinecraftCertificateChain().refreshIfExpired();
        save(manager);

        return new AuthData(
                manager.getMinecraftMultiplayerToken().getCached().getToken(),
                manager.getSessionKeyPair(),
                manager.getDeviceId()
        );
    }

    private static void load() {
        if (!Files.isRegularFile(ACCOUNT_FILE)) {
            return;
        }

        try {
            final String json = Files.readString(ACCOUNT_FILE, StandardCharsets.UTF_8);
            final JsonObject object = new GsonBuilder().create().fromJson(json, JsonObject.class);
            final BedrockAuthManager manager = BedrockAuthManager.fromJson(
                    MinecraftAuth.createHttpClient("ViaBedrock-Fabric-Client/1.0"),
                    ProtocolConstants.BEDROCK_VERSION_NAME,
                    object
            );
            manager.getMinecraftMultiplayerToken().refreshIfExpired();
            ACCOUNT.set(manager);
            save(manager);
        } catch (Throwable ignored) {
            ACCOUNT.set(null);
        }
    }

    private static void save(final BedrockAuthManager manager) throws IOException {
        Files.createDirectories(ACCOUNT_FILE.getParent());
        Files.writeString(
                ACCOUNT_FILE,
                BedrockAuthManager.toJson(manager).toString(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }
}
