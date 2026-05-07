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
        String normalizedCategory = normalizeCategory(type, category);
        String line = id.trim() + "|" + type.trim() + "|" + itemId.trim() + "|" + unitPrice + "|" + normalizedCategory;
        if (SystemMarketOffer.parse(line) == null) {
            return Result.fail("Invalid system offer.");
        }

        List<String> offers = mutableOffers();
        offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(id.trim()));
        offers.removeIf(value -> sameShelfItem(value, line));
        offers.add(line);
        saveOffers(offers);
        String categoryNote = normalizedCategory.isEmpty() ? "" : " in category " + normalizedCategory;
        return Result.success("Saved system offer: " + id.trim() + categoryNote);
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
            List<String> offers = deduplicateShelfItems(stringList(config.get("systemMarket.offers")));
            Config.SYSTEM_BUY_CATEGORIES.set(categories);
            Config.SYSTEM_OFFERS.set(offers);
            Config.SPEC.save();
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

    private static String normalizeCategory(String type, String category) {
        String trimmedCategory = category.trim();
        if (!isSystemSelling(type) || !trimmedCategory.isEmpty()) {
            return trimmedCategory;
        }
        List<String> categories = mutableCategories().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return categories.isEmpty() ? "#1" : categories.getFirst();
    }

    private static boolean isSystemSelling(String type) {
        String trimmedType = type.trim();
        return "sell_to_player".equalsIgnoreCase(trimmedType)
                || "system_sells".equalsIgnoreCase(trimmedType);
    }

    private static void saveOffers(List<String> offers) {
        Config.SYSTEM_OFFERS.set(deduplicateShelfItems(offers));
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
            } else if (parts.length == 10 && parts[3].trim().equals(oldName)) {
                parts[3] = newName;
                renamed.add(String.join("|", parts));
            } else {
                renamed.add(offer);
            }
        }
        saveOffers(renamed);
    }

    private static boolean sameShelfItem(String leftLine, String rightLine) {
        SystemMarketOffer left = SystemMarketOffer.parse(leftLine);
        SystemMarketOffer right = SystemMarketOffer.parse(rightLine);
        return left != null
                && right != null
                && left.type() == right.type()
                && left.item().is(right.item().getItem());
    }

    private static List<String> deduplicateShelfItems(List<String> offers) {
        List<String> result = new ArrayList<>();
        for (String offer : offers) {
            if (SystemMarketOffer.parse(offer) == null) {
                continue;
            }
            result.removeIf(existing -> sameShelfItem(existing, offer));
            result.add(offer);
        }
        return List.copyOf(result);
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
