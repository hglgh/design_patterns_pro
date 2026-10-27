# 享元模式（Flyweight Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：把「不随场景变化的重数据」提炼出来全局只留一份，把「随场景变化的轻数据」放在外面每次传进来。
>
> 本项目取的例子是**字符字形**：一个 `'a'` 的字形数据（轮廓点集、度量表、位图缓存）只有一份，全文档共享；它出现在第几行第几列、什么颜色，**不进享元**。

---

**📖 目录**

- [0. 先看结论](#0-先看结论)
- [1. 现实案例](#1-现实案例)
- [2. 原始实现：不用享元会怎样，原项目又写成了什么样](#2-原始实现不用享元会怎样原项目又写成了什么样)
- [3. 设计动机](#3-设计动机)
- [4. 设计推导：为什么是这五个角色](#4-设计推导为什么是这五个角色)
- [5. 代码解析：每个类为什么必须存在](#5-代码解析每个类为什么必须存在)
- [6. 执行流程：从 main 到输出](#6-执行流程从-main-到输出)
- [7. 扩展对比](#7-扩展对比)
- [8. 优缺点与常见误区](#8-优缺点与常见误区)
- [9. 面试与自测](#9-面试与自测)
- [10. 附录](#10-附录)

---

## 0. 先看结论

### 0.1 三句话

1. **享元模式只在"对象又多、单个又重"时才有意义。** 如果一个对象只有 20 字节，共享 100 万次也只省 20MB，换个方向优化更划算。
2. **它的全部技术难点不在"池"上，而在"切分"上**——判断哪些字段能进享元（内部状态）、哪些必须留在外面（外部状态）。切错了，共享就变成数据错误。
3. **共享的前提是不可变。** 一旦某个调用方能修改享元对象，所有共享它的调用方都会莫名其妙地看到数据变了，这种 bug 几乎无法排查。

### 0.2 一句话说清它解决什么

> **同一份重数据，被成千上万个"使用现场"需要，但每个现场只多出一点点不同的轻数据。**

文字渲染是最典型的场景：

| 数据 | 有多重 | 随什么变化 |
|---|---|---|
| `'a'` 的字形轮廓点集、度量信息、位图缓存 | 几 KB 到几十 KB | **不随场景变化**——永远是同一个 `'a'` |
| 这个 `'a'` 画在第几行第几列、什么颜色、什么字号 | 几十字节 | **每次都要变** |

如果一个字符实例同时持有这两部分，那么屏幕上出现 10 万个字符就要 10 万份字形数据。而实际上整篇文章可能只用到 60 种字符。

**享元的做法**：字形数据抽到一个被所有字符共享的对象里（1 份），坐标颜色留在每个字符自己的小对象里（10 万份且每份极轻）。

```
不用享元：100000 × (10KB + 30B) ≈ 1000 MB
用享元：    60 × 10KB + 100000 × 30B ≈ 3.5 MB
```

差三个数量级。**这就是享元存在的唯一理由。**

### 0.3 诚实前置说明：原项目方向对了，但没证明"值得"

原项目的享元实现（`structuraltype/FlyweightPattern`，5 个文件）**方向是正确的**：

- ✅ `Glyph` 接口把 `x/y/color` 放在方法参数上，这就是内外部状态的分界线，切得对；
- ✅ `CharacterGlyph` 用 `record` 保证不可变，这是共享的前提，选得对；
- ✅ 有独立的 `GlyphFactory` 管池，没有让客户端自己 `new`，架构对；
- ✅ 5 个文件全部编译通过，运行输出 5 行，没有报错。

但它有**三个层面**的问题。

**第一层：它没能证明"值得共享"。**

原项目的 Client 只放了 5 个字符：

```java
CharacterView[] page = new CharacterView[]{
        new CharacterView(factory.getGlyph('a', "Arial"), 10, 10, "black"),
        new CharacterView(factory.getGlyph('b', "Arial"), 20, 10, "black"),
        new CharacterView(factory.getGlyph('a', "Arial"), 30, 10, "red"),
        new CharacterView(factory.getGlyph('a', "Arial"), 40, 10, "blue"),
        new CharacterView(factory.getGlyph('b', "Arial"), 50, 10, "black"),
};
```

5 个视图、2 个享元，省了 3 个对象。**在 5 个对象的规模上讨论享元，等于在 5 块钱的生意上谈成本优化。**

**第二层：它没能证明"共享真的发生了"。**

那 5 行输出是这样：

```text
渲染字符 'a'，字体：Arial，颜色：black，位置：(10,10)
渲染字符 'b'，字体：Arial，颜色：black，位置：(20,10)
渲染字符 'a'，字体：Arial，颜色：red，位置：(30,10)
渲染字符 'a'，字体：Arial，颜色：blue，位置：(40,10)
渲染字符 'b'，字体：Arial，颜色：black，位置：(50,10)
```

5 行渲染结果里，「共享」两个字一次都没出现。读者看完只知道"画了 5 个字符"，**不知道这个模式到底在干什么**。如果把 `GlyphFactory.getGlyph` 改成 `return new CharacterGlyph(symbol, font)`（每次新建），这 5 行输出**一字不差**——因为 `record` 的 `equals` 是按值比较的。

这就是享元教学里最大的坑：**共享失败是静默的**。

**第三层：有 4 个实际缺陷**，其中第一个会直接导致渲染错字体。详见 [§2.6 探针取证](#26-探针取证原项目的四个缺陷)。

重构版针对这三点做了处理：

| 原项目 | 重构版 | 为什么 |
|---|---|---|
| 5 个字符、2 个享元 | 一段 131 字文本、26 个享元，命中率 75.2% | 让规模效应可见 |
| 无任何统计 | `requestCount` / `creationCount` / `savedCount` / `poolSize` / `pooledKeys` | 让共享可量化、可断言 |
| 输出看不出共享 | demo1 直接 `a1 == a2 ? true` | 共享的唯一证据是**引用相等** |
| key 用 `char + ":" + font` 拼 | **仍然这样**（未修，见 §8.3） | 诚实记录，不假装修好了 |
| 池只增不减 | **仍然如此**（未修，见 §8.3） | 同上 |

### 0.4 读完你应该能回答的 6 个问题

1. 内部状态和外部状态**怎么切**？给我一个能在 10 秒内做出的判断口诀。
2. 为什么享元工厂的 key **必须覆盖全部内部状态**？漏掉一个会出什么错？
3. `record` 的 `equals` 为什么会让"共享失效"变得几乎无法察觉？单元测试该用 `assertEquals` 还是 `assertSame`？
4. 享元跟缓存、对象池的**本质区别**是什么？
5. 什么情况下**不该**用享元？给三个具体的判断条件。
6. 本项目在 `-Xmx64m` 下，300 万个字符"共享 vs 不共享"分别是什么结果？

---

## 1. 现实案例

### 1.1 说人话版：开了 20 年的打印店

老张开了 20 年打印店，接了个活：印一本 500 页的书。

**新手做法（不用享元）**：每印一个字就重新刻一个铅字模。整本书 50 万字，就要刻 50 万个字模。每个字模是块金属，均价 5 块钱——光字模成本 250 万。而且刻模的时间比印刷还长。

**老张做法（用享元）**：

- **第一步**：先统计这本书用了哪些字。一查，常用字只有 3000 个（汉字常用字表就这么多）。
- **第二步**：刻 **3000 个字模**，放进抽屉，编号存档。这个抽屉叫**字模库**（享元池）。
- **第三步**：印到某个位置需要"第 37 页第 5 行的'的'字"时，从抽屉里拿出"的"字的那个字模，**位置信息不刻在字模上**——位置由排版单（外部状态）告诉机器。

成本对比：

| | 字模数量 | 字模成本 | 每个字的位置信息 |
|---|---|---|---|
| 新手 | 50 万个 | 250 万 | 刻在字模上，用完就废 |
| 老张 | 3000 个 | 1.5 万 | 写在排版单上，一张纸几分钱 |

**这就是享元模式的全部内容。** 三个关键点，缺一不可：

1. **字模要能被多个位置复用** → 字模上不能带位置信息（**内部状态与外部状态分离**）
2. **字模必须不可变** → 如果印到一半有人把"的"字的字模磨掉一角，后面所有"的"都印错（**享元必须不可变**）
3. **必须有一个抽屉统一管** → 不能谁要用谁去刻，那就又变成 50 万个了（**必须由工厂统一分配**）

### 1.2 专业版：文字渲染引擎

真实系统的规模和数字是这样的：

**一个 `Glyph` 对象里到底存什么**

| 字段 | 大小量级 | 说明 |
|---|---|---|
| 贝塞尔曲线轮廓点集 | 200 点 × 2 坐标 × 4 字节 ≈ **1.6 KB** | 矢量字形的主体 |
| 度量信息（ascent/descent/advance width） | 几十字节 | 排版要用 |
| kerning 表（字距调整对） | 数百字节 | "AV" 要比 "AA" 紧一点 |
| 预渲染位图缓存（32px 灰度） | 32 × 32 = **1 KB** | 光栅化后的结果，避免每次重算 |
| 多分辨率位图（16/32/64px） | 3 份 ≈ **3 KB** | 缩放时直接取对应档 |

单个 Glyph **轻松 5–10 KB**。

**一篇 10 万字中文文章的算账**

- 用到多少个不同的字？常用汉字约 3000 个，一篇文章实际用到 **1500–2500 个**。
- 不共享：100000 × 8KB = **800 MB**
- 共享：2000 × 8KB + 100000 × 32B ≈ **19 MB**

**这就是为什么所有文字渲染系统——FreeType、Skia、DirectWrite、CoreText——都必然有字形缓存（glyph cache）。** 而"字形缓存"就是享元模式在系统级的一个具体实现。

**浏览器处理一个网页的字体时**，同一个道理：一个页面可能引 4 种字体、每种 200 个字形，共享后字形数据只有 800 份；而页面上有 5 万个字符节点。

### 1.3 现实世界里的享元清单

| 场景 | 内部状态（共享） | 外部状态（每次传） | 为什么值得 |
|---|---|---|---|
| 文字渲染 | 字形轮廓、度量、位图 | 坐标、颜色、字号 | 单字形 5–10 KB |
| 棋盘游戏（围棋） | 棋子的颜色/图案（黑/白） | 落在第几行第几列 | 棋盘 361 个位置，只有 2 种棋子 |
| 森林渲染（游戏） | 树的模型、纹理、网格 | 位置、旋转、缩放 | 一棵树的模型几 MB |
| 文档编辑器 | 字符格式（字体/字号/粗斜体组合） | 起始位置、长度 | 同一格式被几千个片段共用 |
| 电商 SKU 展示 | 商品基础信息（标题/主图/描述） | 库存、价格、促销标签 | 基础信息被几十个渠道复用 |
| `Integer.valueOf(-128~127)` | 数值本身 | —— | JDK 内置享元池 |
| `String` 常量池 | 字面量内容 | —— | JVM 级享元 |
| `Boolean.TRUE/FALSE` | 两个常量 | —— | 最简享元 |

**注意最后三行**——享元不是一个"只有大型系统才用"的模式，JDK 里到处是它：

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);
System.out.println(a == b);   // true —— 命中享元池

Integer c = Integer.valueOf(128);
Integer d = Integer.valueOf(128);
System.out.println(c == d);   // false —— 超出池范围，各自新建
```

这个 `-128~127` 缓存（`IntegerCache`）就是标准享元：内部状态是整数值，池在上限 127 个对象。**它带来的一个著名副作用**：用 `==` 比较两个 `Integer` 时，127 以下碰巧正确、128 以上突然错误。这个坑每年都在坑新人——**本质上就是"享元池边界"没有被使用者意识到**。

### 1.4 一个必须先澄清的误解：享元不是缓存

初学者最容易把两者混为一谈。"都是拿 Map 存对象、下次直接取"，看起来一样。但目的和判据完全不同：

| 维度 | 享元（Flyweight） | 缓存（Cache） |
|---|---|---|
| **目的** | 省**内存**——同一份数据不多存 | 省**时间**——同一份计算不重算 |
| **存的什么** | 逻辑上"本来就该是一份"的共享数据 | 某次计算的**结果**，可能过期 |
| **能否淘汰** | 理论上**不能**——淘汰了就要重建，语义仍在 | **必须**能淘汰——否则内存涨爆 |
| **命中率** | 由**数据本身的重复度**决定，与时间无关 | 由**访问局部性**决定，随时间变化 |
| **失效问题** | 不存在（数据不可变） | 核心难题（什么时候失效） |
| **典型实现** | `Map<内部状态, 享元>` | `Map<请求参数, 计算结果>` + TTL/LRU |
| **判断标准** | 对象**多且重** | 计算**慢且可重复** |

**一句话区分**：

> 缓存是"算过了，先存着，以后可能还要用"。
> 享元是"这个东西本来就只有一份，不该有第二份"。

这个区别有非常实际的后果——**它决定了你该不该给池加淘汰**：

- 缓存**必须**有淘汰策略（LRU / TTL / 容量上限），否则内存泄漏。
- 享元**理论上不该**有淘汰——一个 `'a'` 的字形只有一份，淘汰了下次还得重建，反而更慢。但如果**内部状态的组合是无限的**（比如用户可以上传任意字体），就必须有上界，否则池会无限增长。

本项目重构版**没有**做池的淘汰，这是一个**已知遗留瑕疵**（见 [§8.3](#83-已知遗留瑕疵诚实清单)）。原因写在那里，不含糊。

---

## 2. 原始实现：不用享元会怎样，原项目又写成了什么样

这一节走完四步：**先看不用享元的反例 → 看反例的真实代价 → 看原项目怎么写的 → 用探针把原项目的问题抓出来**。

### 2.1 v0：把全部状态塞进一个对象

最直觉的写法——一个字符对象，包含它的一切：

```java
/**
 * v0：不用享元的写法。
 * 每种"字符 + 位置 + 颜色"的组合都是一个独立对象，字形数据跟着复制。
 */
public class Character {
    private final char symbol;      // 字符本身
    private final String font;      // 字体
    private final byte[] outline;   // 字形轮廓（真实系统里几 KB）
    private final int x;            // 横坐标
    private final int y;            // 纵坐标
    private final String color;     // 颜色

    public Character(char symbol, String font, byte[] outline,
                     int x, int y, String color) {
        this.symbol = symbol;
        this.font = font;
        this.outline = outline;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public void draw() {
        System.out.println("渲染 " + symbol + " 于 (" + x + "," + y + ") 颜色 " + color);
    }
}
```

然后用它画一段文字：

```java
String text = "hello world";
byte[] aOutline = loadOutline('a');   // 从字体文件读出来，假设 8KB
int x = 0;
List<Character> page = new ArrayList<>();
for (char ch : text.toCharArray()) {
    page.add(new Character(ch, "Arial", loadOutline(ch), x, 20, "black"));
    x += 12;
}
```

### 2.2 v0 的两个问题（不只是内存）

**问题 1：内存按"字符出现次数"增长，而不是按"不同字符数"增长**

```
"hello world"  → 11 个 Character 对象 → 11 份 outline
实际只用到 8 种不同字符（h/e/l/o/w/r/d/空格）
```

"hello world" 里 `l` 出现 3 次、`o` 出现 2 次——**这 5 份 `outline` 是完全相同的数据，被复制了 5 次**。

规模一大就是灾难：

| 文本长度 | `Character` 对象数 | 字形数据占用（每份 8KB） |
|---|---|---|
| 100 字 | 100 | 800 KB |
| 1 万字 | 10000 | 78 MB |
| 10 万字 | 100000 | **781 MB** |

**问题 2：数据一致性风险**

`outline` 是 `byte[]`，数组是**可变**的。如果某个地方不小心改了 `page.get(0).getOutline()[0]`，那么**只有第 0 个 `h`** 的轮廓坏了，第 5 个位置上的 `h` 还是好的——同一篇文章里两个 `h` 长得不一样，这种 bug 排查起来极其痛苦。

**注意这一点很重要**：`byte[]` 共享之后问题会**放大**——如果共享同一份数组，改一处所有处都坏。所以享元模式对"不可变"的要求不是教条，而是**共享这个动作本身带来的强制约束**。

### 2.3 v0 的第一次修补：加个 Map 缓存（仍然不对）

看到内存问题，很自然会想到"加个 Map 存起来"：

```java
/** 第一次修补：搞个静态 Map 缓存字形 */
public class Character {
    private static final Map<Character, byte[]> OUTLINE_CACHE = new HashMap<>();

    private final char symbol;
    private final String font;
    private final int x;
    private final int y;
    private final String color;

    public Character(char symbol, String font, int x, int y, String color) {
        this.symbol = symbol;
        this.font = font;
        this.x = x;
        this.y = y;
        this.color = color;
        OUTLINE_CACHE.computeIfAbsent(this, k -> loadOutline(symbol));
    }

    // getter 略
}
```

这个写法有**三个问题**，每一个都很典型：

**问题 1：`Character` 被用作 Map 的 key，而它带有 `x/y/color`。**

`Character` 的 `equals`/`hashCode` 如果包含 `x/y/color`，那么 `('a', 10, 10)` 和 `('a', 34, 10)` 是两个**不同的 key**，缓存完全失效——每个位置仍然加载一份字形。

**如果想修这个，就必须把 `x/y/color` 从 key 里排除**——那 `equals` 就变得很怪：两个对象不完全相等却返回 `true`。这是设计开始崩坏的第一个信号。

**问题 2：`OUTLINE_CACHE` 是静态的，池的生命周期失控。**

静态 Map 没有归属，无法按场景销毁。测试 A 跑完的数据留在里面，测试 B 拿到的是脏数据。

**问题 3：`byte[]` 共享出去后仍可被改。**

`getOutline()` 如果直接 `return OUTLINE_CACHE.get(...)`，那么任何调用方都能改到共享数据。**共享+可变 = 全局污染**。

**这三个问题合起来，正好就是享元模式要解决的全部内容**：

| 第一次修补的问题 | 享元模式的答案 |
|---|---|
| key 混入了外部状态 | **把外部状态从对象里彻底挪出**，放进方法参数（`Glyph.render(x, y, color)`） |
| 静态 Map 生命周期失控 | **池放在工厂实例里**，由工厂的生命周期决定 |
| 共享数据可被修改 | **享元对象必须不可变**（`record` 或全 `final` 字段 + 防御性复制） |

### 2.4 原项目 v1 的实际代码

原项目（`E:\codeFile\codeFather\design_patterns\src\structuraltype\FlyweightPattern`）5 个文件，共 107 行：

**`Glyph.java`（13 行）** —— 享元接口

```java
public interface Glyph {
    void render(int x, int y, String color);
}
```

**`CharacterGlyph.java`（17 行）** —— 具体享元

```java
public record CharacterGlyph(char symbol, String font) implements Glyph {
    @Override
    public void render(int x, int y, String color) {
        System.out.println("渲染字符 '" + symbol + "'，字体：" + font + "，颜色 ：" + color + "，位置：(" + x + "," + y + ")");
    }
}
```

**`GlyphFactory.java`（23 行）** —— 享元工厂

```java
public class GlyphFactory {
    private final Map<String, Glyph> glyphPool = new HashMap<>();

    public Glyph getGlyph(char symbol, String font) {
        String key = symbol + ":" + font;
        if (!glyphPool.containsKey(key)) {
            glyphPool.put(key, new CharacterGlyph(symbol, font));
        }
        return glyphPool.get(key);
    }
}
```

**`CharacterView.java`（27 行）** —— 外部状态持有者

```java
public class CharacterView {
    private final Glyph glyph;
    private final int x;
    private final int y;
    private final String color;

    public CharacterView(Glyph glyph, int x, int y, String color) {
        this.glyph = glyph;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public void draw() {
        glyph.render(x, y, color);
    }
}
```

**`Client.java`（27 行）** —— 客户端

已在上文 [§0.3](#03-诚实前置说明原项目方向对了但没证明值得) 引用过，这里不再重复。

### 2.5 原项目实测输出

编译运行（JDK 25）：

```text
渲染字符 'a'，字体：Arial，颜色：black，位置：(10,10)
渲染字符 'b'，字体：Arial，颜色：black，位置：(20,10)
渲染字符 'a'，字体：Arial，颜色：red，位置：(30,10)
渲染字符 'a'，字体：Arial，颜色：blue，位置：(40,10)
渲染字符 'b'，字体：Arial，颜色：black，位置：(50,10)
```

实测：**5 行 / 352 字节**，`JAVAC_EXIT=0`，无警告无错误。

**这 5 行输出里有一个隐藏的信息量**：第 1、3、4 行都是 `'a'`，颜色不同、位置不同，但渲染结果里看不出"它们用的是同一个对象"。

要证明这一点，必须写 `a1 == a2`。而原项目的 Client 一次 `==` 都没写。

### 2.6 探针取证：原项目的四个缺陷

`_probe/FlyweightOrigProbe.java`（原项目包内），实测 **21 行 / 1081 字节**：

```text
[事实 1] font 传 null 与传字符串 "null" 撞成同一个 key
  getGlyph('a', null)   = CharacterGlyph[symbol=a, font=null]
  getGlyph('a', "null") = CharacterGlyph[symbol=a, font=null]
  是同一个对象？true
  池大小 = 1（两次不同字体，本该是 2）

[事实 2] containsKey + put 不是原子操作，并发下同一字形被重复创建
  3000 轮 × 16 线程争抢同一个 key 的首次创建
  出现过多个实例的轮数 = 12 / 3000
  单轮最多创建出几个实例 = 3（=1 才叫真共享）

[事实 3] 池只增不减，没有任何淘汰手段
  请求 20000 个不同汉字后，池大小 = 20000
  GlyphFactory 的自有方法全部列出：
    public Glyph getGlyph(2 参数)
  没有 clear / remove / 容量上限 —— 池里的对象无处释放

[事实 4] record 的 equals 让"共享失效"极难被发现
  x1 == x2 ?       false   ← 共享已失效，是两个对象
  x1.equals(x2) ?  true    ← 但 equals 依然 true
  所以单元测试里用 assertEquals 抓不到这个 bug，必须用 assertSame
```

（事实 2 的数字每次运行都会波动，这是并发测试的固有性质，下文 §8.3 会说明。）

#### P1：key 用字符串拼接，`null` 与 `"null"` 撞成同一个 key

```java
String key = symbol + ":" + font;
```

当 `font` 是 `null` 时：

```java
'a' + ":" + null   →   "a:null"
```

当 `font` 是字符串 `"null"` 时：

```java
'a' + ":" + "null"  →   "a:null"
```

**同一个 key。** 于是 `getGlyph('a', null)` 和 `getGlyph('a', "null")` 返回**同一个对象**——第二个调用方拿到的是字体为 `null` 的字形，却以为自己请求的是字体名 `"null"`。

探针实测 `池大小 = 1（本该是 2）`，确认碰撞。

**这个问题在真实系统里会怎么爆**：

- 字体是可选的（默认字体 → `font = null`），用户上传了一个名为 `"null"` 的字体文件 → 两批字符渲染成同一个字形，用户看到"我设了字体但没生效"。
- 更普遍的情况：任何"用字符串拼接做复合 key"的地方都有这个风险。比如 `userId + ":" + orderId`，当某个 id 本身含 `:` 时，`("a:b", "c")` 和 `("a", "b:c")` 会撞。

**为什么本项目没有修**：修法很简单（用 `record Key(char symbol, String font)` 作 key），但本项目选择了**如实记录而不是假装修好**——因为这个缺陷恰好是最有价值的一课。真正的修法见 [§7.5](#75-场景-e把字符串-key-换成-record-key修掉-null-碰撞)，那里有完整代码。

#### P2：`containsKey` + `put` 不是原子操作

```java
if (!glyphPool.containsKey(key)) {     // ← 步骤 1：检查
    glyphPool.put(key, new CharacterGlyph(symbol, font));   // ← 步骤 2：放入
}
return glyphPool.get(key);             // ← 步骤 3：取出
```

对同一个 key，三个步骤一共做了 **3 次哈希计算 + 3 次桶查找**。但性能不是重点——**重点是"检查"和"放入"之间存在时间窗口**：

```text
线程 A：containsKey("a:null") → false
线程 B：containsKey("a:null") → false      ← A 还没来得及 put
线程 A：put("a:null", X)
线程 B：put("a:null", Y)                    ← 覆盖了 X
线程 A：get → Y（拿到 B 建的）
线程 B：get → Y
```

结果：**创建了 2 个对象，池里只留 1 个**。对于本项目这种"享元对象很轻"的场景，泄漏 1 个对象无所谓；但如果享元是 10KB 的字形、每毫秒命中一次竞态，一小时就能漏几百 MB。

探针实测：**3000 轮 × 16 线程，12 轮出现多实例，单轮最多 3 个**。

#### P3：池只增不减，没有淘汰手段

探针实测：请求 20000 个不同汉字后，池大小 = 20000，而 `GlyphFactory` 的全部公开方法只有一个：

```text
public Glyph getGlyph(2 参数)
```

没有 `clear()`、没有 `remove()`、没有容量上限、没有 LRU。**池里的对象无处释放。**

对文字渲染场景，这通常**不算问题**——汉字常用字就 3000 个，池涨到 3000 就停了。但如果内部状态里包含"用户上传的字体"，那么组合数就是**无限的**：

```text
(字符 65536 种) × (字体 无限种) = 无限
```

一个恶意用户上传 10 万个字体文件、每个文件触发 3000 个字形的创建 → 池里 3 亿个对象。**这是真实的攻击面。**

重构版**也没有**修这个问题（见 [§8.3](#83-已知遗留瑕疵诚实清单)），但它把"池里现在有多少"暴露出来了（`getPoolSize()`），至少**可观测**。

#### P4：`record` 的 `equals` 让"共享失效"极难被发现

这是本项目最有教学价值的一条。

`CharacterGlyph` 是 `record`，编译器自动生成**按值比较**的 `equals`：

```java
Glyph x1 = new CharacterGlyph('a', "Arial");
Glyph x2 = new CharacterGlyph('a', "Arial");
x1 == x2        // false —— 两个不同对象，共享已失效
x1.equals(x2)   // true  —— 但按值相等
```

于是这么写单元测试：

```java
// ❌ 这个断言抓不到任何问题
assertEquals(factory.getGlyph('a', "Arial"), factory.getGlyph('a', "Arial"));
```

即使工厂每次 `return new CharacterGlyph(...)`（完全不共享），这个断言**永远通过**。测试全绿，内存照样爆炸。

**正确的断言只有一种**：

```java
// ✅ 这才是共享的判据
assertSame(factory.getGlyph('a', "Arial"), factory.getGlyph('a', "Arial"));
```

**推广开来的原则**：

> 验证"两个引用是否指向同一个对象"，必须用 `==` / `assertSame` / `is`（Python）；
> `equals` / `assertEquals` / `==`（Python）验证的是"值是否相等"，**与共享无关**。

这条原则在写享元的单元测试时是硬性的。重构版的 [§2.6](#26-探针取证原项目的四个缺陷) 之外，demo1 直接打印了 `a1 == a2 ? true`，就是为了让这件事**在运行输出里可见**。

### 2.7 缺陷汇总 P1–P8

| 编号 | 缺陷 | 类型 | 探针证据 | 重构版是否修复 |
|---|---|---|---|---|
| P1 | key 用字符串拼接，`null` 与 `"null"` 碰撞 | 正确性 | 事实 1，池大小 1 | **否**（§8.3 记录，§7.5 给修法） |
| P2 | `containsKey` + `put` 非原子，并发重复创建 | 并发 | 事实 2，12/3000 轮 | **否**（§7.3 给修法并实测 0/3000） |
| P3 | 池只增不减，无淘汰、无容量上界 | 内存 | 事实 3，池 20000 | **否**（仅增加可观测性） |
| P4 | `record.equals` 掩盖共享失效 | 测试 | 事实 4 | **是**（demo1 用 `==` 明示） |
| P5 | 无任何统计 API，共享效果不可观测 | 可观测性 | 事实 3 方法列表 | **是**（5 个统计方法） |
| P6 | 5 个字符 / 2 个享元，规模效应看不出来 | 教学 | 原 Client 输出 5 行 | **是**（131 字 / 26 享元） |
| P7 | 内部状态只有 `symbol` + `font`，漏掉字号/字重就是数据错误 | 设计 | —— | **否**（§7.1、§7.2 讨论） |
| P8 | 没有"何时不该用享元"的边界说明 | 教学 | —— | **是**（§3.4） |

**这张表里 4 个"否"，是本项目刻意保留的。** 一个诚实的学习材料应该告诉读者"哪里还没做好"，而不是假装完美。每一个"否"都在后续章节给了具体的修法和代价分析。

---

## 3. 设计动机

### 3.1 一句话动机

> **同一个对象被需要 10 万次，但它只有 60 种。**

这句话里藏着两个数字，缺一个享元就不成立：

- **10 万次** → 使用频次高，所以"共享"能省下大量重复；
- **只有 60 种** → 种类少，所以"池"能装得下、放得进。

如果反过来——10 万种各用 1 次——那共享毫无意义，直接 `new` 更快。

### 3.2 内部状态 / 外部状态的判定方法

这是享元模式唯一需要动脑的地方。判断错了，整个实现就错了。

#### 口诀

> **「这个东西会不会因为使用场景不同而不同？」**
>
> **会 → 外部状态（传参）；不会 → 内部状态（存字段）。**

#### 三个例子

| 字段 | 会因场景不同而不同吗 | 判定 | 理由 |
|---|---|---|---|
| `symbol`（哪个字符） | 不会。`'a'` 就是 `'a'`，在哪个位置都是 `'a'` | **内部** | 它是享元的身份 |
| `font`（什么字体） | 不会。同一份文档里 Arial 就是 Arial | **内部** | 不同字体就是不同的字形数据 |
| `x` / `y`（坐标） | 会。每画一次都不同 | **外部** | 存进对象等于放弃了共享 |
| `color`（颜色） | 会。同一个 `'a'` 可以是黑也可以是红 | **外部** | 同上 |
| `fontSize`（字号） | **看情况** ⚠️ | 取决于设计 | 见下文 |

#### 一个反例：把 `color` 当成内部状态会怎样

有人会想："颜色也是字形的一部分啊，红色 `'a'` 和黑色 `'a'` 长得不一样，为什么不放进享元？"

```java
// ❌ 错误切分
public record CharacterGlyph(char symbol, String font, String color) implements Glyph {
    @Override
    public void render(int x, int y) {      // 注意：color 从参数里消失了
        System.out.println(symbol + " " + color);
    }
}
```

后果立刻出现：

```text
池里需要存的组合数 = 字符数 × 字体数 × 颜色数
```

- 3000 个汉字 × 4 种字体 × 16 种颜色 = **19.2 万**个享元
- 而如果 `color` 是外部状态，只要 3000 × 4 = **1.2 万**个

**颜色是典型的"渲染期属性"，它的取值空间与字形数据无关，放进内部状态是纯粹的浪费。**

一般的判断标准是：**这个字段是否影响"字形数据本身的计算"**。

- `symbol` + `font` 影响：要从字体文件里查哪一段轮廓。
- `fontSize` **影响**：不同字号要光栅化成不同分辨率的位图。但在矢量渲染里，一份轮廓可以缩放，所以字号常常**不做内部状态**（缩放即可）。这就是上表里标 ⚠️ 的原因——**它取决于你的渲染管线是矢量的还是位图的**。
- `color` **不影响**：字形轮廓与颜色无关，颜色是填充时的一个参数。

#### 判定错了的两个方向

| 错误方向 | 后果 | 严重程度 |
|---|---|---|
| **该外部的做成了内部** | 池里的组合数爆炸，共享率骤降，甚至比不用享元更费内存 | 性能问题，可观测 |
| **该内部的做成了外部** | 同一个享元在不同场景下渲染出**错误结果**（如字体串了） | **正确性问题，静默发生** |

**第二种更危险。** 而且它和 §2.6 的 P1（key 漏字段）是同一个病根：**内部状态的集合没被完整地表达成一个 key**。

### 3.3 享元生效的四个前提

GoF 原文里给出的是三条，本项目补上第四条（并发），因为这是 Java 工程里绕不过去的：

| # | 前提 | 本项目 | 如果不满足 |
|---|---|---|---|
| 1 | 应用**大量**使用对象 | ✅ 一段 131 字文本 → 105 个视图 | 用几十个对象谈共享毫无意义 |
| 2 | 对象**占用内存大** | ⚠️ Demo 里 `char + String` 很小，真实字形 5–10KB | 共享省下的量抵不过池的管理成本 |
| 3 | 对象的**大部分状态可以外部化** | ✅ `x/y/color` 完全外部化 | 切不出内部状态，共享无从谈起 |
| 4 | 能被**多个线程安全共享**（本项目补充） | ❌ 池不是线程安全的，见 §8.3 | 并发下重复创建，或更糟：数据竞争 |

**前提 2 是本项目 Demo 的天然弱点。** 一个 `CharacterGlyph` 里只有 `char`（2 字节）+ `String` 引用（4 字节）+ 对象头（12 字节）≈ 24 字节。共享 105 次只省 79 × 24B ≈ **1.9 KB**。

这 1.9 KB 当然不值得任何优化。**但模式的形状是对的**——所以本项目用 `FlyweightMemoryProbe` 做了个真正的内存实验（§6.5），把"共享到底省多少"变成可观测的事实，而不是靠嘴说。

### 3.4 什么时候不需要享元

这一节比"什么时候用"更重要。**模式误用的代价远大于不用模式。**

#### 情况 1：对象数量少（< 几千个）

```text
60 个享元 × 10KB = 600KB      ← 完全不需要优化
60 个享元 × 24B  = 1.4KB      ← 优化了也是笑话
```

**判断**：先算一遍"不共享会占多少内存"。如果答案是"几 MB 以内"，直接 `new`，别引入池。

#### 情况 2：内部状态种类多、重复率低

```text
10 万个对象，9.9 万种不同状态 → 池里会装 9.9 万个
```

这时享元**只会增加复杂度**：多了一层 Map 查找，多了一个可能出 bug 的 key 设计，省下的内存几乎为零。

**判断**：`不同状态数 / 总对象数` 这个比值低于 **20%** 时，收益基本被管理成本吃掉。本项目的实际比值是 `26 / 105 = 24.8%`（即命中率 75.2%），刚好在有效区间。

#### 情况 3：对象是可变的需要频繁修改

```java
// ❌ 这样的对象不能做享元
public class GameCharacter {
    private int hp;        // 会变
    private int mp;        // 会变
    private Position pos;  // 会变
}
```

**享元必须是不可变的。** 如果对象有会变的状态，它就不该被共享——两个"使用者"改同一个对象，必然互相干扰。

**判断**：对象里所有字段能不能都声明成 `final`？不能就不要用享元。

#### 决策树

```text
              ┌─────────────────────────────┐
              │ 同一类对象要创建很多个吗？   │
              └──────────┬──────────────────┘
                   否 ───┴─── 是
                    │         │
              ┌─────▼─────┐   ▼
              │ 直接用 new │ ┌──────────────────────────────┐
              └───────────┘ │ 单个对象占多少内存？          │
                            └──────────┬───────────────────┘
                                 < 100B ┴ >= 1KB
                                  │          │
                    ┌─────────────▼──┐   ┌───▼──────────────────────┐
                    │ 不值得，找别的  │   │ 大部分字段能挪到方法参数？ │
                    │ 优化点          │   └──────┬───────────────────┘
                    └────────────────┘      否 ───┴─── 是
                                              │          │
                                    ┌─────────▼──┐  ┌────▼──────────────────┐
                                    │ 切不出内部  │  │ 这些字段能不能全 final？ │
                                    │ 状态，放弃   │  └──────┬────────────────┘
                                    └────────────┘    否 ───┴─── 是
                                                        │          │
                                              ┌─────────▼──┐  ┌────▼─────────┐
                                              │ 会变的对象  │  │ ✓ 用享元      │
                                              │ 不能共享    │  └──────────────┘
                                              └────────────┘
```

---

## 4. 设计推导：为什么是这五个角色

这一节从**问题**出发，一步步推出结构。每一步都问"如果不这么做会怎样"。

### 4.1 第一次倒推：内存压不下来，必须先分离状态

**问题**：10 万个 `Character` 对象，每个 8KB 字形数据，共 800MB。

**第一次思考**：能不能让它们指向同一个 `byte[]`？

```java
public class Character {
    private static final byte[] A_OUTLINE = loadOutline('a');   // 共享
    private final char symbol;
    private final int x, y;
}
```

**立刻失败**：静态字段做不到按需加载——文章里没用到 `'é'` 也会加载它，而 `loadOutline` 要读字体文件，几百种字体的所有字形加起来比不共享更费内存。

**关键认识**：

> 共享不是"预先全部加载"，而是"**按需创建、创建后复用**"。

这个"按需"两字，直接推翻了静态字段的方案，指向了「一个能查表的容器」。

### 4.2 第二次倒推：需要一个容器，谁来管？

**问题**：容器放哪儿？

**方案 A：放在 `Character` 里当静态字段**

```java
public class Character {
    private static final Map<String, byte[]> POOL = new HashMap<>();
}
```

**失败**：

1. **生命周期失控**——静态字段跟着类加载器活，测试之间互相污染。
2. **职责混乱**——`Character` 是"使用者"，同时又是"管理者"，两件事混在一起。
3. **无法替换**——想给测试注入一个空池，做不到。

**方案 B：独立成一个工厂类**

```java
public class GlyphFactory {
    private final Map<String, Glyph> pool = new HashMap<>();

    public Glyph getGlyph(char symbol, String font) { ... }
}
```

**成功**，因为：

- 池的生命周期 = 工厂实例的生命周期，可以按需创建/丢弃；
- 职责单一：工厂只管"保证唯一"，不管渲染；
- 可替换：测试能传一个独立的工厂实例进来。

> **这就是为什么享元模式必然有四件套：接口 / 具体享元 / 工厂 / 客户端。** 工厂不是摆设，它是"共享"这个语义的**唯一执行者**。

### 4.3 第三次倒推：坐标放哪儿？

**问题**：`x/y/color` 从 `Character` 里挪出来了，挪到哪儿？

**方案 A：放在工厂里当参数传**

```java
// 荒谬但值得推演
public class GlyphFactory {
    public Glyph getGlyph(char symbol, String font, int x, int y, String color) {
        // 工厂凭什么知道坐标？
    }
}
```

**失败**：工厂管的是"有多少种字形"，坐标是"这一次画在哪里"，两者毫无关系。工厂不该知道坐标。

**方案 B：调用方自己拿着**

```java
// 客户端里手写
Glyph g = factory.getGlyph('a', "Arial");
g.render(10, 10, "black");
g.render(34, 10, "red");
```

**这个能用**，但有个问题：**坐标和字形分离之后，就没有"一个字符"这个概念了**。文档里的字符需要被保存、被遍历、被重新绘制——总得有个对象把"用哪个字形"和"画在哪里"绑在一起。

**方案 C：引入上下文对象 `CharacterView`**

```java
public class CharacterView {
    private final Glyph glyph;      // 指向共享的享元
    private final int x, y;
    private final String color;

    public void draw() { glyph.render(x, y, color); }
}
```

**成功**。这个类在 GoF 里叫 **UnsharedConcreteFlyweight**（不共享的具体享元），或者更通俗的"外部状态持有者 / 上下文对象"。

> **这是享元模式最容易被忽略的第四个角色。** 很多人学会了"抽接口 + 建池"，却忘了"外部状态总得有地方放"，最后把坐标塞回享元的字段里，共享就白做了。

### 4.4 类结构图

```text
        ┌──────────────────────────────────────────────────┐
        │  «interface»                                     │
        │  Glyph                                           │
        ├──────────────────────────────────────────────────┤
        │  + render(x: int, y: int, color: String): void   │  ← 外部状态全部是参数
        └────────────────────▲─────────────────────────────┘
                             │ implements
        ┌────────────────────┴─────────────────────────────┐
        │  CharacterGlyph  «record»                        │
        ├──────────────────────────────────────────────────┤
        │  - symbol: char          ← 内部状态               │
        │  - font: String          ← 内部状态               │
        ├──────────────────────────────────────────────────┤
        │  + render(x, y, color): void                     │
        └────────────────────▲─────────────────────────────┘
                             │ 创建并持有
        ┌────────────────────┴─────────────────────────────┐
        │  GlyphFactory                                    │
        ├──────────────────────────────────────────────────┤
        │  - pool: Map<String, Glyph>                      │
        │  - creationCount: int                            │
        │  - requestCount: int                             │
        ├──────────────────────────────────────────────────┤
        │  + getGlyph(symbol, font): Glyph    ← 唯一入口   │
        │  + getPoolSize(): int                            │
        │  + getCreationCount(): int                       │
        │  + getRequestCount(): int                        │
        │  + getSavedCount(): int                          │
        │  + getPooledKeys(): Set<String>                  │
        └────────────────────▲─────────────────────────────┘
                             │ 索取享元
        ┌────────────────────┴─────────────────────────────┐
        │  CharacterView            （不共享，每个位置一个） │
        ├──────────────────────────────────────────────────┤
        │  - glyph: Glyph          ← 指向共享享元           │
        │  - x: int                ← 外部状态               │
        │  - y: int                ← 外部状态               │
        │  - color: String         ← 外部状态               │
        ├──────────────────────────────────────────────────┤
        │  + draw(): void          ← 把外部状态传给享元      │
        └────────────────────▲─────────────────────────────┘
                             │ 批量生产
        ┌────────────────────┴─────────────────────────────┐
        │  TextDocument       （调用方示范，非模式必需角色） │
        ├──────────────────────────────────────────────────┤
        │  + layout(startX, startY, color): List<CharacterView> │
        └──────────────────────────────────────────────────┘
```

**箭头方向值得注意**：`CharacterView → Glyph` 是**多对一**。5 个视图的 `glyph` 字段可以全部指向同一个对象——**这就是共享在内存里的样子**。

### 4.5 运行时对象共享图

以 `Client.demo2ExternalState()` 为例，5 个 `CharacterView`、2 个 `Glyph`：

```text
  CharacterView 堆                         Glyph 堆（享元池）
  ┌─────────────────────┐
  │ view[0]             │
  │  glyph ─────────────┼──────────────┐
  │  x=10, y=10         │              │
  │  color="black"      │              ├──▶ ┌──────────────────────────┐
  └─────────────────────┘              │    │ CharacterGlyph           │
  ┌─────────────────────┐              │    │  symbol='a'              │
  │ view[1]             │              │    │  font="Arial"            │
  │  glyph ─────────────┼──────────┐   │    │  （真实系统：8KB 轮廓）    │
  │  x=22, y=10         │          │   │    └──────────────────────────┘
  │  color="black"      │          │   │            ▲
  └─────────────────────┘          │   │            │
  ┌─────────────────────┐          │   └────────────┘
  │ view[2]             │          │        （3 个视图共享 1 个 'a'）
  │  glyph ─────────────┼──────────┤
  │  x=34, y=10         │          │   ┌──────────────────────────┐
  │  color="red"        │          └──▶│ CharacterGlyph           │
  └─────────────────────┘              │  symbol='b'              │
  ┌─────────────────────┐              │  font="Arial"            │
  │ view[3]             │              └──────────────────────────┘
  │  glyph ─────────────┼──────────────────────────▲
  │  x=46, y=10         │                          │
  │  color="blue"       │                          │
  └─────────────────────┘                          │
  ┌─────────────────────┐                          │
  │ view[4]             │                          │
  │  glyph ─────────────┼──────────────────────────┘
  │  x=58, y=10         │              （2 个视图共享 1 个 'b'）
  │  color="black"      │
  └─────────────────────┘

  5 个 view 各占 ≈ 40 字节，合计 200 字节
  2 个 glyph 各占 ≈ 24 字节（Demo）/ 8KB（真实），合计 48 字节 / 16KB
```

**如果不用享元**：5 个 view 各自内嵌一份字形 → 5 × 8KB = 40KB。
**用了享元**：2 × 8KB + 5 × 40B = 16KB。

**注意**：5 个字符的规模下省了 24KB，看起来还行。但真正的差距在 10 万个字符时——那时是 800MB vs 19MB。

### 4.6 六个关键设计决策

#### 决策 1：具体享元用 `record`，不用普通类

```java
public record CharacterGlyph(char symbol, String font) implements Glyph { }
```

**替代方案**：普通类 + 全 `final` 字段 + getter。

| | `record` | 普通类 + `final` |
|---|---|---|
| 字段不可变 | 编译器保证 | 需要人工写全 `final` + 防御性复制 |
| 能否被继承 | **不能**（`record` 隐含 `final`） | 能，子类可能加可变字段 |
| 反射改字段 | **抛 `IllegalAccessException`** | `setAccessible(true)` 就能改 |
| 代码量 | 1 行 | 约 15 行 |

**为什么这一点关键**：共享的前提是不可变。用普通类，任何人写个 `class MyGlyph extends CharacterGlyph` 加个 setter 就破坏了整个池的语义。`record` 从语言层面堵死这条路。

探针 `FlyweightProProbe` 事实 3 的实测：

```text
是否 final  = true
是否 record = true
  字段 symbol → private final char
  字段 font → private final String
反射强改失败：java.lang.IllegalAccessException（record 字段连反射都改不了）
```

**这一条比想象中更强**：普通类的 `private final` 字段，用 `setAccessible(true)` + `Field.set` 是能改的（JDK 有 `--illegal-access` 等历史包袱）。但 `record` 的组件字段被 JDK 特殊处理，**反射都改不了**。

#### 决策 2：key 必须覆盖全部内部状态

本项目用的 key 是 `symbol + ":" + font`，两个内部状态都在里面。

**如果漏掉 `font`**：

```java
String key = String.valueOf(symbol);   // ❌ 只看字符
```

```java
Glyph g1 = factory.getGlyph('a', "Arial");
Glyph g2 = factory.getGlyph('a', "SimSun");
g1 == g2   // true —— 但字体不同，本该是两个对象
```

`g2` 拿到的是 Arial 的字形，却以为自己在用宋体。**渲染出来字体错了，而编译器、运行时、日志都不会报任何错。**

**这是一类无解的 bug**：它不崩、不抛异常、不写日志，只在最终的视觉结果上体现。而等你在几万行文本里发现"某几个字字体不对"时，几乎不可能定位到是 key 少了一个字段。

> **规则**：内部状态的集合，必须与 key 的构成**一一对应**。改内部状态字段时，必须同步改 key。

这条规则在实践中经常被破坏，因为"加字段"和"改 key"是两个不同文件的两次修改，很容易只改一个。**彻底的解法**是用 `record` 做 key（见 §7.5），让编译器把字段和 key 绑在一起。

#### 决策 3：工厂是唯一入口，客户端不能 `new`

`GlyphFactory` 之外，**没有任何地方能创建 `CharacterGlyph`**。

严格来说 Java 里做不到完全禁止（`CharacterGlyph` 是 `public record`，客户端可以直接 `new`）。但**架构上**它只应该被工厂创建：

```java
// ✅ 正确
Glyph g = factory.getGlyph('a', "Arial");

// ❌ 能编译，但绕过了共享
Glyph g = new CharacterGlyph('a', "Arial");
```

**为什么工厂必须存在**：共享不会自动发生。`new` 一次就是一个新对象，跟池毫无关系。**只有经过工厂的请求才可能复用。**

如果想从语言层面堵死，可以把 `CharacterGlyph` 的构造器收窄：

```java
// 方案：把具体享元做成工厂的静态内部类，构造器私有
public class GlyphFactory {
    private record CharacterGlyph(char symbol, String font) implements Glyph { }
}
```

本项目没这么做，原因是**想把享元类和工厂分开、便于阅读**。这是一个可读性与封装强度的权衡，不是对错问题。

#### 决策 4：`CharacterView` 必须存在

前面 §4.3 已经推导过。这里补一个**如果不引入它会怎样**的具体后果：

```java
// 没有 CharacterView 时，客户端被迫这么写
Map<Glyph, List<int[]>> positions = new HashMap<>();
```

或者更常见的写法——把坐标塞回享元：

```java
// ❌ 灾难性的"伪享元"
public class CharacterGlyph implements Glyph {
    private final char symbol;
    private final String font;
    private final List<int[]> positions = new ArrayList<>();   // 攒着所有位置

    public void addPosition(int x, int y) { positions.add(new int[]{x, y}); }
}
```

这个写法"看起来"只有一个字形对象，内存却更糟：

- 每个 `int[]` 是一个独立对象（16 字节头 + 8 字节数据 = 24 字节），比直接用两个 `int` 字段还费；
- `positions` 列表本身会不断扩容；
- 而且它**不再是不可变的**——共享对象里塞了一个可变的 `List`。

**`CharacterView` 的价值在于：它让"不共享的部分"有了一个正当的、轻量的归属。** 每个视图只有 4 个字段（1 个引用 + 2 个 int + 1 个引用）≈ 32–40 字节，非常轻。

#### 决策 5：池对外只读

```java
public Set<String> getPooledKeys() {
    return Collections.unmodifiableSet(pool.keySet());
}
```

**如果直接 `return pool.keySet()`**：

```java
factory.getPooledKeys().clear();   // 池被一键清空，所有共享失效
```

探针 `FlyweightProProbe` 事实 1 实测：

```text
池内 key = [a:Arial]
keys.add 抛：java.lang.UnsupportedOperationException
池内 key 仍为 = [a:Arial]
```

**注意 `keySet()` 的特殊性**：`HashMap.keySet()` 返回的是**视图**，不是副本。对视图的 `remove` / `clear` 会**直接作用到原 Map**。所以 `Collections.unmodifiableSet` 包一层不是可选的保护，是**必须的**。

同样的问题在 `composite` 模块的 `children()` 里也讨论过——这是集合封装的一个通用陷阱。

#### 决策 6：加统计 API，让收益可量化

```java
private int creationCount = 0;
private int requestCount = 0;

public int getSavedCount() {
    return requestCount - creationCount;
}
```

**原项目的做法**是只提供 `getGlyph`，共享效果完全不可见。学习者只能靠读代码推断"大概省了几个"。

加上统计后，探针 `FlyweightProProbe` 事实 2 的输出：

```text
请求次数 = 1000
实际创建 = 26
节省对象 = 974
池大小   = 26
命中率   = 97.4%
```

**这组数字的价值**：

1. 它是**可断言的**——单元测试可以写 `assertEquals(26, factory.getCreationCount())`；
2. 它是**可监控的**——生产环境可以把命中率打到指标系统，命中率长期低于 30% 说明这个享元用得不对；
3. 它是**可教学的**——"97.4%"比"共享了很多"有说服力得多。

⚠️ **但这两个计数器本身不是线程安全的**，见 §8.3。探针 `FlyweightNullKeyProbe` 事实 2 实测：500 轮 × 16 线程 × 200 次请求，**485 轮漏计**，最狠的一轮 3200 次请求只记到 540 次。这是一个必须诚实记下的瑕疵。

---

## 5. 代码解析：每个类为什么必须存在

重构版共 6 个文件，合计 469 行：

| 文件 | 行数 | 角色 | 能删掉吗 |
|---|---|---|---|
| `Glyph.java` | 41 | 享元接口 | **不能**——它是内外部状态的分界线 |
| `CharacterGlyph.java` | 47 | 具体享元 | **不能**——共享的就是它 |
| `GlyphFactory.java` | 98 | 享元工厂 | **不能**——共享的执行者 |
| `CharacterView.java` | 65 | 外部状态持有者 | **不能**——删了坐标无处安放 |
| `TextDocument.java` | 79 | 调用方示范 | **可以**——但删了就看不到规模效应 |
| `Client.java` | 139 | 验证入口 | **可以**——但删了就无法证明共享 |

### 5.1 `Glyph`（接口）：方法签名就是模式的命脉

```java
public interface Glyph {
    void render(int x, int y, String color);
}
```

**只有一行方法签名，但整个模式就住在这一行里。**

```text
void render(int x, int y, String color);
              ↑─────────────────↑
              这些是「外部状态」，必须由调用方每次传进来
```

对照一下**如果签名写错**会怎样：

| 错误签名 | 后果 |
|---|---|
| `void render()` | 外部状态无处传入，只能塞回字段 → 共享失效 |
| `void render(CharacterView view)` | 享元反过来依赖上下文，形成**双向依赖**，两个类都无法单独复用 |
| `void render(int x, int y)`（少了 color） | 颜色只能存进享元 → 组合数 ×16，共享率崩盘 |

**为什么参数顺序是 `x, y, color`**：没有技术原因，只是符合"位置在前、属性在后"的常见直觉。但**同一个接口的所有实现必须一致**——否则换了实现就变成另一个接口了。

**为什么返回 `void` 而不是 `String`**：

- 返回 `void`：直接输出到控制台，Demo 简单，但**不可测试**；
- 返回 `String`：调用方拿到渲染结果自己决定怎么用，**可测试、可组合**。

本项目选了 `void`，原因是**和原项目保持一致，降低阅读差异**。如果要做生产级实现，应该改成：

```java
// 生产级写法
public interface Glyph {
    String render(int x, int y, String color);   // 返回渲染指令，而不是直接输出
}
```

这样 `Glyph` 就能在测试里被断言、在被缓存时被比较。**这是一个真实的改进方向，本项目没做，如实记录在此。**

### 5.2 `CharacterGlyph`（具体享元）：为什么必须是 `record`

```java
public record CharacterGlyph(char symbol, String font) implements Glyph {

    @Override
    public void render(int x, int y, String color) {
        System.out.printf("    渲染 '%c' | 字体=%-6s | 位置=(%3d,%3d) | 颜色=%s%n",
                symbol, font, x, y, color);
    }
}
```

#### 两个字段，一个都不能少

| 字段 | 类型 | 为什么是内部状态 |
|---|---|---|
| `symbol` | `char` | 决定从字体文件里取哪一段轮廓 |
| `font` | `String` | 决定去哪个字体文件里取 |

#### 为什么不用 `String` 存 `symbol`

用 `String` 也能跑，但：

- **内存**：`String` 对象 = 对象头 16 + `byte[]` 引用 4 + `byte[]` 对象（16 + 2）≈ 40 字节；`char` 只占 2 字节。**差 20 倍**。
- **语义**：享元的全部价值在于"共享重数据"，而它自己如果是重的，就本末倒置了。

**但 `char` 有它的局限**：它只能表示 BMP（基本多文种平面）内的字符。emoji（如 😀 U+1F600）超出 BMP，需要**代理对**（两个 `char`）。本项目用 `char`，所以**不支持 emoji**。

这是一个已知限制。生产级实现应该用 `int codePoint`：

```java
// 生产级写法
public record CharacterGlyph(int codePoint, String font) implements Glyph { }
```

`String.codePoints()` 可以拿到完整的码点流，能正确处理 emoji 和生僻字。

#### `render` 里为什么用 `printf` 而不是字符串拼接

```java
// 原项目：字符串拼接
System.out.println("渲染字符 '" + symbol + "'，字体：" + font + "，颜色 ：" + color + "，位置：(" + x + "," + y + ")");

// 重构版：printf 格式化
System.out.printf("    渲染 '%c' | 字体=%-6s | 位置=(%3d,%3d) | 颜色=%s%n", symbol, font, x, y, color);
```

**`%-6s` 和 `%3d` 让列能对齐**。这一点不是美观问题——它让 5 行输出能**垂直对比**：

```text
渲染 'a' | 字体=Arial  | 位置=( 10, 10) | 颜色=black
渲染 'b' | 字体=Arial  | 位置=( 22, 10) | 颜色=black
渲染 'a' | 字体=Arial  | 位置=( 34, 10) | 颜色=red
渲染 'a' | 字体=Arial  | 位置=( 46, 10) | 颜色=blue
渲染 'b' | 字体=Arial  | 位置=( 58, 10) | 颜色=black
              ↑ 注意字体列完全一致 —— 但它们来自 2 个不同的享元
```

字符串拼接版本里，每个字段宽度不一样，眼睛很难快速看出"哪些字段是同的、哪些是不同的"。**教学输出应该让"相同"和"不同"一眼可辨。**

#### ⚠️ 一个实际问题：享元里直接 `System.out`

`render` 方法直接写控制台，这意味着：

- 这个享元**无法在 Web 应用里使用**（没有控制台）；
- **无法测试**（断言不了输出）；
- **无法重定向到日志系统**。

这是本项目的一个**已知遗留瑕疵**，同样存在于 `GlyphFactory.getGlyph`（池未命中时打印）。正确做法是返回结构化的渲染指令，或者注入一个可替换的输出接口。见 §8.3。

### 5.3 `GlyphFactory`（工厂）：一行代码的取舍

```java
public Glyph getGlyph(char symbol, String font) {
    requestCount++;
    String key = symbol + ":" + font;
    return pool.computeIfAbsent(key, k -> {
        creationCount++;
        System.out.println("    [Factory] 池未命中，新建享元：" + k);
        return new CharacterGlyph(symbol, font);
    });
}
```

#### `computeIfAbsent` vs 原项目的三步

| | 原项目 | 重构版 |
|---|---|---|
| 写法 | `containsKey` → `put` → `get` | `computeIfAbsent` |
| 哈希计算次数 | **3 次** | **1 次** |
| 桶查找次数 | **3 次** | **1 次** |
| 在 `ConcurrentHashMap` 上 | 仍非原子 | **原子** |

**性能上的差异是次要的**（HashMap 查找本来就快）。真正重要的是最后一行：

> `computeIfAbsent` 在 `ConcurrentHashMap` 上保证**原子的"查-建-放"**，因为它会在对应桶上加锁；而 `containsKey` + `put` 两步即使换成 `ConcurrentHashMap`，两步之间仍然有窗口。

这一点非常关键。很多人以为"把 `HashMap` 换成 `ConcurrentHashMap` 就安全了"——**这取决于你用哪个方法**：

```java
// ❌ 即使 pool 是 ConcurrentHashMap，这里仍然会重复创建
if (!pool.containsKey(k)) {
    pool.put(k, create(k));
}

// ✅ 这个才是原子的
pool.computeIfAbsent(k, this::create);
```

#### ⚠️ 但本项目用的是 `HashMap`，所以并不安全

必须说清楚：`pool` 声明为 `new HashMap<>()`，**不是** `ConcurrentHashMap`。所以在 `HashMap` 上，`computeIfAbsent` **同样不是原子的**。

探针 `FlyweightProProbe` 事实 4 实测：

```text
[事实 4] computeIfAbsent 在 HashMap 上依然不是原子的：老问题没修好
  3000 轮 × 16 线程争抢同一个 key 的首次创建
  出现过多个实例的轮数 = 5 / 3000
  单轮最多创建出几个实例 = 2
  结论：换成 ConcurrentHashMap 后 computeIfAbsent 才是原子的
```

而事实 5 验证了修法：

```text
[事实 5] 验证修法：同样压力下，ConcurrentHashMap + computeIfAbsent 零失败
  3000 轮 × 16 线程，出现过多个实例的轮数 = 0 / 3000
```

**这一对数字（5/3000 与 0/3000）是本模块最有价值的一组证据。** 它说明：

- 原项目的并发缺陷**没有被真正修掉**，只是把窗口从"三次操作"缩小到了"一次方法调用"；
- 真正的修法只需要把 `HashMap` 改成 `ConcurrentHashMap`，改动量一行；
- **不改是因为 Demo 场景是单线程的**，但文档必须把这个事实摆出来，不能让读者以为 `computeIfAbsent` 是万灵药。

#### `creationCount++` 为什么在 lambda 里面

```java
pool.computeIfAbsent(key, k -> {
    creationCount++;      // ← 只有 lambda 执行（即真的创建）时才 ++
    return new CharacterGlyph(symbol, font);
});
```

`computeIfAbsent` 的 lambda **只在 key 不存在时执行一次**。所以 `creationCount++` 自然就只统计真正的创建。这是一个优雅的副产品。

**但注意一个坑**：如果 lambda 里抛异常，`creationCount` 已经加过了。不过本 lambda 不会抛异常（`new CharacterGlyph` 不会失败），所以没这个问题。

#### 五个统计方法的用途

| 方法 | 返回 | 用途 |
|---|---|---|
| `getPoolSize()` | 池里现有多少 | 监控内存增长 |
| `getCreationCount()` | 累计创建了多少 | 断言共享是否生效 |
| `getRequestCount()` | 累计请求了多少 | 算命中率的分母 |
| `getSavedCount()` | 请求 - 创建 | 直接看省了多少 |
| `getPooledKeys()` | 池里有哪些 key | 调试"为什么没命中" |

`getPooledKeys()` 在调试时特别有用。比如发现命中率低于预期，打印一下池内容就能立刻知道：

```text
池中共享的字形 = [a:Arial, b:Arial]
```

如果期望的是 26 个字母却只有 2 个，说明文本里的字母没进 `TextDocument`。

### 5.4 `CharacterView`（上下文）：最容易被忽略的角色

```java
public class CharacterView {
    private final Glyph glyph;
    private final int x;
    private final int y;
    private final String color;

    public void draw() {
        glyph.render(x, y, color);
    }
}
```

#### 注意 `draw()` 的方向

```java
public void draw() {
    glyph.render(x, y, color);   // 把「我的」外部状态传给享元
}
```

是 `CharacterView` **把自己的字段传给** `Glyph`，**不是** `Glyph` 从 `CharacterView` 里读。

这个方向很重要：

- 前者：`Glyph` 不知道 `CharacterView` 的存在，两者**单向依赖**；
- 后者：`Glyph.render(CharacterView)` 会让享元依赖上下文，**双向依赖**，两个类都变难复用。

#### 为什么每个字段都是 `final`

`CharacterView` 是**不共享**的，理论上它可以可变（比如改颜色）。但本项目全设成 `final`，理由是：

- **避免误共享**：万一有人把同一个 `CharacterView` 塞进两个位置，可变状态会互相干扰；
- **便于推理**：全 `final` 的对象可以放心地在多线程间传递。

如果需要"改颜色"，正确做法是**建一个新的 view**，而不是改旧的：

```java
// ✅ 新建
CharacterView redView = new CharacterView(glyph, x, y, "red");

// ❌ 不提供 setColor，也不应该加
```

这是**不可变对象**的通用原则：修改 = 创建新对象。`String`、`LocalDate`、`BigDecimal` 都是这么做的。

#### 体积算账

```text
对象头           12 字节
 glyph 引用        4 字节（压缩指针）
  x (int)          4 字节
  y (int)          4 字节
  color 引用        4 字节
────────────────────────────
合计             28 字节 → 对齐到 32 字节
```

每个 `CharacterView` 只占 **32 字节**。所以用享元之后：

```text
10 万个字符的内存 = 2000 个字形 × 8KB  +  10 万个 view × 32B
                 = 16 MB            +  3.2 MB
                 ≈ 19 MB
```

而不用享元是 800MB。**差 42 倍。**

### 5.5 `TextDocument`（调用方示范）：从 5 个字符到一段文本

这是重构版相比原项目**新增**的类。原项目的 Client 手写了 5 个 `CharacterView`，规模太小，看不出享元的价值。

```java
public List<CharacterView> layout(int startX, int startY, String color) {
    List<CharacterView> views = new ArrayList<>(text.length());
    int x = startX;
    for (char ch : text.toCharArray()) {
        if (ch == ' ') {
            x += CHAR_WIDTH;      // 空格不生成字形，只推进光标
            continue;
        }
        Glyph glyph = factory.getGlyph(ch, font);   // ← 关键这一行
        views.add(new CharacterView(glyph, x, startY, color));
        x += CHAR_WIDTH;
    }
    return views;
}
```

#### 为什么空格要跳过

真实渲染引擎里空格**通常不生成字形**——它的"宽度"来自字体的 `advance width` 度量，不需要轮廓。本项目简化处理：空格只推进光标，不建 `CharacterView`。

这带来一个副产品：131 字的文本只生成 105 个视图（26 个空格被跳过）。这个数字在输出里的作用：

```text
文本长度            = 131 个字符
生成的 View 对象数  = 105（空格不生成字形）
```

**它同时也解释了为什么池里只有 26 个享元**——26 个英文字母，不多不少。

#### 为什么工厂要从外部注入

```java
public TextDocument(String text, String font, GlyphFactory factory) { ... }
```

而不是在内部 `new GlyphFactory()`。理由：

> **享元池必须是全局唯一的。** 如果每个 `TextDocument` 自己 `new` 一个工厂，那两篇文档的相同字形就无法共享了。

这一点非常关键。想象一个编辑器里打开 10 个标签页：

```java
// ✅ 共享一个工厂：全局只有 26 个字形
GlyphFactory factory = new GlyphFactory();
for (String text : tabs) {
    new TextDocument(text, "Arial", factory).layout(0, 0, "black");
}

// ❌ 每个文档自己建工厂：10 × 26 = 260 个字形（实际相等的数据存了 10 份）
for (String text : tabs) {
    new TextDocument(text, "Arial", new GlyphFactory()).layout(0, 0, "black");
}
```

**"池放在哪里"决定了共享的边界。** 这是享元模式实际部署时最难的一步——在 Spring 里，它对应"工厂该是单例 Bean 还是 `new` 出来的对象"。

### 5.6 `Client`：四个 demo 各验证什么

| demo | 验证什么 | 关键断言 |
|---|---|---|
| `demo1SharedIdentity` | 相同内部状态 → 同一个引用 | `a1 == a2` 为 `true` |
| `demo2ExternalState` | 同一享元服务于不同位置/颜色 | 3 个 `'a'` 位置颜色不同，但只 2 个享元 |
| `demo3InternalStateKey` | key 必须覆盖全部内部状态 | `('a',Arial) != ('a',SimSun)` |
| `demo4ScaleEffect` | 长文本只产生少量享元 | 131 字 → 26 享元，命中率 75.2% |

**四个 demo 的顺序不是随意的**：

```text
demo1  证明「共享存在」            ← 先确立基本事实
  ↓
demo2  证明「共享安全可用」        ← 再证明共享不损失表达能力
  ↓
demo3  证明「共享边界的正确性」    ← 再划清边界在哪里
  ↓
demo4  证明「共享有规模价值」      ← 最后给规模上的证据
```

#### demo1 为什么必须先跑

如果共享根本没发生（比如工厂写错了），后面的 demo 全都没有意义。所以 **demo1 是前提**，它用最直接的 `==` 确立了事实：

```text
a1 == a2 ? true   ← true 说明是同一个对象，不是内容相等的副本
a1 == b1 ? false   ← 不同字符当然是不同对象
a1.equals(a2) ? true  ← record 的 equals 也成立，但 == 才是共享的证据
```

**第三行是全模块最重要的一行输出**：它同时展示了 `==` 和 `equals` 都为 `true` 的情况，并明确指出**哪个才是共享的证据**。§2.6 的 P4 说的就是这件事。

### 5.7 如果删掉某个类会怎样（逐个实验）

| 删掉 | 编译结果 | 后果 |
|---|---|---|
| `Glyph` 接口 | ❌ 编译失败 | `CharacterGlyph` 无法声明实现什么 |
| `CharacterGlyph` | ❌ 编译失败 | 工厂没有可创建的类型 |
| `GlyphFactory` | ❌ 编译失败 | 客户端无法获取享元 |
| `CharacterView` | ❌ 编译失败 | `TextDocument.layout` 无处返回 |
| `TextDocument` | ✅ 编译通过 | Client 的 demo4 需改写，规模效应无法演示 |
| `Client` | ✅ 编译通过 | 模块无入口，但类本身完整 |

**这四个"不能删"的类，恰好就是 GoF 定义享元的四个角色**：

```text
Flyweight（享元接口）        →  Glyph
ConcreteFlyweight（具体享元）→  CharacterGlyph
FlyweightFactory（工厂）     →  GlyphFactory
Client / Context（上下文）   →  CharacterView
```

`TextDocument` 和 `Client` 是**演示用的脚手架**，不属于模式本身。这个区分很重要——**模式是骨架，Demo 是投影**。

---

## 6. 执行流程：从 main 到输出

### 6.1 demo2 的完整时序

```text
Client                GlyphFactory            pool(HashMap)        CharacterView        CharacterGlyph
  │                        │                        │                    │                    │
  │ getGlyph('a',"Arial")  │                        │                    │                    │
  ├───────────────────────▶│ requestCount=1         │                    │                    │
  │                        │ key="a:Arial"          │                    │                    │
  │                        ├──computeIfAbsent──────▶│                    │                    │
  │                        │                        │ 未命中 → 执行 lambda │                    │
  │                        │                        │  creationCount=1   │                    │
  │                        │                        │◀───────────────────┤ new CharacterGlyph │
  │                        │◀───────────────────────┤ 放入 "a:Arial"     │                    │
  │◀───────────────────────┤ 返回 glyph#1           │                    │                    │
  │                        │                        │                    │                    │
  │ new CharacterView(glyph#1, 10, 10, "black")     │                    │                    │
  ├────────────────────────────────────────────────────────────────────▶│                    │
  │                        │                        │                    │                    │
  │ getGlyph('b',"Arial")  │                        │                    │                    │
  ├───────────────────────▶│ requestCount=2         │                    │                    │
  │                        │                        │ 未命中 → 新建 glyph#2                   │
  │◀───────────────────────┤ 返回 glyph#2           │                    │                    │
  │                        │                        │                    │                    │
  │ getGlyph('a',"Arial")  │   ← 第三次请求同一个 key │                    │                    │
  ├───────────────────────▶│ requestCount=3         │                    │                    │
  │                        │                        │ ★ 命中！不执行 lambda                   │
  │◀───────────────────────┤ 返回 glyph#1（同一个）  │                    │                    │
  │                        │                        │                    │                    │
  │ new CharacterView(glyph#1, 34, 10, "red")       │                    │                    │
  ├────────────────────────────────────────────────────────────────────▶│                    │
  │                        │                        │                    │                    │
  │ ══════════════ 绘制阶段 ══════════════          │                    │                    │
  │ for each view: view.draw()                      │                    │                    │
  ├────────────────────────────────────────────────────────────────────▶│                    │
  │                        │                        │                    ├─ glyph.render(x,y,color) ─▶│
  │                        │                        │                    │                    │ printf
```

**关键节点是 `★ 命中！`**——第三次请求 `('a', Arial)` 时，`computeIfAbsent` 发现 key 已存在，**lambda 不执行**，直接返回已存在的 `glyph#1`。

**这就是享元的全部运行时行为。** 其余都是装饰。

### 6.2 关键变量的变化轨迹

以 `demo4ScaleEffect` 为例，逐阶段记录：

| 阶段 | `requestCount` | `creationCount` | `pool.size()` | 说明 |
|---|---|---|---|---|
| 初始 | 0 | 0 | 0 | 空池 |
| 遇到首个 `t` | 1 | 1 | 1 | 新建 |
| 遇到 `h` | 2 | 2 | 2 | 新建 |
| 遇到第二个 `t`（在 "the" 里）| 3 | 2 | 2 | **命中** |
| 遍历完 105 个非空格字符 | 105 | 26 | 26 | 26 个字母全部见过 |
| 渲染中文 "你好世界你好" | 111 | 30 | 30 | 4 个新汉字（你/好/世/界）| 

**注意最后一行**：`"你好世界你好"` 有 6 个字符，但只新建了 4 个享元——`'你'` 和 `'好'` 各重复一次。

输出里的对应行：

```text
中文 6 个字里 '你' 和 '好' 各重复一次，所以只新建了 4 个享元
```

#### 一个反直觉的地方

为什么 `requestCount` 是 105 而不是 131？

```text
文本长度 131，其中空格 26 个
空格的循环走了 continue，没有调 getGlyph
所以 requestCount = 131 - 26 = 105 ✓
```

**这个细节容易被漏掉**。如果有人以为 `requestCount` 应该等于文本长度，会以为程序算错了。

### 6.3 完整实测输出

JDK 25，`java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.flyweight.Client`，**77 行 / 4474 字节 / SHA-256 前 16 位 `5818120EC6001FF7`**（连续两次运行一致）：

```text
=== 1. 共享验证：相同内部状态返回同一对象 ===
    [Factory] 池未命中，新建享元：a:Arial
    [Factory] 池未命中，新建享元：b:Arial
    a1 == a2 ? true   ← true 说明是同一个对象，不是内容相等的副本
    a1 == b1 ? false   ← 不同字符当然是不同对象
    a1.equals(a2) ? true  ← record 的 equals 也成立，但 == 才是共享的证据
    请求 3 次，实际创建 2 个，节省 1 个对象

=== 2. 外部状态：同一个 'a' 渲染在不同位置、不同颜色 ===
    [Factory] 池未命中，新建享元：a:Arial
    [Factory] 池未命中，新建享元：b:Arial
    -- 逐个绘制（注意 3 个 'a' 位置颜色各不同，但用的是同一个享元对象）--
    渲染 'a' | 字体=Arial  | 位置=( 10, 10) | 颜色=black
    渲染 'b' | 字体=Arial  | 位置=( 22, 10) | 颜色=black
    渲染 'a' | 字体=Arial  | 位置=( 34, 10) | 颜色=red
    渲染 'a' | 字体=Arial  | 位置=( 46, 10) | 颜色=blue
    渲染 'b' | 字体=Arial  | 位置=( 58, 10) | 颜色=black
    -- 统计 --
    CharacterView 对象数 = 5（不共享，每个位置一个）
    Glyph 享元对象数     = 2（共享，池内 key: [a:Arial, b:Arial]）

=== 3. 内部状态决定池的 key：同字符不同字体是两个享元 ===
    [Factory] 池未命中，新建享元：a:Arial
    [Factory] 池未命中，新建享元：a:SimSun
    ('a',Arial) == ('a',SimSun) ? false   ← false：字体不同，不能共享
    ('a',Arial) == ('a',Arial)  ? true    ← true：内部状态完全相同，共享
    池内容：[a:Arial, a:SimSun]

    若工厂只用 symbol 作 key（忽略 font），第二个调用会拿到 Arial 的字形，
    渲染出错误的字体——这是享元模式最典型的实现错误。

=== 4. 规模效应：渲染一整段文本 ===
    [Factory] 池未命中，新建享元：t:Arial
    [Factory] 池未命中，新建享元：h:Arial
    [Factory] 池未命中，新建享元：e:Arial
    [Factory] 池未命中，新建享元：q:Arial
    [Factory] 池未命中，新建享元：u:Arial
    [Factory] 池未命中，新建享元：i:Arial
    [Factory] 池未命中，新建享元：c:Arial
    [Factory] 池未命中，新建享元：k:Arial
    [Factory] 池未命中，新建享元：b:Arial
    [Factory] 池未命中，新建享元：r:Arial
    [Factory] 池未命中，新建享元：o:Arial
    [Factory] 池未命中，新建享元：w:Arial
    [Factory] 池未命中，新建享元：n:Arial
    [Factory] 池未命中，新建享元：f:Arial
    [Factory] 池未命中，新建享元：x:Arial
    [Factory] 池未命中，新建享元：j:Arial
    [Factory] 池未命中，新建享元：m:Arial
    [Factory] 池未命中，新建享元：p:Arial
    [Factory] 池未命中，新建享元：s:Arial
    [Factory] 池未命中，新建享元：v:Arial
    [Factory] 池未命中，新建享元：l:Arial
    [Factory] 池未命中，新建享元：a:Arial
    [Factory] 池未命中，新建享元：z:Arial
    [Factory] 池未命中，新建享元：y:Arial
    [Factory] 池未命中，新建享元：d:Arial
    [Factory] 池未命中，新建享元：g:Arial
    文本长度            = 131 个字符
    生成的 View 对象数  = 105（空格不生成字形）
    实际创建的享元数    = 26
    向工厂请求的次数    = 105
    节省的对象数        = 79
    池中共享的字形      = [j:Arial, c:Arial, t:Arial, o:Arial, x:Arial, r:Arial, z:Arial, e:Arial, h:Arial, m:Arial, i:Arial, p:Arial, u:Arial, w:Arial, k:Arial, b:Arial, f:Arial, s:Arial, g:Arial, a:Arial, y:Arial, l:Arial, n:Arial, q:Arial, d:Arial, v:Arial]
    共享命中率          = 75.2%

    结论：一段 131 字的文本，只需要 26 个字形对象。
    真实渲染引擎中每个字形含轮廓点集与位图缓存（数 KB 到数十 KB），
    这里的节省量会被放大成千上万倍——这才是享元模式存在的理由。

    [Factory] 池未命中，新建享元：你:SimSun
    [Factory] 池未命中，新建享元：好:SimSun
    [Factory] 池未命中，新建享元：世:SimSun
    [Factory] 池未命中，新建享元：界:SimSun
    再渲染一段宋体中文：你好世界你好
    View 数 = 6，池大小从 26 增长到 30
    中文 6 个字里 '你' 和 '好' 各重复一次，所以只新建了 4 个享元
```

#### 三处值得注意的地方

**第一处：`[Factory] 池未命中` 一共打印了 26 + 4 = 30 次**，而池里最终也是 30 个享元。这个对应关系正确。

**第二处：`池中共享的字形` 的顺序完全不是插入顺序。**

插入顺序是 `t, h, e, q, u, i, ...`（按文章里首次出现的顺序），而输出是 `j, c, t, o, x, r, z, e, ...`。

原因见 §6.4。

**第三处：demo3 里 `池内容：[a:Arial, a:SimSun]` 保持了插入顺序**，而 demo4 里没有。

原因是 `HashMap` 在容量小时按哈希顺序排布，而 `String` 的 `hashCode` 是确定的，所以**同一份 JDK 下顺序稳定，但不保证跨版本稳定**。这是下一节的内容。

### 6.4 池内 key 的顺序问题：为什么不能当回归基准

**先说事实**：`HashMap.keySet()` 的迭代顺序由**哈希值和容量**共同决定，Java 规范**不保证任何顺序**。

| 场景 | 顺序是什么 |
|---|---|
| 插入顺序 | ❌ 不保证 |
| 字典序 | ❌ 不保证 |
| 哈希顺序 | ⚠️ 实际表现如此，但不属于契约 |
| 跨 JDK 版本一致 | ❌ 不保证 |

所以 `getPooledKeys()` 返回的 `Set` **只适合人眼看，不能用作自动化测试的断言**。

```java
// ❌ 这个测试可能在某些 JDK 上莫名失败
assertEquals(Set.of("a:Arial", "b:Arial"), factory.getPooledKeys());   // ← 这里其实没问题，Set 比较无视顺序

// ❌ 真正有问题的是这个
assertEquals("[a:Arial, b:Arial]", factory.getPooledKeys().toString());
```

**第一行没问题**，因为 `Set.equals` 按内容比较。**第二行有问题**，因为它把顺序固化了。

#### 如果想保证顺序怎么办

换成 `LinkedHashMap`：

```java
private final Map<String, Glyph> pool = new LinkedHashMap<>();
```

**代价**：每个节点多一个 `before`/`after` 指针（8 字节），内存略高，插入略慢（维护链表）。

**收益**：迭代顺序 = 插入顺序，文档输出可复现，调试更直观。

**本项目没改**，因为：

1. 享元池是"按 key 查询"的结构，顺序无业务意义；
2. `HashMap` 是默认选择，换 `LinkedHashMap` 需要额外解释；
3. **实测两次运行 SHA 一致**（`5818120EC6001FF7`），说明在当前 JDK 25 下顺序是稳定的，不影响本项目的输出可复现性。

但这**不等于顺序有保证**。如果换 JDK 版本后 SHA 变了，原因就在这里。

### 6.5 内存实验：`-Xmx64m` 下的生死线

§3.3 说过，Demo 里的 `CharacterGlyph` 只有 24 字节，共享了也省不了多少。**口头说"真实字形有几 KB"说服力不够**，所以写个探针用真实的 `-Xmx` 限制做实验。

`_probe/FlyweightMemoryProbe.java`，用同一个 `-Xmx64m` 跑两种模式：

```bash
java -Xmx64m -cp ... FlyweightMemoryProbe shared
java -Xmx64m -cp ... FlyweightMemoryProbe unshared
```

**共享模式**：

```text
模式 = 共享
目标 = 3000000 个字符
结果 = 通过：持有 3000000 个引用
maxMemory = 64 MB
```

**不共享模式**（同一份代码，只是把 `factory.getGlyph('a', "Arial")` 换成 `new CharacterGlyph('a', "Arial")`）：

```text
模式 = 不共享
目标 = 3000000 个字符
结果 = OOM：只建到第 2179071 个就内存耗尽
maxMemory = 64 MB
```

#### 这组数字说明什么

同样的 64MB 堆、同样的 300 万个字符：

- **共享**：全程无压力，因为只有 1 个 `CharacterGlyph` 对象；
- **不共享**：到 **217 万**个就撑不住了（OOM 的具体位置每次运行会略有不同，取决于 GC 时机）。

**为什么共享模式能过**：300 万个引用存在 `ArrayList` 里，占 `3000000 × 8B = 24MB`，加上 JVM 基线和扩容余量，64MB 够用。

**为什么不适合共享模式**：24MB 引用数组 + `300万 × 24B = 72MB` 的 `CharacterGlyph` 对象 = 96MB，超了 64MB。

#### 这个实验的真实意义

它证明了以下推理链：

```text
1. 单个 CharacterGlyph 占 24 字节
2. 300 万个 = 72MB，64MB 装不下
3. 如果真实字形是 8KB，那么只需 8000 个就超过 64MB
4. 而一篇文章有 10 万个字符，其中 26 种字形
5. 所以真实场景下享元是必需的，不是优化
```

**同时它也暴露了 Demo 的局限**：因为 Demo 的字形只有 24 字节，你必须在 300 万这个量级才能看到差别。而在真实渲染引擎里，**8000 个字符**就够了。

这个差距（300 万 vs 8000）本身就是享元价值的量化表达：**对象越重，享元越早生效。**

---

## 7. 扩展对比

本节做五个场景推演。每个场景都回答两个问题：**改哪里？为什么这样改？**

> ⚠️ 除了 §7.3 和 §7.5 的修法经过探针实测，其余为**设计推演**，本项目并未实际修改代码。这一点必须说清楚。

### 7.1 场景 A：新增「字号」作为内部状态

**需求**：同一个字要支持 12px 和 24px 两种字号，且两种字号的字形数据不同（位图渲染）。

**改动 1：给 `CharacterGlyph` 加字段**

```java
public record CharacterGlyph(char symbol, String font, int fontSize) implements Glyph {
    @Override
    public void render(int x, int y, String color) {
        System.out.printf("渲染 '%c' | 字体=%-6s | 字号=%2d | 位置=(%3d,%3d) | 颜色=%s%n",
                symbol, font, fontSize, x, y, color);
    }
}
```

**改动 2：工厂方法加参数，且 key 必须同步**

```java
public Glyph getGlyph(char symbol, String font, int fontSize) {
    requestCount++;
    String key = symbol + ":" + font + ":" + fontSize;   // ← 必须加上 fontSize
    return pool.computeIfAbsent(key, k -> {
        creationCount++;
        return new CharacterGlyph(symbol, font, fontSize);
    });
}
```

**改动 3：`TextDocument` 把字号传下去**

```java
public class TextDocument {
    private final int fontSize;   // 新增字段

    public List<CharacterView> layout(int startX, int startY, String color) {
        // ...
        Glyph glyph = factory.getGlyph(ch, font, fontSize);
        // ...
    }
}
```

**池的组合数变化**：

```text
原来：字符 × 字体              = 26 × 1 = 26
现在：字符 × 字体 × 字号        = 26 × 1 × 2 = 52
```

**关键点**：**改动 2 里的 `key` 拼接是隐藏的雷区**。如果只改了 `CharacterGlyph` 和 `getGlyph` 的参数，却忘了改 `key`：

```java
public Glyph getGlyph(char symbol, String font, int fontSize) {
    String key = symbol + ":" + font;   // ← 忘了加 fontSize
    return pool.computeIfAbsent(key, k -> new CharacterGlyph(symbol, font, fontSize));
}
```

后果：

```text
第一次 getGlyph('a', "Arial", 12) → 池里放 ('a',Arial,12)，key="a:Arial"
第二次 getGlyph('a', "Arial", 24) → key="a:Arial" 命中！返回 ('a',Arial,12)
```

**24px 的字符渲染成了 12px。** 压缩器不报错，运行时不打日志，只是最终页面上字号不对。

这正是 §4.6 决策 2 说的：**内部状态的集合，必须与 key 的构成一一对应。** 每次加内部状态字段，都必须同步改 key——这是享元维护中**最容易漏的一步**。

§7.5 的 record key 修法能从根上解决这个问题：字段和 key 合为一体，加字段就必然影响 key。

### 7.2 场景 B：如果把字号当外部状态

**需求同上，但换个切分**：字号放到 `render` 的参数里。

```java
public record CharacterGlyph(char symbol, String font) implements Glyph {
    @Override
    public void render(int x, int y, String color, int fontSize) {   // ← 字号变参数
        System.out.printf("渲染 '%c' | 字体=%-6s | 字号=%2d | 位置=(%3d,%3d)%n",
                symbol, font, fontSize, x, y);
    }
}
```

**能用吗？取决于渲染方式是矢量还是位图。**

#### 矢量渲染：字号可以做外部状态 ✅

矢量字形的本质是**一组贝塞尔曲线**，它与分辨率无关。渲染时：

```text
矢量路径 + 变换矩阵（缩放 12/24）→ 光栅化 → 位图
```

同一个 `'a'` 的轮廓，缩放 12 倍和 24 倍用的是**同一组控制点**。所以字号完全可以作为参数传入。

**这是更优的设计**：池里只需 26 个字元，而不是 52 个。

#### 位图渲染：字号必须是内部状态 ❌

位图字形的本质是**一张预渲染的像素图**。12px 的 `'a'` 是 12×12 个像素，24px 的 `'a'` 是 24×24 个像素——**它们是完全不同的数据**，无法通过缩放互相得到（缩放会模糊）。

所以：

```text
位图字体下，字号必须是内部状态
→ 池里必须有 26 × 2 = 52 个享元
```

#### 结论：这不是“对错”问题，是“实现约束”问题

| 渲染方式 | 字号归属 | 池大小 | 理由 |
|---|---|---|---|
| 矢量（轮廓） | 外部状态 | 26 | 一份轮廓可缩放 |
| 位图（预渲染） | 内部状态 | 52 | 不同尺寸是不同数据 |
| SDF / MSDF | 外部状态 | 26 | 有向距离场也可缩放 |

**这个例子说明一个重要的事**：内部/外部状态的划分**不是绝对的**，它取决于**底层数据的存储形式**。

所以 §3.2 的口诀「会不会因为使用场景不同而不同」还需要加一个限定：

> **「这个东西的底层数据会不会因为场景不同而不同？」**
>
> 会（位图字号）→ 内部；不会（矢量字号）→ 外部。

### 7.3 场景 C：改成 `ConcurrentHashMap`（已实测）

**改动只有一行**：

```java
// 修改前
import java.util.HashMap;
import java.util.Map;

private final Map<String, Glyph> pool = new HashMap<>();

// 修改后
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

private final ConcurrentMap<String, Glyph> pool = new ConcurrentHashMap<>();
```

**为什么一行就够**：因为 `computeIfAbsent` 的调用形式不用变，而 `ConcurrentHashMap` 重写了它，在对应桶上加锁，保证「查-建-放」原子。

**实测对比**（同样是 3000 轮 × 16 线程）：

| 实现 | 出现多实例的轮数 |
|---|---|
| `HashMap` + `computeIfAbsent` | **5 / 3000** |
| `ConcurrentHashMap` + `computeIfAbsent` | **0 / 3000** |

（`HashMap` 那个数字是波动的，不同次运行曾在 2–12 之间；`ConcurrentHashMap` 多次运行均为 0。）

#### 但改完之后还有两个问题

**问题 1：计数器仍然不是原子的**

```java
requestCount++;   // ← ConcurrentHashMap 管不了这个
creationCount++;  // ← 这个在 computeIfAbsent 的 lambda 里，受到桶锁保护，安全
```

`requestCount` 在方法第一行，不受任何锁保护。实测：500 轮 × 16 线程 × 200 次请求，**485 轮漏计**，最狠的一轮 3200 次请求只记到 540 次。

修法：

```java
private final AtomicInteger requestCount = new AtomicInteger(0);
private final AtomicInteger creationCount = new AtomicInteger(0);

public Glyph getGlyph(char symbol, String font) {
    requestCount.incrementAndGet();
    // ...
}

public int getSavedCount() {
    return requestCount.get() - creationCount.get();
}
```

**注意**：`creationCount` 如果在 lambda 里用 `AtomicInteger`，安全性不变（本来就受桶锁保护），但写法统一更好。

**问题 2：`savedCount` / 命中率会由于两个计数器不同步而出现负数或 >100%**

```text
极端情况：requestCount 漏计 80%，creationCount 准确
→ savedCount = 640 - 1000 = -360
→ 命中率 = -360 / 640 = -56.25%
```

**一个负数命中率会让人怀疑人生。** 这是并发计数器不同步的典型症状。要么两个都用原子类，要么在计算时做边界处理。

#### 为什么本项目不改

1. Demo 场景是单线程的，`Client.main` 串行执行；
2. 加 `ConcurrentHashMap` 会让代码多两行 import、多一层“为什么要用并发容器”的解释；
3. **更重要的是：保留这个缺陷，才能让读者看到“用对了 API 不等于用对了并发”。**

§5.3 已经指出很多人以为“换成 ConcurrentHashMap 就安全了”——这个误区非常普遍，值得用一个可复现的实验来打破。

### 7.4 场景 D：加容量上限与 LRU 淘汰

§2.6 的 P3 指出池只增不减。如果内部状态是无限的（用户上传字体），这就是内存泄漏。

**最小改动：用 `LinkedHashMap` + `removeEldestEntry`**

```java
public class GlyphFactory {

    private static final int MAX_POOL_SIZE = 5000;

    /**
     * accessOrder = true 时，每次 get/put 会把命中的节点移到链表尾部，
     * 于是链表头部就是「最久未使用」的那个。
     */
    private final Map<String, Glyph> pool = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Glyph> eldest) {
            return size() > MAX_POOL_SIZE;
        }
    };

    // 其余不变
}
```

**效果**：池超过 5000 时，自动删除最久未使用的那个。

#### 但这里有一个严峻的语义问题

**享元和 LRU 是天然矛盾的。**

| | 缓存 | 享元 |
|---|---|---|
| 淘汰后 | 下次重算，结果相同 | 下次重建，对象不同 |
| 后果 | 性能降一点 | **引用相等性被破坏** |

具体会出什么问题：

```java
Glyph g1 = factory.getGlyph('a', "Arial");
// ... 池满，g1 被 LRU 淘汰 ...
Glyph g2 = factory.getGlyph('a', "Arial");   // 重建
System.out.println(g1 == g2);   // false —— 共享语义被破坏了
```

对于本项目这种“用完即丢”的场景，没有影响。但如果有代码依赖“同一个字形必然同一个对象”（比如用 `Glyph` 做 `Map` 的 key），就会出错。

**所以加 LRU 之前必须先回答**：你的系统里有代码依赖享元的引用相等性吗？

- **有** → 不能加 LRU，改用别的限流手段（比如限制字体上传数量）；
- **没有** → 可以加，但要在文档里写清楚“共享不是永久保证”。

#### 更彻底的做法：永不淘汰，但限制输入

真实文字渲染引擎的做法通常是**不淘汰**，而是限制**内部状态的取值空间**：

```text
不限制：字符 65536 种 × 字体无限种
限制：  字符 65536 种 × 字体 32 种 = 209 万（有界）
```

在字体这一层做限制（只允许预置字体），比在池这一层做 LRU 更安全——**它从源头上保证池有界**。

### 7.5 场景 E：把字符串 key 换成 `record` key，修掉 null 碰撞

这是 §2.6 P1 的修法，也是 §4.6 决策 2、§7.1 的通用解。

**改动**：

```java
public class GlyphFactory {

    /** 内部状态的完整表达。加/删字段时 key 自动跟着变，编译器帮你看着。 */
    private record GlyphKey(char symbol, String font) { }

    private final Map<GlyphKey, Glyph> pool = new HashMap<>();

    public Glyph getGlyph(char symbol, String font) {
        requestCount++;
        GlyphKey key = new GlyphKey(symbol, font);   // ← 不再是字符串拼接
        return pool.computeIfAbsent(key, k -> {
            creationCount++;
            return new CharacterGlyph(symbol, font);
        });
    }

    public Set<GlyphKey> getPooledKeys() {
        return Collections.unmodifiableSet(pool.keySet());
    }
}
```

**验证探针**（`_probe/FlyweightRecordKeyProbe.java`，15 行 / 686 字节）：

```text
[对比] 字符串拼接 key vs record key
  字符串 key('a', null)   = "a:null"
  字符串 key('a', "null") = "a:null"
  字符串 key 相等？true   ← 碰撞
  record key('a', null)   = GlyphKey[symbol=a, font=null]  hashCode=3007
  record key('a', "null") = GlyphKey[symbol=a, font=null]  hashCode=3395910
  record key 相等？false   ← 能区分

[验证] 用 record key 建池，两个请求得到两个享元
  池大小 = 2（应为 2）
  'a'+null   → 字形#1(null 字体)
  'a'+"null" → 字形#2("null" 字体)

[额外的坑] record key 里如果放大对象，hashCode 每次都要重算
  100 万次 record key hashCode = 34 ms（h=909757568）
```

#### 为什么 `record` key 能自动防住这类 bug

| | 字符串 key | `record` key |
|---|---|---|
| `null` 与 `"null"` | **碰撞** | 区分（`Objects.equals(null, "null")` = false）|
| 分隔符注入（`"a:b"`） | **可能碰撞** | 不可能 |
| 加字段时是否要改 key | **必须手动改** | 自动，不改就编译不过 |
| 哈希计算 | 字符串的 `hashCode`（有缓存） | 每次重算（record 不缓存）|
| 调试可读性 | `"a:Arial"` 简洁 | `GlyphKey[symbol=a, font=Arial]` 冗长 |

**最后两行是代价**：

- `record` 的 `hashCode` **不缓存**，每次调用都要重算所有字段。如果内部状态里有一个 `byte[]` 轮廓数据，那 `hashCode` 会非常慢；
- `toString` 冗长，日志里看着芒。

**折中方案**：如果内部状态里有大对象，用「不可变的小 key 对象 + 缓存 hashCode」：

```java
public final class GlyphKey {
    private final char symbol;
    private final String font;
    private final int hash;          // ← 构造时算一次，之后直接返回

    public GlyphKey(char symbol, String font) {
        this.symbol = symbol;
        this.font = font;
        this.hash = Objects.hash(symbol, font);
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object o) { /* 略 */ }
}
```

但本项目的内部状态就是 `char + String`，`record` 的 34ms/100 万次完全够用。

### 7.6 与对象池、缓存的本质区别

三个东西都是“用 Map 存对象、下次直接取”，但目的完全不同。

| 维度 | 享元 Flyweight | 对象池 Object Pool | 缓存 Cache |
|---|---|---|---|
| **解决的问题** | 内存太多 | 创建成本太高 | 计算太慢 |
| **对象数量** | 1 个 key 对应 1 个对象 | 有限个（如 10 个连接）| 1 个 key 对应 1 个结果 |
| **借出后** | 仍在池中，**同时被多人用** | **独占**，归还前别人拿不到 | 仍在缓存中，只读 |
| **状态** | **不可变**（共享不改） | **可变**（用完要重置）| 不可变 |
| **能不能淘汰** | 理论上不能 | 可以（且必须回收）| 必须（TTL/LRU）|
| **典型例子** | 字形、颜色常量、`IntegerCache` | 数据库连接、线程池 | Spring `@Cacheable`、Redis |
| **并发要求** | 享元本身无状态，天然安全 | 池本身必须线程安全 | 缓存本身必须线程安全 |

**最容易混的是享元和对象池**，因为名字里都有“池”。区别很大：

```text
享元：10 个线程同时用同一个 'a' 字形 → ✅ 正确，字形不可变
对象池：10 个线程同时用同一条 DB 连接 → ❌ 灾难，事务全乱
```

**记住一句话**：

> 享元是“**共享**同一份数据”，对象池是“**轮流**使用同一个资源”。
> 前者允许多人同时拿，后者同一时刻只能一个人拿。

---

## 8. 优缺点与常见误区

### 8.1 优点（每条给证据）

#### 优点 1：内存占用下降一个数量级

证据：`-Xmx64m` 下的 300 万字符实验（§6.5）：

```text
共享   → 通过：持有 3000000 个引用
不共享 → OOM：只建到第 2179071 个就内存耗尽
```

更极端的真实数字：

```text
不用享元：100000 × 8KB = 781 MB
用享元：  2000 × 8KB + 100000 × 32B = 19 MB
```

#### 优点 2：对象创建次数大幅减少

证据：demo4 的 131 字文本：

```text
向工厂请求的次数 = 105
实际创建的享元数 = 26
节省的对象数     = 79
命中率          = 75.2%
```

**注意这里只省了 79 次创建**（Demo 里对象很轻）。但在真实场景下，每次创建都要**读字体文件、解压数据、光栅化位图**——省 79 次就是省 79 次磁盘 IO + CPU 运算。

**享元的次要价值是省 CPU，主要价值才是省内存。** 很多面试者只说得出“省内存”。

#### 优点 3：内部状态只有一份，修改时只改一处

这个好处容易被忽略。假设发现 `'a'` 的轮廓有 0.5 像素的偏差，要修正：

```text
不用享元：100000 个 'a' 对象，全都要改 → 不可能
用享元：  1 个 'a' 享元，改它一个 → 全局生效
```

**注意**：这需要享元**可变**才能享受这个好处，但享元必须**不可变**才能共享。两者矛盾。

所以这个“好处”实际上是这样的：**修正只能通过“清空池 + 重新从数据源加载”来实现**。

#### 优点 4：可观测、可断言

重构版新增的 5 个统计方法让享元的效果从“凭感觉”变成“可度量”：

```java
assertEquals(26, factory.getCreationCount());   // 字形种类数
assertEquals(105, factory.getRequestCount());  // 请求次数
assertSame(factory.getGlyph('a', "Arial"), factory.getGlyph('a', "Arial"));   // 真共享
```

生产环境还可以把命中率上报成监控指标。**命中率长期低于 30%，说明这个享元用错了。**

### 8.2 缺点

#### 缺点 1：把代码复杂度提高了一个层级

不用享元：1 个类（`Character`）。
用享元：4 个类（`Glyph` + `CharacterGlyph` + `GlyphFactory` + `CharacterView`）。

**代价是真实的**：新人要理解 4 个类的协作才能看懂代码。如果只有 5 个字符，这个代价完全不值得。

#### 缺点 2：内部/外部状态切错会引入静默错误

这是最大的风险，而且**编译不会报错**：

| 错法 | 后果 | 能否被测试抓到 |
|---|---|---|
| 坐标/颜色存进享元 | 共享失效，内存暴涨 | ✅ 能（内存监控）|
| key 漏掉一个内部状态 | **渲染错误** | ❌ 几乎不能 |
| 内部状态字段做成可变 | **数据污染** | ⚠️ 很难 |

**中间那行是最恐怖的**：key 漏字段时，所有对象都“看起来正常”，只是某些字符渲染成了别的字体。

#### 缺点 3：需要额外的池管理逻辑

即使最简单的地图式工厂，也要考虑：

- 池放在哪里（单例？静态？注入？）
- 什么时候创建（急加载 / 懒加载）
- 并发安全（`HashMap` 不行）
- 内存上界（无界 = 内存泄漏）

**这四项每一项都是一个真实的工程决策**，不是写完 `computeIfAbsent` 就完了。

#### 缺点 4：享元必须不可变，这限制了表达能力

如果需求是“这个字符要能改颜色”，那享元就无能为力——必须重建 `CharacterView`：

```java
// 想改颜色，只能重建（不共享的那部分）
views.set(i, new CharacterView(views.get(i).glyph, x, y, "red"));
```

`views.get(i).glyph` 是 `private` 的，拿不到。所以实际写法是：

```java
CharacterView old = views.get(i);
views.set(i, new CharacterView(factory.getGlyph(old.symbol(), old.font()), x, y, "red"));
// ↑ 需要 CharacterView 暴露 symbol/font，又破坏了封装
```

**享元让“修改”变麻烦了。** 这是不可变的代价。

### 8.3 已知遗留瑕疵（诚实清单）

**以下 7 条是重构版真实存在的问题，不是假想。每条都标注了是否实测。**

| # | 问题 | 实测证据 | 修法位置 |
|---|---|---|---|
| 1 | `pool` 用 `HashMap`，`computeIfAbsent` 非原子 | **5 / 3000 轮**出现多实例 | §7.3 |
| 2 | `requestCount++` / `creationCount++` 非原子 | **485 / 500 轮**漏计，最多漏 83% | §7.3 |
| 3 | key 用字符串拼接，`null` 与 `"null"` 碰撞 | 池大小 1（应为 2）| §7.5 |
| 4 | 池无淘汰、无容量上界 | 20000 汉字 → 池 20000 | §7.4 |
| 5 | 享元与工厂直接 `System.out`，不可测试 | 代码可见 | 见下 |
| 6 | `char` 不支持 BMP 外字符（emoji）| 代码可见 | 见下 |
| 7 | `Glyph.render` 返回 `void`，无法断言渲染结果 | 代码可见 | 见下 |

#### 关于第 5 条：库代码直接写控制台

```java
// CharacterGlyph.render 里
System.out.printf("    渲染 '%c' | 字体=%-6s | ...%n", symbol, font, x, y, color);

// GlyphFactory.getGlyph 里
System.out.println("    [Factory] 池未命中，新建享元：" + k);
```

**后果**：

- 在 Web 服务器里跑，所有渲染输出进了 `catalina.out`，**日志污染**；
- 单元测试无法断言“渲染了什么”，只能算验“有没有调”；
- 无法把渲染结果发给前端（前端的 canvas 需要的是记录数据，不是文字）。

**正确做法**（两种）：

```java
// 方案 A：返回结构化的渲染指令
public interface Glyph {
    RenderCommand render(int x, int y, String color);
}

// 方案 B：注入可替换的输出口
public interface Glyph {
    void render(int x, int y, String color, GlyphSink sink);
}
```

本项目选 `void` + `System.out` 的原因是**教学简化**：让输出直接可见、不需要额外的类。但这个代价必须说清楚。

#### 关于第 6 条：`char` 与 emoji

```java
CharacterGlyph g = new CharacterGlyph("😀".charAt(0), "Arial");
// ← 拿到的是代理对的第一个半字节，不是完整的 emoji
```

"😀" 的码点是 U+1F600，在 Java 里占**两个** `char`（`\uD83D` + `\uDE00`）。用 `char` 只能拿到前半，渲染出来是乱码。

**修法**：

```java
public record CharacterGlyph(int codePoint, String font) implements Glyph { }

// 遍历时用 codePoints()
text.codePoints().forEach(cp -> {
    Glyph g = factory.getGlyph(cp, font);
    // ...
});
```

本项目改成 `int codePoint` 只是多两行代码，但会遮蔽“字符 vs 码点”这个重要区别。**保留 `char` 是为了让这个限制本身成为一个教学点。**

#### 关于第 7 条：`render` 返回 `void`

如果 `render` 返回 `void`，单元测试只能这样验证：

```java
// ❌ 什么都没断言
new CharacterGlyph('a', "Arial").render(10, 20, "red");

// ⚠️ 只能捕获标准输出（脆弱、易碎）
ByteArrayOutputStream out = new ByteArrayOutputStream();
System.setOut(new PrintStream(out));
assertEquals("    渲染 'a' | ...", out.toString());
```

第二种写法一旦改了格式就全面崩，且对排版空格敏感。**这是 `void` 的直接代价。**

### 8.4 七个常见误区

#### 误区 1：以为“用了 Map 就是享元”

```java
// ❌ 这不是享元，只是一个缓存
private static final Map<String, Glyph> CACHE = new HashMap<>();
```

**真正的享元必须满足**：

1. 有一层**接口或抽象**，把外部状态表达在方法签名上；
2. 享元对象**不可变**；
3. 有**统一的工厂入口**。

只写个 Map 不叫享元，叫“用了个 Map”。

#### 误区 2：以为“享元就是为了省内存，所以越快越好”

享元的主要价值是**省内存**，但也省了创建成本（读文件、解压、光栅化）。

不过要小心：**享元增加了一次 Map 查找**。如果对象很轻、创建很快，享元反而更慢：

```text
直接 new：      ≈ 10 ns
Map 查找 + 可能 new：≈ 30-50 ns
```

**对象轻时享元是负优化。** 这是 §3.4 情况 1 说的事。

#### 误区 3：把可变对象放进了享元池

```java
// ❌ 灾难
public class Glyph {
    private char symbol;
    private String font;
    private int usageCount;   // ← 会变

    public void incrementUsage() { usageCount++; }
}
```

两个调用方共享同一个 `Glyph`，A 调用 `incrementUsage()`，B 看到的计数也变了。**所有共享用户互相干扰。**

**这是最严重的享元错误**，比 key 碰撞还严重——因为它的后果是**不可预测的数据竞争**。

#### 误区 4：以为“享元池在 `static` 字段里最方便”

```java
// ⚠️ 常见但不推荐
public class GlyphFactory {
    public static final Map<String, Glyph> POOL = new HashMap<>();
}
```

问题：**生命周期失控**。

- 测试 A 往里塞数据，测试 B 拿到脏池；
- 无法按场景销毁；
- 无法并行运行多个独立文档（它们的字形会互相串）。

**正确做法**：池属于工厂**实例**，工厂属于文档或应用的上下文。本项目就是这么做的。

#### 误区 5：忘了并发

见 §8.3 第 1、2 条。这是本项目保留的缺陷，也是大部分教学代码的通病。

#### 误区 6：以为“享元就是缓存，所以必须有 LRU”

见 §1.4、§7.4。**享元淘汰会破坏引用相等性，缓存淘汰不会。** 两者不能直接套用同一套策略。

#### 误区 7：用 `assertEquals` 测试共享

见 §2.6 P4。必须用 `assertSame`。再看一遍这个对比：

```text
x1 == x2 ?       false   ← 共享已失效
x1.equals(x2) ?  true    ← 但 assertEquals 会通过
```

**这是本项目最有价值的一条知识点。** 很多人写完享元，写了单元测试，测试全绿，但共享根本没生效。

### 8.5 与相似模式对比

| 模式 | 相同点 | 关键区别 | 何时选它 |
|---|---|---|---|
| **单例** | 都是“控制实例数量” | 单例只有 **1 个**对象；享元有 **N 个**（每个内部状态一个）| 全局配置、日志器 |
| **对象池** | 都有“池”，都复用 | 对象池借出时**独占**；享元**同时共享** | DB 连接、线程 |
| **缓存** | 都是 Map + 复用 | 缓存可过期，享元不可变 | 计算结果、HTTP 响应 |
| **原型** | 都能“避免重复创建” | 原型是 **clone 出新对象**，享元是 **复用同一对象** | 创建昂贵且可变的对象 |
| **外观** | 都希望“减少复杂度” | 外观是**隐藏子系统**；享元是**减少实例** | 子系统很复杂时 |
| **工厂方法** | 都有工厂 | 工厂方法只管“怎么创建”；享元工厂管“**只创建一次**” | 需要灵活替换实现 |

**享元与单例的区分最容易混**：

```text
单例：整个 JVM 里只有 1 个 GlyphFactory
享元：整个 JVM 里有 26 个 CharacterGlyph
```

它们经常**同时出现**：工厂是单例，享元有 26 个。但这是两件事。

---

## 9. 面试与自测

### 9.1 30 秒版

> 享元模式解决的是“**大量细粒度对象的内存开销**”问题。
>
> 核心做法是把对象的状态分成两类：
>
> - **内部状态**：不随使用场景变化的部分（如字符、字体），存在享元对象里，全局只有一份；
> - **外部状态**：每次使用都不同的部分（如坐标、颜色），存在调用方，通过方法参数传入。
>
> 然后由一个工厂维护一个享元池，相同内部状态只创建一次。
>
> 前提是：对象多、单个重、内部状态可枚举、享元不可变。

### 9.2 2 分钟版

> 享元是一个**为了内存而生的模式**，它不解决任何功能问题。
>
> 它的使用场景有一个很硬的门槛：**必须有大量对象，且单个对象很重，且对象之间有大量重复**。三个条件缺一个就不值得。
>
> 技术上分三步：
>
> 1. **切分状态**。把“会不会因场景不同而不同”作为判据，把会变的部分挪到方法参数上。这一步是整个模式最难的地方，切错了要么池爆炸、要么渲染错误。
> 2. **建池**。一个工厂 + 一个 `Map<内部状态, 享元>`，客户端只能通过工厂取对象。池必须放在工厂实例上，不能放静态字段，否则生命周期失控。
> 3. **加上下文对象**。挪出来的外部状态总得有地方放，这就是 `CharacterView` 这个角色——它每个位置一个，但只有几十字节。
>
> 有四个坑必须知道：
>
> - **key 必须覆盖全部内部状态**。漏一个字段就会返回错误对象，而且**编译、运行、日志都不会报错**，只在最终视觉上出错。
> - **享元必须不可变**。用 `record` 最稳，因为连反射都改不了它的字段。
> - **测试共享必须用 `assertSame`**。`record` 的 `equals` 是按值比较的，用 `assertEquals` 永远通过，即使共享完全失效。
> - **`HashMap` + `computeIfAbsent` 不是线程安全的**。必须换成 `ConcurrentHashMap`。
>
> 最后区分一下：享元不是缓存。享元是“本来就只该有一份”，缓存是“算过了先存着”。享元不该淘汰，缓存必须淘汰。

### 9.3 高频追问

#### Q1：内部状态和外部状态怎么分？

**一句话**：看“会不会因使用场景不同而不同”。会 → 外部，不会 → 内部。

**还要看底层数据**：

- 矢量字的字号可以外部化（轮廓可缩放）；
- 位图字的字号必须内部化（不同尺寸是不同数据）。

**验证方法**：如果把这个字段挪进享元后，池里的组合数会乘上一个很大的乘数，那它大概率不该进去。

#### Q2：为什么享元必须不可变？

因为**共享就意味着多个使用者**。如果 A 能改，B 看到的数据就变了。

更严重的是：这种变化不会报错、不会抛异常，只会让渲染结果莫名其妙地不对。**等你在几万个字符里发现“某几个字不对”时，几乎不可能定位。**

#### Q3：`HashMap` + `computeIfAbsent` 线程安全吗？

**不安全。** `computeIfAbsent` 的原子性保证来自 `ConcurrentHashMap` 的重写，不是 `Map` 接口的契约。

本项目实测：3000 轮 × 16 线程，`HashMap` 版有 **5 轮**出现重复实例；`ConcurrentHashMap` 版 **0 轮**。

#### Q4：为什么要工厂？客户端自己 `new` 不行吗？

不行。**共享不会自动发生。** `new` 一次就是一个新对象，跟池毫无关系。

只有经过工厂的请求才可能命中池。如果允许客户端 `new`，那池就形同虚设。

如果想从语言层面堵死，把具体享元做成工厂的私有内部类：

```java
public class GlyphFactory {
    private record CharacterGlyph(char symbol, String font) implements Glyph { }
}
```

#### Q5：享元跟缓存、对象池的区别？

| | 享元 | 对象池 | 缓存 |
|---|---|---|---|
| 目的 | 省内存 | 省创建成本 | 省计算时间 |
| 能否同时用 | **能** | 不能（独占）| 能 |
| 能否淘汰 | 理论上不能 | 能 | 必须 |
| 状态 | 不可变 | 可变 | 不可变 |

**名句**：享元是“共享同一份数据”，对象池是“轮流使用同一个资源”。

#### Q6：什么情况下不该用享元？

三个条件只要满足一个，就不该用：

1. **对象数量少**（几千个以下）；
2. **内部状态重复率低**（不同状态数 / 总数 > 80%）；
3. **对象需要频繁修改**（无法全部 `final`）。

#### Q7：享元池放静态字段还是实例字段？

**实例字段**。静态字段的问题：

- 生命周期失控（跟类加载器活）；
- 测试之间互相污染；
- 无法并行运行多个独立文档。

如果要全局单例，应该用 Spring 的 `@Component` 或手写的单例模式，**而不是裸静态字段**。

#### Q8：JDK 里有哪些享元？

| 例子 | 内部状态 | 池范围 |
|---|---|---|
| `Integer.valueOf(-128~127)` | 整数值 | 256 个 |
| `Long.valueOf(-128~127)` | 长整数 | 256 个 |
| `Boolean.TRUE/FALSE` | 布尔值 | 2 个 |
| `String` 常量池 | 字面量内容 | 无上限 |
| `Character.valueOf(0~127)` | 字符 | 128 个 |

**面试名题**：

```java
Integer a = 127, b = 127;
System.out.println(a == b);   // true（命中池）

Integer c = 128, d = 128;
System.out.println(c == d);   // false（超池，各自 new）
```

这个“127 对、128 错”的反直觉行为，本质上就是**享元池边界**没有被使用者感知。

### 9.4 自测题

#### 题 1：把 `CharacterGlyph` 改成普通类、字段去掉 `final`，会出什么事？

<details>
<summary>点开看答案</summary>

三个后果，从轻到重：

**后果 1：被继承篡改**

```java
class EvilGlyph extends CharacterGlyph {
    void corrupt() { this.symbol = 'X'; }   // 能编译（字段非 final）
}
```

**后果 2：共享的数据被任意改写**

```java
Glyph g1 = factory.getGlyph('a', "Arial");
((CharacterGlyph) g1).symbol = 'b';   // 如果暴露了 setter 或非 final 字段
Glyph g2 = factory.getGlyph('a', "Arial");   // g2 == g1，但现在渲染出 'b'
```

**后果 3：多线程下数据竞争**

一个线程改，另一个线程读，结果是未定义的（可能看到旧值、新值、甚至撕裂值）。

**根因**：享元的**共享**语义依赖**不可变**。去掉不可变，共享就从“优势”变成“灾难”。

**正确做法**：用 `record`，连反射都改不了（实测 `IllegalAccessException`）。

</details>

#### 题 2：为什么 `CharacterView` 不实现 `Glyph` 接口？

<details>
<summary>点开看答案</summary>

**因为 `CharacterView` 携带外部状态，它不能共享。**

如果让它实现 `Glyph`：

```java
// ❌ 灾难性设计
public class CharacterView implements Glyph {
    private final Glyph glyph;
    private final int x, y;
    private final String color;

    @Override
    public void render(int x, int y, String color) {
        // 参数传进来的 x/y/color 和字段里的冲突了，用哪个？
    }
}
```

两个问题：

1. **接口语义矛盾**：`Glyph` 的契约是“我只用参数里的外部状态”，而 `CharacterView` 自己有外部状态；
2. **方法签名冲突**：`render(int x, int y, String color)` 的参数与自己的字段同名，语义不清。

**GoF 原文把这类对象称为 `UnsharedConcreteFlyweight`**——它**不**属于享元池，也**不**实现享元接口。

**记住**：**享元接口属于“可共享的东西”，不属于“使用现场”。**

</details>

#### 题 3：`pool.computeIfAbsent` 在 `HashMap` 上并发安全吗？为什么？

<details>
<summary>点开看答案</summary>

**不安全。** 实测 5/3000 轮出现重复实例。

原因：`computeIfAbsent` 是 `Map` 接口的**默认方法**（`HashMap` 有优化实现，但仍然不加锁）。它的“查-建-放”三步之间没有任何同步。两个线程同时进入时，会各自执行 lambda、各自 `put`，后来者覆盖先来者。

**关键认识**：`computeIfAbsent` 的原子性保证**不在 `Map` 接口里，而在 `ConcurrentHashMap` 的实现里**。

```java
// ❌ HashMap：不原子
Map<String, Glyph> a = new HashMap<>();
a.computeIfAbsent(k, this::create);

// ✅ ConcurrentHashMap：原子
ConcurrentMap<String, Glyph> b = new ConcurrentHashMap<>();
b.computeIfAbsent(k, this::create);
```

**但即使换成 `ConcurrentHashMap`，如果 lambda 里有副作用（如 `creationCount++`），也要单独考虑。** 不过本项目的 `creationCount++` 在 lambda 里，受桶锁保护，是安全的；`requestCount++` 在方法头上，不受保护。

</details>

#### 题 4：要支持 emoji，需要改哪些地方？

<details>
<summary>点开看答案</summary>

**4 处**：

**第 1 处：`CharacterGlyph` 的 `symbol` 类型**

```java
// 改前
public record CharacterGlyph(char symbol, String font)

// 改后
public record CharacterGlyph(int codePoint, String font)
```

**第 2 处：`GlyphFactory.getGlyph` 的参数和 key**

```java
public Glyph getGlyph(int codePoint, String font)
```

**第 3 处：`TextDocument.layout` 的遍历方式**

```java
// 改前：遍历 char（代理对会被拆开）
for (char ch : text.toCharArray()) { ... }

// 改后：遍历码点
text.codePoints().forEach(cp -> { ... });
// 或者用 index 手动推进
for (int i = 0; i < text.length(); ) {
    int cp = text.codePointAt(i);
    i += Character.charCount(cp);
    // ...
}
```

**第 4 处：`render` 的输出方式**

```java
// %c 只能打 char
System.out.printf("渲染 '%c' ...", symbol);

// 改成 %s + Character.toString
System.out.printf("渲染 '%s' ...", Character.toString(codePoint));
```

**关键点**：`"😀".length()` 返回 **2**，`"😀".codePointCount(0, 2)` 返回 **1**。

这是 Unicode 代理对给 Java 留下的历史包袱。所有涉及“字符串按字符处理”的地方都要小心。

</details>

#### 题 5：一个 100 万字的文档，用到 2000 种字形，单个字形 8KB。不用享元和用享元各占多少内存？

<details>
<summary>点开看答案</summary>

**不用享元**：

```text
1000000 × (8KB + 32B)
= 1000000 × 8224 B
≈ 8.2 GB
```

**用享元**：

```text
字形：2000 × 8KB = 16384 KB ≈ 16 MB
视图：1000000 × 32B = 32000 KB ≈ 31.25 MB
合计：≈ 47 MB
```

**差 178 倍**。

注意两个细节：

1. **视图对象本身也占内存**，所以不能忽略——100 万个 `CharacterView` 也有 31MB。**享元只消除了重复的字形数据，不能消除视图本身。**
2. **视图占的比例随字符大小变化**。如果字形只有 100B，那 100 万个视图的 31MB 反而成了大头，享元就不值得了。

**这就是为什么 §3.3 说“单个对象占多少内存”是享元的前提。**

</details>

### 9.5 动手题

#### 动手题 1：把字号加为内部状态

按 §7.1 改 3 处（`CharacterGlyph` / `GlyphFactory` / `TextDocument`），然后验证：

- `factory.getGlyph('a', "Arial", 12) == factory.getGlyph('a', "Arial", 12)` → `true`
- `factory.getGlyph('a', "Arial", 12) == factory.getGlyph('a', "Arial", 24)` → `false`

**故意只改前两处、不改 key**，看会发生什么。你会得到 `false` 变成 `true` 的结果——亲身体验一次“key 漏字段”的静默错误。

#### 动手题 2：修掉并发问题并验证

1. 把 `pool` 改成 `ConcurrentHashMap`，`requestCount` / `creationCount` 改成 `AtomicInteger`；
2. 自己写一个和 `FlyweightProProbe` 事实 4 一样的压力测试（3000 轮 × 16 线程）；
3. 验证 `badRounds == 0`；
4. 再跑一次 `FlyweightNullKeyProbe` 事实 2，验证漏计变成 0/500。

**如果第 3 步不通过**，检查你是不是只换了 `ConcurrentHashMap` 但保留了 `containsKey` + `put`。那是 §5.3 说的典型陷阱。

#### 动手题 3：给工厂加 LRU，并证明它破坏了共享

1. 按 §7.4 改成 `LinkedHashMap(16, 0.75f, true)` + `removeEldestEntry`，上限设成 3；
2. 依次请求 4 个不同的字形；
3. 再请求第 1 个，验证返回的**不是**第一次那个对象（因为被淘汰了）；
4. 写一句话总结这个实验说明了什么。

**预期结论**：在享元池上套 LRU，会把“共享”降级成“缓存”——引用相等性不再成立。如果你的代码里有任何地方依赖 `g1 == g2`，就会出错。

---

## 10. 附录

### 10.1 文件清单

| 文件 | 行数 | 说明 |
|---|---|---|
| `Glyph.java` | 41 | 享元接口，方法签名定义了内/外部状态分界线 |
| `CharacterGlyph.java` | 47 | 具体享元，`record` 保证不可变 |
| `GlyphFactory.java` | 98 | 享元工厂，维护池 + 5 个统计方法 |
| `CharacterView.java` | 65 | 外部状态持有者（`UnsharedConcreteFlyweight`）|
| `TextDocument.java` | 79 | 调用方示范，把文本排成视图列表 |
| `Client.java` | 139 | 四个 demo，各验证一条性质 |
| `README.md` | 本文件 | 教学文档 |
| **合计（.java）** | **469** | —— |

### 10.2 探针清单

均存放于 `E:\codeFile\codeFather\design_patterns\_probe\`，**不属于交付代码**：

| 探针 | 目标包 | 行数 | 输出行数 | 取证内容 |
|---|---|---|---|---|
| `FlyweightOrigProbe.java` | 原项目包内 | 99 | 21 | 原项目 4 个缺陷 |
| `FlyweightProProbe.java` | 默认包 | 155 | 54 | 重构版 5 个设计点（含并发对比）|
| `FlyweightNullKeyProbe.java` | 默认包 | 78 | 13 | 重构版 2 个未修问题 |
| `FlyweightMemoryProbe.java` | 默认包 | 54 | 4 | `-Xmx64m` 内存对比 |
| `FlyweightRecordKeyProbe.java` | 默认包 | 48 | 15 | record key 修法验证 |

**编译命令**（`orig_flyweight` / `out2` 只是临时产物目录，`javac -d` 会自动创建，跑完可整目录删掉）：

```bash
# 原项目探针：FlyweightOrigProbe 在 structuraltype.FlyweightPattern 包里，
# 必须连同原项目的 5 个源文件一起编译，否则找不到同包类
javac -encoding UTF-8 -d _probe/orig_flyweight design_patterns/src/structuraltype/FlyweightPattern/*.java _probe/FlyweightOrigProbe.java

# 重构版探针：classpath 用 build.bat 的产物 out
javac -encoding UTF-8 -cp design_patterns_pro/out -d _probe/out2 _probe/FlyweightProProbe.java
```

> 上一版写的是 `-cp _probe/orig_flyweight -d _probe/orig_flyweight`，要求该目录里事先躺着原项目编译好的类（我本机当时有，换台机器就没有）。现在把原项目源码直接编进去，命令自身就能闭环；已实测 `javac` 退出码 0（7 个 class）、探针运行 21 行、内容与上表一致。

**运行命令**（必须用 `Start-Process` 或加引号，否则 `-Dstdout.encoding=UTF-8` 会被 shell 拆成两个参数）：

```bash
java -Dstdout.encoding=UTF-8 -cp "_probe/out2;design_patterns_pro/out" FlyweightProProbe
java -Xmx64m -Dstdout.encoding=UTF-8 -cp "_probe/out2;design_patterns_pro/out" FlyweightMemoryProbe shared
```

### 10.3 实测环境与数字汇编

**环境**：

| 项 | 值 |
|---|---|
| OS | Windows 26H2 |
| JDK | `F:\enCodingApps\JDK\jdk-25`（JDK 25）|
| 源项目 | `E:\codeFile\codeFather\design_patterns` |
| 目标项目 | `E:\codeFile\codeFather\design_patterns_pro` |
| 编译命令 | `javac -encoding UTF-8` |
| 运行命令 | `java -Dstdout.encoding=UTF-8` |

**主程序输出**：

| 项目 | 行数 | 字节数 | SHA-256 前 16 位 | 可复现 |
|---|---|---|---|---|
| 重构版 `Client` | 77 | 4474 | `5818120EC6001FF7` | ✅ 连续两次一致 |
| 原项目 `Client` | 5 | 352 | —— | ✅（未测哈希）|

**探针输出**：

| 探针 | 行数 | 字节数 |
|---|---|---|
| `FlyweightOrigProbe` | 21 | 1081 |
| `FlyweightProProbe` | 54 | 2609 |
| `FlyweightNullKeyProbe` | 13 | 681 |
| `FlyweightRecordKeyProbe` | 15 | 686 |
| `FlyweightMemoryProbe shared` | 4 | —— |
| `FlyweightMemoryProbe unshared` | 4 | —— |

**关键数字汇总**：

| 数字 | 含义 | 来源 |
|---|---|---|
| `131` | 测试文本字符数 | demo4 |
| `105` | 非空格字符数 = 请求次数 | demo4 |
| `26` | 英文字母享元数 | demo4 |
| `79` | 节省的对象数 | demo4 |
| `75.2%` | 共享命中率 | demo4 |
| `97.4%` | 探针 2 的命中率（1000 次请求 / 26 个）| `FlyweightProProbe` |
| `12 / 3000` | 原项目并发重复创建的轮数 | `FlyweightOrigProbe` |
| `5 / 3000` | 重构版 `HashMap` 版重复创建的轮数 | `FlyweightProProbe` |
| `0 / 3000` | `ConcurrentHashMap` 版重复创建的轮数 | `FlyweightProProbe` |
| `485 / 500` | 计数器漏计的轮数 | `FlyweightNullKeyProbe` |
| `540 / 3200` | 最狠一轮的计数（漏 83%）| `FlyweightNullKeyProbe` |
| `2179071` | `-Xmx64m` 下不共享模式的 OOM 位置 | `FlyweightMemoryProbe` |
| `3000000` | 共享模式下撑住的字符数 | `FlyweightMemoryProbe` |
| `3007` vs `3395910` | record key 的 hashCode（`null` vs `"null"`）| `FlyweightRecordKeyProbe` |

> ⚠️ **并发相关数字（12、5、485、540）每次运行都会波动**，这是并发测试的固有性质。
> 文档记录的是某一次具体运行的结果，不是固定值。

### 10.4 修订记录

| 版本 | 变更 |
|---|---|
| v1（原项目）| 5 个文件，5 个字符的 Client，能跑通但无法证明共享 |
| v2 | 新增 `TextDocument`（规模效应），新增 5 个统计方法，新增 4 个 demo |
| v2 | `Client` 从 27 行扩到 139 行，输出从 5 行扩到 77 行 |
| v2 | `CharacterGlyph` 的 `render` 从字符串拼接改成 `printf` 对齐输出 |
| v2 | `getPooledKeys()` 返回 `unmodifiableSet` 而非裸 `keySet()` |
| v2 | 文档重写：791 → 2482+ 行，补上内/外部状态推导、探针取证、诚实瑕疵清单 |
| v2 | **新增 5 个探针**，其中并发对比（5/3000 vs 0/3000）和内存对比（217 万 vs 300 万）是本次最重要的两份证据 |
| v2 | 诚实记录 **7 条已知遗留瑕疵**，而非假装修好 |

#### 本次文档重写修正的旧文档错误

| 旧文档说法 | 实际情况 | 证据 |
|---|---|---|
| （无并发讨论）| `HashMap` + `computeIfAbsent` 不安全 | 5/3000 轮实测 |
| （未提 key 碰撞）| `null` 与 `"null"` 碰撞 | 池大小 1 实测 |
| （未提测试方法）| 必须用 `assertSame` 而非 `assertEquals` | `record` 按值相等 |
| （未提内存实验）| `-Xmx64m` 下不共享 217 万就 OOM | 探针实测 |

---

> **一句话收尾**
>
> **享元模式不是“用个 Map 存对象”，而是“把对象切成共享的重部分和不共享的轻部分”。**
>
> 池是最简单的部分（一个 `ConcurrentHashMap` 加一行 `computeIfAbsent`）。
> 真正难的是切分：切对了省十倍内存，切错了渲染出错而且不发一声。
