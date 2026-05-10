package com.nekros.market.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.nekros.market.Config;
import com.nekros.market.pricing.policy.EconomicTier;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class SystemPayoutBudgetSavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_system_payout_budget";
    private static final long TICKS_PER_DAY = 24000L;
    private static final SavedData.Factory<SystemPayoutBudgetSavedData> FACTORY = new SavedData.Factory<>(
            SystemPayoutBudgetSavedData::new,
            SystemPayoutBudgetSavedData::load);

    private long dayIndex = -1L;
    private long spentToday;
    private long systemSellIncomeToday;
    private final Map<UUID, Long> spentTodayByPlayer = new LinkedHashMap<>();
    private final Map<EconomicTier, Long> spentTodayByTier = new java.util.EnumMap<>(EconomicTier.class);

    public static SystemPayoutBudgetSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public long dailyBudget() {
        return Math.max(0L, Config.SYSTEM_BUY_DAILY_PAYOUT_BUDGET.get());
    }

    public long effectiveDailyBudget(MarketSavedData marketData, long gameTime) {
        long baseBudget = scaledBaseBudget(marketData);
        if (baseBudget <= 0L) {
            return Long.MAX_VALUE;
        }
        return addClamped(baseBudget, sellIncomeCredit(gameTime));
    }

    public long effectiveDailyBudget(long gameTime) {
        long baseBudget = scaledBaseBudget(null);
        if (baseBudget <= 0L) {
            return Long.MAX_VALUE;
        }
        return addClamped(baseBudget, sellIncomeCredit(gameTime));
    }

    public long scaledBaseBudget(MarketSavedData marketData) {
        long configuredBudget = dailyBudget();
        if (configuredBudget <= 0L) {
            return 0L;
        }
        long supply = recordedCurrencySupply(marketData);
        long supplyBudget = supplyBudget(supply);
        long raw = addClamped(configuredBudget, supplyBudget);
        long min = Math.round(configuredBudget * Config.SYSTEM_BUY_BUDGET_MIN_SCALE.get());
        long max = Math.round(configuredBudget * Math.max(Config.SYSTEM_BUY_BUDGET_MIN_SCALE.get(),
                Config.SYSTEM_BUY_BUDGET_MAX_SCALE.get()));
        return clamp(raw, Math.max(0L, min), Math.max(0L, max));
    }

    public long supplyBudget(MarketSavedData marketData) {
        return supplyBudget(recordedCurrencySupply(marketData));
    }

    public long recordedCurrencySupply(MarketSavedData marketData) {
        if (marketData == null) {
            return 0L;
        }
        long balances = marketData.balances().values().stream().mapToLong(Long::longValue).sum();
        long claims = marketData.claims().values().stream().mapToLong(claim -> claim.money()).sum();
        return addClamped(Math.max(0L, balances), Math.max(0L, claims));
    }

    private static long supplyBudget(long recordedSupply) {
        if (recordedSupply <= 0L) {
            return 0L;
        }
        double ratio = Config.SYSTEM_BUY_BUDGET_CURRENCY_SUPPLY_RATIO.get();
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(0L, (long) Math.floor(recordedSupply * Math.min(1.0D, ratio)));
    }

    public long systemSellIncomeToday(long gameTime) {
        rolloverIfNeeded(gameTime);
        return systemSellIncomeToday;
    }

    public long sellIncomeCredit(long gameTime) {
        double ratio = Config.SYSTEM_SELL_INCOME_TO_BUY_BUDGET_RATIO.get();
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(0L, (long) Math.floor(systemSellIncomeToday(gameTime) * Math.min(1.0D, ratio)));
    }

    public long spentToday(long gameTime) {
        rolloverIfNeeded(gameTime);
        return spentToday;
    }

    public long remainingToday(long gameTime) {
        long budget = effectiveDailyBudget(gameTime);
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, budget - spentToday(gameTime));
    }

    public long remainingToday(MarketSavedData marketData, long gameTime) {
        long budget = effectiveDailyBudget(marketData, gameTime);
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, budget - spentToday(gameTime));
    }

    public long playerDailyBudget() {
        long budget = dailyBudget();
        if (budget <= 0L) {
            return Long.MAX_VALUE;
        }
        double ratio = Config.SYSTEM_BUY_PLAYER_DAILY_PAYOUT_RATIO.get();
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(budget * Math.min(1.0D, ratio)));
    }

    public long playerDailyBudget(long gameTime) {
        long budget = effectiveDailyBudget(gameTime);
        if (budget <= 0L) {
            return Long.MAX_VALUE;
        }
        double ratio = Config.SYSTEM_BUY_PLAYER_DAILY_PAYOUT_RATIO.get();
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(budget * Math.min(1.0D, ratio)));
    }

    public long playerDailyBudget(MarketSavedData marketData, long gameTime) {
        long budget = effectiveDailyBudget(marketData, gameTime);
        if (budget <= 0L) {
            return Long.MAX_VALUE;
        }
        double ratio = Config.SYSTEM_BUY_PLAYER_DAILY_PAYOUT_RATIO.get();
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(budget * Math.min(1.0D, ratio)));
    }

    public long playerSpentToday(UUID playerId, long gameTime) {
        rolloverIfNeeded(gameTime);
        return playerId == null ? 0L : spentTodayByPlayer.getOrDefault(playerId, 0L);
    }

    public long playerRemainingToday(UUID playerId, long gameTime) {
        long playerBudget = playerDailyBudget(gameTime);
        if (playerBudget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, playerBudget - playerSpentToday(playerId, gameTime));
    }

    public long playerRemainingToday(UUID playerId, MarketSavedData marketData, long gameTime) {
        long playerBudget = playerDailyBudget(marketData, gameTime);
        if (playerBudget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, playerBudget - playerSpentToday(playerId, gameTime));
    }

    public long remainingToday(UUID playerId, long gameTime) {
        if (playerId == null) {
            return remainingToday(gameTime);
        }
        return Math.min(remainingToday(gameTime), playerRemainingToday(playerId, gameTime));
    }

    public long remainingToday(UUID playerId, MarketSavedData marketData, long gameTime) {
        if (playerId == null) {
            return remainingToday(marketData, gameTime);
        }
        return Math.min(remainingToday(marketData, gameTime), playerRemainingToday(playerId, marketData, gameTime));
    }

    public long tierDailyBudget(EconomicTier tier, MarketSavedData marketData, long gameTime) {
        long budget = effectiveDailyBudget(marketData, gameTime);
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        double ratio = tierBudgetRatio(tier);
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(budget * Math.min(1.0D, ratio)));
    }

    public long tierDailyBudget(EconomicTier tier, long gameTime) {
        long budget = effectiveDailyBudget(gameTime);
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        double ratio = tierBudgetRatio(tier);
        if (ratio <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, (long) Math.floor(budget * Math.min(1.0D, ratio)));
    }

    public long tierSpentToday(EconomicTier tier, long gameTime) {
        rolloverIfNeeded(gameTime);
        return tier == null ? 0L : spentTodayByTier.getOrDefault(tier, 0L);
    }

    public long tierRemainingToday(EconomicTier tier, MarketSavedData marketData, long gameTime) {
        long tierBudget = tierDailyBudget(tier, marketData, gameTime);
        if (tierBudget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, tierBudget - tierSpentToday(tier, gameTime));
    }

    public long tierRemainingToday(EconomicTier tier, long gameTime) {
        long tierBudget = tierDailyBudget(tier, gameTime);
        if (tierBudget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, tierBudget - tierSpentToday(tier, gameTime));
    }

    public long remainingToday(UUID playerId, EconomicTier tier, long gameTime) {
        return Math.min(remainingToday(playerId, gameTime), tierRemainingToday(tier, gameTime));
    }

    public long remainingToday(UUID playerId, EconomicTier tier, MarketSavedData marketData, long gameTime) {
        return Math.min(remainingToday(playerId, marketData, gameTime),
                tierRemainingToday(tier, marketData, gameTime));
    }

    public long capTotal(long requestedTotal, long gameTime) {
        if (requestedTotal <= 0L) {
            return 0L;
        }
        long budget = effectiveDailyBudget(gameTime);
        if (budget == Long.MAX_VALUE) {
            return requestedTotal;
        }
        return Math.min(requestedTotal, remainingToday(gameTime));
    }

    public long capTotal(UUID playerId, long requestedTotal, long gameTime) {
        if (playerId == null) {
            return capTotal(requestedTotal, gameTime);
        }
        return Math.min(capTotal(requestedTotal, gameTime), playerRemainingToday(playerId, gameTime));
    }

    public void recordPayout(long amount, long gameTime) {
        recordPayout(null, amount, gameTime);
    }

    public void recordPayout(UUID playerId, long amount, long gameTime) {
        recordPayout(playerId, null, amount, gameTime);
    }

    public void recordPayout(UUID playerId, EconomicTier tier, long amount, long gameTime) {
        if (amount <= 0L) {
            return;
        }
        rolloverIfNeeded(gameTime);
        spentToday = addClamped(spentToday, amount);
        if (playerId != null) {
            spentTodayByPlayer.put(playerId, addClamped(spentTodayByPlayer.getOrDefault(playerId, 0L), amount));
        }
        if (tier != null) {
            spentTodayByTier.put(tier, addClamped(spentTodayByTier.getOrDefault(tier, 0L), amount));
        }
        setDirty();
    }

    public void recordSystemSellIncome(long amount, long gameTime) {
        if (amount <= 0L) {
            return;
        }
        rolloverIfNeeded(gameTime);
        systemSellIncomeToday = addClamped(systemSellIncomeToday, amount);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("dayIndex", dayIndex);
        tag.putLong("spentToday", spentToday);
        tag.putLong("systemSellIncomeToday", systemSellIncomeToday);
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Long> entry : spentTodayByPlayer.entrySet()) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("playerId", entry.getKey());
            playerTag.putLong("amount", entry.getValue());
            players.add(playerTag);
        }
        tag.put("spentTodayByPlayer", players);
        ListTag tiers = new ListTag();
        for (Map.Entry<EconomicTier, Long> entry : spentTodayByTier.entrySet()) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag tierTag = new CompoundTag();
            tierTag.putString("tier", entry.getKey().name());
            tierTag.putLong("amount", entry.getValue());
            tiers.add(tierTag);
        }
        tag.put("spentTodayByTier", tiers);
        return tag;
    }

    private void rolloverIfNeeded(long gameTime) {
        long currentDay = Math.max(0L, gameTime / TICKS_PER_DAY);
        if (dayIndex != currentDay) {
            dayIndex = currentDay;
            spentToday = 0L;
            systemSellIncomeToday = 0L;
            spentTodayByPlayer.clear();
            spentTodayByTier.clear();
            setDirty();
        }
    }

    private static SystemPayoutBudgetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SystemPayoutBudgetSavedData data = new SystemPayoutBudgetSavedData();
        data.dayIndex = tag.getLong("dayIndex");
        data.spentToday = Math.max(0L, tag.getLong("spentToday"));
        data.systemSellIncomeToday = Math.max(0L, tag.getLong("systemSellIncomeToday"));
        ListTag players = tag.getList("spentTodayByPlayer", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (playerTag.hasUUID("playerId")) {
                long amount = Math.max(0L, playerTag.getLong("amount"));
                if (amount > 0L) {
                    data.spentTodayByPlayer.put(playerTag.getUUID("playerId"), amount);
                }
            }
        }
        ListTag tiers = tag.getList("spentTodayByTier", Tag.TAG_COMPOUND);
        for (int i = 0; i < tiers.size(); i++) {
            CompoundTag tierTag = tiers.getCompound(i);
            EconomicTier tier = parseTier(tierTag.getString("tier"));
            long amount = Math.max(0L, tierTag.getLong("amount"));
            if (tier != null && amount > 0L) {
                data.spentTodayByTier.put(tier, amount);
            }
        }
        return data;
    }

    private static double tierBudgetRatio(EconomicTier tier) {
        if (tier == null) {
            return 0.0D;
        }
        for (String line : Config.SYSTEM_BUY_TIER_DAILY_PAYOUT_RATIOS.get().stream().map(String::valueOf).toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length != 2) {
                continue;
            }
            EconomicTier parsedTier = parseTier(parts[0].trim());
            if (parsedTier != tier) {
                continue;
            }
            try {
                return Math.max(0.0D, Math.min(1.0D, Double.parseDouble(parts[1].trim())));
            } catch (NumberFormatException ignored) {
                return 0.0D;
            }
        }
        return 0.25D;
    }

    private static EconomicTier parseTier(String value) {
        try {
            return EconomicTier.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long addClamped(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
