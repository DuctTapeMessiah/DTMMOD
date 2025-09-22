package com.dtmmod.modules;

import baritone.api.BaritoneAPI;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import com.dtmmod.DTMMOD;
import com.dtmmod.utils.*;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class FarmingSimulator extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .defaultValue(128)
        .min(0)
        .sliderRange(0, 128)
        .build()
    );

    private final Setting<Integer> verticalDistance = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-distance")
        .defaultValue(16)
        .min(1)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Boolean> convertMycelium = sgGeneral.add(new BoolSetting.Builder()
        .name("convert-mycelium")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tillToFarmland = sgGeneral.add(new BoolSetting.Builder()
        .name("till-to-farmland")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> plantSeeds = sgGeneral.add(new BoolSetting.Builder()
        .name("plant-seeds")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> inventoryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("inventory-delay")
        .defaultValue(0)
        .range(0, 200)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Double> placementRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("placement-range")
        .defaultValue(4.5)
        .range(1.0, 5.0)
        .sliderRange(1.0, 5.0)
        .build()
    );

    private final Setting<Boolean> baritoneSelectionMode = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-selection-mode")
        .description("Only consider positions within the Baritone selection for actions.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Whether to show chat messages for events.")
        .defaultValue(false)
        .build()
    );

    private enum State {
        IDLE,
        PATHING,
        ROTATING,
        BREAKING_MUSHROOM,
        PERFORMING_ACTION,
        RETRY_TOOLS
    }


    private final MinecraftClient mc = MinecraftClient.getInstance();
    private BlockPos targetPos = null;
    private BlockPos pathingTarget = null;
    private State state = State.IDLE;
    private final List<Item> shovels = Arrays.asList(Items.NETHERITE_SHOVEL, Items.DIAMOND_SHOVEL, Items.GOLDEN_SHOVEL, Items.IRON_SHOVEL, Items.STONE_SHOVEL, Items.WOODEN_SHOVEL);
    private final List<Item> hoes = Arrays.asList(Items.NETHERITE_HOE, Items.DIAMOND_HOE, Items.GOLDEN_HOE, Items.IRON_HOE, Items.STONE_HOE, Items.WOODEN_HOE);
    private final List<Item> plantables = Arrays.asList(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.CARROT, Items.POTATO);
    private final List<Item> toolWeapons = Arrays.asList(
        Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
        Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
        Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD
    );
    private final Set<BlockPos> unreachable = new HashSet<>();
    private long pathStartTime = 0;
    private long pathingCheckDelay = 2000;
    private boolean baritoneStarted = false;
    private boolean didFirstTill = false;
    private long breakStartTime = 0;
    private long retryTime = 0;
    private String retryType = "";

    public FarmingSimulator() {
        super(DTMMOD.CATEGORY, "Farming Simulator 26", "Automates converting mycelium to path blocks, tilling blocks to farmland, and planting various seeds/crops.");
    }

    @Override
    public void onActivate() {
        targetPos = null;
        pathingTarget = null;
        state = State.IDLE;
        unreachable.clear();
        baritoneStarted = false;
        didFirstTill = false;
        retryTime = 0;
        retryType = "";
        organizeHotbar();
        ChatUtils.info("Farming Simulator activated!");
    }

    @Override
    public void onDeactivate() {
        BaritoneUtils.forceCancel();
        targetPos = null;
        pathingTarget = null;
        state = State.IDLE;
        baritoneStarted = false;
        didFirstTill = false;
        retryTime = 0;
        retryType = "";
        ChatUtils.info("Farming Simulator deactivated!");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        switch (state) {
            case IDLE -> handleIdle();
            case PATHING -> handlePathing();
            case ROTATING -> handleRotating();
            case BREAKING_MUSHROOM -> handleBreakingMushroom();
            case PERFORMING_ACTION -> handlePerformingAction();
            case RETRY_TOOLS -> handleRetryTools();
        }
    }

    private void resetTarget() {
        state = State.IDLE;
        targetPos = null;
    }

    private void handleIdle() {
        if (!manageTools()) {
            toggle();
            return;
        }

        targetPos = findNextTarget();
        if (targetPos == null) {
            if (chatFeedback.get()) ChatUtils.info("No target blocks found in range!");
            toggle();
            return;
        }

        if (isWithinRangeAndVisible(targetPos)) {
            state = State.ROTATING;
            pathingTarget = null;
            return;
        }

        pathingTarget = findAdjacentPathingTarget(targetPos);
        if (pathingTarget == null) {
            if (chatFeedback.get()) ChatUtils.warning("No valid adjacent block found for target at %s, skipping.", targetPos.toShortString());
            unreachable.add(targetPos);
            targetPos = null;
            state = State.IDLE;
            return;
        }

        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(
            String.format("goto %d %d %d", pathingTarget.getX(), pathingTarget.getY(), pathingTarget.getZ())
        );
        state = State.PATHING;
        pathStartTime = System.currentTimeMillis();
        baritoneStarted = false;
    }

    private BlockPos findNextTarget() {
        int viewChunks = mc.options.getViewDistance().getValue();
        int horizontalRange = (maxDistance.get() == 0) ? viewChunks * 16 : Math.max(1, maxDistance.get());
        int verticalRange = verticalDistance.get();
        BlockPos playerPos = mc.player.getBlockPos();

        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, horizontalRange, verticalRange, horizontalRange)) {
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (unreachable.contains(pos)) continue;

            if (baritoneSelectionMode.get()) {
                ISelection selection = BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().getOnlySelection();
                if (selection == null || !isWithinSelection(selection, pos)) {
                    continue;
                }
            }

            BlockState bs = mc.world.getBlockState(pos);
            BlockState aboveState = mc.world.getBlockState(pos.up());

            if (convertMycelium.get() && getAvailableTool(shovels) != null && bs.isOf(Blocks.MYCELIUM) &&
                (aboveState.isAir() || aboveState.isOf(Blocks.BROWN_MUSHROOM) || aboveState.isOf(Blocks.RED_MUSHROOM))) {
                return pos.toImmutable();
            }

            boolean isTillable = bs.isOf(Blocks.DIRT_PATH) || bs.isOf(Blocks.GRASS_BLOCK) || bs.isOf(Blocks.DIRT);

            if (tillToFarmland.get() && getAvailableTool(hoes) != null && !didFirstTill && isTillable && aboveState.isAir() && hasSufficientLight(pos)) {
                return pos.toImmutable();
            }

            if (plantSeeds.get() && getAvailablePlantable() != null && !didFirstTill && bs.isOf(Blocks.FARMLAND) && aboveState.isAir() && hasSufficientLight(pos)) {
                return pos.toImmutable();
            }

            if (didFirstTill) {
                if (plantSeeds.get() && getAvailablePlantable() != null && bs.isOf(Blocks.FARMLAND) && aboveState.isAir() && hasSufficientLight(pos)) {
                    return pos.toImmutable();
                }
                if (tillToFarmland.get() && getAvailableTool(hoes) != null && isTillable && aboveState.isAir() && hasSufficientLight(pos)) {
                    return pos.toImmutable();
                }
            }
        }
        return null;
    }

    private boolean isWithinSelection(ISelection selection, BlockPos pos) {
        if (selection == null) return false;
        BetterBlockPos min = selection.min();
        BetterBlockPos max = selection.max();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() &&
            pos.getY() >= min.getY() && pos.getY() <= max.getY() &&
            pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private boolean hasSufficientLight(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getLightLevel(LightType.SKY, pos.up()) > 9;
    }

    private void handleRotating() {
        if (targetPos == null) {
            state = State.IDLE;
            return;
        }
        Vec3d targetCenter = Vec3d.ofCenter(targetPos).add(0, 0.3, 0);
        RotationUtils.rotateTo(targetCenter);

        if (isWithinRangeAndVisible(targetPos)) {
            state = State.PERFORMING_ACTION;
        } else {
            if (chatFeedback.get()) ChatUtils.warning("No clear line of sight to %s, skipping.", targetPos.toShortString());
            unreachable.add(targetPos);
            state = State.IDLE;
            targetPos = null;
        }
    }

    private void handlePerformingAction() {
        if (targetPos == null) {
            state = State.IDLE;
            return;
        }

        BlockState bs = mc.world.getBlockState(targetPos);
        BlockState aboveState = mc.world.getBlockState(targetPos.up());

        if (bs.isOf(Blocks.MYCELIUM) && (aboveState.isOf(Blocks.BROWN_MUSHROOM) || aboveState.isOf(Blocks.RED_MUSHROOM))) {
            RotationUtils.rotateTo(Vec3d.ofCenter(targetPos.up()).add(0, 0.3, 0));
            state = State.BREAKING_MUSHROOM;
            breakStartTime = System.currentTimeMillis();
            return;
        }

        if (bs.isOf(Blocks.MYCELIUM)) {
            if (!convertMycelium.get()) {
                if (chatFeedback.get()) ChatUtils.info("Skipping mycelium at %s (conversion disabled).", targetPos.toShortString());
                resetTarget();
                return;
            }
            Item tool = getAvailableTool(shovels);
            if (tool == null) {
                startRetry("shovel");
                return;
            }
            InventoryUtils.switchToItem(tool);
            BlockUtils.interactBlock(targetPos, Hand.MAIN_HAND);
            resetTarget();
            return;
        }

        boolean isTillable = bs.isOf(Blocks.DIRT_PATH) || bs.isOf(Blocks.GRASS_BLOCK) || bs.isOf(Blocks.DIRT);
        if (tillToFarmland.get() && isTillable && aboveState.isAir()) {
            if (!hasSufficientLight(targetPos)) {
                if (chatFeedback.get()) ChatUtils.info("Skipping %s due to low light.", targetPos.toShortString());
                resetTarget();
                return;
            }
            Item tool = getAvailableTool(hoes);
            if (tool == null) {
                startRetry("hoe");
                return;
            }
            InventoryUtils.switchToItem(tool);
            BlockUtils.interactBlock(targetPos, Hand.MAIN_HAND);
            didFirstTill = true;
            resetTarget();
            return;
        }

        if (plantSeeds.get() && bs.isOf(Blocks.FARMLAND) && aboveState.isAir()) {
            if (!hasSufficientLight(targetPos)) {
                if (chatFeedback.get()) ChatUtils.info("Skipping %s due to low light.", targetPos.toShortString());
                resetTarget();
                return;
            }
            Item plantable = getAvailablePlantable();
            if (plantable == null) {
                startRetry("plantable");
                return;
            }
            InventoryUtils.switchToItem(plantable);
            BlockUtils.interactBlock(targetPos, Hand.MAIN_HAND);
            didFirstTill = true;
            resetTarget();
            return;
        }

        String reason = !aboveState.isAir() ? " (obstructed above)" : " (unknown reason)";
        if (chatFeedback.get()) {
            ChatUtils.warning("Unexpected block %s at %s%s (skipping).", bs.getBlock().getName().getString(), targetPos.toShortString(), reason);
        }
        resetTarget();
    }

    private void startRetry(String type) {
        retryType = type;
        retryTime = System.currentTimeMillis() + 3000;
        state = State.RETRY_TOOLS;
        if (chatFeedback.get()) {
            ChatUtils.info("Retrying inventory check for %s in 3 seconds...", type);
        }
    }

    private void handleRetryTools() {
        if (System.currentTimeMillis() > retryTime) {
            Item item = null;
            String type = retryType;
            if (type.equals("shovel")) {
                item = getAvailableTool(shovels);
                if (item == null) {
                    ChatUtils.warning("No suitable shovel found after retry!");
                    resetTarget();
                    return;
                }
            } else if (type.equals("hoe")) {
                item = getAvailableTool(hoes);
                if (item == null) {
                    ChatUtils.warning("No suitable hoe found for tilling after retry!");
                    resetTarget();
                    return;
                }
            } else if (type.equals("plantable")) {
                item = getAvailablePlantable();
                if (item == null) {
                    ChatUtils.warning("No plantable seeds or crops found after retry!");
                    resetTarget();
                    return;
                }
            }
            if (chatFeedback.get()) {
                ChatUtils.info("%s now available, resuming action.", type);
            }
            state = State.PERFORMING_ACTION;
            clearCursorIfNeeded();
        }
    }

    private void handleBreakingMushroom() {
        if (targetPos == null) {
            resetTarget();
            return;
        }

        if (System.currentTimeMillis() - breakStartTime > 5000) {
            if (chatFeedback.get()) ChatUtils.warning("Mushroom break timeout at %s, skipping target.", targetPos.toShortString());
            unreachable.add(targetPos);
            resetTarget();
            return;
        }

        BlockPos mushroomPos = targetPos.up();
        BlockState bs = mc.world.getBlockState(mushroomPos);

        if (bs.isAir()) {
            state = State.PERFORMING_ACTION;
            return;
        }

        if (!bs.isOf(Blocks.BROWN_MUSHROOM) && !bs.isOf(Blocks.RED_MUSHROOM)) {
            state = State.PERFORMING_ACTION;
            return;
        }

        mc.interactionManager.updateBlockBreakingProgress(mushroomPos, mc.player.getHorizontalFacing());
        mc.player.swingHand(Hand.MAIN_HAND);

    }


    private void handlePathing() {
        if (!baritoneStarted && System.currentTimeMillis() - pathStartTime < pathingCheckDelay) {
            return;
        }

        if (!baritoneStarted && BaritoneUtils.isPathing()) {
            baritoneStarted = true;
        }

        if (targetPos != null && isWithinRangeAndVisible(targetPos)) {
            BaritoneUtils.forceCancel();
            state = State.ROTATING;
            pathStartTime = 0;
            baritoneStarted = false;
            pathingTarget = null;
            return;
        }

        if (!BaritoneUtils.isPathing()) {
            if (targetPos != null && isWithinRangeAndVisible(targetPos)) {
                BaritoneUtils.forceCancel();
                state = State.ROTATING;
                pathStartTime = 0;
                baritoneStarted = false;
                pathingTarget = null;
            } else {
                if (chatFeedback.get()) ChatUtils.warning("Failed to reach adjacent block for target at %s!", targetPos != null ? targetPos.toShortString() : "unknown");
                if (targetPos != null) unreachable.add(targetPos);
                state = State.IDLE;
                targetPos = null;
                pathingTarget = null;
                pathStartTime = 0;
                baritoneStarted = false;
            }
            return;
        }

        BlockPos nearbyTarget = findNearbyTarget();
        if (nearbyTarget != null && isWithinRangeAndVisible(nearbyTarget)) {
            targetPos = nearbyTarget;
            pathingTarget = findAdjacentPathingTarget(targetPos);
            if (pathingTarget == null && !isWithinRangeAndVisible(targetPos)) {
                if (chatFeedback.get()) ChatUtils.warning("No valid adjacent block found for nearby target at %s, skipping.", targetPos.toShortString());
                unreachable.add(targetPos);
                targetPos = null;
                state = State.IDLE;
                return;
            }
            BaritoneUtils.forceCancel();
            if (pathingTarget != null) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(
                    String.format("goto %d %d %d", pathingTarget.getX(), pathingTarget.getY(), pathingTarget.getZ())
                );
                state = State.PATHING;
                pathStartTime = System.currentTimeMillis();
                baritoneStarted = false;
            } else {
                state = State.ROTATING;
                pathingTarget = null;
            }
            return;
        }

        if (baritoneStarted && System.currentTimeMillis() - pathStartTime > 30000) {
            if (chatFeedback.get()) ChatUtils.warning("Pathing timeout for target at %s, restarting search.", targetPos != null ? targetPos.toShortString() : "unknown");
            BaritoneUtils.forceCancel();
            if (targetPos != null) unreachable.add(targetPos);
            state = State.IDLE;
            targetPos = null;
            pathingTarget = null;
            pathStartTime = 0;
            baritoneStarted = false;
        }
    }

    private boolean manageTools() {
        if (convertMycelium.get() && getAvailableTool(shovels) == null) {
            ChatUtils.warning("No suitable shovel found!");
            return false;
        }
        if (tillToFarmland.get() && getAvailableTool(hoes) == null) {
            ChatUtils.warning("No suitable hoe found!");
            return false;
        }
        if (plantSeeds.get() && getAvailablePlantable() == null) {
            ChatUtils.warning("No plantable seeds or crops found!");
            return false;
        }
        return true;
    }


    private Item getAvailableTool(List<Item> items) {
        for (Item item : items) {
            int slot = InventoryUtils.findItemSlot(item);
            if (slot != -1) {
                ItemStack stack = mc.player.getInventory().getStack(slot);
                if (!ItemUtils.hasDurability(stack) || ItemUtils.getPercentageDurability(stack) >= 20) {
                    return item;
                }
            }
        }
        return null;
    }

    private Item getAvailablePlantable() {
        for (Item item : plantables) {
            int slot = InventoryUtils.findItemSlot(item);
            if (slot != -1) {
                return item;
            }
        }
        return null;
    }

    private boolean isWithinRangeAndVisible(BlockPos pos) {
        if (pos == null) return false;
        double distance = mc.player.getPos().distanceTo(Vec3d.ofCenter(pos));
        if (distance > placementRange.get()) return false;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetCenter = Vec3d.ofCenter(pos).add(0, 0.3, 0);
        RaycastContext ctx = new RaycastContext(eyePos, targetCenter,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult hit = mc.world.raycast(ctx);
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private BlockPos findAdjacentPathingTarget(BlockPos targetPos) {
        if (mc.world == null || !mc.world.isChunkLoaded(targetPos.getX() >> 4, targetPos.getZ() >> 4)) {
            return null;
        }

        double distanceToTarget = mc.player.getPos().distanceTo(Vec3d.ofCenter(targetPos));
        if (distanceToTarget <= placementRange.get()) {
            return null;
        }

        int[] xOffsets = {-1, 0, 1};
        int[] zOffsets = {-1, 0, 1};
        int[] yOffsets = {-1, 0, 1};

        for (int yOffset : yOffsets) {
            for (int xOffset : xOffsets) {
                for (int zOffset : zOffsets) {
                    if (xOffset == 0 && zOffset == 0) continue;
                    if (Math.abs(xOffset) != 1 && xOffset != 0) continue;
                    if (Math.abs(zOffset) != 1 && zOffset != 0) continue;

                    BlockPos pos = targetPos.add(xOffset, yOffset, zOffset);
                    if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    if (unreachable.contains(pos)) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    BlockState belowState = mc.world.getBlockState(pos.down());
                    if (state.isAir() && belowState.isSolidBlock(mc.world, pos.down())) {
                        Vec3d eyePos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
                        Vec3d targetCenter = Vec3d.ofCenter(targetPos).add(0, 0.3, 0);
                        RaycastContext ctx = new RaycastContext(eyePos, targetCenter,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
                        BlockHitResult hit = mc.world.raycast(ctx);
                        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(targetPos)) {
                            return pos.toImmutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findNearbyTarget() {
        int range = 5;
        BlockPos playerPos = mc.player.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(playerPos, range, range, range)) {
            if (!mc.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (unreachable.contains(pos)) continue;

            if (baritoneSelectionMode.get()) {
                ISelection selection = BaritoneAPI.getProvider().getPrimaryBaritone().getSelectionManager().getOnlySelection();
                if (selection == null || !isWithinSelection(selection, pos)) {
                    continue;
                }
            }

            BlockState bs = mc.world.getBlockState(pos);
            BlockState aboveState = mc.world.getBlockState(pos.up());

            if (plantSeeds.get() && getAvailablePlantable() != null && bs.isOf(Blocks.FARMLAND) && aboveState.isAir() && hasSufficientLight(pos)) {
                return pos.toImmutable();
            }

            boolean isTillable = bs.isOf(Blocks.DIRT_PATH) || bs.isOf(Blocks.GRASS_BLOCK) || bs.isOf(Blocks.DIRT);

            if (tillToFarmland.get() && getAvailableTool(hoes) != null && isTillable && aboveState.isAir() && hasSufficientLight(pos)) {
                return pos.toImmutable();
            }

            if (convertMycelium.get() && getAvailableTool(shovels) != null && bs.isOf(Blocks.MYCELIUM) &&
                (aboveState.isAir() || aboveState.isOf(Blocks.BROWN_MUSHROOM) || aboveState.isOf(Blocks.RED_MUSHROOM)) && hasSufficientLight(pos)) {
                return pos.toImmutable();
            }
        }
        return null;
    }

    private void organizeHotbar() {
        if (convertMycelium.get()) {
            Item shovelItem = getAvailableTool(shovels);
            if (shovelItem != null) {
                boolean inHotbar = false;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(shovelItem)) {
                        inHotbar = true;
                        break;
                    }
                }
                if (!inHotbar) {
                    int invSlot = InventoryUtils.findItemSlot(shovelItem);
                    if (invSlot >= 9) {
                        int target = findReplaceableHotbarSlot(3);
                        if (target != -1) {
                            swapSlots(invSlot, target);
                        }
                    }
                }
            }
        }
        if (tillToFarmland.get()) {
            Item hoeItem = getAvailableTool(hoes);
            if (hoeItem != null) {
                boolean inHotbar = false;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(hoeItem)) {
                        inHotbar = true;
                        break;
                    }
                }
                if (!inHotbar) {
                    int invSlot = InventoryUtils.findItemSlot(hoeItem);
                    if (invSlot >= 9) {
                        int target = findReplaceableHotbarSlot(3);
                        if (target != -1) {
                            swapSlots(invSlot, target);
                        }
                    }
                }
            }
        }
        if (plantSeeds.get()) {
            Item plantItem = getAvailablePlantable();
            if (plantItem != null) {
                boolean inHotbar = false;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).isOf(plantItem)) {
                        inHotbar = true;
                        break;
                    }
                }
                if (!inHotbar) {
                    int invSlot = InventoryUtils.findItemSlot(plantItem);
                    if (invSlot >= 9) {
                        int target = findReplaceableHotbarSlot(3);
                        if (target != -1) {
                            swapSlots(invSlot, target);
                        }
                    }
                }
            }
        }
        moveToEmptyHotbar(this::isPriorityFood);
        moveToEmptyHotbar(this::isToolWeapon);
    }

    private void clearCursorIfNeeded() {
        ItemStack cursorStack = mc.player.playerScreenHandler.getCursorStack();
        if (!cursorStack.isEmpty()) {
            int emptySlot = InventoryUtils.findEmptySlot();
            if (emptySlot != -1) {
                int screenSlot = emptySlot < 9 ? 36 + emptySlot : emptySlot;
                InventoryUtils.clickSlotDelay(screenSlot, SlotActionType.PICKUP, 0, inventoryDelay.get());
            }
        }
    }

    private int getPriority(Item item) {
        if (item == null) return -1;
        if (isEssential(item)) return 3;
        if (isPriorityFood(item)) return 2;
        if (isToolWeapon(item)) return 1;
        return 0;
    }

    private boolean isEssential(Item item) {
        return shovels.contains(item) || hoes.contains(item) || plantables.contains(item);
    }

    private boolean isPriorityFood(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.get(DataComponentTypes.FOOD) != null && item != Items.ROTTEN_FLESH;
    }

    private boolean isToolWeapon(Item item) {
        return toolWeapons.contains(item);
    }

    private int findReplaceableHotbarSlot(int minPrio) {
        int best = -1;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int prio = stack.isEmpty() ? -1 : getPriority(stack.getItem());
            if (prio < minPrio && prio < lowest) {
                lowest = prio;
                best = i;
            }
        }
        return best;
    }

    private void swapSlots(int invSlot, int hotbarSlot) {
        int screenInv = invSlot;
        int screenHot = 36 + hotbarSlot;
        InventoryUtils.clickSlotDelay(screenInv, SlotActionType.PICKUP, 0, inventoryDelay.get());
        InventoryUtils.clickSlotDelay(screenHot, SlotActionType.PICKUP, 0, inventoryDelay.get());
        InventoryUtils.clickSlotDelay(screenInv, SlotActionType.PICKUP, 0, inventoryDelay.get());
    }

    private void moveToEmptyHotbar(Predicate<Item> predicate) {
        int empty = InventoryUtils.findEmptyHotbarSlot();
        while (empty != -1) {
            boolean moved = false;
            for (int s = 9; s < 36; s++) {
                ItemStack st = mc.player.getInventory().getStack(s);
                if (!st.isEmpty() && predicate.test(st.getItem())) {
                    int screenS = s;
                    int screenH = 36 + empty;
                    InventoryUtils.clickSlotDelay(screenS, SlotActionType.PICKUP, 0, inventoryDelay.get());
                    InventoryUtils.clickSlotDelay(screenH, SlotActionType.PICKUP, 0, inventoryDelay.get());
                    moved = true;
                    break;
                }
            }
            if (!moved) break;
            empty = InventoryUtils.findEmptyHotbarSlot();
        }
        clearCursorIfNeeded();
    }
}
