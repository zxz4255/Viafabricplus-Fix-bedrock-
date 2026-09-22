package net.raphimc.viabedrockfabric.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.viabedrockfabric.ui.BedrockScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void viabedrock$addBedrockButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(Component.literal("Bedrock"), button -> {
            this.minecraft.gui.setScreen(new BedrockScreen((Screen) (Object) this));
        }).bounds(6, 6, 90, 20).build());
    }
}
