package com.nekros.market;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nekros.market.command.MarketCommands;
import com.nekros.market.menu.ModMenus;
import com.nekros.market.network.ModNetworking;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@Mod(NeksMarket.MODID)
public class NeksMarket {
    public static final String MODID = "neksmarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeksMarket(IEventBus modEventBus, ModContainer modContainer) {
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        MarketCommands.register(event.getDispatcher(), event.getBuildContext());
    }
}
