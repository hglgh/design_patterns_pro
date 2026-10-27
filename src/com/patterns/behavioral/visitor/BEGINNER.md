# 访问者模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么把操作从元素类里搬出去」，
> 以及为什么新增元素类型反而很痛。
>
> 第二层：[README.md](README.md)（文档元素、双重分派、多种访问者）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**访问者就是把「对每种元素做什么」从元素类里搬到一个独立对象里；**
**元素只负责开门（`accept`），访问者负责进来做事（`visit`）。**

### 什么需求会让开发者想到它

一个内容平台有一份文档，由三种元素组成：

- 文本段；
- 图片；
- 表格。

同一份文档，不同部门要不同的操作：

- 运营要**导出**（文本、图片、表格）；
- 数据分析要**统计**（多少段、多少字、多少图、多少格）；
- 审核要**查敏感词**；
- 以后还可能加「字数统计」「生成目录」「转 PDF」。

第一版很自然：每个元素类都写 `export()`、`check()`。
两种元素、两个操作还能忍；三种元素、四种操作时，每个元素类都变成了工具箱。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldText {
    private final String content;

    void export() {
        System.out.println("[文本] " + content);
    }

    void check() {
        System.out.println("[文本] 检查敏感词");
    }
}

class OldImage {
    void export() { /* ... */ }
    void check() { System.out.println("[图片] 无需检查"); }
}

class OldTable {
    void export() { /* ... */ }
    void check() { System.out.println("[表格] 无需检查"); }
}
```

真实输出（节选）：

```text
[文本] 内部季度总结：内部营收增长 12%
[图片] 增长曲线（240KB）
[表格] 4 行 × 3 列
[文本] 检查敏感词
[图片] 无需检查
[表格] 无需检查
```

现在要加「字数统计」：`OldText`、`OldImage`、`OldTable` **三个类都要改**。
图片和表格虽然不需要统计，也得各加一个空方法。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldText  ──实现──▶ export、check、（以及未来的 wordCount、toPdf...）
OldImage ──实现──▶ export、check、...
OldTable ──实现──▶ export、check、...
```

每个元素类都依赖**所有操作**。
元素类型是 3 个，操作是 N 个，代码量是 3×N；
更糟的是，元素类的职责从「表示文档节点」变成了「文档工具箱」。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 新增「字数统计」 | 三个元素类都加方法 | 图片/表格被迫加空方法 |
| 新增「导出 PDF」 | 三个元素类都加方法 | 每加一种格式改一遍所有元素 |
| 修改敏感词规则 | 改 `OldText.check()` | 操作逻辑和元素数据绑在一起 |
| 新增「视频」元素 | 新类要实现所有操作 | 操作越多，新元素越难加 |
| 单独测试「文本导出」 | 要构造 `OldText`，调用方法 | 操作无法脱离元素独立测试 |

### 3.3 为什么加一个 `if` 不够

因为操作不是「多一种情况」，而是**一组会持续增加的行为**。
把 `export` 写成 `if (type == TEXT) ...` 只是把三个类里的重复搬到一个方法里，
新增元素类型时仍然要改这个 `if`，而且所有操作都要各写一份 `if`。

---

## 4. 从问题推导出设计

### 第一步：元素只留一个「开门」方法

```java
interface DocumentElement {
    void accept(Visitor visitor);
}

class TextElement implements DocumentElement {
    public void accept(Visitor visitor) {
        visitor.visit(this);          // 把自己交给访问者
    }
}
```

- **为什么需要 `accept`？** 元素要能把「自己」交给访问者，让访问者按真实类型处理。
- **为什么不直接在元素里写 `export()`？** 那样操作又回到元素类里，新增操作要改元素。
- **`visitor.visit(this)` 里的 `this` 是什么类型？** 编译期是 `TextElement`，
  所以会选中 `visit(TextElement)` 这个重载。

### 第二步：访问者接口为每种元素提供一个 `visit` 重载

```java
interface Visitor {
    void visit(TextElement element);
    void visit(ImageElement element);
    void visit(TableElement element);
}
```

- **为什么是重载，不是 `visit(DocumentElement)`？** 访问者需要拿到具体类型的数据
  （文本内容、图片大小、表格行列），参数必须具体。
- **为什么访问者要知道所有元素类型？** 这是访问者的代价：元素类型变化时，
  所有访问者都要改。它假设「元素类型稳定，操作常变」。

### 第三步：每种操作一个访问者

