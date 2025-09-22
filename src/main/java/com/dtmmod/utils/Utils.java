package com.dtmmod.utils;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Utils {
    public static MinecraftClient mc = MinecraftClient.getInstance();

    private static final HashMap<String, Block> blockMap = Registries.BLOCK.stream().collect(Collectors.toMap(b -> b.getName().getString(), Function.identity(), (a, b) -> b, HashMap::new));
    private static final HashMap<String, Item> itemMap = Registries.ITEM.stream().collect(Collectors.toMap(i -> i.getName().getString(), Function.identity(), (a, b) -> b, HashMap::new));
    public static final Direction[] SIDE_DIRECTIONS = {Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH};

    public static int random(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

    @SuppressWarnings("removal")
    public static void suspend(Thread thread) {
        if (thread != null) thread.suspend();
    }

    public static String[] getLastSplit(String input, String prefix) {
        int i = input.lastIndexOf(prefix);
        try {
            return new String[]{input.substring(0, i), input.substring(i + 1)};
        } catch (Exception ignored) {
            return new String[]{"", ""};
        }
    }


    public static Color generateColorFromHash(int hashCode, int alpha) {
        int positiveHashCode = Math.abs(hashCode);

        int red = (positiveHashCode >> 16) & 0xFF;
        int green = (positiveHashCode >> 8) & 0xFF;
        int blue = positiveHashCode & 0xFF;

        return new Color(red, green, blue, alpha);
    }

    public static Block getBlockFromName(String name) {
        return blockMap.get(name);
    }

    public static Item getItemFromName(String name) {
        return itemMap.get(name);
    }

    public static String[] addToArray(String[] myArray, String newItem) {
        int currentSize = myArray.length;
        int newSize = currentSize + 1;
        String[] tempArray = new String[newSize];
        System.arraycopy(myArray, 0, tempArray, 0, currentSize);
        tempArray[newSize - 1] = newItem;

        return tempArray;
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static double getDecimals(double d) {
        return d - (int) d;
    }

    public static List<String> getStringListThatFitsIntoWidth(MatrixStack stack, String string, int width) {
        List<String> list = new ArrayList<>();
        String current = "";
        for (String space : string.split(" ")) {
            int measured = mc.textRenderer.getWidth(current + " " + space);
            if (measured >= width - 2) {
                list.add(current);
                current = space;
            } else {
                current += (current.isEmpty() ? "" : " ") + space;
            }
        }
        list.add(current);
        return list;
    }


    public static boolean sleepUntil(BooleanSupplier condition, int timeout, int sleepAmount) {
        long startTime = System.currentTimeMillis();
        while (!condition.getAsBoolean() && (timeout == -1 || System.currentTimeMillis() - startTime < timeout)) {
            sleep(sleepAmount);
        }
        return condition.getAsBoolean();
    }

    public static boolean sleepUntil(BooleanSupplier condition, int timeout) {
        return sleepUntil(condition, timeout, 10);
    }

    public static String vecToString(Vec3d vec) {
        return vec.x + "," + vec.y + "," + vec.z;
    }

    public static Vec3d vecFromString(String s) {
        String[] split = s.split(",");
        return new Vec3d(Double.parseDouble(split[0]), Double.parseDouble(split[1]), Double.parseDouble(split[2]));
    }

}
