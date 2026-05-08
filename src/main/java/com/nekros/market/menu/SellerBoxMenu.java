package com.nekros.market.menu;

import com.nekros.market.block.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class SellerBoxMenu extends AbstractContainerMenu {
    public static final int ROWS = 6;
    public static final int BOX_SLOT_COUNT = ROWS * 9;

    private final Container container;
    private final ContainerLevelAccess access;

    public SellerBoxMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(BOX_SLOT_COUNT), ContainerLevelAccess.NULL);
    }

    public SellerBoxMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, new SimpleContainer(BOX_SLOT_COUNT), ContainerLevelAccess.NULL);
    }

    public SellerBoxMenu(int containerId, Inventory inventory, Container container, ContainerLevelAccess access) {
        super(ModMenus.SELLER_BOX.get(), containerId);
        checkContainerSize(container, BOX_SLOT_COUNT);
        this.container = container;
        this.access = access;
        container.startOpen(inventory.player);

        int inventoryYOffset = (ROWS - 4) * 18;
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + inventoryYOffset));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 161 + inventoryYOffset));
        }
    }

    public Container container() {
        return container;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValidAtBlock(access, player, ModBlocks.SELLER_BOX.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BOX_SLOT_COUNT) {
                if (!moveItemStackTo(stack, BOX_SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, BOX_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public static ContainerLevelAccess access(Player player, BlockPos pos) {
        return ContainerLevelAccess.create(player.level(), pos);
    }

    private static boolean stillValidAtBlock(ContainerLevelAccess access, Player player, Block block) {
        return access.evaluate((level, pos) -> level.getBlockState(pos).is(block) && player.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D) <= 64.0D, true);
    }
}
