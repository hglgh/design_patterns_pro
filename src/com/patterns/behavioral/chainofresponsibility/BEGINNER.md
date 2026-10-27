# 责任链模式：通俗入门

> **这是第一层「入门」材料。** 目标：不背定义也能说清「为什么要把审批人串成一条链」，
> 以及什么时候一个 `if/else` 就够了。
>
> 第二层：[README.md](README.md)（完整工程版：审批链、折扣链、异常与兜底策略）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**责任链就是把「这件事该谁办」从一张写死的名单，变成一条可以接力传递的流水线：**
**当前这个人办不了，就交给下一个人，直到有人办为止。**

### 什么需求会让开发者想到它

一家公司的差旅报销按金额分级审批：

- 1000 元以内，组长批；
- 5000 元以内，经理批；
- 20000 元以内，总监批；
- 超过 20000 元，驳回。

一开始，这些规则写在一个 `ExpenseService.approve()` 里，四个 `if/else` 分支，很清楚。

三个月后，公司加了副总，50000 元以内归副总批；再后来，又要求「审批顺序可以按部门配置」。
这时问题就不再是「再加一个分支」了，而是**审批级别和顺序本身变成了会变的数据**。

---

## 2. 先看不使用设计模式的代码

改造前的核心代码（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class ExpenseService {

    void approve(String applicant, int amount) {
        if (amount <= 1000) {
            System.out.println(applicant + " 报销 " + amount + " 元 → 组长审批");
        } else if (amount <= 5000) {
            System.out.println(applicant + " 报销 " + amount + " 元 → 经理审批");
        } else if (amount <= 20000) {
            System.out.println(applicant + " 报销 " + amount + " 元 → 总监审批");
        } else {
            System.out.println(applicant + " 报销 " + amount + " 元 → 驳回：金额超出权限");
        }
    }
}
```

运行结果（节选）：

```text
张三 报销 800 元 → 组长审批
李四 报销 4000 元 → 经理审批
王五 报销 15000 元 → 总监审批
赵六 报销 50000 元 → 驳回：金额超出权限
```

看起来没问题，是因为**审批人只有四个，而且顺序永远不变**。
一旦顺序可配置、审批人可增删，这段代码就开始拖后腿。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
ExpenseService ──知道──▶ 组长、经理、总监、金额阈值、判断顺序
```

这里只有「一个类依赖所有规则」，连一个独立的审批人对象都没有。
`ExpenseService` 同时承担了四件事：**有哪些级别、每个级别的上限、先问谁、没人批怎么办**。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 加一个副总（≤50000） | 改 `approve()`，在 `else if` 链里插一段 | 改核心方法，所有金额都要重新回归 |
| 把总监提到经理前面 | 调换 `else if` 的顺序 | 业务规则藏在代码顺序里，容易改错 |
| 某个部门不设经理 | 再加一个 `if (department...)` | 规则开始组合爆炸 |
| 单独复用「组长审批」 | 做不到，它只是方法里的一段 | 无法测试、无法装配 |
| 记录每一级审批耗时 | 在方法里到处插计时代码 | 审批逻辑和监控逻辑继续缠绕 |

### 3.3 为什么加一个 `if` 不够

因为新增的不是「一种金额」，而是**一个新的处理者**。
处理者有可能是空的（某部门没有经理），有可能顺序会变，还有可能一个请求走到最后都没人处理。
这些都不是一个 `if` 能表达的，它们需要「节点 + 后继」这种结构。

---

## 4. 从问题推导出设计

### 第一步：把每个审批人变成一个类

先不管链，只把「组长批多少」从方法里拿出来：

```java
abstract class Approver {
    abstract void approve(String applicant, int amount);
}

class TeamLead extends Approver {
    void approve(String applicant, int amount) {
        if (amount <= 1000) {
            System.out.println(applicant + " 报销 " + amount + " 元 → 组长审批");
        } else {
            // 我批不了，接下来找谁？
        }
    }
}
```

这一步解决了「审批规则和 `ExpenseService` 绑死」的问题，但留下一个新问题：
**组长批不了的时候，它该找谁？**

### 第二步：让每个节点持有「下一个节点」

