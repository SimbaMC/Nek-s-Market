package com.nekros.market.pricing.system;

import com.nekros.market.Config;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.system.PriceMode;
import com.nekros.market.system.SystemOfferPricing;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.storage.SystemStockSavedData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SystemPriceService {
    private SystemPriceService() {
    }

    public static SystemTradeQuote quoteSellToPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("Count must be positive.");
        }

        PriceProfile profile = profile(server, offer);
        if (offer.pricing().mode() != PriceMode.FIXED && hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemSell(profile.tradeLevel())) {
            return SystemTradeQuote.fail("This item is not available from the system market.");
        }
        long unitPrice = resolveUnitPrice(offer, profile, true);
        if (unitPrice <= 0L) {
            return SystemTradeQuote.fail("This item is not available from the system market.");
        }
        return total(unitPrice, count);
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("Count must be positive.");
        }

        PriceProfile profile = profile(server, offer);
        if (offer.pricing().mode() != PriceMode.FIXED && hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemBuy(profile.tradeLevel())) {
            return SystemTradeQuote.fail("The system market is not buying this item.");
        }
        long unitPrice = stockAdjustedBuyPrice(server, offer, resolveUnitPrice(offer, profile, false));
        if (unitPrice <= 0L) {
            return SystemTradeQuote.fail("The system market is not buying this item.");
        }
        return total(unitPrice, count);
    }

    private static PriceProfile profile(MinecraftServer server, SystemMarketOffer offer) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        return PriceResolver.resolve(server, itemId);
    }

    private static SystemTradeQuote total(long unitPrice, int count) {
        try {
            return SystemTradeQuote.success(Math.multiplyExact(unitPrice, count), unitPrice);
        } catch (ArithmeticException exception) {
            return SystemTradeQuote.fail("Total price is too large.");
        }
    }

    private static boolean hasExplicitTradeLevel(PriceProfile profile) {
        return profile.source() == PriceSource.ANCHOR || profile.source() == PriceSource.MIXED;
    }

    private static long resolveUnitPrice(SystemMarketOffer offer, PriceProfile profile, boolean systemSells) {
        SystemOfferPricing pricing = offer.pricing();
        return switch (pricing.mode()) {
            case FIXED -> pricing.basePrice();
            case AUTO -> dynamicPrice(profile, systemSells);
            case ANCHOR -> dynamicPriceFromAnchor(pricing.basePrice(), profile, systemSells);
            case MULTIPLIER -> multiply(dynamicPrice(profile, systemSells), pricing.multiplier());
            case BAND -> clamp(dynamicPrice(profile, systemSells), pricing.minPrice(), pricing.maxPrice());
        };
    }

    private static long dynamicPrice(PriceProfile profile, boolean systemSells) {
        return systemSells ? profile.systemSellPrice() : profile.systemBuyPrice();
    }

    private static long dynamicPriceFromAnchor(long anchorPrice, PriceProfile profile, boolean systemSells) {
        if (anchorPrice <= 0L) {
            return dynamicPrice(profile, systemSells);
        }

        long referencePrice = anchorPrice;
        if (profile.marketPrice() > 0L) {
            double confidence = switch (profile.confidence()) {
                case HIGH -> 1.0D;
                case MEDIUM -> 0.5D;
                case LOW -> 0.25D;
                case NONE -> 0.0D;
            };
            referencePrice = Math.max(1L, Math.round(anchorPrice * (1.0D - confidence) + profile.marketPrice() * confidence));
        }

        if (systemSells) {
            return Math.max(1L, (long) Math.ceil(referencePrice * Config.PRICING_DEFAULT_SELL_RATIO.get()));
        }
        return Math.max(1L, (long) Math.floor(referencePrice * Config.PRICING_DEFAULT_BUY_RATIO.get()));
    }

    private static long multiply(long price, double multiplier) {
        if (price <= 0L || multiplier <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.round(price * multiplier));
    }

    private static long clamp(long price, long minPrice, long maxPrice) {
        if (price <= 0L) {
            return 0L;
        }
        long clamped = price;
        if (minPrice > 0L) {
            clamped = Math.max(clamped, minPrice);
        }
        if (maxPrice > 0L) {
            clamped = Math.min(clamped, maxPrice);
        }
        return Math.max(1L, clamped);
    }

    private static long stockAdjustedBuyPrice(MinecraftServer server, SystemMarketOffer offer, long unitPrice) {
        if (server == null || unitPrice <= 0L || offer.pricing().mode() == PriceMode.FIXED) {
            return unitPrice;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        long stock = SystemStockSavedData.get(server).actualStock(itemId);
        if (stock <= 0L) {
            return unitPrice;
        }

        double ratio = 1.0D - stock * Config.SYSTEM_STOCK_BUY_PRICE_IMPACT_PER_ITEM.get();
        ratio = Math.max(Config.SYSTEM_STOCK_MIN_BUY_PRICE_RATIO.get(), ratio);
        return Math.max(1L, (long) Math.floor(unitPrice * ratio));
    }
}
