# Design Patterns Pro — Java 23 种设计模式教学项目

> 基于原生 Java（JDK 25 LTS）实现的 GoF 23 种设计模式学习项目，**不依赖 Spring 等任何框架**。
> 每种模式都配有可独立运行的 Demo + 通俗易懂的 Markdown 学习文档。

## 项目亮点

- **23 种模式全覆盖**：创建型 5 + 结构型 7 + 行为型 11，**含原项目缺失的单例模式**。
- **每种模式独立 Demo**：不强行整合成一个业务系统，每个模式都可单独编译运行。
- **两层教学 + 一层实践**：`BEGINNER.md` 讲「为什么这样设计」，`README.md` 讲「实现细节与适用边界」，[综合练习](docs/practice/README.md) 讲「拿到需求怎么自己推」。
- **两个真实组合案例**：`orderservice`（策略 + 装饰器 + 责任链 + 工厂）与 `apigateway`（责任链 + 策略 + 装饰器 + 状态 + 观察者 + 享元），后者配 5092 行深度拆解与 304 行可复现输出。
- **设计能力主线**：[docs/pattern-selection-guide.md](docs/pattern-selection-guide.md) 回答「什么时候该抽象、该用哪个、怎么避免过度设计」，给出「业务问题 → 关键变化点 → 候选模式 → 方案比较 → 最终设计」的完整推导链。
- **综合练习 5 题**：[docs/practice/](docs/practice/README.md) 五个真实业务任务（订单优惠 / 状态流转 / 消息通知 / 报表导出 / 请求管线），每题都用 `<details>` 把参考答案默认折叠在分界线之后，**必须自己先想**。
- **UML 图谱**：[docs/10-uml-atlas.md](docs/10-uml-atlas.md) 25 张类图 + 2 张时序图，全部对着真实类名绘制，附录有「图 ↔ 代码」对照表与画图三坑。
- **现代场景地图**：[docs/06-modern-scenarios.md](docs/06-modern-scenarios.md) 把 23 个模式映射到 JDK 源码、主流框架扩展点与云原生组件上，并回答「record / sealed / 虚拟线程出现后还要不要手写」。
- **原理专文**：[docs/07-composition-vs-inheritance.md](docs/07-composition-vs-inheritance.md) 用装机、奶茶加料、泡茶、文件夹四个现实场景，把「组合 vs 继承」逐条映射到代码；§0 按 23 个模式的真实实现做了严谨四分类，**不把 23 个模式笼统归为组合**。
- **贴近现实业务**：订单折扣、优惠券、状态流转、消息通知、报表导出、API 网关等场景，让模式用途一目了然。
- **JDK 25 原生**：使用 record、sealed、switch 表达式、var 等现代 Java 特性，但不引入任何第三方依赖。
- **零构建工具**：只用 `javac` / `java`，没有 Maven、Gradle、CI 工作流，也没有自定义构建脚本。

## 三层学习：先入门，再深入，最后自己推

每个模式都准备了两层材料，整个项目再加一层实践。不需要一上来就啃完整工程版：

| 层级 | 文件 | 适合谁 | 目标 |
|---|---|---|---|
| 第一层：入门 | `BEGINNER.md` + `beginner/BeginnerDemo.java` | 学过 Java 语法，没系统学过设计模式 | 说清「为什么用、什么时候不用」 |
| 第二层：深度 | `README.md` + 工程版 `Client.java` | 已理解动机，要做实现/面试 | 角色、协作、扩展、异常边界 |
| 第三层：实践 | [docs/practice/](docs/practice/README.md) | 能读懂模式，但拿到需求不知道用哪个 | 从需求识别变化点、独立推导结构 |

入门 Demo 都是**单文件、纯 Java、输出确定**：同一个文件里先跑「改造前」，再跑「改造后」，编译一次就能对照。23 篇入门文档按 [docs/08-beginner-teaching-template.md](docs/08-beginner-teaching-template.md) 的统一八段式写作。

**两层文档的分工不是「长短」**：

