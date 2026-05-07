package com.nekros.market.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceResolver;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PriceAdminCommands {
    private PriceAdminCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> priceCommand() {
        return Commands.literal("price")
                .then(Commands.literal("hand")
                        .executes(context -> priceHand(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reload(context.getSource())));
    }

    private static int priceHand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Main hand is empty."));
            return 0;
        }

        PriceProfile profile = PriceResolver.resolve(source.getServer(), stack);
        source.sendSuccess(() -> Component.literal("Item: " + BuiltInRegistries.ITEM.getKey(stack.getItem())), false);
        source.sendSuccess(() -> Component.literal("Source: " + profile.source()), false);
        source.sendSuccess(() -> Component.literal("Confidence: " + profile.confidence()), false);
        source.sendSuccess(() -> Component.literal("Trade Level: " + profile.tradeLevel()), false);
        source.sendSuccess(() -> Component.literal("Floor: " + profile.floorPrice()), false);
        source.sendSuccess(() -> Component.literal("Derived: " + profile.derivedPrice()), false);
        source.sendSuccess(() -> Component.literal("Market: " + profile.marketPrice()), false);
        source.sendSuccess(() -> Component.literal("Reference: " + profile.referencePrice()), false);
        source.sendSuccess(() -> Component.literal("System Buy: " + profile.systemBuyPrice()), false);
        source.sendSuccess(() -> Component.literal("System Sell: " + profile.systemSellPrice()), false);
        source.sendSuccess(() -> Component.literal("Explanation: " + profile.explanation()), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        PriceRegistry.reload();
        source.sendSuccess(() -> Component.literal("Reloaded Nek's Market pricing registry."), true);
        return 1;
    }
}
