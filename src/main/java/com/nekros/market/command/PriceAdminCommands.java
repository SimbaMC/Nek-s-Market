package com.nekros.market.command;

import java.util.Comparator;
import java.util.Map;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;
import com.nekros.market.pricing.derived.DerivedPriceService;
import com.nekros.market.pricing.derived.PriceWarmupService;
import com.nekros.market.pricing.market.MarketPriceIndexService;
import com.nekros.market.pricing.market.MarketTradeRecord;
import com.nekros.market.pricing.policy.EconomicPolicy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry.BuybackListDecision;
import com.nekros.market.pricing.policy.EconomicTier;
import com.nekros.market.pricing.system.SystemBuyPressure;
import com.nekros.market.pricing.system.SystemPriceService;
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
                .executes(context -> priceHelp(context.getSource()))
                .then(Commands.literal("item")
                        .executes(context -> priceHand(context.getSource()))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> priceItem(
                                        context.getSource(),
                                        itemId(context, "item")))))
                .then(Commands.literal("history")
                        .executes(context -> historyHand(context.getSource()))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> historyItem(
                                        context.getSource(),
                                        itemId(context, "item")))))
                .then(Commands.literal("explain")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> explainHand(context.getSource()))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> explainItem(
                                        context.getSource(),
                                        itemId(context, "item")))))
                .then(policyCommand(buildContext))
                .then(Commands.literal("curve")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> curveHand(context.getSource(), 4096))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> curveItem(
                                        context.getSource(),
                                        itemId(context, "item"),
                                        4096))
                                .then(Commands.argument("maxCount", IntegerArgumentType.integer(1, 1000000))
                                        .executes(context -> curveItem(
                                                context.getSource(),
                                                itemId(context, "item"),
                                                IntegerArgumentType.getInteger(context, "maxCount"))))))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("report")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> report(context.getSource(), 20))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                .executes(context -> report(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("audit")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> auditBuyback(context.getSource(), 30))
                        .then(Commands.literal("allowed")
                                .executes(context -> auditBuyback(context.getSource(), 30))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> auditBuyback(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(Commands.literal("blocked")
                                .executes(context -> auditBlockedBuyback(context.getSource(), 30))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                        .executes(context -> auditBlockedBuyback(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                .executes(context -> auditBuyback(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("recipes")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> recipeTypes(context.getSource(), 20))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 200))
                                .executes(context -> recipeTypes(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")))))
                .then(Commands.literal("warmup")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(context -> warmupStart(context.getSource(), 8))
                                .then(Commands.argument("itemsPerTick", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> warmupStart(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "itemsPerTick")))))
                        .then(Commands.literal("status")
                                .executes(context -> warmupStatus(context.getSource())))
                        .then(Commands.literal("cancel")
                                .executes(context -> warmupCancel(context.getSource()))));
    }

    private static int priceHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("价格诊断命令:"), false);
        source.sendSuccess(() -> Component.literal("/market price item [物品] - 查看价格，不填则读取主手"), false);
        source.sendSuccess(() -> Component.literal("/market price history [物品] - 查看近期成交，不填则读取主手"), false);
        source.sendSuccess(() -> Component.literal("/market price explain [物品] - 查看价格来源与推导解释，不填则读取主手"), false);
        source.sendSuccess(() -> Component.literal("/market price policy [物品] - 查看经济分级，不填则读取主手"), false);
        source.sendSuccess(() -> Component.literal("/market price report [数量] - 查看覆盖率和未定价样本"), false);
        source.sendSuccess(() -> Component.literal("/market price reload - 重载定价配置"), false);
        source.sendSuccess(() -> Component.literal("/market price warmup start|status|cancel - 后台预热价格图"), false);
        source.sendSuccess(() -> Component.literal("/market price recipes [数量] - 查看配方类型和通用策略"), false);
        return 1;
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

    private static LiteralArgumentBuilder<CommandSourceStack> policyCommand(CommandBuildContext buildContext) {
        return Commands.literal("policy")
                .executes(context -> policyHand(context.getSource()))
                .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .executes(context -> policyItem(
                                context.getSource(),
                                itemId(context, "item"))));
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

    private static int explainHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("主手为空。"));
            return 0;
        }
        return explainItem(source, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static int explainItem(CommandSourceStack source, ResourceLocation itemId) {
        PriceProfile profile = PriceResolver.resolve(source.getServer(), itemId);
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        String sourceLabel = profile.source() == null ? "未知" : sourceName(profile.source());
        String tierLabel = policy == null || policy.tier() == null ? "UNKNOWN" : policy.tier().name();
        String confidenceLabel = profile.confidence() == null ? "NONE" : profile.confidence().name();
        String tradeLevelLabel = profile.tradeLevel() == null ? "PLAYER_MARKET_ONLY" : profile.tradeLevel().name();
        source.sendSuccess(() -> Component.literal("价格解释: " + itemId), false);
        source.sendSuccess(() -> Component.literal("来源: " + sourceLabel
                + "，分级: " + tierLabel
                + "，置信度: " + confidenceLabel
                + "，交易级别: " + tradeLevelLabel), false);
        source.sendSuccess(() -> Component.literal("参考价: " + profile.referencePrice()
                + "，系统回收基准: " + profile.systemBuyPrice()
                + "，系统出售基准: " + profile.systemSellPrice()), false);
        String explanation = profile.explanation();
        if (explanation == null || explanation.isBlank()) {
            source.sendSuccess(() -> Component.literal("推导说明: 暂无（可能来自固定锚点或未定价）。"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("推导说明: " + explanation), false);
        sendStructuredRecipeExplanation(source, explanation, profile.referencePrice());
        return 1;
    }

    private static void sendStructuredRecipeExplanation(CommandSourceStack source, String explanation, long referencePrice) {
        String baseExplanation = explanation;
        int mixedMarker = explanation.indexOf(" 已混合近期玩家交易均价");
        if (mixedMarker > 0) {
            baseExplanation = explanation.substring(0, mixedMarker).trim();
            String mixedPart = explanation.substring(mixedMarker + 1).trim();
            source.sendSuccess(() -> Component.literal("市场混合说明: " + mixedPart), false);
        }

        ParsedRecipeExplanation parsed = parseRecipeExplanation(baseExplanation);
        if (parsed == null) {
            return;
        }

        source.sendSuccess(() -> Component.literal("完整配方解释:"), false);
        source.sendSuccess(() -> Component.literal("选中配方: " + parsed.recipeKind() + " " + parsed.recipeId()), false);
        if (!parsed.ingredients().isEmpty()) {
            source.sendSuccess(() -> Component.literal("材料明细:"), false);
            for (ParsedIngredient ingredient : parsed.ingredients()) {
                long unitPrice = ingredient.count() <= 0 ? ingredient.totalPrice() : ingredient.totalPrice() / ingredient.count();
                source.sendSuccess(() -> Component.literal("- " + ingredient.label()
                        + " x" + ingredient.count()
                        + "，总价=" + ingredient.totalPrice()
                        + "，单价≈" + unitPrice), false);
            }
        }
        if (parsed.materialTotal() != null) {
            source.sendSuccess(() -> Component.literal("材料合计: " + parsed.materialTotal()), false);
        }
        if (parsed.processingFee() != null) {
            source.sendSuccess(() -> Component.literal("加工费: " + parsed.processingFee()), false);
        }
        if (parsed.cookingFee() != null) {
            source.sendSuccess(() -> Component.literal("烧制费: " + parsed.cookingFee()), false);
        }
        if (parsed.timeFee() != null) {
            source.sendSuccess(() -> Component.literal("时间费: " + parsed.timeFee()), false);
        }
        if (parsed.markupPercent() != null) {
            source.sendSuccess(() -> Component.literal("加成: " + parsed.markupPercent() + "%"), false);
        }
        if (parsed.resultCount() > 0) {
            source.sendSuccess(() -> Component.literal("输出数量折算: /" + parsed.resultCount()
                    + "，当前参考单价=" + referencePrice), false);
        }
    }

    private static ParsedRecipeExplanation parseRecipeExplanation(String explanation) {
        if (explanation == null || explanation.isBlank() || !explanation.startsWith("由")) {
            return null;
        }
        int recipeTypeEnd = explanation.indexOf("配方 ");
        int deriveStart = explanation.indexOf(" 推导:");
        int outputStart = explanation.lastIndexOf(" -> /");
        if (recipeTypeEnd <= 1 || deriveStart <= recipeTypeEnd || outputStart <= deriveStart) {
            return null;
        }

        String recipeKind = explanation.substring(1, recipeTypeEnd);
        String recipeId = explanation.substring(recipeTypeEnd + "配方 ".length(), deriveStart).trim();
        String body = explanation.substring(deriveStart + " 推导:".length(), outputStart).trim();
        int resultCount = parseInt(explanation.substring(outputStart + " -> /".length()).trim(), 0);

        String ingredientPart = body;
        String metricsPart = "";
        int metricStart = body.indexOf("; ");
        if (metricStart >= 0) {
            ingredientPart = body.substring(0, metricStart).trim();
            metricsPart = body.substring(metricStart + 2).trim();
        }

        java.util.List<ParsedIngredient> ingredients = parseIngredients(ingredientPart);
        Map<String, String> metrics = parseMetrics(metricsPart);
        Long materialTotal = parseLong(metrics.get("材料"), null);
        Long processingFee = parseLong(metrics.get("加工"), null);
        Long cookingFee = parseLong(metrics.get("烧制"), null);
        Long timeFee = parseLong(metrics.get("时间"), null);
        Integer markupPercent = parsePercent(metrics.get("加成"));
        return new ParsedRecipeExplanation(
                recipeKind,
                recipeId,
                ingredients,
                materialTotal,
                processingFee,
                cookingFee,
                timeFee,
                markupPercent,
                resultCount);
    }

    private static java.util.List<ParsedIngredient> parseIngredients(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<ParsedIngredient> ingredients = new java.util.ArrayList<>();
        for (String token : text.split(",\\s*")) {
            String item = token.trim();
            if (item.isEmpty()) {
                continue;
            }
            int equalsIndex = item.lastIndexOf('=');
            if (equalsIndex <= 0 || equalsIndex >= item.length() - 1) {
                continue;
            }
            String left = item.substring(0, equalsIndex).trim();
            long totalPrice = parseLong(item.substring(equalsIndex + 1).trim(), -1L);
            if (totalPrice < 0L) {
                continue;
            }
            int count = 1;
            String label = left;
            int countIndex = left.lastIndexOf(" x");
            if (countIndex > 0) {
                count = Math.max(1, parseInt(left.substring(countIndex + 2).trim(), 1));
                label = left.substring(0, countIndex).trim();
            }
            ingredients.add(new ParsedIngredient(label, count, totalPrice));
        }
        return java.util.List.copyOf(ingredients);
    }

    private static Map<String, String> parseMetrics(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, String> metrics = new java.util.LinkedHashMap<>();
        for (String token : text.split(",\\s*")) {
            int equalsIndex = token.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex >= token.length() - 1) {
                continue;
            }
            String key = token.substring(0, equalsIndex).trim();
            String value = token.substring(equalsIndex + 1).trim();
            metrics.put(key, value);
        }
        return metrics;
    }

    private static Integer parsePercent(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.endsWith("%") ? text.substring(0, text.length() - 1).trim() : text.trim();
        return parseInt(normalized, null);
    }

    private static Integer parseInt(String text, Integer fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Long parseLong(String text, Long fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int policyHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("主手为空。"));
            return 0;
        }
        return policyItem(source, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static int policyItem(CommandSourceStack source, ResourceLocation itemId) {
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        PriceProfile profile = PriceResolver.resolve(source.getServer(), itemId);
        source.sendSuccess(() -> Component.literal("物品: " + itemId), false);
        source.sendSuccess(() -> Component.literal("经济分级: " + policy.tier()), false);
        source.sendSuccess(() -> Component.literal("系统回收策略: " + yesNo(policy.systemBuyAllowed())), false);
        source.sendSuccess(() -> Component.literal("实际可回收: " + yesNo(SystemPriceService.allowsAutomaticBuyback(itemId, profile))), false);
        source.sendSuccess(() -> Component.literal("系统默认出售: " + yesNo(policy.systemSellAllowedByDefault())), false);
        source.sendSuccess(() -> Component.literal("压力模型: " + policy.pressureModel()), false);
        source.sendSuccess(() -> Component.literal("基础买卖比例: buy="
                + fmt(EconomicPolicyRegistry.buyRatio(itemId))
                + ", sell=" + fmt(EconomicPolicyRegistry.sellRatio(itemId))), false);
        source.sendSuccess(() -> Component.literal("参数: alpha=" + fmt(policy.longAlpha())
                + ", gamma=" + fmt(policy.gamma())
                + ", beta=" + fmt(policy.memoryBeta())
                + ", lambda=" + fmt(policy.memoryLambda())
                + ", delta=" + fmt(policy.tradeDelta())
                + ", minBuyRatio=" + fmt(policy.minBuyRatio())), false);
        source.sendSuccess(() -> Component.literal("说明: " + policy.explanation()), false);
        return 1;
    }

    private static int curveHand(CommandSourceStack source, int maxCount)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("主手为空。"));
            return 0;
        }
        return curveItem(source, BuiltInRegistries.ITEM.getKey(stack.getItem()), maxCount);
    }

    private static int curveItem(CommandSourceStack source, ResourceLocation itemId, int maxCount) {
        PriceProfile profile = PriceResolver.resolve(source.getServer(), itemId);
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        source.sendSuccess(() -> Component.literal("回收压力曲线: " + itemId), false);
        source.sendSuccess(() -> Component.literal("经济分级: " + policy.tier()
                + "，基础回收价: " + profile.systemBuyPrice()
                + "，可回收: " + yesNo(SystemPriceService.allowsAutomaticBuyback(itemId, profile))), false);
        if (!SystemPriceService.allowsAutomaticBuyback(itemId, profile)) {
            source.sendSuccess(() -> Component.literal("该物品当前不能自动回收，原因可用 /market price policy 查看。"), false);
            return 0;
        }

        int[] counts = curveCounts(maxCount);
        for (int count : counts) {
            SystemBuyPressure.Quote quote = SystemBuyPressure.quote(source.getServer(), itemId, profile.systemBuyPrice(), count);
            source.sendSuccess(() -> Component.literal("x" + count
                    + " -> 均价 " + quote.averageUnitPrice()
                    + "，总价 " + quote.totalPrice()
                    + "，倍率 " + fmt(quote.averageRatio())), false);
        }
        SystemBuyPressure.Quote maxQuote = SystemBuyPressure.quote(source.getServer(), itemId, profile.systemBuyPrice(), maxCount);
        source.sendSuccess(() -> Component.literal(maxQuote.explanation()), false);
        return counts.length;
    }

    private static int[] curveCounts(int maxCount) {
        int[] base = {1, 16, 64, 256, 1024, 4096, 16384, 65536, 262144, 1000000};
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        for (int value : base) {
            if (value <= maxCount) {
                counts.add(value);
            }
        }
        if (counts.isEmpty() || counts.get(counts.size() - 1) != maxCount) {
            counts.add(maxCount);
        }
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void sendProfile(CommandSourceStack source, PriceProfile profile) {
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(profile.itemId());
        long pressureBuyPrice = profile.systemBuyPrice() > 0L
                ? SystemPriceService.dynamicBuyPriceForStock(source.getServer(), profile.itemId(), profile.systemBuyPrice(), 0)
                : 0L;
        source.sendSuccess(() -> Component.literal("经济分级: " + policy.tier()), false);
        source.sendSuccess(() -> Component.literal("当前回收价(含压力): " + pressureBuyPrice), false);
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

    private static String usageText(PriceProfile profile) {
        return switch (profile.tradeLevel()) {
            case BLOCKED -> "禁止交易";
            case PLAYER_MARKET_ONLY -> "玩家交易行";
            case REFERENCE_ONLY -> "仅参考";
            case SYSTEM_BUY_ONLY -> "可系统回收";
            case SYSTEM_BUY_AND_SELL -> "可系统回收，有系统售价基准；实际出售仍由货架控制";
        };
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
        double effectiveTradeCount = MarketPriceIndexService.effectiveTradeCount(source.getServer(), itemId);
        int participants = MarketPriceIndexService.uniqueParticipantCount(source.getServer(), itemId);
        long vwap = MarketPriceIndexService.recentVWAP(source.getServer(), itemId);
        double confidence = MarketPriceIndexService.confidence(source.getServer(), itemId);
        source.sendSuccess(() -> Component.literal("时间衰减成交数: "
                + String.format(java.util.Locale.ROOT, "%.2f", effectiveTradeCount)), false);
        source.sendSuccess(() -> Component.literal("独立参与者: " + participants), false);
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

    private static String yesNo(boolean value) {
        return value ? "允许" : "禁止";
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private static int reload(CommandSourceStack source) {
        PriceRegistry.reload();
        source.sendSuccess(() -> Component.literal("已重载 Nek's Market 定价注册表与经济策略。已跳过全量预热，避免卡住服务器。"), true);
        return 1;
    }

    private static int report(CommandSourceStack source, int limit) {
        PriceWarmupService.WarmupSnapshot snapshot = PriceWarmupService.status(source.getServer());
        if (hasWarmupSnapshot(snapshot)) {
            source.sendSuccess(() -> Component.literal("价格覆盖率(后台预热快照):"), false);
            source.sendSuccess(() -> Component.literal("状态: " + snapshot.state()
                    + "，候选物: " + snapshot.total()
                    + "，已处理: " + snapshot.processed()
                    + "，剩余: " + snapshot.remaining()), false);
            source.sendSuccess(() -> Component.literal("可定价: " + snapshot.resolved()
                    + "，未知: " + snapshot.unresolved()
                    + "，缓存: " + snapshot.cachedCount()), false);
            source.sendSuccess(() -> Component.literal("已处理覆盖率: "
                    + coveragePercent(snapshot.processed(), snapshot.resolved()) + "%"), false);
            source.sendSuccess(() -> Component.literal("来源统计: " + sourceCounts(snapshot.sourceCounts())), false);
            source.sendSuccess(() -> Component.literal("交易级别统计: " + tradeLevelCounts(snapshot.tradeLevelCounts())), false);
            int sampleLimit = Math.min(Math.max(0, limit), snapshot.unresolvedSamples().size());
            source.sendSuccess(() -> Component.literal("未定价样本 " + sampleLimit
                    + "/" + snapshot.unresolved() + ":"), false);
            if (sampleLimit > 0) {
                for (int i = 0; i < sampleLimit; i++) {
                    ResourceLocation itemId = snapshot.unresolvedSamples().get(i);
                    source.sendSuccess(() -> Component.literal("- " + itemId), false);
                }
            }
            return snapshot.resolved();
        }

        DerivedPriceService.CoverageReport report = DerivedPriceService.fastCoverage(source.getServer(), limit);
        source.sendSuccess(() -> Component.literal("价格覆盖率:"), false);
        source.sendSuccess(() -> Component.literal("候选物: " + report.candidateCount()
                + "，可定价: " + report.resolvedCount()
                + "，未知: " + report.unresolvedCount()
                + "，缓存: " + report.cachedCount()), false);
        source.sendSuccess(() -> Component.literal("覆盖率: " + coveragePercent(report) + "%"), false);
        source.sendSuccess(() -> Component.literal("来源统计: " + sourceCounts(report)), false);
        source.sendSuccess(() -> Component.literal("交易级别统计: " + tradeLevelCounts(report.tradeLevelCounts())), false);
        source.sendSuccess(() -> Component.literal("无法定价样本 " + report.unresolvedSamples().size()
                + "/" + report.unresolvedCount() + ":"), false);
        if (!report.unresolvedSamples().isEmpty()) {
            for (ResourceLocation itemId : report.unresolvedSamples()) {
                source.sendSuccess(() -> Component.literal("- " + itemId), false);
            }
        }
        return report.resolvedCount();
    }

    private static boolean hasWarmupSnapshot(PriceWarmupService.WarmupSnapshot snapshot) {
        return snapshot != null && (snapshot.total() > 0 || snapshot.processed() > 0);
    }

    private static int auditBuyback(CommandSourceStack source, int limit) {
        var entries = BuiltInRegistries.ITEM.entrySet().stream()
                .map(entry -> auditEntry(source, entry.getKey().location()))
                .filter(AuditEntry::autoBuyback)
                .sorted(Comparator
                        .comparingInt(AuditEntry::riskScore).reversed()
                        .thenComparing(AuditEntry::itemId))
                .limit(limit)
                .toList();

        source.sendSuccess(() -> Component.literal("自动回收风险审计: " + entries.size() + " 项"), false);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("没有发现当前会被系统自动回收的风险样本。"), false);
            return 0;
        }
        sendAuditGroup(source, "高风险", entries, entry -> entry.riskScore() >= 6);
        sendAuditGroup(source, "中风险", entries, entry -> entry.riskScore() >= 3 && entry.riskScore() < 6);
        sendAuditGroup(source, "低风险", entries, entry -> entry.riskScore() < 3);
        return entries.size();
    }

    private static int auditBlockedBuyback(CommandSourceStack source, int limit) {
        var entries = BuiltInRegistries.ITEM.entrySet().stream()
                .map(entry -> auditEntry(source, entry.getKey().location()))
                .filter(entry -> !entry.autoBuyback())
                .filter(entry -> entry.referencePrice() > 0L
                        || entry.listDecision() != BuybackListDecision.NONE
                        || entry.tier() == EconomicTier.UNKNOWN
                        || entry.tier() == EconomicTier.BOSS_DROP
                        || entry.tier() == EconomicTier.PROGRESSION_LOCKED
                        || entry.tier() == EconomicTier.PLAYER_MARKET_ONLY)
                .sorted(Comparator
                        .comparingInt(AuditEntry::blockPriority).reversed()
                        .thenComparing(Comparator.comparingLong(AuditEntry::referencePrice).reversed())
                        .thenComparing(AuditEntry::itemId))
                .limit(limit)
                .toList();

        source.sendSuccess(() -> Component.literal("automatic buyback blocked audit: " + entries.size() + " items"), false);
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No notable blocked buyback items found."), false);
            return 0;
        }
        sendAuditGroup(source, "explicit deny", entries, entry -> entry.listDecision() == BuybackListDecision.DENY);
        sendAuditGroup(source, "allow list but still blocked", entries, entry -> entry.listDecision() == BuybackListDecision.ALLOW);
        sendAuditGroup(source, "protected tier", entries,
                entry -> entry.tier() == EconomicTier.BOSS_DROP
                        || entry.tier() == EconomicTier.PROGRESSION_LOCKED
                        || entry.tier() == EconomicTier.PLAYER_MARKET_ONLY);
        sendAuditGroup(source, "unknown or low confidence", entries,
                entry -> entry.tier() == EconomicTier.UNKNOWN
                        || entry.confidence() == com.nekros.market.pricing.PriceConfidence.LOW
                        || entry.confidence() == com.nekros.market.pricing.PriceConfidence.NONE);
        return entries.size();
    }

    private static void sendAuditGroup(CommandSourceStack source, String title, java.util.List<AuditEntry> entries,
            java.util.function.Predicate<AuditEntry> predicate) {
        java.util.List<AuditEntry> group = entries.stream().filter(predicate).toList();
        if (group.isEmpty()) {
            return;
        }
        source.sendSuccess(() -> Component.literal("[" + title + "] " + group.size() + " 项"), false);
        for (AuditEntry entry : group) {
            source.sendSuccess(() -> Component.literal(entry.itemId()
                    + " | risk=" + entry.riskScore()
                    + " | tier=" + entry.tier()
                    + " | buy=" + entry.buyPrice()
                    + " | ref=" + entry.referencePrice()
                    + " | source=" + entry.source()
                    + " | confidence=" + entry.confidence()
                    + " | list=" + entry.listDecision()
                    + " | " + entry.reason()), false);
        }
    }

    private static AuditEntry auditEntry(CommandSourceStack source, ResourceLocation itemId) {
        PriceProfile profile = PriceResolver.resolve(source.getServer(), itemId);
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        boolean autoBuyback = SystemPriceService.allowsAutomaticBuyback(itemId, profile);
        BuybackListDecision listDecision = EconomicPolicyRegistry.buybackListDecision(itemId);
        int risk = 0;
        StringBuilder reason = new StringBuilder();
        if (listDecision == BuybackListDecision.ALLOW) {
            risk += 2;
            reason.append("explicit_allow ");
        } else if (listDecision == BuybackListDecision.DENY) {
            reason.append("explicit_deny ");
        }
        if (!"minecraft".equals(itemId.getNamespace())) {
            risk += 4;
            reason.append("modded ");
        }
        if (profile.systemBuyPrice() >= 1000L) {
            risk += 3;
            reason.append("high_price ");
        } else if (profile.systemBuyPrice() >= 300L) {
            risk += 1;
            reason.append("mid_price ");
        }
        if (policy.tier() == EconomicTier.RARE_RESOURCE) {
            risk += 4;
            reason.append("rare ");
        } else if (policy.tier() == EconomicTier.UNKNOWN) {
            risk += 3;
            reason.append("unknown_tier ");
        } else if (policy.tier() == EconomicTier.INDUSTRIAL_RENEWABLE) {
            risk += 2;
            reason.append("farmable ");
        }
        switch (profile.confidence()) {
            case LOW -> {
                risk += 3;
                reason.append("low_confidence ");
            }
            case NONE -> {
                risk += 5;
                reason.append("no_confidence ");
            }
            default -> {
            }
        }
        if (profile.source() == PriceSource.MIXED || profile.source() == PriceSource.PLAYER_MARKET) {
            risk += 2;
            reason.append("market_influenced ");
        }
        if (reason.length() == 0) {
            reason.append("normal");
        }
        return new AuditEntry(
                itemId.toString(),
                autoBuyback,
                risk,
                blockPriority(profile, policy, listDecision),
                policy.tier(),
                profile.systemBuyPrice(),
                profile.referencePrice(),
                profile.source(),
                profile.confidence(),
                listDecision,
                reason.toString().trim());
    }

    private static int blockPriority(PriceProfile profile, EconomicPolicy policy, BuybackListDecision listDecision) {
        int priority = 0;
        if (listDecision == BuybackListDecision.DENY) {
            priority += 100;
        } else if (listDecision == BuybackListDecision.ALLOW) {
            priority += 80;
        }
        if (policy.tier() == EconomicTier.BOSS_DROP || policy.tier() == EconomicTier.PROGRESSION_LOCKED) {
            priority += 60;
        } else if (policy.tier() == EconomicTier.PLAYER_MARKET_ONLY) {
            priority += 45;
        } else if (policy.tier() == EconomicTier.UNKNOWN) {
            priority += 35;
        }
        if (profile.referencePrice() > 0L) {
            priority += 20;
        }
        switch (profile.confidence()) {
            case NONE -> priority += 12;
            case LOW -> priority += 8;
            default -> {
            }
        }
        return priority;
    }

    private static int recipeTypes(CommandSourceStack source, int limit) {
        var reports = DerivedPriceService.recipeTypeReport(source.getServer(), limit);
        source.sendSuccess(() -> Component.literal("配方类型报告: " + reports.size() + " 项"), false);
        if (reports.isEmpty()) {
            source.sendSuccess(() -> Component.literal("没有发现可报告的配方类型。"), false);
            return 0;
        }
        for (DerivedPriceService.RecipeTypeReport report : reports) {
            source.sendSuccess(() -> Component.literal(recipeTypeLine(report)), false);
        }
        return reports.size();
    }

    private static String recipeTypeLine(DerivedPriceService.RecipeTypeReport report) {
        if (report.handledByBuiltinIndexer()) {
            return report.recipeType()
                    + " | 配方=" + report.recipeCount()
                    + " | 可索引=" + report.indexableCount()
                    + " | 内置处理";
        }
        String note = report.genericNote().isBlank() ? "" : " | " + report.genericNote();
        return report.recipeType()
                + " | 配方=" + report.recipeCount()
                + " | 可索引=" + report.indexableCount()
                + " | 通用策略=" + report.genericTradeLevel() + "/" + report.genericConfidence()
                + " | max=" + report.genericMaxResultCount()
                + " fee=" + report.genericProcessingFee()
                + " markup=" + fmt(report.genericMarkup())
                + note;
    }

    private static int warmupStart(CommandSourceStack source, int itemsPerTick) {
        PriceWarmupService.WarmupSnapshot snapshot = PriceWarmupService.start(source.getServer(), itemsPerTick);
        source.sendSuccess(() -> Component.literal("已启动后台价格图预热。候选物: " + snapshot.total()
                + "，每 tick 处理: " + snapshot.itemsPerTick()
                + "。可用 /market price warmup status 查看进度。"), true);
        return 1;
    }

    private static int warmupStatus(CommandSourceStack source) {
        PriceWarmupService.WarmupSnapshot snapshot = PriceWarmupService.status(source.getServer());
        sendWarmupSnapshot(source, snapshot);
        return snapshot.processed();
    }

    private static int warmupCancel(CommandSourceStack source) {
        PriceWarmupService.WarmupSnapshot snapshot = PriceWarmupService.cancel(source.getServer());
        source.sendSuccess(() -> Component.literal("后台价格图预热已取消。"), true);
        sendWarmupSnapshot(source, snapshot);
        return snapshot.processed();
    }

    private static void sendWarmupSnapshot(CommandSourceStack source, PriceWarmupService.WarmupSnapshot snapshot) {
        source.sendSuccess(() -> Component.literal("预热状态: " + snapshot.state()), false);
        source.sendSuccess(() -> Component.literal("进度: " + snapshot.processed() + "/" + snapshot.total()
                + " (" + String.format(java.util.Locale.ROOT, "%.2f", snapshot.progressPercent()) + "%)"
                + "，剩余: " + snapshot.remaining()), false);
        source.sendSuccess(() -> Component.literal("可定价: " + snapshot.resolved()
                + "，未知: " + snapshot.unresolved()
                + "，缓存: " + snapshot.cachedCount()), false);
        source.sendSuccess(() -> Component.literal("每 tick: " + snapshot.itemsPerTick()
                + "，已用 tick: " + snapshot.elapsedTicks()), false);
        source.sendSuccess(() -> Component.literal("来源统计: " + sourceCounts(snapshot.sourceCounts())), false);
        source.sendSuccess(() -> Component.literal("交易级别统计: " + tradeLevelCounts(snapshot.tradeLevelCounts())), false);
        if (!snapshot.unresolvedSamples().isEmpty()) {
            source.sendSuccess(() -> Component.literal("未定价样本 "
                    + snapshot.unresolvedSamples().size() + "/" + snapshot.unresolved() + ":"), false);
            for (ResourceLocation itemId : snapshot.unresolvedSamples()) {
                source.sendSuccess(() -> Component.literal("- " + itemId), false);
            }
        }
    }

    private static String coveragePercent(DerivedPriceService.CoverageReport report) {
        return coveragePercent(report.candidateCount(), report.resolvedCount());
    }

    private static String coveragePercent(int candidateCount, int resolvedCount) {
        if (candidateCount <= 0) {
            return "0.00";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", resolvedCount * 100.0D / candidateCount);
    }

    private static String sourceCounts(DerivedPriceService.CoverageReport report) {
        return sourceCounts(report.sourceCounts());
    }

    private static String sourceCounts(Map<PriceSource, Integer> sourceCounts) {
        if (sourceCounts.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (PriceSource source : PriceSource.values()) {
            int count = sourceCounts.getOrDefault(source, 0);
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

    private static String tradeLevelCounts(Map<TradeLevel, Integer> tradeLevelCounts) {
        if (tradeLevelCounts.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (TradeLevel tradeLevel : TradeLevel.values()) {
            int count = tradeLevelCounts.getOrDefault(tradeLevel, 0);
            if (count <= 0) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(tradeLevelName(tradeLevel)).append("=").append(count);
            first = false;
        }
        return builder.toString();
    }

    private static String tradeLevelName(TradeLevel tradeLevel) {
        return switch (tradeLevel) {
            case BLOCKED -> "禁止";
            case PLAYER_MARKET_ONLY -> "玩家交易";
            case REFERENCE_ONLY -> "仅参考";
            case SYSTEM_BUY_ONLY -> "系统回收";
            case SYSTEM_BUY_AND_SELL -> "系统买卖";
        };
    }

    private record AuditEntry(
            String itemId,
            boolean autoBuyback,
            int riskScore,
            int blockPriority,
            EconomicTier tier,
            long buyPrice,
            long referencePrice,
            PriceSource source,
            com.nekros.market.pricing.PriceConfidence confidence,
            BuybackListDecision listDecision,
            String reason) {
    }

    private record ParsedRecipeExplanation(
            String recipeKind,
            String recipeId,
            java.util.List<ParsedIngredient> ingredients,
            Long materialTotal,
            Long processingFee,
            Long cookingFee,
            Long timeFee,
            Integer markupPercent,
            int resultCount) {
    }

    private record ParsedIngredient(String label, int count, long totalPrice) {
    }

    private static ResourceLocation itemId(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
        return BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(context, name).getItem());
    }
}
