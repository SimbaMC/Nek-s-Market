package com.nekros.market.pricing.market;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record MarketTradeRecord(
        ResourceLocation itemId,
        UUID buyerId,
        UUID sellerId,
        long unitPrice,
        int count,
        long totalPrice,
        long gameTime,
        long realTime) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("itemId", itemId.toString());
        tag.putUUID("buyerId", buyerId);
        tag.putUUID("sellerId", sellerId);
        tag.putLong("unitPrice", unitPrice);
        tag.putInt("count", count);
        tag.putLong("totalPrice", totalPrice);
        tag.putLong("gameTime", gameTime);
        tag.putLong("realTime", realTime);
        return tag;
    }

    public static MarketTradeRecord load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("itemId"));
        if (id == null) {
            id = ResourceLocation.fromNamespaceAndPath("minecraft", "air");
        }
        return new MarketTradeRecord(
                id,
                tag.getUUID("buyerId"),
                tag.getUUID("sellerId"),
                tag.getLong("unitPrice"),
                tag.getInt("count"),
                tag.getLong("totalPrice"),
                tag.getLong("gameTime"),
                tag.getLong("realTime"));
    }
}
