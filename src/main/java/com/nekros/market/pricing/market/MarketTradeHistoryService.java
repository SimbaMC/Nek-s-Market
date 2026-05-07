package com.nekros.market.pricing.market;

import java.util.UUID;

import com.nekros.market.storage.EconomySavedData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class MarketTradeHistoryService {
    private MarketTradeHistoryService() {
    }

    public static void recordPlayerTrade(
            MinecraftServer server,
            ServerPlayer buyer,
            UUID sellerId,
            ItemStack item,
            long unitPrice,
            int count) {
        if (server == null || buyer == null || sellerId == null || item.isEmpty() || unitPrice <= 0L || count <= 0) {
            return;
        }

        long totalPrice;
        try {
            totalPrice = Math.multiplyExact(unitPrice, count);
        } catch (ArithmeticException exception) {
            return;
        }

        MarketTradeRecord record = new MarketTradeRecord(
                BuiltInRegistries.ITEM.getKey(item.getItem()),
                buyer.getUUID(),
                sellerId,
                unitPrice,
                count,
                totalPrice,
                buyer.level().getGameTime(),
                System.currentTimeMillis());
        EconomySavedData.get(server).addTrade(record);
    }
}
