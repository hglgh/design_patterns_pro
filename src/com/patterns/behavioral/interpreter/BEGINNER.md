# 解释器模式：通俗入门

> **这是第一层「入门」材料。** 目标：不背定义也能说清「为什么把规则表示成一棵树」，
> 以及什么时候几个 `Predicate` 就够了。
>
> 第二层：[README.md](README.md)（完整工程版：词法分析、语法分析、表达式求值）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

> 本入门示例只演示「表达式树怎么求值」，**不包含**从字符串解析出树的过程。
> 词法/语法分析是另一个独立问题，深度版才涉及。

---

## 1. 一句话说人话

**解释器模式就是把一条规则写成一棵树：叶子是「VIP」「金额」这样的条件，
枝干是「并且」「或者」「非」，求值就是让树从下往上算一遍。**

### 什么需求会让开发者想到它

电商的优惠资格规则一开始只有一条：

> VIP 且订单金额大于 100 元，才能领券。

运营用了两个月，提出新要求：

- 有时是「VIP 且金额大于 100」；
- 有时是「VIP 且金额大于 100，**或者**是新用户」；
- 下个月可能变成「（VIP 或新用户）且金额大于 100，且不在黑名单」。

如果每来一条规则就改一次 `if`，代码会变成运营需求的复制品。
解释器模式换了个思路：**把规则本身变成数据（一棵树），求值逻辑只写一次。**

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class DiscountRule {

    boolean isEligible(boolean vip, int amount, boolean newUser) {
        return vip && amount > 100;        // 新规则来了，只能改这一行
    }
}
```

调用：

```java
rule.isEligible(true, 150, false);   // true
rule.isEligible(true, 80, false);    // false
rule.isEligible(false, 50, true);    // false
```

真实输出：

```text
VIP / 150 元：true
VIP / 80 元：false
新用户 / 50 元：false
```

第三条明明是「新用户」，却因为方法里根本没用到 `newUser` 而返回 `false`。
这不是 bug，是**规则写死之后，参数只是摆设**。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
DiscountRule ──知道──▶ 所有变量（vip、amount、newUser）
             ──知道──▶ 它们怎么组合（&&、>、||）
```

规则方法同时知道「有哪些条件」和「条件怎么组合」。
调用方只能传三个固定参数，**无法表达「这次用哪条规则」**。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 「或新用户」 | 改 `isEligible()` 的返回表达式 | 改核心代码、重新编译、回归所有调用 |
| 「金额门槛从 100 改成 200」 | 改方法里的字面量 | 参数化不够，规则仍不可配置 |
| 不同活动用不同规则 | 再加一个方法或再加一个 `if` | 方法数量随规则数量增长 |
| 单独测试「金额 > 100」 | 做不到，它只是表达式的一半 | 条件无法独立复用 |
| 让运营在后台配规则 | 做不到，规则在 Java 代码里 | 每次改规则都要发版 |

### 3.3 为什么加一个 `if` 不够

因为规则不是「多一种情况」，而是**条件之间可以任意组合**。
你可以加一个 `|| newUser`，但下一条可能是 `&& !blacklist`，再下一条是嵌套括号。
用 `if` 表达组合，代码结构会跟着规则形状一起长；而规则形状是运营决定的，不该由代码结构承担。

---

## 4. 从问题推导出设计

### 第一步：让「每个条件」都能被求值

```java
interface Expression {
    boolean interpret(Context context);
}

class Flag implements Expression {
    private final String name;

    public boolean interpret(Context context) {
        return context.flag(name);
    }
}

class NumberGreaterThan implements Expression {
    private final String variable;
    private final int threshold;

    public boolean interpret(Context context) {
        return context.number(variable) > threshold;
    }
}
```

- **为什么需要 `Expression`？** 调用方要能统一地对「叶子」和「组合」求值。
- **为什么需要 `Context`？** 条件不应该自己去读全局变量；把本次求值的数据装进一个上下文传进去，
  同一个表达式就能对不同订单求值。
- **不加 `Context` 会怎样？** 每个条件都要持有订单引用，表达式和某一次具体数据绑死，无法复用。

### 第二步：让「并且/或者/非」也实现 `Expression`

