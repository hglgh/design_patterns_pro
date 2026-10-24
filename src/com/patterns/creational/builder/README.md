# 建造者模式（Builder Pattern）

> **一句话**：把"复杂对象怎么一步步组装出来"从"对象长什么样"里拆出来，让同一个组装流程能产出不同配置，且产品出厂即定型。
>
> 本目录实测：**6 个源文件 / 351 行**，`javac -Xlint:all` 零告警；运行输出 **13 行 / 1182 字节**，
> SHA-256 `BE8BF185A09C825C…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 一致）。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 它解决什么 | 一个对象字段多、可选参数多、构造有顺序要求时，避免构造器参数爆炸和"半成品对象" |
| 它的分水岭是什么 | **产品是不是不可变**。如果 `build()` 后面还能 `setXxx`，那只是"链式 setter"，不是建造者 |
| 它和工厂方法的区别 | 工厂管"造哪个类"（一次性决策）；建造者管"怎么一步步组装"（过程） |
| 它和抽象工厂的区别 | 抽象工厂产**一族**产品；建造者产**一个**复杂产品 |
| 什么时候别用 | 字段 ≤ 4 个且全必填；或者产品本来就该是可变的配置对象 |
| 最容易犯的错 | ① `build()` 直接 `return` 内部那个可变对象（两次 `build` 拿到同一个引用）② Builder 实例被复用，上一次的残留状态污染下一次 |

一句话判断：**"这个对象是不是字段多到没法用构造器写清楚？而且它一旦造好就不该再变？"** 两个都是，才需要它。

## 1. 现实案例：说人话版

### 1.1 三个生活类比

**装机**。你走进电脑店说"我要打游戏"，店员按固定流程走：CPU → 内存 → 硬盘 → 显卡 → 电源 → 机箱。你不关心中间顺序，也不自己拧螺丝。同一套流程，换一份配置单，出来的就是另一台机器。

**快餐套餐**。"一个巨无霸套餐，可乐换无糖、薯条加大、不要酸黄瓜"——你只描述差异，店员知道基础套餐长什么样，把差异叠上去。**默认值 + 可选覆盖**，这就是 Builder 的日常形态。

**装修报价单**。设计师先出一份"基础包"（水电、墙面、地面），再按你的要求加"主材升级""智能家居包"。每一步都是独立的可选决策，最后合成一份完整报价。

### 1.2 业务场景：电脑装配

`Computer` 有 6 个部件：CPU、内存、硬盘、显卡、电源、机箱。其中：
- **必填**：CPU、内存（没有这两样开不了机）。
- **可选**：其余 4 项（都能给默认值）。

**不用 Builder**，你会发现两个都无法接受：

```java
// 写法 A：全参构造器
Computer pc = new Computer("i9-14900K", "64GB DDR5", "2TB NVMe",
                           "RTX 4090", "1200W", "全塔机箱");

// 写法 B：只想配 CPU 和内存呢？
Computer pc = new Computer("i9-14900K", "64GB DDR5", null, null, null, null);
```

写法 B 的 `null` 是什么？读代码的人要跳回构造器数第 4 个参数是谁。**而且 6 个参数全是 `String`，顺序写反了编译器一声不响**——这是比 `null` 更危险的问题。

**用 Builder**：

```java
Computer pc = Computer.builder()
        .cpu("Intel i3-14100")
        .memory("8GB DDR4")
        .build();     // 其余 4 项自动取默认值
```

要什么写什么，没写的用默认值。**读代码的人不需要知道参数顺序**，因为每个值都带了名字。

### 1.3 专业版：GoF 的定义与四个角色

> Separate the construction of a complex object from its representation so that the same construction process can create different representations.
> —— *Design Patterns*, GoF, 1994

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **Builder（抽象建造者）** | 声明分步构造的接口 | `ComputerBuilder` |
| **ConcreteBuilder（具体建造者）** | 实现每一步的具体选型，提供 `getResult()` | `GamingComputerBuilder` / `OfficeComputerBuilder` |
| **Director（指挥者）** | 编排构造步骤的**顺序** | `Director` |
| **Product（产品）** | 被构造的复杂对象 | `Computer` |

GoF 原文里的关键词是 **"same construction process"**：Director 固定流程，Builder 决定每一步填什么。这就是"同一流程、不同表示"。

本项目还额外演示了**简化 Builder**（`Computer.Builder` 静态内部类）——它没有 Director，也就没有独立的"流程"概念，使用方自己决定调用哪些 `set`。这是现代 Java 里更常见的一种，Lombok 的 `@Builder`、`java.net.http.HttpRequest.Builder` 都是这个形态。

### 1.4 JDK 里真实存在的 Builder

**① `java.net.http.HttpRequest.Builder`（最标准）**

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(5))
        .GET()
        .build();
```

`HttpRequest` 是不可变对象（所有字段 `final`，没有 setter），`HttpRequest.newBuilder()` 拿到可变 Builder，`build()` 一次性产出产品。**这是和本项目 `Computer.Builder` 一模一样的结构**，连方法名都对应。

**② `Stream.Builder`**

```java
Stream<String> stream = Stream.<String>builder()
        .add("a").add("b").add("c")
        .build();
```

`Stream.Builder` 只有 `add` 和 `build` 两个动作，是最"瘦"的 Builder。

**③ `Locale.Builder`**

```java
Locale locale = new Locale.Builder()
        .setLanguage("zh")
        .setRegion("CN")
        .build();
```

**④ 反例：`StringBuilder` 不是 GoF 建造者**

```java
StringBuilder sb = new StringBuilder();
sb.append("a").append("b");
String s = sb.toString();
```

`append` 返回 `this`，看起来像链式 Builder，但 **`StringBuilder` 本身就是产品**——没有"可变 Builder + 不可变产品"的分离，`toString()` 之后你还能继续 `append`。它是一个"可变的字符串缓冲"，不是建造者模式。

> 面试里说"StringBuilder 是建造者模式"，大概率会被追问："那它的 Director 在哪？产品为什么还能改？"

## 2. 原始实现：三版代码

### 2.1 v0：伸缩构造器（telescoping constructor）

```java
public class Computer {
    public Computer(String cpu) { ... }
    public Computer(String cpu, String memory) { ... }
    public Computer(String cpu, String memory, String hardDisk) { ... }
    public Computer(String cpu, String memory, String hardDisk, String gpu) { ... }
    public Computer(String cpu, String memory, String hardDisk, String gpu,
                    String powerSupply) { ... }
    public Computer(String cpu, String memory, String hardDisk, String gpu,
                    String powerSupply, String caseType) { ... }
}
```

三个硬伤：

| 硬伤 | 表现 |
|---|---|
| **重载爆炸** | 6 个字段 → 6 个构造器。加一个字段 `cooler`，要再加一个重载 |
| **调用点不可读** | `new Computer("i9", "64GB", null, "RTX4090", null, null)` 里 `null` 是谁？ |
| **顺序错了不报错** | 6 个参数都是 `String`，`new Computer(memory, cpu, ...)` 编译通过，运行时数据错位 |

第三条是真 bug 温床。**类型系统能防的错，不要靠注释和记忆防。**

### 2.2 v1：JavaBean 的 setter 链

```java
Computer pc = new Computer();
pc.setCpu("i9-14900K");
pc.setMemory("64GB DDR5");
// 忘了 setHardDisk 呢？
```

解决了"可读性"，但引入了新问题：**对象存在"半成品"窗口期**。

