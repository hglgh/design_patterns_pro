# 原项目源码审查报告与优化方案

> 本报告基于对 `E:\codeFile\codeFather\design_patterns` 全量源码（163 个 Java 文件、3715 行）的逐一阅读，
> 覆盖 GoF 23 种设计模式的原实现，输出问题清单与优化方案。
> 所有结论均以源码为依据，未凭类名或包名猜测设计。

---

## 0. 项目总体情况

| 项 | 数据 |
|---|---|
| 原项目路径 | `E:\codeFile\codeFather\design_patterns` |
| 新项目路径 | `E:\codeFile\codeFather\design_patterns_pro` |
| Java 文件总数 | 163（实测 `Get-ChildItem -Recurse -Filter *.java`） |
| 代码物理行数 | 3715 |
| 已实现模式数 | **22**（缺 **单例模式 Singleton**） |
| 顶层包结构 | `creationtype`、`structuraltype`、`BehaviorType`、`CombineApplicationExamples` |
| 构建方式 | 仅 IntelliJ 模块文件（`design_patterns.iml`），无 Maven / Gradle |
| 目标 JDK | JDK 25（LTS） |

### 0.1 全局性问题（跨模式）

| # | 问题 | 影响 | 优化方案 |
|---|---|---|---|
| G1 | 包命名风格不一致：`creationtype`（小写）、`structuraltype`（小写）、`BehaviorType`（大驼峰） | 违反 Java 包命名规范（全小写），IDE 索引与 import 混乱 | 统一为 `com.patterns.creational` / `structural` / `behavioral` / `combined` |
| G2 | 缺少 **单例模式（Singleton）** | 23 种 GoF 模式不完整 | 新增 `creational/singleton`，覆盖饿汉、懒汉、DCL、静态内部类、枚举 5 种实现 |
| G3 | 部分类使用 `default`（package-private）可见性，如 `structural/CompositePattern/demo2/Folder`、`behavioral/StatePattern/Order` | 无法跨包复用，教学演示不友好 | 全部改为 `public` |
| G4 | 大量 Javadoc 使用 `@ClassName/@Package/@Description/@Author/@Create` 自定义标签 | 不是标准 Javadoc，IDE 无法生成文档 | 改为标准 Javadoc（`@author`、`@since`），并补全类型/方法说明 |
| G5 | 每个模式没有独立 README，学习者需要打开代码才能理解意图 | 学习成本高 | 每种模式配一份 `README.md`，含现实案例、原始实现、动机、推导、代码解析、执行流程、扩展对比、优缺点、面试题 |
| G6 | 项目根目录没有 README，没有编译/运行说明 | 无法快速上手 | 新增根 `README.md` + `build.bat` / `build.sh` 一键编译运行 |
| G7 | `CombineApplicationExamples` 里 `DiscountStrategy` 接口方法叫 `applyDiscount`，而 `BehaviorType/StrategyPattern` 里叫 `calculate` | 同名接口方法名不一致，容易混淆 | 统一命名为 `applyDiscount(double)` |
| G8 | `Prototype` 里 `DocumentPrototype extends Cloneable` 后又自定义 `cloneDocument()` | Cloneable 是标记接口，继承它没有实际作用，且 `cloneDocument` 与 `clone` 语义重叠 | 保留 `cloneDocument()` 作为业务语义入口，内部委托 `Object.clone()`，`Cloneable` 仅作为标记 |

---

## 1. 创建型模式（Creational）

### 1.1 工厂方法（Factory Method）

**原实现**：`creationtype/factorymethod/`
- 抽象产品：`Message`（接口）
- 具体产品：`EmailMessage`、`SmsMessage`、`InAppMessage`
- 抽象工厂：`MessageFactory`（接口）
- 具体工厂：`EmailMessageFactory`、`SmsMessageFactory`、`InAppMessageFactory`
- 客户端：`Main` + `NotificationService`（record）

