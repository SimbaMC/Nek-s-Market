# NeksMarket 开发接力说明：当前状态与测试指南

更新时间：2026-05-08  
项目环境：Minecraft 1.21.1 / NeoForge 21.1.228 / Java 21  
主要执行计划参考：`NeksMarket_Codex执行用_动态经济系统计划_加入系统商店货架与定价权.md`  
早期参考资料：`MC自动化定价模型.md`

## 1. 当前总体状态

NeksMarket 当前已经从基础玩家交易市场，扩展到了系统商店、动态价格、出货箱回收、物品回收价提示、配方推导定价的早期版本。

目前可稳定使用的核心功能：

- 玩家交易市场基础功能。
- 系统商店货架配置。
- 系统收购/售卖报价。
- 系统库存记录。
- 基于库存和短期买卖压力的动态调价。
- 出货箱方块。
- 出货箱 GUI，54 格大箱子布局，带“售出”按钮。
- 出货箱自动化输入接口，兼容原版漏斗以及 NeoForge item handler 类自动化输入。
- 物品 tooltip 显示一行 `回收价：xxx` 或 `回收价：暂无`。
- 基础价格来源：锚定价、自然/掉落规则、配方推导、玩家市场成交价混合。
- `/market price` 系列价格诊断命令。

最近一次重要修复：

- 全量价格推导曾经在启动或命令执行时卡住服务器主线程。
- 现在启动时不再自动全量 warmup。
- `/market price reload` 不再全量预热，只重载注册表。
- `/market price coverage` 和 `/market price unresolved` 目前使用快速统计，不做递归全图计算，避免再次卡死。
- 配方递归推导已经增加中间节点缓存，后续做后台全量计算时可继续复用。

## 2. 当前开发环境说明

常用构建命令：

```powershell
.\gradlew.bat build
```

常用测试方式：

- 推荐用 IDEA 的 `runClient` 测试单机集成服务器。
- 目前一般不需要 `runServer`。
- 若只测试 GUI、出货箱、普通命令、单机逻辑，`runClient` 足够。

测试用的外部 mod 当前已临时禁用，没有删除：

- 原目录：`run/mods`
- 禁用目录：`run/mods.disabled`

已被挪到 `run/mods.disabled` 的测试 mod：

- `create-1.21.1-6.0.10.jar`
- `appliedenergistics2-19.2.17.jar`
- `flywheel-neoforge-1.21.1-1.0.6.jar`
- `guideme-21.1.15.jar`
- `ponder-neoforge-1.0.82+mc1.21.1.jar`

后续需要测试机械动力或 AE2 兼容时，把这些 jar 移回 `run/mods` 即可。

## 3. 已实现功能详情

### 3.1 玩家交易市场

玩家市场功能仍保留。

常用命令：

```mcfunction
/market
/market balance
/market sell <单价> [数量]
/market listings
/market buy <挂单ID> [数量]
/market cancel <挂单ID>
/market claim
/market mine
```

说明：

- `/market` 打开/显示市场相关入口或帮助。
- `/market balance` 查看余额。
- `/market sell` 将主手物品上架。
- `/market listings` 查看玩家挂单。
- `/market buy` 购买玩家挂单。
- `/market claim` 领取已成交收入或退回物品。
- `/market mine` 查看自己的挂单。

### 3.2 系统商店与货架

系统商店支持两类 offer：

- `sell_to_player`：系统卖给玩家，显示在系统商店购买页。
- `buy_from_player`：系统从玩家处回收物品。

系统商店价格模式：

- `FIXED`：固定价格。
- `AUTO`：自动使用价格系统推导。
- `ANCHOR`：用填写的 base 作为锚点，再套系统倍率。
- `MULTIPLIER`：基于自动价乘倍率。
- `BAND`：带最小/最大价格限制。

常用命令：

```mcfunction
/market system reload
/market system quote sell_to_player <物品> [数量]
/market system quote buy_from_player <物品> [数量]
/market system stock <物品>
/market system stock sell_to_player <物品>
/market system stock buy_from_player <物品>
```

添加系统售卖货架：

```mcfunction
/market system shelf <物品> <分类序号> [flags]
```