```java
Computer pc = new Computer();     // 此刻 cpu=null、memory=null
service.deploy(pc);               // 这行代码合法，但 pc 是废的
```

写库、传参、放线程池——任何一步提前泄露引用，都会让一个字段残缺的对象进入系统。**"构造期间对象不可见"这条约束，setter 模式给不了。**

### 2.3 v2：原项目的写法

原项目 `creationtype.builder` 把两种 Builder 都写了，结构上是对的，但有 6 个问题。**这不是"我凭代码看出来的"，是跑探针跑出来的**：

```text
[缺陷 1] 两个同名 Computer 类
  builder.Computer      = creationtype.builder.Computer
  builder.model.Computer = creationtype.builder.model.Computer
  是同一个类吗：false
  → ComputerBuilder 用的是 model 版，Client2 用的是 builder 版
```

**缺陷 1 最严重：一个包里同时存在两个 `Computer` 类。** `creationtype.builder.Computer`（含 Builder，无参构造私有）和 `creationtype.builder.model.Computer`（有 setter，构造公开）。

- `ComputerBuilder` 里写着 `import creationtype.builder.model.Computer`；
- `Client` 用的是 `model` 版（经 `ComputerBuilder` 返回）；
- `Client2` 同包直接用了 `builder` 版。

**两个 Client 演示的竟不是同一个产品类。**

两个类的字段集合都是 cpu / memory / hardDisk / gpu 四个，`toString()` 的格式也几乎一样，所以**肉眼看输出根本分辨不出用的是哪个类**——只有 `getClass().getName()` 能暴露。区别在可见性：`model` 版公开构造 + 公开 setter，`builder` 版私有构造 + 只有 getter + 静态 `Builder`。

```text
[缺陷 2] build() 零校验，能造出空电脑
  一个字段都不设：Computer{cpu='null', memory='null', hardDisk='null', gpu='null'}

[缺陷 3] build() 返回同一个对象，不是新实例
  p1 == p2 ：true

[缺陷 4] build 之后继续改 Builder，已交付的对象跟着变
  build 时的 p1：Computer{cpu='i9', memory='null', hardDisk='null', gpu='null'}
  b.memory("64GB") 之后，p1：Computer{cpu='i9', memory='64GB', hardDisk='null', gpu='null'}

[缺陷 5] 产品完全可变，setter 谁都能调
  绕过 Builder 直接 new + setter：Computer{cpu='i5', memory='null', hardDisk='null', gpu='null'}

[缺陷 6] Director 只有一种流程，换流程要改 Director 源码
  Director.construct() 固定 4 步：Cpu → Memory → HardDisk → Gpu
```

缺陷 2~5 的**根因是同一句话**：原项目的 `Computer.Builder` 是这样写的——

```java
public static class Builder {
    private final Computer computer;

    private Builder() {
        this.computer = new Computer();     // ← 先造产品，再往里面填
    }

    public Builder cpu(String cpu) {
        computer.cpu = cpu;                 // ← 直接改产品字段
        return this;
    }

    public Computer build() {
        return computer;                    // ← 只是把同一个引用交出去
    }
}
```

这就是 **"假 Builder"**：它长得像 Builder（`builder()` 入口、链式方法、`build()` 收尾），但实现上只是给一个可变对象包了个壳。

| 后果 | 原因 |
|---|---|
| 两次 `build()` 是同一个对象 | `return computer` 返回的是字段，不是新实例 |
| `build()` 后再 `set` 会改已交付对象 | 同上，Builder 与已交付对象共享同一个实例 |
| `build()` 无法校验 | 校验要在"一次性赋值"那一刻做，这里没有那一刻 |
| 产品可变 | `model.Computer` 有公开 setter + 公开无参构造 |

### 2.4 六个缺陷的性质与处置

| # | 缺陷 | 性质 | 重构版处置 |
|---|---|---|---|
| F1 | 两个同名 `Computer` 类 | **结构混乱**（同一包内同名类，`import` 决定用哪个） | 合并为一个 `Computer`，6 个字段 |
| F2 | `build()` 零校验 | **真缺陷**（能造出空对象） | `build()` 校验 `cpu` / `memory` 非空，抛 `IllegalStateException` |
| F3 | `build()` 返回内部实例 | **真缺陷**（别名问题） | Builder 只暂存字符串，`build()` 里 `new Computer(this)` |
| F4 | `build()` 后还能改 | 同 F3 | 同上，产品字段全 `final`，Builder 与产品解耦 |
| F5 | 产品有公开 setter | **设计缺陷**（模式价值被抵消） | `Computer` 私有构造 + 全 `final` 字段 + 无 setter |
| F6 | Director 只有一种流程 | 教学缺口 | 增加 `constructMinimal()`，演示"同 Builder、不同流程" |

另外原项目的 `ComputerBuilder` 是**抽象类**且暴露 `protected Computer computer` 字段：

```java
public abstract class ComputerBuilder {
    protected Computer computer = new Computer();   // 子类直接摸字段
    public abstract void buildCpu();
    ...
    public Computer getResult() { return computer; }
}
```

`protected` 字段意味着任何子类都能绕过 `buildXxx()` 直接改产品。**这里没有任何理由用抽象类——没有共享实现，只有共享状态。** 重构版改成 `interface`。

### 2.5 重构版的改进

| 项 | 原项目 | 重构版 |
|---|---|---|
| 产品类 | 两个同名 `Computer` | 一个 `Computer`，6 个 `final` 字段 |
| 产品可变性 | 公开 setter + 公开无参构造 | 私有构造 + 无 setter |
| `build()` 语义 | `return computer`（同一实例） | `new Computer(this)`（每次新实例） |
| 必填校验 | 无 | `cpu` / `memory` 非空，否则抛异常 |
| 默认值 | 无 | 4 个可选部件有默认值 |
| 抽象建造者 | `abstract class` + `protected` 字段 | `interface`，无字段 |
| Director 流程 | 1 种（4 步） | 2 种（6 步 / 3 步） |
| Builder 复用 | 会静默污染（见 §2.3 缺陷 4） | 文档明确"不可复用"，`Client` 用新实例 |

## 3. 设计动机

### 3.1 四个真实动机

| 动机 | 说明 |
|---|---|
| **消灭参数列表** | 6 个以上的参数，任何构造器都会变成"位置谜题" |
| **可选参数有默认值** | 不必为每种组合写一个重载；没写的用默认值 |
| **产品不可变** | 出厂即定型，天然线程安全，可以放心传递 |
| **构造过程可复用** | 同一个 Director 流程，换 Builder 就换一套配置（本项目的 `construct` / `constructMinimal`） |

第三条是工程上最重要的。**不可变对象是所有并发问题的第一道防线**，而 Builder 是"字段多又要不可变"时唯一不丑陋的写法。

### 3.2 什么时候**不该**用建造者

**① 字段 ≤ 4 个且全必填。** 直接构造器。`new Money(amount, currency)` 比 `Money.builder().amount(..).currency(..).build()` 清楚一百倍。

**② 产品本来就该可变。** 比如 `ProcessBuilder` 那种"配置容器"，或者运行期要反复调整的 DTO。硬套不可变只会让人每次改动都重建对象。

**③ 只是"参数多了点"，但没有可选参数、没有默认值、没有顺序要求。** 用参数对象（`record ComputerSpec(cpu, memory, disk, gpu, psu, chassis)`）就够了，一行构造，不需要 `build()`。

**④ 你想解决的是"创建哪个类"。** 那是工厂方法。

