package com.nekros.market.network;

import com.nekros.market.NeksMarket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SellerBoxSellPayload() implements CustomPacketPayload {
    public static final Type<SellerBoxSellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeksMarket.MODID, "seller_box_sell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SellerBoxSellPayload> STREAM_CODEC = StreamCodec.unit(new SellerBoxSellPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
