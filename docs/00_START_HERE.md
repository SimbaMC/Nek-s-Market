# NeksMarket 文档入口

更新时间：2026-05-08

这份文档给新的 Codex 或开发者使用。请先读这里，再继续看其他文档。

## 1. 当前主参考

当前开发应优先参考：

1. `docs/current/01_economy_system_current_plan.md`
   - 当前经济系统主方案。
   - 解释为什么不能照搬真实经济。
   - 定义经济分级、系统回收压力、货架制投放、玩家交易行和货币回收。

2. `docs/reference/pricing_formula_sc_delta.md`
   - 当前回收价核心公式参考。
   - 重点是 `S / C / Δ` 模型：

```text
buyPrice = baseBuyPrice * exp(-alpha * S^gamma - beta * C - delta * count)
```

3. `docs/current/02_current_status_and_test_guide.md`
   - 当前功能状态。
   - 可用命令。
   - 测试流程。
   - 已知问题。

## 2. 历史参考

以下文档只作为历史背景，不应覆盖当前主方案：

- `docs/archive/legacy_dynamic_economy_execution_plan.md`
  - 早期 Codex 执行计划。
  - 其中很多第一阶段任务已经完成或被新经济方案升级。

- `docs/archive/old_codex_handoff_dynamic_economy.md`
  - 旧交接文档。
  - 内容可能落后于当前代码。

- `docs/archive/project_brief_2026-05-08.md`
  - 早期项目简报。

- `docs/reference/early_auto_pricing_discussion.md`
  - 早期自动化定价讨论。
  - 可参考思路，但不是当前最终方案。

## 3. 当前开发方向

当前经济系统的方向是：

```text
价格推导系统
+ 经济分级系统
+ S/C/Δ 回收压力衰减
+ 系统商店货架制投放
+ 玩家交易行
+ 货币回收
```

核心原则：

- 自动定价不等于自动上架。
- 可回收不等于可出售。
- 系统商店不是万能兑换机。
- 高阶资源不能被低阶无限资源稳定兑换。
- 自动化可以赚钱，但不能永久稳定印钞。

## 4. 当前代码状态速记

已经实现：

- 玩家交易市场。
- 系统商店货架。
- 系统库存。
- 出货箱回收。
- tooltip 显示 `回收价：xxx` / `回收价：暂无`。
- 配方推导定价。
- 后台价格图 warmup。
- 经济分级诊断。
- 系统回收价 `S/C/Δ` 模型。
- 经济策略配置化：

```text
pricing.economy.policies
pricing.economy.overrides
```

## 5. 推荐启动检查

构建：

```powershell
.\gradlew.bat build
```

常用测试命令：

```mcfunction
/market
/market price item minecraft:iron_ingot
/market system stock minecraft:iron_ingot
/market system quote buy_from_player minecraft:iron_ingot 1
/market system quote buy_from_player minecraft:iron_ingot 64
/market price warmup start
/market price warmup status
```

## 6. 开发警戒线

不要做：

- 不要在服务器启动时同步全量递归价格图。
- 不要在命令主线程里跑完整全图计算。
- 不要把系统出售页自动填满所有可定价物品。
- 不要让系统商店变成万能兑换机。
- 不要改大已调好的市场卡片尺寸。
- 不要删除 `run/mods.disabled` 中的测试 mod。

继续开发时，如果不确定文档冲突，以 `docs/current/01_economy_system_current_plan.md` 和 `docs/reference/pricing_formula_sc_delta.md` 为准。

