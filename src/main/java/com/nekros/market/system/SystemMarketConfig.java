package com.nekros.market.system;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.nekros.market.Config;
import com.nekros.market.NeksMarket;

import net.neoforged.fml.loading.FMLPaths;

public final class SystemMarketConfig {
    private SystemMarketConfig() {
    }

    public static Result addOffer(String id, String type, String itemId, long unitPrice, String category) {
        String line = id.trim() + "|" + type.trim() + "|" + itemId.trim() + "|" + unitPrice + "|" + category.trim();
        if (SystemMarketOffer.parse(line) == null) {
            return Result.fail("Invalid system offer.");
        }

        List<String> offers = mutableOffers();
        offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(id.trim()));
        offers.add(line);
        saveOffers(offers);
        return Result.success("Saved system offer: " + id.trim());
    }

    public static Result removeOffer(String id) {
        String trimmedId = id.trim();
        List<String> offers = mutableOffers();
        boolean removed = offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(trimmedId));
        if (!removed) {
            return Result.fail("No system offer found with id: " + trimmedId);
        }
        saveOffers(offers);
        return Result.success("Removed system offer: " + trimmedId);
    }

    public static Result renameCategory(String oldCategory, String newCategory) {
        String oldName = oldCategory.trim();
        String newName = newCategory.trim();
        if (oldName.isEmpty() || newName.isEmpty()) {
            return Result.fail("Category names cannot be empty.");
        }
        List<String> categories = mutableCategories();
        int index = categories.indexOf(oldName);
        if (index < 0) {
            return Result.fail("No system category found: " + oldName);
        }
        categories.set(index, newName);
        saveCategories(categories);
        renameOfferCategories(oldName, newName);
        return Result.success("Renamed system category " + oldName + " to " + newName + ".");
    }

    public static Result resetCategory(String category) {
        String oldName = category.trim();
        List<String> categories = mutableCategories();
        int index = categories.indexOf(oldName);
        if (index < 0) {
            return Result.fail("No system category found: " + oldName);
        }
        String defaultName = "#" + (index + 1);
        categories.set(index, defaultName);
        saveCategories(categories);
        renameOfferCategories(oldName, defaultName);
        return Result.success("Reset system category " + oldName + " to " + defaultName + ".");
    }

    public static Result reload() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(NeksMarket.MODID + "-common.toml");
        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().autosave().build()) {
            config.load();
            List<String> categories = stringList(config.get("systemMarket.buyCategories"));
            List<String> offers = stringList(config.get("systemMarket.offers"));
            Config.SYSTEM_BUY_CATEGORIES.set(categories);
            Config.SYSTEM_OFFERS.set(offers);
            return Result.success("Reloaded system market config.");
        } catch (RuntimeException exception) {
            NeksMarket.LOGGER.warn("Failed to reload system market config.", exception);
            return Result.fail("Could not reload system market config: " + exception.getMessage());
        }
    }

    private static List<String> mutableOffers() {
        return new ArrayList<>(Config.SYSTEM_OFFERS.get().stream().map(String::valueOf).toList());
    }

    private static List<String> mutableCategories() {
        return new ArrayList<>(Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList());
    }

    private static void saveOffers(List<String> offers) {
        Config.SYSTEM_OFFERS.set(List.copyOf(offers));
        Config.SPEC.save();
    }

    private static void saveCategories(List<String> categories) {
        Config.SYSTEM_BUY_CATEGORIES.set(List.copyOf(categories));
        Config.SPEC.save();
    }

    private static void renameOfferCategories(String oldName, String newName) {
        List<String> renamed = new ArrayList<>();
        for (String offer : mutableOffers()) {
            String[] parts = offer.split("\\|", -1);
            if (parts.length == 5 && parts[4].trim().equals(oldName)) {
                parts[4] = newName;
                renamed.add(String.join("|", parts));
            } else {
                renamed.add(offer);
            }
        }
        saveOffers(renamed);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    public record Result(boolean success, String message) {
        static Result success(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
