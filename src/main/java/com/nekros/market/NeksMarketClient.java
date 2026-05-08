package com.nekros.market;

import java.util.LinkedHashMap;
import java.util.Map;

import com.nekros.market.client.MarketScreen;
import com.nekros.market.client.SellerBoxScreen;
import com.nekros.market.menu.ModMenus;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceResolver;
import com.nekros.market.pricing.system.SystemPriceService;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = NeksMarket.MODID, dist = Dist.CLIENT)
public class NeksMarketClient {
    private static final int TOOLTIP_CACHE_LIMIT = 2048;
    private static final Map<ResourceLocation, String> RECYCLE_TOOLTIP_CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, String> eldest) {
            return size() > TOOLTIP_CACHE_LIMIT;
        }
    };

    public NeksMarketClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerScreens);
        NeoForge.EVENT_BUS.addListener(this::addRecyclePriceTooltip);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MARKET.get(), MarketScreen::new);
        event.register(ModMenus.SELLER_BOX.get(), SellerBoxScreen::new);
    }

    private void addRecyclePriceTooltip(ItemTooltipEvent event) {
        if (event.getEntity() == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        event.getToolTip().add(Component.literal(recyclePriceText(stack)).withStyle(ChatFormatting.GOLD));
    }

    private static String recyclePriceText(ItemStack stack) {
        if (!stack.isComponentsPatchEmpty()) {
            return "回收价：暂无";
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String cached = RECYCLE_TOOLTIP_CACHE.get(itemId);
        if (cached != null) {
            return cached;
        }

        long price = recyclePrice(stack);
        String text = price > 0L ? "回收价：" + price : "回收价：暂无";
        RECYCLE_TOOLTIP_CACHE.put(itemId, text);
        return text;
    }

    private static long recyclePrice(ItemStack stack) {

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        PriceProfile profile = server == null && Minecraft.getInstance().level != null
                ? PriceResolver.resolve(Minecraft.getInstance().level, stack)
                : PriceResolver.resolve(server, stack);
        if (!PriceResolver.allowsSystemBuy(profile.tradeLevel()) || profile.systemBuyPrice() <= 0L) {
            return 0L;
        }
        if (server == null) {
            return profile.systemBuyPrice();
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SystemPriceService.dynamicBuyPriceForStock(server, itemId, profile.systemBuyPrice());
    }
}
