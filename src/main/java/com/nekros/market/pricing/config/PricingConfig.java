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
}
