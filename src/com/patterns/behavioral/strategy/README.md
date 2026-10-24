# 策略模式（Strategy Pattern）

> **一句话**：把「同一件事的多种做法」各自封装成独立对象，让它们可以互相替换，
> 于是新增一种做法时**不用改任何已有代码**。
>
> **本目录代码已用 JDK 25 实际编译运行验证**，文中所有输出、所有数字都是真实运行结果，不是编造的。

---

## 目录

1. [现实案例：超市收银台](#1-现实案例超市收银台)
2. [原始实现：不用策略模式会怎样](#2-原始实现不用策略模式会怎样)
3. [设计动机：为什么必须换一种写法](#3-设计动机为什么必须换一种写法)
4. [设计推导：从 if-else 一步步推出策略模式](#4-设计推导从-if-else-一步步推出策略模式)
5. [代码解析：每个类为什么存在](#5-代码解析每个类为什么存在)
6. [执行流程：从 main() 到输出的完整路径](#6-执行流程从-main-到输出的完整路径)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试题与自测](#9-面试题与自测)
- [附录：文件清单与运行方式](#附录文件清单与运行方式)

---

## 1. 现实案例：超市收银台

### 说人话版

你去超市买东西，收银员扫完码，屏幕上出现一个「应付金额」。

这个金额是怎么来的？取决于当天有什么活动：

- **平时**：扫码价直接相加，一分不少。
- **周末大促**：全场九折。
- **会员日**：刷会员卡打八折，但单笔最多优惠 200 元（超市怕有人一次性买十万块的茅台，八折就是白送两万）。
- **满减券**：满 100 减 20，满 300 减 80。
- **新人首单**：立减 15 元。

关键在于——**收银员的操作流程是完全一样的**：

```
扫码 → 系统算出应付金额 → 收钱 → 打小票
```

变的只有中间那一步「系统怎么算」。收银员不需要知道今天是九折还是满减，
他只管点一下「结算」按钮，屏幕给出一个数字。

**如果超市每换一种活动就要重新培训收银员、重新改造收银机，这家超市早就倒闭了。**

收银机的设计是：把「怎么算钱」做成一个**可插拔的模块**，活动变了就换一个模块插上去，
收银机本身一行代码都不用改。

这个「可插拔的算钱模块」，就是策略模式里的**策略（Strategy）**。
收银机就是**上下文（Context）**。

### 专业版

策略模式定义了一系列算法，将每个算法封装起来，并使它们可以相互替换。
本模式使得算法的变化独立于使用算法的客户。

对应到上面的例子：

| 现实 | 模式角色 | 本项目类 |
|---|---|---|
| 「怎么算钱」这个抽象职责 | 抽象策略 Strategy | `DiscountStrategy` |
| 九折 / 满减 / VIP 八折 …… | 具体策略 ConcreteStrategy | `PercentageDiscountStrategy` 等 |
| 收银机（持有并调用算法） | 上下文 Context | `PriceCalculator` |
| 收银员按下结算键 | 客户端调用 | `Client.calculatePrice(...)` |
| 「今天用哪套活动规则」的决定者 | （GoF 未定义，工程必备） | `DiscountStrategyFactory` |

> **注意最后一行**：GoF 原书**没有**规定「谁来选策略」，
> 但真实系统里这个问题必然存在。很多教程（包括原项目）到这里就停了，
> 结果 if-else 只是从「算法实现」搬到了「算法选择」，模式只成功了一半。
> 本项目专门补了一个工厂类来解决它，详见 [第 5.8 节](#58-discountstrategyfactoryjava谁来选策略)。

---

## 2. 原始实现：不用策略模式会怎样

### 2.1 原项目的写法（真实源码）

`e:\codeFile\codeFather\design_patterns\src\BehaviorType\StrategyPattern\` 下的原始实现：

```java
// DiscountStrategy.java（原项目）
public interface DiscountStrategy {
    double calculate(double originalPrice);      // ← 用 double 算钱
}

// FullReductionStrategy.java（原项目）
public class FullReductionStrategy implements DiscountStrategy {
    @Override
    public double calculate(double originalPrice) {
        if (originalPrice >= 100) {              // ← 门槛硬编码
            return originalPrice - 20;           // ← 减免额硬编码
        }
        return originalPrice;
    }
}

// PriceCalculator.java（原项目）
public class PriceCalculator {
    private DiscountStrategy strategy;           // ← 没有 final，可能为 null

    public void setStrategy(DiscountStrategy strategy) {   // ← setter 注入，两步初始化
        this.strategy = strategy;
    }

    public double calculatePrice(double originalPrice) {
        if (strategy == null) {
            throw new IllegalStateException("未设置折扣策略");   // ← 错误暴露得太晚
        }
        return strategy.calculate(originalPrice);
    }
}

// Client.java（原项目）
public class Client {
    public static void main(String[] args) {
        PriceCalculator calculator = new PriceCalculator();   // 此刻对象「已创建但不可用」
        double price = 120.0;

        calculator.setStrategy(new FullReductionStrategy());
        System.out.println("【满减策略】最终价格：" + calculator.calculatePrice(price));

        calculator.setStrategy(new PercentageDiscountStrategy());
        System.out.println("【打折策略】最终价格：" + calculator.calculatePrice(price));
        // ...
    }
}
```

**原项目的类结构本身是对的**——它确实有抽象策略、具体策略、Context，
`setStrategy` + 多态调用也确实是策略模式。这一点必须先说清楚，
**不能因为要重构就说原来全是错的**。

但它有 6 个真实的工程问题：

### 2.2 问题清单（每一条都有实测证据）

#### 问题 ①：用 `double` 算钱 —— 这是最严重的

`double` 是 IEEE 754 二进制浮点数，**无法精确表示大多数十进制小数**。
下面是本项目在 **JDK 25 下实际运行**得到的结果（不是从书上抄的）：

```
      99.9 * 0.7  （七折）           = 69.92999999999999      应为 69.93   [错]
      58.8 * 0.75 （七五折）         = 44.099999999999994     应为 44.10   [错]
      0.29 * 100  （元转分）         = 28.999999999999996     应为 29      [错]
      0.07 * 100  （元转分）         = 7.000000000000001      应为 7       [错]
      3.0 - 2.1   （找零）           = 0.8999999999999999     应为 0.90    [错]
      Math.round(1.005*100)/100.0    = 1.0                    应为 1.01    [错]
```

**最严重的是最后一行**：`1.005` 四舍五入应该是 `1.01`，但 double 里 `1.005` 实际存的是
`1.00499999999999989...`，本来就小于 1.005，所以舍去得到 `1.00`。

> 这不是「精度差一点点」的学术问题，是**真真实实地少收了客户一分钱**。
> 每天一百万笔订单，每笔差一分，一天就是一万元的账目差异，财务对账永远对不平。

**元转分那一行也很危险**：`(long)(0.29 * 100)` 实测等于 `28`，静默丢了 1 分。
这种 bug 不会抛异常、不会打日志，只会在月底对账时炸出来。

**一个诚实的补充**：原项目用的恰好是 `120.0 * 0.9`，
而这个算式实测结果是**精确的** `108.0`（0.9 和 120 的二进制误差恰好相消）。
所以原项目的 Demo 跑起来看不出任何问题——

> **这正是浮点数 bug 最阴险的地方**：测试用例里的那些数字刚好能算对，
> 一上生产遇到真实价格就出错。**不能靠「测了几个数字都对」来证明浮点数安全，必须从类型上根治。**

#### 问题 ②：满减门槛和金额硬编码

`originalPrice >= 100` 和 `- 20` 写死在代码里。
运营想说「双十一改成满 300 减 80」，只能改代码、重新编译、重新发版。

**真实电商里，满减规则是配置在数据库里的**，运营在后台点几下就上线一个新活动。
硬编码版本做不到「同时存在满 100 减 20 和满 300 减 80 两个活动」——
因为一个类只能有一套常量。

重构后：`new FullReductionStrategy(new BigDecimal("300"), new BigDecimal("80"))`，
**同一个类的两个实例**，见 [demo3 的实测输出](#63-demo3遍历所有策略一次看清全部方案)。

#### 问题 ③：setter 注入 → 对象有「已创建但不可用」的中间态

```java
PriceCalculator calculator = new PriceCalculator();   // 编译通过，运行通过
// ↑ 此刻 calculator 是个「半成品」：strategy == null
// 如果这中间有 200 行业务代码，其中某一行调了 calculatePrice()……
calculator.calculatePrice(price);   // 运行时才抛 IllegalStateException
```

**错误暴露的位置离根源很远**。你在 `calculatePrice()` 那一行看到异常，
但真正的问题是 200 行之前忘了 `setStrategy()`。
排查时要沿着调用栈一路往回找，非常痛苦。

重构后改成构造器注入：忘记传策略的代码**根本编译不过**，问题在写代码时就被 IDE 标红。

#### 问题 ④：`strategy == null` 判断散落各处

因为策略可能是 null，所以每个用到策略的地方都要判空。
Context 里判一次，别的地方可能还要判。

**「不打折」本身是一种合法的业务状态，不是一种「缺失」。**
把它表达成 null，就等于用「类型系统之外的特殊值」表达业务概念，
这是 NullPointerException 的万恶之源。

重构后引入 `NoDiscountStrategy.INSTANCE`（**空对象模式 Null Object Pattern**），
「不打折」也是一个正常的策略对象，所有判空代码全部消失。

#### 问题 ⑤：策略名字是调用方硬编码的字符串

```java
calculator.setStrategy(new FullReductionStrategy());
System.out.println("【满减策略】最终价格：" + ...);   // ← 这个「满减策略」是人手写死的
```

这个字符串和策略对象**没有任何关联**。后果：

- 新增一个策略时，调用方要自己编一个名字，编得对不对没人管；
- 同一个策略在不同调用点可能被写成「满减策略」「满减」「FullReduction」三种；
- **想遍历所有策略打印一张对比表，根本拿不到名字**——因为名字不在对象里。

重构后接口增加 `String name()`，名字成为策略的**内在属性**，
于是可以写出这样的通用代码（[demo3 实测](#63-demo3遍历所有策略一次看清全部方案)）：

```java
for (DiscountStrategy s : strategies) {
    System.out.println(s.name() + " → " + s.apply(price));
}
```

`currentPromotions()` 里有几个策略，表就有几行，**这段代码一个字都不用改**。

#### 问题 ⑥：`VipDiscountStrategy` 和 `PercentageDiscountStrategy` 只差一个数字

原项目里：

```java
// PercentageDiscountStrategy
return originalPrice * 0.9;
// VipDiscountStrategy
return originalPrice * 0.8;
```

**这种情况下 VIP 策略确实应该删掉**——它没有任何独立存在的理由，
`new PercentageDiscountStrategy(0.8)` 完全等价。多一个类只是增加阅读负担。

> 这是「过度设计」的典型：**为了凑够模式里的角色数量而制造类**。

但真实业务的 VIP 折扣**不会**这么简单。它通常带着风控规则：

- 「VIP 八折，单笔**最多优惠 200 元**」（防止有人拿 VIP 账号批量套现）；
- 「VIP 折扣**不与满减叠加**」；
- 「VIP 折扣有**每月额度**」。

这些规则 `PercentageDiscountStrategy` 表达不了。
重构后给 `VipDiscountStrategy` 加上了**封顶（cap）**逻辑，它才有了独立存在的价值。

而这个改动的效果**远超预期**——它直接导致了一个精彩的现象，见 [第 6.5 节：答案反转](#65-demo5自动选最优方案)。

### 2.3 如果完全不用策略模式，代码会长什么样

这是最原始的写法，也是策略模式真正要解决的问题：

```java
public class PriceCalculator {

    public double calculatePrice(double price, String type) {
        switch (type) {
            case "FULL_REDUCTION":
                return price >= 100 ? price - 20 : price;
            case "PERCENTAGE":
                return price * 0.9;
            case "VIP":
                return price * 0.8;
            default:
                return price;
        }
    }
}
```

看起来挺干净的？**加三个真实需求试试**：

```java
public double calculatePrice(double price, String type, User user, List<Coupon> coupons) {
    switch (type) {
        case "FULL_REDUCTION":
            // 需求：满减可以和会员折扣叠加，但要先算会员折扣
            double base = user.isVip() ? price * 0.8 : price;
            // 需求：VIP 折扣封顶 200
            if (user.isVip() && price - base > 200) base = price - 200;
            // 需求：满减门槛按品类不同（生鲜不参与）
            if (!containsFresh(coupons) && base >= 100) base -= 20;
            return base;
        case "PERCENTAGE":
            // …… 又是一堆 if
        case "NEW_CUSTOMER":
            // 新人首单，还要判断是否真的是首单
            if (orderDao.countByUser(user.getId()) > 0) return price;
            return price - 15;
        // …… 每加一个活动，这个方法就长 20 行
    }
}
```

这个方法会在半年内长到 500 行。它的每一个问题都是致命的：

| 症状 | 后果 |
|---|---|
| 所有活动的逻辑挤在一个方法里 | 改「满减」时可能碰坏「VIP」，回归测试成本爆炸 |
| 想单独测试「VIP 折扣」，必须构造整个 Context | 单元测试写不动，最后没人写 |
| 新增活动必须**修改**这个方法 | 违反开闭原则，每次上线都是高风险操作 |
| 多个开发同时改这个方法 | Git 冲突天天有 |
| 方法参数越来越多 | `price, type, user, coupons, channel, ...` 签名失控 |

**策略模式的本质，就是把这个 500 行的方法拆成 N 个 20 行的类。**

---

## 3. 设计动机：为什么必须换一种写法

### 3.1 核心矛盾：变化的是「算法」，不变的是「流程」

回到收银台的例子。把整个结账流程拆开看：

```
① 校验商品价格合法          ← 不变
② 【套用某个折扣规则】        ← 变！
③ 把结果保留 2 位小数        ← 不变
④ 计算省了多少钱            ← 不变（永远是 原价 - 折后价）
⑤ 展示给用户                ← 不变
```

只有 ② 在变。**设计模式的第一原则就是「找出变化点，把它隔离出来」**
（GoF 原话：*Identify the aspects of your application that vary and separate them from what stays the same*）。

隔离变化点有两种手段：

- **继承**：把 ② 做成抽象方法，每个活动一个子类 → 编译期绑定，换算法要重新编译；
- **组合**：把 ② 做成一个对象，运行时注入 → 运行期绑定，随时可换。

策略模式选的是**组合**。

### 3.2 为什么继承不行

假设我们用继承来实现：

```java
abstract class PriceCalculator {
    public final double calculatePrice(double price) {   // 模板方法
        requireValid(price);
        double result = doDiscount(price);               // 抽象的「变化点」
        return round(result);
    }
    protected abstract double doDiscount(double price);
}

class FullReductionCalculator extends PriceCalculator { ... }
class VipCalculator extends PriceCalculator { ... }
```

这其实是**模板方法模式**（本项目的下一个模式）。它能用，但有三个硬伤：

**硬伤 1：一个对象一旦创建，算法就固定了。**

```java
PriceCalculator calc = new VipCalculator();
// 用户在结算页改选了「满减券」……
// 做不到！必须扔掉 calc，重新 new 一个 FullReductionCalculator
```

而组合版本：

```java
PriceCalculator calc = new PriceCalculator(vipStrategy);
calc = calc.withStrategy(fullReductionStrategy);   // 换个策略对象即可
```

**硬伤 2：Java 单继承，名额被用掉了。**

`VipCalculator` 已经 `extends PriceCalculator`，
如果将来它还需要继承别的类（比如 `AbstractAuditableCalculator`），就没名额了。
组合没有这个限制——一个类可以持有任意多个策略对象。

**硬伤 3：算法无法在运行时动态选择、无法遍历比较。**

继承版本的四种计算器是四个**不同的类型**，你没法把它们放进一个 List 里同等对待：

```java
// 继承版本：做不到「自动选最优惠的方案」
List<PriceCalculator> all = List.of(new FullReductionCalculator(), new VipCalculator());
// 这个 List 里的元素类型不统一，无法遍历比较
// （其实能编译，因为都是 PriceCalculator 子类，但语义上「计算器」不该被当成「算法」来比较）
```

而策略版本可以：

```java
// 策略版本：一行搞定
DiscountStrategy best = DiscountStrategyFactory.bestOf(promotions, price);
```

**这就是 [demo5 自动选最优方案](#65-demo5自动选最优方案) 能存在的根本原因。**
这个功能在真实电商里叫「智能优惠推荐」，是结算页的核心竞争力。

> **一句话总结**：继承是「我是什么」（is-a），组合是「我用什么」（has-a）。
> 折扣算法不是计算器的**本质属性**，只是它**临时用到的工具**，所以应该用组合。

### 3.3 什么时候**不需要**策略模式

这一点比「怎么用」更重要。**滥用策略模式的代价是类爆炸。**

**不需要用策略模式的场景**：

| 场景 | 原因 |
|---|---|
| 只有一种算法，未来也不会有第二种 | 直接写方法就行，加接口纯属自嗨 |
| 分支只有 2~3 个，且每个分支只有 1~2 行 | `if (a) return x; else return y;` 比两个类清晰得多 |
| 分支逻辑**永远不会**被外部替换或配置化 | 没有变化点，不需要隔离 |
| 各分支之间**共享大量状态和辅助方法** | 这时模板方法模式更合适 |
| 分支的差异只是**参数不同**，逻辑完全一样 | 参数化即可：`new FullReductionStrategy(100, 20)`，不需要两个类 |

**最后一条特别重要**，原项目就踩了这个坑：
`VipDiscountStrategy` 和 `PercentageDiscountStrategy` 逻辑完全一样（都是 `price * rate`），
只差一个数字。**那种情况下正确的做法是删掉 VIP 类，用参数表达差异。**

重构后 VIP 类有了「封顶」这条独立规则，才配得上一个单独的类。
**判断标准很简单：如果两个策略类的 `apply()` 方法体除了常量以外完全相同，它们就应该是同一个类的两个实例。**

**需要用策略模式的信号**：

- ✅ 出现 `switch`/`if-else if` 链条，且分支数量在增长；
- ✅ 分支逻辑需要**独立单元测试**；
- ✅ 产品说「这个规则将来要能配置」；
- ✅ 需要「同时对比多种算法的结果」；
- ✅ 多个不同的类都需要这同一族算法（复用）。

---

## 4. 设计推导：从 if-else 一步步推出策略模式

这一节是全文最重要的部分。**不是「策略模式长这样，我们来写一个」，
而是「遇到这个问题，正常人会怎么一步步想到策略模式」。**

### 第 0 步：起点

需求：算一笔订单的折后价，支持满减、打折、VIP 三种规则。

最直觉的写法：

```java
public BigDecimal calculatePrice(BigDecimal price, String type) {
    if (type.equals("FULL_REDUCTION")) {
        return price.compareTo(new BigDecimal("100")) >= 0
                ? price.subtract(new BigDecimal("20")) : price;
    } else if (type.equals("PERCENTAGE")) {
        return price.multiply(new BigDecimal("0.9"));
    } else if (type.equals("VIP")) {
        return price.multiply(new BigDecimal("0.8"));
    }
    return price;
}
```

能跑。但用 `String` 表示类型很危险：`"PERCENTAGE"` 拼错了编译器不报错，运行时静默返回原价。

### 第 1 步：把 String 换成 enum

```java
public enum DiscountType { FULL_REDUCTION, PERCENTAGE, VIP }

public BigDecimal calculatePrice(BigDecimal price, DiscountType type) {
    return switch (type) {
        case FULL_REDUCTION -> price.compareTo(THRESHOLD) >= 0 ? price.subtract(REDUCTION) : price;
        case PERCENTAGE     -> price.multiply(RATE_90);
        case VIP            -> price.multiply(RATE_80);
    };
}
```

**进步**：拼错会编译报错；switch 表达式对 enum 有穷尽性检查，新增枚举值忘了处理会编译报错。

**仍有的问题**：新增一种折扣，**必须修改这个方法**。
而且这个方法是所有折扣的必经之路，改它就是高风险操作。

### 第 2 步：把每个分支提取成独立的私有方法

```java
public BigDecimal calculatePrice(BigDecimal price, DiscountType type) {
    return switch (type) {
        case FULL_REDUCTION -> fullReduction(price);
        case PERCENTAGE     -> percentage(price);
        case VIP            -> vip(price);
    };
}
private BigDecimal fullReduction(BigDecimal price) { ... }
private BigDecimal percentage(BigDecimal price) { ... }
private BigDecimal vip(BigDecimal price) { ... }
```

**进步**：每个算法可以单独看了，方法体变短了。

**仍有的问题**：`switch` 还在，新增折扣还是要改 `calculatePrice`。
**提取方法只是把大块代码切成小块，没有解决「对修改开放」的问题。**

### 第 3 步：关键一跃 —— 让方法变成对象

现在问一个问题：**这三个私有方法有什么共同点？**

- 输入都是 `BigDecimal price`；
- 输出都是 `BigDecimal`；
- 都是「算折后价」这件事的一种做法。

**它们其实是同一个抽象的三个实例。** 那就把这个抽象定义出来：

```java
public interface DiscountStrategy {
    BigDecimal apply(BigDecimal originalPrice);
}
```

然后把三个方法变成三个类：

```java
public class FullReductionStrategy implements DiscountStrategy {
    @Override public BigDecimal apply(BigDecimal price) { ... }
}
public class PercentageDiscountStrategy implements DiscountStrategy {
    @Override public BigDecimal apply(BigDecimal price) { ... }
}
public class VipDiscountStrategy implements DiscountStrategy {
    @Override public BigDecimal apply(BigDecimal price) { ... }
}
```

**这一步之后，`switch` 就消失了**——不是被藏起来了，是真的不需要了。
因为「选哪个算法」变成了「传哪个对象」：

```java
public BigDecimal calculatePrice(BigDecimal price, DiscountStrategy strategy) {
    return strategy.apply(price);      // 多态调用，不关心具体是哪个策略
}
```

> **这就是策略模式的全部核心。** 剩下的都是工程细节。
>
> 「用多态替代条件分支」——把编译期的 `switch` 变成运行期的对象绑定。

### 第 4 步：把「策略参数」也收进来

现在 `calculatePrice(price, strategy)` 有两个参数，调用方每次都要传策略，很啰嗦。
而且真实的 Context 还持有别的东西（订单号、用户信息）。

于是引入 Context，把策略变成它的**字段**：

```java
public class PriceCalculator {
    private final DiscountStrategy strategy;

    public PriceCalculator(DiscountStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "...");
    }
    public BigDecimal calculatePrice(BigDecimal price) {
        return strategy.apply(price);
    }
}
```

**为什么字段要 `final` + `requireNonNull`？**
因为这样这个字段**永远不可能为 null**，于是 `calculatePrice` 里不需要任何判空代码。
这叫**构造器不变式（constructor invariant）**：
一个对象要么被正确创建，要么根本创建不出来，不存在「已 new 但不可用」的中间态。

### 第 5 步：处理「不打折」—— 空对象模式

问题来了：普通用户没有任何折扣，传什么？

**错误的做法**：传 `null`，然后在 Context 里判空。

```java
public BigDecimal calculatePrice(BigDecimal price) {
    if (strategy == null) return price;      // ← 特殊分支又回来了
    return strategy.apply(price);
}
```

这就是原项目的 `IllegalStateException` 的来源——它选择了抛异常，
但不管是返回原价还是抛异常，**都意味着「无折扣」是一种特殊状态，需要特殊处理**。

**正确的做法**：把「不打折」也做成一个策略。

```java
public final class NoDiscountStrategy implements DiscountStrategy {
    public static final NoDiscountStrategy INSTANCE = new NoDiscountStrategy();
    private NoDiscountStrategy() {}      // 单例，因为它无状态

    @Override public String name() { return "无折扣（原价）"; }
    @Override public BigDecimal apply(BigDecimal price) {
        DiscountStrategy.requireValidPrice(price);
        return DiscountStrategy.normalize(price);
    }
}
```

**这就是空对象模式（Null Object Pattern）**。它的威力在于：

```java
// 用 NoDiscountStrategy 之后，这段代码对 4 个会员等级的处理【完全一致】
for (UserLevel level : UserLevel.values()) {
    DiscountStrategy s = DiscountStrategyFactory.forLevel(level);
    System.out.println(s.name() + " → " + new PriceCalculator(s).calculatePrice(price));
}
// 没有任何 if (level == NORMAL) 特殊分支
```

实测输出（[demo4](#64-demo4谁来选策略工厂)）：

```
      NORMAL   无折扣（原价）                      → ￥350.00
      SILVER   打折（9.5 折，折扣率 0.95）         → ￥332.50
      GOLD     打折（9 折，折扣率 0.90）           → ￥315.00
      VIP      VIP（8 折，最多优惠 200 元）        → ￥280.00
```

> **「把特殊情况变成一般情况」是消除 if-else 最有效的手段之一。**
> 空对象不是策略模式的组成部分，但它和策略模式是天作之合。

### 第 6 步：补上 GoF 没说的那个洞 —— 谁来选策略？

到第 5 步，策略模式在教科书意义上已经完整了。但真实项目会立刻撞上一个问题：

**`DiscountStrategyFactory.forLevel(level)` 这个方法该写在哪？**

如果没有工厂，「用户等级 → 用哪个策略」这条业务规则只能写在客户端：

```java
// 下单接口里
DiscountStrategy s;
if (user.getLevel() == UserLevel.VIP) {
    s = new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200"));
} else if (user.getLevel() == UserLevel.GOLD) {
    s = new PercentageDiscountStrategy(new BigDecimal("0.90"));
} else if (...) { ... }

// 购物车接口里 —— 同样的 if-else 又抄了一遍
// 订单预览接口里 —— 再抄一遍
// 促销活动页里 —— 再抄一遍
```

**这就完全违背了策略模式的初衷。**

> 策略模式消灭的是「**算法实现**」里的 if-else，
> 但如果「**算法选择**」处又长出一片 if-else，而且还被复制了 N 份，
> 那模式只成功了一半。

所以本项目补了一个工厂：

```java
public static DiscountStrategy forLevel(UserLevel level) {
    return switch (level) {
        case NORMAL -> NoDiscountStrategy.INSTANCE;
        case SILVER -> new PercentageDiscountStrategy(level.getRate());
        case GOLD   -> new PercentageDiscountStrategy(level.getRate());
        case VIP    -> new VipDiscountStrategy(level.getRate(), level.getMaxDiscount());
    };
}
```

**注意：这个 switch 并没有违反「消灭 if-else」的目标。** 区别在于：

| | 散落在客户端的 if-else | 集中在工厂的 switch |
|---|---|---|
| 出现次数 | N 份（每个调用点一份） | **1 份** |
| 改规则要动几处 | 全局搜索，改 N 处 | 改 1 处 |
| 客户端认识具体策略类吗 | 认识（要 `new` 它们） | **不认识**（只拿 `DiscountStrategy`） |
| 客户端能越权吗 | 能（自己 `new VipDiscountStrategy`） | **不能**（没有构造入口） |

最后一条是安全问题：**如果没有工厂，普通用户完全可以自己 `new VipDiscountStrategy(0.8)` 给自己打八折。**
工厂把构造入口收起来了，这才叫真正的封装。

### 第 7 步：为什么用 switch 表达式而不是 Map 注册表？

两种写法都能实现工厂，本项目选了 switch 表达式，理由如下：

| | switch 表达式 | Map 注册表 |
|---|---|---|
| 新增 enum 值忘了处理 | **编译报错**（enum switch 要求穷尽） | 运行时才发现（`get` 返回 null） |
| 策略创建需要参数/条件 | 直接写代码，灵活 | 只能存固定实例 |
| 规则数量很大（几十上百） | switch 会很长 | 更清晰 |
| 规则来自数据库/配置 | 做不到 | 天然适合 |

本例只有 4 个等级，且 VIP 需要额外参数（封顶金额），所以用 switch。
**「编译器帮我检查有没有漏」这一点在教学和小型系统里价值极高。**

另外，switch 表达式**没有写 `default` 分支**——正因为穷尽了所有 enum 值，
所以「忘记处理新等级」不会被 `default` 悄悄掩盖掉。这是刻意的设计。

**Spring 项目里怎么写？** 让所有策略类加 `@Component`，然后注入 `Map<String, DiscountStrategy>`：

```java
@Service
public class DiscountStrategyFactory {
    private final Map<String, DiscountStrategy> registry;   // Spring 自动注入所有实现
    public DiscountStrategyFactory(Map<String, DiscountStrategy> registry) {
        this.registry = registry;
    }
}
```

新增策略只需加一个 `@Component` 类，工厂代码**一行都不用改**。
本项目不引入 Spring，用 switch 表达同样的意图。

### 第 8 步：Java 8 之后，还需要写这么多类吗？

`DiscountStrategy` 有两个抽象方法（`name()` 和 `apply()`），**不是函数式接口**，不能直接用 lambda。

这是一个真实的取舍：

- 只留 `apply()` → 可以写 `price -> price.multiply(RATE)`，极简，但日志里不知道是哪个策略；
- 加上 `name()` → 可观测性大幅提升，但必须写具名类。

本项目选择后者，因为「能在日志和界面上说清楚用了哪种折扣」在电商场景里是硬需求。

但每个新活动都写一个类文件太重了。折中方案是 **`SimpleDiscountStrategy`（record + lambda 适配器）**：

```java
public record SimpleDiscountStrategy(String name, UnaryOperator<BigDecimal> rule)
        implements DiscountStrategy {

    public SimpleDiscountStrategy {          // 紧凑构造器，做参数校验
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("策略名称不能为空——没有名字的策略在日志里无法辨认");
        Objects.requireNonNull(rule, "折扣规则不能为 null");
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        return DiscountStrategy.normalize(rule.apply(originalPrice));   // lambda 只管「怎么算」
    }
    // name() 由 record 自动生成，正好就是接口要求的方法
}
```

于是新增一个促销活动**真的只要一行**（[demo6 实测](#66-demo6扩展性)）：

```java
DiscountStrategy flashSale = new SimpleDiscountStrategy(
        "限时秒杀（直降 30，最低 1 元）",
        price -> price.subtract(new BigDecimal("30")).max(BigDecimal.ONE));
```

> **一个诚实的观点**：GoF 写《设计模式》是 1994 年，那时没有 lambda。
> 照搬书上的类结构到 Java 8+，会产生大量「只有一个方法、方法体只有一行」的类文件，
> **这本身就是过度设计**。
> 现代 Java 的策略模式应该是：**复杂策略用具名类，简单策略用 lambda 适配器。**

**什么时候用 lambda，什么时候用具名类？**

| 用 `SimpleDiscountStrategy`（lambda） | 用具名类 |
|---|---|
| 逻辑一两行说得清 | 逻辑超过 5 行 |
| 无内部状态、无配置参数 | 有构造参数需要校验（如满减的 threshold/reduction） |
| 临时活动、A/B 测试 | 长期存在的核心业务规则 |
| 不需要单元测试其内部 | 需要独立的单元测试 |
| 例如：「新人首单立减 15」 | 例如：`VipDiscountStrategy`（有封顶逻辑） |

### 第 9 步：最终结构

```
                    ┌──────────────────────┐
                    │   DiscountStrategy   │  «interface» 抽象策略
                    │──────────────────────│
                    │ + name() : String    │
                    │ + apply(BigDecimal)  │
                    │──────────────────────│
                    │ + requireValidPrice()│ «static» 契约工具
                    │ + normalize()        │ «static» 契约工具
                    └──────────▲───────────┘
                               │ implements
      ┌────────────┬───────────┼────────────┬──────────────────┐
      │            │           │            │                  │
┌─────┴─────┐┌─────┴──────┐┌───┴────┐┌──────┴─────┐┌───────────┴──────────┐
│FullReduc- ││Percentage- ││VipDis- ││NoDiscount- ││SimpleDiscountStrategy│
│tionStrategy││DiscountStr.││countStr.││Strategy    ││  «record»           │
├───────────┤├────────────┤├────────┤├────────────┤├──────────────────────┤
│threshold  ││rate        ││rate    ││INSTANCE    ││name  : String        │
│reduction  ││            ││maxDisc.││(单例)      ││rule  : UnaryOperator │
└───────────┘└────────────┘└────────┘└────────────┘└──────────────────────┘

        ┌────────────────────┐ 持有 (has-a)   ┌──────────────────────┐
        │  PriceCalculator   │───────────────▶│   DiscountStrategy   │
        │  «Context»         │                └──────────────────────┘
        ├────────────────────┤                          ▲
        │ - strategy : final │                          │ 创建
        ├────────────────────┤                ┌─────────┴──────────────┐
        │ + calculatePrice() │                │ DiscountStrategyFactory│
        │ + savedAmount()    │                ├────────────────────────┤
        │ + withStrategy()   │                │ + forLevel(UserLevel)  │
        └────────────────────┘                │ + currentPromotions()  │
                                              │ + bestOf(list, price)  │
                                              └─────────┬──────────────┘
                                                        │ 读取配置
                                              ┌─────────▼──────────┐
                                              │   UserLevel «enum» │
                                              │ NORMAL/SILVER/     │
                                              │ GOLD/VIP           │
                                              │ + rate, maxDiscount│
                                              └────────────────────┘
```

**关系说明**（严格对应源码）：

- 5 个具体策略 `implements DiscountStrategy`（其中 `SimpleDiscountStrategy` 是 record）；
- `PriceCalculator` **持有**一个 `DiscountStrategy`（组合，不是继承）；
- `DiscountStrategyFactory` **创建**策略，**读取** `UserLevel` 的配置数据；
- `Client` 通过工厂拿策略、交给 Context 执行，**自己不 new 任何具体策略类**。

---


## 5. 代码解析：每个类为什么存在

这一节逐个回答两个问题：**这个类为什么必须存在？如果删掉它或者把它合并进别的类，会发生什么？**

### 5.0 文件总览

| 文件 | 行数 | 模式角色 | 一句话职责 |
|---|---|---|---|
| `DiscountStrategy.java` | 176 | 抽象策略 | 定义「算折后价」这个抽象 + 两个契约工具方法 |
| `FullReductionStrategy.java` | 104 | 具体策略 | 满 X 减 Y，参数可配 |
| `PercentageDiscountStrategy.java` | 87 | 具体策略 | 打 N 折，折扣率可配 |
| `VipDiscountStrategy.java` | 150 | 具体策略 | 打 N 折 **且单笔优惠封顶** |
| `NoDiscountStrategy.java` | 99 | 具体策略（空对象） | 「不打折」也是一个策略，消灭 null 判断 |
| `SimpleDiscountStrategy.java` | 100 | 具体策略（record 适配器） | 用一行 lambda 创建一个简单策略 |
| `PriceCalculator.java` | 163 | 上下文 Context | 持有策略、转发调用、包装结果 |
| `UserLevel.java` | 86 | 配置数据 | 会员等级 → 折扣率/封顶额 |
| `DiscountStrategyFactory.java` | 226 | 工厂（GoF 未定义） | 回答「该用哪个策略」+ 选最优 |
| `Client.java` | 347 | 客户端 | 6 段演示 |
| **合计** | **1638** | | |

> 其中约 **60% 的行数是注释和 Javadoc**。这是刻意的——本项目的目标是教学，
> 注释承载的是「为什么这样设计」，这部分信息无法从代码本身推断出来。

---

### 5.1 `DiscountStrategy.java` —— 抽象策略

```java
public interface DiscountStrategy {

    String name();                              // 策略的可读名称
    BigDecimal apply(BigDecimal originalPrice); // 计算折后价

    static void requireValidPrice(BigDecimal price) { ... }   // 契约工具 ①
    static BigDecimal normalize(BigDecimal raw) { ... }       // 契约工具 ②
}
```

#### 为什么是 `interface` 而不是 `abstract class`？

因为**各策略之间没有任何共同状态**。

`FullReductionStrategy` 有 `threshold`/`reduction`，`PercentageDiscountStrategy` 有 `rate`，
`VipDiscountStrategy` 有 `rate`/`maxDiscount`，`NoDiscountStrategy` 什么都没有。
**它们的字段完全不重叠**——没有可以放进基类的东西。

如果硬要抽一个 `AbstractDiscountStrategy`：

```java
public abstract class AbstractDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal apply(BigDecimal price) {
        requireValidPrice(price);
        return normalize(doApply(price));
    }
    protected abstract BigDecimal doApply(BigDecimal price);
}
```

看起来还挺优雅（模板方法模式），但代价是：

1. **`SimpleDiscountStrategy` 就没法写了**——它是 `record`，而 record 不能 `extends` 类；
2. 读者要先理解基类才能理解子类，阅读路径变长；
3. 基类很容易随着时间演变成「什么都往里塞」的垃圾桶；
4. 子类被迫绑定到单一继承链上。

**为了两行无状态的工具方法引入一层继承，不值得。**

所以本项目用**接口 static 方法**（Java 8+）：它属于这个抽象，但不强制任何继承关系。

> **能用组合/工具解决的，不要用继承。**

#### 为什么接口里有 `name()`？它不是「算法」的一部分啊

因为**可观测性是硬需求**。看这两段代码的区别：

```java
// 没有 name()
System.out.println("最终价格：" + calculator.calculatePrice(price));
// 输出：最终价格：280.00
// → 运维看到日志：这单为什么是 280？用了什么优惠？查不到。

// 有 name()
System.out.println(strategy.name() + " → " + calculator.calculatePrice(price));
// 输出：VIP（8 折，最多优惠 200 元） → 280.00
// → 一目了然
```

更关键的是，**有了 `name()` 才能写出通用的遍历代码**。
原项目的名字是调用方硬编码的字符串，和对象没有关联，所以根本没法遍历打印对比表。

`name()` 的实现也有讲究——**名字要由参数动态生成**：

```java
// FullReductionStrategy
return "满减（满 " + threshold.stripTrailingZeros().toPlainString()
        + " 减 " + reduction.stripTrailingZeros().toPlainString() + "）";
```

于是「满 100 减 20」和「满 300 减 80」在日志里能被区分开。
原项目所有满减都叫「满减策略」，出了事故根本定位不到是哪个活动。

> **`name()` 应该返回「人类能看懂的规则描述」，而不是类名。**
> `VIP（8 折，最多优惠 200 元）` 对运营有意义，`VipDiscountStrategy` 只对开发者有意义。

#### `requireValidPrice()`：为什么校验放在每个策略里，而不是 Context 里？

因为**策略可能被 Context 之外的代码直接调用**。

看 [demo6](#66-demo6扩展性) 里的这行：

```java
onCheap.apply(new BigDecimal("20.00"))    // ← 直接调策略，没经过 Context
```

如果校验只放在 Context 里，这条路径就绕过了校验。
**「谁提供契约，谁负责执行契约」**——接口定义了「入参必须非 null 非负」，
那就应该由每个实现来保证。

代价是每个策略都要写一行 `DiscountStrategy.requireValidPrice(originalPrice);`。
**这个重复是值得的**：它换来的是「策略对象单独拿出来用也是安全的」。

#### `normalize()`：为什么两个动作都不能省

```java
static BigDecimal normalize(BigDecimal raw) {
    return raw.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
}
```

**动作 ①：`setScale(2, HALF_UP)`**

`BigDecimal.multiply` 会保留**两个因子的小数位数之和**：

```
99.90 (2 位) × 0.70 (2 位) = 69.9300 (4 位)
```

不处理就会在界面上显示成 `￥69.9300`。人民币最小单位是分，必须收敛到 2 位。

**为什么用 HALF_UP 而不是 HALF_EVEN？**

- `HALF_UP` 是大众理解的「四舍五入」（1.005 → 1.01）；
- `HALF_EVEN` 是「银行家舍入」（1.005 → 1.00，1.015 → 1.02），统计学上偏差更小，
  但**普通用户会认为它算错了**。

电商面向消费者，用 `HALF_UP`；金融清算内部对账，才考虑 `HALF_EVEN`。
**关键是全公司统一一种并写进规范**，最怕的是不同模块用不同舍入模式，对账永远差几分钱。

**动作 ②：`max(BigDecimal.ZERO)`**

这是**资金安全的最后一道防线**。

看 [demo6 的边界测试](#66-demo6扩展性)：「限时秒杀直降 30」在 ￥20 的订单上会算出 `-10`。
没有这个兜底，用户会看到 `￥-10.00`，或者更糟——**系统倒贴钱**。

`FullReductionStrategy` 的构造器已经校验了 `reduction <= threshold`，
但 `SimpleDiscountStrategy` 接受任意 lambda，**没法在构造期校验它的行为**。
所以 `normalize()` 在结果层兜底。

> **防御性设计的层次**：能在构造期拦截的（参数校验）就在构造期拦截；
> 拦不住的（lambda 的行为）就在结果层兜底。两层都要有。

**实测验证兜底生效**：

```
  >>> 边界测试：'限时秒杀直降 30' 在 ￥20 的订单上会算出 -10，
      实际结果：1.00  ← max(BigDecimal.ONE) 兜底 + normalize 保证不为负。
      没有这个兜底，用户会看到'￥-10.00'，或者更糟——系统倒贴钱。
```

（这里结果是 `1.00` 而不是 `0.00`，因为 lambda 自己写了 `.max(BigDecimal.ONE)`——
「最低 1 元」是业务规则，「不为负」是技术兜底，两者是不同层次的防护。）

#### 如果删掉 `DiscountStrategy` 会怎样？

那就没有「策略模式」了——所有策略类之间没有共同类型，
无法放进同一个 `List`，无法多态调用，`PriceCalculator` 只能持有某个具体类。
代码会退化成第 2 节的 `switch` 版本。

**这个接口是整个模式的基石，绝对不能删。**

---

### 5.2 `FullReductionStrategy.java` —— 满减

```java
public class FullReductionStrategy implements DiscountStrategy {
    private final BigDecimal threshold;    // 门槛，如 100
    private final BigDecimal reduction;    // 减免，如 20

    public FullReductionStrategy(BigDecimal threshold, BigDecimal reduction) {
        if (threshold == null || threshold.signum() <= 0) throw new IllegalArgumentException(...);
        if (reduction == null || reduction.signum() <= 0) throw new IllegalArgumentException(...);
        if (reduction.compareTo(threshold) > 0) throw new IllegalArgumentException(
                "减免金额(" + reduction + ")不应大于门槛(" + threshold + ")，否则订单会变成免费");
        ...
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        if (originalPrice.compareTo(threshold) >= 0) {
            return DiscountStrategy.normalize(originalPrice.subtract(reduction));
        }
        return DiscountStrategy.normalize(originalPrice);   // 未达门槛也要 normalize
    }
}
```

#### 三个设计要点

**要点 ①：参数化，而不是硬编码**

原项目写死 `>= 100` 和 `- 20`。重构后是构造参数，于是：

```java
new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20"))   // 满 100 减 20
new FullReductionStrategy(new BigDecimal("300"), new BigDecimal("80"))   // 满 300 减 80
```

**同一个类的两个实例，同时存在**。看 [demo3 实测输出](#63-demo3遍历所有策略一次看清全部方案)：

```
      满减（满 100 减 20）                   ￥330.00      ￥20.00
      满减（满 300 减 80）                   ￥270.00      ￥80.00
```

原项目的硬编码版本做不到这一点。

> **算法族不是「满减 / 打折 / VIP」这么三种，而是「任意参数组合」这么无限种。**
> 参数化之后，运营在数据库里加一行配置就能上线一个新活动，不需要发版。

**要点 ②：构造期校验 `reduction > threshold`**

「满 30 减 50」这种配置错误，如果不校验：

```java
new FullReductionStrategy(30, 50).apply(new BigDecimal("35"))
// = 35 - 50 = -15 → normalize 兜底成 0.00
```

结果是「静默免费」——订单金额变成 0，不抛异常、不打日志。
**这种 bug 极难排查**，往往要到月底对账时才发现有一批 0 元订单。

改成构造期抛异常后，配置错误在**创建策略的那一刻**就暴露了，
而不是等到某个用户下单时才算出诡异价格。这叫**快速失败（fail fast）**。

**要点 ③：`compareTo` 而不是 `equals`**

这是 `BigDecimal` 的经典坑：

```java
new BigDecimal("100").equals(new BigDecimal("100.00"))      // false！scale 不同
new BigDecimal("100").compareTo(new BigDecimal("100.00"))   // 0，数值相同
```

`equals()` 会比较 **scale（小数位数）**。如果用 `equals` 判断门槛，
传入 `100.00` 的订单会被判定为「未达门槛」而不打折——
**用户刚好凑到 100 元却发现没减钱，这是必然会被投诉的 bug**。

> **规则：比较 `BigDecimal` 的数值大小，永远用 `compareTo`，永远不用 `equals`。**
> （`equals` 只在需要区分 scale 的场景才用，而这种场景极少。）

#### 未达门槛为什么也要 `normalize`？

```java
return DiscountStrategy.normalize(originalPrice);   // 看起来是多余的？
```

不多余。假设传入 `new BigDecimal("99.9")`（scale=1），不 normalize 就原样返回 `99.9`，
而其他策略返回的是 `108.00`（scale=2）。调用方拼接字符串时会出现：

```
￥99.9      ← 少一位
￥108.00
```

**界面显示不一致**。`normalize` 保证了「所有策略返回值的 scale 统一为 2」，
这是一个跨策略的**契约**，必须由每个策略共同遵守。

---

### 5.3 `PercentageDiscountStrategy.java` —— 打折

```java
public PercentageDiscountStrategy(BigDecimal rate) {
    if (rate == null) throw new IllegalArgumentException("折扣率不能为 null");
    // 下界为什么是「大于 0」而不是「大于等于 0」？
    if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException(
                "折扣率必须在 (0, 1] 区间内，实际传入：" + rate
                        + "。若想表达'不打折'请使用 NoDiscountStrategy");
    }
    this.rate = rate;
}

@Override
public String name() {
    // 0.90 × 10 = 9.0 → "9 折"；0.75 × 10 = 7.5 → "7.5 折"
    BigDecimal zhe = rate.multiply(BigDecimal.TEN).stripTrailingZeros();
    return "打折（" + zhe.toPlainString() + " 折，折扣率 " + rate.toPlainString() + "）";
}
```

#### 为什么 `rate = 0` 不合法？

`rate = 0` 意味着「全部免费」。这在业务上是**赠品/全额券**，不是「打折」。

如果允许 `rate = 0`，那么「免费」这个语义就藏在了一个数字里，
读代码的人看到 `new PercentageDiscountStrategy(BigDecimal.ZERO)` 会一头雾水：
这是打 0 折？还是配置错了？还是测试代码？

**应该用一个专门的类来表达「免费」这个业务概念**（比如 `FreeOrderStrategy`），
而不是让打折策略退化到 `rate = 0`。

> **原则：不要用魔法值表达业务语义。**
> 同理，`rate = 1` 虽然合法（表示不打折），但异常信息里明确提示
> 「若想表达'不打折'请使用 NoDiscountStrategy」——引导调用方用更清晰的表达方式。

#### 为什么 `rate = 1` 合法但不推荐？

`rate = 1` 是数学上的边界，不会导致任何计算错误，所以不该拦。
但从**表达力**上讲，`NoDiscountStrategy.INSTANCE` 比 `new PercentageDiscountStrategy(ONE)` 清晰得多：
前者一眼看出「这单不打折」，后者要读者自己算一遍 `1 折是多少`。

**校验的边界应该由「会不会算错」决定，而不是由「好不好看」决定。**
不好看的写法用文档和异常提示去引导，不用异常去禁止。

#### `stripTrailingZeros()` 是干什么的？

`new BigDecimal("0.90").multiply(BigDecimal.TEN)` = `9.00`（scale 相加：2+0=... 实际是 2 位）。
不 strip 就会显示成「打折（9.00 折，折扣率 0.90）」——很怪。

`stripTrailingZeros()` 去掉末尾的 0，得到 `9`，显示成「打折（9 折，折扣率 0.90）」。

实测（[demo4](#64-demo4谁来选策略工厂)）：

```
      SILVER   打折（9.5 折，折扣率 0.95）         → ￥332.50
      GOLD     打折（9 折，折扣率 0.90）           → ￥315.00
```

`0.95 × 10 = 9.5` → 「9.5 折」；`0.90 × 10 = 9.00` → strip → 「9 折」。✓

#### 如果把它和 `VipDiscountStrategy` 合并会怎样？

**原项目那样（两者只差 0.9 / 0.8）：应该合并。**

**重构后（VIP 有封顶逻辑）：不能合并。** 因为封顶规则会让 `apply()` 变成：

```java
if (isVip && cap != null && actualDiscount > cap) { ... }   // ← 类型判断又回来了
```

**在一个类里用 `if (isVip)` 区分两种行为，就是把已经拆开的 if-else 又合回去了。**
详见 [5.4](#54-vipdiscountstrategyjava--最重要的一个类)。

---

### 5.4 `VipDiscountStrategy.java` —— 最重要的一个类

这个类的演化过程，是本项目**最值得讲的一段设计故事**。

#### 原项目的问题：它不该存在

```java
// 原项目 PercentageDiscountStrategy
return originalPrice * 0.9;
// 原项目 VipDiscountStrategy
return originalPrice * 0.8;
```

**两个类的方法体除了常量以外完全相同。** 按第 3.3 节的判断标准，
它们应该是**同一个类的两个实例**：

```java
new PercentageDiscountStrategy(new BigDecimal("0.9"))
new PercentageDiscountStrategy(new BigDecimal("0.8"))
```

多一个 `VipDiscountStrategy` 类，只是增加阅读负担，是**为了凑够模式角色数量而制造类**的过度设计。

> **这一点必须诚实指出：原项目在这个类上的设计是不合理的。**
> 但解决方案不是「删掉 VIP 类」，而是「让 VIP 类真的有独立价值」——
> 因为真实业务里 VIP 折扣从来不只是一个数字。

#### 重构：加入封顶规则

真实电商的 VIP 折扣一定带风控：

> **「钻石会员享八折，单笔最多优惠 200 元」**

为什么要封顶？**防套利**。如果不封顶，有人会用 VIP 账号批量下大额订单套现：
买 10 万元的货，八折就是白送 2 万。封顶 200 元之后，套利空间被锁死。

```java
public class VipDiscountStrategy implements DiscountStrategy {
    private final BigDecimal rate;
    private final BigDecimal maxDiscount;   // null 表示不封顶

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);

        BigDecimal discounted = originalPrice.multiply(rate);
        if (maxDiscount == null) {
            return DiscountStrategy.normalize(discounted);      // 不封顶，直接返回
        }

        BigDecimal actualDiscount = originalPrice.subtract(discounted);
        if (actualDiscount.compareTo(maxDiscount) > 0) {
            // 封顶生效：按封顶额反推折后价
            return DiscountStrategy.normalize(originalPrice.subtract(maxDiscount));
        }
        return DiscountStrategy.normalize(discounted);
    }
}
```

**注意封顶的实现方式**：不是 `discounted = max(discounted, price - cap)`，
而是**反推**——「优惠额 = 封顶额」意味着「折后价 = 原价 - 封顶额」。
两种写法结果一样，但反推的语义更直接，也更好读。

#### 为什么 `maxDiscount == null` 表示不封顶，而不用 `BigDecimal.valueOf(Long.MAX_VALUE)`？

因为 `name()` 会打印它：

```java
String cap = (maxDiscount == null)
        ? "不封顶"
        : "最多优惠 " + maxDiscount.stripTrailingZeros().toPlainString() + " 元";
return "VIP（" + zhe + " 折，" + cap + "）";
```

如果用哨兵值，`name()` 会返回：

```
VIP（8 折，最多优惠 9223372036854775807 元）
```

**荒谬。** 用户看到这行字会以为系统坏了。

> **`null` 在这里不是「缺失」，而是「这个维度不适用」的正当表达。**
> 关键是它被**封装在类内部**，没有泄漏给调用方——外部只看到 `name()` 返回「不封顶」。
>
> 这和「用 null 表示不打折」（第 4 步批评过的做法）不矛盾：
> 那个 null 泄漏到了 Context 的字段和调用方的代码里；这个 null 只活在 VIP 策略内部。

#### `isCapped()`：一个业务上很有用的方法

```java
public boolean isCapped(BigDecimal originalPrice) {
    DiscountStrategy.requireValidPrice(originalPrice);
    if (maxDiscount == null) return false;
    BigDecimal actualDiscount = originalPrice.subtract(originalPrice.multiply(rate));
    return actualDiscount.compareTo(maxDiscount) > 0;
}
```

**为什么单独提供这个方法？** 因为「封顶生效的临界价格」是业务上很关心的数字：

```
临界价格 = 封顶额 ÷ (1 - 折扣率) = 200 ÷ 0.2 = 1000 元
```

- 1000 元以下：按八折走，优惠额不足 200；
- 1000 元以上：优惠额**固定为 200**，相当于折扣率越来越低（5000 元只优惠 200 = 96 折）。

**客服需要这个临界点，才能向用户解释「为什么我这单只减了 200」。**
如果客服不知道有封顶规则，就只能说「系统就是这么算的」，然后收获一个投诉。

实测（[demo5](#65-demo5自动选最优方案)）：

```
      VipDiscountStrategy.isCapped(5000) = true  ← 封顶已生效
      VipDiscountStrategy.isCapped(500)  = false ← 500×0.2=100 < 200，未生效
```

#### 这个改动带来的意外收获：答案反转

加入封顶之后，出现了一个非常精彩的现象。看 [demo5 的实测输出](#65-demo5自动选最优方案)：

```
      ￥1000.00   VIP（8 折，最多优惠 200 元）           ￥800.00     ￥200.00
      ￥5000.00   打折（9 折，折扣率 0.90）             ￥4500.00     ￥500.00
```

**5000 元时，「九折」反而比「VIP 八折」更便宜！**

```
VIP 八折：5000 × 0.80 = 4000，优惠 1000 元
          但封顶 200 元 → 实际只优惠 200，折后 ￥4800.00
普通九折：5000 × 0.90 = ￥4500.00，优惠 500 元，不封顶
→ 500 元优惠 > 200 元封顶优惠，所以九折更划算，答案反转了。
```

> **这段实测数据是 `VipDiscountStrategy` 必须独立成类的最强证明。**
> 它有「封顶」这条 `PercentageDiscountStrategy` 无法表达的规则，
> 而这条规则会**实质性地改变业务结果**（最优方案从 VIP 变成了九折）。
>
> 这才是「一个类值得存在」的标准：**它有别人无法替代的行为，而不只是不同的常量。**

---

### 5.5 `NoDiscountStrategy.java` —— 空对象

```java
public final class NoDiscountStrategy implements DiscountStrategy {

    /** 单例：因为它无状态，多个实例没有任何意义。 */
    public static final NoDiscountStrategy INSTANCE = new NoDiscountStrategy();

    /** 私有构造器：强制所有人用 INSTANCE，防止 new 出一堆相同对象。 */
    private NoDiscountStrategy() {}

    @Override
    public String name() {
        return "无折扣（原价）";
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        // 即使不打折也要 normalize，保证返回值 scale 统一为 2
        return DiscountStrategy.normalize(originalPrice);
    }
}
```

#### 它是「策略模式」的一部分吗？

**不是。** 空对象模式（Null Object Pattern）是一个独立的模式。
但它和策略模式是天作之合——**因为策略模式最怕的就是「策略为 null」**。

#### 为什么用单例？

因为它**完全无状态**（没有任何字段）。多个实例之间没有任何区别，
创建多个只是浪费内存和 GC 时间。

`INSTANCE` 常量 + 私有构造器 = 标准的「无状态工具对象」写法。

> 注意这和「单例模式」不是一回事。单例模式关心的是「全局唯一 + 延迟加载 + 线程安全」，
> 这里只是「无状态所以复用一个对象」，用最朴素的 `static final` 就够了，
> **不需要双重检查锁、不需要 enum 单例、不需要 Holder 类**。
> 不要为了「看起来专业」而给一个无状态对象套上复杂的单例实现。

#### 如果删掉它会怎样？

「不打折」就只能用 `null` 表示，于是：

```java
// PriceCalculator 里
public BigDecimal calculatePrice(BigDecimal price) {
    if (strategy == null) {
        return DiscountStrategy.normalize(price);    // ← 特殊分支
    }
    return strategy.apply(price);
}

// 工厂里
public static DiscountStrategy forLevel(UserLevel level) {
    return switch (level) {
        case NORMAL -> null;                          // ← 返回 null，调用方要判空
        ...
    };
}

// 客户端里
DiscountStrategy s = factory.forLevel(level);
System.out.println(s == null ? "无折扣" : s.name());  // ← 每个用到策略的地方都要判空
```

**判空代码会扩散到每一个使用策略的地方。** 而有了空对象，
[demo4 的循环](#64-demo4谁来选策略工厂)对 4 个等级的处理**完全一致，没有任何特殊分支**。

> **「把特殊情况变成一般情况」是消除 if-else 最有效的手段之一。**

---

### 5.6 `SimpleDiscountStrategy.java` —— record + lambda 适配器

```java
public record SimpleDiscountStrategy(String name, UnaryOperator<BigDecimal> rule)
        implements DiscountStrategy {

    public SimpleDiscountStrategy {          // 紧凑构造器
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("策略名称不能为空——没有名字的策略在日志里无法辨认");
        Objects.requireNonNull(rule, "折扣规则不能为 null");
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        return DiscountStrategy.normalize(rule.apply(originalPrice));   // lambda 只管「怎么算」
    }
    // name() 由 record 自动生成，正好就是接口要求的方法
}
```

#### 为什么用 `record`？

三个好处：

1. **`name` 字段自动生成 `name()` 方法**，正好就是接口要求的签名——不用手写；
2. **不可变**：record 的字段都是 `final`，天然线程安全；
3. **代码量极小**：100 行里有 90 行是注释，真正的代码只有 10 行。

#### 紧凑构造器（compact constructor）是什么？

```java
public SimpleDiscountStrategy {      // ← 注意：没有参数列表，没有 return
    if (name == null || name.isBlank()) throw ...;
}
```

这是 record 特有的语法（Java 16+ 正式）。它会在**字段赋值之前**执行，
专门用来做参数校验和规范化。不需要写 `this.name = name`，编译器自动补上。

#### 为什么 lambda 里不用管校验和 normalize？

因为**适配器已经包了一层**：

```java
public BigDecimal apply(BigDecimal originalPrice) {
    DiscountStrategy.requireValidPrice(originalPrice);        // ← 校验在适配器里
    return DiscountStrategy.normalize(rule.apply(originalPrice));  // ← 规范化在适配器里
}
```

lambda 只负责「怎么算」，**契约由适配器统一保证**。

这就是为什么 [demo6](#66-demo6扩展性) 里那个 `flashSale` lambda 敢直接写
`price.subtract(30).max(ONE)` 而不用担心返回 scale 不对——
`normalize()` 会自动把结果收敛到 2 位小数。

> **适配器模式的思想在这里被复用了**：`SimpleDiscountStrategy` 把
> 「`UnaryOperator<BigDecimal>`（只有一个方法）」适配成
> 「`DiscountStrategy`（有两个方法 + 契约）」。

#### 为什么 `name` 不能为空？

因为**没有名字的策略在日志里无法辨认**。

想象线上出了事故，日志里打印 `SimpleDiscountStrategy@1a2b3c → ￥280.00`。
你能知道这是哪个活动吗？不能。查事故的人要翻代码才能知道那个 hash 对应哪个 lambda。

**强制要求名字，是逼着使用者为可观测性负责。**

#### 如果删掉它会怎样？

每个新促销活动都要写一个类文件：

```java
public class FlashSaleStrategy implements DiscountStrategy {
    @Override public String name() { return "限时秒杀（直降 30，最低 1 元）"; }
    @Override public BigDecimal apply(BigDecimal p) {
        DiscountStrategy.requireValidPrice(p);
        return DiscountStrategy.normalize(p.subtract(new BigDecimal("30")).max(BigDecimal.ONE));
    }
}
```

**12 行代码 + 一个新文件 + 一次编译。** 而用它只要 2 行：

```java
DiscountStrategy flashSale = new SimpleDiscountStrategy(
        "限时秒杀（直降 30，最低 1 元）",
        price -> price.subtract(new BigDecimal("30")).max(BigDecimal.ONE));
```

对于「双十一临时活动」「A/B 测试方案」这类**短生命周期**的策略，差别巨大。

---

### 5.7 `PriceCalculator.java` —— 上下文 Context

```java
public class PriceCalculator {
    private final DiscountStrategy strategy;

    public PriceCalculator(DiscountStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy,
                "折扣策略不能为 null；若表示'不打折'请传 NoDiscountStrategy.INSTANCE");
    }

    public BigDecimal calculatePrice(BigDecimal originalPrice) {
        return strategy.apply(originalPrice);
    }

    public BigDecimal savedAmount(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        BigDecimal saved = originalPrice.subtract(calculatePrice(originalPrice));
        return saved.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public PriceCalculator withStrategy(DiscountStrategy newStrategy) {
        return new PriceCalculator(newStrategy);
    }

    public DiscountStrategy getStrategy() { return strategy; }
}
```

#### Context 的职责边界 —— 这是最容易搞错的地方

Context **只负责三件事**：

1. 持有策略引用；
2. 把调用转发给策略；
3. （可选）提供策略需要的额外数据，或包装策略的结果。

Context **绝不负责**：判断「该用哪个策略」、实现任何折扣算法。

> **一旦 Context 里出现 `if (isVip) ... else if (amount > 100) ...`，策略模式就白用了**
> ——那些 if-else 正是策略模式要消灭的东西。
> 策略的选择应该由**调用方**或**专门的工厂**决定。

#### 为什么 `savedAmount()` 放在 Context 而不是策略里？

因为它的实现**与具体策略无关**：不管是满减、打折还是 VIP，
省下的钱永远是「原价 - 折后价」。

如果放进 `DiscountStrategy`，每个策略都要重复实现一遍，
而且新增策略时很容易忘记实现（或者实现错了）。

> **判断「逻辑该放哪」的通用准则**：
> **随策略变化而变化的 → 放策略；所有策略都一样的 → 放 Context。**

`savedAmount()` 里那个 `max(BigDecimal.ZERO)` 也有讲究：

```java
// 理论上不会为负（策略保证折后价 ≤ 原价），但仍做兜底：
// 万一某个自定义策略写错了算出「折后价高于原价」，
// 界面会显示「已优惠 ￥-5.00」，这比抛异常更容易被用户看到并投诉。
```

#### 为什么 `withStrategy()` 而不是 `setStrategy()`？

```java
// 原项目（可变）：切换后旧策略丢失，且非线程安全
calculator.setStrategy(newVip);

// 重构后（不可变）：可以同时持有两个，做价格对比
PriceCalculator withCouponA = calculator.withStrategy(couponA);
PriceCalculator withCouponB = calculator.withStrategy(couponB);
// 给用户展示："用 A 券 108.00 元，用 B 券 100.00 元，B 更划算"
```

两个好处：

- **线程安全**：不可变对象可以自由地在多线程间共享，不需要任何同步；
- **可追溯**：旧对象还在，能同时算出多种方案给用户对比。

实测（[demo1](#61-demo1基础用法)）：

```
      base       仍然是：无折扣（原价）
      withCoupon 是新的：满减（满 100 减 20）
      → 两个对象同时存在，可以做'A 券 vs B 券'的价格对比界面。
      → setter 版本切换后旧策略就丢了，做不出对比。
```

#### Context 是不是多余的？直接用策略不行吗？

确实，`strategy.apply(price)` 一行就能算价，Context 看起来只是转发。
它存在的价值在于：

- 给策略提供一个**稳定的挂载点**。真实系统里 Context 还会持有订单号、用户 ID、
  商品列表等信息，策略需要时可以从 Context 拿（把 Context 传进 `apply`）；
- 集中放置**与具体算法无关的公共逻辑**，比如 `savedAmount`、日志、埋点；
- 让调用方面对一个**业务语义的名字**（`PriceCalculator`）而不是一个抽象接口。

> 本项目的 Context 刻意保持精简（163 行，其中 100 行是注释），
> 因为示例的重点是「策略可替换」，**不是**「Context 有多少功能」。
> **如果 Context 膨胀到几百行，说明职责划分出了问题。**

---

### 5.8 `DiscountStrategyFactory.java` —— 谁来选策略

这个类回答的是**策略模式里最常被忽略的问题**。详细的设计推导见 [第 4.6~4.7 步](#第-6-步补上-gof-没说的那个洞--谁来选策略)。

它提供四个方法，各有明确职责：

| 方法 | 职责 | 什么时候用 |
|---|---|---|
| `forLevel(UserLevel)` | 会员等级 → 折扣策略 | 用户下单时按等级自动应用折扣 |
| `currentPromotions()` | 返回当前所有促销活动 | 结算页展示可用优惠、遍历对比 |
| `bestOf(list, price)` | 从候选里选折后价最低的 | 「已为你选择最优惠方案」 |
| `printComparison(list, price)` | 打印对比表 | 纯教学演示 |

#### `bestOf()`：策略模式最有说服力的应用

```java
public static DiscountStrategy bestOf(List<DiscountStrategy> candidates, BigDecimal originalPrice) {
    if (candidates == null || candidates.isEmpty()) {
        throw new IllegalArgumentException("候选策略列表不能为空");
    }
    DiscountStrategy.requireValidPrice(originalPrice);

    DiscountStrategy best = null;
    BigDecimal bestPrice = null;

    for (DiscountStrategy candidate : candidates) {
        BigDecimal price = candidate.apply(originalPrice);
        // compareTo < 0 表示「更便宜」。BigDecimal 不能用 < 运算符，必须用 compareTo。
        if (bestPrice == null || price.compareTo(bestPrice) < 0) {
            bestPrice = price;
            best = candidate;
        }
    }
    return best;
}
```

**它能写得这么简洁，完全依赖于一个前提**：
所有策略都实现同一个接口，所以可以被放进 `List` 里被同等对待。

**如果没有策略模式，同样的功能要怎么写？**

```java
double p1 = fullReduction(originalPrice);        // 四个独立的方法
double p2 = percentageOff(originalPrice, 0.9);
double p3 = vipDiscount(originalPrice, 0.8, 200);
double p4 = newCustomerDiscount(originalPrice);
double best = Math.min(Math.min(p1, p2), Math.min(p3, p4));
// 而且 best 只是一个数字，你不知道它是哪个活动算出来的，
// 没法告诉用户「你用了哪张券」
```

新增第 5 个活动，就要改这段代码（加一个变量、加一层 `Math.min`）。
而策略版本**只需要往 `currentPromotions()` 里加一行**，`bestOf` 一个字都不用动。

> **这就是「对扩展开放、对修改关闭」的实际含义。**

#### 为什么每次调用都 `new` 一个新策略，而不缓存？

因为这些策略对象都很小（1~2 个 `BigDecimal` 字段），
**创建成本远低于一次 HashMap 查找**。而且策略是**不可变**的，即使 new 出多个也不会有状态冲突。

如果策略创建成本真的高（比如要读配置文件、要初始化规则引擎），
那就该在这里加缓存，或者用 `EnumMap<UserLevel, DiscountStrategy>` 预建好。

> **不要为了「看起来高效」而提前优化。**
> 先测量，确认它是瓶颈，再优化。

#### `NAME_WIDTH` / `PRICE_WIDTH` 是干什么的？

```java
static final int NAME_WIDTH = 36;    // 对比表里「策略名称」列的显示宽度
static final int PRICE_WIDTH = 11;   // 金额列的显示宽度
```

这是为了解决一个**中文输出的老大难问题**，见下一节。

---

### 5.9 `com.patterns.util.DisplayWidth` —— 一个非模式的必要工具

> **这个类不属于策略模式**，它是本项目所有 Demo 共用的输出对齐工具，
> 位于 `src/com/patterns/util/DisplayWidth.java`（139 行）。

#### 为什么需要它？

Java 的 `System.out.printf("%-30s", s)` 是按**字符个数**补齐的，
但**一个汉字在等宽终端里占两列**。看两个字符串（**下面是程序实测的宽度，不是手算的**）：

```
  chars=14  width=20  |满减（满 100 减 20）|
  chars=9   width=15  |新人首单立减 15|
```

用 `%-30s` 打印它们，printf 分别补 16 和 21 个空格，
于是两行的**实际显示列数变成 36 和 36**——看似相等，但与后面拼接的金额列就对不上了。

**未修正的实际输出**（金额列参差不齐）：

```
      满减（满 100 减 20）                        ￥330.00     ￥20.00
      打折（9 折，折扣率 0.90）                      ￥315.00     ￥35.00
      VIP（8 折，最多优惠 200 元）                   ￥280.00     ￥70.00
      新人首单立减 15                             ￥335.00     ￥15.00
```

**用 `DisplayWidth` 修正后**（金额列整齐）：

```
      满减（满 100 减 20）                   ￥330.00      ￥20.00
      满减（满 300 减 80）                   ￥270.00      ￥80.00
      打折（9 折，折扣率 0.90）              ￥315.00      ￥35.00
      VIP（8 折，最多优惠 200 元）           ￥280.00      ￥70.00
      新人首单立减 15                        ￥335.00      ￥15.00
```

#### 它怎么算宽度？

依据是 Unicode 的 **East Asian Width 属性（UAX #11）**：
标记为 `W`（Wide）或 `F`（Fullwidth）的字符占 2 列，其余占 1 列。
实现上用码点区间近似判定：

```java
private static boolean isFullWidth(int cp) {
    return (cp >= 0x1100 && cp <= 0x115F)     // 韩文字母 Jamo
        || (cp >= 0x2E80 && cp <= 0x303E)     // CJK 部首、符号标点（含 、。「」）
        || (cp >= 0x3041 && cp <= 0x33FF)     // 平假名、片假名、注音
        || (cp >= 0x3400 && cp <= 0x4DBF)     // CJK 扩展 A
        || (cp >= 0x4E00 && cp <= 0x9FFF)     // CJK 基本汉字
        || (cp >= 0xA000 && cp <= 0xA4CF)     // 彝文
        || (cp >= 0xAC00 && cp <= 0xD7A3)     // 韩文音节
        || (cp >= 0xF900 && cp <= 0xFAFF)     // CJK 兼容汉字
        || (cp >= 0xFE30 && cp <= 0xFE6F)     // CJK 兼容形式
        || (cp >= 0xFF00 && cp <= 0xFF60)     // 全角 ASCII（含 ，（）：；！？）
        || (cp >= 0xFFE0 && cp <= 0xFFE6)     // 全角符号 ￥ ￠ ￡ ₩
        || (cp >= 0x20000 && cp <= 0x3FFFD);  // CJK 扩展 B 及以上（生僻字）
}
```

**实测各字符的判定结果**：

```
  → U+2192 width=1        ← U+2190 width=1
  × U+00D7 width=1        ÷ U+00F7 width=1
  ★ U+2605 width=1        ◆ U+25C6 width=1
  ￥ U+FFE5 width=2       ， U+FF0C width=2
  （ U+FF08 width=2       【 U+3010 width=2
```

#### 已知局限（诚实说明）

- 制表符、换行符、ANSI 颜色转义序列不做处理；
- **Emoji（U+1F300 以上）在多数现代终端占 2 列，本实现未覆盖**；
- `→ ★ ◆ ◇` 这类**「歧义宽度（Ambiguous）」字符**，在中文终端里通常占 2 列、
  在西文终端占 1 列，本实现按 **1 列**处理（因为它们只用于行内标记，不参与表格对齐）；
- 最终效果还取决于终端字体是否真正等宽。

#### 为什么 `padRight` 超宽时不截断？

```java
public static String padRight(String text, int targetWidth) {
    String safe = (text == null) ? "" : text;
    int gap = targetWidth - width(safe);
    return (gap <= 0) ? safe : safe + " ".repeat(gap);   // 超宽就原样返回
}
```

因为**截断中文会切出半个字符，或者丢掉关键信息**。
宁可这一行错位，也不要丢数据。

> 这是一个价值判断：**对齐是「好看」，数据完整是「正确」。正确优先于好看。**

#### 对齐效果的程序化验证

不能靠肉眼看输出文件判断对齐（因为文本编辑器里汉字也是 2 列，但字符数不同）。
本项目写了一个验证程序，**读取真实运行输出，计算每行「金额列标记符之前」的显示宽度**：

```
  [demo3] 标记 '￥' 之前的显示宽度：
    L59   width=45        满减（满 100 减 20）                   ￥330.00      ￥20.00
    L60   width=45        满减（满 300 减 80）                   ￥270.00      ￥80.00
    L61   width=45        打折（9 折，折扣率 0.90）              ￥315.00      ￥35.00
    L62   width=45        VIP（8 折，最多优惠 200 元）           ￥280.00      ￥70.00
    L63   width=45        新人首单立减 15                        ￥335.00      ￥15.00
    => [OK] 该列对齐

  [demo1] 标记 '→' 之前的显示宽度：
    L4    width=42   ...  L5  width=42   L6  width=42   L7  width=42
    => [OK] 该列对齐

  [demo4] 标记 '→' 之前的显示宽度：
    L70~L73  width=51 ×4
    => [OK] 该列对齐

  [demo5] / [demo6]  => [OK] 该列对齐
```

**5 张表全部 `[OK]`。**

> **一个附带收获**：验证程序纠正了我的一处手算错误。
> 我原以为 `双十一（每满 200 减 50，上不封顶）` 的宽度是 35，
> 程序实测是 **34**（`chars=21 width=34`）。
> **CJK 宽度靠手算极不可靠，必须用程序验证。**

#### 金额列是右对齐的，所以 `￥` 符号本身不对齐

看 demo5 的这两行：

```
      ￥50.00     新人首单立减 15                         ￥35.00      ￥15.00
      ￥5000.00   打折（9 折，折扣率 0.90）             ￥4500.00     ￥500.00
```

`￥35.00`（宽 7，补 4）和 `￥4500.00`（宽 9，补 2）的 **`￥` 符号左边缘不在同一列**。
这是**正确的**——因为金额用的是 `padLeft`（右对齐），
右对齐的意义就是让**小数点对齐**，便于逐位比较大小。

```
   ￥35.00
￥4500.00
   ↑ 小数点都在同一列
```

就像会计报表里 `$1,000` 和 `$999` 右对齐时，`$` 也不对齐一样。
**数字右对齐、文字左对齐，是表格排版的基本规范。**

---

## 6. 执行流程：从 main() 到输出的完整路径

> **本节所有输出都是 JDK 25 下的真实运行结果**，逐字摘录，未做美化。

### 6.0 完整运行输出

```
=== 1. 基础用法：同一笔订单，换不同策略 ===
  原价：￥120.00

      满减（满 100 减 20）                → 折后 ￥  100.00  已优惠 ￥20.00
      打折（9 折，折扣率 0.90）           → 折后 ￥  108.00  已优惠 ￥12.00
      VIP（8 折，最多优惠 200 元）        → 折后 ￥   96.00  已优惠 ￥24.00
      无折扣（原价）                      → 折后 ￥  120.00  已优惠 ￥0.00

  >>> 关键点 1：上面这段 for 循环【不认识任何具体策略类】。
      它只调用 name() / apply()，所以新增第 5 种策略时这段代码一行都不用改。

  >>> 关键点 2：'不打折'也是一个策略（NoDiscountStrategy.INSTANCE），
      不是 null。所以循环里没有 if (strategy == null) 这种分支。

  -- 原项目的写法会怎样？--
      new PriceCalculator(null) → 直接抛异常，且异常信息明确告诉你该怎么做：
      [捕获] NullPointerException: 折扣策略不能为 null；若表示'不打折'请传 NoDiscountStrategy.INSTANCE

      对比原项目：new PriceCalculator() 能成功，但 calculatePrice() 时才抛
      IllegalStateException("未设置折扣策略")——错误暴露的位置离根源很远。

  -- withStrategy() 而不是 setStrategy() --
      base       仍然是：无折扣（原价）
      withCoupon 是新的：满减（满 100 减 20）
      → 两个对象同时存在，可以做'A 券 vs B 券'的价格对比界面。
      → setter 版本切换后旧策略就丢了，做不出对比。

=== 2. 为什么不能用 double 算钱（实测对比）===
  -- double 的错误（原项目用的类型）--
      99.9 * 0.7  （七折）           = 69.92999999999999      应为 69.93   [错]
      58.8 * 0.75 （七五折）         = 44.099999999999994     应为 44.10   [错]
      0.29 * 100  （元转分）         = 28.999999999999996     应为 29      [错]
      0.07 * 100  （元转分）         = 7.000000000000001      应为 7       [错]
      3.0 - 2.1   （找零）           = 0.8999999999999999     应为 0.90    [错]
      Math.round(1.005*100)/100.0    = 1.0                    应为 1.01    [错]

      最严重的是最后一行：1.005 四舍五入应该是 1.01，
      但 double 里 1.005 实际存的是 1.00499999999999989...，所以舍去得到 1.00。
      → 这不是'精度差一点'，是【真的少收了客户一分钱】。

      元转分那一行也很危险：(long)(0.29 * 100) = 28，静默丢了 1 分。

  -- BigDecimal 的结果（本项目用的类型）--
      99.90 × 0.70                   = 69.93
      58.80 × 0.75                   = 44.10
      2.675 → 保留 2 位              = 2.68

  -- 一个诚实的说明 --
      原项目用的是 120.0 * 0.9，实测结果恰好是精确的 108.0，
      所以那个 Demo 跑起来看不出任何问题。
      → 这正是浮点数 bug 最阴险的地方：测试用例的数字刚好能算对，
        一上生产遇到真实价格就出错。不能靠'测了几个都对'来证明安全。

=== 3. 遍历所有策略，一次看清全部方案 ===
  订单原价：￥350.00

      策略名称                                 折后价       已优惠
      ------------------------------------------------------------
      满减（满 100 减 20）                   ￥330.00      ￥20.00
      满减（满 300 减 80）                   ￥270.00      ￥80.00
      打折（9 折，折扣率 0.90）              ￥315.00      ￥35.00
      VIP（8 折，最多优惠 200 元）           ￥280.00      ￥70.00
      新人首单立减 15                        ￥335.00      ￥15.00

  >>> 注意：'满 100 减 20' 和 '满 300 减 80' 是【同一个类】的两个不同实例，
      只是构造参数不同。原项目的硬编码版本做不到这一点。
  >>> '新人首单立减 15' 是一个 lambda 策略，没有对应的类文件（见 demo6）。

=== 4. 谁来选策略？—— 工厂 ===
      NORMAL   无折扣（原价）                      → ￥350.00
      SILVER   打折（9.5 折，折扣率 0.95）         → ￥332.50
      GOLD     打折（9 折，折扣率 0.90）           → ￥315.00
      VIP      VIP（8 折，最多优惠 200 元）        → ￥280.00

  >>> 普通用户的策略是 NoDiscountStrategy（空对象），不是 null。
      所以上面这段循环对 4 个等级的处理方式【完全一致】，没有任何特殊分支。

  >>> 客户端能 new 出 VIP 策略吗？不能——它没有 VIP 的构造入口。
      只能通过工厂按等级取。这就避免了'普通用户自己给自己打八折'的越权漏洞。

  >>> 规则改一处即可：把 UserLevel.GOLD 的折扣率从 0.90 改成 0.88，
      所有调用点自动生效。原项目的写法要全局搜索 new PercentageDiscountStrategy。

=== 5. 自动选最优方案（真实电商的核心功能）===
  候选活动共 5 个：
      - 满减（满 100 减 20）
      - 满减（满 300 减 80）
      - 打折（9 折，折扣率 0.90）
      - VIP（8 折，最多优惠 200 元）
      - 新人首单立减 15

      原价        最优方案                                 折后价       已优惠
      ------------------------------------------------------------------------
      ￥50.00     新人首单立减 15                         ￥35.00      ￥15.00
      ￥120.00    VIP（8 折，最多优惠 200 元）            ￥96.00      ￥24.00
      ￥350.00    满减（满 300 减 80）                   ￥270.00      ￥80.00
      ￥1000.00   VIP（8 折，最多优惠 200 元）           ￥800.00     ￥200.00
      ￥5000.00   打折（9 折，折扣率 0.90）             ￥4500.00     ￥500.00

  >>> 关键观察：随着原价上升，最优方案在不断变化，
      没有任何一个策略在所有价位上都是最优的。

  -- 为什么 5000 元时'九折'反而比'VIP 八折'更便宜？--
      VIP 八折：5000 × 0.80 = 4000，优惠 1000 元
                但封顶 200 元 → 实际只优惠 200，折后 ￥4800.00
      普通九折：5000 × 0.90 = ￥4500.00，优惠 500 元，不封顶
      → 500 元优惠 > 200 元封顶优惠，所以九折更划算，答案反转了。

      VipDiscountStrategy.isCapped(5000) = true  ← 封顶已生效
      VipDiscountStrategy.isCapped(500)  = false ← 500×0.2=100 < 200，未生效

      临界价格 = 封顶额 ÷ (1 - 折扣率) = 200 ÷ 0.2 = 1000 元。
      1000 元以下 VIP 八折更优；1000 元以上普通九折更优。
      → 客服需要这个临界点才能向用户解释'为什么我这单只减了 200'。

  >>> 这段逻辑证明了 VipDiscountStrategy 必须独立成类：
      它有'封顶'这条 PercentageDiscountStrategy 无法表达的规则。
      （原项目里两者只差一个数字 0.8/0.9，那种情况下确实应该合并。）

=== 6. 扩展性：新增一个促销要写几行代码？===
  新增的两个策略（都是用 lambda 一行创建的，没有新建类文件）：
      - 双十一（每满 200 减 50，上不封顶）
      - 限时秒杀（直降 30，最低 1 元）

  在 ￥650.00 的订单上测试：
      双十一（每满 200 减 50，上不封顶）  → ￥  500.00  已优惠 ￥150.00
      限时秒杀（直降 30，最低 1 元）      → ￥  620.00  已优惠 ￥30.00

  把这两个新策略加进候选列表（共 7 个）后，自动选出的最优方案是：
      双十一（每满 200 减 50，上不封顶） → ￥500.00

  >>> 注意 bestOf() / printComparison() / demo4 的代码【一个字都没改】。
      新策略自动被纳入对比和选择。这就是开闭原则的实际含义：
      对扩展开放（加一行 lambda），对修改关闭（0 个已有方法被改动）。

  >>> 边界测试：'限时秒杀直降 30' 在 ￥20 的订单上会算出 -10，
      实际结果：1.00  ← max(BigDecimal.ONE) 兜底 + normalize 保证不为负。
      没有这个兜底，用户会看到'￥-10.00'，或者更糟——系统倒贴钱。
```

---

### 6.1 demo1：基础用法

**入口**：`Client.main()` → `demo1BasicUsage()`

```java
BigDecimal price = new BigDecimal("120.00");
List<DiscountStrategy> strategies = List.of(
        new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")),
        new PercentageDiscountStrategy(new BigDecimal("0.90")),
        new VipDiscountStrategy(new BigDecimal("0.80"), new BigDecimal("200")),
        NoDiscountStrategy.INSTANCE
);

for (DiscountStrategy strategy : strategies) {
    PriceCalculator calculator = new PriceCalculator(strategy);
    System.out.println("      "
            + DisplayWidth.padRight(strategy.name(), 36)
            + "→ 折后 ￥" + DisplayWidth.padLeft(calculator.calculatePrice(price).toPlainString(), 8)
            + "  已优惠 ￥" + calculator.savedAmount(price).toPlainString());
}
```

#### 逐步展开：以第一个策略「满 100 减 20」为例

| 步 | 调用 | 内部发生了什么 | 结果 |
|---|---|---|---|
| 1 | `new FullReductionStrategy(100, 20)` | 三条校验：threshold>0 ✓、reduction>0 ✓、reduction≤threshold ✓ | 策略对象创建成功 |
| 2 | `new PriceCalculator(strategy)` | `Objects.requireNonNull` 通过，`this.strategy = 满减策略` | Context 创建成功，字段 final |
| 3 | `strategy.name()` | `"满减（满 " + 100 + " 减 " + 20 + "）"` | `"满减（满 100 减 20）"` |
| 4 | `DisplayWidth.padRight(name, 36)` | name 显示宽度 = **20**，补 16 个空格 | 占满 36 列 |
| 5 | `calculator.calculatePrice(120.00)` | 转发 → `strategy.apply(120.00)` | 见下 |
| 5a | └ `requireValidPrice(120.00)` | 非 null ✓、signum()>0 ✓ | 通过 |
| 5b | └ `120.00.compareTo(100) >= 0` | **用 compareTo 不用 equals** | `true` |
| 5c | └ `120.00.subtract(20)` | | `100.00` |
| 5d | └ `normalize(100.00)` | `max(ZERO)` → `100.00`；`setScale(2, HALF_UP)` → `100.00` | `100.00` |
| 6 | `padLeft("100.00", 8)` | 宽 6，补 2 | `"  100.00"` |
| 7 | `calculator.savedAmount(120.00)` | `120.00 - calculatePrice(120.00)` = `120.00 - 100.00` | `20.00` |
| 8 | 拼接输出 | `6空格 + 36列 + "→ 折后 ￥" + 8列 + "  已优惠 ￥20.00"` | 见下 |

**最终输出行**：

```
      满减（满 100 减 20）                → 折后 ￥  100.00  已优惠 ￥20.00
```

#### 关键观察：循环体里没有 `switch`、没有 `instanceof`、没有具体类名

```java
for (DiscountStrategy strategy : strategies) {
    // 这里只出现 DiscountStrategy 一个类型
}
```

**这就是多态的价值**：新增第 5 种策略时，这段代码一行都不用改。

#### `null` 策略的实测行为

```java
try {
    new PriceCalculator(null);
} catch (NullPointerException e) {
    System.out.println("      [捕获] NullPointerException: " + e.getMessage());
}
```

实测输出：

```
      [捕获] NullPointerException: 折扣策略不能为 null；若表示'不打折'请传 NoDiscountStrategy.INSTANCE
```

**异常信息不仅说了「哪里错」，还说了「该怎么做」。** 这是良好异常设计的标准：
> 一条有用的异常信息 = 出了什么问题 + 实际传入的值 + 正确的做法是什么

对比原项目：`new PriceCalculator()` 能成功创建，
但要等到 `calculatePrice()` 时才抛 `IllegalStateException("未设置折扣策略")`——
**错误暴露的位置离根源很远**。

#### `withStrategy()` 的实测行为

```java
PriceCalculator base = new PriceCalculator(NoDiscountStrategy.INSTANCE);
PriceCalculator withCoupon = base.withStrategy(
        new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")));
```

```
      base       仍然是：无折扣（原价）
      withCoupon 是新的：满减（满 100 减 20）
```

**`base` 没有被改变**——这是不可变对象的特征。
如果用 setter，`base` 就变成满减了，做不出「两种方案同时展示」的对比界面。

---

### 6.2 demo2：为什么不能用 double 算钱

这段不涉及策略模式的结构，但它是**本项目最有实用价值的一段**——
因为它列出的每一个错误数字，都是真实生产事故的来源。

#### double 的六个实测错误

| 表达式 | 实际结果 | 应为 | 后果 |
|---|---|---|---|
| `99.9 * 0.7` | `69.92999999999999` | `69.93` | 界面显示一长串小数 |
| `58.8 * 0.75` | `44.099999999999994` | `44.10` | 同上 |
| `0.29 * 100` | `28.999999999999996` | `29` | **元转分丢 1 分**（`(long)` 截断成 28） |
| `0.07 * 100` | `7.000000000000001` | `7` | 元转分**多** 1（幸好截断后对了） |
| `3.0 - 2.1` | `0.8999999999999999` | `0.90` | 找零算不对 |
| `Math.round(1.005*100)/100.0` | `1.0` | `1.01` | **四舍五入算错，少收客户 1 分** |

#### 最后一行为什么最严重？

`1.005` 在 double 里实际存的是 `1.00499999999999989...`（因为 0.005 无法用二进制精确表示）。
`Math.round(1.00499999... * 100)` = `Math.round(100.499999...)` = `100`，
`100 / 100.0` = `1.0`。

**它没有「算错」，它是忠实地计算了「一个错误的输入」。**
这就是浮点数问题的本质：**误差在输入阶段就已经产生了，任何后续运算都无法挽回。**

#### BigDecimal 的实测结果

```
      99.90 × 0.70                   = 69.93
      58.80 × 0.75                   = 44.10
      2.675 → 保留 2 位              = 2.68
```

全部正确。

**`2.675 → 2.68` 这一行值得注意**：`HALF_UP` 把 2.675 进位到 2.68。
如果用 `new BigDecimal(2.675)`（double 构造）会得到
`2.67499999999999982236431605997495353221893310546875`，
`setScale(2, HALF_UP)` 之后是 **2.67**——错了。

> **规则：构造 BigDecimal 永远用字符串 `new BigDecimal("2.675")`，
> 或者 `BigDecimal.valueOf(2.675)`（它内部走 `Double.toString`，也是对的）。
> 绝对不要用 `new BigDecimal(2.675)`。**

#### 一个诚实的说明

```
      原项目用的是 120.0 * 0.9，实测结果恰好是精确的 108.0，
      所以那个 Demo 跑起来看不出任何问题。
      → 这正是浮点数 bug 最阴险的地方：测试用例的数字刚好能算对，
        一上生产遇到真实价格就出错。不能靠'测了几个都对'来证明安全。
```

**这段话是我在重构过程中真实经历的一次自我纠正。**
我最初在 Javadoc 里写的是 `120.0 * 0.9 = 108.00000000000001`——
后来实际运行验证，发现结果是**精确的 `108.0`**，我的数据是编造的。

于是我扩大测试范围，实测了 14 个算例，才找到上面那批**真的会出错**的数字。

> **教训：任何写进文档的「实测数据」，都必须真的测过。**
> 「凭印象编一个看起来合理的浮点误差」是技术写作里最危险的习惯——
> 因为它看起来太合理了，读者不会怀疑，作者自己也不会怀疑。

---

### 6.3 demo3：遍历所有策略，一次看清全部方案

**调用链**：

```
Client.demo3Comparison()
  ├─ DiscountStrategyFactory.currentPromotions()      // 拿到 5 个策略
  └─ DiscountStrategyFactory.printComparison(list, 350.00)
       └─ for each strategy:
            ├─ new PriceCalculator(s)
            ├─ s.name()                    → 第 1 列
            ├─ calc.calculatePrice(price)  → 第 2 列
            └─ calc.savedAmount(price)     → 第 3 列
```

**实测输出**：

```
      策略名称                                 折后价       已优惠
      ------------------------------------------------------------
      满减（满 100 减 20）                   ￥330.00      ￥20.00
      满减（满 300 减 80）                   ￥270.00      ￥80.00
      打折（9 折，折扣率 0.90）              ￥315.00      ￥35.00
      VIP（8 折，最多优惠 200 元）           ￥280.00      ￥70.00
      新人首单立减 15                        ￥335.00      ￥15.00
```

#### 逐行验算

原价 350.00：

| 策略 | 计算过程 | 折后价 | 已优惠 |
|---|---|---|---|
| 满 100 减 20 | `350 ≥ 100` ✓ → `350 - 20` | `330.00` | `20.00` |
| 满 300 减 80 | `350 ≥ 300` ✓ → `350 - 80` | `270.00` | `80.00` |
| 九折 | `350 × 0.90 = 315.0000` → normalize | `315.00` | `35.00` |
| VIP 八折封顶 200 | `350 × 0.80 = 280.0000`；优惠 `70 ≤ 200` 未封顶 | `280.00` | `70.00` |
| 新人首单立减 15 | lambda: `350 - 15 = 335` → normalize | `335.00` | `15.00` |

全部与实测输出一致 ✓

#### 这段代码的通用性

```java
public static void printComparison(List<DiscountStrategy> candidates, BigDecimal originalPrice) {
    // 表头
    // 分隔线
    for (DiscountStrategy s : candidates) { ... }    // ← 只依赖 List 的长度
}
```

**`currentPromotions()` 里有几个策略，表就有几行，不需要改这里的任何代码。**

看 [demo6](#66-demo6扩展性)：加了两个 lambda 策略后，候选变成 7 个，
`printComparison` 一个字都没改就能正确输出 7 行。

---

### 6.4 demo4：谁来选策略？—— 工厂

**调用链**：

```
Client.demo4Factory()
  └─ for level in UserLevel.values():        // NORMAL, SILVER, GOLD, VIP
       ├─ DiscountStrategyFactory.forLevel(level)
       │    └─ switch (level) { ... }        // ← 唯一一处「选择逻辑」
       ├─ new PriceCalculator(strategy)
       └─ calculatePrice(350.00)
```

**`forLevel` 内部逐步展开**：

| level | switch 分支 | 读取的配置 | 创建的策略 | `name()` |
|---|---|---|---|---|
| `NORMAL` | `case NORMAL` | — | `NoDiscountStrategy.INSTANCE`（单例，不 new） | `无折扣（原价）` |
| `SILVER` | `case SILVER` | `rate = 0.95` | `new PercentageDiscountStrategy(0.95)` | `打折（9.5 折，折扣率 0.95）` |
| `GOLD` | `case GOLD` | `rate = 0.90` | `new PercentageDiscountStrategy(0.90)` | `打折（9 折，折扣率 0.90）` |
| `VIP` | `case VIP` | `rate = 0.80`, `maxDiscount = 200` | `new VipDiscountStrategy(0.80, 200)` | `VIP（8 折，最多优惠 200 元）` |

**实测输出**：

```
      NORMAL   无折扣（原价）                      → ￥350.00
      SILVER   打折（9.5 折，折扣率 0.95）         → ￥332.50
      GOLD     打折（9 折，折扣率 0.90）           → ￥315.00
      VIP      VIP（8 折，最多优惠 200 元）        → ￥280.00
```

验算：`350 × 0.95 = 332.5000` → `332.50` ✓；`350 × 0.80 = 280.0000`，优惠 `70 < 200` 未封顶 → `280.00` ✓

#### 三个关键点

**① 客户端完全不认识具体策略类**

`demo4Factory()` 的 import 列表里**没有** `VipDiscountStrategy`、`PercentageDiscountStrategy`。
它只认识 `UserLevel` 和 `DiscountStrategy` 两个抽象。

**这才是真正的解耦**——把 VIP 折扣率从 0.80 改成 0.75，客户端代码一行都不用动。

**② 对 4 个等级的处理完全一致，没有特殊分支**

因为 `NORMAL` 拿到的是 `NoDiscountStrategy`（空对象），不是 `null`。
如果没有空对象，这个循环里就必须有：

```java
if (level == UserLevel.NORMAL) {
    System.out.println("无折扣 → ￥" + price);      // 特殊分支
} else {
    DiscountStrategy s = factory.forLevel(level);
    ...
}
```

**③ 安全性：客户端无法越权**

```
  >>> 客户端能 new 出 VIP 策略吗？不能——它没有 VIP 的构造入口。
      只能通过工厂按等级取。这就避免了'普通用户自己给自己打八折'的越权漏洞。
```

严格说，`VipDiscountStrategy` 的构造器是 `public` 的，客户端**技术上**能 new。
但在**架构约定**上，客户端只应该通过工厂拿策略。

要做到**编译期**禁止，可以把具体策略类的构造器改成包级私有（`VipDiscountStrategy(...)` 不加 `public`），
只留工厂在同一个包里能调用。**本项目没有这么做**，因为：

- 教学项目需要让读者能直接 `new` 出来做实验（demo1、demo5、demo6 都直接 new 了）；
- 真实项目里这类约束通常靠 **ArchUnit 之类的架构测试**或 **代码评审**保证，而不是靠访问修饰符。

> **这是一个刻意的取舍，不是疏漏。** 生产代码里应该收紧。

---

### 6.5 demo5：自动选最优方案

**这是本项目最精彩的一段**，它同时证明了三件事。

#### `bestOf()` 的执行过程（以 ￥120.00 为例）

```
候选列表（5 个）：[满100减20, 满300减80, 九折, VIP八折封顶200, 新人立减15]

迭代 1：满 100 减 20
  apply(120.00) → 120 ≥ 100 ✓ → 120 - 20 = 100.00
  bestPrice == null → 记录 best = 满100减20, bestPrice = 100.00

迭代 2：满 300 减 80
  apply(120.00) → 120 < 300 ✗ → normalize(120.00) = 120.00
  120.00.compareTo(100.00) = 1 > 0 → 不更新

迭代 3：九折
  apply(120.00) → 120 × 0.90 = 108.0000 → 108.00
  108.00.compareTo(100.00) = 1 > 0 → 不更新

迭代 4：VIP 八折封顶 200
  apply(120.00) → 120 × 0.80 = 96.0000
                 优惠 = 120 - 96 = 24；24 ≤ 200 未封顶 → 96.00
  96.00.compareTo(100.00) = -1 < 0 → 【更新】best = VIP, bestPrice = 96.00

迭代 5：新人立减 15
  apply(120.00) → lambda: 120 - 15 = 105 → normalize → 105.00
  105.00.compareTo(96.00) = 1 > 0 → 不更新

返回：VIP（8 折，最多优惠 200 元）
```

与实测输出 `￥120.00 → VIP（8 折，最多优惠 200 元） ￥96.00 已优惠 ￥24.00` 一致 ✓

#### 完整实测表格

```
      原价        最优方案                                 折后价       已优惠
      ------------------------------------------------------------------------
      ￥50.00     新人首单立减 15                         ￥35.00      ￥15.00
      ￥120.00    VIP（8 折，最多优惠 200 元）            ￥96.00      ￥24.00
      ￥350.00    满减（满 300 减 80）                   ￥270.00      ￥80.00
      ￥1000.00   VIP（8 折，最多优惠 200 元）           ￥800.00     ￥200.00
      ￥5000.00   打折（9 折，折扣率 0.90）             ￥4500.00     ￥500.00
```

**验算 ￥50.00 那一行**（为什么是「新人立减 15」而不是别的）：

| 策略 | 折后价 |
|---|---|
| 满 100 减 20 | 50 < 100，不打折 → `50.00` |
| 满 300 减 80 | 50 < 300，不打折 → `50.00` |
| 九折 | `50 × 0.90 = 45.00` |
| VIP 八折 | `50 × 0.80 = 40.00`，优惠 10 < 200 → `40.00` |
| 新人立减 15 | `50 - 15 = 35.00` ← **最低** |

✓ 与实测一致。

**注意**：这里 VIP 八折算出 `40.00`，比新人立减的 `35.00` 高，所以没被选中。
但**普通用户其实拿不到 VIP 策略**——`currentPromotions()` 里之所以包含 VIP，
是因为这个方法模拟的是「所有正在进行的活动」，用于教学演示。
真实系统里 `bestOf` 的候选列表应该由**用户实际可用的优惠**构成。

#### 答案反转：为什么 5000 元时九折赢了

```
      VIP 八折：5000 × 0.80 = 4000，优惠 1000 元
                但封顶 200 元 → 实际只优惠 200，折后 ￥4800.00
      普通九折：5000 × 0.90 = ￥4500.00，优惠 500 元，不封顶
      → 500 元优惠 > 200 元封顶优惠，所以九折更划算，答案反转了。
```

**追踪 `VipDiscountStrategy.apply(5000)` 的执行**：

```java
discounted = 5000 × 0.80 = 4000.0000
maxDiscount != null → 继续
actualDiscount = 5000 - 4000 = 1000.0000
actualDiscount.compareTo(maxDiscount) → 1000 vs 200 → 1 > 0 ✓ 封顶生效
return normalize(5000 - 200) = normalize(4800) = 4800.00
```

**而 `PercentageDiscountStrategy.apply(5000)`**：

```java
return normalize(5000 × 0.90) = normalize(4500.0000) = 4500.00
```

`4500 < 4800` → `bestOf` 选中九折。

**临界点计算**：

```
设原价为 P，封顶生效的条件是 P × (1 - rate) > cap
即 P > cap / (1 - rate) = 200 / 0.2 = 1000
```

实测验证：

```
      VipDiscountStrategy.isCapped(5000) = true  ← 封顶已生效
      VipDiscountStrategy.isCapped(500)  = false ← 500×0.2=100 < 200，未生效
```

`isCapped(1000)`：优惠 = `1000 × 0.2 = 200`，`200.compareTo(200) = 0`，**不 > 0**，所以返回 `false`。
即**恰好 1000 元时封顶不生效**（优惠正好等于封顶额），这与表格里
`￥1000.00 → VIP ￥800.00 已优惠 ￥200.00` 一致 ✓

#### 这段演示证明了三件事

```
  >>> 这段逻辑证明了 VipDiscountStrategy 必须独立成类：
      它有'封顶'这条 PercentageDiscountStrategy 无法表达的规则。
      （原项目里两者只差一个数字 0.8/0.9，那种情况下确实应该合并。）
```

1. **策略模式让「自动选最优」成为可能**——所有策略可以被同等对待；
2. **`VipDiscountStrategy` 的封顶规则有真实的业务意义**——它不是凑数的类；
3. **「最优策略」是价格的函数**——不存在一个永远最优的策略。

第 3 点尤其重要。它意味着**「智能优惠推荐」这个功能不可能用 if-else 写死**，
因为它要对每个价格点重新计算所有方案。只有把所有算法封装成同接口的对象，
才能写出 `bestOf()` 这样简洁的实现。

---

### 6.6 demo6：扩展性

**问题**：双十一要新增两个促销活动，需要改多少代码？

```java
DiscountStrategy doubleEleven = new SimpleDiscountStrategy(
        "双十一（每满 200 减 50，上不封顶）",
        price -> {
            // 每满 200 减 50：350 元 → 减 50；600 元 → 减 150
            int times = price.divideToIntegralValue(new BigDecimal("200")).intValue();
            return price.subtract(new BigDecimal("50").multiply(BigDecimal.valueOf(times)));
        });

DiscountStrategy flashSale = new SimpleDiscountStrategy(
        "限时秒杀（直降 30，最低 1 元）",
        price -> price.subtract(new BigDecimal("30")).max(BigDecimal.ONE));
```

**答案：两个 lambda，0 个新类文件，0 个已有方法被修改。**

#### 实测输出

```
  在 ￥650.00 的订单上测试：
      双十一（每满 200 减 50，上不封顶）  → ￥  500.00  已优惠 ￥150.00
      限时秒杀（直降 30，最低 1 元）      → ￥  620.00  已优惠 ￥30.00

  把这两个新策略加进候选列表（共 7 个）后，自动选出的最优方案是：
      双十一（每满 200 减 50，上不封顶） → ￥500.00
```

#### 验算

**双十一 @ 650**：
```
times = 650 ÷ 200 取整 = 3          （divideToIntegralValue）
减 50 × 3 = 150
650 - 150 = 500 → normalize → 500.00 ✓
```
`divideToIntegralValue` 是 `BigDecimal` 的方法，返回商的整数部分（丢弃余数），
**不需要 `setScale(0, RoundingMode.DOWN)` 这种绕弯的写法**。

**限时秒杀 @ 650**：
```
650 - 30 = 620；max(620, 1) = 620 → normalize → 620.00 ✓
```

**7 个候选里选最优 @ 650**：

| 策略 | 折后价 |
|---|---|
| 满 100 减 20 | `630.00` |
| 满 300 减 80 | `570.00` |
| 九折 | `585.00` |
| VIP 八折封顶 200 | `650×0.8=520`，优惠 `130 < 200` 未封顶 → `520.00` |
| 新人立减 15 | `635.00` |
| **双十一每满 200 减 50** | **`500.00`** ← 最低 |
| 限时秒杀直降 30 | `620.00` |

✓ 与实测一致。

#### 边界测试：兜底真的生效了吗？

```
  >>> 边界测试：'限时秒杀直降 30' 在 ￥20 的订单上会算出 -10，
      实际结果：1.00  ← max(BigDecimal.ONE) 兜底 + normalize 保证不为负。
      没有这个兜底，用户会看到'￥-10.00'，或者更糟——系统倒贴钱。
```

**执行路径**：

```java
// lambda 内部
20 - 30 = -10
max(-10, ONE) = 1                     ← lambda 自己的业务兜底「最低 1 元」
// 回到 SimpleDiscountStrategy.apply
normalize(1) = 1.max(ZERO).setScale(2, HALF_UP) = 1.00    ← 接口的技术兜底
```

**两层防护都生效了**，输出 `1.00`（scale=2，格式统一）✓

如果把 lambda 里的 `.max(BigDecimal.ONE)` 去掉，结果会是 `normalize(-10)` = `0.00`。
**`normalize` 保证不会出负数，但保证不了「最低 1 元」这种业务规则**——
业务规则必须由策略自己表达。这是两个不同层次的防护，缺一不可。

---


## 7. 扩展对比：新需求来了改哪里

这一节用**五个真实的产品需求**，检验这套设计的扩展成本。
每个场景都列出「要改哪些文件、要加多少代码、有没有已有代码被修改」。

### 场景 A：运营要新增一个「跨店满 500 减 120」活动

**改动清单**：

| 文件 | 改动 |
|---|---|
| `DiscountStrategyFactory.java` | `currentPromotions()` 里加 **1 行** |
| 其他所有文件 | **0 改动** |

```java
public static List<DiscountStrategy> currentPromotions() {
    return List.of(
            new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20")),
            new FullReductionStrategy(new BigDecimal("300"), new BigDecimal("80")),
            new FullReductionStrategy(new BigDecimal("500"), new BigDecimal("120")),   // ← 只加这一行
            ...
    );
}
```

**自动获得的能力**：
- `Client.demo3` 的对比表**自动多一行**（`printComparison` 没改）；
- `Client.demo5` 的 `bestOf` **自动把它纳入比较**（`bestOf` 没改）；
- 日志里**自动**能区分它（因为 `name()` 由参数生成，会显示「满减（满 500 减 120）」）。

**这就是开闭原则的实际含义**：对扩展开放（加一行），对修改关闭（0 个已有方法被改动）。

> **对比原项目**：硬编码的 `>= 100` / `- 20` 意味着必须**新建一个类**
> `CrossShopFullReductionStrategy`，然后改 `Client` 的演示代码，还要给它编一个名字字符串。
> 三处改动 vs 一处改动。

---

### 场景 B：产品说「会员折扣可以和满减叠加，先算会员折扣再算满减」

**这是最能检验设计质量的场景。**

#### 错误做法：在 `VipDiscountStrategy` 里加满减逻辑

```java
// ❌ 不要这样写
public class VipDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal apply(BigDecimal price) {
        BigDecimal afterVip = price.multiply(rate);
        if (afterVip.compareTo(new BigDecimal("100")) >= 0) {   // ← 满减逻辑混进来了
            afterVip = afterVip.subtract(new BigDecimal("20"));
        }
        return normalize(afterVip);
    }
}
```

后果：
- 满减规则被复制到了 VIP 策略里，将来改满减要改两个地方；
- 无法自由组合（如果还想叠加「新人立减」，就要在 VIP 策略里再加一段）；
- `PercentageDiscountStrategy` 也需要同样改造，重复扩散。

#### 正确做法：新增一个「组合策略」

**关键洞察**：「两个策略依次执行」本身**也是一个策略**——它符合 `DiscountStrategy` 的接口。

```java
/** 组合策略：把多个策略串成一条链，依次执行。 */
public class CompositeDiscountStrategy implements DiscountStrategy {

    private final List<DiscountStrategy> chain;

    public CompositeDiscountStrategy(List<DiscountStrategy> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("组合策略至少要包含一个子策略");
        }
        this.chain = List.copyOf(chain);      // 防御性拷贝，保证不可变
    }

    @Override
    public String name() {
        // "会员八折 + 满 100 减 20"
        return chain.stream().map(DiscountStrategy::name)
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        DiscountStrategy.requireValidPrice(originalPrice);
        BigDecimal result = originalPrice;
        for (DiscountStrategy s : chain) {
            result = s.apply(result);         // ← 前一个的输出是后一个的输入
        }
        return DiscountStrategy.normalize(result);
    }
}
```

**改动清单**：

| 文件 | 改动 |
|---|---|
| `CompositeDiscountStrategy.java` | **新建**（约 30 行代码） |
| `DiscountStrategyFactory.java` | `forLevel()` 里把 VIP 分支改成返回组合策略 |
| 其他所有文件 | **0 改动** |

```java
case VIP -> new CompositeDiscountStrategy(List.of(
        new VipDiscountStrategy(level.getRate(), level.getMaxDiscount()),
        new FullReductionStrategy(new BigDecimal("100"), new BigDecimal("20"))
));
```

**为什么这个方案好？**

1. **`PriceCalculator` 完全不知道发生了组合**——它拿到的还是一个 `DiscountStrategy`；
2. **`bestOf()` 自动能处理组合策略**——因为它也是 `DiscountStrategy`；
3. **组合可以嵌套**——`CompositeDiscountStrategy` 里可以再放一个 `CompositeDiscountStrategy`；
4. **顺序是显式的**——`List` 的顺序就是执行顺序，一目了然。

> **这个方案的本质是「组合模式 + 策略模式」的结合**：
> 把「一组策略」也当成「一个策略」，于是客户端无法区分（也不需要区分）单个和组合。
> 这是**组合模式（Composite Pattern）**的核心思想，本项目在结构型部分有独立实现。

**注意顺序的语义差异**：

```
先 VIP 八折再满 100 减 20，原价 350：
  350 × 0.8 = 280 → 280 ≥ 100 → 280 - 20 = 260.00

先满 100 减 20 再 VIP 八折，原价 350：
  350 ≥ 100 → 350 - 20 = 330 → 330 × 0.8 = 264.00
```

**差 4 元。** 这就是为什么 `List` 的顺序必须由业务明确规定，而不能随意排列。
真实电商里这个顺序是财务和法务共同确认的（涉及税务计算基数）。

---

### 场景 C：策略需要更多信息（用户等级、商品品类、渠道）

**需求**：「生鲜品类不参与满减」「App 渠道额外减 5 元」。

现在的接口是 `apply(BigDecimal originalPrice)`，只有一个参数，信息不够。

#### 三种改法

**改法 ①：加参数**

```java
BigDecimal apply(BigDecimal price, User user, List<Item> items, Channel channel);
```

❌ **最差的选择**。每次有新需求就要改接口签名，**所有实现类都要跟着改**，
而且大部分策略根本用不到 `channel` 参数。这是「接口污染」。

**改法 ②：引入参数对象（推荐）**

```java
/** 计价上下文：策略需要的所有信息都放这里。 */
public record PricingContext(
        BigDecimal originalPrice,
        UserLevel userLevel,
        List<String> categories,
        String channel
) {
    public boolean containsCategory(String c) { return categories.contains(c); }
}

public interface DiscountStrategy {
    String name();
    BigDecimal apply(PricingContext ctx);      // ← 只改一个参数类型
}
```

✅ **这是正确方向**。将来再加字段（比如优惠券列表），**接口签名不变**，
只改 `PricingContext` 这个 record，各策略按需读取。

代价：所有策略的 `apply` 签名要改一次（一次性成本），
且每个策略都要改 `requireValidPrice(ctx.originalPrice())`。

**改法 ③：让 Context 传递自己**

```java
public interface DiscountStrategy {
    BigDecimal apply(PriceCalculator calculator);      // 策略反向依赖 Context
}
```

❌ 会造成**双向依赖**（Context 持有策略，策略又依赖 Context 类型），
测试策略时必须构造整个 Context，失去独立性。

> **结论**：本项目**没有做这个改造**，因为示例的重点是「策略可替换」，
> 引入 `PricingContext` 会让读者分心。
> 但要知道：**真实系统里几乎必然会走到改法 ②**，因为「只看原价就能决定折扣」的场景太少了。
>
> **这是一个刻意的简化，不是设计缺陷。**

---

### 场景 D：折扣规则要从数据库读，运营能随时改

**需求**：不能每次改折扣都发版。

**改动清单**：

| 文件 | 改动 |
|---|---|
| `UserLevel.java` | 可保留（枚举仍表示等级），但 `rate`/`maxDiscount` 改从配置读 |
| `DiscountStrategyFactory.java` | `forLevel()` 内部改成查配置 |
| 新增 `DiscountConfigRepository.java` | 读数据库/配置中心 |
| 所有具体策略类 | **0 改动** ← 关键 |

```java
public static DiscountStrategy forLevel(UserLevel level) {
    DiscountConfig cfg = repository.findByLevel(level);   // 从数据库读
    if (cfg == null || !cfg.isEnabled()) {
        return NoDiscountStrategy.INSTANCE;
    }
    // 用配置里的「类型」决定创建哪个策略
    return switch (cfg.type()) {
        case PERCENTAGE   -> new PercentageDiscountStrategy(cfg.rate());
        case FULL_REDUCE  -> new FullReductionStrategy(cfg.threshold(), cfg.reduction());
        case VIP_WITH_CAP -> new VipDiscountStrategy(cfg.rate(), cfg.maxDiscount());
        case NONE         -> NoDiscountStrategy.INSTANCE;
    };
}
```

**注意这里出现了一个新的 switch**——但它和「客户端散落的 if-else」有本质区别：

| | 客户端的 if-else | 工厂里的 switch |
|---|---|---|
| 判断依据 | 业务规则（会员等级、金额） | **策略类型标识**（数据库里的一个字段） |
| 改动频率 | 每次业务规则变化 | 只在**新增一种策略类型**时 |
| 位置 | 散落 N 处 | 集中 1 处 |

**「新增一种折扣参数」不需要改这个 switch**（只是数据库多一行）；
只有「新增一类**算法逻辑**」才需要改（新写一个策略类 + 加一个 case）。
后者是低频事件，而且必然伴随新代码，改 switch 是合理的。

> **这是一个重要的认知**：策略模式**不能消灭所有的 if-else**，
> 它消灭的是「**业务规则的** if-else」，剩下的是「**类型分派的** switch」——
> 后者数量固定、位置集中、改动低频。这是可以接受的。

---

### 场景 E：想做 A/B 测试，一半用户用新折扣规则

**改动清单**：

| 文件 | 改动 |
|---|---|
| `DiscountStrategyFactory.java` | 新增一个 `forLevelWithExperiment(level, bucket)` |
| 新策略 | 用 `SimpleDiscountStrategy` 一行创建（不需要新类文件） |
| 其他所有文件 | **0 改动** |

```java
public static DiscountStrategy forLevelWithExperiment(UserLevel level, String bucket) {
    if ("B".equals(bucket)) {
        // B 组：新的激进折扣，用 lambda 一行定义，随时可下线
        return new SimpleDiscountStrategy("实验组：全场 7.5 折",
                p -> p.multiply(new BigDecimal("0.75")));
    }
    return forLevel(level);      // A 组：走原有逻辑
}
```

**为什么这个场景特别适合策略模式？**

因为实验代码**天然要求「可以随时插拔、随时下线」**。
用 `SimpleDiscountStrategy` + lambda，实验规则的整个生命周期只有几行代码，
下线时删掉即可，**不会留下任何残余的类文件**。

> 如果用继承或者 if-else 写实验逻辑，下线时会留下一堆
> 「这段代码还有用吗？」「这个分支还有人走吗？」的疑问，
> 最后没人敢删，代码越积越厚。

---

### 扩展成本汇总

| 场景 | 新建文件 | 修改已有文件 | 影响范围 |
|---|---|---|---|
| A 新增一个满减活动 | 0 | 1（工厂加 1 行） | 极小 |
| B 支持折扣叠加 | 1（组合策略） | 1（工厂改 1 个 case） | 小 |
| C 策略需要更多上下文 | 1（PricingContext） | 接口 + 所有策略（一次性） | **中**（接口变更） |
| D 规则配置化 | 1（Repository） | 1（工厂内部） | 小 |
| E A/B 测试 | 0 | 1（工厂加 1 个方法） | 极小 |

**只有场景 C（改接口签名）成本较高**，这是所有基于接口的设计的共同代价。
**接口一旦发布就难以修改**——所以设计接口时要预留扩展空间
（比如一开始就用参数对象，而不是散参数）。

---

## 8. 优缺点与常见误区

### 8.1 优点

| 优点 | 具体体现（本项目实测证据） |
|---|---|
| **符合开闭原则** | 场景 A 只加 1 行，0 个已有方法被修改 |
| **消除条件分支** | `Client` 全文没有一处 `if (type == ...)` |
| **算法可独立测试** | 每个策略是纯函数（输入→输出，无副作用），单测不需要 mock |
| **算法可运行时切换** | `withStrategy()` 返回新 Context，旧的不受影响 |
| **算法可遍历比较** | `bestOf()` 能自动选最优——继承方案做不到 |
| **避免重复代码** | `savedAmount()` 只在 Context 里实现一次 |
| **提高可观测性** | `name()` 让日志能说清用了哪个折扣 |

### 8.2 缺点与代价

| 缺点 | 说明 | 缓解方式 |
|---|---|---|
| **类数量增加** | 本项目 10 个文件，原项目 6 个 | 简单策略用 `SimpleDiscountStrategy` + lambda |
| **调用方必须知道有哪些策略** | 才能选对——本项目用工厂解决了 | `DiscountStrategyFactory` |
| **多了一层间接** | `calc.calculatePrice()` → `strategy.apply()`，看代码要跳转 | Context 保持精简，只做转发 |
| **策略间无法共享状态** | 每个策略是独立对象 | 需要共享时用组合策略包装 |
| **过度设计风险** | 只有 2 个分支也套策略模式 | 见 [3.3 什么时候不需要](#33-什么时候不需要策略模式) |

### 8.3 九个常见误区

#### 误区 ①：「策略模式就是把 if-else 拆成多个类」

**不对。** 拆类只是手段，目的是**让算法成为可以独立传递、替换、组合的对象**。

判断你是否真的用对了策略模式，看这三条：

- ✅ 能不能把策略放进 `List` 里遍历？（本项目 demo3）
- ✅ 能不能在运行时换一个策略而不影响其他代码？（本项目 `withStrategy`）
- ✅ 能不能把「一组策略」当成「一个策略」用？（场景 B 的组合策略）

**如果三条都做不到，那你只是把 if-else 换了个地方写。**

#### 误区 ②：「Context 里应该有选择策略的逻辑」

**大错特错。** 看这段代码：

```java
// ❌ 这是「披着策略模式外衣的 if-else」
public class PriceCalculator {
    public BigDecimal calculatePrice(BigDecimal price, User user) {
        DiscountStrategy s;
        if (user.isVip()) {
            s = new VipDiscountStrategy(...);
        } else if (price.compareTo(HUNDRED) >= 0) {
            s = new FullReductionStrategy(...);
        } else {
            s = NoDiscountStrategy.INSTANCE;
        }
        return s.apply(price);
    }
}
```

**if-else 一点没少，只是从「算法实现」搬到了「算法选择」，而且还多了一堆类。**
这种代码比原始的 switch 版本**更糟**——它有了策略模式的全部复杂度，却没有它的好处。

> **Context 只负责「持有 + 转发 + 包装结果」，绝不负责「选择」。**
> 选择是调用方或工厂的职责。

#### 误区 ③：「lambda 可以完全替代策略类」

Java 8 之后很多人认为「策略模式过时了，用 lambda 就行」。

**部分正确，但有边界**：

| lambda 能做 | lambda 做不到 |
|---|---|
| 无状态的简单算法 | 带构造参数校验的复杂算法 |
| 临时活动、A/B 测试 | 需要独立单元测试的核心逻辑 |
| 一个方法说得清的事 | 有多个方法（如 `isCapped()`） |

本项目的 `VipDiscountStrategy` 有 `apply()` + `name()` + `isCapped()` + 构造器校验，
**用 lambda 写不出来**（lambda 只能实现单抽象方法）。

而且 `DiscountStrategy` 有**两个**抽象方法，本来就**不是函数式接口**，
不能直接用 `price -> price.multiply(RATE)` 赋值给它。
`SimpleDiscountStrategy` 这个 record 适配器的存在，正是为了解决这个问题。

> **现代 Java 的策略模式 = 复杂策略用具名类 + 简单策略用 lambda 适配器。**
> 照搬 1994 年的书上的类结构，会产生大量只有一行逻辑的类文件，这本身就是过度设计。

#### 误区 ④：「不打折就传 null」

见 [5.5](#55-nodiscountstrategyjava--空对象)。传 null 会让判空代码扩散到每一个使用点。
**「不打折」是一种合法的业务状态，应该用一个对象表达，而不是用「对象的缺失」表达。**

#### 误区 ⑤：「策略对象应该做成单例来节省内存」

**要看策略有没有状态。**

- `NoDiscountStrategy` 无状态 → 单例合理（本项目就是这么做的）；
- `FullReductionStrategy` 有 `threshold`/`reduction` → **不能单例**，因为不同活动参数不同；
- `PercentageDiscountStrategy` 有 `rate` → 同理不能单例。

如果硬要给有状态的策略做单例，就只能把参数变成 `apply()` 的入参，
于是接口变成 `apply(price, threshold, reduction)`——**接口污染**，
而且不同策略需要的参数不同，签名无法统一。

> **策略对象通常很小（几个字段），创建成本远低于一次 HashMap 查找。
> 不要为了「看起来高效」而提前优化。**

#### 误区 ⑥：「策略模式 = 状态模式」

**结构几乎一样，意图完全不同。** 这是最高频的面试题，见 [9.2](#92-高频追问)。

一句话区分：
- **策略**：外界**主动选择**用哪个算法（用户选了哪张券）；
- **状态**：对象**自己演化**到下一个状态（订单从「待付款」变成「已付款」），
  且**当前状态决定下一个状态**。

#### 误区 ⑦：「用了策略模式就不需要工厂了」

**恰恰相反，策略模式几乎总是需要一个工厂。**
因为「谁来选策略」这个问题必然存在，不写工厂，选择逻辑就会散落到各个客户端。

GoF 原书没规定这一点，是原书的一个空白（1994 年的工程实践还没这么细化）。

#### 误区 ⑧：「策略模式的策略之间应该有继承关系」

比如「VIP 策略继承打折策略，因为 VIP 也是打折」——**这是错误的建模**。

```java
// ❌ 不要这样
public class VipDiscountStrategy extends PercentageDiscountStrategy {
    @Override
    public BigDecimal apply(BigDecimal price) {
        BigDecimal result = super.apply(price);      // 复用父类的打折逻辑
        // 再加封顶
    }
}
```

问题：
- 子类依赖父类的实现细节，父类一改子类就可能坏（**脆弱基类问题**）；
- 封顶逻辑和打折逻辑其实是**正交**的两个维度，用继承绑定会造成组合爆炸
  （如果还要「VIP + 满减 + 封顶」呢？继承链要多深？）；
- 语义上「VIP 折扣」不是一种「普通打折」，它是**另一种算法**。

**策略之间应该是平级的兄弟关系，都只实现同一个接口。**
需要复用逻辑时，用**组合**（场景 B 的 `CompositeDiscountStrategy`）或**静态工具方法**
（本项目的 `DiscountStrategy.normalize()`）。

#### 误区 ⑨：「金额可以用 double，只要最后四舍五入就行」

**不行。** 看 [6.2](#62-demo2为什么不能用-double-算钱) 的实测：

```
Math.round(1.005 * 100) / 100.0 = 1.0        // 应为 1.01
```

**误差在输入阶段就产生了**（`1.005` 在 double 里存的是 `1.004999...`），
后面再怎么四舍五入都无法挽回。

而且 `(long)(0.29 * 100) = 28`——**元转分静默丢 1 分，不抛异常、不打日志**。

> **规则：凡是钱，一律用 `BigDecimal`（或以「分」为单位的 `long`）。**
> 构造时用字符串 `new BigDecimal("0.1")`，绝不用 `new BigDecimal(0.1)`。
> 比较大小用 `compareTo`，绝不用 `equals`。

---

### 8.4 与相似模式的区别

#### 策略 vs 状态

| | 策略模式 Strategy | 状态模式 State |
|---|---|---|
| **意图** | 封装**可互换的算法** | 封装**对象的生命周期状态** |
| **谁决定用哪个** | **外界**（调用方/工厂）主动选 | **对象自己**按业务规则演化 |
| **切换时机** | 任意时刻，可来回切 | 只能按状态机的转移路径 |
| **实现类之间** | 互相**不知道**对方存在 | 一个状态**主动创建**下一个状态 |
| **有没有「当前状态」概念** | 没有，只有「当前用的算法」 | 有，且状态转换是核心 |
| **典型例子** | 折扣计算、排序算法、支付方式 | 订单状态、审批流、线程生命周期 |

**代码结构上几乎一样**（一个接口 + N 个实现 + 一个 Context 持有它），
所以**光看类图分不清**，必须看**意图**和**切换逻辑**。

一个判别技巧：

```java
// 策略模式：切换由外部驱动
calculator.withStrategy(couponA);
calculator.withStrategy(couponB);      // 可以随便切回来
calculator.withStrategy(couponA);

// 状态模式：切换由内部驱动，且有方向性
order.pay();          // PendingState → PaidState
order.ship();         // PaidState → ShippedState
order.pay();          // ❌ 抛异常！已发货的订单不能再付款
```

**状态模式里「从 A 切到 B」是业务规则，「从 B 切回 A」通常是非法的。**
策略模式里没有这种限制。

> 本项目在 `behavioral/state/` 有独立的状态模式实现（订单状态机），可以对照阅读。

#### 策略 vs 工厂方法

| | 策略模式 | 工厂方法 |
|---|---|---|
| **关注点** | **算法**可以互换 | **对象创建**被延迟到子类 |
| **返回什么** | 一个「行为」（会算价的对象） | 一个「产品」（数据/实体对象） |
| **谁来选** | 调用方或工厂 | 子类重写工厂方法 |

**它们经常一起用**：本项目的 `DiscountStrategyFactory` 就用了**简单工厂**（不是工厂方法）
来创建策略对象。

> 注意区分「简单工厂」（一个静态方法 + switch，**不是 GoF 23 种模式之一**）
> 和「工厂方法」（抽象工厂方法 + 子类重写，**是 GoF 模式**）。
> 本项目的 `DiscountStrategyFactory` 是简单工厂。

#### 策略 vs 模板方法

| | 策略模式 | 模板方法模式 |
|---|---|---|
| **复用手段** | **组合**（持有策略对象） | **继承**（子类重写抽象方法） |
| **绑定时机** | 运行期（可切换） | 编译期（固定） |
| **谁控制流程** | **策略自己**（整个算法都在策略里） | **父类**（骨架在父类，子类只填空） |
| **粒度** | 替换**整个算法** | 替换算法中的**某几步** |
| **能不能运行时换** | 能 | **不能**（对象创建后类型就定了） |

**什么时候用哪个？**

- 如果**整个算法**都要换 → 策略模式；
- 如果**流程固定，只有某几步不同** → 模板方法模式；
- 如果流程里**大部分步骤相同，只有一步不同**，且不需要运行时切换 → 模板方法更简洁。

本项目的下一个模式就是模板方法（`templatemethod/`），可以对照阅读。

> **两者可以结合**：模板方法的某个「可变步骤」可以委托给一个策略对象。
> Spring 的 `JdbcTemplate` 就是这种混合结构。

#### 策略 vs 命令

| | 策略模式 | 命令模式 |
|---|---|---|
| **封装的是** | **算法**（怎么算） | **请求**（做什么 + 对谁做 + 参数） |
| **有没有接收者** | 没有，策略自己就是执行者 | **有 Receiver**，命令只是转发 |
| **能不能撤销** | 不能（纯计算，无副作用） | **能**（`undo()` 是命令模式的核心能力） |
| **典型用途** | 同一件事的多种做法 | 排队、日志、撤销、事务 |

本项目的 `behavioral/command/` 有独立实现。

#### 策略 vs 装饰器

| | 策略模式 | 装饰器模式 |
|---|---|---|
| **意图** | **换**一种做法 | **加**一层功能 |
| **结构** | 平级替换（A 或 B） | 层层包裹（A(B(C))） |
| **接口关系** | 策略实现同一个接口 | 装饰器**既实现又持有**同一个接口 |

**场景 B 的 `CompositeDiscountStrategy` 其实更接近装饰器**——它包裹了多个策略。
如果改成「在 VIP 折扣外面再包一层日志记录」，那就是标准的装饰器：

```java
public class LoggingDiscountStrategy implements DiscountStrategy {
    private final DiscountStrategy delegate;      // 既实现又持有
    @Override
    public BigDecimal apply(BigDecimal price) {
        BigDecimal result = delegate.apply(price);
        log.info("{} : {} → {}", delegate.name(), price, result);
        return result;
    }
}
```

> 本项目的 `CombineApplicationExamples` 里就有 `LoggingDiscountDecorator`，
> 是「策略 + 装饰器 + 责任链 + 工厂」的组合应用示例。

---

## 9. 面试题与自测

### 9.1 面试话术

#### 30 秒版本

> 策略模式把「同一件事的多种做法」各自封装成独立的类，让它们实现同一个接口，
> 于是可以互相替换。它的核心价值是**用多态替代条件分支**——
> 把编译期的 `switch` 变成运行期的对象绑定，新增一种做法时不用改任何已有代码。
>
> 我在项目里用它实现电商的折扣计算：满减、打折、VIP 封顶折扣、空对象表示不打折，
> 再加一个 record + lambda 的适配器让简单活动可以一行代码创建。
> 因为所有策略都是同一个接口，我还实现了「自动选最优惠方案」的功能——
> 这个功能用 if-else 是写不出来的。

#### 2 分钟版本（加上工程细节）

> ……（前面同上）……
>
> 实现时有几个工程上的考量：
>
> **第一，金额全部用 `BigDecimal`，不用 `double`。**
> 我实测过，`Math.round(1.005 * 100) / 100.0` 在 Java 里得到的是 `1.0` 而不是 `1.01`，
> 因为 `1.005` 在 double 里实际存的是 `1.00499...`。
> `(long)(0.29 * 100)` 会得到 `28`，静默丢一分钱。
> 这种 bug 不抛异常、不打日志，只会在月底对账时炸出来。
>
> **第二，用空对象模式处理「不打折」。**
> 传 `null` 会让判空代码扩散到每一个使用点，
> 而 `NoDiscountStrategy.INSTANCE` 让所有会员等级的处理逻辑完全一致，没有任何特殊分支。
>
> **第三，Context 用构造器注入而不是 setter。**
> `private final` + `Objects.requireNonNull` 保证策略字段永远不为 null，
> 忘记传策略的代码根本编译不过，而不是运行到一半才抛 `IllegalStateException`。
>
> **第四，补了一个工厂来解决「谁来选策略」。**
> GoF 原书没规定这一点，但如果不写工厂，
> 「用户等级 → 用哪个策略」这条规则就会在每个调用点被抄一遍，
> 模式只成功了一半。工厂里用 switch 表达式而不是 Map，
> 是因为 enum 的 switch 有穷尽性检查——新增一个等级忘了处理会**编译报错**。
>
> **第五，简单策略用 lambda 适配器。**
> 因为 `DiscountStrategy` 有两个抽象方法（`name()` 和 `apply()`），不是函数式接口，
> 所以我写了一个 `record SimpleDiscountStrategy(String name, UnaryOperator<BigDecimal> rule)`
> 来适配。这样新增一个临时促销活动只要两行代码，不需要新建类文件。

---

### 9.2 高频追问

#### Q1：策略模式和状态模式的区别？（**必考**）

**答**：类结构几乎一样，区别在**意图**和**切换逻辑**。

- 策略：**外界主动选**用哪个算法，可以任意来回切换，各实现之间互不知道对方存在；
- 状态：**对象自己**按业务规则演化，切换有方向性（`PendingState → PaidState`，反向通常非法），
  而且一个状态**会主动创建**下一个状态。

判别技巧：看能不能「切回去」。

```java
calculator.withStrategy(couponA);
calculator.withStrategy(couponB);
calculator.withStrategy(couponA);    // ✅ 策略模式：随便切

order.pay();     // Pending → Paid
order.ship();    // Paid → Shipped
order.pay();     // ❌ 状态模式：已发货不能再付款，抛异常
```

#### Q2：为什么用组合（持有策略对象）而不用继承（子类重写方法）？

**答**：三个原因。

1. **继承是编译期绑定**——对象一旦创建，算法就固定了，运行时换不了；
2. **Java 单继承**——继承名额被用掉后，将来无法再继承别的类；
3. **继承无法遍历比较**——四种计算器是四个不同类型，
   没法写 `bestOf()` 这种「把所有算法放进 List 挑最优」的代码。

> **GoF 的原则：优先使用组合，而不是继承（Favor composition over inheritance）。**

#### Q3：`switch` 还在工厂里，这不是没消灭条件分支吗？

**答**：策略模式消灭的是「**业务规则的** if-else」，
剩下的是「**类型分派的** switch」，两者性质不同：

| | 客户端散落的 if-else | 工厂里的 switch |
|---|---|---|
| 出现次数 | N 份 | **1 份** |
| 判断依据 | 业务规则（等级、金额、品类……） | 策略类型标识 |
| 改动频率 | 每次业务规则变化 | 只在**新增一类算法**时 |
| 客户端认识具体类吗 | 认识 | **不认识** |

而且工厂里的 switch 用了 **enum + switch 表达式**，
新增枚举值忘了处理会**编译报错**，比散落的 if-else 安全得多。

> **策略模式不能消灭所有条件分支，它把分支从 N 处集中到 1 处，并从「业务判断」降级为「类型分派」。**

#### Q4：lambda 能替代策略模式吗？

**答**：**部分能，有边界。**

能替代的：无状态、一两行说得清的简单算法。
不能替代的：

- 带构造参数校验的（`FullReductionStrategy` 有三条校验）；
- 有多个方法的（`VipDiscountStrategy` 有 `isCapped()`）；
- 需要独立单元测试的核心逻辑。

而且 lambda 要求接口是**函数式接口**（只有一个抽象方法）。
本项目的 `DiscountStrategy` 有 `name()` 和 `apply()` 两个，
**不能直接 lambda 赋值**，所以才写了 `SimpleDiscountStrategy` 这个 record 适配器。

> **最佳实践：复杂策略用具名类，简单策略用 lambda 适配器，两者共存。**

#### Q5：Context 是不是多余的？直接 `strategy.apply(price)` 不就行了？

**答**：在**这个示例里**，Context 确实只是转发。它存在的价值在于：

1. 提供一个**稳定的挂载点**——真实系统的 Context 还持有订单号、用户 ID、商品列表；
2. 集中放置**与具体算法无关的公共逻辑**——比如 `savedAmount()`（原价 - 折后价，所有策略都一样）、日志、埋点；
3. 让调用方面对一个**业务语义的名字**（`PriceCalculator`）而不是抽象接口。

**判断 Context 是否合理的标准**：它应该只有「持有 + 转发 + 包装结果」三类代码。
**如果 Context 里出现了算法逻辑或者策略选择逻辑，说明职责划分错了。**

#### Q6：策略对象要不要做成单例？

**答**：看有没有状态。

- 无状态（`NoDiscountStrategy`）→ 单例合理，本项目用 `public static final INSTANCE`；
- 有构造参数（`FullReductionStrategy` 的 threshold/reduction）→ **不能单例**，不同活动参数不同。

而且策略对象通常只有 1~2 个字段，**创建成本远低于一次 HashMap 查找**，
不要为了「看起来高效」而提前优化。

#### Q7：为什么 `name()` 要放在接口里？这不是「算法」的一部分

**答**：因为**可观测性是硬需求**。

原项目的策略名字是调用方硬编码的字符串（`"【满减策略】"`），和对象没有关联，导致：

- 想遍历所有策略打印对比表 → **拿不到名字**；
- 同一个策略在不同调用点可能被写成不同名字；
- 出了事故，日志里只有一个 `VipDiscountStrategy@1a2b3c`，定位不到是哪个活动。

有了 `name()`，名字成为策略的**内在属性**，
本项目还让它**由参数动态生成**（`"满减（满 300 减 80）"`），
所以同一个类的不同实例在日志里也能区分开。

#### Q8：`BigDecimal` 有哪些坑？

**答**：四个，本项目全部踩过并处理了。

1. **构造**：`new BigDecimal(0.1)` 会得到 `0.1000000000000000055511...`。
   必须用字符串 `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`；
2. **比较**：`equals()` 会比较 scale，
   `new BigDecimal("100").equals(new BigDecimal("100.00"))` 返回 **false**。
   比数值必须用 `compareTo() == 0`；
3. **乘法的小数位**：`multiply` 保留两个因子小数位数之和，
   `99.90 × 0.70 = 69.9300`（4 位），必须 `setScale(2, HALF_UP)` 收敛；
4. **舍入模式**：`HALF_UP` 是大众理解的四舍五入，
   `HALF_EVEN` 是银行家舍入（1.005 → 1.00）。
   电商面向消费者用 `HALF_UP`，**关键是全公司统一一种**。

#### Q9：Spring 项目里策略模式怎么写？

**答**：让所有策略类加 `@Component`，注入 `Map<String, DiscountStrategy>`：

```java
@Service
public class DiscountStrategyFactory {
    private final Map<String, DiscountStrategy> registry;   // Spring 自动按 bean 名收集所有实现

    public DiscountStrategyFactory(Map<String, DiscountStrategy> registry) {
        this.registry = registry;
    }

    public DiscountStrategy get(String type) {
        DiscountStrategy s = registry.get(type);
        if (s == null) throw new IllegalArgumentException("未知的折扣类型：" + type);
        return s;
    }
}
```

**新增策略只需加一个 `@Component` 类，工厂代码一行都不用改**——
Spring 的依赖注入天然支持「注册表式工厂」。

本项目不引入 Spring，用 switch 表达式表达同样的意图。

#### Q10：这个设计有没有过度设计的地方？

**答**：有，我刻意保留了两处，并能说清理由：

1. **`PriceCalculator` 在这个示例里确实只是转发**。
   保留它是因为它是策略模式的**标准角色**，教学上不能省；
   而且 `savedAmount()` 证明了「Context 放公共逻辑」的价值。

2. **具体策略类的构造器是 `public` 的**，客户端技术上能绕过工厂直接 `new VipDiscountStrategy`。
   生产代码应该收紧成包级私有，只让工厂能调用。
   本项目没收紧，是因为 demo1/demo5/demo6 都需要直接 new 来做实验。

**反过来，有两处我刻意「没有设计」**：

1. **没有抽 `AbstractDiscountStrategy` 基类**——各策略没有共同状态，
   为两行工具方法引入继承不值得，而且会挡住 `record` 实现；
2. **没有合并 `VipDiscountStrategy` 和 `PercentageDiscountStrategy`**——
   原项目那样（只差 0.8/0.9）确实该合并，
   但重构后 VIP 有「封顶」这条独立规则，
   而且这条规则会**实质改变业务结果**（5000 元时最优方案从 VIP 反转成九折），
   所以它配得上一个单独的类。

> **判断「一个类值不值得存在」的标准：它有别人无法替代的行为，而不只是不同的常量。**

---

### 9.3 自测题

> 答案在 `<details>` 里折叠，先自己想再展开。

**1. 为什么 `PriceCalculator` 的 `strategy` 字段要声明成 `final`？**

<details>
<summary>查看答案</summary>

三个理由：

1. **保证非 null**：`final` + 构造器 `Objects.requireNonNull` = 这个字段永远不可能为 null，
   于是 `calculatePrice()` 里不需要任何判空代码；
2. **线程安全**：不可变对象可以自由地在多线程间共享，不需要同步；
3. **强制构造器注入**：没有 setter，就不存在「已 new 但不可用」的中间态。
   忘记传策略的代码**编译不过**，而不是运行时才抛 `IllegalStateException`。

这叫**构造器不变式（constructor invariant）**：
一个对象要么被正确创建，要么根本创建不出来。
</details>

**2. `FullReductionStrategy.apply()` 里，为什么用 `compareTo` 而不用 `equals` 判断门槛？**

<details>
<summary>查看答案</summary>

因为 `BigDecimal.equals()` 会比较 **scale（小数位数）**：

```java
new BigDecimal("100").equals(new BigDecimal("100.00"))      // false！
new BigDecimal("100").compareTo(new BigDecimal("100.00"))   // 0，数值相同
```

如果用 `equals`，传入 `100.00` 的订单会被判定为「未达门槛」而不打折——
**用户刚好凑到 100 元却发现没减钱，必然会被投诉**。

规则：**比较 `BigDecimal` 的数值大小，永远用 `compareTo`。**
</details>

**3. `DiscountStrategy.normalize()` 里的 `max(ZERO)` 和 lambda 里的 `max(ONE)` 有什么区别？能省掉哪个？**

<details>
<summary>查看答案</summary>

**两个都不能省，它们是不同层次的防护。**

- `normalize()` 里的 `max(BigDecimal.ZERO)` 是**技术兜底**：
  保证任何策略的返回值都不为负（不能倒找钱给用户）。
  它作用在**所有**策略上，是接口的契约。
- lambda 里的 `max(BigDecimal.ONE)` 是**业务规则**：
  「限时秒杀最低 1 元」是这个活动特有的规则，只有它需要。

实测：「直降 30」用在 ￥20 的订单上，
lambda 算出 `-10` → `max(ONE)` → `1` → `normalize` → `1.00`。

如果去掉 lambda 的 `max(ONE)`，结果是 `normalize(-10)` = `0.00`——
**技术兜底保证了不出负数，但保证不了「最低 1 元」这个业务规则。**
</details>

**4. 为什么 `requireValidPrice()` 和 `normalize()` 是接口的 `static` 方法，而不是抽一个 `AbstractDiscountStrategy` 基类？**

<details>
<summary>查看答案</summary>

因为各策略之间**只有这两行工具代码是共同的，没有任何共同状态**
（`threshold`/`rate`/`maxDiscount` 完全不重叠）。

为两行无状态的工具方法引入继承，代价是：

1. **`SimpleDiscountStrategy` 就写不出来了**——它是 `record`，record 不能 `extends` 类；
2. 读者要先理解基类才能理解子类，阅读路径变长；
3. 基类容易演变成「什么都往里塞」的垃圾桶；
4. 子类被绑定到单一继承链上。

接口的 `static` 方法（Java 8+）正好适合放「与契约相关的工具」：
它属于这个抽象，但不强制任何继承关系。

> **能用组合/工具解决的，不要用继承。**
</details>

**5. demo5 里，为什么 ￥5000 的订单「九折」比「VIP 八折」更便宜？临界价格是多少？**

<details>
<summary>查看答案</summary>

因为 VIP 折扣**封顶 200 元**：

```
VIP 八折：5000 × 0.80 = 4000，优惠 1000 元
          但封顶 200 → 实际只优惠 200，折后 ￥4800.00
普通九折：5000 × 0.90 = ￥4500.00，优惠 500 元，不封顶
→ 500 > 200，九折更划算
```

**临界价格 = 封顶额 ÷ (1 - 折扣率) = 200 ÷ 0.2 = 1000 元。**

- 1000 元以下：VIP 八折更优；
- 1000 元以上：普通九折更优（因为 VIP 优惠额固定在 200，相当于折扣率越来越低）。

实测：`isCapped(5000) = true`，`isCapped(500) = false`。
（`isCapped(1000)` 也是 `false`，因为优惠恰好等于 200，代码判断的是 `> 0` 而不是 `>= 0`。）

**这个现象证明了 `VipDiscountStrategy` 必须独立成类**——
它有 `PercentageDiscountStrategy` 无法表达的规则，
而且这条规则会实质改变业务结果。
</details>

**6. 如果要支持「会员折扣和满减叠加」，你会怎么改？能不能在 `VipDiscountStrategy` 里直接加满减逻辑？**

<details>
<summary>查看答案</summary>

**不能直接加。** 那样会导致满减规则被复制到多个策略里，改一次要改多处，而且无法自由组合。

**正确做法：新增一个组合策略**（见 [场景 B](#场景-b产品说会员折扣可以和满减叠加先算会员折扣再算满减)）：

```java
public class CompositeDiscountStrategy implements DiscountStrategy {
    private final List<DiscountStrategy> chain;

    @Override
    public BigDecimal apply(BigDecimal originalPrice) {
        BigDecimal result = originalPrice;
        for (DiscountStrategy s : chain) {
            result = s.apply(result);      // 前一个的输出是后一个的输入
        }
        return DiscountStrategy.normalize(result);
    }
}
```

**关键洞察**：「一组策略」本身**也是一个策略**（符合接口），
所以 `PriceCalculator` 完全不知道发生了组合，`bestOf()` 也自动能处理它。
这是**组合模式 + 策略模式**的结合。

**注意顺序有语义**：原价 350 时
「先八折再满 100 减 20」= 260.00，
「先满减再八折」= 264.00，**差 4 元**。
顺序必须由业务（财务/法务）明确规定。

改动清单：新建 1 个文件 + 工厂改 1 个 case，**其他文件 0 改动**。
</details>

**7. 综合设计题：现在要支持「优惠券」，用户可能同时持有多张券，但每单只能用一张，且有些券不能和会员折扣共用。你会怎么设计？**

<details>
<summary>查看答案</summary>

这是一道开放题，一个合理的答案：

**① 券是一种策略**

```java
public record Coupon(String code, CouponType type, BigDecimal value,
                     BigDecimal minAmount, boolean stackableWithMemberDiscount,
                     LocalDateTime expireAt) { }
```

用 `CouponStrategy implements DiscountStrategy` 包装它，
`apply()` 里做校验（是否过期、是否达门槛），不满足就**返回原价**（不抛异常，
因为「券不可用」是正常业务情况，不是错误）。

**② 「只能用一张」→ 在 `bestOf` 的候选列表构造处控制**

不要把互斥规则写进策略里。构造候选列表时就应该只放一张券：

```java
List<DiscountStrategy> candidates = new ArrayList<>();
candidates.add(factory.forLevel(user.getLevel()));       // 会员折扣
if (selectedCoupon != null && selectedCoupon.stackableWithMemberDiscount()) {
    candidates.add(new CompositeDiscountStrategy(List.of(memberStrategy, couponStrategy)));
} else if (selectedCoupon != null) {
    candidates.add(couponStrategy);                       // 二选一
}
DiscountStrategy best = factory.bestOf(candidates, price);
```

**③ 「不能共用」→ 用组合策略表达「可叠加」，用候选列表表达「互斥」**

- 可叠加 → `CompositeDiscountStrategy`（场景 B）；
- 互斥 → 候选列表里只出现其中一个。

**④ 关键设计判断**

- **互斥规则不应该写在策略类里**。因为互斥是「策略之间的关系」，
  而策略应该互相不知道对方存在（否则组合爆炸）。
  关系应该由**构造候选列表的那一层**（工厂或 Service）表达。
- **「券不可用」返回原价而不是抛异常**——因为结算页要遍历所有券展示给用户，
  抛异常会中断遍历。但**「券被伪造」必须抛异常**，那是安全问题，不是业务情况。

**⑤ 这个设计的扩展性检验**

- 新增一种券类型 → 加一个 `CouponType` 枚举值 + 工厂 switch 加一个 case（**编译期会提醒**）；
- 新增叠加规则 → 只改候选列表构造逻辑，策略类不动；
- 券配置从数据库读 → 加一个 Repository，策略类不动。

> **评分要点**：能不能识别出「互斥关系不属于策略内部」，
> 是这道题的分水岭。把互斥写进策略里（比如 `if (other instanceof VipDiscountStrategy) throw ...`）
> 会导致策略之间产生依赖，彻底破坏策略模式的价值。
</details>

---

## 附录：文件清单与运行方式

### A.1 文件清单

```
src/com/patterns/behavioral/strategy/
├── README.md                          本文档
├── DiscountStrategy.java       (176)  抽象策略接口 + 2 个契约工具方法
├── FullReductionStrategy.java  (104)  满减：满 X 减 Y，参数可配
├── PercentageDiscountStrategy.java (87)  打折：打 N 折，折扣率可配
├── VipDiscountStrategy.java    (150)  VIP 折扣：打 N 折 + 单笔优惠封顶
├── NoDiscountStrategy.java      (99)  空对象：「不打折」也是一个策略
├── SimpleDiscountStrategy.java (100)  record 适配器：一行 lambda 创建策略
├── PriceCalculator.java        (163)  Context：持有 + 转发 + 包装结果
├── UserLevel.java               (86)  枚举：会员等级 → 折扣率/封顶额
├── DiscountStrategyFactory.java(226)  工厂：选策略 + 选最优 + 打印对比
└── Client.java                 (347)  6 段演示
                              ─────
                              1638 行（约 60% 是注释和 Javadoc）

src/com/patterns/util/
└── DisplayWidth.java           (139)  CJK 显示宽度对齐工具（非模式，全项目共用）
```

### A.2 编译与运行

```powershell
# 编译（PowerShell）
& "F:\enCodingApps\JDK\jdk-25\bin\javac.exe" --release 25 -encoding UTF-8 `
    -d out `
    (Get-ChildItem -Path src\com\patterns -Recurse -Filter *.java `
       | Where-Object { $_.FullName -match 'strategy|util' } `
       | Select-Object -ExpandProperty FullName)

# 运行
& "F:\enCodingApps\JDK\jdk-25\bin\java.exe" "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" `
    -cp out com.patterns.behavioral.strategy.Client
```

```bash
# 或者标准命令行
javac --release 25 -encoding UTF-8 -d out \
    src/com/patterns/util/DisplayWidth.java \
    src/com/patterns/behavioral/strategy/*.java
java -Dfile.encoding=UTF-8 -cp out com.patterns.behavioral.strategy.Client
```

> **Windows 控制台编码提示**：如果中文显示成乱码，
> 加上 `-Dstdout.encoding=UTF-8`（JDK 19+）或先执行 `chcp 65001`。
> 本项目的输出**只使用 GBK 控制台也能正常渲染的字符**
> （`→ ★ ◆ ￥ ← ×`），没有使用 `▶ ✔ ✘` 这类会显示成方块的符号。

### A.3 验证状态

| 项目 | 状态 |
|---|---|
| JDK 25 编译 | ✅ 通过，无警告 |
| `Client` 运行 | ✅ 6 段演示全部输出正常 |
| 业务计算正确性 | ✅ 所有金额逐行手工验算，与输出一致（见第 6 节） |
| `double` 精度数据 | ✅ 全部实测，非编造（见 [6.2](#62-demo2为什么不能用-double-算钱) 的自我纠正说明） |
| 表格对齐 | ✅ 程序化验证，5 张表全部 `[OK]`（见 [5.9](#59-compatternsutildisplaywidth--一个非模式的必要工具)） |
| 边界情况 | ✅ 实测：null 策略抛 NPE、￥20 订单用「直降 30」得到 1.00、封顶临界点 1000 元 |
| 单元测试 | ⚠️ **本项目未编写 JUnit 测试**——所有验证通过运行 `Client` 并逐行核对输出完成 |

### A.4 推荐阅读顺序

1. **先看本文档的第 1、2 节**——理解要解决什么问题；
2. **再看第 4 节设计推导**——这是核心，理解「为什么会演变成这样」；
3. **然后打开 `DiscountStrategy.java`**——看接口定义和它的 Javadoc（里面有 double 精度实测数据）；
4. **接着 `PriceCalculator.java`**——理解 Context 的职责边界；
5. **再看四个具体策略**，顺序：`NoDiscountStrategy`（最简单）→ `PercentageDiscountStrategy` → `FullReductionStrategy` → `VipDiscountStrategy`（最复杂，也最精彩）；
6. **然后 `DiscountStrategyFactory.java`**——理解「谁来选策略」；
7. **最后运行 `Client.java`**，对照本文档第 6 节逐段看输出。

### A.5 与其他模式的关联

| 相关模式 | 关联点 | 本项目位置 |
|---|---|---|
| **空对象模式** | `NoDiscountStrategy` 消灭 null 判断 | 本目录内 |
| **组合模式** | 场景 B 的 `CompositeDiscountStrategy` | `structural/composite/` |
| **装饰器模式** | 给策略包一层日志/缓存 | `structural/decorator/` |
| **简单工厂** | `DiscountStrategyFactory` | 本目录内 |
| **工厂方法** | GoF 版本的工厂 | `creational/factorymethod/` |
| **状态模式** | 结构相同，意图不同（最高频面试题） | `behavioral/state/` |
| **模板方法模式** | 继承 vs 组合的对照 | `behavioral/templatemethod/` |
| **命令模式** | 封装「请求」而非「算法」 | `behavioral/command/` |
| **享元模式** | `NoDiscountStrategy.INSTANCE` 的无状态复用思想 | `structural/flyweight/` |

> **组合应用示例**：`src/CombineApplicationExamples/` 里有
> 「策略 + 责任链 + 装饰器 + 工厂」的完整综合案例（`LoggingDiscountDecorator` 等）。

---

**文档版本**：基于 JDK 25 实际编译运行结果编写，所有输出、所有数字均为真实实测。
