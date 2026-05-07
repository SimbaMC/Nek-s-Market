package com.nekros.market.network;

import com.nekros.market.NeksMarket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketAdminActionPayload(String action, String id, String offerType, String itemId, long price, String category) implements CustomPacketPayload {
    public static final String ADD = "add";
    public static final String REMOVE = "remove";
    public static final String RELOAD = "reload";
    public static final String RENAME_CATEGORY = "rename_category";
    public static final String RESET_CATEGORY = "reset_category";

    public static final Type<MarketAdminActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeksMarket.MODID, "market_admin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketAdminActionPayload> STREAM_CODEC = StreamCodec.ofMember(
            MarketAdminActionPayload::write,
            MarketAdminActionPayload::read);

    public static MarketAdminActionPayload add(String id, String offerType, String itemId, long price, String category) {
        return new MarketAdminActionPayload(ADD, id, offerType, itemId, price, category);
    }

    public static MarketAdminActionPayload remove(String id) {
        return new MarketAdminActionPayload(REMOVE, id, "", "", 0L, "");
    }

    public static MarketAdminActionPayload reload() {
        return new MarketAdminActionPayload(RELOAD, "", "", "", 0L, "");
    }

    public static MarketAdminActionPayload renameCategory(String oldCategory, String newCategory) {
        return new MarketAdminActionPayload(RENAME_CATEGORY, oldCategory, "", "", 0L, newCategory);
    }

    public static MarketAdminActionPayload resetCategory(String category) {
        return new MarketAdminActionPayload(RESET_CATEGORY, category, "", "", 0L, "");
    }

    public static MarketAdminActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new MarketAdminActionPayload(
                buffer.readUtf(32),
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readUtf(128),
                buffer.readLong(),
                buffer.readUtf(64));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeUtf(id, 64);
        buffer.writeUtf(offerType, 32);
        buffer.writeUtf(itemId, 128);
        buffer.writeLong(price);
        buffer.writeUtf(category, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
