# 相似模式对比：一张表说清「到底差在哪」

> 学习的最大障碍不是「某个模式不懂」，而是**两个模式看起来一样**。
> 本文只回答一件事：**遇到代码时，凭什么判断该用 A 而不是 B。**
> 每个对比都配一条「判别三问」和本项目里的真实对照文件。

## 如何使用本文

不要在第一次学习时就通读。**每学完一组就回来读对应的那一节**，效果最好。

每组的结构固定：

1. **一句话区别** —— 只说一句，说不清就是还没懂
2. **判别三问** —— 拿实际问题去问，答案唯一
3. **真实对照** —— 本项目中对应的代码

---

## 一、创建型：对象怎么造

### 1.1 单例 vs 静态工具类

**一句话区别**：单例是一个**对象**（可以有状态、可以被注入、可以实现接口），静态工具类只是一堆**函数**。

| 判别三问 | 单例 | 静态工具类 |
|---|---|---|
| 需要持有可变状态吗？ | 需要 | 不该有（有就是全局变量） |
| 需要实现接口 / 被 mock 替换吗？ | 需要 | 做不到 |
| 需要延迟初始化 + 释放吗？ | 可以 | 做不到 |

**本项目对照**：

- 单例：[`DoubleCheckLockSingleton`](../src/com/patterns/creational/singleton/DoubleCheckLockSingleton.java) 持有实例，五种写法各有取舍
- 静态工具：[`EditorLimits`](../src/com/patterns/behavioral/memento/EditorLimits.java) —— 纯静态常量 + 纯函数，构造器私有但**从不创建实例**。这类东西写成单例反而是过度设计

**关键判断**：如果一个类从头到尾没有 `this` 的用法，它就是工具类，不要套单例。

### 1.2 工厂方法 vs 抽象工厂

**一句话区别**：工厂方法造**一个**产品（一个工厂类对应一个产品），抽象工厂造**一族**产品（一个工厂类对应多个相互配套的产品）。

| 判别三问 | 工厂方法 | 抽象工厂 |
|---|---|---|
| 要造几种产品？ | 1 种 | ≥2 种，且必须配套 |
| 产品之间有「必须同族」的约束吗？ | 无 | 有（如 iOS 的 Button 不能配 Android 的 TextBox） |
| 新增一个产品种类时，要改几个地方？ | 加 1 个工厂 + 1 个产品 | 改工厂**接口** + 所有工厂实现 |

**本项目对照**：

- 工厂方法：[`MessageFactory`](../src/com/patterns/creational/factorymethod/MessageFactory.java) → `EmailMessageFactory` / `SmsMessageFactory` / `WeChatMessageFactory` / `InAppMessageFactory`，每个工厂只造一种 `Message`
- 抽象工厂：[`UIFactory`](../src/com/patterns/creational/abstractfactory/UIFactory.java) 同时声明 `createButton()` / `createTextBox()` / `createImage()`，`AndroidUIFactory` 与 `WebUIFactory` 各自产出一整套同族组件

**关键判断**：**产品族是否需要一致**。只有需要一致时才用抽象工厂；否则一个产品一个工厂方法就够了。

### 1.3 建造者 vs 多参数构造器 vs 工厂

**一句话区别**：工厂关心「造哪个」，建造者关心「怎么一步步造」。

| 判别三问 | 建造者 | 工厂 / 构造器 |
|---|---|---|
| 参数超过 5 个且大部分可选吗？ | 是 | 否 |
| 需要固定「组装顺序」并复用吗？ | 是（`Director`） | 不需要这层 |
| 造出来的对象需要「构造完就不可变」吗？ | 是 | 都可以 |

**本项目对照**：

- 建造者：[`ComputerBuilder`](../src/com/patterns/creational/builder/ComputerBuilder.java)（抽象）+ `GamingComputerBuilder` / `OfficeComputerBuilder`（具体）+ [`Director`](../src/com/patterns/creational/builder/Director.java)（固定组装顺序）
- `Client` 与 `Client2` 展示了**带 Director** 和**不带 Director** 两种用法 —— 后者就是纯链式写法

**关键判断**：如果参数只有 2-3 个，写个构造器，不要写 Builder。**Builder 的价值在「可选参数多 + 校验集中在 `build()`」**，不在「看起来专业」。

### 1.4 原型 vs 工厂

**一句话区别**：工厂是「按图纸造新的」，原型是「拿现成的复制一份」。

