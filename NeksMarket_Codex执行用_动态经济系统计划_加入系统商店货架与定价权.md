# Nek's Market 动态经济系统 Codex 执行文档

> 用途：把本文件交给 Codex，减少它探索仓库和重复推理的 token 消耗。  
> 原则：不要一次性大改；按阶段执行；先做可运行框架，再逐步扩展。

---

## 1. 项目基本信息

仓库：

```text
SimbaMC/Nek-s-Market
```

技术栈：

```text
Minecraft 1.21.1
NeoForge 21.1.228
Java 21
Gradle / ModDevGradle
```

Mod 信息：

```text
mod_id = neksmarket
主包名 = com.nekros.market
默认分支 = master
```

第一阶段目标：

```text
价格档案系统
基础锚点价格
玩家市场成交记录
成交价指数
系统商店动态报价入口
价格查询命令
独立经济存档
```

第一阶段不要做：

```text
完整配方图
SCC / 环检测
全模组机器配方
世界生成率自动分析
掉落率自动分析
GUI 大改
高级反套利检测
全物品动态池
```

---

## 2. 当前项目结构速查

### 2.1 入口类

```text
src/main/java/com/nekros/market/NeksMarket.java
```

职责：

```text
注册菜单 ModMenus
注册网络 ModNetworking
注册 /market 命令
注册 common config
```

不要重写，只允许追加必要注册。

---

### 2.2 客户端入口

```text
src/main/java/com/nekros/market/NeksMarketClient.java
```

职责：

```text
注册 MarketScreen
```

第一阶段不要改 GUI。

---

### 2.3 配置类

```text
src/main/java/com/nekros/market/Config.java
```

当前已有：

```text
defaultListingDurationHours
listingFeeEnabled
listingFeeFlat
listingFeePercent
systemMarket.buyCategories
systemMarket.offers
```

第一阶段可以向这里追加少量 pricing 配置。

---

### 2.4 玩家自由市场核心

```text
src/main/java/com/nekros/market/listing/MarketService.java
```

当前职责：

```text
sellMainHand
buy
cancel
claim
page
pageCount
ownListings
expireListings
```

第一阶段只改 `buy` 成功后记录成交历史。

不要重写整个类。

---

### 2.5 玩家挂单数据

```text
src/main/java/com/nekros/market/listing/MarketListing.java
```

当前字段：

```text
UUID id
UUID sellerId
String sellerName
ItemStack item
int count
long price
long createdAt
long expiresAt
```

第一阶段不要改这个 record，避免破坏存档兼容。

---

### 2.6 系统商店核心

```text
src/main/java/com/nekros/market/system/SystemMarketService.java
```

当前固定价格问题：

```java
totalPrice = offer.unitPrice() * count;
```

第一阶段改造目标：

```text
交易前调用 SystemPriceService 生成报价
系统出售与系统收购都走服务端报价
保留旧 offer.unitPrice() 作为 fallback
```

---

### 2.7 系统商店商品

```text
src/main/java/com/nekros/market/system/SystemMarketOffer.java
```

当前配置格式：

```text
id|type|item|unitPrice|category
```

type：

```text
sell_to_player
buy_from_player
```

第一阶段不要改旧格式。

---

### 2.8 存档

```text
src/main/java/com/nekros/market/storage/MarketSavedData.java
```

当前保存：

```text
listings
balances
claims
```

第一阶段新增独立存档：

```text
src/main/java/com/nekros/market/storage/EconomySavedData.java
```

不要把成交历史塞进 `MarketSavedData`。

---

### 2.9 货币

```text
src/main/java/com/nekros/market/economy/MarketEconomy.java
```

当前虚拟货币：

```text
村民币
```

第一阶段继续沿用。

---

### 2.10 背包工具

```text
src/main/java/com/nekros/market/util/InventoryUtil.java
```

注意当前匹配逻辑：

```java
public static boolean matchesTemplate(ItemStack stack, ItemStack template) {
    return !stack.isEmpty() && stack.is(template.getItem());
}
```

这只判断物品 ID，不严格判断 DataComponent / NBT。  
第一阶段不要大改，但系统商店必须默认限制复杂物品。

---

## 3. 新增包结构

第一阶段新增：

```text
com.nekros.market.pricing
com.nekros.market.pricing.market
com.nekros.market.pricing.system
com.nekros.market.pricing.config
```

第一阶段暂不新增：

```text
com.nekros.market.pricing.recipe
```

---

