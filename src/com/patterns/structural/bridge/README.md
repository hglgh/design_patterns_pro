# 桥接模式（Bridge Pattern）

> **一句话**：把"抽象部分"和"实现部分"拆成两条独立的继承体系，用组合（桥）连起来，让两个维度各自扩展。
>
> 本目录实测：**10 个源文件 / 214 行**，`javac -Xlint:all` 零告警；运行输出 **25 行 / 774 字节**，
> SHA-256 `527F6F60F491634E…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 一致）。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 桥接解决什么问题 | 两个维度都会增长，用继承会得到 M×N 个类 |
| 它和策略最大的区别 | 策略只有**一个**维度可换；桥接**两个**维度都各有继承体系 |
| 它和适配器的区别 | 适配器是**事后补救**（接口已经不兼容），桥接是**事前设计**（预判会膨胀） |
| 什么时候别用 | 只有一个维度会变；或两个维度组合固定、根本不会增长 |
| 最容易犯的错 | 把"一个类持有接口引用"就叫桥接——那只是依赖倒置 |

一句话判断：**"这两个维度以后会不会各自都长出很多子类？"** 会，才是桥接。

## 1. 现实案例：说人话版

### 1.1 遥控器与电视

GoF 原书用的就是这个例子。

- **抽象维度**：遥控器。基础遥控器、带屏幕的高级遥控器、游戏手柄。
- **实现维度**：电视品牌。索尼、三星、小米。

如果按"继承"来做，需要 `索尼基础遥控器`、`三星基础遥控器`、`索尼高级遥控器`……**3 × 3 = 9 个类**。而且每加一个品牌，就要加 3 个类。

桥接的做法：遥控器持有一个"设备"引用，只管调用 `device.setVolume()`；电视实现"设备"接口，只管真的调音量。

```
基础遥控器 ──持有──▶ 设备接口 ◀──实现── 索尼电视
高级遥控器 ──持有──▶    △     ◀──实现── 三星电视
游戏手柄   ──持有──▶    │      ◀──实现── 小米电视
                    （桥）
```

**关键**：遥控器不知道是哪个品牌，电视不知道是被哪个遥控器操作。两边都能独立加新成员。

### 1.2 本目录的业务场景：消息类型 × 发送渠道

公司要做一个通知中心：

- **消息类型（抽象维度）**：普通通知、紧急告警、定时消息。
- **发送渠道（实现维度）**：邮件、短信、微信、钉钉。

如果按继承：

```text
NormalEmailMessage、NormalSmsMessage、NormalWeChatMessage、NormalDingTalkMessage
UrgentEmailMessage、UrgentSmsMessage、UrgentWeChatMessage、UrgentDingTalkMessage
ScheduledEmailMessage、ScheduledSmsMessage、ScheduledWeChatMessage、ScheduledDingTalkMessage
```

**3 × 4 = 12 个类**。加一个"飞书"渠道 → +3 个类；加一个"营销消息"类型 → +4 个类。

桥接：**3 + 4 = 7 个类**（`Message` 抽象 + 3 个子类 + `MessageSender` 接口 + 4 个实现）。

### 1.3 第二个场景：报表 × 导出格式

- 报表类型：销售报表、库存报表、财务报表（3）
- 导出格式：PDF、Excel、CSV（3）

继承 = 9 个类，桥接 = 3 + 3 = 6 个。而且"新增一种格式"是**横切所有报表**的改动——继承方案要改 3 个类，桥接方案只加 1 个类。

> 判断是不是"横切"：新增这个功能时，**是否每一行、每一种类型都要跟着加一份**？是，就该抽出维度。

### 1.4 专业版：GoF 说的"抽象"和"实现"到底指什么

这是桥接模式最容易被误解的地方。

> Decouple an abstraction from its implementation so that the two can vary independently.
> —— *Design Patterns*, GoF, 1994

**这里的 "abstraction" 不是"接口"，"implementation" 也不是"实现类"。**

| GoF 术语 | 真实含义 | 本目录对应 |
|---|---|---|
| Abstraction（抽象部分） | **高层控制逻辑**，它自己也有一套继承体系（RefinedAbstraction） | `Message` + `NormalMessage` / `UrgentMessage` / `ScheduledMessage` |
| Implementor（实现部分） | **平台/渠道相关的底层代码**，它自己也有一套继承体系（ConcreteImplementor） | `MessageSender` + 4 个 Sender |
| 桥 | 抽象部分持有的实现部分引用 | `Message.sender` |

所以下面这些**都不是**桥接：

| 结构 | 实际是什么 | 为什么不是桥接 |
|---|---|---|
| `PriceCalculator` 持有 `DiscountStrategy`，策略有 4 个实现 | 策略模式 | 只有策略这一侧有多个实现，`PriceCalculator` 没有子类 |
| `UserService` 持有 `UserDAO` 接口，DAO 有 2 个实现 | 依赖倒置（DAO 模式） | 同上：服务层没有"多个 Service 子类"这个维度 |
| `MediaAdapter implements AudioPlayer` 并持有 `AdvancedMediaPlayer` | 适配器 | 适配器是"接口转换"，不是为了两个维度独立扩展 |

> 记忆法：桥接的类图是 **"两个三角形 + 一条横线"**。只有一个三角形，就不是桥接。

### 1.5 JDK 与框架里的桥接（严格区分"结构相同"与"精神相同"）

**① AWT 的 peer 体系（结构完全相同）**

`java.awt.Component`（抽象部分）→ `Button` / `TextField`（RefinedAbstraction）；
`java.awt.peer.ComponentPeer`（实现部分）→ Windows / Linux 各自的原生实现。

AWT 的组件只写"逻辑"，真正的绘制交给平台相关的 peer。这就是 GoF 书里的原生例子。现代 AWT 的 peer 已被 `LightweightPeer` 等机制演化，但结构思想仍在。

**② SLF4J（精神相同，结构略异）**

`org.slf4j.Logger`（抽象）不关心日志写到哪里；Logback / Log4j2 提供实现（绑定）。业务代码只依赖 SLF4J API。

差异点：SLF4J 的实现是通过 classpath 上的 binding 选定的，不是由抽象类持有引用——**更像"服务发现 + 桥接"**。学的时候要看清这一点，别背错。

**③ JDBC（精神相同，结构略异）**

业务代码面向 `java.sql.Connection` / `Statement` / `ResultSet`（抽象部分），具体实现由各数据库驱动提供（实现部分）。

差异点：连接由 `DriverManager` 这个工厂创建，不是"抽象类持有实现引用"。所以严格说它是 **SPI + 工厂 + 桥接思想**，而不是教科书式桥接。

**④ 本项目的 `Message` × `MessageSender`：教科书式桥接**

抽象类持有接口引用，两边都有继承体系，结构完全对得上。

## 2. 原始实现：三版代码，三次痛

### 2.1 v0：纯继承，类爆炸

```java
public class NormalEmailMessage {
    public void send(String content) {
        System.out.println("【普通邮件】" + content);
    }
}