```java
abstract class Approver {

    private Approver next;

    Approver setNext(Approver next) {
        this.next = next;
        return next;             // 方便链式接线
    }

    protected void forward(String applicant, int amount) {
        if (next != null) {
            next.approve(applicant, amount);
        } else {
            System.out.println(applicant + " 报销 " + amount + " 元 → 无人可批，转总经理办公室");
        }
    }
}
```

- **为什么需要 `next`？** 当前节点办不了时，必须知道把请求交给谁。
- **为什么不直接在子类里写 `next.approve()`？** 每个子类都要判空，漏一个就 NPE；
  放到父类 `forward()` 里，兜底逻辑只写一次。
- **不给 `next` 会怎样？** 每个审批人都得自己维护「下一个是谁」，链就散了。

### 第三步：子类只回答「我办不办」

```java
class TeamLead extends Approver {
    void approve(String applicant, int amount) {
        if (amount <= 1000) {
            System.out.println(applicant + " 报销 " + amount + " 元 → 组长审批");
        } else {
            forward(applicant, amount);   // 办不了，往后传
        }
    }
}
```

经理、总监、副总的写法完全一样，只是阈值不同。
**每个类只知道自己的权限和「下一个是谁」，不知道整条链长什么样。**

### 最终对象关系

```text
调用方
  │ approve(30000)
  ▼
TeamLead ──next──▶ Manager ──next──▶ Director ──next──▶ VicePresident ──next──▶ null
  (≤1000)            (≤5000)           (≤20000)           (≤50000)          (兜底)
  办不了 → 后传      办不了 → 后传      办不了 → 后传       办不了 → 后传
```

---

## 5. 用具体数据走一遍

### 5.1 接线与调用

```java
Approver teamLead = new TeamLead();
Approver manager = new Manager();
Approver director = new Director();

teamLead.setNext(manager).setNext(director);

teamLead.approve("王五", 15000);
```

`setNext` 返回的是**下一个节点**，所以可以连续写：

```text
teamLead.setNext(manager)  → 返回 manager
manager.setNext(director)  → 返回 director
```

等价于：

```java
teamLead.setNext(manager);
manager.setNext(director);
```

### 5.2 15000 元怎么走

```text
① teamLead.approve(15000)
   15000 > 1000 → forward
② manager.approve(15000)
   15000 > 5000 → forward
③ director.approve(15000)
   15000 <= 20000 → 总监审批，停止
```

真实输出（节选）：

```text
王五 报销 15000 元 → 总监审批
```

### 5.3 新增副总之后

```java
Approver vicePresident = new VicePresident();
director.setNext(vicePresident);

teamLead.approve("孙七", 30000);
```

```text
TeamLead（15000 不够？30000 > 1000）→ Manager（30000 > 5000）
→ Director（30000 > 20000）→ VicePresident（30000 <= 50000，审批）
```

真实输出：

```text
孙七 报销 30000 元 → 副总审批
```

### 5.4 最容易写错的两处

1. **`setNext` 的返回值**：它返回 `next` 而不是 `this`。
   写成 `teamLead.setNext(manager).setNext(director)` 时，第二段其实是 `manager.setNext(director)`。
   如果你希望它返回 `this` 以便从链头继续接，语义就完全不同。
2. **链尾兜底**：`赵六 50000` 在只有三级时输出「无人可批，转总经理办公室」。
   如果 `forward()` 里不写 `else`，这个请求会被**静默丢弃**，日志里什么都看不到。

---

## 6. 改造前后对比

### 新增「副总审批」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `ExpenseService` | 新增 `VicePresident` |
| 要改的旧代码 | `approve()` 的 `else if` 链 | 接线处加一行 `director.setNext(vicePresident)` |
| 回归范围 | 所有金额档位 | 只需验证 20000～50000 这一档 |
| 复用 | 无法单独复用某一级 | 每个审批人都是独立对象，可单独测试 |

### 但调用方仍然要改

模式没有让「新增审批级别」变成零改动。调用方仍然要：

- 创建 `VicePresident`；
- 决定它接在谁后面；
- 如果顺序可配置，还要把这份配置读进来。

**责任链解决的是「节点怎么协作」，不是「节点从哪来、顺序谁决定」。**

