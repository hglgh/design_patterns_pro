# 策略模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么把每种算法变成独立对象」，
> 以及什么时候一个 `if/else` 反而更清楚。
>
> 第二层：[README.md](README.md)（折扣策略、策略工厂、价格计算器、用户等级）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**策略模式就是把「同一件事的几种做法」各自装进一个对象，用的时候挑一个；**
**收银台只负责收钱，不负责记住所有折扣规则。**

### 什么需求会让开发者想到它

超市收银台的应付金额，取决于当天活动：

- 平时：原价；
- 周末：九折；
- 会员日：八折，但单笔最多优惠 200 元；
- 满减券：满 100 减 20，满 300 减 80；
- 新人：立减 15。

收银流程完全一样：扫码、算钱、收款。**变的只是「怎么算」。**

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldCashier {

    int total(int amount, String type) {
        if ("normal".equals(type)) {
            return amount;
        }
        if ("weekend".equals(type)) {
            return amount * 90 / 100;
        }
        if ("member".equals(type)) {
            int payable = amount * 80 / 100;
            int saved = amount - payable;
            if (saved > 200) {
                payable = amount - 200;
            }
            return payable;
        }
        if ("coupon".equals(type)) {
            // 满 300 减 80，满 100 减 20
            // ...
        }
        return amount;             // 未知类型：静默按原价
    }
}
```

真实输出（节选）：

```text
100 元：normal=100，weekend=90，member=80，coupon=80
300 元：normal=300，weekend=270，member=240，coupon=220
1000 元：normal=1000，weekend=900，member=800，coupon=920
2000 元：normal=2000，weekend=1800，member=1800，coupon=1920
未知类型「member 」（末尾多个空格）：1000 元
```

`member` 多一个空格，**不报错，直接按原价收钱**。
顾客少付了还是多付了，取决于字符串有没有拼对。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldCashier.total() ──知道──▶ 所有折扣算法
                   ──知道──▶ 每个算法的参数和边界
                   ──知道──▶ type 字符串的取值
```

`OldCashier` 同时是「收银入口」和「折扣规则库」。
每加一种活动，这个类都要改，而且改的是最核心的算钱方法。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 新增「新人立减 15」 | `total()` 里再加一个 `if` | 改核心方法、重新回归所有金额 |
| 会员封顶从 200 改成 300 | 改方法里的字面量 | 规则不可配置 |
| 不同门店用不同活动 | 再加参数/分支，或复制方法 | 组合越来越多 |
| 单独测试「会员八折」 | 只能通过 `total()` 间接测 | 测试要准备无关分支 |
| 运行时切换活动 | 只能改传进来的字符串 | 拼写错误静默失败 |

### 3.3 为什么加一个 `if` 不够

新增活动不是「多一种金额」，而是**多一个完整的算法**，它有自己的参数、边界和测试用例。
把算法继续塞进 `total()`，方法会变成规则仓库；
而「选哪个算法」和「算法怎么算」本来是两件事，应该分开。

---

## 4. 从问题推导出设计

### 第一步：把「怎么算」抽成一个接口

```java
interface DiscountStrategy {
    int payable(int amount);
    String name();
}
```

- **为什么需要它？** 收银台要能统一调用「算钱」，不关心具体公式。
- **为什么不直接传一个方法引用？** 可以，Java 的 `IntUnaryOperator` 就能做；
  但策略需要 `name()` 用于展示/日志，用接口更直观。
- **不加接口会怎样？** 收银台还是得知道每个算法类，调用处继续 `if`。

### 第二步：每种折扣一个类

```java
class MemberDiscount implements DiscountStrategy {
    private static final int MAX_SAVING = 200;

    public int payable(int amount) {
        int payable = amount * 80 / 100;
        int saved = amount - payable;
        return saved > MAX_SAVING ? amount - MAX_SAVING : payable;
    }

    public String name() {
        return "会员八折";
    }
}
```