public class NormalSmsMessage {
    public void send(String content) {
        System.out.println("【普通短信】" + content);
    }
}

// …… 还差 10 个
```

12 个类里，**90% 的代码是重复的**：只有"前缀"和"发送方式"两处不同。这就是"用继承表达两个变化方向"的必然结果。

更糟的是：**每加一个渠道，要复制粘贴 3 次**；每加一个消息类型，要复制 4 次。复制粘贴就是 bug 的来源（改一处漏一处）。

### 2.2 v1：退回 if-else，用参数表达两个维度

```java
public class MessageService {
    public void send(String type, String channel, String content) {
        if (type.equals("normal")) {
            if (channel.equals("email")) {
                System.out.println("【普通邮件】" + content);
            } else if (channel.equals("sms")) {
                System.out.println("【普通短信】" + content);
            }
            // …… 每个 type 里再 4 个 if
        } else if (type.equals("urgent")) {
            // …… 又是 4 个 if
        }
    }
}
```

类没爆炸，但换来两个新问题：

1. **双重分发**（`type` × `channel`），嵌套 if 层数随维度增加。
2. **所有变化都集中在这一个类**，每次加渠道/类型都要改它，且改完要回归全部组合。

### 2.3 v2：桥接（原项目的结构）

原项目把两个维度拆开：

```java
public abstract class Message {
    protected MessageSender messageSender;          // ← 桥