| 判别三问 | 原型 | 工厂 |
|---|---|---|
| 新对象的初始状态和某个现成对象高度相似吗？ | 是 | 不一定 |
| 创建成本高（要读文件、查库、算很久）吗？ | 是，复制更便宜 | 无所谓 |
| 需要「运行时注册」新类型吗？ | 可以（`DocumentRegistry`） | 需要改工厂代码 |

**本项目对照**：[`DocumentRegistry`](../src/com/patterns/creational/prototype/DocumentRegistry.java) 存一批模板 `DocumentPrototype`，每次按名字取一个再 `copy()`。

**关键判断**：原型模式最容易踩的坑不是「怎么复制」，是**深拷贝还是浅拷贝**。`WordDocument` 里的引用类型字段必须一起处理，否则两个对象共享同一个内部集合。

---

## 二、结构型：东西怎么拼

这一组是混淆重灾区。**四个模式全都在「包一层」**，区别在「包的目的」和「包的接口」。

### 2.1 总表：适配器 vs 装饰器 vs 代理 vs 外观

| 维度 | 适配器 Adapter | 装饰器 Decorator | 代理 Proxy | 外观 Facade |
|---|---|---|---|---|
| **包的目的** | 转接口 | 加能力 | 控访问 | 简化调用 |
| **是否实现同一接口** | **否**（转成目标接口） | **是** | **是**（通常） | **否** |
| **能否多层叠** | 一般一层 | 可以无限叠 | 一般一层 | 不适用 |
| **被包对象谁创建** | 外部传入 | 外部传入 | 代理自己创建 | 外观自己创建 |
| **调用方感知** | 感知不到原接口 | 感知不到被装饰者 | 以为在调真实对象 | 不知道有子系统 |
| **典型场景** | 老接口接入新系统 | 加日志/缓存/限流 | 懒加载/权限/远程 | 一键下单/一键启动 |

**判别三问**（按顺序问，能唯一定位）：

1. **调用方期望的接口，和被包对象的接口是同一个吗？**
   - 不是 → **适配器**
   - 是 → 继续问
2. **包装是为了「加功能」还是「控访问」？**
   - 加功能（且能任意叠加） → **装饰器**
   - 控访问（懒加载/权限/缓存，通常一层） → **代理**
3. **被包的是「一个对象」还是「一堆子系统」？**
   - 一堆子系统，且只暴露一个简化入口 → **外观**

**本项目对照**：

| 模式 | 文件 | 看点 |
|---|---|---|
| 适配器 | [`MediaAdapter`](../src/com/patterns/structural/adapter/MediaAdapter.java) | `AudioPlayer` 期望 `play()`，`AdvancedMediaPlayer` 只有 `playVlc()`/`playMp4()`，适配器在中间翻译 |
| 装饰器 | [`TextDecorator`](../src/com/patterns/structural/decorator/TextDecorator.java) + `BoldDecorator`/`ColorDecorator`/`ItalicDecorator`/`UnderlineDecorator` | 四个装饰器都 `implements TextComponent`，所以能任意组合叠加 |
| 代理（虚拟） | [`VirtualImageProxy`](../src/com/patterns/structural/proxy/VirtualImageProxy.java) | 真正加载推迟到第一次 `display()` |
| 代理（保护） | [`ProtectedImageProxy`](../src/com/patterns/structural/proxy/ProtectedImageProxy.java) | 检查权限后才转发 |
| 代理（缓存） | [`CachingImageProxy`](../src/com/patterns/structural/proxy/CachingImageProxy.java) | 命中缓存就不转发 |
| 代理（动态） | [`DynamicImageProxy`](../src/com/patterns/structural/proxy/DynamicImageProxy.java) + `ImageInvocationHandler` | 一个 handler 代理任意目标，运行时生成 |
| 外观 | [`UserServiceFacade`](../src/com/patterns/structural/facade/UserServiceFacade.java) | 把 `UserValidator` + `UserRepository` + `EmailNotifier` + `AuditLogger` 的固定顺序收敛成一个方法 |

**最常见的错判**：把「加日志」写成代理。日志是**横切能力**，不改变访问控制，且可能叠加多层（日志 + 缓存 + 限流），所以它是装饰器。组合示例里 [`TracePolicy`](../src/com/patterns/combined/orderservice/TracePolicy.java) 就是装饰器而非代理。

### 2.2 装饰器 vs 继承

**一句话区别**：继承是**编译期**定死的能力组合，装饰器是**运行期**可拼的能力组合。

