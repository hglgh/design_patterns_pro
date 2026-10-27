# 中介者模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么让同事只认识一个中心，而不是互相认识」，
> 以及什么时候直接调用更清楚。
>
> 第二层：[README.md](README.md)（售后协同 Hub、消息类型、角色与通知）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**中介者就是把「大家互相打电话」改成「大家都打给总机，由总机转接」；**
**同事不再持有彼此，只持有总机。**

### 什么需求会让开发者想到它

售后系统里，一笔退款要经过好几个角色：

- 客户发起退款；
- 客服受理；
- 财务核对金额；
- 仓库检查退货入库；
- 金额大或客户升级投诉时，还要主管介入。

第一版很自然：`Customer` 持有 `Agent`、`Finance`、`Warehouse`，
`Agent` 又持有 `Finance`、`Warehouse`，谁需要谁就直接调。

角色只有四个时还能忍。加到第五个「主管」时，你会发现：
**每个角色都可能需要知道主管，而主管也可能需要知道财务和仓库。**

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldCustomer {
    private final OldAgent agent;
    private final OldFinance finance;
    private final OldWarehouse warehouse;

    void askRefund(String orderNo) {
        System.out.println("[客户] 申请退款：" + orderNo);
        agent.handle(orderNo);
        finance.checkRefund(orderNo);      // 又直接找财务
        warehouse.checkStock(orderNo);     // 又直接找仓库
    }
}

class OldAgent {
    private final OldFinance finance;
    private final OldWarehouse warehouse;

    void handle(String orderNo) {
        System.out.println("[客服] 受理 " + orderNo + " 的退款申请");
        finance.checkRefund(orderNo);
        warehouse.checkStock(orderNo);
    }
}
```

真实输出（节选）：

```text
[客户] 申请退款：ORDER-1001
  [客服] 受理 ORDER-1001 的退款申请
  [财务] 核对 ORDER-1001 的退款金额
  [仓库] 检查 ORDER-1001 的退货入库状态
  [财务] 核对 ORDER-1001 的退款金额
  [仓库] 检查 ORDER-1001 的退货入库状态
```

注意最后两行：**财务和仓库各被调用了两次**。
客户不知道客服已经处理过，又自己调了一遍。这不是谁粗心，是协作流程没有唯一入口。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
Customer ──▶ Agent ──▶ Finance
    │           └────▶ Warehouse
    ├────────▶ Finance
    └────────▶ Warehouse
```

四个角色，依赖边已经有 5 条。每加一个角色，可能要加多条边。
这就是常说的 **N×N 依赖**：不是数量大，而是**每条边都代表一个需要维护的「谁知道谁」**。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 新增「主管」角色 | Customer/Agent/Finance 都可能要加字段和调用 | 改多个类，回归多条流程 |
| 调整退款顺序 | 改 Customer 或 Agent 的方法体 | 流程散落，改一处漏一处 |
| 退款金额大时自动升级 | 在 Customer/Agent 里加 `if` | 判断逻辑复制到多个角色 |
| 禁止客户直接找财务 | 删掉 Customer 的 finance 字段和调用 | 牵一发动全身 |
| 记录完整协作链路 | 每个角色各自打日志 | 链路拼不起来 |

### 3.3 为什么加一个 `if` 不够

因为问题不是「缺一个判断」，而是**协作关系本身是网状的**。
只要同事之间还互相持有，每加一个角色就会增加若干条边；
每调整一次流程，就要在多个类里同步修改。
你需要的是把「谁通知谁」从同事里拿出来，放到一个地方。

---

## 4. 从问题推导出设计

### 第一步：同事只保留一个依赖——Hub

```java
abstract class Participant {
    protected final String name;
    protected final Hub hub;

    Participant(String name, Hub hub) {
        this.name = name;
        this.hub = hub;
    }

    abstract String role();
}
```

- **为什么需要 `Hub`？** 同事要能发出消息，但不能直接找具体同事。
- **为什么同事不再持有其他同事？** 一旦持有，依赖又变回网状。
- **不加 Hub 会怎样？** 每个同事的构造器都要列出一串协作者，新增角色时全部要改。

### 第二步：Hub 负责注册和路由

