# NeksMarket 动态经济系统 Codex 交接文档

更新时间：2026-05-08  
项目目录：`C:\Users\A1242\IdeaProjects\neksmarket-template-1.21.1`  
技术栈：Minecraft 1.21.1 / NeoForge / Java 21 / Gradle  
Mod ID：`neksmarket`  
主包名：`com.nekros.market`

## 给下一个 Codex 的第一句话

这个仓库已经进入“动态经济核心 + 系统商店货架与定价权”阶段。不要从零重写，也不要回滚现有改动。当前工作树有大量已完成但未提交的功能改动，还有若干用户原本就有的资源文件。继续开发前先读这份文档，然后用 `git status --short` 确认现状。

每次完成一个大功能后，请给用户一份简单测试方案。

## 当前构建状态

最近一次构建命令：

```powershell
.\gradlew.bat build
```

结果：`BUILD SUCCESSFUL`

## 当前 Git 状态概览

已修改的主要源码文件：

```text
src/main/java/com/nekros/market/Config.java
src/main/java/com/nekros/market/NeksMarket.java
src/main/java/com/nekros/market/client/MarketScreen.java
src/main/java/com/nekros/market/command/MarketCommands.java
src/main/java/com/nekros/market/command/PriceAdminCommands.java
src/main/java/com/nekros/market/network/MarketSystemConfigPayload.java
src/main/java/com/nekros/market/network/ModNetworking.java
src/main/java/com/nekros/market/pricing/system/SystemPriceService.java
src/main/java/com/nekros/market/storage/EconomySavedData.java
src/main/java/com/nekros/market/system/SystemMarketConfig.java
src/main/java/com/nekros/market/system/SystemMarketOffer.java
src/main/java/com/nekros/market/system/SystemMarketService.java
```

新增源码文件：

```text
src/main/java/com/nekros/market/storage/SystemStockSavedData.java
src/main/java/com/nekros/market/system/PriceMode.java
src/main/java/com/nekros/market/system/SystemOfferPricing.java
```

未跟踪资源文件存在于 `src/main/resources`，不要误删：

```text
body.png
frame_1.png
frame_2.png
frame_3.png
seller_box.json
texture.png
```

## 已完成阶段

### 1. 动态价格核心框架

已完成：

```text
PriceSource
PriceConfidence
TradeLevel
PriceProfile
PriceRegistry
PriceResolver
```

作用：

```text
为物品提供基础价格档案
支持 fallback 价格
支持系统商店报价入口
支持后续 recipe / rarity / trade history 扩展
```

注意：

```text
当前不要把所有物品都做成动态池
先保持少量核心物品可验证
```

### 2. 玩家交易历史与价格指数

已完成：

```text
MarketTradeRecord
EconomySavedData
MarketTradeHistoryService
MarketPriceIndexService
```

作用：

```text
玩家市场成交后记录交易历史
可查询某物品历史成交
为动态报价提供历史价格参考
```

### 3. 系统商店动态报价

已完成：

```text
SystemTradeQuote
SystemPriceService
SystemMarketService 接入动态报价
```

当前行为：

```text
系统卖给玩家：使用 quoteSellToPlayer
系统从玩家收购：使用 quoteBuyFromPlayer
FIXED 模式保持固定价
非 FIXED 模式会走动态价格逻辑
```

### 4. 系统商店货架与定价权

已完成：

```text
PriceMode
SystemOfferPricing
SystemMarketOffer 支持新旧两种配置格式
SystemMarketConfig 支持去重、reload、分类重命名
```

旧格式：

```text
id|type|item|unitPrice|category
```

新格式：

```text
id|type|item|category|priceMode|basePrice|multiplier|minPrice|maxPrice|flags
```

已支持的 `priceMode`：

```text
FIXED
AUTO
ANCHOR
MULTIPLIER
BAND
```

重要规则：

```text
旧 5 段格式会解析为 FIXED
同一 type + item 只保留一个货架商品，后写入的覆盖旧的
系统卖给玩家的商品如果 command 没填 category，会自动进入第一个货架
```

### 5. GUI 与网络同步

已完成：