## 4. 第一阶段新增文件清单

### 4.1 pricing 基础模型

```text
src/main/java/com/nekros/market/pricing/PriceSource.java
src/main/java/com/nekros/market/pricing/PriceConfidence.java
src/main/java/com/nekros/market/pricing/TradeLevel.java
src/main/java/com/nekros/market/pricing/PriceProfile.java
src/main/java/com/nekros/market/pricing/PriceRegistry.java
src/main/java/com/nekros/market/pricing/PriceResolver.java
```

### 4.2 玩家成交记录

```text
src/main/java/com/nekros/market/pricing/market/MarketTradeRecord.java
src/main/java/com/nekros/market/pricing/market/MarketTradeHistoryService.java
src/main/java/com/nekros/market/pricing/market/MarketPriceIndexService.java
```

### 4.3 系统商店报价

```text
src/main/java/com/nekros/market/pricing/system/SystemTradeQuote.java
src/main/java/com/nekros/market/pricing/system/SystemPriceService.java
```

### 4.4 配置包装

```text
src/main/java/com/nekros/market/pricing/config/PricingConfig.java
```

### 4.5 存档

```text
src/main/java/com/nekros/market/storage/EconomySavedData.java
```

### 4.6 命令

```text
src/main/java/com/nekros/market/command/PriceAdminCommands.java
```

---

## 5. 枚举定义

### 5.1 PriceSource

```java
package com.nekros.market.pricing;

public enum PriceSource {
    ANCHOR,
    SYSTEM_OFFER,
    PLAYER_MARKET,
    MIXED,
    UNKNOWN
}
```

### 5.2 PriceConfidence

```java
package com.nekros.market.pricing;

public enum PriceConfidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE
}
```

### 5.3 TradeLevel

```java
package com.nekros.market.pricing;

public enum TradeLevel {
    BLOCKED,
    PLAYER_MARKET_ONLY,
    REFERENCE_ONLY,
    SYSTEM_BUY_ONLY,
    SYSTEM_BUY_AND_SELL
}
```

---

## 6. PriceProfile

文件：

```text
src/main/java/com/nekros/market/pricing/PriceProfile.java
```

建议结构：

```java
package com.nekros.market.pricing;

import net.minecraft.resources.ResourceLocation;

public record PriceProfile(
        ResourceLocation itemId,
        PriceSource source,
        PriceConfidence confidence,
        TradeLevel tradeLevel,
        long floorPrice,
        long derivedPrice,
        long marketPrice,
        long referencePrice,
        long systemBuyPrice,
        long systemSellPrice,
        String explanation
) {
    public static PriceProfile unknown(ResourceLocation itemId) {
        return new PriceProfile(
                itemId,
                PriceSource.UNKNOWN,
                PriceConfidence.NONE,
                TradeLevel.PLAYER_MARKET_ONLY,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                "No reliable price source."
        );
    }
}
```

---

## 7. Config.java 追加配置

在 `Config.java` 里追加：

```java
public static final ModConfigSpec.ConfigValue<List<? extends String>> PRICE_ANCHORS = BUILDER
        .comment(
                "Pricing anchors.",
                "Format: itemId|price|tradeLevel",
                "Example: minecraft:iron_ingot|100|SYSTEM_BUY_AND_SELL")
        .defineListAllowEmpty("pricing.anchors",
                List.of(
                        "minecraft:oak_log|1|SYSTEM_BUY_AND_SELL",
                        "minecraft:iron_ingot|100|SYSTEM_BUY_AND_SELL",
                        "minecraft:gold_ingot|500|SYSTEM_BUY_AND_SELL",
                        "minecraft:diamond|1000|SYSTEM_BUY_AND_SELL",
                        "minecraft:netherite_ingot|10000|SYSTEM_BUY_AND_SELL"
                ),
                value -> value instanceof String);

public static final ModConfigSpec.DoubleValue PRICING_DEFAULT_BUY_RATIO = BUILDER
        .comment("Default ratio for system buying from players.")
        .defineInRange("pricing.defaultBuyRatio", 0.65D, 0.0D, 10.0D);

public static final ModConfigSpec.DoubleValue PRICING_DEFAULT_SELL_RATIO = BUILDER
        .comment("Default ratio for system selling to players.")
        .defineInRange("pricing.defaultSellRatio", 1.35D, 0.0D, 10.0D);

public static final ModConfigSpec.IntValue PRICING_MARKET_CONFIDENCE_TRADE_COUNT = BUILDER
        .comment("Recent trade count required for full player market confidence.")
        .defineInRange("pricing.marketConfidenceTradeCount", 30, 1, 10000);
```

