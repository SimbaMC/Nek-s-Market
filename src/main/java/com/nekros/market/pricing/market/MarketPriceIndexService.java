package com.nekros.market.pricing.market;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.nekros.market.Config;
import com.nekros.market.pricing.config.PricingConfig;
import com.nekros.market.storage.EconomySavedData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class MarketPriceIndexService {
    private MarketPriceIndexService() {
    }

    public static long recentVWAP(MinecraftServer server, ResourceLocation itemId) {
        double totalValue = 0.0D;
        double totalWeight = 0.0D;
        for (MarketTradeRecord record : records(server, itemId)) {
            if (record.unitPrice() <= 0L || record.count() <= 0) {
                continue;
            }
            double weight = timeWeight(server, record);
            totalValue += record.unitPrice() * record.count() * weight;
            totalWeight += record.count() * weight;
        }
        return totalWeight > 0.0D ? Math.max(1L, Math.round(totalValue / totalWeight)) : 0L;
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
        double tradeConfidence = Math.min(1.0D, effectiveTradeCount(server, itemId) / requiredTrades);
        double participantConfidence = Math.min(1.0D, uniqueParticipantCount(server, itemId)
                / (double) Config.PRICING_MARKET_CONFIDENCE_PARTICIPANT_COUNT.get());
        return Math.min(tradeConfidence, participantConfidence);
    }

    public static double effectiveTradeCount(MinecraftServer server, ResourceLocation itemId) {
        double count = 0.0D;
        for (MarketTradeRecord record : records(server, itemId)) {
            if (record.unitPrice() > 0L && record.count() > 0) {
                count += timeWeight(server, record);
            }
        }
        return count;
    }

    public static int uniqueParticipantCount(MinecraftServer server, ResourceLocation itemId) {
        Set<UUID> participants = new HashSet<>();
        for (MarketTradeRecord record : records(server, itemId)) {
            if (record.unitPrice() <= 0L || record.count() <= 0) {
                continue;
            }
            if (timeWeight(server, record) < 0.05D) {
                continue;
            }
            participants.add(record.buyerId());
            participants.add(record.sellerId());
        }
        return participants.size();
    }

    private static List<MarketTradeRecord> records(MinecraftServer server, ResourceLocation itemId) {
        if (server == null || itemId == null) {
            return List.of();
        }
        return EconomySavedData.get(server).tradesFor(itemId);
    }

    private static double timeWeight(MinecraftServer server, MarketTradeRecord record) {
        if (server == null || record.gameTime() <= 0L) {
            return 1.0D;
        }
        long now = server.overworld().getGameTime();
        long age = Math.max(0L, now - record.gameTime());
        long halfLife = Math.max(1L, PricingConfig.marketVwapHalfLifeTicks());
        return Math.pow(0.5D, age / (double) halfLife);
    }
}
