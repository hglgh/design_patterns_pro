# 项目优化记录：从 22 种模式到 23 种 + 组合示例

> 本文记录**实际做了什么改动、为什么这么改、验证结果如何**。
> 与 [00-source-review.md](00-source-review.md) 的分工：那份是**改造前的计划**，这份是**改造后的执行记录**，包含计划与实际的偏差及原因。
> 全部数据来自 `javac` / `java` 实测，未做估算。
>
> **2026-10-27 说明**：本文记录的是「22 → 23 种模式」那一轮重构，文件数与行数是当时基线。之后项目新增了 23 个入门 Demo、23 份 `BEGINNER.md` 与两个组合案例；当前规模（310 文件 / 35614 行 / 558 class）见 [09-beginner-refactor-report.md](09-beginner-refactor-report.md) 与根 README。
>
> **2026-11-08 说明**：那轮新增的 `build.bat` / `build.sh` 已删除，项目改用标准 `javac` / `java`（见根 README）。下文提到这两个脚本处仅作历史记录。

## 0. 总览

| 项 | 原项目 | 重构后 | 变化 |
|---|---|---|---|
| 已实现模式数 | 22（缺单例） | **23** | +1 |
| Java 文件数 | 163 | **263** | +100 |
| 代码物理行数 | 3715 | **28193** | ×7.6 |
| 编译产物 class 数 | — | **326** | — |
| 编译告警 | 未记录 | **0**（`-Xlint:all`） | — |
| 包命名 | `creationtype` / `structuraltype` / `BehaviorType` / `CombineApplicationExamples` | `com.patterns.creational` / `structural` / `behavioral` / `combined` + `util` | 全小写 |
| 构建方式 | 仅 IntelliJ `.iml` | 直接 `javac` 编译（零依赖） | 无需 IDE |
| 学习文档 | 0 份 | **24 份 README，合计 89964 行**（另加顶层 README 与 5 份 docs） | +24 |
| 组合应用 | 9 文件 217 行 | **20 文件 3547 行** | 重写并加旧实现对照 |

> 263 = 24 个模块 261 个文件 + `com/patterns/util` 2 个工具类（`Money` 91 行、`DisplayWidth` 138 行）。

## 1. 全局改造（7 项）

| # | 改动 | 原状 | 现状 | 为什么 |
|---|---|---|---|---|
| G1 | 包名规范化 | `BehaviorType` 大驼峰、`creationtype` 与 `structuraltype` 风格不一 | `com.patterns.{creational\|structural\|behavioral\|combined}` | 违反 Java 包命名规范；改名后才能作为库被外部引用 |
| G2 | 补齐单例 | 完全没有 | `creational/singleton` 6 文件 263 行 | 23 种 GoF 模式不完整，且单例是面试第一问 |
| G3 | 可见性放开 | `Folder`、`Order`、4 个状态类等 package-private | 全部 `public`（或 `sealed` + `permits`） | 教学项目要让学习者能在 `Client` 之外复用 |
| G4 | Javadoc 标准化 | `@ClassName/@Package/@Author/@Create` 自定义标签 | 标准 `@author`/`@since`/`@param`/`@return`/`@throws` | 自定义标签无法被 IDE 与 `javadoc` 识别 |
| G5 | 每模式一份 README | 无 | 24 份，统一九大模块 | 代码只能说明「怎么写」，说明不了「为什么」 |
| G6 | 根 README | 无 | `README.md`（含 `javac` / `java` 编译运行说明） | 原项目无法脱离 IDEA 运行 |
| G7 | 新增 `util` 包 | 各模块自己拼金额/宽度字符串 | `Money`（金额格式与舍入）、`DisplayWidth`（中文宽度对齐） | 输出对齐与金额精度在各模式中重复出现，抽出来才能保证一致 |

## 2. 逐模块台账

「原项目」列只统计该模式实际存在的源文件；「重构后」列为实测值。

### 创建型（5）

| 模式 | 原项目 | 重构后 | 关键改动 |
|---|---|---|---|
| 单例 | **无** | 6 文件 / 263 行 | 从零新增：饿汉、懒汉、DCL、静态内部类、枚举 5 种写法 + `SingletonClient` 演示 |
| 工厂方法 | 10 文件 | 12 文件 / 225 行 | `Message.send(String to, String content)` 参数顺序与实现对齐；`Main` → `Client` 循环演示三种工厂；`NotificationService` 由 record 改 `final class` |
| 抽象工厂 | 13 文件 | 14 文件 / 297 行 | `ui/` 更名为 `web/`；`Client` 同时演示 Android 与 Web 两个产品族；补「产品族 vs 产品等级」对照 |
| 建造者 | 7 文件（两个 `Computer`） | 6 文件 / 351 行 | 删除重复的 `builder.Computer`；`ComputerBuilder` 由抽象类改接口；新增 `OfficeComputerBuilder`；`Director` 保留但 `Client` 同时演示「不用 Director」的链式写法；后来又把 `Client2` 并入 `Client`，并在每轮演示前重建 `Director`，避免跨轮复用带来的状态残留 |
| 原型 | 3 文件（`DocumentPrototype extends Cloneable`） | 4 文件 / 242 行 | 接口不再 `extends Cloneable`；`WordDocument.copy()` 对 `List<String> images` 深拷贝；新增 `DocumentRegistry` 原型注册表；`getImages()` 改为返回 `List.copyOf`，不再把内部集合泄漏给外部 |

