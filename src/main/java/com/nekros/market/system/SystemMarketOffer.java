package com.nekros.market.system;

import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SystemMarketOffer(String id, ItemStack item, long unitPrice, Type type) {
    public enum Type {
        SYSTEM_SELLS,
        SYSTEM_BUYS
    }

    public static final List<SystemMarketOffer> OFFERS = List.of(
            new SystemMarketOffer("buy_diamond", new ItemStack(Items.DIAMOND), 500L, Type.SYSTEM_SELLS),
            new SystemMarketOffer("buy_oak_log", new ItemStack(Items.OAK_LOG), 5L, Type.SYSTEM_SELLS),
            new SystemMarketOffer("sell_wheat", new ItemStack(Items.WHEAT), 2L, Type.SYSTEM_BUYS),
            new SystemMarketOffer("sell_cobblestone", new ItemStack(Items.COBBLESTONE), 1L, Type.SYSTEM_BUYS));

    public static SystemMarketOffer byId(String id) {
        for (SystemMarketOffer offer : OFFERS) {
            if (offer.id.equals(id)) {
                return offer;
            }
        }
        return null;
    }
}
