# 装饰器模式（Decorator Pattern）

> **一句话**：把要加的功能放进一个个"包装纸"里，包装纸本身和里面的东西**类型相同**，所以可以一层层套下去，而客户端拿到的仍然是同一个类型。

| 项目 | 内容 |
|---|---|
| 分类 | 结构型模式 |
| 包路径 | `com.patterns.structural.decorator` |
| 运行入口 | `Client.main()` |
| 核心角色 | `TextComponent`（抽象组件）、`PlainText`（具体组件）、`TextDecorator`（装饰器基类）、四个具体装饰器 |
| 一句话记忆 | **"我穿了一件外套，但别人看我仍然是'人'"** |

---

## 0. 先看结论

| 问题 | 结论 |
|---|---|
| 装饰器到底解决什么 | 在不改原类、不改接口的前提下，**运行期**给对象叠加功能 |
| 为什么不用继承 | 4 种样式做全组合要 2⁴−1 = **15 个类**；装饰器只要 4 个类 + 自由组合 |
| 成立的技术前提 | 装饰器**和被装饰者实现同一个接口**。这一条不满足，就不是装饰器模式 |
| 关键的一行 | `return "<b>" + super.render() + "</b>";` —— 先拿到内层结果，再套自己的壳 |
| 最隐蔽的坑 | 子类里写 `"<b>" + component + "</b>"`（漏了 `.render()`），会把 `toString()` 拼进去 |
| 第二个坑 | 装饰后对象的 `equals` / `hashCode` / 类型判断全部"变形"，`getClass()` 不再是 `PlainText` |
| 顺序要紧吗 | 可交换的装饰（如加粗 + 变色）无所谓；不可交换的（如截断 + 反转）顺序就是语义 |
| 什么时候不该用 | 只需要一层增强、或者用静态代理/继承一次就够、或者接口方法太多（Java IO 的教训） |
| 和代理的分界 | 装饰器由**调用方**决定套几层（客户端组装）；代理对调用方**透明**（客户端不知道有代理） |
| 本项目做了什么 | 补默认转发、`final` 字段、构造期校验、颜色白名单；原实现 6 个缺陷全部实测取证 |

### 诚实前置说明

原项目的装饰器 demo **能跑通**，输出 `<span style='color:red'><b>Hello, Decorator!</b></span>` 是正确的（实测 2 行 / 80 字节）。
所以本模块不是"修了一个跑不起来的 bug"，而是补上了**六个"能跑但会在生产里咬人"的洞**：

| 洞 | 后果 |
|---|---|
| 构造器不校验 `null` | `new BoldDecorator(null)` 构造成功，`render()` 时才 NPE —— 错误离现场很远 |
| `PlainText` 不校验 `null` | `new PlainText(null)` 合法，最终渲染出字符串 `"null"` 而不是报错 |
| `component` 非 `final` | 装饰关系能在运行期被替换，`protected` 让同包/子类随便改 |
| 基类没有默认 `render()` | 每个子类必须手写 `component.render()`，漏写就断链 —— 而且**不报错** |
| `ColorDecorator` 不校验颜色 | `"彩虹色"` 会原样拼进 HTML，生成非法 CSS |
| `style` 用单引号 | 与 HTML 常规写法不一致，虽然浏览器能容错 |

六条都有探针实测，见 §2.3。

---

## 1. 现实案例

### 说人话版

去奶茶店点一杯奶茶。你可以加珍珠、加椰果、加布丁、少冰、半糖。
**你点的永远是"奶茶"这个品类**，但每一份配料都在它上面叠了一层，最后结账时按层收费。

关键在于：店员问你"要什么"时，你的回答始终是"奶茶"，而不是"珍珠椰果布丁奶茶"——
后者听起来像是一个全新的品类。如果每种组合都是一个新品类，菜单会爆炸：
珍珠、椰果、布丁两两组合就是 7 种，三三组合就是 15 种。这就是 **v0 反例**（继承爆炸）。

同样的结构在业务里到处都是：

| 场景 | 被装饰的东西 | 装饰层 | 统一操作 |
|---|---|---|---|
| 奶茶店 | 奶茶 | 珍珠 / 椰果 / 布丁 / 糖度 | 计价、做出来 |
| 电商订单 | 订单 | 优惠券 / 满减 / 积分抵扣 / 运费 | 算最终价 |
| Web 请求处理 | 请求处理器 | 鉴权 / 限流 / 日志 / 缓存 / 事务 | 处理请求 |
| Java IO | 字节流 | 缓冲 / 加密 / 压缩 / 字符编码 | `read` / `write` |
| 前端样式 | 纯文本 | 加粗 / 斜体 / 颜色 / 下划线 | 渲染成 HTML |
| 数据源 | 数据查询 | 缓存层 / 重试 / 熔断 / 监控 | `query` |

### 一个你每天都在用的例子：`BufferedReader`

```java
BufferedReader reader = new BufferedReader(
        new InputStreamReader(
                new FileInputStream("data.txt"), StandardCharsets.UTF_8));
```

三层嵌套，从里到外：

1. `FileInputStream` —— 真正读字节（具体组件）
2. `InputStreamReader` —— 把字节按 UTF-8 解码成字符（装饰器，把 `InputStream` 适配成 `Reader`）
3. `BufferedReader` —— 加缓冲、提供 `readLine()`（装饰器）

注意最外层的变量类型是 `BufferedReader`，但你调用的 `read()` 最终会一路委托到 `FileInputStream`。
每一层都只做自己那一件事，谁也不认识谁。

> **严格说**，`InputStreamReader` 是**适配器**不是装饰器（它把 `InputStream` 转成 `Reader`，换了接口）。
> 但 `BufferedReader`、`BufferedInputStream`、`DataInputStream`、`GZIPInputStream` 都是标准装饰器。
> 这个"混搭"恰恰说明：真实代码里模式往往不是教科书原样出现的。

### 专业版

装饰器模式（Decorator Pattern）动态地给一个对象**添加一些额外的职责**。
就增加功能而言，装饰器比生成子类更灵活：它把功能放进独立的装饰器类里，
让这些类**实现与被装饰者相同的接口**，并在内部持有被装饰者的引用，从而可以任意层叠。

GoF 原文（《设计模式：可复用面向对象软件的基础》，结构型第 4 章）对这个模式的意图描述是：
"Attach additional responsibilities to an object dynamically. Decorators provide a flexible
alternative to subclassing for extending functionality."

两个关键词：

- **dynamically**：运行期决定，不是编译期定死
- **an object**：装饰的是**对象**，不是类。同一个类的两个实例可以装饰得完全不同

---

## 2. 原始实现：不用装饰器会怎样，以及原项目写成了什么样

### 2.1 v0：用继承做全组合 —— 类爆炸

假设我们要给文本加四种样式：加粗、斜体、颜色、下划线。

**第一反应：为每种组合写一个类。**

```java
// ❌ 反例：继承做全组合
class BoldText extends Text { }
class ItalicText extends Text { }
class ColorText extends Text { }
class BoldItalicText extends Text { }
class BoldColorText extends Text { }
class ItalicColorText extends Text { }
class BoldItalicColorText extends Text { }
// ……还没算下划线
```

4 种样式，两两组合 C(4,2)=6，三三组合 C(4,3)=4，四四组合 C(4,4)=1，
加上单个的 C(4,1)=4 —— **一共 15 个类**。这就是 2⁴−1。

再加第五种样式（比如"高亮背景"），立刻变成 2⁵−1 = **31 个类**。
加第六种 → 63 个。

| 样式数 | 子类数 |
|---|---|
| 3 | 7 |
| 4 | 15 |
| 5 | 31 |
| 6 | 63 |
| 10 | 1023 |

而且这个爆炸还有第二个问题：**组合在编译期就固定了**。
用户今天想要"加粗 + 红色"，明天想要"加粗 + 红色 + 下划线"，你得**再写一个类**。
更要命的是，如果样式是用户从界面上勾选的，那**永远无法穷举**。

**第三,** 每个子类都会复制粘贴同一段渲染逻辑。`BoldItalicText` 和 `BoldItalicColorText`
里都有 `<b><i>` 那段前缀，改一处要改一片。

### 2.2 v1：原项目的实现 —— 结构对了，细节松了

原项目 `structuraltype.DecoratorPattern` 的六个文件：

```java
public interface TextComponent {
    String render();
}

public record PlainText(String content) implements TextComponent {
    @Override
    public String render() {
        return content;
    }
}

public abstract class TextDecorator implements TextComponent {
    protected TextComponent component;

    public TextDecorator(TextComponent component) {
        this.component = component;
    }
    // ← 注意：这里没有 render() 的实现
}

public class BoldDecorator extends TextDecorator {
    public BoldDecorator(TextComponent component) {
        super(component);
    }

    @Override
    public String render() {
        return "<b>" + component.render() + "</b>";
    }
}

public class ColorDecorator extends TextDecorator {
    private final String color;

    public ColorDecorator(TextComponent component, String color) {
        super(component);
        this.color = color;
    }

    @Override
    public String render() {
        return "<span style='color:" + color + "'>" + component.render() + "</span>";
    }
}
```

客户端：

```java
TextComponent text = new PlainText("Hello, Decorator!");
text = new BoldDecorator(text);
text = new ColorDecorator(text, "red");
System.out.println(text.render());
```

实测输出（2 行 / 80 字节）：

```text
最终渲染结果：
<span style='color:red'><b>Hello, Decorator!</b></span>
```

**结构是对的**：接口统一、装饰器持有同类型引用、层层委托。
问题在细节 —— 下面用探针逐条钉死。

### 2.3 用探针钉死六个缺陷

探针 `DecoratorProbe` 放在原项目的包里（因为 `component` 是 `protected`，同包才能访问）。
完整输出 20 行：

