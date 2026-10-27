# 原型模式：通俗入门

> **第一层「入门」材料**：重点不是 `clone()`，而是「复制一个现成对象」为什么比重新 new 更可靠。
> `Cloneable`、浅拷贝/深拷贝、注册表等工程细节见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**原型就是：不从头造，而是拿一个现成的样板复制一份；复制规则由样板自己负责，
而不是让每个使用它的人自己抄字段。**

### 什么需求会让开发者想到它

一个周报系统有「周报模板」：标题、章节列表、字号样式。
每个员工写周报时，都从模板复制一份草稿，再改标题和内容。

第一版这样复制：

```java
OldDocument draft = new OldDocument(template.title, template.sections, template.style);
```

看起来没问题，直到有人往草稿里加了一个章节，**模板也跟着多了一章**；
有人把草稿字号改成 20，**模板的字号也变成 20**。

这不是「复制代码写错了」那么简单，而是**复制规则散落在每个调用方**。
原型模式要解决的是：**复制这件事由对象自己负责，调用方只说「给我一份」。**

---

## 2. 先看不使用原型的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class OldDocument {
    String title;
    List<String> sections;
    OldStyle style;

    OldDocument(String title, List<String> sections, OldStyle style) { ... }
}
```

调用方手动「拷贝」：

```java
OldDocument draft = new OldDocument(template.title, template.sections, template.style);
draft.sections.add("风险与求助");
draft.style.fontSize = 20;
```

真实输出：

```text
  模板章节：[本周完成, 下周计划, 风险与求助]
  草稿章节：[本周完成, 下周计划, 风险与求助]
  模板字号：20，草稿字号：20
  ↑ 改草稿把模板也改了：sections 和 style 指向同一份对象。
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
调用方 ──逐个字段读取──▶ template
       ──把引用直接塞给──▶ draft
```

`draft.sections` 和 `template.sections` 指向同一个 `ArrayList`；
`draft.style` 和 `template.style` 指向同一个 `Style`。

### 3.2 问题出在哪

```java
new OldDocument(template.title, template.sections, template.style)
```

这是**浅拷贝**：只复制了引用，没有复制引用指向的对象。
对于 `String`（不可变）没问题；对于 `List`、`Style`（可变）就是灾难。

### 3.3 新增字段会怎样

| 新需求 | 改造前 | 结果 |
|---|---|---|
| 文档加「作者」字段 | 所有复制代码加一个参数 | 漏一处就丢字段 |
| 加一个可变嵌套对象 | 拷贝代码要记得深拷贝 | 漏一处就共享 |
| 换一种复制方式 | 改所有调用方 | 复制规则无法复用 |

**为什么不能只加个 `clone()`？** 可以，但 `Object.clone()` 默认也是浅拷贝，
而且 `Cloneable` 的用法容易被误解。**关键是复制规则要归对象自己**，
不一定非得用 `clone()`。

---

## 4. 从问题推导出设计

### 第 1 步：让可变部分自己会复制

```java
class Style {
    Style copy() {
        return new Style(fontSize);
    }
}
```

**为什么？** `Style` 最清楚自己有哪些字段需要复制。
**不加会怎样？** 每个使用 `Style` 的地方都要自己 new 一遍，规则重复。

### 第 2 步：让文档提供 `copy()`

```java
Document copy() {
    List<String> sectionsCopy = new ArrayList<>(sections);
    Style styleCopy = style.copy();
    return new Document(title, sectionsCopy, styleCopy);
}
```

**为什么需要这个方法？** 调用方只需要说「复制」，不需要知道 Document 有几个字段、
哪些要深拷贝。**不加会怎样？** 复制逻辑继续散落在每个调用方，新增字段时到处改。

### 第 3 步：复制时区分「可变」和「不可变」

- `title` 是 `String`，不可变，直接共享引用安全；
- `sections` 是 `ArrayList`，可变，必须新建一个列表；
- `style` 是可变对象，必须调用 `style.copy()`。

**为什么不能全都直接赋值？** 可变对象会被两个文档共享，改一个影响另一个。
**为什么不能全都深拷贝？** 不可变对象深拷贝没有意义，只会增加成本。

最终关系：

```text
template ──copy()──▶ 新 Document
                       ├── title    ──共享（String 不可变）
                       ├── sections ──新 ArrayList（元素是 String，可共享）
                       └── style    ──新 Style（可变对象）
```

真实输出（节选）：

```text
  模板：Document{title=周报模板, sections=[本周完成, 下周计划], fontSize=12}
  草稿：Document{title=第 42 周周报, sections=[本周完成, 下周计划, 风险与求助], fontSize=20}
  ↑ 草稿怎么改，模板都不动。