**⑤ 你想解决的是"一族产品要配套创建"。** 那是抽象工厂。

### 3.3 三种"造复杂对象"的手段，边界在哪

| 手段 | 适用 | 代价 |
|---|---|---|
| **全参构造器** | 字段 ≤ 4，全必填，顺序天然清晰 | 字段一多就不可读 |
| **参数对象（record）** | 字段多，但**一次性**给出，不需要分步 | 仍然要一次写全，无默认值 |
| **Builder** | 字段多 + 有可选 + 要不可变 + 可能要分步校验 | 多一个内部类，多一次对象创建 |

**参数对象 + Builder 可以叠加**：`Computer.builder().spec(spec).build()`。但对 6 个字段的对象没必要——**这属于为模式而模式**。

## 4. 设计推导：从 v1 到最终代码

### 4.1 第 1 步：先把"参数顺序"这个坑填掉

v0 最致命的不是"参数多"，而是**同为 `String` 的参数可以互换**。第一反应是加个参数对象：

```java
public record ComputerSpec(String cpu, String memory, String hardDisk,
                           String gpu, String powerSupply, String caseType) {}

Computer pc = new Computer(new ComputerSpec("i9", "64GB", "2TB", "4090", "1200W", "全塔"));
```

`record` 让参数**有名字**了，但**顺序问题没解决**——`new ComputerSpec("i9", "64GB", ...)` 里第 3 个还是"硬盘"还是"显卡"全靠记。

> `record` 的价值在"返回值和入参结构化"，不在"防顺序错误"。要防顺序错误只有两条路：Builder（每个值前有方法名），或者用不同类型（`Cpu`、`MemorySize`）。

### 4.2 第 2 步：可选参数 + 默认值，逼出"暂存"这个角色

6 个字段里只有 2 个必填。如果坚持用构造器，要么写 4 个重载，要么让调用方传 `null`。

于是自然想到：**先弄个可变的东西把值攒着，攒够了再一次性造产品。**

```java
public static final class Builder {
    private String cpu;                            // 必填，不给默认值
    private String memory;                         // 必填
    private String hardDisk = "512GB SSD";         // 可选，给默认值
    private String gpu = "集成显卡";
    private String powerSupply = "500W";
    private String caseType = "标准机箱";
}
```

这个"可变的东西"就是 Builder。**它的可变性是被允许的，因为它是过程对象，不是最终产品。**

### 4.3 第 3 步：分水岭——Builder 里存的是"值"还是"产品"

这是本项目最关键的一处推导。有两种写法：

```java
// 写法 A：Builder 持有产品，set 方法往里写（原项目的写法）
private final Computer computer = new Computer();
public Builder cpu(String cpu) { computer.cpu = cpu; return this; }
public Computer build() { return computer; }

// 写法 B：Builder 持有值，build 时一次性赋值（本项目的写法）
private String cpu;
public Builder cpu(String cpu) { this.cpu = cpu; return this; }
public Computer build() { return new Computer(this); }
```

| 维度 | 写法 A（持有产品） | 写法 B（持有值） |
|---|---|---|
| 两次 `build()` | 同一实例（别名） | 两个独立实例 |
| `build()` 后改 Builder | **污染已交付对象** | 无影响 |
| 能否校验 | 只能在 `build()` 里读产品字段，但产品可能已被外部改过 | `build()` 里读的是 Builder 自己的值，干净 |
| 产品能否不可变 | 不能（必须留写入口给 Builder） | 能（`final` 字段 + 私有构造） |

**写法 A 的 `build()` 是一句谎言**——它没有"建造"任何东西，只是把一个已经存在的对象交出去。`p1 == p2` 为 `true` 就是铁证。

所以：**Builder 必须是"值的暂存区"，不是"产品的包装纸"。**

### 4.4 第 4 步：`build()` 里放校验

```java
public Computer build() {
    if (cpu == null || cpu.isBlank()) {
        throw new IllegalStateException("CPU 不能为空");
    }
    if (memory == null || memory.isBlank()) {
        throw new IllegalStateException("内存不能为空");
    }
    return new Computer(this);
}
```

三个设计点：

1. **只校验必填字段**。可选字段有默认值，永远不为 `null`，校验它们没意义。
2. **抛 `IllegalStateException` 而不是 `IllegalArgumentException`**。参数本身合法（`"i9"` 是合法字符串），是"对象状态不完整"，所以是 state 不是 argument。
3. **校验位置在 `build()`，不在 setter**。因为只有到 `build()` 才知道"用户已经设置完了"；在 setter 里校验会阻止"先设 A 再设 B"的正常流程。

### 4.5 第 5 步：要不要 Director

传统 Builder 里有 Director，本项目也写了。但要诚实地说：**Director 在多数业务代码里是多余的。**

它只在一种情况下有真价值：**同一个 Builder，有多套流程**。

```java
public Computer construct() {          // 全配
    builder.buildCpu(); builder.buildMemory(); builder.buildHardDisk();
    builder.buildGpu(); builder.buildPowerSupply(); builder.buildCase();
    return builder.getResult();
}

public Computer constructMinimal() {   // 只核心三件
    builder.buildCpu(); builder.buildMemory(); builder.buildHardDisk();
    return builder.getResult();
}
```

如果只有一套流程，Director 就只是"把 6 行调用从 Client 搬到另一个类里"，是纯粹的仪式。**很多项目的 Builder 根本没有 Director，这不是缺陷。**

### 4.6 第 6 步：Builder 能被复用吗？——本项目踩过的坑

**不能。Builder 是有状态的。**

`Director` 构造时接收一个 `ComputerBuilder`，如果同一个 `Director` 被调用两次，第二次的步骤会**叠加在第一次的状态上**。

本项目的 `Client` 最初就是这么写的：

```java
Director gamingDirector = new Director(new GamingComputerBuilder());
Computer gamingPc = gamingDirector.construct();              // 6 步，字段全填
Computer minimalPc = gamingDirector.constructMinimal();      // 只走 3 步
```

第二行输出是（修复前的真实输出，逐字）：

```text
最小配置（只装核心部件）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='NVIDIA RTX 4090 24GB', powerSupply='1200W 金牌全模组', caseType='全塔电竞机箱（带 RGB）'}
```

**标签写着"最小配置"，内容却是全套。** 因为 `GamingComputerBuilder` 内部那个 `Computer.Builder` 还留着上一次的 6 个值，`constructMinimal()` 只覆盖了前 3 个。

实测三种复用场景（探针输出，逐字）：

```text
[陷阱] 同一个 Director 连调两次 constructMinimal()
  第 1 次（全新 Builder）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}
  第 2 次（复用同一实例）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}
  两次内容是否相同：true

[对照] 换用全新 Builder
  Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}

[陷阱 2] build() 后继续改 Builder，已产出的产品会不会变？
  先 build 再改字段，已产出的对象：Computer{cpu='i5', memory='8GB', hardDisk='512GB SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}

[陷阱 3] 两个产品共用同一个外部 Builder，会不会互相污染？
  a（construct 全流程）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='NVIDIA RTX 4090 24GB', powerSupply='1200W 金牌全模组', caseType='全塔电竞机箱（带 RGB）'}
  c（复用 shared 的 minimal）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='NVIDIA RTX 4090 24GB', powerSupply='1200W 金牌全模组', caseType='全塔电竞机箱（带 RGB）'}
  c 其实是完整配置，说明 minimal 的前 3 步只覆盖了部分字段
```

三段输出分别说明三件事：