```text
MarketSystemConfigPayload 增加 fallbackOffers
ModNetworking 下发动态报价后的 offers
客户端 MarketScreen 使用服务端同步配置显示系统商品
admin 面板文案改为 Fallback Price
动态价格显示已接入 GUI
```

已修复：

```text
刚打开 system sell 页面价格显示 0
reload 后商品不立刻出现，需要交易一次才刷新
同一物品出现多个价格
GUI 内买卖后玩家背包不实时刷新
```

背包实时刷新修复点：

```text
ModNetworking.syncInventory
发送 ClientboundContainerSetSlotPacket(-2, 0, slot, stack)
```

### 6. 命令系统

已完成命令：

```text
/market price hand
/market price item <item>
/market price history hand
/market price history item <item>
/market price reload
/market system add <id> <type> <item> <price> [category]
/market system remove <id>
/market system reload
/market system stock <item>
```

已完成：

```text
所有涉及 item 参数的相关命令已改成 ItemArgument
现在能像 /give 一样输入 minecraft:iron_ingot 这种补全
NeksMarket 注册命令时已传入 buildContext
```

### 7. 系统库存雏形

已完成：

```text
SystemStockSavedData
Config.SYSTEM_STOCK_BUY_PRICE_IMPACT_PER_ITEM
Config.SYSTEM_STOCK_MIN_BUY_PRICE_RATIO
SystemMarketService 记录系统买入 / 卖出数量
SystemPriceService.quoteBuyFromPlayer 根据 actualStock 降低收购价
/market system stock <item>
```

当前规则：

```text
玩家卖给系统后，系统 actualStock 增加
玩家从系统购买后，系统 actualStock 减少
系统收购物品时，库存越多，非 FIXED 收购价越低
FIXED 不受库存影响
```

## 已通过的用户测试

用户已经确认：

```text
现阶段功能测试都通过
系统商店能正常添加系统出售给玩家的商品
多个价格问题已修复
首次打开系统商店 0 元问题已修复
GUI 内交易后背包实时刷新已修复
item 参数补全需求已完成
config 中铁锭多条算法/报价的重复问题已处理
```

最近修复的问题：

```text
无法向系统商店添加向玩家出售的物品
```

原因：

```text
/market system add ... sell_to_player ... 如果不填 category，会保存空分类
Buy 页按货架分类过滤，空分类商品不可见
```

修复：

```text
SystemMarketConfig.addOffer 中 normalizeCategory
sell_to_player / system_sells 未填 category 时自动放入第一个 buy category
成功提示会显示 in category ...
```

## 当前配置注意事项

运行配置文件通常在：

```text
run/config/neksmarket-common.toml
```

现有 `systemMarket.offers` 可能包含类似：

```text
buy_cat1_minecraft_acacia_fence|sell_to_player|minecraft:acacia_fence|12|...
sell_minecraft_andesite_stairs|buy_from_player|minecraft:andesite_stairs|22|
sell_iron_multi|buy_from_player|minecraft:iron_ingot|#1|MULTIPLIER|0|2.0|0|0|
```

注意：

```text
buyCategories 里如果出现中文乱码，可能是控制台编码显示问题，不一定是逻辑错误
旧格式和新格式会共存
重复 item + type 会被去重，后者覆盖前者
```

## 下一阶段建议

### 下一步 1：完善系统库存机制

当前库存只影响“系统从玩家收购”的价格。下一步建议做：

```text
系统向玩家出售时，也根据 actualStock 做可售数量限制
actualStock <= 0 时，非无限库存商品不可购买
支持 offer flags，例如 infinite_stock / limited_stock
GUI 显示库存状态
购买按钮在库存不足时禁用或返回明确提示
```

建议实现点：

```text
SystemOfferPricing.flags()
新增 helper 判断 hasFlag("infinite_stock")
SystemMarketService.trade 购买分支检查库存
SystemPriceService.quoteSellToPlayer 可根据低库存提高售价
ModNetworking.quoteOffer / payload 增加库存展示字段，或先只做服务端限制
MarketScreen 商品卡片显示 Stock: n / Infinite
```

测试方案应包含：

```text
先让系统库存为 0，尝试从系统购买非 infinite 商品，应失败
玩家先卖给系统 10 个铁锭，再从系统买 3 个，应成功且库存减少
infinite_stock 商品无视库存限制
```

