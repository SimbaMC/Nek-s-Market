package com.nekros.market.storage;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class EconomyLedgerSavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_economy_ledger";
    private static final SavedData.Factory<EconomyLedgerSavedData> FACTORY = new SavedData.Factory<>(
            EconomyLedgerSavedData::new,
            EconomyLedgerSavedData::load);

    private long systemBuyPayout;
    private long systemSellIncome;
    private long playerTradeVolume;
    private long playerTradeTaxBurned;
    private long listingFeesBurned;
    private long adminMoneyIssued;
    private long adminMoneyRemoved;
    private final Map<String, Long> systemBuyPayoutByItem = new LinkedHashMap<>();
    private final Map<String, Long> systemSellIncomeByItem = new LinkedHashMap<>();

    public static EconomyLedgerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void recordSystemBuyPayout(long amount) {
        systemBuyPayout = addClamped(systemBuyPayout, amount);
        setDirty();
    }

    public void recordSystemBuyPayout(ResourceLocation itemId, long amount) {
        recordSystemBuyPayout(amount);
        addToMap(systemBuyPayoutByItem, itemId, amount);
    }

    public void recordSystemSellIncome(long amount) {
        systemSellIncome = addClamped(systemSellIncome, amount);
        setDirty();
    }

    public void recordSystemSellIncome(ResourceLocation itemId, long amount) {
        recordSystemSellIncome(amount);
        addToMap(systemSellIncomeByItem, itemId, amount);
    }

    public void recordPlayerTrade(long volume, long taxBurned) {
        playerTradeVolume = addClamped(playerTradeVolume, volume);
        playerTradeTaxBurned = addClamped(playerTradeTaxBurned, taxBurned);
        setDirty();
    }

    public void recordListingFee(long amount) {
        listingFeesBurned = addClamped(listingFeesBurned, amount);
        setDirty();
    }

    public void recordAdminMoneyIssued(long amount) {
        adminMoneyIssued = addClamped(adminMoneyIssued, amount);
        setDirty();
    }

    public void recordAdminMoneyRemoved(long amount) {
        adminMoneyRemoved = addClamped(adminMoneyRemoved, amount);
        setDirty();
    }

    public long systemBuyPayout() {
        return systemBuyPayout;
    }

    public long systemSellIncome() {
        return systemSellIncome;
    }

    public long playerTradeVolume() {
        return playerTradeVolume;
    }

    public long playerTradeTaxBurned() {
        return playerTradeTaxBurned;
    }

    public long listingFeesBurned() {
        return listingFeesBurned;
    }

    public long adminMoneyIssued() {
        return adminMoneyIssued;
    }

    public long adminMoneyRemoved() {
        return adminMoneyRemoved;
    }

    public long netSystemIssue() {
        return systemBuyPayout - systemSellIncome;
    }

    public long totalBurned() {
        return addClamped(playerTradeTaxBurned, listingFeesBurned);
    }

    public long netCurrencyChange() {
        return addClamped(netSystemIssue(), adminMoneyIssued) - addClamped(totalBurned(), adminMoneyRemoved);
    }

    public List<ItemMoney> topSystemBuyPayouts(int limit) {
        return topItems(systemBuyPayoutByItem, limit);
    }

    public List<ItemMoney> topSystemSellIncomes(int limit) {
        return topItems(systemSellIncomeByItem, limit);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("systemBuyPayout", systemBuyPayout);
        tag.putLong("systemSellIncome", systemSellIncome);
        tag.putLong("playerTradeVolume", playerTradeVolume);
        tag.putLong("playerTradeTaxBurned", playerTradeTaxBurned);
        tag.putLong("listingFeesBurned", listingFeesBurned);
        tag.putLong("adminMoneyIssued", adminMoneyIssued);
        tag.putLong("adminMoneyRemoved", adminMoneyRemoved);
        tag.put("systemBuyPayoutByItem", saveItemMoney(systemBuyPayoutByItem));
        tag.put("systemSellIncomeByItem", saveItemMoney(systemSellIncomeByItem));
        return tag;
    }

    private static EconomyLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyLedgerSavedData data = new EconomyLedgerSavedData();
        data.systemBuyPayout = Math.max(0L, tag.getLong("systemBuyPayout"));
        data.systemSellIncome = Math.max(0L, tag.getLong("systemSellIncome"));
        data.playerTradeVolume = Math.max(0L, tag.getLong("playerTradeVolume"));
        data.playerTradeTaxBurned = Math.max(0L, tag.getLong("playerTradeTaxBurned"));
        data.listingFeesBurned = Math.max(0L, tag.getLong("listingFeesBurned"));
        data.adminMoneyIssued = Math.max(0L, tag.getLong("adminMoneyIssued"));
        data.adminMoneyRemoved = Math.max(0L, tag.getLong("adminMoneyRemoved"));
        data.systemBuyPayoutByItem.putAll(loadItemMoney(tag.getList("systemBuyPayoutByItem", Tag.TAG_COMPOUND)));
        data.systemSellIncomeByItem.putAll(loadItemMoney(tag.getList("systemSellIncomeByItem", Tag.TAG_COMPOUND)));
        return data;
    }

    private static void addToMap(Map<String, Long> map, ResourceLocation itemId, long amount) {
        if (itemId == null || amount <= 0L) {
            return;
        }
        map.put(itemId.toString(), addClamped(map.getOrDefault(itemId.toString(), 0L), amount));
    }

    private static List<ItemMoney> topItems(Map<String, Long> map, int limit) {
        return map.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(0, limit))
                .map(entry -> new ItemMoney(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static ListTag saveItemMoney(Map<String, Long> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("itemId", entry.getKey());
            tag.putLong("amount", entry.getValue());
            list.add(tag);
        }
        return list;
    }

    private static Map<String, Long> loadItemMoney(ListTag list) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            String itemId = tag.getString("itemId");
            long amount = Math.max(0L, tag.getLong("amount"));
            if (!itemId.isBlank() && amount > 0L) {
                map.put(itemId, amount);
            }
        }
        return map;
    }

    private static long addClamped(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    public record ItemMoney(String itemId, long amount) {
    }
}
