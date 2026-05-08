package com.nekros.market.pricing.system;

import com.nekros.market.pricing.PriceProfile;

public record SystemPriceBreakdown(
        PriceProfile profile,
        long dynamicPrice,
        long modePrice,
        long finalUnitPrice,
        String modeExplanation,
        String stockExplanation) {
}