**发现的问题**：
1. **接口参数顺序 Bug**：`Message` 接口声明 `void send(String content, String to)`，而所有实现类都写成 `send(String to, String content)`。Java 只按位置匹配，运行结果正确，但接口的参数名误导阅读者。
2. `Main.java` 的注释与实际使用不一致（注释写"切换只需换这里"，但代码只留了一行 `new SmsMessageFactory()`，其余被注释掉）。
3. `NotificationService` 使用 `record` 承载依赖，虽然可以工作，但 record 的语义是"不可变数据载体"，用它做服务类不够贴切。

**优化方案**：
- 修正 `Message.send(String to, String content)` 的参数顺序，与实现一致。
- `Main` 改为 `Client`，通过循环演示三种工厂，避免注释切换。
- `NotificationService` 改为普通 `final class`，构造器注入工厂。

### 1.2 抽象工厂（Abstract Factory）

**原实现**：`creationtype/abstractfactory/`
- 抽象产品：`Button`、`TextBox`、`Image`
- 抽象工厂：`UIFactory`
- 具体工厂：`AndroidUIFactory`、`WebUIFactory`
- 具体产品：`android/AndroidButton` 等 3 个、`ui/WebButton` 等 3 个
- 客户端：`Client`

**发现的问题**：
1. **注释与代码不符**：`Client.java` 注释写"假设当前运行环境为 Web"，但实际 `new AndroidUIFactory()`，`WebUIFactory` 被注释。
2. 具体产品分包不合理：`android/` 与 `ui/`（Web 产品）命名不对称，`ui` 实际是 Web 产品。
3. 缺少"产品族"概念的显式说明，学习者容易把它和工厂方法混淆。

**优化方案**：
- 包结构改为 `abstractfactory/product/`（抽象产品）、`abstractfactory/android/`、`abstractfactory/web/`、`abstractfactory/factory/`。
- `Client` 同时演示 Android 与 Web 两个产品族，输出对比。
- 在 README 中明确"产品族 vs 产品等级"的区别。

### 1.3 建造者（Builder）

**原实现**：`creationtype/builder/`
- 传统 Builder：`ComputerBuilder`（抽象类）+ `GamingComputerBuilder` + `Director` + `model/Computer`
- 简化 Builder：`Computer`（含静态内部 `Builder`）+ `Client2`
- 客户端：`Client`、`Client2`

**发现的问题**：
1. **两个同名 `Computer` 类**：`creationtype.builder.Computer` 与 `creationtype.builder.model.Computer` 同时存在，`Client` 与 `Client2` 分别使用不同版本，极易混淆。
2. `ComputerBuilder` 里 `protected Computer computer = new Computer();` 在抽象类构造时就 new 出产品，违背"建造者控制构造过程"的语义。
3. `Director` 的 `construct()` 没有返回 `this`，无法链式调用。
4. 简化 Builder 没有校验必填字段。

**优化方案**：
- 删除 `creationtype.builder.Computer`，统一使用 `model/Computer`。
- 传统 Builder：`ComputerBuilder` 改为接口，`GamingComputerBuilder` / `OfficeComputerBuilder` 实现，`Director` 负责编排。
- 简化 Builder：`Computer` 内部静态 `Builder`，`build()` 时校验必填字段（cpu/memory）。
- `Client` 同时演示两种 Builder 风格。

### 1.4 原型（Prototype）

**原实现**：`creationtype/PrototypePattern/`
- `DocumentPrototype extends Cloneable`（接口）
- `WordDocument implements DocumentPrototype`
- `Client`

**发现的问题**：
1. `DocumentPrototype extends Cloneable` 意义不大：`Cloneable` 是标记接口，继承它不会让子接口获得 `clone()` 方法。
2. `cloneDocument()` 内部调用 `super.clone()`，但接口无法调用 `super.clone()`，实际是 `WordDocument` 里调用 `Object.clone()`，写法容易误解。
3. 只演示了浅拷贝 → 深拷贝的修复，没有演示"原型注册表"（Prototype Registry）这一常见用法。