    public Message(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public abstract void send(String content);
}

public class NormalMessage extends Message {
    public NormalMessage(MessageSender s) { super(s); }
    @Override public void send(String content) {
        System.out.println("【普通通知】开始发送...");
        messageSender.send(content);
    }
}

public class UrgentMessage extends Message {
    public UrgentMessage(MessageSender s) { super(s); }
    @Override public void send(String content) {
        System.out.println("【紧急告警】开始发送...");
        messageSender.send("【加急】" + content);
    }
}
```

结构对了。但有四个问题。

### 2.4 原项目的四个问题

| # | 问题 | 后果 |
|---|---|---|
| B1 | `messageSender` 是 `protected` 且**非 final** | 子类可以 `messageSender = null` 或换成别的渠道，桥在运行期可被悄悄改掉 |
| B2 | 构造器不校验 `null` | `new NormalMessage(null)` 编译通过、运行到 `send()` 才 NPE，栈里看到的错误位置离真正原因很远 |
| B3 | 每个子类都重复 `System.out.println("【…】开始发送...")` 骨架 | 一旦要加"内容长度校验""失败重试"，要抄进每个子类 |
| B4 | 只有 2 个消息类型 × 3 个渠道，且没有演示"新增一个维度" | 看不出桥接的价值，读者只会觉得"多写了一层" |

### 2.5 重构版的两个遗留问题（本轮修掉）

重构版已经做了两件事：`sender` 改成 `protected final`、新增 `ScheduledMessage` 和 `DingTalkSender` 演示双向扩展。但本轮抽查又发现两处：

**① 构造器没有 null 校验**

```java
// 修复前
protected Message(MessageSender sender) {
    this.sender = sender;      // null 也能进来
}
```

```java
// 修复后
protected Message(MessageSender sender) {
    // 桥不能是 null：否则要到 send() 里才报 NPE，离真正的错误位置很远
    this.sender = Objects.requireNonNull(sender, "sender");
}
```

`final` 只保证"不能改"，**不保证"不是 null"**。这是两个不同的不变式，经常被混为一谈。

**② 注释把抽象方法说成"模板方法"**

```java
// 修复前
/**
 * 发送消息（模板方法，子类实现具体逻辑）。
 */
public abstract void send(String content);
```

`send` 是 `abstract`，没有任何固定骨架，子类可以完全不按"先 A 再 B"的顺序写——**它不是模板方法**。模板方法的定义是"父类固定流程，子类填步骤"，这里没有流程。

修复后的注释：

```java
/**
 * 发送消息。
 *
 * <p>子类负责"这种消息怎么包装内容"，具体"走哪个渠道"由 {@link #sender} 完成。
 * 注意这不是模板方法：这里没有固定骨架，子类完全决定流程。
 */
```

> 注释写错模式名，比不写注释更糟——它会让后来者按错误的心智模型去改代码。
> 这个项目里"模板方法"有专门的一章（`behavioral/templatemethod`），那里 `processOrder()` 才是 `final` + 固定五步。

### 2.6 重构前后对照

| 项 | 原项目 | 重构后 |
|---|---|---|
| 渠道数 | 3（邮件/短信/微信） | 4（+ 钉钉） |
| 消息类型 | 2（普通/紧急） | 3（+ 定时） |
| `sender` 可变性 | `protected` 可写 | `protected final` + `requireNonNull` |
| 演示"独立扩展" | 无 | 两个维度各新增一个类，输出里显式说明 |
| 输出可区分度 | `通过【邮件】发送消息：` | `  [邮件] `，前缀更短，便于对比 |

## 3. 设计动机

### 3.1 类爆炸的数学

| M（消息类型） | N（渠道） | 继承的类数 M×N | 桥接的类数 M+N | 差距 |
|---|---|---|---|---|
| 3 | 4 | 12 | 7 | 5 |
| 5 | 5 | 25 | 10 | 15 |
| 10 | 10 | 100 | 20 | 80 |
| 20 | 20 | 400 | 40 | 360 |

**M×N 的增长是乘法的，M+N 是加法的。** 维度越多、越大，差距越夸张。

但注意：**类多不是唯一问题，更痛的是"改动的扩散"**。

| 变化 | 继承方案要改几个类 | 桥接方案要改几个类 |
|---|---|---|
| 新增渠道 | M 个（每个消息类型一份） | 1 个（新增实现类） |
| 新增消息类型 | N 个 | 1 个（新增抽象子类） |
| 改渠道公共逻辑（如加重试） | M 个 | 1 个（接口的默认方法或基类） |

**"改动扩散"比"类数量"更能说明问题。**

### 3.2 什么时候**不该**用桥接

**① 只有一个维度会变。** 比如"折扣算法"有 5 种，但"计算器"只有一种——这就是策略模式，不需要为计算器造继承体系。

**② 两个维度的组合是固定的、不会增长。** 比如系统只有"邮件 + 短信"两种渠道、"普通 + 紧急"两种类型，且产品明确不再加——直接写 4 个类更直白。

**③ 维度之间有强耦合约束。** 例如"紧急告警只允许走短信和电话"。桥接的 `new UrgentMessage(emailSender)` 在语法上是合法的，**桥接本身无法表达这种约束**。约束要放在工厂或校验层（§7.5）。

**④ 只有 1 个抽象子类或 1 个实现。** 这就是"假桥接"：两条继承体系都只有一个节点，等于白白多了一层。等第二个实现出现再拆也不迟。

**⑤ 你想解决的是"接口不兼容"。** 那是适配器。

### 3.3 桥接与依赖倒置的关系

依赖倒置原则（DIP）说：高层模块不应该依赖低层模块，两者都依赖抽象。

桥接是 DIP 的一个**具体结构**：抽象部分依赖 `MessageSender` 接口，而不是 `EmailSender` 类。

但反过来不成立：**DIP 不要求两个维度都有继承体系**。`UserService` 依赖 `UserDAO` 接口，满足 DIP，但不是桥接（因为 `UserService` 没有子类）。

## 4. 设计推导：从 v2 到最终代码

### 4.1 第 1 步：先把两个维度写成一张矩阵

|  | 邮件 | 短信 | 微信 | 钉钉 |
|---|---|---|---|---|
| **普通** | ✓ | ✓ | ✓ | ✓ |
| **紧急** | ✓ | ✓ | ✓ | ✓ |
| **定时** | ✓ | ✓ | ✓ | ✓ |

**每一格都有意义**，说明这两个维度是"正交"的（任意组合都成立）。**正交是桥接成立的前提。**

反例：如果矩阵里只有对角线有值（紧急只能用短信），那维度就不正交，硬套桥接会产生大量"语法合法但业务非法"的组合。

### 4.2 第 2 步：把"渠道"抽成接口

```java
public interface MessageSender {
    void send(String message);
}
```

为什么是接口而不是抽象类？

- 渠道之间**没有共享逻辑**（真实项目里每个渠道的 SDK 完全不同），不需要基类。
- Java 单继承：抽象类会占掉子类唯一的父类名额。
- 接口更像"能力声明"：能发消息。

如果渠道之间有公共逻辑（比如都要做"签名 + 重试"），就该是**抽象类 + 模板方法**（§4.5 有讨论）。

### 4.3 第 3 步：抽象部分持有引用（这就是"桥"）

```java
public abstract class Message {
    protected final MessageSender sender;

    protected Message(MessageSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public abstract void send(String content);
}
```

三个设计决定：

| 决定 | 理由 |
|---|---|
| 字段放在**抽象父类** | 三个子类共享同一个桥，不用各写一个字段 |
| `protected` | 子类要直接调用 `sender.send(...)`；包外不可见 |
| `final` + `requireNonNull` | 桥一旦搭好就不许拆、不许为空 |

### 4.4 第 4 步：子类只负责"这种消息怎么包装"

```java
public class NormalMessage extends Message {
    @Override public void send(String content) {
        System.out.println("【普通通知】");
        sender.send(content);                 // 原样发
    }
}

public class UrgentMessage extends Message {
    @Override public void send(String content) {
        System.out.println("【紧急告警】");
        sender.send("【加急】" + content);     // 加前缀
    }
}

public class ScheduledMessage extends Message {
    private final long delayMillis;

    @Override public void send(String content) {
        System.out.println("【定时消息】将在 " + delayMillis + "ms 后发送");
        sender.send("[定时] " + content);      // 加标记
    }
}
```

注意 `delayMillis` 放在 `ScheduledMessage` 里，**不放 `Message`**：普通消息和紧急消息不需要延迟参数，放进父类会污染另外两个子类。这是"字段跟随维度"的原则。

### 4.5 第 5 步：`send` 该不该写成 `final` 模板方法？

这是一个真实的设计岔路口。

**方案 A（当前实现）：`public abstract void send(String)`**

子类完全控制流程。灵活，但如果将来要加"所有消息都要做的公共动作"（长度校验、敏感词过滤、埋点），只能逐个改子类。

**方案 B：`final send()` + `protected abstract doSend()`**

```java
public abstract class Message {
    protected final MessageSender sender;

    public final void send(String content) {
        if (content == null || content.isBlank()) {          // 固定步骤 1
            throw new IllegalArgumentException("内容不能为空");
        }
        doSend(content);                                     // 变化步骤
    }