```text
[事实 1] 构造时不校验 null，错误延迟到 render 才爆
  new BoldDecorator(null) 构造成功，类型 = BoldDecorator
  render() 时抛出了：java.lang.NullPointerException

[事实 2] PlainText 也不校验，null 会被一路包装成字符串 "null"
  render() = <span style='color:red'><b>null</b></span>

[事实 3] component 是 protected 非 final，装饰关系能被中途替换
  替换前：<b>原始内容</b>
  替换后：<b>偷偷换掉的内容</b>

[事实 4] 子类忘记委托 component，装饰链在这里断掉
  render() = <b><div>固定内容</div></b>

[事实 5] 颜色无校验，非法值会原样拼进 HTML
  render() = <span style='color:彩虹色'>x</span>

[事实 6] style 属性用单引号，与重构版的双引号写法不一致
  原项目：<span style='color:red'>q</span>
  重构版：<span style="color:red">q</span>
```

逐条分析：

**事实 1：错误延迟。** `new BoldDecorator(null)` 完全没有报错，构造出了一个"看起来正常"的对象。
直到某处调用 `render()` 才抛 NPE。这时候调用栈已经很深，你看到的是"渲染时 NPE"，
而实际问题在**几小时前、另一个模块、构造那一行**。这是典型的"错误离现场太远"。

**事实 2 更阴险。** `new PlainText(null)` 合法，`render()` 返回 `null`，然后字符串拼接
`"<b>" + null + "</b>"` 会得到 `"<b>null</b>"` —— **不报错，输出了字面量 "null"**。
如果这是给用户看的页面，用户会看到页面上写着 "null"；如果这是写进数据库的，脏数据就进去了。

> Java 里 `String + null` 的规则是转换成字符串 `"null"`，不是抛 NPE。这个规则在很多地方救过人，
> 也在这里坑过人。

**事实 3：装饰关系可被替换。** `component` 是 `protected` 且非 `final`，同包或子类可以直接赋值。
一旦被替换，之前构建的"装饰链"就名存实亡 —— 你以为在装饰 A，实际装饰的是 B。

**事实 4：断链不报错。** 探针写了一个 `ForgotDelegate`，它继承 `TextDecorator` 但
`render()` 直接返回固定字符串，**没有调用 `component.render()`**。结果：

```text
render() = <b><div>固定内容</div></b>
```

最内层的 `PlainText("本该出现的文本")` 彻底消失了。**编译通过，运行不报错，只是内容没了。**

这是装饰器模式最危险的一类错误：它不是崩溃，而是**静默丢失**。
如果有 100 个装饰器里混进了 1 个忘记委托的，你可能几个月都发现不了。

**事实 5：参数不校验。** `"彩虹色"` 被原样拼进 `style='color:彩虹色'`，生成非法 CSS。
浏览器通常会忽略这条规则（文字不变色），用户看到的是"颜色没生效"，而不是"程序出错"。

**事实 6** 是风格问题，不是 bug。单引号在 HTML 里合法，只是与项目其他地方的写法不一致。

整理成表：

| 编号 | 缺陷 | 性质 | 后果 |
|---|---|---|---|
| P1 | 构造器不校验 `null` | 错误延迟 | NPE 抛在 `render()` 而不是构造处，排查距离远 |
| P2 | `PlainText` 不校验 `null` | 静默错误 | 渲染出字符串 `"null"`，不报错 |
| P3 | `component` 非 `final` | 封装漏洞 | 装饰关系可被中途替换 |
| P4 | 基类无默认 `render()` | 静默错误 | 子类漏写委托 → 内层内容丢失，且编译/运行都不报错 |
| P5 | 颜色参数不校验 | 错误延迟 | 非法 CSS 被生成，浏览器静默忽略 |
| P6 | `style` 用单引号 | 风格不一致 | 无功能影响 |

### 2.4 v2：重构版改了什么

| 缺陷 | 重构版的处理 | 代码位置 |
|---|---|---|
| P1 | `TextDecorator` 构造器判空，抛 `IllegalArgumentException` | `TextDecorator` |
| P2 | `PlainText` 紧凑构造器判空 | `PlainText` |
| P3 | `component` 改为 `protected final` | `TextDecorator` |
| P4 | 基类提供**默认转发实现** `return component.render();` | `TextDecorator` |
| P5 | 颜色白名单 + `#RRGGBB` 正则校验 | `ColorDecorator` |
| P6 | 统一用双引号 | `ColorDecorator` |
| （新增） | 增加 `ItalicDecorator` / `UnderlineDecorator` | 新文件 |
| （新增） | 客户端从 1 个场景扩到 5 个场景 | `Client` |

P3 和 P4 是两个最值得展开的设计决策，§4.4 和 §4.5 专门讲。

---

## 3. 设计动机

### 为什么需要装饰器模式？

| 动机 | 说明 |
|---|---|
| **避免子类爆炸** | n 种功能的全组合是 2ⁿ−1 个类；装饰器只需 n 个类，且能任意层叠 |
| **运行期可配置** | 要不要装饰、装饰几层、什么顺序，都由运行期代码决定，不需要重新编译 |
| **单一职责** | 每个装饰器只管一种功能，`BoldDecorator` 只懂加粗，不懂颜色 |
| **符合开闭原则** | 新增一种样式 = 新增一个类，原有类一行不改 |
| **可任意叠加** | 同一个装饰器能套多次（双层斜体 = `<i><i>x</i></i>`），继承做不到 |
| **不改变接口** | 装饰后仍是 `TextComponent`，可以传给任何接收它的方法 |

### 最后一条的实际价值

看 `Client.demo5UniformType()`：

```java
TextComponent plain = new PlainText("纯文本");
TextComponent fancy = new UnderlineDecorator(
        new ItalicDecorator(
                new BoldDecorator(
                        new ColorDecorator(new PlainText("花哨文本"), "purple"))));

publish(plain);
publish(fancy);
```

`publish` 只接收 `TextComponent`，它**完全不知道**两个对象的差异：

```text
  [发布] 纯文本
  [发布] <u><i><b><span style="color:purple">花哨文本</span></b></i></u>
plain 实际类型 : PlainText
fancy 实际类型 : UnderlineDecorator
但静态类型都是 TextComponent，所以 publish() 不需要重载
```

如果用继承，`publish` 就得写 15 个重载（对应 15 个子类）。
这就是"统一接口"的威力 —— **装饰器的价值不在于"加功能"，而在于"加了功能还能当原类型用"**。

### 什么情况下**不需要**装饰器？

**① 只需要一层增强，而且永远不会变**

如果需求是"给所有日志加时间戳"，那就写一个 `logWithTimestamp()` 方法，或者干脆在
日志框架的配置里加 pattern。套一个 `TimestampDecorator` 属于纯过度设计。

判断标准：**如果装饰器永远只套一层、且只有一种实现，那它就是一个普通的方法调用。**

**② 增强逻辑和原对象强耦合**

装饰器只能"在调用前后加料"，它**看不到原对象的内部状态**（只有一个 `component` 引用）。
如果需求是"根据 `PlainText` 的 `content` 长度决定要不要加粗"，装饰器要么再解析一遍渲染结果，
要么就该换个设计（比如把策略放进 `PlainText` 内部）。

**③ 接口方法太多**

这是 Java IO 给全世界上的一课。`InputStream` 有 3 个 `read` 重载 + `skip` + `available` +
`close` + `mark` + `reset` + `markSupported`，一共 9 个方法。
`FilterInputStream` 为了自动转发，把 9 个方法全部重写了一遍；`FilterOutputStream` 同样。

这导致想写一个新的装饰器，你必须**全部转发**（或者继承 `FilterInputStream`）。
更糟的是，`BufferedInputStream` 内部的 `buf` 需要和 `fill()` 配合，
而 `mark`/`reset` 的语义还要跨层协调 —— 所以 `BufferedReader` 的 `mark` 行为特别容易出错。

**教训**：接口越大，装饰器的维护成本越高。设计接口时要问一句：
"将来会有人装饰它吗？如果会，这么多方法能转发得过来吗？"

**④ 只需要在固定位置加固定功能**

比如"每个 DAO 方法都要打印 SQL 日志"。这种横切关注点用 **AOP**（Spring AOP / AspectJ）
或动态代理更合适 —— 你不用为每个接口写一个装饰器类。

**⑤ 性能极度敏感**

每层装饰 = 一次额外的方法调用。对绝大多数业务代码无所谓，
但在高频路径（比如序列化框架的流处理）上，层层嵌套的虚方法调用会明显影响性能。
JDK 自己的 `StringBuilder`（非同步）和 `StringBuffer`（同步）就是权衡的例子。

---

## 4. 设计推导：为什么是这四个角色？

### 4.1 从需求倒推结构

需求：给文本加样式，样式可任意组合、任意顺序、任意次数，且**客户端不需要知道套了几层**。

```text
需求：客户端拿到一个对象，调 render() 就得到最终结果
  ↓
必须有一个统一类型，让"裸文本"和"套了 4 层的文本"能放进同一个变量
  ↓
【角色 1】抽象组件 TextComponent：声明 render()
  ↓
需要有一个"真正的内容"，它是所有装饰的最内层
  ↓
【角色 2】具体组件 PlainText：render() 返回原始内容（递归终点）
  ↓
装饰器要能持有"另一个 TextComponent"，并在它的结果外面加料
  ↓
【角色 3】装饰器基类 TextDecorator：持有 TextComponent + 默认转发
  ↓
每种样式一个类，各自覆盖 render()
  ↓
【角色 4】具体装饰器 Bold / Italic / Underline / Color
```

关键在倒数第二步：**为什么要有一个"装饰器基类"，而不是让每个装饰器直接实现接口？**

### 4.2 类结构图

