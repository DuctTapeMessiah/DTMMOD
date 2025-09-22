package com.dtmmod.utils;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;

public class ItemUtils extends Utils {

    public static int getPercentageDurability(ItemStack itemStack) {
        return (int)(((double)getDurability(itemStack) / (double)itemStack.getMaxDamage()) * 100);
    }

    public static boolean hasDurability(ItemStack itemStack) {
        return itemStack.getMaxDamage() != 0;
    }

    public static Formatting getDurabilityColor(ItemStack itemStack) {
        Formatting color = Formatting.GREEN;
        int durability = ItemUtils.getPercentageDurability(itemStack);

        if (durability < 20) {
            color = Formatting.RED;
        } else if (durability < 60) {
            color = Formatting.GOLD;
        }

        return color;
    }

    public static int getDurability(ItemStack itemStack) {
        return itemStack.getMaxDamage() - itemStack.getDamage();
    }

}