### 引入模式新增的成本

- 类数量：每个审批级别一个类；
- 多了一层间接：排查一个请求要跟完整条链；
- 链的装配散在调用方，装配错了编译不会报错；
- 链尾必须有明确策略（驳回、转人工、抛异常），否则请求会消失。

---

## 7. 适用边界

### 适合用

- 请求需要**按顺序经过多个处理者**，且每个处理者只负责自己那段逻辑（审批、过滤、日志级别）；
- 处理者的**数量和顺序会变**，希望不改调用代码就能增删；
- 某个请求可能**没人处理**，需要显式的兜底策略；
- 每个处理者都希望**独立测试、独立复用**。

### 不适合用

- 只有两三个固定规则，顺序永远不变——一个 `if/else` 更短、更好读；
- 每个请求都必须且只能由一个固定对象处理——直接调用或策略模式更清楚；
- 处理者之间有复杂的状态共享——链会把状态散得到处都是。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 装饰器 | 装饰器**每一层都会执行**并增强结果；责任链**可能中途停止**，后面的节点不执行 |
| 策略 | 策略是**选一个**算法；责任链是**依次尝试多个**处理者 |
| 观察者 | 观察者是**一对多通知**，所有订阅者都会收到；责任链是**一对一接力**，有人处理就结束 |

> 一句话：**装饰器是「都做」，责任链是「谁能做谁做」，策略是「事先选一个做」。**

---

## 8. 小练习

### 练习 1（场景判断）

客服工单系统要求：普通客服先处理，处理不了升级到主管，主管处理不了升级到经理；VIP 工单直接进入经理队列。
要不要用责任链？说明理由。

**答案**：适合，但要注意两点。
「逐级升级」本身就是链式传递；「VIP 直接进入经理队列」可以通过**不同的链装配**实现
（VIP 链从经理开始），不需要在节点里写 `if (vip)`。
如果只有「普通 → 主管 → 经理」这一条永远不变的链，而且每一级都必须执行，那么一个数组加循环也够，
不一定非要为每一级建类。

### 练习 2（解释设计原因）

`Approver.forward()` 为什么放在抽象父类，而不是让每个子类自己写 `next.approve()`？
去掉它会怎样？

**答案**：`forward()` 统一了「有下一个就传，没有就兜底」这两件事。
如果每个子类自己写，就必须重复判空和兜底；漏写判空会 `NullPointerException`，
漏写兜底会让请求静默消失。放到父类后，子类只关心「我能不能批」。
去掉它，链仍然能跑，但每个子类都要复制同样的边界代码，维护成本更高。

### 练习 3（执行过程）

链的接线是 `teamLead → manager → director → vicePresident`。
调用 `teamLead.approve("周八", 18000)`，请求经过了哪些节点？最后谁处理？

**答案**：`TeamLead`（18000 > 1000，后传）→ `Manager`（18000 > 5000，后传）→
`Director`（18000 ≤ 20000，处理）。`VicePresident` 不会收到请求。
责任链的关键不是「走完整条链」，而是「遇到能处理的人就停」。

### 练习 4（扩展推导）

如果把 `Director` 接在 `Manager` 前面，`4000 元` 的报销会由谁处理？这说明什么？

**答案**：`Director`（4000 ≤ 20000）会直接处理，`Manager` 永远收不到。
这说明**链的顺序本身就是业务规则**，模式只提供传递机制，不保证顺序正确。
装配链的人必须对业务顺序负责。

---

## 读完后应能回答的 5 个问题

1. 责任链解决了「新增审批级别要改核心方法」的什么问题？
2. 为什么每个节点只需要知道「下一个是谁」，不需要知道整条链？
3. `forward()` 里的兜底分支去掉会发生什么？
4. 责任链和装饰器都会「包一层」，它们执行方式的根本区别是什么？
5. 什么情况下一个 `if/else` 比责任链更合适？

---

## 下一步

- 深度文档：[README.md](README.md)（审批链、折扣链、异常处理、链的装配）
- 工程版入口：[Client.java](Client.java)
- 两个子案例：[approval/](approval/)、[discount/](discount/)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/apigateway](../../combined/apigateway/)（请求过滤链）
