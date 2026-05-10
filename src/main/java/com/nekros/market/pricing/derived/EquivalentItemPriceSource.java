package com.nekros.market.pricing.derived;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.nekros.market.pricing.PriceConfidence;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;

import net.minecraft.resources.ResourceLocation;

final class EquivalentItemPriceSource {
    private static final String[] WOOD_TYPES = {
            "acacia", "birch", "cherry", "dark_oak", "jungle", "mangrove", "spruce", "pale_oak"
    };
    private static final String[] COLORS = {
            "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black"
    };
    private static final Map<ResourceLocation, ResourceLocation> EQUIVALENTS = buildEquivalents();

    private EquivalentItemPriceSource() {
    }

    static PriceProfile resolve(ResourceLocation itemId) {
        ResourceLocation anchorId = EQUIVALENTS.get(itemId);
        if (anchorId == null) {
            return PriceProfile.unknown(itemId);
        }
        PriceProfile anchor = PriceRegistry.get(anchorId);
        if (anchor.referencePrice() <= 0L) {
            anchor = NaturalPriceSource.resolve(anchorId);
        }
        if (anchor.referencePrice() <= 0L) {
            return PriceProfile.unknown(itemId);
        }
        return new PriceProfile(
                itemId,
                PriceSource.DERIVED,
                PriceConfidence.MEDIUM,
                TradeLevel.SYSTEM_BUY_AND_SELL,
                anchor.referencePrice(),
                anchor.referencePrice(),
                0L,
                anchor.referencePrice(),
                0L,
                0L,
                "等价参考锚点 " + anchorId + "。");
    }

    static Set<ResourceLocation> itemIds() {
        return EQUIVALENTS.keySet();
    }

    private static Map<ResourceLocation, ResourceLocation> buildEquivalents() {
        Map<ResourceLocation, ResourceLocation> equivalents = new LinkedHashMap<>();
        addWoodEquivalents(equivalents);
        addColorEquivalents(equivalents, "wool", "white_wool");
        addColorEquivalents(equivalents, "carpet", "white_carpet");
        addColorEquivalents(equivalents, "bed", "white_bed");
        addColorEquivalents(equivalents, "terracotta", "white_terracotta");
        addColorEquivalents(equivalents, "concrete", "white_concrete");
        addColorEquivalents(equivalents, "concrete_powder", "white_concrete_powder");
        addColorEquivalents(equivalents, "stained_glass", "white_stained_glass");
        addColorEquivalents(equivalents, "stained_glass_pane", "white_stained_glass_pane");
        addColorEquivalents(equivalents, "shulker_box", "white_shulker_box");
        addColorEquivalents(equivalents, "candle", "white_candle");
        return Map.copyOf(equivalents);
    }

    private static void addWoodEquivalents(Map<ResourceLocation, ResourceLocation> equivalents) {
        ResourceLocation oakLog = minecraft("oak_log");
        for (String wood : WOOD_TYPES) {
            addEquivalent(equivalents, wood + "_log", oakLog);
            addEquivalent(equivalents, wood + "_wood", oakLog);
            addEquivalent(equivalents, "stripped_" + wood + "_log", oakLog);
            addEquivalent(equivalents, "stripped_" + wood + "_wood", oakLog);
        }
        addEquivalent(equivalents, "oak_wood", oakLog);
        addEquivalent(equivalents, "stripped_oak_log", oakLog);
        addEquivalent(equivalents, "stripped_oak_wood", oakLog);
    }

    private static void addColorEquivalents(Map<ResourceLocation, ResourceLocation> equivalents, String suffix, String anchorPath) {
        ResourceLocation anchor = minecraft(anchorPath);
        for (String color : COLORS) {
            addEquivalent(equivalents, color + "_" + suffix, anchor);
        }
    }

    private static void addEquivalent(Map<ResourceLocation, ResourceLocation> equivalents, String path, ResourceLocation anchor) {
        ResourceLocation itemId = minecraft(path);
        if (!itemId.equals(anchor)) {
            equivalents.put(itemId, anchor);
        }
    }

    private static ResourceLocation minecraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
