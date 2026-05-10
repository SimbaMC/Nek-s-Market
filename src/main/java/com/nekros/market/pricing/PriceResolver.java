package com.nekros.market.pricing;

import com.nekros.market.pricing.config.PricingConfig;
import com.nekros.market.pricing.derived.DerivedPriceService;
import com.nekros.market.pricing.market.MarketPriceIndexService;
import com.nekros.market.pricing.policy.EconomicPolicy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry.BuybackListDecision;
import com.nekros.market.pricing.policy.EconomicTier;

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
            long boundedMarketPrice = boundedMarketPrice(baseline.derivedPrice(), marketPrice);
            referencePrice = Math.max(1L, Math.round(baseline.derivedPrice() * (1.0D - marketConfidence)
                    + boundedMarketPrice * marketConfidence));
            source = PriceSource.MIXED;
            confidence = confidenceFrom(marketConfidence, true, hasAnchor);
            tradeLevel = baseline.tradeLevel();
            explanation = mixedExplanation(baseline, marketPrice, boundedMarketPrice, marketConfidence);
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
            explanation = "近期玩家交易成交均价。";
        }

        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        long systemBuyPrice = allowsAutomaticBuyPrice(itemId, policy, tradeLevel, confidence)
                ? Math.max(1L, (long) Math.floor(referencePrice * EconomicPolicyRegistry.buyRatio(itemId)))
                : 0L;
        long systemSellPrice = allowsSystemSell(tradeLevel)
                ? Math.max(1L, (long) Math.ceil(referencePrice * EconomicPolicyRegistry.sellRatio(itemId)))
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

    public static boolean allowsAutomaticBuyConfidence(PriceConfidence confidence) {
        return confidenceRank(confidence) >= confidenceRank(minAutomaticBuyConfidence());
    }

    public static boolean allowsAutomaticBuyPrice(ResourceLocation itemId, EconomicPolicy policy,
            TradeLevel tradeLevel, PriceConfidence confidence) {
        if (itemId == null || policy == null || tradeLevel == null || !allowsAutomaticBuyConfidence(confidence)) {
            return false;
        }
        BuybackListDecision listDecision = EconomicPolicyRegistry.buybackListDecision(itemId);
        if (listDecision == BuybackListDecision.DENY || tradeLevel == TradeLevel.BLOCKED) {
            return false;
        }
        if (listDecision == BuybackListDecision.ALLOW) {
            return tradeLevel != TradeLevel.PLAYER_MARKET_ONLY;
        }
        return policy.tier() != EconomicTier.UNKNOWN
                && policy.systemBuyAllowed()
                && allowsSystemBuy(tradeLevel);
    }

    private static PriceConfidence minAutomaticBuyConfidence() {
        try {
            return PriceConfidence.valueOf(PricingConfig.minAutomaticBuyConfidence()
                    .trim()
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return PriceConfidence.MEDIUM;
        }
    }

    private static int confidenceRank(PriceConfidence confidence) {
        if (confidence == null) {
            return 0;
        }
        return switch (confidence) {
            case NONE -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    private static long boundedMarketPrice(long baselinePrice, long marketPrice) {
        if (baselinePrice <= 0L || marketPrice <= 0L) {
            return marketPrice;
        }
        double minRatio = Math.max(0.0D, PricingConfig.marketBaselineMinRatio());
        double maxRatio = Math.max(minRatio, PricingConfig.marketBaselineMaxRatio());
        long min = Math.max(1L, (long) Math.floor(baselinePrice * minRatio));
        long max = Math.max(min, (long) Math.ceil(baselinePrice * maxRatio));
        return Math.max(min, Math.min(max, marketPrice));
    }

    private static String mixedExplanation(PriceProfile baseline, long marketPrice, long boundedMarketPrice,
            double marketConfidence) {
        return baseline.explanation()
                + " 已混合近期玩家交易均价，VWAP=" + marketPrice
                + "，边界后=" + boundedMarketPrice
                + "，置信=" + String.format(java.util.Locale.ROOT, "%.2f", marketConfidence)
                + "。";
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