**优化方案**：
- `DocumentPrototype` 不再继承 `Cloneable`，只声明 `cloneDocument()`。
- `WordDocument` 实现 `Cloneable`，`cloneDocument()` 内部调用 `super.clone()` 并对 `List<String> images` 做深拷贝。
- 新增 `DocumentRegistry`（原型注册表），演示"从注册表按 key 取原型并克隆"的真实用法。

### 1.5 单例（Singleton）—— **新增**

**原实现**：无。

**优化方案**：新增 `creational/singleton/`，覆盖：
- `EagerSingleton`（饿汉式）
- `LazySingleton`（懒汉式，synchronized）
- `DoubleCheckLockSingleton`（DCL + volatile）
- `StaticInnerClassSingleton`（静态内部类）
- `EnumSingleton`（枚举，推荐）
- `SingletonClient` 演示 5 种实现并对比线程安全性

---

## 2. 结构型模式（Structural）

### 2.1 适配器（Adapter）

**原实现**：`structuraltype/AdapterPattern/`
- 目标接口：`AudioPlayer`
- 适配者接口：`AdvancedMediaPlayer`
- 具体适配者：`VlcPlayer`、`Mp4Player`
- 适配器：`MediaAdapter`
- 默认实现：`DefaultAudioPlayer`
- 客户端：`Client`

**发现的问题**：
1. `MediaAdapter` 每次 `play()` 都 `new` 一个 `AdvancedMediaPlayer`，没有缓存。
2. `DefaultAudioPlayer` 里 `if/else` 判断格式，扩展性差。
3. 没有区分"对象适配器"与"类适配器"，教学价值打折。

**优化方案**：
- `MediaAdapter` 构造时注入 `AdvancedMediaPlayer`，避免每次 new。
- `DefaultAudioPlayer` 使用 `Map<String, AudioPlayer>` 注册格式 → 播放器映射。
- README 中对比对象适配器（组合）与类适配器（继承）的差异。

### 2.2 桥接（Bridge）

**原实现**：`structuraltype/BridgePattern/`
- 抽象部分：`Message`（抽象类）+ `NormalMessage`、`UrgentMessage`
- 实现部分：`MessageSender`（接口）+ `EmailSender`、`SMSSender`、`WeChatSender`
- 客户端：`Client`

**评价**：实现合理，职责清晰，是原项目中质量较高的 Demo。

**优化方案**：
- 保留结构，仅补充 README 说明"为什么要用组合而不是继承"。
- 新增一种 `Message` 子类（如 `ScheduledMessage`）与一种 `MessageSender`（如 `DingTalkSender`），演示"两个维度独立扩展"。

### 2.3 组合（Composite）

**原实现**：`structuraltype/CompositePattern/demo1` + `demo2`
- demo1：`FileComponent` + `FileLeaf`（record）+ `FolderComposite`
- demo2：`FileSystemComponent` + `File`（record）+ `Folder`（package-private）

**发现的问题**：
1. 两个 demo 高度重复，且 demo2 的 `Folder` / `File` 是 package-private，外部无法使用。
2. demo2 的 `File` 与 `java.io.File` 同名，容易误导。
3. 两个 demo 都没有演示"安全式 vs 透明式"组合模式的区别。

**优化方案**：
- 合并为一个 demo：`FileSystemComponent`（接口）+ `File`（record，叶子）+ `Directory`（组合）。
- 类名改为 `Directory` 避免与 `java.io.File` 冲突。
- README 中对比"透明式（接口声明 add/remove）"与"安全式（只在组合类声明）"。

### 2.4 装饰器（Decorator）

**原实现**：`structuraltype/DecoratorPattern/`
- `TextComponent`（接口）+ `PlainText`（record）+ `TextDecorator`（抽象类）+ `BoldDecorator`、`ColorDecorator`
- 客户端：`Client`

**评价**：结构清晰，是标准装饰器实现。

**优化方案**：
- 保留结构，新增 `ItalicDecorator` 演示"装饰器可无限叠加"。
- README 中对比装饰器 vs 继承 vs 代理。

