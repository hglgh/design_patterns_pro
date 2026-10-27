# 项目优化记录：从 22 种模式到 23 种 + 组合示例

> 本文记录**实际做了什么改动、为什么这么改、验证结果如何**。
> 与 [00-source-review.md](00-source-review.md) 的分工：那份是**改造前的计划**，这份是**改造后的执行记录**，包含计划与实际的偏差及原因。
> 全部数据来自 `javac` / `java` 实测，未做估算。
>
> **2026-10-27 说明**：本文记录的是「22 → 23 种模式」那一轮重构，文件数与行数是当时基线。之后项目新增了 23 个入门 Demo、23 份 `BEGINNER.md` 与两个组合案例；当前规模（310 文件 / 35614 行 / 558 class）见 [09-beginner-refactor-report.md](09-beginner-refactor-report.md) 与根 README。

## 0. 总览

| 项 | 原项目 | 重构后 | 变化 |
|---|---|---|---|
| 已实现模式数 | 22（缺单例） | **23** | +1 |
| Java 文件数 | 163 | **263** | +100 |
| 代码物理行数 | 3715 | **28193** | ×7.6 |
| 编译产物 class 数 | — | **326** | — |
| 编译告警 | 未记录 | **0**（`-Xlint:all`） | — |
| 包命名 | `creationtype` / `structuraltype` / `BehaviorType` / `CombineApplicationExamples` | `com.patterns.creational` / `structural` / `behavioral` / `combined` + `util` | 全小写 |
| 构建方式 | 仅 IntelliJ `.iml` | `build.bat` / `build.sh` + 直接 `javac` | 零依赖 |
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
| G6 | 根 README + 构建脚本 | 无 | `README.md` + `build.bat` / `build.sh` | 原项目无法脱离 IDEA 运行 |
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
