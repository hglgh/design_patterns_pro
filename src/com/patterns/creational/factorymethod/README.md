# 工厂方法模式（Factory Method Pattern）

> **一句话**：定义创建对象的接口，让子类决定实例化哪个类——把"创建"延迟到子类。
>
> 本目录实测：**12 个源文件 / 225 行**，`javac -Xlint:all` 零告警；运行输出 **8 行 / 392 字节**，
> SHA-256 `B5F024D79033B191…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 一致）。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 它解决什么 | 使用方不该知道"具体创建哪个类"，更不该因为新增产品而改自己 |
| 它的分水岭是什么 | **工厂本身也有多态**。只有一个 `Factory` 类 + `switch`，那是简单工厂，不是工厂方法 |
| 它和抽象工厂的区别 | 工厂方法产出**一个**产品；抽象工厂产出**一族**产品（§7.6） |
| 什么时候别用 | 产品只有一种且不会新增；直接 `new` 更清楚 |
| 最容易犯的错 | 把"静态方法返回对象"当成工厂方法（那是静态工厂，属于编码习惯，不是 GoF 模式） |

一句话判断：**"以后会不会新增产品类型，而且新增时不能改使用方？"** 会，才需要它。

## 1. 现实案例：说人话版

### 1.1 三个生活类比

**招聘**。用人部门写"岗位需求"（要什么能力），HR 按需求招人。业务部门不需要知道招聘渠道是校招、社招还是猎头——那是 HR 子部门各自决定的。

**连锁店出餐**。总部规定"每个门店必须能出一份招牌套餐"，但具体谁做、怎么做，各门店自己定。总部（框架）不需要知道每家店的厨房（具体产品）。

**证件照打印**。你选"一寸照"，机器自己决定用哪套模板、哪种纸张。你只说"要什么"，不说"怎么造"。

### 1.2 业务场景：多端消息通知

`NotificationService` 负责"给用户发一条通知"。发送方式有：短信、邮件、站内信、微信。

**不用工厂**，代码必然长这样：

```java
public class NotificationService {
    public void notifyUser(String to, String content) {
        // 硬编码：每加一种渠道就要改这里
        new SmsMessage().send(to, content);
    }
}
```

要支持邮件？改 `NotificationService`。要按配置切换？加 `if`。结果是：**一个业务服务被迫成为"消息类型注册表"**。

**用工厂方法**，`NotificationService` 只依赖两个抽象：

```java
public final class NotificationService {
    private final MessageFactory messageFactory;

    public NotificationService(MessageFactory messageFactory) {
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory");
    }

    public void notifyUser(String to, String content) {
        Message message = messageFactory.createMessage();   // 创建交给工厂
        message.send(to, content);                          // 使用交给产品
    }
}
```

新增微信消息：**加 2 个类，改 0 个已有类**（`Client` 里加一行 `new` 属于装配，不算修改业务逻辑）。

### 1.3 专业版：GoF 的定义与四个角色

> Define an interface for creating an object, but let subclasses decide which class to instantiate. Factory Method lets a class defer instantiation to subclasses.
> —— *Design Patterns*, GoF, 1994

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **Product（抽象产品）** | 使用方依赖的产品接口 | `Message` |
| **ConcreteProduct（具体产品）** | 真正被创建的对象 | `SmsMessage` / `EmailMessage` / `InAppMessage` / `WeChatMessage` |
| **Creator（抽象工厂）** | 声明工厂方法 `createMessage()` | `MessageFactory` |
| **ConcreteCreator（具体工厂）** | 决定 `new` 哪个具体产品 | `SmsMessageFactory` 等 4 个 |

注意 GoF 原文里的关键词：**"let subclasses decide"**。子类决定，而不是"一个方法里 switch"。这就是工厂方法与简单工厂的分界线。

### 1.4 JDK 里真实存在的工厂方法

**① `Collection.iterator()`（最典型）**

```java
List<String> list = new ArrayList<>();
Iterator<String> it = list.iterator();      // 谁来 new 这个 Iterator？
```

`ArrayList.iterator()` 返回 `ArrayList$Itr`，`HashMap.iterator()` 返回 `HashMap$KeyIterator`，`TreeMap` 返回红黑树的遍历器。**`Collection` 接口定义了"能产出迭代器"，具体产出哪个由各集合子类决定。**

`Iterator` 是 Product，`Collection` 是 Creator，各集合类是 ConcreteCreator。这就是标准的工厂方法——只不过"工厂"和"产品使用者"常常是同一个对象。

**② `ThreadFactory.newThread(Runnable)`**

```java
ThreadFactory f = Executors.defaultThreadFactory();
Thread t = f.newThread(task);
```

`newThread` 是工厂方法，`Thread` 是产品。线程池用它来定制线程（命名、优先级、守护线程），而 `ThreadPoolExecutor` 完全不知道具体怎么创建线程。

**③ `URLStreamHandlerFactory.createURLStreamHandler(String protocol)`**

`URL` 遇到 `http://` 时问工厂"有 http 的处理器吗"，工厂决定返回哪个 `URLStreamHandler`。新增协议 = 新增处理器 + 注册，`URL` 类不动。

