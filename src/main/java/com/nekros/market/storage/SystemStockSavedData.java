package com.nekros.market.storage;

import java.util.LinkedHashMap;
import java.util.Map;

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

    public void recordSystemBuy(ResourceLocation itemId, int count) {
        if (count <= 0) {
            return;
        }
        StockEntry entry = entry(itemId);
        stockByItem.put(itemId.toString(), new StockEntry(
                addClamped(entry.actualStock(), count),
                addClamped(entry.totalBought(), count),
                entry.totalSold()));
        setDirty();
    }

    public void recordSystemSell(ResourceLocation itemId, int count) {
        if (count <= 0) {
            return;
        }
        StockEntry entry = entry(itemId);
        stockByItem.put(itemId.toString(), new StockEntry(
                Math.max(0L, entry.actualStock() - count),
                entry.totalBought(),
                addClamped(entry.totalSold(), count)));
        setDirty();
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
                        Math.max(0L, stockTag.getLong("totalSold"))));
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

    public record StockEntry(long actualStock, long totalBought, long totalSold) {
        static final StockEntry EMPTY = new StockEntry(0L, 0L, 0L);
    }
}