### 结构型（7）

| 模式 | 原项目 | 重构后 | 关键改动 |
|---|---|---|---|
| 适配器 | 7 文件 | 7 文件 / 172 行 | `MediaAdapter` 用 `Map<String, AdvancedMediaPlayer>` 预建播放器，不再每次 `play()` 都 `new`；`DefaultAudioPlayer` 保留 `if/else` 作为「未被适配的旧代码」对照 |
| 桥接 | 8 文件 | 10 文件 / 214 行 | 保留 `Message` × `MessageSender` 双维度；新增 `ScheduledMessage` 与 `DingTalkSender`，把 2×3 扩到 3×4，直观展示两个维度独立扩展 |
| 组合 | 8 文件（demo1 + demo2） | 4 文件 / 365 行 | 两个 demo 合并；`File` → `FileLeaf`、`Folder` → `Directory`，避免与 `java.io.File` 冲突；采用**安全式**（`add`/`remove` 只声明在 `Directory`） |
| 装饰器 | 6 文件 | 8 文件 / 408 行 | 新增 `ItalicDecorator`、`UnderlineDecorator`；`TextDecorator` 字段由 `protected` 改 `private final` + `protected` 访问器 |
| 外观 | 5 文件（1 个 DAO） | 8 文件 / 635 行 | 子系统从 1 个 DAO 扩到 4 个（校验 / 仓储 / 通知 / 审计）；`UserServiceFacade` 固定「校验 → 落库 → 通知 → 审计」顺序 |
| 享元 | 5 文件 | 6 文件 / 469 行 | `GlyphFactory` 的 key 由**全部内部状态**（symbol + font）拼成，用 `computeIfAbsent` 一次查找；输出享元池命中率，展示复用效果 |
| 代理 | 4 文件（仅虚拟代理） | 8 文件 / 737 行 | `RealImage` 由 record 改普通类（构造器有加载副作用）；新增 `ProtectedImageProxy`（权限）、`CachingImageProxy`（缓存）、`DynamicImageProxy` + `ImageInvocationHandler`（JDK 动态代理） |

### 行为型（11）

| 模式 | 原项目 | 重构后 | 关键改动 |
|---|---|---|---|
| 责任链 | 8 文件（链表节点式） | 14 文件 / 1743 行 | 用 `Handler<C,R>` 抽象类 + `final handle()` + `Optional<R> tryHandle()` 重写；`ApprovalChain` 负责组装与兜底。另有 `discount/DiscountPipeline` 作为**反例**：需求不允许中断时，明确不用责任链 |
| 命令 | 6 文件（无 Receiver） | 16 文件 / 1923 行 | 引入 Receiver（`EmailService`/`BackupService`/`ReportService`/`ConfigService`）；拆出 `UndoableCommand`；新增 `MacroCommand`（失败逆序补偿）与 `CommandHistory`、`TaskScheduler` 重试 |
| 解释器 | 12 文件 / 257 行 | 16 文件 / 1987 行 | `Context` 改 `Map<String, Value>`；新增 `Lexer`/`Parser`/`TokenType`，支持 `&&`/`\|\|`/`!`/比较/四则/括号；`ExpressionException` 带列号定位 |
| 迭代器 | 4 文件 / 109 行 | 11 文件 / 1807 行 | `Iterator<T>` 泛型化 + `Workflow implements Iterable`；改用 `ArrayList` 不再静默丢弃；新增 `DepthFirstIterator`/`BreadthFirstIterator`/`FilteringIterator` 三种遍历 |
| 中介者 | 5 文件 | 13 文件 / 1623 行 | 从「聊天室」升级为「售后协作中心」：`Map<Role, Participant>` 登记五类角色，`route(Role, MessageKind)` 用 `switch` 表达式穷尽路由 |
| 备忘录 | 4 文件 | 13 文件 / 1590 行 | `SnapshotHistory` 用 undo/redo 两个 `ArrayDeque`；新增 `EditorLimits`（正文 200000 字符上限、撤销 100 层）、`SnapshotMetadata`、`HistoryListener` 与 `SteppedClock` |
| 观察者 | 6 文件（`Followable` 职责混乱） | 11 文件 / 1535 行 | 重构为 `Subject`/`Observer`/`Event`/`EventType`；用 `LinkedHashMap<String, Observer>` 按 id 去重并保序；通知前拷贝快照，避免 `ConcurrentModificationException` |
| 状态 | 7 文件 / 130 行（全 package-private） | 12 文件 / 1951 行 | 全部 `public`；`request()` → `OrderAction`；新增 `TransitionTable` 把「状态 × 动作 → 新状态」集中成表，`HistoryEntry` 记录每次转移 |
| 策略 | 6 文件 | 10 文件 / 1537 行 | `PriceCalculator` 构造器注入策略（不再有「已 new 但未 setStrategy」的中间态）；新增 `NoDiscountStrategy` 空对象；新增 `DiscountStrategyFactory.forLevel(UserLevel)` |
| 模板方法 | 4 文件 | 6 文件 / 1780 行 | 保留 `public final processOrder()`；补钩子 `needsStockCheck()`/`beforePay()`/`deliveryProofLabel()`/`notifyCustomer()`；新增 `PreSaleOrder` 演示「同骨架不同流程」 |
| 访问者 | 16 文件（主 demo 7 + CalculatePrice 9） | 26 文件 / 2563 行 | 主 demo 拆 `document` 包（密封元素 + 泛型 `DocumentVisitor<R>`）；`CalculatePrice` 拆 `pricing` 包；新增 `StatisticsVisitor`、`PlainTextVisitor`、`SavingsVisitor`、`ReceiptVisitor` 四类操作 |

