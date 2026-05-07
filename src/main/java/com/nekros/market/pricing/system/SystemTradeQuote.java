package com.nekros.market.pricing.system;

public record SystemTradeQuote(
        boolean allowed,
        long totalPrice,
        long unitPricePreview,
        String message) {
    public static SystemTradeQuote fail(String message) {
        return new SystemTradeQuote(false, 0L, 0L, message);
    }

    public static SystemTradeQuote success(long totalPrice, long unitPricePreview) {
        return new SystemTradeQuote(true, totalPrice, unitPricePreview, "");
    }
}
