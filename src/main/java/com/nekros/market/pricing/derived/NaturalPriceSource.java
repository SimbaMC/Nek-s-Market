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

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class NaturalPriceSource {
    private static final Map<ResourceLocation, NaturalRule> ITEM_RULES = new LinkedHashMap<>();
    private static final List<NaturalTagRule> TAG_RULES = new ArrayList<>();
    private static boolean loaded;

    private NaturalPriceSource() {
    }

    public static void clearCache() {
        loaded = false;
        ITEM_RULES.clear();
        TAG_RULES.clear();
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

        return PriceProfile.unknown(itemId);
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

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        ITEM_RULES.clear();
        TAG_RULES.clear();
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
