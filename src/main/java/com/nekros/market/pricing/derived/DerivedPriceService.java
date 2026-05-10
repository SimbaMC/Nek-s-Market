package com.nekros.market.pricing.derived;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

public final class DerivedPriceService {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_GENERIC_RESULT_COUNT = 64;
    private static final GenericRecipeTypePolicy DEFAULT_GENERIC_POLICY = new GenericRecipeTypePolicy(
            TradeLevel.REFERENCE_ONLY,
            PriceConfidence.LOW,
            MAX_GENERIC_RESULT_COUNT,
            1L,
            0.10D,
            "generic safe fallback");
    private static final Map<ResourceLocation, GenericRecipeTypePolicy> GENERIC_RECIPE_POLICIES = new HashMap<>();
    private static GenericRecipeTypePolicy genericFallbackPolicy = DEFAULT_GENERIC_POLICY;
    private static boolean genericPoliciesLoaded;
    private static final Map<ResourceLocation, PriceProfile> CACHE = new HashMap<>();
    private static final Map<RecipeManager, RecipeIndex> INDEX_CACHE = new IdentityHashMap<>();

    private DerivedPriceService() {
    }

    public static void clearCache() {
        CACHE.clear();
        INDEX_CACHE.clear();
        GENERIC_RECIPE_POLICIES.clear();
        genericFallbackPolicy = DEFAULT_GENERIC_POLICY;
        genericPoliciesLoaded = false;
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

    public static Set<ResourceLocation> candidateIds(MinecraftServer server) {
        if (server == null) {
            return Set.of();
        }
        return candidateIds(context(server.getRecipeManager(), server.registryAccess()));
    }

    public static PriceProfile resolveForWarmup(MinecraftServer server, ResourceLocation itemId) {
        if (server == null) {
            return PriceProfile.unknown(itemId);
        }
        return baselineForCoverage(context(server.getRecipeManager(), server.registryAccess()), itemId);
    }

    public static int cachedCount() {
        return CACHE.size();
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
            return new CoverageReport(0, 0, 0, 0, Map.of(), Map.of(), List.of());
        }

        RecipeContext context = context(server.getRecipeManager(), server.registryAccess());
        Set<ResourceLocation> candidates = candidateIds(context);
        Map<PriceSource, Integer> sourceCounts = new EnumMap<>(PriceSource.class);
        Map<TradeLevel, Integer> tradeLevelCounts = new EnumMap<>(TradeLevel.class);
        List<ResourceLocation> unresolved = new ArrayList<>();
        int resolved = 0;

        for (ResourceLocation itemId : candidates) {
            PriceProfile profile = baselineForCoverage(context, itemId);
            if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                resolved++;
                sourceCounts.merge(profile.source(), 1, Integer::sum);
                tradeLevelCounts.merge(profile.tradeLevel(), 1, Integer::sum);
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
                Map.copyOf(tradeLevelCounts),
                List.copyOf(unresolved));
    }

    public static CoverageReport fastCoverage(MinecraftServer server, int unresolvedLimit) {
        if (server == null) {
            return new CoverageReport(0, 0, 0, 0, Map.of(), Map.of(), List.of());
        }

        RecipeContext context = context(server.getRecipeManager(), server.registryAccess());
        Set<ResourceLocation> candidates = candidateIds(context);
        Map<PriceSource, Integer> sourceCounts = new EnumMap<>(PriceSource.class);
        Map<TradeLevel, Integer> tradeLevelCounts = new EnumMap<>(TradeLevel.class);
        List<ResourceLocation> unresolved = new ArrayList<>();
        int resolved = 0;

        for (ResourceLocation itemId : candidates) {
            PriceProfile profile = directBaseline(itemId);
            if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                resolved++;
                sourceCounts.merge(profile.source(), 1, Integer::sum);
                tradeLevelCounts.merge(profile.tradeLevel(), 1, Integer::sum);
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
                Map.copyOf(tradeLevelCounts),
                List.copyOf(unresolved));
    }

    public static List<RecipeTypeReport> recipeTypeReport(MinecraftServer server, int limit) {
        if (server == null || limit <= 0) {
            return List.of();
        }

        Map<RecipeType<?>, MutableRecipeTypeReport> reports = new IdentityHashMap<>();
        HolderLookup.Provider registries = server.registryAccess();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            RecipeType<?> recipeType = recipe.getType();
            MutableRecipeTypeReport report = reports.computeIfAbsent(recipeType, ignored -> new MutableRecipeTypeReport());
            report.recipeCount++;

            ItemStack result = recipe.getResultItem(registries);
            if (safePlainResult(result) && !recipe.getIngredients().isEmpty() && !recipe.isSpecial()) {
                report.indexableCount++;
            }
        }