| | 回答的问题 | 会有什么 | 不会有什么 |
|---|---|---|---|
| `BEGINNER.md` | 这个模式**解决什么问题**？为什么非这么设计不可？ | 一个真实业务痛点、一段「不用模式」的代码、一份推导、什么时候**不该**用 | 完整类图、扩展场景、面试题 |
| `README.md` | 它**怎么实现**？边界在哪？工程上怎么用？ | 意图、类图、职责、协作、执行流程、代码逐段分析、代价、常见错误、相似模式对比、Java 实战、面试题 | 「为什么需要这个模式」的科普 |

### 入门文档索引

| 分类 | 模式（点击进入 `BEGINNER.md`） |
|---|---|
| 创建型 5 | [单例](src/com/patterns/creational/singleton/BEGINNER.md) · [工厂方法](src/com/patterns/creational/factorymethod/BEGINNER.md) · [抽象工厂](src/com/patterns/creational/abstractfactory/BEGINNER.md) · [建造者](src/com/patterns/creational/builder/BEGINNER.md) · [原型](src/com/patterns/creational/prototype/BEGINNER.md) |
| 结构型 7 | [适配器](src/com/patterns/structural/adapter/BEGINNER.md) · [桥接](src/com/patterns/structural/bridge/BEGINNER.md) · [组合](src/com/patterns/structural/composite/BEGINNER.md) · [装饰器](src/com/patterns/structural/decorator/BEGINNER.md) · [外观](src/com/patterns/structural/facade/BEGINNER.md) · [享元](src/com/patterns/structural/flyweight/BEGINNER.md) · [代理](src/com/patterns/structural/proxy/BEGINNER.md) |
| 行为型 11 | [责任链](src/com/patterns/behavioral/chainofresponsibility/BEGINNER.md) · [命令](src/com/patterns/behavioral/command/BEGINNER.md) · [解释器](src/com/patterns/behavioral/interpreter/BEGINNER.md) · [迭代器](src/com/patterns/behavioral/iterator/BEGINNER.md) · [中介者](src/com/patterns/behavioral/mediator/BEGINNER.md) · [备忘录](src/com/patterns/behavioral/memento/BEGINNER.md) · [观察者](src/com/patterns/behavioral/observer/BEGINNER.md) · [状态](src/com/patterns/behavioral/state/BEGINNER.md) · [策略](src/com/patterns/behavioral/strategy/BEGINNER.md) · [模板方法](src/com/patterns/behavioral/templatemethod/BEGINNER.md) · [访问者](src/com/patterns/behavioral/visitor/BEGINNER.md) |

## 项目规模（2026-11-08 第五轮实测）

| 指标 | 数值 |
|---|---|
| 模式覆盖 | 23 种 GoF 模式 + 2 个组合示例（orderservice 四模式 / apigateway 六模式） |
| Java 源文件 | **310**（`src/` 下，含 `util` 工具包 2 个、23 个入门 Demo） |
| 工程代码行数 | **35614**（仅 `src/`，物理行） |
| 练习参考实现 | **5** 个单文件（`docs/practice/solutions/`，合计 **2203** 行，均可 `java Xxx.java` 直接跑） |
| 学习文档 | **67** 份 md、**114500** 行：25 份模块 README + 23 份 BEGINNER + 19 份项目级文档 |
| 可视化 | **40** 张 Mermaid 图：UML 图谱 27 张（25 类图 + 2 时序图）、选择指南 3 张决策图、练习题 10 张（类图 / 时序图 / 流程图） |
| 编译产物 | **558** 个 class，`javac --release 25 -Xlint:all` **0 错误 0 告警** |
| 运行验证 | **25** 个工程入口全部退出码 0，stderr **0 行**，stdout 合计 **2531** 行 |
| 文档审计 | **733** 条相对链接全部命中，**失效 0**，围栏全偶数 |

> 统计口径：文件数 = 递归枚举结果；行数 = **换行符个数**（文件末尾没有换行时补 1）。即每个换行符结束一行，不在末尾额外补空行；反过来说，程序输出里**故意打印的空行也算一行**，这也是 25 个入口合计 2531 行（而不是 2526）的原因。
>
> 三个时点（第三轮 / 第四轮 / 第五轮）的同一口径快照见 [docs/05-verification.md §2.7](docs/05-verification.md)；历史上出现过的 114077 / 114331 / 710 / 725 都是**当时**的值，不是当前值。
> 完整验证数据见 [docs/05-verification.md](docs/05-verification.md)。