### 组合应用（1）

| 模块 | 原项目 | 重构后 | 关键改动 |
|---|---|---|---|
| 订单折扣系统 | 9 文件 / 217 行 / 7 问题 | 20 文件 / 3547 行 | 四模式分工重划：策略=单一减免算法、装饰器=封顶与轨迹、责任链=顺序/跳过/终止、工厂=配置文本→对象图。新增 `LegacyOrderService`（210 行）作为「不用模式」的对照，同一批需求可直接跑出对比输出 |

## 3. 计划与实际的 9 处偏差

这一节记录**没有按 00 号文档原计划做**的地方，以及原因。比「按计划完成」更有价值的是**为什么计划需要改**。

| # | 原计划 | 实际做法 | 原因 |
|---|---|---|---|
| 1 | 外观新增 `UserCache` | 改为 `EmailNotifier` + `AuditLogger` + `InMemoryUserRepository` | 加缓存会掩盖 DAO 的真实调用路径，学习者看不清「外观协调了几个子系统」；通知 + 审计是**有先后顺序**的副作用，才能体现外观「固化调用顺序」的价值 |
| 2 | 享元用 `ConcurrentHashMap` | 用 `HashMap` + `computeIfAbsent` | **这个取舍后来被实测推翻了。** 本轮补做 16 线程 × 3000 轮压力测试：`HashMap + computeIfAbsent` 有 **5/3000 轮**出现重复创建，`ConcurrentHashMap` 版为 **0/3000**。结论保留在代码里（作为反面教材），修法、探针与数据写在 flyweight README §7.3 与 §8.3 诚实清单第 1 条 |
| 3 | `DefaultAudioPlayer` 改用 `Map` | `MediaAdapter` 用 `Map`，`DefaultAudioPlayer` 保留 `if/else` | 如果旧代码也写得很优雅，就**没有「需要适配」的动机**了。保留 `if/else` 才能让「新增格式要改哪里」的对比成立 |
| 4 | 迭代器新增 `ReverseIterator` | `DepthFirstIterator` / `BreadthFirstIterator` / `FilteringIterator` | 逆序遍历在数组上体现不出价值；树形审批流的 DFS/BFS 才能说明「遍历算法独立于容器」——同一个 `Workflow`，三种走法，容器一行不改 |
| 5 | 访问者新增 `WordCountVisitor` | `StatisticsVisitor` + `PlainTextVisitor` | 一个访问者同时产出页数、单元格数、元素分布等多个指标，更能体现「访问者可以携带累积状态」 |
| 6 | 备忘录「简化 `History`」 | 重写为 `SnapshotHistory`（undo/redo 双 `ArrayDeque`）+ `EditorLimits` + 监听器 | 原项目的真正问题不是「双栈绕」，而是**没有边界**：不限条数、不限正文长度、快照可变。只简化逻辑等于没修到根因 |
| 7 | 命令的 `Task` 增加 `undo()` | 拆出独立接口 `UndoableCommand` | 不是每个命令都能撤销。把 `undo()` 放进 `Task` 会逼 `MacroCommand`、`GenerateReportCommand` 写一个**骗人的空实现** |
| 8 | 观察者「删除 `Followable`」 | 整体重构为 `Subject`/`Observer`/`Event`/`EventType` | 原项目问题不止接口职责：没有事件类型（订阅者无法按需过滤）、没有投递结果（发失败无人知晓）、没有去重（同一观察者重复注册） |
| 9 | 组合应用新增 `OrderServiceTest` | 用 `Client` 十段 Demo + 三次运行哈希比对替代 | 项目承诺**零依赖**，不引入 JUnit。可复现性用「同一份输出 + SHA256 三次一致」保证，效果等价且不增加构建负担 |

## 4. 原项目缺陷取证汇总

每条缺陷都不是「读代码觉得有问题」，而是**写探针程序实际跑出来**的结果。详细取证见各模式 README 的 §2。

