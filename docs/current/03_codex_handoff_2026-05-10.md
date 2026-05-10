# NeksMarket Codex 接力文档：2026-05-10

面向下一位 Codex / 开发者。  
项目环境：Minecraft 1.21.1 / NeoForge 21.1.228 / Java 21。  
当前分支：`master`。  
当前工作树：有大量未提交改动。

## 1. 本次接力结论

当前代码不是“语法残缺/编译到一半”的状态。

已经验证：

```powershell
.\gradlew.bat clean compileJava
.\gradlew.bat build
```

两者均成功。`build` 没有测试任务可跑，只有编译、资源处理和打包验证。

本次只做了一处小修：

- `src/main/java/com/nekros/market/pricing/system/SystemPriceService.java`
- 将一条乱码失败提示改回：`系统商店不出售该物品。`

没有做大规模重构，没有提交 Git。

## 2. 当前未提交改动概览

`git status --short --branch` 显示：

- 旧的根目录规划/交接文档被删除或移动。
- 新增 `docs/` 文档目录。
- 动态经济、价格系统、系统商店、出货箱、预算、账本相关代码有大量修改。
- 新增关键文件：
  - `src/main/java/com/nekros/market/pricing/derived/PriceWarmupService.java`
  - `src/main/java/com/nekros/market/pricing/policy/EconomicPolicy.java`
  - `src/main/java/com/nekros/market/pricing/policy/EconomicPolicyRegistry.java`
  - `src/main/java/com/nekros/market/pricing/policy/EconomicTier.java`
  - `src/main/java/com/nekros/market/pricing/system/SystemBuyPressure.java`
  - `src/main/java/com/nekros/market/storage/EconomyLedgerSavedData.java`
  - `src/main/java/com/nekros/market/storage/SystemPayoutBudgetSavedData.java`

重要：不要随手还原这些删除的旧文档。它们大概率已经归档到 `docs/archive/`。

## 3. 当前功能推进程度

### 3.1 价格与经济分级

已实现：

- `EconomicTier` 经济分级。
- `EconomicPolicy` 经济策略。
- `EconomicPolicyRegistry` 默认推断、物品覆盖、标签覆盖、自动回收 allow/deny 列表。
- `PriceResolver` 已接入经济策略，决定系统自动回收价。
- `/market price policy` 可查看分级和策略。
- `/market price curve` 可查看回收压力曲线。
- `/market price audit` 可审计自动回收允许/阻止物品。

当前策略方向：

- 常见资源允许系统回收。
- 高自动化产物允许回收但衰减更强。
- Boss、推进关键、未知、高风险物品默认不自动买卖。
- 系统自动出售仍受货架控制，不是能定价就自动出售。

### 3.2 S/C/Delta 回收压力

核心类：

- `SystemBuyPressure`
- `SystemStockSavedData`
- `SystemPriceService`

当前模型已接入：

- `S`：长期累计净流入，使用系统累计回收量减去系统卖出缓解量。
- `C`：中期记忆，保存在 `SystemStockSavedData.buyMemory`，按 `lambda` 惰性衰减。
- `Delta`：本次交易数量滑点。
- `minBuyRatio`：最低回收倍率下限。
- `SYSTEM_BUY_MIN_PAYING_UNIT_PRICE`：过低单价直接不支付。

交易时会记录：

- 系统实际库存。
- 累计回收/售出。
- 中期回收记忆。

### 3.3 预算和经济账本

新增：

- `SystemPayoutBudgetSavedData`
- `EconomyLedgerSavedData`

系统回收支付会受预算限制：

- 全局每日预算。
- 单玩家每日预算。
- 分经济层级预算。
- 系统售卖收入可部分回流为回收预算。
- 预算可按玩家货币总供应量缩放。

账本记录：

- 系统回收支出。
- 系统售卖收入。
- 玩家交易额。
- 玩家交易税。
- 上架费销毁。
- 管理员发钱/扣钱。
- 系统回收/售卖按物品的 top 统计。

### 3.4 出货箱

核心类：

- `SellerBoxService`
- `SellerBoxMenu`
- `SellerBoxBlockEntity`

当前出货箱已接入自动回收：

- 遍历 54 格容器。
- 对每组物品调用 `SystemPriceService.quoteAutomaticBuyback`。
- 只回收可自动回收且无组件补丁的物品。
- 成交后增加玩家余额。
- 记录系统库存、经济账本、支付预算。
- 不可回收物品会跳过并留在箱内。

仍需游戏内验证：

- 大量格子同时出售时，预算限制和跳过逻辑是否符合预期。
- 出货箱 GUI 提示是否足够清晰。
- NBT/耐久/附魔物品是否都正确拒绝自动回收。

### 3.5 后台价格图 warmup

核心类：

- `PriceWarmupService`
- `DerivedPriceService`
- `PriceAdminCommands`

已经实现：

```mcfunction
/market price warmup start
/market price warmup start <itemsPerTick>
/market price warmup status
/market price warmup cancel
```

启动时不会自动全量预热。`NeksMarket` 中只是注册 server tick，让手动启动的 warmup 分 tick 推进。

重要警戒线：

- 不要把完整配方图计算重新放回服务器启动。
- 不要让 `/market price report` 在主线程同步递归全量计算。
- `itemsPerTick` 当前最大 64，默认 8。