## 快速开始

### 环境要求
- JDK 25（推荐 Microsoft Build of OpenJDK 25.0.4.1）
- Windows / Linux / macOS

### 编译与运行

项目只依赖 JDK 自带的 `javac` 和 `java`：没有 Maven / Gradle，也没有额外的构建脚本。

**1. 全量编译**（在项目根目录执行，产物输出到 `out/`）

```bash
# Linux / macOS / Git Bash
javac --release 25 -encoding UTF-8 -Xlint:all -d out $(find src -name "*.java")
```

```powershell
# Windows PowerShell
javac --release 25 -encoding UTF-8 -Xlint:all -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
```

`-encoding UTF-8` 不能省：源码含中文注释与字符串字面量，编码不对会直接编译失败。

**2. 运行任意一个入口**

```bash
# 以单例为例
java -cp out com.patterns.creational.singleton.SingletonClient

# 需要跨机器一致的输出字节时，显式固定 stdout 编码
java -Dstdout.encoding=UTF-8 -cp out com.patterns.combined.apigateway.Client
```

**3. 单独跑某个入门 Demo**（单文件源模式，不需要先编译）

```bash
java src/com/patterns/behavioral/strategy/beginner/BeginnerDemo.java
```

**4. 一次跑完全部 25 个工程入口**

25 个入口类名与逐条输出行数见 [docs/05-verification.md](docs/05-verification.md) §2.3；字节数与 SHA256 基线见 §5.1，照着表逐条 `java -cp out …` 执行即可。

## 项目结构

```
design_patterns_pro/
├── README.md                     # 本文件
├── docs/                         # 项目级文档（导航：[docs/README.md](docs/README.md)）
│   ├── 00-source-review.md       # 原项目源码审查报告
│   ├── 01-learning-path.md       # 推荐学习顺序
│   ├── 02-pattern-comparison.md  # 相似模式对比（定义差异 + 代码对照）
│   ├── 03-optimization-log.md    # 项目优化记录（含四轮改造日志）
│   ├── 05-verification.md        # 编译运行验证结果与重跑方法
│   ├── 06-modern-scenarios.md    # 现代场景：JDK / 框架 / 云原生里的模式落点
│   ├── 07-composition-vs-inheritance.md # 组合 vs 继承：23 模式的严谨四分类
│   ├── 08-beginner-teaching-template.md # 入门层八段式教学模板与验收清单
│   ├── 09-beginner-refactor-report.md   # 入门层改造报告与实测验证数据
│   ├── 10-uml-atlas.md           # UML 图谱：25 类图 + 2 时序图 + 图-代码索引
│   ├── pattern-selection-guide.md# 选择指南：从业务需求推导设计
│   └── practice/                 # 综合练习
│       ├── README.md             # 练习总览与使用方法
│       ├── 01-order-discount.md  # 电商订单优惠计算
│       ├── 02-order-state.md     # 订单状态流转
│       ├── 03-notification.md    # 消息通知系统
│       ├── 04-file-export.md     # 报表文件导出
│       ├── 05-api-request.md     # 下单接口处理管线
│       └── solutions/            # 5 份可运行参考实现
└── src/com/patterns/
    ├── creational/               # 创建型（5）
    │   ├── singleton/            # 单例
    │   ├── factorymethod/        # 工厂方法
    │   ├── abstractfactory/      # 抽象工厂
    │   ├── builder/              # 建造者
    │   └── prototype/            # 原型
    ├── structural/               # 结构型（7）
    │   ├── adapter/              # 适配器
    │   ├── bridge/               # 桥接
    │   ├── composite/            # 组合
    │   ├── decorator/            # 装饰器
    │   ├── facade/               # 外观
    │   ├── flyweight/            # 享元
    │   └── proxy/                # 代理
    ├── behavioral/               # 行为型（11）
    │   ├── chainofresponsibility/# 责任链
    │   ├── command/              # 命令
    │   ├── interpreter/          # 解释器
    │   ├── iterator/             # 迭代器
    │   ├── mediator/             # 中介者
    │   ├── memento/              # 备忘录
    │   ├── observer/             # 观察者
    │   ├── state/                # 状态
    │   ├── strategy/             # 策略
    │   ├── templatemethod/       # 模板方法
    │   └── visitor/              # 访问者
    └── combined/                 # 多模式组合应用
        ├── orderservice/         # 策略 + 装饰器 + 责任链 + 工厂
        └── apigateway/           # 责任链 + 策略 + 装饰器 + 状态 + 观察者 + 享元
```

