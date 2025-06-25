package com.example.addon.modules;

import static com.example.addon.AddonTemplate.CATEGORY;
import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoNetheriteUpgrade extends Module {

    private final Setting<Boolean> autoDrop = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-drop")
        .description("Automatically drop netherite items after upgrading.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> cooldownDelay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("cooldown-delay")
        .description("Cooldown delay in ticks between actions. Lower = faster, higher = slower.")
        .defaultValue(4)
        .min(1)
        .max(20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> noCooldown = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("no-cooldown")
        .description("If enabled, skips cooldown delays to speed up actions (may cause desync or kicks).")
        .defaultValue(false)
        .build()
    );

    public AutoNetheriteUpgrade() {
        super(AddonTemplate.CATEGORY, "auto-netherite-upgrade", "Slowly upgrades diamond gear to netherite.");
    }

    private int step = 0;
    private int cooldown = 0;
    private int currentInvSlot = -1;
    private boolean holdingOutput = false;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(mc.currentScreen instanceof SmithingScreen)) return;
        if (!(mc.player.currentScreenHandler instanceof SmithingScreenHandler handler)) return;

        if (!noCooldown.get() && cooldown > 0) {
            cooldown--;
            return;
        }

        // Check if gear slot (1) contains non-diamond gear, drop it immediately
        ItemStack gearStack = handler.getSlot(1).getStack();
        if (!gearStack.isEmpty() && !isDiamondGear(gearStack.getItem())) {
            int gearSlotIndex = -1;
            for (int i = 0; i < handler.slots.size(); i++) {
                if (handler.getSlot(i) == handler.getSlot(1)) {
                    gearSlotIndex = i;
                    break;
                }
            }
            if (gearSlotIndex != -1) {
                mc.interactionManager.clickSlot(handler.syncId, gearSlotIndex, 0, SlotActionType.THROW, mc.player);
                setCooldown();
                return;
            }
        }

        // Auto-refill Template slot (0)
        if (handler.getSlot(0).getStack().isEmpty()) {
            int templateSlot = findInventorySlot(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
            if (templateSlot != -1 && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                clickInventorySlot(handler, templateSlot);
                clickSmithingSlot(0);
                setCooldown();
                return;
            }
        }

        // Auto-refill Diamond Gear slot (1)
        if (handler.getSlot(1).getStack().isEmpty()) {
            int diamondGearSlot = findDiamondGearSlot();
            if (diamondGearSlot != -1 && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                clickInventorySlot(handler, diamondGearSlot);
                clickSmithingSlot(1);
                setCooldown();
                return;
            }
        }

        // Auto-refill Netherite Ingot slot (2)
        if (handler.getSlot(2).getStack().isEmpty()) {
            int ingotSlot = findInventorySlot(Items.NETHERITE_INGOT);
            if (ingotSlot != -1 && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                clickInventorySlot(handler, ingotSlot);
                clickSmithingSlot(2);
                setCooldown();
                return;
            }
        }

        switch (step) {
            case 0 -> {
                if (handler.getSlot(0).getStack().isEmpty() ||
                    !handler.getSlot(0).getStack().isOf(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)) {

                    currentInvSlot = findInventorySlot(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

                    if (currentInvSlot != -1) {
                        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                            if (clickInventorySlot(handler, currentInvSlot)) setCooldown();
                        } else {
                            step++;
                            setCooldown(cooldownDelay.get() / 2); // shorter cooldown for smoothness
                        }
                    } else {
                        setCooldown(cooldownDelay.get() * 5); // longer cooldown if no template found
                    }
                } else {
                    step++;
                }
            }
            case 1 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(0);
                    setCooldown();
                    step++;
                } else if (!handler.getSlot(0).getStack().isEmpty()) {
                    step++;
                } else step = 0;
            }
            case 2 -> {
                currentInvSlot = findDiamondGearSlot();
                if (currentInvSlot != -1 && handler.getSlot(1).getStack().isEmpty()) {
                    if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (clickInventorySlot(handler, currentInvSlot)) setCooldown();
                    } else {
                        step++;
                        setCooldown(cooldownDelay.get() / 2);
                    }
                } else if (!handler.getSlot(1).getStack().isEmpty()) {
                    step++;
                } else step = 8;
            }
            case 3 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(1);
                    setCooldown();
                    step++;
                } else if (!handler.getSlot(1).getStack().isEmpty()) {
                    step++;
                } else step = 2;
            }
            case 4 -> {
                currentInvSlot = findInventorySlot(Items.NETHERITE_INGOT);
                if (currentInvSlot != -1 && handler.getSlot(2).getStack().isEmpty()) {
                    if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        if (clickInventorySlot(handler, currentInvSlot)) setCooldown();
                    } else {
                        step++;
                        setCooldown(cooldownDelay.get() / 2);
                    }
                } else if (!handler.getSlot(2).getStack().isEmpty()) {
                    step++;
                } else step = 8;
            }
            case 5 -> {
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(2);
                    setCooldown();
                    step++;
                } else if (!handler.getSlot(2).getStack().isEmpty()) {
                    step++;
                } else step = 4;
            }
            case 6 -> {
                if (!handler.getSlot(3).getStack().isEmpty() && mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    clickSmithingSlot(3);
                    holdingOutput = true;
                    setCooldown();
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
                            setCooldown();
                            if (autoDrop.get()) dropItemFromInventory(emptySlot);
                            step = 2;
                        }
                    } else {
                        setCooldown(cooldownDelay.get() * 2); // longer cooldown if no space
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
                        setCooldown(cooldownDelay.get() * 5); // longer cooldown if nothing to upgrade
                    }
                }
            }
        }
    }

    private void setCooldown() {
        setCooldown(cooldownDelay.get());
    }

    private void setCooldown(int value) {
        cooldown = noCooldown.get() ? 0 : value;
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

    private void dropItemFromInventory(int slot) {
        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            if (mc.player.currentScreenHandler.getSlot(i).inventory == mc.player.getInventory()
                && mc.player.currentScreenHandler.getSlot(i).getIndex() == slot) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.THROW, mc.player);
                return;
            }
        }
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
