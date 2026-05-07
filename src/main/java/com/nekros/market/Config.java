package com.nekros.market;

import java.util.List;

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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SYSTEM_BUY_CATEGORIES = BUILDER
            .comment("Category tabs shown on the System Market Buy page.")
            .defineListAllowEmpty("systemMarket.buyCategories",
                    List.of("#1", "#2", "#3", "#4", "#5", "#6", "#7", "#8"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SYSTEM_OFFERS = BUILDER
            .comment(
                    "System market offers.",
                    "Format: id|type|item|unitPrice|category",
                    "type: sell_to_player for Buy page, buy_from_player for Sell page.",
                    "item: Minecraft item id, for example minecraft:stone.",
                    "category is only used by sell_to_player offers and must match systemMarket.buyCategories.",
                    "Example: buy_stone|sell_to_player|minecraft:stone|5|#1",
                    "Example: sell_wheat|buy_from_player|minecraft:wheat|2|")
            .defineListAllowEmpty("systemMarket.offers", List.of(), value -> value instanceof String);

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
