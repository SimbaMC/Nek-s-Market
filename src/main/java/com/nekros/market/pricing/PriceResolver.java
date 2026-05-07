package com.nekros.market.pricing;

import com.nekros.market.Config;
import com.nekros.market.pricing.market.MarketPriceIndexService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class PriceResolver {
    private PriceResolver() {
    }

    public static PriceProfile resolve(MinecraftServer server, ItemStack stack) {
        return resolve(server, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static PriceProfile resolve(MinecraftServer server, ResourceLocation itemId) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        long marketPrice = server == null ? 0L : MarketPriceIndexService.recentVWAP(server, itemId);
        double marketConfidence = server == null ? 0.0D : MarketPriceIndexService.confidence(server, itemId);

        boolean hasAnchor = anchor.source() != PriceSource.UNKNOWN && anchor.referencePrice() > 0L;
        boolean hasMarket = marketPrice > 0L;
        if (!hasAnchor && !hasMarket) {
            return PriceProfile.unknown(itemId);
        }

        long floorPrice = hasAnchor ? anchor.floorPrice() : 0L;
        long derivedPrice = hasAnchor ? anchor.derivedPrice() : 0L;
        long referencePrice;
        PriceSource source;
        PriceConfidence confidence;
        TradeLevel tradeLevel;
        String explanation;

        if (hasAnchor && hasMarket) {
            referencePrice = Math.max(1L, Math.round(anchor.derivedPrice() * (1.0D - marketConfidence) + marketPrice * marketConfidence));
            source = PriceSource.MIXED;
            confidence = confidenceFrom(marketConfidence, true);
            tradeLevel = anchor.tradeLevel();
            explanation = "Anchor price blended with recent player-market VWAP.";
        } else if (hasAnchor) {
            referencePrice = anchor.derivedPrice();
            source = PriceSource.ANCHOR;
            confidence = PriceConfidence.HIGH;
            tradeLevel = anchor.tradeLevel();
            explanation = anchor.explanation();
        } else {
            referencePrice = marketPrice;
            source = PriceSource.PLAYER_MARKET;
            confidence = confidenceFrom(marketConfidence, false);
            tradeLevel = TradeLevel.PLAYER_MARKET_ONLY;
            explanation = "Recent player-market VWAP.";
        }

        long systemBuyPrice = allowsSystemBuy(tradeLevel)
                ? Math.max(1L, (long) Math.floor(referencePrice * Config.PRICING_DEFAULT_BUY_RATIO.get()))
                : 0L;
        long systemSellPrice = allowsSystemSell(tradeLevel)
                ? Math.max(1L, (long) Math.ceil(referencePrice * Config.PRICING_DEFAULT_SELL_RATIO.get()))
                : 0L;

        return new PriceProfile(
                itemId,
                source,
                confidence,
                tradeLevel,
                floorPrice,
                derivedPrice,
                marketPrice,
                referencePrice,
                systemBuyPrice,
                systemSellPrice,
                explanation);
    }

    public static boolean allowsSystemBuy(TradeLevel tradeLevel) {
        return tradeLevel == TradeLevel.SYSTEM_BUY_ONLY || tradeLevel == TradeLevel.SYSTEM_BUY_AND_SELL;
    }

    public static boolean allowsSystemSell(TradeLevel tradeLevel) {
        return tradeLevel == TradeLevel.SYSTEM_BUY_AND_SELL;
    }

    private static PriceConfidence confidenceFrom(double confidence, boolean hasAnchor) {
        if (hasAnchor || confidence >= 1.0D) {
            return PriceConfidence.HIGH;
        }
        if (confidence >= 0.5D) {
            return PriceConfidence.MEDIUM;
        }
        if (confidence > 0.0D) {
            return PriceConfidence.LOW;
        }
        return PriceConfidence.NONE;
    }
}