```text
                    ┌─────────────────────────┐
                    │  «interface»            │
                    │  TextComponent          │
                    │─────────────────────────│
                    │ + render() : String     │
                    └─────────────────────────┘
                          △                △
              implements  │                │  implements
              ┌───────────┘                └────────────┐
              │                                         │
   ┌────────────────────────┐          ┌────────────────────────────────┐
   │ «record» PlainText     │          │ «abstract» TextDecorator       │
   │────────────────────────│          │────────────────────────────────│
   │ - content : String     │          │ # component : TextComponent    │
   │────────────────────────│          │   （protected final）           │
   │ + render() : String    │          │────────────────────────────────│
   └────────────────────────┘          │ + render() : String  ← 默认转发 │
              │                        └────────────────────────────────┘
        递归终点                                      △
      返回 content                       ┌───────────┼───────────┬──────────┐
                                         │           │           │          │
                                  ┌──────────┐ ┌──────────┐ ┌─────────┐ ┌─────────┐
                                  │  Bold    │ │ Italic   │ │Underline│ │ Color   │
                                  │──────────│ │──────────│ │─────────│ │─────────│
                                  │ render() │ │ render() │ │ render()│ │-color   │
                                  └──────────┘ └──────────┘ └─────────┘ │ render()│
                                                                        └─────────┘

                     TextDecorator 内部持有 TextComponent
                     ↓
        这个 component 可以是 PlainText，也可以是另一个 TextDecorator
        ↓
              于是形成任意深度的装饰链
```

### 4.3 关键设计决策一：为什么要 `TextDecorator` 这层抽象

三个原因，逐个说：

**① 消除重复**

每个装饰器都要"持有一个 `TextComponent` + 构造器赋值"。抽到父类只写一次。
4 个装饰器就省了 4 份字段声明和 4 份构造器赋值。

看起来省得不多，但如果你有 20 个装饰器（Spring 里很常见），这个重复量是灾难性的。

**② 提供默认转发 —— 这是最重要的**

原项目的 `TextDecorator` **只有字段和构造器，没有任何方法**。
这意味着每个子类必须自己写 `component.render()`：

```java
// 原项目：子类必须自己访问字段
@Override
public String render() {
    return "<b>" + component.render() + "</b>";
}
```

重构版加了默认实现，子类改成调 `super.render()`：

```java
// 重构版：子类通过 super 调用
@Override
public String render() {
    return "<b>" + super.render() + "</b>";
}
```

这两者今天的**运行结果完全一样**，但将来不一样。假设某天你需要统计"每个装饰层耗时"：

```java
// 在基类里加一行，所有子类自动获得
@Override
public String render() {
    long start = System.nanoTime();
    String result = component.render();
    System.out.println("耗时 " + (System.nanoTime() - start) + " ns");
    return result;
}
```

如果子类写的是 `component.render()`，这行统计代码**被绕过**；
如果子类写的是 `super.render()`，**自动生效**。

> **这就是"模板方法"的雏形**：父类定义骨架（`super.render()` 里可以插任意逻辑），
> 子类只负责"加什么料"。装饰器 + 模板方法是天生一对。

**③ 类型约束**

有了 `TextDecorator`，"装饰器"成为一个明确的概念。
如果只是"实现了 `TextComponent` 的类"，那 `PlainText` 也算 —— 而它不是装饰器。

这个区分在**单元测试**里很有用：`assertTrue(obj instanceof TextDecorator)` 能直接判断
"这个对象是不是装饰链里的一环"。

### 4.4 关键设计决策二：`component` 为什么必须是 `final`

原项目是 `protected TextComponent component`，重构版是 `protected final TextComponent component`。

`final` 拦住的是这件事（探针 `DecoratorFinalProbe` 实测）：

```java
TextComponent text = new BoldDecorator(new PlainText("原始内容"));
((TextDecorator) text).component = new PlainText("偷偷换掉的内容");
```

编译结果（`JAVAC_EXIT=1`）：

```text
DecoratorFinalProbe.java:11: 错误: 无法为 final 变量 component 分配值
        ((TextDecorator) text).component = new PlainText("偷偷换掉的内容");
                              ^
1 个错误
```

对比原项目里同样的代码 —— **编译通过，运行也通过**，只是输出变了：

```text
[事实 3] component 是 protected 非 final，装饰关系能被中途替换
  替换前：<b>原始内容</b>
  替换后：<b>偷偷换掉的内容</b>
```

**为什么这不只是"风格问题"？**

装饰器的心智模型是"**这一层负责包装那一个对象**"。这个关系一旦建立，就是不可变的。
如果允许中途替换，会出现非常难查的 bug：你手上有一个 `TextComponent` 引用，
传给三个模块用，第二个模块偷偷换掉了内层 —— 第三个模块拿到的结果就变了，
而它根本不知道发生了什么。

`final` 把"装饰关系不可变"这个设计意图**写进了类型系统**。这比写 10 行注释管用。

> **注意 `protected final` 而不是 `private`**：子类需要通过 `super.render()` 访问它，
> 而 `super.render()` 在基类内部，其实 `private` 也能访问。
> 那为什么不是 `private`？
>
> 因为**子类有时确实需要直接读 `component`**（比如 `component.getClass()` 做类型判断）。
> `protected final` 给了一个"只读但可看"的边界：能看到，不能换。

### 4.5 关键设计决策三：白名单 + 正则的校验

```java
private static final Set<String> NAMED_COLORS =
        Set.of("red", "blue", "green", "black", "gray", "orange", "purple");

public ColorDecorator(TextComponent component, String color) {
    super(component);
    if (color == null || color.isBlank()) {
        throw new IllegalArgumentException("颜色不能为空");
    }
    boolean isNamed = NAMED_COLORS.contains(color.toLowerCase());
    boolean isHex = color.matches("#[0-9a-fA-F]{6}");
    if (!isNamed && !isHex) {
        throw new IllegalArgumentException("不支持的颜色值：" + color
                + "，请使用常用颜色名或 #RRGGBB 格式");
    }
    this.color = color;
}
```

三层校验，逐层收紧：

| 检查 | 拦住什么 |
|---|---|
| `color == null \|\| color.isBlank()` | 空值、全是空格 |
| `NAMED_COLORS.contains(...)` | 常见颜色名 |
| `color.matches("#[0-9a-fA-F]{6}")` | `#RRGGBB` 十六进制 |

探针实测（`DecoratorProProbe`，完整 12 行输出见 §6.3）：

```text
[事实 3] 白名单与十六进制都能通过，非法值被拒
  red     -> <span style="color:red">a</span>
  #336699 -> <span style="color:#336699">b</span>
  REDD    -> 不支持的颜色值：REDD，请使用常用颜色名或 #RRGGBB 格式
```

注意 `REDD` 这个例子：它是 `RED` 多打了一个字母，长度 4，既不在白名单也不是合法 hex，
所以被拒。而 `RED`（大写）会被 `toLowerCase()` 转成 `red` 通过 —— **大小写不敏感**是刻意的。

**为什么要做这个校验？** 因为颜色值最终会被拼进 HTML：

```java
return "<span style=\"color:" + color + "\">" + super.render() + "</span>";
```

不校验的话，攻击者传入 `"><script>alert(1)</script>` 就能**注入脚本**：

```text
<span style="color:"><script>alert(1)</script>">...</span>
```

这就是 **XSS（跨站脚本攻击）**。装饰器模式本身不制造漏洞，但它把"参数拼进字符串"
这件事藏在了看似无害的 `render()` 里 —— **越是看起来人畜无害的地方，越容易忘记校验**。

> 真实项目里，正确的做法是**输出时编码**（如 Spring 的 `HtmlUtils.htmlEscape`），
> 而不是只靠白名单。白名单是"第一道闸门"，编码是"最后一道闸门"，两者都要有。

### 4.6 关键设计决策四：`PlainText` 为什么用 record

| | `PlainText` | `TextDecorator` |
|---|---|---|
| 状态 | 不可变（就一个字符串） | 持有可变对象引用（但引用本身 final） |
| 需要 `equals` / `hashCode` | 是（便于测试断言） | 否 |
| 需要被继承 | 否 | 是（4 个装饰器继承它） |
| 适合 record | ✅ | ❌（record 不能被子类继承） |

**record 的一个硬限制正好被这里用上了**：record 不能 `extends` 别的类，也不能被别的类 `extends`
（record 隐式 final）。所以 `PlainText` 用 record、`TextDecorator` 用 abstract class，
两者的边界非常清晰。

`PlainText` 的紧凑构造器也顺便做了判空：

```java
public PlainText {
    if (content == null) {
        throw new IllegalArgumentException("文本内容不能为 null");
    }
}
```

探针实测：

```text
  new PlainText(null)    -> 文本内容不能为 null
```

这一行避免了 §2.3 事实 2 里"渲染出字符串 null"的尴尬。

### 4.7 关键设计决策五：为什么 `render()` 返回 `String` 而不是直接打印

```java
String render();     // ✅ 返回值
// void print();     // ❌ 直接输出
```

三个好处：

**① 可测试**。`assertEquals("<b>x</b>", new BoldDecorator(new PlainText("x")).render())`
一行就能验证，不需要搭 `System.out` 重定向。

**② 可组合**。返回值能继续参与运算（拼接、再装饰、写入文件、返回给 HTTP 响应）。

**③ 与 IO 无关**。这个类不关心你是要打印到控制台、写进日志、还是塞进 JSON 响应。
**职责单一**。

> 组合模式里的 `print(String indent)` 是直接打印的，因为它要演示"树形缩进"这件事本身就依赖输出。
> 但即使那样，更好的设计也是返回 `List<String>` 让调用方决定怎么输出。
> 这是本项目 `Directory.print` 的一个已知瑕疵（见那边 README §8.3）。

---

## 5. 代码解析：每个类为什么必须存在？