示例：

```mcfunction
/market system shelf minecraft:oak_log 1 infinite_stock
```

注意：

- 命令里的分类使用数字 `1, 2, 3...`。
- GUI 中可以显示为“建材”等人类可读名称。
- 用户明确要求：命令里不要用中文分类名作为参数。

添加系统回收：

```mcfunction
/market system buyback <物品> [flags]
```

按标签批量添加：

```mcfunction
/market system shelftag <标签ID> <分类序号> [flags]
/market system buybacktag <标签ID> [flags]
```

示例：

```mcfunction
/market system shelftag minecraft:logs 1 infinite_stock
/market system buybacktag minecraft:logs
```

### 3.3 系统库存与动态调价

系统库存使用 `SystemStockSavedData` 保存。

当前记录：

- 实际库存。
- 累计从玩家处回收数量。
- 累计卖给玩家数量。
- 短期回收压力。
- 短期售出压力。

动态调价大致逻辑：

- 系统库存越高，系统继续回收该物品的价格越低。
- 系统库存越低，系统卖出该物品的价格可上升。
- 玩家短时间大量卖给系统，会形成短期回收压力，进一步压低回收价。
- 玩家短时间大量从系统买走，会形成短期售出压力。

相关配置位于 `run/config/neksmarket-common.toml`：

- `systemMarket.stockHealthyTarget`
- `systemMarket.stockCurveStrength`
- `systemMarket.stockPressureHalfLifeTicks`
- `systemMarket.stockPressureMaxRatio`
- `systemMarket.stockBuyPriceImpactPerItem`
- `systemMarket.stockMinBuyPriceRatio`

测试命令：

```mcfunction
/market system stock minecraft:ender_pearl
/market system quote buy_from_player minecraft:ender_pearl 64
```

### 3.4 出货箱

方块：

```mcfunction
neksmarket:seller_box
```

当前行为：

- 2 格高方块。
- 放置时正面朝向玩家。
- 挖掘等级：石镐。
- 下半部分保存真实库存。
- 上半部分转发能力到下半部分。
- 破坏时掉落内部物品。
- GUI 是 54 格大箱子布局。
- GUI 有一个“售出”按钮。

自动化：

- 支持 NeoForge `Capabilities.ItemHandler.BLOCK`。
- 原版漏斗可向出货箱输入物品。
- 机械动力/AE2 兼容之前已初步验证过，但当前测试 mod 被临时移到 `run/mods.disabled`。

测试建议：

1. 创造模式拿出出货箱。
2. 放置方块，确认两格高和朝向正常。
3. 打开 GUI。
4. 放入可回收物品。
5. 点击“售出”。
6. 检查余额增加、物品扣除、日志无错误。
7. 用漏斗向箱子输入物品，确认能进入库存。

### 3.5 物品 tooltip 回收价

物品悬停 tooltip 目前显示：

```text
回收价：xxx
```

如果没有价格：

```text
回收价：暂无
```

注意：

- 用户希望只显示这一行。
- 不要显示长解释，不要显示货币单位，避免 tooltip 太长。
- 之前 tooltip 查询造成过浏览 mod 物品时帧率下降，已经做过缓存优化。

### 3.6 价格系统

当前价格来源：

- `ANCHOR`：锚定价。
- `NATURAL`：自然资源/掉落规则。
- `DERIVED`：配方推导。
- `PLAYER_MARKET`：玩家市场近期成交均价。
- `MIXED`：基础价和玩家市场成交价混合。
- `UNKNOWN`：未知。

核心类：

- `PriceProfile`
- `PriceRegistry`
- `PriceResolver`
- `PriceSource`
- `PriceConfidence`
- `TradeLevel`
- `DerivedPriceService`
- `NaturalPriceSource`
- `EquivalentItemPriceSource`
- `SystemPriceService`

已支持的配方推导：

- 原版 crafting。
- smelting。
- blasting。
- smoking。
- campfire cooking。

暂未正式支持：

- 机械动力机器配方。
- AE2 处理配方。
- 其他科技模组自定义 recipe type。

设计方向：

