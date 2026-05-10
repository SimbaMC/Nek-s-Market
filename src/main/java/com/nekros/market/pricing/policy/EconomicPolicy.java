package com.nekros.market.pricing.policy;

public record EconomicPolicy(
        EconomicTier tier,
        boolean systemBuyAllowed,
        boolean systemSellAllowedByDefault,
        String pressureModel,
        double longAlpha,
        double gamma,
        double memoryBeta,
        double memoryLambda,
        double tradeDelta,
        double minBuyRatio,
        String explanation) {
    public static EconomicPolicy of(EconomicTier tier, boolean systemBuyAllowed, boolean systemSellAllowedByDefault,
            String pressureModel, double longAlpha, double gamma, double memoryBeta, double memoryLambda,
            double tradeDelta, double minBuyRatio, String explanation) {
        return new EconomicPolicy(tier, systemBuyAllowed, systemSellAllowedByDefault, pressureModel,
                longAlpha, gamma, memoryBeta, memoryLambda, tradeDelta, minBuyRatio, explanation);
    }
}