注意：

```text
必须放在 SPEC = BUILDER.build() 之前。
```

---

## 8. PriceRegistry

文件：

```text
src/main/java/com/nekros/market/pricing/PriceRegistry.java
```

职责：

```text
读取锚点配置
缓存基础价格档案
根据物品 ID 查询价格档案
没有价格则返回 unknown
```

接口：

```java
public final class PriceRegistry {
    public static void reload();
    public static PriceProfile get(ResourceLocation itemId);
    public static PriceProfile get(ItemStack stack);
}
```

实现规则：

```text
1. reload() 读取 Config.PRICE_ANCHORS
2. itemId|price|tradeLevel
3. 生成 anchor PriceProfile
4. get() 查询不到时返回 PriceProfile.unknown(itemId)
```

第一阶段不要在 `PriceRegistry` 里做复杂配方计算。

---

## 9. PriceResolver

文件：

```text
src/main/java/com/nekros/market/pricing/PriceResolver.java
```

职责：

```text
混合锚点价和玩家市场价
生成 referencePrice / systemBuyPrice / systemSellPrice
```

第一阶段规则：

```text
如果有 anchor：
    floorPrice = anchor
    derivedPrice = anchor
如果有 marketPrice：
    referencePrice = anchor * (1 - confidence) + marketPrice * confidence
否则：
    referencePrice = anchor
```

如果没有 anchor 但有 marketPrice：

```text
referencePrice = marketPrice
confidence = LOW 或 MEDIUM
tradeLevel = PLAYER_MARKET_ONLY 或 REFERENCE_ONLY
```

系统价格：

```text
systemBuyPrice = floor(referencePrice * defaultBuyRatio)
systemSellPrice = ceil(referencePrice * defaultSellRatio)
```

规则：

```text
systemBuyPrice 最低为 1
如果 tradeLevel 不允许系统收购，systemBuyPrice = 0
如果 tradeLevel 不允许系统出售，systemSellPrice = 0
```

---

## 10. EconomySavedData

文件：

```text
src/main/java/com/nekros/market/storage/EconomySavedData.java
```

职责：

```text
保存玩家市场成交历史
```

基本结构：

```java
public class EconomySavedData extends SavedData {
    private static final String DATA_NAME = "neksmarket_economy";
    private static final int MAX_TRADES_PER_ITEM = 200;

    private final Map<String, List<MarketTradeRecord>> tradesByItem = new LinkedHashMap<>();

    public static EconomySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addTrade(MarketTradeRecord record) {
        String key = record.itemId().toString();
        List<MarketTradeRecord> records = tradesByItem.computeIfAbsent(key, ignored -> new ArrayList<>());
        records.add(record);
        while (records.size() > MAX_TRADES_PER_ITEM) {
            records.remove(0);
        }
        setDirty();
    }

    public List<MarketTradeRecord> tradesFor(ResourceLocation itemId) {
        return List.copyOf(tradesByItem.getOrDefault(itemId.toString(), List.of()));
    }
}
```

NBT 存储结构建议：

```text
trades: [
  {
    itemId: "minecraft:iron_ingot",
    records: [
      {
        buyerId: uuid,
        sellerId: uuid,
        unitPrice: long,
        count: int,
        totalPrice: long,
        gameTime: long,
        realTime: long
      }
    ]
  }
]
```

---

## 11. MarketTradeRecord

文件：

```text
src/main/java/com/nekros/market/pricing/market/MarketTradeRecord.java
```

建议结构：

```java
package com.nekros.market.pricing.market;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record MarketTradeRecord(
        ResourceLocation itemId,
        UUID buyerId,
        UUID sellerId,
        long unitPrice,
        int count,
        long totalPrice,
        long gameTime,
        long realTime
) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("itemId", itemId.toString());
        tag.putUUID("buyerId", buyerId);
        tag.putUUID("sellerId", sellerId);
        tag.putLong("unitPrice", unitPrice);
        tag.putInt("count", count);
        tag.putLong("totalPrice", totalPrice);
        tag.putLong("gameTime", gameTime);
        tag.putLong("realTime", realTime);
        return tag;
    }

    public static MarketTradeRecord load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("itemId"));
        if (id == null) {
            id = ResourceLocation.fromNamespaceAndPath("minecraft", "air");
        }
        return new MarketTradeRecord(
                id,
                tag.getUUID("buyerId"),
                tag.getUUID("sellerId"),
                tag.getLong("unitPrice"),
                tag.getInt("count"),
                tag.getLong("totalPrice"),
                tag.getLong("gameTime"),
                tag.getLong("realTime")
        );
    }
}
```