### 2.5 外观（Facade）

**原实现**：`structuraltype/FacadePattern/`
- `UserServiceFacade` + `UserDAO` + `UserDAOImpl` + `model/User`
- 客户端：`Client`

**发现的问题**：
1. `UserServiceFacade` 构造器里 `new UserDAOImpl()`，硬编码依赖，违背"外观封装子系统"的初衷。
2. 子系统只有一个 DAO，体现不出"外观简化复杂子系统"的价值。

**优化方案**：
- 构造器注入 `UserDAO`，提供默认无参构造器作为便捷入口。
- 新增 `UserValidator`（校验）与 `UserCache`（缓存）两个子系统，外观协调三者，真正体现"简化复杂调用"。

### 2.6 享元（Flyweight）

**原实现**：`structuraltype/FlyweightPattern/`
- `Glyph`（接口）+ `CharacterGlyph`（record）+ `GlyphFactory` + `CharacterView`
- 客户端：`Client`

**评价**：实现合理，内部状态（symbol/font）与外部状态（x/y/color）分离清晰。

**优化方案**：
- `GlyphFactory` 使用 `ConcurrentHashMap` + `computeIfAbsent`，线程安全且代码更简洁。
- 新增"享元池大小统计"输出，直观展示复用效果。

### 2.7 代理（Proxy）

**原实现**：`structuraltype/ProxyPattern/`
- `Image`（接口）+ `RealImage`（record）+ `ProxyImage`
- 客户端：`Client`

**发现的问题**：
1. `RealImage` 是 record，record 的构造器里调用 `loadFromDisk()` 是副作用，不符合 record "不可变数据"的语义。
2. 只演示了"虚拟代理（懒加载）"，没有覆盖"保护代理"、"远程代理"、"缓存代理"。

**优化方案**：
- `RealImage` 改为普通类，构造器里加载。
- 新增 `ProtectionProxy`（权限校验）与 `CachingProxy`（缓存），演示代理的多种用途。

---

## 3. 行为型模式（Behavioral）

### 3.1 责任链（Chain of Responsibility）

**原实现**：`BehaviorType/ChainOfResponsibilityPattern/`
- `DiscountHandler`（接口）+ `HandlerNode`（链表节点）+ `DiscountChain`（链表管理）
- 具体处理器：`FullReductionDiscountHandler`、`CouponDiscountHandler`、`MemberDiscountHandler`
- `Order` + `Client`

**评价**：使用单向链表 + 优先级排序，比常见的"每个 handler 持有 next"更清晰，是原项目的亮点。

**优化方案**：
- 保留链表结构，`DiscountChain` 增加 `process` 返回值（最终金额），便于断言。
- `Order` 改为 record（不可变）+ 可变 amount 字段的折中：保留 `amount` 可变，其余字段 final。
- README 中对比"纯责任链（任一节点处理即停止）"与"管道式（全部节点依次处理）"。

### 3.2 命令（Command）

**原实现**：`BehaviorType/CommandPattern/`
- `Task`（接口）+ `EmailTask`、`BackupTask`、`ReportTask`
- `TaskScheduler`（调用者）+ `SchedulerClient`

**发现的问题**：
1. 缺少"接收者（Receiver）"角色，命令直接内联业务逻辑，体现不出"命令解耦调用者与接收者"的价值。
2. 没有演示"撤销（undo）"与"命令队列/日志"。

**优化方案**：
- 引入 `Receiver`（如 `DatabaseReceiver`、`EmailReceiver`），命令持有接收者引用。
- `Task` 增加 `undo()` 方法，`TaskScheduler` 支持撤销栈。
- 新增 `CommandHistory` 演示命令日志与重放。

### 3.3 解释器（Interpreter）

**原实现**：`BehaviorType/InterpreterPattern/`
- `Expression`（接口）+ `Context`（record）
- `GreaterThanExpression`、`LessThanExpression`、`AndExpression`（record）
- `RuleParser` + `Client`
- `AST_Demo/`：`Node`（抽象类）+ `NumberNode`、`AddNode` + `SimpleParser` + `SimpleASTMain`

