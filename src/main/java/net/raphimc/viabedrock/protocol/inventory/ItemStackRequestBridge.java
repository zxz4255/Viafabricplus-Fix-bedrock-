/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * GPL-3.0-or-later
 */
package net.raphimc.viabedrock.protocol.inventory;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ServerboundBedrockPackets;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ContainerType;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ContainerEnumName;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ItemStackRequestActionType;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.ContainerInput;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import net.raphimc.viabedrock.protocol.model.FullContainerName;
import net.raphimc.viabedrock.protocol.storage.InventoryTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts Java 26.1/26.2 container clicks into modern Bedrock ItemStackRequest packets.
 * This deliberately follows the Cloudburst/Geyser ItemStackRequest model instead of
 * the legacy InventoryTransaction path.
 */
public final class ItemStackRequestBridge {

    private ItemStackRequestBridge() {
    }

    private static final FullContainerName LEVEL_ENTITY = new FullContainerName(ContainerEnumName.LevelEntityContainer, null);
    private static final FullContainerName INVENTORY = new FullContainerName(ContainerEnumName.InventoryContainer, null);
    private static final FullContainerName HOTBAR = new FullContainerName(ContainerEnumName.HotbarContainer, null);
    private static final FullContainerName ARMOR = new FullContainerName(ContainerEnumName.ArmorContainer, null);
    private static final FullContainerName OFFHAND = new FullContainerName(ContainerEnumName.OffhandContainer, null);
    private static final FullContainerName CURSOR = new FullContainerName(ContainerEnumName.CursorContainer, null);

    private record SlotRef(FullContainerName container, int slot, BedrockItem item) {
        int netId() {
            return item == null || item.isEmpty() || item.netId() == null ? 0 : item.netId();
        }
    }

    private record Transfer(int type, int count, SlotRef source, SlotRef destination) {
    }

    private record Swap(SlotRef source, SlotRef destination) {
    }

    private record Drop(int count, SlotRef source) {
    }

    /**
     * @return true if this click was translated and emitted. False leaves the caller's
     * normal resync fallback in place.
     */
    public static boolean handleClick(final Container window, final int revision, final short slot, final byte button,
                                      final ContainerInput action) {
        final UserConnection user = getUser(window);
        final InventoryTracker tracker = user.get(InventoryTracker.class);

        if (slot < 0 && slot != -999) {
            return false;
        }
        if (tracker.getPendingCloseContainer() != null) {
            return false;
        }

        final int windowSlot = slot;
        final SlotRef cursor = new SlotRef(CURSOR, 0, tracker.getHudContainer().getItem(0));
        final List<Object> actions;

        try {
            actions = switch (action) {
                case PICKUP -> buildPickup(window, windowSlot, button, cursor, tracker);
                case QUICK_MOVE -> buildQuickMove(window, windowSlot, cursor, tracker);
                case SWAP -> new ArrayList<>(buildHotbarSwap(window, windowSlot, button, tracker));
                case THROW -> new ArrayList<>(buildDrop(window, windowSlot, button, tracker));
                case QUICK_CRAFT, CLONE, PICKUP_ALL -> Collections.emptyList();
            };
        } catch (Throwable ignored) {
            return false;
        }

        // Outside-window pickup uses DROP rather than a transfer.
        if (actions.isEmpty() && action == ContainerInput.PICKUP && slot == -999) {
            final int count = button == 1 ? 1 : cursor.item.amount();
            if (count > 0) {
                sendRequest(user, List.of(new Drop(count, cursor)));
                return true;
            }
        }
        if (actions.isEmpty()) {
            return false;
        }

        sendRequest(user, actions);
        return true;
    }

    private static UserConnection getUser(final Container container) {
        return container.user();
    }

    private static List<Object> buildPickup(final Container window, final int slot, final byte button,
                                               final SlotRef cursor, final InventoryTracker tracker) {
        if (slot == -999) {
            return Collections.emptyList();
        }
        final SlotRef clicked = resolveWindowSlot(window, slot, tracker);
        if (clicked == null || clicked.item.isEmpty()) {
            if (cursor.item.isEmpty()) return Collections.emptyList();
            if (slot < 0) return Collections.emptyList();
            return List.of(new Transfer(ItemStackRequestActionType.Place.getValue(), button == 1 ? 1 : cursor.item.amount(), cursor, clicked));
        }

        if (cursor.item.isEmpty()) {
            final int amount = button == 1 ? (clicked.item.amount() + 1) / 2 : clicked.item.amount();
            return List.of(new Transfer(ItemStackRequestActionType.Take.getValue(), amount, clicked, cursor));
        }

        if (itemsCanStack(cursor.item, clicked.item)) {
            final int free = Math.max(0, 64 - clicked.item.amount());
            final int amount = button == 1 ? Math.min(1, Math.min(cursor.item.amount(), free))
                    : Math.min(cursor.item.amount(), free);
            if (amount > 0) {
                return List.of(new Transfer(ItemStackRequestActionType.Place.getValue(), amount, cursor, clicked));
            }
            // Same item, but the destination stack is already full. Java does nothing.
            return Collections.emptyList();
        }

        return List.of(new Swap(cursor, clicked));
    }

