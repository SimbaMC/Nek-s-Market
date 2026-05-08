package com.nekros.market.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.derived.DerivedPriceService;
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
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("coverage")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> coverage(context.getSource())))
                .then(Commands.literal("unresolved")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> unresolved(context.getSource(), 20))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                .executes(context -> unresolved(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")))));
    }

    private static int priceHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("主手为空。"));
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
            source.sendFailure(Component.literal("主手为空。"));
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
        source.sendSuccess(() -> Component.literal("物品: " + profile.itemId()), false);
        source.sendSuccess(() -> Component.literal("来源: " + sourceName(profile.source())), false);
        source.sendSuccess(() -> Component.literal("置信度: " + profile.confidence()), false);
        source.sendSuccess(() -> Component.literal("交易级别: " + profile.tradeLevel()), false);
        source.sendSuccess(() -> Component.literal("底价: " + profile.floorPrice()), false);
        source.sendSuccess(() -> Component.literal("推导价: " + profile.derivedPrice()), false);
        source.sendSuccess(() -> Component.literal("市场价: " + profile.marketPrice()), false);
        source.sendSuccess(() -> Component.literal("参考价: " + profile.referencePrice()), false);
        source.sendSuccess(() -> Component.literal("系统回收价: " + profile.systemBuyPrice()), false);
        source.sendSuccess(() -> Component.literal("系统售价: " + profile.systemSellPrice()), false);
        source.sendSuccess(() -> Component.literal("说明: " + profile.explanation()), false);
    }

    private static String sourceName(PriceSource source) {
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

    private static void sendHistory(CommandSourceStack source, ResourceLocation itemId) {
        int tradeCount = MarketPriceIndexService.recentTradeCount(source.getServer(), itemId);
        long vwap = MarketPriceIndexService.recentVWAP(source.getServer(), itemId);
        double confidence = MarketPriceIndexService.confidence(source.getServer(), itemId);
        source.sendSuccess(() -> Component.literal("物品: " + itemId), false);
        source.sendSuccess(() -> Component.literal("近期成交数: " + tradeCount), false);
        source.sendSuccess(() -> Component.literal("近期成交均价: " + vwap), false);
        source.sendSuccess(() -> Component.literal("市场置信度: " + String.format("%.2f", confidence)), false);

        var records = EconomySavedData.get(source.getServer()).recentTradesFor(itemId, 5);
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无玩家市场成交记录。"), false);
            return;
        }
        source.sendSuccess(() -> Component.literal("最近 " + records.size() + " 笔成交:"), false);
        for (MarketTradeRecord record : records) {
            source.sendSuccess(() -> Component.literal("单价=" + record.unitPrice()
                    + ", 数量=" + record.count()
                    + ", 总价=" + record.totalPrice()
                    + ", 游戏时间=" + record.gameTime()), false);
        }
    }

    private static int reload(CommandSourceStack source) {
        PriceRegistry.reload();
        source.sendSuccess(() -> Component.literal("已重载 Nek's Market 定价注册表。已跳过全量预热，避免卡住服务器。"), true);
        return 1;
    }

    private static int coverage(CommandSourceStack source) {
        DerivedPriceService.CoverageReport report = DerivedPriceService.fastCoverage(source.getServer(), 0);
        source.sendSuccess(() -> Component.literal("价格覆盖率:"), false);
        source.sendSuccess(() -> Component.literal("候选物: " + report.candidateCount()
                + "，可定价: " + report.resolvedCount()
                + "，未知: " + report.unresolvedCount()
                + "，缓存: " + report.cachedCount()), false);
        source.sendSuccess(() -> Component.literal("覆盖率: " + coveragePercent(report) + "%"), false);
        source.sendSuccess(() -> Component.literal("来源统计: " + sourceCounts(report)), false);
        return report.resolvedCount();
    }

    private static int unresolved(CommandSourceStack source, int limit) {
        DerivedPriceService.CoverageReport report = DerivedPriceService.fastCoverage(source.getServer(), limit);
        source.sendSuccess(() -> Component.literal("无法定价样本 " + report.unresolvedSamples().size()
                + "/" + report.unresolvedCount() + ":"), false);
        if (report.unresolvedSamples().isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无无法定价的候选物。"), false);
            return 1;
        }
        for (ResourceLocation itemId : report.unresolvedSamples()) {
            source.sendSuccess(() -> Component.literal("- " + itemId), false);
        }
        return report.unresolvedSamples().size();
    }

    private static String coveragePercent(DerivedPriceService.CoverageReport report) {
        if (report.candidateCount() <= 0) {
            return "0.00";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", report.resolvedCount() * 100.0D / report.candidateCount());
    }

    private static String sourceCounts(DerivedPriceService.CoverageReport report) {
        if (report.sourceCounts().isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (PriceSource source : PriceSource.values()) {
            int count = report.sourceCounts().getOrDefault(source, 0);
            if (count <= 0) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(sourceName(source)).append("=").append(count);
            first = false;
        }
        return builder.toString();
    }

    private static ResourceLocation itemId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(context, name).getItem());
    }
}
