package com.nekros.market.pricing;

import com.nekros.market.Config;
import com.nekros.market.pricing.market.MarketPriceIndexService;
import com.nekros.market.pricing.derived.DerivedPriceService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PriceResolver {
    private PriceResolver() {
    }

    public static PriceProfile resolve(MinecraftServer server, ItemStack stack) {
        return resolve(server, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static PriceProfile resolve(Level level, ItemStack stack) {
        return resolve(level, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static PriceProfile resolve(Level level, ResourceLocation itemId) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        PriceProfile derived = DerivedPriceService.resolve(level, itemId);
        return baselineProfile(itemId, anchor, derived, 0L, 0.0D);
    }

    public static PriceProfile resolve(MinecraftServer server, ResourceLocation itemId) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        PriceProfile derived = DerivedPriceService.resolve(server, itemId);
        long marketPrice = server == null ? 0L : MarketPriceIndexService.recentVWAP(server, itemId);
        double marketConfidence = server == null ? 0.0D : MarketPriceIndexService.confidence(server, itemId);
        return baselineProfile(itemId, anchor, derived, marketPrice, marketConfidence);
    }

    private static PriceProfile baselineProfile(ResourceLocation itemId, PriceProfile anchor, PriceProfile derived,
            long marketPrice, double marketConfidence) {
        boolean hasAnchor = anchor.source() != PriceSource.UNKNOWN && anchor.referencePrice() > 0L;
        boolean hasDerived = derived.source() != PriceSource.UNKNOWN && derived.referencePrice() > 0L;
        boolean hasMarket = marketPrice > 0L;
        if (!hasAnchor && !hasDerived && !hasMarket) {
            return PriceProfile.unknown(itemId);
        }

        PriceProfile baseline = hasAnchor ? anchor : derived;
        long floorPrice = hasAnchor || hasDerived ? baseline.floorPrice() : 0L;
        long derivedPrice = hasAnchor || hasDerived ? baseline.derivedPrice() : 0L;
        long referencePrice;
        PriceSource source;
        PriceConfidence confidence;
        TradeLevel tradeLevel;
        String explanation;

        if ((hasAnchor || hasDerived) && hasMarket) {
            referencePrice = Math.max(1L, Math.round(baseline.derivedPrice() * (1.0D - marketConfidence) + marketPrice * marketConfidence));
            source = PriceSource.MIXED;
            confidence = confidenceFrom(marketConfidence, true, hasAnchor);
            tradeLevel = baseline.tradeLevel();
            explanation = baseline.explanation() + " 已混合近期玩家市场成交均价。";
        } else if (hasAnchor || hasDerived) {
            referencePrice = baseline.derivedPrice();
            source = baseline.source();
            confidence = baseline.confidence();
            tradeLevel = baseline.tradeLevel();
            explanation = baseline.explanation();
        } else {
            referencePrice = marketPrice;
            source = PriceSource.PLAYER_MARKET;
            confidence = confidenceFrom(marketConfidence, false, false);
            tradeLevel = TradeLevel.PLAYER_MARKET_ONLY;
            explanation = "近期玩家市场成交均价。";
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

    private static PriceConfidence confidenceFrom(double confidence, boolean hasBaseline, boolean highBaseline) {
        if (highBaseline) {
            return PriceConfidence.HIGH;
        }
        if (confidence >= 1.0D) {
            return PriceConfidence.HIGH;
        }
        if (hasBaseline) {
            return PriceConfidence.MEDIUM;
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
