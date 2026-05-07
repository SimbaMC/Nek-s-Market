package com.nekros.market.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.market.MarketPriceIndexService;
import com.nekros.market.pricing.market.MarketTradeRecord;
import com.nekros.market.storage.EconomySavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PriceAdminCommands {
    private PriceAdminCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> priceCommand(CommandBuildContext buildContext) {
        return Commands.literal("price")
                .then(Commands.literal("hand")
                        .executes(context -> priceHand(context.getSource())))
                .then(Commands.literal("item")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> priceItem(
                                        context.getSource(),
                                        itemId(context, "item")))))
                .then(Commands.literal("history")
                        .then(Commands.literal("hand")
                                .executes(context -> historyHand(context.getSource())))
                        .then(Commands.literal("item")
                                .then(Commands.argument("item", ItemArgument.item(buildContext))
                                        .executes(context -> historyItem(
                                                context.getSource(),
                                                itemId(context, "item"))))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource())));
    }

    private static int priceHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Main hand is empty."));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        sendProfile(source, PriceResolver.resolve(source.getServer(), itemId));
        return 1;
    }

    private static int priceItem(CommandSourceStack source, ResourceLocation itemId) {
        sendProfile(source, PriceResolver.resolve(source.getServer(), itemId));
        return 1;
    }

    private static int historyHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Main hand is empty."));
            return 0;
        }
        sendHistory(source, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return 1;
    }

    private static int historyItem(CommandSourceStack source, ResourceLocation itemId) {
        sendHistory(source, itemId);
        return 1;
    }

    private static void sendProfile(CommandSourceStack source, PriceProfile profile) {
        source.sendSuccess(() -> Component.literal("Item: " + profile.itemId()), false);
        source.sendSuccess(() -> Component.literal("Source: " + profile.source()), false);
        source.sendSuccess(() -> Component.literal("Confidence: " + profile.confidence()), false);
        source.sendSuccess(() -> Component.literal("Trade Level: " + profile.tradeLevel()), false);
        source.sendSuccess(() -> Component.literal("Floor: " + profile.floorPrice()), false);
        source.sendSuccess(() -> Component.literal("Derived: " + profile.derivedPrice()), false);
        source.sendSuccess(() -> Component.literal("Market: " + profile.marketPrice()), false);
        source.sendSuccess(() -> Component.literal("Reference: " + profile.referencePrice()), false);
        source.sendSuccess(() -> Component.literal("System Buy: " + profile.systemBuyPrice()), false);
        source.sendSuccess(() -> Component.literal("System Sell: " + profile.systemSellPrice()), false);
        source.sendSuccess(() -> Component.literal("Explanation: " + profile.explanation()), false);
    }

    private static void sendHistory(CommandSourceStack source, ResourceLocation itemId) {
        int tradeCount = MarketPriceIndexService.recentTradeCount(source.getServer(), itemId);
        long vwap = MarketPriceIndexService.recentVWAP(source.getServer(), itemId);
        double confidence = MarketPriceIndexService.confidence(source.getServer(), itemId);
        source.sendSuccess(() -> Component.literal("Item: " + itemId), false);
        source.sendSuccess(() -> Component.literal("Recent Trades: " + tradeCount), false);
        source.sendSuccess(() -> Component.literal("Recent VWAP: " + vwap), false);
        source.sendSuccess(() -> Component.literal("Market Confidence: " + String.format("%.2f", confidence)), false);

        var records = EconomySavedData.get(source.getServer()).recentTradesFor(itemId, 5);
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No player-market trades recorded yet."), false);
            return;
        }
        source.sendSuccess(() -> Component.literal("Last " + records.size() + " trade(s):"), false);
        for (MarketTradeRecord record : records) {
            source.sendSuccess(() -> Component.literal("unit=" + record.unitPrice()
                    + ", count=" + record.count()
                    + ", total=" + record.totalPrice()
                    + ", gameTime=" + record.gameTime()), false);
        }
    }

    private static int reload(CommandSourceStack source) {
        PriceRegistry.reload();
        source.sendSuccess(() -> Component.literal("Reloaded Nek's Market pricing registry."), true);
        return 1;
    }

    private static ResourceLocation itemId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(context, name).getItem());
    }
}
