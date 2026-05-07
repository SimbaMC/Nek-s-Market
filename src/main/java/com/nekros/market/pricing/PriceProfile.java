package com.nekros.market.pricing;

import net.minecraft.resources.ResourceLocation;

public record PriceProfile(
        ResourceLocation itemId,
        PriceSource source,
        PriceConfidence confidence,
        TradeLevel tradeLevel,
        long floorPrice,
        long derivedPrice,
        long marketPrice,
        long referencePrice,
        long systemBuyPrice,
        long systemSellPrice,
        String explanation) {
    public static PriceProfile unknown(ResourceLocation itemId) {
        return new PriceProfile(
                itemId,
                PriceSource.UNKNOWN,
                PriceConfidence.NONE,
                TradeLevel.PLAYER_MARKET_ONLY,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                "No reliable price source.");
    }
}
