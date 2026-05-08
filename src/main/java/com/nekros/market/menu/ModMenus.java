package com.nekros.market.menu;

import com.nekros.market.NeksMarket;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NeksMarket.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MarketMenu>> MARKET = MENUS.register(
            "market",
            () -> new MenuType<>((IContainerFactory<MarketMenu>) MarketMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<SellerBoxMenu>> SELLER_BOX = MENUS.register(
            "seller_box",
            () -> new MenuType<>((IContainerFactory<SellerBoxMenu>) SellerBoxMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
