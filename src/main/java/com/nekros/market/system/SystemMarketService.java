package com.nekros.market.system;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.pricing.system.SystemTradeQuote;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemStockSavedData;
import com.nekros.market.util.InventoryUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class SystemMarketService {
    private SystemMarketService() {
    }

    public static Result trade(MarketSavedData data, ServerPlayer player, String offerId, int count) {
        if (count <= 0) {
            return Result.fail("Count must be positive.");
        }

        SystemMarketOffer offer = SystemMarketOffer.byId(offerId);
        if (offer == null) {
            return Result.fail("That system offer no longer exists.");
        }

        SystemTradeQuote quote;
        if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            quote = SystemPriceService.quoteSellToPlayer(player.server, offer, count);
        } else {
            quote = SystemPriceService.quoteBuyFromPlayer(player.server, offer, count);
        }

        if (!quote.allowed()) {
            return Result.fail(quote.message());
        }
        long totalPrice = quote.totalPrice();

        if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            if (!InventoryUtil.canFit(player.getInventory(), offer.item(), count)) {
                return Result.fail("You do not have enough inventory space.");
            }
            if (!MarketEconomy.withdraw(data, player.getUUID(), totalPrice)) {
                return Result.fail("You do not have enough " + MarketEconomy.CURRENCY_NAME + ".");
            }
            InventoryUtil.addSplit(player.getInventory(), offer.item(), count);
            SystemStockSavedData.get(player.server).recordSystemSell(itemId(offer), count);
            data.setDirty();
            return Result.success("Bought " + offer.item().getHoverName().getString() + " x" + count + " for " + totalPrice + " " + MarketEconomy.CURRENCY_NAME + ".");
        }

        int available = InventoryUtil.countMatching(player.getInventory(), offer.item());
        if (available < count) {
            return Result.fail("You only have " + available + " matching item(s).");
        }
        if (!InventoryUtil.removeMatching(player.getInventory(), offer.item(), count)) {
            return Result.fail("Could not remove the selected item from your inventory.");
        }
        MarketEconomy.add(data, player.getUUID(), totalPrice);
        SystemStockSavedData.get(player.server).recordSystemBuy(itemId(offer), count);
        data.setDirty();
        return Result.success("Sold " + offer.item().getHoverName().getString() + " x" + count + " for " + totalPrice + " " + MarketEconomy.CURRENCY_NAME + ".");
    }

    private static ResourceLocation itemId(SystemMarketOffer offer) {
        return BuiltInRegistries.ITEM.getKey(offer.item().getItem());
    }

    public record Result(boolean success, String message) {
        static Result success(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