**发现的问题**：
1. `RuleParser.parse` 只支持 `&&` 分割，且假设左边一定是 `>`、右边一定是 `<`，规则写死。
2. `GreaterThanExpression` / `LessThanExpression` 里 `if ("age".equals(field))` 硬编码字段，扩展性差。
3. AST_Demo 与主 demo 没有关联，像是两个独立示例。

**优化方案**：
- `Context` 改为 `Map<String, Integer>`，支持任意字段。
- 新增 `OrExpression`、`NotExpression`，`RuleParser` 支持 `&&` / `||` / `!` 与括号（简化版：仅 `&&` / `||` 优先级）。
- AST_Demo 保留作为"解释器模式的另一应用：算术表达式求值"，README 中说明两者关系。

### 3.4 迭代器（Iterator）

**原实现**：`BehaviorType/IteratorPattern/`
- `Iterator`（接口，返回 `Object`）+ `ProcessNode`（record）+ `Workflow`（含内部类 `WorkflowIterator`）
- 客户端：`Client`

**发现的问题**：
1. `Iterator.next()` 返回 `Object`，客户端需要强转，没有利用泛型。
2. `Workflow` 使用数组 + 容量限制，`addNode` 超出容量时静默丢弃，不友好。
3. 没有演示"多种遍历方式"（正序、逆序、过滤）。

**优化方案**：
- `Iterator<T>` 泛型化，`Workflow` 实现 `Iterable<ProcessNode>`，支持 for-each。
- `Workflow` 内部用 `ArrayList`，无容量限制。
- 新增 `ReverseIterator` 演示"同一聚合，多种遍历"。

### 3.5 中介者（Mediator）

**原实现**：`BehaviorType/MediatorPattern/`
- `ChatRoomMediator`（接口）+ `ChatRoom`（实现）
- `User`（抽象类）+ `ChatUser`（实现）
- 客户端：`ChatClient`

**评价**：结构清晰，是标准中介者实现。

**优化方案**：
- 保留结构，新增 `GroupChatRoom`（群聊，只发给指定分组）演示"中介者封装复杂交互"。
- README 中对比中介者 vs 外观 vs 观察者。

### 3.6 备忘录（Memento）

**原实现**：`BehaviorType/MementoPattern/`
- `Document` + `DocumentMemento`（record）+ `History`（含 undo/redo 双栈）
- 客户端：`Client`

**发现的问题**：
1. `History.popMemento()` 逻辑绕：先 pop 当前，再 peek 上一个，返回 peek 结果。语义上"撤销"应该返回上一个状态，但当前实现把"当前状态"压入 redo 栈后返回上一个，容易误解。
2. `Document` 没有暴露 `getContent()` 之外的状态查询，测试不便。

**优化方案**：
- 简化 `History`：`undo()` 返回上一个状态并压入 redo 栈，`redo()` 返回下一个状态并压回 undo 栈。
- 逻辑更直观，README 中画出双栈状态转移图。

### 3.7 观察者（Observer）

**原实现**：`BehaviorType/ObserverPattern/`
- `User`（接口，只有 `notifyFollowers`）+ `ConcreteUser`（实现，含 `addFollower`/`removeFollower`）
- `Follower`（接口）+ `ConcreteFollower`（record，同时实现 `Followable`）
- `Followable`（接口，`follow`/`unfollow`）
- 客户端：`Client`

**发现的问题**：
1. **接口职责不清**：`User` 接口只声明 `notifyFollowers`，但 `addFollower`/`removeFollower` 是 `ConcreteUser` 的 public 方法，不在接口里。导致 `ConcreteFollower.follow(User target)` 必须 `instanceof ConcreteUser` 强转，违背面向接口编程。
2. `ConcreteFollower` 是 record，但同时实现 `Follower` 与 `Followable`，record 的不可变性与"关注/取关"的状态变化矛盾（虽然这里没有状态，但语义上别扭）。
3. `Followable` 接口的 `follow(User target)` 参数是 `User`，但实际只能关注 `ConcreteUser`，接口设计不严谨。

