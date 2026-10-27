# 抽象工厂模式（Abstract Factory Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：提供一个创建**一族相关对象**的接口，让使用方在不知道具体类的情况下，一次拿到一整套配套的产品。
>
> 本目录实测：**14 个源文件 / 297 行**，`javac -Xlint:all` 零告警；运行输出 **16 行 / 520 字节**，
> SHA-256 `5356F49B361AEBF2…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 一致）。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 它解决什么 | **一族**对象必须配套创建，不能混搭（Android 按钮 + Web 文本框 = 界面错乱） |
| 它和工厂方法的区别 | 工厂方法产出**一个**产品；抽象工厂产出**一族**产品（工厂接口有 N 个 `createXxx`） |
| 它的 OCP 是单向的 | **新增产品族容易**（加一个工厂），**新增产品种类很难**（要改接口和所有工厂） |
| 什么时候别用 | 只有一种组件；产品族不会增加；业务上需要混搭不同族 |
| 最容易犯的错 | 把它当成"更大的工厂方法"背下来，却说不清"族"到底约束了什么 |

一句话判断：**"这些对象是不是必须来自同一个品牌 / 同一套实现？"** 是，才需要它。

## 1. 现实案例：说人话版

### 1.1 三个生活类比

**家具套装**。你买"北欧风"客厅套装：沙发、茶几、电视柜。它们风格必须一致——北欧沙发配工业风茶几，摆在一起就是灾难。抽象工厂就是"北欧家具厂"，它保证出厂的每件家具都属于同一套风格。

**汽车配件**。同一车型的零件要配套：发动机、变速箱、电控系统来自同一平台。你不能把 A 平台的变速箱装到 B 平台的发动机上。

**UI 主题**。App 有浅色/深色两套主题，每套包含按钮、输入框、图标。切主题时是**整套切换**，不是逐个换。

### 1.2 业务场景：跨平台 UI 渲染

同一段客户端代码，要在 Android、Web、iOS 上渲染出各自风格的界面：

- 组件（产品等级）：按钮、文本框、图片。
- 平台（产品族）：Android（Material Design）、Web（HTML/CSS）、iOS（UIKit）。

**核心约束**：三个组件必须来自同一个平台。Android 的按钮 + Web 的图片渲染在同一页上，视觉和交互都是错的。

抽象工厂把"平台"做成工厂：

```java
Button button   = factory.createButton();
TextBox textBox = factory.createTextBox();
Image image     = factory.createImage();
```

只要 `factory` 是同一个，三个组件必然同族。**这个"必然"不是靠约定，是靠结构保证的**——使用方拿不到第二个工厂，就没有混搭的机会。

### 1.3 专业版：GoF 的定义与角色

> Provide an interface for creating families of related or dependent objects without specifying their concrete classes.
> —— *Design Patterns*, GoF, 1994

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **AbstractFactory** | 声明创建一族产品的方法 | `UIFactory`（3 个 `createXxx`） |
| **ConcreteFactory** | 实现某一族产品的创建 | `AndroidUIFactory` / `WebUIFactory` / `IOSUIFactory` |
| **AbstractProduct** | 每个产品角色的接口 | `Button` / `TextBox` / `Image` |
| **ConcreteProduct** | 某个族里的具体产品 | `AndroidButton` / `WebButton` / `IOSButton` … |
| **Client** | 只依赖抽象工厂和抽象产品 | `Client.renderUI(UIFactory)` |

### 1.4 两个必须分清的概念：产品族 vs 产品等级

这是抽象工厂最核心的概念，也是面试最容易露馅的地方。

```text
                  Button        TextBox       Image        ← 产品等级结构（3 个）
                    │              │            │
Android 族  ──  AndroidButton  AndroidTextBox  AndroidImage
Web 族      ──  WebButton      WebTextBox      WebImage
iOS 族      ──  IOSButton      IOSTextBox      IOSImage
```

| 概念 | 含义 | 变化方向 |
|---|---|---|
| **产品等级结构** | 同一角色的不同实现（所有按钮） | 纵向：加一种**组件**（如"下拉框"） |
| **产品族** | 同一个品牌/平台的一整套（Android 全系） | 横向：加一个**平台**（如"鸿蒙"） |

**抽象工厂约束的是"横向"**：同一个工厂产出的产品必须属于同一族。

这也解释了它的 OCP 为什么是单向的：

| 变化 | 影响 | 评价 |
|---|---|---|
| 新增产品族（鸿蒙） | 新增 1 个工厂 + N 个产品，**已有代码 0 修改** | ✔ 符合 OCP |
| 新增产品种类（下拉框） | 改 `UIFactory` 接口 + **所有**具体工厂 + 所有族 | ✘ 违反 OCP |

**没有哪种设计能在两个方向上都符合 OCP。** 选抽象工厂，就是选择了"横向易扩展、纵向难扩展"。

### 1.5 JDK 里可以看作抽象工厂的类

| 类 | 它产出的"一族" | 说明 |
|---|---|---|
| `javax.xml.parsers.DocumentBuilderFactory` | `newDocumentBuilder()` + `newSchema()` | 解析器生态必须配套（同一实现的 builder 与 schema 才能协作） |
| `javax.xml.stream.XMLInputFactory` | `createXMLStreamReader()` + `createXMLStreamWriter()` | StAX 的读与写来自同一实现 |
| `java.nio.channels.spi.SelectorProvider` | `openSelector()` + `openSocketChannel()` + `openServerSocketChannel()` | NIO 的选择器与通道必须同实现才兼容 |

**判别要点**：工厂接口里是否有**多个** `createXxx`，且这些产品有配套关系。只有一个 `createXxx` 的，是工厂方法。

> 注意：这些 JDK 类通常还用 `newInstance()` 让调用方按系统属性选实现，那是"抽象工厂 + 静态工厂"的组合，不要混为一谈。

## 2. 原始实现：三版代码

### 2.1 v0：每个平台写一份 UI 代码

```java
public void renderAndroid() {
    new AndroidButton().render();
    new AndroidTextBox().render();
    new AndroidImage().render();
}

