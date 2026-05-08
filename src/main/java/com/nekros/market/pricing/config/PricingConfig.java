package com.nekros.market.pricing.config;

import com.nekros.market.Config;

public final class PricingConfig {
    private PricingConfig() {
    }

    public static double defaultBuyRatio() {
        return Config.PRICING_DEFAULT_BUY_RATIO.get();
    }

    public static double defaultSellRatio() {
        return Config.PRICING_DEFAULT_SELL_RATIO.get();
    }

    public static int marketConfidenceTradeCount() {
        return Config.PRICING_MARKET_CONFIDENCE_TRADE_COUNT.get();
    }

    public static long derivedProcessingFeePerIngredient() {
        return Config.PRICING_DERIVED_PROCESSING_FEE_PER_INGREDIENT.get();
    }

    public static double derivedProcessingMarkup() {
        return Config.PRICING_DERIVED_PROCESSING_MARKUP.get();
    }

    public static long derivedCookingFee() {
        return Config.PRICING_DERIVED_COOKING_FEE.get();
    }

    public static java.util.List<? extends String> naturalItems() {
        return Config.PRICING_NATURAL_ITEMS.get();
    }

    public static java.util.List<? extends String> naturalTags() {
        return Config.PRICING_NATURAL_TAGS.get();
    }
}
