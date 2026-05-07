package com.nekros.market.system;

import java.util.ArrayList;
import java.util.List;

import com.nekros.market.Config;
import com.nekros.market.NeksMarket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SystemMarketOffer(String id, ItemStack item, long unitPrice, Type type, String buyCategory) {
    private static List<String> syncedBuyCategories;
    private static List<String> syncedOfferLines;

    public enum Type {
        SYSTEM_SELLS,
        SYSTEM_BUYS
    }

    public static List<String> buyCategories() {
        return categoryLines().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public static List<SystemMarketOffer> offers() {
        List<SystemMarketOffer> offers = new ArrayList<>();
        for (String configLine : offerLines()) {
            SystemMarketOffer offer = parse(configLine);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return List.copyOf(offers);
    }

    public static List<String> categoryLines() {
        return syncedBuyCategories != null ? syncedBuyCategories : Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList();
    }

    public static List<String> offerLines() {
        return syncedOfferLines != null ? syncedOfferLines : Config.SYSTEM_OFFERS.get().stream().map(String::valueOf).toList();
    }

    public static List<String> configCategoryLines() {
        return Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList();
    }

    public static List<String> configOfferLines() {
        return Config.SYSTEM_OFFERS.get().stream().map(String::valueOf).toList();
    }

    public static List<SystemMarketOffer> configOffers() {
        List<SystemMarketOffer> offers = new ArrayList<>();
        for (String configLine : configOfferLines()) {
            SystemMarketOffer offer = parse(configLine);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return List.copyOf(offers);
    }

    public static void setSyncedConfig(List<String> categories, List<String> offers) {
        syncedBuyCategories = List.copyOf(categories);
        syncedOfferLines = List.copyOf(offers);
    }

    public static SystemMarketOffer byId(String id) {
        for (SystemMarketOffer offer : configOffers()) {
            if (offer.id.equals(id)) {
                return offer;
            }
        }
        return null;
    }

    public static SystemMarketOffer parse(String configLine) {
        String[] parts = configLine.split("\\|", -1);
        if (parts.length != 5) {
            NeksMarket.LOGGER.warn("Invalid system market offer '{}'. Expected: id|type|item|unitPrice|category", configLine);
            return null;
        }

        String id = parts[0].trim();
        Type type = parseType(parts[1].trim());
        ResourceLocation itemId = ResourceLocation.tryParse(parts[2].trim());
        long unitPrice = parsePrice(parts[3].trim(), configLine);
        String category = parts[4].trim();
        if (id.isEmpty() || type == null || itemId == null || unitPrice <= 0L) {
            NeksMarket.LOGGER.warn("Invalid system market offer '{}'.", configLine);
            return null;
        }

        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR && !itemId.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
            NeksMarket.LOGGER.warn("Unknown item '{}' in system market offer '{}'.", itemId, configLine);
            return null;
        }

        return new SystemMarketOffer(id, new ItemStack(item), unitPrice, type, category);
    }

    private static Type parseType(String value) {
        if ("sell_to_player".equalsIgnoreCase(value) || "system_sells".equalsIgnoreCase(value)) {
            return Type.SYSTEM_SELLS;
        }
        if ("buy_from_player".equalsIgnoreCase(value) || "system_buys".equalsIgnoreCase(value)) {
            return Type.SYSTEM_BUYS;
        }
        return null;
    }

    private static long parsePrice(String value, String configLine) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid price in system market offer '{}'.", configLine);
            return 0L;
        }
    }
}