| 模块 | 原项目规模 | 取证到的缺陷数 | 典型缺陷 |
|---|---|---|---|
| 责任链 | 8 文件 | 7 | 链尾无人处理时返回原价且无提示；`Order.amount` 被多节点反复修改，中间态不可追溯 |
| 命令 | 6 文件 | 6 | 无 Receiver，命令内联业务；无 undo；失败后无法回滚已执行步骤 |
| 解释器 | 12 文件 / 257 行 | 10 | `RuleParser` 只按 `&&` 切分且写死 `>` / `<`；字段名硬编码 `"age"`；无括号、无优先级 |
| 迭代器 | 4 文件 / 109 行 | 8 | `next()` 返回 `Object`；超容量静默丢弃；无 fail-fast；只有一种遍历顺序 |
| 中介者 | 5 文件 | 7 | 用户直接持有其他用户引用；新增角色要改所有用户类；消息无类型 |
| 备忘录 | 4 文件 | 10 | 快照无条数上限；正文无长度限制；撤销逻辑绕；无 redo 边界 |
| 观察者 | 6 文件 / 144 行 | 7 | `User` 接口缺 `addFollower`，导致必须 `instanceof ConcreteUser` 强转；无事件类型；无投递结果 |
| 状态 | 7 文件 / 130 行 | 7 | 全部 package-private；`request()` 语义模糊；转移散落各状态类，无法一览 |
| 模板方法 | 4 文件 | 5 | 无钩子方法；库存为 0 仍继续支付发货（真实超卖路径） |
| 访问者 | 13 文件 / 348 行 | 8 | `PrintListVisitor.getTotalPrice()` 返回 0；元素类与访问者耦合；无第二种操作对比 |
| 组合应用 | 9 文件 / 217 行 | 7 | 工厂与责任链职责重叠；折扣可叠加到负数；无轨迹，出问题查不到哪一步 |

合计 **72 条**已取证缺陷。每条的复现方式与真实输出都在对应 README 中。

## 5. 十种可复用的重构手法

这些手法在多个模块中反复出现，可以直接迁移到业务代码。

| # | 手法 | 用在哪 | 解决了什么 |
|---|---|---|---|
| 1 | **record 只用于纯数据** | `RealImage`、`ConcreteFollower` 都从 record 改回普通类 | record 语义是不可变数据载体；构造器里做 I/O 或承载可变状态会让语义失真 |
| 2 | **不可变上下文 + 校验前置** | `OrderContext`、`Value`、`ApprovalContext` | 参数校验集中在构造器，方法体内不再防御性判空 |
| 3 | **`sealed` + `switch` 穷尽** | `DiscountDecision`、`FileSystemNode`、`Value` | 新增子类型时编译器会强制你处理，而不是运行时 `default` 静默吞掉 |
| 4 | **契约写进 Javadoc** | `DiscountPolicy.discount()` 写明 `0 ≤ 减免 ≤ price`、两位小数 | 接口的约束不写下来，实现者一定会越界 |
| 5 | **`final` 模板方法** | `OrderProcessTemplate.processOrder()`、`Handler.handle()`、`DiscountHandler.process()` | 骨架一旦可被子类覆盖，模板方法就失效了 |
| 6 | **`Optional<R>` 表达「不处理」** | `Handler.tryHandle()` | `boolean` 表达不了结果，`null` 分不清「不处理」和「处理结果为空」 |
| 7 | **集合返回前 `List.copyOf`** | `DiscountResult`、`DocumentBundle` | 调用方拿不到内部集合的写权限，不可能从外部破坏不变式 |
| 8 | **方法名表达真实语义** | `MacroCommand.rollback()` 而非 `undo()`；`ChainState.markGroup()` 而非 `markUsed()` | 名字说谎比没有名字更危险——`undo` 会让人以为一步能回到执行前 |
| 9 | **异常消息带修复指引** | `PolicyFactory` 的 `helpText()`、`Parser` 的列号定位 | 报错只说「不合法」等于没报；要告诉调用方**怎么改才对** |
| 10 | **空对象替代 `null`** | `NoDiscountStrategy`；组合示例的 `DiscountPipeline.empty()` | 调用方不需要判空，也就不会漏判 |

## 6. 未采纳的三个方案

| 方案 | 为什么没做 |
|---|---|
| 引入 JUnit / Maven | 任务要求「优先原生 Java、不依赖框架」。用 `Client` + 哈希比对达到同等可复现性 |
| 把 23 种模式合并成一个「电商系统」 | 会让每个模式的边界被业务代码淹没。**独立 Demo + 一个组合示例**是更合适的分层 |
| 给所有类补 `equals`/`hashCode` | 只有值对象需要。给有身份的服务类加 `equals` 反而会掩盖「同一个对象」的语义 |

## 7. 验证结果

| 项 | 结果 |
|---|---|
| 编译 | `javac -Xlint:all --release 25 -encoding UTF-8` → **263 文件，0 错误，0 告警** |
| class 产物 | **326** 个 |
| 模块可运行性 | 24 个模块的 `Client.main`（含 `SingletonClient`）全部可独立运行 |
| 组合示例可复现性 | 输出 152 行 / 9797 字节，SHA256 `DF1E29B1…8DD7`，**连续 4 次一致** |
| 文档一致性 | 24 份 README 的文件行数、类数量、输出片段均与源码实测对齐；全项目 30 个 md 的围栏全偶数、155 条相对链接与 493 条页内锚点均 0 失效 |