public void renderWeb() {
    new WebButton().render();
    new WebTextBox().render();
    new WebImage().render();
}
```

痛点：**渲染流程的逻辑重复了 N 份**（顺序、布局、错误处理都一样），而且新增平台要复制一整段。

### 2.2 v1：工厂方法（每个组件一个工厂）

```java
Button button = new AndroidButtonFactory().createButton();
TextBox textBox = new AndroidTextBoxFactory().createTextBox();
```

问题来了：**三个组件来自三个不同的工厂对象**，没有任何结构能保证它们属于同一族。写代码的人手一抖，就混搭了：

```java
Button button = new AndroidButtonFactory().createButton();
TextBox textBox = new WebTextBoxFactory().createTextBox();   // 编译通过，界面错乱
```

工厂方法解决了"创建谁"，**没解决"必须配套"**。

### 2.3 v2：抽象工厂（原项目的结构）

```java
public interface UIFactory {
    Button createButton();
    TextBox createTextBox();
    Image createImage();
}

public class AndroidUIFactory implements UIFactory {
    @Override public Button createButton()   { return new AndroidButton(); }
    @Override public TextBox createTextBox() { return new AndroidTextBox(); }
    @Override public Image createImage()     { return new AndroidImage(); }
}
```

使用方：

```java
UIFactory factory = new AndroidUIFactory();
Button button = factory.createButton();
TextBox textBox = factory.createTextBox();
Image image = factory.createImage();
```

现在三件套来自**同一个** `factory` 变量，混搭在结构上被挡住了（除非有人故意创建第二个工厂）。

### 2.4 原项目的四个问题

| # | 问题 | 性质 |
|---|---|---|
| A1 | `Client` 里的注释写"假设当前运行环境为 Web"，代码却是 `new AndroidUIFactory()` | **注释与代码矛盾**，容易误导读者 |
| A2 | 切换平台靠注释掉一行 | 演示方式，但让人误以为"改代码换平台"是正常做法 |
| A3 | Web 产品的包叫 `ui`，Android 的叫 `android` | **命名不对称**：`ui` 是角色名，`android` 是平台名，分类维度混了 |
| A4 | 只有 2 个产品族，没有演示"新增一族" | 教学缺口，读者看不出横向扩展的价值 |

A1 是最典型的"文档债"：注释停留在上一版，代码已经改了。**注释不会编译，所以它永远是对的——直到有人照着它改代码。**

### 2.5 重构版的改进

| 项 | 原项目 | 重构版 |
|---|---|---|
| 注释与代码 | 注释说 Web，代码是 Android | 三个场景分开打印，无歧义 |
| 布局 | 全部写在 `main` 里 | 抽成 `renderUI(UIFactory)` 方法，**渲染流程只写一次** |
| 产品族 | 2 个（Android / Web） | 3 个（+ iOS，演示横向扩展） |
| 包命名 | `ui`（角色名） | `web`（平台名，与 `android` 对称） |
| iOS 族的位置 | —— | 4 个类放在 `IOSUIFactory.java` 一个文件里（见 §5.6 的取舍说明） |

## 3. 设计动机

### 3.1 四个动机

| 动机 | 说明 |
|---|---|
| **保证配套** | 一族产品必须一起用，抽象工厂让使用方**拿不到**第二个族的工厂 |
| **隔离平台差异** | 换平台 = 换一个工厂实例，渲染代码不动 |
| **集中创建规则** | 哪个产品属于哪个族，只在具体工厂里定义一次 |
| **可测试** | 注入一个"测试工厂"（全部返回假组件），UI 逻辑可脱离平台测试 |

第一条是**抽象工厂独有的**，也是它和工厂方法最根本的差别。其余三条，工厂方法也有。

### 3.2 什么时候**不该**用抽象工厂

**① 只有一种组件。** 那只是工厂方法。

**② 产品族只有 1~2 个，且明确不会增加。** 直接 `new` 或用简单工厂，别上三层结构。

**③ 业务上需要混搭。** 比如"Android 原生按钮 + 自定义 H5 文本框"是常见需求。抽象工厂的"同族约束"在这里变成**障碍**，应该改用工厂方法或策略。

**④ 产品种类会频繁增加。** 每加一种组件都要改 `UIFactory` 接口和所有具体工厂，痛感远大于收益。这时应该考虑"注册表 + 依赖注入"或"组合优于继承"的方案。

**⑤ 你只是想少写 `new`。** 抽象工厂不解决"少写"的问题，它解决"配套"的问题。

### 3.3 工厂方法 vs 抽象工厂：一张表说清

| 维度 | 工厂方法 | 抽象工厂 |
|---|---|---|
| 工厂接口方法数 | 1 个 `createXxx` | N 个 `createXxx` |
| 产出 | 一个产品 | 一族产品 |
| 新增产品 | 加一个产品 + 一个工厂 | 加一族产品 + 一个工厂 |
| 新增产品**种类** | 不影响已有工厂 | **要改工厂接口和所有实现** |
| 核心价值 | 创建与使用解耦 | 保证同族配套 |
| 典型场景 | 消息渠道、日志实现 | 跨平台 UI、数据库驱动套件、主题 |

**关系**：抽象工厂的每个 `createXxx` 方法，本身就是一个工厂方法。所以可以说：**抽象工厂 = 一组有配套约束的工厂方法**。

## 4. 设计推导

### 4.1 第 1 步：先画出产品矩阵

任何抽象工厂的设计都从一张表开始：

|  | 按钮 | 文本框 | 图片 |
|---|---|---|---|
| **Android** | AndroidButton | AndroidTextBox | AndroidImage |
| **Web** | WebButton | WebTextBox | WebImage |
| **iOS** | IOSButton | IOSTextBox | IOSImage |

**行 = 产品族，列 = 产品等级**。抽象工厂的接口方法数是**列数**，具体工厂数是**行数**。

如果这张表里某一行只有一格有值，说明"族"的概念不成立——直接 `new` 就好。

### 4.2 第 2 步：抽象产品接口

```java
public interface Button {
    void render();
}

