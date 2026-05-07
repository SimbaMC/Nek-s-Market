package com.nekros.market.menu;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record MarketMenuEntry(UUID id, String sellerName, ItemStack item, int count, long price, long expiresAt) {
    public static MarketMenuEntry read(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String sellerName = buffer.readUtf(64);
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        long price = buffer.readLong();
        long expiresAt = buffer.readLong();
        return new MarketMenuEntry(id, sellerName, item, count, price, expiresAt);
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(sellerName, 64);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, item);
        buffer.writeVarInt(count);
        buffer.writeLong(price);
        buffer.writeLong(expiresAt);
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
