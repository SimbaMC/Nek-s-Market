package com.nekros.market.network;

import java.util.ArrayList;
import java.util.List;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.listing.MarketService;
import com.nekros.market.menu.MarketMenu;
import com.nekros.market.menu.MarketMenuSnapshot;
import com.nekros.market.menu.MarketMenuSnapshots;
import com.nekros.market.menu.SellerBoxMenu;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.pricing.system.SystemTradeQuote;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemStockSavedData;
import com.nekros.market.system.PriceMode;
import com.nekros.market.system.SellerBoxService;
import com.nekros.market.system.SystemMarketConfig;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.system.SystemMarketService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(MarketListingsPayload.TYPE, MarketListingsPayload.STREAM_CODEC, ModNetworking::handleListings);
        registrar.playToClient(MarketSystemConfigPayload.TYPE, MarketSystemConfigPayload.STREAM_CODEC, ModNetworking::handleSystemConfig);
        registrar.playToServer(MarketActionPayload.TYPE, MarketActionPayload.STREAM_CODEC, ModNetworking::handleAction);
        registrar.playToServer(MarketAdminActionPayload.TYPE, MarketAdminActionPayload.STREAM_CODEC, ModNetworking::handleAdminAction);
        registrar.playToServer(SellerBoxSellPayload.TYPE, SellerBoxSellPayload.STREAM_CODEC, ModNetworking::handleSellerBoxSell);
    }

    private static void handleSellerBoxSell(SellerBoxSellPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof SellerBoxMenu menu)) {
            return;
        }

        SellerBoxService.Result result = SellerBoxService.sell(player, menu);
        player.displayClientMessage(Component.literal(result.message()), false);
        if (result.success()) {
            syncInventory(player);
        }
    }

    private static void handleListings(MarketListingsPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player().containerMenu instanceof MarketMenu menu) {
            menu.updateSnapshot(payload.snapshot());
        }
    }

    private static void handleSystemConfig(MarketSystemConfigPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        SystemMarketOffer.setSyncedConfig(payload.categories(), payload.offers(), payload.fallbackOffers(), payload.stocks());
    }

    private static void handleAction(MarketActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        MarketSavedData data = MarketSavedData.get(player.server);
        int page = Math.max(1, payload.page());
        if (MarketActionPayload.BUY.equals(payload.action()) && payload.listingId().isPresent()) {
            MarketService.BuyResult result = MarketService.buy(data, player, payload.listingId().get(), payload.count());
            if (result.success()) {
                player.displayClientMessage(Component.literal("已购买 " + result.listing().item().getHoverName().getString() + " x" + result.listing().count() + "，花费 " + result.totalPrice() + " " + MarketEconomy.CURRENCY_NAME + "。"), false);
                syncInventory(player);
            } else {
                player.displayClientMessage(Component.literal(result.message()), false);
            }
        } else if (MarketActionPayload.SELL.equals(payload.action())) {
            MarketService.SellResult result = MarketService.sellMainHand(data, player, payload.price(), payload.count());
            if (result.success()) {
                player.displayClientMessage(Component.literal("已上架 " + result.listing().item().getHoverName().getString() + " x" + result.listing().count() + "，单价 " + result.listing().price() + " " + MarketEconomy.CURRENCY_NAME + "。"), false);
                syncInventory(player);
            } else {
                player.displayClientMessage(Component.literal(result.message()), false);
            }
        } else if (MarketActionPayload.SYSTEM_TRADE.equals(payload.action())) {
            SystemMarketService.Result result = SystemMarketService.trade(data, player, payload.offerId(), payload.count());
            player.displayClientMessage(Component.literal(result.message()), false);
            if (result.success()) {
                syncInventory(player);
            }
        }

        sendSnapshot(player, page);
    }

    public static void sendSnapshot(ServerPlayer player, int page) {
        MarketSavedData data = MarketSavedData.get(player.server);
        MarketMenuSnapshot snapshot = MarketMenuSnapshots.create(data, player, page);
        if (player.containerMenu instanceof MarketMenu menu) {
            menu.updateSnapshot(snapshot);
        }
        PacketDistributor.sendToPlayer(player, new MarketSystemConfigPayload(
                SystemMarketOffer.configCategoryLines(),
                quotedOfferLines(player),
                SystemMarketOffer.configOfferLines(),
                stockLines(player)));
        PacketDistributor.sendToPlayer(player, new MarketListingsPayload(snapshot));
    }

    private static void handleAdminAction(MarketAdminActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("你没有权限编辑系统商店。"), false);
            return;
        }

        SystemMarketConfig.Result result;
        if (MarketAdminActionPayload.ADD.equals(payload.action())) {
            result = SystemMarketConfig.addOffer(payload.id(), payload.offerType(), payload.itemId(), payload.price(), payload.category());
        } else if (MarketAdminActionPayload.ADD_PRICED.equals(payload.action())) {
            result = addPricedAdminOffer(payload);
        } else if (MarketAdminActionPayload.REMOVE.equals(payload.action())) {
            result = SystemMarketConfig.removeOffer(payload.id());
        } else if (MarketAdminActionPayload.RELOAD.equals(payload.action())) {
            result = SystemMarketConfig.reload();
        } else if (MarketAdminActionPayload.RENAME_CATEGORY.equals(payload.action())) {
            result = SystemMarketConfig.renameCategory(payload.id(), payload.category());
        } else if (MarketAdminActionPayload.RESET_CATEGORY.equals(payload.action())) {
            result = SystemMarketConfig.resetCategory(payload.id());
        } else {
            result = new SystemMarketConfig.Result(false, "未知管理操作。");
        }

        player.displayClientMessage(Component.literal(result.message()), false);
        sendSnapshot(player, 1);
        PacketDistributor.sendToPlayer(player, new MarketSystemConfigPayload(
                SystemMarketOffer.configCategoryLines(),
                quotedOfferLines(player),
                SystemMarketOffer.configOfferLines(),
                stockLines(player)));
    }

    private static SystemMarketConfig.Result addPricedAdminOffer(MarketAdminActionPayload payload) {
        PriceMode mode;
        try {
            mode = PriceMode.valueOf(payload.mode().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return new SystemMarketConfig.Result(false, "无效价格模式: " + payload.mode());
        }
        return SystemMarketConfig.addPricedOffer(
                payload.id(),
                payload.offerType(),
                payload.itemId(),
                payload.category(),
                mode,
                payload.basePrice(),
                payload.multiplier(),
                payload.minPrice(),
                payload.maxPrice(),
                payload.flags());
    }

    private static List<String> quotedOfferLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        for (SystemMarketOffer offer : SystemMarketOffer.configOffers()) {
            long unitPrice = quoteUnitPrice(player, offer);
            if (unitPrice <= 0L) {
                continue;
            }
            lines.add(offer.id()
                    + "|" + configType(offer.type())
                    + "|" + BuiltInRegistries.ITEM.getKey(offer.item().getItem())
                    + "|" + unitPrice
                    + "|" + offer.buyCategory());
        }
        return List.copyOf(lines);
    }

    private static long quoteUnitPrice(ServerPlayer player, SystemMarketOffer offer) {
        SystemTradeQuote quote = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS
                ? SystemPriceService.quoteSellToPlayer(player.server, offer, 1)
                : SystemPriceService.quoteBuyFromPlayer(player.server, offer, 1);
        if (quote.unitPricePreview() > 0L) {
            return quote.unitPricePreview();
        }
        return offer.pricing().basePrice() > 0L ? offer.pricing().basePrice() : 0L;
    }

    private static List<String> stockLines(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        SystemStockSavedData stock = SystemStockSavedData.get(player.server);
        for (SystemMarketOffer offer : SystemMarketOffer.configOffers()) {
            long available = offer.pricing().infiniteStock()
                    ? Long.MAX_VALUE
                    : stock.actualStock(BuiltInRegistries.ITEM.getKey(offer.item().getItem()));
            lines.add(offer.id() + "|" + available);
        }
        return List.copyOf(lines);
    }

    private static String configType(SystemMarketOffer.Type type) {
        return type == SystemMarketOffer.Type.SYSTEM_SELLS ? "sell_to_player" : "buy_from_player";
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, slot, player.getInventory().getItem(slot).copy()));
        }
    }
}
