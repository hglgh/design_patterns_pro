# 组合模式：通俗入门

> **第一层「入门」材料**：重点是「叶子和容器为什么必须长得一样」。
> 树形结构的工程版、递归边界和安全模式见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**组合就是：把「一个东西」和「一组东西」当成同一种东西来用，
这样客户端不用关心自己拿到的是单个文件还是一个文件夹。**

### 什么需求会让开发者想到它

一个文件系统要统计大小、打印目录树。文件夹里既能放文件，也能放子文件夹，
后来还加了「快捷方式」。

第一版用 `List<Object>` 装子项，每个方法里写：

```java
if (item instanceof File) { ... }
else if (item instanceof Folder) { ... }
```

加「快捷方式」时，`size()` 和 `print()` 都得各加一个 `else if`，
漏一个就出现「文件明明在，统计/打印里却不见了」。

组合模式要解决的是：**让文件和文件夹实现同一个接口，递归由多态完成，
而不是由每个方法里的 instanceof 完成。**

---

## 2. 先看不使用组合的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class OldFolder {
    final List<Object> items = new ArrayList<>();

    int sizeKb() {
        int total = 0;
        for (Object item : items) {
            if (item instanceof OldFile file) {
                total += file.sizeKb;
            } else if (item instanceof OldFolder folder) {
                total += folder.sizeKb();
            }
            // OldShortcut 没有被处理
        }
        return total;
    }

    void print(String indent) {
        // 同样的一串 instanceof
    }
}
```

真实输出：

```text
根目录/
  订单.txt (120KB)
  报表/
    日报.csv (80KB)
    月报.csv (200KB)
  说明.md (5KB)
  总大小：405KB
  ↑ 快捷方式既没被统计，也没被打印：两个方法都不认识它。
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldFolder ──持有 List<Object>──▶ 文件 / 文件夹 / 快捷方式（都是 Object）
客户端   ──拿到 Object──────────▶ 强转才能用
```

容器和叶子没有共同类型，客户端和容器都靠 `instanceof` 猜。

### 3.2 问题出在哪

- 每个遍历方法都要枚举所有节点类型；
- 新增节点类型要改**所有**遍历方法；
- `Object` 让类型错误推迟到运行时；
- 容器无法递归调用「子节点自己的 size」，只能自己判断再强转。

### 3.3 为什么加个 `else if` 不够

加一个分支只能让当前方法认识新类型。下一个遍历方法（比如 `countFiles`、`export`）
还要再加一遍。**问题不是分支数量，而是「容器不知道子节点能做什么」。**

---

## 4. 从问题推导出设计

### 第 1 步：定义叶子和容器的共同接口

```java
interface Node {
    int sizeKb();
    void print(String indent);
}
```

**为什么需要它？** 容器只需要知道「子节点能算大小、能打印」，不需要知道它具体是什么。
**不加会怎样？** 容器只能持有 `Object`，继续 instanceof。

### 第 2 步：文件实现接口

```java
class FileNode implements Node {
    public int sizeKb() { return sizeKb; }
    public void print(String indent) { ... }
}
```

**为什么文件也要实现？** 因为它要能被放进 `List<Node>`，和文件夹一起被统一处理。

### 第 3 步：文件夹也实现接口，并持有 `List<Node>`

```java
class FolderNode implements Node {
    private final List<Node> children = new ArrayList<>();

    public int sizeKb() {
        int total = 0;
        for (Node child : children) {
            total += child.sizeKb();     // 递归由多态完成
        }
        return total;
    }
}
```

**为什么文件夹要持有 `List<Node>` 而不是 `List<FileNode>`？** 因为子节点可能是文件，
也可能是文件夹，还可能是未来的快捷方式。**不加会怎样？** 又回到按类型分开处理。

### 第 4 步：新增节点类型只加一个类

```java
class ShortcutNode implements Node {
    public int sizeKb() { return 1; }
    public void print(String indent) { ... }
}
```

**为什么不用改 `FolderNode`？** 因为它只调 `Node` 接口，新类型自然能被递归。

最终关系：

```text
        Node（size/print）
        ▲                 ▲
   FileNode          FolderNode ──持有 List<Node>──▶ 任意 Node
                          │
                          └── 递归调用 child.sizeKb()/print()
```

真实输出（节选）：

```text
根目录/
  订单.txt (120KB)
  报表/
    日报.csv (80KB)
    月报.csv (200KB)
  说明.md (5KB)
  报表快捷方式 -> 快捷方式 (1KB)
  总大小：406KB
