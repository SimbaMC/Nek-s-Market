package com.nekros.market.system;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.menu.SellerBoxMenu;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.storage.MarketSavedData;
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

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            long unitPrice = unitBuyPrice(player, stack);
            if (unitPrice <= 0L) {
                skippedStacks++;
                continue;
            }

            long stackPrice;
            try {
                stackPrice = Math.multiplyExact(unitPrice, stack.getCount());
                totalPrice = Math.addExact(totalPrice, stackPrice);
            } catch (ArithmeticException exception) {
                skippedStacks++;
                continue;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            SystemStockSavedData.get(player.server).recordSystemBuy(itemId, stack.getCount(), gameTime);
            soldItems += stack.getCount();
            soldStacks++;
            container.setItem(slot, ItemStack.EMPTY);
        }

        if (totalPrice <= 0L) {
            return Result.fail(skippedStacks > 0 ? "没有可回收的物品。" : "回收箱是空的。");
        }

        MarketSavedData data = MarketSavedData.get(player.server);
        MarketEconomy.add(data, player.getUUID(), totalPrice);
        data.setDirty();
        container.setChanged();
        menu.broadcastChanges();

        String skippedText = skippedStacks > 0 ? "，跳过 " + skippedStacks + " 组" : "";
        return Result.success("已回收 " + soldItems + " 个物品（" + soldStacks + " 组），获得 " + totalPrice + skippedText + "。");
    }

    private static long unitBuyPrice(ServerPlayer player, ItemStack stack) {
        if (!safeForAutomaticBuyback(stack)) {
            return 0L;
        }
        PriceProfile profile = PriceResolver.resolve(player.server, stack);
        if (!PriceResolver.allowsSystemBuy(profile.tradeLevel())) {
            return 0L;
        }
        long unitPrice = profile.systemBuyPrice();
        if (unitPrice <= 0L) {
            return 0L;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SystemPriceService.dynamicBuyPriceForStock(player.server, itemId, unitPrice);
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