**④ 容易误认的：`Calendar.getInstance()`、`NumberFormat.getInstance()`**

它们是**静态工厂方法**（static factory method），是《Effective Java》第 1 条讲的编码技巧，**不是 GoF 的工厂方法模式**——因为它们没有"子类决定"这一层多态。

> 面试里把 `getInstance()` 当工厂方法讲，会被追问"那它哪个子类决定了创建哪个类？"——答不上来就露馅了。

## 2. 原始实现：三版代码

### 2.1 v0：使用方直接 `new`

```java
public class NotificationService {
    public void notifyUser(String to, String content) {
        Message message = new SmsMessage();      // ← 依赖具体类
        message.send(to, content);
    }
}
```

三个阶段的问题：

1. **编译期绑死**：想换邮件，必须改源码重新编译。
2. **无法按配置切换**：产品类型是运行期数据，`new` 是编译期决策。
3. **测试困难**：想测"通知逻辑"，必须有真实的 `SmsMessage`。

### 2.2 v1：静态简单工厂

```java
public final class SimpleMessageFactory {
    public static Message create(String type) {
        return switch (type) {
            case "sms" -> new SmsMessage();
            case "email" -> new EmailMessage();
            case "inapp" -> new InAppMessage();
            default -> throw new IllegalArgumentException("未知类型：" + type);
        };
    }
}
```

好处：使用方只依赖 `Message`；坏处：**新增产品必须改这个 `switch`**，违反开闭原则。而且最后一个 `default` 分支永远在提醒你"还有产品没登记"。

简单工厂不是"错的设计"——它简单、集中、易读。**产品固定且数量少时，它是更好的选择**（§7.2）。

### 2.3 v2：工厂方法（原项目的结构）

原项目把"创建"拆成 4 个工厂类：

```java
public interface MessageFactory {
    Message createMessage();
}

public class SmsMessageFactory implements MessageFactory {
    @Override
    public Message createMessage() {
        return new SmsMessage();
    }
}
```

`NotificationService` 用 `record` 持有工厂：

```java
public record NotificationService(MessageFactory messageFactory) {
    public void notifyUser(String to, String content) {
        Message message = messageFactory.createMessage();
        message.send(to, content);
    }
}
```

`Main` 里切换工厂靠**注释**：

```java
// MessageFactory factory = new InAppMessageFactory();
MessageFactory factory = new SmsMessageFactory();
// MessageFactory factory = new EmailMessageFactory();
```

结构对了。但原项目有一个**编译期完全发现不了的 bug**。

### 2.4 原项目的缺陷：接口与实现的参数顺序是反的（实测）

`message/Message.java` 的声明：

```java
public interface Message {
    /**
     * 发送消息
     * @param content 消息内容
     * @param to 接收者
     */
    void send(String content, String to);      // ← 内容在前，接收者在后
}
```

而三个实现类：

```java
public class SmsMessage implements Message {
    @Override
    public void send(String to, String content) {     // ← 接收者在前，内容在后
        System.out.println("发送短信给 " + to + "，内容：" + content);
    }
}
```

**为什么能编译通过？** 因为 Java 的覆写检查只看**方法名 + 参数类型**，不看参数名。`send(String, String)` 与 `send(String, String)` 完全匹配，参数名只是"文档"。

**后果有多严重？** 拿探针实测（照着接口签名调用）：

```text
[1] 照着接口签名调：send(content, to)
发送短信给 这是消息内容，内容：13800000000
[2] 照着实现类参数名调：send(to, content)
发送短信给 13800000000，内容：这是消息内容
```

`[1]` 输出里，**手机号变成了"接收者"、消息内容变成了"内容"的反面**——一条验证码短信会发给"这是消息内容"这个字符串。

这个 bug 的可怕之处：

| 特征 | 说明 |
|---|---|
| 编译不报错 | 类型都是 `String`，签名匹配 |
| 单测难发现 | 只有断言"输出内容"才能发现；断言"调用成功"没用 |
| IDE 不提示 | 覆写检查不比对参数名 |
| 只在"照接口编程"时爆 | 直接 `new SmsMessage().send(to, content)` 反而正常 |

**教训**：**参数名是接口契约的一部分**。同一类型的多个参数，必须靠命名（或参数对象）消除歧义。更好的做法是把 `(String to, String content)` 换成 `record MessageTarget(String type, String address)`，从类型上杜绝顺序错误。

> 重构版已把接口与实现统一为 `send(String to, String content)`，并在 Javadoc 里明确 `@param to 接收者（手机号 / 邮箱 / 用户 ID）`。

### 2.5 原项目的其他问题

| # | 问题 | 性质 |
|---|---|---|
| F1 | 接口与实现的参数顺序相反 | **真 bug**（已修） |
| F2 | 切换工厂靠注释行 | 演示方式，但也说明"选择工厂"的责任留在使用方 |
| F3 | 三个具体工厂的 `createMessage()` 只有一行 `new` | 正常，工厂方法的成本就在这里（见 §8.2） |
| F4 | 产品无状态，每次调用都 `new` 一个 | 可优化为复用，但工厂方法的重点就是"创建"，Demo 保持原样 |
| F5 | 没有演示"新增产品" | 教学缺口（重构版补了微信消息） |