```

---

## 5. 用执行过程理解代码

`root.sizeKb()`：

1. `FolderNode.sizeKb()` 遍历 `children`；
2. 对每个 `child` 调 `child.sizeKb()`；
3. 文件返回自己的大小；文件夹继续递归；快捷方式返回 1；
4. 结果逐层相加。

`root.print("")` 类似，只是多传一个缩进字符串，容器负责给子节点加两个空格。

**最容易写错的地方**：

```java
for (Node child : children) {
    total += child.sizeKb();
}
```

这里不需要 `instanceof`，也不能写成 `children.stream().mapToInt(FileNode::sizeKb)`——
容器的职责是「对所有子节点做同一件事」，具体怎么做由子节点自己决定。

---

## 6. 改造前后对比

### 6.1 新增快捷方式

| | 改造前 | 改造后 |
|---|---|---|
| 新增类 | `OldShortcut` | `ShortcutNode` |
| 修改统计方法 | `sizeKb()` 加分支 | 0 |
| 修改打印方法 | `print()` 加分支 | 0 |
| 客户端 | 拿到 Object，强转 | 拿到 Node，直接调 |
| 漏改风险 | 每个遍历方法都可能漏 | 结构上不会漏 |

### 6.2 代价

- 每个节点都要实现完整接口，叶子类可能有很多空方法/无意义方法；
- 接口一旦增加操作，所有节点都要改（例如加 `exportJson()`）；
- 递归深度大时可能栈溢出，需要改成迭代；
- 简单的一层列表不需要组合，只会增加抽象。

### 6.3 什么时候不需要

- 结构只有一层，没有「容器里再放容器」；
- 节点类型固定且很少，`instanceof` 只有一两处；
- 未来不会新增节点类型，也不会新增遍历操作。

---

## 7. 适用边界

### 适合

- 天然的树形结构：文件系统、组织架构、UI 组件树、菜单、表达式树；
- 希望客户端对「单个」和「一组」一视同仁；
- 新增节点类型时不想改容器的遍历逻辑。

### 不适合

- 结构是扁平的；
- 叶子节点和容器节点行为差异很大，硬套同一接口会产生大量空实现；
- 需要频繁新增「操作」而不是「节点类型」——那更接近访问者模式。

### 容易混淆

| 模式 | 区别 |
|---|---|
| **装饰器** | 装饰器是「一层套一层」的链，目的是加功能；组合是「容器包含多个子节点」的树，目的是统一处理整体与部分 |
| **责任链** | 责任链把请求沿链传递，通常只有一个节点处理；组合是把结果逐层汇总 |
| **访问者** | 组合解决「结构统一」；访问者解决「不修改节点类就新增操作」 |

> 组合模式买到的是「新增节点类型容易」，代价是「新增操作要改所有节点」。
> 这两件事正好是访问者模式的反方向，别把两者混为一谈。

---

## 8. 小练习

### 练习 1（场景判断）

一个订单只有「订单项列表」，没有「订单项里再套订单项」的结构。
需要组合模式吗？

**参考答案**：不需要。结构是扁平的，一个 `List<OrderItem>` 加一个循环就够。
组合模式的前提是**部分-整体的递归结构**。

### 练习 2（解释设计原因）

为什么 `FolderNode` 持有 `List<Node>`，而不是 `List<Object>`？

**参考答案**：`List<Node>` 让编译器保证每个子节点都能 `sizeKb()`/`print()`，
容器可以直接递归调用；`List<Object>` 则要求每个方法先 instanceof 再强转，
新增类型时所有方法都要改。

### 练习 3（取舍）

现在要给所有节点增加「导出 JSON」操作。用组合模式要改哪里？
有没有别的模式更适合？

**参考答案**：要在 `Node` 接口加 `exportJson()`，并让 `FileNode`、`FolderNode`、
`ShortcutNode` 都实现，违反「新增操作不改节点类」的期望。
如果「操作种类」会不断增加、节点类型稳定，访问者模式更合适；
如果「节点类型」不断增加、操作稳定，组合模式更合适。

---

## 9. 读完之后

你应该能回答：

1. 组合为什么存在？——让单个对象和容器对象统一对待，递归由多态完成。
2. 原始实现的问题？——`List<Object>` + 每个方法 instanceof，新增类型漏改。
3. 每个角色为什么存在？——`Node` 统一接口，`FileNode` 是叶子，`FolderNode` 是容器。
4. 怎么协作？——容器遍历子 Node，递归调用，结果逐层汇总。
5. 新场景要不要用？——看是否存在树形结构，以及变化方向是「加节点」还是「加操作」。

继续阅读：

- [深度文档](README.md)：透明式/安全式组合、递归边界与工程实现；
- [Client.java](Client.java)：工程版入口；
- [访问者入门](../../behavioral/visitor/BEGINNER.md)：新增操作频繁时；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
