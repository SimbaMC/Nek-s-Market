package com.nekros.market.system;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.pricing.system.SystemTradeQuote;
import com.nekros.market.storage.EconomyLedgerSavedData;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemPayoutBudgetSavedData;
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
            return Result.fail("数量必须大于 0。");
        }

        SystemMarketOffer offer = SystemMarketOffer.byId(offerId);
        if (offer == null) {
            return Result.fail("该系统货架已不存在。");
        }

        SystemTradeQuote quote;
        if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            quote = SystemPriceService.quoteSellToPlayer(player.server, offer, count);
        } else {
            quote = SystemPriceService.quoteBuyFromPlayer(player.server, offer, count, player.getUUID(), data);
        }

        if (!quote.allowed()) {
            return Result.fail(quote.message());
        }
        long totalPrice = quote.totalPrice();

        if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
            if (!InventoryUtil.canFit(player.getInventory(), offer.item(), count)) {
                return Result.fail("背包空间不足。");
            }
            if (!MarketEconomy.withdraw(data, player.getUUID(), totalPrice)) {
                return Result.fail("你的 " + MarketEconomy.CURRENCY_NAME + " 不足。");
            }
            InventoryUtil.addSplit(player.getInventory(), offer.item(), count);
            ResourceLocation itemId = itemId(offer);
            long gameTime = player.server.overworld().getGameTime();
            SystemStockSavedData.get(player.server).recordSystemSell(itemId, count, gameTime);
            EconomyLedgerSavedData.get(player.server).recordSystemSellIncome(itemId, totalPrice);
            SystemPayoutBudgetSavedData.get(player.server).recordSystemSellIncome(totalPrice, gameTime);
            data.setDirty();
            return Result.success("已购买 " + offer.item().getHoverName().getString() + " x" + count + "，花费 " + totalPrice + " " + MarketEconomy.CURRENCY_NAME + "。");
        }

        int available = InventoryUtil.countMatching(player.getInventory(), offer.item());
        if (available < count) {
            return Result.fail("你只有 " + available + " 个匹配物品。");
        }
        if (!InventoryUtil.removeMatching(player.getInventory(), offer.item(), count)) {
            return Result.fail("无法从背包移除选中的物品。");
        }
        MarketEconomy.add(data, player.getUUID(), totalPrice);
        ResourceLocation itemId = itemId(offer);
        long gameTime = player.server.overworld().getGameTime();
        SystemStockSavedData.get(player.server).recordSystemBuy(itemId, count, gameTime);
        EconomyLedgerSavedData.get(player.server).recordSystemBuyPayout(itemId, totalPrice);
        SystemPayoutBudgetSavedData.get(player.server).recordPayout(
                player.getUUID(),
                EconomicPolicyRegistry.tierOf(itemId),
                totalPrice,
                gameTime);
        data.setDirty();
        return Result.success("已出售 " + offer.item().getHoverName().getString() + " x" + count + "，获得 " + totalPrice + " " + MarketEconomy.CURRENCY_NAME + "。");
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
