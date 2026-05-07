package com.nekros.market.menu;

import java.util.List;

import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.listing.MarketListing;
import com.nekros.market.listing.MarketService;
import com.nekros.market.storage.MarketSavedData;

import net.minecraft.server.level.ServerPlayer;

public final class MarketMenuSnapshots {
    private MarketMenuSnapshots() {
    }

    public static MarketMenuSnapshot create(MarketSavedData data, ServerPlayer player, int page) {
        int pageCount = MarketService.pageCount(data);
        int clampedPage = Math.max(1, Math.min(page, pageCount));
        List<MarketMenuEntry> entries = MarketService.page(data, clampedPage).stream()
                .map(MarketMenuSnapshots::entry)
                .toList();
        return new MarketMenuSnapshot(clampedPage, pageCount, MarketEconomy.balance(data, player.getUUID()), entries);
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