### 2.6 重构版的改进

| 项 | 原项目 | 重构版 |
|---|---|---|
| 参数顺序 | 接口 `(content, to)`，实现 `(to, content)` | 统一 `(to, content)` |
| 构造器 null 防御 | 无 | `Objects.requireNonNull(messageFactory)` |
| 服务类可变性 | `record`（不可变） | `final` + `final` 字段（同样不可变） |
| 扩展演示 | 无 | 新增 `WeChatMessage` + `WeChatMessageFactory`，输出里显式验证 |
| 演示场景 | 1 条（短信） | 4 条（短信/邮件/站内信/微信） |

## 3. 设计动机

### 3.1 四个真实动机

| 动机 | 说明 |
|---|---|
| **框架延迟绑定** | 框架代码要能在**不知道具体类**的情况下工作（`Collection.iterator()` 是范例） |
| **隔离创建与使用** | 使用方只关心"能发消息"，不关心"怎么造消息" |
| **新增产品不改旧代码** | 新增 = 新增两个类，已有类零修改 |
| **可测试性** | 测试时注入一个假工厂，就能验证"通知逻辑"本身 |

第四条常被忽略，但它是工程上最实用的：**工厂是天然的可替换接缝**。

### 3.2 什么时候**不该**用工厂方法

**① 产品只有一种，且明确不会新增。** 直接 `new`。多一层工厂只是多一个文件。

**② 产品类型由运行期字符串决定，且种类固定。** 简单工厂的 `switch` 更直观，一个文件就能看完。

**③ 创建逻辑很简单（一行 `new`），且产品不需要被替换。** 工厂方法的收益是"可替换"，没有替换需求就没有收益。

**④ 你想解决的是"创建复杂对象"。** 那是建造者；工厂方法只管"创建哪个类"。

**⑤ 你想解决的是"一族产品要配套创建"。** 那是抽象工厂（§7.6）。

### 3.3 三种"工厂"的边界（最容易混）

| 形式 | 结构 | 新增产品要改什么 | 是否 GoF 模式 |
|---|---|---|---|
| **静态工厂方法** | `MessageFactory.create(type)` 一个静态方法 | 改这个方法的 `switch` | 否（编码习惯） |
| **简单工厂** | 一个 `Factory` 类 + `switch`/`Map` | 改工厂类 | 否（习惯称"简单工厂"，GoF 未收录） |
| **工厂方法** | `Factory` 接口 + 每个产品一个工厂类 | 只**新增**类，不改已有类 | 是 |
| **抽象工厂** | `Factory` 接口 + **每个产品族**一个工厂，每个工厂创建多个产品 | 新增产品族 = 新工厂；新增产品种类 = 改接口（痛） | 是 |

## 4. 设计推导：从 v2 到最终代码

### 4.1 第 1 步：把"创建"从"使用"里挪出来

目标：让 `NotificationService` 不知道 `SmsMessage` 的存在。

```java
// 只依赖产品接口
Message message = createMessage();
message.send(to, content);
```

问题变成：`createMessage()` 从哪来？

### 4.2 第 2 步：为什么不用"静态方法 + 参数"

```java
Message message = MessageFactory.create(type);     // 静态工厂
```

这条路最短，但有两个问题：

1. **`type` 是运行期数据，`new` 是编译期决策**——静态工厂把编译期决策藏进了 `switch`，类型安全变成字符串匹配。
2. **新增产品必须改工厂**，而工厂会被所有调用方共享（一个类的改动影响全局）。

如果产品种类**固定**（比如只有 3 种，且 5 年内不会变），静态工厂是最优解：**别为了模式而模式**。

### 4.3 第 3 步：工厂也做多态——分水岭

把工厂变成接口，每个产品配一个工厂：

```java
public interface MessageFactory {
    Message createMessage();
}
```

此时：

- `NotificationService` 依赖 `MessageFactory`（抽象），不依赖任何具体工厂。
- 新增产品 = 新增 `XxxMessage` + `XxxMessageFactory`，**已有类零修改**。

这就是工厂方法。**它的代价是类数量翻倍**（N 个产品 → N 个工厂），收益是"新增不改旧"。

### 4.4 第 4 步：谁来选工厂？——模式的边界

工厂方法解决了"创建哪个产品"的多态，但**"选哪个工厂"这件事没有被消除**，只是换了位置：

| 选择方式 | 代码 | 适用 |
|---|---|---|
| 使用方 `new` | `new NotificationService(new SmsMessageFactory())` | Demo、启动装配 |
| 配置 + 反射 | `Class.forName(cfg.factory()).newInstance()` | 插件化（代价：运行期才报错） |
| 依赖注入容器 | `@Bean MessageFactory factory()` | Spring 等框架 |
| 工厂的工厂 | `FactoryProvider.get("sms")` | 产品族很多时（又回到 switch） |

**这不是工厂方法的缺陷，而是任何"间接"的固有代价**：总要有人在某处做最终决定。模式能做的是：**把决定集中到一个可配置的点**，而不是分散在业务代码里。

### 4.5 第 5 步：产品要不要带参数？