- 先把原版和通用配方图做好。
- 再加可插拔的 recipe source adapter。
- 机械动力/AE2 等模组通过独立 adapter 接入，不要把特殊逻辑硬塞进主推导器。

## 4. 当前可用价格命令

查看主手物品价格：

```mcfunction
/market price hand
```

查看指定物品价格：

```mcfunction
/market price item <物品>
```

示例：

```mcfunction
/market price item minecraft:diamond
```

查看玩家市场成交历史：

```mcfunction
/market price history hand
/market price history item <物品>
```

重载定价注册表：

```mcfunction
/market price reload
```

当前安全行为：

- 只重载注册表。
- 不做全量预热。
- 不会卡住服务器。

快速覆盖率：

```mcfunction
/market price coverage
```

当前安全行为：

- 只统计锚点、等价物、自然/掉落规则等快速来源。
- 不做完整递归配方图。
- 因此覆盖率数字偏保守。

无法定价样本：

```mcfunction
/market price unresolved 50
```

当前安全行为：

- 同样是快速版本。
- 主要用于看还缺哪些基础锚点或自然资源规则。

## 5. 已知问题与注意事项

### 5.1 不要在主线程做全量价格图

之前出现过严重问题：

- `/market price reload`
- `/market price coverage`
- `/market price unresolved`

一度会触发全量递归配方图计算，导致服务器主线程卡住，表现为：

- 存档进不去。
- 命令没反应。
- `/market` 也没反应。

当前已经止血：

- 启动不 warmup。
- reload 不 warmup。
- coverage/unresolved 用快速版。

后续如果要恢复完整覆盖率，必须做成后台任务或分 tick 任务。

### 5.2 配方推导缓存刚做过修复

当前 `DerivedPriceService` 已经改为：

- 递归中间节点也会缓存。
- 循环引用和超过深度限制时不会污染全局缓存。

但后台全量计算还没有实现，不要贸然把 `DerivedPriceService.coverage` 重新接回命令主线程。

### 5.3 日志编码显示问题

日志中中文可能显示为乱码，例如：

```text
宸查噸杞...
```

这主要是日志显示/编码问题，游戏内聊天显示通常正常。后续可以考虑统一文件编码和运行参数，但这不是当前核心阻塞。

### 5.4 当前测试环境已禁用 Create/AE2

为了保持日志干净，Create/AE2 等 jar 已移到：

```text
run/mods.disabled
```

不要删除，后续兼容性测试还要用。

## 6. 推荐下一步计划

### 阶段 A：后台价格图任务

目标：

- 做一个不会卡主线程的全量价格图计算器。

建议实现方式：

- 新建一个服务，例如 `PriceGraphWarmupService`。
- 服务器启动时只初始化任务，不立即跑完。
- 每 tick 只处理有限数量候选物，例如 10 到 30 个。
- 命令 `/market price coverage` 读取最近一次快照，不现场全量计算。
- 命令 `/market price warmup start` 手动启动后台预热。
- 命令 `/market price warmup status` 查看进度。
- 命令 `/market price warmup cancel` 取消。

建议数据结构：

- 待处理队列：`ArrayDeque<ResourceLocation>`。
- 当前快照：候选数、已处理数、成功数、未知数、来源统计、耗时。
- 每个 tick 有处理预算，不能 while 跑到完。

### 阶段 B：扩大基础资源规则

目标：

- 让更多配方链有起点。

当前已有一些基础规则：

- 土石沙。
- 木材。
- 农作物。
- 畜牧。
- 钓鱼。
- 常见生物掉落。
- 部分下界/末地资源。

后续可继续补：

- 更多矿石原矿与矿块。
- 染料体系。
- 海晶石、珊瑚、海洋资源。
- 红石元件基础件。
- 药水材料。
- 装饰性自然方块。

注意：

- 规则最好保留在配置可覆盖层。
- 内置规则只作为默认可用的基础价格。

### 阶段 C：配方推导解释与调试

目标：

- 管理员能知道某个价格是怎么来的。

建议命令：

```mcfunction
/market price explain <物品>
```

输出内容：

- 最终来源。
- 选中的最便宜配方。
- 每个材料的单价。
- 加工费。
- 加成。
- 输出数量折算。