```java
class And implements Expression {
    private final Expression left;
    private final Expression right;

    public boolean interpret(Context context) {
        return left.interpret(context) && right.interpret(context);
    }
}

class Or implements Expression {
    // 与 And 对称：left.interpret(context) || right.interpret(context)
}

class Not implements Expression {
    private final Expression inner;

    public boolean interpret(Context context) {
        return !inner.interpret(context);
    }
}
```

关键点：`And` 不关心 `left` 是 `Flag` 还是另一个 `And`，
它只知道「你也是 `Expression`，我调用你的 `interpret`」。
**树因此可以无限嵌套。**

### 第三步：组装规则

```java
Expression rule = new And(
        new Flag("vip"),
        new NumberGreaterThan("amount", 100));

Expression withNewUser = new Or(rule, new Flag("newUser"));
```

没有改 `And`、`Flag`、`NumberGreaterThan` 一行代码，只是换了一种组合方式。

### 最终对象关系

```text
Expression
├── Flag("vip")                    ← 叶子（终结符）
├── NumberGreaterThan("amount",100)← 叶子（终结符）
├── And(left, right)               ← 枝干（非终结符）
├── Or(left, right)                ← 枝干
└── Not(inner)                     ← 枝干

求值方向：叶子先算，枝干汇总，根节点给出最终 true/false
```

---

## 5. 用具体数据走一遍

### 5.1 规则 1：VIP 且金额 > 100

```java
Expression vipAndAmount = new And(
        new Flag("vip"),
        new NumberGreaterThan("amount", 100));

Context c1 = new Context();
c1.setNumber("amount", 150);
c1.setFlag("vip", true);

vipAndAmount.interpret(c1);
```

执行顺序：

```text
① And.interpret(c1)
② ├─ Flag("vip").interpret(c1)              → true
③ └─ NumberGreaterThan("amount",100)
        context.number("amount") = 150
        150 > 100                             → true
④ true && true                                → true
```

`Context` 里存的是：`numbers = {amount=150}`，`flags = {vip}`。

### 5.2 换一份数据，同一棵树给出不同结果

```java
Context c2 = new Context();
c2.setNumber("amount", 80);
c2.setFlag("vip", true);
```

```text
Flag("vip")            → true
NumberGreaterThan      → 80 > 100 = false
true && false          → false
```

**表达式树本身没有变，变的只是 `Context`。** 这就是把「规则」和「数据」分开的价值。

### 5.3 新规则：或者新用户

```java
Expression vipOrNewUser = new Or(vipAndAmount, new Flag("newUser"));

Context c3 = new Context();
c3.setNumber("amount", 50);
c3.setFlag("newUser", true);

vipOrNewUser.interpret(c3);   // true
```

执行：

```text
Or.interpret(c3)
├─ vipAndAmount.interpret(c3)
│   ├─ Flag("vip")            → false（c3 没设 vip）
│   └─ NumberGreaterThan      → 50 > 100 = false
│   → false
└─ Flag("newUser")            → true
false || true                 → true
```

### 5.4 最容易写错的两处

1. **`Context.flag()` 和 `Context.number()` 的行为不对称**：
   没设置的 flag 返回 `false`，没设置的 number 直接抛 `IllegalArgumentException`。
   前者适合「可选标记」，后者是为了避免「金额没传却按 0 算」的静默错误。
2. **`And`/`Or` 的短路**：Java 的 `&&`/`||` 会短路，右边可能不执行。
   如果某个条件带副作用（计数、远程查询），它可能被跳过。规则求值应尽量保持无副作用。

---

## 6. 改造前后对比

### 新增「或新用户」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `DiscountRule` | 不改已有表达式类 |
| 要改的代码 | 改返回表达式、重新编译 | 调用方用 `new Or(...)` 重新组装 |
| 规则能否运行时变化 | 不能 | 可以把「用哪些节点、怎么组合」存成配置 |
| 单条件测试 | 做不到 | 每个 `Expression` 可单独测试 |

### 但调用方仍然要改

- 新增一种**运算符**（比如 `Xor`）仍要新增类；
- 如果规则来自字符串/后台配置，还需要词法分析、语法分析和校验；
- 规则变了，调用方要重新组装树，或者从配置里读树。

**解释器解决的是「规则如何组合与求值」，不是「规则从哪来」。**

