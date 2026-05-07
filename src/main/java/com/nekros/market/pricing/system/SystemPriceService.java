package com.nekros.market.pricing.system;

import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.system.SystemMarketOffer;

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
        if (hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemSell(profile.tradeLevel())) {
            return SystemTradeQuote.fail("This item is not available from the system market.");
        }
        long unitPrice = profile.systemSellPrice() > 0L ? profile.systemSellPrice() : offer.unitPrice();
        if (profile.systemSellPrice() <= 0L && offer.unitPrice() <= 0L) {
            return SystemTradeQuote.fail("This item is not available from the system market.");
        }
        return total(unitPrice, count);
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("Count must be positive.");
        }

        PriceProfile profile = profile(server, offer);
        if (hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemBuy(profile.tradeLevel())) {
            return SystemTradeQuote.fail("The system market is not buying this item.");
        }
        long unitPrice = profile.systemBuyPrice() > 0L ? profile.systemBuyPrice() : offer.unitPrice();
        if (profile.systemBuyPrice() <= 0L && offer.unitPrice() <= 0L) {
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
}
