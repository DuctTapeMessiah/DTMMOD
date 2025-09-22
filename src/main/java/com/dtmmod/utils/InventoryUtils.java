package com.dtmmod.utils;

import com.dtmmod.mixins.PlayerInventoryAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

import static com.dtmmod.utils.Utils.sleep;

public class InventoryUtils {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static ItemStack getStack(int slot) {
        if (mc.player == null || mc.player.playerScreenHandler == null || slot < 0 || slot >= mc.player.playerScreenHandler.getStacks().size()) {
            return ItemStack.EMPTY;
        }
        return mc.player.playerScreenHandler.getStacks().get(slot);
    }

    public static int getAmountOfItem(Item item) {
        int count = 0;
        for (ItemStack stack : mc.player.playerScreenHandler.getStacks()) {
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static int getFreeSpace() {
        int count = -10;
        for (ItemStack stack : mc.player.playerScreenHandler.getStacks()) {
            if (stack.isEmpty()) {
                count += 1;
            }
        }
        return count;
    }

    public static int getEmptySlotCount() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int getHotbarSlot(Item item) {
        for (int i = 36; i < 44; i++) {
            if (mc.player.playerScreenHandler.getSlot(i).getStack().getItem() == item) {
                return i - 36;
            }
        }
        return -1;
    }

    public static void switchToItem(Item item) {
        int hotbarSlot = getHotbarSlot(item);
        if (hotbarSlot != -1) {
            setSelectedHotbarSlot(hotbarSlot);
            return;
        }

        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot.getStack().getItem() == item) {
                quickMove(slot.id);
                sleep(100);
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                hotbarSlot = getHotbarSlot(item);
                if (hotbarSlot != -1) {
                    setSelectedHotbarSlot(hotbarSlot);
                }

                return;
            }
        }
    }

    public static void clickSlot(int id, int button, SlotActionType actionType) {
        if (id != -1) {
            try {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, id, button, actionType, mc.player);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void clickSlotDelay(int slot, SlotActionType actionType, int button, int delayMs) {
        if (mc.interactionManager == null || mc.player == null) return;

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            slot,
            button,
            actionType,
            mc.player
        );

        Utils.sleep(delayMs);
    }

    public static void leftClickPickup(int slot, int delayMs) {
        clickSlotDelay(slot, SlotActionType.PICKUP, 0, delayMs);
    }

    public static void rightClickPickup(int slot, int delayMs) {
        clickSlotDelay(slot, SlotActionType.PICKUP, 1, delayMs);
    }

    public static List<StackAndSlot> getStacksWithSlots() {
        List<StackAndSlot> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            list.add(new StackAndSlot(getStack(i), i));
        }
        return list;
    }

    public static List<ItemStack> getStacks() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            list.add(getStack(i));
        }
        return list;
    }

    public static void quickMove(int id) {
        if (id != -1) {
            try {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, id, 0, SlotActionType.QUICK_MOVE, mc.player);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void throwAway(int id) {
        if (id != -1) {
            try {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, id, 1, SlotActionType.THROW, mc.player);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static int findItemSlot(Item item) {
        return findItemSlot(item, true);
    }

    public static void quickMoveSlot(int slot) {
        clickSlot(slot, 0, SlotActionType.QUICK_MOVE);
    }

    public static int findItemSlot(Item item, boolean nonEmpty) {
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (item == null && stack.isEmpty()) {
                return slot;
            }
            if (stack.isOf(item)) {
                if (nonEmpty) {
                    if (item == Items.BUNDLE) {
                        BundleContentsComponent contents = stack.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
                        if (!contents.isEmpty()) {
                            return slot;
                        }
                    } else {
                        return slot;
                    }
                } else {
                    if (item == Items.BUNDLE) {
                        BundleContentsComponent contents = stack.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT);
                        if (contents.isEmpty()) {
                            return slot;
                        }
                    }
                }
            }
        }
        return -1;
    }

    public static int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static int findEmptySlot() {
        for (int i = 2; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void moveItemToOffhand(int slot) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
        }
    }

    public static void setSelectedHotbarSlot(int slot) {
        ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
    }

    public static List<Integer> findSlotsWithItem(String itemId) {
        List<Integer> matchingSlots = new ArrayList<>();

        if (mc.player == null || mc.player.currentScreenHandler == null) return matchingSlots;

        ScreenHandler handler = mc.player.currentScreenHandler;
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();

            if (!stack.isEmpty() && stack.getItem().toString().equals(itemId)) {
                matchingSlots.add(i);
            }
        }

        return matchingSlots;
    }

    public record StackAndSlot(ItemStack itemStack, int slot) {}
}