完整数据见 [05-verification.md](05-verification.md)。

---

## 8. 第三轮改造（2026-11-08）：从「能读懂」到「能推导」

前两轮解决的是「有没有」、「对不对」。这一轮解决的是一个问题：

> **23 个 Demo 都跑得通、25 份文档都齐全，但读完的人仍然不知道：拿到一个真实需求时该用哪个。**

### 8.1 改动全表

| # | 改动 | 类别 | 教学/工程价值 |
|---|---|---|---|
| 1 | 删除 `build.bat` / `build.sh` | 结构精简 | 项目不需要养一套构建体系；脚本本身就是上一轮一个 bug 的温床 |
| 2 | 清理 23 处脚本引用 | 一致性 | 文档提到的命令必须能真的执行 |
| 3 | 重写 [07](07-composition-vs-inheritance.md) §0 | **理论纠错** | 原文把 21 个模式全说成「组合变体」，不准确。改为四分类（子类型分派 3 = 类继承 1 + 接口实现 2 / 组合委托 10 / 用组合但意图不是组合 6 / 归不进去 4），第五轮又把第一类拆得更细 |
| 4 | 新增 [10-uml-atlas.md](10-uml-atlas.md) | 可视化 | 全项目原本 Mermaid 图为 0；现在 27 张图 + 图-代码对照索引 |
| 5 | 新增 [pattern-selection-guide.md](pattern-selection-guide.md) | **能力主线** | 回答「如何从业务需求推导设计」，而不是「模式定义背下来」 |
| 6 | 新增 `docs/practice/`（5 题 + 5 实现） | **能力主线** | 第一次把「先想再看答案」写进项目 |
| 7 | 新增 [docs/README.md](README.md) | 导航 | `docs/` 从 11 份增到 18 份，必须有入口 |
| 8 | 根 README 补「三层学习」「设计能力路线」 | 导航 | 把 BEGINNER / README 的分工写成表，而不是「长短不同」 |
| 9 | 修正 `02` 的 `Client2` 失效引用 | 一致性 | 指向了不存在的类 |
| 10 | 统一统计口径 | 一致性 | 行数 = 换行符个数（不含末尾空行），并在 README 里显式声明 |

### 8.2 三个关键判断（做了什么 / 刻意没做什么）

#### 判断一：不重写 orderservice 与 apigateway

审查结论：两个案例**已经是真正的模式协作**，不是 Demo 堆叠。

- `orderservice` 有 4 个明确接缝：策略↔责任链（链节点问策略）、装饰器↔策略（Capped 包住单个算法）、条件↔策略（Requirement 与 DiscountPolicy 分开）、工厂↔全部；
- `apigateway` 有 6 个接缝：链↔策略、链↔装饰器、状态↔观察者、享元↔策略、工厂↔链装配、统一响应包装。

所以只给它们补了 UML 与时序图（[10-uml-atlas.md](10-uml-atlas.md) §四），**没有重写业务代码**。重复开发已有的、已经正确的代码是浪费。

#### 判断二：不新增「Java 后端专题」

任务书第 9 项要求覆盖 JDK / Spring / MyBatis / Netty。审查后发现 [06-modern-scenarios.md](06-modern-scenarios.md)（约 3000 行）**已经完整覆盖**：Spring `BeanPostProcessor` 四阶段、Netty `ChannelPipeline` 双向规则、MyBatis「代理套链」两层结构。

再写一份只会造成两份文档说法不一。**正确做法是把它接入导航**，于是根 README 与 [docs/README.md](README.md) 都指向它。

#### 判断三：练习题只做 5 个，且刻意保留「不需要模式」的答案

5 个题里有 **3 个的正确答案包含「这里不该用模式」**：

| 练习 | 反模式结论 |
|---|---|
| 02 状态流转 | 状态多、规则常改、无副作用时，**查表比状态模式好** |
| 04 文件导出 | 门槛阈值、列定义这类**数据不该变成类** |
| 05 请求管线 | 三步固定时，**直接顺序调用三个方法就够了** |

这是刻意设计。只教「什么时候用」的项目会制造过度设计者；同时教「什么时候不用」才能培养判断力。

### 8.3 本轮新增了什么数字

> **时点**：第二轮 = 练习层开工前；第三轮 = 2026-11-08（练习、选择指南、验证报告全部落地后）。两组数字都是**当时**的实测值，**不是当前值** —— 当前值见 §10.3。

| 项 | 第二轮 | 第三轮 | 变化 |
|---|---|---|---|
| Java 源文件（`src/`） | 310 | 310 | **无变化** |
| `src/` 代码行数 | 35614 | 35614 | **无变化** |
| 练习参考实现 | 0 | 5 个文件 / 2028 行 | +5 |
| Markdown 文件 | 58 | 67 | +9 |
| Markdown 行数 | 109335 | 114077 | +4742 |
| Mermaid 图 | 0 | 40 | +40 |
| 编译结果 | 0 错 0 告警 | 0 错 0 告警 | 保持 |
| 25 个入口 | 全部 exit=0 | 全部 exit=0 | 保持 |

