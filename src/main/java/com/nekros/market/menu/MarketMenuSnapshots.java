package com.nekros.market.menu;

import java.util.List;
import java.util.Comparator;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.listing.MarketListing;
import com.nekros.market.listing.MarketService;
import com.nekros.market.storage.MarketSavedData;

import net.minecraft.server.level.ServerPlayer;

public final class MarketMenuSnapshots {
    private MarketMenuSnapshots() {
    }

    public static MarketMenuSnapshot create(MarketSavedData data, ServerPlayer player, int page) {
        MarketService.expireListings(data);
        List<MarketMenuEntry> entries = data.listings().values().stream()
                .sorted(Comparator.comparingLong(MarketListing::createdAt).reversed())
                .map(MarketMenuSnapshots::entry)
                .toList();
        return new MarketMenuSnapshot(1, 1, MarketEconomy.balance(data, player.getUUID()), entries);
    }

    private static MarketMenuEntry entry(MarketListing listing) {
        return new MarketMenuEntry(
                listing.id(),
                listing.sellerName(),
                listing.item().copy(),
                listing.count(),
                listing.price(),
                listing.expiresAt());
    }
}
