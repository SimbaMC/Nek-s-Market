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

    public static String minAutomaticBuyConfidence() {
        return Config.PRICING_MIN_AUTOMATIC_BUY_CONFIDENCE.get();
    }

    public static java.util.List<? extends String> economyTradeRatios() {
        return Config.PRICING_ECONOMY_TRADE_RATIOS.get();
    }

    public static int marketConfidenceTradeCount() {
        return Config.PRICING_MARKET_CONFIDENCE_TRADE_COUNT.get();
    }

    public static double marketBaselineMinRatio() {
        return Config.PRICING_MARKET_BASELINE_MIN_RATIO.get();
    }

    public static double marketBaselineMaxRatio() {
        return Config.PRICING_MARKET_BASELINE_MAX_RATIO.get();
    }

    public static long marketVwapHalfLifeTicks() {
        return Config.PRICING_MARKET_VWAP_HALF_LIFE_TICKS.get();
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

    public static java.util.List<? extends String> genericRecipeTypePolicies() {
        return Config.PRICING_GENERIC_RECIPE_TYPE_POLICIES.get();
    }

    public static java.util.List<? extends String> naturalItems() {
        return Config.PRICING_NATURAL_ITEMS.get();
    }

    public static java.util.List<? extends String> naturalTags() {
        return Config.PRICING_NATURAL_TAGS.get();
    }

    public static java.util.List<? extends String> inferredMaterials() {
        return Config.PRICING_INFERRED_MATERIALS.get();
    }

    public static java.util.List<? extends String> inferredShapes() {
        return Config.PRICING_INFERRED_SHAPES.get();
    }

    public static java.util.List<? extends String> inferredRarityPrices() {
        return Config.PRICING_INFERRED_RARITY_PRICES.get();
    }

    public static java.util.List<? extends String> inferredTierMultipliers() {
        return Config.PRICING_INFERRED_TIER_MULTIPLIERS.get();
    }

    public static java.util.List<? extends String> economyPolicies() {
        return Config.PRICING_ECONOMY_POLICIES.get();
    }

    public static java.util.List<? extends String> economyOverrides() {
        return Config.PRICING_ECONOMY_OVERRIDES.get();
    }

    public static java.util.List<? extends String> economyTagOverrides() {
        return Config.PRICING_ECONOMY_TAG_OVERRIDES.get();
    }

    public static java.util.List<? extends String> automaticBuybackAllowItems() {
        return Config.PRICING_AUTOMATIC_BUYBACK_ALLOW_ITEMS.get();
    }

    public static java.util.List<? extends String> automaticBuybackDenyItems() {
        return Config.PRICING_AUTOMATIC_BUYBACK_DENY_ITEMS.get();
    }

    public static java.util.List<? extends String> automaticBuybackAllowTags() {
        return Config.PRICING_AUTOMATIC_BUYBACK_ALLOW_TAGS.get();
    }

    public static java.util.List<? extends String> automaticBuybackDenyTags() {
        return Config.PRICING_AUTOMATIC_BUYBACK_DENY_TAGS.get();
    }
}
