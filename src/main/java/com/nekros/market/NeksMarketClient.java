package com.nekros.market;

import com.nekros.market.client.MarketScreen;
import com.nekros.market.menu.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = NeksMarket.MODID, dist = Dist.CLIENT)
public class NeksMarketClient {
    public NeksMarketClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerScreens);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MARKET.get(), MarketScreen::new);
    }
}
