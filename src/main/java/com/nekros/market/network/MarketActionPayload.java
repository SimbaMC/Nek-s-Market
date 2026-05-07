package com.nekros.market.network;

import java.util.Optional;
import java.util.UUID;

import com.nekros.market.NeksMarket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketActionPayload(String action, int page, Optional<UUID> listingId, String offerId, long price, int count) implements CustomPacketPayload {
    public static final String REFRESH = "refresh";
    public static final String BUY = "buy";
    public static final String SELL = "sell";
    public static final String SYSTEM_TRADE = "system_trade";

    public static final Type<MarketActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeksMarket.MODID, "market_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketActionPayload> STREAM_CODEC = StreamCodec.ofMember(
            MarketActionPayload::write,
            MarketActionPayload::read);

    public static MarketActionPayload refresh(int page) {
        return new MarketActionPayload(REFRESH, page, Optional.empty(), "", 0L, 0);
    }

    public static MarketActionPayload buy(UUID listingId, int page) {
        return buy(listingId, 1, page);
    }

    public static MarketActionPayload buy(UUID listingId, int count, int page) {
        return new MarketActionPayload(BUY, page, Optional.of(listingId), "", 0L, count);
    }

    public static MarketActionPayload sell(long price, int count, int page) {
        return new MarketActionPayload(SELL, page, Optional.empty(), "", price, count);
    }

    public static MarketActionPayload systemTrade(String offerId, int count, int page) {
        return new MarketActionPayload(SYSTEM_TRADE, page, Optional.empty(), offerId, 0L, count);
    }

    public static MarketActionPayload read(RegistryFriendlyByteBuf buffer) {
        String action = buffer.readUtf(32);
        int page = buffer.readVarInt();
        Optional<UUID> listingId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        String offerId = buffer.readUtf(64);
        long price = buffer.readLong();
        int count = buffer.readVarInt();
        return new MarketActionPayload(action, page, listingId, offerId, price, count);
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(action, 32);
        buffer.writeVarInt(page);
        buffer.writeBoolean(listingId.isPresent());
        listingId.ifPresent(buffer::writeUUID);
        buffer.writeUtf(offerId, 64);
        buffer.writeLong(price);
        buffer.writeVarInt(count);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