| 场景 | 结果 | 含义 |
|---|---|---|
| 同一 Director 连调两次 `constructMinimal()` | 两次结果完全相同 | 因为两次都只覆盖前 3 个字段，残留值恰好一样——**看起来没问题，其实是巧合** |
| `build()` 后继续改 Builder | 已产出的对象**不变** | 本项目的 `build()` 是 `new Computer(this)`，产品和 Builder 已解耦 |
| 两个产品共用同一个外部 Builder | `c` 变成了完整配置 | 这才是复用的真实后果：`constructMinimal()` 只覆盖 3 个字段，另外 3 个是上一次的残留 |

**推论**：

| 结论 | 理由 |
|---|---|
| Builder 不能是单例 | 有状态，共享 = 数据串台 |
| Builder 不能跨线程共享 | 同上，且没有同步 |
| 简化 Builder 天然规避此问题 | `Computer.builder()` 每次返回新实例 |
| 传统 Builder 要由使用方保证"一次一个" | 本项目在 `Client` 里显式 `new` 一个新 Builder |

> 这是"传统 Builder 比简化 Builder 更容易出错"的**具体证据**，不是风格偏好。

### 4.7 第 7 步：最终结构

```text
                        Director（流程）
                        ├─ construct()          6 步
                        └─ constructMinimal()   3 步
                              │ 调用
                              ▼
                    ComputerBuilder（接口）
                    ├─ GamingComputerBuilder  ──持有──▶ Computer.Builder
                    └─ OfficeComputerBuilder  ──持有──▶ Computer.Builder
                                                            │ build()
                                                            ▼
                                                    Computer（不可变产品）
                                                    final × 6，私有构造

       使用方 ──▶ Computer.builder() ──链式──▶ Computer.Builder ──build()──▶ Computer
                  （简化 Builder：跳过 Director，直接操作 Builder）
```

读图要点：**两个入口、一个产品**。左边是传统 Builder（有 Director），右边是简化 Builder（无 Director），两者最终都落到同一个 `Computer.Builder`。

## 5. 代码解析

### 5.1 `Computer`——不可变产品

```java
public final class Computer {

    private final String cpu;
    private final String memory;
    private final String hardDisk;
    private final String gpu;
    private final String powerSupply;
    private final String caseType;

    /** 私有构造器，只能通过 Builder 创建。 */
    private Computer(Builder builder) {
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.hardDisk = builder.hardDisk;
        this.gpu = builder.gpu;
        this.powerSupply = builder.powerSupply;
        this.caseType = builder.caseType;
    }
```

四个细节，每个都能回答"为什么"：

| 细节 | 为什么 |
|---|---|
| `final class` | 不打算被继承。否则子类可以加可变字段，破坏不可变性 |
| 6 个字段全 `final` | 出厂即定型。没有 `final`，"不可变"只是一句注释 |
| 构造器 `private` | **唯一入口原则**。如果公开，就有人 `new Computer()` 造半成品 |
| 构造器参数是 `Builder` | 避免 6 个参数的构造器——那样等于把 v0 的问题搬进产品类 |

注意构造器直接读 `builder.cpu`（私有字段）而不是 `builder.getCpu()`：**外部类和内部类是同一个类的两个部分**，可以直接访问私有成员。这是 Java 内部类的特性，不是封装被破坏。

### 5.2 `Computer.Builder`——简化建造者

```java
public static final class Builder {
    // 必填字段
    private String cpu;
    private String memory;
    // 可选字段
    private String hardDisk = "512GB SSD";       // 默认值
    private String gpu = "集成显卡";
    private String powerSupply = "500W";
    private String caseType = "标准机箱";

    public Builder cpu(String cpu) { this.cpu = cpu; return this; }
    public Builder memory(String memory) { this.memory = memory; return this; }
    ...

    public Computer build() {
        if (cpu == null || cpu.isBlank()) {
            throw new IllegalStateException("CPU 不能为空");
        }
        if (memory == null || memory.isBlank()) {
            throw new IllegalStateException("内存不能为空");
        }
        return new Computer(this);
    }
}
```

逐点解释：

**① 为什么 `static`。** 非静态内部类会隐式持有外部 `Computer` 引用，而 `Computer` 又是通过 Builder 创建的——逻辑上互为前提，没意义。`static` 让 `Builder` 独立存在，`Computer` 里只留一个 `builder()` 工厂方法。

**② 为什么 `final`。** Builder 不该被继承。子类可能覆盖 `build()` 跳过校验，或覆盖 `cpu()` 做别的事。

**③ 必填字段不给默认值。** 给了默认值就等于"必填"变"可选"。`cpu = null` + `build()` 校验，是唯一可靠的"必填"表达方式。

**④ 可选字段给默认值。** 这样 `build()` 里不用为可选字段写校验——它们永远非 `null`。**默认值本质上把"可选"这个状态消除掉了。**

**⑤ `build()` 用 `isBlank()` 而不是只判 `null`。** `builder().cpu("  ")` 能通过 `null` 检查，但显然不是合法 CPU。`isBlank()` 挡住了纯空白。

**⑥ `build()` 每次都 `new Computer(this)`。** 这是与"假 Builder"的分界线（§4.3）。

### 5.3 `ComputerBuilder`——抽象建造者（接口）

```java
public interface ComputerBuilder {
    void buildCpu();
    void buildMemory();
    void buildHardDisk();
    void buildGpu();
    void buildPowerSupply();
    void buildCase();
    Computer getResult();
}
```

为什么是接口不是抽象类？**因为没有任何可共享的实现，也没有可共享的状态。**

原项目是 `abstract class` + `protected Computer computer` 字段，那个字段让所有子类共享同一份"裸产品"，是缺陷 F5 的直接来源。

`getResult()` 而不是 `getProduct()` / `build()`：刻意与 `Computer.Builder.build()` 区分开，避免读者以为这两个是同一层的东西。名字不影响功能，但影响理解成本。

### 5.4 两个具体建造者

```java
public class GamingComputerBuilder implements ComputerBuilder {

    private final Computer.Builder builder = Computer.builder();

    @Override
    public void buildCpu() {
        builder.cpu("Intel i9-14900K");
    }

    @Override
    public void buildGpu() {
        builder.gpu("NVIDIA RTX 4090 24GB");
    }
    ...
    @Override
    public Computer getResult() {
        return builder.build();      // 校验在这里发生
    }
}
```

关键设计：**具体建造者的内部仍然是 `Computer.Builder`，不是 `Computer`。**

有人会问："传统 Builder 怎么还在用简化 Builder？这不是套娃吗？"

答案是：**它们分工不同。**
- `ComputerBuilder` 解决的是"每步选什么"（`buildCpu` 填 i9 还是 i5）；
- `Computer.Builder` 解决的是"值往哪攒、怎么校验、怎么定型"。

如果没有 `Computer.Builder`，`GamingComputerBuilder` 就得自己维护 6 个字段 + 自己写校验——两个具体建造者会重复这两件事。**复用"攒值 + 校验"的逻辑，是这里套娃的正当理由。**

`private final Computer.Builder builder`：`final` 保证不会被换成另一个容器，`private` 保证外部拿不到它——否则外部绕过 `getResult()` 直接 `builder.build()`，流程就乱了。

### 5.5 `Director`——指挥者

