package com.nekros.market.pricing.derived;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nekros.market.NeksMarket;
import com.nekros.market.pricing.PriceConfidence;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;
import com.nekros.market.pricing.config.PricingConfig;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.policy.EconomicTier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

public final class NaturalPriceSource {
    private static final Map<ResourceLocation, NaturalRule> ITEM_RULES = new LinkedHashMap<>();
    private static final List<NaturalTagRule> TAG_RULES = new ArrayList<>();
    private static final List<MaterialRule> MATERIAL_RULES = new ArrayList<>();
    private static final List<ShapeRule> SHAPE_RULES = new ArrayList<>();
    private static final Map<String, Long> RARITY_PRICES = new LinkedHashMap<>();
    private static final Map<EconomicTier, Double> TIER_MULTIPLIERS = new java.util.EnumMap<>(EconomicTier.class);
    private static boolean loaded;

    private NaturalPriceSource() {
    }

    public static void clearCache() {
        loaded = false;
        ITEM_RULES.clear();
        TAG_RULES.clear();
        MATERIAL_RULES.clear();
        SHAPE_RULES.clear();
        RARITY_PRICES.clear();
        TIER_MULTIPLIERS.clear();
    }

    public static PriceProfile resolve(ResourceLocation itemId) {
        ensureLoaded();

        NaturalRule itemRule = ITEM_RULES.get(itemId);
        if (itemRule != null) {
            return itemRule.toProfile(itemId, "天然/掉落物品规则");
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return PriceProfile.unknown(itemId);
        }
        for (NaturalTagRule tagRule : TAG_RULES) {
            if (BuiltInRegistries.ITEM.getTag(tagRule.tagKey()).stream()
                    .flatMap(tag -> tag.stream())
                    .map(Holder::value)
                    .anyMatch(item::equals)) {
                return tagRule.rule().toProfile(itemId, "天然/掉落标签规则 #" + tagRule.tagKey().location());
            }
        }

        return inferredFallback(itemId, item);
    }

    public static Set<ResourceLocation> configuredItemIds() {
        ensureLoaded();
        Set<ResourceLocation> ids = new LinkedHashSet<>(ITEM_RULES.keySet());
        for (NaturalTagRule tagRule : TAG_RULES) {
            BuiltInRegistries.ITEM.getTag(tagRule.tagKey()).ifPresent(tag -> tag.stream()
                    .map(Holder::value)
                    .filter(item -> item != Items.AIR)
                    .map(BuiltInRegistries.ITEM::getKey)
                    .forEach(ids::add));
        }
        return ids;
    }