| 类 | 角色 | 职责 | 删掉会怎样 |
|---|---|---|---|
| `TextComponent` | 抽象组件 | 声明 `render()` | **装饰链断裂**。装饰器无法持有"同类型"的引用，退化成一条不能嵌套的死链 |
| `PlainText` | 具体组件 | 返回原始内容 | **没有递归终点**。所有装饰器都无处可委托，`component` 只能是 null |
| `TextDecorator` | 装饰器基类 | 持有 component + 默认转发 | 4 个子类各自重复写字段和构造器；漏写委托时无兜底 |
| `BoldDecorator` | 具体装饰器 | 包 `<b>` | 少一种样式（不影响其他） |
| `ItalicDecorator` | 具体装饰器 | 包 `<i>` | 同上 |
| `UnderlineDecorator` | 具体装饰器 | 包 `<u>` | 同上 |
| `ColorDecorator` | 具体装饰器（带参数） | 包 `<span style="color:...">` | 少一种"可参数化"的样式 |
| `Client` | 客户端 | 五个场景演示 | 无法运行验证 |

### 5.1 最关键的一行

```java
// BoldDecorator.java
@Override
public String render() {
    return "<b>" + super.render() + "</b>";
}
```

这一行同时体现了三件事：

1. **`super.render()` 是"先算内层"** —— 它最终会走到 `component.render()`，一路递归到 `PlainText`
2. **`"<b>" + ... + "</b>"` 是"后加外壳"** —— 内层结果被包在标签里
3. **返回 `String` 而不是打印** —— 结果可以继续被外层装饰

**执行方向**：调用从外向内（`BoldDecorator.render` → `ColorDecorator.render` → `PlainText.render`），
**返回值从内向外**（`"Hello"` → `"<span>Hello</span>"` → `"<b><span>Hello</span></b>"`）。

记忆口诀：**"请求往里钻，结果往外裹。"**

### 5.2 三种"忘记委托"的写法，效果天差地别

```java
// ✅ 正确：先拿内层结果，再加料
return "<b>" + super.render() + "</b>";
// 输出：<b>Hello</b>

// ❌ 错法 1：漏了 .render()，调用 toString()
return "<b>" + component + "</b>";
// 输出：<b>com.patterns.structural.decorator.PlainText[content=Hello]</b>

// ❌ 错法 2：完全没用 component，内层内容丢失
return "<b>Hello</b>";
// 输出：<b>Hello</b>（看起来对，但换了任何内容都不变）
```

错法 1 的症状很明显（输出里有 `PlainText[content=` 字样），容易发现。
**错法 2 才是真正危险的** —— 它的输出"看起来是对的"，只是永远不反映内层。

探针 `DecoratorProbe` 的事实 4 就是在演示错法 2 的后果：

```text
[事实 4] 子类忘记委托 component，装饰链在这里断掉
  render() = <b><div>固定内容</div></b>
```

最内层的 `PlainText("本该出现的文本")` 完全消失了，而**没有任何异常**。

> 这就是为什么重构版要在基类里提供默认 `render()`：如果你忘了覆盖，至少还能原样转发；
> 如果你覆盖了但写错，那 `super.render()` 的存在也在提醒你"应该调它"。

### 5.3 `ColorDecorator` 为什么比 `BoldDecorator` 复杂

对比这两个类：

```java
public class BoldDecorator extends TextDecorator {
    public BoldDecorator(TextComponent component) { super(component); }

    @Override
    public String render() {
        return "<b>" + super.render() + "</b>";
    }
}
```

```java
public class ColorDecorator extends TextDecorator {
    private final String color;

    public ColorDecorator(TextComponent component, String color) {
        super(component);
        // ……三层校验
        this.color = color;
    }

    @Override
    public String render() {
        return "<span style=\"color:" + color + "\">" + super.render() + "</span>";
    }
}
```

多了一个字段 `color`、一个参数、一段校验。

**这个差异揭示了装饰器的一个关键扩展点**：装饰器可以带自己的状态。

| 类型 | 例子 | 状态 | 能产生多少种效果 |
|---|---|---|---|
| 无参装饰器 | `BoldDecorator` | 无 | 1 种（加粗） |
| 带参装饰器 | `ColorDecorator` | `color` | 无限种（任意合法颜色） |

如果用继承实现"带参数"，你需要为每个参数值写一个类：`RedText`、`BlueText`、`GreenText`……
颜色有无限种，类就有无限个。**这是继承绝对做不到的事情**。

`ColorDecorator` 的类注释里写得很直白：

> 同一份代码，运行期产生无数种装饰效果。

### 5.4 `Client` 的五个场景分别在证明什么

| 场景 | 证明的事 | 关键输出 |
|---|---|---|
| `demo1BasicDecoration` | 基础叠加 | `<i><span style="color:red"><b>Hello, Decorator!</b></span></i>` |
| `demo2OrderMatters` | 顺序影响标签嵌套 | `<b><span ...>顺序A</span></b>` vs `<span ...><b>顺序B</b></span>` |
| `demo3RuntimeComposition` | 运行期按条件组装 | 张三被装饰，李四没有 |
| `demo4RepeatAndParameterize` | 同一装饰器可叠多次 / 带参展开 | `<i><i>双层斜体</i></i>`、三种颜色 |
| `demo5UniformType` | 类型不变，可无缝传递 | `fancy 实际类型 : UnderlineDecorator`，但 `publish()` 不需要重载 |

**场景 3 最能说明"继承做不到"**：

```java
boolean isVip = true;
boolean needHighlight = false;

for (String userName : new String[]{"张三", "李四"}) {
    TextComponent text = new PlainText("欢迎，" + userName);

    if (isVip) {
        text = new BoldDecorator(text);
        text = new ColorDecorator(text, "orange");
    }
    if (needHighlight) {
        text = new UnderlineDecorator(text);
    }

    System.out.println("  " + text.render());
    isVip = false;   // 第二个用户不是 VIP，走不同的装饰路径
}
```

如果用继承，`isVip` 这个条件决定的是"用 `VipWelcomeText` 还是 `NormalWelcomeText`"，
而 `needHighlight` 又会让这个选择翻倍 —— **4 个条件 = 2⁴ 个类**。
用装饰器，条件就是 `if` 语句，一共 4 行。

**场景 5 揭示了一个容易被忽略的事实**：

```text
plain 实际类型 : PlainText
fancy 实际类型 : UnderlineDecorator
```

`fancy.getClass()` **不等于** `PlainText.class`。如果代码里有 `if (text instanceof PlainText)`，
装饰之后**判断就失败了**。这是装饰器的固有限制，§8.3 会专门讲。

### 5.5 为什么没有 `HighlightDecorator`

需求里其实提到了"高亮背景"，但代码里没有。理由：它和 `ColorDecorator` 的结构完全一样。

```java
public class HighlightDecorator extends TextDecorator {
    private final String bg;

    public HighlightDecorator(TextComponent component, String bg) {
        super(component);
        this.bg = bg;
    }

    @Override
    public String render() {
        return "<span style=\"background:" + bg + "\">" + super.render() + "</span>";
    }
}
```

加了它只会让 Demo 更长，不会带来新知识。读取者可以自己照着 `ColorDecorator` 写一个 ——
**能照着写出来，才说明真的理解了**。§9.4 的自测题 4 就留了这个练习。

### 5.6 一个容易写错的细节：`super.render()` vs `component.render()`

在 `BoldDecorator` 里，这两种写法今天**结果完全一样**：

```java
return "<b>" + super.render() + "</b>";       // 走父类的 render()
return "<b>" + component.render() + "</b>";   // 直接访问字段
```

因为 `TextDecorator.render()` 的实现就是 `return component.render();`。等价。

但**用 `super.render()` 是更好的选择**，三个理由：

1. **父类将来可能加逻辑**（日志、计时、断言），`super` 自动获得
2. **`component` 下划线 / 可见性将来可能变化**，`super` 不受影响
3. **语义更准确**："我在父类的行为上加料"，而不是"我在操作一个字段"

> 反过来，如果某个装饰器**确实要绕过父类逻辑**（比如"跳过缓存的直接渲染"），
> 那才该用 `component.render()`。但这种情况很少，而且**应该写注释说明为什么绕过**。

---

## 6. 执行流程：从 main 到输出

### 6.1 一次三层装饰的完整调用链

以 `demo1BasicDecoration` 的最后一步为例：

```java
TextComponent text = new PlainText("Hello, Decorator!");
text = new BoldDecorator(text);
text = new ColorDecorator(text, "red");
text = new ItalicDecorator(text);
text.render();
```

对象图：

```text
text ──► ItalicDecorator
           └─ component ──► ColorDecorator
                              └─ component ──► BoldDecorator
                                                 └─ component ──► PlainText("Hello, Decorator!")
```

渲染过程：

```text
ItalicDecorator.render()
│  return "<i>" + super.render() + "</i>"
│      │
│      └─► TextDecorator.render()  （super）
│             return component.render()
│                 │
│                 └─► ColorDecorator.render()
│                        return "<span style=\"color:red\">" + super.render() + "</span>"
│                            │
│                            └─► TextDecorator.render()  （super）
│                                   return component.render()
│                                       │
│                                       └─► BoldDecorator.render()
│                                              return "<b>" + super.render() + "</b>"
│                                                  │
│                                                  └─► TextDecorator.render()  （super）
│                                                         return component.render()
│                                                             │
│                                                             └─► PlainText.render()
│                                                                    return "Hello, Decorator!"   ← 递归终点
│
└─ 返回值逐层向外裹：
   "Hello, Decorator!"
   → "<b>Hello, Decorator!</b>"
   → "<span style=\"color:red\"><b>Hello, Decorator!</b></span>"
   → "<i><span style=\"color:red\"><b>Hello, Decorator!</b></span></i>"
```

**两个方向要分清**：

