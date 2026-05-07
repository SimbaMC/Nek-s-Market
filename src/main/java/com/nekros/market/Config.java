package com.nekros.market;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue DEFAULT_LISTING_DURATION_HOURS = BUILDER
            .comment("Default listing duration in hours. 168 hours is 7 days.")
            .defineInRange("defaultListingDurationHours", 168L, 1L, 24L * 365L);

    public static final ModConfigSpec.BooleanValue LISTING_FEE_ENABLED = BUILDER
            .comment("Whether selling an item charges an up-front listing fee.")
            .define("listingFeeEnabled", false);

    public static final ModConfigSpec.LongValue LISTING_FEE_FLAT = BUILDER
            .comment("Flat Villager Coin fee charged when creating a listing.")
            .defineInRange("listingFeeFlat", 0L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue LISTING_FEE_PERCENT = BUILDER
            .comment("Percent of the listing price charged as a listing fee.")
            .defineInRange("listingFeePercent", 0, 0, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static long defaultListingDurationMillis() {
        return DEFAULT_LISTING_DURATION_HOURS.get() * 60L * 60L * 1000L;
    }

    public static long listingFee(long price) {
        if (!LISTING_FEE_ENABLED.get()) {
            return 0L;
        }
        return LISTING_FEE_FLAT.get() + price * LISTING_FEE_PERCENT.get() / 100L;
    }
}
