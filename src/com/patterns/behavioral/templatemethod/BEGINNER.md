# 模板方法模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么把流程锁在父类、只把步骤留给子类」，
> 以及什么时候继承反而会绑住你。
>
> 第二层：[README.md](README.md)（订单处理模板、钩子方法、预售/实物/虚拟商品）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**模板方法就是：流程的「顺序」由父类定死，流程里的「某几步怎么做」由子类填；**
**子类可以改细节，但不能把顺序改乱。**

### 什么需求会让开发者想到它

公司报销有一套固定流程：

1. 提交单据；
2. 主管/部门经理审批；
3. 财务复核；
4. 打款；
5. 短信通知。

差旅报销和招待报销的**步骤一样**，只是审批人、复核内容不同。
第一版很自然：两个类各自写一遍 `process()`，每步一行 `println`。

能跑，但流程顺序靠每个类自觉。招待报销就把「打款」写在了「财务复核」前面，
还漏了到账通知——**没有任何编译错误**。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldTravelReimbursement {
    void process(String id, int amount) {
        System.out.println("提交单据 " + id + "，金额 " + amount + " 元");
        System.out.println("主管审批通过");
        System.out.println("财务复核发票");
        System.out.println("打款到工资卡");
        System.out.println("短信通知已到账");
    }
}

class OldEntertainmentReimbursement {
    void process(String id, int amount) {
        System.out.println("提交单据 " + id + "，金额 " + amount + " 元");
        System.out.println("部门经理审批通过");
        System.out.println("打款到工资卡");          // 顺序反了
        System.out.println("财务复核招待标准");    // 复核在打款之后
    }
}
```

真实输出（节选）：

```text
差旅报销：
  提交单据 TRIP-01，金额 3800 元
  主管审批通过
  财务复核发票
  打款到工资卡
  短信通知已到账
招待报销：
  提交单据 ENT-02，金额 1200 元
  部门经理审批通过
  打款到工资卡
  财务复核招待标准
```

招待报销**先打款、后复核，而且没有通知**。
两份代码看起来都「能跑」，但流程已经不一致了。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldTravelReimbursement        ──自己实现──▶ 五步流程
OldEntertainmentReimbursement ──自己实现──▶ 五步流程（顺序不同）
```

这里没有共享的流程定义。每个类都拥有「完整流程」的知识，
**流程不是被复用的，而是被复制了两遍。**

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 在打款前加「预算校验」 | 两个类都要加，且位置要一致 | 漏一个就出现流程差异 |
| 通知改成企业微信 | 两个类都要改 | 复制粘贴的代码继续分叉 |
| 新增「采购报销」 | 再复制一遍五步 | 第三份副本 |
| 保证复核一定在打款前 | 靠代码评审/人眼 | 没有编译期或结构上的保证 |
| 只改差旅的复核内容 | 改 `OldTravel` | 可以，但流程仍然没有统一 |

### 3.3 为什么加一个 `if` 不够

因为问题不是「少一个步骤」，而是**流程的所有权不明确**。
只要每个子类都能自由排列五步，顺序就永远靠自觉；
你需要把「顺序」放到一个子类改不了的地方，把「差异」放到子类必须实现的地方。

---

## 4. 从问题推导出设计

### 第一步：把流程搬进父类，并锁死顺序

```java
abstract class Reimbursement {

    final void process(String id, int amount) {
        submit(id, amount);
        approve(id, amount);
        review(id, amount);
        pay(id, amount);
        notifyDone(id);
    }
}
```

- **为什么需要父类？** 五步顺序是两类报销的共同知识，应该只写一次。
- **为什么 `process` 是 `final`？** 子类不能重写它，也就不能打乱顺序。
- **如果不加 `final` 会怎样？** 子类仍然可以覆盖 `process()`，
  模板方法就退化成「一个可选的默认实现」。

### 第二步：把「共同的步骤」写成普通方法

```java
protected void submit(String id, int amount) {
    System.out.println("提交单据 " + id + "，金额 " + amount + " 元");
}

protected void notifyDone(String id) {
    System.out.println("短信通知已到账");
}
```

这两步所有报销都一样，放在父类，子类不用重复写。
如果某个子类要改通知方式，可以覆盖它——这叫**钩子（hook）**：
父类给默认实现，子类可选择性地改。

### 第三步：把「必须不同的步骤」写成抽象方法

```java
protected abstract void approve(String id, int amount);
protected abstract void review(String id, int amount);
protected abstract void pay(String id, int amount);
```

- **为什么用 `abstract`？** 父类不知道每种报销的审批人和复核内容，
  强制子类给出答案，避免漏步骤。
- **为什么不用空实现？** 空实现会让子类「忘了写」也能编译，
  流程看起来跑通，实际少了一步。

### 第四步：子类只填差异

```java
class TravelReimbursement extends Reimbursement {
    protected void approve(String id, int amount) {
        System.out.println("主管审批通过");
    }

    protected void review(String id, int amount) {
        System.out.println("财务复核发票");
    }

    protected void pay(String id, int amount) {
        System.out.println("打款到工资卡");
    }
}
```

招待报销同理，只改审批人（部门经理）和复核内容（招待标准）。
**子类不再拥有流程，只拥有步骤。**

### 最终对象关系

```text
Reimbursement（抽象父类）
  │ final process()：submit → approve → review → pay → notifyDone
  │ 共同步骤：submit()、notifyDone()
  │ 抽象步骤：approve()、review()、pay()
  ▲
  ├── TravelReimbursement
  └── EntertainmentReimbursement
```

---

## 5. 用具体数据走一遍

### 5.1 差旅报销 3800 元

```java
new TravelReimbursement().process("TRIP-01", 3800);
```