    private static List<Object> buildQuickMove(final Container window, final int slot, final SlotRef cursor,
                                                   final InventoryTracker tracker) {
        if (slot < 0 || !cursor.item.isEmpty()) {
            return Collections.emptyList();
        }
        final SlotRef source = resolveWindowSlot(window, slot, tracker);
        if (source == null || source.item.isEmpty()) {
            return Collections.emptyList();
        }

        final List<SlotRef> targets = new ArrayList<>();
        if (window.type() == ContainerType.INVENTORY) {
            addPlayerQuickMoveTargets(tracker, source, targets);
        } else {
            // Chest/container -> player inventory; player inventory -> chest/container.
            if (slot < window.size()) {
                addPlayerTargets(tracker, source, targets);
            } else {
                for (int i = 0; i < window.size(); i++) {
                    targets.add(new SlotRef(windowName(window), i, window.getItem(i)));
                }
            }
        }

        final List<Object> result = new ArrayList<>();
        int remaining = source.item.amount();
        final SlotRef cursorAfterTake = new SlotRef(CURSOR, 0, source.item);

        // A real Bedrock shift-click first takes the stack to the cursor and then releases
        // it into the target slots. This preserves the Bedrock action semantics rather than
        // trying to invent a legacy direct-move transaction.
        result.add(new Transfer(ItemStackRequestActionType.Take.getValue(), source.item.amount(), source, cursorAfterTake));

        // Partial stacks first, matching Geyser's normal shift-click ordering.
        for (SlotRef target : targets) {
            if (remaining <= 0) break;
            if (!target.item.isEmpty() && itemsCanStack(source.item, target.item) && target.item.amount() < 64) {
                final int amount = Math.min(remaining, 64 - target.item.amount());
                if (amount > 0) {
                    result.add(new Transfer(ItemStackRequestActionType.Place.getValue(), amount, cursorAfterTake, target));
                    remaining -= amount;
                }
            }
        }
        // Empty slots next.
        for (SlotRef target : targets) {
            if (remaining <= 0) break;
            if (target.item.isEmpty()) {
                final int amount = Math.min(remaining, 64);
                result.add(new Transfer(ItemStackRequestActionType.Place.getValue(), amount, cursorAfterTake, target));
                remaining -= amount;
            }
        }
        // Nothing could be placed: don't emit a request that only takes an item into the cursor.
        if (result.size() == 1) {
            return Collections.emptyList();
        }
        return result;
    }

    private static void addPlayerTargets(final InventoryTracker tracker, final SlotRef source, final List<SlotRef> targets) {
        final BedrockItem[] items = tracker.getInventoryContainer().getItems();
        // Main inventory first, then hotbar, as Geyser does for container -> player movement.
        for (int i = 9; i < items.length; i++) {
            targets.add(new SlotRef(INVENTORY, i, items[i]));
        }
        for (int i = 0; i < 9; i++) {
            targets.add(new SlotRef(HOTBAR, i, items[i]));
        }
    }

    private static void addPlayerQuickMoveTargets(final InventoryTracker tracker, final SlotRef source,
                                                  final List<SlotRef> targets) {
        final BedrockItem[] items = tracker.getInventoryContainer().getItems();
        if (source.container.name() == ContainerEnumName.HotbarContainer) {
            for (int i = 9; i < items.length; i++) {
                targets.add(new SlotRef(INVENTORY, i, items[i]));
            }
        } else if (source.container.name() == ContainerEnumName.InventoryContainer) {
            for (int i = 0; i < 9; i++) {
                targets.add(new SlotRef(HOTBAR, i, items[i]));
            }
        }
    }

    private static List<Swap> buildHotbarSwap(final Container window, final int slot, final byte button,
                                                final InventoryTracker tracker) {
        if (slot < 0 || button < 0 || button > 8) {
            return Collections.emptyList();
        }
        final SlotRef source = resolveWindowSlot(window, slot, tracker);
        if (source == null) return Collections.emptyList();
        final BedrockItem hotbar = tracker.getInventoryContainer().getItem(button);
        final SlotRef destination = new SlotRef(HOTBAR, button, hotbar);
        if (source.container.name() == ContainerEnumName.HotbarContainer && source.slot == button) {
            return Collections.emptyList();
        }
        return List.of(new Swap(source, destination));
    }

