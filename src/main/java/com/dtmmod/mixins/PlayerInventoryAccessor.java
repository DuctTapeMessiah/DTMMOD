package com.dtmmod.mixins;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInventory.class)
public interface PlayerInventoryAccessor {
    @Accessor("selectedSlot")
    void setSelectedSlot(int slot);

    @Accessor("selectedSlot")
    int getSelectedSlot();

    @Accessor("main")
    DefaultedList<ItemStack> getMain();
}
