package com.dtmmod.utils;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.*;

import java.util.Optional;

import static com.dtmmod.utils.Utils.mc;


public class RotationUtils {
    public static void rotateTo(Vec3d vec, boolean sendPacket) {
        float[] rotations = getRotations(vec);
        mc.player.setYaw(rotations[0]);
        mc.player.setPitch(rotations[1]);

        if (sendPacket) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(),
                mc.player.getPitch(),
                mc.player.isOnGround(),
                false // changePosition set to false since we're only updating rotation
            ));
        }
    }

    public static void rotateTo(Vec3d vec) { // No Packet
        rotateTo(vec, false);
    }

    public static void rotateTo(BlockPos pos, double yAdd, Direction setDirection) { // Best direction
        Direction closestDirection = Direction.UP;
        boolean directionSet = false;

        for (Direction direction : Direction.values()) {
            if (BlockUtils.distance(mc.player.getBlockPos(), pos.offset(direction)) < BlockUtils.distance(mc.player.getBlockPos(), pos.offset(closestDirection)) &&
                mc.world.getBlockState(pos.offset(direction)).isAir()) {
                closestDirection = direction;
                directionSet = true;
            }
        }

        if (!directionSet) {
            for (Direction direction : Direction.values()) {
                if (BlockUtils.distance(mc.player.getBlockPos(), pos.offset(direction)) < BlockUtils.distance(mc.player.getBlockPos(), pos.offset(closestDirection)) &&
                    mc.world.getBlockState(pos.offset(direction)).isOpaque()) {
                    closestDirection = direction;
                }
            }
        }

        if (setDirection != null) {
            closestDirection = setDirection;
        }

        Vec3d targetVec = switch (closestDirection) {
            case SOUTH, WEST, NORTH, EAST -> new Vec3d(pos.getX() + 0.5, pos.getY() + 0.25 + yAdd, pos.getZ() + 0.5);
            case UP -> new Vec3d(pos.getX() + 0.5, pos.getY() + 1 + yAdd, pos.getZ() + 0.5);
            case DOWN -> new Vec3d(pos.getX() + 0.5, pos.getY() + yAdd, pos.getZ() + 0.5);
        };

        rotateTo(targetVec);
    }



    public static float[] getRotations(Vec3d vec) {
        Vec3d eyesPos = new Vec3d(mc.player.getPos().x,
            mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getPos().z);

        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{
            mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()),
            mc.player.getPitch() + MathHelper.wrapDegrees(pitch - mc.player.getPitch())
        };
    }

    public static float[] getRequiredRotations(Vec3d target) {
        Vec3d eyesPos = new Vec3d(mc.player.getX(),
            mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
            mc.player.getZ());

        double diffX = target.x - eyesPos.x;
        double diffY = target.y - eyesPos.y;
        double diffZ = target.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[] {
            MathHelper.wrapDegrees(yaw),
            MathHelper.wrapDegrees(pitch)
        };
    }

    public static class Rotation {
        private final float yaw;
        private final float pitch;

        public Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }
    }
    public static boolean isLookingAt(Vec3d target) {
        float[] required = getRequiredRotations(target);

        float yawDiff = MathHelper.wrapDegrees(required[0] - mc.player.getYaw());
        float pitchDiff = MathHelper.wrapDegrees(required[1] - mc.player.getPitch());

        // Adjust these thresholds to be stricter or looser
        return Math.abs(yawDiff) < 3.0f && Math.abs(pitchDiff) < 3.0f;
    }
    public static boolean isLookingAtEntity(Entity target) {
        Vec3d camera = mc.player.getCameraPosVec(1.0F);
        Vec3d lookVec = mc.player.getRotationVec(1.0F);
        Vec3d reachVec = camera.add(lookVec.multiply(4.5)); // default reach for survival
        Box box = target.getBoundingBox().expand(0.3); // Add margin to ensure detection
        Optional<Vec3d> hit = box.raycast(camera, reachVec);

        return hit.isPresent();
    }


}