本项目的 `createMessage()` 无参——产品是无状态的（`SmsMessage` 只有一个 `send` 方法）。

如果产品需要构造参数（比如短信模板 ID、邮件发件人），有两种设计：

```java
// 方案 A：参数传给工厂方法
public interface MessageFactory {
    Message createMessage(MessageConfig config);
}

// 方案 B：参数放在具体工厂的构造器里
public class SmsMessageFactory implements MessageFactory {
    private final String templateId;
    public SmsMessageFactory(String templateId) { this.templateId = templateId; }
    @Override public Message createMessage() { return new SmsMessage(templateId); }
}
```

**方案 B 更好**：配置属于"工厂的职责"，不该让使用方每次创建都传一遍。使用方只该说"给我一条消息"。

### 4.6 第 6 步：两处细节

**① 构造器 `requireNonNull`**

```java
public NotificationService(MessageFactory messageFactory) {
    // 工厂是使用方唯一依赖的实现入口，为 null 时要尽早报错，而不是等到 notifyUser()
    this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory");
}
```

不校验的话，`new NotificationService(null)` 能通过编译、能构造成功，直到第一次 `notifyUser` 才在 `messageFactory.createMessage()` 抛 NPE——**错误位置离原因很远**。

**② 类声明 `final`**

`NotificationService` 是业务服务，不打算被继承。`final` 让"不可扩展"成为编译期事实，也避免子类覆盖 `notifyUser` 破坏行为。

### 4.7 最终结构

```text
      NotificationService（使用方，只依赖两个抽象）
              │ 持有
              ▼
        MessageFactory（Creator）
        ├─ SmsMessageFactory    ──创建──▶ SmsMessage
        ├─ EmailMessageFactory  ──创建──▶ EmailMessage
        ├─ InAppMessageFactory  ──创建──▶ InAppMessage
        └─ WeChatMessageFactory ──创建──▶ WeChatMessage
                                              │
                                              ▼
                                        Message（Product）
```

读图要点：**两个继承体系（工厂、产品）+ 一条使用关系**。工厂和产品是**一一对应**的——这是工厂方法与抽象工厂最直观的区别。

## 5. 代码解析

### 5.1 `Message`——抽象产品

```java
public interface Message {
    void send(String to, String content);
}
```

- 两个 `String` 参数是**歧义的温床**（§2.4 的 bug 就出在这里）。参数名必须写清楚，Javadoc 必须标注顺序。
- 方法只有两个参数，不能靠"参数类型不同"来防错。
- 它是**函数式接口**，所以 `SmsMessage` 这种"只打印"的产品可以退化成 lambda（§7.4）。

删除它会怎样？`NotificationService` 只能依赖具体的 `SmsMessage`，工厂也失去返回类型。

### 5.2 `MessageFactory`——抽象工厂

```java
public interface MessageFactory {
    Message createMessage();
}
```

一行方法，但它是整个模式的支点：**使用方依赖它，具体工厂实现它，产品由它产出**。

注意它**没有参数**：产品类型由"哪个实现类"决定，而不是由参数决定。这正是工厂方法与简单工厂的本质区别。

### 5.3 四个具体产品

```java
public class SmsMessage implements Message {
    @Override
    public void send(String to, String content) {
        System.out.println("[短信] 发送给 " + to + "，内容：" + content);
    }
}
```

`InAppMessage` 的输出是"推送给用户"，不是"发送给"——**站内信不是"发出去"，而是"推给已登录用户"**。这个措辞差异是有意的：同一个接口，不同产品的语义细节可以不同。

`WeChatMessage` 是**包私有**（`class WeChatMessage`，没有 `public`），另外三个是 `public`。两种都能跑，但风格不统一：

- 包私有 = 最小可见性，只在本包用，**推荐**；
- `public` = 当作示例 API 暴露。

本项目没统一这一点，属于已知的小瑕疵。真要统一，应当**全部改成包私有**——因为外部只需要 `Message` 和 `MessageFactory`。

### 5.4 四个具体工厂

```java
public class SmsMessageFactory implements MessageFactory {
    @Override
    public Message createMessage() {
        return new SmsMessage();
    }
}
```

它们几乎一模一样，只有 `new` 的类型不同。**看起来是重复代码，但这是工厂方法的成本**：把"创建"这件事从"使用"里剥离，必然要多出这些类。

想消除这种重复，有三条路（都在 §7）：

1. 用方法引用：`MessageFactory f = SmsMessage::new;`
2. 用 `Supplier<Message>` 代替工厂接口；
3. 退回简单工厂（牺牲 OCP）。

### 5.5 `NotificationService`——使用方

```java
public final class NotificationService {

    private final MessageFactory messageFactory;

    public NotificationService(MessageFactory messageFactory) {
        this.messageFactory = Objects.requireNonNull(messageFactory, "messageFactory");
    }

    public void notifyUser(String to, String content) {
        Message message = messageFactory.createMessage();
        message.send(to, content);
    }
}
```

三件事值得说：

