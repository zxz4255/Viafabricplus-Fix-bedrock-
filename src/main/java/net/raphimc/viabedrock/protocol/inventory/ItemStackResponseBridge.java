/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.inventory;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import net.raphimc.viabedrock.ViaBedrock;

/** Consumes modern Bedrock ItemStackResponse packets and resynchronizes Java on rejection. */
public final class ItemStackResponseBridge {
    private ItemStackResponseBridge() {
    }

    public static void handleResponse(final PacketWrapper wrapper) {
        try {
            final int count = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
            boolean rejected = false;
            for (int i = 0; i < count; i++) {
                final int result = wrapper.read(Types.UNSIGNED_BYTE); // ItemStackResponseStatus ordinal; 0 = OK
                wrapper.read(BedrockTypes.VAR_INT); // request id
                if (result == 0) {
                    final int containers = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
                    for (int c = 0; c < containers; c++) {
                        wrapper.read(BedrockTypes.FULL_CONTAINER_NAME); // container name
                        final int entries = wrapper.read(BedrockTypes.UNSIGNED_VAR_INT);
                        for (int e = 0; e < entries; e++) {
                            wrapper.read(Types.UNSIGNED_BYTE); // slot
                            wrapper.read(Types.UNSIGNED_BYTE); // hotbar slot
                            wrapper.read(Types.UNSIGNED_BYTE); // count
                            wrapper.read(BedrockTypes.VAR_INT); // stack network id
                            wrapper.read(BedrockTypes.STRING); // custom name
                            wrapper.read(BedrockTypes.STRING); // filtered custom name
                            wrapper.read(BedrockTypes.VAR_INT); // durability correction
                        }
                    }
                } else {
                    rejected = true;
                }
            }

            final InventoryTracker inventory = wrapper.user().get(InventoryTracker.class);
            if (!rejected) {
                wrapper.cancel();
                return;
            }

            // The Java client predicts clicks locally. If Bedrock rejects the request,
            // explicitly restore the authoritative storage copy we already track.
            final Container current = inventory.getCurrentContainer();
            if (current != null && current.type() != ContainerType.INVENTORY) {
                PacketFactory.sendJavaContainerSetContent(wrapper.user(), current);
                PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventory.getInventoryContainer());
            } else if (current != null) {
                PacketFactory.sendJavaContainerSetContent(wrapper.user(), inventory.getInventoryContainer());
            }
            wrapper.cancel();
        } catch (Throwable e) {
            ViaBedrock.getPlatform().getLogger().warning("Failed to consume ItemStackResponse: " + e.getMessage());
            wrapper.cancel();
        }
    }
}
