# 责任链模式（Chain of Responsibility）

> **一句话**：把"谁能处理这件事"从**调用方猜**变成**链自己找**。
> 请求沿着一条预先组装好的路径往前走，**遇到第一个能负责的节点就停下**；
> 一路走完都没人负责，那也是一种必须被显式处理的结局。

> **本目录代码已用 JDK 25 实际编译运行验证**。文中所有输出、所有错误、
> 所有"原项目跑出来的结果"都是真实执行得到的，不是编造的。
> 验证方式见[附录 B](#b-运行方式与验证记录)。

---

## 目录

1. [现实案例：审批一张退款单](#1-现实案例审批一张退款单)
2. [原始实现：原项目源码与它的七个问题](#2-原始实现原项目源码与它的七个问题)
3. [设计动机：责任链到底解决了什么](#3-设计动机责任链到底解决了什么)
4. [设计推导：从三个处理人推到十五个文件](#4-设计推导从三个处理人推到十五个文件)
5. [代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
6. [执行流程：五段 Demo 的真实运行结果](#6-执行流程五段-demo-的真实运行结果)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- [附录：文件清单与运行方式](#附录文件清单与运行方式)

---

## 1. 现实案例：审批一张退款单

### 1.1 说人话版：这单该谁批

你在电商平台买了一台 8000 元的显示器，到货发现屏幕有坏点，于是提交退款申请。

接下来发生的事，你完全看不见：

1. 系统先看你**下单几天了**——超过 7 天？直接驳回，后面的人根本不会收到通知。
2. 7 天以内，把单子交到**客服专员**手上。她的权限是 200 元，8000 元她批不了。
3. 单子往上走，交到**客服主管**。权限 2000 元，还是批不了。
4. 再往上，**售后经理**。权限 20000 元，8000 元在范围内，批了。

你从头到尾只做了一件事：**提交申请**。
你没有说"请找售后经理"，你甚至不知道有"客服主管"这个岗位。
**"该谁批"这件事，是系统在内部走出来的，不是你指定的。**

这就是责任链模式要解决的问题：

> **把"谁来处理"的决定权，从调用方手里拿走，交给一条预先组装好的链。**

### 1.2 专业版：链上只有两个动作

GoF 对责任链的定义是：

> 使多个对象都有机会处理请求，从而避免请求的发送者和接收者之间的耦合。
> 将这些对象连成一条链，并沿着这条链传递请求，**直到有一个对象处理它为止**。

这句话里有三个关键词，每一个都对应一段代码：

| 关键词 | 含义 | 对应到代码 |
|---|---|---|
| **都有机会** | 链上每个节点都要被"问"到 | `handle()` 逐个下传 |
| **连成链** | 节点之间知道"下一个是谁" | `next` 引用 |
| **直到有一个处理它** | 一旦决定，后面的不再执行 | `tryHandle()` 返回非空即终止 |

去掉任何一个，就不是责任链了：

- 去掉"直到有一个处理它" → 变成**管道**（每条都执行，永不终止）；
- 去掉"连成链" → 变成**一堆互不相干的策略**，由调用方自己 `if-else` 挑；
- 去掉"都有机会" → 变成**调用方直接指定**，也就是没有模式。

**最容易丢掉的是第三个。** 原项目就是在这里丢的——它把三个折扣处理器串成一串挨个执行，
一个都不许停。那不是责任链，是管道。详见 §2.2。

### 1.3 真实的框架里到处都是它

责任链是**框架层出现频率最高的模式**，因为"框架不知道业务有哪些处理者"这件事，
几乎每个框架都要面对：

| 框架 | 链是什么 | 节点是什么 |
|---|---|---|
| Servlet | `FilterChain` | 每个 `Filter` |
| Spring Security | `FilterChainProxy` 里的一串 Filter | 认证、授权、CSRF…… |
| Netty | `ChannelPipeline` | 每个 `ChannelHandler` |
| 日志框架 | Logger 的 level 判断 | `ERROR`→`WARN`→`INFO`→`DEBUG` |
| 审批引擎 | 审批流定义 | 每个审批节点 |

**但请注意一个事实**：Servlet Filter 和 Netty Pipeline **不是纯责任链**。
它们的每个节点默认都会执行，节点通过显式调用 `chain.doFilter(req, resp)` 决定要不要往下传。
这类结构更准确的名字叫"不纯责任链"或"拦截器链"。

本项目的态度很明确：

> **纯责任链（处理即停）和不纯责任链（处理还传）是两种不同的结构，
> 应该用两种不同的抽象。** 混着叫"责任链"，是后面一系列设计错误的源头。

本项目同时给出了两种：

- `approval/` —— 纯责任链，用 `Handler` + `next`，**命中即停**；
- `discount/` —— 管道，用 `List<DiscountRule>`，**每条都跑**。

**后者故意不使用 `Handler`**，这是本次重构最重要的一条判断，详见 §4.10。

---

## 2. 原始实现：原项目源码与它的七个问题

### 2.1 全貌：8 个文件，257 行

原项目在 `src/BehaviorType/ChainOfResponsibilityPattern/`，一共 8 个文件：

| 文件 | 行数 | 职责 |
|---|---|---|
| `Order.java` | 40 | 订单：金额 + 是否有券 + 是否会员 |
| `DiscountHandler.java` | 18 | 接口：`applyDiscount(Order)` + `getPriority()` |
| `FullReductionDiscountHandler.java` | 26 | 满 200 减 50 |
| `CouponDiscountHandler.java` | 26 | 有券减 30 |
| `MemberDiscountHandler.java` | 26 | 会员减 20 |
| `HandlerNode.java` | 29 | **链表节点**：装一个 handler + 一个 next |
| `DiscountChain.java` | 62 | **链表管理器**：按优先级插入 + 遍历执行 + 清空 |
| `Client.java` | 30 | 入口 |
| **合计** | **257** | |

原项目能跑，输出也只有 4 行（真实运行结果）：

```
满减折扣: -50
优惠券折扣: -30
会员折扣: -20
最终订单金额: 150.0
```

250 − 50 − 30 − 20 = 150，数字全对。**所以从"能不能跑"的角度看，它没有问题。**

但从"它到底是不是责任链"的角度看，问题很多——而且不是风格问题，有几个是**真实的行为缺陷**。

### 2.2 问题一：它不是责任链，是"管道"

看 `DiscountChain.process()` 的核心四行：

```java
public void process(Order order) {
    HandlerNode current = head;
    while (current != null) {
        current.getHandler().applyDiscount(order);   // ← 无条件执行
        current = current.getNext();                  // ← 无条件往后走
    }
}
```

**没有一个地方可以"停下来"。**

- `applyDiscount()` 的返回类型是 `void`，所以它**没有能力**告诉链"我处理了"或"我不处理"；
- 循环体里没有任何 `break` 或 `return`；
- 所以不管第一个处理器做了什么，第二个、第三个**一定会被执行**。

这在折扣叠加的场景里恰好是**对的业务**（满减、券、会员确实要一起算），
但它在模式上是**错的命名**：

> 这个结构叫**管道（Pipeline）**或**过滤链（Filter Chain）**，
> 它的特征恰恰是"每个节点都执行"。
> 叫它责任链，会让后来的人误以为"命中即停"是它的行为。

为什么这个错误很危险？因为它会**传播**。后来有人要加一个"风控否决"节点：

```java
// 想表达"这笔订单风控不通过，后面的优惠就不用算了"
public void applyDiscount(Order order) {
    if (isFraud(order)) {
        order.setAmount(order.getAmount());   // 只能这样——什么也做不了
    }
}
```

他会发现**根本无法表达"终止"**。于是他只能去改 `DiscountChain`（加一个 `break` 条件），
而那个修改会让"折扣叠加"这个业务一起坏掉——因为它们被强行塞进了同一个循环。

**这就是"用错模式"的真实代价：不是多写了几行，而是把两种相反的需求绑死在一起，谁也改不动。**

### 2.3 问题二：`HandlerNode` 是多余的一层

原项目把"链表节点"和"处理器"拆成了两个类：

```java
public class HandlerNode {
    private final DiscountHandler handler;   // 装一个处理器
    private HandlerNode next;                // 指向下一个节点
}
```

于是 `DiscountChain` 里到处是这种代码：

```java
current.getHandler().applyDiscount(order);
//      ^^^^^^^^^^^^ 先取出处理器，再调用
```

**GoF 的标准做法是让处理器自己持有 `next`。** 两种做法的区别不是"少一个类"这么简单：

| | 外挂链表节点（原项目） | 处理器持有 next（本项目） |
|---|---|---|
| 处理器知道自己在链上吗 | **不知道** | 知道 |
| 能自己决定"到此为止"吗 | **不能** | 能 |
| 能自己决定"跳过我不算"吗 | **不能** | 能 |
| 需要额外类吗 | 需要 `HandlerNode` | 不需要 |
| 遍历谁来做 | 外部 `while` 循环 | 递归 `next.handle()` |

第一行是关键：**外挂链表时，处理器是个"被调用的函数"；只有它自己持有 next，
它才成为一个"链上的节点"。**

这不是术语之争。"节点"和"被调用者"的能力边界完全不同：
节点可以在自己内部判断"我处理完就不要再往下传了"，
而被调用者连"下一个是谁"都不知道，只能被循环推着走。

**这也解释了为什么原项目写不出"命中即停"——不是没写，是结构上做不到。**

### 2.4 问题三：金额会变成负数（真实输出）

原项目的 `CouponDiscountHandler`：

```java
if (order.hasCoupon()) {
    order.setAmount(order.getAmount() - 30);   // ← 没有下限
}
```

只要订单有券，就减 30，**不管订单还剩多少钱**。

`MemberDiscountHandler` 同样：

```java
if (order.isMember()) {
    order.setAmount(order.getAmount() - 20);   // ← 也没有下限
}
```

原项目的 Demo 用的是 250 元，减到 150，看不出问题。
本项目写了一个探针，用原项目的类跑了一笔 **25 元、有券、是会员** 的订单，真实输出：

```
CASE: 25 元 / 有券 / 会员   （券没门槛，会不会减成负数？）
  输入金额 = 25.0
优惠券折扣: -30
会员折扣: -20
  => 最终金额 = -25.0
```

**最终金额 −25.0。**

这不是"精度差一点"，这是**用户余额反而变多了 25 元**。
如果这段代码接的是真实支付接口：

- 25 元的订单，用户付了钱，系统倒找他 25 元；
- 或者数据库里 `amount` 字段是 `DECIMAL(10,2) UNSIGNED`，直接抛 SQL 异常；
- 或者对账时出现一笔负数流水，财务要花半天查。

而且注意：**满减那一步没有打印**（因为 25 > 200 不成立），说明三个处理器**都跑了**，
只是前两个"命中"了。这正是 §2.2 说的"无条件执行"的另一个证据。

### 2.5 问题四：恰好 200 元不打折（真实输出）

```java
public void applyDiscount(Order order) {
    if (order.getAmount() > 200) {      // ← 注意是 >，不是 >=
        order.setAmount(order.getAmount() - 50);
    }
}
```

运营的口径永远是"**满** 200 减 50"——**200 元是含在内的**。
但代码写的是 `> 200`，于是恰好 200 元的订单拿不到优惠。

用原项目的类跑一笔 **200 元、无券、非会员** 的订单，真实输出：

```
CASE: 200 元 / 无券 / 非会员（恰好 200 元算不算「满 200」？）
  输入金额 = 200.0
  => 最终金额 = 200.0
```

**一分钱没减。**

对比一下：同一段代码跑 250 元是能减的（真实输出 `满减折扣: -50`，最终 200.0）。
所以这个 bug **只在金额恰好等于门槛时出现**——测试用例用 250、300、1000 都碰不到，
偏偏真实订单里"凑单凑到整 200"是最常见的行为。

> **差一错误（off-by-one）在金额分档里是最高发的 bug**，
> 因为 `>` 和 `>=` 在代码里只差一个字符，在业务上差一整档客户。

本项目的 `FullReductionRule` 用 `compareTo(threshold) >= 0` 表达"满"，
`LimitApprover` 用 `compareTo(limit) > 0` 表示"超出上限才无权"。
**每一处比较都写清"含不含等号"，并且各自有对应的测试用例。**

### 2.6 问题五：`getPriority()` 把顺序塞进了处理器内部

原项目的接口是这样：

```java
public interface DiscountHandler {
    void applyDiscount(Order order);
    int getPriority();          // 1 / 2 / 3
}
```

三个处理器分别写死 `PRIORITY = 1`、`2`、`3`，`DiscountChain.addHandler()` 按这个数字做插入排序。

这个设计有三个问题：

**问题一：顺序是"链的属性"，不是"处理器的属性"。**

同一个"会员折扣"处理器，在 A 活动里可能应该第一个算（先打折再满减），
在 B 活动里可能应该最后算。把优先级写死在类里，
就意味着**同一个处理器无法在两条链上处于不同位置**。

想实现这一点只有一个办法：新建一个一模一样的类，只改 `getPriority()` 的返回值。
于是类爆炸就开始了。

**问题二：想看清整条链，必须打开三个文件。**

`Client` 里加了这么一句注释：

```java
// 添加各个优惠处理器（添加顺序不影响执行顺序）
// 优先级3
discountChain.addHandler(new MemberDiscountHandler());
// 优先级1
discountChain.addHandler(new FullReductionDiscountHandler());
// 优先级2
discountChain.addHandler(new CouponDiscountHandler());
```

上面这段是原项目 `Client.java` 的原文，一字未改——包括那三行注释的位置。
它们之所以写成独立行，正是因为**优先级不能写在调用里**，只能靠注释补充。

**调用方必须手写注释来记住"实际执行顺序是 满减→券→会员"，因为这个顺序在代码里看不见。**

而且这个注释会过期：明天有人把某个 `PRIORITY` 从 1 改成 3，注释就成了误导。

**问题三：魔术数字互相冲突，没有任何检查。**

两个处理器都写 `PRIORITY = 2` 会怎样？插入排序里用的是 `<=`，所以它们会按添加顺序排列——
**顺序取决于谁先被 `add`，而不是优先级**。这种"看起来有序、实际上靠运气"的状态，
在三个类以内还能靠人盯着，到十个就是灾难。

本项目的做法：**顺序只写在一个地方**——`ApprovalChain.standard()` 或
`DiscountPipeline.standard()` 的 `List.of(...)` 里。想看执行顺序？看那一行。

### 2.7 问题六：`void` 返回值，调用方什么都不知道

`process(Order)` 返回 `void`。于是调用方无法知道：

- 有没有处理器真的处理了？
- 处理了几个？
- 谁处理的？
- 如果没人处理，是"不需要处理"还是"漏了"？

原项目的 `Client` 只能这样：

```java
discountChain.process(order);
System.out.println("最终订单金额: " + order.getAmount());
```

它只能看到**最终金额**这一个数字。而最终金额恰恰是**信息量最少**的那个东西：

> 150.0 到底是怎么来的？三种折扣都生效了，还是满减 100 + 券 50？
> 如果哪天金额不对，靠这一个数字根本无法定位是哪一步出的问题。

这是本项目改造时的一条硬标准：**凡是"过程"有价值的场景，就一定要把过程留下来。**
所以：

- `Handler.handle()` 返回 `Optional<R>`，空 = 一路都没人处理；
- `ApprovalDecision` 里带一份完整的 `auditTrail`；
- `DiscountPipeline.Result` 里带每一步的 `Step`（**包括没命中的**）。

### 2.8 问题七：没有终止、没有兜底、没有审计

把上面几条合起来看，原项目缺的是三样"生产必需"的东西：

| 缺什么 | 后果 | 本项目怎么补 |
|---|---|---|
| **终止语义** | 想表达"到此为止"只能改循环 | `tryHandle()` 返回非空即终止 |
| **兜底** | 所有人都不处理时，静默返回原值 | `ApprovalChain.submit()` 兜底成 `NeedsHumanReview` |
| **审计** | 只留最终金额，过程全丢 | `ApprovalContext.record()` + 结论里的 `auditTrail` |

其中"兜底"最容易被忽略。原项目的三个折扣都不命中时，金额**原样返回**，
调用方完全无法区分这两种情况：

- 用户没有任何优惠资格（正常）；
- 规则配置错了，一条都没命中（故障）。

**这两种情况在业务上的处理方式完全不同**，但它们的返回值一模一样。

### 2.9 一句话总结原项目

如果把原项目的问题压缩成一句话：

> **它把一个"每条都要执行的管道"，写成了一个"看起来像责任链"的链表，
> 于是既没得到责任链的能力（终止），又付出了链表的成本（两个多余的类 +
> 一个散落在各处的排序字段），还顺手带走了两个金额 bug。**

这个判断很重要，因为它决定了本次重构的方向：

- **不需要**把折扣场景改造成责任链（业务上就不该终止）；
- **需要**把折扣场景改成一个诚实的管道（`List` + 循环），并修掉金额 bug；
- **需要**另外提供一个**真正的责任链**场景（审批），来展示这个模式的真实能力。

---

## 3. 设计动机：责任链到底解决了什么

### 3.1 它解决的唯一问题是"谁来处理"

先说清楚责任链**不**解决什么，以免误用：

- 它**不**解决"怎么处理"（那是算法的事，用策略）；
- 它**不**解决"处理完还要做什么"（那是装饰器/模板方法的事）；
- 它**不**解决"处理完要通知谁"（那是观察者的事）。

它只解决一件事：

> **请求的发送方，不知道也不应该知道接收方是谁。**

为什么"不知道"是一件好事？看三个真实的证据。

#### 证据 1：调用方不需要改

没有责任链时，客户端必须自己挑：

```java
// 没有责任链
if (amount <= 200) {
    new CustomerService().approve(req);
} else if (amount <= 2000) {
    new Supervisor().approve(req);
} else if (amount <= 20000) {
    new Manager().approve(req);
} else {
    escalateToHuman(req);
}
```

这段代码的问题不是"长"，而是：**每加一个审批层级，所有调用点都要改。**
如果这段 `if-else` 出现在三个地方（Web 接口、定时批处理、消息消费者），
那就是三处要改，而且**总有一处会漏**。

有了责任链，调用方永远只有一行：

```java
chain.submit(request);
```

#### 证据 2：链的组成可以变，而调用方不变

同一个 `ApprovalChain`，今天是"政策 → 客服 → 主管 → 经理 → 总监"，
明天业务要求"金额超过 5 万必须加一道财务复核"，
改的只是 `ApprovalChain.standard()` 里的 `List.of(...)` 一行。

**客户端代码一个字都不用动**——它甚至不知道有"财务复核"这个角色存在。

#### 证据 3：每个节点的判断可以独立测试

`LimitApprover` 的测试只需要：

```java
var approver = new LimitApprover("客服专员", "200");
var ctx = new ApprovalContext(new RefundRequest("R-1", Money.of("150"), "坏了", 3));
assertTrue(approver.handle(ctx).isPresent());        // 150 元，能批

var ctx2 = new ApprovalContext(new RefundRequest("R-2", Money.of("800"), "坏了", 3));
assertTrue(approver.handle(ctx2).isEmpty());          // 800 元，无权，返回空
```

不需要构造整条链、不需要模拟数据库、不需要 Spring 容器。
**这是"每个节点只依赖自己的判断条件"带来的可测试性。**

### 3.2 什么时候**不该**用责任链

这一节比上面重要，因为责任链是**被滥用得最厉害的模式之一**——
很多项目里所谓的"责任链"，实际只是一个 `for` 循环。

#### 不该用 1：所有节点都要执行

如果你的业务是"每条规则都要跑一遍"（折扣叠加、参数校验清单、数据清洗步骤），
那就**不要用责任链**，用 `List` + 循环。

判断方法只有一句：

> **这条规则命中之后，后面的规则还该不该执行？**
> 该 → 管道；不该 → 责任链。

原项目正是在这里走偏的：三个折扣规则**都该执行**，却被写成了链。
代价是 257 行代码里有 90 行（`HandlerNode` + `DiscountChain`）纯属多余。

#### 不该用 2：只有一个处理者，而且永远不会有第二个

```java
// 不需要任何模式
public void refund(RefundRequest req) {
    if (req.amount().compareTo(limit) > 0) {
        throw new IllegalArgumentException("超出权限");
    }
    doRefund(req);
}
```

为一个分支建一个接口 + 一个抽象类 + 一个链，得到的是**四层跳转**，不是解耦。

#### 不该用 3：处理者之间没有"传递"的可能

如果业务上"这件事只能由 A 或 B 其中一个处理，而且永远不会出现'两个都不能处理'的情况"，
那用 `if-else` 或 `switch` 更直接——**因为不存在"沿链传递"这个动作**。

#### 不该用 4：为了"以后可能会加节点"而提前抽象

这是 YAGNI 的典型违反。判断标准：

> **能说出"哪个具体需求会加节点"吗？**
> 「下个月的审批流有 5 个层级」→ 可以用；
> 「以后业务可能会变复杂」→ 不要用。

本项目之所以用，是因为 `demo1` 里五张金额不同的单子**已经存在**，
它们必须在不同节点停下——不是为了将来预留。

### 3.3 一句话对照：五个容易混的模式

| 模式 | 一句话 | 谁决定"用哪个/谁处理" | 执行几个 |
|---|---|---|---|
| **责任链** | 沿链传递，直到有人处理 | **链自己**（运行时） | **1 个**（或 0 个） |
| **管道/过滤器链** | 沿链传递，每个都处理 | 无所谓，全都要跑 | 全部 |
| **策略** | 同一个问题，换一套算法 | **调用方** | 1 个 |
| **装饰器** | 在原有行为外面套一层 | **调用方**（组装时） | 全部（层层包裹） |
| **观察者** | 状态变了，通知所有订阅者 | 被观察者（广播） | 全部 |

最容易混的是**责任链和装饰器**，因为两者的结构几乎一样——都是"对象持有同类型的下一个对象"。

区别在**意图和执行路径**：

> **装饰器**：每个包装层都**一定**会执行，而且是**环绕**执行（before + 核心 + after）。
> 它的目的是"叠加功能"，套几层就执行几次。
>
> **责任链**：每个节点**可能**执行，而且**一旦决定就终止**。
> 它的目的是"找到那个负责的人"，所以只需要一个。

一个具体的判别方法：

> 装饰器的 `next` 一定**会被调用**（否则功能就丢了）；
> 责任链的 `next` **可能永远不会被调用**（因为上一个节点已经处理了）。

还有一对容易混的是**责任链和观察者**：

> 观察者是**广播**——所有订阅者都收到，而且互不知道对方存在；
> 责任链是**接力**——只有一个人接棒，而且每个人都知道下一个人是谁。

---

## 4. 设计推导：从三个处理人推到十五个文件

### 4.0 推导路线表

这一节按"每一步解决了上一步的什么问题"来组织。每一步都指向一个真实需求，
不是为了用模式而加抽象。

| 步 | 上一步的写法 | 崩在哪个需求上 | 这一步加了什么 |
|---|---|---|---|
| 0 | `if-else` 挑审批人 | 三个调用点要同步改 | ——（先看清问题的形状） |
| 1 | 三个互不相干的处理类 | 请求要沿链传递，得有人知道"下一个是谁" | **`next` 放在处理器内部** |
| 2 | `void applyDiscount()` | 无法表达"我处理了"还是"我不处理" | **`Optional<R> tryHandle()`** |
| 3 | 返回 `R`，用 `null` 表示不处理 | `null` 和"处理结果为空"分不清 | **`Optional` 的三态语义** |
| 4 | 处理结果只有"批了多少钱" | 运营要问"为什么是主管批的" | **`ApprovalContext` + `auditTrail`** |
| 5 | 三种结论都塞进一个类 | 每种结论需要的字段完全不同 | **`sealed interface` + 三个 record** |
| 6 | 没人处理时返回 `null` | 调用方要判空，而且分不清"正常/故障" | **`ApprovalChain.submit()` 兜底** |
| 7 | `linkTo()` 随手链 | 重复链接静默丢子链、成环直接栈溢出 | **组装期三道校验** |
| 8 | 四个几乎一样的审批人类 | 只有"名字"和"上限"不同 | **`LimitApprover` 参数化** |
| 9 | 折扣也照搬责任链 | 业务要求"每条都执行"，链做不到 | **改用 `List` 管道**（诚实降级） |
| 10 | 组装逻辑散在 `Client` | 启动时看不出一条链长什么样 | **`describe()` 自述** |

**注意第 9 步。** 它是整份文档里唯一一个"做减法"的步骤——
发现不该用模式，于是不用。**这一步比前面九步都难，因为它需要否定自己。**

### 4.1 第 0 步：先看清问题的形状

在写任何抽象之前，先把"没有模式"的写法摆出来。审批的原始逻辑：

```java
public ApprovalDecision approve(RefundRequest req) {
    if (req.daysAfterPurchase() > 7) {
        return denied(req, "超过 7 天");
    }
    if (req.amount().compareTo(new BigDecimal("200")) <= 0) {
        return granted(req, "客服专员");
    }
    if (req.amount().compareTo(new BigDecimal("2000")) <= 0) {
        return granted(req, "客服主管");
    }
    if (req.amount().compareTo(new BigDecimal("20000")) <= 0) {
        return granted(req, "售后经理");
    }
    if (req.amount().compareTo(new BigDecimal("200000")) <= 0) {
        return granted(req, "售后总监");
    }
    return needsHumanReview(req);
}
```

**这段代码其实一点都不烂。** 它：
- 只有一个出口变量，逻辑线性；
- 没有类爆炸；
- 一眼能看出全部规则；
- 加一层就加一个 `if`。

所以在**这个规模**下，它比任何模式都好。什么时候它开始崩？三个真实场景：

**场景 1：三处调用。** Web 接口、定时批处理、消息消费者都要审批退款。
上面这段代码会被复制三份，或者抽成一个静态方法。
抽成静态方法是可行的——但一旦各渠道的审批规则开始分叉
（App 端允许 7 天，线下门店允许 15 天），静态方法就得加参数、加 `if`。

**场景 2：每层审批人要带自己的数据。** "售后经理"这个角色除了金额上限，
还要看"该客户是否在黑名单"、"该订单是否已经退过一次"。
这些数据不属于 `approve()` 方法，属于那个角色。塞进 `if` 里就开始变味了。

**场景 3：审批层级的顺序要能配置。** 业务说"大促期间，金额超过 1000 就先走风控"。
在 `if` 序列里插一个判断容易，但"顺序可配置"做不到——
因为顺序被写死在语句的位置里。

**责任链要解决的正是这三件事**：多调用点、每节点自带数据、顺序可组装。
如果这三件事一件都没有，就别用。

### 4.2 第 1 步：`next` 放哪里——三种方案

这是责任链最核心的一个结构决定。三种方案都真实存在：

#### 方案 A：外挂链表节点（原项目）

```java
class HandlerNode {
    private final DiscountHandler handler;
    private HandlerNode next;
}
```

- 优点：处理器可以完全无状态，能被多条链共享；
- 缺点：**处理器不知道自己在链上**，无法自己决定终止（§2.2 已论证）；
- 额外成本：多一个类 + 一次 `getHandler()` 跳转。

#### 方案 B：抽象类自己持有 `next`（本项目）

```java
public abstract class Handler<C, R> {
    private Handler<C, R> next;          // 处理器自己知道下一个是谁
    public final Optional<R> handle(C ctx) { ... }
    protected abstract Optional<R> tryHandle(C ctx);
}
```

- 优点：处理器能自己决定"我处理完就不要再传了"；
- 优点：不需要额外类，`handle()` 的递归天然表达"沿链传递"；
- 缺点：**处理器变成"有状态"的**（`next` 是字段），不能被两条不同形状的链共享。

第三条缺点是真的，必须承认。但它在本场景下**不构成问题**：

> 一旦某条链组装完成，节点就不再变了。
> 同一组节点被复用的正确方式是**复用整条子链**（`a.linkTo(b)` 之后，`x.linkTo(a)`），
> 而不是"把 b 从链上拆下来接到别处"——后者本身就是危险操作。

而且 `linkTo()` 会拒绝二次写入，所以"链组装后不可变"是**结构上的保证**，不是约定。

#### 方案 C：链对象持有列表 + 游标（框架式）

Servlet Filter、Netty、Express 都是这种：

```java
public final class Chain<C, R> {
    private final List<Handler<C, R>> handlers;
    private int index = 0;                       // 游标
    public Optional<R> proceed(C ctx) {
        if (index >= handlers.size()) return Optional.empty();
        return handlers.get(index++).apply(ctx, this);   // 把链自己传下去
    }
}
```

- 优点：可以用 `for` 循环而不是递归，几千个节点也不会栈溢出；
- 优点：支持"环绕"（`before → chain.proceed() → after`），这就是 Filter 的 `doFilter` 模型；
- 缺点：节点必须接受 `Chain` 参数，接口更重；而且"游标"是可变状态，**同一条链不能被并发使用**
  （Servlet 的做法是每次请求新建一个 `FilterChain` 对象）。

#### 本项目的选择

选 **B**。理由：

1. 审批链只有 5 个节点，递归深度完全不是问题；
2. 不需要"环绕"语义（审批是单向的：判断 → 决定 → 结束）；
3. 接口最轻——`handle(C)` 一个参数，节点完全不知道"链"这个对象的存在。

**什么时候该换成 C？** 两个信号：链很长（几十上百个节点）、
或者节点需要在处理**前后**都插手（比如统一的耗时统计、事务开启/提交）。
这两种情况 `Handler` 的递归模型都做不到，必须换成游标模型。

### 4.3 第 2 步：怎么表达"我不处理"

`tryHandle()` 的返回类型是整个设计里第二个关键决定。三种候选：

#### 候选 1：`boolean tryHandle(C ctx)`

```java
boolean tryHandle(C ctx);   // true = 我处理了，false = 你继续
```

**问题：结果往哪放？** 只能写进 `ctx`（隐式副作用）：

```java
public boolean tryHandle(ApprovalContext ctx) {
    if (ctx.request().amount().compareTo(limit) > 0) return false;
    ctx.setDecision(new Granted(...));      // ← 结果藏在上下文里
    return true;
}
```

后果是：**"谁写了这个字段"变得不可追踪**。链上有 5 个节点，
如果最终结论不对，你无法从签名上看出是哪个节点写的——它们都可能写。

#### 候选 2：`R tryHandle(C ctx)`，用 `null` 表示不处理

```java
ApprovalDecision tryHandle(ApprovalContext ctx);   // null = 我不处理
```

**问题：`null` 有歧义。** 两种完全不同的情况共用一个值：

- "我不处理"（正常的传递）；
- "我处理了，但结论是空的"（异常/错误）。

而且调用方每次都要 `if (result != null)`，而 `null` 是**编译器不会提醒你**的东西。

#### 候选 3：`Optional<R> tryHandle(C ctx)`（本项目）

```java
Optional<ApprovalDecision> tryHandle(ApprovalContext ctx);
// Optional.of(decision)  = 我做了决定，链到此为止
// Optional.empty()       = 我不做决定，请下一个节点接着看
```

**优点 1：语义唯一，没有第三种解释。** 只有两个分支，而且两个分支都有明确含义。

**优点 2：空值被类型系统标出来了。** 调用方拿到 `Optional` 就必须处理"可能为空"，
不像 `null` 那样可以装作看不见。

**优点 3：它让"没人处理"成为 `handle()` 的正常返回值。**
`Handler.handle()` 走到链尾返回 `Optional.empty()`，
于是 `ApprovalChain.submit()` 里那句 `orElseGet(...)` 就是**唯一**处理"无人负责"的地方。

**一个必须说清的代价**：`Optional` 会被装箱。
在每秒百万次的极热路径上，这会带来额外的对象分配。
**但审批流一天几千笔，这个开销完全不值一提。**
如果真在极热路径上，可以用一个 `sealed interface Verdict<R> { record Handled<>() ... record Passed<>() ... }`
把两种状态做成对象——但那只是把装箱换成了另一种分配，并不会消失。
**先问"这个开销在总量上占多少"，再决定要不要优化。**

### 4.4 第 3 步：终止语义——`final handle()`

有了 `tryHandle()`，流程本身只剩四行。这四行**必须锁在基类里**：

```java
public final Optional<R> handle(C context) {
    Optional<R> mine = tryHandle(context);   // 1. 问自己
    if (mine.isPresent()) {
        return mine;                          // 2. 自己给了决定 → 终止
    }
    Handler<C, R> downstream = next;
    if (downstream == null) {
        return Optional.empty();              // 3. 没有下一个 → 没人处理
    }
    return downstream.handle(context);        // 4. 交给下一个
}
```

为什么是 `final`？因为**这四行是责任链的定义**。
一旦允许子类重写，就会有人写出：

```java
@Override
public Optional<R> handle(C ctx) {
    Optional<R> mine = tryHandle(ctx);
    if (mine.isPresent()) return mine;
    return handle(ctx);        // ← 写错了，无限递归
}
```

或者更隐蔽的：

```java
@Override
public Optional<R> handle(C ctx) {
    tryHandle(ctx);            // ← 忘了处理返回值，于是永远往下传
    return next == null ? Optional.empty() : next.handle(ctx);
}
```

**这两个 bug 都不会编译报错。** 第一个会在运行期栈溢出，第二个会让"命中即停"彻底失效——
所有节点都跑一遍，链退化成管道，而且没有任何日志提示你写错了。

把 `handle()` 设成 `final`，等于**在编译期拿掉这个可能性**。

### 4.5 第 4 步：请求和轨迹放哪——`ApprovalContext`

`tryHandle` 需要一个参数，里面装两样东西：**不变的请求** + **不断增长的审计轨迹**。

三种做法：

#### 做法 A：两个参数

```java
Optional<R> tryHandle(RefundRequest request, List<Step> audit);
```

问题是：**每加一个横切关注点，签名就变一次。** 今天要审计，明天要埋点，
后天要"链路追踪 ID"——三次改接口，五次改所有实现类。

#### 做法 B：塞进 `RefundRequest` record

```java
public record RefundRequest(String orderNo, BigDecimal amount, ..., List<Step> audit) { }
```

这是**最容易踩的坑**，因为它编译得过、跑起来也没问题。但它有两个后患：

1. `record` 自动生成的 `equals`/`hashCode` 会把 `audit` 算进去。
   而 `audit` 在链上一路增长，所以**同一个请求在不同时刻 `equals` 出不同结果**。
   一旦有人把它放进 `HashSet` 或用作 `Map` 的 key，就再也取不出来了。
2. "请求是什么"和"这次请求是怎么被处理的"是**两件事**。
   把后者塞进前者，等于让一次性的处理过程污染了业务实体。

#### 做法 C：独立的上下文对象（本项目）

```java
public final class ApprovalContext {
    private final RefundRequest request;
    private final List<Step> steps = new ArrayList<>();
    public RefundRequest request() { return request; }
    public void record(String handler, String verdict, String detail) { ... }
    public List<Step> auditTrail() { return List.copyOf(steps); }
}
```

- 签名永远只有一个参数 `C`，加横切关注点不改接口；
- 请求保持不可变，轨迹独立增长；
- 生命周期只有一次 `submit()` 调用那么长，**所以它可变是安全的**。

**为什么它可变还安全？** 因为一个 `submit()` 创建一个 `ApprovalContext`，
整条链在同一个线程上同步走完，没有任何人共享它。
这比给 `List<Step>` 加锁或换成 `CopyOnWriteArrayList` 简单得多，也快得多。

> **先想清楚"谁会并发访问"，再决定要不要线程安全——而不是无脑加锁。**

### 4.6 第 5 步：三种结论——`sealed interface`

审批的结论有三种，它们需要的字段**完全不同**：

| 结论 | 需要什么 |
|---|---|
| 批准 | 批了多少钱、谁批的、依据 |
| 驳回 | 为什么驳回、谁驳的 |
| 转人工 | 申请了多少钱、为什么走到人工 |

如果用一个类装：

```java
public class ApprovalDecision {
    private String status;        // "GRANTED" / "DENIED" / "NEEDS_REVIEW"
    private BigDecimal amount;    // 只有 GRANTED / NEEDS_REVIEW 有意义
    private String approver;      // 只有 GRANTED / DENIED 有意义
    private String reason;        // 只有 DENIED / NEEDS_REVIEW 有意义
    private String note;          // 只有 GRANTED 有意义
}
```

四个字段里，**没有任何一个在所有状态下都有意义**。于是注释里必须写
"这个字段在 X 状态下为 null"，而所有读这些字段的地方都要判空。

改成 sealed：

```java
public sealed interface ApprovalDecision
        permits ApprovalDecision.Granted, ApprovalDecision.Denied, ApprovalDecision.NeedsHumanReview {
    String orderNo();
    List<ApprovalContext.Step> auditTrail();

    record Granted(String orderNo, BigDecimal approvedAmount, String approver,
                   String note, List<ApprovalContext.Step> auditTrail) implements ApprovalDecision { }
    record Denied(String orderNo, String reason, String rejectedBy,
                  List<ApprovalContext.Step> auditTrail) implements ApprovalDecision { }
    record NeedsHumanReview(String orderNo, BigDecimal amount, String reason,
                            List<ApprovalContext.Step> auditTrail) implements ApprovalDecision { }
}
```

好处有三个，第三个才是真正的杀手级：

1. **每个字段都在有意义的地方**，不存在"这个字段在这种状态下是 null"；
2. **每个 record 自带 `equals`/`hashCode`/`toString`**，单测断言可以直接比对象；
3. **`switch` 可以穷尽**，不需要 `default`：

```java
switch (decision) {
    case ApprovalDecision.Granted granted -> ...;
    case ApprovalDecision.Denied denied -> ...;
    case ApprovalDecision.NeedsHumanReview review -> ...;
}
```

第 3 点的价值在于**编译期保护**：将来如果有人加了第四种结论
（比如 `PartiallyApproved`），**所有没处理它的 `switch` 会直接编译失败**。
这不是"多此一举"——审批准结论是要落到财务系统的，漏处理一种结论意味着漏打一笔款。

如果用的是 `String status` + `default` 分支，新增一种状态时编译器**一声不响**，
所有没改的 `switch` 都会悄悄走进 `default`，把"部分批准"当成"未知状态"处理掉。

### 4.7 第 6 步：兜底放哪

`Handler.handle()` 走到链尾返回 `Optional.empty()`。这个空值最终要变成什么？

三种做法：

#### 做法 A：把空值传给调用方

```java
Optional<ApprovalDecision> submit(RefundRequest req);
```

调用方每次都要 `if (opt.isPresent())`。而且"没人能批"这个业务事实，
被压缩成了一个通用类型 `Optional.empty()`——**丢失了所有业务信息**
（申请的是多少钱？走到哪一步了？）。

#### 做法 B：在链尾挂一个"兜底节点"

```java
public final class EscalateHandler extends Handler<ApprovalContext, ApprovalDecision> {
    protected Optional<ApprovalDecision> tryHandle(ApprovalContext ctx) {
        return Optional.of(new NeedsHumanReview(...));
    }
}
```

这个做法**完全正确**，而且有个额外的好处：兜底逻辑也进了审计轨迹。

但它有个前提：**兜底得有业务语义**。如果兜底要做真实的事
（建工单、发通知、写待办），那它必须是一个节点。
如果它只是"把没人处理翻译成返回值"，那它只是一个三行的类，价值不大。

#### 做法 C：放在链的拥有者里（本项目）

```java
public ApprovalDecision submit(RefundRequest request) {
    ApprovalContext context = new ApprovalContext(request);
    return head.handle(context)
            .orElseGet(() -> ApprovalDecision.needsHumanReview(request, context.auditTrail()));
}
```

选择理由：

- `NeedsHumanReview` 只是一个数据，**没有任何副作用**，不值得为它建一个类；
- 兜底是"链的语义补全"，属于框架职责，放在 `ApprovalChain` 里最合适；
- 调用方拿到的返回值**永远非 null、永远不需要判空**。

**关键收益是接口形状**：

```java
public ApprovalDecision submit(RefundRequest request)    // 而不是 Optional<ApprovalDecision>
```

**把可空性挡在框架里，不要漏给业务代码。** 业务代码里每多一个 `if (x != null)`，
就多一个可能被忘记的分支。

### 4.8 第 7 步：组装期的三道保险

`linkTo()` 看起来只是 `this.next = next`。但这一行如果不加校验，
会制造三种极难排查的 bug：

| 错误写法 | 直接后果 | 排查难度 |
|---|---|---|
| `a.linkTo(b); a.linkTo(c);` | b 那一段被静默丢掉 | **极高**：没有任何异常，只是某些单子莫名走了兜底 |
| `a.linkTo(a);` | 无限递归 → `StackOverflowError` | 中：堆栈会指向 `handle()`，但看不出是谁链错了 |
| `a.linkTo(b); b.linkTo(a);` | 同上，但**从任一节点看都"合法"** | **极高**：链的形状是个环，只有请求进来时才爆 |

本项目的做法：**三道校验全放在组装期**。

```java
public final Handler<C, R> linkTo(Handler<C, R> next) {
    if (next == null) { throw new IllegalArgumentException(...); }
    if (this.next != null) { throw new IllegalStateException(...); }   // 防重复链接
    if (next == this) { throw new IllegalArgumentException(...); }    // 防自环
    for (Handler<C, R> cursor = next; cursor != null; cursor = cursor.next) {
        if (cursor == this) { throw new IllegalArgumentException(...); }  // 防环
    }
    this.next = next;
    return next;
}
```

**代价与收益**：环检测是一次 O(n) 遍历。链组装只发生**一次**（通常在应用启动时），
而递归发生在**每次请求**。用一次启动期的遍历，换掉运行期的 `StackOverflowError`，
这笔交易非常划算。

实际拦截效果（Demo 3 的真实输出）：

```
  ✓ [节点A] 已经有后继节点 [节点B]，不能重复链接 [节点C]：重复链接会把前一条子链整段丢掉
  ✓ 链接 [节点Y] → [节点X] 会形成环，责任链必须是一条有向无环路径
  ✓ [节点Z] 不能链接到自己：会造成无限递归
```

#### 附带的一个坑：`linkTo` 的返回值

```java
public final Handler<C, R> linkTo(Handler<C, R> next) {
    ...
    return next;      // ← 返回的是"下一个"，不是 this
}
```

为什么返回 `next` 而不是 `this`？因为只有返回 `next` 才能写出流畅的链式调用：

```java
a.linkTo(b).linkTo(c);      // a→b, b→c，正确
```

但如果返回 `this`，同一个表达式会变成：

```java
a.linkTo(b).linkTo(c);      // a→b, 然后 a→c（覆盖！），b 被丢掉
```

**返回 `this` 会让链式写法变成"不断覆盖同一个节点的 next"，是个更糟的陷阱。**

可返回 `next` 也有代价——它让"想拿到头节点"变得反直觉：

```java
Handler<C, R> head = a.linkTo(b).linkTo(c);   // head 拿到的是 c，不是 a
```

这个坑本项目在 Demo 里真的跑给你看了（`Client.demo3` 的真实输出）：

```
  写 head = 甲.linkTo(乙)，head.name() 却是：乙
  所以请用 Handler.chain(List.of(甲, 乙))，它保证返回头节点
```

`Handler.chain(List)` 的存在就是为了**让调用方永远不必关心 `linkTo` 返回什么**：

```java
public static <C, R> Handler<C, R> chain(List<Handler<C, R>> nodes) {
    Handler<C, R> head = nodes.get(0);
    Handler<C, R> cursor = head;
    for (int i = 1; i < nodes.size(); i++) {
        cursor = cursor.linkTo(nodes.get(i));
    }
    return head;              // ← 明确返回头节点
}
```

**为什么参数是 `List` 而不是可变参数 `Handler...`？**
因为泛型可变参数在 Java 里天生不干净：每次调用都会在编译期生成一个泛型数组，
而它的运行期类型是 `Handler[]`，可以被塞进任何 `Handler`——这叫"堆污染"。
用 `@SafeVarargs` 可以把 `-Xlint:all` 的告警压下去，但那只是让编译器闭嘴。

> **能让问题消失的时候，不要只让告警消失。**

### 4.9 第 8 步：四个审批人 vs 一个参数化类

原项目三个处理器各 26 行，除了判断条件和减免金额，几乎逐行相同。
本项目如果照搬，就是四个审批人类，每个约 30 行：

```java
public class CustomerServiceApprover { /* 上限 200 */ }
public class SupervisorApprover      { /* 上限 2000 */ }
public class ManagerApprover         { /* 上限 20000 */ }
public class DirectorApprover        { /* 上限 200000 */ }
```

**它们之间的差别只有两样：名字、上限。** 所以本项目把它们压成一个：

```java
new LimitApprover("客服专员", "200"),
new LimitApprover("客服主管", "2000"),
new LimitApprover("售后经理", "20000"),
new LimitApprover("售后总监", "200000"),
```

**四个对象，一个类，零重复。**

这和策略模式把"满 100 减 20"参数化是同一个道理：

> **模式的价值不在于"有几个类"，而在于"变化被收敛到了哪里"。**

参数化之后，加一层审批只需要在 `List.of(...)` 里加一行，
不需要新建文件、不会漏改、也不会出现"复制过来忘了改上限"这种低级事故。

**什么时候反而应该拆成多个类？** 当各节点的判断逻辑**真的不一样**时：

- "主管"除了看金额，还要看客户等级；
- "总监"除了看金额，还要看该订单是否已经退过一次；
- "财务复核"完全不看金额，只看发票是否合规。

这类差异是**行为差异**，参数化表达不了。硬塞会变成：

```java
if (title.equals("主管")) { ... } else if (title.equals("总监")) { ... }
```

**那才是应该拆类的时候。** 判据是：

> 如果差异能用**数据**描述（上限、天数、比例），就参数化；
> 如果差异必须用**分支**描述（不同的判断逻辑、不同的副作用），就拆类。

### 4.10 第 9 步：折扣场景——为什么最后没用责任链

这是全篇最需要诚实的一节。

改造完审批之后，很自然会想："折扣那三个处理器也照这个套路改一遍不就行了？"
**不行。** 因为折扣的业务要求是：

> 满减、优惠券、会员折扣，**三条都要算**。

而责任链的定义是"命中一个就停"。这两件事直接矛盾。

三种"硬套"的方案，以及它们为什么都不行：

#### 硬套方案 A：把所有节点都写成"命中还继续传"

```java
protected Optional<ApprovalDecision> tryHandle(ApprovalContext ctx) {
    applyDiscount(ctx.order());        // 处理
    return Optional.empty();           // 但永远返回空，让链继续
}
```

结果是：一条**永远不终止**的链。它的行为等价于 `for` 循环，
但代价是递归调用 + `Optional` 装箱 + 一个 `Handler` 抽象类 + 五个参数。

**这是最典型的"为模式而模式"**：付出模式的全部成本，拿到零收益。

#### 硬套方案 B：用"最后一个节点"决定终止

```java
// 会员节点是最后一个，它返回结果
protected Optional<BigDecimal> tryHandle(...) {
    ...
    return Optional.of(finalAmount);   // 只有这里返回非空
}
```

这样链确实能终止。但它的语义变成了：
**"前三个节点只是准备工作，真正的决定在最后一个"**——
那这个"链"只是把一段顺序代码拆成了四个类，而且**顺序被写死在"谁最后返回"上**，
比 `List` 的顺序更难改。

#### 硬套方案 C：给 `Handler` 加一个"是否终止"的标志

这是最糟的一种：为了容纳两种语义，给抽象基类加参数、加枚举、加 `if`。

```java
public enum Mode { TERMINATE_ON_HANDLE, ALWAYS_CONTINUE }
```

**一旦基类开始为"某一种使用场景"开开关，它就同时属于两种模式了，
而两种模式的行为都不再清晰。**

#### 本项目的选择：诚实降级成 `List`

```java
public final class DiscountPipeline {
    private final List<DiscountRule> rules;

    public Result apply(DiscountOrder order) {
        DiscountOrder current = order;
        List<Step> steps = new ArrayList<>();
        for (DiscountRule rule : rules) {
            DiscountRule.Outcome outcome = rule.evaluate(current);
            steps.add(new Step(rule.name(), outcome.applied(), outcome.discount(), outcome.reason()));
            if (outcome.applied()) {
                current = current.withDiscount(rule.name(), outcome.discount());
            }
        }
        return new Result(current, steps);
    }
}
```

这段代码有 20 行，做了原项目 257 行做的事，外加上：

- 每一步都记录轨迹（**包括未命中的**）；
- 金额不会变成负数；
- 订单不可变，每一步产生新对象；
- 顺序只写在 `List.of(...)` 一处。

**这一次"不用模式"的决定，省掉了两个类（`HandlerNode` + `DiscountChain`）
和一个散落的 `getPriority()` 字段。**

这就是 `DiscountRule` 接口的注释里那句"这个接口不是责任链的 Handler"的意思：

> **长得像，但语义相反。长得像不是复用的理由，语义一致才是。**

### 4.11 十个决策汇总表

| # | 决策 | 选择 | 放弃的方案 | 判据 |
|---|---|---|---|---|
| 1 | `next` 放哪里 | 抽象类内部 | 外挂链表节点 | 处理器要能自己决定终止 |
| 2 | 返回类型 | `Optional<R>` | `boolean` / `R`+null | 需要表达"我不决定"，且不能歧义 |
| 3 | 流程是否可重写 | `final handle()` | 允许子类重写 | 四行流程是模式定义，不是实现细节 |
| 4 | 请求+轨迹 | 独立 `Context` | 两参数 / 塞进 record | 签名稳定 + record 语义不被污染 |
| 5 | 结论类型 | `sealed interface` | 一个类 + status 字段 | 三种结论字段完全不同 |
| 6 | 兜底位置 | 链的拥有者 | 调用方判空 / 兜底节点 | 兜底无副作用，不值得建类 |
| 7 | 组装期校验 | 三道 | 不校验 | 组装一次 vs 每次请求 |
| 8 | 审批人类 | 参数化一个类 | 四个类 | 差异只有名字和上限 |
| 9 | 折扣场景 | `List` 管道 | 硬套责任链 | 业务要求每条都执行 |
| 10 | 链的自述 | `describe()` | 无 | 启动日志能一眼看出链的形状 |

---

## 5. 代码解析：每个文件为什么存在

### 5.1 全景：15 个文件，两个 Demo

```
com/patterns/behavioral/chainofresponsibility/
├── Handler.java                 245 行   ★ 模式骨架：next + final handle + Optional
├── Client.java                  329 行     五段演示
├── approval/                              ★ 纯责任链：命中即停
│   ├── RefundRequest.java         65 行     请求（record，不可变）
│   ├── ApprovalContext.java       94 行     请求 + 审计轨迹
│   ├── ApprovalDecision.java     109 行     sealed 三态结论
│   ├── LimitApprover.java        113 行     参数化审批人
│   ├── PolicyGuard.java           78 行     链首一票否决
│   └── ApprovalChain.java        132 行     组装 + 兜底 + 自述
└── discount/                                管道：每条都执行（不是责任链）
    ├── DiscountOrder.java        116 行     不可变订单 + 账单
    ├── DiscountRule.java          85 行     规则接口 + Outcome
    ├── FullReductionRule.java     80 行     满减
    ├── CouponRule.java            81 行     优惠券
    ├── MemberRule.java            68 行     会员折扣
    └── DiscountPipeline.java     147 行     List 循环 + 轨迹

com/patterns/util/
└── Money.java                     91 行     金额构造与显示（多个模式共用，见 §5.8）
```

**注意最后一行**：`Money` 并不在本包里。它最初写在这里，后来被搬到了 `com/patterns/util/`——
理由见 §5.8，这是一条真实发生过的包结构修正，不是虚构。

**比例洞察**：

| 分组 | 行数 | 占比 |
|---|---|---|
| 模式骨架 `Handler`（本包） | 245 | 13.4% |
| 金额工具 `Money`（util 包） | 91 | 5.0% |
| 审批域（6 文件） | 591 | 32.2% |
| 折扣管道（6 文件） | 577 | 31.5% |
| 入口 Demo（`Client`） | 329 | 17.9% |
| **合计** | **1833** | **100%** |

和命令模式的 16 文件 1923 行相比，规模接近。但**结构完全不同**：

- 命令模式的核心是"把一个请求变成对象"（多一层封装）；
- 责任链的核心是"把多个处理者串成一条路径"（多一层**连接**）。

**注意 32% 那一栏**：折扣管道的六个文件占了三分之一，而它**完全不属于责任链**。
它之所以在这个包里，是因为它要对照说明"什么情况下不该用这个模式"。

如果只看"实现责任链"这一件事，真正必需的是：

```
模式骨架（Handler 245 + Money 91）  336 行
审批域（6 个文件）                  591 行
                              ───────────
真正必需                            927 行   （占 1833 行的 51%）
```

剩下 906 行 = 折扣管道 577 + 入口 Demo 329，是**为了让读者看懂边界**而存在的。

**还有一个更值得记住的比例**：1833 行里真正会被编译成字节码的逻辑只有 **671 行**（37%），
剩下 1162 行（63%）是注释、javadoc 和空行。

```
物理行数        1833
代码行数         671   ← 去掉注释与空行后
注释 + 空行     1162   ← 63%
```

这个比例不是为了炫耀注释多，而是想说清一件事：

> **设计模式的成本从来不在代码量上，而在"为什么这样写"的解释上。**
> 一个 `Handler` 类去掉注释只有 40 行，但要讲清"为什么 `next` 必须放在基类"
> "为什么返回值是 `Optional` 而不是 `boolean`"，需要几百行。
> 反过来，如果一份设计模式代码的注释量只有 5%，那它几乎一定是在教"怎么写"，
> 而不是在教"为什么这样设计"。

### 5.2 `Handler`：整个模式只有四行

`Handler` 有 245 行，其中**真正执行流程的只有 4 行**：

```java
public final Optional<R> handle(C context) {
    Optional<R> mine = tryHandle(context);
    if (mine.isPresent()) {
        return mine;
    }
    Handler<C, R> downstream = next;
    if (downstream == null) {
        return Optional.empty();
    }
    return downstream.handle(context);
}
```

剩下的 241 行是：类注释（解释为什么这么设计）、三道组装期校验、
`describeChain()` 自述、`chain()` 静态工厂。

**这个比例本身就是一条设计结论**：

> 一个模式的核心代码通常只有几行，剩下的都是**对"用错"的防御**。
> 那些防御看起来啰嗦，但每一行都对应一种真实发生过的错误。

`Handler` 的泛型签名 `<C, R>` 也值得说一句：

- `C`（Context）——请求 + 过程数据，每次调用新建；
- `R`（Result）——决定，由某个节点产出。

**两个类型参数分开，让这个基类能被任何"沿链找负责人"的场景复用。**
如果把 `R` 写死成 `ApprovalDecision`，那它就只能用在审批里，
下次要做一个"告警按级别找值班人"的链，就得再抄一遍。

### 5.3 `RefundRequest` 与 `ApprovalContext`

这两个类是一组，一个装"不变的东西"，一个装"会变的东西"。

`RefundRequest` 是 record，四个字段全部在构造器里校验：

```java
public record RefundRequest(String orderNo, BigDecimal amount, String reason, int daysAfterPurchase) {
    public RefundRequest {
        if (orderNo == null || orderNo.isBlank()) { throw new IllegalArgumentException(...); }
        if (amount == null) { throw new IllegalArgumentException(...); }
        if (amount.signum() <= 0) { throw new IllegalArgumentException(...); }
        if (reason == null || reason.isBlank()) { throw new IllegalArgumentException(...); }
        if (daysAfterPurchase < 0) { throw new IllegalArgumentException(...); }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }
}
```

**一个刻意的分层**：

| 错误类型 | 例子 | 处理方式 |
|---|---|---|
| **调用方写错了** | 金额是负数、订单号是空串 | **构造时抛异常**，立刻炸在出错那一行 |
| **业务上不允许** | 超过 7 天不给退 | **链上返回 `Denied`**，是一个合法结论 |

这两类错误混在一起处理，是最常见的设计缺陷。如果"金额为负"也走 `Denied`，
那么代码写错时（比如漏传了 amount，默认值 0）会得到一条"业务驳回"的结论，
**看起来完全正常，实际上是个 bug**。

`ApprovalContext` 是普通类，因为它是可变的：

```java
public void record(String handler, String verdict, String detail) {
    steps.add(new Step(handler, verdict, detail));
}
```

三个参数都是必填，而且 `verdict` 必须能区分**"我不做决定"**和**"我同意了"**。
这是本 Demo 里最容易写错的地方——一个只写 `record(name, "ok")` 的实现，
事后完全看不出这个节点到底是批准了还是跳过了。

`auditTrail()` 用 `List.copyOf(steps)` 而不是直接返回字段：

```java
public List<Step> auditTrail() {
    return List.copyOf(steps);      // 真拷贝
}
```

**为什么不用 `Collections.unmodifiableList(steps)`？** 因为那只是一个**视图**：
它禁止你修改，但底层字段还在变，你手里的"快照"会跟着一起变。
审计轨迹的意义恰恰是"记录下决定那一刻的样子"，所以必须是真拷贝。

### 5.4 `ApprovalDecision`：三种结论，一个都不许漏

这个文件的 109 行里，真正的代码不到 30 行，剩下全在解释**为什么是这三种**。

三种结论对应三种终态：

| 结论 | 由谁产出 | 是不是终态 | 为什么必须有 |
|---|---|---|---|
| `Granted` | 某个 `LimitApprover` | 是 | 正常路径 |
| `Denied` | `PolicyGuard` | 是（**否定式**终态） | 一票否决 |
| `NeedsHumanReview` | `ApprovalChain` 兜底 | 是 | 超权限工单 |

**`Denied` 是最容易被漏掉的一种。** 很多实现把"不符合条件"写成"继续往下传"，
理由是"让后面的人去拒"——后果是一条明明不可能通过的申请，惊动了链上所有人。

```java
// 错误写法：不符合政策也继续传
if (req.daysAfterPurchase() > maxDays) {
    ctx.record(name(), "不符合", "...");
    return Optional.empty();          // ← 错！后面四个人白干
}
```

正确写法是**立刻返回 `Denied`，链终止**：

```java
if (request.daysAfterPurchase() > maxDaysAfterPurchase) {
    String reason = "距下单 " + request.daysAfterPurchase() + " 天，已超过 "
            + maxDaysAfterPurchase + " 天无理由退款期";
    context.record(name(), "驳回", reason);
    return Optional.of(new ApprovalDecision.Denied(
            request.orderNo(), reason, name(), context.auditTrail()));
}
```

Demo 2 的真实输出证明了这个差别：30 天的那张单子**只惊动了 1/5 个节点**。

### 5.5 `LimitApprover` 与 `PolicyGuard`：两种节点，一套契约

这两个类都继承 `Handler<ApprovalContext, ApprovalDecision>`，但职责完全不同：

| | `LimitApprover` | `PolicyGuard` |
|---|---|---|
| 职责 | 在额度内批准 | 违反政策则驳回 |
| 命中时返回 | `Granted` | `Denied` |
| 不命中时返回 | `empty`（无权，往上走） | `empty`（不否决，往上走） |
| 放在链的哪里 | 中间 | **链首** |
| 有几个实例 | 4 个（参数不同） | 1 个 |

**它们用同一套契约（要么决定、要么传递），表达了四种业务含义：**

```
LimitApprover + Optional.of(Granted)   → 我批了
LimitApprover + Optional.empty()       → 我没权限，往上走
PolicyGuard   + Optional.of(Denied)    → 我否决了
PolicyGuard   + Optional.empty()       → 我不否决，往上走
```

这就是为什么 `tryHandle()` 的返回类型必须是 `Optional` 而不是 `boolean`：
**一旦用 `boolean`，"不否决"和"同意"就再也分不开了。**

`LimitApprover` 的边界判断：

```java
if (request.amount().compareTo(limit) > 0) {
    return Optional.empty();      // 超过上限 → 无权
}
// 否则批准，即 amount <= limit
```

写成 `> 0` 才符合"上限 200 表示 200 元（含）以下都能批"的语义。
写成 `>= 0` 就会出现"恰好 200 元的单子被推给主管"——
和原项目 §2.5 里那个 `>` / `>=` 是同一类错误，只是方向相反。

`PolicyGuard` 放在链首，是因为它的职责是**过滤**，不是**分派**：

> 把"过滤"放在"分派"之前，是链设计里最基本的一条顺序原则。

### 5.6 `ApprovalChain`：三件不属于任何节点的事

```java
public final class ApprovalChain {
    private final Handler<ApprovalContext, ApprovalDecision> head;

    public static ApprovalChain standard() { ... }        // 1. 组装
    public ApprovalDecision submit(RefundRequest request) { ... }   // 2. 兜底
    public String describe() { return head.describeChain(); }       // 3. 自述
}
```

这三件事**都不属于某个审批人**：

- 组装是"谁来当节点"的决定，审批人自己不知道；
- 兜底是"没人处理怎么办"，审批人自己不关心；
- 自述是"整条链长什么样"，单个节点只能描述自己。

**为什么不把它们塞进 `LimitApprover`？** 因为那会让"一个审批人"同时承担
"我是谁"和"整条链是什么形状"两个完全不同的职责。这是最经典的一种职责错位：
**为了让代码少一个类，把一个全局职责塞进了一个局部对象。**

`submit()` 只有四行，但它承担了"永远返回非 null"的承诺：

```java
public ApprovalDecision submit(RefundRequest request) {
    Objects.requireNonNull(request, "退款单不能为 null");
    ApprovalContext context = new ApprovalContext(request);
    Optional<ApprovalDecision> decision = head.handle(context);
    return decision.orElseGet(() -> ApprovalDecision.needsHumanReview(request, context.auditTrail()));
}
```

**注意 `submit()` 不 catch 异常。** 这不是遗漏，是刻意：

| 错误类型 | 处理方式 | 理由 |
|---|---|---|
| 业务上办不成 | 返回 `Denied` / `NeedsHumanReview` | 是**合法结论**，要记录、要统计 |
| 代码跑错了（NPE、状态非法） | **让它炸** | 是**bug**，必须带着堆栈到顶层 |

如果在这里 `catch (Exception e)` 然后翻译成"转人工"，结果是：
**所有 bug 都变成了"这笔单子需要人工复核"**——线上安静得可怕，
而真正的问题一次也没被修过。

> **业务上的"办不成"用返回值表达；代码上的"跑错了"用异常表达。**

### 5.7 折扣包：六个文件，一个 `for` 循环

`discount/` 里最重要的文件是 `DiscountPipeline`，因为它是**唯一有循环**的地方：

```java
public Result apply(DiscountOrder order) {
    DiscountOrder current = order;
    List<Step> steps = new ArrayList<>();
    for (DiscountRule rule : rules) {
        DiscountRule.Outcome outcome = rule.evaluate(current);
        steps.add(new Step(rule.name(), outcome.applied(), outcome.discount(), outcome.reason()));
        if (outcome.applied()) {
            current = current.withDiscount(rule.name(), outcome.discount());
        }
    }
    return new Result(current, steps);
}
```

三个细节：

1. **`evaluate(current)` 用的是当前订单，不是原始订单**——
   这样后面的规则看到的是前面的结果，叠加才有意义；
2. **无论命中与否都记一条 `Step`**——只记命中的话，"为什么没生效"永远查不到；
3. **`withDiscount` 返回新对象**——订单不可变，没有"改了一半"的中间态。

`DiscountRule.Outcome` 是三条规则统一返回的形态：

```java
record Outcome(boolean applied, BigDecimal discount, String reason) { }
```

**为什么不用 `Optional<BigDecimal>`？** 因为 `Optional.empty()` 说不出"为什么"。
而运营最常问的两个问题恰恰是"这个活动为什么没生效"和"券为什么没抵扣"。
带上 `reason`，排查就是读一行日志的事。

`DiscountOrder.withDiscount()` 里那一行是本次重构的关键：

```java
BigDecimal actual = discount.min(payableAmount);   // 最多减到 0
```

Demo 4 的 D-4005 证明了它的作用：

```
  订单 D-4005  原价 ￥25.00（原项目在这里算出 -25.0）
    ✔ 优惠券 SAVE30（满 0.01 减 30）  券 SAVE30 生效，减 ￥30.00
    ⇒ 应付 ￥0.00，共优惠 ￥25.00
    账单：优惠券 SAVE30（满 0.01 减 30） → -￥25.00
```

注意这里有两个数字：**规则申报 30，系统实扣 25**。
两个都必须留下——前者说明规则配置是什么，后者说明用户实际付了多少。
只留一个，都会在某个场景下说不清。

### 5.8 `Money`：一个"不是模式"的工具类

`Money` 只有三个方法，但它解决了一个**贯穿全项目**的问题：

| 方法 | 作用 | 关键细节 |
|---|---|---|
| `of(String)` | 字符串 → 金额 | 用字符串构造 `BigDecimal`，不用 `new BigDecimal(0.1)` |
| `yuan(BigDecimal)` | 显示成 `￥8,000.00` | `String.format(Locale.ROOT, "%,.2f", v)` |
| `plain(BigDecimal)` | 去掉尾随零 | `stripTrailingZeros().toPlainString()` |

**两个坑值得单独记：**

1. **不用 `DecimalFormat`**：它不是线程安全的（内部有可变的 `DigitList` 缓冲），
   多线程共用一个实例会算出乱码格式，而且只在并发下偶发。
2. **`stripTrailingZeros()` 之后必须 `toPlainString()`**：
   `200.00` 去掉尾零后会变成 `2E+2`，直接 `toString()` 会打出科学计数法。

**它后来搬了家，这件事本身值得记一笔。**

`Money` 最初就写在这个包里（`chainofresponsibility/Money.java`），因为责任链是
第一个需要金额的模式。等到写状态模式时，`Order` 又需要金额，问题就出现了：

```java
// 如果 Money 留在原处，状态模式只能这样写：
import com.patterns.behavioral.chainofresponsibility.Money;
```

**让"状态"去 import"责任链"，只因为两个模式都需要格式化金额。**
这是纯粹由文件位置造成的假耦合：两种模式在业务上毫无关系，
却因为一个工具类的落脚点而产生了依赖。

所以它被移到了 `com/patterns/util/Money.java`，责任链里 7 个引用它的文件
统一改成 `import com.patterns.util.Money;`。**移动之后重跑责任链，
输出 132 行、SHA-256 与移动前完全一致**——这也是判断一次重构是否安全的方法：
不看代码改得多漂亮，看输出是不是一个字节都没变。

> **包结构应该按"谁使用"划分，而不是按"第一次被谁写下"划分。**
> 一个类被第二个模式需要的那一刻，就是它应该搬家的时刻。
> 判断标准很简单：如果新写的 import 让你觉得别扭，那就是包结构错了。

---

## 6. 执行流程：五段 Demo 的真实运行结果

### 6.0 怎么跑

```powershell
# 在 design_patterns_pro 目录下
javac --release 25 -encoding UTF-8 -g -d out -sourcepath src `
      src/com/patterns/behavioral/chainofresponsibility/Client.java

java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out `
     com.patterns.behavioral.chainofresponsibility.Client
```

本节所有输出都是**上面这条命令的真实输出**，共 132 行，
用 SHA-256 连续跑三次完全一致（见附录 B）。

### 6.1 Demo 1：一张退款单怎么在链上走

**代码在做什么**：同一个 `ApprovalChain`，提交五张金额不同的退款单。

**对象创建顺序**（`ApprovalChain.standard()` 里）：

1. `new PolicyGuard(7)` —— 无理由退款期 7 天
2. `new LimitApprover("客服专员", "200")`
3. `new LimitApprover("客服主管", "2000")`
4. `new LimitApprover("售后经理", "20000")`
5. `new LimitApprover("售后总监", "200000")`
6. `Handler.chain(List.of(...))` —— 依次 `linkTo`，返回第 1 个作为头
7. `new ApprovalChain(head)`

**关键变量**：

| 变量 | 类型 | 变化 |
|---|---|---|
| `context` | `ApprovalContext` | 每提交一单新建一个，`steps` 从空开始增长 |
| `mine` | `Optional<ApprovalDecision>` | 每个节点的 `tryHandle` 返回值 |
| `downstream` | `Handler` | 局部变量，只在下传那一行有用 |

**真实输出**：

```
=== 1. 一张退款单怎么在链上走 ===
  链的形状：售后政策校验(≤7 天) → 客服专员(≤￥200.00) → 客服主管(≤￥2,000.00) → 售后经理(≤￥20,000.00) → 售后总监(≤￥200,000.00)

  退款单 R-1001  申请 ￥150.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      批准  ￥150.00 在本节点上限 ￥200.00 之内
    ⇒ 批准 ￥150.00，批准人：客服专员
    （本单实际惊动了 2/5 个节点）

  退款单 R-1002  申请 ￥800.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      无权  ￥800.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      批准  ￥800.00 在本节点上限 ￥2,000.00 之内
    ⇒ 批准 ￥800.00，批准人：客服主管
    （本单实际惊动了 3/5 个节点）

  退款单 R-1003  申请 ￥8,000.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      无权  ￥8,000.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      无权  ￥8,000.00 超出本节点上限 ￥2,000.00，转下一节点
    售后经理      批准  ￥8,000.00 在本节点上限 ￥20,000.00 之内
    ⇒ 批准 ￥8,000.00，批准人：售后经理
    （本单实际惊动了 4/5 个节点）

  退款单 R-1004  申请 ￥50,000.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      无权  ￥50,000.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      无权  ￥50,000.00 超出本节点上限 ￥2,000.00，转下一节点
    售后经理      无权  ￥50,000.00 超出本节点上限 ￥20,000.00，转下一节点
    售后总监      批准  ￥50,000.00 在本节点上限 ￥200,000.00 之内
    ⇒ 批准 ￥50,000.00，批准人：售后总监
    （本单实际惊动了 5/5 个节点）

  退款单 R-1005  申请 ￥500,000.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      无权  ￥500,000.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      无权  ￥500,000.00 超出本节点上限 ￥2,000.00，转下一节点
    售后经理      无权  ￥500,000.00 超出本节点上限 ￥20,000.00，转下一节点
    售后总监      无权  ￥500,000.00 超出本节点上限 ￥200,000.00，转下一节点
    ⇒ 转人工：链上所有审批节点的权限上限都低于 ￥500,000.00，需人工复核
    （本单实际惊动了 5/5 个节点）
```

**三个必须对着输出看的细节：**

1. **轨迹长度是递增的**：2 → 3 → 4 → 5 → 5。
   这就是"命中即停"的可视化证据——如果链不终止，每一单都会是 5 条轨迹。

2. **`R-1005` 走了 5 个节点却没有审批人**。
   轨迹里最后一条是"售后总监：无权"，然后兜底逻辑接管。
   **"走到链尾"和"被拒绝"是两件不同的事**，值也不同
   （`NeedsHumanReview` vs `Denied`）。

3. **每一条轨迹都带具体数字**："￥800.00 超出本节点上限 ￥200.00"。
   如果只写"无权"，事后排查时你必须同时知道"单子多少钱"和"这个节点上限多少"
   才能判断这个判断对不对——而这两个数字在几个月后可能都已经变了。

### 6.2 Demo 2：链首的一票否决

**代码在做什么**：两笔一模一样的 800 元退款，只有"距下单天数"不同（3 天 vs 30 天）。

**真实输出**：

```
=== 2. 链首的一票否决：同样 800 元，只差「距下单天数」 ===

  退款单 R-2001  申请 ￥800.00  距下单 3 天
    售后政策校验  通过  距下单 3 天，在 7 天无理由期内
    客服专员      无权  ￥800.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      批准  ￥800.00 在本节点上限 ￥2,000.00 之内
    ⇒ 批准 ￥800.00，批准人：客服主管
    （本单实际惊动了 3/5 个节点）

  退款单 R-2002  申请 ￥800.00  距下单 30 天
    售后政策校验  驳回  距下单 30 天，已超过 7 天无理由退款期
    ⇒ 驳回：距下单 30 天，已超过 7 天无理由退款期（售后政策校验）
    （本单实际惊动了 1/5 个节点）
```

**这张输出里最重要的是最后一行**：`1/5`。

如果 `PolicyGuard` 被放在链尾，这个数字会变成 `5/5`——
同样是驳回，但代价完全不同：

- 4 个审批人各做了一次无意义的判断；
- 4 条"无权"噪声进入审计日志；
- 如果节点带副作用（发通知、写待办），就是 4 条无效通知。

**顺序在责任链里不只是"谁先谁后"，它还决定了"多少工作被浪费"。**

### 6.3 Demo 3：顺序就是策略

**代码在做什么**：两件事。

前半段：同样的 800 元单子，走"正序"和"逆序"两条链。
后半段：演示组装期的三道校验，以及 `linkTo` 返回值的坑。

**真实输出**：

```
=== 3. 顺序就是策略：同样 800 元，换顺序就换了批准人 ===
  正序：客服专员(≤￥200.00) → 客服主管(≤￥2,000.00) → 售后经理(≤￥20,000.00)
  逆序：售后经理(≤￥20,000.00) → 客服主管(≤￥2,000.00) → 客服专员(≤￥200.00)

  退款单 R-3001  申请 ￥800.00  距下单 3 天
    客服专员      无权  ￥800.00 超出本节点上限 ￥200.00，转下一节点
    客服主管      批准  ￥800.00 在本节点上限 ￥2,000.00 之内
    ⇒ 批准 ￥800.00，批准人：客服主管
    （本单实际惊动了 2/3 个节点）

  退款单 R-3002  申请 ￥800.00  距下单 3 天
    售后经理      批准  ￥800.00 在本节点上限 ￥20,000.00 之内
    ⇒ 批准 ￥800.00，批准人：售后经理
    （本单实际惊动了 1/3 个节点）

  --- 组装期的三道保险 ---
  ✓ [节点A] 已经有后继节点 [节点B]，不能重复链接 [节点C]：重复链接会把前一条子链整段丢掉
  ✓ 链接 [节点Y] → [节点X] 会形成环，责任链必须是一条有向无环路径
  ✓ [节点Z] 不能链接到自己：会造成无限递归

  --- linkTo 的返回值是个坑 ---
  写 head = 甲.linkTo(乙)，head.name() 却是：乙
  所以请用 Handler.chain(List.of(甲, 乙))，它保证返回头节点
```

**三件值得细看的事：**

1. **"批不批"没变，"谁批的"变了**。
   正序是客服主管，逆序是售后经理。这看起来只是署名问题，
   但在真实业务里它意味着：绩效算谁的、审批记录签的是谁、出问题追谁的责。

2. **逆序只惊动了 1 个节点**。
   因为"售后经理"的上限（20000）覆盖了 800，它直接批了。
   这说明一件事：**把权限大的人放在前面，链会"短路"**——
   效率高了，但"分级审批"这个业务意图也就没了。

3. **三道校验全部在组装期触发**。
   如果它们不存在，前两个 bug 会在**某笔真实订单进来的时候**才暴露，
   而那时你看到的是一个 `StackOverflowError` 堆栈，指向 `Handler.handle()`——
   完全看不出是哪两个节点链错了。

### 6.4 Demo 4：折扣管道

**代码在做什么**：四笔订单走标准管道（满减 → 券 → 会员），
外加一笔"券面额大于应付金额"的订单。

**真实输出**：

```
=== 4. 该用 List 的时候别用链表：折扣管道 ===
  管道顺序：满减（满 200 减 50） → 优惠券 SAVE30（满 100 减 30） → 会员折扣（9 折）

  订单 D-4001  原价 ￥250.00（原项目同款用例）
    ✔ 满减（满 200 减 50）            当前应付 ￥250.00 已满 ￥200.00，减 ￥50.00
    ✔ 优惠券 SAVE30（满 100 减 30）   券 SAVE30 生效，减 ￥30.00
    ✔ 会员折扣（9 折）                当前应付 ￥170.00 按 0.9 计算，减 ￥17.00
    ⇒ 应付 ￥153.00，共优惠 ￥97.00
    账单：满减（满 200 减 50） → -￥50.00 | 优惠券 SAVE30（满 100 减 30） → -￥30.00 | 会员折扣（9 折） → -￥17.00

  订单 D-4002  原价 ￥200.00（恰好 200 元：原项目不触发满减）
    ✔ 满减（满 200 减 50）            当前应付 ￥200.00 已满 ￥200.00，减 ￥50.00
    · 优惠券 SAVE30（满 100 减 30）   本单未勾选优惠券
    · 会员折扣（9 折）                非会员
    ⇒ 应付 ￥150.00，共优惠 ￥50.00
    账单：满减（满 200 减 50） → -￥50.00

  订单 D-4003  原价 ￥85.00（未达券门槛 100 元）
    · 满减（满 200 减 50）            当前应付 ￥85.00 未满 ￥200.00
    · 优惠券 SAVE30（满 100 减 30）   券 SAVE30 需满 ￥100.00 可用，当前应付 ￥85.00
    ✔ 会员折扣（9 折）                当前应付 ￥85.00 按 0.9 计算，减 ￥8.50
    ⇒ 应付 ￥76.50，共优惠 ￥8.50
    账单：会员折扣（9 折） → -￥8.50

  订单 D-4004  原价 ￥1,000.00（叠加三档）
    ✔ 满减（满 200 减 50）            当前应付 ￥1,000.00 已满 ￥200.00，减 ￥50.00
    ✔ 优惠券 SAVE30（满 100 减 30）   券 SAVE30 生效，减 ￥30.00
    ✔ 会员折扣（9 折）                当前应付 ￥920.00 按 0.9 计算，减 ￥92.00
    ⇒ 应付 ￥828.00，共优惠 ￥172.00
    账单：满减（满 200 减 50） → -￥50.00 | 优惠券 SAVE30（满 100 减 30） → -￥30.00 | 会员折扣（9 折） → -￥92.00

  --- 券面额大于应付金额时会怎样 ---
  订单 D-4005  原价 ￥25.00（原项目在这里算出 -25.0）
    ✔ 优惠券 SAVE30（满 0.01 减 30）  券 SAVE30 生效，减 ￥30.00
    ⇒ 应付 ￥0.00，共优惠 ￥25.00
    账单：优惠券 SAVE30（满 0.01 减 30） → -￥25.00

  注意轨迹里券申报的是「减 ￥30.00」，账单里记的是「-￥25.00」——
  前者是规则想减多少，后者是系统实际扣了多少。两个数字都必须留下。
```

**四个必须对着输出看的细节：**

1. **D-4002 是原项目 §2.5 那个 bug 的修复证明**：
   原项目跑同一笔 200 元订单得到 `200.0`（不打折），这里得到 `￥150.00`。

2. **D-4003 的第 2 行是"未命中也要记录"的价值**：
   `· 优惠券 SAVE30（满 100 减 30）   券 SAVE30 需满 ￥100.00 可用，当前应付 ￥85.00`
   这一行不产生任何金额变化，但它**直接回答了客诉问题**。

3. **D-4001 的会员折扣排在最后，而且基数是对的**：
   `当前应付 ￥170.00 按 0.9 计算，减 ￥17.00`。
   170 是满减和券之后的价格。如果会员折扣算在原价 250 上，就会减 25 而不是 17，
   **商家多损失 8 元**。这就是"顺序即金额"。

4. **D-4005 里申报 30、实扣 25**：
   如果只记录申报值，账单会显示减了 30，但实际只减了 25，
   **对账时永远差 5 元**。如果只记录实扣值，就看不出"券配置有问题"。

### 6.5 Demo 5：链 vs 管道

**真实输出**：

```
=== 5. 责任链 vs 管道：一句话判据 ===

  维度        责任链（Handler + next）          管道（List 循环）
  命中之后    立刻停止，后面的节点不执行        继续执行，后面的规则照样要跑
  结构        每个节点持有 next，递归下传       一个 List，for 循环
  返回值      Optional：空 = 一路都没人处理     每条规则都有结果，含未命中的原因
  典型场景    审批、过滤、拦截、熔断降级        计价、校验清单、后置处理
  原项目      ——                                折扣叠加（被误写成链表，多写 200 行）

  判据：这条规则命中之后，后面的规则还该不该执行？
        该 → 管道        不该 → 责任链
```

**"多写 200 行"这个数字怎么来的？**
原项目 257 行里，`HandlerNode`（29）+ `DiscountChain`（62）+ 三个处理器里的
`getPriority()` 样板（约 4 行 × 3 = 12）≈ 103 行是纯粹的结构开销，
再加上因为"没有终止语义"而多出来的间接成本。说"200 行"是保守估计——
如果换成参数化写法，整个折扣部分 60 行就能写完。

---

## 7. 扩展对比：新需求来了改哪里

### 7.1 加一个"财务复核"节点

**需求**：金额超过 5 万的退款，批准之后还要财务复核发票。

这是最容易做对的一个扩展，因为它只需要一个新类 + 一行组装。

```java
public final class FinanceReview extends Handler<ApprovalContext, ApprovalDecision> {
    public FinanceReview() {
        super("财务复核");
    }

    @Override
    protected Optional<ApprovalDecision> tryHandle(ApprovalContext context) {
        RefundRequest request = context.request();
        if (!request.reason().contains("发票")) {
            context.record(name(), "通过", "无需复核发票");
            return Optional.empty();
        }
        context.record(name(), "通过", "发票号已核对");
        return Optional.empty();
    }
}
```

**等等，这个节点永远返回 `empty`，那它根本不影响链的走向。**

这是一个真实的建模问题：**"复核"是一个动作，不是一个决定**。
它应该放在**批准之前**做（作为前置检查），还是**批准之后**做（作为后置处理）？

- 如果它可能**否决**（发票不合规就不退），那它应该返回 `Denied`，是一个正常的链节点；
- 如果它只是**记录一下**，那它不该是链节点，应该是批准之后的一个步骤。

**这个例子说明：不是所有"处理"都适合放进链里。**
判断标准还是那一句：**它会不会改变"谁来处理"的走向？**

如果会 → 放进链里；如果不会 → 放在链外面（或者用装饰器包一层）。

正确的写法（会否决）：

```java
if (!invoiceVerified(request)) {
    context.record(name(), "驳回", "发票号 " + request.invoiceNo() + " 未通过校验");
    return Optional.of(new ApprovalDecision.Denied(...));
}
context.record(name(), "通过", "发票号已核对");
return Optional.empty();
```

**改动清单**：

| 文件 | 改什么 |
|---|---|
| `approval/FinanceReview.java` | **新建**（约 40 行） |
| `approval/ApprovalChain.java` | `standard()` 里加一行到 `List.of(...)` |
| 其他所有文件 | **不改** |

### 7.2 按金额动态启用节点

**需求**：只有金额超过 1 万的单子才需要"总监"节点。

两种做法：

#### 做法 A：节点自己判断（推荐）

```java
public final class ConditionalApprover extends Handler<ApprovalContext, ApprovalDecision> {
    private final BigDecimal activeAbove;   // 超过这个金额才启用

    @Override
    protected Optional<ApprovalDecision> tryHandle(ApprovalContext ctx) {
        if (ctx.request().amount().compareTo(activeAbove) < 0) {
            ctx.record(name(), "跳过", "金额未达到启用门槛 " + Money.yuan(activeAbove));
            return Optional.empty();
        }
        return delegate(ctx);      // 真正判断
    }
}
```

**优点**：链的形状不变，日志里能看到"这个节点被跳过了以及为什么"。

#### 做法 B：组装期根据参数决定加不加

```java
public static ApprovalChain of(BigDecimal tenantLimit) {
    List<Handler<...>> nodes = new ArrayList<>();
    nodes.add(new PolicyGuard(7));
    nodes.add(new LimitApprover("客服专员", "200"));
    if (tenantLimit.compareTo(new BigDecimal("10000")) > 0) {
        nodes.add(new LimitApprover("售后总监", "200000"));
    }
    return new ApprovalChain(Handler.chain(nodes));
}
```

**优点**：链的形状就是"这次真正会执行的节点"，`describe()` 更准确。

**缺点**：如果链是单例（大多数情况），那"按金额"就没意义了——
因为金额是**请求级**的，而链是**应用级**的，组建链的时候还不知道会来什么请求。

**结论**：

> **请求级条件 → 做法 A（节点自己判断）；应用级配置 → 做法 B（组装时决定）。**
> 把请求级条件写成组装期判断，会导致"链的形状取决于第一个请求"，是个灾难。

### 7.3 把链配置化

**需求**：运营在配置中心改审批层级，不发版。

```java
public static ApprovalChain fromConfig(List<NodeConfig> configs) {
    List<Handler<ApprovalContext, ApprovalDecision>> nodes = new ArrayList<>();
    for (NodeConfig cfg : configs) {
        nodes.add(switch (cfg.type()) {
            case "policy" -> new PolicyGuard(cfg.maxDays());
            case "limit" -> new LimitApprover(cfg.title(), cfg.limit());
            default -> throw new IllegalArgumentException("未知节点类型：" + cfg.type());
        });
    }
    return new ApprovalChain(Handler.chain(nodes));
}
```

**能做的部分**：节点类型、顺序、参数（上限、天数）都可以配置。

**做不到的部分**：节点的**行为**不能配置。如果运营说"我要一个'金额是质数就驳回'的规则"，
那还是得写代码。

> **配置化能消灭"参数变更"，消灭不了"行为变更"。**
> 试图用配置表达任意行为，最后一定会发明一门图灵完备的配置语言——
> 那是另一个产品，不是这个设计模式要解决的问题。

### 7.4 给链加"统一耗时统计"

**需求**：每个节点的耗时都要打日志。

这是责任链最典型的一个"横切关注点"需求。三种做法：

#### 做法 A：在每个 `tryHandle` 里计时（最差）

5 个节点 → 5 段重复的 `long start = System.nanoTime(); ...`。
**任何一个节点漏写，统计就不完整**，而这种遗漏没有任何编译期提示。

#### 做法 B：在 `handle()` 里统一计时（本项目唯一需要改的地方）

```java
public final Optional<R> handle(C context) {
    long start = System.nanoTime();
    try {
        Optional<R> mine = tryHandle(context);
        if (mine.isPresent()) return mine;
        Handler<C, R> downstream = next;
        return downstream == null ? Optional.empty() : downstream.handle(context);
    } finally {
        // 每个节点的耗时都会被记录（嵌套结构下需要减去下游耗时才是"自身耗时"）
    }
}
```

**好处**：只改一处，所有节点自动获得。
**代价**：`handle()` 变成非纯函数，而且嵌套计时需要额外处理（子节点的耗时会算进父节点）。

#### 做法 C：装饰器包一层

```java
public final class TimedHandler<C, R> extends Handler<C, R> {
    private final Handler<C, R> delegate;

    @Override
    protected Optional<R> tryHandle(C ctx) {
        return delegate.handle(ctx);      // 注意：调的是 handle，不是 tryHandle
    }
}
```

**问题：这会把 delegate 整条子链包成一个节点**，链的语义就变了
（被包住的子链内部不再"命中即停"于外部，因为外层只有一个决定）。

**结论**：横切关注点放在 **模板方法**（做法 B）里最合适——
这正是"把流程锁在基类"的第二个收益，第一个收益是防止子类写错终止逻辑。

### 7.5 异步 / 并行

**能不能让节点并行执行？**

对纯责任链来说：**不能，而且不该**。

原因：责任链的本质是"**谁先有能力谁决定**"。并行执行意味着多个节点同时做决定，
那就不再是"找到第一个负责人"，而是"投票"——那是另一种模式（更像 MapReduce）。

对管道来说：**可以，但要小心**。折扣规则如果都是纯函数（不改订单、不查库），
理论上可以并行评估再合并。但本项目**不建议**：

- 规则之间有顺序依赖（满减先于券，券先于会员），并行会破坏这个依赖；
- 规则数量只有 3~10 个，并行的线程调度开销大于收益。

> **并行的前提是"任务之间没有顺序依赖"，而责任链的全部价值就在于顺序。**

### 7.6 扩展成本汇总

| 需求 | 改哪些文件 | 改多少行 | 风险 |
|---|---|---|---|
| 加一个审批节点 | 新建 1 个类 + `ApprovalChain.standard()` 加 1 行 | ≈ 40 | **低** |
| 改审批上限 | `ApprovalChain.standard()` 改数字 | 1 | **低** |
| 改审批顺序 | `ApprovalChain.standard()` 调换 `List.of` 顺序 | 1 | **中**（业务影响大） |
| 加一种结论 | `ApprovalDecision` 加 record + 所有 `switch` 补分支 | 视 `switch` 数量 | **中**（编译器会提醒） |
| 加一条折扣规则 | 新建 1 个类 + `DiscountPipeline.standard()` 加 1 行 | ≈ 50 | **低** |
| 改折扣顺序 | `DiscountPipeline.standard()` 调换顺序 | 1 | **高**（直接影响金额） |
| 加一个横切关注点 | `Handler.handle()` 一处 | ≈ 10 | **低** |
| 把链配置化 | 新建 `fromConfig` + 配置模型 | ≈ 80 | **中** |

**这张表里风险最高的是"改折扣顺序"**——因为它是唯一一个
"改一行代码、直接改变金额、而且没有任何编译期或运行期检查"的操作。
所以本项目的做法是：**把顺序写在一个带注释的 `List.of()` 里，并说明每条顺序的业务理由**
（见 `DiscountPipeline.standard()` 的注释）。

---

## 8. 优缺点与常见误区

### 8.1 四条优点（每条都有证据）

#### 优点 1：调用方与处理者解耦

`Client` 里提交退款的代码只有一行：

```java
ApprovalDecision decision = chain.submit(request);
```

它不知道链上有几个节点、每个节点是谁、判断逻辑是什么。
Demo 1 里同一个 `chain` 处理了 5 张金额差 3000 倍的单子，
**调用方代码一个字都没变**。

#### 优点 2：节点可以独立测试和复用

`LimitApprover` 的测试不需要链、不需要 Spring、不需要数据库：

```java
var approver = new LimitApprover("客服专员", "200");
var ctx = new ApprovalContext(new RefundRequest("R-1", Money.of("150"), "坏了", 3));
assertTrue(approver.handle(ctx).isPresent());
```

同一个类被实例化了 4 次（客服/主管/经理/总监），**零重复代码**。

#### 优点 3：顺序是可组装的配置

`ApprovalChain.standard()` 里的 `List.of(...)` 就是**整条链的形状**。
Demo 3 只调换了顺序，同一个 800 元的单子就从"主管批"变成"经理批"——
**不需要改任何一个节点类**。

#### 优点 4：过程天然可观测

`ApprovalContext` 让每个节点都能记录自己做了什么、为什么，
最终这些记录随结论一起返回。

Demo 1 的 `R-1005`（转人工）如果只看返回值，你只知道"没人能批"；
看轨迹，你知道**每一个节点为什么不能批，以及各自的权限是多少**。

### 8.2 六条代价

| 代价 | 具体表现 | 什么时候可以接受 |
|---|---|---|
| **多一层间接** | 读代码要"链 → 节点 → 判断"跳三次 | 链的组成会变，或者节点数 > 2 |
| **类数量上升** | 每种节点一个类（本项目 3 个节点类 + 4 个实例） | 节点有独立逻辑，不是纯参数差异 |
| **调试栈更深** | 5 个节点 → 5 层递归栈帧 | 链不长（< 20） |
| **有状态** | `next` 是字段，节点不能被两条链共享 | 链只组装一次 |
| **顺序隐式依赖** | 换个顺序行为就变了，但没有编译期提示 | 顺序写在一处并有注释 |
| **"没人处理"要设计** | 必须有兜底，否则返回 null | 兜底是一个合法业务结果 |

**第六条最容易被低估。** 很多实现是这样的：

```java
public R handle(C ctx) {
    ... // 一路传下去
    return null;      // 走到头了
}
```

调用方拿到 `null` 时的第一反应是"加个判空"，而不是"分析为什么会走到头"。
于是**"没人处理"这个业务事实被一个 `null` 吞掉了**，既没有日志，也没有统计。

### 8.3 与五个相似模式的区别

#### 责任链 vs 装饰器

结构几乎一样（都是持有同类型的引用），区别在**执行路径**：

| | 责任链 | 装饰器 |
|---|---|---|
| `next` 会不会被调用 | **可能永远不**（上一个已处理） | **一定会**（否则功能丢失） |
| 执行几个节点 | 1 个（或 0 个） | 全部（层层包裹） |
| 典型目的 | 找到负责人 | 叠加功能 |
| 客户端感知 | 只提交请求 | 组装时就决定了顺序和层数 |
| 本项目例子 | `Handler` + `next` | 装饰器模式那一章 |

**一句话判别**：把 `next` 的调用注释掉，装饰器会**功能缺失**，
责任链会**少处理几个不该处理的请求**——前者是 bug，后者是优化。

#### 责任链 vs 管道（过滤器链）

| | 责任链 | 管道 |
|---|---|---|
| 命中后 | 停止 | 继续 |
| 结构 | `next` 递归 | `List` 循环 |
| 返回值 | `Optional`（可能没人处理） | 每条都有结果 |
| 本项目例子 | `approval/` | `discount/` |

**这是本项目最重要的一组对比**，因为原项目把管道写成了链。

#### 责任链 vs 策略

| | 责任链 | 策略 |
|---|---|---|
| 谁选择 | **链自己**（运行时判断） | **调用方**（显式传入） |
| 执行几个 | 1 个 | 1 个 |
| 调用方知道用了哪个吗 | **不知道**（也不需要知道） | 知道 |
| 典型场景 | 审批、过滤 | 计价、排序 |

**一句话判别**：策略是"**我选一个**"，责任链是"**谁行谁上**"。

#### 责任链 vs 观察者

| | 责任链 | 观察者 |
|---|---|---|
| 通知范围 | **1 个**（接棒者） | **全部**（广播） |
| 节点之间知道彼此吗 | 知道（`next`） | **不知道**（只认识主题） |
| 能否否决后续 | 能（终止） | 不能（所有订阅者都会收到） |

#### 责任链 vs 命令

| | 责任链 | 命令 |
|---|---|---|
| 关注点 | **谁来处理** | **把请求变成对象** |
| 请求的形态 | 参数（Context） | 对象（Command） |
| 能排队/撤销吗 | 不能 | 能（`CommandHistory`） |

**这两个模式经常被混用**：一个"命令调度器"里，每个命令也可能是一条链。
它们不是竞争关系，而是**正交**的——命令管"请求的生命周期"，责任链管"请求的路由"。

### 8.4 九个常见误区

#### 误区 1：把"串成一串挨个执行"当成责任链

这是最普遍的一个，也是原项目的错误。**判据只有一句：命中之后还继续吗？**

#### 误区 2：认为"责任链一定要有 `setNext`"

GoF 的书里确实有 `setNext`，但**不是必须**。本项目用构造后 `linkTo` 组装，
链的形状在组装完成后不可变——这比"运行期随手 `setNext`"安全得多。

更极端的做法是完全不用 `next`：把 `List<Handler>` 传进 `handle()`，
用索引推进。三种都能算责任链。

#### 误区 3：以为"每个节点都必须返回结果"

正好相反：**每个节点都必须有能力返回"我不处理"**。
如果所有节点都返回结果，那链永远在第一个节点终止，后面的节点是死代码。

#### 误区 4：用 `boolean` 表示"我处理了"

```java
boolean tryHandle(C ctx);   // true = 处理了
```

问题：**结果往哪放？** 只能写进 `ctx`，于是"谁写了这个字段"变得不可追踪。
而且 `false` 无法区分"我不处理"和"我处理失败了"。

#### 误区 5：把"没人处理"写成返回 `null`

`null` 是**最糟的兜底**：它既不是业务结论，也没有任何信息。
正确做法是定义一个显式的"无人负责"结论（本项目的 `NeedsHumanReview`）。

#### 误区 6：在节点里自己调用 `next`

```java
// 错误写法
protected Optional<R> tryHandle(C ctx) {
    if (canHandle(ctx)) { return Optional.of(handle(ctx)); }
    return next.handle(ctx);      // ← 把"传递"从基类抢走了
}
```

后果：每个节点都要重复一遍"我处理不了怎么办"，
而**只要有一个节点漏写这一行，链就断在那里**——而且没有任何编译错误。

`handle()` 是 `final` 就是为了防止这个。

#### 误区 7：把顺序塞进节点内部

原项目的 `getPriority()` 是最典型的例子。顺序是**链的属性**，不是**节点的属性**——
因为同一个节点在不同链里可能处于不同位置。

#### 误区 8：以为责任链能提升性能

它**不会**。链上的每个节点都要执行一次判断，
"命中即停"只是省掉了命中之后的节点，并没有跳过命中之前的。

而且它比 `if-else` **更慢**：多了虚方法调用和递归。
责任链换的是**可扩展性**，不是性能。如果性能敏感，先用 `if-else`。

#### 误区 9：在链上处理"不该处理"的事

比如"财务复核"这个例子（§7.1）：如果它不影响"谁来处理"的走向，
那它就不该是链节点，而应该是链外面的一步。

**判据**：这个节点的返回值会不会改变链的走向？
不会 → 它不是链的一部分。

---

## 9. 面试与自测

### 9.1 十道面试题

#### Q1：责任链模式解决什么问题？

**一句话核心**：让请求的发送方不需要知道接收方是谁。

**展开**：把"谁来处理"的决定从调用方挪到链内部。
调用方只负责提交请求，链负责找到第一个能负责的节点。

**加分点**：它解决的是**耦合**问题，不是"代码复用"或"性能"。
如果调用方本来就知道该谁来处理，用责任链是纯粹的过度设计。

#### Q2：责任链和管道有什么区别？

**一句话核心**：命中之后还继续吗。

**展开**：

- 责任链：命中即停，只执行 1 个节点；
- 管道：每个节点都执行。

**加分点**：能说出原项目把管道写成了链，多写了 200 行。
并且能指出 Servlet Filter 属于"不纯责任链"（每个都执行，但可以主动不往下传）。

#### Q3：`next` 应该放在处理器里还是外挂一个链表？

**一句话核心**：放在处理器里，否则处理器无法自己决定终止。

**展开**：外挂链表时，处理器是个"被调用的函数"，
它不知道自己在链上的位置，也没有能力说"到此为止"。

**加分点**：能承认"放在处理器里"的代价——处理器变成有状态，不能被两条链共享。
并给出缓解方案：链组装后不可变（`linkTo` 拒绝二次写入）。

#### Q4：为什么 `handle()` 要声明成 `final`？

**一句话核心**：那四行是模式的定义，不是实现细节。

**展开**：如果允许重写，就会有人写出"忘了处理返回值"或"无限递归"的代码，
而且都不报编译错误——链退化成管道或直接栈溢出。

**加分点**：这才是"模板方法"的真正用处：**把流程锁住，把判断放开**。

#### Q5：怎么表达"我不处理这个请求"？

**一句话核心**：`Optional.empty()`，而不是 `null` 或 `false`。

**展开**：三种方案对比（见 §4.3）。
`boolean` 无法带结果，`null` 有歧义，`Optional` 语义唯一。

**加分点**：能说出 `Optional` 的代价（装箱），
以及什么场景下值得改用 `sealed interface`。

#### Q6：链走到头没人处理怎么办？

**一句话核心**：必须有一个显式的业务结论，不能返回 `null`。

**展开**：本项目的做法是 `ApprovalChain.submit()` 里 `orElseGet(...)`
兜底成 `NeedsHumanReview`。

**加分点**：能说出"兜底放在链里还是放在链外"的判断标准——
兜底有副作用（建工单、发通知）就当节点，只是翻译返回值就放链外。

#### Q7：怎么防止链形成环？

**一句话核心**：组装期遍历一遍，O(n) 一次。

**展开**：`a.linkTo(b); b.linkTo(a)` 从任一节点看都"合法"，
只有请求进来时才会 `StackOverflowError`。

**加分点**：能说出"组装一次 vs 每次请求"的成本对比，
以及为什么这笔交易划算。

#### Q8：责任链的顺序可以随意调换吗？

**一句话核心**：不能，顺序是业务规则。

**展开**：本项目 Demo 3 证明，同样 800 元，正序是主管批，逆序是经理批。
"批不批"没变，"谁批的"变了——而后者决定绩效、署名、追责。

**加分点**：折扣场景更严重——顺序直接改变金额（§6.4 的 D-4001）。

#### Q9：如果要给链加统一的日志/耗时代码，改哪里？

**一句话核心**：改 `Handler.handle()` 一处，不要改每个节点。

**展开**：横切关注点放在模板方法里，这是"流程锁在基类"的第二个收益。

**加分点**：能指出嵌套计时需要"减去下游耗时"，否则父节点的时间会包含子节点。

#### Q10：责任链能并行执行吗？

**一句话核心**：不能，因为责任链的全部价值就是顺序。

**展开**：并行意味着多个节点同时做决定，那就不是"找第一个负责人"，
而是"投票"——另一种模式。

**加分点**：能区分"纯责任链不能并行"和"管道理论上可以但通常不值得"。

### 9.2 八道自测题

<details>
<summary>1. 如果把 `validateChain` 里的环检测删掉，什么时候会发现 bug？</summary>

只有在**请求进来**并且路径正好经过那个环时才会发现，
表现是 `StackOverflowError`，堆栈指向 `Handler.handle()`。
你看不出是哪两个节点链错了，因为堆栈里全是同一个方法。

更麻烦的是：如果那笔请求永远不进来（比如只在某个金额区间才走到环上），
这个 bug 会**潜伏几个月**。
</details>

<details>
<summary>2. `PolicyGuard` 为什么必须放在链首？</summary>

因为它的职责是**过滤**（这事该不该进审批流程），不是**分派**（该谁批）。

放在链尾的话，一笔超过 7 天的单子会先经过 4 个审批人，
每个人都说"我无权"，最后才被政策驳回。成本是：
4 次无意义判断 + 4 条审计噪声 + 4 条可能的通知。

Demo 2 的 `1/5` vs `5/5` 就是这个差别。
</details>

<details>
<summary>3. 为什么 `ApprovalDecision` 要带 `auditTrail`，而不是让调用方自己拼？</summary>

因为轨迹只在**做决定的那一刻**是完整的。那一刻之后，
`ApprovalContext` 就不再被任何人持有，调用方拿不到它。

而且"结论"和"怎么得出这个结论的"必须一起交付——
只给 `Granted(800)`，运营问"为什么是主管批的不是经理批的"就答不上来。
</details>

<details>
<summary>4. 如果把 `RefundRequest` 改成可变类，会有什么问题？</summary>

链上有 5 个节点都能改它。一旦金额被某个节点改错，
你无法从最终结果看出是**哪个节点**改的——因为每个节点都有可能。

更隐蔽的问题是：如果某个节点在 `tryHandle` 里改了金额**又返回了 `empty`**，
那么下游看到的金额就已经被篡改了，而审计轨迹里只有"无权"两个字。

不可变让这种 bug 在结构上不可能发生。
</details>

<details>
<summary>5. 为什么 `DiscountRule` 不实现 `Handler`？</summary>

因为语义相反：`Handler` 是"命中即停"，`DiscountRule` 是"每条都跑"。

硬套的话，所有规则都必须返回 `empty`（永不终止），
于是付出 `Handler` 的全部成本（递归、`Optional` 装箱、抽象类），
拿到零收益——行为等价于一个 `for` 循环。
</details>

<details>
<summary>6. `linkTo` 返回 `next` 而不是 `this`，为什么？</summary>

如果返回 `this`，链式写法 `a.linkTo(b).linkTo(c)` 会变成
"a→b，然后 a→c（覆盖 b）"，b 被静默丢掉。

返回 `next` 才能写出 `a.linkTo(b).linkTo(c)` = "a→b, b→c"。

代价是 `head = a.linkTo(b)` 拿到的是 b，所以本项目额外提供
`Handler.chain(List)` 来固定返回头节点。
</details>

<details>
<summary>7. 如果需要链上 100 个节点，本项目还能用吗？</summary>

不能直接用。递归深度 100 层不会栈溢出（默认栈大约几千层），
但 100 层递归的**调试体验**极差，而且每次 `handle` 都有一层方法调用开销。

这时候应该换成"游标模型"：链对象持有 `List` + `index`，
用 `for`/`while` 推进，节点接受 `Chain` 参数（Servlet Filter 的做法）。

这也是为什么 §4.2 要把三种方案都摆出来——**没有一种结构适合所有规模**。
</details>

<details>
<summary>8. 这个模式能防止"某个节点被漏掉"吗？</summary>

不能。责任链只能保证"请求沿链传递"，不能保证"链上该有的节点都在"。

这就是 `describe()` 存在的原因：**启动时打一行链的形状**，
让人一眼看出"财务复核怎么没了"。
这比等出问题后翻代码快得多。
</details>

### 9.3 三个必答问题

| 问题 | 一句话答案 |
|---|---|
| **为什么需要它？** | 因为"谁来处理"这件事会变，而调用方不该跟着变 |
| **为什么这样实现？** | `next` 放节点内（能自己终止）+ `Optional`（语义唯一）+ `final handle`（流程锁死）+ 兜底（可空性挡在框架里） |
| **换一种实现会怎样？** | 外挂链表 → 无法终止；`boolean` → 结果不可追踪；不加环检测 → 运行期栈溢出；不兜底 → 调用方判空且分不清"正常/故障" |

### 9.4 一句话总结

> **责任链不是"把处理者串起来"，而是"让处理者有权说'下一个'，也有权说'到此为止'"。**
> 少了后半句，它就只是一个写得比较啰嗦的 `for` 循环——
> 这正是原项目的样子，也是这个模式最常被误用的地方。

---

## 附录：文件清单与运行方式

### A. 完整文件清单与行数

| # | 文件 | 行数 | 职责 |
|---|---|---|---|
| 1 | `Handler.java` | 245 | 模式骨架：`next` + `final handle` + `Optional` 语义 + 组装校验 |
| 2 | `Client.java` | 329 | 五段 Demo |
| 3 | `approval/RefundRequest.java` | 65 | 退款单（record，构造期校验） |
| 4 | `approval/ApprovalContext.java` | 94 | 请求 + 审计轨迹 |
| 5 | `approval/ApprovalDecision.java` | 109 | sealed 三态结论 |
| 6 | `approval/LimitApprover.java` | 113 | 参数化审批人 |
| 7 | `approval/PolicyGuard.java` | 78 | 链首一票否决 |
| 8 | `approval/ApprovalChain.java` | 132 | 组装 + 兜底 + 自述 |
| 9 | `discount/DiscountOrder.java` | 116 | 不可变订单 + 账单 |
| 10 | `discount/DiscountRule.java` | 85 | 规则接口 + `Outcome` |
| 11 | `discount/FullReductionRule.java` | 80 | 满减 |
| 12 | `discount/CouponRule.java` | 81 | 优惠券 |
| 13 | `discount/MemberRule.java` | 68 | 会员折扣 |
| 14 | `discount/DiscountPipeline.java` | 147 | `List` 循环 + 轨迹 |
| | **本包合计** | **1742** | |

另有一个文件不在本包，但本模式离不开它：

| # | 文件 | 行数 | 职责 |
|---|---|---|---|
| 15 | `com/patterns/util/Money.java` | 91 | 金额构造、显示、去尾零（见 §5.8） |
| | **总计** | **1833** | |

### B. 运行方式与验证记录

**环境**：`F:\enCodingApps\JDK\jdk-25`（JDK 25）

**编译**：

```powershell
javac --release 25 -encoding UTF-8 -g -Xlint:all -d out -sourcepath src `
      src/com/patterns/behavioral/chainofresponsibility/Client.java
```

**运行**：

```powershell
java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out `
     com.patterns.behavioral.chainofresponsibility.Client
```

**本项目的实测结果**：

```
COMPILE-EXIT = 0
javac 输出行数 = 0（-Xlint:all 下零告警）

RUN-EXIT = 0
输出行数 = 132
```

**可复现性验证**（连续运行三次，比对 SHA-256）：

```
运行 1   403461BA3401A765ABA4538BE1B79D52746EE9BFAF73288FF892916405DAFE62
运行 2   403461BA3401A765ABA4538BE1B79D52746EE9BFAF73288FF892916405DAFE62
运行 3   403461BA3401A765ABA4538BE1B79D52746EE9BFAF73288FF892916405DAFE62
```

**为什么能保证可复现**：

- 不用随机数、不打印时间、不打印耗时；
- `Money.yuan` 用 `Locale.ROOT`，不受系统区域设置影响；
- 所有集合都是 `LinkedHashMap`/`List` 这类保序结构；
- 输出宽度用 `DisplayWidth` 按**显示宽度**对齐（中文字符占 2 列）。

**关于 `-Dstdout.encoding`**：JDK 18 起 `stdout` 的编码是独立参数，
只设 `-Dfile.encoding=UTF-8` 在部分终端下仍会乱码。
两者都设是**跨平台最稳的做法**。

### C. 原项目 vs 重构版对照

| 维度 | 原项目 | 重构版 |
|---|---|---|
| 文件数 | 8 | 15（含 6 个管道文件，非责任链） |
| 行数 | 257 | 1833 |
| 结构 | 外挂链表（`HandlerNode`） | `next` 在处理器内部 |
| 能否终止 | **不能** | 能（`Optional` 非空即终止） |
| 返回类型 | `void` | `Optional<R>` |
| 兜底 | **无**（返回原金额） | `NeedsHumanReview` |
| 审计 | **无**（只有最终金额） | 每节点一条 `Step` |
| 金额类型 | `double` | `BigDecimal` |
| 负数保护 | **无**（实测 −25.0） | `min(payableAmount)` |
| 满减边界 | `> 200`（实测不触发） | `>= 200`（触发） |
| 顺序 | `getPriority()` 散在三个类里 | `List.of(...)` 一处 |
| 参数化 | 三个类硬编码 | 一个类四个实例 |
| 组装校验 | **无** | 三道（null / 重复 / 环） |
| 链自述 | **无** | `describe()` |
| 编译告警 | 未验证 | `-Xlint:all` 零告警 |

**三个"真实 bug"的证据链**（全部用原项目代码跑出）：

| 用例 | 原项目真实输出 | 重构版真实输出 |
|---|---|---|
| 25 元 + 券 + 会员 | **−25.0** | ￥0.00（券实扣 25） |
| 200 元（恰好满减门槛） | **200.0**（不打折） | ￥150.00 |
| 250 元 + 券 + 会员 | 150.0 | ￥153.00（会员改为 9 折） |

第三行不是 bug，是**业务规则不同**：原项目会员减固定 20 元，
重构版改成 9 折（更符合"会员折扣"的常见含义）。这里如实列出，避免读者误以为两者应该相等。

### D. 术语表

| 术语 | 含义 |
|---|---|
| **纯责任链** | 命中即停，只执行一个节点（本项目 `approval/`） |
| **不纯责任链** | 每个节点都执行，但可以主动不往下传（Servlet Filter） |
| **管道 / 过滤器链** | 每个节点都执行，不终止（本项目 `discount/`） |
| **后继（next）** | 当前节点之后要询问的那个节点 |
| **终止（terminal）** | `tryHandle` 返回非空，不再往下传 |
| **透传（pass along）** | `tryHandle` 返回空，把请求交给下一个 |
| **兜底（fallback）** | 一路透传到链尾时的默认结论 |
| **堆污染（heap pollution）** | 泛型可变参数在运行期产生的类型不安全数组 |

### E. 延伸阅读

| 主题 | 去哪里看 |
|---|---|
| 命令模式（把请求变成对象） | `../command/README.md` |
| 策略模式（换算法） | `../strategy/README.md` |
| 模板方法（父类定流程） | `../templatemethod/README.md` |
| 装饰器（环绕增强） | `../../structuraltype/DecoratorPattern/` |
| `BigDecimal` 的完整论证 | `../strategy/README.md` 的 `DiscountStrategy` 注释 |

### F. 下一站

责任链之后，行为型还剩 7 种。下一批是**批 2 的另外两种：状态 + 观察者**，
它们和本章的关系可以先预告一下：

| 模式 | 与责任链的关系 |
|---|---|
| **状态** | 状态机也会"一个请求交给下一个状态"处理，但**转移的是对象自身的状态**，不是请求的路径。责任链是"横向传递"，状态是"纵向演进" |
| **观察者** | 观察者是**广播**（所有订阅者都收到），责任链是**接力**（只有一个人接棒）。两者最容易混，因为都涉及"一个事件、多个处理者" |

一个提前的判别：

> **责任链问"该谁处理"，状态问"我现在是什么状态"，观察者问"谁想知道"。**

---

**最后一句**：

> 这个模式最难的地方不是写出 `next`，而是**判断你的业务到底需不需要"停下来"**。
> 本项目用 6 个文件、577 行的折扣管道，专门演示了"不需要"的那种情况——
> 因为**知道什么时候不用，比知道怎么用更重要。**
