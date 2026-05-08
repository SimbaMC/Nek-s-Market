package com.nekros.market.pricing.derived;

import java.util.Map;

import com.nekros.market.pricing.PriceConfidence;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;

import net.minecraft.resources.ResourceLocation;

final class EquivalentItemPriceSource {
    private static final ResourceLocation OAK_LOG = minecraft("oak_log");
    private static final Map<ResourceLocation, ResourceLocation> EQUIVALENTS = Map.ofEntries(
            Map.entry(minecraft("acacia_log"), OAK_LOG),
            Map.entry(minecraft("birch_log"), OAK_LOG),
            Map.entry(minecraft("cherry_log"), OAK_LOG),
            Map.entry(minecraft("dark_oak_log"), OAK_LOG),
            Map.entry(minecraft("jungle_log"), OAK_LOG),
            Map.entry(minecraft("mangrove_log"), OAK_LOG),
            Map.entry(minecraft("spruce_log"), OAK_LOG),
            Map.entry(minecraft("pale_oak_log"), OAK_LOG),
            Map.entry(minecraft("oak_wood"), OAK_LOG),
            Map.entry(minecraft("acacia_wood"), OAK_LOG),
            Map.entry(minecraft("birch_wood"), OAK_LOG),
            Map.entry(minecraft("cherry_wood"), OAK_LOG),
            Map.entry(minecraft("dark_oak_wood"), OAK_LOG),
            Map.entry(minecraft("jungle_wood"), OAK_LOG),
            Map.entry(minecraft("mangrove_wood"), OAK_LOG),
            Map.entry(minecraft("spruce_wood"), OAK_LOG),
            Map.entry(minecraft("pale_oak_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_oak_log"), OAK_LOG),
            Map.entry(minecraft("stripped_acacia_log"), OAK_LOG),
            Map.entry(minecraft("stripped_birch_log"), OAK_LOG),
            Map.entry(minecraft("stripped_cherry_log"), OAK_LOG),
            Map.entry(minecraft("stripped_dark_oak_log"), OAK_LOG),
            Map.entry(minecraft("stripped_jungle_log"), OAK_LOG),
            Map.entry(minecraft("stripped_mangrove_log"), OAK_LOG),
            Map.entry(minecraft("stripped_spruce_log"), OAK_LOG),
            Map.entry(minecraft("stripped_pale_oak_log"), OAK_LOG),
            Map.entry(minecraft("stripped_oak_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_acacia_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_birch_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_cherry_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_dark_oak_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_jungle_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_mangrove_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_spruce_wood"), OAK_LOG),
            Map.entry(minecraft("stripped_pale_oak_wood"), OAK_LOG));

    private EquivalentItemPriceSource() {
    }

    static PriceProfile resolve(ResourceLocation itemId) {
        ResourceLocation anchorId = EQUIVALENTS.get(itemId);
        if (anchorId == null) {
            return PriceProfile.unknown(itemId);
        }
        PriceProfile anchor = PriceRegistry.get(anchorId);
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

    private static ResourceLocation minecraft(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }
}