| 判别三问 | 装饰器 | 继承 |
|---|---|---|
| 能力组合需要在运行时决定吗？ | 是 | 否 |
| 需要 N 种能力任意组合（2^N 种子类）吗？ | 是 | 会类爆炸 |
| 能力之间有顺序语义吗？ | 有（顺序影响结果） | 无此概念 |

**关键判断**：如果能力只有 1-2 种且永不变，用继承更简单。装饰器的收益在「组合数」上，不在「优雅」上。

> 本节只比「装饰器 vs 继承」。把「组合 vs 继承」当作**通用复用机制**的完整对比（四个现实场景映射、继承的四个硬伤、决策五问、五个常见误用）见 [07-composition-vs-inheritance.md](07-composition-vs-inheritance.md)。

### 2.3 桥接 vs 策略

这是**最容易被混的一对**，因为两者都是「持有一个接口字段」。

**一句话区别**：策略是**替换算法**（同一维度上的不同实现），桥接是**组合两个独立维度**（抽象侧 × 实现侧）。

| 判别三问 | 桥接 | 策略 |
|---|---|---|
| 有几个**独立变化**的方向？ | 2 个（如「消息类型」× 「发送渠道」） | 1 个（如「折扣算法」） |
| 抽象侧自己也有子类层次吗？ | 有（`NormalMessage`/`ScheduledMessage`/`UrgentMessage`） | 通常没有（`PriceCalculator` 只是一个宿主） |
| 换个实现会改变「抽象侧的业务语义」吗？ | 不会（换渠道不改变消息类型） | 会（换策略就换了算法） |

**本项目对照**：

- 桥接：[`Message`](../src/com/patterns/structural/bridge/Message.java)（普通/定时/紧急 三种）× [`MessageSender`](../src/com/patterns/structural/bridge/MessageSender.java)（邮件/短信/微信/钉钉 四种）= 3×4 = 12 种组合，只用 3+4 = 7 个类
- 策略：[`PriceCalculator`](../src/com/patterns/behavioral/strategy/PriceCalculator.java) 只持有一个 `DiscountStrategy`，没有子类层次

**关键判断**：**抽象侧有没有子类**。有 → 桥接；没有，只是一个持有策略的普通类 → 策略。

### 2.4 享元 vs 缓存

**一句话区别**：享元是**同一个对象被多处共享**（省内存），缓存是**同一个结果被多次复用**（省时间）。

| 判别三问 | 享元 | 缓存 |
|---|---|---|
| 共享的是「对象」还是「计算结果」？ | 对象 | 结果 |
| 对象有「必须由外部传入」的状态吗？ | 有（外部状态） | 无此概念 |
| 共享对象的生命周期？ | 与工厂同长，通常不淘汰 | 通常有 TTL / LRU 淘汰 |

**本项目对照**：[`GlyphFactory`](../src/com/patterns/structural/flyweight/GlyphFactory.java) 按「字符 + 字体」缓存 `Glyph`；[`CharacterView`](../src/com/patterns/structural/flyweight/CharacterView.java) 持有位置和颜色（外部状态），每次渲染时传给 `Glyph`。

**关键判断**：**外部状态**是享元的标志性特征。如果一个"缓存"里的对象需要调用方每次传入一部分数据才能工作，它就是享元。

### 2.5 组合 vs 装饰器

**一句话区别**：组合是**树**（一对多，节点可以有很多子节点），装饰器是**链**（一对一，只包一个）。

| 判别三问 | 组合 | 装饰器 |
|---|---|---|
| 一个节点持有几个同类对象？ | 多个（子节点列表） | 恰好 1 个 |
| 存在「叶子」概念吗？ | 有 | 无 |
| 操作需要向下递归聚合吗？ | 是（总大小 = 所有叶子之和） | 否（逐层包装后透传） |

**本项目对照**：[`Directory`](../src/com/patterns/structural/composite/Directory.java) 持有 `List<FileSystemNode>`，`FileLeaf` 没有子节点。两者共同实现 `FileSystemNode`，所以 `totalSize()` 可以递归求和。

---

## 三、行为型：逻辑放在哪

### 3.1 策略 vs 状态

**一句话区别**：策略由**外部**选择、彼此**不知道对方**；状态由**内部**流转、知道**下一个是谁**。