`NormalPrice`、`WeekendDiscount`、`CouponDiscount`、`NewcomerDiscount` 同理。
每个类只回答「这种活动怎么算」，可以单独测试、单独修改。

### 第三步：让收银台持有策略

```java
class Cashier {
    private DiscountStrategy strategy;

    Cashier(DiscountStrategy strategy) {
        this.strategy = strategy;
    }

    void setStrategy(DiscountStrategy strategy) {
        this.strategy = strategy;
    }

    int checkout(int amount) {
        return strategy.payable(amount);
    }
}
```

- **为什么收银台要持有策略？** 它给调用方一个稳定入口，并允许运行时切换。
- **为什么用组合而不是继承？** 继承会把「收银台」和「某种折扣」绑死，
  想换折扣就要换对象；组合可以在同一个收银台上 `setStrategy`。
- **如果收银台只有一行转发，是否多余？** 如果调用方本来就只算一次，
  可以直接 `new MemberDiscount().payable(amount)`；`Cashier` 适合需要保存订单、
  记录流水、统一入口的场景。

### 最终对象关系

```text
Cashier（Context）
  │ 持有 DiscountStrategy
  │ checkout(amount) → strategy.payable(amount)
  ▼
DiscountStrategy（接口）
  ▲          ▲           ▲            ▲
Normal   Weekend      Member      Coupon / Newcomer
```

---

## 5. 用具体数据走一遍

### 5.1 四种金额，五种策略

```java
DiscountStrategy[] strategies = {
        new NormalPrice(),
        new WeekendDiscount(),
        new MemberDiscount(),
        new CouponDiscount(),
        new NewcomerDiscount()
};

for (int amount : amounts) {
    for (DiscountStrategy strategy : strategies) {
        strategy.payable(amount);
    }
}
```

真实输出：

```text
100 元：原价=100  九折=90  会员八折=80  满减=80  新人立减=85
300 元：原价=300  九折=270  会员八折=240  满减=220  新人立减=285
1000 元：原价=1000  九折=900  会员八折=800  满减=920  新人立减=985
2000 元：原价=2000  九折=1800  会员八折=1800  满减=1920  新人立减=1985
```

### 5.2 会员封顶怎么算

以 2000 元为例：

```text
① payable = 2000 * 80 / 100 = 1600（整数除法）
② saved = 2000 - 1600 = 400
③ 400 > 200 → payable = 2000 - 200 = 1800
```

1000 元时 `saved = 200`，不超过上限，所以应付 800。
**边界是「大于 200」才封顶，等于 200 时不封顶**，输出里 1000 元是 800。

### 5.3 满减的门槛

```text
100 元：>=100 → 减 20 → 80
300 元：>=300 → 减 80 → 220
1000 元：>=300 → 减 80 → 920
```

两个门槛是「从上往下」判断的，顺序不能反：
如果先判断 `>=100`，300 元只会减 20。

### 5.4 最容易写错的三处

1. **整数除法**：`amount * 80 / 100` 对 99 元得到 79（而不是 79.2）。
   货币计算最好用 `BigDecimal` 或「分」为单位，入门示例为了简短用了 `int`。
2. **封顶判断用 `>` 还是 `>=`**：业务必须明确「刚好 200 元优惠」算不算封顶。
3. **未知类型静默返回原价**：这是改造前最危险的地方。
   策略版没有 `type` 字符串，选错策略会直接表现为「用了另一个类的算法」，
   至少不会因为多一个空格而静默变原价。

---

## 6. 改造前后对比

### 新增「新人立减 15」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `OldCashier` | 新增 `NewcomerDiscount` |
| 要改的旧代码 | `total()` 里加一个 `if` | 无 |
| 测试 | 通过 `total()` 间接测 | 直接测 `NewcomerDiscount.payable()` |
| 运行时切换 | 改字符串，拼错静默失败 | `setStrategy(new NewcomerDiscount())` |

### 但调用方仍然要改

