package com.nekros.market.system;

public record SystemOfferPricing(
        PriceMode mode,
        long basePrice,
        double multiplier,
        long minPrice,
        long maxPrice,
        String flags) {
    public static SystemOfferPricing fixed(long price) {
        return new SystemOfferPricing(PriceMode.FIXED, price, 1.0D, 0L, 0L, "");
    }
}
