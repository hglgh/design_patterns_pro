# 状态模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么把状态判断搬进状态对象」，
> 以及什么时候 `if/switch` 反而更合适。
>
> 第二层：[README.md](README.md)（订单状态机、转移表、历史记录、非法流转）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**状态模式就是让「能不能做」跟着「现在是什么状态」走：**
**订单把动作转交给当前状态对象，状态对象决定执行还是拒绝。**

### 什么需求会让开发者想到它

一个订单系统，按钮的可用性随状态变化：

- 待付款：可以支付、可以取消；
- 已付款：可以发货、可以取消；
- 已发货：不能取消；
- 已取消：什么都不能做。

第一版用一个 `String state` 加 `if/else`，在每个动作方法里判断。
状态少的时候很清楚；状态一多，每个方法都变成一张状态表。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldOrder {
    String state = "待付款";

    boolean cancel() {
        if (state.equals("待付款") || state.equals("已付款")) {
            state = "已取消";
            return true;
        }
        return false;
    }
}
```

真实输出（节选）：

```text
新订单，状态=待付款
pay() → 成功，状态=已付款
ship() → 成功，状态=已发货
cancel() → 失败，状态=已发货
把状态改成「已付款 」（末尾多个空格），再 ship()：
ship() → 失败，状态=「已付款 」
看起来是已付款，却发不了货；编译器不会报错。
```

注意最后一行：状态字符串是 `public`，任何地方都能写。
多一个空格，`equals("已付款")` 就永远不成立，**编译器不会提醒，业务静默失败**。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldOrder.pay()    ──知道──▶ 待付款、已付款
OldOrder.ship()   ──知道──▶ 已付款、已发货
OldOrder.cancel() ──知道──▶ 待付款、已付款、已取消
客户端             ──可写──▶ state（任意字符串）
```

每个动作方法都知道**所有状态**，而且知道的还是同一张表的不同片段。
客户端也直接依赖 `state` 的字符串值。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 新增「退款中」 | `pay/ship/cancel` 三个方法都要加分支 | 改一处不够，容易漏 |
| 已发货允许「确认收货」 | 新增方法，又要在方法里判断状态 | 状态判断继续扩散 |
| 禁止客户端乱改状态 | 把 `state` 改 `private`，但内部仍是字符串 | 只堵住外部，内部仍可能拼错 |
| 记录状态流转历史 | 在每个 `if` 分支里插日志 | 业务和审计缠绕 |
| 测试「已发货不能取消」 | 构造对象、手动设 `state`、调方法 | 测试依赖内部字段 |

### 3.3 为什么加一个 `if` 不够

因为新增的不是「一种情况」，而是**一个新的状态，以及它在每个动作下的行为**。
一个状态要在 `pay/ship/cancel` 三个方法里各回答一次；
状态越多，`if` 的组合越多，而且这些回答散落在不同方法里，无法作为一个整体复用或测试。

---

## 4. 从问题推导出设计

### 第一步：把「一个状态下的所有行为」收进一个类

```java
interface OrderState {
    boolean pay(Order order);
    boolean ship(Order order);
    boolean cancel(Order order);
    String name();
}
```

- **为什么需要这个接口？** 订单要能统一地把动作转给「当前状态」，不写 `if`。
- **为什么一个状态要包含三个动作？** 因为「已发货不能取消」和「已发货不能支付」
  是同一个状态的知识，放在一起才能一眼看全。
- **不抽接口会怎样？** 订单还是得为每个状态写分支。

### 第二步：让状态自己决定「接受还是拒绝、转到哪」

```java
class PendingState implements OrderState {
    public boolean pay(Order order) {
        order.setState(new PaidState());
        return true;
    }

    public boolean cancel(Order order) {
        order.setState(new CancelledState());
        return true;
    }

    public boolean ship(Order order) {
        return false;              // 没付款不能发货
    }

    public String name() {
        return "待付款";
    }
}
```