**优化方案**：
- `User` 接口补全 `addFollower` / `removeFollower` / `notifyFollowers`，`ConcreteUser` 实现。
- `Follower` 接口保留 `update`。
- 删除 `Followable`，`follow` / `unfollow` 作为 `Follower` 的默认方法（default method），参数为 `User`。
- `ConcreteFollower` 改为普通类（record 不适合有行为的角色）。

### 3.8 状态（State）

**原实现**：`BehaviorType/StatePattern/`
- `OrderState`（接口）+ `PendingState`、`PaidState`、`ShippedState`、`CompletedState`（均 package-private）
- `Order`（package-private）+ `Client`

**发现的问题**：
1. 所有状态类与 `Order` 都是 package-private，外部无法复用。
2. `Order.request()` 语义模糊："请求"什么？应该是"推进到下一状态"。
3. 状态转换逻辑分散在各状态类里，没有集中展示状态机。

**优化方案**：
- 全部改为 `public`。
- `Order.request()` 改为 `Order.next()`，语义明确。
- 新增 `OrderState` 的 `getName()` 方法，便于日志输出。
- README 中画出状态转移图。

### 3.9 策略（Strategy）

**原实现**：`BehaviorType/StrategyPattern/`
- `DiscountStrategy`（接口）+ `FullReductionStrategy`、`PercentageDiscountStrategy`、`VipDiscountStrategy`
- `PriceCalculator`（上下文）+ `Client`

**评价**：标准策略模式实现，结构清晰。

**优化方案**：
- 保留结构，`PriceCalculator` 构造器注入默认策略，避免 `setStrategy` 前调用报错。
- 新增 `NoDiscountStrategy`（原价）作为空对象模式示例。
- README 中对比策略 vs 状态 vs 命令。

### 3.10 模板方法（Template Method）

**原实现**：`BehaviorType/TemplateMethodPattern/`
- `OrderProcessTemplate`（抽象类，`processOrder` 为 final 模板方法）
- `PhysicalProductOrder`、`VirtualProductOrder`
- 客户端：`Client`

**评价**：标准模板方法实现，`final` 修饰模板方法是亮点。

**优化方案**：
- 保留结构，新增"钩子方法（hook）"示例：`isExpressDelivery()` 默认 false，子类可覆盖。
- README 中对比模板方法 vs 策略（继承 vs 组合）。

### 3.11 访问者（Visitor）

**原实现**：`BehaviorType/VisitorPattern/`
- 主 demo：`Document`（接口）+ `PDFDocument`、`WordDocument`、`ExcelDocument`（record）+ `DocumentVisitor`（接口）+ `HtmlExportVisitor`
- 子 demo：`CalculatePrice/` 购物车计价（`CartItem` + `NormalItem`/`DiscountItem`/`BogofItem` + `ShoppingCartVisitor` + `PriceCalculatorVisitor`/`PrintListVisitor` + `ShoppingCart`）

**评价**：两个 demo 都合理，`CalculatePrice` 子 demo 尤其好，展示了访问者"在不修改元素类的前提下新增操作"的核心价值。

**发现的问题**：
1. `PrintListVisitor.getTotalPrice()` 返回 0，语义奇怪（打印访问者不需要总价）。
2. 主 demo 的 `HtmlExportVisitor` 只演示了一种访问者，没有对比"新增访问者"的扩展性。

**优化方案**：
- `ShoppingCartVisitor` 拆分为 `CartVisitor`（visit 方法）与 `PriceProvider`（getTotalPrice），`PrintListVisitor` 只实现 `CartVisitor`。
- 主 demo 新增 `WordCountVisitor`（统计字数），演示"新增操作无需修改 Document 子类"。

---

## 4. 组合应用示例（CombineApplicationExamples）

