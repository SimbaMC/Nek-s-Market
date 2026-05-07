package com.nekros.market.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class MarketMenu extends AbstractContainerMenu {
    private MarketMenuSnapshot snapshot;

    public MarketMenu(int containerId, Inventory inventory) {
        this(containerId, MarketMenuSnapshot.empty());
    }

    public MarketMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, buffer == null ? MarketMenuSnapshot.empty() : MarketMenuSnapshot.read(buffer));
    }

    public MarketMenu(int containerId, MarketMenuSnapshot snapshot) {
        super(ModMenus.MARKET.get(), containerId);
        this.snapshot = snapshot;
    }

    public MarketMenuSnapshot snapshot() {
        return snapshot;
    }

    public void updateSnapshot(MarketMenuSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