关键数字：**`src/` 一行没改**。这一轮的产出全部在「怎么教」与「怎么练」，不在「有什么代码」。

### 8.4 本轮踩到的真实坑

保留记录，因为它们本身就是教学内容：

| 坑 | 表现 | 原因 | 修正 |
|---|---|---|---|
| 链装配覆盖 | 五节点链只剩首尾两个，日志里「会员九折」消失 | `linkTo()` 返回 `this`，而装配写成了 `cursor = cursor.linkTo(next)` | 拆成两步：先 `linkTo`，再 `cursor = next` |
| 折扣按原价算 | 300 元打九折减了 32 而非 30 | 旧签名只收 `Order`，内部读 `order.money()`（原价） | 签名改为 `discount(BigDecimal price, Order)` |
| 封顶做假 | 往链里塞一个返回 0 的节点假装封顶 | 搞混了「单算法上限」和「整链上限」 | `DiscountChain` 持仓 `cap`，跑完再截断 |
| `EnumMap` 编译失败 | `EnumMap<>(OrderState.class)` 报错 | `EnumMap` 的 key 必须是 enum，而 `OrderState` 是接口 | 改 `LinkedHashMap` |
| 接口静态方法 | `valid(...)` 报「找不到符号」 | 接口静态方法不被实现类继承 | 写全名 `DiscountPolicy.valid(...)` |
| PowerShell 输出乱码 | 中文全变 `?` | `>` 重定向会先解码（GBK）再编码（UTF-8） | 用 .NET Process 接管 stdout，固定 UTF-8 |
| `-D` 参数被拆 | `ClassNotFoundException: /encoding=UTF-8` | PowerShell 按空格拆参数 | 加引号：`java "-Dstdout.encoding=UTF-8" ...` |

前三个坑直接写进了练习 01 的「为什么不选其他模式」与「坑点单独说」段；`EnumMap` 与接口静态方法两条属于该练习的实现细节，保留在本表；PowerShell 编码与 `-D` 引号两条进了 [05-verification.md](05-verification.md) §4.1 与 §3。

### 8.5 仍未做（有意为之）

| 项 | 为什么不做 |
|---|---|
| 新增 `LICENSE` 文件 | 授权是作者的决定。已在根 README「许可」节给出 MIT / Apache-2.0 / CC BY-NC-SA 三选一的分析与推荐，**等作者拍板** |
| 给 25 份模块 README 都翻新一遍 | 多数已经达标。为了「看起来改了很多」而重写，会把已有价值弄丢 |
| 给每个模式都补时序图 | 只有「对象协作复杂」的模式需要时序图（链、观察者、组合案例）。给单例画时序图只是凑数 |
| 引入 Maven / JUnit / CI | 项目定位就是纯 Java。增加构建工具会把学习者的注意力从设计模式转到工具上 |

完整验证数据见 [05-verification.md §2](05-verification.md)。

## 9. 收尾审查（2026-11-08）：只修准确性和一致性

第三轮把「练习」和「选择指南」建起来了。这一轮**没有新增任何教学主题**，只做三件事：修正理论表述、修正 UML 与代码的不一致、把验证报告改造成「当前 / 历史」两层。

### 9.1 改了什么

| # | 文件 | 改动 | 类别 |
|---|---|---|---|
| 1 | [05-verification.md](05-verification.md) | 整篇重组：当前数据前置到 §1~§4，历史记录收进 §5 并逐节标注日期 | 结构 |
| 2 | [07](07-composition-vs-inheritance.md) §0.1 | 「以继承为主」→「以子类型分派为主」，拆开 `extends` / `implements` / 多态；解释器「5 个 record」→ 实际的 **6 个** | 理论纠错 |
| 3 | [07](07-composition-vs-inheritance.md) 新增 §1.4 | 四种机制对照表（类继承 / 接口实现 / 子类型多态 / 对象组合） | 理论补齐 |
| 4 | [07](07-composition-vs-inheritance.md) §3 | 「字段类型永远是接口」是错的：`Order.history` 是 `List<HistoryEntry>`（具体 record） | 事实纠错 |
| 5 | [10](10-uml-atlas.md) §0.1 | `*--` 的说明原来写「静态内部类就是组合」，改成三个判据 | 理论纠错 |
| 6 | [10](10-uml-atlas.md) 建造者图 | `Computer *-- Builder` → 两条 `..>`；补「嵌套 ≠ 组合」说明 | 图-代码一致性 |
| 7 | [10](10-uml-atlas.md) 享元图 | `TextDocument` 根本没有 `views` 字段（`layout()` 的局部变量），删字段、改 `o--` 为 `..>` | 图-代码一致性 |
| 8 | [10](10-uml-atlas.md) 中介者 / 观察者 / 网关图 | 三处外部注册的关系从 `o--` 改为 `-->` | UML 语义 |
| 9 | [practice/01](practice/01-order-discount.md) · [practice/05](practice/05-api-request.md) | 两处 `*--` 改为 `o--`：节点都由 Builder 装配后传入，`StockGuard` 还被 `OrderService` 字段直接持有，不满足「独占创建」 | UML 语义 |
| 10 | [practice](practice/README.md) 5 份 | 参考答案改为 `<details>` 默认折叠；README 补「关于折叠」与 05 的编号例外 | 体验 |
| 11 | 本文件 §8.1 第 3 行、[docs/README.md](README.md)、根 README | 同步新的分类口径与当前行数 | 一致性 |

