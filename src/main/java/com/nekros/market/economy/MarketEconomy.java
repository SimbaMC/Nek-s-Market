package com.nekros.market.economy;

import java.util.UUID;

import com.nekros.market.storage.MarketSavedData;

public final class MarketEconomy {
    public static final String CURRENCY_NAME = "\u6751\u6c11\u5e01";

    private MarketEconomy() {
    }

    public static long balance(MarketSavedData data, UUID playerId) {
        return data.balances().getOrDefault(playerId, 0L);
    }

    public static void setBalance(MarketSavedData data, UUID playerId, long amount) {
        data.balances().put(playerId, Math.max(0L, amount));
        data.setDirty();
    }

    public static void add(MarketSavedData data, UUID playerId, long amount) {
        setBalance(data, playerId, Math.addExact(balance(data, playerId), amount));
    }

    public static boolean withdraw(MarketSavedData data, UUID playerId, long amount) {
        long balance = balance(data, playerId);
        if (amount < 0L || balance < amount) {
            return false;
        }

        data.balances().put(playerId, balance - amount);
        data.setDirty();
        return true;
    }
}