| 判别三问 | 策略 | 状态 |
|---|---|---|
| 谁来切换到下一个实现？ | 调用方 / 工厂 | 状态自己或上下文 |
| 实现之间需要知道彼此吗？ | **绝对不需要** | **需要**（转移关系） |
| 状态转移是一等公民吗？ | 不是 | 是（`TransitionTable`） |

**本项目对照**：

- 策略：[`DiscountStrategyFactory.forLevel(UserLevel)`](../src/com/patterns/behavioral/strategy/DiscountStrategyFactory.java) 根据用户等级选策略，四个策略互不引用
- 状态：[`TransitionTable`](../src/com/patterns/behavioral/state/TransitionTable.java) 集中登记「哪个状态下哪个动作合法、转移到哪个状态」，`Order` 只是执行转移

**关键判断**：**如果实现类里出现 `setState(...)` / `context.xxx()` 这种把对方推进下一状态的动作，就是状态；如果实现类只做纯计算，就是策略。**

### 3.2 策略 vs 命令

**一句话区别**：策略是「**算法**可以替换」，命令是「**请求**可以被当作对象传递、排队、记录、撤销」。

| 判别三问 | 策略 | 命令 |
|---|---|---|
| 需要把请求「存起来稍后执行」吗？ | 不需要 | 需要（`TaskScheduler` 队列） |
| 需要撤销 / 重做吗？ | 不需要 | 需要（`CommandHistory` + `UndoableCommand`） |
| 需要把多个请求打包成一个吗？ | 不需要 | 需要（`MacroCommand`） |

**本项目对照**：[`TaskScheduler`](../src/com/patterns/behavioral/command/TaskScheduler.java) 把 `Command` 排队、执行、记入 `CommandHistory`；`UpdateConfigCommand` 记旧值以便 `undo()`。

**关键判断**：**命令多了一个「生命周期」**。策略对象被调用完就结束；命令对象会被创建、排队、执行、记录、撤销。

### 3.3 命令 vs 模板方法

**一句话区别**：模板方法是**继承**（子类填空），命令是**组合**（对象排队）。

| 判别三问 | 模板方法 | 命令 |
|---|---|---|
| 变化点通过「子类覆盖方法」实现吗？ | 是 | 否，通过不同命令类实现 |
| 需要运行时动态增删步骤吗？ | 做不到 | 可以 |
| 需要记录、撤销、重放吗？ | 不涉及 | 是核心能力 |

**本项目对照**：[`OrderProcessTemplate`](../src/com/patterns/behavioral/templatemethod/OrderProcessTemplate.java) 的 `processOrder()` 是 `public final`，骨架固定，子类只能填 `protected abstract` 步骤；命令则是完全独立的对象。

### 3.4 责任链 vs 装饰器

**一句话区别**：装饰器**每一层都执行**（并叠加结果），责任链**命中即止**（或继续传递，但每层决策独立）。

| 判别三问 | 责任链 | 装饰器 |
|---|---|---|
| 每层都要执行吗？ | 不一定，可中断 | 每一层都执行 |
| 需要「谁来处理」的语义吗？ | 需要 | 不需要 |
| 层与层之间有「优先级/顺序跳过」吗？ | 有 | 无（只有叠加顺序） |

**本项目对照**：

- 责任链：[`Handler`](../src/com/patterns/behavioral/chainofresponsibility/Handler.java) 抽象类持有唯一 `next`，`handle()` 是 `final`，子类只实现 `tryHandle()` 返回 `Optional<R>`；[`ApprovalChain`](../src/com/patterns/behavioral/chainofresponsibility/approval/ApprovalChain.java) 负责组装与兜底
- 装饰器：[`TextDecorator`](../src/com/patterns/structural/decorator/TextDecorator.java) 每一层都执行并包装结果

**注意**：同目录下的 `discount/DiscountPipeline` **不是**责任链实现——它的类注释明确说「没有 `next`、没有 `Handler`、没有 `setNext()`」，而是「列表 + 遍历全部执行」的变体，每条规则无论命中与否都记一笔轨迹。这恰好展示了「需求不允许中断时，不要硬套责任链」。

**关键判断**：**能否跳过/中断**。能中断的是责任链；每层都必须跑的是装饰器。

### 3.5 责任链 vs 状态

**一句话区别**：责任链的节点**互不知道彼此**、由外部组装顺序；状态之间**知道转移关系**、由转移规则驱动。

这条容易混，因为两者都是「一个对象持有同类型引用」。区别在于：责任链的 `next` 是**固定装配**的，一旦装配完就不再变；状态的「下一个」是**运行时算出来**的。