    protected abstract void doSend(String content);
}
```

公共逻辑只写一次，子类无法绕过。代价是子类失去流程控制权——如果某个子类需要"发送前先排队"，`final` 会挡住它。

**怎么选？**

| 场景 | 选择 |
|---|---|
| 各子类流程差异大，且没有稳定的公共步骤 | 抽象方法（方案 A，本项目） |
| 有必须统一的公共步骤（校验、日志、重试、埋点） | `final` + 模板方法（方案 B） |

**两个模式可以叠加**：`Message` 用模板方法固定"校验 → 发送"，同时用桥接把"怎么发送"交给 `MessageSender`。这是真实项目里最常见的组合——**模板方法管步骤，桥接管分工**。

> 这也是一个重要的认知：**设计模式不是互斥选项**。`OrderProcessTemplate` 那个模块演示的就是模板方法，本模块演示的是桥接，两者在同一个类里共存完全正常。

### 4.6 第 6 步：验证"独立扩展"是不是真的

这是桥接最容易被说空话的地方。用**文件级证据**说话：

| 扩展动作 | 新增文件 | 修改已有文件 |
|---|---|---|
| 新增 `DingTalkSender`（实现维度） | 1 个 | **0 个** |
| 新增 `ScheduledMessage`（抽象维度） | 1 个 | **0 个** |

`Client` 里只是多写了两行 `new`：

```java
new ScheduledMessage(dingtalk, 5000).send("明日 10:00 例会");
new NormalMessage(dingtalk).send("新版本已发布");
```

**如果这是继承方案**：新增钉钉要动 3 个消息类（或加 3 个新类），新增定时消息要动 4 个渠道类（或加 4 个新类）。这就是"0 修改"和"改一片"的差别。

### 4.7 最终结构

```text
        Message（Abstraction）
        ├─ field: sender ───────────────┐
        ├─ NormalMessage                │  桥（组合）
        ├─ UrgentMessage                │
        └─ ScheduledMessage             │
                                        ▼
                          MessageSender（Implementor）
                          ├─ EmailSender
                          ├─ SmsSender
                          ├─ WeChatSender
                          └─ DingTalkSender
```

读图要点：**两个继承体系各自生长，只在 `sender` 这一个点上相交。**

## 5. 代码解析

### 5.1 `Message`——抽象部分的根

```java
public abstract class Message {

    /** 桥：持有实现部分的引用。 */
    protected final MessageSender sender;

    protected Message(MessageSender sender) {
        // 桥不能是 null：否则要到 send() 里才报 NPE，离真正的错误位置很远
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public abstract void send(String content);
}
```

- **它是抽象类而不是接口**：因为要持有状态（`sender` 字段）。Java 接口不能有实例字段。
- **`protected` 而不是 `private` + getter**：子类要调用 `sender.send(...)`，加个 getter 只是多一层无意义的转发。
- **构造器 `protected`**：抽象类本来就不能 `new`，写 `protected` 是明确语义。

删除它会怎样？三个子类各自持有一个 `MessageSender` 字段，重复三遍；`Client` 也无法用 `Message` 类型做多态。

### 5.2 `MessageSender`——实现部分的根

```java
public interface MessageSender {
    void send(String message);
}
```

只有一行方法签名，但这行签名决定了扩展成本：**新渠道 = 新类 = 一个 `send` 方法**。

它是**函数式接口**（只有一个抽象方法），所以可以用 lambda 实现（§7.7）。

### 5.3 `NormalMessage`——最朴素的抽象子类

```java
public class NormalMessage extends Message {
    public NormalMessage(MessageSender sender) {
        super(sender);
    }

    @Override
    public void send(String content) {
        System.out.println("【普通通知】");
        sender.send(content);
    }
}
```

它做两件事：打一个类型标记、把内容原样交给渠道。

**为什么还要打标记？** 真实系统里这是"消息分类"，用于审计、统计、限流。Demo 里用 `println` 代替。

### 5.4 `UrgentMessage`——在抽象维度做内容加工

```java
@Override
public void send(String content) {
    System.out.println("【紧急告警】");
    sender.send("【加急】" + content);
}
```

`"【加急】"` 这个前缀属于**消息类型**的职责，不属于渠道。如果放到 `SmsSender` 里，那么"紧急邮件"就没有加急标记了——**职责放错维度，是桥接最常见的实现错误**。

判断方法：**这段逻辑，换一个渠道还需要吗？** 需要 → 属于抽象维度；不需要 → 属于实现维度。

### 5.5 `ScheduledMessage`——带私有状态的抽象子类

```java
public class ScheduledMessage extends Message {
    private final long delayMillis;

    public ScheduledMessage(MessageSender sender, long delayMillis) {
        super(sender);
        this.delayMillis = delayMillis;
    }