每个模式目录都包含：
- 若干 `.java` 源文件（接口 / 抽象类 / 实现类 / Client）
- 一份 `README.md` 深度文档
- 一份 `BEGINNER.md` 入门文档
- `beginner/BeginnerDemo.java` 单文件入门 Demo（改造前 + 改造后）

## 23 种模式导航

### 创建型（Creational）— 5 种
关注"对象怎么创建"，把对象的创建与使用分离。

| # | 模式 | 一句话 | 入口 | 文档 |
|---|---|---|---|---|
| 1 | 单例 Singleton | 全局只此一份 | [SingletonClient](src/com/patterns/creational/singleton/SingletonClient.java) | [README](src/com/patterns/creational/singleton/README.md) |
| 2 | 工厂方法 Factory Method | 把 new 延迟到子类 | [Client](src/com/patterns/creational/factorymethod/Client.java) | [README](src/com/patterns/creational/factorymethod/README.md) |
| 3 | 抽象工厂 Abstract Factory | 一族产品一起造 | [Client](src/com/patterns/creational/abstractfactory/Client.java) | [README](src/com/patterns/creational/abstractfactory/README.md) |
| 4 | 建造者 Builder | 分步骤造复杂对象 | [Client](src/com/patterns/creational/builder/Client.java) | [README](src/com/patterns/creational/builder/README.md) |
| 5 | 原型 Prototype | 克隆一个现成的 | [Client](src/com/patterns/creational/prototype/Client.java) | [README](src/com/patterns/creational/prototype/README.md) |

### 结构型（Structural）— 7 种
关注"类与对象怎么组合"，用组合获得更灵活的结构。

| # | 模式 | 一句话 | 入口 | 文档 |
|---|---|---|---|---|
| 6 | 适配器 Adapter | 接口转换器 | [Client](src/com/patterns/structural/adapter/Client.java) | [README](src/com/patterns/structural/adapter/README.md) |
| 7 | 桥接 Bridge | 抽象与实现分离 | [Client](src/com/patterns/structural/bridge/Client.java) | [README](src/com/patterns/structural/bridge/README.md) |
| 8 | 组合 Composite | 树形结构统一操作 | [Client](src/com/patterns/structural/composite/Client.java) | [README](src/com/patterns/structural/composite/README.md) |
| 9 | 装饰器 Decorator | 动态叠加功能 | [Client](src/com/patterns/structural/decorator/Client.java) | [README](src/com/patterns/structural/decorator/README.md) |
| 10 | 外观 Facade | 复杂子系统的统一入口 | [Client](src/com/patterns/structural/facade/Client.java) | [README](src/com/patterns/structural/facade/README.md) |
| 11 | 享元 Flyweight | 共享细粒度对象 | [Client](src/com/patterns/structural/flyweight/Client.java) | [README](src/com/patterns/structural/flyweight/README.md) |
| 12 | 代理 Proxy | 控制访问的中间人 | [Client](src/com/patterns/structural/proxy/Client.java) | [README](src/com/patterns/structural/proxy/README.md) |

### 行为型（Behavioral）— 11 种
关注"对象之间怎么协作"，把职责分配清楚。