```java
public class Director {

    private final ComputerBuilder builder;

    public Director(ComputerBuilder builder) {
        this.builder = builder;
    }

    public Computer construct() {
        builder.buildCpu();
        builder.buildMemory();
        builder.buildHardDisk();
        builder.buildGpu();
        builder.buildPowerSupply();
        builder.buildCase();
        return builder.getResult();
    }

    public Computer constructMinimal() {
        builder.buildCpu();
        builder.buildMemory();
        builder.buildHardDisk();
        return builder.getResult();
    }
}
```

三个点：

**① 构造器注入 `ComputerBuilder`。** Director 不知道具体是游戏机还是办公机——这就是"流程与表示分离"的落地。

**② 没有 `Objects.requireNonNull`。** 与其他模块（如 `NotificationService`）不一致：`new Director(null)` 会构造成功，直到 `construct()` 才 NPE。这是一个**已知的小瑕疵**——按理应该加，本轮没动，如实记录在此。

**③ 两个方法共用一个 `builder` 字段，所以 Director 不可复用。** 见 §4.6。这是本项目留下的教学反面案例：`Client` 里显式用 `new Director(new GamingComputerBuilder())` 规避它，并在注释里写明原因。

### 5.6 `Client`——三种用法

```java
// 方式 1：传统 Builder（Director 编排）
Director gamingDirector = new Director(new GamingComputerBuilder());
Computer gamingPc = gamingDirector.construct();

// 方式 2：简化 Builder（链式调用）
Computer customPc = Computer.builder()
        .cpu("AMD Ryzen 9 7950X")
        .memory("32GB DDR5")
        .hardDisk("1TB NVMe SSD")
        ...
        .build();

// 方式 3：校验必填字段
try {
    Computer.builder().memory("16GB").build();     // 没设 CPU
} catch (IllegalStateException e) {
    System.out.println("构建失败（符合预期）：" + e.getMessage());
}
```

方式 3 是**故意让程序抛异常再捕获**——演示"校验真的生效"。比在文档里写"它会校验"有说服力。

## 6. 执行流程

以"传统 Builder 造游戏电脑"为例：

```text
1. new GamingComputerBuilder()
     └─ 字段初始化：private final Computer.Builder builder = Computer.builder()
          └─ new Computer.Builder()（cpu=null, memory=null，其余 4 项为默认值）

2. new Director(builder)
     └─ this.builder = builder（只存引用，不复制）

3. gamingDirector.construct()
     ├─ builder.buildCpu()        → 动态分派到 GamingComputerBuilder
     │    └─ builder.cpu("Intel i9-14900K")
     ├─ builder.buildMemory()     → builder.memory("64GB DDR5 6000MHz")
     ├─ builder.buildHardDisk()   → builder.hardDisk("2TB NVMe Gen5 SSD")
     ├─ builder.buildGpu()        → builder.gpu("NVIDIA RTX 4090 24GB")
     ├─ builder.buildPowerSupply()→ builder.powerSupply("1200W 金牌全模组")
     ├─ builder.buildCase()       → builder.caseType("全塔电竞机箱（带 RGB）")
     └─ builder.getResult()
          └─ Computer.Builder.build()
               ├─ 校验 cpu 非空 → 通过
               ├─ 校验 memory 非空 → 通过
               └─ new Computer(this)   ← 此刻 6 个字段一次性赋值，全部 final
                    └─ return Computer

4. System.out.println(gamingPc)
     └─ Computer.toString() → 输出 6 个字段
```

**两次动态分派**：
1. `Director` 里的 `builder.buildXxx()` → 分派到 `GamingComputerBuilder` 还是 `OfficeComputerBuilder`；
2. `getResult()` → 分派到各具体建造者（这里两者实现相同，但接口允许不同）。

**关键时序**：`Computer` 实例在 `new Computer(this)` 那一刻才存在。之前所有操作都在改 Builder，产品字段一个都没被写过。这就是"不会出现半成品对象"的机制保证。

### 6.1 完整输出（13 行，逐字）

```text
========== 建造者模式演示 ==========

--- 方式 1：传统 Builder ---
游戏电脑：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='NVIDIA RTX 4090 24GB', powerSupply='1200W 金牌全模组', caseType='全塔电竞机箱（带 RGB）'}
办公电脑：Computer{cpu='Intel i5-14400', memory='16GB DDR4 3200MHz', hardDisk='512GB NVMe SSD', gpu='集成显卡 Intel UHD 730', powerSupply='400W 铜牌', caseType='小型静音机箱'}
最小配置（只装核心部件）：Computer{cpu='Intel i9-14900K', memory='64GB DDR5 6000MHz', hardDisk='2TB NVMe Gen5 SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}

--- 方式 2：简化 Builder（链式调用）---
自定义电脑：Computer{cpu='AMD Ryzen 9 7950X', memory='32GB DDR5', hardDisk='1TB NVMe SSD', gpu='AMD RX 7900 XTX', powerSupply='850W 金牌', caseType='海景房机箱'}
入门电脑（其余用默认值）：Computer{cpu='Intel i3-14100', memory='8GB DDR4', hardDisk='512GB SSD', gpu='集成显卡', powerSupply='500W', caseType='标准机箱'}

--- 方式 3：校验必填字段 ---
构建失败（符合预期）：CPU 不能为空
```

第 6 行是重点：**`最小配置` 只有前 3 项来自 `GamingComputerBuilder`，后 3 项是 `Computer.Builder` 的默认值**（`集成显卡` / `500W` / `标准机箱`），不是上一次 `construct()` 的残留。这正是 §4.6 那个坑修好之后的正确行为。

### 6.2 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/builder/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.builder.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 13 |
| 字节数 | 1182（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `BE8BF185A09C825C` |
| 确定性 | 连跑两次逐行 diff 一致 |

> 若不加 `-Dstdout.encoding=UTF-8`，Windows 控制台默认 GBK，中文占 2 字节，字节数与哈希都会变——**行数不变**。这是编码口径差异，不是代码被改过（详见 `docs/05-verification.md` §4.1.1）。

## 7. 扩展对比

### 7.1 新增"工作站电脑"：1 个类，0 处修改

```java
public class WorkstationComputerBuilder implements ComputerBuilder {

    private final Computer.Builder builder = Computer.builder();

    @Override public void buildCpu() { builder.cpu("AMD Threadripper 7980X"); }
    @Override public void buildMemory() { builder.memory("128GB DDR5 ECC"); }
    @Override public void buildHardDisk() { builder.hardDisk("4TB NVMe Gen5"); }
    @Override public void buildGpu() { builder.gpu("NVIDIA RTX A6000"); }
    @Override public void buildPowerSupply() { builder.powerSupply("1600W 钛金"); }
    @Override public void buildCase() { builder.caseType("静音工作站机箱"); }
    @Override public Computer getResult() { return builder.build(); }
}
```

`Director` / `Computer` / `ComputerBuilder` / 已有两个 Builder **全部零修改**。

对比"不用建造者"：必须给 `Computer` 加一个 6 参构造器重载，或者改现有构造器签名。每加一种配置就改一次产品类——**产品类成了所有配置的注册表**。

### 7.2 诚实评价：本项目里 Director 值不值

| 维度 | 评价 |
|---|---|
| 演示价值 | 高——`construct` vs `constructMinimal` 直观展示了"同 Builder、不同流程" |
| 生产价值 | 中——多数业务只有一套流程，Director 沦为"搬运 6 行调用" |
| 风险 | 有——Director 持有有状态的 Builder，复用即污染（§4.6） |

**如果重写一遍，我会保留 Director 作为教学对照，但会在类注释里直接写明"仅当存在多套流程时才值得引入"。** 现在的注释偏中性，容易让人以为 Director 是必需角色。

