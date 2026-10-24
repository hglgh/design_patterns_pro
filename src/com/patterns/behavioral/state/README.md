# 状态模式（State）

> **一句话**：同一个方法调用，在对象的不同状态下产生不同的行为，
> 而**调用方不需要写任何 `if`**——分支住在状态对象里，不住在调用方。
>
> 更进一步：状态模式真正的主角不是"行为差异"，是**拒绝**。
> 一个只会往前走的流程不需要状态模式，一个会**挡住不该做的事**的流程才需要。

> **本目录代码已用 JDK 25 实际编译运行验证**。文中所有输出、
> 所有"原项目跑出来的结果"都是真实执行得到的，不是编造的。
> 验证方式见[附录 B](#附录-b运行方式与验证记录)。

---

## 目录

1. [现实案例：一个"取消订单"按钮的三副面孔](#1-现实案例一个取消订单按钮的三副面孔)
2. [原始实现：原项目 130 行与它的七个问题](#2-原始实现原项目-130-行与它的七个问题)
3. [设计动机：状态模式到底解决了什么](#3-设计动机状态模式到底解决了什么)
4. [设计推导：从"补一个 if"推到 12 个文件](#4-设计推导从补一个-if推到-12-个文件)
5. [代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
6. [执行流程：五段 Demo 的真实运行结果](#6-执行流程五段-demo-的真实运行结果)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- 附录
  - [A：文件清单](#附录-a文件清单)
  - [B：运行方式与验证记录](#附录-b运行方式与验证记录)
  - [C：相关模式交叉索引](#附录-c相关模式交叉索引)
  - [D：原项目七个问题对照表](#附录-d原项目七个问题对照表)
  - [E：概念速查](#附录-e概念速查)
  - [F：版本与工具链](#附录-f版本与工具链)

---

## 1. 现实案例：一个"取消订单"按钮的三副面孔

### 1.1 说人话版：按钮为什么有时能点，有时不能

你在某平台买了一台 2999 元的显示器，下单之后想反悔。

**场景一：刚下单，还没付钱。**
页面上"取消订单"按钮是亮的，点下去，秒退。系统告诉你"订单已取消，库存已释放"。

**场景二：付了钱，卖家还没发货。**
按钮还是亮的。点下去，系统说"订单已取消，2999 元将原路退回，1-3 个工作日到账"。
同样是"取消"，这一次多了退款。

**场景三：已经发货了。**
按钮**灰掉了**，鼠标悬停显示"已发货，无法取消，请收货后申请退货"。

三个场景，同一个按钮，三种完全不同的表现。

你在前端看到的是"按钮亮不亮"，而后端要回答的是三个不同的问题：

1. 这张订单现在处于什么状态？
2. 在这个状态下，"取消"这个动作意味着什么？
3. 如果允许，做完之后订单变成什么状态、要顺带做什么事？

**这三个问题，就是状态模式的全部内容。**

### 1.2 一个真实事故：只在前端判断的状态

上面的场景里藏着一条极易被忽略的分界线：

> **按钮灰掉，是给人看的；状态校验，是给系统用的。**

如果后端只做了前者不做后者，会发生什么？

有人用 Postman 直接发一个 `POST /order/1001/cancel`，绕过了前端的灰按钮。
后端如果只有一句 `order.setStatus(CANCELLED)`，那么：

- 一张**已发货**的订单被标记成"已取消"；
- 货还在路上，仓库不知道；
- 退款流程没有触发，但订单状态说已取消；
- 等到买家签收，系统发现一张"已取消但已签收"的订单，对账直接炸。

这不是假想的场景。**"前端做了校验所以后端没做"是生产环境里最常见的一类漏洞**，
因为前端的校验从来就不是安全边界。

所以状态模式要守住的不是"按钮好不好看"，而是一条硬约束：

> **状态允许什么，只有一个出处。前端读它，后端也读它。**
> 两边读同一份数据，就不可能出现"前端说不能点、后端却接受了"。

本项目的做法是把这份"唯一真相"做成一个可查询的方法：

```java
order.allowedActions()     // 返回 {SHIP, CANCEL}
```

前端拿它渲染按钮，后端拿它做前置校验。**同一份规则，两个消费者。**

### 1.3 专业版：状态模式的三个关键词

GoF 对状态模式的定义是：

> 允许一个对象在其内部状态改变时改变它的行为。
> 对象看起来似乎修改了它的类。

这句话里最容易被读漏的是**"行为"**二字。它不是说"数据变了"，
而是说**同一个方法调用，执行了不同的代码**：

```java
order.cancel();   // 待支付时：释放库存
order.cancel();   // 已支付时：释放库存 + 原路退款
order.cancel();   // 已发货时：拒绝
```

**同一行代码，三种后果。** 而且调用方一个字都没改。

把这件事拆开，需要三个部件：

| 部件 | 职责 | 本项目的类 |
|---|---|---|
| **状态** | 回答"我这个状态下，这个动作意味着什么" | `OrderState` 及五个实现 |
| **上下文** | 持有当前状态，把业务动作转发给它 | `Order` |
| **动作** | 调用方真正想做的事（支付/发货/收货/取消） | `OrderAction` |

三者缺一不可：

- 只有状态没有上下文 → 状态对象不知道该改谁的状态；
- 只有上下文没有状态 → 又回到了 `switch (status)` 的老路；
- 没有动作这个概念 → 调用方只能 `setState(下一状态)`，绕过所有校验（原项目就栽在这里）。

### 1.4 什么时候**不该**用状态模式

先说清楚边界，免得读者看完就去把所有 `if` 都改成状态类。

**不该用的三种情况：**

| 情况 | 为什么不该用 | 该用什么 |
|---|---|---|
| 状态只有两三个，且**不会增加** | 两个类换两个 `if`，不划算 | 直接 `if` |
| 状态之间**没有行为差异**，只有数据不同 | 多态没有落脚点 | 一个枚举字段 |
| 转移规则**只有"转到哪"**，没有副作用 | 表比类更直观 | `Map` 转移表 |

第三条在本项目里有**可执行的证据**：`TransitionTable` 用一张表实现了完全相同的
状态序列，它只有 **227 物理行 / 71 行代码**；而「`OrderState` 接口 + 五个状态类」
合计 **613 物理行 / 141 行代码**。按物理行算是三分之一出头，按代码行算是一半。
它的局限也很具体——见 §6.5 的实测对比。

**该用的判据只有一条：**

> **同一个动作，在不同状态下的处理逻辑不同，而且这种不同无法用数据描述。**
> 能用一个字段（上限、天数、比例）描述的差异 → 用参数；
> 必须用一段代码描述的差异 → 用状态。

举个例子：审批金额档位（200/2000/20000）能用数据描述，所以责任链那章用了
**一个类四个实例**，不是四个类。而"取消"在不同状态下的后果
（释放库存 / 退款 / 拒绝）**没法用数据描述**，所以这里必须是三个实现。

---

## 2. 原始实现：原项目 130 行与它的七个问题

### 2.1 全貌：7 个文件，130 行

原项目在 `src/BehaviorType/StatePattern/`，一共 7 个文件：

| 文件 | 行数 | 职责 |
|---|---|---|
| `OrderState.java` | 13 | 接口：一个 `handleRequest(Order)` |
| `Order.java` | 27 | 上下文：持有状态 + `setState` + `request` |
| `PendingState.java` | 17 | 待支付：打印一句，切到已支付 |
| `PaidState.java` | 17 | 已支付：打印一句，切到已发货 |
| `ShippedState.java` | 17 | 已发货：打印一句，切到已完成 |
| `CompletedState.java` | 15 | 已完成：只打印一句 |
| `Client.java` | 24 | 入口：连调四次 `request()` |
| **合计** | **130** | |

原项目能跑，输出只有 4 行（真实运行结果）：

```
订单处于待支付状态，请支付。
订单已支付，请发货。
订单已发货，请等待接收。
订单已完成。
```

**从"能不能跑"的角度看，它没有问题。**

但从"它到底是不是状态模式"的角度看，问题很多——而且有几个是**结构性的**，
不是改两行就能修好的。

### 2.2 问题一：`request()` 没有参数，调用方无法表达意图

这是最根本的一个问题。看 `Order` 暴露的 API：

```java
class Order {
    public void request() {
        state.handleRequest(this);
    }
}
```

`request()` 这个名字说明不了任何事。**它到底是支付、是发货、还是查询？**

答案是：**都不是**。它的真实语义是"**把订单往前推一格**"。

于是：

- 用户只想看一眼订单状态，调了 `request()`，订单被"支付"了；
- 客服想查物流，调了 `request()`，订单被"发货"了；
- 后台批处理跑了一次对账，调了 `request()`，一批订单全部自动往前跳了一步。

更严重的是：**这个 API 无法表达"我想取消"**。因为每个状态只有一条出路，
`PendingState` 的唯一动作就是"去 PaidState"，没有第二条分支。

> 一个方法的参数表，暴露了它能不能表达意图。
> `request()` 没有参数，所以它什么意图都表达不了，只能表达"下一步"。

### 2.3 问题二：四个动作被压缩成一个，状态机退化成流水线

把这个设计推到极致会得到什么？

```
待支付 --request--> 已支付 --request--> 已发货 --request--> 已完成
```

**一条直线，没有分支。**

这意味着：

- 不能取消（没有叉路）；
- 不能退款（没有回退）；
- 不能拒绝（每一步都必然成功）；
- 不能停在原地（每次调用都往前走）。

**状态机的本质是"图"，不是"线"。** 一张图必须有分支、有汇合、有终点。
原项目画出来的是一条线，那它就不是状态机，是流水线。

看它的五个状态类，每个都只有三行实质代码：

```java
class PendingState implements OrderState {
    public void handleRequest(Order order) {
        System.out.println("订单处于待支付状态，请支付。");
        order.setState(new PaidState());       // ← 唯一的动作：切下一个
    }
}
```

**这五个类的内容是同构的**：打印一句话，然后切换到下一个。
把它们合并成一个类加一个 `List<OrderState>` 也能跑，而且更短。

> **当五个"状态类"结构完全相同时，说明状态之间的差异是假的。**
> 真正的状态模式里，每个状态类的内容必须**不一样**——
> 因为它要处理的动作集合不一样、副作用不一样、拒绝的理由不一样。

### 2.4 问题三：`setState` 是 `public`，谁都能跳状态

```java
class Order {
    public void setState(OrderState state) {   // ← public
        this.state = state;
    }
}
```

任何一段拿到 `Order` 引用的代码都能写：

```java
order.setState(new CompletedState());   // 跳过支付和发货，直接完成
order.setState(new PendingState());     // 从已完成倒回待支付
```

**状态机的全部约束，被一个 `public` setter 抹平了。**

而且这个 setter 是**必需的**——因为状态对象自己需要它来推进。这就成了一个死循环：

- 不给 setter → 状态对象无法切换状态，模式跑不起来；
- 给了 setter → 外部代码也能切换状态，约束失效。

本项目的解法是把"切换"这个动作从状态类里**拿走**，交给 `Order` 自己：

```java
private TransitionResult transition(OrderAction action) {
    TransitionResult result = state.accept(action, this);   // 状态只做决策
    if (result.success()) {
        state = result.to();                                // 订单自己执行切换
    }
    history.add(HistoryEntry.next(history.size(), result));
    return result;
}
```

状态只**返回**目标状态，不**执行**切换。切换只有一个写入口，而且和"成功"绑定。

> **让状态负责决策，让上下文负责执行。**
> 这条分界一旦划清，"谁能改状态"这个问题就不需要回答了——只有一处。

### 2.5 问题四：`CompletedState` 是终态，但再调一次不报错也不提示

```java
class CompletedState implements OrderState {
    public void handleRequest(Order order) {
        System.out.println("订单已完成。");     // ← 只有这一行
    }
}
```

已完成订单再调一次 `request()`，会打印"订单已完成"，然后什么也不做。

问题在于**调用方拿不到任何反馈**。方法返回 `void`，所以：

- 调用方无法知道这次操作是否生效；
- 日志里只有一行"订单已完成"，看不出这是"第一次到达"还是"第五次重复调用"；
- 如果是接口，客户端会收到 200，以为操作成功。

**"静默忽略"是最糟糕的一种错误处理**：它既不是成功，也不是失败，
而是一个谁也说不清的中间态。

本项目的处理是返回一个明确的拒绝：

```
[ 7] [--] 确认收货 ⊣ 已完成
     说明：「已完成」下不能「确认收货」；当前允许：（无，终态）
```

（这是真实输出，见 §6.3。）

### 2.6 问题五：每次转移都 `new` 一个状态对象

```java
order.setState(new PaidState());       // PendingState 里
order.setState(new ShippedState());    // PaidState 里
order.setState(new CompletedState());  // ShippedState 里
```

四个状态类**没有任何字段**，所有行为都由类本身决定。
这种情况下创建多个实例毫无意义：

- 一张订单走完流程创建了 3 个状态对象，它们彼此完全等价；
- 一亿张订单就是三亿个完全冗余的对象；
- 更麻烦的是**语义混乱**：读者会以为"状态对象里存了什么数据"，
  于是有人真的往里加字段（比如 `PaidState.paidAt`），然后发现第二张订单覆盖了第一张。

第二条是真实会发生的事故。因为状态对象是**所有订单共享**的，
往里塞实例数据就等于用一个全局变量存每张订单的信息。

本项目用 `enum` 实现五个状态：

```java
public enum PaidState implements OrderState {
    INSTANCE;
    ...
}
```

- 全场唯一实例，JVM 保证；
- 序列化安全（自己写 `static final` 单例做不到这点）；
- 可以放心用 `==` 比较；
- 反向强制了"不能往状态对象里塞实例数据"——**编译器帮你守住了架构边界**。

### 2.7 问题六：没有"允许的动作"，界面层只能自己再判断一遍

原项目的 `Order` 没有任何查询当前状态的能力：

```java
class Order {
    private OrderState state;      // private，没有 getter
    public void setState(...)      // 只有 setter
    public void request()          // 只有动作
}
```

于是渲染订单详情页时，前端必须自己维护一份状态映射：

```javascript
// 前端某处
const actions = {
  1: ['pay', 'cancel'],       // 1 = 待支付
  2: ['ship', 'cancel'],      // 2 = 已支付
  3: ['confirm'],             // 3 = 已发货
  4: [],                      // 4 = 已完成
};
```

**这份映射和后端的五个状态类是两份独立的规则。** 它们会：

- 在需求变更时不同步（后端加了"已支付可退款"，前端不知道）；
- 在两边对状态编号的理解不一致时出错（后端 2 是已支付，前端以为是已发货）；
- 出问题时无法判断哪边是对的。

本项目的做法是把这份规则**暴露出去**：

```java
public Set<OrderAction> allowedActions() {
    return state.allowedActions();
}
```

前端遍历 `OrderAction.values()`，用 `allowedActions().contains(action)` 决定按钮状态。
**规则只有一个出处，两边读同一份数据。**

### 2.8 问题七：没有轨迹，出问题只能靠猜

原项目的 `Order` 只有一个 `state` 字段。一次操作之后：

- 之前是什么状态？不知道；
- 中间被拒绝过几次？不知道；
- 谁在什么时候做了这一步？不知道。

如果用户投诉"我的订单莫名其妙变成已取消了"，你能查到的只有"当前是已取消"。
**这个信息量等于零**——它连"取消了几次"都回答不了。

本项目给 `Order` 加了一条轨迹，**成功和失败都记**：

```
轨迹：共 7 步（3 步成功，4 步被拒绝）
  #1 发货 ⊣ 待支付
  #2 支付：待支付 → 已支付
  #3 支付 ⊣ 已支付
  #4 发货：已支付 → 已发货
  #5 取消 ⊣ 已发货
  #6 确认收货：已发货 → 已完成
  #7 确认收货 ⊣ 已完成
```

（这是真实输出，见 §6.3。）

看这段轨迹能立刻回答三个问题：

1. 这张单被非法操作过 4 次（说明上游有问题）；
2. 每次非法操作的具体原因（`#5` 是已发货不能取消）；
3. 合法的路径走对了没有。

> **只记成功 = 只留下了结果，丢掉了过程。**
> 而排查问题时，过程永远比结果值钱。

### 2.9 一句话总结原项目

如果把原项目的问题压缩成一句话：

> **它把"状态机"写成了一条"单行道"：每个状态只有一条出路，
> 每次调用必然前进一格，没有分支、没有拒绝、没有反馈、没有轨迹。
> 于是它既没得到状态模式的能力（同一动作不同后果），
> 又付出了状态模式的成本（五个类 + 一个 public setter）。**

这个判断决定了本次重构的方向：

- **不需要**保留"没有参数的动作"——改成四个明确的业务动作；
- **需要**让每个状态**内容不同**——不同的允许集合、不同的副作用、不同的拒绝理由；
- **需要**另外提供一个**转移表**对照，说明什么时候不该用这个模式。

---
## 3. 设计动机：状态模式到底解决了什么

### 3.1 它解决的唯一问题是"行为随状态变"

先说清楚它**不**解决什么，免得误用：

- 它**不**解决"用哪种算法"（那是策略：算法由调用方选，选完就固定）；
- 它**不**解决"该谁处理"（那是责任链：处理者接力，请求横向传递）；
- 它**不**解决"通知谁"（那是观察者：状态变了广播出去）；
- 它**不**解决"流程编排"（那是工作流引擎或者模板方法）。

它只解决一件事：

> **对象的状态变了，它响应同一个方法的行为也要跟着变，
> 而调用方不该为此写任何分支。**

请注意最后半句：**"调用方不该写分支"**。
这是状态模式和 `if-else` 唯一真正的区别，也是唯一值得为它付出代价的理由。

因为 `if-else` 也能实现"行为随状态变"——它甚至更短。区别在于**分支写在哪里**：

| | `if-else` | 状态模式 |
|---|---|---|
| 分支写在哪 | **调用方**（每个调用点一份） | **状态类**（全局一份） |
| 新增一个调用点 | 要抄一遍分支 | 直接调，不用管状态 |
| 分支和规则的关系 | 规则散落在 N 个地方 | 规则收在状态类里 |
| 谁需要知道全部状态 | **每个调用方** | 只有 `OrderState` 的实现 |

**"分支写在哪里"就是全部的区别。** 其他所有优缺点都是这句话的推论。

### 3.2 一个必须诚实面对的对比：`if-else` 版本并不差

先别急着下结论。下面是一段完全正确的 `if-else` 实现：

```java
public void cancel(Order order) {
    switch (order.getStatus()) {
        case PENDING -> {
            inventoryService.release(order);
            order.setStatus(CANCELLED);
        }
        case PAID -> {
            inventoryService.release(order);
            paymentService.refund(order);
            order.setStatus(CANCELLED);
        }
        case SHIPPED -> throw new BizException("已发货，请收货后申请退货");
        default -> throw new BizException("当前状态不能取消");
    }
}
```

**这段代码没什么可挑的。** 它清晰、直白、一眼能看懂所有分支。

如果有人跟你说"这段代码有设计问题"，那他大概率是在套模板。

那它的问题在哪？**不在这个方法内部，而在这个方法被复制的时候。**

真实系统里，"取消订单"这个动作至少有四个入口：

| 入口 | 谁在调 |
|---|---|
| 买家 App 的取消按钮 | 用户 |
| 客服后台的取消工单 | 客服 |
| 超时未支付自动关单 | 定时任务 |
| 风控系统拦截后强制关单 | 外部回调 |

四个入口，四个服务类。如果每个都写一遍上面的 `switch`，会怎样？

- 第一个入口：写了 `PENDING` 和 `PAID` 两个分支，`SHIPPED` 抛异常。
- 第二个入口：抄了一遍，忘了 `SHIPPED` 的分支，`default` 兜底成"当前状态不能取消"——
  功能上碰巧对，但**错误信息变成了误导**（客服以为是状态非法，其实是已发货）。
- 第三个入口：定时任务只处理 `PENDING`，其他状态直接 `continue`。合理。
- 第四个入口：风控强制关单，**故意不校验状态**——"强制"的意思就是不管什么状态都关。

看到问题了吗？**第四个入口是合理的例外，前三个是真实的重复。**

到了这一步，团队会怎么处理？

- 把 `switch` 抽成一个 `OrderService.cancel()` 方法，四个入口都调它 —— **这已经是状态模式的思路了**（把分支收进一处）；
- 但第四个入口"强制关单"需要绕过校验，于是又得从 `cancel()` 里再拆一个 `forceCancel()` 出来，两个方法各有一份 `switch`；
- 再往后，"已发货能不能取消"这条规则被客服要求改成"可以取消但要拦截快递"，于是要改**两处**。

> **`if-else` 的问题不是"写得丑"，而是"它必然会被复制"。**
> 一旦复制，规则就有了多份；一旦有多份，就一定有不同步的那一天。

### 3.3 状态模式把"复制"变成了"编译期不可能"

换成状态模式之后，结构变成：

```java
public enum ShippedState implements OrderState {
    INSTANCE;

    private static final Set<OrderAction> ALLOWED = Set.of(OrderAction.CONFIRM);

    @Override
    public Set<OrderAction> allowedActions() {
        return ALLOWED;                              // ← 规则只有这里一处
    }

    @Override
    public TransitionResult accept(OrderAction action, Order order) {
        return switch (action) {
            case CONFIRM -> { ... }
            case PAY, SHIP, CANCEL -> throw OrderState.unreachable(this, action);
        };
    }
}
```

现在，四个入口都这么写：

```java
TransitionResult result = order.cancel();      // 不需要 switch，不需要知道状态
```

而"已发货不能取消"这条规则，**只存在于 `ShippedState.ALLOWED` 这一个集合里**。

第四个入口（风控强制关单）怎么办？它需要的是**另一个业务动作**：

```java
public TransitionResult forceCancel() { ... }   // 规则不同 → 动作不同 → 单独建模
```

而不是"在 `cancel()` 里加一个 if"。**这是状态模式带来的一个额外好处：
它会逼你把"语义不同的动作"拆开，而不是塞进同一个方法里用参数区分。**

### 3.4 三种实现方式的完整对比

在动手推导之前，先把三条路的账算清楚。这张表是后面所有设计选择的依据：

| 维度 | 枚举 + `switch` | 转移表 | 状态模式 |
|---|---|---|---|
| **代码量**（本示例） | 约 150 行，2 个类 | 约 230 行，1 个类 | 约 1950 行，12 个类 |
| **规则可见性** | 中，分散在 N 个 `switch` | **最好**，一眼看全 | 差，要开 5 个文件 |
| **加一个状态** | 改所有 `switch` | 改表 + 加枚举值 | 加一个类 + 改 `permits` |
| **加一个动作** | 改所有 `switch` | 改表 | **改 5 个类的 `switch`** |
| **能在转移时做副作用** | 能 | **不能** | 能 |
| **编译期能查漏吗** | `switch` 穷尽检查能 | **不能**，漏填就是 `null` | 能 |
| **能表达"拒绝"吗** | 能（`else` 分支） | 弱（查不到 = `null`） | 强（`allowedActions` 少一个元素） |
| **适合什么规模** | 2~3 个状态、1~2 个动作 | 状态多、动作多、无副作用 | 动作在多状态间差异大 |

**这张表里最该看的是两行：**

1. **"代码量"那一行**：状态模式是转移表的 8 倍。这不是笔误——
   本项目为了讲清楚，注释写得很多；但即使只看代码行（624 行），
   也是转移表（71 行）的近 9 倍。**这是一笔必须用真实收益来还的账。**
2. **"能在转移时做副作用"那一行**：这是状态模式**唯一无法被替代**的能力。
   其他所有能力，`switch` 和表都能做到，只是形式不同。

所以判据可以收成一句话：

> **如果转移时不需要做任何事，就别用状态模式。**
> 一旦需要"发货时生成单号""取消时释放库存""支付时记账"，表就无能为力了——
> 因为表里每个格子只能存"目标状态"这一个值。

这条判据在本项目里有**可执行的证明**，见 §6.5：用同一张单、同一套规则，
表跑出了完全相同的状态序列，但**物流单号是空的，订单对象的状态还停在"待支付"**。

---

## 4. 设计推导：从"补一个 if"推到 12 个文件

### 4.0 推导路线表

本节按"**遇到一个问题 → 做一个决策 → 得到一个类**"的顺序推进。
每一步都先说明**不这么做会怎样**，再说为什么选这个方案。

| 步 | 遇到的问题 | 决策 | 产出 |
|---|---|---|---|
| 0 | 状态之间到底是什么关系 | 先画一张状态图，不写代码 | （一张图） |
| 1 | `request()` 无法表达意图 | 把动作抽成枚举 | `OrderAction` |
| 2 | 状态接口该有几个方法 | 三个：名字、允许集合、处理动作 | `OrderState` |
| 3 | 谁执行状态切换 | 上下文执行，状态只做决策 | `Order.transition` |
| 4 | "拒绝"怎么表达 | 返回值而不是异常 | `TransitionResult` |
| 5 | 转移时的副作用放哪 | 状态调上下文的包内写方法 | `markPaid` 等 |
| 6 | 状态用 `enum` 还是类 | 无实例数据 → `enum` | 5 个枚举 |
| 7 | 允许集合和处理逻辑会不一致 | 前置校验 + 不可达分支抛异常 | （无新类） |
| 8 | 出问题查不到过程 | 轨迹，成功失败都记 | `HistoryEntry` |
| 9 | 什么时候别用这套东西 | 诚实写一个表驱动对照 | `TransitionTable` |

### 4.1 第 0 步：先画图，别先写代码

写状态模式最常见的一个错误是：**先建类，再想转移关系**。
结果是五个类建好了，然后发现某个状态根本没有出口，或者两个状态应该合并。

正确的顺序正好相反：**先在纸上画出状态图**，把所有边编号，
再决定要建几个类。

本示例的状态图：

```
                    ┌──────────────┐
        创建 ──────►│    待支付     │
                    └──┬────────┬──┘
                  支付 │        │ 取消
                       ▼        ▼
                    ┌──────┐  ┌────────┐
                    │已支付 │  │ 已取消 │◄─── 终态
                    └─┬──┬─┘  └────────┘
                 发货 │  │ 取消     ▲
                      ▼  └──────────┘
                    ┌──────┐
                    │已发货 │
                    └───┬──┘
                 确认收货│
                        ▼
                    ┌──────┐
                    │已完成 │◄─── 终态
                    └──────┘
```

从这张图能直接读出五个事实，它们决定了后面的所有代码：

| 图上看到的事实 | 对应的设计决策 |
|---|---|
| 有 5 个节点 | 5 个状态类 |
| 有 6 条边（支付/取消/发货/取消/确认收货） | 6 个合法转移 |
| 有 2 个终态（无出边） | `allowedActions()` 返回空集合 |
| 有 2 个节点能通向"已取消" | 取消的后果取决于从哪来（一个退款，一个不退款） |
| 有 4 种边标签 | 4 个动作（`OrderAction`） |

**特别注意第四行**：`已取消` 有两个入边，来自 `待支付` 和 `已支付`。
这是本示例最重要的一个设计点——同一个动作、同一个终点，
但**副作用完全不同**（一个只释放库存，一个还要退款）。

如果先建类再想图，很容易把"取消"统一成一个 `CancelledState` 的构造参数，
于是取消原因、退款金额这些东西就开始往状态对象里塞——
而状态对象是**共享单例**，塞进去就串数据了（见 §2.6 和 `CancelledState` 的类注释）。

### 4.2 第 1 步：把"动作"抽出来

**遇到的问题**：`request()` 没有参数，调用方无法表达"我要取消"。

**如果直接改**：把 `request()` 改成 `request(String action)` 或者 `request(int type)`。

**为什么不行**：

- 字符串写错只有运行时才发现（`"cancle"` 和 `"cancel"` 编译器不管）；
- `int` 更糟，`1` 是支付还是发货，只有写代码的人知道；
- 参数是开放的，谁都能传一个没定义过的值。

**决策**：用 `enum`，因为动作集合是**封闭**的。

```java
public enum OrderAction {
    PAY("支付"),
    SHIP("发货"),
    CONFIRM("确认收货"),
    CANCEL("取消");

    private final String label;
    ...
}
```

**这一决策带来的三个能力**（都是编译期的，不是运行期的）：

1. **写错名字编译不过。** `OrderAction.CANCLE` 直接报错。
2. **`switch` 可以穷尽。** 将来加第五个动作时，所有没处理它的 `switch` 会**编译失败**，
   而不是在运行时掉进 `default` 悄悄走错分支。
3. **可以放进 `Set` 做集合运算。** `allowedActions().contains(action)` 是 O(1)。

第 2 条最关键，值得单独说：

> **`enum` + 穷尽 `switch` 的组合，是 Java 里唯一能强制"新增枚举值时必须回来处理"的机制。**
> 用 `default` 会毁掉这个机制——所以本项目的五个状态类里，
> **一个 `default` 都没有**，全部显式列出四个分支。

看 `PendingState.accept()` 的真实写法：

```java
return switch (action) {
    case PAY -> { ... }
    case CANCEL -> { ... }
    case SHIP, CONFIRM -> throw OrderState.unreachable(this, action);
};
```

`SHIP, CONFIRM` 这两个分支**永远不会执行**（`Order.transition()` 会先拦住它们），
但它们**必须写出来**——否则 `switch` 不穷尽，编译不过。

这就是"用编译期检查换几行死代码"，非常划算：
将来有人加了 `REFUND` 动作，这五个 `switch` 会同时报错，
**逼着他回到每一个状态想清楚"退款在这个状态下意味着什么"**。

### 4.3 第 2 步：状态接口只留三个方法

**遇到的问题**：状态接口该定义什么方法？

原项目的接口只有一个方法：`void handleRequest(Order order)`。
本项目的接口有三个：

```java
public sealed interface OrderState
        permits PendingState, PaidState, ShippedState, CompletedState, CancelledState {

    String displayName();                                    // 我叫什么
    Set<OrderAction> allowedActions();                        // 我这儿能做什么
    TransitionResult accept(OrderAction action, Order order); // 做这件事会怎样
}
```

**为什么是这三个，而不是别的？**

因为状态需要回答三类**不同消费者**的问题：

| 方法 | 谁在用 | 用来干什么 |
|---|---|---|
| `displayName()` | 日志、界面、轨迹 | 把状态变成人能读的字符串 |
| `allowedActions()` | 界面渲染、前置校验 | 决定哪些按钮可点、哪些动作要拦 |
| `accept()` | 业务逻辑 | 真正执行动作、产生副作用、给出目标状态 |

**如果把这三件事混成一个方法会怎样？** 看两种糟糕的写法：

**糟糕写法一**：只留 `accept()`，让调用方自己判断能不能做。

```java
// 调用方得先问一遍再做事，但它没地方问 —— 只能 try-catch
try {
    order.cancel();
} catch (IllegalStateException e) {
    // 能不能取消变成了"试一下才知道"
}
```
界面层渲染时更痛苦：它得对每个动作都 try 一次才能知道按钮该不该灰。
**用异常做控制流，是性能问题，更是可读性问题。**

**糟糕写法二**：只留 `allowedActions()`，判断全交给调用方。

```java
if (order.allowedActions().contains(CANCEL)) {
    // 这里要写一遍"取消该做什么" —— 分支又回到调用方了
    if (order.state() == PENDING) { ... }
    else if (order.state() == PAID) { ... }
}
```
**等于什么都没做**——状态模式的价值就是把这段分支挪走。

所以三个方法缺一不可：一个给日志，一个给界面，一个给业务。

**再看一个刻意没写的方法：`isFinal()`。**

终态的本质是"没有允许的动作"，也就是 `allowedActions().isEmpty()`。
多写一个 `isFinal()` 就多了一个可能和 `allowedActions()` 打架的真相来源：

- 有人把 `isFinal()` 改成 `true`，忘了清空动作集合；
- 于是"终态"的订单还能被取消；
- 而这种 bug 只在特定路径下出现，测试很难覆盖。

> **凡是能从已有方法推导出来的属性，就不要加第二个方法。**
> 两个真相来源 = 一个必然会出现的矛盾。

### 4.4 第 3 步：谁执行状态切换

**遇到的问题**：状态对象决定"要转到哪"，那它能不能自己转？

**原项目的做法**：能，而且只能它来转——所以 `setState` 必须是 `public`（见 §2.4）。

**如果沿用**：外部代码也能拿到这个 `public` 方法，
于是"跳过支付直接完成"这种事永远防不住。

**决策**：把切换从状态类里拿走，交给 `Order`，并且设为唯一写入口。

```java
private TransitionResult transition(OrderAction action) {
    Objects.requireNonNull(action, "动作不能为 null");

    TransitionResult result;
    if (state.allowedActions().contains(action)) {
        result = state.accept(action, this);          // 状态：决策 + 副作用
    } else {
        result = TransitionResult.rejected(action, state,
                "「" + state.displayName() + "」下不能「" + action.label()
                        + "」；当前允许：" + describeAllowed());
    }

    if (result.success()) {
        state = result.to();                          // 订单：执行切换
    }
    history.add(HistoryEntry.next(history.size(), result));
    return result;
}
```

**这个方法的四步顺序不能换，每一步都在守一条不变量：**

| 顺序 | 做什么 | 守住的不变量 |
|---|---|---|
| 1 | 前置校验 | 非法动作**不进入** `accept()`，不会撞进"不可达"异常 |
| 2 | 问状态 | 状态只做决策和副作用，**不碰** `state` 字段 |
| 3 | 成功才切 | **失败绝不转移**（这是整个类最重要的一行） |
| 4 | 记轨迹再返回 | 调用方看到的和轨迹里存的**必然一致** |

**第 1 步为什么必需？** 这是个真实的坑。最初的版本没有前置校验：

```java
// 错误版本
TransitionResult result = state.accept(action, this);   // ← 直接进
```

看起来没问题，因为 `CompletedState.accept()` 里是 `throw unreachable(...)`。
但它意味着：**用户在已完成订单上再点一次"确认收货"，会收到一个 `IllegalStateException`。**

- Web 层没 catch → 500 错误；
- 批量任务里一张单子的正常拒绝 → 中断整批；
- 日志里刷满异常栈 → 真正的故障被淹掉。

**一个正常的业务拒绝，被写成了程序崩溃。** 加上前置校验之后，
它变成了一条干净的 `[--] 确认收货 ⊣ 已完成`（真实输出，见 §6.3）。

**第 3 步为什么是关键？** 因为它是唯一一个"如果不写就会出大事、但看起来又很自然"的地方。
`result.success()` 这个判断看起来是多余的——"状态都返回成功的结果了，还能有假？"
但 `accept()` 是五个类各自实现的，任何人写错一行 `TransitionResult.ok(...)`
都会绕过它。把判断放在这里，**五个类一起受保护**。

### 4.5 第 4 步："拒绝"怎么表达

**遇到的问题**：动作不被允许时，怎么告诉调用方？

**三个候选方案**：

| 方案 | 优点 | 致命缺点 |
|---|---|---|
| `void` + 打印日志 | 简单 | 调用方**什么都收不到**（原项目的做法） |
| 抛异常 | 强制处理，不会漏 | 把正常业务分支当故障（见上） |
| 返回 `boolean` | 简单 | 只有"成不成"，没有"为什么" |
| **返回 `TransitionResult`** | 有成败、有原因、有前后状态 | 多一个类 |

**决策**：`TransitionResult`，一个 `record`。

```java
public record TransitionResult(
        boolean success,
        OrderAction action,
        OrderState from,
        OrderState to,        // 成功时非空，失败时必须是 null
        String message
) { ... }
```

**为什么不用 `Optional<OrderState>`？** 它只能表达"转到哪 / 没转"，
表达不了"为什么没转"。而"为什么"恰恰是客诉和排查时最需要的信息：

```
说明：「已发货」下不能「取消」；当前允许：确认收货
```

这句话比一个 `empty()` 有用一百倍——它直接告诉调用方**现在能做什么**。

**为什么 `record` 而不是普通类？** 因为它有两条必须被钉死的不变量：

1. 成功必须有目标状态（否则调用方 `to()` 拿到 null）；
2. 失败必须没有目标状态（否则"失败但转了"这种自相矛盾的对象会流出去）。

`record` 的**紧凑构造器**能把这两条写在对象创建处：

```java
public TransitionResult {
    Objects.requireNonNull(action, "动作不能为 null：失败的结果也要能说清是哪个动作失败了");
    Objects.requireNonNull(from, "起始状态不能为 null");
    Objects.requireNonNull(message, "结果说明不能为 null：没有说明的失败等于没说");
    if (success && to == null) {
        throw new IllegalArgumentException("转移成功必须给出目标状态");
    }
    if (!success && to != null) {
        throw new IllegalArgumentException("转移失败不应该有目标状态，实际给出：" + to.displayName());
    }
}
```

于是**任何一个 `TransitionResult` 实例都必然自洽**。

后续所有用它的人（打印、写库、前端渲染）都不需要再考虑
"万一 `success` 是 true 但 `to` 是 null 怎么办"——
**这个"万一"在类型层面被消灭了。**

还有一个小设计值得说：**失败构造器不接收 `to` 参数**。

```java
public static TransitionResult rejected(OrderAction action, OrderState from, String reason) {
    return new TransitionResult(false, action, from, null, reason);
}
```

从签名上就不给你传错的机会。**能用 API 形状避免的错误，不要靠文档和注释去避免。**

---
### 4.6 第 5 步：转移时的副作用放哪

**遇到的问题**：支付要记录"付过了"，发货要生成物流单号，取消要记录原因。
这些**业务数据**在转移时产生，该由谁写？

**三个候选方案**：

| 方案 | 写法 | 问题 |
|---|---|---|
| A. 状态自己持有数据 | `PaidState.paidAt = now()` | **状态是共享单例，会串数据** |
| B. 状态把数据返回给调用方 | `result = accept(); result.paidAt()` | 调用方要一个个判断"这次该写什么"，分支又回去了 |
| C. 状态调上下文的写方法 | `order.markPaid()` | 状态能改数据，需要约束边界 |

**方案 A 是最危险的一个**，而且它看起来最自然：

```java
// 错误示范
public enum PaidState implements OrderState {
    INSTANCE;
    private LocalDateTime paidAt;      // ← 灾难的开始
}
```

为什么说是灾难？因为 `PaidState.INSTANCE` 是**全场唯一**的。
第一张订单支付时设了 `paidAt = 10:00`，第二张订单支付时覆盖成 `10:05`。
于是所有订单的支付时间都是最后一次支付的时间。

> **状态对象存的是"这一类状态怎么表现"，不是"这一张订单发生了什么"。**
> 前者是**行为**，全局唯一；后者是**数据**，一单一份。

这条边界一旦越过，后面所有的好处都会连锁崩塌：
状态不能是 `enum` → 不能享受单例和序列化安全 →
`switch` 不能穷尽 → 丢掉了编译期保护。

**方案 B 的毛病**是它把"写数据"这件事又推回给调用方：

```java
TransitionResult result = order.ship();
if (result.success()) {
    order.setTrackingNo(result.payload().trackingNo());   // ← 又是 setter
}
```

调用方开始承担"我这次动作该写哪些字段"的责任，而这正是状态模式要消灭的东西。

**决策**：方案 C——状态调用 `Order` 上几个**包内可见**的写方法。

```java
// Order 中
void markPaid() {
    this.paid = true;
}

String issueTrackingNo() {
    if (trackingNo != null) {
        throw new IllegalStateException("订单 " + orderNo + " 的物流单号已经生成过："
                + trackingNo + "；重复发货说明有代码绕过了状态机");
    }
    this.trackingNo = "SF" + digitsOf(orderNo);
    return trackingNo;
}

void markDelivered() {
    this.delivered = true;
}

void markCancelled(String reason) {
    Objects.requireNonNull(reason, "取消原因不能为 null");
    this.cancelReason = reason;
}
```

四个方法，对应四件真实发生的事。**没有 `setState`，没有 `setPaid`，没有通用 setter。**

**这些方法为什么是包内可见（无修饰符）？**

因为它们不是业务 API，是**状态机的内部协议**。外部代码不该说
"把这张单标记成已付款"——它应该说"支付"（`order.pay()`）。
前者跳过了一切校验，后者会完整走一遍状态机。

**但包级可见不是绝对安全**：同一个包里的 `Client` 也能调 `order.markPaid()`。

这是 Java 包级封装的已知限制。本项目的处理方式不是假装它不存在，而是：

1. **用命名标出来**——`markPaid` 听起来就是"内部记账"，不像业务动作；
2. **用 javadoc 说清楚**——每个方法都写明"只允许某状态在成功后调用"；
3. **在 `Order` 的注释里承认这个弱点**——

```java
/**
 * <p>包内可见（无修饰符）不是绝对安全：同一个包里的 {@code Client} 也能调。
 * 这是 Java 包级封装的已知限制，本项目的处理方式是
 * <b>用命名和文档把"内部协议"标出来</b>，而不是假装它不存在。
 * 如果确实需要强隔离，把状态类放进 {@code Order} 的嵌套类即可——
 * 但那样会牺牲包结构的可读性，得不偿失。
 */
```

**如果真的要强隔离怎么做？** 把五个状态类做成 `Order` 的嵌套枚举：

```java
public final class Order {
    private enum PaidState implements OrderState { ... }   // 外部完全看不见
}
```

这样外部连 `PaidState.INSTANCE` 都拿不到，`markPaid()` 也能设成 `private`。
但代价是 `Order.java` 会变成 700 行的巨型文件，五个状态的行为挤在一起，
**失去了"打开一个文件就知道这个状态能做什么"的可读性**。

> **封装强度和可读性在这里是一对权衡。**
> 本项目选可读性，理由是这个 Demo 的读者比攻击者重要得多。
> 真实项目里如果状态变更涉及资金，应该选嵌套类 + 私有方法。

### 4.7 第 6 步：状态用 `enum` 还是普通类

**判据只有一条：状态对象有没有实例数据。**

| 情况 | 选择 | 理由 |
|---|---|---|
| 状态只有行为，没有数据 | `enum` | 天然单例、序列化安全、可 `==` 比较 |
| 状态要携带数据（如截止时间、退款金额） | `record` / 普通类 | 每次都是不同实例，不能用单例 |

**本示例的五个状态全部没有实例数据**，所以全部用 `enum`：

```java
public enum PendingState implements OrderState {
    INSTANCE;
    ...
}
```

**但现实里"状态带数据"的场景非常多**，这里给一个反例，说明什么时候必须换成 `record`：

```java
// 假设需求变成："限时折扣中"是一个状态，要记住什么时候结束
public record FlashSaleState(Instant endsAt, BigDecimal discountRate)
        implements OrderState {

    @Override
    public String displayName() {
        return "限时折扣（至 " + endsAt + "）";
    }
    ...
}
```

这个状态**不能**用 `enum`，因为 `endsAt` 每个活动都不同。
如果强行做成 `enum`，就回到了 §4.6 方案 A 的老路——数据串台。

**`enum` 的三个好处分别是什么？**

1. **单例由 JVM 保证。** 自己写 `static final` 单例做不到这点：
   反序列化会造出新对象（除非写 `readResolve`），反射也能。
   `enum` 的实例由 JVM 在类加载时创建，任何手段都造不出第二个。
2. **可以放心用 `==`。** 单例意味着引用相等就是值相等，省掉 `equals` 调用。
   在 `Map<OrderState, ...>` 的查找里，`==` 比 `equals` 快一个数量级。
3. **`switch` 可以穷尽。** `sealed interface` 也支持这点，但 `enum` 更简洁——
   一个 `permits` 列表 vs 五个 `case`。

**代价是什么？** `enum` 不能继承其他类（`java.lang.Enum` 已经占了那个位置），
所以五个状态**无法共享一个抽象父类**。

这正好挡住了本项目里一个诱人的错误：把两个终态抽成 `abstract class FinalState`
来共享那一行 `return Set.of();`。

> **为了一行代码，牺牲 enum 的三个好处，是一笔明显的亏本买卖。**
> 而且"两个终态现在长得一样"不等于"它们是一个概念"——
> 已完成将来要支持申请售后，已取消将来要支持恢复订单，它们迟早分道扬镳。

### 4.8 第 7 步：允许集合和处理逻辑会打架

**遇到的问题**：`allowedActions()` 和 `accept()` 是**两个独立的实现**。

```java
private static final Set<OrderAction> ALLOWED = Set.of(PAY, CANCEL);   // 说允许 PAY

public TransitionResult accept(OrderAction action, Order order) {
    return switch (action) {
        case CANCEL -> { ... }                    // 只处理了 CANCEL
        case PAY, SHIP, CONFIRM -> throw ...;     // PAY 跑到这里了！
    };
}
```

有人改了集合忘了改 `switch`（或者反过来），结果就是：

- `allowedActions()` 说"可以支付"→ 前端显示支付按钮；
- 用户点了 → `accept()` 走进不可达分支 → **`IllegalStateException` 抛到用户脸上**。

**这是编程错误，不是业务错误。** 处理它只有一条正确的路：**让它在开发期就崩掉**。

**本项目布了三道防线：**

**第一道：`Order.transition()` 的前置校验**

```java
if (state.allowedActions().contains(action)) {
    result = state.accept(action, this);      // 只有集合说允许，才进 accept
} else {
    result = TransitionResult.rejected(...);  // 否则直接拒绝，不碰 accept
}
```

这保证了 `accept()` **永远只会收到它自己声明允许的动作**。
所以那行 `throw OrderState.unreachable(...)` 在正常路径下永远走不到。

**第二道：不可达分支抛异常，而不是 `default` 返回一个假结果**

```java
static IllegalStateException unreachable(OrderState state, OrderAction action) {
    return new IllegalStateException(
            "[" + state.displayName() + "] 收到了不该收到的动作「" + action.label()
                    + "」：allowedActions() 与 accept() 的实现不一致，"
                    + "这是一个编程错误，不是业务错误");
}
```

**为什么不 `default -> TransitionResult.rejected(...)`？**

因为那会把**结构性错误**伪装成**正常业务拒绝**：

- 日志里出现一条"已完成不能支付"，看起来完全正常；
- 真正的原因是某个状态类的 `switch` 写漏了；
- 这个 bug 会一直存在，直到有人恰好对比了 `allowedActions()` 的输出。

> **让错误在错误的地方大声崩掉，比让它静静地伪装成正常结果好一万倍。**

**第三道：Demo 5 的 20 组合全覆盖自检**

前两道防线只能保证"运行时不出事"，不能保证"规则没写错"。
所以 `Client.demo5` 里做了一次**穷尽遍历**：

```java
for (OrderState state : TransitionTable.states()) {           // 5 个状态
    for (OrderAction action : OrderAction.values()) {          // 4 个动作
        boolean stateSays = state.allowedActions().contains(action);
        boolean tableSays = TransitionTable.allows(state, action);
        if (stateSays != tableSays) { mismatch++; }            // 两份规则对比
        if (stateSays) {
            Order probe = new Order("PROBE-1", "1.00");
            TransitionResult result = state.accept(action, probe);   // 真跑一次
            if (result.success()) { acceptedOk++; } else { mismatch++; }
        }
    }
}
```

真实输出（§6.6）：

```
--- 自检：5 个状态 × 4 个动作 = 20 种组合 ---
  状态类说允许：5 种；说禁止：15 种
  其中 accept() 实际成功：5 / 5
  两份规则（状态类 vs 转移表）不一致：0 处
```

**这段自检的巧妙之处在于"两份规则互为校验"**：

- `allowedActions()` 写在五个状态类里；
- 转移规则写在 `TransitionTable.TABLE` 里。

它们是**两个独立的数据源**，但描述的是**同一套业务规则**。
如果哪天有人改了其中一个忘了改另一个，这里会立刻报出不一致。

> **重复不一定是坏事——如果两份重复是用来互相校验的。**
> 这叫"冗余校验"（redundancy check），和"复制粘贴的重复"有本质区别：
> 前者是故意留两份并对它们，后者是无意识地留两份且从不对。

### 4.9 第 8 步：出问题查不到过程

**遇到的问题**：只留最终状态，等于没留任何信息。

**决策**：加一条轨迹，成功和失败都记。

**为什么 `HistoryEntry` 要单独一个类，而不是直接 `List<TransitionResult>`？**

因为"第几步"这件信息**不属于转移结果本身**，它属于历史的位置。

```java
public record HistoryEntry(int step, TransitionResult result) { ... }
```

如果硬塞进 `TransitionResult`：

- 同一个转移结果在不同位置就变成**不同的值**；
- `equals` 失去意义（两个内容相同的结果因为步号不同而不相等）；
- 想把一条结果挪个位置都做不到。

**这跟责任链里 `ApprovalContext.Step` 的思路一致**：
结果描述"发生了什么"，轨迹描述"它排在第几位"。两个职责，两个类型。

**步号为什么从 1 开始？** 因为给人看的。第 0 步读起来像"还没开始"，
而轨迹的第一条正是"第一次尝试"。

紧凑构造器会把这条规则钉死：

```java
public HistoryEntry {
    if (step <= 0) {
        throw new IllegalArgumentException("步号从 1 开始，实际传入：" + step);
    }
    Objects.requireNonNull(result, "轨迹条目必须带结果");
}
```

**为什么失败的那几步也要记？** 这是最容易被省掉的东西。
只记录成功的转移看起来"干净"，但现实中最需要排查的恰恰是被拒绝的那几次：

| 现象 | 轨迹能回答什么 |
|---|---|
| 用户投诉"我点不了取消" | `#5 取消 ⊣ 已发货` → 是已发货状态拒绝了 |
| 接口突然大量报错 | 轨迹里全是同一条拒绝 → 上游在批量发错动作 |
| 运营问"这张单经历了什么" | 每一步都在，包括没走通的那几步 |

**"只记成功 = 只留下了结果，丢掉了过程。"** 而排查问题时，过程永远比结果值钱。

**最后一个细节：`history()` 返回的是 `List.copyOf(history)`，不是 `history` 本身。**

```java
public List<HistoryEntry> history() {
    return List.copyOf(history);
}
```

- 直接返回 `history`：调用方一个 `clear()` 就把审计记录抹掉了；
- 返回 `Collections.unmodifiableList(history)`：这只是个**视图**，
  内部一追加，外面"已经拿到的那份"也跟着变——拿来做快照是错的；
- `List.copyOf`：**真拷贝**，返回的那一刻就是快照。

> **不可变视图和不可变快照是两回事。**
> 前者禁止你改，但它自己会变；后者在你手里的那一刻就冻结了。
> 需要"某一时刻的记录"时，必须用后者。

### 4.10 第 9 步：什么时候别用这套东西

前面九步都在说"怎么做"，这一步说"什么时候不该做"。

**本项目刻意写了一个 `TransitionTable`（227 行，其中代码 71 行）**，
用一张 `Map<OrderState, Map<OrderAction, OrderState>>` 实现了**完全相同的转移规则**。

它的存在不是为了炫技，是为了**证明状态模式的成本是必要的**。
§6.5 会用同一张订单跑两条路径，让差异变成可验证的事实：

- 表跑出的状态序列：**完全相同**；
- 表算出的物流单号：**空**；
- 表的执行结束后订单对象的状态：**还停在"待支付"**（表根本没碰过它）。

**所以判据是：**

> **转移只有"转到哪"，用表；转移还要顺手做事，用状态模式。**

还有两条辅助判据：

| 判据 | 用表 | 用状态模式 |
|---|---|---|
| 状态/动作数量 | 多（8 个状态 × 10 个动作） | 少（5 × 4） |
| 需不需要"拒绝"的明确语义 | 弱（查不到 = null，含义靠约定） | 强（集合里少一个元素） |
| 规则会不会经常变 | 会（改表最省事） | 不会（改 5 个类很痛） |

**第三条反过来说也成立**：如果规则**经常变**，状态模式就是灾难——
每加一个动作要改五个类。这种情况表明显更好。

### 4.11 十个决策汇总

把 §4 的全部决策压成一张表，方便回查：

| # | 决策 | 替代方案 | 为什么选它 |
|---|---|---|---|
| 1 | 动作抽成 `enum` | `String` / `int` | 编译期检查 + `switch` 穷尽 |
| 2 | 状态接口三个方法 | 一个 `handle()` | 三类消费者（日志/界面/业务）各取所需 |
| 3 | 不写 `isFinal()` | 加一个 `isFinal()` | 能推导出来的属性不加第二个真相源 |
| 4 | 切换由 `Order` 执行 | 状态自己 `setState` | 唯一写入口，失败绝不转移 |
| 5 | 拒绝用返回值 | 抛异常 | 用户点灰按钮是正常分支，不是故障 |
| 6 | `TransitionResult` 用 `record` | 普通类 | 紧凑构造器钉死不变量 |
| 7 | 状态用 `enum` | 普通类 / `static final` | 无实例数据 → 天然单例 + 序列化安全 |
| 8 | 副作用走包内写方法 | 状态存数据 / 调用方写回 | 数据必须存在 `Order` 上，状态是共享的 |
| 9 | 不一致时抛异常 | `default` 返回假结果 | 让结构性错误大声崩掉 |
| 10 | 轨迹单独一个类 | `List<TransitionResult>` | 步号属于历史位置，不属于结果 |

**这十条里，第 8 条和第 4 条是最容易做错的。**
第 8 条错了会导致数据串台，第 4 条错了会导致约束失效——
两者都不会立刻报错，而是在生产环境里慢慢发酵。

---

## 5. 代码解析：每个文件为什么存在

### 5.1 全景：12 个文件，1951 行，其中只有 624 行是代码

```
src/com/patterns/behavioral/state/
├── OrderAction.java         65 行   动作枚举（4 个常量）
├── OrderState.java         154 行   状态接口（sealed，3 个方法）
├── PendingState.java        99 行   待支付
├── PaidState.java           91 行   已支付
├── ShippedState.java        84 行   已发货
├── CompletedState.java      94 行   已完成（终态）
├── CancelledState.java      91 行   已取消（终态）
├── Order.java              415 行   Context（唯一状态写入口）
├── TransitionResult.java   138 行   一次转移尝试的结果（record）
├── HistoryEntry.java        77 行   轨迹上的一格（record）
├── TransitionTable.java    227 行   表驱动对照实现
└── Client.java             416 行   五段 Demo
```

按职责分成六组，每组回答一个问题：

| 组 | 文件 | 物理行 | 代码行 | 回答什么问题 |
|---|---|---|---|---|
| **基础类型** | `OrderAction` + `TransitionResult` + `HistoryEntry` | 280 | 64 | 动作长什么样、结果长什么样、历史长什么样 |
| **状态骨架** | `OrderState` | 154 | 14 | 一个状态必须回答哪几个问题 |
| **五个状态** | `PendingState` 等五个 | 459 | 127 | 每个状态下的具体规则与副作用 |
| **上下文** | `Order` | 415 | 128 | 谁持有状态、谁执行切换、谁记轨迹 |
| **对照实现** | `TransitionTable` | 227 | 71 | 什么时候**不该**用状态模式 |
| **演示** | `Client` | 416 | 220 | 五段实验，每段证明一件事 |
| **合计** | | **1951** | **624** | |

**第一件要说清楚的事：1951 行里有 1327 行是注释和空行（68%）。**

这个比例不是失手，是刻意的。因为这份代码的主要用途是**教学**，
而不是交付一个业务模块。凡是"看起来可以一眼看懂、但初学者一定会猜错"的地方，
都写了为什么——比如「为什么 `transition` 是 `private`」写了 40 行注释，
而方法体只有 14 行。

但也要诚实地说：**真实业务项目里不该是这个比例。** 业务代码 60%~75%
是正常区间（注释 20%~35%），本项目倒过来了。原因只有一个：
它要兼任教材。

**第二件要说清楚的事：跟原项目比，代码量到底涨了多少？**

| | 原项目 | 本项目 | 倍数 |
|---|---|---|---|
| 文件数 | 7 | 12 | 1.7× |
| 物理行 | 130 | 1951 | 15.0× |
| **代码行** | **58** | **624** | **10.8×** |

行数涨 15 倍、代码涨 10.8 倍，差额就是那 1327 行注释。

那么问题来了：**多出来的 566 行代码，每一行都在解决什么？**

这正是 §5.2 到 §5.9 要逐组回答的。判断标准不是"这行代码有没有用"
（每一行都能找到用处），而是——**删掉它，会发生什么具体的事故？**
下面是八组逐一回答。

### 5.2 `OrderState`：三个方法，每个都在回答一个问题

整个模式骨架只有 14 行代码：

```java
public sealed interface OrderState
        permits PendingState, PaidState, ShippedState, CompletedState, CancelledState {

    String displayName();
    Set<OrderAction> allowedActions();
    TransitionResult accept(OrderAction action, Order order);

    static IllegalStateException unreachable(OrderState state, OrderAction action) { ... }
}
```

三个方法对应三类消费者，**互相不重叠**：

| 方法 | 回答的问题 | 谁在消费 | 如果删掉 |
|---|---|---|---|
| `displayName()` | 我叫什么 | 日志、界面文案 | 到处出现 `PendingState` 这种英文类名 |
| `allowedActions()` | 我这儿能做哪些事 | 界面渲染、前置校验 | 前端只能自己再写一遍 `if (status == 1)` |
| `accept()` | 做这件事会发生什么 | 业务逻辑 | 状态变成纯数据，模式退化成枚举字段 |

**为什么 `displayName()` 不能省？** 因为枚举常量名是给编译器看的。
界面要显示「待支付」而不是 `PendingState`，日志里也要显示中文。
这层翻译必须有地方放，放在状态类里是唯一合理的位置——
放在 `Client` 里会变成一份 `switch`，加一个状态就漏一个分支。

**为什么 `allowedActions()` 是独立方法，而不是让 `accept()` 返回空表示拒绝？**
因为**消费者不同**。界面渲染只需要"能不能做"，不需要执行副作用。
如果只能通过 `accept()` 判断，那渲染一次按钮就要真的支付一次——荒谬。
`allowedActions()` 是**只读的规则查询**，`accept()` 是**有副作用的执行**。
两个职责，两个方法。

**为什么没有 `isFinal()`？** 见 §4.3 的论证。一句话：
`isFinal()` 恒等于 `allowedActions().isEmpty()`，加上它就是给同一件事造两个真相源。
而两个真相源必然会在某次改动后打架。

**为什么是 `sealed`？** 三个具体收益，都是编译期生效的：

| 收益 | 没有 `sealed` 时 | 有 `sealed` 时 |
|---|---|---|
| 状态清单 | 翻目录才知道有几个 | `permits` 一行列全 |
| `switch` 穷尽 | 必须写 `default`，新状态静默走错分支 | 不需要 `default`，漏了编译失败 |
| 新增状态的影响面 | 谁都能加，没人知道要改哪 | 所有未覆盖的 `switch` 编译报错，逐个处理 |

原项目就是开放的 `interface OrderState`。后果很具体：任何人都能写一个
`RefundedState` 并**不改任何现有代码**就让它生效。听起来是扩展性，
实际上是**没人知道一共有几种状态**。

**`unreachable()` 为什么放在接口里，而不是五个状态各写一遍？**
两个理由：

1. **格式统一**——五处报错文案完全一致，排查时不用分辨是哪段代码打的；
2. **表达同一件事**——这五个异常说的是同一句话：
   「`allowedActions()` 说允许，但 `accept()` 里没有对应分支」，
   这是同一个编程错误的五处现场，不该有五份措辞。

**它是 `static` 方法而不是常量**，因为异常对象带栈信息，
每次 `new` 才能拿到准确抛出的位置。复用同一个异常实例会让栈永远指向第一次创建的地方——
这是 Java 里一个很常见的坑。

### 5.3 五个状态：459 行里只有 127 行代码

| 状态 | 物理行 | 代码行 | `ALLOWED` | `accept()` 里成功的分支 |
|---|---|---|---|---|
| `PendingState` | 99 | 32 | `PAY, CANCEL` | 2 个 |
| `PaidState` | 91 | 33 | `SHIP, CANCEL` | 2 个 |
| `ShippedState` | 84 | 26 | `CONFIRM` | 1 个 |
| `CompletedState` | 94 | 18 | `{}` | 0 个 |
| `CancelledState` | 91 | 18 | `{}` | 0 个 |
| **合计** | **459** | **127** | | **5 个** |

五个类加起来 127 行代码，平均每个 25 行。**这就是状态模式的全部业务规则。**

#### 逐个问「删掉它会怎样」

**删掉 `PendingState`：** 新订单没有起点。`Order` 的状态字段
初始化成什么？如果初始化成 `PaidState`，那张单一开始就是已支付——
所有订单白送。

**删掉 `PaidState`：** 「已支付」这个状态消失，那么 `pay()` 成功后去哪？
只能直接跳到 `ShippedState`（没发货却能确认收货），或者留在 `PendingState`
（同一张单能无限次支付）。**两条路都是事故。**

**删掉 `ShippedState`：** 「已发货不能取消」这条规则没有落脚点。
这条规则在真实业务里的价值，`ShippedState` 的类注释写得很直白：
没有状态模式时，它会在 `OrderService`、`RefundService`、`AdminController`
里各写一遍，然后**权限最高的那个入口漏掉了**。

**删掉 `CompletedState`：** 订单走到终点后没有状态可去。
更麻烦的是「已完成订单不能做任何事」这条规则——
如果靠 `if (status != COMPLETED)` 守，那这个 `if` 会被复制到每个入口。

**删掉 `CancelledState`：** 取消之后订单停在哪？回到 `PendingState` 就能被重新支付，
那就等于「取消不掉」。

**五个都删掉，换成一个 `status` 字符串字段：** 这就是 §3.2 的 if-else 版本。
它跑得通，问题在于**拒绝规则会散落到每个调用方**，而且没法保证每一处都写全。

#### 为什么是 `enum` 而不是 `class`

三个前提在本项目里全部成立：

| 前提 | 本项目符不符合 | 不成立时会怎样 |
|---|---|---|
| 状态对象**没有实例数据** | ✅ 五个类都没有字段 | 有数据就必须用类，否则数据会串台 |
| 需要**单例** | ✅ 全局只需要一个「已支付」 | 用类就得自己写单例，还要处理反序列化 |
| 需要 **`switch` 穷尽** | ✅ `accept()` 里就是 `switch` | 用类就没有编译期穷尽检查 |

第一条尤其关键。**状态对象存的是「行为」，不是「数据」。**
想一下：如果 `CancelledState` 里存一个 `refundAmount` 字段，
因为它是全局单例，第二张订单退款时就会覆盖第一张的金额——
`order1.getCancelReason()` 突然返回 `order2` 的原因。
这种 bug 只在多张单同时流转时出现，测试环境很难复现。

> **规则的归属是状态，数据的归属是订单。**
> 这句话搞反了，状态模式就会变成一个共享可变全局变量的灾难。

#### 两个终态为什么不合并

`CompletedState` 和 `CancelledState` 的代码**几乎一样**：
都是 `ALLOWED = Set.of()`，`accept()` 都直接抛 `unreachable`。
18 行代码各一份，看起来就该抽成一个 `TerminalState`。

`CompletedState` 的类注释里给了一个"合并之后会怎样"的推演：

```java
public enum TerminalState implements OrderState {
    COMPLETED("已完成"), CANCELLED("已取消");
}
```

合并当天很爽。然后：

1. 运营说「已完成 7 天内的订单要支持申请售后」→ 出现 `if (this == COMPLETED)`；
2. 客服说「已取消的订单要支持恢复」→ 加了 `else if (this == CANCELLED)`；
3. 财务说「已取消的订单要区分退款中/已退款」→ 再一个分支；
4. 三个月后 `TerminalState` 变成一个用 `if` 分派的伪状态类——
   **正是状态模式要消灭的那种代码。**

**判断标准不是「代码是否相同」，而是「将来是否会同向演化」。**
已完成和已取消是两个完全不同的业务结局，它们分道扬镳是迟早的事。

同一段注释里还拒绝了另一种合并：把两个终态抽成
`abstract class FinalState` 共享 `allowedActions()`。
理由更直接——那点共享只有一行 `return Set.of();`，
而代价是五个状态不能都做成 `enum`（Java 不支持多继承）。
**为一行代码牺牲 enum 的三个好处，是一笔明显的亏本买卖。**

#### 五份 `switch` 是成本，也是收益

`PendingState.accept()` 里四个分支只执行两个，另两个直接抛异常：

```java
return switch (action) {
    case PAY -> { order.markPaid(); yield TransitionResult.ok(...); }
    case CANCEL -> { order.markCancelled("用户主动取消，未付款"); yield TransitionResult.ok(...); }
    case SHIP, CONFIRM -> throw OrderState.unreachable(this, action);
};
```

那两个永远走不到的分支**不是废代码**。它们的作用是：

> 将来有人给 `OrderAction` 加了第五个动作（比如 `UPDATE_ADDRESS`），
> 这个 `switch` 会**编译失败**，逼着人回来想清楚
> 「待支付状态下这个新动作该怎么处理」。

如果当初用 `default ->` 一笔带过，编译不会失败，
运行时新动作会静默走进 `default` 分支——**一个没人想过的分支**。

这就是 §4.10 说的「加动作要改五个类」：痛苦是真的，但痛苦发生在**编译期**，
而不是发生在某张订单被错误处理之后。

### 5.4 `Order`：415 行，唯一的状态写入口

`Order` 是本模式的 Context。它的 415 行里，**public 的部分只有 13 个方法，
而真正让模式成立的只有 3 行**。

#### 结构：四个入口，一条通道

```
  order.pay()      ─┐
  order.ship()     ─┤
  order.confirm()  ─┼──►  transition(action)  ──►  唯一改 state 的地方
  order.cancel()   ─┘           │
                                ├─ 1. 前置校验：allowedActions().contains(action)
                                ├─ 2. 问状态：state.accept(action, this)
                                ├─ 3. 成功才切：state = result.to()
                                └─ 4. 记轨迹：history.add(...)  然后 return result
```

四个 public 动作**全部只有一行**：

```java
public TransitionResult pay()     { return transition(OrderAction.PAY); }
public TransitionResult ship()    { return transition(OrderAction.SHIP); }
public TransitionResult confirm() { return transition(OrderAction.CONFIRM); }
public TransitionResult cancel()  { return transition(OrderAction.CANCEL); }
```

**这四行里没有任何状态判断。** 这不是偷懒，这是模式的全部意义：
判断住在状态对象里，所以调用方不需要知道当前是什么状态，
也**没有办法直接指定目标状态**——它只能说「我要做支付这件事」。

**如果这四个方法写成 `if` 会怎样？** 那就退回了 §3.2 的 if-else 版本，
而且更糟：四个方法里各有四个 `if`，一共十六个分支，
其中十二个是拒绝分支。加一个状态，十六个分支全要重看。

#### `transition()` 的四步，一步都不能换

```java
private TransitionResult transition(OrderAction action) {
    Objects.requireNonNull(action, "动作不能为 null");

    TransitionResult result;
    if (state.allowedActions().contains(action)) {
        result = state.accept(action, this);              // 第 2 步
    } else {
        result = TransitionResult.rejected(action, state, ...);   // 第 1 步：前置校验
    }

    if (result.success()) {
        state = result.to();                              // 第 3 步：成功才切
    }
    history.add(HistoryEntry.next(history.size(), result));   // 第 4 步：记轨迹
    return result;
}
```

**第 1 步为什么必须在 `accept()` 之前？**
因为终态的 `ALLOWED` 是空的，而 `accept()` 里所有分支都抛 `unreachable`。
如果没有这道前置校验，**用户在已完成订单上再点一次确认收货，
会得到一个 `IllegalStateException`，而不是一句「不能再确认收货」**。

在 Web 场景下，这个差别是「接口返回 500 并告警」 vs 「接口返回 400 提示文案」。
**一次正常的用户误操作不该惊动值班。** 这个 bug 在本项目开发过程中真实出现过，
是 §8.3 记录的头号误区。

**第 3 步为什么是 `if (result.success())` 而不是直接赋值？**
因为「失败绝不转移」是状态机最重要的不变量。
如果写成 `state = result.to()`，失败时 `to` 是 `null`，状态被清空——
下次调用 `state.accept()` 直接 NPE。

**第 4 步为什么在切换之后？**
因为要记录的是「那一步的完整结果」，包括转移前后的状态。
`result` 里已经同时有 `from` 和 `to`，而 `to` 在成功后正好等于新的 `state`——
顺序反了也不影响正确性，但按「发生 → 记录」的自然顺序写更好读。

**第 4 步为什么把同一个 `result` 既存进轨迹、又返回给调用方？**
因为这样**调用方看到的和轨迹里存的必然是同一个对象**。
如果分两次构造，就可能出现「返回给用户的是成功，轨迹里记的是失败」——
审计系统的意义就此归零。

`TransitionResult` 是不可变 `record`，所以共享同一个实例没有任何风险。

#### 为什么 `transition()` 是 `private`，而状态写方法不带 `public`

`transition()` 是 `private`，所以 **`state` 字段在整个程序里只有一个写入点**。
这一个决定带来三个能力：

| 想做的事 | 没这个约束时 | 有这个约束时 |
|---|---|---|
| 给所有转移加日志 | 找到所有改状态的地方 | 只改 `transition()` 一处 |
| 加锁保证并发安全 | 每个改状态的地方都要加 | 只改 `transition()` 一处 |
| 排查「谁把我的状态改了」 | 全局搜 `setState` | 打断点在一个方法上 |

四个写业务数据的方法则是**包内可见（无修饰符）**：

```java
void markPaid()                    { this.paid = true; }
String issueTrackingNo()           { ... }
void markDelivered()               { this.delivered = true; }
void markCancelled(String reason)  { ... }
```

**为什么不加 `public`？** 因为外部代码不该说「把这张单标记成已付款」，
它应该说「支付」（`order.pay()`）。前者跳过一切校验，后者完整走一遍状态机。

**那为什么不加 `private`？** 因为状态类要调它们，而状态类和 `Order` 是平级的独立文件。
要让 `private` 可行，只能把五个状态做成 `Order` 的嵌套类——
但那样 1951 行会挤进一个文件，包结构完全失去可读性。

> 这是 Java 包级封装的已知限制：**包内可见 = 包内所有人都能调。**
> 本项目的处理方式是用命名和文档把「内部协议」标出来，
> 而不是假装这个漏洞不存在。

`issueTrackingNo()` 里有一道额外的保险：

```java
if (trackingNo != null) {
    throw new IllegalStateException("订单 " + orderNo + " 的物流单号已经生成过：" + trackingNo
            + "；重复发货说明有代码绕过了状态机");
}
```

**为什么这里抛异常而不是静默返回旧单号？**
因为重复发货**不可能是正常业务**——状态机保证 `SHIP` 只能从 `PaidState` 触发一次。
真的发生了，说明有代码绕过了 `Order.transition()`。
这是结构性错误，必须立刻暴露，而不是让两个包裹共用一个单号。

#### 查询方法：三个 `public`，各有各的消费者

| 方法 | 消费者 | 设计要点 |
|---|---|---|
| `describeAllowed()` | 界面、拒绝文案 | 按枚举声明顺序拼接，**不用 `Set.toString()`** |
| `history()` | 审计、演示 | `List.copyOf` 真拷贝，不是不可变视图 |
| `describeData()` | 演示输出 | 数据不存在就不出现在摘要里 |

`describeAllowed()` 的注释里写清了为什么不能直接 `allowedActions().toString()`：
`Set.of(...)` 的迭代顺序**不保证**。同一台机器可能碰巧稳定，
换 JDK 版本就可能变——那种「昨天跑出来是‘支付、取消’，今天变成‘取消、支付’」的变化
会让文档里的真实输出对不上。

按 `OrderAction.values()` 遍历过滤，顺序就**由语言规范保证**。

`history()` 用 `List.copyOf` 而不是 `Collections.unmodifiableList`，
两者的区别见 §4.9：前者是真拷贝快照，后者是会被内部改动带着一起变的视图。

### 5.5 `TransitionResult` + `HistoryEntry`：两个 record，四条不变量

这两个类加起来 215 行，但只有 50 行代码。它们存在的原因只有一个：
**把「一个转移结果长什么样」和「历史里的一格长什么样」变成两个不可能出错的对象。**

#### `TransitionResult`：五个字段，两个工厂，一处校验

```java
public record TransitionResult(
        boolean success,
        OrderAction action,
        OrderState from,
        OrderState to,          // 成功时非空，失败时必须为 null
        String message
) {
    public TransitionResult {   // 紧凑构造器
        Objects.requireNonNull(action,  "动作不能为 null：失败的结果也要能说清是哪个动作失败了");
        Objects.requireNonNull(from,    "起始状态不能为 null");
        Objects.requireNonNull(message, "结果说明不能为 null：没有说明的失败等于没说");
        if (success && to == null)  throw new IllegalArgumentException("转移成功必须给出目标状态");
        if (!success && to != null) throw new IllegalArgumentException("转移失败不应该有目标状态...");
    }
}
```

**五条检查钉死了四条不变量：**

| # | 不变量 | 如果不存在 |
|---|---|---|
| 1 | `action` 永远非空 | 失败结果显示「?? 支付」，审计时无从追查 |
| 2 | `from` 永远非空 | 日志里「null → 已支付」 |
| 3 | `message` 永远非空 | 被拒绝时只看到一句空字符串，不知道为啥 |
| 4 | `success == true` **当且仅当** `to != null` | 调用方需要到处判空 |

第 4 条是核心。它让下游所有代码都**不需要再考虑「万一 success 是 true 但 to 是 null 怎么办」**——
因为这种对象根本造不出来。这就是 §4.5 说的「把不变量钉死在创建处」。

#### 两个工厂方法：签名本身就是约束

```java
public static TransitionResult ok(OrderAction action, OrderState from,
                                  OrderState to, String message) { ... }

public static TransitionResult rejected(OrderAction action, OrderState from, String reason) { ... }
```

看清楚这两个签名的差别：**`rejected()` 根本没有 `to` 参数。**

这不是省事，是**从签名上不给你传错的机会**。如果两个工厂都接受 `to`，
调用方就可能写出 `rejected(action, state, SomeState.INSTANCE, "不行")`——
一个语义上自相矛盾的拒绝。现在这种代码**编译不过**。

> **能用方法签名拦住的错误，就不要留给构造器里的 `if` 去拦。**
> 前者在 IDE 里就标红，后者要等跑到那一行。

#### `describe()`：两种箭头，一种排版哲学

```java
public String describe() {
    if (success) {
        return action.label() + "：" + from.displayName() + " → " + to.displayName();
    }
    return action.label() + " ⊣ " + from.displayName();
}
```

真实的两种输出：

```
支付：待支付 → 已支付        ← 成功了，状态真的动了
发货 ⊣ 待支付                 ← 被挡住了，状态没动
```

**失败时为什么不把 `message` 拼进来？** 因为拒绝原因通常很长
（`「已支付」下不能「支付」；当前允许：发货、取消`），拼进来会让一行变得非常长，
打出来的轨迹一条比三条还宽，根本没法扫。

> **一行只放得下一件事。** 轨迹要的是「扫一眼看完全部步骤」，
> 不是「在同一行里说清一切」。原因交给下一行的 `message` 单独展示。

这个决定在开发过程中是被实测推翻后修正的：最初版本把 `message` 拼进了 `describe()`，
打出来的轨迹行宽参差不齐。改成现在的形式后，七步轨迹才变得能纵向扫读。

#### `HistoryEntry`：为什么步号必须单独一个类型

```java
public record HistoryEntry(int step, TransitionResult result) {
    public HistoryEntry {
        if (step <= 0) throw new IllegalArgumentException("步号从 1 开始，实际传入：" + step);
        Objects.requireNonNull(result, "轨迹条目必须带结果");
    }
    public static HistoryEntry next(int existingSize, TransitionResult result) {
        return new HistoryEntry(existingSize + 1, result);
    }
}
```

**为什么不直接 `List<TransitionResult>`？** 因为「第几步」这件信息
**不属于转移结果本身**，它属于「历史里的位置」。

如果硬塞进 `TransitionResult`，同一个转移结果在不同位置就变成不同的值，
`equals` 也就失去意义了——你在 `PaidState` 下支付成功这件事，
放在第 2 步和第 5 步是**同一件事**，不该因为位置不同而不相等。

> **结果描述「发生了什么」，轨迹描述「它排在第几位」。**
> 两个职责，两个类型。这与责任链里 `ApprovalContext.Step` 的思路一致。

**紧凑构造器里的 `step <= 0` 检查值多少？**
它防止的是一个非常具体的错误：有人用 `history.size()` 当步号（从 0 开始）。
有了这道检查，第一笔记录会在**构造时**炸掉，而不是等到日志里出现「第 0 步」
才被人发现。

`next()` 这个静态工厂的作用类似：把「步号怎么算」收在一处。
调用方永远只写 `HistoryEntry.next(history.size(), result)`，
不会出现一个地方从 0 数、另一个地方从 1 数。

#### 为什么失败也要进轨迹

`Order.transition()` 的第 4 步**无条件** `history.add(...)`，成功失败都加。

只记成功的轨迹看起来干净，但现实中最需要排查的恰恰是被拒绝的那几次：

| 现象 | 轨迹里能查到什么 |
|---|---|
| 用户投诉「我点不了取消」 | 是「已发货」状态拒绝了 `CANCEL` |
| 接口突然大量报错 | 上游把 `CANCEL` 发给了已完成订单 |
| 运营问「这张单到底经历了什么」 | 每一步都在，包括没走通的那步 |

> **只记成功 = 只留下结果，丢掉了过程。**
> 而排查问题时，过程永远比结果值钱。

Demo 2 的七步轨迹就是这个设计的直接体现：3 步成功 + 4 步被拒，
四种不同的拒绝理由全部可读。

### 5.6 `OrderAction`：65 行换来三个编译期能力

这个枚举只有 4 个常量和一个 `label` 字段：

```java
public enum OrderAction {
    PAY("支付"), SHIP("发货"), CONFIRM("确认收货"), CANCEL("取消");
    private final String label;
    public String label() { return label; }
}
```

**它解决了原项目最大的一个结构问题：`request()` 没有参数。**

```java
// 原项目
order.request();   // 是支付？是发货？还是查询？
```

没有参数的入口只能表达「把订单往前推一格」。于是：
用户只想看一眼订单状态，调了 `request()`，订单被支付了；
后台批处理跑了一次对账，一批订单全部自动往前跳了一步。

**用 `enum` 而不是 `String` 或 `int`，换来三个编译期能力：**

| 能力 | `String` / `int` | `enum` |
|---|---|---|
| 写错名字 | 编译通过，运行时才发现 | **编译失败** |
| `switch` 穷尽 | 做不到，必须 `default` | **可以，漏了编译失败** |
| 集合类型 | `HashSet<String>`，靠约定去重 | `EnumSet`，位图实现，又小又快 |

第二条在本项目里被用了五次（每个状态的 `accept()`）。这也是为什么
`PendingState` 里那两个永远不会执行的分支必须写出来——它们是穷尽性的代价，也是它的收益。

**`label()` 为什么不直接用 `name()`？** 因为两者服务不同的人：

- `PAY` 是给编译器看的标识符，改它要改代码；
- `支付` 是给用户看的文案，运营可能要求改成「立即付款」。

分开之后，改文案不用改常量名，改常量名也不影响已经发给用户的文案。

### 5.7 `TransitionTable`：227 行里 156 行是注释，为什么还留着

这个类**不是状态模式的一部分**，它是对照组。它存在的唯一目的是回答：
**什么时候不该用状态模式。**

它做三件事：

| 职责 | 方法 | 对应状态模式里的什么 |
|---|---|---|
| 存一份规则副本 | `TABLE` + `build()` | 五个状态类的 `ALLOWED` + `accept()` 分支 |
| 提供固定顺序的状态清单 | `states()` | `permits` 列表 |
| 把表渲染成文本 | `describeAsText()` | （无对应物） |

#### `LinkedHashMap` 而不是 `Map.of`：一个会灭掉可复现输出的陷阱

```java
private static final Map<OrderState, Map<OrderAction, OrderState>> TABLE = build();
private static Map<...> build() {
    Map<...> table = new LinkedHashMap<>();
    ...
    return Collections.unmodifiableMap(table);
}
```

`build()` 里的注释把两个原因写得很清楚，第二个尤其容易踩：

> 表的键是 `enum` 实例，`Map.of` 内部按 `hashCode` 散列，
> 而 `enum` 的默认 `hashCode` 是 **identity hash**——每次 JVM 启动都可能不同。

这意味着：同一份代码，今天跑出来「待支付、已支付、已发货」，
明天跑出来「已发货、待支付、已支付」。文档里写的「真实输出」立刻变成假的。

**这个问题不是理论上的。** 本项目在写这段时就遇到过：最初用 `Map.of` 存外层表，
自检那段的状态遍历顺序每天都不一样，直到换成 `LinkedHashMap` 才稳定。

`states()` 里还有一层保险：**不用 `TABLE.keySet()`，显式列出五个状态。**

```java
public static List<OrderState> states() {
    return List.of(PendingState.INSTANCE, PaidState.INSTANCE, ShippedState.INSTANCE,
            CompletedState.INSTANCE, CancelledState.INSTANCE);
}
```

即使本类用了 `LinkedHashMap`、顺序恰好稳定，把「顺序」托付给一个 Map 的实现细节
也是很脆弱的依赖——某天有人把它换成 `HashMap`，输出就会悄悄变。

> **顺序应该是被声明的，而不是被推断的。**

#### `describeAsText()`：两个排版坑，都是实测踩出来的

**坑一：中文按字符数补空格会歪。**

```java
// 错误做法
String.format("%-12s", "待支付")   // 补 9 个空格，但「待支付」实际占 6 列
String.format("%-12s", "已支付")   // 补 9 个空格，「已支付」也占 6 列
```

前两行碰巧都对齐（都是 3 个汉字），但如果某列是「确认收货」（4 字）和「取消」（2 字），
就会差两列。本类统一用 `DisplayWidth.padRight` 按**显示宽度**补齐。

**坑二：每个单元格都补空格 → 行尾空白。**

```java
row.append(i == actions.length - 1
        ? cell                                  // 最后一个单元格：不补
        : DisplayWidth.padRight(cell, cellWidth));
```

最初版本对每个单元格无条件 `padRight`，结果输出里每一行末尾都有 4~8 个无意义空格。
后果不是「不好看」，而是：

- 放进 Git，形成一堆看不见的 diff；
- 复制到 Markdown，被 linter 报「行尾空白」；
- 用 `Compare-Object` 比对输出时，肉眼完全看不出差别在哪里。

本项目最终把「行尾空白 0 行」当作交付标准之一，就是被这个坑教育的。

#### 那为什么不干脆删掉它

因为它是**可执行的论证**。

如果只在文字里写「表表达不了副作用」，读者可以不信。
但 `Client` 的 Demo 4 用同一个订单号跑了表版本和状态版本，输出里明晃晃地写着：

```
用表跑：  最终状态：已完成 / 物流单号：（空） / 订单对象自己的状态：待支付
用状态：  最终状态：已完成 / 物流单号：SF2005
```

**同一个状态序列，两种不同的结局。** 这个对比不需要任何解释就能看懂，
比一段三百字的论断有力得多。

> 教学代码里，一个能跑的反例比十段道理值钱。

### 5.8 两个 util：`Money` 和 `DisplayWidth`

这两个类都**不是设计模式的一部分**，它们是多个 Demo 共用的工具。
放在 `com.patterns.util` 而不是任何模式包里，有一条明确的纪律。

#### `Money`：一次真实的搬家

这个文件最初放在 `com.patterns.behavioral.chainofresponsibility` 包下，
因为责任链是第一个需要金额的模式。等状态模式也要用的时候，问题出现了：

```java
// 如果不搬家，状态模式就得这么写
import com.patterns.behavioral.chainofresponsibility.Money;
```

**让「状态」去依赖「责任链」，这是纯粹由文件位置造成的假耦合。**
两个模式毫无关系，却因为一个金额工具类被绑在一起。

所以它被上移到 `com.patterns.util`。搬完之后，状态模式和责任链都只依赖 `util`，
两者之间没有任何依赖关系。

> **包结构应该按「被谁使用」划分，而不是按「第一次被谁写下」划分。**
> 这条经验在任何一个多模块项目里都适用。

搬家的验证方式是哈希对比：搬家前后责任链 Demo 的输出 SHA256 完全一致
（`403461BA...DAFE62`，132 行），证明只是位置变了，行为一点没变。

`Money` 里三个方法各自的要点：

| 方法 | 要点 | 坑 |
|---|---|---|
| `of(String)` | 从字符串构造，统一 `setScale(2, HALF_UP)` | 用 `new BigDecimal(0.1)` 会把 double 误差带进来 |
| `yuan(BigDecimal)` | `String.format(Locale.ROOT, "%,.2f")` | 不指定 `Locale.ROOT`，不同系统打出不同分隔符 |
| `plain(BigDecimal)` | `stripTrailingZeros().toPlainString()` | 只 `stripTrailingZeros()` 会打出 `2E+2` |

**为什么不用 `DecimalFormat`？** 因为它**不是线程安全的**。
内部有可变的 `DigitList` 缓冲，多线程共用一个实例会算出乱七八糟的格式，
而且这种 bug 只在并发下偶发，极难复现。`String.format` 每次调用内部新建 `Formatter`，
天然线程安全，代价只是稍微慢一点——在「打印日志」这个量级上完全不值一提。

#### `DisplayWidth`：139 行解决一个中文终端的顽固问题

核心只有三个方法：

```java
int     width(String)                    // 「待支付」→ 6，不是 3
String  padRight(String, int targetWidth) // 按显示宽度右补空格
String  line(char, int length)            // 生成分隔线
```

`width()` 用 `codePointAt` + `Character.charCount` 遍历，而不是 `for (char c : ...)`：
后者会把 Emoji 和生僻字拆成两个 `char`，宽度算错。

类注释里诚实列了四条局限：制表符/ANSI 转义不处理、Emoji 未覆盖、
「歧义宽度」字符按 1 列算、最终效果还取决于字体是否真等宽。

**为什么把局限写进注释而不是想办法全解决？** 因为它是工具类，不是产品。
覆盖四条局限的代价可能是 500 行码表，而收益只是「输出更完美一点」。
把边界说清楚，比假装没边界更专业。

`padRight` 里也有一个决定：**文本超出目标宽度时原样返回，不截断。**
理由很直接——截断中文会切出半个字符或丢掉关键信息，**宁可错位也不要丢数据**。

### 5.9 `Client`：416 行，220 行代码，五段 Demo

它是本项目里代码最密的一个文件（53% 是代码），因为它几乎全是打印语句。

#### 五段 Demo 的分工

| # | 演示 | 订单 | 要证明的事 |
|---|---|---|---|
| 1 | 走完一生 | `O-2001` / ￥299.00 | 正常路径长什么样（基准） |
| 2 | 合法与非法交错 | `O-2002` / ￥88.00 | 状态模式的价值在于**拒绝** |
| 3 | 两种取消 | `O-2003`、`O-2004` | 同一方法、不同状态、不同后果 |
| 4 | 转移表对照 | `O-2005` | 什么时候**不该**用状态模式 |
| 5 | 自检 + 对比 | （20 种组合） | 两份规则互为校验；三模式区别 |

**为什么 Demo 2 是重点而不是 Demo 1？** `Client` 里写了理由：

> 很多状态模式的示例只演示「状态一路往前走」，那看完之后读者会以为
> 状态模式就是「把 if 拆成类」。真正让它有价值的是：**它同样擅长拒绝**。

七次尝试里有四次被挡下来，而且每次挡下来的理由都不一样：
待支付不能发货、已支付不能重复支付、已发货不能取消、已完成不能再确认。
这四条规则分别住在四个状态类里，**互相不知道对方的存在**。

#### 八个打印辅助，只排版不判断

| 方法 | 作用 | 为什么单独抽出来 |
|---|---|---|
| `section(title)` | 打印 `=== 标题 ===` | 五段统一格式 |
| `printStep(seq, order, result)` | 一次尝试的**四行快照** | 保证五段打出来的信息量一致 |
| `printHistory(order)` | 完整轨迹 + 成功/拒绝计数 | 轨迹格式统一 |
| `printIndented(text, n)` | 多行文本整体缩进 | 打印转移表用 |
| `printCompareHeader()` | 对比表表头 + 分隔线 | 分隔线长度要与表头实际宽度一致 |
| `printCompareRow(...)` | 对比表一行 | 同上 |
| `padSeq(seq)` | 序号补两位数 | 让 `[ 1]` 和 `[10]` 对齐 |
| `orEmpty(value)` | 空值显示成 `（空）` | 区分「没单号」和「单号是空串」 |

`printStep()` 的四行设计很值得看：

```
  [ 2] [OK] 支付：待支付 → 已支付
       说明：收到 ￥88.00，等待卖家发货
       数据：已付款 ￥88.00
       当前：已支付   可做：发货、取消
```

第 3、4 行读的是**执行之后**的订单，所以能看到状态转移到底改动了什么——
这是验证「副作用没有被丢掉」的直接证据。

`printCompareHeader()` 里有一行值得单拎出来：

```java
System.out.println("  " + DisplayWidth.line('-', 14 + 22 + 22 + DisplayWidth.width("责任链")));
```

最后一列用 `DisplayWidth.width("责任链")` 而不是写死 `14`。
最初写死成 `14`，分隔线比表头长了 8 列。这类小错在纯文本里一眼就能看出来，
但在 IDE 的等宽字体预览里经常被忽略。

#### 输出可复现的五个技巧

这是本项目所有 Demo 的共同标准，`Client` 的类注释里写明了：

| # | 技巧 | 如果不做 |
|---|---|---|
| 1 | 不用 `System.currentTimeMillis()` | 每次输出的时间戳不同，哈希对不上 |
| 2 | 不用 `Random` / `UUID` | 同上 |
| 3 | 物流单号由订单号派生（`"SF" + digits`） | 单号随机，无法比对 |
| 4 | 格式串一律带 `Locale.ROOT` | 不同系统打出不同千分位符号 |
| 5 | 展示顺序按枚举声明顺序，不用 `Set` 迭代 | 顺序不稳定，输出不可复现 |

代价是 Demo 看起来「假」了一点——真实系统的单号当然不会从订单号派生。
但收益是：**文档里贴的每一段输出都是可以验证的**，读者在自己机器上跑，
能得到字节完全一致的结果。这个收益在教学项目里远大于「真实感」。

---

## 6. 执行流程：五段 Demo 的真实运行结果

### 6.1 怎么跑，跑出来是什么

在 `design_patterns_pro` 目录下：

```powershell
$jdk = "F:\enCodingApps\JDK\jdk-25"

& "$jdk\bin\javac" --release 25 -encoding UTF-8 -g -Xlint:all `
    -d out -sourcepath src src/com/patterns/behavioral/state/Client.java

& "$jdk\bin\java" "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" `
    -cp out com.patterns.behavioral.state.Client
```

实测结果：

| 项 | 值 |
|---|---|
| 编译告警 | **0 条**（`-Xlint:all`） |
| 输出行数 | **155 行** |
| SHA256 | `AD11B4469BA736A20E170E7998DD40554FFCB6573EE180B0C38CA77BE8A0F1D1` |
| 连续三次运行的哈希 | **完全一致** |
| 行尾空白 | 0 行 |

下面五段全部是 `final.txt` 的原文，一字未改。

### 6.2 Demo 1：一张订单走完它的一生

```
=== 1. 一张订单走完它的一生 ===

  订单 O-2001，金额 ￥299.00，初始状态：待支付
  开局可做：支付、取消

  [ 1] [OK] 支付：待支付 → 已支付
       说明：收到 ￥299.00，等待卖家发货
       数据：已付款 ￥299.00
       当前：已支付   可做：发货、取消

  [ 2] [OK] 发货：已支付 → 已发货
       说明：已出库，物流单号 SF2001
       数据：已付款 ￥299.00 | 单号 SF2001
       当前：已发货   可做：确认收货

  [ 3] [OK] 确认收货：已发货 → 已完成
       说明：买家已签收，交易完成
       数据：已付款 ￥299.00 | 单号 SF2001 | 已签收
       当前：已完成   可做：（无，终态）

  轨迹：共 3 步（3 步成功，0 步被拒绝）
    #1 支付：待支付 → 已支付
    #2 发货：已支付 → 已发货
    #3 确认收货：已发货 → 已完成
```

#### 逐步拆解第 1 步：`order.pay()` 到底经过了多少地方

以 `O-2001` 的 `pay()` 为例，从入口到输出一共 12 个瞬间：

| # | 发生了什么 | 关键变量 / 值 |
|---|---|---|
| 1 | `new Order("O-2001", "299.00")` | `state = PendingState.INSTANCE`，`history = []` |
| 2 | `Main` 调 `order.pay()` | 进入 `pay()`，无任何判断 |
| 3 | `pay()` 转发 `transition(OrderAction.PAY)` | `action = PAY` |
| 4 | `transition` 先查前置校验 | `state.allowedActions()` = `{PAY, CANCEL}`，包含 `PAY` ✓ |
| 5 | 进入 `PendingState.accept(PAY, order)` | `this = PendingState.INSTANCE` |
| 6 | `switch (action)` 命中 `case PAY` | 不是 `unreachable` 分支 |
| 7 | `order.markPaid()` | `order.paid = true`（**副作用发生在这里**） |
| 8 | `TransitionResult.ok(PAY, 待支付, 已支付, "收到 ￥299.00，等待卖家发货")` | 返回一个不可变 record |
| 9 | 回到 `transition`：`if (result.success())` | `true` |
| 10 | `state = result.to()` | `state` 从 `PendingState` 变为 `PaidState` |
| 11 | `history.add(HistoryEntry.next(0, result))` | 新增 `#1`，`step = 1` |
| 12 | `return result` 回到 `main` | `printStep` 打印四行 |

**注意第 7 步和第 10 步的顺序。** 副作用（`markPaid`）发生在状态切换**之前**，
而且是状态对象自己做的。如果反过来，让 `Order` 在切完状态后再去 `if (action == PAY) markPaid()`，
那就等于把状态类的逻辑又抖回了调用方——`Order` 会出现一个 `switch`，
而每加一个动作就要改它。

**第 4 步的前置校验不能省。** 它在 `accept()` 之前就把非法动作挡掉了。
`PendingState` 的 `ALLOWED` 里有 `PAY`，所以这一步直接通过；
如果是 `ship()`，第 4 步就会把它挡下，**连 `PaidState.accept()` 都不会进**。

#### 输出的四行分别从哪来

| 行 | 数据来源 | 说明 |
|---|---|---|
| `[ 1] [OK] 支付：待支付 → 已支付` | `result.describe()` | 成功用 `→` |
| `说明：收到 ￥299.00，等待卖家发货` | `result.message()` | 由**状态对象**生成 |
| `数据：已付款 ￥299.00` | `order.describeData()` | 读的是**执行之后**的订单 |
| `当前：已支付   可做：发货、取消` | `order.state()` + `describeAllowed()` | 同样是执行之后 |

后两行是关键：它们读的都是**转移完成后**的对象，
所以能直接看到「状态变了」和「数据变了」两件事同时发生。
如果某个 demo 里状态变了但数据没变，这两行会立刻显出来。

#### 轨迹的计数

```
  轨迹：共 3 步（3 步成功，0 步被拒绝）
```

`printHistory` 里用 `stream().filter(entry -> entry.result().success()).count()` 算成功数。
这里有意识地没用「最后一个 `to` 就是当前状态」这种推演——直接数据说话。

### 6.3 Demo 2：同一张单上，合法与非法交错出现

**这是五段里最重要的一段。** 完整输出：

```
=== 2. 同一张单上，合法与非法交错出现 ===

  订单 O-2002，金额 ￥88.00，初始状态：待支付

  [ 1] [--] 发货 ⊣ 待支付
       说明：「待支付」下不能「发货」；当前允许：支付、取消
       数据：未付款
       当前：待支付   可做：支付、取消

  [ 2] [OK] 支付：待支付 → 已支付
       说明：收到 ￥88.00，等待卖家发货
       数据：已付款 ￥88.00
       当前：已支付   可做：发货、取消

  [ 3] [--] 支付 ⊣ 已支付
       说明：「已支付」下不能「支付」；当前允许：发货、取消
       数据：已付款 ￥88.00
       当前：已支付   可做：发货、取消

  [ 4] [OK] 发货：已支付 → 已发货
       说明：已出库，物流单号 SF2002
       数据：已付款 ￥88.00 | 单号 SF2002
       当前：已发货   可做：确认收货

  [ 5] [--] 取消 ⊣ 已发货
       说明：「已发货」下不能「取消」；当前允许：确认收货
       数据：已付款 ￥88.00 | 单号 SF2002
       当前：已发货   可做：确认收货

  [ 6] [OK] 确认收货：已发货 → 已完成
       说明：买家已签收，交易完成
       数据：已付款 ￥88.00 | 单号 SF2002 | 已签收
       当前：已完成   可做：（无，终态）

  [ 7] [--] 确认收货 ⊣ 已完成
       说明：「已完成」下不能「确认收货」；当前允许：（无，终态）
       数据：已付款 ￥88.00 | 单号 SF2002 | 已签收
       当前：已完成   可做：（无，终态）

  轨迹：共 7 步（3 步成功，4 步被拒绝）
    #1 发货 ⊣ 待支付
    #2 支付：待支付 → 已支付
    #3 支付 ⊣ 已支付
    #4 发货：已支付 → 已发货
    #5 取消 ⊣ 已发货
    #6 确认收货：已发货 → 已完成
    #7 确认收货 ⊣ 已完成
```

#### 四次拒绝，四个不同的理由，住在四个不同的类里

| 步 | 动作 | 当前状态 | 拒绝理由（真实输出） | 规则住在哪里 |
|---|---|---|---|---|
| 1 | 发货 | 待支付 | 当前允许：支付、取消 | `PendingState.ALLOWED` 里没有 `SHIP` |
| 3 | 支付 | 已支付 | 当前允许：发货、取消 | `PaidState.ALLOWED` 里没有 `PAY` |
| 5 | 取消 | 已发货 | 当前允许：确认收货 | `ShippedState.ALLOWED` 里没有 `CANCEL` |
| 7 | 确认收货 | 已完成 | 当前允许：（无，终态） | `CompletedState.ALLOWED` 是空集 |

**四行理由，分别由四个类产生，而四个类互相不知道对方的存在。**
这就是 §3.3 说的「把复制变成编译期不可能」的具体表现：
没有一份集中的 `if-else` 需要维护。

#### 拒绝之后，订单真的没变

对比第 1 步前后的「数据」和「当前」两行：

```
步 1（发货被拒）：
       数据：未付款
       当前：待支付   可做：支付、取消

步 2（支付成功）：
       数据：已付款 ￥88.00
       当前：已支付   可做：发货、取消
```

被拒绝的第 1 步后面，订单还是「未付款 + 待支付」。
这不是靠「相信它不会变」——是 `transition()` 里 `if (result.success())` 那一行
**物理上不执行** `state = result.to()`。

再对比第 5 步（取消被拒）与第 4 步（发货成功）的数据行：
未付款没变、单号没变、状态没变。**一次被拒绝的取消，不留下任何痕迹。**
这比「取消了但退款没发」要安全一万倍。

#### 七步轨迹：只记成功会有多危险

轨迹里同时有 `→` 和 `⊣` 两种符号：

```
    #1 发货 ⊣ 待支付
    #2 支付：待支付 → 已支付
    #3 支付 ⊣ 已支付
    #4 发货：已支付 → 已发货
    #5 取消 ⊣ 已发货
    #6 确认收货：已发货 → 已完成
    #7 确认收货 ⊣ 已完成
```

如果只记成功的三步（`#2`、`#4`、`#6`），那么这张单的故事就是
「正常完成了」。但实际上用户在前前后后点了四次不该点的按钮。

这两个故事的差别在哪？在于**排查时能不能找到答案**：

- 用户投诉「我要取消但取消不了」→ 看 `#5`，马上知道是已发货；
- 接口监控看到大量 400 → 看 `#1 #3 #7`，马上知道上游顺序错了。

**只记成功的轨迹，等于把事故现场抹干净了再报案。**

#### 这段输出证明的三个不变量

| 不变量 | 证据 |
|---|---|
| 失败绝不转移 | 步 1/3/5/7 的「当前」行与上一步完全一致 |
| 失败绝不留下副作用 | 步 1/3/5/7 的「数据」行与上一步完全一致 |
| 每次尝试都入轨迹 | 7 次尝试 = 7 条轨迹，一条不多一条不少 |

这三个不变量不是靠注释声明的，是上面这 29 行输出一行一行比对出来的。

### 6.4 Demo 3：同一个 `cancel()`，两种完全不同的后果

```
=== 3. 同一个 cancel()，两种完全不同的后果 ===

  --- 没付钱就取消 ---
  订单 O-2003，金额 ￥50.00

  [ 1] [OK] 取消：待支付 → 已取消
       说明：订单已取消，占用库存已释放
       数据：未付款 | 取消原因：用户主动取消，未付款
       当前：已取消   可做：（无，终态）

  --- 付了钱之后取消 ---
  订单 O-2004，金额 ￥50.00

  [ 1] [OK] 支付：待支付 → 已支付
       说明：收到 ￥50.00，等待卖家发货
       数据：已付款 ￥50.00
       当前：已支付   可做：发货、取消

  [ 2] [OK] 取消：已支付 → 已取消
       说明：订单已取消，将原路退回 ￥50.00
       数据：已付款 ￥50.00 | 取消原因：已支付后取消，需原路退款
       当前：已取消   可做：（无，终态）

  两次调用的都是 order.cancel()，一行代码没变。
  但一次是「占用库存已释放」，一次是「将原路退回 ￥50.00」。
  调用方没有写任何 if —— 分支全在状态对象里。
```

#### 两张单的差异，全在数据行里

| | 未付款取消（`O-2003`） | 已付款取消（`O-2004`） |
|---|---|---|
| 说明行 | 订单已取消，占用库存**已释放** | 订单已取消，**将原路退回 ￥50.00** |
| 数据行 | 未付款 \| 取消原因：用户主动取消，**未付款** | 已付款 ￥50.00 \| 取消原因：**已支付后取消，需原路退款** |
| 取消原因来自 | `PendingState.accept()` | `PaidState.accept()` |

**同一行 `order.cancel()`，两个不同的状态类各自产生了不同的文案和不同的业务标记。**

#### 如果不用状态模式，这段逻辑长什么样

`Client` 的 javadoc 里直接写了出来：

```java
public void cancel(Order order) {
    if (order.getStatus() == PAID) {
        refund(order);          // 已支付：要退款
    } else if (order.getStatus() == PENDING) {
        releaseStock(order);    // 待支付：只释放库存
    } else {
        throw new BizException("当前状态不能取消");
    }
    order.setStatus(CANCELLED);
}
```

这段代码在一处还好。问题是它会被复制到 Web 接口、定时任务、客服后台、运营工具……
**每一个入口都要抄一遍，而且总有一个抄漏分支。**

更具体地说，上面这段代码有三个隐患：

1. `else` 分支吞掉了「已发货」和「已完成」的区别——它们拒绝的理由不同，但用户看到的都是同一句；
2. 新加一个「已退款」状态时，这个 `else` 会默默把它也归入「不能取消」，
   **而不会编译失败**；
3. `setStatus(CANCELLED)` 写在外面，任何一个人把 `else` 改成 `else if` 时都可能把它也吞进去。

状态模式里这三条都不存在：差异在 `ALLOWED` 里（是元素的有无，不是分支）、
新状态加进去时 `sealed permits` 和 `switch` 会报错、
切状态只有一个写入口。

### 6.5 Demo 4：同一套规则用「表」写，跑得通，但丢了副作用

**这一段是整篇文档最诚实的地方**：它不回避「状态模式不是唯一解」。

```
=== 4. 同一套规则用「表」写：跑得通，但丢了副作用 ===

  转移表（一张表就能表达清楚）：
    状态      支付        发货        确认收货    取消
    ----------------------------------------------------------
    待支付    →已支付     —           —           →已取消
    已支付    —           →已发货     —           →已取消
    已发货    —           —           →已完成     —
    已完成    —           —           —           —
    已取消    —           —           —           —

  用表跑一遍 O-2005（￥99.00），只记录状态序列：
    待支付 --支付--> 已支付
    已支付 --发货--> 已发货
    已发货 --确认收货--> 已完成
    最终状态：已完成
    物流单号：（空）
    订单对象自己的状态：待支付   ← 表从头到尾没有碰过这个对象

  同一个订单号，换成状态模式再跑一遍：
    最终状态：已完成
    物流单号：SF2005

  两次的状态序列完全一致：待支付 → 已支付 → 已发货 → 已完成。
  但表漏掉的东西有两处：
    · 物流单号是空的；
    · 订单对象自己的状态还停在「待支付」—— 表根本没有碰过它。

  为什么？表里每个格子只存了「目标状态」一个信息，
  挂不住「发货时要把单号写进订单」这件事。
  要补上它，只能回到调用方写 if (action == SHIP) { ... }，
  于是表退化成「查表 + 一长串 if」，比状态模式更乱，
  而且还得把 setState() 请回来 —— 那是本模式最该删掉的东西。

  判据：转移只有「转到哪」→ 用表；
        转移还要顺手做事 → 用状态模式。
```

#### 两处「丢失」的具体机制

| 丢失了什么 | 为什么会丢 | 证据（输出原文） |
|---|---|---|
| 物流单号 | 表里每个格子只存了一个 `OrderState`，没有地方挂副作用 | `物流单号：（空）` |
| 订单对象的状态 | 表算完只返回一个 `cursor` 变量，**从头到尾没调用过 `Order` 的任何写方法** | `订单对象自己的状态：待支付` |

看 `Client.demo4TableCompare()` 的代码就很清楚：

```java
OrderState cursor = PendingState.INSTANCE;
for (OrderAction action : List.of(OrderAction.PAY, OrderAction.SHIP, OrderAction.CONFIRM)) {
    Optional<OrderState> next = TransitionTable.next(cursor, action);
    System.out.println(...);
    cursor = next.orElse(cursor);   // ← 只是移动一个局部变量
}
```

`byTable` 这个 `Order` 对象只在最后被**读**了两次（`trackingNo()` 和 `state()`），
一次都没被**写**过。所以它的状态还是初始的 `待支付`，单号是 `null`。

**这正是「表驱动」的本质局限**：它描述的是「状态图」，而不是「状态机」。
图只需要「节点 + 边」，而机器还需要「转的时候顺便干什么」。

#### 补副作用会付出什么代价

假设真的要在表的基础上把单号补上：

```java
OrderState next = TransitionTable.next(from, action).orElse(null);
if (next == null) { /* 拒绝 */ }
if (action == SHIP)        { order.setTrackingNo(generateNo()); }   // ← 第一个 if
if (action == CANCEL)      { order.setCancelReason(...); }        // ← 第二个 if
if (action == PAY)         { order.markPaid(); }                // ← 第三个 if
order.setState(next);                                            // ← setState 回来了
```

三个后果：

1. **表退化成「查表 + 一长串 if」**，比状态模式更乱；
2. **`setState()` 必须请回来**——而它是本模式最该删掉的东西（§2.4 的七个问题之一）；
3. 副作用和规则分居两处，**新增动作时两边都要改**，而且编译器不会提醒。

#### 反过来的场景：什么时候真的该用表

诚实地说，下面这些场景里表是**更好的选择**：

| 场景 | 为什么表更好 |
|---|---|
| 订单状态只有「待审核 → 已审核 → 已归档」这种纯流转 | 没有任何副作用，用五个类纯属浪费 |
| 工作流引擎 / 审批流程图配置 | 规则需要**在运行时动态加载**，写成代码就固化了 |
| 协议状态机（TCP、WebSocket） | 状态和事件都是纯枚举，转移只产生一个信号 |
| 一次性脚本 / 数据迁移 | 不需要长期维护，用类反而阻碍阅读 |

本项目保留 `TransitionTable` 的 227 行，就是为了**让这个边界看得见**。
删掉它，读者只会看到「状态模式很好」；留着它，读者会看到「什么时候它不该出场」。

### 6.6 Demo 5：20 种组合的自检与三个模式的对比

```
=== 5. 一致性自检与三个模式的对比 ===

  --- 自检：5 个状态 × 4 个动作 = 20 种组合 ---
    状态类说允许：5 种；说禁止：15 种
    其中 accept() 实际成功：5 / 5
    两份规则（状态类 vs 转移表）不一致：0 处

  --- 三个「很像」的模式 ---

  维度          状态模式              策略模式              责任链
  ----------------------------------------------------------------
  一句话        状态决定行为          算法可替换            处理者接力
  调用方写什么  order.pay()           setStrategy(s)        chain.submit(req)
  关键问题      我现在是什么状态      用哪种算法            该谁处理
  会不会拒绝    会，非法转移被挡      不会，总要算结果      会，无人处理返回空
  谁在变        Context 内部的状态    注入进来的算法        链上的节点
  典型误用      拿状态做流程编排      用策略消灭所有 if     把管道当责任链
```

#### 自检到底在验什么

`demo5SelfCheckAndCompare()` 里有两层验证，第二层才是真正有价值的：

**第一层：两份规则是否一致。**

```java
boolean stateSays = state.allowedActions().contains(action);   // 规则来源 A：五个状态类
boolean tableSays = TransitionTable.allows(state, action);     // 规则来源 B：TransitionTable
if (stateSays != tableSays) { mismatch++; ... }
```

`allowedActions()` 写在五个状态类里，转移表写在 `TransitionTable` 里——
这是**两个独立的数据源描述同一套规则**。它们本可以各自出错、互相矛盾。
遍历全部 20 种组合逐一比对，任何一处写歪都会在这里暴露。

**第二层：说了允许，是不是真能做。**

```java
if (stateSays) {
    Order probe = new Order("PROBE-1", "1.00");
    TransitionResult result = state.accept(action, probe);
    if (result.success()) { acceptedOk++; } else { mismatch++; ... }
}
```

这一步验的是「`allowedActions()` 和 `accept()` 的实现是否一致」——
也就是 `unreachable()` 保护的那个不变量。如果某个状态在 `ALLOWED` 里写了 `CANCEL`
但 `accept()` 里忘了写分支，这里会直接捕获 `IllegalStateException`。

**注意 `probe` 是一张一次性订单。** 如果多次复用同一张 `probe`，
第二轮的 `PAY` 就会因为状态已经变了而被拒绝，测试自己把自己搞脏了。
每次 `new` 一个，才能保证每种组合都在「干净的起点」上测。

#### 20 = 5 × 4，为什么是 5 和 15

| 状态 | 允许的动作数 |
|---|---|
| 待支付 | 2（支付、取消） |
| 已支付 | 2（发货、取消） |
| 已发货 | 1（确认收货） |
| 已完成 | 0 |
| 已取消 | 0 |
| **合计** | **5** |

20 种组合 = 5 个允许 + 15 个禁止。而 `accept()` 实际成功 5 / 5——
说明「说允许」和「真能做」完全对上。

**这比单元测试强在哪里？**

| | 常见的单元测试 | 这里的自检 |
|---|---|---|
| 覆盖范围 | 只测 4~5 条正常路径 | **全部 20 种组合** |
| 非法组合 | 通常不测 | **15 条全部遍历** |
| 规则冲突 | 需要人工对比两个文件 | 程序逐项比对 |

正常路径通常只有 4~5 条，剩下的 15 条非法组合才是真正容易写错、
也最容易被忽略的部分。

#### 为什么不把这段自检写成 JUnit

因为它要**跑在 Demo 里，和输出一起被看到**。

一个 JUnit 测试跑绿了，读者只能看到「测试通过」；
而这里跑出来的三行数字（5 / 15 / 0）直接告诉了读者规则集的完整形状。
在教学代码里，**可看见的过程**比「通过」两个字值钱。

真实项目里当然应该写成 JUnit——本项目在附录 B 里说明了如何把这段逻辑搬进测试类。

### 6.7 五段串起来看

五段不是并列的五个例子，是一条递进的论证线：

| 段 | 建立的前提 | 下一段用它做什么 |
|---|---|---|
| 1 | 正常路径长什么样 | 作为基准，后面所有「不同」都相对它而言 |
| 2 | 拒绝也是正常业务，且不留痕迹 | 证明状态模式的价值不只是「往前走」 |
| 3 | 同一方法不同状态 → 不同后果 | 证明分支真的被状态吸收了 |
| 4 | 表也能跑，但丢副作用 | **给上面三段设一个边界** |
| 5 | 两份规则互相校验 + 三模式对比 | 把前四段的感性认识变成可验证的数字 |

**如果只能保留一段，应该保留 Demo 2。** 因为它同时展示了状态模式的三个核心能力：
拒绝、隔离（四个规则住在四个类里）、留痕（七步轨迹）。

Demo 4 是第二重要的：它是唯一一段**主动给状态模式泼冷水**的内容。

---

## 7. 扩展对比：新需求来了改哪里

这一章回答一个比「模式是什么」更实际的问题：
**上线三个月后，需求变了，这套代码好不好改？**

### 7.1 需求 A：新增一个状态「已退款」

业务背景：以前「已支付取消」直接进「已取消」，财务说不行，
退款要单独跟踪，所以「已支付取消」应该进「已退款」。

**要改的地方：**

| # | 文件 | 改什么 | 行数 |
|---|---|---|---|
| 1 | `RefundedState.java` | **新建**：仿 `CancelledState`，`ALLOWED = Set.of()` | +91 |
| 2 | `OrderState.java` | `permits` 末尾加 `RefundedState` | +1 |
| 3 | `PaidState.java` | `CANCEL` 分支的目标改成 `RefundedState.INSTANCE`，文案改「退款已受理」 | 改 3 行 |
| 4 | `TransitionTable.java` | `build()` 里 `paid.put(CANCEL, ...)` 改目标 + `states()` 加一项 | 改 2 行 |
| 5 | `TransitionTable.java` | `table.put(RefundedState.INSTANCE, Map.of())` | +1 |
| 6 | `Client.java` | （可选）Demo 3 多一行对比 | +3 |

**总计：新建 1 个文件，改 3 个现有文件，新增/修改约 98 行。**

**编译器会帮你找到所有遗漏点吗？** 分两类：

| 位置 | 编译器会不会报错 |
|---|---|
| `OrderState permits` | 不报（但要是不加，新类编译不过，会反向提醒） |
| 五个状态里的 `switch` | **不报**（`OrderState` 的 `accept` 已经是穷尽的，新状态不在里面） |
| `TransitionTable.states()` | **不报**（只是少了一项） |
| `Order.transition()` | **不报**（它只问 `state.allowedActions()`） |

**所以新状态的发现靠两样东西：**
`sealed permits` 强制你登记，Demo 5 的自检会立刻告诉你
「转移表和状态类不一致」——因为新状态在 `states()` 里没被遍历到，
或者被遍历到但表里没填。

**对比 if-else 版本要改哪里：**

```java
public void cancel(Order order) {
    if (order.getStatus() == PAID) {
        refund(order);
        order.setStatus(REFUNDED);      // ← 改这里
    } else if (...) { ... }
}
```

只看 `cancel()` 好像只改一行。但真正的问题是：
**前端的状态映射表、报表的状态分组、客服后台的筛选条件里，各自还有一份 `REFUNDED` 要加。**
而这三处的遗漏，编译器**一个都不会告诉你**。

> 这就是 §3.3 的「复制变编译期不可能」在扩展场景下的价值：
> 不是「改得少」，而是「漏不掉」。

### 7.2 需求 B：新增一个动作「修改收货地址」

业务背景：买家下单后想改地址。

**这是状态模式最痛的一类改动，必须诚实看。**

| # | 文件 | 改什么 |
|---|---|---|
| 1 | `OrderAction.java` | 加 `UPDATE_ADDRESS("修改地址")` |
| 2 | `PendingState.java` | `switch` **编译失败** → 得决定：允许（未发货，应该允许） |
| 3 | `PaidState.java` | `switch` **编译失败** → 得决定：允许（仍未发货） |
| 4 | `ShippedState.java` | `switch` **编译失败** → 得决定：拒绝（已出库，地址改了也送不到） |
| 5 | `CompletedState.java` | 不用改（它没有 `switch`，直接抛） |
| 6 | `CancelledState.java` | 不用改（同上） |
| 7 | `TransitionTable.java` | `build()` 里给 pending / paid 各加一行 `put` |
| 8 | `Client.java` | （可选）演示改地址 |

**代价：改 5 个文件。收益：编译器把三个 `switch` 全部标红，
逼你逐个回答「这个新动作在每个状态下该怎么处理」。**

对比 if-else 版本：改一个 `switch (status)` 就够了——
但前提是你**记得**改。如果忘了，新动作在「已发货」状态下会落入
`default: throw` 或者更糟——什么也不做、静默返回成功。

**一个真实的判断标准：**

| 动作增加频率 | 推荐 |
|---|---|
| 很少（一年一两次） | 状态模式。编译期提醒的价值远超改 5 个文件的麻烦 |
| 频繁（每季度都在加） | **表驱动**。改表一行就完事 |
| 完全动态（运营可配置） | **表驱动 + 数据库** |

> 这条判据跟 §4.10 一致：**规则经常变 → 用表。**

### 7.3 需求 C：「已发货 7 天自动确认收货」

**这个需求根本不需要改状态类。**

因为自动确认收货只是**另一个调用方**，它跟 `AdminController`、
定时任务、客服后台一样，只是调 `order.confirm()`：

```java
// 定时任务里
for (Order order : orderRepository.findShippedBefore(now.minusDays(7))) {
    TransitionResult result = order.confirm();
    if (!result.success()) {
        log.info("自动确认失败：{}", result.message());   // 不是异常，只是返回
    }
}
```

三件事证明了模式在这里的价值：

1. **不关心当前状态**：`confirm()` 自己会拒绝不合适的情况；
2. **失败不是异常**：`result.success()` 为 `false` 就记日志跳过，**不会中断整批**；
3. **轨迹自动记录**：定时任务触发的确认与用户手动点的完全一样。

**如果当初用异常表达拒绝呢？** 一张已被用户手动确认的订单会让整个批次
抛异常中断，剩下的几千张单都不处理了。这就是 §4.5 选择返回值的实际回报。

### 7.4 需求 D：状态需要持久化到数据库

真实项目里 `Order.state` 不能只活在内存里。

**推荐做法：库里存一个独立的 `status` 列，而不是枚举的 `name()` 或 `ordinal()`。**

```sql
CREATE TABLE orders (
    order_no VARCHAR(32) PRIMARY KEY,
    amount   DECIMAL(12,2),
    status   VARCHAR(16) NOT NULL,   -- PENDING / PAID / SHIPPED / COMPLETED / CANCELLED
    ...
);
```

**为什么不存 `ordinal()`？** 因为它就是常量在枚举里的**下标**。
某天有人把 `PAID` 和 `SHIPPED` 的顺序调换，库里所有历史订单的状态就静默地错了——
而且**不会有任何报错**。

**为什么不存 `name()`？** 比 `ordinal()` 好，但把代码标识符泄漏到了数据里。
重命名常量意味着要写数据库迁移脚本。用一个独立的 `status` 列 + 显式映射方法更稳：

```java
static OrderState fromCode(String code) {
    return switch (code) {
        case "PENDING"   -> PendingState.INSTANCE;
        case "PAID"      -> PaidState.INSTANCE;
        case "SHIPPED"   -> ShippedState.INSTANCE;
        case "COMPLETED" -> CompletedState.INSTANCE;
        case "CANCELLED" -> CancelledState.INSTANCE;
        default -> throw new IllegalArgumentException("未知状态码：" + code);
    };
}
```

这个 `switch` 也是穷尽的——少写一个分支编译不过。

**恢复订单时为什么要重建轨迹？**
如果 `history` 只在内存里，重启后轨迹就空了。真实项目里轨迹应该**入库**，
而且与状态更新在**同一个事务**里——否则会出现「状态变了但轨迹少一条」，
或者反过来。

本项目的 `history` 是内存实现，因为它是教学 Demo。§8.2 会把这条列为已知局限。

### 7.5 需求 E：多线程并发下单同一张单

这是一个很现实的场景：用户手机和网页同时点了「支付」。

**本项目的 `transition()` 不是线程安全的。** 两个线程可能同时通过第 1 步的校验，
然后都执行 `accept()`，都成功——一张单被支付两次。

**最小修法：给 `transition()` 加锁。**

```java
private synchronized TransitionResult transition(OrderAction action) { ... }
```

因为**只有一个写入口**，加锁只需要改这一行。这正是 §5.4 那个决定的回报：
如果状态是在五个地方被改的，你得找全五处并确保锁的范围一致。

**更完整的做法**（真实项目）：

| 层次 | 做法 |
|---|---|
| 单机内存 | `synchronized` 或 `ReentrantLock` |
| 数据库 | `UPDATE orders SET status=? WHERE order_no=? AND status=?` + 检查影响行数 |
| 分布式 | 分布式锁（Redis / ZooKeeper）或乐观锁版本号 |

注意数据库那一行：`AND status=?` 就是**把状态机的校验下沉到 SQL 的 WHERE 里**，
靠数据库的行锁保证只有一个请求能成功。这是生产环境最常用的做法。

### 7.6 需求 F：给所有转移加埋点

监控要求：每一次状态转移都要上报 `{from, to, action, 耗时, 结果}`。

**只需要改一个方法：**

```java
private TransitionResult transition(OrderAction action) {
    long start = System.nanoTime();
    TransitionResult result = doTransition(action);
    metrics.record(result.from(), result.to(), action,
            System.nanoTime() - start, result.success());
    return result;
}
```

如果这段逻辑分散在五个状态类里，你得改五处，还得保证五处的字段名、标签完全一致。

> **单一写入口的价值不在「代码少」，而在「横切关注点只有一个落脚点」。**
> 日志、埋点、加锁、审计、开关、限流——都属于这类需求。

### 7.7 扩展成本总表

| 需求 | 改文件数 | 改行数 | 编译器帮忙吗 | 难度 |
|---|---|---|---|---|
| 加一个状态 | 3~4 | ~98 | 部分（`permits` + 自检） | 中 |
| 加一个动作 | 5~7 | ~40 | **是（`switch` 全部标红）** | 高（但安全） |
| 加一个调用方 | 0 | 0 | — | **零** |
| 加横切逻辑 | 1 | ~5 | — | **极低** |
| 改一条拒绝理由 | 1 | 1 | — | 极低 |
| 整个规则改成动态配置 | 全部重写 | — | — | 高（应改用表） |

**最后一行很重要**：如果规则需要运营在后台配置，状态模式就是错的选择。
那时候应该是一张数据库表 + 一个解释器。

---

## 8. 优缺点与常见误区

### 8.1 四条优点，每一条都带代价

**优点一：拒绝规则只有一个出处。**

前端读 `allowedActions()` 渲染按钮，后端读同一份做前置校验。
不存在「前端说不能点、后端却接受了」。

> **代价**：多了一个接口方法，多了一个调用方需要遵守的约定。
> 如果某个调用方不用它而自己写 `if`，收益立刻归零。

**优点二：新增状态不可能漏改。**

`sealed permits` 强制登记，五个 `switch` 的穷尽性把遗漏变成编译错误。

> **代价**：状态集合必须是**封闭**的。如果业务上状态真的会无限增长（比如运营自定义标签），
> `sealed` 会变成阻碍，那时候该用表。

**优点三：失败不转移、失败不留副作用、失败也入轨迹。**

三条不变量全部由 `Order.transition()` 集中保证，而不是靠五个状态类各自自律。

> **代价**：引入 `TransitionResult` 这个类型，并且所有调用方都要处理
> `success() == false` 的分支。相比「调完就完事」，调用方多了一个 `if`。

**优点四：横切关注点只有一个落脚点。**

日志、埋点、加锁、审计全部加在 `transition()` 一处。

> **代价**：所有状态转移必须经过 `Order`。如果某个状态直接拿到别的 `Order` 引用
> 自己改，这个保证就破了。本项目用包级封装 + 文档来约束，
> 而不是靠语言层面绝对禁止。

### 8.2 四条缺点，每条都带缓解

**缺点一：类数量膨胀。**

5 个状态 = 5 个类。如果有 15 个状态，就是 15 个类。

| 缓解方式 | 适用条件 |
|---|---|
| 先看是不是能用**数据**描述差异 | 能描述 → 用一个类 + 参数（参看责任链的审批链） |
| 用表驱动 | 转移只有「转到哪」 |
| 接受它 | 状态数 ≤ 8 且规则稳定 |

**缺点二：加动作的成本高。**

见 §7.2：改 5 个文件。

| 缓解方式 | 适用条件 |
|---|---|
| 接受它（因为编译期安全） | 动作很少变 |
| 改用表驱动 | 动作频繁变 |
| 把 `switch` 收进一个 EnumMap | 会牺牲类型安全，不推荐 |

**缺点三：调试时跳转多。**

从 `order.pay()` 到真正执行，要跳三次（`transition` → `accept` → 具体分支）。
相比一个 200 行的 `switch`，IDE 里追踪更难。

| 缓解方式 | 说明 |
|---|---|
| 给每个类写清楚 javadoc | 本项目 68% 是注释，部分原因就是为了补这个 |
| 用轨迹 | `history()` 直接告诉你“发生了什么”，不用跟调用栈 |
| 异常信息带上下文 | `unreachable()` 会打出状态名和动作名 |

**缺点四：本项目的轨迹不持久、不加锁。**

这是**明确的局限**，不是遗漏：

| 局限 | 真实项目应该怎么做 |
|---|---|
| `history` 在内存里，重启就没了 | 轨迹入库，与状态更新同一个事务 |
| `transition()` 无锁 | `synchronized` / 数据库乐观锁 |
| 状态无持久化 | 存独立 `status` 列（见 §7.4） |
| 物流单号是派生的 | 接入真实单号服务 |

本项目把这几条写在这里，而不是假装完整——“能编译运行”和“能上生产”是两件事。

### 8.3 七个常见误区

#### 误区一：把所有 `if-else` 都改成状态类

这是最普遍的误用。看到 `if (status == 1)` 就想改成状态模式。

**判据回到 §3.2**：如果分支里只是「算一个值」或「拼一个字符串」，
不是「同一个方法在不同状态下行为不同」，那用 `if` 就好。

反例：

```java
// 这不需要状态模式
if (amount > 100) discount = 0.9;
else if (amount > 500) discount = 0.8;
```

这与状态无关，是**价格档位**——应该用数据（一个 `List<Threshold>`）描述。
本项目在责任链那一章就是这么做的：一个类 + 四个实例，不是四个类。

#### 误区二：状态对象里存业务数据

```java
// 错的
public enum CancelledState implements OrderState {
    INSTANCE;
    private BigDecimal refundAmount;   // ← 灾难
    ...
}
```

因为它是全局单例，第二张订单取消时会把第一张的金额覆盖掉。

**根本原因：状态对象存的是「行为」，不是「数据」。** 数据必须存在 `Order` 上。
如果某个状态真的需要携带数据（比如「限时折扣中」要记截止时间），
那它就不该用 `enum`，应该用 `record`：“有数据就用类，没数据就用枚举”。

#### 误区三：用异常表达业务拒绝

```java
// 错的
if (!allowed) throw new BizException("已发货不能取消");
```

后果：Web 层返回 500（不是 400）、批量任务中断、日志被异常栈淹没。

**正确判据**：这个失败是调用方**应该预料并处理**的吗？是→返回值；不是→异常。

用户点一个灰掉的按钮、定时任务扫到一张已完成单，都是“应该预料”。

#### 误区四：把 `setState` 开成 `public`

原项目栽在这里（§2.4）。一旦有了 `public setState`，
所有状态机约束都变成“建议”，而不再是“保证”。

本项目的做法：`state` 字段 `private` 且无 setter，`transition()` 是唯一写入口。

**一个常见的反驳**：“测试里需要 `setState` 才能把订单构造到某个中间状态。”
正确做法是**走真实路径**（`pay()` → `ship()`），或者写一个测试专用的构造器。
把 `setState` 开成 `public` 是把自己的防线拆了去方便测试。

#### 误区五：忘记前置校验（本项目真的踩过）

这是开发过程中真实出现的一个 bug，值得单独说。

最初的 `transition()` 是这么写的：

```java
// 错的
TransitionResult result = state.accept(action, this);
```

看起来没问题。但终态的 `ALLOWED` 是空的，而 `CompletedState.accept()` 直接抛
`unreachable`。于是：

> 用户在已完成订单上再点一次确认收货 → 得到一个 `IllegalStateException` → Web 层 500。

**修复就是加一行前置校验：**

```java
if (state.allowedActions().contains(action)) {
    result = state.accept(action, this);
} else {
    result = TransitionResult.rejected(action, state, ...);
}
```

这个 bug 的教训很深，因为它**测试时不会暴露**——正常路径走不到终态。
只有手工去点一下“已完成订单的确认收货”才会碰到。

> 这也是 Demo 2 第七步（已完成再确认）存在的原因：它是这个 bug 的回归测试。

#### 误区六：只记成功的轨迹

见 §5.5 与 §6.3。只记成功 = 只留下结果，丢掉了过程。

一个更隐形的后果：**监控会失真**。如果只统计成功转移，
“已发货不能取消”这条规则被触发多少次、哪些用户被卡住，全部看不到。

#### 误区七：拿状态模式做流程编排

```java
// 错的：把“注册流程”做成六个状态
PendingState → EmailVerifiedState → ProfileFilledState → ...
```

这是把状态模式当**工作流引擎**用。问题在于：

1. 流程通常要求“能回退到任意步骤”，而状态模式的转移是有向的；
2. 流程通常需要“并行分支、会签、超时”，这些都是引擎的职责；
3. 步骤会频繁变（业务方每周都在调流程），而状态模式最怕这个。

**判据**：状态是“对象当前处于什么状态”，不是“流程走到了第几步”。
后者应该用工作流引擎或一个 `step` 字段 + 配置。

### 8.4 与五个相似模式的区别

这是面试里最容易被追问的部分。

| 维度 | 状态 | 策略 | 责任链 | 命令 | 备忘录 |
|---|---|---|---|---|---|
| **一句话** | 状态决定行为 | 算法可替换 | 处理者接力 | 请求对象化 | 快照可恢复 |
| **调用方写什么** | `order.pay()` | `setStrategy(s)` | `chain.submit(req)` | `invoker.execute(cmd)` | `caretaker.save()` |
| **关键问题** | 我现在是什么状态 | 用哪种算法 | 该谁处理 | 怎么把请求变成对象 | 怎么把状态存下来 |
| **会不会拒绝** | 会，非法转移被挡 | 不会，总要算结果 | 会，无人处理返回空 | 不会（可撤销/重试） | 不会 |
| **谁在变** | Context 内部的状态 | 注入进来的算法 | 链上的节点 | 请求的封装 | 被保存的快照 |
| **转移由谁触发** | **状态自己** | 调用方 | 处理者自己传下去 | 调用方 | — |
| **典型误用** | 拿状态做流程编排 | 用策略消灭所有 `if` | 把管道当责任链 | 把命令当回调 | 忘了控制快照体积 |

#### 状态 vs 策略：最容易混的一对

两者结构几乎一样（都有一个接口 + 多个实现 + 一个 Context）。区别只有一个：

> **谁决定下一次用哪个实现？**

- **策略**：调用方决定。`calculator.setStrategy(new VipDiscount())`——
  换完之后一直用它，直到调用方再换；
- **状态**：**状态自己决定**。`PendingState.accept(PAY)` 返回 `PaidState.INSTANCE`——
  调用方根本没说要换，是状态自己把下一步交出去了。

这个差别导致了一个直接后果：**策略不会拒绝，状态会拒绝。**
因为策略是“你让我用哪个我就用哪个”，状态是“你要做的事在我这儿能不能做，由我说了算”。

一个更实用的判据：

| 问题 | 答案 → 用哪个 |
|---|---|
| 两种算法可以**随意切换且随时可换**？ | 策略 |
| 换不换取决于**业务走到哪一步**？ | 状态 |
| 调用方知道当前用的是哪个吗？ | 知道 → 策略；不知道 → 状态 |
| 需要“当前不允许”这个语义吗？ | 需要 → 状态 |

#### 状态 vs 责任链：一个关于“拒绝”的对照

两者都会拒绝，但拒绝的方式完全不同：

| | 状态 | 责任链 |
|---|---|---|
| 谁拒绝 | 当前状态对象 | 整条链（都没处理） |
| 拒绝后怎么办 | 停在原地，返回原因 | 返回空 / 默认结果 |
| 有几个对象参与决策 | 1 个 | N 个，依次尝试 |
| 调用方知道是谁拒绝的吗 | 知道（`from` 就是它） | 通常不知道 |

责任链的“不敢确定谁处理”在审批、折扣管道里是优点（灵活）；
在订单状态里是缺点（必须知道当前到底是什么状态）。

#### 状态 vs 命令：它们经常一起用

命令模式把“请求”变成对象，好处是能排队、能撤销、能记日志。
把两者结合的一个典型场景：

```java
// 命令封装了“要做什么”，状态决定“能不能做”
public record PayCommand(Order order) implements Command {
    public void execute() {
        TransitionResult r = order.pay();          // ← 状态在这里把关
        if (!r.success()) throw new CommandRejectedException(r.message());
    }
}
```

区别一句话：**命令管“请求的生命周期”，状态管“对象的行为分派”。**
它们不竞争，而是前后把关。

#### 状态 vs 备忘录：两个“状态”不是一回事

初学者容易把这两个混淆，因为它们的名字里都有“状态”。

| | 状态模式 | 备忘录模式 |
|---|---|---|
| “状态”指什么 | 业务状态（待支付/已发货） | 对象快照（那一刻的所有字段） |
| 目的 | 行为随状态变 | 可回滚到之前 |
| 能不能回退 | **不能**（状态机是有向的） | **就是用来回退的** |
| 本项目在哪 | `state` 包 | `memento` 包 |

**关键区别**：状态模式的“状态”是**业务概念**，
备忘录的“状态”是**内存快照**。前者在别的系统里可能叫 `status`，
后者在数据库里叫“版本”。

---

## 9. 面试与自测

### 9.1 十道自测题

先不看答案。每道题都想一遍「为什么」，再看后面。

**1. `OrderState` 为什么没有 `isFinal()`？**

因为它可以推导：`isFinal()` 恒等于 `allowedActions().isEmpty()`。
多一个方法 = 多一个可能和 `allowedActions()` 矛盾的真相源。

**2. 把 `transition()` 里的 `if (result.success())` 去掉，会发生什么？**

失败时 `result.to()` 是 `null`，`state` 被赋成 `null`。
下一次任何调用都会在 `state.allowedActions()` 处 NPE。
而且轨迹里已经记了一条“被拒绝”，看起来一切正常——故障在下一步才爆。

**3. 把前置校验（`allowedActions().contains`）拿掉，会发生什么？**

非法动作会直接进 `accept()`，终态抛 `IllegalStateException`。
一次正常的用户误操作变成 500 错误 + 告警。这个 bug 本项目真的踩过（§8.3 误区五）。

**4. 为什么 `CancelledState` 里不能存 `refundAmount`？**

因为状态对象是全局单例。第二张订单取消时会覆盖第一张的金额。
数据必须存在 `Order` 上，“规则的归属是状态，数据的归属是订单”。

**5. `List.copyOf` 和 `Collections.unmodifiableList` 的区别？本项目为什么用前者？**

前者是**真拷贝快照**，返回后内部再怎么变都不影响拿到的对象；
后者是**不可变视图**，内部一变外面看到的也跟着变。
`history()` 需要的是“某一时刻的记录”，所以必须用前者。

**6. 用 `Map.of` 做转移表的外层容器，会出什么问题？**

`enum` 的默认 `hashCode` 是 identity hash，`Map.of` 按它散列，
导致**迭代顺序每次 JVM 启动可能不同**。
于是同一份代码两次运行的输出顺序不一样，文档里的“真实输出”立刻变成假的。
必须用 `LinkedHashMap`，而且展示顺序要显式声明。

**7. 能直接 `allowedActions().toString()` 展示「允许的动作」吗？**

不能。`Set.of(...)` 的迭代顺序**不保证**。
必须按 `OrderAction.values()` 遍历过滤，顺序才由语言规范保证。

**8. `rejected()` 为什么没有 `to` 参数？**

签名即约束。拒绝就是“状态不变”，从签名上就不给你传错的机会。
这比在构造器里写 `if` 更早一步拦住错误（IDE 里就标红）。

**9. 新增一个动作要改几个文件？编译器帮什么忙？**

改 5~7 个文件。三个状态的 `switch` 会**全部编译失败**，
逼你逐个回答“这个动作在我这儿怎么处理”。§7.2 有详细清单。

**10. 什么时候该用表而不是状态模式？**

转移**只有“转到哪”、没有任何副作用**时。
一旦要“顺手发个单号 / 释放库存 / 发通知”，表就挂不住了。
Demo 4 用同一个订单号跑了两遍，差别是“物流单号：SF2005” vs “（空）”。

### 9.2 三道推导题

这三道没有标准答案，但有明确的评判标准。

#### 题一：要支持「部分退款」，怎么设计？

现状：`PaidState` 取消 → `CancelledState`。
新需求：用户可以只退一部分商品，退完订单变成「部分退款」。

**思考方向：**

1. 退款金额是**数据**还是**行为**？——是数据，所以不能存在状态对象上；
2. 它是新状态还是旧状态的一个字段？——看“退款后能不能再买”。
   如果能，那就是状态；如果只是订单的一个属性，那就是字段；
3. 「部分退款」之后还能不能发货？——这是业务规则，必须先问清楚。

**一个可能的答案：**

```java
public enum PartiallyRefundedState implements OrderState {
    INSTANCE;
    // 还能发货（剩下的商品），还能再次退款（再退一部分），还能取消
    private static final Set<OrderAction> ALLOWED =
            Set.of(OrderAction.SHIP, OrderAction.CANCEL);
}
```

金额存在 `Order.refundedAmount` 上，状态只负责“能不能”。

**评判标准**：能不能说出“数据与规则分离”这个原则，而不是只说“加一个类”。

#### 题二：支持「售后中」，且可以从「已发货」与「已完成」两处进入

这是状态模式里一个经典的难点：**多个入口指向同一个状态**。

**关键发现：这不是状态模式的问题，是图的形状问题。**

```
已发货 ──申请售后──▶ 售后中
已完成 ──申请售后──▶ 售后中
```

两条边，一个汇点。状态模式天然支持这个——两个状态各自在自己的 `accept()` 里
返回 `AfterSalesState.INSTANCE` 就行。

**要点：**

- 两个源状态的 `ALLOWED` 里都要加 `APPLY_AFTER_SALES`；
- `ShippedState` 加一条分支，`CompletedState` 从“空实现”改成真正的 `switch`；
  **这是两个终态不合并的又一个理由**（§5.3）；
- 转移表里两行都指向 `AfterSalesState`，自检自动覆盖。

**评价标准**：能不能指出“汇点不是问题，分岔才是”，“能不能说出两个终态此刻真的分开了”。

#### 题三：状态规则要运营在后台配置，怎么改？

**正确答案是：不要用状态模式。**

当规则变成“配置”时：

1. 规则的“真相”不在代码里而在数据库里，所以 `sealed` + `switch` 穷尽完全失效；
2. 新状态随时可能出现，不可能每次都发一次版；
3. 规则的合法性需要**启动时校验**（比如检测死循环、不可达状态）——
   这是表驱动实现需要额外做的事。

**应该改成：** 一张 `transition_rule(from_state, action, to_state, guard_expr)` 表
+ 一个规则引擎（轻量的话可用解释器模式）。

`com.patterns.behavioral.interpreter` 包里有现成的表达式实现，可以参考。

**评价标准**：能不能坚定地说“这个场景不该用状态模式”，而不是强行套用。

### 9.3 模拟面试：五轮追问

**Q1：状态模式和一堆 `if-else` 比，最大的好处是什么？**

不是“代码短”——实际上本项目代码量涨了 10.8 倍。
最大的好处是：**拒绝规则只有一个出处，而且漏不掉。**

`if-else` 版本里，“已发货不能取消”这条规则会被复制到 Web 接口、定时任务、
客服后台、运营工具。每处都是一次遗漏的机会。
状态模式里它只在 `ShippedState.ALLOWED` 里出现一次。

**Q2：那为什么不把 `if-else` 抽成一个方法，大家调它？**

抽成方法能解决“重复”，解决不了“穷尽性”。

```java
public boolean canCancel(Order o) {
    return o.getStatus() == PENDING || o.getStatus() == PAID;
}
```

问题在于：新加一个状态（比如「退款中」）时，这个 `if` **不会编译失败**，
它会默默地把新状态归入“不能取消”。
而状态模式里，新状态必须在 `permits` 里登记，
而且 Demo 5 的自检会立刻告诉你“两份规则不一致”。

**Q3：状态多了怎么办？比如 20 个状态。**

先说结论：20 个状态已经不是状态模式的好场景了。

应该先问两个问题：

1. 这 20 个状态里，有多少能用**数据**描述差异？（能就用参数，不用类）
2. 转移规则是不是经常变？（是就用表）

如果确实有 20 个真正不同的状态且规则稳定，那就接受 20 个类——
但一定要配套：按业务域分包、每个类一行 javadoc 说清楚它存在的理由。

**Q4：状态和策略到底怎么区分？**

一句话：**谁决定下一次用哪个实现。**

- 策略：调用方决定，`setStrategy(...)`，换完一直用；
- 状态：**状态自己决定**，`accept()` 返回下一个状态，调用方没说换。

衍生的差别：策略**不会拒绝**（你让它算它就算），状态**一定会拒绝**（非法就是非法）。

**Q5：你的状态模式并发安全吗？**

本项目不安全，而且我在文档里写明了（§8.2 缺点四）。

如果要安全，最小修法是给 `transition()` 加 `synchronized`——
**只需改一个地方，因为状态只有一个写入口。**
生产环境更常用数据库乐观锁：

```sql
UPDATE orders SET status = 'PAID' WHERE order_no = ? AND status = 'PENDING'
```

检查影响行数为 1 才算成功。这相当于把状态校验下推到数据库的行锁上。

**这轮追问的加分点**：主动说出“本项目不加锁”，而不是声称已经线程安全。

### 9.4 一页纸总结

```
状态模式的全部内容：

【一个问题】
  同一个动作，在不同状态下行为不同，而且这种不同无法用数据描述。

【三个部件】
  状态（OrderState + 5 个实现）  → 回答“这个动作在我这儿意味着什么”
  上下文（Order）               → 持有状态，是唯一写入口
  动作（OrderAction）           → 让调用方能够表达意图

【三条不变量】
  1. 失败绝不转移     → if (result.success()) state = result.to();
  2. 失败无副作用     → 非法动作不进 accept()
  3. 拒绝也是记录     → 成功失败都进轨迹

【一条判据】
  转移只有“转到哪”  → 用表
  转移还要顺手做事  → 用状态模式

【一个最常见的 bug】
  忘了前置校验 → 终态抛异常 → 用户误操作变 500
```

---

## 附录 A：文件清单

| # | 文件 | 物理行 | 代码行 | 角色 |
|---|---|---|---|---|
| 1 | `OrderAction.java` | 65 | 14 | 动作枚举 |
| 2 | `OrderState.java` | 154 | 14 | 状态接口（`sealed`） |
| 3 | `PendingState.java` | 99 | 32 | 待支付 |
| 4 | `PaidState.java` | 91 | 33 | 已支付 |
| 5 | `ShippedState.java` | 84 | 26 | 已发货 |
| 6 | `CompletedState.java` | 94 | 18 | 已完成（终态） |
| 7 | `CancelledState.java` | 91 | 18 | 已取消（终态） |
| 8 | `Order.java` | 415 | 128 | Context |
| 9 | `TransitionResult.java` | 138 | 34 | 转移结果（record） |
| 10 | `HistoryEntry.java` | 77 | 16 | 轨迹条目（record） |
| 11 | `TransitionTable.java` | 227 | 71 | 表驱动对照 |
| 12 | `Client.java` | 416 | 220 | 五段 Demo |
| | **合计** | **1951** | **624** | |

依赖的外部类（两个，都在 `com.patterns.util`）：

| 文件 | 行数 | 用途 |
|---|---|---|
| `Money.java` | 91 | 金额构造与格式化 |
| `DisplayWidth.java` | 139 | 中文表格对齐 |

**依赖关系图：**

```
Client ──────────▶ Order ──────────▶ OrderState ◀──── PendingState
  │                 │                    ▲              PaidState
  │                 │                    │              ShippedState
  │                 ├──▶ TransitionResult │              CompletedState
  │                 └──▶ HistoryEntry     │              CancelledState
  │                                       │
  ├──▶ TransitionTable ─────────────────┘
  ├──▶ Money（util）
  └──▶ DisplayWidth（util）

注意：五个状态之间互相不知道对方的存在，
     唯一的横向依赖是“返回下一个状态”，而那是通过 OrderState 类型完成的。
```

## 附录 B：运行方式与验证记录

### B.1 编译

```powershell
$jdk  = "F:\enCodingApps\JDK\jdk-25"
$root = "E:\codeFile\codeFather\design_patterns_pro"

& "$jdk\bin\javac" --release 25 -encoding UTF-8 -g -Xlint:all `
    -d "$root\out" -sourcepath "$root\src" `
    "$root\src\com\patterns\behavioral\state\Client.java"
```

实测：`EXIT=0`，告警 **0 条**。

### B.2 运行

```powershell
& "$jdk\bin\java" "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" `
    -cp "$root\out" com.patterns.behavioral.state.Client
```

**两个编码参数必须同时给。** 只给 `-Dfile.encoding` 不够——
Java 18+ 的 `System.out` 用的是 `stdout.encoding`，
少了它，中文在默认代码页 936 的控制台里会变成 `锟斤拷`。

### B.3 验证记录

| 项 | 结果 |
|---|---|
| 编译告警（`-Xlint:all`） | 0 条 |
| 输出行数 | 155 行 |
| 行尾空白行数 | 0 |
| 第 1 次运行 SHA256 | `AD11B446...A0F1D1` |
| 第 2 次运行 SHA256 | `AD11B446...A0F1D1` |
| 第 3 次运行 SHA256 | `AD11B446...A0F1D1` |
| 一致性 | **三次完全一致** |

完整哈希：`AD11B4469BA736A20E170E7998DD40554FFCB6573EE180B0C38CA77BE8A0F1D1`。

### B.4 原项目的输出（对比用）

原项目 `src/BehaviorType/StatePattern/` 编译运行后的真实输出，**只有 4 行**：

```
订单处于待支付状态，请支付。
订单已支付，请发货。
订单已发货，请等待接收。
订单已完成。
```

本项目 155 行 vs 原项目 4 行。差额不是“代码变啰嗦了”，而是：

| 原项目展示了什么 | 本项目额外展示了什么 |
|---|---|
| 状态能往前走 | 拒绝、轨迹、副作用、对照实现、自检 |
| 一条直线 | 五个状态 × 四个动作 = 20 种组合 |

## 附录 C：相关模式交叉索引

本项目的 23 种模式互相独立，但有几个与状态模式天然相邻：

| 模式 | 目录 | 与本模式的关系 |
|---|---|---|
| **策略** | `behavioral/strategy` | 结构几乎一样，区别在「谁决定下一次用哪个」 |
| **责任链** | `behavioral/chainofresponsibility` | 都靠拒绝，但责任链是 N 个处理者接力，状态是 1 个对象自身变化 |
| **命令** | `behavioral/command` | 可组合：命令管“请求的生命周期”，状态管“能不能做” |
| **备忘录** | `behavioral/memento` | 名字都有“状态”但完全不同：备忘录存快照可回滚，状态是行为分派 |
| **模板方法** | `behavioral/templatemethod` | 都在父类/接口层定义骨架，但状态靠**组合**切换，模板方法靠**继承**固化 |
| **解释器** | `behavioral/interpreter` | 规则要变成可配置时，用解释器替代状态类 |
| **状态机（非模式）** | — | 状态模式只是 FSM 的一种 OO 实现，不是唯一实现 |

**一个常用的辨析：状态 vs 模板方法**

初学者容易把这两个搞混，因为两者都要求“子类实现几个方法”。区别在于**变化的方向**：

```
模板方法：变化在“子类是谁”，一旦确定就固定（编译期多态）
状态模式：变化在“当前是哪个状态”，运行时可以变（运行时多态 + 组合）
```

模板方法的 `this` 永远指向同一个对象；状态模式的 `this` 不变，但 `state` 指向的对象在变。

## 附录 D：原项目七个问题对照表

这是 §2 的压缩版，一页看完“改了什么、为什么改”：

| # | 原项目问题 | 本项目怎么解决 | 在哪个文件 | 如果没改会怎样 |
|---|---|---|---|---|
| 1 | `request()` 无参数，无法表达意图 | 四个业务方法 `pay/ship/confirm/cancel` | `Order` | 查个订单把单给“支付”了 |
| 2 | 四个动作压成一个，直线无分支 | `OrderAction` 枚举 + 每个状态一套 `switch` | `OrderAction` + 五个状态 | 不能取消、不能拒绝 |
| 3 | `setState` 是 `public` | `state` 私有且无 setter，`transition()` 唯一写入口 | `Order` | 一句话跳过所有校验 |
| 4 | `CompletedState` 静默，调了不报错 | `TransitionResult.rejected` 带原因，且入轨迹 | `TransitionResult` | 调用方无法区分“成功”和“什么也没发生” |
| 5 | 每次转移 `new` 一个状态对象 | `enum` 单例 | 五个状态 | 无意义对象；`==` 也不能用 |
| 6 | 没有允许动作集合，界面只能自己判 | `allowedActions()` 一份规则两个消费者 | `OrderState` | 前后端规则不同步 |
| 7 | 没有轨迹，出问题只能猜 | `HistoryEntry` 记全部尝试（含失败） | `HistoryEntry` + `Order` | 用户投诉无从举证 |

**七个问题里，第 3 个和第 1 个是结构性的**——它们不是“改两行”能修的，
而是需要重新划分职责。其余五个在职责划对之后都自然解决了。

## 附录 E：概念速查

| 术语 | 在本项目里指什么 | 一句话 |
|---|---|---|
| **状态（State）** | `OrderState` 及 5 个实现 | 回答“这个动作在我这儿意味着什么” |
| **上下文（Context）** | `Order` | 持有状态，把业务动作转发给它 |
| **动作（Action）** | `OrderAction` | 调用方想做的事，不是“下一步” |
| **转移（Transition）** | `Order.transition()` | 校验 → 问状态 → 切换 → 记轨迹 |
| **拒绝（Rejected）** | `TransitionResult.rejected()` | 不是异常，是正常业务分支 |
| **终态（Final State）** | `CompletedState` / `CancelledState` | `allowedActions()` 为空的状态 |
| **轨迹（History）** | `List<HistoryEntry>` | 全部尝试（成功 + 失败）的不可变快照 |
| **不变量（Invariant）** | 失败不转移 / 失败无副作用 | 由集中代码保证，而不是靠约定 |
| **表驱动（Table-driven）** | `TransitionTable` | 同一套规则的另一种写法，对照用 |
| **穷尽（Exhaustive）** | `switch (action)` 无 `default` | 新增枚举值会编译失败 |

## 附录 F：版本与工具链

| 项 | 值 |
|---|---|
| JDK | 25（`F:\enCodingApps\JDK\jdk-25`） |
| 编译参数 | `--release 25 -encoding UTF-8 -g -Xlint:all` |
| 运行参数 | `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8` |
| 语言特性使用 | `record` / `sealed` / `switch` 表达式 / `var` 未使用 / 文本块未使用 |
| 外部依赖 | **无**（原生 Java，不引任何框架与测试库） |
| 构建方式 | `javac` 直编，无需 Maven/Gradle |
| 代码风格 | 4 空格缩进，行宽 100，全角中文标点 |

**为什么不用 `var`？** 因为教学代码里变量类型是**有效信息**。
`TransitionResult result = state.accept(action, this)` 比 `var result = ...`
多告诉读者一件事：这个方法的返回类型是什么。

**为什么不用文本块？** 本项目输出全靠 `System.out.println` 拼接，
没有多行字符串需求；而 javadoc 里的示例代码用 `<pre>` 更可控。

---

## 写在最后

如果把整篇文档压缩成三句话：

1. **状态模式不是“把 if 拆成类”**，它是把“同一个动作在不同状态下的不同后果”
   安放到各自的状态对象里，让**调用方不需要知道状态**。
2. **它真正的价值在于拒绝**。只会往前走的流程不需要它，
   会挡住不该做的事的流程才需要。
3. **它不总是对的**。转移只有“转到哪”时，一张 `Map` 就够了——
   Demo 4 用同一张单跑了两条路，差异是“SF2005” vs “（空）”，
   那个括号里的空，就是这个模式存在的理由。

如果你的项目里正好有这样一段代码——一堆 `if (status == ...)` 被复制到了五个入口，
而且总有一个入口漏了分支——那它就是你该用状态模式的地方。

如果只是两个状态、两行 `if`，而且半年都不会变——保持现状，别动它。