### 3.6 配方推导

`DerivedPriceService` 已扩展：

- 原版 crafting。
- smelting / blasting / smoking / campfire cooking。
- 通用 recipe type 策略。
- 中间节点缓存。
- warmup 专用解析入口。

仍未正式完成：

- Create / AE2 专用 recipe adapter。
- 管理员级 `/market price explain <物品>` 的完整配方解释。

## 4. 本次检查发现

### 4.1 编译状态

通过：

```powershell
.\gradlew.bat build
```

结论：没有明显半截 Java 文件、缺类、缺方法、语法错误。

### 4.2 乱码状态

PowerShell 默认输出会把 UTF-8 中文显示成乱码，这不一定代表源码坏了。

我用 `Get-Content -Encoding UTF8` 复查后确认，大部分源码中文是正常的。

真正污染到源码字面量的乱码只搜到一处，已经修复：

- `SystemPriceService.java` 中一条失败提示。

可复查命令：

```powershell
rg -n "鍥|杩|宸|鏈|涓|銆|绯荤|瀹氫|缁|浠锋" src\main\java
```

当前应该没有 Java 源码命中。`docs/current/02_current_status_and_test_guide.md` 中有一处故意引用的乱码示例，不需要修。

## 5. 建议下一步

优先级从高到低：

1. 用 IDEA `runClient` 进单机世界做集成测试。
2. 测试 `/market price reload/report/policy/curve/audit/recipes/warmup status`。
3. 测试 `/market price warmup start 8`，确认不会卡主线程。
4. 测试出货箱批量出售：常见可回收物、未知物、NBT/耐久/附魔物。
5. 测试预算耗尽：全局预算、个人预算、层级预算。
6. 测试系统商店买卖是否正确记录库存、账本和预算。
7. 如都稳定，再考虑提交一次当前大改动。

## 6. 推荐手动测试命令

价格系统：

```mcfunction
/market price item minecraft:oak_log
/market price item minecraft:diamond
/market price item minecraft:ender_pearl
/market price policy minecraft:ender_pearl
/market price curve minecraft:iron_ingot 4096
/market price report 20
/market price audit allowed 30
/market price audit blocked 30
/market price recipes 30
```

后台 warmup：

```mcfunction
/market price warmup status
/market price warmup start 8
/market price warmup status
/market price warmup cancel
```

系统商店：

```mcfunction
/market system quote buy_from_player minecraft:ender_pearl 64
/market system quote sell_to_player minecraft:diamond 1
/market system stock minecraft:ender_pearl
```

经济统计：

```mcfunction
/market economy
```

如果命令名或参数与当前实际注册有差异，以 Brigadier 自动补全为准。

## 7. 不要踩的坑

- 不要在服务器启动时自动跑完整价格图。
- 不要让 `/market price report` 现场同步递归计算所有物品。
- 不要删除 `run/mods.disabled`，Create/AE2 测试 jar 还在里面。
- 不要把所有能定价的物品自动开放系统出售。
- 不要让出货箱回收带 NBT、耐久、附魔或组件补丁的物品。
- 不要把 tooltip 变成长解释；用户希望只显示一行回收价。
- 不要随意还原旧根目录文档删除，先看 `docs/archive/`。

## 8. 文件入口速查

主 mod：

- `src/main/java/com/nekros/market/NeksMarket.java`
- `src/main/java/com/nekros/market/NeksMarketClient.java`
- `src/main/java/com/nekros/market/Config.java`

价格：

- `src/main/java/com/nekros/market/pricing/PriceResolver.java`
- `src/main/java/com/nekros/market/pricing/PriceRegistry.java`
- `src/main/java/com/nekros/market/pricing/derived/DerivedPriceService.java`
- `src/main/java/com/nekros/market/pricing/derived/PriceWarmupService.java`
- `src/main/java/com/nekros/market/pricing/system/SystemPriceService.java`
- `src/main/java/com/nekros/market/pricing/system/SystemBuyPressure.java`

经济策略：

- `src/main/java/com/nekros/market/pricing/policy/EconomicTier.java`
- `src/main/java/com/nekros/market/pricing/policy/EconomicPolicy.java`
- `src/main/java/com/nekros/market/pricing/policy/EconomicPolicyRegistry.java`

系统商店 / 出货箱：

- `src/main/java/com/nekros/market/system/SystemMarketService.java`
- `src/main/java/com/nekros/market/system/SystemMarketOffer.java`
- `src/main/java/com/nekros/market/system/SellerBoxService.java`

存档数据：

- `src/main/java/com/nekros/market/storage/SystemStockSavedData.java`
- `src/main/java/com/nekros/market/storage/SystemPayoutBudgetSavedData.java`
- `src/main/java/com/nekros/market/storage/EconomyLedgerSavedData.java`
- `src/main/java/com/nekros/market/storage/MarketSavedData.java`

命令：

- `src/main/java/com/nekros/market/command/MarketCommands.java`
- `src/main/java/com/nekros/market/command/PriceAdminCommands.java`

当前文档：

- `docs/current/01_economy_system_current_plan.md`
- `docs/current/02_current_status_and_test_guide.md`
- `docs/current/03_codex_handoff_2026-05-10.md`