注意：

- 玩家 tooltip 不显示这些内容。
- 只给管理员命令查看。

### 阶段 D：模组配方适配

目标：

- 支持机械动力、AE2、其他科技模组的处理配方。

建议架构：

- 定义统一接口：

```java
interface RecipePriceAdapter {
    void indexRecipes(...);
}
```

每个 adapter 负责：

- 识别某类 recipe type。
- 读取输入物品。
- 读取输出物品和数量。
- 添加机器处理费、能耗费或复杂度系数。

不要把 Create/AE2 的处理逻辑直接写死进 `DerivedPriceService` 主体。

### 阶段 E：经济平衡和服务器配置体验

目标：

- 让服主不用一个个上架系统回收物。
- 通过出货箱 + tooltip + 自动价格系统完成大多数回收场景。

建议保留：

- 系统商店售卖页。
- 少量可配置回收页面，给服主管理特殊物品。
- 出货箱作为主要批量回收方式。

不建议：

- 让玩家通过系统回收页面一个个卖物品。
- 让服主手动为所有物品建立系统回收货架。

## 7. 建议测试流程

### 基础启动测试

1. 确认 `run/mods` 为空或只包含当前需要测试的 mod。
2. 执行 IDEA `runClient`。
3. 进入已有测试世界。
4. 查看日志是否出现：

```text
Nek's Market pricing registry loaded. Price graph warmup is skipped during world startup.
```

### 市场命令测试

```mcfunction
/market
/market balance
/market listings
/market price reload
/market price coverage
/market price unresolved 20
```

预期：

- 命令立即返回。
- 不出现卡死。
- `/market` 仍可继续使用。

### 价格查询测试

```mcfunction
/market price item minecraft:diamond
/market price item minecraft:oak_log
/market price item minecraft:ender_pearl
/market price item minecraft:crafting_table
```

预期：

- 钻石来自锚定价。
- 原木来自锚定价或等价/自然规则。
- 末影珍珠来自自然/掉落规则。
- 工作台来自配方推导。

### 系统报价测试

```mcfunction
/market system quote buy_from_player minecraft:ender_pearl 64
/market system quote sell_to_player minecraft:diamond 1
/market system stock minecraft:ender_pearl
```

预期：

- 报价能返回。
- 能显示库存、动态价格、来源说明。
- 不阻塞服务器。

### 出货箱测试

1. 放置 `neksmarket:seller_box`。
2. 打开 GUI。
3. 放入钻石、末影珍珠、原木等。
4. 点击“售出”。
5. 执行：

```mcfunction
/market balance
/market system stock minecraft:diamond
```

预期：

- 余额增加。
- 可回收物品被扣除。
- 系统库存变化。
- 无法定价物品保留或显示无法售出。

### Tooltip 测试

1. 打开创造物品栏。
2. 悬停原版物品。
3. 查看 tooltip。

预期：

- 有价格：`回收价：数字`
- 无价格：`回收价：暂无`
- 浏览物品栏不卡顿。

## 8. 当前最重要的开发警戒线

不要做这些事：

- 不要在服务器启动时全量递归计算所有配方价格。
- 不要在命令主线程里同步跑完整价格图。
- 不要把系统商店卡片大小改掉，用户已经精调过。
- 不要让 tooltip 显示长解释。
- 不要删除 `run/mods.disabled` 里的测试 mod。

可以安全继续做：

- 后台分 tick 价格图任务。
- 快速价格规则补充。
- 单物品价格解释命令。
- 出货箱体验小修。
- 系统报价显示优化。

## 9. 给下一位开发者的简短路线

优先顺序建议：

1. 做 `PriceGraphWarmupService`，把全量价格图计算变成后台分 tick。
2. 让 `/market price coverage` 读取后台快照，而不是现场计算。
3. 加 `/market price warmup start/status/cancel`。
4. 再逐步扩大自然资源规则和等价物规则。
5. 最后再考虑 Create/AE2 的 adapter。

当前最关键目标：

> 继续推进算法覆盖率，但绝不能再卡住存档加载、服务器主线程或 `/market` 主命令。

