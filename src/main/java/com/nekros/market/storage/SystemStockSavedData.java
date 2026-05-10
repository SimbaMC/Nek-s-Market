package com.nekros.market.storage;

import java.util.LinkedHashMap;
import java.util.Map;

import com.nekros.market.Config;
import com.nekros.market.pricing.policy.EconomicPolicy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class SystemStockSavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_system_stock";
    private static final SavedData.Factory<SystemStockSavedData> FACTORY = new SavedData.Factory<>(
            SystemStockSavedData::new,
            SystemStockSavedData::load);

    private final Map<String, StockEntry> stockByItem = new LinkedHashMap<>();

    public static SystemStockSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public long actualStock(ResourceLocation itemId) {
        return entry(itemId).actualStock();
    }

    public long totalBought(ResourceLocation itemId) {
        return entry(itemId).totalBought();
    }

    public long totalSold(ResourceLocation itemId) {
        return entry(itemId).totalSold();
    }

    public double recentBought(ResourceLocation itemId, long gameTime, long halfLifeTicks) {
        return entry(itemId).decayedBought(gameTime, halfLifeTicks);
    }

    public double recentSold(ResourceLocation itemId, long gameTime, long halfLifeTicks) {
        return entry(itemId).decayedSold(gameTime, halfLifeTicks);
    }

    public double netRecentDemand(ResourceLocation itemId, long gameTime, long halfLifeTicks) {
        StockEntry entry = entry(itemId);
        return entry.decayedSold(gameTime, halfLifeTicks) - entry.decayedBought(gameTime, halfLifeTicks);
    }

    public double buyMemory(ResourceLocation itemId, long gameTime) {
        StockEntry entry = entry(itemId);
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        return entry.decayedBuyMemory(gameTime, policy.memoryLambda());
    }

    public void recordSystemBuy(ResourceLocation itemId, int count) {
        recordSystemBuy(itemId, count, 0L);
    }

    public void recordSystemBuy(ResourceLocation itemId, int count, long gameTime) {
        if (count <= 0) {
            return;
        }
        StockEntry entry = entry(itemId);
        long halfLife = pressureHalfLife();
        double boughtPressure = entry.decayedBought(gameTime, halfLife) + count;
        double soldPressure = entry.decayedSold(gameTime, halfLife);
        stockByItem.put(itemId.toString(), new StockEntry(
                addClamped(entry.actualStock(), count),
                addClamped(entry.totalBought(), count),
                entry.totalSold(),
                boughtPressure,
                soldPressure,
                buyMemoryAfterTrade(itemId, entry, count, gameTime),
                gameTime));
        setDirty();
    }

    public void recordSystemSell(ResourceLocation itemId, int count) {
        recordSystemSell(itemId, count, 0L);
    }

    public void recordSystemSell(ResourceLocation itemId, int count, long gameTime) {
        if (count <= 0) {
            return;
        }
        StockEntry entry = entry(itemId);
        long halfLife = pressureHalfLife();
        double boughtPressure = entry.decayedBought(gameTime, halfLife);
        double soldPressure = entry.decayedSold(gameTime, halfLife) + count;
        stockByItem.put(itemId.toString(), new StockEntry(
                Math.max(0L, entry.actualStock() - count),
                entry.totalBought(),
                addClamped(entry.totalSold(), count),
                boughtPressure,
                soldPressure,
                entry.decayedBuyMemory(gameTime, EconomicPolicyRegistry.resolve(itemId).memoryLambda()),
                gameTime));
        setDirty();
    }

    public void setActualStock(ResourceLocation itemId, long count) {
        StockEntry entry = entry(itemId);
        stockByItem.put(itemId.toString(), new StockEntry(
                Math.max(0L, count),
                entry.totalBought(),
                entry.totalSold(),
                entry.recentBought(),
                entry.recentSold(),
                entry.buyMemory(),
                entry.pressureGameTime()));
        setDirty();
    }

    public void addActualStock(ResourceLocation itemId, long count) {
        if (count == 0L) {
            return;
        }
        StockEntry entry = entry(itemId);
        long newStock = count > 0L
                ? addClamped(entry.actualStock(), count)
                : Math.max(0L, entry.actualStock() + count);
        stockByItem.put(itemId.toString(), new StockEntry(
                newStock,
                entry.totalBought(),
                entry.totalSold(),
                entry.recentBought(),
                entry.recentSold(),
                entry.buyMemory(),
                entry.pressureGameTime()));
        setDirty();
    }

    public void clearActualStock(ResourceLocation itemId) {
        setActualStock(itemId, 0L);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag stocks = new ListTag();
        for (Map.Entry<String, StockEntry> entry : stockByItem.entrySet()) {
            CompoundTag stockTag = new CompoundTag();
            stockTag.putString("itemId", entry.getKey());
            stockTag.putLong("actualStock", entry.getValue().actualStock());
            stockTag.putLong("totalBought", entry.getValue().totalBought());
            stockTag.putLong("totalSold", entry.getValue().totalSold());
            stockTag.putDouble("recentBought", entry.getValue().recentBought());
            stockTag.putDouble("recentSold", entry.getValue().recentSold());
            stockTag.putDouble("buyMemory", entry.getValue().buyMemory());
            stockTag.putLong("pressureGameTime", entry.getValue().pressureGameTime());
            stocks.add(stockTag);
        }
        tag.put("stocks", stocks);
        return tag;
    }

    private StockEntry entry(ResourceLocation itemId) {
        return stockByItem.getOrDefault(itemId.toString(), StockEntry.EMPTY);
    }

    private static SystemStockSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SystemStockSavedData data = new SystemStockSavedData();
        ListTag stocks = tag.getList("stocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < stocks.size(); i++) {
            CompoundTag stockTag = stocks.getCompound(i);
            String itemId = stockTag.getString("itemId");
            if (!itemId.isBlank()) {
                data.stockByItem.put(itemId, new StockEntry(
                        Math.max(0L, stockTag.getLong("actualStock")),
                        Math.max(0L, stockTag.getLong("totalBought")),
                        Math.max(0L, stockTag.getLong("totalSold")),
                        Math.max(0.0D, stockTag.getDouble("recentBought")),
                        Math.max(0.0D, stockTag.getDouble("recentSold")),
                        Math.max(0.0D, stockTag.getDouble("buyMemory")),
                        Math.max(0L, stockTag.getLong("pressureGameTime"))));
            }
        }
        return data;
    }

    private static long addClamped(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long pressureHalfLife() {
        return Math.max(1L, Config.SYSTEM_STOCK_PRESSURE_HALF_LIFE_TICKS.get());
    }

    private static double buyMemoryAfterTrade(ResourceLocation itemId, StockEntry entry, int count, long gameTime) {
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        double lambda = policy.memoryLambda();
        return entry.decayedBuyMemory(gameTime, lambda) + lambda * count;
    }

    public record StockEntry(
            long actualStock,
            long totalBought,
            long totalSold,
            double recentBought,
            double recentSold,
            double buyMemory,
            long pressureGameTime) {
        static final StockEntry EMPTY = new StockEntry(0L, 0L, 0L, 0.0D, 0.0D, 0.0D, 0L);

        double decayedBought(long gameTime, long halfLifeTicks) {
            return decay(recentBought, gameTime, halfLifeTicks);
        }

        double decayedSold(long gameTime, long halfLifeTicks) {
            return decay(recentSold, gameTime, halfLifeTicks);
        }

        double decayedBuyMemory(long gameTime, double lambda) {
            if (buyMemory <= 0.0D || gameTime <= pressureGameTime || lambda <= 0.0D) {
                return buyMemory;
            }
            long elapsed = gameTime - pressureGameTime;
            return buyMemory * Math.exp(-lambda * elapsed);
        }

        private double decay(double value, long gameTime, long halfLifeTicks) {
            if (value <= 0.0D || gameTime <= pressureGameTime || halfLifeTicks == Long.MAX_VALUE) {
                return value;
            }
            long elapsed = gameTime - pressureGameTime;
            return value * Math.pow(0.5D, elapsed / (double) Math.max(1L, halfLifeTicks));
        }
    }
}
