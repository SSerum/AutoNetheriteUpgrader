package com.example.addon;

import static com.example.addon.AddonTemplate.CATEGORY;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoNetheriteUpgrade extends Module {

    public AutoNetheriteUpgrade() {
        super(CATEGORY, "Auto Netherite Upgrade", "Slowly upgrades diamond gear to netherite, one step at a time.");
    }

    private int step = 0;
    private int cooldown = 0;
    private int currentInvSlot = -1;
    private boolean holdingOutput = false;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(mc.currentScreen instanceof SmithingScreen)) return;
        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler handler)) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        switch (step) {
            case 0 -> {
                currentInvSlot = findInventorySlot(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
                if (currentInvSlot != -1 && handler.getSlot(0).getStack().isEmpty()) {
                    if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (clickInventorySlot(handler, currentInvSlot)) cooldown = 4;
                    } else {
                        step++;
                        cooldown = 2;
                    }
                } else if (!handler.getSlot(0).getStack().isEmpty()) {
                    step++; // template already there
                }
            }

            case 1 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(0);
                    cooldown = 4;
                    step++;
                } else if (!handler.getSlot(0).getStack().isEmpty()) {
                    step++;
                } else step = 0;
            }

            case 2 -> {
                currentInvSlot = findDiamondGearSlot();
                if (currentInvSlot != -1 && handler.getSlot(1).getStack().isEmpty()) {
                    if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (clickInventorySlot(handler, currentInvSlot)) cooldown = 4;
                    } else {
                        step++;
                        cooldown = 2;
                    }
                } else if (!handler.getSlot(1).getStack().isEmpty()) {
                    step++;
                } else step = 8; // No more gear
            }

            case 3 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(1);
                    cooldown = 4;
                    step++;
                } else if (!handler.getSlot(1).getStack().isEmpty()) {
                    step++;
                } else step = 2;
            }

            case 4 -> {
                currentInvSlot = findInventorySlot(Items.NETHERITE_INGOT);
                if (currentInvSlot != -1 && handler.getSlot(2).getStack().isEmpty()) {
                    if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (clickInventorySlot(handler, currentInvSlot)) cooldown = 4;
                    } else {
                        step++;
                        cooldown = 2;
                    }
                } else if (!handler.getSlot(2).getStack().isEmpty()) {
                    step++;
                } else step = 8; // no netherite
            }

            case 5 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(2);
                    cooldown = 4;
                    step++;
                } else if (!handler.getSlot(2).getStack().isEmpty()) {
                    step++;
                } else step = 4;
            }

            case 6 -> {
                if (!handler.getSlot(3).getStack().isEmpty() && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(3);
                    holdingOutput = true;
                    cooldown = 4;
                } else if (holdingOutput) {
                    step++;
                }
            }

            case 7 -> {
                if (holdingOutput) {
                    int emptySlot = findFirstEmptyInventorySlot();
                    if (emptySlot != -1) {
                        if (clickInventorySlot(handler, emptySlot)) {
                            holdingOutput = false;
                            cooldown = 4;
                            step = 2; // Repeat upgrade for next item
                        }
                    } else {
                        cooldown = 10;
                    }
                } else {
                    step = 2;
                }
            }

            case 8 -> {
                if (handler.getSlot(0).getStack().isEmpty()
                    && handler.getSlot(1).getStack().isEmpty()
                    && handler.getSlot(2).getStack().isEmpty()) {
                    if (findDiamondGearSlot() != -1 && findInventorySlot(Items.NETHERITE_INGOT) != -1) {
                        step = 0;
                    } else {
                        cooldown = 20;
                    }
                }
            }
        }
    }

    private boolean clickInventorySlot(SmithingScreenHandler handler, int invSlot) {
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).inventory == mc.player.getInventory()
                    && handler.getSlot(i).getIndex() == invSlot) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return true;
            }
        }
        return false;
    }

    private void clickSmithingSlot(int slotId) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findInventorySlot(Item item) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findDiamondGearSlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isDiamondGear(stack.getItem())) return i;
        }
        return -1;
    }

    private int findFirstEmptyInventorySlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean isDiamondGear(Item item) {
        return item == Items.DIAMOND_HELMET
            || item == Items.DIAMOND_CHESTPLATE
            || item == Items.DIAMOND_LEGGINGS
            || item == Items.DIAMOND_BOOTS
            || item == Items.DIAMOND_SWORD
            || item == Items.DIAMOND_PICKAXE
            || item == Items.DIAMOND_AXE
            || item == Items.DIAMOND_SHOVEL
            || item == Items.DIAMOND_HOE;
    }
}
