package com.nekros.market.block.entity;

import com.nekros.market.menu.SellerBoxMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

public class SellerBoxBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(SellerBoxMenu.BOX_SLOT_COUNT, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);

    public SellerBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SELLER_BOX.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return SellerBoxMenu.BOX_SLOT_COUNT;
    }

    @Override
    protected Component getDefaultName() {
        return Component.literal("系统回收箱");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SellerBoxMenu(containerId, inventory, this, ContainerLevelAccess.create(level, worldPosition));
    }

    public InvWrapper itemHandler() {
        return itemHandler;
    }
}