| 方向 | 发生什么 | 类比 |
|---|---|---|
| 调用向内 | 每层先问"我里面那层是什么，让我调它" | 剥洋葱 |
| 返回向外 | 每层拿到内层结果，把标签裹在外面 | 包洋葱 |

**栈帧深度**：4 层对象 → 8 个 `render()` 栈帧（每层装饰器 + 它的 `super.render()`）。
再套 10 层装饰器就是 20 个栈帧 —— 对 JVM 来说毫无压力（默认栈深度几千到一万），
但**调试时断点会打得你怀疑人生**（§8.3 会讲这个代价）。

### 6.2 实际输出（JDK 25 实测）

```text
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.decorator.Client
```

结果：**29 行 / 1391 字节 / SHA-256 前 16 位 `551D0ADCFF282FC2`**

```text
=== 1. 基础装饰：加粗 + 红色 ===
原始文本       : Hello, Decorator!
加粗后         : <b>Hello, Decorator!</b>
再加红色后     : <span style="color:red"><b>Hello, Decorator!</b></span>
再加斜体后     : <i><span style="color:red"><b>Hello, Decorator!</b></span></i>

=== 2. 装饰顺序会影响最终结果 ===
先变色再加粗 : <b><span style="color:blue">顺序A</span></b>
先加粗再变色 : <span style="color:blue"><b>顺序B</b></span>
说明：外层装饰器包在更外面，标签嵌套层次随构造顺序改变

=== 3. 按运行时条件动态组装样式 ===
  <span style="color:orange"><b>欢迎，张三</b></span>
  欢迎，李四

=== 4. 重复装饰 & 参数化装饰 ===
同一装饰器叠两次 : <i><i>双层斜体</i></i>
红色             : <span style="color:red">红</span>
蓝色             : <span style="color:blue">蓝</span>
十六进制色值     : <span style="color:#336699">自定义</span>
非法颜色已拦截   : 不支持的颜色值：彩虹色，请使用常用颜色名或 #RRGGBB 格式

=== 5. 装饰前后类型不变，可无缝传递 ===
  [发布] 纯文本
  [发布] <u><i><b><span style="color:purple">花哨文本</span></b></i></u>
plain 实际类型 : PlainText
fancy 实际类型 : UnderlineDecorator
但静态类型都是 TextComponent，所以 publish() 不需要重载
```

**四处值得盯住看**：

1. **第 1 段的层层递进**：每加一层装饰，标签就多裹一层，而内层内容始终不变。
2. **第 2 段的顺序对比**：`先变色再加粗` 的输出是 `<b><span>...`，说明 `<b>` 在最外层 ——
   因为 `new BoldDecorator(new ColorDecorator(...))` 里 `BoldDecorator` 是外层。
   **构造顺序 → 标签嵌套顺序，是反的**（外层构造器 → 最外层标签）。
3. **第 3 段的两行差别**：张三被装饰了两层，李四原样输出。
   同一个循环、同一行 `System.out.println`，输出样式却不同 —— 这就是"运行期动态"。
4. **第 4 段的 `<i><i>`**：同一个装饰器套两次是合法的，界面不会报错。
   这在继承里完全做不到（`ItalicText` 不能再 `extends` 自己）。

### 6.3 重构版的行为探针

`DecoratorProProbe` 验证三个设计细节，完整 12 行：

```text
[事实 1] 构造期就拦下 null，不用等到 render
  new BoldDecorator(null) -> 被装饰对象不能为 null
  new PlainText(null)    -> 文本内容不能为 null
  new ColorDecorator(x, "彩虹色") -> 不支持的颜色值：彩虹色，请使用常用颜色名或 #RRGGBB 格式

[事实 2] 子类不覆盖 render() 时，父类默认原样转发，装饰链不会断
  PassThroughDecorator.render() = 内容

[事实 3] 白名单与十六进制都能通过，非法值被拒
  red     -> <span style="color:red">a</span>
  #336699 -> <span style="color:#336699">b</span>
  REDD    -> 不支持的颜色值：REDD，请使用常用颜色名或 #RRGGBB 格式
```

**事实 2 是重构版独有的能力**：探针写了一个 `PassThroughDecorator`，它**完全不覆盖 `render()`**：

```java
static class PassThroughDecorator extends TextDecorator {
    PassThroughDecorator(TextComponent component) {
        super(component);
    }
    // 没有 render()
}
```

`render()` 的结果是 `"内容"` —— 父类的默认实现原样转发了。

这个能力在原项目里不存在（原项目的 `TextDecorator` 没有 `render()`，不覆盖就编译不过）。
它的价值不在于"能写空装饰器"，而在于**给忘记覆盖的人一个安全的默认行为**。

### 6.4 装饰顺序到底重不重要？—— 四组实测

`Client.demo2OrderMatters()` 展示了顺序影响 HTML 标签嵌套。但**标签嵌套和视觉效果无关**
（`<b><span>` 和 `<span><b>` 在浏览器里渲染一模一样）。

那顺序到底什么时候才真正要紧？用一个不涉及 HTML 的探针来测（`DecoratorOrderProbe`，
用 `UnaryOperator` 组合模拟装饰链，逻辑与 `super.render()` 等价），完整 15 行：

```text
[对比 1] trim 与 upper：两种顺序结果相同（可交换）
  upper(trim("  hi  ")) = "HI"
  trim(upper("  hi  ")) = "HI"

[对比 2] upper 与 reverse：两种顺序结果相同（可交换）
  upper(reverse("abc")) = "CBA"
  reverse(upper("abc")) = "CBA"

[对比 3] trim 与 reverse：首尾空格等长时结果恰好相同（碰巧可交换）
  trim(reverse("  ab  ")) = "ba"
  reverse(trim("  ab  ")) = "ba"

[对比 4] head3 与 reverse：两种顺序结果不同（不可交换）
  head3(reverse("abcdef")) = "fed"
  reverse(head3("abcdef")) = "cba"
```

结论分三层：

| 情况 | 例子 | 顺序重要吗 |
|---|---|---|
| 两个装饰互不影响 | `upper` + `reverse` | ❌ 不重要 |
| **碰巧**某组输入下结果相同 | `trim` + `reverse` on `"  ab  "` | ⚠️ 只是巧合，换输入就变 |
| 装饰之间存在依赖 | `head3`（截前三） + `reverse` | ✅ 重要，且是语义的一部分 |

**对比 3 是一个陷阱**。我一开始用它来证明"不可交换"，结果两种顺序都得到 `"ba"`。
原因是 `"  ab  "` 首尾空格等长，反转后两端空格仍在，trim 的结果刚好一样。
换成 `"  abc  "` 还是相同，换成 `" abc   "`（两端空格不等长）就会不同：

- `trim(reverse(" abc   "))` = `trim("   cba ")` = `"cba"`
- `reverse(trim(" abc   "))` = `reverse("abc")` = `"cba"`

还是相同！因为 trim 会把两端都削掉。所以 `trim` 和 `reverse` 对任何字符串都是可交换的
（trim 削两端空格，reverse 保持两端空格的数量，二者作用在不同的"维度"上）。

**这个弯路本身值得记下来**：判断"两个装饰是否可交换"，不能只看一两个样例，
要看**它们作用的维度是否正交**。

**对比 4 才是真正不可交换的例子**：`head3` 取前三个字符，`reverse` 反转。
一个作用在"位置"上，一个作用在"顺序"上 —— 两者互相影响。

- 先反转再截断：`abcdef` → `fedcba` → `fed`
- 先截断再反转：`abcdef` → `abc` → `cba`

**在真实业务里**，不可交换的装饰非常多：

| 装饰 A | 装饰 B | 顺序为什么重要 |
|---|---|---|
| 压缩 | 加密 | 先压缩再加密 → 密文小；先加密再压缩 → 压缩率极低 |
| 日志 | 重试 | 先日志再重试 → 每次重试都记；反之 → 只记最终结果 |
| 缓存 | 权限校验 | 先缓存再校验 → 未授权用户可能拿到缓存；反之 → 安全 |
| 限流 | 业务处理 | 先限流再处理 → 被限流的请求不进业务；反之 → 白干一遍 |

**所以"装饰器的顺序由客户端决定"既是优点也是负担**：
它把顺序的责任交给了调用方。写框架的人必须在文档里说清楚"哪个装饰器该放外层"。

---

## 7. 扩展对比：新增一种样式要改几个文件

**需求**：加一个"高亮背景"样式。

### 7.1 使用装饰器：新增 1 个文件，改 0 处

```java
// 新增 HighlightDecorator.java
public class HighlightDecorator extends TextDecorator {
    private final String background;

    public HighlightDecorator(TextComponent component, String background) {
        super(component);
        this.background = background;
    }

    @Override
    public String render() {
        return "<span style=\"background:" + background + "\">"
                + super.render() + "</span>";
    }
}
```

**改动清单**：

| 文件 | 改动 |
|---|---|
| `HighlightDecorator.java` | **新增** |
| `TextComponent` / `TextDecorator` / `PlainText` | **零改动** |
| `BoldDecorator` / `ItalicDecorator` / `UnderlineDecorator` / `ColorDecorator` | **零改动** |
| `Client` | 零改动（除非想演示新样式） |

这就是开闭原则的标准姿势：**对扩展开放（加类），对修改关闭（不改已有类）**。

### 7.2 用继承做，要改几个文件

假设当前已有 4 种样式（15 个子类）。加第五种：

- 新增 1 个"高亮"子类
- 新增"高亮 + 加粗""高亮 + 斜体""高亮 + 颜色""高亮 + 下划线" 4 个
- 新增两两组合 6 个、三三组合 4 个、四四组合 1 个、五五组合 1 个
- **一共新增 16 个类**（总类数从 15 → 31）

而且这 16 个类里，每一个都要写一遍"高亮"的那段渲染逻辑。改一处 bug 要改 16 遍。

### 7.3 装饰器 vs 继承：一张表算清

