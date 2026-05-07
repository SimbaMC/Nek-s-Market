package com.nekros.market.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.nekros.market.claim.MarketClaim;
import com.nekros.market.listing.MarketListing;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class MarketSavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_market";
    private static final SavedData.Factory<MarketSavedData> FACTORY = new SavedData.Factory<>(
            MarketSavedData::new,
            MarketSavedData::load);

    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> balances = new LinkedHashMap<>();
    private final Map<UUID, MarketClaim> claims = new LinkedHashMap<>();

    public static MarketSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Map<UUID, MarketListing> listings() {
        return listings;
    }

    public Map<UUID, Long> balances() {
        return balances;
    }

    public Map<UUID, MarketClaim> claims() {
        return claims;
    }

    public MarketClaim claimFor(UUID playerId) {
        return claims.computeIfAbsent(playerId, ignored -> new MarketClaim());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listingList = new ListTag();
        for (MarketListing listing : listings.values()) {
            listingList.add(listing.save(registries));
        }
        tag.put("listings", listingList);

        ListTag balanceList = new ListTag();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            CompoundTag balanceTag = new CompoundTag();
            balanceTag.putUUID("playerId", entry.getKey());
            balanceTag.putLong("balance", entry.getValue());
            balanceList.add(balanceTag);
        }
        tag.put("balances", balanceList);

        ListTag claimList = new ListTag();
        for (Map.Entry<UUID, MarketClaim> entry : claims.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                CompoundTag claimTag = entry.getValue().save(registries);
                claimTag.putUUID("playerId", entry.getKey());
                claimList.add(claimTag);
            }
        }
        tag.put("claims", claimList);
        return tag;
    }

    private static MarketSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketSavedData data = new MarketSavedData();

        ListTag listingList = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingList.size(); i++) {
            MarketListing listing = MarketListing.load(listingList.getCompound(i), registries);
            if (!listing.item().isEmpty()) {
                data.listings.put(listing.id(), listing);
            }
        }

        ListTag balanceList = tag.getList("balances", Tag.TAG_COMPOUND);
        for (int i = 0; i < balanceList.size(); i++) {
            CompoundTag balanceTag = balanceList.getCompound(i);
            data.balances.put(balanceTag.getUUID("playerId"), balanceTag.getLong("balance"));
        }

        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag claimTag = claimList.getCompound(i);
            data.claims.put(claimTag.getUUID("playerId"), MarketClaim.load(claimTag, registries));
        }
        return data;
    }
}
