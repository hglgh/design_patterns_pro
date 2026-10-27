# 工厂方法模式：通俗入门

> **第一层「入门」材料**：先看清「创建代码散落」的代价，再理解为什么要让子类决定 new 谁。
> 简单工厂、工厂方法、抽象工厂的完整对比见 [README.md](README.md) 与
> [docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**工厂方法就是：需要什么对象，不在这里 new，而是交给一个「专门负责创建」的子类去 new。**

### 什么需求会让开发者想到它

一个系统要发通知：邮件、短信，以后还会有推送。第一版写在两个地方：

- 用户注册时发欢迎通知；
- 每天批量任务发日报通知。

两处都写了 `if ("email") ... else if ("sms") ...`。现在产品说要加「推送」，
开发者改了一处、忘了另一处，线上就出现了「批量任务不支持推送」的 bug。

这不是「if 写得不熟」，而是**创建逻辑被复制到了多个调用点**。
工厂方法要解决的是：把「创建哪个实现」这件事，收口到创建者自己的继承体系里。

---

## 2. 先看不使用工厂方法的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class NotificationService {
    void notify(String type, String to, String message) {
        if ("email".equals(type)) {
            System.out.println("  [邮件] " + to + "：" + message);
        } else if ("sms".equals(type)) {
            System.out.println("  [短信] " + to + "：" + message);
        } else {
            throw new IllegalArgumentException("不支持的渠道：" + type);
        }
    }
}

class BatchJob {
    void sendDaily() {
        String type = "email";
        // 同一个 if/else，又抄了一遍
        if ("email".equals(type)) { ... }
        else if ("sms".equals(type)) { ... }
    }
}
```

真实输出：

```text
  [邮件] u1@shop.com：订单已发货
  [短信] 138****0000：订单已发货
  [批量-邮件] ops@shop.com：日报已生成
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
NotificationService ──if/else──▶ 具体渠道（邮件/短信）
BatchJob            ──if/else──▶ 具体渠道（邮件/短信）
```

两个业务类都直接依赖「有哪些渠道、怎么创建」。渠道增加，它们都要改。

### 3.2 新增 push 要改几处

| 文件 | 改什么 | 漏改的后果 |
|---|---|---|
| `NotificationService` | 加一个 `else if ("push")` | 注册通知发不出去 |
| `BatchJob` | 再加一个 `else if ("push")` | 批量通知发不出去 |
| 测试 | 两处都要补用例 | 覆盖不全 |

### 3.3 为什么抽一个 `Sender` 接口还不够

如果只是把 `println` 换成 `Sender`，但仍在两个业务类里 `if/else` 决定 new 哪个实现，
那么**创建判断依旧存在两份**。真正要挪走的是「选择实现」这件事，而不只是「调用方式」。

---

## 4. 从问题推导出设计

### 第 1 步：先抽出产品接口

```java
interface Sender {
    void send(String to, String message);
}
```

**为什么？** 业务方法只想「发一条消息」，不关心是邮件还是短信。
**不加会怎样？** 每个业务方法都要知道自己拿的是 `EmailSender` 还是 `SmsSender`，
无法用同一段代码处理。

### 第 2 步：每个渠道一个实现

```java
class EmailSender implements Sender { ... }
class SmsSender implements Sender { ... }
```

**为什么？** 把「怎么发」关进各自的类，新增渠道时不动已有渠道。

### 第 3 步：把创建放进工厂

```java
abstract class SenderFactory {
    abstract Sender createSender();

    void send(String to, String message) {
        createSender().send(to, message);
    }
}
```

**为什么需要抽象工厂类？** 因为「创建哪个 Sender」这件事需要一个落点。
放在业务类里，就又回到 if/else；放在一个具体工厂里，新增渠道仍要改那个工厂。
**放在抽象方法里，让子类决定**，新增渠道就是新增子类，已有代码不动。

**为什么不加会怎样？** 业务方法只能自己 new，创建逻辑再次散落。

### 第 4 步：每个渠道一个工厂子类

```java
class EmailSenderFactory extends SenderFactory {
    Sender createSender() { return new EmailSender(); }
}

class SmsSenderFactory extends SenderFactory {
    Sender createSender() { return new SmsSender(); }
}
```

**为什么工厂也要有继承体系？** 因为业务方法只接收 `SenderFactory`，
调用 `factory.send(...)` 时，具体 new 谁由运行时传入的子类决定。
这就是「把 new 延迟到子类」。

最终关系：

```text
sendWelcome(SenderFactory, to)
        │
        ▼
SenderFactory.send() ──▶ createSender() ──▶ Sender.send()
        ▲                      │
        │                      ├── EmailSender
   EmailSenderFactory          └── SmsSender
   SmsSenderFactory
   PushSenderFactory（新增）
```

---

## 5. 用执行过程理解代码

`sendWelcome(new SmsSenderFactory(), "138****0000")`：

1. 创建 `SmsSenderFactory` 对象（此时还没有 Sender）；
2. 进入父类 `SenderFactory.send(...)`；
3. 调用 `createSender()`，动态分派到 `SmsSenderFactory` 的实现；
4. 返回 `new SmsSender()`；
5. 调用 `sender.send(...)`，输出短信。

真实输出（节选）：

```text
  [邮件] u1@shop.com：欢迎注册
  [短信] 138****0000：欢迎注册
  —— 新增 push 渠道：只加两个类，sendWelcome 不改 ——
  [推送] device-42：欢迎注册