### 7.3 用 `record` 替代（Java 16+）

如果所有字段都必填、也不需要分步，`record` 是更好的选择：

```java
public record Computer(String cpu, String memory, String hardDisk,
                       String gpu, String powerSupply, String caseType) {}
```

一行得到：构造器、`equals` / `hashCode` / `toString`、不可变性。**没有 Builder 的样板代码。**

但 `record` 有两个限制：

| 限制 | 后果 |
|---|---|
| 不能有默认值 | 6 个字段必须全给，等价于 v0 的"全参构造器" |
| 参数顺序仍靠记 | `new Computer(a, b, c, ...)` 的顺序错误照样编译通过 |

**决策**：全必填 + 字段少 → `record`；有可选 + 要默认值 + 字段多 → Builder。本项目属于后者。

`record` 也可以和 Builder 组合：

```java
public record Computer(String cpu, ..., String caseType) {
    public static Builder builder() { return new Builder(); }
    public static final class Builder { ... }
}
```

这样 `equals` / `toString` 不用手写。本项目为了展示"纯手写 Builder"的完整形态，没有用 `record`——**两种都能接受，没有对错**。

### 7.4 Builder 做"分步校验"，而不是"最后一次性校验"

本项目的校验集中在 `build()`。也可以分步校验：

```java
public Builder cpu(String cpu) {
    if (cpu == null || cpu.isBlank()) {
        throw new IllegalArgumentException("CPU 不能为空");
    }
    this.cpu = cpu;
    return this;
}
```

| 方案 | 优点 | 缺点 |
|---|---|---|
| **集中校验（本项目）** | 错误信息统一；允许"先设别的再回来补必填" | 错误发生点离原因远（`build()` 才知道） |
| **分步校验** | 错误立即暴露，栈更短 | 无法表达"必须先设 A 才能设 B"这类跨字段约束 |

**跨字段约束只能集中校验**，例如"选了 RTX 4090 就必须配 ≥ 850W 电源"：

```java
public Computer build() {
    if (cpu == null || cpu.isBlank()) throw new IllegalStateException("CPU 不能为空");
    if (memory == null || memory.isBlank()) throw new IllegalStateException("内存不能为空");
    if (gpu != null && gpu.contains("4090") && !powerSupply.contains("850W")) {
        throw new IllegalStateException("RTX 4090 需要 850W 以上电源，当前：" + powerSupply);
    }
    return new Computer(this);
}
```

**这条约束放在任何单个 setter 里都写不出来**——设 `gpu` 时电源可能还没设。这是"校验必须在 `build()`"的最强理由。

### 7.5 继承式 Builder（泛型 self-type）——为什么本项目不用

想让 `GamingComputerBuilder extends ComputerBuilder` 并保持链式返回子类型，需要泛型自引用：

```java
public abstract class BaseBuilder<T extends BaseBuilder<T>> {
    @SuppressWarnings("unchecked")
    protected T self() { return (T) this; }

    public T cpu(String cpu) { this.cpu = cpu; return self(); }
}
```

**本项目不用它，三个理由**：

1. **不需要继承 Builder**。两个具体建造者都是"填值"，没有共享逻辑要继承。
2. **`@SuppressWarnings("unchecked")` 是设计味道**。需要强转才能表达的继承关系，通常说明继承用错了。
3. **组合优于继承**。`GamingComputerBuilder` 内部**持有**一个 `Computer.Builder`，比继承它更灵活——将来要换成 `record` 版 Builder，只改一行。

### 7.6 Lombok `@Builder` 与本项目手写版的差异

| 项 | Lombok `@Builder` | 本项目 |
|---|---|---|
| 代码量 | 0 行（注解生成） | ~30 行内部类 |
| 默认值 | 靠字段初始化器 | 同 |
| 校验 | **不生成**，要自己写 `build()` | 手写 |
| 必填表达 | 需要 `@Builder.Default` + 手写 `build()` 校验 | 字段不初始化 + `build()` 校验 |
| 可调试性 | 生成代码在 `target/generated-sources` | 源码里就能看 |
| `toBuilder()` | `@Builder(toBuilder = true)` 一行 | 要手写 `toBuilder()` |

**Lombok 省的是样板，不是设计。** 它生成的 Builder 仍然是"值暂存 + `build()` 新建对象"的正确形态（不会犯原项目"返回同一实例"的错）。真正的差异在**校验**——Lombok 不管你，必须自己补 `build()`。

> 本项目的 `build()` 校验是手写的，这正是 Lombok 无法替代的部分。

### 7.7 Builder 的线程安全与对象池

| 问题 | 答案 |
|---|---|
| Builder 线程安全吗 | **不安全**。字段是普通 `String`，无同步 |
| 产品线程安全吗 | **安全**。全 `final` 字段，安全发布后不可变 |
| 能缓存 Builder 吗 | **不能**。有状态，复用即串台（§4.6 实测） |
| 能缓存 `Computer` 吗 | 可以。不可变对象共享没有风险 |
| 需要 `ThreadLocal<Builder>` 吗 | 只在"高频构造同类对象"时考虑，且要先测出瓶颈 |

**"产品不可变"是这里最有价值的性质**：一旦 `Computer` 造好，你可以把它放进 `HashMap` 当 key、传给任意线程、作为缓存值——**不需要任何防御性拷贝**。

### 7.8 与五个模式的分界

| 模式 | 关注点 | 与本模式的区别 |
|---|---|---|
| **工厂方法** | 创建**哪个类** | 工厂不关心构造过程；Builder 的核心就是构造过程 |
| **抽象工厂** | 创建**一族**产品 | 抽象工厂一次给出多个产品；Builder 分步拼一个产品 |
| **原型** | 通过**克隆**创建 | 原型复制已有实例（适合构造昂贵）；Builder 从零分步拼装 |
| **模板方法** | 固定**算法骨架**，子类填步骤 | 结构极像！区别：模板方法操作的是 `this`（产品就是自己），Builder 操作的是另一个对象 |
| **组合** | 树形结构递归处理 | 组合是"整体-部分"的**结构**；Builder 是"分步构造"的**过程** |

**建造者 vs 模板方法**是最容易混的一对。两者都是"父类定流程、子类填步骤"，区别在**产品是谁**：

- 模板方法：`this` 就是产品（`OrderProcessTemplate` 的子类自己就是订单处理器）；
- 建造者：产品是**另一个对象**（`GamingComputerBuilder` 造的是 `Computer`，不是自己）。

## 8. 优缺点与误区

### 8.1 优点

1. **参数有名字**。`builder.gpu("4090")` 不可能被误解成"硬盘"。
2. **可选参数有默认值**。不必为组合写重载，也不必传 `null`。
3. **产品不可变**。全 `final` + 私有构造，安全发布后天然线程安全。
4. **构造过程可复用**。`Director` 一套流程配多个 Builder；`Computer.Builder` 一套攒值逻辑配多个具体建造者。
5. **校验有唯一落点**。跨字段约束只能在这里写（§7.4）。
6. **构造过程可观测**。每一步都能打日志、计时、做权限检查。

### 8.2 缺点

1. **类数量增加**。本项目 1 个产品 + 1 个内部 Builder + 1 个接口 + 2 个具体 Builder + 1 个 Director = 6 个文件。
2. **多一次对象创建**。`new Computer(this)` 之外还有个 Builder 实例。现代 JVM 下可忽略，但**在高频路径上确实多一次分配**（逃逸分析常常能消除）。
3. **Builder 有状态，容易被误复用**。这是本项目实测踩到的坑（§4.6）。
4. **读代码多一跳**。`builder.cpu(...)` 到底写进哪个字段，要跳到 Builder 类。
5. **对"字段少"的对象是纯负担**。6 个以下字段、全必填，构造器更好。