1. **构造器注入**：工厂从外部传入，而不是内部 `new`。这是依赖倒置的落地方式。
2. **每次调用都创建新产品**：`createMessage()` 在 `notifyUser` 内部调用，意味着"每条通知一个新对象"。产品无状态时这样最安全（不用担心线程安全）。
3. **它不知道自己发的是什么**：`notifyUser` 里没有任何 `instanceof` / `type` 判断。这就是工厂方法的验收标准。

### 5.6 `Client`——装配层

```java
NotificationService smsService = new NotificationService(new SmsMessageFactory());
smsService.notifyUser("13812345678", "您的验证码是 123456");
```

`Client` 是唯一需要知道具体工厂的地方。**装配的复杂度被集中在入口**，业务代码保持干净。

## 6. 执行流程

以 `smsService.notifyUser("13812345678", "您的验证码是 123456")` 为例：

```text
1. new SmsMessageFactory()                      → 具体工厂实例
2. new NotificationService(factory)
     └─ this.messageFactory = requireNonNull(factory)
3. smsService.notifyUser("13812345678", "您的验证码是 123456")
     ├─ messageFactory.createMessage()           ← 动态分派到 SmsMessageFactory
     │    └─ return new SmsMessage()
     ├─ message.send("13812345678", "您的验证码是 123456")
     │    └─ 动态分派到 SmsMessage.send(...)
     └─ println("[短信] 发送给 13812345678，内容：您的验证码是 123456")
```

**两次动态分派**：一次选工厂（决定产品类型），一次选产品的 `send`（决定发送方式）。

### 6.1 完整输出（8 行，逐字）

```text
========== 工厂方法模式演示 ==========

[短信] 发送给 13812345678，内容：您的验证码是 123456
[邮件] 发送给 user@example.com，内容：您的订单已发货
[站内信] 推送给用户 user_1001，内容：您有一条新的系统通知

========== 扩展：新增微信消息 ==========
[微信] 发送给 wx_user_888，内容：您的好友请求已通过
```

### 6.2 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/factorymethod/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.factorymethod.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 8 |
| 字节数 | 392（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `B5F024D79033B191` |
| 确定性 | 连跑两次逐行 diff 一致 |

## 7. 扩展对比

### 7.1 新增"微信"消息：2 个类，0 处修改

```java
class WeChatMessage implements Message {
    @Override
    public void send(String to, String content) {
        System.out.println("[微信] 发送给 " + to + "，内容：" + content);
    }
}

class WeChatMessageFactory implements MessageFactory {
    @Override
    public Message createMessage() {
        return new WeChatMessage();
    }
}
```

**已有类一个都不用改**（`Client` 加一行 `new` 是装配，不属于业务逻辑）。

对比简单工厂：要往 `switch` 里加一个 `case`——**改的是已有类**，虽然只有一行，但它是"共享的、所有人都会经过的类"。

### 7.2 退回简单工厂（什么时候这样更好）

```java
public final class SimpleMessageFactory {
    private SimpleMessageFactory() {}

    public static Message create(String type) {
        return switch (type) {
            case "sms" -> new SmsMessage();
            case "email" -> new EmailMessage();
            case "inapp" -> new InAppMessage();
            case "wechat" -> new WeChatMessage();
            default -> throw new IllegalArgumentException("未知类型：" + type);
        };
    }
}
```

**优点**：使用方一行搞定；工厂只有一个文件；新增产品只改一处，不容易漏。
**缺点**：违反 OCP；`switch` 会越来越长；无法在编译期约束 `type`。

**决策依据**：

| 条件 | 选择 |
|---|---|
| 产品种类 ≤ 5，且业务上明确不会频繁新增 | 简单工厂 |
| 产品种类会持续增长，或由不同团队各自扩展 | 工厂方法 |
| 产品由配置文件/插件决定 | 工厂方法 + 反射/SPI |

### 7.3 用"注册表 + Supplier"替代工厂类

现代 Java 里更常见的写法是把工厂退化成 `Supplier`：

```java
public final class MessageRegistry {
    private static final Map<String, Supplier<Message>> FACTORIES =
            Map.of(
                    "sms", SmsMessage::new,
                    "email", EmailMessage::new,
                    "inapp", InAppMessage::new
            );

    public static Message create(String type) {
        Supplier<Message> supplier = FACTORIES.get(type);
        if (supplier == null) {
            throw new IllegalArgumentException("未知类型：" + type);
        }
        return supplier.get();
    }
}
```

**收益**：消灭了 4 个只有一行 `new` 的工厂类，`Map` 一眼看全。
**代价**：新增产品要改这个 `Map`（回到 OCP 问题）；需要运行期注册就得上 `ConcurrentHashMap`。

> 这不是"工厂方法的替代品"，而是**简单工厂的现代写法**。目标不同：它优化的是"少写类"，不是"新增不改旧"。

### 7.4 方法引用就是最简工厂

因为 `MessageFactory` 是函数式接口，产品又都是无参构造：

```java
MessageFactory smsFactory = SmsMessage::new;
NotificationService service = new NotificationService(smsFactory);
```

`SmsMessage::new` 完全等价于 `new SmsMessageFactory()`——**一个 lambda 就是一个工厂**。

那还写 `SmsMessageFactory` 干什么？三个理由：

