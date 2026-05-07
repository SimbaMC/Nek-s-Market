package com.nekros.market.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static boolean canFit(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        int remaining = stack.getCount();
        for (ItemStack slotStack : inventory.items) {
            if (slotStack.isEmpty()) {
                remaining -= stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slotStack, stack)) {
                remaining -= slotStack.getMaxStackSize() - slotStack.getCount();
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean canFit(Inventory inventory, ItemStack template, int count) {
        if (template.isEmpty() || count <= 0) {
            return true;
        }

        int remaining = count;
        int maxStackSize = template.getMaxStackSize();
        for (ItemStack slotStack : inventory.items) {
            if (slotStack.isEmpty()) {
                remaining -= maxStackSize;
            } else if (ItemStack.isSameItemSameComponents(slotStack, template)) {
                remaining -= slotStack.getMaxStackSize() - slotStack.getCount();
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public static int countMatching(Inventory inventory, ItemStack template) {
        if (template.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (ItemStack slotStack : inventory.items) {
            if (matchesTemplate(slotStack, template)) {
                count += slotStack.getCount();
            }
        }
        return count;
    }

    public static boolean removeMatching(Inventory inventory, ItemStack template, int amount) {
        if (template.isEmpty() || amount <= 0) {
            return false;
        }

        int remaining = amount;
        for (ItemStack slotStack : inventory.items) {
            if (!matchesTemplate(slotStack, template)) {
                continue;
            }

            int taken = Math.min(remaining, slotStack.getCount());
            slotStack.shrink(taken);
            remaining -= taken;
            if (remaining <= 0) {
                break;
            }
        }

        return remaining == 0;
    }

    public static boolean matchesTemplate(ItemStack stack, ItemStack template) {
        return !stack.isEmpty() && stack.is(template.getItem());
    }

    public static void addSplit(Inventory inventory, ItemStack template, int count) {
        for (ItemStack stack : split(template, count)) {
            inventory.add(stack);
        }
    }

    public static List<ItemStack> split(ItemStack template, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = count;
        int maxStackSize = template.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStackSize);
            stacks.add(template.copyWithCount(chunk));
            remaining -= chunk;
        }
        return stacks;
    }
}