public interface TextBox {
    void render();
}

public interface Image {
    void render();
}
```

三个接口都是 `void render()`，看起来重复。**为什么不为它们抽一个公共父接口 `UIComponent`？**

| 方案 | 好处 | 坏处 |
|---|---|---|
| 三个独立接口（当前） | 每个角色可以自由演化（按钮将来加 `onClick`，图片不需要） | 无法把"所有组件"放进一个集合统一处理 |
| 抽 `UIComponent` | 可以 `List<UIComponent>` 统一渲染 | 一旦某个组件需要专属方法，父接口就会被污染 |

当前 Demo 里三者行为相同，抽公共接口也说得过去。**但判断标准不是"现在像不像"，而是"将来会不会分叉"**：按钮和图片的演化方向几乎肯定不同，所以保持独立更稳。

### 4.3 第 3 步：抽象工厂接口

```java
public interface UIFactory {
    Button createButton();
    TextBox createTextBox();
    Image createImage();
}
```

三个方法**没有任何参数**。原因和工厂方法一样：**由"哪个实现类"决定族，而不是由参数决定**。

如果写成 `createButton(String style)`，就退化成了"一个工厂 + switch"，族的一致性又要靠调用方自觉。

### 4.4 第 4 步：具体工厂 = 一行的 `new`

```java
public class AndroidUIFactory implements UIFactory {
    @Override public Button createButton()   { return new AndroidButton(); }
    @Override public TextBox createTextBox() { return new AndroidTextBox(); }
    @Override public Image createImage()     { return new AndroidImage(); }
}
```

三个方法都是"一行 `new`"，看起来毫无技术含量。**价值不在方法体，而在"这三个方法在同一个类里"**——它把"这三个产品属于同一族"这条业务规则，固化成了代码结构。

### 4.5 第 5 步：使用方只接收工厂

```java
private static void renderUI(UIFactory factory) {
    Button button = factory.createButton();
    TextBox textBox = factory.createTextBox();
    Image image = factory.createImage();

    button.render();
    textBox.render();
    image.render();
}
```

这个方法是**整个模式的价值所在**：

- 它只有一份，三个平台共用；
- 它没有任何 `if (platform == ...)`；
- 它拿到什么工厂，就渲染什么风格，**不可能混搭**。

对比原项目把渲染写在 `main` 里：一旦新增场景就要复制一遍，且复制时容易漏改一个 `new`。

### 4.6 第 6 步：谁来选工厂

和工厂方法一样，"选工厂"的责任没有消失：

| 方式 | 代码 | 适用 |
|---|---|---|
| 使用方 `new` | `renderUI(new WebUIFactory())` | Demo、启动装配 |
| 按运行环境判断 | `renderUI(isAndroid() ? new AndroidUIFactory() : new WebUIFactory())` | 客户端 App（真实项目常见） |
| 配置 + 反射 | `Class.forName(cfg).newInstance()` | 插件化、多品牌定制 |
| DI 容器 | `@Bean UIFactory uiFactory()` | 服务端 |

**唯一不能接受的是**：在 `renderUI` 内部判断平台。那等于把族的选择逻辑塞回业务代码，抽象工厂就白用了。

### 4.7 最终结构

```text
                        UIFactory（AbstractFactory）
                        ├─ createButton()
                        ├─ createTextBox()
                        └─ createImage()
              ┌──────────────┼──────────────┐
     AndroidUIFactory   WebUIFactory   IOSUIFactory
              │              │              │
              ▼              ▼              ▼
        Android 三件套   Web 三件套     iOS 三件套
              │              │              │
              └──────────────┴──────────────┘
                             ▼
                  Button / TextBox / Image（AbstractProduct）
