package com.nekros.market.command;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nekros.market.Config;
import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.listing.MarketListing;
import com.nekros.market.listing.MarketService;
import com.nekros.market.menu.MarketMenuSnapshots;
import com.nekros.market.network.ModNetworking;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.system.SystemBuyPressure;
import com.nekros.market.pricing.system.SystemPriceBreakdown;
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.pricing.system.SystemTradeQuote;
import com.nekros.market.storage.EconomyLedgerSavedData;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemPayoutBudgetSavedData;
import com.nekros.market.storage.SystemStockSavedData;
import com.nekros.market.system.SystemMarketConfig;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.system.PriceMode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;

public final class MarketCommands {
    private static final SuggestionProvider<CommandSourceStack> SYSTEM_TYPE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(List.of("sell", "buy"), builder);
    private static final SuggestionProvider<CommandSourceStack> SYSTEM_CATEGORY_SLOT_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    java.util.stream.IntStream.rangeClosed(1, Config.SYSTEM_BUY_CATEGORIES.get().size())
                            .mapToObj(Integer::toString),
                    builder);
    private static final SuggestionProvider<CommandSourceStack> ITEM_TAG_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    BuiltInRegistries.ITEM.getTags()
                            .map(pair -> pair.getFirst().location().toString()),
                    builder);

    private MarketCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
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
                        .executes(context -> economyLedger(context.getSource()))
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
                .then(Commands.literal("economy")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> economyLedger(context.getSource())))
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
                .then(PriceAdminCommands.priceCommand(buildContext))
                .then(Commands.literal("expire")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> expire(context.getSource())))
                .then(systemCommand(buildContext)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> systemCommand(CommandBuildContext buildContext) {
        return Commands.literal("system")
                .requires(source -> source.hasPermission(2))
                .executes(context -> systemHelp(context.getSource()))
                .then(Commands.literal("reload")
                        .executes(context -> reloadSystemMarket(context.getSource())))
                .then(stockCommand(buildContext))
                .then(Commands.literal("quote")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SYSTEM_TYPE_SUGGESTIONS)
                                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(context -> systemQuote(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "type"),
                                                itemId(context, "item"),
                                                1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                .executes(context -> systemQuote(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "type"),
                                                        itemId(context, "item"),
                                                        IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("testsell")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> systemTestSell(
                                                context.getSource(),
                                                itemId(context, "item"),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> removeSystemOffer(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("category", StringArgumentType.string()).suggests(SYSTEM_CATEGORY_SLOT_SUGGESTIONS)
                                        .executes(context -> addAutoSystemOffer(
                                                context.getSource(),
                                                "sell_to_player",
                                                itemId(context, "item"),
                                                StringArgumentType.getString(context, "category"),
                                                ""))
                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                .executes(context -> addAutoSystemOffer(
                                                        context.getSource(),
                                                        "sell_to_player",
                                                        itemId(context, "item"),
                                                        StringArgumentType.getString(context, "category"),
                                                        StringArgumentType.getString(context, "flags")))))))
                .then(Commands.literal("selltag")
                        .then(Commands.argument("tag", ResourceLocationArgument.id()).suggests(ITEM_TAG_SUGGESTIONS)
                                .then(Commands.argument("category", StringArgumentType.string()).suggests(SYSTEM_CATEGORY_SLOT_SUGGESTIONS)
                                        .executes(context -> addAutoSystemOffersFromTag(
                                                context.getSource(),
                                                "sell_to_player",
                                                ResourceLocationArgument.getId(context, "tag"),
                                                StringArgumentType.getString(context, "category"),
                                                ""))
                                        .then(Commands.argument("flags", StringArgumentType.greedyString())
                                                .executes(context -> addAutoSystemOffersFromTag(
                                                        context.getSource(),
                                                        "sell_to_player",
                                                        ResourceLocationArgument.getId(context, "tag"),
                                                        StringArgumentType.getString(context, "category"),
                                                        StringArgumentType.getString(context, "flags")))))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> addAutoSystemOffer(
                                        context.getSource(),
                                        "buy_from_player",
                                        itemId(context, "item"),
                                        "",
                                        ""))
                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                        .executes(context -> addAutoSystemOffer(
                                                context.getSource(),
                                                "buy_from_player",
                                        itemId(context, "item"),
                                        "",
                                        StringArgumentType.getString(context, "flags"))))))
                .then(Commands.literal("buytag")
                        .then(Commands.argument("tag", ResourceLocationArgument.id()).suggests(ITEM_TAG_SUGGESTIONS)
                                .executes(context -> addAutoSystemOffersFromTag(
                                        context.getSource(),
                                        "buy_from_player",
                                        ResourceLocationArgument.getId(context, "tag"),
                                        "",
                                        ""))
                                .then(Commands.argument("flags", StringArgumentType.greedyString())
                                        .executes(context -> addAutoSystemOffersFromTag(
                                                context.getSource(),
                                                "buy_from_player",
                                        ResourceLocationArgument.getId(context, "tag"),
                                        "",
                                        StringArgumentType.getString(context, "flags"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stockCommand(CommandBuildContext buildContext) {
        return Commands.literal("stock")
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(context -> systemStock(context.getSource(), "", itemId(context, "item"))))
                .then(Commands.argument("type", StringArgumentType.word()).suggests(SYSTEM_TYPE_SUGGESTIONS)
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> systemStock(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "type"),
                                        itemId(context, "item")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("count", LongArgumentType.longArg(0L))
                                        .executes(context -> setSystemStock(
                                                context.getSource(),
                                                itemId(context, "item"),
                                                LongArgumentType.getLong(context, "count"))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("delta", LongArgumentType.longArg())
                                        .executes(context -> addSystemStock(
                                                context.getSource(),
                                                itemId(context, "item"),
                                                LongArgumentType.getLong(context, "delta"))))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> clearSystemStock(
                                        context.getSource(),
                                        itemId(context, "item")))));
    }

    private static int systemHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("系统商店管理命令:"), false);
        source.sendSuccess(() -> Component.literal("/market system sell <物品> <分类序号> [flags] - 加入系统商店货架"), false);
        source.sendSuccess(() -> Component.literal("/market system buy <物品> [flags] - 允许系统回收"), false);
        source.sendSuccess(() -> Component.literal("/market system selltag <标签ID> <分类序号> [flags] - 批量加入货架"), false);
        source.sendSuccess(() -> Component.literal("/market system buytag <标签ID> [flags] - 批量允许回收"), false);
        source.sendSuccess(() -> Component.literal("/market system quote <sell|buy> <物品> [数量] - 试算系统报价"), false);
        source.sendSuccess(() -> Component.literal("/market system stock <物品> - 查看库存和压力"), false);
        source.sendSuccess(() -> Component.literal("/market system reload - 重载系统商店配置"), false);
        return 1;
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
                            Component.literal("Nek's Market 市场")),
                    buffer -> MarketMenuSnapshots.create(data, player, 1).write(buffer));
            ModNetworking.sendSnapshot(player, 1);
            return 1;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            source.sendSuccess(() -> Component.literal("Nek's Market 指令: /market, /market balance, /market sell <价格>, /market listings, /market buy <ID>, /market claim, /market price item"), false);
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
        source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " 当前余额: " + balance + " " + MarketEconomy.CURRENCY_NAME + "。"), false);
        return 1;
    }

    private static int addMoney(CommandSourceStack source, ServerPlayer player, long amount) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketEconomy.add(data, player.getUUID(), amount);
        EconomyLedgerSavedData.get(source.getServer()).recordAdminMoneyIssued(amount);
        long balance = MarketEconomy.balance(data, player.getUUID());
        source.sendSuccess(() -> Component.literal("已给 " + player.getGameProfile().getName() + " 增加 " + amount + " " + MarketEconomy.CURRENCY_NAME + "。当前余额: " + balance), true);
        return 1;
    }

    private static int setMoney(CommandSourceStack source, ServerPlayer player, long amount) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        long oldBalance = MarketEconomy.balance(data, player.getUUID());
        MarketEconomy.setBalance(data, player.getUUID(), amount);
        if (amount > oldBalance) {
            EconomyLedgerSavedData.get(source.getServer()).recordAdminMoneyIssued(amount - oldBalance);
        } else if (oldBalance > amount) {
            EconomyLedgerSavedData.get(source.getServer()).recordAdminMoneyRemoved(oldBalance - amount);
        }
        source.sendSuccess(() -> Component.literal("已将 " + player.getGameProfile().getName() + " 的余额设为 " + amount + " " + MarketEconomy.CURRENCY_NAME + "。"), true);
        return 1;
    }

    private static int economyLedger(CommandSourceStack source) {
        MarketSavedData marketData = MarketSavedData.get(source.getServer());
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(source.getServer());
        SystemPayoutBudgetSavedData budget = SystemPayoutBudgetSavedData.get(source.getServer());
        long gameTime = source.getServer().overworld().getGameTime();
        long dailyBudget = budget.effectiveDailyBudget(marketData, gameTime);
        long spentToday = budget.spentToday(gameTime);
        long remainingToday = budget.remainingToday(gameTime);
        long playerBudget = budget.playerDailyBudget(gameTime);
        long sellIncomeCredit = budget.sellIncomeCredit(gameTime);
        long supplyBudget = budget.supplyBudget(marketData);
        long balances = marketData.balances().values().stream().mapToLong(Long::longValue).sum();
        long pendingClaims = marketData.claims().values().stream().mapToLong(claim -> claim.money()).sum();
        long activeListingValue = marketData.listings().values().stream()
                .mapToLong(listing -> multiplyClamped(listing.price(), listing.count()))
                .sum();

        source.sendSuccess(() -> Component.literal("经济总账:"), false);
        source.sendSuccess(() -> Component.literal("玩家余额: " + balances + "，待领取: " + pendingClaims
                + "，挂单标价总额: " + activeListingValue), false);
        source.sendSuccess(() -> Component.literal("系统回收支出: " + ledger.systemBuyPayout()
                + "，系统出售收入: " + ledger.systemSellIncome()
                + "，系统净投放: " + ledger.netSystemIssue()), false);
        source.sendSuccess(() -> Component.literal("主要回收支出: " + topItemMoney(ledger.topSystemBuyPayouts(5))), false);
        source.sendSuccess(() -> Component.literal("今日系统回收预算: "
                + (dailyBudget <= 0L
                        ? "无限制"
                        : spentToday + "/" + dailyBudget + "，剩余 " + remainingToday
                                + "，单玩家上限 " + playerBudget
                                + "，货币规模加成 " + supplyBudget
                                + "，出售回流 " + sellIncomeCredit)), false);
        source.sendSuccess(() -> Component.literal("主要出售收入: " + topItemMoney(ledger.topSystemSellIncomes(5))), false);
        source.sendSuccess(() -> Component.literal("玩家交易额: " + ledger.playerTradeVolume()
                + "，成交税销毁: " + ledger.playerTradeTaxBurned()
                + "，上架费销毁: " + ledger.listingFeesBurned()
                + "，总销毁: " + ledger.totalBurned()), false);
        source.sendSuccess(() -> Component.literal("管理命令投放: " + ledger.adminMoneyIssued()
                + "，管理命令移除: " + ledger.adminMoneyRemoved()
                + "，记录净变化: " + ledger.netCurrencyChange()), false);
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
        source.sendSuccess(() -> Component.literal("已上架 " + itemName(listing) + " x" + listing.count() + "，单价 " + price + " " + MarketEconomy.CURRENCY_NAME + "。ID: " + shortId(listing.id())), false);
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

        String taxText = result.tax() > 0L ? "，成交税 " + result.tax() : "";
        source.sendSuccess(() -> Component.literal("已购买 " + itemName(result.listing()) + " x" + result.listing().count()
                + "，花费 " + result.totalPrice() + taxText + " " + MarketEconomy.CURRENCY_NAME + "。"), false);
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

        source.sendSuccess(() -> Component.literal("已取消挂单 " + shortId(result.listing().id()) + "。物品可通过 /market claim 领取。"), false);
        return 1;
    }

    private static int claim(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketSavedData data = MarketSavedData.get(source.getServer());
        MarketService.ClaimResult result = MarketService.claim(data, player);
        source.sendSuccess(() -> Component.literal("已领取 " + result.money() + " " + MarketEconomy.CURRENCY_NAME + " 和 " + result.claimedItems() + " 组物品。剩余物品组数: " + result.remainingItems()), false);
        return 1;
    }

    private static int listings(CommandSourceStack source, int page) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        int pageCount = MarketService.pageCount(data);
        int clampedPage = Math.min(page, pageCount);
        List<MarketListing> listings = MarketService.page(data, clampedPage);

        source.sendSuccess(() -> Component.literal("市场挂单 第 " + clampedPage + "/" + pageCount + " 页:"), false);
        if (listings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无挂单。"), false);
            return 1;
        }

        for (MarketListing listing : listings) {
            source.sendSuccess(() -> Component.literal(shortId(listing.id()) + " | " + itemName(listing) + " x" + listing.count() + " | 单价 " + listing.price() + " " + MarketEconomy.CURRENCY_NAME + " | 卖家: " + listing.sellerName()), false);
        }
        return listings.size();
    }

    private static int mine(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MarketSavedData data = MarketSavedData.get(source.getServer());
        List<MarketListing> listings = MarketService.ownListings(data, player.getUUID());
        source.sendSuccess(() -> Component.literal("你的有效挂单:"), false);
        if (listings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无有效挂单。"), false);
            return 1;
        }

        for (MarketListing listing : listings) {
            source.sendSuccess(() -> Component.literal(shortId(listing.id()) + " | " + itemName(listing) + " x" + listing.count() + " | 单价 " + listing.price() + " " + MarketEconomy.CURRENCY_NAME), false);
        }
        return listings.size();
    }

    private static int expire(CommandSourceStack source) {
        MarketSavedData data = MarketSavedData.get(source.getServer());
        int expired = MarketService.expireListings(data);
        source.sendSuccess(() -> Component.literal("已清理 " + expired + " 个过期挂单。"), true);
        return expired;
    }

    private static int reloadSystemMarket(CommandSourceStack source) {
        SystemMarketConfig.Result result = SystemMarketConfig.reload();
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        if (source.getEntity() instanceof ServerPlayer player) {
            ModNetworking.sendSnapshot(player, 1);
        }
        return 1;
    }

    private static int systemStock(CommandSourceStack source, String type, String itemText) {
        String offerType = type.isBlank() ? "" : normalizeOfferType(type);
        if (!type.isBlank() && offerType == null) {
            source.sendFailure(Component.literal("类型必须是 sell 或 buy。"));
            return 0;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }
        SystemStockSavedData stock = SystemStockSavedData.get(source.getServer());
        String heading = offerType.isBlank()
                ? "系统库存 " + itemId + ":"
                : "系统库存 " + offerTypeName(offerType) + " " + itemId + ":";
        source.sendSuccess(() -> Component.literal(heading), false);
        source.sendSuccess(() -> Component.literal("实际库存: " + stock.actualStock(itemId)), false);
        source.sendSuccess(() -> Component.literal("累计从玩家回收: " + stock.totalBought(itemId)), false);
        source.sendSuccess(() -> Component.literal("累计卖给玩家: " + stock.totalSold(itemId)), false);
        long gameTime = source.getServer().overworld().getGameTime();
        long halfLife = Math.max(1L, Config.SYSTEM_STOCK_PRESSURE_HALF_LIFE_TICKS.get());
        source.sendSuccess(() -> Component.literal("近期回收压力: " + formatDecimal(stock.recentBought(itemId, gameTime, halfLife))), false);
        source.sendSuccess(() -> Component.literal("近期售出压力: " + formatDecimal(stock.recentSold(itemId, gameTime, halfLife))), false);
        source.sendSuccess(() -> Component.literal("回收中期记忆C: " + formatDecimal(stock.buyMemory(itemId, gameTime))), false);
        if (offerType.isBlank()) {
            List<SystemMarketOffer> offers = matchingOffers(itemText);
            if (!offers.isEmpty()) {
                source.sendSuccess(() -> Component.literal("已配置货架:"), false);
                for (SystemMarketOffer offer : offers) {
                    sendStockOfferSummary(source, stock, itemId, offer);
                }
            }
        } else {
            SystemMarketOffer offer = matchingOffer(offerType, itemText);
            if (offer == null) {
                source.sendSuccess(() -> Component.literal("货架: 未配置"), false);
            } else {
                sendStockOfferSummary(source, stock, itemId, offer);
            }
        }
        return 1;
    }

    private static int setSystemStock(CommandSourceStack source, String itemText, long count) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }
        SystemStockSavedData stock = SystemStockSavedData.get(source.getServer());
        stock.setActualStock(itemId, count);
        source.sendSuccess(() -> Component.literal("已将系统库存 " + itemId + " 设为 " + stock.actualStock(itemId) + "。"), true);
        return 1;
    }

    private static int addSystemStock(CommandSourceStack source, String itemText, long delta) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }
        SystemStockSavedData stock = SystemStockSavedData.get(source.getServer());
        stock.addActualStock(itemId, delta);
        source.sendSuccess(() -> Component.literal("已调整系统库存 " + itemId + " " + signed(delta) + "，当前库存 " + stock.actualStock(itemId) + "。"), true);
        return 1;
    }

    private static int clearSystemStock(CommandSourceStack source, String itemText) {
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }
        SystemStockSavedData stock = SystemStockSavedData.get(source.getServer());
        stock.clearActualStock(itemId);
        source.sendSuccess(() -> Component.literal("已清空系统库存 " + itemId + "。"), true);
        return 1;
    }

    private static void sendStockOfferSummary(CommandSourceStack source, SystemStockSavedData stock, ResourceLocation itemId, SystemMarketOffer offer) {
        String typeText = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS ? "系统出售" : "系统回收";
        String modeText = offer.pricing().mode().name();
        String flagsText = offer.pricing().flags().isBlank() ? "-" : offer.pricing().flags();
        String available = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS && offer.pricing().infiniteStock()
                ? "无限"
                : Long.toString(stock.actualStock(itemId));
        source.sendSuccess(() -> Component.literal(typeText + " | " + offer.id()
                + " | 模式=" + modeText
                + " | 标记=" + flagsText
                + " | 可用=" + available), false);
    }

    private static int systemQuote(CommandSourceStack source, String type, String itemText, int count) {
        String offerType = normalizeOfferType(type);
        if (offerType == null) {
            source.sendFailure(Component.literal("类型必须是 sell 或 buy。"));
            return 0;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }

        SystemMarketOffer offer = matchingOffer(offerType, itemText);
        if (offer == null) {
            String line = "quote_" + offerType + "|" + offerType + "|" + itemText + "||AUTO|0|1.0|0|0|";
            offer = SystemMarketOffer.parse(line);
        }
        if (offer == null) {
            source.sendFailure(Component.literal("无法为 " + itemText + " 生成报价。"));
            return 0;
        }

        SystemTradeQuote quote = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS
                ? SystemPriceService.quoteSellToPlayer(source.getServer(), offer, count)
                : SystemPriceService.quoteBuyFromPlayer(source.getServer(), offer, count);
        SystemPriceBreakdown breakdown = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS
                ? SystemPriceService.breakdownSellToPlayer(source.getServer(), offer)
                : SystemPriceService.breakdownBuyFromPlayer(source.getServer(), offer, count);
        long stock = SystemStockSavedData.get(source.getServer()).actualStock(itemId);
        String stockText = offer.pricing().infiniteStock() ? "无限" : Long.toString(stock);
        String modeText = offer.pricing().mode().name();
        String flagsText = offer.pricing().flags().isBlank() ? "-" : offer.pricing().flags();

        source.sendSuccess(() -> Component.literal("系统报价 " + itemId + " x" + count + ":"), false);
        source.sendSuccess(() -> Component.literal("类型: " + offerTypeName(offerType)), false);
        source.sendSuccess(() -> Component.literal("模式: " + modeText), false);
        source.sendSuccess(() -> Component.literal("标记: " + flagsText), false);
        source.sendSuccess(() -> Component.literal("库存: " + stockText), false);
        source.sendSuccess(() -> Component.literal("可交易: " + (quote.allowed() ? "是" : "否")), false);
        source.sendSuccess(() -> Component.literal("单价: " + quote.unitPricePreview()), false);
        source.sendSuccess(() -> Component.literal("总价: " + quote.totalPrice()), false);
        sendQuoteBreakdown(source, breakdown);
        if (!quote.allowed()) {
            source.sendSuccess(() -> Component.literal("原因: " + quote.message()), false);
        }
        return quote.allowed() ? 1 : 0;
    }

    private static int systemTestSell(CommandSourceStack source, String itemText, int count)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation itemId = ResourceLocation.tryParse(itemText);
        if (itemId == null) {
            source.sendFailure(Component.literal("无效物品 ID: " + itemText));
            return 0;
        }

        SystemMarketOffer offer = matchingOffer("buy_from_player", itemText);
        if (offer == null) {
            String line = "quote_buy_from_player|buy_from_player|" + itemText + "||AUTO|0|1.0|0|0|";
            offer = SystemMarketOffer.parse(line);
        }
        if (offer == null) {
            source.sendFailure(Component.literal("无法为 " + itemText + " 生成测试回收报价。"));
            return 0;
        }

        SystemTradeQuote quote = SystemPriceService.quoteBuyFromPlayer(source.getServer(), offer, count, player.getUUID());
        if (!quote.allowed()) {
            source.sendFailure(Component.literal("测试出售失败: " + quote.message()
                    + " | 预览单价 " + quote.unitPricePreview()));
            return 0;
        }

        MarketSavedData marketData = MarketSavedData.get(source.getServer());
        MarketEconomy.add(marketData, player.getUUID(), quote.totalPrice());
        EconomyLedgerSavedData.get(source.getServer()).recordSystemBuyPayout(itemId, quote.totalPrice());

        long gameTime = source.getServer().overworld().getGameTime();
        SystemPayoutBudgetSavedData.get(source.getServer()).recordPayout(
                player.getUUID(),
                EconomicPolicyRegistry.tierOf(itemId),
                quote.totalPrice(),
                gameTime);

        SystemStockSavedData stock = SystemStockSavedData.get(source.getServer());
        stock.recordSystemBuy(itemId, count, gameTime);
        long currentStock = stock.actualStock(itemId);
        long balance = MarketEconomy.balance(marketData, player.getUUID());

        source.sendSuccess(() -> Component.literal("[临时测试] 已向系统出售 " + itemId + " x" + count
                + "，均价 " + quote.unitPricePreview()
                + "，收入 " + quote.totalPrice()
                + "，系统库存 " + currentStock
                + "，当前余额 " + balance + "。"), true);
        return 1;
    }

    private static void sendQuoteBreakdown(CommandSourceStack source, SystemPriceBreakdown breakdown) {
        PriceProfile profile = breakdown.profile();
        source.sendSuccess(() -> Component.literal("价格来源: " + sourceName(profile.source())
                + "，参考价 " + profile.referencePrice()
                + "，系统动态价 " + breakdown.dynamicPrice()), false);
        source.sendSuccess(() -> Component.literal("模式修正: " + breakdown.modeExplanation()), false);
        source.sendSuccess(() -> Component.literal(breakdown.stockExplanation()), false);
        if (!profile.explanation().isBlank()) {
            source.sendSuccess(() -> Component.literal("来源说明: " + profile.explanation()), false);
        }
    }

    private static String sourceName(com.nekros.market.pricing.PriceSource source) {
        return switch (source) {
            case ANCHOR -> "锚定价格";
            case DERIVED -> "配方推导";
            case NATURAL -> "天然/掉落规则";
            case SYSTEM_OFFER -> "系统商品";
            case PLAYER_MARKET -> "玩家市场";
            case MIXED -> "混合价格";
            case UNKNOWN -> "未知";
        };
    }

    private static int addAutoSystemOffer(CommandSourceStack source, String type, String item, String category, String flags) {
        SystemMarketConfig.Result result = SystemMarketConfig.addPricedOffer(
                generatedOfferId(type, item, category),
                type,
                item,
                category,
                PriceMode.AUTO,
                0L,
                1.0D,
                0L,
                0L,
                flags);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int addAutoSystemOffersFromTag(CommandSourceStack source, String type, ResourceLocation tagId, String category,
            String flags) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        var tag = BuiltInRegistries.ITEM.getTag(tagKey);
        if (tag.isEmpty()) {
            source.sendFailure(Component.literal("找不到物品标签: #" + tagId));
            return 0;
        }

        List<SystemMarketConfig.OfferDraft> drafts = tag.get().stream()
                .map(Holder::value)
                .filter(item -> item != net.minecraft.world.item.Items.AIR)
                .map(item -> {
                    String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
                    String offerCategory = "sell_to_player".equals(type) ? category : "";
                    return new SystemMarketConfig.OfferDraft(
                            generatedOfferId(type, itemId, offerCategory),
                            type,
                            itemId,
                            offerCategory,
                            PriceMode.AUTO,
                            0L,
                            1.0D,
                            0L,
                            0L,
                            flags);
                })
                .toList();

        SystemMarketConfig.Result result = SystemMarketConfig.addPricedOffers(drafts);
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message() + " 标签: #" + tagId), true);
        return drafts.size();
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
            source.sendFailure(Component.literal("无效挂单 ID。可用 /market listings 查看并复制 ID。"));
            return null;
        }
    }

    private static String itemName(MarketListing listing) {
        return listing.item().getHoverName().getString();
    }

    private static String itemId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(context, name).getItem()).toString();
    }

    private static String normalizeOfferType(String type) {
        if ("sell".equalsIgnoreCase(type)
                || "sell_to_player".equalsIgnoreCase(type)
                || "system_sells".equalsIgnoreCase(type)) {
            return "sell_to_player";
        }
        if ("buy".equalsIgnoreCase(type)
                || "buy_from_player".equalsIgnoreCase(type)
                || "system_buys".equalsIgnoreCase(type)) {
            return "buy_from_player";
        }
        return null;
    }

    private static String offerTypeName(String offerType) {
        return "sell_to_player".equals(offerType) ? "sell/系统出售" : "buy/系统回收";
    }

    private static SystemMarketOffer matchingOffer(String offerType, String itemText) {
        for (SystemMarketOffer offer : SystemMarketOffer.configOffers()) {
            String type = offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS ? "sell_to_player" : "buy_from_player";
            if (type.equals(offerType) && BuiltInRegistries.ITEM.getKey(offer.item().getItem()).toString().equals(itemText)) {
                return offer;
            }
        }
        return null;
    }

    private static List<SystemMarketOffer> matchingOffers(String itemText) {
        return SystemMarketOffer.configOffers().stream()
                .filter(offer -> BuiltInRegistries.ITEM.getKey(offer.item().getItem()).toString().equals(itemText))
                .toList();
    }

    private static String generatedOfferId(String type, String itemId, String category) {
        String prefix = "sell_to_player".equals(type) ? "shelf" : "buyback";
        String raw = prefix + "_" + category + "_" + itemId;
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                id.append(Character.toLowerCase(c));
            } else {
                id.append('_');
            }
        }
        return id.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    private static String signed(long value) {
        return value >= 0L ? "+" + value : Long.toString(value);
    }

    private static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static long multiplyClamped(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String topItemMoney(List<EconomyLedgerSavedData.ItemMoney> items) {
        if (items.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (EconomyLedgerSavedData.ItemMoney item : items) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(item.itemId()).append("=").append(item.amount());
            first = false;
        }
        return builder.toString();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
