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

    public boolean hasFlag(String flag) {
        if (flag == null || flag.isBlank() || flags == null || flags.isBlank()) {
            return false;
        }
        String needle = normalizeFlag(flag);
        for (String value : flags.split("[,;\\s]+")) {
            if (normalizeFlag(value).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    public boolean infiniteStock() {
        return hasFlag("infinite_stock") || hasFlag("infinite");
    }

    private static String normalizeFlag(String value) {
        return value.trim().toLowerCase();
    }
}