注意：

```text
如果当前 mappings 中 ResourceLocation.tryParse 不可用，使用可用 API 替换。
```

---

## 12. MarketTradeHistoryService

文件：

```text
src/main/java/com/nekros/market/pricing/market/MarketTradeHistoryService.java
```

接口：

```java
public final class MarketTradeHistoryService {
    public static void recordPlayerTrade(
            MinecraftServer server,
            ServerPlayer buyer,
            UUID sellerId,
            ItemStack item,
            long unitPrice,
            int count
    )
}
```

逻辑：

```text
1. 如果 item 为空，return
2. 如果 unitPrice <= 0 或 count <= 0，return
3. itemId = BuiltInRegistries.ITEM.getKey(item.getItem())
4. totalPrice = unitPrice * count，使用 Math.multiplyExact
5. 创建 MarketTradeRecord
6. EconomySavedData.get(server).addTrade(record)
```

---

## 13. 修改 MarketService.buy

文件：

```text
src/main/java/com/nekros/market/listing/MarketService.java
```

在 `buy(...)` 方法成功分支中，找到类似代码：

```java
InventoryUtil.addSplit(buyer.getInventory(), listing.item(), boughtCount);
data.claimFor(listing.sellerId()).addMoney(totalPrice);
data.setDirty();
return BuyResult.success(listing.withCount(boughtCount), totalPrice);
```

改成：

```java
InventoryUtil.addSplit(buyer.getInventory(), listing.item(), boughtCount);
data.claimFor(listing.sellerId()).addMoney(totalPrice);
data.setDirty();

MarketTradeHistoryService.recordPlayerTrade(
        buyer.server,
        buyer,
        listing.sellerId(),
        listing.item(),
        listing.price(),
        boughtCount
);

return BuyResult.success(listing.withCount(boughtCount), totalPrice);
```

新增 import：

```java
import com.nekros.market.pricing.market.MarketTradeHistoryService;
```

---

## 14. MarketPriceIndexService

文件：

```text
src/main/java/com/nekros/market/pricing/market/MarketPriceIndexService.java
```

接口：

```java
public final class MarketPriceIndexService {
    public static long recentVWAP(MinecraftServer server, ResourceLocation itemId);
    public static int recentTradeCount(MinecraftServer server, ResourceLocation itemId);
    public static double confidence(MinecraftServer server, ResourceLocation itemId);
}
```

VWAP：

```text
sum(unitPrice * count) / sum(count)
```

规则：

```text
unitPrice <= 0 跳过
count <= 0 跳过
没有成交返回 0
```

confidence：

```text
min(1.0, recentTradeCount / Config.PRICING_MARKET_CONFIDENCE_TRADE_COUNT)
```

---

## 15. SystemTradeQuote

文件：

```text
src/main/java/com/nekros/market/pricing/system/SystemTradeQuote.java
```

内容：

```java
package com.nekros.market.pricing.system;

public record SystemTradeQuote(
        boolean allowed,
        long totalPrice,
        long unitPricePreview,
        String message
) {
    public static SystemTradeQuote fail(String message) {
        return new SystemTradeQuote(false, 0L, 0L, message);
    }

    public static SystemTradeQuote success(long totalPrice, long unitPricePreview) {
        return new SystemTradeQuote(true, totalPrice, unitPricePreview, "");
    }
}
```

---

## 16. SystemPriceService

文件：

```text
src/main/java/com/nekros/market/pricing/system/SystemPriceService.java
```

接口：

```java
public final class SystemPriceService {
    public static SystemTradeQuote quoteSellToPlayer(MinecraftServer server, SystemMarketOffer offer, int count);
    public static SystemTradeQuote quoteBuyFromPlayer(MinecraftServer server, SystemMarketOffer offer, int count);
}
```

命名：

```text
quoteSellToPlayer = 系统卖给玩家，玩家付款
quoteBuyFromPlayer = 系统从玩家收购，玩家收钱
```

第一阶段逻辑：