| | 继承 | 装饰器 |
|---|---|---|
| 5 种样式的全组合类数 | 31 | 5（装饰器） + 1（PlainText） = 6 |
| 新增第 6 种样式 | +32 个类 | +1 个类 |
| 组合在什么时期决定 | 编译期 | **运行期** |
| 能按条件选择吗 | 不能（除非写 if-else 选类） | 能（直接 `if` 包裹） |
| 能叠加同一个装饰两次吗 | 不能 | 能（`<i><i>x</i></i>`） |
| 调试难度 | 低（一层调用） | 高（多层嵌套） |
| 性能 | 好（无额外调用） | 略差（每层一次虚调用） |

### 7.4 装饰器 vs 代理模式

这两个模式在代码结构上**几乎一模一样**：都是"实现同一接口 + 持有一个同类型引用"。

| | 装饰器 | 代理 |
|---|---|---|
| 客户端知道吗 | **知道**（客户端自己组装） | **不知道**（客户端拿到的是代理，以为是本体） |
| 层数 | 任意多层，可自由组合 | 通常一层 |
| 目的 | **增强功能**（加料） | **控制访问**（延迟加载、权限、远程调用） |
| 谁创建 | 客户端 | 框架 / 工厂 |
| 典型例子 | `BufferedReader`、`Collections.unmodifiableList` | Spring AOP、MyBatis Mapper、RMI stub |

**一句话区分**：装饰器是"我主动给你穿了件外套"；代理是"你以为在跟我说话，其实是秘书在转达"。

**代码上的关键差异**：装饰器的构造器通常接收一个 `TextComponent`，意味着**调用方必须自己 new 内层**；
代理通常**自己创建**被代理对象（或者用动态代理生成），调用方只 new 代理。

### 7.5 装饰器 vs 适配器

| | 装饰器 | 适配器 |
|---|---|---|
| 接口是否改变 | **不改变**（装饰前后同一接口） | **改变**（把 A 接口转成 B 接口） |
| 目的 | 增强功能 | 让不兼容的接口能协作 |
| 例子 | `BufferedInputStream`（还是 `InputStream`） | `InputStreamReader`（`InputStream` → `Reader`） |

**判断口诀**：装饰后**类型不变**，适配后**类型改变**。

### 7.6 装饰器 vs 组合模式

这是最容易被混淆的一对，因为结构极像（都是持有同类型引用）。

| | 装饰器 | 组合 |
|---|---|---|
| 持有数量 | **1 个** `Component` | **N 个** `Component`（List） |
| 结构 | 链（线性） | 树（层次） |
| 聚合方式 | **转发 + 包裹**（每层只增强一次） | **递归聚合**（求和、计数） |
| 客户端感知 | 感知不到层数 | 感知不到层级 |
| 删除一层 | 拆掉包装即可 | 删掉一个分支 |

**一句话**：装饰器是"我穿了一件外套"，组合是"我有一个团队"。

### 7.7 JDK 与框架里的装饰器

| 位置 | 装饰器 | 被装饰的 |
|---|---|---|
| `java.io` | `BufferedInputStream` / `DataInputStream` / `GZIPInputStream` | `InputStream` |
| `java.io` | `BufferedReader` / `LineNumberReader` | `Reader` |
| `java.util.Collections` | `unmodifiableList` / `synchronizedList` / `checkedList` | `List` |
| `java.util` | `Collections.unmodifiableMap` 等全套 | Collection 家族 |
| Servlet | `HttpServletRequestWrapper` / `HttpServletResponseWrapper` | 请求/响应对象 |
| Spring | `TransactionAwareCacheDecorator` | `Cache` |
| Spring | `ServerHttpRequestDecorator`（WebFlux） | `ServerHttpRequest` |
| MyBatis | `CachingExecutor` | `Executor` |
| Netty | `ChannelHandler` 的 `ByteToMessageDecoder` 等 | pipeline 上的处理器 |
| GoF 原文 | `BorderDecorator` / `ScrollDecorator` | `VisualComponent` |

**`Collections.unmodifiableList` 值得单独说**：

```java
List<String> readOnly = Collections.unmodifiableList(new ArrayList<>(List.of("a", "b")));
readOnly.add("c");   // UnsupportedOperationException
```

它返回的 `UnmodifiableList` 实现了 `List` 接口、持有一个 `List` 引用、把**所有读方法转发**给内层、
把所有**写方法改成抛异常**。这就是一个 22 个方法的装饰器（`List` 有 20+ 个方法要转发）。

**这也解释了为什么 §3 说"接口方法太多时别用装饰器"**：JDK 能这么干，是因为它可以写一个
`UnmodifiableList` 内部类，用 100 行代码把所有方法转发一遍，而且写一次就永不再改。
你写业务代码时没这个预算。

### 7.8 装饰器 + 工厂 = 从配置构建装饰链

真实项目里，装饰链通常不是手写的，而是根据配置组装：

```java
public static TextComponent build(String content, List<String> styles) {
    TextComponent result = new PlainText(content);
    for (String style : styles) {
        result = switch (style) {
            case "bold" -> new BoldDecorator(result);
            case "italic" -> new ItalicDecorator(result);
            case "underline" -> new UnderlineDecorator(result);
            case "red" -> new ColorDecorator(result, "red");
            case "blue" -> new ColorDecorator(result, "blue");
            default -> throw new IllegalArgumentException("未知样式：" + style);
        };
    }
    return result;
}
```

`build("hi", List.of("bold", "red"))` → `<span style="color:red"><b>hi</b></span>`

**注意顺序**：循环是从左到右包装，所以 `List.of("bold", "red")` 里 `red` 后包装、**在外层**。
这个细节如果搞反，配置文件的语义就全错了 —— 所以文档必须写清楚"列表顺序 = 从内到外"。

### 7.9 装饰器 + 模板方法：基类定义骨架

§4.3 提到的"父类默认转发"其实就是模板方法的雏形。完整版可以是这样：

```java
public abstract class TimingDecorator implements TextComponent {
    protected final TextComponent component;

    protected TimingDecorator(TextComponent component) { this.component = component; }

    @Override
    public final String render() {                 // final：禁止子类覆盖骨架
        long start = System.nanoTime();
        String inner = component.render();          // 固定的一步：先算内层
        String result = decorate(inner);            // 可变的一步：子类决定加什么料
        System.out.println(getClass().getSimpleName() + " 耗时 "
                + (System.nanoTime() - start) + " ns");
        return result;
    }

    /** 子类只实现这一步。 */
    protected abstract String decorate(String inner);
}
```

这个设计把"先算内层、再计时、最后返回"的骨架固定住，子类只写 `decorate`。
它比 `super.render()` 更严格 —— **子类根本没法忘记委托**，因为骨架里已经调过了。

> 代价是灵活性下降：如果某个装饰器需要"看一眼内层再决定要不要调它"（比如缓存装饰器
> 可能直接返回缓存而不调内层），这个骨架就用不了了。
> **模板方法换安全，装饰器换灵活**，二选一。

---

## 8. 优缺点与常见误区

### 8.1 优点

1. **避免类爆炸**：n 种功能从 2ⁿ−1 个类降到 n 个类。
2. **运行期可组合**：装饰哪些、几层、什么顺序，全在运行时决定。
3. **单一职责**：每个装饰器只管一件事，代码短到可以一眼看完。
4. **符合开闭原则**：加功能 = 加类，不改已有代码。
5. **可叠加自身**：`<i><i>x</i></i>` 合法，继承做不到。
6. **类型不变**：装饰后仍是原接口，可以无缝替换。

### 8.2 缺点

1. **产生大量小类**：每个功能一个类，类数量可能膨胀（虽然比继承的 2ⁿ 好得多）。
2. **调试困难**：断点要穿过多层，栈帧比实际业务深好几倍。
3. **身份变化**：`getClass()`、`equals`、`hashCode` 都不再是原对象的值。
4. **顺序敏感**：装饰顺序错了，结果可能完全不同（见 §6.4）。
5. **接口方法多时成本高**：`List` 有 20+ 个方法，写一个装饰器要转发一遍。
6. **理解门槛**：新人看到 `new A(new B(new C(x)))` 会懵，尤其在配置驱动的项目里。

### 8.3 三个真实代价

**① `instanceof` 判断失效**

```java
TextComponent text = new BoldDecorator(new PlainText("hi"));
System.out.println(text instanceof PlainText);       // false
System.out.println(text.getClass() == PlainText.class); // false
```

如果代码里有"如果是 `PlainText` 就特殊处理"的逻辑，装饰之后就走不到了。
**解决**：要么别依赖具体类型（用接口方法），要么加一个 `undecorate()` 方法剥掉所有装饰层：

```java
public static TextComponent unwrap(TextComponent c) {
    while (c instanceof TextDecorator d) {
        c = d.component;
    }
    return c;
}
```

**② `equals` 变了**

`PlainText` 是 record，`new PlainText("hi").equals(new PlainText("hi"))` 为 `true`。
但 `new BoldDecorator(new PlainText("hi")).equals(new PlainText("hi"))` 为 `false` ——
`BoldDecorator` 没重写 `equals`，用的是 `Object` 的引用相等。

如果要把装饰后的对象放进 `HashSet` 或当 `Map` 的 key，**必须知道这一点**。
（真要支持相等比较，得给 `TextDecorator` 实现 `equals`，但那样又会引出一堆问题：
两个不同装饰器包着同一个文本，算不算相等？）

**③ 断点穿不透**

装饰链 5 层，一个 bug 出在第 3 层。你在 `Client` 里打个断点，然后单步跟进 ——
要按十几次 F7 才能到位。如果装饰链是配置动态生成的，你甚至不知道有几层。

**解决**：临时加一段"打印装饰链"的调试代码：

```java
public static void dump(TextComponent c, String indent) {
    System.out.println(indent + c.getClass().getSimpleName());
    if (c instanceof TextDecorator d) {
        dump(d.component, indent + "  ");
    }
}
```

