package com.nekros.market.claim;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class MarketClaim {
    private long money;
    private final List<ItemStack> items = new ArrayList<>();

    public long money() {
        return money;
    }

    public void addMoney(long amount) {
        money = Math.addExact(money, amount);
    }

    public long takeMoney() {
        long claimed = money;
        money = 0L;
        return claimed;
    }

    public List<ItemStack> items() {
        return items;
    }

    public boolean isEmpty() {
        return money <= 0L && items.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("money", money);

        ListTag itemList = new ListTag();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                itemList.add(stack.saveOptional(registries));
            }
        }
        tag.put("items", itemList);
        return tag;
    }

    public static MarketClaim load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketClaim claim = new MarketClaim();
        claim.money = tag.getLong("money");

        ListTag itemList = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemList.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, itemList.getCompound(i));
            if (!stack.isEmpty()) {
                claim.items.add(stack);
            }
        }
        return claim;
    }
}
