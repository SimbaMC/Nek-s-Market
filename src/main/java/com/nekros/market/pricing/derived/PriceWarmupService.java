package com.nekros.market.pricing.derived;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.nekros.market.pricing.PriceProfile;
import com.nekros.market.pricing.PriceSource;
import com.nekros.market.pricing.TradeLevel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class PriceWarmupService {
    private static final int DEFAULT_ITEMS_PER_TICK = 8;
    private static final int MAX_ITEMS_PER_TICK = 64;
    private static final int MAX_UNRESOLVED_SAMPLES = 20;

    private static WarmupTask activeTask;
    private static WarmupSnapshot lastSnapshot = WarmupSnapshot.idle();

    private PriceWarmupService() {
    }

    public static WarmupSnapshot start(MinecraftServer server) {
        return start(server, DEFAULT_ITEMS_PER_TICK);
    }

    public static WarmupSnapshot start(MinecraftServer server, int itemsPerTick) {
        if (server == null) {
            lastSnapshot = WarmupSnapshot.idle();
            return lastSnapshot;
        }
        int budget = Math.max(1, Math.min(MAX_ITEMS_PER_TICK, itemsPerTick));
        List<ResourceLocation> candidates = DerivedPriceService.candidateIds(server).stream().toList();
        activeTask = new WarmupTask(new ArrayDeque<>(candidates), candidates.size(), budget, server.getTickCount());
        lastSnapshot = activeTask.snapshot(server.getTickCount(), "运行中");
        return lastSnapshot;
    }

    public static WarmupSnapshot cancel(MinecraftServer server) {
        if (activeTask == null) {
            return lastSnapshot;
        }
        lastSnapshot = activeTask.snapshot(server == null ? activeTask.startedTick() : server.getTickCount(), "已取消");
        activeTask = null;
        return lastSnapshot;
    }

    public static WarmupSnapshot status(MinecraftServer server) {
        if (activeTask == null) {
            return lastSnapshot;
        }
        return activeTask.snapshot(server == null ? activeTask.startedTick() : server.getTickCount(), "运行中");
    }

    public static void tick(MinecraftServer server) {
        if (server == null || activeTask == null) {
            return;
        }

        activeTask.tick(server);
        lastSnapshot = activeTask.snapshot(server.getTickCount(), activeTask.done() ? "已完成" : "运行中");
        if (activeTask.done()) {
            activeTask = null;
        }
    }

    public static boolean running() {
        return activeTask != null;
    }

    private static final class WarmupTask {
        private final Queue<ResourceLocation> remaining;
        private final int total;
        private final int itemsPerTick;
        private final int startedTick;
        private final Map<PriceSource, Integer> sourceCounts = new EnumMap<>(PriceSource.class);
        private final Map<TradeLevel, Integer> tradeLevelCounts = new EnumMap<>(TradeLevel.class);
        private final List<ResourceLocation> unresolvedSamples = new ArrayList<>();
        private int processed;
        private int resolved;
        private int unresolved;

        private WarmupTask(Queue<ResourceLocation> remaining, int total, int itemsPerTick, int startedTick) {
            this.remaining = remaining;
            this.total = total;
            this.itemsPerTick = itemsPerTick;
            this.startedTick = startedTick;
        }

        private void tick(MinecraftServer server) {
            int budget = itemsPerTick;
            while (budget-- > 0 && !remaining.isEmpty()) {
                ResourceLocation itemId = remaining.poll();
                PriceProfile profile = DerivedPriceService.resolveForWarmup(server, itemId);
                processed++;
                if (profile.source() != PriceSource.UNKNOWN && profile.referencePrice() > 0L) {
                    resolved++;
                    sourceCounts.merge(profile.source(), 1, Integer::sum);
                    tradeLevelCounts.merge(profile.tradeLevel(), 1, Integer::sum);
                } else {
                    unresolved++;
                    if (unresolvedSamples.size() < MAX_UNRESOLVED_SAMPLES) {
                        unresolvedSamples.add(itemId);
                    }
                }
            }
        }

        private boolean done() {
            return remaining.isEmpty();
        }

        private int startedTick() {
            return startedTick;
        }

        private WarmupSnapshot snapshot(int nowTick, String state) {
            return new WarmupSnapshot(
                    state,
                    total,
                    processed,
                    resolved,
                    unresolved,
                    remaining.size(),
                    itemsPerTick,
                    Math.max(0, nowTick - startedTick),
                    Map.copyOf(sourceCounts),
                    Map.copyOf(tradeLevelCounts),
                    List.copyOf(unresolvedSamples),
                    DerivedPriceService.cachedCount());
        }
    }

    public record WarmupSnapshot(
            String state,
            int total,
            int processed,
            int resolved,
            int unresolved,
            int remaining,
            int itemsPerTick,
            int elapsedTicks,
            Map<PriceSource, Integer> sourceCounts,
            Map<TradeLevel, Integer> tradeLevelCounts,
            List<ResourceLocation> unresolvedSamples,
            int cachedCount) {
        private WarmupSnapshot(String state, int total, int processed, int resolved, int unresolved, int remaining,
                int itemsPerTick, int elapsedTicks, Map<PriceSource, Integer> sourceCounts, int cachedCount) {
            this(state, total, processed, resolved, unresolved, remaining, itemsPerTick, elapsedTicks, sourceCounts,
                    Map.of(), List.of(), cachedCount);
        }

        private static WarmupSnapshot idle() {
            return new WarmupSnapshot("未启动", 0, 0, 0, 0, 0, 0, 0, Map.of(), 0);
        }

        public double progressPercent() {
            if (total <= 0) {
                return 0.0D;
            }
            return processed * 100.0D / total;
        }
    }
}