**原实现**：`CombineApplicationExamples/`
- `OrderService`（main）+ `DiscountStrategyFactory`（工厂）+ `LoggingDiscountDecorator`（装饰器）
- `handler/DiscountHandler`（抽象类）+ `handler/StrategyHandler`
- `strategy/DiscountStrategy` + 3 个具体策略

**评价**：责任链 + 策略 + 装饰器 + 工厂 四模式组合，是原项目的亮点。

**发现的问题**：
1. `DiscountStrategy.applyDiscount` 与 `BehaviorType/StrategyPattern/DiscountStrategy.calculate` 方法名不一致。
2. `OrderService.placeOrder` 里手动构建责任链，代码冗长。
3. 没有单元测试验证最终价格。

**优化方案**：
- 统一接口方法名为 `applyDiscount`。
- 新增 `DiscountChainBuilder`（链式构建器），简化责任链组装。
- 新增 `OrderServiceTest`（main 方法断言），验证最终价格正确。

---

## 5. 优化清单汇总（按优先级）

### P0（必须修复，影响正确性/完整性）
- [x] 新增单例模式（5 种实现）
- [x] 修复工厂方法 `Message.send` 参数顺序
- [x] 修复观察者 `User` 接口缺失 `addFollower`/`removeFollower`
- [x] 统一包命名为全小写
- [x] 合并 Builder 两个 `Computer` 类
- [x] 组合模式类名 `File` → `Directory` 避免与 `java.io.File` 冲突

### P1（强烈建议，影响教学质量）
- [x] 每种模式配独立 README.md（§0-§10 共 11 节：结论、现实案例、原始实现、设计动机、设计推导、代码解析、执行流程、扩展对比、优缺点与误区、面试与自测、附录）
- [x] 根 README.md + 学习路径 + 模式对比
- [x] 命令模式引入 Receiver 与 undo
- [x] 解释器 `Context` 改为 Map，支持任意字段
- [x] 迭代器泛型化，实现 `Iterable`
- [x] 备忘录 `History` 逻辑简化
- [x] 外观新增子系统（Validator/Cache）
- [x] 代理新增保护代理/缓存代理
- [x] 状态模式全部 public，`request()` → `next()`

### P2（锦上添花）
- [x] 适配器使用 Map 注册格式
- [~] 享元使用 `computeIfAbsent` 复用对象——**实际选了 `HashMap` 而非 `ConcurrentHashMap`**。后来实测证明这是个真缺陷：16 线程 × 3000 轮压力测试有 5/3000 轮出现重复创建，数据与修法见 flyweight README §7.3、§8.3
- [x] 装饰器新增 `ItalicDecorator`
- [x] 模板方法新增 hook 方法
- [x] 访问者新增 `WordCountVisitor`
- [x] 桥接新增 `ScheduledMessage` / `DingTalkSender`
- [x] 原型新增 `DocumentRegistry`
- [x] 组合应用新增 `DiscountChainBuilder` 与测试

---

## 6. 未变更项（保留原设计）

以下原实现合理，仅做包名/可见性/Javadoc 规范化，不改变设计：
- 桥接模式（Message / MessageSender 双维度）
- 装饰器模式（TextComponent / TextDecorator）
- 享元模式（Glyph / CharacterGlyph / CharacterView）
- 模板方法（OrderProcessTemplate）
- 策略模式（DiscountStrategy / PriceCalculator）
- 中介者模式（ChatRoomMediator / User）
- 访问者模式 CalculatePrice 子 demo

---

## 7. 验证计划

| 阶段 | 验证内容 | 工具 |
|---|---|---|
| 编译 | 全部 23 个模式 + 组合示例编译通过 | `javac --release 25` |
| 运行 | 每个模式的 `Client.main` 可独立运行，输出符合预期 | `java -cp out` |
| 文档 | 每个 README.md 链接有效，代码片段与源码一致 | 人工核对 |
| 结构 | 目录树与 README 导航一致 | `tree` 命令 |

> 详细编译/运行结果见 `docs/05-verification.md`（第五阶段输出）。
