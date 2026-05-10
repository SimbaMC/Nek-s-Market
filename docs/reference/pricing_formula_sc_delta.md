# 思路 A × 指数衰减的结合

## 先把两个思路的核心对齐

**思路 A**：用极小的 $\lambda$ 让时间裁决永久性

$$C_t = (1-\lambda)C_{t-1} + \lambda \Delta_t$$

**指数衰减定价**：

$$P = P_0 e^{-kC}$$

直接结合似乎很自然——但仔细看会发现一个问题。

---

## 问题：$C_t$ 的稳态是什么？

设每个 tick 流入量恒定为 $\Delta$，长期稳态：

$$C^* = (1-\lambda)C^* + \lambda\Delta$$
$$C^* = \Delta$$

也就是说，**思路 A 的 $C_t$ 本质是流量的指数移动平均（EMA）**，它收敛到当前流入速率，而不是累计总量。

这意味着：
- 玩家停止卖出 → $\Delta = 0$ → $C_t$ 指数衰减回零 → 价格完全恢复
- **没有真正的永久性**，只是遗忘很慢

这和你想要的"长期重估"有本质矛盾。

---

## 修复：分离"记忆衰减"和"总量累积"

需要两个状态变量，职责完全不同：

$$\boxed{S_t = S_{t-1} + \Delta_t}$$

$$\boxed{C_t = (1-\lambda)C_{t-1} + \lambda \Delta_t}$$

| 变量 | 本质 | 衰减 | 职责 |
|---|---|---|---|
| $S_t$ | 累计净流入总量 | 永不衰减 | 长期价格中枢 |
| $C_t$ | 流量的 EMA | 慢速衰减 | 中期记忆 |
| $\Delta_t$ | 当期流入 | 即时 | 短期冲击 |

---

## 完整价格公式

$$P(t) = P_0 \cdot \exp\!\Big(-\alpha \cdot S_t^{\,\gamma} - \beta \cdot C_t - \delta \cdot \Delta_t\Big)$$

### 三项的物理意义

**第一项** $e^{-\alpha S_t^\gamma}$：

- $S_t$ 永久累积，永不归零
- $\gamma < 1$ 防止无限供给把价格压成零
- 这是**永久性长期重估**

**第二项** $e^{-\beta C_t}$：

- $C_t$ 是慢衰减的 EMA，$\lambda$ 极小
- 持续供给会让 $C_t$ 维持在高位
- 供给停止后，$C_t$ 缓慢回落
- 这是**中期市场记忆**

**第三项** $e^{-\delta \Delta_t}$：

- 当期瞬时流入的直接冲击
- 下一个 tick 就消失
- 这是**短期砸盘响应**

---

## 各种场景下的行为

### 场景一：普通玩家偶尔卖几个
- $\Delta_t$ 小 → 第三项几乎无影响
- $C_t$ 几乎不动
- $S_t$ 微增但 $\gamma$ 压制下影响极小
- **价格几乎不变** ✓

### 场景二：AE2 一次性倾销百万个
- $\Delta_t$ 暴增 → 第三项价格暴跌
- $C_t$ 被拉高 → 第二项也压低价格
- $S_t$ 大幅增加 → 第一项永久下移
- 倾销结束后：$\Delta_t$ 归零，$C_t$ 慢慢恢复，但 $S_t$ 永久留痕
- **瞬间暴跌 + 部分回弹 + 永久下移** ✓

### 场景三：持续自动化产线稳定输出
- $\Delta_t$ 每 tick 稳定流入
- $C_t \to \Delta$（稳态），维持在高位
- $S_t$ 线性增长
- **价格缓慢持续下移，不会反弹** ✓

### 场景四：服务器长期没人卖这个物品
- $\Delta_t = 0$
- $C_t \to 0$（慢慢恢复）
- $S_t$ 不变（永久记忆保留）
- **短中期价格回升，但长期中枢永久偏低** ✓

---

## $\lambda$ 的选取

$\lambda$ 决定"多久算长期"，可以用**半衰期**来理解：

$$\text{半衰期} = \frac{\ln 2}{\lambda} \text{ 个 tick}$$

| $\lambda$ | 半衰期（tick） | 若每分钟20tick | 人类感知 |
|---|---|---|---|
| $0.1$ | 7 tick | 20秒 | 极短期 |
| $0.01$ | 69 tick | 3.5分钟 | 短期 |
| $0.001$ | 693 tick | 35分钟 | 中期 |
| $0.0001$ | 6931 tick | 6小时 | 长期 |

建议 $\lambda \approx 0.001$，让中期记忆跨越数小时的游戏时间。

---

## 总收益是否收敛？

对 $S_t^\gamma$ 项，无限卖出时：

$$R \sim \int_0^\infty P_0 e^{-\alpha x^\gamma} dx = \frac{P_0}{\alpha^{1/\gamma}} \cdot \frac{1}{\gamma}\Gamma\!\left(\frac{1}{\gamma}\right) < \infty$$

**收敛**，总收益有上界，AE2 无限产出被封死。