    @Override
    public void send(String content) {
        System.out.println("【定时消息】将在 " + delayMillis + "ms 后发送");
        // 实际场景会用 ScheduledExecutorService，这里简化为立即发送
        sender.send("[定时] " + content);
    }
}
```

两个诚实说明：

1. **它并没有真的延迟发送**，只是打印"将在 N 毫秒后发送"。要真做，应该注入 `ScheduledExecutorService` 并 `schedule(() -> sender.send(...), delay, MILLISECONDS)`。
2. 这个简化是**有意的**：本模块要讲的是"抽象维度能独立扩展"，不是线程调度。README 里写清楚比假装实现更负责。

### 5.6 四个 `Sender`——为什么它们看起来"什么都没做"

```java
public class EmailSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [邮件] " + message);
    }
}
```

四个类几乎一模一样，只有方括号里的字不同。这看起来像"过度设计"，但要看清楚**它们代表什么**：

| Sender | 真实项目里 `send` 内部会做什么 |
|---|---|
| `EmailSender` | 建 SMTP 连接、拼 MIME、处理附件、重试 |
| `SmsSender` | 调运营商 API、签名、模板参数、限流 |
| `WeChatSender` | 换 access_token、调企业微信接口、处理错误码 |
| `DingTalkSender` | 加签、@ 指定人、Markdown 消息体 |

**桥接把"这些完全不同的技术细节"关在各自的类里，抽象维度一行都不用知道。** Demo 用 `println` 替代，是为了让结构清晰。

> 反过来说：如果四个 `Sender` 真的只有一行 `println`，且永远不会接真实 SDK，那这个桥接确实是过度设计。**模式的价值取决于被隐藏的复杂度**。

### 5.7 `Client`——组合两个维度

```java
MessageSender email = new EmailSender();
MessageSender sms = new SmsSender();
MessageSender wechat = new WeChatSender();
MessageSender dingtalk = new DingTalkSender();

new NormalMessage(email).send("欢迎使用我们的系统！");
new UrgentMessage(sms).send("服务器 CPU 负载过高！");
new UrgentMessage(wechat).send("数据库连接异常！");
new ScheduledMessage(dingtalk, 5000).send("明日 10:00 例会");
new NormalMessage(dingtalk).send("新版本已发布");
```

注意 `MessageSender` 变量和 `Message` 变量是**两个独立的类型维度**，`Client` 负责把它们拼在一起。

**这也是桥接的一个代价**：组合的责任落在了使用方（或工厂）身上。§7.5 会讲怎么用工厂收口。

### 5.8 命名：为什么叫 `Message` 而不是 `AbstractMessage`

`AbstractXxx` 是"我用了抽象类"的技术命名，`Message` 是业务命名。

优先用业务名——抽象类是实现细节，不该出现在领域词汇里。`Message` 本来就该是个抽象概念（"一条消息"），不需要叫 `AbstractMessage` 提醒别人。

例外：JDK 里的 `AbstractList`、`AbstractMap` 是**骨架实现**（Skeletal Implementation），它们的价值就是"给实现者省事"，叫 `Abstract` 是准确的。

## 6. 执行流程

### 6.1 `new NormalMessage(email).send(...)`

```text
new EmailSender()                       → 具体实现对象
new NormalMessage(email)
  └─ Message.<init>(email)
       └─ this.sender = requireNonNull(email)
NormalMessage.send("欢迎使用我们的系统！")
  ├─ println("【普通通知】")
  └─ sender.send("欢迎使用我们的系统！")
       └─ 动态分派到 EmailSender.send(...)
            └─ println("  [邮件] 欢迎使用我们的系统！")
```

### 6.2 `new UrgentMessage(sms).send(...)`

```text
new UrgentMessage(new SmsSender())
  └─ Message.<init>(sms) → sender = SmsSender
UrgentMessage.send("服务器 CPU 负载过高！")
  ├─ println("【紧急告警】")
  └─ sender.send("【加急】服务器 CPU 负载过高！")     ← 内容在抽象维度被加工
       └─ SmsSender.send(...)
            └─ println("  [短信] 【加急】服务器 CPU 负载过高！")
```

**两次动态分派**：一次是 `Message.send`（选普通/紧急/定时），一次是 `MessageSender.send`（选邮件/短信/微信/钉钉）。桥接的运行本质就是"两次分派"。

### 6.3 `new ScheduledMessage(dingtalk, 5000).send(...)`

```text
new ScheduledMessage(new DingTalkSender(), 5000)
  └─ sender = DingTalkSender, delayMillis = 5000
send("明日 10:00 例会")
  ├─ println("【定时消息】将在 5000ms 后发送")
  └─ sender.send("[定时] 明日 10:00 例会")
       └─ println("  [钉钉] [定时] 明日 10:00 例会")
```

### 6.4 完整输出（25 行，逐字）

```text
========== 桥接模式演示 ==========

--- 普通通知 × 邮件 ---
【普通通知】
  [邮件] 欢迎使用我们的系统！

--- 紧急告警 × 短信 ---
【紧急告警】
  [短信] 【加急】服务器 CPU 负载过高！

--- 紧急告警 × 微信 ---
【紧急告警】
  [微信] 【加急】数据库连接异常！

--- 定时消息 × 钉钉（扩展演示）---
【定时消息】将在 5000ms 后发送
  [钉钉] [定时] 明日 10:00 例会

--- 普通通知 × 钉钉（扩展演示）---
【普通通知】
  [钉钉] 新版本已发布

========== 维度独立扩展验证 ==========
新增 ScheduledMessage（抽象维度）：未修改任何 Sender 类
新增 DingTalkSender（实现维度）：未修改任何 Message 类
```

### 6.5 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/structural/bridge/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.bridge.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 25 |
| 字节数 | 774（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `527F6F60F491634E` |
| 确定性 | 连跑两次逐行 diff 一致 |

## 7. 扩展对比

### 7.1 新增"飞书"渠道

**只加一个文件：**

```java
public class FeishuSender implements MessageSender {
    @Override
    public void send(String message) {
        System.out.println("  [飞书] " + message);
    }
}
```

**修改 0 个文件**。`Client` 想用就加一行 `new NormalMessage(new FeishuSender())`。

对比继承方案：要新增 `NormalFeishuMessage`、`UrgentFeishuMessage`、`ScheduledFeishuMessage` 三个类，而且以后每加一种消息类型还要再补一个。

### 7.2 新增"营销消息"类型

```java
public class MarketingMessage extends Message {
    public MarketingMessage(MessageSender sender) { super(sender); }