```text
1. count <= 0，fail
2. itemId = BuiltInRegistries.ITEM.getKey(offer.item().getItem())
3. profile = PriceRegistry.get(itemId)
4. 如果 profile unknown 或 system price 为 0，fallback 到 offer.unitPrice()
5. 根据 tradeLevel 判断是否允许
6. total = unitPrice * count，使用 Math.multiplyExact
```

允许规则：

系统从玩家收购：

```text
SYSTEM_BUY_ONLY
SYSTEM_BUY_AND_SELL
```

系统卖给玩家：

```text
SYSTEM_BUY_AND_SELL
```

fallback 规则：

```text
如果没有动态价格，但 offer.unitPrice > 0，允许旧配置继续工作
```

---

## 17. 修改 SystemMarketService.trade

文件：

```text
src/main/java/com/nekros/market/system/SystemMarketService.java
```

当前开头有固定总价计算。

替换为：

```java
SystemTradeQuote quote;
if (offer.type() == SystemMarketOffer.Type.SYSTEM_SELLS) {
    quote = SystemPriceService.quoteSellToPlayer(player.server, offer, count);
} else {
    quote = SystemPriceService.quoteBuyFromPlayer(player.server, offer, count);
}

if (!quote.allowed()) {
    return Result.fail(quote.message());
}

long totalPrice = quote.totalPrice();
```

新增 import：

```java
import com.nekros.market.pricing.system.SystemPriceService;
import com.nekros.market.pricing.system.SystemTradeQuote;
```

其余背包检查、扣钱、加钱逻辑尽量保持不变。

---

## 18. PriceAdminCommands

文件：

```text
src/main/java/com/nekros/market/command/PriceAdminCommands.java
```

先实现：

```text
/market price hand
/market price reload
```

### 18.1 /market price hand

逻辑：

```text
1. 获取执行命令玩家
2. 获取主手物品
3. 如果为空，提示 Main hand is empty.
4. 查询 PriceRegistry.get(stack)
5. 输出 PriceProfile 各字段
```

输出示例：

```text
Item: minecraft:iron_ingot
Source: ANCHOR
Confidence: HIGH
Trade Level: SYSTEM_BUY_AND_SELL
Floor: 100
Derived: 100
Market: 0
Reference: 100
System Buy: 65
System Sell: 135
Explanation: Anchor price.
```

### 18.2 /market price reload

权限：

```text
requires source.hasPermission(2)
```

逻辑：

```java
PriceRegistry.reload();
```

输出：

```text
Reloaded Nek's Market pricing registry.
```

---

## 19. 修改 MarketCommands.register

文件：

```text
src/main/java/com/nekros/market/command/MarketCommands.java
```

不要重写整个命令树。

在 `/market` literal 下面追加：

```java
.then(PriceAdminCommands.priceCommand())
```

`PriceAdminCommands` 提供：

```java
public static LiteralArgumentBuilder<CommandSourceStack> priceCommand()
```

---

## 20. 性能要求

### 20.1 交易时不要实时扫描配方

错误做法：

```text
每次交易读取全部配方并递归计算
```

正确做法：

```text
服务器启动 / 配置重载时建立价格缓存
交易时只查缓存
```

第一阶段交易复杂度目标：

```text
O(1) 或 O(最近成交记录数量)
```

---

### 20.2 成交记录上限

第一阶段：

```text
每个物品最多 200 条成交记录
```

查询 VWAP：

```text
O(200)
```

可接受。

---

### 20.3 系统商店报价

第一阶段不做复杂滑点积分，只做：

```text
referencePrice
systemBuyPrice
systemSellPrice
```

以后再做：

```text
库存滑点
每日贬值
曲线积分
```

---

## 21. 动态算法后续性能预估

以后加入配方图后：

假设：

```text
物品 3000 ~ 10000
配方 2000 ~ 20000
迭代 64
```

复杂度：

```text
O(recipeCount * iterations)
```

例如：

```text
20000 * 64 = 1280000 次配方检查
```

放在启动 / reload 阶段可以接受。

不要放在 tick 或每笔交易中。

---

## 22. Java 文件数量预估

第一阶段：

```text
约 14 ~ 18 个 Java 文件
```

推荐第一版：

```text
约 20 ~ 25 个 Java 文件
```

完整成熟版：

```text
约 40 ~ 60 个 Java 文件
```

真正的难点不是文件数量，而是边界规则：

```text
哪些物品系统能买
哪些物品系统能卖
哪些只允许玩家市场
哪些禁止交易
```

---

## 23. 第一阶段验收标准

