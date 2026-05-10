# NeksMarket

NeksMarket 是一个 Minecraft 1.21.1 NeoForge 市场模组，当前开发重点是玩家交易行、系统商店、出货箱回收，以及带经济分级和动态回收压力的服务器经济系统。

## 文档入口

新的 Codex 或开发者请先阅读：

```text
docs/00_START_HERE.md
```

当前最重要的文档：

```text
docs/current/01_economy_system_current_plan.md
docs/reference/pricing_formula_sc_delta.md
docs/current/02_current_status_and_test_guide.md
```

历史计划和旧交接文档已经归档到：

```text
docs/archive/
```

## 常用命令

构建：

```powershell
.\gradlew.bat build
```

常用测试命令：

```mcfunction
/market
/market price item minecraft:iron_ingot
/market system stock minecraft:iron_ingot
/market system quote buy_from_player minecraft:iron_ingot 64
```

## 开发提醒

- 当前主经济方案以 `docs/current/01_economy_system_current_plan.md` 为准。
- 当前回收价核心算法以 `docs/reference/pricing_formula_sc_delta.md` 为准。
- 不要把系统商店做成所有可定价物品的万能兑换机。
- 不要在服务器启动或普通命令里同步跑完整价格图。
- `run/mods.disabled` 中的测试 mod 不要删除，后续还要用于兼容性测试。

## NeoForge 参考

Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

