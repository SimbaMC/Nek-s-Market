package com.nekros.market.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nekros.market.pricing.market.MarketTradeRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class EconomySavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_economy";
    private static final int MAX_TRADES_PER_ITEM = 200;
    private static final SavedData.Factory<EconomySavedData> FACTORY = new SavedData.Factory<>(
            EconomySavedData::new,
            EconomySavedData::load);

    private final Map<String, List<MarketTradeRecord>> tradesByItem = new LinkedHashMap<>();

    public static EconomySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addTrade(MarketTradeRecord record) {
        String key = record.itemId().toString();
        List<MarketTradeRecord> records = tradesByItem.computeIfAbsent(key, ignored -> new ArrayList<>());
        records.add(record);
        while (records.size() > MAX_TRADES_PER_ITEM) {
            records.remove(0);
        }
        setDirty();
    }

    public List<MarketTradeRecord> tradesFor(ResourceLocation itemId) {
        return List.copyOf(tradesByItem.getOrDefault(itemId.toString(), List.of()));
    }

    public List<MarketTradeRecord> recentTradesFor(ResourceLocation itemId, int limit) {
        List<MarketTradeRecord> records = tradesByItem.getOrDefault(itemId.toString(), List.of());
        int fromIndex = Math.max(0, records.size() - Math.max(0, limit));
        return List.copyOf(records.subList(fromIndex, records.size()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag trades = new ListTag();
        for (Map.Entry<String, List<MarketTradeRecord>> entry : tradesByItem.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString("itemId", entry.getKey());

            ListTag records = new ListTag();
            for (MarketTradeRecord record : entry.getValue()) {
                records.add(record.save());
            }
            itemTag.put("records", records);
            trades.add(itemTag);
        }
        tag.put("trades", trades);
        return tag;
    }

    private static EconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomySavedData data = new EconomySavedData();
        ListTag trades = tag.getList("trades", Tag.TAG_COMPOUND);
        for (int i = 0; i < trades.size(); i++) {
            CompoundTag itemTag = trades.getCompound(i);
            String itemId = itemTag.getString("itemId");
            if (itemId.isBlank()) {
                continue;
            }

            List<MarketTradeRecord> records = new ArrayList<>();
            ListTag recordTags = itemTag.getList("records", Tag.TAG_COMPOUND);
            for (int j = 0; j < recordTags.size(); j++) {
                records.add(MarketTradeRecord.load(recordTags.getCompound(j)));
            }
            while (records.size() > MAX_TRADES_PER_ITEM) {
                records.remove(0);
            }
            data.tradesByItem.put(itemId, records);
        }
        return data;
    }
}