### 23.1 编译通过

```text
./gradlew build
```

必须通过。

---

### 23.2 价格查询可用

进入游戏，手持铁锭：

```text
/market price hand
```

应显示：

```text
Source: ANCHOR
Reference: 100
System Buy: 65
System Sell: 135
```

---

### 23.3 玩家成交记录可用

流程：

```text
玩家 A 上架铁锭
玩家 B 购买铁锭
```

结果：

```text
EconomySavedData 中记录成交
MarketPriceIndexService 能计算 recentTradeCount
MarketPriceIndexService 能计算 VWAP
```

---

### 23.4 系统商店走新报价

系统商店交易时：

```text
SystemMarketService.trade 调用 SystemPriceService
```

而不是直接固定：

```text
offer.unitPrice() * count
```

---

### 23.5 旧配置兼容

如果某个系统商店商品没有动态价格：

```text
fallback 到 offer.unitPrice()
```

不能让已有系统商店全部失效。

---

## 24. Codex 分批执行 Prompt

### Prompt 1：新增价格基础模型

```text
请只完成第一批任务：
1. 新增 com.nekros.market.pricing 包下的 PriceSource、PriceConfidence、TradeLevel、PriceProfile。
2. 修改 Config.java，追加 pricing.anchors、pricing.defaultBuyRatio、pricing.defaultSellRatio、pricing.marketConfidenceTradeCount。
3. 新增 PriceRegistry 和 PriceResolver 的最小实现。
不要修改 SystemMarketService，不要修改 MarketService，不要做 GUI。
完成后运行 ./gradlew build 并修复编译错误。
```

---

### Prompt 2：新增成交记录存档

```text
请只完成第二批任务：
1. 新增 MarketTradeRecord。
2. 新增 EconomySavedData。
3. 新增 MarketTradeHistoryService。
4. 修改 MarketService.buy，在玩家购买成功后记录成交。
不要改系统商店，不要改 GUI。
完成后运行 ./gradlew build 并修复编译错误。
```

---

### Prompt 3：新增市场价格指数

```text
请只完成第三批任务：
1. 新增 MarketPriceIndexService。
2. 通过 EconomySavedData 的成交记录计算 recentTradeCount、recentVWAP、confidence。
3. 让 PriceResolver 可以读取玩家市场价格并混合 referencePrice。
不要改 GUI。
完成后运行 ./gradlew build 并修复编译错误。
```

---

### Prompt 4：改造系统商店报价

```text
请只完成第四批任务：
1. 新增 SystemTradeQuote。
2. 新增 SystemPriceService。
3. 修改 SystemMarketService.trade，使系统商店交易通过 SystemPriceService 报价。
4. 保留 offer.unitPrice 作为 fallback，保证旧系统商店配置仍可用。
不要做滑点积分，不要改 GUI。
完成后运行 ./gradlew build 并修复编译错误。
```

---

### Prompt 5：新增价格命令

```text
请只完成第五批任务：
1. 新增 PriceAdminCommands。
2. 增加 /market price hand。
3. 增加 /market price reload。
4. 在 MarketCommands.register 中挂载 price 子命令。
不要重写整个 MarketCommands。
完成后运行 ./gradlew build 并修复编译错误。
```

---

### Prompt 6：系统商店货架与定价权

```text
请只完成第六批任务：
1. 不要把系统购买页做成自动展示所有可定价物品。
2. 将系统购买页明确设计为 admin 管理的“货架制”。
3. 新增系统商品价格模式 PriceMode：
   - FIXED：完全使用管理员填写价格
   - AUTO：使用系统算法参考价
   - ANCHOR：管理员填写价格作为基础锚点，再由动态算法发散
   - MULTIPLIER：系统算法价 × 管理员倍率
   - BAND：系统算法价，但限制最低价和最高价
4. 保持旧 systemMarket.offers 五段格式兼容。
5. 旧格式 id|type|item|unitPrice|category 默认视为：
   priceMode = FIXED
   basePrice = unitPrice
6. 暂时不要做完整 GUI 重构，先完成服务端数据结构、解析、报价逻辑。
完成后运行 ./gradlew build 并修复编译错误。
```

---

## 25. 不要做的事

第一阶段明确禁止：

