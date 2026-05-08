package com.nekros.market;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nekros.market.block.ModBlocks;
import com.nekros.market.block.SellerBoxBlock;
import com.nekros.market.block.entity.ModBlockEntities;
import com.nekros.market.block.entity.SellerBoxBlockEntity;
import com.nekros.market.command.MarketCommands;
import com.nekros.market.item.ModItems;
import com.nekros.market.menu.ModMenus;
import com.nekros.market.network.ModNetworking;
import com.nekros.market.pricing.PriceRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.items.IItemHandler;

@Mod(NeksMarket.MODID)
public class NeksMarket {
    public static final String MODID = "neksmarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeksMarket(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.SELLER_BOX.get());
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SELLER_BOX.get(),
                (sellerBox, side) -> sellerBox.itemHandler());

        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    if (state.getValue(SellerBoxBlock.HALF) != DoubleBlockHalf.UPPER) {
                        return null;
                    }
                    BlockPos lowerPos = pos.below();
                    BlockEntity lowerEntity = level.getBlockEntity(lowerPos);
                    if (lowerEntity instanceof SellerBoxBlockEntity sellerBox) {
                        return (IItemHandler) sellerBox.itemHandler();
                    }
                    return null;
                },
                ModBlocks.SELLER_BOX.get());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MarketCommands.register(event.getDispatcher(), event.getBuildContext());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        PriceRegistry.reload();
        LOGGER.info("Nek's Market pricing registry loaded. Price graph warmup is skipped during world startup.");
    }
}