        return reports.entrySet().stream()
                .map(entry -> toRecipeTypeReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparingInt(RecipeTypeReport::recipeCount)
                        .reversed()
                        .thenComparing(report -> report.recipeType().toString()))
                .limit(limit)
                .toList();
    }

    private static RecipeTypeReport toRecipeTypeReport(RecipeType<?> recipeType, MutableRecipeTypeReport mutable) {
        ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
        if (recipeTypeId == null) {
            recipeTypeId = ResourceLocation.fromNamespaceAndPath("unknown", "unregistered");
        }
        boolean handled = isHandledRecipeType(recipeType);
        GenericRecipeTypePolicy policy = genericPolicy(recipeType);
        return new RecipeTypeReport(
                recipeTypeId,
                mutable.recipeCount,
                mutable.indexableCount,
                handled,
                policy.tradeLevel(),
                policy.confidence(),
                policy.maxResultCount(),
                policy.processingFee(),
                policy.markup(),
                policy.note());
    }

    private static Set<ResourceLocation> candidateIds(RecipeContext context) {
        Set<ResourceLocation> candidates = new LinkedHashSet<>();
        candidates.addAll(PriceRegistry.anchorIds());
        candidates.addAll(EquivalentItemPriceSource.itemIds());
        candidates.addAll(NaturalPriceSource.configuredItemIds());
        candidates.addAll(NaturalPriceSource.inferredItemIds());
        candidates.addAll(context.index().crafting().keySet());
        candidates.addAll(context.index().cooking().keySet());
        candidates.addAll(context.index().stonecutting().keySet());
        candidates.addAll(context.index().smithing().keySet());
        candidates.addAll(context.index().generic().keySet());
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
        best = cheapest(bestStonecuttingCandidate(context, itemId, visiting, depth + 1), best);
        best = cheapest(bestSmithingCandidate(context, itemId, visiting, depth + 1), best);
        best = cheapest(bestGenericCandidate(context, itemId, visiting, depth + 1), best);

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

    private static RecipeCandidate bestStonecuttingCandidate(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        RecipeCandidate best = null;
        for (IndexedStonecuttingRecipe recipe : context.index().stonecutting().getOrDefault(itemId, List.of())) {
            best = cheapest(candidateFromStonecuttingRecipe(context, recipe.id(), recipe.recipe(), recipe.resultCount(),
                    visiting, depth), best);
        }
        return best;
    }

    private static RecipeCandidate bestSmithingCandidate(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        RecipeCandidate best = null;
        for (IndexedSmithingRecipe recipe : context.index().smithing().getOrDefault(itemId, List.of())) {
            best = cheapest(candidateFromSmithingRecipe(context, recipe.id(), recipe.recipe(), recipe.resultCount(),
                    visiting, depth), best);
        }
        return best;
    }

    private static RecipeCandidate bestGenericCandidate(RecipeContext context, ResourceLocation itemId,
            Set<ResourceLocation> visiting, int depth) {
        RecipeCandidate best = null;
        for (IndexedGenericRecipe recipe : context.index().generic().getOrDefault(itemId, List.of())) {
            best = cheapest(candidateFromGenericRecipe(context, recipe.id(), recipe.recipe(), recipe.resultCount(),
                    visiting, depth), best);
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
                    ? new IngredientSummary(1, choice.price(), choice.confidence(), choice.tradeLevel())
                    : new IngredientSummary(
                            existing.count() + 1,
                            existing.totalPrice() + choice.price(),
                            lowerConfidence(existing.confidence(), choice.confidence()),
                            lowerTradeLevel(existing.tradeLevel(), choice.tradeLevel())));
        }

        if (ingredientTotal <= 0L) {
            return null;
        }
        long flatProcessingFee = Math.max(0L, PricingConfig.derivedProcessingFeePerIngredient()) * ingredientSlots;
        double markup = Math.max(0.0D, PricingConfig.derivedProcessingMarkup());
        long total = Math.max(1L, (long) Math.ceil((ingredientTotal + flatProcessingFee) * (1.0D + markup)));
        long unitPrice = Math.max(1L, (long) Math.ceil(total / (double) resultCount));
        PriceConfidence confidence = confidenceFromIngredients(ingredientSummaries.values());
        TradeLevel tradeLevel = tradeLevelFromIngredients(ingredientSummaries.values());
        return new RecipeCandidate(unitPrice, explainRecipe(recipeId, ingredientSummaries, ingredientTotal,
                flatProcessingFee, markup, resultCount, confidence), confidence, tradeLevel);
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
                resultCount), choice.confidence(), choice.tradeLevel());
    }

    private static RecipeCandidate candidateFromStonecuttingRecipe(RecipeContext context, ResourceLocation recipeId,
            StonecutterRecipe recipe, int resultCount, Set<ResourceLocation> visiting, int depth) {
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

        long unitPrice = Math.max(1L, (long) Math.ceil(choice.price() / (double) resultCount));
        return new RecipeCandidate(unitPrice, explainSingleInputRecipe(recipeId, "石切", choice, resultCount),
                choice.confidence(), choice.tradeLevel());
    }

    private static RecipeCandidate candidateFromSmithingRecipe(RecipeContext context, ResourceLocation recipeId,
            SmithingRecipe recipe, int resultCount, Set<ResourceLocation> visiting, int depth) {
        if (resultCount <= 0) {
            return null;
        }

        IngredientChoice template = cheapestSmithingIngredient(context, recipe::isTemplateIngredient, visiting, depth);
        IngredientChoice base = cheapestSmithingIngredient(context, recipe::isBaseIngredient, visiting, depth);
        IngredientChoice addition = cheapestSmithingIngredient(context, recipe::isAdditionIngredient, visiting, depth);
        if (template == null || base == null || addition == null) {
            return null;
        }

        long total = template.price() + base.price() + addition.price();
        PriceConfidence confidence = lowerConfidence(template.confidence(), lowerConfidence(base.confidence(), addition.confidence()));
        TradeLevel tradeLevel = lowerTradeLevel(template.tradeLevel(), lowerTradeLevel(base.tradeLevel(), addition.tradeLevel()));
        long unitPrice = Math.max(1L, (long) Math.ceil(total / (double) resultCount));
        return new RecipeCandidate(unitPrice, explainSmithingRecipe(recipeId, template, base, addition, resultCount),
                confidence, tradeLevel);
    }

    private static RecipeCandidate candidateFromGenericRecipe(RecipeContext context, ResourceLocation recipeId,
            Recipe<?> recipe, int resultCount, Set<ResourceLocation> visiting, int depth) {
        GenericRecipeTypePolicy policy = genericPolicy(recipe.getType());
        if (resultCount <= 0
                || resultCount > policy.maxResultCount()
                || recipe.isSpecial()
                || policy.tradeLevel() == TradeLevel.BLOCKED) {
            return null;
        }

        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return null;
        }

        long ingredientTotal = 0L;
        int ingredientSlots = 0;
        Map<ResourceLocation, IngredientSummary> ingredientSummaries = new LinkedHashMap<>();
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
                    ? new IngredientSummary(1, choice.price(), choice.confidence(), choice.tradeLevel())
                    : new IngredientSummary(
                            existing.count() + 1,
                            existing.totalPrice() + choice.price(),
                            lowerConfidence(existing.confidence(), choice.confidence()),
                            lowerTradeLevel(existing.tradeLevel(), choice.tradeLevel())));
        }
        if (ingredientTotal <= 0L || ingredientSlots <= 0) {
            return null;
        }

        long flatProcessingFee = Math.max(0L, policy.processingFee()) * ingredientSlots;
        double markup = Math.max(0.0D, policy.markup());
        long total = Math.max(1L, (long) Math.ceil((ingredientTotal + flatProcessingFee) * (1.0D + markup)));
        long unitPrice = Math.max(1L, (long) Math.ceil(total / (double) resultCount));
        PriceConfidence confidence = lowerConfidence(policy.confidence(), confidenceFromIngredients(ingredientSummaries.values()));
        TradeLevel tradeLevel = lowerTradeLevel(policy.tradeLevel(), tradeLevelFromIngredients(ingredientSummaries.values()));
        return new RecipeCandidate(unitPrice, explainRecipe(recipeId, ingredientSummaries, ingredientTotal,
                flatProcessingFee, markup, resultCount, confidence), confidence, tradeLevel);
    }

    private static RecipeCandidate cheapest(RecipeCandidate candidate, RecipeCandidate best) {
        if (candidate == null) {
            return best;
        }
        if (best == null || candidateScore(candidate) < candidateScore(best)) {
            return candidate;
        }
        return best;
    }

    private static double candidateScore(RecipeCandidate candidate) {
        return candidate.price() * confidencePenalty(candidate.confidence()) * tradeLevelPenalty(candidate.tradeLevel());
    }

    private static IngredientChoice cheapestIngredient(RecipeContext context, Ingredient ingredient,
            Set<ResourceLocation> visiting, int depth) {
        IngredientChoice best = null;
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty() || !stack.isComponentsPatchEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            PriceProfile profile = baseOrDerived(context, itemId, visiting, depth);
            if (profile.referencePrice() <= 0L) {
                continue;
            }
            long price = profile.referencePrice();
            IngredientChoice choice = new IngredientChoice(itemId, price, profile.confidence(), profile.tradeLevel());
            if (best == null || ingredientScore(choice) < ingredientScore(best)) {
                best = choice;
            }
        }
        return best;
    }

    private static IngredientChoice cheapestSmithingIngredient(RecipeContext context, Predicate<ItemStack> predicate,
            Set<ResourceLocation> visiting, int depth) {
        IngredientChoice best = null;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!predicate.test(stack)) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            PriceProfile profile = baseOrDerived(context, itemId, visiting, depth);
            if (profile.referencePrice() <= 0L) {
                continue;
            }
            IngredientChoice choice = new IngredientChoice(itemId, profile.referencePrice(), profile.confidence(), profile.tradeLevel());
            if (best == null || ingredientScore(choice) < ingredientScore(best)) {
                best = choice;
            }
        }
        return best;
    }

    private static double ingredientScore(IngredientChoice choice) {
        return choice.price() * confidencePenalty(choice.confidence()) * tradeLevelPenalty(choice.tradeLevel());
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
                candidate.confidence(),
                candidate.tradeLevel(),
                candidate.price(),
                candidate.price(),
                0L,
                candidate.price(),
                0L,
                0L,
                candidate.explanation());
    }

    private static String explainRecipe(ResourceLocation recipeId, Map<ResourceLocation, IngredientSummary> ingredientSummaries,
            long ingredientTotal, long flatProcessingFee, double markup, int resultCount, PriceConfidence confidence) {
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
        explanation.append(", 可信度=").append(confidence);
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

    private static String explainSingleInputRecipe(ResourceLocation recipeId, String recipeKind, IngredientChoice choice,
            int resultCount) {
        return "由" + recipeKind + "配方 " + recipeId + " 推导: "
                + choice.itemId() + " x1=" + choice.price()
                + " -> /" + resultCount;
    }

    private static String explainSmithingRecipe(ResourceLocation recipeId, IngredientChoice template,
            IngredientChoice base, IngredientChoice addition, int resultCount) {
        return "由锻造配方 " + recipeId + " 推导: "
                + "template " + template.itemId() + "=" + template.price()
                + ", base " + base.itemId() + "=" + base.price()
                + ", addition " + addition.itemId() + "=" + addition.price()
                + " -> /" + resultCount;
    }

    private static PriceConfidence confidenceFromIngredients(Iterable<IngredientSummary> ingredientSummaries) {
        PriceConfidence confidence = PriceConfidence.HIGH;
        boolean hasIngredient = false;
        for (IngredientSummary summary : ingredientSummaries) {
            confidence = lowerConfidence(confidence, summary.confidence());
            hasIngredient = true;
        }
        return hasIngredient ? confidence : PriceConfidence.NONE;
    }

    private static PriceConfidence lowerConfidence(PriceConfidence left, PriceConfidence right) {
        return confidenceRank(left) <= confidenceRank(right) ? left : right;
    }

    private static TradeLevel tradeLevelFromIngredients(Iterable<IngredientSummary> ingredientSummaries) {
        TradeLevel tradeLevel = TradeLevel.SYSTEM_BUY_AND_SELL;
        boolean hasIngredient = false;
        for (IngredientSummary summary : ingredientSummaries) {
            tradeLevel = lowerTradeLevel(tradeLevel, summary.tradeLevel());
            hasIngredient = true;
        }
        return hasIngredient ? tradeLevel : TradeLevel.PLAYER_MARKET_ONLY;
    }

    private static TradeLevel lowerTradeLevel(TradeLevel left, TradeLevel right) {
        return tradeLevelRank(left) <= tradeLevelRank(right) ? left : right;
    }

    private static int confidenceRank(PriceConfidence confidence) {
        return switch (confidence) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case NONE -> 0;
        };
    }

    private static int tradeLevelRank(TradeLevel tradeLevel) {
        return switch (tradeLevel) {
            case BLOCKED -> 0;
            case PLAYER_MARKET_ONLY -> 1;
            case REFERENCE_ONLY -> 2;
            case SYSTEM_BUY_ONLY -> 3;
            case SYSTEM_BUY_AND_SELL -> 4;
        };
    }

    private static double confidencePenalty(PriceConfidence confidence) {
        return switch (confidence) {
            case HIGH -> 1.0D;
            case MEDIUM -> 1.08D;
            case LOW -> 1.25D;
            case NONE -> 2.0D;
        };
    }

    private static double tradeLevelPenalty(TradeLevel tradeLevel) {
        return switch (tradeLevel) {
            case SYSTEM_BUY_AND_SELL, SYSTEM_BUY_ONLY -> 1.0D;
            case REFERENCE_ONLY -> 1.20D;
            case PLAYER_MARKET_ONLY -> 1.50D;
            case BLOCKED -> 3.0D;
        };
    }

    private static final class MutableRecipeTypeReport {
        private int recipeCount;
        private int indexableCount;
    }

    private record IngredientChoice(ResourceLocation itemId, long price, PriceConfidence confidence, TradeLevel tradeLevel) {
    }

    private record IngredientSummary(int count, long totalPrice, PriceConfidence confidence, TradeLevel tradeLevel) {
    }

    private record RecipeCandidate(long price, String explanation, PriceConfidence confidence, TradeLevel tradeLevel) {
        RecipeCandidate(long price, String explanation) {
            this(price, explanation, PriceConfidence.MEDIUM);
        }

        RecipeCandidate(long price, String explanation, PriceConfidence confidence) {
            this(price, explanation, confidence, TradeLevel.SYSTEM_BUY_AND_SELL);
        }
    }

    private record GenericRecipeTypePolicy(
            TradeLevel tradeLevel,
            PriceConfidence confidence,
            int maxResultCount,
            long processingFee,
            double markup,
            String note) {
    }

    private static boolean safePlainResult(ItemStack result) {
        return !result.isEmpty() && result.getCount() > 0 && result.isComponentsPatchEmpty();
    }

    private static <T extends AbstractCookingRecipe> void addCookingRecipes(Map<ResourceLocation, List<IndexedCookingRecipe>> index,
            RecipeManager recipeManager, HolderLookup.Provider registries, RecipeType<T> recipeType, String kind) {
        for (RecipeHolder<T> holder : recipeManager.getAllRecipesFor(recipeType)) {
            T recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (!safePlainResult(result)) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            index.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                    .add(new IndexedCookingRecipe(holder.id(), kind, recipe, result.getCount()));
        }
    }

    private static boolean isHandledRecipeType(RecipeType<?> recipeType) {
        return recipeType == RecipeType.CRAFTING
                || recipeType == RecipeType.SMELTING
                || recipeType == RecipeType.BLASTING
                || recipeType == RecipeType.SMOKING
                || recipeType == RecipeType.CAMPFIRE_COOKING
                || recipeType == RecipeType.STONECUTTING
                || recipeType == RecipeType.SMITHING;
    }

    private static GenericRecipeTypePolicy genericPolicy(RecipeType<?> recipeType) {
        ensureGenericPoliciesLoaded();
        ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipeType);
        if (recipeTypeId == null) {
            return genericFallbackPolicy;
        }
        return GENERIC_RECIPE_POLICIES.getOrDefault(recipeTypeId, genericFallbackPolicy);
    }

    private static void ensureGenericPoliciesLoaded() {
        if (genericPoliciesLoaded) {
            return;
        }

        GENERIC_RECIPE_POLICIES.clear();
        genericFallbackPolicy = DEFAULT_GENERIC_POLICY;
        for (String line : PricingConfig.genericRecipeTypePolicies().stream().map(String::valueOf).toList()) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 6 || parts.length > 7) {
                continue;
            }
            GenericRecipeTypePolicy policy = parseGenericPolicy(parts, line);
            if (policy == null) {
                continue;
            }
            String typeText = parts[0].trim();
            if ("*".equals(typeText)) {
                genericFallbackPolicy = policy;
                continue;
            }
            ResourceLocation recipeTypeId = ResourceLocation.tryParse(typeText);
            if (recipeTypeId != null) {
                GENERIC_RECIPE_POLICIES.put(recipeTypeId, policy);
            }
        }
        genericPoliciesLoaded = true;
    }

    private static GenericRecipeTypePolicy parseGenericPolicy(String[] parts, String line) {
        try {
            TradeLevel tradeLevel = TradeLevel.valueOf(parts[1].trim().toUpperCase(java.util.Locale.ROOT));
            PriceConfidence confidence = PriceConfidence.valueOf(parts[2].trim().toUpperCase(java.util.Locale.ROOT));
            int maxResultCount = Math.max(1, Integer.parseInt(parts[3].trim()));
            long processingFee = Math.max(0L, Long.parseLong(parts[4].trim()));
            double markup = Math.max(0.0D, Double.parseDouble(parts[5].trim()));
            String note = parts.length >= 7 ? parts[6].trim() : "";
            return new GenericRecipeTypePolicy(tradeLevel, confidence, maxResultCount, processingFee, markup, note);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record IndexedCraftingRecipe(ResourceLocation id, CraftingRecipe recipe, int resultCount) {
    }

    private record IndexedCookingRecipe(ResourceLocation id, String kind, AbstractCookingRecipe recipe, int resultCount) {
    }

    private record IndexedStonecuttingRecipe(ResourceLocation id, StonecutterRecipe recipe, int resultCount) {
    }

    private record IndexedSmithingRecipe(ResourceLocation id, SmithingRecipe recipe, int resultCount) {
    }

    private record IndexedGenericRecipe(ResourceLocation id, Recipe<?> recipe, int resultCount) {
    }

    private record RecipeIndex(
            Map<ResourceLocation, List<IndexedCraftingRecipe>> crafting,
            Map<ResourceLocation, List<IndexedCookingRecipe>> cooking,
            Map<ResourceLocation, List<IndexedStonecuttingRecipe>> stonecutting,
            Map<ResourceLocation, List<IndexedSmithingRecipe>> smithing,
            Map<ResourceLocation, List<IndexedGenericRecipe>> generic) {
        static RecipeIndex build(RecipeManager recipeManager, HolderLookup.Provider registries) {
            Map<ResourceLocation, List<IndexedCraftingRecipe>> crafting = new HashMap<>();
            for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(registries);
                if (!safePlainResult(result)) {
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
            Map<ResourceLocation, List<IndexedStonecuttingRecipe>> stonecutting = new HashMap<>();
            for (RecipeHolder<StonecutterRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.STONECUTTING)) {
                StonecutterRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(registries);
                if (!safePlainResult(result)) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                stonecutting.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                        .add(new IndexedStonecuttingRecipe(holder.id(), recipe, result.getCount()));
            }
            Map<ResourceLocation, List<IndexedSmithingRecipe>> smithing = new HashMap<>();
            for (RecipeHolder<SmithingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.SMITHING)) {
                SmithingRecipe recipe = holder.value();
                ItemStack result = recipe.getResultItem(registries);
                if (!safePlainResult(result)) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                smithing.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                        .add(new IndexedSmithingRecipe(holder.id(), recipe, result.getCount()));
            }
            Map<ResourceLocation, List<IndexedGenericRecipe>> generic = new HashMap<>();
            for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
                Recipe<?> recipe = holder.value();
                if (isHandledRecipeType(recipe.getType())) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(registries);
                if (!safePlainResult(result) || recipe.getIngredients().isEmpty()) {
                    continue;
                }
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                generic.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                        .add(new IndexedGenericRecipe(holder.id(), recipe, result.getCount()));
            }
            return new RecipeIndex(crafting, cooking, stonecutting, smithing, generic);
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
            Map<TradeLevel, Integer> tradeLevelCounts,
            List<ResourceLocation> unresolvedSamples) {
    }

    public record RecipeTypeReport(
            ResourceLocation recipeType,
            int recipeCount,
            int indexableCount,
            boolean handledByBuiltinIndexer,
            TradeLevel genericTradeLevel,
            PriceConfidence genericConfidence,
            int genericMaxResultCount,
            long genericProcessingFee,
            double genericMarkup,
            String genericNote) {
    }
}