1. **可读性**：`new SmsMessageFactory()` 在装配处比 `SmsMessage::new` 更能表达"这是一个工厂"。
2. **可扩展**：工厂将来可能要注入配置（模板 ID、发件人），方法引用就装不下了。
3. **可调试**：栈里能看到 `SmsMessageFactory.createMessage`，而不是 `lambda$main$0`。

**结论**：Demo、测试里用方法引用更简洁；生产代码里保留显式工厂更好维护。

### 7.5 产品带参数怎么办

产品需要配置时，**把配置放在工厂里**（§4.5 方案 B）：

```java
public class SmsMessageFactory implements MessageFactory {
    private final String templateId;

    public SmsMessageFactory(String templateId) {
        this.templateId = Objects.requireNonNull(templateId);
    }

    @Override
    public Message createMessage() {
        return new SmsMessage(templateId);
    }
}
```

这样 `NotificationService` 的签名不用变——**配置的复杂度被关在装配层**。

### 7.6 什么时候升级成抽象工厂

当产品从"一种"变成"一族"，工厂方法就不够了：

| 场景 | 工厂方法的困境 | 抽象工厂的解法 |
|---|---|---|
| 通知要同时有"消息 + 回执查询" | 每个产品都要配一个工厂，工厂接口要加方法 | `NotificationFactory` 提供 `createMessage()` + `createReceiptQuery()` |
| 不同云厂商的一整套 SDK | 阿里云的消息、回执、统计要配套 | `AliyunFactory` / `TencentFactory` 各产出一族 |

抽象工厂的代价：**新增"产品种类"要改工厂接口**（所有工厂都得实现新方法）。它的 OCP 只对"新增产品族"开放。

### 7.7 与另外四个模式的分界

| 模式 | 关注点 | 与本模式的区别 |
|---|---|---|
| **简单工厂** | 一个类集中创建 | 没有多态，新增产品要改工厂 |
| **抽象工厂** | 创建**一族**产品 | 产品维度 ≥ 2；工厂方法只有 1 个产品 |
| **建造者** | 分步构造**一个复杂对象** | 建造者关心"怎么一步步组装"，工厂只关心"造哪个类" |
| **原型** | 通过**克隆**创建 | 原型靠复制已有实例，工厂靠 `new` |
| **策略** | 替换**算法** | 策略替换的是行为，工厂方法替换的是"被创建的类型" |

**一句话**：工厂方法解决"**创建谁**"，策略解决"**怎么做**"，建造者解决"**怎么组装**"。

## 8. 优缺点与误区

### 8.1 优点

1. **使用方与具体产品解耦**：只依赖 `Message` / `MessageFactory` 两个抽象。
2. **新增产品不改旧代码**：加 2 个类即可（有实测：微信消息）。
3. **创建逻辑可以复用**：工厂里可以放缓存、对象池、配置校验，使用方无感。
4. **天然可测试**：注入假工厂即可隔离产品。
5. **符合单一职责**：产品负责行为，工厂负责创建。

### 8.2 缺点

1. **类数量翻倍**：N 个产品 → N 个工厂。本项目 4 个产品 = 4 个产品类 + 4 个工厂类 + 2 个接口。
2. **"创建"和"使用"要多跳一次**：读代码时 `createMessage()` 跳到哪个实现，需要 IDE 支持。
3. **选择责任没消失**：`Client` 仍要 `new SmsMessageFactory()`；只是从"业务代码"挪到了"装配代码"。
4. **容易过度设计**：产品只有一种时，工厂纯属负担。
5. **对"参数顺序"这类契约问题无能为力**：模式解决不了 `(String, String)` 的歧义（§2.4）。

### 8.3 八个误区

| 误区 | 真相 |
|---|---|
| "有 `Factory` 字样就是工厂方法" | 要看**工厂本身有没有多态**。`SimpleMessageFactory.create(type)` 不是 |
| "静态工厂方法 `getInstance()` 是工厂方法模式" | 不是。它是《Effective Java》的编码技巧，没有子类决定这一层 |
| "工厂方法能消除 `new`" | 不能。`new` 只是从使用方挪到了工厂里 |
| "工厂方法能消除 if/switch" | 它消除的是**使用方的**分发；选择工厂时仍然要判断（或配置/反射） |
| "工厂方法一定要一个产品一个工厂" | 严格说是。想省类可以用 `Supplier` 或注册表，但那就退化成简单工厂了 |
| "工厂方法返回的必须是新对象" | 不必。工厂可以返回缓存对象、对象池里的对象、甚至单例（此时要注意线程安全） |
| "有了工厂就不用管参数顺序" | 反了。**参数歧义是接口设计的责任**，工厂只会把错误放大（§2.4） |
| "工厂方法比简单工厂高级" | 不是高级，是**代价不同**。产品少而稳定时，简单工厂更划算 |

### 8.4 出现这些信号，说明你在滥用工厂

- 每个工厂类只有一行 `return new Xxx()`，且产品无状态、无配置 → 用 `Supplier` 或方法引用。
- 只有一个产品和一个工厂 → 去掉工厂，直接 `new`。
- 工厂接口有 5 个以上 `createXxx()` → 你其实需要抽象工厂，或者该拆成多个工厂。
- 工厂里出现业务判断（`if (user.isVip()) return ...`）→ 创建逻辑混入了业务规则，该上移。
- `Client` 里有 20 个 `new XxxFactory()` → 装配该交给容器或配置。