### 下一步 2：补充系统商店高级添加命令

当前 `/market system add` 只写旧 5 段固定价格式。建议新增命令：

```text
/market system addpriced <id> <type> <item> <category> <mode> <basePrice> <multiplier> <minPrice> <maxPrice> [flags]
```

作用：

```text
允许管理员直接添加 AUTO / ANCHOR / MULTIPLIER / BAND 商品
避免手改 config
```

注意：

```text
item 参数继续用 ItemArgument
mode 建议用 enum suggestions
flags 可先用 StringArgumentType.word
sell_to_player category 为空时继续默认第一个货架
```

测试方案应包含：

```text
添加 MULTIPLIER 收购商品
添加 AUTO 系统出售商品
reload 后 GUI 立即显示
config 中只保留该 item + type 的一条
```

### 下一步 3：完善价格查询和调试输出

建议增强：

```text
/market price item <item> 显示 priceMode 来源
/market system stock <item> 显示库存影响后的收购价
添加 /market system quote <type> <item> [count]
```

目的：

```text
让用户不用打开 GUI 就能验证动态价格
方便排查 0 元、重复价格、库存影响异常
```

### 下一步 4：把配置编码和分类显示理顺

用户当前看到过 buyCategories 中文乱码。建议检查：

```text
run/config/neksmarket-common.toml 的实际编码
IDEA 控制台编码
NightConfig 保存中文时的行为
```

可选改法：

```text
内部 category id 使用 #1/#2/#3
GUI displayName 单独配置中文名
避免用中文字符串作为逻辑匹配 key
```

这个不是当前功能 blocker，但后续做货架 UI 时值得处理。

### 下一步 5：阶段性清理与提交

建议在下一阶段功能稳定后做一次提交。提交前：

```powershell
.\gradlew.bat build
git status --short
```

不要误删未跟踪资源文件。提交信息建议：

```text
Implement dynamic system market pricing and stock tracking
```

## 关键文件阅读顺序

下一个 Codex 接手后建议按这个顺序读：

```text
src/main/java/com/nekros/market/system/SystemMarketOffer.java
src/main/java/com/nekros/market/system/SystemOfferPricing.java
src/main/java/com/nekros/market/system/PriceMode.java
src/main/java/com/nekros/market/pricing/system/SystemPriceService.java
src/main/java/com/nekros/market/system/SystemMarketService.java
src/main/java/com/nekros/market/system/SystemMarketConfig.java
src/main/java/com/nekros/market/network/ModNetworking.java
src/main/java/com/nekros/market/client/MarketScreen.java
src/main/java/com/nekros/market/command/MarketCommands.java
src/main/java/com/nekros/market/storage/SystemStockSavedData.java
```

## 常用测试命令

```mcfunction
/market price hand
/market price item minecraft:iron_ingot
/market price history item minecraft:iron_ingot
/market system add test_iron_buy sell_to_player minecraft:iron_ingot 100
/market system add test_diamond_buy sell_to_player minecraft:diamond 500 #2
/market system add test_iron_sell buy_from_player minecraft:iron_ingot 80
/market system reload
/market system stock minecraft:iron_ingot
```

GUI 测试路径：

```text
/market
切到系统商店 Buy 页
切换货架分类
测试系统出售给玩家
切到 Sell 页
测试玩家卖给系统
保持 GUI 打开时观察热键栏数量是否实时变化
```

## 接手时不要做的事

```text
不要 git reset --hard
不要 git checkout -- 回滚文件
不要重写整个 MarketScreen
不要把 EconomySavedData 合并进 MarketSavedData
不要删除旧 5 段 offer 格式兼容
不要删除 src/main/resources 下的未跟踪资源文件
```

## 交接结论

当前阶段可以认为“动态经济基础 + 系统商店动态报价 + 货架定价权 + 库存雏形”已经跑通。下一阶段最自然的目标是把 `SystemStockSavedData` 从“影响收购价”扩展成真正的系统库存：限制系统出售数量、支持无限库存 flag、在 GUI 显示库存，并补充一个能添加高级 priceMode 的管理员命令。