### 9.2 这一轮为什么只改这些

四个判断：

1. **分类错在「继承」这个词上，不在数字上。** 原来说「3 个以继承为核心」，数得没错（模板方法 / 解释器 / 工厂方法确实都靠父类型分派），但那 3 个里只有模板方法真的 `extends` 了一个带实现的类。读者记住「3 个继承」之后，会在解释器上得出错误结论——所以改的不是数字，是**机制**。
2. **UML 的 `*--` 被当成了「写在类里面」。** `Computer.Builder` 是静态嵌套类，`Computer` 根本没有 `Builder` 字段。这条如果不改，读者会以为「嵌套 = 组合」，而这正好是 UML 里最容易混的一对概念。顺同一标准回查，练习 01 / 05 里各有一处 `*--` 也不成立（节点是 Builder 装配进来的，`StockGuard` 还被外部字段引用），一并改成 `o--`。
3. **享元那张图画了一个不存在的字段。** `TextDocument` 只有 `text` / `font` / `factory` 三个字段，`views` 是 `layout()` 里的局部变量。图比文档更容易骗人，所以改图不改文档。
4. **验证报告的结构问题比数字问题更严重。** 原来 487 行里，读者要翻到第 392 行才知道「现在是什么状态」。数字没错，顺序错了。

### 9.3 没有做的事

| 项 | 为什么 |
|---|---|
| 全面重画 27 张图 | 只改与代码矛盾或 UML 语义错的 5 处。其余图的类名、字段、方法与源码逐条对过，没有问题 |
| 把 23 个模式的分类再分成更细的子类 | 四类已经够用。再分类会变成「为了分类而分类」 |
| 给练习答案加视频 / 动画解析 | 项目是纯文本 + Markdown，不引入外部资源 |
| 新增设计模式或教学文档 | 本轮目标就是收尾，不加规模 |

### 9.4 一个数字口径的教训

`2531` 这个 stdout 总行数，用 `TrimEnd("\n")` 统计会得到 `2526`。差的 5 行不是程序变了，而是 5 个入口在结尾**故意** `println()` 了一个空行（decorator / chainofresponsibility / state / strategy / templatemethod）。

**教训**：凡是「行数」类指标，必须先定义「末尾空行算不算」。写进验证报告时，口径要跟数字一起写。

### 9.5 本轮的数字变化

> **时点**：本轮前 = 第三轮结束（114077 行 / 710 条链接）；本轮后 = 第四轮结束（114331 行 / 725 条）。**两个数字都已过期**，当前值见 §10.3；三个时点的统一口径快照在 [05-verification.md](05-verification.md) §2.7。

| 项 | 本轮前 | 本轮后 | 变化 |
|---|---|---|---|
| `src/` 源文件 / 行数 | 310 / 35614 | 310 / 35614 | **无变化** |
| Markdown 文件 | 67 | 67 | 无变化（只改内容，不增文档） |
| Markdown 行数 | 114077 | **114331** | +254（07 补 §1.4、10 补 §0.1.1、05 重组、03 新增本§） |
| 相对链接 | 710 | **725** | +15（§1.4 新增 3 条源码链接，两张练习图补 4 条，05 重写新增若干） |
| Mermaid 图 | 40 | 40 | 无变化 |
| 编译 | 0 错 0 告警 | 0 错 0 告警 | 保持 |
| 25 个入口 | 全部 exit=0，2531 行 | 全部 exit=0，2531 行 | 保持 |
| 5 个练习实现 | 全部 exit=0 | 全部 exit=0 | 保持 |

`src/` 第二次做到一行未改。这一轮的产出全部是「让已有的说法更准确」，不是「增加能学的东西」。

## 10. 最终修复（2026-11-08，第五轮）：让代码、UML、文档、统计对齐

第四轮之后剩下的问题不是「内容不够」，而是三类不一致：**同一指标两套数字**、**文档讲的设计与 Java 实现不符**、**UML 箭头比代码说了更多**。这一轮只修这三类，不新增模式、不新增文档。

### 10.1 三个真问题

