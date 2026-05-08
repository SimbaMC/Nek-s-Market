package com.nekros.market.pricing.derived;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.nekros.market.pricing.PriceConfidence;
import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceRegistry;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;
import com.nekros.market.pricing.config.PricingConfig;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class DerivedPriceService {
    private static final int MAX_DEPTH = 8;
    private static final Map<ResourceLocation, PriceProfile> CACHE = new HashMap<>();
    private static final Map<RecipeManager, RecipeIndex> INDEX_CACHE = new IdentityHashMap<>();

    private DerivedPriceService() {
    }

    public static void clearCache() {
        CACHE.clear();
        INDEX_CACHE.clear();
    }

    public static PriceProfile resolve(MinecraftServer server, ResourceLocation itemId) {
        if (server == null) {
            return PriceProfile.unknown(itemId);
        }
        return resolve(context(server.getRecipeManager(), server.registryAccess()), itemId);
    }

    public static PriceProfile resolve(Level level, ResourceLocation itemId) {
        if (level == null) {
            return PriceProfile.unknown(itemId);
        }
        return resolve(context(level.getRecipeManager(), level.registryAccess()), itemId);
    }

    public static WarmupResult warmup(MinecraftServer server) {
        if (server == null) {
            return new WarmupResult(0, 0, 0);
        }

        RecipeContext context = context(server.getRecipeManager(), server.registryAccess());
        Set<ResourceLocation> candidates = candidateIds(context);

        int resolved = 0;
        for (ResourceLocation itemId : candidates) {
            PriceProfile profile = baselineForCoverage(context, itemId);
            if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                resolved++;
            }
        }
        return new WarmupResult(candidates.size(), resolved, CACHE.size());
    }

    public static CoverageReport coverage(MinecraftServer server, int unresolvedLimit) {
        if (server == null) {
            return new CoverageReport(0, 0, 0, 0, Map.of(), List.of());
        }

        RecipeContext context = context(server.getRecipeManager(), server.registryAccess());
        Set<ResourceLocation> candidates = candidateIds(context);
        Map<PriceSource, Integer> sourceCounts = new EnumMap<>(PriceSource.class);
        List<ResourceLocation> unresolved = new ArrayList<>();
        int resolved = 0;

        for (ResourceLocation itemId : candidates) {
            PriceProfile profile = baselineForCoverage(context, itemId);
            if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                resolved++;
                sourceCounts.merge(profile.source(), 1, Integer::sum);
            } else if (unresolvedLimit > 0 && unresolved.size() < unresolvedLimit) {
                unresolved.add(itemId);
            }
        }

        return new CoverageReport(
                candidates.size(),
                resolved,
                candidates.size() - resolved,
                CACHE.size(),
                Map.copyOf(sourceCounts),
                List.copyOf(unresolved));
    }

    public static CoverageReport fastCoverage(MinecraftServer server, int unresolvedLimit) {
        if (server == null) {
            return new CoverageReport(0, 0, 0, 0, Map.of(), List.of());
        }

        RecipeContext context = context(server.getRecipeManager(), server.registryAccess());
        Set<ResourceLocation> candidates = candidateIds(context);
        Map<PriceSource, Integer> sourceCounts = new EnumMap<>(PriceSource.class);
        List<ResourceLocation> unresolved = new ArrayList<>();
        int resolved = 0;

        for (ResourceLocation itemId : candidates) {
            PriceProfile profile = directBaseline(itemId);
            if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                resolved++;
                sourceCounts.merge(profile.source(), 1, Integer::sum);
            } else if (unresolvedLimit > 0 && unresolved.size() < unresolvedLimit) {
                unresolved.add(itemId);
            }
        }

        return new CoverageReport(
                candidates.size(),
                resolved,
                candidates.size() - resolved,
                CACHE.size(),
                Map.copyOf(sourceCounts),
                List.copyOf(unresolved));
    }

    private static Set<ResourceLocation> candidateIds(RecipeContext context) {
        Set<ResourceLocation> candidates = new LinkedHashSet<>();
        candidates.addAll(PriceRegistry.anchorIds());
        candidates.addAll(NaturalPriceSource.configuredItemIds());
        candidates.addAll(context.index().crafting().keySet());
        candidates.addAll(context.index().cooking().keySet());
        return candidates;
    }

    private static PriceProfile baselineForCoverage(RecipeContext context, ResourceLocation itemId) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        if (anchor.source() != PriceSource.UNKNOWN && anchor.referencePrice() > 0L) {
            return anchor;
        }
        PriceProfile derived = resolve(context, itemId);
        if (derived.source() != PriceSource.UNKNOWN && derived.referencePrice() > 0L) {
            return derived;
        }
        return NaturalPriceSource.resolve(itemId);
    }

    private static PriceProfile directBaseline(ResourceLocation itemId) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        if (anchor.source() != PriceSource.UNKNOWN && anchor.referencePrice() > 0L) {
            return anchor;
        }
        PriceProfile equivalent = EquivalentItemPriceSource.resolve(itemId);
        if (equivalent.source() != PriceSource.UNKNOWN && equivalent.referencePrice() > 0L) {
            return equivalent;
        }
        return NaturalPriceSource.resolve(itemId);
    }

    private static RecipeContext context(RecipeManager recipeManager, HolderLookup.Provider registries) {
        return new RecipeContext(registries, INDEX_CACHE.computeIfAbsent(recipeManager,
                ignored -> RecipeIndex.build(recipeManager, registries)));
    }

    private static PriceProfile resolve(RecipeContext context, ResourceLocation itemId) {
        return resolveCached(context, itemId, new HashSet<>(), 0);
    }

    private static PriceProfile resolveCached(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        PriceProfile cached = CACHE.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (depth > MAX_DEPTH || visiting.contains(itemId)) {
            return PriceProfile.unknown(itemId);
        }

        PriceProfile resolved = resolveUncached(context, itemId, visiting, depth);
        CACHE.put(itemId, resolved);
        return resolved;
    }

    private static PriceProfile resolveUncached(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        if (depth > MAX_DEPTH || !visiting.add(itemId)) {
            return PriceProfile.unknown(itemId);
        }

        PriceProfile equivalent = EquivalentItemPriceSource.resolve(itemId);
        if (equivalent.source() != PriceSource.UNKNOWN) {
            visiting.remove(itemId);
            return equivalent;
        }

        RecipeCandidate best = cheapest(bestCraftingCandidate(context, itemId, visiting, depth + 1), null);
        best = cheapest(bestCookingCandidate(context, itemId, visiting, depth + 1), best);

        visiting.remove(itemId);
        if (best == null) {
            PriceProfile natural = NaturalPriceSource.resolve(itemId);
            if (natural.source() != PriceSource.UNKNOWN) {
                return natural;
            }
            return PriceProfile.unknown(itemId);
        }
        return toProfile(itemId, best);
    }

    private static RecipeCandidate bestCraftingCandidate(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        RecipeCandidate best = null;
        for (IndexedCraftingRecipe recipe : context.index().crafting().getOrDefault(itemId, List.of())) {
            best = cheapest(candidateFromRecipe(context, recipe.id(), recipe.recipe(), recipe.resultCount(), visiting, depth), best);
        }
        return best;
    }

    private static RecipeCandidate bestCookingCandidate(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        RecipeCandidate best = null;
        for (IndexedCookingRecipe recipe : context.index().cooking().getOrDefault(itemId, List.of())) {
            best = cheapest(candidateFromCookingRecipe(context, recipe.id(), recipe.kind(), recipe.recipe(), recipe.resultCount(), visiting, depth),
                    best);
        }
        return best;
    }

    private static RecipeCandidate candidateFromRecipe(RecipeContext context, ResourceLocation recipeId, CraftingRecipe recipe,
            int resultCount, Set<ResourceLocation> visiting, int depth) {
        if (resultCount <= 0 || recipe.isSpecial()) {
            return null;
        }

        long ingredientTotal = 0L;
        int ingredientSlots = 0;
        Map<ResourceLocation, IngredientSummary> ingredientSummaries = new LinkedHashMap<>();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            IngredientChoice choice = cheapestIngredient(context, ingredient, visiting, depth);
            if (choice == null) {
                return null;
            }
            ingredientTotal += choice.price();
            ingredientSlots++;
            ingredientSummaries.compute(choice.itemId(), (id, existing) -> existing == null
                    ? new IngredientSummary(1, choice.price())
                    : new IngredientSummary(existing.count() + 1, existing.totalPrice() + choice.price()));
        }

        if (ingredientTotal <= 0L) {
            return null;
        }
        long flatProcessingFee = Math.max(0L, PricingConfig.derivedProcessingFeePerIngredient()) * ingredientSlots;
        double markup = Math.max(0.0D, PricingConfig.derivedProcessingMarkup());
        long total = Math.max(1L, (long) Math.ceil((ingredientTotal + flatProcessingFee) * (1.0D + markup)));
        long unitPrice = Math.max(1L, (long) Math.ceil(total / (double) resultCount));
        return new RecipeCandidate(unitPrice, explainRecipe(recipeId, ingredientSummaries, ingredientTotal,
                flatProcessingFee, markup, resultCount));
    }

    private static RecipeCandidate candidateFromCookingRecipe(RecipeContext context, ResourceLocation recipeId, String recipeKind,
            AbstractCookingRecipe recipe, int resultCount, Set<ResourceLocation> visiting, int depth) {
        if (resultCount <= 0) {
            return null;
        }

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty() || ingredients.getFirst().isEmpty()) {
            return null;
        }

        IngredientChoice choice = cheapestIngredient(context, ingredients.getFirst(), visiting, depth);
        if (choice == null) {
            return null;
        }

        long cookingFee = Math.max(0L, PricingConfig.derivedCookingFee());
        long timeFee = Math.max(0L, Math.round(recipe.getCookingTime() / 200.0D));
        double markup = Math.max(0.0D, PricingConfig.derivedProcessingMarkup());
        long total = Math.max(1L, (long) Math.ceil((choice.price() + cookingFee + timeFee) * (1.0D + markup)));
        long unitPrice = Math.max(1L, (long) Math.ceil(total / (double) resultCount));
        return new RecipeCandidate(unitPrice, explainCookingRecipe(recipeId, recipeKind, choice, cookingFee, timeFee, markup,
                resultCount));
    }

    private static RecipeCandidate cheapest(RecipeCandidate candidate, RecipeCandidate best) {
        if (candidate == null) {
            return best;
        }
        if (best == null || candidate.price() < best.price()) {
            return candidate;
        }
        return best;
    }

    private static IngredientChoice cheapestIngredient(RecipeContext context, Ingredient ingredient,
            Set<ResourceLocation> visiting, int depth) {
        IngredientChoice best = null;
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            PriceProfile profile = baseOrDerived(context, itemId, visiting, depth);
            if (profile.referencePrice() <= 0L) {
                continue;
            }
            long price = profile.referencePrice();
            if (best == null || price < best.price()) {
                best = new IngredientChoice(itemId, price);
            }
        }
        return best;
    }

    private static PriceProfile baseOrDerived(RecipeContext context, ResourceLocation itemId, Set<ResourceLocation> visiting, int depth) {
        PriceProfile anchor = PriceRegistry.get(itemId);
        if (anchor.source() != PriceSource.UNKNOWN && anchor.referencePrice() > 0L) {
            return anchor;
        }
        PriceProfile derived = resolveCached(context, itemId, visiting, depth);
        if (derived.source() != PriceSource.UNKNOWN && derived.referencePrice() > 0L) {
            return derived;
        }
        return NaturalPriceSource.resolve(itemId);
    }

    private static PriceProfile toProfile(ResourceLocation itemId, RecipeCandidate candidate) {
        return new PriceProfile(
                itemId,
                PriceSource.DERIVED,
                PriceConfidence.MEDIUM,
                TradeLevel.SYSTEM_BUY_AND_SELL,
                candidate.price(),
                candidate.price(),
                0L,
                candidate.price(),
                0L,
                0L,
                candidate.explanation());
    }

    private static String explainRecipe(ResourceLocation recipeId, Map<ResourceLocation, IngredientSummary> ingredientSummaries,
            long ingredientTotal, long flatProcessingFee, double markup, int resultCount) {
        StringBuilder explanation = new StringBuilder("由合成配方 ").append(recipeId).append(" 推导: ");
        boolean first = true;
        for (Map.Entry<ResourceLocation, IngredientSummary> entry : ingredientSummaries.entrySet()) {
            if (!first) {
                explanation.append(", ");
            }
            IngredientSummary summary = entry.getValue();
            explanation.append(entry.getKey()).append(" x").append(summary.count()).append("=").append(summary.totalPrice());
            first = false;
        }
        explanation.append("; 材料=").append(ingredientTotal);
        if (flatProcessingFee > 0L) {
            explanation.append(", 加工=").append(flatProcessingFee);
        }
        if (markup > 0.0D) {
            explanation.append(", 加成=").append(Math.round(markup * 100.0D)).append("%");
        }
        return explanation.append(" -> /").append(resultCount).toString();
    }

    private static String explainCookingRecipe(ResourceLocation recipeId, String recipeKind, IngredientChoice choice,
            long cookingFee, long timeFee, double markup, int resultCount) {
        StringBuilder explanation = new StringBuilder("由")
                .append(recipeKind)
                .append("配方 ")
                .append(recipeId)
                .append(" 推导: ")
                .append(choice.itemId())
                .append(" x1=")
                .append(choice.price())
                .append("; 材料=")
                .append(choice.price());
        if (cookingFee > 0L) {
            explanation.append(", 烧制=").append(cookingFee);
        }
        if (timeFee > 0L) {
            explanation.append(", 时间=").append(timeFee);
        }
        if (markup > 0.0D) {
            explanation.append(", 加成=").append(Math.round(markup * 100.0D)).append("%");
        }
        return explanation.append(" -> /").append(resultCount).toString();
    }

    private record IngredientChoice(ResourceLocation itemId, long price) {
    }

    private record IngredientSummary(int count, long totalPrice) {
    }

    private record RecipeCandidate(long price, String explanation) {
    }

    private static <T extends AbstractCookingRecipe> void addCookingRecipes(Map<ResourceLocation, List<IndexedCookingRecipe>> index,
            RecipeManager recipeManager, HolderLookup.Provider registries, RecipeType<T> recipeType, String kind) {
        for (RecipeHolder<T> holder : recipeManager.getAllRecipesFor(recipeType)) {
            T recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty() || result.getCount() <= 0) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            index.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                    .add(new IndexedCookingRecipe(holder.id(), kind, recipe, result.getCount()));
        }
    }

    private record IndexedCraftingRecipe(ResourceLocation id, CraftingRecipe recipe, int resultCount) {
    }

    private record IndexedCookingRecipe(ResourceLocation id, String kind, AbstractCookingRecipe recipe, int resultCount) {
    }

    private record RecipeIndex(
            Map<ResourceLocation, List<IndexedCraftingRecipe>> crafting,
            Map<ResourceLocation, List<IndexedCookingRecipe>> cooking) {
        static RecipeIndex build(RecipeManager recipeManager, HolderLookup.Provider registries) {
            Map<ResourceLocation, List<IndexedCraftingRecipe>> crafting = new HashMap<>();
            for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(registries);
                if (result.isEmpty() || result.getCount() <= 0) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                crafting.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                        .add(new IndexedCraftingRecipe(holder.id(), recipe, result.getCount()));
            }

            Map<ResourceLocation, List<IndexedCookingRecipe>> cooking = new HashMap<>();
            addCookingRecipes(cooking, recipeManager, registries, RecipeType.SMELTING, "熔炼");
            addCookingRecipes(cooking, recipeManager, registries, RecipeType.BLASTING, "高炉");
            addCookingRecipes(cooking, recipeManager, registries, RecipeType.SMOKING, "烟熏");
            addCookingRecipes(cooking, recipeManager, registries, RecipeType.CAMPFIRE_COOKING, "营火");
            return new RecipeIndex(crafting, cooking);
        }
    }

    private record RecipeContext(HolderLookup.Provider registries, RecipeIndex index) {
    }

    public record WarmupResult(int candidateCount, int resolvedCount, int cachedCount) {
    }

    public record CoverageReport(
            int candidateCount,
            int resolvedCount,
            int unresolvedCount,
            int cachedCount,
            Map<PriceSource, Integer> sourceCounts,
            List<ResourceLocation> unresolvedSamples) {
    }
}
