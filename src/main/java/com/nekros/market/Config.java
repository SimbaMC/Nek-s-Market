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

    public static final ModConfigSpec.BooleanValue MARKET_TRADE_TAX_ENABLED = BUILDER
            .comment("Whether completed player-market trades burn a percentage of the seller payout.")
            .define("marketTradeTaxEnabled", true);

    public static final ModConfigSpec.IntValue MARKET_TRADE_TAX_PERCENT = BUILDER
            .comment("Percent of completed player-market trade value removed before seller payout.")
            .defineInRange("marketTradeTaxPercent", 3, 0, 100);

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

    public static final ModConfigSpec.ConfigValue<String> PRICING_MIN_AUTOMATIC_BUY_CONFIDENCE = BUILDER
            .comment(
                    "Minimum price confidence required before automatic system buyback pays real money.",
                    "HIGH and MEDIUM are suitable for anchors, explicit natural rules, and reliable recipe prices.",
                    "LOW inferred prices remain visible as reference prices but are not automatically bought back by default.",
                    "Allowed values: HIGH, MEDIUM, LOW, NONE.")
            .define("pricing.minAutomaticBuyConfidence", "MEDIUM");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_ECONOMY_TRADE_RATIOS = BUILDER
            .comment(
                    "System buy/sell base ratios by economic tier before dynamic stock pressure.",
                    "Format: tier|buyRatio|sellRatio",
                    "buyRatio controls the initial system buy-from-player price. sellRatio controls system sell-to-player reference price.",
                    "Example: INDUSTRIAL_RENEWABLE|0.45|1.50")
            .defineListAllowEmpty("pricing.economy.tradeRatios",
                    List.of(
                            "COMMON_RENEWABLE|0.55|1.35",
                            "COMMON_NON_RENEWABLE|0.65|1.35",
                            "INDUSTRIAL_RENEWABLE|0.45|1.50",
                            "RARE_RESOURCE|0.75|1.45",
                            "BOSS_DROP|0.00|0.00",
                            "PROGRESSION_LOCKED|0.00|0.00",
                            "PLAYER_MARKET_ONLY|0.00|0.00",
                            "UNKNOWN|0.00|0.00"),
                    value -> value instanceof String);

    public static final ModConfigSpec.IntValue PRICING_MARKET_CONFIDENCE_TRADE_COUNT = BUILDER
            .comment("Recent trade count required for full player market confidence.")
            .defineInRange("pricing.marketConfidenceTradeCount", 30, 1, 10000);

    public static final ModConfigSpec.IntValue PRICING_MARKET_CONFIDENCE_PARTICIPANT_COUNT = BUILDER
            .comment("Unique market participants required for full player market confidence. This limits wash-trading impact.")
            .defineInRange("pricing.marketConfidenceParticipantCount", 4, 1, 10000);

    public static final ModConfigSpec.LongValue PRICING_MARKET_VWAP_HALF_LIFE_TICKS = BUILDER
            .comment(
                    "Half-life, in game ticks, for player-market VWAP and trade-count confidence.",
                    "Recent trades matter most; old trades gradually fade out. 72000 ticks is 3 Minecraft days.")
            .defineInRange("pricing.marketVwapHalfLifeTicks", 72000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue PRICING_MARKET_BASELINE_MIN_RATIO = BUILDER
            .comment(
                    "Minimum ratio applied when player-market VWAP is blended into an anchor/derived baseline.",
                    "Example: 0.50 means player trades cannot drag the baseline below half of recipe/anchor value.")
            .defineInRange("pricing.marketBaselineMinRatio", 0.50D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue PRICING_MARKET_BASELINE_MAX_RATIO = BUILDER
            .comment(
                    "Maximum ratio applied when player-market VWAP is blended into an anchor/derived baseline.",
                    "Example: 2.00 means player trades cannot push the baseline above double recipe/anchor value.")
            .defineInRange("pricing.marketBaselineMaxRatio", 2.00D, 0.0D, 10.0D);

    public static final ModConfigSpec.LongValue PRICING_DERIVED_PROCESSING_FEE_PER_INGREDIENT = BUILDER
            .comment("Flat processing cost added for each non-empty crafting ingredient slot when deriving recipe prices.")
            .defineInRange("pricing.derivedProcessingFeePerIngredient", 1L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue PRICING_DERIVED_PROCESSING_MARKUP = BUILDER
            .comment("Recipe processing markup applied after ingredient costs and flat processing fees. Example: 0.10 adds 10%.")
            .defineInRange("pricing.derivedProcessingMarkup", 0.10D, 0.0D, 10.0D);

    public static final ModConfigSpec.LongValue PRICING_DERIVED_COOKING_FEE = BUILDER
            .comment("Flat processing cost added to vanilla cooking recipe derivation, such as smelting, blasting, smoking, and campfire cooking.")
            .defineInRange("pricing.derivedCookingFee", 2L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_GENERIC_RECIPE_TYPE_POLICIES = BUILDER
            .comment(
                    "Policies for non-vanilla or otherwise unhandled recipe types.",
                    "Format: recipeType|tradeLevel|confidence|maxResultCount|processingFee|markup|note",
                    "recipeType examples: create:crushing, mekanism:enriching, thermal:smelter.",
                    "Use * as a default fallback. Default fallback is REFERENCE_ONLY and LOW confidence.",
                    "tradeLevel: BLOCKED, PLAYER_MARKET_ONLY, REFERENCE_ONLY, SYSTEM_BUY_ONLY, SYSTEM_BUY_AND_SELL.",
                    "confidence: HIGH, MEDIUM, LOW, NONE.",
                    "Example: *|REFERENCE_ONLY|LOW|64|1|0.10|generic safe fallback")
            .defineListAllowEmpty("pricing.genericRecipeTypePolicies",
                    List.of("*|REFERENCE_ONLY|LOW|64|1|0.10|generic safe fallback"),
                    value -> value instanceof String);

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

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_INFERRED_MATERIALS = BUILDER
            .comment(
                    "Material keyword rules used by conservative inferred reference pricing.",
                    "Format: keyword1,keyword2|basePrice|note",
                    "Keywords are matched against item ids. Inferred prices are REFERENCE_ONLY by default.",
                    "Example: iron,steel|100|iron-like material")
            .defineListAllowEmpty("pricing.inferredMaterials",
                    List.of(
                            "netherite,ancient_debris|10000|下界合金/远古残骸",
                            "diamond|1000|钻石",
                            "emerald|800|绿宝石",
                            "gold|500|金",
                            "iron,steel|100|铁/钢",
                            "copper|45|铜",
                            "coal,charcoal|20|煤炭",
                            "redstone|30|红石",
                            "lapis|35|青金石",
                            "quartz|40|石英",
                            "amethyst|80|紫水晶",
                            "tin,lead,zinc,nickel,silver|80|常见模组金属",
                            "uranium,osmium,platinum,iridium|600|稀有模组金属"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_INFERRED_SHAPES = BUILDER
            .comment(
                    "Item shape rules used by conservative inferred reference pricing.",
                    "Format: keyword|matchMode|multiplier|note",
                    "matchMode: suffix, prefix, contains.",
                    "Example: nugget|suffix|0.111111|nugget is one ninth of an ingot")
            .defineListAllowEmpty("pricing.inferredShapes",
                    List.of(
                            "nugget|suffix|0.111111|碎粒",
                            "block|suffix|9.0|块",
                            "ore|suffix|0.85|矿石",
                            "raw_ore|suffix|0.85|粗矿石",
                            "raw|prefix|0.90|粗矿",
                            "dust|suffix|0.95|粉",
                            "plate|suffix|1.15|板",
                            "gear|suffix|4.20|齿轮",
                            "rod|suffix|0.55|杆",
                            "wire|suffix|0.50|线缆",
                            "ingot|suffix|1.0|锭",
                            "gem|suffix|1.0|宝石"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_INFERRED_RARITY_PRICES = BUILDER
            .comment(
                    "Fallback reference prices by Minecraft item rarity.",
                    "Format: rarity|price",
                    "Supported vanilla rarities: COMMON, UNCOMMON, RARE, EPIC. COMMON is usually 0.",
                    "Example: RARE|800")
            .defineListAllowEmpty("pricing.inferredRarityPrices",
                    List.of(
                            "COMMON|0",
                            "UNCOMMON|120",
                            "RARE|800",
                            "EPIC|5000"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_INFERRED_TIER_MULTIPLIERS = BUILDER
            .comment(
                    "Reference price multipliers by economic tier for conservative inferred pricing.",
                    "Format: tier|multiplier",
                    "Example: RARE_RESOURCE|1.25")
            .defineListAllowEmpty("pricing.inferredTierMultipliers",
                    List.of(
                            "COMMON_RENEWABLE|0.80",
                            "COMMON_NON_RENEWABLE|1.00",
                            "INDUSTRIAL_RENEWABLE|0.70",
                            "RARE_RESOURCE|1.25",
                            "BOSS_DROP|2.00",
                            "PROGRESSION_LOCKED|1.50",
                            "PLAYER_MARKET_ONLY|1.00",
                            "UNKNOWN|0.80"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_ECONOMY_POLICIES = BUILDER
            .comment(
                    "Default economy policies by tier.",
                    "Format: tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    "S/C/delta buyback model: price = base * exp(-alpha*S^gamma - beta*C - delta*count).",
                    "Example: INDUSTRIAL_RENEWABLE|true|false|industrial|0.0008|0.3|1.2|0.001|0.004|0.00|farm output")
            .defineListAllowEmpty("pricing.economy.policies",
                    List.of(
                            "COMMON_RENEWABLE|true|false|common|0.0003|0.3|0.8|0.001|0.002|0.20|常见可再生资源，允许系统回收，衰减中等。",
                            "COMMON_NON_RENEWABLE|true|false|resource|0.00015|0.3|0.5|0.001|0.001|0.30|普通资源，允许系统回收，长期压制较慢。",
                            "INDUSTRIAL_RENEWABLE|true|false|industrial|0.0008|0.3|1.2|0.001|0.004|0.00|高自动化产物，允许系统回收，但压力足够高时会停止付款。",
                            "RARE_RESOURCE|true|false|rare|0.00005|0.3|0.25|0.0005|0.0005|0.45|稀有资源，默认只回收不自动出售。",
                            "BOSS_DROP|false|false|restricted|0|0.3|0|0.001|0|0|Boss 或唯一物品，默认交给玩家市场。",
                            "PROGRESSION_LOCKED|false|false|locked|0|0.3|0|0.001|0|0|推进关键物品，默认禁止系统自动买卖。",
                            "PLAYER_MARKET_ONLY|false|false|player_market|0|0.3|0|0.001|0|0|特殊物品，默认只允许玩家市场。",
                            "UNKNOWN|false|false|unknown|0|0.3|0|0.001|0|0|未知物品默认不自动回收，需要显式策略或白名单。"),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_AUTOMATIC_BUYBACK_ALLOW_ITEMS = BUILDER
            .comment(
                    "Explicit item whitelist for automatic system buyback.",
                    "Format: itemId",
                    "Whitelist entries can allow reference-priced items to be bought back, but blocked/no-price items still do not pay.",
                    "Deny lists always win over allow lists.")
            .defineListAllowEmpty("pricing.automaticBuyback.allowItems",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_AUTOMATIC_BUYBACK_DENY_ITEMS = BUILDER
            .comment(
                    "Explicit item blacklist for automatic system buyback.",
                    "Format: itemId",
                    "Use this to stop a dangerous item without changing its reference price.")
            .defineListAllowEmpty("pricing.automaticBuyback.denyItems",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_AUTOMATIC_BUYBACK_ALLOW_TAGS = BUILDER
            .comment(
                    "Explicit tag whitelist for automatic system buyback.",
                    "Format: tagId, without #. Example: minecraft:logs",
                    "Deny item/tag entries always win over allow tags.")
            .defineListAllowEmpty("pricing.automaticBuyback.allowTags",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_AUTOMATIC_BUYBACK_DENY_TAGS = BUILDER
            .comment(
                    "Explicit tag blacklist for automatic system buyback.",
                    "Format: tagId, without #. Example: minecraft:tools")
            .defineListAllowEmpty("pricing.automaticBuyback.denyTags",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_ECONOMY_OVERRIDES = BUILDER
            .comment(
                    "Item-specific economy policy overrides.",
                    "Format: itemId|tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    "Use this for key items such as minecraft:iron_ingot or minecraft:nether_star.")
            .defineListAllowEmpty("pricing.economy.overrides",
                    List.of(),
                    value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICING_ECONOMY_TAG_OVERRIDES = BUILDER
            .comment(
                    "Tag-specific economy policy overrides. Item-specific overrides still win.",
                    "Format: tagId|tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    "Use this for broad modded groups such as c:ores, c:ingots, forge:ores, or mod-specific tags.")
            .defineListAllowEmpty("pricing.economy.tagOverrides",
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

    public static final ModConfigSpec.DoubleValue SYSTEM_BUY_PRESSURE_SOLD_RELIEF_RATIO = BUILDER
            .comment(
                    "How much system sell-to-player volume relieves long-term buyback pressure.",
                    "0 means farms leave permanent pressure forever. 1 means sold stock fully offsets bought stock.",
                    "Default 0.35 lets real demand partially heal prices without allowing easy wash resets.")
            .defineInRange("systemMarket.buyPressureSoldReliefRatio", 0.35D, 0.0D, 1.0D);

    public static final ModConfigSpec.LongValue SYSTEM_BUY_MIN_PAYING_UNIT_PRICE = BUILDER
            .comment(
                    "Minimum pressure-adjusted unit price required for automatic system buyback.",
                    "When pressure pushes an item below this value, the system refuses buyback instead of paying a permanent 1-coin floor.")
            .defineInRange("systemMarket.buyMinPayingUnitPrice", 1L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue SYSTEM_BUY_DAILY_PAYOUT_BUDGET = BUILDER
            .comment(
                    "Daily global payout budget for all system buyback. 0 disables the global faucet cap.",
                    "This is a server-wide safety valve on top of per-item pressure curves.",
                    "When the budget is exhausted, automatic system buyback stops paying until the next Minecraft day.")
            .defineInRange("systemMarket.buyDailyPayoutBudget", 250000L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SYSTEM_BUY_PLAYER_DAILY_PAYOUT_RATIO = BUILDER
            .comment(
                    "Maximum share of the global daily buyback budget that one player can receive per Minecraft day.",
                    "Example: 0.50 means one player can drain at most 50% of the daily system buyback budget.",
                    "Only applies when systemMarket.buyDailyPayoutBudget is greater than 0.")
            .defineInRange("systemMarket.buyPlayerDailyPayoutRatio", 0.50D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_INCOME_TO_BUY_BUDGET_RATIO = BUILDER
            .comment(
                    "Share of system sell-to-player income that recharges today's system buyback budget.",
                    "Example: 0.50 means half of player spending at the system shop becomes extra buyback capacity.",
                    "This creates a currency loop without making system shop income fully reusable.")
            .defineInRange("systemMarket.sellIncomeToBuyBudgetRatio", 0.50D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_BUY_BUDGET_CURRENCY_SUPPLY_RATIO = BUILDER
            .comment(
                    "Extra daily buyback budget as a share of current recorded player currency supply.",
                    "This lets the faucet scale gently with server economy size. 0 disables supply scaling.",
                    "Example: 0.02 adds 2% of player balances and pending claims to today's buyback budget.")
            .defineInRange("systemMarket.buyBudgetCurrencySupplyRatio", 0.02D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_BUY_BUDGET_MIN_SCALE = BUILDER
            .comment("Minimum multiplier applied to the configured base daily buyback budget.")
            .defineInRange("systemMarket.buyBudgetMinScale", 0.50D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_BUY_BUDGET_MAX_SCALE = BUILDER
            .comment("Maximum multiplier applied to the configured base daily buyback budget before sell-income recharge.")
            .defineInRange("systemMarket.buyBudgetMaxScale", 3.00D, 0.0D, 100.0D);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SYSTEM_BUY_TIER_DAILY_PAYOUT_RATIOS = BUILDER
            .comment(
                    "Maximum share of the effective daily buyback budget each economic tier can consume.",
                    "Format: tier|ratio",
                    "This prevents rare resources from draining the same faucet used by common resources.",
                    "Example: RARE_RESOURCE|0.25")
            .defineListAllowEmpty("systemMarket.buyTierDailyPayoutRatios",
                    List.of(
                            "COMMON_RENEWABLE|1.00",
                            "COMMON_NON_RENEWABLE|0.80",
                            "INDUSTRIAL_RENEWABLE|0.50",
                            "RARE_RESOURCE|0.25",
                            "BOSS_DROP|0.00",
                            "PROGRESSION_LOCKED|0.00",
                            "PLAYER_MARKET_ONLY|0.00",
                            "UNKNOWN|0.25"),
                    value -> value instanceof String);

    public static final ModConfigSpec.LongValue SYSTEM_STOCK_SELL_LOW_STOCK_TARGET = BUILDER
            .comment("Stock level considered healthy for system sell-to-player offers. Below this level, non-fixed prices rise.")
            .defineInRange("systemMarket.stockSellLowStockTarget", 64L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue SYSTEM_STOCK_SELL_MAX_PRICE_BONUS_RATIO = BUILDER
            .comment("Maximum low-stock price bonus for system sell-to-player offers. Example: 0.50 means up to +50%.")
            .defineInRange("systemMarket.stockSellMaxPriceBonusRatio", 0.50D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_STOCK_ELASTICITY = BUILDER
            .comment("Elasticity used by system sell-to-player stock pricing: stockFactor = (targetStock / stock)^elasticity.")
            .defineInRange("systemMarket.sellStockElasticity", 0.35D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_STOCK_MIN_RATIO = BUILDER
            .comment("Minimum stock factor for non-fixed system sell-to-player prices.")
            .defineInRange("systemMarket.sellStockMinRatio", 0.75D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_STOCK_MAX_RATIO = BUILDER
            .comment("Maximum stock factor for non-fixed system sell-to-player prices.")
            .defineInRange("systemMarket.sellStockMaxRatio", 2.00D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_MARKET_WEIGHT = BUILDER
            .comment("How strongly recent player-market VWAP affects non-fixed system sell-to-player prices.")
            .defineInRange("systemMarket.sellMarketWeight", 0.35D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_MARKET_MIN_RATIO = BUILDER
            .comment("Minimum player-market factor for non-fixed system sell-to-player prices.")
            .defineInRange("systemMarket.sellMarketMinRatio", 0.75D, 0.0D, 10.0D);

    public static final ModConfigSpec.DoubleValue SYSTEM_SELL_MARKET_MAX_RATIO = BUILDER
            .comment("Maximum player-market factor for non-fixed system sell-to-player prices.")
            .defineInRange("systemMarket.sellMarketMaxRatio", 1.75D, 0.0D, 10.0D);

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

    public static long marketTradeTax(long totalPrice) {
        if (!MARKET_TRADE_TAX_ENABLED.get() || totalPrice <= 0L) {
            return 0L;
        }
        return totalPrice * MARKET_TRADE_TAX_PERCENT.get() / 100L;
    }
}