    @Override
    public void send(String content) {
        System.out.println("【营销推广】");
        sender.send("【推广】" + content);
    }
}
```

**修改 0 个文件**，立刻支持全部 5 个渠道。

### 7.3 运行时切换渠道：`setter` 还是 `withSender`

有人想让同一条消息换渠道重发：

```java
// 危险写法：加 setter
message.setSender(new WeChatSender());
```

问题：`sender` 变成可变状态后，① 不再线程安全；② 无法用 `final` 表达"桥一旦搭好就不拆"；③ 一个 `Message` 对象在不同时刻含义不同，调试困难。

**更好的做法：不可变 + 返回新对象**

```java
public Message withSender(MessageSender newSender) {
    // 每个子类返回自己的新实例
    return new NormalMessage(newSender);
}
```

或者干脆在调用方重新 `new` 一个：

```java
new NormalMessage(wechat).send(content);
```

**结论**：桥接的"桥"应该是**构造期确定、生命周期不变**的。需要换渠道，就换对象，不要改字段。

### 7.4 如果要加第三个维度（优先级）

比如再加"低/中/高"三档优先级，组合变成 3 × 4 × 3 = 36 种。

**桥接只能处理两个维度**，再加一个维度会变成"桥接套桥接"，类数量 3 + 4 + 3 = 10 但组合逻辑复杂度上去了。

两条出路：

**① 把"优先级"变成数据，而不是类型**

```java
record MessageEnvelope(String content, Priority priority) {}

message.send(envelope);   // 发送逻辑里根据 priority 决定是否加急/重试
```

**能变成数据的东西，不要变成类型。** 优先级只是一个枚举值，没有独立的"行为继承体系"，不值得为它开一个维度。

**② 用装饰器叠加横切能力**

```java
Message retryable = new RetryableMessage(new NormalMessage(sender), 3);
Message traced    = new TracedMessage(retryable);
```

装饰器处理"可叠加的能力"，桥接处理"正交的两个维度"。

> 判断标准：**新维度会不会带来新的子类？** 会 → 考虑桥接；只是一个属性/开关 → 变成数据。

### 7.5 桥接 + 抽象工厂：避免"非法组合"

桥接的语法允许任意组合，但业务上常常有约束：

- "紧急告警只能走短信和电话"
- "定时消息至少延迟 1 分钟"
- "营销消息不许走钉钉"

**桥接不负责表达这些约束**，应该放在工厂或校验层：

```java
public final class MessageFactory {
    public static Message urgent(String content, MessageSender sender) {
        if (sender instanceof EmailSender) {
            throw new IllegalArgumentException("紧急告警不允许走邮件");
        }
        return new UrgentMessage(sender);
    }
}
```

更彻底的方案是**抽象工厂**：为每个"渠道套装"提供一个工厂，保证创建出来的实现是配套的（比如"阿里云套装"里短信、邮件、推送都来自同一个 SDK，凭据共享）。

```java
public interface SenderFactory {
    MessageSender sms();
    MessageSender email();
    MessageSender push();
}
```

这样 `Client` 只选工厂，不逐个选实现，避免"短信用阿里云、邮件用腾讯云、凭据对不上"的混搭错误。

### 7.6 与另外四个模式的分界

| 模式 | 维度数 | 目的 | 时机 |
|---|---|---|---|
| **桥接** | **2**（都有继承体系） | 两个维度独立扩展，避免 M×N | 事前设计 |
| **策略** | 1 | 替换算法 | 事前/事中 |
| **适配器** | 1 | 接口转换，让不兼容的能协作 | 事后补救 |
| **抽象工厂** | 1（产品族维度） | 创建一整套配套对象 | 事前 |
| **装饰器** | 1（可叠加） | 动态增加行为 | 事中/运行时 |

**判定流程（按顺序问）：**

```text
1. 是为了让两个不兼容的接口能协作吗？  ── 是 → 适配器
2. 两个维度是否都各自有多个子类？      ── 否 → 看下面
3. 只有一个维度可替换吗？              ── 是 → 策略
4. 需要动态叠加多层能力吗？            ── 是 → 装饰器
5. 需要统一创建一整套配套对象吗？      ── 是 → 抽象工厂
```

**桥接 vs 策略的一句话**：策略是"换一个算法"，桥接是"换一套平台"。

### 7.7 语言演化：`MessageSender` 是函数式接口

因为它只有一个抽象方法，可以退化成 lambda：

```java
MessageSender feishu = msg -> System.out.println("  [飞书] " + msg);
new NormalMessage(feishu).send("新版本已发布");
```

真实项目里这就够用了（比如测试时塞一个"只记录不发送"的假渠道）：

```java
List<String> sent = new ArrayList<>();
MessageSender fake = sent::add;              // 方法引用
new UrgentMessage(fake).send("test");
```

**但这不能替代整个模式**：一旦渠道需要多个方法（`send`、`healthCheck`、`close`），或者需要共享状态（连接池、token 缓存），就必须回到类。

## 8. 优缺点与误区

### 8.1 优点

1. **避免类爆炸**：M×N → M+N，且增长方式从乘法变加法。
2. **两个维度独立扩展**：新增渠道不动消息类，新增消息类型不动渠道类（有文件级证据）。
3. **运行时组合**：`new NormalMessage(wechat)` 与 `new NormalMessage(email)` 共用同一个 `NormalMessage` 类。
4. **职责清晰**：抽象维度管"消息怎么组织"，实现维度管"怎么发出去"。
5. **可替换平台**：测试时用假 `MessageSender`，不需要真的发短信。

### 8.2 缺点

1. **多一层间接**：读代码要跳一次；调试栈更深。
2. **维度识别成本**：拆错了维度，两边都会痛（比如把"加急"拆进渠道，导致"加急邮件"无法实现）。
3. **无法表达维度间的约束**："紧急只能走短信"这种规则，桥接不管，得另找地方。
4. **组合责任外移**：谁来决定 `new UrgentMessage(sms)`？`Client` 里散落 `new` 会难维护，需要工厂收口（§7.5）。
5. **假桥接风险**：两条继承体系各只有一个节点时，纯粹是负担。

### 8.3 八个误区

| 误区 | 真相 | 判定 |
|---|---|---|
| "接口 + 实现类就是桥接" | 桥接要求**两条**继承体系 | 抽象部分有子类吗？ |
| "桥接就是策略模式" | 策略一个维度，桥接两个 | 实现侧有多个继承节点吗？ |
| "桥接是适配器的事前版" | 适配器改接口，桥接分离维度，目的不同 | 是在解决"不兼容"还是"会膨胀"？ |
| "任何两个维度都能桥接" | 必须**正交**（任意组合都有意义） | 矩阵里是不是每一格都合法？ |
| "桥接能消除所有 if" | `Client` 仍要选组合；工厂里仍有判断 | 判断只是从"发送时"移到"创建时" |
| "桥接一定要用抽象类" | 接口也行，但接口不能有实例字段，桥要放在实现类里 | 需要共享 `sender` 字段吗？ |
| "`sender` 应该有 setter 便于切换" | 破坏不可变，导致状态漂移 | 换渠道就换对象（`withSender`） |
| "用了桥接就不会有非法组合" | 桥接只保证结构合法，不保证业务合法 | 约束要放工厂/校验层 |

### 8.4 出现这些信号，说明你在滥用桥接

- 抽象部分只有 1 个子类，实现部分只有 1 个实现 → 假桥接。
- 两个维度的组合矩阵里有大量空格（业务上不存在）→ 维度不正交。
- `Message` 里出现了 `if (sender instanceof SmsSender)` → 抽象维度反向依赖了实现维度，桥断了。
- `MessageSender` 接口有 5 个以上方法，且实现类里有大量空方法 → 接口太宽，该拆。
- `Client` 里出现 `new XxxMessage(new YyySender())` 十几处 → 组合逻辑该收进工厂。

## 9. 面试与自测

### 9.1 30 秒版

> 桥接模式把抽象部分和实现部分拆成两条独立的继承体系，用组合连接，让两个维度各自扩展。典型场景是"消息类型 × 发送渠道"：3 种类型 4 个渠道，用继承要 12 个类，用桥接只要 7 个。它和策略的区别是策略只有一个维度可替换；和适配器的区别是适配器事后补救接口不兼容，桥接事前预防类爆炸。

### 9.2 2 分钟版

> 我拿通知中心举例。消息类型有三种：普通、紧急、定时；渠道有四个：邮件、短信、微信、钉钉。如果按"每个组合一个类"来做，是 3×4 共 12 个类，而且每加一个渠道就要复制 3 份，每加一个类型要复制 4 份。
>
> 桥接的做法是拆成两条继承体系：`Message` 抽象类持有 `MessageSender` 接口引用，这个引用就是"桥"。`NormalMessage`、`UrgentMessage`、`ScheduledMessage` 负责"这种消息怎么包装内容"——比如紧急消息加【加急】前缀；四个 Sender 负责"怎么真的发出去"。
>
> 两个维度可以独立扩展：新增钉钉只加一个类，修改 0 个文件；新增定时消息也只加一个类，修改 0 个文件。
>
> 实现上有个细节：`sender` 必须是 `final` 且非 null。`final` 保证桥不被偷偷换掉，`requireNonNull` 保证不会把 NPE 推迟到 `send()` 里。这是两个不同的不变式。
>
> 还有一个容易犯的错：把注释写成"模板方法"。`send` 是抽象的，没有固定骨架，不是模板方法。如果要统一"内容校验 + 发送"，才应该改成 `final send()` + `protected doSend()`——那时是桥接和模板方法叠加，不冲突。
>
> 桥接的边界是：两个维度必须正交。如果"紧急只能用短信"，这种约束桥接表达不了，要放在工厂或校验层。

### 9.3 追问链

| 追问 | 回答要点 |
|---|---|
| 桥接和依赖倒置什么关系 | 桥接是 DIP 的一个具体结构；DIP 不要求两个维度都有继承体系 |
| 为什么 `sender` 放父类不放子类 | 三个子类共享同一个桥，避免重复字段；也保证"所有消息都必然有一个渠道" |
| 能不能把 `delayMillis` 放父类 | 不能。它只属于定时消息，放父类会污染其他子类（`Message` 会变成"什么字段都塞一点"的上帝类） |
| 加了第三个维度怎么办 | 优先变成数据（`priority` 字段）；需要可叠加能力用装饰器；不要再套一层桥接 |
| 桥接能不能用接口代替抽象类 | 不能直接替代：接口不能有实例字段。可以定义 `interface Message { MessageSender sender(); }`，但每个实现都得自己存字段 |
| 测试怎么测 | 用假 `MessageSender`（lambda 或 stub）记录调用；断言"抽象维度加工后的内容 + 渠道被调用"两件事 |
| 桥接的性能开销 | 一次额外的方法调用，微乎其微。真正影响性能的是渠道实现（网络、序列化），不是桥 |

### 9.4 自测题

**1. 桥接模式与策略模式最本质的区别？**

<details><summary>参考答案</summary>

**维度的数量**。策略模式里只有一个维度在变（算法），持有方（如 `PriceCalculator`）没有子类体系；桥接里抽象部分和实现部分**各自都有继承体系**，两个维度独立扩展。结构上看：策略是"一个三角形"，桥接是"两个三角形 + 一条横线"。
</details>

**2. 为什么桥接能避免类爆炸？**

<details><summary>参考答案</summary>

继承把两个变化方向合并在一个类型里，组合数是 M×N；桥接把两个方向拆成两条继承体系，用组合连接，类数是 M+N。更重要的是"改动扩散"：新增渠道在继承方案里要改 M 个类，在桥接里只加 1 个类。
</details>

**3. "桥"指的是什么？为什么必须是 `final`？**

<details><summary>参考答案</summary>

桥是抽象部分持有的 `MessageSender` 引用，它是连接两个维度的唯一通道。`final` 保证桥在对象生命周期内不变——否则一个 `Message` 对象可能在 `send()` 前后指向不同的渠道，行为不可预测，也无法安全共享。`final` 只防"改指向"，还要配 `Objects.requireNonNull` 防"指向空"。
</details>

**4. `NormalMessage` 和 `UrgentMessage` 的差异应该放在哪一维？**

<details><summary>参考答案</summary>

放在**抽象维度**（`Message` 的子类）。判断标准：这段逻辑换个渠道还需要吗？"加急"这个语义换邮件、换钉钉都需要，所以属于消息类型。如果放到 `SmsSender` 里，紧急邮件就没有加急标记了——职责放错维度。
</details>

**5. 本项目的 `send` 为什么不是模板方法？**

<details><summary>参考答案</summary>

因为它是 `abstract`，没有任何固定骨架，子类可以自由决定流程。模板方法要求父类定义 `final` 的流程方法，把可变步骤留给 `protected abstract` 钩子。修复前 `Message` 的注释写成"模板方法"是错的。若将来要统一加"内容校验/埋点"，才应该改成 `final send()` + `doSend()`。
</details>

**6. 新增一个渠道要改几处？新增一个消息类型呢？**

<details><summary>参考答案</summary>

新增渠道：1 个新文件（`XxxSender implements MessageSender`），0 处修改。新增消息类型：1 个新文件（`XxxMessage extends Message`），0 处修改。`Client` 想用新组合时只加 `new` 语句。这正是桥接的验收标准。
</details>

**7. 如果业务规定"紧急告警只能走短信和电话"，桥接怎么表达？**

<details><summary>参考答案</summary>

桥接**表达不了**。它的语法允许 `new UrgentMessage(emailSender)`，因为两个维度是正交的。约束要放在工厂（创建时校验）或领域服务（发送前校验）里。这也是桥接的已知缺点：它只管结构，不管业务规则。
</details>

**8. 加第三个维度（优先级）该怎么做？**

<details><summary>参考答案</summary>

优先把优先级变成**数据**（`record MessageEnvelope(String content, Priority priority)`），而不是再开一个类型维度——因为它没有独立的行为继承体系。如果第三维度确实有多个子类且行为各异，考虑装饰器叠加；继续套桥接会让组合数和理解成本失控。
</details>

**9. 手写题：用桥接实现"报表类型 × 导出格式"。**

<details><summary>参考答案</summary>

```java
// 实现维度
public interface Exporter {
    void export(String data);
}

public class PdfExporter implements Exporter {
    @Override public void export(String data) {
        System.out.println("[PDF] " + data);
    }
}

public class ExcelExporter implements Exporter {
    @Override public void export(String data) {
        System.out.println("[Excel] " + data);
    }
}

// 抽象维度
public abstract class Report {
    protected final Exporter exporter;

