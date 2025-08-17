package com.dtmmod.utils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.dtmmod.utils.Utils.mc;

public class BlockUtils {
    public static List<BlockPos> getAll(Vec3d around, int radius, boolean sort) {
        List<BlockPos> list = new ArrayList<>();
        for (int x = (int) (around.x - radius); x < around.x + radius; x++) {
            for (int z = (int) (around.z - radius); z < around.z + radius; z++) {
                for (int y = (int) (around.y + radius); y > around.y - radius; y--) {
                    list.add(new BlockPos(x, y, z));
                }
            }
        }
        if (sort) {
            list.sort(Comparator.comparingDouble(lhs -> around.squaredDistanceTo(lhs.getX(), lhs.getY(), lhs.getZ())));
        }
        return list;
    }

    public static BlockPos findBlock(Vec3d around, Block block, int radius) {
        return getAll(around, radius, true).stream()
            .filter(pos -> getBlock(pos) == block)
            .findFirst()
            .orElse(null);
    }

    public static BlockPos findBlockAtY151(Vec3d around, Block block, int radius) {
        List<BlockPos> list = new ArrayList<>();
        int centerX = (int) Math.floor(around.x);
        int centerZ = (int) Math.floor(around.z);
        int fixedY = 151;

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                list.add(new BlockPos(x, fixedY, z));
            }
        }

        list.sort(Comparator.comparingDouble(lhs -> around.squaredDistanceTo(lhs.getX(), lhs.getY(), lhs.getZ())));
        return list.stream()
            .filter(pos -> getBlock(pos) == block)
            .findFirst()
            .orElse(null);
    }

    public static int distance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) +
            Math.abs(first.getY() - second.getY()) +
            Math.abs(first.getZ() - second.getZ());
    }

    public static double distanceToPlayer(BlockPos pos) {
        return Math.abs(mc.player.getPos().x - pos.getX()) +
            Math.abs(mc.player.getPos().y - pos.getY()) +
            Math.abs(mc.player.getPos().z - pos.getZ());
    }

    public static Vec3d toVec(BlockPos pos) {
        return new Vec3d(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos fromVec(Vec3d vec) {
        return new BlockPos((int) vec.x, (int) vec.y, (int) vec.z);
    }

    public static boolean isSolid(BlockPos pos) {
        return mc.world.getBlockState(pos).isSolidBlock(mc.world, pos);
    }

    public static Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    public static boolean canPlaceAt(BlockPos pos, boolean ignoreSelf) {
        return mc.world.getBlockState(pos).canPlaceAt(mc.world, pos);
    }

    public static Color getUniqueColor(BlockPos pos, int alpha) {
        MapColor mapColor = mc.world.getBlockState(pos).getMapColor(mc.world, pos);
        if (getBlock(pos) == Blocks.NETHER_PORTAL) {
            mapColor = MapColor.PURPLE;
        } else if (mapColor == MapColor.CLEAR) {
            return null;
        }

        int c = mapColor.getRenderColor(MapColor.Brightness.NORMAL);
        Color color = new Color(c);
        return new Color(color.getBlue(), color.getGreen(), color.getRed(), alpha);
    }
    public static ActionResult interactBlock(BlockPos pos, Hand hand) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return ActionResult.FAIL;
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        return mc.interactionManager.interactBlock(mc.player, hand, hit);
    }

    public static Box getSideBox(Box box, Direction side) {
        switch (side) {
            case WEST: return new Box(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            case NORTH: return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
            case EAST: return new Box(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.minZ);
            case SOUTH: return new Box(box.maxX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ);
            case DOWN: return new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
            case UP: return new Box(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
            default: return box;
        }
    }

    public static Box getBbFromPos(BlockPos pos) {
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) {
            return null;
        }

        Box temp = shape.getBoundingBox();
        return new Box(
            temp.minX + pos.getX(), temp.minY + pos.getY(), temp.minZ + pos.getZ(),
            temp.maxX + pos.getX(), temp.maxY + pos.getY(), temp.maxZ + pos.getZ()
        );
    }

    public static double distanceXZ(Vec3d pos1, Vec3d pos2) {
        double dx = pos1.x - pos2.x;
        double dz = pos1.z - pos2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