| # | 问题 | 证据 | 修法 |
|---|---|---|---|
| 1 | 满减门槛判了两次，而且两边口径不同 | `FullReduction` 存了 `threshold` 并在 `discount()` 里按**当前价**判一次；`Requirement.atLeast` 又按**原价**判一次，于是会出现「日志说命中、实际减 0」 | 「满」全部搬进 `Requirement`（新增 `currentPrice` 参数），算法改名 `FlatReduction` 只返回金额；返回值统一过 `DiscountPolicy.valid` |
| 2 | 参考实现悄悄改了外部行为，文档承诺却是「不改变外部行为」 | 改造前 `before()` 是 `price >= 100`（当前价），改造后 `atLeast` 用的是 `order.money()`（原价）。主场景 320 元两种口径都成立，把 bug 盖住了 | `atLeast` 改回当前价，另留 `atLeastOriginal` 做对照；主程序新增「原价 120、先减 30」场景，两种口径输出 90.00 / 70.00 |
| 3 | 同一份统计出现两套数字，且没说时点 | 114077（§8.3）vs 114331（§9.5「本轮后」）；710 vs 725；2028 行在本轮改代码后已失效 | 保留历史表并逐张标时点；在 [05](05-verification.md) §2.7 建「口径 + 三轮快照」表；数字统一由 `_verify/final_stats.ps1` 一份脚本算出 |

### 10.2 顺手修掉的四处

- **`*--` 与 static nested class**：上一轮已把 `Computer *-- Builder` 改成 `..>`，但没点名「**静态字段引用**」这一类。这一轮在 [10](10-uml-atlas.md) §0.1.2 把三种写法（静态嵌套类 / 静态字段 / 实例字段）列成一张判定表，并用 `Order --> OrderState`（五个状态都是 enum 单例）作具体例子。
- **同形不同箭头**：`Directory o-- FileSystemNode` 与 `SequenceNode --> WorkflowNode` / `BranchNode --> WorkflowNode` 是同一种「持有一组子节点」，箭头却不一样。统一成 `o--`，并把 `o--` / `-->` 的判据写进 §0.1.3。
- **两个边界关系的理由没写**：`Order o-- HistoryEntry`、`DispatchReport o-- Delivery` 看上去像 `*--`。在图集里写明它们为什么停在 `o--`（`history()` 对外给快照，`deliveries` 由发布方创建并与观察者共享）。
- **PowerShell 5.1 吞行**：`Get-Content -Raw` 对无 BOM 的 UTF-8 文件按 ANSI 解码，「汉字末字节 + 换行」被当成一个双字节字符，`07` 因此少算 113 行。统计脚本改用 `[IO.File]::ReadAllText`，这个坑写进 [05](05-verification.md) §2.7。

### 10.3 本轮的数字变化

| 项 | 本轮前（第四轮结束） | 本轮后（第五轮结束） | 变化 |
|---|---|---|---|
| Markdown 文件 | 67 | 67 | 无变化（不新增文档） |
| Markdown 行数 | 114331 | **114500** | +169（01 补边界与校验、07 补 §1.5、10 补 §0.1.2 / 0.1.3、05 补 §2.7、03 补本§） |
| 相对链接 | 725 | **733** | +8（07 §1.5 指向 `Computer.java` 与图集 §0.1.2，05 §2.7 与本§指向 05 / 10 / 03） |
| `src/` 源文件 / 行数 | 310 / 35614 | 310 / 35614 | **无变化** |
| 练习参考实现 | 5 个 / 2028 行 | 5 个 / **2203** 行 | +175（只有 `OrderDiscountSolution` 483 → 658） |
| 编译 | 0 错 0 告警 | 0 错 0 告警 | 保持 |
| 25 个入口 | 全部 exit=0，2531 行 | 全部 exit=0，2531 行 | 保持 |
| 5 个练习实现 | 全部 exit=0，237 行 | 全部 exit=0，**258** 行 | +21（门槛口径、顺序、非法参数三段边界） |

## 11. 边界场景补全（2026-11-08，第六轮）

第五轮修完了设计一致性，但任务书里的七个边界场景只覆盖了三个。本轮不碰任何类结构，只补验证：

| # | 新增场景 | 输出 | 验证点 |
|---|---|---|---|
| 1 | 门槛刚好踩线（当前价 == 100） | 命中，应付 80.00 | `>=` 的等号语义写在 `test` 里 |
| 2 | 券面值 110 > 订单 30 | 应付 0.00，日志可见兑底 | `ChainState.apply` 的 `reduction.min(price)`：金额只减不增、永不为负 |
| 3 | 双封顶对照：积分 8 超 3；券面值 200 超整单 50 | 减 3.00；应付 150.00 | `Capped` 与链 `cap` 各管一段 |
| 4 | 策略运行期返回负数 | `IllegalStateException` 当场拦截 | 构造期管参数，运行期 `valid` 管返回值 |

同步更新 [01](practice/01-order-discount.md) §10 的边界清单表与 [05](05-verification.md) §2.4 / §2.7 的数字。本轮数字变化：`OrderDiscountSolution` 658 → 716 行，练习合计 2203 → **2261** 行、stdout 258 → **284** 行；Markdown 行数 114500 → **114527**（链接 +2，本§指向 01 与 05，733 → **735**）；`src/` 与 25 个入口均无变化。