- 要**选择**用哪个策略（根据活动、用户等级、配置）；
- 新策略要能被创建和传入；
- 多个策略叠加（会员 + 满减）不是策略模式自动解决的，
  需要组合策略/责任链/规则引擎，且要定义优先级。

### 引入模式新增的成本

- 每种算法一个类，规则很少时显得啰嗦；
- 策略对象可能无状态可共享，也可能带配置，生命周期要管理；
- 调用方多了一步「选策略」，选择逻辑本身也可能变复杂；
- 如果算法需要大量上下文，接口参数会变长，或需要引入 Context 对象。

---

## 7. 适用边界

### 适合用

- 同一件事有**多种可互换的算法**（计价、压缩、排序、路由、限流）；
- 需要在**运行时**根据配置/用户/环境切换；
- 算法各自有边界和测试用例，希望独立维护；
- 想避免一个方法里堆满 `if/else` 或 `switch`。

### 不适合用

- 只有两三种固定算法，永远不变——一个方法更短；
- 算法之间需要共享大量中间状态，拆开反而更复杂；
- 选择逻辑本身极复杂（多规则叠加、优先级、互斥）——规则引擎/责任链更合适。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 状态 | 状态由**对象内部流转**决定，状态会自己切换；策略由**外部选择**，策略之间通常不互相切换 |
| 模板方法 | 模板方法用继承固定流程，子类填步骤；策略用组合替换整个算法 |
| 工厂方法 | 工厂关注「创建哪个实现」；策略关注「运行时用哪个算法」 |

---

## 8. 小练习

### 练习 1（场景判断）

物流系统要计算运费：普通快递、次日达、冷链，每种计费公式不同，用户在下单时选择。
要不要用策略模式？

**答案**：适合。三种运费算法互斥、可替换，用户选择发生在运行时；
新增「同城配送」只加一个策略类。但「选择哪种配送」还要考虑地址、温层、时效，
这些是选择逻辑，不是策略本身，应放在下单服务/配置里。

### 练习 2（解释设计原因）

`Cashier` 只有一行 `strategy.payable(amount)`，为什么还要它？
直接让客户端调用策略不行吗？

**答案**：可以直接调用，尤其只算一次时。`Cashier` 的价值在于：
它是收银的稳定入口，可以持有订单、记录流水、做统一校验，
并在同一个对象上 `setStrategy` 切换活动。
如果这些都还没有，`Cashier` 确实可以省掉——**不要为了模式而模式**。

### 练习 3（执行过程）

会员八折、封顶 200。原价 1000 和 2000 分别应付多少？
如果把封顶判断改成 `saved >= 200`，1000 元的结果会变吗？

**答案**：1000 → 800（saved=200，不封顶）；2000 → 1800（saved=400，封顶）。
改成 `>=` 后，1000 元 saved=200 也命中封顶，`payable = 1000 - 200 = 800`，
结果仍然是 800。这个例子刚好不变，但在 1001 元时会有差异——
边界条件必须用具体数字验证，不能只看「差不多」。

### 练习 4（扩展推导）

新增「满 500 减 100」，并且允许和会员八折叠加。只加一个策略类够吗？

**答案**：不够。「叠加」不是单一策略能表达的，需要：
① 新增 `Full500Minus100` 策略；
② 定义一个组合策略（先会员再满减，或反之）；
③ 明确优先级、是否可叠加、封顶怎么算。
这说明策略模式解决「选一个算法」，**多个算法组合**需要额外设计。

---

## 读完后应能回答的 5 个问题

1. 策略模式把「算法」和「使用算法的对象」怎么分开？
2. 为什么用组合/持有策略，而不是为每种折扣继承一个收银台？
3. 新增一种折扣，改造前后各要改哪里？
4. 策略模式和状态模式都靠多态，谁负责切换实现？
5. 多个策略叠加时，为什么不能只靠策略模式？

---

## 下一步

- 深度文档：[README.md](README.md)（折扣策略、策略工厂、价格计算器）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/orderservice](../../combined/orderservice/)（订单计价）