```

### 最容易混淆的一行

```java
Sender sender = factory.createSender();
```

这行没有 `new`，但对象确实是新创建的——`new` 发生在具体工厂子类里。
**「看不到 new」不等于「没有创建」，而是「创建被推迟到运行时决定」。**

---

## 6. 改造前后对比

### 6.1 新增 push 渠道

| | 改造前 | 改造后 |
|---|---|---|
| 新增类 | 0（直接加分支） | `PushSender` + `PushSenderFactory` |
| 修改已有业务类 | 2 处 if/else | **0 处** |
| 修改已有工厂 | — | 0 处（新增子类） |
| 装配处 | 字符串 `"push"` | `new PushSenderFactory()` |
| 测试 | 两个业务类都要补 | 新工厂单独测 |

### 6.2 代价

- 每种渠道多一个工厂类，类数量翻倍；
- 「选哪个工厂」仍要在启动/装配处写一次（这行不会消失）；
- 如果渠道只有 2 种、且只在一个地方创建，一个简单工厂方法（甚至直接 new）更清楚。

### 6.3 什么时候不需要

- 对象创建很简单、没有多态需求 → 直接 `new`；
- 只有一个调用点、类型固定 → 一个简单工厂方法足够；
- 需要的是「一族产品一起创建」→ 那是抽象工厂，不是工厂方法。

---

## 7. 适用边界

### 适合

- 创建逻辑会在多个调用点重复；
- 具体类型要到运行时才能确定（配置、环境、插件）；
- 框架需要把「创建产品」这一步留给使用者扩展（模板方法里的 `createXxx()` 钩子）；
- 希望新增产品时**不改已有业务代码**。

### 不适合

- 产品只有一种、未来也看不到第二种；
- 创建逻辑简单到只有一行 `new`，且只出现在一个地方；
- 团队把工厂当成「所有 new 的集合」，结果工厂本身变成巨型 switch。

### 容易混淆

| 对比 | 区别 |
|---|---|
| **简单工厂** | 一个具体类里用 switch 创建所有产品；新增产品要改这个类，违反开闭原则，但结构简单 |
| **工厂方法** | 每个产品一个创建者子类；新增产品是新增子类，业务方法不变 |
| **抽象工厂** | 一个工厂创建**一族**产品（按钮 + 复选框）；换主题换整个工厂，保证产品配套 |
| **建造者** | 关注「一个复杂对象分几步组装」，不是「选哪个实现」 |

> 工厂方法最容易被过度使用的地方：把每个 `new` 都包一层工厂。
> 判断标准不是「有没有 new」，而是「创建逻辑是否重复、是否需要在运行时替换」。

---

## 8. 小练习

### 练习 1（场景判断）

一个项目里只有 `MySqlUserRepository` 一种实现，创建它的代码只出现在启动类的一行。
同事建议「为了符合开闭原则，加一个 `UserRepositoryFactory`」。**需要吗？**

**参考答案**：不需要。只有一种实现、一个创建点，工厂只是多一层跳转。
等出现第二种存储（比如测试用的内存实现），或创建逻辑（读配置、装配连接）变复杂时，
再引入工厂更合适。

### 练习 2（解释设计原因）

为什么 `SenderFactory.send()` 里要调用 `createSender()`，而不是直接 `new EmailSender()`？

**参考答案**：因为父类不应该知道具体产品。父类写死 `new EmailSender()`，就把创建逻辑
绑回了父类；子类也无法改变产品类型。调用抽象方法 `createSender()`，才会在运行时
分派到具体子类，实现「父类定流程，子类定产品」。

### 练习 3（扩展推导）

新增「推送」渠道，需要改哪些文件？`sendWelcome` 要不要改？

**参考答案**：新增 `PushSender` 和 `PushSenderFactory`；`sendWelcome` 的参数是
`SenderFactory`，不需要改；启动/装配处要决定何时传入 `PushSenderFactory`。
**新增的是类，不是已有方法的分支**，这就是工厂方法买到的扩展方式。

---

## 9. 读完之后

你应该能回答：

1. 工厂方法为什么存在？——把「创建哪个实现」从业务代码挪到创建者子类。
2. 原始实现的问题？——创建判断重复，新增渠道改多处、漏一处就出错。
3. 每个角色为什么存在？——`Sender` 统一使用方式，`SenderFactory` 提供创建入口，子类决定具体产品。
4. 怎么协作？——业务方法收工厂 → 调 `send` → 工厂子类 `createSender` → 产品执行。
5. 新场景要不要用？——看创建逻辑是否重复、是否需要运行时替换，而不是看有没有 `new`。

继续阅读：

- [深度文档](README.md)：简单工厂/工厂方法/抽象工厂的完整工程版；
- [Client.java](Client.java)：工程版入口；
- [抽象工厂入门](../abstractfactory/BEGINNER.md)：当需要「一族产品」时；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
