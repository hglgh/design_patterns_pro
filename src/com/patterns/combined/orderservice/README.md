# 组合应用示例：订单折扣引擎（策略 + 装饰器 + 责任链 + 工厂）

> **一句话**：四个模式不是四个名词堆在一起，而是四个互不重叠的问题的四个答案。
> 每一个都能回答「不做会缺什么」，答不上来的抽象就应该删掉。

> **本目录 20 个文件、3547 行代码已用 JDK 25 实际编译运行验证**：
> `javac -Xlint:all` 零警告，运行输出 152 行 / 9797 字节（`-Dstdout.encoding=UTF-8` 口径），
> 三次运行 SHA-256 完全一致（`DF1E29B1…`）。
> 文中所有数字、所有旧实现的错误输出、所有新链路的轨迹，都是真实执行得到的，不是编造的。
> 验证方式见[附录 B](#附录-b-编译运行与验证记录)。

---

## 元数据

| 项目 | 内容 |
|---|---|
| 包名 | `com.patterns.combined.orderservice` |
| 源文件 | 20 个 `.java` + 本 README |
| 物理行数 | 3547 行（源码 147563 字节） |
| 编译产物 | 42 个 `.class`，110552 字节 |
| JDK | 25（`javac -Xlint:all -encoding UTF-8`，零警告） |
| 运行入口 | `com.patterns.combined.orderservice.Client` |
| 运行输出 | 152 行 / 9797 字节 / `DF1E29B1FCAAF0C65242EADDFE16D34D90BC8CECC81FAF498193AE9190518DD7` |
| 对照基线 | 原项目 9 文件 / 217 行 / 输出 11 行 / 411 字节 |
| 涉及模式 | 策略（Strategy）、装饰器（Decorator）、责任链（Chain of Responsibility）、工厂方法 + 简单工厂（Factory） |

---

## 目录

1. [现实案例：一张订单页上的四行优惠](#1-现实案例一张订单页上的四行优惠)
2. [原始实现：9 个文件 217 行，与它的七个问题](#2-原始实现9-个文件-217-行与它的七个问题)
3. [设计动机：为什么四个模式必须一起用](#3-设计动机为什么四个模式必须一起用)
4. [设计推导：从一行算式推到二十个文件](#4-设计推导从一行算式推到二十个文件)
5. [代码解析：二十个文件为什么存在](#5-代码解析二十个文件为什么存在)
6. [执行流程：十段 Demo 的真实输出](#6-执行流程十段-demo-的真实输出)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- [附录 A. 文件清单](#附录-a-文件清单)
- [附录 B. 编译运行与验证记录](#附录-b-编译运行与验证记录)
- [附录 C. 交叉引用](#附录-c-交叉引用)
- [附录 D. 术语表](#附录-d-术语表)
- [附录 E. 缺陷取证台账](#附录-e-缺陷取证台账)
- [附录 F. 版本与约定](#附录-f-版本与约定)

---

## 1. 现实案例：一张订单页上的四行优惠

### 1.1 说人话版：用户只看见四行

你在电商平台买了一件 150 元的商品，进入结算页，看到这样一块区域：

```text
商品总价              ￥150.00
─────────────────────────────
满减优惠  满100减20   -￥20.00
会员九折  九折        -￥13.00
积分抵扣  1000 分      -￥10.00
─────────────────────────────
应付金额              ￥107.00
```

你只看到四行。你不会去想：

- 这三个优惠是谁先谁后？
- 为什么满减减了 20，九折却只减了 13 而不是 15？
- 我有 1000 积分，为什么只抵了 10 元而不是 1000 元？
- 如果我还领了一张「满 100 打 85 折」的券，能不能一起用？

但**系统必须想清楚这些**，而且必须能在事后回答每一个用户的追问：

> 「客服说我的新客礼没生效，为什么？」
> 「为什么我同事同样的订单比我便宜 2 块钱？」
> 「我明明有 3000 积分，怎么一分都没抵？」

这些问题有一个共同点：**它们问的不是「结果是多少」，而是「结果是怎么来的」。**

这就是本示例要解决的核心问题。它不是一个模式能解决的，需要四个模式各管一段：

| 用户的疑问 | 系统要回答的问题 | 该由谁负责 |
|---|---|---|
| 为什么减了 20 而不是 30？ | 这个折扣**怎么算**？ | 策略模式 |
| 为什么这次只减 50？ | 算出来的钱**要不要再套一层限制**？ | 装饰器模式 |
| 为什么积分没抵？ | 这个折扣**该不该执行**、后面的**还要不要执行**？ | 责任链模式 |
| 这些规则**从哪来**？改规则要改代码吗？ | 配置文本怎么变成对象？ | 工厂模式 |

四个问题彼此独立：改「满 100 减 20」为「满 200 减 30」，不该影响「满减和折扣券互斥」；
改「互斥规则」，也不该影响「单笔最多减 50」的风控。

**四个独立的变更原因，就该有四个独立的抽象。** 这是整篇文档的主线。

### 1.2 专业版：四个模式各自回答一个问题

GoF 二十三种模式里，这四种经常被一起使用，因为它们恰好覆盖了「一次计算」的四个正交维度：

| 模式 | GoF 的定义要点 | 在本示例中的具体形态 | 一句话职责 |
|---|---|---|---|
| 策略 Strategy | 定义一系列算法，把它们封装起来，并使它们可以互相替换 | `DiscountPolicy` 及其三个实现 | **单一折扣怎么算** |
| 装饰器 Decorator | 动态地给一个对象添加一些额外的职责，比继承更灵活 | `CappedPolicy`、`TracePolicy` | **在算法外面套一层横切能力** |
| 责任链 Chain of Responsibility | 使多个对象都有机会处理请求，沿链传递直到被处理 | `DiscountHandler` 及两个子类 | **多步之间按什么顺序、何时跳过、何时终止** |
| 工厂 Factory | 定义创建对象的接口，让子类决定实例化哪个类 | `PolicyFactory`、`PipelineFactory` | **配置文本 → 对象图** |

请注意第三条的措辞：**「直到被处理」**。

这是责任链与「管道」的分界线，也是原项目最大的问题所在 ——
原项目的 `DiscountHandler.apply` 是「无条件执行自己，再无条件调用下一个」，
它没有「跳过」，也没有「终止」。它是管道，不是责任链（详见 §2.4）。

### 1.3 为什么单模式示例不够

如果你只学过策略模式的示例，你看到的多半是这样一个类图：

```text
   Context ──→ Strategy ⟨interface⟩
                  △
       ┌──────────┼──────────┐
  ConcreteA   ConcreteB   ConcreteC
```

这个图里没有回答三个真实问题：

1. **多个策略同时存在时，按什么顺序执行？** 图上只有一个 `Context` 持有一个 `Strategy`。
2. **某个策略这次不适用怎么办？** 图上的 `Context` 会无条件调用 `strategy.execute()`。
3. **策略对象从哪来？** 图上通常是 `new ConcreteA()` 直接写在 `main` 里。

真实业务里，这三个问题一个都躲不开。而它们恰好分别属于责任链、条件与工厂。

**所以「组合示例」的价值不在于展示四个模式，而在于划清四条边界：**

> 策略只管「怎么减」，不管「要不要减」；
> 条件只管「要不要减」，不管「怎么减」；
> 责任链只管「顺序与去留」，不管「减多少」；
> 装饰器只管「外面套什么」，不改「里面怎么算」。

任何一条边界被打破，四个模式就会退化成「四个名字写在同一个类里」。
本文 §4 会用十几个具体的设计决定，把这条边界一寸一寸钉死。

### 1.4 本文的四个角色边界表

先给结论，后文所有代码都是它的展开：

| 角色 | 允许知道什么 | 禁止知道什么 | 判断标准 |
|---|---|---|---|
| `DiscountPolicy` | 当前价格、订单上下文 | 自己在链上的位置、前后有谁、是否被装饰 | 换成另一个策略，链的其余部分**一行不改** |
| `Requirement` | 订单上下文 | 价格、策略、链 | 换成另一个条件，策略的实现**一行不改** |
| `DiscountHandler` | 顺序、后继、决策三态 | 具体折扣怎么算 | 增减一个节点，其他节点**一行不改** |
| `CappedPolicy` | 被装饰者的返回值 | 被装饰者的内部算法 | 去掉装饰，被装饰者**行为完全不变** |
| `PolicyFactory` | 配置文本、参数格式 | 链的顺序、条件 | 新增一种折扣，链的装配代码**一行不改** |

最后再强调一次本文的态度：

> **模式不是目标，边界才是。** 一个类如果同时负责「判断该不该执行」和「算出减多少」，
> 它叫什么名字都不重要 —— 它已经不是一个策略了，它是一个被压扁的流程。

---

## 2. 原始实现：9 个文件 217 行，与它的七个问题

### 2.1 全貌：9 个文件，217 行，11 行输出

原项目在 `src/CombineApplicationExamples/`，一共 9 个文件：

| 文件 | 行数 | 职责 |
|---|---:|---|
| `OrderService.java` | 48 | 组装链、执行、打印前后价格 |
| `DiscountStrategyFactory.java` | 26 | `switch (type)` 创建策略 |
| `LoggingDiscountDecorator.java` | 26 | 前后各打一行日志 |
| `handler/DiscountHandler.java` | 28 | 责任链抽象类 |
| `handler/StrategyHandler.java` | 23 | 把策略包装成链节点 |
| `strategy/DiscountStrategy.java` | 14 | 策略接口 |
| `strategy/FullReductionStrategy.java` | 19 | 满 100 减 20（写死） |
| `strategy/PercentageOffStrategy.java` | 17 | 打九折（写死） |
| `strategy/PointsDeductionStrategy.java` | 16 | 固定减 10（写死） |
| **合计** | **217** | 9 个文件 |

运行入口是 `OrderService.main`，它调用：

```java
service.placeOrder(150.0, Arrays.asList("full", "percent", "points"));
```

**真实输出 11 行（411 字节，逐字记录，未做任何美化）：**

```text
原始价格：150.0
【日志】执行策略前价格：150.0
【满减】满100减20，已减20
【日志】执行策略后价格：130.0
【日志】执行策略前价格：130.0
【打折】打九折，已减：13.0
【日志】执行策略后价格：117.0
【日志】执行策略前价格：117.0
【积分抵扣】已减10
【日志】执行策略后价格：107.0
最终支付金额：107.0
```

**这 11 行是本文所有对比的原点。** 新实现的第一段 Demo 会逐字复现它（见 §6.1），
然后再指出：这 11 行里有 4 个数字、6 行日志、0 个策略名、0 个原因。

### 2.2 逐字源码：接口与三个策略

**`strategy/DiscountStrategy.java`（14 行）**

```java
public interface DiscountStrategy {
    double applyDiscount(double originalPrice);
}
```

一个方法。注意两点：参数是 `double`，返回值是**折后价**。

**`strategy/FullReductionStrategy.java`（19 行）**

```java
public class FullReductionStrategy implements DiscountStrategy {
    @Override
    public double applyDiscount(double originalPrice) {
        if (originalPrice >= 100) {
            System.out.println("【满减】满100减20，已减20");
            return originalPrice - 20;
        }
        return originalPrice;
    }
}
```

门槛 100、额度 20 都写死在方法体里。运营想配「满 200 减 30」，只能改代码。

**`strategy/PercentageOffStrategy.java`（17 行）**

```java
public class PercentageOffStrategy implements DiscountStrategy {
    @Override
    public double applyDiscount(double originalPrice) {
        double discount = originalPrice * 0.1;
        System.out.println("【打折】打九折，已减：" + discount);
        return originalPrice - discount;
    }
}
```

九折写死。折扣额用 `double` 算。

**`strategy/PointsDeductionStrategy.java`（16 行）**

```java
public class PointsDeductionStrategy implements DiscountStrategy {
    @Override
    public double applyDiscount(double originalPrice) {
        System.out.println("【积分抵扣】已减10");
        return originalPrice - 10;
    }
}
```

**它连「用户有多少积分」都不问，固定减 10 元。**

### 2.3 逐字源码：工厂、装饰器、责任链、服务

**`DiscountStrategyFactory.java`（26 行）**

```java
public class DiscountStrategyFactory {
    public static DiscountStrategy getStrategy(String type) {
        return switch (type) {
            case "full" -> new FullReductionStrategy();
            case "percent" -> new PercentageOffStrategy();
            case "points" -> new PointsDeductionStrategy();
            default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
        };
    }
}
```

**`LoggingDiscountDecorator.java`（26 行）**

```java
public class LoggingDiscountDecorator implements DiscountStrategy {
    private final DiscountStrategy delegate;

    public LoggingDiscountDecorator(DiscountStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public double applyDiscount(double originalPrice) {
        System.out.println("【日志】执行策略前价格：" + originalPrice);
        double result = delegate.applyDiscount(originalPrice);
        System.out.println("【日志】执行策略后价格：" + result);
        return result;
    }
}
```

**`handler/DiscountHandler.java`（28 行）**

```java
public abstract class DiscountHandler {
    protected DiscountHandler next;

    public void setNext(DiscountHandler next) {
        this.next = next;
    }

    public double apply(double price) {
        double discounted = handle(price);
        if (next != null) {
            return next.apply(discounted);
        }
        return discounted;
    }

    protected abstract double handle(double price);
}
```

**这就是「两个无条件」**：`handle` 无条件执行，`next.apply` 无条件下传。

**`handler/StrategyHandler.java`（23 行）**

```java
public class StrategyHandler extends DiscountHandler {
    private final DiscountStrategy strategy;

    public StrategyHandler(DiscountStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    protected double handle(double price) {
        return strategy.applyDiscount(price);
    }
}
```

**`OrderService.java`（48 行）**

```java
public class OrderService {
    public static void main(String[] args) {
        OrderService service = new OrderService();
        service.placeOrder(150.0, Arrays.asList("full", "percent", "points"));
    }

    public void placeOrder(double originalPrice, List<String> discountTypes) {
        DiscountHandler head = null;
        DiscountHandler current = null;

        for (String type : discountTypes) {
            DiscountStrategy strategy = DiscountStrategyFactory.getStrategy(type);
            DiscountStrategy enhanced = new LoggingDiscountDecorator(strategy);
            StrategyHandler handler = new StrategyHandler(enhanced);

            if (head == null) {
                head = handler;
            } else {
                current.setNext(handler);
            }
            current = handler;
        }

        System.out.println("原始价格：" + originalPrice);
        double finalPrice = head != null ? head.apply(originalPrice) : originalPrice;
        System.out.println("最终支付金额：" + finalPrice);
    }
}
```

### 2.4 问题一：这不是责任链，是管道

让我们把 `apply` 的逻辑用文字展开：

```text
节点处理请求 = 无条件执行 handle(price)
             + 无条件把结果传给 next
             + 永远返回最后一个节点的结果
```

**「跳过」和「终止」这两个责任链最核心的能力，在这里都不存在。**

对比一下 GoF 的定义：「沿链传递请求，**直到有一个对象处理它为止**」。
原项目的链永远不会「为止」：三个节点全跑，跑完为止。

这不是命名之争。看两个真实业务场景：

| 业务规则 | 责任链应该怎么做 | 原项目能做到吗 |
|---|---|---|
| 新客礼只对新客生效 | 节点返回「跳过」，链继续 | ❌ 只要在链上就一定执行 |
| 用了满减就不能再用折扣券 | 节点返回「终止」，后面的全部不执行 | ❌ 没有任何终止机制 |

原项目的「链」只有一种行为：**全员执行**。这在数学上等价于一个 `for` 循环：

```java
double price = originalPrice;
for (String type : discountTypes) {
    price = strategy(type).applyDiscount(price);
}
```

**当一条「链」可以被一个 `for` 循环无差别替代时，它就不是责任链。**
它只是把一个循环拆成了 28 行类文件而已。

### 2.5 问题二：金额用 double，负价与浮点尾巴都是真实输出

`DiscountStrategy.applyDiscount` 的签名是 `double`。这不是风格问题，是正确性问题。

**缺陷 A：11 元订单算出负价**

在 JDK 25 下真实执行（`LegacyOrderService.silentPrice(11.0, "percent", "points")`）：

```text
11.0 → 打九折：11.0 - 11.0 * 0.1 = 9.9
     → 积分固定减 10：9.9 - 10 = -0.09999999999999964
```

**应付金额是负数。** 平台要倒找用户 0.1 元。

**缺陷 B：58.8 元的浮点尾巴**

```text
58.8 → 打九折：58.8 - 5.880000000000001 = 52.919999999999995
     → 积分固定减 10 = 42.919999999999995
```

用户会看到 `42.919999999999995` 这样的价格。任何前端格式化都只能掩盖它，
掩盖之后对账仍然差 0.000000000000005 元 —— 单笔无所谓，一天一千万单就是账目对不上。

**缺陷 C：99.9 元的折扣额本身就有误差**

```text
99.9 * 0.1 = 9.990000000000002
```

`double` 连「减了多少钱」这个中间值都存不准。而原项目的日志里恰好打印了这个值：

```text
【打折】打九折，已减：9.990000000000002
```

**根因**：`double` 是二进制浮点数，`0.1`、`0.9`、`9.9` 在二进制下都是无限循环小数，
只能近似存储。金额必须用 `BigDecimal`（十进制精确表示）。

### 2.6 问题三：策略无条件执行，不看上下文

`PointsDeductionStrategy` 的方法体只有两行：

```java
System.out.println("【积分抵扣】已减10");
return originalPrice - 10;
```

**它问了「用户有多少积分」吗？没有。它问了「价格够不够减」吗？也没有。**

真实执行（`silentPrice(150.0, "points")`，模拟用户 0 积分）：

```text
150.0 → 140.0
```

用户一分积分都没有，平台照样减了 10 元。这不是「优惠」，这是**资损**。

三个策略全都有这个问题：

| 策略 | 应该看什么 | 实际看什么 |
|---|---|---|
| 满减 | 当前价是否达到门槛 | ✅ 看了（唯一一个看了的） |
| 打折 | 价格是否为正 | ❌ 不看，0 元也打折（结果还是 0，无害但无意义） |
| 积分 | 用户可用积分余额 | ❌ 完全不看 |

**为什么策略不看上下文？** 因为接口签名里没有上下文：

```java
double applyDiscount(double originalPrice);   // 只有价格
```

**接口缺参数，实现就只能靠写死。** 这是「签名的表达能力决定了实现的表达能力」的典型例子。

### 2.7 问题四：日志关不掉、不带身份、不成结构

`LoggingDiscountDecorator` 的三行代码里藏着三个问题：

```java
System.out.println("【日志】执行策略前价格：" + originalPrice);
double result = delegate.applyDiscount(originalPrice);
System.out.println("【日志】执行策略后价格：" + result);
```

**问题 A：关不掉。** `System.out.println` 写死在业务方法里。生产环境不想打印？做不到。

**问题 B：不带身份。** 日志里只有价格，没有策略名。三个折扣叠加时输出是：

```text
【日志】执行策略前价格：150.0
【日志】执行策略后价格：130.0
【日志】执行策略前价格：130.0
【日志】执行策略后价格：117.0
【日志】执行策略前价格：117.0
【日志】执行策略后价格：107.0
```

六行日志，**没有一行告诉你哪一行属于满减、哪一行属于打折**。
想按策略统计「本月满减一共减了多少钱」，必须靠价格差反推。

**问题 C：不成结构。** 日志是字符串。想统计「本单用了几种优惠」，
只能解析字符串或者重新算一遍。**信息一旦被格式化成文本，结构化查询就死了。**

### 2.8 问题五：顺序由调用方传参决定，没有任何规则可查

`placeOrder(150.0, ["full", "percent", "points"])` 的结果是 **107.0**。
如果把参数换个顺序：`placeOrder(150.0, ["percent", "full", "points"])`，结果是 **105.0**。

两者都是真实执行结果：

```text
满减 → 打折 → 积分：150 → 130 → 117 → 107
打折 → 满减 → 积分：150 → 135 → 115 → 105
```

**差 2 元。** 原因很简单：满减的门槛看「当时的价」。

- 先减 20 再打九折：`130 × 0.9 = 117`
- 先打九折再减 20：`135 - 20 = 115`

这两种都是合法业务规则，本身没有对错。**问题在于它是隐式的**：

- 顺序写在 `main` 方法的数组字面量里，运营看不到；
- 没有任何地方记录「本单为什么是这个顺序」；
- 改一个数字的位置，结果就变了，而代码评审时没有人会注意到。

### 2.9 问题六：工厂的错误消息不可操作

```java
default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
```

用户（这里是运营配置人员）看到的是：

```text
IllegalArgumentException: 未知的折扣类型: fulll
```

**它告诉了你「错了」，但没说三件最重要的事：**

1. 有哪些合法的类型？（`full` / `percent` / `points`，但它是怎么拼的？）
2. 每个类型要几个参数？
3. 正确写法长什么样？

结果就是：运营改一次配置，问一次开发。**一条不会自我解释的错误消息，等于一个 7×24 的客服工单来源。**

### 2.10 问题七：没有结果对象，只有一前一后两个数字

`placeOrder` 的最后两行：

```java
System.out.println("原始价格：" + originalPrice);
double finalPrice = head != null ? head.apply(originalPrice) : originalPrice;
System.out.println("最终支付金额：" + finalPrice);
```

**这个方法的全部产出是：一个 `double` + 两行打印。**

于是三个角色都只能拿到同一个数字：

| 角色 | 想知道什么 | 实际能拿到什么 |
|---|---|---|
| 用户 | 每项优惠减了多少 | `107.0` |
| 客服 | 为什么某项优惠没生效 | `107.0` |
| 财务 | 每一步之后的余额、优惠占比 | `107.0` |
| 风控 | 哪个策略减得异常多 | `107.0` |

**「减了多少」不是这个方法的返回值，而是日志的副产品。**
要拿到它，调用方必须自己再算一遍，或者去解析那 6 行日志字符串。

### 2.11 七个问题的共同根因

把七个问题排在一起看，会发现它们不是七个独立的 bug，而是**三个结构性缺失**：

```text
缺失一：状态没有地方放
  ├── 用户积分放不进策略参数   → 问题三（无条件执行）
  ├── 每步明细没有对象承接     → 问题七（只有 double）
  └── 顺序与互斥没有地方记录   → 问题五（顺序隐式）

缺失二：决策没有表达能力
  ├── 节点只能"执行"，不能"跳过" → 问题一（管道冒充责任链）
  └── 节点只能"继续"，不能"终止" → 问题一（同上）

缺失三：变化点没有隔离
  ├── 门槛额度写死在策略里     → 问题二之外的参数硬编码
  ├── 日志写死在装饰器里       → 问题四（关不掉）
  └── 创建逻辑写死在 switch 里 → 问题六（消息不可操作）
```

新实现的 20 个文件，就是围绕这三个缺失重建结构：

| 缺失 | 对应新增 | 文件 |
|---|---|---|
| 状态没有地方放 | `OrderContext`（输入事实）+ `ChainState`（运行累加器）+ `DiscountResult`（输出快照） | 3 |
| 决策没有表达能力 | `DiscountDecision` 三态（生效 / 跳过 / 终止） | 1 |
| 变化点没有隔离 | `Requirement`（条件）+ `CappedPolicy`（风控）+ `PolicyFactory`（创建） | 3 |

**注意：新增的类不是「为了凑模式」，每一个都对应上表的一行。**

### 2.12 七个问题的验证方式

本文所有「原项目会算错」的结论都不是推演，而是**实际跑出来的**。验证方式：

1. 把原项目 9 个文件复制到临时目录，用 JDK 25 编译（`javac -encoding UTF-8 -d tmp`）；
2. 执行 `OrderService.main`，得到 §2.1 的 11 行输出（411 字节）；
3. 为拿到「无日志的纯数值」，在 `LegacyOrderService` 里复现同样的算式（`silentPrice`），
   对 11.0 / 58.8 / 150.0 三个价格分别执行；
4. 三个数字与 `BigDecimal` 的精确结果逐位对比，误差来源全部可解释。

**复现命令与哈希见[附录 B](#附录-b-编译运行与验证记录)，缺陷清单见[附录 E](#附录-e-缺陷取证台账)。**

---

## 3. 设计动机：为什么四个模式必须一起用

### 3.1 先看反例：一个方法能不能把四个需求全干了

需求是真实的，只有一条：**下单时按顺序应用若干个折扣，给出应付金额和明细。**

「最省事」的写法是这样的：

```java
public static double checkout(double price, String type,
                              int points, int level,
                              boolean newCustomer, String coupon) {
    // 需求一：三种折扣算法
    switch (type) {
        case "full":    if (price >= 100) price -= 20; break;
        case "percent": price = price * 0.9; break;
        case "points":  price -= 10; break;
        default: throw new IllegalArgumentException("未知类型: " + type);
    }

    // 需求二：单笔最多减 50
    // 需求三：不满 Lv.3 不参与
    // 需求四：满减和折扣券互斥
    // ……
    return price;
}
```

这个方法是 6 个参数、一个 `switch`、若干 `if`。它**能跑**，而且对于「三种折扣、两条规则」这样的小需求，
它甚至是**正确的选择**（见 §3.6）。但它有四个无法回避的后果：

| 后果 | 具体表现 |
|---|---|
| **改一处要读全文** | 运营说「满减门槛改成 200」，开发要在 60 行里找到 `price >= 100` 这一处 |
| **逻辑无法单独测试** | 想测「积分策略在 0 积分时返回什么」，必须构造完整的 6 个参数并跑完整条流程 |
| **规则无法组合** | 「新客 + 满减」「会员日 + 打折」只能靠再写 `if`，组合数是乘积 |
| **错误无法提前** | 配置里写错折扣码，要等用户真下单才抛异常 |

**这不是「代码写得不好看」的问题，而是四个不同方向的变更被焊死在同一段代码里。**

接下来逐个说明：每个模式具体解开了哪一根焊点。

### 3.2 为什么是策略：算法的变化频率和调用方不同

**观察一个事实**：下面三次需求变更，改的是同一个方法吗？

| 变更 | 谁提出 | 影响什么 |
|---|---|---|
| 「九折改成八五折」 | 运营（价格策略） | 一个算式的系数 |
| 「新增一种『满 3 件打 8 折』」 | 运营（商品策略） | 一个新的算法 |
| 「新增『满减』” | 运营（活动） | 一个新的算法 |

如果三种变更都改 `checkout`，那么：

- 每一次变更都要重新回归整条流程；
- 两个运营同时提需求，就会在同一个方法上产生冲突；
- 方法会随着折扣种类线性增长，最终变成一个 500 行的 `switch`。

**策略模式解决的是「算法的种类会增长，但调用方式不变」。**

具体到本示例，它把「怎么算」抽成一个方法：

```java
public interface DiscountPolicy {
    String name();                                              // 我是谁
    BigDecimal discount(BigDecimal price, OrderContext ctx);     // 我能减多少
    default String unavailableReason(BigDecimal price, OrderContext ctx) { ... }  // 我为什么没减
}
```

然后每种算法一个类：

```text
满减：if (price >= 100) 减 20
打折：减 price × (1 - 0.9)
积分：减 min(积分余额, 上限) × 每分价值
```

**关键判断：这里真的需要接口吗？**

如果只有一种折扣，不需要 —— 一个方法就够。
如果折扣种类固定为三种、且永不增加，也不需要 —— `switch` 更短。

**但本示例的前提是**：三个折扣已经存在，且运营随时会加第四、第五种。
所以「新增一种折扣不该修改已有代码」是一条真实的价值，策略模式兑现了它。

### 3.3 为什么是装饰器：横切关注点不属于任何一个算法

需求里有一条：「**每次大促，单个折扣最多减 50 元**，防止配置错误导致资损。」

注意这条规则的特征：**它不属于任何一种折扣。**

- 满减算法不该知道「有个 50 元上限」；
- 打折算法不该知道；
- 积分算法也不该知道。

它属于「风控」，而风控和算法来自不同部门，变化节奏也完全不同：

| | 折扣算法 | 风控上限 |
|---|---|---|
| 谁提需求 | 运营 | 风控 / 财务 |
| 多久变一次 | 每周（换活动） | 每季度（调策略） |
| 改错了谁负责 | 运营 | 风控 |
| 作用范围 | 一种折扣 | **所有**折扣 |

如果把它写进每个算法：

```java
public BigDecimal discount(BigDecimal price, OrderContext ctx) {
    BigDecimal reduction = ...;                  // 算
    return reduction.min(new BigDecimal("50"));  // 截 —— 每个类都要抄一遍
}
```

**抄三遍就会有三种写法**：一个用 `min`，一个用 `if`，一个忘了。这就是「横切逻辑的复制粘贴」。

**装饰器解决的是「在对象外面套一层，接口不变」。**

```java
DiscountPolicy capped = new CappedPolicy(barePolicy, new BigDecimal("50"));
```

`CappedPolicy` 与被装饰者是同一个类型（`DiscountPolicy`），所以：

- 链节点分不清手里拿的是裸策略还是包装过的策略（**对调用方透明**）；
- 可以叠两层：`new CappedPolicy(new CappedPolicy(p, a), b)`；
- 想临时关掉：不包它就行，**不需要改任何算法**。

**为什么不用继承？** `class CappedFullReduction extends FullReduction` 只能解决一种折扣；
三种折扣就要三个子类；再叠「追踪」就要 3×2=6 个子类。**装饰器是「组合优于继承」最典型的一处。**

### 3.4 为什么是责任链：链不是循环

这是本示例最容易含糊的一处，必须说透。

**如果只需要「按顺序全部执行」，一个 `for` 循环就够了，不需要责任链。**

```java
BigDecimal price = original;
for (DiscountPolicy policy : policies) {
    price = price.subtract(policy.discount(price, ctx));
}
```

这段代码只有 3 行，比 20 个文件的链短得多。**那为什么本示例还要用链？**

因为真实需求里有两件事是循环表达不了的：

**需求 A：某个折扣本次不适用，跳过即可。**

```text
用户 Lv.1，链上有「满减（需 Lv.3）」→ 不执行，但后面的积分照常用
```

用循环也能写（`if` 跳过一次迭代），但**「跳过」这件事在循环里是隐式的**：
循环没有返回值告诉调用方「这一步被跳过了、原因是什么」。
本示例的 `Skipped(reason)` 让「跳过」成为**一等公民**，可以记录、可以展示。

**需求 B：某个折扣生效后，后面的全部不许执行。**

```text
用户用了满减 → 与折扣券互斥 → 积分抵扣也不许用了
```

这就是循环**根本表达不了**的东西：需要提前 `break`，而且要知道「这是业务规则要求的停止，不是出错」。

```java
for (DiscountPolicy policy : policies) {
    price = price.subtract(policy.discount(price, ctx));
    if (/* 某条规则要求停止 */) break;   // 但谁来告诉它「要停」？循环不知道
}
```

**责任链的三态决策，就是为了让「继续 / 跳过 / 终止」这三个词在类型上区分开。**

这也是为什么本示例用 `DiscountDecision` 而不是 `boolean`：

| 用 boolean | 用三态 |
|---|---|
| `true` = 生效，`false` = ？ | `Applied` = 生效 |
| 「跳过」和「终止」都是 `false`，无法区分 | `Skipped` = 跳过（继续） |
| 调用方无法知道「后面还有没有执行」 | `Stopped` = 终止（不再执行） |

**一句话总结**：循环表达「都做」，链表达「谁做、谁不做、到谁为止」。

### 3.5 为什么是工厂：把配置错误提前到装配期

需求最后一个特征：**折扣规则来自运营后台的配置文本**，不是写死在代码里。

```text
full:100:20        满 100 减 20
percent:0.9        九折
points:0.01:500    500 分以内，每分抵 1 分钱
```

于是「把文本变成对象」这件事必须有地方做。三种做法：

| 做法 | 缺点 |
|---|---|
| 每个调用的地方 `new` | `new FullReductionPolicy("满100减20", 100, 20)` 散落在 20 个文件里，改构造参数要改 20 处 |
| 解析逻辑写进 `main` | 演示能跑，但链的装配和文本解析混在一起，单元测试没法单独测解析 |
| **工厂** | 一处解析、一处校验、一处报错；调用方只写 `create("full:100:20")` |

**工厂带来的最大价值不是「少写 new」，而是「错误提前」。**

对比两段错误消息：

```text
旧：IllegalArgumentException: 未知的折扣类型: fullx

新：未知的折扣码 [fullx]。
    支持的折扣码：
      full:门槛:额度         满减，例如 full:100:20 表示满 100 减 20
      percent:实付比例       打折，例如 percent:0.9 表示九折（必须在 0 和 1 之间）
      points:每分抵扣:上限   积分抵扣，例如 points:0.01:500 表示 500 分以内、每分抵 1 分钱
```

后者让看到错误的人**不需要再问第二个人**。这条原则在 §4.12 会展开为一个具体的设计决定。

### 3.6 什么时候不需要这四个模式

这是整篇文档最需要诚实的一节。**不是所有订单系统都值得写成 20 个文件。**

以下四种情况，请直接用 §3.1 的那个 `switch` 方法：

#### 情况一：折扣种类固定，且不超过三种

如果业务就是「满减 + 打折 + 积分」三种，三年没变过，未来也不会变 ——
那么策略模式带来的「新增不改旧代码」是**零收益**，而它带来的类数量是实打实的三倍。

**判断标准**：过去一年是否新增过折扣类型？如果没有，别急着抽象。

#### 情况二：没有「跳过」和「终止」的需求

如果所有折扣对所有用户总是生效、且必须全部执行 —— 那么它就是一个管道，
用一个 `List` + `for` 循环就够了（这正是本项目责任链 README 里 `discount/` 包的做法）。

**硬用责任链的后果**：每个节点都要写 `return Applied(...)`，而实际上永远不会返回 `Skipped` / `Stopped`。
**这是为了用模式而用模式。**

#### 情况三：只有一个「横切」规则，而且永远不会增加

如果全系统只有一条「最多减 50」，那直接在算完之后写一行 `reduction.min(cap)` 更清楚。
装饰器的价值在于「多个横切能力可以自由叠加、自由关闭」；
只有一条规则时，它的间接层是纯成本。

#### 情况四：配置永远来自代码，不来自运营后台

工厂的核心价值是「解析 + 校验 + 报错」。如果折扣配置是编译期常量，
那么 `new FullReductionPolicy(...)` 直接写在装配处反而更清晰 —— 编译器已经帮你校验了参数个数和类型。

#### 本示例的取舍

本示例四个都用，是因为需求同时满足：

- ✅ 折扣种类会增长（运营驱动）；
- ✅ 有跳过与终止的真实规则（新客、Lv.3、互斥）；
- ✅ 有两条横切规则（封顶、追踪），且需要能独立开关；
- ✅ 配置来自文本（运营后台）。

**四条全部不满足时，写 20 个类就是过度设计。**
这条判断标准会写进 §8.3「常见误区」的第一条。

---

## 4. 设计推导：从一行算式推到二十个文件

本节按**时间顺序**还原设计过程：每一小节先提出一个问题，再给出一个决定，
最后说明「另一种做法为什么被否掉」。**这是本文最重要的部分 —— 代码只是这些决定的落地。**

先给出最终的 20 个文件全景，后面逐个解释它是怎么被推出来的：

```text
                    ┌─────────────────────────────┐
                    │  Client（10 段 Demo）        │
                    └──────────────┬──────────────┘
                                   │ 只写"要什么"
                    ┌──────────────▼──────────────┐
                    │  PipelineFactory（Builder）  │  配置文本 → 对象图
                    │  PolicyFactory（注册表）     │
                    └──────────────┬──────────────┘
                                   │ 组装
          ┌────────────────────────▼────────────────────────┐
          │  DiscountPipeline（执行器）                      │
          │    └── DiscountHandler 链                        │
          │          ├── PolicyHandler                       │
          │          └── ExclusiveGroupHandler               │
          └────────────────────────┬────────────────────────┘
                                   │ 每个节点持有
                    ┌──────────────▼──────────────┐
                    │  DiscountPolicy（策略）      │
                    │    ├── FullReductionPolicy   │
                    │    ├── PercentagePolicy      │
                    │    └── PointsDeductionPolicy │
                    │    └── 被装饰：CappedPolicy  │
                    │              TracePolicy     │
                    └─────────────────────────────┘

  横切支撑：OrderContext（输入） ChainState（累加） AuditTrail（账本）
            DiscountDecision（三态） Requirement（条件） DiscountResult（快照）
```

### 4.1 决定一：策略返回「减免额」还是「折后价」

**问题**：`discount` 方法的返回值应该是什么？

原项目返回**折后价**：

```java
double applyDiscount(double originalPrice);   // 返回 100，意思是"打完折是 100"
```

新实现返回**减免额**：

```java
BigDecimal discount(BigDecimal price, OrderContext ctx);   // 返回 20，意思是"减了 20"
```

两种都能算对，但组合场景下的代价完全不同：

| 要做的事 | 返回折后价 | 返回减免额 |
|---|---|---|
| 明细里写「减了多少」 | 得用前价减后价，还要猜是谁减的 | 直接就是返回值 |
| 给单个策略封顶 50 | 要反算：前价 - 50 才是新价，且要防负数 | `min(减免, 50)` 一行 |
| 判断「这一步有没有生效」 | 比较前后价是否相等 | 看返回值是否大于 0 |
| 累加总优惠 | 要保存每一步的前后价再相加 | 直接相加 |
| 保证折后价不为负 | 每个策略各自判断 | **契约层面就不可能为负** |

**决定：返回减免额。**

**最有说服力的是最后一行。** 返回折后价时，「不能为负」是一条**每个策略都要自己遵守的纪律**；
返回减免额并把契约定为 `0 ≤ 减免 ≤ 当前价`，负价就成了**类型上不可能发生的事**。

原项目的 `-0.09999999999999964` 之所以能出现，正是因为「折后价」这个返回值本身没有约束 ——
它可以是任何 `double`。

### 4.2 决定二：为什么需要 `OrderContext`

**问题**：积分抵扣要看「用户有多少积分」，等级折扣要看「用户几级」。这些信息放哪？

原项目的接口只有一个价格参数，于是积分策略只能写死减 10 元（§2.6）。

三种做法：

| 做法 | 问题 |
|---|---|
| 给 `discount` 加参数：`discount(price, points, level, isNew, coupon, memberDay)` | 每加一个字段就要改接口，所有实现跟着改 |
| `Map<String, Object> context` | 拼错 key 编译器不管；每个取值都要强转；读代码不知道有哪些 key |
| **`OrderContext` 值对象** | 字段显式、类型安全、IDE 可补全、重构可改名 |

**决定：显式的不可变值对象 + Builder。**

两个细节值得单独说：

**细节一：为什么 `couponCode` 允许空串而不是 `null`？**

空串表示「没有填券」这个**已知状态**；`null` 表示「不知道有没有」。业务上不存在「不知道」，
所以构造时把 `null` 统一收敛成空串：

```java
public Builder coupon(String code) {
    this.couponCode = code == null ? "" : code.trim();
    return this;
}
```

这样所有调用方永远不需要写 `if (coupon != null)`。这个做法在项目里叫 **null 收敛**。

**细节二：为什么用 Builder 而不是 `with` 链？**

解释器模式的 `Context` 用 `with(name, value)` 返回新实例，因为它的字段本质是一个 Map。
但 `OrderContext` 有 6 个**类型各不相同**的字段，如果也用 `withX().withY()`：

- 每加一个字段要多一个方法；
- 每加一个字段还要多一个复制构造函数；
- 6 个字段会产生 21 种组合方法。

Builder 让「一次设置多个字段，最后一次性校验」成为可能，
也避免了「构造到一半的对象」被传出去。

### 4.3 决定三：把契约写成 `0 ≤ 减免 ≤ 当前价`

**问题**：怎么保证任何策略、任何组合都不会算出负数？

**决定：把契约写成代码，让每个策略最后都过同一道闸门。**

```java
static BigDecimal legal(BigDecimal reduction, BigDecimal price) {
    BigDecimal rounded = money(reduction);       // 收敛到分
    if (rounded.signum() <= 0) {
        return ZERO;                             // 负数 → 0
    }
    return rounded.min(price);                   // 超过当前价 → 截到当前价
}
```

这个 11 行的方法同时处理三种情况：

| 情况 | 处理 | 真实来源 |
|---|---|---|
| 算出来是负数 | 当成 0 | 积分抵扣超过剩余价 |
| 算出来超过当前价 | 截到当前价 | 满减 20 但价格只剩 10 |
| 小数位超过 2 位 | 四舍五入到分 | 九折产生的 10.999… |

**关键点：这不是「防御性编程」，而是「契约的唯一实现」。**

三个策略都调用它，所以「负价」在系统里只有**一个**可能出错的地方，而不是三个。
如果每个策略自己写 `if (reduction < 0) reduction = 0;`，三份代码就会在半年后长出三种行为。

**为什么不抛异常？** 因为「减免额被截断」是**正常业务**，不是错误：
用户只有 100 积分（抵 10 元），但价格已经只剩 5 元 —— 抵 5 元是正确结果，不该让订单失败。

### 4.4 决定四：为什么决策是三态，而不是 boolean

**问题**：节点执行完之后，怎么告诉链「继续 / 不继续」？

`boolean` 只能表达两个状态，但业务需要三个：

```text
生效（减了钱）   → 继续
跳过（不适用）   → 继续
终止（到此为止） → 不再继续
```

**决定：sealed interface + 三个 record。**

```java
public sealed interface DiscountDecision {
    record Applied(BigDecimal amount) implements DiscountDecision { ... }
    record Skipped(String reason)     implements DiscountDecision { ... }
    record Stopped(String reason)     implements DiscountDecision { ... }
}
```

三个实现各自带的信息也不同：

| 类型 | 携带什么 | 为什么必须携带 |
|---|---|---|
| `Applied` | 金额 | 审计要显示「减了 20」；装饰器要对金额做文章 |
| `Skipped` | 原因 | 客服要能回答「为什么我的新客礼没生效」 |
| `Stopped` | 原因 | 用户要能理解「为什么后面的积分没用上」 |

**为什么不用枚举 + 一个 `reason` 字段？**

那会变成「一个枚举值 + 一个可能为 null 的金额 + 一个可能为 null 的原因」——
三个字段的有效组合只有三种，但类型允许 2×N×M 种。**用 sealed 让非法状态无法构造。**

**为什么 record 的紧凑构造器要校验原因为空就抛异常？**

```java
public Skipped {
    if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("跳过必须给出原因：否则审计轨迹只是一串 false");
    }
}
```

因为一条没有原因的「跳过」记录，对排查问题**毫无价值**。
让它在构造时就失败，比让它悄悄出现在日志里好。

### 4.5 决定五：为什么把「条件」从「策略」里拆出来

**问题**：新客礼只对新客、满减要 Lv.3 以上、会员日折扣只在会员日 —— 这些规则放哪？

三种做法：

| 做法 | 后果 |
|---|---|
| 写进策略 | `FullReductionPolicy`、`NewCustomerFullReductionPolicy`、`MemberFullReductionPolicy`、`MemberDayFullReductionPolicy` —— 四个几乎一样的类，只差一行判断 |
| 写进链节点 | 节点同时负责「判断」和「调用策略」，节点变成第二个策略 |
| **独立的 `Requirement`** | 策略只算钱，条件只判断，链节点把它们组合起来 |

**决定：独立的 `Requirement` 接口。**

```java
@FunctionalInterface
public interface Requirement {
    boolean test(OrderContext context);
    default String describe() { return "自定义条件"; }
}
```

拆开之后，组合数从「策略数 × 条件数 **个类**」变成「策略数 × 条件数 **种组合**」：

```java
new PolicyHandler("① 满减", new FullReductionPolicy(...), Requirement.minLevel(3));
new PolicyHandler("② 满减", new FullReductionPolicy(...), Requirement.newCustomerOnly());
```

**同一个满减算法，配两个条件，就是两条业务规则。**

**为什么不用 `Predicate<OrderContext>`？**

功能上完全等价，但 `Requirement` 多了一个东西：**可读的名称**。

```text
用 Predicate：  跳过：false
用 Requirement：跳过：不满足条件：需 Lv.3 及以上
```

审计轨迹里需要写清楚原因，`Predicate` 给不出来。**这是「日志可读性」倒逼「接口设计」的例子。**

**为什么提供静态工厂而不是让调用方写 lambda？**

`Requirement.minLevel(3)` 比 `ctx -> ctx.userLevel() >= 3` 更容易在代码评审时被理解，
而且「等级比较」这个逻辑集中在一处 —— 将来改成「等级或成长值」，只改一个地方。

### 4.6 决定六：为什么 `ChainState` 可变，而 `OrderContext` 不可变

**问题**：链执行过程中，价格会一路减少、互斥组会被占用、轨迹会变长。这些状态放哪？

一个直觉的答案是「都放进 `OrderContext`」。**这是错的。**

两者的角色完全不同：

| | `OrderContext` | `ChainState` |
|---|---|---|
| 是什么 | **输入事实**：订单金额、用户等级、积分余额 | **运行累加器**：当前价格、已用组、轨迹 |
| 该不该变 | 不该。它描述「订单是什么」 | 必须变。它描述「算到哪了」 |
| 谁写 | 调用方构造后冻结 | 每个链节点写 |
| 如果可变 | 前一个节点改了积分，后面看到的就不是原始订单 | 每个节点都要返回新状态，链变成返回值接力 |

**决定：`OrderContext` 不可变（final 字段 + Builder），`ChainState` 可变。**

```java
public final class ChainState {
    private BigDecimal price;                                  // 会变
    private final AuditTrail trail = new AuditTrail();          // 会变（内部 List）
    private final Set<String> usedGroups = new LinkedHashSet<>(); // 会变
    private boolean stopped;                                   // 会变
}
```

**「不可变」不是教条，它只适用于那些不该被改的东西。** 把累加器也做成不可变
（每步 `return` 一个新 `ChainState`）会让链的执行变成一串返回值传递，
可读性反而更差，而且每步都要复制轨迹列表，复杂度 O(n²)。

**另一个细节：为什么用 `LinkedHashSet` 存互斥组？**

只为一件事：打印「已占用的组」时顺序稳定，Demo 输出可复现。
`HashSet` 的迭代顺序在同一 JDK 内虽然稳定，但不该依赖它 ——
这条纪律在 §4.12 会再次出现（`LinkedHashMap` vs `Map.of`）。

### 4.7 决定七：为什么 `process` 必须是 `final`

**问题**：`DiscountHandler` 的模板方法 `process`，要不要允许子类重写？

**决定：`final`。子类只能实现 `decide`。**

```java
public final void process(ChainState state, OrderContext context) {
    if (state.stopped()) { return; }
    BigDecimal before = state.price();
    DiscountDecision decision = decide(before, context, state);
    if (decision instanceof DiscountDecision.Applied applied) {
        state.reduce(applied.amount());
    }
    state.trail().record(name, policyName(), decision, before, state.price());
    if (!decision.continues()) { state.stop(); return; }
    if (next != null) { next.process(state, context); }
}
```

**为什么必须锁住？** 一旦允许重写，就会有人写出：

```java
@Override
public void process(ChainState state, OrderContext context) {
    decide(state.price(), context, state);    // 处理完，忘了往下传
}
```

链在最难排查的地方断掉：**上游以为请求已送达，下游根本没收到。**
而且这种 bug 不会抛异常，只会表现为「某个折扣偶尔不生效」。

**模板方法的意义就是把流程锁在基类，把判断留给子类。**

四个步骤的顺序也是有讲究的：

| 步骤 | 为什么在这个位置 |
|---|---|
| 先看 `stopped()` | 终止后不再问任何节点，**连轨迹也不记** —— 那些节点确实没执行，记「未执行」只会让轨迹变长 |
| `before = state.price()` | 记录的是「决策前」的价格，必须在 `reduce` 之前取 |
| 先 `reduce` 再 `record` | 轨迹里要写「决策后」的价格，所以扣减必须发生在记录之前 |
| 决策决定去留 | `Stopped` 标记终止并 `return`；`Skipped` / `Applied` 继续 |

**注意 `decide` 的参数里有 `ChainState`，但它只允许读。** 唯一的写操作（`reduce`）
由基类根据 `Applied` 完成。这保证「谁改了价格」永远能追溯到一条记录。

### 4.8 决定八：装饰器改不改名字

**问题**：`CappedPolicy` 和 `TracePolicy` 都包了一层，它们的 `name()` 应该返回什么？

两个装饰器给出了**相反**的答案，而这个反差是本节的核心：

| 装饰器 | 是否改变 `name()` | 理由 |
|---|---|---|
| `CappedPolicy` | **改**：`"封顶 50（九折）"` | 它改变了**计算结果**。如果还叫「九折」，用户看到「减了 50」会以为规则被改了 |
| `TracePolicy` | **不改**：原样返回 `delegate.name()` | 它**不改变任何计算结果**，只是旁观者。观测手段不该混进业务名称 |

**决定：装饰器该不该改名字，取决于它改不改变行为。**

这条判断标准可以直接推广：

```text
改变行为的装饰器（封顶、限流、重试）→ 名字要变，让调用方知道"外面还有一层"
只观测的装饰器（日志、计时、监控）    → 名字不变，它是透明的
```

**如果反过来会怎样？**

- 封顶不改名：审计轨迹里出现「九折 减 ￥50.00」，运营会以为九折规则被改了；
- 追踪改名：审计轨迹里出现「追踪（九折） 减 ￥13.00」，
  把观测手段写进了业务名称，下一层再包装就会变成「追踪（封顶 50（九折））」——
  名字变成了包装层的堆栈，而不是业务规则。

### 4.9 决定九：装配顺序必须是「先封顶，再追踪」

**问题**：链节点拿到一个策略时，如果同时要封顶和追踪，谁包谁？

两种顺序：

```java
// 顺序 A：先封顶，再追踪
policy = new CappedPolicy(bare, cap);
policy = new TracePolicy(policy, true, out);

// 顺序 B：先追踪，再封顶
policy = new TracePolicy(bare, true, out);
policy = new CappedPolicy(policy, cap);
```

**决定：顺序 A。**

区别在追踪打出来的数字：

| | 顺序 A（先封顶后追踪） | 顺序 B（先追踪后封顶） |
|---|---|---|
| 追踪看到的值 | 封顶后的**真实**减免额 50 | 封顶前的**理论**值 100 |
| 日志 | `[追踪] 封顶 50（九折） 入 ￥1,000.00 出 ￥950.00 减 ￥50.00` | `[追踪] 九折 入 ￥1,000.00 出 ￥900.00 减 ￥100.00` |
| 问题 | 无 | **日志说减了 100，实际减了 50** |

**观测到的数字必须与真实生效的数字一致，否则日志就是在骗人。**

这不是「两种都行，各有所爱」的问题：顺序 B 的日志会直接导致排查时得出错误结论
（「为什么日志说减 100，用户只少了 50？是不是有 bug？」）。

**同理，`CappedPolicy` 内部的顺序也是固定的：先让被装饰者算，再截到上限。**

```java
BigDecimal inner = delegate.discount(price, context);   // 先算
if (inner.compareTo(cap) <= 0) { return inner; }
return DiscountPolicy.legal(cap, price);                // 再截
```

如果反过来「先把价格截到 cap 再交给策略」，打折策略就会基于被截过的价格计算，
结果完全不同 —— **「封顶」约束的是减免额，不是参与计算的基数。**

### 4.10 决定十：为什么是两个工厂，而不是一个「超级工厂」

**问题**：`PolicyFactory` 和 `PipelineFactory` 能不能合成一个？

它们的分工是：

| | `PolicyFactory` | `PipelineFactory` |
|---|---|---|
| 输入 | 一个字符串 `"full:100:20"` | 若干「阶段」配置 |
| 输出 | **一个**策略对象 | **一条**链 + 执行器 |
| 回答 | 「这种折扣怎么算」 | 「这些折扣按什么顺序串起来」 |
| 变化原因 | 新增一种折扣算法 | 调整叠加顺序、互斥关系、上限 |
| 谁提需求 | 开发 | 运营 |

**决定：拆成两个。**

**理由不是「职责单一」这种口号，而是「协作冲突」这个具体后果：**

- 把两者合成一个「超级工厂」，就会导致「运营只想调顺序」和「开发新增算法」
  改同一个文件 —— 代码评审互相阻塞，合并冲突天天发生；
- **工厂的粒度应该跟「谁提需求」对齐。**

**两层工厂的调用关系也体现了这个分工：**

```java
// PipelineFactory 内部逐个调用 PolicyFactory —— 上层工厂组合下层工厂
DiscountPolicy policy = PolicyFactory.create(step.policySpec());
if (cap != null)   policy = new CappedPolicy(policy, cap);
if (trace)         policy = new TracePolicy(policy, true, traceOut, "    ");
```

`PipelineFactory` 不需要知道「满减怎么算」，它只知道「`full:100:20` 能变成一个策略」。

### 4.11 决定十一：为什么用 Builder，而不是一个字符串 DSL

**问题**：一条链的配置怎么表达？`PipelineFactory` 需要接受「节点名 + 折扣配置 + 互斥组 + 条件」四样东西。

一个看似更「精简」的做法是压成一个字符串：

```text
"@券|full:100:20|new"
```

**决定：拒绝 DSL，改用 Builder。**

```java
PipelineFactory.builder()
        .stage("满减", "full:100:20")
        .exclusiveStage("折扣券", "券", "percent:0.9")
        .stage("积分", "points:0.01:500")
        .cap("50")
        .trace(true)
        .build();
```

**为什么拒绝 DSL：**

| 问题 | DSL | Builder |
|---|---|---|
| 拼错一个竖线 | 运行期异常，错误位置在解析器里，与业务无关 | 编译期就报错 |
| IDE 补全 | 没有 | 有 |
| 参数含义 | 要靠文档 | 方法名 + 参数名 |
| 加一个配置项 | 改解析器，且旧配置可能不兼容 | 加一个方法 |

**「解析代码会比业务代码还长」** 是把配置压成字符串的典型代价。
Builder 让配置在**编译期**就被类型检查，也让调用处读起来像一句配置说明。

**为什么装饰器和条件也在 Builder 里装配，而不是让调用方自己包？**

三种做法：

| 做法 | 问题 |
|---|---|
| 调用方 `new CappedPolicy(new TracePolicy(new FullReductionPolicy(...)))` | 每处调用都要写一层括号，且容易漏掉封顶 |
| 每个策略自己支持 `cap` 参数 | 策略类被「风控」「观测」污染 |
| **工厂统一装配** | 调用方只说「我要封顶 50、要追踪」，包几层、按什么顺序包由工厂决定 |

**决定：工厂统一装配。**

这是「把复杂度锁在一个地方」的取舍：调用方获得了极简的 API，
代价是工厂里多了 20 行装配代码 —— 而那 20 行只需要被审查一次。

### 4.12 决定十二：为什么用 `LinkedHashMap` 而不是 `Map.of`

**问题**：`PolicyFactory` 的注册表怎么存？

```java
private static final Map<String, Creator> REGISTRY;
static {
    Map<String, Creator> map = new LinkedHashMap<>();
    map.put("full", PolicyFactory::fullReduction);
    map.put("percent", PolicyFactory::percentage);
    map.put("points", PolicyFactory::points);
    REGISTRY = Collections.unmodifiableMap(map);
}
```

**决定：`LinkedHashMap`。**

`Map.of` 更短，但它的**迭代顺序是未指定的**（JDK 文档明确说明：顺序可能随 JVM 启动而变）。

本类里真正受影响的是 `supportedCodes()`：它直接返回 `REGISTRY.keySet()`。
调用方拿到的是一个**有序集合**，而不是「三个码，顺序随便」。

（`helpText()` 目前是硬编码文本，不受注册表实现影响。
但正因为它手写，与注册表之间的一致性只能靠人工维护 —— 见 §6.11。）

只要这个顺序不固定，任何基于它生成的帮助表格、下拉框、CLI 列表
都无法逐字节复现。

**这是本项目的一条硬规则：凡是会出现在输出里的集合，都必须用有序实现。**

同一条规则还出现在：

| 位置 | 用了什么 | 如果用无序实现 |
|---|---|---|
| `PolicyFactory.REGISTRY` | `LinkedHashMap` | `supportedCodes()` 的顺序会变 |
| `ChainState.usedGroups` | `LinkedHashSet` | `互斥组 [券]` 的顺序会变 |
| `AuditTrail.entries` | `ArrayList` | 轨迹顺序会乱（更严重） |

### 4.13 决定十三：为什么保留 `LegacyOrderService`

**问题**：既然旧实现有七个问题，为什么还要在新项目里复刻一份？

**决定：保留，标 `@Deprecated`，只在 Demo 1、3、4 里调用。**

三个理由：

**理由一：对比需要基线。** 「旧实现会算错」是断言；「算出 `-0.09999999999999964`」是证据。
Demo 1 会逐字复现旧实现的 11 行输出，读者可以自己核对。

**理由二：证明新实现没改变正确的结果。** 150 元那笔订单，新旧都算出 107，
这证明重构没有引入回归 —— 只是让过程变得可解释。

**理由三：`@Deprecated` 是给读者的视觉标记。** IDE 会在所有调用点画上删除线，
**被删除线的代码，读者一眼就知道「这是反面教材」**，不需要先读三百行注释。

**为什么用 `silentPrice` 单独做一个不打印的版本？**

因为 Demo 3、4 需要拿到旧实现的**数值**做对照，但不希望旧实现的 6 行日志污染这部分的输出。
`silentPrice` 用完全相同的算式，只是不创建任何会打印的策略对象。

> 实现细节：`silentPrice` 初版是通过 `create(type)` 创建策略的，结果策略内部有 `println`，
> 输出被污染。修正方式是**内联旧算式**，不经过任何会打印的策略对象。

### 4.14 决定十四：为什么结果对象是不可变的

**问题**：`calculate` 的返回值应该是一个 `double`，还是一个结果对象？

**决定：`DiscountResult`（record + 集合冻结）。**

```java
public record DiscountResult(BigDecimal originalPrice, BigDecimal finalPrice,
                             List<AuditTrail.Entry> entries, boolean stopped,
                             Set<String> usedGroups) {
    public DiscountResult {
        entries = List.copyOf(entries);
        usedGroups = Set.copyOf(usedGroups);
    }
}
```

**为什么 `List.copyOf` / `Set.copyOf` 不可省略？**

record 的字段是 `final`，但**引用的集合可以是可变的**：

```java
List<Entry> list = new ArrayList<>();
DiscountResult result = new DiscountResult(price, finalPrice, list, false, Set.of());
list.add(entry);          // result.entries() 悄悄多了一条！
```

**这比字段可变更隐蔽** —— 字段不能重新赋值，但内容可以变。`copyOf` 做了两件事：
复制一份，并且拒绝任何 `null` 元素。

**为什么保留 `stopped` 和 `usedGroups`？**

它们是**解释性信息**，不是计算结果：

- `stopped` 告诉调用方「后面还有节点没执行」——对「为什么我的积分没用上」至关重要；
- `usedGroups` 让订单服务知道哪些互斥组被占用，下次用户改选优惠时可以提示冲突。

**注意 `totalReduction()` 是方法而不是字段。** 它是派生值（原价减应付），
存一份就可能与算出来的不一致 —— **「两个真相」是账目 bug 的常见来源，派生的东西永远现算。**

### 4.15 决定十五：为什么「组已占用」的判断要放在「条件判断」之前

**问题**：`ExclusiveGroupHandler.decide` 里有两个判断，谁先谁后？

```java
if (state.groupUsed(group)) {
    return new DiscountDecision.Stopped("同组「" + group + "」已被占用，后续优惠不再生效");
}
if (!requirement.test(context)) {
    return new DiscountDecision.Skipped("不满足条件：" + requirement.describe());
}
```

**决定：先判组，再判条件。**

**理由是可解释性，不是性能。** 假设用户是 Lv.1，而「券」组已经被占用，两种顺序给出两种原因：

| 顺序 | 输出的原因 | 用户看到后会发生什么 |
|---|---|---|
| 先判组 | `同组「券」已被占用，后续优惠不再生效` | 真实的障碍：换一张券或者取消满减 |
| 先判条件 | `不满足条件：需 Lv.3 及以上` | **次要原因**：用户升到 Lv.3 后会发现还是不能用，更困惑 |

**报告第一个真实障碍，是错误信息设计的基本要求。**

**同一条原则还体现在 `PolicyHandler.decide` 的分支顺序：**

```text
条件不满足 → Skipped（原因来自 Requirement）
条件满足但算出 0 → Skipped（原因来自策略的 unavailableReason）
算出大于 0 → Applied
```

「算出来是 0」的原因由**策略自己**解释（`unavailableReason`），而不是节点猜一个「策略未生效」。
因为最了解规则的，永远是这个规则自己。

### 4.16 二十个文件的分层账

把上述 15 个决定落到文件上，就得到最终的 20 个文件。按职责分四层：

**第一层：数据与契约（6 个）**

| 文件 | 行数 | 一句话 |
|---|---:|---|
| `DiscountPolicy.java` | 165 | 策略接口 + 三个静态工具（校验、收敛、夹逼） |
| `OrderContext.java` | 252 | 不可变输入事实 + Builder |
| `Requirement.java` | 209 | 条件接口 + 6 个静态工厂（含 and / or） |
| `DiscountDecision.java` | 165 | sealed 三态决策 |
| `AuditTrail.java` | 148 | 结构化账本（可变收集器 + 不可变快照） |
| `ChainState.java` | 147 | 运行累加器（价格 / 组 / 轨迹 / 终止标记） |

**第二层：算法与装饰（5 个）**

| 文件 | 行数 | 一句话 |
|---|---:|---|
| `FullReductionPolicy.java` | 99 | 满减（门槛 + 额度参数化） |
| `PercentagePolicy.java` | 84 | 打折（实付比例） |
| `PointsDeductionPolicy.java` | 108 | 积分（每分价值 + 笔数上限 + 余额） |
| `CappedPolicy.java` | 88 | 装饰器：单步封顶，**改名** |
| `TracePolicy.java` | 141 | 装饰器：追踪，**不改名**，可关闭 |

**第三层：链与执行（5 个）**

| 文件 | 行数 | 一句话 |
|---|---:|---|
| `DiscountHandler.java` | 204 | 链骨架：`final process` + 四校验 `linkTo` |
| `PolicyHandler.java` | 111 | 节点：条件 → 策略 → 三态 |
| `ExclusiveGroupHandler.java` | 138 | 节点：先判组 → 条件 → 策略 |
| `DiscountPipeline.java` | 169 | 执行器：每次新建状态、输出不可变结果 |
| `DiscountResult.java` | 178 | 结果快照 + 两种视图 |

**第四层：装配与演示（4 个）**

| 文件 | 行数 | 一句话 |
|---|---:|---|
| `PolicyFactory.java` | 280 | 配置文本 → 策略（注册表 + 帮助文本） |
| `PipelineFactory.java` | 237 | Builder → 链（装饰、条件、圈码） |
| `LegacyOrderService.java` | 210 | 旧实现复现（@Deprecated） |
| `Client.java` | 414 | 十段 Demo |

**合计：20 个文件，3547 行。**

**这张表就是「为什么不合并」的答案**：每一行都能独立回答「删掉它会缺什么」。
（`Client` 例外：它删掉之后示例就不存在了，但它也不参与任何业务逻辑。）

---

## 5. 代码解析：二十个文件为什么存在

本节逐个文件解析。**每个文件回答四个问题**：

1. **它是什么角色**（一句话）；
2. **关键代码**（真实源码，不是伪代码）；
3. **为什么这样写**（设计决定，附「换一种写法会怎样」）；
4. **它和谁协作**。

**建议的阅读顺序**（不按文件名字母序）：

```text
① 契约层：DiscountPolicy → DiscountDecision → OrderContext → Requirement
② 实现层：FullReductionPolicy → PercentagePolicy → PointsDeductionPolicy
③ 装饰层：CappedPolicy → TracePolicy
④ 运行层：AuditTrail → ChainState → DiscountHandler → PolicyHandler
          → ExclusiveGroupHandler → DiscountPipeline → DiscountResult
⑤ 装配层：PolicyFactory → PipelineFactory
⑥ 对照层：LegacyOrderService
⑦ 演示层：Client
```

先看契约，因为**后面 19 个文件都在兑现契约**。

---

### 5.1 `DiscountPolicy.java`（165 行）：一个接口，三个静态工具

**角色**：抽象策略。只回答「这种折扣能减多少钱」。

**接口本体只有三个方法：**

```java
public interface DiscountPolicy {

    BigDecimal ZERO = new BigDecimal("0.00");

    String name();

    BigDecimal discount(BigDecimal price, OrderContext context);

    default String unavailableReason(BigDecimal price, OrderContext context) {
        return "策略未产生减免";
    }
}
```

**三个静态工具才是这个接口真正的价值：**

```java
static void requireValidPrice(BigDecimal price) {
    if (price == null) {
        throw new IllegalArgumentException("价格不能为 null");
    }
    if (price.signum() < 0) {
        throw new IllegalArgumentException("价格不能为负：" + price.toPlainString());
    }
}

static BigDecimal money(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
}

static BigDecimal legal(BigDecimal reduction, BigDecimal price) {
    BigDecimal rounded = money(reduction);
    if (rounded.signum() <= 0) {
        return ZERO;
    }
    return rounded.min(price);
}
```

**为什么这三个工具放在接口里而不是一个工具类？**

因为它们是**契约的一部分**，不是「顺便用用」的辅助函数：

| 方法 | 属于契约的哪一条 |
|---|---|
| `requireValidPrice` | 「价格必须非负」—— 每个实现的第一行 |
| `money` | 「金额保留 2 位小数」—— 每个实现的最后一步 |
| `legal` | 「减免额在 `[0, price]` 内」—— 每个实现的返回值 |

**契约的表述只应该存在一处。** 如果把它们放进 `PolicyUtils`，读者读完接口仍然不知道
「实现者需要遵守什么」，还得再跳一个文件。

**为什么 `unavailableReason` 是 `default` 而不是抽象方法？**

因为只有「有门槛」的策略才需要解释原因：

| 策略 | 需要覆盖吗 | 原因 |
|---|---|---|
| 满减 | 需要 → `"未满 ￥100.00（当前 ￥80.00）"` | 有门槛，用户想知道差多少 |
| 积分 | 需要 → `"无可用积分（持有 0，单笔上限 1000）"` | 有余额，用户想知道为什么没抵 |
| 打折 | 不需要，用默认的 `"策略未产生减免"` | 只要价格为正就一定有减免，没有「不适用」的情况 |

如果做成抽象方法，打折策略也要写一个永远不会被用到的实现 —— **这是接口强加的无用负担**。

**`ZERO` 为什么是 `new BigDecimal("0.00")` 而不是 `BigDecimal.ZERO`？**

因为 `BigDecimal.ZERO` 的 scale 是 0，`new BigDecimal("0.00")` 的 scale 是 2。
两者 `compareTo` 相等但 `equals` 不等：

```java
BigDecimal.ZERO.equals(new BigDecimal("0.00"))        // false！
new BigDecimal("0.00").equals(new BigDecimal("0.0"))  // false！
```

如果在某些地方返回 `ZERO`、另一些地方返回 `money(0)`，`equals` 比较就会出错。
**全系统统一一个零的写法**，是金额代码最基本的纪律之一。

**与谁协作**：三个实现类、两个装饰器、`PolicyHandler`、`PolicyFactory` 都依赖这个接口。

---

### 5.2 `OrderContext.java`（252 行）：不可变的输入事实

**角色**：折扣计算需要的、不属于「价格」本身的那部分信息。

**字段只有 6 个，全部 `final`：**

```java
private final BigDecimal orderAmount;   // 订单原价
private final int userLevel;           // 会员等级 1-5
private final int availablePoints;     // 可用积分
private final boolean newCustomer;     // 是否新客
private final String couponCode;       // 券码，空串表示无券
private final boolean memberDay;       // 是否会员日
```

**入口只有一个：`builder(orderAmount)`，它承担全部校验：**

```java
public static Builder builder(String orderAmount) {
    if (orderAmount == null || orderAmount.isBlank()) {
        throw new IllegalArgumentException("订单金额不能为空");
    }
    BigDecimal amount;
    try {
        amount = new BigDecimal(orderAmount);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("订单金额不是合法数字：" + orderAmount, e);
    }
    if (amount.signum() <= 0) {
        throw new IllegalArgumentException("订单金额必须为正：" + orderAmount);
    }
    return new Builder(DiscountPolicy.money(amount));
}
```

**为什么订单金额在入口处就 `money()` 收敛到 2 位小数？**

因为它是后面所有计算的基数。如果传进来 `"150.005"`，那么：

- 第一次乘 0.9 之后就已经不是「分」了；
- 每一步的 `legal` 都四舍五入一次，误差会逐步累积；
- 审计轨迹里出现 `￥150.005` 这种不存在的金额。

**在边界处收敛，内部永远是干净的。** 这是一条通用的输入处理原则。

**为什么 `describe()` 的拼接顺序是写死的？**

```java
public String describe() {
    return com.patterns.util.Money.yuan(orderAmount)
            + " / Lv." + userLevel
            + " / 积分 " + availablePoints
            + (newCustomer ? " / 新客" : " / 非新客")
            + (couponCode.isEmpty() ? " / 无券" : " / 券 " + couponCode)
            + (memberDay ? " / 会员日" : " / 非会员日");
}
```

因为演示输出要能**逐字节复现**。这里没有任何集合迭代，字段顺序就是源码顺序，
所以同样的输入永远得到同样的字符串。

**为什么四段布尔值要写成「A / 非 A」而不是省略？**

```text
￥150.00 / Lv.3 / 积分 1000 / 非新客 / 无券 / 非会员日
```

如果把「非新客」「无券」「非会员日」都省略，输出会变成：

```text
￥150.00 / Lv.3 / 积分 1000
```

**读者无法区分「不是新客」和「忘了设置新客」** —— 这正是原项目「日志里没有策略名」
同类的问题：省略掉的信息，恰恰是用来判断状态的信息。

**与谁协作**：所有策略读它，`Requirement` 判断它，`DiscountPipeline` 把它传进 `calculate`。

---

### 5.3 `Requirement.java`（209 行）：条件是一等公民

**角色**：这条折扣对当前订单是否适用。

**接口只有一个抽象方法 + 一个默认描述方法：**

```java
@FunctionalInterface
public interface Requirement {

    boolean test(OrderContext context);

    default String describe() {
        return "自定义条件";
    }
}
```

**五个静态工厂 + 两个组合子：**

```java
static Requirement always()            { ... }   // 无条件
static Requirement minLevel(int level) { ... }   // 需 Lv.N 及以上
static Requirement newCustomerOnly()   { ... }   // 仅限新客
static Requirement withCoupon()        { ... }   // 需持券
static Requirement memberDayOnly()     { ... }   // 仅会员日

default Requirement and(Requirement other) { ... }
default Requirement or(Requirement other)  { ... }
```

**为什么每个工厂都返回匿名类，而不是 lambda？**

因为要覆盖 `describe()`：

```java
static Requirement minLevel(int level) {
    if (level < 1 || level > 5) {
        throw new IllegalArgumentException("最低等级必须在 1-5 之间：" + level);
    }
    return new Requirement() {
        @Override
        public boolean test(OrderContext context) {
            return context.userLevel() >= level;
        }

        @Override
        public String describe() {
            return "需 Lv." + level + " 及以上";
        }
    };
}
```

用 lambda 只能实现 `test`，`describe()` 会退回默认的「自定义条件」——
审计轨迹里就变成：

```text
跳过：不满足条件：自定义条件
```

**这个匿名类是「lambda 表达能力不够」时正确的回退。** 它不是啰嗦，
它是在 lambda 无法承载第二个方法时的标准做法。

**为什么参数校验放在工厂方法里，而不是 `test` 里？**

```java
if (level < 1 || level > 5) {
    throw new IllegalArgumentException("最低等级必须在 1-5 之间：" + level);
}
```

`minLevel(0)` 是**装配期的错误**，不是运行期的业务分支。
放在工厂里，错误在构造链的时候就暴露；放在 `test` 里，要等某个真实订单跑到这个节点才暴露。

**这就是「快速失败」**：错误暴露得越早，修复成本越低。

**`and()` 和 `or()` 为什么要保存 `Requirement self = this;`？**

```java
default Requirement and(Requirement other) {
    Requirement self = this;                  // 必须存一份
    return new Requirement() {
        @Override
        public boolean test(OrderContext context) {
            return self.test(context) && other.test(context);
        }
        ...
    };
}
```

因为匿名类里的 `this` 指向**匿名类自己**，不是外层的 `Requirement`。
不存这一份，`this.test(context)` 就是无限递归。

**为什么 `or()` 的描述要加括号？**

```java
return "(" + self.describe() + " 或 " + other.describe() + ")";
```

因为组合后的条件会被再次组合：

```java
Requirement complex = minLevel(3).and(newCustomerOnly().or(memberDayOnly()));
// describe() → "需 Lv.3 及以上 且 (仅限新客 或 仅会员日)"
```

没有括号的话，描述就变成「A 且 B 或 C」，运算符优先级不明 ——
**人能读懂，但读出来的意思可能是错的。**

**与谁协作**：`PipelineFactory.stage(..., requirement)` 接收它，`PolicyHandler` / `ExclusiveGroupHandler` 调用它。

---

### 5.4 `DiscountDecision.java`（165 行）：三态决策

**角色**：一个链节点对请求做的决定 —— 生效、跳过、终止。

**sealed 接口 + 三个 record（全部嵌套定义）：**

```java
public sealed interface DiscountDecision {

    record Applied(java.math.BigDecimal amount) implements DiscountDecision { ... }

    record Skipped(String reason) implements DiscountDecision { ... }

    record Stopped(String reason) implements DiscountDecision { ... }

    String label();

    boolean continues();

    default String describe() {
        return switch (this) {
            case Applied applied -> "生效 减 " + com.patterns.util.Money.yuan(applied.amount());
            case Skipped skipped -> "跳过：" + skipped.reason();
            case Stopped stopped -> "终止：" + stopped.reason();
        };
    }
}
```

**为什么三个 record 要嵌在接口里，而不是各占一个文件？**

与解释器模式的 `Value` 同理由：三个 record 各自只有一两个字段、两三行校验，
独立成文件会让读者在「决策类型」和「决策实现」之间来回跳。
嵌进来之后，三态的定义与公共契约在同一屏里，`sealed` 的 `permits` 也可以省略。

**`switch (this)` 为什么不需要 `default` 分支？**

因为 `sealed` 保证了只有三个实现，编译器知道所有可能性。
**加 `default` 反而会掩盖「将来新增一种决策忘了处理」的错误。**

**`continues()` 的返回值为什么是 `Applied` 和 `Skipped` 都为 true？**

这是三态设计的核心：

| 决策 | `continues()` | 语义 |
|---|---|---|
| `Applied` | true | 我减了钱，后面的继续 |
| `Skipped` | true | 我不适用，后面的继续 |
| `Stopped` | **false** | 到此为止，后面的不许执行 |

**如果只有 boolean，「我不适用」和「不许别人用」就混成同一个 `false`。**
这两种情况在业务上完全是两件事 —— 前者是「这条规则不适用」，后者是「所有规则都不许再生效」。

**为什么 `Applied` 在构造器里就把金额 `money()` 掉？**

```java
public Applied {
    if (amount == null || amount.signum() <= 0) {
        throw new IllegalArgumentException("生效决策必须携带正的减免金额：" + amount);
    }
    amount = DiscountPolicy.money(amount);
}
```

**让非法状态无法构造。** 如果允许 `Applied(-5)`，那么「生效但减了负数」这种荒谬的
记录就会出现在审计轨迹里，而链还会照样往下传。

**与谁协作**：`PolicyHandler` / `ExclusiveGroupHandler` 产生它，`DiscountHandler.process` 消费它，
`AuditTrail.Entry` 保存它。

---

### 5.5 `AuditTrail.java`（148 行）：结构化账本

**角色**：把链上每一步发生了什么记下来。

**核心是一个可变的 `List` + 一个不可变的快照方法：**

```java
public final class AuditTrail {

    private final List<Entry> entries = new ArrayList<>();

    public Entry record(String node, String policy, DiscountDecision decision,
                        BigDecimal before, BigDecimal after) {
        Entry entry = new Entry(entries.size() + 1, node, policy, decision, before, after);
        entries.add(entry);
        return entry;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
```

**为什么这个类是可变类，而项目里其他值对象都是不可变的？**

因为它是一个**构建中的收集器**，不是最终产物：

```text
链执行期间：每步追加一条         → 必须可变（O(1) 追加）
执行结束后：entries() 交出快照   → 不可变（外部改不了）
            ↓
       DiscountResult 再冻结一次
```

这是「构建期可变、发布后不可变」的标准做法 —— `StringBuilder`、`Stream.Builder` 都是这个模式。
**不可变的边界应该在对外暴露处，而不是内部实现里。**

如果把收集器也做成不可变（每次 `add` 复制整个列表），链的执行会变成 O(n²)：

| 节点数 | 复制次数 | 元素复制总量 |
|---|---|---|
| 3 | 3 | 6 |
| 10 | 10 | 55 |
| 100 | 100 | 5050 |

**`entries()` 为什么返回 `new ArrayList<>(entries)` 而不是直接返回内部列表？**

```java
return Collections.unmodifiableList(new ArrayList<>(entries));
```

两层保护缺一不可：

- `new ArrayList<>()` 复制一份 → 后续 `record()` 不会影响已发布的结果；
- `unmodifiableList` → 调用方不能通过这个视图删改元素。

**`Entry` 为什么同时保存前价和后价，而不只保存减免额？**

三个角色要的东西不同：

| 角色 | 要什么 | 用哪个字段 |
|---|---|---|
| 运营 | 这一步减了多少 | `before - after` |
| 财务 | 每一步之后的余额 | `after` |
| 排查 | 顺序变了结果差多少 | `before` 和 `after` 一起看 |

**只存减免额，后价要靠「从头累加」才能得到；一旦中间有跳过的节点，很容易算错。**

**`render()` 为什么用 `DisplayWidth.padRight` 而不是 `String.format("%-16s")`？**

因为中文字符的显示宽度是 2，而 `%-16s` 按**字符数**补齐：

```text
String.format("%-10s", "满减")   → "满减" + 8 个空格   （视觉宽度 4+8=12）
String.format("%-10s", "points") → "points" + 4 个空格 （视觉宽度 10）
```

结果就是中英文混排时列不对齐。`DisplayWidth` 按**显示宽度**计算，才能对齐。

**与谁协作**：`ChainState` 持有它，`DiscountHandler` 写入它，`DiscountResult` 读取并冻结它。

---

### 5.6 `ChainState.java`（147 行）：运行累加器

**角色**：当前价格 + 已用互斥组 + 审计轨迹 + 终止标记。

**四个字段，三个可变：**

```java
private BigDecimal price;                                    // 会变
private final AuditTrail trail = new AuditTrail();            // 内容会变
private final Set<String> usedGroups = new LinkedHashSet<>(); // 内容会变
private boolean stopped;                                     // 会变
```

**为什么要把这四样打包成一个对象？**

因为不打包，`process` 的签名会变成：

```java
process(BigDecimal price, OrderContext ctx, AuditTrail trail,
        Set<String> usedGroups, boolean stopped)
```

**五个参数里四个是「传递中的状态」。** 而且每加一种状态，所有节点的签名都要改。

打包之后：**签名稳定，新增状态不影响任何子类。**

**`reduce()` 里为什么还要再做一次「不低于 0」的兜底？**

```java
public void reduce(BigDecimal amount) {
    BigDecimal next = DiscountPolicy.money(price.subtract(amount));
    this.price = next.signum() < 0 ? DiscountPolicy.ZERO : next;
}
```

理论上策略契约已经保证了 `amount <= price`，但这是**跨类的约定**：

```text
DiscountPolicy 的实现者 ←承诺→ ChainState 的使用者
        ↑                            ↑
    可能由第三个人           可能是半年后改的代码
    在未来新增
```

**跨类约定是最容易在重构中被破坏的东西。** 多一次 `max`，换来的是
「无论哪个策略写错，用户都不会看到负价」。

**注意这不是「重复校验」，而是「防线分层」**：策略层的 `legal` 是**第一道**（业务正确性），
`ChainState.reduce` 是**第二道**（运行时兜底）。两道防线的目的不同。

**`stop()` 为什么不保存终止原因？**

```java
public void stop() {
    this.stopped = true;
}
```

因为原因已经写在最后一条 `AuditTrail.Entry` 里了：

```text
2  ② 折扣券        终止  ￥130.00 → ￥130.00  减 ￥0.00  （同组「券」已被占用，后续优惠不再生效）
```

**重复保存两份状态，是「两处真相」的开始。** 一旦两处不一致，排查时就要先判断哪一处是对的。

**与谁协作**：`DiscountPipeline` 每次计算创建一个，`DiscountHandler` 传递它，`DiscountResult` 读取它。

---

### 5.7 `FullReductionPolicy.java`（99 行）：门槛 + 额度参数化

**角色**：满减。订单金额达到门槛，就减固定金额。

**它是一个 record，全部状态就是三个参数：**

```java
public record FullReductionPolicy(String name, BigDecimal threshold, BigDecimal reduction)
        implements DiscountPolicy {
```

**核心方法只有四行：**

```java
@Override
public BigDecimal discount(BigDecimal price, OrderContext context) {
    DiscountPolicy.requireValidPrice(price);
    if (price.compareTo(threshold) < 0) {
        return ZERO;
    }
    return DiscountPolicy.legal(reduction, price);
}
```

**对比原项目：**

```java
// 原项目：门槛和额度写死
if (originalPrice >= 100) {
    return originalPrice - 20;
}
```

参数化带来的直接收益：`full:300:50`、`full:200:30` 都是同一个类，
**运营改规则不需要发版**。

**为什么记录要拒绝「减免额度 ≥ 门槛」？**

```java
if (reduction.compareTo(threshold) >= 0) {
    throw new IllegalArgumentException(
            "满减额度 " + reduction.toPlainString() + " 不能大于等于门槛 "
                    + threshold.toPlainString() + "：这会让订单直接变成 0 元");
}
```

「满 100 减 120」在技术上能跑：`legal()` 会把减免截到当前价，结果是不倒找钱。
但用户花 100 元买到 100 元的东西只付 0 元 —— **这显然是配置写错了，不是运营想表达的意思。**

**这类错误必须在装配阶段炸掉。** 如果放行，它会以「某些订单莫名其妙 0 元」的形式
在几天后的财务报表上暴露，排查成本高一个数量级。

**为什么门槛比较用 `compareTo` 而不是 `equals`？**

```java
price.compareTo(threshold) < 0     // ✅ 正确
price.equals(threshold)            // ❌ scale 不同就 false
```

`BigDecimal.equals` 连小数位数一起比较：

```java
new BigDecimal("100").equals(new BigDecimal("100.00"))    // false
new BigDecimal("100").compareTo(new BigDecimal("100.00")) // 0（相等）
```

**金额比较永远用 `compareTo`**，这是本项目所有比较的统一做法。

**`unavailableReason` 为什么要写「当前价」而不是只说「未达门槛」？**

```java
return "未满 " + Money.yuan(threshold) + "（当前 " + Money.yuan(price) + "）";
// → "未满 ￥100.00（当前 ￥80.00）"
```

前者能让运营一眼算出「再买 20 元就能用」，后者什么信息都没给。

---

### 5.8 `PercentagePolicy.java`（84 行）：一个比例

**角色**：打折。按比例减免。

**状态只有一个：`rate`（实付比例）。**

```java
public record PercentagePolicy(String name, BigDecimal rate) implements DiscountPolicy {
```

**核心算式：**

```java
BigDecimal reduction = price.multiply(BigDecimal.ONE.subtract(rate));
return DiscountPolicy.legal(reduction, price);
```

**为什么用「实付比例」而不是「优惠比例」？**

| 说法 | 参数 | 计算 |
|---|---|---|
| **实付比例（本项目）** | `0.9` 表示付九成 | `price × (1 - 0.9)` |
| 优惠比例 | `0.1` 表示优惠一成 | `price × 0.1` |

选实付比例的理由：**它与日常说法一一对应**。「九折」对应 `0.9`、「八五折」对应 `0.85`，
运营填参数时不容易搞错。如果反过来用「优惠 10%」，代码里就会出现 `price * (1 - 0.1)`，
多一步减法就多一次解释成本。

**为什么要求 `0 < rate < 1`（三条边界都要拒绝）？**

```java
if (rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
    throw new IllegalArgumentException(
            "折扣比例必须在 (0, 1) 之间（0.9 表示九折）：" + rate.toPlainString());
}
```

| 值 | 含义 | 为什么拒绝 |
|---|---|---|
| `rate = 1` | 等于没打折 | 它不该出现在链上占一个位置 |
| `rate = 0` | 免费送 | 这是「赠品」不是「折扣」，要走完全不同的风控流程 |
| `rate > 1` | 涨价 | 会让价格变高，明显是配置错误 |
| `rate < 0` | 负比例 | 会让价格变成负数 |

**`price.signum() == 0` 为什么要提前返回？**

```java
if (price.signum() == 0) {
    // 价格已经是 0，再打折也只能减 0。
    return ZERO;
}
```

两个理由：

1. 避免无意义的乘法（性能可以忽略，但意图更清楚）；
2. **让审计轨迹里的「减免 0」有明确的原因** —— 不是「算出来恰好是 0」，而是「本来就是 0」。

**与 `FullReductionPolicy` 的对照**：满减有门槛（可能返回 0），打折没有门槛
（只要价格为正就一定有减免）。所以打折**不需要覆盖** `unavailableReason`。

---

### 5.9 `PointsDeductionPolicy.java`（108 行）：一个策略，两道上限

**角色**：把用户手里的积分换成钱。

**三个参数 + 一个上下文来源：**

```java
public record PointsDeductionPolicy(String name, BigDecimal yuanPerPoint, int maxPoints)
        implements DiscountPolicy {
```

| 值 | 来自 | 含义 |
|---|---|---|
| `yuanPerPoint` | 配置 | 每 1 积分抵多少钱 |
| `maxPoints` | 配置 | 单笔最多可用积分 |
| `context.availablePoints()` | 订单 | 用户余额 |

**核心是「两道上限取较小者」：**

```java
int usable = Math.min(context.availablePoints(), maxPoints);
if (usable <= 0) {
    return ZERO;
}
BigDecimal reduction = yuanPerPoint.multiply(BigDecimal.valueOf(usable));
return DiscountPolicy.legal(reduction, price);
```

**三道上限，缺一不可：**

| 上限 | 由谁保证 | 不保证会怎样 |
|---|---|---|
| 余额上限 | `Math.min(余额, maxPoints)` | 用户 10 积分抵了 1000 元 |
| 笔数上限 | 同上 | 大客户一次用掉 100 万积分，现金流断裂 |
| **价格上限** | `legal(reduction, price)` | 价格只剩 5 元，积分抵了 10 元 → **负价** |

**第三道最容易被漏掉。** 原项目的 `-0.09999999999999964` 就是漏掉它的直接后果。

**为什么这个策略只读积分、不扣积分？**

这是整个组合示例里最重要的一条边界，`PointsDeductionPolicy` 的注释写得很直白：

> 折扣计算是「试算」：用户可能在支付页反复切换优惠券、反复点「重新计算」，
> 每次都会把链跑一遍。如果策略在 `discount` 里扣积分，那么：
> 1. 用户刷新三次页面，积分被扣三次；
> 2. 链在第四个节点抛异常，前三个节点的副作用已经发生；
> 3. 「预览最终价」这个纯查询动作变成了写操作，接口再也不能做缓存。

**所以扣积分必须由订单服务在「确认下单」时统一执行一次。**
策略返回的只是「建议抵扣多少」，是一个纯粹的计算结果。

这条约束让整个策略层可以：

- **被反复求值**（预览、重算、比价都不产生副作用）；
- **被并发共享**（record 不可变，无状态）；
- **被单元测试固定输入输出**（纯函数）。

**`unavailableReason` 为什么要区分两种原因？**

```java
if (usable <= 0) {
    return "无可用积分（持有 " + held + "，单笔上限 " + maxPoints + "）";
}
return "当前价格仅剩 " + Money.yuan(price) + "，积分无法再抵";
```

「没积分」和「有积分但被价格卡住」对用户是完全不同的两件事：

| 原因 | 用户该做什么 |
|---|---|
| 无可用积分 | 去赚积分 / 换一张券 |
| 积分无法再抵 | 已经减到 0 了，不用再操作 |

**一条只说「积分未生效」的消息，会让两种情况都变成工单。**

---

### 5.10 `CappedPolicy.java`（88 行）：装饰器之一，会改名

**角色**：给任意策略加一条「单步最多减多少钱」的上限，不改它的算法。

**全部代码只有一个字段对 + 一个方法：**

```java
public record CappedPolicy(DiscountPolicy delegate, BigDecimal cap) implements DiscountPolicy {

    @Override
    public String name() {
        return "封顶 " + Money.plain(cap) + "（" + delegate.name() + "）";
    }

    @Override
    public BigDecimal discount(BigDecimal price, OrderContext context) {
        BigDecimal inner = delegate.discount(price, context);
        if (inner.compareTo(cap) <= 0) {
            return inner;
        }
        return DiscountPolicy.legal(cap, price);
    }
}
```

**它为什么能叫「装饰器」？**

三个条件全部满足：

| 条件 | 本类的实现 |
|---|---|
| 与被装饰者**同一类型** | 都实现 `DiscountPolicy` |
| **持有**被装饰者的引用 | `delegate` 字段 |
| 在转发前后**增加行为** | 转发后把结果截到 `cap` |

所以链节点、工厂、其他装饰器都**分不清**手里拿的是裸策略还是包装过的策略。
这正是装饰器的定义：**在不改变接口的前提下，动态地增加职责。**

**为什么不在每个策略里加一个 `cap` 参数？**

| 做法 | 新增一个策略时 | 想临时关闭上限时 | 想叠两层上限时 |
|---|---|---|---|
| 写进每个策略 | 要记得抄一遍 `min` 逻辑，漏抄就少一道风控 | 要改构造函数 | 做不到 |
| 继承 `AbstractCappedPolicy` | 必须继承，且只能有一层 | 要新建子类 | 类爆炸 |
| **装饰器** | 不需要改，自动获得 | 不包它就行 | `new CappedPolicy(new CappedPolicy(p, a), b)` |

**更关键的是职责归属**：单个折扣「最多减多少」通常不是折扣算法的一部分，
而是**活动风控**的要求（「这次大促单个折扣最多减 50，防止配置错误导致资损」）。
风控和算法来自不同部门、变化节奏也不同。

**为什么 `cap` 只约束「减免额」，不约束「最终价」？**

最终价的底线（不能为负）是**全局不变量**，由 `DiscountPolicy.legal` 和 `ChainState.reduce` 统一保证。
如果让 `CappedPolicy` 同时管「单步上限」和「最终价下限」，它就有了**两个变化原因** ——
将来「上限」的规则变了会牵连「下限」的判断。

**「一个类只有一个变化原因」不是口号，它直接决定了这个类被几个人同时修改的概率。**

**为什么名字必须变？**

```java
return "封顶 " + Money.plain(cap) + "（" + delegate.name() + "）";
// → "封顶 50（九折）"
```

因为**它改变了计算结果**。如果一个被封顶的策略仍叫「九折」，用户看到「减了 50」会以为规则被改了。
写成「封顶 50（九折）」才能解释「为什么这次只减了 50」。

**真实输出（§6.5）：**

```text
裸策略  九折                   1000 元 -> 减 ￥100.00
封顶后  封顶 50（九折）        1000 元 -> 减 ￥50.00
```

**注意 `discount` 里的顺序：必须先算再截。**

```java
BigDecimal inner = delegate.discount(price, context);   // ① 先算
if (inner.compareTo(cap) <= 0) {
    return inner;
}
return DiscountPolicy.legal(cap, price);                // ② 再截
```

如果反过来「先把价格截到 cap 再交给策略」，打折策略就会基于被截过的价格计算 ——
`1000` 变成 `50` 再打九折，减免只有 5 元。**「封顶」约束的是减免额，不是参与计算的基数。**

**为什么最后一步是 `legal(cap, price)` 而不是直接返回 `cap`？**

因为 `cap` 可能大于当前价。例如 `cap = 50`，但价格已经被前面的折扣减到 30 ——
直接返回 50 会让价格变成 -20。`legal(cap, price)` 会把它截到 30。

**装饰器也不应该信任「上限一定小于当前价」这个假设。**

---

### 5.11 `TracePolicy.java`（141 行）：装饰器之二，不改名

**角色**：把每次求值的前后价格打印出来，而且可以随时关掉。

**它直接对应原项目的 `LoggingDiscountDecorator`，但修掉了三个问题：**

| 维度 | 原项目 `LoggingDiscountDecorator` | 本类 `TracePolicy` |
|---|---|---|
| 能不能关 | 不能，`println` 写死在方法里 | `enabled=false` 即完全不打印，**零开销** |
| 输出到哪里 | `System.out` 硬编码 | 注入 `PrintStream`，可重定向到文件、内存、测试捕获器 |
| 带不带身份 | 只有价格，不知道是哪个策略 | 每行带策略名 |
| 有没有结论 | 只打前后价格，读者要自己相减 | 直接给出「减了多少」 |

**核心方法：**

```java
@Override
public BigDecimal discount(BigDecimal price, OrderContext context) {
    if (!enabled) {
        return delegate.discount(price, context);
    }
    BigDecimal reduction = delegate.discount(price, context);
    BigDecimal after = DiscountPolicy.money(price.subtract(reduction));
    out.println(indent + "[追踪] " + delegate.name()
            + "  入 " + Money.yuan(price)
            + "  出 " + Money.yuan(after)
            + "  减 " + Money.yuan(reduction));
    return reduction;
}
```

**`if (!enabled) return delegate.discount(...)` 这一行为什么必须放在最前面？**

因为关闭状态下**不能做任何字符串拼接**。如果写成：

```java
BigDecimal reduction = delegate.discount(price, context);
String line = "[追踪] " + delegate.name() + ...;   // 先拼好
if (enabled) { out.println(line); }                // 再决定打不打
```

每个订单、每个节点都会白白构造一个字符串。**「关闭」应该是零成本，而不是「不输出但仍计算」。**

**为什么不用 `record`？**

`TracePolicy` 持有 `PrintStream` —— 一个明确的可变外部资源，
而且它的 `equals` 没有业务意义（两个追踪装饰器不会因为指向同一个流就被认为相等）。

**`record` 应该只用于值对象。** `FullReductionPolicy` 是值对象，本类是「带外部资源的工具」，
所以写成 `final class`。

**为什么 `enabled()` 要返回新实例，而不是提供一个 `setEnabled(true)`？**

```java
public TracePolicy enabled() {
    return new TracePolicy(delegate, true, out, indent);
}
```

因为本类是不可变的。**「打开」意味着创建一个新实例，而不是修改已有的那个。**
这样同一个策略可以同时有「安静版」和「追踪版」两个包装，互不影响：

```java
TracePolicy quiet = new TracePolicy(policy);       // 默认关闭
TracePolicy loud = quiet.enabled();                 // 另一个实例
```

**为什么名字与被装饰者完全一致？**

```java
@Override
public String name() {
    return delegate.name();
}
```

**它与 `CappedPolicy` 给出了相反的答案，而这个反差正是本文最想传达的一条判断标准：**

```text
改变行为的装饰器（封顶、限流、重试）→ 名字要变，让调用方知道"外面还有一层"
只观测的装饰器（日志、计时、监控）    → 名字不变，它是透明的
```

**如果追踪改名会怎样？** 审计轨迹里会出现「追踪（九折） 减 ￥13.00」，
把观测手段写进了业务名称。再包一层封顶就变成「追踪（封顶 50（九折））」——
**名字变成了包装层的堆栈，而不是业务规则。**

---

### 5.12 `DiscountHandler.java`（204 行）：链骨架

**角色**：一条线 + 一个模板方法 + 一个三态答案。

**它只做四件事：**

```java
public abstract class DiscountHandler {

    private final String name;          // ① 节点名
    private DiscountHandler next;       // ② 后继

    public final DiscountHandler linkTo(DiscountHandler next) { ... }   // ③ 组装校验

    public final void process(ChainState state, OrderContext context) { ... }  // ④ 模板方法

    protected abstract DiscountDecision decide(BigDecimal price, OrderContext context,
                                               ChainState state);      // 子类只实现这个
}
```

**`process` 的四步顺序（已在 §4.7 说明，这里看代码）：**

```java
public final void process(ChainState state, OrderContext context) {
    if (state.stopped()) {
        return;
    }
    BigDecimal before = state.price();
    DiscountDecision decision = decide(before, context, state);
    if (decision instanceof DiscountDecision.Applied applied) {
        state.reduce(applied.amount());
    }
    state.trail().record(name, policyName(), decision, before, state.price());
    if (!decision.continues()) {
        state.stop();
        return;
    }
    if (next != null) {
        next.process(state, context);
    }
}
```

**为什么用递归而不是循环？**

```java
// 循环版本
for (DiscountHandler node : nodes) {
    if (state.stopped()) break;
    node.process(state, context);
}
```

两者在功能上等价，但递归版本有一个循环版本没有的能力：**每个节点都可以决定「不往下传」**，
而这个决定是节点自己的（通过 `Stopped`），不需要外部循环配合。

循环版本把「什么时候停」的知识放到了循环里 —— 也就是说，**链的结构被外部的 `for` 掌握了**，
这又回到了 §3.4 说的「管道」。

**`linkTo` 的四个校验，全部在组装期完成：**

```java
public final DiscountHandler linkTo(DiscountHandler next) {
    if (next == null) {
        throw new IllegalArgumentException("[" + name + "] 不能链接 null；链的末尾本身就是 null");
    }
    if (this.next != null) {
        throw new IllegalStateException("[" + name + "] 已经有后继，不能重复链接");
    }
    if (next == this) {
        throw new IllegalArgumentException("[" + name + "] 不能链接到自己：会造成无限递归");
    }
    for (DiscountHandler cursor = next; cursor != null; cursor = cursor.next) {
        if (cursor == this) {
            throw new IllegalArgumentException("链接 [" + name + "] → [" + next.name + "] 会形成环");
        }
    }
    this.next = next;
    return next;
}
```

四个校验各自防一种真实事故：

| 校验 | 不校验会怎样 |
|---|---|
| `next == null` | 调用方误以为「链的末尾要用 null 显式连接」，写出 `a.linkTo(null)` |
| `this.next != null` | `a.linkTo(b); a.linkTo(c);` → **b 被静默丢弃**，链少了一个节点 |
| `next == this` | `a.linkTo(a)` → 无限递归 → `StackOverflowError` |
| 环路检测 | `a.linkTo(b); b.linkTo(a)` → 从任一节点看都「合法」，只有跑到头才发现 |

**注意最后一项：环路检测是 O(n)。** 每次链接都要遍历剩余链。这值得吗？

**值得。** 因为组装发生在「活动配置变更时」（一天几次），而执行发生在「每笔订单」（一天几百万次）。
**把成本放到低频操作上，是性能与正确性之间最划算的交换。**

**`chain()` 静态工厂为什么存在？**

```java
public static DiscountHandler chain(List<DiscountHandler> nodes) {
    if (nodes == null || nodes.isEmpty()) {
        throw new IllegalArgumentException("链至少需要一个节点");
    }
    for (int index = 0; index < nodes.size() - 1; index++) {
        nodes.get(index).linkTo(nodes.get(index + 1));
    }
    return nodes.get(0);
}
```

因为 `linkTo` 返回的是**下一个**，手写时很容易写成：

```java
DiscountHandler head = a.linkTo(b).linkTo(c);   // head 实际是 c！
```

**`chain()` 让调用方永远不需要关心返回值。**

**与谁协作**：`PolicyHandler` / `ExclusiveGroupHandler` 继承它，`DiscountPipeline` 持有它的头节点。

---

### 5.13 `PolicyHandler.java`（111 行）：策略与链的接缝

**角色**：把「一个策略 + 一个适用条件」变成链上的一个环节。

**这是整个组合示例的核心方法（只有 10 行）：**

```java
@Override
protected DiscountDecision decide(BigDecimal price, OrderContext context, ChainState state) {
    if (!requirement.test(context)) {
        return new DiscountDecision.Skipped("不满足条件：" + requirement.describe());
    }
    BigDecimal reduction = policy.discount(price, context);
    if (reduction.signum() <= 0) {
        return new DiscountDecision.Skipped(policy.unavailableReason(price, context));
    }
    return new DiscountDecision.Applied(reduction);
}
```

**三个分支对应三种真实业务情况：**

| 分支 | 返回 | 原因来自 |
|---|---|---|
| 条件不满足 | `Skipped` | `requirement.describe()` |
| 条件满足但算出 0 | `Skipped` | `policy.unavailableReason(...)` |
| 算出大于 0 | `Applied` | —— |

**三个问题分别由三个对象回答，谁都不越界：**

```text
策略  →「怎么减」      discount(price, context)
条件  →「要不要减」    requirement.test(context)
节点  →「减完要不要继续」 返回三态决策
```

**为什么条件不满足时「不执行策略」？**

```java
if (!requirement.test(context)) {
    return new DiscountDecision.Skipped(...);      // 直接返回，不调用 policy.discount
}
```

两个理由：

1. **省掉一次无用的计算**（虽然本项目约定策略是纯函数，代价很小）；
2. **省掉策略内部可能产生的副作用** —— 本项目约定策略无副作用，但**别的项目未必**。
   如果某个策略偷偷打日志、写缓存，条件不满足时仍然执行它就是一种浪费和风险。

**节点名为什么要单独传，不直接用策略名？**

```java
public PolicyHandler(String name, DiscountPolicy policy, Requirement requirement)
```

因为同一条策略可能在链上出现两次，且两次的条件不同：

```text
① 满减（无条件）      满 100 减 20
② 满减（会员日专属）  满 100 减 20
```

如果两行都叫「满 100 减 20」，运营看审计时会以为是**重复执行**。

**节点名是位置标识（第几步、什么角色），策略名是规则标识，两者回答的问题不同，所以分成两列。**

**真实输出里两列都出现了：**

```text
 1  ① 满减          生效  ￥150.00 → ￥130.00  减 ￥20.00
```

`① 满减` 是节点名（`name()`），`满 100 减 20` 是策略名（`policyName()`）。
`AuditTrail.Entry` 把两者都保存了。

---

### 5.14 `ExclusiveGroupHandler.java`（138 行）：互斥与终止

**角色**：互斥组内只允许一个折扣生效。

**它与 `PolicyHandler` 只有两处不同：一个额外字段 + 两个额外判断。**

```java
@Override
protected DiscountDecision decide(BigDecimal price, OrderContext context, ChainState state) {
    if (state.groupUsed(group)) {
        return new DiscountDecision.Stopped(
                "同组「" + group + "」已被占用，后续优惠不再生效");
    }
    if (!requirement.test(context)) {
        return new DiscountDecision.Skipped("不满足条件：" + requirement.describe());
    }
    BigDecimal reduction = policy.discount(price, context);
    if (reduction.signum() <= 0) {
        return new DiscountDecision.Skipped(policy.unavailableReason(price, context));
    }
    state.markGroup(group);                              // ← 第二处不同
    return new DiscountDecision.Applied(reduction);
}
```

**为什么返回 `Stopped` 而不是 `Skipped`？**

这是本类存在的全部意义。假设链是：

```text
① 满减（组：券）
② 折扣券（组：券）
③ 积分抵扣
```

如果 ① 生效后，② 返回 `Skipped`，那么链会继续走到 ③，结果是「满减 + 积分」。

但业务规则是「**用了满减，整单不允许再使用任何优惠**」—— ② 必须返回 `Stopped`，让 ③ 也不执行。

**「跳过」和「终止」的差别，就是「只影响我」和「影响所有人」的差别。**

**真实输出的对照（§6.7）：**

```text
原价 ￥150.00 → 应付 ￥130.00，共减 ￥20.00（13.3%）  生效 1/2 步  链路已终止  互斥组 [券]
 1  ① 满减          生效  ￥150.00 → ￥130.00  减 ￥20.00
 2  ② 折扣券        终止  ￥130.00 → ￥130.00  减 ￥0.00  （同组「券」已被占用，后续优惠不再生效）
```

注意两件事：

- **总步数是 2 而不是 3** —— ③ 积分根本**没有产生轨迹**；
- 第一行末尾出现了 `链路已终止` 和 `互斥组 [券]`，这是 `DiscountResult` 的解释性信息。

**为什么「组已占用」要放在「条件判断」之前？**（§4.15 已论证，此处补代码视角）

```java
if (state.groupUsed(group)) { ... }       // 先判组
if (!requirement.test(context)) { ... }   // 再判条件
```

如果用户是 Lv.1 且「券」组已被占用：

| 顺序 | 输出 | 用户照着做之后 |
|---|---|---|
| 先判组 | `同组「券」已被占用` | 换一张券或取消满减 → 解决 |
| 先判条件 | `不满足条件：需 Lv.3 及以上` | 升到 Lv.3 → **发现还是不能用** |

**报告第一个真实障碍，是错误信息设计的基本要求。**

**`markGroup(group)` 为什么写在最后一行、`return Applied` 之前？**

```java
state.markGroup(group);
return new DiscountDecision.Applied(reduction);
```

因为只有**真正生效**的折扣才应该占用组。如果放在方法开头，那么：

- 条件不满足的节点也会占用组 → 后面的同组节点全部被终止；
- **这是一个静默的、灾难性的 bug**：优惠券还没用，组就被占了。

**与谁协作**：`PipelineFactory.exclusiveStage(...)` 创建它，`ChainState.usedGroups` 记录占用。

---

### 5.15 `DiscountPipeline.java`（169 行）：执行器

**角色**：把「一条组装好的链」变成「一次计算结果」。

**它自己不是节点，也不参与计算。三个职责：**

```java
public DiscountResult calculate(OrderContext context) {
    if (context == null) {
        throw new IllegalArgumentException("订单上下文不能为 null");
    }
    ChainState state = new ChainState(context.orderAmount());   // ① 每次新建状态
    if (head != null) {
        head.process(state, context);                            // ② 交给链头
    }
    return new DiscountResult(                                   // ③ 打包成不可变结果
            context.orderAmount(),
            state.price(),
            state.trail().entries(),
            state.stopped(),
            state.usedGroups());
}
```

**为什么每次都要 `new ChainState(...)`？**

这一行决定了线程安全：

```java
// ✅ 正确：状态在方法内部
ChainState state = new ChainState(context.orderAmount());

// ❌ 错误：状态作为字段
private ChainState state;              // 所有订单共享同一个状态！
public DiscountResult calculate(...) {
    this.state = new ChainState(...);  // A 订单刚设置，B 订单就把它覆盖了
    ...
}
```

**状态必须是「每次请求一份」，而不是「每个对象一份」。**
这就是本类所有可变数据都放在方法内部的理由。

**真实后果**：如果状态是字段，两个订单并发计算时，A 的减免会被记到 B 的轨迹上 ——
而且**不会抛任何异常**，只会表现为「月底对账差了几十块钱」。

**为什么空链要返回原价，而不是抛异常？**

```java
public static DiscountPipeline empty() {
    return new DiscountPipeline(null, List.of());
}
```

「这个用户没有任何可用优惠」是完全正常的业务场景。
如果空链抛异常，每个调用点都要 `try-catch` 或者判空，错误处理会淹没主流程。

**返回一个「减了 0 元」的结果，让调用方用同一套代码处理所有情况。**

**为什么还要保存 `nodes` 列表？**

```java
public static DiscountPipeline of(DiscountHandler head) {
    if (head == null) {
        throw new IllegalArgumentException("链头不能为 null；无优惠请用 DiscountPipeline.empty()");
    }
    List<DiscountHandler> nodes = new ArrayList<>();
    for (DiscountHandler cursor = head; cursor != null; cursor = cursor.next()) {
        nodes.add(cursor);
    }
    return new DiscountPipeline(head, nodes);
}
```

为了**在不执行的情况下**回答「这条链长什么样」—— 运营后台、日志、监控都需要展示链的构成。

只存头节点也能遍历出来，但**每次都遍历一遍既浪费又容易写出死循环**（万一链里有环）。
组装时一次性展开，之后 `describeChain()` 是纯读操作。

**`describeChain()` 的真实输出：**

```text
① 满减 → ② 会员九折 → ③ 积分抵扣
```

**注意 `of()` 里的遍历没有环检测** —— 因为 `linkTo` 已经在组装期保证了无环。
**这是在「已经有一道防线」的地方不重复设防的例子**（对比 `ChainState.reduce` 的兜底：
那里是跨类约定，这里只是本类内部的约定）。

---

### 5.16 `DiscountResult.java`（178 行）：不可变快照

**角色**：一次链执行的全部产出。

**五个字段，全部冻结：**

```java
public record DiscountResult(BigDecimal originalPrice, BigDecimal finalPrice,
                             List<AuditTrail.Entry> entries, boolean stopped,
                             Set<String> usedGroups) {

    public DiscountResult {
        if (originalPrice == null || finalPrice == null) {
            throw new IllegalArgumentException("原价与应付金额都不能为 null");
        }
        if (entries == null || usedGroups == null) {
            throw new IllegalArgumentException("轨迹与互斥组集合都不能为 null");
        }
        entries = List.copyOf(entries);
        usedGroups = Set.copyOf(usedGroups);
    }
}
```

**`List.copyOf` 到底防住了什么？**

```java
List<Entry> list = new ArrayList<>();
DiscountResult result = new DiscountResult(price, finalPrice, list, false, Set.of());
list.add(entry);          // result.entries() 悄悄多了一条！
```

**record 的字段是 `final`，但引用的集合可以是可变的。** 这比字段可变更隐蔽 ——
字段不能重新赋值，但内容可以变，而且**没有任何编译警告**。

`copyOf` 做了两件事：

1. 复制一份（切断与调用方的引用）；
2. **拒绝任何 `null` 元素**（`copyOf` 遇到 null 会抛 NPE，而 `new ArrayList<>(list)` 不会）。

**`totalReduction()` 为什么是方法而不是字段？**

```java
public BigDecimal totalReduction() {
    return DiscountPolicy.money(originalPrice.subtract(finalPrice));
}
```

因为它是**派生值**。存一份就可能与 `originalPrice - finalPrice` 算出来不一致 ——
**「两个真相」是账目 bug 的常见来源。派生的东西永远现算。**

**`reductionPercent()` 为什么要判零？**

```java
if (originalPrice.signum() == 0) {
    return BigDecimal.ZERO.setScale(1);
}
```

`OrderContext` 保证订单金额为正，但 `DiscountResult` 是**公开类型**，
不能假设调用方一定从那里来。**公共 API 的边界校验，不能依赖上游的类型约束。**

**`render()` 与 `renderUserFacing()`：同一份数据，两种视图**

```java
public String render() {
    // 汇总行 + 每一行轨迹（含跳过、终止、原因）
}

public String renderUserFacing() {
    // 只保留 Applied 的行，格式「优惠名 减 ￥X.XX」
}
```

**真实输出的对照（§6.8）：**

```text
给用户看：
满 100 减 20            减 ￥20.00
九折                    减 ￥13.00
积分抵扣                减 ￥10.00

给客服看：轨迹 3 步，生效 3 步，共减 ￥43.00，占比 28.7%
给财务看：原价 ￥150.00，应付 ￥107.00
```

**为什么不生成两个结果对象？**

因为跳过和终止的记录对客服排查有用，对用户是噪音。
**同一个结果对象提供两种视图，比生成两个结果对象更省** ——
而且保证两个视图的数据**一定一致**（它们读的是同一份 `entries`）。

---

### 5.17 `PolicyFactory.java`（280 行）：配置文本 → 策略

**角色**：把一段配置文本翻译成策略对象。

**注册表是核心（顺序固定）：**

```java
private static final Map<String, Creator> REGISTRY;

static {
    Map<String, Creator> map = new LinkedHashMap<>();
    map.put("full", PolicyFactory::fullReduction);
    map.put("percent", PolicyFactory::percentage);
    map.put("points", PolicyFactory::points);
    REGISTRY = Collections.unmodifiableMap(map);
}
```

**为什么用「注册表 + 方法引用」而不是一个 `switch`？**

对比原项目：

```java
// 原项目
return switch (type) {
    case "full" -> new FullReductionStrategy();
    case "percent" -> new PercentageOffStrategy();
    case "points" -> new PointsDeductionStrategy();
    default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
};
```

本项目的 `switch` 版本其实也能写，但注册表有两个实际好处：

1. **注册与解析分离**：`create()` 负责切分、查表、包装异常；创建函数只负责把参数变成对象。
   两者的测试可以独立进行；
2. **`supportedCodes()` 和 `helpText()` 可以直接从注册表生成**，不会出现
   「新增了一种折扣，但帮助文本忘了更新」。

**`create()` 的三层错误包装：**

```java
public static DiscountPolicy create(String spec) {
    if (spec == null || spec.isBlank()) {
        throw new IllegalArgumentException("折扣配置不能为空；" + helpText());
    }
    String[] parts = spec.trim().split(":", -1);
    String code = parts[0].trim();
    Creator creator = REGISTRY.get(code);
    if (creator == null) {
        throw new IllegalArgumentException("未知的折扣码 [" + code + "]。\n" + helpText());
    }
    List<String> params = new ArrayList<>();
    for (int index = 1; index < parts.length; index++) {
        params.add(parts[index].trim());
    }
    try {
        return creator.create(params);
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("折扣配置 [" + spec + "] 不合法：" + e.getMessage(), e);
    }
}
```

| 层 | 捕获什么 | 消息形式 |
|---|---|---|
| 入口 | 空字符串 | `折扣配置不能为空；` + 帮助 |
| 查表 | 未知折扣码 | `未知的折扣码 [fullx]。` + 帮助 |
| 参数 | 个数/格式/业务校验 | `折扣配置 [full:100] 不合法：需要 2 个参数…` |

**注意最后一层用了 `, e` 保留原始异常。** 这样堆栈里能看到「是 `FullReductionPolicy` 的构造器抛的」，
而不是只有 `PolicyFactory` 一层。**包装异常时永远保留 cause**，这条纪律能省下大量排查时间。

**`split(":", -1)` 的 `-1` 为什么不能省？**

```java
"full:".split(":")      // ["full"]        ← 末尾空串被丢弃
"full:".split(":", -1)  // ["full", ""]    ← 保留
```

没有 `-1`，`full:` 会被当成「0 个参数」而不是「1 个空参数」，
错误消息就变成「需要 2 个参数，实际给了 0 个」—— **与用户看到的东西不符**。

**`nameOfRate()`：数值 → 中文的翻译**

```java
private static String nameOfRate(BigDecimal rate) {
    String plain = Money.plain(rate);
    if (plain.startsWith("0.")) {
        String digits = plain.substring(2);
        String[] cn = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            char ch = digits.charAt(index);
            if (ch >= '0' && ch <= '9') {
                name.append(cn[ch - '0']);
            }
        }
        if (name.length() > 0) {
            return name + "折";
        }
    }
    return "折扣 " + plain;
}
```

`0.9` → `九折`，`0.85` → `八五折`。

**为什么工厂要管「名字」这种展示层的事？**

因为**名字属于策略自身**，而策略的构造参数（门槛、额度、比例）只有工厂知道。
如果让 `PercentagePolicy` 自己根据 `rate` 生成名字，它就要依赖 `Money` 和中文数字表 ——
**算法类不应该关心自己怎么被称呼。**

**这是「展示逻辑上移」的一个例子**：把「如何描述」交给创建者，把「如何计算」留给算法。

---

### 5.18 `PipelineFactory.java`（237 行）：装配整条链

**角色**：把「配置」翻译成一条装配好的责任链。

**两个工厂的分工（§4.10 已论证，这里看代码）：**

```java
public DiscountPipeline build() {
    List<DiscountHandler> nodes = new ArrayList<>();
    int index = 1;
    for (Step step : steps) {
        DiscountPolicy policy = PolicyFactory.create(step.policySpec());
        if (cap != null) {
            policy = new CappedPolicy(policy, cap);
        }
        if (trace) {
            policy = new TracePolicy(policy, true, traceOut, "    ");
        }
        String name = circled(index++) + " " + step.nodeName();
        if (step.group() == null) {
            nodes.add(new PolicyHandler(name, policy, step.requirement()));
        } else {
            nodes.add(new ExclusiveGroupHandler(
                    name, step.group(), policy, step.requirement()));
        }
    }
    if (nodes.isEmpty()) {
        return DiscountPipeline.empty();
    }
    return DiscountPipeline.of(DiscountHandler.chain(nodes));
}
```

**注意包装顺序（§4.9）：**

```java
policy = new CappedPolicy(policy, cap);      // ① 先封顶
policy = new TracePolicy(policy, ...);       // ② 再追踪
```

追踪打在**最外层**，所以它看到的是封顶后的真实减免额。

**如果反过来：**

```java
policy = new TracePolicy(policy, ...);       // ① 先追踪（内层）
policy = new CappedPolicy(policy, cap);      // ② 再封顶（外层）
```

追踪看到的是封顶前的**理论值** 100，而实际只减了 50 —— **日志会骗人。**

**`Builder` 的四个配置维度：**

```java
private final List<Step> steps = new ArrayList<>();
private BigDecimal cap;                        // 全局封顶
private boolean trace;                         // 全局追踪
private PrintStream traceOut = System.out;     // 追踪去向
```

**为什么 `cap` 和 `trace` 是「全局」而不是「每个阶段单独配置」？**

因为在本示例的业务里，这两条是**活动级风控与观测**，对所有折扣一视同仁。
如果将来需要「只给积分封顶」， Builder 加一个重载即可 ——
**当前不做，是因为没有这个需求（不为套用模式过度设计）。**

**`cap()` 的校验为什么在 Builder 里而不是在 `CappedPolicy` 里？**

```java
public Builder cap(String amount) {
    BigDecimal value = new BigDecimal(amount);
    if (value.signum() <= 0) {
        throw new IllegalArgumentException("封顶金额必须为正：" + amount);
    }
    this.cap = value;
    return this;
}
```

两层都有：`CappedPolicy` 的紧凑构造器也校验（§5.10）。
**这不是重复，而是「两个入口各自负责自己的边界」** ——
`CappedPolicy` 是公开类，可能被直接 `new`；`Builder.cap` 是配置入口，要给出更早的错误。

**`circled()`：为什么节点名要自动加圈码？**

```java
private static String circled(int index) {
    if (index >= 1 && index <= 20) {
        return String.valueOf((char) ('\u2460' + index - 1));
    }
    return "(" + index + ")";
}
```

因为审计轨迹的每一行都要回答「这是第几步」：

```text
 1  ① 满减          生效  ￥150.00 → ￥130.00  减 ￥20.00
```

**圈码是给人看的序号**，而 `AuditTrail.Entry.seq` 是给程序看的序号。
两者都存在，是因为它们服务不同的读者。

---

### 5.19 `LegacyOrderService.java`（210 行）：反面教材

**角色**：原项目组合示例的忠实复现，只用于对照。

**它把 9 个文件 217 行压缩成一个类，行为逐字节一致：**

```java
@Deprecated
public final class LegacyOrderService {

    @Deprecated
    private interface LegacyStrategy {
        double applyDiscount(double originalPrice);      // 返回折后价
    }

    @Deprecated
    private static final class LegacyLoggingDecorator implements LegacyStrategy { ... }

    @Deprecated
    private static final class LegacyFullReduction implements LegacyStrategy { ... }

    @Deprecated
    private static final class LegacyPercentage implements LegacyStrategy { ... }

    @Deprecated
    private static final class LegacyPoints implements LegacyStrategy { ... }
}
```

**入口 `placeOrder` 复现原项目的完整行为（包括每一行日志的顺序）：**

```java
public static double placeOrder(double originalPrice, String... discountTypes) {
    System.out.println("原始价格：" + originalPrice);
    List<LegacyStrategy> chain = new ArrayList<>();
    for (String type : discountTypes) {
        chain.add(new LegacyLoggingDecorator(create(type)));
    }
    double finalPrice = applyChain(chain, 0, originalPrice);
    System.out.println("最终支付金额：" + finalPrice);
    return finalPrice;
}
```

**为什么用递归 `applyChain` 而不是 for 循环？**

```java
private static double applyChain(List<LegacyStrategy> chain, int index, double price) {
    if (index >= chain.size()) {
        return price;
    }
    return applyChain(chain, index + 1, chain.get(index).applyDiscount(price));
}
```

因为原项目的 `DiscountHandler.apply` 就是递归的：

```java
public double apply(double price) {
    double discounted = handle(price);
    if (next != null) {
        return next.apply(discounted);
    }
    return discounted;
}
```

**递归与循环在数学上等价，但「逐字复现」要求连求值顺序都一致。**

**`silentPrice` 为什么要单独写一个不打印的版本？**

```java
public static double silentPrice(double originalPrice, String... discountTypes) {
    double price = originalPrice;
    for (String type : discountTypes) {
        price = switch (type) {
            case "full" -> price >= 100 ? price - 20 : price;
            case "percent" -> price - price * 0.1;
            case "points" -> price - 10;
            default -> throw new IllegalArgumentException("未知的折扣类型: " + type);
        };
    }
    return price;
}
```

Demo 3、4 需要拿到旧实现的**数值**做对照，但不希望 6 行日志污染输出。

**注意这里是「内联旧算式」，而不是调用 `create(type)`。** 原因：

`create(type)` 创建的策略对象内部有 `println`（例如 `LegacyPoints` 会打印「【积分抵扣】已减10」），
即使外层不打印，策略自己也会打。**必须不创建任何会打印的对象，才能真正「安静」。**

> 这是本项目实际踩过的坑：`silentPrice` 初版通过 `create(type)` 创建策略，
> 结果 Demo 3 的输出里混进了「【积分抵扣】已减10」。修正方式就是内联算式。

**为什么整个类标 `@Deprecated`？**

不是为了让人别用（它本来就不该被用），而是让 IDE 在所有调用点画上删除线。

**被删除线的代码，读者一眼就知道「这是反面教材」**，不需要先读三百行注释才反应过来。

---

### 5.20 `Client.java`（414 行）：十段 Demo

**角色**：演示入口。**它不参与任何业务逻辑。**

**十段 Demo 遵循同一个结构：**

```text
① 先说清楚要证明什么
② 用 LegacyOrderService 跑一遍旧实现，拿到真实数字
③ 用新链路跑同样的输入，给出明细
④ 最后一行点明「差异来自哪个模式」
```

| 段 | 证明什么 | 用到的模式 |
|---|---|---|
| Demo 1 | 旧实现的 11 行基线逐字复现 | —— |
| Demo 2 | 同样 107 元，但每一步都有名字和原因 | 策略 + 链 |
| Demo 3 | 负价、浮点尾巴、无积分也抵扣 | 策略契约 + 条件 |
| Demo 4 | 顺序决定结果（差 2 元） | 链的顺序 |
| Demo 5 | 装饰器封顶，且不改变策略本身 | 装饰器 |
| Demo 6 | 条件跳过是有原因的 | 条件 + 链 |
| Demo 7 | 互斥组终止整条链 | 链的终止 |
| Demo 8 | 同一份数据，两种视图 | 结果对象 |
| Demo 9 | 错误消息必须告诉你「怎么改」 | 工厂 |
| Demo 10 | 四个模式各自负责什么 | 全部 |

**为什么每段都要真的跑旧实现，而不是在注释里写「旧实现会算错」？**

因为**「会算错」是断言，「算出 -0.09999999999999964」是证据。**

本文档所有旧实现的数字都是 JDK 25 下真实跑出来的，读者可以自己复现；
而注释里的断言，读者只能选择相信。

**`printIndented` 为什么存在？**

```java
private static void printIndented(String text) {
    for (String line : text.split("\n", -1)) {
        System.out.println("  " + line);
    }
}
```

因为 `DiscountResult.render()` 返回的是**不带缩进**的多行文本（它不该知道调用方的排版），
而 Demo 输出的正文统一缩进 2 格。**排版是调用方的事，不是结果对象的事。**

**`expectFailure` 为什么要把多行消息逐行缩进？**

```java
private static void expectFailure(String label, Runnable action) {
    try {
        action.run();
        System.out.println("  [" + label + "] 没有抛异常 —— 这本身就是 bug");
    } catch (IllegalArgumentException e) {
        System.out.println("  [" + label + "] IllegalArgumentException:");
        for (String line : e.getMessage().split("\n", -1)) {
            System.out.println("      " + line);
        }
    }
}
```

因为 `helpText()` 是**三行**，异常消息里带换行。如果不逐行缩进，
第一行缩进 6 格，后三行顶格 —— 输出会错位。

**注意 `catch` 里那句「没有抛异常 —— 这本身就是 bug」**：
一个「预期失败的测试」如果没失败，说明校验漏了。**这种反向断言比正向断言更重要。**

---

### 5.21 二十个文件的依赖关系图

```text
Client
  │  只调用工厂 + 读结果
  ├──────────────┬──────────────────┐
  ▼              ▼                  ▼
PolicyFactory  PipelineFactory   LegacyOrderService
  │              │                  （无依赖，纯对照）
  │ 创建          │ 创建 + 包装
  ▼              ▼
FullReduction / Percentage / Points
  │  被 CappedPolicy 包装
  │  被 TracePolicy 包装
  ▼
DiscountHandler（抽象）
  ├── PolicyHandler
  └── ExclusiveGroupHandler
  │  被串成链
  ▼
DiscountPipeline
  └── 每次计算 new ChainState
        ├── price
        ├── AuditTrail
        ├── usedGroups
        └── stopped
  │  输出
  ▼
DiscountResult（不可变快照）

横切依赖（被所有层读取 / 产生）：
  DiscountPolicy（契约）   OrderContext（输入）
  DiscountDecision（三态）  Requirement（条件）
```

**读图要点：**

1. **依赖方向是单向的**：Client → 工厂 → 链 → 策略。没有任何反向依赖。
2. **`DiscountPolicy` 被三层使用**：工厂创建它、装饰器包装它、节点调用它。
   这就是它必须最稳定的原因。
3. **`LegacyOrderService` 不依赖任何新类** —— 它的存在意义就是「独立可比」。

**这张图与 §4.16 的文件分层表是同构的**：图里的每一层，对应表里的一组文件。
如果你能不看表、只看图说出 20 个文件各在哪一层，说明这份设计已经读懂了。

---

## 6. 执行流程：十段 Demo 的真实输出

本节逐段引用 `Client` 的真实运行输出（JDK 25，`-Dstdout.encoding=UTF-8`）。

**全文 152 行，下面 10 段合起来就是完整的 152 行** ——
没有删减、没有改写、没有「省略若干行」。

每段输出后面跟一个「怎么读」：**输出里每一行都在回答一个设计问题。**

### 6.1 Demo 1：旧实现的基线（11 行）

**目的**：证明 `LegacyOrderService` 是原项目的忠实复现。

```text
================================================================================================
Demo 1 旧实现基线：150 元 + 满减 + 打折 + 积分
================================================================================================
  调用：LegacyOrderService.placeOrder(150.0, "full", "percent", "points")

原始价格：150.0
【日志】执行策略前价格：150.0
【满减】满100减20，已减20
【日志】执行策略后价格：130.0
【日志】执行策略前价格：130.0
【打折】打九折，已减：13.0
【日志】执行策略后价格：117.0
【日志】执行策略前价格：117.0
【积分抵扣】已减10
【日志】执行策略后价格：107.0
最终支付金额：107.0

  这 11 行与原项目 CombineApplicationExamples.OrderService 的输出逐字一致。
  注意两点：日志里没有任何策略名；每一行都是 println，无法关闭。
```

**怎么读这段输出：**

| 观察 | 说明什么 |
|---|---|
| 中间 11 行与 §2.1 完全相同 | 复现是忠实的，后面所有对比都有意义 |
| `【日志】` 出现 6 次，`【满减】`/`【打折】`/`【积分抵扣】` 各 1 次 | **日志来自两个不同的地方**：装饰器打前后价，策略打自己的名字 |
| 所有数字都是 `150.0` 这种一位小数 | `double` 的 `toString`，不是金额格式 |
| 最后一行 `107.0` | 这是旧实现的**唯一产出** |

**注意一个细节**：`【日志】执行策略后价格：130.0` 后面紧跟
`【日志】执行策略前价格：130.0` —— 同一个数字被打了两次。
因为装饰器的「后价」和下一个装饰器的「前价」是同一个值。
**这就是「没有共享状态」的代价：每个装饰器都要自己记一遍。**

### 6.2 Demo 2：同样的 107 元，但每一步都有名字

**目的**：证明新链路在**结果不变**的前提下，提供了完整的过程信息。

```text
================================================================================================
Demo 2 新链路：同样的 107 元，但每一步都有名字和原因
================================================================================================
  链路：① 满减 → ② 会员九折 → ③ 积分抵扣
  上下文：￥150.00 / Lv.3 / 积分 1000 / 非新客 / 无券 / 非会员日

  原价 ￥150.00 → 应付 ￥107.00，共减 ￥43.00（28.7%）  生效 3/3 步
   1  ① 满减          生效  ￥150.00 → ￥130.00  减 ￥20.00
   2  ② 会员九折      生效  ￥130.00 → ￥117.00  减 ￥13.00
   3  ③ 积分抵扣      生效  ￥117.00 → ￥107.00  减 ￥10.00

  策略层负责「怎么减」，责任链负责「按顺序减、并说明每步」
  旧实现只打印前后价格，没有人知道 107 是怎么来的
```

**怎么读这段输出：**

| 行 | 信息量 |
|---|---|
| `链路：① 满减 → ② 会员九折 → ③ 积分抵扣` | **顺序是显式的**，不是藏在 `main` 的数组里 |
| `上下文：￥150.00 / Lv.3 / 积分 1000 / …` | 输入被完整记录，事后可复现 |
| `应付 ￥107.00 … 生效 3/3 步` | 汇总：结果 + 命中率 |
| 每一行轨迹 | 步号、节点名、决策、前价、后价、减免额 |

**对照 Demo 1 的 11 行**：同样是 107，旧实现需要读者自己从 6 行日志里
「前价后价相减」才能算出每一步减了多少；新实现直接给出「减 ￥20.00」。

**注意第二行的 13.00**：`130 × 10% = 13`。这正是 Demo 4 要解释的「顺序敏感」——
如果先打折，这一步会是 `150 × 10% = 15`。

### 6.3 Demo 3：金额安全

**目的**：把三个金额缺陷（负价、浮点尾巴、无条件抵扣）摆在一起。

```text
================================================================================================
Demo 3 金额安全：负价、浮点尾巴、无积分也抵扣
================================================================================================
  旧 11.00 元 + 打折 + 积分 -> -0.09999999999999964
  新 11.00 元 + 打折 + 积分 -> ￥0.00（积分最多抵到 0）
  旧 58.80 元 + 打折 + 积分 -> 42.919999999999995
  新 58.80 元 + 打折 + 积分 -> ￥42.92
  旧 150.00 元 + 积分（用户 0 分）-> 140.0
  新 150.00 元 + 积分（用户 0 分）-> ￥150.00  轨迹：跳过：无可用积分（持有 0，单笔上限 1000）

  前两条是「钱不能用 double」：负价与 0.000000000000005 的尾巴；
  第三条是「策略不该无条件执行」：不看余额就减，等于平台倒贴
```

**怎么读这段输出：**

**第一对（11 元）：**

```text
旧：-0.09999999999999964
新：￥0.00
```

旧实现算出**负数**。新实现停在 0 —— 因为 `DiscountPolicy.legal` 保证
`减免额 ≤ 当前价`，`ChainState.reduce` 再兜一次底。

**注意「（积分最多抵到 0）」这个括注**：它解释了为什么是 0 而不是负数，
**这就是「可解释性」的具体形态** —— 同样一个 0，有的系统会让人怀疑「是不是没算」，
这里明确说了「抵到 0 为止」。

**第二对（58.8 元）：**

```text
旧：42.919999999999995
新：￥42.92
```

旧实现把浮点误差直接暴露给用户。新实现用 `BigDecimal` 精确计算，
最后 `setScale(2, HALF_UP)` 收敛到分。

**第三对（0 积分）：**

```text
旧：140.0
新：￥150.00  轨迹：跳过：无可用积分（持有 0，单笔上限 1000）
```

旧实现减了 10 元，因为 `PointsDeductionStrategy` 不看余额。
新实现返回 `Skipped`，**并把原因写进了轨迹**：

```text
无可用积分（持有 0，单笔上限 1000）
```

这条消息告诉用户三件事：**为什么没减**（无可用积分）、**你有多少**（0）、**上限是多少**（1000）。

### 6.4 Demo 4：顺序敏感（差 2 元）

**目的**：证明顺序是真实影响金额的，而新实现把顺序变成可审查的配置。

```text
================================================================================================
Demo 4 顺序敏感：满减先还是打折先，差 2 元
================================================================================================
  旧 满减→打折→积分 -> 107.0
  旧 打折→满减→积分 -> 105.0
  旧实现里顺序完全由调用方传参决定，没有任何规则可查
  新 满减先 -> 107.00  链路：① 满减 → ② 九折 → ③ 积分
  新 打折先 -> 105.00  链路：① 九折 → ② 满减 → ③ 积分

  差 2 元的原因：满减的门槛看「当时的价」。
  先减 20 再打折，等于 130 打九折；先打折再减 20，等于 135 减 20。
  新实现不改变这个事实，但把顺序变成链上的显式配置 —— 可审查、可对比
```

**怎么读这段输出：**

| | 满减先 | 打折先 |
|---|---|---|
| 原价 | 150.00 | 150.00 |
| 第 1 步 | 减 20 → 130.00 | 打九折 → 135.00 |
| 第 2 步 | 打九折 → 117.00 | 减 20 → 115.00 |
| 第 3 步 | 积分 → 107.00 | 积分 → 105.00 |

**数学本质：满减的门槛判断用的是「当时的价」。**

- 满减先：150 ≥ 100 ✓，减 20 → 130；再打九折 → 117
- 打折先：150 打九折 → 135；135 ≥ 100 ✓，减 20 → 115

**两者都合法，但结果差 2 元。** 这不是 bug，是业务规则；
**真正的 bug 是旧实现让这个规则隐式地由调用方的参数顺序决定。**

新实现做了什么？**它没有改变「顺序敏感」这个事实**（数学规律无法改变），
它只把顺序变成 `describeChain()` 能打印出来的东西：

```text
链路：① 满减 → ② 九折 → ③ 积分
```

**从「数组字面量的顺序」到「可打印的链描述」，这就是可审查性。**

### 6.5 Demo 5：装饰器封顶

**目的**：证明封顶是「套在策略外面」的，策略本身一行没改。

```text
================================================================================================
Demo 5 装饰器：单步封顶 50，且封顶不改变策略本身
================================================================================================
  裸策略  九折                   1000 元 -> 减 ￥100.00
  封顶后  封顶 50（九折）        1000 元 -> 减 ￥50.00

  追踪装饰器（显式打开，输出可重定向）：
    [追踪] 封顶 50（九折）  入 ￥1,000.00  出 ￥950.00  减 ￥50.00

  三层的名字：封顶 50（九折）
  被装饰者仍是 九折，算法一行没改
```

**怎么读这段输出：**

**第一层证明：封顶真的生效了。**

```text
裸策略  九折                   1000 元 -> 减 ￥100.00
封顶后  封顶 50（九折）        1000 元 -> 减 ￥50.00
```

同一个策略、同一个价格，一个减 100、一个减 50。**差额来自装饰器，不是来自策略。**

**第二层证明：追踪看到的是「封顶后」的真实值。**

```text
[追踪] 封顶 50（九折）  入 ￥1,000.00  出 ￥950.00  减 ￥50.00
```

注意 `减 ￥50.00` 而不是 `减 ￥100.00` —— 这印证了 §4.9 的装配顺序：
**追踪在最外层，它看到的是最终生效的数字。**

**第三层证明：名字体现了包装关系。**

```text
三层的名字：封顶 50（九折）
```

「三层」指的是：`TracePolicy` → `CappedPolicy` → `PercentagePolicy`。
但名字只有两层（封顶 + 九折），因为 `TracePolicy` 不改名（§5.11）。

**第四层证明：被装饰者没变。**

```text
被装饰者仍是 九折，算法一行没改
```

这不是一句口号 —— 在代码里，`PercentagePolicy` 完全不知道 `CappedPolicy` 的存在。
**它没有 `cap` 字段，没有 `if (cap != null)` 分支。**

---

### 6.6 Demo 6：条件不满足就「跳过」，链继续

**目的**：证明「要不要减」已经从策略里搬出去了。

上一段 Demo 里封顶和追踪都作用在**算法外部**，但算法本身还是无条件执行的 ——
只要它在链上，就一定会算。这一段补上另一半：节点可以**被条件挡住**。

**Client 里构造这条链的代码（`Client.java` 第 230-234 行）：**

```java
DiscountPipeline pipeline = PipelineFactory.builder()
        .stage("满减", "full:100:20", Requirement.minLevel(3))
        .stage("新客礼", "full:50:15", Requirement.newCustomerOnly())
        .stage("会员日九折", "percent:0.9", Requirement.memberDayOnly())
        .build();
```

三步，三个不同的条件，三个不同的配置串。**注意 `Requirement` 是第三个参数** ——
顺序、算法、条件三者并列写在装配处，各占一个位置，谁都不藏在别人肚子里。

**真实输出（152 行输出中的第 74-92 行，逐字）：**

```text
  链路：① 满减 → ② 新客礼 → ③ 会员日九折

  用户 A：￥200.00 / Lv.1 / 积分 0 / 非新客 / 无券 / 非会员日
  原价 ￥200.00 → 应付 ￥200.00，共减 ￥0.00（0.0%）  生效 0/3 步
   1  ① 满减          跳过  ￥200.00 → ￥200.00  减 ￥0.00  （不满足条件：需 Lv.3 及以上）
   2  ② 新客礼        跳过  ￥200.00 → ￥200.00  减 ￥0.00  （不满足条件：仅限新客）
   3  ③ 会员日九折    跳过  ￥200.00 → ￥200.00  减 ￥0.00  （不满足条件：仅会员日）

  用户 B：￥200.00 / Lv.3 / 积分 0 / 新客 / 无券 / 非会员日
  原价 ￥200.00 → 应付 ￥165.00，共减 ￥35.00（17.5%）  生效 2/3 步
   1  ① 满减          生效  ￥200.00 → ￥180.00  减 ￥20.00
   2  ② 新客礼        生效  ￥180.00 → ￥165.00  减 ￥15.00
   3  ③ 会员日九折    跳过  ￥165.00 → ￥165.00  减 ￥0.00  （不满足条件：仅会员日）

  条件从策略里拆出来之后，同一个满减策略可以配任意条件；
  旧实现没有条件概念 —— 在链上就一定执行
```

**怎么读这 13 行：**

| 现象 | 背后的机制 |
|---|---|
| 用户 A 三行全部「跳过」，价格一路 `200.00 → 200.00 → 200.00` | `decide()` 返回 `Skipped`，模板方法**不减价**，但仍记录一行 |
| 每条跳过后面都跟着一句「不满足条件：……」 | `Skipped` 强制携带 `reason`，**编译期就不允许无理由的跳过** |
| 用户 B 只有前两步生效，第三步照旧跳过 | 跳过是**逐个节点**判定的，不是「整条链开或关」 |
| 用户 A 是 `0/3`，用户 B 是 `2/3` | 总数是**已执行步数**，两个用户都走完了全程 |
| 「满减」和「新客礼」都减钱，但配置串不同（`full:100:20` 与 `full:50:15`） | 它们是**同一个类** `FullReductionPolicy` 的两个实例 |

**第一个关键点：三句跳过原因，三种完全不同的判断，却只有一种写法。**

「满 3 级才能用」「只有新客能用」「只在会员日能用」——
如果按原项目的思路，这三句话要分别写进三个策略的 `applyDiscount` 里，
于是每个策略都长出了「我这个折扣什么时候才算数」的知识。

现在它们都是 `Requirement` 的实现，而 `FullReductionPolicy` 里**一个字都没提等级、新客、会员日**：

```java
// Requirement.java 里三个静态工厂（真实签名）
public static Requirement minLevel(int level)
public static Requirement newCustomerOnly()
public static Requirement memberDayOnly()
static Requirement always()          // 无条件，等价于「永远满足」
```

策略只回答「怎么减」，条件只回答「该不该减」。**这两句话各自能被单独替换。**

举一个直接的好处：运营说「新客礼以后改成 Lv.2 以上也能领」。
改的是装配处的那一个参数：

```java
.stage("新客礼", "full:50:15", Requirement.minLevel(2))   // 改这一个词
```

`FullReductionPolicy` 不用重新编译，`Requirement` 也不用新增实现。

**第二个关键点：参数化让「写死的常量」变成了「配置」。**

原项目的满减是 `if (originalPrice >= 100) return originalPrice - 20;`，
门槛 100、额度 20 都焊在方法体里。现在 `full:100:20` 与 `full:50:15`
是**同一个类**的两个实例 —— Demo 里同时出现了这两个规格：

| 节点 | 配置串 | 门槛 | 额度 |
|---|---|---:|---:|
| ① 满减 | `full:100:20` | 100 | 20 |
| ② 新客礼 | `full:50:15` | 50 | 15 |

**一个类，两种业务。** 而原项目想加「满 50 减 15」，只能再写一个
`NewCustomerFullReductionStrategy`，然后在工厂的 `switch` 里加一行。

**第三个关键点：「跳过」这件事本身是要被记录的。**

用户 A 的输出里有三行，价格一分没变。既然没变，为什么要记？

因为**「为什么没减」和「减了多少」是同等重要的信息**。

上一节 §2 列出的七个问题里，有一条是用户会问的：
「我明明是新客，为什么新客礼没生效？」客服打开这条轨迹，看到
「跳过 / 不满足条件：仅限新客」，就能直接回答 —— 因为这笔单在下单时
`newCustomer` 是 `false`（可能是老账号、也可能是注册状态同步失败）。

如果轨迹里只记「生效的节点」，那么「没生效」这件事就变成了一片空白，
客服只能回答「系统没给」，然后升级成工单。

**同一段逻辑，在原项目里的三种错误写法**（都会让条件重新粘回策略）：

| 做法 | 代码形态 | 后果 |
|---|---|---|
| 条件写进策略 | `if (user.level() >= 3) { ... }` | 策略同时知道「怎么算」和「什么时候算」，换条件要改策略 |
| 调用方先过滤 | `if (level >= 3) pipeline.add(fullReduction)` | 链的形态随用户变化，每次下单都要重新装配，轨迹里看不出「本可以但没生效」 |
| **条件作为节点属性** | `stage(name, spec, requirement)` | 链固定、条件可换、跳过可见 —— **本项目采用** |

---

### 6.7 Demo 7：互斥组把整条链截断

**目的**：区分「这一步不算」与「后面都别算了」。

上一段 Demo 的三种跳过，链都是**走完全程**的。这一段是反例。

**Client 里构造这条链的代码（`Client.java` 第 270-274 行）：**

```java
DiscountPipeline pipeline = PipelineFactory.builder()
        .exclusiveStage("满减", "券", "full:100:20")
        .exclusiveStage("折扣券", "券", "percent:0.85")
        .stage("积分", "points:0.01:1000")
        .build();
```

与上一段的唯一差别：前两步用的是 `exclusiveStage(名字, 组名, 配置)`。
**多出来的那个参数就是组名** —— 同一个组名下的节点互斥。

**真实输出（152 行输出中的第 94-105 行，逐字）：**

```text
  链路：① 满减 → ② 折扣券 → ③ 积分
  上下文：￥150.00 / Lv.3 / 积分 1000 / 非新客 / 券 SALE2026 / 非会员日

  原价 ￥150.00 → 应付 ￥130.00，共减 ￥20.00（13.3%）  生效 1/2 步  链路已终止  互斥组 [券]
   1  ① 满减          生效  ￥150.00 → ￥130.00  减 ￥20.00
   2  ② 折扣券        终止  ￥130.00 → ￥130.00  减 ￥0.00  （同组「券」已被占用，后续优惠不再生效）

  ② 返回的是「终止」而不是「跳过」：链在这里停住，③ 根本没被执行；
  旧实现的两个「无条件」让节点既不会跳过、也不会终止 —— 那是管道，不是责任链
```

**怎么读这 8 行：**

| 现象 | 背后的机制 |
|---|---|
| 轨迹只有 **2 行**，③ 积分**完全没有出现** | 链在第 2 个节点就 `return` 了，第 3 个节点的 `process` 从未被调用 |
| 写的是 `生效 1/2 步`，而不是 `1/3` | 分母是**已执行步数**（轨迹条数），不是链上节点数 —— 这个差别就是「终止」留下的指纹 |
| ② 那一行价格是 `￥130.00 → ￥130.00`，减 `￥0.00` | `Stopped` 同样不改变价格，只改变**链的命运** |
| 结果行多了两个前缀 `链路已终止` `互斥组 [券]` | `DiscountResult` 里的 `stopped` 与 `usedGroups` 两个字段 |
| ② 的配置是 `percent:0.85`（85 折），但没有生效 | 它**本来会**减 19.50 元（130 × 15%），被互斥规则挡在门外 |

**第一个关键点：`1/2` 与 `1/3` 是两种结构的指纹。**

用户 A 走完了三个节点（`0/3`），因为每个节点都被「问」过 —— 只是答案都是「跳过」。
这次只走了两个节点（`1/2`），因为第二个节点说「到此为止」。

**如果分母永远等于链上节点数，那就说明链从不提前结束 —— 那还是管道。**

这个细节不是格式问题，它是**可观测的**：拿到一份结果，只看分母就能判断
这条链是被正常走完的，还是被中途掐断的。

**第二个关键点：为什么互斥不能实现成「跳过」。**

假设把 ② 的 `Stopped` 改成 `Skipped`，会发生什么？推演一遍（按当前算法）：

```text
① 满减：150.00 - 20.00 = 130.00                    （生效）
② 折扣券：跳过，价格不动                             = 130.00
③ 积分：130.00 - 10.00（1000 分 × 0.01）= 120.00    （生效）
最终 120.00
```

而实际输出是 **130.00**。差的那 10 元，就是「互斥」这个词的含金量。

业务规则「满减与折扣券不可同享」有两种可能的含义：

| 读法 | 行为 | 最终价 |
|---|---|---:|
| 「券不能用」 | 跳过券，其他优惠照旧 | 120.00 |
| **「用了满减，这一单的券类优惠就结束了」** | 终止，后面不再评估 | **130.00** |

本项目选的是第二种，理由写在 `ExclusiveGroupHandler` 的类注释里：
**互斥组表达的是「同一类优惠只能用一个，且用完就收口」**，
而不是「只把当前这一条划掉」。

这也解释了为什么它需要一个单独的类，而不是在 `PolicyHandler` 里加一个 `if`：

- `PolicyHandler` 的语义是「按条件决定这一步生不生效」，天然只影响**自己**；
- `ExclusiveGroupHandler` 的语义是「组占位 → 同组冲突 → 终止全链」，影响的是**整条链**。

一个影响自己，一个影响全局。**影响范围不同的两件事，不该塞进同一个方法。**

**第三个关键点：「已被占用」这句话必须能说出是谁占的。**

输出写的是 `同组「券」已被占用`，而不是 `不满足互斥条件`。

因为前者能直接回答客服的问题：「为什么这张 85 折券没生效？」
——「因为前面已经用了同组的满减。」

实现上，`ChainState` 里有一个 `LinkedHashSet<String> usedGroups`，
节点在**生效后**才把组名写进去：

```java
// ExclusiveGroupHandler 的判定逻辑（节选）
if (group != null && state.usedGroups().contains(group)) {
    return DiscountDecision.stopped("同组「" + group + "」已被占用，后续优惠不再生效");
}
```

**注意顺序**：先查「同组有没有被占」，再调 `decide()`。如果反过来，
就会出现「自己占了自己的组」这种 bug —— 这一步在 §4.15 里作为
「先判组、后算钱」的设计决定单独讲过。

---

### 6.8 Demo 8：同一份结果，三种视图

**目的**：证明结果对象不是「一个更好看的 `double`」。

前七段 Demo 关注的都是**算得对不对**。这一段换个角度：算完之后，这些数据**给谁看**。

**真实输出（152 行输出中的第 107-118 行，逐字）：**

```text
  给用户看（只有生效项，不显示跳过和原因）：
  满 100 减 20            减 ￥20.00
  九折                    减 ￥13.00
  积分抵扣                减 ￥10.00

  给客服看：轨迹 3 步，生效 3 步，共减 ￥43.00，占比 28.7%
  给财务看：原价 ￥150.00，应付 ￥107.00

  旧实现只有一个 double 返回值 —— 三种角色都只能看到「107.0」
```

**三种视图，同一份数据：**

| 视图 | 方法 | 内容 | 谁在看 |
|---|---|---|---|
| 用户视图 | `renderUserFacing()` | 只有生效项 + 各自减了多少，**没有跳过、没有原因、没有中间价** | 订单页的「优惠明细」 |
| 客服视图 | `render()` + `appliedCounts()` | 每一步的 `前价 → 后价`、三态、跳过原因、终止标记 | 客服后台 |
| 财务视图 | `originalPrice()` / `finalPrice()` / `totalReduction()` | 原价、应付、共减、占比 | 对账、报表 |

**第一个关键点：为什么用户视图要藏掉「跳过」。**

把客服视图原样给用户看，会变成这样：

```text
满 100 减 20            减 ￥20.00
新客礼                  跳过（仅限新客）
会员日九折              跳过（仅会员日）
```

用户看到的第一反应不是「哦我不符合条件」，而是**「为什么我有这么多优惠没拿到？」**
然后打开客服对话。**每一条对他无效的规则，都是一个制造疑问的引子。**

所以 `renderUserFacing()` 做的事很简单 —— 过滤：

```java
/**
 * 只保留生效的轨迹，用于「给用户看的优惠明细」。
 */
public String renderUserFacing() {
    // 遍历 entries，只挑 decision instanceof Applied 的行
}
```

**但请看清这里的取舍**：它不是「把字段藏起来」（那只是格式问题），
而是**换了一个投影**（projection）—— 同一个对象的两种合法读法。

这也是为什么 `DiscountResult` 里同时保留原始 `entries` 和派生视图：
**原始数据只有一份，视图可以有很多份。**

**第二个关键点：`28.7%` 是怎么来的。**

```text
43.00 ÷ 150.00 = 0.286666…  →  28.7%
```

三个生效项的减免额相加：`20.00 + 13.00 + 10.00 = 43.00`。
`13.00` 是 130 × 10%（满减之后的价），不是 150 × 10%（`15.00`）——
**这就是 §4.1「返回减免额」这个决定的直接后果**：每一层拿到的是上一层留下的价。

财务看到的两个数字（`150.00` / `107.00`）与客服看到的「共减 43.00」
**不需要校验就必然自洽** —— 因为 `totalReduction()` 不是存下来的字段：

```java
public BigDecimal totalReduction() {
    return DiscountPolicy.money(originalPrice.subtract(finalPrice));
}
```

它每次调用都现算。**「原价 - 共减 = 应付」不是一条需要遵守的规则，而是这个方法的定义本身。**

`DiscountResult` 的紧凑构造器只做两件事：拒绝 null，用 `List.copyOf` / `Set.copyOf` 冻结集合。
它**故意不存** `totalReduction` 字段 —— 类注释里写得很清楚：

> 没有把它存成字段，因为它是派生值：存一份就可能与 `originalPrice - finalPrice`
> 算出来不一致，这类「两个真相」是账目 bug 的常见来源。

**这是比「存两份再校验」更彻底的做法：只留一个真相。**

**第三个关键点：为什么不把视图方法拆到外面去。**

严格按分层原则，`render()` 属于展示层，不该由领域对象提供。
真正的大型系统里，这些方法会放到 `OrderPresenter` 或 `DiscountJsonMapper` 里。

本项目把它们留在 `DiscountResult` 上，是一个**有意的折中**：

| 理由 | 说明 |
|---|---|
| 零外部依赖 | 这些方法只读自己的字段，不碰 IO、不碰配置、不碰数据库 |
| 教学可见性 | 读者打开结果类就能看到「一份数据可以怎么被读」 |
| 代价明确 | 一旦要做多语言或 JSON，就该把它们搬走 —— 那时视图会依赖 Locale 和序列化框架 |

**判断标准是一条线：视图方法开不值得留在领域对象里，看它有没有引入外部依赖。**
只做字符串拼接就留下；一旦要读 `Locale`、要 `@JsonProperty`，就该搬走。

**第四个关键点：原项目那一个 `double` 到底缺了什么。**

原项目 `OrderService.placeOrder` 的最后一行：

```java
System.out.println("最终支付金额：" + finalPrice);
```

`finalPrice` 是 `107.0`。这一个数字里**不包含**：

| 缺失的信息 | 后果 |
|---|---|
| 每一步减了多少 | 客服无法回答「为什么是 107」 |
| 哪一步生效、哪一步跳过 | 无法排查「优惠没生效」类工单 |
| 是否有节点被终止 | 无法解释「券为什么没用上」 |
| 原价是多少 | 财务拿到 107.0 无法反推 150.0 |
| 减免占比 | 运营无法做「平均优惠力度」报表 |

**这五条里，没有一条是「算错了」。** 五条全是「算对了，但说不清楚」。

这也是本篇最想强调的一句话：

> **在优惠引擎里，「算得出」是及格线，「说得清」才是交付标准。**
> 因为每一个数字后面都跟着一个会来问「为什么」的人。

---

### 6.9 Demo 9：错误消息必须告诉你「怎么改」

**目的**：证明「配置化」没有把风险转嫁给配置的人。

把写死的代码变成配置串，换来的是灵活性，代价是**配置会写错**。
一个只写「未知的折扣类型」的异常，等于把排查成本全部推给运营。

**真实输出（152 行输出中的第 120-139 行，逐字）：**

```text
  [未知折扣码 fullx:100] IllegalArgumentException:
      未知的折扣码 [fullx]。
      支持的折扣码：
        full:门槛:额度         满减，例如 full:100:20 表示满 100 减 20
        percent:实付比例       打折，例如 percent:0.9 表示九折（必须在 0 和 1 之间）
        points:每分抵扣:上限   积分抵扣，例如 points:0.01:500 表示 500 分以内、 每分抵 1 分钱
  [参数个数不对 full:100] IllegalArgumentException:
      折扣配置 [full:100] 不合法：需要 2 个参数，实际给了 1 个；正确写法：full: 门槛:额度，例如 full:100:20
  [比例越界 percent:1.5] IllegalArgumentException:
      折扣配置 [percent:1.5] 不合法：折扣比例必须在 (0, 1) 之间（0.9 表示九折） ：1.5
  [额度不小于门槛 full:100:120] IllegalArgumentException:
      折扣配置 [full:100:120] 不合法：满减额度 120 不能大于等于门槛 100：这会让 订单直接变成 0 元
  [订单金额为负] IllegalArgumentException:
      订单金额必须为正：-5

  对比旧实现：IllegalArgumentException("未知的折扣类型: fullx")
  —— 只说错了，不说有哪些可用、各自什么格式
```

**五条消息，五类错误：**

| # | 触发输入 | 错误类型 | 消息提供了什么 |
|---|---|---|---|
| 1 | `fullx:100` | 折扣码不存在 | 回显错的码 + **全部可用码 + 各自格式 + 例子** |
| 2 | `full:100` | 参数个数不足 | 需要几个、给了几个、**正确写法 + 例子** |
| 3 | `percent:1.5` | 取值越界 | 合法区间 `(0, 1)`、**口语解释**（0.9 = 九折）、实际值 |
| 4 | `full:100:120` | 参数之间自相矛盾 | 两个参数的关系 + **后果**（订单变 0 元） |
| 5 | `-5` | 订单金额非法 | 直接说明约束与违规值 |

**第一个关键点：消息的三段式 —— 错在哪、为什么错、怎么改。**

以第 2 条为例，一句话里塞了三件事：

```text
折扣配置 [full:100] 不合法：需要 2 个参数，实际给了 1 个；正确写法：full: 门槛:额度，例如 full:100:20
└──────┬──────────┘         └──────┬──────┘  └────┬────┘  └────────────────┬───────────────────┘
     错在哪（原样回显）          为什么错          差多少              怎么改（带例子）
```

只写「参数不合法」是**把问题原样退回**；写清「需要 2 个、给了 1 个、照这个格式写」，
运营看完就能自己改完重试，不需要拉开发。

这个差别在工时上是可量化的：一次配置错误，前者平均要一轮沟通（十几分钟），
后者是几秒钟。**当配置的频次上升到每天几十次，错误消息的质量就变成了吞吐量。**

**第二个关键点：第 4 条为什么要说「后果」。**

`full:100:120` 单看格式完全合法 —— 两个参数、都是正整数。它的问题是**语义矛盾**：
门槛 100，额度 120，意味着任何达到门槛的订单都会被减掉比原价更多的钱。

如果消息只写「额度不能大于等于门槛」，运营会想：为什么不让我这么配？是不是系统限制？
补上半句「这会让订单直接变成 0 元」，理由就闭环了 —— **这不是系统偷懒，是业务上不能这么干。**

**错误消息里最有价值的部分，往往不是「规则是什么」，而是「违反规则会出什么事」。**

**第三个关键点：`helpText()` 是一处定义、多处使用。**

```java
// PolicyFactory.java 第 146-149 行
public static String helpText() {
    return """
        支持的折扣码：
        full:门槛:额度         满减，例如 full:100:20 表示满 100 减 20
        percent:实付比例       打折，例如 percent:0.9 表示九折（必须在 0 和 1 之间）
        points:每分抵扣:上限   积分抵扣，例如 points:0.01:500 表示 500 分以内、每分抵 1 分钱""";
}
```

它被两处使用：

| 使用点 | 形式 | 面向谁 |
|---|---|---|
| 未知折扣码的异常 | `"未知的折扣码 [" + code + "]。\n" + helpText()` | 配置写错的人 |
| `supportedCodes()`（程序读取） | 同一份注册表的 key 集合 | 需要动态渲染下拉框的前端 / 运维脚本 |

**为什么不能在每个策略里各写一份格式说明？**

那样一来，加一种新折扣就要在 N 个策略里各改一处说明，
漏掉一处就会出现「文档说支持、实际不支持」—— 这是配置系统最常见的坑。

现在的形态把它压到了**两处**：注册表（`REGISTRY`）与帮助文本（`helpText()`）。
还能再压成一处吗？能，但代价不值得 —— 详见 §6.11 末尾对这条人工同步纪律的说明。

**单一事实来源（Single Source of Truth）不只是数据的事，错误提示也一样。**

**第四个关键点：包装，而不是覆盖。**

第 2、3、4 条的异常在 `create()` 里是被**包装**过的：

```java
try {
    return creator.create(params);           // 策略自己校验参数个数与取值
} catch (IllegalArgumentException e) {
    throw new IllegalArgumentException(
            "折扣配置 [" + spec + "] 不合法：" + e.getMessage(), e);
}
```

外层补上「是哪一串配置出的问题」，内层保留「具体哪个参数不对」。
两者缺一不可：

- 只有外层 → 用户知道哪串错了，但不知道为什么；
- 只有内层 → 用户知道为什么错，但订单里配了七八个折扣，不知道是哪一串。

**注意 `, e` 这个参数。** 它保留了原始异常的调用栈，
排查框架级问题时能直接定位到抛出的那一行。丢掉 `cause` 是异常包装里最常见的错误。

**第五个关键点：这些都是「运营会犯的错」，不是「程序员才会犯的错」。**

| 旧实现的视角 | 新实现的视角 |
|---|---|
| 折扣类型是 `switch` 里的字面量，只有程序员能改 | 折扣是运营在后台填的配置串 |
| 类型写错 = 编译不通过，不可能发生 | 类型写错 = 线上配置错误，每天都会发生 |
| 异常消息写给看日志的人 | 异常消息写给**改配置的人** |

**同一段代码，用户变了，错误消息的标准就变了。**

这就是为什么这一段要单独拿出来做 Demo：它不是「顺手把提示写好」，
而是**配置化这个设计决定带来的必然配套**。没有它，配置化就是把 bug 从编译期搬到了运行期。

---

### 6.10 Demo 10：四个模式各自负责什么

**目的**：收束。用一张表把四个模式钉在各自的位置上，并回答「拿掉会怎样」。

**真实输出（152 行输出中的第 141-152 行，逐字）：**

```text
  模式      负责                    在本示例里是                          拿掉会怎样
  ------------------------------------------------------------------------------------------------
  策略      单一折扣的算法          三个 discount() 方法                  所有折扣挤进一个 switch
  装饰器    横切能力：封顶、追踪    CappedPolicy / TracePolicy            每个策略都要自己实现封顶
  责任链    顺序、跳过、终止        PolicyHandler / ExclusiveGroupHandler 顺序与条件写死在调用方
  工厂      配置文本 → 对象图       PolicyFactory / PipelineFactory       new 散落在每个调用点

  一条纪律：四个模式没有一个是「为了用模式而加」的 ——
  每个都能回答「不做会缺什么」，答不上来的抽象就该删掉
```

**逐行读这张表：**

| 模式 | 「拿掉会怎样」这一列真正在说什么 |
|---|---|
| 策略 | 三个 `discount()` 会塌缩成一个 `switch`，**每加一种折扣就改一次这个方法** |
| 装饰器 | 封顶逻辑会被复制进每个策略 —— 三个策略改三次，将来三十个改三十次 |
| 责任链 | 「谁先谁后、要不要跳过、什么时候停」会重新回到调用方的 `if-else` 里 |
| 工厂 | `new FullReductionPolicy(...)` 会散落在每个调用点，**改构造参数要全局搜** |

**这张表的第四列，是本篇最重要的一列。**

它把「用了什么模式」换成了另一种问法：**如果没有它，哪段代码会变差？**

- 答得上来 → 这个抽象是**挣来的**（earned abstraction）；
- 答不上来 → 这个抽象是**加上去的**，应该删。

举一个本项目里**主动删掉**的例子：最初设计时曾考虑过给 `DiscountPipeline`
加一个 `PipelineVisitor`，用来遍历链上所有节点做统计。
后来发现唯一的使用场景就是 `describeChain()` 里的字符串拼接 ——
为一个方法引入一个访问者接口 + 三处 `accept()` 实现，**答不上「不做会缺什么」**。
最后用节点自己的 `describe()` 解决了。

**「能加」和「该加」是两件事。** GoF 的每一个模式都能解决一类问题，
但它们也都能在不需要它的地方制造复杂度。

**四个模式的边界，最后再对齐一次：**

```text
                ┌─────────────────────────────────────────┐
   配置文本 ───→ │ PolicyFactory   （这是什么折扣）          │
                └───────────────────┬─────────────────────┘
                                    ↓  DiscountPolicy
                ┌─────────────────────────────────────────┐
                │ CappedPolicy → TracePolicy（外面套什么）  │  ← 装饰器
                └───────────────────┬─────────────────────┘
                                    ↓  同一个 DiscountPolicy 类型
                ┌─────────────────────────────────────────┐
                │ PolicyHandler / ExclusiveGroupHandler    │  ← 责任链
                │  （先算哪一步、跳过还是终止）              │
                └───────────────────┬─────────────────────┘
                                    ↓  ChainState + OrderContext
                ┌─────────────────────────────────────────┐
                │ DiscountResult（说得清）                  │
                └─────────────────────────────────────────┘

   而 PipelineFactory 在左上角，负责把上面这条竖线装配起来。
```

**注意这张图的箭头方向**：配置文本从左上进入，结果从底部出去。
装饰器**包住**策略（横向套壳），责任链**串起**装饰后的策略（纵向排队）。
两条方向的组合方式完全不同 —— 这正是为什么它们是两个独立的模式，而不是一个。

---

### 6.11 可复现性：为什么这 152 行每次运行都一样

十段 Demo 全部跑完，输出是**确定**的。

**实测数据（三次独立运行）：**

| 指标 | 值 |
|---|---|
| 输出行数 | 152 行 |
| 输出字节数 | 9797 字节（加 `-Dstdout.encoding=UTF-8`）；默认 GBK 控制台下为 8102 字节 |
| SHA-256 | `DF1E29B1FCAAF0C65242EADDFE16D34D90BC8CECC81FAF498193AE9190518DD7` |
| 三次运行一致性 | **完全一致**（同一哈希 × 3） |
| Java 退出码 | 0 |
| 编译告警 | 0（`javac -Xlint:all -encoding UTF-8`） |

复现步骤见[附录 B](#附录-b-编译运行与验证记录)。

**为什么能做到逐字节一致** —— 输出里不能有任何一处「隐性随机」：

| 随机源 | 本项目的处理 |
|---|---|
| 集合遍历顺序 | `PolicyFactory` 的注册表用 `LinkedHashMap`，`ChainState` 的互斥组用 `LinkedHashSet` |
| 对象默认 `toString()` | 输出里不打印任何对象的默认形式（那会带上 `@1b6d3586` 这样的哈希） |
| 时间戳 | 输出里不含任何时间；`OrderContext` 也不依赖当前时间 |
| 随机数 / UUID | 全项目未使用 `Random`、`UUID`、`currentTimeMillis` |
| 绝对路径 | 输出里不含文件路径，只打印配置串与金额 |
| 浮点格式化差异 | 金额统一走 `BigDecimal` + `Money.yuan()`，格式化规则固定 |
| 字符编码 | 运行时指定 `-Dstdout.encoding=UTF-8`，不依赖平台默认编码 |
| 默认 Locale | 金额格式化不依赖 `Locale`（`Money.yuan()` 内部用固定符号与小数位） |

**这张表不需要逐条去踩 —— 只要有任何一行没做到，输出就不再可复现。**

举一个具体的：`supportedCodes()` 直接把 `REGISTRY.keySet()` 交给调用方。
如果注册表换成 `Map.of`，三个折扣码的遍历顺序就由哈希决定，
与 `full` / `percent` / `points` 的书写顺序无关：

```text
LinkedHashMap：full → percent → points   （恒等于注册顺序）
Map.of       ：顺序未指定（JDK 文档明确说可能随 JVM 启动而变）
```

而这个顺序会一路传下去：运维脚本按下标取值、前端按顺序渲染下拉框、
未来的命令行 `--list` 输出 —— 任何一处依赖它，就都不可复现。

**顺带指出一个真实存在的隐患**：`helpText()` 是手写的 text block，
不是从 `REGISTRY` 生成的。注册表用 `LinkedHashMap` 保证了 `supportedCodes()` 有序，
但 `helpText()` 里那三行的顺序与内容**完全靠人工同步**。
加第四种折扣时如果只往 `REGISTRY` 里加、忘了改 `helpText()`，
就会出现「异常消息里说支持三种、实际支持四种」——
这正是 §6.9 说的「文档说支持、实际不支持」的另一面。

本项目选择不自动生成 `helpText()`，因为它需要「例子」和「口语解释」
（例如「0.9 表示九折」），这些无法从注册表推导。
代价就是这条人工同步的纪律 —— **写在这里，是为了让它被看见，而不是假装不存在。**

**这条纪律值得单独写下来：**

> **凡是会出现在输出里的集合，都必须用有序实现。**
> 代码不一定会错，但输出不可复现，就等于没法验证。

**最后要说清「三次一致」证明了什么、没证明什么：**

| 证明了 | 没证明 |
|---|---|
| 没有隐性的随机源与顺序依赖 | 业务算法一定正确（哈希只能证明「没变」，不能证明「对」） |
| 同样的输入确实产生同样的输出 | 在所有 JDK 版本上都一致（本文只验证了 JDK 25） |
| 别人拿到代码能复现同样的结果 | 并发场景下也一致（本项目全部为单线程） |

**「可复现」是验证的起点，不是终点。** 但它是最便宜的那一道门槛 ——
如果连两次运行的结果都不一样，后面所有的对比、截图、文档引用都失去了依据。

---

## 7. 扩展对比：新需求来了改哪里

前六节讲的是「已经写好的东西为什么长这样」。这一节换一个视角：
**明天运营提了新需求，你要动哪几个文件？**

判断一个设计好不好，最直接的办法就是看它**面对变化时的改动半径**。
下面九个需求全部来自真实的电商场景，按改动量从小到大排列。

### 7.1 需求一：把「满 100 减 20」改成「满 200 减 30」

**改动：改一个字符串。**

```java
// 改之前
.stage("满减", "full:100:20")

// 改之后
.stage("满减", "full:200:30")
```

| 项目 | 数量 |
|---|---|
| 改动的 Java 文件 | **0** |
| 改动的配置 | 1 处 |
| 需要重新编译 | 不需要（如果配置来自外部文件） |

这就是 §4.8「参数化」那一节挣来的。原项目想改这个，必须打开
`FullReductionStrategy.java` 改方法体里的两个数字，然后重新编译发布。

**注意一个前提**：本项目里配置串是写在 `Client` 里的字面量，
所以严格说还是要重新编译。但**结构上**它已经是数据了 ——
把 `.stage(...)` 的参数换成从配置文件读，`PipelineFactory` 一行不用改。

### 7.2 需求二：新增「满 300 减 80」

**改动：加一个 stage，零新类。**

```java
.stage("满减", "full:100:20")
.stage("大额满减", "full:300:80")     // 新增这一行
```

| 项目 | 数量 |
|---|---|
| 新增类 | **0** |
| 改动文件 | 1（装配处） |
| 触碰策略 / 装饰器 / 结果类 | 否 |

同一种算法、不同参数 —— 这是最容易被过度设计的需求。
**不需要为它新建一个 `BigFullReductionStrategy`**，
因为「满 300 减 80」和「满 100 减 20」是同一个算法。

### 7.3 需求三：新增「阶梯满减」（满 200 减 50、满 500 减 150）

这一次算法真的变了：不再是「达到门槛减固定额」，而是「按区间取不同额度」。

**改动：新增一个类 + 注册两行。**

```java
// 新增 TieredPolicy implements DiscountPolicy
@Override
public DiscountDecision decide(BigDecimal price, OrderContext context) {
    // 按区间选档，返回 Applied / Skipped
}
```

```java
// PolicyFactory 的静态块里加一行
map.put("tier", PolicyFactory::tiered);

// PolicyFactory.helpText() 里加一行说明
```

| 项目 | 数量 |
|---|---|
| 新增类 | **1** |
| 改动文件 | 2（新类 + `PolicyFactory`） |
| 触碰责任链 / 装饰器 / 结果类 / 旧代码 | 否 |

**关键点：`PolicyFactory` 的注册表是唯一的扩展点。**
它用 `Map<String, Creator>` 而不是 `switch`，
新增一种折扣不需要修改任何已有分支 —— 这正是 §4.12 那条
「注册表 vs switch」的决定在扩展时兑现的价值。

对比原项目：`DiscountStrategyFactory` 的 `switch` 必须加一个 `case`，
而且它是一个**已有的方法**，每次新增都在改一个已经被测试覆盖的文件。

### 7.4 需求四：新增「周末九折」

这个需求的关键在于：**算法没变**（还是打九折），**变的是「什么时候算数」**。

**改动：先看条件依赖什么数据 —— 这决定了改动面。**

**情况 A：条件只依赖已有的上下文（等级、积分、券、新客、会员日）**

比如「持有优惠券的人才能用」，`Requirement` 里加一个静态方法就够了：

```java
public static Requirement withCoupon() {
    return new Requirement() {
        @Override
        public boolean test(OrderContext context) {
            return !context.couponCode().isEmpty();
        }

        @Override
        public String describe() {
            return "需持有优惠券";
        }
    };
}
```

**情况 B：条件依赖上下文中还不存在的数据（比如「周末」）**

这时光加 `Requirement` 不够 —— `OrderContext` 里必须先有 `weekend` 字段，
否则 `Requirement` 无从判断。改动变成三处：

| 文件 | 改什么 |
|---|---|
| `OrderContext` | 加 `private final boolean weekend` + `weekend()` 访问器 + Builder 的 `weekend(boolean)` |
| `Requirement` | 加 `weekendOnly()` 静态方法 |
| 装配处 | `.stage("周末九折", "percent:0.9", Requirement.weekendOnly())` |

| 项目 | 情况 A | 情况 B |
|---|---|---|
| 新增类 | 0 | 0 |
| 改动文件 | 2（`Requirement` + 装配处） | 3（`OrderContext` + `Requirement` + 装配处） |
| 触碰 `PercentagePolicy` | **否** | **否** |

**请注意最后一列：两种情况都没有碰策略。** 这才是关键。
加数据是必然的代价 —— 系统不知道今天是周末，就得有人告诉它；
但「因为加了一个条件，所以打折算法要改」这件事，没有发生。

**这就是「条件从策略里拆出去」最直接的回报。**
无论条件是读已有字段（券、等级）还是读新增字段（周末），
`PercentagePolicy` 都完全不知道这些条件存在 ——
它只知道「给我一个价，我算九折」。

如果条件还写在策略里（像原项目那样），这个需求会导致：
要么给 `PercentageOffStrategy` 加一个 `isWeekend` 字段（把策略变成有状态的），
要么再写一个 `WeekendPercentageStrategy`（复制一遍打折算法）。

### 7.5 需求五：新增「单笔最多减 100 元」

**改动：builder 上加一个 `.cap("100")`，零新类**（`Client.java` 第 210-213 行就是这么写的）：

```java
DiscountPipeline pipeline = PipelineFactory.builder()
        .stage("满减", "full:100:20")
        .stage("会员九折", "percent:0.9")
        .cap("100")
        .build();
```

| 项目 | 数量 |
|---|---|
| 新增类 | **0**（`CappedPolicy` 已存在） |
| 改动文件 | 1（装配处） |
| 触碰任何策略 | **否** |

**但务必看清 `cap` 的语义：它是「每个节点各自最多减 100」，不是「整单最多减 100」。**

因为它作用在 `build()` 的循环里：

```java
for (Step step : steps) {
    DiscountPolicy policy = PolicyFactory.create(step.policySpec());
    if (cap != null) {
        policy = new CappedPolicy(policy, cap);   // 每个节点各套一层
    }
    // ...
}
```

上例里满减减 20、九折减 13，都没碰到 100 的上限，所以看不出区别。
但如果是「满 1000 减 300」+「五折」，两个节点各自封顶 100，合计仍可能减 200。

**如果需求是「整单最多减 100」，`cap` 就放错地方了。**
那是一个**汇总层**的约束，应该在 `DiscountPipeline.calculate()` 返回前
对 `totalReduction` 做一次截断，或者在 `DiscountResult` 的构造器里校验。

本项目的 `CappedPolicy` 明确只做「单节点封顶」—— 见 §5.10。

**一个参数的位置，决定了它约束的范围。** 这是配置化最容易出错的地方：
写对了值、放错了层，语义就变了。

**`CappedPolicy` 是「一次写好、到处能用」的横切能力。**
它不关心被装饰的是满减、打折还是积分 ——
只要是 `DiscountPolicy`，它就能套。

如果将来要「单笔最多减 30%」，可以再写一个 `PercentCapPolicy`，
同样零侵入地套在任何策略外面。**这就是装饰器相对继承的核心优势：
能力与算法是两个维度，组合数是乘出来的，不是加出来的。**

### 7.6 需求六：新增「黑五折扣与所有券互斥」

**改动：装配处换一个方法名。**

```java
// 普通节点
.stage("黑五", "percent:0.8")

// 互斥节点
.exclusiveStage("黑五", "券", "percent:0.8")
```

| 项目 | 数量 |
|---|---|
| 新增类 | **0** |
| 改动文件 | 1 |
| 触碰 `ExclusiveGroupHandler` | 否（组名是参数） |

**组名是数据，不是类型。** 所以「黑五与券互斥」「满减与券互斥」
「生日礼与新人礼互斥」都不需要新类，只需要不同的组名。

如果将来出现「一个节点同时属于两个互斥组」（例如黑五既与券互斥、又与积分互斥），
那才需要改 `ExclusiveGroupHandler` 让它接受 `Set<String> groups` ——
**这是一次真实的模型演进，不是配置能解决的，应该在需求出现时再做。**

### 7.7 需求七：新增「跨境订单不参与任何折扣」

这个需求特殊在：它不是「某一节点跳过」，而是**整条链都不执行**。

**改动：在 `DiscountPipeline.calculate()` 的入口加一个判断，或在装配时选一条空链。**

| 方案 | 做法 | 评价 |
|---|---|---|
| A. 空链 | 跨境订单直接装配一个 `stage` 都没有的 pipeline | 最省事，但「为什么没优惠」这件事没有记录 |
| B. 全链前置条件 | 给 `DiscountPipeline` 加一个 `Requirement precondition`，不满足就直接返回原价 + 一条 `Stopped` 记录 | 轨迹里能说清原因 |
| C. 第一个节点返回 `Stopped` | 链首放一个 `PolicyHandler`，条件是「非跨境」，不满足就终止 | 无需改任何框架代码，但语义上「跨境」不是一种折扣 |

**本项目当前没有实现这个需求。** 写在这里是为了说明一件事：

> **不是所有新需求都应该用「加一个节点」来解决。**
> 有些需求改变的是「整条链要不要跑」，那是 `DiscountPipeline` 的职责，
> 硬塞进链里会让第一个节点承担它不该承担的语义。

如果真要做，B 方案最干净：它在 `calculate()` 里加一个前置判断，
但复用了 `DiscountResult` 已有的 `stopped` 字段与轨迹结构。

### 7.8 需求八：把结果导出成 JSON 给前端

**改动：新增一个渲染器，不动领域类。**

```java
// 新增 JsonRenderer（不 implements 任何领域接口）
public final class JsonRenderer {
    public static String render(DiscountResult result) {
        // 遍历 result.entries()，按三态分别输出
    }
}
```

| 项目 | 数量 |
|---|---|
| 新增类 | **1** |
| 改动领域类 | **0** |
| 需要 `DiscountResult` 暴露什么 | `entries()` / `originalPrice()` / `finalPrice()` / `stopped()` / `usedGroups()` |

**这里有一个值得注意的点**：`renderUserFacing()` 现在长在 `DiscountResult` 上（§6.8 讨论过），
一旦要支持 JSON、多语言、不同端的字段裁剪，这些视图方法就该搬出去。
**判断标准仍然是那条线：视图方法有没有引入外部依赖。**

JSON 渲染要依赖序列化框架，所以它从一开始就不该放进领域对象。

### 7.9 需求九：折扣顺序改由数据库配置

这是改动最大的一类：**装配的输入从硬编码变成数据**。

**改动：`PipelineFactory` 增加一个「从规格列表构建」的入口。**

```java
// 现在（Builder 链式调用）
DiscountPipeline pipeline = PipelineFactory.builder()
        .stage("满减", "full:100:20")
        .stage("会员九折", "percent:0.9")
        .build();

// 将来（从 DB 读出的步骤列表构建）
DiscountPipeline pipeline = PipelineFactory.fromSteps(stepsFromDatabase);
```

| 项目 | 数量 |
|---|---|
| 新增方法 | 1（`fromSteps`） |
| 调整类型可见性 | 1（`Step` 目前是 `private record`，要提升为 `public`） |
| 改动已有装配逻辑 | 0（`fromSteps` 内部复用 `builder()`） |
| 触碰策略 / 装饰器 / 责任链 / 结果类 | **否** |

**这里多出来的一项值得注意**：`PipelineFactory` 内部已经有一个
`private record Step(String nodeName, String policySpec, String group, Requirement requirement)`，
结构正好就是「一行配置」。但它是私有类型，外部拿不到。
**要让配置真的从数据库来，这个类型必须先公开。**

**这提醒了一件事：一个类的扩展成本，不只取决于它自己，
还取决于它内部使用的类型有没有准备好被外部复用。**

**为什么这个需求也能低成本落地？** 因为 `PipelineFactory` 从一开始
就把「装配」这件事集中在一个地方。如果 `new` 散落在各个调用点，
这个需求就变成了「找到所有装配代码并一起改」。

**但请注意一个风险**：顺序一旦可以由数据库配置，
「谁改的、什么时候改的、改之前是什么」就变成了必须回答的问题。
本项目**没有**配套的审计与版本机制 —— 那是配置中心该做的事，
不是这个示例该做的事。

### 7.10 九个需求的改动半径汇总

| # | 需求 | 新增类 | 改动文件 | 动到已有算法 |
|---|---|---:|---:|---|
| 1 | 改门槛与额度 | 0 | 0（改配置串） | 否 |
| 2 | 新增「满 300 减 80」 | 0 | 1 | 否 |
| 3 | 新增阶梯满减 | 1 | 2 | 否 |
| 4 | 新增「周末九折」 | 0 | 2 | 否 |
| 5 | 单笔最多减 100 | 0 | 1 | 否 |
| 6 | 黑五与券互斥 | 0 | 1 | 否 |
| 7 | 跨境不参与 | （未实现） | —— | —— |
| 8 | 导出 JSON | 1 | 1 | 否 |
| 9 | 顺序由 DB 配置 | 0 | 1（新增方法） | 否 |

**请注意最后一列：九个需求，没有一个是「动到已有算法」的。**

这才是这套设计的核心指标，比「用了四个模式」重要得多。

### 7.11 有一类需求，改动半径必然很大

上面九个需求都在「加东西」。但有些需求是**改执行模型**：

| 需求 | 为什么不是「加一个类」能解决的 |
|---|---|
| 「满减之后要按新价重新判定所有未执行的折扣」 | 链是单向走完的，重新判定意味着要回溯 —— 这是执行模型的改变 |
| 「所有折扣并行计算，取最优组合」 | 每步依赖上一步的价，并行意味着要改成组合搜索 |
| 「折扣可以撤销，用户能反悔」 | 需要引入命令模式的 undo（参见本项目的命令模式文档） |
| 「同一次下单要支持多币种」 | 金额模型要引入 `Currency`，`Money` 与 `DiscountPolicy` 的契约都要动 |

**这四条没有被「设计模式」解决，也不该指望它解决。**

四个模式解决的是「**在固定的执行模型内，让规则可插拔**」。
执行模型本身要变，那是重新设计，不是扩展。

**诚实地划出这条界线，比声称「我的设计支持一切变化」有用得多。**

---

## 8. 优缺点与常见误区

前面七节都在讲「为什么这样设计」。这一节讲反面：
**这套设计的代价是什么，什么情况下它反而是错的。**

一个只讲优点的文档，只能教会人模仿；把代价讲清楚，才能教会人判断。

### 8.1 优点：五条，每条都能落到代码

**优点一：变更局部化。**

§7 的九个需求，没有一个是「动到已有算法」的。这不是巧合，
它来自一条具体的结构约束：**每个类只有一个变更原因**。

| 变更原因 | 唯一受影响的类 |
|---|---|
| 折扣怎么算 | 对应的 `DiscountPolicy` 实现 |
| 怎么限制结果 | `CappedPolicy` |
| 要不要执行 | `Requirement` 的某个实现 |
| 顺序与终止 | `DiscountHandler` 的两个子类 |
| 配置怎么写 | `PolicyFactory` |
| 结果怎么展示 | `DiscountResult` 的视图方法 |

**「唯一」这个词是可以验证的**：改 `FullReductionPolicy` 的算法，
会影响到 `CappedPolicy` 吗？不会 —— 它只知道「有个 `DiscountPolicy` 返回了某个数」。

**优点二：每一步都可观测。**

原项目的 11 行输出里，只有 4 个互不相同的价格值和 6 行无主日志。
新实现的 `DiscountResult` 能回答五个问题：

| 问题 | 数据来源 |
|---|---|
| 减了多少 | `totalReduction()` |
| 哪几步生效 | `entries()` 中 `Applied` 的条目 |
| 哪几步跳过了、为什么 | `entries()` 中 `Skipped` 的 `reason` |
| 链有没有被终止、被谁终止 | `stopped()` + 终止那条记录 |
| 哪些互斥组被占用 | `usedGroups()` |

**这不是「顺手加的日志」，而是被设计进结果模型的字段。**
区别在于：日志可以关、可以丢、格式随意；字段是**类型的一部分**，
编译器会强制每个结果都带上它们。

**优点三：金额安全是结构性的，不是靠自觉。**

项目里有三道闸：

```text
第一道  DiscountPolicy.money()     统一 setScale(2, HALF_UP)
第二道  DiscountPolicy.legal()     0 ≤ 减免 ≤ 当前价
第三道  DiscountResult            总减免不存字段，恒由原价减应付现算
```

**注意第二道里 `min(price)` 这一句**：它让「减得比原价还多」在**单个节点**层面
就不可能发生。所以 §2.5 里那个 `-0.09999999999999964` 的负价，
在新实现里不是「通常不会出现」，而是**构造不出来**。

**优点四：规则是数据，不是代码。**

```text
"full:100:20"      →  FullReductionPolicy(门槛=100, 额度=20)
"percent:0.9"      →  PercentagePolicy(0.9)
"points:0.01:1000" →  PointsDeductionPolicy(每分 0.01, 上限 1000 分)
```

三种折扣、五个参数，全部由字符串决定。**这意味着运营不需要等发版。**

**优点五：每个类都能独立测试。**

| 要测什么 | 怎么测 | 需要启动什么 |
|---|---|---|
| 满减算法 | `new FullReductionPolicy(...).discount(price, ctx)` | 什么都不用 |
| 封顶装饰器 | `new CappedPolicy(policy, cap).discount(...)` | 什么都不用 |
| 互斥终止 | `new ExclusiveGroupHandler(...).process(state, ctx)` | 什么都不用 |
| 整条链 | `PipelineFactory.builder()...build().calculate(ctx)` | 不用数据库、不用 Spring |

本项目没有写单元测试（示例项目的取舍），但**结构上每个类都是可测的** ——
没有静态状态、没有单例、没有藏在方法体里的 `new Date()`。

**「可测性」不是测试写得多，而是被测对象能不能被单独拿出来。**

### 8.2 代价：五个真实成本

**代价一：文件数从 9 涨到 20。**

| 项目 | 原项目 | 新实现 | 倍数 |
|---|---:|---:|---|
| 源文件 | 9 | 20 | 2.2× |
| 物理行数 | 217 | 3547 | 16.3× |
| 编译产物 | —— | 42 个 `.class` | —— |

**16 倍的行数不是免费的。** 其中很大一部分是 javadoc 与注释
（本项目是教学项目，注释密度远高于生产代码），但类是实打实多了 11 个。

如果只看代码功能，`LegacyOrderService` 一个文件（210 行，含 javadoc）
就能复现原项目的全部行为 —— 也就是说，**这套设计的「功能等价物」只需要一个类**。

**代价二：读一条执行路径要跳六层。**

追一个问题「为什么这单只减了 20」，需要依次经过：

```text
Client.demo2()                        ← 装配与调用
  → DiscountPipeline.calculate()      ← 建立 ChainState
    → DiscountHandler.process()       ← final 模板方法
      → PolicyHandler.decide()        ← 条件 + 委托
        → CappedPolicy.discount()     ← 装饰器（可能有两层）
          → FullReductionPolicy.discount()   ← 真正的算法
```

**六层调用栈，只为算出一个 20。** 原项目是 `strategy.applyDiscount(price)` 一跳到底。

这是抽象的固有代价：**每一层都在替你做一件事，也都挡住了一次视线。**
缓解手段是 `TracePolicy` 与轨迹（让运行时自己讲出来），
但它改变不了「读代码要跳」这个事实。

**代价三：调试路径变长。**

断点打在哪？

| 想知道什么 | 断点位置 |
|---|---|
| 某个折扣算得对不对 | 对应 `DiscountPolicy.discount()` |
| 条件判得对不对 | 对应 `Requirement.test()` |
| 顺序对不对 | `DiscountHandler.process()` |
| 结果汇总对不对 | `DiscountResult` 构造器 |

**问题在于：你得先知道该打在哪。** 熟悉这套结构的人一眼就知道，
不熟悉的人会在 `process()` 里单步十几次。

**代价四：新人要懂四个模式才能改一处。**

改「满减额度」很容易（改配置串）。但改「互斥规则」需要理解：

- 为什么节点要能返回三种状态（`DiscountDecision`）
- 为什么终止之后不再往下走（`process` 的 `return`）
- 为什么组名要在算钱之前检查（§4.15）

**这三条知识，没有一条能从这个类的 30 行代码里看出来。**
它们存在于类之间的关系里，也就只能通过文档和带人来传递。

**代价五：装配本身也是一段代码。**

```java
DiscountPipeline pipeline = PipelineFactory.builder()
        .stage("满减", "full:100:20")
        .stage("会员九折", "percent:0.9")
        .cap("100")
        .build();
```

五行。它不复杂，但它是**新增的一层**：原来 `new` 三个对象就完了，
现在要先描述再构建。

**当规则只有两三条时，这五行的成本比它省下的更多。**

### 8.3 成本与收益：什么时候值得

把 8.1 和 8.2 放在一起，可以画出一条分界线：

| 场景 | 建议 | 理由 |
|---|---|---|
| 规则 ≤ 3 条，半年不改一次 | **直接写 if-else** | 20 个文件的维护成本，换不来一年一次的改动便利 |
| 规则 3-5 条，每月会调参数 | 策略 + 参数化 | 装饰器与责任链可能还不需要 |
| 规则 5 条以上，且有「跳过 / 互斥 / 封顶」 | **本项目的结构** | 这三类需求各自会逼出一个模式 |
| 规则由运营配置，不改代码 | 本项目 + 配置中心 | 需要额外补审计、灰度、回滚 |
| 一次性脚本 / 数据迁移 | **不要用模式** | 跑完就删的代码，没有「下一次修改」 |
| 团队里没人维护过这套抽象 | 谨慎 | 无人理解的抽象会腐烂成「不敢改的代码」 |

**判断的核心只有一个问题：未来 12 个月，这段代码会被改几次？**

- 0-1 次 → 抽象的成本收不回来；
- 3 次以上，且每次改的地方不同 → 抽象开始挣钱；
- 每次改的地方都一样 → 说明抽象切错了，应该重新划分边界。

**再给一条更具体的自检：**

> 如果删掉 `CappedPolicy`，把封顶逻辑写进三个策略 ——
> 你会觉得「也行，就是有重复」还是「不行，改起来太痛苦」？

- 前者：现在还不该用装饰器；
- 后者：你已经在为重复付账了，装饰器是把这笔账一次性买断。

**本项目选择这套结构，不是因为它「更高级」，而是因为示例要演示这些边界。**
真实项目里，第一版通常是 `LegacyOrderService` 那样的 48 行；
只有当规则真的长到 20 条、且运营天天改的时候，
才值得付出 16 倍的行数把它拆开。

**先写能跑的，再写能改的。反过来做，通常两样都做不好。**

---

### 8.4 十个常见误区

下面每一条，都是学设计模式时最容易形成的错误直觉。
每一条都配一句「为什么错」和一个「本项目的真实对照」。

#### 误区一：用了模式就是好设计

**错误说法**：「这个类用了策略模式，所以它比 `if-else` 好。」

**为什么错**：模式只描述**结构**，不描述**质量**。
一个用错了边界的策略模式，比 `if-else` 更难改 ——
因为它把逻辑分散到了多个文件里，而边界切错了。

**本项目的对照**：原项目的 `CombineApplicationExamples` 同时用了
策略、装饰器、责任链、工厂 —— 四个模式一个不少。
但它仍然有四个可复现的缺陷（负价、浮点尾巴、不看积分、顺序敏感）。

**它的问题不是"没用模式"，而是「用了模式却没建立边界」。**

更有说服力的例子是 §6.10 提到的：本项目**主动删掉**了一个 `PipelineVisitor`。
它能让结构"看起来更完整"，但答不上「不做会缺什么」。

**判断标准不是"用了几个模式"，而是「每个抽象能不能说出它挡住了什么变化」。**

#### 误区二：责任链就是过滤器链

**错误说法**：「Servlet 的 `FilterChain` 就是责任链，所以责任链的每个节点都会执行。」

**为什么错**：这是把**两种不同的结构**混成了一个名字。

| | 纯责任链 | 过滤器链（不纯责任链） |
|---|---|---|
| 节点默认行为 | 处理完就停 | 处理完继续往下传 |
| 谁来控制传递 | 框架（命中即停） | 节点自己调 `chain.doFilter()` |
| 能否中途终止 | 能（返回即终止） | 能（不调用下一个） |
| 典型代表 | 审批流 | Servlet `Filter`、Netty `Pipeline` |

**关键差别**：纯责任链里，「谁处理」是**被寻找出来的**；
过滤器链里，「每个都执行」是**默认的**，跳过才需要显式动作。

**本项目的对照**：`DiscountHandler.process()` 是 `final` 的模板方法，
子类只能返回三种决策，**不能自己决定要不要调下一个**（§4.7）。

```java
if (!decision.continues()) {
    state.stop();
    return;                 // 终止：后面的节点连机会都没有
}
if (next != null) {
    next.process(state, context);
}
```

**这段代码在父类里，子类改不了。** 这是刻意的：
如果让子类自己调 `next`，就等于把「链的形状」交给了子类，
迟早会出现「某个节点忘了调下一个」的 bug。

**结论**：说「责任链」之前，先问一句 —— 节点是**默认执行**还是**被选中才执行**？
这两者的代码结构完全不同。

#### 误区三：装饰器顺序无所谓

**错误说法**：「装饰器都是套壳，`new A(new B(x))` 和 `new B(new A(x))` 一样。」

**为什么错**：顺序无关只在**互不干扰的装饰器**之间成立（比如给文字加粗 + 改颜色）。
一旦装饰器会**读取或改变被装饰者的返回值**，顺序就决定了结果。

**本项目的对照**：`PipelineFactory.build()` 里的顺序是固定的：

```java
if (cap != null) {
    policy = new CappedPolicy(policy, cap);      // 先：封顶
}
if (trace) {
    policy = new TracePolicy(policy, true, traceOut, "    ");   // 后：追踪（在外层）
}
```

装配出来是 `TracePolicy(CappedPolicy(真实策略))`，执行顺序是
**追踪先进、封顶后算、追踪最后记录**。

| 顺序 | 追踪打印的「减了多少」 | 是否正确 |
|---|---|---|
| `Trace(Capped(P))` | 封顶后的真实减免额（上例是 `减 ￥50.00`） | ✅ 对 |
| `Capped(Trace(P))` | 未封顶的减免额（上例会是 `减 ￥100.00`） | ❌ 日志说减 100，实际只减 50 |

**第二种顺序不会算错钱，但会让日志说谎。** 而一份会说谎的日志，
比没有日志更糟 —— 排查时会把人带到错误的方向。

**结论**：装饰器的顺序，取决于「谁需要看到谁的输出」。
**把「需要看到最终值」的放在外层。**

#### 误区四：策略应该返回折后价

**错误说法**：「策略的职责是算出最终价格，返回折后价最自然。」

**为什么错**：返回折后价，在多步叠加时会产生三个具体问题。

**问题一：拿不到"这一步减了多少"。**

原项目的日志只能打印前后价格：

```java
System.out.println("【日志】执行策略前价格：" + originalPrice);
double result = delegate.applyDiscount(originalPrice);
System.out.println("【日志】执行策略后价格：" + result);
```

要得到「减了多少」，必须自己再减一次 —— 而这一次减法又会引入浮点误差。

**问题二：封顶的语义变得模糊。**

「单笔最多减 50」约束的是**减免额**，不是**折后价**。
如果策略返回折后价，装饰器要先反推：

```java
BigDecimal reduction = price.subtract(discountedPrice);   // 反推
BigDecimal capped = reduction.min(cap);
return price.subtract(capped);                            // 再算回去
```

两次减法，两次舍入，还要处理「反推出的减免额为负」的异常情况。

**问题三：无法直接校验契约。**

本项目的 `DiscountPolicy.legal()`：

```java
static BigDecimal legal(BigDecimal reduction, BigDecimal price) {
    BigDecimal rounded = money(reduction);
    if (rounded.signum() <= 0) {
        return ZERO;
    }
    return rounded.min(price);
}
```

`0 ≤ 减免 ≤ 当前价` 这一句话，同时挡住了负价与超减。
如果返回的是折后价，对应的约束是 `0 ≤ 折后价 ≤ 原价` ——
看起来对称，但**装饰器与责任链需要的恰恰是"减了多少"**，
所以每次都要多算一次。

**结论**：`DiscountPolicy.discount()` 返回**减免额**（§4.1）。
这是一个反直觉但影响全局的决定 —— 它让后续所有环节都能直接拿到需要的数。

#### 误区五：三态是过度设计

**错误说法**：「减或不减两种情况，用 `boolean` 就行了，搞三个状态是炫技。」

**为什么错**：`boolean` 只能表达两种结果，而链上的节点有**三种**语义：

| 状态 | 含义 | 链怎么办 | 结果里怎么记 |
|---|---|---|---|
| `Applied` | 减了 | 继续 | 记减免额 |
| `Skipped` | 这次不适用 | **继续** | 记原因 |
| `Stopped` | 到此为止 | **终止** | 记原因 |

**「跳过」和「终止」在 boolean 里是同一个值 `false`**，
但它们对链的影响完全相反 —— 一个是继续走，一个是立刻停。

用 boolean 会写出这样的代码：

```java
// 反例：无法区分跳过与终止
boolean applied = handler.tryApply(state);
if (!applied && handler.shouldStop()) {   // 需要额外的方法或标志位
    break;
}
```

`shouldStop()` 这个额外方法，就是三态被压成两态后**漏出来的信息**。
它迟早会以另一种形式回来 —— 可能是一个 `boolean stopped` 字段，
可能是一个 `Optional<String> stopReason`，也可能是一个 `RuntimeException`。

**本项目的对照**：`DiscountDecision` 是 sealed interface，三个 record：

```java
public sealed interface DiscountDecision
        permits DiscountDecision.Applied,
                DiscountDecision.Skipped,
                DiscountDecision.Stopped {

    boolean continues();      // Applied / Skipped → true，Stopped → false
}
```

**两个直接收益**：

1. `continues()` 把「链要不要继续」变成**状态自己的属性**，
   而不是调用方 `if-else` 里的一段判断逻辑；
2. `switch` 可以对 sealed 类型做**穷尽检查** ——
   将来加第四种状态，所有没处理它的 `switch` 会编译失败，
   而不是在运行时悄悄走错分支。

**结论**：状态的数量应该由**语义**决定，不是由「看起来简洁」决定。
两个状态装不下三件事时，硬压会导致信息在别处泄漏。

---

#### 误区六：不可变一定优于可变

**错误说法**：「所有对象都应该不可变，这样线程安全、不会出 bug。」

**为什么错**：不可变有它的**成本**：每次修改都要复制一份。
在「一条链要走十几步、每步都改价并追加轨迹」的场景里，
全不可变会导致 **O(n²) 的复制开销** —— 第 k 步要复制前 k-1 步的全部轨迹。

**本项目的对照**：两个核心数据类，一个可变、一个不可变，理由是分开的：

| 类 | 可变性 | 为什么 |
|---|---|---|
| `OrderContext` | **不可变**（6 个 `final` 字段 + Builder） | 它是一次性输入，整场计算中不该变 —— 策略不能偷偷改用户等级 |
| `ChainState` | **可变**（`reduce()` / `stop()` / `record()`） | 它是执行过程本身，每一步都在累加 |

`ChainState` 的三个修改方法：

```java
void reduce(BigDecimal amount)      // 累减价格
void markGroup(String group)        // 占用互斥组
void stop()                         // 标记终止
```

如果把它做成不可变，`process()` 就会变成：

```java
// 反例：全不可变的代价
ChainState next = state.withPrice(state.price().subtract(amount));   // 复制一份
next = next.withTrail(state.trail().appended(entry));                 // 又复制一份
```

**每一步复制两次，二十个节点就是四十次对象创建 + 四十次列表复制。**

**判别标准不是「可变好还是不可变好」，而是「这个对象代表什么」：**

| 角色 | 建议 | 本项目 |
|---|---|---|
| 输入参数 | 不可变 | `OrderContext` ✅ |
| 执行过程中的累加器 | 可变，但**不对外暴露** | `ChainState`（只在 `calculate()` 内部创建） |
| 输出结果 | 不可变 | `DiscountResult`（record + `List.copyOf`） |

**一句话：输入不可变、过程可变、输出不可变。**
把「过程」也做成不可变，是把函数式编程的优雅套在了不该套的地方。

#### 误区七：工厂必须用反射或注解

**错误说法**：「真正的工厂要用 `Class.forName()` 或 `@Component` 扫描，写 `switch` 太 low。」

**为什么错**：反射把「系统支持哪些折扣」这件事从**编译期**推到了**运行期**。

| | 注册表 + 方法引用 | 反射 / 注解扫描 |
|---|---|---|
| 新增折扣漏了注册 | **编译能过，但 `create()` 时抛异常（消息里列出可用码）** | 类没被扫到，运行时才发现 |
| IDE 能否找到引用 | 能（`PolicyFactory::fullReduction` 是可跳转的） | 通常不能（字符串 / 注解） |
| 启动开销 | 零 | 类路径扫描 |
| 调试 | 直接进方法 | 先进反射调用栈 |
| 适用场景 | 折扣种类固定、由开发新增 | 插件化、第三方扩展 |

**本项目的对照**：`PolicyFactory` 的注册表是**方法引用**，零反射：

```java
private static final Map<String, Creator> REGISTRY;
static {
    Map<String, Creator> map = new LinkedHashMap<>();
    map.put("full", PolicyFactory::fullReduction);
    map.put("percent", PolicyFactory::percentage);
    map.put("points", PolicyFactory::points);
    REGISTRY = Collections.unmodifiableMap(map);
}
```

`Creator` 是一个函数式接口：

```java
@FunctionalInterface
private interface Creator {
    DiscountPolicy create(List<String> params);
}
```

**它的好处是：`PolicyFactory::fullReduction` 这个引用，IDE 能跳转、能重命名、能找引用。**
而 `Class.forName("com.patterns...FullReductionPolicy")` 只是一串字符串 ——
改类名时编译器不会提醒你，只有上线后才会。

**什么时候该用反射？** 当「支持哪些实现」在编译时**真的**不知道 ——
比如允许用户上传 jar 包扩展。折扣引擎不属于这一类：
折扣是自家业务，种类由开发控制，编译期完全可知。

**结论**：注册表不是「简化的反射」，它是**更合适的选择**。
用反射反而丢掉了编译期检查这个最强的工具。

#### 误区八：条件写进策略才叫内聚

**错误说法**：「『满 100 减 20』里的『满 100』是满减的一部分，拆出去反而破坏了内聚。」

**为什么错**：这是对「高内聚」最常见的误读。
高内聚的单位不是「一次执行」，而是**一个变更原因**。

看这两个问题的答案是否相同：

| 问题 | 谁来回答 | 多久变一次 |
|---|---|---|
| 减多少、怎么算 | 开发 / 算法 | 少（除非业务规则大改） |
| 什么条件下才生效 | 运营 / 市场 | 多（活动、人群、时段天天变） |

**它们的变化频率和决策人都不一样。** 放在同一个类里，
就意味着一方改动要触碰另一方的代码。

**本项目的对照**：`FullReductionPolicy` 的 `discount()` 里
**没有一个字**提到等级、新客、券、会员日：

```java
@Override
public BigDecimal discount(BigDecimal price, OrderContext context) {
    DiscountPolicy.requireValidPrice(price);
    if (price.compareTo(threshold) < 0) {
        return ZERO;                      // 没达到门槛：减 0
    }
    return DiscountPolicy.legal(reduction, price);
}
```

**注意它连「跳过」这个决策都没有返回 —— 它只回答「能减多少」。**
把 `ZERO` 翻译成 `Skipped`、把「为什么是 0」翻译成人话，
是 `PolicyHandler.decide()` 与 `unavailableReason()` 的事：

```java
// FullReductionPolicy.unavailableReason()
return "未满 " + Money.yuan(threshold) + "（当前 " + Money.yuan(price) + "）";
```

**这是一个比「策略返回决策」更彻底的分层**：
策略连「这次算不算生效」都不判断，它只交出数字；
「这个数字意味着生效还是跳过」，由节点结合条件共同决定。

**「价格是否达到门槛」为什么留在策略里？** 因为它和「减多少」是**同一个变更原因** ——
门槛和额度是同一个配置项（`full:100:20`）的两个参数。运营改门槛时，额度通常一起改。

**判别标准很清晰**：这个条件的变化，会不会连带改变算法本身的参数？
- 会 → 留在策略里（它是算法的一部分）
- 不会 → 拆到 `Requirement` 里（它是外部的开关）

#### 误区九：结果对象只是一个 DTO

**错误说法**：「`DiscountResult` 就是装数据的，跟 DTO 没区别。」

**为什么错**：DTO 的定义是「只有数据、没有行为」。
`DiscountResult` 有三处**行为**，而且每一处都在防止一类 bug。

**行为一：派生值现算，不存字段。**

```java
public BigDecimal totalReduction() {
    return DiscountPolicy.money(originalPrice.subtract(finalPrice));
}
```

如果它是一个 DTO，会有 `totalReduction` 字段。那么「存进来的值」
和「算出来的值」就可能不一致 —— 而这正是账目 bug 最常见的形态。

**行为二：紧凑构造器冻结集合。**

```java
public DiscountResult {
    if (entries == null || usedGroups == null) {
        throw new IllegalArgumentException("轨迹与互斥组集合都不能为 null");
    }
    entries = List.copyOf(entries);
    usedGroups = Set.copyOf(usedGroups);
}
```

`List.copyOf` 不只是「防御性复制」—— 它让结果对象在交给客服系统之后，
不会被某个下游代码偷偷 `add` 一条轨迹。**结果一旦产出，就是只读的。**

**行为三：两个视图方法。**

`render()` 与 `renderUserFacing()` 是**对同一份数据的两种投影**（§6.8）。
DTO 不会知道自己该被怎么展示。

**结论**：`DiscountResult` 是一个**有行为的不可变值对象**，
不是 DTO。名字里有 `Result` 不代表它只能装结果 ——
**它还要保证这个结果在被读的时候，不会被读歪。**

#### 误区十：金额用 double，只要最后格式化就没问题

**错误说法**：「`double` 算得快，前端格式化两位小数就看不出来了。」

**为什么错**：格式化只影响**显示**，不影响三件更重要的事：
**计算、比较、存储**。

**问题一：格式化掩盖不了负数。**

```text
11.0 → 打九折 9.9 → 积分固定减 10 → -0.09999999999999964
```

格式化两位小数是 `-0.10`。**一个负的应付金额，格式化之后仍然是负的。**
支付接口拿到它，要么报错，要么真的给用户转 0.1 元。

**问题二：格式化掩盖不了比较。**

```java
// 原项目的门槛判断
if (originalPrice >= 100) { ... }
```

假设前面某一步算出了 `99.99999999999999`（本该是 100），
这个判断就会**判错** —— 而格式化发生在展示时，比这行代码晚得多。

**「差一点点」在比较运算里不是一点点，是另一个分支。**

**问题三：格式化掩盖不了对账。**

```text
58.8 - 5.880000000000001 = 52.919999999999995
52.919999999999995 - 10  = 42.919999999999995
```

页面显示 `42.92`，用户付了 42.92。
但账目里存的是 `42.919999999999995`，与 42.92 差 `5e-15`。
**单笔无所谓，一天一千万单，账就对不平。**

**本项目的对照**：三道结构性防线（§8.1 优点三）：

```text
输入        统一走 new BigDecimal(String)，不用 double 构造
计算        DiscountPolicy.money() 统一 setScale(2, HALF_UP)
约束        legal() 保证 0 ≤ 减免 ≤ 当前价
```

**关键在第一条**：`new BigDecimal(0.1)` 会把 `0.1` 的二进制误差原样带进来，
而 `new BigDecimal("0.1")` 是精确的。**这个区别值一个专门的构造入口。**

**结论**：金额不是一个「数字」，是一个**带精度和舍入规则的领域概念**。
把它当 `double` 处理，等于把领域知识扔掉了，然后用格式化去补 ——
而格式化补不了计算和比较。

### 8.5 十二道自检题

合上文档，试着回答。答不上来的那一条，就是需要回头再读的章节。

| # | 问题 | 答不上来去看 |
|---|---|---|
| 1 | 为什么 `DiscountPolicy` 返回减免额而不是折后价？ | §4.1、§8.4 误区四 |
| 2 | 「跳过」和「终止」有什么区别？各自由谁决定？ | §4.4、§6.7 |
| 3 | 为什么 `DiscountHandler.process()` 是 `final`？ | §4.7 |
| 4 | `CappedPolicy` 为什么会「改名」，而 `TracePolicy` 不会？ | §4.8、§5.10、§5.11 |
| 5 | 为什么 `CappedPolicy` 必须在 `TracePolicy` 的内层？ | §4.9、§8.4 误区三 |
| 6 | `OrderContext` 不可变、`ChainState` 可变，各自的理由是什么？ | §4.2、§4.6、§8.4 误区六 |
| 7 | 互斥组为什么必须在算钱之前检查？ | §4.15、§6.7 |
| 8 | 为什么用 `LinkedHashMap` 而不是 `Map.of`？ | §4.12、§6.11 |
| 9 | `totalReduction()` 为什么不存成字段？ | §6.8、§8.4 误区九 |
| 10 | 条件（`Requirement`）为什么不能写进策略？ | §4.5、§8.4 误区八 |
| 11 | 旧实现的「责任链」为什么其实是管道？ | §2.4、§8.4 误区二 |
| 12 | 什么情况下**不该**用这套结构？ | §3.6、§8.3 |

**十二道题里，只有第 1、5、7 题是关于「代码怎么写」的。**
其余九道全在问「为什么这样划分边界」。

这不是出题人的偏好 —— 它反映了一个事实：
**这套代码的难点不在实现，在边界。**
写一个 `DiscountPolicy` 接口，任何学过 Java 的人十分钟就能写出来；
但决定「条件不该在这个接口里」，需要理解业务的变化频率。

---

## 9. 面试与自测

这一节不是题库，而是**检验理解深度的三种方式**：
被追问、被质疑、被要求改代码。

### 9.1 五个必答题

#### 问题一：策略模式和 if-else 的边界在哪？

**问的是**：你是不是见 `if-else` 就想重构。

**答题骨架**：

1. **不是按代码行数划线，是按变化频率划线。**
   三个分支但三年不改，`if-else` 是更好的选择 —— 它在一屏里能看全。
2. **分支背后的"算法"是否需要独立演化。**
   如果每个分支都在长自己的参数、自己的校验、自己的测试，就该拆。
3. **分支是否被多处复用。**
   `switch` 只能在一个方法里；策略对象可以被链、被装饰器、被别的服务复用。
4. **谁来决定走哪个分支。**
   如果这个决定来自配置或数据库，那"分支"已经变成数据了，策略是自然形态。

**落到本项目**：`PolicyFactory` 的注册表替代了 `switch`，
但真正让策略值得存在的不是这个替换，而是**策略能被装饰、能被装进链**。
如果只是「按类型选一个算法然后调用一次」，`switch` 就够了。

**减分回答**：「策略模式可以消除 `if-else`，符合开闭原则。」
—— 这句话背得很熟，但没回答「什么时候不该用」。

#### 问题二：装饰器和继承怎么选？

**问的是**：你知不知道「组合优于继承」到底优在哪。

**答题骨架**：

1. **继承是编译期决定的，装饰器是运行期组合的。**
   要「有时封顶、有时不封顶」，继承需要两个子类；装饰器只需要传不传那个参数。
2. **继承会把能力焊进类型层次。**
   `CappedFullReduction extends FullReduction` —— 那 `CappedPercentage` 呢？
   每加一种能力，类数量按「算法数 × 能力数」增长。
3. **装饰器要求被装饰者与装饰者同类型。**
   `CappedPolicy implements DiscountPolicy` 并持有一个 `DiscountPolicy`，
   所以它能套在任何策略上，也能被再套一层。
4. **代价：对象数量变多、调试栈变深、顺序敏感。**

**落到本项目**：`CappedPolicy` / `TracePolicy` 各自只做一件事，
且都能与任意 `DiscountPolicy` 组合。两种能力、三种算法，
组合出 4 种包装形态，**只用了 2 个类**。

**加分项**：主动提「装饰器顺序敏感」（§8.4 误区三）——
说明你不只知道优点。

#### 问题三：责任链里，「下一个是谁」应该由谁决定？

**问的是**：你写过的是真链，还是 `for` 循环。

**答题骨架**：

1. **结构上：链在装配时确定，不在运行时改。**
   本项目的 `next` 是 `private final`，通过 `DiscountHandler.chain(nodes)` 一次性串好。
2. **行为上：节点只能决定「继续 / 终止」，不能决定「跳到哪里」。**
   这是纯责任链与「路由表」的区别 —— 后者更像状态机。
3. **为什么不暴露 `setNext`：**
   可变的后继意味着链的形状可以在运行时被任意修改，
   而「顺序」在本项目里是运营配置的一部分，应该只有一个入口（`PipelineFactory`）。
4. **谁来兜底「没人处理」：**
   本项目的链末尾没有隐式的「默认处理器」，走完就是走完 ——
   结果是「一步都没生效」，而不是异常。**这是业务选择，不是技术默认。**

**落到本项目**：`DiscountHandler.process()` 是 `final` 的，
子类实现 `decide()`，**没有机会自己调 `next`**（§4.7）。

**减分回答**：「责任链就是每个 handler 里有 `next`，处理不了就往下传。」
—— 没说清「谁控制传递」，也没区分纯链与过滤器链。

#### 问题四：工厂方法、简单工厂、抽象工厂，你用哪个？

**问的是**：你能不能把三个容易混的名字分清。

**答题骨架**：

| 形态 | 结构 | 本项目 |
|---|---|---|
| 简单工厂 | 一个类 + `switch` / 注册表，静态方法返回产品 | `PolicyFactory` ✅ |
| 工厂方法 | 抽象创建者定义 `createX()`，子类决定实例化哪个 | 本项目**没有**用 |
| 抽象工厂 | 一个工厂创建**一族**相关产品 | 本项目**没有**用 |

**为什么只用简单工厂**：本项目只创建**一个产品族**（`DiscountPolicy`）。
引入工厂方法需要为每种折扣建一个工厂类，收益为零；
抽象工厂是为「多个产品族需要保持一致」准备的，这里没有第二个族。

**加分项**：指出 `PipelineFactory` 不是 GoF 意义上的工厂 ——
它组装的是**对象图**（链 + 装饰器），更接近「构建器 + 工厂」的混合。
名字叫 Factory 是因为它的职责是「把配置变成可执行对象」。

#### 问题五：四个模式一起用，怎么证明不是过度设计？

**问的是**：你会不会用模式堆砌。

**答题骨架**：

1. **每个模式回答一个不同的问题**，且这四个问题不能互相替代（§1.2 的表）。
2. **每个模式都有「拿掉会怎样」的具体答案**（§6.10 的第四列）。
3. **改动半径可验证**：§7 的九个需求，没有一个动到已有算法。
4. **主动承认代价**：20 个文件、六层调用栈、16 倍行数（§8.2）。

**最优回答的结构**：先说收益，再说代价，最后给出「什么时候我会选择不这么做」。

**减分回答**：「这四个模式是经典组合，很多框架都这么用。」
—— 用别人的选择代替自己的判断。

### 9.2 三个追问：区分「背过」和「想过」

#### 追问一：把 `DiscountDecision` 改成 `boolean`，会丢什么？

**期望的回答**：

丢的不是一个枚举值，是**三条信息**：

1. **丢失「跳过」与「终止」的区分。**
   两者都映射到 `false`，链的行为却相反（继续 vs 停止）。
   要补回来，必须额外加一个 `boolean stopped` 或抛异常 —— 信息会从类型里漏到约定里。
2. **丢失原因。**
   `Skipped(reason)` 与 `Stopped(reason)` 强制携带人话。
   `false` 什么都没带，客服的问题又答不上来了。
3. **丢失编译期穷尽检查。**
   sealed + `switch` 让「新增一种状态」变成编译错误；
   `boolean` 永远不会编译失败，它只会在运行时走错分支。

**进一步追问**：「那用 `Optional<String>` 呢？空表示没减，有值表示终止原因。」
—— 仍然丢第 1 条：`Optional.empty()` 无法区分「跳过」与「生效但减了 0」。
而这两种情况在轨迹里的记录完全不同。

#### 追问二：为什么 `CappedPolicy` 要改名，`TracePolicy` 不改？

**期望的回答**：

- `CappedPolicy.name()` 返回 `"封顶 50（九折）"` —— 因为它**改变了行为**，
  用户看到的优惠名必须体现这个改变，否则「九折」减了 50 而不是 100，会被当成 bug。
- `TracePolicy.name()` 返回 `delegate.name()` —— 因为它**不改变行为**，
  它只是旁观。**观测手段不该出现在业务名称里。**

**判断标准**：装饰器有没有改变「被装饰者的输出」。
改变 → 名字必须变；不改变 → 名字必须不变。

**进一步追问**：「如果所有装饰器都改名会怎样？」
—— 名字会随包装层数增长：`追踪(封顶 50（九折）)`。
日志、轨迹、前端展示全都被观测细节污染。
**观测应该是透明的，这是它与业务装饰的根本区别。**

#### 追问三：两个节点同时写同一个互斥组，`ChainState` 会出问题吗？

**期望的回答**：

不会 —— 因为 `ChainState` **不是共享状态**。
它在 `DiscountPipeline.calculate()` 里**每次调用新建一个**，
是方法内部的局部对象，一次计算独占一个。

**如果换成单例或静态字段**，才会出现：

- 两个订单并发计算，A 的「券」组会挡住 B 的券；
- 轨迹会交错写入；
- 最终价格可能被两个线程同时 `reduce`，产生丢失更新。

**加分项**：指出这正是「可变对象也能安全」的前提 ——
**可变 + 不共享 = 安全**；不可变解决的是「共享」问题，不是「可变」问题（§8.4 误区六）。

### 9.3 五道动手题

前四道是正常需求，第五道是**反题** —— 它考察你会不会拒绝。

#### 题一：加一种「阶梯满减」

**要求**：`tier:100:20,200:50,500:150` —— 达到哪一档就减对应的额度。

**改哪里**：

| 步骤 | 文件 | 要点 |
|---|---|---|
| 1 | 新增 `TieredFullReductionPolicy` | 解析多档参数，选**满足条件的最高档** |
| 2 | `PolicyFactory` 注册表 | `map.put("tier", PolicyFactory::tiered)` |
| 3 | `PolicyFactory.helpText()` | 补一行格式说明与例子 |

**陷阱**：

- 最后一步必须调 `DiscountPolicy.legal(reduction, price)`，
  否则「500 减 150」遇到当前价 120 时会减出负数；
- 档位必须**按门槛升序校验**，否则 `200:50,100:20` 这种配置会选错档；
- 参数格式带逗号，而现有解析是按 `:` 切分的 —— 需要在策略内部再切一次，
  并保证错误消息指出是**哪一档**不合法。

#### 题二：把追踪输出写到文件

**要求**：不改 `TracePolicy` 的行为，只把输出从控制台换到文件。

**改哪里**：只改装配处。

```java
PrintStream file = new PrintStream(Files.newOutputStream(path), true, StandardCharsets.UTF_8);
PipelineFactory.builder()
        .traceOut(file)          // 构造器已支持任意 PrintStream
        .trace(true)
        .build();
```

**陷阱**：

- **谁负责 close**：`PipelineFactory` 不应该接管外部传入流的生命周期 ——
  它用完就丢，关闭责任在创建者。这也是为什么构造器接受 `PrintStream` 而不是路径；
- `PrintStream` 会吞掉 IO 异常（`checkError()` 才拿得到），
  真要严谨就得用 `Writer` 并处理异常；
- 中文字符必须显式指定 `UTF-8`，否则在 Windows 默认编码下会乱码。

**这道题想说明**：`TracePolicy` 的构造器有 `PrintStream out` 这个参数，
不是「为了测试方便」，而是**把输出目的地变成可配置的**。
一个只写 `System.out` 的装饰器，在容器化部署里等于把日志扔了。

#### 题三：给 `DiscountPolicy` 加 `priority()`，自动排序

**要求**：让策略自带优先级，`PipelineFactory` 按优先级排序，不再依赖 `stage()` 的书写顺序。

**正确答案：拒绝这个需求。**

**为什么**：

1. **顺序是运营的决策，不是算法的属性。**
   「满减先还是九折先」取决于当前活动的设计（§6.4 实测差 2 元），
   同一个满减策略在不同活动里可能排在不同位置。
2. **加 `priority()` 会让策略知道「我在链上的位置」。**
   这违反 §1.4 的角色边界：`DiscountPolicy` **禁止知道**自己前后有谁。
3. **排序会隐藏顺序。**
   现在 `stage()` 的书写顺序就是执行顺序，一眼可见；
   改成按 `priority` 排序后，读代码要先去每个策略里找优先级数字，
   而重复的优先级怎么办？又是一个新问题。

**如果一定要做**，正确的做法是：把优先级放在**装配层**（`PipelineFactory` 内部的 `Step` 加一个 `order` 字段），
而不是放在策略接口上。**位置信息属于链，不属于节点。**

**这道题想说明**：扩展点不是越多越好。
一个看起来"更灵活"的接口方法，可能把边界破坏掉。

#### 题四：让「跳过」支持「稍后重试」

**要求**：某个节点跳过时，等前面的节点都跑完，再回来试一次。

**为什么这道题很难**：

链是**单向**的（§7.11）。「稍后重试」意味着要么回溯、要么多轮：

| 方案 | 做法 | 代价 |
|---|---|---|
| A. 两轮执行 | 第一轮跳过记录，第二轮重跑跳过的节点 | 每轮的「当前价」不同，同一节点可能得出不同结果 |
| B. 延迟队列 | 跳过的节点进队列，链尾统一处理 | 顺序语义变了（不再是链） |
| C. 显式递归 | 节点自己再次调用 `process` | 终止语义被破坏，可能死循环 |

**正确的回答是**：「这需要先定义业务语义 ——
第二轮的『当前价』是哪个价？重试次数上限是多少？重试失败怎么记轨迹？」

**这道题想说明**：有些需求看起来是「加个功能」，
实际上是在问「你的执行模型是什么」。
**回答『做不了』并说明原因，比硬做出来更专业。**

#### 题五：把 `OrderContext` 的 Builder 换成 record + wither

**要求**：用 `record OrderContext(...)` 加 `withUserLevel(int)` 之类的方法替代 Builder。

**改哪里**：

1. `OrderContext` 改成 record，6 个组件；
2. 紧凑构造器里保留金额校验与 `money()` 归一；
3. 6 个 `withXxx()` 方法；
4. `OrderContext.builder("200").userLevel(3).build()` 的所有调用点（`Client` 里十几处）。

**陷阱**：

- `OrderContext.of("-5")` 的异常消息必须保留 —— record 的紧凑构造器可以做；
- `withXxx()` 会产生 6 个方法，而 Builder 只有 6 个 setter + 1 个 build，
  **代码量没有减少，只是形态变了**；
- Builder 允许「先设部分字段、最后统一校验」，
  record 的每个 `withXxx()` 都要重新走一遍构造器 —— 校验会被执行 6 次。

**这道题想说明**：**record + wither 不是「更现代所以更好」**。
在本例里，Builder 的优势恰恰是「延迟构造、一次校验」，
而这正是 `OrderContext` 需要的（价格必须最后校验，因为它是必填项）。

**判断标准**：字段少（≤3）且都必填 → record 直接构造；
字段多、有可选值、需要延迟校验 → Builder。

### 9.4 一句话收束

如果这篇文档只能记住一句话：

> **模式的价值不在"用了它"，而在"用它划出的那条边界能不能被验证"。**

本项目的边界是可以验证的：

- 改满减额度 → 只需改配置串（§7.1）
- 加一种折扣 → 只碰 `PolicyFactory` 的注册表（§7.3）
- 改顺序 → 只碰装配处（§6.4）
- 加封顶 → 只套一个装饰器（§7.5）
- 加互斥 → 只换一个方法名（§7.6）

**如果这五条里有任何一条做不到，说明边界划错了，而不是模式用少了。**

---

## 附录 A. 文件清单

### A.1 二十个源文件，按职责分组

**第一组：契约与模型（6 个文件，1086 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `DiscountPolicy.java` | 165 | 唯一的算法契约：`discount()` 返回减免额，外加三个静态工具（`money` / `legal` / `requireValidPrice`） |
| `Requirement.java` | 209 | 条件契约：只回答「这一步该不该算」，附 5 个工厂（always / minLevel / newCustomerOnly / withCoupon / memberDayOnly） |
| `OrderContext.java` | 252 | 订单的一次性输入：金额、等级、积分、新客、券、会员日，6 个 `final` 字段 + Builder |
| `DiscountDecision.java` | 165 | 决策三态（Applied / Skipped / Stopped），`continues()` 决定链的去留 |
| `AuditTrail.java` | 148 | 轨迹：每一步都能记录「谁、前后价、减多少、为什么」 |
| `ChainState.java` | 147 | 执行期累加器：当前价、轨迹、已占用互斥组、是否终止 |

**第二组：策略（3 个文件，291 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `FullReductionPolicy.java` | 99 | 满减：达到门槛减固定额，构造时校验「额度 < 门槛」 |
| `PercentagePolicy.java` | 84 | 打折：按实付比例计算，构造时校验 `(0, 1)` |
| `PointsDeductionPolicy.java` | 108 | 积分抵扣：每分抵多少、单笔最多抵多少，**看用户余额**（原项目不看） |

**第三组：装饰器（2 个文件，229 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `CappedPolicy.java` | 88 | 单节点封顶：把减免额压到上限以内，**会改名**（因为它改变了行为） |
| `TracePolicy.java` | 141 | 追踪：打印一行「入 / 出 / 减」，**不改名**（旁观者不该出现在业务名里） |

**第四组：责任链（3 个文件，453 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `DiscountHandler.java` | 204 | 链节点抽象：`process()` 是 `final` 模板方法，串起「检查终止 → 判定 → 减价 → 记录 → 下传」 |
| `PolicyHandler.java` | 111 | 普通节点：条件满足就调策略，否则跳过 |
| `ExclusiveGroupHandler.java` | 138 | 互斥节点：先查组是否被占，再算钱，生效后占组 |

**第五组：执行与结果（2 个文件，347 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `DiscountPipeline.java` | 169 | 执行器：持有链头，负责新建 `ChainState` 并把结果封装成 `DiscountResult` |
| `DiscountResult.java` | 178 | 结果值对象：不可变，`totalReduction()` 现算，提供用户/客服两种视图 |

**第六组：工厂（2 个文件，517 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `PolicyFactory.java` | 280 | 配置串 → `DiscountPolicy`：注册表 + 参数校验 + `helpText()` |
| `PipelineFactory.java` | 237 | 装配整条链：Builder + 每个节点的装饰器顺序 + 命名编号 |

**第七组：对照与入口（2 个文件，624 行）**

| 文件 | 行数 | 为什么存在 |
|---|---:|---|
| `LegacyOrderService.java` | 210 | 原项目行为的忠实复现，`@Deprecated`，只在 Demo 1/3/4 里调用 |
| `Client.java` | 414 | 十段 Demo 的唯一入口 |

**合计：20 个文件，3547 行。**

### A.2 依赖的两个工具类（不属于本示例，但缺了编译不过）

| 文件 | 位置 | 职责 |
|---|---|---|
| `Money.java` | `com/patterns/util/` | 金额的解析与展示：`of(String)` / `yuan(BigDecimal)` |
| `DisplayWidth.java` | `com/patterns/util/` | 按显示宽度对齐（中文算两格），让 Demo 输出整齐 |

**它们被本示例复用，而不是复制一份** —— 这是有意的：
金额格式化规则如果每个示例各写一份，就会出现「同一个 107.00 在不同示例里显示不同」。

### A.3 编译产物

| 项目 | 值 |
|---|---|
| `.class` 文件数 | 42 |
| 总字节数 | 110552 |

**42 个 class 来自 22 个源文件**（本示例 20 + `com/patterns/util` 2）。实测构成如下：

| 来源 | 数量 | 说明 |
|---|---:|---|
| 20 个源文件各自的主 class | 20 | 一个源文件一个 |
| `Money` 与 `DisplayWidth` | 2 | 复用的工具类 |
| `DiscountDecision` 的三个 record | 3 | `$Applied` / `$Skipped` / `$Stopped` |
| `Requirement` 的匿名类 | 7 | `$1` 到 `$7`：always / minLevel / newCustomerOnly / withCoupon / memberDayOnly / and / or |
| `LegacyOrderService` 的内部类型 | 5 | `$LegacyStrategy` 接口 + 日志装饰器 + 三个策略 |
| `OrderContext.Builder` | 1 | 内部类 |
| `PipelineFactory` 的 `Builder` 与 `Step` | 2 | 内部类 + 内部 record |
| `PolicyFactory.Creator` | 1 | 私有函数式接口 |
| `AuditTrail.Entry` | 1 | 内部 record |
| **合计** | **42** | —— |

**这张表的用途**：当你看到 `ls *.class` 有 42 个而源文件只有 22 个时，
不必怀疑「是不是多编译了别的包」—— 内部类、嵌套 record、匿名类
都会各自生成 class 文件。

---

## 附录 B. 编译运行与验证记录

### B.1 环境

| 项目 | 值 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25` |
| 编译命令 | `javac -Xlint:all -encoding UTF-8 -d <输出目录> <22 个源文件>` |
| 运行命令 | `java -Dstdout.encoding=UTF-8 -cp <输出目录> com.patterns.combined.orderservice.Client` |
| 源文件数 | 22（本示例 20 + `com/patterns/util` 2） |
| 控制台 | PowerShell 7（`pwsh -NoProfile`） |

### B.2 完整验证脚本

**下面这段脚本是本项目实际使用的验证脚本**（路径按自己的环境改）。
它做四件事：编译、运行、统计输出、计算哈希。

```powershell
$ErrorActionPreference = "Stop"
$jdk  = "F:\enCodingApps\JDK\jdk-25"
$root = "E:\codeFile\codeFather\design_patterns_pro"
$out  = Join-Path $root "build_combined_classes"
$txt  = Join-Path $root "build_combined_out.txt"

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null

$files = @()
$files += (Get-ChildItem "$root\src\com\patterns\combined\orderservice" -Filter *.java |
           ForEach-Object { $_.FullName })
$files += (Get-ChildItem "$root\src\com\patterns\util" -Filter *.java |
           ForEach-Object { $_.FullName })

"SOURCE_COUNT=$($files.Count)"
$compile = & "$jdk\bin\javac.exe" -Xlint:all -encoding UTF-8 -d $out $files 2>&1
"JAVAC_EXIT=$LASTEXITCODE"
if ($compile) {
    "JAVAC_OUTPUT_LINES=$(@($compile).Count)"
    $compile | Select-Object -First 10
} else {
    "JAVAC_OUTPUT_LINES=0"
}

$p = Start-Process -FilePath "$jdk\bin\java.exe" `
        -ArgumentList "-Dstdout.encoding=UTF-8", "-cp", $out, `
                      "com.patterns.combined.orderservice.Client" `
        -RedirectStandardOutput $txt -NoNewWindow -Wait -PassThru
"JAVA_EXIT=$($p.ExitCode)"
"RUN_LINES=$((Get-Content $txt).Count)"
"RUN_BYTES=$((Get-Item $txt).Length)"
"RUN_SHA256=$((Get-FileHash $txt -Algorithm SHA256).Hash)"
"CLASS_COUNT=$((Get-ChildItem $out -Recurse -Filter *.class).Count)"
"CLASS_BYTES=$((Get-ChildItem $out -Recurse -Filter *.class |
                Measure-Object Length -Sum).Sum)"
```

**三个容易踩的坑，脚本里都处理了：**

| 坑 | 处理 |
|---|---|
| `javac` 的警告走 stderr，直接看 `$LASTEXITCODE` 会漏掉内容 | `2>&1` 捕获到 `$compile`，并统计行数 |
| PowerShell 的 `>` 重定向会改写编码 | 用 `Start-Process -RedirectStandardOutput`，保持字节原样 |
| 控制台默认编码可能不是 UTF-8 | 运行时显式 `-Dstdout.encoding=UTF-8` |

### B.3 实测结果

**最近一次完整执行（本会话第四次复现）：**

```text
SOURCE_COUNT=22
JAVAC_EXIT=0
JAVAC_OUTPUT_LINES=0
JAVA_EXIT=0
RUN_LINES=152
RUN_BYTES=9797
RUN_SHA256=DF1E29B1FCAAF0C65242EADDFE16D34D90BC8CECC81FAF498193AE9190518DD7
CLASS_COUNT=42
CLASS_BYTES=110552
```

**与前三次运行完全一致：**

| 次数 | 行数 | 字节数 | SHA-256 前 8 位 |
|---|---:|---:|---|
| 第 1 次 | 152 | 9797 | `DF1E29B1` |
| 第 2 次 | 152 | 9797 | `DF1E29B1` |
| 第 3 次 | 152 | 9797 | `DF1E29B1` |
| 第 4 次（本次） | 152 | 9797 | `DF1E29B1` |

**完整哈希：**

```text
DF1E29B1FCAAF0C65242EADDFE16D34D90BC8CECC81FAF498193AE9190518DD7
```

### B.4 怎么看「零警告」

`JAVAC_OUTPUT_LINES=0` 表示 `javac -Xlint:all` 没有输出任何一行。

**注意 `-Xlint:all` 的含义**：它打开所有推荐的警告类别，
包括 `unchecked`（泛型未检查）、`deprecation`（使用已废弃 API）、
`serial`（可序列化类无 serialVersionUID）等。

**本项目里的一个真实细节**：`Client` 调用了 `@Deprecated` 的
`LegacyOrderService`，为什么没有产生 `deprecation` 警告？

因为 `Client` 自己也被标注了 `@SuppressWarnings("deprecation")`（在调用处），
这是**有意的**：Demo 必须调用旧实现才能做对照，
而调用点已经是最小范围。**抑制警告不是掩盖问题，是把范围缩到最小并写明理由。**

### B.5 旧实现的基线是怎么得到的

旧实现的 11 行输出（§2.1 逐字引用）来自：

```java
LegacyOrderService.placeOrder(150.0, "full", "percent", "points");
```

实测：**11 行 / 411 字节**。

**它与新实现的 Demo 1 输出逐字一致**（§6.1）——
这不是巧合，`LegacyOrderService` 存在的唯一目的就是保证这一点。

### B.6 复现失败的三种可能

| 现象 | 原因 | 处理 |
|---|---|---|
| `RUN_SHA256` 不一致，但行数一致 | 平台的换行符不同（`\r\n` vs `\n`） | 用 `Get-FileHash` 对比的是字节，换行符不同则哈希必然不同；对比时统一用同一平台 |
| `RUN_SHA256` 不一致，行数一致但字节数变成 8102 | 没指定编码，JVM 沿用平台默认的 GBK，中文由 3 字节/字变为 2 字节/字 | 不是代码被改过。本文的 `9797` / `DF1E29B1…` 是 **UTF-8** 口径；GBK 口径为 `8102` / `53928A97…`。两种口径各自稳定，关键是同一口径内比较 |
| `javac` 报编码错误 | 源文件含中文，但未加 `-encoding UTF-8` | 加上该参数 |
| 运行时输出乱码 | 控制台编码不是 UTF-8 | 加 `-Dstdout.encoding=UTF-8`，或先执行 `chcp 65001` |

**这三条都写在脚本里了。** 之所以单独列出来，是因为它们与业务逻辑无关，
却最容易让人误以为「代码有问题」。

---

## 附录 C. 交叉引用

本示例里的四个模式，各自都有一份独立文档。**先读单模式、再读组合**，
会比直接读本篇轻松得多。

### C.1 四个主角

| 模式 | 文档 | 什么时候该去看 |
|---|---|---|
| 策略 | [`../../behavioral/strategy/README.md`](../../behavioral/strategy/README.md) | 想弄清「算法怎么独立于调用方」；本示例的 `DiscountPolicy` 是它的最小形态 |
| 装饰器 | [`../../structural/decorator/README.md`](../../structural/decorator/README.md) | 想弄清「为什么不用继承」；`CappedPolicy` 与 `TracePolicy` 是它的两个变体 |
| 责任链 | [`../../behavioral/chainofresponsibility/README.md`](../../behavioral/chainofresponsibility/README.md) | 想弄清「纯链与管道的区别」；本示例的 `DiscountHandler` 与那份文档里的 `approval/` 同构 |
| 工厂方法 | [`../../creational/factorymethod/README.md`](../../creational/factorymethod/README.md) | 想弄清「简单工厂 / 工厂方法 / 抽象工厂」的区别；本示例只用了简单工厂 |

### C.2 容易混淆的邻居

| 模式 | 文档 | 与本示例的关系 |
|---|---|---|
| 状态 | [`../../behavioral/state/README.md`](../../behavioral/state/README.md) | 状态也是「行为随内部状态改变」，但它的转移由**状态自己**决定；责任链的去留由**决策**决定 |
| 观察者 | [`../../behavioral/observer/README.md`](../../behavioral/observer/README.md) | 观察者是「一对多通知」，`TracePolicy` 只做「单点旁路」，不要混为一谈 |
| 命令 | [`../../behavioral/command/README.md`](../../behavioral/command/README.md) | 如果折扣需要「撤销 / 重做」（§7.11），应该引入命令，而不是给链加重试 |
| 解释器 | [`../../behavioral/interpreter/README.md`](../../behavioral/interpreter/README.md) | 条件如果复杂到「支持与或非的表达式」，可以从 `Requirement` 走向解释器 |
| 模板方法 | [`../../behavioral/templatemethod/README.md`](../../behavioral/templatemethod/README.md) | `DiscountHandler.process()` 就是模板方法：父类定骨架，子类填 `decide()` |

### C.3 反向引用

如果从其他文档跳到这里，推荐按这个顺序读：

1. §1（现实案例）→ 知道要解决什么问题；
2. §6（十段 Demo）→ 看真实输出，建立直觉；
3. §4（十六个设计决定）→ 理解每个选择的取舍；
4. §5（逐文件解析）→ 对照源码；
5. §8（代价与误区）→ 知道什么时候不该这么写。

**跳过 §1 和 §6 直接读 §5，会看到二十个「不知道为什么存在」的类。**

---

## 附录 D. 术语表

按在本篇中出现的顺序排列。

| 术语 | 含义 | 在本项目中的位置 |
|---|---|---|
| **减免额** | 这一步「减掉多少钱」，非负、不超过当前价 | `DiscountPolicy.discount()` 的返回值 |
| **折后价** | 减完之后的应付金额，即「当前价 - 减免额」 | 由 `ChainState.reduce()` 维护 |
| **当前价** | 走到某一步时的价格，已经过前面所有节点 | `ChainState.price()` |
| **决策三态** | Applied / Skipped / Stopped | `DiscountDecision` |
| **跳过（Skipped）** | 这一步不生效，**链继续** | `PolicyHandler` 在条件不满足时返回 |
| **终止（Stopped）** | 到此为止，**后面全部不执行** | `ExclusiveGroupHandler` 在组冲突时返回 |
| **互斥组** | 一组「只能用一个」的优惠，组名是数据 | `ExclusiveGroupHandler` 的 `group` 参数 |
| **轨迹（trail）** | 每一步的记录：谁、前后价、减多少、三态、原因 | `AuditTrail` |
| **累加器** | 执行过程中不断变化的对象，不对外共享 | `ChainState` |
| **装配** | 把配置文本变成「链 + 装饰器」的对象图 | `PipelineFactory` |
| **注册表** | 折扣码 → 创建函数的映射，保序、不可变 | `PolicyFactory.REGISTRY` |
| **密封接口** | `sealed interface`，限定实现者，可做穷尽 `switch` | `DiscountDecision` |
| **穷尽检查** | 编译器要求 `switch` 覆盖所有子类型 | 对 sealed 类型的 `switch` |
| **派生值** | 能由其他字段算出来、因此不存字段的值 | `DiscountResult.totalReduction()` |
| **派生视图** | 同一份数据的另一种投影 | `render()` / `renderUserFacing()` |
| **契约** | 一个方法对输入输出的硬性约定，违反即 bug | `discount()` 的 `0 ≤ 减免 ≤ 价格` |
| **横切能力** | 与具体算法无关、可套在任何算法外面的能力 | 封顶、追踪 |
| **被装饰者** | 装饰器内部持有的那个对象 | `CappedPolicy` 的 `delegate` |
| **模板方法** | 父类固定流程骨架，子类只填可变步骤 | `DiscountHandler.process()` |
| **可复现** | 同一输入在同一环境下产生逐字节相同的输出 | §6.11 的 152 行 / 9797 字节 |

---

## 附录 E. 缺陷取证台账

本附录把 §2 的七个问题、以及 §2.5 的四个数值缺陷，
逐条对应到「证据 / 根因 / 新实现的应对 / 是否彻底解决」。

**「是否彻底解决」这一列是刻意保留的** —— 有几条只是「显式化」，
不是「消除」，必须如实标注。

### E.1 七个结构问题

| # | 问题（§2 编号） | 证据 | 根因 | 新实现的应对 | 彻底解决？ |
|---|---|---|---|---|---|
| P1 | 管道冒充责任链（2.4） | `apply()` 里 `handle` 与 `next.apply` 都是无条件 | 缺少「跳过」与「终止」两种语义 | `DiscountDecision` 三态 + `process()` 的 `final` 骨架 | ✅ |
| P2 | 金额用 `double`（2.5） | 见 E.2 的四个数值缺陷 | 二进制浮点无法精确表示十进制小数 | `BigDecimal` + `money()` 统一两位小数 + `legal()` 约束 | ✅ |
| P3 | 策略不看上下文（2.6） | `PointsDeductionStrategy` 固定减 10 | 策略内部没有「该不该执行」的判断入口 | `Requirement` 独立 + `PointsDeductionPolicy` 读余额 | ✅ |
| P4 | 日志关不掉、不带身份（2.7） | 6 行 `【日志】`，没有策略名，无法关闭 | 日志写死在装饰器里，且与业务强绑 | `TracePolicy`（可开关、可重定向、带名字）+ `AuditTrail`（结构化轨迹） | ✅ |
| P5 | 顺序由调用方传参决定（2.8） | 150 元两种顺序 → 107 vs 105 | 顺序是 `List` 的遍历顺序，没有任何规则 | 顺序成为 `PipelineFactory` 的 `stage()` 书写顺序，显式、可审查 | ⚠️ 只做到「显式化」 |
| P6 | 工厂错误消息不可操作（2.9） | `未知的折扣类型: fullx` | 消息只说了「错了」 | `helpText()` + 五类校验消息（§6.9） | ✅ |
| P7 | 没有结果对象（2.10） | 只有 `原始价格` 与 `最终支付金额` 两个数字 | 返回值是 `double` | `DiscountResult`（不可变、带轨迹与三态） | ✅ |

**P5 为什么只算「显式化」：**

数学上，满减与打折的先后顺序**必然**影响结果 —— 因为满减的门槛看的是「当时的价」。
新实现没有、也不可能改变这个数学事实。

它做的是把「顺序」从「调用方传参的偶然结果」变成「装配处的显式配置」，
并让两种顺序的结果可以被并排对比（§6.4）。**从「说不清」变成「说得清」。**

### E.2 四个可复现的数值缺陷

全部由 `LegacyOrderService` 在 JDK 25 下实测得到。

| # | 输入 | 实测输出 | 缺陷 |
|---|---|---|---|
| N1 | `silentPrice(11.0, "percent", "points")` | `-0.09999999999999964` | 应付为负，平台倒找钱 |
| N2 | `silentPrice(58.8, "percent", "points")` | `42.919999999999995` | 用户看到一串小数 |
| N3 | `silentPrice(150.0, "points")`（模拟 0 积分） | `140.0` | 没有积分也减 10 元 |
| N4 | `silentPrice(150.0, "full", "percent")` vs `("percent", "full")` | `107.0` vs `105.0` | 结果取决于传参顺序 |

**N1 的算式展开：**

```text
11.0 → 打九折：11.0 - 11.0 * 0.1 = 9.9
     → 积分固定减 10：9.9 - 10 = -0.09999999999999964
```

**N2 的算式展开：**

```text
58.8 → 打九折：58.8 - 5.880000000000001 = 52.919999999999995
     → 积分固定减 10 = 42.919999999999995
```

**N3 的新实现对照**：`PointsDeductionPolicy` 会读 `context.availablePoints()`，
余额为 0 时返回 `ZERO`，并由 `unavailableReason()` 给出「积分为 0」之类的说明。
**这一步不再是「减 10 元」，而是「跳过」。**

**N4 的新实现对照**：两种顺序都可以装配出来，结果依然是 107 与 105（数学不变），
但 `PipelineFactory` 的 `stage()` 顺序就是执行顺序，任何人都能一眼看出差异从哪来。

### E.3 一个单独验算的数值

§2.5 还引用了第三个数值，它**不是**通过 `silentPrice` 得到的，而是单独验算：

```text
99.9 * 0.1 = 9.990000000000002
```

**「减了多少钱」这个中间值本身就是不准的。** 而原项目的日志恰好把它打印了出来。

新实现里这个值由 `DiscountPolicy.money()` 统一处理：
`setScale(2, RoundingMode.HALF_UP)`，结果是 `9.99`。

### E.4 取证方式

本节所有旧实现的数字，都来自同一个入口：

```java
LegacyOrderService.silentPrice(...)   // 只算不打印，避免日志污染数值对照
```

以及：

```java
LegacyOrderService.placeOrder(150.0, "full", "percent", "points")   // 完整输出 11 行
```

**为什么要有 `silentPrice` 这个「只算不打印」的方法？**
因为原项目的每个策略都会 `System.out.println`，
如果直接用它做数值对照，输出里会混进大量日志，
**读者无法判断某个数字到底是计算结果还是日志内容。**

把「算」与「说」分开，是取证的第一步。

---

## 附录 F. 版本与约定

### F.1 代码约定

| 约定 | 内容 | 理由 |
|---|---|---|
| 金额类型 | 一律 `BigDecimal`，禁止 `double` / `float` | 十进制精确表示 |
| 金额精度 | 统一 `setScale(2, RoundingMode.HALF_UP)` | 全链路一致的舍入 |
| 金额输入 | 用字符串构造（`new BigDecimal("0.1")`） | 避免把二进制误差带进来 |
| 集合 | 会出现在输出里的集合一律用有序实现 | 输出可逐字节复现 |
| 不可变 | `OrderContext` / `DiscountResult` / `DiscountDecision` / `PolicyFactory` 的注册表 | 输入与输出只读 |
| 可变 | 只有 `ChainState`，且不对外共享 | 执行期累加器 |
| 链的后继 | `private final`，装配时一次串好 | 顺序只有一个入口 |
| 模板方法 | `process()` 为 `final` | 子类不能改变链的推进规则 |
| 扩展入口 | 新增折扣只改 `PolicyFactory`（注册 + 帮助文本） | 单一扩展点 |
| 旧实现 | `@Deprecated`，只在对照 Demo 中调用 | IDE 画出删除线，一眼识别反面教材 |

### F.2 文档约定

| 约定 | 内容 |
|---|---|
| 所有输出 | **实测**，逐字引用，不做美化、不省略 |
| 所有数字 | 可由附录 B 的脚本复现 |
| 行数 | 物理行数（含空行与注释），由 `Get-Content` 统计 |
| 引号 | 正文用「」，代码用 `"` |
| 章节引用 | `§N.M` 形式，指向本文内的编号 |
| 跨文档引用 | 相对路径链接（附录 C） |
| ✅ / ❌ | 表示「是 / 否」，⚠️ 表示「部分做到」 |
| 未实现的需求 | 明确写「未实现」，并说明应该怎么设计（§7.7） |

### F.3 本文不做什么

**明确划出边界，避免读者期待落空：**

| 不做 | 原因 |
|---|---|
| 不贴二十个文件的完整源码 | 源码在 `.java` 里，重复贴会让文档失去重点；§5 只贴关键片段 |
| 不引入 Spring / 任何框架 | 任务要求原生 Java，且框架会掩盖模式本身 |
| 不做性能基准测试 | 本项目是教学示例，`BigDecimal` 与 `double` 的性能差异不影响结论 |
| 不写单元测试 | 示例项目的取舍；但结构上每个类都可单独测试（§8.1） |
| 不实现「跨境不参与」「整单封顶」等需求 | 它们是扩展练习（§7.7、§7.5），保留给读者动手 |
| 不讨论分布式 / 并发 | 单线程示例；并发相关的唯一结论是「`ChainState` 不共享」（§9.2 追问三） |

### F.4 一句话总结

如果这份文档只能留一个结论：

> **优惠引擎的难点不在算钱，在「算完之后能不能说清」。**

十个 Demo 里，真正算错的只有旧实现的四次；
其余六次，演示的都是「怎么把算对的过程**讲出来**」：

- 为什么是 107 而不是 105（顺序）
- 为什么这步没减（跳过原因）
- 为什么后面不算了（终止）
- 为什么九折只减了 13（上一层留下的价）
- 为什么封顶之后是 50 而不是 100（装饰器顺序）
- 为什么用户看不到跳过项（视图裁剪）

**这六个「为什么」，才是这套二十个文件换来的东西。**

---

*本文档的所有结论均基于 `com.patterns.combined.orderservice` 包下的 20 个源文件，
以及附录 B 记录的实测输出。*