### 3.6 观察者 vs 中介者

**一句话区别**：观察者是**一对多广播**（发布者不认识订阅者的具体类型），中介者是**多对多转接**（每个同事只认识中介者）。

| 判别三问 | 观察者 | 中介者 |
|---|---|---|
| 通信方向？ | 一个源 → N 个接收者 | N 个同事 → N 个同事 |
| 接收者之间需要互相通信吗？ | 不需要 | 需要，但通过中介者转 |
| 谁掌握路由规则？ | 无路由，全广播 | 中介者掌握全部路由 |

**本项目对照**：

- 观察者：[`ContentCreator`](../src/com/patterns/behavioral/observer/ContentCreator.java) 持有 `Map<String, Observer>`，一次发布通知所有人
- 中介者：[`CollaborationHub`](../src/com/patterns/behavioral/mediator/CollaborationHub.java) 接口 + [`AfterSalesHub`](../src/com/patterns/behavioral/mediator/AfterSalesHub.java) 实现：内部用 `Map<Role, Participant>` 登记五类角色，用 `route(Role, MessageKind)` 的 `switch` 表达式决定一条消息发给谁

**关键判断**：**接收者要不要回话**。单向广播 → 观察者；需要互相协作 → 中介者。

### 3.7 观察者 vs 发布订阅（pub/sub）

**一句话区别**：观察者是**进程内、直接引用**；发布订阅是**通过 broker（消息中间件）解耦**，发布者根本不知道有谁在订阅。

| 判别三问 | 观察者 | 发布订阅 |
|---|---|---|
| 发布者知道订阅者对象吗？ | 知道（持有引用） | 不知道（只发到 topic） |
| 需要跨进程 / 持久化 / 重试吗？ | 不需要 | 需要 |
| 生命周期一致吗？ | 同进程同生命周期 | 完全独立 |

**重要提醒**：**GoF 23 种里没有「发布订阅」**。它是观察者的分布式演化版，常被面试官用来追问「观察者模式有什么局限」。本项目的观察者是纯进程内实现。

### 3.8 迭代器 vs for 循环 vs Stream

**一句话区别**：迭代器把「遍历算法」从聚合类里**抽出来变成对象**，从而支持多种遍历方式且不修改聚合类。

| 判别三问 | 迭代器 | for / Stream |
|---|---|---|
| 需要多种遍历顺序（DFS/BFS/过滤）吗？ | 需要 | 一种就够 |
| 聚合类的内部结构需要隐藏吗？ | 需要 | 无所谓 |
| 需要「遍历状态」可保存/恢复吗？ | 可以 | 做不到 |

**本项目对照**：[`DepthFirstIterator`](../src/com/patterns/behavioral/iterator/DepthFirstIterator.java) / [`BreadthFirstIterator`](../src/com/patterns/behavioral/iterator/BreadthFirstIterator.java) / [`FilteringIterator`](../src/com/patterns/behavioral/iterator/FilteringIterator.java) 三种遍历共用同一个 `Workflow` 树，聚合类一行都不用改。

**关键判断**：业务代码里 99% 的情况用 for 或 Stream 就够。**只有当「遍历方式本身是需求」时才需要迭代器模式。**

---

## 四、跨类别的三组对照

### 4.1 组合 vs 访问者

**一句话区别**：组合解决「**结构**怎么组织」，访问者解决「**操作**怎么加而不改结构」。

| 判别三问 | 组合 | 访问者 |
|---|---|---|
| 关注点在结构还是操作？ | 结构 | 操作 |
| 新增一种「操作」要改元素类吗？ | 会（要加方法） | 不会（加一个访问者） |
| 新增一种「元素」要改什么？ | 加一个类即可 | 改访问者接口 + 所有实现 |

**本项目对照**：`DocumentBundle`（组合结构）+ `DocumentVisitor`（访问者接口），`HtmlExportVisitor` / `PlainTextVisitor` / `StatisticsVisitor` 三种操作互不干扰。

**重要**：这两个模式经常**配合使用**，不是二选一。

### 4.2 桥接 vs 抽象工厂

**一句话区别**：抽象工厂解决「**怎么创建**一族配套对象」，桥接解决「**怎么组合**两个独立维度」。