```text
① process() 被调用（final，不能改）
② submit("TRIP-01", 3800)     → 父类实现：提交单据
③ approve(...)                → 子类：主管审批通过
④ review(...)                 → 子类：财务复核发票
⑤ pay(...)                    → 子类：打款到工资卡
⑥ notifyDone(...)             → 父类实现：短信通知已到账
```

真实输出：

```text
差旅报销：
  提交单据 TRIP-01，金额 3800 元
  主管审批通过
  财务复核发票
  打款到工资卡
  短信通知已到账
```

### 5.2 招待报销 1200 元

```text
① process()
② submit → 提交单据
③ approve → 部门经理审批通过
④ review → 财务复核招待标准
⑤ pay → 打款到工资卡
⑥ notifyDone → 短信通知已到账
```

真实输出：

```text
招待报销：
  提交单据 ENT-02，金额 1200 元
  部门经理审批通过
  财务复核招待标准
  打款到工资卡
  短信通知已到账
```

两类的顺序现在完全一致，**因为顺序只有一份**。

### 5.3 最容易写错的三处

1. **`process()` 忘了 `final`**：子类可以重写，模板方法失去「锁顺序」的意义。
2. **抽象方法写成空实现**：子类漏写也能编译，流程静默少一步；
   必须不同的步骤要用 `abstract`。
3. **在父类构造器里调用抽象方法**：子类字段还没初始化，
   抽象方法可能读到 `null`/`0`。模板方法应在对象构造完成后调用。

---

## 6. 改造前后对比

### 新增「采购报销」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | 复制一份五步流程 | 新增 `PurchaseReimbursement` |
| 要改的旧代码 | 无，但多一份副本 | 无 |
| 顺序保证 | 靠人自觉 | 父类 `final process()` 保证 |
| 公共步骤修改 | 每个子类都要改 | 只改父类 |

### 但调用方仍然要改

- 调用方仍然要 `new` 具体的子类；
- 如果新增的是「流程不同」的报销（比如先审批后提交），
  模板方法就不适用了，不能硬塞；
- 子类仍然要理解每个抽象方法的语义和调用时机。

### 引入模式新增的成本

- **继承耦合**：子类被父类的流程绑死，父类改流程会影响所有子类；
- **脆弱基类**：父类新增一个抽象方法，所有子类都要实现；
- 钩子方法一多，父类会变成「半个业务类」；
- 流程需要运行时组合/调整时，继承不如组合灵活。

---

## 7. 适用边界

### 适合用

- 多个类有**相同的流程顺序**，只有部分步骤不同；
- 流程顺序是**业务硬约束**，必须防止子类改乱；
- 公共步骤可以复用，差异步骤可以明确枚举；
- 框架/父类希望控制生命周期，子类只填扩展点（如 `HttpServlet` 的 `doGet/doPost`）。

### 不适合用

- 每个子类的流程顺序本来就不同——继承会逼你写一堆空实现；
- 需要在运行时切换/组合步骤——策略/责任链/管道更合适；
- 步骤之间需要复杂的数据传递和返回值——模板方法的 `void` 钩子会不够用。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 策略 | 策略替换**整个算法**，用组合；模板方法固定**流程骨架**，用继承填步骤 |
| 工厂方法 | 工厂方法常作为模板方法中的一个步骤（创建对象）；模板方法关注整个流程 |
| 责任链 | 责任链把请求传给下一个节点，可能停止；模板方法在**一个对象内**按固定顺序执行 |

---

## 8. 小练习

### 练习 1（场景判断）

数据导入：CSV 和 JSON 都要「读取 → 校验 → 转换 → 入库 → 记录日志」，顺序固定。
要不要用模板方法？

**答案**：适合。五步顺序是共同的，读取/转换两步因格式不同；
把 `process()` 放父类，`read()`/`convert()` 设为抽象方法即可。
但「校验」和「入库」如果也需要按格式变化，可以做成钩子。
如果未来要支持「边读边入库」这种不同顺序，模板方法就不合适了。

### 练习 2（解释设计原因）

为什么 `process()` 要加 `final`？去掉会怎样？

**答案**：`final` 禁止子类重写流程，保证所有报销顺序一致。
去掉后，子类可以覆盖 `process()` 并打乱顺序，
父类的「流程模板」就只是建议，不是约束，改造前的问题会重新出现。

### 练习 3（执行过程）

`EntertainmentReimbursement` 没有重写 `submit()` 和 `notifyDone()`。
调用 `process("ENT-02", 1200)` 时，这两步执行的是谁的代码？

**答案**：执行父类 `Reimbursement` 的实现。
子类继承父类的普通方法；只有被 `abstract` 或显式 `@Override` 的步骤才走子类。
这正是模板方法复用公共步骤的方式。

### 练习 4（扩展推导）

只有「招待报销」需要在打款前多一步「部门总监会签」，其他报销不需要。
应该改 `process()` 吗？怎么设计？

**答案**：不应该改 `process()` 的固定五步，否则所有子类都被迫多一步。
可以加一个**钩子** `protected void extraCheck(...) {}`，默认空实现，
在 `process()` 的 `review` 之后、`pay` 之前调用；只有招待报销覆盖它。
钩子适合「可选步骤」，抽象方法适合「必填步骤」，两者不要混。

---

## 读完后应能回答的 5 个问题

1. 模板方法把「流程」和「步骤」分别放在哪里？为什么这样能保证顺序？
2. `final process()` 去掉后会发生什么？
3. 抽象方法和钩子方法各自适合什么步骤？
4. 模板方法和策略模式，一个用继承一个用组合，各自适合什么变化？
5. 什么情况下不应该用模板方法？

---

## 下一步

- 深度文档：[README.md](README.md)（订单处理模板、钩子、预售/实物/虚拟商品）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/orderservice](../../combined/orderservice/)（订单处理流程）
