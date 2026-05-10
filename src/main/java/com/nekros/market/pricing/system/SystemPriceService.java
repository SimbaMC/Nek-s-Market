package com.nekros.market.pricing.system;

import java.util.UUID;

import com.nekros.market.Config;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.market.MarketPriceIndexService;
import com.nekros.market.pricing.policy.EconomicPolicy;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry;
import com.nekros.market.pricing.policy.EconomicPolicyRegistry.BuybackListDecision;
import com.nekros.market.system.PriceMode;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.system.SystemOfferPricing;
import com.nekros.market.storage.MarketSavedData;
import com.nekros.market.storage.SystemStockSavedData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SystemPriceService {
    private SystemPriceService() {
    }

    public static SystemPriceBreakdown breakdownSellToPlayer(MinecraftServer server, SystemMarketOffer offer) {
        return breakdown(server, offer, true, 0);
    }

    public static SystemPriceBreakdown breakdownBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer) {
        return breakdown(server, offer, false, 0);
    }

    public static SystemPriceBreakdown breakdownBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        return breakdown(server, offer, false, Math.max(0, count));
    }

    public static SystemTradeQuote quoteSellToPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("数量必须大于 0。");
        }

        PriceProfile profile = profile(server, offer);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        if (isSyntheticQuoteOffer(offer)
                && offer.pricing().mode() == PriceMode.AUTO
                && !EconomicPolicyRegistry.resolve(itemId).systemSellAllowedByDefault()) {
            return SystemTradeQuote.fail("经济分级默认不允许系统自动出售该物品。");
        }
        if (offer.pricing().mode() != PriceMode.FIXED
                && hasExplicitTradeLevel(profile)
                && !PriceResolver.allowsSystemSell(profile.tradeLevel())
                && !manualSellMode(offer.pricing().mode())) {
            return SystemTradeQuote.fail("系统商店不出售该物品。");
        }

        SystemPriceBreakdown breakdown = breakdownSellToPlayer(server, offer);
        long unitPrice = breakdown.finalUnitPrice();
        if (unitPrice <= 0L) {
            return SystemTradeQuote.fail("系统商店不出售该物品。");
        }
        if (!hasStockForSale(server, offer, count)) {
            long stock = availableStock(server, offer);
            return SystemTradeQuote.fail("系统库存只有 " + stock + " 个。", unitPrice);
        }
        if (offer.pricing().mode() == PriceMode.FIXED || server == null) {
            return total(unitPrice, count);
        }

        ModePrice modePrice = resolveModePrice(offer, profile, true, dynamicPrice(profile, true));
        SellQuote quote = stockAdjustedSellQuote(server, offer, profile, itemId, modePrice.price(), count);
        if (quote.totalPrice() <= 0L || quote.averageUnitPrice() <= 0L) {
            return SystemTradeQuote.fail("系统商店不出售该物品。");
        }
        return SystemTradeQuote.success(quote.totalPrice(), quote.averageUnitPrice());
    }

    public static long availableStock(MinecraftServer server, SystemMarketOffer offer) {
        if (server == null || offer.pricing().infiniteStock()) {
            return Long.MAX_VALUE;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        return SystemStockSavedData.get(server).actualStock(itemId);
    }

    public static boolean hasStockForSale(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0 || offer.pricing().infiniteStock()) {
            return count > 0;
        }
        if (server == null) {
            return true;
        }
        return availableStock(server, offer) >= count;
    }

    public static long dynamicBuyPriceForStock(MinecraftServer server, ResourceLocation itemId, long unitPrice) {
        return dynamicBuyPriceForStock(server, itemId, unitPrice, 0);
    }

    public static long dynamicBuyPriceForStock(MinecraftServer server, ResourceLocation itemId, long unitPrice, int tradeCount) {
        return buyPressureAdjustedPriceWithExplanation(server, itemId, unitPrice, tradeCount, false).price();
    }

    public static boolean allowsAutomaticBuyback(ResourceLocation itemId, PriceProfile profile) {
        if (itemId == null || profile == null || profile.systemBuyPrice() <= 0L) {
            return false;
        }
        BuybackListDecision listDecision = EconomicPolicyRegistry.buybackListDecision(itemId);
        if (listDecision == BuybackListDecision.DENY) {
            return false;
        }
        EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
        return PriceResolver.allowsAutomaticBuyPrice(itemId, policy, profile.tradeLevel(), profile.confidence());
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        return quoteBuyFromPlayer(server, offer, count, null);
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count, UUID playerId) {
        return quoteBuyFromPlayer(server, offer, count, playerId, null);
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count,
            UUID playerId, MarketSavedData marketData) {
        if (count <= 0) {
            return SystemTradeQuote.fail("数量必须大于 0。");
        }

        PriceProfile profile = profile(server, offer);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        if (offer.pricing().mode() != PriceMode.FIXED && !allowsAutomaticBuyback(itemId, profile)) {
            EconomicPolicy policy = EconomicPolicyRegistry.resolve(itemId);
            if (!policy.systemBuyAllowed()) {
                return SystemTradeQuote.fail("经济分级不允许系统回收该物品: " + policy.tier() + "。");
            }
            return SystemTradeQuote.fail("价格档案不允许系统回收该物品。");
        }

        ModePrice modePrice = resolveModePrice(offer, profile, false, dynamicPrice(profile, false));
        if (modePrice.price() <= 0L) {
            return SystemTradeQuote.fail("系统商店不回收该物品。");
        }
        if (offer.pricing().mode() == PriceMode.FIXED) {
            return total(modePrice.price(), count);
        }

        SystemBuyPressure.Quote quote = SystemBuyPressure.quote(server, itemId, modePrice.price(), count, playerId, marketData);
        if (quote.totalPrice() <= 0L) {
            return SystemTradeQuote.fail(quote.explanation(), quote.averageUnitPrice());
        }
        if (quote.totalPrice() <= 0L) {
            return SystemTradeQuote.fail("系统商店不回收该物品。");
        }
        return SystemTradeQuote.success(quote.totalPrice(), quote.averageUnitPrice());
    }

    public static SystemBuyPressure.Quote quoteAutomaticBuyback(MinecraftServer server, ResourceLocation itemId,
            PriceProfile profile, int count) {
        if (!allowsAutomaticBuyback(itemId, profile)) {
            return new SystemBuyPressure.Quote(0L, 0L, 0.0D, "回收压力: 该物品不可自动回收。");
        }
        return SystemBuyPressure.quote(server, itemId, profile.systemBuyPrice(), count);
    }

    public static SystemBuyPressure.Quote quoteAutomaticBuyback(MinecraftServer server, ResourceLocation itemId,
            PriceProfile profile, int count, UUID playerId) {
        return quoteAutomaticBuyback(server, itemId, profile, count, playerId, null);
    }

    public static SystemBuyPressure.Quote quoteAutomaticBuyback(MinecraftServer server, ResourceLocation itemId,
            PriceProfile profile, int count, UUID playerId, MarketSavedData marketData) {
        if (playerId == null) {
            return quoteAutomaticBuyback(server, itemId, profile, count);
        }
        if (!allowsAutomaticBuyback(itemId, profile)) {
            return new SystemBuyPressure.Quote(0L, 0L, 0.0D, "回收压力: 该物品不可自动回收。");
        }
        return SystemBuyPressure.quote(server, itemId, profile.systemBuyPrice(), count, playerId, marketData);
    }

    private static SystemPriceBreakdown breakdown(MinecraftServer server, SystemMarketOffer offer,
            boolean systemSells, int buyTradeCount) {
        PriceProfile profile = profile(server, offer);
        long dynamicPrice = dynamicPrice(profile, systemSells);
        ModePrice modePrice = resolveModePrice(offer, profile, systemSells, dynamicPrice);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        StockPrice stockPrice = systemSells
                ? stockAdjustedSellPriceWithExplanation(server, offer, profile, modePrice.price())
                : buyPressureAdjustedPriceWithExplanation(server, itemId, modePrice.price(), buyTradeCount,
                        offer.pricing().mode() == PriceMode.FIXED);
        if (!systemSells && buyTradeCount > 0 && offer.pricing().mode() != PriceMode.FIXED) {
            SystemBuyPressure.Quote quote = SystemBuyPressure.quote(server, itemId, modePrice.price(), buyTradeCount);
            stockPrice = new StockPrice(quote.averageUnitPrice(), quote.explanation());
        }
        return new SystemPriceBreakdown(
                profile,
                dynamicPrice,
                modePrice.price(),
                stockPrice.price(),
                modePrice.explanation(),
                stockPrice.explanation());
    }

    private static PriceProfile profile(MinecraftServer server, SystemMarketOffer offer) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        return PriceResolver.resolve(server, itemId);
    }

    private static SystemTradeQuote total(long unitPrice, int count) {
        try {
            return SystemTradeQuote.success(Math.multiplyExact(unitPrice, count), unitPrice);
        } catch (ArithmeticException exception) {
            return SystemTradeQuote.fail("总价过大。");
        }
    }

    private static boolean hasExplicitTradeLevel(PriceProfile profile) {
        return profile.source() == PriceSource.ANCHOR
                || profile.source() == PriceSource.DERIVED
                || profile.source() == PriceSource.NATURAL
                || profile.source() == PriceSource.MIXED;
    }

    private static boolean manualSellMode(PriceMode mode) {
        return mode == PriceMode.ANCHOR || mode == PriceMode.MULTIPLIER || mode == PriceMode.BAND;
    }

    private static boolean isSyntheticQuoteOffer(SystemMarketOffer offer) {
        return offer.id().startsWith("quote_");
    }

    private static ModePrice resolveModePrice(SystemMarketOffer offer, PriceProfile profile, boolean systemSells, long dynamicPrice) {
        SystemOfferPricing pricing = offer.pricing();
        return switch (pricing.mode()) {
            case FIXED -> new ModePrice(pricing.basePrice(), "FIXED: 使用货架固定价 " + pricing.basePrice() + "。");
            case AUTO -> {
                long price = fallbackPrice(dynamicPrice, pricing.basePrice());
                String explanation = dynamicPrice > 0L
                        ? "AUTO: 使用动态价 " + dynamicPrice + "。"
                        : "AUTO: 动态价不可用，使用备用基准价 " + pricing.basePrice() + "。";
                yield new ModePrice(price, explanation);
            }
            case ANCHOR -> {
                long price = dynamicPriceFromAnchor(pricing.basePrice(), profile, systemSells);
                yield new ModePrice(price, "ANCHOR: 基准价 " + pricing.basePrice() + "，混合市场后得到 " + price + "。");
            }
            case MULTIPLIER -> {
                long base = fallbackPrice(dynamicPrice, pricing.basePrice());
                long price = multiply(base, pricing.multiplier());
                yield new ModePrice(price, "MULTIPLIER: " + base + " x " + formatRatio(pricing.multiplier()) + " = " + price + "。");
            }
            case BAND -> {
                long base = fallbackPrice(dynamicPrice, pricing.basePrice());
                long price = clamp(base, pricing.minPrice(), pricing.maxPrice());
                yield new ModePrice(price, "BAND: " + base + " 限制到 [" + pricing.minPrice() + ", " + pricing.maxPrice() + "] = " + price + "。");
            }
        };
    }

    private static long dynamicPrice(PriceProfile profile, boolean systemSells) {
        return systemSells ? profile.systemSellPrice() : profile.systemBuyPrice();
    }

    private static long fallbackPrice(long dynamicPrice, long fallbackPrice) {
        return dynamicPrice > 0L ? dynamicPrice : fallbackPrice;
    }

    private static long dynamicPriceFromAnchor(long anchorPrice, PriceProfile profile, boolean systemSells) {
        if (anchorPrice <= 0L) {
            return dynamicPrice(profile, systemSells);
        }

        long referencePrice = anchorPrice;
        if (profile.marketPrice() > 0L) {
            double confidence = switch (profile.confidence()) {
                case HIGH -> 1.0D;
                case MEDIUM -> 0.5D;
                case LOW -> 0.25D;
                case NONE -> 0.0D;
            };
            referencePrice = Math.max(1L, Math.round(anchorPrice * (1.0D - confidence) + profile.marketPrice() * confidence));
        }

        if (systemSells) {
            return Math.max(1L, (long) Math.ceil(referencePrice * EconomicPolicyRegistry.sellRatio(profile.itemId())));
        }
        return Math.max(1L, (long) Math.floor(referencePrice * EconomicPolicyRegistry.buyRatio(profile.itemId())));
    }

    private static long multiply(long price, double multiplier) {
        if (price <= 0L || multiplier <= 0.0D) {
            return 0L;
        }
        return Math.max(1L, Math.round(price * multiplier));
    }

    private static long clamp(long price, long minPrice, long maxPrice) {
        if (price <= 0L) {
            return 0L;
        }
        long clamped = price;
        if (minPrice > 0L) {
            clamped = Math.max(clamped, minPrice);
        }
        if (maxPrice > 0L) {
            clamped = Math.min(clamped, maxPrice);
        }
        return Math.max(1L, clamped);
    }

    private static StockPrice buyPressureAdjustedPriceWithExplanation(MinecraftServer server, ResourceLocation itemId,
            long unitPrice, int tradeCount, boolean fixedMode) {
        if (server == null || unitPrice <= 0L || fixedMode) {
            return new StockPrice(unitPrice, "回收压力: 无。");
        }
        SystemBuyPressure.Result result = SystemBuyPressure.apply(server, itemId, unitPrice, tradeCount);
        return new StockPrice(result.price(), result.explanation());
    }

    private static StockPrice stockAdjustedSellPriceWithExplanation(MinecraftServer server, SystemMarketOffer offer,
            PriceProfile profile, long unitPrice) {
        if (server == null || unitPrice <= 0L || offer.pricing().mode() == PriceMode.FIXED) {
            return new StockPrice(unitPrice, "出售修正: 无。");
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        SellAdjustment adjustment = sellAdjustment(server, offer, profile, itemId, 0);
        double ratio = adjustment.stockFactor() * adjustment.marketFactor();
        long adjusted = Math.max(1L, (long) Math.ceil(unitPrice * ratio));
        return new StockPrice(adjusted, "系统出售修正: 库存系数 " + formatRatio(adjustment.stockFactor())
                + "（库存 " + adjustment.stock() + "/" + adjustment.target() + "）"
                + "，市场系数 " + formatRatio(adjustment.marketFactor())
                + "（VWAP " + adjustment.marketPrice() + "，置信 " + formatRatio(adjustment.marketConfidence()) + "）"
                + "，总倍率 " + formatRatio(ratio) + "，" + unitPrice + " -> " + adjusted + "。");
    }

    private static SellQuote stockAdjustedSellQuote(MinecraftServer server, SystemMarketOffer offer, PriceProfile profile,
            ResourceLocation itemId, long unitPrice, int count) {
        if (server == null || unitPrice <= 0L || count <= 0 || offer.pricing().mode() == PriceMode.FIXED) {
            return new SellQuote(multiplyClamped(unitPrice, Math.max(0, count)),
                    count <= 0 ? 0L : unitPrice,
                    1.0D,
                    "sell adjustment: none");
        }

        SellAdjustment initial = sellAdjustment(server, offer, profile, itemId, 0);
        int samples = Math.min(count, 256);
        double ratioTotal = 0.0D;
        long total = 0L;
        if (count <= samples) {
            for (int soldSoFar = 0; soldSoFar < count; soldSoFar++) {
                SellAdjustment adjustment = sellAdjustment(server, offer, profile, itemId, soldSoFar);
                double ratio = adjustment.stockFactor() * adjustment.marketFactor();
                ratioTotal += ratio;
                total = addClamped(total, Math.max(1L, (long) Math.ceil(unitPrice * ratio)));
            }
        } else {
            for (int sample = 0; sample < samples; sample++) {
                int soldSoFar = Math.max(0, (int) Math.floor((sample + 0.5D) * count / samples));
                SellAdjustment adjustment = sellAdjustment(server, offer, profile, itemId, soldSoFar);
                ratioTotal += adjustment.stockFactor() * adjustment.marketFactor();
            }
        }

        double averageRatio = ratioTotal / samples;
        long averageUnitPrice;
        if (count <= samples) {
            averageUnitPrice = total / count;
        } else {
            averageUnitPrice = Math.max(1L, (long) Math.ceil(unitPrice * averageRatio));
            total = multiplyClamped(averageUnitPrice, count);
        }
        String explanation = "sell batch adjustment: stock " + initial.stock()
                + "/" + initial.target()
                + ", count " + count
                + ", initialStockFactor " + formatRatio(initial.stockFactor())
                + ", marketFactor " + formatRatio(initial.marketFactor())
                + ", averageRatio " + formatRatio(averageRatio)
                + ", averageUnit " + averageUnitPrice;
        return new SellQuote(total, averageUnitPrice, averageRatio, explanation);
    }

    private static SellAdjustment sellAdjustment(MinecraftServer server, SystemMarketOffer offer, PriceProfile profile,
            ResourceLocation itemId, int soldSoFar) {
        SystemStockSavedData stockData = SystemStockSavedData.get(server);
        long stock = Math.max(0L, stockData.actualStock(itemId) - Math.max(0, soldSoFar));
        long target = Math.max(1L, Config.SYSTEM_STOCK_SELL_LOW_STOCK_TARGET.get());
        double stockFactor = offer.pricing().infiniteStock()
                ? 1.0D
                : stockFactor(stock, target);

        long marketPrice = MarketPriceIndexService.recentVWAP(server, itemId);
        double confidence = MarketPriceIndexService.confidence(server, itemId);
        double marketFactor = marketFactor(profile, marketPrice, confidence);
        return new SellAdjustment(stock, target, stockFactor, marketPrice, confidence, marketFactor);
    }

    private static double stockFactor(long stock, long target) {
        double effectiveStock = Math.max(1.0D, stock);
        double raw = Math.pow(target / effectiveStock, Config.SYSTEM_SELL_STOCK_ELASTICITY.get());
        return clampDouble(raw, Config.SYSTEM_SELL_STOCK_MIN_RATIO.get(), Config.SYSTEM_SELL_STOCK_MAX_RATIO.get());
    }

    private static double marketFactor(PriceProfile profile, long marketPrice, double confidence) {
        long basePrice = profile.referencePrice() > 0L ? profile.referencePrice() : profile.systemSellPrice();
        if (basePrice <= 0L || marketPrice <= 0L || confidence <= 0.0D) {
            return 1.0D;
        }
        double marketRatio = marketPrice / (double) basePrice;
        double weighted = 1.0D + (marketRatio - 1.0D)
                * Config.SYSTEM_SELL_MARKET_WEIGHT.get()
                * clampDouble(confidence, 0.0D, 1.0D);
        return clampDouble(weighted, Config.SYSTEM_SELL_MARKET_MIN_RATIO.get(), Config.SYSTEM_SELL_MARKET_MAX_RATIO.get());
    }

    private static String formatRatio(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long multiplyClamped(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long addClamped(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private record ModePrice(long price, String explanation) {
    }

    private record StockPrice(long price, String explanation) {
    }

    private record SellQuote(long totalPrice, long averageUnitPrice, double averageRatio, String explanation) {
    }

    private record SellAdjustment(
            long stock,
            long target,
            double stockFactor,
            long marketPrice,
            double marketConfidence,
            double marketFactor) {
    }
}
