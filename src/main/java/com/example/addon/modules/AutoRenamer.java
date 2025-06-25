package com.example.addon.modules;

import java.util.List;

import static com.example.addon.AddonTemplate.CATEGORY;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class AutoRenamer extends Module {

    private final Setting<String> renameText = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("rename-text")
        .description("What to rename items to.")
        .defaultValue("Renamed")
        .build()
    );

    private final Setting<List<Item>> whitelist = settings.getDefaultGroup().add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("Items to rename.")
        .build()
    );

    private final Setting<Boolean> autoDrop = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("auto-drop")
        .description("Drops renamed items.")
        .defaultValue(false)
        .build()
    );

    public AutoRenamer() {
        super(CATEGORY, "auto-renamer", "Automatically renames whitelisted items in an anvil.");
    }

    private int cooldown = 0;
    private boolean sentRename = false;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!(mc.currentScreen instanceof AnvilScreen)) return;
        if (!(mc.player.currentScreenHandler instanceof AnvilScreenHandler handler)) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // STEP 0: Clear cursor if it's holding something
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            // Try placing it in inventory or dropping it
            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.PICKUP, mc.player);
            cooldown = 2;
            return;
        }

        ItemStack input = handler.getSlot(0).getStack();
        ItemStack output = handler.getSlot(2).getStack();

        // STEP 1: Place whitelisted item into anvil
        if (input.isEmpty()) {
            int slot = findWhitelistedItemSlot();
            if (slot != -1) {
                clickInventorySlot(handler, slot); // Pick it up
                clickAnvilSlot(0); // Place in slot 0
                cooldown = 2;
                sentRename = false;
                return;
            }
            return; // Nothing left to rename
        }

        // STEP 2: Send rename packet only once
        if (!input.getName().getString().equals(renameText.get()) && !sentRename) {
            mc.player.networkHandler.sendPacket(new RenameItemC2SPacket(renameText.get()));
            sentRename = true;
            cooldown = 2;
            return;
        }

        // STEP 3: Wait for output to be ready, then click to pick it up
        if (!output.isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, 2, 0, SlotActionType.PICKUP, mc.player);
            cooldown = 3;
            return;
        }

        // STEP 4: If holding renamed item, optionally drop it
        if (autoDrop.get() && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, -999, 0, SlotActionType.THROW, mc.player);
            cooldown = 2;
        }

        // STEP 5: Reset after rename is done
        if (handler.getSlot(0).getStack().isEmpty() && handler.getSlot(2).getStack().isEmpty()) {
            sentRename = false;
        }
    }

    private int findWhitelistedItemSlot() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && whitelist.get().contains(stack.getItem())) return i;
        }
        return -1;
    }

    private void clickInventorySlot(AnvilScreenHandler handler, int invSlot) {
        for (int i = 0; i < handler.slots.size(); i++) {
            if (handler.getSlot(i).inventory == mc.player.getInventory()
                && handler.getSlot(i).getIndex() == invSlot) {
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
    }

    private void clickAnvilSlot(int slotId) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 0, SlotActionType.PICKUP, mc.player);
    }
}