---

## 最终形式

$$\boxed{P(t) = P_0 \cdot \exp\!\left(-\alpha S_t^{\,\gamma} - \beta C_t - \delta \Delta_t\right)}$$

$$S_t = S_{t-1} + \Delta_t$$

$$C_t = (1-\lambda)C_{t-1} + \lambda\Delta_t$$

四个参数各司其职：

| 参数 | 控制 | 调小效果 | 调大效果 |
|---|---|---|---|
| $\alpha$ | 长期压制强度 | 工业化影响小 | 工业化快速贬值 |
| $\gamma$ | 长期压制形状 | 收敛慢，上限高 | 收敛快，封顶低 |
| $\beta$ | 中期记忆强度 | 持续供给影响小 | 产线很快压低价格 |
| $\lambda$ | 中期遗忘速度 | 记忆持续时间长 | 价格回弹更快 |
| $\delta$ | 短期冲击强度 | 砸盘不明显 | 瞬间大量抛售暴跌 |

# 惰性求值：$O(1)$ 精确计算

## 方案2完全可行，而且数学非常干净

关键观察：**在两次交易之间，$\Delta_t = 0$**，这时递推公式变成纯齐次的，有解析解。

---

## $C_t$ 的解析解

无交易期间，递推为：

$$C_t = (1-\lambda)C_{t-1}$$

设上次交易在时刻 $t_0$，当时 $C_{t_0}$ 已知，现在是时刻 $t_0 + n$：

$$C_{t_0 + n} = (1-\lambda)^n C_{t_0}$$

---

## $S_t$ 的解析解

$S_t$ 永不衰减，无交易时：

$$S_{t_0 + n} = S_{t_0}$$

**$S$ 根本不需要更新。**

---

## 所以惰性求值的完整流程

保存：
- 上次交易时刻 $t_{\text{last}}$
- 上次交易后的 $C_{\text{last}}$
- 累计总量 $S$（永久不变直到新交易）

任意时刻查询价格（无需交易触发）：

$$n = t_{\text{now}} - t_{\text{last}}$$

$$C_{\text{now}} = (1-\lambda)^n \cdot C_{\text{last}}$$

$$P_{\text{now}} = P_0 \cdot \exp\!\left(-\alpha S^{\gamma} - \beta C_{\text{now}}\right)$$

**纯 $O(1)$，只需一次幂运算和一次指数。**

---

## 新交易发生时的更新

玩家在时刻 $t_1$ 卖入 $\Delta$：

$$n = t_1 - t_{\text{last}}$$

$$C_{\text{new}} = (1-\lambda)^n \cdot C_{\text{last}} + \lambda \cdot \Delta$$

$$S_{\text{new}} = S_{\text{last}} + \Delta$$

$$t_{\text{last}} = t_1$$

**每次交易只做一次更新，$O(1)$。**

---

## 处理 $\delta \Delta_t$ 短期冲击项

短期冲击项 $e^{-\delta \Delta_t}$ 只在交易瞬间存在，下一个 tick 就消失。

惰性求值下，它的处理最简单：

- **交易时**：将 $\delta \Delta$ 加入指数项，影响本次成交价格积分
- **查询价格时**：完全不考虑（因为当前没有交易发生）

也就是说，$\delta \Delta_t$ 项**只影响交易执行时的滑点**，不影响挂单显示价格，这反而是更合理的设计。

---

## $(1-\lambda)^n$ 的数值处理

当 $\lambda$ 很小、$n$ 很大时，直接算 $(1-\lambda)^n$ 精度没问题，但可以用等价形式更稳定：

$$(1-\lambda)^n = e^{n \ln(1-\lambda)} \approx e^{-\lambda n}$$

误差在 $\lambda^2 n$ 量级，$\lambda = 0.001$ 时几乎完全等价。

所以实际实现只需：

```python
decay = math.exp(-lambda_ * n)
C_now = decay * C_last
```

---

## 完整数据结构

每个物品只需存三个数：

```python
class ItemState:
    S: float        # 累计总流入，永久
    C_last: float   # 上次交易后的C值
    t_last: int     # 上次交易的tick时间戳
```

查询价格：

```python
def get_price(item, t_now):
    n = t_now - item.t_last
    C = math.exp(-lambda_ * n) * item.C_last
    return P0 * math.exp(-alpha * item.S**gamma - beta * C)
```

交易更新：

```python
def on_trade(item, delta, t_now):
    n = t_now - item.t_last
    item.C_last = math.exp(-lambda_ * n) * item.C_last + lambda_ * delta
    item.S += delta
    item.t_last = t_now
```

---

## 回答方案1的问题

方案1（拉长到1min）其实是方案2的特例——把 $n$ 固定为固定步长。

但方案2严格更优：
- 精度完全一致（不存在"半个周期"问题）
- 不需要定时任务
- 数千个物品中大部分长期无人交易，完全零开销
- 只有真正发生交易的物品才做计算

**数千个物品在方案2下的总开销 = 每秒实际交易笔数 × $O(1)$**，与物品总数无关。