`PaidState`、`ShippedState`、`CancelledState` 同理，只是各自允许的动作不同。
**状态对象持有「在这个状态下，每个动作该怎么做」的完整知识。**

### 第三步：Order 只做转发

```java
class Order {
    private OrderState state = new PendingState();

    void setState(OrderState state) {
        this.state = state;
    }

    void pay() {
        boolean ok = state.pay(this);
        System.out.println("pay() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
    }

    void ship() {
        boolean ok = state.ship(this);
        System.out.println("ship() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
    }

    void cancel() {
        boolean ok = state.cancel(this);
        System.out.println("cancel() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
    }
}
```

- **为什么 `Order` 要把 `this` 传给状态？** 状态需要修改订单的当前状态。
- **为什么 `state` 是 `private`？** 客户端不能再写任意字符串。
- **`Order` 还知道具体状态吗？** 只在初始化时 `new PendingState()`；
  之后所有转移由状态类决定。

### 最终对象关系

```text
Order（Context）
  │ 持有 OrderState
  │ pay/ship/cancel 转发
  ▼
OrderState（接口）
  ▲           ▲            ▲             ▲
Pending     Paid        Shipped      Cancelled
  │ pay→Paid  │ ship→Shipped │ cancel→false │ 全部拒绝
  │ cancel→Cancelled │ cancel→Cancelled
```

---

## 5. 用具体数据走一遍

### 5.1 一笔正常订单

```java
Order order = new Order();
order.pay();
order.ship();
order.cancel();
```

```text
① 初始 state = PendingState
② pay()   → PendingState.pay   → setState(PaidState)，true
③ ship()  → PaidState.ship     → setState(ShippedState)，true
④ cancel()→ ShippedState.cancel→ false，状态不变
```

真实输出：

```text
pay() → 成功，状态=已付款
ship() → 成功，状态=已发货
cancel() → 被拒绝，状态=已发货
```

### 5.2 付款后取消

```java
Order another = new Order();
another.pay();
another.cancel();
another.pay();
```

```text
pay()    → PaidState
cancel() → PaidState.cancel → CancelledState，true
pay()    → CancelledState.pay → false
```

真实输出：

```text
pay() → 成功，状态=已付款
cancel() → 成功，状态=已取消
pay() → 被拒绝，状态=已取消
```

### 5.3 最容易写错的三处

1. **转移发生在 `Order` 打印之前**：`state.pay(this)` 里已经把 `state` 换成 `PaidState`，
   所以 `Order` 打印的是**转移后**的状态。如果先打印再转移，输出会差一行。
2. **`Order` 不要自己判断状态**：一旦在 `Order.pay()` 里写
   `if (state.name().equals("待付款"))`，状态知识又漏回 Context，模式白用。
3. **拒绝动作不能改状态**：`ShippedState.cancel()` 返回 `false` 且不调用 `setState`。
   如果拒绝时也改状态，订单会进入一个「被拒绝但状态变了」的怪状态。

---

## 6. 改造前后对比

### 新增「退款中」状态

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `OldOrder` 的 `pay/ship/cancel` | 新增 `RefundingState` |
| 要改的旧代码 | 三个方法各加一段 `if` | 在允许进入退款的状态里加一行转移 |
| 客户端 | 可能还要认识新字符串 | 不需要改 |
| 测试 | 手动设字符串，容易漏 | 每个状态类可单独测 |

### 但调用方仍然要改

- 要决定「从哪个状态可以进入退款中」（比如 `PaidState.refund()`）；
- 新状态要实现 `OrderState` 的全部动作，哪怕大部分是 `return false`；
- 如果状态需要持久化，还要考虑状态对象的序列化/重建。

### 引入模式新增的成本

- 每个状态一个类，状态多时类会膨胀；
- 状态转移散在各状态类里，**整体流程不容易一眼看完**；
- 新增一个动作要改接口和所有状态类（可以用默认方法缓解，但仍是改动）；
- 只有两三个状态、流程基本线性时，`if/switch` 更短。

