package com.nekros.market.pricing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.nekros.market.Config;
import com.nekros.market.NeksMarket;
import com.nekros.market.pricing.derived.DerivedPriceService;
import com.nekros.market.pricing.derived.NaturalPriceSource;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PriceRegistry {
    private static final Map<ResourceLocation, PriceProfile> ANCHORS = new LinkedHashMap<>();
    private static boolean loaded;

    private PriceRegistry() {
    }

    public static void reload() {
        ANCHORS.clear();
        DerivedPriceService.clearCache();
        NaturalPriceSource.clearCache();
        EconomicPolicyRegistry.clearCache();
        for (String line : builtInAnchors()) {
            PriceProfile profile = parseAnchor(line);
            if (profile != null) {
                ANCHORS.put(profile.itemId(), profile);
            }
        }
        for (String line : Config.PRICE_ANCHORS.get().stream().map(String::valueOf).toList()) {
            PriceProfile profile = parseAnchor(line);
            if (profile != null) {
                ANCHORS.put(profile.itemId(), profile);
            }
        }
        loaded = true;
    }

    public static PriceProfile get(ResourceLocation itemId) {
        ensureLoaded();
        return ANCHORS.getOrDefault(itemId, PriceProfile.unknown(itemId));
    }

    public static PriceProfile get(ItemStack stack) {
        return get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static Set<ResourceLocation> anchorIds() {
        ensureLoaded();
        return Set.copyOf(ANCHORS.keySet());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    private static java.util.List<String> builtInAnchors() {
        return java.util.List.of(
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
                "minecraft:netherite_ingot|10000|SYSTEM_BUY_AND_SELL");
    }

    private static PriceProfile parseAnchor(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            NeksMarket.LOGGER.warn("Invalid pricing anchor '{}'. Expected: itemId|price|tradeLevel", line);
            return null;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(parts[0].trim());
        long price = parsePrice(parts[1].trim(), line);
        TradeLevel tradeLevel = parseTradeLevel(parts[2].trim(), line);
        if (itemId == null || price <= 0L || tradeLevel == null) {
            NeksMarket.LOGGER.warn("Invalid pricing anchor '{}'.", line);
            return null;
        }

        return new PriceProfile(
                itemId,
                PriceSource.ANCHOR,
                PriceConfidence.HIGH,
                tradeLevel,
                price,
                price,
                0L,
                price,
                0L,
                0L,
                "锚定价格。");
    }

    private static long parsePrice(String value, String line) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid price in pricing anchor '{}'.", line);
            return 0L;
        }
    }

    private static TradeLevel parseTradeLevel(String value, String line) {
        try {
            return TradeLevel.valueOf(value);
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid trade level in pricing anchor '{}'.", line);
            return null;
        }
    }
}
