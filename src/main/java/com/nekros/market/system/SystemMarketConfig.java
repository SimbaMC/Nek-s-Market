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
        return addOfferLine(id, line, normalizedCategory);
    }

    public static Result addPricedOffer(String id, String type, String itemId, String category, PriceMode mode,
            long basePrice, double multiplier, long minPrice, long maxPrice, String flags) {
        String normalizedCategory = normalizeCategory(type, category);
        String line = id.trim()
                + "|" + type.trim()
                + "|" + itemId.trim()
                + "|" + normalizedCategory
                + "|" + mode.name()
                + "|" + basePrice
                + "|" + multiplier
                + "|" + minPrice
                + "|" + maxPrice
                + "|" + flags.trim();
        return addOfferLine(id, line, normalizedCategory);
    }

    public static Result addPricedOffers(List<OfferDraft> drafts) {
        if (drafts.isEmpty()) {
            return Result.fail("没有可添加的系统货架。");
        }

        List<String> offers = mutableOffers();
        int added = 0;
        int skipped = 0;
        for (OfferDraft draft : drafts) {
            String normalizedCategory = normalizeCategory(draft.type(), draft.category());
            String line = draft.id().trim()
                    + "|" + draft.type().trim()
                    + "|" + draft.itemId().trim()
                    + "|" + normalizedCategory
                    + "|" + draft.mode().name()
                    + "|" + draft.basePrice()
                    + "|" + draft.multiplier()
                    + "|" + draft.minPrice()
                    + "|" + draft.maxPrice()
                    + "|" + draft.flags().trim();
            SystemMarketOffer offer = SystemMarketOffer.parse(line);
            if (offer == null) {
                skipped++;
                continue;
            }
            if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS && !knownCategory(normalizedCategory)) {
                skipped++;
                continue;
            }
            offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(draft.id().trim()));
            offers.removeIf(value -> sameShelfItem(value, line));
            offers.add(line);
            added++;
        }

        if (added <= 0) {
            return Result.fail("没有成功添加任何系统货架。");
        }
        saveOffers(offers);
        String skippedText = skipped > 0 ? "，跳过 " + skipped + " 个" : "";
        return Result.success("已批量添加 " + added + " 个系统货架" + skippedText + "。");
    }

    private static Result addOfferLine(String id, String line, String normalizedCategory) {
        if (SystemMarketOffer.parse(line) == null) {
            return Result.fail("无效系统货架。");
        }
        SystemMarketOffer offer = SystemMarketOffer.parse(line);
        if (offer != null && offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS && !knownCategory(normalizedCategory)) {
            return Result.fail("未知系统分类: " + normalizedCategory + "。可用分类: " + String.join(", ", knownCategories()));
        }

        List<String> offers = mutableOffers();
        offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(id.trim()));
        offers.removeIf(value -> sameShelfItem(value, line));
        offers.add(line);
        saveOffers(offers);
        String categoryNote = normalizedCategory.isEmpty() ? "" : "，分类 " + normalizedCategory;
        return Result.success("已保存系统货架: " + id.trim() + categoryNote);
    }

    public static Result removeOffer(String id) {
        String trimmedId = id.trim();
        List<String> offers = mutableOffers();
        boolean removed = offers.removeIf(value -> value.split("\\|", -1)[0].trim().equals(trimmedId));
        if (!removed) {
            return Result.fail("找不到系统货架 ID: " + trimmedId);
        }
        saveOffers(offers);
        return Result.success("已移除系统货架: " + trimmedId);
    }

    public static Result renameCategory(String oldCategory, String newCategory) {
        String oldName = oldCategory.trim();
        String newName = newCategory.trim();
        if (oldName.isEmpty() || newName.isEmpty()) {
            return Result.fail("分类名不能为空。");
        }
        List<String> categories = mutableCategories();
        int index = categories.indexOf(oldName);
        if (index < 0) {
            return Result.fail("找不到系统分类: " + oldName);
        }
        categories.set(index, newName);
        saveCategories(categories);
        renameOfferCategories(oldName, newName);
        return Result.success("已将系统分类 " + oldName + " 重命名为 " + newName + "。");
    }

    public static Result resetCategory(String category) {
        String oldName = category.trim();
        List<String> categories = mutableCategories();
        int index = categories.indexOf(oldName);
        if (index < 0) {
            return Result.fail("找不到系统分类: " + oldName);
        }
        String defaultName = "#" + (index + 1);
        categories.set(index, defaultName);
        saveCategories(categories);
        renameOfferCategories(oldName, defaultName);
        return Result.success("已将系统分类 " + oldName + " 重置为 " + defaultName + "。");
    }

    public static Result reload() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(NeksMarket.MODID + "-common.toml");
        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath).sync().autosave().build()) {
            config.load();
            List<String> categories = nonEmptyCategories(stringList(config.get("systemMarket.buyCategories")));
            List<String> offers = deduplicateShelfItems(stringList(config.get("systemMarket.offers")));
            Config.SYSTEM_BUY_CATEGORIES.set(categories);
            Config.SYSTEM_OFFERS.set(offers);
            return Result.success("已重载系统商店配置。");
        } catch (RuntimeException exception) {
            NeksMarket.LOGGER.warn("Failed to reload system market config.", exception);
            return Result.fail("无法重载系统商店配置: " + exception.getMessage());
        }
    }

    private static List<String> mutableOffers() {
        return new ArrayList<>(Config.SYSTEM_OFFERS.get().stream().map(String::valueOf).toList());
    }

    private static List<String> mutableCategories() {
        return new ArrayList<>(nonEmptyCategories(Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList()));
    }

    private static String normalizeCategory(String type, String category) {
        String trimmedCategory = category.trim();
        if (isSystemSelling(type) && !trimmedCategory.isEmpty() && trimmedCategory.chars().allMatch(Character::isDigit)) {
            int slot = Integer.parseInt(trimmedCategory);
            List<String> categories = mutableCategories();
            if (slot >= 1 && slot <= categories.size()) {
                return categories.get(slot - 1).trim();
            }
            return "#" + trimmedCategory;
        }
        if (!isSystemSelling(type) || !trimmedCategory.isEmpty()) {
            return trimmedCategory;
        }
        List<String> categories = mutableCategories().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return categories.isEmpty() ? "#1" : categories.getFirst();
    }

    private static boolean knownCategory(String category) {
        return knownCategories().contains(category);
    }

    private static List<String> knownCategories() {
        return nonEmptyCategories(mutableCategories());
    }

    private static boolean isSystemSelling(String type) {
        String trimmedType = type.trim();
        return "sell_to_player".equalsIgnoreCase(trimmedType)
                || "system_sells".equalsIgnoreCase(trimmedType);
    }

    private static void saveOffers(List<String> offers) {
        ensureCategoriesConfigured();
        Config.SYSTEM_OFFERS.set(deduplicateShelfItems(offers));
        Config.SPEC.save();
    }

    private static void saveCategories(List<String> categories) {
        Config.SYSTEM_BUY_CATEGORIES.set(nonEmptyCategories(categories));
        Config.SPEC.save();
    }

    private static void ensureCategoriesConfigured() {
        List<String> categories = Config.SYSTEM_BUY_CATEGORIES.get().stream().map(String::valueOf).toList();
        if (categories.stream().map(String::trim).noneMatch(value -> !value.isEmpty())) {
            Config.SYSTEM_BUY_CATEGORIES.set(Config.defaultSystemBuyCategories());
        }
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

    private static List<String> nonEmptyCategories(List<String> categories) {
        List<String> normalized = categories.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return normalized.isEmpty() ? Config.defaultSystemBuyCategories() : normalized;
    }

    public record Result(boolean success, String message) {
        static Result success(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    public record OfferDraft(
            String id,
            String type,
            String itemId,
            String category,
            PriceMode mode,
            long basePrice,
            double multiplier,
            long minPrice,
            long maxPrice,
            String flags) {
    }
}
