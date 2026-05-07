package com.nekros.market.pricing;

import java.util.LinkedHashMap;
import java.util.Map;

import com.nekros.market.Config;
import com.nekros.market.NeksMarket;

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

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
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
                "Anchor price.");
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