## 9. 面试与自测

### 9.1 30 秒版

> 工厂方法模式定义一个创建对象的接口，让子类决定实例化哪个类，把创建延迟到子类。它由四个角色组成：抽象产品、具体产品、抽象工厂、具体工厂。使用方只依赖抽象产品和抽象工厂。它和简单工厂的区别是工厂本身也有多态；和抽象工厂的区别是它只产出一个产品，抽象工厂产出一族产品。JDK 里的 `Collection.iterator()` 就是标准工厂方法。

### 9.2 2 分钟版

> 我拿通知服务举例。`NotificationService` 只依赖 `MessageFactory` 和 `Message`，构造时注入具体工厂，调用时 `messageFactory.createMessage()` 拿到产品再 `send`。
>
> 好处是新增产品只加两个类：比如新增微信消息，加 `WeChatMessage` 和 `WeChatMessageFactory`，`NotificationService` 一行都不用改——这就是开闭原则。
>
> 对比简单工厂：简单工厂是一个类里 `switch(type)`，新增产品要改那个 `switch`，虽然只有一行，但改的是共享类。工厂方法把"新增"变成"新增类"，代价是类数量翻倍。
>
> 我在原项目里发现一个真实 bug：接口声明是 `send(String content, String to)`，三个实现却写成 `send(String to, String content)`。因为类型都是 String，编译器完全不拦，覆写检查也不比参数名。实测照接口签名调用，输出会变成"发送短信给 这是消息内容，内容：13800000000"。这说明**参数名是契约的一部分**，同类型多参数必须靠命名或参数对象消除歧义。
>
> 另外要注意：工厂方法并没有消除"选择工厂"的责任，只是把它从业务代码挪到了装配层，可以由 `new`、配置、反射或 DI 容器承担。

### 9.3 追问链

| 追问 | 要点 |
|---|---|
| 工厂方法 vs 抽象工厂 | 前者 1 个产品、1 个工厂方法；后者 N 个产品、N 个创建方法，新增"产品种类"要改接口 |
| 工厂方法 vs 建造者 | 工厂管"造哪个类"，建造者管"怎么一步步组装"；建造者通常有 `build()` 返回复杂对象 |
| 工厂方法 vs 原型 | 原型靠 `clone` 复制已有实例，适合创建成本高的对象；工厂靠 `new` |
| 静态工厂有什么好处 | 有名字、可缓存、可返回子类型、可减少可见性（《Effective Java》第 1 条） |
| 工厂方法会不会影响性能 | 多一次虚调用，可忽略；真正开销在产品构造（连接、序列化） |
| Spring 里哪里有 | `BeanFactory.getBean()`、`FactoryBean.getObject()`；`@Bean` 方法本质就是工厂方法 |
| 工厂返回单例要注意什么 | 线程安全、状态污染；无状态产品可共享，有状态产品每次要新实例 |

### 9.4 自测题

**1. 工厂方法与简单工厂最本质的区别？**

<details><summary>参考答案</summary>

**工厂本身是否有子类多态**。简单工厂是一个类 + 条件分发，新增产品要改那个类；工厂方法是 `Factory` 接口 + 每个产品一个具体工厂，新增产品只新增类。前者违反 OCP，后者符合，代价是类数量翻倍。
</details>

**2. `Calendar.getInstance()` 是工厂方法模式吗？**

<details><summary>参考答案</summary>

不是。它是**静态工厂方法**（编码技巧）：一个静态方法内部决定返回哪个子类，没有"抽象工厂 + 具体工厂子类"的结构，也没有让子类决定创建。它平时说的"静态工厂"和 GoF 的"工厂方法模式"是两回事。
</details>

**3. 本项目的 `createMessage()` 为什么没有参数？**

<details><summary>参考答案</summary>

因为产品类型由"哪个具体工厂"决定，而不是由参数决定——这正是工厂方法与简单工厂的分界。如果产品需要配置（模板 ID、发件人），应该把配置放在**具体工厂的构造器**里，而不是加到 `createMessage` 的参数上，这样使用方不需要每次创建都传一遍。
</details>

**4. 新增微信消息，需要改哪些文件？**

<details><summary>参考答案</summary>

新增 `WeChatMessage`（产品）和 `WeChatMessageFactory`（工厂）两个文件，已有关类 0 处修改。`Client` 里加一行 `new NotificationService(new WeChatMessageFactory())` 属于装配代码，不是业务逻辑修改。可用"新增文件数 / 修改文件数"作为 OCP 的量化证据。
</details>

**5. 为什么 `NotificationService` 要用构造器注入工厂，而不是在方法里 `new`？**

<details><summary>参考答案</summary>

① 依赖倒置：服务只依赖 `MessageFactory` 抽象；② 可测试：测试可注入一个假工厂；③ 生命周期清晰：工厂在对象创建时确定，不在每次调用时变化；④ 避免服务内部出现 `if (type == ...) new ...` 的分发逻辑。
</details>

