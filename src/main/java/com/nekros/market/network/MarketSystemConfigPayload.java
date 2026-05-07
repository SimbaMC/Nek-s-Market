package com.nekros.market.network;

import java.util.ArrayList;
import java.util.List;

import com.nekros.market.NeksMarket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketSystemConfigPayload(List<String> categories, List<String> offers, List<String> fallbackOffers) implements CustomPacketPayload {
    public static final Type<MarketSystemConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeksMarket.MODID, "market_system_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSystemConfigPayload> STREAM_CODEC = StreamCodec.ofMember(
            MarketSystemConfigPayload::write,
            MarketSystemConfigPayload::read);

    public static MarketSystemConfigPayload read(RegistryFriendlyByteBuf buffer) {
        int categoryCount = buffer.readVarInt();
        List<String> categories = new ArrayList<>(categoryCount);
        for (int i = 0; i < categoryCount; i++) {
            categories.add(buffer.readUtf(64));
        }
        int offerCount = buffer.readVarInt();
        List<String> offers = new ArrayList<>(offerCount);
        for (int i = 0; i < offerCount; i++) {
            offers.add(buffer.readUtf(256));
        }
        int fallbackOfferCount = buffer.readVarInt();
        List<String> fallbackOffers = new ArrayList<>(fallbackOfferCount);
        for (int i = 0; i < fallbackOfferCount; i++) {
            fallbackOffers.add(buffer.readUtf(256));
        }
        return new MarketSystemConfigPayload(List.copyOf(categories), List.copyOf(offers), List.copyOf(fallbackOffers));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(categories.size());
        for (String category : categories) {
            buffer.writeUtf(category, 64);
        }
        buffer.writeVarInt(offers.size());
        for (String offer : offers) {
            buffer.writeUtf(offer, 256);
        }
        buffer.writeVarInt(fallbackOffers.size());
        for (String offer : fallbackOffers) {
            buffer.writeUtf(offer, 256);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