    public static Set<ResourceLocation> inferredItemIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (inferredFallback(itemId, item).source() != PriceSource.UNKNOWN) {
                ids.add(itemId);
            }
        }
        return ids;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        ITEM_RULES.clear();
        TAG_RULES.clear();
        MATERIAL_RULES.clear();
        SHAPE_RULES.clear();
        RARITY_PRICES.clear();
        TIER_MULTIPLIERS.clear();
        for (String line : builtInItemRules()) {
            NaturalRule rule = parseRule(line, "built-in natural items");
            if (rule != null && rule.id() != null) {
                ITEM_RULES.put(rule.id(), rule);
            }
        }
        for (String line : builtInTagRules()) {
            NaturalRule rule = parseRule(line, "built-in natural tags");
            if (rule != null && rule.id() != null) {
                TAG_RULES.add(new NaturalTagRule(TagKey.create(Registries.ITEM, rule.id()), rule));
            }
        }
        for (String line : PricingConfig.naturalItems().stream().map(String::valueOf).toList()) {
            NaturalRule rule = parseRule(line, "pricing.naturalItems");
            if (rule != null && rule.id() != null) {
                ITEM_RULES.put(rule.id(), rule);
            }
        }
        for (String line : PricingConfig.naturalTags().stream().map(String::valueOf).toList()) {
            NaturalRule rule = parseRule(line, "pricing.naturalTags");
            if (rule != null && rule.id() != null) {
                TAG_RULES.add(new NaturalTagRule(TagKey.create(Registries.ITEM, rule.id()), rule));
            }
        }
        for (String line : builtInMaterialRules()) {
            MaterialRule rule = parseMaterialRule(line);
            if (rule != null) {
                MATERIAL_RULES.add(rule);
            }
        }
        for (String line : PricingConfig.inferredMaterials().stream().map(String::valueOf).toList()) {
            MaterialRule rule = parseMaterialRule(line);
            if (rule != null) {
                MATERIAL_RULES.add(rule);
            }
        }
        for (String line : builtInShapeRules()) {
            ShapeRule rule = parseShapeRule(line);
            if (rule != null) {
                SHAPE_RULES.add(rule);
            }
        }
        for (String line : PricingConfig.inferredShapes().stream().map(String::valueOf).toList()) {
            ShapeRule rule = parseShapeRule(line);
            if (rule != null) {
                SHAPE_RULES.add(rule);
            }
        }
        loadRarityPrices();
        loadTierMultipliers();
        loaded = true;
    }

    private static List<String> builtInItemRules() {
        return List.of(
                "minecraft:dirt|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础土方资源",
                "minecraft:coarse_dirt|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础土方资源",
                "minecraft:rooted_dirt|2|SYSTEM_BUY_AND_SELL|LOW|稀有土方资源",
                "minecraft:mud|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础湿地资源",
                "minecraft:clay_ball|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础湿地资源",
                "minecraft:gravel|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础矿物资源",
                "minecraft:flint|6|SYSTEM_BUY_AND_SELL|MEDIUM|沙砾副产物",
                "minecraft:sand|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础建材资源",
                "minecraft:red_sand|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础建材资源",
                "minecraft:cobblestone|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础采掘资源",
                "minecraft:stone|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础采掘资源",
                "minecraft:deepslate|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础采掘资源",
                "minecraft:cobbled_deepslate|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础采掘资源",
                "minecraft:tuff|4|SYSTEM_BUY_AND_SELL|MEDIUM|基础采掘资源",
                "minecraft:calcite|5|SYSTEM_BUY_AND_SELL|MEDIUM|洞穴装饰资源",
                "minecraft:granite|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础石材资源",
                "minecraft:diorite|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础石材资源",
                "minecraft:andesite|3|SYSTEM_BUY_AND_SELL|MEDIUM|基础石材资源",
                "minecraft:basalt|4|SYSTEM_BUY_AND_SELL|MEDIUM|下界石材资源",
                "minecraft:blackstone|5|SYSTEM_BUY_AND_SELL|MEDIUM|下界石材资源",
                "minecraft:netherrack|1|SYSTEM_BUY_AND_SELL|MEDIUM|下界基础资源",
                "minecraft:soul_sand|6|SYSTEM_BUY_AND_SELL|MEDIUM|下界资源",
                "minecraft:soul_soil|6|SYSTEM_BUY_AND_SELL|MEDIUM|下界资源",
                "minecraft:end_stone|8|SYSTEM_BUY_AND_SELL|MEDIUM|末地基础资源",
                "minecraft:snowball|1|SYSTEM_BUY_AND_SELL|MEDIUM|采集资源",
                "minecraft:ice|4|SYSTEM_BUY_AND_SELL|MEDIUM|采集资源",
                "minecraft:packed_ice|16|SYSTEM_BUY_AND_SELL|MEDIUM|压缩采集资源",
                "minecraft:blue_ice|144|SYSTEM_BUY_AND_SELL|MEDIUM|压缩采集资源",
                "minecraft:oak_log|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础木材资源",
                "minecraft:bamboo|1|SYSTEM_BUY_AND_SELL|MEDIUM|可再生植物资源",
                "minecraft:sugar_cane|3|SYSTEM_BUY_AND_SELL|MEDIUM|可再生作物",
                "minecraft:wheat|5|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:wheat_seeds|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础种子",
                "minecraft:potato|5|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:carrot|5|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:beetroot|5|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:beetroot_seeds|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础种子",
                "minecraft:melon_slice|2|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:pumpkin|8|SYSTEM_BUY_AND_SELL|MEDIUM|基础作物",
                "minecraft:cactus|3|SYSTEM_BUY_AND_SELL|MEDIUM|可再生植物资源",
                "minecraft:kelp|2|SYSTEM_BUY_AND_SELL|MEDIUM|水生植物资源",
                "minecraft:cocoa_beans|4|SYSTEM_BUY_AND_SELL|MEDIUM|作物资源",
                "minecraft:sweet_berries|3|SYSTEM_BUY_AND_SELL|MEDIUM|作物资源",
                "minecraft:glow_berries|8|SYSTEM_BUY_AND_SELL|MEDIUM|洞穴作物",
                "minecraft:apple|8|SYSTEM_BUY_AND_SELL|MEDIUM|树叶掉落",
                "minecraft:egg|3|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:feather|6|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧掉落",
                "minecraft:leather|20|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧掉落",
                "minecraft:white_wool|12|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:beef|20|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:porkchop|20|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:chicken|15|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:mutton|18|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:rabbit|18|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:cod|12|SYSTEM_BUY_AND_SELL|MEDIUM|钓鱼/生物产物",
                "minecraft:salmon|16|SYSTEM_BUY_AND_SELL|MEDIUM|钓鱼/生物产物",
                "minecraft:tropical_fish|24|SYSTEM_BUY_AND_SELL|LOW|钓鱼/生物产物",
                "minecraft:pufferfish|30|SYSTEM_BUY_AND_SELL|LOW|钓鱼/生物产物",
                "minecraft:string|8|SYSTEM_BUY_AND_SELL|MEDIUM|生物掉落",
                "minecraft:spider_eye|16|SYSTEM_BUY_ONLY|MEDIUM|生物掉落",
                "minecraft:bone|10|SYSTEM_BUY_AND_SELL|MEDIUM|生物掉落",
                "minecraft:rotten_flesh|3|SYSTEM_BUY_ONLY|MEDIUM|生物掉落",
                "minecraft:gunpowder|28|SYSTEM_BUY_AND_SELL|MEDIUM|生物掉落",
                "minecraft:slime_ball|60|SYSTEM_BUY_AND_SELL|MEDIUM|生物掉落",
                "minecraft:magma_cream|90|SYSTEM_BUY_ONLY|LOW|下界生物掉落",
                "minecraft:phantom_membrane|120|SYSTEM_BUY_ONLY|LOW|稀有生物掉落",
                "minecraft:rabbit_hide|8|SYSTEM_BUY_AND_SELL|LOW|畜牧掉落",
                "minecraft:rabbit_foot|180|SYSTEM_BUY_ONLY|LOW|稀有生物掉落",
                "minecraft:ink_sac|12|SYSTEM_BUY_AND_SELL|MEDIUM|水生生物掉落",
                "minecraft:glow_ink_sac|30|SYSTEM_BUY_AND_SELL|MEDIUM|水生生物掉落",
                "minecraft:scute|220|SYSTEM_BUY_ONLY|LOW|稀有成长产物",
                "minecraft:honeycomb|12|SYSTEM_BUY_AND_SELL|MEDIUM|养蜂产物",
                "minecraft:honey_bottle|16|SYSTEM_BUY_AND_SELL|MEDIUM|养蜂产物");
    }

    private static List<String> builtInTagRules() {
        return List.of(
                "minecraft:logs|1|SYSTEM_BUY_AND_SELL|MEDIUM|基础木材资源",
                "minecraft:wool|12|SYSTEM_BUY_AND_SELL|MEDIUM|畜牧产物",
                "minecraft:saplings|2|SYSTEM_BUY_AND_SELL|MEDIUM|树木繁殖资源",
                "minecraft:leaves|1|SYSTEM_BUY_AND_SELL|LOW|装饰采集资源",
                "minecraft:flowers|3|SYSTEM_BUY_AND_SELL|MEDIUM|装饰采集资源",
                "minecraft:small_flowers|3|SYSTEM_BUY_AND_SELL|MEDIUM|装饰采集资源",
                "minecraft:tall_flowers|6|SYSTEM_BUY_AND_SELL|MEDIUM|装饰采集资源",
                "minecraft:terracotta|8|SYSTEM_BUY_AND_SELL|MEDIUM|粘土制品资源",
                "minecraft:concrete_powder|5|SYSTEM_BUY_AND_SELL|MEDIUM|基础建材资源",
                "minecraft:concrete|6|SYSTEM_BUY_AND_SELL|MEDIUM|基础建材资源",
                "minecraft:beds|45|SYSTEM_BUY_AND_SELL|MEDIUM|木材和羊毛制品",
                "minecraft:coals|20|SYSTEM_BUY_AND_SELL|MEDIUM|燃料矿物资源");
    }

    private static List<String> builtInMaterialRules() {
        return List.of(
                "netherite,ancient_debris|10000|netherite/ancient debris",
                "diamond|1000|diamond",
                "emerald|800|emerald",
                "gold|500|gold",
                "iron,steel|100|iron/steel",
                "copper|45|copper",
                "coal,charcoal|20|coal",
                "redstone|30|redstone",
                "lapis|35|lapis",
                "quartz|40|quartz",
                "amethyst|80|amethyst",
                "tin,lead,zinc,nickel,silver,aluminum,aluminium|80|common modded metal",
                "bronze,brass,invar,electrum,constantan|160|alloy",
                "uranium,osmium,platinum,iridium,titanium,tungsten|600|rare modded metal");
    }

    private static List<String> builtInShapeRules() {
        return List.of(
                "nugget|suffix|0.111111|nugget",
                "block|suffix|9.0|storage block",
                "ore|suffix|0.85|ore",
                "raw_ore|suffix|0.85|raw ore",
                "raw|prefix|0.90|raw material",
                "dust|suffix|0.95|dust",
                "plate|suffix|1.15|plate",
                "gear|suffix|4.20|gear",
                "rod|suffix|0.55|rod",
                "wire|suffix|0.50|wire",
                "ingot|suffix|1.0|ingot",
                "gem|suffix|1.0|gem");
    }

    private static NaturalRule parseRule(String line, String configKey) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4 || parts.length > 5) {
            NeksMarket.LOGGER.warn("Invalid {} rule '{}'. Expected: id|price|tradeLevel|confidence|note", configKey, line);
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        long price = parsePrice(parts[1].trim(), line, configKey);
        TradeLevel tradeLevel = parseTradeLevel(parts[2].trim(), line, configKey);
        PriceConfidence confidence = parseConfidence(parts[3].trim(), line, configKey);
        String note = parts.length >= 5 ? parts[4].trim() : "";
        if (id == null || price <= 0L || tradeLevel == null || confidence == null) {
            NeksMarket.LOGGER.warn("Invalid {} rule '{}'.", configKey, line);
            return null;
        }

        return new NaturalRule(id, price, tradeLevel, confidence, note);
    }

    private static long parsePrice(String value, String line, String configKey) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid price in {} rule '{}'.", configKey, line);
            return 0L;
        }
    }

    private static TradeLevel parseTradeLevel(String value, String line, String configKey) {
        try {
            return TradeLevel.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid trade level in {} rule '{}'.", configKey, line);
            return null;
        }
    }

    private static PriceConfidence parseConfidence(String value, String line, String configKey) {
        try {
            return PriceConfidence.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid confidence in {} rule '{}'.", configKey, line);
            return null;
        }
    }

    private static PriceProfile inferredFallback(ResourceLocation itemId, Item item) {
        EconomicTier tier = EconomicPolicyRegistry.tierOf(itemId);
        if (blocksInferredReference(tier)) {
            return PriceProfile.unknown(itemId);
        }

        InferredValue inferred = inferredValue(itemId, item);
        Rarity rarity = new net.minecraft.world.item.ItemStack(item).getRarity();
        long rarityPrice = rarityPrice(rarity);
        long price = Math.max(inferred.price(), rarityPrice);
        if (price <= 0L) {
            return PriceProfile.unknown(itemId);
        }

        price = Math.max(1L, Math.round(price * tierReferenceMultiplier(tier)));
        String reason = inferred.reason().isBlank()
                ? "稀有度 " + rarity.getSerializedName()
                : inferred.reason() + "，稀有度 " + rarity.getSerializedName();

        return new PriceProfile(
                itemId,
                PriceSource.NATURAL,
                PriceConfidence.LOW,
                TradeLevel.REFERENCE_ONLY,
                price,
                price,
                0L,
                price,
                0L,
                0L,
                "按物品名称/稀有度/经济分级推断的保守参考价: " + reason + "，分级 " + tier + "。");
    }

    private static boolean blocksInferredReference(EconomicTier tier) {
        return tier == EconomicTier.BOSS_DROP
                || tier == EconomicTier.PROGRESSION_LOCKED
                || tier == EconomicTier.PLAYER_MARKET_ONLY;
    }

    private static InferredValue inferredValue(ResourceLocation itemId, Item item) {
        ensureLoaded();
        InferenceTerms terms = InferenceTerms.of(itemId, item);
        MaterialRule materialRule = materialRule(terms);
        long material = materialRule == null ? 0L : materialRule.basePrice();
        if (material <= 0L) {
            return new InferredValue(0L, "");
        }
        ShapeRule shape = shapeRule(terms);
        long price = Math.max(1L, Math.round(material * shape.multiplier()));
        return new InferredValue(price, shape.note() + " 形态，材料 " + materialRule.note() + " 基准 " + material);
    }

    private static MaterialRule materialRule(InferenceTerms terms) {
        for (MaterialRule rule : MATERIAL_RULES) {
            if (rule.matches(terms)) {
                return rule;
            }
        }
        return null;
    }

    private static ShapeRule shapeRule(InferenceTerms terms) {
        for (ShapeRule rule : SHAPE_RULES) {
            if (rule.matches(terms)) {
                return rule;
            }
        }
        return ShapeRule.DEFAULT;
    }

    private static long rarityPrice(Rarity rarity) {
        return RARITY_PRICES.getOrDefault(rarity.name(), defaultRarityPrice(rarity));
    }

    private static double tierReferenceMultiplier(EconomicTier tier) {
        return TIER_MULTIPLIERS.getOrDefault(tier, defaultTierReferenceMultiplier(tier));
    }

    private static void loadRarityPrices() {
        for (Rarity rarity : Rarity.values()) {
            RARITY_PRICES.put(rarity.name(), defaultRarityPrice(rarity));
        }
        for (String line : PricingConfig.inferredRarityPrices().stream().map(String::valueOf).toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 2) {
                NeksMarket.LOGGER.warn("Invalid pricing.inferredRarityPrices rule '{}'. Expected: rarity|price", line);
                continue;
            }
            String rarity = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
            long price = parsePositiveLong(parts[1], line, "pricing.inferredRarityPrices");
            RARITY_PRICES.put(rarity, price);
        }
    }

    private static void loadTierMultipliers() {
        for (EconomicTier tier : EconomicTier.values()) {
            TIER_MULTIPLIERS.put(tier, defaultTierReferenceMultiplier(tier));
        }
        for (String line : PricingConfig.inferredTierMultipliers().stream().map(String::valueOf).toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 2) {
                NeksMarket.LOGGER.warn("Invalid pricing.inferredTierMultipliers rule '{}'. Expected: tier|multiplier", line);
                continue;
            }
            EconomicTier tier = parseEconomicTier(parts[0], line);
            double multiplier = parsePositiveDouble(parts[1], line, "pricing.inferredTierMultipliers");
            if (tier != null && multiplier > 0.0D) {
                TIER_MULTIPLIERS.put(tier, multiplier);
            }
        }
    }

    private static long defaultRarityPrice(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> 120L;
            case RARE -> 800L;
            case EPIC -> 5000L;
            default -> 0L;
        };
    }

    private static double defaultTierReferenceMultiplier(EconomicTier tier) {
        return switch (tier) {
            case COMMON_RENEWABLE -> 0.80D;
            case COMMON_NON_RENEWABLE -> 1.00D;
            case INDUSTRIAL_RENEWABLE -> 0.70D;
            case RARE_RESOURCE -> 1.25D;
            case BOSS_DROP -> 2.00D;
            case PROGRESSION_LOCKED -> 1.50D;
            case PLAYER_MARKET_ONLY -> 1.00D;
            case UNKNOWN -> 0.80D;
        };
    }

    private static EconomicTier parseEconomicTier(String value, String line) {
        try {
            return EconomicTier.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid tier in pricing.inferredTierMultipliers rule '{}'.", line);
            return null;
        }
    }

    private static MaterialRule parseMaterialRule(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 2 || parts.length > 3) {
            NeksMarket.LOGGER.warn("Invalid pricing.inferredMaterials rule '{}'. Expected: keyword1,keyword2|basePrice|note", line);
            return null;
        }
        List<String> keywords = java.util.Arrays.stream(parts[0].split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .toList();
        long basePrice = parsePositiveLong(parts[1], line, "pricing.inferredMaterials");
        String note = parts.length >= 3 && !parts[2].trim().isBlank() ? parts[2].trim() : String.join("/", keywords);
        if (keywords.isEmpty() || basePrice <= 0L) {
            NeksMarket.LOGGER.warn("Invalid pricing.inferredMaterials rule '{}'.", line);
            return null;
        }
        return new MaterialRule(keywords, basePrice, note);
    }

    private static ShapeRule parseShapeRule(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 3 || parts.length > 4) {
            NeksMarket.LOGGER.warn("Invalid pricing.inferredShapes rule '{}'. Expected: keyword|matchMode|multiplier|note", line);
            return null;
        }
        String keyword = parts[0].trim().toLowerCase(java.util.Locale.ROOT);
        MatchMode mode = parseMatchMode(parts[1], line);
        double multiplier = parsePositiveDouble(parts[2], line, "pricing.inferredShapes");
        String note = parts.length >= 4 && !parts[3].trim().isBlank() ? parts[3].trim() : keyword;
        if (keyword.isBlank() || mode == null || multiplier <= 0.0D) {
            NeksMarket.LOGGER.warn("Invalid pricing.inferredShapes rule '{}'.", line);
            return null;
        }
        return new ShapeRule(keyword, mode, multiplier, note);
    }

    private static MatchMode parseMatchMode(String value, String line) {
        try {
            return MatchMode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid match mode in pricing.inferredShapes rule '{}'.", line);
            return null;
        }
    }

    private static long parsePositiveLong(String value, String line, String configKey) {
        try {
            long parsed = Long.parseLong(value.trim());
            return Math.max(0L, parsed);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid long in {} rule '{}'.", configKey, line);
            return 0L;
        }
    }

    private static double parsePositiveDouble(String value, String line, String configKey) {
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? Math.max(0.0D, parsed) : 0.0D;
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid decimal in {} rule '{}'.", configKey, line);
            return 0.0D;
        }
    }

    private record InferredValue(long price, String reason) {
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

        boolean containsKeyword(String keyword) {
            if (itemPath.contains(keyword)) {
                return true;
            }
            if (tagPaths.stream().anyMatch(path -> path.contains(keyword))) {
                return true;
            }
            return tagSegments.stream().anyMatch(segment -> segment.equals(keyword));
        }
    }

    private record MaterialRule(List<String> keywords, long basePrice, String note) {
        boolean matches(InferenceTerms terms) {
            return keywords.stream().anyMatch(terms::containsKeyword);
        }
    }

    private record ShapeRule(String keyword, MatchMode mode, double multiplier, String note) {
        static final ShapeRule DEFAULT = new ShapeRule("material", MatchMode.CONTAINS, 1.0D, "材料");

        boolean matches(InferenceTerms terms) {
            if (matchesPath(terms.itemPath())) {
                return true;
            }
            if (terms.tagPaths().stream().map(path -> path.replace('/', '_')).anyMatch(this::matchesPath)) {
                return true;
            }
            return terms.tagSegments().stream().anyMatch(this::matchesTagSegment);
        }

        private boolean matchesPath(String path) {
            String suffix = "_" + keyword;
            String prefix = keyword + "_";
            return switch (mode) {
                case SUFFIX -> path.equals(keyword) || path.endsWith(suffix) || path.contains(suffix + "_");
                case PREFIX -> path.equals(keyword) || path.startsWith(prefix) || path.contains("_" + prefix);
                case CONTAINS -> path.contains(keyword);
            };
        }

        private boolean matchesTagSegment(String segment) {
            String plural = keyword.endsWith("s") ? keyword : keyword + "s";
            return switch (mode) {
                case SUFFIX -> segment.equals(keyword)
                        || segment.equals(plural)
                        || segment.equals(keyword + "s")
                        || segment.endsWith("_" + keyword)
                        || segment.endsWith("_" + plural);
                case PREFIX -> segment.equals(keyword)
                        || segment.equals(plural)
                        || segment.equals(keyword + "s")
                        || segment.startsWith(keyword + "_")
                        || segment.startsWith(plural + "_");
                case CONTAINS -> segment.contains(keyword);
            };
        }
    }

    private enum MatchMode {
        SUFFIX,
        PREFIX,
        CONTAINS
    }

    private record NaturalRule(ResourceLocation id, long price, TradeLevel tradeLevel, PriceConfidence confidence, String note) {
        PriceProfile toProfile(ResourceLocation itemId, String sourceText) {
            String explanation = note.isBlank()
                    ? sourceText + "。"
                    : sourceText + ": " + note + "。";
            return new PriceProfile(
                    itemId,
                    PriceSource.NATURAL,
                    confidence,
                    tradeLevel,
                    price,
                    price,
                    0L,
                    price,
                    0L,
                    0L,
                    explanation);
        }
    }

    private record NaturalTagRule(TagKey<Item> tagKey, NaturalRule rule) {
    }
}