```java
interface Hub {
    void register(Participant participant);
    void send(Participant from, String topic, String message);
}

class AfterSalesHub implements Hub {
    private final List<Participant> participants = new ArrayList<>();

    public void send(Participant from, String topic, String message) {
        List<String> targets = switch (topic) {
            case "refund" -> List.of("AGENT", "FINANCE");
            case "escalate" -> List.of("SUPERVISOR", "AGENT");
            default -> List.of();
        };

        for (Participant participant : participants) {
            if (participant != from && targets.contains(participant.role())) {
                participant.receive(from.name, topic, message);
            }
        }
    }
}
```

- **为什么 `send` 要带 `from`？** 避免把消息回传给发送者自己。
- **为什么按 `topic` 路由，而不是广播给所有人？** 中介者的价值就在「知道谁该收到」；
  如果所有人都收到，那更像观察者，不是中介者。
- **为什么用 `role()` 而不是具体类型判断？** 同事只暴露角色，Hub 不需要认识每个具体类。

### 第三步：同事只负责发和收

```java
class Customer extends Participant {
    void askRefund(String orderNo) {
        System.out.println("[客户] 申请退款：" + orderNo);
        hub.send(this, "refund", orderNo);
    }

    void escalate(String orderNo) {
        hub.send(this, "escalate", orderNo);
    }
}
```

客户不知道谁会收到，也不需要知道。
**它只表达「我要退款」或「我要升级」，由 Hub 决定通知谁。**

### 最终对象关系

```text
        ┌──────────────┐
        │     Hub      │  ← 知道所有角色和路由规则
        └──────┬───────┘
   ┌───────────┼───────────┬───────────┐
   ▼           ▼           ▼           ▼
Customer     Agent      Finance    Warehouse
   │
   └── 只持有 Hub，不持有其他同事
```

---

## 5. 用具体数据走一遍

### 5.1 注册

```java
AfterSalesHub hub = new AfterSalesHub();
Customer customer = new Customer("客户小王", hub);
Agent agent = new Agent("客服小李", hub);
Finance finance = new Finance("财务小张", hub);
Warehouse warehouse = new Warehouse("仓库小赵", hub);

hub.register(customer);
hub.register(agent);
hub.register(finance);
hub.register(warehouse);
```

### 5.2 申请退款

```java
customer.askRefund("ORDER-1001");
```

```text
① Customer 打印「申请退款」
② hub.send(customer, "refund", "ORDER-1001")
③ targets = [AGENT, FINANCE]
④ 遍历注册列表：
   - customer == from，跳过
   - agent.role()=AGENT，命中 → 收到
   - finance.role()=FINANCE，命中 → 收到
   - warehouse.role()=WAREHOUSE，不命中 → 不收到
```

真实输出：

```text
[客户] 申请退款：ORDER-1001
  [客服小李] 收到 客户小王 的 refund：ORDER-1001
  [财务小张] 收到 客户小王 的 refund：ORDER-1001
```

### 5.3 新增主管并升级投诉

```java
Supervisor supervisor = new Supervisor("主管老周", hub);
hub.register(supervisor);

customer.escalate("ORDER-1001");
```

```text
targets = [SUPERVISOR, AGENT]
→ 客服小李、主管老周收到；财务、仓库不收到
```

真实输出：

```text
[客户] 升级投诉：ORDER-1001
  [客服小李] 收到 客户小王 的 escalate：ORDER-1001
  [主管老周] 收到 客户小王 的 escalate：ORDER-1001
```

### 5.4 最容易写错的三处

1. **注册顺序决定打印顺序**：Hub 遍历的是注册列表，谁先注册谁先收到。
   如果业务要求「主管必须先于客服收到」，只靠这个 Hub 保证不了。
2. **`participant != from` 不能省**：否则客户会收到自己发出的退款消息，
   在真实系统里可能触发重复处理。
3. **主管没注册时**：`escalate` 的 targets 里有 `SUPERVISOR`，但列表里没有这个角色，
   消息只会到客服。**Hub 不会报错，也不会补人**——路由规则和注册状态要一致。

---

## 6. 改造前后对比