### 8.3 十个误区

| 误区 | 真相 |
|---|---|
| "链式 setter 就是建造者" | 链式 setter 返回 `this`，产品仍可变。Builder 必须有"值暂存 + `build()` 定型"这一步 |
| "Builder 里存产品就行" | 那是"假 Builder"（原项目的错）。`build()` 返回同一实例、`build()` 后还能改，全由此而来 |
| "`build()` 可以返回 `this` 里的对象" | 除非你希望两次 `build()` 是同一个对象。正确做法是每次 `new` |
| "Builder 可以复用" | 不能。有状态，第二次调用会继承第一次的残留（§4.6 实测 `c` 变成全套配置） |
| "Builder 必须是单例" | 反了。**绝不能**是单例 |
| "有 Director 才叫 Builder" | Director 是可选的。Lombok、`HttpRequest.Builder`、本项目简化 Builder 都没有 |
| "Builder 一定比构造器好" | 字段 ≤ 4 且全必填时，构造器更好读。Builder 是**复杂对象**的工具 |
| "Builder 能替代工厂" | 工厂解决"造哪个类"，Builder 解决"怎么组装"。两者常一起用 |
| "Builder 能保证线程安全" | Builder 自己不安全；**产品**才安全（靠 `final`） |
| "`toString()` 打印出来对就行" | 原项目两个同名 `Computer` 的 `toString` 格式几乎一样，肉眼分辨不出用的是哪个类（§2.3 缺陷 1） |

### 8.4 出现这些信号，说明你在滥用 Builder

- 对象只有 2~4 个字段，且全必填 → 用构造器或 `record`。
- `build()` 里只有 `return new Xxx(this)`，没有任何校验 → 那 Builder 的唯一价值是"参数有名字"，考虑 `record`。
- Builder 的每个 setter 都只是 `this.x = x; return this;`，且字段全必填 → 同 `record`。
- 代码里出现 `static final Builder INSTANCE` → **危险**，有状态对象不能共享。
- `build()` 返回的对象还能 `set` → 说明产品可变，Builder 的价值被抵消了一大半。
- 有人写 `builder.build().builder()` 想"基于已有对象改一个字段" → 那是 `toBuilder()` 的职责，不是重新 `builder()`。

## 9. 面试与自测

### 9.1 30 秒版

> 建造者模式把复杂对象的构造过程和最终表示分离。四个角色是抽象建造者、具体建造者、指挥者、产品。传统形态由 Director 编排固定流程、Builder 决定每步选型；简化形态用静态内部类 + 链式调用 + `build()` 校验，Lombok `@Builder` 和 `HttpRequest.Builder` 都是这个形态。它的核心价值是：参数有名字、可选参数有默认值、产品不可变、跨字段校验有唯一落点。

### 9.2 2 分钟版

> 我拿电脑装配举例。`Computer` 有 6 个部件，只有 CPU 和内存必填。不用 Builder 只有两条路：6 参构造器（`null` 满天飞、顺序错了还不报错）或者 setter（对象存在半成品窗口期）。
>
> Builder 的解法是：先弄一个可变的值暂存区，攒够了再一次性造产品。所以 `Computer.Builder` 里存的是 6 个 `String`，不是 `Computer`；`build()` 里先校验 `cpu` 和 `memory`，再 `new Computer(this)` 把值一次性赋给 6 个 `final` 字段。产品是真正不可变的，出厂即定型。
>
> 我在原项目里实测出 6 个缺陷，最狠的两个：一是同一个包里有**两个同名 `Computer` 类**，`ComputerBuilder` 和 `Client2` 用的竟不是同一个；二是 `build()` 写成 `return computer`，直接返回内部那个可变对象，导致 `p1 == p2` 为 `true`，而且 `build()` 之后再调 `set` 会改到已经交付的对象。这说明**Builder 必须是值的暂存区，不是产品的包装纸**。
>
> 还有一点容易被忽略：**Builder 有状态，不能复用**。我原来的 `Client` 用同一个 Director 连调 `construct()` 和 `constructMinimal()`，第二次输出里"最小配置"却是全套 RTX 4090——因为 Builder 里还留着上一次的值。修法是每次 `new` 一个新 Builder。
>
> 关于 Director：它只在"同一个 Builder 有多套流程"时才有价值。多数业务代码只有一套流程，Director 就是纯粹的搬运工，可以不要。

### 9.3 追问链

| 追问 | 要点 |
|---|---|
| Builder vs 工厂方法 | 工厂管"造哪个类"（一次性）；Builder 管"怎么一步步组装"（过程） |
| Builder vs 抽象工厂 | 抽象工厂产一族产品；Builder 产一个复杂产品 |
| Builder vs 模板方法 | 都是"父类定流程、子类填步骤"；区别是**产品是谁**：模板方法里 `this` 就是产品，Builder 里产品是另一个对象 |
| 为什么 `build()` 要返回新对象 | 否则两次 `build()` 是同一实例（别名），且 `build()` 后改 Builder 会污染已交付对象 |
| Builder 能复用吗 | 不能。有状态。复用会继承上一次的残留值 |
| Builder 线程安全吗 | 不安全。产品安全（`final` + 安全发布） |
| 为什么必填字段不给默认值 | 给了默认值就等于把"必填"降级成"可选"。`null` + `build()` 校验是唯一可靠的表达 |
| 校验为什么放在 `build()` | 跨字段约束（如"4090 要配 ≥850W 电源"）在任何单个 setter 里都写不出来 |
| 为什么抛 `IllegalStateException` | 参数本身合法，是"对象状态不完整"，属于 state 不属于 argument |
| Lombok `@Builder` 是建造者模式吗 | 是。但它不生成校验，`build()` 要自己补 |
| `StringBuilder` 是建造者模式吗 | 不是。`StringBuilder` 自己就是产品，没有"可变 Builder + 不可变产品"的分离 |
| `record` 能替代 Builder 吗 | 全必填 + 字段少可以；有可选参数或要默认值时不行 |
| 什么时候该用 Director | 只有当"同一个 Builder 有多套流程"时。单流程时它是仪式 |

### 9.4 自测题

**1. 下面这个 Builder 有什么问题？**

```java
public static class Builder {
    private final Computer computer = new Computer();
    public Builder cpu(String cpu) { computer.cpu = cpu; return this; }
    public Computer build() { return computer; }
}
```

<details><summary>参考答案</summary>

三个问题：① **`build()` 返回内部实例**，两次 `build()` 得到同一对象（`p1 == p2` 为 `true`）；② **`build()` 之后再调 `cpu()` 会改到已交付对象**；③ **产品必须可变**（要有可写的 `cpu` 字段），与"Builder 支持不可变产品"的初衷矛盾。

正确写法：Builder 只存值，`build()` 里 `new Computer(this)`。原项目就是踩了这个坑，实测 `p1 == p2` 为 `true`。
</details>

**2. 为什么必填字段不给默认值？**

<details><summary>参考答案</summary>

因为默认值会把"必填"变成"可选"。如果 `cpu = "默认 CPU"`，那么 `Computer.builder().build()` 就能通过——但调用方根本没有表达过"我要这个 CPU"。

