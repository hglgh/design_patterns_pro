# Design Patterns Pro — Java 23 种设计模式教学项目

> 基于原生 Java（JDK 25 LTS）实现的 GoF 23 种设计模式学习项目，**不依赖 Spring 等任何框架**。
> 每种模式都配有可独立运行的 Demo + 通俗易懂的 Markdown 学习文档。

## 项目亮点

- **23 种模式全覆盖**：创建型 5 + 结构型 7 + 行为型 11，**含原项目缺失的单例模式**。
- **每种模式独立 Demo**：不强行整合成一个业务系统，每个模式都可单独编译运行。
- **配套学习文档**：每种模式一份 `README.md`，含现实案例、原始实现、设计动机、设计推导、代码解析、执行流程、扩展对比、优缺点与误区、面试与自测 9 大模块。
- **两个真实组合案例**：`orderservice`（策略 + 装饰器 + 责任链 + 工厂）与 `apigateway`（责任链 + 策略 + 装饰器 + 状态 + 观察者 + 享元），后者配 5092 行深度拆解与 304 行可复现输出。
- **现代场景地图**：[docs/06-modern-scenarios.md](docs/06-modern-scenarios.md) 把 23 个模式映射到 JDK 源码、主流框架扩展点与云原生组件上，并回答「record / sealed / 虚拟线程出现后还要不要手写」。
- **原理专文**：[docs/07-composition-vs-inheritance.md](docs/07-composition-vs-inheritance.md) 用装机、奶茶加料、泡茶、文件夹四个现实场景，把「组合 vs 继承」逐条映射到代码，含两个可直接编译运行的最小示例。
- **贴近现实业务**：订单折扣、聊天室、文件系统、播放器、UI 组件库、文档导出、API 网关等场景，让模式用途一目了然。
- **JDK 25 原生**：使用 record、sealed、switch 表达式、var 等现代 Java 特性，但不引入任何第三方依赖。
- **两层教学**：每种模式新增一份通俗 `BEGINNER.md` + 一个单文件 `beginner/BeginnerDemo.java`，先讲「不用的痛点」，再讲「怎么推导」，最后讲「什么时候不用」。

## 两层学习：先入门，再深入

每个模式都准备了两层材料，不需要一上来就啃完整工程版：

| 层级 | 文件 | 适合谁 | 目标 |
|---|---|---|---|
| 第一层：入门 | `BEGINNER.md` + `beginner/BeginnerDemo.java` | 学过 Java 语法，没系统学过设计模式 | 说清「为什么用、什么时候不用」 |
| 第二层：深度 | `README.md` + 工程版 `Client.java` | 已理解动机，要做实现/面试 | 角色、协作、扩展、异常边界 |

入门 Demo 都是**单文件、纯 Java、输出确定**：同一个文件里先跑「改造前」，再跑「改造后」，编译一次就能对照。23 篇入门文档按 [docs/08-beginner-teaching-template.md](docs/08-beginner-teaching-template.md) 的统一八段式写作。

### 入门文档索引

| 分类 | 模式（点击进入 `BEGINNER.md`） |
|---|---|
| 创建型 5 | [单例](src/com/patterns/creational/singleton/BEGINNER.md) · [工厂方法](src/com/patterns/creational/factorymethod/BEGINNER.md) · [抽象工厂](src/com/patterns/creational/abstractfactory/BEGINNER.md) · [建造者](src/com/patterns/creational/builder/BEGINNER.md) · [原型](src/com/patterns/creational/prototype/BEGINNER.md) |
| 结构型 7 | [适配器](src/com/patterns/structural/adapter/BEGINNER.md) · [桥接](src/com/patterns/structural/bridge/BEGINNER.md) · [组合](src/com/patterns/structural/composite/BEGINNER.md) · [装饰器](src/com/patterns/structural/decorator/BEGINNER.md) · [外观](src/com/patterns/structural/facade/BEGINNER.md) · [享元](src/com/patterns/structural/flyweight/BEGINNER.md) · [代理](src/com/patterns/structural/proxy/BEGINNER.md) |
| 行为型 11 | [责任链](src/com/patterns/behavioral/chainofresponsibility/BEGINNER.md) · [命令](src/com/patterns/behavioral/command/BEGINNER.md) · [解释器](src/com/patterns/behavioral/interpreter/BEGINNER.md) · [迭代器](src/com/patterns/behavioral/iterator/BEGINNER.md) · [中介者](src/com/patterns/behavioral/mediator/BEGINNER.md) · [备忘录](src/com/patterns/behavioral/memento/BEGINNER.md) · [观察者](src/com/patterns/behavioral/observer/BEGINNER.md) · [状态](src/com/patterns/behavioral/state/BEGINNER.md) · [策略](src/com/patterns/behavioral/strategy/BEGINNER.md) · [模板方法](src/com/patterns/behavioral/templatemethod/BEGINNER.md) · [访问者](src/com/patterns/behavioral/visitor/BEGINNER.md) |

