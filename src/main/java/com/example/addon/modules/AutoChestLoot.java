package com.example.addon.modules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class AutoChestLoot extends Module {
    private final Setting<List<Item>> whitelist = settings.getDefaultGroup().add(
        new ItemListSetting.Builder()
            .name("whitelist")
            .description("Only loot these items.")
            .build()
    );

    private final Setting<Boolean> autoClose = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-close")
        .description("Automatically close the chest after looting.")
        .defaultValue(true)
        .build()
    );

    private boolean hasRunGoto = false;
    private boolean miningInProgress = false;
    private List<BlockPos> chestPositions = new ArrayList<>();
    private int mineIndex = 0;
    private final Set<BlockPos> breakingBlocks = new HashSet<>();

    // Rotation-related fields for smooth rotation
    private boolean rotatingToNextBlock = false;
    private float targetYaw;
    private float targetPitch;
    private final float rotationSpeed = 10f; // degrees per tick, tweak as needed

    public AutoChestLoot() {
        super(AddonTemplate.CATEGORY, "auto-jew", "Walks to the nearest chest and loots whitelisted items.");
    }

    private void updateTargetRotation(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = pos.getZ() + 0.5 - mc.player.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        targetPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
    }

    private boolean rotateTowardsTarget() {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();

        boolean yawDone = Math.abs(yawDiff) < 1f;
        boolean pitchDone = Math.abs(pitchDiff) < 1f;

        if (!yawDone) {
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -rotationSpeed, rotationSpeed));
        }
        if (!pitchDone) {
            mc.player.setPitch(mc.player.getPitch() + MathHelper.clamp(pitchDiff, -rotationSpeed, rotationSpeed));
        }

        return yawDone && pitchDone;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Mining mode: hold break properly, only if looking at chest blocks
        if (miningInProgress && !chestPositions.isEmpty()) {
            BlockPos pos = chestPositions.get(mineIndex);
            Block block = mc.world.getBlockState(pos).getBlock();

            if (block == Blocks.CHEST) {
                if (rotatingToNextBlock) {
                    boolean doneRotating = rotateTowardsTarget();
                    if (doneRotating) {
                        rotatingToNextBlock = false; // finished rotating, next tick start breaking
                    }
                    return; // Only rotate this tick
                }

                // Check if player is looking at the block (raycast)
                HitResult raycast = mc.player.raycast(5, 1.0f, false);
                if (raycast.getType() == HitResult.Type.BLOCK && ((BlockHitResult) raycast).getBlockPos().equals(pos)) {
                    // Start breaking if not started already
                    if (!breakingBlocks.contains(pos)) {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
                        breakingBlocks.add(pos);
                    }

                    // Update breaking progress (hold break)
                    mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);

                    // Swing main hand for animation
                    mc.player.swingHand(Hand.MAIN_HAND);
                } else {
                    // Player not looking at chest, cancel breaking
                    if (breakingBlocks.contains(pos)) {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
                        breakingBlocks.remove(pos);
                    }
                }
            } else {
                // Block broken or no longer chest block
                if (breakingBlocks.contains(pos)) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
                    breakingBlocks.remove(pos);
                }

                chestPositions.remove(mineIndex);

                if (chestPositions.isEmpty()) {
                    miningInProgress = false;
                } else {
                    // Move to next block in the list
                    if (mineIndex >= chestPositions.size()) mineIndex = 0;
                    else mineIndex = mineIndex % chestPositions.size();

                    // Start rotating toward next chest block before breaking
                    updateTargetRotation(chestPositions.get(mineIndex));
                    rotatingToNextBlock = true;
                }
            }
            return; // Skip the rest while mining
        }

        // Not in chest screen
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            if (!hasRunGoto && !miningInProgress) {
                mc.player.networkHandler.sendChatMessage("#goto chest");
                hasRunGoto = true;
            }
            return;
        }

        // In chest screen: reset goto flag
        hasRunGoto = false;

        Inventory inv = ((GenericContainerScreen) mc.currentScreen).getScreenHandler().getInventory();
        boolean foundItem = false;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && whitelist.get().contains(stack.getItem())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                foundItem = true;
                return;
            }
        }

        // No whitelisted items found
        if (!foundItem) {
            if (autoClose.get()) mc.player.closeHandledScreen();

            // Raycast to find chest block position
            HitResult hit = mc.player.raycast(5, 1.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos basePos = blockHit.getBlockPos();

                chestPositions.clear();
                chestPositions.add(basePos);
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos neighbor = basePos.offset(dir);
                    if (mc.world.getBlockState(neighbor).getBlock() == Blocks.CHEST) {
                        chestPositions.add(neighbor);
                    }
                }

                miningInProgress = true;
                mineIndex = 0;

                // Start rotating toward first chest block before mining
                updateTargetRotation(chestPositions.get(mineIndex));
                rotatingToNextBlock = true;
            }
        }
    }

    @Override
    public void onDeactivate() {
        hasRunGoto = false;
        miningInProgress = false;
        chestPositions.clear();
        rotatingToNextBlock = false;

        // Cancel all block breakings on deactivate
        for (BlockPos pos : breakingBlocks) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
        }
        breakingBlocks.clear();
    }
}
