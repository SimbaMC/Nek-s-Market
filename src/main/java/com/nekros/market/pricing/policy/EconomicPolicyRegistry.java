package com.nekros.market.pricing.policy;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.nekros.market.NeksMarket;
import com.nekros.market.pricing.config.PricingConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class EconomicPolicyRegistry {
    private static final Map<EconomicTier, EconomicPolicy> TIER_POLICIES = new EnumMap<>(EconomicTier.class);
    private static final Map<EconomicTier, TradeRatio> TIER_TRADE_RATIOS = new EnumMap<>(EconomicTier.class);
    private static final Map<ResourceLocation, EconomicPolicy> ITEM_OVERRIDES = new HashMap<>();
    private static final Map<TagKey<Item>, EconomicPolicy> TAG_OVERRIDES = new HashMap<>();
    private static final java.util.Set<ResourceLocation> BUYBACK_ALLOW_ITEMS = new java.util.HashSet<>();
    private static final java.util.Set<ResourceLocation> BUYBACK_DENY_ITEMS = new java.util.HashSet<>();
    private static final java.util.Set<TagKey<Item>> BUYBACK_ALLOW_TAGS = new java.util.HashSet<>();
    private static final java.util.Set<TagKey<Item>> BUYBACK_DENY_TAGS = new java.util.HashSet<>();
    private static boolean loaded;

    private EconomicPolicyRegistry() {
    }

    public static void reload() {
        TIER_POLICIES.clear();
        TIER_TRADE_RATIOS.clear();
        ITEM_OVERRIDES.clear();
        TAG_OVERRIDES.clear();
        BUYBACK_ALLOW_ITEMS.clear();
        BUYBACK_DENY_ITEMS.clear();
        BUYBACK_ALLOW_TAGS.clear();
        BUYBACK_DENY_TAGS.clear();
        for (String line : PricingConfig.economyPolicies().stream().map(String::valueOf).toList()) {
            EconomicPolicy policy = parsePolicy(line, "pricing.economy.policies");
            if (policy != null) {
                TIER_POLICIES.put(policy.tier(), policy);
            }
        }
        for (String line : PricingConfig.economyTradeRatios().stream().map(String::valueOf).toList()) {
            TierTradeRatio tradeRatio = parseTierTradeRatio(line);
            if (tradeRatio != null) {
                TIER_TRADE_RATIOS.put(tradeRatio.tier(), tradeRatio.tradeRatio());
            }
        }
        for (String line : PricingConfig.economyOverrides().stream().map(String::valueOf).toList()) {
            ItemPolicy itemPolicy = parseItemPolicy(line);
            if (itemPolicy != null) {
                ITEM_OVERRIDES.put(itemPolicy.itemId(), itemPolicy.policy());
            }
        }
        for (String line : PricingConfig.economyTagOverrides().stream().map(String::valueOf).toList()) {
            TagPolicy tagPolicy = parseTagPolicy(line);
            if (tagPolicy != null) {
                TAG_OVERRIDES.put(tagPolicy.tagKey(), tagPolicy.policy());
            }
        }
        loadBuybackItems(PricingConfig.automaticBuybackAllowItems(), BUYBACK_ALLOW_ITEMS, "pricing.automaticBuyback.allowItems");
        loadBuybackItems(PricingConfig.automaticBuybackDenyItems(), BUYBACK_DENY_ITEMS, "pricing.automaticBuyback.denyItems");
        loadBuybackTags(PricingConfig.automaticBuybackAllowTags(), BUYBACK_ALLOW_TAGS, "pricing.automaticBuyback.allowTags");
        loadBuybackTags(PricingConfig.automaticBuybackDenyTags(), BUYBACK_DENY_TAGS, "pricing.automaticBuyback.denyTags");
        loaded = true;
    }

    public static void clearCache() {
        loaded = false;
        TIER_POLICIES.clear();
        TIER_TRADE_RATIOS.clear();
        ITEM_OVERRIDES.clear();
        TAG_OVERRIDES.clear();
        BUYBACK_ALLOW_ITEMS.clear();
        BUYBACK_DENY_ITEMS.clear();
        BUYBACK_ALLOW_TAGS.clear();
        BUYBACK_DENY_TAGS.clear();
    }

    public static EconomicPolicy resolve(ResourceLocation itemId) {
        ensureLoaded();
        EconomicPolicy override = ITEM_OVERRIDES.get(itemId);
        if (override != null) {
            return override;
        }
        EconomicPolicy tagOverride = tagOverride(itemId);
        if (tagOverride != null) {
            return tagOverride;
        }
        EconomicTier tier = tierOf(itemId);
        return TIER_POLICIES.getOrDefault(tier, builtInPolicyFor(tier, itemId));
    }

    public static EconomicTier tierOf(ResourceLocation itemId) {
        ensureLoaded();
        EconomicPolicy override = ITEM_OVERRIDES.get(itemId);
        if (override != null) {
            return override.tier();
        }
        EconomicPolicy tagOverride = tagOverride(itemId);
        if (tagOverride != null) {
            return tagOverride.tier();
        }
        return inferTier(itemId);
    }

    public static double buyRatio(ResourceLocation itemId) {
        ensureLoaded();
        return tradeRatioFor(tierOf(itemId)).buyRatio();
    }

    public static double sellRatio(ResourceLocation itemId) {
        ensureLoaded();
        return tradeRatioFor(tierOf(itemId)).sellRatio();
    }

    public static BuybackListDecision buybackListDecision(ResourceLocation itemId) {
        ensureLoaded();
        if (itemId == null) {
            return BuybackListDecision.NONE;
        }
        if (BUYBACK_DENY_ITEMS.contains(itemId) || matchesAnyTag(itemId, BUYBACK_DENY_TAGS)) {
            return BuybackListDecision.DENY;
        }
        if (BUYBACK_ALLOW_ITEMS.contains(itemId) || matchesAnyTag(itemId, BUYBACK_ALLOW_TAGS)) {
            return BuybackListDecision.ALLOW;
        }
        return BuybackListDecision.NONE;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    private static EconomicPolicy tagOverride(ResourceLocation itemId) {
        if (TAG_OVERRIDES.isEmpty()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return null;
        }
        for (Map.Entry<TagKey<Item>, EconomicPolicy> entry : TAG_OVERRIDES.entrySet()) {
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean matchesAnyTag(ResourceLocation itemId, java.util.Set<TagKey<Item>> tags) {
        if (tags.isEmpty()) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return false;
        }
        for (TagKey<Item> tag : tags) {
            if (BuiltInRegistries.ITEM.wrapAsHolder(item).is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static EconomicTier inferTier(ResourceLocation itemId) {
        String namespace = itemId.getNamespace();
        String path = itemId.getPath();
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return EconomicTier.UNKNOWN;
        }
        InferenceTerms terms = InferenceTerms.of(itemId, item);
        if (!"minecraft".equals(namespace)) {
            return inferModdedTier(terms);
        }
        if (isUnsafeVanillaSystemItem(path)) {
            return EconomicTier.PROGRESSION_LOCKED;
        }
        if (isBossOrUnique(path)) {
            return EconomicTier.BOSS_DROP;
        }
        if (isProgressionLocked(path)) {
            return EconomicTier.PROGRESSION_LOCKED;
        }
        if (isDurabilitySensitive(path)) {
            return EconomicTier.PLAYER_MARKET_ONLY;
        }
        if (isRareResource(path)) {
            return EconomicTier.RARE_RESOURCE;
        }
        if (isIndustrialRenewable(path) || terms.hasAnyTagPath("crops", "seeds")) {
            return EconomicTier.INDUSTRIAL_RENEWABLE;
        }
        if (isCommonNonRenewable(path) || terms.hasAnyTagSegment("ores", "ingots", "nuggets", "raw_materials", "gems")) {
            return EconomicTier.COMMON_NON_RENEWABLE;
        }
        if (isKnownRenewable(path) || hasRenewableName(path) || terms.hasAnyTagPath("logs", "saplings", "wool", "leaves")) {
            return EconomicTier.COMMON_RENEWABLE;
        }
        return EconomicTier.UNKNOWN;
    }

    private static EconomicTier inferModdedTier(InferenceTerms terms) {
        if (terms.containsAny("creative", "debug", "command", "barrier", "structure", "jigsaw",
                "spawn_egg", "spawn", "singularity", "antimatter", "ultimate", "controller")) {
            return EconomicTier.PROGRESSION_LOCKED;
        }
        if (terms.containsAny("star", "dragon", "boss")) {
            return EconomicTier.BOSS_DROP;
        }
        if (terms.containsAny("diamond", "emerald", "netherite", "ancient_debris",
                "uranium", "osmium", "platinum", "iridium", "titanium", "tungsten")) {
            return EconomicTier.RARE_RESOURCE;
        }
        if (terms.hasAnyTagSegment("ores", "ingots", "dusts", "nuggets", "gears", "plates", "gems")
                || terms.hasAnyTagPath("raw_materials", "storage_blocks")
                || terms.containsAny("ingot", "dust", "nugget", "gear", "plate", "ore", "raw",
                        "copper", "tin", "lead", "zinc", "nickel", "silver", "aluminum", "aluminium",
                        "bronze", "brass", "invar", "electrum", "constantan")) {
            return EconomicTier.COMMON_NON_RENEWABLE;
        }
        if (terms.hasAnyTagPath("crops", "seeds", "logs", "saplings", "leaves")) {
            return EconomicTier.COMMON_RENEWABLE;
        }
        return EconomicTier.PLAYER_MARKET_ONLY;
    }

    private static boolean isUnsafeVanillaSystemItem(String path) {
        return path.endsWith("_spawn_egg")
                || path.contains("command")
                || path.contains("debug")
                || path.equals("bedrock")
                || path.equals("barrier")
                || path.equals("light")
                || path.equals("structure_void")
                || path.equals("structure_block")
                || path.equals("jigsaw")
                || path.equals("knowledge_book")
                || path.equals("trial_spawner")
                || path.equals("spawner")
                || path.equals("end_portal_frame")
                || path.equals("reinforced_deepslate");
    }

    private static boolean isBossOrUnique(String path) {
        return path.equals("nether_star")
                || path.equals("dragon_egg")
                || path.equals("elytra")
                || path.equals("heart_of_the_sea");
    }

    private static boolean isProgressionLocked(String path) {
        return path.equals("trial_key")
                || path.equals("ominous_trial_key")
                || path.equals("end_portal_frame")
                || path.equals("command_block")
                || path.equals("structure_block")
                || path.equals("jigsaw")
                || path.equals("barrier")
                || path.equals("spawner")
                || path.equals("vault")
                || path.equals("heavy_core");
    }

    private static boolean isRareResource(String path) {
        return path.equals("diamond")
                || path.equals("diamond_block")
                || path.equals("emerald")
                || path.equals("emerald_block")
                || path.equals("netherite_scrap")
                || path.equals("netherite_ingot")
                || path.equals("netherite_block")
                || path.equals("ancient_debris")
                || path.equals("echo_shard")
                || path.equals("shulker_shell")
                || path.equals("totem_of_undying");
    }

    private static boolean isDurabilitySensitive(String path) {
        return path.equals("bow")
                || path.equals("crossbow")
                || path.equals("trident")
                || path.equals("shield")
                || path.equals("fishing_rod")
                || path.equals("shears")
                || path.equals("flint_and_steel")
                || path.endsWith("_sword")
                || path.endsWith("_pickaxe")
                || path.endsWith("_axe")
                || path.endsWith("_shovel")
                || path.endsWith("_hoe")
                || path.endsWith("_helmet")
                || path.endsWith("_chestplate")
                || path.endsWith("_leggings")
                || path.endsWith("_boots")
                || path.endsWith("_horse_armor");
    }

    private static boolean isIndustrialRenewable(String path) {
        return path.equals("iron_ingot")
                || path.equals("iron_nugget")
                || path.equals("poppy")
                || path.equals("bone")
                || path.equals("bone_meal")
                || path.equals("rotten_flesh")
                || path.equals("gunpowder")
                || path.equals("string")
                || path.equals("spider_eye")
                || path.equals("slime_ball")
                || path.equals("magma_cream")
                || path.equals("ender_pearl")
                || path.equals("bamboo")
                || path.equals("kelp")
                || path.equals("sugar_cane")
                || path.equals("cactus")
                || path.equals("wheat")
                || path.equals("carrot")
                || path.equals("potato")
                || path.equals("beetroot")
                || path.equals("pumpkin")
                || path.equals("melon_slice");
    }

    private static boolean isCommonNonRenewable(String path) {
        return path.equals("coal")
                || path.equals("coal_block")
                || path.equals("copper_ingot")
                || path.equals("copper_block")
                || path.equals("gold_ingot")
                || path.equals("gold_block")
                || path.equals("redstone")
                || path.equals("redstone_block")
                || path.equals("lapis_lazuli")
                || path.equals("lapis_block")
                || path.equals("quartz")
                || path.equals("amethyst_shard")
                || path.equals("amethyst_block")
                || path.endsWith("_ore")
                || path.endsWith("_raw_ore");
    }

    private static boolean isKnownRenewable(String path) {
        return path.equals("oak_log")
                || path.equals("spruce_log")
                || path.equals("birch_log")
                || path.equals("jungle_log")
                || path.equals("acacia_log")
                || path.equals("dark_oak_log")
                || path.equals("mangrove_log")
                || path.equals("cherry_log")
                || path.equals("dirt")
                || path.equals("mud")
                || path.equals("cobblestone")
                || path.equals("stone")
                || path.equals("sand")
                || path.equals("gravel")
                || path.equals("clay_ball")
                || path.equals("egg")
                || path.equals("feather")
                || path.equals("leather")
                || path.endsWith("_wool")
                || path.equals("cod")
                || path.equals("salmon")
                || path.equals("beef")
                || path.equals("porkchop")
                || path.equals("chicken")
                || path.equals("mutton")
                || path.equals("rabbit");
    }

    private static boolean hasRenewableName(String path) {
        return path.contains("sapling")
                || path.contains("leaves")
                || path.contains("flower")
                || path.contains("seeds")
                || path.contains("berries")
                || path.contains("honey")
                || path.endsWith("_log")
                || path.endsWith("_stem");
    }

    private static EconomicPolicy builtInPolicyFor(EconomicTier tier, ResourceLocation itemId) {
        return switch (tier) {
            case COMMON_RENEWABLE -> EconomicPolicy.of(tier, true, false, "common",
                    0.0003D, 0.3D, 0.8D, 0.001D, 0.002D, 0.20D,
                    "常见可再生资源，允许系统回收，衰减中等。");
            case COMMON_NON_RENEWABLE -> EconomicPolicy.of(tier, true, false, "resource",
                    0.00015D, 0.3D, 0.5D, 0.001D, 0.001D, 0.30D,
                    "普通资源，允许系统回收，长期压制较慢。");
            case INDUSTRIAL_RENEWABLE -> EconomicPolicy.of(tier, true, false, "industrial",
                    0.0008D, 0.3D, 1.2D, 0.001D, 0.004D, 0.0D,
                    "高自动化产物，允许系统回收，但压力衰减更强。");
            case RARE_RESOURCE -> EconomicPolicy.of(tier, true, false, "rare",
                    0.00005D, 0.3D, 0.25D, 0.0005D, 0.0005D, 0.45D,
                    "稀有资源，默认只回收不自动出售。");
            case BOSS_DROP -> EconomicPolicy.of(tier, false, false, "restricted",
                    0.0D, 0.3D, 0.0D, 0.001D, 0.0D, 0.0D,
                    "Boss 或唯一物品，默认交给玩家市场。");
            case PROGRESSION_LOCKED -> EconomicPolicy.of(tier, false, false, "locked",
                    0.0D, 0.3D, 0.0D, 0.001D, 0.0D, 0.0D,
                    "推进关键物品，默认禁止系统自动买卖。");
            case PLAYER_MARKET_ONLY -> EconomicPolicy.of(tier, false, false, "player_market",
                    0.0D, 0.3D, 0.0D, 0.001D, 0.0D, 0.0D,
                    "模组或特殊物品，默认只允许玩家市场。");
            case UNKNOWN -> unknownPolicy(itemId);
        };
    }

    private static EconomicPolicy unknownPolicy(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return EconomicPolicy.of(EconomicTier.UNKNOWN, false, false, "unknown",
                    0.0D, 0.3D, 0.0D, 0.001D, 0.0D, 0.0D,
                    "未知物品，默认不允许系统自动买卖。");
        }
        return EconomicPolicy.of(EconomicTier.UNKNOWN, false, false, "unknown",
                0.0D, 0.3D, 0.0D, 0.001D, 0.0D, 0.0D,
                "未匹配到明确分级，暂按保守回收策略处理。");
    }

    private static TradeRatio tradeRatioFor(EconomicTier tier) {
        return TIER_TRADE_RATIOS.getOrDefault(tier, builtInTradeRatioFor(tier));
    }

    private static TradeRatio builtInTradeRatioFor(EconomicTier tier) {
        return switch (tier) {
            case COMMON_RENEWABLE -> new TradeRatio(0.55D, 1.35D);
            case COMMON_NON_RENEWABLE -> new TradeRatio(0.65D, 1.35D);
            case INDUSTRIAL_RENEWABLE -> new TradeRatio(0.45D, 1.50D);
            case RARE_RESOURCE -> new TradeRatio(0.75D, 1.45D);
            case BOSS_DROP, PROGRESSION_LOCKED, PLAYER_MARKET_ONLY -> new TradeRatio(0.0D, 0.0D);
            case UNKNOWN -> new TradeRatio(0.0D, 0.0D);
        };
    }

    private static TierTradeRatio parseTierTradeRatio(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            NeksMarket.LOGGER.warn("Invalid pricing.economy.tradeRatios line '{}'. Expected tier|buyRatio|sellRatio", line);
            return null;
        }
        EconomicTier tier = parseTier(parts[0], line, "pricing.economy.tradeRatios");
        double buyRatio = parseDouble(parts[1], line, "pricing.economy.tradeRatios", 0.0D, 10.0D);
        double sellRatio = parseDouble(parts[2], line, "pricing.economy.tradeRatios", 0.0D, 10.0D);
        if (tier == null) {
            return null;
        }
        return new TierTradeRatio(tier, new TradeRatio(buyRatio, sellRatio));
    }

    private static EconomicPolicy parsePolicy(String line, String configKey) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 11) {
            NeksMarket.LOGGER.warn("Invalid {} line '{}'. Expected tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    configKey, line);
            return null;
        }

        EconomicTier tier = parseTier(parts[0], line, configKey);
        Boolean systemBuy = parseBoolean(parts[1], line, configKey);
        Boolean systemSell = parseBoolean(parts[2], line, configKey);
        String pressureModel = parts[3].trim();
        double alpha = parseDouble(parts[4], line, configKey, 0.0D, Double.MAX_VALUE);
        double gamma = parseDouble(parts[5], line, configKey, 0.0D, 10.0D);
        double beta = parseDouble(parts[6], line, configKey, 0.0D, Double.MAX_VALUE);
        double lambda = parseDouble(parts[7], line, configKey, 0.0D, 1.0D);
        double delta = parseDouble(parts[8], line, configKey, 0.0D, Double.MAX_VALUE);
        double minBuyRatio = parseDouble(parts[9], line, configKey, 0.0D, 1.0D);
        String note = parts[10].trim();
        if (tier == null || systemBuy == null || systemSell == null || pressureModel.isBlank()) {
            NeksMarket.LOGGER.warn("Invalid {} line '{}'.", configKey, line);
            return null;
        }
        return EconomicPolicy.of(tier, systemBuy, systemSell, pressureModel, alpha, gamma, beta, lambda, delta, minBuyRatio, note);
    }

    private static ItemPolicy parseItemPolicy(String line) {
        String[] parts = line.split("\\|", 2);
        if (parts.length != 2) {
            NeksMarket.LOGGER.warn("Invalid pricing.economy.overrides line '{}'. Expected itemId|tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    line);
            return null;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(parts[0].trim());
        EconomicPolicy policy = parsePolicy(parts[1], "pricing.economy.overrides");
        if (itemId == null || policy == null) {
            NeksMarket.LOGGER.warn("Invalid pricing.economy.overrides line '{}'.", line);
            return null;
        }
        return new ItemPolicy(itemId, policy);
    }

    private static TagPolicy parseTagPolicy(String line) {
        String[] parts = line.split("\\|", 2);
        if (parts.length != 2) {
            NeksMarket.LOGGER.warn("Invalid pricing.economy.tagOverrides line '{}'. Expected tagId|tier|systemBuy|systemSellDefault|pressureModel|alpha|gamma|beta|lambda|delta|minBuyRatio|note",
                    line);
            return null;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(parts[0].trim());
        EconomicPolicy policy = parsePolicy(parts[1], "pricing.economy.tagOverrides");
        if (tagId == null || policy == null) {
            NeksMarket.LOGGER.warn("Invalid pricing.economy.tagOverrides line '{}'.", line);
            return null;
        }
        return new TagPolicy(TagKey.create(Registries.ITEM, tagId), policy);
    }

    private static void loadBuybackItems(java.util.List<? extends String> lines, java.util.Set<ResourceLocation> target,
            String configKey) {
        for (String raw : lines.stream().map(String::valueOf).toList()) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(stripHash(line));
            if (itemId == null) {
                NeksMarket.LOGGER.warn("Invalid {} item id '{}'.", configKey, raw);
                continue;
            }
            target.add(itemId);
        }
    }

    private static void loadBuybackTags(java.util.List<? extends String> lines, java.util.Set<TagKey<Item>> target,
            String configKey) {
        for (String raw : lines.stream().map(String::valueOf).toList()) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            ResourceLocation tagId = ResourceLocation.tryParse(stripHash(line));
            if (tagId == null) {
                NeksMarket.LOGGER.warn("Invalid {} tag id '{}'.", configKey, raw);
                continue;
            }
            target.add(TagKey.create(Registries.ITEM, tagId));
        }
    }

    private static String stripHash(String value) {
        return value.startsWith("#") ? value.substring(1) : value;
    }

    private static EconomicTier parseTier(String value, String line, String configKey) {
        try {
            return EconomicTier.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid tier in {} line '{}'.", configKey, line);
            return null;
        }
    }

    private static Boolean parseBoolean(String value, String line, String configKey) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized)) {
            return Boolean.FALSE;
        }
        NeksMarket.LOGGER.warn("Invalid boolean in {} line '{}'.", configKey, line);
        return null;
    }

    private static double parseDouble(String value, String line, String configKey, double min, double max) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
                NeksMarket.LOGGER.warn("Out-of-range number in {} line '{}'.", configKey, line);
                return min;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid number in {} line '{}'.", configKey, line);
            return min;
        }
    }

    private record InferenceTerms(String itemPath, List<String> tagPaths, List<String> tagSegments) {
        static InferenceTerms of(ResourceLocation itemId, Item item) {
            List<String> tagPaths = BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
                    .map(tag -> tag.location().getPath().toLowerCase(java.util.Locale.ROOT))
                    .toList();
            List<String> tagSegments = tagPaths.stream()
                    .flatMap(path -> java.util.Arrays.stream(path.split("[/_]")))
                    .filter(segment -> !segment.isBlank())
                    .toList();
            return new InferenceTerms(itemId.getPath().toLowerCase(java.util.Locale.ROOT), tagPaths, tagSegments);
        }

        boolean containsAny(String... keywords) {
            for (String keyword : keywords) {
                String normalized = keyword.toLowerCase(java.util.Locale.ROOT);
                if (itemPath.contains(normalized)
                        || tagPaths.stream().anyMatch(path -> path.contains(normalized))
                        || tagSegments.stream().anyMatch(segment -> segment.equals(normalized))) {
                    return true;
                }
            }
            return false;
        }

        boolean hasAnyTagPath(String... paths) {
            for (String path : paths) {
                String normalized = path.toLowerCase(java.util.Locale.ROOT);
                if (tagPaths.stream().anyMatch(tagPath -> tagPath.equals(normalized)
                        || tagPath.startsWith(normalized + "/")
                        || tagPath.endsWith("/" + normalized))) {
                    return true;
                }
            }
            return false;
        }

        boolean hasAnyTagSegment(String... segments) {
            for (String segment : segments) {
                String normalized = segment.toLowerCase(java.util.Locale.ROOT);
                if (tagSegments.stream().anyMatch(value -> value.equals(normalized))) {
                    return true;
                }
            }
            return false;
        }
    }

    private record ItemPolicy(ResourceLocation itemId, EconomicPolicy policy) {
    }

    private record TagPolicy(TagKey<Item> tagKey, EconomicPolicy policy) {
    }

    private record TradeRatio(double buyRatio, double sellRatio) {
    }

    private record TierTradeRatio(EconomicTier tier, TradeRatio tradeRatio) {
    }

    public enum BuybackListDecision {
        NONE,
        ALLOW,
        DENY
    }
}