> 状态模式不是「消灭 if」，而是把 if **分散到每个状态自己的类里**。
> 如果状态只有两三个，集中在一个方法里的 if 反而更好维护。

---

## 7. 适用边界

### 适合用

- 对象有**明确的生命周期状态**，不同状态下同一动作的行为/可用性不同；
- 非法流转需要被**明确拒绝**，而不是靠调用方自觉；
- 状态数量会增长，且每个状态的行为值得独立测试；
- 希望把「某状态能做什么」集中在一个类里，而不是散在多个方法。

### 不适合用

- 只有两三个状态、流程永远线性（新建 → 完成），一个枚举加 `if` 就够；
- 状态只是数据字段，行为没有差异（比如仅用于展示）；
- 状态转移需要复杂审计/可视化，且团队更习惯状态机表——可以用表驱动，不一定为每个状态建类。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 策略 | 策略由**外部选择**，算法之间通常不互相转移；状态由**内部流转**，一个状态会决定下一个状态 |
| 责任链 | 责任链把请求往后传，直到有人处理；状态是**当前对象自己换状态**，不是传给别的对象 |
| 状态机表 | 表驱动把「状态 × 事件 → 新状态」集中成数据；状态模式把行为放进类。表更易审计，类更易扩展行为 |

---

## 8. 小练习

### 练习 1（场景判断）

一个交通灯：红 → 绿 → 黄 → 红，每个灯亮固定时间，没有「非法操作」。
要不要用状态模式？

**答案**：如果只是定时轮转、没有用户动作、没有拒绝逻辑，一个枚举加 `next()` 就够，
状态模式反而增加类数量。状态模式的强项是**同一动作在不同状态下有不同结果，且要拒绝非法流转**；
交通灯没有「拒绝」，所以不是典型场景。

### 练习 2（解释设计原因）

为什么 `Order.pay()` 不自己判断状态，而要调用 `state.pay(this)`？
如果改回 `if` 会怎样？

**答案**：`state.pay(this)` 把「待付款能付、已付款不能付」的知识放在状态类里。
`Order` 只负责转发，新增状态时不用改 `Order`。
改回 `if` 后，`Order` 又知道所有状态，新增状态要改 `Order` 的每个动作方法，
状态知识重新散落。

### 练习 3（执行过程）

订单当前是 `PaidState`，调用 `cancel()` 后状态是什么？
再调用 `ship()` 会怎样？

**答案**：`PaidState.cancel()` 返回 `true` 并 `setState(new CancelledState())`，
所以状态变成「已取消」。再 `ship()` 进入 `CancelledState.ship()`，返回 `false`，
状态仍是「已取消」。**取消后不能发货**，由状态类保证。

### 练习 4（扩展推导）

要求「已发货的订单可以申请退款，进入退款中；退款完成后回到已付款或已取消」。
应该改哪里？`Order` 需要改吗？

**答案**：新增 `RefundingState`，实现三个动作（通常都拒绝，除了「退款完成」）；
在 `ShippedState` 里新增 `refund()` 或让 `cancel()` 转入退款中（取决于业务）；
`Order` 增加一个 `refund()` 转发方法。
`Order` 只多一个转发入口，不需要知道退款的具体规则。
如果退款流程很复杂，状态类里的转移会变多，这时可以配合状态机表/事件对象。

---

## 读完后应能回答的 5 个问题

1. 状态模式把「状态判断」从哪儿搬到了哪儿？为什么这能支持新增状态？
2. `Order` 为什么要把 `this` 传给状态对象？
3. 拒绝动作时为什么不能改状态？
4. 状态模式和策略模式都靠多态，决定「用哪个实现」的时机有什么不同？
5. 什么情况下枚举 + `if/switch` 比状态模式更合适？

---

## 下一步

- 深度文档：[README.md](README.md)（状态机、转移表、历史记录、非法流转）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/orderservice](../../combined/orderservice/)（订单生命周期）