```java
class ExportVisitor implements Visitor {
    public void visit(TextElement element) {
        System.out.println("[文本] " + element.content());
    }

    public void visit(ImageElement element) {
        System.out.println("[图片] " + element.name() + "（" + element.sizeKb() + "KB）");
    }

    public void visit(TableElement element) {
        System.out.println("[表格] " + element.rows() + " 行 × " + element.cols() + " 列");
    }
}
```

`StatsVisitor` 累加计数，`SensitiveWordVisitor` 查关键词，`WordCountVisitor` 只统计文本字数。
**每个访问者只关心一种操作，元素类一行不改。**

### 第四步：遍历文档，逐个 `accept`

```java
private static void visitAll(List<DocumentElement> document, Visitor visitor) {
    for (DocumentElement element : document) {
        element.accept(visitor);
    }
}
```

```java
visitAll(document, new ExportVisitor());
visitAll(document, new StatsVisitor());
visitAll(document, new SensitiveWordVisitor("内部"));
visitAll(document, new WordCountVisitor());
```

### 最终对象关系

```text
DocumentElement（元素接口）
  ▲            ▲              ▲
TextElement  ImageElement   TableElement
  │ accept(visitor) → visitor.visit(this)
  ▼
Visitor（访问者接口）
  ▲              ▲                 ▲
ExportVisitor  StatsVisitor   SensitiveWordVisitor / WordCountVisitor
```

---

## 5. 用具体数据走一遍

文档内容：

```java
document.add(new TextElement("内部季度总结：内部营收增长 12%"));
document.add(new ImageElement("增长曲线", 240));
document.add(new TableElement(4, 3));
```

### 5.1 导出

```text
① element = TextElement → accept(exportVisitor)
   → exportVisitor.visit(TextElement) → [文本] 内部季度总结...
② element = ImageElement → accept(exportVisitor)
   → exportVisitor.visit(ImageElement) → [图片] 增长曲线（240KB）
③ element = TableElement → accept(exportVisitor)
   → exportVisitor.visit(TableElement) → [表格] 4 行 × 3 列
```

真实输出：

```text
—— 导出 ——
  [文本] 内部季度总结：内部营收增长 12%
  [图片] 增长曲线（240KB）
  [表格] 4 行 × 3 列
```

### 5.2 统计

`StatsVisitor` 分别累加：

```text
文本：1 段，content.length() = 17 字
图片：1 张，240KB
表格：1 个，4 × 3 = 12 格
```

真实输出：

```text
文本 1 段 / 17 字；图片 1 张 / 240KB；表格 1 个 / 12 格
```

### 5.3 敏感词

文本是「内部季度总结：内部营收增长 12%」，`indexOf("内部")` 命中两次：

```text
第一次 index=0 → hits=1，从 index+2 继续
第二次 index=7 → hits=2，从 index+9 继续
没有更多 → 返回
```

真实输出：

```text
文本命中 2 次
```

### 5.4 双重分派到底分派了什么

```java
element.accept(visitor);
```

1. **第一重**：`element` 的运行时类型决定调用哪个 `accept`
   （`TextElement.accept` / `ImageElement.accept` / `TableElement.accept`）。
2. **第二重**：在 `TextElement.accept` 里执行 `visitor.visit(this)`，
   `this` 的静态类型是 `TextElement`，所以编译期选中 `visit(TextElement)`；
   `visitor` 的运行时类型（`ExportVisitor`/`StatsVisitor`）决定执行哪个实现。

**元素类型和访问者类型各分派一次，所以叫双重分派。**

如果写成：

```java
visitor.visit(element);   // element 的静态类型是 DocumentElement
```

编译期找不到 `visit(DocumentElement)`，只能退回 `instanceof` 判断。
`accept` 的存在，就是为了让「元素的具体类型」在编译期被确定下来。

### 5.5 最容易写错的两处

1. **`accept` 里写成 `visitor.visit((DocumentElement) this)`**：重载解析会选不中具体类型，
   编译失败或退化成错误的重载。
2. **元素数据全部私有且没有 getter**：访问者拿不到内容，只能在元素里加 getter；
   这会稍微放松封装——这是访问者的成本，不是 bug。

---

## 6. 改造前后对比

### 新增「字数统计」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `OldText`、`OldImage`、`OldTable` | 新增 `WordCountVisitor` |
| 要改的旧代码 | 三个类各加一个方法 | 元素类 0 修改 |
| 图片/表格 | 被迫加空方法 | 在访问者里写空实现（仍然要写，但集中在一处） |
| 测试 | 操作和元素绑在一起 | 访问者可单独测试 |