```

读图要点：**纵向是族，横向是产品等级；工厂是"一行的代表"**。

## 5. 代码解析

### 5.1 `UIFactory`——抽象工厂

```java
public interface UIFactory {
    Button createButton();
    TextBox createTextBox();
    Image createImage();
}
```

三个方法就是"这一族必须有哪些产品"的清单。**接口一旦发布，新增产品种类就是破坏性变更**——所有实现类都要补方法，所有测试都要改。

> 这是抽象工厂最需要提前想清楚的地方：**列的维度要稳定，行的维度才适合扩展**。

### 5.2 `Button` / `TextBox` / `Image`——抽象产品

```java
public interface Button {
    void render();
}
```

它们只声明"能渲染"。真实项目里还会有 `setText()`、`onClick()`、`setEnabled()` 等，而且每个角色的方法集不同——这正是"不要抽公共父接口"的理由（§4.2）。

### 5.3 `AndroidUIFactory`——具体工厂

```java
public class AndroidUIFactory implements UIFactory {
    @Override public Button createButton() {
        return new AndroidButton();
    }
    @Override public TextBox createTextBox() {
        return new AndroidTextBox();
    }
    @Override public Image createImage() {
        return new AndroidImage();
    }
}
```

注意它 `import` 的是 `android` 子包里的类。**族的边界在 import 里就能看出来**：一个工厂只该 import 一个族的实现。

如果某天有人在 `AndroidUIFactory` 里 `import web.WebButton`，代码立刻能闻到味道——这是"结构即约束"的好处。

### 5.4 `WebUIFactory`——另一个族

结构和 `AndroidUIFactory` 完全同构，只是产品换成了 Web 系列。

**这看起来是重复代码**。能不能用一个 `switch` 工厂代替？

```java
public class SwitchUIFactory implements UIFactory {
    private final String platform;
    public Button createButton() {
        return switch (platform) {
            case "android" -> new AndroidButton();
            case "web" -> new WebButton();
            default -> throw new IllegalStateException();
        };
    }
    // createTextBox / createImage 同样 switch
}
```

可以，但这样：
1. 三个方法各一个 `switch`，**同一套判断写三遍**；
2. 新增平台要改三处，漏一处就出现"部分组件是旧平台"的诡异 bug；
3. 单测要覆盖 3×3 = 9 条路径。

具体工厂的"重复"恰恰是**把 3 个 `switch` 压成了 3 行 `new`**。这是用类数量换清晰度。

### 5.5 `Client.renderUI`——使用方的正确姿态

```java
private static void renderUI(UIFactory factory) {
    Button button = factory.createButton();
    TextBox textBox = factory.createTextBox();
    Image image = factory.createImage();

    button.render();
    textBox.render();
    image.render();
}
```

三点：

1. **参数是抽象工厂**，不是 `String platform`——用类型表达"我要一族产品"。
2. **三次创建都来自同一个 `factory`**，这是同族保证的来源。
3. **没有 `instanceof` / `switch`**，使用方完全不知道渲染的是什么平台。

### 5.6 `IOSUIFactory`——新增一族的最小代价

```java
class IOSUIFactory implements UIFactory {
    @Override public Button createButton()   { return new IOSButton(); }
    @Override public TextBox createTextBox() { return new IOSTextBox(); }
    @Override public Image createImage()     { return new IOSImage(); }
}