### 新增「主管」角色

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | Customer/Agent/Finance 可能都要改 | 新增 `Supervisor` |
| 要改的旧代码 | 构造器、字段、调用处 | 无；只需 `hub.register(supervisor)` |
| 路由调整 | 散落在各同事方法里 | 改 Hub 的 `targets` 表 |
| 复用 | 流程和角色绑死 | 同事可独立测试，Hub 可单独测路由 |

### 但调用方仍然要改

- 新角色要**注册**，否则收不到消息；
- 新主题要在 Hub 里**加路由规则**；
- 如果要求严格顺序、事务、重试，Hub 还要额外实现，模式不自动提供。

### 引入模式新增的成本

- Hub 会越来越知道所有角色和规则，容易变成**上帝类**；
- Hub 是单点，出问题所有协作都受影响；
- 同事之间不再直接调用，排查一个业务流程要同时看同事和 Hub；
- 只有两三个对象时，直接调用更直观。

---

## 7. 适用边界

### 适合用

- 多个同事之间存在**网状的相互调用**，新增角色会牵动多处；
- 协作/路由规则**集中管理**比散落各处更清晰；
- 同事希望独立变化、独立测试，不想持有彼此的引用；
- 流程会调整（谁先谁后、金额大转主管），希望改一处而不是改一片。

### 不适合用

- 只有两三个对象，调用关系简单且稳定——直接调用更清楚；
- 所有消息都是**广播**，没有路由规则——观察者更合适；
- 协作需要强事务/顺序保证——中介者只是结构，不提供事务能力。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 外观 | 外观是**单向**的「调用方 → 子系统」，简化入口；中介者是**多向**的同事协作，同事也会主动发消息 |
| 观察者 | 观察者是**一对多广播**，发布者不知道订阅者是谁；中介者**知道角色和路由**，按主题定向通知 |
| 责任链 | 责任链是请求沿链**逐个尝试**；中介者是中心**一次路由**给多个相关角色 |

---

## 8. 小练习

### 练习 1（场景判断）

一个聊天室：用户发消息，所有人都能看到；再加「私聊」和「管理员禁言」。
要不要用中介者？

**答案**：聊天室本身就是典型的中介者：用户不直接持有其他用户，消息发给房间/服务器，
由它决定广播给谁。加私聊、禁言时，规则集中在服务器，用户端不用改。
如果只是两个人点对点聊天，直接调用对方就行，不需要中介者。

### 练习 2（解释设计原因）

为什么 `Participant` 只持有 `Hub`，而不是同时持有 `Hub` 和其他同事？
如果保留其他同事会怎样？

**答案**：只持有 Hub，依赖边从 N×N 降到 N×1，新增角色不影响旧同事。
如果保留其他同事，Hub 只是多了一个入口，网状依赖仍然存在；
调整流程时还是要改多个同事，中介者就退化成了「多一个转发方法」。

### 练习 3（执行过程）

主管**没有注册**时，`customer.escalate("ORDER-1001")` 会发生什么？
Hub 会报错吗？

**答案**：`targets` 是 `[SUPERVISOR, AGENT]`，但注册列表里没有 SUPERVISOR，
只有 AGENT 会收到消息。Hub 不会报错，因为它只是「在列表里找匹配的角色」。
这说明**路由规则和注册状态是两套东西**，部署时要保证一致。

### 练习 4（扩展推导）

如果新增一个主题 `"return"`，需要改哪些地方？同事类需要改吗？

**答案**：只需在 `AfterSalesHub.send` 的 `switch` 里加一个 `case "return" -> List.of(...)`；
发消息的同事调用 `hub.send(this, "return", ...)` 即可。
其他同事类不需要改。但如果新主题需要新的**发送方法**（比如 `Customer.askReturn`），
那要在对应的同事类里加一个方法——这是业务 API，不是协作结构。

---

## 读完后应能回答的 5 个问题

1. 中介者把 N×N 依赖变成了什么？为什么这能降低修改成本？
2. 为什么同事只应该持有 Hub，而不是其他同事？
3. 中介者和观察者都会「通知别人」，路由方式有什么根本不同？
4. Hub 为什么会容易变成上帝类？如何判断它已经过重？
5. 什么情况下两三个对象的直接调用比中介者更好？

---

## 下一步

- 深度文档：[README.md](README.md)（CollaborationHub、消息类型、角色）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/orderservice](../../combined/orderservice/)（订单协作）