| 判别三问 | 桥接 | 抽象工厂 |
|---|---|---|
| 关注创建还是关注结构？ | 结构 | 创建 |
| 两个维度是「运行时组合」还是「编译期选一族」？ | 运行时组合（可以今天邮件明天短信） | 编译期选定（iOS 就一直是 iOS） |
| 需要运行时替换实现吗？ | 需要 | 一般不需要 |

### 4.3 模板方法 vs 策略

**一句话区别**：模板方法用**继承**固定骨架、开放步骤；策略用**组合**整体替换算法。

| 判别三问 | 模板方法 | 策略 |
|---|---|---|
| 变化的是「整体算法」还是「某几个步骤」？ | 几个步骤 | 整体算法 |
| 骨架必须固定吗？ | 是（`final`） | 无骨架概念 |
| 需要在运行时换算法吗？ | 不能（继承已定） | 能 |

**本项目对照**：[`OrderProcessTemplate`](../src/com/patterns/behavioral/templatemethod/OrderProcessTemplate.java) 固定「库存检查 → 支付 → 发货 → 通知」四步，其中库存检查由钩子 `needsStockCheck()` 控制是否执行；[`PreSaleOrder`](../src/com/patterns/behavioral/templatemethod/PreSaleOrder.java) 通过覆盖 6 个方法使同一副骨架变成预售流程（跳过库存、改写支付、改写发货、重写通知）。而策略是整体换掉 `discount()`。

**一句话总结这一对**：**模板方法开放「填空题」，策略开放「选择题」。**

---

## 五、五组「同名不同物」的坑

| 易混说法 | 真相 |
|---|---|
| 「策略模式就是 if/else 的替代品」 | 错。分支 ≤3 且稳定时 if/else 更好读。策略的价值是**可独立测试 + 可运行时替换** |
| 「单例就是全局变量」 | 错。单例可以延迟初始化、可以释放、可以实现接口；全局变量是**可写的公共状态** |
| 「工厂模式 = 简单工厂」 | 错。简单工厂不是 GoF 23 种之一；工厂方法靠**继承**延迟创建，抽象工厂靠**组合**打包一族 |
| 「代理和装饰器可以互换」 | 错。代理**自己创建**目标（调用方不知道目标存在），装饰器**由外部传入**目标（调用方明确知道在叠加） |
| 「享元就是对象池」 | 错。对象池是**借还**（同一时刻一个对象只服务一个调用方），享元是**同时共享**（多个调用方读同一个对象） |

---

## 六、一张决策流程图（文字版）

```
遇到一个「会变」的地方
│
├─ 变的是「对象的创建」？
│   ├─ 只造 1 种产品 → 工厂方法
│   ├─ 造一族配套产品 → 抽象工厂
│   ├─ 造的步骤多、可选参数多 → 建造者
│   └─ 造的成本高、和现成对象相似 → 原型
│
├─ 变的是「对象的结构」？
│   ├─ 接口不兼容，要转换 → 适配器
│   ├─ 要加能力，且可能叠加 → 装饰器
│   ├─ 要控访问（懒加载/权限/缓存）→ 代理
│   ├─ 要简化一堆子系统的调用 → 外观
│   ├─ 是树形结构，要统一操作 → 组合
│   ├─ 两个维度独立变化 → 桥接
│   └─ 大量重复的小对象 → 享元
│
└─ 变的是「行为 / 算法 / 协作」？
    ├─ 整体算法可替换 → 策略
    ├─ 骨架固定、步骤可变 → 模板方法
    ├─ 请求要排队/记录/撤销 → 命令
    ├─ 多个处理器按序尝试、可中断 → 责任链
    ├─ 行为随状态变化、状态有转移规则 → 状态
    ├─ 一对多通知 → 观察者
    ├─ 多对多通信、需要路由 → 中介者
    ├─ 遍历方式本身是需求 → 迭代器
    ├─ 需要保存/恢复历史状态 → 备忘录
    ├─ 元素稳定、操作频繁增加 → 访问者
    └─ 有一套小文法要解释 → 解释器
```

---

## 七、还拿不准时的三个办法

1. **看变化点在哪**：模式是用来隔离变化的。写不出「哪里会变」，就不要用模式。
2. **看类的数量**：如果一个方案让类从 3 个变成 12 个，而需求只有 2 个分支，这个方案就是过度设计。
3. **看删除的成本**：假设半年后要删掉这个模式，需要改几个文件？如果答案是「全部」，说明抽象边界选错了。

各模式的详细推导见对应目录的 `README.md`；推荐学习顺序见 [01-learning-path.md](01-learning-path.md)。