| # | 模式 | 一句话 | 入口 | 文档 |
|---|---|---|---|---|
| 13 | 责任链 Chain of Responsibility | 请求沿链传递 | [Client](src/com/patterns/behavioral/chainofresponsibility/Client.java) | [README](src/com/patterns/behavioral/chainofresponsibility/README.md) |
| 14 | 命令 Command | 把请求封装成对象 | [Client](src/com/patterns/behavioral/command/Client.java) | [README](src/com/patterns/behavioral/command/README.md) |
| 15 | 解释器 Interpreter | 解释一种语言 | [Client](src/com/patterns/behavioral/interpreter/Client.java) | [README](src/com/patterns/behavioral/interpreter/README.md) |
| 16 | 迭代器 Iterator | 顺序访问聚合元素 | [Client](src/com/patterns/behavioral/iterator/Client.java) | [README](src/com/patterns/behavioral/iterator/README.md) |
| 17 | 中介者 Mediator | 对象间通信的中间人 | [Client](src/com/patterns/behavioral/mediator/Client.java) | [README](src/com/patterns/behavioral/mediator/README.md) |
| 18 | 备忘录 Memento | 保存与恢复状态 | [Client](src/com/patterns/behavioral/memento/Client.java) | [README](src/com/patterns/behavioral/memento/README.md) |
| 19 | 观察者 Observer | 一对多通知 | [Client](src/com/patterns/behavioral/observer/Client.java) | [README](src/com/patterns/behavioral/observer/README.md) |
| 20 | 状态 State | 状态驱动行为 | [Client](src/com/patterns/behavioral/state/Client.java) | [README](src/com/patterns/behavioral/state/README.md) |
| 21 | 策略 Strategy | 算法可替换 | [Client](src/com/patterns/behavioral/strategy/Client.java) | [README](src/com/patterns/behavioral/strategy/README.md) |
| 22 | 模板方法 Template Method | 骨架固定，步骤可变 | [Client](src/com/patterns/behavioral/templatemethod/Client.java) | [README](src/com/patterns/behavioral/templatemethod/README.md) |
| 23 | 访问者 Visitor | 不修改元素类新增操作 | [Client](src/com/patterns/behavioral/visitor/Client.java) | [README](src/com/patterns/behavioral/visitor/README.md) |

### 组合应用
| 模式组合 | 场景 | 入口 | 文档 |
|---|---|---|---|
| 策略 + 装饰器 + 责任链 + 工厂 | 订单折扣系统（20 文件，含旧实现对照） | [Client](src/com/patterns/combined/orderservice/Client.java) | [README](src/com/patterns/combined/orderservice/README.md) |
| 责任链 + 策略 + 装饰器 + 状态 + 观察者 + 享元 | API 网关（24 文件，304 行可复现输出） | [Client](src/com/patterns/combined/apigateway/Client.java) | [README](src/com/patterns/combined/apigateway/README.md) |

## 推荐学习顺序

详见 [docs/01-learning-path.md](docs/01-learning-path.md)。

**速成路线（面试优先）**：
单例 → 工厂方法 → 策略 → 观察者 → 装饰器 → 代理 → 责任链 → 模板方法 → 适配器 → 建造者

每学一个模式，建议顺序是：先读该目录的 `BEGINNER.md` 并运行 `beginner/BeginnerDemo.java`，
能回答「为什么用、什么时候不用」后，再读 `README.md` 和工程版 `Client.java`。

**系统路线（按难度递增）**：
1. 创建型：单例 → 工厂方法 → 抽象工厂 → 建造者 → 原型
2. 结构型：适配器 → 装饰器 → 代理 → 外观 → 组合 → 桥接 → 享元
3. 行为型：策略 → 观察者 → 模板方法 → 命令 → 责任链 → 状态 → 迭代器 → 中介者 → 备忘录 → 访问者 → 解释器

**设计能力路线（本项目主线，建议 6~8 周）**：

