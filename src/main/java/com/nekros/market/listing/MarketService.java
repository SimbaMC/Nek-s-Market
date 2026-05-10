package com.nekros.market.listing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.nekros.market.Config;
import com.nekros.market.claim.MarketClaim;
import com.nekros.market.economy.MarketEconomy;
import com.nekros.market.pricing.market.MarketTradeHistoryService;
import com.nekros.market.storage.EconomyLedgerSavedData;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.util.InventoryUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class MarketService {
    public static final int LISTINGS_PER_PAGE = 8;

    private MarketService() {
    }

    public static SellResult sellMainHand(MarketSavedData data, ServerPlayer seller, long price) {
        ItemStack selected = seller.getInventory().getSelected();
        int count = selected.isEmpty() ? 0 : selected.getCount();
        return sellMainHand(data, seller, price, count);
    }

    public static SellResult sellMainHand(MarketSavedData data, ServerPlayer seller, long price, int count) {
        if (price <= 0L) {
            return SellResult.fail("价格必须大于 0。");
        }
        if (count <= 0) {
            return SellResult.fail("数量必须大于 0。");
        }

        ItemStack selected = seller.getInventory().getSelected();
        if (selected.isEmpty()) {
            return SellResult.fail("请把要出售的物品拿在主手。");
        }
        ItemStack template = selected.copyWithCount(1);
        int available = InventoryUtil.countMatching(seller.getInventory(), template);
        if (count > available) {
            return SellResult.fail("你的背包里只有 " + available + " 个匹配物品。");
        }

        long fee = Config.listingFee(price);
        if (fee > 0L && !MarketEconomy.withdraw(data, seller.getUUID(), fee)) {
            return SellResult.fail("上架费需要 " + fee + " " + MarketEconomy.CURRENCY_NAME + "。");
        }

        if (fee > 0L) {
            EconomyLedgerSavedData.get(seller.server).recordListingFee(fee);
        }

        if (!InventoryUtil.removeMatching(seller.getInventory(), template, count)) {
            return SellResult.fail("无法从背包移除选中的物品。");
        }
        long now = System.currentTimeMillis();
        UUID id = UUID.randomUUID();
        MarketListing listing = new MarketListing(
                id,
                seller.getUUID(),
                seller.getGameProfile().getName(),
                template,
                count,
                price,
                now,
                now + Config.defaultListingDurationMillis());
        data.listings().put(id, listing);
        data.setDirty();
        return SellResult.success(listing, fee);
    }

    public static BuyResult buy(MarketSavedData data, ServerPlayer buyer, UUID listingId) {
        return buy(data, buyer, listingId, Integer.MAX_VALUE);
    }

    public static BuyResult buy(MarketSavedData data, ServerPlayer buyer, UUID listingId, int count) {
        expireListings(data);

        MarketListing listing = data.listings().get(listingId);
        if (listing == null) {
            return BuyResult.fail("该挂单已不存在。");
        }
        if (count <= 0) {
            return BuyResult.fail("数量必须大于 0。");
        }
        int boughtCount = Math.min(count, listing.count());
        if (!InventoryUtil.canFit(buyer.getInventory(), listing.item(), boughtCount)) {
            return BuyResult.fail("背包空间不足。");
        }
        long totalPrice;
        try {
            totalPrice = Math.multiplyExact(listing.price(), boughtCount);
        } catch (ArithmeticException exception) {
            return BuyResult.fail("总价过大。");
        }
        if (!MarketEconomy.withdraw(data, buyer.getUUID(), totalPrice)) {
            return BuyResult.fail("你的 " + MarketEconomy.CURRENCY_NAME + " 不足。");
        }

        int remainingCount = listing.count() - boughtCount;
        if (remainingCount > 0) {
            data.listings().put(listingId, listing.withCount(remainingCount));
        } else {
            data.listings().remove(listingId);
        }
        long tax = Config.marketTradeTax(totalPrice);
        long sellerPayout = Math.max(0L, totalPrice - tax);
        EconomyLedgerSavedData.get(buyer.server).recordPlayerTrade(totalPrice, tax);
        InventoryUtil.addSplit(buyer.getInventory(), listing.item(), boughtCount);
        data.claimFor(listing.sellerId()).addMoney(sellerPayout);
        data.setDirty();

        MarketTradeHistoryService.recordPlayerTrade(
                buyer.server,
                buyer,
                listing.sellerId(),
                listing.item(),
                listing.price(),
                boughtCount);

        return BuyResult.success(listing.withCount(boughtCount), totalPrice, tax);
    }

    public static CancelResult cancel(MarketSavedData data, ServerPlayer seller, UUID listingId) {
        expireListings(data);

        MarketListing listing = data.listings().get(listingId);
        if (listing == null) {
            return CancelResult.fail("该挂单已不存在。");
        }
        if (!listing.sellerId().equals(seller.getUUID()) && !seller.hasPermissions(2)) {
            return CancelResult.fail("只能取消自己的挂单。");
        }

        data.listings().remove(listingId);
        data.claimFor(listing.sellerId()).items().addAll(InventoryUtil.split(listing.item(), listing.count()));
        data.setDirty();
        return CancelResult.success(listing);
    }

    public static ClaimResult claim(MarketSavedData data, ServerPlayer player) {
        expireListings(data);

        MarketClaim claim = data.claimFor(player.getUUID());
        long money = claim.takeMoney();
        if (money > 0L) {
            MarketEconomy.add(data, player.getUUID(), money);
        }

        int claimedItems = 0;
        Iterator<ItemStack> iterator = claim.items().iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (InventoryUtil.canFit(player.getInventory(), stack)) {
                player.getInventory().add(stack.copy());
                iterator.remove();
                claimedItems++;
            }
        }

        data.setDirty();
        return new ClaimResult(money, claimedItems, claim.items().size());
    }

    public static List<MarketListing> page(MarketSavedData data, int page) {
        expireListings(data);

        int skip = Math.max(0, page - 1) * LISTINGS_PER_PAGE;
        return data.listings().values().stream()
                .sorted(Comparator.comparingLong(MarketListing::createdAt).reversed())
                .skip(skip)
                .limit(LISTINGS_PER_PAGE)
                .toList();
    }

    public static int pageCount(MarketSavedData data) {
        expireListings(data);
        int size = data.listings().size();
        return Math.max(1, (size + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE);
    }

    public static List<MarketListing> ownListings(MarketSavedData data, UUID playerId) {
        expireListings(data);
        List<MarketListing> result = new ArrayList<>();
        for (MarketListing listing : data.listings().values()) {
            if (listing.sellerId().equals(playerId)) {
                result.add(listing);
            }
        }
        result.sort(Comparator.comparingLong(MarketListing::createdAt).reversed());
        return result;
    }

    public static int expireListings(MarketSavedData data) {
        long now = System.currentTimeMillis();
        int expired = 0;
        Iterator<MarketListing> iterator = data.listings().values().iterator();
        while (iterator.hasNext()) {
            MarketListing listing = iterator.next();
            if (listing.isExpired(now)) {
                iterator.remove();
                data.claimFor(listing.sellerId()).items().addAll(InventoryUtil.split(listing.item(), listing.count()));
                expired++;
            }
        }

        if (expired > 0) {
            data.setDirty();
        }
        return expired;
    }

    public record SellResult(boolean success, String message, MarketListing listing, long fee) {
        static SellResult success(MarketListing listing, long fee) {
            return new SellResult(true, "", listing, fee);
        }

        static SellResult fail(String message) {
            return new SellResult(false, message, null, 0L);
        }
    }

    public record BuyResult(boolean success, String message, MarketListing listing, long totalPrice, long tax) {
        static BuyResult success(MarketListing listing, long totalPrice, long tax) {
            return new BuyResult(true, "", listing, totalPrice, tax);
        }

        static BuyResult fail(String message) {
            return new BuyResult(false, message, null, 0L, 0L);
        }
    }

    public record CancelResult(boolean success, String message, MarketListing listing) {
        static CancelResult success(MarketListing listing) {
            return new CancelResult(true, "", listing);
        }

        static CancelResult fail(String message) {
            return new CancelResult(false, message, null);
        }
    }

    public record ClaimResult(long money, int claimedItems, int remainingItems) {
    }
}