```text
不要重写 MarketService
不要重写 MarketScreen
不要改变 MarketListing 存档结构
不要改变 SystemMarketOffer 旧配置格式
不要移除现有 /market 命令
不要把系统商店改成全物品万能兑换
不要让挂单价影响市场价
不要每笔交易扫描所有配方
不要默认允许 NBT / DataComponent 复杂物品进系统商店
不要把系统购买页自动填充为所有可定价物品
不要把收购页和购买页做成镜像关系
不要让系统商店成为万能兑换机
```

---

## 26. 系统商店货架与定价权计划

### 26.1 核心原则

系统商店必须区分两个入口：

```text
收购页 = 系统吸收资源的入口
购买页 = 系统向玩家投放资源的出口
```

这两个页面不应该是镜像关系。

不要做成：

```text
系统能收购什么，就自动出售什么
```

正确方向：

```text
系统收购页可以自动生成
系统购买页必须由 admin 控制货架
```

原因：

```text
收购页的主要风险是刷钱
购买页的主要风险是破坏模组 progression
```

自动定价系统负责给出价格建议，不负责自动决定系统应该出售什么。

---

### 26.2 收购页策略

收购页可以支持自动填充。

自动收购物品来源：

```text
pricing anchors
tag 规则
tradeLevel = SYSTEM_BUY_ONLY
tradeLevel = SYSTEM_BUY_AND_SELL
自动收购规则 autoBuyRules
```

适合自动收购：

```text
作物
原木
矿物
锭
宝石
基础掉落物
基础模组材料
```

默认不要自动收购：

```text
装备
带 DataComponent / NBT 的复杂物品
容器类物品
机器
高级工业产物
终局材料
任务物品
创造物品
```

第一版可以先不实现完整 `autoBuyRules`，但设计上要预留入口。

---

### 26.3 购买页策略

购买页不要自动显示所有可定价物品。

购买页应采用 admin 管理的货架制。

购买页商品由 admin 明确上架，包含：

```text
物品 ID
所在标签页 category
显示顺序
价格模式 priceMode
基础价 basePrice
倍率 multiplier
最低价 minPrice
最高价 maxPrice
是否允许动态价格
是否需要真实库存
```

系统能算出价格，不代表系统必须出售这个物品。

特别是这些物品默认不应进入购买页：

```text
高级机器
核工业产物
反物质
AE2 高级存储
Boss 掉落物
任务物品
创造物品
```

这些物品最多只能：

```text
玩家市场交易
只显示参考价
admin 手动固定价并限量出售
```

---

### 26.4 PriceMode 设计

新增枚举：

```java
public enum PriceMode {
    FIXED,
    AUTO,
    ANCHOR,
    MULTIPLIER,
    BAND
}
```

含义：

```text
FIXED:
    完全使用 admin 填写价格。
    不受动态算法影响。
    适合活动商品、特殊商品、临时补给。

AUTO:
    不使用 admin 填写价格。
    完全按 PriceResolver / SystemPriceService 算法价。
    适合基础资源。

ANCHOR:
    admin 填写价格作为基础锚点。
    动态算法基于该锚点继续发散。
    适合小麦、铁锭、钻石这类需要人工定基准但允许动态波动的物品。

MULTIPLIER:
    使用系统算法价 × admin 倍率。
    适合“比正常价更贵/更便宜”的服务器调控商品。

BAND:
    使用系统算法价，但限制最低价和最高价。
    适合防止动态价格过低或过高的基础商品。
```

---

### 26.5 新系统商品格式

当前旧格式：

```text
id|type|item|unitPrice|category
```

继续兼容，不要破坏旧配置。

新格式建议：

```text
id|type|item|category|priceMode|basePrice|multiplier|minPrice|maxPrice|flags
```

例子：

```text
buy_iron|sell_to_player|minecraft:iron_ingot|#矿物|AUTO|0|1.0|0|0|
buy_diamond|sell_to_player|minecraft:diamond|#矿物|MULTIPLIER|0|1.5|0|0|
buy_wheat|sell_to_player|minecraft:wheat|#作物|ANCHOR|5|1.0|2|20|
buy_plutonium|sell_to_player|mekanism:pellet_plutonium|#高级|FIXED|50000|1.0|0|0|admin_only
```

旧格式解释规则：

```text
id|type|item|unitPrice|category
```

等价于：

```text
priceMode = FIXED
basePrice = unitPrice
multiplier = 1.0
minPrice = 0
maxPrice = 0
flags = ""
```

---

### 26.6 系统出售商品分类建议

系统出售商品分 4 类。

#### A. 永久基础商品

```text
木头
石头
铁
铜
煤
红石
基础食物
基础建筑材料
```