### 8.4 常见误区

| 误区 | 真相 |
|---|---|
| "装饰器就是在方法前后加代码" | 那是 AOP / 代理。装饰器**必须实现同一接口**，并且**持有一个同类型的对象**。只加代码不持有对象的，不叫装饰器 |
| "装饰器和继承一样，只是写法不同" | 继承是**编译期**决定、**静态**叠加；装饰器是**运行期**决定、**动态**叠加。前者类数 2ⁿ，后者 n |
| "装饰器越多层越好" | 每层都是一次虚调用 + 一层栈帧。5 层以上就很难调试，10 层以上通常说明设计有问题（考虑合并或改成策略） |
| "装饰器能改变被装饰对象的行为" | 不能。它**看不到内部状态**，只能在内层返回的结果上加工。想改行为，得用策略模式替换算法 |
| "装饰器顺序随便排" | 可交换的可以，不可交换的（压缩 + 加密、日志 + 重试）顺序就是语义。§6.4 有实测 |
| "`super.render()` 和 `component.render()` 一样" | 今天等价，明天不一样。用 `super` 才能让父类将来加的逻辑自动生效 |
| "装饰后对象还是原来那个对象" | 不是。`==` 和 `equals` 都变了，`getClass()` 也变了。**只有静态类型不变** |
| "有了装饰器就不用改原类了" | 对，但你需要**原类实现了一个稳定的接口**。如果原类只有一堆具体方法没有接口，装饰器无从下手 |

### 8.5 一个反直觉的点：装饰器越多，越难保证正确

假设每个装饰器的单测覆盖率都是 99%（很高了）。5 层装饰组合起来，
"整条链正确"的概率是 0.99⁵ ≈ **95%**。10 层就是 90%。20 层只剩 82%。

这不是说装饰器不好，而是说：**装饰链本身是一个需要被测试的复合对象**。

正确做法是维护一组"组合测试"：

```java
@Test
void bold_red_underline_组合输出正确() {
    TextComponent c = new UnderlineDecorator(
            new ColorDecorator(new BoldDecorator(new PlainText("x")), "red"));
    assertEquals("<u><span style=\"color:red\"><b>x</b></span></u>", c.render());
}
```

**测的是"组合后的结果"，不是"每一层独立正确"。**

---

## 9. 面试与自测

### 9.1 面试讲解（30 秒版）

> "装饰器模式在不改变原类和接口的前提下，运行期给对象动态叠加功能。核心是让装饰器**和被装饰者实现同一个接口**，并在内部持有一个被装饰者引用。每次调 `render()` 都是先让内层渲染，拿到结果再在外面裹一层自己的标签。典型应用是 Java IO：`BufferedReader` 包 `InputStreamReader` 包 `FileInputStream`。用它替代继承的原因是避免类爆炸 —— 4 种样式的全组合需要 15 个类，装饰器只要 4 个，而且能按运行期条件自由组装、能对同一个装饰器叠多次。"

### 9.2 面试讲解（2 分钟版）

> "装饰器解决的是'动态给对象加功能'的问题，而且是**不改变接口**的加功能。
>
> **不用它会怎样**：想支持加粗、斜体、颜色、下划线四种样式的任意组合，用继承要写 2⁴−1 = 15 个类；再加一种样式就是 31 个。而且组合在编译期就固定了，用户从界面勾选的组合根本穷举不了。
>
> **四个角色**：抽象组件 `TextComponent` 声明 `render()`；具体组件 `PlainText` 返回原始内容，是递归终点；装饰器基类 `TextDecorator` 持有 `TextComponent` 并提供默认转发；具体装饰器各自覆盖 `render()`，写 `"<b>" + super.render() + "</b>"`。
>
> **核心机制**：`component` 的静态类型是接口，所以它既可以是 `PlainText` 也可以是另一个装饰器 —— 这就是链条能无限嵌套的原因。调用从外向内钻，返回值从内向外裹。
>
> **几个设计取舍**：① `component` 用 `protected final` —— 装饰关系一旦建立就不该被替换，实测原项目非 final 时能中途掉包；② 基类提供默认 `render()` 转发 —— 原项目没有，导致子类漏写 `component.render()` 时**内层内容静默丢失，编译和运行都不报错**；③ 构造器判空 —— 否则 `new BoldDecorator(null)` 构造成功、`render()` 时才 NPE；④ 颜色用白名单 + 正则校验 —— 因为颜色值会被拼进 HTML，不校验就是 XSS 入口。
>
> **一个容易忽略的点**：装饰顺序。加粗和变色可交换，但压缩和加密不可交换 —— 先压缩再加密密文小，反过来压缩率极低。所以框架必须文档化'哪个装饰器放外层'。
>
> **代价**：`getClass()`、`equals` 都变了，`instanceof PlainText` 在装饰后是 false；调试要穿过多层栈帧。"

### 9.3 高频追问

**Q1：装饰器和代理模式怎么区分？**

> 结构几乎一样（实现同一接口 + 持有同类型引用），区别在**意图**和**谁创建**：
>
> - 装饰器：客户端**知道**自己在套壳，壳由客户端组装，目的是**增强功能**，可以套多层
> - 代理：客户端**不知道**有壳，代理通常由框架创建，目的是**控制访问**（延迟加载、权限、远程调用），通常一层
>
> 一句话：装饰器是"我主动穿上外套"，代理是"我以为是本人，其实是秘书"。
>
> 代码上的判据：装饰器的构造器**接收**被装饰对象（`new BoldDecorator(text)`）；
> 代理通常**自己创建**被代理对象（`new UserDaoProxy()` 内部 `new UserDaoImpl()`）。

**Q2：为什么不直接用一个 `List<Renderer>` 循环处理？**

> 可以，但那就不是装饰器了。区别在于：
>
> ```java
> // 装饰器：层层包裹，每层都拿到"内层的结果"
> text = new BoldDecorator(text);          // BoldDecorator 内部持有 text
>
> // 处理器链：遍历调用，每层拿到"同一个输入"
> for (Renderer r : renderers) { r.render(input); }
> ```
>
> 装饰器的关键在于**下一层的结果是上一层的输入**。它支持"内层结果决定外层怎么处理"
> （比如"如果内层是空的就不加标签"）。处理器链则是各干各的，最后再合并。
>
> 三种结构的区别：
> - 装饰器：`f(g(h(x)))` —— 嵌套调用
> - 职责链：`a → b → c` —— 传递同一个请求，可能被某一环终止
> - 观察者：`a, b, c` 都收到通知 —— 并列

**Q3：`Collections.unmodifiableList` 是装饰器吗？**

> 是。它实现了 `List` 接口、持有一个 `List` 引用、把所有读方法转发给内层、把写方法改成抛
> `UnsupportedOperationException`。这是"选择性转发"的装饰器 —— 不是所有方法都原样转发。
>
> 同理 `Collections.synchronizedList`（给每个方法加 `synchronized`）、
> `Collections.checkedList`（在 `add` 时做类型检查）都是装饰器。
>
> 它们也印证了 §3 的警告：`List` 有 20+ 个方法，所以 JDK 里这三个类的代码都超过 300 行，
> **全是转发**。接口越大，装饰器越贵。

**Q4：装饰器链能有多长？有什么限制？**

> 技术上没有硬限制，但有三条实践边界：
>
> ① **栈深度**：每层装饰至少 2 个栈帧（装饰器 + super），100 层就是 200 帧。
>    JVM 默认栈深度约 5000-10000，理论上千层也没事，但没必要冒这个险。
> ② **调试成本**：超过 5 层，单步调试就变得难以忍受。
> ③ **性能**：每层一次虚方法调用。在百万 QPS 的路径上，10 层装饰的调用开销是可测量的。
>
> 我的经验值：**业务代码 1-3 层，框架代码 3-5 层**。超过 5 层，通常说明
> "这些功能本该合并成一个装饰器"或者"这里其实需要的是策略/职责链"。

**Q5：如果想在装饰器里访问被装饰对象的内部状态怎么办？**

> 两个办法：
>
> **① 通过接口暴露**。如果 `TextComponent` 有 `String raw()` 方法返回原始内容，装饰器就能拿到。
>    但这会污染接口 —— 为了一个装饰器的需要，让所有实现都多一个方法。
>
> **② 用 `instanceof` + 强转**。`if (component instanceof PlainText p) { p.content(); }`。
>    简单但脆弱 —— 一旦内层是另一个装饰器，判断就失败了。要写成循环剥壳：
>    `while (component instanceof TextDecorator d) { component = d.component; }`。
>
> 更好的思路是**问自己为什么要访问内部状态**。通常说明"这个功能不该是装饰器"——
> 装饰器的语义是"在结果上加工"，而不是"检查原材料"。
> 需要检查原材料的，应该用**策略模式**（把算法换掉）或**访问者模式**（在结构上遍历）。

**Q6：`TextDecorator` 为什么是抽象类而不是接口？**

> 因为它要**持有状态**（`component` 字段）。接口不能有实例字段（`private` 字段）。
>
> 如果写成接口 + `default` 方法，那 `component` 只能靠实现类自己存 ——
> 每个装饰器又得重复写字段和构造器，抽象类就白抽了。
>
> 补充：**JDK 21+ 可以用 `sealed interface` + `record` 组合**，但 record 不能持有可变引用
> 且不能继承，不适合做装饰器基类。所以抽象类在这里仍是正确选择。

### 9.4 自测题

**1. 为什么装饰器必须和被装饰者实现同一个接口？**

<details><summary>参考答案</summary>

因为装饰器要能**替换**被装饰者的位置。看这个链条：

```java
TextComponent text = new PlainText("hi");
text = new BoldDecorator(text);      // ← 这一行要求 BoldDecorator 能被赋给 TextComponent
text = new ColorDecorator(text);     // ← 同样
```

