package com.nekros.market.command;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.listing.MarketListing;
import com.nekros.market.listing.MarketService;
import com.nekros.market.menu.MarketMenuSnapshots;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.system.SystemMarketConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class MarketCommands {
    private MarketCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("market")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("balance")
                        .executes(context -> balanceSelf(context.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> balanceOther(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("money")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                                .executes(context -> addMoney(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        LongArgumentType.getLong(context, "amount"))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0L))
                                                .executes(context -> setMoney(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        LongArgumentType.getLong(context, "amount")))))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", LongArgumentType.longArg(1L))
                                .executes(context -> sell(
                                        context.getSource(),
                                        LongArgumentType.getLong(context, "price"),
                                        -1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> sell(
                                                context.getSource(),
                                                LongArgumentType.getLong(context, "price"),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("listingId", StringArgumentType.word())
                                .executes(context -> buy(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "listingId"),
                                        1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> buy(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "listingId"),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("listingId", StringArgumentType.word())
                                .executes(context -> cancel(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "listingId")))))
                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource())))
                .then(Commands.literal("listings")
                        .executes(context -> listings(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> listings(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("mine")
                        .executes(context -> mine(context.getSource())))
                .then(Commands.literal("expire")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> expire(context.getSource())))
                .then(Commands.literal("system")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadSystemMarket(context.getSource())))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> removeSystemOffer(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .then(Commands.argument("item", StringArgumentType.word())
                                                        .then(Commands.argument("price", LongArgumentType.longArg(1L))
                                                                .executes(context -> addSystemOffer(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "type"),
                                                                        StringArgumentType.getString(context, "item"),
                                                                        LongArgumentType.getLong(context, "price"),
                                                                        ""))
                                                                .then(Commands.argument("category", StringArgumentType.word())
                                                                        .executes(context -> addSystemOffer(
                                                                                context.getSource(),
                                                                                StringArgumentType.getString(context, "id"),
                                                                                StringArgumentType.getString(context, "type"),
                                                                                StringArgumentType.getString(context, "item"),
                                                                                LongArgumentType.getLong(context, "price"),
                                                                                StringArgumentType.getString(context, "category")))))))))));
    }

    private static int showHelp(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            MarketSavedData data = MarketSavedData.get(source.getServer());
            player.openMenu(
                    new SimpleMenuProvider(
                            (containerId, inventory, ignored) -> new com.nekros.market.menu.MarketMenu(
                                    containerId,
                                    MarketMenuSnapshots.create(data, player, 1)),
                            Component.literal("Nek's Market")),
                    buffer -> MarketMenuSnapshots.create(data, player, 1).write(buffer));
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            source.sendSuccess(() -> Component.literal("Nek's Market commands: /market listings, /market sell <price>, /market buy <id>, /market claim, /market balance"), false);
            return 1;
        }
    }

    private static int balanceSelf(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return showBalance(source, source.getPlayerOrException());
    }

    private static int balanceOther(CommandSourceStack source, ServerPlayer player) {
        return showBalance(source, player);
    }

    private static int showBalance(CommandSourceStack source, ServerPlayer player) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        long balance = MarketEconomy.balance(data, player.getUUID());
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " has " + balance + " " + MarketEconomy.CURRENCY_NAME + "."), false);
        return 1;
    }

    private static int addMoney(CommandSourceStack source, ServerPlayer player, long amount) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketEconomy.add(data, player.getUUID(), amount);
        long balance = MarketEconomy.balance(data, player.getUUID());
        source.sendSuccess(() -> Component.literal("Added " + amount + " " + MarketEconomy.CURRENCY_NAME + " to " + player.getGameProfile().getName() + ". Balance: " + balance), true);
        return 1;
    }

    private static int setMoney(CommandSourceStack source, ServerPlayer player, long amount) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketEconomy.setBalance(data, player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Set " + player.getGameProfile().getName() + "'s balance to " + amount + " " + MarketEconomy.CURRENCY_NAME + "."), true);
        return 1;
    }

    private static int sell(CommandSourceStack source, long price, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketService.SellResult result = count > 0
                ? MarketService.sellMainHand(data, player, price, count)
                : MarketService.sellMainHand(data, player, price);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        MarketListing listing = result.listing();
        source.sendSuccess(() -> Component.literal("Listed " + itemName(listing) + " x" + listing.count() + " for " + price + " " + MarketEconomy.CURRENCY_NAME + " each. ID: " + shortId(listing.id())), false);
        return 1;
    }

    private static int buy(CommandSourceStack source, String idText, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID id = parseListingId(source, idText);
        if (id == null) {
            return 0;
        }

        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketService.BuyResult result = MarketService.buy(data, source.getPlayerOrException(), id, count);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Bought " + itemName(result.listing()) + " x" + result.listing().count() + " for " + result.totalPrice() + " " + MarketEconomy.CURRENCY_NAME + "."), false);
        return 1;
    }

    private static int cancel(CommandSourceStack source, String idText) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID id = parseListingId(source, idText);
        if (id == null) {
            return 0;
        }

        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketService.CancelResult result = MarketService.cancel(data, source.getPlayerOrException(), id);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Canceled listing " + shortId(result.listing().id()) + ". The item is waiting in /market claim."), false);
        return 1;
    }

    private static int claim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketService.ClaimResult result = MarketService.claim(data, player);
        source.sendSuccess(() -> Component.literal("Claimed " + result.money() + " " + MarketEconomy.CURRENCY_NAME + " and " + result.claimedItems() + " item stack(s). Remaining item stack(s): " + result.remainingItems()), false);
        return 1;
    }

    private static int listings(CommandSourceStack source, int page) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        int pageCount = MarketService.pageCount(data);
        int clampedPage = Math.min(page, pageCount);
        List<MarketListing> listings = MarketService.page(data, clampedPage);

        source.sendSuccess(() -> Component.literal("Market listings page " + clampedPage + "/" + pageCount + ":"), false);
        if (listings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No listings yet."), false);
            return 1;
        }

        for (MarketListing listing : listings) {
            source.sendSuccess(() -> Component.literal(shortId(listing.id()) + " | " + itemName(listing) + " x" + listing.count() + " | " + listing.price() + " " + MarketEconomy.CURRENCY_NAME + " each | seller: " + listing.sellerName()), false);
        }
        return listings.size();
    }

    private static int mine(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketSavedData data = MarketSavedData.get(source.getServer());
        List<MarketListing> listings = MarketService.ownListings(data, player.getUUID());
        source.sendSuccess(() -> Component.literal("Your active listings:"), false);
        if (listings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active listings."), false);
            return 1;
        }

        for (MarketListing listing : listings) {
            source.sendSuccess(() -> Component.literal(shortId(listing.id()) + " | " + itemName(listing) + " x" + listing.count() + " | " + listing.price() + " " + MarketEconomy.CURRENCY_NAME + " each"), false);
        }
        return listings.size();
    }

    private static int expire(CommandSourceStack source) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        int expired = MarketService.expireListings(data);
        source.sendSuccess(() -> Component.literal("Expired " + expired + " listing(s)."), true);
        return expired;
    }

    private static int reloadSystemMarket(CommandSourceStack source) {
        SystemMarketConfig.Result result = SystemMarketConfig.reload();
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int addSystemOffer(CommandSourceStack source, String id, String type, String item, long price, String category) {
        SystemMarketConfig.Result result = SystemMarketConfig.addOffer(id, type, item, price, category);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int removeSystemOffer(CommandSourceStack source, String id) {
        SystemMarketConfig.Result result = SystemMarketConfig.removeOffer(id);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static UUID parseListingId(CommandSourceStack source, String idText) {
        for (MarketListing listing : MarketSavedData.get(source.getServer()).listings().values()) {
            if (listing.id().toString().equalsIgnoreCase(idText) || shortId(listing.id()).equalsIgnoreCase(idText)) {
                return listing.id();
            }
        }

        try {
            return UUID.fromString(idText);
        } catch (IllegalArgumentException ignored) {
            source.sendFailure(Component.literal("Invalid listing id. Use /market listings to copy an id."));
            return null;
        }
    }

    private static String itemName(MarketListing listing) {
        return listing.item().getHoverName().getString();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