class IOSButton implements Button { ... }
class IOSTextBox implements TextBox { ... }
class IOSImage implements Image { ... }
```

**4 个类放在同一个文件里**，且都是包私有。这是一个有意的取舍：

| 取舍 | 好处 | 代价 |
|---|---|---|
| 同文件 4 个类 | 演示"新增一族"时一眼看全，文件数不爆炸 | 与 `android` / `web` 的"一类一文件一包"风格不一致 |

严格说，生产代码应该建 `ios/` 包，三个产品各一个文件，与 `android` / `web` 对称。**本项目为了压缩演示噪音没这么做**，这个不一致如实记录在这里。

### 5.7 包结构为什么是 `android` / `web` / `factory`

- `android` / `web`：**按平台分**，一个包 = 一个族的实现。
- `factory`：**按角色分**，放所有具体工厂。

严格说，`factory` 包里的 `AndroidUIFactory` 和 `android` 包是强相关的，放在一起也合理。当前的划分让"族的实现"和"族的创建"分开了，好处是 `factory` 包能一眼看全所有平台。

**包结构的本质是"变化边界"**：按平台分包，新增平台就是新增一个包，不会碰别人的文件。

## 6. 执行流程

```text
Client.main
  └─ renderUI(new WebUIFactory())
       ├─ factory.createButton()    → new WebButton()
       ├─ factory.createTextBox()   → new WebTextBox()
       ├─ factory.createImage()     → new WebImage()
       ├─ button.render()           → [Web] 渲染 HTML/CSS 风格按钮
       ├─ textBox.render()          → [Web] 渲染 HTML input 文本框
       └─ image.render()            → [Web] 渲染 <img> 标签图片
```

**四次动态分派**：三次 `createXxx`（选族） + 三次 `render`（选具体实现），同一段 `renderUI` 代码因此能渲染出三种风格。

### 6.1 完整输出（16 行，逐字）

```text
========== 抽象工厂模式演示 ==========

--- 场景 1：Android 应用 ---
[Android] 渲染 Material Design 风格按钮
[Android] 渲染 Material Design 风格文本框
[Android] 渲染 Material Design 风格图片

--- 场景 2：Web 应用 ---
[Web] 渲染 HTML/CSS 风格按钮
[Web] 渲染 HTML input 文本框
[Web] 渲染 <img> 标签图片

========== 扩展：新增 iOS 产品族 ==========
[iOS] 渲染 UIKit 风格按钮
[iOS] 渲染 UITextField 文本框
[iOS] 渲染 UIImageView 图片
```

### 6.2 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out $(find src/com/patterns/creational/abstractfactory -name "*.java")
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.abstractfactory.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 16 |
| 字节数 | 520（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `5356F49B361AEBF2` |
| 确定性 | 连跑两次逐行 diff 一致 |

## 7. 扩展对比

### 7.1 新增"鸿蒙"产品族：加 1+N 个类，改 0 处

```java
class HarmonyUIFactory implements UIFactory {
    @Override public Button createButton()   { return new HarmonyButton(); }
    @Override public TextBox createTextBox() { return new HarmonyTextBox(); }
    @Override public Image createImage()     { return new HarmonyImage(); }
}
```

`Client.renderUI` **一行都不用改**，调用处加一句 `renderUI(new HarmonyUIFactory())` 即可。

**这就是抽象工厂的 OCP 方向**：横向（族）扩展几乎零成本。

### 7.2 新增"下拉框"组件：改 1 + N 处

```java
// 1. 新增产品接口
public interface Dropdown {
    void render();
}

// 2. 改抽象工厂（破坏性变更）
public interface UIFactory {
    Button createButton();
    TextBox createTextBox();
    Image createImage();
    Dropdown createDropdown();     // ← 所有实现类都会编译失败
}
```

**每个具体工厂都必须补方法**，否则编译不过。这正是"纵向扩展难"的体现。

> 好处是：**编译器会强制你补全**，不会出现"某个平台忘了实现"的静默缺失。这是"难但安全"。

**缓解手段**（当产品种类确实会不断增加时）：

```java
// 只加一个"泛型创建"方法，避免接口膨胀
public interface UIFactory {
    <T> T create(Class<T> type);
}
```

代价：失去编译期类型约束，`create(Dropdown.class)` 可能抛运行期异常。**这是用类型安全换扩展性**，要慎用。

### 7.3 参数化工厂：一个类搞定所有族

```java
public class ParamUIFactory implements UIFactory {
    private final Platform platform;

    public ParamUIFactory(Platform platform) { this.platform = platform; }

    @Override
    public Button createButton() {
        return switch (platform) {
            case ANDROID -> new AndroidButton();
            case WEB -> new WebButton();
            case IOS -> new IOSButton();
        };
    }
    // 其余方法同理
}
```

**优点**：类数量从 3 个工厂降到 1 个。
**缺点**：三个方法各有一个 `switch`，新增平台要改三处（漏一处就混搭），单测路径 3×3。

**适用**：族很多（10+）但产品种类极少（2~3），且团队小、改动可控。**不适用于本项目**。

### 7.4 注册表 + Supplier：现代写法

```java
public final class UIFactories {
    private static final Map<String, UIFactory> FACTORIES = Map.of(
            "android", new AndroidUIFactory(),
            "web", new WebUIFactory(),
            "ios", new IOSUIFactory()
    );