**6. `Message` 的两个参数都是 `String`，有什么风险？怎么消除？**

<details><summary>参考答案</summary>

风险是**参数顺序写反也编译通过**（原项目就踩了这个坑：接口 `(content, to)`，实现 `(to, content)`，实测输出把手机号和内容互换了）。消除办法：① 参数名清晰 + Javadoc 标注；② 用参数对象（`record MessageTarget(String address, String content)`）；③ 用不同类型包装（如 `PhoneNumber`、`Email`）。类型系统能防的错，不要靠注释防。
</details>

**7. 工厂方法能消除 `switch` 吗？**

<details><summary>参考答案</summary>

不能完全消除。它消除的是**使用方内部**按类型 `switch` 创建产品的代码；但"选哪个工厂"这件事仍然存在，只是转移到了装配层（`new`、配置、反射、DI 容器）。模式改变的是判断发生的位置和时机，不是消灭判断。
</details>

**8. 手写题：用工厂方法实现"图表导出"（柱状图/折线图，导出 PNG/SVG）。**

<details><summary>参考答案</summary>

先判断维度：图表类型和导出格式是两个维度，**严格说更适合桥接**。若只做"按类型创建图表"这一个维度，工厂方法是合适的：

```java
public interface Chart {
    void draw();
}

public class BarChart implements Chart {
    @Override public void draw() { System.out.println("绘制柱状图"); }
}

public class LineChart implements Chart {
    @Override public void draw() { System.out.println("绘制折线图"); }
}

public interface ChartFactory {
    Chart createChart();
}

public class BarChartFactory implements ChartFactory {
    @Override public Chart createChart() { return new BarChart(); }
}
```
**关键**：如果还要加"导出格式"，就不该再为格式加一层工厂，而应该把格式抽成实现维度（桥接）。**面试里能指出这一点，比写对工厂方法更加分。**
</details>

**9. 判断对错：工厂方法让使用方完全不需要知道具体产品类。**

<details><summary>参考答案</summary>

**部分对**。业务代码（`NotificationService`）确实不需要知道；但**装配代码**（`Client`、Spring 配置）必须知道用哪个具体工厂，否则无从创建。所以准确说法是："使用方**业务逻辑**不需要知道具体产品，装配层仍然需要"。这也是为什么很多项目用配置文件或注解把装配也隐藏起来。
</details>

**10. 工厂类只有一行 `return new Xxx()`，是不是可以去掉？**

<details><summary>参考答案</summary>

看需求。如果①产品无状态、无配置、②未来不需要替换、③没有人按"工厂"这个抽象来扩展，那确实可以用 `Supplier<Message>` 或方法引用替代，甚至直接 `new`。但如果框架需要"子类决定创建"（比如插件机制），这个类就是扩展点，不能省。**判断标准是"这个类是不是扩展点"，不是"它的行数"。**
</details>

### 9.5 面试官想听什么

**想听**：

- "子类决定创建哪个类"——说明你抓住了定义里的关键词。
- "简单工厂不是 GoF 模式"——说明你不把概念混着用。
- "工厂方法只解决一个产品，抽象工厂解决一族"——说明你知道升级边界。
- "选择工厂的责任没有消失，只是转移了"——说明你想清楚了模式的边界。
- "参数名是契约的一部分"（或类似的具体教训）——说明你真的写过、踩过。

**不想听**：

- 只背"定义一个接口让子类实现"。
- 把 `getInstance()`、`Builder` 都说成工厂方法。
- 说"工厂方法可以消除所有 if"。
- 说不出缺点，认为工厂方法一定比 `new` 好。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 角色 | 职责 |
|---|---|---|
| `Message.java` | Product | 消息接口，`send(to, content)` |
| `SmsMessage.java` | ConcreteProduct | 短信 |
| `EmailMessage.java` | ConcreteProduct | 邮件 |
| `InAppMessage.java` | ConcreteProduct | 站内信（"推送给用户"） |
| `WeChatMessage.java` | ConcreteProduct | 微信（扩展演示，包私有） |
| `MessageFactory.java` | Creator | 声明 `createMessage()` |
| `SmsMessageFactory.java` | ConcreteCreator | 创建短信 |
| `EmailMessageFactory.java` | ConcreteCreator | 创建邮件 |
| `InAppMessageFactory.java` | ConcreteCreator | 创建站内信 |
| `WeChatMessageFactory.java` | ConcreteCreator | 创建微信（扩展演示，包私有） |
| `NotificationService.java` | 使用方 | 注入工厂，创建 + 发送 |
| `Client.java` | 装配层 | 4 种组合演示 |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.creational.factorymethod.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/factorymethod/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.factorymethod.Client
```

### 10.3 本次修订记录

| 改动 | 原因 |
|---|---|
| `NotificationService` 构造器增加 `Objects.requireNonNull` | 工厂为 null 时应立即报错，而不是推迟到 `notifyUser()` |

> 原项目"接口与实现的参数顺序相反"的问题，已在重构阶段统一为 `send(String to, String content)`，不是本轮改动，特此说明。

---

相关文档：[模式对比 §创建型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
