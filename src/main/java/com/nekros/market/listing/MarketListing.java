package com.nekros.market.listing;

import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record MarketListing(
        UUID id,
        UUID sellerId,
        String sellerName,
        ItemStack item,
        int count,
        long price,
        long createdAt,
        long expiresAt) {
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("sellerId", sellerId);
        tag.putString("sellerName", sellerName);
        tag.put("item", item.copyWithCount(1).saveOptional(registries));
        tag.putInt("count", count);
        tag.putLong("price", price);
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        return tag;
    }

    public static MarketListing load(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack item = ItemStack.parseOptional(registries, tag.getCompound("item"));
        int count = tag.contains("count") ? tag.getInt("count") : item.getCount();
        return new MarketListing(
                tag.getUUID("id"),
                tag.getUUID("sellerId"),
                tag.getString("sellerName"),
                item.copyWithCount(1),
                count,
                tag.getLong("price"),
                tag.getLong("createdAt"),
                tag.getLong("expiresAt"));
    }

    public boolean isExpired(long now) {
        return expiresAt > 0L && now >= expiresAt;
    }

    public MarketListing withCount(int count) {
        return new MarketListing(id, sellerId, sellerName, item, count, price, createdAt, expiresAt);
    }
}