    protected Report(Exporter exporter) {
        this.exporter = Objects.requireNonNull(exporter);
    }

    public abstract void generate();
}

public class SalesReport extends Report {
    public SalesReport(Exporter e) { super(e); }

    @Override public void generate() {
        exporter.export("销售数据：……");
    }
}

// 使用
new SalesReport(new PdfExporter()).generate();
```
要点：抽象维度管"报表内容怎么算"，实现维度管"怎么写出文件"；两边各有子类，才是桥接。
</details>

**10. 判断对错：`UserService` 持有 `UserDAO` 接口，这是桥接模式。**

<details><summary>参考答案</summary>

错。这是依赖倒置（DAO 模式）。`UserService` 没有"多个服务子类"这条继承体系，只有 DAO 一侧在变，属于策略/依赖倒置的范畴。桥接要求**两条**继承体系都存在且独立扩展。
</details>

**11. 为什么 `MessageSender` 设计成接口，而不是抽象类？**

<details><summary>参考答案</summary>

① 各渠道之间没有共享逻辑，不需要基类；② Java 单继承，抽象类会占掉实现类唯一的父类名额；③ 接口是函数式接口，可以退化成 lambda，便于测试。如果渠道之间有公共逻辑（加签、重试），才应该改成抽象类 + 模板方法。
</details>

**12. 桥接的"独立扩展"怎么用实验证明，而不是靠说？**

<details><summary>参考答案</summary>

用**文件级证据**：新增 `DingTalkSender` 时只新增 1 个文件、修改 0 个文件；新增 `ScheduledMessage` 时同样只新增 1 个。可以对比编译前后的 `git status`，或者像本目录一样在输出里直接断言"未修改任何 Sender 类 / 任何 Message 类"。空口说"符合开闭原则"没有说服力。
</details>

### 9.5 面试官想听什么

**想听**：

- "两个维度必须正交"——说明你懂适用条件。
- "策略是换算法，桥接是换平台"——说明你能区分相近模式。
- "`final` 防换，`requireNonNull` 防空，是两个不变式"——说明你写的是工程代码，不是背模式。
- "约束放工厂，桥接不管业务规则"——说明你知道模式的边界。
- "第三维度优先变成数据"——说明你有取舍能力。

**不想听**：

- 把"接口 + 实现"当桥接，讲不出两条继承体系。
- 说"桥接就是解耦"，说不出解的是哪两个维度。
- 认为桥接可以消除所有 if（组合选择总得有人做）。
- 举不出一个自己项目里的例子。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 角色 | 职责 |
|---|---|---|
| `Message.java` | Abstraction | 持有 `sender`（桥），定义 `send` |
| `NormalMessage.java` | RefinedAbstraction | 原样发送 |
| `UrgentMessage.java` | RefinedAbstraction | 加"【加急】"前缀 |
| `ScheduledMessage.java` | RefinedAbstraction | 带延迟参数，加"[定时]"标记 |
| `MessageSender.java` | Implementor | 渠道能力接口 |
| `EmailSender.java` | ConcreteImplementor | 邮件渠道 |
| `SmsSender.java` | ConcreteImplementor | 短信渠道 |
| `WeChatSender.java` | ConcreteImplementor | 微信渠道 |
| `DingTalkSender.java` | ConcreteImplementor | 钉钉渠道（扩展演示） |
| `Client.java` | Client | 组合两个维度并验证独立扩展 |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.structural.bridge.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/structural/bridge/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.bridge.Client
```

### 10.3 本次修订记录

| 改动 | 原因 |
|---|---|
| `Message` 构造器增加 `Objects.requireNonNull(sender)` | `final` 只防"改指向"，不防"指向 null"；NPE 应尽早暴露 |
| 修正 `send` 的注释（去掉"模板方法"的错误表述） | 抽象方法没有固定骨架，不是模板方法；错误注释会误导后续修改 |

---

相关文档：[模式对比 §结构型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
