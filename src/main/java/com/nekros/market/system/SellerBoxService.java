package com.nekros.market.system;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.menu.SellerBoxMenu;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.system.SystemBuyPressure;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.storage.EconomyLedgerSavedData;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemPayoutBudgetSavedData;
import com.nekros.market.storage.SystemStockSavedData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class SellerBoxService {
    private SellerBoxService() {
    }

    public static Result sell(ServerPlayer player, SellerBoxMenu menu) {
        Container container = menu.container();
        long totalPrice = 0L;
        int soldStacks = 0;
        int soldItems = 0;
        int skippedStacks = 0;
        long gameTime = player.server.overworld().getGameTime();
        MarketSavedData data = MarketSavedData.get(player.server);

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            SystemBuyPressure.Quote quote = buyQuote(player, stack, data);
            if (quote.totalPrice() <= 0L) {
                skippedStacks++;
                continue;
            }

            try {
                totalPrice = Math.addExact(totalPrice, quote.totalPrice());
            } catch (ArithmeticException exception) {
                skippedStacks++;
                continue;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            SystemStockSavedData.get(player.server).recordSystemBuy(itemId, stack.getCount(), gameTime);
            EconomyLedgerSavedData.get(player.server).recordSystemBuyPayout(itemId, quote.totalPrice());
            SystemPayoutBudgetSavedData.get(player.server).recordPayout(
                    player.getUUID(),
                    EconomicPolicyRegistry.tierOf(itemId),
                    quote.totalPrice(),
                    gameTime);
            soldItems += stack.getCount();
            soldStacks++;
            container.setItem(slot, ItemStack.EMPTY);
        }

        if (totalPrice <= 0L) {
            return Result.fail(skippedStacks > 0 ? "没有可回收的物品。" : "回收箱是空的。");
        }

        MarketEconomy.add(data, player.getUUID(), totalPrice);
        data.setDirty();
        container.setChanged();
        menu.broadcastChanges();

        String skippedText = skippedStacks > 0 ? "，跳过 " + skippedStacks + " 组" : "";
        return Result.success("已回收 " + soldItems + " 个物品（" + soldStacks + " 组），获得 " + totalPrice + skippedText + "。");
    }

    private static SystemBuyPressure.Quote buyQuote(ServerPlayer player, ItemStack stack, MarketSavedData marketData) {
        if (!safeForAutomaticBuyback(stack)) {
            return new SystemBuyPressure.Quote(0L, 0L, 0.0D, "回收压力: 该物品不可自动回收。");
        }
        PriceProfile profile = PriceResolver.resolve(player.server, stack);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SystemPriceService.quoteAutomaticBuyback(player.server, itemId, profile, stack.getCount(), player.getUUID(), marketData);
    }

    private static boolean safeForAutomaticBuyback(ItemStack stack) {
        return !stack.isEmpty() && stack.isComponentsPatchEmpty();
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