如果 `BoldDecorator` 不实现 `TextComponent`，第一行赋值就编译失败，链条立刻断裂。

更深一层：**装饰器的 `component` 字段类型必须是 `TextComponent`**，否则它只能包 `PlainText`，
不能包另一个装饰器 —— 那就退化成"一层装饰"，失去模式的价值。

**这一条不满足，就不是装饰器模式**，无论代码长得多像。
</details>

**2. `new BoldDecorator(null)` 在原项目和重构版分别会发生什么？**

<details><summary>参考答案</summary>

**原项目**：构造成功。输出 `new BoldDecorator(null) 构造成功，类型 = BoldDecorator`。
直到调用 `render()` 时才抛 `NullPointerException`（因为 `component.render()` 对 null 调用）。

**重构版**：构造时立刻抛 `IllegalArgumentException: 被装饰对象不能为 null`。

差别在于**错误发现的时机**：
- 原项目：错误在"使用"时暴露，距离"产生"可能隔了几小时、跨了几个模块
- 重构版：错误在"产生"时暴露，堆栈直接指向出错那一行

这是"快速失败"（fail-fast）原则的典型应用。参数校验的成本是几微秒，
收益是把排查时间从小时级降到秒级。
</details>

**3. `super.render()` 和 `component.render()` 今天输出相同，为什么要用前者？**

<details><summary>参考答案</summary>

因为**将来会不同**。假设某天在 `TextDecorator` 里加一段统一逻辑：

```java
@Override
public String render() {
    long start = System.nanoTime();
    String result = component.render();
    System.out.println(getClass().getSimpleName() + " 耗时 " + (System.nanoTime() - start));
    return result;
}
```

- 子类写 `super.render()` → 自动获得计时
- 子类写 `component.render()` → **绕过**父类，计时丢失

这就是"模板方法"的思路：父类定义骨架，子类只负责加料。

另外还有两个次要好处：① `component` 的可见性将来可能改（`protected` → `private`），
用 `super` 不受影响；② 语义更准确 —— "我在父类行为上加料"，而不是"我在操作字段"。
</details>

**4. 请照着 `ColorDecorator` 写一个"高亮背景"装饰器，支持 `#RRGGBB` 和几个常用色名。**

<details><summary>参考答案</summary>

```java
public class HighlightDecorator extends TextDecorator {

    private static final Set<String> NAMED_COLORS =
            Set.of("yellow", "pink", "lightblue", "lightgray", "orange");

    private final String background;

    public HighlightDecorator(TextComponent component, String background) {
        super(component);
        if (background == null || background.isBlank()) {
            throw new IllegalArgumentException("背景色不能为空");
        }
        boolean isNamed = NAMED_COLORS.contains(background.toLowerCase());
        boolean isHex = background.matches("#[0-9a-fA-F]{6}");
        if (!isNamed && !isHex) {
            throw new IllegalArgumentException("不支持的背景色：" + background);
        }
        this.background = background;
    }

    @Override
    public String render() {
        return "<span style=\"background:" + background + "\">"
                + super.render() + "</span>";
    }
}
```

关键点：① 继承 `TextDecorator` 而不是直接实现接口；② 调 `super.render()` 拿内层结果；
③ 校验放在构造器；④ 用 `htmlEscape` 之类的手段处理外部输入（生产代码必须）。

写完之后应该能通过类似这样的测试：

```java
assertEquals("<span style=\"background:yellow\">x</span>",
        new HighlightDecorator(new PlainText("x"), "yellow").render());
```
</details>

**5. 装饰链 5 层，第 3 层的装饰器有 bug。你怎么定位？**

<details><summary>参考答案</summary>

**第一步：打印装饰链**，先确认结构和你以为的一致：

```java
public static void dump(TextComponent c, String indent) {
    System.out.println(indent + c.getClass().getSimpleName());
    if (c instanceof TextDecorator d) {
        dump(d.component, indent + "  ");
    }
}
```

很多时候问题就出在这一步 —— 你以为套了 3 层，实际是 4 层（因为配置里多了一条）。

**第二步：逐层验证输出**。从最内层开始，每加一层就打印一次：

```java
TextComponent c = new PlainText("x");
System.out.println(c.render());              // 内层基准
c = new ItalicDecorator(c);
System.out.println(c.render());              // 加第 2 层
c = new ColorDecorator(c, "red");
System.out.println(c.render());              // 加第 3 层 ← 问题在这一步暴露
```

**第三步：在装饰器基类里加日志**（临时），所有层自动打日志：

```java
@Override
public String render() {
    System.out.println(">>> 进入 " + getClass().getSimpleName());
    String result = component.render();
    System.out.println("<<< 离开 " + getClass().getSimpleName() + "： " + result);
    return result;
}
```

嵌套的输出会呈现清晰的层次（进进进…出出出），一眼就能看出哪一层的返回值不对。

**根治**：如果这类 bug 反复出现，说明缺少"组合测试"（§8.5）。
加一组针对常见组合的断言测试，比事后调试划算得多。
</details>

**6. 为什么说"装饰器顺序由客户端决定"既是优点也是负担？**

<details><summary>参考答案</summary>

**优点**：灵活性。同一个 `PlainText`，在不同场景下可以组装成不同的样式，
不需要为每种场景写一个类。§6.2 场景 3 就是例子 —— 同一个循环里，张三被装饰两层，李四没有。

**负担**：**正确性责任转移给了调用方**。框架作者知道"缓存必须放在权限校验之后"，
但客户端不知道。一旦调用方顺序搞反，就会出现安全漏洞或性能问题。

看这张表（§6.4 的扩展）：

| 装饰 A | 装饰 B | 顺序错了会怎样 |
|---|---|---|
| 缓存 | 权限校验 | 未授权用户可能命中缓存拿到数据 |
| 日志 | 重试 | 只记最终结果，看不出重试了几次 |
| 限流 | 业务 | 被限流的请求也走了一遍业务逻辑，限流失效 |

**所以框架必须做的事**：
1. **文档化**：明确写出"缓存装饰器必须放在最外层，权限校验必须放在最内层"
2. **提供组装入口**：不要让客户端自己 `new` 一堆装饰器，而是给一个 `builder()` 或工厂方法，
   内部按正确顺序组装
3. **加断言**：如果某个装饰器必须在最外层，可以在 `render()` 里检查
   `if (getClass() != ExpectedOutermost.class) throw new IllegalStateException(...)`
</details>

---

## 10. 附录

### 10.1 文件清单

| 文件 | 行数 | 角色 |
|---|---|---|
| `TextComponent.java` | 32 | 抽象组件，声明 `render()` |
| `PlainText.java` | 36 | record 具体组件，紧凑构造器判空 |
| `TextDecorator.java` | 65 | 抽象装饰器基类，`protected final component` + 默认转发 |
| `BoldDecorator.java` | 29 | 具体装饰器：加粗 |
| `ItalicDecorator.java` | 22 | 具体装饰器：斜体 |
| `UnderlineDecorator.java` | 18 | 具体装饰器：下划线 |
| `ColorDecorator.java` | 60 | 带参数的具体装饰器：颜色（含白名单校验） |
| `Client.java` | 146 | 五个场景的演示入口 |
| `README.md` | 本文档 | — |

### 10.2 本文引用的探针

探针不属于交付代码，只用于取证。

| 探针 | 位置 | 用途 | 输出 |
|---|---|---|---|
| `DecoratorProbe.java` | 原项目包内 | 原项目 6 个缺陷 | 20 行 |
| `DecoratorFinalProbe.java` | 重构版包内 | `final` 字段阻止替换 | **故意编译失败**：`无法为 final 变量 component 分配值` |
| `DecoratorProProbe.java` | 默认包 | 重构版的校验、默认转发、白名单 | 12 行 |
| `DecoratorOrderProbe.java` | 默认包 | 装饰顺序的可交换性 | 15 行 |

> `DecoratorProbe` 和 `DecoratorFinalProbe` 必须放在对应包内，因为要访问 `protected` 字段。
> 这是"测试同包可见性"的常用技巧。

### 10.3 实测环境

| 项 | 值 |
|---|---|
| JDK | `javac 25.0.4.1` |
| 运行参数 | `java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.decorator.Client` |
| 重构版输出 | 29 行 / 1391 字节 / SHA-256 前 16 位 `551D0ADCFF282FC2` |
| 原项目输出 | 2 行 / 80 字节 |

> **关于字节数口径**：必须用 `Start-Process -RedirectStandardOutput` 落盘后统计。
> PowerShell 的 `>` 重定向会转码，同一段输出在 UTF-8 与 GBK 下字节数差一个汉字数。

### 10.4 本次修订记录

| 改动 | 原因 |
|---|---|
| `TextDecorator.component` 加 `final` | 原版可被中途替换，实测能掉包 |
| `TextDecorator` 增加默认 `render()` 转发 | 原版没有，子类漏写委托会**静默丢失**内层内容 |
| `TextDecorator` 构造器判空 | 原版 `new BoldDecorator(null)` 构造成功，`render()` 才 NPE |
| `PlainText` 紧凑构造器判空 | 原版 `new PlainText(null)` 会渲染出字符串 `"null"` |
| `ColorDecorator` 增加白名单 + 正则校验 | 原版任意字符串都能拼进 HTML，是 XSS 入口 |
| `style` 属性单引号改双引号 | 与项目其他部分保持一致 |
| 新增 `ItalicDecorator` / `UnderlineDecorator` | 演示"同一装饰器可叠加多次""四种样式自由组合" |
| 客户端从 1 个场景扩到 5 个 | 原版只演示了"能跑"，没演示"为什么需要它" |
| 本文档新增 §0 结论、§6.4 顺序实测、§10 附录 | 原文档 595 行，缺少实测数据与决策记录 |