推荐价格模式：

```text
AUTO
ANCHOR
BAND
```

#### B. 服务器调控商品

```text
钻石
绿宝石
下界石英
模组基础矿物
服务器阶段性短缺资源
```

推荐价格模式：

```text
MULTIPLIER
BAND
```

#### C. 活动 / 限购商品

```text
节日商品
特殊食物
装饰方块
钥匙
稀有消耗品
```

推荐价格模式：

```text
FIXED
```

后续可扩展：

```text
dailyLimit
weeklyLimit
stockLimit
```

#### D. 高级 / 终局商品

```text
mekanism:pellet_plutonium
mekanism:pellet_antimatter
高级机器
AE2 高级存储
Boss 掉落
```

默认不进入系统购买页。

推荐处理：

```text
PLAYER_MARKET_ONLY
REFERENCE_ONLY
admin 手动固定价并限量出售
```

---

### 26.7 推荐新增 Java 文件

建议新增：

```text
src/main/java/com/nekros/market/system/PriceMode.java
src/main/java/com/nekros/market/system/SystemOfferPricing.java
```

如果不想拆太细，也可以先把字段扩展到 `SystemMarketOffer`。

但长期建议：

```text
SystemMarketOffer 负责商品身份、物品、类型、分类
SystemOfferPricing 负责 priceMode、basePrice、multiplier、minPrice、maxPrice、flags
```

---

### 26.8 SystemPriceService 调整规则

系统报价时：

```text
先读取 SystemMarketOffer
再读取 SystemOfferPricing
再读取 PriceResolver 算法价
最后按 PriceMode 得到最终单价
```

伪逻辑：

```java
long resolveSystemUnitPrice(SystemMarketOffer offer, PriceProfile profile) {
    return switch (offer.pricing().mode()) {
        case FIXED -> offer.pricing().basePrice();

        case AUTO -> dynamicPriceFromProfile(profile);

        case ANCHOR -> dynamicPriceFromAnchor(
                offer.pricing().basePrice(),
                profile
        );

        case MULTIPLIER -> Math.round(
                dynamicPriceFromProfile(profile) * offer.pricing().multiplier()
        );

        case BAND -> clamp(
                dynamicPriceFromProfile(profile),
                offer.pricing().minPrice(),
                offer.pricing().maxPrice()
        );
    };
}
```

注意：

```text
购买页中没有 admin 上架的物品，不允许因为 PriceResolver 能估价就自动出售。
```

---

### 26.9 GUI 设计边界

第一版不要做完整 GUI 重构。

第一版只需要保证：

```text
旧系统商店 GUI 还能显示
购买页只显示 systemMarket.offers / admin 上架商品
显示价格已经经过 SystemPriceService 报价
```

后续 GUI 再加：

```text
PriceMode 选择框
basePrice 输入框
multiplier 输入框
minPrice / maxPrice 输入框
flags 勾选
是否动态定价开关
```

---

### 26.10 反万能兑换红线

必须遵守：

```text
自动定价 ≠ 自动上架
可收购 ≠ 可出售
有参考价 ≠ 系统可以买卖
```

不要让系统购买页变成：

```text
所有可估价物品列表
```

也不要让玩家通过基础物资间接买到所有高级物品。

系统商店定位：

```text
系统收购页：资源回收和经济托底
系统购买页：admin 控制资源投放
玩家市场：高级物品和稀有物品真实定价
```

---

## 27. 后续阶段计划

第二阶段：

```text
基础资源动态库存
系统买入越多越贬值
每日行情波动
系统真实库存 actualStock
系统有效库存 effectiveStock
系统商店购买页货架制
PriceMode / 定价权
admin 上架商品与自动定价解耦
```

第三阶段：

```text
配方图推导
普通合成 / 烧炼 / 切石支持
基础 SCC / 等价组
价格报告
```

第四阶段：

```text
玩家挂单建议价
GUI 展示参考价
成交价趋势
管理员价格报告
```

第五阶段：

```text
Mekanism / AE2 / Create 等模组特殊配方适配
稀缺度规则
掉落物规则
Boss 物品规则
高级反套利检测
```

---

## 28. 总结

第一阶段的唯一目标：

```text
让 Nek's Market 拥有一个可运行的价格档案与成交记录框架。
```

这不是最终完整经济系统，但它是后续动态算法、配方推导、系统调控、玩家市场反馈的基础。

Codex 应该严格小步提交，避免一次性大改。