    private static List<Drop> buildDrop(final Container window, final int slot, final byte button,
                                        final InventoryTracker tracker) {
        if (slot < 0) return Collections.emptyList();
        final SlotRef source = resolveWindowSlot(window, slot, tracker);
        if (source == null || source.item.isEmpty()) return Collections.emptyList();
        return List.of(new Drop(button == 1 ? source.item.amount() : 1, source));
    }

    private static SlotRef resolveWindowSlot(final Container window, final int windowSlot, final InventoryTracker tracker) {
        if (window.type() == ContainerType.INVENTORY) {
            if (windowSlot >= 9 && windowSlot <= 35) {
                final BedrockItem item = tracker.getInventoryContainer().getItem(windowSlot);
                return new SlotRef(INVENTORY, windowSlot, item);
            }
            if (windowSlot >= 36 && windowSlot <= 44) {
                final int hotbar = windowSlot - 36;
                return new SlotRef(HOTBAR, hotbar, tracker.getInventoryContainer().getItem(hotbar));
            }
            if (windowSlot >= 5 && windowSlot <= 8) {
                final int armor = windowSlot - 5;
                return new SlotRef(ARMOR, armor, tracker.getArmorContainer().getItem(armor));
            }
            if (windowSlot == 45) {
                return new SlotRef(OFFHAND, 1, tracker.getOffhandContainer().getItem(0));
            }
            return null;
        }

        if (windowSlot >= 0 && windowSlot < window.size()) {
            return new SlotRef(windowName(window), windowSlot, window.getItem(windowSlot));
        }

        final int playerSlot = windowSlot - window.size();
        if (playerSlot < 0 || playerSlot >= 36) return null;
        if (playerSlot < 27) {
            final int inventorySlot = playerSlot + 9;
            return new SlotRef(INVENTORY, inventorySlot, tracker.getInventoryContainer().getItem(inventorySlot));
        }
        final int hotbarSlot = playerSlot - 27;
        return new SlotRef(HOTBAR, hotbarSlot, tracker.getInventoryContainer().getItem(hotbarSlot));
    }

    private static FullContainerName windowName(final Container window) {
        return switch (window.type()) {
            case CONTAINER -> LEVEL_ENTITY;
            default -> switch (window.type()) {
                case INVENTORY -> INVENTORY;
                default -> LEVEL_ENTITY;
            };
        };
    }

    private static boolean itemsCanStack(final BedrockItem a, final BedrockItem b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && !a.isDifferent(b);
    }

    private static void sendRequest(final UserConnection user, final List<?> actions) {
        final InventoryTracker tracker = user.get(InventoryTracker.class);
        final int requestId = tracker.nextItemStackRequestId();
        final PacketWrapper wrapper = PacketWrapper.create(ServerboundBedrockPackets.ITEM_STACK_REQUEST, user);

        wrapper.write(BedrockTypes.VAR_INT, requestId);
        wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, actions.size());
        for (Object action : actions) {
            if (action instanceof Transfer transfer) {
                wrapper.write(Types.BYTE, (byte) transfer.type);
                wrapper.write(Types.BYTE, (byte) transfer.count);
                writeSlot(wrapper, transfer.source);
                writeSlot(wrapper, transfer.destination);
            } else if (action instanceof Swap swap) {
                wrapper.write(Types.BYTE, (byte) ItemStackRequestActionType.Swap.getValue());
                writeSlot(wrapper, swap.source);
                writeSlot(wrapper, swap.destination);
            } else if (action instanceof Drop drop) {
                wrapper.write(Types.BYTE, (byte) ItemStackRequestActionType.Drop.getValue());
                wrapper.write(Types.BYTE, (byte) drop.count);
                writeSlot(wrapper, drop.source);
                wrapper.write(Types.BOOLEAN, false);
            } else {
                throw new IllegalArgumentException("Unknown item stack request action: " + action);
            }
        }

        // 1.26.30 / protocol 1001 still uses the v712+ request footer:
        // filtered strings array + TextProcessingEventOrigin(-1 == absent).
        wrapper.write(BedrockTypes.UNSIGNED_VAR_INT, 0);
        wrapper.write(BedrockTypes.INT_LE, -1);
        wrapper.sendToServer(BedrockProtocol.class);
    }

    private static void writeSlot(final PacketWrapper wrapper, final SlotRef slot) {
        wrapper.write(BedrockTypes.FULL_CONTAINER_NAME, slot.container);
        wrapper.write(Types.BYTE, (byte) slot.slot);
        wrapper.write(BedrockTypes.VAR_INT, slot.netId());
    }
}