    public static UIFactory of(String platform) {
        UIFactory f = FACTORIES.get(platform);
        if (f == null) throw new IllegalArgumentException("未知平台：" + platform);
        return f;
    }
}
```

本质是**简单工厂**：新增平台要改这个 `Map`。但它把"选工厂"从业务代码里彻底拿走了，`Client` 只写 `renderUI(UIFactories.of(cfg.platform()))`。

**工厂方法 / 抽象工厂解决"怎么创建"，注册表解决"怎么选"。** 两者经常一起用。

### 7.5 与工厂方法、建造者、原型的边界

| 模式 | 产出 | 关键区别 |
|---|---|---|
| **工厂方法** | 一个产品 | 工厂接口只有 1 个创建方法 |
| **抽象工厂** | 一族产品 | 工厂接口有 N 个创建方法，且产品有配套约束 |
| **建造者** | 一个复杂对象 | 分步组装，最后 `build()`；抽象工厂一步到位 |
| **原型** | 已有对象的副本 | 靠克隆，适合创建成本高的对象 |
| **外观** | 无（不创建对象） | 封装子系统调用，常与抽象工厂配合 |

**最容易被问的组合**：抽象工厂 + 外观。真实框架里常见"外观类内部持有抽象工厂，对外只暴露一个入口"。

### 7.6 一个反例：什么时候"族"的约束反而碍事

假设产品需求是"Android 原生按钮 + 自研 H5 文本框"（混合渲染在跨端项目里很常见）。

抽象工厂在这里会挡住你：

```java
UIFactory factory = new AndroidUIFactory();
Button b = factory.createButton();          // 想要的
TextBox t = factory.createTextBox();        // 不想要，但拿不到 H5 的
```

要么破坏结构（在 `renderUI` 里额外 `new` 一个 H5 文本框），要么就得为"混合族"再写一个工厂——而混合组合是笛卡尔积，爆炸得比继承还快。

**结论**：抽象工厂的前提是"产品必须成套使用"。**业务允许混搭时，不要用它**。

## 8. 优缺点与误区

### 8.1 优点

1. **保证同族配套**：使用方拿不到第二个工厂，混搭在结构上被阻断。
2. **横向扩展便宜**：新增一族 = 新增工厂 + 产品，已有代码零修改。
3. **渲染/装配逻辑只写一次**：`renderUI` 一份代码服务所有平台。
4. **平台细节被隔离**：`Client` 完全不知道 Material Design 和 UIKit 的区别。
5. **便于整体替换**：换 UI 框架 = 换一个工厂实例，甚至只改配置。

### 8.2 缺点

1. **纵向扩展困难**：新增产品种类要改接口和所有工厂（编译器会强制，但改动面大）。
2. **类数量多**：族数 × 产品数 + 族数。本项目 3×3 + 3 = 12 个实现类。
3. **接口一旦发布就难改**：`UIFactory` 是对外契约，加方法等于破坏性升级。
4. **禁止混搭**：业务需要混搭时，它从优点变成障碍。
5. **学习成本**：产品族 / 产品等级两个概念不讲清楚，读者会觉得"只是工厂变大了"。

### 8.3 八个误区

| 误区 | 真相 |
|---|---|
| "抽象工厂就是工厂方法的升级版" | 不是升级，是**不同的取舍**：横向易扩展、纵向难扩展 |
| "抽象工厂能解决所有创建问题" | 只能解决"一族必须配套"的问题；产品种类频繁变化时它很痛 |
| "产品族 = 产品等级" | 族是横向（Android 全系），等级是纵向（所有按钮） |
| "工厂接口方法越多越灵活" | 方法越多，纵向扩展越痛；接口应尽量稳定 |
| "抽象工厂一定要 3 个以上产品" | 2 个产品也算（`DocumentBuilderFactory` 就是 2 个），关键是"配套" |
| "用了抽象工厂就能保证不混搭" | 结构上挡住了大部分，但有人显式 `new` 两个工厂仍能混搭；要配合代码评审/架构测试 |
| "抽象工厂适合所有跨平台场景" | 只适合"整套切换"。允许混搭的跨端项目应该用组件级工厂或依赖注入 |
| "抽象工厂和简单工厂是竞争关系" | 它们解决不同问题：一个管"配套创建"，一个管"按名字选一个" |

### 8.4 出现这些信号，说明你在滥用抽象工厂

- 工厂接口只有 1 个 `createXxx` → 你其实只需要工厂方法。
- 只有一个具体工厂 → 没有"族"的概念，直接 `new`。
- 工厂接口有 10 个 `createXxx` 且还在增加 → 纵向扩展已经失控，考虑按角色拆分工厂。
- `renderUI` 里出现 `if (factory instanceof ...)` → 使用方开始感知具体族，抽象漏了。
- 有人为了"混搭"在业务代码里 `new` 具体产品 → 抽象工厂与需求不匹配，该换方案。

## 9. 面试与自测

### 9.1 30 秒版

> 抽象工厂提供一个创建一族相关对象的接口，让使用方在不指定具体类的情况下拿到一整套配套产品。典型场景是跨平台 UI：Android、Web、iOS 三套组件，用一个工厂保证按钮、文本框、图片都来自同一平台。它和工厂方法的区别是工厂方法产出一个产品，抽象工厂产出一族。它的开闭原则是单向的：新增产品族容易，新增产品种类要改接口和所有工厂。

### 9.2 2 分钟版

> 我拿本项目举例。`UIFactory` 声明了 `createButton`、`createTextBox`、`createImage` 三个方法，`AndroidUIFactory`、`WebUIFactory`、`IOSUIFactory` 各自实现一族。
>
> 使用方的核心代码是 `renderUI(UIFactory factory)`：三次创建都用同一个 `factory`，所以三个组件必然同族。这一点比"少写 new"重要得多——它把"必须配套"这条业务规则固化成了结构，而不是靠注释提醒。
>
> 概念的根是"产品族 vs 产品等级"：族是横向（Android 全系），等级是纵向（所有按钮）。抽象工厂约束横向。所以它新增一族很便宜——加 iOS 只加一个工厂和三个产品，`Client` 一行不改；但新增一种组件很痛——要改 `UIFactory` 接口，所有具体工厂编译失败。
>
> 这个"痛"其实是保护：编译器强制你补全，不会出现某个平台漏实现。
>
> 原项目里有个小问题很典型：注释写着"假设当前运行环境为 Web"，代码却是 `new AndroidUIFactory()`。注释不参与编译，所以它能一直错下去。另外 Web 产品的包叫 `ui`、Android 的包叫 `android`，命名维度不统一，重构时统一成了 `web`。
>
> 最后是适用边界：抽象工厂的前提是产品必须成套使用。如果业务要"Android 按钮 + H5 文本框"这种混搭，抽象工厂反而是障碍。

### 9.3 追问链

| 追问 | 要点 |
|---|---|
| 抽象工厂的 OCP 为什么是单向的 | 族（行）可扩展，产品等级（列）扩展要改接口；这是"产品矩阵"的固有限制 |
| 和工厂方法怎么选 | 产品有配套约束 → 抽象工厂；只是创建单个产品 → 工厂方法 |
| 怎么新增一种产品又不改接口 | 泛型 `create(Class<T>)` 或注册表；代价是失去编译期检查 |
| 怎么保证不同族不混搭 | 使用方只接收一个工厂参数，且工厂不使用可变字段（或每次 `new`） |
| 工厂要不要做成单例 | 无状态工厂可以（省对象），但要注意测试隔离；有状态工厂绝对不行 |
| Spring 里对应什么 | `FactoryBean`、`@Bean` 方法返回一族对象；`AbstractFactoryBean` 是抽象工厂的骨架实现 |
| 抽象工厂和依赖注入的关系 | 抽象工厂是"手工选择实现"，DI 是"容器注入实现"。DI 容器可以替代工厂的"选择"职责，但替代不了"配套创建设计" |

### 9.4 自测题

**1. 产品族和产品等级的区别？**

<details><summary>参考答案</summary>

产品等级是**同一角色的不同实现**（所有按钮：Android 按钮、Web 按钮、iOS 按钮），是纵向的；产品族是**同一平台/品牌的一整套**（Android 的按钮+文本框+图片），是横向的。抽象工厂的接口方法数=产品等级数，具体工厂数=产品族数。
</details>

**2. 抽象工厂和工厂方法最本质的区别？**

<details><summary>参考答案</summary>

工厂接口的方法数量与产出物：工厂方法只有 1 个 `createXxx`，产出一个产品，关注"创建与使用解耦"；抽象工厂有 N 个 `createXxx`，产出一族产品，关注"产品必须配套"。抽象工厂的每个方法本身都是工厂方法。
</details>

**3. 为什么说抽象工厂的 OCP 是单向的？**

<details><summary>参考答案</summary>

因为产品矩阵有两个维度。新增产品族（行）= 新增工厂和产品，已有代码不动，符合 OCP；新增产品等级（列）= 要给 `AbstractFactory` 接口加方法，所有具体工厂编译失败，违反 OCP。任何设计都无法在两个方向同时满足 OCP。
</details>

**4. 本项目怎么保证"不混搭"？**

<details><summary>参考答案</summary>

`renderUI(UIFactory factory)` 只接收一个工厂参数，三次 `createXxx` 都调用同一个 `factory`，因此三个组件必然同族。结构上使用方拿不到第二个工厂，也就没有混搭的机会。前提是工厂本身无状态——如果工厂有 `setPlatform()` 之类的可变状态，同族保证就破了。
</details>

**5. 如果产品种类会不断增加，该怎么设计？**

<details><summary>参考答案</summary>

三条路：① 泛型创建方法 `<T> T create(Class<T>)`，避免接口膨胀，但失去编译期检查；② 按角色拆成多个工厂（`ButtonFactory`、`TextBoxFactory`），代价是失去"同族"保证；③ 用依赖注入容器按类型注入，把"配套"交给配置。核心权衡是：**类型安全 vs 扩展成本**。
</details>

**6. 为什么 `Button` / `TextBox` / `Image` 不抽一个公共父接口？**

<details><summary>参考答案</summary>

因为它们的演化方向不同：按钮要 `onClick`、输入框要 `setText`、图片要 `setSource`，公共父接口很快会被专属方法污染。抽取公共接口的唯一好处是"能放进同一个集合统一处理"，本项目不需要。**接口应该按"是否会被统一使用"来划分，不是按"现在长得像不像"。**
</details>

**7. 手写题：用抽象工厂实现"数据库访问套件"（Connection + Command）。**

<details><summary>参考答案</summary>

```java
public interface Connection { void open(); }
public interface Command { void execute(); }

