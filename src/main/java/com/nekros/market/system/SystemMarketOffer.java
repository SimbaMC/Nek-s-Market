package com.nekros.market.system;

import java.util.ArrayList;
import java.util.List;

import com.nekros.market.Config;
import com.nekros.market.NeksMarket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SystemMarketOffer(String id, ItemStack item, long unitPrice, Type type, String buyCategory, SystemOfferPricing pricing) {
    private static List<String> syncedBuyCategories;
    private static List<String> syncedOfferLines;
    private static List<String> syncedFallbackOfferLines;

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
        return parseUniqueOffers(offerLines());
    }

    public static List<SystemMarketOffer> allOffers() {
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

    public static List<String> fallbackOfferLines() {
        return syncedFallbackOfferLines != null ? syncedFallbackOfferLines : configOfferLines();
    }

    public static List<String> configCategoryLines() {
        return Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList();
    }

    public static List<String> configOfferLines() {
        return Config.SYSTEM_OFFERS.get().stream().map(String::valueOf).toList();
    }

    public static List<SystemMarketOffer> configOffers() {
        return parseUniqueOffers(configOfferLines());
    }

    public static List<SystemMarketOffer> allConfigOffers() {
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
        setSyncedConfig(categories, offers, offers);
    }

    public static void setSyncedConfig(List<String> categories, List<String> offers, List<String> fallbackOffers) {
        syncedBuyCategories = List.copyOf(categories);
        syncedOfferLines = uniqueOfferLines(offers);
        syncedFallbackOfferLines = List.copyOf(fallbackOffers);
    }

    public static SystemMarketOffer byId(String id) {
        for (SystemMarketOffer offer : allConfigOffers()) {
            if (offer.id.equals(id)) {
                return offer;
            }
        }
        return null;
    }

    public static SystemMarketOffer parse(String configLine) {
        String[] parts = configLine.split("\\|", -1);
        if (parts.length != 5 && parts.length != 10) {
            NeksMarket.LOGGER.warn("Invalid system market offer '{}'. Expected old id|type|item|unitPrice|category or new id|type|item|category|priceMode|basePrice|multiplier|minPrice|maxPrice|flags", configLine);
            return null;
        }

        String id = parts[0].trim();
        Type type = parseType(parts[1].trim());
        ResourceLocation itemId = ResourceLocation.tryParse(parts[2].trim());
        String category;
        SystemOfferPricing pricing;
        if (parts.length == 5) {
            long unitPrice = parseLong(parts[3].trim(), configLine);
            category = parts[4].trim();
            pricing = SystemOfferPricing.fixed(unitPrice);
        } else {
            category = parts[3].trim();
            PriceMode mode = parsePriceMode(parts[4].trim(), configLine);
            long basePrice = parseLong(parts[5].trim(), configLine);
            double multiplier = parseDouble(parts[6].trim(), configLine);
            long minPrice = parseLong(parts[7].trim(), configLine);
            long maxPrice = parseLong(parts[8].trim(), configLine);
            pricing = mode == null ? null : new SystemOfferPricing(mode, basePrice, multiplier, minPrice, maxPrice, parts[9].trim());
        }
        if (id.isEmpty() || type == null || itemId == null || pricing == null || !validPricing(pricing)) {
            NeksMarket.LOGGER.warn("Invalid system market offer '{}'.", configLine);
            return null;
        }

        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR && !itemId.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
            NeksMarket.LOGGER.warn("Unknown item '{}' in system market offer '{}'.", itemId, configLine);
            return null;
        }

        return new SystemMarketOffer(id, new ItemStack(item), pricing.basePrice(), type, category, pricing);
    }

    private static List<SystemMarketOffer> parseUniqueOffers(List<String> lines) {
        List<SystemMarketOffer> offers = new ArrayList<>();
        for (String configLine : lines) {
            SystemMarketOffer offer = parse(configLine);
            if (offer != null) {
                offers.removeIf(existing -> sameShelfItem(existing, offer));
                offers.add(offer);
            }
        }
        return List.copyOf(offers);
    }

    private static List<String> uniqueOfferLines(List<String> lines) {
        List<String> uniqueLines = new ArrayList<>();
        List<SystemMarketOffer> uniqueOffers = new ArrayList<>();
        for (String line : lines) {
            SystemMarketOffer offer = parse(line);
            if (offer != null) {
                for (int i = uniqueOffers.size() - 1; i >= 0; i--) {
                    if (sameShelfItem(uniqueOffers.get(i), offer)) {
                        uniqueOffers.remove(i);
                        uniqueLines.remove(i);
                    }
                }
                uniqueOffers.add(offer);
                uniqueLines.add(line);
            }
        }
        return List.copyOf(uniqueLines);
    }

    private static boolean sameShelfItem(SystemMarketOffer left, SystemMarketOffer right) {
        return left.type() == right.type() && left.item().is(right.item().getItem());
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

    private static PriceMode parsePriceMode(String value, String configLine) {
        try {
            return PriceMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            NeksMarket.LOGGER.warn("Invalid price mode in system market offer '{}'.", configLine);
            return null;
        }
    }

    private static long parseLong(String value, String configLine) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid numeric price field in system market offer '{}'.", configLine);
            return 0L;
        }
    }

    private static double parseDouble(String value, String configLine) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            NeksMarket.LOGGER.warn("Invalid multiplier in system market offer '{}'.", configLine);
            return 0.0D;
        }
    }

    private static boolean validPricing(SystemOfferPricing pricing) {
        return switch (pricing.mode()) {
            case FIXED -> pricing.basePrice() > 0L;
            case AUTO -> true;
            case ANCHOR -> pricing.basePrice() > 0L;
            case MULTIPLIER -> pricing.multiplier() > 0.0D;
            case BAND -> pricing.minPrice() <= 0L || pricing.maxPrice() <= 0L || pricing.minPrice() <= pricing.maxPrice();
        };
    }
}
