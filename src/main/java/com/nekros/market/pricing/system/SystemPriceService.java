package com.nekros.market.pricing.system;

import com.nekros.market.Config;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.system.PriceMode;
import com.nekros.market.system.SystemOfferPricing;
import com.nekros.market.system.SystemMarketOffer;
import com.nekros.market.storage.SystemStockSavedData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class SystemPriceService {
    private SystemPriceService() {
    }

    public static SystemPriceBreakdown breakdownSellToPlayer(MinecraftServer server, SystemMarketOffer offer) {
        return breakdown(server, offer, true);
    }

    public static SystemPriceBreakdown breakdownBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer) {
        return breakdown(server, offer, false);
    }

    public static SystemTradeQuote quoteSellToPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("数量必须大于 0。");
        }

        PriceProfile profile = profile(server, offer);
        if (offer.pricing().mode() != PriceMode.FIXED && hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemSell(profile.tradeLevel())) {
            return SystemTradeQuote.fail("系统商店不出售该物品。");
        }
        long unitPrice = breakdown(server, offer, true).finalUnitPrice();
        if (unitPrice <= 0L) {
            return SystemTradeQuote.fail("系统商店不出售该物品。");
        }
        if (!hasStockForSale(server, offer, count)) {
            long stock = availableStock(server, offer);
            return SystemTradeQuote.fail("系统库存只有 " + stock + " 个。", unitPrice);
        }
        return total(unitPrice, count);
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
        return stockAdjustedBuyPriceWithExplanation(server, itemId, unitPrice, false).price();
    }

    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count) {
        if (count <= 0) {
            return SystemTradeQuote.fail("数量必须大于 0。");
        }

        PriceProfile profile = profile(server, offer);
        if (offer.pricing().mode() != PriceMode.FIXED && hasExplicitTradeLevel(profile) && !PriceResolver.allowsSystemBuy(profile.tradeLevel())) {
            return SystemTradeQuote.fail("系统商店不回收该物品。");
        }
        long unitPrice = breakdown(server, offer, false).finalUnitPrice();
        if (unitPrice <= 0L) {
            return SystemTradeQuote.fail("系统商店不回收该物品。");
        }
        return total(unitPrice, count);
    }

    private static SystemPriceBreakdown breakdown(MinecraftServer server, SystemMarketOffer offer, boolean systemSells) {
        PriceProfile profile = profile(server, offer);
        long dynamicPrice = dynamicPrice(profile, systemSells);
        ModePrice modePrice = resolveModePrice(offer, profile, systemSells, dynamicPrice);
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        StockPrice stockPrice = systemSells
                ? stockAdjustedSellPriceWithExplanation(server, offer, modePrice.price())
                : stockAdjustedBuyPriceWithExplanation(server, itemId, modePrice.price(), offer.pricing().mode() == PriceMode.FIXED);
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

    private static long resolveUnitPrice(SystemMarketOffer offer, PriceProfile profile, boolean systemSells) {
        return resolveModePrice(offer, profile, systemSells, dynamicPrice(profile, systemSells)).price();
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
            return Math.max(1L, (long) Math.ceil(referencePrice * Config.PRICING_DEFAULT_SELL_RATIO.get()));
        }
        return Math.max(1L, (long) Math.floor(referencePrice * Config.PRICING_DEFAULT_BUY_RATIO.get()));
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

    private static StockPrice stockAdjustedBuyPriceWithExplanation(MinecraftServer server, ResourceLocation itemId, long unitPrice, boolean fixedMode) {
        if (server == null || unitPrice <= 0L || fixedMode) {
            return new StockPrice(unitPrice, "库存修正: 无。");
        }

        SupplyDemand demand = supplyDemand(server, itemId);
        double ratio = demand.buyRatio();
        long adjusted = Math.max(1L, (long) Math.floor(unitPrice * ratio));
        return new StockPrice(adjusted, "库存修正: 库存 " + demand.stock() + "/" + demand.target()
                + "，近期压力 " + signedRatio(demand.pressureRatio())
                + "，回收倍率 " + formatRatio(ratio) + "，" + unitPrice + " -> " + adjusted + "。");
    }

    private static StockPrice stockAdjustedSellPriceWithExplanation(MinecraftServer server, SystemMarketOffer offer, long unitPrice) {
        if (server == null
                || unitPrice <= 0L
                || offer.pricing().mode() == PriceMode.FIXED
                || offer.pricing().infiniteStock()) {
            return new StockPrice(unitPrice, "库存修正: 无。");
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem());
        SupplyDemand demand = supplyDemand(server, itemId);
        double ratio = demand.sellRatio();
        long adjusted = Math.max(1L, (long) Math.ceil(unitPrice * ratio));
        return new StockPrice(adjusted, "库存修正: 库存 " + demand.stock() + "/" + demand.target()
                + "，近期压力 " + signedRatio(demand.pressureRatio())
                + "，售价倍率 " + formatRatio(ratio) + "，" + unitPrice + " -> " + adjusted + "。");
    }

    private static SupplyDemand supplyDemand(MinecraftServer server, ResourceLocation itemId) {
        SystemStockSavedData stockData = SystemStockSavedData.get(server);
        long stock = stockData.actualStock(itemId);
        long gameTime = server.overworld().getGameTime();
        long halfLife = Math.max(1L, Config.SYSTEM_STOCK_PRESSURE_HALF_LIFE_TICKS.get());
        double recentDemand = stockData.netRecentDemand(itemId, gameTime, halfLife);
        long target = Math.max(1L, Config.SYSTEM_STOCK_HEALTHY_TARGET.get());
        double normalizedStock = stock / (double) target;
        double curveStrength = Math.max(0.0D, Config.SYSTEM_STOCK_CURVE_STRENGTH.get());
        double shortage = 1.0D / (1.0D + normalizedStock * curveStrength);
        double oversupply = normalizedStock / (1.0D + normalizedStock * curveStrength);

        double pressureScale = Math.max(1.0D, target);
        double pressure = clampDouble(recentDemand / pressureScale, -1.0D, 1.0D);
        double maxPressure = Math.max(0.0D, Config.SYSTEM_STOCK_PRESSURE_MAX_RATIO.get());

        double buyRatio = 1.0D - oversupply * Config.SYSTEM_STOCK_BUY_PRICE_IMPACT_PER_ITEM.get() * target;
        buyRatio += Math.max(0.0D, pressure) * maxPressure;
        buyRatio -= Math.max(0.0D, -pressure) * maxPressure;
        buyRatio = Math.max(Config.SYSTEM_STOCK_MIN_BUY_PRICE_RATIO.get(), buyRatio);

        double sellRatio = 1.0D + shortage * Config.SYSTEM_STOCK_SELL_MAX_PRICE_BONUS_RATIO.get();
        sellRatio += Math.max(0.0D, pressure) * maxPressure;
        sellRatio -= Math.max(0.0D, -pressure) * maxPressure * 0.5D;
        sellRatio = Math.max(0.1D, sellRatio);
        return new SupplyDemand(stock, target, pressure, buyRatio, sellRatio);
    }

    private static String formatRatio(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String signedRatio(double value) {
        return (value >= 0.0D ? "+" : "") + formatRatio(value);
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ModePrice(long price, String explanation) {
    }

    private record StockPrice(long price, String explanation) {
    }

    private record SupplyDemand(long stock, long target, double pressureRatio, double buyRatio, double sellRatio) {
    }
}