### 引入模式新增的成本

- 每条语法一个类，运算符一多类就多；
- 树是嵌套结构，调试时不如一行 `if` 直观；
- 需要额外的解析器才能让非程序员配置规则；
- 如果只是两三条固定规则，`Predicate<Context>` 的组合（`and`/`or`/`negate`）更轻。

> 这一点必须诚实：**Java 8 的 `Predicate` 已经能组合条件。**
> 解释器真正划算的场景是：你需要一棵**可检查、可序列化、可解析**的树，
> 而不只是「把几个条件拼起来」。

---

## 7. 适用边界

### 适合用

- 规则由**少量运算符 + 多个条件**组合而成，且组合方式会变；
- 需要把规则**存成配置/DSL**，运行时读取；
- 需要在求值前**检查、优化、展示**这棵树（比如告诉运营「这条规则用了哪些条件」）；
- 同一套语法需要多种求值方式（布尔判断、打分、生成 SQL）。

### 不适合用

- 只有两三条固定规则，`if` 或几个方法更直接；
- 规则主要是「查表」「阈值判断」，没有组合结构；
- 团队里没人维护解析器，却硬要自造 DSL——用成熟规则引擎更稳。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 策略 | 策略是**选一个完整算法**；解释器是**按树结构组合多个小表达式** |
| 访问者 | 访问者把「操作」外置到元素之外；解释器把「语法」表示成对象树，求值仍在节点里 |
| 组合 | 组合关注「部分-整体」的树形结构；解释器关注「这棵树如何被解释/求值」 |
| `Predicate` 组合 | 更轻，但只能求值，不能检查/序列化/解析整棵树 |

---

## 8. 小练习

### 练习 1（场景判断）

运费规则：「订单金额大于 200，或者（VIP 且重量小于 5kg）」。
要不要用解释器？说明理由。

**答案**：如果这条规则**固定不变**，一个方法就够，不必上解释器。
如果规则会由运营配置、还会继续嵌套（比如再加「偏远地区除外」），
那么它确实是「条件 + 与或非」的组合，适合表示成表达式树。
判断标准不是「有没有与或非」，而是**组合结构是否会变、是否需要配置化**。

### 练习 2（解释设计原因）

为什么每个表达式都接收一个 `Context`，而不是直接读 `DiscountRule` 里的字段？
去掉 `Context` 会怎样？

**答案**：`Context` 把「这次求值的数据」和「规则结构」分开。
同一棵树可以用于不同订单、不同时间；表达式对象可以做成无状态、可复用。
去掉 `Context`，每个表达式就要持有具体订单，规则树和一次请求绑死，
无法缓存、无法并发复用，测试也要构造完整订单。

### 练习 3（执行过程）

规则是 `new Not(new Flag("vip"))`，`Context` 里设置了 `vip=true`。
求值过程是什么？结果是多少？

**答案**：`Not.interpret` 调用 `inner.interpret(context)`，即 `Flag("vip")` 返回 `true`，
`Not` 取反得到 `false`。`Not` 不需要知道 `inner` 是 `Flag` 还是 `And`，
它只依赖 `Expression` 接口——这就是组合能无限嵌套的原因。

### 练习 4（边界推导）

如果规则字符串是 `"vip AND amount > 100"`，本入门示例能直接处理吗？还缺什么？

**答案**：不能。本示例只接受**已经组装好的 Java 对象树**。
要处理字符串，还需要：词法分析（切出 `vip`、`AND`、`amount`、`>`、`100`）、
语法分析（按优先级构造 `And`/`NumberGreaterThan`）、变量校验（`amount` 是否已定义）。
这正是深度版 [README.md](README.md) 里的 `Lexer` 和 `Parser` 做的事。

---

## 读完后应能回答的 5 个问题

1. 解释器把「规则」表示成了什么？为什么这样能支持任意组合？
2. `Context` 解决了什么问题？没有它，表达式对象会受什么限制？
3. 为什么说「Java 的 `Predicate` 组合」有时比解释器更合适？
4. 新增一种运算符和新增一条规则，改动范围有什么不同？
5. 从字符串到表达式树，还缺哪两个关键步骤？

---

## 下一步

- 深度文档：[README.md](README.md)（Lexer、Parser、Value、异常体系）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
