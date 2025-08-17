package com.dtmmod.utils;


import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

public class BaritoneUtils extends Utils {
    public static void goTo(BlockPos pos) {
        getBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos.getX(), pos.getY(), pos.getZ()));
    }

    public static void forceCancel() {
        getBaritone().getPathingBehavior().forceCancel();
    }

    public static boolean isPathing() {
        return getBaritone().getPathingBehavior().isPathing();
    }

    public static IBaritone getBaritone() {
        return BaritoneAPI.getProvider().getPrimaryBaritone();
    }

}
