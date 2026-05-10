package com.nekros.market.pricing.system;

import java.util.UUID;

import com.nekros.market.Config;
import com.nekros.market.pricing.policy.EconomicPolicy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemPayoutBudgetSavedData;
import com.nekros.market.storage.SystemStockSavedData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SystemBuyPressure {
    private SystemBuyPressure() {
    }

    public static Result apply(MinecraftServer server, ResourceLocation itemId, long baseUnitPrice, int tradeCount) {
        if (server == null || baseUnitPrice <= 0L) {
            return new Result(baseUnitPrice, 1.0D, "回收压力: 无。");
        }

        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        if (!policy.systemBuyAllowed()) {
            return new Result(0L, 0.0D, "回收压力: 经济策略禁止系统回收。");
        }

        SystemStockSavedData stockData = SystemStockSavedData.get(server);
        long gameTime = server.overworld().getGameTime();
        SupplyPressure supply = supplyPressure(stockData, itemId);
        double memory = Math.max(0.0D, stockData.buyMemory(itemId, gameTime));
        double delta = Math.max(0.0D, tradeCount);

        double exponent = -policy.longAlpha() * Math.pow(supply.effectiveLongSupply(), policy.gamma())
                - policy.memoryBeta() * memory
                - policy.tradeDelta() * delta;
        double rawRatio = Math.exp(exponent);
        double ratio = Math.max(policy.minBuyRatio(), rawRatio);
        long adjusted = pressureUnitPrice(baseUnitPrice, ratio);
        if (adjusted < minPayingUnitPrice()) {
            adjusted = 0L;
        }
        return new Result(adjusted, ratio, explanation(policy, supply, memory, delta, ratio, baseUnitPrice, adjusted));
    }

    public static Quote quote(MinecraftServer server, ResourceLocation itemId, long baseUnitPrice, int count) {
        return quote(server, itemId, baseUnitPrice, count, null);
    }

    public static Quote quote(MinecraftServer server, ResourceLocation itemId, long baseUnitPrice, int count, UUID playerId) {
        return quote(server, itemId, baseUnitPrice, count, playerId, null);
    }

    public static Quote quote(MinecraftServer server, ResourceLocation itemId, long baseUnitPrice, int count,
            UUID playerId, MarketSavedData marketData) {
        if (count <= 0 || baseUnitPrice <= 0L) {
            return new Quote(0L, 0L, 0.0D, "回收压力: 无。");
        }
        if (server == null) {
            long total = multiplyClamped(baseUnitPrice, count);
            return new Quote(total, baseUnitPrice, 1.0D, "回收压力: 无。");
        }

        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        if (!policy.systemBuyAllowed()) {
            return new Quote(0L, 0L, 0.0D, "回收压力: 经济策略禁止系统回收。");
        }

        SystemStockSavedData stockData = SystemStockSavedData.get(server);
        long gameTime = server.overworld().getGameTime();
        SupplyPressure supply = supplyPressure(stockData, itemId);
        double memory = Math.max(0.0D, stockData.buyMemory(itemId, gameTime));
        double baseExponent = -policy.longAlpha() * Math.pow(supply.effectiveLongSupply(), policy.gamma())
                - policy.memoryBeta() * memory;

        int samples = Math.min(count, 256);
        double ratioTotal = 0.0D;
        long total = 0L;
        if (count <= samples) {
            for (int delta = 1; delta <= count; delta++) {
                double ratio = ratioFor(baseExponent, policy.tradeDelta(), delta, policy.minBuyRatio());
                ratioTotal += ratio;
                total = addClamped(total, pressureUnitPrice(baseUnitPrice, ratio));
            }
        } else {
            for (int sample = 0; sample < samples; sample++) {
                int delta = Math.max(1, (int) Math.ceil((sample + 0.5D) * count / samples));
                ratioTotal += ratioFor(baseExponent, policy.tradeDelta(), delta, policy.minBuyRatio());
            }
        }

        double averageRatio = ratioTotal / samples;
        long averageUnitPrice;
        if (count <= samples) {
            averageUnitPrice = total / count;
        } else {
            averageUnitPrice = pressureUnitPrice(baseUnitPrice, averageRatio);
            if (averageUnitPrice < minPayingUnitPrice()) {
                averageUnitPrice = 0L;
            }
            total = multiplyClamped(averageUnitPrice, count);
        }
        if (averageUnitPrice < minPayingUnitPrice()) {
            averageUnitPrice = 0L;
            total = 0L;
        }
        BudgetCap budgetCap = budgetCap(server, total, count, playerId, marketData, baseUnitPrice, policy, baseExponent);
        return new Quote(budgetCap.totalPrice(), budgetCap.averageUnitPrice(), averageRatio,
                explanation(policy, supply, memory, count, averageRatio, baseUnitPrice, averageUnitPrice)
                        + budgetCap.explanation());
    }

    private static SupplyPressure supplyPressure(SystemStockSavedData stockData, ResourceLocation itemId) {
        long totalBought = Math.max(0L, stockData.totalBought(itemId));
        long totalSold = Math.max(0L, stockData.totalSold(itemId));
        double reliefRatio = Config.SYSTEM_BUY_PRESSURE_SOLD_RELIEF_RATIO.get();
        double effectiveLongSupply = Math.max(0.0D, totalBought - totalSold * reliefRatio);
        return new SupplyPressure(totalBought, totalSold, reliefRatio, effectiveLongSupply);
    }

    private static String explanation(EconomicPolicy policy, SupplyPressure supply, double memory, double delta,
            double ratio, long baseUnitPrice, long adjusted) {
        return "回收压力: 分级 " + policy.tier()
                + "，S有效=" + format(supply.effectiveLongSupply())
                + "，累计回收=" + supply.totalBought()
                + "，系统售出=" + supply.totalSold()
                + "，售出缓解=" + format(supply.soldReliefRatio())
                + "，C=" + format(memory)
                + "，本次数量=" + format(delta)
                + "，倍率=" + format(ratio)
                + "，" + baseUnitPrice + " -> " + adjusted + "。";
    }

    private static double ratioFor(double baseExponent, double delta, int tradeCount, double minRatio) {
        return Math.max(minRatio, Math.exp(baseExponent - delta * Math.max(0, tradeCount)));
    }

    private static long pressureUnitPrice(long baseUnitPrice, double ratio) {
        return Math.max(0L, (long) Math.floor(baseUnitPrice * ratio));
    }

    private static long minPayingUnitPrice() {
        return Math.max(0L, Config.SYSTEM_BUY_MIN_PAYING_UNIT_PRICE.get());
    }

    private static BudgetCap budgetCap(MinecraftServer server, long totalPrice, int count, UUID playerId,
            MarketSavedData marketData, long baseUnitPrice, EconomicPolicy policy, double baseExponent) {
        if (marketData == null) {
            return budgetCap(server, totalPrice, count, playerId, baseUnitPrice, policy, baseExponent);
        }
        if (server == null || totalPrice <= 0L || count <= 0) {
            return new BudgetCap(totalPrice, count <= 0 ? 0L : totalPrice / count, "");
        }
        long gameTime = server.overworld().getGameTime();
        SystemPayoutBudgetSavedData budget = SystemPayoutBudgetSavedData.get(server);
        long dailyBudget = budget.effectiveDailyBudget(marketData, gameTime);
        if (dailyBudget == Long.MAX_VALUE) {
            return new BudgetCap(totalPrice, totalPrice / count, "");
        }
        long globalRemaining = budget.remainingToday(marketData, gameTime);
        long playerBudget = budget.playerDailyBudget(marketData, gameTime);
        long playerRemaining = playerId == null ? Long.MAX_VALUE : budget.playerRemainingToday(playerId, marketData, gameTime);
        long tierBudget = budget.tierDailyBudget(policy.tier(), marketData, gameTime);
        long tierRemaining = budget.tierRemainingToday(policy.tier(), marketData, gameTime);
        long remaining = Math.min(Math.min(globalRemaining, playerRemaining), tierRemaining);
        String explanation = " 全局预算: 今日剩余 " + globalRemaining + "/" + dailyBudget + "。";
        if (playerId != null && playerBudget != Long.MAX_VALUE) {
            explanation += " 个人预算: 今日剩余 " + playerRemaining + "/" + playerBudget + "。";
        }
        if (tierBudget != Long.MAX_VALUE) {
            explanation += " 层级预算(" + policy.tier() + "): 今日剩余 " + tierRemaining + "/" + tierBudget + "。";
        }
        if (remaining >= totalPrice) {
            return new BudgetCap(totalPrice, totalPrice / count, explanation);
        }
        int maxAffordable = maxAffordableCount(remaining, count, baseUnitPrice, policy, baseExponent);
        return new BudgetCap(0L, 0L, explanation + "不足以支付本次回收，当前最多可回收 " + maxAffordable + " 个。");
    }

    private static BudgetCap budgetCap(MinecraftServer server, long totalPrice, int count, UUID playerId,
            long baseUnitPrice, EconomicPolicy policy, double baseExponent) {
        if (server == null || totalPrice <= 0L || count <= 0) {
            return new BudgetCap(totalPrice, count <= 0 ? 0L : totalPrice / count, "");
        }
        long gameTime = server.overworld().getGameTime();
        SystemPayoutBudgetSavedData budget = SystemPayoutBudgetSavedData.get(server);
        long dailyBudget = budget.dailyBudget();
        if (dailyBudget <= 0L) {
            return new BudgetCap(totalPrice, totalPrice / count, "");
        }

        long globalRemaining = budget.remainingToday(gameTime);
        long playerBudget = budget.playerDailyBudget();
        long playerRemaining = playerId == null ? Long.MAX_VALUE : budget.playerRemainingToday(playerId, gameTime);
        long remaining = Math.min(globalRemaining, playerRemaining);
        String explanation = " 全局预算: 今日剩余 " + globalRemaining + "/" + dailyBudget + "。";
        if (playerId != null && playerBudget != Long.MAX_VALUE) {
            explanation += " 个人预算: 今日剩余 " + playerRemaining + "/" + playerBudget + "。";
        }
        if (remaining >= totalPrice) {
            return new BudgetCap(totalPrice, totalPrice / count, explanation);
        }

        int maxAffordable = maxAffordableCount(remaining, count, baseUnitPrice, policy, baseExponent);
        return new BudgetCap(0L, 0L, explanation + "不足以支付本次回收，当前最多可回收 " + maxAffordable + " 个。");
    }

    private static BudgetCap budgetCap(MinecraftServer server, long totalPrice, int count, UUID playerId) {
        if (playerId == null) {
            return budgetCap(server, totalPrice, count);
        }
        if (server == null || totalPrice <= 0L || count <= 0) {
            return new BudgetCap(totalPrice, count <= 0 ? 0L : totalPrice / count, "");
        }
        long gameTime = server.overworld().getGameTime();
        SystemPayoutBudgetSavedData budget = SystemPayoutBudgetSavedData.get(server);
        long dailyBudget = budget.effectiveDailyBudget(gameTime);
        if (dailyBudget == Long.MAX_VALUE) {
            return new BudgetCap(totalPrice, totalPrice / count, "");
        }
        long globalRemaining = budget.remainingToday(gameTime);
        long playerBudget = budget.playerDailyBudget(gameTime);
        long playerRemaining = budget.playerRemainingToday(playerId, gameTime);
        long remaining = Math.min(globalRemaining, playerRemaining);
        String explanation = " 全局预算: 今日剩余 " + globalRemaining + "/" + dailyBudget
                + "。个人预算: 今日剩余 " + playerRemaining + "/" + playerBudget + "。";
        if (remaining >= totalPrice) {
            return new BudgetCap(totalPrice, totalPrice / count, explanation);
        }
        return new BudgetCap(0L, 0L, explanation + "不足以支付本次回收。");
    }

    private static BudgetCap budgetCap(MinecraftServer server, long totalPrice, int count) {
        if (server == null || totalPrice <= 0L || count <= 0) {
            return new BudgetCap(totalPrice, count <= 0 ? 0L : totalPrice / count, "");
        }
        long gameTime = server.overworld().getGameTime();
        SystemPayoutBudgetSavedData budget = SystemPayoutBudgetSavedData.get(server);
        long dailyBudget = budget.effectiveDailyBudget(gameTime);
        if (dailyBudget == Long.MAX_VALUE) {
            return new BudgetCap(totalPrice, totalPrice / count, "");
        }
        long remaining = budget.remainingToday(gameTime);
        if (remaining >= totalPrice) {
            return new BudgetCap(totalPrice, totalPrice / count,
                    " 全局预算: 今日剩余 " + remaining + "/" + dailyBudget + "。");
        }
        return new BudgetCap(0L, 0L,
                " 全局预算: 今日剩余 " + remaining + "/" + dailyBudget
                        + "，不足以支付本次回收。");
    }

    private static int maxAffordableCount(long budget, int maxCount, long baseUnitPrice,
            EconomicPolicy policy, double baseExponent) {
        if (budget <= 0L || maxCount <= 0 || baseUnitPrice <= 0L) {
            return 0;
        }
        int low = 0;
        int high = maxCount;
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            long total = pressureTotal(baseUnitPrice, policy, baseExponent, mid).totalPrice();
            if (total > 0L && total <= budget) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static PriceTotal pressureTotal(long baseUnitPrice, EconomicPolicy policy, double baseExponent, int count) {
        int samples = Math.min(count, 256);
        double ratioTotal = 0.0D;
        long total = 0L;
        if (count <= samples) {
            for (int delta = 1; delta <= count; delta++) {
                double ratio = ratioFor(baseExponent, policy.tradeDelta(), delta, policy.minBuyRatio());
                ratioTotal += ratio;
                total = addClamped(total, pressureUnitPrice(baseUnitPrice, ratio));
            }
        } else {
            for (int sample = 0; sample < samples; sample++) {
                int delta = Math.max(1, (int) Math.ceil((sample + 0.5D) * count / samples));
                ratioTotal += ratioFor(baseExponent, policy.tradeDelta(), delta, policy.minBuyRatio());
            }
        }

        double averageRatio = ratioTotal / samples;
        long averageUnitPrice;
        if (count <= samples) {
            averageUnitPrice = total / count;
        } else {
            averageUnitPrice = pressureUnitPrice(baseUnitPrice, averageRatio);
            total = multiplyClamped(averageUnitPrice, count);
        }
        if (averageUnitPrice < minPayingUnitPrice()) {
            averageUnitPrice = 0L;
            total = 0L;
        }
        return new PriceTotal(total, averageUnitPrice, averageRatio);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static long multiplyClamped(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long addClamped(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    public record Result(long price, double ratio, String explanation) {
    }

    public record Quote(long totalPrice, long averageUnitPrice, double averageRatio, String explanation) {
    }

    private record BudgetCap(long totalPrice, long averageUnitPrice, String explanation) {
    }

    private record PriceTotal(long totalPrice, long averageUnitPrice, double averageRatio) {
    }

    private record SupplyPressure(long totalBought, long totalSold, double soldReliefRatio, double effectiveLongSupply) {
    }
}
