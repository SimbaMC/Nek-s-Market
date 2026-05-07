package com.nekros.market.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record MarketMenuSnapshot(int page, int pageCount, long balance, List<MarketMenuEntry> entries) {
    public static MarketMenuSnapshot empty() {
        return new MarketMenuSnapshot(1, 1, 0L, List.of());
    }

    public static MarketMenuSnapshot read(RegistryFriendlyByteBuf buffer) {
        int page = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        long balance = buffer.readLong();
        int count = buffer.readVarInt();
        List<MarketMenuEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(MarketMenuEntry.read(buffer));
        }
        return new MarketMenuSnapshot(page, pageCount, balance, List.copyOf(entries));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(page);
        buffer.writeVarInt(pageCount);
        buffer.writeLong(balance);
        buffer.writeVarInt(entries.size());
        for (MarketMenuEntry entry : entries) {
            entry.write(buffer);
        }
    }
}