### 但调用方仍然要改

- 要创建访问者并传给 `accept`；
- 新增访问者要实现**所有** `visit` 重载，哪怕某些元素不关心；
- 遍历逻辑（`visitAll`）要自己写，模式不提供遍历。

### 引入模式新增的成本

- **新增元素类型很痛**：`Visitor` 接口加一个重载，所有访问者都要实现；
- 元素要暴露 getter，封装比原来松；
- 访问者可能积累状态（统计计数），要注意复用/线程安全；
- 操作很少、元素类型经常变时，访问者方向是反的，不如把操作留在元素里。

> 访问者的适用方向必须记牢：**元素类型稳定、操作经常增加**。
> 反过来（元素经常加、操作很少），它会让你改到崩溃。

---

## 7. 适用边界

### 适合用

- 元素类型**稳定**（文本/图片/表格），操作**经常增加**（导出、统计、检查、转换）；
- 希望把「数据结构」和「操作」分开，让元素类保持单纯；
- 需要对一组异质元素执行同一种操作，且操作需要访问具体类型的数据；
- 操作可以独立测试、独立发布。

### 不适合用

- 元素类型经常增加——每加一种，所有访问者都要改；
- 只有一两种操作——直接写在元素类里更短；
- 操作需要修改元素状态，或元素之间有复杂协作——访问者不适合承担。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 迭代器 | 迭代器负责「按顺序拿出元素」；访问者负责「对拿出的元素做什么」 |
| 策略 | 策略是「选一个算法处理同一类数据」；访问者是「对多种元素类型分别处理」 |
| 组合 | 组合关注「叶子/容器统一结构」；访问者常配合组合，对树中不同节点做操作 |

---

## 8. 小练习

### 练习 1（场景判断）

报表系统有三种固定图表（折线、柱状、饼图），要支持导出 PNG、SVG、PDF，未来还会加 Excel。
要不要用访问者？

**答案**：适合。图表类型稳定、导出格式经常增加，正好是「元素稳定、操作常变」。
每种格式一个访问者，新增 Excel 只加一个类。
但如果图表类型本身经常增加（每周加一种新图），访问者会让所有导出器都要改，
这时把导出逻辑留在图表里可能更稳。

### 练习 2（解释设计原因）

为什么一定要 `element.accept(visitor)`，不能直接 `visitor.visit(element)`？
去掉 `accept` 会怎样？

**答案**：`visitor.visit(element)` 中 `element` 的静态类型是 `DocumentElement`，
编译器选不中 `visit(TextElement)` 等具体重载；要么加一个 `visit(DocumentElement)`，
要么在访问者里写 `instanceof` 分支。
`accept` 让元素在自己的类里调用 `visit(this)`，`this` 的静态类型是具体元素，
重载解析才能命中正确方法。**`accept` 是双重分派的第一重。**

### 练习 3（执行过程）

文档里依次是 `TextElement`、`ImageElement`、`TableElement`，
传入 `StatsVisitor`。三个 `visit` 分别累加了什么？最终报告是多少？

**答案**：`visit(TextElement)` 让 `textCount=1`、`charCount=17`；
`visit(ImageElement)` 让 `imageCount=1`、`imageKb=240`；
`visit(TableElement)` 让 `tableCount=1`、`cellCount=12`。
报告：文本 1 段 / 17 字；图片 1 张 / 240KB；表格 1 个 / 12 格。

### 练习 4（扩展推导）

新增 `VideoElement`（时长、分辨率），需要改哪些地方？
这和新增「字数统计」的改动方向有什么不同？

**答案**：要改 `DocumentElement` 的实现（新增类）、`Visitor` 接口（加
`visit(VideoElement)`），以及**所有已有访问者**（Export/Stats/Sensitive/WordCount）
都要实现新重载。而新增「字数统计」只需加一个访问者，元素类不改。
这就是访问者的取舍：**操作方向容易扩展，元素方向困难。**

---

## 读完后应能回答的 5 个问题

1. 访问者把什么从元素类里搬走了？为什么这能支持新增操作？
2. `accept` 为什么必须存在？它在双重分派里起什么作用？
3. 新增元素类型和新增操作，改动范围为什么正好相反？
4. 访问者需要元素提供 getter，这对封装有什么影响？
5. 什么情况下应该把操作留在元素类里，而不是用访问者？

---

## 下一步

- 深度文档：[README.md](README.md)（文档元素、双重分派、访问者扩展）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
