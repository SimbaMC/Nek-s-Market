package com.nekros.market.client;

import com.nekros.market.menu.SellerBoxMenu;
import com.nekros.market.network.SellerBoxSellPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class SellerBoxScreen extends AbstractContainerScreen<SellerBoxMenu> {
    private static final ResourceLocation CONTAINER_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public SellerBoxScreen(SellerBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + SellerBoxMenu.ROWS * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("售出"), ignored -> PacketDistributor.sendToServer(new SellerBoxSellPayload()))
                .bounds(leftPos + imageWidth - 48, topPos + 4, 40, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(CONTAINER_BACKGROUND, leftPos, topPos, 0, 0, imageWidth, SellerBoxMenu.ROWS * 18 + 17);
        guiGraphics.blit(CONTAINER_BACKGROUND, leftPos, topPos + SellerBoxMenu.ROWS * 18 + 17, 0, 126, imageWidth, 96);
    }
}
