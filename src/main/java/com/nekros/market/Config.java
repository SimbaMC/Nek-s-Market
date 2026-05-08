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
                    defaultSystemBuyCategories(),
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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICE_ANCHORS = BUILDER
            .comment(
                    "Pricing anchors.",
                    "Format: itemId|price|tradeLevel",
                    "Example: minecraft:iron_ingot|100|SYSTEM_BUY_AND_SELL")
            .defineListAllowEmpty("pricing.anchors",
                    List.of(
                            "minecraft:oak_log|1|SYSTEM_BUY_AND_SELL",
                            "minecraft:sand|2|SYSTEM_BUY_AND_SELL",
                            "minecraft:red_sand|2|SYSTEM_BUY_AND_SELL",
                            "minecraft:beef|20|SYSTEM_BUY_AND_SELL",
                            "minecraft:porkchop|20|SYSTEM_BUY_AND_SELL",
                            "minecraft:chicken|15|SYSTEM_BUY_AND_SELL",
                            "minecraft:mutton|18|SYSTEM_BUY_AND_SELL",
                            "minecraft:rabbit|18|SYSTEM_BUY_AND_SELL",
                            "minecraft:cod|12|SYSTEM_BUY_AND_SELL",
                            "minecraft:salmon|16|SYSTEM_BUY_AND_SELL",
                            "minecraft:potato|5|SYSTEM_BUY_AND_SELL",
                            "minecraft:kelp|2|SYSTEM_BUY_AND_SELL",
                            "minecraft:iron_ingot|100|SYSTEM_BUY_AND_SELL",
                            "minecraft:gold_ingot|500|SYSTEM_BUY_AND_SELL",
                            "minecraft:diamond|1000|SYSTEM_BUY_AND_SELL",
                            "minecraft:netherite_ingot|10000|SYSTEM_BUY_AND_SELL"),
                    value -> value instanceof String);

    public static final ModConfigSpec.DoubleValue PRICING_DEFAULT_BUY_RATIO = BUILDER
            .comment("Default ratio for system buying from players.")
            .defineInRange("pricing.defaultBuyRatio", 0.65D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue PRICING_DEFAULT_SELL_RATIO = BUILDER
            .comment("Default ratio for system selling to players.")
            .defineInRange("pricing.defaultSellRatio", 1.35D, 0.0D, 10.0D);

    public static final ModConfigSpec.IntValue PRICING_MARKET_CONFIDENCE_TRADE_COUNT = BUILDER
            .comment("Recent trade count required for full player market confidence.")
            .defineInRange("pricing.marketConfidenceTradeCount", 30, 1, 10000);

    public static final ModConfigSpec.LongValue PRICING_DERIVED_PROCESSING_FEE_PER_INGREDIENT = BUILDER
            .comment("Flat processing cost added for each non-empty crafting ingredient slot when deriving recipe prices.")
            .defineInRange("pricing.derivedProcessingFeePerIngredient", 1L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue PRICING_DERIVED_PROCESSING_MARKUP = BUILDER
            .comment("Recipe processing markup applied after ingredient costs and flat processing fees. Example: 0.10 adds 10%.")
            .defineInRange("pricing.derivedProcessingMarkup", 0.10D, 0.0D, 10.0D);

    public static final ModConfigSpec.LongValue PRICING_DERIVED_COOKING_FEE = BUILDER
            .comment("Flat processing cost added to vanilla cooking recipe derivation, such as smelting, blasting, smoking, and campfire cooking.")
            .defineInRange("pricing.derivedCookingFee", 2L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_NATURAL_ITEMS = BUILDER
            .comment(
                    "Natural or drop-based price rules used when an item has no anchor or recipe-derived price.",
                    "Format: itemId|price|tradeLevel|confidence|note",
                    "tradeLevel: BLOCKED, PLAYER_MARKET_ONLY, REFERENCE_ONLY, SYSTEM_BUY_ONLY, SYSTEM_BUY_AND_SELL.",
                    "confidence: HIGH, MEDIUM, LOW, NONE.",
                    "Example: minecraft:ender_pearl|240|SYSTEM_BUY_ONLY|MEDIUM|Enderman drop")
            .defineListAllowEmpty("pricing.naturalItems",
                    List.of(
                            "minecraft:coal|20|SYSTEM_BUY_AND_SELL|MEDIUM|world ore",
                            "minecraft:copper_ingot|45|SYSTEM_BUY_AND_SELL|MEDIUM|world ore",
                            "minecraft:lapis_lazuli|35|SYSTEM_BUY_AND_SELL|MEDIUM|world ore",
                            "minecraft:redstone|30|SYSTEM_BUY_AND_SELL|MEDIUM|world ore",
                            "minecraft:quartz|40|SYSTEM_BUY_AND_SELL|MEDIUM|nether ore",
                            "minecraft:emerald|800|SYSTEM_BUY_AND_SELL|MEDIUM|rare ore and villager trade",
                            "minecraft:amethyst_shard|80|SYSTEM_BUY_AND_SELL|MEDIUM|geode resource",
                            "minecraft:prismarine_shard|90|SYSTEM_BUY_ONLY|LOW|guardian drop",
                            "minecraft:prismarine_crystals|120|SYSTEM_BUY_ONLY|LOW|guardian drop",
                            "minecraft:ender_pearl|240|SYSTEM_BUY_ONLY|MEDIUM|enderman drop",
                            "minecraft:blaze_rod|320|SYSTEM_BUY_ONLY|MEDIUM|nether fortress drop",
                            "minecraft:ghast_tear|900|SYSTEM_BUY_ONLY|LOW|rare mob drop",
                            "minecraft:shulker_shell|1800|SYSTEM_BUY_ONLY|LOW|end city drop",
                            "minecraft:echo_shard|2500|SYSTEM_BUY_ONLY|LOW|ancient city loot",
                            "minecraft:nether_star|50000|PLAYER_MARKET_ONLY|LOW|boss drop"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_NATURAL_TAGS = BUILDER
            .comment(
                    "Tag-based natural price rules. Item-specific rules and anchors take priority.",
                    "Format: tagId|price|tradeLevel|confidence|note",
                    "Example: minecraft:coals|20|SYSTEM_BUY_AND_SELL|MEDIUM|fuel ore")
            .defineListAllowEmpty("pricing.naturalTags",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_BUY_PRICE_IMPACT_PER_ITEM = BUILDER
            .comment("How much each stocked item lowers the system buy-from-player price. Example: 0.002 means 100 stocked items lower buy price by 20%.")
            .defineInRange("systemMarket.stockBuyPriceImpactPerItem", 0.002D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_MIN_BUY_PRICE_RATIO = BUILDER
            .comment("Lowest stock-adjusted ratio for system buy-from-player prices.")
            .defineInRange("systemMarket.stockMinBuyPriceRatio", 0.25D, 0.0D, 1.0D);

    public static final ModConfigSpec.LongValue SYSTEM_STOCK_HEALTHY_TARGET = BUILDER
            .comment("Default healthy stock target used by the supply/demand pricing curve.")
            .defineInRange("systemMarket.stockHealthyTarget", 64L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_CURVE_STRENGTH = BUILDER
            .comment("Strength of the long-term inventory pricing curve. Higher values make stock levels affect prices more aggressively.")
            .defineInRange("systemMarket.stockCurveStrength", 0.75D, 0.0D, 10.0D);

    public static final ModConfigSpec.LongValue SYSTEM_STOCK_PRESSURE_HALF_LIFE_TICKS = BUILDER
            .comment("Half-life, in game ticks, for short-term buy/sell pressure. 24000 ticks is one Minecraft day.")
            .defineInRange("systemMarket.stockPressureHalfLifeTicks", 24000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_PRESSURE_MAX_RATIO = BUILDER
            .comment("Maximum short-term pressure adjustment applied to dynamic stock prices. Example: 0.25 means up to +/-25%.")
            .defineInRange("systemMarket.stockPressureMaxRatio", 0.25D, 0.0D, 10.0D);

    public static final ModConfigSpec.LongValue SYSTEM_STOCK_SELL_LOW_STOCK_TARGET = BUILDER
            .comment("Stock level considered healthy for system sell-to-player offers. Below this level, non-fixed prices rise.")
            .defineInRange("systemMarket.stockSellLowStockTarget", 64L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_SELL_MAX_PRICE_BONUS_RATIO = BUILDER
            .comment("Maximum low-stock price bonus for system sell-to-player offers. Example: 0.50 means up to +50%.")
            .defineInRange("systemMarket.stockSellMaxPriceBonusRatio", 0.50D, 0.0D, 10.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static List<String> defaultSystemBuyCategories() {
        return List.of("#1", "#2", "#3", "#4", "#5", "#6", "#7", "#8");
    }

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