```

---

## 5. 用执行过程理解代码

```java
Document template = new Document("周报模板", new ArrayList<>(List.of("本周完成", "下周计划")), new Style(12));
Document draft = template.copy();
draft.addSection("风险与求助");
draft.getStyle().setFontSize(20);
```

1. 模板持有列表 A 和样式 S；
2. `copy()` 创建新列表 B（内容从 A 复制）和新样式 S2（字号从 S 复制）；
3. 草稿持有 B 和 S2；
4. `draft.addSection` 改的是 B，A 不变；
5. `draft.getStyle().setFontSize(20)` 改的是 S2，S 仍是 12。

**最容易写错的一行**：

```java
List<String> sectionsCopy = new ArrayList<>(sections);
```

`new ArrayList<>(sections)` 是**复制列表本身**，不是复制元素。
这里元素是 `String`（不可变），所以够用；如果元素是可变对象，还需要逐个复制元素。

---

## 6. 改造前后对比

### 6.1 新增「作者」字段

| | 改造前 | 改造后 |
|---|---|---|
| 目标类 | 加字段 | 加字段 |
| 所有复制点 | 每个调用方补参数 | **0 处**（copy() 内部处理） |
| 深拷贝规则 | 每个调用方自己记得 | 集中在 copy() |
| 遗漏风险 | 高 | 低 |

### 6.2 代价

- 每个可复制类都要写 `copy()`，字段多时是体力活；
- 深拷贝遇到大对象图、循环引用时实现复杂、成本高；
- 复制规则写错（该深拷的共享了）不会立刻报错，可能在很久之后才暴露；
- 对象如果本来可以设计成不可变，直接共享最安全，不需要原型。

### 6.3 什么时候不需要

- 对象很小、字段很少，直接 `new` 并赋值更清楚；
- 对象不可变，复制没有意义（直接共享）；
- 只需要复制一层，没有嵌套可变对象——手动拷贝也能接受。

---

## 7. 适用边界

### 适合

- 创建成本高（读文件、查数据库、复杂计算），而复制更快；
- 需要保留某个「样板」的完整状态，再基于它做变体；
- 对象类型要到运行时才知道（例如从注册表里取一个原型再复制）；
- 希望把复制规则封装在对象内部，避免调用方漏字段。

### 不适合

- 对象很小、构造很简单；
- 对象图复杂且有循环引用，深拷贝难以维护；
- 对象可以设计成不可变——共享比复制更好。

### 容易混淆

| 对比 | 区别 |
|---|---|
| **new** | 从类和参数重新构造；原型从**已有对象的状态**复制 |
| **工厂** | 关注「创建哪个类型」；原型关注「复制哪一份现成状态」 |
| **备忘录** | 备忘录保存状态用于**恢复**；原型复制对象用于**产生新对象** |
| **`Cloneable`** | 只是 JDK 提供的一种实现手段；原型模式不强制用它，拷贝构造器往往更清晰 |

> 常见误区：以为「实现了 `Cloneable` 就是原型模式」。
> 原型的关键是**由对象自己负责复制**，而不是某个接口。

---

## 8. 小练习

### 练习 1（场景判断）

一个 `User` 对象只有 `id`、`name` 两个 String 字段，创建它只需要一次 `new`。
需要原型模式吗？

**参考答案**：不需要。对象小、创建便宜、没有嵌套可变状态，直接 new 更清楚。
原型适合「创建成本高」或「复制规则复杂」的场景。

### 练习 2（解释设计原因）

为什么 `copy()` 要放在 `Document` 里，而不是让调用方自己 new 再逐个赋值？

**参考答案**：因为只有 `Document` 自己知道哪些字段需要深拷贝、哪些可以共享。
放在调用方，每加一个字段就要改所有复制点，漏一个就出隐性 bug；
放在类内部，复制规则只有一份，新增字段时也只改一处。

### 练习 3（找 bug）

下面的 `copy()` 有什么问题？

```java
Document copy() {
    return new Document(title, sections, style);
}
```

**参考答案**：这是浅拷贝，`sections` 和 `style` 仍与原件共享。
改副本的章节或字号会同时改掉原件。应该新建列表并调用 `style.copy()`。

---

## 9. 读完之后

你应该能回答：

1. 原型为什么存在？——把复制规则收回到对象内部，避免调用方漏字段/浅拷贝。
2. 原始实现的问题？——手动拷贝、共享可变对象、模板被改坏。
3. 每个角色为什么存在？——`copy()` 提供统一复制入口，可变子对象各自负责复制。
4. 怎么协作？——调用方拿模板 → 调 copy → 新对象持有独立的可变状态。
5. 新场景要不要用？——看创建成本、复制规则复杂度和对象是否可变。

继续阅读：

- [深度文档](README.md)：`Cloneable`、深浅拷贝、原型注册表与工程取舍；
- [Client.java](Client.java)：工程版入口；
- [建造者入门](../builder/BEGINNER.md)：从零分步构建一个对象；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