public interface DbFactory {
    Connection createConnection();
    Command createCommand();
}

public class MySqlFactory implements DbFactory {
    @Override public Connection createConnection() { return new MySqlConnection(); }
    @Override public Command createCommand()       { return new MySqlCommand(); }
}

public class PostgresFactory implements DbFactory {
    @Override public Connection createConnection() { return new PostgresConnection(); }
    @Override public Command createCommand()       { return new PostgresCommand(); }
}
```
要点：`Connection` 和 `Command` 必须来自同一驱动（不同驱动的协议、占位符语法、事务语义都不同），这就是"族"的配套约束。使用方只持有 `DbFactory`，切换数据库 = 换工厂。
</details>

**8. 判断对错：抽象工厂可以替代工厂方法。**

<details><summary>参考答案</summary>

错。两者解决不同问题：工厂方法解决"创建哪个产品"，抽象工厂解决"一族产品必须配套"。只有一个产品时用抽象工厂是过度设计；需要在运行时按类型选择单个产品时，工厂方法或简单工厂更直接。
</details>

**9. 抽象工厂 + 单例/缓存，会有什么坑？**

<details><summary>参考答案</summary>

如果具体工厂是有状态的（比如持有"当前主题"字段），把它做成单例会让多个使用方互相污染，破坏同族保证。**无状态工厂**可以共享；有状态工厂必须每个使用方一个实例（或每次 `new`）。同理，工厂返回的产品如果被缓存复用，要注意产品本身是否可变。
</details>

**10. 设计一个"新增组件不改接口"的方案，并说明代价。**

<details><summary>参考答案</summary>

```java
public interface UIFactory {
    <T> T create(ComponentType<T> type);
}