可靠的做法是：必填字段声明为 `null`，在 `build()` 里检查 `null` / `isBlank()` 并抛异常。**"缺失"必须是一种可检测的状态**，默认值把这种状态抹掉了。
</details>

**3. `build()` 里为什么用 `isBlank()` 而不是只判 `null`？**

<details><summary>参考答案</summary>

`builder().cpu("   ")` 能通过 `null` 检查，但三个空格显然不是合法 CPU 型号。`isBlank()`（Java 11+）同时挡住 `null`-安全前提下的空串和纯空白。

注意要先判 `null` 再调 `isBlank()`——`null.isBlank()` 会 NPE。所以写法是 `cpu == null || cpu.isBlank()`。
</details>

**4. 本项目为什么把 `ComputerBuilder` 设计成接口，而不是抽象类？**

<details><summary>参考答案</summary>

因为它**没有可共享的实现，也没有可共享的状态**。原项目写成 `abstract class` 并暴露 `protected Computer computer` 字段，结果是：① 所有子类共享同一份"裸产品"；② 任何子类都能绕过 `buildXxx()` 直接改产品；③ 这个字段是缺陷 F5（产品可变）的直接来源。

没有共享实现时，接口是更准确的表达——**它只声明契约，不携带任何状态**。
</details>

**5. 用同一个 Director 连调两次 `construct()` 会怎样？为什么？**

<details><summary>参考答案</summary>

第二次的结果会**叠加第一次的状态**（因为 `GamingComputerBuilder` 内部那个 `Computer.Builder` 没被重置）。本项目实测：先 `construct()` 再 `constructMinimal()`，"最小配置"输出了全套 RTX 4090 / 1200W 电源 / 全塔机箱。

根因：**Builder 是有状态的容器**，`Director` 只是持有它的引用，不会每次新建。修法：每次构造前 `new` 一个全新的 Builder。
</details>

**6. Builder 和模板方法都"父类定流程、子类填步骤"，区别在哪？**

<details><summary>参考答案</summary>

**产品是谁。**

- 模板方法：`this` 就是产品。`OrderProcessTemplate.processOrder()` 是 `final`，子类填的 `pay()` / `deliver()` 都是**自己**的方法，操作的是**自己**的状态。
- 建造者：产品是**另一个对象**。`GamingComputerBuilder.buildCpu()` 操作的是 `Computer.Builder`，最终产出的是 `Computer`。

一句话：模板方法是"我按流程做我的事"，建造者是"我按流程造一个别人"。
</details>

**7. `record` 能完全替代 Builder 吗？**

<details><summary>参考答案</summary>

不能。`record` 有两个硬限制：① **不能有默认值**——所有字段必须显式给出；② **参数顺序仍靠记**——`new Computer(a, b, c...)` 顺序错了照样编译通过。

所以决策是：全必填 + 字段少 → `record`；有可选参数 + 要默认值 + 字段多 → Builder。

两者可以组合：`record Computer(...)` 内部再放一个 `static final class Builder`，这样 `equals` / `hashCode` / `toString` 不用手写。本项目为了展示纯手写形态没有这样做，属于风格选择。
</details>

**8. 手写题：给 `Computer` 加一条约束"选 RTX 4090 必须配 850W 以上电源"，写在哪？**

<details><summary>参考答案</summary>

只能写在 `build()`：

```java
public Computer build() {
    if (cpu == null || cpu.isBlank()) throw new IllegalStateException("CPU 不能为空");
    if (memory == null || memory.isBlank()) throw new IllegalStateException("内存不能为空");
    if (gpu != null && gpu.contains("4090") && !powerSupply.contains("850W")) {
        throw new IllegalStateException("RTX 4090 需要 850W 以上电源，当前：" + powerSupply);
    }
    return new Computer(this);
}
```

**为什么不能放在 setter 里**：设 `gpu` 时 `powerSupply` 可能还没设（拿到的是默认值 `500W`），会误报；设 `powerSupply` 时 `gpu` 可能还没设，也会误报。**跨字段约束天然只能在"所有字段都就位"的时刻校验**，那个时刻就是 `build()`。
</details>

**9. 判断对错：Builder 应该是单例，避免重复创建。**

<details><summary>参考答案</summary>

**错，而且反了。** Builder 持有正在构造中的可变状态，如果是单例：

- 两个线程同时构造会互相覆盖字段；
- 连续两次构造会继承上一次的残留值。

正确结论：**Builder 绝不能是单例，也绝不能跨线程共享**。可以安全共享的是**产品**——因为它不可变。
</details>

**10. 一个类有 20 个字段、全部必填、无默认值，该用 Builder 吗？**

<details><summary>参考答案</summary>

**该用，但理由和"可选参数"无关。** 20 个必填字段意味着 20 个参数的构造器——顺序错误无法被编译器发现。Builder 在这里的价值是**每个值前面有方法名**，把"位置错误"变成"编译错误"。

但更该先问一句：**20 个必填字段的类，是不是职责太多了？** 正确的下一步可能是拆分（比如把"硬件配置"抽成独立的 `HardwareSpec`），而不是给它套一个 20 个 setter 的 Builder。**模式解决不了设计问题。**
</details>

### 9.5 面试官想听什么

**想听**：

- "Builder 里存的是值，不是产品；`build()` 要 `new` 一个新对象"——说明你知道真假 Builder 的分界。
- "产品必须不可变，否则模式价值被抵消"——说明你抓住了目的而不是形式。
- "Director 是可选的，只有多套流程时才值得"——说明你不迷信角色清单。
- "Builder 有状态，不能复用、不能单例"——说明你踩过或想过并发/复用问题。
- "跨字段校验只能放在 `build()`"——说明你理解校验时序。

**不想听**：

- 只背"把复杂对象的构建与表示分离"。
- 把 `StringBuilder` 当建造者模式。
- 说"Builder 就是链式调用"。
- 认为必须有 Director。
- 说不出 Builder 的缺点，或者认为它一定比构造器好。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 行数 | 角色 | 职责 |
|---|---|---|---|
| `Computer.java` | 98 | Product + 简化 Builder | 不可变产品；内含 `static final class Builder` |
| `ComputerBuilder.java` | 38 | Builder（抽象） | 声明 6 个 `buildXxx()` + `getResult()` |
| `GamingComputerBuilder.java` | 48 | ConcreteBuilder | 游戏机选型 |
| `OfficeComputerBuilder.java` | 49 | ConcreteBuilder | 办公机选型 |
| `Director.java` | 52 | Director | `construct()` 6 步 / `constructMinimal()` 3 步 |
| `Client.java` | 66 | 装配层 | 三种用法演示（传统 / 简化 / 校验） |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.creational.builder.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/builder/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.builder.Client
```

### 10.3 本次修订记录

| 改动 | 原因 |
|---|---|
| `Client` 的 `constructMinimal()` 改用全新 `Director` / Builder | 原写法复用了已跑完 6 步的 Builder，"最小配置"实际输出全套配置，标签与内容不符（§4.6 有实测） |
| `Client` 增加三行注释说明"Builder 有状态，不可复用" | 这是本项目最容易踩的坑，必须在代码里就提示，不能只写在文档 |

> 原项目的 6 个缺陷（两个同名 `Computer`、`build()` 零校验、`build()` 返回内部实例、`build()` 后可改、产品 setter 公开、Director 单流程）均已在重构阶段处置，不是本轮改动，特此说明。本轮只修了 `Client` 的复用问题。

---

相关文档：[模式对比 §创建型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
