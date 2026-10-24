# 模板方法模式（Template Method Pattern）

> **一句话**：把「一件事的固定流程」写在父类里锁死（`final`），
> 把「流程中因情况而异的那几步」留给子类去填，
> 于是**流程永远走不乱，而细节可以随便加**。
>
> **本目录代码已用 JDK 25 实际编译运行验证**，文中所有输出、所有数字、所有 bug 都是真实发生过的，不是编造的。

---

## 目录

1. [现实案例：公司报销流程](#1-现实案例公司报销流程)
2. [原始实现：原项目源码与它的五个问题](#2-原始实现原项目源码与它的五个问题)
3. [设计动机：为什么必须换一种写法](#3-设计动机为什么必须换一种写法)
4. [设计推导：从四行 println 一步步推出完整设计](#4-设计推导从四行-println-一步步推出完整设计)
5. [代码解析：六个文件各自为什么存在](#5-代码解析六个文件各自为什么存在)
6. [执行流程：七段 Demo 的真实运行结果](#6-执行流程七段-demo-的真实运行结果)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试题与自测](#9-面试与自测)
- [附录：文件清单与运行方式](#附录文件清单与运行方式)

---

## 1. 现实案例：公司报销流程

### 说人话版

你在公司出差回来，提交了一张 3800 元的差旅报销单。财务系统会走这么一套流程：

```
① 提交单据（附发票）
② 主管审批
③ 财务复核（发票真伪、科目归类）
④ 打款到工资卡
⑤ 发短信通知你「已到账」
```

现在关键的问题来了：**这五步的顺序能不能改？**

- 把 ④ 打款挪到 ② 审批前面 → **钱先出去了才有人审**，审出问题是虚假发票也追不回来。这是资金事故。
- 把 ⑤ 通知挪到 ④ 打款前面 → 你收到「已到账」短信但卡里没钱，客服电话被打爆。
- 把 ③ 财务复核删掉 → 假发票直接报销成功。

**顺序是业务强制的，任何人无权调整。** 这是第一个事实。

第二个事实是：**不同报销类型，某些步骤的做法不一样。**

| 报销类型 | 主管审批 | 财务复核 | 打款 |
|---|---|---|---|
| 差旅费 | 要审 | 标准复核 | 全额打款 |
| 招待费 | 要审 | **额外核查是否超标** | 全额打款 |
| 200 元以下小额 | **免审** | 标准复核 | 全额打款 |
| 外币报销 | 要审 | **按当日汇率折算** | **打外币账户** |

第三个事实：**有些步骤对某些类型压根不该执行。**

200 元以下的小额报销免主管审批——不是「审批通过了」，而是**这一步根本不发生**。
这两件事在审计时天差地别：前者说明主管认可了这笔钱，后者说明公司制度授权跳过。
出了贪污案，责任归属完全不同。

于是财务系统的架构就长这样：

- **总部**写死一份《报销作业规程》：有哪几步、什么顺序、哪一步失败必须终止整个流程。
  这份规程**不允许任何分公司修改**。
- **各分公司**只能在规程留出的空位里填自己的做法：「我们这儿的主管审批是这样走的」。
- 规程里还留了几个**开关**：「是否需要主管审批？默认需要，小额报销把它设成不需要」。

**总部控制流程，分公司填充细节**——这就是模板方法模式。

那份不可修改的《报销作业规程》，就是**模板方法（Template Method）**。
分公司要填的空位，就是**抽象方法（Abstract Method）**。
总部已经写好、分公司可以直接用的部分，就是**具体方法（Concrete Method）**。
那几个开关，就是**钩子方法（Hook Method）**。

### 专业版

> **GoF 定义**：定义一个操作中的算法骨架，而将一些步骤延迟到子类中。
> 模板方法使得子类可以不改变一个算法的结构即可重定义该算法的某些特定步骤。

拆开来是三个要点：

1. **算法骨架在父类**：步骤的**数量、顺序、终止条件**全部由父类决定；
2. **可变步骤延迟到子类**：父类只声明「这里要做支付」，不规定「怎么支付」；
3. **子类不改变结构**：子类**没有能力**调整骨架——靠 `final` 强制，不是靠自觉。

角色映射：

| 现实 | 模式角色 | 修饰符 | 本项目类/方法 |
|---|---|---|---|
| 《报销作业规程》全文 | 抽象模板 AbstractClass | `public abstract class` | `OrderProcessTemplate` |
| 规程里那份「五步流程图」 | 模板方法 Template Method | `public final` | `processOrder(OrderContext)` |
| 分公司必须填的空位 | 抽象方法 Abstract Method | `protected abstract` | `pay()`、`deliver()` |
| 总部已写好、可直接用的条款 | 具体方法 Concrete Method | `protected` | `checkStock()`、`notifyCustomer()` |
| 规程里的开关/可选扩展位 | 钩子方法 Hook Method | `protected`（带默认实现） | `needsStockCheck()`、`deliveryProofLabel()`、`beforePay()` |
| 某一家具体的分公司 | 具体子类 ConcreteClass | `public class extends` | `PhysicalProductOrder`、`VirtualProductOrder`、`PreSaleOrder` |
| 那张报销单本身（在流程中被逐步填写） | （GoF 未定义，工程必备） | `public class` | `OrderContext` |

> **注意最后一行**：GoF 原书的模板方法**不带参数**——步骤方法之间靠父类的**字段**共享数据。
> 但「用继承体系的字段当数据总线」在实践中很难维护（字段可见性、生命周期、线程安全都是坑）。
> 现代写法普遍改成**显式传一个上下文对象**，Spring 的 `JdbcTemplate`、
> MyBatis 的 `BaseExecutor`、Flowable 的 `DelegateExecution` 都是这个路子。
> 本项目采用上下文对象方案，理由在 [第 4.3 节](#43-问题二步骤之间怎么传数据三种方案的取舍) 详细推导。

### 这个模式在真实框架里到处都是

模板方法**不是你才会用到的教学玩具**，它是 Java 生态里出现频率最高的模式之一。
你每天用的框架里，随手就能找出五六个：

| 框架/库 | 模板方法 | 骨架规定的事 | 留给你填的钩子 |
|---|---|---|---|
| `javax.servlet.http.HttpServlet` | `service(req, resp)` | 解析 HTTP 方法 → 分发 | `doGet()` / `doPost()` / `doPut()` |
| Spring `AbstractApplicationContext` | `refresh()` | 12 步容器启动流程 | `postProcessBeanFactory()`、`onRefresh()`、`finishRefresh()` |
| Spring `JdbcTemplate` | `execute()` | 拿连接 → 执行 → 处理异常 → 释放连接 | `RowMapper.mapRow()` |
| JUnit 5 `@BeforeEach`/`@Test`/`@AfterEach` | 测试执行器 | 前置 → 执行 → 后置 → 报告 | 你标注的测试方法 |
| `java.util.AbstractList` | `iterator()` | 迭代协议（hasNext/next/remove） | `get(index)`、`size()` |
| MyBatis `BaseExecutor` | `query()` | 查一级缓存 → 命中就返回，否则查库 | `doQuery()` |
| Flowable/Activiti | 流程引擎的执行链 | 节点流转规则 | `JavaDelegate.execute()` |

**共同点**：骨架里都包含了「资源获取/释放」「异常处理」「日志/审计」这类**必须做对、做错代价极高**的公共逻辑。
把它们收敛到父类，等于给所有实现者上了一道保险。

> 以 `JdbcTemplate` 为例：JDBC 原生写法有 20 多行，其中 15 行是
> `try { ... } finally { conn.close(); }` 这种样板。
> **忘了 close 连接是生产事故的头号原因之一。**
> `JdbcTemplate` 把 open/close/异常翻译固定在模板里，你只提供一行 `RowMapper`——
> 你想忘记关连接都做不到。这就是模板方法模式最有说服力的价值证明。

---

## 2. 原始实现：原项目源码与它的五个问题

### 2.1 原项目的完整源码（真实文件，逐字摘录）

原项目路径：`design_patterns/src/BehaviorType/TemplateMethodPattern/`，共 4 个文件、97 行。

**`OrderProcessTemplate.java`（35 行）**

```java
package BehaviorType.TemplateMethodPattern;

abstract class OrderProcessTemplate {
    // 模板方法，定义了订单处理的固定步骤
    public final void processOrder() {
        checkStock();
        pay();
        deliver();
        notifyCustomer();
    }

    // 校验库存的步骤，所有订单都需要
    protected void checkStock() {
        System.out.println("检查库存");
    }

    // 支付步骤，交由具体子类实现
    protected abstract void pay();

    // 发货步骤，交由具体子类实现
    protected abstract void deliver();

    // 通知客户的步骤，所有订单都需要
    protected void notifyCustomer() {
        System.out.println("通知客户订单处理完成");
    }
}
```

**`PhysicalProductOrder.java`（20 行）**

```java
public class PhysicalProductOrder extends OrderProcessTemplate {
    @Override
    protected void pay() {
        System.out.println("支付实物商品的订单");
    }

    @Override
    protected void deliver() {
        System.out.println("发货实物商品");
    }
}
```

**`VirtualProductOrder.java`（21 行）**

```java
class VirtualProductOrder extends OrderProcessTemplate {     // ← 注意：没有 public
    @Override
    protected void pay() {
        System.out.println("支付虚拟商品的订单");
    }

    @Override
    protected void deliver() {
        System.out.println("通过邮件发送虚拟商品");
    }
}
```

**`Client.java`（21 行）**

```java
public class Client {
    public static void main(String[] args) {
        System.out.println("处理实物商品订单：");
        OrderProcessTemplate physicalOrder = new PhysicalProductOrder();
        physicalOrder.processOrder(); // 调用模板方法

        System.out.println("\n处理虚拟商品订单：");
        OrderProcessTemplate virtualOrder = new VirtualProductOrder();
        virtualOrder.processOrder(); // 调用模板方法
    }
}
```

### 2.2 原项目的真实运行输出

下面这段输出**是我用 JDK 25 实际编译运行原项目得到的**，不是照代码推演的：

```
处理实物商品订单：
检查库存
支付实物商品的订单
发货实物商品
通知客户订单处理完成

处理虚拟商品订单：
检查库存
支付虚拟商品的订单
通过邮件发送虚拟商品
通知客户订单处理完成
```

### 2.3 先说原项目做对了什么

**必须承认：原项目的模式骨架是完全正确的。** 这一点很重要，本次重构保留了它。

| 做对的地方 | 体现在哪 |
|---|---|
| 模板方法加了 `final` | `public final void processOrder()` |
| 模板方法只负责编排，不含业务 | 方法体就是四行调用 |
| 共用步骤放父类实现 | `checkStock()`、`notifyCustomer()` 是具体方法 |
| 差异步骤声明为抽象 | `pay()`、`deliver()` 是 `protected abstract` |
| 步骤方法用 `protected` 而非 `public` | 外部无法单独调用某一步 |
| 调用方持有父类引用 | `OrderProcessTemplate physicalOrder = new PhysicalProductOrder()` |

**这六条正是模板方法模式的全部核心约束，一条都没错。**
如果只是想「跑通一个模板方法的最小示例」，原项目已经达标了。

下面五个问题，都不是「模式用错了」，而是「模式只用了三成威力」——
它们让这个示例**无法回答学习者真正会问的那些问题**。

### 2.4 问题一：完全没有钩子方法（最严重的缺失）

原项目有三种方法角色：模板方法、抽象方法、具体方法。**第四种「钩子方法」一个都没有。**

而钩子恰恰是模板方法模式**最有实用价值**的部分。缺了它，模式就退化成一件事：
「父类定死四步，子类填两步」——完全无法应对「某一步压根不该执行」的现实需求。

**这个缺失直接导致了业务错误。** 看 2.2 的输出：

```
处理虚拟商品订单：
检查库存          ← 这一行是【错的】
```

`VirtualProductOrder` 卖的是「Java 编程思想电子书」这类虚拟商品。
**电子书的库存是无限的**，「检查库存」这个动作对它没有任何意义。

这不是吹毛求疵。设想一个真实场景：
虚拟商品的「库存」字段在数据库里可能是 `null` 或者 `0`（因为压根没维护）。
如果 `checkStock()` 是个真实的库存查询，它会返回 0，
然后**整个流程会在第一步就被判定为「无货」而中止**——
用户买电子书永远买不成。

> **原项目之所以看不出这个 bug，是因为它的 `checkStock()` 只有一行 `println`，
> 不做任何判断、不产生任何数据。**
> 这正是问题二（全是 void）掩盖了问题一（缺钩子）的后果。
> 两个缺陷互相打掩护，这在真实代码评审里非常常见。

**有了钩子之后的写法**（本项目 `VirtualProductOrder`）：

```java
@Override
protected boolean needsStockCheck() {
    return false;      // 虚拟商品库存无限，跳过步骤 ①
}
```

模板方法里：

```java
if (needsStockCheck()) {
    checkStock(ctx);
    // ...
} else {
    ctx.record("跳过库存检查（" + getClass().getSimpleName() + ".needsStockCheck() = false）");
    System.out.println("  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①");
}
```

**注意那个 `else` 分支不是多余的**：跳过一件事，和做了但没结果，是两件不同的事。
审计时必须能区分。原项目连这个概念都没有。

### 2.5 问题二：四个步骤全是 `void`，步骤之间没有任何数据传递

```java
public final void processOrder() {      // ← void
    checkStock();                        // ← void
    pay();                               // ← void
    deliver();                           // ← void
    notifyCustomer();                    // ← void
}
```

这带来一个**致命的教学问题**：

> 既然步骤之间没有数据依赖，那把顺序调换一下会怎样？
>
> **答案是：什么都不会发生，输出只是换了个顺序。**
>
> 于是 `public final void processOrder()` 里那个 **`final` 就失去了意义**——
> 读者无法理解「为什么这个顺序不允许子类修改」。

`final` 是模板方法模式的**灵魂**。一个无法论证 `final` 必要性的示例，
等于把模式最核心的约束讲成了「一种编程习惯」。

真实的订单流程是有严格数据依赖的：

```
① 检查库存 ──产出──▶ 可用库存数
                        │
                        ▼ 库存 ≤ 0 必须中止，否则超卖
② 支付     ──产出──▶ 支付流水号
                        │
                        ▼ 财务对账要求发货记录必须带支付流水号
③ 发货     ──产出──▶ 物流单号 / 下载链接
                        │
                        ▼ 通知内容必须包含交付凭证，否则用户不知道去哪收货
④ 通知客户 ──读取──▶ 上面所有产出
```

有了这条依赖链，`final` 的必要性就**可以被运行验证**了。
本项目的 demo6 就做了一件原项目做不到的事——**故意把顺序写反，用真实输出证明后果**：

```
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0011
  │        └ 关联支付流水 null  ← 这个值来自步骤 ②，所以顺序不能调换

      >>> 发货步骤里读取的「关联支付流水」是什么？
          ctx.getPaymentNo() = null
          ↑ 【null】！因为 pay() 还没执行。
```

`null` 是从**真实运行的程序里打出来的**，不是我写在文档里的假设。

### 2.6 问题三：没有失败短路——库存为 0 也会照常收款发货

原项目的 `processOrder()` 是**无条件的四连调用**，中间没有任何判断。

这意味着：**即使库存是 0，流程也会走完「支付 → 发货 → 通知」。**

```java
// 原项目：库存不足时依然会输出这三行
支付实物商品的订单
发货实物商品
通知客户订单处理完成
```

这在真实系统里叫**超卖（oversell）**，是电商最经典的事故之一。
用户付了钱，仓库没货，只能道歉退款——
双十一零点几万个这样的订单，客服系统直接崩掉。

本项目补上了短路逻辑：

```java
if (needsStockCheck()) {
    checkStock(ctx);
    if (ctx.getAvailableStock() <= 0) {
        ctx.abort("库存不足（可用 " + ctx.getAvailableStock() + " 件），订单不予处理");
        System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
        return ctx;                       // ← 立即返回，绝不继续
    }
}
```

真实运行输出（demo4-A）：

```
  ┌─ 开始处理订单 ORD-P-002（杯子，￥29.90）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 0 件  ← 不足！
  └─ 流程中止：库存不足（可用 0 件），订单不予处理
      支付流水号 = null   ← 【null】说明根本没走到支付步骤
```

**「支付流水号 = null」是流程被真正短路的铁证。** 原项目给不出这样的证据，因为它没有数据。

### 2.7 问题四：可见性不一致，跨包时多态会失效

原项目三个类的可见性是**乱的**：

| 类 | 修饰符 |
|---|---|
| `OrderProcessTemplate`（父类） | **包级私有**（没有 `public`） |
| `PhysicalProductOrder`（子类） | `public` |
| `VirtualProductOrder`（子类） | **包级私有** |

`public` 子类继承包级私有父类，会造成一个**真实但很隐蔽**的问题。

假设另一个包想用：

```java
package com.example.order;

import BehaviorType.TemplateMethodPattern.PhysicalProductOrder;   // ✅ 能导入，它是 public

public class OrderService {
    public void handle() {
        // ❌ 编译错误：OrderProcessTemplate is not public in
        //    BehaviorType.TemplateMethodPattern; cannot be accessed from outside package
        OrderProcessTemplate t = new PhysicalProductOrder();

        // ✅ 只能这么写
        PhysicalProductOrder t2 = new PhysicalProductOrder();
        t2.processOrder();
    }
}
```

看明白问题了吗？**外部包能 `new` 这个子类，却无法声明父类类型的引用。**

后果是**多态失效**：

```java
// ❌ 写不出来，因为父类不可见
List<OrderProcessTemplate> processors = List.of(new PhysicalProductOrder(), new VirtualProductOrder());

// 只能退化成这样
List<PhysicalProductOrder> list1 = ...;
List<VirtualProductOrder> list2 = ...;
// 然后调用方被迫为每种类型写一套处理逻辑，instanceof 满天飞
```

**模板方法模式的全部价值就建立在「调用方面对的是父类」这个前提上。**
父类不可见，前提就塌了。

而两个子类可见性不一致（一个 `public` 一个包级私有）更是纯粹的疏漏——
没有任何设计意图能解释这种差异。

本项目统一为 `public`。demo5 因此才能写出这段**不含任何 `instanceof`** 的批处理代码：

```java
List<OrderProcessTemplate> processors = List.of(
        new PhysicalProductOrder(),
        new VirtualProductOrder(),
        new PreSaleOrder()
);
```

### 2.8 问题五：子类实现太单薄，看不出「为什么这一步必须由子类做」

原项目的子类实现：

```java
protected void pay() { System.out.println("支付实物商品的订单"); }
protected void deliver() { System.out.println("发货实物商品"); }
```

学习者看到这里，脑子里必然冒出一个问题：

> **「既然只是打印一句话，那把 pay() 写成父类的具体方法，
> 里面用 `if (this instanceof PhysicalProductOrder)` 判断一下不也行吗？
> 为什么要搞两个子类？」**

**原项目无法回答这个问题。** 因为它的两个实现之间**没有任何实质差异**——
都只是打印一句话，差异仅在文案上。

要让「必须是抽象方法」有说服力，子类实现之间必须存在**无法合并的实质差异**。
本项目的三个 `pay()`：

| 子类 | `pay()` 做什么 | 能合并吗 |
|---|---|---|
| `PhysicalProductOrder` | 全额支付，生成流水号 | — |
| `VirtualProductOrder` | **先校验充值账号**（充错账号无法撤回），再全额支付 | 实物商品没有「充值账号」概念 |
| `PreSaleOrder` | **只收 30% 定金**，算出尾款金额 | 另两个都是全额，比例逻辑无处安放 |

三个实现**没有共同的代码主体**，父类写不出一个通用版本。
这才是「必须声明为抽象方法」的真实理由。

`deliver()` 同理，三种交付物的形态完全不同：

| 子类 | `deliver()` 产出 | 特有逻辑 |
|---|---|---|
| `PhysicalProductOrder` | 物流运单号 | **称重，超过 30kg 改走重货专线** |
| `VirtualProductOrder` | 下载链接 | **用支付流水号做 token 防盗链** |
| `PreSaleOrder` | 预计到货日 | 登记生产排期 |

真实运行输出（demo1，超重分支被真实触发）：

```
  │  ③ 发货：已交付重货物流专线，运单号 SF20261022124832-0004
  │        └ 重量 45.0kg 超过 30kg 阈值，普通快递不收，改走专线
  │        └ 关联支付流水 PAY20261022124832-0003  ← 这个值来自步骤 ②，所以顺序不能调换
```

**「超重走专线」这个规则只应该存在于 `PhysicalProductOrder` 里。**
虚拟商品没有重量，预售商品发货时才知道重量。
把它放进父类就是污染——这正是「抽象方法」和「具体方法」的分界线。

### 2.9 五个问题汇总

| # | 问题 | 性质 | 后果 | 本项目如何解决 |
|---|---|---|---|---|
| 1 | 没有钩子方法 | **模式缺失** | 虚拟商品被迫「检查库存」，业务上是错的 | 补 3 个钩子：`needsStockCheck()`、`deliveryProofLabel()`、`beforePay()` |
| 2 | 四步全 `void` | **数据流缺失** | `final` 失去意义，无法论证顺序为何不可改 | 引入 `OrderContext`，步骤间真实传递数据 |
| 3 | 无失败短路 | **业务逻辑缺陷** | 库存为 0 也照常收款发货（超卖事故） | 模板方法中加 `abort()` + `return` |
| 4 | 可见性不一致 | **编码疏漏** | 跨包时无法声明父类引用，多态失效 | 全部统一为 `public` |
| 5 | 子类实现太薄 | **教学说服力不足** | 读者会质疑「为什么不用 instanceof」 | 三个子类的支付/交付逻辑存在无法合并的实质差异 |

---

## 3. 设计动机：为什么必须换一种写法

### 3.1 说人话版：不用这个模式，代码会烂在哪

假设没有模板方法模式，你要支持实物、虚拟、预售三种订单。
最直觉的写法是——**每个类型写一个完整的处理方法**：

```java
public class OrderService {

    public void processPhysicalOrder(Order o) {
        int stock = inventoryService.query(o.getSkuId());
        if (stock <= 0) { /* 中止 */ return; }
        String payNo = payGateway.charge(o.getAmount());
        BigDecimal weight = warehouse.weigh(o.getSkuId());
        String carrier = weight.compareTo(THIRTY_KG) > 0 ? "重货专线" : "顺丰";
        String trackNo = logistics.ship(o, payNo, carrier);
        notifyService.send(o, "物流单号：" + trackNo);
    }

    public void processVirtualOrder(Order o) {
        String account = o.getRechargeAccount();
        if (!isValid(account)) { /* 中止 */ return; }
        String payNo = payGateway.charge(o.getAmount());
        String link = "https://dl.example.com/" + o.getNo() + "?token=" + payNo;
        notifyService.send(o, "下载链接：" + link);
    }

    public void processPreSaleOrder(Order o) {
        BigDecimal deposit = o.getAmount().multiply(new BigDecimal("0.30"));
        if (deposit.compareTo(BigDecimal.ONE) < 0) { /* 中止 */ return; }
        String payNo = payGateway.charge(deposit);
        String eta = LocalDate.now().plusDays(30).toString();
        notifyService.send(o, "预计到货日：" + eta + "；尾款请于...");
    }
}
```

**看起来挺清楚的是吧？跑起来也没问题。** 问题在三个月后：

**场景一：财务说所有订单的通知里都必须带上支付流水号（对账要求）。**

你要改**三个方法**。改完还得回归测试三种订单。
漏改一个 → 那种订单的通知永远缺流水号，而且**编译器不会报错**。

**场景二：安全部门要求所有支付前必须做风控校验。**

又是三处。而且这次更危险——风控漏加一处，那条路径就成了攻击面。

**场景三：新增第四种订单类型「海外直邮」。**

你得从头写一个新方法。写的时候**只能靠记忆和肉眼**去对照前三个方法，
确认「该做的公共步骤都做了」。**没有任何机制保证你不遗漏。**

**场景四（最要命）：新来的同事觉得「先发货再收款」对客户体验更好，
在 `processPhysicalOrder` 里把两行代码调了个位置。**

编译通过、单元测试（如果他写了的话）也可能通过。
上线后：货发出去了、支付流水号是 `null`、财务对不上账。
**没有任何东西阻止他这么做。**

### 3.2 专业版：三个具体的工程收益

上面四个场景，对应模板方法模式的三个收益：

#### 收益一：消除重复代码（DRY）

公共逻辑（通知模板、流水号生成、审计记录、异常处理）**只写一次**，放在父类。

本项目的实测数据（用脚本统计，`code` 列 = 总行数 − Javadoc − 行注释 − 空行）：

| 文件 | 总行数 | Javadoc | 行注释 | 空行 | **实际代码** |
|---|---:|---:|---:|---:|---:|
| `OrderProcessTemplate.java` | 526 | 362 | 57 | 28 | **79** |
| `PhysicalProductOrder.java` | 143 | 85 | 9 | 12 | **37** |
| `VirtualProductOrder.java` | 207 | 137 | 9 | 13 | **48** |
| `PreSaleOrder.java` | 264 | 159 | 9 | 22 | **74** |
| `OrderContext.java` | 267 | 133 | 9 | 33 | **92** |
| `Client.java` | 373 | 65 | 13 | 39 | **256** |

**两个值得注意的数字：**

1. `PhysicalProductOrder` 的**全部可执行代码只有 37 行**，
   却获得了「库存检查 + 失败短路 + 流水号生成 + 审计轨迹 + 通知文案拼装 + 防御性校验」全套能力。
2. 父类的 **79 行可执行代码被三个子类完全共享**。

对比 3.1 那种「每个类型写一个完整方法」的写法：
那 79 行流程编排逻辑要在三个方法里各抄一遍，变成 **约 237 行分散的重复代码**——
而且它们会随时间逐渐不一致（这是必然的，不是可能）。

#### 收益二：把「必须做对的事」变成「无法做错的事」

这是模板方法模式**最被低估**的价值。

| 约束 | 靠什么保证 | 违反的后果 |
|---|---|---|
| 步骤顺序不可改 | `final` | 编译错误：`cannot override the final method` |
| 子类必须实现支付和发货 | `abstract` | 编译错误：`class is not abstract and does not override abstract method` |
| 外部不能跳过支付直接发货 | `protected` | 编译错误：`pay() has protected access`（跨包时） |
| 库存不足不能收款 | 模板方法里的 `if + return` | 运行时被拦截，流程中止 |
| 支付没产生流水号不能发货 | 模板方法里的防御性检查 | 运行时被拦截，明确报错 |

**前四条里有三条是编译器强制的。** 这和「写在文档里要求大家遵守」有本质区别——
文档会被忽略，编译器不会。

对比 3.1 场景四：在模板方法模式下，那位新同事**写不出**「先发货再收款」的子类：

```java
public class BadOrder extends OrderProcessTemplate {
    @Override
    public OrderContext processOrder(OrderContext ctx) {   // ← 编译错误
        deliver(ctx);
        pay(ctx);
        return ctx;
    }
}
```

```
error: processOrder(OrderContext) in BadOrder cannot override processOrder(OrderContext) in OrderProcessTemplate
  public OrderContext processOrder(OrderContext ctx) {
                     ^
  overridden method is final
```

**他的错误想法在编译阶段就被否决了，根本进不了代码库。** 这就是 `final` 的价值。

> 本项目 demo6 里那段反例**能跑起来**，是因为它绕过了模板方法、直接调用 `protected` 步骤，
> 而这依赖「Client 和订单类在同一个包」这个特殊条件。
> 这个限制以及生产环境的三种应对方式，在 [第 6.6 节](#66-demo6为什么模板方法必须是-final反例演示) 有诚实说明。

#### 收益三：新增类型时，已有代码零改动（开闭原则）

本项目**真实发生**的一次扩展：新增 `PreSaleOrder`（预售订单）。

| 文件 | 改动 |
|---|---|
| `PreSaleOrder.java` | **新建**（264 行） |
| `Client.java` | 加一段演示代码（客户端本来就要用新类型） |
| `OrderProcessTemplate.java` | **0 改动** |
| `OrderContext.java` | **0 改动** |
| `PhysicalProductOrder.java` | **0 改动** |
| `VirtualProductOrder.java` | **0 改动** |

**父类和两个兄弟类完全没被碰过。** 这意味着：

- 它们的回归测试**不用重跑**（因为它们的行为在数学上不可能变化）；
- 已经上线的实物/虚拟订单**不受任何影响**；
- Code Review 只需要审一个新文件。

**对照：如果父类里用了 `instanceof` 会怎样？**

```java
// ❌ 反模式
protected void notifyCustomer(OrderContext ctx) {
    String label = (this instanceof VirtualProductOrder) ? "下载链接"
                 : (this instanceof PreSaleOrder) ? "预计到货日"      // ← 新增类型被迫修改父类
                 : "物流单号";
    // ...
}
```

新增预售订单时**必须修改父类**。改父类 = 波及全部订单类型 = 全量回归测试。
而且父类会变成一个「知道所有子类」的上帝类，**违反依赖倒置原则**（高层依赖低层的细节）。

本项目用钩子解决：

```java
// ✅ 父类
protected String deliveryProofLabel() { return "物流单号"; }

// 子类各自重写，父类不认识任何子类
@Override protected String deliveryProofLabel() { return "下载链接"; }      // VirtualProductOrder
@Override protected String deliveryProofLabel() { return "预计到货日"; }    // PreSaleOrder
```

**依赖方向反过来了**：父类定义契约，子类实现契约。父类不需要知道子类是谁。

### 3.3 什么时候【不】该用模板方法模式

这一节同样重要。**模式用错场景比不用模式更糟。**

#### 不该用的情况一：步骤组合是自由的，不是固定的

如果你的「流程」其实是**用户可以自由编排的**——比如低代码平台里拖拽节点、
ETL 工具里配置管道、自动化脚本里选择步骤——那**流程本身就是数据**，
不该硬编码在父类里。

这时候该用：**命令模式**（把每步封装成对象，可以任意组合排序）、
**责任链模式**（步骤之间自由传递）、或者**工作流引擎**。

> **判断标准**：如果新增一种「步骤顺序」需要**新建一个子类**，那还能接受；
> 如果需要**改父类**，或者需要**运行时动态组装**，就不该用模板方法。

#### 不该用的情况二：只有一个实现，且可预见的将来也只会有一个

```java
public abstract class ReportGenerator {
    public final void generate() { validate(); render(); save(); }
    protected abstract void render();
    // ...
}

public class SalesReportGenerator extends ReportGenerator {   // ← 全项目唯一一个子类
    protected void render() { ... }
}
```

**只有一个子类的抽象父类，是过度设计。** 直接写一个普通类就行。
等真的出现第二种子类时再重构——那时候你才知道**哪些步骤真的不同**，
现在猜的大概率是错的（这就是 YAGNI 原则）。

#### 不该用的情况三：变化的不是「步骤」，而是「整个算法」

如果两种实现之间**几乎没有共用的步骤**，那继承来的父类是个空壳，
硬套模板方法只会增加理解成本。这时候用**策略模式**（组合优于继承）。

对比一下本项目和策略模式项目的实际差异：

| | 模板方法（本目录） | 策略模式（`../strategy/`） |
|---|---|---|
| 三个实现之间共用多少 | **共用 2 个完整步骤** + 全部流程编排 | **共用 0 行**，每个策略从头算到尾 |
| 适合的模式 | 模板方法 | 策略 |

#### 不该用的情况四：继承层次已经很深了

模板方法模式**依赖继承**，而 Java 是单继承。
如果你的类已经 `extends` 了别的框架类（比如 `HttpServlet`、`AbstractController`），
就**没有继承额度**再套一层模板了。

这时候的替代方案：**用组合**——把流程编排抽成一个独立的 `ProcessTemplate` 对象，
通过回调接口（或者 `Consumer<T>` / `Supplier<T>` lambda）注入各步骤。
Netty 的 `ChannelPipeline`、Spring WebFlux 的 `WebFilter` 链都是这个思路。

#### 一个诚实的补充：模板方法模式在现代 Java 里确实在「退潮」

这不是秘密。**继承带来的问题**（父类改动波及所有子类、单继承限制、
子类必须读父类源码才能正确重写——即「脆弱基类问题」）让很多团队更偏好组合。

**但它没有过时**，因为在特定场景下它依然是最优解：

- **框架内部**（框架作者控制父类，使用者只写子类）——Spring、Servlet、JUnit 全是这个结构；
- **步骤固定 + 资源管理复杂**的场景——`JdbcTemplate` 那种「必须保证 close」的场景；
- **需要强制约束**的场景——金融、医疗这类「顺序错了就是事故」的领域。

**结论**：在**应用层业务代码**里，优先考虑策略/组合；
在**框架层、基础设施层、强合规业务**里，模板方法依然是首选。

---

## 4. 设计推导：从四行 println 一步步推出完整设计

这一节是整个文档的核心。**不是「模式长这样，记住它」，
而是「遇到这些问题，每一步为什么只能这么解」。**

起点是原项目那 35 行代码，终点是本项目的 1780 行。
中间每一步都由一个**具体的、无法回避的问题**驱动。

---

### 4.1 起点：一个能跑但什么都证明不了的示例

```java
abstract class OrderProcessTemplate {
    public final void processOrder() {
        checkStock();
        pay();
        deliver();
        notifyCustomer();
    }
    protected void checkStock() { System.out.println("检查库存"); }
    protected abstract void pay();
    protected abstract void deliver();
    protected void notifyCustomer() { System.out.println("通知客户订单处理完成"); }
}
```

**它做对了两件事**：`final` 加上了，抽象方法留出来了。骨架是标准的。

**但它证明不了任何事**，因为它没有数据、没有分支、没有失败。
读者看完只能记住「哦，父类定四步，子类填两步」这个**形状**，
完全无法理解这个形状**为什么必须是这样**。

下面九个问题，会一步步把它逼成本项目的样子。

---

### 4.2 问题一：`final` 凭什么不可动摇？

这是最该先回答的问题，因为 `final` 是本模式的核心约束。

要让 `final` 有说服力，必须让读者看到**「如果顺序能改，会发生什么具体的坏事」**。

「顺序能改」的前提是「步骤之间有依赖」。当前代码里步骤之间**没有任何依赖**——
四个 void 方法，谁先谁后都只是打印顺序不同。

**所以我们必须先制造出依赖。** 依赖从哪来？从数据来：

- 步骤 ③ 发货需要步骤 ② 支付产生的**支付流水号**（财务对账要求）；
- 步骤 ④ 通知需要步骤 ③ 发货产生的**物流单号**（不然通知里没内容）；
- 步骤 ② 支付需要步骤 ① 库存检查的**结果**（库存为 0 就不能收钱）。

**结论：`final` 的必要性 ⟸ 步骤间的数据依赖 ⟸ 步骤必须传递数据。**

这就推出了第一个改动方向。

---

### 4.3 问题二：步骤之间怎么传数据？三种方案的取舍

要让 `checkStock()` 的结果被 `pay()` 用到，有三种写法。

#### 方案 A：用返回值

```java
public final void processOrder() {
    int stock = checkStock();
    String payNo = pay(stock);
    String proof = deliver(payNo);
    notifyCustomer(proof);
}

protected abstract String pay(int stock);
protected abstract String deliver(String payNo);
```

**看起来很干净，但有三个致命问题：**

1. **签名会失控。** 每加一步，后面所有步骤的签名都要改。
   加一个「风控评分」字段 → `deliver(String payNo, int riskScore)` →
   **所有子类的 `deliver()` 签名全变**，全部编译报错。
   这正是模板方法模式最忌讳的：**父类改动波及全部子类**。

2. **表达不了「多个产出」。** `deliver()` 除了物流单号，还可能产出承运方、预计送达时间。
   一个返回值装不下，只能返回数组或者再包一个对象——那还不如直接方案 C。

3. **表达不了「中止」。** 库存为 0 时 `checkStock()` 返回什么？返回 0？
   那 `pay(0)` 里还要再判一次。返回 `-1`？那是魔法数字。抛异常？见方案 B。

#### 方案 B：抛异常来中止流程

```java
protected int checkStock() {
    int stock = ...;
    if (stock <= 0) throw new OutOfStockException("库存不足");
    return stock;
}
```

**中止流程这件事，异常确实能表达。** 但用它来表达「库存不足」是错的：

> **异常应该表示「程序遇到了预料之外的错误」，而不是「业务上的正常分支」。**

库存不足是电商每天都会发生几万次的**正常情况**，不是错误。
用异常表达正常分支有三个坏处：

- 调用方被迫写 `try-catch`，但 catch 里能做的只是「告诉用户没货」——这就是个 `if`；
- 异常构造要**填充栈轨迹**（`fillInStackTrace()`），高频场景下开销显著；
- 更糟的是**语义混淆**：真正的程序 bug（比如 `NullPointerException`）
  和业务性的「没货」混在同一个 catch 里，排查事故时无法区分。

**本项目的选择：异常只用于「程序错误」，业务中止用状态标志。**

```java
// 程序错误 → 抛异常（这是不该发生的，发生了说明代码有 bug）
if (ctx == null) throw new IllegalArgumentException("订单上下文不能为 null");

// 业务中止 → 状态标志（这是正常情况，调用方用 if 判断）
ctx.abort("库存不足（可用 0 件），订单不予处理");
```

这个区分在 demo4-B 的输出里被明确讲了出来：

```
      >>> 注意：这里用 ctx.abort() 而不是抛异常。
          「账号填错了」是正常业务情况，不是程序错误。
          抛异常会逼调用方写 try-catch，用 if 判断更符合语义。
```

#### 方案 C：传一个上下文对象（本项目采用）

```java
public final OrderContext processOrder(OrderContext ctx) {
    checkStock(ctx);
    pay(ctx);
    deliver(ctx);
    notifyCustomer(ctx);
    return ctx;
}
```

**三个方案问题的解决情况：**

| 方案 A 的问题 | 方案 C 怎么解 |
|---|---|
| 加字段要改所有签名 | **加字段只改 `OrderContext` 一个类**，所有步骤方法签名不变 |
| 一个返回值装不下多个产出 | 上下文想加几个字段加几个 |
| 表达不了中止 | `ctx.abort(reason)` + 模板方法检查 `ctx.isAborted()` |

**方案 C 唯一的代价**：`OrderContext` 是**可变的**，数据流不再像方案 A 那样在签名上一目了然。

这个代价值得付，而且可以缓解——本项目在 `OrderContext` 里做了两件事：

```java
// ① 输入字段全部 final：流程中不可篡改
private final String orderNo;
private final String productName;
private final BigDecimal amount;

// ② 轨迹只暴露只读视图：外部无法伪造审计日志
public List<String> getTrace() {
    return Collections.unmodifiableList(trace);
}
```

demo7 实测验证了 ②：

```
  -- 轨迹是只读的 --
      [OK] trace.add() 抛 UnsupportedOperationException
           → getTrace() 返回的是 unmodifiableList 视图，
             外部无法篡改审计日志。
```

> **这个方案在真实框架里是主流做法**：
> Servlet 的 `HttpServletRequest` 贯穿整个过滤器链、
> Flowable 的 `DelegateExecution` 承载流程变量、
> Spring 的 `RequestContextHolder`。
> 都不是巧合——**「多步骤流程 + 步骤间数据依赖」这个问题，上下文对象是标准解**。

#### 一个延伸思考：为什么策略模式的 Context 是不可变的，这里却必须可变？

本项目的 `../strategy/PriceCalculator` **刻意做成不可变**（`withStrategy()` 返回新对象），
而这里的 `OrderContext` **必须可变**。同一个人写的两个 Context，为什么相反？

**因为职责不同：**

- 策略模式的 Context 只是**持有**一个策略，本身不产生任何数据 → 没有变的理由 → 不可变；
- 模板方法的 Context 是**流程产物的累积器**，每走一步就多一个字段 → 不变根本没法用 → 必须可变。

**可变性应该由职责决定，不是由个人偏好决定。**
「不可变更好」是一条经验法则，不是教条。

---

### 4.4 问题三：怎么表达「这一步可以不做」？

虚拟商品不需要检查库存。这是**问题一里那个业务错误**的根源。

有三种解法。这个对比是本模式最值得记住的一张表：

| 解法 | 写法 | 问题 |
|---|---|---|
| ① 子类把 `checkStock()` 重写成空方法 | `protected void checkStock(ctx) { }` | 空方法**看不出意图**：是「不需要检查」还是「忘了实现」？而且轨迹里没有记录，审计时无法区分 |
| ② 把 `checkStock()` 改成抽象方法 | `protected abstract void checkStock(ctx);` | 逼着**所有**子类实现一遍相同的库存查询逻辑，或者各自抄一遍——正是模式要消除的重复 |
| ③ **加一个钩子方法（本项目采用）** | `protected boolean needsStockCheck() { return true; }` | 无。意图明确、默认行为合理、子类只改一个布尔值 |

**为什么解法 ① 不行？** 看这个空方法：

```java
@Override
protected void checkStock(OrderContext ctx) {
    // 空的
}
```

三个月后接手代码的人看到这个，**无法判断**这是：
- (a) 虚拟商品不需要检查库存（正确的设计决策），还是
- (b) 上一个开发者忘了实现（bug），还是
- (c) 临时注释掉了准备回头补（技术债）。

**代码必须表达意图，而不只是表达行为。** 解法 ③ 的 `needsStockCheck()` 返回 `false`，
方法名本身就说清楚了「这是不需要，不是忘了」。

**为什么解法 ② 不行？** 因为它把「要不要做」和「怎么做」混为一谈了。

> 「查库存」这个**动作**对所有商品类型都是一样的：拿商品名去库存服务查一个数字。
> 不同的是**要不要查**，不是**怎么查**。

「怎么查」→ 复用父类的具体方法；「要不要查」→ 用钩子表达。**两件事用两种机制**，这才是正确拆分。

#### 钩子方法的定义与两种形态

**定义**：父类提供**默认实现**（返回一个默认值，或者什么都不做），
子类**可以重写也可以不重写**的方法。

**它和抽象方法的关键区别**：

| | 抽象方法 | 钩子方法 |
|---|---|---|
| 父类有实现吗 | 没有 | **有**（默认值或空） |
| 子类不实现会怎样 | **编译不过** | **正常运行**（用默认行为） |
| 语义 | 「这一步必须做，但只有你知道怎么做」 | 「这一步要不要做/怎么称呼，你有不同意见可以提」 |

**两种形态：**

**形态 1：返回值型钩子** —— 影响模板方法的**走向**或**参数**。

```java
protected boolean needsStockCheck() { return true; }        // 控制流程走向
protected String deliveryProofLabel() { return "物流单号"; }  // 控制父类逻辑的措辞
```

`deliveryProofLabel()` 这个钩子特别值得说。父类的 `notifyCustomer()` 逻辑对所有订单都一样，
只有那个「称呼」不同：

```java
protected void notifyCustomer(OrderContext ctx) {
    String content = "尊敬的客户，您的订单 " + ctx.getOrderNo()
            + "（" + ctx.getProductName() + "）已处理完成，"
            + deliveryProofLabel() + "：" + ctx.getDeliveryProof();     // ← 只有这四个字随子类变
    // ...
}
```

**为什么不把整个 `notifyCustomer()` 设为抽象方法？**
因为那样每个子类都要重写一遍**完全相同的拼接逻辑**，只有四个字不同。
**钩子让子类只需要提供那「四个字」。**

实测效果对比：

```
实物：...已处理完成，物流单号：SF20261022124832-0002
虚拟：...已处理完成，下载链接：https://dl.example.com/ord-v-001?token=VPAY20261022124832-0005
预售：...已处理完成，预计到货日：2026-11-21
```

`VirtualProductOrder` 为此付出的全部代价是：

```java
@Override
protected String deliveryProofLabel() { return "下载链接"; }
```

**形态 2：空实现型钩子** —— 提供一个**可选的扩展点**。

```java
protected void beforePay(OrderContext ctx) {
    // 默认空实现：这是一个可选的扩展点。
}
```

大多数子类不用管它。`PreSaleOrder` 重写了它来做定金校验。

---

### 4.5 问题四：钩子应该由谁调用？（一个真实的设计缺陷修复）

**这一节记录的是本项目开发过程中真实发生过的一次设计返工。**

最初 `beforePay()` 是这么用的——**每个子类在自己的 `pay()` 第一行调用它**：

```java
// ❌ 早期版本（PhysicalProductOrder / VirtualProductOrder / PreSaleOrder 都这么写）
@Override
protected void pay(OrderContext ctx) {
    beforePay(ctx);                        // ← 子类自己调钩子
    // ... 自己的支付逻辑
}
```

而且 `PreSaleOrder.pay()` 还必须写一段防御代码：

```java
@Override
protected void pay(OrderContext ctx) {
    beforePay(ctx);
    // beforePay() 里可能已经 abort 了，这时不该继续支付
    if (ctx.isAborted()) {                 // ← 被迫写的防御代码
        System.out.println("  │  ② 支付：已取消（定金校验未通过）");
        return;
    }
    // ...
}
```

**这个设计有两个问题，第二个是致命的：**

**问题 1：违反好莱坞原则。**
钩子的定义就是「**父类**在合适的时机回调子类」。
让子类自己在 `pay()` 里调 `beforePay()`，方向反了——变成「子类调用父类的流程编排」。

**问题 2（致命）：靠「每个子类都记得写一行」来保证的机制是不成立的。**

新增第四种订单类型时，如果开发者忘了写 `beforePay(ctx)`：

- 编译器**不会报错**（`pay()` 的契约里没这条）；
- 运行时**不会抛异常**；
- 单元测试**大概率也发现不了**（因为测试是针对新类型写的，写测试的人同样不知道有这个钩子）；
- 结果：**风控校验静默失效**。

**在支付场景下，「静默失效」四个字意味着真金白银的损失。**

#### 修复：把钩子调用移进模板方法

```java
// ✅ 现在的版本
// -------- 步骤 ②：支付 --------
beforePay(ctx);
if (ctx.isAborted()) {
    System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
    return ctx;
}

pay(ctx);
```

**改完之后，三个子类的 `pay()` 都变简单了**：

```java
// PreSaleOrder.pay() 现在的第一段注释
// 【注意】这里既不用调 beforePay(ctx)，也不用写 if (ctx.isAborted()) return;
// 钩子由模板方法统一调用，abort 也由模板方法统一检测——
// 定金校验失败时，本方法【根本不会被执行】。
// 这正是「把钩子调用放进模板方法」的收益：子类不需要写任何防御代码。
BigDecimal deposit = depositOf(ctx.getAmount());
```

**收益清单：**

| | 修复前 | 修复后 |
|---|---|---|
| 调用点数量 | 3 处（每个子类一处） | **1 处**（模板方法） |
| 新增子类忘了调 | 静默失效，无法发现 | **不可能忘**，父类保证 |
| 子类要不要写防御代码 | 要（`if isAborted`） | **不要** |
| 好莱坞原则 | 违反 | 遵守 |

#### 修复带来的可观测差异（真实运行输出）

修复后 demo4-C 的输出里**没有「② 支付」这一行**了：

```
  ┌─ 开始处理订单 ORD-S-002（定制马克杯，￥0.10）
  │  处理器类型：PreSaleOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  [钩子 beforePay] 定金校验失败：￥0.03 < 最低限额 ￥1
  └─ 流程中止：定金 ￥0.03 低于最低限额 ￥1，不适用预售（订单金额 ￥0.10 × 0.30）
```

对比修复前，少了一行 `│  ② 支付：已取消（定金校验未通过）`。
**这一行的消失恰恰证明 `PreSaleOrder.pay()` 根本没被执行**——
拦截发生在更早的位置，由父类完成。

> **这是一个很好的教训：钩子/回调这类「框架保证调用」的机制，
> 调用点必须只有一个，而且必须在框架手里。**
> 一旦下放到实现类，可靠性就从「编译器/框架保证」降级为「开发者自觉」。

---

### 4.6 问题五：哪些步骤该是抽象方法，哪些该是具体方法？

这是模板方法模式**最需要判断力**的地方。判错了会有两种后果：

- 该抽象的写成具体 → 子类被迫接受不适用的行为（原项目的「虚拟商品检查库存」）；
- 该具体的写成抽象 → 每个子类抄一遍相同代码（模式的价值归零）。

**判断标准（本项目实际使用的）：**

> **把三个子类在这一步的做法并排列出来，看它们有没有共同的代码主体。**
> - 有共同主体，只有细节不同 → **具体方法 + 钩子定制细节**
> - 没有共同主体，各做各的 → **抽象方法**

按这个标准逐项检查：

#### `checkStock()` → 具体方法 ✅

| 子类 | 查库存的做法 |
|---|---|
| `PhysicalProductOrder` | 拿 SKU 去库存服务查数字 |
| `VirtualProductOrder` | （不查） |
| `PreSaleOrder` | （不查） |

需要查的那些，做法**完全一样**。「要不要查」是另一个维度，用钩子表达。
→ **具体方法 + `needsStockCheck()` 钩子**

#### `notifyCustomer()` → 具体方法 ✅

| 子类 | 通知内容 |
|---|---|
| `PhysicalProductOrder` | 「您的订单 X（Y）已处理完成，**物流单号**：Z」 |
| `VirtualProductOrder` | 「您的订单 X（Y）已处理完成，**下载链接**：Z」 |
| `PreSaleOrder` | 同上 + **追加一句尾款提醒** |

**主体拼接逻辑完全相同**，只有称呼（四个字）不同。
→ **具体方法 + `deliveryProofLabel()` 钩子**

`PreSaleOrder` 要追加内容，用第三种手法：**重写具体方法并调用 `super`**。详见 [5.5 节](#55-presaleorderjava264-行四种角色全用上)。

#### `pay()` → 抽象方法 ✅

| 子类 | 支付做法 | 代码主体 |
|---|---|---|
| `PhysicalProductOrder` | 全额支付 | 生成流水号 → 写入 ctx |
| `VirtualProductOrder` | **先校验充值账号** → 全额支付 | 校验 → （可能 abort）→ 生成流水号 → 写入 ctx |
| `PreSaleOrder` | **算 30% 定金** → 支付定金 | 算定金 → 算尾款 → 生成流水号 → 写入 ctx |

后两行「生成流水号 → 写入 ctx」看着有点像共同主体，**但前面的差异是实质性的**：
一个要校验账号、一个要算比例，**父类写不出一个通用版本**。

> **能不能把「生成流水号 + 写入 ctx」抽到父类，只把差异部分留成抽象方法？**
> 理论上可以（这是「模板方法嵌套模板方法」的写法），但本项目**刻意没这么做**：
> 那样会多出一个 `doPayInternal()` 之类的中间方法，
> 而三个子类的支付逻辑总共也就 10 来行——**为了省 3 行重复而增加一层间接，不划算**。
> 取而代之，父类提供了一个 `generateSerialNo(prefix)` **工具方法**，
> 子类需要时自己调。这是更轻量的复用手段。

#### `deliver()` → 抽象方法 ✅

| 子类 | 交付物 | 特有逻辑 |
|---|---|---|
| `PhysicalProductOrder` | 物流运单号 | **称重，超 30kg 改走重货专线** |
| `VirtualProductOrder` | 下载链接 | **用支付流水号做 token 防盗链** |
| `PreSaleOrder` | 预计到货日 | 登记生产排期 |

**交付物的形态都不一样**（运单号 / URL / 日期），没有任何共同主体。→ 抽象方法

---

### 4.7 问题六：访问修饰符该怎么定？

这一节回答三个「为什么是这个修饰符」。

#### ① 类为什么是 `public abstract`？

`public` —— 见 [2.7 节](#27-问题四可见性不一致跨包时多态会失效)，原项目的包级私有父类导致跨包多态失效。

`abstract` —— 因为有抽象方法，且**「抽象模板」本身就不该被实例化**。
`new OrderProcessTemplate()` 没有任何意义：它不知道该怎么支付。

#### ② 模板方法为什么是 `public final`？

`public` —— **这是外界唯一的入口**。调用方必须能调到它。

`final` —— 见 [3.2 收益二](#收益二把必须做对的事变成无法做错的事)。**模式的全部价值建立在这个 `final` 上。**

#### ③ 步骤方法为什么是 `protected` 而不是 `public`？

```java
protected abstract void pay(OrderContext ctx);
protected void checkStock(OrderContext ctx) { ... }
```

**因为它们是流程的内部环节，不是对外 API。** 如果声明成 `public`：

```java
OrderProcessTemplate order = new PhysicalProductOrder();
order.deliver(ctx);      // ← 绕过库存检查和支付，直接发货！
```

这等于把精心设计的流程约束全部废掉了。

`protected` 的语义正好：**子类能看到（必须能重写），外部包看不到**。

#### ④ 但 `protected` 有个真实的漏洞，必须诚实说明

**Java 的 `protected` = 同包可见 + 子类可见。** 没有「只给子类、不给同包」这种更严格的选项。

所以本项目 demo6 里那段反例**能跑起来**：

```java
// Client 和订单类在同一个包 com.patterns.behavioral.templatemethod 里
PhysicalProductOrder processor = new PhysicalProductOrder();
processor.deliver(ctx);      // ✅ 编译通过！因为同包可见
processor.pay(ctx);          // ✅ 编译通过！
```

**这不是设计的失败，而是语言的固有限制。** 生产项目的三种应对：

| 方案 | 做法 | 强度 |
|---|---|---|
| ① 分包 | 把调用方放在**不同的包**，`protected` 就真的访问不到了 | 中（约定） |
| ② JPMS | 用模块系统，只 `exports` 需要暴露的包 | 强（运行时强制） |
| ③ ArchUnit | 写架构测试，禁止跨层调用步骤方法 | 强（CI 拦截） |

本项目为了能演示反例，**刻意把 Client 和订单类放在同一个包里**。
这个取舍在 demo6 的输出里被明确告知了读者：

```
  -- 一个诚实的说明：protected 在同包内是可以被绕过的 --
      上面这段反例能跑起来，正是因为 Client 和订单类在同一个包里。
      Java 的 protected = 【同包可见 + 子类可见】，没有更严格的选项。
      生产项目里的应对方式：
        ① 把 Client（调用方）放在【不同的包】，protected 就真的访问不到了；
        ② 用模块系统（JPMS）只 exports 需要暴露的包；
        ③ 用 ArchUnit 写架构测试，禁止跨层调用步骤方法。
      本项目为了能演示反例，刻意把它们放在同一个包里。
```

#### ⑤ `generateSerialNo()` 为什么是 `protected final`？

```java
protected final String generateSerialNo(String prefix) { ... }
```

`protected` —— 给子类用的工具方法，外部不需要。

`final` —— **流水号格式必须全公司统一**（对账系统的解析规则依赖它）。
如果某个子类重写了它、返回一个别的格式，财务的对账脚本就会崩。

> 注意这个 `final` 和模板方法的 `final` **理由不同**：
> 模板方法 `final` 是为了保护**流程顺序**；
> 工具方法 `final` 是为了保护**输出格式契约**。
> 两个都是「不允许子类改变的东西」，但保护的对象不一样。

---

### 4.8 问题七：为什么要记录流程轨迹（trace）？

`OrderContext` 里有一个 `List<String> trace` 字段，每一步都往里加一条记录。

**这个字段不属于 GoF 定义的模板方法模式。** 加它有三个理由：

**理由 1：它让「父类控制流程」这件事变得可以验证。**

运行结束后打印轨迹，能一眼看出：哪些步骤执行了、哪些被钩子跳过了、顺序对不对、
每一步是父类实现的还是子类重写的。

demo7 的真实输出：

```
  -- 这笔订单的完整轨迹 --
      1. 流程启动，处理器 = PhysicalProductOrder
      2. ① 检查库存 → 可用 7 件
      3. ② 支付 → 全额 ￥1299.00，流水号 PAY20261022125133-0013
      4. ③ 发货 → 已交付顺丰，运单号 SF20261022125133-0014（重量 12.0kg，关联支付流水 PAY20261022125133-0013）
      5. ④ 通知客户 → 已发送（凭证类型：物流单号）
      6. 流程结束
```

对照虚拟商品的轨迹，**第 2 条完全不同**：

```
      2. 跳过库存检查（VirtualProductOrder.needsStockCheck() = false）
```

**理由 2：它演示了「跳过」和「通过」的区别。**

```java
} else {
    // 【为什么要记录"跳过"而不是什么都不做？】
    // 因为审计时必须能区分"检查通过了"和"根本没检查"，
    // 这两者在出问题时的责任归属完全不同。
    ctx.record("跳过库存检查（" + getClass().getSimpleName() + ".needsStockCheck() = false）");
}
```

回到第 1 节的报销类比：「主管审批通过」和「小额免审」，出了贪污案时责任归属天差地别。

**理由 3：真实系统里这就是操作日志表 / 链路追踪 span。**

任何涉及资金的流程都必须可审计。这不是过度设计，是合规要求。

---

### 4.9 最终结构：九个决策的汇总

```
                        ┌─────────────────────────────────────┐
                        │   OrderProcessTemplate (public abstract)  │
                        ├─────────────────────────────────────┤
   【角色 1 模板方法】    │ + processOrder(ctx) : OrderContext  │ ← public final
                        ├─────────────────────────────────────┤
   【角色 2 抽象方法】    │ # pay(ctx) : void                   │ ← protected abstract
                        │ # deliver(ctx) : void               │ ← protected abstract
                        ├─────────────────────────────────────┤
   【角色 3 具体方法】    │ # checkStock(ctx) : void            │ ← protected（可重写）
                        │ # notifyCustomer(ctx) : void        │ ← protected（可重写）
                        ├─────────────────────────────────────┤
   【角色 4 钩子方法】    │ # needsStockCheck() : boolean       │ ← 默认 true
                        │ # deliveryProofLabel() : String     │ ← 默认「物流单号」
                        │ # beforePay(ctx) : void             │ ← 默认空实现
                        ├─────────────────────────────────────┤
   【工具方法】          │ # generateSerialNo(pfx) : String    │ ← protected final
                        └───────────────┬─────────────────────┘
                                        │ extends
              ┌─────────────────────────┼─────────────────────────┐
              │                         │                         │
   ┌──────────▼───────────┐  ┌──────────▼───────────┐  ┌──────────▼───────────┐
   │ PhysicalProductOrder │  │ VirtualProductOrder  │  │    PreSaleOrder      │
   ├──────────────────────┤  ├──────────────────────┤  ├──────────────────────┤
   │ 实现 pay()   全额    │  │ 实现 pay()  校验账号 │  │ 实现 pay()  30%定金  │
   │ 实现 deliver() 称重  │  │ 实现 deliver() 防盗链│  │ 实现 deliver() 到货日│
   │                      │  │ 重写 needsStockCheck │  │ 重写 needsStockCheck │
   │ （三个钩子全不重写）  │  │   → false            │  │   → false            │
   │                      │  │ 重写 deliveryProof   │  │ 重写 deliveryProof   │
   │                      │  │   Label → 下载链接   │  │   Label → 预计到货日 │
   │                      │  │                      │  │ 重写 beforePay       │
   │                      │  │                      │  │   → 定金最低限额校验 │
   │                      │  │                      │  │ 重写 notifyCustomer  │
   │                      │  │                      │  │   → super + 尾款提醒 │
   └──────────────────────┘  └──────────────────────┘  └──────────────────────┘
              └─────────────────────────┴─────────────────────────┘
                                        │ 都读写
                        ┌───────────────▼─────────────────────┐
                        │        OrderContext (public)        │
                        ├─────────────────────────────────────┤
   【输入：final】       │ - orderNo, productName, amount     │
   【产出：可变】        │ - availableStock, paymentNo        │
                        │ - deliveryProof, notificationContent│
   【控制】              │ - aborted, abortReason             │
   【审计】              │ - trace : List<String>（只读视图）  │
                        └─────────────────────────────────────┘
```

**九个设计决策一览：**

| # | 决策 | 驱动力 | 章节 |
|---|---|---|---|
| 1 | 模板方法加 `final` | 保护步骤顺序，让「先发货后收款」编译不过 | 4.2、4.7 |
| 2 | 引入 `OrderContext` 传数据 | 制造步骤间依赖，让 `final` 的必要性可验证 | 4.3 |
| 3 | 业务中止用 `abort()` 不用异常 | 库存不足是正常分支，不是程序错误 | 4.3 |
| 4 | 加 `needsStockCheck()` 钩子 | 虚拟商品被迫「检查库存」是业务错误 | 4.4 |
| 5 | 加 `deliveryProofLabel()` 钩子 | 通知主体逻辑相同、只有称呼不同 | 4.4 |
| 6 | `beforePay()` 由模板方法调用 | 子类自己调会静默失效（真实返工） | 4.5 |
| 7 | `pay`/`deliver` 抽象、`checkStock`/`notifyCustomer` 具体 | 看三个子类有没有共同代码主体 | 4.6 |
| 8 | 全部类改 `public` | 包级私有父类导致跨包多态失效 | 2.7、4.7 |
| 9 | 加 `trace` 审计轨迹 | 区分「跳过」和「通过」，满足合规 | 4.8 |

**每一个决策都对应一个具体的问题，没有一个是「因为书上这么写」。**

---

## 5. 代码解析：六个文件各自为什么存在

### 5.0 文件清单

```
src/com/patterns/behavioral/templatemethod/
├── OrderContext.java             267 行   流程数据载体（GoF 未定义，工程必备）
├── OrderProcessTemplate.java     526 行   抽象模板（模式核心）
├── PhysicalProductOrder.java     143 行   具体子类 ①：实物商品
├── VirtualProductOrder.java      207 行   具体子类 ②：虚拟商品
├── PreSaleOrder.java             264 行   具体子类 ③：预售（新增，验证开闭原则）
├── Client.java                   373 行   七段演示
└── README.md                     本文档
                          合计 1780 行 Java
```

---

### 5.1 `OrderContext.java`（267 行）：流程的数据载体

#### 它为什么必须存在

见 [4.3 节](#43-问题二步骤之间怎么传数据三种方案的取舍)。一句话总结：

> **没有它，步骤之间就没有数据依赖；没有数据依赖，`final` 就无法论证；
> `final` 无法论证，模板方法模式的核心约束就成了一句话而已。**

#### 字段设计：三组，各有各的可变性策略

```java
public class OrderContext {

    // ========== 第一组：输入字段（final，流程中不可变） ==========
    private final String orderNo;
    private final String productName;
    private final BigDecimal amount;

    // ========== 第二组：产出字段（由各步骤依次写入） ==========
    private int availableStock = -1;        // ① 的产出
    private String paymentNo;               // ② 的产出
    private String deliveryProof;           // ③ 的产出
    private String notificationContent;     // ④ 的产出

    // ========== 第三组：控制与审计 ==========
    private boolean aborted;
    private String abortReason;
    private final List<String> trace = new ArrayList<>();
}
```

**为什么 `availableStock` 的初始值是 `-1` 而不是 `0`？**

```java
/** ① 检查库存的产出：可用库存数量。{@code -1} 表示「该商品不需要检查库存」。 */
private int availableStock = -1;
```

因为 `0` 有明确的业务含义——**「查过了，库存是 0」**，这会触发中止。
而虚拟商品**压根没查**。两种情况必须能区分开，所以用一个不可能出现的业务值 `-1` 做哨兵。

> **这是一个通用技巧：`int` 类型的「未初始化」和「值为 0」必须用不同的表示。**
> 更严格的做法是用 `Integer`（`null` 表示未设置），本项目为了简单用了 `-1` 哨兵，
> 但**必须在 Javadoc 里写清楚**——否则 `-1` 就是个魔法数字。

#### 构造器：把非法状态挡在门外

```java
public OrderContext(String orderNo, String productName, BigDecimal amount) {
    if (orderNo == null || orderNo.isBlank()) {
        throw new IllegalArgumentException("订单号不能为空");
    }
    if (productName == null || productName.isBlank()) {
        throw new IllegalArgumentException("商品名称不能为空");
    }
    if (amount == null || amount.signum() < 0) {
        throw new IllegalArgumentException("订单金额不能为 null 或负数，实际传入：" + amount);
    }
    this.orderNo = orderNo;
    this.productName = productName;
    this.amount = amount;
}
```

**三条校验，每条都有具体的下游依赖：**

| 校验 | 谁依赖它 |
|---|---|
| `orderNo` 非空 | `VirtualProductOrder.deliver()` 里 `ctx.getOrderNo().toLowerCase()`，null 会 NPE |
| `productName` 非空 | `checkStock()` 里 `ctx.getProductName().length()`，null 会 NPE |
| `amount` 非 null 非负 | `PreSaleOrder.depositOf()` 里 `amount.multiply(...)`，null 会 NPE |

**这就是「快速失败」（fail fast）**：在数据进入流程之前拦住，
而不是让 null 流到第三步才炸出一个看不出根因的 NPE。

**为什么这里用异常而不是 `abort()`？** 因为「传了 null 订单号」是**调用方的编码错误**，
不是业务上的正常情况。参见 [4.3 方案 B](#方案-b抛异常来中止流程) 的区分标准。

#### `getTrace()`：返回内部集合必须包一层

```java
/**
 * 直接返回 {@code trace} 字段的话，外部可以 {@code clear()} 掉整条轨迹，
 * 审计日志就没了。返回内部集合时一定要包一层 unmodifiable 视图。
 */
public List<String> getTrace() {
    return Collections.unmodifiableList(trace);
}
```

**这是 Java 里最经典的封装漏洞之一。** 反面教材：

```java
// ❌ 这样写，外部可以随便改
public List<String> getTrace() { return trace; }

// 调用方
List<String> t = ctx.getTrace();
t.clear();                        // 审计日志没了，而且没有任何报错
t.add("伪造一条：主管已审批");      // 可以伪造记录
```

demo7 实测验证了封装有效：

```
  -- 轨迹是只读的 --
      [OK] trace.add() 抛 UnsupportedOperationException
           → getTrace() 返回的是 unmodifiableList 视图，
             外部无法篡改审计日志。
```

> **`unmodifiableList` 是「视图」不是「副本」**——它包着原来的 list，
> 原 list 后续新增的内容，通过视图**能看到**。
> 这正好符合需求：流程还在跑，调用方随时能读到最新轨迹。
> 如果要「快照」语义（拿到之后不再变化），得用 `List.copyOf(trace)`。
> **两者的区别是真实存在的，选错会导致读到过期数据或者意外的实时数据。**

#### `abort()`：一次调用做三件事

```java
public void abort(String reason) {
    this.aborted = true;
    this.abortReason = reason;
    record("【中止】" + reason);       // ← 关键：中止本身也要进轨迹
}
```

**为什么 `abort()` 内部要自动 `record()`？**
因为「流程为什么停了」是审计时**最重要**的一条信息。
如果让调用方自己记得写 `record()`，就会出现「中止了但轨迹里没记」的情况——
和 [4.5 节](#45-问题四钩子应该由谁调用一个真实的设计缺陷修复) 的钩子问题是**完全一样的错误模式**：靠自觉的机制必然失效。

#### 删掉这个类会怎样？

退回到原项目的状态：四步全 void、无法短路、无法审计、`final` 无法论证。
**这是本项目改动量最大、收益也最大的一个新增文件。**

---

### 5.2 `OrderProcessTemplate.java`（526 行）：模式的核心

526 行里 **362 行是 Javadoc、57 行是行内注释，只有 79 行是可执行代码**。
这个比例是有意的——**这个类的价值一半在代码，一半在「为什么这么写」的说明里**。

#### 5.2.1 【角色 1】模板方法：`processOrder()`

这是全项目最重要的一个方法。完整源码：

```java
public final OrderContext processOrder(OrderContext ctx) {
    if (ctx == null) {
        throw new IllegalArgumentException("订单上下文不能为 null");
    }

    System.out.println("  ┌─ 开始处理订单 " + ctx.getOrderNo()
            + "（" + ctx.getProductName() + "，￥" + ctx.getAmount().toPlainString() + "）");
    System.out.println("  │  处理器类型：" + getClass().getSimpleName());
    ctx.record("流程启动，处理器 = " + getClass().getSimpleName());

    // -------- 步骤 ①：库存检查（钩子控制是否执行） --------
    if (needsStockCheck()) {
        checkStock(ctx);
        if (ctx.getAvailableStock() <= 0) {
            ctx.abort("库存不足（可用 " + ctx.getAvailableStock() + " 件），订单不予处理");
            System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
            return ctx;
        }
    } else {
        ctx.record("跳过库存检查（" + getClass().getSimpleName() + ".needsStockCheck() = false）");
        System.out.println("  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①");
    }

    // -------- 步骤 ②：支付 --------
    beforePay(ctx);                       // 钩子由模板方法调用，见 4.5
    if (ctx.isAborted()) {
        System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
        return ctx;
    }

    pay(ctx);

    // 检查 1：子类是否已经主动中止了流程？
    if (ctx.isAborted()) {
        System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
        return ctx;
    }

    // 检查 2：流程没中止，但也没写入流水号 → 子类的实现有缺陷
    if (ctx.getPaymentNo() == null) {
        ctx.abort("支付步骤未产生流水号，疑似子类实现缺陷（忘了调 ctx.setPaymentNo）");
        System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
        return ctx;
    }

    // -------- 步骤 ③：发货 --------
    deliver(ctx);

    // -------- 步骤 ④：通知客户 --------
    notifyCustomer(ctx);

    System.out.println("  └─ 订单处理完成 √");
    ctx.record("流程结束");
    return ctx;
}
```

**逐段拆解它的职责：**

| 代码段 | 职责 | 删掉会怎样 |
|---|---|---|
| `if (ctx == null) throw` | 入口防御 | 后面第一行 `ctx.getOrderNo()` 就 NPE，栈里看不出是调用方的错 |
| `getClass().getSimpleName()` | 打印真实处理器类型 | 无法从输出判断走的是哪个子类 |
| `if (needsStockCheck())` | **钩子判断**（不是业务 if-else） | 虚拟商品被迫检查库存 |
| `if (availableStock <= 0) abort+return` | **失败短路** | 超卖事故 |
| `else { record("跳过...") }` | 记录「没做」而非「做了」 | 审计时无法区分免检和通过 |
| `beforePay(ctx)` + `isAborted` | 钩子调用点 + 中止检测 | 见 4.5，会静默失效 |
| `pay(ctx)` | 调用抽象方法 | — |
| **检查 1**：`isAborted()` | 保留子类的业务中止原因 | **会覆盖真实原因**（真实 bug，见 5.2.2） |
| **检查 2**：`paymentNo == null` | 暴露子类的编码缺陷 | null 流到 `deliver()`，产生「无流水号的发货记录」 |
| `deliver(ctx)` | 调用抽象方法 | — |
| `notifyCustomer(ctx)` | 调用具体方法 | — |
| `return ctx` | 让调用方读到全部产出 | 退化成原项目的 void |

#### 5.2.2 【一个真实修复的 bug】两个检查的顺序不能调换

**这个 bug 是本项目实测时真实出现的，不是设想出来的。**

看源码里那段注释（原文照录）：

```java
// 检查 1：子类是否已经主动中止了流程？
// 【为什么这个检查必须放在 paymentNo 判空之前？】
// 因为子类可能因为业务原因 abort（例如充值账号格式非法），
// 此时 paymentNo 必然也是 null。如果先判 paymentNo，
// 就会用「疑似子类实现缺陷」把子类给出的【真实业务原因】覆盖掉，
// 排查事故时看到的是错误信息。这个 bug 在本项目实测时真的出现过。
if (ctx.isAborted()) { ... return ctx; }
```

**bug 现场**：早期版本的顺序是反的（先判 `paymentNo == null`）。
跑 demo4-B（虚拟商品充值账号非法）时，输出是这样的：

```
  │  ② 支付：账号校验失败 → 12
  └─ 流程中止：支付步骤未产生流水号，疑似子类实现缺陷（忘了调 ctx.setPaymentNo）
```

**这个中止原因是错的。** 子类明明给出了准确的业务原因「充值账号格式非法：12」，
却被父类的防御性检查覆盖了。

**为什么这是严重问题？** 想象生产环境：
运营看到告警「疑似子类实现缺陷」，会去查代码、找开发、翻提交记录——
而真实原因只是**用户把手机号填错了**。排查方向完全跑偏，可能耗掉几个小时。

**修复后的输出**（demo4-B 实测）：

```
  ┌─ 开始处理订单 ORD-V-002（话费充值@12，￥100.00）
  │  处理器类型：VirtualProductOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  ② 支付：账号校验失败 → 12
  └─ 流程中止：充值账号格式非法：12
      isAborted = true
      中止原因  = 充值账号格式非法：12
      下载链接  = null   ← 【null】没走到交付步骤
```

> **通用教训：防御性检查的顺序，必须按「原因的具体程度」从具体到笼统排列。**
>
> 「子类主动 abort」是**具体的、有业务语义的**原因；
> 「paymentNo 是 null」是**笼统的、推测性的**原因（它只能说明「不对劲」，说不出为什么）。
> **让笼统的推测覆盖具体的事实，是所有日志/错误处理设计里最典型的错误。**
>
> 这个规律在异常链里也一样：`catch` 的顺序必须从最具体的异常类型到最笼统的 `Exception`，
> 反了的话具体的 catch 永远进不去（Java 编译器会直接报 `exception has already been caught`）。

#### 5.2.3 【角色 2】抽象方法：`pay()` 与 `deliver()`

```java
protected abstract void pay(OrderContext ctx);
protected abstract void deliver(OrderContext ctx);
```

**这两个方法虽然各只有一行，但 Javadoc 各写了 20 多行——因为它们是「契约」。**

`pay()` 的实现契约（摘自源码 Javadoc）：

```
1. 必须调用 ctx.setPaymentNo(...) 写入支付流水号。
   不写的话模板方法会检测到并中止流程（见 processOrder）。
2. 应该调用 ctx.record(...) 记录本步骤做了什么。
3. 如果支付失败，应该调用 ctx.abort(reason)，
   而不是抛异常——「支付失败」是正常业务情况，不是程序错误。
```

`deliver()` 的实现契约：

```
1. 必须调用 ctx.setDeliveryProof(...) 写入交付凭证；
2. 可以使用 ctx.getPaymentNo()——它此时【保证非 null】，
   因为模板方法已经检查过了。这个保证来自「顺序不可调换」，
   如果子类能改顺序（即模板方法不是 final），这个保证就不成立了。
```

**第 2 条是整个设计里最精妙的一处**：

> `deliver()` 里可以放心地用 `ctx.getPaymentNo()`，**不用做 null 检查**。
> 这个「可以放心」的保证，是由 `final` 的模板方法 + 检查 2 共同提供的。
>
> **契约不是靠文档约定的，是靠代码结构强制的。**

这就是为什么 `PhysicalProductOrder.deliver()` 里可以直接写：

```java
ctx.record("③ 发货 → 已交付" + carrier + "，运单号 " + trackingNo
        + "（重量 " + weight + "kg，关联支付流水 " + ctx.getPaymentNo() + "）");
```

没有任何防御性判空——**因为父类已经保证了**。

#### 5.2.4 【角色 3】具体方法：`checkStock()` 与 `notifyCustomer()`

```java
protected void checkStock(OrderContext ctx) {
    // 模拟库存查询：商品名越长代表越冷门、库存越少（纯粹为了 Demo 可复现）
    int stock = Math.max(0, ctx.getProductName().length() - 3) * 7;
    ctx.setAvailableStock(stock);
    ctx.record("① 检查库存 → 可用 " + stock + " 件");
    System.out.println("  │  ① 检查库存：可用 " + stock + " 件"
            + (stock <= 0 ? "  ← 不足！" : ""));
}
```

**那个 `(length - 3) * 7` 的公式是刻意设计的**，目的是让「库存不足」这条分支
**可以被真实触发**，而不是永远走不到。下面每个数字都来自真实运行输出：

| 商品名 | 字数 | `Math.max(0, len-3) * 7` | 结果 | 出现在 |
|---|---:|---:|---|---|
| 杯子 | 2 | `max(0,-1) * 7 = 0` | **库存 0 → 中止** | demo4-A |
| 机械键盘 | 4 | `1 * 7 = 7` | 库存 7 → 通过 | demo1、demo6 |
| 无线鼠标 | 4 | `1 * 7 = 7` | 库存 7 → 通过 | demo5 |
| 智能手表 | 4 | `1 * 7 = 7` | 库存 7 → 通过 | demo7 |
| 对开门大容量冰箱洗衣机组合套装 | 15 | `12 * 7 = 84` | 库存 84 → 通过 | demo1 |

> **一个 Demo 设计原则**：如果你写了一个 `if (失败) { 处理失败 }` 分支，
> **就必须构造一个能真实走进这个分支的测试用例**。
> 写不出来的分支，等于没写——而且很可能藏着 bug（因为你从没执行过它）。

```java
protected void notifyCustomer(OrderContext ctx) {
    String content = "尊敬的客户，您的订单 " + ctx.getOrderNo()
            + "（" + ctx.getProductName() + "）已处理完成，"
            + deliveryProofLabel() + "：" + ctx.getDeliveryProof();

    ctx.setNotificationContent(content);
    ctx.record("④ 通知客户 → 已发送（凭证类型：" + deliveryProofLabel() + "）");
    System.out.println("  │  ④ 通知客户：" + content);
}
```

**注意这个方法内部调用了钩子 `deliveryProofLabel()`**——
「具体方法内部调用钩子」是模板方法模式的**常见组合拳**：
逻辑主体复用父类，只有措辞这种细节由子类定制。

三种订单的实测通知文案（只有粗体部分不同）：

```
实物：...已处理完成，**物流单号**：SF20261022125133-0002
虚拟：...已处理完成，**下载链接**：https://dl.example.com/ord-v-001?token=VPAY20261022125133-0005
预售：...已处理完成，**预计到货日**：2026-11-21
```

#### 5.2.5 【角色 4】三个钩子方法

```java
protected boolean needsStockCheck() { return true; }         // 形态 1：控制流程走向
protected String deliveryProofLabel() { return "物流单号"; }   // 形态 1：定制父类逻辑的参数
protected void beforePay(OrderContext ctx) { /* 空 */ }       // 形态 2：可选扩展点
```

设计理由全部在 [4.4](#44-问题三怎么表达这一步可以不做) 和 [4.5](#45-问题四钩子应该由谁调用一个真实的设计缺陷修复) 两节。

**三个钩子各自被谁重写（实测统计）：**

| 钩子 | `PhysicalProductOrder` | `VirtualProductOrder` | `PreSaleOrder` |
|---|:---:|:---:|:---:|
| `needsStockCheck()` | 不重写（`true`） | **重写 → `false`** | **重写 → `false`** |
| `deliveryProofLabel()` | 不重写（物流单号） | **重写 → 下载链接** | **重写 → 预计到货日** |
| `beforePay()` | 不重写（空） | 不重写（空） | **重写 → 定金校验** |

**`PhysicalProductOrder` 一个钩子都不重写**——它是「最标准」的订单类型，
所以写的代码最少（37 行）。**这正是模板方法模式的收益分布规律：
越常见的情况，代码越少；越特殊的情况，代码越多。**

#### 5.2.6 工具方法：`generateSerialNo()`

```java
protected final String generateSerialNo(String prefix) {
    // synchronized 保证并发下序号不重复。
    // 【为什么这里要考虑线程安全？】因为真实系统里订单处理必然是多线程的，
    // 静态的 sequence 字段会被并发访问。Demo 虽然是单线程，
    // 但写出线程不安全的示例代码会给读者错误的示范。
    synchronized (OrderProcessTemplate.class) {
        sequence++;
        return prefix + LocalDateTime.now().format(TS)
                + "-" + String.format("%04d", sequence);
    }
}
```

**这不是模式的一部分，是工程便利。** 但它体现了一个原则：

> **父类除了定义「骨架」，通常还会提供一批「给子类用的工具方法」。**
> 这些方法解决的是所有子类都会遇到的共性问题（生成 ID、格式化金额、获取配置……）。
> 把它们放父类，既消除重复，又保证全公司格式统一。

四种前缀对应四种业务，实测输出可以逐个核对（同一次运行内序号连续递增）：

| 前缀 | 使用者 | 实测样例 |
|---|---|---|
| `PAY` | `PhysicalProductOrder.pay()` | `PAY20261022125133-0001` |
| `SF` | `PhysicalProductOrder.deliver()` | `SF20261022125133-0002` |
| `VPAY` | `VirtualProductOrder.pay()` | `VPAY20261022125133-0005` |
| `PRE` | `PreSaleOrder.pay()` | `PRE20261022125133-0006` |

#### 5.2.7 如果删掉这个类会怎样？

`OrderProcessTemplate` 一旦被删，三个子类**没有任何共同父类**，
`List<OrderProcessTemplate>` 写不出来，demo5 的批处理循环写不出来，
流程顺序、失败短路、审计轨迹全部消失——**整个模式不复存在**。

**这个类是模式的载体，其他五个文件都围绕它。**

---

### 5.3 `PhysicalProductOrder.java`（143 行）：最省事的子类

#### 定位

它承担两个教学任务：

1. **展示「标准情况」的代码量有多小**——37 行可执行代码，一个钩子都不重写；
2. **展示子类应该装什么样的逻辑**——「超重改走重货专线」这个规则**只属于实物商品**。

#### `pay()`：全额支付

```java
@Override
protected void pay(OrderContext ctx) {
    // 【注意】这里不需要调 beforePay(ctx)——钩子由模板方法统一调用。
    // 本类没重写 beforePay()，所以那一步对实物商品而言是空操作，
    // 但流程依然会走到它——【这就是"扩展点"的意义：位置预留好了，用时才填】。

    // 全额支付：实物商品没有定金/尾款的概念
    BigDecimal paid = ctx.getAmount().setScale(2, RoundingMode.HALF_UP);
    String paymentNo = generateSerialNo("PAY");

    ctx.setPaymentNo(paymentNo);
    ctx.record("② 支付 → 全额 ￥" + paid.toPlainString() + "，流水号 " + paymentNo);

    System.out.println("  │  ② 支付：全额 ￥" + paid.toPlainString()
            + "，流水号 " + paymentNo);
}
```

**为什么 `setScale(2, HALF_UP)` 是必要的？**
`ctx.getAmount()` 的 scale 是不确定的——调用方可能传 `new BigDecimal("399")`（scale=0），
也可能传 `new BigDecimal("399.0")`（scale=1）。**统一成 2 位小数是金额输出的基本要求。**

**为什么用 `toPlainString()` 而不是 `toString()`？**
`BigDecimal.toString()` 在某些情况下会输出科学计数法（例如 `1E+2`）。
**金额永远不能用科学计数法显示**，所以一律用 `toPlainString()`。

#### `deliver()`：称重 + 选承运方

```java
@Override
protected void deliver(OrderContext ctx) {
    // 实物商品特有的校验：超重不能走普通快递。
    // 【为什么这个校验放在子类而不是父类？】
    // 因为虚拟商品没有重量、预售商品发货时才知道重量，
    // 这是"实物商品"这一类型独有的规则，放进父类会污染其他类型。
    BigDecimal weight = estimateWeight(ctx.getProductName());
    boolean overweight = weight.compareTo(MAX_PARCEL_WEIGHT) > 0;
    String carrier = overweight ? "重货物流专线" : "顺丰";

    // 使用上一步产出的支付流水号（体现步骤间的数据依赖）
    String trackingNo = generateSerialNo("SF");
    ctx.setDeliveryProof(trackingNo);

    ctx.record("③ 发货 → 已交付" + carrier + "，运单号 " + trackingNo
            + "（重量 " + weight + "kg，关联支付流水 " + ctx.getPaymentNo() + "）");

    System.out.println("  │  ③ 发货：已交付" + carrier + "，运单号 " + trackingNo);
    if (overweight) {
        System.out.println("  │        └ 重量 " + weight + "kg 超过 "
                + MAX_PARCEL_WEIGHT + "kg 阈值，普通快递不收，改走专线");
    }
    System.out.println("  │        └ 关联支付流水 " + ctx.getPaymentNo()
            + "  ← 这个值来自步骤 ②，所以顺序不能调换");
}
```

**`weight.compareTo(MAX_PARCEL_WEIGHT) > 0` 而不是 `weight.doubleValue() > 30`：**

`BigDecimal` 比较**必须用 `compareTo`**。原因是 `equals()` 会比较 scale：

```java
new BigDecimal("30.0").equals(new BigDecimal("30.00"))      // false！scale 不同
new BigDecimal("30.0").compareTo(new BigDecimal("30.00"))   // 0，相等
```

**这是金额计算里最常见的坑之一。** `compareTo` 只比数值，不比精度。

两条分支的实测输出（demo1，同一个处理器处理两笔订单）：

```
12.0 kg（不超重）：
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0002
  │        └ 关联支付流水 PAY20261022125133-0001  ← 这个值来自步骤 ②，所以顺序不能调换

45.0 kg（超重）：
  │  ③ 发货：已交付重货物流专线，运单号 SF20261022125133-0004
  │        └ 重量 45.0kg 超过 30kg 阈值，普通快递不收，改走专线
  │        └ 关联支付流水 PAY20261022125133-0003  ← 这个值来自步骤 ②，所以顺序不能调换
```

#### `estimateWeight()`：一个刻意的模拟设计

```java
private BigDecimal estimateWeight(String productName) {
    return BigDecimal.valueOf(productName.length())
            .multiply(new BigDecimal("3"))
            .setScale(1, RoundingMode.HALF_UP);
}
```

**为什么不用随机数或者写死一个值？**

| 方案 | 问题 |
|---|---|
| 写死一个值 | 只能演示一条分支，另一条永远走不到 |
| 随机数 | 每次运行结果不同，**文档里的输出就没法和运行结果对照** |
| **用商品名长度（本项目）** | **确定性的、可复现的、能通过换商品名精确控制走哪条分支** |

> **Demo 代码的可复现性是一种严肃的质量要求。**
> 如果读者照着文档跑一遍得到的输出和文档不一样，文档就失去了全部价值。

#### 一个曾经的文档-代码不一致（已修复）

**这个类在开发过程中出过一次「Javadoc 和代码不符」的问题，记录在这里作为教训。**

早期版本的 Javadoc 写着「发货时校验**重量和地址**」，但代码里只校验了重量——
**根本没有地址校验这回事**。

同时 `estimateWeight()` 的公式是 `length`（1 字 = 1 kg），而阈值是 30 kg，
意味着**商品名要 30 个字才能触发超重分支**。Javadoc 里却写着「商品名超过 10 个字就会触发」。

两处都是文档在撒谎。修复方式：

| 问题 | 修复 |
|---|---|
| Javadoc 说校验「重量和地址」 | 改成「校验包裹重量、超重改走物流专线」，只描述真实存在的行为 |
| 公式 `length` 导致分支难以触发 | 改成 `length × 3`，让 15 字的商品名（45 kg）能真实触发 |
| Javadoc 说「10 个字触发」 | 改成列出两个**具体商品名和具体计算结果**，可直接核对 |
| Client 里缺少 Javadoc 承诺的第二个用例 | demo1 补上 `ORD-P-003 对开门大容量冰箱洗衣机组合套装` |

> **教训：Javadoc 描述的行为，必须能在代码里逐条找到对应实现。**
> 文档比代码更容易过时，因为它**不参与编译、不参与测试**。
> 写「校验重量和地址」这种话的成本是零，但它会让下一个读代码的人多花半小时找一个不存在的东西。

---

### 5.4 `VirtualProductOrder.java`（207 行）：重写钩子的示范

#### 定位

它回答一个问题：**「当某个步骤不适用于我时，怎么优雅地跳过它？」**

答案是：**重写钩子，而不是重写步骤方法。**

#### 两个钩子重写

```java
/**
 * <p><b>注意：重写钩子不需要调用 {@code super.needsStockCheck()}</b>——
 * 钩子的语义是「替换默认答案」，不是「在默认答案基础上追加」。
 * 这和 {@code notifyCustomer()} 那种「具体方法」不同（那种往往需要调 super 复用主体逻辑）。
 */
@Override
protected boolean needsStockCheck() {
    return false;
}

@Override
protected String deliveryProofLabel() {
    return "下载链接";
}
```

**「重写钩子不调 super，重写具体方法要调 super」**——这条区别很重要：

| 方法类型 | 要不要调 `super` | 原因 |
|---|---|---|
| 钩子（返回值型） | **不要** | 语义是「替换默认答案」，`return super.needsStockCheck()` 等于没重写 |
| 钩子（空实现型） | **不要** | 父类是空的，调了什么都不发生 |
| 具体方法 | **通常要** | 父类有实质逻辑，不调就是把主体功能整个丢掉 |

#### `pay()`：账号校验

```java
@Override
protected void pay(OrderContext ctx) {
    // 虚拟商品特有的前置校验：账号格式。
    // 【为什么放在子类而不是父类？】因为实物商品根本没有"充值账号"这个概念，
    // 放进父类会让 PhysicalProductOrder 也被迫处理一个它不关心的字段。
    // 【为什么不用 beforePay() 钩子？】因为这段校验是虚拟商品【自己】的支付逻辑的一部分，
    // 不是「所有订单都可能需要、只是默认不做」的扩展点。钩子用于后者。
    String account = extractAccount(ctx.getProductName());
    if (!isValidAccount(account)) {
        // 注意：这里用 abort() 而不是抛异常。
        ctx.abort("充值账号格式非法：" + account);
        System.out.println("  │  ② 支付：账号校验失败 → " + account);
        return;
    }

    BigDecimal paid = ctx.getAmount().setScale(2, RoundingMode.HALF_UP);
    String paymentNo = generateSerialNo("VPAY");
    ctx.setPaymentNo(paymentNo);
    ctx.record("② 支付 → 全额 ￥" + paid.toPlainString()
            + "，账号 " + account + " 校验通过，流水号 " + paymentNo);
    // ...
}
```

**注释里那个「为什么不用 `beforePay()` 钩子」是一个真实的判断。** 区分标准：

| | 放在自己的 `pay()` 里 | 放在 `beforePay()` 钩子里 |
|---|---|---|
| 这段逻辑属于谁 | **本类型独有** | 概念上所有类型都可能需要 |
| 例子 | 虚拟商品的账号校验 | 支付前的通用校验位（预售定金、未来的风控/限额） |

**账号校验只有虚拟商品需要，实物商品和预售商品永远不会用到它。**
放进钩子会传达错误信息：「这是一个通用扩展点」。

**业务理由（为什么虚拟商品必须校验账号）**：
话费充值、游戏点卡这类商品，**充错账号是无法撤回的**——
货（余额）已经进入别人的账户，退不回来。所以支付前必须校验，这是虚拟商品特有的风控要求。

#### `deliver()`：防盗链

```java
@Override
protected void deliver(OrderContext ctx) {
    // 用支付流水号做链接的签名，防止盗链（真实项目会用 HMAC 签名）
    String link = "https://dl.example.com/" + ctx.getOrderNo().toLowerCase()
            + "?token=" + ctx.getPaymentNo();

    ctx.setDeliveryProof(link);
    ctx.record("③ 交付 → 已生成下载链接（token 来自支付流水 " + ctx.getPaymentNo() + "）");

    System.out.println("  │  ③ 交付：已生成下载链接");
    System.out.println("  │        └ " + link);
    System.out.println("  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用");
}
```

**这又是一处「顺序不可调换」的实际约束**：
下载链接的 token 来自支付流水号。如果 `deliver()` 在 `pay()` 之前执行，
链接就变成 `...?token=null`——**任何人都能下载，等于免费**。

实测输出：

```
  │  ③ 交付：已生成下载链接
  │        └ https://dl.example.com/ord-v-001?token=VPAY20261022125133-0005
  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用
```

#### 两个私有辅助方法：让失败分支可被触发

```java
/** 真实项目里账号是订单的独立字段。这里用「商品名里 @ 后面的部分」模拟，
 *  好处是可以构造出校验失败的用例来演示 abort 分支。 */
private String extractAccount(String productName) {
    int at = productName.indexOf('@');
    return (at >= 0 && at < productName.length() - 1)
            ? productName.substring(at + 1)
            : productName;
}

/** 故意写得很宽松——目的不是做真实的账号校验，
 *  而是让「校验失败 → abort」这条分支可以被触发。 */
private boolean isValidAccount(String account) {
    return account != null && account.length() >= 5 && !account.contains(" ");
}
```

**两个用例的实测结果：**

| 商品名 | `extractAccount()` 结果 | 长度 | `isValidAccount()` | 流程 |
|---|---|---:|:---:|---|
| `Java编程思想电子书@zhangsan@mail.com` | `zhangsan@mail.com` | 17 | `true` | 正常支付（demo2、demo7） |
| `话费充值@12` | `12` | 2 | `false` | **中止**（demo4-B） |

**为什么 `isValidAccount()` 写得这么宽松（≥ 5 位且不含空格）？**

因为它的目的**不是做真实的账号校验**，而是让「校验失败 → abort」这条分支
**可以被真实触发**。如果写成真实的手机号正则（`1[3-9]\d{9}`），
读者就得先构造一个合法手机号才能看到正常流程——增加理解成本，且和教学目标无关。

> **Demo 里的校验逻辑要「够用就好」，不要在示例代码里塞真实的业务规则。**
> 真实规则会让读者分心去理解规则本身，而忘了要看的是设计模式。

#### 类 Javadoc 里批判的一个反模式

原项目的写法很容易让人想到「用 `instanceof` 判断类型」。
本类的 Javadoc 详细列出了它的三个致命问题：

```java
// ❌ 反模式
protected void notifyCustomer(OrderContext ctx) {
    String label;
    if (this instanceof VirtualProductOrder) {
        label = "下载链接";
    } else {
        label = "物流单号";
    }
}
```

| 问题 | 说明 |
|---|---|
| **父类依赖子类，违反 DIP** | 父类引用了子类的类型，高层模块依赖了低层模块的细节 |
| **新增类型必须改父类，违反 OCP** | 每加一种订单就要动父类，波及全部类型的回归测试 |
| **多层继承时判断出错** | 如果 `PreSaleOrder extends VirtualProductOrder`，`instanceof VirtualProductOrder` 对预售也返回 `true`，走进错误分支 |

**第三条尤其阴险**——它在当前的两层继承结构下**完全正常**，
只有等到某天有人加了一层继承才会爆发。而那时候改代码的人根本不会想到要动父类。

> **`instanceof` 不是永远不能用。** 它的合理场景是「对外部传入的对象做一次性分类」
> （比如处理第三方回调、解析异构消息）。
> **但在自己的继承体系内部，用 `instanceof` 区分兄弟类型，几乎总是设计出了问题。**

---

### 5.5 `PreSaleOrder.java`（264 行）：四种角色全用上

#### 定位

**这个类是本次重构新增的，原项目没有。** 它承担两个任务：

1. **验证开闭原则**——新增一种订单类型，父类和两个兄弟类**改动 0 行**；
2. **完整演示四种方法角色的子类侧用法**——前两个子类各自只用了一部分。

| 父类方法 | 角色 | 本类的处理 |
|---|---|---|
| `processOrder()` | 模板方法 | **不能改**（final，想改也改不了） |
| `pay()` | 抽象方法 | **必须实现** → 只付 30% 定金 |
| `deliver()` | 抽象方法 | **必须实现** → 给出预计到货日 |
| `notifyCustomer()` | 具体方法 | **重写**，但**调用 super 复用父类逻辑** |
| `checkStock()` | 具体方法 | 不重写（钩子已让它不被调用） |
| `needsStockCheck()` | 钩子（返回值型） | **重写 → false** |
| `deliveryProofLabel()` | 钩子（返回值型） | **重写 → 「预计到货日」** |
| `beforePay()` | 钩子（空实现型） | **重写** → 插入定金比例校验 |

**八行里，六种不同的处理方式**——这是全项目对「四种角色」最完整的演示。

三条预售业务规则，正好各自对应一种「方法角色」：

1. **不占现货库存**（卖的是未来的产能）→ 重写**钩子** `needsStockCheck()`；
2. **只付 30% 定金**，尾款到货后付 → 实现**抽象方法** `pay()`；
3. **通知内容要多一句尾款提醒**，但主体和别的订单一样 → 重写**具体方法** `notifyCustomer()` 并调用 `super`。

#### 常量定义：一个真实 bug 的产物

```java
/** 定金比例：30%。真实项目里这个值来自商品配置表。 */
private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.30");

/**
 * 定金最低金额：1 元。低于这个值就不走预售流程。
 *
 * <p><b>为什么需要这个下限？</b>
 * 因为支付渠道（微信/支付宝/银联）对单笔交易有最低金额限制，
 * 几分钱的定金实际上收不上来；而且为几分钱走一整套预售流程
 * （定金 → 排产 → 到货 → 尾款）在业务上毫无意义。
 *
 * <p><b>一个真实的调试经历</b>：这个阈值最初写的是 {@code deposit.signum() <= 0}
 * （即「定金为 0 才拦」），结果拿 ￥0.10 的订单测试时，
 * 定金算出来是 {@code 0.03 > 0}，<b>校验直接通过了</b>，根本没拦住。
 * 是运行 Demo 时看到「定金 ￥0.03」这行输出才发现的。
 * <b>"金额为 0"和"金额小得不合理"是两回事，阈值要按业务定，不能按数学定。</b>
 */
private static final BigDecimal MIN_DEPOSIT = BigDecimal.ONE;

private static final int ESTIMATED_DELIVERY_DAYS = 30;    // 预计发货天数
private static final int BALANCE_DEADLINE_DAYS = 3;       // 到货后 3 日内付清尾款
```

**这是本项目开发过程中真实踩到的第二个 bug**，完整经过：

| 阶段 | 代码 / 现象 |
|---|---|
| 修复前 | `if (deposit.signum() <= 0)` → ￥0.10 的定金算出来是 0.03，`signum()` 返回 `1` → **校验通过** → 订单被当成正常预售处理，输出「定金 ￥0.03」「订单处理完成 √」「isAborted = false」 |
| 同时 Client 却打印 | 「这一单是被 beforePay() 钩子拦下的」→ **文字与实际行为完全矛盾** |
| 修复后 | `if (deposit.compareTo(MIN_DEPOSIT) < 0)` → `0.03 < 1` → **拦下** |

**发现过程**：跑 Demo 时看到输出里有「定金 ￥0.03」这行，
而按设计这一单应该被拦住、根本不该有定金。这才回去查条件。

> **教训一：`signum()` 只能判断「正/零/负」，判断「小于某个业务阈值」必须用 `compareTo`。**
> `signum() <= 0` 的语义是「≤ 0」，不是「太小」。这两个概念在数学上不同，在业务上更是天差地别。
>
> **教训二：Demo 的输出文字必须和实际行为一致，而且必须靠运行来核对，不能靠推理。**
> 我写 Client 那句「这一单是被钩子拦下的」时，**是基于对代码的理解而非实际运行结果**。
> 理解错了，文字就成了误导。
> **这个项目的每一段输出文字，最终都对照真实运行结果核对过一遍。**

#### 三个钩子重写

```java
/**
 * 预售不检查现货库存。
 * 原因：预售卖的是「未来的产能」，不是仓库里的现货。
 * 工厂还没生产出来，查现货库存只会得到 0，
 * 于是流程会在步骤 ① 就被【错误地中止】——这正是原项目那种设计会导致的 bug。
 */
@Override
protected boolean needsStockCheck() {
    return false;
}

/** 预售订单在「发货」这一步交付给用户的不是实物，而是一个承诺的日期。 */
@Override
protected String deliveryProofLabel() {
    return "预计到货日";
}

@Override
protected void beforePay(OrderContext ctx) {
    BigDecimal deposit = depositOf(ctx.getAmount());
    // compareTo < 0 而不是 signum() <= 0：拦的是「定金低于 1 元」，不只是「定金为 0」。
    if (deposit.compareTo(MIN_DEPOSIT) < 0) {
        ctx.abort("定金 ￥" + deposit.toPlainString() + " 低于最低限额 ￥"
                + MIN_DEPOSIT.toPlainString() + "，不适用预售（订单金额 ￥"
                + ctx.getAmount().toPlainString() + " × " + DEPOSIT_RATE + "）");
        System.out.println("  │  [钩子 beforePay] 定金校验失败：￥" + deposit.toPlainString()
                + " < 最低限额 ￥" + MIN_DEPOSIT.toPlainString());
    } else {
        ctx.record("【钩子 beforePay】定金校验通过，应付定金 ￥" + deposit.toPlainString());
        System.out.println("  │  [钩子 beforePay] 定金校验通过，应付 ￥" + deposit.toPlainString());
    }
}
```

**`needsStockCheck()` 返回 `false` 的理由特别值得注意：**

> 在**本项目的模拟公式**下，预售商品名「新款折叠屏手机首发预售版」12 字
> 算出来是 `(12-3)*7 = 63` 件，看着没问题。
> **但真实系统里预售商品的现货库存就是 0**——于是所有预售订单都会在第一步被错误中止。
>
> **这是原项目那种「无钩子」设计会导致的真实 bug**，只是被 Demo 里的模拟公式掩盖了。
> **Demo 的模拟逻辑有时会掩盖设计的缺陷，这是写示例代码时必须警惕的事。**

#### `pay()`：只收定金

```java
@Override
protected void pay(OrderContext ctx) {
    // 【注意】这里既不用调 beforePay(ctx)，也不用写 if (ctx.isAborted()) return;
    // 钩子由模板方法统一调用，abort 也由模板方法统一检测——
    // 定金校验失败时，本方法【根本不会被执行】。
    // 这正是「把钩子调用放进模板方法」的收益：子类不需要写任何防御代码。
    BigDecimal deposit = depositOf(ctx.getAmount());
    BigDecimal balance = ctx.getAmount().subtract(deposit)
            .setScale(2, RoundingMode.HALF_UP);
    String paymentNo = generateSerialNo("PRE");

    ctx.setPaymentNo(paymentNo);
    ctx.record("② 支付 → 定金 ￥" + deposit.toPlainString()
            + "（尾款 ￥" + balance.toPlainString() + " 到货后付），流水号 " + paymentNo);

    System.out.println("  │  ② 支付：定金 ￥" + deposit.toPlainString()
            + "（" + DEPOSIT_RATE.multiply(BigDecimal.TEN).stripTrailingZeros() + " 成），流水号 " + paymentNo);
    System.out.println("  │        └ 尾款 ￥" + balance.toPlainString() + " 待到货后支付");
}
```

**`DEPOSIT_RATE.multiply(BigDecimal.TEN).stripTrailingZeros()`** 这一串是在把 `0.30` 转成「3 成」：

```
new BigDecimal("0.30") × 10 = 3.00  →  stripTrailingZeros()  →  3
```

**为什么不直接写死字符串「3」？** 因为那样 `DEPOSIT_RATE` 改成 `0.20` 时，
输出的文案还会说「3 成」——**同一个业务数字出现在两个地方，必然有一天不一致**。

> **`multiply` 的 scale 规则：结果的 scale = 两个操作数 scale 之和。**
> `0.30`(scale=2) × `10`(scale=0) = scale 2，所以是 `3.00` 而不是 `3`——这就是必须 `stripTrailingZeros()` 的原因。
>
> **`stripTrailingZeros()` 有个著名的坑**：对 `new BigDecimal("100")` 调用会返回 `1E+2`。
> 要输出给终端得再 `.toPlainString()`。
> 本项目这里拼进字符串（隐式调 `toString()`），因为值一定是 0~10 之间的一位数，不会触发。
> **但这是一个真实存在的陷阱，值得记一笔。**

实测输出（demo3）：

```
  │  [钩子 beforePay] 定金校验通过，应付 ￥2099.70
  │  ② 支付：定金 ￥2099.70（3 成），流水号 PRE20261022125133-0006
  │        └ 尾款 ￥4899.30 待到货后支付
```

**手工验算**：`6999.00 × 0.30 = 2099.70` ✅；`6999.00 − 2099.70 = 4899.30` ✅；`2099.70 + 4899.30 = 6999.00` ✅

#### `deliver()`：给出预计到货日

```java
@Override
protected void deliver(OrderContext ctx) {
    String eta = LocalDateTime.now().plusDays(ESTIMATED_DELIVERY_DAYS).format(DATE_FMT);

    ctx.setDeliveryProof(eta);
    ctx.record("③ 交付 → 已登记生产排期，预计到货 " + eta
            + "（关联定金流水 " + ctx.getPaymentNo() + "）");

    System.out.println("  │  ③ 交付：已登记生产排期，预计到货日 " + eta);
    System.out.println("  │        └ 关联定金流水 " + ctx.getPaymentNo());
}
```

**注意这一步同样依赖 `ctx.getPaymentNo()`**——到货通知要带上定金流水号，用户才能对上账。
**这是第三处「顺序不可调换」的实际约束。**

`PhysicalProductOrder` 用 `getPaymentNo()` 做对账关联，`VirtualProductOrder` 用它做链接签名，
`PreSaleOrder` 用它做定金凭证——**三种业务，同一个数据依赖方向**。
这就是 `final` 存在的意义：**它保护的是数据流的方向。**

---

#### `notifyCustomer()`：重写具体方法 + 调 super（本类最值得学的一段）

```java
@Override
protected void notifyCustomer(OrderContext ctx) {
    // 第一步：让父类完成标准通知（它会写入 ctx.notificationContent）
    super.notifyCustomer(ctx);

    // 第二步：在父类结果的基础上追加尾款提醒
    BigDecimal balance = ctx.getAmount().subtract(depositOf(ctx.getAmount()))
            .setScale(2, RoundingMode.HALF_UP);
    String deadline = LocalDateTime.now()
            .plusDays(ESTIMATED_DELIVERY_DAYS + BALANCE_DEADLINE_DAYS).format(DATE_FMT);

    String extra = "尾款 ￥" + balance.toPlainString() + " 请于 " + deadline
            + " 前支付，逾期订单取消且定金不退";

    ctx.setNotificationContent(ctx.getNotificationContent() + "；" + extra);
    ctx.record("④+ 追加尾款提醒 → " + extra);

    System.out.println("  │     + 追加提醒：" + extra);
    System.out.println("  │     ↑ 这一行由子类追加，上一行由 super.notifyCustomer() 产生");
}
```

**「大部分逻辑复用父类，只追加一点自己的东西」是继承体系里最常见的需求。** 三种写法对比：

```java
// ❌ 写法 1：完全不调 super，把父类逻辑抄一遍
protected void notifyCustomer(OrderContext ctx) {
    String content = "尊敬的客户，您的订单 " + ctx.getOrderNo() + ... ;   // 抄父类的 5 行
    content += "；尾款提醒...";
}
// 问题：父类改了通知模板，本类不会跟着改，两份代码逐渐不一致。

// ❌ 写法 2：把父类的 notifyCustomer() 改成抽象方法
// 问题：逼着 PhysicalProductOrder 和 VirtualProductOrder 各写一遍相同的逻辑。

// ✅ 写法 3（本类采用）：调 super 再追加
protected void notifyCustomer(OrderContext ctx) {
    super.notifyCustomer(ctx);                          // 复用父类
    ctx.setNotificationContent(ctx.getNotificationContent() + "；" + extra);
}
```

**写法 3 的关键是：追加的内容依赖父类的产出。**

`super.notifyCustomer(ctx)` 会写入 `ctx.notificationContent`，
本方法**读出来 → 拼接 → 再写回去**。

> **这个「读-改-写」的顺序不能颠倒。**
> 如果先拼后调 super，父类会用它的标准文案**整个覆盖掉**本类刚拼好的内容。
>
> ```java
> // ❌ 顺序错了
> ctx.setNotificationContent("尾款提醒...");    // 先写
> super.notifyCustomer(ctx);                     // 父类覆盖掉，追加内容丢失
> ```
>
> **这种 bug 编译器抓不到，测试也可能抓不到**——只能靠读代码或者看实际输出发现。

实测输出（demo3，注意两行的来源不同）：

```
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-S-001（新款折叠屏手机首发预售版）已处理完成，预计到货日：2026-11-21
  │     + 追加提醒：尾款 ￥4899.30 请于 2026-11-24 前支付，逾期订单取消且定金不退
  │     ↑ 这一行由子类追加，上一行由 super.notifyCustomer() 产生
```

**日期验算**（运行日 2026-10-22）：
- 预计到货 = `+30 天` = **2026-11-21** ✅（10 月有 31 天，22 + 30 = 52 → 52 − 31 = 21，跨到 11 月）
- 尾款截止 = `+30+3 天` = **2026-11-24** ✅

#### `depositOf()`：同一个业务数字只能有一个计算入口

```java
/**
 * <p>抽成私有方法是因为 {@code pay()} 和 {@code notifyCustomer()} 都要用，
 * 且两处的结果<b>必须完全一致</b>（否则用户看到的尾款和实际要付的对不上）。
 * <b>同一个业务数字只能有一个计算入口</b>，这是消除「数据不一致 bug」的基本手段。
 */
private BigDecimal depositOf(BigDecimal amount) {
    return amount.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
}
```

`beforePay()` 校验定金、`pay()` 算定金、`notifyCustomer()` 算尾款——**三处都要用**。
如果各写各的 `amount.multiply(new BigDecimal("0.30"))`，
某天比例改成 20%，改了 `pay()` 忘了改 `notifyCustomer()`，
用户就会看到「定金 ￥1399.80，尾款 ￥4899.30」这种**加起来不等于订单总额**的通知。

**这类 bug 的可怕之处在于：程序不报错、流程不中止、每一行单独看都合理。**

#### 开闭原则的真实证据

`PreSaleOrder` 是新增的，加它的时候被修改的文件是：

| 文件 | 改动 |
|---|---|
| `PreSaleOrder.java` | **新建**（264 行） |
| `Client.java` | 加一段演示代码（客户端本来就要用新类型） |
| `OrderProcessTemplate.java` | **0 改动** |
| `OrderContext.java` | **0 改动** |
| `PhysicalProductOrder.java` | **0 改动** |
| `VirtualProductOrder.java` | **0 改动** |

**父类和两个兄弟类完全没被碰过。**

> 这个证据之所以有力，是因为它**可以被 `git diff` 验证**，不是一句「我们遵循了开闭原则」的口号。
> 面试时被问到「你怎么证明开闭原则」——**拿得出这种改动范围表，比背定义有说服力得多**。

---

### 5.6 `Client.java`（373 行）：七段演示，各回答一个问题

| Demo | 回答的问题 | 用到的关键能力 |
|---|---|---|
| `demo1Physical()` | 标准流程长什么样？步骤之间怎么传数据？ | 数据依赖、超重分支 |
| `demo2Virtual()` | 钩子怎么让某个步骤被跳过？ | `needsStockCheck()`、`deliveryProofLabel()` |
| `demo3PreSale()` | 四种方法角色怎么在同一个子类里配合？ | `super` 调用、定金计算 |
| `demo4ShortCircuit()` | 步骤失败时流程怎么中止？ | 三种不同的中止路径 |
| `demo5Polymorphism()` | 多态的价值是什么？ | `List<OrderProcessTemplate>`、无 `instanceof` |
| `demo6WhyFinal()` | **为什么模板方法必须是 final？** | 故意写反顺序，实测 `null` |
| `demo7Trace()` | 怎么审计「谁做了什么」？ | 轨迹对比、只读性验证 |

**为什么是七段而不是一段大 main？**

因为**模板方法模式的每个约束，都需要一个独立的场景才能被观察到**：

- `final` 的必要性 → 必须**故意绕过**它才能看到后果（demo6）；
- 钩子的价值 → 必须有一个**不适用某步骤**的类型（demo2）；
- 失败短路 → 必须构造**三种不同的失败原因**（demo4-A/B/C）；
- 开闭原则 → 必须有**第三种类型**来证明前两种没被改动（demo3）；
- 多态 → 必须把**三种类型放进同一个 List**（demo5）。

**把这些混在一段代码里，读者只能看到「跑通了」，看不到「为什么必须这样」。**

#### demo5：一段不认识任何具体类型的代码

```java
List<OrderProcessTemplate> processors = List.of(
        new PhysicalProductOrder(),
        new VirtualProductOrder(),
        new PreSaleOrder()
);
List<OrderContext> orders = List.of(
        new OrderContext("ORD-B-001", "无线鼠标", new BigDecimal("129.00")),
        new OrderContext("ORD-B-002", "在线课程@lisi@example.com", new BigDecimal("299.00")),
        new OrderContext("ORD-B-003", "限量版手办预售", new BigDecimal("899.00"))
);

int success = 0;
for (int i = 0; i < orders.size(); i++) {
    OrderContext result = processors.get(i).processOrder(orders.get(i));
    if (!result.isAborted()) {
        success++;
    }
    System.out.println();
}

System.out.println("      处理完成：" + success + " / " + orders.size() + " 笔成功");
```

**这个循环体里没有任何 `instanceof`、没有任何类型判断。**
三种完全不同的订单类型，用同一个方法调用、同一套结果读取方式。

**这就是模板方法模式对调用方的价值**：

> 调用方只需要知道**父类的接口**（`processOrder(OrderContext)` 和 `OrderContext`），
> 完全不需要知道有几种订单类型、每种类型怎么处理。
>
> **新增第四种订单类型时，这段代码一个字都不用改。**

**为什么这个能力依赖 2.7 节修的那个可见性问题？** 因为如果父类不是 `public`，
`List<OrderProcessTemplate>` 这行**根本编译不过**——多态在源头上就被切断了。

#### demo6：全项目最反直觉的一段

这段代码**故意绕过模板方法**，直接按错误顺序调用 `protected` 步骤：

```java
PhysicalProductOrder processor = new PhysicalProductOrder();
OrderContext ctx = new OrderContext(
        "ORD-X-001", "机械键盘", new BigDecimal("399.00"));

// ❌ 错误顺序：先发货（pay() 还没执行）
processor.deliver(ctx);
System.out.println("      >>> 发货步骤里读取的「关联支付流水」是什么？");
System.out.println("          ctx.getPaymentNo() = " + ctx.getPaymentNo());

// 补上支付
processor.pay(ctx);
System.out.println("      现在再调 pay()：" + ctx.getPaymentNo());
System.out.println("      → 但发货记录已经写进物流系统了，改不回来了。");
```

真实输出：

```
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0011
  │        └ 关联支付流水 null  ← 这个值来自步骤 ②，所以顺序不能调换

      >>> 发货步骤里读取的「关联支付流水」是什么？
          ctx.getPaymentNo() = null
          ↑ 【null】！因为 pay() 还没执行。

      >>> 后果：物流公司收到一条「货已发出、支付流水号为 null」的记录，
          财务对账时这笔货不知道是谁付的钱。这是真实的资金风险。

  │  ② 支付：全额 ￥399.00，流水号 PAY20261022125133-0012
      现在再调 pay()：PAY20261022125133-0012
      → 但发货记录已经写进物流系统了，改不回来了。
```

**为什么这段要放在 Client 里？**

因为**如果不这样演示一遍，`final` 就只是一条规则；演示过之后，它才是「防止了一个具体事故的机制」。**

**这段也暴露了 `protected` 的语言级限制**（见 [4.7 ④](#-但-protected-有个真实的漏洞必须诚实说明)）：
它能跑，只因为 Client 和订单类同包。生产项目的三种应对方式（分包 / JPMS / ArchUnit）
在 demo6 的输出里明确列出。

#### demo7：审计轨迹的价值

```java
OrderContext ctx = new OrderContext(
        "ORD-T-001", "智能手表", new BigDecimal("1299.00"));
new PhysicalProductOrder().processOrder(ctx);

List<String> trace = ctx.getTrace();
for (int i = 0; i < trace.size(); i++) {
    System.out.println("      " + (i + 1) + ". " + trace.get(i));
}
```

**同一个循环，两种订单类型的轨迹完全不同**——这是本 demo 的核心对照：

```
实物商品：                              虚拟商品：
1. 流程启动，处理器 = PhysicalProductOrder   1. 流程启动，处理器 = VirtualProductOrder
2. ① 检查库存 → 可用 7 件                  2. 跳过库存检查（VirtualProductOrder.needsStockCheck() = false）
3. ② 支付 → 全额 ￥1299.00，流水号 ...      3. ② 支付 → 全额 ￥168.00，账号 ... 校验通过，流水号 ...
4. ③ 发货 → 已交付顺丰，运单号 ...          4. ③ 交付 → 已生成下载链接（token 来自支付流水 ...）
5. ④ 通知客户 → 已发送（凭证类型：物流单号）  5. ④ 通知客户 → 已发送（凭证类型：下载链接）
6. 流程结束                               6. 流程结束
```

**第 2 条的差异最重要**：一个写「检查库存 → 可用 7 件」，一个写「跳过库存检查」。

> **审计日志的核心价值不是「记录做了什么」，而是「记录没做什么」。**
> 因为「做了什么」通常能从结果反推，「没做什么」永远反推不出来。

#### 输出格式的工程细节

你可能注意到所有输出都用了 `┌ │ └` 这些制表符，而且对齐了：

```
  ┌─ 开始处理订单 ORD-P-001（机械键盘，￥399.00）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 7 件
  │  ② 支付：全额 ￥399.00，流水号 PAY20261022125133-0001
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0002
  │        └ 关联支付流水 PAY20261022125133-0001  ← 这个值来自步骤 ②，所以顺序不能调换
  │  ④ 通知客户：尊敬的客户，……
  └─ 订单处理完成 √
```

**这套符号是有代价的**：在 Windows 的 GBK 控制台下，
`▶ ✔ ✘` 这类字符**渲染不出来**（会变成问号或空白）。

本项目采用的字符都经过实际测试：

| 字符 | 状态 | 用途 |
|---|---|---|
| `┌ │ └ ├ ─` | ✅ 可用 | 流程树状图 |
| `① ② ③ ④` | ✅ 可用 | 步骤编号 |
| `→ ← ★ ◆ ◇ × √ μ ↑` | ✅ 可用 | 箭头、标记 |
| `▶ ✔ ✘` | ❌ **不用** | 渲染失败 |
| `✓`（U+2713） | ⚠️ **未验证，已改为 `√`** | 成功标记 |

> **这一条是踩过坑之后才总结出来的。**
> 早期代码里写了 `订单处理完成 ✓`，但没有在真实 GBK 控制台下验证过，
> 保守起见全部换成了已验证可用的 `√`。
> **「我测过的」和「我以为能用」必须严格区分——这是本项目贯穿始终的原则。**

---

## 6. 执行流程：七段 Demo 的真实运行结果

### 6.0 怎么跑

```bash
# 编译
javac --release 25 -encoding UTF-8 -d out \
      src/com/patterns/util/*.java \
      src/com/patterns/behavioral/templatemethod/*.java

# 运行（三个编码参数都必要，见附录）
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out \
     com.patterns.behavioral.templatemethod.Client
```

运行环境：**JDK 25，Windows，PowerShell**。
下面所有输出都是这一次真实运行的**逐字拷贝**（流水号里的时间戳 `20261022125133` 是运行时刻）。

> **阅读提示**：整段输出里流水号是 `0001` → `0015` 连续递增的。
> 这不是巧合，而是**静态计数器 `sequence` 的作用**——
> 更重要的是：**demo4 里三笔中止的订单没有消耗任何流水号**（从 `0006` 直接跳到 `0007`），
> 这本身就是「失败短路生效」的一条硬证据。

---

### 6.1 demo1：实物商品订单（标准流程）

#### 完整输出

```
=== 1. 实物商品订单（标准流程）===
  ┌─ 开始处理订单 ORD-P-001（机械键盘，￥399.00）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 7 件
  │  ② 支付：全额 ￥399.00，流水号 PAY20261022125133-0001
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0002
  │        └ 关联支付流水 PAY20261022125133-0001  ← 这个值来自步骤 ②，所以顺序不能调换
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-P-001（机械键盘）已处理完成，物流单号：SF20261022125133-0002
  └─ 订单处理完成 √

  -- 调用方能读到的产出（原项目全是 void，这些一个都拿不到）--
      支付流水号：PAY20261022125133-0001
      物流单号  ：SF20261022125133-0002
      可用库存  ：7
      是否中止  ：false

  -- 同一个处理器处理另一笔订单（触发子类特有的超重分支）--
  ┌─ 开始处理订单 ORD-P-003（对开门大容量冰箱洗衣机组合套装，￥8999.00）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 84 件
  │  ② 支付：全额 ￥8999.00，流水号 PAY20261022125133-0003
  │  ③ 发货：已交付重货物流专线，运单号 SF20261022125133-0004
  │        └ 重量 45.0kg 超过 30kg 阈值，普通快递不收，改走专线
  │        └ 关联支付流水 PAY20261022125133-0003  ← 这个值来自步骤 ②，所以顺序不能调换
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-P-003（对开门大容量冰箱洗衣机组合套装）已处理完成，物流单号：SF20261022125133-0004
  └─ 订单处理完成 √

      >>> 「超重改走专线」这个判断只存在于 PhysicalProductOrder 里。
          虚拟商品没有重量、预售商品发货时才知道重量，
          所以这段逻辑【不该】放到父类去污染其他类型。
          这就是「子类实现抽象方法」的实际含义：子类装自己的特殊规则。
```

#### 逐步执行流程（第一笔订单）

下表把「调用方做了什么都不用管」这件事拆成每一步：

| 步 | 执行位置 | 发生了什么 | 关键变量 | 输出 |
|---:|---|---|---|---|
| 1 | `Client.demo1Physical()` | `new PhysicalProductOrder()` | — | — |
| 2 | `Client` | `new OrderContext("ORD-P-001", "机械键盘", 399.00)` | 三条构造校验全部通过 | — |
| 3 | `Client` | 声明 `OrderProcessTemplate processor`，**指向子类实例** | 父类引用 | — |
| 4 | `Client` → 父类 | `processor.processOrder(ctx)`：静态类型是父类，**实际执行父类的 `final` 方法** | — | `┌─ 开始处理订单...` |
| 5 | 父类 | `getClass().getSimpleName()` 取真实类型 | `PhysicalProductOrder` | `处理器类型：...` |
| 6 | 父类 → **钩子** | `needsStockCheck()` 未被子类重写 → 返回默认值 `true` | `true` | — |
| 7 | 父类 → **具体方法** | `checkStock(ctx)`：`max(0, 4−3) × 7 = 7` | `availableStock = 7` | `① 检查库存：可用 7 件` |
| 8 | 父类 | `7 <= 0` 为假 → **不中止** | — | — |
| 9 | 父类 → **钩子** | `beforePay(ctx)`：未重写 → 空操作 | — | — |
| 10 | 父类 | `ctx.isAborted()` 为 `false` → 继续 | — | — |
| 11 | 父类 → **抽象方法** | `pay(ctx)`：调用**子类的实现** | — | — |
| 12 | 子类 `PhysicalProductOrder` | `setScale(2)` 得 `399.00`；`generateSerialNo("PAY")` → `sequence=1` | `paid=399.00`、`paymentNo=PAY...0001` | `② 支付：...` |
| 13 | 子类 → `ctx` | `ctx.setPaymentNo("PAY...0001")` **← 数据写进上下文** | `paymentNo` | — |
| 14 | 父类 | 检查 1：`isAborted()` = `false` | — | — |
| 15 | 父类 | 检查 2：`getPaymentNo()` = `"PAY...0001"` ≠ null | — | — |
| 16 | 父类 → **抽象方法** | `deliver(ctx)` → 子类实现 | — | — |
| 17 | 子类 | `estimateWeight("机械键盘")` = `4 × 3 = 12.0` kg | `weight=12.0` | — |
| 18 | 子类 | `12.0 > 30` 为假 → `carrier = "顺丰"` | `overweight=false` | — |
| 19 | 子类 | `generateSerialNo("SF")` → `sequence=2` | `trackingNo=SF...0002` | `③ 发货：已交付顺丰...` |
| 20 | 子类 → `ctx` | **读取** `ctx.getPaymentNo()` 拼进轨迹 | — | `└ 关联支付流水 PAY...0001` |
| 21 | 父类 → **具体方法** | `notifyCustomer(ctx)` → **父类实现**，内部调用**钩子** `deliveryProofLabel()` | 返回 `"物流单号"` | `④ 通知客户：...物流单号：SF...0002` |
| 22 | 父类 | `record("流程结束")`、打印结束行、`return ctx` | — | `└─ 订单处理完成 √` |
| 23 | `Client` | 从返回的 `ctx` 读四个字段 | — | `支付流水号：...` 等四行 |

**这个流程里最值得注意的三件事：**

**① 第 11 步和第 16 步是「回旋镖」式的控制反转。**

`processOrder()` 在父类里，但它调用的 `pay()` / `deliver()` 会**跳回子类执行**。
这就是好莱坞原则的字面含义：

> **"Don't call us, we'll call you."**（别调用我们，我们会调用你）
>
> 子类**不能**主动调用父类的模板方法，也**不能**决定执行顺序；
> 只有父类在合适的时机**回调**子类实现的那些步骤。

**② 第 13 步 → 第 20 步是一条真实的数据流。**

`pay()` 写入 `paymentNo` → 模板方法检查它非 null → `deliver()` 读取它。
**这条链就是 `final` 的意义所在**：断了任何一环，第 20 步打印出来的就是 `null`。

**③ 第 21 步的 `notifyCustomer()` 是父类实现，但它内部调了钩子。**

```java
// 父类的具体方法里调钩子 —— 这是模式里最常见的"组合拳"
String content = "尊敬的客户，..." + deliveryProofLabel() + "：" + ctx.getDeliveryProof();
```

于是**整段拼接逻辑复用父类，只有那四个字随子类变**。demo2 会看到这个钩子的效果。

#### 第二笔订单：超重分支

同一个 `processor` 对象处理另一笔订单——**复用一个处理器对象处理多笔订单是完全正常的**，
因为处理器**没有状态**（所有状态都在 `OrderContext` 里）。这一点很重要：

> **模板里的「状态」应该放在上下文里，而不是放在处理器对象上。**
> 如果处理器有成员变量，那它就不能并发处理多笔订单了。
> 本项目所有字段都是 `static final` 常量或 `static` 计数器，**实例字段为零**。

| 数据 | 订单 1 | 订单 2 |
|---|---|---|
| 商品名 | 机械键盘（4 字） | 对开门大容量冰箱洗衣机组合套装（15 字） |
| 库存 `max(0,len-3)*7` | `1 × 7 = 7` | `12 × 7 = 84` |
| 重量 `len × 3` | `4 × 3 = 12.0` kg | `15 × 3 = 45.0` kg |
| 超重判断 `> 30` | `false` | **`true`** |
| 承运方 | 顺丰 | **重货物流专线** |
| 运单号 | `SF...0002` | `SF...0004` |

---

### 6.2 demo2：虚拟商品订单（钩子跳过库存检查）

#### 完整输出

```
=== 2. 虚拟商品订单（钩子跳过库存检查）===
  ┌─ 开始处理订单 ORD-V-001（Java编程思想电子书@zhangsan@mail.com，￥89.90）
  │  处理器类型：VirtualProductOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  ② 支付：全额 ￥89.90，充值账号 zhangsan@mail.com 已校验，流水号 VPAY20261022125133-0005
  │  ③ 交付：已生成下载链接
  │        └ https://dl.example.com/ord-v-001?token=VPAY20261022125133-0005
  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-V-001（Java编程思想电子书@zhangsan@mail.com）已处理完成，下载链接：https://dl.example.com/ord-v-001?token=VPAY20261022125133-0005
  └─ 订单处理完成 √

  >>> 注意步骤 ① 被【跳过】了，而不是「检查通过」。
      这两者在审计时的责任归属完全不同，所以轨迹里明确记录了「跳过」。

  >>> 步骤 ④ 的通知文案自动变成了「下载链接」，
      而 notifyCustomer() 的整段拼接逻辑【完全复用父类】。
      子类只改了一个钩子的返回值（四个字），没有抄一行父类代码。

  -- 对照原项目的输出 --
      处理虚拟商品订单：
      检查库存                    ← 电子书库存无限，这一行是【错的】
      支付虚拟商品的订单
      通过邮件发送虚拟商品
      通知客户订单处理完成
```

#### 逐段对照：本项目 vs 原项目

| 步骤 | 原项目输出 | 本项目输出 | 差异性质 |
|---|---|---|---|
| ① 库存 | `检查库存` | `[钩子] 本类型商品无需检查库存，跳过步骤 ①` | **业务修正**：电子书没有库存 |
| ② 支付 | `支付虚拟商品的订单` | `② 支付：全额 ￥89.90，充值账号 zhangsan@mail.com 已校验，流水号 VPAY...0005` | 增加了**账号校验**和**真实产出** |
| ③ 交付 | `通过邮件发送虚拟商品` | `③ 交付：已生成下载链接` + 真实 URL | 产出了**可验证的交付凭证** |
| ④ 通知 | `通知客户订单处理完成`（固定文案） | `下载链接：https://...` | 文案随类型变化，且**带真实内容** |

**第 ④ 行最能说明钩子的价值。**

原项目：`notifyCustomer()` 是父类的具体方法，输出一句固定文案，
**子类完全无法影响它**。

本项目：`notifyCustomer()` 依然是父类的具体方法，但它内部调用了钩子
`deliveryProofLabel()`。子类付出的全部代价是：

```java
@Override
protected String deliveryProofLabel() { return "下载链接"; }
```

**四个字的差异，换来了整条通知文案的正确性。** 这就是「钩子定制细节」的性价比。

#### 一条容易被忽略的数据依赖

看第 ③ 步的 URL：

```
https://dl.example.com/ord-v-001?token=VPAY20261022125133-0005
                                        └──────────┬──────────┘
                                        这个值来自步骤 ②
```

**`ord-v-001` 是 `ctx.getOrderNo().toLowerCase()`，`token` 是 `ctx.getPaymentNo()`。**
两个值都来自上下文，一个在构造器里设置（输入），一个在步骤 ② 产生（产出）。

**如果 `deliver()` 在 `pay()` 之前执行，URL 会是 `...?token=null`**——
任何人都能下载，等于白送。这是第三处「顺序不可调换」的实际约束。

---

### 6.3 demo3：预售订单（新增子类，父类 0 改动）

#### 完整输出

```
=== 3. 预售订单（新增的子类，父类 0 改动）===
  ┌─ 开始处理订单 ORD-S-001（新款折叠屏手机首发预售版，￥6999.00）
  │  处理器类型：PreSaleOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  [钩子 beforePay] 定金校验通过，应付 ￥2099.70
  │  ② 支付：定金 ￥2099.70（3 成），流水号 PRE20261022125133-0006
  │        └ 尾款 ￥4899.30 待到货后支付
  │  ③ 交付：已登记生产排期，预计到货日 2026-11-21
  │        └ 关联定金流水 PRE20261022125133-0006
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-S-001（新款折叠屏手机首发预售版）已处理完成，预计到货日：2026-11-21
  │     + 追加提醒：尾款 ￥4899.30 请于 2026-11-24 前支付，逾期订单取消且定金不退
  │     ↑ 这一行由子类追加，上一行由 super.notifyCustomer() 产生
  └─ 订单处理完成 √

  -- 本类用到了全部四种方法角色 --
      模板方法 processOrder()      → final，子类【改不了】
      抽象方法 pay()/deliver()     → 子类【必须】实现（定金 30% / 预计到货日）
      具体方法 notifyCustomer()    → 子类【重写并调用 super】，追加尾款提醒
      钩子 needsStockCheck()       → 子类【重写为 false】，跳过库存检查
      钩子 deliveryProofLabel()    → 子类【重写】，称呼改为「预计到货日」
      钩子 beforePay()             → 子类【重写空钩子】，插入定金校验（由模板方法调用）

  -- 开闭原则的验证 --
      新增这个类时，OrderProcessTemplate.java 改动了 0 行；
      PhysicalProductOrder / VirtualProductOrder 也改动了 0 行。
      → 对扩展开放（新建一个类），对修改关闭（不碰任何已有类）。

      实际产出：定金流水 PRE20261022125133-0006
                预计到货 2026-11-21
```

#### 逐步执行流程（这一次有钩子参与）

| 步 | 执行位置 | 发生了什么 | 输出 |
|---:|---|---|---|
| 1 | 父类 | `needsStockCheck()` → **子类重写，返回 `false`** | — |
| 2 | 父类 | 走 `else` 分支：`ctx.record("跳过库存检查（PreSaleOrder.needsStockCheck() = false）")` | `[钩子] 本类型商品无需检查库存，跳过步骤 ①` |
| 3 | 父类 → **钩子** | `beforePay(ctx)` → **子类重写** | — |
| 4 | 子类 | `depositOf(6999.00)` = `2099.70` | — |
| 5 | 子类 | `2099.70.compareTo(1) < 0` 为假 → 走 `else`，`record(...)` | `[钩子 beforePay] 定金校验通过，应付 ￥2099.70` |
| 6 | 父类 | `isAborted()` = `false` → 继续 | — |
| 7 | 父类 → 抽象方法 | `pay(ctx)` → 子类实现 | — |
| 8 | 子类 | `deposit = 2099.70`，`balance = 6999.00 − 2099.70 = 4899.30` | — |
| 9 | 子类 | `generateSerialNo("PRE")` → `sequence=6` | `② 支付：定金 ￥2099.70（3 成）...` |
| 10 | 父类 → 抽象方法 | `deliver(ctx)` → 子类实现 | — |
| 11 | 子类 | `now().plusDays(30)` = `2026-11-21` | `③ 交付：...预计到货日 2026-11-21` |
| 12 | 父类 → **具体方法（子类重写）** | `notifyCustomer(ctx)` → 子类实现 | — |
| 13 | 子类 | **先调 `super.notifyCustomer(ctx)`** → 父类写入 `notificationContent`，并用钩子取到「预计到货日」 | `④ 通知客户：...预计到货日：2026-11-21` |
| 14 | 子类 | **读出** `getNotificationContent()`、拼上尾款提醒、**写回** | `+ 追加提醒：尾款 ￥4899.30 请于 2026-11-24 前支付...` |
| 15 | 父类 | `record("流程结束")`、`return ctx` | `└─ 订单处理完成 √` |

**第 13-14 步的「读-改-写」是整个 demo3 最值得看的地方。**

如果顺序反了（先写自定义内容，再调 `super`），父类会用标准文案**整个覆盖掉**追加内容，
而 `+ 追加提醒` 那行 `println` 依然会打印——**看起来对，实际错了**。

> **这类「看起来对」的 bug 是最危险的。**
> 要发现它，必须去看 `ctx.getNotificationContent()` 的**最终值**，而不只是看 `println` 打了什么。
> （本项目 demo7 的轨迹里能看到 `④+ 追加尾款提醒` 这一条，间接验证了追加发生在父类之后。）

#### 三道钩子是怎么依次生效的

```
needsStockCheck() = false
    └─▶ 跳过步骤 ①（不再查库存）

beforePay() 重写
    └─▶ 在 pay() 之前插入定金校验（模板方法负责调用，见 4.5）

deliveryProofLabel() = "预计到货日"
    └─▶ 父类 notifyCustomer() 里的称呼从「物流单号」变成「预计到货日」
```

**三个钩子，三种不同的介入方式**：
一个控制「要不要做」，一个在「做之前插入」，一个改变「父类怎么描述」。
**这是钩子方法能表达的全部三种能力。**

---

### 6.4 demo4：失败短路（三种不同的中止路径）

这一段是全项目**最能体现「原项目缺了什么」**的演示。

#### 完整输出

```
=== 4. 失败短路：库存不足 / 账号非法 ===
  -- A. 实物商品库存不足 --
  ┌─ 开始处理订单 ORD-P-002（杯子，￥29.90）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 0 件  ← 不足！
  └─ 流程中止：库存不足（可用 0 件），订单不予处理
      支付流水号 = null   ← 【null】说明根本没走到支付步骤
      isAborted  = true
      中止原因   = 库存不足（可用 0 件），订单不予处理

      >>> 对比原项目：库存不足时它依然会打印「支付实物商品的订单」「发货实物商品」
          ——用户付了钱但没货，只能退款，这就是超卖事故。

  -- B. 虚拟商品充值账号非法 --
  ┌─ 开始处理订单 ORD-V-002（话费充值@12，￥100.00）
  │  处理器类型：VirtualProductOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  ② 支付：账号校验失败 → 12
  └─ 流程中止：充值账号格式非法：12
      isAborted = true
      中止原因  = 充值账号格式非法：12
      下载链接  = null   ← 【null】没走到交付步骤

      >>> 注意：这里用 ctx.abort() 而不是抛异常。
          「账号填错了」是正常业务情况，不是程序错误。
          抛异常会逼调用方写 try-catch，用 if 判断更符合语义。

  -- C. 预售订单定金低于最低限额，被钩子拦下 --
  ┌─ 开始处理订单 ORD-S-002（定制马克杯，￥0.10）
  │  处理器类型：PreSaleOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  [钩子 beforePay] 定金校验失败：￥0.03 < 最低限额 ￥1
  └─ 流程中止：定金 ￥0.03 低于最低限额 ￥1，不适用预售（订单金额 ￥0.10 × 0.30）
      isAborted = true
      中止原因  = 定金 ￥0.03 低于最低限额 ￥1，不适用预售（订单金额 ￥0.10 × 0.30）
      定金流水  = null   ← 【null】没真的扣钱

      >>> 这一单是被 beforePay() 钩子拦下的：0.10 × 0.30 = 0.03 < 最低限额 1 元。
          钩子不只是「开关」，它也能主动中止流程。

      >>> 【注意输出里没有「② 支付」这一行】
          因为 beforePay() 是由模板方法调用的，它一返回就检查 isAborted()，
          直接 return 了——PreSaleOrder.pay() 【根本没被执行】。
          对比：如果钩子是由子类自己在 pay() 里调的，
          子类就得额外写 if (ctx.isAborted()) return; 这种防御代码，
          而且【新增子类忘了写就会静默失效】。这就是调用位置的区别。

      >>> 【一个真实的调试经历】这个阈值最初写的是 deposit.signum() <= 0，
          即「定金为 0 才拦」。结果 0.03 > 0，校验直接通过了，
          这一单被当成正常预售处理了——是跑 Demo 看到「定金 ￥0.03」才发现的。
          教训："金额为 0"和"金额小得不合理"是两回事，阈值要按业务定。
```

#### 三条中止路径的对比

| | A. 库存不足 | B. 账号非法 | C. 定金过低 |
|---|---|---|---|
| 谁触发中止 | **父类**（`checkStock` 后检查 `availableStock`） | **子类**（在 `pay()` 里） | **子类**（在 `beforePay()` 钩子里） |
| `abort()` 由谁调用 | 父类 | 子类 | 子类 |
| 中止发生在第几步 | 步骤 ① 之后 | 步骤 ② 内部 | 步骤 ② 之前 |
| `pay()` 执行了吗 | ❌ 没执行 | ✅ 执行了（自己返回的） | ❌ **没执行** |
| 上下文里缺什么 | `paymentNo`、`deliveryProof` | `deliveryProof` | `paymentNo`、`deliveryProof` |
| 中止原因由谁提供 | 父类（基于数据） | 子类（业务判断） | 子类（业务判断） |

**这张表说明了一件重要的事：`abort()` 是一个通用的中止机制，
父类和子类都能用，而且有四种不同的组合。**

#### 一条硬证据：中止的订单没有消耗流水号

把整段输出里的流水号连起来看：

```
demo1:  0001  0002  0003  0004
demo2:  0005
demo3:  0006
demo4:  （无）  ← 三笔全部中止，一个流水号都没生成
demo5:  0007  0008  0009  0010
demo6:  0011  0012
demo7:  0013  0014  0015
```

**从 `0006` 直接跳到 `0007`。**

`generateSerialNo()` 是在 `pay()` 和 `deliver()` 里调用的。
demo4 的三笔订单**都没有走到这两步**（C 的 `pay()` 甚至没被执行），
所以序号一个都没涨。

> **这是「失败短路真的生效了」的最客观的证据**——不是靠日志文字，而是靠一个
> 独立于业务逻辑的计数器。**设计验证方案时，找一个「副作用」来验证，
> 比读日志靠谱得多。**

#### 一个代价：`try-catch` vs `if`

本项目在 demo4-B 的注释里明确解释了为什么用 `abort()` 而不是抛异常：

```java
// ❌ 用异常（语义错误）
try {
    processor.processOrder(ctx);
} catch (OutOfStockException e) {
    // 处理"没货"——但这本来就是正常业务分支
} catch (AccountInvalidException e) {
    // 处理"账号错"——同上
} catch (Exception e) {
    // 这里会同时接住真正的程序 bug，无法区分
}

// ✅ 用状态标志（本项目）
OrderContext result = processor.processOrder(ctx);
if (result.isAborted()) {
    // 一个 if 处理所有业务中止，看 reason 就知道原因
    log.warn("订单处理中止：{}", result.getAbortReason());
}
```

**两种写法的核心差异：**

| | 异常 | 状态标志 |
|---|---|---|
| 语义 | 「程序出了意料之外的错」 | 「业务上正常的一种结果」 |
| 调用方代码 | 必须 `try-catch` | 一个 `if` |
| 性能 | 要填充栈轨迹，开销大 | 一次布尔赋值 |
| 能否区分业务与 bug | **难**（都在 catch 里） | **能**（`isAborted` vs 真异常） |
| 能否「部分成功」 | 难 | 容易（上下文里有部分产出） |

> **判断标准：这件事在业务上是否每天都会发生？**
> 会 → 用状态标志；不会（说明代码有 bug）→ 用异常。

---

### 6.5 demo5：多态（一批混合订单统一处理）

#### 完整输出

```
=== 5. 多态：一批混合订单统一处理 ===
  待处理订单 3 笔，处理器 3 种
  下面这个循环体【不认识任何具体订单类型】：

      for (int i = 0; i < orders.size(); i++) {
          processors.get(i).processOrder(orders.get(i));
      }

  ┌─ 开始处理订单 ORD-B-001（无线鼠标，￥129.00）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 7 件
  │  ② 支付：全额 ￥129.00，流水号 PAY20261022125133-0007
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0008
  │        └ 关联支付流水 PAY20261022125133-0007  ← 这个值来自步骤 ②，所以顺序不能调换
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-B-001（无线鼠标）已处理完成，物流单号：SF20261022125133-0008
  └─ 订单处理完成 √

  ┌─ 开始处理订单 ORD-B-002（在线课程@lisi@example.com，￥299.00）
  │  处理器类型：VirtualProductOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  ② 支付：全额 ￥299.00，充值账号 lisi@example.com 已校验，流水号 VPAY20261022125133-0009
  │  ③ 交付：已生成下载链接
  │        └ https://dl.example.com/ord-b-002?token=VPAY20261022125133-0009
  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-B-002（在线课程@lisi@example.com）已处理完成，下载链接：https://dl.example.com/ord-b-002?token=VPAY20261022125133-0009
  └─ 订单处理完成 √

  ┌─ 开始处理订单 ORD-B-003（限量版手办预售，￥899.00）
  │  处理器类型：PreSaleOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  [钩子 beforePay] 定金校验通过，应付 ￥269.70
  │  ② 支付：定金 ￥269.70（3 成），流水号 PRE20261022125133-0010
  │        └ 尾款 ￥629.30 待到货后支付
  │  ③ 交付：已登记生产排期，预计到货日 2026-11-21
  │        └ 关联定金流水 PRE20261022125133-0010
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-B-003（限量版手办预售）已处理完成，预计到货日：2026-11-21
  │     + 追加提醒：尾款 ￥629.30 请于 2026-11-24 前支付，逾期订单取消且定金不退
  │     ↑ 这一行由子类追加，上一行由 super.notifyCustomer() 产生
  └─ 订单处理完成 √

  ----------------------------------------------------
      处理完成：3 / 3 笔成功
```

#### 三种订单在第 ① 步就分道扬镳了

| 订单 | 处理器 | 步骤 ① | 步骤 ② | 步骤 ③ |
|---|---|---|---|---|
| ORD-B-001 | `PhysicalProductOrder` | 检查库存 → 7 件 | 全额 ￥129.00 | 交付顺丰，运单号 |
| ORD-B-002 | `VirtualProductOrder` | **跳过** | 全额 ￥299.00（校验账号） | 生成下载链接 |
| ORD-B-003 | `PreSaleOrder` | **跳过** | **定金 ￥269.70** + 尾款 ￥629.30 | 登记排产，预计到货日 |

**但第 ④ 步的结构完全一致**：都是「尊敬的客户，您的订单 X（Y）已处理完成，Z：W」。
差别只在 `Z` 那四个字（物流单号 / 下载链接 / 预计到货日），以及预售多一句尾款提醒。

**这就是模板方法的形态：骨架一样，细节分叉。**

#### 验收算：预售订单的数字

```
899.00 × 0.30 = 269.70        ← 定金
899.00 − 269.70 = 629.30      ← 尾款
269.70 + 629.30 = 899.00      ✅ 加起来正好等于订单金额
```

**这三个数字必须能对上。** 如果 `depositOf()` 和 `notifyCustomer()` 里各算一遍定金，
就很容易出现「定金 269.70 + 尾款 629.00 = 898.70」这种**差几毛钱**的问题——
用户一定会发现，客服一定会收到工单。

#### 统计代码：只读 `isAborted()`，不看类型

```java
int success = 0;
for (int i = 0; i < orders.size(); i++) {
    OrderContext result = processors.get(i).processOrder(orders.get(i));
    if (!result.isAborted()) {
        success++;
    }
    System.out.println();
}
```

**这段代码对三种订单类型一视同仁。** 因为它只依赖于两个父类型的东西：

- `OrderProcessTemplate.processOrder(OrderContext)` —— 方法签名一致；
- `OrderContext.isAborted()` —— 结果读取方式一致。

**这正是「面向接口/父类编程」的具体收益**。如果 `OrderProcessTemplate` 不是 `public`（原项目的问题），
`List<OrderProcessTemplate>` 这行就编译不过，这段代码根本写不出来。

---

### 6.6 demo6：为什么模板方法必须是 final（反例演示）

#### 完整输出

```
=== 6. 为什么模板方法必须是 final（反例演示）===
  -- 正确的顺序（由 final 的模板方法保证）--
      ① 检查库存 → ② 支付 → ③ 发货 → ④ 通知

  -- 现在【故意】把顺序写反：先发货，再支付 --
      （这段代码能编译通过，因为 Client 和订单类在同一个包内，
        protected 方法对同包可见）

  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0011
  │        └ 关联支付流水 null  ← 这个值来自步骤 ②，所以顺序不能调换

      >>> 发货步骤里读取的「关联支付流水」是什么？
          ctx.getPaymentNo() = null
          ↑ 【null】！因为 pay() 还没执行。

      >>> 后果：物流公司收到一条「货已发出、支付流水号为 null」的记录，
          财务对账时这笔货不知道是谁付的钱。这是真实的资金风险。

  │  ② 支付：全额 ￥399.00，流水号 PAY20261022125133-0012
      现在再调 pay()：PAY20261022125133-0012
      → 但发货记录已经写进物流系统了，改不回来了。

  -- final 是怎么防止这件事的？--
      因为 processOrder() 是 public final，子类【无法重写】它，
      所以子类没有任何办法改变步骤顺序。下面这段代码编译不过：

          public class BadOrder extends OrderProcessTemplate {
              @Override
              public OrderContext processOrder(OrderContext ctx) {  // ← 编译错误
                  deliver(ctx);      // 先发货
                  pay(ctx);          // 再收款
                  return ctx;
              }
          }
      报错信息：cannot override the final method from OrderProcessTemplate

  -- 一个诚实的说明：protected 在同包内是可以被绕过的 --
      上面这段反例能跑起来，正是因为 Client 和订单类在同一个包里。
      Java 的 protected = 【同包可见 + 子类可见】，没有更严格的选项。
      生产项目里的应对方式：
        ① 把 Client（调用方）放在【不同的包】，protected 就真的访问不到了；
        ② 用模块系统（JPMS）只 exports 需要暴露的包；
        ③ 用 ArchUnit 写架构测试，禁止跨层调用步骤方法。
      本项目为了能演示反例，刻意把它们放在同一个包里。
```

#### 这段输出的三个层次

**第一层：`null` 是真的。**

`ctx.getPaymentNo() = null` 这一行是 `System.out.println` 打印的真实值，
不是文档里的假设。**整个「顺序不可调换」的论证，就建立在这个 `null` 上。**

**第二层：写进物流系统的记录改不回来了。**

补调 `pay()` 之后 `paymentNo` 有值了（`PAY...0012`），
但**发货记录已经用 `null` 发出去了**。这是一个不可逆的外部副作用。

> **这个细节很真实**：真实系统里「调用外部接口」是不可撤销的。
> 一旦发出，就只能发更正/冲销接口，而那张错误的记录会永远留在对方系统里。
> 这就是「流程顺序」为什么值得用 `final` 这种强约束来保护。

**第三层：`final` 让这件事在编译期就不可能发生。**

```java
public class BadOrder extends OrderProcessTemplate {
    @Override
    public OrderContext processOrder(OrderContext ctx) {  // ← 编译错误
```

```
error: processOrder(OrderContext) in BadOrder cannot override processOrder(OrderContext) in OrderProcessTemplate
  public OrderContext processOrder(OrderContext ctx) {
                     ^
  overridden method is final
```

**这个错误是在写代码的那一刻出现的**，不是在跑测试时、不是在上线后。
**越早发现的错误，修复成本越低**——编译期 < 单元测试 < 集成测试 < 线上。

#### 那为什么 demo6 还能跑起来？

因为 **`protected` 的可见范围是「同包 + 子类」**，而 `Client` 和订单类在**同一个包**里。

```java
// Client.java 在 com.patterns.behavioral.templatemethod 包
processor.deliver(ctx);    // ✅ 同包可见，编译通过
processor.pay(ctx);        // ✅ 同上
```

**这不是设计的失败，是 Java 语言的固有限制。** 生产项目的三种应对方式在输出里列了：

| 方案 | 做法 | 强度 | 代价 |
|---|---|---|---|
| ① 分包 | 调用方放在不同的包 | 中 | 需要规划包结构 |
| ② JPMS 模块系统 | 只 `exports` 需要暴露的包 | **强**（运行时强制） | 需要 `module-info.java`，生态兼容性成本 |
| ③ ArchUnit | 架构测试禁止跨层调用 | **强**（CI 拦截） | 引入一个测试依赖，需要写规则 |

**本项目选择了「不修」**，因为：

- 这是教学模式包的**刻意安排**——不演示一遍，读者永远理解不了「顺序错了会怎样」；
- 修了之后 demo6 就编译不过了，**整个核心论证就消失了**。

**但必须把这个取舍明确告诉读者**，而不是假装 `final` 能挡住一切。
这就是为什么这段输出里有一整段「一个诚实的说明」。

> **工程写作的一条原则：不要为了让结论显得漂亮而隐藏代价。**
> 读者迟早会自己发现 `protected` 同包可见——那时候他会怀疑你写的其他所有内容。

---

### 6.7 demo7：流程轨迹（可审计的执行记录）

#### 完整输出

```
=== 7. 流程轨迹（可审计的执行记录）===
  ┌─ 开始处理订单 ORD-T-001（智能手表，￥1299.00）
  │  处理器类型：PhysicalProductOrder
  │  ① 检查库存：可用 7 件
  │  ② 支付：全额 ￥1299.00，流水号 PAY20261022125133-0013
  │  ③ 发货：已交付顺丰，运单号 SF20261022125133-0014
  │        └ 关联支付流水 PAY20261022125133-0013  ← 这个值来自步骤 ②，所以顺序不能调换
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-T-001（智能手表）已处理完成，物流单号：SF20261022125133-0014
  └─ 订单处理完成 √

  -- 这笔订单的完整轨迹 --
      1. 流程启动，处理器 = PhysicalProductOrder
      2. ① 检查库存 → 可用 7 件
      3. ② 支付 → 全额 ￥1299.00，流水号 PAY20261022125133-0013
      4. ③ 发货 → 已交付顺丰，运单号 SF20261022125133-0014（重量 12.0kg，关联支付流水 PAY20261022125133-0013）
      5. ④ 通知客户 → 已发送（凭证类型：物流单号）
      6. 流程结束

  -- 对照：虚拟商品的轨迹（注意第 2 条）--
  ┌─ 开始处理订单 ORD-T-002（音乐会员年卡@music@example.com，￥168.00）
  │  处理器类型：VirtualProductOrder
  │  [钩子] 本类型商品无需检查库存，跳过步骤 ①
  │  ② 支付：全额 ￥168.00，充值账号 music@example.com 已校验，流水号 VPAY20261022125133-0015
  │  ③ 交付：已生成下载链接
  │        └ https://dl.example.com/ord-t-002?token=VPAY20261022125133-0015
  │        └ token 取自步骤 ② 的支付流水，防止链接被盗用
  │  ④ 通知客户：尊敬的客户，您的订单 ORD-T-002（音乐会员年卡@music@example.com）已处理完成，下载链接：https://dl.example.com/ord-t-002?token=VPAY20261022125133-0015
  └─ 订单处理完成 √

      1. 流程启动，处理器 = VirtualProductOrder
      2. 跳过库存检查（VirtualProductOrder.needsStockCheck() = false）
      3. ② 支付 → 全额 ￥168.00，账号 music@example.com 校验通过，流水号 VPAY20261022125133-0015
      4. ③ 交付 → 已生成下载链接（token 来自支付流水 VPAY20261022125133-0015）
      5. ④ 通知客户 → 已发送（凭证类型：下载链接）
      6. 流程结束

  >>> 第 2 条明确写着「跳过库存检查（needsStockCheck = false）」。
      审计时能一眼看出【这一步没做】，而不是【做了但没记录】。

  -- 轨迹是只读的 --
      [OK] trace.add() 抛 UnsupportedOperationException
           → getTrace() 返回的是 unmodifiableList 视图，
             外部无法篡改审计日志。
```

#### 两条轨迹的逐条对照

| # | 实物商品 | 虚拟商品 |
|---:|---|---|
| 1 | 流程启动，处理器 = `PhysicalProductOrder` | 流程启动，处理器 = `VirtualProductOrder` |
| 2 | **① 检查库存 → 可用 7 件** | **跳过库存检查（`needsStockCheck()` = false）** ← 关键差异 |
| 3 | ② 支付 → 全额 ￥1299.00，流水号 `PAY...0013` | ② 支付 → 全额 ￥168.00，账号 `music@example.com` 校验通过，流水号 `VPAY...0015` |
| 4 | ③ 发货 → 已交付顺丰，运单号 `SF...0014`（重量 12.0kg，关联支付流水 `PAY...0013`） | ③ 交付 → 已生成下载链接（token 来自支付流水 `VPAY...0015`） |
| 5 | ④ 通知客户 → 已发送（凭证类型：**物流单号**） | ④ 通知客户 → 已发送（凭证类型：**下载链接**） |
| 6 | 流程结束 | 流程结束 |

**第 2 条是全表最重要的一行。**

如果 `needsStockCheck()` 返回 `false` 时**什么都不记录**，那轨迹里就只有 5 条（1、3、4、5、6），
审计人员看到的时候会想：

> 「第 2 条怎么不见了？是流程实现有问题，还是这个订单不需要检查？」

**「没有记录」和「记录了没做」是完全不同的信息。**

| 情形 | 轨迹里的表现 | 审计结论 |
|---|---|---|
| 检查了，库存充足 | `① 检查库存 → 可用 7 件` | 合格 |
| 检查了，库存不足 | `① 检查库存 → 可用 0 件` + `【中止】库存不足...` | 合规拒单 |
| **压根没检查** | `跳过库存检查（needsStockCheck() = false）` | **免检，制度允许** |
| 忘了检查（bug） | **什么都没有** | **流程缺陷** |

**第 3 行和第 4 行在轨迹里的差别，就是「有记录」和「没记录」的差别。**
有了明确的「跳过」记录，最后一行那种情况就能被立刻识别出来。

#### 轨迹的第三条：`④+ 追加尾款提醒`

demo3 的轨迹里会出现这一条：

```
4. ③ 交付 → 已登记生产排期，预计到货 2026-11-21（关联定金流水 PRE...0006）
5. ④ 通知客户 → 已发送（凭证类型：预计到货日）
6. ④+ 追加尾款提醒 → 尾款 ￥4899.30 请于 2026-11-24 前支付，逾期订单取消且定金不退
7. 流程结束
```

**`④+` 这个前缀是子类自己加的**（`PreSaleOrder.notifyCustomer()` 里 `record("④+ 追加尾款提醒 → " + extra)`）。

它传达的信息是：**这一步不在父类定义的骨架里，是子类在步骤 ④ 之后追加的**。

> **审计日志里，区分「框架做的」和「业务代码做的」同样重要。**
> 出问题时，前者查框架版本，后者查业务提交记录——排查方向完全不同。

#### 最后一行：轨迹只读性的验证

```java
try {
    trace.add("伪造一条记录");
    System.out.println("      [FAIL] 居然添加成功了？这说明封装有问题");
} catch (UnsupportedOperationException e) {
    System.out.println("      [OK] trace.add() 抛 UnsupportedOperationException");
}
```

**这段代码是「防御性设计」的正面示例**：

- 它**主动尝试**破坏封装，而不是假设封装有效；
- 失败时打印 `[FAIL]`，而不是静默通过；
- 捕获的是**具体的** `UnsupportedOperationException`，不是笼统的 `Exception`。

实测输出 `[OK]`，说明 `Collections.unmodifiableList()` 生效了。

> **注意这段代码写在 `Client`（Demo）里，而不是 `OrderContext` 里。**
> 这是正确的——**`OrderContext` 不需要「验证自己封装得好不好」，
> 那是测试代码的职责**。
> 把自检代码写进业务类里，是另一种形式的设计污染。

---

### 6.8 全流程的对象生命周期

把七段 demo 合起来看，一个 `OrderContext` 从生到死经历了什么：

```
new OrderContext(no, name, amount)
    │
    ├─ 三条构造校验（orderNo/productName 非空、amount 非负）
    │      └─ 失败 → IllegalArgumentException（程序错误，不是业务分支）
    │
    └─ 状态：orderNo/productName/amount 已设置（final）
              availableStock = -1, paymentNo = null,
              deliveryProof = null, notificationContent = null,
              aborted = false, trace = []
    │
    ▼
processOrder(ctx)
    │
    ├─ record("流程启动，处理器 = X")
    │
    ├─ [needsStockCheck() == true]
    │      checkStock(ctx) → availableStock = N
    │      N <= 0 → abort(...) → return ctx   ← 出口 A（demo4-A）
    │
    ├─ [needsStockCheck() == false]
    │      record("跳过库存检查（...）")
    │
    ├─ beforePay(ctx)
    │      子类可能 abort(...) → isAborted → return ctx   ← 出口 B（demo4-C）
    │
    ├─ pay(ctx)
    │      子类可能 abort(...) → 检查 1 → return ctx       ← 出口 C（demo4-B）
    │      子类忘了 setPaymentNo → 检查 2 → return ctx     ← 出口 D（防御性，demo 未触发）
    │
    ├─ deliver(ctx)      ← 此时 paymentNo 保证非 null
    │
    ├─ notifyCustomer(ctx)
    │
    ├─ record("流程结束")
    └─ return ctx        ← 出口 E（正常完成）
    │
    ▼
调用方读结果：getPaymentNo() / getDeliveryProof() / isAborted() / getAbortReason() / getTrace()
```

**五个出口，每一个都有明确的语义：**

| 出口 | 触发条件 | `aborted` | `paymentNo` | `deliveryProof` |
|---|---|:---:|---|---|
| A | 库存不足 | `true` | `null` | `null` |
| B | 钩子 `beforePay()` 中 abort | `true` | `null` | `null` |
| C | `pay()` 中 abort | `true` | `null` | `null` |
| D | `pay()` 没写 `paymentNo` | `true` | `null` | `null` |
| E | 正常完成 | `false` | 有值 | 有值 |

**出口 D 是一个防御性分支，本项目所有 demo 都没有触发它**——
因为三个子类都正确调用了 `setPaymentNo()`。

> **这是一个诚实的说明：出口 D 未经运行验证。**
> 但我保留它，理由有两个：
> ① 它对应一个真实的编码错误（子类忘写 `setPaymentNo`），
>    而 **`abstract` 方法无法强制子类写某个字段**——只能靠运行时检查；
> ② 它的实现只有 4 行，且逻辑极其简单（判 null + abort + return）。
>
> **如果要验证它，方法是写一个故意不调 `setPaymentNo()` 的子类**，
> 但那样的子类**不该出现在教学代码里**——会误导读者以为这是正常写法。
> **取此舍彼，是文档里说明清楚，而不是假装它验证过。**

---

## 7. 扩展对比：新需求来了改哪里

这一节用**五个真实的新需求**，检验这套设计到底扛不扛得住。
每个需求都会给出「改哪些文件、改多少行」，以及「为什么这样改是对的」。

**判断标准只有一条**：**能不能只加新类，不改旧类。**

---

### 7.1 场景一：新增「海外直邮订单」

**需求**：新增一种订单，商品从海外仓发货，需要报关，交付凭证是「国际运单号 + 预计清关天数」。

#### 改动清单

| 文件 | 改动 |
|---|---|
| `OverseasOrder.java` | **新建**（约 80 行含注释） |
| `Client.java` | 加一段演示 |
| `OrderProcessTemplate.java` | **0 改动** |
| `OrderContext.java` | **0 改动** |
| 其他三个子类 | **0 改动** |

#### 实现

```java
public class OverseasOrder extends OrderProcessTemplate {

    @Override
    protected void pay(OrderContext ctx) {
        // 海外订单必须全额预付（不支持货到付款）
        BigDecimal paid = ctx.getAmount().setScale(2, RoundingMode.HALF_UP);
        String paymentNo = generateSerialNo("OPAY");
        ctx.setPaymentNo(paymentNo);
        ctx.record("② 支付 → 全额预付 ￥" + paid.toPlainString() + "，流水号 " + paymentNo);
        System.out.println("  │  ② 支付：全额预付 ￥" + paid.toPlainString()
                + "，流水号 " + paymentNo);
    }

    @Override
    protected void deliver(OrderContext ctx) {
        // 海外订单特有的：报关 + 国际物流
        String customsNo = generateSerialNo("CUS");
        String trackingNo = generateSerialNo("INTL");
        String proof = trackingNo + "（报关单 " + customsNo + "，预计清关 7 天）";

        ctx.setDeliveryProof(proof);
        ctx.record("③ 发货 → 已报关（" + customsNo + "）并交付国际物流，运单号 " + trackingNo);
        System.out.println("  │  ③ 发货：已报关，国际运单号 " + trackingNo);
        System.out.println("  │        └ 报关单 " + customsNo + "，关联支付流水 " + ctx.getPaymentNo());
    }

    @Override
    protected String deliveryProofLabel() {
        return "国际运单号";
    }

    // needsStockCheck() 不重写 → 默认 true，海外仓也有库存
    // beforePay() 不重写 → 空操作
}
```

#### 为什么这个扩展是「干净」的

- **`OrderProcessTemplate` 一行都不用改** —— 因为父类不认识任何子类；
- **`OrderContext` 一行都不用改** —— 因为上下文里已经有它需要的全部字段（`paymentNo`、`deliveryProof`）；
- **其他三个子类一行都不用改** —— 因为它们和 `OverseasOrder` 互不依赖。

**如果父类里写了 `instanceof` 判断，这个扩展就要改父类了**（见 [5.5 节](#55-presaleorderjava264-行四种角色全用上)）。

#### 一个值得注意的点

`OverseasOrder` 用 `generateSerialNo()` 生成了**两个**流水号（报关单 + 运单号）。
这完全没问题——`sequence` 是静态的，谁调都行。

> **父类提供的工具方法不应该假设「每个子类只调一次」。**
> 如果 `generateSerialNo()` 里加了「同一个前缀只能调一次」之类的限制，
> `OverseasOrder` 就会被卡住。**工具方法要尽量无状态、无假设。**

---

### 7.2 场景二：新增「全流程风控校验」步骤

**需求**：所有订单在支付前都必须做风控评分，评分低于 60 分则拒绝。

**注意：这是一个「加步骤」的需求，不是「加类型」的需求。**
模板方法模式对这种需求的响应是——**要改父类**。

#### 改动清单

| 文件 | 改动 |
|---|---|
| `OrderProcessTemplate.java` | **改**（加一步） |
| `OrderContext.java` | **改**（加一个字段 `riskScore`） |
| 四个子类 | **0 改动**（除非需要自定义风控逻辑） |
| `Client.java` | 加演示 |

#### 实现

```java
// ① OrderContext 加字段
private int riskScore = -1;         // -1 表示未评分

public int getRiskScore() { return riskScore; }
public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

// ② 父类加一步（在 beforePay 之前）
protected void assessRisk(OrderContext ctx) {
    // 模拟风控：金额越大分越低
    int score = ctx.getAmount().compareTo(new BigDecimal("10000")) > 0 ? 30 : 85;
    ctx.setRiskScore(score);
    ctx.record("【风控】评分 " + score);
    System.out.println("  │  [风控] 评分 " + score);
}

// ③ 模板方法里插入
assessRisk(ctx);
if (ctx.getRiskScore() < 60) {
    ctx.abort("风控拒绝（评分 " + ctx.getRiskScore() + " < 60）");
    System.out.println("  └─ 流程中止：" + ctx.getAbortReason());
    return ctx;
}
```

#### 这个扩展「脏」在哪？——必须诚实地说

**这是模板方法模式的固有代价**：新增一个**通用步骤**，必须改父类。

| | 加类型（场景一） | 加步骤（场景二） |
|---|---|---|
| 改父类吗 | ❌ 不用 | ✅ **必须改** |
| 影响已有子类吗 | ❌ 不影响 | ⚠️ **全部受影响**（回归测试要全跑） |
| 符合开闭原则吗 | ✅ 符合 | ❌ **不完全符合** |

**但这是可以接受的吗？** 在多数情况下**是**，因为：

1. **「加步骤」是低频事件**。一个业务流程的骨架通常在需求分析阶段就定下来了，
   后续变更 90% 是「加类型」而不是「加步骤」；
2. **改父类时编译器会帮你检查**。如果新增的是 `abstract` 方法，所有子类立刻编译报错——
   **不会有「漏改某个子类」的风险**；
3. **这正是模板方法模式的定位**：它保护的是「流程不变、细节可变」，
   而不是「流程也能随便变」。**如果流程真的频繁变，就不该用模板方法**（见 [3.3 节](#33-什么时候不该用模板方法模式)）。

> **如果「加步骤」是高频需求，正确做法是换架构**——
> 责任链模式（把每步做成可插拔的处理器）或者工作流引擎。
> **不要指望一个模式解决所有变化方向。**

---

### 7.3 场景三：新增「审核不通过时回滚已扣款项」

**需求**：支付成功但后续步骤失败时，要自动退款。

#### 问题出在哪

当前设计**只支持「前向中止」，不支持「回滚」**：

```java
if (ctx.getAvailableStock() <= 0) {
    ctx.abort(...);
    return ctx;                     // ← 直接返回，之前做的操作不会撤销
}
```

在**当前的四步流程里这没问题**，因为「失败点都在支付之前或之后没有更多扣款动作」。
但如果要支持回滚，就需要记录「已经做了什么」，然后逆序撤销。

#### 两个层次的解法

**层次一：加法（不推荐但最简单）—— 加一个 `compensate()` 方法**

```java
protected void compensate(OrderContext ctx) {
    if (ctx.getPaymentNo() != null && !ctx.isDelivered()) {
        // 已支付但未发货 → 退款
        System.out.println("  │  [补偿] 退款 " + ctx.getPaymentNo());
        ctx.record("【补偿】已退款（流水 " + ctx.getPaymentNo() + "）");
    }
}
```

在模板方法的每个 `return ctx` 之前调用它。

**层次二：换模式（推荐）—— 这就是 Saga 模式**

> **「每个正向操作都有一个对应的补偿操作，失败时逆序执行补偿」**
> 这是分布式事务的标准解法，叫 **Saga 模式**。

```java
interface Step {
    void execute(OrderContext ctx);
    void compensate(OrderContext ctx);
}

class ProcessSaga {
    private final List<Step> steps = List.of(...);
    private final Deque<Step> executed = new ArrayDeque<>();

    void run(OrderContext ctx) {
        for (Step step : steps) {
            try {
                step.execute(ctx);
                executed.push(step);
            } catch (Exception e) {
                // 逆序补偿
                while (!executed.isEmpty()) {
                    executed.pop().compensate(ctx);
                }
                throw e;
            }
        }
    }
}
```

**注意这里已经不再是模板方法模式了**：

| | 模板方法 | Saga |
|---|---|---|
| 步骤组织 | **继承**（编译期固定） | **组合**（列表可配置） |
| 回滚 | 不支持 | **核心能力** |
| 适合 | 流程简单、失败即终止 | 跨服务、需要最终一致性 |

> **模式的边界要认清：模板方法解决的是「骨架固定 + 细节可变」，
> 不是「事务一致性」。** 需要 Saga 时就用 Saga，别硬套模板方法。

---

### 7.4 场景四：让调用方自定义通知渠道（短信 / 邮件 / 企微）

**需求**：`notifyCustomer()` 现在是 `println`，要真正发出去，且渠道可配置。

#### 关键判断：这是「类型差异」还是「实例差异」？

| | 类型差异 | 实例差异 |
|---|---|---|
| 定义 | 同一种订单类型的所有实例都一样 | 同一个类型的不同实例可能不同 |
| 例子 | 实物订单永远用「物流单号」这个称呼 | 这个客户希望收短信，那个客户希望收邮件 |
| **表达方式** | **重写方法/钩子** | **构造参数/依赖注入** |

**通知渠道是「实例差异」**——同一个实物订单，A 客户要短信、B 客户要邮件。
**所以不能用钩子，要用组合。**

#### 实现：把通知渠道抽成接口

```java
/** 通知渠道（这是策略模式的用法，不是模板方法） */
public interface NotifyChannel {
    void send(String orderNo, String content);
}

public class SmsChannel implements NotifyChannel {
    public void send(String orderNo, String content) {
        System.out.println("  │  ④ [短信] 发给 " + orderNo + "：" + content);
    }
}

public class EmailChannel implements NotifyChannel {
    public void send(String orderNo, String content) {
        System.out.println("  │  ④ [邮件] 发给 " + orderNo + "：" + content);
    }
}
```

父类改成**依赖注入**：

```java
public abstract class OrderProcessTemplate {
    private final NotifyChannel notifyChannel;

    protected OrderProcessTemplate(NotifyChannel notifyChannel) {
        this.notifyChannel = notifyChannel;
    }

    protected void notifyCustomer(OrderContext ctx) {
        String content = "尊敬的客户，...";
        ctx.setNotificationContent(content);
        notifyChannel.send(ctx.getOrderNo(), content);      // ← 委托出去
        // ...
    }
}
```

#### 这里同时用了两个模式

```java
// 组合：渠道是可替换的策略
new PhysicalProductOrder(new SmsChannel())        // 短信
new PhysicalProductOrder(new EmailChannel())      // 邮件
```

| 变化维度 | 用什么模式 | 怎么表达 |
|---|---|---|
| **凭证称呼**（物流单号 / 下载链接 / 预计到货日） | **模板方法**（钩子） | 子类重写 `deliveryProofLabel()` |
| **通知渠道**（短信 / 邮件 / 企微） | **策略**（组合） | 构造器注入 `NotifyChannel` |

**这是本项目和策略模式项目的交叉点**：
`../strategy/` 目录里有完整的策略模式实现，可以对照阅读。

> **判断「用继承还是用组合」的基本准则：**
> 「**类型**差异用继承（重写方法），**实例**差异用组合（注入参数）。」
> `deliveryProofLabel()` 是类型差异（预售价永远报日期），
> `NotifyChannel` 是实例差异（同类型不同客户可能不同）。
>
> **搞混这一条，就会写出「为每个客户建一个子类」的灾难。**

---

### 7.5 场景五：给每笔订单加上处理耗时统计

**需求**：记录每个步骤耗时，超过 1 秒的步骤打警告日志。

#### 这个需求揭示了模板方法的一个「天然优势」

**因为所有步骤的调用都集中在模板方法里，加埋点只需要改一个地方。**

```java
// ❌ 如果按 3.1 那种写法（每个类型一个完整方法），要在 3 × 4 = 12 处加埋点
// ✅ 在模板方法里加，只需要改一次

private void step(String name, Runnable action) {
    long start = System.nanoTime();
    action.run();
    long ms = (System.nanoTime() - start) / 1_000_000;
    System.out.println("  │  [耗时] " + name + "：" + ms + "ms"
            + (ms > 1000 ? "  ← 超过 1 秒！" : ""));
}

// 调用处
step("① 检查库存", () -> checkStock(ctx));
step("② 支付",     () -> pay(ctx));
step("③ 发货",     () -> deliver(ctx));
step("④ 通知客户", () -> notifyCustomer(ctx));
```

**但这里有一个真实的坑，必须指出来：**

> **`pay()` 和 `notifyCustomer()` 内部可能调用 `ctx.abort()`，而 `abort()` 不会中断执行。**
> 用 `Runnable` 包起来之后，「中止后 return」的逻辑仍然在被包裹的方法内部正常执行，
> 所以**行为不变**——这是安全的。
>
> **但如果某个步骤是通过抛异常中止的**（本项目刻意避免了这种设计），
> 那 `step()` 里就必须 `try-finally`，否则耗时会漏记。
> **这就是第 4.3 节「用状态标志而非异常表达业务中止」的又一个收益。**

#### 这个改动符合开闭原则吗？

**不符合。** 它改了父类。

但它**不属于「业务变化」**，而属于「**横切关注点**」——耗时统计、日志、权限、缓存这类
「和业务无关但到处都要」的需求。

> **开闭原则的本意不是「任何改动都不能有」，而是「不要因为业务变化去改已有代码」。**
> 加埋点、加日志、加监控这类横切需求，改父类是正确的做法——
> **因为模板方法本来就是这些横切逻辑的天然收敛点。**
>
> **反过来说：如果你发现自己在每个子类里都加了一遍日志，那说明你漏用了模板方法。**

---

### 7.6 五个场景的汇总

| 场景 | 变化方向 | 改父类？ | 符合开闭原则？ | 说明 |
|---|---|:---:|:---:|---|
| ① 加海外直邮订单 | **加类型** | ❌ 不改 | ✅ **完全符合** | 模板方法的强项 |
| ② 加风控步骤 | **加步骤** | ✅ 改 | ❌ 不符合 | 固有代价，低频可接受 |
| ③ 加事务回滚 | **改机制** | ✅ 大改 | ❌ 不符合 | **应该换 Saga 模式** |
| ④ 通知渠道可配置 | **实例差异** | ✅ 小改 | 部分 | **继承 + 组合混用** |
| ⑤ 加耗时埋点 | **横切关注点** | ✅ 改 | — | 模板方法的天然优势 |

**这张表是本模式最重要的实用结论：**

> **模板方法最适合「加类型」，不适合「加步骤」，完全不适合「改机制」。**
>
> 拿它去应对「加步骤/改机制」，会得到一堆「父类里塞满 if-else」的代码——
> 那已经退化成 3.1 节那种反面教材了，只是套了一层继承的壳。

---

## 8. 优缺点与常见误区

### 8.1 优点（按实际收益排序，不按书本顺序）

#### 优点 1：把「必须做对的事」变成「无法做错的事」

**这是本模式最被低估的价值。** 它不只是「消除重复」，而是**提供强制力**。

| 约束 | 强制手段 | 违反的后果 |
|---|---|---|
| 步骤顺序不可改 | `final` | **编译错误** |
| 子类必须实现关键步骤 | `abstract` | **编译错误** |
| 外部不能单独调用某一步 | `protected` | **编译错误**（跨包时） |
| 库存不足不能继续收款 | 模板方法里的 `if + return` | 运行时拦截 |
| 支付无流水号不能发货 | 防御性检查 | 运行时拦截 + 明确报错 |

**五条约束里，三条是编译期强制的。** 这和「写在文档里呼吁大家遵守」有本质区别——
**文档会被忽略，编译器不会。**

`JdbcTemplate` 是最有说服力的例子：原生 JDBC 的 `try-finally-close` 样板代码
**忘了 close 就是连接池泄漏**。`JdbcTemplate` 把它固定在模板里，
**你连「忘记」的机会都没有**。

#### 优点 2：公共逻辑只写一次，且不可能不一致

本项目的实测数据：

| 文件 | 总行数 | 可执行代码 | 说明 |
|---|---:|---:|---|
| `OrderProcessTemplate.java` | 526 | **79** | 三个子类共享 |
| `PhysicalProductOrder.java` | 143 | **37** | 最省事的子类 |
| `VirtualProductOrder.java` | 207 | **48** | 重写两个钩子 |
| `PreSaleOrder.java` | 264 | **74** | 四种角色全用 |

`PhysicalProductOrder` **只有 37 行代码**，却获得了六项能力：
库存检查、失败短路、流水号生成、审计轨迹、通知文案拼装、支付流水号防御性校验。

**这六项全部在父类的 79 行里，三个子类共享。**
如果按「每个类型写一个完整方法」的写法，这 79 行要抄三遍，且**必然逐渐不一致**。

#### 优点 3：变化点集中，扩展时不碰旧代码

**「加类型」是模板方法的强项**（见 [7.1](#71-场景一新增海外直邮订单)）：

| 加 `PreSaleOrder` 时 | 改动 |
|---|---|
| `PreSaleOrder.java` | 新建 |
| `OrderProcessTemplate.java` | **0 改动** |
| 两个已有子类 | **0 改动** |

**已有代码零改动 = 行为在数学上不可能变化 = 不用重跑它们的回归测试 = 风险可控。**

#### 优点 4：框架作者控制流程，使用者只填细节

这是**框架设计的核心需求**。Spring 的 `AbstractApplicationContext.refresh()` 有 12 步、
Servlet 的 `service()` 要先解析 HTTP 方法、
JUnit 的执行器要先跑 `@BeforeEach`——**这些顺序都不允许插件作者改。**

**模板方法就是「框架」和「插件」之间的标准契约形式。**

---

### 8.2 缺点（按严重程度排序）

#### 缺点 1：继承是强耦合，父类改动波及全部子类

**这是本模式最大的代价，也是现代 Java 偏好组合而非继承的根本原因。**

```java
// 父类改一行
protected void notifyCustomer(OrderContext ctx) {
    String content = "尊敬的客户，..." + deliveryProofLabel() + "：" + ctx.getDeliveryProof();
    // 改成：+ "【" + getClass().getSimpleName() + "】" +
}
// 四个子类的行为全部变化，全部需要重新测试
```

这也叫**「脆弱基类问题」（Fragile Base Class Problem）**：

> 子类必须知道父类的**实现细节**才能正确重写。
> 父类改了内部实现（哪怕签名没变），子类可能就坏了。

**具体表现**：

- 子类重写 `notifyCustomer()` 时，必须知道父类会写入 `ctx.notificationContent`——
  这是**实现细节**，不是签名；
- 如果父类某天改成「不再写入 `notificationContent`，直接发送」，
  `PreSaleOrder` 的 `super + 追加` 就**静默失效**了（不报错，但尾款提醒没了）。

**缓解办法**：

| 办法 | 做法 |
|---|---|
| 契约文档化 | 在 `abstract` 方法 Javadoc 里写明「必须调 `setPaymentNo`」等契约（本项目已做） |
| 上下文对象代替字段 | 所有数据交互都走 `OrderContext` 的公开 API，而不是父类的 protected 字段（本项目已做） |
| 防御性检查 | 模板方法在关键点检查 `paymentNo` 非 null（本项目已做） |
| 减少继承层次 | 最多两层，不要 `A extends B extends C extends D` |

#### 缺点 2：Java 单继承，占用了唯一的继承额度

```java
// ❌ 编译不过：类不能继承多个父类
public class MyOrder extends OrderProcessTemplate, SomeFrameworkBase {
}
```

**如果这个类还需要继承框架基类，模板方法就用不了了。**

**替代方案**：把流程编排抽成独立对象，用**组合 + 回调**：

```java
public class OrderProcessor {
    public void process(OrderContext ctx,
                        BiConsumer<OrderContext, Runnable> stockCheck,
                        Consumer<OrderContext> pay,
                        Consumer<OrderContext> deliver) { ... }
}
```

或者用 lambda 注入（更现代）：

```java
new OrderProcessor(
    ctx -> checkStock(ctx),
    ctx -> pay(ctx),
    ctx -> deliver(ctx)
).process(ctx);
```

**代价**：失去了 `final` 的强制力（调用方可以按任意顺序调这些 lambda）。
**所以这是「灵活性 vs 强制力」的取舍**，不是「新写法一定更好」。

#### 缺点 3：子类数量会膨胀

**每种「步骤组合」都需要一个子类。** 如果有 3 种支付方式 × 3 种交付方式 × 2 种通知方式
= **18 种组合**，按模板方法就要写 18 个子类。

**这是「用继承表达组合」的固有问题。**

**解法**：把「组合维度」抽成**策略**注入，只让「真正的类型差异」用子类表达。
见 [7.4 节](#74-场景四让调用方自定义通知渠道短信--邮件--企微) 的例子——
通知渠道用策略注入，凭证称呼用钩子重写。

> **判断标准：如果两个子类之间的差异，可以描述成「参数不同」而不是「行为不同」，
> 那就不该建子类。**

#### 缺点 4：流程固定，运行时无法改变

```java
OrderProcessTemplate processor = new PhysicalProductOrder();
// 无法在运行时把它变成 PreSaleOrder 的处理方式
```

**这是和策略模式的根本差异**（见 [8.3](#83-与相似模式的对比)）。

如果业务需要「运营在后台配置流程」「A/B 测试两种流程」，
**模板方法就完全不适用**——流程在编译期就固定了。

#### 缺点 5：`protected` 不是真正的「只给子类」

**这是 Java 语言级的限制**（本项目在 demo6 里亲身体验过）：

```java
// Client 和订单类同包时
processor.deliver(ctx);    // ✅ 编译通过！绕过了整个流程
```

`protected` = 同包可见 + 子类可见，**没有更严格的选项**。

**应对**：分包 / JPMS / ArchUnit（见 [4.7 ④](#-但-protected-有个真实的漏洞必须诚实说明)）。

---

### 8.3 与相似模式的对比

#### 模板方法 vs 策略模式（最容易混淆的一对）

**两者解决同一个问题**：「有一族相似的操作，某些部分不同」。
**但手段完全相反。**

| | 模板方法 | 策略模式 |
|---|---|---|
| **复用手段** | **继承**（子类重写步骤） | **组合**（Context 持有策略对象） |
| **绑定时机** | 编译期，对象创建后**无法更换** | 运行期，**随时可换** |
| **变化粒度** | 算法中的**某几步** | **整个算法** |
| **流程控制权** | 在**父类**手里 | 在**调用方**手里 |
| **代码复用方向** | 子类**复用**父类的公共步骤 | 策略之间**完全不共用**代码 |
| **典型场景** | 流程固定、只有细节因类型而异 | 算法本身可互换、可配置 |
| **本项目/兄弟项目** | `templatemethod/`（本目录） | `../strategy/` |

**用具体数字对比两个项目的实际情况：**

| | 模板方法项目 | 策略模式项目 |
|---|---|---|
| 三种实现之间共用多少 | **共用 2 个完整步骤**（`checkStock`、`notifyCustomer`）+ 全部流程编排 | **共用 0 行**，每个策略从头算到尾 |
| 新增一种实现要写多少 | 37~74 行（其余从父类继承） | 约 40 行（完整独立） |
| 能否运行时切换 | ❌ 不能 | ✅ `withStrategy()` 可换 |
| 能否遍历比较 | ❌ 无意义 | ✅ `bestOf()` 自动选最优 |

**典型例子对比：**

```java
// 模板方法：流程固定，只有支付/交付因类型而异
processor.processOrder(ctx);              // 内部顺序不可改

// 策略模式：算法整体可换、可比、可选
calculator.calculate(500);                // 用当前策略
calculator.withStrategy(vip).calculate(500);   // 换一个策略
calculator.bestOf(500, candidates);       // 全部跑一遍选最优
```

**怎么判断该用哪个？问自己两个问题：**

1. **「顺序是业务强制的吗？」** 是 → 模板方法；不是 → 可能用策略；
2. **「这些实现之间有没有可以复用的公共步骤？」** 有 → 模板方法；没有 → 策略。

**本项目为什么选模板方法：**
「检查库存 → 支付 → 发货 → 通知」这个顺序**是业务强制的**，
不存在「某个客户希望先发货再付款」的需求。流程固定 + 细节可变 = 模板方法的适用场景。

**反过来说**：如果折扣算法要能被运营在后台自由配置、能运行时切换、能遍历比较，
那就**必须**用策略模式（见 `../strategy/README.md`）。

#### 模板方法 vs 工厂方法

**这两个模式经常一起出现，甚至互相嵌套。**

| | 模板方法 | 工厂方法 |
|---|---|---|
| 解决的问题 | 「流程的某些**步骤**由子类实现」 | 「**创建对象**这一步由子类决定」 |
| 关键词 | `final` 骨架 + `abstract` 步骤 | `abstract Product createProduct()` |
| 关系 | **工厂方法可以就是模板方法里的一个步骤** | — |

**典型案例**：`javax.servlet.http.HttpServlet` 里其实两个都有：

```java
// HttpServlet 的 service() 是模板方法
protected void service(HttpServletRequest req, HttpServletResponse resp) {
    // ...
    doGet(req, resp);       // ← 这是模板方法里的"步骤"
}

// GenericServlet 里
public abstract ServletConfig getServletConfig();   // ← 这更像工厂方法
```

**更典型的嵌套案例**（Spring）：

```java
public abstract class AbstractApplicationContext {
    // refresh() 是模板方法
    public void refresh() {
        // ...
        obtainFreshBeanFactory();              // ← 内部调用 createBeanFactory()
        // ...
    }

    // createBeanFactory() 是工厂方法
    protected abstract BeanFactory createBeanFactory();   // ← 让子类决定创建哪种 BeanFactory
}
```

> **「工厂方法」本质上就是「专门用来创建对象的模板方法步骤」。**
> 所以这两个模式不是互斥的，而是**同一套思路在不同粒度上的应用**。

#### 模板方法 vs 责任链

| | 模板方法 | 责任链 |
|---|---|---|
| 步骤数量 | **编译期固定** | 运行时可变（链可增删） |
| 步骤顺序 | **编译期固定** | 运行时组装 |
| 谁来终止 | 父类的模板方法 | 每个处理器自己决定「继续传递」还是「中断」 |
| 适合 | 流程稳定的业务 | 流程需要动态配置的业务 |

**本项目 `OrderProcessTemplate.processOrder()` 如果用责任链改写：**

```java
// 责任链版本
Chain chain = new Chain(
    new StockCheckHandler(),
    new RiskCheckHandler(),
    new PayHandler(),
    new DeliverHandler(),
    new NotifyHandler()
);
chain.handle(ctx);
```

**对比**：

| 维度 | 模板方法 | 责任链 |
|---|---|---|
| 加步骤 | **改父类** | 新增一个 Handler，插进链里 |
| 顺序保证 | **编译期强制**（`final`） | ❌ 运行时组装，可能装错 |
| 步骤间数据流 | 编译器/模板方法保证（`paymentNo` 非 null） | 每个 Handler 自己判断 |
| 适合 | 流程稳定 + 顺序重要 | 流程频繁变化 + 需要插件化 |

> **本项目选择模板方法，核心原因是「顺序重要」。**
> 在支付领域，「先发货后收款」是**必须被编译器挡住**的错误。
> 责任链的灵活性在这里是**负担**——它允许装错顺序。

---

### 8.4 九个常见误区

#### 误区 1：「模板方法就是 abstract class」——漏掉了最重要的钩子

**错。** 只有 `abstract` 方法的类，只是「抽象类」，不是模板方法模式。

模板方法模式**必须**有：
- 一个**具体方法**（模板方法本身）**调用**其他方法；
- 其他方法里有子类要实现的（抽象方法或可重写的具体方法）。

**加上钩子才完整。** 原项目有前三者，缺钩子，结果虚拟商品被迫「检查库存」（见 2.4）。

#### 误区 2：「模板方法不用加 final，靠约定就行」

**错，而且这是最危险的一个误区。**

```java
public void processOrder(OrderContext ctx) {    // ← 少了 final
    // ...
}
```

子类可以重写它，把顺序改掉。**编译器不会报任何错。**

> **模式的价值全部建立在「骨架不可篡改」上。**
> 少了 `final`，这个类就不再是模板方法，只是一组继承来的辅助方法。

**什么情况下可以不 `final`？**
只有在「你把父类当作可扩展的基类，而不是模板」的时候。
但那就不是模板方法模式了——**别把两种情况混着叫。**

#### 误区 3：把「钩子」当成「抽象方法」

**这是初学者最常犯的错误。**

| | 抽象方法 | 钩子方法 |
|---|---|---|
| 子类不实现 | **编译不过** | **正常运行** |
| 语义 | 「你必须做」 | 「你可以不做 / 可以不这样」 |
| 举例 | `pay()`、`deliver()` | `needsStockCheck()`、`beforePay()` |

**判断标准：如果所有子类都要写同一个实现，那它就该是具体方法；如果一个子类可以什么都不写，那它才该是钩子。**

**反例**：把 `checkStock()` 写成 `abstract`，逼着虚拟商品和预售订单
都写一个空方法或者一份相同的查询代码——**这就是把钩子当抽象方法用的后果**。

#### 误区 4：用 `instanceof` 在父类里区分兄弟子类

**这是把模板方法的优势全部扔掉的写法。**

```java
// ❌ 反模式
protected void notifyCustomer(OrderContext ctx) {
    String label;
    if (this instanceof VirtualProductOrder) label = "下载链接";
    else if (this instanceof PreSaleOrder) label = "预计到货日";
    else label = "物流单号";
}
```

**三个致命问题**（详见 [5.4 节](#54-virtualproductorderjava207-行重写钩子的示范)）：
父类依赖子类（违反 DIP）、新增类型必须改父类（违反 OCP）、多层继承时判断出错。

**正确做法**：父类定义钩子，子类重写。

#### 误区 5：以为「钩子调用位置」无关紧要

**这是本项目踩过的真实坑**（见 [4.5 节](#45-问题四钩子应该由谁调用一个真实的设计缺陷修复)）。

```java
// ❌ 让子类自己调钩子
protected void pay(OrderContext ctx) {
    beforePay(ctx);       // ← 靠每个子类「记得写这一行」
    // ...
}

// ✅ 由模板方法调用
public final OrderContext processOrder(OrderContext ctx) {
    beforePay(ctx);       // ← 框架保证调用，子类不可能忘
    if (ctx.isAborted()) return ctx;
    pay(ctx);
    // ...
}
```

**差别是「可靠性」**：前者靠开发者自觉（会忘、无法发现），后者靠框架保证（不可能忘）。

#### 误区 6：以为「模板方法只能处理流程，不能有返回值」

**错。** 原项目把 `processOrder()` 写成 `void`，导致调用方拿不到任何结果。

**正确做法**：返回一个结果对象（本项目返回 `OrderContext`），让调用方能读到全流程产出。

```java
// ❌ 原项目
public final void processOrder() { ... }        // 调用方：成功了吗？流水号是多少？不知道

// ✅ 本项目
public final OrderContext processOrder(OrderContext ctx) { ... }
```

> **一个设计建议**：任何「对外提供操作」的方法，
> 都应该让调用方能判断**是否成功**、**得到了什么**。
> `void` 只在「纯粹的副作用操作」里才合理（比如 `log()`、`notify()`）。

#### 误区 7：把业务失败也用异常表达

```java
// ❌ 库存不足是每天几万次的正常情况，不该用异常
if (stock <= 0) throw new OutOfStockException();
```

**判断标准见 [4.3 方案 B](#方案-b抛异常来中止流程)**：

> **这件事在业务上是否每天都会发生？** 会 → 用状态标志；不会（说明代码有 bug）→ 用异常。

**用异常的三个坏处**：调用方被迫 `try-catch`、异常要填充栈轨迹（性能开销）、
**业务失败和真 bug 混在同一个 catch 里无法区分**。

#### 误区 8：「模板方法过时了，现在都用策略/组合」

**这个说法半对半错，要看语境。**

**说它对的部分**：在**应用层业务代码**里，组合确实更受欢迎。原因：
- 继承是强耦合（脆弱基类问题）；
- Java 单继承限制；
- lambda 让「注入行为」变得很轻量。

**说它错的部分**：模板方法在**这三个领域依然不可替代**：

| 领域 | 例子 | 为什么非它不可 |
|---|---|---|
| **框架内部** | Spring `JdbcTemplate`、Servlet、JUnit | 框架要**强制**使用者的流程顺序，组合做不到（使用者可以不按顺序调 lambda） |
| **资源管理** | `JdbcTemplate` 的 open/close | 「必须保证 close」需要**结构强制**，不能靠回调 |
| **强合规业务** | 金融、医疗 | 「顺序错了就是事故」，需要 `final` 的编译期保证 |

**正确说法**：

> **在业务代码里优先考虑组合；在框架层、基础设施层、强合规业务里，模板方法依然是首选。**

#### 误区 9：认为「模板方法的钩子越多越灵活」

**错。** 钩子不是免费的。

```java
// ❌ 一个类里 8 个钩子
protected boolean needsStockCheck() { return true; }
protected boolean needsRiskCheck() { return true; }
protected boolean needsInvoice() { return false; }
protected boolean needsSms() { return true; }
protected String proofLabel() { return "物流单号"; }
protected String notificationPrefix() { return "尊敬的客户"; }
protected void beforePay(OrderContext ctx) { }
protected void afterPay(OrderContext ctx) { }
```

**三个问题：**

1. **子类要读 8 个钩子的 Javadoc 才知道自己能改什么**——认知成本；
2. **钩子之间可能互相影响**（`needsSms=false` 时 `notificationPrefix()` 还有意义吗？）——组合爆炸；
3. **大部分钩子永远没人重写**——这是 speculative generality，YAGNI 反对的。

> **本项目最初的 `beforePay()` 就属于这一类。**
> 它在第一次设计时是「预留但没人用」的扩展点，
> 当时的 Javadoc 里写了一整段诚实说明：
>
> > 「生产代码里不应该预留没人用的扩展点——那属于为了将来可能的需求而设计，
> > 是 YAGNI 原则反对的。」
>
> **后来的处理方式不是删掉它，而是让它「真的被用上」**：
> `PreSaleOrder` 重写了它来做定金校验。
> **这样它就从「猜测性设计」变成了「有真实使用者的设计」。**

**判断标准**：

| 情形 | 该不该加钩子 |
|---|---|
| 已有子类要重写它 | ✅ 该加 |
| 能明确说出「哪个业务场景会用到」 | ✅ 该加 |
| 「以后可能需要」 | ❌ **先不加** |

---

## 9. 面试与自测

这一节分两部分：

- **9.1 面试题**——题目都是真实面试里出现过的（含追问），答案按「一句话核心 + 展开」组织，方便你现场回忆；
- **9.2 自测题**——不带标准答案的**动手题**，答案折叠着，逼你先自己想。

---

### 9.1 面试题（12 道，含追问）

#### Q1：介绍一下模板方法模式

**一句话核心**：父类定流程，子类填细节。

**展开**：

> 模板方法模式在一个方法里定义算法的骨架，把某些步骤延迟到子类实现。
> 它让子类可以在不改变算法结构的前提下，重新定义算法中的某些步骤。
>
> 落到代码上就是三层结构：
> 1. **模板方法**（`public final`）——写死调用顺序，不许改；
> 2. **抽象方法**（`protected abstract`）——必须由子类实现的步骤；
> 3. **钩子方法**（`protected`，带默认实现）——可选重写的扩展点。
>
> 它体现的是**好莱坞原则**「Don't call us, we'll call you」：
> 子类不主动调用流程，而是等着父类在合适的时机回调自己。

**加分点**：主动提到「GoF 原书的模板方法不带参数，现代写法普遍改成传一个上下文对象」，说明你读过原书也写过实际代码。

**❌ 减分回答**：「就是抽象类里定义一个方法，里面调几个抽象方法」——这只描述了形式，没有说清价值（控制流程 + 强制约束）。

---

#### Q2：模板方法为什么必须加 `final`？

**一句话核心**：不加 `final`，整个模式的强制力就是零。

**展开**（这是最能分出水平的一道题）：

> `final` 保护的不是「这个方法」，而是**步骤的执行顺序**。
>
> 假设父类的 `processOrder()` 是：
> `检查库存 → 支付 → 发货 → 通知`。
> 如果子类能重写它，子类完全可以写成 `支付 → 发货 → 检查库存`，
> 于是**库存不够也照样收款、照样发货**——业务上就是超卖。
>
> 而且这个错误**不会让编译失败、不会抛异常、单元测试也可能通过**，
> 因为每一步单独看都是对的，错的只是顺序。
> 唯一能在编译期拦住它的手段，就是 `final`。

**追问：那 `final` 会不会限制扩展性？**

> 不会。`final` 限制的是「改流程」，而扩展点本来就该放在**步骤内部**（抽象方法）和**钩子**上，
> 不该放在「改流程」上。如果你的需求真的是「运行时换一套流程」，
> 那就不该用模板方法，该用**策略模式 + 组合**——那是另一个模式要解决的问题。

---

#### Q3：钩子方法和抽象方法有什么区别？

**一句话核心**：抽象方法「必须实现」，钩子「想改才改」。

| 维度 | 抽象方法 | 钩子方法 |
|---|---|---|
| 修饰符 | `protected abstract` | `protected`（有方法体） |
| 子类是否必须实现 | **必须**，否则编译错误 | **可选**，不重写就用默认行为 |
| 父类能否提供默认逻辑 | 不能（没有方法体） | **能**（这正是钩子的价值） |
| 语义 | 「这一步我做不到，你来」 | 「这一步我有默认做法，你想换再换」 |
| 本项目实例 | `pay()`、`deliver()` | `needsStockCheck()`、`deliveryProofLabel()`、`beforePay()` |

**追问：什么时候该用钩子，什么时候该用抽象方法？**

> 判断标准是：**父类能不能给出一个合理的默认行为**。
>
> - 能 → 钩子。比如「要不要检查库存」，绝大多数订单要检查（`return true`），
>   只有虚拟商品不要 → 默认 `true` + 虚拟商品重写成 `false`。
> - 不能 → 抽象方法。比如「怎么支付」，父类根本不知道你是全额、定金还是分期，
>   给不出默认实现，只能逼子类实现。

**⚠️ 陷阱**：很多人会说「钩子就是空方法」。**不对**——
钩子的本质是**默认实现**，空方法只是默认实现的一种特例（如 `beforePay(){}`）。
返回值型钩子（`needsStockCheck()` 返回 `true`）才是钩子更典型的形态。

---

#### Q4：钩子应该由谁调用？

**一句话核心**：**必须由模板方法调用**，绝不能由子类自己调。

**展开**（这题答对了能明显区分「背过模式」和「真写过」）：

> 钩子的存在意义是「父类在合适的时机回调子类」。
> 如果让子类在自己的步骤方法里调 `beforePay(ctx)`，就变成了
> **子类决定什么时候调父类的扩展点**，方向反了——这违反好莱坞原则。
>
> 更严重的是**可靠性问题**：
> 靠「每个子类都记得写一行 `beforePay(ctx)`」来保证调用，就意味着
> **新增子类忘了写，这个钩子就静默失效**。
> 编译器不会报错、运行时不抛异常、测试也发现不了——
> 在风控、对账这种场景里，这就是事故。
>
> 放进模板方法后，调用点只有一处，子类只负责「重写」，不负责「调用」。

**加分点**：说出这是**自己在项目里返工修过的真实 bug**，而不是书本知识。

**追问：那如果某个子类需要在「支付前」做自己的事，但不是校验，怎么办？**

> 那说明它不是钩子，是子类**自己的业务逻辑**，直接写在它自己的 `pay()` 里就行。
> 判断标准：**「父类需要在某个时机回调我」→ 钩子；「我自己在某个时刻要做点事」→ 自有逻辑。**

---

#### Q5：模板方法怎么在步骤之间传数据？

**一句话核心**：三种方案，本项目选了「上下文对象」，因为步骤间是**多对多**依赖。

| 方案 | 做法 | 致命问题 |
|---|---|---|
| A. 返回值 | `String payNo = pay();` | 步骤间依赖一复杂，方法签名就爆炸；父类必须知道每个返回值类型 |
| B. 抛出异常传值 | 用异常携带数据 | 语义错乱——**正常业务数据流不该用异常表达** |
| C. 上下文对象 | 传一个可变的 `OrderContext` | 需要额外设计一个类，且它是**可变状态**（有并发风险） |

**追问：为什么不用不可变对象（每步返回新对象）？**

> 因为步骤之间有**写后读**关系：`deliver()` 需要读 `pay()` 写入的 `paymentNo`。
> 用不可变对象就得每步 `return ctx.withPaymentNo(...)`，
> 父类要把新对象一路传下去，代码里全是 `ctx = ctx.withXxx(...)`，可读性反而更差。
>
> **策略模式**里我用的是不可变上下文（`PriceContext`），那是另一回事——
> 因为策略模式只有**一次**计算，没有「多步之间互相传值」的需求。
> **同一个项目里两种选择，是因为需求不同，不是因为哪个更先进。**

**追问：可变对象不会有线程安全问题吗？**

> 会。但这个 `OrderContext` 的生命周期是**一次 `processOrder()` 调用**，
> 每次调用都 `new` 一个新的，不跨线程共享，所以安全。
> 真正危险的是「把上下文对象做成字段或静态变量复用」——那才是事故。

---

#### Q6：模板方法和策略模式有什么区别？什么时候用哪个？

**一句话核心**：模板方法用**继承**改「步骤内部」，策略用**组合**换「整个算法」。

| 维度 | 模板方法 | 策略模式 |
|---|---|---|
| 复用方式 | 继承 | 组合 + 委托 |
| 变化粒度 | **步骤内部**（流程骨架不变） | **整个算法**（可以完全换一套） |
| 绑定时机 | **编译期**（`new` 哪个子类就定了） | **运行期**（可随时 `setStrategy`） |
| 父类/接口的角色 | **控制流程**（父类调用子类） | **被调用**（Context 调用策略） |
| 扩展成本 | 加子类，父类 0 改动 | 加实现类，Context 0 改动 |
| 主要缺点 | 继承强耦合、单继承限制 | 类数量膨胀、调用方要选策略 |

**判断标准（一句话）**：

> **「流程一样，个别步骤不一样」→ 模板方法；
> 「流程本身就不一样，需要运行时切换」→ 策略。**

**本项目里的对照**：策略模式那边是「折扣算法」，每个算法都是一次独立计算，可以随时换；
模板方法这边是「订单处理流程」，五步顺序是业务硬约束，绝不能换，只能换步骤内部的实现。

**追问：能不能两个一起用？**

> 能，而且很常见。比如「订单处理流程」用模板方法固定五步，
> 其中「计算运费」这一步**委托**给一个 `ShippingStrategy`——
> 这就是「模板方法负责流程，策略负责算法」的组合。
> 本项目 §7.4 的「通知渠道」扩展就是这个思路：钩子 `notificationChannel()` 返回一个策略接口。

---

#### Q7：工厂方法和模板方法是什么关系？

**一句话核心**：**工厂方法常常是模板方法的一个特例**。

**展开**：

> 工厂方法模式里，父类定义了「创建对象」这个步骤，但把「创建哪个对象」交给子类决定——
> 这本身就是模板方法思想在「对象创建」这一件事上的应用。
>
> 反过来说：模板方法是一个**更通用的结构**，
> 「延迟到子类实现」的那一步可以是创建对象（→ 工厂方法），
> 也可以是算法步骤（→ 本项目的 `pay()`/`deliver()`）。
>
> **所以「工厂方法是模板方法的特例」这句话在结构上成立**，
> 但两者解决的问题不同：工厂方法关注「解耦对象的创建」，模板方法关注「固定算法骨架」。

**加分点**：Spring 的 `AbstractApplicationContext.refresh()` 里既有模板方法（`refresh()` 定流程），
也有工厂方法（`createBeanFactory()` 由子类决定创建哪种 `BeanFactory`）——**同一个类里两者并存**。

---

#### Q8：`protected` 能真正做到「只给子类用」吗？

**一句话核心**：**不能**。Java 的 `protected` = 同包可见 + 子类可见，没有更严格的选项。

**展开**：

> 这意味着**同包下的任何类**（哪怕不是子类）都能直接调 `pay()`、`deliver()`，
> 绕过 `processOrder()` 的顺序约束。
>
> 本项目 demo6 就真的演示了这个漏洞：
> `Client` 和 `OrderProcessTemplate` 在同一个包，于是 `Client` 可以直接
> `processor.deliver(ctx);` → `processor.pay(ctx);`，
> **先发货后收款**，而且编译、运行都不报错。
> 输出里 `ctx.getPaymentNo()` 打印出 `null`——这就是顺序被打乱的直接证据。

**追问：生产代码怎么防？**

| 手段 | 做法 | 代价 |
|---|---|---|
| 分包 | 模板类放 `framework` 包，具体实现放 `impl` 包 | 包结构变复杂 |
| JPMS | `module-info.java` 里 `exports` 只暴露门面包 | 要上模块系统 |
| 架构测试 | ArchUnit 断言「`impl` 包不得调用 `protected` 方法」 | 需要额外依赖和 CI 集成 |

**⚠️ 诚实说明**：这不是模板方法的缺陷，是 **Java 语言的设计取舍**（C++ 的 `protected` 也没有「只给子类」的严格语义）。面试时如果说「`protected` 就是只给子类」，是对语言规范的误解。

---

#### Q9：模板方法某一步失败了，用异常还是返回值？

**一句话核心**：**看这是「程序错误」还是「业务分支」**。

| 情形 | 手段 | 理由 |
|---|---|---|
| 参数为 `null`、状态非法（程序 bug） | **抛异常** | 快速失败，让 bug 尽早暴露 |
| 库存不足、账号格式错误（正常业务分支） | **状态标志 + 短路返回** | 异常不该用来表达「预期内的业务结果」 |

**展开**：

> 本项目用 `OrderContext.abort(reason)` 表达业务失败：
> 设置 `aborted = true` 和 `abortReason`，模板方法在每一步之后检查 `isAborted()` 并短路返回。
>
> **为什么不用异常？**
> 1. **性能**：`fillInStackTrace()` 是相对昂贵的操作，业务分支可能很频繁；
> 2. **语义**：异常 = 「出了不该出的事」，而「库存不足」是每天都会发生的正常情况；
> 3. **可读性**：`ctx.abort("库存不足")` 一眼看出是业务流程，`throw new StockException()` 需要看 catch 才知道后面会怎样。

**追问：那你什么情况下会抛异常？**

> `OrderContext` 构造器里的三条校验——`productName` 为空、`amount` 为 `null`、`amount < 0`——
> 这些**都是调用方的编码错误**，正常业务不可能出现，所以直接 `throw new IllegalArgumentException(...)`。
> 这是 **fail fast**：让错误在最早的地方炸掉，而不是带着脏数据跑完整个流程。

---

#### Q10：Spring / JDK 里哪些地方用了模板方法？

**一句话核心**：模板方法是**框架最底层的模式**，几乎所有「框架定流程、你填细节」的地方都是它。

| 位置 | 模板方法 | 留给你的步骤 / 钩子 |
|---|---|---|
| `HttpServlet` | `service(req, resp)` | `doGet()` / `doPost()`（抽象方法） |
| `AbstractApplicationContext` | `refresh()` | `postProcessBeanFactory()` / `onRefresh()`（钩子） |
| `JdbcTemplate` | `execute()` / `query()` | `PreparedStatementSetter` / `RowMapper`（回调接口） |
| `AbstractList` | — | `removeRange()`（钩子，`AbstractList` 里抛 `UnsupportedOperationException`） |
| `JUnit 5` | 测试生命周期 | `@BeforeEach` / `@AfterEach`（注解形式的钩子） |
| MyBatis `BaseExecutor` | `query()` | `doQuery()`（抽象方法） |
| `Arrays.sort()` | 双轴快排骨架 | `Comparator.compare()`（回调） |

**加分点（`JdbcTemplate` 最有说服力）**：

> 原生 JDBC 每一步都要 `try-finally-close`，忘了就连接泄漏。
> `JdbcTemplate` 把「打开连接 → 执行 → 处理异常 → 关闭」这个骨架用模板方法写死，
> 你只需要提供「SQL 是什么」和「结果怎么映射」。
> **它消除的不是重复代码，是「忘了关连接」这种事故。**

---

#### Q11：模板方法有什么缺点？怎么缓解？

**一句话核心**：**继承是强耦合**，父类一动，所有子类跟着动。

| 缺点 | 具体表现 | 缓解手段 |
|---|---|---|
| 脆弱基类 | 父类改了某个步骤的签名，所有子类编译失败 | 步骤方法尽量只依赖**稳定**的抽象（如 `OrderContext`），不要频繁改签名 |
| 单继承被占用 | Java 类只能 `extends` 一个类 | 变化维度多的部分改用**接口 + 组合**（如策略、装饰器） |
| 子类膨胀 | 5 种订单 = 5 个子类，10 种 = 10 个 | 如果差异只是「参数不同」，用**配置/枚举**而不是新子类 |
| 流程固定 | 运行时无法改变 | 需要运行时切换 → 用**策略 + 组合** |
| `protected` 漏洞 | 同包可绕过流程 | 分包 / JPMS / ArchUnit |

**追问：那为什么还用它？**

> 因为它提供的是**编译期强制力**——`final` 挡住改流程、`abstract` 挡住漏实现。
> 这和「写在文档里呼吁大家遵守」有本质区别：
> **文档会被忽略，编译器不会。**
> 当一个约束**必须**被满足（比如「库存不足绝不能收款」），
> 用模板方法把它变成「无法做错」，比任何代码规范都可靠。

---

#### Q12：如果流程需要在运行时变化，还能用模板方法吗？

**一句话核心**：**不能，该换策略模式 + 组合。**

**展开**：

> 模板方法的绑定是**编译期**的：`new PhysicalProductOrder()` 那一刻，
> 流程就定了，运行中没法变成虚拟商品的流程。
>
> 如果需求真的是「同一笔订单，运行时决定走哪套流程」，
> 那就该把「流程」本身抽成接口（策略），由 `Context` 持有并**委托**调用，
> 这样 `setStrategy()` 一调就换了一套算法。
>
> **判断标准**：
> - 流程**固定**、只换步骤内部 → 模板方法（继承）
> - 流程**可变**、需要运行时切换 → 策略（组合）

**加分点**：

> 但要注意：**「为了运行时可变」不总是真需求**。
> 本项目的订单类型是**创建时就知道**的（用户下单时选了实物还是虚拟），
> 后续流程绝不会变，所以用模板方法是对的。
> 如果只是为了「以后可能要动态改」就上策略，那是 **speculative generality**——
> YAGNI 反对的正是这个。

---

### 9.2 自测题（8 道动手题）

> 下面每道题都**不需要写代码**，但需要你对着代码**说出具体结论**。
> 想不出来的，回去翻对应的小节——比直接看答案有效得多。

---

#### 自测 1：删掉 `final` 会发生什么？

把 `OrderProcessTemplate.processOrder()` 的 `final` 去掉，然后回答：

1. 现有代码**能不能编译**？
2. 如果这时新增一个 `BadOrder extends OrderProcessTemplate`，
   在 `processOrder()` 里写成「先 `pay()` 再 `checkStock()`」，会发生什么？
3. 为什么这个错误**测试也很难发现**？

<details>
<summary>点我看答案</summary>

1. **能编译**。`final` 只是限制重写，去掉它不影响现有代码。
2. 编译**依然通过**（因为 `processOrder()` 不再是 `final`，重写合法），
   运行时也**不抛异常**——它会真的先收款再检查库存，库存为 0 也照收。
3. 因为每一步单独调用都是对的，错的只是**顺序**。
   单元测试通常测「`pay()` 返回了流水号吗」「`checkStock()` 算对了吗」，
   很少测「`pay()` 有没有在 `checkStock()` 之后被调用」。
   **顺序错误是「沉默的 bug」**，只有 `final` 能在编译期拦住它。

</details>

---

#### 自测 2：把钩子调用从模板方法挪回子类

现在 `beforePay(ctx)` 由 `processOrder()` 调用。假设你把它挪回 `PreSaleOrder.pay()` 的第一行：

1. demo4-C（定金不足）的输出会**多出哪一行**？
2. 为什么说这个改动「编译器不会报错」？
3. 如果这时新增 `OverseasOrder`，开发者忘了写 `beforePay(ctx)`，会发生什么？

<details>
<summary>点我看答案</summary>

1. 会多出 `│  ② 支付：已取消` 这一行——因为 `PreSaleOrder.pay()` 被执行了，
   它得靠自己写 `if (ctx.isAborted()) return;` 来防御，防御前那行 `println` 就打印了。
   （这正是本项目**修复前**的真实输出。）
2. 因为「调不调一个 `protected` 方法」是运行时的行为选择，与类型系统无关。
   `pay()` 的签名没变、`beforePay()` 依然存在，编译期完全合法。
3. **定金校验静默失效**：`OverseasOrder` 会正常收款、发货、通知，
   看起来一切正常，只是「最低定金 ￥1」这条规则被绕过了。
   这种 bug 通常要等到财务对账时才发现。

</details>

---

#### 自测 3：为什么两个检查的顺序不能换？

`processOrder()` 里有两段：

```java
if (ctx.isAborted()) { ... return ctx; }              // 检查 1
if (ctx.getPaymentNo() == null) { ... return ctx; }   // 检查 2
```

把检查 2 挪到检查 1 前面，然后回答：

1. demo4-B（充值账号非法）的中止原因会变成什么？
2. 这个 bug 的本质是什么？和 Java 里哪个语言特性犯了同一个错误？

<details>
<summary>点我看答案</summary>

1. 会变成 `疑似子类实现缺陷：pay() 执行后没有设置支付流水号`。
   因为 `VirtualProductOrder` 在 `beforePay()` 里 `abort` 了，
   `pay()` 根本没执行，`paymentNo` 自然是 `null`，
   于是「笼统的推测」覆盖了「具体的事实」。
2. 本质是**防御性检查的顺序错了**：应该按「原因的具体程度」从具体到笼统排列。
   这与 Java 的 **`catch` 块顺序**是同一个道理——
   `catch (Exception e)` 写在 `catch (IOException e)` 前面，编译直接报错，
   因为编译器知道笼统的会吃掉具体的。
   **但这里的两个 `if` 编译器不管，只能靠人。**

</details>

---

#### 自测 4：`availableStock = -1` 是干什么的？

`OrderContext` 里 `availableStock` 的初始值是 `-1`，而不是 `0`。

1. 如果改成 `0`，会有什么问题？
2. 这个技巧叫什么名字？还有哪些常见例子？

<details>
<summary>点我看答案</summary>

1. 就分不清「**还没检查**」和「**检查了，结果是 0 件**」——
   虚拟商品走 `needsStockCheck() = false` 直接跳过检查，`availableStock` 会一直是 `0`，
   后面任何读取它的逻辑都会误以为「库存是 0」而不是「没检查过」。
2. 叫**哨兵值（sentinel value）**。
   常见例子：`indexOf()` 返回 `-1` 表示「没找到」、
   `InputStream.read()` 返回 `-1` 表示「读完了」、
   `Optional` 用「空」表示「没有值」。

</details>

---

#### 自测 5：`BigDecimal` 的两个坑

```java
BigDecimal a = new BigDecimal("30.0");
BigDecimal b = new BigDecimal("30.00");
```

1. `a.equals(b)` 是什么？`a.compareTo(b)` 是什么？为什么？
2. 本项目里 `estimateWeight()` 为什么用 `setScale(1, HALF_UP)` 而不是 `setScale(2, ...)`？
3. `DEPOSIT_RATE.multiply(BigDecimal.TEN).stripTrailingZeros()` 打印 `3` 还是 `3.0`？
   如果 `DEPOSIT_RATE = new BigDecimal("3.00")` 呢？

<details>
<summary>点我看答案</summary>

1. `equals` 是 **`false`**（`BigDecimal.equals` 比较 **值 + scale**），
   `compareTo` 是 **`0`**（只比较数值大小）。
   **`BigDecimal` 判等永远用 `compareTo`，不要用 `equals`。**
2. 因为重量的业务精度是「0.1 kg」（`12.0kg`、`45.0kg`），
   用 `setScale(2)` 会显示成 `12.00kg`，与业务语义不符。
   **scale 应该由业务精度决定，不能一律写 2。**
3. 打印 `3`——`stripTrailingZeros()` 去掉了 `30.00` 的尾随零。
   但如果 `DEPOSIT_RATE = new BigDecimal("3.00")`，`multiply(TEN)` 得到 `30.00`，
   `stripTrailingZeros()` 会返回 **`3E+1`**（科学计数法！）。
   **所以 `stripTrailingZeros()` 之后要跟 `toPlainString()`，或者干脆别用它。**

</details>

---

#### 自测 6：`getTrace()` 返回的是副本吗？

`OrderContext.getTrace()` 返回 `Collections.unmodifiableList(trace)`。

1. 拿到这个 `List` 后，**能不能** `add()`？
2. 拿到之后，如果原 `ctx` 又 `record()` 了几条，你手里的 `List` **能看到吗**？
3. 如果你想要「拿到那一刻的快照」，该怎么写？

<details>
<summary>点我看答案</summary>

1. **不能**，会抛 `UnsupportedOperationException`（demo7 真实验证过）。
2. **能看到**。`unmodifiableList()` 返回的是**视图**，不是副本，
   它包装的是原来的 `ArrayList` 引用，原列表变了它就看到变化。
   **这是很多人以为「不可变 = 快照」的常见误解。**
3. 用 `List.copyOf(trace)`——它是**真副本**，之后原列表无论怎么变都不影响。
   代价是**每次调用都要复制**，如果 `trace` 很大就要考虑性能。

</details>

---

#### 自测 7：新增一种订单要改几个文件？

需求：新增「**海外直邮订单**」，要求：全额预付、交付凭证是「国际运单号」、不需要检查国内库存。

1. 列出必须改的文件。
2. `OrderProcessTemplate.java` 要改吗？为什么？
3. 如果**必须**改父类，说明你的设计哪里出了问题？

<details>
<summary>点我看答案</summary>

1. **只新增 `OverseasOrder.java` 一个文件**（+ 可选地在 `Client.java` 加一段演示）。
2. **不改**。它只需要 `extends OrderProcessTemplate` 并实现两个抽象方法
   `pay()`、`deliver()`，再按需重写钩子 `needsStockCheck()`（返回 `false`）
   和 `deliveryProofLabel()`（返回 `"国际运单号"`）。
3. 说明**变化点没有被抽干净**。常见的两种跑偏：
   - 在父类里写 `if (this instanceof OverseasOrder)` —— 父类反过来依赖子类，DIP 被破坏；
   - 新增了一个抽象方法（比如 `abstract void customsClearance()`），
     结果逼着已经存在的三个子类都去实现一个它们根本用不到的方法。
     **父类为「一个子类的需求」去改接口，就是开闭原则失效。**

</details>

---

#### 自测 8：为什么 demo 里不能用随机数？

`PhysicalProductOrder.estimateWeight()` 用的是 `productName.length() × 3`，
`checkStock()` 用的是 `(productName.length() - 3) × 7`。

1. 如果改成 `new Random().nextInt(100)`，会有什么问题？
2. 这份 README 里的所有输出，是怎么保证「你跑出来和我写的一样」的？
3. `(length - 3) * 7` 里为什么要 `-3`？

<details>
<summary>点我看答案</summary>

1. **文档里的输出就没法验证了**。读者跑出来是 63 件库存，文档写的是 7 件，
   他会怀疑「是不是我环境有问题」「是不是代码和文档不是一版」。
   **随机数让输出不可复现，而不可复现的输出无法作为教学材料。**
   而且 demo4-A 需要「库存为 0」这个精确的失败分支，随机数根本保证不了能触发。
2. 靠**确定性公式**：库存和重量都只由 `productName` 决定（`hashCode` 也不行，
   因为不同 JDK 版本的 `String.hashCode` 虽然稳定，但 `length` 更直观）。
   同一份代码、同一个输入，输出必然一致（除了流水号里的时间戳）。
3. `-3` 是为了让**短名字的商品库存变少甚至为 0**：
   「杯子」2 个字 → `(2-3)*7 = -7` → 取 `Math.max(0, ...)` 得 0，
   正好触发 demo4-A 的「库存不足」分支。
   **这是刻意构造的边界用例**，不是随手写的数字。

</details>

---

### 9.3 学完这一章，你应该能回答的三个问题

任务书里有一句话贯穿整个项目：

> **所有核心设计都应回答三个问题——为什么需要它？为什么这样实现？换一种实现会有什么区别？**

落到模板方法模式上：

| 问题 | 你的答案应该包含 |
|---|---|
| **为什么需要它？** | 五步流程的顺序是业务硬约束（库存不够不能收款、没支付不能发货），而每种订单的步骤内部实现不同。用模板方法把顺序写死、把差异留给子类，同时用 `final`/`abstract` 让约束**编译期强制**。 |
| **为什么这样实现？** | `final` 保护顺序；两个 `protected abstract` 是父类给不出默认实现的步骤；三个钩子对应三处「大多数订单一样、个别不一样」的差异；`OrderContext` 承载步骤间数据；`abort()` + 短路返回区分业务失败与程序错误。 |
| **换一种实现会有什么区别？** | 用 `instanceof` 判断类型 → 父类依赖子类，新增类型要改父类；用策略 + 组合 → 流程可在运行时切换，但步骤间的顺序约束失去编译期保护；三个 `void` 方法各自为政 → 数据只能靠字段传，`final` 也失去意义。 |

---

### 9.4 一句话总结

> **模板方法模式的全部价值，浓缩成一句话：
> 把「必须做对的事」写成「无法做错的事」。**

它不炫技，也不新潮。但在「流程固定、细节可变、且顺序绝不能错」的场景里，
它依然是**成本最低、强制力最强**的解法——
这也是为什么 Spring、Servlet、JdbcTemplate、JUnit 这些你天天在用的框架，
底层都站着同一个模式。

---

## 附录：文件清单与运行方式

### A. 本目录文件清单

```
design_patterns_pro/
└── src/com/patterns/
    ├── util/
    │   └── DisplayWidth.java          ← 中文宽度对齐工具（演示输出用）
    └── behavioral/templatemethod/
        ├── README.md                  ← 本文档
        ├── OrderProcessTemplate.java  ← 【模式核心】抽象父类，526 行
        ├── OrderContext.java          ← 流程上下文，267 行
        ├── PhysicalProductOrder.java  ← 子类：实物商品，143 行
        ├── VirtualProductOrder.java   ← 子类：虚拟商品，207 行
        ├── PreSaleOrder.java          ← 子类：预售商品（本次新增），264 行
        └── Client.java                ← 七段演示，373 行
```

**六个 Java 文件合计 1780 行**（`OrderProcessTemplate` 526 + `OrderContext` 267 + `Client` 373 + `PreSaleOrder` 264 + `VirtualProductOrder` 207 + `PhysicalProductOrder` 143）。

各文件的职责边界（**这个表比类图更能说明设计**）：

| 文件 | 一句话职责 | 如果删掉它会怎样 |
|---|---|---|
| `OrderProcessTemplate` | 定义五步流程 + 三个扩展点 | 模式消失，退化成三份互不相干的 `main` |
| `OrderContext` | 承载步骤间传递的数据 + 失败状态 + 审计轨迹 | 步骤之间无法传值，`final` 也失去意义（见 §4.3） |
| `PhysicalProductOrder` | 展示「标准情况」的代码量有多小 | 少一个实物商品示例，但模式完整性不受影响 |
| `VirtualProductOrder` | 展示「重写钩子跳过一个步骤」 | 就看不到 `needsStockCheck()` 这个钩子的真实用途 |
| `PreSaleOrder` | 证明「新增子类时父类 0 改动」+ 四种角色全用上 | 开闭原则只剩口头声明，没有可运行的证据 |
| `Client` | 七个问题，七段演示 | 所有设计意图都只能靠文档描述，无法验证 |

---

### B. 编译与运行

#### B.1 环境

| 项 | 值 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25`（`javac 25`） |
| 操作系统 | Windows / PowerShell |
| 源文件编码 | UTF-8（源码里的中文、`│ └ ┌ √` 都靠它） |

#### B.2 编译

在**项目根目录** `E:\codeFile\codeFather\design_patterns_pro` 下执行：

```powershell
javac --release 25 -encoding UTF-8 -Xlint:all -d out `
      src/com/patterns/util/DisplayWidth.java `
      src/com/patterns/behavioral/templatemethod/*.java
```

**本次实际执行的验证结果**：

```
COMPILE-EXIT = 0
（加了 -Xlint:all，零警告）
```

> **坑 0：PATH 里的 `javac` 可能不是 JDK 25。**
> 本次验证时，直接敲 `javac --release 25` 报的是
> `错误: 不支持发行版本 25`——因为 PATH 里指向的是旧 JDK。
> 改用全路径后一切正常：
>
> ```powershell
> & "F:\enCodingApps\JDK\jdk-25\bin\javac.exe" --release 25 ...
> & "F:\enCodingApps\JDK\jdk-25\bin\java.exe"  -cp out ...Client
> ```
>
> **以后看到「不支持发行版本 X」，先 `javac -version`，别先怀疑代码。**
>
> 另：PowerShell 里 `-Dfile.encoding=UTF-8` 这种参数**必须加引号**，
> 否则会被拆成两个参数，报出莫名其妙的 `找不到或无法加载主类 .encoding=UTF-8`。

> `-Xlint:all` 不是摆设。本项目在修 `beforePay()` 调用位置那次改动后，
> 用它确认过「删掉 `PreSaleOrder` 里的防御代码没有引入未使用变量、未使用 import」这类问题。

#### B.3 运行

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8   # 第一步：让控制台按 UTF-8 解码
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out `
     com.patterns.behavioral.templatemethod.Client
```

**本次实际执行的验证结果**：

```
RUN-EXIT = 0
输出共 275 行（9583 字符），逐字记录在 tmp/t.txt（§6 全部引自该文件）
```

如果你想**复现文档里的数字**，注意两点：

1. **流水号里的时间戳会不同**（`yyyyMMddHHmmss`），但要**位数、前缀、递增关系**必须一致：

   | 前缀 | 出现的 demo | 含义 |
   |---|---|---|
   | `PAY` | demo1 / demo3 / demo5 | 实物商品全额支付 |
   | `SF` | demo1 / demo5 | 顺丰运单 |
   | `VPAY` | demo4-B / demo5 | 虚拟商品支付 |
   | `PRE` | demo3 | 预售定金 |

2. **库存和重量只由商品名长度决定**，所以这两组数字必然一致：

   | 商品名 | 字数 | 库存 `(n-3)×7` | 重量 `n×3` kg |
   |---|---|---|---|
   | 机械键盘 | 4 | 7 | 12.0 |
   | 无线鼠标 | 4 | 7 | 12.0 |
   | 杯子 | 2 | 0（`max(0, -7)`） | 6.0 |
   | 话费充值 | 4 | 7 | 12.0 |
   | 对开门大容量冰箱洗衣机组合套装 | 15 | 84 | 45.0 |

   > 这就是为什么 demo 里**绝不能用 `new Random()`**——见 §9.2 自测 8。

---

### C. Windows 控制台的三个编码坑（都是真实踩过的）

#### 坑 1：只设 `-Dfile.encoding` 不够

`-Dfile.encoding=UTF-8` 影响的是**文件的读写**，不影响 `System.out` 往控制台写字节时的编码。
JDK 19 之后，标准输出有独立参数：**`-Dstdout.encoding=UTF-8`**。

```powershell
# ❌ 中文可能变乱码
java -Dfile.encoding=UTF-8 -cp out ...Client

# ✅ 三个都写
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out ...Client
```

#### 坑 2：`[Console]::OutputEncoding` 是 PowerShell 侧的设置

Java 进程写出去的是 UTF-8 字节，但 **PowerShell 按系统代码页（GBK/936）去解码**，
于是你看到的是乱码。两边必须同时是 UTF-8：

| 侧 | 设置 |
|---|---|
| Java（写） | `-Dstdout.encoding=UTF-8` |
| PowerShell（读） | `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` |

#### 坑 3：符号在不同终端里渲染不一致

本项目在演示输出里用的符号，都是**实测能在 UTF-8 文件输出里正常显示**的：

| 符号 | 用途 | 状态 |
|---|---|---|
| `│ ├ └ ─ ┌` | 树形/流程缩进 | ✅ 实测正常 |
| `①②③④` | 步骤编号 | ✅ 实测正常 |
| `→ ←` | 数据流向 | ✅ 实测正常 |
| `√` | 成功标记 | ✅ 实测正常 |
| `>>> [OK] / [FAIL]` | 断言结果 | ✅ 实测正常（**特意避开 `✔` `✘`**） |
| `▶ ★ ◆` | — | ❌ **未使用**，在部分终端渲染为方块 |

> **为什么不用 `✔` `✘`？** 它们属于 Dingbats 区段，在 Windows 默认字体里可能缺字形。
> 换成 `[OK]` / `[FAIL]` 这种纯 ASCII 标记，**可读性不降、兼容性拉满**——
> 这是「演示代码要能在别人机器上跑」的基本要求。

---

### D. 与原项目的逐项对照

这一节回答任务书的第五步：**修改了什么、保留了什么、解决了什么问题、哪些尚未验证。**

#### D.1 保留了什么（原项目做对了，一行都没改）

| 保留项 | 为什么保留 |
|---|---|
| `abstract class OrderProcessTemplate` 的骨架 | 五个步骤的顺序是对的，这是模式的核心 |
| `public final void processOrder()`（本项目改为返回 `OrderContext`） | `final` 是整个模式的强制力来源，**这是原项目最大的亮点** |
| `protected abstract void pay()` / `deliver()` | 修饰符、抽象性都对 |
| `protected void checkStock()` 有默认实现 | 这正是「钩子」的思路，只是原项目没把它显式化 |
| 独立 Demo 形式（`Client` 里 `main` 跑到底） | 符合任务书「不强行整合成一个业务系统」 |

#### D.2 修复了什么（五个问题，每个都有可运行的证据）

| # | 问题 | 修复手段 | 证据 |
|---|---|---|---|
| 1 | **没有钩子**，虚拟商品被迫实现「检查库存」，业务语义错误 | 抽出 `needsStockCheck()` / `deliveryProofLabel()` 两个返回值型钩子 | §2.4；demo 输出里虚拟商品那条「跳过库存检查」 |
| 2 | **三个步骤全 `void`**，数据只能靠字段传，`final` 失去意义 | 引入 `OrderContext` 作为参数，`processOrder` 返回它 | §2.5；demo6 里「先发货后支付」打出 `paymentNo = null` |
| 3 | **没有失败短路**，库存不足照样收款（超卖） | `ctx.abort(reason)` + 每步之后 `isAborted()` 短路 | §2.6；demo4 三笔订单全部中止且**不消耗流水号** |
| 4 | **可见性不一致**：父类包级私有、一个子类 `public`、另一个包级私有 | 统一 `public`，并加类级 Javadoc 说明 | §2.7；否则 `List<OrderProcessTemplate>` 写不出来 |
| 5 | **子类太薄**，读者会质疑「为什么不用 `instanceof`」 | 加 `PreSaleOrder`（定金/尾款、四种角色全用上） | §2.8 / §5.5；新增它时父类**改动 0 行** |

#### D.3 新增了什么（原项目没有，为解决上面的问题而加）

| 新增 | 解决什么 | 详见 |
|---|---|---|
| `OrderContext` 类 | 步骤间传值 + 失败状态 + 审计轨迹 | §5.1 |
| `needsStockCheck()` 钩子 | 「这一步不适用于我」→ 跳过，而不是「假装做一下」 | §5.2.5 |
| `deliveryProofLabel()` 钩子 | 同一个步骤，不同子类的**名词**不同（物流单号/下载链接/预计到货日） | §5.2.5 |
| `beforePay()` 钩子（由模板方法调用） | 支付前的可选校验，且能被**子类用来中止流程** | §4.5 / §5.2.5 |
| `abort()` / `isAborted()` | 业务失败用状态标志，不用异常 | §4.3 |
| `trace`（`record()` / `getTrace()`） | 区分「某步骤被跳过」和「某步骤执行了但没输出」，合规场景必需 | §4.8 |
| `generateSerialNo(prefix)`（`protected final`） | 消除三个子类里重复的流水号生成逻辑 | §5.2.6 |

#### D.4 本次返工（写文档时才发现的设计缺陷）

这一条单独列出来，因为它是**「代码写完了、能跑、测试也过，但设计是错的」**的典型：

- **返工内容**：`beforePay(ctx)` 的调用点，从「三个子类各自 `pay()` 的第一行」**移进** `processOrder()`。
- **为什么必须改**：① 违反好莱坞原则；② 靠「每个子类都记得写」保证调用，**新增子类忘了写就静默失效**，编译器不报错。
- **怎么发现的**：写 §4.5 时想画「钩子调用时序图」，画到第二个子类时发现「这条线是我自己画的，不是模板方法画的」——
  **画不出来，说明设计本身站不住**。
- **可观测的差异**：修复后 demo4-C 的输出里，`② 支付：已取消` 这一行**消失了**，
  证明 `PreSaleOrder.pay()` 根本没被执行（详见 §4.5）。

> **这条值得单独记住**：写文档不只是「记录代码」，它是一次**强制性的设计复核**。
> 很多设计问题是在「试图把它讲清楚」的过程中暴露的。

---

### E. 尚未验证的事项（诚实清单）

任务书要求「无法实际运行的代码必须明确说明，不能声称验证成功」。本目录的情况：

| 项 | 状态 | 说明 |
|---|---|---|
| JDK 25 编译 | ✅ **已验证** | `javac --release 25 -Xlint:all`，`COMPILE-EXIT = 0`，零警告 |
| 运行 | ✅ **已验证** | `RUN-EXIT = 0`，275 行输出逐字记录在 `tmp/t.txt`；重跑两次输出**完全一致**（仅时间戳不同，已用哈希比对） |
| 七段 demo 的输出 | ✅ **已验证** | §6 全部引自真实运行结果，含失败分支（demo4-A/B/C） |
| `final` 的编译期拦截 | ✅ **已验证** | demo6 里 `BadOrder` 重写 `processOrder()` 的编译错误原文见 §2.5 |
| `unmodifiableList` 的防护 | ✅ **已验证** | demo7 捕获到 `UnsupportedOperationException`，打印 `[OK]` |
| **GBK 真实控制台的渲染** | ⚠️ **未验证** | 输出是通过 PowerShell 重定向到 UTF-8 文件验证的；在**系统代码页 936 的原生 cmd 窗口**里，`┌ │ ①②③④` 是否正常显示**未实测**。若显示异常，把输出重定向到文件或改用 Windows Terminal 即可 |
| **非 Windows 环境** | ⚠️ **未验证** | 代码本身无平台依赖（没用 `System.lineSeparator` 之外的东西），但未在 Linux/macOS 上跑过 |
| **低版本 JDK 兼容性** | ⚠️ **未验证** | 代码用到 `List.of()`（JDK 9+）和 `String.isBlank()`（JDK 11+），**语法上最低要求 JDK 11**；本项目只在 25 上编译运行过，没有在 11/17 上实测 |

---

### F. 下一步

本模式是「行为型批 1」三个模式中的第二个。已完成的：

- ✅ **策略模式**（`behavioral/strategy/`）—— 组合 + 委托，10 个文件 + README
- ✅ **模板方法模式**（本目录）—— 继承 + 回调，6 个文件 + README

接下来是 **命令模式**（`behavioral/command/`），它和本模式有一个共同点值得留意：

> 两者都能「把一段行为包装起来」，但**目的完全相反**：
> - 模板方法用**继承**把流程**锁死**；
> - 命令用**对象化**把行为**解耦**，于是能排队、能撤销、能重放、能记日志。
>
> 一个追求「不许变」，一个追求「随便换」——**这组对比想清楚了，行为型模式的门就进了一半。**

---

<div align="center">

**本文档全部数字、输出、bug 均来自 `E:\codeFile\codeFather\design_patterns_pro` 的真实编译运行记录。**

**如需核对：`tmp/t.txt`（运行输出）、§2.2（原项目输出）、§4.5（返工前后对比）。**

</div>