public final class ComponentType<T> {
    private final String key;
    private final Class<T> type;
    // 注册表：key → (factory, supplier)
}
```
使用方 `factory.create(BUTTON)`，新增组件只注册新 key。代价：① 失去编译期"所有族都必须实现"的强制；② 可能运行期才报"未注册"；③ 泛型擦除下 `ComponentType` 的设计要做足功课。**这是"扩展性换类型安全"的典型交易。**
</details>

### 9.5 面试官想听什么

**想听**：

- "产品族 vs 产品等级"——能说清这两个词，说明真懂。
- "OCP 是单向的"——说明你不是背"符合开闭原则"。
- "保证配套，而不是少写 new"——说明你抓住了核心价值。
- "新增组件时编译器会强制所有工厂补方法，这是保护"——说明你能辩证看代价。
- "业务允许混搭时不该用它"——说明你知道边界。

**不想听**：

- 把抽象工厂说成"工厂方法的升级版"。
- 说不出"族"的具体含义，只会说"创建一系列对象"。
- 认为它"完全符合开闭原则"。
- 举不出 JDK 里的例子，或把 `Calendar.getInstance()` 当抽象工厂。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 角色 |
|---|---|
| `UIFactory.java` | AbstractFactory，3 个 `createXxx` |
| `Button.java` / `TextBox.java` / `Image.java` | AbstractProduct × 3 |
| `factory/AndroidUIFactory.java` | ConcreteFactory（Android 族） |
| `factory/WebUIFactory.java` | ConcreteFactory（Web 族） |
| `android/AndroidButton.java` 等 3 个 | ConcreteProduct（Android 族） |
| `web/WebButton.java` 等 3 个 | ConcreteProduct（Web 族） |
| `IOSUIFactory.java` | ConcreteFactory + 3 个 iOS 产品（同文件，扩展演示） |
| `Client.java` | 使用方，`renderUI(UIFactory)` |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.creational.abstractfactory.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out $(find src/com/patterns/creational/abstractfactory -name "*.java")
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.abstractfactory.Client
```

### 10.3 本次修订记录

本轮**未改动本目录的 Java 代码**，只重写了本文档。文档中记录的代码行为均来自实际运行：

- 输出 16 行 / 520 字节 / `5356F49B361AEBF2…`；
- 连跑两次逐行 diff 一致；
- `Client.renderUI(UIFactory)` 的确只接收一个工厂参数（同族保证的来源）。

---

相关文档：[模式对比 §创建型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
