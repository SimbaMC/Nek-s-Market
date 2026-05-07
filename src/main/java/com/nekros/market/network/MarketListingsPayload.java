package com.nekros.market.network;

import com.nekros.market.NeksMarket;
import com.nekros.market.menu.MarketMenuSnapshot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketListingsPayload(MarketMenuSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<MarketListingsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeksMarket.MODID, "market_listings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingsPayload> STREAM_CODEC = StreamCodec.ofMember(
            MarketListingsPayload::write,
            MarketListingsPayload::read);

    public static MarketListingsPayload read(RegistryFriendlyByteBuf buffer) {
        return new MarketListingsPayload(MarketMenuSnapshot.read(buffer));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        snapshot.write(buffer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