| 阶段 | 学什么 | 读完能做什么 | 入口 |
|---|---|---|---|
| 1. 建立直觉 | 任选 5 个模式（建议：策略 / 装饰器 / 责任链 / 观察者 / 模板方法）的 `BEGINNER.md` | 说清「模式解决的是什么问题」 | [入门索引](#入门文档索引) |
| 2. 看穿机制 | [组合 vs 继承](docs/07-composition-vs-inheritance.md) §1~§7 | 解释「为什么用组合而不是继承」 | [07](docs/07-composition-vs-inheritance.md) |
| 3. 分清相似 | [相似模式对比](docs/02-pattern-comparison.md) + [选择指南](docs/pattern-selection-guide.md) §4~§6 | 在四个候选里选出一个并说明理由 | [02](docs/02-pattern-comparison.md) · [指南](docs/pattern-selection-guide.md) |
| 4. 自己推导 | [综合练习](docs/practice/README.md) 任选两题，先做完「你的任务」与自检表再看答案 | 从需求数出变化维度 | [practice](docs/practice/README.md) |
| 5. 看真实组合 | [orderservice](src/com/patterns/combined/orderservice/README.md) · [apigateway](src/com/patterns/combined/apigateway/README.md) | 识别模式之间的 4~6 个接缝 | [combined](src/com/patterns/combined) |
| 6. 联系工程 | [现代应用场景](docs/06-modern-scenarios.md) + [UML 图谱](docs/10-uml-atlas.md) | 在 JDK / Spring / MyBatis / Netty 里认出模式 | [06](docs/06-modern-scenarios.md) |
| 7. 反向能力 | [选择指南 §8 五次过度设计](docs/pattern-selection-guide.md) + 各练习的「过度设计」节 | **判断什么时候不该用模式** | [指南 §8](docs/pattern-selection-guide.md) |

> 第 7 步才是这个项目的终点。能说出「这里不需要抽象」比能说出「这里用策略」难得多。

## 相似模式对比

- 想知道「**定义上**差在哪」→ [docs/02-pattern-comparison.md](docs/02-pattern-comparison.md)（含代码对照）
- 想知道「**同一个需求**为什么最后选了不同模式」→ [docs/pattern-selection-guide.md](docs/pattern-selection-guide.md) §4~§6

快速索引：
- 组合 vs 继承（复用机制，不是模式对比，见 [docs/07-composition-vs-inheritance.md](docs/07-composition-vs-inheritance.md)）
- 工厂方法 vs 抽象工厂 vs 建造者 vs 原型 → [选择指南 §4](docs/pattern-selection-guide.md)
- 适配器 vs 装饰器 vs 代理 vs 外观 vs 桥接 → [选择指南 §5](docs/pattern-selection-guide.md)
- 策略 vs 状态 vs 命令 vs 模板方法 → [选择指南 §6](docs/pattern-selection-guide.md)
- 观察者 vs 中介者 vs 责任链 → [选择指南 §6](docs/pattern-selection-guide.md)
- 组合 vs 装饰器 vs 访问者 → [docs/02-pattern-comparison.md](docs/02-pattern-comparison.md)

## 设计模式选择指南

这是本项目最想让你带走的一份文档：[docs/pattern-selection-guide.md](docs/pattern-selection-guide.md)。

实际工作中，你面对的永远是业务问题，不是模式名字。这份指南不按模式分类，而按**推导过程**组织：

| 章节 | 回答的问题 |
|---|---|
| §0 三个误区 | 为什么「用了模式」不等于「设计好了」 |
| §1 六步推导法 | 变化清单 → 四分法 → 数维度 → 定职责 → 选机制 → 验收标准 |
| §2 何时值得抽象 | 三条硬指标、五种不该抽象、Rule of Three |
| §3 接口/继承/组合 | 成本对照、决策流程图、抽象类 vs 接口 vs sealed |
| §4 创建型四选一 | 同一个「文档导出器」讲四遍，看四种方案的差别 |
| §5 结构型五选一 | 同一个「订单查询加东西」讲五遍 |
| §6 行为型六选一 | 策略 vs 状态、模板方法 vs 策略、链 vs 装饰器、观察者 vs 中介者 |
| §7 订单优惠五版演进 | 同一需求在五个阶段的**不同最优解**（第一版最优解是 if-else） |
| §8 五次过度设计 | 单实现接口、三套 DAO、链做固定三步、观察者做一对一、解释器写配置 |
| §9 决策卡片 | 21 行「如果你看到…先想…再确认…不要…」，可直接贴在工位上 |

## 综合练习

[docs/practice/](docs/practice/README.md) — 五个真实业务任务，每个都按「**先思考，后看答案**」组织。

| # | 主题 | 练的是什么 |
|---|---|---|
| [01](docs/practice/01-order-discount.md) | 电商订单优惠计算 | 把一坨 if-else 拆成三个正交的变化维度 |
| [02](docs/practice/02-order-state.md) | 订单状态流转 | 状态对象 vs 查表，**什么时候数据比对象好** |
| [03](docs/practice/03-notification.md) | 消息通知系统 | 广播为什么不是责任链，也不是中介者 |
| [04](docs/practice/04-file-export.md) | 报表文件导出 | 模板方法和策略**不是二选一** |
| [05](docs/practice/05-api-request.md) | 下单接口处理管线 | 一个机制装不下所有事，剩下的该放哪 |

五份参考实现都可以直接跑：

```bash
java docs/practice/solutions/OrderDiscountSolution.java
```

## 现代场景：模式今天活在哪里

不是「模式过时了吗」，而是**它在哪个类的源码里、哪个框架的扩展点上、哪个云原生组件的配置项后面**。

详见 [docs/06-modern-scenarios.md](docs/06-modern-scenarios.md)，含：

- **§1 JDK 里的 23 个模式**：每个都给出可立即打开的类名（`FilterInputStream`、`Flow`、`Proxy`、`AbstractList`…）
- **§2 框架扩展点**：`BeanPostProcessor` 的四阶段生命周期、Netty `ChannelPipeline` 的双向规则、MyBatis 「代理套链」的两层结构
- **§3 云原生**：Service Mesh 就是搬到进程外的装饰器栈、Sentinel 的固定 SlotChain、Saga 与 Outbox
- **§4 语言在吃掉模式**：`record` / `sealed` + `switch` / 虚拟线程 / lambda 分别吃掉了谁，以及**哪些一个都没被吃掉**
- **§5 十种现代误用**：每条给一条可直接背下来在 code review 时用的判断

## 项目优化记录

详见 [docs/03-optimization-log.md](docs/03-optimization-log.md) 与 [docs/00-source-review.md](docs/00-source-review.md)。

## 编译与运行验证

详见 [docs/05-verification.md](docs/05-verification.md)。入门层 23 个 Demo 的改造范围、逐类规模与已知问题见 [docs/09-beginner-refactor-report.md](docs/09-beginner-refactor-report.md)。

## 许可

**本项目当前缺少 `LICENSE` 文件。** 在没有许可证的情况下，默认版权规则（保留所有权利）会生效：其他人只能查看，不能合法地复制、修改或分发 —— 即使 README 里写了「可自由使用」。

如果要对外开源，建议从下面三种里选一个（按适合程度排序）：

| 许可证 | 适合理由 | 代价 |
|---|---|---|
| **MIT** | 教学项目首选：最短、最好懂、允许任何用途（包括商业），只要求保留版权声明 | 别人可以拿去闭源卖课，你无法阻止 |
| **Apache-2.0** | 比 MIT 多一条显式专利授权和「修改需标注」，对可能被企业引用的教学项目更稳 | 文件长得多，项目根目录要放完整文本 |
| **CC BY-NC-SA 4.0** | 只允许非商业使用，适合「不想被别人拿去卖」的场景 | **不是软件许可证**，与代码分发习惯不匹配；很多公司会回避 |

**推荐 MIT**：这个项目的价值在教学内容，不在代码本身；越少的法律障碍越容易传播。

> ⚠️ 添加许可证是作者的授权决定，本项目**不会**代为创建 `LICENSE` 文件。确定后告诉我，我可以帮忙生成。
>
> 另外，仓库里没有 `CONTRIBUTING.md`。如果以后接受外部 PR，建议补一份，至少写清两件事：
> 1. 新增模式的文档必须同时提供 `BEGINNER.md` + `README.md` + `beginner/BeginnerDemo.java`；
> 2. 代码不得引入任何第三方依赖（这是项目定位的核心约束）。