## 项目规模（实测）

| 指标 | 数值 |
|---|---|
| 模式覆盖 | 23 种 GoF 模式 + 2 个组合示例（orderservice 四模式 / apigateway 六模式） |
| Java 源文件 | **310**（含 `util` 工具包 2 个、23 个入门 Demo） |
| 代码物理行数 | **35614** |
| 学习文档 | **25 份模块 README + 23 份 BEGINNER，合计 48 份模式文档**（含 9 份项目级文档，58 份 md 合计 109335 行） |
| 编译产物 | **558** 个 class，`javac --release 25 -Xlint:all` **0 错误 0 告警** |
| 运行验证 | **25** 个入口全部退出码 0，stderr **0 行**，stdout 合计 **2531** 行 |
| 文档审计 | **58** 个 md、相对链接全部命中，**失效 0**，围栏全偶数 |

> 完整验证数据见 [docs/05-verification.md](docs/05-verification.md)。

## 快速开始

### 环境要求
- JDK 25（推荐 Microsoft Build of OpenJDK 25.0.4.1）
- Windows / Linux / macOS

### 一键编译运行

**Windows**：
```bat
build.bat
```

**Linux / macOS**：
```bash
./build.sh
```

脚本会：
1. 编译全部源码到 `out/` 目录
2. 依次运行每个模式的 `Client.main`，输出结果到控制台

### 单独运行某个模式

```bash
# 编译（-encoding UTF-8 必填：源码含中文）
javac --release 25 -encoding UTF-8 -d out $(find src -name "*.java")

# 运行（以单例为例）
java -cp out com.patterns.creational.singleton.SingletonClient
```

## 项目结构

```
design_patterns_pro/
├── README.md                     # 本文件
├── build.bat / build.sh          # 一键编译运行脚本
├── docs/                         # 项目级文档
│   ├── 00-source-review.md       # 原项目源码审查报告
│   ├── 01-learning-path.md       # 推荐学习顺序
│   ├── 02-pattern-comparison.md  # 相似模式对比
│   ├── 03-optimization-log.md    # 项目优化记录
│   ├── 05-verification.md        # 编译运行验证结果
│   ├── 06-modern-scenarios.md    # 现代场景：JDK / 框架 / 云原生里的模式落点
│   ├── 07-composition-vs-inheritance.md # 组合 vs 继承：设计模式的第一道分水岭
│   ├── 08-beginner-teaching-template.md # 入门层八段式教学模板与验收清单
│   └── 09-beginner-refactor-report.md # 入门层改造报告与实测验证数据
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

## 相似模式对比

详见 [docs/02-pattern-comparison.md](docs/02-pattern-comparison.md)。

快速索引：
- 组合 vs 继承（复用机制，不是模式对比，见 [docs/07-composition-vs-inheritance.md](docs/07-composition-vs-inheritance.md)）
- 工厂方法 vs 抽象工厂 vs 建造者 vs 原型
- 适配器 vs 装饰器 vs 代理 vs 外观
- 策略 vs 状态 vs 命令 vs 模板方法
- 观察者 vs 中介者 vs 发布订阅
- 组合 vs 装饰器 vs 访问者

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

本项目仅用于学习目的，代码可自由使用、修改、分发。
