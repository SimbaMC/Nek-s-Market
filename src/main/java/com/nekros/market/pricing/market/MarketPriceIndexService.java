package com.nekros.market.pricing.market;

import java.util.List;

import com.nekros.market.Config;
import com.nekros.market.storage.EconomySavedData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class MarketPriceIndexService {
    private MarketPriceIndexService() {
    }

    public static long recentVWAP(MinecraftServer server, ResourceLocation itemId) {
        long totalValue = 0L;
        long totalCount = 0L;
        for (MarketTradeRecord record : records(server, itemId)) {
            if (record.unitPrice() <= 0L || record.count() <= 0) {
                continue;
            }
            try {
                totalValue = Math.addExact(totalValue, Math.multiplyExact(record.unitPrice(), record.count()));
                totalCount = Math.addExact(totalCount, record.count());
            } catch (ArithmeticException exception) {
                return 0L;
            }
        }
        return totalCount > 0L ? totalValue / totalCount : 0L;
    }

    public static int recentTradeCount(MinecraftServer server, ResourceLocation itemId) {
        int count = 0;
        for (MarketTradeRecord record : records(server, itemId)) {
            if (record.unitPrice() > 0L && record.count() > 0) {
                count++;
            }
        }
        return count;
    }

    public static double confidence(MinecraftServer server, ResourceLocation itemId) {
        int requiredTrades = Config.PRICING_MARKET_CONFIDENCE_TRADE_COUNT.get();
        return Math.min(1.0D, recentTradeCount(server, itemId) / (double) requiredTrades);
    }

    private static List<MarketTradeRecord> records(MinecraftServer server, ResourceLocation itemId) {
        if (server == null || itemId == null) {
            return List.of();
        }
        return EconomySavedData.get(server).tradesFor(itemId);
    }
}
